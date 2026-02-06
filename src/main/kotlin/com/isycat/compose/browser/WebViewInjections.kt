package com.isycat.compose.browser

import java.io.File
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Describes CSS/JS assets to inject into a web page rendered by the embedded Desktop WebView.
 */
data class WebViewInjectionRule(
    val id: String,
    val urlMatches: (String) -> Boolean,
    val css: List<WebViewTextAsset> = emptyList(),
    val js: List<WebViewTextAsset> = emptyList(),
)

sealed class WebViewTextAsset {
    abstract val id: String
    abstract fun loadTextOrNull(): String?

    data class Resource(
        override val id: String,
        val resourcePath: String,
        private val classLoader: ClassLoader = Thread.currentThread().contextClassLoader
    ) : WebViewTextAsset() {
        override fun loadTextOrNull(): String? {
            return runCatching {
                classLoader.getResourceAsStream(resourcePath)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
        }
    }

    data class LocalFile(
        override val id: String,
        val file: File
    ) : WebViewTextAsset() {
        override fun loadTextOrNull(): String? {
            return runCatching { file.takeIf { it.isFile }?.readText() }.getOrNull()
        }
    }
}

object WebViewInjectionScripts {
    @OptIn(ExperimentalEncodingApi::class)
    fun buildInjectCssScript(namespace: String, ruleId: String, assetId: String, cssText: String): String {
        val encoded = Base64.encode(cssText.encodeToByteArray())
        val styleId = cssStyleTagId(namespace, ruleId, assetId)
        return """
            (function() {
              try {
                var existing = document.getElementById(${jsString(styleId)});
                if (existing) return 'already';
                var css = atob(${jsString(encoded)});
                var style = document.createElement('style');
                style.type = 'text/css';
                style.id = ${jsString(styleId)};
                style.appendChild(document.createTextNode(css));
                (document.head || document.documentElement).appendChild(style);
                return 'injected';
              } catch (e) {
                return 'error';
              }
            })();
        """.trimIndent()
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun buildRunJsOnceScript(namespace: String, ruleId: String, assetId: String, jsText: String): String {
        val encoded = Base64.encode(jsText.encodeToByteArray())
        val guardKey = jsGuardKey(ruleId, assetId)
        return """
            (function() {
              try {
                window.__${namespace} = window.__${namespace} || {};
                window.__${namespace}.injected = window.__${namespace}.injected || {};
                if (window.__${namespace}.injected[${jsString(guardKey)}]) return 'already';
                window.__${namespace}.injected[${jsString(guardKey)}] = true;
                var js = atob(${jsString(encoded)});
                (0, eval)(js);
                return 'ran';
              } catch (e) {
                return 'error';
              }
            })();
        """.trimIndent()
    }

    private fun jsString(value: String): String {
        return "\"" + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t") + "\""
    }

    private fun cssStyleTagId(namespace: String, ruleId: String, assetId: String): String = "${namespace}-css-$ruleId-$assetId"
    private fun jsGuardKey(ruleId: String, assetId: String): String = "js_${ruleId}_${assetId}"
}
