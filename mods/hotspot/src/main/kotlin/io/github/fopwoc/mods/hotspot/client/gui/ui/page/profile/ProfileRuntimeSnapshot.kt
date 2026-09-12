package io.github.fopwoc.mods.hotspot.client.gui.ui.page.profile

import io.github.fopwoc.mods.framework.format.TimeFormat
import io.github.fopwoc.mods.hotspot.client.profile.AccessState
import io.github.fopwoc.mods.hotspot.client.profile.ChunkRef
import io.github.fopwoc.mods.hotspot.client.profile.ProfileSessionStatus
import io.github.fopwoc.mods.hotspot.client.profile.ProfileStore
import io.github.fopwoc.mods.hotspot.client.profile.TileEntityRef
import io.github.fopwoc.mods.hotspot.protocol.DimensionProfile
import io.github.fopwoc.mods.hotspot.protocol.MAX_DURATION_TICKS
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Reads [ProfileStore] into an immutable model for the screen. */
object ProfileRuntimeSnapshot {
  fun read(selectedDimensionId: Int?, durationSeconds: Int): ProfileModel {
    val snapshot = ProfileStore.snapshot
    val status = ProfileStore.status
    val maxDurationSeconds = (ProfileStore.serverMaxDurationTicks ?: (MAX_DURATION_TICKS)) / 20
    val dimension = snapshot?.let {
      it.dimension(selectedDimensionId ?: -1) ?: it.dimensions.firstOrNull()
    }

    val statusLine =
        when (status) {
          ProfileSessionStatus.Idle ->
              snapshot?.let {
                "taken ${TIME.format(Date(it.takenAtEpochMillis))} · ${it.durationTicks / 20} s window"
              } ?: "No data yet"
          ProfileSessionStatus.Waiting -> "Waiting for the server…"
          is ProfileSessionStatus.Profiling ->
              "Profiling… ${(status.remainingTicks + 19) / 20} s" +
                  if ((ProfileStore.serverMaxDurationTicks ?: Int.MAX_VALUE) < durationSeconds * 20)
                      " (server limit)"
                  else ""
          ProfileSessionStatus.Receiving -> "Receiving snapshot…"
          is ProfileSessionStatus.Failed -> status.reason
        }

    val focusedChunk = ProfileStore.focusedChunk?.takeIf { it.dimensionId == dimension?.id }
    val chunkRows =
        dimension?.chunks.orEmpty().map { chunk ->
          checkNotNull(dimension)
          ChunkRow(
              ref = ChunkRef(dimension.id, chunk.chunkX, chunk.chunkZ),
              label =
                  "${TimeFormat.millisAdaptive(chunk.totalMs)}  ${chunkLabel(chunk.chunkX, chunk.chunkZ)}  ${chunk.tileEntityCount} TE" +
                      if (chunk.entityCount > 0) " · ${chunk.entityCount} ent" else "",
          )
        }
    val focusedIndex = chunkRows.indexOfFirst { it.ref == focusedChunk }
    val focusedProfile = focusedChunk?.let(ProfileStore::chunk)
    val tileEntityRows =
        focusedProfile?.tileEntities.orEmpty().map { entry ->
          checkNotNull(focusedChunk)
          TileEntityRow(
              ref = TileEntityRef(focusedChunk.dimensionId, entry.x, entry.y, entry.z),
              label =
                  "${TimeFormat.millisAdaptive(entry.ms)}  ${entry.name}  ${blockLabel(entry.x, entry.y, entry.z)}",
          )
        }
    val selectedIndices =
        tileEntityRows
            .withIndex()
            .filter { ProfileStore.isSelected(it.value.ref) }
            .mapTo(HashSet()) { it.index }

    val highlightedChunks = dimension?.let { ProfileStore.highlightedChunks(it.id).size } ?: 0
    val selectedBlocks = ProfileStore.selectedCount()
    val access = ProfileStore.access
    return ProfileModel(
        blocker = (access as? AccessState.Blocked)?.blocker,
        checkingAccess = access is AccessState.Checking,
        statusLine = statusLine,
        canProfile = !status.isBusy && access is AccessState.Granted,
        durationSeconds = durationSeconds.coerceAtMost(maxDurationSeconds),
        maxDurationSeconds = maxDurationSeconds,
        hasSnapshot = snapshot != null,
        emptyHint =
            "Profile the server for a few seconds, then pick chunks and blocks to highlight them in the world.",
        dimensionLabel = dimension?.let { "${it.name} (DIM ${it.id})" } ?: "—",
        canCycleDimensions = (snapshot?.dimensions?.size ?: 0) > 1,
        dimensionSummary = dimension?.let(::summarize) ?: "",
        chunks = chunkRows,
        focusedChunkIndex = focusedIndex,
        tileEntities = tileEntityRows,
        selectedTileEntityIndices = selectedIndices,
        selectionSummary =
            if (highlightedChunks == 0 && selectedBlocks == 0) "Nothing highlighted"
            else "Highlighting $highlightedChunks chunks · $selectedBlocks blocks",
        hasSelection = ProfileStore.hasSelection,
    )
  }

  private fun summarize(dimension: DimensionProfile): String {
    val attributed = dimension.tileEntityMs + dimension.entityMs
    // The per-tile-entity clocks and the world-tick clock are separate; on a near-idle world
    // their overheads make the sum exceed the tick, and "other" means nothing then.
    val other =
        if (dimension.tickMs > attributed) TimeFormat.millisAdaptive(dimension.tickMs - attributed)
        else "—"
    return "${TimeFormat.millisAdaptive(dimension.tickMs)}/tick · blocks ${TimeFormat.millisAdaptive(dimension.tileEntityMs)} · entities ${TimeFormat.millisAdaptive(dimension.entityMs)} · other $other · ${dimension.chunks.size} chunks"
  }

  private val TIME = SimpleDateFormat("HH:mm:ss", Locale.ROOT)

  private fun chunkLabel(x: Int, z: Int): String = "($x, $z)"

  private fun blockLabel(x: Int, y: Int, z: Int): String = "$x $y $z"
}
