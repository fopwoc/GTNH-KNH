package io.github.fopwoc.mods.testgui.client.gui.ui

data class TestGuiFeature(
    val destination: TestGuiDestination,
    val title: String = destination.title,
    val summary: String = destination.summary,
)

val testGuiFeatureCatalog: List<TestGuiFeature> =
    testGuiDestinations
        .filterNot { destination -> destination == TestGuiDestination.Overview }
        .map(::TestGuiFeature)
