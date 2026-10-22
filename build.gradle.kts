plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    kotlin("plugin.compose")
    kotlin("plugin.serialization")
    `maven-publish`
}

group = "com.isycat.compose"
version = "v1.02"

repositories {
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    mavenCentral()
    google()
    // maven("https://maven.datlag.dev")
    maven("https://jogamp.org/deployment/maven/")
}

// The components (commonMain) run wherever Compose does: the desktop (jvm) and the browser (wasmJs). What only a
// desktop has (the embedded browser, speech, the native file dialogs and the Windows integrations) is in jvmMain.
kotlin {
    jvmToolchain(21)
    compilerOptions {
        // FileTypeFilter is an expected class, so the desktop keeps the one it always had.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    jvm()
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            // No browser tests: the components are tested on the desktop.
            testTask { enabled = false }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)

            // Embedded browser
            implementation("io.github.kevinnzou:compose-webview-multiplatform:2.0.3")

            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")

            // Serialization
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")

            // For TTS and native integration
            implementation("net.java.dev.jna:jna:5.14.0")
            implementation("net.java.dev.jna:jna-platform:5.14.0")
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            // runComposeUiTest: drive real components (clicks, right-clicks, popups) in tests.
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
        }
    }
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
}

// Kotlin Multiplatform makes a publication per target (compose-lib, compose-lib-jvm, compose-lib-wasm-js); a desktop
// app asking for com.github.isycat:compose-lib gets the jvm one through Gradle's module metadata.
publishing {
    publications.withType<MavenPublication>().configureEach {
        groupId = "com.github.isycat"
        pom {
            name.set("Compose Lib")
            description.set("Reusable Compose components and utilities, for the desktop and the browser")
            url.set("https://github.com/isycat/compose-lib")

            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://opensource.org/licenses/MIT")
                }
            }

            developers {
                developer {
                    id.set("isycat")
                    name.set("isycat")
                }
            }

            scm {
                connection.set("scm:git:git://github.com/isycat/compose-lib.git")
                developerConnection.set("scm:git:ssh://github.com/isycat/compose-lib.git")
                url.set("https://github.com/isycat/compose-lib")
            }
        }
    }
}
