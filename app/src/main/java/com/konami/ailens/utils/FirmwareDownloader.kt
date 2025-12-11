package com.konami.ailens.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Native firmware downloader utility
 * Reference iOS: LocalSwiftPM/Downloader/Sources/Downloader/Downloader.swift
 */
object FirmwareDownloader {

    /**
     * Download firmware file
     * @param url Download URL
     * @param savePath Save path
     * @param onProgress Progress callback (bytesDownloaded, totalBytes, progress 0.0-1.0)
     * @return Downloaded file path, throws exception on failure
     */
    suspend fun download(
        url: String,
        savePath: File,
        onProgress: (Long, Long, Float) -> Unit = { _, _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        Log.e("FirmwareDownloader", "Start downloading: $url")
        Log.e("FirmwareDownloader", "Save path: ${savePath.absolutePath}")

        // Create parent directory
        savePath.parentFile?.mkdirs()

        var connection: HttpURLConnection? = null
        var outputStream: FileOutputStream? = null

        try {
            // Open connection
            connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 30_000
            connection.readTimeout = 30_000
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP error: $responseCode")
            }

            // Get file size
            val fileSize = connection.contentLengthLong
            Log.e("FirmwareDownloader", "File size: $fileSize bytes")

            val inputStream = connection.inputStream
            outputStream = FileOutputStream(savePath)

            val buffer = ByteArray(8192)
            var totalBytesRead = 0L
            var bytesRead: Int

            // Download and write to file
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead

                // Calculate progress
                val progress = if (fileSize > 0) {
                    totalBytesRead.toFloat() / fileSize.toFloat()
                } else {
                    0f
                }

                // Progress callback
                onProgress(totalBytesRead, fileSize, progress)
            }

            outputStream.flush()
            Log.e("FirmwareDownloader", "Download completed: ${savePath.absolutePath}")

            savePath

        } catch (e: Exception) {
            Log.e("FirmwareDownloader", "Download failed: ${e.message}")
            // Delete incomplete file
            if (savePath.exists()) {
                savePath.delete()
            }
            throw e
        } finally {
            outputStream?.close()
            connection?.disconnect()
        }
    }

    /**
     * Cancel download (delete incomplete file)
     */
    fun cancelDownload(savePath: File) {
        if (savePath.exists()) {
            savePath.delete()
            Log.e("FirmwareDownloader", "Download cancelled and file deleted: ${savePath.absolutePath}")
        }
    }
}
