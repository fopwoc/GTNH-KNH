plugins {
    id("io.github.fopwoc.knhmp")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

knhmp {
    modId = "palimpsest"
    modName = "Palimpsest"
    modGroup = "io.github.fopwoc.mods.palimpsest"

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
                alias(libs.plugins.kotlin.serialization)
                alias(libs.plugins.compose.compiler)
            }
            dependencies {
                implementation(libs.forgelin)
            }
        }
    }
}

// Headless storage tools: they use only common code, so they run in the module build without Minecraft.
kotlin {
    targets.withType<org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget>().configureEach {
        val main = compilations.getByName("main")
        val benchmark = compilations.create("benchmark") {
            associateWith(main)
            defaultSourceSet.kotlin.setSrcDirs(listOf("src/benchmark/kotlin"))
        }

        val benchmarkTest = compilations.create("benchmarkTest") {
            associateWith(benchmark)
            defaultSourceSet.kotlin.setSrcDirs(listOf("src/benchmarkTest/kotlin"))
            defaultSourceSet.dependencies { implementation(kotlin("test-junit5")) }
        }
        val benchmarkTestTask = tasks.register<Test>("benchmarkTest") {
            group = "verification"
            description = "Runs the storage suite's own tests."
            testClassesDirs = benchmarkTest.output.classesDirs
            classpath = files(benchmarkTest.output.allOutputs, benchmarkTest.runtimeDependencyFiles)
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
