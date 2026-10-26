package app.betterhome.ui.add

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.betterhome.data.Home
import app.betterhome.data.SavedDevice
import app.betterhome.miio.Found
import app.betterhome.miio.MiioDevice
import app.betterhome.miio.discover
import app.betterhome.miio.hexToBytes
import app.betterhome.provision.Provisioner
import app.betterhome.provision.Step
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException

private enum class Page { Menu, Pair, Scan, Manual, Finish }

/** What we know about a device once it answers; the Finish page names it. */
private data class Draft(val ip: String, val token: String, val deviceId: Long, val model: String?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeviceScreen(home: Home, onDone: () -> Unit) {
    var page by rememberSaveable { mutableStateOf(Page.Menu) }
    var draft by remember { mutableStateOf<Draft?>(null) }
    var manualIp by remember { mutableStateOf("") }

    val back = { if (page == Page.Menu) onDone() else page = Page.Menu }
    BackHandler(onBack = back)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (page) {
                            Page.Menu -> "Add device"
                            Page.Pair -> "Pair a new device"
                            Page.Scan -> "Find on this network"
                            Page.Manual -> "Enter details"
                            Page.Finish -> "Almost done"
                        }
                    )
                },
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        AnimatedContent(
            page,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                (slideInHorizontally { if (forward) it / 4 else -it / 4 } + fadeIn()) togetherWith
                    (slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut())
            },
            modifier = Modifier.padding(padding).fillMaxSize(),
            label = "add",
        ) { p ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (p) {
                    Page.Menu -> Menu { page = it }
                    Page.Pair -> PairPage { draft = it; page = Page.Finish }
                    Page.Scan -> ScanPage(
                        onReady = { draft = it; page = Page.Finish },
                        onNeedsToken = { manualIp = it; page = Page.Manual },
                    )
                    Page.Manual -> ManualPage(manualIp) { draft = it; page = Page.Finish }
                    Page.Finish -> draft?.let { d ->
                        FinishPage(d) { name, room ->
                            home.store.upsert(SavedDevice(name = name, room = room, ip = d.ip, token = d.token, deviceId = d.deviceId, model = d.model))
                            onDone()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Menu(go: (Page) -> Unit) {
    Text(
        "Devices are controlled directly over your Wi-Fi. Their keys are stored encrypted on this phone and nowhere else.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
    )
    Option(Icons.Outlined.WifiTethering, "Pair a new device", "Device is in setup mode, or was just reset") { go(Page.Pair) }
    Option(Icons.Outlined.Radar, "Find on this network", "Device is already on your Wi-Fi") { go(Page.Scan) }
    Option(Icons.Outlined.Keyboard, "Enter IP and token", "You already know the device token") { go(Page.Manual) }
}

@Composable
private fun Option(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// -- pairing --------------------------------------------------------------------

private val STEPS = listOf(
    Step.JoinDevice to "Connect to the device",
    Step.Handshake to "Say hello",
    Step.SendWifi to "Send your Wi-Fi details",
    Step.WaitLan to "Wait for it to join your network",
)

@Composable
private fun PairPage(onPaired: (Draft) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ssid by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var step by remember { mutableStateOf<Step?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun start() {
        error = null
        scope.launch {
            try {
                val r = Provisioner(context).run(ssid.trim(), password) { step = it }
                onPaired(Draft(r.ip, r.token, r.deviceId, r.model))
            } catch (e: IOException) {
                error = e.message ?: "Pairing failed."
                step = null
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                error = "Timed out waiting for the device network."
                step = null
            }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) start() else error = "Nearby Wi-Fi permission is needed to find the device's setup network."
    }

    val running = step != null
    Hint(
        "1. Hold the purifier's button until the Wi-Fi light blinks.\n" +
            "2. Enter your home Wi-Fi below. It must be 2.4 GHz.\n" +
            "3. Android will ask you to pick the device's network. Choose it.",
    )
    OutlinedTextField(
        ssid, { ssid = it }, label = { Text("Home Wi-Fi name") }, singleLine = true, enabled = !running,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        password, { password = it }, label = { Text("Wi-Fi password") }, singleLine = true, enabled = !running,
        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        trailingIcon = {
            IconButton(onClick = { showPassword = !showPassword }) {
                Icon(if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (showPassword) "Hide" else "Show")
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )

    if (running || error != null) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                val current = STEPS.indexOfFirst { it.first == step }
                STEPS.forEachIndexed { i, (_, label) ->
                    StepRow(label, done = current > i, active = current == i)
                }
                error?.let {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(12.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    Button(
        onClick = { permission.launch(Manifest.permission.NEARBY_WIFI_DEVICES) },
        enabled = !running && ssid.isNotBlank(),
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { Text(if (error != null) "Try again" else "Start pairing") }
}

@Composable
private fun StepRow(label: String, done: Boolean, active: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when {
                done -> Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                active -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.5.dp)
                else -> Surface(Modifier.size(10.dp), shape = CircleShape, color = MaterialTheme.colorScheme.outlineVariant) {}
            }
        }
        Spacer(Modifier.width(16.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (done || active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// -- LAN scan -----------------------------------------------------------------

@Composable
private fun ScanPage(onReady: (Draft) -> Unit, onNeedsToken: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var scanning by remember { mutableStateOf(true) }
    var found by remember { mutableStateOf<List<Found>>(emptyList()) }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun scan() {
        scanning = true
        found = runCatching { discover(3000) }.getOrDefault(emptyList())
        scanning = false
    }
    LaunchedEffect(Unit) { scan() }

    if (scanning) {
        Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
            Spacer(Modifier.width(16.dp))
            Text("Looking for devices…", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }
    if (found.isEmpty()) {
        Hint("Nothing answered. Make sure the phone is on the same Wi-Fi as the device. Some routers block device discovery between clients.")
    }
    found.forEach { f ->
        Option(
            Icons.Outlined.Air,
            f.ip,
            if (f.token != null) "Ready to add" else "Needs its token",
        ) {
            if (f.token == null) return@Option onNeedsToken(f.ip)
            busy = f.ip
            scope.launch {
                val model = runCatching { modelOf(f.ip, f.token, f.deviceId) }.getOrNull()
                busy = null
                onReady(Draft(f.ip, f.token, f.deviceId, model))
            }
        }
    }
    busy?.let { Text("Talking to $it…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    OutlinedButton(onClick = { scope.launch { scan() } }, modifier = Modifier.fillMaxWidth()) { Text("Scan again") }
}

// -- manual ---------------------------------------------------------------------

@Composable
private fun ManualPage(initialIp: String, onReady: (Draft) -> Unit) {
    val scope = rememberCoroutineScope()
    var ip by rememberSaveable { mutableStateOf(initialIp) }
    var token by rememberSaveable { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val tokenValid = token.trim().length == 32 && token.trim().all { it.lowercaseChar() in "0123456789abcdef" }

    Hint("The token is a 32-character hex key. If you don't have it, reset the device and use “Pair a new device” instead.")
    OutlinedTextField(
        ip, { ip = it.trim() }, label = { Text("IP address") }, placeholder = { Text("192.168.1.42") }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        token, { token = it.trim() }, label = { Text("Token") }, singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
        isError = token.isNotEmpty() && !tokenValid,
        supportingText = { Text("${token.length} / 32") },
        modifier = Modifier.fillMaxWidth(),
    )
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    Button(
        enabled = ip.isNotBlank() && tokenValid && !checking,
        onClick = {
            checking = true
            error = null
            scope.launch {
                try {
                    val dev = MiioDevice(ip, token.hexToBytes())
                    val header = dev.hello()
                    val model = dev.call("miIO.info").jsonObject["model"]?.jsonPrimitive?.content
                    onReady(Draft(ip, token.lowercase(), header.deviceId, model))
                } catch (e: IOException) {
                    error = "Couldn't talk to the device: ${e.message ?: "no answer"}"
                } finally {
                    checking = false
                }
            }
        },
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        if (checking) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        else Text("Connect")
    }
}

// -- finish ---------------------------------------------------------------------

@Composable
private fun FinishPage(draft: Draft, onSave: suspend (String, String?) -> Unit) {
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("Air purifier") }
    var room by rememberSaveable { mutableStateOf("") }

    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Connected", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    "${draft.model ?: "Unknown model"} at ${draft.ip}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(room, { room = it }, label = { Text("Room (optional)") }, placeholder = { Text("e.g. Bedroom") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Button(
        enabled = name.isNotBlank(),
        onClick = { scope.launch { onSave(name.trim(), room.trim().ifBlank { null }) } },
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { Text("Save") }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
    )
}

private suspend fun modelOf(ip: String, token: String, deviceId: Long): String? =
    MiioDevice(ip, token.hexToBytes(), deviceId).call("miIO.info").jsonObject["model"]?.jsonPrimitive?.content
