package com.konami.ailens.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.HttpURLConnection
import java.net.URL

class HttpException(val statusCode: Int, message: String) : Exception("HTTP $statusCode: $message")

abstract class BaseRequest<T>(
    val url: String,
    val method: Method = Method.GET
) {
    enum class Method { GET, POST }

    open var data: ByteArray? = null
    open val headers: MutableMap<String, String> = mutableMapOf()
    open var needAuthenticate = true
    open val queryParams: Map<String, String> = emptyMap()

    suspend fun execute(): T = withContext(Dispatchers.IO) {
        if (needAuthenticate)
            refreshTokenIfNeed()

        var lastError: Throwable? = null
        var statusCode: Int

        try {
            return@withContext withTimeout(3_000L) {
                val connection = openConnection()
                statusCode = connection.responseCode

                val responseText = if (statusCode >= 400) {
                    connection.errorStream?.bufferedReader()?.use { it.readText() }
                        ?: connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    connection.inputStream.bufferedReader().use { it.readText() }
                }

//                Log.e("BaseRequest", "Response: $statusCode $responseText")

                if (statusCode < 200 || statusCode >= 300)
                    throw HttpException(statusCode, responseText)
                debug(statusCode, responseText)
                val result = parseResponse(statusCode, responseText)
                result
            }
        } catch (e: Throwable) {
            lastError = e
            Log.e("TAG", "e = ${e}")
        }

        throw lastError
    }

    fun executeBlocking(): T = runBlocking { execute() }

    private fun openConnection(): HttpURLConnection {
        val fullUrl = if (queryParams.isNotEmpty()) {
            val paramString = queryParams.entries.joinToString("&") {
                "${it.key}=${java.net.URLEncoder.encode(it.value, "UTF-8")}"
            }
            "$url?$paramString"
        } else {
            url
        }

//        Log.e("BaseRequest", "Request: $method $fullUrl")
        if (data != null) {
//            Log.e("BaseRequest", "Body: ${String(data!!)}")
        }

        val connection = URL(fullUrl).openConnection() as? HttpURLConnection
            ?: throw Exception("Cannot open connection")

        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000

        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.setRequestProperty("Accept", "application/json")
        connection.requestMethod = method.name

        if (needAuthenticate) {
            SessionManager.getAccessToken()?.let {
                connection.setRequestProperty("Authorization", "Bearer $it")
            }
        }

        headers.forEach { (key, value) ->
            connection.setRequestProperty(key, value)
        }

        if (method == Method.POST && data != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Length", data!!.size.toString())
            connection.outputStream.use {
                it.write(data)
                it.flush()
            }
        }

        return connection
    }

    private suspend fun refreshTokenIfNeed() {
        val now = System.currentTimeMillis() / 1000
        val expiresAt = SessionManager.getExpiresAt()
        val remaining = expiresAt - now

        if (remaining <= 600) {
            try {
                val refreshToken = SessionManager.getRefreshToken() ?: return
                val refreshResponse = RefreshTokenRequest(refreshToken).execute()

                val session = refreshResponse.session
                SessionManager.saveTokens(
                    access = session.accessToken,
                    refresh = session.refreshToken,
                    expiresAt = session.expiresAt
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    open fun debug(statusCode: Int, response: String) {

    }

    abstract fun parseResponse(statusCode: Int, response: String): T
}
