package io.github.fopwoc.mods.palimpsest.benchmark

import io.github.fopwoc.mods.palimpsest.storage.TileHistoryStore
import io.github.fopwoc.mods.palimpsest.storage.TileKey
import io.github.fopwoc.mods.palimpsest.storage.TileLayer
import java.nio.file.Path

/** Measures solid-color layers against varied tiles that must keep their original encoding. */
internal object StructuredColorScenario {
  data class Result(
      val flatBytes: Long,
      val variedBytes: Long,
      val patchBytes: Long,
      val flatBaselineBytes: Long,
      val variedBaselineBytes: Long,
      val patchBaselineBytes: Long,
  )

  fun run(directory: Path): Result {
    val flat = ByteArray(TileLayer.PIXELS) { 7 }
    val varied =
        ByteArray(TileLayer.PIXELS) { position ->
          val x = position % TileLayer.SIDE
          val z = position / TileLayer.SIDE
          ((x * 29 + z * 17) and 255).toByte()
        }
    val patched =
        flat.copyOf().apply {
          for (z in 4 until 12) for (x in 4 until 12) this[z * TileLayer.SIDE + x] = 9
        }
    val flatKeys = List(256) { TileKey(it, 0) }
    val variedKeys = List(256) { TileKey(it, 1) }
    val patchKeys = List(256) { TileKey(it, 2) }
    val result =
        TileHistoryStore(directory).use { store ->
          val flatBytes =
              store.append((flatKeys + patchKeys).map { TileLayer.full(it, 0, flat) }).bytesAdded
          val variedBytes =
              store.append(variedKeys.map { TileLayer.full(it, 0, varied) }).bytesAdded
          val patchBytes =
              store
                  .append(
                      patchKeys.map { key ->
                        checkNotNull(TileLayer.changed(key, 1, flat, patched))
                      }
                  )
                  .bytesAdded
          store.reload()
          for (key in flatKeys) check(store.readPixel(key, 1, 100) == 7)
          for (key in variedKeys) check(store.read(key, 1)?.colors?.contentEquals(varied) == true)
          for (key in patchKeys) {
            check(store.read(key, 0)?.colors?.contentEquals(flat) == true)
            check(store.read(key, 1)?.colors?.contentEquals(patched) == true)
            check(store.readPixel(key, 1, 100) == 9)
            check(store.readPixel(key, 1, 0) == 7)
          }
          Result(
              flatBytes = flatBytes,
              variedBytes = variedBytes,
              patchBytes = patchBytes,
              flatBaselineBytes = segmentBytes(512, 258),
              variedBaselineBytes = segmentBytes(256, 258),
              patchBaselineBytes = segmentBytes(256, 98),
          )
        }
    check(result.flatBytes < result.flatBaselineBytes / 10)
    check(result.patchBytes < result.patchBaselineBytes / 2)
    check(result.variedBytes == result.variedBaselineBytes)
    return result
  }

  private fun segmentBytes(tiles: Int, recordBytes: Int): Long = 12L + tiles * (12L + recordBytes)
}
