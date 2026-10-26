package app.localhome.miio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class CodecTest {
    private val token = "00112233445566778899aabbccddeeff".hexToBytes()

    @Test
    fun helloIsAllFfHeader() {
        assertEquals("21310020" + "ff".repeat(28), Miio.HELLO.toHex())
    }

    @Test
    fun keyAndIvAreDerivedFromToken() {
        // Encrypt independently with key = MD5(token), iv = MD5(key + token) and compare.
        val key = Miio.md5(token)
        val iv = Miio.md5(key, token)
        val payload = """{"id":1,"method":"miIO.info","params":[]}""".toByteArray() + 0
        val expected = Cipher.getInstance("AES/CBC/PKCS5Padding").run {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv)); doFinal(payload)
        }
        val packet = Codec(token).encode(0x1234, 99, payload)
        assertArrayEquals(expected, packet.copyOfRange(32, packet.size))
    }

    @Test
    fun headerLayoutAndChecksum() {
        val packet = Codec(token).encode(0x0ABBCCDD, 0x11223344, "{}".toByteArray())
        assertEquals("2131", packet.copyOfRange(0, 2).toHex())
        assertEquals(packet.size, ((packet[2].toInt() and 0xFF) shl 8) or (packet[3].toInt() and 0xFF))
        assertEquals("00000000", packet.copyOfRange(4, 8).toHex())
        assertEquals("0abbccdd", packet.copyOfRange(8, 12).toHex())
        assertEquals("11223344", packet.copyOfRange(12, 16).toHex())
        val body = packet.copyOfRange(32, packet.size)
        assertArrayEquals(Miio.md5(packet.copyOfRange(0, 16), token, body), packet.copyOfRange(16, 32))
    }

    @Test
    fun roundTripStripsTrailingNul() {
        val codec = Codec(token)
        val (header, plain) = codec.decode(codec.encode(42, 7, """{"result":["ok"]}""".toByteArray() + 0))
        assertEquals(42L, header.deviceId)
        assertEquals(7L, header.stamp)
        assertEquals("""{"result":["ok"]}""", plain.decodeToString())
    }

    @Test
    fun wrongTokenIsRejected() {
        val packet = Codec(token).encode(1, 1, "{}".toByteArray())
        val other = Codec("ffeeddccbbaa99887766554433221100".hexToBytes())
        assertThrows(MiioException::class.java) { other.decode(packet) }
    }

    @Test
    fun helloReplyTokenOnlyWhenUnprovisioned() {
        val reply = Miio.HELLO.copyOf().also { token.copyInto(it, 16) }
        assertArrayEquals(token, Miio.parseHeader(reply).advertisedToken)
        assertNull(Miio.parseHeader(Miio.HELLO).advertisedToken)
    }
}
