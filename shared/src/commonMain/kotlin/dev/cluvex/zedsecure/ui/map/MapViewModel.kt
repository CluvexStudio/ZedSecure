package dev.cluvex.zedsecure.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cluvex.zedsecure.data.map.WorldMap
import dev.cluvex.zedsecure.data.net.NetworkInfo
import dev.cluvex.zedsecure.data.net.NetworkInfoRepository
import dev.cluvex.zedsecure.core.VpnManager
import dev.cluvex.zedsecure.domain.model.ConnectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MapViewModel(
    private val repository: NetworkInfoRepository = NetworkInfoRepository(),
) : ViewModel() {
    private val _countries = MutableStateFlow<List<WorldMap.Country>>(emptyList())
    val countries = _countries.asStateFlow()

    private val _info = MutableStateFlow(NetworkInfo())
    val info = _info.asStateFlow()

    var preferredIpApiUrl: String? = null

    private var job: Job? = null

    init {
        viewModelScope.launch { _countries.value = WorldMap.countries() }
    }

    fun refresh(force: Boolean = false) {
        job?.cancel()
        _info.value = _info.value.copy(loading = true, pingLoading = true, error = null)
        job = viewModelScope.launch(Dispatchers.IO) {
            val ping = async {
                if (VpnManager.status.value.state == ConnectionState.Connected) runCatching { repository.realDelay() }.getOrNull() else null
            }
            val resolved = repository.fetchInfo(preferredIpApiUrl, force = force)
            _info.value = resolved.copy(loading = false, pingLoading = false, pingMs = ping.await() ?: resolved.pingMs)
        }
    }
}
