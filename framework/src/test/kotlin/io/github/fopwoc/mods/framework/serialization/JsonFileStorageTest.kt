package io.github.fopwoc.mods.framework.serialization

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsonFileStorageTest {
    @Test
    fun readsDefaultWhenMissingAndWritesValue() {
        val root = createTempDirectory(prefix = "json-file-storage-test").toFile()
        try {
            val file = File(root, "sample.json")
            val value = JsonFileStorage.readOrDefault(file, defaultValue = { SampleConfig(enabled = true, retries = 3) })

            assertEquals(SampleConfig(enabled = true, retries = 3), value)

            JsonFileStorage.write(file, value)

            assertTrue(file.isFile)
            val roundTrip = JsonFileStorage.readOrDefault(file, defaultValue = { SampleConfig() })
            assertEquals(value, roundTrip)
        } finally {
            root.deleteRecursively()
        }
    }

    @kotlinx.serialization.Serializable
    private data class SampleConfig(
        val enabled: Boolean = false,
        val retries: Int = 0
    )
}

