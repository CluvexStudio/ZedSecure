package dev.cluvex.zedsecure.ui.servers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cluvex.zedsecure.core.DeepLinkBus
import dev.cluvex.zedsecure.data.config.ConfigRepository
import dev.cluvex.zedsecure.domain.config.ConfigParseException
import dev.cluvex.zedsecure.domain.config.DeepLinkPreview
import dev.cluvex.zedsecure.domain.config.DeepLinkRequest
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.*
import dev.cluvex.zedsecure.ui.platform.LocalPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun DeepLinkImportHost(repository: ConfigRepository, onImported: () -> Unit) {
    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()
    val pending by DeepLinkBus.pending.collectAsStateWithLifecycle()
    val current = pending ?: return
    val request = current.request
    val preview = remember(current) { DeepLinkPreview.of(request) }
    val isSubscription = request is DeepLinkRequest.Subscription

    AlertDialog(
        onDismissRequest = { DeepLinkBus.clear() },
        icon = { Icon(painterResource(Res.drawable.ic_add_link), null) },
        title = {
            Text(
                stringResource(
                    if (isSubscription) Res.string.deeplink_subscription_title else Res.string.deeplink_config_title,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(
                        if (isSubscription) Res.string.deeplink_subscription_body else Res.string.deeplink_config_body,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                preview.name?.let { name ->
                    Text(
                        name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    preview.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (request is DeepLinkRequest.Subscription) {
                    Text(
                        request.url,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                DeepLinkBus.clear()

                scope.launch(Dispatchers.Default) {
                    val outcome = when (request) {
                        is DeepLinkRequest.Subscription -> repository.importSubscriptionUrl(request.url, request.name)
                        is DeepLinkRequest.ConfigText -> repository.importPasted(request.text)
                    }
                    outcome
                        .onSuccess { r ->
                            platform.toast(
                                when {
                                    r.subscription -> getString(Res.string.subs_imported, r.subscriptionName, r.count)
                                    r.count > 0 -> getString(Res.string.servers_imported, r.count)
                                    r.duplicates > 0 -> getString(Res.string.servers_already_added, r.duplicates)
                                    else -> getString(Res.string.config_invalid)
                                },
                            )
                            if (r.count > 0) launch(Dispatchers.Main) { onImported() }
                        }
                        .onFailure { e ->
                            if (e is ConfigRepository.SubscriptionNotFetchedException && e.savedForLater) {
                                platform.toast(getString(Res.string.subs_saved_unreachable, e.name))
                                return@onFailure
                            }
                            platform.toast(
                                getString(
                                    when {
                                        request is DeepLinkRequest.Subscription ||
                                            e is ConfigRepository.SubscriptionNotFetchedException -> Res.string.subs_failed
                                        else -> when ((e as? ConfigParseException)?.reason) {
                                            ConfigParseException.Reason.UnsupportedSsCipher -> Res.string.import_err_ss_cipher
                                            ConfigParseException.Reason.MissingSsCipher -> Res.string.import_err_ss_no_cipher
                                            ConfigParseException.Reason.UnsupportedSsPlugin -> Res.string.import_err_ss_plugin
                                            else -> Res.string.config_invalid
                                        }
                                    },
                                ),
                            )
                        }
                }
            }) {
                Text(
                    stringResource(
                        if (isSubscription) Res.string.deeplink_add_subscription else Res.string.deeplink_import,
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { DeepLinkBus.clear() }) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}
