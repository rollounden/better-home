package app.betterhome.miio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/** Opens the UDP socket; lets provisioning bind it to the device's own AP network. */
typealias SocketFactory = () -> DatagramSocket

/** A miot property address: service id + property id. */
data class Prop(val siid: Int, val piid: Int)

/** One device, addressed by IP and token. Thread-safe; calls are serialised. */
class MiioDevice(
    val ip: String,
    token: ByteArray,
    var deviceId: Long = 0,
    private val sockets: SocketFactory = { DatagramSocket() },
    private val timeoutMs: Int = 2500,
) {
    var token: ByteArray = token
        private set
    private var codec = Codec(token)
    private val lock = Mutex()
    private var nextId = 1
    private var stamp = 0L
    private var stampAtNanos = 0L

    suspend fun hello(): Header = lock.withLock { helloLocked() }

    private suspend fun helloLocked(): Header {
        val header = Miio.parseHeader(exchange(Miio.HELLO))
        deviceId = header.deviceId
        stamp = header.stamp
        stampAtNanos = System.nanoTime()
        return header
    }

    /** Adopt the token an unprovisioned device advertised, so commands are keyed right. */
    fun adoptToken(newToken: ByteArray) {
        token = newToken
        codec = Codec(newToken)
    }

    suspend fun call(method: String, params: JsonElement = JsonArray(emptyList())): JsonElement =
        lock.withLock {
            var last: Exception? = null
            repeat(3) { attempt ->
                try {
                    if (deviceId == 0L || attempt > 0 || ageSeconds() > 120) helloLocked()
                    return@withLock send(method, params)
                } catch (e: SocketTimeoutException) {
                    last = e
                }
            }
            throw MiioException("$ip did not answer", last)
        }

    private fun ageSeconds() = (System.nanoTime() - stampAtNanos) / 1_000_000_000

    private suspend fun send(method: String, params: JsonElement): JsonElement {
        // Devices drop requests whose id they have already seen, so ids only go up.
        nextId += 1
        val request = buildJsonObject {
            put("id", nextId)
            put("method", method)
            put("params", params)
        }
        val payload = request.toString().toByteArray() + 0
        val packet = codec.encode(deviceId, stamp + ageSeconds() + 1, payload)
        val (_, body) = codec.decode(exchange(packet))
        val reply = try {
            Json.parseToJsonElement(body.decodeToString()).jsonObject
        } catch (e: Exception) {
            throw MiioException("undecodable reply", e)
        }
        reply["error"]?.let { err ->
            val msg = (err as? JsonObject)?.get("message")?.jsonPrimitive?.content ?: err.toString()
            throw MiioException("$method: $msg")
        }
        return reply["result"] ?: JsonArray(emptyList())
    }

    private suspend fun exchange(packet: ByteArray): ByteArray = withContext(Dispatchers.IO) {
        sockets().use { socket ->
            socket.soTimeout = timeoutMs
            socket.send(DatagramPacket(packet, packet.size, InetAddress.getByName(ip), Miio.PORT))
            val buf = ByteArray(4096)
            val reply = DatagramPacket(buf, buf.size)
            socket.receive(reply)
            buf.copyOf(reply.length)
        }
    }

    // -- miot -----------------------------------------------------------------

    suspend fun getProperties(props: Collection<Prop>): Map<Prop, JsonPrimitive> {
        val params = buildJsonArray {
            props.forEach { p ->
                add(buildJsonObject {
                    put("did", deviceId.toString())
                    put("siid", p.siid)
                    put("piid", p.piid)
                })
            }
        }
        val result = call("get_properties", params) as? JsonArray ?: return emptyMap()
        return result.mapNotNull { item ->
            val o = item.jsonObject
            val code = o["code"]?.jsonPrimitive?.int ?: 0
            val value = o["value"] as? JsonPrimitive
            if (code != 0 || value == null) null
            else Prop(o["siid"]!!.jsonPrimitive.int, o["piid"]!!.jsonPrimitive.int) to value
        }.toMap()
    }

    suspend fun setProperty(prop: Prop, value: JsonPrimitive) {
        val params = buildJsonArray {
            add(buildJsonObject {
                put("did", deviceId.toString())
                put("siid", prop.siid)
                put("piid", prop.piid)
                put("value", value)
            })
        }
        val result = call("set_properties", params) as? JsonArray
        val code = result?.firstOrNull()?.jsonObject?.get("code")?.jsonPrimitive?.int ?: 0
        if (code != 0) throw MiioException("device refused the change (code $code)")
    }
}
