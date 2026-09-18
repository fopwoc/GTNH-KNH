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
import io.github.fopwoc.mods.palimpsest.benchmark.BenchmarkDiagnostics
import io.github.fopwoc.mods.palimpsest.benchmark.BenchmarkGenerator
import io.github.fopwoc.mods.palimpsest.benchmark.BenchmarkPageRenderer
import io.github.fopwoc.mods.palimpsest.benchmark.BenchmarkReadProbe
import io.github.fopwoc.mods.palimpsest.benchmark.BenchmarkStorageSuite
import io.github.fopwoc.mods.palimpsest.benchmark.BenchmarkTileRenderer
import io.github.fopwoc.mods.palimpsest.benchmark.CheckpointPlanner
import io.github.fopwoc.mods.palimpsest.map.MapCamera
import io.github.fopwoc.mods.palimpsest.map.MapPageCache
import io.github.fopwoc.mods.palimpsest.map.MapPageKey
import io.github.fopwoc.mods.palimpsest.storage.TileHistoryStore
import io.github.fopwoc.mods.palimpsest.storage.TileKey
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.minecraft.client.Minecraft

@Composable
internal fun BenchmarkView(screenWidth: Int, screenHeight: Int, onClose: () -> Unit) {
    val directory = remember {
        Paths.get(Minecraft.getMinecraft().mcDataDir.path, "config", "palimpsest", "benchmark")
    }
    val store = remember(directory) { TileHistoryStore(directory) }
    val pageCache =
        remember(store) {
            MapPageCache(
                { key, epoch -> store.read(key, epoch)?.colors },
                BenchmarkTileRenderer.palette,
                hasChanged = { key, from, to -> store.hasChanges(key, from, to) },
                readSamples = { key, epoch, positions ->
                    store.readSamples(key, epoch, positions)?.colors
                },
            )
        }
    val canvas = remember { GpuCanvasState(GpuCanvasFrame(emptyList())) }
    val scope = rememberCoroutineScope()
    val stopSuite = remember { AtomicBoolean(false) }
    var latest by remember { mutableIntStateOf(store.latestEpoch.toInt()) }
    var selected by remember { mutableIntStateOf(latest) }
    var left by remember { mutableIntStateOf(0) }
    var top by remember { mutableIntStateOf(0) }
    var pageMode by remember { mutableStateOf(false) }
    var zoom by remember { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var suiteRunning by remember { mutableStateOf(false) }
    var suiteStopping by remember { mutableStateOf(false) }
    var autoCheckpoint by remember { mutableStateOf(false) }
    var readBudget by remember { mutableIntStateOf(2048) }
    var result by remember { mutableStateOf<BenchmarkTileRenderer.Result?>(null) }
    var pageResult by remember { mutableStateOf<BenchmarkPageRenderer.Result?>(null) }
    var probe by remember { mutableStateOf<BenchmarkReadProbe.Result?>(null) }
    var diagnosticPath by remember { mutableStateOf<String?>(null) }
    var message by remember {
        mutableStateOf("Generate a batch to create the first 32×32 tile history.")
    }

    DisposableEffect(store) {
        onDispose {
            stopSuite.set(true)
            store.close()
        }
    }

    fun writeHistory(
        label: String,
        write: (TileHistoryStore) -> BenchmarkGenerator.Result,
    ) {
        busy = true
        scope.launch {
            try {
                val generated = withContext(Dispatchers.IO) { write(store) }
                if (generated.layersWritten > 0) {
                    pageCache.invalidate(
                        buildList {
                            for (z in 0 until BenchmarkGenerator.WORLD_SIDE) for (x in
                                0 until BenchmarkGenerator.WORLD_SIDE) add(TileKey(x, z))
                        }
                    )
                }
                latest = store.latestEpoch.toInt()
                selected = latest
                refresh++
                probe = null
                message =
                    "$label: ${generated.layersWritten} layers written, ${generated.layersDiscarded} unchanged discarded, ${generated.coveredCells} covered cells, ${generated.bytesAdded / 1024} KiB in ${generated.elapsedNanos / 1_000_000} ms"
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                message = "$label failed: ${failure.message}"
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(
        store,
        selected,
        left,
        top,
        refresh,
        autoCheckpoint,
        readBudget,
        pageMode,
        zoom,
    ) {
        try {
            if (pageMode) {
                val scale = 2.0 / (1 shl zoom)
                val camera =
                    MapCamera(
                        left * 16.0 + BenchmarkTileRenderer.WIDTH / (2.0 * scale),
                        top * 16.0 + BenchmarkTileRenderer.HEIGHT / (2.0 * scale),
                        scale,
                        BenchmarkTileRenderer.WIDTH,
                        BenchmarkTileRenderer.HEIGHT,
                    )
                val loaded =
                    withContext(Dispatchers.IO) {
                        val job = coroutineContext[Job]
                        BenchmarkPageRenderer.read(
                            pageCache,
                            camera,
                            selected.toLong(),
                            selected == latest,
                        ) {
                            if (job?.isActive == false)
                                throw CancellationException("Map read superseded")
                        }
                    }
                canvas.submit(loaded.frame)
                pageResult = loaded
                result = null
            } else {
                val loaded =
                    withContext(Dispatchers.IO) {
                        BenchmarkTileRenderer.read(store, selected.toLong(), left, top)
                    }
                canvas.submit(loaded.frame)
                result = loaded
                pageResult = null
                if (autoCheckpoint && selected == latest && !busy) {
                    val keys = CheckpointPlanner.select(loaded.tileCosts, readBudget)
                    if (keys.isNotEmpty()) {
                        writeHistory("Auto checkpoint ${keys.size} tiles") {
                            BenchmarkGenerator.checkpoint(it, keys)
                        }
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
                Text("Synthetic 16×16 indexed tiles · no world data")
                GpuCanvas(
                    state = canvas,
                    modifier =
                        Modifier.width(BenchmarkTileRenderer.WIDTH.uu)
                            .height(BenchmarkTileRenderer.HEIGHT.uu)
                            .background(Color(0xFF11121B)),
                )
                Text(
                    "Epoch $selected / $latest · viewport ($left, $top) · ${if (pageMode) "pages LOD ${maxOf(0, zoom - 1)}" else "tiles"}"
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = HorizontalArrangement.spacedBy(3.uu),
                ) {
                    Button(
                        if (pageMode) "Paged view: on" else "Paged view: off",
                        modifier = Modifier.weight(2f),
                    ) {
                        pageMode = !pageMode
                    }
                    Button(
                        "Zoom −",
                        modifier = Modifier.weight(1f),
                        enabled = pageMode && zoom <= MapPageKey.MAX_LOD,
                    ) {
                        zoom++
                    }
                    Button(
                        "Zoom +",
                        modifier = Modifier.weight(1f),
                        enabled = pageMode && zoom > 0,
                    ) {
                        zoom--
                    }
                }
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
                    Button("First", modifier = Modifier.weight(1f), enabled = latest > 0) {
                        selected = 0
                    }
                    Button("−1", modifier = Modifier.weight(1f), enabled = selected > 0) {
                        selected--
                    }
                    Button("+1", modifier = Modifier.weight(1f), enabled = selected < latest) {
                        selected++
                    }
                    Button("Latest", modifier = Modifier.weight(1f), enabled = selected < latest) {
                        selected = latest
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = HorizontalArrangement.spacedBy(3.uu),
                ) {
                    Button("←", modifier = Modifier.weight(1f), enabled = pageMode || left > 0) {
                        left -= if (pageMode) 1 shl zoom else 1
                    }
                    Button(
                        "→",
                        modifier = Modifier.weight(1f),
                        enabled =
                            pageMode ||
                                left <
                                    BenchmarkGenerator.WORLD_SIDE -
                                        BenchmarkTileRenderer.VIEW_COLUMNS,
                    ) {
                        left += if (pageMode) 1 shl zoom else 1
                    }
                    Button("↑", modifier = Modifier.weight(1f), enabled = pageMode || top > 0) {
                        top -= if (pageMode) 1 shl zoom else 1
                    }
                    Button(
                        "↓",
                        modifier = Modifier.weight(1f),
                        enabled =
                            pageMode ||
                                top <
                                    BenchmarkGenerator.WORLD_SIDE - BenchmarkTileRenderer.VIEW_ROWS,
                    ) {
                        top += if (pageMode) 1 shl zoom else 1
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
                            BenchmarkGenerator.append(
                                it,
                                BenchmarkGenerator.Pattern.ADVERSARIAL,
                                1000,
                            )
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
                Button(
                    "Run diagnostics + checkpoint visible tiles",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy && latest > 0,
                ) {
                    busy = true
                    val diagnosticLeft = left
                    val diagnosticTop = top
                    scope.launch {
                        try {
                            val diagnostic =
                                withContext(Dispatchers.IO) {
                                    BenchmarkDiagnostics.run(
                                        store,
                                        directory,
                                        diagnosticLeft,
                                        diagnosticTop,
                                    )
                                }
                            latest = store.latestEpoch.toInt()
                            selected = latest
                            refresh++
                            diagnosticPath = diagnostic.file.toAbsolutePath().toString()
                            message =
                                if (diagnostic.successful) "Diagnostics passed; log saved below"
                                else "Diagnostics failed; details saved below"
                        } catch (failure: CancellationException) {
                            throw failure
                        } catch (failure: Exception) {
                            message = "Diagnostics could not write a log: ${failure.message}"
                        } finally {
                            busy = false
                        }
                    }
                }
                Button(
                    when {
                        suiteStopping -> "Stopping suite after current batch…"
                        suiteRunning -> "Stop storage suite"
                        else -> "Run storage suite (1M layers + 512² world)"
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = if (suiteRunning) !suiteStopping else !busy,
                ) {
                    if (suiteRunning) {
                        suiteStopping = true
                        stopSuite.set(true)
                        message = "Stopping storage suite after the current batch"
                    } else {
                        busy = true
                        suiteRunning = true
                        stopSuite.set(false)
                        message = "Starting isolated storage suite"
                        scope.launch {
                            try {
                                val suite =
                                    withContext(Dispatchers.IO) {
                                        val workJob = coroutineContext[Job]
                                        BenchmarkStorageSuite.run(
                                            directory,
                                            wideWorldSide = 512,
                                            shouldStop = {
                                                stopSuite.get() || workJob?.isActive == false
                                            },
                                            onProgress = { progress ->
                                                scope.launch {
                                                    if (suiteRunning && !suiteStopping)
                                                        message = progress
                                                }
                                            },
                                        )
                                    }
                                diagnosticPath = suite.file.toAbsolutePath().toString()
                                message =
                                    "Storage suite ${suite.status.name.lowercase()}; log saved below"
                            } catch (failure: CancellationException) {
                                throw failure
                            } catch (failure: Exception) {
                                message = "Storage suite could not write a log: ${failure.message}"
                            } finally {
                                suiteRunning = false
                                suiteStopping = false
                                busy = false
                            }
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
                                        pageCache.clear()
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
                diagnosticPath?.let { Text("Diagnostic log: $it") }
                Text(
                    "${store.tileCount} tiles · ${store.layerCount} layers · ${store.byteCount / 1024} KiB sealed · ${store.indexArrayBytes / 1024} KiB index arrays"
                )
                result?.let {
                    Text(
                        "Historical read: ${it.elapsedNanos / 1_000} µs · ${it.visibleTiles} tiles · ${it.visitedLayers} layers visited · ${it.skippedLayers} skipped by masks · ${it.decodedLayers} decoded"
                    )
                }
                pageResult?.let {
                    Text(
                        "Paged read: ${it.elapsedNanos / 1_000} µs · ${it.tileReads} tile lookups · ${it.pageCount} GPU pages · ${it.cachedPages} pages cached"
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
