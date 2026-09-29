package dev.cluvex.zedsecure.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cluvex.zedsecure.R
import dev.cluvex.zedsecure.core.SsoBus

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OpenConnectSsoDialog() {
    val uri by SsoBus.pending.collectAsStateWithLifecycle()
    val loginUri = uri ?: return

    AlertDialog(
        onDismissRequest = { SsoBus.cancel() },
        title = { Text(stringResource(R.string.openconnect_sso_title)) },
        text = {
            Box(Modifier.fillMaxWidth().height(460.dp)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            webViewClient = object : WebViewClient() {
                                override fun doUpdateVisitedHistory(v: WebView?, url: String?, reload: Boolean) {
                                    url?.let { report(it) }
                                }

                                override fun onPageFinished(v: WebView?, url: String?) {
                                    url?.let { report(it) }
                                }

                                override fun shouldOverrideUrlLoading(
                                    v: WebView?,
                                    req: WebResourceRequest?,
                                ): Boolean {
                                    req?.url?.toString()?.let { report(it) }
                                    return false
                                }
                            }
                            loadUrl(loginUri)
                        }
                    },
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { SsoBus.cancel() }) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        modifier = Modifier.padding(8.dp),
        titleContentColor = MaterialTheme.colorScheme.onSurface,
    )
}

private fun report(url: String) {
    val cookie = CookieManager.getInstance().getCookie(url).orEmpty()

    val cookies = cookie.split(';')
        .mapNotNull { part ->
            val kv = part.trim()
            val eq = kv.indexOf('=')
            if (eq <= 0) null else kv.substring(0, eq) to kv.substring(eq + 1)
        }
        .flatMap { listOf(it.first, it.second) }
        .toTypedArray()
    SsoBus.report(url, cookies, emptyArray())
}
