package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import net.minecraft.client.Minecraft
import net.minecraft.util.MathHelper
import net.minecraft.util.MovingObjectPosition
import net.minecraft.util.Vec3

private const val AIR_TARGET_STEP = 0.1

enum class MeasurementHoverTargetKind {
    DIRECT,
    OFFSET
}

data class MeasurementHoverTarget(
    val block: BlockSelection,
    val kind: MeasurementHoverTargetKind
)

object MeasurementHoverResolver {
    fun resolve(
        minecraft: Minecraft,
        currentDimensionId: Int,
        usePlacementOffset: Boolean
    ): MeasurementHoverTarget? {
        val world = minecraft.theWorld ?: return null
        val player = minecraft.thePlayer ?: return null
        val hit = minecraft.objectMouseOver

        if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            if (usePlacementOffset) {
                resolvePlacementOffsetBlock(world, hit, currentDimensionId)?.let { offsetBlock ->
                    return MeasurementHoverTarget(offsetBlock, MeasurementHoverTargetKind.OFFSET)
                }
            }
            return MeasurementHoverTarget(
                block = BlockSelection(hit.blockX, hit.blockY, hit.blockZ, currentDimensionId),
                kind = MeasurementHoverTargetKind.DIRECT
            )
        }

        val reach = minecraft.playerController?.blockReachDistance?.toDouble() ?: 5.0
        if (reach <= 0.0) {
            return null
        }

        val eyePosition = Vec3.createVectorHelper(
            player.posX,
            player.posY + player.getEyeHeight().toDouble(),
            player.posZ
        )
        val look = player.getLookVec() ?: return null
        var bestAirTarget: BlockSelection? = null
        var distance = AIR_TARGET_STEP
        while (distance <= reach + AIR_TARGET_STEP * 0.5) {
            val sampleX = eyePosition.xCoord + look.xCoord * distance
            val sampleY = eyePosition.yCoord + look.yCoord * distance
            val sampleZ = eyePosition.zCoord + look.zCoord * distance

            val blockX = MathHelper.floor_double(sampleX)
            val blockY = MathHelper.floor_double(sampleY)
            val blockZ = MathHelper.floor_double(sampleZ)

            if (blockY >= 0 && blockY < world.actualHeight && world.blockExists(blockX, blockY, blockZ)) {
                if (!world.isAirBlock(blockX, blockY, blockZ)) {
                    return MeasurementHoverTarget(
                        block = BlockSelection(blockX, blockY, blockZ, currentDimensionId),
                        kind = MeasurementHoverTargetKind.DIRECT
                    )
                }

                val candidate = BlockSelection(blockX, blockY, blockZ, currentDimensionId)
                if (candidate != bestAirTarget) {
                    bestAirTarget = candidate
                }
            }

            distance += AIR_TARGET_STEP
        }

        return bestAirTarget?.let { MeasurementHoverTarget(it, MeasurementHoverTargetKind.DIRECT) }
    }

    private fun resolvePlacementOffsetBlock(
        world: net.minecraft.world.World,
        hit: MovingObjectPosition,
        currentDimensionId: Int
    ): BlockSelection? {
        val offset = when (hit.sideHit) {
            0 -> Triple(0, -1, 0)
            1 -> Triple(0, 1, 0)
            2 -> Triple(0, 0, -1)
            3 -> Triple(0, 0, 1)
            4 -> Triple(-1, 0, 0)
            5 -> Triple(1, 0, 0)
            else -> return null
        }
        val targetX = hit.blockX + offset.first
        val targetY = hit.blockY + offset.second
        val targetZ = hit.blockZ + offset.third
        if (targetY !in 0 until world.actualHeight) {
            return null
        }
        if (!world.blockExists(targetX, targetY, targetZ)) {
            return null
        }
        return BlockSelection(targetX, targetY, targetZ, currentDimensionId)
    }
}

