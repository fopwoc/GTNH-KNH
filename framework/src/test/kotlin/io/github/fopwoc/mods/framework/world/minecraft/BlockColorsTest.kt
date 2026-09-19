package io.github.fopwoc.mods.framework.world.minecraft

import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.minecraft.block.Block
import net.minecraft.block.material.Material
import net.minecraft.world.IBlockAccess

class BlockColorsTest {
    private class PositionBoundBlock : Block(Material.rock) {
        override fun renderAsNormalBlock(): Boolean = true

        override fun setBlockBoundsBasedOnState(world: IBlockAccess, x: Int, y: Int, z: Int) {
            if (x == 1) setBlockBounds(0f, 0f, 0f, 0.5f, 1f, 1f)
            else setBlockBounds(0f, 0f, 0f, 1f, 1f, 1f)
        }
    }

    private val world =
        Proxy.newProxyInstance(
            IBlockAccess::class.java.classLoader,
            arrayOf(IBlockAccess::class.java),
        ) { _, method, _ ->
            when (method.returnType) {
                java.lang.Boolean.TYPE -> false
                java.lang.Integer.TYPE -> 0
                else -> null
            }
        } as IBlockAccess

    @Test
    fun fullCubeUsesPositionBoundsAndRestoresSharedBlockState() {
        val block = PositionBoundBlock()
        block.setBlockBounds(0.25f, 0f, 0f, 0.75f, 1f, 1f)

        assertTrue(BlockColors.isFullCube(world, 0, 20, 0, block))
        assertEquals(0.25, block.blockBoundsMinX)
        assertEquals(0.75, block.blockBoundsMaxX)

        assertFalse(BlockColors.isFullCube(world, 1, 20, 0, block))
        assertEquals(0.25, block.blockBoundsMinX)
        assertEquals(0.75, block.blockBoundsMaxX)
    }
}
