package io.github.fopwoc.mods.framework.ui.compose.runtime

import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.internal.MainDispatcherFactory
import java.util.ArrayDeque
import kotlin.coroutines.CoroutineContext

internal object ComposeMainDispatcherBridge {
    private val pendingTasks = ArrayDeque<Runnable>()
    private val lock = Any()
    private var installDepth: Int = 0

    @Volatile
    private var mainThread: Thread? = null

    fun installForCurrentThread() {
        val currentThread = Thread.currentThread()
        synchronized(lock) {
            val installedThread = mainThread
            when {
                installedThread == null -> {
                    mainThread = currentThread
                    installDepth = 1
                }

                installedThread === currentThread -> {
                    installDepth += 1
                }

                else -> {
                    error(
                        "ComposeMainDispatcher is already installed for thread ${installedThread.name} and cannot also bind to ${currentThread.name}"
                    )
                }
            }
        }
    }

    fun releaseForCurrentThread() {
        val currentThread = Thread.currentThread()
        synchronized(lock) {
            if (mainThread !== currentThread || installDepth == 0) {
                return
            }

            installDepth -= 1
            if (installDepth == 0) {
                mainThread = null
                pendingTasks.clear()
            }
        }
    }

    fun isDispatchNeeded(): Boolean {
        val boundThread = mainThread ?: return false
        return Thread.currentThread() !== boundThread
    }

    fun dispatch(block: Runnable) {
        if (!isDispatchNeeded()) {
            block.run()
            return
        }

        synchronized(lock) {
            pendingTasks.addLast(block)
        }
    }

    fun pump(): Boolean {
        if (isDispatchNeeded()) {
            return false
        }

        var drainedAny = false
        while (true) {
            val next = synchronized(lock) {
                if (pendingTasks.isEmpty()) {
                    null
                } else {
                    pendingTasks.removeFirst()
                }
            } ?: return drainedAny
            drainedAny = true
            next.run()
        }
    }

    internal fun resetForTests() {
        synchronized(lock) {
            pendingTasks.clear()
            installDepth = 0
            mainThread = null
        }
    }
}

internal object ComposeMainDispatcher : MainCoroutineDispatcher() {
    override val immediate: MainCoroutineDispatcher
        get() = this

    override fun isDispatchNeeded(context: CoroutineContext): Boolean {
        return ComposeMainDispatcherBridge.isDispatchNeeded()
    }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        ComposeMainDispatcherBridge.dispatch(block)
    }
}

@OptIn(InternalCoroutinesApi::class)
class ComposeMainDispatcherFactory : MainDispatcherFactory {
    override val loadPriority: Int
        get() = Int.MAX_VALUE

    override fun createDispatcher(allFactories: List<MainDispatcherFactory>): MainCoroutineDispatcher {
        return ComposeMainDispatcher
    }

    override fun hintOnError(): String {
        return "Compose main dispatcher is provided by io.github.fopwoc.mods.framework.ui.compose.runtime.ComposeMainDispatcherFactory"
    }
}
