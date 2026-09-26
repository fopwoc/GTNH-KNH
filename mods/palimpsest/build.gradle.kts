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

// Headless storage tools: they use only common code, so they run in the module build without
// Minecraft.
kotlin {
    targets.withType<org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget>().configureEach {
        val main = compilations.getByName("main")
        val benchmark =
            compilations.create("benchmark") {
                associateWith(main)
                defaultSourceSet.kotlin.setSrcDirs(listOf("src/benchmark/kotlin"))
            }

        val benchmarkTest =
            compilations.create("benchmarkTest") {
                associateWith(benchmark)
                defaultSourceSet.kotlin.setSrcDirs(listOf("src/benchmarkTest/kotlin"))
                defaultSourceSet.dependencies { implementation(kotlin("test-junit5")) }
            }
        val benchmarkTestTask =
            tasks.register<Test>("benchmarkTest") {
                group = "verification"
                description = "Runs the storage suite's own tests."
                testClassesDirs = benchmarkTest.output.classesDirs
                classpath =
                    files(benchmarkTest.output.allOutputs, benchmarkTest.runtimeDependencyFiles)
                useJUnitPlatform()
            }
        tasks.named("check") { dependsOn(benchmarkTestTask) }

        fun registerBenchmarkTask(name: String, mainClassName: String, descriptionText: String) {
            tasks.register<JavaExec>(name) {
                group = "verification"
                description = descriptionText
                classpath = files(benchmark.output.allOutputs, benchmark.runtimeDependencyFiles)
                mainClass.set(mainClassName)
            }
        }

        registerBenchmarkTask(
            "analyzeMap",
            "io.github.fopwoc.mods.palimpsest.analyze.AnalyzeMapMainKt",
            "Prints where the bytes of a slice directory go; pass the directory with --args.",
        )
        registerBenchmarkTask(
            "scratchDiff",
            "io.github.fopwoc.mods.palimpsest.analyze.ScratchDiffMainKt",
            "Diffs two scratch slice directories; pass them with --args.",
        )
        registerBenchmarkTask(
            "heightExperiment",
            "io.github.fopwoc.mods.palimpsest.analyze.HeightExperimentMainKt",
            "Tries height and block coding variants over a slice directory's full tiles.",
        )
        registerBenchmarkTask(
            "storageSuite",
            "io.github.fopwoc.mods.palimpsest.benchmark.StorageSuiteMainKt",
            "Runs the isolated storage workload suite and prints its report.",
        )
        tasks.named<JavaExec>("storageSuite") {
            args(layout.buildDirectory.dir("palimpsest").get().asFile.absolutePath)
        }
    }
}
