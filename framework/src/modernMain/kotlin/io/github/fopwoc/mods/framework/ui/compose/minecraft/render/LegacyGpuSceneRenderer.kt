/*? if <26 {*/
/*package io.github.fopwoc.mods.framework.ui.compose.minecraft.render

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexBuffer
import com.mojang.blaze3d.vertex.VertexFormat
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuScene
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuSceneMesh
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import java.util.Collections
import java.util.IdentityHashMap
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.GameRenderer
import org.joml.Matrix4f
import org.lwjgl.opengl.GL11

// The scene of one GPU canvas on 1.21.1, like GpuSceneRenderer on 1.7.10: each mesh lives in its
// own static vertex buffer while frames keep drawing it. The GUI matrices place local GUI x and y
// and the scene camera alone sets depth, tested against a depth buffer cleared inside the canvas's
// scissor before and after, so the scene neither hides behind the GUI nor hides what comes next.
// A faded scene first lays down depth alone, so only the nearest surface of each pixel blends.
internal class LegacyGpuSceneRenderer {
    private val buffers = IdentityHashMap<GpuSceneMesh, VertexBuffer>()
    private val gui = Matrix4f()
    private val local = Matrix4f()
    private val identity = Matrix4f()

    fun draw(graphics: GuiGraphics, bounds: Rect, scene: GpuScene?) {
        val meshes = scene?.meshes.orEmpty()
        release(meshes)
        if (scene == null || bounds.isEmpty() || meshes.isEmpty()) return
        val shader = GameRenderer.getPositionColorShader() ?: return
        // Batched fills drawn before the canvas must land beneath the scene.
        graphics.flush()
        gui.set(RenderSystem.getProjectionMatrix())
            .mul(RenderSystem.getModelViewMatrix())
            .mul(graphics.pose().last().pose())
            .translate(bounds.x.toFloat(), bounds.y.toFloat(), 0f)
        RenderSystem.enableDepthTest()
        RenderSystem.depthMask(true)
        RenderSystem.depthFunc(GL11.GL_LEQUAL)
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX)
        if (scene.alpha < 1f) {
            RenderSystem.disableBlend()
            RenderSystem.colorMask(false, false, false, false)
            drawMeshes(scene, shader)
            RenderSystem.colorMask(true, true, true, true)
            RenderSystem.enableBlend()
            RenderSystem.defaultBlendFunc()
        } else RenderSystem.disableBlend()
        RenderSystem.setShaderColor(1f, 1f, 1f, scene.alpha)
        drawMeshes(scene, shader)
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
        RenderSystem.disableBlend()
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX)
    }

    private fun drawMeshes(scene: GpuScene, shader: net.minecraft.client.renderer.ShaderInstance) {
        for (mesh in scene.meshes) {
            if (mesh.quadCount == 0) continue
            val rows = scene.camera.relativeTo(mesh.originX, mesh.originY, mesh.originZ)
            // Columns of the affine part: world x, y and z, then the offset.
            local
                .set(
                    rows[0], rows[4], 0f, 0f,
                    rows[1], rows[5], 0f, 0f,
                    rows[2], rows[6], 0f, 0f,
                    rows[3], rows[7], 0f, 1f,
                )
                .mulLocal(gui)
                .m02(rows[8])
                .m12(rows[9])
                .m22(rows[10])
                .m32(rows[11])
            val vertices = buffers.getOrPut(mesh) { upload(mesh) }
            vertices.bind()
            vertices.drawWithShader(identity, local, shader)
        }
        VertexBuffer.unbind()
    }

    fun dispose() {
        buffers.values.forEach(VertexBuffer::close)
        buffers.clear()
    }

    // Closes the buffers of meshes this frame no longer draws.
    private fun release(drawn: List<GpuSceneMesh>) {
        if (buffers.isEmpty()) return
        val keep = drawn.toCollection(Collections.newSetFromMap(IdentityHashMap()))
        val iterator = buffers.entries.iterator()
        while (iterator.hasNext()) {
            val (mesh, buffer) = iterator.next()
            if (mesh in keep) continue
            buffer.close()
            iterator.remove()
        }
    }

    private fun upload(mesh: GpuSceneMesh): VertexBuffer {
        val vertices = VertexBuffer(VertexBuffer.Usage.STATIC)
        val format = DefaultVertexFormat.POSITION_COLOR
        ByteBufferBuilder(mesh.vertexCount * format.vertexSize).use { bytes ->
            val builder = BufferBuilder(bytes, VertexFormat.Mode.QUADS, format)
            for (index in 0 until mesh.vertexCount) {
                builder
                    .addVertex(mesh.x(index), mesh.y(index), mesh.z(index))
                    .setColor(mesh.colors[index])
            }
            vertices.bind()
            vertices.upload(builder.buildOrThrow())
            VertexBuffer.unbind()
        }
        return vertices
    }
}
*//*?}*/
