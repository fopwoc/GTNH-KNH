package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementMode

enum class OverlayVisualState {
    NORMAL,
    SELECTED,
    PASTE,
    MOVE,
    RESIZE
}

data class MeasurementRenderStyle(
    val firstAnchorColor: Int,
    val secondAnchorColor: Int,
    val lineColor: Int,
    val areaColor: Int,
    val anchorWidth: Float,
    val shapeWidth: Float
) {
    fun shapeColor(mode: MeasurementMode): Int = when (mode) {
        MeasurementMode.LINE -> lineColor
        MeasurementMode.AREA -> areaColor
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
        val hoverDirect: Int,
        val hoverOffset: Int,
        val draftSecond: Int
    )

    private val linePalette = ModePalette(
        normal = MeasurementRenderStyle(
            firstAnchorColor = 0x4AA3FF,
            secondAnchorColor = 0x65F2CF,
            lineColor = 0x5CFFE0,
            areaColor = 0x5CFFE0,
            anchorWidth = 1.8f,
            shapeWidth = 2.0f
        ),
        selected = MeasurementRenderStyle(
            firstAnchorColor = 0x8DDCFF,
            secondAnchorColor = 0xB8FFF0,
            lineColor = 0xB1FFF6,
            areaColor = 0xB1FFF6,
            anchorWidth = 3.0f,
            shapeWidth = 3.3f
        ),
        paste = MeasurementRenderStyle(
            firstAnchorColor = 0xA78FFF,
            secondAnchorColor = 0x8FDFFF,
            lineColor = 0xC3B5FF,
            areaColor = 0xC3B5FF,
            anchorWidth = 2.4f,
            shapeWidth = 2.6f
        ),
        move = MeasurementRenderStyle(
            firstAnchorColor = 0x63FFD7,
            secondAnchorColor = 0x38F2B9,
            lineColor = 0x68FFE3,
            areaColor = 0x68FFE3,
            anchorWidth = 2.6f,
            shapeWidth = 2.8f
        ),
        resize = MeasurementRenderStyle(
            firstAnchorColor = 0x6EB8FF,
            secondAnchorColor = 0x4D8BFF,
            lineColor = 0x84C7FF,
            areaColor = 0x84C7FF,
            anchorWidth = 2.6f,
            shapeWidth = 2.8f
        ),
        hoverDirect = 0x7AE7FF,
        hoverOffset = 0xC88CFF,
        draftSecond = 0xA8FFA8
    )

    private val areaPalette = ModePalette(
        normal = MeasurementRenderStyle(
            firstAnchorColor = 0xFFB45A,
            secondAnchorColor = 0xFF7A7A,
            lineColor = 0xFFAE7D,
            areaColor = 0xFF9777,
            anchorWidth = 1.8f,
            shapeWidth = 2.0f
        ),
        selected = MeasurementRenderStyle(
            firstAnchorColor = 0xFFD48B,
            secondAnchorColor = 0xFFC0B0,
            lineColor = 0xFFD0A8,
            areaColor = 0xFFD2BE,
            anchorWidth = 3.0f,
            shapeWidth = 3.3f
        ),
        paste = MeasurementRenderStyle(
            firstAnchorColor = 0xFF9FC5,
            secondAnchorColor = 0xE4AAFF,
            lineColor = 0xFFB7D8,
            areaColor = 0xF3B0FF,
            anchorWidth = 2.4f,
            shapeWidth = 2.6f
        ),
        move = MeasurementRenderStyle(
            firstAnchorColor = 0xFFE07D,
            secondAnchorColor = 0xFFC15B,
            lineColor = 0xFFD480,
            areaColor = 0xFFC270,
            anchorWidth = 2.6f,
            shapeWidth = 2.8f
        ),
        resize = MeasurementRenderStyle(
            firstAnchorColor = 0xFFBE66,
            secondAnchorColor = 0xFF8A54,
            lineColor = 0xFFB36B,
            areaColor = 0xFF9668,
            anchorWidth = 2.6f,
            shapeWidth = 2.8f
        ),
        hoverDirect = 0xFFC96E,
        hoverOffset = 0xFF92CF,
        draftSecond = 0xFFD37E
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

    fun hoverColor(mode: MeasurementMode, isOffsetTarget: Boolean): Int {
        val palette = paletteFor(mode)
        return if (isOffsetTarget) palette.hoverOffset else palette.hoverDirect
    }

    fun draftSecondColor(mode: MeasurementMode, isOffsetTarget: Boolean): Int = when {
        isOffsetTarget -> hoverColor(mode, isOffsetTarget = true)
        else -> paletteFor(mode).draftSecond
    }

    private fun paletteFor(mode: MeasurementMode): ModePalette = when (mode) {
        MeasurementMode.LINE -> linePalette
        MeasurementMode.AREA -> areaPalette
        MeasurementMode.DISABLED -> linePalette
    }
}

