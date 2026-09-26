package io.github.fopwoc.mods.framework.minecraft

import net.minecraft.world.level.LevelHeightAccessor

// 26.x renamed the build height accessors and made the top one inclusive.

/** The lowest block y. */
val LevelHeightAccessor.bottomY: Int
    get() {
        /*? if >=26 {*/
        return minY
        /*?} else {*/
        /*return minBuildHeight
         */
        /*?}*/
    }

/** The highest block y, inclusive. */
val LevelHeightAccessor.topY: Int
    get() {
        /*? if >=26 {*/
        return maxY
        /*?} else {*/
        /*return maxBuildHeight - 1
         */
        /*?}*/
    }

/** The section y of the lowest section. */
val LevelHeightAccessor.bottomSectionY: Int
    get() {
        /*? if >=26 {*/
        return minSectionY
        /*?} else {*/
        /*return minSection
         */
        /*?}*/
    }
