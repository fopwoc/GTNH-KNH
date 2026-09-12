package io.github.fopwoc.mods.testgui.client.gui.ui.story

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.fopwoc.mods.framework.ui.compose.component.native.Button
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.runtime.collectAsStateWithLifecycle
import io.github.fopwoc.mods.framework.ui.compose.unit.uu
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Lives in the screen's ViewModelStore: survives switching stories, dies with the screen. */
class CounterViewModel : ViewModel() {
  val count = MutableStateFlow(0)

  fun add(delta: Int) = count.update { it + delta }
}

@Composable
fun StateStory(viewModel: CounterViewModel = viewModel(CounterViewModel::class)) {
  val count by viewModel.count.collectAsStateWithLifecycle()
  var remembered by remember { mutableIntStateOf(0) }
  var saveable by rememberSaveable { mutableIntStateOf(0) }
  var ticks by remember { mutableIntStateOf(0) }
  LaunchedEffect(Unit) {
    while (true) {
      delay(500)
      ticks++
    }
  }
  Examples {
    Example(
        "ViewModel + StateFlow + collectAsStateWithLifecycle — switch stories and come back: $count"
    ) {
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = HorizontalArrangement.spacedBy(4.uu),
      ) {
        Button(text = "-1", modifier = Modifier.weight(1f)) { viewModel.add(-1) }
        Button(text = "+1", modifier = Modifier.weight(1f)) { viewModel.add(1) }
      }
    }
    Example("remember (resets when this story leaves composition): $remembered") {
      Button(text = "+1") { remembered++ }
    }
    Example("rememberSaveable (survives leaving and resizing): $saveable") {
      Button(text = "+1") { saveable++ }
    }
    Example("LaunchedEffect + delay on the screen's dispatcher") { Text("ticks $ticks") }
  }
}
