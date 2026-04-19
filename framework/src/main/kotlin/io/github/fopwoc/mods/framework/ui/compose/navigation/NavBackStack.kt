package io.github.fopwoc.mods.framework.ui.compose.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.autoSaver
import androidx.compose.runtime.saveable.rememberSaveable

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

    internal val nextEntryId: Long
        get() = nextId

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

    internal constructor(
        initialEntries: Iterable<NavEntry<K>>,
        nextId: Long
    ) : this(emptyList()) {
        entriesState += initialEntries
        this.nextId = nextId.coerceAtLeast((entriesState.maxOfOrNull(NavEntry<K>::id) ?: 0L) + 1L)
    }
}

fun <K : NavKey> navBackStackOf(vararg initialKeys: K): NavBackStack<K> = NavBackStack(initialKeys.asList())

@Composable
fun <K : NavKey> rememberNavBackStack(vararg initialKeys: K): NavBackStack<K> =
    rememberNavBackStack(*initialKeys, keySaver = autoSaver())

@Composable
fun <K : NavKey, S : Any> rememberNavBackStack(
    vararg initialKeys: K,
    keySaver: Saver<K, S>
): NavBackStack<K> = rememberSaveable(saver = navBackStackSaver(keySaver)) {
    navBackStackOf(*initialKeys)
}


internal fun <K : NavKey, S : Any> navBackStackSaver(keySaver: Saver<K, S>): Saver<NavBackStack<K>, Any> = Saver(
    save = {
        val savedEntries = buildList<List<Any>>() {
            it.entries.forEach { entry ->
                val savedKey = with(keySaver) { this@Saver.save(entry.key) } ?: return@Saver null
                add(listOf(entry.id, savedKey))
            }
        }
        listOf(it.nextEntryId, savedEntries)
    },
    restore = { restored ->
        val savedState = restored as? List<*> ?: return@Saver null
        val nextId = savedState.getOrNull(0) as? Long ?: return@Saver null
        val savedEntries = savedState.getOrNull(1) as? List<*> ?: return@Saver null
        val restoredEntries = buildList<NavEntry<K>> {
            savedEntries.forEach { savedEntry ->
                val values = savedEntry as? List<*> ?: return@Saver null
                val entryId = values.getOrNull(0) as? Long ?: return@Saver null
                @Suppress("UNCHECKED_CAST")
                val restoredKey = keySaver.restore(values.getOrNull(1) as? S ?: return@Saver null) ?: return@Saver null
                add(NavEntry(id = entryId, key = restoredKey))
            }
        }
        NavBackStack(initialEntries = restoredEntries, nextId = nextId)
    }
)

