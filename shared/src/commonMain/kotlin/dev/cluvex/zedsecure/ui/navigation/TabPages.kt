package dev.cluvex.zedsecure.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.cluvex.zedsecure.data.config.ConfigRepository
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.logs_title
import dev.cluvex.zedsecure.shared.resources.subs_title
import dev.cluvex.zedsecure.ui.home.LogPanel
import dev.cluvex.zedsecure.ui.servers.SubscriptionsPanel
import dev.cluvex.zedsecure.ui.settings.SettingsArts
import dev.cluvex.zedsecure.ui.settings.SettingsHeader
import dev.cluvex.zedsecure.ui.settings.SettingsPageScaffold
import org.jetbrains.compose.resources.stringResource

@Composable
fun SubscriptionsTabPage(repository: ConfigRepository, contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    SettingsPageScaffold(
        title = stringResource(Res.string.subs_title),
        art = SettingsArts.Update,
        contentPadding = contentPadding,
        modifier = modifier,
    ) {
        SubscriptionsPanel(
            repository = repository,
            onImported = {},
            showTitle = false,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
    }
}

@Composable
fun LogsTabPage(contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(contentPadding)) {
        Spacer(Modifier.height(8.dp))
        SettingsHeader(title = stringResource(Res.string.logs_title), art = SettingsArts.Ssh, scroll = rememberScrollState())
        Spacer(Modifier.height(6.dp))
        LogPanel(Modifier.fillMaxWidth().weight(1f).padding(start = 16.dp, end = 16.dp, bottom = 12.dp), fill = true, showTitle = false)
    }
}
