package com.example.transport

import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class WebSocketClient {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null

    fun connect(url: String): Flow<ConnectionState> = callbackFlow {
        try {
            webSocket?.close(1000, "Reconnecting")
        } catch (_: Exception) {}
        webSocket = null

        val request = try {
            Request.Builder().url(url).build()
        } catch (e: Exception) {
            trySend(ConnectionState.Error(e.message ?: "Invalid WebSocket URL"))
            close()
            return@callbackFlow
        }
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                trySend(ConnectionState.Connected)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // Future expansion
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                trySend(ConnectionState.Disconnected)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("WebSocketClient", "Error: ${t.message}", t)
                trySend(ConnectionState.Error(t.message ?: "Connection failed"))
            }
        })
        
        trySend(ConnectionState.Connecting)

        awaitClose {
            try {
                webSocket?.close(1000, "User closed connection")
            } catch (_: Exception) {}
            webSocket = null
        }
    }

    fun send(message: String): Boolean {
        return webSocket?.send(message) ?: false
    }

    fun disconnect() {
        webSocket?.close(1000, "Disconnected by user")
        webSocket = null
    }
}

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}
