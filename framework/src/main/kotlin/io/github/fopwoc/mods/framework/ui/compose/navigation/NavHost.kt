package io.github.fopwoc.mods.framework.ui.compose.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import io.github.fopwoc.mods.framework.ui.compose.runtime.BackHandlerResult
import io.github.fopwoc.mods.framework.ui.compose.runtime.ComposeViewModelOwner

/** Displays Navigation 3 entries in KNH's Compose Runtime tree. */
@Composable
fun <K : NavKey> NavHost(
    backStack: NavBackStack<K>,
    entryProvider: (K) -> NavEntry<K>,
    handleBack: Boolean = true,
    emptyContent: @Composable () -> Unit = {},
) {
    val ownerRegistry = remember { NavEntryViewModelOwnerRegistry() }
    val saveableStateDecorator = rememberSaveableStateHolderNavEntryDecorator<K>()
    val entries =
        rememberDecoratedNavEntries(backStack, listOf(saveableStateDecorator), entryProvider)
    val currentEntry = entries.lastOrNull()

    DisposableEffect(ownerRegistry) {
        onDispose(ownerRegistry::clearAll)
    }

    SideEffect {
        ownerRegistry.update(entries, currentEntry?.contentKey)
    }

    if (currentEntry == null) {
        emptyContent()
        return
    }

    BackHandlerResult(enabled = handleBack && backStack.size > 1) {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
            true
        } else {
            false
        }
    }

    val owner = ownerRegistry.getOrCreate(currentEntry.contentKey)
    key(currentEntry.contentKey) {
        CompositionLocalProvider(
            LocalLifecycleOwner provides owner,
            LocalViewModelStoreOwner provides owner,
        ) {
            currentEntry.Content()
        }
    }
}

private class NavEntryViewModelOwnerRegistry {
    private val owners = LinkedHashMap<Any, NavEntryHostOwner>()

    fun getOrCreate(contentKey: Any): ComposeViewModelOwner =
        owners.getOrPut(contentKey) {
            NavEntryHostOwner().also { it.attach() }
        }

    fun update(entries: List<NavEntry<*>>, currentContentKey: Any?) {
        val activeKeys = entries.mapTo(linkedSetOf()) { it.contentKey }
        val iterator = owners.entries.iterator()
        while (iterator.hasNext()) {
            val next = iterator.next()
            if (next.key !in activeKeys) {
                next.value.clear()
                iterator.remove()
            }
        }

        activeKeys.forEach { contentKey ->
            val owner = getOrCreate(contentKey) as NavEntryHostOwner
            if (contentKey == currentContentKey) owner.resumeEntry() else owner.retainCoveredEntry()
        }
    }

    fun clearAll() {
        owners.values.forEach(ComposeViewModelOwner::clear)
        owners.clear()
    }
}

private class NavEntryHostOwner : ComposeViewModelOwner() {
    fun attach() = onCreate()

    fun retainCoveredEntry() = onStart()

    fun resumeEntry() {
        onStart()
        onResume()
    }
}
