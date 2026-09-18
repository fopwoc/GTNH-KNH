package io.github.fopwoc.mods.framework.ui.compose.canvas

/** Thread-safe ARGB canvas with tile-sized dirty snapshots for incremental texture uploads. */
class PixelCanvas(val width: Int, val height: Int, val tileSize: Int = 16) {
    init {
        require(width > 0 && height > 0)
        require(width.toLong() * height <= Int.MAX_VALUE)
        require(tileSize > 0)
    }

    private val pixels = IntArray(width * height)
    private val tileColumns = (width - 1) / tileSize + 1
    private val tileRows = (height - 1) / tileSize + 1
    private val dirtyTiles = BooleanArray(tileColumns * tileRows) { true }
    private val tileRevisions = LongArray(dirtyTiles.size)
    private val lock = Any()
    private var revision = 0L
    private var dirtyCount = dirtyTiles.size
    private var nextDirtyTile = 0

    /** Copies [region] from row-major [argb]. The caller may reuse the array when this returns. */
    fun write(region: PixelRegion, argb: IntArray, offset: Int = 0, stride: Int = region.width) {
        checkRegion(region)
        require(offset >= 0 && stride >= region.width)
        require(offset.toLong() + (region.height - 1L) * stride + region.width <= argb.size)
        synchronized(lock) {
            repeat(region.height) { row ->
                argb.copyInto(
                    destination = pixels,
                    destinationOffset = (region.top + row) * width + region.left,
                    startIndex = offset + row * stride,
                    endIndex = offset + row * stride + region.width,
                )
            }
            markDirty(region)
        }
    }

    fun fill(region: PixelRegion, argb: Int) {
        checkRegion(region)
        synchronized(lock) {
            repeat(region.height) { row ->
                pixels.fill(
                    argb,
                    (region.top + row) * width + region.left,
                    (region.top + row) * width + region.right,
                )
            }
            markDirty(region)
        }
    }

    /** Returns one dirty tile. A failed upload can retry without losing the tile. */
    fun snapshotDirty(): PixelSnapshot? =
        synchronized(lock) {
            if (dirtyCount == 0) return@synchronized null
            while (!dirtyTiles[nextDirtyTile]) nextDirtyTile++
            val tileIndex = nextDirtyTile
            val left = (tileIndex % tileColumns) * tileSize
            val top = (tileIndex / tileColumns) * tileSize
            val region =
                PixelRegion(left, top, minOf(tileSize, width - left), minOf(tileSize, height - top))
            val copy = IntArray(region.width * region.height)
            repeat(region.height) { row ->
                pixels.copyInto(
                    destination = copy,
                    destinationOffset = row * region.width,
                    startIndex = (region.top + row) * width + region.left,
                    endIndex = (region.top + row) * width + region.right,
                )
            }
            PixelSnapshot(this, region, copy, tileRevisions[tileIndex], tileIndex)
        }

    /** Call after a successful upload. Writes racing with the upload keep this tile dirty. */
    fun acknowledge(snapshot: PixelSnapshot) {
        require(snapshot.owner === this)
        synchronized(lock) {
            if (snapshot.revision == tileRevisions[snapshot.tileIndex]) {
                if (dirtyTiles[snapshot.tileIndex]) {
                    dirtyTiles[snapshot.tileIndex] = false
                    dirtyCount--
                }
            }
        }
    }

    private fun checkRegion(region: PixelRegion) {
        require(region.right <= width && region.bottom <= height)
    }

    private fun markDirty(region: PixelRegion) {
        revision++
        for (tileY in region.top / tileSize..(region.bottom - 1) / tileSize) {
            for (tileX in region.left / tileSize..(region.right - 1) / tileSize) {
                val index = tileY * tileColumns + tileX
                if (!dirtyTiles[index]) dirtyCount++
                dirtyTiles[index] = true
                tileRevisions[index] = revision
                if (index < nextDirtyTile) nextDirtyTile = index
            }
        }
    }
}
