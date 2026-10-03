package dev.cluvex.zedsecure.core.psiphon

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PsiphonDownloadBus {
    class Request(
        val onResult: (Boolean) -> Unit,
    )

    var isAvailableCheck: () -> Boolean = { true }

    private val _pending = MutableStateFlow<Request?>(null)
    val pending: StateFlow<Request?> = _pending.asStateFlow()

    fun ensure(onResult: (Boolean) -> Unit) {
        if (isAvailableCheck()) {
            onResult(true)
            return
        }
        _pending.value = Request(onResult)
    }

    fun dismiss() {
        val current = _pending.value
        _pending.value = null
        current?.onResult?.invoke(false)
    }

    fun complete(success: Boolean) {
        val current = _pending.value
        _pending.value = null
        current?.onResult?.invoke(success)
    }
}
