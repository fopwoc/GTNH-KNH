package io.github.fopwoc.mods.framework.ui.compose.unit

@JvmInline
value class UiUnit(val value: Int)

val Int.uu: UiUnit
    get() = UiUnit(this)

