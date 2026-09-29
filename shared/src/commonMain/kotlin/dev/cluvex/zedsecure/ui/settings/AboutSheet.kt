@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.cluvex.zedsecure.ui.settings

import org.jetbrains.compose.resources.DrawableResource

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.*
import dev.cluvex.zedsecure.platform.AppInfo
import dev.cluvex.zedsecure.ui.platform.LocalPlatform
import dev.cluvex.zedsecure.ui.components.MorphingBlob
import dev.cluvex.zedsecure.ui.theme.ZedGradients

private const val TELEGRAM_URL = "https://t.me/CluvexStudio"
private const val GITHUB_URL = "https://github.com/CluvexStudio"

private const val XRAY_SOURCE_URL = "https://github.com/CluvexStudio/Xray-core"
private const val LIB_SOURCE_URL = "https://github.com/CluvexStudio/AndroidLibXrayLite"

@Composable
fun AboutSheet(onDismiss: () -> Unit) {
    val platform = LocalPlatform.current

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
            Text(
                stringResource(Res.string.about_built_on),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.size(4.dp))

            LinkRow(
                iconRes = Res.drawable.ic_add_link,
                title = stringResource(Res.string.about_telegram),
                subtitle = "t.me/CluvexStudio",
                onClick = { platform.openUri(TELEGRAM_URL) },
            )
            LinkRow(
                iconRes = Res.drawable.ic_description,
                title = stringResource(Res.string.about_github),
                subtitle = "github.com/CluvexStudio",
                onClick = { platform.openUri(GITHUB_URL) },
            )

            Spacer(Modifier.size(8.dp))

            Text(
                stringResource(Res.string.about_source_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(Res.string.about_source_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            LinkRow(
                iconRes = Res.drawable.ic_description,
                title = stringResource(Res.string.about_source_xray),
                subtitle = "github.com/CluvexStudio/Xray-core",
                onClick = { platform.openUri(XRAY_SOURCE_URL) },
            )
            LinkRow(
                iconRes = Res.drawable.ic_description,
                title = stringResource(Res.string.about_source_lib),
                subtitle = "github.com/CluvexStudio/AndroidLibXrayLite",
                onClick = { platform.openUri(LIB_SOURCE_URL) },
            )
        }
    }
}

@Composable
private fun LinkRow(iconRes: DrawableResource, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(iconRes), null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(painterResource(Res.drawable.ic_chevron_right), null)
        }
    }
}
