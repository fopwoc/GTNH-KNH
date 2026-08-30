package io.github.fopwoc.mods.framework.ui.compose.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.saveable.SaveableStateHolder

@Stable
class NavEntryScope<K : NavKey>
internal constructor(
    val backStack: NavBackStack<K>,
    val entry: NavEntry<K>,
    val navigator: Navigator<K>,
) {
  val entryId: Long
    get() = entry.id

  val key: K
    get() = entry.key

  fun navigate(key: K): NavEntry<K> = navigator.navigate(key)

  fun push(key: K): NavEntry<K> = navigator.push(key)

  fun navigateBack(): Boolean = navigator.navigateBack()

  fun pop(): Boolean = navigator.pop()

  fun replaceTop(key: K): NavEntry<K> = navigator.replaceTop(key)

  fun popToRoot() {
    navigator.popToRoot()
  }
}

class NavEntryProvider<K : NavKey>
internal constructor(private val handlers: List<NavEntryHandler<K>>) {
  @Composable
  internal fun render(
      scope: NavEntryScope<K>,
      saveableStateHolder: SaveableStateHolder,
  ) {
    val handler =
        handlers.firstOrNull { it.matches(scope.key) }
            ?: error("No NavHost entry registered for ${scope.key::class.qualifiedName}")
    if (handler.retainSaveableState) {
      saveableStateHolder.SaveableStateProvider(scope.entryId) {
        handler.content(scope, scope.key)
      }
    } else {
      handler.content(scope, scope.key)
    }
  }
}

class NavEntryProviderBuilder<K : NavKey> {
  @PublishedApi internal val handlers = mutableListOf<NavEntryHandler<K>>()

  inline fun <reified T : K> entry(
      retainSaveableState: Boolean = false,
      noinline content: @Composable NavEntryScope<K>.(T) -> Unit,
  ) {
    handlers +=
        NavEntryHandler(
            matches = { it is T },
            retainSaveableState = retainSaveableState,
            content = { key ->
              @Suppress("UNCHECKED_CAST") content(key as T)
            },
        )
  }

  internal fun build(): NavEntryProvider<K> = NavEntryProvider(handlers.toList())
}

fun <K : NavKey> entryProvider(
    builder: NavEntryProviderBuilder<K>.() -> Unit
): NavEntryProvider<K> = NavEntryProviderBuilder<K>().apply(builder).build()

@PublishedApi
internal class NavEntryHandler<K : NavKey>(
    val matches: (K) -> Boolean,
    val retainSaveableState: Boolean,
    val content: @Composable NavEntryScope<K>.(K) -> Unit,
)
