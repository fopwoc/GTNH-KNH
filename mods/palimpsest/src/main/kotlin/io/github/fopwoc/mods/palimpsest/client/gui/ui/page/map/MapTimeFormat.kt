package io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val formatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

internal fun formatEpoch(epoch: Long): String = formatter.format(Instant.ofEpochMilli(epoch))
