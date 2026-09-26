/*? if <26 {*/
/*package io.github.fopwoc.mods.framework.render

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.LightTexture
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf

// World overlay shapes on Minecraft 1.21.1, like ModernWorldShapes' gizmos on 26.x: recorded during
// a frame, then drawn with OpenGL state after the level. Depth-tested shapes go first and do not
// write depth; the rest draw over everything. Client thread only.
object LegacyWorldShapes {
    private class Quad(val a: Vec3, val b: Vec3, val c: Vec3, val d: Vec3, val argb: Int)

    private class Line(val from: Vec3, val to: Vec3, val argb: Int, val width: Float)

    private class Text(val text: String, val at: Vec3, val argb: Int, val scale: Float)

    private class Layer : WorldPrimitives {
        val quads = ArrayList<Quad>()
        val lines = ArrayList<Line>()

        override fun addQuad(a: Vec3, b: Vec3, c: Vec3, d: Vec3, argb: Int) {
            quads += Quad(a, b, c, d, argb)
        }

        override fun addLine(from: Vec3, to: Vec3, argb: Int, width: Float) {
            lines += Line(from, to, argb, width)
        }
    }

    private val depthTested = Layer()
    private val onTop = Layer()
    private val texts = ArrayList<Text>()

    fun line(from: Vec3, to: Vec3, argb: Int, width: Float, onTop: Boolean) =
        layer(onTop).addLine(from, to, argb, width)

    fun box(box: AABB, argb: Int, onTop: Boolean) {
        val layer = layer(onTop)
        faces(box).forEach { (a, b, c, d) -> layer.addQuad(a, b, c, d, argb) }
    }

    fun boxOutline(box: AABB, argb: Int, width: Float, onTop: Boolean) {
        val layer = layer(onTop)
        edges(box).forEach { (from, to) -> layer.addLine(from, to, argb, width) }
    }

    // Camera-facing text centred on `at`, over everything; `scale` is world units per pixel.
    fun text(text: String, at: Vec3, argb: Int, scale: Float) {
        texts += Text(text, at, argb, scale)
    }

    // A shape of quads and lines; nothing fades here, so the alpha multiplier is 1.
    fun custom(onTop: Boolean, draw: WorldPrimitives.(alphaMultiplier: Float) -> Unit) =
        layer(onTop).draw(1f)

    internal fun frame(camera: Camera, draw: () -> Unit) {
        try {
            draw()
            render(camera)
        } finally {
            listOf(depthTested, onTop).forEach {
                it.quads.clear()
                it.lines.clear()
            }
            texts.clear()
        }
    }

    private fun layer(onTop: Boolean) = if (onTop) this.onTop else depthTested

    // Vertices are relative to the eye, in doubles until then, so they stay exact far from the
    // origin; the model-view matrix holds only the camera's rotation, as vanilla sets it.
    private fun render(camera: Camera) {
        val eye = camera.position
        val modelView = RenderSystem.getModelViewStack()
        modelView.pushMatrix()
        modelView.rotation(camera.rotation().conjugate(Quaternionf()))
        RenderSystem.applyModelViewMatrix()
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.disableCull()
        RenderSystem.depthMask(false)
        RenderSystem.enableDepthTest()
        draw(depthTested, eye)
        RenderSystem.disableDepthTest()
        draw(onTop, eye)
        drawTexts(camera, eye)
        RenderSystem.enableDepthTest()
        RenderSystem.depthMask(true)
        RenderSystem.enableCull()
        RenderSystem.disableBlend()
        RenderSystem.lineWidth(1f)
        modelView.popMatrix()
        RenderSystem.applyModelViewMatrix()
    }

    private fun draw(layer: Layer, eye: Vec3) {
        if (layer.quads.isNotEmpty()) {
            RenderSystem.setShader(GameRenderer::getPositionColorShader)
            val buffer =
                Tesselator.getInstance()
                    .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)
            layer.quads.forEach { quad ->
                for (corner in arrayOf(quad.a, quad.b, quad.c, quad.d))
                    buffer.vertex(corner, eye).setColor(quad.argb)
            }
            BufferUploader.drawWithShader(buffer.buildOrThrow())
        }
        layer.lines.groupBy { it.width }.forEach { (width, lines) ->
            RenderSystem.setShader(GameRenderer::getRendertypeLinesShader)
            RenderSystem.lineWidth(width)
            val buffer =
                Tesselator.getInstance()
                    .begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL)
            lines.forEach { line ->
                val normal = line.to.subtract(line.from).normalize()
                for (end in arrayOf(line.from, line.to))
                    buffer
                        .vertex(end, eye)
                        .setColor(line.argb)
                        .setNormal(normal.x.toFloat(), normal.y.toFloat(), normal.z.toFloat())
            }
            BufferUploader.drawWithShader(buffer.buildOrThrow())
        }
    }

    // Like vanilla's name tags: turned towards the camera, y down in font pixels.
    private fun drawTexts(camera: Camera, eye: Vec3) {
        if (texts.isEmpty()) return
        val minecraft = Minecraft.getInstance()
        val font = minecraft.font
        val buffers = minecraft.renderBuffers().bufferSource()
        val pose = PoseStack()
        texts.forEach { text ->
            pose.pushPose()
            pose.translate(text.at.x - eye.x, text.at.y - eye.y, text.at.z - eye.z)
            pose.mulPose(camera.rotation())
            pose.scale(text.scale, -text.scale, text.scale)
            font.drawInBatch(
                text.text,
                -font.width(text.text) / 2f,
                -font.lineHeight / 2f,
                text.argb,
                false,
                pose.last().pose(),
                buffers,
                Font.DisplayMode.SEE_THROUGH,
                0,
                LightTexture.FULL_BRIGHT,
            )
            pose.popPose()
        }
        buffers.endBatch()
    }

    private fun BufferBuilder.vertex(point: Vec3, eye: Vec3): VertexConsumer =
        addVertex(
            (point.x - eye.x).toFloat(),
            (point.y - eye.y).toFloat(),
            (point.z - eye.z).toFloat(),
        )

    private fun faces(box: AABB): List<List<Vec3>> {
        val (x0, y0, z0) = Triple(box.minX, box.minY, box.minZ)
        val (x1, y1, z1) = Triple(box.maxX, box.maxY, box.maxZ)
        return listOf(
            listOf(Vec3(x0, y0, z0), Vec3(x1, y0, z0), Vec3(x1, y0, z1), Vec3(x0, y0, z1)),
            listOf(Vec3(x0, y1, z0), Vec3(x0, y1, z1), Vec3(x1, y1, z1), Vec3(x1, y1, z0)),
            listOf(Vec3(x0, y0, z0), Vec3(x0, y1, z0), Vec3(x1, y1, z0), Vec3(x1, y0, z0)),
            listOf(Vec3(x0, y0, z1), Vec3(x1, y0, z1), Vec3(x1, y1, z1), Vec3(x0, y1, z1)),
            listOf(Vec3(x0, y0, z0), Vec3(x0, y0, z1), Vec3(x0, y1, z1), Vec3(x0, y1, z0)),
            listOf(Vec3(x1, y0, z0), Vec3(x1, y1, z0), Vec3(x1, y1, z1), Vec3(x1, y0, z1)),
        )
    }

    private fun edges(box: AABB): List<Pair<Vec3, Vec3>> {
        val corners =
            listOf(box.minX, box.maxX).flatMap { x ->
                listOf(box.minY, box.maxY).flatMap { y ->
                    listOf(box.minZ, box.maxZ).map { z -> Vec3(x, y, z) }
                }
            }
        // Corners differing in exactly one axis share an edge.
        return corners.indices.flatMap { i ->
            (i + 1 until corners.size)
                .filter { j -> Integer.bitCount(i xor j) == 1 }
                .map { j -> corners[i] to corners[j] }
        }
    }
}
*//*?}*/
