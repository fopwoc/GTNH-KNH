package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementMode

enum class OverlayVisualState {
  NORMAL,
  /** Crosshair rests on one of its anchors: brighter than normal, thinner than selected. */
  HOVERED,
  SELECTED,
  PASTE,
  MOVE,
  RESIZE,
}

data class MeasurementRenderStyle(
    val firstAnchorColor: Color,
    val secondAnchorColor: Color,
    val lineColor: Color,
    val areaColor: Color,
    val anchorWidth: Float,
    val shapeWidth: Float,
) {
  fun shapeColor(mode: MeasurementMode): Color =
      when (mode) {
        MeasurementMode.LINE -> lineColor
        MeasurementMode.AREA -> areaColor
        MeasurementMode.SPHERE -> areaColor
        MeasurementMode.DISABLED -> lineColor
      }
}

object MeasurementOverlayPalette {
  private data class ModePalette(
      val normal: MeasurementRenderStyle,
      val selected: MeasurementRenderStyle,
      val paste: MeasurementRenderStyle,
      val move: MeasurementRenderStyle,
      val resize: MeasurementRenderStyle,
      val hoverDirect: Color,
      val hoverOffset: Color,
      val draftSecond: Color,
  )

  private val linePalette =
      ModePalette(
          normal =
              MeasurementRenderStyle(
                  firstAnchorColor = Color(0xFF4AA3FF),
                  secondAnchorColor = Color(0xFF65F2CF),
                  lineColor = Color(0xFF5CFFE0),
                  areaColor = Color(0xFF5CFFE0),
                  anchorWidth = 1.8f,
                  shapeWidth = 2.0f,
              ),
          selected =
              MeasurementRenderStyle(
                  firstAnchorColor = Color(0xFF8DDCFF),
                  secondAnchorColor = Color(0xFFB8FFF0),
                  lineColor = Color(0xFFB1FFF6),
                  areaColor = Color(0xFFB1FFF6),
                  anchorWidth = 3.0f,
                  shapeWidth = 3.3f,
              ),
          paste =
              MeasurementRenderStyle(
                  firstAnchorColor = Color(0xFFA78FFF),
                  secondAnchorColor = Color(0xFF8FDFFF),
                  lineColor = Color(0xFFC3B5FF),
                  areaColor = Color(0xFFC3B5FF),
                  anchorWidth = 2.4f,
                  shapeWidth = 2.6f,
              ),
          move =
              MeasurementRenderStyle(
                  firstAnchorColor = Color(0xFF63FFD7),
                  secondAnchorColor = Color(0xFF38F2B9),
                  lineColor = Color(0xFF68FFE3),
                  areaColor = Color(0xFF68FFE3),
                  anchorWidth = 2.6f,
                  shapeWidth = 2.8f,
              ),
          resize =
              MeasurementRenderStyle(
                  firstAnchorColor = Color(0xFF6EB8FF),
                  secondAnchorColor = Color(0xFF4D8BFF),
                  lineColor = Color(0xFF84C7FF),
                  areaColor = Color(0xFF84C7FF),
                  anchorWidth = 2.6f,
                  shapeWidth = 2.8f,
              ),
          hoverDirect = Color(0xFF7AE7FF),
          hoverOffset = Color(0xFFC88CFF),
          draftSecond = Color(0xFFA8FFA8),
      )

  private val areaPalette =
      ModePalette(
          normal =
              MeasurementRenderStyle(
                  firstAnchorColor = Color(0xFFFFB45A),
                  secondAnchorColor = Color(0xFFFF7A7A),
                  lineColor = Color(0xFFFFAE7D),
                  areaColor = Color(0xFFFF9777),
                  anchorWidth = 1.8f,
                  shapeWidth = 2.0f,
              ),
          selected =
              MeasurementRenderStyle(
                  firstAnchorColor = Color(0xFFFFD48B),
                  secondAnchorColor = Color(0xFFFFC0B0),
                  lineColor = Color(0xFFFFD0A8),
                  areaColor = Color(0xFFFFD2BE),
                  anchorWidth = 3.0f,
                  shapeWidth = 3.3f,
              ),
          paste =
              MeasurementRenderStyle(
                  firstAnchorColor = Color(0xFFFF9FC5),
                  secondAnchorColor = Color(0xFFE4AAFF),
                  lineColor = Color(0xFFFFB7D8),
                  areaColor = Color(0xFFF3B0FF),
                  anchorWidth = 2.4f,
                  shapeWidth = 2.6f,
              ),
          move =
              MeasurementRenderStyle(
                  firstAnchorColor = Color(0xFFFFE07D),
                  secondAnchorColor = Color(0xFFFFC15B),
                  lineColor = Color(0xFFFFD480),
                  areaColor = Color(0xFFFFC270),
                  anchorWidth = 2.6f,
                  shapeWidth = 2.8f,
              ),
          resize =
              MeasurementRenderStyle(
                  firstAnchorColor = Color(0xFFFFBE66),
                  secondAnchorColor = Color(0xFFFF8A54),
                  lineColor = Color(0xFFFFB36B),
                  areaColor = Color(0xFFFF9668),
                  anchorWidth = 2.6f,
                  shapeWidth = 2.8f,
              ),
          hoverDirect = Color(0xFFFFC96E),
          hoverOffset = Color(0xFFFF92CF),
          draftSecond = Color(0xFFFFD37E),
      )

  private val spherePalette =
      ModePalette(
          normal =
              MeasurementRenderStyle(
                  firstAnchorColor = Color(0xFFC594FF),
                  secondAnchorColor = Color(0xFF8EE8FF),
                  lineColor = Color(0xFFD8B8FF),
                  areaColor = Color(0xFFBEC2FF),
                  anchorWidth = 1.8f,
                  shapeWidth = 2.0f,
              ),
          selected =
              MeasurementRenderStyle(
                  firstAnchorColor = Color(0xFFE1C3FF),
                  secondAnchorColor = Color(0xFFC7F3FF),
                  lineColor = Color(0xFFE7D4FF),
                  areaColor = Color(0xFFD7DAFF),
                  anchorWidth = 3.0f,
                  shapeWidth = 3.3f,
              ),
          paste =
              MeasurementRenderStyle(
                  firstAnchorColor = Color(0xFFFFA2E3),
                  secondAnchorColor = Color(0xFFB4B3FF),
                  lineColor = Color(0xFFFFC0F0),
                  areaColor = Color(0xFFCFC8FF),
                  anchorWidth = 2.4f,
                  shapeWidth = 2.6f,
              ),
          move =
              MeasurementRenderStyle(
                  firstAnchorColor = Color(0xFFD1B4FF),
                  secondAnchorColor = Color(0xFF86E1FF),
                  lineColor = Color(0xFFDFC9FF),
                  areaColor = Color(0xFFB7D6FF),
                  anchorWidth = 2.6f,
                  shapeWidth = 2.8f,
              ),
          resize =
              MeasurementRenderStyle(
                  firstAnchorColor = Color(0xFFB98FFF),
                  secondAnchorColor = Color(0xFF72C8FF),
                  lineColor = Color(0xFFC8AEFF),
                  areaColor = Color(0xFF9FC7FF),
                  anchorWidth = 2.6f,
                  shapeWidth = 2.8f,
              ),
          hoverDirect = Color(0xFFD9A8FF),
          hoverOffset = Color(0xFF8DC9FF),
          draftSecond = Color(0xFFC9C1FF),
      )

  fun style(mode: MeasurementMode, visualState: OverlayVisualState): MeasurementRenderStyle {
    val palette = paletteFor(mode)
    return when (visualState) {
      OverlayVisualState.NORMAL -> palette.normal
      OverlayVisualState.HOVERED ->
          palette.selected.copy(
              anchorWidth = palette.normal.anchorWidth + 0.6f,
              shapeWidth = palette.normal.shapeWidth + 0.6f,
          )
      OverlayVisualState.SELECTED -> palette.selected
      OverlayVisualState.PASTE -> palette.paste
      OverlayVisualState.MOVE -> palette.move
      OverlayVisualState.RESIZE -> palette.resize
    }
  }

  fun hoverColor(mode: MeasurementMode, isOffsetTarget: Boolean): Color {
    val palette = paletteFor(mode)
    return if (isOffsetTarget) palette.hoverOffset else palette.hoverDirect
  }

  fun draftSecondColor(mode: MeasurementMode, isOffsetTarget: Boolean): Color =
      when {
        isOffsetTarget -> hoverColor(mode, isOffsetTarget = true)
        else -> paletteFor(mode).draftSecond
      }

  private fun paletteFor(mode: MeasurementMode): ModePalette =
      when (mode) {
        MeasurementMode.LINE -> linePalette
        MeasurementMode.AREA -> areaPalette
        MeasurementMode.SPHERE -> spherePalette
        MeasurementMode.DISABLED -> linePalette
      }
}
