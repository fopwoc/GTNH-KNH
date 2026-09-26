plugins {
    id("io.github.fopwoc.knhmp")
    alias(libs.plugins.compose.compiler)
}

knhmp {
    modId = "tpstab"
    modName = "TPS Tab"
    modGroup = "io.github.fopwoc.mods.tabtps"
    archiveName = "knh-tps-tab"

    sourceSets {
        commonMain {
            jvmTarget = 21
        }
        gtnhMain {
            dependsOn(commonMain)
        }
        val modernMain = sourceSet("modernMain").apply { dependsOn(commonMain) }
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
            jvmTarget = libs.versions.jvmBytecode.get().toInt()
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
            minecraft(
                libs.versions.minecraft.get(),
                libs.versions.minecraft261.get(),
                libs.versions.minecraft1211.get(),
            )
            kotlin {
                stdlibVersion = libs.versions.fabricKotlinStdlib.get()
            }
            plugins {
                alias(libs.plugins.compose.compiler)
            }
            minecraft(libs.versions.minecraft.get()) {
                jvmTarget = 25
                plugins {
                    alias(libs.plugins.loom)
                }
                dependencies {
                    implementation(libs.fabric.loader)
                    implementation(libs.fabric.api)
                    implementation(libs.fabric.language.kotlin)
                }
            }
            minecraft(libs.versions.minecraft261.get()) {
                jvmTarget = 25
                plugins {
                    alias(libs.plugins.loom)
                }
                dependencies {
                    implementation(libs.fabric.loader)
                    implementation(libs.fabric.api.v261)
                    implementation(libs.fabric.language.kotlin)
                }
            }
            minecraft(libs.versions.minecraft1211.get()) {
                plugins {
                    alias(libs.plugins.loom.remap)
                }
                dependencies {
                    modImplementation(libs.fabric.loader)
                    modImplementation(libs.fabric.api.v1211)
                    modImplementation(libs.fabric.language.kotlin)
                }
            }
        }

        neoforge {
            minecraft(
                libs.versions.minecraft.get(),
                libs.versions.minecraft261.get(),
                libs.versions.minecraft1211.get(),
            )
            plugins {
                alias(libs.plugins.moddev)
                alias(libs.plugins.compose.compiler)
            }
            minecraft(libs.versions.minecraft.get()) {
                jvmTarget = 25
                kotlin {
                    stdlibVersion = libs.versions.neoforgeKotlinStdlib.get()
                }
                dependencies {
                    neoForge(libs.neoforge)
                    implementation(libs.kotlinforforge.neoforge)
                }
            }
            minecraft(libs.versions.minecraft261.get()) {
                jvmTarget = 25
                kotlin {
                    stdlibVersion = libs.versions.neoforgeKotlinStdlib.get()
                }
                dependencies {
                    neoForge(libs.neoforge.v261)
                    implementation(libs.kotlinforforge.neoforge)
                }
            }
            minecraft(libs.versions.minecraft1211.get()) {
                kotlin {
                    stdlibVersion = libs.versions.neoforge1211KotlinStdlib.get()
                }
                dependencies {
                    neoForge(libs.neoforge.v1211)
                    implementation(libs.kotlinforforge.neoforge.v1211)
                }
            }
        }
    }
}
