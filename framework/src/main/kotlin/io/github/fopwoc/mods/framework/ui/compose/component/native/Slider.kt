package io.github.fopwoc.mods.framework.ui.compose.component.native

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.node.NodeApplier
import io.github.fopwoc.mods.framework.ui.compose.node.SliderNode

@Composable
fun Slider(
    value: Double,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Double> = 0.0..1.0,
    label: String = "",
    suffix: String = "",
    enabled: Boolean = true,
    showDecimal: Boolean = true
) {
    ComposeNode<SliderNode, NodeApplier>(
        factory = {
            SliderNode(
                modifier = modifier,
                value = value,
                valueRangeStart = valueRange.start,
                valueRangeEnd = valueRange.endInclusive,
                label = label,
                suffix = suffix,
                enabled = enabled,
                showDecimal = showDecimal,
                onValueChange = onValueChange
            )
        },
        update = {
            set(value) { this.value = it }
            set(modifier) { this.modifier = it }
            set(valueRange.start) { this.valueRangeStart = it }
            set(valueRange.endInclusive) { this.valueRangeEnd = it }
            set(label) { this.label = it }
            set(suffix) { this.suffix = it }
            set(enabled) { this.enabled = it }
            set(showDecimal) { this.showDecimal = it }
            set(onValueChange) { this.onValueChange = it }
        }
    )
}


