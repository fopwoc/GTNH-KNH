import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.gtnh.convention)
    alias(libs.plugins.detekt)
    alias(libs.plugins.buildconfig)
    `java-library`
    `maven-publish`
}

fun requiredProperty(name: String): String = property(name).toString()

extra["knh.withSourcesJar"] = true

extra["knh.archiveName"] = requiredProperty("frameworkArtifactId")

apply(from = "../gradle/gtnh-module-conventions.gradle.kts")

val bundledLibraries by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = false
}

// Libraries merged into the runtime jar. Kotlin stdlib and coroutines are provided by Forgelin and
// must never be duplicated on the Forge classpath.
val bundledLibrariesClasspath by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    extendsFrom(bundledLibraries)
    exclude(group = "org.jetbrains.kotlin")
    exclude(group = "org.jetbrains", module = "annotations")
}

configurations.named("implementation") {
    extendsFrom(bundledLibraries)
}

group = requiredProperty("frameworkGroup")

buildConfig {
    packageName(requiredProperty("modGroup"))
    className("FrameworkMetadata")
    useKotlinOutput {
        topLevelConstants = true
    }

    buildConfigField("MOD_ID", requiredProperty("modId"))
    buildConfigField("MOD_NAME", requiredProperty("modName"))
    buildConfigField("MOD_VERSION", requiredProperty("modVersion"))
    buildConfigField("EXPECTED_KOTLIN_STDLIB_VERSION", libs.versions.kotlinStdlib.get())
}

fun javaStringContent(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")

val bootstrapTemplate =
    layout.projectDirectory.file(
        "src/main/bootstrap/io/github/fopwoc/mods/framework/FrameworkBootstrap.java.in"
    )
val bootstrapOutput = layout.buildDirectory.dir("generated/sources/bootstrap/java")
val bootstrapClasses = layout.buildDirectory.dir("classes/bootstrap")
val bootstrapJava = bootstrapOutput.map {
    it.file("io/github/fopwoc/mods/framework/FrameworkBootstrap.java")
}
val bootstrapTokens =
    mapOf(
        "@MOD_ID@" to javaStringContent(requiredProperty("modId")),
        "@MOD_NAME@" to javaStringContent(requiredProperty("modName")),
        "@MOD_VERSION@" to javaStringContent(requiredProperty("modVersion")),
    )

val generateFrameworkBootstrap =
    tasks.register("generateFrameworkBootstrap") {
        inputs.file(bootstrapTemplate)
        inputs.properties(bootstrapTokens)
        outputs.file(bootstrapJava)
        doLast {
            val source =
                bootstrapTokens.entries.fold(bootstrapTemplate.asFile.readText()) {
                    text,
                    (token, value) ->
                    text.replace(token, value)
                }
            bootstrapJava.get().asFile.apply {
                parentFile.mkdirs()
                writeText(source)
            }
        }
    }

val compileFrameworkBootstrap =
    tasks.register<JavaCompile>("compileFrameworkBootstrap") {
        dependsOn(generateFrameworkBootstrap)
        source(bootstrapJava)
        classpath = tasks.named<JavaCompile>("compileJava").get().classpath
        destinationDirectory.set(bootstrapClasses)
        options.release.set(8)
        options.compilerArgs.add("-Xlint:-options")
    }

sourceSets.named("main") {
    output.dir(mapOf("builtBy" to compileFrameworkBootstrap), bootstrapClasses)
}

tasks.named<Jar>("sourcesJar") {
    dependsOn(generateFrameworkBootstrap)
    from(bootstrapOutput)
}

dependencies {
    implementation(libs.forgelin)
    api(libs.serialization.json)
    bundledLibraries(libs.serialization.json)
    api(libs.compose.runtime)
    bundledLibraries(libs.compose.runtime)
    api(libs.compose.runtime.saveable)
    bundledLibraries(libs.compose.runtime.saveable)
    api(libs.lifecycle.runtime)
    bundledLibraries(libs.lifecycle.runtime)
    api(libs.lifecycle.runtime.compose)
    bundledLibraries(libs.lifecycle.runtime.compose)
    api(libs.lifecycle.viewmodel)
    bundledLibraries(libs.lifecycle.viewmodel)
    api(libs.lifecycle.viewmodel.compose) {
        exclude(group = "org.jetbrains.compose.ui", module = "ui")
    }
    bundledLibraries(libs.lifecycle.viewmodel.compose) {
        exclude(group = "org.jetbrains.compose.ui", module = "ui")
    }
    api(libs.navigation3.runtime)
    bundledLibraries(libs.navigation3.runtime)
    detektPlugins(libs.compose.rules.detekt)
    testImplementation(kotlin("test"))
}

detekt {
    config.from(file("detekt.yml"))
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
        bundledLibrariesClasspath.filter { it.name.endsWith(".jar") }.map(::zipTree)
    }

    // Forgelin supplies these at runtime; shipping a second copy breaks the game in subtle ways.
    doLast {
        val forbidden = listOf("kotlin/", "kotlinx/coroutines/")
        val leaked =
            zipTree(archiveFile.get().asFile).matching { include(forbidden.map { "$it**" }) }.files
        check(leaked.isEmpty()) {
            "knh-core jar must not bundle the Kotlin stdlib or coroutines; found ${leaked.size} entries, e.g. ${leaked.take(3)}"
        }
    }

    from(bundledLibraryTrees) {
        exclude(
            "META-INF/*.SF",
            "META-INF/*.DSA",
            "META-INF/*.RSA",
            "META-INF/versions/**",
            "META-INF/com.android.tools/**",
            "META-INF/proguard/**",
            "META-INF/*.kotlin_module",
            "META-INF/*.version",
        )
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = requiredProperty("frameworkArtifactId")
            from(components["java"])
        }
    }

    repositories {
        mavenLocal()
    }
}
