@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.cluvex.zedsecure.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cluvex.zedsecure.platform.AppInfo
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.*
import dev.cluvex.zedsecure.ui.bento.Bento
import dev.cluvex.zedsecure.ui.bento.BentoCard
import dev.cluvex.zedsecure.ui.bento.BentoToneKind
import dev.cluvex.zedsecure.ui.bento.BentoType
import dev.cluvex.zedsecure.ui.glass.Glass
import dev.cluvex.zedsecure.ui.glass.GlassType
import dev.cluvex.zedsecure.ui.glass.GlassZMark
import dev.cluvex.zedsecure.ui.glass.glassCell
import dev.cluvex.zedsecure.ui.glass.glassHighlight
import dev.cluvex.zedsecure.ui.platform.LocalPlatform
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val TELEGRAM_URL = "https://t.me/CluvexStudio"
private const val GITHUB_URL = "https://github.com/CluvexStudio"
private const val APP_SOURCE_URL = "https://github.com/CluvexStudio/ZedSecure"
private const val XRAY_SOURCE_URL = "https://github.com/CluvexStudio/Xray-core"
private const val LIB_SOURCE_URL = "https://github.com/CluvexStudio/AndroidLibXrayLite"
private const val LICENSE_URL = "https://github.com/CluvexStudio/ZedSecure/blob/main/LICENSE"

@Composable
internal fun AboutPage(contentPadding: PaddingValues, modifier: Modifier) {
    val platform = LocalPlatform.current
    SettingsPageScaffold(
        title = stringResource(Res.string.settings_about),
        art = SettingsArts.About,
        contentPadding = contentPadding,
        modifier = modifier,
    ) {
        AboutHero()
        SettingsGroup(stringResource(Res.string.about_community)) {
            SettingsMenuRow(
                title = stringResource(Res.string.about_telegram),
                subtitle = "t.me/CluvexStudio",
                art = SettingsArts.Telegram,
            ) { platform.openUri(TELEGRAM_URL) }
            SettingsMenuRow(
                title = stringResource(Res.string.about_github),
                subtitle = "github.com/CluvexStudio",
                art = SettingsArts.GitHub,
            ) { platform.openUri(GITHUB_URL) }
        }
        SettingsGroup(stringResource(Res.string.about_source_title)) {
            SettingsMenuRow(
                title = stringResource(Res.string.about_source_app),
                subtitle = "github.com/CluvexStudio/ZedSecure",
                art = SettingsArts.AppSource,
            ) { platform.openUri(APP_SOURCE_URL) }
            SettingsMenuRow(
                title = stringResource(Res.string.about_source_xray),
                subtitle = "github.com/CluvexStudio/Xray-core",
                art = SettingsArts.Core,
            ) { platform.openUri(XRAY_SOURCE_URL) }
            SettingsMenuRow(
                title = stringResource(Res.string.about_source_lib),
                subtitle = "github.com/CluvexStudio/AndroidLibXrayLite",
                art = SettingsArts.SingBox,
            ) { platform.openUri(LIB_SOURCE_URL) }
            SettingsMenuRow(
                title = stringResource(Res.string.about_license),
                subtitle = "AGPL-3.0",
                art = SettingsArts.License,
            ) { platform.openUri(LICENSE_URL) }
        }
        AboutFootnote(stringResource(Res.string.about_source_body))
    }
}

@Composable
private fun AboutHero() {
    val name = stringResource(Res.string.app_name)
    val version = stringResource(Res.string.about_version, AppInfo.versionName)
    val builtOn = stringResource(Res.string.about_built_on)
    when {
        Glass.active -> GlassHero(name, version, builtOn)
        Bento.active -> BentoHero(name, version, builtOn)
        else -> PixelHero(name, version, builtOn)
    }
}

@Composable
private fun GlassHero(name: String, version: String, builtOn: String) {
    val c = Glass.colors
    val tile = RoundedCornerShape(26.dp)
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .glassCell(c, RoundedCornerShape(26.dp))
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(96.dp)
                .shadow(14.dp, tile, clip = false, ambientColor = Color(0xFFC7F24E).copy(alpha = 0.5f), spotColor = Color(0xFFC7F24E).copy(alpha = 0.5f))
                .clip(tile)
                .background(
                    Brush.verticalGradient(
                        0f to Color(0xFFDDFB86),
                        0.6f to Color(0xFFC7F24E),
                        1f to Color(0xFFB6E23B),
                    ),
                )
                .glassHighlight(tile, Color.White.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center,
        ) {
            GlassZMark(Color(0xFF15170B), Modifier.size(62.dp))
        }
        Box(Modifier.size(6.dp))
        Text(name, style = GlassType.title2().copy(fontSize = 26.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold), color = c.label)
        Text(version, style = GlassType.headline(), color = c.label2)
        Text(builtOn, style = GlassType.footnote(), color = c.label3, textAlign = TextAlign.Center)
    }
}

@Composable
private fun BentoHero(name: String, version: String, builtOn: String) {
    val c = Bento.colors
    BentoCard(
        tone = c.tone(BentoToneKind.Stage),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
        motif = MaterialShapes.Cookie9Sided,
        motifSize = 220.dp,
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Image(
            painterResource(Res.drawable.ic_zed_mark),
            contentDescription = null,
            modifier = Modifier.size(72.dp),
        )
        Text(name, style = BentoType.display(), color = c.tone(BentoToneKind.Stage).content)
        Text(version, style = BentoType.rowTitle(), color = c.tone(BentoToneKind.Stage).content.copy(alpha = 0.86f))
        Text(builtOn, style = BentoType.body(), color = c.tone(BentoToneKind.Stage).content.copy(alpha = 0.72f))
    }
}

@Composable
private fun PixelHero(name: String, version: String, builtOn: String) {
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 24.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Image(
            painterResource(Res.drawable.ic_zed_mark),
            contentDescription = null,
            modifier = Modifier.size(92.dp).clip(RoundedCornerShape(24.dp)),
        )
        Box(Modifier.size(6.dp))
        Text(
            name,
            style = MaterialTheme.typography.headlineMediumEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            version,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            builtOn,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AboutFootnote(text: String) {
    val color = when {
        Glass.active -> Glass.colors.label3
        Bento.active -> Bento.colors.muted
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val style = when {
        Glass.active -> GlassType.footnote()
        Bento.active -> BentoType.body()
        else -> MaterialTheme.typography.bodySmall
    }
    Text(
        text,
        style = style,
        color = color,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 10.dp),
    )
}
