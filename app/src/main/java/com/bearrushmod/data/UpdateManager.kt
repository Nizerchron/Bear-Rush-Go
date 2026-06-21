package com.bearrushmod.data

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

// ponytail: satu file untuk auto-update via GitHub
// Upgrade path: pake in-app update Google Play kalau sudah rilis di Play Store
object UpdateManager {
    private const val VERSION_URL = "https://raw.githubusercontent.com/Nizerchron/Bear-Rush-Go/main/version.json"
    private const val APK_NAME = "BearRushMod.apk"
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(Android)

    @Serializable
    data class VersionInfo(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String,
        val changelog: String = ""
    )

    suspend fun checkAndShowUpdate(activity: Activity, currentVersionCode: Int) {
        try {
            val response = client.get(VERSION_URL)
            val info = json.decodeFromString<VersionInfo>(response.bodyAsText())
            if (info.versionCode > currentVersionCode) {
                withContext(Dispatchers.Main) {
                    showUpdateDialog(activity, info)
                }
            }
        } catch (_: Exception) {
            // ponytail: silent fail — jangan ganggu UX
        }
    }

    private fun showUpdateDialog(activity: Activity, info: VersionInfo) {
        AlertDialog.Builder(activity).apply {
            setTitle("Update Tersedia v${info.versionName}")
            setMessage(buildString {
                append("Versi baru tersedia! Download sekarang?\n\n")
                if (info.changelog.isNotBlank()) {
                    append("📋 Perubahan:\n${info.changelog}")
                }
            })
            setPositiveButton("Update") { _, _ -> downloadAndInstall(activity, info.apkUrl) }
            setNegativeButton("Nanti", null)
            setCancelable(true)
        }.show()
    }

    private fun downloadAndInstall(activity: Activity, apkUrl: String) {
        MainScope().launch {
            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                downloadsDir.mkdirs()
                val file = File(downloadsDir, APK_NAME)

                withContext(Dispatchers.IO) {
                    val response = client.get(apkUrl)
                    val channel: ByteReadChannel = response.bodyAsChannel()
                    file.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        while (!channel.isClosedForRead) {
                            val bytesRead = channel.readAvailable(buffer)
                            if (bytesRead == -1) break
                            if (bytesRead > 0) {
                                output.write(buffer, 0, bytesRead)
                            }
                        }
                    }
                }

                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(
                        Uri.fromFile(file),
                        "application/vnd.android.package-archive"
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                activity.startActivity(intent)
            } catch (_: Exception) {
                AlertDialog.Builder(activity)
                    .setTitle("Download Gagal")
                    .setMessage("Gagal mendownload update. Coba lagi nanti.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }
}