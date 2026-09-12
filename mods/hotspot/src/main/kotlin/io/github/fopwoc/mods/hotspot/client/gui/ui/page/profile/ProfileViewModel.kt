package io.github.fopwoc.mods.hotspot.client.gui.ui.page.profile

import androidx.lifecycle.ViewModel
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.hotspot.client.profile.ProfileStore
import io.github.fopwoc.mods.hotspot.config.HotspotConfig
import kotlinx.coroutines.flow.MutableStateFlow
import net.minecraft.client.Minecraft

@SideOnly(Side.CLIENT)
class ProfileViewModel : ViewModel() {
  private var selectedDimensionId: Int? = null
  private var durationSeconds = HotspotConfig.defaultDurationSeconds

  val stateFlow = MutableStateFlow(read())

  init {
    ProfileStore.checkAccess()
  }

  fun refreshFromRuntime() {
    if (selectedDimensionId == null) {
      // First look defaults to where the player stands, once a snapshot exists for it.
      val playerDimension = Minecraft.getMinecraft().theWorld?.provider?.dimensionId
      if (playerDimension != null && ProfileStore.snapshot?.dimension(playerDimension) != null) {
        selectedDimensionId = playerDimension
      }
    }
    stateFlow.value = read()
  }

  fun profile() {
    ProfileStore.requestProfile(stateFlow.value.durationSeconds * 20)
    refreshFromRuntime()
  }

  fun setDuration(seconds: Int) {
    durationSeconds = seconds.coerceIn(1, 60)
    refreshFromRuntime()
  }

  fun cycleDimension(step: Int) {
    val dimensions = ProfileStore.snapshot?.dimensions.orEmpty()
    if (dimensions.isEmpty()) return
    val currentIndex = dimensions.indexOfFirst { it.id == selectedDimensionId }.coerceAtLeast(0)
    selectedDimensionId = dimensions[Math.floorMod(currentIndex + step, dimensions.size)].id
    refreshFromRuntime()
  }

  fun focusChunk(index: Int) {
    ProfileStore.focusChunk(stateFlow.value.chunks.getOrNull(index)?.ref)
    refreshFromRuntime()
  }

  fun selectTileEntities(indices: Set<Int>) {
    val chunk = ProfileStore.focusedChunk ?: return
    val rows = stateFlow.value.tileEntities
    ProfileStore.setSelectedInChunk(chunk, indices.mapNotNull { rows.getOrNull(it)?.ref })
    refreshFromRuntime()
  }

  fun clear() {
    ProfileStore.clearSelection()
    refreshFromRuntime()
  }

  private fun read(): ProfileModel =
      ProfileRuntimeSnapshot.read(selectedDimensionId, durationSeconds)
}
