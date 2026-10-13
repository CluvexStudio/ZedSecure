package dev.cluvex.zedsecure.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cluvex.zedsecure.core.ConnectionFault
import dev.cluvex.zedsecure.core.CoreProbe
import dev.cluvex.zedsecure.data.net.GeoPreference
import dev.cluvex.zedsecure.data.net.NetworkInfo
import dev.cluvex.zedsecure.data.net.NetworkInfoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class InfoFocus { Ping, Place }

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
    private var pingJob: Job? = null
    private var placeJob: Job? = null

    fun toggleExpanded() {
        _expanded.value = !_expanded.value
        if (_expanded.value) refresh()
    }

    fun openDetails(focus: InfoFocus) {
        _expanded.value = true
        when (focus) {
            InfoFocus.Ping -> refreshPing()
            InfoFocus.Place -> refreshPlace()
        }
    }

    fun closeDetails() {
        _expanded.value = false
    }

    fun refreshPing() {
        if (job?.isActive == true) return
        pingJob?.cancel()
        _info.update { it.copy(pingLoading = true) }
        pingJob = viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) { repository.health(preferredDelayUrl) }
            _info.update { withHealth(it, outcome).copy(pingLoading = false) }
        }
    }

    fun refreshPlace() {
        if (job?.isActive == true) return
        placeJob?.cancel()
        _info.update { it.copy(placeLoading = true) }
        placeJob = viewModelScope.launch {
            val fresh = withContext(Dispatchers.IO) { repository.fetchInfo(preferredIpApiUrl, force = true) }
            _info.update { withPlace(it, fresh).copy(placeLoading = false) }
        }
    }

    fun refresh(settleDelayMs: Long = 0L, force: Boolean = false) {
        job?.cancel()
        pingJob?.cancel()
        placeJob?.cancel()
        _info.value = _info.value.copy(
            loading = true,
            pingLoading = true,
            placeLoading = true,
            error = null,
            fault = null,
        )
        job = viewModelScope.launch {
            if (settleDelayMs > 0) delay(settleDelayMs)

            val health = async(Dispatchers.IO) { repository.health(preferredDelayUrl) }
            val ping = async(Dispatchers.IO) { health.await().ms.takeIf { it > 0 }?.toInt() }
            val info = async(Dispatchers.IO) {
                var r = repository.fetchInfo(preferredIpApiUrl, force)
                if (r.ipv4 == null) {
                    delay(RETRY_DELAY_MS)
                    r = repository.fetchInfo(preferredIpApiUrl, force)
                }
                r
            }

            launch {
                val h = health.await()
                _info.update { withHealth(it, h).copy(pingLoading = false) }
            }

            val resolved = info.await()
            val h = health.await()
            _info.value = resolved.copy(
                loading = false,
                pingLoading = false,
                placeLoading = false,
                pingMs = ping.await() ?: resolved.pingMs,
                fault = if (h.ok) null else faultOf(h.error),
            )
        }
    }

    fun clear() {
        job?.cancel()
        pingJob?.cancel()
        placeJob?.cancel()
        _info.value = NetworkInfo()
        _expanded.value = false
    }

    private fun withHealth(current: NetworkInfo, outcome: CoreProbe.DelayOutcome): NetworkInfo =
        if (outcome.ok) {
            current.copy(pingMs = outcome.ms.toInt(), fault = null)
        } else {
            current.copy(fault = faultOf(outcome.error))
        }

    private fun withPlace(current: NetworkInfo, fresh: NetworkInfo): NetworkInfo {
        if (fresh.ipv4 == null && !GeoPreference.hasLocation(fresh)) {
            return current.copy(error = fresh.error ?: current.error)
        }
        return current.copy(
            ipv4 = fresh.ipv4,
            ipv6 = fresh.ipv6,
            isp = fresh.isp,
            city = fresh.city,
            country = fresh.country,
            countryCode = fresh.countryCode,
            error = null,
            viaTunnel = fresh.viaTunnel,
            underVpn = fresh.underVpn,
        )
    }

    private fun faultOf(error: String?): ConnectionFault? =
        error?.takeIf { it.isNotBlank() }?.let { ConnectionFault.classify(it) }

    private companion object {
        const val RETRY_DELAY_MS = 1_200L
    }
}
