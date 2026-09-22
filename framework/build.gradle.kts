import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenLocal

plugins {
    id("io.github.fopwoc.knhmp")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
}

repositories {
    google()
}

fun requiredProperty(name: String): String = property(name).toString()

val buildVersion = providers.environmentVariable("VERSION").orElse("0.1.0-SNAPSHOT")

group = requiredProperty("frameworkGroup")
version = buildVersion.get()

knhmp {
    modId = "knhcore"
    modName = "KNH Core"
    modGroup = "io.github.fopwoc.mods.framework"
    modVersion = buildVersion.get()
    javaToolchain = 26

    sourceSets {
        commonMain {
            jvmTarget = 24
        }
        gtnhMain {
            dependsOn(commonMain)
            jvmTarget = 24
        }
    }

    dependencies {
        api(libs.serialization.json)
        add("bundledLibraries", libs.serialization.json)
        api(libs.compose.runtime)
        add("bundledLibraries", libs.compose.runtime)
        api(libs.compose.runtime.saveable)
        add("bundledLibraries", libs.compose.runtime.saveable)
        api(libs.lifecycle.runtime)
        add("bundledLibraries", libs.lifecycle.runtime)
        api(libs.lifecycle.runtime.compose)
        add("bundledLibraries", libs.lifecycle.runtime.compose)
        api(libs.lifecycle.viewmodel)
        add("bundledLibraries", libs.lifecycle.viewmodel)
        api(libs.lifecycle.viewmodel.compose)
        add("bundledLibraries", libs.lifecycle.viewmodel.compose)
        api(libs.navigation3.runtime)
        add("bundledLibraries", libs.navigation3.runtime)
    }

    targets {
        gtnh {
            kotlin {
                apiVersion = libs.versions.kotlinApi.get()
            }
            plugins {
                id("com.gtnewhorizons.gtnhconvention", "2.0.29")
                alias(libs.plugins.kotlin.serialization)
                alias(libs.plugins.compose.compiler)
                alias(libs.plugins.detekt)
            }
            dependencies {
                implementation(libs.forgelin)
                add("detektPlugins", libs.compose.rules.detekt)
            }
            compilerScript("knhmp/gtnh.gradle.kts")
        }
    }
}

val runtimeJar = layout.buildDirectory.file("libs/${project.name}-gtnh-${buildVersion.get()}.jar")
val sourcesJar =
    layout.projectDirectory.file(
        ".knhmp/gtnh/build/libs/${project.name}-gtnh-${buildVersion.get()}-sources.jar"
    )

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = requiredProperty("frameworkArtifactId")
            artifact(runtimeJar)
            artifact(sourcesJar) {
                classifier = "sources"
            }
        }
    }

    repositories {
        mavenLocal()
    }
}

afterEvaluate {
    publishing.publications
        .filter { it.name != "mavenJava" }
        .forEach(publishing.publications::remove)
}

tasks.matching {
    it.name.startsWith("publishIdePublication") ||
        it.name.startsWith("publishKotlinMultiplatformPublication")
}.configureEach {
    enabled = false
    setDependsOn(emptyList<Any>())
}

tasks.matching { it.name.startsWith("generateMetadataFileFor") }.configureEach {
    dependsOn("buildAll")
}

tasks.withType<PublishToMavenLocal>().configureEach {
    dependsOn("buildAll")
}

tasks.named("publishToMavenLocal") {
    setDependsOn(listOf("publishMavenJavaPublicationToMavenLocal"))
}

tasks.register("printVersion") {
    doLast {
        println(project.version)
    }
}
