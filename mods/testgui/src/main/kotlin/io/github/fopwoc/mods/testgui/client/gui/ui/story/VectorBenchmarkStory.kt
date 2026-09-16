package io.github.fopwoc.mods.testgui.client.gui.ui.story

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.fopwoc.mods.framework.ui.compose.component.native.Button
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.testgui.client.benchmark.VectorBenchmark
import io.github.fopwoc.mods.testgui.client.benchmark.VectorBenchmarkResult
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun VectorBenchmarkStory() {
  val available = remember { VectorBenchmark.available() }
  var request by remember { mutableIntStateOf(0) }
  var running by remember { mutableStateOf(false) }
  var result by remember { mutableStateOf<VectorBenchmarkResult?>(null) }
  var error by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(request) {
    if (request == 0) return@LaunchedEffect
    running = true
    result = null
    error = null
    try {
      result = withContext(Dispatchers.Default) { VectorBenchmark.run() }
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (failure: Exception) {
      error = failure.message ?: failure.javaClass.simpleName
    } finally {
      running = false
    }
  }

  Examples {
    Example("Java ${System.getProperty("java.version")} · Vector API") {
      Text(if (available) "Module loaded; ready to test." else "Module is not loaded.")
      if (!available) {
        Text(
            "Add --add-modules=jdk.incubator.vector to this GTNH instance's Java arguments, then restart."
        )
      }
      Button(text = if (running) "Running…" else "Run benchmark", enabled = available && !running) {
        request++
      }
    }
    Example("1,048,576 ARGB pixels · weighted grayscale · 32 passes per sample") {
      Text("Five timed samples per path; the middle time is shown. Runs on a worker thread.")
      Text("The scalar loop uses no explicit vectors. HotSpot may still auto-vectorize it.")
      if (running) Text("Warming up and measuring…")
      error?.let { Text("Error: $it") }
      result?.let { measured ->
        Text(
            "Vector lanes: ${measured.lanes}; outputs match; checksum: ${measured.checksum.toUInt().toString(16)}"
        )
        Text("Scalar: ${formatMs(measured.scalarMedianNs)} ms")
        Text("Vector API: ${formatMs(measured.vectorMedianNs)} ms")
        Text("Vector / scalar speed: ${String.format(Locale.ROOT, "%.2f", measured.speedup)}x")
        Text(
            "This compares implementations; timing alone cannot prove which CPU instructions HotSpot used."
        )
      }
    }
  }
}

private fun formatMs(nanos: Long): String = String.format(Locale.ROOT, "%.1f", nanos / 1_000_000.0)
