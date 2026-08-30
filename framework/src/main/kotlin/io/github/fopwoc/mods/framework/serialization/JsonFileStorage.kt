package io.github.fopwoc.mods.framework.serialization

import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object JsonFileStorage {

  fun modConfigFile(
      minecraftDirectory: File,
      modId: String,
      vararg relativeSegments: String,
  ): File {
    var current = File(minecraftDirectory, "config/$modId")
    relativeSegments.forEach { segment ->
      current = File(current, segment)
    }
    return current
  }

  inline fun <reified T> readOrDefault(
      file: File,
      json: Json = FrameworkJson.prettyConfig,
      defaultValue: () -> T,
      noinline onReadFailure: ((Throwable) -> Unit)? = null,
  ): T {
    if (!file.isFile) {
      return defaultValue()
    }

    return runCatching {
          json.decodeFromString<T>(file.readText())
        }
        .onFailure { throwable ->
          onReadFailure?.invoke(throwable)
        }
        .getOrElse {
          defaultValue()
        }
  }

  inline fun <reified T> write(
      file: File,
      value: T,
      json: Json = FrameworkJson.prettyConfig,
  ) {
    file.parentFile?.mkdirs()
    file.writeText(json.encodeToString(value))
  }
}
