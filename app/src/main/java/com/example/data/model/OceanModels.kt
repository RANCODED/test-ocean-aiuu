package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class LicenseStatus {
    ACTIVE, EXPIRING, EXPIRED;

    companion object {
        fun fromRaw(raw: String?): LicenseStatus =
            entries.firstOrNull { it.name == raw } ?: ACTIVE
    }
}

enum class TripStatus {
    AT_SEA, LANDED, VERIFIED, FLAGGED;

    companion object {
        fun fromRaw(raw: String?): TripStatus =
            entries.firstOrNull { it.name == raw } ?: AT_SEA
    }
}

enum class IuuRiskLevel {
    NONE, LOW, MEDIUM, HIGH;

    companion object {
        fun fromRaw(raw: String?): IuuRiskLevel =
            entries.firstOrNull { it.name == raw } ?: NONE
    }
}

enum class HaulEventType {
    CATCH, BYCATCH_RELEASE, GEAR_CHANGE, TRANSSHIPMENT, WEATHER_DELAY;

    companion object {
        fun fromRaw(raw: String?): HaulEventType =
            entries.firstOrNull { it.name == raw } ?: CATCH
    }
}

@Keep
@IgnoreExtraProperties
data class Vessel(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val registrationNo: String = "",
    val countryCode: String = "IDN",
    val gearType: String = "",
    val licenseStatus: String = LicenseStatus.ACTIVE.name,
    val licenseExpiry: String = "",
    val coOwnerSplitPct: Long = 50L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun licenseStatusEnum(): LicenseStatus = LicenseStatus.fromRaw(licenseStatus)
}

@Keep
@IgnoreExtraProperties
data class Trip(
    @DocumentId val id: String = "",
    val userId: String = "",
    val vesselId: String = "",
    val vesselName: String = "",
    val countryCode: String = "IDN",
    val captainName: String = "",
    val crewCount: Long = 1L,
    val departurePort: String = "",
    val faoZone: String = "",
    val gearType: String = "",
    val status: String = TripStatus.AT_SEA.name,
    val unloadedPort: String? = null,
    val unloadedWeightKg: Double? = null,
    val declaredWeightKg: Double = 0.0,
    val discrepancyPct: Double? = null,
    val iuuRiskLevel: String = IuuRiskLevel.NONE.name,
    val iuuSummary: String? = null,
    val certificateCode: String? = null,
    val auditNotes: String? = null,
    val syncPending: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun statusEnum(): TripStatus = TripStatus.fromRaw(status)
    fun riskEnum(): IuuRiskLevel = IuuRiskLevel.fromRaw(iuuRiskLevel)
}

@Keep
@IgnoreExtraProperties
data class Haul(
    @DocumentId val id: String = "",
    val userId: String = "",
    val tripId: String = "",
    val haulNumber: Long = 1L,
    val speciesCode: String = "",
    val speciesName: String = "",
    val scientificName: String = "",
    val cratesCount: Long = 0L,
    val weightKg: Double = 0.0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val faoZone: String = "",
    val zoneAlert: Boolean = false,
    val loggedBy: String = "",
    val eventType: String = HaulEventType.CATCH.name,
    val aiVerified: Boolean = false,
    val notes: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun eventTypeEnum(): HaulEventType = HaulEventType.fromRaw(eventType)
}

@Keep
@IgnoreExtraProperties
data class ProcessorLot(
    @DocumentId val id: String = "",
    val userId: String = "",
    val lotCode: String = "",
    val tripId: String = "",
    val vesselName: String = "",
    val countryCode: String = "IDN",
    val speciesCode: String = "",
    val speciesName: String = "",
    val faoZone: String = "",
    val inputWeightKg: Double = 0.0,
    val yieldWeightKg: Double = 0.0,
    val coldChainTempC: Double = -18.0,
    val processorName: String = "",
    val certificateCode: String = "",
    val gdstCompliant: Boolean = true,
    val exportDestination: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AuditLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val actorName: String = "",
    val action: String = "",
    val entityType: String = "",
    val entityId: String = "",
    val details: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class SpeciesIdentificationResult(
    val commonName: String,
    val scientificName: String,
    val faoAlphaCode: String,
    val confidencePercent: Int,
    val protectedSpeciesWarning: Boolean,
    val inspectionNote: String
)

data class IuuAuditResult(
    val riskLevel: IuuRiskLevel,
    val summary: String,
    val anomalyFlags: List<String>,
    val coastGuardSyncRecommendation: String
)
