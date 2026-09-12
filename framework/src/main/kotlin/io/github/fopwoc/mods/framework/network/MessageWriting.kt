package io.github.fopwoc.mods.framework.network

import cpw.mods.fml.common.network.ByteBufUtils
import io.netty.buffer.ByteBuf

/** Counterparts of the [MessageReader] helpers that need more than a plain `ByteBuf` call. */
fun ByteBuf.writeUtf8(value: String, maxLength: Int): ByteBuf {
  ByteBufUtils.writeUTF8String(this, value.take(maxLength))
  return this
}

fun ByteBuf.writeEnum(value: Enum<*>): ByteBuf = writeByte(value.ordinal)
