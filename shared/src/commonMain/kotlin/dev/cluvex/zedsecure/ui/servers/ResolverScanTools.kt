@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.cluvex.zedsecure.ui.servers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.cluvex.zedsecure.data.config.ConfigRepository
import dev.cluvex.zedsecure.domain.config.VpnProfile
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.*
import dev.cluvex.zedsecure.ui.platform.LocalPlatform
import dev.cluvex.zedsecure.ui.settings.DnsScannerPage
import dev.cluvex.zedsecure.ui.settings.ScanPick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ResolverScanTools(
    repository: ConfigRepository?,
    domain: String,
    recordType: String,
    current: String,
    profile: () -> VpnProfile?,
    onResolvers: (String) -> Unit,
) {
    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()
    var scanning by remember { mutableStateOf(false) }
    val canScan = platform.dnsScanner != null
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (canScan) {
            FilledTonalButton(
                onClick = { scanning = true },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Icon(painterResource(Res.drawable.ic_fill_radar), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.dnsscan_find), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        OutlinedButton(
            onClick = {
                scope.launch {
                    val local = withContext(Dispatchers.Default) { platform.systemDnsServers() }
                    if (local.isEmpty()) {
                        platform.toast(getString(Res.string.dnsscan_local_none))
                    } else {
                        val have = current.split('\n', ',').map { it.trim() }.filter { it.isNotEmpty() }
                        onResolvers((have + local).distinct().joinToString("\n"))
                        platform.toast(getString(Res.string.dnsscan_local_added, local.size))
                    }
                }
            },
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Icon(painterResource(Res.drawable.ic_fill_router), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.dnsscan_local), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    if (scanning) {
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { scanning = false }, sheetState = state) {
            DnsScannerPage(
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.94f),
                repository = repository,
                pick = ScanPick(
                    domain = domain.trim(),
                    recordType = recordType,
                    profile = profile(),
                    onApply = { list ->
                        if (list.isNotEmpty()) onResolvers(list.joinToString("\n"))
                        scanning = false
                    },
                ),
            )
        }
    }
}
