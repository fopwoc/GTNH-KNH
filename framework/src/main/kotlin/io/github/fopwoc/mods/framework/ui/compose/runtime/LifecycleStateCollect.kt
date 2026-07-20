package io.github.fopwoc.mods.framework.ui.compose.runtime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@Composable
fun <T> StateFlow<T>.collectAsStateWithLifecycle(
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    context: CoroutineContext = EmptyCoroutineContext
): State<T> = collectAsStateWithLifecycle(
    initialValue = value,
    lifecycle = lifecycleOwner.lifecycle,
    minActiveState = minActiveState,
    context = context
)

@Composable
fun <T> StateFlow<T>.collectAsStateWithLifecycle(
    lifecycle: Lifecycle,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    context: CoroutineContext = EmptyCoroutineContext
): State<T> = collectAsStateWithLifecycle(
    initialValue = value,
    lifecycle = lifecycle,
    minActiveState = minActiveState,
    context = context
)

@Composable
fun <T> Flow<T>.collectAsStateWithLifecycle(
    initialValue: T,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    context: CoroutineContext = EmptyCoroutineContext
): State<T> = collectAsStateWithLifecycle(
    initialValue = initialValue,
    lifecycle = lifecycleOwner.lifecycle,
    minActiveState = minActiveState,
    context = context
)

@Composable
fun <T> Flow<T>.collectAsStateWithLifecycle(
    initialValue: T,
    lifecycle: Lifecycle,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    context: CoroutineContext = EmptyCoroutineContext
): State<T> {
    require(minActiveState != Lifecycle.State.INITIALIZED) {
        "Lifecycle.State.INITIALIZED is not allowed for collectAsStateWithLifecycle"
    }

    return produceState(initialValue, this, lifecycle, minActiveState, context) {
        var collectionJob: Job? = null

        fun startCollection() {
            if (collectionJob?.isActive == true || !lifecycle.currentState.isAtLeast(minActiveState)) {
                return
            }
            collectionJob = if (context == EmptyCoroutineContext) {
                launch {
                    this@collectAsStateWithLifecycle.collect { value = it }
                }
            } else {
                launch(context) {
                    this@collectAsStateWithLifecycle.collect { value = it }
                }
            }
        }

        fun stopCollection() {
            collectionJob?.cancel()
            collectionJob = null
        }

        val observer = LifecycleEventObserver { _, _ ->
            if (lifecycle.currentState.isAtLeast(minActiveState)) {
                startCollection()
            } else {
                stopCollection()
            }
        }

        lifecycle.addObserver(observer)
        startCollection()

        try {
            awaitCancellation()
        } finally {
            stopCollection()
            lifecycle.removeObserver(observer)
        }
    }
}
