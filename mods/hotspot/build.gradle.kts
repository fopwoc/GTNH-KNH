import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.gtnh.convention)
    alias(libs.plugins.buildconfig)
}

apply(from = "../../gradle/gtnh-module-conventions.gradle.kts")

fun requiredProperty(name: String): String = property(name).toString()

group = requiredProperty("modGroup")

buildConfig {
    packageName(group.toString())
    className("ModMetadata")
    useKotlinOutput {
        topLevelConstants = true
    }

    buildConfigField("MOD_ID", requiredProperty("modId"))
    buildConfigField("MOD_NAME", requiredProperty("modName"))
    buildConfigField("MOD_VERSION", requiredProperty("modVersion"))
    buildConfigField("CLIENT_PROXY_CLASS", "${group}.proxy.ClientProxy")
    buildConfigField("SERVER_PROXY_CLASS", "${group}.proxy.ServerProxy")
    buildConfigField("GUI_FACTORY_CLASS", "${group}.config.gui.HotspotGuiFactory")
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
    compileOnly(variantOf(libs.opis) { classifier("dev") })
    compileOnly(libs.serialization.json)
    testImplementation(kotlin("test"))
    testImplementation(libs.compose.runtime)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

composeCompiler {
    featureFlags.set(emptySet())
}
