package io.github.fopwoc.mods.palimpsest.config

import io.github.fopwoc.mods.framework.config.ForgeConfig
import io.github.fopwoc.mods.palimpsest.MOD_ID
import io.github.fopwoc.mods.palimpsest.map.MapStorageEstimate
import io.github.fopwoc.mods.palimpsest.map.ObservationBroker
import java.time.Duration

/** Client-side map settings; editable in game under Mods → Palimpsest → Config. */
object PalimpsestConfig : ForgeConfig(modId = MOD_ID, fileName = "palimpsest.cfg") {
    val commitIntervalSeconds by
        int(
            "commitIntervalSeconds",
            default = 60,
            min = ObservationBroker.MINIMUM_STABILITY_SECONDS,
            max = 3600,
            comment =
                "Seconds between commits of what you have seen into the map's history. Shorter " +
                    "keeps a finer time-lapse and costs more disk. A change must also be seen " +
                    "unchanged for at least five seconds before it can become history.",
            hint = MapStorageEstimate::describeDay,
        )

    val commitInterval: Duration
        get() = Duration.ofSeconds(commitIntervalSeconds.toLong())
}
