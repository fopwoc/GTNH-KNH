package io.github.fopwoc.mods.testgui.client.gui.ui

import androidx.compose.runtime.saveable.SaverScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TestGuiDestinationTest {
    @Test
    fun saverRestoresEveryTopLevelDestination() {
        val destinations = listOf(
            TestGuiDestination.Overview,
            TestGuiDestination.Controls,
            TestGuiDestination.TextAndTooltips,
            TestGuiDestination.InputsAndLists,
            TestGuiDestination.LayoutAndScroll,
            TestGuiDestination.StateLab,
            TestGuiDestination.Navigation,
            TestGuiDestination.HostedStress,
            TestGuiDestination.ScrollClipStress
        )

        destinations.forEach { destination ->
            val saved = with(testGuiDestinationSaver) { AlwaysSaveScope.save(destination) }
            val restored = saved?.let(testGuiDestinationSaver::restore)

            assertNotNull(saved)
            assertEquals(destination, restored)
            assertEquals(destination.title, restored?.title)
        }
    }

    @Test
    fun featureCatalogOmitsOverviewRoot() {
        assertEquals(testGuiDestinations.size - 1, testGuiFeatureCatalog.size)
        assertEquals(TestGuiDestination.Controls, testGuiFeatureCatalog.first().destination)
        assertEquals(false, testGuiFeatureCatalog.any { it.destination == TestGuiDestination.Overview })
    }

    private companion object {
        val AlwaysSaveScope = object : SaverScope {
            override fun canBeSaved(value: Any): Boolean = true
        }
    }
}

