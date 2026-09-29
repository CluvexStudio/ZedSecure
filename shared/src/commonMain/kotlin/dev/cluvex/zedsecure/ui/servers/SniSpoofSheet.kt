@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.cluvex.zedsecure.ui.servers

import org.jetbrains.compose.resources.StringResource

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import dev.cluvex.zedsecure.domain.config.SniSpoofProfile
import dev.cluvex.zedsecure.domain.config.VpnProfile

@Composable
fun SniSpoofSheet(
    onDismiss: () -> Unit,
    onSave: (name: String, SniSpoofProfile) -> Unit,
    initial: SniSpoofProfile? = null,
    initialName: String = "",

    candidates: List<VpnProfile> = emptyList(),
) {
    var name by remember { mutableStateOf(initialName) }
    var link by remember { mutableStateOf(initial?.link ?: "") }
    var fakeSni by remember { mutableStateOf(initial?.fakeSni ?: "") }
    var cleanIp by remember { mutableStateOf(initial?.cleanIp ?: "") }

    var pickerOpen by remember { mutableStateOf(false) }
    var pickedName by remember {
        mutableStateOf(candidates.firstOrNull { it.rawPayload() == initial?.link }?.name ?: "")
    }
    var manual by remember { mutableStateOf(candidates.isEmpty() || (initial != null && pickedName.isEmpty())) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(Res.string.snispoof_add_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(Res.string.snispoof_root_warn),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(14.dp),
                )
            }

            F(name, Res.string.manual_remark) { name = it }

            if (candidates.isNotEmpty()) {
                ExposedDropdownMenuBox(
                    expanded = pickerOpen,
                    onExpandedChange = { pickerOpen = !pickerOpen },
                ) {
                    OutlinedTextField(
                        value = pickedName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(Res.string.snispoof_pick_server)) },
                        placeholder = { Text(stringResource(Res.string.snispoof_pick_hint)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(pickerOpen) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(
                            androidx.compose.material3.MenuAnchorType.PrimaryNotEditable,
                        ),
                    )
                    DropdownMenu(expanded = pickerOpen, onDismissRequest = { pickerOpen = false }) {
                        candidates.forEach { server ->
                            DropdownMenuItem(
                                text = { Text("${server.name} · ${server.address}:${server.port}") },
                                onClick = {
                                    pickerOpen = false
                                    manual = false
                                    pickedName = server.name
                                    link = server.rawPayload().orEmpty()
                                    if (name.isBlank()) name = server.name

                                    if (cleanIp.isBlank() && server.address.count { c -> c == '.' } == 3) {
                                        cleanIp = server.address
                                    }
                                },
                            )
                        }
                    }
                }
                Row {
                    AssistChip(
                        onClick = { manual = !manual },
                        label = {
                            Text(
                                stringResource(
                                    if (manual) Res.string.snispoof_use_picker
                                    else Res.string.snispoof_paste_instead,
                                ),
                            )
                        },
                    )
                }
            }

            if (manual) {
                F(link, Res.string.snispoof_link, minLines = 3, placeholder = "vless://… / trojan://… / { \"outbounds\": … }") { link = it }
            }
            F(fakeSni, Res.string.snispoof_fake_sni, placeholder = "www.speedtest.net") { fakeSni = it }
            F(cleanIp, Res.string.snispoof_clean_ip, placeholder = "104.16.0.0 (optional)") { cleanIp = it }

            Button(

                enabled = link.isNotBlank() && fakeSni.isNotBlank(),
                onClick = {
                    onSave(
                        name.trim(),
                        SniSpoofProfile(link = link.trim(), fakeSni = fakeSni.trim(), cleanIp = cleanIp.trim()),
                    )
                },
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) { Text(stringResource(Res.string.action_save), fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun F(value: String, labelRes: StringResource, placeholder: String? = null, minLines: Int = 1, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(labelRes)) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = minLines == 1,
        minLines = minLines,
        modifier = Modifier.fillMaxWidth(),
    )
}
