package io.github.fopwoc.mods.gtnhclientworldbackup.backup

import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.common.gameevent.TickEvent
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.gtnhclientworldbackup.ClientWorldBackupMod
import io.github.fopwoc.mods.gtnhclientworldbackup.MOD_ID
import io.github.fopwoc.mods.gtnhclientworldbackup.MOD_VERSION
import io.github.fopwoc.mods.gtnhclientworldbackup.backup.model.BackupUiState
import io.github.fopwoc.mods.gtnhclientworldbackup.backup.model.ChunkHighlightState
import io.github.fopwoc.mods.gtnhclientworldbackup.client.highlight.BackedUpChunkHighlighter
import io.github.fopwoc.mods.gtnhclientworldbackup.config.BackupConfig
import java.io.File
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.minecraft.client.Minecraft
import net.minecraft.client.entity.EntityClientPlayerMP
import net.minecraft.client.gui.inventory.GuiContainer
import net.minecraft.client.multiplayer.WorldClient
import net.minecraft.inventory.IInventory
import net.minecraft.inventory.Slot
import net.minecraft.item.ItemStack
import net.minecraft.nbt.CompressedStreamTools
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagDouble
import net.minecraft.nbt.NBTTagFloat
import net.minecraft.nbt.NBTTagList
import net.minecraft.util.MathHelper
import net.minecraft.tileentity.TileEntity
import net.minecraft.world.WorldSettings
import net.minecraft.world.WorldType
import net.minecraft.world.chunk.Chunk
import net.minecraft.world.chunk.storage.AnvilChunkLoader
import net.minecraft.world.chunk.storage.RegionFile
import net.minecraft.world.chunk.storage.RegionFileCache
import net.minecraft.world.storage.SaveHandler
import net.minecraft.world.storage.WorldInfo
import net.minecraftforge.event.world.WorldEvent

@Suppress("unused")
@SideOnly(Side.CLIENT)
object ClientWorldBackupManager {
    private const val TICKS_PER_SECOND = 20
    private const val ANVIL_SAVE_VERSION = 19133
    private const val MANIFEST_FILE_NAME = "client-world-backup-manifest.json"
    private const val UNKNOWN_SOURCE = "unknown"
    private const val SAFE_OVERWORLD_DIMENSION_ID = 0
    private const val SAFE_FALLBACK_SPAWN_Y = 80

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    private var activeSession: BackupSession? = null
    private val dimensionLoaders = linkedMapOf<Int, AnvilChunkLoader>()
    private val standaloneDimensionLoaders = linkedMapOf<Int, AnvilChunkLoader>()
    private val observedTileEntitySnapshots = linkedMapOf<InventoryLocation, NBTTagCompound>()
    private val observedInventorySnapshots = linkedMapOf<InventoryLocation, ObservedInventorySnapshot>()
    private var ticksUntilNextCapture = 0
    private var statusLine = "Backup: waiting for multiplayer world"
    private var detailLine = "Only chunks the client actually received can be archived."

    fun canCaptureNow(): Boolean = resolveManualCaptureUnavailableReason(Minecraft.getMinecraft()) == null

    fun captureNowFromUi(): Boolean {
        val minecraft = Minecraft.getMinecraft()
        val unavailableReason = resolveManualCaptureUnavailableReason(minecraft)
        if (unavailableReason != null) {
            statusLine = "Backup: capture unavailable"
            detailLine = unavailableReason
            return false
        }

        val world = minecraft.theWorld ?: return false
        val player = minecraft.thePlayer ?: return false
        val source = resolveSourceDescriptor(minecraft)
        val session = ensureSession(minecraft, world, player, source)
        captureObservedChunks(minecraft, world, player, session)
        ticksUntilNextCapture = BackupConfig.autosaveIntervalSeconds * TICKS_PER_SECOND
        return true
    }

    fun getUiState(): BackupUiState {
        val session = activeSession
        val currentDimensionId = Minecraft.getMinecraft().theWorld?.provider?.dimensionId
        val currentDimensionChunkCount = currentDimensionId?.let {
            session?.savedChunksByDimension?.get(it)?.size ?: 0
        } ?: 0

        return BackupUiState(
            statusLine = statusLine,
            detailLine = detailLine,
            saveName = session?.saveName,
            sourceName = session?.source?.displayName,
            sourceAddress = session?.source?.address,
            currentDimensionId = currentDimensionId,
            totalUniqueChunks = session?.totalUniqueChunks() ?: 0,
            currentDimensionChunkCount = currentDimensionChunkCount,
            nextAutosaveSeconds = ticksUntilNextCapture / TICKS_PER_SECOND,
            highlightsEnabled = BackedUpChunkHighlighter.areHighlightsEnabled(),
            highlightLegend = listOf(
                "Blue: chunk was already in the backup from an earlier session",
                "Green: chunk was saved during this session",
                "Stronger outline: chunk under your crosshair"
            ),
            notes = listOf(
                "Only chunks and state actually sent to the client can be archived.",
                "Some modded tile entity data may still be incomplete if the server never synced it."
            )
        )
    }

    fun getChunkHighlightSnapshot(dimensionId: Int): Map<Long, ChunkHighlightState> {
        val session = activeSession ?: return emptyMap()
        val savedChunkKeys = session.savedChunksByDimension[dimensionId].orEmpty()
        val sessionChunkKeys = session.sessionSavedChunksByDimension[dimensionId].orEmpty()
        return buildMap(savedChunkKeys.size) {
            savedChunkKeys.forEach { chunkKey ->
                put(
                    chunkKey,
                    if (sessionChunkKeys.contains(chunkKey)) ChunkHighlightState.SAVED_THIS_SESSION else ChunkHighlightState.SAVED_EARLIER
                )
            }
        }
    }

    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase != TickEvent.Phase.END) {
            return
        }

        if (BackupConfig.refreshIfChanged()) {
            ticksUntilNextCapture = ticksUntilNextCapture.coerceAtMost(BackupConfig.autosaveIntervalSeconds * TICKS_PER_SECOND)
        }

        val minecraft = Minecraft.getMinecraft()

        if (!BackupConfig.enabled) {
            flushSessionIfNeeded()
            statusLine = "Backup: disabled"
            detailLine = "Set \"enabled\": true in config/${MOD_ID}.json to archive chunks."
            return
        }

        val world = minecraft.theWorld ?: run {
            flushSessionIfNeeded()
            statusLine = "Backup: waiting for multiplayer world"
            detailLine = "Only chunks the client actually received can be archived."
            return
        }

        val player = minecraft.thePlayer ?: return

        if (minecraft.isIntegratedServerRunning && !BackupConfig.saveSingleplayer) {
            flushSessionIfNeeded()
            statusLine = "Backup: paused in singleplayer"
            detailLine = "Set \"saveSingleplayer\": true to archive integrated-server worlds too."
            return
        }

        val source = resolveSourceDescriptor(minecraft)
        val session = ensureSession(minecraft, world, player, source)

        if (ticksUntilNextCapture > 0) {
            ticksUntilNextCapture -= 1
            updateIdleStatus(world, session)
            return
        }

        ticksUntilNextCapture = BackupConfig.autosaveIntervalSeconds * TICKS_PER_SECOND
        captureObservedChunks(minecraft, world, player, session)
    }

    @SubscribeEvent
    fun onWorldUnload(event: WorldEvent.Unload) {
        if (!event.world.isRemote) {
            return
        }

        val saveName = activeSession?.saveName
        flushSessionIfNeeded()

        if (saveName != null) {
            statusLine = "Backup: session flushed"
            detailLine = "Saved observed chunks into saves/$saveName."
        }
    }

    private fun ensureSession(
        minecraft: Minecraft,
        world: WorldClient,
        player: EntityClientPlayerMP,
        source: BackupSourceDescriptor
    ): BackupSession {
        val current = activeSession
        if (current != null && current.key == source.key) {
            return current
        }

        flushSessionIfNeeded()

        val savesDirectory = File(minecraft.mcDataDir, "saves")
        val saveName = buildSaveName(source)
        val rootDirectory = File(savesDirectory, saveName)
        rootDirectory.mkdirs()

        val session = BackupSession(
            key = source.key,
            saveName = saveName,
            rootDirectory = rootDirectory,
            source = source,
            startedAtEpochMillis = System.currentTimeMillis()
        )
        initializeKnownChunks(session)

        activeSession = session
        ticksUntilNextCapture = 0
        writeLevelDat(minecraft, world, player, session)
        writeManifest(world, player, session)
        statusLine = "Backup: started"
        detailLine = "Writing observed chunks into saves/${session.saveName}."
        return session
    }

    private fun captureObservedChunks(
        minecraft: Minecraft,
        world: WorldClient,
        player: EntityClientPlayerMP,
        session: BackupSession
    ) {
        captureObservedTileEntities(world)
        captureObservedInventories(minecraft, world)

        val dimensionId = world.provider.dimensionId
        val chunkKeys = session.savedChunksByDimension.getOrPut(dimensionId) { linkedSetOf() }
        val stats = session.dimensionStats.getOrPut(dimensionId) {
            DimensionStats(
                dimensionId = dimensionId,
                folder = resolveDimensionFolderName(dimensionId),
                firstSeenEpochMillis = System.currentTimeMillis(),
                lastUpdatedEpochMillis = System.currentTimeMillis()
            )
        }
        val loader = dimensionLoaders.getOrPut(dimensionId) {
            AnvilChunkLoader(resolveDimensionDirectory(session.rootDirectory, dimensionId))
        }
        val standaloneLoader = if (dimensionId == SAFE_OVERWORLD_DIMENSION_ID) {
            null
        } else {
            standaloneDimensionLoaders.getOrPut(dimensionId) {
                AnvilChunkLoader(resolveStandaloneWorldDirectory(minecraft, session, dimensionId))
            }
        }
        val radius = resolveCaptureRadius(minecraft)
        val minChunkX = player.chunkCoordX - radius
        val maxChunkX = player.chunkCoordX + radius
        val minChunkZ = player.chunkCoordZ - radius
        val maxChunkZ = player.chunkCoordZ + radius

        var savedThisPass = 0
        val savedChunkCoordinates = linkedSetOf<Pair<Int, Int>>()

        for (chunkX in minChunkX..maxChunkX) {
            for (chunkZ in minChunkZ..maxChunkZ) {
                val chunk = world.getChunkFromChunkCoords(chunkX, chunkZ)
                if (!shouldSaveChunk(chunk, chunkKeys)) {
                    continue
                }

                runCatching {
                    loader.saveChunk(world, chunk)
                    standaloneLoader?.saveChunk(world, chunk)
                    chunk.isModified = false
                }.onSuccess {
                    savedThisPass += 1
                    val chunkKey = toChunkKey(chunk.xPosition, chunk.zPosition)
                    val isNewChunk = chunkKeys.add(chunkKey)
                    savedChunkCoordinates.add(chunk.xPosition to chunk.zPosition)
                    if (isNewChunk) {
                        session.sessionSavedChunksByDimension
                            .getOrPut(dimensionId) { linkedSetOf() }
                            .add(chunkKey)
                    }
                    stats.lastUpdatedEpochMillis = System.currentTimeMillis()
                    stats.playerChunkX = player.chunkCoordX
                    stats.playerChunkZ = player.chunkCoordZ
                    if (isNewChunk) {
                        stats.chunkCount += 1
                    }

                    if (savedThisPass % BackupConfig.flushEverySavedChunks == 0) {
                        loader.saveExtraData()
                    }
                }.onFailure {
                    ClientWorldBackupMod.logger.warn(
                        "Failed to save observed chunk {},{} in dimension {}",
                        chunk.xPosition,
                        chunk.zPosition,
                        dimensionId,
                        it
                    )
                }
            }
        }

        if (savedThisPass > 0) {
            loader.saveExtraData()
            standaloneLoader?.saveExtraData()
            savedChunkCoordinates.forEach { (savedChunkX, savedChunkZ) ->
                patchChunkTileEntityData(resolveDimensionDirectory(session.rootDirectory, dimensionId), dimensionId, savedChunkX, savedChunkZ)
                if (dimensionId != SAFE_OVERWORLD_DIMENSION_ID) {
                    patchChunkTileEntityData(resolveStandaloneWorldDirectory(minecraft, session, dimensionId), dimensionId, savedChunkX, savedChunkZ)
                }
            }
            writeLevelDat(minecraft, world, player, session)
            writeManifest(world, player, session)
            if (dimensionId != SAFE_OVERWORLD_DIMENSION_ID) {
                writeStandaloneDimensionLevelDat(minecraft, world, player, session, dimensionId)
                writeStandaloneDimensionManifest(world, player, session, dimensionId)
            }
            statusLine = "Backup: saved $savedThisPass chunk(s)"
            detailLine = "Tracked ${session.totalUniqueChunks()} unique chunk(s) in saves/${session.saveName}."
        } else {
            updateIdleStatus(world, session)
        }
    }

    private fun shouldSaveChunk(chunk: Chunk, chunkKeys: Set<Long>): Boolean {
        if (!chunk.isChunkLoaded || chunk.isEmpty()) {
            return false
        }

        val chunkKey = toChunkKey(chunk.xPosition, chunk.zPosition)
        return chunk.isModified || !chunkKeys.contains(chunkKey) || !chunk.chunkTileEntityMap.isEmpty()
    }

    private fun writeLevelDat(
        minecraft: Minecraft,
        world: WorldClient,
        player: EntityClientPlayerMP,
        session: BackupSession
    ) {
        val worldSettings = WorldSettings(
            0L,
            WorldSettings.GameType.CREATIVE,
            true,
            false,
            WorldType.DEFAULT
        ).enableCommands()

        val worldInfo = WorldInfo(worldSettings, session.saveName)
        val spawn = resolveInitialSpawn(world, player, session)

        worldInfo.setSaveVersion(ANVIL_SAVE_VERSION)
        worldInfo.setSpawnPosition(spawn.x, spawn.y, spawn.z)
        worldInfo.incrementTotalWorldTime(world.totalWorldTime)
        worldInfo.setWorldTime(world.worldTime)
        worldInfo.setRaining(world.isRaining)
        worldInfo.setThundering(world.isThundering)
        worldInfo.setServerInitialized(true)

        val playerTag = NBTTagCompound()
        player.writeToNBT(playerTag)
        applySafeSingleplayerPlayerState(playerTag, spawn)

        val saveHandler = SaveHandler(File(minecraft.mcDataDir, "saves"), session.saveName, true)
        saveHandler.saveWorldInfoWithPlayer(worldInfo, playerTag)
    }

    private fun writeManifest(
        world: WorldClient,
        player: EntityClientPlayerMP,
        session: BackupSession
    ) {
        val now = System.currentTimeMillis()
        val captures = session.dimensionStats.values
            .sortedBy { it.dimensionId }
            .map {
                BackupDimensionCapture(
                    dimensionId = it.dimensionId,
                    folder = it.folder,
                    chunkCount = it.chunkCount,
                    firstSeenEpochMillis = it.firstSeenEpochMillis,
                    lastUpdatedEpochMillis = it.lastUpdatedEpochMillis,
                    playerChunkX = it.playerChunkX,
                    playerChunkZ = it.playerChunkZ
                )
            }

        val manifest = BackupManifest(
            schemaVersion = 1,
            modVersion = MOD_VERSION,
            saveName = session.saveName,
            startedAtEpochMillis = session.startedAtEpochMillis,
            updatedAtEpochMillis = now,
            source = BackupManifestSource(
                key = session.source.key,
                type = session.source.type,
                displayName = session.source.displayName,
                address = session.source.address,
                integratedServer = session.source.integratedServer
            ),
            player = BackupPlayerState(
                dimensionId = world.provider.dimensionId,
                posX = player.posX,
                posY = player.posY,
                posZ = player.posZ,
                chunkX = player.chunkCoordX,
                chunkZ = player.chunkCoordZ
            ),
            worldState = BackupWorldState(
                totalWorldTime = world.totalWorldTime,
                dayTime = world.worldTime,
                isRaining = world.isRaining,
                isThundering = world.isThundering
            ),
            captures = captures,
            warnings = listOf(
                "This archive is client-side only and can only contain chunks, tile entities, and state the server actually sent to this client.",
                "Unseen chunks, hidden server-side data, anti-xray replacements, and some mod-specific block/entity state may be incomplete or absent.",
                "The exported save is intended for inspection and recovery, not as an authoritative full server backup."
            )
        )

        File(session.rootDirectory, MANIFEST_FILE_NAME).writeText(json.encodeToString(manifest))
    }

    private fun writeStandaloneDimensionLevelDat(
        minecraft: Minecraft,
        world: WorldClient,
        player: EntityClientPlayerMP,
        session: BackupSession,
        dimensionId: Int
    ) {
        val standaloneSaveName = session.standaloneSaveNamesByDimension.getValue(dimensionId)
        val worldSettings = WorldSettings(
            0L,
            WorldSettings.GameType.CREATIVE,
            true,
            false,
            WorldType.DEFAULT
        ).enableCommands()

        val worldInfo = WorldInfo(worldSettings, standaloneSaveName)
        val spawn = SafeSpawn(
            x = MathHelper.floor_double(player.posX),
            y = MathHelper.floor_double(player.posY.coerceIn(1.0, 255.0)),
            z = MathHelper.floor_double(player.posZ)
        )

        worldInfo.setSaveVersion(ANVIL_SAVE_VERSION)
        worldInfo.setSpawnPosition(spawn.x, spawn.y, spawn.z)
        worldInfo.incrementTotalWorldTime(world.totalWorldTime)
        worldInfo.setWorldTime(world.worldTime)
        worldInfo.setRaining(world.isRaining)
        worldInfo.setThundering(world.isThundering)
        worldInfo.setServerInitialized(true)

        val playerTag = NBTTagCompound()
        player.writeToNBT(playerTag)
        applySafeSingleplayerPlayerState(playerTag, spawn)

        SaveHandler(File(minecraft.mcDataDir, "saves"), standaloneSaveName, true)
            .saveWorldInfoWithPlayer(worldInfo, playerTag)
    }

    private fun writeStandaloneDimensionManifest(
        world: WorldClient,
        player: EntityClientPlayerMP,
        session: BackupSession,
        dimensionId: Int
    ) {
        val standaloneSaveName = session.standaloneSaveNamesByDimension.getValue(dimensionId)
        val capture = session.dimensionStats[dimensionId] ?: return
        val manifest = BackupManifest(
            schemaVersion = 1,
            modVersion = MOD_VERSION,
            saveName = standaloneSaveName,
            startedAtEpochMillis = session.startedAtEpochMillis,
            updatedAtEpochMillis = System.currentTimeMillis(),
            source = BackupManifestSource(
                key = session.source.key,
                type = "standalone-dimension-export",
                displayName = "${session.source.displayName} dimension $dimensionId",
                address = session.source.address,
                integratedServer = session.source.integratedServer
            ),
            player = BackupPlayerState(
                dimensionId = dimensionId,
                posX = player.posX,
                posY = player.posY,
                posZ = player.posZ,
                chunkX = player.chunkCoordX,
                chunkZ = player.chunkCoordZ
            ),
            worldState = BackupWorldState(
                totalWorldTime = world.totalWorldTime,
                dayTime = world.worldTime,
                isRaining = world.isRaining,
                isThundering = world.isThundering
            ),
            captures = listOf(
                BackupDimensionCapture(
                    dimensionId = dimensionId,
                    folder = ".",
                    chunkCount = capture.chunkCount,
                    firstSeenEpochMillis = capture.firstSeenEpochMillis,
                    lastUpdatedEpochMillis = capture.lastUpdatedEpochMillis,
                    playerChunkX = capture.playerChunkX,
                    playerChunkZ = capture.playerChunkZ
                )
            ),
            warnings = listOf(
                "This save is a standalone export of server dimension $dimensionId mapped into a normal singleplayer overworld.",
                "Dimension-specific world provider logic, portals, and server-only data may still be incomplete.",
                "Storage contents are only preserved when the client actually observed and synced them."
            )
        )

        File(resolveStandaloneWorldDirectory(Minecraft.getMinecraft(), session, dimensionId), MANIFEST_FILE_NAME)
            .writeText(json.encodeToString(manifest))
    }

    private fun updateIdleStatus(world: WorldClient, session: BackupSession) {
        val dimensionId = world.provider.dimensionId
        statusLine = "Backup: monitoring dimension $dimensionId"
        detailLine = "Tracked ${session.totalUniqueChunks()} unique chunk(s) in saves/${session.saveName}."
    }

    private fun flushSessionIfNeeded() {
        if (activeSession == null && dimensionLoaders.isEmpty()) {
            return
        }

        runCatching {
            dimensionLoaders.values.forEach(AnvilChunkLoader::saveExtraData)
            standaloneDimensionLoaders.values.forEach(AnvilChunkLoader::saveExtraData)
        }.onFailure {
            ClientWorldBackupMod.logger.warn("Failed to flush pending chunk writes", it)
        }

        dimensionLoaders.clear()
        standaloneDimensionLoaders.clear()
        observedTileEntitySnapshots.clear()
        observedInventorySnapshots.clear()
        RegionFileCache.clearRegionFileReferences()
        activeSession = null
        ticksUntilNextCapture = 0
    }

    private fun resolveCaptureRadius(minecraft: Minecraft): Int {
        val configuredRadius = BackupConfig.maxChunkRadius
        return when {
            configuredRadius > 0 -> configuredRadius
            minecraft.gameSettings.renderDistanceChunks > 0 -> minecraft.gameSettings.renderDistanceChunks
            else -> 8
        }.coerceIn(2, 32)
    }

    private fun resolveManualCaptureUnavailableReason(minecraft: Minecraft): String? {
        return when {
            !BackupConfig.enabled -> "Set \"enabled\": true in config/$MOD_ID.json to archive chunks."
            minecraft.theWorld == null || minecraft.thePlayer == null -> "Join a world before requesting a manual capture."
            minecraft.isIntegratedServerRunning && !BackupConfig.saveSingleplayer ->
                "Set \"saveSingleplayer\": true to archive integrated-server worlds too."
            else -> null
        }
    }

    private fun resolveSourceDescriptor(minecraft: Minecraft): BackupSourceDescriptor {
        val serverData = minecraft.func_147104_D()
        if (serverData != null) {
            val displayName = serverData.serverName.takeIf { it.isNotBlank() } ?: serverData.serverIP
            return BackupSourceDescriptor(
                key = "multiplayer:${serverData.serverIP.lowercase(Locale.ROOT)}",
                type = "multiplayer",
                displayName = displayName,
                address = serverData.serverIP,
                integratedServer = false
            )
        }

        return if (minecraft.isIntegratedServerRunning) {
            BackupSourceDescriptor(
                key = "singleplayer:${UNKNOWN_SOURCE}",
                type = "singleplayer",
                displayName = "Integrated Server",
                address = null,
                integratedServer = true
            )
        } else {
            BackupSourceDescriptor(
                key = "multiplayer:$UNKNOWN_SOURCE",
                type = "multiplayer",
                displayName = "Unknown Server",
                address = null,
                integratedServer = false
            )
        }
    }

    private fun buildSaveName(source: BackupSourceDescriptor): String {
        val prefix = sanitizeForFileName(BackupConfig.saveNamePrefix).ifBlank { "observed" }
        val sourceName = sanitizeForFileName(source.address ?: source.displayName).ifBlank { UNKNOWN_SOURCE }
        val display = sanitizeForFileName(source.displayName).ifBlank { UNKNOWN_SOURCE }
        return listOf(prefix, display, sourceName)
            .joinToString(separator = "-")
            .replace("--", "-")
            .trim('-')
            .take(80)
            .ifBlank { "observed-backup" }
    }

    private fun sanitizeForFileName(value: String): String {
        return value.trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9._-]+"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
    }

    private fun resolveDimensionDirectory(rootDirectory: File, dimensionId: Int): File {
        val directory = if (dimensionId == 0) {
            rootDirectory
        } else {
            File(rootDirectory, resolveDimensionFolderName(dimensionId))
        }
        directory.mkdirs()
        return directory
    }

    private fun resolveStandaloneWorldDirectory(minecraft: Minecraft, session: BackupSession, dimensionId: Int): File {
        val standaloneSaveName = session.standaloneSaveNamesByDimension.getOrPut(dimensionId) {
            buildStandaloneSaveName(session.saveName, dimensionId)
        }
        return File(File(minecraft.mcDataDir, "saves"), standaloneSaveName).apply { mkdirs() }
    }

    private fun resolveDimensionFolderName(dimensionId: Int): String =
        if (dimensionId == 0) "." else "DIM$dimensionId"

    private fun buildStandaloneSaveName(baseSaveName: String, dimensionId: Int): String =
        "$baseSaveName-dim$dimensionId".take(100)

    private fun captureObservedInventories(minecraft: Minecraft, world: WorldClient) {
        world.loadedTileEntityList
            .filterIsInstance<TileEntity>()
            .forEach { tileEntity ->
                val inventory = tileEntity as? IInventory ?: return@forEach
                val snapshot = snapshotInventory(inventory) ?: return@forEach
                observedInventorySnapshots[InventoryLocation(world.provider.dimensionId, tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord)] = snapshot
            }

        val currentScreen = minecraft.currentScreen
        if (currentScreen !is GuiContainer) {
            return
        }

        val groupedSnapshots = linkedMapOf<InventoryLocation, MutableMap<Int, NBTTagCompound>>()
        currentScreen.inventorySlots.inventorySlots
            .filterIsInstance<Slot>()
            .forEach { slot ->
                val tileEntity = slot.inventory as? TileEntity ?: return@forEach
                val stack = slot.stack ?: return@forEach
                val location = InventoryLocation(world.provider.dimensionId, tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord)
                groupedSnapshots.getOrPut(location) { linkedMapOf() }[slot.slotIndex] = ItemStack.copyItemStack(stack).writeToNBT(NBTTagCompound())
            }

        groupedSnapshots.forEach { (location, items) ->
            observedInventorySnapshots[location] = ObservedInventorySnapshot(items = items.toMap(), explicitlyObserved = true)
        }
    }

    private fun captureObservedTileEntities(world: WorldClient) {
        world.loadedTileEntityList
            .filterIsInstance<TileEntity>()
            .forEach { tileEntity ->
                val snapshot = runCatching {
                    NBTTagCompound().also(tileEntity::writeToNBT)
                }.getOrNull() ?: return@forEach

                observedTileEntitySnapshots[
                    InventoryLocation(world.provider.dimensionId, tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord)
                ] = snapshot
            }
    }

    private fun snapshotInventory(inventory: IInventory): ObservedInventorySnapshot? {
        val items = linkedMapOf<Int, NBTTagCompound>()
        for (slotIndex in 0 until inventory.sizeInventory) {
            val stack = inventory.getStackInSlot(slotIndex) ?: continue
            items[slotIndex] = ItemStack.copyItemStack(stack).writeToNBT(NBTTagCompound())
        }

        if (items.isEmpty()) {
            return null
        }

        return ObservedInventorySnapshot(items = items, explicitlyObserved = false)
    }

    private fun patchChunkTileEntityData(chunkDirectory: File, dimensionId: Int, chunkX: Int, chunkZ: Int) {
        val chunkRoot = readChunkNbt(chunkDirectory, chunkX, chunkZ) ?: return
        val levelTag = chunkRoot.getCompoundTag("Level")
        val tileEntities = levelTag.getTagList("TileEntities", 10)
        var mutated = false

        for (tileEntityIndex in 0 until tileEntities.tagCount()) {
            val tileEntityTag = tileEntities.getCompoundTagAt(tileEntityIndex)
            val location = InventoryLocation(
                dimensionId = dimensionId,
                x = tileEntityTag.getInteger("x"),
                y = tileEntityTag.getInteger("y"),
                z = tileEntityTag.getInteger("z")
            )
            val liveSnapshot = observedTileEntitySnapshots[location]
            if (liveSnapshot != null) {
                mutated = mergeTileEntitySnapshotIntoTag(tileEntityTag, liveSnapshot) || mutated
            }
            val snapshot = observedInventorySnapshots[location] ?: continue
            mutated = mergeInventorySnapshotIntoTileEntity(tileEntityTag, snapshot) || mutated
        }

        if (mutated) {
            writeChunkNbt(chunkDirectory, chunkX, chunkZ, chunkRoot)
        }
    }

    private fun mergeInventorySnapshotIntoTileEntity(tileEntityTag: NBTTagCompound, snapshot: ObservedInventorySnapshot): Boolean {
        if (snapshot.items.isEmpty() && !snapshot.explicitlyObserved) {
            return false
        }

        val serializedItems = NBTTagList()
        snapshot.items.entries
            .sortedBy { it.key }
            .forEach { (slotIndex, itemTag) ->
                val entry = itemTag.copy() as NBTTagCompound
                entry.setByte("Slot", slotIndex.toByte())
                serializedItems.appendTag(entry)
            }

        val preferredKeys = listOf("Items", "items", "Inventory", "inventory", "inv")
        val targetKey = preferredKeys.firstOrNull { tileEntityTag.hasKey(it, 9) }
        if (targetKey != null) {
            tileEntityTag.setTag(targetKey, serializedItems)
            tileEntityTag.setTag("Items", serializedItems.copy())
            return true
        }

        tileEntityTag.setTag("Items", serializedItems)
        tileEntityTag.setTag("inv", serializedItems.copy())
        return true
    }

    private fun mergeTileEntitySnapshotIntoTag(tileEntityTag: NBTTagCompound, liveSnapshot: NBTTagCompound): Boolean {
        var mutated = false
        @Suppress("UNCHECKED_CAST")
        val keys = liveSnapshot.func_150296_c() as Set<String>

        keys.forEach { key ->
            val sourceTag = liveSnapshot.getTag(key) ?: return@forEach
            val existingTag = tileEntityTag.getTag(key)
            if (existingTag != sourceTag) {
                tileEntityTag.setTag(key, sourceTag.copy())
                mutated = true
            }
        }

        return mutated
    }

    private fun readChunkNbt(chunkDirectory: File, chunkX: Int, chunkZ: Int): NBTTagCompound? {
        val input = RegionFileCache.getChunkInputStream(chunkDirectory, chunkX, chunkZ) ?: return null
        return input.use(CompressedStreamTools::read)
    }

    private fun writeChunkNbt(chunkDirectory: File, chunkX: Int, chunkZ: Int, chunkRoot: NBTTagCompound) {
        val output = RegionFileCache.getChunkOutputStream(chunkDirectory, chunkX, chunkZ) ?: return
        output.use { CompressedStreamTools.write(chunkRoot, it) }
    }

    private fun initializeKnownChunks(session: BackupSession) {
        val knownChunks = loadKnownChunksFromDisk(session.rootDirectory)
        knownChunks.forEach { (dimensionId, chunkKeys) ->
            if (chunkKeys.isEmpty()) {
                return@forEach
            }

            session.savedChunksByDimension[dimensionId] = chunkKeys.toMutableSet()
            session.previouslySavedChunksByDimension[dimensionId] = chunkKeys.toMutableSet()
            session.dimensionStats[dimensionId] = DimensionStats(
                dimensionId = dimensionId,
                folder = resolveDimensionFolderName(dimensionId),
                firstSeenEpochMillis = System.currentTimeMillis(),
                lastUpdatedEpochMillis = System.currentTimeMillis(),
                chunkCount = chunkKeys.size
            )
        }
    }

    private fun loadKnownChunksFromDisk(rootDirectory: File): Map<Int, Set<Long>> {
        val knownChunks = linkedMapOf<Int, Set<Long>>()

        scanDimensionChunks(rootDirectory, SAFE_OVERWORLD_DIMENSION_ID)?.also {
            knownChunks[SAFE_OVERWORLD_DIMENSION_ID] = it
        }

        rootDirectory.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("DIM") }
            ?.forEach { dimensionDirectory ->
                val dimensionId = dimensionDirectory.name.removePrefix("DIM").toIntOrNull() ?: return@forEach
                scanDimensionChunks(dimensionDirectory, dimensionId)?.also {
                    knownChunks[dimensionId] = it
                }
            }

        return knownChunks
    }

    private fun scanDimensionChunks(dimensionDirectory: File, dimensionId: Int): Set<Long>? {
        val regionDirectory = File(dimensionDirectory, "region")
        val regionFiles = regionDirectory.listFiles { file ->
            file.isFile && file.name.startsWith("r.") && file.name.endsWith(".mca")
        } ?: return null

        val chunkKeys = linkedSetOf<Long>()

        regionFiles.forEach { regionFilePath ->
            val regionCoordinates = parseRegionCoordinates(regionFilePath.name) ?: return@forEach
            val regionFile = RegionFile(regionFilePath)

            try {
                for (localChunkX in 0 until 32) {
                    for (localChunkZ in 0 until 32) {
                        if (!regionFile.chunkExists(localChunkX, localChunkZ)) {
                            continue
                        }

                        val globalChunkX = regionCoordinates.first * 32 + localChunkX
                        val globalChunkZ = regionCoordinates.second * 32 + localChunkZ
                        chunkKeys.add(toChunkKey(globalChunkX, globalChunkZ))
                    }
                }
            } finally {
                runCatching(regionFile::close).onFailure {
                    ClientWorldBackupMod.logger.warn(
                        "Failed to close region file {} while scanning known chunks for dimension {}",
                        regionFilePath.name,
                        dimensionId,
                        it
                    )
                }
            }
        }

        return chunkKeys
    }

    private fun parseRegionCoordinates(fileName: String): Pair<Int, Int>? {
        val match = REGION_FILE_NAME_REGEX.matchEntire(fileName) ?: return null
        val regionX = match.groupValues[1].toIntOrNull() ?: return null
        val regionZ = match.groupValues[2].toIntOrNull() ?: return null
        return regionX to regionZ
    }

    private fun resolveInitialSpawn(
        world: WorldClient,
        player: EntityClientPlayerMP,
        session: BackupSession
    ): SafeSpawn {
        if (world.provider.dimensionId == SAFE_OVERWORLD_DIMENSION_ID) {
            return SafeSpawn(
                x = MathHelper.floor_double(player.posX),
                y = MathHelper.floor_double(player.posY.coerceIn(1.0, 255.0)),
                z = MathHelper.floor_double(player.posZ)
            )
        }

        val savedOverworldChunk = session.savedChunksByDimension[SAFE_OVERWORLD_DIMENSION_ID]
            ?.firstOrNull()

        if (savedOverworldChunk != null) {
            val chunkX = chunkXFromKey(savedOverworldChunk)
            val chunkZ = chunkZFromKey(savedOverworldChunk)
            return SafeSpawn(
                x = chunkX * 16 + 8,
                y = SAFE_FALLBACK_SPAWN_Y,
                z = chunkZ * 16 + 8
            )
        }

        return SafeSpawn(x = 0, y = SAFE_FALLBACK_SPAWN_Y, z = 0)
    }

    private fun applySafeSingleplayerPlayerState(playerTag: NBTTagCompound, spawn: SafeSpawn) {
        playerTag.setInteger("Dimension", SAFE_OVERWORLD_DIMENSION_ID)
        playerTag.setTag("Pos", doubleList(spawn.x + 0.5, spawn.y.toDouble(), spawn.z + 0.5))
        playerTag.setTag("Motion", doubleList(0.0, 0.0, 0.0))
        playerTag.setTag("Rotation", floatList(0.0f, 0.0f))
        playerTag.setInteger("SpawnX", spawn.x)
        playerTag.setInteger("SpawnY", spawn.y)
        playerTag.setInteger("SpawnZ", spawn.z)
        playerTag.setBoolean("SpawnForced", true)
        playerTag.setBoolean("Sleeping", false)
        playerTag.setShort("SleepTimer", 0)
    }

    private fun doubleList(vararg values: Double): NBTTagList {
        val list = NBTTagList()
        values.forEach { list.appendTag(NBTTagDouble(it)) }
        return list
    }

    private fun floatList(vararg values: Float): NBTTagList {
        val list = NBTTagList()
        values.forEach { list.appendTag(NBTTagFloat(it)) }
        return list
    }

    private fun toChunkKey(chunkX: Int, chunkZ: Int): Long =
        (chunkX.toLong() shl 32) xor (chunkZ.toLong() and 0xFFFFFFFFL)

    private fun chunkXFromKey(chunkKey: Long): Int = (chunkKey shr 32).toInt()

    private fun chunkZFromKey(chunkKey: Long): Int = chunkKey.toInt()
}

private data class BackupSession(
    val key: String,
    val saveName: String,
    val rootDirectory: File,
    val source: BackupSourceDescriptor,
    val startedAtEpochMillis: Long,
    val standaloneSaveNamesByDimension: MutableMap<Int, String> = linkedMapOf(),
    val savedChunksByDimension: MutableMap<Int, MutableSet<Long>> = linkedMapOf(),
    val previouslySavedChunksByDimension: MutableMap<Int, MutableSet<Long>> = linkedMapOf(),
    val sessionSavedChunksByDimension: MutableMap<Int, MutableSet<Long>> = linkedMapOf(),
    val dimensionStats: MutableMap<Int, DimensionStats> = linkedMapOf()
) {
    fun totalUniqueChunks(): Int = savedChunksByDimension.values.sumOf(Set<Long>::size)
}

private data class InventoryLocation(
    val dimensionId: Int,
    val x: Int,
    val y: Int,
    val z: Int
)

private data class ObservedInventorySnapshot(
    val items: Map<Int, NBTTagCompound>,
    val explicitlyObserved: Boolean
)


private data class BackupSourceDescriptor(
    val key: String,
    val type: String,
    val displayName: String,
    val address: String?,
    val integratedServer: Boolean
)

private data class DimensionStats(
    val dimensionId: Int,
    val folder: String,
    val firstSeenEpochMillis: Long,
    var lastUpdatedEpochMillis: Long,
    var chunkCount: Int = 0,
    var playerChunkX: Int? = null,
    var playerChunkZ: Int? = null
)

private data class SafeSpawn(
    val x: Int,
    val y: Int,
    val z: Int
)

@Serializable
private data class BackupManifest(
    val schemaVersion: Int,
    val modVersion: String,
    val saveName: String,
    val startedAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val source: BackupManifestSource,
    val player: BackupPlayerState,
    val worldState: BackupWorldState,
    val captures: List<BackupDimensionCapture>,
    val warnings: List<String>
)

@Serializable
private data class BackupManifestSource(
    val key: String,
    val type: String,
    val displayName: String,
    val address: String?,
    val integratedServer: Boolean
)

@Serializable
private data class BackupPlayerState(
    val dimensionId: Int,
    val posX: Double,
    val posY: Double,
    val posZ: Double,
    val chunkX: Int,
    val chunkZ: Int
)

@Serializable
private data class BackupWorldState(
    val totalWorldTime: Long,
    val dayTime: Long,
    val isRaining: Boolean,
    val isThundering: Boolean
)

@Serializable
private data class BackupDimensionCapture(
    val dimensionId: Int,
    val folder: String,
    val chunkCount: Int,
    val firstSeenEpochMillis: Long,
    val lastUpdatedEpochMillis: Long,
    val playerChunkX: Int?,
    val playerChunkZ: Int?
)

private val REGION_FILE_NAME_REGEX = Regex("r\\.(-?\\d+)\\.(-?\\d+)\\.mca")



