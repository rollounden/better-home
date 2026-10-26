package app.betterhome.provision

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.PatternMatcher
import app.betterhome.miio.Header
import app.betterhome.miio.MiioDevice
import app.betterhome.miio.MiioException
import app.betterhome.miio.discover
import app.betterhome.miio.toHex
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.DatagramSocket
import java.net.Inet4Address
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class Step { JoinDevice, Handshake, SendWifi, WaitLan, Done }

data class Provisioned(val ip: String, val token: String, val deviceId: Long, val model: String?)

class ProvisionException(message: String) : IOException(message)

/**
 * Pairs a device in setup mode:
 *
 *  1. ask Android to join its open setup network (the system shows a picker
 *     filtered to Xiaomi SSIDs — the phone's normal Wi-Fi stays up),
 *  2. hello on the AP gateway, which leaks the device token while unpaired,
 *  3. send miIO.config_router with the home Wi-Fi credentials,
 *  4. drop the AP and wait for the device to answer on the home LAN.
 */
class Provisioner(context: Context) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)

    suspend fun run(homeSsid: String, password: String, onStep: (Step) -> Unit): Provisioned {
        onStep(Step.JoinDevice)
        val (network, callback) = joinSetupNetwork()
        val token: ByteArray
        val deviceId: Long
        try {
            onStep(Step.Handshake)
            val gateway = gatewayOf(cm.getLinkProperties(network))
            val sockets = { DatagramSocket().also(network::bindSocket) }
            val dev = MiioDevice(gateway, ByteArray(16) { 0xFF.toByte() }, sockets = sockets, timeoutMs = 2000)

            // Firmware takes a few seconds after association to open its socket.
            var header: Header? = null
            for (attempt in 1..20) {
                header = runCatching { dev.hello() }.getOrNull()
                if (header != null) break
                delay(1500)
            }
            val h = header ?: throw ProvisionException("The device didn't answer on its setup network.")
            token = h.advertisedToken
                ?: throw ProvisionException("The device is already paired. Reset it (hold the button until the Wi-Fi light blinks) and try again.")
            deviceId = h.deviceId
            dev.adoptToken(token)

            onStep(Step.SendWifi)
            val params = buildJsonObject {
                put("ssid", homeSsid)
                put("passwd", password)
                put("uid", 0)
            }
            try {
                dev.call("miIO.config_router", params)
            } catch (_: MiioException) {
                // Some firmware only accepts the "safe" variant.
                dev.call("miIO.config_router_safe", params)
            }
        } finally {
            cm.unregisterNetworkCallback(callback)
        }

        onStep(Step.WaitLan)
        val ip = waitOnLan(deviceId)
        val lanDevice = MiioDevice(ip, token, deviceId)
        val model = runCatching {
            lanDevice.call("miIO.info").jsonObject["model"]?.jsonPrimitive?.content
        }.getOrNull()
        onStep(Step.Done)
        return Provisioned(ip, token.toHex(), deviceId, model)
    }

    private suspend fun joinSetupNetwork(): Pair<Network, ConnectivityManager.NetworkCallback> {
        val specifier = WifiNetworkSpecifier.Builder()
            // Xiaomi setup networks end in _miapXXXX or _mibtXXXX, e.g. "xiaomi-airp-cpa4_mibt4960".
            .setSsidPattern(PatternMatcher(".*_mi[ab][pt].*", PatternMatcher.PATTERN_ADVANCED_GLOB))
            .build()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifier)
            .build()
        return withTimeout(120_000) {
            suspendCancellableCoroutine { cont ->
                val cb = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        if (cont.isActive) cont.resume(network to this)
                    }

                    override fun onUnavailable() {
                        if (cont.isActive) cont.resumeWithException(
                            ProvisionException("Couldn't join the device. Is it in setup mode and close by?")
                        )
                    }
                }
                cm.requestNetwork(request, cb)
                cont.invokeOnCancellation { runCatching { cm.unregisterNetworkCallback(cb) } }
            }
        }
    }

    private fun gatewayOf(lp: LinkProperties?): String {
        lp?.dhcpServerAddress?.hostAddress?.let { return it }
        lp?.routes?.firstNotNullOfOrNull { r -> r.gateway?.takeIf { it is Inet4Address && !it.isAnyLocalAddress } }
            ?.hostAddress?.let { return it }
        val addr = lp?.linkAddresses?.firstOrNull { it.address is Inet4Address }
            ?: throw ProvisionException("No address on the device's network.")
        val ip = addr.address.address
        val mask = if (addr.prefixLength == 0) 0 else -1 shl (32 - addr.prefixLength)
        val net = ((ip[0].toInt() and 0xFF shl 24) or (ip[1].toInt() and 0xFF shl 16) or
            (ip[2].toInt() and 0xFF shl 8) or (ip[3].toInt() and 0xFF)) and mask
        val gw = net + 1
        return "${gw ushr 24 and 0xFF}.${gw ushr 16 and 0xFF}.${gw ushr 8 and 0xFF}.${gw and 0xFF}"
    }

    private suspend fun waitOnLan(deviceId: Long): String {
        repeat(30) {
            discover(2000).firstOrNull { it.deviceId == deviceId }?.let { return it.ip }
            delay(1000)
        }
        throw ProvisionException("The device took the Wi-Fi details but never showed up on your network. Check the password and that it's a 2.4 GHz network.")
    }
}
