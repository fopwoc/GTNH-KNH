package io.github.fopwoc.mods.framework.ui.compose.minecraft.render

import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuCanvasFrame
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuImage
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuImageDraw
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import java.nio.ByteBuffer
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL21
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL31
import org.lwjgl.opengl.GL33
import org.lwjgl.opengl.GLContext

/** Uploads changed images into a texture array and draws positioned quads in instanced batches. */
internal class GpuImageRenderer {
    private val resident = LinkedHashMap<GpuImage, Int>(256, 0.75f, true)
    private var imageWidth = 0
    private var imageHeight = 0
    private var texture = 0
    private var program = 0
    private var vertexArray = 0
    private var instanceBuffer = 0
    private var layerCount = 0
    private var uploadBuffer: ByteBuffer? = null
    private var instances: ByteBuffer? = null

    fun draw(bounds: Rect, screenWidth: Int, screenHeight: Int, frame: GpuCanvasFrame) {
        if (bounds.width <= 0 || bounds.height <= 0 || frame.draws.isEmpty()) return
        val image = frame.draws.first().image
        val oldProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
        val oldActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)
        val oldVertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING)
        val oldArrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING)
        val oldDepth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
        val oldCull = GL11.glIsEnabled(GL11.GL_CULL_FACE)
        val oldBlend = GL11.glIsEnabled(GL11.GL_BLEND)
        val oldBlendSrc = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB)
        val oldBlendDst = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB)
        val oldBlendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA)
        val oldBlendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA)
        val oldUnpack = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT)
        val oldUnpackRowLength = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH)
        val oldUnpackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING)
        GL13.glActiveTexture(GL13.GL_TEXTURE0)
        val oldTexture = GL11.glGetInteger(GL30.GL_TEXTURE_BINDING_2D_ARRAY)
        val oldTexture2D = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
        try {
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0)
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0)
            ensureResources(image.width, image.height)
            GL30.glBindVertexArray(vertexArray)
            GL20.glUseProgram(program)
            GL20.glUniform2f(
                GL20.glGetUniformLocation(program, "screen"),
                screenWidth.toFloat(),
                screenHeight.toFloat(),
            )
            GL20.glUniform2f(
                GL20.glGetUniformLocation(program, "origin"),
                bounds.x.toFloat(),
                bounds.y.toFloat(),
            )
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "images"), 0)
            GL11.glDisable(GL11.GL_DEPTH_TEST)
            GL11.glDisable(GL11.GL_CULL_FACE)
            GL11.glEnable(GL11.GL_BLEND)
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
            frame.draws.chunked(layerCount).forEach(::drawBatch)
        } finally {
            GL20.glUseProgram(oldProgram)
            GL30.glBindVertexArray(oldVertexArray)
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, oldArrayBuffer)
            GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, oldTexture)
            // Angelica GLSM caches one binding per unit even across texture targets.
            // Restore the 2D binding last so vanilla item and GUI texture binds stay coherent.
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, oldTexture2D)
            GL13.glActiveTexture(oldActiveTexture)
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, oldUnpack)
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, oldUnpackRowLength)
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, oldUnpackBuffer)
            GL14.glBlendFuncSeparate(oldBlendSrc, oldBlendDst, oldBlendSrcAlpha, oldBlendDstAlpha)
            if (oldBlend) GL11.glEnable(GL11.GL_BLEND) else GL11.glDisable(GL11.GL_BLEND)
            if (oldDepth) GL11.glEnable(GL11.GL_DEPTH_TEST) else GL11.glDisable(GL11.GL_DEPTH_TEST)
            if (oldCull) GL11.glEnable(GL11.GL_CULL_FACE) else GL11.glDisable(GL11.GL_CULL_FACE)
        }
    }

    private fun drawBatch(batch: List<GpuImageDraw>) {
        val visible = batch.mapTo(HashSet()) { it.image }
        val buffer = instanceBuffer(batch.size)
        for (draw in batch) {
            val layer = resident[draw.image] ?: allocateLayer(visible) ?: continue
            if (draw.image !in resident) upload(layer, draw.image)
            resident[draw.image] = layer
            buffer.putFloat(draw.x).putFloat(draw.y).putFloat(draw.width).putFloat(draw.height)
            buffer.putFloat(layer.toFloat())
            buffer.putFloat(Math.toRadians(draw.rotation.toDouble()).toFloat()).putFloat(draw.alpha)
        }
        buffer.flip()
        if (!buffer.hasRemaining()) return
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, instanceBuffer)
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STREAM_DRAW)
        GL31.glDrawArraysInstanced(GL11.GL_TRIANGLE_STRIP, 0, 4, buffer.limit() / INSTANCE_BYTES)
    }

    private fun ensureResources(width: Int, height: Int) {
        check(GLContext.getCapabilities().OpenGL33) { "GpuCanvas requires OpenGL 3.3" }
        if (program == 0) program = createProgram()
        if (vertexArray == 0) {
            vertexArray = GL30.glGenVertexArrays()
            instanceBuffer = GL15.glGenBuffers()
            GL30.glBindVertexArray(vertexArray)
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, instanceBuffer)
            GL20.glEnableVertexAttribArray(0)
            GL20.glVertexAttribPointer(0, 4, GL11.GL_FLOAT, false, INSTANCE_BYTES, 0L)
            GL33.glVertexAttribDivisor(0, 1)
            GL20.glEnableVertexAttribArray(1)
            GL20.glVertexAttribPointer(1, 3, GL11.GL_FLOAT, false, INSTANCE_BYTES, 16L)
            GL33.glVertexAttribDivisor(1, 1)
        }
        if (texture != 0 && imageWidth == width && imageHeight == height) {
            GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, texture)
            return
        }
        GpuTextureDeletion.delete(texture)
        resident.clear()
        imageWidth = width
        imageHeight = height
        val bytesPerImage = width.toLong() * height * 4
        require(
            width <= GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE) &&
                height <= GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE)
        ) {
            "GPU image exceeds the maximum texture size"
        }
        layerCount =
            minOf(
                    1024L,
                    64L * 1024 * 1024 / bytesPerImage,
                    GL11.glGetInteger(GL30.GL_MAX_ARRAY_TEXTURE_LAYERS).toLong(),
                )
                .toInt()
        require(layerCount > 0) { "GPU image exceeds the canvas texture budget" }
        texture = GL11.glGenTextures()
        GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, texture)
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST)
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST)
        GL11.glTexParameteri(
            GL30.GL_TEXTURE_2D_ARRAY,
            GL11.GL_TEXTURE_WRAP_S,
            GL12.GL_CLAMP_TO_EDGE,
        )
        GL11.glTexParameteri(
            GL30.GL_TEXTURE_2D_ARRAY,
            GL11.GL_TEXTURE_WRAP_T,
            GL12.GL_CLAMP_TO_EDGE,
        )
        GL12.glTexImage3D(
            GL30.GL_TEXTURE_2D_ARRAY,
            0,
            GL11.GL_RGBA8,
            width,
            height,
            layerCount,
            0,
            GL11.GL_RGBA,
            GL11.GL_UNSIGNED_BYTE,
            null as ByteBuffer?,
        )
        uploadBuffer = BufferUtils.createByteBuffer(bytesPerImage.toInt())
    }

    private fun allocateLayer(visible: Set<GpuImage>): Int? {
        if (resident.size < layerCount) return resident.size
        val evicted = resident.entries.firstOrNull { it.key !in visible } ?: return null
        resident.remove(evicted.key)
        return evicted.value
    }

    private fun upload(layer: Int, image: GpuImage) {
        val bytes = checkNotNull(uploadBuffer)
        bytes.clear()
        bytes.put(image.pixels)
        bytes.flip()
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1)
        GL12.glTexSubImage3D(
            GL30.GL_TEXTURE_2D_ARRAY,
            0,
            0,
            0,
            layer,
            image.width,
            image.height,
            1,
            GL11.GL_RGBA,
            GL11.GL_UNSIGNED_BYTE,
            bytes,
        )
    }

    private fun instanceBuffer(capacity: Int): ByteBuffer {
        val required = capacity * INSTANCE_BYTES
        val current = instances
        if (current == null || current.capacity() < required)
            instances = BufferUtils.createByteBuffer(required)
        return checkNotNull(instances).apply { clear() }
    }

    fun dispose() {
        GpuTextureDeletion.delete(texture)
        if (instanceBuffer != 0) GL15.glDeleteBuffers(instanceBuffer)
        if (vertexArray != 0) GL30.glDeleteVertexArrays(vertexArray)
        if (program != 0) GL20.glDeleteProgram(program)
        texture = 0
        program = 0
        vertexArray = 0
        instanceBuffer = 0
        resident.clear()
    }

    @Suppress("TooGenericExceptionCaught")
    private fun createProgram(): Int {
        val vertex = compile(GL20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragment =
            try {
                compile(GL20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
            } catch (failure: Throwable) {
                GL20.glDeleteShader(vertex)
                throw failure
            }
        val result = GL20.glCreateProgram()
        try {
            GL20.glAttachShader(result, vertex)
            GL20.glAttachShader(result, fragment)
            GL20.glBindAttribLocation(result, 0, "rect")
            GL20.glBindAttribLocation(result, 1, "look")
            GL20.glLinkProgram(result)
            check(GL20.glGetProgrami(result, GL20.GL_LINK_STATUS) != 0) {
                GL20.glGetProgramInfoLog(result, 4096)
            }
            return result
        } catch (failure: Throwable) {
            GL20.glDeleteProgram(result)
            throw failure
        } finally {
            GL20.glDeleteShader(vertex)
            GL20.glDeleteShader(fragment)
        }
    }

    private fun compile(type: Int, code: String): Int {
        val shader = GL20.glCreateShader(type)
        GL20.glShaderSource(shader, code)
        GL20.glCompileShader(shader)
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            val log = GL20.glGetShaderInfoLog(shader, 4096)
            GL20.glDeleteShader(shader)
            error("GPU canvas shader compilation failed: $log")
        }
        return shader
    }

    private companion object {
        val VERTEX_SHADER =
            """
            #version 330 core
            in vec4 rect;
            // Array layer, clockwise rotation in radians around the quad's centre, alpha.
            in vec3 look;
            uniform vec2 screen;
            uniform vec2 origin;
            out vec3 sampleAt;
            out float alpha;
            void main() {
              vec2 corner = vec2(float(gl_VertexID & 1), float((gl_VertexID >> 1) & 1));
              vec2 halfSize = rect.zw * 0.5;
              vec2 offset = corner * rect.zw - halfSize;
              float c = cos(look.y);
              float s = sin(look.y);
              vec2 pixel = origin + rect.xy + halfSize + vec2(offset.x * c - offset.y * s, offset.x * s + offset.y * c);
              gl_Position = vec4(pixel.x * 2.0 / screen.x - 1.0, 1.0 - pixel.y * 2.0 / screen.y, 0.0, 1.0);
              sampleAt = vec3(corner, look.x);
              alpha = look.z;
            }
            """
                .trimIndent()
        /** Per quad: x, y, width, height, array layer, rotation, alpha; seven floats. */
        const val INSTANCE_BYTES = 28
        val FRAGMENT_SHADER =
            """
            #version 330 core
            in vec3 sampleAt;
            in float alpha;
            uniform sampler2DArray images;
            out vec4 color;
            void main() { color = texture(images, sampleAt) * vec4(1.0, 1.0, 1.0, alpha); }
            """
                .trimIndent()
    }
}
