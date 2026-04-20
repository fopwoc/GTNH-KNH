apply(from = "../gradle/shared-settings-properties.settings.gradle.kts")

rootProject.name = "knh-core"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
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
	id("com.gtnewhorizons.gtnhsettingsconvention") version("2.0.24")
}

