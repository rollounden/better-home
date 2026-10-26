package app.localhome.devices

import app.localhome.miio.MiioDevice
import app.localhome.miio.Prop
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

enum class PurifierMode(val value: Int) { Auto(0), Sleep(1), Favorite(2) }

enum class Brightness(val value: Int) { Off(0), Dim(1), Bright(2) }

data class PurifierState(
    val power: Boolean = false,
    val mode: PurifierMode? = null,
    val fault: Int = 0,
    val pm25: Int? = null,
    val filterLife: Int? = null,
    val filterDaysLeft: Int? = null,
    val favoriteLevel: Int? = null,
    val childLock: Boolean = false,
    val buzzer: Boolean = false,
    val brightness: Brightness? = null,
    val motorRpm: Int? = null,
)

/**
 * Property map for xiaomi.airp.cpa4 (Air Purifier 4 Compact), from
 * urn:miot-spec-v2:device:air-purifier:0000A007:xiaomi-cpa4:2.
 */
object Cpa4 {
    val POWER = Prop(2, 1)
    val FAULT = Prop(2, 2)
    val MODE = Prop(2, 4)
    val PM25 = Prop(3, 4)
    val FILTER_LIFE = Prop(4, 1)
    val FILTER_DAYS_LEFT = Prop(4, 4)
    val BUZZER = Prop(6, 1)
    val CHILD_LOCK = Prop(8, 1)
    val MOTOR_RPM = Prop(9, 1)
    val FAVORITE_LEVEL = Prop(9, 11)
    val BRIGHTNESS = Prop(13, 2)

    const val FAVORITE_MAX = 14

    val ALL = listOf(POWER, FAULT, MODE, PM25, FILTER_LIFE, FILTER_DAYS_LEFT, BUZZER, CHILD_LOCK, MOTOR_RPM, FAVORITE_LEVEL, BRIGHTNESS)
}

class Purifier(val device: MiioDevice) {

    suspend fun state(): PurifierState {
        val raw = device.getProperties(Cpa4.ALL)
        fun int(p: Prop) = raw[p]?.intOrNull
        fun bool(p: Prop) = raw[p]?.booleanOrNull ?: false
        return PurifierState(
            power = bool(Cpa4.POWER),
            mode = int(Cpa4.MODE)?.let { v -> PurifierMode.entries.firstOrNull { it.value == v } },
            fault = int(Cpa4.FAULT) ?: 0,
            pm25 = int(Cpa4.PM25),
            filterLife = int(Cpa4.FILTER_LIFE),
            filterDaysLeft = int(Cpa4.FILTER_DAYS_LEFT),
            favoriteLevel = int(Cpa4.FAVORITE_LEVEL),
            childLock = bool(Cpa4.CHILD_LOCK),
            buzzer = bool(Cpa4.BUZZER),
            brightness = int(Cpa4.BRIGHTNESS)?.let { v -> Brightness.entries.firstOrNull { it.value == v } },
            motorRpm = int(Cpa4.MOTOR_RPM),
        )
    }

    suspend fun setPower(on: Boolean) = device.setProperty(Cpa4.POWER, JsonPrimitive(on))
    suspend fun setMode(mode: PurifierMode) = device.setProperty(Cpa4.MODE, JsonPrimitive(mode.value))
    suspend fun setFavoriteLevel(level: Int) =
        device.setProperty(Cpa4.FAVORITE_LEVEL, JsonPrimitive(level.coerceIn(0, Cpa4.FAVORITE_MAX)))
    suspend fun setChildLock(on: Boolean) = device.setProperty(Cpa4.CHILD_LOCK, JsonPrimitive(on))
    suspend fun setBuzzer(on: Boolean) = device.setProperty(Cpa4.BUZZER, JsonPrimitive(on))
    suspend fun setBrightness(b: Brightness) = device.setProperty(Cpa4.BRIGHTNESS, JsonPrimitive(b.value))
}

/** Which UI and driver a model gets. New device types are added here. */
enum class DeviceKind { Purifier, Unsupported }

fun kindOf(model: String?): DeviceKind = when {
    model == null -> DeviceKind.Unsupported
    model.startsWith("xiaomi.airp.") || model.startsWith("zhimi.airp") -> DeviceKind.Purifier
    else -> DeviceKind.Unsupported
}
