plugins {
    id("io.github.fopwoc.knhmp.library")
    application
}

dependencies {
    implementation(project(":palimpsest-db"))
    implementation(libs.clikt)
}

application {
    applicationName = "palimpsest-inspect"
    mainClass.set("io.github.fopwoc.palimpsest.inspect.MainKt")
    // Clikt's terminal detection loads JNA, which newer JDKs only allow when asked.
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}
