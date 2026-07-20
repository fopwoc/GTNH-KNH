package io.github.fopwoc.mods.testgui.client.gui.ui.screens.overview

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.component.native.Button
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.runtime.rememberScrollState
import io.github.fopwoc.mods.framework.ui.compose.unit.uu
import io.github.fopwoc.mods.testgui.client.gui.ui.TestGuiFeature
import io.github.fopwoc.mods.testgui.client.gui.ui.chrome.AccentText
import io.github.fopwoc.mods.testgui.client.gui.ui.chrome.BodyText
import io.github.fopwoc.mods.testgui.client.gui.ui.chrome.MutedText
import io.github.fopwoc.mods.testgui.client.gui.ui.chrome.SectionBlock

@Composable
fun OverviewView(
    state: OverviewModel,
    onOpenFeature: (TestGuiFeature) -> Unit = {}
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = VerticalArrangement.spacedBy(8.uu)
    ) {
        SectionBlock(title = "Screen list", modifier = Modifier.fillMaxWidth()) {
            BodyText("This demo app opens each framework area as its own full-screen route. The list stays at the root so Back always returns here.")
            AccentText("Opened from menu: ${state.openCount} · Last opened: ${state.lastOpenedTitle}")
        }

        state.features.forEach { feature ->
            SectionBlock(
                title = feature.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .tooltip(feature.summary),
                elevated = true
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = HorizontalArrangement.spacedBy(6.uu),
                    verticalAlignment = VerticalAlignment.CENTER
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = VerticalArrangement.spacedBy(3.uu)
                    ) {
                        MutedText(text = feature.summary, modifier = Modifier.fillMaxWidth(), wrap = true)
                    }
                    Button(
                        text = "Open",
                        modifier = Modifier.tooltip("Open ${feature.title} full-screen and return here with Back."),
                        onClick = {
                            onOpenFeature(feature)
                        }
                    )
                }
                if (feature == state.features.last()) {
                    BodyText("The newest stress routes are meant for intentionally uncomfortable UI conditions: dense hosted widgets, focus churn, long scroll chains, and offset badge pressure.")
                } else {
                    BodyText("Open this route to validate one framework surface in isolation, then back out to the menu list.")
                }
            }
        }
    }
}

