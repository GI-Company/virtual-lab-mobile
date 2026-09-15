package com.example.camera

import android.util.Log
import com.example.transport.ConnectionState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class CameraWebSocketClient {
    companion object {
        private const val TAG = "CameraWebSocketClient"
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
        val formatted = "[$timestamp] [CAMERA] $line"
        Log.i(TAG, formatted)
        val current = _connectionLogs.value.toMutableList()
        current.add(formatted)
        if (current.size > MAX_LOGS) {
            current.removeAt(0)
        }
        _connectionLogs.value = current
    }

    fun connect(cameraUrl: String): Flow<ConnectionState> = callbackFlow {
        try {
            webSocket?.close(1000, "Reconnecting camera")
        } catch (_: Exception) {}
        webSocket = null

        val request = try {
            Request.Builder().url(cameraUrl).build()
        } catch (e: Exception) {
            val errorMsg = "Invalid Camera WebSocket URL: ${e.message}"
            appendLog("[FAILURE] $errorMsg")
            trySend(ConnectionState.Error(errorMsg, e))
            close()
            return@callbackFlow
        }

        appendLog("[CONNECTING] $cameraUrl")
        Log.d(TAG, "CONNECTING TO: $cameraUrl")
        trySend(ConnectionState.Connecting)

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                appendLog("[OPEN] Camera stream connected to $cameraUrl (HTTP ${response.code} ${response.message})")
                trySend(ConnectionState.Connected)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                appendLog("[CLOSING] code=$code, reason='$reason'")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                appendLog("[CLOSED] code=$code, reason='$reason'")
                trySend(ConnectionState.Disconnected)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val respInfo = if (response != null) " (HTTP ${response.code})" else ""
                val errorMsg = "${t.javaClass.simpleName}: ${t.message ?: "Camera connection failure"}$respInfo"
                appendLog("[FAILURE] $errorMsg")
                Log.e(TAG, "[FAILURE] $errorMsg", t)
                trySend(ConnectionState.Error(errorMsg, t))
            }
        })

        awaitClose {
            try {
                webSocket?.close(1000, "Camera client closed")
            } catch (_: Exception) {}
            webSocket = null
        }
    }

    /**
     * Sends binary camera frame composed of 4-byte big-endian JSON length + UTF-8 JSON metadata + raw JPEG bytes.
     */
    fun sendBinaryFrame(metadata: CameraFrameMetadata, jpegBytes: ByteArray): Boolean {
        val ws = webSocket ?: return false
        return try {
            val jsonString = Json.encodeToString(metadata)
            val jsonBytes = jsonString.toByteArray(Charsets.UTF_8)
            val totalSize = 4 + jsonBytes.size + jpegBytes.size

            val buffer = ByteBuffer.allocate(totalSize)
            buffer.order(ByteOrder.BIG_ENDIAN)
            buffer.putInt(jsonBytes.size)
            buffer.put(jsonBytes)
            buffer.put(jpegBytes)

            val byteString = buffer.array().toByteString(0, totalSize)
            ws.send(byteString)
        } catch (e: Exception) {
            Log.e(TAG, "Failed sending binary frame: ${e.message}", e)
            false
        }
    }

    fun disconnect() {
        appendLog("[CLOSING] Disconnect requested")
        try {
            webSocket?.close(1000, "Disconnected by user")
        } catch (_: Exception) {}
        webSocket = null
        appendLog("[CLOSED] Disconnected")
    }

    fun isConnected(): Boolean {
        return webSocket != null
    }
}
