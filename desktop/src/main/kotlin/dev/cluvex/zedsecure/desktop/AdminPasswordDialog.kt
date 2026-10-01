package dev.cluvex.zedsecure.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.action_cancel
import dev.cluvex.zedsecure.shared.resources.action_ok
import dev.cluvex.zedsecure.shared.resources.admin_password_body
import dev.cluvex.zedsecure.shared.resources.admin_password_label
import dev.cluvex.zedsecure.shared.resources.admin_password_title
import dev.cluvex.zedsecure.shared.resources.admin_password_wrong
import org.jetbrains.compose.resources.stringResource

@Composable
fun AdminPasswordDialog(retry: Boolean, onDone: (CharArray?) -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    fun submit() {
        if (text.isEmpty()) return
        val password = text.toCharArray()
        text = ""
        onDone(password)
    }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text(stringResource(Res.string.admin_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.admin_password_body), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text(stringResource(Res.string.admin_password_label)) },
                    isError = retry,
                    supportingText = if (retry) {
                        { Text(stringResource(Res.string.admin_password_wrong)) }
                    } else {
                        null
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { submit() }, enabled = text.isNotEmpty()) { Text(stringResource(Res.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = { onDone(null) }) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}
