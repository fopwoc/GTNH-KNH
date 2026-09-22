plugins {
    id("io.github.fopwoc.knhmp")
}

val buildVersion = providers.environmentVariable("VERSION").orElse("0.1.0-SNAPSHOT")

knhmp {
    modId = "testgui"
    modName = "Test GUI"
    modGroup = "io.github.fopwoc.mods.testgui"
    modVersion = buildVersion.get()

    sourceSets {
        gtnhMain { jvmTarget = 24 }
    }

    targets {
        gtnh {
            kotlin { apiVersion = libs.versions.kotlinApi.get() }
            plugins {
                id("com.gtnewhorizons.gtnhconvention", "2.0.29")
                alias(libs.plugins.kotlin.serialization)
                alias(libs.plugins.compose.compiler)
                alias(libs.plugins.detekt)
            }
            dependencies {
                implementation(libs.forgelin)
                module(":framework")
            }
            compilerScript("../../gradle/knhmp-gtnh.gradle.kts")
        }
    }
}
