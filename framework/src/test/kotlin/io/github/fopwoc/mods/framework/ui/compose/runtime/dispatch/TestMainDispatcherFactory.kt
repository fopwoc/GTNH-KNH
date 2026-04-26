@file:OptIn(kotlinx.coroutines.InternalCoroutinesApi::class)

package io.github.fopwoc.mods.framework.ui.compose.runtime

import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.internal.MainDispatcherFactory

class ComposeTestMainDispatcherFactory : MainDispatcherFactory {
    override val loadPriority: Int
        get() = Int.MAX_VALUE

    override fun createDispatcher(allFactories: List<MainDispatcherFactory>): MainCoroutineDispatcher {
        return ComposeMainDispatcher
    }

    override fun hintOnError(): String {
        return "Compose main dispatcher is registered only for framework tests"
    }
}
