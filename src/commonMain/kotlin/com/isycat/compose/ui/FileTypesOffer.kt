package com.isycat.compose.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** How the system makes the running app the opener of its files on a double-click, and tells whether it is already. */
interface FileOpeners {
    /** Whether the running app opens all of [extensions] on a double-click already. */
    fun opensThem(extensions: List<String>): Boolean

    /** Makes the running app open [extensions] on a double-click. Throws when the system refuses. */
    fun takeThem(extensions: List<String>)
}

/**
 * An app's offer, as it starts, to open its files when they are double-clicked, as moonshine and dittoCAD make it: made
 * only where the system can be asked ([openers]; null where it can't, as when run from Gradle), the user has not turned
 * it off ([offering], kept by [keepOffering] and shown in Settings) and the app does not open them already. The window
 * shows it in a bar under its title bar ([FileTypesBar]). The system is asked on [away], as it reads and writes the
 * system's settings.
 *
 * [extensions] are read when [look] runs, so an app whose plugins add file types looks again once they have loaded;
 * once answered, it asks no more until the next start.
 */
class FileTypesOffer(
    /** The app's name as the bar says it: "Make cardSTUDIO open ...". */
    val appName: String,
    private val extensions: () -> List<String>,
    private val openers: FileOpeners?,
    offering: Boolean = true,
    private val keepOffering: (Boolean) -> Unit = {},
    private val away: CoroutineDispatcher = Dispatchers.Default,
    /** Extensions taken with the rest but not named in the question, as older names of the same files. */
    private val unnamed: Set<String> = emptySet(),
) {
    /** The extensions asked about, without dots, each once: as [look] last read them. */
    var asked: List<String> by mutableStateOf(emptyList())
        private set

    /**
     * What the bar asks, naming each extension but the [unnamed] ones: "Make cardSTUDIO open .a, .b and .c files when
     * you double-click them?".
     */
    val question: String
        get() = "Make $appName open ${sentence(asked.filterNot { it in unnamed }.ifEmpty { asked })} files when you double-click them?"

    /** The bar's yes. */
    val answer: String get() = "Open them with $appName"

    /** Whether the offer is made at all, as Settings shows it; on at first. */
    var offering: Boolean by mutableStateOf(offering)
        private set

    /** Whether the system can be asked, so Settings shows the choice. */
    val possible: Boolean get() = openers != null

    /** Whether the offer stands, for the bar to show. */
    var asking: Boolean by mutableStateOf(false)
        private set

    /** Whether "Don't ask again" is ticked: either answer then turns the offer off for good. */
    var dontAskAgain: Boolean by mutableStateOf(false)

    /** Whether the files are being taken now, the bar's buttons waiting meanwhile. */
    var working: Boolean by mutableStateOf(false)
        private set

    /** Why taking them failed, which the bar shows in place of the question; null while it has not. */
    var failure: String? by mutableStateOf(null)
        private set

    /** Has the offer made, or not, from now on: turned back on in Settings, the next [look] asks again. */
    fun offering(offer: Boolean) {
        offering = offer
        keepOffering(offer)
        if (offer) {
            answered = false
            dontAskAgain = false
        } else {
            asking = false
            failure = null
        }
    }

    // Answered this start: Not now waits for the next start, and yes has taken them.
    private var answered = false

    /** Looks whether to make the offer, and makes it. */
    suspend fun look() {
        val openers = openers ?: return
        if (!offering || answered) return
        val wanted = extensions().map { it.removePrefix(".").lowercase() }.distinct()
        if (wanted.isEmpty()) return
        val opens = withContext(away) { runCatching { openers.opensThem(wanted) }.getOrDefault(false) }
        asked = wanted
        if (!opens && offering) asking = true
    }

    /** Takes the files; or [failure] says why not. */
    suspend fun accept() {
        val openers = openers ?: return
        if (working) return
        working = true
        // The system can refuse for its own reasons, as a registry an administrator locked: they are said, never thrown.
        val wanted = asked
        val taken = withContext(away) { runCatching { openers.takeThem(wanted) } }
        working = false
        taken.onSuccess {
            answered = true
            if (dontAskAgain) offering(false)
            asking = false
            failure = null
        }.onFailure { failed ->
            failure = "Could not make $appName open its files: ${failed.message ?: failed::class.simpleName}"
        }
    }

    /** Puts the offer aside: until the next start, or for good where "Don't ask again" is ticked. */
    fun decline() {
        answered = true
        if (dontAskAgain) offering(false)
        asking = false
        failure = null
    }

    companion object {
        /** [extensions] as a sentence names them: ".a", ".a and .b", ".a, .b and .c". */
        fun sentence(extensions: List<String>): String {
            val names = extensions.map { ".$it" }
            return if (names.size <= 1) names.joinToString() else names.dropLast(1).joinToString(", ") + " and " + names.last()
        }
    }
}
