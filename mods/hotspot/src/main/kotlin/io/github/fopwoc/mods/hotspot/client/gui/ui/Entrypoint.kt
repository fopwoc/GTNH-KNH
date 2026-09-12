package io.github.fopwoc.mods.hotspot.client.gui.ui

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.hotspot.client.gui.ui.page.profile.ProfileRoute

@Composable
fun Entrypoint(
    screenWidth: Int,
    screenHeight: Int,
    refreshToken: Int,
    onClose: () -> Unit,
) {
  ProfileRoute(
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      refreshToken = refreshToken,
      onClose = onClose,
  )
}
