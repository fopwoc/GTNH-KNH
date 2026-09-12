package io.github.fopwoc.mods.hotspot.client.profile

import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.common.gameevent.TickEvent
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.client.ClientWorldContext
import io.github.fopwoc.mods.framework.network.ClientChannelTracker
import io.github.fopwoc.mods.hotspot.protocol.ChunkProfile
import io.github.fopwoc.mods.hotspot.protocol.HotspotChannel
import io.github.fopwoc.mods.hotspot.protocol.ProfileRequest
import io.github.fopwoc.mods.hotspot.protocol.ProfileRequestMessage
import io.github.fopwoc.mods.hotspot.protocol.ProfileSnapshot
import io.github.fopwoc.mods.hotspot.protocol.ProfileSnapshotPart
import io.github.fopwoc.mods.hotspot.protocol.ProfileSnapshotParts
import io.github.fopwoc.mods.hotspot.protocol.ProfileStatus
import io.github.fopwoc.mods.hotspot.protocol.ProfileStatusUpdate
import io.github.fopwoc.mods.hotspot.protocol.TileEntityProfile
import org.apache.logging.log4j.LogManager

/**
 * The client's copy of the last snapshot plus what the player picked from it. The menu edits it,
 * the world overlay reads it; both run on the client thread.
 */
@SideOnly(Side.CLIENT)
object ProfileStore {
  private const val FAILED_STATUS_TICKS = 20 * 6
  private val logger = LogManager.getLogger(ProfileStore::class.java)
  private val channel = ClientChannelTracker.watch(HotspotChannel) { onDisconnected() }

  var status: ProfileSessionStatus = ProfileSessionStatus.Idle
    private set

  var snapshot: ProfileSnapshot? = null
    private set

  /** The chunk whose contents the menu shows; always highlighted. */
  var focusedChunk: ChunkRef? = null
    private set

  private val selectedTileEntities = LinkedHashSet<TileEntityRef>()
  private var chunkIndex: Map<ChunkRef, ChunkProfile> = emptyMap()
  private var tileEntityIndex: Map<TileEntityRef, TileEntityProfile> = emptyMap()

  private val assembler = ProfileSnapshotParts.Assembler()
  private var nextRequestId = System.nanoTime()
  private var pendingRequestId: Long? = null
  private var failedTicks = 0
  private var loadedContextId: String? = null
  private var dirty = false

  val hasSelection: Boolean
    get() = focusedChunk != null || selectedTileEntities.isNotEmpty()

  fun requestProfile(durationTicks: Int) {
    if (status.isBusy) {
      return
    }
    val requestId = nextRequestId++
    if (!channel.isAvailable) {
      fail("Hotspot is not installed on this server")
      return
    }
    HotspotChannel.requests.send(ProfileRequestMessage(ProfileRequest(requestId, durationTicks)))
    pendingRequestId = requestId
    status = ProfileSessionStatus.Waiting
  }

  fun onStatus(update: ProfileStatusUpdate) {
    if (update.requestId != pendingRequestId) {
      return
    }
    when (update.status) {
      ProfileStatus.STARTED ->
          status = ProfileSessionStatus.Profiling(update.remainingTicks, update.remainingTicks)
      ProfileStatus.DENIED -> fail("Hotspot is not enabled for you on this server")
      ProfileStatus.PROFILER_UNAVAILABLE -> fail("The server has no Opis profiler to read")
    }
  }

  fun onSnapshotPart(part: ProfileSnapshotPart) {
    val expected = pendingRequestId ?: return
    if (part.requestId != expected) {
      return
    }
    status = ProfileSessionStatus.Receiving
    val complete = assembler.accept(part, expected) ?: return
    pendingRequestId = null
    status = ProfileSessionStatus.Idle
    install(complete)
    dirty = true
    logger.debug(
        "Received snapshot with {} dimensions, {} chunks",
        complete.dimensions.size,
        complete.dimensions.sumOf { it.chunks.size },
    )
  }

  fun onDisconnected() {
    flush()
    assembler.reset()
    pendingRequestId = null
    status = ProfileSessionStatus.Idle
    snapshot = null
    chunkIndex = emptyMap()
    tileEntityIndex = emptyMap()
    focusedChunk = null
    selectedTileEntities.clear()
    loadedContextId = null
  }

  /** Loads the saved profile when a world becomes current and writes back pending changes. */
  private fun syncPersistence() {
    val contextId = ClientWorldContext.currentId() ?: return
    if (contextId != loadedContextId) {
      loadedContextId = contextId
      val saved = ProfilePersistence.load(contextId)
      saved.snapshot?.let(::install)
      focusedChunk = saved.focusedChunk?.takeIf { it in chunkIndex }
      selectedTileEntities.clear()
      saved.selectedTileEntities.filterTo(selectedTileEntities) { it in tileEntityIndex }
      dirty = false
      return
    }
    flush()
  }

  private fun flush() {
    val contextId = loadedContextId ?: return
    if (!dirty) {
      return
    }
    dirty = false
    ProfilePersistence.save(
        contextId,
        ProfilePersistence.Saved(
            snapshot = snapshot,
            focusedChunk = focusedChunk,
            selectedTileEntities = selectedTileEntities.toList(),
        ),
    )
  }

  @SubscribeEvent
  fun onClientTick(event: TickEvent.ClientTickEvent) {
    if (event.phase != TickEvent.Phase.END) {
      return
    }
    syncPersistence()
    when (val current = status) {
      is ProfileSessionStatus.Profiling ->
          status = current.copy(remainingTicks = (current.remainingTicks - 1).coerceAtLeast(0))
      is ProfileSessionStatus.Failed -> {
        failedTicks += 1
        if (failedTicks >= FAILED_STATUS_TICKS) {
          status = ProfileSessionStatus.Idle
        }
      }
      else -> Unit
    }
  }

  fun focusChunk(chunk: ChunkRef?) {
    focusedChunk = chunk?.takeIf { it in chunkIndex }
    dirty = true
  }

  fun replaceSelection(refs: Collection<TileEntityRef>) {
    selectedTileEntities.clear()
    refs.filterTo(selectedTileEntities) { it in tileEntityIndex }
    dirty = true
  }

  fun setSelectedInChunk(chunk: ChunkRef, refs: Collection<TileEntityRef>) {
    selectedTileEntities.removeAll { it.chunk == chunk }
    refs.filterTo(selectedTileEntities) { it.chunk == chunk && it in tileEntityIndex }
    dirty = true
  }

  fun isSelected(ref: TileEntityRef): Boolean = ref in selectedTileEntities

  fun selectedInChunk(chunk: ChunkRef): List<TileEntityRef> = selectedTileEntities.filter {
    it.chunk == chunk
  }

  fun selectedCount(): Int = selectedTileEntities.size

  fun clearSelection() {
    focusedChunk = null
    selectedTileEntities.clear()
    dirty = true
  }

  fun chunk(ref: ChunkRef): ChunkProfile? = chunkIndex[ref]

  fun tileEntity(ref: TileEntityRef): TileEntityProfile? = tileEntityIndex[ref]

  /** Chunks to draw in [dimensionId]: the focused one and every chunk with a selected block. */
  fun highlightedChunks(dimensionId: Int): List<ChunkRef> {
    val chunks = LinkedHashSet<ChunkRef>()
    focusedChunk?.takeIf { it.dimensionId == dimensionId }?.let(chunks::add)
    selectedTileEntities.filter { it.dimensionId == dimensionId }.mapTo(chunks) { it.chunk }
    return chunks.toList()
  }

  fun highlightedTileEntities(dimensionId: Int): List<TileEntityRef> = selectedTileEntities.filter {
    it.dimensionId == dimensionId
  }

  private fun fail(reason: String) {
    pendingRequestId = null
    failedTicks = 0
    status = ProfileSessionStatus.Failed(reason)
  }

  private fun install(complete: ProfileSnapshot) {
    snapshot = complete
    chunkIndex =
        complete.dimensions
            .flatMap { dimension ->
              dimension.chunks.map { ChunkRef(dimension.id, it.chunkX, it.chunkZ) to it }
            }
            .toMap()
    tileEntityIndex =
        complete.dimensions
            .flatMap { dimension ->
              dimension.chunks.flatMap { chunk ->
                chunk.tileEntities.map { TileEntityRef(dimension.id, it.x, it.y, it.z) to it }
              }
            }
            .toMap()
    // Picks survive a re-profile as long as the same blocks are still listed.
    selectedTileEntities.retainAll { it in tileEntityIndex }
    focusedChunk = focusedChunk?.takeIf { it in chunkIndex }
  }
}
