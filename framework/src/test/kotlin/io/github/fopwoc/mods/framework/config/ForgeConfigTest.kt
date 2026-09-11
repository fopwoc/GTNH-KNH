package io.github.fopwoc.mods.framework.config

import cpw.mods.fml.relauncher.FMLInjectionData
import java.io.File
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ForgeConfigTest {
  @BeforeTest
  fun seedMinecraftHome() {
    // Configuration resolves paths relative to the game directory that FML injects at launch.
    FMLInjectionData::class.java.getDeclaredField("minecraftHome").apply {
      isAccessible = true
      set(null, Files.createTempDirectory("minecraft-home").toFile())
    }
  }

  private enum class Side {
    LEFT,
    RIGHT,
  }

  private class TestConfig :
      ForgeConfig(modId = "testmod", fileName = "test.cfg", languageKeyPrefix = "config.test") {
    val enabled by boolean("enabled", default = true, comment = "on")
    val interval by int("interval", default = 20, min = 1, max = 100, comment = "ticks")
    val stale by
        int(
            "stale",
            default = 10,
            min = 1,
            comment = "ticks",
            normalize = { it.coerceAtLeast(interval * 2) },
        )
    val side by enum("side", default = Side.LEFT, comment = "side")
    val label by string("label", default = "x", comment = "label", normalize = { it.trim() })
  }

  @Test
  fun loadWritesNormalizedDefaults() {
    val dir = Files.createTempDirectory("forge-config").toFile()
    val config = TestConfig()

    config.load(dir)

    val text = File(dir, "test.cfg").readText()
    assertTrue(text.contains("I:interval=20"), text)
    assertTrue(text.contains("I:stale=40"), text)
    assertTrue(text.contains("S:side=left"), text)
    assertEquals(40, config.stale)
    assertEquals(1L, config.revision)
  }

  @Test
  fun externalEditsAreNormalizedAndPickedUp() {
    val dir = Files.createTempDirectory("forge-config").toFile()
    val config = TestConfig()
    config.load(dir)
    val file = File(dir, "test.cfg")

    val edited =
        file
            .readText()
            .replace("I:interval=20", "I:interval=500")
            .replace("S:side=left", "S:side=RIGHT")
            .replace("S:label=x", "S:label=  hello ")
            .replace("B:enabled=true", "B:enabled=false")
    file.writeText(edited)
    file.setLastModified(file.lastModified() + 5_000)

    assertTrue(config.refreshIfChanged())
    assertFalse(config.refreshIfChanged())
    assertEquals(100, config.interval)
    assertEquals(200, config.stale)
    assertEquals(Side.RIGHT, config.side)
    assertEquals("hello", config.label)
    assertFalse(config.enabled)
    assertEquals(2L, config.revision)
  }
}
