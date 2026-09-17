package io.github.fopwoc.mods.palimpsest.client.gui.ui.page.benchmark

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuCanvasFrame
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuCanvasState
import io.github.fopwoc.mods.framework.ui.compose.component.Scaffold
import io.github.fopwoc.mods.framework.ui.compose.component.Section
import io.github.fopwoc.mods.framework.ui.compose.component.native.Button
import io.github.fopwoc.mods.framework.ui.compose.component.native.Slider
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.GpuCanvas
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.runtime.rememberScrollState
import io.github.fopwoc.mods.framework.ui.compose.unit.uu
import io.github.fopwoc.mods.palimpsest.benchmark.BenchmarkGenerator
import io.github.fopwoc.mods.palimpsest.benchmark.BenchmarkReadProbe
import io.github.fopwoc.mods.palimpsest.benchmark.BenchmarkTileRenderer
import io.github.fopwoc.mods.palimpsest.benchmark.CheckpointPlanner
import io.github.fopwoc.mods.palimpsest.storage.TileHistoryStore
import java.nio.file.Paths
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.minecraft.client.Minecraft

@Composable
internal fun BenchmarkView(screenWidth: Int, screenHeight: Int, onClose: () -> Unit) {
  val directory = remember {
    Paths.get(Minecraft.getMinecraft().mcDataDir.path, "config", "palimpsest", "benchmark")
  }
  val store = remember(directory) { TileHistoryStore(directory) }
  val canvas = remember { GpuCanvasState(GpuCanvasFrame(emptyList())) }
  val scope = rememberCoroutineScope()
  var latest by remember { mutableIntStateOf(store.latestEpoch.toInt()) }
  var selected by remember { mutableIntStateOf(latest) }
  var left by remember { mutableIntStateOf(0) }
  var top by remember { mutableIntStateOf(0) }
  var refresh by remember { mutableIntStateOf(0) }
  var busy by remember { mutableStateOf(false) }
  var autoCheckpoint by remember { mutableStateOf(false) }
  var readBudget by remember { mutableIntStateOf(2048) }
  var result by remember { mutableStateOf<BenchmarkTileRenderer.Result?>(null) }
  var probe by remember { mutableStateOf<BenchmarkReadProbe.Result?>(null) }
  var message by remember {
    mutableStateOf("Generate a batch to create the first 32×32 tile history.")
  }

  DisposableEffect(store) { onDispose { store.close() } }

  fun writeHistory(
      label: String,
      write: (TileHistoryStore) -> BenchmarkGenerator.Result,
  ) {
    busy = true
    scope.launch {
      try {
        val generated = withContext(Dispatchers.IO) { write(store) }
        latest = store.latestEpoch.toInt()
        selected = latest
        refresh++
        probe = null
        message =
            "$label: ${generated.layersWritten} layers, ${generated.coveredCells} covered cells, ${generated.bytesAdded / 1024} KiB in ${generated.elapsedNanos / 1_000_000} ms"
      } catch (failure: CancellationException) {
        throw failure
      } catch (failure: Exception) {
        message = "$label failed: ${failure.message}"
      } finally {
        busy = false
      }
    }
  }

  LaunchedEffect(store, selected, left, top, refresh, autoCheckpoint, readBudget) {
    try {
      val loaded =
          withContext(Dispatchers.IO) {
            BenchmarkTileRenderer.read(store, selected.toLong(), left, top)
          }
      canvas.submit(loaded.frame)
      result = loaded
      if (autoCheckpoint && selected == latest && !busy) {
        val keys = CheckpointPlanner.select(loaded.tileCosts, readBudget)
        if (keys.isNotEmpty()) {
          writeHistory("Auto checkpoint ${keys.size} tiles") {
            BenchmarkGenerator.checkpoint(it, keys)
          }
        }
      }
    } catch (failure: CancellationException) {
      throw failure
    } catch (failure: Exception) {
      message = "Read failed: ${failure.message}"
    }
  }

  Scaffold(
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      title = "Palimpsest",
      subtitle = "Layer history storage benchmark",
      onClose = onClose,
      maxWidth = 500,
      maxHeight = 430,
  ) {
    Section(title = "Synthetic tile history", modifier = Modifier.fillMaxSize()) {
      Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("8×6 viewport · 16×16 indexed pixels per tile · no world data")
        GpuCanvas(
            state = canvas,
            modifier =
                Modifier.width(BenchmarkTileRenderer.WIDTH.uu)
                    .height(BenchmarkTileRenderer.HEIGHT.uu)
                    .background(Color(0xFF11121B)),
        )
        Text("Epoch $selected / $latest · viewport ($left, $top)")
        Slider(
            value = selected.toDouble(),
            onValueChange = { selected = it.roundToInt().coerceIn(0, latest) },
            valueRange = 0.0..maxOf(1, latest).toDouble(),
            label = "Time",
            showDecimal = false,
            modifier = Modifier.fillMaxWidth(),
            enabled = latest > 0,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = HorizontalArrangement.spacedBy(3.uu),
        ) {
          Button("First", modifier = Modifier.weight(1f), enabled = latest > 0) { selected = 0 }
          Button("−1", modifier = Modifier.weight(1f), enabled = selected > 0) { selected-- }
          Button("+1", modifier = Modifier.weight(1f), enabled = selected < latest) { selected++ }
          Button("Latest", modifier = Modifier.weight(1f), enabled = selected < latest) {
            selected = latest
          }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = HorizontalArrangement.spacedBy(3.uu),
        ) {
          Button("←", modifier = Modifier.weight(1f), enabled = left > 0) { left-- }
          Button(
              "→",
              modifier = Modifier.weight(1f),
              enabled = left < BenchmarkGenerator.WORLD_SIDE - BenchmarkTileRenderer.VIEW_COLUMNS,
          ) {
            left++
          }
          Button("↑", modifier = Modifier.weight(1f), enabled = top > 0) { top-- }
          Button(
              "↓",
              modifier = Modifier.weight(1f),
              enabled = top < BenchmarkGenerator.WORLD_SIDE - BenchmarkTileRenderer.VIEW_ROWS,
          ) {
            top++
          }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = HorizontalArrangement.spacedBy(3.uu),
        ) {
          Button("Sparse +250", modifier = Modifier.weight(1f), enabled = !busy) {
            writeHistory("Sparse batch") { BenchmarkGenerator.append(it) }
          }
          Button("Mixed +250", modifier = Modifier.weight(1f), enabled = !busy) {
            writeHistory("Mixed batch") {
              BenchmarkGenerator.append(it, BenchmarkGenerator.Pattern.MIXED)
            }
          }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = HorizontalArrangement.spacedBy(3.uu),
        ) {
          Button("Stress +1000", modifier = Modifier.weight(1f), enabled = !busy) {
            writeHistory("Adversarial batch") {
              BenchmarkGenerator.append(it, BenchmarkGenerator.Pattern.ADVERSARIAL, 1000)
            }
          }
          Button(
              if (autoCheckpoint) "Auto: on" else "Auto: off",
              modifier = Modifier.weight(1f),
              enabled = !busy,
          ) {
            autoCheckpoint = !autoCheckpoint
          }
        }
        Button(
            "Probe 120 reads",
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy && latest > 0,
        ) {
          busy = true
          val epoch = selected.toLong()
          val probeLeft = left
          val probeTop = top
          scope.launch {
            try {
              probe =
                  withContext(Dispatchers.IO) {
                    BenchmarkReadProbe.run(store, epoch, probeLeft, probeTop)
                  }
            } catch (failure: CancellationException) {
              throw failure
            } catch (failure: Exception) {
              message = "Probe failed: ${failure.message}"
            } finally {
              busy = false
            }
          }
        }
        Slider(
            value = readBudget.toDouble(),
            onValueChange = { readBudget = it.roundToInt() },
            valueRange = 256.0..8192.0,
            label = "Visible layer budget",
            showDecimal = false,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = HorizontalArrangement.spacedBy(3.uu),
        ) {
          Button(
              "Checkpoint all tiles",
              modifier = Modifier.weight(1f),
              enabled = !busy && latest > 0,
          ) {
            writeHistory("Checkpoint") { BenchmarkGenerator.checkpoint(it) }
          }
          Button("Reopen index", modifier = Modifier.weight(1f), enabled = !busy) {
            busy = true
            scope.launch {
              try {
                val elapsed =
                    withContext(Dispatchers.IO) {
                      val started = System.nanoTime()
                      store.reload()
                      System.nanoTime() - started
                    }
                latest = store.latestEpoch.toInt()
                selected = selected.coerceAtMost(latest)
                refresh++
                message = "Rebuilt index from disk in ${elapsed / 1_000_000} ms"
              } catch (failure: CancellationException) {
                throw failure
              } catch (failure: Exception) {
                message = "Reopen failed: ${failure.message}"
              } finally {
                busy = false
              }
            }
          }
        }
        Text(message)
        Text(
            "${store.tileCount} tiles · ${store.layerCount} layers · ${store.byteCount / 1024} KiB sealed · ${store.indexArrayBytes / 1024} KiB index arrays"
        )
        result?.let {
          Text(
              "Historical read: ${it.elapsedNanos / 1_000} µs · ${it.visibleTiles} tiles · ${it.visitedLayers} layers visited · ${it.skippedLayers} skipped by masks · ${it.decodedLayers} decoded"
          )
        }
        probe?.let {
          Text(
              "Probe epoch ${it.epoch} at (${it.left}, ${it.top}): median ${it.medianMicros} µs · p95 ${it.p95Micros} µs · max ${it.maxMicros} µs · up to ${it.maxVisited} layers visited"
          )
        }
        Text("Files: ${directory.toAbsolutePath()}")
      }
    }
  }
}
