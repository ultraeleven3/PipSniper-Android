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
        val maxDim = 800
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
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, baos)
        val base64Image = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

        val prompt = """
            Analyze this trading chart screenshot. Find the Buy or Sell position tool (green/red risk-reward box) or marked Entry, Stop Loss (SL), and Take Profit (TP) price levels on the right price axis.
            Reply ONLY with valid JSON and nothing else:
            {"direction":"BUY","entry":0.0,"sl":0.0,"tp":0.0}
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
                put("responseMimeType", "application/json")
            })
        }

        val models = listOf("gemini-3.6-flash", "gemini-3.1-flash-lite", "gemini-3.8-flash")
        var lastError = "No response from Gemini"

        for (model in models) {
            onProgress("Scanning with \$model...")
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
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
                            .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

                        val json = JSONObject(raw)
                        return ScanOutcome(
                            success = true,
                            direction = json.optString("direction", "BUY").uppercase(),
                            entry = json.optDouble("entry", 0.0),
                            sl = json.optDouble("sl", 0.0),
                            tp = json.optDouble("tp", 0.0)
                        )
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
            delay(600)
        }

        return ScanOutcome(success = false, errorMessage = lastError)
    }
}
