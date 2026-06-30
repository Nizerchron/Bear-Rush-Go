package com.bearrushbuilder.app.data

import android.content.Context
import android.os.Environment
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DownloadManager {
    private val client by lazy { HttpClient(Android) }

    // ponytail: path hardcoded sesuai request user. Upgrade path: jadikan configurable via settings
    private val baseDir = File(
        Environment.getExternalStorageDirectory(),
        "Geokar_Mods/SBA/saved_scenes"
    )

    data class Progress(
        val bytesDownloaded: Long = 0,
        val totalBytes: Long = -1,
        val isCompleted: Boolean = false,
        val error: String? = null
    )

    /**
     * Download file dari URL dan simpan ke Geokar_Mods/SBA/saved_scenes/[fileName].
     */
    suspend fun download(
        url: String,
        fileName: String,
        onProgress: (Progress) -> Unit
    ) = withContext(Dispatchers.IO) {
        baseDir.mkdirs()
        val file = File(baseDir, fileName)
        try {
            val response = client.get(url)
            val totalBytes = response.headers["Content-Length"]?.toLongOrNull() ?: -1L
            onProgress(Progress(totalBytes = totalBytes))
            val channel: ByteReadChannel = response.bodyAsChannel()
            var downloaded = 0L
            file.outputStream().use { output ->
                val buffer = ByteArray(8192)
                while (!channel.isClosedForRead) {
                    val bytesRead = channel.readAvailable(buffer)
                    if (bytesRead == -1) break
                    if (bytesRead > 0) {
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        onProgress(Progress(downloaded, totalBytes))
                    }
                }
            }
            onProgress(Progress(downloaded, totalBytes, isCompleted = true))
        } catch (e: Exception) {
            onProgress(Progress(error = e.message ?: "Download gagal"))
        }
    }

    suspend fun downloadToUri(
        url: String,
        context: Context,
        uri: android.net.Uri,
        onProgress: (Progress) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val response = client.get(url)
            val totalBytes = response.headers["Content-Length"]?.toLongOrNull() ?: -1L
            onProgress(Progress(totalBytes = totalBytes))
            val channel: ByteReadChannel = response.bodyAsChannel()
            var downloaded = 0L
            context.contentResolver.openOutputStream(uri)?.use { output ->
                val buffer = ByteArray(8192)
                while (!channel.isClosedForRead) {
                    val bytesRead = channel.readAvailable(buffer)
                    if (bytesRead == -1) break
                    if (bytesRead > 0) {
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        onProgress(Progress(downloaded, totalBytes))
                    }
                }
            }
            onProgress(Progress(downloaded, totalBytes, isCompleted = true))
        } catch (e: Exception) {
            onProgress(Progress(error = e.message ?: "Download gagal"))
        }
    }

    /**
     * Download file ke folder Downloads publik (untuk APK).
     * Google Drive >100MB butuh bypass confirm page.
     */
    suspend fun downloadToDownloads(
        url: String,
        fileName: String,
        onProgress: (Progress) -> Unit
    ) = withContext(Dispatchers.IO) {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        downloadsDir.mkdirs()
        val file = File(downloadsDir, fileName)

        try {
            // Ekstrak ID dari berbagai format URL Google Drive
            val id = Regex("""[-\w]{25,}""").find(url)?.value
                ?: throw Exception("ID file tidak ditemukan di URL")

            // Ponytail: Google Drive >100MB return confirm page.
            // Dua langkah: (1) GET /uc dapat confirm token, (2) GET dengan confirm token dapat file asli.
            val confirmUrl = "https://docs.google.com/uc?export=download&id=$id"
            val confirmResp = client.get(confirmUrl)
            val html = confirmResp.bodyAsText()

            val confirmToken = Regex("""confirm=([\w-]+)""").find(html)?.groupValues?.get(1)

            val downloadUrl = if (confirmToken != null) {
                "https://docs.google.com/uc?export=download&id=$id&confirm=$confirmToken"
            } else {
                // File kecil — tidak perlu confirm
                confirmUrl
            }

            val response = client.get(downloadUrl)
            val totalBytes = response.headers["Content-Length"]?.toLongOrNull() ?: -1L
            onProgress(Progress(totalBytes = totalBytes))

            val channel: ByteReadChannel = response.bodyAsChannel()
            var downloaded = 0L
            file.outputStream().use { output ->
                val buffer = ByteArray(8192)
                while (!channel.isClosedForRead) {
                    val bytesRead = channel.readAvailable(buffer)
                    if (bytesRead == -1) break
                    if (bytesRead > 0) {
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        onProgress(Progress(downloaded, totalBytes))
                    }
                }
            }
            onProgress(Progress(downloaded, totalBytes, isCompleted = true))
        } catch (e: Exception) {
            onProgress(Progress(error = e.message ?: "Download gagal"))
        }
    }

    fun isDownloaded(fileName: String): Boolean {
        return File(baseDir, fileName).exists()
    }
}