/*? if <26 {*/
/*package io.github.fopwoc.mods.framework.ui.compose.minecraft.render

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexBuffer
import com.mojang.blaze3d.vertex.VertexFormat
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuMesh
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.GameRenderer
import org.joml.Matrix4f

// The mesh of one GPU canvas on 1.21.1, like ModernGpuMeshRenderer on 26.x: meshes are immutable,
// so each one is uploaded once into a static vertex buffer and redrawn through the current pose
// and scissor until the canvas hands over another.
internal class LegacyGpuMeshRenderer {
    private var uploaded: GpuMesh? = null
    private var buffer: VertexBuffer? = null
    private val modelView = Matrix4f()

    fun draw(graphics: GuiGraphics, bounds: Rect, mesh: GpuMesh) {
        if (bounds.isEmpty() || mesh.vertexCount == 0) return
        val vertices = upload(mesh)
        val shader = GameRenderer.getPositionColorShader() ?: return
        // Batched fills drawn before the canvas must land beneath the mesh.
        graphics.flush()
        modelView
            .set(RenderSystem.getModelViewMatrix())
            .mul(graphics.pose().last().pose())
            .translate(bounds.x.toFloat(), bounds.y.toFloat(), 0f)
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.disableDepthTest()
        vertices.bind()
        vertices.drawWithShader(modelView, RenderSystem.getProjectionMatrix(), shader)
        VertexBuffer.unbind()
        RenderSystem.enableDepthTest()
        RenderSystem.disableBlend()
    }

    fun dispose() {
        buffer?.close()
        buffer = null
        uploaded = null
    }

    private fun upload(mesh: GpuMesh): VertexBuffer {
        val vertices = buffer ?: VertexBuffer(VertexBuffer.Usage.STATIC).also { buffer = it }
        if (uploaded === mesh) return vertices
        val format = DefaultVertexFormat.POSITION_COLOR
        ByteBufferBuilder(mesh.vertexCount * format.vertexSize).use { bytes ->
            val builder = BufferBuilder(bytes, VertexFormat.Mode.TRIANGLES, format)
            for (index in 0 until mesh.vertexCount) {
                builder
                    .addVertex(mesh.positions[index * 2], mesh.positions[index * 2 + 1], 0f)
                    .setColor(mesh.colors[index])
            }
            vertices.bind()
            vertices.upload(builder.buildOrThrow())
            VertexBuffer.unbind()
        }
        uploaded = mesh
        return vertices
    }
}
*//*?}*/
