package io.github.fopwoc.mods.framework.ui.compose.minecraft.render

import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuMesh
import java.nio.ByteBuffer
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30

/**
 * Draws flat-coloured triangles for [GpuImageRenderer], which owns the GL state around it. The
 * vertex buffer is re-uploaded only when a different mesh object arrives.
 */
internal class GpuMeshRenderer {
    private var program = 0
    private var vertexArray = 0
    private var vertexBuffer = 0
    private var capacity = 0L
    private var uploaded: GpuMesh? = null
    private var staging: ByteBuffer? = null

    fun draw(
        mesh: GpuMesh,
        originX: Float,
        originY: Float,
        screenWidth: Float,
        screenHeight: Float,
    ) {
        if (mesh.vertexCount == 0) return
        ensureResources()
        GL30.glBindVertexArray(vertexArray)
        if (mesh !== uploaded) upload(mesh)
        GL20.glUseProgram(program)
        GL20.glUniform2f(GL20.glGetUniformLocation(program, "screen"), screenWidth, screenHeight)
        GL20.glUniform2f(GL20.glGetUniformLocation(program, "origin"), originX, originY)
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, mesh.vertexCount)
    }

    private fun ensureResources() {
        if (program == 0)
            program = GpuShaders.link(VERTEX_SHADER, FRAGMENT_SHADER, "position", "tint")
        if (vertexArray != 0) return
        vertexArray = GL30.glGenVertexArrays()
        vertexBuffer = GL15.glGenBuffers()
        GL30.glBindVertexArray(vertexArray)
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vertexBuffer)
        GL20.glEnableVertexAttribArray(0)
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, VERTEX_BYTES, 0L)
        GL20.glEnableVertexAttribArray(1)
        GL20.glVertexAttribPointer(1, 4, GL11.GL_UNSIGNED_BYTE, true, VERTEX_BYTES, 8L)
    }

    private fun upload(mesh: GpuMesh) {
        val required = mesh.vertexCount.toLong() * VERTEX_BYTES
        val bytes = staging(required.toInt())
        val positions = mesh.positions
        for ((vertex, argb) in mesh.colors.withIndex()) {
            bytes.putFloat(positions[vertex * 2]).putFloat(positions[vertex * 2 + 1])
            bytes.put((argb shr 16).toByte()).put((argb shr 8).toByte()).put(argb.toByte())
            bytes.put((argb ushr 24).toByte())
        }
        bytes.flip()
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vertexBuffer)
        // Orphan the old storage so the driver need not wait on frames still reading it.
        if (required > capacity) capacity = maxOf(required, capacity * 3 / 2)
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, capacity, GL15.GL_STATIC_DRAW)
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0L, bytes)
        uploaded = mesh
    }

    private fun staging(required: Int): ByteBuffer {
        val current = staging?.capacity() ?: 0
        if (current < required)
            staging = BufferUtils.createByteBuffer(maxOf(required, current * 3 / 2))
        return checkNotNull(staging).apply { clear() }
    }

    fun dispose() {
        if (vertexBuffer != 0) GL15.glDeleteBuffers(vertexBuffer)
        if (vertexArray != 0) GL30.glDeleteVertexArrays(vertexArray)
        if (program != 0) GL20.glDeleteProgram(program)
        program = 0
        vertexArray = 0
        vertexBuffer = 0
        capacity = 0
        uploaded = null
        staging = null
    }

    private companion object {
        val VERTEX_SHADER =
            """
            #version 330 core
            in vec2 position;
            in vec4 tint;
            uniform vec2 screen;
            uniform vec2 origin;
            out vec4 shade;
            void main() {
              vec2 pixel = origin + position;
              gl_Position = vec4(pixel.x * 2.0 / screen.x - 1.0, 1.0 - pixel.y * 2.0 / screen.y, 0.0, 1.0);
              shade = tint;
            }
            """
                .trimIndent()
        /** Per vertex: x and y floats, then RGBA as normalized unsigned bytes. */
        const val VERTEX_BYTES = 12
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
