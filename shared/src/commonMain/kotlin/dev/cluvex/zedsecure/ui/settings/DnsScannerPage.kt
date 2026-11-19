package dev.cluvex.zedsecure.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cluvex.zedsecure.data.config.ConfigRepository
import dev.cluvex.zedsecure.domain.config.VpnProfile
import dev.cluvex.zedsecure.platform.currentTimeMillis
import dev.cluvex.zedsecure.scan.DnsPack
import dev.cluvex.zedsecure.scan.DnsScanHub
import dev.cluvex.zedsecure.scan.E2ePlan
import dev.cluvex.zedsecure.scan.FoundEntry
import dev.cluvex.zedsecure.scan.ResolverResult
import dev.cluvex.zedsecure.scan.ScanBackend
import dev.cluvex.zedsecure.scan.ScanCheckpoint
import dev.cluvex.zedsecure.scan.ScanRequest
import dev.cluvex.zedsecure.scan.ScanSpeed
import dev.cluvex.zedsecure.scan.ScanState
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.*
import dev.cluvex.zedsecure.ui.components.ProvidePageBack
import dev.cluvex.zedsecure.ui.format.ltrIsolate
import dev.cluvex.zedsecure.ui.glass.Glass
import dev.cluvex.zedsecure.ui.glass.GlassIcons
import dev.cluvex.zedsecure.ui.glass.IosColors
import dev.cluvex.zedsecure.ui.glass.glassCell
import dev.cluvex.zedsecure.ui.glass.glassHighlight
import dev.cluvex.zedsecure.ui.platform.BackHandler
import dev.cluvex.zedsecure.ui.platform.LocalPlatform
import dev.cluvex.zedsecure.ui.platform.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import java.util.Locale

@Serializable
internal data class ScanForm(
    val mode: String = "simple",
    val source: String = SRC_DEFAULT,
    val country: String = "",
    val count: Long = 5000,
    val ranges: String = "",
    val importName: String = "",
    val importLines: Int = 0,
    val lastPool: String = "working",
    val profileId: String = "",
    val tunnel: String = "",
    val type: String = "TXT",
    val domain: String = "google.com",
    val timeoutMs: Int = 2500,
    val port: Int = 53,
    val speed: String = "Normal",
    val transport: String = "udp",
    val tcp: Boolean = true,
    val dot: Boolean = false,
    val neighbors: Boolean = false,
    val blocked: String = "",
    val stopAfter: Int = 0,
    val e2eFull: Boolean = false,
    val e2eUrl: String = "http://www.gstatic.com/generate_204",
    val e2eConcurrency: Int = 4,
    val e2eMinScore: Int = 60,
    val rootCapture: Boolean = false,
    val rootPcap: Boolean = false,
    val rootPort53: Boolean = false,
) {
    fun resetSettings(): ScanForm {
        val d = ScanForm()
        return copy(
            domain = d.domain, timeoutMs = d.timeoutMs, port = d.port, speed = d.speed, transport = d.transport,
            tcp = d.tcp, dot = d.dot, neighbors = d.neighbors, blocked = d.blocked, stopAfter = d.stopAfter,
            e2eFull = d.e2eFull, e2eUrl = d.e2eUrl, e2eConcurrency = d.e2eConcurrency, e2eMinScore = d.e2eMinScore,
            rootCapture = false, rootPcap = false, rootPort53 = false, count = d.count,
        )
    }
}

class ScanPick(
    val domain: String,
    val recordType: String,
    val profile: VpnProfile?,
    val onApply: (List<String>) -> Unit,
)

private val formJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    coerceInputValues = true
}

private const val ROOT_FWMARK = 0x20000
private const val FORM_KEY = "dns_scan_form"
private const val RESULTS_KEY = "dns_scan_results"
private const val CHECKPOINT_KEY = "dns_scan_checkpoint"
private const val CHECKPOINT_REQ_KEY = "dns_scan_checkpoint_request"
private const val FOUND_KEY = "dns_pack_found"
private const val IMPORT_KEY = "dns_scan_import"
internal const val SRC_DEFAULT = "default"
private const val SRC_IMPORT = "import"
private const val SRC_COUNTRY = "country"
private const val SRC_RANGES = "ranges"
private const val SRC_IRAN = "iran"
private const val SRC_LAST = "last"
private val RECORD_TYPES = listOf("TXT", "NULL", "CNAME", "MX", "A", "AAAA")

private fun ScanForm.speedOf(): ScanSpeed = ScanSpeed.entries.firstOrNull { it.name == speed } ?: ScanSpeed.Normal

private fun saveCheckpoint(repository: ConfigRepository?, cp: ScanCheckpoint?, lastSeed: LongArray) {
    if (repository == null) return
    if (cp == null) {
        repository.putExtra(CHECKPOINT_KEY, null)
        repository.putExtra(CHECKPOINT_REQ_KEY, null)
        lastSeed[0] = 0L
        return
    }
    if (lastSeed[0] != cp.request.seed) {
        repository.putExtra(CHECKPOINT_REQ_KEY, DnsScanHub.json.encodeToString(ScanRequest.serializer(), cp.request))
        lastSeed[0] = cp.request.seed
    }
    repository.putExtra(CHECKPOINT_KEY, DnsScanHub.encodeCheckpoint(cp.copy(request = ScanRequest(seed = cp.request.seed))))
}

private fun loadCheckpoint(repository: ConfigRepository?): ScanCheckpoint? {
    val cp = DnsScanHub.decodeCheckpoint(repository?.getExtra(CHECKPOINT_KEY)) ?: return null
    val request = repository?.getExtra(CHECKPOINT_REQ_KEY)
        ?.let { runCatching { DnsScanHub.json.decodeFromString(ScanRequest.serializer(), it) }.getOrNull() }
        ?: return null
    return if (request.seed == cp.request.seed) cp.copy(request = request) else null
}

private fun workingOf(results: List<ResolverResult>) = results.filter { it.alive && it.score >= 50 }

private fun passedOf(results: List<ResolverResult>) = results.filter { it.e2e?.ok == true }

private suspend fun ScanForm.request(
    platform: Platform,
    repository: ConfigRepository?,
    last: List<ResolverResult>,
    found: List<FoundEntry>,
): Result<ScanRequest> = runCatching {
    val wait = timeoutMs.coerceIn(300, 20_000)
    val base = ScanRequest(
        domain = domain.trim().trim('.').ifBlank { "google.com" },
        port = port.coerceIn(1, 65535),
        rate = speedOf().rate,
        timeoutMs = wait,
        deepTimeoutMs = wait,
        transport = transport,
        tunnelDomain = tunnel.trim().trim('.'),
        recordTypes = listOf(type),
        checkTcp = tcp,
        checkDot = dot,
        neighbors = neighbors,
        blockedDomains = blocked.split(',', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() },
        stopAfter = stopAfter,
        deepConcurrency = if (platform.isComputer) 96 else 32,
        capture = rootCapture || rootPcap,
        sourcePort = if (rootPort53) 53 else 0,
    )
    when (source) {
        SRC_COUNTRY -> {
            val geo = platform.geoipFile ?: error("geo")
            base.copy(countries = listOf(country.ifBlank { "IR" }), geoipPath = geo, limit = count, order = "smart")
        }
        SRC_RANGES -> {
            require(ranges.isNotBlank()) { "empty" }
            base.copy(targets = listOf(ranges), order = "smart", excludeSpecial = false)
        }
        SRC_IRAN -> base.copy(targets = listOf(DnsPack.iranRanges(found).joinToString("\n")), limit = count, order = "smart", excludeSpecial = false)
        SRC_IMPORT -> {
            val text = repository?.getExtra(IMPORT_KEY).orEmpty()
            require(text.isNotBlank()) { "empty" }
            base.copy(targets = listOf(text), order = "given", excludeSpecial = false)
        }
        SRC_LAST -> {
            val list = if (lastPool == "e2e") passedOf(last) else workingOf(last)
            require(list.isNotEmpty()) { "empty" }
            base.copy(targets = listOf(list.joinToString("\n") { it.endpoint }), order = "given", excludeSpecial = false)
        }
        else -> base.copy(targets = listOf(DnsPack.defaultList(found).joinToString("\n")), order = "given", excludeSpecial = false)
    }
}

internal fun mergePool(text: String, add: List<String>): String {
    val have = text.split('\n', ',').map { it.trim() }.filter { it.isNotEmpty() }
    return (have + add).distinct().joinToString("\n")
}

private fun pickList(selected: Set<String>, results: List<ResolverResult>, fallback: Int): List<ResolverResult> {
    val chosen = results.filter { it.endpoint in selected }
    if (chosen.isNotEmpty()) return chosen
    return results.filter { it.score >= 50 }.take(fallback).ifEmpty { results.take(fallback) }
}

@Composable
internal fun DnsScannerPage(
    contentPadding: PaddingValues,
    modifier: Modifier,
    repository: ConfigRepository?,
    pick: ScanPick? = null,
    onPool: ((List<String>) -> Unit)? = null,
) {
    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()
    val native = remember { platform.dnsScanner }
    val state by DnsScanHub.state.collectAsState()
    var form by remember {
        mutableStateOf(
            (
                repository?.getExtra(FORM_KEY)
                    ?.let { runCatching { formJson.decodeFromString(ScanForm.serializer(), it) }.getOrNull() }
                    ?: ScanForm(country = (platform.simCountry ?: platform.mobileNetworkCountry ?: "IR").uppercase())
                ).let { f ->
                if (pick != null) f.copy(tunnel = pick.domain, type = pick.recordType.uppercase(), profileId = pick.profile?.id ?: "") else f
            },
        )
    }
    val update: (ScanForm) -> Unit = { f ->
        form = f
        repository?.putExtra(FORM_KEY, formJson.encodeToString(ScanForm.serializer(), if (pick != null) f.copy(profileId = "", tunnel = "") else f))
    }
    var showResults by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf(DnsPack.decodeFound(repository?.getExtra(FOUND_KEY))) }
    var checkpoint by remember { mutableStateOf(loadCheckpoint(repository)) }
    var rootOk by remember { mutableStateOf<Boolean?>(null) }
    var rootChecking by remember { mutableStateOf(false) }
    val network = remember { platform.networkLabel.orEmpty() }
    val otherVpn = remember(state.running) { platform.otherVpnActive() }
    LaunchedEffect(repository) {
        DnsScanHub.restore(repository?.getExtra(RESULTS_KEY))
        DnsScanHub.onFinished = { list ->
            repository?.putExtra(RESULTS_KEY, DnsScanHub.encode(list.take(300)))
            val merged = DnsPack.merge(DnsPack.decodeFound(repository?.getExtra(FOUND_KEY)), list, network, currentTimeMillis())
            repository?.putExtra(FOUND_KEY, DnsPack.encodeFound(merged))
            found = merged
        }
        val lastSeed = longArrayOf(0L)
        DnsScanHub.onCheckpoint = { cp ->
            saveCheckpoint(repository, cp, lastSeed)
            checkpoint = cp
        }
    }
    val profiles = remember(repository) { repository?.dnsProfiles().orEmpty() }
    val chosenProfile = pick?.profile ?: profiles.firstOrNull { it.id == form.profileId }
    val tester = platform.tunnelTester
    val e2eReady = tester != null && chosenProfile != null && tester.supports(chosenProfile)
    val e2ePlan: (Boolean) -> E2ePlan? = { auto ->
        if (e2eReady && tester != null && chosenProfile != null) {
            E2ePlan(
                tester = tester,
                profile = chosenProfile,
                full = form.e2eFull,
                url = form.e2eUrl,
                timeoutMs = if (form.e2eFull) 18_000 else 10_000,
                concurrency = form.e2eConcurrency,
                minScore = form.e2eMinScore,
                auto = auto,
            )
        } else {
            null
        }
    }
    val wantsRoot = form.rootCapture || form.rootPcap || form.rootPort53
    val launch: (ScanRequest, List<ResolverResult>) -> Unit = { request, previous ->
        val root = if (wantsRoot && rootOk == true) platform.rootScanner else null
        val engine: ScanBackend? = root ?: native
        if (engine != null) {
            val req = when {
                root == null -> request.copy(capture = false, pcapPath = "", sourcePort = 0)
                form.rootPcap -> request.copy(fwmark = ROOT_FWMARK, pcapPath = "/sdcard/Download/zedsecure-dns-${currentTimeMillis() / 1000}.pcap")
                else -> request.copy(fwmark = ROOT_FWMARK)
            }
            DnsScanHub.start(engine, req, previous, e2ePlan(form.mode == "simple"))
        }
    }
    val onStart: () -> Unit = {
        if (!busy) {
            busy = true
            scope.launch {
                if (wantsRoot && rootOk == null && platform.rootScanner != null) {
                    rootOk = withContext(Dispatchers.IO) { platform.hasRoot() }
                }
                val result = withContext(Dispatchers.Default) { form.request(platform, repository, state.results, found) }
                busy = false
                result.onSuccess { request ->
                    checkpoint = null
                    saveCheckpoint(repository, null, longArrayOf(0L))
                    launch(request, emptyList())
                }.onFailure { e ->
                    platform.toast(
                        when (e.message) {
                            "geo" -> getString(Res.string.dnsscan_geo_missing)
                            "empty" -> getString(Res.string.dnsscan_custom_empty)
                            else -> e.message ?: e.toString()
                        },
                    )
                }
            }
        }
    }

    if (showResults && state.results.isNotEmpty()) {
        BackHandler(enabled = true) { showResults = false }
        ProvidePageBack({ showResults = false }) {
            ScanResultsView(
                contentPadding = contentPadding,
                modifier = modifier,
                state = state,
                repository = repository,
                pick = pick,
                onPool = onPool,
                e2eReady = e2eReady,
                onRunE2e = { endpoints -> e2ePlan(false)?.let { plan -> DnsScanHub.runE2e(plan, endpoints) } },
                onCleared = {
                    repository?.putExtra(RESULTS_KEY, null)
                    showResults = false
                },
            )
        }
        return
    }

    SettingsPageScaffold(
        title = stringResource(Res.string.dnsscan_title),
        subtitle = stringResource(Res.string.dnsscan_subtitle),
        art = SettingsArts.Scanner,
        contentPadding = contentPadding,
        modifier = modifier,
    ) {
        if (native == null) {
            SettingsGroup {
                SettingsInfoRow(stringResource(Res.string.dnsscan_unavailable_title), stringResource(Res.string.dnsscan_unavailable_body))
            }
            return@SettingsPageScaffold
        }
        if (otherVpn) {
            SettingsGroup {
                SettingsInfoRow(stringResource(Res.string.dnsscan_vpn_title), stringResource(Res.string.dnsscan_vpn_body))
            }
        }
        if (e2eReady && !state.running) {
            SettingsGroup {
                SettingsChoiceRow(
                    title = stringResource(Res.string.dnsscan_mode),
                    summary = stringResource(if (form.mode == "simple") Res.string.dnsscan_mode_sum_simple else Res.string.dnsscan_mode_sum_advanced),
                    options = listOf(
                        "simple" to stringResource(Res.string.dnsscan_mode_simple),
                        "advanced" to stringResource(Res.string.dnsscan_mode_advanced),
                    ),
                    selected = form.mode,
                    onSelected = { update(form.copy(mode = it)) },
                )
            }
        }
        val active = state.running || DnsScanHub.e2eRunning
        if (active || state.error != null) ScanStatus(state)
        ScanButton(running = state.running, busy = busy, onStart = onStart, onStop = { DnsScanHub.stop() })
        if (state.results.isNotEmpty()) {
            val working = workingOf(state.results).size
            val passed = passedOf(state.results).size
            SettingsGroup {
                SettingsMenuRow(
                    stringResource(Res.string.dnsscan_results, state.results.size),
                    if (passed > 0) {
                        stringResource(Res.string.dnsscan_results_sum_e2e, working, passed)
                    } else {
                        stringResource(Res.string.dnsscan_results_sum, working)
                    },
                ) { showResults = true }
            }
        }
        val cp = checkpoint
        if (!state.running && cp != null && pick == null) {
            SettingsGroup {
                SettingsInfoRow(
                    stringResource(Res.string.dnsscan_resume_title),
                    stringResource(Res.string.dnsscan_resume_body, cp.walked.toInt(), cp.planned.toInt()),
                )
                SettingsActionRow(
                    title = stringResource(Res.string.dnsscan_resume),
                    onClick = {
                        checkpoint = null
                        launch(cp.request.copy(skip = cp.walked), cp.results)
                    },
                )
                SettingsActionRow(
                    title = stringResource(Res.string.dnsscan_fresh),
                    onClick = {
                        checkpoint = null
                        saveCheckpoint(repository, null, longArrayOf(0L))
                    },
                )
            }
        }
        if (!state.running) {
            ScanSettingsGroup(
                form = form,
                update = update,
                repository = repository,
                platform = platform,
                profiles = profiles,
                pick = pick,
                e2eReady = e2eReady,
                rootOk = rootOk,
                rootChecking = rootChecking,
                onCheckRoot = {
                    if (!rootChecking) {
                        rootChecking = true
                        scope.launch {
                            rootOk = withContext(Dispatchers.IO) { platform.hasRoot() }
                            rootChecking = false
                        }
                    }
                },
            )
            ResolverListGroup(
                form = form,
                update = update,
                repository = repository,
                platform = platform,
                found = found,
                last = state.results,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ScanSettingsGroup(
    form: ScanForm,
    update: (ScanForm) -> Unit,
    repository: ConfigRepository?,
    platform: Platform,
    profiles: List<VpnProfile>,
    pick: ScanPick?,
    e2eReady: Boolean,
    rootOk: Boolean?,
    rootChecking: Boolean,
    onCheckRoot: () -> Unit,
) {
    var more by remember { mutableStateOf(false) }
    SettingsGroup(stringResource(Res.string.dnsscan_settings)) {
        SettingsEditRow(
            title = stringResource(Res.string.dnsscan_domain),
            value = form.domain,
            placeholder = "google.com",
            onValueChanged = { update(form.copy(domain = it.trim())) },
        )
        SettingsEditRow(
            title = stringResource(Res.string.dnsscan_timeout),
            value = form.timeoutMs.toString(),
            numeric = true,
            placeholder = "2500",
            onValueChanged = { v -> v.filter { it.isDigit() }.take(5).toIntOrNull()?.let { update(form.copy(timeoutMs = it)) } },
        )
        SettingsEditRow(
            title = stringResource(Res.string.dnsscan_port),
            value = form.port.toString(),
            numeric = true,
            placeholder = "53",
            onValueChanged = { v -> v.filter { it.isDigit() }.take(5).toIntOrNull()?.let { update(form.copy(port = it)) } },
        )
        val speed = form.speedOf()
        SettingsChoiceRow(
            title = stringResource(Res.string.dnsscan_speed_title),
            summary = stringResource(Res.string.dnsscan_speed_sum, speed.rate),
            options = listOf(
                ScanSpeed.Gentle.name to stringResource(Res.string.dnsscan_speed_gentle),
                ScanSpeed.Normal.name to stringResource(Res.string.dnsscan_speed_normal),
                ScanSpeed.Fast.name to stringResource(Res.string.dnsscan_speed_fast),
                ScanSpeed.Max.name to stringResource(Res.string.dnsscan_speed_max),
            ),
            selected = speed.name,
            onSelected = { update(form.copy(speed = it)) },
        )
        SettingsChoiceRow(
            title = stringResource(Res.string.dnsscan_transport),
            summary = null,
            options = listOf("udp" to "UDP", "tcp" to "TCP", "both" to stringResource(Res.string.dnsscan_both)),
            selected = form.transport,
            onSelected = { update(form.copy(transport = it)) },
        )
        if (e2eReady) {
            SettingsSwitchRow(
                title = stringResource(Res.string.dnsscan_e2e_full),
                summary = stringResource(Res.string.dnsscan_e2e_full_sum),
                checked = form.e2eFull,
                onCheckedChange = { update(form.copy(e2eFull = it)) },
            )
            if (form.e2eFull) {
                SettingsEditRow(
                    title = stringResource(Res.string.dnsscan_e2e_url),
                    value = form.e2eUrl,
                    placeholder = "http://www.gstatic.com/generate_204",
                    onValueChanged = { update(form.copy(e2eUrl = it.trim())) },
                )
            }
            SettingsChoiceRow(
                title = stringResource(Res.string.dnsscan_e2e_concurrency),
                summary = null,
                options = listOf(1 to "1", 2 to "2", 4 to "4", 6 to "6", 10 to "10"),
                selected = form.e2eConcurrency,
                onSelected = { update(form.copy(e2eConcurrency = it)) },
            )
        }
        SettingsActionRow(
            title = stringResource(if (more) Res.string.dnsscan_less else Res.string.dnsscan_more_options),
            onClick = { more = !more },
        )
        if (more) {
            if (pick == null && repository != null && profiles.isNotEmpty()) {
                SettingsListRow(
                    title = stringResource(Res.string.dnsscan_from_config),
                    options = listOf("" to stringResource(Res.string.option_none)) + profiles.map { it.id to it.name },
                    selected = form.profileId.takeIf { id -> profiles.any { it.id == id } } ?: "",
                    onSelected = { id ->
                        val p = profiles.firstOrNull { it.id == id }
                        update(
                            if (p == null) {
                                form.copy(profileId = "")
                            } else {
                                form.copy(profileId = id, tunnel = repository.tunnelDomainOf(p), type = repository.tunnelRecordTypeOf(p))
                            },
                        )
                    },
                )
            }
            SettingsEditRow(
                title = stringResource(Res.string.dnsscan_tunnel),
                value = form.tunnel,
                placeholder = "t.example.com",
                onValueChanged = { update(form.copy(tunnel = it.trim())) },
            )
            if (form.tunnel.isNotBlank()) {
                SettingsListRow(
                    title = stringResource(Res.string.dnsscan_type),
                    options = RECORD_TYPES.map { it to it },
                    selected = form.type.uppercase().takeIf { it in RECORD_TYPES } ?: "TXT",
                    onSelected = { update(form.copy(type = it)) },
                )
            }
            SettingsSwitchRow(title = stringResource(Res.string.dnsscan_tcp), checked = form.tcp, onCheckedChange = { update(form.copy(tcp = it)) })
            SettingsSwitchRow(title = stringResource(Res.string.dnsscan_dot), checked = form.dot, onCheckedChange = { update(form.copy(dot = it)) })
            SettingsSwitchRow(
                title = stringResource(Res.string.dnsscan_neighbors),
                summary = stringResource(Res.string.dnsscan_neighbors_sum),
                checked = form.neighbors,
                onCheckedChange = { update(form.copy(neighbors = it)) },
            )
            SettingsChoiceRow(
                title = stringResource(Res.string.dnsscan_stop_after),
                summary = null,
                options = listOf(0 to stringResource(Res.string.dnsscan_off), 10 to "10", 50 to "50", 200 to "200"),
                selected = form.stopAfter,
                onSelected = { update(form.copy(stopAfter = it)) },
            )
            SettingsEditRow(
                title = stringResource(Res.string.dnsscan_blocked),
                value = form.blocked,
                placeholder = "twitter.com, youtube.com",
                onValueChanged = { update(form.copy(blocked = it)) },
            )
            if (platform.rootScanner != null) RootRows(form, update, rootOk, rootChecking, onCheckRoot)
            SettingsActionRow(title = stringResource(Res.string.dnsscan_reset), onClick = { update(form.resetSettings()) })
        }
    }
}

@Composable
private fun RootRows(form: ScanForm, update: (ScanForm) -> Unit, rootOk: Boolean?, rootChecking: Boolean, onCheckRoot: () -> Unit) {
    val wantsRoot = form.rootCapture || form.rootPcap || form.rootPort53
    when {
        rootChecking -> SettingsInfoRow(stringResource(Res.string.dnsscan_root), stringResource(Res.string.dnsscan_root_checking))
        rootOk == false -> SettingsActionRow(
            title = stringResource(Res.string.dnsscan_root_enable),
            summary = stringResource(Res.string.dnsscan_root_none),
            onClick = onCheckRoot,
        )
        rootOk == null && !wantsRoot -> SettingsActionRow(
            title = stringResource(Res.string.dnsscan_root_enable),
            summary = stringResource(Res.string.dnsscan_root_enable_sum),
            onClick = onCheckRoot,
        )
        else -> {
            SettingsSwitchRow(
                title = stringResource(Res.string.dnsscan_root_capture),
                summary = stringResource(Res.string.dnsscan_root_capture_sum),
                checked = form.rootCapture,
                onCheckedChange = { update(form.copy(rootCapture = it)) },
            )
            SettingsSwitchRow(
                title = stringResource(Res.string.dnsscan_root_pcap),
                summary = stringResource(Res.string.dnsscan_root_pcap_sum),
                checked = form.rootPcap,
                onCheckedChange = { update(form.copy(rootPcap = it)) },
            )
            SettingsSwitchRow(
                title = stringResource(Res.string.dnsscan_root_port53),
                summary = stringResource(Res.string.dnsscan_root_port53_sum),
                checked = form.rootPort53,
                onCheckedChange = { update(form.copy(rootPort53 = it)) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResolverListGroup(
    form: ScanForm,
    update: (ScanForm) -> Unit,
    repository: ConfigRepository?,
    platform: Platform,
    found: List<FoundEntry>,
    last: List<ResolverResult>,
) {
    val scope = rememberCoroutineScope()
    var defaultCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(found.size) { defaultCount = DnsPack.defaultList(found).size }
    SettingsGroup(stringResource(Res.string.dnsscan_list)) {
        SettingsSegment {
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    SRC_DEFAULT to Res.string.dnsscan_src_default,
                    SRC_IMPORT to Res.string.dnsscan_import,
                    SRC_COUNTRY to Res.string.dnsscan_source_country,
                    SRC_RANGES to Res.string.dnsscan_src_ranges,
                    SRC_IRAN to Res.string.dnsscan_src_iran,
                    SRC_LAST to Res.string.dnsscan_src_last,
                ).forEach { (key, label) ->
                    ScanChip(stringResource(label), form.source == key) { update(form.copy(source = key)) }
                }
            }
        }
        when (form.source) {
            SRC_IMPORT -> {
                SettingsActionRow(
                    title = stringResource(Res.string.dnsscan_import_pick),
                    summary = if (form.importLines > 0) {
                        stringResource(Res.string.dnsscan_import_sum, form.importLines, form.importName)
                    } else {
                        stringResource(Res.string.dnsscan_import_none)
                    },
                    onClick = {
                        scope.launch {
                            val file = platform.pickFileBytes() ?: return@launch
                            val text = file.bytes.decodeToString()
                            val lines = text.lineSequence().count { it.isNotBlank() && !it.trimStart().startsWith("#") }
                            repository?.putExtra(IMPORT_KEY, text)
                            update(form.copy(importName = file.name, importLines = lines))
                        }
                    },
                )
            }
            SRC_COUNTRY -> {
                val current = form.country.ifBlank { "IR" }
                SettingsListRow(
                    title = stringResource(Res.string.dnsscan_country),
                    options = (listOf(current) + DnsPack.countries).distinct().map { it to it },
                    selected = current,
                    onSelected = { update(form.copy(country = it)) },
                )
                CountRow(form, update)
            }
            SRC_RANGES -> {
                SettingsEditRow(
                    title = stringResource(Res.string.dnsscan_custom),
                    value = form.ranges,
                    placeholder = "5.160.0.0/16\n2.144.3.1-2.144.3.254\n78.157.42.100\n1.2.3.4:53",
                    onValueChanged = { update(form.copy(ranges = it)) },
                )
                SettingsActionRow(
                    title = stringResource(Res.string.dnsscan_paste),
                    onClick = {
                        platform.readClipboard()?.takeIf { it.isNotBlank() }?.let { update(form.copy(ranges = (form.ranges + "\n" + it).trim())) }
                    },
                )
            }
            SRC_IRAN -> {
                SettingsInfoRow(
                    stringResource(Res.string.dnsscan_src_iran),
                    stringResource(Res.string.dnsscan_iran_sum, DnsPack.iranRanges(found).size),
                )
                CountRow(form, update)
            }
            SRC_LAST -> {
                SettingsChoiceRow(
                    title = stringResource(Res.string.dnsscan_src_last),
                    summary = null,
                    options = listOf(
                        "working" to stringResource(Res.string.dnsscan_last_working, workingOf(last).size),
                        "e2e" to stringResource(Res.string.dnsscan_last_e2e, passedOf(last).size),
                    ),
                    selected = form.lastPool,
                    onSelected = { update(form.copy(lastPool = it)) },
                )
            }
            else -> {
                SettingsInfoRow(
                    stringResource(Res.string.dnsscan_src_default),
                    stringResource(Res.string.dnsscan_default_sum, count(defaultCount.toLong()), found.size),
                )
            }
        }
    }
}

@Composable
private fun CountRow(form: ScanForm, update: (ScanForm) -> Unit) {
    SettingsEditRow(
        title = stringResource(Res.string.dnsscan_count),
        value = if (form.count > 0) form.count.toString() else "",
        numeric = true,
        placeholder = stringResource(Res.string.dnsscan_sample_all),
        onValueChanged = { v -> update(form.copy(count = v.filter { it.isDigit() }.take(9).toLongOrNull() ?: 0L)) },
    )
}

@Composable
private fun ScanResultsView(
    contentPadding: PaddingValues,
    modifier: Modifier,
    state: ScanState,
    repository: ConfigRepository?,
    pick: ScanPick?,
    onPool: ((List<String>) -> Unit)?,
    e2eReady: Boolean,
    onRunE2e: (List<String>) -> Unit,
    onCleared: () -> Unit,
) {
    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(setOf<String>()) }
    var shown by remember { mutableIntStateOf(40) }
    var filter by remember { mutableStateOf("all") }
    var sort by remember { mutableStateOf("score") }
    var query by remember { mutableStateOf("") }
    var pickConfig by remember { mutableStateOf(false) }
    val visible = remember(state.results, filter, sort, query, selected) {
        state.results
            .filter {
                when (filter) {
                    "good" -> it.alive && it.score >= 50
                    "tunnel" -> it.tunnel == true
                    "e2e" -> it.e2e?.ok == true
                    "selected" -> it.endpoint in selected
                    else -> true
                }
            }
            .filter { query.isBlank() || it.ip.contains(query.trim()) || (DnsPack.nameOf(it.ip)?.contains(query.trim(), ignoreCase = true) == true) }
            .let { list ->
                when (sort) {
                    "speed" -> list.sortedBy { if (it.alive) it.rtt else Int.MAX_VALUE }
                    "e2e" -> list.sortedWith(compareBy<ResolverResult> { if (it.e2e?.ok == true) it.e2e.totalMs else Int.MAX_VALUE }.thenByDescending { it.score })
                    "ip" -> list.sortedBy { it.ip }
                    else -> list
                }
            }
    }
    SettingsPageScaffold(
        title = stringResource(Res.string.dnsscan_results, state.results.size),
        contentPadding = contentPadding,
        modifier = modifier,
    ) {
        ScanStatus(state)
        if (state.running) ScanButton(running = true, busy = false, onStart = {}, onStop = { DnsScanHub.stop() })
        if (pick != null) {
            ApplyButton { pick.onApply(pickList(selected, visible, 8).map { it.endpoint }) }
        }
        val labels = ResultLabels.current()
        SettingsGroup {
            ResultTools(
                filter = filter,
                onFilter = { filter = it; shown = 40 },
                sort = sort,
                onSort = { sort = it },
                query = query,
                onQuery = { query = it },
                hasTunnel = state.results.any { it.tunnel != null },
                hasE2e = state.results.any { it.e2e != null },
                hasSelection = selected.isNotEmpty(),
            )
            visible.take(shown).forEach { r ->
                val key = r.endpoint
                val name = DnsPack.nameOf(r.ip)
                SettingsRow(
                    title = ltrIsolate(key) + (name?.let { "  ·  $it" } ?: ""),
                    description = metrics(r, labels),
                    trailing = { ScoreBadge(r.score, key in selected, r.e2e?.ok) },
                    onClick = { selected = if (key in selected) selected - key else selected + key },
                )
            }
            if (visible.size > shown) {
                SettingsActionRow(title = stringResource(Res.string.dnsscan_more, visible.size - shown), onClick = { shown += 40 })
            }
        }
        SettingsGroup {
            if (e2eReady && !DnsScanHub.e2eRunning) {
                SettingsActionRow(
                    title = stringResource(Res.string.dnsscan_e2e_run),
                    summary = stringResource(Res.string.dnsscan_e2e_run_sum),
                    onClick = { onRunE2e(pickList(selected, visible.filter { it.alive && it.tunnel != false }, 30).map { it.endpoint }) },
                )
            }
            SettingsActionRow(
                title = stringResource(Res.string.dnsscan_copy),
                summary = stringResource(Res.string.dnsscan_copy_sum),
                onClick = {
                    val list = pickList(selected, visible, 10)
                    platform.copyToClipboard(list.joinToString("\n") { it.endpoint })
                    scope.launch { platform.toast(getString(Res.string.dnsscan_copied, list.size)) }
                },
            )
            SettingsActionRow(
                title = stringResource(Res.string.dnsscan_share),
                onClick = { platform.shareText(pickList(selected, visible, visible.size).joinToString("\n") { it.endpoint }) },
            )
            if (pick == null && onPool != null) {
                SettingsActionRow(
                    title = stringResource(Res.string.dnsscan_pool),
                    summary = stringResource(Res.string.dnsscan_pool_sum),
                    onClick = {
                        val list = pickList(selected, visible, 20).map { it.endpoint }
                        onPool(list)
                        scope.launch { platform.toast(getString(Res.string.dnsscan_pooled, list.size)) }
                    },
                )
            }
            if (pick == null && repository != null) {
                SettingsActionRow(
                    title = stringResource(Res.string.dnsscan_use),
                    summary = stringResource(Res.string.dnsscan_use_sum),
                    onClick = { pickConfig = true },
                )
            }
        }
        if (!state.running) {
            SettingsGroup {
                SettingsActionRow(
                    title = stringResource(Res.string.dnsscan_clear),
                    onClick = {
                        DnsScanHub.clear()
                        onCleared()
                    },
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (pickConfig && repository != null) {
        UseInConfigDialog(
            profiles = repository.dnsProfiles(),
            onDismiss = { pickConfig = false },
            onPick = { profile ->
                pickConfig = false
                val list = pickList(selected, state.results, 8).map { it.endpoint }
                val saved = repository.setResolvers(profile.id, list)
                scope.launch {
                    platform.toast(
                        if (saved != null) getString(Res.string.dnsscan_used, profile.name) else getString(Res.string.dnsscan_use_none),
                    )
                }
            },
        )
    }
}

@Composable
private fun ResultTools(
    filter: String,
    onFilter: (String) -> Unit,
    sort: String,
    onSort: (String) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    hasTunnel: Boolean,
    hasE2e: Boolean,
    hasSelection: Boolean,
) {
    SettingsSegment {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchBox(query, onQuery)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ScanChip(stringResource(Res.string.dnsscan_filter_all), filter == "all") { onFilter("all") }
                ScanChip(stringResource(Res.string.dnsscan_filter_good), filter == "good") { onFilter("good") }
                if (hasTunnel) ScanChip(stringResource(Res.string.dnsscan_filter_tunnel), filter == "tunnel") { onFilter("tunnel") }
                if (hasE2e) ScanChip(stringResource(Res.string.dnsscan_filter_e2e), filter == "e2e") { onFilter("e2e") }
                if (hasSelection) ScanChip(stringResource(Res.string.dnsscan_filter_selected), filter == "selected") { onFilter("selected") }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ScanChip(stringResource(Res.string.dnsscan_sort_score), sort == "score") { onSort("score") }
                ScanChip(stringResource(Res.string.dnsscan_sort_speed), sort == "speed") { onSort("speed") }
                if (hasE2e) ScanChip(stringResource(Res.string.dnsscan_sort_e2e), sort == "e2e") { onSort("e2e") }
                ScanChip(stringResource(Res.string.dnsscan_sort_ip), sort == "ip") { onSort("ip") }
            }
        }
    }
}

@Composable
private fun SearchBox(query: String, onQuery: (String) -> Unit) {
    val glass = Glass.active
    val fill = if (glass) Glass.colors.fill2 else MaterialTheme.colorScheme.surfaceContainerHighest
    val text = if (glass) Glass.colors.label else MaterialTheme.colorScheme.onSurface
    val hint = if (glass) Glass.colors.label3 else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(20.dp)).background(fill).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(GlassIcons.Search, contentDescription = null, tint = hint, modifier = Modifier.size(16.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text(stringResource(Res.string.dnsscan_search), color = hint, fontSize = 14.sp)
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(color = text, fontSize = 14.sp),
                cursorBrush = SolidColor(text),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ScanChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val glass = Glass.active
    val fill = when {
        glass && selected -> Glass.colors.tint
        glass -> Glass.colors.fill2
        selected -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val text = when {
        glass && selected -> Color.White
        glass -> Glass.colors.label
        selected -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        label,
        color = text,
        fontSize = 14.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(fill)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

private fun count(v: Long): String = String.format(Locale.US, "%,d", v)

private fun duration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) String.format(Locale.US, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
    else String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}

@Composable
private fun ScanStatus(state: ScanState) {
    val glass = Glass.active
    val shape = RoundedCornerShape(if (glass) 26.dp else 28.dp)
    val label = if (glass) Glass.colors.label else MaterialTheme.colorScheme.onSurface
    val muted = if (glass) Glass.colors.label2 else MaterialTheme.colorScheme.onSurfaceVariant
    val accent = if (glass) Glass.colors.tint else MaterialTheme.colorScheme.primary
    val danger = if (glass) Glass.colors.red else MaterialTheme.colorScheme.error
    val surface = if (glass) Modifier.glassCell(Glass.colors, shape) else Modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
    val p = state.progress
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .then(surface)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val headline = when {
            state.running && p?.sweepDone == true -> stringResource(Res.string.dnsscan_testing)
            state.running -> stringResource(Res.string.dnsscan_running)
            state.restored -> stringResource(Res.string.dnsscan_last)
            state.reason == "stopped" -> stringResource(Res.string.dnsscan_stopped)
            state.error != null && p == null -> stringResource(Res.string.dnsscan_failed)
            p == null && state.e2eQueued > 0 -> stringResource(Res.string.dnsscan_e2e)
            else -> stringResource(Res.string.dnsscan_done, (p?.good ?: 0).toInt(), (p?.found ?: state.found).toInt())
        }
        Text(headline, color = label, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        if (p != null && !state.restored) {
            LinearProgressIndicator(
                progress = { if (p.sweepDone) 1f else p.fraction },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = accent,
                trackColor = accent.copy(alpha = 0.16f),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat(stringResource(Res.string.dnsscan_checked), ltrIsolate("${count(p.done + p.skipped)}/${count(p.planned + p.skipped)}"), label, muted)
                Stat(stringResource(Res.string.dnsscan_answering), count(maxOf(p.found, p.tcpFound)), label, muted)
                Stat(stringResource(Res.string.dnsscan_good), count(p.good), label, muted)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat(stringResource(Res.string.dnsscan_rate), if (p.sweepDone) "—" else count(p.rate.toLong()), label, muted)
                Stat(stringResource(Res.string.dnsscan_tested), ltrIsolate("${count(p.deepDone)}/${count(p.deepQueued)}"), label, muted)
                val ticking = state.running && !p.sweepDone
                Stat(
                    stringResource(if (ticking) Res.string.dnsscan_left else Res.string.dnsscan_took),
                    if (ticking) duration(p.etaMs) else duration(p.elapsedMs),
                    label,
                    muted,
                )
            }
            if (p.root) {
                Text(stringResource(Res.string.dnsscan_root_active, p.captured.toInt()), color = muted, fontSize = 12.sp)
            }
        }
        if (state.e2eQueued > 0) {
            Text(stringResource(Res.string.dnsscan_e2e_line, state.e2eDone, state.e2eQueued, state.e2ePassed), color = label, fontSize = 13.sp)
        }
        state.interceptedBy?.let {
            Text(stringResource(Res.string.dnsscan_intercept, ltrIsolate(it)), color = danger, fontSize = 13.sp)
        }
        state.error?.let { Text(it, color = danger, fontSize = 13.sp) }
        state.warnings.lastOrNull()?.let { Text(it, color = muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        if (state.running) Text(stringResource(Res.string.dnsscan_keep_open), color = muted, fontSize = 12.sp)
    }
}

@Composable
private fun Stat(title: String, value: String, label: Color, muted: Color) {
    Column(Modifier.width(96.dp)) {
        Text(value, color = label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(title, color = muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ApplyButton(onClick: () -> Unit) {
    val text = stringResource(Res.string.dnsscan_apply)
    if (Glass.active) {
        val fill = Glass.colors.green
        val shape = RoundedCornerShape(percent = 50)
        Row(
            Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .fillMaxWidth()
                .height(54.dp)
                .shadow(12.dp, shape, clip = false, ambientColor = fill.copy(alpha = 0.5f), spotColor = fill.copy(alpha = 0.5f))
                .clip(shape)
                .background(fill)
                .background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.24f), 0.52f to Color.Transparent))
                .glassHighlight(shape, Color.White.copy(alpha = 0.55f))
                .clickable(onClick = onClick),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(GlassIcons.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        }
        return
    }
    Button(
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(28.dp),
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

@Composable
private fun ScanButton(running: Boolean, busy: Boolean, onStart: () -> Unit, onStop: () -> Unit) {
    val text = stringResource(if (running) Res.string.dnsscan_stop else if (busy) Res.string.dnsscan_fetching else Res.string.dnsscan_start)
    if (Glass.active) {
        val c = Glass.colors
        val fill = if (running) c.red else c.tint
        val shape = RoundedCornerShape(percent = 50)
        Row(
            Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .fillMaxWidth()
                .height(54.dp)
                .shadow(12.dp, shape, clip = false, ambientColor = fill.copy(alpha = 0.5f), spotColor = fill.copy(alpha = 0.5f))
                .clip(shape)
                .background(fill)
                .background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.24f), 0.52f to Color.Transparent))
                .glassHighlight(shape, Color.White.copy(alpha = 0.55f))
                .clickable(onClick = if (running) onStop else onStart),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (running) GlassIcons.Stop else GlassIcons.Scope, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        }
        return
    }
    Button(
        onClick = if (running) onStop else onStart,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(28.dp),
        colors = if (running) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        } else {
            ButtonDefaults.buttonColors()
        },
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

private class ResultLabels(
    val loss: String,
    val tunnel: String,
    val noTunnel: String,
    val name: String,
    val truncates: String,
    val burst: String,
    val filtered: String,
    val answeredBy: String,
    val proxy: String,
    val injected: String,
    val tcpOnly: String,
    val e2eOk: String,
    val e2eFail: String,
) {
    companion object {
        @Composable
        fun current() = ResultLabels(
            loss = stringResource(Res.string.dnsscan_m_loss),
            tunnel = stringResource(Res.string.dnsscan_m_tunnel),
            noTunnel = stringResource(Res.string.dnsscan_m_no_tunnel),
            name = stringResource(Res.string.dnsscan_m_name),
            truncates = stringResource(Res.string.dnsscan_m_truncates),
            burst = stringResource(Res.string.dnsscan_m_burst),
            filtered = stringResource(Res.string.dnsscan_m_filtered),
            answeredBy = stringResource(Res.string.dnsscan_m_answered_by),
            proxy = stringResource(Res.string.dnsscan_m_proxy),
            injected = stringResource(Res.string.dnsscan_m_injected),
            tcpOnly = stringResource(Res.string.dnsscan_m_tcp_only),
            e2eOk = stringResource(Res.string.dnsscan_m_e2e_ok),
            e2eFail = stringResource(Res.string.dnsscan_m_e2e_fail),
        )
    }
}

private fun metrics(r: ResolverResult, l: ResultLabels): String = buildList {
    r.e2e?.let { e -> add(if (e.ok) "${l.e2eOk} ${ltrIsolate("${e.totalMs} ms")}" else l.e2eFail) }
    add(ltrIsolate("${r.rtt} ms"))
    if (r.loss > 0f) add("${l.loss} ${ltrIsolate("${(r.loss * 100).toInt()}%")}")
    when (r.tunnel) {
        true -> add(l.tunnel)
        false -> add(l.noTunnel)
        null -> {}
    }
    if (r.maxQname > 0) add("${l.name} ${r.maxQname}")
    if (r.truncates) add(l.truncates) else if (r.maxReply > 0) add(ltrIsolate("${r.maxReply} B"))
    r.burst?.takeIf { it < 0.995f }?.let { add("${l.burst} ${ltrIsolate("${(it * 100).toInt()}%")}") }
    if (r.via == "tcp") add(l.tcpOnly) else if (r.tcp == true) add("TCP")
    if (r.dot == true) add("DoT")
    if (r.proxySuspect == true) add(l.proxy)
    if (r.injected == true || r.dupAnswers > 0) add(l.injected)
    if (r.censored == true) add(l.filtered)
    r.hops?.let { add(ltrIsolate("$it hops")) }
    r.answeredBy?.let { add("${l.answeredBy} ${ltrIsolate(it)}") }
}.joinToString(" · ")

@Composable
private fun ScoreBadge(score: Int, selected: Boolean, e2e: Boolean?) {
    val dark = if (Glass.active) Glass.colors.dark else MaterialTheme.colorScheme.surface.luminanceDark()
    val color = when {
        e2e == true -> IosColors.blue(dark)
        score >= 80 -> IosColors.green(dark)
        score >= 50 -> IosColors.orange(dark)
        else -> IosColors.red(dark)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (selected) {
            Icon(GlassIcons.CheckCircle, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        }
        Text(
            score.toString(),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            modifier = Modifier.clip(CircleShape).background(color).padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

private fun Color.luminanceDark(): Boolean = (0.299f * red + 0.587f * green + 0.114f * blue) < 0.5f

@Composable
private fun UseInConfigDialog(profiles: List<VpnProfile>, onDismiss: () -> Unit, onPick: (VpnProfile) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.dnsscan_use)) },
        text = {
            if (profiles.isEmpty()) {
                Text(stringResource(Res.string.dnsscan_use_none))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    profiles.forEach { p ->
                        Text(
                            p.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onPick(p) }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { onDismiss() }) { Text(stringResource(Res.string.action_cancel)) } },
    )
}
