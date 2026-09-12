package io.github.fopwoc.mods.framework.serialization

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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
  ) = writeText(file, json.encodeToString(value))

  /**
   * Writes through a sibling temp file and an atomic rename, so a crash mid-write leaves the
   * previous file intact instead of a truncated one.
   */
  fun writeText(file: File, text: String) {
    file.parentFile?.mkdirs()
    val temp = File(file.parentFile, file.name + ".tmp")
    temp.writeText(text)
    try {
      Files.move(
          temp.toPath(),
          file.toPath(),
          StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.ATOMIC_MOVE,
      )
    } catch (_: AtomicMoveNotSupportedException) {
      Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
  }
}
