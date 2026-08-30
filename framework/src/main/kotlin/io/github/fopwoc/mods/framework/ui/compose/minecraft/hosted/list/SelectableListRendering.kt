package io.github.fopwoc.mods.framework.ui.compose.minecraft.hosted

import io.github.fopwoc.mods.framework.ui.compose.layout.core.ActivePointerSession
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputPressResult
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTargetKind
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import io.github.fopwoc.mods.framework.ui.compose.model.element.HostedWidgetKey

internal fun drawMinecraftHostedSelectableList(
    registry: MinecraftHostedWidgetRegistry,
    environment: MinecraftHostedWidgetRenderEnvironment,
    bounds: Rect,
    hostKey: HostedWidgetKey,
    items: List<String>,
    selectedIndex: Int,
    rowHeight: Int,
    onSelectedIndexChange: (Int) -> Unit,
) {
  if (!bounds.hasVisibleHostedBounds()) {
    return
  }

  val resolvedRowHeight = rowHeight.coerceAtLeast(12)
  val hosted =
      registry.getSelectableList(hostKey)?.takeUnless { it.slotHeight != resolvedRowHeight }
          ?: HostedSelectableList(environment.client, resolvedRowHeight).also {
            registry.putSelectableList(hostKey, it)
          }

  hosted.markSeen(environment.renderEpoch)
  hosted.update(
      bounds = bounds,
      items = items,
      selectedIndex = selectedIndex,
      onSelectedIndexChange = onSelectedIndexChange,
  )
  hosted.render(environment.mouseX, environment.mouseY)
  environment.registerInputTarget(
      InputTarget(
          kind = InputTargetKind.SELECTABLE_LIST,
          bounds = bounds,
          onPress = { clickX, clickY, button ->
            if (!hosted.handleClick(clickX, clickY)) {
              InputPressResult.Ignored
            } else {
              InputPressResult.captured(
                  ActivePointerSession(
                      button = button,
                      validityCheck = { registry.ownsSelectableList(hostKey, hosted) },
                      onDragHandler = { _, dragY -> hosted.handleDrag(dragY) },
                      onReleaseHandler = { _, _, releaseButton ->
                        hosted.handleRelease()
                        releaseButton == button
                      },
                  )
              )
            }
          },
          onWheel = { _, _, wheelDelta -> hosted.handleWheel(wheelDelta) },
      )
  )
}
