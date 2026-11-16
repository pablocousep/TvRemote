package com.reelog.tvremote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URL

/** What the TV's local REST API (port 8001) says about itself. */
data class TvProbe(
    val reachable: Boolean,
    /** "on", "standby" or null when the model does not report it (pre-2018 TVs). */
    val powerState: String? = null,
    val mac: String? = null,
    val networkType: String? = null
)

object WakeOnLan {
    private val MAC_REGEX = Regex("([0-9A-F]{2}:){5}[0-9A-F]{2}")

    fun normalizeMac(input: String): String? {
        val hex = input.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }
        if (hex.length != 12) return null
        return hex.chunked(2).joinToString(":").takeIf { it.matches(MAC_REGEX) }
    }

    /** Asks the TV for its state. Unreachable usually means deep standby (network off). */
    suspend fun probe(ip: String, timeoutMs: Int = 900): TvProbe = withContext(Dispatchers.IO) {
        try {
            val connection = (URL("http://$ip:8001/api/v2/").openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (connection.responseCode !in 200..299) return@withContext TvProbe(false)
                val device = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                    .optJSONObject("device") ?: return@withContext TvProbe(true)
                fun str(key: String) = device.optString(key).takeIf { it.isNotBlank() && it != "null" }
                TvProbe(
                    reachable = true,
                    powerState = str("PowerState")?.lowercase(),
                    mac = sequenceOf("wifiMac", "macAddress", "ethernetMac", "wiredMac")
                        .mapNotNull { str(it)?.let(::normalizeMac) }.firstOrNull(),
                    networkType = str("networkType")
                )
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            TvProbe(false)
        }
    }

    /**
     * Launches an app through the TV's REST API. Returns true when the TV accepted it, false
     * when it answered that the app does not exist, and null when the API is not usable.
     */
    suspend fun restLaunch(ip: String, appId: String): Boolean? = withContext(Dispatchers.IO) {
        try {
            val c = (URL("http://$ip:8001/api/v2/applications/$appId").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 1500
                readTimeout = 2500
                doOutput = true
                setFixedLengthStreamingMode(0)
            }
            try {
                c.outputStream.close()
                when (c.responseCode) {
                    in 200..299 -> true
                    404 -> false
                    else -> null
                }
            } finally {
                c.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Sends one round of magic packets. Every round goes to the global broadcast, to the real
     * broadcast address of each local IPv4 network (not just a guessed /24) and unicast to the
     * TV's last known IP, on UDP ports 9 and 7. When possible the socket is bound to the Wi-Fi
     * network so the packets do not leave through mobile data.
     *
     * Returns a short description of where the packets went, for the diagnostics log.
     */
    suspend fun sendRound(context: Context, macAddress: String, tvIp: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val mac = normalizeMac(macAddress) ?: error("La dirección MAC no es válida")
                val macBytes = mac.split(':').map { it.toInt(16).toByte() }
                val packet = ByteArray(102)
                for (i in 0 until 6) packet[i] = 0xFF.toByte()
                for (i in 0 until 16) for (j in 0 until 6) packet[6 + i * 6 + j] = macBytes[j]

                val targets = linkedSetOf<InetAddress>(InetAddress.getByName("255.255.255.255"))
                localBroadcasts().forEach { targets += it }
                val parts = tvIp.split(".").mapNotNull { it.toIntOrNull() }
                if (parts.size == 4 && parts.all { it in 0..255 }) {
                    targets += InetAddress.getByName("${parts[0]}.${parts[1]}.${parts[2]}.255")
                    targets += InetAddress.getByName(tvIp)
                }

                DatagramSocket().use { socket ->
                    socket.broadcast = true
                    val wifi = wifiNetwork(context)
                    if (wifi != null) runCatching { wifi.bindSocket(socket) }
                    for (address in targets) for (port in intArrayOf(9, 7)) {
                        runCatching { socket.send(DatagramPacket(packet, packet.size, address, port)) }
                    }
                }
                targets.joinToString { it.hostAddress ?: "?" } + " (puertos 9 y 7)"
            }
        }

    /**
     * Full power-on attempt: magic packets every ~1.2 s while polling the TV, for up to
     * [timeoutMs]. Returns the probe that answered, or null if the TV never came up.
     */
    suspend fun wakeAndWait(
        context: Context, mac: String, ip: String,
        timeoutMs: Long = 22_000, log: (String) -> Unit = {}
    ): TvProbe? {
        val start = System.currentTimeMillis()
        var round = 0
        while (System.currentTimeMillis() - start < timeoutMs) {
            round++
            val sent = sendRound(context, mac, ip)
            if (round == 1) log(sent.fold({ "Paquetes enviados a $it" }, { "Error al enviar: ${it.localizedMessage}" }))
            if (sent.isFailure) return null
            val probe = probe(ip, 700)
            if (probe.reachable) {
                log("La TV responde tras ${(System.currentTimeMillis() - start) / 1000.0}s (estado: ${probe.powerState ?: "no informado"})")
                return probe
            }
            delay(500)
        }
        log("La TV no respondió en ${timeoutMs / 1000}s ($round rondas de paquetes)")
        return null
    }

    private fun localBroadcasts(): List<InetAddress> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.interfaceAddresses }
            .filter { it.address is Inet4Address }
            .mapNotNull { it.broadcast }
    }.getOrDefault(emptyList())

    private fun wifiNetwork(context: Context): Network? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        @Suppress("DEPRECATION")
        return cm.allNetworks.firstOrNull { net ->
            cm.getNetworkCapabilities(net)?.let {
                it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            } == true
        }
    }
}
