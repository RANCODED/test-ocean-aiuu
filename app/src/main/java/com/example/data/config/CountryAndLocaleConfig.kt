package com.example.data.config

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.oceanPrefs by preferencesDataStore(name = "oceanaiuu_config")

data class SpeciesMaster(
    val faoCode: String,
    val internationalName: String,
    val localName: String,
    val scientificName: String,
    val protectedStatus: Boolean = false
)

data class ZoneInfo(
    val code: String,
    val name: String,
    val restricted: Boolean,
    val seasonalRule: String
)

data class CountryPack(
    val code: String,
    val name: String,
    val regulatorName: String,
    val coastGuardAgency: String,
    val currencyCode: String,
    val defaultLanguage: AppLanguage,
    val defaultLat: Double,
    val defaultLon: Double,
    val ports: List<String>,
    val gearTypes: List<String>,
    val zones: List<ZoneInfo>,
    val speciesCatalog: List<SpeciesMaster>,
    val processorName: String,
    val exportDestinations: List<String>
)

enum class AppLanguage(val code: String, val displayName: String, val isRtl: Boolean) {
    EN("en", "English", false),
    ID("id", "Bahasa Indonesia", false),
    ES("es", "Español", false),
    AR("ar", "العربية (RTL)", true);

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code == code } ?: EN
    }
}

data class LocalizedLabels(
    val tabAtSea: String,
    val tabFleet: String,
    val tabCompliance: String,
    val tabExport: String,
    val tabAdmin: String,
    val offlineModeBanner: String,
    val onlineSyncedBanner: String,
    val startTrip: String,
    val logHaul: String,
    val aiIdentifySpecies: String,
    val closeAndLandTrip: String,
    val runIuuAudit: String,
    val issueCertificate: String,
    val flagIuuCase: String,
    val createLot: String,
    val verifyLotCode: String,
    val registerVessel: String,
    val seedPilotData: String,
    val signOut: String
)

object CountryRegistry {
    val packs: List<CountryPack> = listOf(
        CountryPack(
            code = "IDN",
            name = "Indonesia",
            regulatorName = "KKP · Ministry of Marine Affairs",
            coastGuardAgency = "BAKAMLA Maritime Feed",
            currencyCode = "IDR",
            defaultLanguage = AppLanguage.ID,
            defaultLat = -8.7467,
            defaultLon = 115.2126,
            ports = listOf("Benoa (Bali)", "Bitung (North Sulawesi)", "Cilacap", "Muara Baru"),
            gearTypes = listOf("Longline", "Purse Seine", "Pole & Line", "Handline"),
            zones = listOf(
                ZoneInfo("FAO 71 / WPP-714", "Banda Sea Pelagic Zone", false, "Open · Quota Monitored"),
                ZoneInfo("FAO 57 / WPP-573", "Indian Ocean South Java", false, "Open · Tuna Corridor"),
                ZoneInfo("FAO 71 / MPA-714", "Banda Sea Spawning Sanctuary", true, "RESTRICTED · No-Take Zone")
            ),
            speciesCatalog = listOf(
                SpeciesMaster("YFT", "Yellowfin Tuna", "Madidihang", "Thunnus albacares"),
                SpeciesMaster("SKJ", "Skipjack Tuna", "Cakalang", "Katsuwonus pelamis"),
                SpeciesMaster("BET", "Bigeye Tuna", "Tuna Mata Besar", "Thunnus obesus"),
                SpeciesMaster("RSN", "Red Snapper", "Kakap Merah", "Lutjanus campechanus"),
                SpeciesMaster("OCS", "Oceanic Whitetip Shark", "Hiu Koboi", "Carcharhinus longimanus", protectedStatus = true)
            ),
            processorName = "Nusantara Cold-Chain JV",
            exportDestinations = listOf("Tokyo, JP", "Los Angeles, US", "Rotterdam, NL")
        ),
        CountryPack(
            code = "ECU",
            name = "Ecuador",
            regulatorName = "SRP · Subsecretaría de Pesca",
            coastGuardAgency = "DIRNEA Coast Guard API",
            currencyCode = "USD",
            defaultLanguage = AppLanguage.ES,
            defaultLat = -0.9538,
            defaultLon = -80.7339,
            ports = listOf("Manta", "Posorja", "Esmeraldas", "Santa Rosa"),
            gearTypes = listOf("Purse Seine", "Longline", "Artisanal Handline"),
            zones = listOf(
                ZoneInfo("FAO 87 / SE-PAC", "Eastern Pacific Tuna Zone", false, "Open · IATTC Compliant"),
                ZoneInfo("FAO 87 / COAST", "Manabí Coastal Shelf", false, "Open · Artisanal Priority"),
                ZoneInfo("FAO 87 / GMR", "Galápagos Marine Reserve Buffer", true, "RESTRICTED · Protected Sanctuary")
            ),
            speciesCatalog = listOf(
                SpeciesMaster("SKJ", "Skipjack Tuna", "Barrilete", "Katsuwonus pelamis"),
                SpeciesMaster("YFT", "Yellowfin Tuna", "Atún Aleta Amarilla", "Thunnus albacares"),
                SpeciesMaster("DOL", "Mahi-Mahi", "Dorado", "Coryphaena hippurus"),
                SpeciesMaster("SWO", "Swordfish", "Pez Espada", "Xiphias gladius"),
                SpeciesMaster("RMB", "Giant Manta Ray", "Mantarraya Gigante", "Mobula birostris", protectedStatus = true)
            ),
            processorName = "Pacífico Export JV",
            exportDestinations = listOf("Vigo, ES", "Miami, US", "Rotterdam, NL")
        ),
        CountryPack(
            code = "MAR",
            name = "Morocco",
            regulatorName = "DPM · Département Pêche Maritime",
            coastGuardAgency = "Marine Royale VMS Sync",
            currencyCode = "MAD",
            defaultLanguage = AppLanguage.AR,
            defaultLat = 30.4278,
            defaultLon = -9.5981,
            ports = listOf("Agadir", "Dakhla", "Tan-Tan", "Safi"),
            gearTypes = listOf("Pelagic Trawl", "Longline", "Sardine Purse Seine"),
            zones = listOf(
                ZoneInfo("FAO 34 / ATL-C", "Central Atlantic Pelagic", false, "Open · Quota Active"),
                ZoneInfo("FAO 34 / SOUTH", "Dakhla Offshore Belt", false, "Open · Seasonal TAC"),
                ZoneInfo("FAO 34 / NURSERY", "Dakhla Octopus Nursery", true, "RESTRICTED · Seasonal Closure")
            ),
            speciesCatalog = listOf(
                SpeciesMaster("PIL", "European Sardine", "Sardine / سردين", "Sardina pilchardus"),
                SpeciesMaster("OCC", "Common Octopus", "Poulpe / أخطبوط", "Octopus vulgaris"),
                SpeciesMaster("MAS", "Chub Mackerel", "Maquereau / إسقمري", "Scomber colias"),
                SpeciesMaster("BFT", "Atlantic Bluefin Tuna", "Thon Rouge / تونة حمراء", "Thunnus thynnus")
            ),
            processorName = "Atlantique Cold-Chain JV",
            exportDestinations = listOf("Marseille, FR", "Algeciras, ES", "Dubai, AE")
        )
    )

    fun byCode(code: String): CountryPack =
        packs.firstOrNull { it.code == code } ?: packs.first()

    fun labels(lang: AppLanguage): LocalizedLabels = when (lang) {
        AppLanguage.EN -> LocalizedLabels(
            tabAtSea = "At Sea",
            tabFleet = "Fleet",
            tabCompliance = "Ministry",
            tabExport = "Export",
            tabAdmin = "Admin",
            offlineModeBanner = "OFFLINE AT SEA · Writes cached locally & queued for port sync",
            onlineSyncedBanner = "PORT SYNC LIVE · Real-time ledger connected",
            startTrip = "Start Voyage",
            logHaul = "Log Haul",
            aiIdentifySpecies = "AI Photo Species ID",
            closeAndLandTrip = "Land & Close Trip",
            runIuuAudit = "Run AI IUU Audit",
            issueCertificate = "Issue Catch Cert",
            flagIuuCase = "Flag IUU Risk",
            createLot = "Create Export Lot",
            verifyLotCode = "Verify Lot Origin",
            registerVessel = "Register Vessel",
            seedPilotData = "Load Country Pilot Fleet",
            signOut = "Sign Out"
        )
        AppLanguage.ID -> LocalizedLabels(
            tabAtSea = "Di Laut",
            tabFleet = "Armada",
            tabCompliance = "KKP / IUU",
            tabExport = "Ekspor",
            tabAdmin = "Admin",
            offlineModeBanner = "OFFLINE DI LAUT · Tersimpan lokal, antre sinkron di pelabuhan",
            onlineSyncedBanner = "SINKRON AKTIF · Terhubung ke buku besar pelabuhan",
            startTrip = "Mulai Pelayaran",
            logHaul = "Catat Tangkapan",
            aiIdentifySpecies = "Identifikasi Ikan AI",
            closeAndLandTrip = "Tutup & Bongkar",
            runIuuAudit = "Audit AI IUU",
            issueCertificate = "Terbitkan SHTI",
            flagIuuCase = "Tandai Kasus IUU",
            createLot = "Buat Lot Ekspor",
            verifyLotCode = "Cek Asal Lot",
            registerVessel = "Daftar Kapal",
            seedPilotData = "Muat Armada Pilot",
            signOut = "Keluar"
        )
        AppLanguage.ES -> LocalizedLabels(
            tabAtSea = "En Mar",
            tabFleet = "Flota",
            tabCompliance = "Ministerio",
            tabExport = "Exportar",
            tabAdmin = "Admin",
            offlineModeBanner = "SIN CONEXIÓN EN MAR · Guardado local para sincronizar en puerto",
            onlineSyncedBanner = "SINCRONIZADO · Registro en vivo conectado",
            startTrip = "Iniciar Viaje",
            logHaul = "Registrar Lance",
            aiIdentifySpecies = "ID Especie con IA",
            closeAndLandTrip = "Cerrar Descarga",
            runIuuAudit = "Auditoría IA INDNR",
            issueCertificate = "Emitir Certificado",
            flagIuuCase = "Marcar Alerta INDNR",
            createLot = "Crear Lote Export",
            verifyLotCode = "Verificar Lote",
            registerVessel = "Registrar Buque",
            seedPilotData = "Cargar Flota Piloto",
            signOut = "Cerrar Sesión"
        )
        AppLanguage.AR -> LocalizedLabels(
            tabAtSea = "في البحر",
            tabFleet = "الأسطول",
            tabCompliance = "الوزارة",
            tabExport = "التصدير",
            tabAdmin = "الإدارة",
            offlineModeBanner = "وضع البحر دون اتصال · يتم الحفظ محلياً للمزامنة بالميناء",
            onlineSyncedBanner = "متصل بالميناء · السجل الرقمي متزامن",
            startTrip = "بدء الرحلة",
            logHaul = "تسجيل الصيد",
            aiIdentifySpecies = "تحديد النوع بالذكاء",
            closeAndLandTrip = "إنهاء وإنزال الحمولة",
            runIuuAudit = "تدقيق مخاطر الصيد",
            issueCertificate = "إصدار شهادة الصيد",
            flagIuuCase = "تسجيل مخالفة",
            createLot = "إنشاء دفعة تصدير",
            verifyLotCode = "التحقق من الدفعة",
            registerVessel = "تسجيل سفينة",
            seedPilotData = "تحميل أسطول تجريبي",
            signOut = "تسجيل الخروج"
        )
    }
}

class PreferencesRepository(private val context: Context) {
    private val keyCountry = stringPreferencesKey("selected_country")
    private val keyLanguage = stringPreferencesKey("selected_language")

    val selectedCountryCode: Flow<String> = context.oceanPrefs.data.map { prefs ->
        prefs[keyCountry] ?: "IDN"
    }

    val selectedLanguage: Flow<AppLanguage> = context.oceanPrefs.data.map { prefs ->
        AppLanguage.fromCode(prefs[keyLanguage])
    }

    suspend fun setCountry(countryCode: String) {
        val pack = CountryRegistry.byCode(countryCode)
        context.oceanPrefs.edit { prefs ->
            prefs[keyCountry] = pack.code
            prefs[keyLanguage] = pack.defaultLanguage.code
        }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.oceanPrefs.edit { prefs ->
            prefs[keyLanguage] = language.code
        }
    }
}
