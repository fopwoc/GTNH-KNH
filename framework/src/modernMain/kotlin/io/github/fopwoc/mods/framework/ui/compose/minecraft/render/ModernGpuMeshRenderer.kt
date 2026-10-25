/*? if >=26 {*/
// 26.x only: deferred GUI elements; LegacyGpuMeshRenderer draws meshes on 1.21.1.
package io.github.fopwoc.mods.framework.ui.compose.minecraft.render

import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.VertexConsumer
import io.github.fopwoc.mods.framework.ModMetadata
import io.github.fopwoc.mods.framework.minecraft.Identifier
import io.github.fopwoc.mods.framework.mixin.GuiGraphicsExtractorAccessor
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuMesh
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.state.gui.GuiElementRenderState
import org.joml.Matrix3x2f

/**
 * Submits a GPU canvas mesh as one GUI element. The GUI renderer lifts later elements whose bounds
 * intersect it to a higher layer, so the canvas images still land on top. Vertices are rebuilt from
 * the mesh arrays every frame, as the GUI renderer stages all vertices per frame.
 */
internal object ModernGpuMeshRenderer {
    /*? if >=26.2 {*/
    // The GUI pipeline with plain triangles; pipelines compile on first use on OpenGL and Vulkan.
    private val meshPipeline: RenderPipeline =
        RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(ModMetadata.MOD_ID, "pipeline/gui_mesh"))
            .withPrimitiveTopology(com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
            .build()

    /*?} else {*/
    /*// Vanilla 26.1 indexes every GUI draw as quads, so triangles go out as degenerate quads.
    private val meshPipeline: RenderPipeline = RenderPipelines.GUI
     */
    /*?}*/

    fun draw(
        graphics: GuiGraphicsExtractor,
        bounds: Rect,
        mesh: GpuMesh,
        scissor: ScreenRectangle?,
    ) {
        if (bounds.isEmpty() || mesh.vertexCount == 0) return
        val pose = graphics.pose()
        val area =
            ScreenRectangle(bounds.x, bounds.y, bounds.width, bounds.height)
                .transformMaxBounds(pose)
        val visible = (if (scissor != null) scissor.intersection(area) else area) ?: return
        val local = Matrix3x2f(pose).translate(bounds.x.toFloat(), bounds.y.toFloat())
        (graphics as GuiGraphicsExtractorAccessor)
            .`knhcore$guiRenderState`()
            .addGuiElement(MeshRenderState(mesh, local, scissor, visible))
    }

    /**
     * What [GuiGraphicsExtractor.enableScissor] records for [rect]: the rectangle through the
     * current pose, within the screen. The extractor keeps its scissor stack private.
     */
    fun scissorOf(graphics: GuiGraphicsExtractor, rect: Rect): ScreenRectangle =
        ScreenRectangle(rect.x, rect.y, rect.width, rect.height)
            .transformAxisAligned(graphics.pose())
            .intersection(ScreenRectangle(0, 0, graphics.guiWidth(), graphics.guiHeight()))
            ?: ScreenRectangle.empty()

    private class MeshRenderState(
        private val mesh: GpuMesh,
        pose: Matrix3x2f,
        private val scissor: ScreenRectangle?,
        private val bounds: ScreenRectangle,
    ) : GuiElementRenderState {
        private val m00 = pose.m00
        private val m01 = pose.m01
        private val m10 = pose.m10
        private val m11 = pose.m11
        private val m20 = pose.m20
        private val m21 = pose.m21

        override fun buildVertices(vertexConsumer: VertexConsumer) {
            var vertex = 0
            while (vertex < mesh.vertexCount) {
                vertexConsumer.vertex(vertex)
                vertexConsumer.vertex(vertex + 1)
                vertexConsumer.vertex(vertex + 2)
                /*? if <26.2 {*/
                /*vertexConsumer.vertex(vertex + 2)
                 */
                /*?}*/
                vertex += 3
            }
        }

        // Applies the pose by hand: the consumer's 2D pose helper allocates a vector per vertex.
        private fun VertexConsumer.vertex(index: Int) {
            val x = mesh.positions[index * 2]
            val y = mesh.positions[index * 2 + 1]
            addVertex(m00 * x + m10 * y + m20, m01 * x + m11 * y + m21, 0f)
                .setColor(mesh.colors[index])
        }

        override fun pipeline(): RenderPipeline = meshPipeline

        override fun textureSetup(): TextureSetup = TextureSetup.noTexture()

        override fun scissorArea(): ScreenRectangle? = scissor

        override fun bounds(): ScreenRectangle = bounds
    }
}
/*?}*/
