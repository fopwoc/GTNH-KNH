package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

object MeasurementHoverResolver {
    fun resolve(
        minecraft: Minecraft = Minecraft.getInstance(),
        usePlacementOffset: Boolean,
    ): MeasurementHoverTarget? {
        val level = minecraft.level ?: return null
        val player = minecraft.player ?: return null
        val viewer = minecraft.cameraEntity ?: player
        val dimensionId = level.dimension().identifier().toString()
        val camera = minecraft.gameRenderer.mainCamera()
        val origin = camera.position()
        val look = camera.forwardVector()
        val direction = Vec3(look.x().toDouble(), look.y().toDouble(), look.z().toDouble())
        val reach = if (viewer !== player) ModernFreecamReach.reach.toDouble() else player.blockInteractionRange()
        if (reach <= 0.0) return null

        val pick = MeasurementRayPicker.pick(
            originX = origin.x,
            originY = origin.y,
            originZ = origin.z,
            directionX = direction.x,
            directionY = direction.y,
            directionZ = direction.z,
            maxDistance = reach,
            dimensionId = dimensionId,
            isLoaded = { x, y, z ->
                y in level.minY until level.maxY && level.hasChunk(x shr 4, z shr 4)
            },
            isSolid = { x, y, z -> !level.getBlockState(BlockPos(x, y, z)).isAir },
            isAnchor = MeasurementSelectionState::isInteractiveAnchor,
        ) ?: return null
        if (pick.isAnchor) return MeasurementHoverTarget(pick.block, MeasurementHoverTargetKind.ANCHOR)

        if (usePlacementOffset) {
            val hit = level.clip(
                ClipContext(origin, origin.add(direction.scale(reach)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, viewer)
            )
            if (hit.type == HitResult.Type.BLOCK) {
                val adjacent = hit.blockPos.relative(hit.direction)
                if (level.isInsideBuildHeight(adjacent) && level.hasChunk(adjacent.x shr 4, adjacent.z shr 4)) {
                    return MeasurementHoverTarget(
                        BlockSelection(adjacent.x, adjacent.y, adjacent.z, dimensionId),
                        MeasurementHoverTargetKind.OFFSET,
                    )
                }
            }
        }
        return MeasurementHoverTarget(pick.block, MeasurementHoverTargetKind.DIRECT)
    }
}
