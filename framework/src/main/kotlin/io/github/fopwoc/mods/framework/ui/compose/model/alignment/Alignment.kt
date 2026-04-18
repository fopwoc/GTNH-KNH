package io.github.fopwoc.mods.framework.ui.compose.model.alignment

data class Alignment(
    val horizontal: HorizontalAlignment,
    val vertical: VerticalAlignment
) {
    companion object {
        val TopStart: Alignment = Alignment(HorizontalAlignment.START, VerticalAlignment.TOP)
        val TopCenter: Alignment = Alignment(HorizontalAlignment.CENTER, VerticalAlignment.TOP)
        val TopEnd: Alignment = Alignment(HorizontalAlignment.END, VerticalAlignment.TOP)
        val CenterStart: Alignment = Alignment(HorizontalAlignment.START, VerticalAlignment.CENTER)
        val Center: Alignment = Alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER)
        val CenterEnd: Alignment = Alignment(HorizontalAlignment.END, VerticalAlignment.CENTER)
        val BottomStart: Alignment = Alignment(HorizontalAlignment.START, VerticalAlignment.BOTTOM)
        val BottomCenter: Alignment = Alignment(HorizontalAlignment.CENTER, VerticalAlignment.BOTTOM)
        val BottomEnd: Alignment = Alignment(HorizontalAlignment.END, VerticalAlignment.BOTTOM)
    }
}

