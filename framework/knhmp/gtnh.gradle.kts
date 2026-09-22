import java.io.File
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import com.gtnewhorizons.retrofuturagradle.mcp.ReobfuscatedJar
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.withGroovyBuilder

val moduleDirectory = checkNotNull(extensions.extraProperties["knhmpModuleDir"] as? File)

val bundledLibraries = configurations.create("bundledLibraries") {
    isCanBeConsumed = false
    isCanBeResolved = false
}

val bundledLibrariesClasspath = configurations.create("bundledLibrariesClasspath") {
    isCanBeConsumed = false
    isCanBeResolved = true
    extendsFrom(bundledLibraries)
    // Forgelin provides the stdlib and coroutines at runtime.
    exclude(group = "org.jetbrains.kotlin")
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core-jvm")
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-bom")
    exclude(group = "org.jetbrains", module = "annotations")
}

configurations.named("implementation") {
    extendsFrom(bundledLibraries)
}

// lifecycle-viewmodel-compose drags in Compose UI, which the framework never uses or ships.
configurations
    .matching {
        it.name in
            setOf(
                "compileClasspath",
                "runtimeClasspath",
                "testCompileClasspath",
                "testRuntimeClasspath",
                "bundledLibrariesClasspath",
            )
    }
    .configureEach { exclude(group = "org.jetbrains.compose.ui", module = "ui") }

extensions.getByName("composeCompiler").withGroovyBuilder {
    @Suppress("UNCHECKED_CAST")
    (getProperty("featureFlags") as SetProperty<Any>).set(emptySet())
}

fun javaStringContent(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")

val bootstrapTemplate =
    moduleDirectory.resolve(
        "src/gtnhMain/bootstrap/io/github/fopwoc/mods/framework/FrameworkBootstrap.java.in"
    )
val bootstrapOutput = layout.buildDirectory.dir("generated/sources/bootstrap/java")
val bootstrapClasses = layout.buildDirectory.dir("classes/bootstrap")
val bootstrapJava =
    bootstrapOutput.map {
        it.file("io/github/fopwoc/mods/framework/FrameworkBootstrap.java")
    }
val bootstrapTokens =
    mapOf(
        "@MOD_ID@" to javaStringContent(property("modId").toString()),
        "@MOD_NAME@" to javaStringContent(property("modName").toString()),
        "@MOD_VERSION@" to javaStringContent(property("modVersion").toString()),
    )

val generateFrameworkBootstrap =
    tasks.register("generateFrameworkBootstrap") {
        inputs.file(bootstrapTemplate)
        inputs.properties(bootstrapTokens)
        outputs.file(bootstrapJava)
        doLast {
            val source =
                bootstrapTokens.entries.fold(bootstrapTemplate.readText()) { text, (token, value) ->
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

the<SourceSetContainer>().named("main") {
    output.dir(mapOf("builtBy" to compileFrameworkBootstrap), bootstrapClasses)
}

tasks.named<Jar>("sourcesJar") {
    dependsOn(generateFrameworkBootstrap)
    from(bootstrapOutput)
}

// The dev jar stays thin: consumers compile against framework classes and get the libraries
// through the framework's api dependencies. Only the shipped jar bundles them.
val bundledJar =
    tasks.register<Jar>("bundledJar") {
        val devJar = tasks.named<Jar>("jar").flatMap { it.archiveFile }
        val bundledLibraryTrees =
            provider {
                bundledLibrariesClasspath.filter { it.name.endsWith(".jar") }.map(::zipTree)
            }

        archiveClassifier.set("bundled")
        destinationDirectory.set(layout.buildDirectory.dir("tmp/bundledJar"))
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        from(devJar.map(::zipTree))
        from(bundledLibraryTrees) {
            exclude(
                "META-INF/*.SF",
                "META-INF/*.DSA",
                "META-INF/*.RSA",
                "META-INF/MANIFEST.MF",
                "META-INF/versions/**",
                "META-INF/com.android.tools/**",
                "META-INF/proguard/**",
                "META-INF/*.kotlin_module",
                "META-INF/*.version",
            )
        }

        // Forgelin supplies these at runtime; shipping a second copy breaks the game in subtle ways.
        doLast {
            val forbidden = listOf("kotlin/", "kotlinx/coroutines/")
            val leaked =
                zipTree(archiveFile.get().asFile)
                    .matching { include(forbidden.map { "$it**" }) }
                    .files
            check(leaked.isEmpty()) {
                "knh-core jar must not bundle the Kotlin stdlib or coroutines; " +
                    "found ${leaked.size} entries, e.g. ${leaked.take(3)}"
            }
        }
    }

tasks.named<ReobfuscatedJar>("reobfJar") {
    dependsOn("sourcesJar")
    setInputJarFromTask(bundledJar)
}
