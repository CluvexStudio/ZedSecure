@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.cluvex.zedsecure.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.*
import dev.cluvex.zedsecure.platform.AppInfo
import dev.cluvex.zedsecure.ui.components.MorphingBlob
import dev.cluvex.zedsecure.ui.theme.ZedGradients
import dev.cluvex.zedsecure.ui.platform.rememberAppSheetState
import org.jetbrains.compose.resources.stringResource

@Composable
fun AboutSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberAppSheetState()) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MorphingBlob(
                progress = 0.5f,
                brush = ZedGradients.connected,
                modifier = Modifier.size(96.dp),
            ) {
                Text(
                    "Z",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            Text(
                stringResource(Res.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(Res.string.about_version, AppInfo.versionName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            AboutInfoCard(
                title = stringResource(Res.string.about_overview_title),
                body = stringResource(Res.string.about_overview_body),
            )
            AboutInfoCard(
                title = stringResource(Res.string.about_privacy_title),
                body = stringResource(Res.string.about_privacy_body),
            )
            AboutInfoCard(
                title = stringResource(Res.string.about_vault_title),
                body = stringResource(Res.string.about_vault_body),
            )
            AboutInfoCard(
                title = stringResource(Res.string.about_connection_title),
                body = stringResource(Res.string.about_connection_body),
            )
            AboutInfoCard(
                title = stringResource(Res.string.about_protocols_title),
                body = stringResource(Res.string.about_protocols_body),
            )
            AboutInfoCard(
                title = stringResource(Res.string.about_servers_title),
                body = stringResource(Res.string.about_servers_body),
            )
            AboutInfoCard(
                title = stringResource(Res.string.about_auto_title),
                body = stringResource(Res.string.about_auto_body),
            )
            AboutInfoCard(
                title = stringResource(Res.string.about_routing_title),
                body = stringResource(Res.string.about_routing_body),
            )
            AboutInfoCard(
                title = stringResource(Res.string.about_dns_title),
                body = stringResource(Res.string.about_dns_body),
            )
            AboutInfoCard(
                title = stringResource(Res.string.about_ghost_title),
                body = stringResource(Res.string.about_ghost_body),
            )
            AboutInfoCard(
                title = stringResource(Res.string.about_tor_title),
                body = stringResource(Res.string.about_tor_body),
            )
            AboutInfoCard(
                title = stringResource(Res.string.about_privacy_tools_title),
                body = stringResource(Res.string.about_privacy_tools_body),
            )
            AboutInfoCard(
                title = stringResource(Res.string.about_monitor_title),
                body = stringResource(Res.string.about_monitor_body),
            )
            AboutInfoCard(
                title = stringResource(Res.string.about_map_speed_title),
                body = stringResource(Res.string.about_map_speed_body),
            )
            AboutInfoCard(
                title = stringResource(Res.string.about_ui_title),
                body = stringResource(Res.string.about_ui_body),
            )
            AboutInfoCard(
                title = stringResource(Res.string.about_ai_title),
                body = stringResource(Res.string.about_ai_body),
            )
            AboutInfoCard(
                title = stringResource(Res.string.about_data_title),
                body = stringResource(Res.string.about_data_body),
            )
            AboutInfoCard(
                title = stringResource(Res.string.about_notifications_title),
                body = stringResource(Res.string.about_notifications_body),
            )
            AboutInfoCard(
                title = stringResource(Res.string.about_logs_title),
                body = stringResource(Res.string.about_logs_body),
            )
            AboutInfoCard(
                title = stringResource(Res.string.about_limits_title),
                body = stringResource(Res.string.about_limits_body),
            )

            Text(
                stringResource(Res.string.about_built_on),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            Spacer(Modifier.size(2.dp))
            AboutInfoCard(
                title = stringResource(Res.string.about_source_title),
                body = stringResource(Res.string.about_source_body),
            )
        }
    }
}

@Composable
private fun AboutInfoCard(title: String, body: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
