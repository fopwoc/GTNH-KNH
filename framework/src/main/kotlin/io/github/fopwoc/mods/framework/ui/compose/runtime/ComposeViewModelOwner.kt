package io.github.fopwoc.mods.framework.ui.compose.runtime

import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras

internal open class ComposeViewModelOwner :
    LifecycleOwner,
    ViewModelStoreOwner,
    HasDefaultViewModelProviderFactory {

    private val store = ViewModelStore()
    private val lifecycleRegistry = LifecycleRegistry.createUnsafe(this)
    private val factory = ViewModelProvider.NewInstanceFactory()

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = factory

    override val defaultViewModelCreationExtras: CreationExtras
        get() = CreationExtras.Empty

    fun moveTo(state: Lifecycle.State) {
        if (lifecycleRegistry.currentState != state) {
            lifecycleRegistry.currentState = state
        }
    }

    open fun clear() {
        if (lifecycleRegistry.currentState == Lifecycle.State.INITIALIZED) {
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
        }
        if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        }
        store.clear()
    }
}


