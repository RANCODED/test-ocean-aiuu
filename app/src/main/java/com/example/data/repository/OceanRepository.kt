package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.config.CountryPack
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.AuditLog
import com.example.data.model.Haul
import com.example.data.model.HaulEventType
import com.example.data.model.IuuRiskLevel
import com.example.data.model.LicenseStatus
import com.example.data.model.ProcessorLot
import com.example.data.model.Trip
import com.example.data.model.TripStatus
import com.example.data.model.Vessel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.math.abs

class OceanRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ).apply {
            try {
                firestoreSettings = firestoreSettings {
                    setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                }
            } catch (_: Exception) {
                // Settings already configured on this instance
            }
        }
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing OceanAiUU.")
    }

    private fun <T> Flow<T>.logAndRethrowFirestoreErrors(uid: String, path: String): Flow<T> =
        catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val exceptionToLog = firestoreError ?: (error as? Exception)
            if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                handleFirestoreError(exceptionToLog, OperationType.LIST, path)
            }
            throw error
        }

    fun observeVessels(): Flow<List<Vessel>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/vessels"
        emitAll(
            db.collection("users").document(uid).collection("vessels")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Vessel::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .logAndRethrowFirestoreErrors(uid, path)
        )
    }

    fun observeTrips(): Flow<List<Trip>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/trips"
        emitAll(
            db.collection("users").document(uid).collection("trips")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Trip::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .logAndRethrowFirestoreErrors(uid, path)
        )
    }

    fun observeHauls(tripId: String): Flow<List<Haul>> = flow {
        if (tripId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val uid = requireUserId()
        val path = "users/$uid/trips/$tripId/hauls"
        emitAll(
            db.collection("users").document(uid)
                .collection("trips").document(tripId)
                .collection("hauls")
                .orderBy("haulNumber", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Haul::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .logAndRethrowFirestoreErrors(uid, path)
        )
    }

    fun observeLots(): Flow<List<ProcessorLot>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/lots"
        emitAll(
            db.collection("users").document(uid).collection("lots")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(ProcessorLot::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .logAndRethrowFirestoreErrors(uid, path)
        )
    }

    fun observeAuditLogs(): Flow<List<AuditLog>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/auditLogs"
        emitAll(
            db.collection("users").document(uid).collection("auditLogs")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(AuditLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .logAndRethrowFirestoreErrors(uid, path)
        )
    }

    suspend fun addVessel(
        name: String,
        registrationNo: String,
        countryCode: String,
        gearType: String,
        licenseStatus: LicenseStatus,
        licenseExpiry: String,
        coOwnerSplitPct: Int,
        actorName: String
    ): Result<String> {
        var docPath = "users/unauthenticated/vessels"
        return try {
            val uid = requireUserId()
            val cleanName = name.trim().take(60)
            val cleanReg = registrationNo.trim().take(32)
            val cleanGear = gearType.trim().take(40)
            val cleanExpiry = licenseExpiry.trim().take(20)
            require(cleanName.length >= 2) { "Vessel name must be at least 2 characters." }
            require(cleanReg.length >= 3) { "Registration number must be at least 3 characters." }
            require(cleanGear.length >= 2) { "Gear type is required." }
            require(cleanExpiry.length >= 4) { "License expiry is required." }
            val split = coOwnerSplitPct.coerceIn(0, 100)

            val docRef = db.collection("users").document(uid).collection("vessels").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "name" to cleanName,
                "registrationNo" to cleanReg,
                "countryCode" to countryCode,
                "gearType" to cleanGear,
                "licenseStatus" to licenseStatus.name,
                "licenseExpiry" to cleanExpiry,
                "coOwnerSplitPct" to split,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            appendAuditLog(
                uid = uid,
                actorName = actorName,
                action = "VESSEL_REGISTERED",
                entityType = "Vessel",
                entityId = docRef.id,
                details = "Registered $cleanName ($cleanReg) in $countryCode"
            )
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun startTrip(
        vessel: Vessel,
        captainName: String,
        crewCount: Int,
        departurePort: String,
        faoZone: String,
        gearType: String,
        isOffline: Boolean
    ): Result<String> {
        var docPath = "users/unauthenticated/trips"
        return try {
            val uid = requireUserId()
            require(vessel.id.isNotBlank()) { "Valid vessel required to open a voyage." }
            val cleanCaptain = captainName.trim().take(60)
            val cleanPort = departurePort.trim().take(60)
            val cleanZone = faoZone.trim().take(40)
            val cleanGear = gearType.trim().take(40)
            require(cleanCaptain.length >= 2) { "Captain name must be at least 2 characters." }
            require(cleanPort.length >= 2) { "Departure port is required." }
            val boundedCrew = crewCount.coerceIn(1, 100)

            val initialRisk = if (vessel.licenseStatusEnum() == LicenseStatus.EXPIRED) {
                IuuRiskLevel.HIGH
            } else {
                IuuRiskLevel.NONE
            }

            val docRef = db.collection("users").document(uid).collection("trips").document()
            docPath = docRef.path
            val payload = mapOf<String, Any?>(
                "userId" to uid,
                "vesselId" to vessel.id,
                "vesselName" to vessel.name.take(60),
                "countryCode" to vessel.countryCode,
                "captainName" to cleanCaptain,
                "crewCount" to boundedCrew,
                "departurePort" to cleanPort,
                "faoZone" to cleanZone,
                "gearType" to cleanGear,
                "status" to TripStatus.AT_SEA.name,
                "declaredWeightKg" to 0.0,
                "iuuRiskLevel" to initialRisk.name,
                "iuuSummary" to if (initialRisk == IuuRiskLevel.HIGH) {
                    "Departed with EXPIRED vessel permit (${vessel.registrationNo})."
                } else null,
                "syncPending" to isOffline,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>

            docRef.set(payload).await()
            appendAuditLog(
                uid = uid,
                actorName = cleanCaptain,
                action = "TRIP_STARTED",
                entityType = "Trip",
                entityId = docRef.id,
                details = "${vessel.name} departed $cleanPort ($cleanZone) · Crew: $boundedCrew"
            )
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun logHaul(
        trip: Trip,
        nextHaulNumber: Int,
        speciesCode: String,
        speciesName: String,
        scientificName: String,
        cratesCount: Int,
        weightKg: Double,
        latitude: Double,
        longitude: Double,
        faoZone: String,
        zoneAlert: Boolean,
        loggedBy: String,
        eventType: HaulEventType,
        aiVerified: Boolean,
        notes: String?,
        isOffline: Boolean
    ): Result<String> {
        var docPath = "users/unauthenticated/trips/${trip.id}/hauls"
        return try {
            val uid = requireUserId()
            require(trip.id.isNotBlank()) { "Trip ID is required." }
            require(trip.statusEnum() != TripStatus.VERIFIED) { "Verified trips are locked." }

            val cleanSpeciesCode = speciesCode.trim().take(16)
            val cleanSpeciesName = speciesName.trim().take(80)
            val cleanScientific = scientificName.trim().take(80)
            val cleanZone = faoZone.trim().take(40)
            val cleanLogger = loggedBy.trim().take(60)
            val cleanNotes = notes?.trim()?.takeIf { it.isNotBlank() }?.take(300)
            val boundedWeight = weightKg.coerceIn(0.0, 200000.0)
            val boundedCrates = cratesCount.coerceIn(0, 10000)

            val haulRef = db.collection("users").document(uid)
                .collection("trips").document(trip.id)
                .collection("hauls").document()
            docPath = haulRef.path

            val payload = mapOf<String, Any?>(
                "userId" to uid,
                "tripId" to trip.id,
                "haulNumber" to nextHaulNumber.coerceIn(1, 1000),
                "speciesCode" to cleanSpeciesCode,
                "speciesName" to cleanSpeciesName,
                "scientificName" to cleanScientific,
                "cratesCount" to boundedCrates,
                "weightKg" to boundedWeight,
                "latitude" to latitude.coerceIn(-90.0, 90.0),
                "longitude" to longitude.coerceIn(-180.0, 180.0),
                "faoZone" to cleanZone,
                "zoneAlert" to zoneAlert,
                "loggedBy" to cleanLogger,
                "eventType" to eventType.name,
                "aiVerified" to aiVerified,
                "notes" to cleanNotes,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>

            haulRef.set(payload).await()

            val newDeclaredTotal = (trip.declaredWeightKg + boundedWeight).coerceAtMost(1000000.0)
            val updatedRisk = when {
                zoneAlert || eventType == HaulEventType.TRANSSHIPMENT -> IuuRiskLevel.HIGH
                else -> trip.riskEnum()
            }
            val tripRef = db.collection("users").document(uid).collection("trips").document(trip.id)
            val tripUpdate = mapOf<String, Any?>(
                "declaredWeightKg" to newDeclaredTotal,
                "iuuRiskLevel" to updatedRisk.name,
                "iuuSummary" to when {
                    zoneAlert -> "Restricted zone haul logged in $cleanZone."
                    eventType == HaulEventType.TRANSSHIPMENT -> "At-sea transshipment logged; requires ministry review."
                    else -> trip.iuuSummary
                },
                "syncPending" to isOffline,
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>
            tripRef.update(tripUpdate).await()

            appendAuditLog(
                uid = uid,
                actorName = cleanLogger,
                action = if (zoneAlert) "ZONE_ALERT_HAUL" else "HAUL_LOGGED",
                entityType = "Haul",
                entityId = haulRef.id,
                details = "Haul #$nextHaulNumber · $cleanSpeciesName ($cleanSpeciesCode) ${boundedWeight}kg @ $cleanZone"
            )
            Result.success(haulRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun landAndCloseTrip(
        trip: Trip,
        unloadedPort: String,
        unloadedWeightKg: Double,
        actorName: String
    ): Result<Unit> {
        var docPath = "users/unauthenticated/trips/${trip.id}"
        return try {
            val uid = requireUserId()
            require(trip.id.isNotBlank()) { "Trip ID is required." }
            val cleanPort = unloadedPort.trim().take(60)
            require(cleanPort.length >= 2) { "Landing port is required." }
            val boundedUnloaded = unloadedWeightKg.coerceIn(0.0, 1000000.0)

            val discrepancyPct = if (trip.declaredWeightKg > 0.0) {
                (abs(boundedUnloaded - trip.declaredWeightKg) / trip.declaredWeightKg) * 100.0
            } else 0.0

            val computedRisk = when {
                trip.riskEnum() == IuuRiskLevel.HIGH || discrepancyPct > 15.0 -> IuuRiskLevel.HIGH
                discrepancyPct > 7.5 -> IuuRiskLevel.MEDIUM
                else -> IuuRiskLevel.LOW
            }
            val nextStatus = if (computedRisk == IuuRiskLevel.HIGH) TripStatus.FLAGGED else TripStatus.LANDED
            val summaryText = "Declared %.1f kg vs Unloaded %.1f kg (%.1f%% variance)."
                .format(trip.declaredWeightKg, boundedUnloaded, discrepancyPct)

            val docRef = db.collection("users").document(uid).collection("trips").document(trip.id)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "status" to nextStatus.name,
                    "unloadedPort" to cleanPort,
                    "unloadedWeightKg" to boundedUnloaded,
                    "discrepancyPct" to discrepancyPct,
                    "iuuRiskLevel" to computedRisk.name,
                    "iuuSummary" to summaryText,
                    "syncPending" to false,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            appendAuditLog(
                uid = uid,
                actorName = actorName,
                action = "TRIP_LANDED",
                entityType = "Trip",
                entityId = trip.id,
                details = "${trip.vesselName} landed at $cleanPort · $summaryText"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun syncPendingTrip(tripId: String, actorName: String): Result<Unit> {
        var docPath = "users/unauthenticated/trips/$tripId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("trips").document(tripId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "syncPending" to false,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            appendAuditLog(
                uid = uid,
                actorName = actorName,
                action = "PORT_SYNC_COMPLETED",
                entityType = "Trip",
                entityId = tripId,
                details = "Offline trip records reconciled with port ledger."
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveAiIuuAssessment(
        trip: Trip,
        riskLevel: IuuRiskLevel,
        summary: String,
        actorName: String
    ): Result<Unit> {
        var docPath = "users/unauthenticated/trips/${trip.id}"
        return try {
            val uid = requireUserId()
            require(trip.statusEnum() != TripStatus.VERIFIED) { "Verified trips are locked." }
            val docRef = db.collection("users").document(uid).collection("trips").document(trip.id)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "iuuRiskLevel" to riskLevel.name,
                    "iuuSummary" to summary.take(600),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            appendAuditLog(
                uid = uid,
                actorName = actorName,
                action = "AI_IUU_AUDIT",
                entityType = "Trip",
                entityId = trip.id,
                details = "AI risk assessment: ${riskLevel.name} · ${summary.take(160)}"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun verifyTripAndIssueCertificate(
        trip: Trip,
        inspectorNotes: String,
        actorName: String
    ): Result<String> {
        var docPath = "users/unauthenticated/trips/${trip.id}"
        return try {
            val uid = requireUserId()
            require(trip.statusEnum() != TripStatus.VERIFIED) { "Certificate already issued." }
            val certCode = "CERT-${trip.countryCode}-${(1000..9999).random()}"
            val docRef = db.collection("users").document(uid).collection("trips").document(trip.id)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "status" to TripStatus.VERIFIED.name,
                    "certificateCode" to certCode,
                    "auditNotes" to inspectorNotes.trim().take(400),
                    "syncPending" to false,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            appendAuditLog(
                uid = uid,
                actorName = actorName,
                action = "CERT_ISSUED",
                entityType = "Trip",
                entityId = trip.id,
                details = "Issued $certCode for ${trip.vesselName}. ${inspectorNotes.take(120)}"
            )
            Result.success(certCode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun flagTripIuuCase(
        trip: Trip,
        reason: String,
        actorName: String
    ): Result<Unit> {
        var docPath = "users/unauthenticated/trips/${trip.id}"
        return try {
            val uid = requireUserId()
            require(trip.statusEnum() != TripStatus.VERIFIED) { "Verified trips are locked." }
            val cleanReason = reason.trim().take(400).ifBlank { "Flagged for Ministry & Coast Guard inspection." }
            val docRef = db.collection("users").document(uid).collection("trips").document(trip.id)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "status" to TripStatus.FLAGGED.name,
                    "iuuRiskLevel" to IuuRiskLevel.HIGH.name,
                    "auditNotes" to cleanReason,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            appendAuditLog(
                uid = uid,
                actorName = actorName,
                action = "IUU_CASE_FLAGGED",
                entityType = "Trip",
                entityId = trip.id,
                details = "Flagged ${trip.vesselName}: $cleanReason"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun createProcessorLot(
        trip: Trip,
        speciesCode: String,
        speciesName: String,
        inputWeightKg: Double,
        yieldWeightKg: Double,
        coldChainTempC: Double,
        processorName: String,
        exportDestination: String,
        actorName: String
    ): Result<String> {
        var docPath = "users/unauthenticated/lots"
        return try {
            val uid = requireUserId()
            require(trip.id.isNotBlank()) { "Valid trip required." }
            val lotCode = "LOT-${trip.countryCode}-${(10000..99999).random()}"
            val certCode = trip.certificateCode?.takeIf { it.isNotBlank() } ?: "PENDING-CERT"
            val docRef = db.collection("users").document(uid).collection("lots").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "lotCode" to lotCode,
                "tripId" to trip.id,
                "vesselName" to trip.vesselName.take(60),
                "countryCode" to trip.countryCode,
                "speciesCode" to speciesCode.take(16),
                "speciesName" to speciesName.take(80),
                "faoZone" to trip.faoZone.take(40),
                "inputWeightKg" to inputWeightKg.coerceIn(1.0, 1000000.0),
                "yieldWeightKg" to yieldWeightKg.coerceIn(0.0, 1000000.0),
                "coldChainTempC" to coldChainTempC.coerceIn(-60.0, 25.0),
                "processorName" to processorName.trim().take(80),
                "certificateCode" to certCode,
                "gdstCompliant" to (trip.statusEnum() == TripStatus.VERIFIED),
                "exportDestination" to exportDestination.trim().take(80),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            appendAuditLog(
                uid = uid,
                actorName = actorName,
                action = "LOT_CREATED",
                entityType = "ProcessorLot",
                entityId = docRef.id,
                details = "Created $lotCode (${yieldWeightKg}kg) -> $exportDestination"
            )
            Result.success(lotCode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun seedCountryPilotWorkspace(pack: CountryPack, actorName: String): Result<Unit> {
        return try {
            val uid = requireUserId()
            val primarySpecies = pack.speciesCatalog.first()
            val secondarySpecies = pack.speciesCatalog.getOrElse(1) { primarySpecies }
            val primaryZone = pack.zones.first()
            val restrictedZone = pack.zones.lastOrNull { it.restricted } ?: primaryZone
            val primaryPort = pack.ports.first()

            val vessel1Id = addVessel(
                name = "${pack.code} Pelagic Horizon",
                registrationNo = "${pack.code}-7410",
                countryCode = pack.code,
                gearType = pack.gearTypes.first(),
                licenseStatus = LicenseStatus.ACTIVE,
                licenseExpiry = "2028-06-30",
                coOwnerSplitPct = 65,
                actorName = actorName
            ).getOrThrow()

            val vessel2Id = addVessel(
                name = "${pack.code} Coastal Pioneer",
                registrationNo = "${pack.code}-3892",
                countryCode = pack.code,
                gearType = pack.gearTypes.last(),
                licenseStatus = LicenseStatus.EXPIRING,
                licenseExpiry = "2026-12-15",
                coOwnerSplitPct = 50,
                actorName = actorName
            ).getOrThrow()

            val vessel1 = Vessel(
                id = vessel1Id,
                userId = uid,
                name = "${pack.code} Pelagic Horizon",
                registrationNo = "${pack.code}-7410",
                countryCode = pack.code,
                gearType = pack.gearTypes.first(),
                licenseStatus = LicenseStatus.ACTIVE.name,
                licenseExpiry = "2028-06-30",
                coOwnerSplitPct = 65L
            )
            val vessel2 = Vessel(
                id = vessel2Id,
                userId = uid,
                name = "${pack.code} Coastal Pioneer",
                registrationNo = "${pack.code}-3892",
                countryCode = pack.code,
                gearType = pack.gearTypes.last(),
                licenseStatus = LicenseStatus.EXPIRING.name,
                licenseExpiry = "2026-12-15",
                coOwnerSplitPct = 50L
            )

            // Active At-Sea Trip
            val activeTripId = startTrip(
                vessel = vessel1,
                captainName = actorName,
                crewCount = 14,
                departurePort = primaryPort,
                faoZone = primaryZone.code,
                gearType = vessel1.gearType,
                isOffline = false
            ).getOrThrow()

            val activeTrip = Trip(
                id = activeTripId,
                userId = uid,
                vesselId = vessel1Id,
                vesselName = vessel1.name,
                countryCode = pack.code,
                captainName = actorName,
                crewCount = 14L,
                departurePort = primaryPort,
                faoZone = primaryZone.code,
                gearType = vessel1.gearType,
                declaredWeightKg = 0.0
            )

            logHaul(
                trip = activeTrip,
                nextHaulNumber = 1,
                speciesCode = primarySpecies.faoCode,
                speciesName = primarySpecies.internationalName,
                scientificName = primarySpecies.scientificName,
                cratesCount = 12,
                weightKg = 540.0,
                latitude = pack.defaultLat,
                longitude = pack.defaultLon,
                faoZone = primaryZone.code,
                zoneAlert = false,
                loggedBy = actorName,
                eventType = HaulEventType.CATCH,
                aiVerified = true,
                notes = "Ice slurry Grade-A catch",
                isOffline = false
            ).getOrThrow()

            // Landed Trip ready for Ministry Inspection
            val landedTripId = startTrip(
                vessel = vessel2,
                captainName = "Capt. Mateo / Hadi",
                crewCount = 9,
                departurePort = primaryPort,
                faoZone = restrictedZone.code,
                gearType = vessel2.gearType,
                isOffline = false
            ).getOrThrow()

            val landedTrip = Trip(
                id = landedTripId,
                userId = uid,
                vesselId = vessel2Id,
                vesselName = vessel2.name,
                countryCode = pack.code,
                captainName = "Capt. Mateo / Hadi",
                crewCount = 9L,
                departurePort = primaryPort,
                faoZone = restrictedZone.code,
                gearType = vessel2.gearType,
                declaredWeightKg = 0.0
            )

            logHaul(
                trip = landedTrip,
                nextHaulNumber = 1,
                speciesCode = secondarySpecies.faoCode,
                speciesName = secondarySpecies.internationalName,
                scientificName = secondarySpecies.scientificName,
                cratesCount = 18,
                weightKg = 820.0,
                latitude = pack.defaultLat - 0.14,
                longitude = pack.defaultLon + 0.19,
                faoZone = restrictedZone.code,
                zoneAlert = restrictedZone.restricted,
                loggedBy = "Tech Assistant",
                eventType = HaulEventType.CATCH,
                aiVerified = false,
                notes = "Logged near zone boundary",
                isOffline = false
            ).getOrThrow()

            landAndCloseTrip(
                trip = landedTrip.copy(declaredWeightKg = 820.0, riskEnumSafePlaceholder = Unit).let {
                    landedTrip.copy(
                        declaredWeightKg = 820.0,
                        iuuRiskLevel = if (restrictedZone.restricted) IuuRiskLevel.HIGH.name else IuuRiskLevel.NONE.name
                    )
                },
                unloadedPort = primaryPort,
                unloadedWeightKg = 812.0,
                actorName = actorName
            ).getOrThrow()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun appendAuditLog(
        uid: String,
        actorName: String,
        action: String,
        entityType: String,
        entityId: String,
        details: String
    ) {
        val logRef = db.collection("users").document(uid).collection("auditLogs").document()
        val payload = mapOf(
            "userId" to uid,
            "actorName" to actorName.trim().ifBlank { "OceanAiUU Operator" }.take(60),
            "action" to action.take(60),
            "entityType" to entityType.take(40),
            "entityId" to entityId.take(128),
            "details" to details.take(400),
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            logRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, logRef.path)
        }
    }
}

private fun Trip.copy(declaredWeightKg: Double, riskEnumSafePlaceholder: Unit): Trip =
    this.copy(declaredWeightKg = declaredWeightKg)
