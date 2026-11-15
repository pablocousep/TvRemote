package com.reelog.tvremote

import android.content.Context
import android.util.Base64
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.util.concurrent.TimeUnit

/**
 * Samsung Tizen remote transport.
 * Uses WSS/8002 and TOFU certificate pinning per TV/IP.
 */
class SamsungRemote(
    context: Context,
    private val onStatus: (String) -> Unit
) {
    private val prefs = context.getSharedPreferences("samsung_tv_pairing", Context.MODE_PRIVATE)
    private var socket: WebSocket? = null
    private var currentHost: String? = null
    private var client: OkHttpClient? = null
    private var token: String? = null

    fun connect(ip: String, deviceName: String = "Reel TV Android") {
        close()
        val host = ip.trim()
        if (host.isBlank() || host.any { it.isWhitespace() }) {
            onStatus("Introduce una dirección IP válida")
            return
        }
        currentHost = host
        token = prefs.getString("token_$host", null)
        onStatus("Conectando de forma segura a $host…")

        val trustManager = pinnedTrustManager(host)
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
        }
        val okClient = OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(25, TimeUnit.SECONDS)
            .build()
        client = okClient

        val encodedName = Base64.encodeToString(deviceName.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        val tokenPart = token?.let { "&token=" + URLEncoder.encode(it, "UTF-8") } ?: ""
        val url = "wss://$host:8002/api/v2/channels/samsung.remote.control?name=$encodedName$tokenPart"
        val request = Request.Builder().url(url).build()

        socket = okClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onStatus("WebSocket seguro abierto. Acepta el acceso en la pantalla del televisor si aparece.")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val root = JSONObject(text)
                    val data = root.optJSONObject("data")
                    val receivedToken = data?.optString("token")?.takeIf { it.isNotBlank() && it != "null" }
                    if (receivedToken != null) {
                        token = receivedToken
                        prefs.edit().putString("token_$host", receivedToken).apply()
                        onStatus("Televisor emparejado y autorizado")
                    } else if (root.optString("event") == "ms.channel.connect") {
                        onStatus("Conectado al Samsung TV")
                    } else if (root.optString("event") == "ms.channel.unauthorized") {
                        onStatus("El televisor rechazó la autorización. Vuelve a emparejarlo.")
                    }
                } catch (_: Exception) {
                    // Ignore non-JSON events.
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) = Unit

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                socket = null
                val message = if (t.message?.contains("certificate", true) == true || t.message?.contains("pin", true) == true)
                    "Certificado del televisor distinto o no válido. Revisa el emparejamiento."
                else "No se pudo conectar: ${t.localizedMessage ?: "error de red"}"
                onStatus(message)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                socket = null
                onStatus("Conexión cerrada")
            }
        })
    }

    private fun pinnedTrustManager(host: String): X509TrustManager = object : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            throw java.security.cert.CertificateException("Client certificates are not accepted")
        }
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            if (chain.isNullOrEmpty()) throw java.security.cert.CertificateException("Empty certificate chain")
            val digest = MessageDigest.getInstance("SHA-256").digest(chain[0].encoded)
            val fingerprint = digest.joinToString("") { "%02X".format(it) }
            val key = "cert_$host"
            val known = prefs.getString(key, null)
            if (known == null) {
                prefs.edit().putString(key, fingerprint).apply()
                onStatus("Certificado local guardado. Confirma el permiso en el televisor.")
            } else if (!known.equals(fingerprint, ignoreCase = true)) {
                throw java.security.cert.CertificateException("Samsung TV certificate fingerprint changed")
            }
        }
    }

    fun sendKey(key: String) {
        val payload = JSONObject().apply {
            put("method", "ms.remote.control")
            put("params", JSONObject().apply {
                put("Cmd", "Click")
                put("DataOfCmd", key)
                put("Option", "false")
                put("TypeOfRemote", "SendRemoteKey")
            })
        }.toString()
        if (socket?.send(payload) != true) onStatus("No hay conexión activa con el televisor")
    }

    fun sendText(value: String) {
        val payload = JSONObject().apply {
            put("method", "ms.remote.control")
            put("params", JSONObject().apply {
                put("Cmd", "Text")
                put("DataOfCmd", value)
                put("Option", "false")
                put("TypeOfRemote", "SendInputString")
            })
        }.toString()
        if (socket?.send(payload) != true) onStatus("No hay conexión activa con el televisor")
    }

    fun forgetPairing(ip: String) {
        prefs.edit().remove("token_${ip.trim()}").remove("cert_${ip.trim()}").apply()
        if (currentHost == ip.trim()) close()
        onStatus("Emparejamiento local eliminado. La próxima conexión pedirá autorización.")
    }

    fun close() {
        socket?.close(1000, "User disconnected")
        socket = null
        client?.dispatcher?.executorService?.shutdown()
        client = null
    }
}
