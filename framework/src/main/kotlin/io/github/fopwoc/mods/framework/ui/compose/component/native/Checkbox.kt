package io.github.fopwoc.mods.framework.ui.compose.component.native

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.node.CheckboxNode
import io.github.fopwoc.mods.framework.ui.compose.node.NodeApplier
import io.github.fopwoc.mods.framework.ui.compose.text.StyledText

@Composable
fun Checkbox(
    label: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
  Checkbox(
      label = StyledText.of(label),
      checked = checked,
      modifier = modifier,
      enabled = enabled,
      onCheckedChange = onCheckedChange,
  )
}

@Composable
fun Checkbox(
    label: StyledText,
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
  ComposeNode<CheckboxNode, NodeApplier>(
      factory = {
        CheckboxNode(
            modifier = modifier,
            label = label,
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
      },
      update = {
        set(label) { this.label = it }
        set(checked) { this.checked = it }
        set(modifier) { this.modifier = it }
        set(enabled) { this.enabled = it }
        set(onCheckedChange) { this.onCheckedChange = it }
      },
  )
}
