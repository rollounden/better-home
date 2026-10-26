package app.betterhome.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DevicesOther
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.betterhome.data.Home
import app.betterhome.data.Live
import app.betterhome.data.SavedDevice
import app.betterhome.data.stateOrNull
import app.betterhome.devices.PurifierMode
import app.betterhome.ui.PollWhileResumed
import app.betterhome.ui.components.AirBand
import app.betterhome.ui.components.PowerToggle
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(home: Home, onOpen: (SavedDevice) -> Unit, onAdd: () -> Unit) {
    val devices by home.store.devices.collectAsState()
    val live by home.live.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var refreshing by remember { mutableStateOf(false) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    suspend fun refreshAll() = coroutineScope { devices.forEach { d -> launch { home.refresh(d) } } }

    PollWhileResumed(devices, 15_000) { refreshAll() }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(greeting())
                        Text(
                            summary(devices, live),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                },
                scrollBehavior = scroll,
            )
        },
        floatingActionButton = {
            if (devices.isNotEmpty()) {
                ExtendedFloatingActionButton(onClick = onAdd, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Add device") })
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (devices.isEmpty()) {
            EmptyHome(onAdd, Modifier.padding(padding))
            return@Scaffold
        }
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                scope.launch {
                    refreshing = true
                    refreshAll()
                    refreshing = false
                }
            },
            modifier = Modifier.padding(padding),
        ) {
            val rooms = devices.groupBy { it.room?.takeIf(String::isNotBlank) }
                .toSortedMap(nullsLast(compareBy { it }))
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                rooms.forEach { (room, list) ->
                    if (rooms.size > 1 || room != null) {
                        item(key = "room:$room") {
                            Text(
                                room ?: "Other",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 8.dp, top = 8.dp),
                            )
                        }
                    }
                    items(list, key = { it.id }) { d ->
                        DeviceCard(
                            device = d,
                            live = live[d.id] ?: Live.Connecting,
                            onClick = { onOpen(d) },
                            onPower = { on ->
                                scope.launch {
                                    home.control(d, { it.copy(power = on) }) { setPower(on) }
                                        ?.let { snackbar.showSnackbar("${d.name}: $it") }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(device: SavedDevice, live: Live, onClick: () -> Unit, onPower: (Boolean) -> Unit) {
    val state = live.stateOrNull
    val on = state?.power == true && live is Live.Online
    val container by animateColorAsState(
        if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        tween(400), label = "card",
    )
    val content = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val band = state?.pm25?.let(AirBand::of)

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(if (live is Live.Offline) Icons.Outlined.CloudOff else Icons.Outlined.Air, null)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(device.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    statusLine(live),
                    style = MaterialTheme.typography.bodyMedium,
                    color = content.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (band != null && state.pm25 != null) {
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 12.dp)) {
                    Text(state.pm25.toString(), style = MaterialTheme.typography.headlineMedium, color = band.color())
                    Text("PM2.5", style = MaterialTheme.typography.labelSmall, color = content.copy(alpha = 0.72f))
                }
            }
            PowerToggle(on = on, enabled = live is Live.Online, onChange = onPower)
        }
    }
}

@Composable
private fun EmptyHome(onAdd: () -> Unit, modifier: Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(112.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.DevicesOther, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("No devices yet", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Everything stays on your Wi-Fi. No account and no cloud.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onAdd, contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)) {
            Icon(Icons.Rounded.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Add a device")
        }
    }
}

private fun statusLine(live: Live): String = when (live) {
    Live.Connecting -> "Connecting…"
    is Live.Offline -> "Offline · ${live.reason}"
    is Live.Online -> {
        val s = live.state
        if (!s.power) "Off"
        else when (s.mode) {
            PurifierMode.Auto -> "On · Auto"
            PurifierMode.Sleep -> "On · Sleep"
            PurifierMode.Favorite -> "On · Manual ${s.favoriteLevel ?: ""}".trimEnd()
            null -> "On"
        }
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}

private fun summary(devices: List<SavedDevice>, live: Map<String, Live>): String {
    if (devices.isEmpty()) return "Let's set up your first device"
    val readings = devices.mapNotNull { (live[it.id] as? Live.Online)?.state?.pm25 }
    val on = devices.count { (live[it.id] as? Live.Online)?.state?.power == true }
    val air = readings.maxOrNull()?.let { "Air is ${AirBand.of(it).label.lowercase()}" }
    val running = if (on == 0) "nothing running" else "$on running"
    return listOfNotNull(air, running).joinToString(" · ")
}

