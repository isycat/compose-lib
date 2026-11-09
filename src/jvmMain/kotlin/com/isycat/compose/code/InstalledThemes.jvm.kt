package com.isycat.compose.code

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText

/**
 * The themes the person's IDEs have on this machine, as moonshine's IDE finds them: every scheme their JetBrains IDEs
 * keep (the newest IDE's first, each name once), Darcula aside, their VS Code as its settings.json makes it, and every
 * colour theme their VS Code has installed. Read from disk, off the interface's thread.
 */
internal actual suspend fun installedChoices(): List<CodeThemes.Choice> = withContext(Dispatchers.IO) {
    val out = ArrayList<CodeThemes.Choice>()
    val seen = HashSet<String>()
    for (scheme in runCatching { jetBrainsSchemes() }.getOrDefault(emptyList()).sortedByDescending { it.current }) {
        if (!seen.add(scheme.name)) continue
        out += CodeThemes.Choice("jetbrains:${scheme.file}", scheme.name, "${scheme.product}${if (scheme.current) ", in use" else ""}") {
            withContext(Dispatchers.IO) { JetBrainsSchemes.load(scheme.file.readText(), scheme.file.name, scheme.font) }
        }
    }
    vsCodeSettings()?.let { settings ->
        out += CodeThemes.Choice("vscode-settings:$settings", SETTINGS_LABEL, "settings.json over ${VsCodeThemes.DEFAULT_THEME}") {
            withContext(Dispatchers.IO) { VsCodeThemes.fromSettings(settings.readText(), SETTINGS_LABEL, "VS Code settings $settings", ::installedVsCodeTheme) }
        }
    }
    for (name in runCatching { vsCodeThemeNames() }.getOrDefault(emptyList())) {
        out += CodeThemes.Choice("vscode:$name", name, "VS Code") {
            VsCodeThemes.theme(installedVsCodeTheme(name) ?: error("VS Code no longer has $name"))
        }
    }
    out
}

internal actual suspend fun installedVsCodeTheme(name: String): VsCodeTheme? = withContext(Dispatchers.IO) {
    for (folder in vsCodeThemeFolders()) {
        val manifest = folder.resolve("package.json").takeIf { it.exists() }?.let { runCatching { readJson(it.readText()) as? Map<*, *> }.getOrNull() } ?: continue
        val entry = VsCodeThemes.themeEntries(manifest, nlsOf(folder)).firstOrNull { it.matches(name) } ?: continue
        val file = folder.resolve(entry.path).normalize()
        if (!file.exists()) continue
        return@withContext with(VsCodeThemes) {
            VsCodeTheme.read(file.readText(), file.name) { include -> file.resolveSibling(include).takeIf { it.exists() }?.readText() }.asContributed(entry)
        }
    }
    null
}

private const val SETTINGS_LABEL = "VS Code, as you set it up"

/** A scheme one of the person's JetBrains IDEs has, whether it is the one in use, and the IDE's editor font. */
private class Installed(val product: String, val file: Path, val name: String, val current: Boolean, val font: CodeFont?)

/** The schemes the person's JetBrains IDEs keep, each IDE's own folder under its config folder, newest first. */
private fun jetBrainsSchemes(): List<Installed> {
    val config = System.getenv("APPDATA")?.let { Path.of(it, "JetBrains") }
        ?: Path.of(System.getProperty("user.home"), if (System.getProperty("os.name").startsWith("Mac")) "Library/Application Support/JetBrains" else ".config/JetBrains")
    if (!config.isDirectory()) return emptyList()
    val out = ArrayList<Installed>()
    for (ide in config.listDirectoryEntries().filter { it.isDirectory() && it.resolve("options").isDirectory() }.sortedByDescending { it.name }) {
        val colors = ide.resolve("colors")
        if (!colors.isDirectory()) continue
        val current = runCatching {
            Regex("""global_color_scheme name="([^"]+)"""").find(ide.resolve("options/colors.scheme.xml").readText())?.groupValues?.get(1)
        }.getOrNull()
        val font = runCatching { editorFont(ide.resolve("options/editor-font.xml")) }.getOrNull()
        for (file in colors.listDirectoryEntries("*.icls")) {
            val name = runCatching { JetBrainsSchemes.parse(file.readText()).name }.getOrNull() ?: continue
            out += Installed(ide.name, file, name, name == current, font)
        }
    }
    return out
}

/** An IDE's editor font from its `editor-font.xml`: family, size and line spacing, with IntelliJ's defaults. */
private fun editorFont(file: Path): CodeFont? {
    if (!file.exists()) return null
    val options = HashMap<String, String>()
    fun walk(element: XmlElement) {
        if (element.name == "option") options[element.attribute("name")] = element.attribute("value")
        element.children.forEach(::walk)
    }
    walk(readXml(file.readText()))
    val family = options["FONT_FAMILY"] ?: return null
    return CodeFont(
        listOf(family),
        options["FONT_SIZE_2D"]?.toFloatOrNull() ?: options["FONT_SIZE"]?.toFloatOrNull() ?: JetBrainsSchemes.DEFAULT_SIZE,
        options["LINE_SPACING"]?.toFloatOrNull() ?: JetBrainsSchemes.DEFAULT_LINE_SPACING,
        ligatures = options["USE_LIGATURES"] == "true",
    )
}

/** The person's VS Code settings.json, where there is one. */
private fun vsCodeSettings(): Path? = listOfNotNull(
    System.getenv("APPDATA")?.let { Path.of(it, "Code", "User", "settings.json") },
    Path.of(System.getProperty("user.home"), "Library", "Application Support", "Code", "User", "settings.json"),
    Path.of(System.getProperty("user.home"), ".config", "Code", "User", "settings.json"),
).firstOrNull { it.exists() }

/** Every colour theme the person's VS Code has, by label. */
private fun vsCodeThemeNames(): List<String> = vsCodeThemeFolders().flatMap { folder ->
    folder.resolve("package.json").takeIf { it.exists() }
        ?.let { runCatching { VsCodeThemes.themeEntries(readJson(it.readText()) as Map<*, *>, nlsOf(folder)).map { entry -> entry.label } }.getOrNull() }
        .orEmpty()
}.distinct()

private fun nlsOf(folder: Path): Map<*, *>? =
    folder.resolve("package.nls.json").takeIf { it.exists() }?.let { runCatching { readJson(it.readText()) as? Map<*, *> }.getOrNull() }

/** The folders VS Code's built-in and installed extensions are in. */
private fun vsCodeThemeFolders(): List<Path> {
    val folders = ArrayList<Path>()
    vsCodeApp()?.resolve("extensions")?.takeIf { it.isDirectory() }?.let { folders += it.listDirectoryEntries().filter { dir -> dir.isDirectory() } }
    Path.of(System.getProperty("user.home"), ".vscode", "extensions").takeIf { it.isDirectory() }?.let { folders += it.listDirectoryEntries().filter { dir -> dir.isDirectory() } }
    return folders
}

/** VS Code's app folder as found on this machine: through `code` on the path, then where it installs by default. */
private fun vsCodeApp(): Path? {
    val candidates = ArrayList<Path>()
    System.getenv("PATH")?.split(File.pathSeparator)?.forEach { dir ->
        val bin = runCatching { Path.of(dir) }.getOrNull() ?: return@forEach
        if (bin.name.equals("bin", ignoreCase = true) && (bin.resolve("code.cmd").exists() || bin.resolve("code").exists())) bin.parent?.let(candidates::add)
    }
    System.getenv("LOCALAPPDATA")?.let { candidates.add(Path.of(it, "Programs", "Microsoft VS Code")) }
    System.getenv("ProgramFiles")?.let { candidates.add(Path.of(it, "Microsoft VS Code")) }
    candidates.add(Path.of("/Applications/Visual Studio Code.app/Contents/Resources"))
    candidates.add(Path.of("/usr/share/code/resources"))
    for (root in candidates) {
        if (!root.isDirectory()) continue
        // Newer installs keep each version in a folder of its own; the current is the newest.
        val apps = listOf(root.resolve("resources/app"), root.resolve("app")) +
            root.listDirectoryEntries().filter { it.isDirectory() }.sortedByDescending { Files.getLastModifiedTime(it) }.map { it.resolve("resources/app") }
        apps.firstOrNull { it.resolve("package.json").exists() && it.resolve("extensions").isDirectory() }?.let { return it }
    }
    return null
}
