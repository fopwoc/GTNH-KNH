rootProject.name = "gtnh-kotlin-monorepo"

pluginManagement {
    includeBuild("knhmp")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":framework")
