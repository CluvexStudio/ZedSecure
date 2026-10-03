package dev.cluvex.zedsecure.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

object DesktopBack {
    class Entry(@Volatile var enabled: Boolean, val onBack: () -> Unit)

    private val entries = mutableListOf<Entry>()

    @Synchronized
    fun register(entry: Entry) {
        entries.add(entry)
    }

    @Synchronized
    fun unregister(entry: Entry) {
        entries.remove(entry)
    }

    fun dispatch(): Boolean {
        val entry = synchronized(this) { entries.lastOrNull { it.enabled } } ?: return false
        entry.onBack()
        return true
    }
}

@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    val latest by rememberUpdatedState(onBack)
    val entry = remember { DesktopBack.Entry(enabled) { latest() } }
    SideEffect { entry.enabled = enabled }
    DisposableEffect(entry) {
        DesktopBack.register(entry)
        onDispose { DesktopBack.unregister(entry) }
    }
}
