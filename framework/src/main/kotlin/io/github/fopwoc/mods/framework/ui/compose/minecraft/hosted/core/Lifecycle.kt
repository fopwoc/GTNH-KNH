package io.github.fopwoc.mods.framework.ui.compose.minecraft.hosted

import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect

internal fun Rect.hasVisibleHostedBounds(): Boolean {
  return width > 0 && height > 0
}

internal fun HostedWidget.markSeen(renderEpoch: Int) {
  lastSeenEpoch = renderEpoch
}
