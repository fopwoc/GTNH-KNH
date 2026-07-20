package io.github.fopwoc.mods.testgui.client.gui.ui.screens.state

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.component.Panel
import io.github.fopwoc.mods.framework.ui.compose.component.SegmentedControl
import io.github.fopwoc.mods.framework.ui.compose.component.native.Button
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextStyle
import io.github.fopwoc.mods.framework.ui.compose.runtime.rememberScrollState
import io.github.fopwoc.mods.framework.ui.compose.unit.uu

@Composable
fun StateView(
    state: StateModel,
    rememberedCounter: Int,
    saveableCounter: Int,
    onIncrement: () -> Unit = {},
    onDecrement: () -> Unit = {},
    onModeSelected: (StateMode) -> Unit = {},
    onIncrementRemembered: () -> Unit = {},
    onIncrementSaveable: () -> Unit = {},
    onOpenCoverDestination: () -> Unit = {}
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = VerticalArrangement.spacedBy(8.uu)
    ) {
        Panel(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = VerticalArrangement.spacedBy(6.uu)
            ) {
                Text(
                    text = "ViewModel token: ${state.viewModelToken}",
                    style = TextStyle(color = Color.rgb(red = 0x8F, green = 0xD0, blue = 0xFF))
                )
                SegmentedControl(
                    options = StateMode.values().toList(),
                    selected = state.mode,
                    modifier = Modifier.fillMaxWidth(),
                    labelOf = StateMode::label,
                    onSelected = onModeSelected
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = HorizontalArrangement.spacedBy(4.uu),
                    verticalAlignment = VerticalAlignment.CENTER
                ) {
                    Button(
                        text = "-1",
                        modifier = Modifier.weight(1f),
                        onClick = onDecrement
                    )
                    Button(
                        text = "+1",
                        modifier = Modifier.weight(1f),
                        onClick = onIncrement
                    )
                    Button(
                        text = "Cover with Controls",
                        modifier = Modifier
                            .weight(2f)
                            .tooltip("Push another top-level destination so this route leaves composition."),
                        onClick = onOpenCoverDestination
                    )
                }
                Text(text = "Counter: ${state.counter}")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = HorizontalArrangement.spacedBy(8.uu),
            verticalAlignment = VerticalAlignment.TOP
        ) {
            Panel(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = VerticalArrangement.spacedBy(4.uu)
                ) {
                    Text(
                        text = "remember",
                        style = TextStyle(color = Color.rgb(red = 0xFF, green = 0xD5, blue = 0x4A))
                    )
                    Text(
                        text = "Drops when another destination covers this route.",
                        modifier = Modifier.fillMaxWidth(),
                        style = TextStyle(wrap = true)
                    )
                    Button(
                        text = "Bump remembered",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onIncrementRemembered
                    )
                    Text(text = "Value: $rememberedCounter")
                }
            }
            Panel(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = VerticalArrangement.spacedBy(4.uu)
                ) {
                    Text(
                        text = "rememberSaveable",
                        style = TextStyle(color = Color.rgb(red = 0xFF, green = 0xD5, blue = 0x4A))
                    )
                    Text(
                        text = "Retained because NavHost entry opts into saveable state retention.",
                        modifier = Modifier.fillMaxWidth(),
                        style = TextStyle(wrap = true)
                    )
                    Button(
                        text = "Bump saveable",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onIncrementSaveable
                    )
                    Text(text = "Value: $saveableCounter")
                }
            }
        }

        Panel(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
                verticalArrangement = VerticalArrangement.spacedBy(4.uu)
            ) {
                Text(
                    text = "Event log",
                    style = TextStyle(color = Color.rgb(red = 0xFF, green = 0xD5, blue = 0x4A))
                )
                state.eventLog.forEach { entry ->
                    Text(
                        text = "• $entry",
                        modifier = Modifier.fillMaxWidth(),
                        style = TextStyle(wrap = true)
                    )
                }
            }
        }
    }
}

