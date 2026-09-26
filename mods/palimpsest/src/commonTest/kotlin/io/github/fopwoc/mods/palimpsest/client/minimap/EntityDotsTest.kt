package io.github.fopwoc.mods.palimpsest.client.minimap

import io.github.fopwoc.mods.framework.client.EntityKind
import io.github.fopwoc.mods.framework.client.EntitySighting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EntityDotsTest {
    private fun cow(x: Double) = EntitySighting(7, EntityKind.PASSIVE, x, 64.0, 0.0)

    @Test
    fun dotsGlideAfterTheirEntitiesAndForgetThemOnceUnseen() {
        val dots = EntityDots()
        assertEquals(0.0, dots.glide(listOf(cow(0.0)), 0.016).single().x)

        val glided = dots.glide(listOf(cow(1.0)), 0.016).single().x
        assertTrue(glided > 0.0 && glided < 1.0)

        // A teleport lands at once instead of sliding across the map.
        assertEquals(100.0, dots.glide(listOf(cow(100.0)), 0.016).single().x)

        // Gone for a frame, the entity comes back where it is, not where the dot was.
        assertTrue(dots.glide(emptyList(), 0.016).isEmpty())
        assertEquals(101.0, dots.glide(listOf(cow(101.0)), 0.016).single().x)
    }
}
