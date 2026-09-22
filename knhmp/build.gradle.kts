import org.gradle.api.publish.maven.MavenPublication
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.20"
    `java-gradle-plugin`
    `maven-publish`
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
    plugins {
        create("knhMp") {
            id = "io.github.fopwoc.knhmp"
            implementationClass = "io.github.fopwoc.knhmp.KnhMpPlugin"
            displayName = "KnhMP"
            description = project.description
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
