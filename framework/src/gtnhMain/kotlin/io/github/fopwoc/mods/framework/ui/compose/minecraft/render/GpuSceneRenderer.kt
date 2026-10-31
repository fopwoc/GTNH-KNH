package io.github.fopwoc.mods.framework.ui.compose.minecraft.render

import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuScene
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuSceneMesh
import java.nio.ByteBuffer
import java.util.Collections
import java.util.IdentityHashMap
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30

/**
 * Draws a [GpuScene] for [GpuImageRenderer], which owns the GL state around it. Each mesh lives in
 * its own vertex buffer while frames keep drawing it; quads share one index buffer. The depth
 * buffer is cleared inside the canvas's scissor before and after, so the scene neither hides behind
 * what the GUI drew there nor hides what it draws next.
 */
internal class GpuSceneRenderer {
    private val buffers = IdentityHashMap<GpuSceneMesh, Int>()
    private var program = 0
    private var vertexArray = 0
    private var indexBuffer = 0
    private var indexedQuads = 0
    private var staging: ByteBuffer? = null
    private val rowBuffer = BufferUtils.createFloatBuffer(12)

    fun draw(
        scene: GpuScene?,
        originX: Float,
        originY: Float,
        screenWidth: Float,
        screenHeight: Float,
    ) {
        val meshes = scene?.meshes.orEmpty()
        release(meshes)
        if (scene == null || meshes.isEmpty()) return
        ensureResources(meshes.maxOf { it.quadCount })
        val oldDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
        val oldDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC)
        try {
            GL11.glDepthMask(true)
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT)
            GL11.glEnable(GL11.GL_DEPTH_TEST)
            GL11.glDepthFunc(GL11.GL_LEQUAL)
            GL30.glBindVertexArray(vertexArray)
            GL20.glUseProgram(program)
            GL20.glUniform2f(
                GL20.glGetUniformLocation(program, "screen"),
                screenWidth,
                screenHeight,
            )
            GL20.glUniform2f(GL20.glGetUniformLocation(program, "origin"), originX, originY)
            val transform = GL20.glGetUniformLocation(program, "transform")
            for (mesh in meshes) {
                if (mesh.quadCount == 0) continue
                val rows = scene.camera.relativeTo(mesh.originX, mesh.originY, mesh.originZ)
                rowBuffer.clear()
                rowBuffer.put(rows).flip()
                GL20.glUniform4(transform, rowBuffer)
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffers.getOrPut(mesh) { upload(mesh) })
                GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, VERTEX_BYTES, 0L)
                GL20.glVertexAttribPointer(1, 4, GL11.GL_UNSIGNED_BYTE, true, VERTEX_BYTES, 12L)
                GL11.glDrawElements(GL11.GL_TRIANGLES, mesh.quadCount * 6, GL11.GL_UNSIGNED_INT, 0L)
            }
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT)
        } finally {
            GL11.glDepthFunc(oldDepthFunc)
            GL11.glDepthMask(oldDepthMask)
            GL11.glDisable(GL11.GL_DEPTH_TEST)
        }
    }

    /** Frees the buffers of meshes this frame no longer draws. */
    private fun release(drawn: List<GpuSceneMesh>) {
        if (buffers.isEmpty()) return
        val keep = drawn.toCollection(Collections.newSetFromMap(IdentityHashMap()))
        val iterator = buffers.entries.iterator()
        while (iterator.hasNext()) {
            val (mesh, buffer) = iterator.next()
            if (mesh in keep) continue
            GL15.glDeleteBuffers(buffer)
            iterator.remove()
        }
    }

    private fun ensureResources(quads: Int) {
        if (program == 0)
            program = GpuShaders.link(VERTEX_SHADER, FRAGMENT_SHADER, "position", "tint")
        if (vertexArray == 0) {
            vertexArray = GL30.glGenVertexArrays()
            indexBuffer = GL15.glGenBuffers()
            GL30.glBindVertexArray(vertexArray)
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, indexBuffer)
            GL20.glEnableVertexAttribArray(0)
            GL20.glEnableVertexAttribArray(1)
        }
        if (quads <= indexedQuads) return
        indexedQuads = maxOf(quads, indexedQuads * 3 / 2)
        val indices = BufferUtils.createIntBuffer(indexedQuads * 6)
        for (quad in 0 until indexedQuads) {
            val first = quad * 4
            indices
                .put(first)
                .put(first + 1)
                .put(first + 2)
                .put(first)
                .put(first + 2)
                .put(first + 3)
        }
        indices.flip()
        GL30.glBindVertexArray(vertexArray)
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, indexBuffer)
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indices, GL15.GL_STATIC_DRAW)
    }

    private fun upload(mesh: GpuSceneMesh): Int {
        val bytes = staging(mesh.vertexCount * VERTEX_BYTES)
        val positions = mesh.positions
        for ((vertex, argb) in mesh.colors.withIndex()) {
            bytes.putFloat(positions[vertex * 3])
            bytes.putFloat(positions[vertex * 3 + 1])
            bytes.putFloat(positions[vertex * 3 + 2])
            bytes.put((argb shr 16).toByte()).put((argb shr 8).toByte()).put(argb.toByte())
            bytes.put((argb ushr 24).toByte())
        }
        bytes.flip()
        val buffer = GL15.glGenBuffers()
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer)
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, bytes, GL15.GL_STATIC_DRAW)
        return buffer
    }

    private fun staging(required: Int): ByteBuffer {
        val current = staging?.capacity() ?: 0
        if (current < required)
            staging = BufferUtils.createByteBuffer(maxOf(required, current * 3 / 2))
        return checkNotNull(staging).apply { clear() }
    }

    fun dispose() {
        buffers.values.forEach(GL15::glDeleteBuffers)
        buffers.clear()
        if (indexBuffer != 0) GL15.glDeleteBuffers(indexBuffer)
        if (vertexArray != 0) GL30.glDeleteVertexArrays(vertexArray)
        if (program != 0) GL20.glDeleteProgram(program)
        program = 0
        vertexArray = 0
        indexBuffer = 0
        indexedQuads = 0
        staging = null
    }

    private companion object {
        val VERTEX_SHADER =
            """
            #version 330 core
            in vec3 position;
            in vec4 tint;
            uniform vec2 screen;
            uniform vec2 origin;
            // World to local GUI x, local GUI y and depth, one row each.
            uniform vec4 transform[3];
            out vec4 shade;
            void main() {
              vec4 world = vec4(position, 1.0);
              vec2 pixel = origin + vec2(dot(transform[0], world), dot(transform[1], world));
              gl_Position = vec4(pixel.x * 2.0 / screen.x - 1.0, 1.0 - pixel.y * 2.0 / screen.y, dot(transform[2], world), 1.0);
              shade = tint;
            }
            """
                .trimIndent()
        /** Per vertex: x, y and z floats, then RGBA as normalized unsigned bytes. */
        const val VERTEX_BYTES = 16
        val FRAGMENT_SHADER =
            """
            #version 330 core
            in vec4 shade;
            out vec4 color;
            void main() { color = shade; }
            """
                .trimIndent()
    }
}
