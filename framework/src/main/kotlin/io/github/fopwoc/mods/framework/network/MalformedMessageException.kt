package io.github.fopwoc.mods.framework.network

/** Thrown by [MessageReader] when a payload is truncated or exceeds a declared bound. */
class MalformedMessageException(message: String) : RuntimeException(message, null, false, false)
