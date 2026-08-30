package io.github.fopwoc.mods.framework.ui.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextStyle
import io.github.fopwoc.mods.framework.ui.compose.node.NodeApplier
import io.github.fopwoc.mods.framework.ui.compose.node.TextNode
import io.github.fopwoc.mods.framework.ui.compose.text.StyledText

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle(),
) {
  Text(
      text = StyledText.of(text),
      modifier = modifier,
      style = style,
  )
}

@Composable
fun Text(
    text: StyledText,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle(),
) {
  ComposeNode<TextNode, NodeApplier>(
      factory = { TextNode(modifier = modifier, text = text, style = style) },
      update = {
        set(text) { this.text = it }
        set(modifier) { this.modifier = it }
        set(style) { this.style = it }
      },
  )
}
