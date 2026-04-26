package io.github.fopwoc.mods.framework.ui.compose

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.runtime.ComposeViewModelOwner
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ViewModelIntegrationTest {
    @Test
    fun composeScreenViewModelOwnerClearsAndroidxViewModels() {
        val owner = ComposeViewModelOwner()
        val viewModel = ViewModelProvider.create(
            owner,
            owner.defaultViewModelProviderFactory,
            owner.defaultViewModelCreationExtras
        ).get(TrackingViewModel::class)

        owner.clear()

        assertTrue(viewModel.cleared)
    }

    @Test
    fun androidxComposeViewModelReturnsSameInstanceAcrossRecomposition() = runBlocking {
        val harness = ComposeUiTestHarness()
        val owner = ComposeViewModelOwner()
        val trigger = mutableIntStateOf(0)
        var first: TrackingViewModel? = null
        var second: TrackingViewModel? = null

        try {
            owner.onCreate()
            harness.setContent {
                CompositionLocalProvider(
                    LocalLifecycleOwner provides owner,
                    LocalViewModelStoreOwner provides owner
                ) {
                    val vm: TrackingViewModel = viewModel(TrackingViewModel::class)
                    if (trigger.intValue == 0) {
                        first = vm
                    } else {
                        second = vm
                    }
                    Text(text = "tick ${trigger.intValue} / ${vm.token}")
                }
            }
            harness.settle(0L)

            trigger.intValue = 1
            harness.settle(16L)

            assertSame(first, second)
        } finally {
            harness.dispose()
            owner.clear()
        }
    }

    @Test
    fun lifecycleRuntimeComposeUsesScreenLifecycleOwnerForCollection() = runBlocking {
        val harness = ComposeUiTestHarness()
        val owner = ComposeViewModelOwner()
        val upstream = MutableStateFlow(0)
        var currentLifecycleOwner: LifecycleOwner? = null
        var latestValue = -1

        try {
            owner.onCreate()
            harness.setContent {
                CompositionLocalProvider(
                    LocalLifecycleOwner provides owner,
                    LocalViewModelStoreOwner provides owner
                ) {
                    val lifecycleOwner = LocalLifecycleOwner.current
                    currentLifecycleOwner = lifecycleOwner
                    val value by upstream.collectAsStateWithLifecycle(
                        lifecycleOwner = lifecycleOwner,
                        minActiveState = androidx.lifecycle.Lifecycle.State.STARTED
                    )
                    latestValue = value
                    Text(text = "value=$value")
                }
            }
            harness.settle(0L)

            assertSame(owner, currentLifecycleOwner)
            assertEquals(0, latestValue)

            upstream.value = 1
            harness.settle(16L)
            assertEquals(0, latestValue)

            owner.onStart()
            harness.settle(32L)
            assertEquals(1, latestValue)
        } finally {
            harness.dispose()
            owner.clear()
        }
    }

    @Test
    fun freshOwnerCreatesFreshAndroidxViewModelInstance() {
        val firstOwner = ComposeViewModelOwner()
        firstOwner.onCreate()
        val firstViewModel = ViewModelProvider.create(
            firstOwner,
            firstOwner.defaultViewModelProviderFactory,
            firstOwner.defaultViewModelCreationExtras
        ).get("plain", TrackingViewModel::class)
        firstOwner.clear()

        val secondOwner = ComposeViewModelOwner()
        secondOwner.onCreate()
        val secondViewModel = ViewModelProvider.create(
            secondOwner,
            secondOwner.defaultViewModelProviderFactory,
            secondOwner.defaultViewModelCreationExtras
        ).get("plain", TrackingViewModel::class)

        try {
            assertTrue(firstViewModel.cleared)
            assertTrue(firstViewModel !== secondViewModel)
        } finally {
            secondOwner.clear()
        }
    }


    class TrackingViewModel : ViewModel() {
        val token: Int = nextToken++
        var cleared: Boolean = false
            private set

        override fun onCleared() {
            cleared = true
        }

        private companion object {
            var nextToken: Int = 1
        }
    }

}

