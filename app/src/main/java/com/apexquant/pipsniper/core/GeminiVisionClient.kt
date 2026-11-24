package com.apexquant.pipsniper.core

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

data class ScanOutcome(
    val success: Boolean,
    val direction: String = "BUY",
    val entry: Double = 0.0,
    val sl: Double = 0.0,
    val tp: Double = 0.0,
    val errorMessage: String = ""
)

object GeminiVisionClient {

    /**
     * Exact Tick Sniper Streamlit technique:
     * 1. Query /v1beta/models to get the exact active models enabled on the user's API key.
     * 2. Send high-clarity JPEG chart screenshot so right-side price axis labels stay razor sharp.
     * 3. Extract direction, entry, sl, and tp via flexible JSON + regex fallback.
     */
    suspend fun scanChart(apiKey: String, bitmap: Bitmap, onProgress: (String) -> Unit): ScanOutcome {
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) {
            return ScanOutcome(false, errorMessage = "API Key is empty.")
        }

        onProgress("Discovering active Gemini models on your API key...")
        val models = discoverActiveModels(cleanKey)

        // Keep resolution high (up to 1600px) so right-axis price numbers remain legible
        val maxDim = 1600
        val scale = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height, 1f)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )
        } else bitmap

        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 92, baos)
        val base64Image = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

        val prompt = """
            Analyze this Gold / XAUUSD trading chart screenshot (TradingView or MetaTrader 5).
            Read the exact price values on the right-hand vertical price axis corresponding to the trade setup box (Long/Buy or Short/Sell risk-reward tool) or horizontal Entry, Stop Loss (SL), and Take Profit (TP) lines.
            - If the green target zone is ABOVE the entry line (or red stop zone is below), direction is "BUY".
            - If the green target zone is BELOW the entry line (or red stop zone is above), direction is "SELL".
            Respond ONLY with a single JSON object in this exact format:
            {"direction": "BUY", "entry": 0.0, "sl": 0.0, "tp": 0.0}
        """.trimIndent()

        val requestJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", prompt))
                        put(JSONObject().apply {
                            put("inline_data", JSONObject().apply {
                                put("mime_type", "image/jpeg")
                                put("data", base64Image)
                            })
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.0)
            })
        }

        var lastError = "Unable to connect to Gemini API"

        for (model in models) {
            onProgress("Scanning screenshot with $model...")
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$cleanKey"
            val req = Request.Builder()
                .url(url)
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            try {
                MarketDataEngine.client.newCall(req).execute().use { res ->
                    val bodyStr = res.body?.string() ?: ""
                    if (res.isSuccessful) {
                        val raw = JSONObject(bodyStr)
                            .getJSONArray("candidates").getJSONObject(0)
                            .getJSONObject("content").getJSONArray("parts")
                            .getJSONObject(0).getString("text")

                        val parsed = parseFlexibleResponse(raw)
                        if (parsed != null && (parsed.entry > 0.0 || parsed.sl > 0.0 || parsed.tp > 0.0)) {
                            return parsed
                        } else {
                            lastError = "$model could not read price levels from image. Raw: ${raw.take(90)}"
                        }
                    } else {
                        val err = try {
                            JSONObject(bodyStr).getJSONObject("error").getString("message")
                        } catch (_: Exception) {
                            "HTTP ${res.code}"
                        }
                        lastError = "$model: $err"
                    }
                }
            } catch (e: Exception) {
                lastError = "$model: ${e.message}"
            }
            delay(300)
        }

        return ScanOutcome(success = false, errorMessage = lastError)
    }

    private fun discoverActiveModels(apiKey: String): List<String> {
        val discovered = mutableListOf<String>()
        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
            val req = Request.Builder().url(url).build()
            MarketDataEngine.client.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val modelsArr = JSONObject(res.body?.string() ?: "{}").optJSONArray("models")
                    if (modelsArr != null) {
                        for (i in 0 until modelsArr.length()) {
                            val m = modelsArr.getJSONObject(i)
                            val name = m.optString("name", "").removePrefix("models/")
                            val methods = m.optJSONArray("supportedGenerationMethods")?.toString() ?: ""
                            if (methods.contains("generateContent") &&
                                name.contains("flash", ignoreCase = true) &&
                                !name.contains("audio", ignoreCase = true) &&
                                !name.contains("tts", ignoreCase = true)
                            ) {
                                discovered.add(name)
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }

        // Prioritize newest Flash models first, followed by verified fallbacks
        val prioritized = discovered.sortedByDescending { it }
        val fallbacks = listOf("gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-flash")
        return (prioritized + fallbacks).distinct()
    }

    private fun parseFlexibleResponse(raw: String): ScanOutcome? {
        // 1. Try clean JSON extraction first
        try {
            val start = raw.indexOf('{')
            val end = raw.lastIndexOf('}')
            if (start != -1 && end > start) {
                val json = JSONObject(raw.substring(start, end + 1))
                val dirRaw = json.optString("direction", "BUY").uppercase()
                val dir = if (dirRaw.contains("SELL") || dirRaw.contains("SHORT")) "SELL" else "BUY"
                val entry = extractDouble(json, "entry")
                val sl = extractDouble(json, "sl")
                val tp = extractDouble(json, "tp")
                if (entry > 0.0 || sl > 0.0 || tp > 0.0) {
                    return ScanOutcome(true, dir, entry, sl, tp)
                }
            }
        } catch (_: Exception) {
        }

        // 2. Regex fallback if model returned key:value text
        val dir = if (raw.contains("SELL", ignoreCase = true) || raw.contains("SHORT", ignoreCase = true)) "SELL" else "BUY"
        val entry = Regex("""entry["']?\s*[:=]\s*([0-9]+\.?[0-9]*)""", RegexOption.IGNORE_CASE).find(raw)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val sl = Regex("""sl["']?\s*[:=]\s*([0-9]+\.?[0-9]*)""", RegexOption.IGNORE_CASE).find(raw)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val tp = Regex("""tp["']?\s*[:=]\s*([0-9]+\.?[0-9]*)""", RegexOption.IGNORE_CASE).find(raw)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

        return if (entry > 0.0 || sl > 0.0 || tp > 0.0) {
            ScanOutcome(true, dir, entry, sl, tp)
        } else null
    }

    private fun extractDouble(json: JSONObject, key: String): Double {
        val v = json.opt(key) ?: return 0.0
        return when (v) {
            is Number -> v.toDouble()
            is String -> v.replace(",", "").replace("$", "").trim().toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }
}
