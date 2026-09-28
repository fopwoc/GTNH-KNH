package io.github.fopwoc.mods.palimpsest.client.prospecting

import io.github.fopwoc.mods.framework.ui.compose.model.color.Color

internal fun ProspectingMark.Kind.color(): Color =
    when (this) {
        ProspectingMark.Kind.ORE -> Color(0xFFE5A95A)
        ProspectingMark.Kind.FLUID -> Color(0xFF55B8DC)
    }

internal fun ProspectingMark.Kind.symbol(): String =
    when (this) {
        ProspectingMark.Kind.ORE -> "O"
        ProspectingMark.Kind.FLUID -> "F"
    }

internal val ProspectingMark.description: String
    get() = buildString {
        append(name)
        detail?.let { append(" · ").append(it) }
        append(" · ").append(x).append(", ").append(z)
    }
