package io.github.fopwoc.mods.framework.ui.compose.component.native

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.style.ButtonStyle
import io.github.fopwoc.mods.framework.ui.compose.node.NodeApplier
import io.github.fopwoc.mods.framework.ui.compose.node.ButtonNode

@Composable
fun Button(
    text: String,
    modifier: Modifier = Modifier(),
    enabled: Boolean = true,
    style: ButtonStyle = ButtonStyle(),
    onClick: () -> Unit
) {
    ComposeNode<ButtonNode, NodeApplier>(
        factory = {
            ButtonNode(
                modifier = modifier,
                text = text,
                enabled = enabled,
                style = style,
                onClick = onClick
            )
        },
        update = {
            set(text) { this.text = it }
            set(modifier) { this.modifier = it }
            set(enabled) { this.enabled = it }
            set(style) { this.style = it }
            set(onClick) { this.onClick = it }
        }
    )
}


