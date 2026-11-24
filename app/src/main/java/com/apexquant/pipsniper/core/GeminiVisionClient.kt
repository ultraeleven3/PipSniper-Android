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
    suspend fun scanChart(apiKey: String, bitmap: Bitmap, onProgress: (String) -> Unit): ScanOutcome {
        val cleanKey = apiKey.trim()
        val maxDim = 1280
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
        scaled.compress(Bitmap.CompressFormat.JPEG, 88, baos)
        val base64Image = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

        val prompt = """
            You are an expert XAUUSD / Gold chart reader. Look closely at this trading chart screenshot (TradingView or MetaTrader 5).
            Identify the trade setup (Buy/Long or Sell/Short position tool box, horizontal lines, or marked Entry, Stop Loss / SL, and Take Profit / TP levels on the right price axis).
            If a Long/Buy box is shown (green profit zone above entry, red stop zone below entry), direction is BUY.
            If a Short/Sell box is shown (green profit zone below entry, red stop zone above entry), direction is SELL.
            Return ONLY raw JSON with numeric values (no markdown, no explanation):
            {"direction":"BUY","entry":2750.50,"sl":2747.00,"tp":2758.00}
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
                put("temperature", 0.1)
            })
        }

        val models = listOf(
            "gemini-2.5-flash",
            "gemini-2.0-flash",
            "gemini-3.6-flash",
            "gemini-3.1-flash-lite",
            "gemini-3.8-flash"
        )
        var lastError = "No response from Gemini"

        for (model in models) {
            onProgress("Analyzing chart with $model...")
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

                        val parsed = parseFlexibleJson(raw)
                        if (parsed != null && (parsed.entry > 0.0 || parsed.sl > 0.0 || parsed.tp > 0.0)) {
                            return parsed
                        } else {
                            lastError = "$model returned unreadable levels: ${raw.take(80)}"
                        }
                    } else {
                        val err = try {
                            JSONObject(bodyStr).getJSONObject("error").getString("message")
                        } catch (_: Exception) {
                            "HTTP ${res.code}"
                        }
                        lastError = "$model -> $err"
                    }
                }
            } catch (e: Exception) {
                lastError = "$model -> ${e.message}"
            }
            delay(400)
        }

        return ScanOutcome(success = false, errorMessage = lastError)
    }

    private fun parseFlexibleJson(raw: String): ScanOutcome? {
        return try {
            val start = raw.indexOf('{')
            val end = raw.lastIndexOf('}')
            if (start == -1 || end <= start) return null
            val json = JSONObject(raw.substring(start, end + 1))

            val dir = json.optString("direction", "BUY").uppercase().let {
                if (it.contains("SELL") || it.contains("SHORT")) "SELL" else "BUY"
            }
            val entry = extractDouble(json, "entry")
            val sl = extractDouble(json, "sl")
            val tp = extractDouble(json, "tp")

            ScanOutcome(success = true, direction = dir, entry = entry, sl = sl, tp = tp)
        } catch (_: Exception) {
            null
        }
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
