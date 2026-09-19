package io.github.fopwoc.mods.palimpsest.benchmark

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BenchmarkStorageSuiteTest {
    @Test
    fun suiteRunsAtSmallScaleAndReportsEveryCase() {
        val directory = Files.createTempDirectory("palimpsest-suite-")
        try {
            val result =
                BenchmarkStorageSuite.run(
                    directory,
                    scenarios =
                        listOf(
                            BenchmarkStorageSuite.Scenario(
                                "sparse-tiny",
                                BenchmarkGenerator.Pattern.SPARSE,
                                40,
                            ),
                            BenchmarkStorageSuite.Scenario(
                                "mixed-tiny",
                                BenchmarkGenerator.Pattern.MIXED,
                                20,
                            ),
                        ),
                    wideWorldSide = 128,
                    giantWorldSide = 64,
                    giantWorldHotEpochs = 200,
                )
            val report = Files.readString(result.file)
            assertEquals(BenchmarkStorageSuite.Status.PASS, result.status, report)
            for (case in
                listOf("tile-shapes", "sparse-tiny", "mixed-tiny", "wide-world", "giant-world")) {
                assertTrue(
                    report.contains("case_status=PASS case=$case"),
                    "missing $case in\n$report",
                )
            }
            assertTrue(Files.list(directory.resolve("reports")).use { it.count() } == 1L)
        } finally {
            Files.walk(directory).use { files ->
                files.sorted(Comparator.reverseOrder()).forEach(Files::delete)
            }
        }
    }

    @Test
    fun stoppingWritesAReportAndRemovesTemporaryFixtures() {
        val directory = Files.createTempDirectory("palimpsest-suite-stop-")
        try {
            val result = BenchmarkStorageSuite.run(directory, shouldStop = { true })
            assertEquals(BenchmarkStorageSuite.Status.STOPPED, result.status)
            assertTrue(Files.readString(result.file).contains("status=STOPPED"))
            assertTrue(Files.list(directory.resolve("reports")).use { it.count() } == 1L)
        } finally {
            Files.walk(directory).use { files ->
                files.sorted(Comparator.reverseOrder()).forEach(Files::delete)
            }
        }
    }
}
