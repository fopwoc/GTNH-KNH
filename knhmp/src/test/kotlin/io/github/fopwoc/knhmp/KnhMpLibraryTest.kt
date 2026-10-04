package io.github.fopwoc.knhmp

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue
import org.gradle.testkit.runner.GradleRunner

class KnhMpLibraryTest {
    private fun build(library: String): Path {
        val root = createTempDirectory("knhmp-library-")
        root
            .resolve("settings.gradle.kts")
            .writeText(
                """
                rootProject.name = "library-smoke"
                include(":lib", ":mod")
                """
                    .trimIndent()
            )
        root
            .resolve("build.gradle.kts")
            .writeText(
                """
                plugins {
                    base
                    id("io.github.fopwoc.knhmp") apply false
                    id("io.github.fopwoc.knhmp.library") apply false
                }
                """
                    .trimIndent()
            )
        val lib = root.resolve("lib").createDirectories()
        lib.resolve("build.gradle.kts")
            .writeText(
                """
            plugins {
                id("io.github.fopwoc.knhmp.library")
            }

            knhmpLibrary {
                $library
            }
            """
                    .trimIndent()
            )
        lib.resolve("src/main/kotlin/demo")
            .createDirectories()
            .resolve("Library.kt")
            .writeText("package demo\n\nobject Library\n")
        val mod = root.resolve("mod").createDirectories()
        mod.resolve("build.gradle.kts")
            .writeText(
                """
                plugins {
                    id("io.github.fopwoc.knhmp")
                }

                knhmp {
                    modId = "librarysmoke"
                    javaToolchain = 21

                    sourceSets {
                        commonMain {
                            jvmTarget = 17
                            include(":lib")
                        }
                        gtnhMain {
                            dependsOn(commonMain)
                        }
                    }

                    targets {
                        gtnh {
                            plugins {
                                id("com.gtnewhorizons.gtnhconvention", "2.0.31")
                            }
                            kotlin {
                                stdlibVersion = "2.1.10"
                            }
                        }
                    }
                }
                """
                    .trimIndent()
            )
        return root
    }

    private fun runner(root: Path) =
        GradleRunner.create()
            .withProjectDir(root.toFile())
            .withArguments(":mod:tasks", "--stacktrace")
            .withPluginClasspath()

    @Test
    fun `islands compile included library sources`() {
        val root = build("jvmTarget = 17\nstdlibVersion = \"2.1.10\"")
        try {
            runner(root).build()
            val island = root.resolve("mod/.knhmp/gtnh/build.gradle.kts").toFile().readText()
            assertContains(island, root.resolve("lib/src/main/kotlin").toFile().path)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `a library newer than an island is refused`() {
        val root = build("jvmTarget = 21\nstdlibVersion = \"2.4.20\"")
        try {
            val output = runner(root).buildAndFail().output
            assertContains(output, "library targets Java 21, island targets Java 17")
            assertContains(output, "library uses Kotlin API 2.4, island provides 2.1")
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `an unpinned library is refused by a pinned island`() {
        val root = build("jvmTarget = 17")
        try {
            val output = runner(root).buildAndFail().output
            assertTrue(output.contains("library uses Kotlin API null, island provides 2.1"), output)
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
