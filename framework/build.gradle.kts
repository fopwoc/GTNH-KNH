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

    // Development jars for other mods; CI points mavenRepository at the gh-pages checkout.
    publishing {
        groupId = "io.github.fopwoc"
        repository(
            "maven",
            providers.gradleProperty("mavenRepository").orNull
                ?: layout.buildDirectory.dir("maven").get().asFile,
        )
        pom {
            licenses {
                license {
                    name.set("WTFNMFPL")
                    url.set("https://github.com/fopwoc/GTNH-KNH/blob/main/LICENSE")
                }
            }
            developers {
                developer {
                    id.set("fopwoc")
                    name.set("fopwoc")
                }
            }
        }
    }

    repositories {
        google()
        maven("https://raw.githubusercontent.com/Fuzss/modresources/main/maven/")
        maven("https://maven.shedaniel.me/")
        maven("https://maven.terraformersmc.com/releases/")
    }

    sourceSets {
        // Java 21 is the oldest runtime of any target (Minecraft 1.21.1); targets raise it.
        commonMain {
            jvmTarget = 21
        }
        gtnhMain {
            dependsOn(commonMain)
            // FrameworkBootstrap: loads on any Java and refuses unsupported ones with a clear
            // message.
            legacyJava(jvmTarget = 8)
        }
        // Vanilla Minecraft with Mojang names, shared by every modern loader and version; version
        // differences are Stonecutter comments.
        val modernMain = sourceSet("modernMain").apply { dependsOn(commonMain) }
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
            }
        }

        fabric {
            minecraft(libs.versions.minecraft.get(), libs.versions.minecraft1211.get())
            mixins {
                packageName = "io.github.fopwoc.mods.framework.fabric.mixin"
            }
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
                    implementation(libs.forgeconfigapiport.fabric)
                    implementation(libs.cloth.config.fabric)
                    implementation(libs.modmenu)
                }
            }
            // Obfuscated: Loom's remapping plugin, and mods through the remapping configurations.
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
            minecraft(libs.versions.minecraft.get(), libs.versions.minecraft1211.get())
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
