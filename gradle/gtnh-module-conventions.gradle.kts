import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.withGroovyBuilder
import org.gradle.language.jvm.tasks.ProcessResources
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun version(name: String): String = libs.findVersion(name).get().requiredVersion
fun requiredProperty(name: String): String = property(name).toString()

val javaVersion = version("java").toInt()
val jvmBytecodeVersion = version("jvmBytecode")
val jvmTargetName = if (jvmBytecodeVersion == "8") "1.8" else jvmBytecodeVersion
val kotlinStdlibVersion = version("kotlinStdlib")
val kotlinApiVersion = KotlinVersion.fromVersion(version("kotlinApi"))

val mcmodProperties = mapOf(
    "modId" to requiredProperty("modId"),
    "modName" to requiredProperty("modName"),
    "minecraftVersion" to requiredProperty("minecraftVersion"),
    "modVersion" to requiredProperty("modVersion")
)

val mcmodInfoTemplate = layout.projectDirectory.file("src/main/resources/mcmod.info")

repositories {
    mavenLocal()
    maven("https://nexus.gtnewhorizons.com/repository/public/")
    maven("https://nexus.gtnewhorizons.com/repository/releases/")
    maven("https://nexus.gtnewhorizons.com/repository/central-sonatype-snapshots/")
    mavenCentral()
    google()
}

extensions.configure<JavaPluginExtension>("java") {
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaVersion)
    }

    sourceCompatibility = JavaVersion.toVersion(jvmBytecodeVersion.toInt())
    targetCompatibility = JavaVersion.toVersion(jvmBytecodeVersion.toInt())

    if ((extra.properties["knh.withSourcesJar"] as? Boolean) == true) {
        withSourcesJar()
    }
}

extensions.configure<BasePluginExtension>("base") {
    archivesName.set(
        (extra.properties["knh.archiveName"] as? String) ?: project.name
    )
}

configure<KotlinJvmProjectExtension> {
    jvmToolchain(javaVersion)
}

extensions.getByName("spotless").withGroovyBuilder {
    "kotlin" {
        "clearSteps"()
        "toggleOffOn"()
        // GTNHGradle 2.0.29 still pins ktfmt 0.39, which cannot run on Java 25.
        "ktfmt"("0.63")
        "trimTrailingWhitespace"()
        "leadingTabsToSpaces"(4)
        "endWithNewline"()
    }
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(jvmTargetName))
        // Forgelin provides the Kotlin stdlib at runtime; refuse newer stdlib API at compile time.
        apiVersion.set(kotlinApiVersion)
    }
}

// Every mod jar runs against the stdlib embedded in Forgelin, so compile against that exact version
// instead of whatever transitive dependency asks for the newest one.
configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin" && requested.name.startsWith("kotlin-stdlib")) {
            useVersion(kotlinStdlibVersion)
            because("Forgelin embeds kotlin-stdlib $kotlinStdlibVersion")
        }
    }
}


// Forge/FML classes touched by unit tests initialise log4j and write logs/ next to the working
// directory; keep that inside the build directory.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    val testWorkDir = layout.buildDirectory.dir("test-work")
    doFirst { testWorkDir.get().asFile.mkdirs() }
    workingDir = testWorkDir.get().asFile
}

tasks.named<JavaCompile>("compileJava") {
    options.release.set(jvmBytecodeVersion.toInt())
}

tasks.named<JavaCompile>("compileTestJava") {
    options.release.set(jvmBytecodeVersion.toInt())
}

tasks.named<ProcessResources>("processResources") {
    inputs.properties(mcmodProperties)
    inputs.file(mcmodInfoTemplate)

    doLast {
        val rendered = mcmodProperties.entries.fold(mcmodInfoTemplate.asFile.readText()) { content, (key, value) ->
            content.replace("\${$key}", value)
        }

        destinationDir.resolve("mcmod.info").writeText(rendered)
    }
}

listOf(
    "compileInjectedTagsKotlin" to "injectTags",
    "compilePatchedMcKotlin" to "decompressDecompiledSources",
    "compileMcLauncherKotlin" to "createMcLauncherFiles",
    "compileMcLauncherJava" to "createMcLauncherFiles"
).forEach { (taskName, dependencyName) ->
    tasks.named(taskName) {
        dependsOn(tasks.named(dependencyName))
    }
}
