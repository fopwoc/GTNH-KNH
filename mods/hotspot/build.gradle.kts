plugins {
    id("io.github.fopwoc.knhmp")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

knhmp {
    modId = "hotspot"
    modName = "Hotspot"
    modGroup = "io.github.fopwoc.mods.hotspot"

    sourceSets {
        commonMain {
            jvmTarget = libs.versions.jvmBytecode.get().toInt()
        }
        gtnhMain {
            dependsOn(commonMain)
        }
    }

    dependencies {
        implementation(projects.framework)
    }

    targets {
        gtnh {
            kotlin {
                stdlibVersion = libs.versions.gtnhKotlinStdlib.get()
            }
            plugins {
                alias(libs.plugins.gtnh.convention)
                alias(libs.plugins.kotlin.serialization)
                alias(libs.plugins.compose.compiler)
            }
            dependencies {
                implementation(libs.forgelin)
                compileOnly(variantOf(libs.opis) { classifier("dev") })
            }
        }
    }
}
