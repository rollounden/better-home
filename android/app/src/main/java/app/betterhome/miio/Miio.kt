package app.betterhome.miio

import java.io.IOException
import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * miIO wire format, with no I/O so it can be unit-tested on the JVM.
 *
 * Every packet is a 32-byte header plus an optional AES-128-CBC body:
 *
 *     0  magic 0x2131      2  total length
 *     4  unknown (0)       8  device id
 *    12  stamp            16  checksum: MD5(header[0..16] + token + body)
 *
 * key = MD5(token), iv = MD5(key + token). The "hello" packet is a bare header
 * of 0xFF; devices answer it with their id, clock and — only while
 * unprovisioned — their token.
 */
object Miio {
    const val PORT = 54321
    const val MAGIC = 0x2131
    const val HEADER = 32

    val HELLO: ByteArray = ByteArray(HEADER) { 0xFF.toByte() }.also {
        it[0] = 0x21; it[1] = 0x31; it[2] = 0x00; it[3] = 0x20
    }

    fun md5(vararg parts: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("MD5")
        for (part in parts) digest.update(part)
        return digest.digest()
    }

    fun parseHeader(data: ByteArray): Header {
        if (data.size < HEADER) throw MiioException("short packet (${data.size} bytes)")
        val buf = ByteBuffer.wrap(data)
        val magic = buf.short.toInt() and 0xFFFF
        if (magic != MAGIC) throw MiioException("bad magic 0x%04x".format(magic))
        val length = buf.short.toInt() and 0xFFFF
        buf.int
        val deviceId = buf.int.toLong() and 0xFFFFFFFFL
        val stamp = buf.int.toLong() and 0xFFFFFFFFL
        return Header(length, deviceId, stamp, data.copyOfRange(16, 32))
    }
}

open class MiioException(message: String, cause: Throwable? = null) : IOException(message, cause)

class Header(val length: Int, val deviceId: Long, val stamp: Long, val checksum: ByteArray) {
    /** The token a device leaks in its hello reply, or null once it is provisioned. */
    val advertisedToken: ByteArray?
        get() = checksum.takeUnless { c -> c.all { it == 0xFF.toByte() } || c.all { it == 0.toByte() } }
}

class Codec(val token: ByteArray) {
    init {
        require(token.size == 16) { "token must be 16 bytes" }
    }

    private val aesKey = SecretKeySpec(Miio.md5(token), "AES")
    private val aesIv = IvParameterSpec(Miio.md5(Miio.md5(token), token))

    private fun cipher(mode: Int) = Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
        init(mode, aesKey, aesIv)
    }

    fun encode(deviceId: Long, stamp: Long, payload: ByteArray): ByteArray {
        val body = cipher(Cipher.ENCRYPT_MODE).doFinal(payload)
        val packet = ByteBuffer.allocate(Miio.HEADER + body.size)
            .putShort(Miio.MAGIC.toShort())
            .putShort((Miio.HEADER + body.size).toShort())
            .putInt(0)
            .putInt(deviceId.toInt())
            .putInt(stamp.toInt())
            .put(token)
            .put(body)
            .array()
        Miio.md5(packet).copyInto(packet, 16)
        return packet
    }

    /** Verify and decrypt a reply. A checksum mismatch almost always means a wrong token. */
    fun decode(data: ByteArray): Pair<Header, ByteArray> {
        val header = Miio.parseHeader(data)
        val end = header.length.coerceAtMost(data.size)
        val body = data.copyOfRange(Miio.HEADER, end)
        if (body.isEmpty()) return header to body
        val expected = Miio.md5(data.copyOfRange(0, 16), token, body)
        if (!expected.contentEquals(header.checksum)) throw MiioException("checksum mismatch — wrong token?")
        val plain = try {
            cipher(Cipher.DECRYPT_MODE).doFinal(body)
        } catch (e: Exception) {
            throw MiioException("could not decrypt reply — wrong token?", e)
        }
        return header to plain.dropLastWhile { it == 0.toByte() }.toByteArray()
    }
}

fun String.hexToBytes(): ByteArray {
    val clean = trim().lowercase()
    require(clean.length % 2 == 0 && clean.all { it in "0123456789abcdef" }) { "not hex" }
    return ByteArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
