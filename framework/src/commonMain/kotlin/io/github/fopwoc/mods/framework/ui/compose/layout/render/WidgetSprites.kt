package io.github.fopwoc.mods.framework.ui.compose.layout.render

/** Vanilla-styled control faces; each platform draws them from its own GUI textures. */
internal enum class Widget {
    Button,
    ButtonHovered,
    ButtonDisabled,

    /** The slider's groove: a disabled button face in vanilla. */
    SliderTrack,
    SliderKnob,
    SliderKnobHovered,
    CheckboxBox,
}

internal object WidgetSprites {
    const val SLIDER_KNOB_WIDTH = 8
    const val SLIDER_KNOB_HEIGHT = 20
}
