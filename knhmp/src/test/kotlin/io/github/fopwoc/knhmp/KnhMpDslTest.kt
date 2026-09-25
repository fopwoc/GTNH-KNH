package io.github.fopwoc.knhmp

import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertTrue
import org.gradle.testkit.runner.GradleRunner

class KnhMpDslTest {

    @Test
    fun `Kotlin DSL configures the public model`() {
        val projectDirectory = createTempDirectory("knhmp-dsl-")
        try {
            projectDirectory
                .resolve("settings.gradle.kts")
                .writeText(
                    """
                    rootProject.name = "dsl-smoke"
                    """
                        .trimIndent()
                )
            projectDirectory
                .resolve("build.gradle.kts")
                .writeText(
                    """
                    plugins {
                        id("io.github.fopwoc.knhmp")
                    }

                    knhmp {
                        modId = "dslsmoke"
                        javaToolchain = 21

                        sourceSets {
                            commonMain {
                                jvmTarget = 17
                            }
                            gtnhMain {
                                dependsOn(commonMain)
                                jvmTarget = 17
                            }
                        }

                        targets {
                            gtnh {
                                plugins {
                                    id("com.gtnewhorizons.gtnhconvention", "2.0.31")
                                }
                            }
                        }
                    }
                    """
                        .trimIndent()
                )

            val result =
                GradleRunner.create()
                    .withProjectDir(projectDirectory.toFile())
                    .withArguments("tasks", "--all", "--stacktrace")
                    .withPluginClasspath()
                    .build()

            assertTrue(result.output.contains("buildAll"))
            assertTrue(result.output.contains("compileGtnhTestKotlinIde"))
            val islandBuild = projectDirectory.resolve(".knhmp/gtnh/build.gradle.kts").toFile()
            assertTrue(islandBuild.isFile)
            val generatedScript = islandBuild.readText()
            assertTrue(generatedScript.contains("src/gtnhTest/kotlin"))
            assertTrue(generatedScript.contains("src/commonTest/kotlin"))
            assertTrue(
                generatedScript.contains("tasks.named(\"reobfJar\") { dependsOn(\"test\") }")
            )
        } finally {
            projectDirectory.toFile().deleteRecursively()
        }
    }
}
