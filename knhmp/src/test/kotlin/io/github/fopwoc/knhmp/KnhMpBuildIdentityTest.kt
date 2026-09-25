package io.github.fopwoc.knhmp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KnhMpBuildIdentityTest {
    @Test
    fun `remotes of every shape become the https page`() {
        val page = "https://github.com/fopwoc/GTNH-KNH"
        listOf(
                "git@github.com:fopwoc/GTNH-KNH.git",
                "git@github.com:fopwoc/GTNH-KNH",
                "ssh://git@github.com/fopwoc/GTNH-KNH.git",
                "ssh://git@github.com:22/fopwoc/GTNH-KNH.git",
                "https://github.com/fopwoc/GTNH-KNH.git",
                "https://github.com/fopwoc/GTNH-KNH/",
                "https://user@github.com/fopwoc/GTNH-KNH",
            )
            .forEach { assertEquals(page, KnhMpBuildIdentity.webUrl(it), it) }
    }

    @Test
    fun `local paths are not web pages`() {
        assertNull(KnhMpBuildIdentity.webUrl("/srv/git/repo.git"))
        assertNull(KnhMpBuildIdentity.webUrl("file:///srv/git/repo.git"))
    }
}
