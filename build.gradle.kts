plugins {
    base
    // One plugin classloader for every module: KnhMP resolves cross-module dependencies in memory.
    id("io.github.fopwoc.knhmp") apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    // Repo-wide formatting and analysis; the tools' versions come from the catalog.
    id("io.github.fopwoc.knhmp.quality")
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.detekt) apply false
}

knhmpQuality {
    formatting {
        ktfmt(libs.versions.ktfmt)
        palantirJavaFormat(libs.versions.palantirJavaFormat)
    }
    analysis {
        config(file("config/detekt.yml"))
        ruleSet(libs.compose.rules.detekt)
    }
    // KnhMP itself is an included build, not a module, but follows the same rules.
    extraSources("knhmp/src", "knhmp/buildSrc/src")
}
