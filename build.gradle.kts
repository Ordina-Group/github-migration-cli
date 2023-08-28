import org.jreleaser.model.Distribution.DistributionType
import java.nio.file.Paths

plugins {
    kotlin("jvm") version "1.9.10"
    kotlin("plugin.serialization") version "1.9.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.1"
    id("org.jlleitschuh.gradle.ktlint") version "11.5.1"
    id("org.jreleaser") version "1.7.0"
    application
}

group = "nl.ordina"
version = "1.0-SNAPSHOT"

application {
    mainClass.set("nl.ordina.migration.Main")
}

kotlin {
    jvmToolchain(17)
}

jreleaser {
    configurations {
        create("osx-aarch_64")
    }

    project {
        copyright.set("2023 Ordina NV")
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
        }
    }

    packagers {
        distributions {
            create("osx-aarch_64") {
                executable.name.set("github-migration-cli")
                distributionType.set(DistributionType.BINARY)

                artifact {
                    path.set(Paths.get("$buildDir/distributions/github-migration-cli-native-$version.zip").toFile())
                }
            }
        }
    }
}

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("nl.ordina:github-kotlin-client:0.0.2-SNAPSHOT")
    implementation("com.github.ajalt.clikt:clikt:4.2.0")
    implementation("com.github.ajalt.mordant:mordant:2.0.0-beta13")
}

repositories {
    mavenCentral()
    mavenLocal()
}
