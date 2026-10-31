package io.github.fopwoc.mods.framework.ui.compose.minecraft.render

import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuScene
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuSceneCamera
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuSceneMesh
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuShadowFrustum
import java.nio.ByteBuffer
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.sqrt
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30

/**
 * Draws a [GpuScene] for [GpuImageRenderer], which owns the GL state around it. Each mesh lives in
 * its own vertex buffer while frames keep drawing it; quads share one index buffer. The depth
 * buffer is cleared inside the canvas's scissor before and after, so the scene neither hides behind
 * what the GUI drew there nor hides what it draws next. A faded scene first lays down depth alone,
 * so only the nearest surface of each pixel blends. While its light shines, the scene is first
 * drawn as the light sees it into a shadow map around what the canvas shows, and every pixel then
 * asks that map, filtered by the hardware, whether the light reaches it; the map is looked up a
 * little out along each surface's normal, so faces do not shadow themselves. Translucent meshes
 * come last, blended over the rest without writing depth, more opaque the flatter they are seen,
 * and cast no shadow. Light is worked out per pixel from leaned normals, so slopes shade smoothly.
 * The more the camera looks straight down, the more the light gives way to the colours as they are,
 * as a flat map shows them, shaded only by how the leaned normals slope: from above, light only
 * muddies a map.
 */
internal class GpuSceneRenderer {
    private val buffers = IdentityHashMap<GpuSceneMesh, Int>()
    private var program = 0
    private var vertexArray = 0
    private var indexBuffer = 0
    private var indexedQuads = 0
    private var staging: ByteBuffer? = null
    private val rowBuffer = BufferUtils.createFloatBuffer(12)
    private var shadowProgram = 0
    private var shadowFramebuffer = 0
    private var shadowMap = 0

    @Suppress("LongParameterList")
    fun draw(
        scene: GpuScene?,
        originX: Float,
        originY: Float,
        screenWidth: Float,
        screenHeight: Float,
        canvasWidth: Float,
        canvasHeight: Float,
    ) {
        val meshes = scene?.meshes.orEmpty()
        release(meshes)
        if (scene == null || meshes.isEmpty()) return
        ensureResources(meshes.maxOf { it.quadCount })
        val oldDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
        val oldDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC)
        val oldActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)
        GL13.glActiveTexture(GL13.GL_TEXTURE1)
        val oldShadowTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
        GL13.glActiveTexture(oldActiveTexture)
        val flatness = flatness(scene)
        // Straight down nothing is lit, so nothing is shadowed either.
        val shadow =
            if (scene.light.intensity < MIN_SHADOW_LIGHT || flatness >= 1f) null
            else
                GpuShadowFrustum.of(
                    scene,
                    canvasWidth.toDouble(),
                    canvasHeight.toDouble(),
                    SHADOW_SIZE,
                )
        try {
            shadow?.let { renderShadowMap(scene, it) }
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
            GL20.glUniform1f(GL20.glGetUniformLocation(program, "flatness"), flatness)
            GL20.glUniform1i(
                GL20.glGetUniformLocation(program, "shadowed"),
                if (shadow != null) 1 else 0,
            )
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "shadowMap"), 1)
            GL20.glUniform1f(
                GL20.glGetUniformLocation(program, "shadowOffset"),
                shadow?.let(::shadowOffset) ?: 0f,
            )
            // Bound even when unused: a shadow sampler must not meet some other texture.
            GL13.glActiveTexture(GL13.GL_TEXTURE1)
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, shadowMap)
            GL13.glActiveTexture(oldActiveTexture)
            if (scene.alpha < 1f) {
                GL11.glColorMask(false, false, false, false)
                drawMeshes(scene, shadow, translucent = false)
                GL11.glColorMask(true, true, true, true)
            }
            drawMeshes(scene, shadow, translucent = false)
            GL11.glDepthMask(false)
            drawMeshes(scene, shadow, translucent = true)
            GL11.glDepthMask(true)
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT)
        } finally {
            GL13.glActiveTexture(GL13.GL_TEXTURE1)
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, oldShadowTexture)
            GL13.glActiveTexture(oldActiveTexture)
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

    /** How much of the flat colours the scene takes: none at 45° and below, all straight down. */
    private fun flatness(scene: GpuScene): Float {
        val rows = scene.camera.relativeTo(0.0, 0.0, 0.0)
        val length = sqrt(rows[8] * rows[8] + rows[9] * rows[9] + rows[10] * rows[10])
        val t = ((-rows[9] / length - FLAT_FROM) / (1 - FLAT_FROM)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }

    /** How far out along a normal to look the shadow map up: a texel and a half, in blocks. */
    private fun shadowOffset(light: GpuSceneCamera): Float {
        val rows = light.relativeTo(0.0, 0.0, 0.0)
        val perBlock = sqrt(rows[0] * rows[0] + rows[1] * rows[1] + rows[2] * rows[2])
        return NORMAL_OFFSET_TEXELS * 2 / (perBlock * SHADOW_SIZE)
    }

    /**
     * The scene's depth as the light sees it, into [shadowMap]; the GUI's framebuffer, viewport and
     * scissor come back as they were.
     */
    private fun renderShadowMap(scene: GpuScene, light: GpuSceneCamera) {
        val oldFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING)
        val viewport = BufferUtils.createIntBuffer(VIEWPORT_INTS)
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport)
        val scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)
        try {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, shadowFramebuffer)
            GL11.glViewport(0, 0, SHADOW_SIZE, SHADOW_SIZE)
            GL11.glDisable(GL11.GL_SCISSOR_TEST)
            GL11.glDepthMask(true)
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT)
            GL11.glEnable(GL11.GL_DEPTH_TEST)
            GL11.glDepthFunc(GL11.GL_LEQUAL)
            // Pushed back a little, so a surface does not shadow itself in stripes.
            GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL)
            GL11.glPolygonOffset(SLOPE_OFFSET, UNIT_OFFSET)
            GL30.glBindVertexArray(vertexArray)
            GL20.glUseProgram(shadowProgram)
            val transform = GL20.glGetUniformLocation(shadowProgram, "transform")
            val scale = GL20.glGetUniformLocation(shadowProgram, "scale")
            for (mesh in scene.meshes) {
                if (mesh.quadCount == 0 || mesh.translucent) continue
                uniformRows(transform, light.relativeTo(mesh.originX, mesh.originY, mesh.originZ))
                GL20.glUniform1f(scale, mesh.scale.toFloat())
                bindMesh(mesh)
                GL11.glDrawElements(GL11.GL_TRIANGLES, mesh.quadCount * 6, GL11.GL_UNSIGNED_INT, 0L)
            }
        } finally {
            GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL)
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, oldFramebuffer)
            GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3])
            if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST)
        }
    }

    private fun uniformRows(location: Int, rows: FloatArray) {
        rowBuffer.clear()
        rowBuffer.put(rows).flip()
        GL20.glUniform4(location, rowBuffer)
    }

    private fun bindMesh(mesh: GpuSceneMesh) {
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffers.getOrPut(mesh) { upload(mesh) })
        GL20.glVertexAttribPointer(0, 2, GL11.GL_UNSIGNED_BYTE, false, VERTEX_BYTES, 0L)
        GL20.glVertexAttribPointer(1, 1, GL11.GL_SHORT, false, VERTEX_BYTES, 2L)
        GL20.glVertexAttribPointer(2, 4, GL11.GL_UNSIGNED_BYTE, true, VERTEX_BYTES, 4L)
        GL20.glVertexAttribPointer(3, 2, GL11.GL_BYTE, true, VERTEX_BYTES, 8L)
        GL20.glVertexAttribPointer(4, 1, GL11.GL_UNSIGNED_BYTE, true, VERTEX_BYTES, 10L)
    }

    private fun drawMeshes(scene: GpuScene, shadow: GpuSceneCamera?, translucent: Boolean) {
        val transform = GL20.glGetUniformLocation(program, "transform")
        val lightSpace = GL20.glGetUniformLocation(program, "lightSpace")
        val scale = GL20.glGetUniformLocation(program, "scale")
        val place = GL20.glGetUniformLocation(program, "place")
        for (mesh in scene.meshes) {
            if (mesh.quadCount == 0 || mesh.translucent != translucent) continue
            uniformRows(
                transform,
                scene.camera.relativeTo(mesh.originX, mesh.originY, mesh.originZ),
            )
            if (shadow != null)
                uniformRows(lightSpace, shadow.relativeTo(mesh.originX, mesh.originY, mesh.originZ))
            GL20.glUniform1f(scale, mesh.scale.toFloat())
            // Where the mesh lies, wrapped: only the ripples on shiny faces look at it.
            GL20.glUniform3f(
                place,
                (mesh.originX % RIPPLE_WRAP).toFloat(),
                (mesh.originY % RIPPLE_WRAP).toFloat(),
                (mesh.originZ % RIPPLE_WRAP).toFloat(),
            )
            bindMesh(mesh)
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
            program =
                GpuShaders.link(
                    VERTEX_SHADER,
                    FRAGMENT_SHADER,
                    "cell",
                    "height",
                    "tint",
                    "lean",
                    "sky",
                )
        if (shadowProgram == 0)
            shadowProgram =
                GpuShaders.link(
                    SHADOW_VERTEX_SHADER,
                    SHADOW_FRAGMENT_SHADER,
                    "cell",
                    "height",
                    "tint",
                    "lean",
                    "sky",
                )
        if (shadowFramebuffer == 0) createShadowMap()
        if (vertexArray == 0) {
            vertexArray = GL30.glGenVertexArrays()
            indexBuffer = GL15.glGenBuffers()
            GL30.glBindVertexArray(vertexArray)
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, indexBuffer)
            GL20.glEnableVertexAttribArray(0)
            GL20.glEnableVertexAttribArray(1)
            GL20.glEnableVertexAttribArray(2)
            GL20.glEnableVertexAttribArray(3)
            GL20.glEnableVertexAttribArray(4)
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

    private fun createShadowMap() {
        val oldTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
        val oldFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING)
        shadowMap = GL11.glGenTextures()
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, shadowMap)
        GL11.glTexImage2D(
            GL11.GL_TEXTURE_2D,
            0,
            GL14.GL_DEPTH_COMPONENT24,
            SHADOW_SIZE,
            SHADOW_SIZE,
            0,
            GL11.GL_DEPTH_COMPONENT,
            GL11.GL_FLOAT,
            null as ByteBuffer?,
        )
        // Compared in the sampler and filtered: each lookup blends four texels' verdicts.
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(
            GL11.GL_TEXTURE_2D,
            GL14.GL_TEXTURE_COMPARE_MODE,
            GL30.GL_COMPARE_REF_TO_TEXTURE,
        )
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_FUNC, GL11.GL_LEQUAL)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
        shadowFramebuffer = GL30.glGenFramebuffers()
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, shadowFramebuffer)
        GL30.glFramebufferTexture2D(
            GL30.GL_FRAMEBUFFER,
            GL30.GL_DEPTH_ATTACHMENT,
            GL11.GL_TEXTURE_2D,
            shadowMap,
            0,
        )
        GL11.glDrawBuffer(GL11.GL_NONE)
        GL11.glReadBuffer(GL11.GL_NONE)
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, oldFramebuffer)
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, oldTexture)
    }

    private fun upload(mesh: GpuSceneMesh): Int {
        val bytes = staging(mesh.vertexCount * VERTEX_BYTES)
        for ((vertex, argb) in mesh.colors.withIndex()) {
            val packed = mesh.vertices[vertex]
            val shading = mesh.shading[vertex]
            bytes.put(packed.toByte()).put((packed shr 8).toByte())
            bytes.put((packed shr 16).toByte()).put((packed shr 24).toByte())
            bytes.put((argb shr 16).toByte()).put((argb shr 8).toByte()).put(argb.toByte())
            bytes.put((argb ushr 24).toByte())
            bytes.put(shading.toByte()).put((shading shr 8).toByte())
            bytes.put((shading shr 16).toByte()).put(0)
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
        if (shadowProgram != 0) GL20.glDeleteProgram(shadowProgram)
        if (shadowFramebuffer != 0) GL30.glDeleteFramebuffers(shadowFramebuffer)
        GpuTextureDeletion.delete(shadowMap)
        program = 0
        shadowProgram = 0
        shadowFramebuffer = 0
        shadowMap = 0
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
            // How an up face's normal leans along x and z, and how much sky it sees.
            in vec2 lean;
            in float sky;
            uniform vec2 screen;
            uniform vec2 origin;
            uniform float scale;
            // World to local GUI x, local GUI y and depth, one row each.
            uniform vec4 transform[3];
            uniform vec3 place;
            uniform float shadowOffset;
            // World to the shadow map's clip space, one row each.
            uniform vec4 lightSpace[3];
            out vec3 base;
            out vec3 normal;
            out vec3 position;
            out vec3 shadowAt;
            out float occlusion;
            out float opacity;
            flat out float shiny;
            const vec3 FACES[6] = vec3[6](
              vec3(0, 1, 0), vec3(1, 0, 0), vec3(-1, 0, 0), vec3(0, 0, 1), vec3(0, 0, -1), vec3(0, -1, 0));
            void main() {
              vec4 world = vec4(cell.x * scale, height / 16.0, cell.y * scale, 1.0);
              vec2 pixel = origin + vec2(dot(transform[0], world), dot(transform[1], world));
              gl_Position = vec4(pixel.x * 2.0 / screen.x - 1.0, 1.0 - pixel.y * 2.0 / screen.y, dot(transform[2], world), 1.0);
              // The alpha byte is the face's direction, and 8 when it shines.
              int look = int(tint.a * 255.0 + 0.5);
              int face = look & 7;
              normal = face == 0 ? vec3(lean.x, sqrt(max(1.0 - dot(lean, lean), 0.0)), lean.y) : FACES[face];
              shiny = float((look & 8) != 0);
              // Its top four bits say how see-through it is, in sixteenths.
              opacity = 1.0 - float((look >> 4) & 15) / 16.0;
              occlusion = sky;
              base = tint.rgb;
              position = place + world.xyz;
              vec4 lifted = vec4(world.xyz + FACES[face] * shadowOffset, 1.0);
              shadowAt = vec3(dot(lightSpace[0], lifted), dot(lightSpace[1], lifted), dot(lightSpace[2], lifted)) * 0.5 + 0.5;
            }
            """
                .trimIndent()
        val SHADOW_VERTEX_SHADER =
            """
            #version 330 core
            in vec2 cell;
            in float height;
            uniform float scale;
            // World to the shadow map's clip space, one row each.
            uniform vec4 transform[3];
            void main() {
              vec4 world = vec4(cell.x * scale, height / 16.0, cell.y * scale, 1.0);
              gl_Position = vec4(dot(transform[0], world), dot(transform[1], world), dot(transform[2], world), 1.0);
            }
            """
                .trimIndent()
        val SHADOW_FRAGMENT_SHADER =
            """
            #version 330 core
            void main() {}
            """
                .trimIndent()
        /** Texels a side of the shadow map. */
        const val SHADOW_SIZE = 2048
        /** A light dimmer than this casts no shadows worth a pass. */
        const val MIN_SHADOW_LIGHT = 0.05f
        const val SLOPE_OFFSET = 1.1f
        const val UNIT_OFFSET = 2f
        /** Texels out along a surface's normal its shadow lookup starts. */
        const val NORMAL_OFFSET_TEXELS = 1.5f
        /** How far down the camera looks, toward the viewer's y, before light gives way. */
        const val FLAT_FROM = 0.7071f
        const val VIEWPORT_INTS = 16
        /** Mesh origins wrap at this many blocks for ripples, keeping floats precise. */
        const val RIPPLE_WRAP = 4096.0
        /**
         * Per vertex: x and z steps as bytes, y sixteenths as a short, RGBA bytes, then the lean's
         * x and z as signed bytes, the sky seen and a spare byte.
         */
        const val VERTEX_BYTES = 12
        val FRAGMENT_SHADER =
            """
            #version 330 core
            in vec3 base;
            in vec3 normal;
            in vec3 position;
            in vec3 shadowAt;
            in float occlusion;
            in float opacity;
            flat in float shiny;
            uniform float alpha;
            uniform int shadowed;
            uniform sampler2DShadow shadowMap;
            uniform vec3 lightDir;
            uniform vec3 lightColor;
            uniform float intensity;
            uniform float ambient;
            uniform vec3 toViewer;
            // How much the camera looks straight down: the flat colours' share.
            uniform float flatness;
            out vec4 color;
            // Smooth noise over the ground, one bump every few blocks, so a ripple never repeats per
            // block.
            float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
            float noise(vec2 p) {
              vec2 cell = floor(p);
              vec2 f = fract(p);
              vec2 u = f * f * (3.0 - 2.0 * f);
              return mix(mix(hash(cell), hash(cell + vec2(1, 0)), u.x),
                         mix(hash(cell + vec2(0, 1)), hash(cell + vec2(1, 1)), u.x), u.y);
            }
            // How much of the light reaches here: nine filtered lookups, each blending four texels.
            float lightness() {
              if (shadowed == 0 || any(lessThan(shadowAt, vec3(0.0))) || any(greaterThan(shadowAt, vec3(1.0))))
                return 1.0;
              vec2 texel = 1.0 / vec2(textureSize(shadowMap, 0));
              float depth = shadowAt.z - 0.0002;
              float lit = 0.0;
              for (int x = -1; x <= 1; x++)
                for (int y = -1; y <= 1; y++)
                  lit += texture(shadowMap, vec3(shadowAt.xy + vec2(x, y) * texel, depth));
              return lit / 9.0;
            }
            void main() {
              vec3 n = normalize(normal);
              float light = lightness();
              // Ambient light takes half the light's colour, so night is cool all over; a hollow
              // sees less of the sky, and its walls catch some of the direct light too.
              vec3 sky = mix(vec3(1.0), lightColor, 0.5) * ambient * occlusion;
              vec3 sun = (1.0 - ambient) * intensity * max(dot(n, lightDir), 0.0) * lightColor * (1.0 + occlusion) * 0.5;
              vec3 shade = base * (sky + sun * light);
              // From above, light gives way to the colours as they are, shaded only by the ground's
              // slope as a map shades its hills: rising to the east and south faces the north-west.
              vec2 slope = -n.xz / max(n.y, 0.2);
              float hills = clamp(1.0 + 0.6 * (slope.x + slope.y), 0.65, 1.3);
              shade = mix(shade, base * hills, flatness);
              if (shiny > 0.5) {
                // A shiny face mirrors the light. Flat ones swell gently, two octaves of noise
                // several blocks wide, so a lake shimmers unevenly instead of all at once.
                vec2 p = position.xz / 7.0;
                vec3 ripple = n.y > 0.5
                  ? 0.22 * vec3(noise(p) + 0.5 * noise(p * 2.3 + 17.0) - 0.75, 0.0,
                                noise(p + 41.0) + 0.5 * noise(p * 2.3 + 59.0) - 0.75)
                  : vec3(0.0);
                vec3 m = normalize(n + ripple);
                float glint = pow(max(dot(m, normalize(lightDir + toViewer)), 0.0), 24.0);
                shade += lightColor * glint * intensity * 0.25 * light;
              }
              // See-through faces hide more of what is behind them the flatter they are seen.
              float seen = opacity < 1.0
                ? mix(opacity, 1.0, pow(1.0 - max(dot(n, toViewer), 0.0), 5.0))
                : 1.0;
              color = vec4(min(shade, vec3(1.0)), alpha * seen);
            }
            """
                .trimIndent()
    }
}
