package com.reelog.tvremote

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.URL
import java.util.concurrent.TimeUnit

data class SamsungTv(
    val ip: String,
    val name: String,
    val model: String,
    val macAddress: String? = null,
    /** User's own note for this TV, e.g. "Tele salón". */
    val label: String? = null,
    /** Wi-Fi network (SSID) the phone was on the last time it reached this TV. */
    val ssid: String? = null
) {
    /** What the app shows: the user's note if there is one, else the TV's name. */
    val displayName: String get() = label?.takeIf { it.isNotBlank() } ?: name
}

class SamsungTvDiscovery(private val context: Context) {
    suspend fun discover(): List<SamsungTv> = withContext(Dispatchers.IO) {
        val candidates = linkedSetOf<String>()
        candidates += discoverSsdp()
        candidates += localSubnetHosts()
        val semaphore = Semaphore(48)
        coroutineScope {
            candidates.map { ip ->
                async(Dispatchers.IO) {
                    semaphore.withPermit { probeSamsungTv(ip) }
                }
            }.awaitAll().filterNotNull().distinctBy { it.ip }.sortedBy { it.name.lowercase() }
        }
    }

    private fun discoverSsdp(): Set<String> {
        val found = linkedSetOf<String>()
        var multicastLock: WifiManager.MulticastLock? = null
        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifi.createMulticastLock("reeltv-ssdp").apply { setReferenceCounted(false); acquire() }
            DatagramSocket().use { socket ->
                socket.soTimeout = 1_300
                socket.broadcast = true
                val targets = listOf(
                    "urn:samsung.com:device:RemoteControlReceiver:1",
                    "ssdp:all"
                )
                for (target in targets) {
                    val request = (
                        "M-SEARCH * HTTP/1.1\r\n" +
                        "HOST: 239.255.255.250:1900\r\n" +
                        "MAN: \"ssdp:discover\"\r\n" +
                        "MX: 1\r\n" +
                        "ST: $target\r\n\r\n"
                    ).toByteArray()
                    socket.send(DatagramPacket(request, request.size, InetAddress.getByName("239.255.255.250"), 1900))
                }
                val deadline = System.currentTimeMillis() + 1_500
                while (System.currentTimeMillis() < deadline) {
                    try {
                        val buffer = ByteArray(8_192)
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val response = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        val server = response.substringAfter("SERVER:", "").substringBefore("\r\n")
                        val location = response.substringAfter("LOCATION:", "").substringBefore("\r\n")
                        val sourceIp = packet.address.hostAddress
                        if (server.contains("Samsung", true) || location.contains(":9197/") || response.contains("samsung.com:device:RemoteControlReceiver", true)) {
                            if (sourceIp != null) found += sourceIp
                        }
                    } catch (_: java.net.SocketTimeoutException) {
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("TvDiscovery", "SSDP discovery unavailable: ${e.message}")
        } finally {
            try { if (multicastLock?.isHeld == true) multicastLock.release() } catch (_: Exception) {}
        }
        return found
    }

    private fun localSubnetHosts(): Set<String> {
        val result = linkedSetOf<String>()
        val addresses = try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val props: LinkProperties? = cm.getLinkProperties(cm.activeNetwork)
            props?.linkAddresses?.mapNotNull { it.address as? Inet4Address }.orEmpty()
        } catch (_: Exception) { emptyList() }

        val wifiInfo = try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val dhcp = wifi.dhcpInfo
            if (dhcp.ipAddress != 0) {
                val bytes = byteArrayOf(
                    (dhcp.ipAddress and 0xff).toByte(),
                    (dhcp.ipAddress shr 8 and 0xff).toByte(),
                    (dhcp.ipAddress shr 16 and 0xff).toByte(),
                    (dhcp.ipAddress shr 24 and 0xff).toByte()
                )
                listOf(InetAddress.getByAddress(bytes) as Inet4Address)
            } else emptyList()
        } catch (_: Exception) { emptyList() }

        val all = (addresses + wifiInfo).distinctBy { it.hostAddress }
        for (address in all) {
            val ipBytes = address.address.map { it.toInt() and 0xff }
            if (ipBytes.size != 4 || ipBytes[0] == 127 || ipBytes[0] == 169) continue
            val prefix = try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                cm.getLinkProperties(cm.activeNetwork)?.linkAddresses
                    ?.firstOrNull { it.address == address }?.prefixLength ?: 24
            } catch (_: Exception) { 24 }
            if (prefix !in 16..30) continue
            val mask = -1 shl (32 - prefix)
            val local = ((ipBytes[0] shl 24) or (ipBytes[1] shl 16) or (ipBytes[2] shl 8) or ipBytes[3])
            val network = local and mask
            val broadcast = network or mask.inv()
            // Avoid accidentally scanning very large networks.
            if (broadcast - network > 1024) continue
            for (host in (network + 1) until broadcast) {
                if (host == local) continue
                val ip = "${(host ushr 24) and 255}.${(host ushr 16) and 255}.${(host ushr 8) and 255}.${host and 255}"
                result += ip
            }
        }
        return result
    }

    private fun probeSamsungTv(ip: String): SamsungTv? {
        return try {
            val connection = (URL("http://$ip:8001/api/v2/").openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 450
                readTimeout = 450
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (connection.responseCode !in 200..299) return null
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(body)
                val device = root.optJSONObject("device") ?: return null
                val type = device.optString("type")
                val description = device.optString("description")
                val name = device.optString("name").takeIf { it.isNotBlank() && it != "null" }
                    ?: device.optString("modelName").takeIf { it.isNotBlank() && it != "null" }
                    ?: "Samsung Smart TV"
                val model = device.optString("modelName").takeIf { it.isNotBlank() && it != "null" }
                    ?: device.optString("model").takeIf { it.isNotBlank() && it != "null" }
                    ?: "Modelo no disponible"
                val mac = sequenceOf("wifiMac", "macAddress", "ethernetMac", "wiredMac")
                    .mapNotNull { key -> device.optString(key).takeIf { it.isNotBlank() && it != "null" } }
                    .firstOrNull()?.uppercase()
                    ?.takeIf { it.matches(Regex("([0-9A-F]{2}:){5}[0-9A-F]{2}")) }
                if (!type.contains("Samsung", true) && !description.contains("Samsung", true) && !name.contains("Samsung", true)) return null
                SamsungTv(ip, name, model, mac)
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }
}
