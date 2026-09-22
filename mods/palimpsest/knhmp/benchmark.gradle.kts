import java.io.File
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.withGroovyBuilder
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

val moduleDirectory = checkNotNull(extensions.extraProperties["knhmpModuleDir"] as? File)
val sourceSets = the<SourceSetContainer>()
val benchmark = sourceSets.create("benchmark") {
    java.setSrcDirs(emptyList<File>())
    resources.setSrcDirs(emptyList<File>())
    compileClasspath += sourceSets.getByName("main").output + sourceSets.getByName("main").compileClasspath
    runtimeClasspath += sourceSets.getByName("main").output + sourceSets.getByName("main").runtimeClasspath
}

extensions.getByType<KotlinJvmProjectExtension>().sourceSets.named("benchmark") {
    kotlin.setSrcDirs(listOf(moduleDirectory.resolve("src/benchmark/kotlin")))
}

extensions.getByName("detekt").withGroovyBuilder {
    (getProperty("source") as ConfigurableFileCollection).from(benchmark.allSource.srcDirs)
}

fun registerBenchmarkTask(name: String, mainClassName: String, descriptionText: String? = null) {
    val javaExtension = extensions.getByType<JavaPluginExtension>()
    val toolchains = extensions.getByType<JavaToolchainService>()
    tasks.register<JavaExec>(name) {
        group = "verification"
        descriptionText?.let { description = it }
        classpath = benchmark.runtimeClasspath
        mainClass.set(mainClassName)
        javaLauncher.set(toolchains.launcherFor(javaExtension.toolchain))
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
