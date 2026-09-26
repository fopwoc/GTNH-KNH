/*? if >=26 {*/
// 26.x only: the GPU device API; LegacyGpuImageAtlas is the 1.21.1 atlas.
package io.github.fopwoc.mods.framework.ui.compose.minecraft.render

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuSampler
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import io.github.fopwoc.mods.framework.ModMetadata
import io.github.fopwoc.mods.framework.log.logger
import io.github.fopwoc.mods.framework.minecraft.Identifier
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuCanvasFrame
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuImage
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.texture.AbstractTexture

/**
 * The GPU canvas of one node: its equally sized images live in the cells of one atlas texture, so
 * every quad of a frame shares a texture and the GUI renderer batches them into one draw. Changed
 * images are uploaded into free or least recently used cells through the device API, which works
 * the same on OpenGL and Vulkan. The atlas is registered as a texture under its own id, since only
 * the id blits take a colour, which carries each draw's alpha.
 */
internal class ModernGpuImageAtlas {
    private val logger = logger<ModernGpuImageAtlas>()
    private val cells = LinkedHashMap<GpuImage, Int>(64, 0.75f, true)
    private var texture: GpuTexture? = null
    private var view: GpuTextureView? = null
    private val id =
        Identifier.fromNamespaceAndPath(ModMetadata.MOD_ID, "gpu_canvas/${ids.incrementAndGet()}")
    private var registered = false
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
        if (view == null) return
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
        frame.draws.forEach { draw ->
            val cell = cells[draw.image] ?: return@forEach
            // Fractional placement through the pose keeps panning smooth at any GUI scale.
            pose.pushMatrix()
            pose.translate(bounds.x + draw.x + draw.width / 2, bounds.y + draw.y + draw.height / 2)
            pose.rotate(Math.toRadians(draw.rotation.toDouble()).toFloat())
            pose.translate(-draw.width / 2, -draw.height / 2)
            pose.scale(draw.width / imageWidth, draw.height / imageHeight)
            graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                id,
                0,
                0,
                ((cell % columns) * imageWidth).toFloat(),
                ((cell / columns) * imageHeight).toFloat(),
                imageWidth,
                imageHeight,
                atlasWidth,
                atlasHeight,
                ((draw.alpha * OPAQUE).roundToInt() shl ALPHA_SHIFT) or WHITE,
            )
            pose.popMatrix()
        }
    }

    fun dispose() {
        if (registered) Minecraft.getInstance().textureManager.release(id)
        registered = false
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
        /*? if >=26.2 {*/
        val maxSize =
            device.deviceInfo
                .limits()
                .maxTextureSizeForFormat(com.mojang.blaze3d.GpuFormat.RGBA8_UNORM)
        /*?} else {*/
        /*val maxSize = device.maxTextureSize
         */
        /*?}*/
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
                /*? if >=26.2 {*/
                com.mojang.blaze3d.GpuFormat.RGBA8_UNORM,
                /*?} else {*/
                /*com.mojang.blaze3d.textures.TextureFormat.RGBA8,
                 */
                /*?}*/
                columns * width,
                rows() * height,
                1,
                1,
            )
        view = device.createTextureView(texture!!)
        Minecraft.getInstance()
            .textureManager
            .register(
                id,
                AtlasTexture(
                    texture!!,
                    view!!,
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST),
                ),
            )
        registered = true
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
                /*? if <26.2 {*/
                /*com.mojang.blaze3d.platform.NativeImage.Format.RGBA,
                 */
                /*?}*/
                0,
                0,
                (cell % columns) * imageWidth,
                (cell / columns) * imageHeight,
                imageWidth,
                imageHeight,
            )
        cells[image] = cell
    }

    /** Hands the atlas to the texture manager; the atlas keeps owning the GPU objects. */
    private class AtlasTexture(
        texture: GpuTexture,
        view: GpuTextureView,
        sampler: GpuSampler,
    ) : AbstractTexture() {
        init {
            this.texture = texture
            textureView = view
            this.sampler = sampler
        }

        override fun close() = Unit
    }

    private companion object {
        val ids = AtomicInteger()
        const val OPAQUE = 255
        const val ALPHA_SHIFT = 24
        const val WHITE = 0xFFFFFF
    }
}
/*?}*/
