/*? if <26 {*/
/*package io.github.fopwoc.mods.framework.ui.compose.minecraft.render

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import io.github.fopwoc.mods.framework.log.logger
import io.github.fopwoc.mods.framework.minecraft.Identifier
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuCanvasFrame
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuImage
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import kotlin.math.ceil
import kotlin.math.sqrt
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.texture.DynamicTexture

// The GPU canvas of one node on 1.21.1, like ModernGpuImageAtlas on 26.x: its equally sized images
// live in the cells of one dynamic texture, so every quad of a frame shares a texture and batches.
// Changed images are uploaded into free or least recently used cells.
internal class LegacyGpuImageAtlas {
    private val logger = logger<LegacyGpuImageAtlas>()
    private val cells = LinkedHashMap<GpuImage, Int>(64, 0.75f, true)
    private var texture: DynamicTexture? = null
    private var location: Identifier? = null
    private var staging: NativeImage? = null
    private var imageWidth = 0
    private var imageHeight = 0
    private var columns = 0
    private var capacity = 0
    private var warnedOverflow = false

    fun draw(graphics: GuiGraphics, bounds: Rect, frame: GpuCanvasFrame) {
        if (bounds.isEmpty() || frame.draws.isEmpty()) return
        val first = frame.draws.first().image
        val images = frame.draws.mapTo(LinkedHashSet()) { it.image }
        ensureAtlas(first.width, first.height, images.size)
        val atlas = location ?: return
        val drawable = images.take(capacity).toSet()
        if (drawable.size < images.size && !warnedOverflow) {
            warnedOverflow = true
            logger.warn(
                "GPU canvas needs {} images of {}x{}; the atlas holds {}",
                images.size,
                imageWidth,
                imageHeight,
                capacity,
            )
        }
        drawable.forEach { image -> if (image !in cells) uploadInto(image, freeCell(drawable)) }
        val atlasWidth = columns * imageWidth
        val atlasHeight = rows() * imageHeight
        val pose = graphics.pose()
        // Images carry their own transparency; blit leaves blending as it finds it.
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        frame.draws.forEach { draw ->
            val cell = cells[draw.image] ?: return@forEach
            // Fractional placement through the pose keeps panning smooth at any GUI scale.
            pose.pushPose()
            pose.translate(
                bounds.x + draw.x + draw.width / 2,
                bounds.y + draw.y + draw.height / 2,
                0f,
            )
            pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(draw.rotation))
            pose.translate(-draw.width / 2, -draw.height / 2, 0f)
            pose.scale(draw.width / imageWidth, draw.height / imageHeight, 1f)
            RenderSystem.setShaderColor(1f, 1f, 1f, draw.alpha)
            graphics.blit(
                atlas,
                0,
                0,
                imageWidth,
                imageHeight,
                ((cell % columns) * imageWidth).toFloat(),
                ((cell / columns) * imageHeight).toFloat(),
                imageWidth,
                imageHeight,
                atlasWidth,
                atlasHeight,
            )
            pose.popPose()
        }
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
        RenderSystem.disableBlend()
    }

    fun dispose() {
        location?.let(Minecraft.getInstance().textureManager::release)
        staging?.close()
        location = null
        texture = null
        staging = null
        cells.clear()
    }

    private fun rows(): Int = (capacity + columns - 1) / columns

    // Grows (never shrinks) the atlas to fit the needed images of this size, within GL limits.
    private fun ensureAtlas(width: Int, height: Int, needed: Int) {
        if (texture != null && width == imageWidth && height == imageHeight && needed <= capacity)
            return
        val maxSize = RenderSystem.maxSupportedTextureSize()
        val maxColumns = (maxSize / width).coerceAtLeast(1)
        val maxCapacity = maxColumns * (maxSize / height).coerceAtLeast(1)
        val wanted =
            maxOf(
                    needed,
                    if (width == imageWidth && height == imageHeight) capacity * 2 else needed,
                )
                .coerceAtMost(maxCapacity)
        dispose()
        imageWidth = width
        imageHeight = height
        columns = ceil(sqrt(wanted.toDouble())).toInt().coerceIn(1, maxColumns)
        capacity = wanted
        val atlas = DynamicTexture(columns * width, rows() * height, true)
        texture = atlas
        location = Minecraft.getInstance().textureManager.register("knhcore_gpu_canvas", atlas)
        staging = NativeImage(width, height, false)
    }

    // A free cell, or the least recently drawn one that this frame does not need.
    private fun freeCell(needed: Set<GpuImage>): Int {
        if (cells.size < capacity) return (0 until capacity).first { it !in cells.values }
        val victim = cells.keys.first { it !in needed }
        return checkNotNull(cells.remove(victim))
    }

    // NativeImage holds pixels as little-endian ABGR words; GpuImage bytes are R, G, B, A.
    private fun uploadInto(image: GpuImage, cell: Int) {
        val pixels = checkNotNull(staging)
        val rgba = image.pixels
        for (index in 0 until imageWidth * imageHeight) {
            val offset = index * 4
            val abgr =
                (rgba[offset + 3].toInt() and 0xFF shl 24) or
                    (rgba[offset + 2].toInt() and 0xFF shl 16) or
                    (rgba[offset + 1].toInt() and 0xFF shl 8) or
                    (rgba[offset].toInt() and 0xFF)
            pixels.setPixelRGBA(index % imageWidth, index / imageWidth, abgr)
        }
        checkNotNull(texture).bind()
        pixels.upload(
            0,
            (cell % columns) * imageWidth,
            (cell / columns) * imageHeight,
            0,
            0,
            imageWidth,
            imageHeight,
            false,
            false,
        )
        cells[image] = cell
    }
}
*//*?}*/
