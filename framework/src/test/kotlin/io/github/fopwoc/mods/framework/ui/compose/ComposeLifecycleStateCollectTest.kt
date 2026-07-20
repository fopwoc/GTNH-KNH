package io.github.fopwoc.mods.framework.ui.compose

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.runtime.ComposeViewModelOwner
import io.github.fopwoc.mods.framework.ui.compose.runtime.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class ComposeLifecycleStateCollectTest {
    @Test
    fun frameworkCollectorFollowsLifecycleStartStopAndResume() = runBlocking {
        val harness = ComposeUiTestHarness()
        val owner = ComposeViewModelOwner()
        val upstream = MutableStateFlow(0)
        var latestValue = -1

        try {
            owner.onCreate()
            harness.setContent {
                CompositionLocalProvider(
                    LocalLifecycleOwner provides owner,
                    LocalViewModelStoreOwner provides owner
                ) {
                    val value by upstream.collectAsStateWithLifecycle()
                    latestValue = value
                    Text(text = "value=$value")
                }
            }

            harness.settle(0L)
            assertEquals(0, latestValue)

            upstream.value = 1
            harness.settle(16L)
            assertEquals(0, latestValue)

            owner.onStart()
            harness.settle(32L)
            assertEquals(1, latestValue)

            owner.onStop()
            upstream.value = 2
            harness.settle(48L)
            assertEquals(1, latestValue)

            owner.onStart()
            harness.settle(64L)
            assertEquals(2, latestValue)
        } finally {
            harness.dispose()
            owner.clear()
        }
    }
}
