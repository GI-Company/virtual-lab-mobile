package com.example.transport

import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    data class Error(val message: String, val throwable: Throwable? = null) : ConnectionState()
}

class WebSocketClient {
    companion object {
        private const val TAG = "WebSocketClient"
        private const val MAX_LOGS = 100
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null

    private val _connectionLogs = MutableStateFlow<List<String>>(emptyList())
    val connectionLogs: StateFlow<List<String>> = _connectionLogs.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private fun appendLog(line: String) {
        val timestamp = timeFormat.format(Date())
        val formatted = "[$timestamp] $line"
        Log.i(TAG, formatted)
        val current = _connectionLogs.value.toMutableList()
        current.add(formatted)
        if (current.size > MAX_LOGS) {
            current.removeAt(0)
        }
        _connectionLogs.value = current
    }

    fun connect(url: String): Flow<ConnectionState> = callbackFlow {
        try {
            webSocket?.close(1000, "Reconnecting")
        } catch (_: Exception) {}
        webSocket = null

        val request = try {
            Request.Builder().url(url).build()
        } catch (e: Exception) {
            val errorMsg = "Invalid WebSocket URL: ${e.message}"
            appendLog("[FAILURE] $errorMsg")
            trySend(ConnectionState.Error(errorMsg, e))
            close()
            return@callbackFlow
        }

        // OkHttp Lifecycle: CONNECTING
        appendLog("[CONNECTING] $url")
        Log.d(TAG, "CONNECTING TO: $url")

        trySend(ConnectionState.Connecting)

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // OkHttp Lifecycle: OPEN
                appendLog("[OPEN] Connected to $url (HTTP ${response.code} ${response.message})")
                trySend(ConnectionState.Connected)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // Incoming application data
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                // OkHttp Lifecycle: CLOSING
                appendLog("[CLOSING] code=$code, reason='$reason'")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                // OkHttp Lifecycle: CLOSED
                appendLog("[CLOSED] code=$code, reason='$reason'")
                trySend(ConnectionState.Disconnected)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // OkHttp Lifecycle: FAILURE
                val respInfo = if (response != null) " (HTTP ${response.code})" else ""
                val errorMsg = "${t.javaClass.simpleName}: ${t.message ?: "Connection failure"}$respInfo"
                appendLog("[FAILURE] $errorMsg")
                Log.e(TAG, "[FAILURE] $errorMsg", t)
                trySend(ConnectionState.Error(errorMsg, t))
            }
        })

        awaitClose {
            try {
                webSocket?.close(1000, "Connection closed by client")
            } catch (_: Exception) {}
            webSocket = null
        }
    }

    fun send(message: String): Boolean {
        return webSocket?.send(message) ?: false
    }

    fun disconnect() {
        appendLog("[CLOSING] Disconnect requested by user")
        try {
            webSocket?.close(1000, "Disconnected by user")
        } catch (_: Exception) {}
        webSocket = null
        appendLog("[CLOSED] Disconnected")
    }

    fun clearLogs() {
        _connectionLogs.value = emptyList()
    }
}
