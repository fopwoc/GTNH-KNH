plugins {
    id("io.github.fopwoc.knhmp")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

knhmp {
    modId = "palimpsest"
    modName = "Palimpsest"
    modGroup = "io.github.fopwoc.mods.palimpsest"
    archiveName = "knh-palimpsest"

    sourceSets {
        commonMain {
            jvmTarget = 21
            include(projects.palimpsestDb)
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
                alias(libs.plugins.kotlin.serialization)
                alias(libs.plugins.compose.compiler)
            }
            dependencies {
                implementation(libs.forgelin)
                compileOnly("com.github.GTNewHorizons:VisualProspecting:1.5.41:dev")
                compileOnly("com.github.GTNewHorizons:TCNodeTracker:1.4.6:dev")
                compileOnly("com.github.GTNewHorizons:ServerUtilities:2.4.13:dev")
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
                alias(libs.plugins.kotlin.serialization)
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
                alias(libs.plugins.kotlin.serialization)
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
