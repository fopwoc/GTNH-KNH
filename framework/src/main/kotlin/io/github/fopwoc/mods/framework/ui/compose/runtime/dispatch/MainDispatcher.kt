package io.github.fopwoc.mods.framework.ui.compose.runtime

import kotlinx.coroutines.MainCoroutineDispatcher
import java.util.ArrayDeque
import kotlin.coroutines.CoroutineContext

internal object ComposeMainDispatcherBridge {
    private data class InstallationState(
        val mainThread: Thread?,
        val installDepth: Int
    )

    private val pendingTasks = ArrayDeque<Runnable>()
    private val lock = Any()

    @Volatile
    private var installationState: InstallationState = InstallationState(
        mainThread = null,
        installDepth = 0
    )

    fun installForCurrentThread() {
        val currentThread = Thread.currentThread()
        synchronized(lock) {
            val state = installationState
            val installedThread = state.mainThread
            when {
                installedThread == null -> {
                    installationState = InstallationState(
                        mainThread = currentThread,
                        installDepth = 1
                    )
                }

                installedThread === currentThread -> {
                    installationState = state.copy(installDepth = state.installDepth + 1)
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
            val state = installationState
            if (state.mainThread !== currentThread || state.installDepth == 0) {
                return
            }

            val nextDepth = state.installDepth - 1
            if (nextDepth == 0) {
                installationState = InstallationState(mainThread = null, installDepth = 0)
                pendingTasks.clear()
            } else {
                installationState = state.copy(installDepth = nextDepth)
            }
        }
    }

    fun isDispatchNeeded(): Boolean {
        val boundThread = installationState.mainThread ?: return false
        return Thread.currentThread() !== boundThread
    }

    fun boundThreadName(): String {
        return installationState.mainThread?.name ?: "unbound"
    }

    fun dispatch(block: Runnable) {
        val shouldRunInline = synchronized(lock) {
            val boundThread = installationState.mainThread
            when {
                boundThread == null -> true
                boundThread === Thread.currentThread() -> true
                else -> {
                    pendingTasks.addLast(block)
                    false
                }
            }
        }
        if (shouldRunInline) {
            block.run()
        }
    }

    fun pump(): Boolean = pump(Int.MAX_VALUE) {
        "ComposeMainDispatcherBridge.pump() exceeded ${Int.MAX_VALUE} task executions without reaching an idle state"
    }

    fun pump(maxTaskExecutions: Int, limitExceededMessage: () -> String): Boolean {
        require(maxTaskExecutions > 0) { "maxTaskExecutions must be greater than zero" }
        if (isDispatchNeeded()) {
            return false
        }

        var drainedAny = false
        var drainedTaskCount = 0
        while (true) {
            val next = synchronized(lock) {
                if (pendingTasks.isEmpty()) {
                    null
                } else {
                    pendingTasks.removeFirst()
                }
            } ?: return drainedAny
            drainedAny = true
            drainedTaskCount += 1
            check(drainedTaskCount <= maxTaskExecutions, limitExceededMessage)
            next.run()
        }
    }

    internal fun resetForTests() {
        synchronized(lock) {
            pendingTasks.clear()
            installationState = InstallationState(mainThread = null, installDepth = 0)
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

