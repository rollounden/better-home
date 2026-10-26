package app.betterhome.ui.add

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.betterhome.data.Home
import app.betterhome.data.SavedDevice
import app.betterhome.miio.Found
import app.betterhome.miio.MiioDevice
import app.betterhome.miio.discover
import app.betterhome.miio.hexToBytes
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException

private enum class Page { Details, Finish }

/** What we know about a device once it answers; the Finish page names it. */
private data class Draft(val ip: String, val token: String, val deviceId: Long, val model: String?)

/**
 * Adding is by IP and token. Pairing over the device's setup network isn't
 * offered: this firmware replaces its token on joining Wi-Fi, so the token has
 * to come from the Xiaomi account once (see README).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeviceScreen(home: Home, onDone: () -> Unit) {
    var page by rememberSaveable { mutableStateOf(Page.Details) }
    var draft by remember { mutableStateOf<Draft?>(null) }

    val back = { if (page == Page.Details) onDone() else page = Page.Details }
    BackHandler(onBack = back)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (page == Page.Details) "Add device" else "Almost done") },
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
                    Page.Details -> DetailsPage(home) { draft = it; page = Page.Finish }
                    Page.Finish -> draft?.let { d ->
                        FinishPage(d) { name, room ->
                            home.store.addOrReplace(SavedDevice(name = name, room = room, ip = d.ip, token = d.token, deviceId = d.deviceId, model = d.model))
                            onDone()
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsPage(home: Home, onReady: (Draft) -> Unit) {
    val scope = rememberCoroutineScope()
    val saved by home.store.devices.collectAsState()
    var ip by rememberSaveable { mutableStateOf("") }
    var token by rememberSaveable { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var found by remember { mutableStateOf<List<Found>?>(null) }
    val tokenValid = token.length == 32 && token.all { it.lowercaseChar() in "0123456789abcdef" }

    LaunchedEffect(Unit) { found = runCatching { discover(2500) }.getOrDefault(emptyList()) }

    Hint(
        "Devices are controlled directly over your Wi-Fi. Their tokens are stored encrypted on this phone and nowhere else."
    )

    // Devices on the LAN that aren't saved yet: tap one to fill in its address.
    val candidates = found?.filter { f -> saved.none { it.deviceId == f.deviceId } }
    when {
        candidates == null -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text("Looking for devices on your Wi-Fi…", style = MaterialTheme.typography.bodyMedium)
        }
        candidates.isNotEmpty() -> {
            Text("On your Wi-Fi", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                candidates.forEach { f ->
                    FilterChip(
                        selected = ip == f.ip,
                        onClick = { ip = f.ip },
                        label = { Text(f.ip) },
                        leadingIcon = { Icon(Icons.Outlined.Air, null, Modifier.size(18.dp)) },
                    )
                }
            }
        }
    }

    OutlinedTextField(
        ip, { ip = it.trim() }, label = { Text("IP address") }, placeholder = { Text("192.168.1.42") }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        token, { token = it.trim() }, label = { Text("Token") }, singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
        isError = token.isNotEmpty() && !tokenValid,
        supportingText = { Text("32-character key from your Xiaomi account · ${token.length} / 32") },
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
                    error = "Couldn't connect: ${e.message ?: "no answer"}"
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
