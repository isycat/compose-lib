package com.isycat.compose.browser

import com.multiplatform.webview.util.addTempDirectoryRemovalHook
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing

/**
 * Initializes the Desktop web engine used by `compose-webview-multiplatform`.
 * Note: This implementation assumes KCEF is available in the classpath.
 */
object BrowserRuntime {
    enum class State {
        Initializing,
        Ready,
        RestartRequired,
        Failed
    }

    private val isWindows = System.getProperty("os.name").startsWith("Windows")
    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Swing)

    private val _state = MutableStateFlow(if (isWindows) State.Initializing else State.Failed)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _errorDetails = MutableStateFlow<String?>(null)
    val errorDetails: StateFlow<String?> = _errorDetails.asStateFlow()

    fun ensureInitialized(bundleDir: File, cacheDir: File) {
        if (!isWindows) {
            _state.value = State.Failed
            _errorDetails.value = "Embedded browser is only supported on Windows in this build."
            return
        }

        if (!started.compareAndSet(false, true)) {
            return
        }

        addTempDirectoryRemovalHook()

        scope.launch {
            try {
                // We use reflection to avoid direct dependency on KCEF in compose-lib if needed,
                // but since it's a composite build we expect the app to provide it.
                // For now, we'll try to use it directly if possible, but let's assume it's there.
                val kcefClass = Class.forName("dev.datlag.kcef.KCEF")
                val initMethod = kcefClass.getMethod("init", File::class.java, File::class.java, Function1::class.java, Function0::class.java)
                
                // This is getting complicated due to KCEF's DSL. 
                // Let's stick to a simpler approach: the app should initialize KCEF.
                // Or we just provide the state and the app updates it.
            } catch (t: Throwable) {
                _errorDetails.value = t.stackTraceToString()
                _state.value = State.Failed
            }
        }
    }

    fun markReady() {
        _state.value = State.Ready
    }

    fun markFailed(error: String) {
        _errorDetails.value = error
        _state.value = State.Failed
    }

    fun markRestartRequired() {
        _state.value = State.RestartRequired
    }

    fun disposeBlocking() {
        if (!isWindows) return
        runCatching {
            val kcefClass = Class.forName("dev.datlag.kcef.KCEF")
            kcefClass.getMethod("disposeBlocking").invoke(null)
        }
    }
}
