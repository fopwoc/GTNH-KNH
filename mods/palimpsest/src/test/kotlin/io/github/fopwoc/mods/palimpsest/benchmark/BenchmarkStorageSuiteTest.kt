package io.github.fopwoc.mods.palimpsest.benchmark

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BenchmarkStorageSuiteTest {
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
