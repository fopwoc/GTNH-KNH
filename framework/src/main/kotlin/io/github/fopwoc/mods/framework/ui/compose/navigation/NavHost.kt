package io.github.fopwoc.mods.framework.ui.compose.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import io.github.fopwoc.mods.framework.ui.compose.runtime.BackHandler

@Composable
fun <K : NavKey> NavHost(
    backStack: NavBackStack<K>,
    entryProvider: NavEntryProvider<K>,
    handleBack: Boolean = true,
    emptyContent: @Composable () -> Unit = {}
) {
    val ownerRegistry = remember { NavEntryViewModelOwnerRegistry() }
    val navigator = rememberNavigator(backStack)
    val saveableStateHolder = rememberSaveableStateHolder()
    val entries = backStack.entries

    SideEffect {
        val removedEntryIds = ownerRegistry.retainOnly(entries.mapTo(linkedSetOf()) { it.id })
        removedEntryIds.forEach(saveableStateHolder::removeState)
    }

    DisposableEffect(ownerRegistry) {
        onDispose {
            ownerRegistry.clearAll()
        }
    }

    val currentEntry = backStack.currentEntry
    if (currentEntry == null) {
        emptyContent()
        return
    }

    BackHandler(enabled = handleBack && navigator.canPop) {
        navigator.navigateBack()
    }

    val scope = remember(backStack, navigator, currentEntry.id) {
        NavEntryScope(backStack = backStack, entry = currentEntry, navigator = navigator)
    }
    val owner = ownerRegistry.ownerFor(currentEntry.id)

    key(currentEntry.id) {
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
            entryProvider.Render(
                scope = scope,
                saveableStateHolder = saveableStateHolder
            )
        }
    }
}

private class NavEntryViewModelOwnerRegistry {
    private val owners = LinkedHashMap<Long, NavEntryViewModelOwner>()

    fun ownerFor(entryId: Long): NavEntryViewModelOwner = owners.getOrPut(entryId) {
        NavEntryViewModelOwner()
    }

    fun retainOnly(activeEntryIds: Set<Long>): List<Long> {
        val removedEntryIds = mutableListOf<Long>()
        val iterator = owners.entries.iterator()
        while (iterator.hasNext()) {
            val next = iterator.next()
            if (next.key !in activeEntryIds) {
                removedEntryIds += next.key
                next.value.clear()
                iterator.remove()
            }
        }
        return removedEntryIds
    }

    fun clearAll() {
        owners.values.forEach(NavEntryViewModelOwner::clear)
        owners.clear()
    }
}

private class NavEntryViewModelOwner : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
    private val store = ViewModelStore()
    private val factory = ViewModelProvider.NewInstanceFactory()

    override val viewModelStore: ViewModelStore
        get() = store

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = factory

    override val defaultViewModelCreationExtras: CreationExtras
        get() = CreationExtras.Empty

    fun clear() {
        store.clear()
    }
}

