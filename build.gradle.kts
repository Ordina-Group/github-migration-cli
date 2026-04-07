import io.gitlab.arturbosch.detekt.Detekt
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform.getCurrentOperatingSystem
import org.gradle.nativeplatform.platform.internal.DefaultOperatingSystem
import org.jreleaser.model.Distribution.DistributionType
import java.nio.file.Paths

plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("org.graalvm.buildtools.native") version "1.0.0"
    id("org.jreleaser") version "1.23.0"
    application
}

group = "com.soprasteria"
version = "0.1-RC2"

val jdkVersion = 21
val currentOperatingSystem: DefaultOperatingSystem = getCurrentOperatingSystem()
val currentOperatingSystemName: String =
    when {
        currentOperatingSystem.isWindows -> "windows"
        currentOperatingSystem.isLinux -> "linux"
        currentOperatingSystem.isMacOsX -> "osx"
        else -> "unknown"
    }

application {
    mainClass.set("com.soprasteria.migration.MainKt")
}

kotlin {
    jvmToolchain(jdkVersion)
}

detekt {
    config.setFrom(files("$projectDir/config/detekt/detekt.yml"))
}

graalvmNative {
    binaries {
        named("main") {
            imageName.set("github-migration")
            debug.set(false)
            verbose.set(false)
            buildArgs.add("--initialize-at-build-time=kotlin.DeprecationLevel")
        }
    }

    metadataRepository {
        enabled.set(true)
    }
}

jreleaser {
    configurations {
        create("osx-aarch_64")
    }

    distributions {
        create("github-migration") {
            distributionType.set(DistributionType.BINARY)
            artifact {
                path.set(Paths.get("build/distributions/{{distributionName}}-{{projectVersion}}.zip").toFile())
            }
        }
    }

    project {
        description.set("CLI to migrate all repositories, teams and members from one organization to another")
        copyright.set("2023 Ordina NV")
        license.set("GNU General Public License v3.0")
        authors.add("Donovan de Kuiper")
        links {
            homepage.set("https://github.com/Ordina-Group/github-migration")
            documentation.set("https://github.com/Ordina-Group/github-migration")
        }
        inceptionYear.set("2023")
    }

    release {

        github {
            enabled.set(true)
            overwrite.set(true)

            prerelease {
                pattern.set(".*-RC\\d*")
            }
        }
    }
}

dependencies {
    implementation("com.soprasteria:github-kotlin-client:1.0.0")
    implementation("com.github.ajalt.clikt:clikt:4.2.0")
    implementation("com.github.ajalt.mordant:mordant:2.1.0")
    implementation(platform("io.arrow-kt:arrow-stack:1.2.1"))
    implementation("io.arrow-kt:arrow-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    testImplementation(platform("io.kotest:kotest-bom:5.8.0"))
    testImplementation("io.kotest:kotest-runner-junit5")
    testImplementation("io.kotest:kotest-assertions-core")
    testImplementation("io.kotest:kotest-property")
    testImplementation("io.mockk:mockk:1.13.8")
}

tasks.test {
    useJUnitPlatform()
}

// detekt 1.23.x does not support jvmTarget 21; cap it at 20 until detekt 2.x is stable
tasks.withType<Detekt>().configureEach {
    jvmTarget = "20"
}

repositories {
    mavenCentral()
    mavenLocal()

    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/Ordina-Group/github-kotlin-client")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_USERNAME")
            password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }
}

tasks.register<Zip>("package") {
    dependsOn(tasks.nativeCompile)

    archiveFileName.set("github-migration-${archiveVersion.get()}-$currentOperatingSystemName.zip")

    from(layout.buildDirectory.dir("native/nativeCompile"))
}

tasks.named("assemble") {
    dependsOn(tasks.named("package"))
}
