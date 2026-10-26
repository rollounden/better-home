package app.localhome.ui.purifier

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.localhome.data.Home
import app.localhome.data.Live
import app.localhome.data.SavedDevice
import app.localhome.data.stateOrNull
import app.localhome.devices.Brightness
import app.localhome.devices.Cpa4
import app.localhome.devices.Purifier
import app.localhome.devices.PurifierMode
import app.localhome.devices.PurifierState
import app.localhome.ui.PollWhileResumed
import app.localhome.ui.components.AirBand
import app.localhome.ui.components.AirGauge
import app.localhome.ui.components.PowerToggle
import app.localhome.ui.components.Section
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurifierScreen(home: Home, deviceId: String, onBack: () -> Unit) {
    val devices by home.store.devices.collectAsState()
    val device = devices.firstOrNull { it.id == deviceId }
    if (device == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val live = home.live.collectAsState().value[deviceId] ?: Live.Connecting
    val state = live.stateOrNull
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }

    PollWhileResumed(device, 4_000) { home.refresh(device) }

    fun control(optimistic: (PurifierState) -> PurifierState, action: suspend Purifier.() -> Unit) {
        scope.launch { home.control(device, optimistic, action)?.let { snackbar.showSnackbar(it) } }
    }

    val active = state?.power == true && live is Live.Online
    val band = state?.pm25?.let(AirBand::of)
    val glow by animateColorAsState(
        if (active && band != null) band.color().copy(alpha = 0.22f) else Color.Transparent,
        tween(800), label = "glow",
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(device.name)
                        device.room?.takeIf(String::isNotBlank)?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Rename or move") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                            onClick = { menu = false; editing = true })
                        DropdownMenuItem(text = { Text("Remove") }, leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null) },
                            onClick = { menu = false; removing = true })
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.background(MaterialTheme.colorScheme.surface),
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0f to glow, 0.55f to Color.Transparent))
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedVisibility(live is Live.Offline) {
                Banner(Icons.Outlined.WifiOff, "Can't reach ${device.name}", (live as? Live.Offline)?.reason ?: "")
            }
            AnimatedVisibility(state != null && state.fault != 0) {
                Banner(Icons.Outlined.WarningAmber, "Device reports a fault", faultText(state?.fault ?: 0))
            }

            AirGauge(state?.pm25, active, Modifier.widthIn(max = 320.dp).fillMaxWidth().padding(top = 8.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                PowerToggle(
                    on = active,
                    enabled = live is Live.Online,
                    size = 72.dp,
                    onChange = { on -> control({ it.copy(power = on) }) { setPower(on) } },
                )
            }
            Text(
                when {
                    live !is Live.Online -> " "
                    !active -> "Off"
                    state?.motorRpm != null && state.motorRpm > 0 -> "Running · ${state.motorRpm} rpm"
                    else -> "On"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )

            val enabled = active
            Section("Mode") {
                ModePicker(state?.mode, enabled, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { m ->
                    control({ it.copy(mode = m) }) { setMode(m) }
                }
                AnimatedVisibility(
                    state?.mode == PurifierMode.Favorite,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    FanSpeed(state?.favoriteLevel ?: 0, enabled) { level ->
                        control({ it.copy(favoriteLevel = level) }) { setFavoriteLevel(level) }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            FilterSection(state)

            Spacer(Modifier.height(16.dp))
            Section("Device") {
                ToggleRow(Icons.Outlined.Lock, "Child lock", "Disables the buttons on the purifier", state?.childLock == true, live is Live.Online) { v ->
                    control({ it.copy(childLock = v) }) { setChildLock(v) }
                }
                ToggleRow(Icons.Outlined.VolumeUp, "Sounds", "Beep when settings change", state?.buzzer == true, live is Live.Online) { v ->
                    control({ it.copy(buzzer = v) }) { setBuzzer(v) }
                }
                ListItem(
                    headlineContent = { Text("Display") },
                    leadingContent = { Icon(Icons.Outlined.BrightnessMedium, null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                    Brightness.entries.forEachIndexed { i, b ->
                        SegmentedButton(
                            selected = state?.brightness == b,
                            enabled = live is Live.Online,
                            onClick = { control({ it.copy(brightness = b) }) { setBrightness(b) } },
                            shape = SegmentedButtonDefaults.itemShape(i, Brightness.entries.size),
                        ) { Text(b.name) }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "${device.ip} · ${device.model ?: "unknown model"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    if (editing) EditDialog(device, onDismiss = { editing = false }) { name, room ->
        editing = false
        scope.launch { home.store.upsert(device.copy(name = name, room = room)) }
    }
    if (removing) AlertDialog(
        onDismissRequest = { removing = false },
        icon = { Icon(Icons.Outlined.DeleteOutline, null) },
        title = { Text("Remove ${device.name}?") },
        text = { Text("Its token is deleted from this phone. To add it again you'll need to pair it again or enter the token.") },
        confirmButton = { TextButton(onClick = { removing = false; scope.launch { home.remove(device) } }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { removing = false }) { Text("Cancel") } },
    )
}

private data class ModeInfo(val mode: PurifierMode, val label: String, val icon: ImageVector)

private val MODES = listOf(
    ModeInfo(PurifierMode.Auto, "Auto", Icons.Rounded.AutoMode),
    ModeInfo(PurifierMode.Sleep, "Sleep", Icons.Outlined.Bedtime),
    ModeInfo(PurifierMode.Favorite, "Manual", Icons.Outlined.Tune),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModePicker(current: PurifierMode?, enabled: Boolean, modifier: Modifier, onPick: (PurifierMode) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        MODES.forEachIndexed { i, m ->
            SegmentedButton(
                selected = current == m.mode,
                enabled = enabled,
                onClick = { onPick(m.mode) },
                shape = SegmentedButtonDefaults.itemShape(i, MODES.size),
                icon = { SegmentedButtonDefaults.Icon(current == m.mode) { Icon(m.icon, null, Modifier.size(18.dp)) } },
            ) { Text(m.label) }
        }
    }
}

@Composable
private fun FanSpeed(level: Int, enabled: Boolean, onSet: (Int) -> Unit) {
    // Local value while dragging; only the final position is sent to the device.
    var dragging by remember { mutableStateOf(false) }
    var value by remember { mutableFloatStateOf(level.toFloat()) }
    LaunchedEffect(level) { if (!dragging) value = level.toFloat() }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Fan speed", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text("${value.roundToInt()} / ${Cpa4.FAVORITE_MAX}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value,
            onValueChange = { dragging = true; value = it },
            onValueChangeFinished = { dragging = false; onSet(value.roundToInt()) },
            valueRange = 0f..Cpa4.FAVORITE_MAX.toFloat(),
            steps = Cpa4.FAVORITE_MAX - 1,
            enabled = enabled,
        )
    }
}

@Composable
private fun FilterSection(state: PurifierState?) {
    val life = state?.filterLife
    val low = life != null && life <= 10
    Section("Filter") {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.FilterAlt, null, tint = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(life?.let { "$it%" } ?: "–", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            low -> "Replace soon"
                            state?.filterDaysLeft != null -> "about ${state.filterDaysLeft} days left"
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { (life ?: 0) / 100f },
                    color = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    gapSize = 4.dp,
                    drawStopIndicator = {},
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun Banner(icon: ImageVector, title: String, body: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (body.isNotBlank()) Text(body, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun faultText(code: Int) = when (code) {
    2 -> "The motor is stuck."
    3 -> "The air sensor isn't responding."
    else -> "Fault code $code"
}

@Composable
fun EditDialog(device: SavedDevice, onDismiss: () -> Unit, onSave: (String, String?) -> Unit) {
    var name by remember { mutableStateOf(device.name) }
    var room by remember { mutableStateOf(device.room.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Edit, null) },
        title = { Text("Rename or move") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(room, { room = it }, label = { Text("Room") }, placeholder = { Text("e.g. Bedroom") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), room.trim().ifBlank { null }) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

