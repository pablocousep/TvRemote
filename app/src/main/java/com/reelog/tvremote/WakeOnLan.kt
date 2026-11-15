package com.reelog.tvremote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object WakeOnLan {
    suspend fun wake(macAddress: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val mac = macAddress.trim().replace('-', ':').split(':')
                .map { it.toInt(16).toByte() }
            require(mac.size == 6) { "La dirección MAC no es válida" }
            val packet = ByteArray(6 + 16 * 6)
            for (i in 0 until 6) packet[i] = 0xFF.toByte()
            for (i in 0 until 16) {
                for (j in 0 until 6) packet[6 + i * 6 + j] = mac[j]
            }
            DatagramSocket().use { socket ->
                socket.broadcast = true
                val targets = listOf(
                    InetAddress.getByName("255.255.255.255"),
                    InetAddress.getByName("192.168.1.255")
                )
                targets.distinctBy { it.hostAddress }.forEach { address ->
                    socket.send(DatagramPacket(packet, packet.size, address, 9))
                }
            }
        }
    }
}
