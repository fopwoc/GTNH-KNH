package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementMode

enum class OverlayVisualState {
  NORMAL,
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
                  firstAnchorColor = Color.rgb(red = 0x4A, green = 0xA3, blue = 0xFF),
                  secondAnchorColor = Color.rgb(red = 0x65, green = 0xF2, blue = 0xCF),
                  lineColor = Color.rgb(red = 0x5C, green = 0xFF, blue = 0xE0),
                  areaColor = Color.rgb(red = 0x5C, green = 0xFF, blue = 0xE0),
                  anchorWidth = 1.8f,
                  shapeWidth = 2.0f,
              ),
          selected =
              MeasurementRenderStyle(
                  firstAnchorColor = Color.rgb(red = 0x8D, green = 0xDC, blue = 0xFF),
                  secondAnchorColor = Color.rgb(red = 0xB8, green = 0xFF, blue = 0xF0),
                  lineColor = Color.rgb(red = 0xB1, green = 0xFF, blue = 0xF6),
                  areaColor = Color.rgb(red = 0xB1, green = 0xFF, blue = 0xF6),
                  anchorWidth = 3.0f,
                  shapeWidth = 3.3f,
              ),
          paste =
              MeasurementRenderStyle(
                  firstAnchorColor = Color.rgb(red = 0xA7, green = 0x8F, blue = 0xFF),
                  secondAnchorColor = Color.rgb(red = 0x8F, green = 0xDF, blue = 0xFF),
                  lineColor = Color.rgb(red = 0xC3, green = 0xB5, blue = 0xFF),
                  areaColor = Color.rgb(red = 0xC3, green = 0xB5, blue = 0xFF),
                  anchorWidth = 2.4f,
                  shapeWidth = 2.6f,
              ),
          move =
              MeasurementRenderStyle(
                  firstAnchorColor = Color.rgb(red = 0x63, green = 0xFF, blue = 0xD7),
                  secondAnchorColor = Color.rgb(red = 0x38, green = 0xF2, blue = 0xB9),
                  lineColor = Color.rgb(red = 0x68, green = 0xFF, blue = 0xE3),
                  areaColor = Color.rgb(red = 0x68, green = 0xFF, blue = 0xE3),
                  anchorWidth = 2.6f,
                  shapeWidth = 2.8f,
              ),
          resize =
              MeasurementRenderStyle(
                  firstAnchorColor = Color.rgb(red = 0x6E, green = 0xB8, blue = 0xFF),
                  secondAnchorColor = Color.rgb(red = 0x4D, green = 0x8B, blue = 0xFF),
                  lineColor = Color.rgb(red = 0x84, green = 0xC7, blue = 0xFF),
                  areaColor = Color.rgb(red = 0x84, green = 0xC7, blue = 0xFF),
                  anchorWidth = 2.6f,
                  shapeWidth = 2.8f,
              ),
          hoverDirect = Color.rgb(red = 0x7A, green = 0xE7, blue = 0xFF),
          hoverOffset = Color.rgb(red = 0xC8, green = 0x8C, blue = 0xFF),
          draftSecond = Color.rgb(red = 0xA8, green = 0xFF, blue = 0xA8),
      )

  private val areaPalette =
      ModePalette(
          normal =
              MeasurementRenderStyle(
                  firstAnchorColor = Color.rgb(red = 0xFF, green = 0xB4, blue = 0x5A),
                  secondAnchorColor = Color.rgb(red = 0xFF, green = 0x7A, blue = 0x7A),
                  lineColor = Color.rgb(red = 0xFF, green = 0xAE, blue = 0x7D),
                  areaColor = Color.rgb(red = 0xFF, green = 0x97, blue = 0x77),
                  anchorWidth = 1.8f,
                  shapeWidth = 2.0f,
              ),
          selected =
              MeasurementRenderStyle(
                  firstAnchorColor = Color.rgb(red = 0xFF, green = 0xD4, blue = 0x8B),
                  secondAnchorColor = Color.rgb(red = 0xFF, green = 0xC0, blue = 0xB0),
                  lineColor = Color.rgb(red = 0xFF, green = 0xD0, blue = 0xA8),
                  areaColor = Color.rgb(red = 0xFF, green = 0xD2, blue = 0xBE),
                  anchorWidth = 3.0f,
                  shapeWidth = 3.3f,
              ),
          paste =
              MeasurementRenderStyle(
                  firstAnchorColor = Color.rgb(red = 0xFF, green = 0x9F, blue = 0xC5),
                  secondAnchorColor = Color.rgb(red = 0xE4, green = 0xAA, blue = 0xFF),
                  lineColor = Color.rgb(red = 0xFF, green = 0xB7, blue = 0xD8),
                  areaColor = Color.rgb(red = 0xF3, green = 0xB0, blue = 0xFF),
                  anchorWidth = 2.4f,
                  shapeWidth = 2.6f,
              ),
          move =
              MeasurementRenderStyle(
                  firstAnchorColor = Color.rgb(red = 0xFF, green = 0xE0, blue = 0x7D),
                  secondAnchorColor = Color.rgb(red = 0xFF, green = 0xC1, blue = 0x5B),
                  lineColor = Color.rgb(red = 0xFF, green = 0xD4, blue = 0x80),
                  areaColor = Color.rgb(red = 0xFF, green = 0xC2, blue = 0x70),
                  anchorWidth = 2.6f,
                  shapeWidth = 2.8f,
              ),
          resize =
              MeasurementRenderStyle(
                  firstAnchorColor = Color.rgb(red = 0xFF, green = 0xBE, blue = 0x66),
                  secondAnchorColor = Color.rgb(red = 0xFF, green = 0x8A, blue = 0x54),
                  lineColor = Color.rgb(red = 0xFF, green = 0xB3, blue = 0x6B),
                  areaColor = Color.rgb(red = 0xFF, green = 0x96, blue = 0x68),
                  anchorWidth = 2.6f,
                  shapeWidth = 2.8f,
              ),
          hoverDirect = Color.rgb(red = 0xFF, green = 0xC9, blue = 0x6E),
          hoverOffset = Color.rgb(red = 0xFF, green = 0x92, blue = 0xCF),
          draftSecond = Color.rgb(red = 0xFF, green = 0xD3, blue = 0x7E),
      )

  private val spherePalette =
      ModePalette(
          normal =
              MeasurementRenderStyle(
                  firstAnchorColor = Color.rgb(red = 0xC5, green = 0x94, blue = 0xFF),
                  secondAnchorColor = Color.rgb(red = 0x8E, green = 0xE8, blue = 0xFF),
                  lineColor = Color.rgb(red = 0xD8, green = 0xB8, blue = 0xFF),
                  areaColor = Color.rgb(red = 0xBE, green = 0xC2, blue = 0xFF),
                  anchorWidth = 1.8f,
                  shapeWidth = 2.0f,
              ),
          selected =
              MeasurementRenderStyle(
                  firstAnchorColor = Color.rgb(red = 0xE1, green = 0xC3, blue = 0xFF),
                  secondAnchorColor = Color.rgb(red = 0xC7, green = 0xF3, blue = 0xFF),
                  lineColor = Color.rgb(red = 0xE7, green = 0xD4, blue = 0xFF),
                  areaColor = Color.rgb(red = 0xD7, green = 0xDA, blue = 0xFF),
                  anchorWidth = 3.0f,
                  shapeWidth = 3.3f,
              ),
          paste =
              MeasurementRenderStyle(
                  firstAnchorColor = Color.rgb(red = 0xFF, green = 0xA2, blue = 0xE3),
                  secondAnchorColor = Color.rgb(red = 0xB4, green = 0xB3, blue = 0xFF),
                  lineColor = Color.rgb(red = 0xFF, green = 0xC0, blue = 0xF0),
                  areaColor = Color.rgb(red = 0xCF, green = 0xC8, blue = 0xFF),
                  anchorWidth = 2.4f,
                  shapeWidth = 2.6f,
              ),
          move =
              MeasurementRenderStyle(
                  firstAnchorColor = Color.rgb(red = 0xD1, green = 0xB4, blue = 0xFF),
                  secondAnchorColor = Color.rgb(red = 0x86, green = 0xE1, blue = 0xFF),
                  lineColor = Color.rgb(red = 0xDF, green = 0xC9, blue = 0xFF),
                  areaColor = Color.rgb(red = 0xB7, green = 0xD6, blue = 0xFF),
                  anchorWidth = 2.6f,
                  shapeWidth = 2.8f,
              ),
          resize =
              MeasurementRenderStyle(
                  firstAnchorColor = Color.rgb(red = 0xB9, green = 0x8F, blue = 0xFF),
                  secondAnchorColor = Color.rgb(red = 0x72, green = 0xC8, blue = 0xFF),
                  lineColor = Color.rgb(red = 0xC8, green = 0xAE, blue = 0xFF),
                  areaColor = Color.rgb(red = 0x9F, green = 0xC7, blue = 0xFF),
                  anchorWidth = 2.6f,
                  shapeWidth = 2.8f,
              ),
          hoverDirect = Color.rgb(red = 0xD9, green = 0xA8, blue = 0xFF),
          hoverOffset = Color.rgb(red = 0x8D, green = 0xC9, blue = 0xFF),
          draftSecond = Color.rgb(red = 0xC9, green = 0xC1, blue = 0xFF),
      )

  fun style(mode: MeasurementMode, visualState: OverlayVisualState): MeasurementRenderStyle {
    val palette = paletteFor(mode)
    return when (visualState) {
      OverlayVisualState.NORMAL -> palette.normal
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
