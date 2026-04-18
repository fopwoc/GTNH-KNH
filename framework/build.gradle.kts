import org.gradle.api.tasks.testing.Test
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.jvm.tasks.Jar
import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.gtnh.convention)
    alias(libs.plugins.buildconfig)
    `java-library`
    `maven-publish`
}

extra["knh.withSourcesJar"] = true
extra["knh.archiveName"] = providers.gradleProperty("frameworkArtifactId").get()

apply(from = "../gradle/gtnh-module-conventions.gradle.kts")

val bundledLibraries by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = false
}

val bundledLibrariesClasspath by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    extendsFrom(bundledLibraries)
}

configurations.named("implementation") {
    extendsFrom(bundledLibraries)
}

group = providers.gradleProperty("frameworkGroup").get()
version = providers.gradleProperty("frameworkVersion").get()

buildConfig {
    packageName(providers.gradleProperty("modGroup").get())
    className("FrameworkMetadata")
    useKotlinOutput {
        topLevelConstants = true
    }

    buildConfigField("MOD_ID", providers.gradleProperty("modId").get())
    buildConfigField("MOD_NAME", providers.gradleProperty("modName").get())
    buildConfigField("MOD_VERSION", providers.gradleProperty("modVersion").get())
}

dependencies {
    implementation(libs.forgelin)
    api(libs.serialization.json)
    bundledLibraries(libs.serialization.json)
    api(libs.compose.runtime)
    bundledLibraries(libs.compose.runtime)
    api(libs.lifecycle.viewmodel)
    bundledLibraries(libs.lifecycle.viewmodel)
    api(libs.lifecycle.viewmodel.compose) {
        exclude(group = "org.jetbrains.compose.ui", module = "ui")
    }
    bundledLibraries(libs.lifecycle.viewmodel.compose) {
        exclude(group = "org.jetbrains.compose.ui", module = "ui")
    }
    implementation(libs.coroutines.core)
    bundledLibraries(libs.coroutines.core)
    testImplementation(kotlin("test"))
}

composeCompiler {
    featureFlags.set(emptySet())
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}



tasks.named<Jar>("jar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    val bundledLibraryTrees = provider {
        bundledLibrariesClasspath
            .filter { it.name.endsWith(".jar") }
            .map(::zipTree)
    }

    from(bundledLibraryTrees) {
        exclude(*listOf(
            "META-INF/*.SF",
            "META-INF/*.DSA",
            "META-INF/*.RSA"
        ).toTypedArray())
    }
}


publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = providers.gradleProperty("frameworkArtifactId").get()
            from(components["java"])
        }
    }

    repositories {
        mavenLocal()
    }
}

