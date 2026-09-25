import org.gradle.api.publish.maven.MavenPublication
import org.gradle.plugin.compatibility.compatibility
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.20"
    `java-gradle-plugin`
    `maven-publish`
    id("com.gradle.plugin-publish") version "2.2.1"
}

group = "io.github.fopwoc"

version = BuildIdentity.version(rootDir.parentFile)

description = "Hierarchical multi-loader and multi-version Minecraft mod build orchestration"

tasks.register("printVersion") {
    doLast {
        println(project.version)
    }
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin-idea:2.4.20")
    // Only the APIs, to configure the plugins; the consuming build picks their versions.
    compileOnly("com.diffplug.spotless:spotless-plugin-gradle:8.10.2")
    compileOnly("dev.detekt:detekt-gradle-plugin:2.0.0-alpha.6")
    testImplementation(gradleTestKit())
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(26)
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

gradlePlugin {
    website.set("https://github.com/fopwoc/GTNH-KNH/tree/main/knhmp")
    vcsUrl.set("https://github.com/fopwoc/GTNH-KNH")
    plugins {
        create("knhMp") {
            id = "io.github.fopwoc.knhmp"
            implementationClass = "io.github.fopwoc.knhmp.KnhMpPlugin"
            displayName = "KnhMP"
            description = project.description
            tags.set(listOf("minecraft", "multiloader", "multiversion", "kotlin"))
            compatibility {
                features {
                    configurationCache = false
                }
            }
        }
        create("knhMpQuality") {
            id = "io.github.fopwoc.knhmp.quality"
            implementationClass = "io.github.fopwoc.knhmp.quality.KnhMpQualityPlugin"
            displayName = "KnhMP quality"
            description =
                "Repository-wide formatting (Spotless: ktfmt, palantir-java-format) and analysis (detekt) for KnhMP modules"
            tags.set(listOf("minecraft", "kotlin", "formatting", "detekt"))
            compatibility {
                features {
                    configurationCache = false
                }
            }
        }
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("KnhMP Gradle Plugin")
            description.set(project.description)
            url.set("https://github.com/fopwoc/GTNH-KNH")
            licenses {
                license {
                    name.set("Do What The Fuck You Want To But It's Not My Fault Public License")
                    url.set("https://github.com/fopwoc/GTNH-KNH/blob/main/LICENSE")
                }
            }
            developers {
                developer {
                    id.set("fopwoc")
                    name.set("fopwoc")
                }
            }
            scm {
                connection.set("scm:git:https://github.com/fopwoc/GTNH-KNH.git")
                developerConnection.set("scm:git:ssh://git@github.com/fopwoc/GTNH-KNH.git")
                url.set("https://github.com/fopwoc/GTNH-KNH")
            }
        }
    }

    // A deterministic repository for validating Maven publications locally and in CI. A release
    // repository can be added by CI without changing the publication or plugin-marker model.
    repositories {
        maven {
            name = "build"
            url = layout.buildDirectory.dir("repository").get().asFile.toURI()
        }
    }
}
