package app.localhome.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/** Run [block] every [periodMs] while the screen is in the foreground. */
@Composable
fun PollWhileResumed(key: Any?, periodMs: Long, block: suspend () -> Unit) {
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(key, owner) {
        owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                block()
                delay(periodMs)
            }
        }
    }
}
