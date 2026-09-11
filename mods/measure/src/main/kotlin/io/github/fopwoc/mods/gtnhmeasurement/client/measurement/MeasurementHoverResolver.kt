package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import io.github.fopwoc.mods.gtnhmeasurement.client.compat.FreecamCompat
import net.minecraft.client.Minecraft
import net.minecraft.util.MovingObjectPosition
import net.minecraft.util.Vec3

enum class MeasurementHoverTargetKind {
  /** The block under the crosshair, or the farthest air block in reach. */
  DIRECT,
  /** The block next to the hit face (Ctrl held). */
  OFFSET,
  /** An anchor of an existing measurement, draft or preview, wherever it sits along the ray. */
  ANCHOR,
}

data class MeasurementHoverTarget(
    val block: BlockSelection,
    val kind: MeasurementHoverTargetKind,
)

object MeasurementHoverResolver {
  fun resolve(
      minecraft: Minecraft,
      currentDimensionId: Int,
      usePlacementOffset: Boolean,
      isAnchor: (BlockSelection) -> Boolean = MeasurementSelectionState::isInteractiveAnchor,
  ): MeasurementHoverTarget? {
    val world = minecraft.theWorld ?: return null
    // The view entity, not the player: freecam-style mods swap it for a detached camera and the
    // crosshair should keep picking from where the user is actually looking.
    val viewer = minecraft.renderViewEntity ?: minecraft.thePlayer ?: return null
    val reach =
        if (FreecamCompat.isActive()) FreecamCompat.reach.toDouble()
        else minecraft.playerController?.blockReachDistance?.toDouble() ?: 5.0
    if (reach <= 0.0) {
      return null
    }

    // Same origin as EntityRenderer.getMouseOver: a client player's posY is already eye level.
    val eyePosition = viewer.getPosition(1.0f) ?: return null
    val look = viewer.getLookVec() ?: return null
    val pick =
        MeasurementRayPicker.pick(
            originX = eyePosition.xCoord,
            originY = eyePosition.yCoord,
            originZ = eyePosition.zCoord,
            directionX = look.xCoord,
            directionY = look.yCoord,
            directionZ = look.zCoord,
            maxDistance = reach,
            dimensionId = currentDimensionId,
            isLoaded = { x, y, z -> y in 0 until world.actualHeight && world.blockExists(x, y, z) },
            isSolid = { x, y, z -> !world.isAirBlock(x, y, z) },
            isAnchor = isAnchor,
        ) ?: return null

    // Existing anchors are picked as they are, so moving/selecting works in mid-air too.
    if (pick.isAnchor) {
      return MeasurementHoverTarget(pick.block, MeasurementHoverTargetKind.ANCHOR)
    }

    if (usePlacementOffset) {
      // Our own trace rather than objectMouseOver: vanilla computes that for the player only, so
      // it is stale or missing when a detached camera (freecam) is the viewer.
      val end =
          Vec3.createVectorHelper(
              eyePosition.xCoord + look.xCoord * reach,
              eyePosition.yCoord + look.yCoord * reach,
              eyePosition.zCoord + look.zCoord * reach,
          )
      val hit =
          world.rayTraceBlocks(
              Vec3.createVectorHelper(eyePosition.xCoord, eyePosition.yCoord, eyePosition.zCoord),
              end,
          )
      if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
        resolvePlacementOffsetBlock(world, hit, currentDimensionId)?.let { offsetBlock ->
          return MeasurementHoverTarget(offsetBlock, MeasurementHoverTargetKind.OFFSET)
        }
      }
    }
    return MeasurementHoverTarget(pick.block, MeasurementHoverTargetKind.DIRECT)
  }

  private fun resolvePlacementOffsetBlock(
      world: net.minecraft.world.World,
      hit: MovingObjectPosition,
      currentDimensionId: Int,
  ): BlockSelection? {
    val offset =
        when (hit.sideHit) {
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
