package io.github.fopwoc.knhmp

internal fun testSourceSetOf(mainSourceSet: String): String {
    require(mainSourceSet.endsWith("Main")) {
        "KnhMP main source set names must end with Main, got $mainSourceSet"
    }
    return mainSourceSet.removeSuffix("Main") + "Test"
}
