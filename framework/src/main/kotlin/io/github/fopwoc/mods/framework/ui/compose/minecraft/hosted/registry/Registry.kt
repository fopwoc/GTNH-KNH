package io.github.fopwoc.mods.framework.ui.compose.minecraft.hosted

import io.github.fopwoc.mods.framework.ui.compose.model.element.HostedWidgetKey
import java.util.IdentityHashMap

internal class MinecraftHostedWidgetRegistry {
  private val buttons = IdentityHashMap<HostedWidgetKey, HostedButton>()
  private val checkboxes = IdentityHashMap<HostedWidgetKey, HostedCheckbox>()
  private val selectableLists = IdentityHashMap<HostedWidgetKey, HostedSelectableList>()
  private val sliders = IdentityHashMap<HostedWidgetKey, HostedSlider>()

  fun clear() {
    buttons.clear()
    checkboxes.clear()
    selectableLists.clear()
    sliders.clear()
  }

  fun getOrCreateButton(hostKey: HostedWidgetKey, create: () -> HostedButton): HostedButton =
      buttons[hostKey] ?: create().also { buttons[hostKey] = it }

  fun ownsButton(hostKey: HostedWidgetKey, hosted: HostedButton): Boolean =
      buttons[hostKey] === hosted

  fun getOrCreateCheckbox(hostKey: HostedWidgetKey, create: () -> HostedCheckbox): HostedCheckbox =
      checkboxes[hostKey] ?: create().also { checkboxes[hostKey] = it }

  fun ownsCheckbox(hostKey: HostedWidgetKey, hosted: HostedCheckbox): Boolean =
      checkboxes[hostKey] === hosted

  fun getSlider(hostKey: HostedWidgetKey): HostedSlider? = sliders[hostKey]

  fun putSlider(hostKey: HostedWidgetKey, hosted: HostedSlider) {
    sliders[hostKey] = hosted
  }

  fun ownsSlider(hostKey: HostedWidgetKey, hosted: HostedSlider): Boolean =
      sliders[hostKey] === hosted

  fun getSelectableList(hostKey: HostedWidgetKey): HostedSelectableList? = selectableLists[hostKey]

  fun putSelectableList(hostKey: HostedWidgetKey, hosted: HostedSelectableList) {
    selectableLists[hostKey] = hosted
  }

  fun ownsSelectableList(hostKey: HostedWidgetKey, hosted: HostedSelectableList): Boolean =
      selectableLists[hostKey] === hosted

  fun prune(renderEpoch: Int) {
    pruneStaleWidgets(buttons, renderEpoch)
    pruneStaleWidgets(checkboxes, renderEpoch)
    pruneStaleWidgets(selectableLists, renderEpoch)
    pruneStaleWidgets(sliders, renderEpoch)
  }

  private fun <V : HostedWidget> pruneStaleWidgets(
      widgets: IdentityHashMap<HostedWidgetKey, V>,
      renderEpoch: Int,
      onRemove: (V) -> Unit = {},
  ) {
    val iterator = widgets.entries.iterator()
    while (iterator.hasNext()) {
      val entry = iterator.next()
      if (entry.value.lastSeenEpoch != renderEpoch) {
        onRemove(entry.value)
        iterator.remove()
      }
    }
  }
}
