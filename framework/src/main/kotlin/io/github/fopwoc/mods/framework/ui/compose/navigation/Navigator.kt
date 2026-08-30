package io.github.fopwoc.mods.framework.ui.compose.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember

@Stable
interface Navigator<K : NavKey> {
  val backStack: NavBackStack<K>

  val entries: List<NavEntry<K>>
    get() = backStack.entries

  val size: Int
    get() = backStack.size

  val isEmpty: Boolean
    get() = backStack.isEmpty

  val currentEntry: NavEntry<K>?
    get() = backStack.currentEntry

  val currentKey: K?
    get() = backStack.currentKey

  val canPop: Boolean
    get() = size > 1

  fun navigate(key: K): NavEntry<K>

  fun push(key: K): NavEntry<K> = navigate(key)

  fun navigateBack(): Boolean

  fun pop(): Boolean = navigateBack()

  fun replaceTop(key: K): NavEntry<K>

  fun popToRoot()

  fun clear()
}

private class NavBackStackNavigator<K : NavKey>(override val backStack: NavBackStack<K>) :
    Navigator<K> {
  override fun navigate(key: K): NavEntry<K> = backStack.push(key)

  override fun navigateBack(): Boolean = backStack.pop()

  override fun replaceTop(key: K): NavEntry<K> = backStack.replaceTop(key)

  override fun popToRoot() {
    backStack.popToRoot()
  }

  override fun clear() {
    backStack.clear()
  }
}

fun <K : NavKey> NavBackStack<K>.navigator(): Navigator<K> = NavBackStackNavigator(this)

@Composable
fun <K : NavKey> rememberNavigator(backStack: NavBackStack<K>): Navigator<K> =
    remember(backStack) {
      backStack.navigator()
    }
