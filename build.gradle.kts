plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    kotlin("plugin.compose")
    kotlin("plugin.serialization")
    `maven-publish`
}

group = "com.isycat.compose"
version = "v1.0.1"

repositories {
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    mavenCentral()
    google()
    // maven("https://maven.datlag.dev")
    maven("https://jogamp.org/deployment/maven/")
}

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

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.github.isycat"
            artifactId = "compose-lib"
            version = "v1.0.1"
            
            from(components["java"])
            
            pom {
                name.set("Compose Lib")
                description.set("Reusable Compose Desktop components and utilities")
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
}
