package app.betterhome.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Serializable
data class SavedDevice(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val ip: String,
    val token: String,
    val deviceId: Long,
    val model: String? = null,
    val room: String? = null,
)

/**
 * Devices and their tokens, in one file in app-private storage, encrypted with
 * a non-exportable Android Keystore key. Tokens never leave the phone.
 */
class DeviceStore(context: Context) {
    private val file = File(context.filesDir, "devices.bin")
    private val json = Json { ignoreUnknownKeys = true }
    private val _devices = MutableStateFlow(load())
    val devices: StateFlow<List<SavedDevice>> = _devices.asStateFlow()

    fun get(id: String): SavedDevice? = _devices.value.firstOrNull { it.id == id }

    suspend fun upsert(device: SavedDevice) = mutate { list ->
        if (list.any { it.id == device.id }) list.map { if (it.id == device.id) device else it } else list + device
    }

    /** Add a newly paired device; re-pairing one we already know replaces its address and token. */
    suspend fun addOrReplace(device: SavedDevice) = mutate { list ->
        val existing = list.firstOrNull { it.deviceId == device.deviceId }
        if (existing == null) list + device
        else list.map { if (it.id == existing.id) device.copy(id = existing.id) else it }
    }

    suspend fun remove(id: String) = mutate { list -> list.filterNot { it.id == id } }

    private suspend fun mutate(f: (List<SavedDevice>) -> List<SavedDevice>) {
        _devices.update(f)
        val snapshot = _devices.value
        withContext(Dispatchers.IO) {
            val tmp = File(file.parentFile, "devices.tmp")
            tmp.writeBytes(Vault.seal(json.encodeToString(snapshot).toByteArray()))
            tmp.renameTo(file)
        }
    }

    private fun load(): List<SavedDevice> = runCatching {
        if (!file.exists()) emptyList()
        else json.decodeFromString<List<SavedDevice>>(Vault.open(file.readBytes()).decodeToString())
    }.getOrDefault(emptyList())
}

private object Vault {
    private const val ALIAS = "betterhome.devices"
    private const val IV = 12

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generateKey()
        }
    }

    fun seal(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return c.iv + c.doFinal(plain)
    }

    fun open(sealed: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, sealed, 0, IV))
        return c.doFinal(sealed, IV, sealed.size - IV)
    }
}
