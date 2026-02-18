pluginManagement {
    repositories {
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        google()
        mavenCentral()
        maven("https://maven.datlag.dev")
        maven("https://jogamp.org/deployment/maven/")
        maven("https://jitpack.io")
    }
}

rootProject.name = "compose-lib"
