package io.github.fopwoc.mods.palimpsest.benchmark

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BenchmarkStorageSuiteTest {
  @Test
  fun horizontalScalingReadsBoundedSamplesAcrossLods() {
    val directory = Files.createTempDirectory("palimpsest-suite-wide-")
    try {
      val result =
          BenchmarkStorageSuite.run(
              directory,
              listOf(BenchmarkStorageSuite.Scenario("small", BenchmarkGenerator.Pattern.SPARSE, 1)),
              wideWorldSide = 512,
          )
      val report = Files.readString(result.file)
      assertEquals(BenchmarkStorageSuite.Status.PASS, result.status, report)
      assertTrue(report.contains("case_status=PASS case=structured-colors"))
      assertTrue(report.contains("case_status=PASS case=color-distribution"))
      val colors = report.lineSequence().filter { it.startsWith("case=color-distribution ") }.toList()
      assertEquals(7, colors.size)
      val colorSizePattern = Regex("(?:sealed|plain)_bytes=(\\d+)")
      for (line in colors) {
        val sizes = colorSizePattern.findAll(line).map { it.groupValues[1].toLong() }.toList()
        assertTrue(sizes[0] <= sizes[1], line)
      }
      val byPattern = colors.associateBy { Regex("pattern=([^ ]+)").find(it)!!.groupValues[1] }
      fun sizes(pattern: String): List<Long> =
          colorSizePattern.findAll(byPattern.getValue(pattern)).map { it.groupValues[1].toLong() }.toList()
      assertTrue(sizes("uniform")[0] < sizes("uniform")[1] / 10)
      assertTrue(sizes("solid-footprint")[0] < sizes("solid-footprint")[1])
      assertTrue(sizes("scattered-solid")[0] < sizes("scattered-solid")[1])
      for (pattern in listOf("near-uniform", "terrain-bands", "varied", "scattered-varied")) {
        assertEquals(sizes(pattern)[1], sizes(pattern)[0], pattern)
      }
      assertTrue(report.contains("case=wide-world tiles="))
      assertTrue(report.contains("wide_lod=4 covered_tiles=16384 tile_lookups=16384"))
      assertTrue(report.contains("wide_lod=5 covered_tiles=65536 tile_lookups=4096"))
      assertTrue(report.contains("wide_lod=6 covered_tiles=262144 tile_lookups=1024"))
      assertTrue(report.contains("wide_lod=12 covered_tiles=1073741824 tile_lookups=256"))
      val smallCache =
          report.lineSequence().first {
            it.startsWith("wide_cache_limit=16 disk_index=false wide_lod=6 ")
          }
      val largeCache =
          report.lineSequence().first {
            it.startsWith("wide_cache_limit=256 disk_index=false wide_lod=6 ")
          }
      val indexedCache =
          report.lineSequence().first {
            it.startsWith("wide_cache_limit=256 disk_index=true wide_lod=6 ")
          }
      val opens = Regex("region_opens=(\\d+)")
      assertTrue(
          opens.find(largeCache)!!.groupValues[1].toLong() <
              opens.find(smallCache)!!.groupValues[1].toLong()
      )
      assertTrue(indexedCache.contains("index_cache_hits=192"))
      assertTrue(report.contains("tile_lookups=16384"))
      val bytes = Regex("logical_record_bytes_read=(\\d+)").find(report)!!.groupValues[1].toLong()
      assertTrue(bytes < 500_000, "Sample reads transferred $bytes logical record bytes")
    } finally {
      Files.walk(directory).use { paths ->
        paths.sorted(Comparator.reverseOrder()).forEach(Files::delete)
      }
    }
  }

  @Test
  fun sparseAndMixedIndexArraysDoNotExceedFixedMaskBaseline() {
    val directory = Files.createTempDirectory("palimpsest-suite-index-")
    try {
      val result =
          BenchmarkStorageSuite.run(
              directory,
              listOf(
                  BenchmarkStorageSuite.Scenario(
                      "sparse",
                      BenchmarkGenerator.Pattern.SPARSE,
                      2_000,
                  ),
                  BenchmarkStorageSuite.Scenario(
                      "mixed",
                      BenchmarkGenerator.Pattern.MIXED,
                      2_000,
                  ),
              ),
          )
      val report = Files.readString(result.file)
      assertEquals(BenchmarkStorageSuite.Status.PASS, result.status, report)
      val arrays =
          Regex("reopened_index_array_bytes=(\\d+)")
              .findAll(report)
              .map { it.groupValues[1].toLong() }
              .toList()
      assertEquals(2, arrays.size)
      assertTrue(arrays[0] < 1_882_112, "Sparse index occupied ${arrays[0]} bytes")
      assertTrue(arrays[1] <= 1_882_112, "Mixed index occupied ${arrays[1]} bytes")
    } finally {
      Files.walk(directory).use { paths ->
        paths.sorted(Comparator.reverseOrder()).forEach(Files::delete)
      }
    }
  }

  @Test
  fun millionSinglePixelLayersStaySmallAndReopenCorrectly() {
    val directory = Files.createTempDirectory("palimpsest-suite-million-")
    try {
      val result =
          BenchmarkStorageSuite.run(
              directory,
              listOf(
                  BenchmarkStorageSuite.Scenario(
                      "million",
                      BenchmarkGenerator.Pattern.ADVERSARIAL,
                      62_500,
                  )
              ),
          )
      val report = Files.readString(result.file)
      assertEquals(BenchmarkStorageSuite.Status.PASS, result.status, report)
      val generated = report.lineSequence().first { it.startsWith("generated_nanos=") }
      val sealed = Regex("sealed_bytes=(\\d+)").find(generated)!!.groupValues[1].toLong()
      assertTrue(sealed < 6_000_000, "Million-layer segment data occupied $sealed bytes")
      val reopened = report.lineSequence().first { it.startsWith("initial_reopen_nanos=") }
      val arrays =
          Regex("reopened_index_array_bytes=(\\d+)").find(reopened)!!.groupValues[1].toLong()
      assertTrue(arrays < 35_000_000, "Million-layer index occupied $arrays bytes")
      assertTrue(report.contains("case_status=PASS case=million"))
    } finally {
      Files.walk(directory).use { paths ->
        paths.sorted(Comparator.reverseOrder()).forEach(Files::delete)
      }
    }
  }

  @Test
  fun stoppingWritesAReportAndRemovesTemporaryFixtures() {
    val directory = Files.createTempDirectory("palimpsest-suite-stop-")
    try {
      val result =
          BenchmarkStorageSuite.run(
              directory,
              listOf(
                  BenchmarkStorageSuite.Scenario(
                      "stopped",
                      BenchmarkGenerator.Pattern.ADVERSARIAL,
                      5_000,
                  )
              ),
              shouldStop = { true },
          )
      assertEquals(BenchmarkStorageSuite.Status.STOPPED, result.status)
      assertTrue(Files.readString(result.file).contains("status=STOPPED"))
      assertEquals(1, Files.list(directory.resolve("reports")).use { it.count() })
    } finally {
      Files.walk(directory).use { paths ->
        paths.sorted(Comparator.reverseOrder()).forEach(Files::delete)
      }
    }
  }

  @Test
  fun isolatedSuiteReportsStorageReadsCheckpointsAndReopen() {
    val directory = Files.createTempDirectory("palimpsest-suite-")
    try {
      val result =
          BenchmarkStorageSuite.run(
              directory,
              listOf(
                  BenchmarkStorageSuite.Scenario(
                      "small-sparse",
                      BenchmarkGenerator.Pattern.SPARSE,
                      30,
                  ),
                  BenchmarkStorageSuite.Scenario(
                      "small-mixed",
                      BenchmarkGenerator.Pattern.MIXED,
                      30,
                  ),
              ),
          )
      val report = Files.readString(result.file)
      assertEquals(BenchmarkStorageSuite.Status.PASS, result.status, report)
      assertTrue(report.contains("case_status=PASS case=small-sparse"))
      assertTrue(report.contains("case_status=PASS case=small-mixed"))
      assertTrue(report.contains("before_checkpoint epoch=30 median_us="))
      assertTrue(report.contains("after_checkpoint epoch=31 median_us="))
      assertTrue(report.contains("after_reopen epoch=31 median_us="))
      assertTrue(report.contains("noop_layers_discarded=48 noop_bytes_added=0"))
      assertTrue(report.contains("status=PASS"))
      assertEquals(1, Files.list(directory).use { it.count() })
      assertEquals(1, Files.list(directory.resolve("reports")).use { it.count() })
    } finally {
      Files.walk(directory).use { paths ->
        paths.sorted(Comparator.reverseOrder()).forEach(Files::delete)
      }
    }
  }
}
