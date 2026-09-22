plugins {
    id("io.github.fopwoc.knhmp")
}

val buildVersion = providers.environmentVariable("VERSION").orElse("0.1.0-SNAPSHOT")

knhmp {
    modId = "hotspot"
    modName = "Hotspot"
    modGroup = "io.github.fopwoc.mods.hotspot"
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
                compileOnly("com.github.GTNewHorizons:Opis:${libs.versions.opis.get()}:dev")
            }
            compilerScript("../../gradle/knhmp-gtnh.gradle.kts")
        }
    }
}
