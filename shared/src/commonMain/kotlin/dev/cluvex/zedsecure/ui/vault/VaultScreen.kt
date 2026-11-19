@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package dev.cluvex.zedsecure.ui.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialShapes
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.RoundedPolygon
import dev.cluvex.zedsecure.ui.bento.Bento
import dev.cluvex.zedsecure.ui.bento.BentoCard
import dev.cluvex.zedsecure.ui.bento.BentoTone
import dev.cluvex.zedsecure.ui.bento.BentoToneKind
import dev.cluvex.zedsecure.ui.bento.BentoType
import dev.cluvex.zedsecure.ui.glass.Glass
import dev.cluvex.zedsecure.ui.glass.GlassIcons
import dev.cluvex.zedsecure.ui.glass.GlassShapes
import dev.cluvex.zedsecure.ui.glass.GlassType
import dev.cluvex.zedsecure.ui.glass.glassCell
import dev.cluvex.zedsecure.ui.glass.glassChrome
import dev.cluvex.zedsecure.ui.glass.glassHighlight
import dev.cluvex.zedsecure.ui.glass.GlassArtBadge
import dev.cluvex.zedsecure.ui.settings.SettingsArts
import dev.cluvex.zedsecure.ui.settings.SettingsGroup
import dev.cluvex.zedsecure.ui.settings.SettingsInfoRow
import dev.cluvex.zedsecure.ui.settings.SettingsPageScaffold
import dev.cluvex.zedsecure.ui.settings.SettingsRow
import org.jetbrains.compose.resources.DrawableResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.*
import dev.cluvex.zedsecure.core.VaultImportBus
import dev.cluvex.zedsecure.ui.platform.LocalPlatform
import dev.cluvex.zedsecure.crypto.ZsxSealRequest
import dev.cluvex.zedsecure.data.config.ConfigRepository
import dev.cluvex.zedsecure.domain.config.VpnProfile
import dev.cluvex.zedsecure.ui.components.MorphingBlob
import dev.cluvex.zedsecure.ui.theme.ZedGradients
import dev.cluvex.zedsecure.ui.platform.appVerticalScroll
import dev.cluvex.zedsecure.ui.platform.mouseDragScrollVertical
import androidx.compose.foundation.lazy.rememberLazyListState
import dev.cluvex.zedsecure.ui.platform.rememberAppSheetState

private const val NOTE_MAX_LEN = 500

@Composable
fun VaultScreen(
    repository: ConfigRepository,
    contentPadding: PaddingValues,
    onImportRequested: () -> Unit,
    modifier: Modifier = Modifier,
    onActiveChanged: () -> Unit = {},
) {
    val platform = LocalPlatform.current
    val needConfigMsg = stringResource(Res.string.vault_need_config_first)
    val profiles by repository.profiles.collectAsStateWithLifecycle()
    val activeId by repository.activeId.collectAsStateWithLifecycle()
    val locked = profiles.filter { it.isLocked }

    val sources = remember(profiles) {
        profiles.filter { p ->
            !p.isLocked && !p.isProxyChain && !p.isCrossChain && !p.isAutoSelect && p.rawPayload() != null
        }
    }
    var showCreate by remember { mutableStateOf(false) }
    var sealing by remember { mutableStateOf(false) }
    val sealScope = rememberCoroutineScope()
    val activeLabel = stringResource(Res.string.config_active)
    val lockedLabel = stringResource(Res.string.locked_badge)

    SettingsPageScaffold(
        title = stringResource(Res.string.vault_title),
        subtitle = stringResource(Res.string.vault_intro_body),
        art = SettingsArts.Vault,
        contentPadding = contentPadding,
        modifier = modifier,
    ) {
        if (Glass.active) PageNote(stringResource(Res.string.vault_intro_body))
        VaultActions(
            onCreate = {
                if (sources.isEmpty()) platform.toast(needConfigMsg) else showCreate = true
            },
            onImport = onImportRequested,
        )
        if (locked.isEmpty()) {
            VaultEmpty()
        } else {
            SettingsGroup(stringResource(Res.string.vault_list_title, locked.size)) {
                locked.forEach { profile ->
                    val active = profile.id == activeId
                    val selectedMsg = stringResource(Res.string.vault_selected, profile.name)
                    SettingsRow(
                        title = profile.name,
                        description = listOfNotNull(
                            if (active) activeLabel else lockedLabel,
                            profile.note?.trim()?.takeIf { it.isNotEmpty() },
                        ).joinToString(" · "),
                        art = SettingsArts.Vault,
                        trailing = { VaultRowTrailing(active) { repository.remove(profile.id) } },
                        onClick = {
                            val changed = profile.id != activeId
                            repository.setActive(profile.id)
                            if (changed) onActiveChanged()
                            platform.toast(selectedMsg)
                        },
                    )
                }
            }
        }
        SettingsGroup {
            SettingsInfoRow(
                stringResource(Res.string.vault_experimental_badge),
                stringResource(Res.string.vault_experimental_note),
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showCreate) {
        CreateLockedSheet(
            sources = sources,
            sealing = sealing,
            onDismiss = { showCreate = false },
            onCreate = { chosen, form, combine ->
                sealing = true
                sealScope.launch {
                    val files = runCatching {
                        withContext(Dispatchers.Default) {
                            repository.lockedRequests(chosen, form, combine)
                                .map { (request, fileName) -> fileName to repository.createLockedZsx(request) }
                        }
                    }.getOrNull()
                    sealing = false
                    if (files != null) {
                        platform.shareFiles(files)
                        showCreate = false
                    }
                }
            },
        )
    }
}

@Composable
private fun VaultActions(onCreate: () -> Unit, onImport: () -> Unit) {
    val create = stringResource(Res.string.vault_create)
    val import = stringResource(Res.string.vault_import)
    when {
        Glass.active -> Column(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GlassWideButton(create, GlassIcons.Lock, primary = true, onClick = onCreate)
            GlassWideButton(import, GlassIcons.Doc, primary = false, onClick = onImport)
        }
        Bento.active -> Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BentoActionTile(create, Res.drawable.ic_lock, Bento.colors.limeTone, MaterialShapes.Cookie6Sided, Modifier.weight(1f), onCreate)
            BentoActionTile(import, Res.drawable.ic_download, Bento.colors.tone(BentoToneKind.Violet), MaterialShapes.Clover4Leaf, Modifier.weight(1f), onImport)
        }
        else -> Column(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onCreate,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Icon(painterResource(Res.drawable.ic_lock), null, Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(create, fontWeight = FontWeight.SemiBold)
            }
            FilledTonalButton(
                onClick = onImport,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Icon(painterResource(Res.drawable.ic_download), null, Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(import, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun GlassWideButton(text: String, icon: ImageVector, primary: Boolean, onClick: () -> Unit) {
    val c = Glass.colors
    val shape = RoundedCornerShape(percent = 50)
    val surface = if (primary) {
        Modifier
            .shadow(12.dp, shape, clip = false, ambientColor = c.tint.copy(alpha = 0.5f), spotColor = c.tint.copy(alpha = 0.5f))
            .clip(shape)
            .background(c.tint)
            .background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.24f), 0.52f to Color.Transparent))
            .glassHighlight(shape, Color.White.copy(alpha = 0.55f))
    } else {
        Modifier.glassChrome(c, GlassShapes.capsule, elevated = false)
    }
    val content = if (primary) Color.White else c.tint
    Row(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .then(surface)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Text(text, color = content, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
    }
}

@Composable
private fun BentoActionTile(
    text: String,
    icon: DrawableResource,
    tone: BentoTone,
    motif: RoundedPolygon,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    BentoCard(
        tone = tone,
        modifier = modifier.heightIn(min = 112.dp),
        onClick = onClick,
        motif = motif,
        motifSize = 128.dp,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tone.content, modifier = Modifier.size(26.dp))
        Text(text, style = BentoType.rowTitle(), color = tone.content, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun VaultRowTrailing(active: Boolean, onDelete: () -> Unit) {
    val accent = if (Glass.active) Glass.colors.tint else MaterialTheme.colorScheme.primary
    val danger = if (Glass.active) Glass.colors.red else MaterialTheme.colorScheme.error
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (active) {
            if (Glass.active) {
                Icon(GlassIcons.CheckCircle, contentDescription = stringResource(Res.string.config_active), tint = accent, modifier = Modifier.size(20.dp))
            } else {
                Icon(painterResource(Res.drawable.ic_check_circle), contentDescription = stringResource(Res.string.config_active), tint = accent, modifier = Modifier.size(20.dp))
            }
        }
        IconButton(onClick = onDelete) {
            if (Glass.active) {
                Icon(GlassIcons.Trash, contentDescription = stringResource(Res.string.action_delete), tint = danger, modifier = Modifier.size(20.dp))
            } else {
                Icon(painterResource(Res.drawable.ic_delete), contentDescription = stringResource(Res.string.action_delete), tint = danger.copy(alpha = 0.85f))
            }
        }
    }
}

@Composable
private fun VaultEmpty() {
    val title = stringResource(Res.string.vault_empty_title)
    val body = stringResource(Res.string.vault_empty_body)
    when {
        Glass.active -> {
            val c = Glass.colors
            Column(
                Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .glassCell(c, RoundedCornerShape(26.dp))
                    .padding(horizontal = 22.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlassArtBadge(SettingsArts.Vault, size = 64.dp)
                Spacer(Modifier.height(4.dp))
                Text(title, style = GlassType.title2(), color = c.label, textAlign = TextAlign.Center)
                Text(body, style = GlassType.footnote(), color = c.label2, textAlign = TextAlign.Center)
            }
        }
        Bento.active -> {
            val tone = Bento.colors.tone(BentoToneKind.Violet)
            BentoCard(
                tone = tone,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
                motif = MaterialShapes.Cookie6Sided,
                motifSize = 210.dp,
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(painterResource(Res.drawable.ic_encrypted), contentDescription = null, tint = tone.content, modifier = Modifier.size(40.dp))
                Text(title, style = BentoType.title(22.sp), color = tone.content)
                Text(body, style = BentoType.body(), color = tone.content.copy(alpha = 0.8f))
            }
        }
        else -> Column(
            Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MorphingBlob(progress = 0.5f, brush = ZedGradients.connected, modifier = Modifier.size(104.dp)) {
                Icon(painterResource(Res.drawable.ic_encrypted), null, tint = Color.White, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PageNote(text: String) {
    Text(
        text,
        style = GlassType.footnote(),
        color = Glass.colors.label2,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 4.dp),
    )
}

@Composable
private fun CreateLockedSheet(
    sources: List<VpnProfile>,
    sealing: Boolean,
    onDismiss: () -> Unit,
    onCreate: (List<VpnProfile>, ZsxSealRequest, Boolean) -> Unit,
) {
    var selected by remember { mutableStateOf(sources.take(1).map { it.id }.toSet()) }
    var combine by remember { mutableStateOf(false) }
    var displayName by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var expiryDays by remember { mutableStateOf("0") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberAppSheetState()) {
        Column(
            Modifier
                .fillMaxWidth()
                .appVerticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(Res.string.vault_create_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(Res.string.vault_pick_servers),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            Row {
                TextButton(onClick = { selected = sources.mapTo(LinkedHashSet()) { it.id } }) {
                    Text(stringResource(Res.string.select_all))
                }
                TextButton(onClick = { selected = emptySet() }, enabled = selected.isNotEmpty()) {
                    Text(stringResource(Res.string.select_none))
                }
            }
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).mouseDragScrollVertical(listState),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              items(sources, key = { it.id }) { profile ->
                val checked = profile.id in selected
                Surface(
                    onClick = { selected = if (checked) selected - profile.id else selected + profile.id },
                    shape = RoundedCornerShape(16.dp),
                    color = if (checked) MaterialTheme.colorScheme.primaryContainer
                    else dev.cluvex.zedsecure.ui.glass.glassSurface(MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                profile.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                profile.transportLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
              }
            }
            if (selected.size > 1) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = combine, onCheckedChange = { combine = it })
                    Text(stringResource(Res.string.vault_combine_one), style = MaterialTheme.typography.bodyMedium)
                }
            }

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text(stringResource(Res.string.vault_form_name)) },
                supportingText = {
                    if (selected.size > 1 && !combine) Text(stringResource(Res.string.vault_name_numbered))
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = note,
                onValueChange = { if (it.length <= NOTE_MAX_LEN) note = it },
                label = { Text(stringResource(Res.string.vault_form_note)) },
                supportingText = { Text("${note.length} / $NOTE_MAX_LEN") },
                minLines = 2,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(Res.string.vault_form_password_optional)) },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            painterResource(
                                if (passwordVisible) Res.drawable.ic_visibility_off
                                else Res.drawable.ic_key,
                            ),
                            contentDescription = stringResource(Res.string.vault_toggle_password),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = expiryDays,
                onValueChange = { expiryDays = it.filter(Char::isDigit).take(4) },
                label = { Text(stringResource(Res.string.vault_form_expiry_days)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    val chosen = sources.filter { it.id in selected }
                    val days = expiryDays.toIntOrNull() ?: 0
                    val expiresAt = if (days > 0) {
                        System.currentTimeMillis() + days * 86_400_000L
                    } else {
                        null
                    }
                    val form = ZsxSealRequest(
                        configPayload = "",
                        nameEn = displayName.trim(),
                        nameFa = displayName.trim(),
                        note = note,
                        expiresAt = expiresAt,
                        password = password.ifBlank { null },
                    )
                    onCreate(chosen, form, combine && chosen.size > 1)
                },

                enabled = !sealing && selected.isNotEmpty(),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                if (sealing) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                } else {
                    Text(
                        stringResource(Res.string.vault_create_and_share),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
