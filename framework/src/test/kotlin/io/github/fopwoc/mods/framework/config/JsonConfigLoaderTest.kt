package io.github.fopwoc.mods.framework.config

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.Serializable

class JsonConfigLoaderTest {
  @Test
  fun loadsNormalizesAndRewritesConfig() {
    val root = createTempDirectory(prefix = "json-config-loader-test").toFile()
    try {
      val config =
          JsonConfigLoader.live(
                  configDirectory = root,
                  fileName = "sample.json",
                  defaultValue = ::SampleConfig,
                  normalize = { current ->
                    current.copy(retries = current.retries.coerceIn(1, 5))
                  },
              )
              .load()

      assertEquals(SampleConfig(enabled = false, retries = 1), config)
      val written = root.resolve("sample.json")
      assertTrue(written.isFile)
      assertTrue(written.readText().contains("\"retries\": 1"))
    } finally {
      root.deleteRecursively()
    }
  }

  @Test
  fun liveConfigReloadsEditsAndKeepsLastGoodValueOnInvalidJson() {
    val root = createTempDirectory(prefix = "json-live-config-test").toFile()
    try {
      val warnings = mutableListOf<String>()
      val config =
          JsonConfigLoader.live(
              configDirectory = root,
              fileName = "sample.json",
              defaultValue = ::SampleConfig,
              normalize = { current ->
                current.copy(retries = current.retries.coerceIn(1, 5))
              },
              onReadFailure = { file, throwable ->
                warnings += "${file.name}:${throwable::class.simpleName}"
              },
          )

      assertEquals(SampleConfig(enabled = false, retries = 1), config.load())

      val written = root.resolve("sample.json")
      written.writeText("""{"enabled":true,"retries":99}""")
      assertTrue(config.refreshIfChanged())
      assertEquals(SampleConfig(enabled = true, retries = 5), config.current())

      written.writeText("{ definitely-not-json }")
      assertFalse(config.refreshIfChanged())
      assertEquals(SampleConfig(enabled = true, retries = 5), config.current())
      assertEquals(listOf("sample.json:JsonDecodingException"), warnings)
    } finally {
      root.deleteRecursively()
    }
  }

  @Serializable
  private data class SampleConfig(
      val enabled: Boolean = false,
      val retries: Int = 0,
  )
}
