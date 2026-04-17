rootProject.name = "dejavu"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
	repositories {
		maven("https://nexus.gtnewhorizons.com/repository/public/")
		gradlePluginPortal()
		mavenCentral()
		mavenLocal()
	}
}

plugins {
	id("com.gtnewhorizons.gtnhsettingsconvention") version("2.0.24")
}
