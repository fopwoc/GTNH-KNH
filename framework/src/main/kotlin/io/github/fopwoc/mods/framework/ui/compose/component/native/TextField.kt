package io.github.fopwoc.mods.framework.ui.compose.component.native

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextFieldStyle
import io.github.fopwoc.mods.framework.ui.compose.node.NodeApplier
import io.github.fopwoc.mods.framework.ui.compose.node.TextFieldNode
import io.github.fopwoc.mods.framework.ui.compose.state.TextFieldState

@Composable
fun TextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    style: TextFieldStyle = TextFieldStyle()
) {
    ComposeNode<TextFieldNode, NodeApplier>(
        factory = {
            TextFieldNode(
                modifier = modifier,
                state = state,
                placeholder = placeholder,
                enabled = enabled,
                style = style
            )
        },
        update = {
            set(state) { this.state = it }
            set(modifier) { this.modifier = it }
            set(placeholder) { this.placeholder = it }
            set(enabled) { this.enabled = it }
            set(style) { this.style = it }
        }
    )
}


