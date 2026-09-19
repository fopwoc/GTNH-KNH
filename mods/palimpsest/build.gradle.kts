plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.gtnh.convention)
    alias(libs.plugins.detekt)
    alias(libs.plugins.buildconfig)
}

apply(from = "../../gradle/gtnh-module-conventions.gradle.kts")

fun requiredProperty(name: String): String = property(name).toString()

group = requiredProperty("modGroup")

buildConfig {
    packageName(group.toString())
    className("ModMetadata")
    useKotlinOutput { topLevelConstants = true }

    buildConfigField("MOD_ID", requiredProperty("modId"))
    buildConfigField("MOD_NAME", requiredProperty("modName"))
    buildConfigField("MOD_VERSION", requiredProperty("modVersion"))
    buildConfigField("CLIENT_PROXY_CLASS", "${group}.proxy.ClientProxy")
    buildConfigField("SERVER_PROXY_CLASS", "${group}.proxy.ServerProxy")
}

dependencies {
    implementation(libs.forgelin)
    implementation(
        "${requiredProperty("frameworkGroup")}:${requiredProperty("frameworkArtifactId")}:${requiredProperty("modVersion")}"
    ) {
        isTransitive = false
    }
    compileOnly(libs.compose.runtime)
    compileOnly(libs.lifecycle.runtime.compose)
    compileOnly(libs.lifecycle.viewmodel)
    compileOnly(libs.lifecycle.viewmodel.compose) {
        exclude(group = "org.jetbrains.compose.ui", module = "ui")
    }
    testImplementation(kotlin("test"))
    testImplementation(libs.compose.runtime)
    testImplementation(libs.lifecycle.viewmodel)
}

composeCompiler { featureFlags.set(emptySet()) }

// The storage workload suite is a headless tool; it is not shipped in the mod jar.
val benchmark by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().output + sourceSets.main.get().runtimeClasspath
}

sourceSets.test {
    compileClasspath += benchmark.output
    runtimeClasspath += benchmark.output
}

kotlin.target.compilations {
    getByName("benchmark").associateWith(getByName("main"))
    getByName("test").associateWith(getByName("benchmark"))
}

detekt { source.from(benchmark.kotlin.srcDirs) }

tasks.register<JavaExec>("analyzeMap") {
    group = "verification"
    description = "Prints where the bytes of a slice directory go; pass the directory with --args."
    classpath = benchmark.runtimeClasspath
    mainClass.set("io.github.fopwoc.mods.palimpsest.analyze.AnalyzeMapMainKt")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
}

tasks.register<JavaExec>("heightExperiment") {
    group = "verification"
    description = "Tries height and block coding variants over a slice directory's full tiles."
    classpath = benchmark.runtimeClasspath
    mainClass.set("io.github.fopwoc.mods.palimpsest.analyze.HeightExperimentMainKt")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
}

tasks.register<JavaExec>("storageSuite") {
    group = "verification"
    description = "Runs the isolated storage workload suite and prints its report."
    classpath = benchmark.runtimeClasspath
    mainClass.set("io.github.fopwoc.mods.palimpsest.benchmark.StorageSuiteMainKt")
    args(layout.buildDirectory.dir("palimpsest").get().asFile.absolutePath)
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
}
