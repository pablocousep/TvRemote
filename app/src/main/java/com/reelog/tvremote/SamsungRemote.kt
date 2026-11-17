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
import java.util.UUID

/**
 * Samsung Tizen remote transport.
 *
 * Samsung TVs commonly expose a self-signed TLS certificate which may not contain
 * the TV's IP address as a subject alternative name. We therefore use TOFU pinning
 * of the leaf certificate public key, scoped to the TV IP, and verify that pin in
 * both the trust manager and hostname verifier. A changed key is never accepted
 * silently: the user must forget the pairing and approve the TV again.
 */
/** Name shown on the TV when it asks to allow this remote. */
const val DEVICE_NAME = "TvRemote"

class SamsungRemote(
    context: Context,
    private val onStatus: (String) -> Unit,
    private val onConnectionChanged: (Boolean) -> Unit = {},
    private val onInstalledApps: (List<TvApp>) -> Unit = {},
    private val onAppIcon: (iconPath: String, png: ByteArray) -> Unit = { _, _ -> }
) {
    private val prefs = context.getSharedPreferences("samsung_tv_pairing", Context.MODE_PRIVATE)
    // Stable per-install identity: Samsung can distinguish phones and recognize each
    // one again after an app restart.
    private val installationId: String = prefs.getString("installation_id", null)
        ?: UUID.randomUUID().toString().also { id ->
            check(prefs.edit().putString("installation_id", id).commit()) {
                "Could not persist TvRemote installation identity"
            }
        }
    private val stableDeviceName = DEVICE_NAME + "-" + installationId.take(6).uppercase()
    @Volatile private var socket: WebSocket? = null
    private var currentHost: String? = null
    private var client: OkHttpClient? = null
    private var token: String? = null
    @Volatile private var connected = false

    fun isConnected(): Boolean = connected

    fun connect(ip: String, deviceName: String = stableDeviceName) {
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
                // A TLS/WebSocket handshake is not proof that Samsung authorized this client.
                // Wait for the explicit ms.channel.connect event before enabling controls.
                onStatus("Canal seguro abierto. Esperando la autorización de Samsung…")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val root = JSONObject(text)
                    val event = root.optString("event")
                    val data = root.optJSONObject("data")
                    if (event == "ms.channel.connect") {
                        val receivedToken = data?.optString("token")
                            ?.takeIf { it.isNotBlank() && it != "null" }
                        if (receivedToken != null) {
                            token = receivedToken
                            // Save synchronously before reporting success, so reconnects
                            // always send Samsung's latest authorization token.
                            if (!prefs.edit().putString("token_$host", receivedToken).commit()) {
                                onStatus("Conectado, pero no se pudo guardar el token de Samsung")
                            }
                        }
                        connected = true
                        onConnectionChanged(true)
                        onStatus(if (receivedToken != null) "Televisor emparejado y autorización guardada"
                            else "Conectado al Samsung TV")
                    } else if (event == "ed.installedApp.get") {
                        val list = root.optJSONObject("data")?.optJSONArray("data")
                        if (list != null) {
                            val apps = (0 until list.length()).mapNotNull { i ->
                                val o = list.optJSONObject(i) ?: return@mapNotNull null
                                val id = o.optString("appId").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                                TvApp(
                                    id, o.optString("name").ifBlank { id },
                                    o.optString("icon").takeIf { it.isNotBlank() && it != "null" },
                                    o.optInt("app_type", 0).takeIf { it != 0 }
                                )
                            }
                            onInstalledApps(apps.sortedBy { it.name.lowercase() })
                        }
                    } else if (root.optString("event") == "ed.apps.icon") {
                        val d = root.optJSONObject("data")
                        val path = d?.optString("iconPath").orEmpty()
                        val b64 = d?.optString("imageBase64").orEmpty()
                        if (path.isNotEmpty() && b64.isNotEmpty()) {
                            runCatching { Base64.decode(b64, Base64.DEFAULT) }.getOrNull()?.let { onAppIcon(path, it) }
                        }
                    } else if (event == "ms.channel.unauthorized") {
                        connected = false
                        onConnectionChanged(false)
                        onStatus("Samsung ha rechazado este dispositivo. Comprueba el permiso en la TV y vuelve a conectar.")
                    }
                } catch (_: Exception) {
                    // Ignore non-JSON events.
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) = Unit

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                // The TV is closing (e.g. it was switched off): finish the handshake right away
                // so onClosed fires and the UI shows the connection as down.
                webSocket.close(1000, null)
            }

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

    /**
     * Types [value] into the TV's focused text field. Samsung expects the text itself
     * base64-encoded in "Cmd", with "DataOfCmd" set to "base64".
     */
    fun sendText(value: String) {
        val encoded = Base64.encodeToString(value.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        val payload = JSONObject().apply {
            put("method", "ms.remote.control")
            put("params", JSONObject().apply {
                put("Cmd", encoded)
                put("DataOfCmd", "base64")
                put("Option", "false")
                put("TypeOfRemote", "SendInputString")
            })
        }.toString()
        if (socket?.send(payload) != true) onStatus("No hay conexión activa con el televisor")
    }

    /** Launches an installed Tizen app (Netflix, Prime Video…) by its Samsung app id. */
    fun launchApp(appId: String, actionType: String = "DEEP_LINK") {
        val payload = JSONObject().apply {
            put("method", "ms.channel.emit")
            put("params", JSONObject().apply {
                put("event", "ed.apps.launch")
                put("to", "host")
                put("data", JSONObject().apply {
                    put("appId", appId)
                    put("action_type", actionType)
                })
            })
        }.toString()
        if (socket?.send(payload) != true) onStatus("No hay conexión activa con el televisor")
    }

    /** Asks the TV for its installed apps; the answer arrives through onInstalledApps. */
    fun requestInstalledApps() {
        val payload = JSONObject().apply {
            put("method", "ms.channel.emit")
            put("params", JSONObject().apply {
                put("event", "ed.installedApp.get")
                put("to", "host")
            })
        }.toString()
        socket?.send(payload)
    }

    /** The same TV moved to a new IP: keep its token and certificate pin. */
    fun migratePairing(oldIp: String, newIp: String) {
        val edit = prefs.edit()
        prefs.getString("token_$oldIp", null)?.let { edit.putString("token_$newIp", it) }
        prefs.getString("spki_$oldIp", null)?.let { edit.putString("spki_$newIp", it) }
        edit.apply()
    }

    /** Asks the TV for an app's own icon; it arrives through onAppIcon. */
    fun requestAppIcon(iconPath: String) {
        val payload = JSONObject().apply {
            put("method", "ms.channel.emit")
            put("params", JSONObject().apply {
                put("event", "ed.apps.icon")
                put("to", "host")
                put("data", JSONObject().put("iconPath", iconPath))
            })
        }.toString()
        socket?.send(payload)
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
