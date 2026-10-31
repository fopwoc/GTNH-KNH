package io.github.fopwoc.mods.framework.ui.compose.minecraft.render

import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuScene
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuSceneMesh
import java.nio.ByteBuffer
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.sqrt
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30

/**
 * Draws a [GpuScene] for [GpuImageRenderer], which owns the GL state around it. Each mesh lives in
 * its own vertex buffer while frames keep drawing it; quads share one index buffer. The depth
 * buffer is cleared inside the canvas's scissor before and after, so the scene neither hides behind
 * what the GUI drew there nor hides what it draws next. A faded scene first lays down depth alone,
 * so only the nearest surface of each pixel blends.
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
            GL20.glUniform1f(GL20.glGetUniformLocation(program, "alpha"), scene.alpha)
            light(scene)
            if (scene.alpha < 1f) {
                GL11.glColorMask(false, false, false, false)
                drawMeshes(scene)
                GL11.glColorMask(true, true, true, true)
            }
            drawMeshes(scene)
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT)
        } finally {
            GL11.glColorMask(true, true, true, true)
            GL11.glDepthFunc(oldDepthFunc)
            GL11.glDepthMask(oldDepthMask)
            GL11.glDisable(GL11.GL_DEPTH_TEST)
        }
    }

    /** The scene's light, and the way to the viewer: the camera's depth falls towards it. */
    private fun light(scene: GpuScene) {
        val light = scene.light
        val rows = scene.camera.relativeTo(0.0, 0.0, 0.0)
        val toViewer = floatArrayOf(-rows[8], -rows[9], -rows[10])
        val length = sqrt(toViewer.sumOf { (it * it).toDouble() }).toFloat()
        GL20.glUniform3f(GL20.glGetUniformLocation(program, "lightDir"), light.x, light.y, light.z)
        GL20.glUniform3f(
            GL20.glGetUniformLocation(program, "lightColor"),
            (light.color shr 16 and 0xFF) / 255f,
            (light.color shr 8 and 0xFF) / 255f,
            (light.color and 0xFF) / 255f,
        )
        GL20.glUniform1f(GL20.glGetUniformLocation(program, "intensity"), light.intensity)
        GL20.glUniform1f(GL20.glGetUniformLocation(program, "ambient"), light.ambient)
        GL20.glUniform3f(
            GL20.glGetUniformLocation(program, "toViewer"),
            toViewer[0] / length,
            toViewer[1] / length,
            toViewer[2] / length,
        )
    }

    private fun drawMeshes(scene: GpuScene) {
        val transform = GL20.glGetUniformLocation(program, "transform")
        val scale = GL20.glGetUniformLocation(program, "scale")
        val place = GL20.glGetUniformLocation(program, "place")
        for (mesh in scene.meshes) {
            if (mesh.quadCount == 0) continue
            val rows = scene.camera.relativeTo(mesh.originX, mesh.originY, mesh.originZ)
            rowBuffer.clear()
            rowBuffer.put(rows).flip()
            GL20.glUniform4(transform, rowBuffer)
            GL20.glUniform1f(scale, mesh.scale.toFloat())
            // Where the mesh lies, wrapped: only the ripples on shiny faces look at it.
            GL20.glUniform3f(
                place,
                (mesh.originX % RIPPLE_WRAP).toFloat(),
                (mesh.originY % RIPPLE_WRAP).toFloat(),
                (mesh.originZ % RIPPLE_WRAP).toFloat(),
            )
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffers.getOrPut(mesh) { upload(mesh) })
            GL20.glVertexAttribPointer(0, 2, GL11.GL_UNSIGNED_BYTE, false, VERTEX_BYTES, 0L)
            GL20.glVertexAttribPointer(1, 1, GL11.GL_SHORT, false, VERTEX_BYTES, 2L)
            GL20.glVertexAttribPointer(2, 4, GL11.GL_UNSIGNED_BYTE, true, VERTEX_BYTES, 4L)
            GL11.glDrawElements(GL11.GL_TRIANGLES, mesh.quadCount * 6, GL11.GL_UNSIGNED_INT, 0L)
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
            program = GpuShaders.link(VERTEX_SHADER, FRAGMENT_SHADER, "cell", "height", "tint")
        if (vertexArray == 0) {
            vertexArray = GL30.glGenVertexArrays()
            indexBuffer = GL15.glGenBuffers()
            GL30.glBindVertexArray(vertexArray)
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, indexBuffer)
            GL20.glEnableVertexAttribArray(0)
            GL20.glEnableVertexAttribArray(1)
            GL20.glEnableVertexAttribArray(2)
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
        for ((vertex, argb) in mesh.colors.withIndex()) {
            val packed = mesh.vertices[vertex]
            bytes.put(packed.toByte()).put((packed shr 8).toByte())
            bytes.put((packed shr 16).toByte()).put((packed shr 24).toByte())
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
            // x and z in steps of scale blocks, y in sixteenths of a block.
            in vec2 cell;
            in float height;
            in vec4 tint;
            uniform vec2 screen;
            uniform vec2 origin;
            uniform float scale;
            // World to local GUI x, local GUI y and depth, one row each.
            uniform vec4 transform[3];
            uniform vec3 place;
            uniform vec3 lightDir;
            uniform vec3 lightColor;
            uniform float intensity;
            uniform float ambient;
            out vec3 base;
            out vec3 lit;
            out vec3 normal;
            out vec3 position;
            flat out float shiny;
            const vec3 FACES[6] = vec3[6](
              vec3(0, 1, 0), vec3(1, 0, 0), vec3(-1, 0, 0), vec3(0, 0, 1), vec3(0, 0, -1), vec3(0, -1, 0));
            void main() {
              vec4 world = vec4(cell.x * scale, height / 16.0, cell.y * scale, 1.0);
              vec2 pixel = origin + vec2(dot(transform[0], world), dot(transform[1], world));
              gl_Position = vec4(pixel.x * 2.0 / screen.x - 1.0, 1.0 - pixel.y * 2.0 / screen.y, dot(transform[2], world), 1.0);
              // The alpha byte is the face's direction, and 8 when it shines.
              int look = int(tint.a * 255.0 + 0.5);
              normal = FACES[look & 7];
              shiny = float((look & 8) != 0);
              // Ambient light takes half the light's colour, so night is cool all over.
              vec3 sky = mix(vec3(1.0), lightColor, 0.5) * ambient;
              lit = sky + (1.0 - ambient) * intensity * max(dot(normal, lightDir), 0.0) * lightColor;
              base = tint.rgb;
              position = place + world.xyz;
            }
            """
                .trimIndent()
        /** Mesh origins wrap at this many blocks for ripples, keeping floats precise. */
        const val RIPPLE_WRAP = 4096.0
        /** Per vertex: x and z steps as bytes, y sixteenths as a short, then RGBA bytes. */
        const val VERTEX_BYTES = 8
        val FRAGMENT_SHADER =
            """
            #version 330 core
            in vec3 base;
            in vec3 lit;
            in vec3 normal;
            in vec3 position;
            flat in float shiny;
            uniform float alpha;
            uniform vec3 lightDir;
            uniform vec3 lightColor;
            uniform float intensity;
            uniform vec3 toViewer;
            out vec4 color;
            void main() {
              vec3 shade = base * lit;
              if (shiny > 0.5) {
                // A shiny face mirrors the light; flat ones ripple a little, so a lake glints in
                // spots instead of all at once.
                vec3 ripple = normal.y > 0.5
                  ? 0.18 * vec3(sin(position.x * 1.7 + position.z * 0.6), 0.0, cos(position.z * 1.3 - position.x * 0.8))
                  : vec3(0.0);
                vec3 n = normalize(normal + ripple);
                float glint = pow(max(dot(n, normalize(lightDir + toViewer)), 0.0), 48.0);
                shade += lightColor * glint * intensity * 0.7;
              }
              color = vec4(min(shade, vec3(1.0)), alpha);
            }
            """
                .trimIndent()
    }
}
