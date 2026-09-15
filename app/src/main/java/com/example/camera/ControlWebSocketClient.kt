package com.example.camera

import android.util.Log
import com.example.transport.ConnectionState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class ControlWebSocketClient {
    companion object {
        private const val TAG = "ControlWebSocketClient"
        private const val MAX_LOGS = 100
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var isOpen: Boolean = false
    
    private val _connectionLogs = MutableStateFlow<List<String>>(emptyList())
    val connectionLogs: StateFlow<List<String>> = _connectionLogs.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<CameraControlMessage>(extraBufferCapacity = 16)
    val incomingMessages: SharedFlow<CameraControlMessage> = _incomingMessages.asSharedFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private fun appendLog(line: String) {
        val timestamp = timeFormat.format(Date())
        val formatted = "[$timestamp] [CONTROL] $line"
        Log.i(TAG, formatted)
        val current = _connectionLogs.value.toMutableList()
        current.add(formatted)
        if (current.size > MAX_LOGS) {
            current.removeAt(0)
        }
        _connectionLogs.value = current
    }

    fun connect(controlUrl: String, helloJson: String? = null): Flow<ConnectionState> = callbackFlow {
        try {
            webSocket?.close(1000, "Reconnecting")
        } catch (_: Exception) {}
        webSocket = null
        isOpen = false

        val request = try {
            Request.Builder().url(controlUrl).build()
        } catch (e: Exception) {
            val errorMsg = "Invalid Control WebSocket URL: ${e.message}"
            appendLog("[FAILURE] $errorMsg")
            trySend(ConnectionState.Error(errorMsg, e))
            close()
            return@callbackFlow
        }

        appendLog("[CONNECTING] $controlUrl")
        Log.d(TAG, "CONNECTING TO: $controlUrl")
        trySend(ConnectionState.Connecting)

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isOpen = true
                // OkHttp Lifecycle: OPEN
                appendLog("[OPEN] Connected to $controlUrl (HTTP ${response.code} ${response.message})")
                if (helloJson != null) {
                    webSocket.send(helloJson)
                }
                trySend(ConnectionState.Connected)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val message = json.decodeFromString<CameraControlMessage>(text)
                    _incomingMessages.tryEmit(message)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse control message: $text", e)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                isOpen = false
                appendLog("[CLOSING] code=$code, reason='$reason'")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isOpen = false
                appendLog("[CLOSED] code=$code, reason='$reason'")
                trySend(ConnectionState.Disconnected)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isOpen = false
                val respInfo = if (response != null) " (HTTP ${response.code})" else ""
                val errorMsg = "${t.javaClass.simpleName}: ${t.message ?: "Control connection failure"}$respInfo"
                appendLog("[FAILURE] $errorMsg")
                Log.e(TAG, "[FAILURE] $errorMsg", t)
                trySend(ConnectionState.Error(errorMsg, t))
            }
        })

        awaitClose {
            try {
                webSocket?.close(1000, "Control client closed")
            } catch (_: Exception) {}
            webSocket = null
        isOpen = false
        }
    }

    fun sendResponse(jsonString: String): Boolean {
        val ws = webSocket ?: return false
        return ws.send(jsonString)
    }

    fun disconnect() {
        appendLog("[CLOSING] Disconnect requested")
        try {
            webSocket?.close(1000, "Disconnected by user")
        } catch (_: Exception) {}
        webSocket = null
        isOpen = false
        appendLog("[CLOSED] Disconnected")
    }

    fun isConnected(): Boolean {
        return isOpen
    }
}
