package com.apexquant.pipsniper.core

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File

object AppUpdater {
    private const val RELEASE_API = "[https://api.github.com/repos/ultraeleven3/PipSniper-Android/releases/tags/latest](https://api.github.com/repos/ultraeleven3/PipSniper-Android/releases/tags/latest)"

    suspend fun downloadAndInstallLatest(
        context: Context,
        onStatus: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            withContext(Dispatchers.Main) { onStatus("Checking GitHub Releases...") }
            val req = Request.Builder()
                .url(RELEASE_API)
                .header("Accept", "application/vnd.github+json")
                .build()

            var apkUrl = ""
            var updatedAt = ""

            MarketDataEngine.client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) {
                    withContext(Dispatchers.Main) { onStatus("Release check failed (HTTP ${res.code})") }
                    return@withContext
                }
                val json = JSONObject(res.body?.string() ?: "{}")
                val assets = json.optJSONArray("assets")
                if (assets != null && assets.length() > 0) {
                    val asset = assets.getJSONObject(0)
                    apkUrl = asset.optString("browser_download_url", "")
                    updatedAt = asset.optString("updated_at", "")
                }
            }

            if (apkUrl.isEmpty()) {
                withContext(Dispatchers.Main) { onStatus("No APK found in latest release yet.") }
                return@withContext
            }

            withContext(Dispatchers.Main) { onStatus("Downloading update ($updatedAt)...") }
            val apkReq = Request.Builder().url(apkUrl).build()
            val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apkFile = File(updateDir, "PipSniper-update.apk")

            MarketDataEngine.client.newCall(apkReq).execute().use { res ->
                val bytes = res.body?.bytes()
                if (!res.isSuccessful || bytes == null) {
                    withContext(Dispatchers.Main) { onStatus("APK download failed.") }
                    return@withContext
                }
                apkFile.writeBytes(bytes)
            }

            withContext(Dispatchers.Main) {
                onStatus("Launching installer...")
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )
                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(installIntent)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onStatus("Update error: ${e.message}") }
        }
    }
}
