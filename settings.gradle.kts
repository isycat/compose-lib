pluginManagement {
    repositories {
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        google()
        gradlePluginPortal()
        mavenCentral()
        maven("https://jitpack.io")
    }
    
    plugins {
        kotlin("jvm") version "2.0.21"
        id("org.jetbrains.compose") version "1.8.1"
        kotlin("plugin.compose") version "2.0.21"
        kotlin("plugin.serialization") version "2.0.21"
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
