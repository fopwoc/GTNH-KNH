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

listOf("hotspot", "measure", "palimpsest", "testgui", "tps-tab").forEach { mod ->
    include(":$mod")
    project(":$mod").projectDir = file("mods/$mod")
}

// Pure JVM libraries that mods compile into themselves (`include(...)` on a source set).
listOf("palimpsest-db").forEach { library ->
    include(":$library")
    project(":$library").projectDir = file("lib/$library")
}

// Command-line tools for developers; never shipped with a mod.
listOf("palimpsest-inspect").forEach { tool ->
    include(":$tool")
    project(":$tool").projectDir = file("tools/$tool")
}
