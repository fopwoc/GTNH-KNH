plugins {
    id("io.github.fopwoc.knhmp")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

repositories {
    google()
}

knhmp {
    modId = "knhcore"
    modName = "KNH Core"
    modGroup = "io.github.fopwoc.mods.framework"
    archiveName = "knh-core"
    javaToolchain = 26

    sourceSets {
        commonMain {
            jvmTarget = 24
        }
        gtnhMain {
            dependsOn(commonMain)
            jvmTarget = 24
        }
    }

    dependencies {
        api(libs.serialization.json)
        add("bundledLibraries", libs.serialization.json)
        api(libs.compose.runtime)
        add("bundledLibraries", libs.compose.runtime)
        api(libs.compose.runtime.saveable)
        add("bundledLibraries", libs.compose.runtime.saveable)
        api(libs.lifecycle.runtime)
        add("bundledLibraries", libs.lifecycle.runtime)
        api(libs.lifecycle.runtime.compose)
        add("bundledLibraries", libs.lifecycle.runtime.compose)
        api(libs.lifecycle.viewmodel)
        add("bundledLibraries", libs.lifecycle.viewmodel)
        api(libs.lifecycle.viewmodel.compose)
        add("bundledLibraries", libs.lifecycle.viewmodel.compose)
        api(libs.navigation3.runtime)
        add("bundledLibraries", libs.navigation3.runtime)
    }

    targets {
        gtnh {
            kotlin {
                apiVersion = libs.versions.kotlinApi.get()
            }
            plugins {
                id("com.gtnewhorizons.gtnhconvention", "2.0.29")
                alias(libs.plugins.kotlin.serialization)
                alias(libs.plugins.compose.compiler)
                alias(libs.plugins.detekt)
            }
            dependencies {
                implementation(libs.forgelin)
                add("detektPlugins", libs.compose.rules.detekt)
            }
            compilerScript("knhmp/gtnh.gradle.kts")
        }
    }
}
