package io.github.fopwoc.mods.framework.event

/** Physical-client events; they never fire on a dedicated server. */
object ClientEvents {
    /** Before the client tick, on the client thread. */
    val tickStart = Event<Unit>()

    /** After the client tick, on the client thread. */
    val tickEnd = Event<Unit>()

    /** A client world appeared: the player joined a server or opened a singleplayer world. */
    val connected = Event<Unit>()

    /** The client world went away; dimension changes within a session do not count. */
    val disconnected = Event<Unit>()

    private var inWorld = false

    /**
     * Platforms call this after every client tick. Session changes are derived from the client
     * world's presence because loader connection events fire on network threads.
     */
    internal fun afterTick(hasWorld: Boolean) {
        if (hasWorld != inWorld) {
            inWorld = hasWorld
            (if (hasWorld) connected else disconnected).emit(Unit)
        }
        tickEnd.emit(Unit)
    }
}
