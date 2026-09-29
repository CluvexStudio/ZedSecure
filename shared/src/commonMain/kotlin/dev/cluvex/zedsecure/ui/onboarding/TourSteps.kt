package dev.cluvex.zedsecure.ui.onboarding

import androidx.compose.runtime.Composable
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun rememberTourSteps(): List<TourStep> = listOf(
    TourStep(
        target = TourTargets.CORE,
        title = stringResource(Res.string.tour_core_title),
        body = stringResource(Res.string.tour_core_body),
        destination = TourDestination.Home,
    ),
    TourStep(
        target = TourTargets.CONFIG_CARD,
        title = stringResource(Res.string.tour_config_title),
        body = stringResource(Res.string.tour_config_body),
        destination = TourDestination.Home,
    ),
    TourStep(
        target = TourTargets.TRAFFIC,
        title = stringResource(Res.string.tour_traffic_title),
        body = stringResource(Res.string.tour_traffic_body),
        destination = TourDestination.Home,
    ),
    TourStep(
        target = TourTargets.HOME_TOOLS,
        title = stringResource(Res.string.tour_tools_title),
        body = stringResource(Res.string.tour_tools_body),
        destination = TourDestination.Home,
    ),
    TourStep(
        target = TourTargets.NAV_SERVERS,
        title = stringResource(Res.string.tour_servers_tab_title),
        body = stringResource(Res.string.tour_servers_tab_body),
        destination = TourDestination.Home,
    ),
    TourStep(
        target = TourTargets.SERVERS_ADD,
        title = stringResource(Res.string.tour_add_title),
        body = stringResource(Res.string.tour_add_body),
        destination = TourDestination.Servers,
    ),
    TourStep(
        target = TourTargets.SERVERS_SUBS,
        title = stringResource(Res.string.tour_subs_title),
        body = stringResource(Res.string.tour_subs_body),
        destination = TourDestination.Servers,
    ),
    TourStep(
        target = TourTargets.SERVERS_PING,
        title = stringResource(Res.string.tour_ping_title),
        body = stringResource(Res.string.tour_ping_body),
        destination = TourDestination.Servers,
    ),
    TourStep(
        target = TourTargets.SERVERS_GROUPS,
        title = stringResource(Res.string.tour_groups_title),
        body = stringResource(Res.string.tour_groups_body),
        destination = TourDestination.Servers,
    ),
    TourStep(
        target = TourTargets.SERVER_CARD,
        title = stringResource(Res.string.tour_card_title),
        body = stringResource(Res.string.tour_card_body),
        destination = TourDestination.Servers,
    ),
    TourStep(
        target = TourTargets.SERVERS_MORE,
        title = stringResource(Res.string.tour_more_title),
        body = stringResource(Res.string.tour_more_body),
        destination = TourDestination.Servers,
    ),
    TourStep(
        target = TourTargets.NAV_SETTINGS,
        title = stringResource(Res.string.tour_settings_title),
        body = stringResource(Res.string.tour_settings_body),
        destination = TourDestination.Servers,
    ),

    TourStep(
        target = TourTargets.SETTINGS_SUPPORT,
        title = stringResource(Res.string.tour_set_support_title),
        body = stringResource(Res.string.tour_set_support_body),
        destination = TourDestination.Settings,
    ),
    TourStep(
        target = TourTargets.SETTINGS_LOOK,
        title = stringResource(Res.string.tour_set_look_title),
        body = stringResource(Res.string.tour_set_look_body),
        destination = TourDestination.Settings,
    ),
    TourStep(
        target = TourTargets.SETTINGS_ROUTING,
        title = stringResource(Res.string.tour_set_routing_title),
        body = stringResource(Res.string.tour_set_routing_body),
        destination = TourDestination.Settings,
    ),
    TourStep(
        target = TourTargets.SETTINGS_TUNNELS,
        title = stringResource(Res.string.tour_set_tunnels_title),
        body = stringResource(Res.string.tour_set_tunnels_body),
        destination = TourDestination.Settings,
    ),
    TourStep(
        target = TourTargets.SETTINGS_CORE,
        title = stringResource(Res.string.tour_set_core_title),
        body = stringResource(Res.string.tour_set_core_body),
        destination = TourDestination.Settings,
    ),
    TourStep(
        target = TourTargets.SETTINGS_TOOLS,
        title = stringResource(Res.string.tour_set_tools_title),
        body = stringResource(Res.string.tour_set_tools_body),
        destination = TourDestination.Settings,
    ),
    TourStep(
        target = TourTargets.SETTINGS_ABOUT,
        title = stringResource(Res.string.tour_set_about_title),
        body = stringResource(Res.string.tour_set_about_body),
        destination = TourDestination.Settings,
    ),
)
