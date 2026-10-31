/*? if >=26 {*/
// 26.x only: the GPU device API; LegacyGpuSceneRenderer draws scenes on 1.21.1.
package io.github.fopwoc.mods.framework.ui.compose.minecraft.render

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.CompareOp
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import io.github.fopwoc.mods.framework.ModMetadata
import io.github.fopwoc.mods.framework.minecraft.Identifier
import io.github.fopwoc.mods.framework.mixin.GuiGraphicsExtractorAccessor
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuScene
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuSceneLight
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuSceneMesh
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Collections
import java.util.IdentityHashMap
import java.util.OptionalDouble
import kotlin.math.roundToInt
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.renderer.ProjectionMatrixBuffer
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.state.gui.BlitRenderState
import org.joml.Matrix3x2f
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f

/**
 * The scene of one GPU canvas: drawn into a texture of its own at the window's pixel density, with
 * a depth texture beside it, and blitted into the GUI like the game's picture-in-picture elements,
 * so it works the same on OpenGL and Vulkan. Each mesh lives in its own vertex buffer while frames
 * keep drawing it. The whole camera goes into the model-view matrix; the projection is identity.
 * The scene's alpha fades the blit, so only what is nearest shows through.
 */
internal class ModernGpuSceneRenderer {
    private val buffers = IdentityHashMap<GpuSceneMesh, GpuBuffer>()
    private var color: GpuTexture? = null
    private var colorView: GpuTextureView? = null
    private var depth: GpuTexture? = null
    private var depthView: GpuTextureView? = null
    private var projection: ProjectionMatrixBuffer? = null

    fun draw(
        graphics: GuiGraphicsExtractor,
        bounds: Rect,
        scene: GpuScene?,
        scissor: ScreenRectangle?,
    ) {
        val meshes = scene?.meshes.orEmpty()
        release(meshes)
        if (scene == null || bounds.isEmpty() || meshes.isEmpty()) return
        val scale = Minecraft.getInstance().window.guiScale
        val target = ensureTargets(bounds.width * scale, bounds.height * scale)
        val device = RenderSystem.getDevice()
        val identity = checkNotNull(projection).getBuffer(IDENTITY)
        /*? if >=26.2 {*/
        val zZeroToOne = device.deviceInfo.isZZeroToOne
        val indices = RenderSystem.getSequentialBuffer(com.mojang.blaze3d.PrimitiveTopology.QUADS)
        /*?} else {*/
        /*val zZeroToOne = false
        val indices = RenderSystem.getSequentialBuffer(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS)
         */
        /*?}*/
        val indexBuffer = indices.getBuffer(meshes.maxOf { it.quadCount } * 6)
        device
            .createCommandEncoder()
            .createRenderPass(
                { "KNH Core GPU scene" },
                target,
                /*? if >=26.2 {*/
                java.util.Optional.of(Vector4f()),
                /*?} else {*/
                /*java.util.OptionalInt.of(0),
                 */
                /*?}*/
                depthView,
                OptionalDouble.of(1.0),
            )
            .use { pass ->
                pass.setPipeline(opaque)
                RenderSystem.bindDefaultUniforms(pass)
                pass.setUniform("Projection", identity)
                pass.setIndexBuffer(indexBuffer, indices.type())
                // Opaque meshes first; translucent ones then blend over them.
                for (mesh in meshes.filter { !it.translucent } + meshes.filter { it.translucent }) {
                    if (mesh.quadCount == 0) continue
                    if (mesh.translucent) pass.setPipeline(translucent)
                    val rows = scene.camera.relativeTo(mesh.originX, mesh.originY, mesh.originZ)
                    // A fresh matrix per mesh: the uniform storage skips a write equal to the last.
                    pass.setUniform(
                        "DynamicTransforms",
                        RenderSystem.getDynamicUniforms()
                            .writeTransform(
                                clipTransform(rows, bounds, zZeroToOne),
                                WHITE,
                                NO_OFFSET,
                                IDENTITY,
                            ),
                    )
                    val vertices = buffers.getOrPut(mesh) { upload(mesh) }
                    /*? if >=26.2 {*/
                    pass.setVertexBuffer(0, vertices.slice())
                    pass.drawIndexed(mesh.quadCount * 6, 1, 0, 0, 0)
                    /*?} else {*/
                    /*pass.setVertexBuffer(0, vertices)
                    pass.drawIndexed(0, 0, mesh.quadCount * 6, 1)
                     */
                    /*?}*/
                }
            }
        // Render targets have their origin at the bottom, so the blit reads them upside down.
        (graphics as GuiGraphicsExtractorAccessor)
            .`knhcore$guiRenderState`()
            .addBlitToCurrentLayer(
                BlitRenderState(
                    RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
                    TextureSetup.singleTexture(
                        target,
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST),
                    ),
                    Matrix3x2f(graphics.pose()),
                    bounds.x,
                    bounds.y,
                    bounds.x + bounds.width,
                    bounds.y + bounds.height,
                    0f,
                    1f,
                    1f,
                    0f,
                    ((scene.alpha * OPAQUE).roundToInt() shl ALPHA_SHIFT) or WHITE_RGB,
                    scissor,
                )
            )
    }

    /**
     * World, relative to the mesh, straight to clip space: local GUI x and y across the canvas, top
     * up, and the camera's depth in the device's range.
     */
    private fun clipTransform(rows: FloatArray, bounds: Rect, zZeroToOne: Boolean): Matrix4f {
        val sx = 2f / bounds.width
        val sy = -2f / bounds.height
        val sz = if (zZeroToOne) 0.5f else 1f
        val oz = if (zZeroToOne) 0.5f else 0f
        return Matrix4f(
            rows[0] * sx,
            rows[4] * sy,
            rows[8] * sz,
            0f,
            rows[1] * sx,
            rows[5] * sy,
            rows[9] * sz,
            0f,
            rows[2] * sx,
            rows[6] * sy,
            rows[10] * sz,
            0f,
            rows[3] * sx - 1f,
            rows[7] * sy + 1f,
            rows[11] * sz + oz,
            1f,
        )
    }

    private fun ensureTargets(width: Int, height: Int): GpuTextureView {
        colorView
            ?.takeIf { color?.getWidth(0) == width && color?.getHeight(0) == height }
            ?.let {
                return it
            }
        closeTargets()
        val device = RenderSystem.getDevice()
        val attachment = GpuTexture.USAGE_RENDER_ATTACHMENT or GpuTexture.USAGE_COPY_DST
        color =
            device.createTexture(
                "KNH Core GPU scene",
                attachment or GpuTexture.USAGE_TEXTURE_BINDING,
                /*? if >=26.2 {*/
                com.mojang.blaze3d.GpuFormat.RGBA8_UNORM,
                /*?} else {*/
                /*com.mojang.blaze3d.textures.TextureFormat.RGBA8,
                 */
                /*?}*/
                width,
                height,
                1,
                1,
            )
        depth =
            device.createTexture(
                "KNH Core GPU scene depth",
                attachment,
                /*? if >=26.2 {*/
                com.mojang.blaze3d.GpuFormat.D32_FLOAT,
                /*?} else {*/
                /*com.mojang.blaze3d.textures.TextureFormat.DEPTH32,
                 */
                /*?}*/
                width,
                height,
                1,
                1,
            )
        colorView = device.createTextureView(color!!)
        depthView = device.createTextureView(depth!!)
        if (projection == null) projection = ProjectionMatrixBuffer("KNH Core GPU scene")
        return colorView!!
    }

    /** Frees the buffers of meshes this frame no longer draws. */
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

    private fun upload(mesh: GpuSceneMesh): GpuBuffer {
        val bytes =
            ByteBuffer.allocateDirect(mesh.vertexCount * VERTEX_BYTES)
                .order(ByteOrder.nativeOrder())
        // Lit here with the fixed light until this renderer lights in a shader.
        for (vertex in 0 until mesh.vertexCount) {
            val argb = mesh.lit(vertex, GpuSceneLight.FIXED)
            bytes.putFloat(mesh.x(vertex)).putFloat(mesh.y(vertex)).putFloat(mesh.z(vertex))
            bytes.put((argb shr 16).toByte()).put((argb shr 8).toByte()).put(argb.toByte())
            bytes.put((argb ushr 24).toByte())
        }
        bytes.flip()
        return RenderSystem.getDevice()
            .createBuffer({ "KNH Core GPU scene mesh" }, GpuBuffer.USAGE_VERTEX, bytes)
    }

    private fun closeTargets() {
        colorView?.close()
        color?.close()
        depthView?.close()
        depth?.close()
        colorView = null
        color = null
        depthView = null
        depth = null
    }

    fun dispose() {
        buffers.values.forEach(GpuBuffer::close)
        buffers.clear()
        closeTargets()
        projection?.close()
        projection = null
    }

    private companion object {
        const val OPAQUE = 255
        const val ALPHA_SHIFT = 24
        const val WHITE_RGB = 0xFFFFFF
        val IDENTITY = Matrix4f()
        val WHITE = Vector4f(1f, 1f, 1f, 1f)
        val NO_OFFSET = Vector3f()
        /** Per vertex: x, y and z floats, then RGBA bytes, as the position-colour format. */
        val VERTEX_BYTES = DefaultVertexFormat.POSITION_COLOR.vertexSize

        // Quads with their own depth test, unlike the game's, which may run reversed.
        val opaque: RenderPipeline = pipeline("gpu_scene", translucent = false)
        // Water: blended over what is drawn, hiding nothing behind it.
        val translucent: RenderPipeline = pipeline("gpu_scene_translucent", translucent = true)

        private fun pipeline(name: String, translucent: Boolean): RenderPipeline =
            /*? if >=26.2 {*/
            RenderPipeline.builder()
                .withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.GLOBALS)
                .withBindGroupLayout(
                    net.minecraft.client.renderer.BindGroupLayouts.MATRICES_PROJECTION
                )
                .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                .withPrimitiveTopology(com.mojang.blaze3d.PrimitiveTopology.QUADS)
                /*?} else {*/
                /*RenderPipeline.builder()
                .withUniform("DynamicTransforms", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
                .withVertexFormat(
                    DefaultVertexFormat.POSITION_COLOR,
                    com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                )
                 */
                /*?}*/
                .withLocation(Identifier.fromNamespaceAndPath(ModMetadata.MOD_ID, "pipeline/$name"))
                .withVertexShader("core/position_color")
                .withFragmentShader("core/position_color")
                .withDepthStencilState(
                    DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, !translucent)
                )
                .apply {
                    if (translucent)
                        withColorTargetState(
                            com.mojang.blaze3d.pipeline.ColorTargetState(
                                com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT
                            )
                        )
                }
                .withCull(false)
                .build()
    }
}
/*?}*/
