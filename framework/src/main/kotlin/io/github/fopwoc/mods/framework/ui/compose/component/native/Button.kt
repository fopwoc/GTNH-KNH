package io.github.fopwoc.mods.framework.ui.compose.component.native

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.node.ButtonNode
import io.github.fopwoc.mods.framework.ui.compose.node.NodeApplier
import io.github.fopwoc.mods.framework.ui.compose.text.StyledText

@Composable
fun Button(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
  Button(
      text = StyledText.of(text),
      modifier = modifier,
      enabled = enabled,
      onClick = onClick,
  )
}

@Composable
fun Button(
    text: StyledText,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
  ComposeNode<ButtonNode, NodeApplier>(
      factory = {
        ButtonNode(
            modifier = modifier,
            text = text,
            enabled = enabled,
            onClick = onClick,
        )
      },
      update = {
        set(text) { this.text = it }
        set(modifier) { this.modifier = it }
        set(enabled) { this.enabled = it }
        set(onClick) { this.onClick = it }
      },
  )
}
