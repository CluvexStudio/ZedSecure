package dev.cluvex.zedsecure.core

import dev.cluvex.zedsecure.domain.config.DeepLinkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DeepLinkBus {
    class Pending(val request: DeepLinkRequest)

    private val _pending = MutableStateFlow<Pending?>(null)
    val pending: StateFlow<Pending?> = _pending.asStateFlow()

    fun request(request: DeepLinkRequest) {
        _pending.value = Pending(request)
    }

    fun clear() {
        _pending.value = null
    }
}
