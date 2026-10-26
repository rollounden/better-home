package app.betterhome.data

import android.content.Context
import app.betterhome.devices.Purifier
import app.betterhome.devices.PurifierState
import app.betterhome.miio.MiioDevice
import app.betterhome.miio.MiioException
import app.betterhome.miio.hexToBytes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

sealed interface Live {
    data object Connecting : Live
    data class Online(val state: PurifierState) : Live
    data class Offline(val reason: String, val last: PurifierState?) : Live
}

val Live.stateOrNull: PurifierState?
    get() = when (this) {
        is Live.Online -> state
        is Live.Offline -> last
        Live.Connecting -> null
    }

/** App-wide graph: saved devices, one client per device, and their live state. */
class Home(context: Context) {
    val store = DeviceStore(context)

    private val clients = ConcurrentHashMap<String, Pair<String, Purifier>>()
    private val _live = MutableStateFlow<Map<String, Live>>(emptyMap())
    val live: StateFlow<Map<String, Live>> = _live.asStateFlow()

    private fun purifier(d: SavedDevice): Purifier {
        val key = "${d.ip}|${d.token}"
        clients[d.id]?.takeIf { it.first == key }?.let { return it.second }
        return Purifier(MiioDevice(d.ip, d.token.hexToBytes(), d.deviceId)).also { clients[d.id] = key to it }
    }

    suspend fun refresh(d: SavedDevice) {
        try {
            val state = purifier(d).state()
            _live.update { it + (d.id to Live.Online(state)) }
        } catch (e: IOException) {
            _live.update { it + (d.id to Live.Offline(e.reason(), it[d.id]?.stateOrNull)) }
        }
    }

    /**
     * Apply [optimistic] immediately so the UI feels instant, send the command,
     * then read back the real state. On failure the read-back undoes the guess.
     */
    suspend fun control(
        d: SavedDevice,
        optimistic: (PurifierState) -> PurifierState,
        action: suspend Purifier.() -> Unit,
    ): String? {
        _live.update { map ->
            val cur = map[d.id]?.stateOrNull ?: return@update map
            map + (d.id to Live.Online(optimistic(cur)))
        }
        val error = try {
            purifier(d).action()
            null
        } catch (e: IOException) {
            e.reason()
        }
        refresh(d)
        return error
    }

    suspend fun remove(d: SavedDevice) {
        store.remove(d.id)
        clients.remove(d.id)
        _live.update { it - d.id }
    }

    private fun IOException.reason() = when (this) {
        is MiioException -> message ?: "device error"
        else -> "unreachable"
    }
}
