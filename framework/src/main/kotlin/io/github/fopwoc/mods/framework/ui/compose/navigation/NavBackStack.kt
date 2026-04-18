package io.github.fopwoc.mods.framework.ui.compose.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember

interface NavKey

data class NavEntry<out K : NavKey>(
    val id: Long,
    val key: K
)

class NavBackStack<K : NavKey> internal constructor(initialKeys: Iterable<K>) {
    private val entriesState = mutableStateListOf<NavEntry<K>>()
    private var nextId: Long = 1L

    init {
        initialKeys.forEach(::push)
    }

    val entries: List<NavEntry<K>>
        get() = entriesState

    val size: Int
        get() = entriesState.size

    val isEmpty: Boolean
        get() = entriesState.isEmpty()

    val currentEntry: NavEntry<K>?
        get() = entriesState.lastOrNull()

    val currentKey: K?
        get() = currentEntry?.key

    fun push(key: K): NavEntry<K> {
        return NavEntry(
            id = nextId++,
            key = key
        ).also(entriesState::add)
    }

    fun pop(): Boolean = removeLastOrNull() != null

    fun removeLastOrNull(): NavEntry<K>? {
        if (entriesState.isEmpty()) {
            return null
        }

        return entriesState.removeAt(entriesState.lastIndex)
    }

    fun replaceTop(key: K): NavEntry<K> {
        removeLastOrNull()
        return push(key)
    }

    fun popToRoot() {
        while (entriesState.size > 1) {
            entriesState.removeAt(entriesState.lastIndex)
        }
    }

    fun clear() {
        entriesState.clear()
    }
}

fun <K : NavKey> navBackStackOf(vararg initialKeys: K): NavBackStack<K> = NavBackStack(initialKeys.asList())

@Composable
fun <K : NavKey> rememberNavBackStack(vararg initialKeys: K): NavBackStack<K> = remember {
    navBackStackOf(*initialKeys)
}

