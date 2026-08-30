package io.github.fopwoc.mods.framework.config

import java.io.File

class LiveJsonConfig<T>
@PublishedApi
internal constructor(
    private val file: File,
    private val defaultValue: () -> T,
    private val normalize: (T) -> T,
    private val onReadFailure: (File, Throwable) -> Unit,
    private val read: (File) -> T,
    private val write: (File, T) -> Unit,
) {
  @Volatile private var currentValue: T? = null

  @Volatile
  var revision: Long = 0
    private set

  private var lastAppliedStamp: FileStamp? = null
  private var lastFailedStamp: FileStamp? = null

  fun load(): T =
      synchronized(this) {
        val loadedValue = readCurrentFileOrNull() ?: defaultNormalizedValue()
        applyLoadedValue(loadedValue, countAsRevision = true)
      }

  fun current(): T =
      currentValue
          ?: synchronized(this) {
            currentValue ?: load()
          }

  fun refreshIfChanged(): Boolean =
      synchronized(this) {
        val stamp = stampOf(file)
        if (stamp == lastAppliedStamp || stamp == lastFailedStamp) {
          return false
        }

        val previousValue = currentValue ?: load()
        val nextValue =
            if (stamp.exists) {
              readCurrentFileOrNull()
                  ?: run {
                    lastFailedStamp = stamp
                    return false
                  }
            } else {
              defaultNormalizedValue()
            }

        applyLoadedValue(nextValue, countAsRevision = previousValue != nextValue)
        return previousValue != nextValue
      }

  private fun defaultNormalizedValue(): T = normalize(defaultValue())

  private fun readCurrentFileOrNull(): T? {
    if (!file.isFile) {
      return null
    }

    return runCatching {
          normalize(read(file))
        }
        .onFailure { throwable ->
          onReadFailure(file, throwable)
        }
        .getOrNull()
  }

  private fun applyLoadedValue(value: T, countAsRevision: Boolean): T {
    write(file, value)
    currentValue = value
    lastAppliedStamp = stampOf(file)
    lastFailedStamp = null
    if (countAsRevision) {
      revision += 1
    }
    return value
  }

  private data class FileStamp(
      val exists: Boolean,
      val lastModified: Long,
      val length: Long,
  )

  private fun stampOf(file: File): FileStamp {
    return FileStamp(
        exists = file.exists(),
        lastModified = file.lastModified(),
        length = file.length(),
    )
  }
}
