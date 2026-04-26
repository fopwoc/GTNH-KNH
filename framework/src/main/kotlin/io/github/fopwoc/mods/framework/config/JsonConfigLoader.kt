package io.github.fopwoc.mods.framework.config

import io.github.fopwoc.mods.framework.serialization.FrameworkJson
import io.github.fopwoc.mods.framework.serialization.JsonFileStorage
import java.io.File
import kotlinx.serialization.json.Json

object JsonConfigLoader {
    inline fun <reified T> live(
        configDirectory: File,
        fileName: String,
        json: Json = FrameworkJson.prettyConfig,
        noinline defaultValue: () -> T,
        noinline normalize: (T) -> T = { it },
        noinline onReadFailure: (File, Throwable) -> Unit = { _, _ -> }
    ): LiveJsonConfig<T> {
        return LiveJsonConfig(
            file = File(configDirectory, fileName),
            defaultValue = defaultValue,
            normalize = normalize,
            onReadFailure = onReadFailure,
            read = { file ->
                json.decodeFromString<T>(file.readText())
            },
            write = { file, value ->
                JsonFileStorage.write(file, value, json)
            }
        )
    }
}

