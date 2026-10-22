@file:OptIn(ExperimentalWasmJsInterop::class)

package com.isycat.compose.web

import kotlinx.coroutines.await
import kotlin.js.Promise

/*
 * What an app running in the browser asks of it beyond Compose: files the user picks, downloads, fetching, the
 * clipboard and somewhere to keep settings.
 */

/** A file the user picked in the browser: its [name] and what's in it. */
class PickedFile(val name: String, val bytes: ByteArray)

/** The browser's file picker and downloads. */
object BrowserFiles {
    /**
     * Shows the browser's file picker for the types in [accept] (`.png,.jpg`, or empty for any) and reads what's chosen.
     * Empty when cancelled.
     */
    suspend fun pick(accept: String, multiple: Boolean): List<PickedFile> {
        val files = pickFiles(accept, multiple).await<JsAny?>() ?: return emptyList()
        return (0 until length(files)).map { i ->
            val file = item(files, i)
            PickedFile(fileName(file), bytesOf(readFile(file).await()))
        }
    }

    /** Hands [bytes] to the browser to save as [fileName]: a download, which the browser shows. */
    fun download(fileName: String, bytes: ByteArray, mimeType: String = mimeTypeOf(fileName)) {
        downloadBlob(fileName, int8ArrayOf(bytes), mimeType)
    }

    /** The media type a file called [fileName] is sent as. */
    fun mimeTypeOf(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        "json" -> "application/json"
        "txt", "yaml", "yml" -> "text/plain"
        else -> "application/octet-stream"
    }
}

/** Fetching from the web, as the page's own requests are. */
object BrowserHttp {
    /** What [url] answers, or fails with why. Relative addresses are the site's own. */
    suspend fun get(url: String): ByteArray = bytesOf(fetchBytes(url).await())
}

/** The browser's clipboard. */
object BrowserClipboard {
    /** Puts [text] on the clipboard, when the browser lets the page. */
    fun copy(text: String) = writeClipboard(text)
}

/** The browser's local storage: strings kept for this site between visits. Quietly does nothing where it's blocked. */
object BrowserStorage {
    operator fun get(key: String): String? = runCatching { storageGet(key) }.getOrNull()

    operator fun set(key: String, value: String?) {
        runCatching { if (value == null) storageRemove(key) else storageSet(key, value) }
    }
}

/** A JS Int8Array's bytes, copied into Kotlin. */
fun bytesOf(array: JsAny?): ByteArray {
    if (array == null) return ByteArray(0)
    val size = length(array)
    return ByteArray(size) { byteAt(array, it) }
}

/** [bytes] copied into a new JS Int8Array. */
fun int8ArrayOf(bytes: ByteArray): JsAny {
    val array = newInt8Array(bytes.size)
    bytes.forEachIndexed { i, b -> setByte(array, i, b) }
    return array
}

private fun pickFiles(accept: String, multiple: Boolean): Promise<JsAny?> = js(
    """new Promise((resolve) => {
        const input = document.createElement('input');
        input.type = 'file';
        if (accept) input.accept = accept;
        input.multiple = multiple;
        input.style.display = 'none';
        document.body.appendChild(input);
        const done = (files) => { input.remove(); resolve(files); };
        input.addEventListener('change', () => done(input.files ? Array.from(input.files) : []));
        input.addEventListener('cancel', () => done([]));
        input.click();
    })""",
)

private fun readFile(file: JsAny): Promise<JsAny?> = js("file.arrayBuffer().then((b) => new Int8Array(b))")

private fun fetchBytes(url: String): Promise<JsAny?> = js(
    """fetch(url).then((r) => {
        if (!r.ok) throw new Error('The web address answered ' + r.status);
        return r.arrayBuffer();
    }).then((b) => new Int8Array(b))""",
)

private fun downloadBlob(name: String, bytes: JsAny, mime: String): Unit = js(
    """{
        const url = URL.createObjectURL(new Blob([bytes], { type: mime }));
        const a = document.createElement('a');
        a.href = url;
        a.download = name;
        a.style.display = 'none';
        document.body.appendChild(a);
        a.click();
        a.remove();
        setTimeout(() => URL.revokeObjectURL(url), 60000);
    }""",
)

private fun writeClipboard(text: String): Unit = js("{ if (navigator.clipboard) navigator.clipboard.writeText(text).catch(() => {}); }")

private fun storageGet(key: String): String? = js("window.localStorage.getItem(key)")

private fun storageSet(key: String, value: String): Unit = js("window.localStorage.setItem(key, value)")

private fun storageRemove(key: String): Unit = js("window.localStorage.removeItem(key)")

private fun length(array: JsAny): Int = js("array.length")

private fun item(array: JsAny, i: Int): JsAny = js("array[i]")

private fun fileName(file: JsAny): String = js("file.name")

private fun byteAt(array: JsAny, i: Int): Byte = js("array[i]")

private fun newInt8Array(size: Int): JsAny = js("new Int8Array(size)")

private fun setByte(array: JsAny, i: Int, b: Byte): Unit = js("{ array[i] = b; }")
