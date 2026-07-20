package io.github.fopwoc.mods.testgui.client.gui.ui.screens.navigation

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
import io.github.fopwoc.mods.testgui.client.gui.ui.chrome.AccentText
import io.github.fopwoc.mods.testgui.client.gui.ui.chrome.BodyText
import io.github.fopwoc.mods.testgui.client.gui.ui.chrome.SectionBlock

@Composable
fun NavigationView(
    state: NavigationModel,
    innerCurrentLabel: String,
    onPushSelf: () -> Unit = {},
    onReplaceTop: () -> Unit = {},
    onPopToRoot: () -> Unit = {},
    innerHost: @Composable () -> Unit
) {
    val outerScrollState = rememberScrollState()
    val innerScrollState = rememberScrollState()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = VerticalArrangement.spacedBy(8.uu)
    ) {
        SectionBlock(title = "Outer stack actions", modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = HorizontalArrangement.spacedBy(4.uu),
                verticalAlignment = VerticalAlignment.CENTER
            ) {
                Button(
                    text = "Push Navigation",
                    modifier = Modifier.weight(1f),
                    onClick = onPushSelf
                )
                Button(
                    text = "Replace with Overview",
                    modifier = Modifier.weight(1f),
                    onClick = onReplaceTop
                )
                Button(
                    text = "Pop to root",
                    modifier = Modifier.weight(1f),
                    onClick = onPopToRoot
                )
            }
            AccentText(
                text = "Self pushes: ${state.selfPushes} · Inner current: $innerCurrentLabel",
                modifier = Modifier.fillMaxWidth()
            )
        }

        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = HorizontalArrangement.spacedBy(8.uu),
            verticalAlignment = VerticalAlignment.TOP
        ) {
            SectionBlock(title = "Outer log", modifier = Modifier.weight(1f).fillMaxHeight(), elevated = true) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(outerScrollState),
                    verticalArrangement = VerticalArrangement.spacedBy(4.uu)
                ) {
                    state.outerEvents.forEach { entry ->
                        BodyText(text = "• $entry", modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            Column(
                modifier = Modifier.weight(2f).fillMaxHeight(),
                verticalArrangement = VerticalArrangement.spacedBy(8.uu)
            ) {
                innerHost()
                SectionBlock(title = "Inner log", modifier = Modifier.weight(1f).fillMaxWidth(), elevated = true) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(innerScrollState),
                        verticalArrangement = VerticalArrangement.spacedBy(4.uu)
                    ) {
                        state.innerEvents.forEach { entry ->
                            BodyText(text = "• $entry", modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

