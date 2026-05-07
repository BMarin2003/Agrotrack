package com.corall.agrotrack.core.network

import android.util.Log
import com.corall.agrotrack.BuildConfig
import com.corall.agrotrack.core.security.SessionManager
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class WsConnectionState { Connecting, Connected, Disconnected, Reconnecting }

@Singleton
class WebSocketManager @Inject constructor(
    private val client: OkHttpClient,
    private val gson: Gson,
) {
    private val tag = "WebSocketManager"
    private var webSocket: WebSocket? = null
    private var gatewayId: Int = 0
    private val wsId = UUID.randomUUID().toString()

    private val _state = MutableSharedFlow<WsConnectionState>(replay = 1)
    val state: SharedFlow<WsConnectionState> = _state.asSharedFlow()

    // Emite cada mensaje JSON crudo recibido del servidor
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 10

    fun connect(gatewayId: Int) {
        this.gatewayId = gatewayId
        reconnectAttempts = 0
        openSocket()
    }

    private fun openSocket() {
        val token = SessionManager.getToken() ?: return
        val url = "${BuildConfig.WS_BASE_URL}telemetry?token=$token&gateway_id=$gatewayId&wsid=$wsId"

        val request = Request.Builder().url(url).build()
        _state.tryEmit(WsConnectionState.Connecting)

        webSocket = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(ws: WebSocket, response: Response) {
                reconnectAttempts = 0
                _state.tryEmit(WsConnectionState.Connected)
                Log.d(tag, "Conectado gateway=$gatewayId")
            }

            override fun onMessage(ws: WebSocket, text: String) {
                if (text == "pong") return
                _messages.tryEmit(text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(1000, null)
                _state.tryEmit(WsConnectionState.Disconnected)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.w(tag, "Fallo WS: ${t.message}")
                _state.tryEmit(WsConnectionState.Disconnected)
                scheduleReconnect()
            }
        })

        // Keepalive ping cada 25 segundos
        startPingLoop()
    }

    private fun scheduleReconnect() {
        if (reconnectAttempts >= maxReconnectAttempts) {
            Log.e(tag, "Máximo de reconexiones alcanzado")
            return
        }
        val delayMs = minOf(1000L * (1 shl reconnectAttempts), 30_000L)
        reconnectAttempts++
        _state.tryEmit(WsConnectionState.Reconnecting)
        Log.d(tag, "Reconectando en ${delayMs}ms (intento $reconnectAttempts)")
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ openSocket() }, delayMs)
    }

    private fun startPingLoop() {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val pingRunnable = object : Runnable {
            override fun run() {
                webSocket?.send("ping")
                handler.postDelayed(this, 25_000L)
            }
        }
        handler.postDelayed(pingRunnable, 25_000L)
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _state.tryEmit(WsConnectionState.Disconnected)
    }
}
