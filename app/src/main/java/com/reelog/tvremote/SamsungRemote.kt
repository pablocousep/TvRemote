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
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.util.concurrent.TimeUnit

/**
 * Samsung Tizen remote transport.
 *
 * Samsung TVs commonly expose a self-signed TLS certificate which may not contain
 * the TV's IP address as a subject alternative name. We therefore use TOFU pinning
 * of the leaf certificate public key, scoped to the TV IP, and verify that pin in
 * both the trust manager and hostname verifier. A changed key is never accepted
 * silently: the user must forget the pairing and approve the TV again.
 */
class SamsungRemote(
    context: Context,
    private val onStatus: (String) -> Unit,
    private val onConnectionChanged: (Boolean) -> Unit = {}
) {
    private val prefs = context.getSharedPreferences("samsung_tv_pairing", Context.MODE_PRIVATE)
    private var socket: WebSocket? = null
    private var currentHost: String? = null
    private var client: OkHttpClient? = null
    private var token: String? = null
    @Volatile private var connected = false

    fun isConnected(): Boolean = connected

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

        val pinKey = "spki_$host"
        val trustManager = pinnedTrustManager(host, pinKey)
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
        }
        val hostnameVerifier = HostnameVerifier { requestedHost, session ->
            if (!requestedHost.equals(host, ignoreCase = true)) {
                false
            } else {
                try {
                    val peer = session.peerCertificates.firstOrNull() as? X509Certificate
                    val expected = prefs.getString(pinKey, null)
                    peer != null && expected != null &&
                        publicKeyFingerprint(peer).equals(expected, ignoreCase = true)
                } catch (_: Exception) {
                    false
                }
            }
        }

        val okClient = OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier(hostnameVerifier)
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
                if (webSocket !== socket) return
                connected = true
                onConnectionChanged(true)
                onStatus("Conexión TLS verificada. Acepta el acceso en la pantalla del televisor si aparece.")
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
                if (webSocket !== socket) return
                connected = false
                socket = null
                onConnectionChanged(false)
                val causes = generateSequence(t as Throwable?) { it.cause }
                    .mapNotNull { it.message }.joinToString(" | ")
                val message = when {
                    causes.contains("public key changed", true) ||
                        causes.contains("pin mismatch", true) ->
                        "La identidad segura de esta TV ha cambiado. Pulsa Olvidar y vuelve a emparejarla."
                    causes.contains("certificate", true) || causes.contains("SSL", true) ||
                        causes.contains("hostname", true) || causes.contains("peer", true) ->
                        "No se pudo verificar el certificado TLS de la TV. Comprueba que sea tu televisor y vuelve a emparejarlo."
                    else -> "No se pudo conectar: ${t.localizedMessage ?: "error de red"}"
                }
                onStatus(message)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (webSocket !== socket) return
                connected = false
                socket = null
                onConnectionChanged(false)
                onStatus("Conexión cerrada")
            }
        })
    }

    private fun publicKeyFingerprint(certificate: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(certificate.publicKey.encoded)
        return digest.joinToString("") { "%02X".format(it) }
    }

    private fun pinnedTrustManager(host: String, pinKey: String): X509TrustManager =
        object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                throw java.security.cert.CertificateException("Client certificates are not accepted")
            }

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                if (chain.isNullOrEmpty()) {
                    throw java.security.cert.CertificateException("Empty Samsung TV certificate chain")
                }
                val fingerprint = publicKeyFingerprint(chain[0])
                val known = prefs.getString(pinKey, null)
                if (known == null) {
                    // Commit synchronously so the hostname verifier sees the pin in this handshake.
                    if (!prefs.edit().putString(pinKey, fingerprint).commit()) {
                        throw java.security.cert.CertificateException("Could not save Samsung TV public key pin")
                    }
                    onStatus("Identidad segura de la TV guardada. Confirma el permiso en el televisor.")
                } else if (!known.equals(fingerprint, ignoreCase = true)) {
                    throw java.security.cert.CertificateException("Samsung TV public key changed for $host")
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

    /** Launches an installed Tizen app (Netflix, Prime Video…) by its Samsung app id. */
    fun launchApp(appId: String) {
        val payload = JSONObject().apply {
            put("method", "ms.channel.emit")
            put("params", JSONObject().apply {
                put("event", "ed.apps.launch")
                put("to", "host")
                put("data", JSONObject().apply {
                    put("appId", appId)
                    put("action_type", "DEEP_LINK")
                })
            })
        }.toString()
        if (socket?.send(payload) != true) onStatus("No hay conexión activa con el televisor")
    }

    fun forgetPairing(ip: String) {
        val host = ip.trim()
        prefs.edit()
            .remove("token_$host")
            .remove("cert_$host") // Remove legacy certificate pin from earlier builds.
            .remove("spki_$host")
            .commit()
        if (currentHost == host) close()
        onStatus("Emparejamiento local eliminado. La próxima conexión pedirá autorización.")
    }

    fun close() {
        val wasConnected = connected
        connected = false
        val old = socket
        socket = null
        old?.close(1000, "User disconnected")
        if (wasConnected) onConnectionChanged(false)
        client?.dispatcher?.executorService?.shutdown()
        client = null
    }
}
