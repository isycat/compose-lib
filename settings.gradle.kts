pluginManagement {
    repositories {
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        google()
        gradlePluginPortal()
        mavenCentral()
        maven("https://jitpack.io")
    }
    
    plugins {
        kotlin("jvm") version "2.4.21"
        kotlin("multiplatform") version "2.4.21"
        id("org.jetbrains.compose") version "1.12.2"
        kotlin("plugin.compose") version "2.4.21"
        kotlin("plugin.serialization") version "2.4.21"
    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        google()
        mavenCentral()
        // maven("https://maven.datlag.dev")
        maven("https://jogamp.org/deployment/maven/")
        maven("https://jitpack.io")
    }
}

rootProject.name = "compose-lib"

// When another build includes this one, its outputs go to build/included-by/<including build's folder>-<path hash>/
// so builds that run at the same time never write the same files.
generateSequence(gradle) { it.parent }.last().takeIf { it !== gradle }?.startParameter?.let { start ->
    val root = (start.projectDir ?: start.currentDir).canonicalFile
    val folder = "build/included-by/${root.name}-${"%08x".format(root.path.hashCode())}"
    gradle.beforeProject { layout.buildDirectory.set(layout.projectDirectory.dir(folder)) }
}
