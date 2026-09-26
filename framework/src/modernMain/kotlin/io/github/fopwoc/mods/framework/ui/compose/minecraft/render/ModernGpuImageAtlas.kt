/*? if >=26 {*/
// Not ported to 1.21.1 yet: the whole file exists only from 26.x.
package io.github.fopwoc.mods.framework.ui.compose.minecraft.render

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import io.github.fopwoc.mods.framework.log.logger
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuCanvasFrame
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuImage
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.math.sqrt
import net.minecraft.client.gui.GuiGraphicsExtractor

/**
 * The GPU canvas of one node: its equally sized images live in the cells of one atlas texture, so
 * every quad of a frame shares a texture and the GUI renderer batches them into one draw. Changed
 * images are uploaded into free or least recently used cells through the device API, which works
 * the same on OpenGL and Vulkan.
 */
internal class ModernGpuImageAtlas {
    private val logger = logger<ModernGpuImageAtlas>()
    private val cells = LinkedHashMap<GpuImage, Int>(64, 0.75f, true)
    private var texture: GpuTexture? = null
    private var view: GpuTextureView? = null
    private var imageWidth = 0
    private var imageHeight = 0
    private var columns = 0
    private var capacity = 0
    private var upload: ByteBuffer? = null
    private var warnedOverflow = false

    fun draw(graphics: GuiGraphicsExtractor, bounds: Rect, frame: GpuCanvasFrame) {
        if (bounds.isEmpty() || frame.draws.isEmpty()) return
        val first = frame.draws.first().image
        val images = frame.draws.mapTo(LinkedHashSet()) { it.image }
        ensureAtlas(first.width, first.height, images.size)
        val atlasView = view ?: return
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

        val sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
        val atlasWidth = (columns * imageWidth).toFloat()
        val atlasHeight = (rows() * imageHeight).toFloat()
        val pose = graphics.pose()
        frame.draws.forEach { draw ->
            val cell = cells[draw.image] ?: return@forEach
            val u0 = (cell % columns) * imageWidth / atlasWidth
            val v0 = (cell / columns) * imageHeight / atlasHeight
            // Fractional placement through the pose keeps panning smooth at any GUI scale.
            pose.pushMatrix()
            pose.translate(bounds.x + draw.x, bounds.y + draw.y)
            pose.scale(draw.width / imageWidth, draw.height / imageHeight)
            graphics.blit(
                atlasView,
                sampler,
                0,
                0,
                imageWidth,
                imageHeight,
                u0,
                u0 + imageWidth / atlasWidth,
                v0,
                v0 + imageHeight / atlasHeight,
            )
            pose.popMatrix()
        }
    }

    fun dispose() {
        view?.close()
        texture?.close()
        view = null
        texture = null
        cells.clear()
    }

    private fun rows(): Int = (capacity + columns - 1) / columns

    /**
     * Grows (never shrinks) the atlas to fit [needed] images of this size, within device limits.
     */
    private fun ensureAtlas(width: Int, height: Int, needed: Int) {
        if (texture != null && width == imageWidth && height == imageHeight && needed <= capacity)
            return
        val device = RenderSystem.getDevice()
        val maxSize = device.deviceInfo.limits().maxTextureSizeForFormat(GpuFormat.RGBA8_UNORM)
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
        texture =
            device.createTexture(
                "KNH Core GPU canvas",
                GpuTexture.USAGE_COPY_DST or GpuTexture.USAGE_TEXTURE_BINDING,
                GpuFormat.RGBA8_UNORM,
                columns * width,
                rows() * height,
                1,
                1,
            )
        view = device.createTextureView(texture!!)
        upload = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
    }

    /** A free cell, or the least recently drawn one that this frame does not need. */
    private fun freeCell(needed: Set<GpuImage>): Int {
        if (cells.size < capacity) return (0 until capacity).first { it !in cells.values }
        val victim = cells.keys.first { it !in needed }
        return checkNotNull(cells.remove(victim))
    }

    private fun uploadInto(image: GpuImage, cell: Int) {
        val buffer = checkNotNull(upload).clear()
        buffer.put(image.pixels).flip()
        RenderSystem.getDevice()
            .createCommandEncoder()
            .writeToTexture(
                checkNotNull(texture),
                buffer,
                0,
                0,
                (cell % columns) * imageWidth,
                (cell / columns) * imageHeight,
                imageWidth,
                imageHeight,
            )
        cells[image] = cell
    }
}
/*?}*/
