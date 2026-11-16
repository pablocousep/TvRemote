package com.reelog.tvremote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface

object WakeOnLan {
    suspend fun wake(macAddress: String, tvIp: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val mac = macAddress.trim().replace('-', ':').split(':')
                .map { it.toInt(16).toByte() }
            require(mac.size == 6) { "La dirección MAC no es válida" }
            val packet = ByteArray(102)
            for (i in 0 until 6) packet[i] = 0xFF.toByte()
            for (i in 0 until 16) {
                for (j in 0 until 6) packet[6 + i * 6 + j] = mac[j]
            }
            val targets = linkedSetOf(InetAddress.getByName("255.255.255.255"))
            // Use the actual interface broadcast (supports networks other than /24).
            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { networkInterface ->
                networkInterface.interfaceAddresses.forEach { interfaceAddress ->
                    val broadcast = interfaceAddress.broadcast
                    if (broadcast != null && interfaceAddress.address is java.net.Inet4Address) {
                        targets += broadcast
                    }
                }
            }
            // Also try the TV's conventional /24 broadcast as a fallback.
            val parts = tvIp.split(".").mapNotNull { it.toIntOrNull() }
            if (parts.size == 4 && parts.all { it in 0..255 }) {
                targets += InetAddress.getByName("${parts[0]}.${parts[1]}.${parts[2]}.255")
            }
            DatagramSocket().use { socket ->
                socket.broadcast = true
                repeat(3) {
                    targets.forEach { address ->
                        socket.send(DatagramPacket(packet, packet.size, address, 9))
                    }
                    if (it < 2) Thread.sleep(120)
                }
            }
        }
    }
}
