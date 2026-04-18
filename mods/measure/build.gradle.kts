plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.gtnh.convention)
    alias(libs.plugins.buildconfig)
}

apply(from = "../../gradle/gtnh-module-conventions.gradle.kts")

group = providers.gradleProperty("modGroup").get()
version = providers.gradleProperty("modVersion").get()

buildConfig {
    packageName(group.toString())
    className("ModMetadata")
    useKotlinOutput {
        topLevelConstants = true
    }

    buildConfigField("MOD_ID", providers.gradleProperty("modId").get())
    buildConfigField("MOD_NAME", providers.gradleProperty("modName").get())
    buildConfigField("MOD_VERSION", providers.gradleProperty("modVersion").get())
    buildConfigField("CLIENT_PROXY_CLASS", "${group}.proxy.ClientProxy")
    buildConfigField("SERVER_PROXY_CLASS", "${group}.proxy.ServerProxy")
}

dependencies {
    implementation(libs.forgelin)
    implementation(
        "${providers.gradleProperty("frameworkGroup").get()}:${providers.gradleProperty("frameworkArtifactId").get()}:${providers.gradleProperty("frameworkVersion").get()}"
    ) {
        isTransitive = false
    }
    compileOnly(libs.compose.runtime)
    compileOnly(libs.serialization.json)
}

composeCompiler {
    featureFlags.set(emptySet())
}



