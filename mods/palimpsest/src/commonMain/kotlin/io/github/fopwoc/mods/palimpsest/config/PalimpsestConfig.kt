package io.github.fopwoc.mods.palimpsest.config

import io.github.fopwoc.mods.palimpsest.ModMetadata.MOD_ID
import io.github.fopwoc.mods.framework.config.ModConfig
import io.github.fopwoc.mods.palimpsest.map.MapStorageEstimate
import java.time.Duration

/** Client-side map settings; editable in game under Mods → Palimpsest → Config. */
object PalimpsestConfig : ModConfig(modId = MOD_ID, name = "palimpsest") {
    val commitIntervalSeconds by
        int(
            "commitIntervalSeconds",
            default = 60,
            min = 1,
            max = 3600,
            comment =
                "Seconds between commits of what you have seen into the map's history. Shorter " +
                    "keeps a finer time-lapse and costs more disk.",
            hint = MapStorageEstimate::describeDay,
        )

    val commitInterval: Duration
        get() = Duration.ofSeconds(commitIntervalSeconds.toLong())
}
