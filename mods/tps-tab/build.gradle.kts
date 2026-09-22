plugins {
    id("io.github.fopwoc.knhmp")
    alias(libs.plugins.compose.compiler)
}

knhmp {
    modId = "tpstab"
    modName = "TPS Tab"
    modGroup = "io.github.fopwoc.mods.tabtps"

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
                stdlibVersion = libs.versions.kotlinStdlib.get()
            }
            plugins {
                alias(libs.plugins.gtnh.convention)
                alias(libs.plugins.compose.compiler)
            }
            dependencies {
                implementation(libs.forgelin)
            }
        }
    }
}
