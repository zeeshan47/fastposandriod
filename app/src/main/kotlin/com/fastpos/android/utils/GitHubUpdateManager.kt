package com.fastpos.android.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.fastpos.android.BuildConfig
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object GitHubUpdateManager {
    data class UpdateInfo(
        val versionName: String,
        val releaseName: String,
        val releaseNotes: String,
        val apkName: String,
        val apkUrl: String,
        val releaseUrl: String
    )

    sealed class CheckResult {
        data class Available(val info: UpdateInfo) : CheckResult()
        data class UpToDate(val currentVersion: String) : CheckResult()
        data class NotConfigured(val message: String) : CheckResult()
        data class Error(val message: String) : CheckResult()
    }

    sealed class InstallResult {
        data object Started : InstallResult()
        data class PermissionRequired(val message: String) : InstallResult()
        data class Error(val message: String) : InstallResult()
    }

    suspend fun checkForUpdate(
        context: Context,
        owner: String = BuildConfig.GITHUB_UPDATE_OWNER,
        repo: String = BuildConfig.GITHUB_UPDATE_REPO
    ): CheckResult = withContext(Dispatchers.IO) {
        if (owner.isBlank() || repo.isBlank()) {
            return@withContext CheckResult.NotConfigured(
                "GitHub updater is not configured. Add owner and repo in app build config."
            )
        }

        try {
            val apiUrl = "https://api.github.com/repos/$owner/$repo/releases/latest"
            val json = httpGet(apiUrl)
            val root = JsonParser.parseString(json).asJsonObject
            val tagName = root.get("tag_name")?.asString.orEmpty()
            val latestVersion = tagName.removePrefix("v").removePrefix("V")
            val currentVersion = currentVersionName(context)

            val assets = root.getAsJsonArray("assets")
            val apkAsset = assets?.firstOrNull { asset ->
                asset.asJsonObject.get("name")?.asString?.endsWith(".apk", ignoreCase = true) == true
            }?.asJsonObject ?: return@withContext CheckResult.Error(
                "Latest GitHub release does not have an APK file."
            )

            if (compareVersions(latestVersion, currentVersion) <= 0) {
                return@withContext CheckResult.UpToDate(currentVersion)
            }

            CheckResult.Available(
                UpdateInfo(
                    versionName = latestVersion,
                    releaseName = root.get("name")?.asString?.takeIf { it.isNotBlank() } ?: tagName,
                    releaseNotes = root.get("body")?.asString.orEmpty(),
                    apkName = apkAsset.get("name").asString,
                    apkUrl = apkAsset.get("browser_download_url").asString,
                    releaseUrl = root.get("html_url")?.asString.orEmpty()
                )
            )
        } catch (e: Exception) {
            CheckResult.Error(e.message ?: "Unable to check GitHub for updates.")
        }
    }

    suspend fun downloadApk(context: Context, info: UpdateInfo): File = withContext(Dispatchers.IO) {
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val safeName = info.apkName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val output = File(updatesDir, safeName)

        val connection = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "FastPOS-Android-Updater")
        }

        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Download failed: HTTP ${connection.responseCode}")
            }
            connection.inputStream.use { input ->
                FileOutputStream(output).use { fileOutput ->
                    input.copyTo(fileOutput)
                }
            }
            output
        } finally {
            connection.disconnect()
        }
    }

    fun installApk(context: Context, apkFile: File): InstallResult {
        if (!apkFile.exists()) return InstallResult.Error("Downloaded APK file was not found.")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settingsIntent)
            return InstallResult.PermissionRequired(
                "Allow app installs for FastPOS, then tap install again."
            )
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return try {
            context.startActivity(installIntent)
            InstallResult.Started
        } catch (e: Exception) {
            InstallResult.Error(e.message ?: "Unable to open Android installer.")
        }
    }

    private fun httpGet(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "FastPOS-Android-Updater")
        }

        return try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("GitHub returned HTTP ${connection.responseCode}")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    @Suppress("DEPRECATION")
    private fun currentVersionName(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"

    private fun compareVersions(left: String, right: String): Int {
        val leftParts = versionParts(left)
        val rightParts = versionParts(right)
        val max = maxOf(leftParts.size, rightParts.size)
        for (index in 0 until max) {
            val leftValue = leftParts.getOrElse(index) { 0 }
            val rightValue = rightParts.getOrElse(index) { 0 }
            if (leftValue != rightValue) return leftValue.compareTo(rightValue)
        }
        return 0
    }

    private fun versionParts(version: String): List<Int> =
        version.split('.', '-', '_')
            .mapNotNull { part -> part.takeWhile { it.isDigit() }.toIntOrNull() }
            .ifEmpty { listOf(0) }
}
