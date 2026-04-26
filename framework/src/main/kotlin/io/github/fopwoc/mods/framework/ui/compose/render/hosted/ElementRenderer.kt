package io.github.fopwoc.mods.framework.ui.compose.render

import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement

internal interface HostedElementRenderer {
    fun drawButton(bounds: Rect, element: LayoutElement.Button)

    fun drawCheckbox(bounds: Rect, element: LayoutElement.Checkbox)

    fun drawTextField(bounds: Rect, element: LayoutElement.TextField)

    fun drawSlider(bounds: Rect, element: LayoutElement.Slider)

    fun drawSelectableList(bounds: Rect, element: LayoutElement.SelectableList)
}

internal object NoOpHostedElementRenderer : HostedElementRenderer {
    override fun drawButton(bounds: Rect, element: LayoutElement.Button) = Unit

    override fun drawCheckbox(bounds: Rect, element: LayoutElement.Checkbox) = Unit

    override fun drawTextField(bounds: Rect, element: LayoutElement.TextField) = Unit

    override fun drawSlider(bounds: Rect, element: LayoutElement.Slider) = Unit

    override fun drawSelectableList(bounds: Rect, element: LayoutElement.SelectableList) = Unit
}

