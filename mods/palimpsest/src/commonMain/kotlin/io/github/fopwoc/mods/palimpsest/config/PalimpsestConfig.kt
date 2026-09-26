package io.github.fopwoc.mods.palimpsest.config

import io.github.fopwoc.mods.framework.config.ModConfig
import io.github.fopwoc.mods.palimpsest.ModMetadata.MOD_ID
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

    val minimapEnabled by
        boolean(
            "minimapEnabled",
            default = true,
            comment =
                "Whether the minimap starts shown; its key toggles it until you leave the game.",
        )

    val minimapCorner by
        enum("minimapCorner", default = MinimapCorner.TOP_LEFT, comment = "Where the minimap sits.")

    val minimapSize by
        int(
            "minimapSize",
            default = 100,
            min = 48,
            max = 256,
            comment = "Side of the minimap, in GUI pixels.",
        )

    val minimapCoordinates by
        boolean(
            "minimapCoordinates",
            default = true,
            comment = "Whether your coordinates show under the minimap.",
        )

    val commitInterval: Duration
        get() = Duration.ofSeconds(commitIntervalSeconds.toLong())
}
