import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform.getCurrentOperatingSystem
import org.gradle.nativeplatform.platform.internal.DefaultOperatingSystem
import org.jreleaser.model.Active
import org.jreleaser.model.Distribution.DistributionType
import org.jreleaser.model.Stereotype
import java.nio.file.Paths

plugins {
    kotlin("jvm") version "1.9.10"
    kotlin("plugin.serialization") version "1.9.10"
    id("io.gitlab.arturbosch.detekt") version "1.23.1"
    id("org.jlleitschuh.gradle.ktlint") version "11.5.1"
    id("org.graalvm.buildtools.native") version "0.9.26"
    id("org.jreleaser") version "1.7.0"
    application
}

group = "nl.ordina"
version = "0.1-RC2"

val jdkVersion = 17
val currentOperatingSystem: DefaultOperatingSystem = getCurrentOperatingSystem()
val currentOperatingSystemName: String = when {
    currentOperatingSystem.isWindows -> "windows"
    currentOperatingSystem.isLinux -> "linux"
    currentOperatingSystem.isMacOsX -> "osx"
    else -> "unknown"
}

application {
    mainClass.set("nl.ordina.migration.MainKt")
}

kotlin {
    jvmToolchain(jdkVersion)
}

graalvmNative {
    binaries {
        named("main") {
            imageName.set("github-migration-cli")
            debug.set(false)
            verbose.set(false)

            javaLauncher.set(
                javaToolchains.launcherFor {
                    languageVersion.set(JavaLanguageVersion.of(jdkVersion))
                }
            )
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


    project {
        description.set("CLI to migrate all repositories, teams and members from one organization to another")
        copyright.set("2023 Ordina NV")
        license.set("GNU General Public License v3.0")
        authors.add("Donovan de Kuiper")
        links {
            homepage.set("https://github.com/Ordina-Group/github-migration-cli")
            documentation.set("https://github.com/Ordina-Group/github-migration-cli")
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
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("nl.ordina:github-kotlin-client:0.0.2")
    implementation("com.github.ajalt.clikt:clikt:4.2.0")
    implementation("com.github.ajalt.mordant:mordant:2.1.0")
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

    from(layout.buildDirectory.dir("native/nativeCompile"))
}

tasks.named("assemble") {
    dependsOn(tasks.named("package"))
}
