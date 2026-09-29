package dev.cluvex.zedsecure.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cluvex.zedsecure.data.net.NetworkInfo
import dev.cluvex.zedsecure.data.net.NetworkInfoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: NetworkInfoRepository = NetworkInfoRepository(),
) : ViewModel() {
    private val _info = MutableStateFlow(NetworkInfo())
    val info = _info.asStateFlow()

    var preferredIpApiUrl: String? = null

    var preferredDelayUrl: String? = null

    private val _expanded = MutableStateFlow(false)
    val expanded = _expanded.asStateFlow()

    private var job: Job? = null

    fun toggleExpanded() {
        _expanded.value = !_expanded.value
        if (_expanded.value) refresh()
    }

    fun refresh(settleDelayMs: Long = 0L) {
        job?.cancel()
        _info.value = _info.value.copy(loading = true, error = null, fault = null)
        job = viewModelScope.launch {
            if (settleDelayMs > 0) delay(settleDelayMs)

            val health = async(Dispatchers.IO) { repository.health(preferredDelayUrl) }
            val ping = async(Dispatchers.IO) { health.await().ms.takeIf { it > 0 }?.toInt() }
            val info = async(Dispatchers.IO) {
                var r = repository.fetchInfo(preferredIpApiUrl)
                if (r.ipv4 == null) {
                    delay(RETRY_DELAY_MS)
                    r = repository.fetchInfo(preferredIpApiUrl)
                }
                r
            }

            launch {
                val h = health.await()
                _info.value = if (h.ok) {
                    _info.value.copy(pingMs = h.ms.toInt(), fault = null)
                } else {
                    _info.value.copy(fault = h.error?.takeIf { it.isNotBlank() }
                        ?.let { dev.cluvex.zedsecure.core.ConnectionFault.classify(it) })
                }
            }

            val resolved = info.await()
            val h = health.await()
            _info.value = resolved.copy(
                loading = false,
                pingMs = ping.await() ?: resolved.pingMs,
                fault = if (h.ok) null else h.error?.takeIf { it.isNotBlank() }
                    ?.let { dev.cluvex.zedsecure.core.ConnectionFault.classify(it) },
            )
        }
    }

    fun clear() {
        job?.cancel()
        _info.value = NetworkInfo()
        _expanded.value = false
    }

    private companion object {
        const val RETRY_DELAY_MS = 1_200L
    }
}
