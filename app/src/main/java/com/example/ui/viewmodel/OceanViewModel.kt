package com.example.ui.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.OceanAiRepository
import com.example.data.config.AppLanguage
import com.example.data.config.CountryPack
import com.example.data.config.CountryRegistry
import com.example.data.config.PreferencesRepository
import com.example.data.model.AuditLog
import com.example.data.model.Haul
import com.example.data.model.HaulEventType
import com.example.data.model.IuuAuditResult
import com.example.data.model.LicenseStatus
import com.example.data.model.ProcessorLot
import com.example.data.model.SpeciesIdentificationResult
import com.example.data.model.Trip
import com.example.data.model.Vessel
import com.example.data.repository.OceanRepository
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface AiTaskState<out T> {
    data object Idle : AiTaskState<Nothing>
    data object Loading : AiTaskState<Nothing>
    data class Success<T>(val data: T) : AiTaskState<T>
    data class Error(val message: String) : AiTaskState<Nothing>
}

data class GpsFix(
    val latitude: Double,
    val longitude: Double,
    val isLiveDeviceFix: Boolean
)

class OceanViewModel(
    private val repository: OceanRepository,
    private val aiRepository: OceanAiRepository,
    private val prefsRepository: PreferencesRepository,
    private val currentUserId: String,
    val actorDisplayName: String
) : ViewModel() {

    private companion object {
        const val TAG = "OceanVM"
        const val STOP_TIMEOUT_MS = 5000L
    }

    val countryCode: StateFlow<String> = prefsRepository.selectedCountryCode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), "IDN")

    val language: StateFlow<AppLanguage> = prefsRepository.selectedLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppLanguage.EN)

    val vesselsState: StateFlow<UiState<List<Vessel>>> = repository.observeVessels()
        .toUiState("Failed to observe vessels")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val tripsState: StateFlow<UiState<List<Trip>>> = repository.observeTrips()
        .toUiState("Failed to observe trips")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    private val _selectedTripId = MutableStateFlow<String?>(null)
    val selectedTripId: StateFlow<String?> = _selectedTripId.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val selectedTripHaulsState: StateFlow<UiState<List<Haul>>> = _selectedTripId
        .flatMapLatest { tripId ->
            repository.observeHauls(tripId.orEmpty())
        }
        .toUiState("Failed to observe hauls")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Success(emptyList()))

    val lotsState: StateFlow<UiState<List<ProcessorLot>>> = repository.observeLots()
        .toUiState("Failed to observe processor lots")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val auditLogsState: StateFlow<UiState<List<AuditLog>>> = repository.observeAuditLogs()
        .toUiState("Failed to observe audit log")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    private val _speciesAiState = MutableStateFlow<AiTaskState<SpeciesIdentificationResult>>(AiTaskState.Idle)
    val speciesAiState: StateFlow<AiTaskState<SpeciesIdentificationResult>> = _speciesAiState.asStateFlow()

    private val _iuuAiState = MutableStateFlow<AiTaskState<IuuAuditResult>>(AiTaskState.Idle)
    val iuuAiState: StateFlow<AiTaskState<IuuAuditResult>> = _iuuAiState.asStateFlow()

    private val _simulatedAtSeaOffline = MutableStateFlow(false)
    val simulatedAtSeaOffline: StateFlow<Boolean> = _simulatedAtSeaOffline.asStateFlow()

    private val _actionFeedback = MutableStateFlow<String?>(null)
    val actionFeedback: StateFlow<String?> = _actionFeedback.asStateFlow()

    private val _gpsFix = MutableStateFlow<GpsFix?>(null)
    val gpsFix: StateFlow<GpsFix?> = _gpsFix.asStateFlow()

    fun selectTrip(tripId: String?) {
        _selectedTripId.value = tripId
    }

    fun clearFeedback() {
        _actionFeedback.value = null
    }

    fun clearSpeciesAi() {
        _speciesAiState.value = AiTaskState.Idle
    }

    fun clearIuuAi() {
        _iuuAiState.value = AiTaskState.Idle
    }

    fun toggleSimulatedAtSeaOffline() {
        _simulatedAtSeaOffline.value = !_simulatedAtSeaOffline.value
    }

    fun setCountry(code: String) {
        viewModelScope.launch { prefsRepository.setCountry(code) }
    }

    fun setLanguage(lang: AppLanguage) {
        viewModelScope.launch { prefsRepository.setLanguage(lang) }
    }

    fun observeNetworkConnected(context: Context): Flow<Boolean> = callbackFlow {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        fun isOnline(): Boolean {
            val active = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(active) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
        trySend(isOnline())
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(isOnline()) }
            override fun onLost(network: Network) { trySend(isOnline()) }
        }
        cm.registerDefaultNetworkCallback(callback)
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }

    @SuppressLint("MissingPermission")
    fun refreshDeviceGps(context: Context, fallbackPack: CountryPack) {
        viewModelScope.launch {
            try {
                val client = LocationServices.getFusedLocationProviderClient(context)
                val location = client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
                    ?: client.lastLocation.await()
                if (location != null) {
                    _gpsFix.value = GpsFix(location.latitude, location.longitude, isLiveDeviceFix = true)
                } else {
                    _gpsFix.value = GpsFix(fallbackPack.defaultLat, fallbackPack.defaultLon, isLiveDeviceFix = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "GPS read fallback to coastal port coordinates: ${e.message}")
                _gpsFix.value = GpsFix(fallbackPack.defaultLat, fallbackPack.defaultLon, isLiveDeviceFix = false)
            }
        }
    }

    fun addVessel(
        name: String,
        registrationNo: String,
        countryCode: String,
        gearType: String,
        licenseStatus: LicenseStatus,
        licenseExpiry: String,
        coOwnerSplitPct: Int
    ) {
        viewModelScope.launch {
            repository.addVessel(
                name = name,
                registrationNo = registrationNo,
                countryCode = countryCode,
                gearType = gearType,
                licenseStatus = licenseStatus,
                licenseExpiry = licenseExpiry,
                coOwnerSplitPct = coOwnerSplitPct,
                actorName = actorDisplayName
            ).onSuccess {
                _actionFeedback.value = "Vessel $name registered."
            }.onFailure { e ->
                _actionFeedback.value = e.localizedMessage ?: "Failed to register vessel."
            }
        }
    }

    fun startTrip(
        vessel: Vessel,
        captainName: String,
        crewCount: Int,
        departurePort: String,
        faoZone: String,
        gearType: String,
        isOffline: Boolean
    ) {
        viewModelScope.launch {
            repository.startTrip(
                vessel = vessel,
                captainName = captainName,
                crewCount = crewCount,
                departurePort = departurePort,
                faoZone = faoZone,
                gearType = gearType,
                isOffline = isOffline
            ).onSuccess { tripId ->
                _selectedTripId.value = tripId
                _actionFeedback.value = if (isOffline) {
                    "Voyage started offline · queued for port sync."
                } else {
                    "Voyage started and logged."
                }
            }.onFailure { e ->
                _actionFeedback.value = e.localizedMessage ?: "Failed to start voyage."
            }
        }
    }

    fun logHaul(
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
    ) {
        viewModelScope.launch {
            repository.logHaul(
                trip = trip,
                nextHaulNumber = nextHaulNumber,
                speciesCode = speciesCode,
                speciesName = speciesName,
                scientificName = scientificName,
                cratesCount = cratesCount,
                weightKg = weightKg,
                latitude = latitude,
                longitude = longitude,
                faoZone = faoZone,
                zoneAlert = zoneAlert,
                loggedBy = loggedBy,
                eventType = eventType,
                aiVerified = aiVerified,
                notes = notes,
                isOffline = isOffline
            ).onSuccess {
                _actionFeedback.value = if (zoneAlert) {
                    "Haul #$nextHaulNumber logged with RESTRICTED ZONE alert."
                } else {
                    "Haul #$nextHaulNumber recorded (${weightKg} kg)."
                }
            }.onFailure { e ->
                _actionFeedback.value = e.localizedMessage ?: "Failed to log haul."
            }
        }
    }

    fun landAndCloseTrip(trip: Trip, unloadedPort: String, unloadedWeightKg: Double) {
        viewModelScope.launch {
            repository.landAndCloseTrip(trip, unloadedPort, unloadedWeightKg, actorDisplayName)
                .onSuccess {
                    _actionFeedback.value = "Trip landed at $unloadedPort for ministry review."
                }.onFailure { e ->
                    _actionFeedback.value = e.localizedMessage ?: "Failed to land trip."
                }
        }
    }

    fun syncTripAtPort(tripId: String) {
        viewModelScope.launch {
            repository.syncPendingTrip(tripId, actorDisplayName)
                .onSuccess { _actionFeedback.value = "Trip synced to port ledger." }
                .onFailure { e -> _actionFeedback.value = e.localizedMessage ?: "Sync failed." }
        }
    }

    fun identifySpecies(bitmap: Bitmap, pack: CountryPack) {
        viewModelScope.launch {
            _speciesAiState.value = AiTaskState.Loading
            aiRepository.identifySpeciesFromPhoto(bitmap, pack)
                .onSuccess { result -> _speciesAiState.value = AiTaskState.Success(result) }
                .onFailure { e ->
                    _speciesAiState.value = AiTaskState.Error(e.localizedMessage ?: "Species ID failed.")
                }
        }
    }

    fun runAiIuuAudit(trip: Trip, hauls: List<Haul>, pack: CountryPack) {
        viewModelScope.launch {
            _iuuAiState.value = AiTaskState.Loading
            aiRepository.analyzeTripForIuuRisk(trip, hauls, pack)
                .onSuccess { result ->
                    _iuuAiState.value = AiTaskState.Success(result)
                    repository.saveAiIuuAssessment(trip, result.riskLevel, result.summary, actorDisplayName)
                }
                .onFailure { e ->
                    _iuuAiState.value = AiTaskState.Error(e.localizedMessage ?: "IUU audit failed.")
                }
        }
    }

    fun verifyTripAndIssueCertificate(trip: Trip, inspectorNotes: String) {
        viewModelScope.launch {
            repository.verifyTripAndIssueCertificate(trip, inspectorNotes, actorDisplayName)
                .onSuccess { cert -> _actionFeedback.value = "Issued catch certificate $cert." }
                .onFailure { e -> _actionFeedback.value = e.localizedMessage ?: "Certification failed." }
        }
    }

    fun flagTripIuuCase(trip: Trip, reason: String) {
        viewModelScope.launch {
            repository.flagTripIuuCase(trip, reason, actorDisplayName)
                .onSuccess { _actionFeedback.value = "IUU case escalated to Ministry & Coast Guard." }
                .onFailure { e -> _actionFeedback.value = e.localizedMessage ?: "Flagging failed." }
        }
    }

    fun createProcessorLot(
        trip: Trip,
        speciesCode: String,
        speciesName: String,
        inputWeightKg: Double,
        yieldWeightKg: Double,
        coldChainTempC: Double,
        processorName: String,
        exportDestination: String
    ) {
        viewModelScope.launch {
            repository.createProcessorLot(
                trip = trip,
                speciesCode = speciesCode,
                speciesName = speciesName,
                inputWeightKg = inputWeightKg,
                yieldWeightKg = yieldWeightKg,
                coldChainTempC = coldChainTempC,
                processorName = processorName,
                exportDestination = exportDestination,
                actorName = actorDisplayName
            ).onSuccess { lotCode ->
                _actionFeedback.value = "Export Lot $lotCode generated."
            }.onFailure { e ->
                _actionFeedback.value = e.localizedMessage ?: "Failed to create export lot."
            }
        }
    }

    fun seedPilotWorkspace(pack: CountryPack) {
        viewModelScope.launch {
            repository.seedCountryPilotWorkspace(pack, actorDisplayName)
                .onSuccess { _actionFeedback.value = "Loaded ${pack.name} pilot fleet & voyages." }
                .onFailure { e -> _actionFeedback.value = e.localizedMessage ?: "Pilot setup failed." }
        }
    }

    private fun <T> Flow<T>.toUiState(fallbackError: String): Flow<UiState<T>> =
        map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, fallbackError, rootError)
                emit(UiState.Error(rootError.message ?: fallbackError))
            }
}
