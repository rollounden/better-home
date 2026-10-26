package app.betterhome.miio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

data class Found(val ip: String, val deviceId: Long, val token: String?)

/** Broadcast hello on the LAN and collect every miIO device that answers. */
suspend fun discover(timeoutMs: Long = 2500): List<Found> = withContext(Dispatchers.IO) {
    val seen = linkedMapOf<String, Found>()
    DatagramSocket().use { socket ->
        socket.broadcast = true
        socket.soTimeout = 300
        val target = InetAddress.getByName("255.255.255.255")
        repeat(3) { socket.send(DatagramPacket(Miio.HELLO, Miio.HELLO.size, target, Miio.PORT)) }
        val end = System.currentTimeMillis() + timeoutMs
        val buf = ByteArray(1024)
        while (System.currentTimeMillis() < end) {
            val p = DatagramPacket(buf, buf.size)
            try {
                socket.receive(p)
            } catch (_: SocketTimeoutException) {
                continue
            }
            val header = runCatching { Miio.parseHeader(buf.copyOf(p.length)) }.getOrNull() ?: continue
            val ip = p.address.hostAddress ?: continue
            seen[ip] = Found(ip, header.deviceId, header.advertisedToken?.toHex())
        }
    }
    seen.values.toList()
}
