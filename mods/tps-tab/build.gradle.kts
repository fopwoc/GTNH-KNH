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
        val modernMain =
            sourceSet("modernMain").apply {
                dependsOn(commonMain)
                jvmTarget = 25
            }
        fabricMain {
            dependsOn(modernMain)
        }
        neoforgeMain {
            dependsOn(modernMain)
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
                alias(libs.plugins.compose.compiler)
            }
            dependencies {
                implementation(libs.forgelin)
            }
        }

        fabric {
            minecraft(libs.versions.minecraft.get())
            kotlin {
                stdlibVersion = libs.versions.fabricKotlinStdlib.get()
            }
            plugins {
                alias(libs.plugins.loom)
                alias(libs.plugins.compose.compiler)
            }
            dependencies {
                implementation(libs.fabric.loader)
                implementation(libs.fabric.api)
                implementation(libs.fabric.language.kotlin)
            }
        }

        neoforge {
            minecraft(libs.versions.minecraft.get())
            kotlin {
                stdlibVersion = libs.versions.neoforgeKotlinStdlib.get()
            }
            plugins {
                alias(libs.plugins.moddev)
                alias(libs.plugins.compose.compiler)
            }
            dependencies {
                neoForge(libs.neoforge)
                implementation(libs.kotlinforforge.neoforge)
            }
        }
    }
}
