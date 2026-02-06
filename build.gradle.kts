plugins {
    kotlin("jvm") version "2.1.21"
    id("org.jetbrains.compose") version "1.8.1"
    kotlin("plugin.compose") version "2.1.21"
    kotlin("plugin.serialization") version "2.1.21"
}

group = "com.isycat.compose"
version = "1.0.0-SNAPSHOT"

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    // Embedded browser
    implementation("io.github.kevinnzou:compose-webview-multiplatform:2.0.3")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    
    // For TTS and native integration
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")
}

kotlin {
    jvmToolchain(21)
}
