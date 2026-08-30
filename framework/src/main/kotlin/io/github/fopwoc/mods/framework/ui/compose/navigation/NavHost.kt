package io.github.fopwoc.mods.framework.ui.compose.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import io.github.fopwoc.mods.framework.ui.compose.runtime.BackHandlerResult
import io.github.fopwoc.mods.framework.ui.compose.runtime.ComposeViewModelOwner

@Composable
fun <K : NavKey> NavHost(
    backStack: NavBackStack<K>,
    entryProvider: NavEntryProvider<K>,
    handleBack: Boolean = true,
    emptyContent: @Composable () -> Unit = {},
) {
  val ownerRegistry = remember { NavEntryViewModelOwnerRegistry() }
  val navigator = rememberNavigator(backStack)
  val saveableStateHolder = rememberSaveableStateHolder()
  val entries = backStack.entries
  val currentEntry = backStack.currentEntry

  DisposableEffect(ownerRegistry) {
    onDispose(ownerRegistry::clearAll)
  }

  SideEffect {
    ownerRegistry
        .update(entries = entries, currentEntryId = currentEntry?.id)
        .forEach(saveableStateHolder::removeState)
  }

  if (currentEntry == null) {
    emptyContent()
    return
  }

  BackHandlerResult(enabled = handleBack && navigator.canPop) {
    navigator.navigateBack()
  }

  val scope =
      remember(backStack, navigator, currentEntry.id) {
        NavEntryScope(backStack = backStack, entry = currentEntry, navigator = navigator)
      }
  val owner = ownerRegistry.getOrCreate(currentEntry.id)

  key(currentEntry.id) {
    NavEntryOwnersProvider(owner = owner) {
      entryProvider.render(
          scope = scope,
          saveableStateHolder = saveableStateHolder,
      )
    }
  }
}

@Composable
private fun NavEntryOwnersProvider(
    owner: ComposeViewModelOwner,
    content: @Composable () -> Unit,
) {
  CompositionLocalProvider(
      LocalLifecycleOwner provides owner,
      LocalViewModelStoreOwner provides owner,
      content = content,
  )
}

private class NavEntryViewModelOwnerRegistry {
  private val owners = LinkedHashMap<Long, NavEntryHostOwner>()

  fun getOrCreate(entryId: Long): ComposeViewModelOwner =
      owners.getOrPut(entryId) {
        NavEntryHostOwner().also {
          it.attach()
        }
      }

  fun update(entries: List<NavEntry<*>>, currentEntryId: Long?): List<Long> {
    val activeEntryIds = entries.mapTo(linkedSetOf()) { it.id }
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

    entries.forEach { entry ->
      val owner = getOrCreate(entry.id) as NavEntryHostOwner
      if (entry.id == currentEntryId) {
        owner.resumeEntry()
      } else {
        owner.retainCoveredEntry()
      }
    }

    return removedEntryIds
  }

  fun clearAll() {
    owners.values.forEach(ComposeViewModelOwner::clear)
    owners.clear()
  }
}

private class NavEntryHostOwner : ComposeViewModelOwner() {
  fun attach() {
    onCreate()
  }

  fun retainCoveredEntry() {
    onStart()
  }

  fun resumeEntry() {
    onStart()
    onResume()
  }
}
