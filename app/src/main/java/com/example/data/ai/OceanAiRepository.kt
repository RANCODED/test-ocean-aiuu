package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.config.CountryPack
import com.example.data.model.Haul
import com.example.data.model.IuuAuditResult
import com.example.data.model.IuuRiskLevel
import com.example.data.model.SpeciesIdentificationResult
import com.example.data.model.Trip
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "OceanAiRepository"

class OceanAiRepository {

    private val speciesSchema = Schema.obj(
        properties = mapOf(
            "commonName" to Schema.string(description = "International English seafood species name"),
            "scientificName" to Schema.string(description = "Binomial Latin scientific name"),
            "faoAlphaCode" to Schema.string(description = "3-letter FAO ASFIS alpha code e.g. YFT, SKJ, BET, RSN"),
            "confidencePercent" to Schema.integer(description = "Confidence score from 0 to 100"),
            "protectedSpeciesWarning" to Schema.boolean(description = "True if CITES-listed, endangered shark/ray/turtle, or restricted catch"),
            "inspectionNote" to Schema.string(description = "Concise physical identification and handling note")
        )
    )

    private val iuuSchema = Schema.obj(
        properties = mapOf(
            "riskLevel" to Schema.enumeration(
                values = listOf("LOW", "MEDIUM", "HIGH"),
                description = "Overall IUU compliance risk classification"
            ),
            "summary" to Schema.string(description = "Concise ministry compliance summary"),
            "anomalyFlags" to Schema.array(
                items = Schema.string(),
                description = "Specific detected discrepancies or risk indicators"
            ),
            "coastGuardSyncRecommendation" to Schema.string(description = "Recommended action for port inspector or Coast Guard API")
        )
    )

    private val speciesModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = speciesSchema
            }
        )
    }

    private val iuuAuditModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = iuuSchema
            }
        )
    }

    suspend fun identifySpeciesFromPhoto(
        bitmap: Bitmap,
        countryPack: CountryPack
    ): Result<SpeciesIdentificationResult> {
        return try {
            val catalogSummary = countryPack.speciesCatalog.joinToString("; ") {
                "${it.faoCode}: ${it.internationalName} (${it.scientificName}, local: ${it.localName})"
            }
            val scaledBitmap = scaleBitmapForAi(bitmap)

            val response = withTimeout(60_000L) {
                speciesModel.generateContent(
                    content {
                        image(scaledBitmap)
                        text(
                            "You are OceanAiUU's marine fisheries taxonomist for ${countryPack.name} (${countryPack.regulatorName}). " +
                                "Identify the fish or marine species shown in this catch photo. " +
                                "Reference regional FAO species when applicable: $catalogSummary. " +
                                "Flag protectedSpeciesWarning=true if it appears to be a protected shark, ray, sea turtle, or marine mammal."
                        )
                    }
                )
            }

            val jsonText = response.text
                ?: return Result.failure(IllegalStateException("Gemini returned an empty response."))
            val json = JSONObject(jsonText)
            Result.success(
                SpeciesIdentificationResult(
                    commonName = json.getString("commonName"),
                    scientificName = json.getString("scientificName"),
                    faoAlphaCode = json.getString("faoAlphaCode").uppercase().take(6),
                    confidencePercent = json.optInt("confidencePercent", 80).coerceIn(0, 100),
                    protectedSpeciesWarning = json.optBoolean("protectedSpeciesWarning", false),
                    inspectionNote = json.optString("inspectionNote", "")
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Species identification timed out", e)
            Result.failure(Exception("Species identification timed out after 60 seconds. Please retry."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Species identification failed", e)
            Result.failure(e)
        }
    }

    suspend fun analyzeTripForIuuRisk(
        trip: Trip,
        hauls: List<Haul>,
        countryPack: CountryPack
    ): Result<IuuAuditResult> {
        return try {
            val haulLines = if (hauls.isEmpty()) {
                "No individual hauls loaded in view; declared trip total is ${trip.declaredWeightKg} kg."
            } else {
                hauls.joinToString("\n") { h ->
                    "Haul #${h.haulNumber}: ${h.eventType} ${h.speciesName} (${h.speciesCode}), " +
                        "${h.weightKg}kg, GPS(${h.latitude}, ${h.longitude}), Zone=${h.faoZone}, " +
                        "ZoneAlert=${h.zoneAlert}, AI_Photo_Verified=${h.aiVerified}, By=${h.loggedBy}"
                }
            }

            val prompt = """
                You are an anti-IUU fisheries compliance auditor for ${countryPack.name} (${countryPack.regulatorName}).
                Evaluate this voyage for Illegal, Unreported, and Unregulated (IUU) fishing anomalies:
                - Vessel: ${trip.vesselName} | Country: ${trip.countryCode} | Gear: ${trip.gearType}
                - Departure Port: ${trip.departurePort} | Target Zone: ${trip.faoZone}
                - Status: ${trip.status} | Crew Count: ${trip.crewCount}
                - At-Sea Declared Weight: ${trip.declaredWeightKg} kg
                - Unloaded Port: ${trip.unloadedPort ?: "Not landed"} | Unloaded Scale Weight: ${trip.unloadedWeightKg ?: "N/A"} kg
                - Discrepancy %: ${trip.discrepancyPct ?: 0.0}%
                - Existing Risk Flag: ${trip.iuuRiskLevel}
                - Haul Logbook:
                $haulLines

                Assess weight mismatches (>7.5% medium, >15% high), restricted zone alerts, transshipments, and missing AI/photo verification.
            """.trimIndent()

            val response = withTimeout(60_000L) {
                iuuAuditModel.generateContent(prompt)
            }
            val jsonText = response.text
                ?: return Result.failure(IllegalStateException("Gemini returned an empty IUU audit."))
            val json = JSONObject(jsonText)
            val flagsArray = json.getJSONArray("anomalyFlags")
            val flags = (0 until flagsArray.length()).map { flagsArray.getString(it) }
            val risk = IuuRiskLevel.fromRaw(json.getString("riskLevel"))

            Result.success(
                IuuAuditResult(
                    riskLevel = risk,
                    summary = json.getString("summary"),
                    anomalyFlags = flags,
                    coastGuardSyncRecommendation = json.getString("coastGuardSyncRecommendation")
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "IUU audit timed out", e)
            Result.failure(Exception("IUU risk audit timed out after 60 seconds. Please retry."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "IUU audit failed", e)
            Result.failure(e)
        }
    }

    private fun scaleBitmapForAi(bitmap: Bitmap): Bitmap {
        val maxDim = 1024
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDim && height <= maxDim) return bitmap
        val ratio = minOf(maxDim.toFloat() / width, maxDim.toFloat() / height)
        return Bitmap.createScaledBitmap(
            bitmap,
            (width * ratio).toInt().coerceAtLeast(1),
            (height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }
}
