plugins {
    id("io.github.fopwoc.knhmp")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

knhmp {
    modId = "knhcore"
    modName = "KNH Core"
    modGroup = "io.github.fopwoc.mods.framework"
    archiveName = "knh-core"
    javaToolchain = 26

    repositories {
        google()
        maven("https://raw.githubusercontent.com/Fuzss/modresources/main/maven/")
        maven("https://maven.shedaniel.me/")
        maven("https://maven.terraformersmc.com/releases/")
    }

    sourceSets {
        commonMain {
            jvmTarget = libs.versions.jvmBytecode.get().toInt()
        }
        gtnhMain {
            dependsOn(commonMain)
        }
        // Vanilla Minecraft 26.2 with Mojang names, shared by every modern loader.
        val modernMain = sourceSet("modernMain").apply {
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
        // Shipped inside knh-core on every loader; mods only compile against them.
        // lifecycle-viewmodel-compose drags in Compose UI, which the framework never uses or ships.
        exclude("org.jetbrains.compose.ui", "ui")
        bundle(libs.serialization.json)
        bundle(libs.compose.runtime)
        bundle(libs.compose.runtime.saveable)
        bundle(libs.lifecycle.runtime)
        bundle(libs.lifecycle.runtime.compose)
        bundle(libs.lifecycle.viewmodel)
        bundle(libs.lifecycle.viewmodel.compose)
        bundle(libs.navigation3.runtime)
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
            }
            compilerScript("knhmp/gtnh.gradle.kts")
        }

        fabric {
            minecraft(libs.versions.minecraft.get())
            kotlin {
                stdlibVersion = libs.versions.fabricKotlinStdlib.get()
            }
            plugins {
                alias(libs.plugins.loom)
                alias(libs.plugins.kotlin.serialization)
                alias(libs.plugins.compose.compiler)
            }
            dependencies {
                implementation(libs.fabric.loader)
                implementation(libs.fabric.api)
                implementation(libs.fabric.language.kotlin)
                implementation(libs.forgeconfigapiport.fabric)
                implementation(libs.cloth.config.fabric)
                implementation(libs.modmenu)
            }
        }

        neoforge {
            minecraft(libs.versions.minecraft.get())
            kotlin {
                stdlibVersion = libs.versions.neoforgeKotlinStdlib.get()
            }
            plugins {
                alias(libs.plugins.moddev)
                alias(libs.plugins.kotlin.serialization)
                alias(libs.plugins.compose.compiler)
            }
            dependencies {
                neoForge(libs.neoforge)
                implementation(libs.kotlinforforge.neoforge)
            }
        }
    }
}
