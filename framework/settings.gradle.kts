apply(from = "../gradle/shared-settings-properties.settings.gradle.kts")

rootProject.name = "knh-core"

pluginManagement {
    includeBuild("../knhmp")
    repositories {
        maven("https://nexus.gtnewhorizons.com/repository/public/")
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
