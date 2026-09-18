package io.github.fopwoc.mods.framework

import cpw.mods.fml.common.Mod
import java.io.DataInputStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FrameworkBootstrapTest {
    @Test
    fun acceptsExactlyJava24To26() {
        listOf("24", "25", "26").forEach(FrameworkBootstrap::requireSupportedJava)

        listOf("1.8", "17", "23", "27").forEach { version ->
            val error =
                assertFailsWith<IllegalStateException> {
                    FrameworkBootstrap.requireSupportedJava(version)
                }
            assertContains(error.message.orEmpty(), "supports Java 24-26")
        }
    }

    @Test
    fun forgeEntrypointUsesJava8BytecodeAndCurrentModVersion() {
        val classFile =
            DataInputStream(
                FrameworkBootstrap::class.java.getResourceAsStream("FrameworkBootstrap.class")
            )
        classFile.use {
            assertEquals(0xCAFEBABE.toInt(), it.readInt())
            it.readUnsignedShort()
            assertEquals(52, it.readUnsignedShort())
        }

        assertEquals(
            MOD_VERSION,
            FrameworkBootstrap::class.java.getAnnotation(Mod::class.java).version,
        )
    }

    @Test
    fun delegatesInitializationToKotlinCoreOnSupportedJava() {
        FrameworkBootstrap().onInit(null)
    }
}
