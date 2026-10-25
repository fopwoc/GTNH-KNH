package io.github.fopwoc.mods.framework.ui.compose.minecraft.render

import org.lwjgl.opengl.GL20

/** Compiles and links the GPU canvas shader programs, binding attributes to locations in order. */
internal object GpuShaders {
    @Suppress("TooGenericExceptionCaught")
    fun link(vertexCode: String, fragmentCode: String, vararg attributes: String): Int {
        val vertex = compile(GL20.GL_VERTEX_SHADER, vertexCode)
        val fragment =
            try {
                compile(GL20.GL_FRAGMENT_SHADER, fragmentCode)
            } catch (failure: Throwable) {
                GL20.glDeleteShader(vertex)
                throw failure
            }
        val result = GL20.glCreateProgram()
        try {
            GL20.glAttachShader(result, vertex)
            GL20.glAttachShader(result, fragment)
            attributes.forEachIndexed { location, name ->
                GL20.glBindAttribLocation(result, location, name)
            }
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
}
