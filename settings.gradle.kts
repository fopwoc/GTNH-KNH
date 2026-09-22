rootProject.name = "gtnh-kotlin-monorepo"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

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

// Resolves the IDE facades only; generated compiler islands declare their own repositories.
dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
        google()
    }
}

include(":framework")

listOf("tps-tab").forEach { mod ->
    include(":$mod")
    project(":$mod").projectDir = file("mods/$mod")
}
