package io.github.fopwoc.mods.framework

import cpw.mods.fml.common.Mod
import java.io.DataInputStream
import java.lang.reflect.InvocationTargetException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FrameworkBootstrapTest {
    private val bootstrapClass = Class.forName("io.github.fopwoc.mods.framework.FrameworkBootstrap")

    @Test
    fun acceptsExactlyJava24To26() {
        listOf("24", "25", "26").forEach(::requireSupportedJava)

        listOf("1.8", "17", "23", "27").forEach { version ->
            val error =
                assertFailsWith<IllegalStateException> {
                    requireSupportedJava(version)
                }
            assertContains(error.message.orEmpty(), "supports Java 24-26")
        }
    }

    @Test
    fun forgeEntrypointUsesJava8BytecodeAndCurrentModVersion() {
        val classFile =
            DataInputStream(
                bootstrapClass.getResourceAsStream("FrameworkBootstrap.class")
            )
        classFile.use {
            assertEquals(0xCAFEBABE.toInt(), it.readInt())
            it.readUnsignedShort()
            assertEquals(52, it.readUnsignedShort())
        }

        assertEquals(
            ModMetadata.MOD_VERSION,
            bootstrapClass.getAnnotation(Mod::class.java).version,
        )
    }

    @Test
    fun delegatesInitializationToKotlinCoreOnSupportedJava() {
        bootstrapClass.getMethod("onInit", cpw.mods.fml.common.event.FMLInitializationEvent::class.java)
            .invoke(bootstrapClass.getDeclaredConstructor().newInstance(), null)
    }

    private fun requireSupportedJava(version: String) {
        try {
            bootstrapClass.getMethod("requireSupportedJava", String::class.java).invoke(null, version)
        } catch (failure: InvocationTargetException) {
            throw failure.cause ?: failure
        }
    }
}
