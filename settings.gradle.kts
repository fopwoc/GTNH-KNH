rootProject.name = "gtnh-kotlin-monorepo"

apply(from = "gradle/shared-settings-properties.settings.gradle.kts")

includeBuild("knhmp")

include(":framework")
