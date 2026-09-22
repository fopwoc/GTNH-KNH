package io.github.fopwoc.mods.framework.event

import io.github.fopwoc.mods.framework.player.GamePlayer

/** Logical-server events: a dedicated server or the integrated server of a singleplayer world. */
object ServerEvents {
    /** Before the server tick, on the server thread. */
    val tickStart = Event<Unit>()

    /** After the server tick, on the server thread. */
    val tickEnd = Event<Unit>()

    /** The server finished starting and is about to tick. */
    val started = Event<Unit>()

    /** The server is shutting down; players are still connected. */
    val stopping = Event<Unit>()

    val playerJoined = Event<GamePlayer>()

    val playerLeft = Event<GamePlayer>()
}
