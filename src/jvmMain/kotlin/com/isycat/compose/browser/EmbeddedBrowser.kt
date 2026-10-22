package com.isycat.compose.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.multiplatform.webview.web.LoadingState
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.multiplatform.webview.web.rememberWebViewState

@Composable
fun EmbeddedBrowser(
    url: String,
    modifier: Modifier = Modifier.background(MaterialTheme.colorScheme.background),
    injectionNamespace: String = "compose",
    injectionRules: List<WebViewInjectionRule> = emptyList(),
    onUrlChanged: (String) -> Unit = {}
) {
    val browserState by BrowserRuntime.state.collectAsState()
    val errorDetails by BrowserRuntime.errorDetails.collectAsState()

    when (browserState) {
        BrowserRuntime.State.Ready -> {
            BrowserContent(
                url = url,
                modifier = modifier,
                injectionNamespace = injectionNamespace,
                injectionRules = injectionRules,
                onUrlChanged = onUrlChanged
            )
        }

        BrowserRuntime.State.Initializing -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Initializing Browser...", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        BrowserRuntime.State.RestartRequired -> {
            BrowserErrorPanel(
                title = "Restart Required",
                url = url,
                details = errorDetails ?: "The browser engine was updated and requires an application restart.",
                icon = { Icon(Icons.Default.Refresh, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary) },
                modifier = modifier
            )
        }

        BrowserRuntime.State.Failed -> {
            BrowserErrorPanel(
                title = "Browser Initialization Failed",
                url = url,
                details = errorDetails,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun BrowserContent(
    url: String,
    modifier: Modifier,
    injectionNamespace: String,
    injectionRules: List<WebViewInjectionRule>,
    onUrlChanged: (String) -> Unit
) {
    val state = rememberWebViewState(url)
    val navigator = rememberWebViewNavigator()

    LaunchedEffect(state.lastLoadedUrl) {
        state.lastLoadedUrl?.let { onUrlChanged(it) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        val loadingState = state.loadingState
        if (loadingState is LoadingState.Loading) {
            LinearProgressIndicator(
                progress = { loadingState.progress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent
            )
        } else {
            Spacer(modifier = Modifier.height(2.dp))
        }

        Box(modifier = Modifier.weight(1f)) {
            WebView(
                state = state,
                modifier = Modifier.fillMaxSize(),
                navigator = navigator,
            )

            // Auto-injection logic
            val currentUrl = state.lastLoadedUrl
            if (currentUrl != null && loadingState is LoadingState.Finished) {
                LaunchedEffect(currentUrl, injectionRules) {
                    injectionRules.filter { it.urlMatches(currentUrl) }.forEach { rule ->
                        rule.css.forEach { asset ->
                            asset.loadTextOrNull()?.let { css ->
                                val script = WebViewInjectionScripts.buildInjectCssScript(injectionNamespace, rule.id, asset.id, css)
                                navigator.evaluateJavaScript(script)
                            }
                        }
                        rule.js.forEach { asset ->
                            asset.loadTextOrNull()?.let { js ->
                                val script = WebViewInjectionScripts.buildRunJsOnceScript(injectionNamespace, rule.id, asset.id, js)
                                navigator.evaluateJavaScript(script)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BrowserErrorPanel(
    title: String,
    url: String,
    details: String?,
    icon: @Composable () -> Unit = { Icon(Icons.Default.Error, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error) },
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            icon()
            Spacer(modifier = Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(8.dp))
            Text(url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            
            if (details != null) {
                Spacer(modifier = Modifier.height(24.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        details,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp
                        ),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}
