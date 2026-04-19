package io.github.fopwoc.mods.framework.ui.compose.layout

import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement

internal fun LayoutElement.isLayoutEquivalentTo(other: LayoutElement): Boolean {
    return when (this) {
        is LayoutElement.Box -> {
            val otherBox = other as? LayoutElement.Box ?: return false
            modifier == otherBox.modifier &&
                contentAlignment == otherBox.contentAlignment &&
                children.areLayoutEquivalentTo(otherBox.children)
        }

        is LayoutElement.Column -> {
            val otherColumn = other as? LayoutElement.Column ?: return false
            modifier == otherColumn.modifier &&
                verticalArrangement == otherColumn.verticalArrangement &&
                horizontalAlignment == otherColumn.horizontalAlignment &&
                children.areLayoutEquivalentTo(otherColumn.children)
        }

        is LayoutElement.ScrollableColumn -> {
            val otherColumn = other as? LayoutElement.ScrollableColumn ?: return false
            modifier == otherColumn.modifier &&
                verticalArrangement == otherColumn.verticalArrangement &&
                horizontalAlignment == otherColumn.horizontalAlignment &&
                scrollValue == otherColumn.scrollValue &&
                children.areLayoutEquivalentTo(otherColumn.children)
        }

        is LayoutElement.ScrollableRow -> {
            val otherRow = other as? LayoutElement.ScrollableRow ?: return false
            modifier == otherRow.modifier &&
                horizontalArrangement == otherRow.horizontalArrangement &&
                verticalAlignment == otherRow.verticalAlignment &&
                scrollValue == otherRow.scrollValue &&
                children.areLayoutEquivalentTo(otherRow.children)
        }

        is LayoutElement.Row -> {
            val otherRow = other as? LayoutElement.Row ?: return false
            modifier == otherRow.modifier &&
                horizontalArrangement == otherRow.horizontalArrangement &&
                verticalAlignment == otherRow.verticalAlignment &&
                children.areLayoutEquivalentTo(otherRow.children)
        }

        is LayoutElement.Text -> {
            val otherText = other as? LayoutElement.Text ?: return false
            modifier == otherText.modifier &&
                text == otherText.text &&
                style == otherText.style
        }

        is LayoutElement.Button -> {
            val otherButton = other as? LayoutElement.Button ?: return false
            modifier == otherButton.modifier &&
                text == otherButton.text
        }

        is LayoutElement.Checkbox -> {
            val otherCheckbox = other as? LayoutElement.Checkbox ?: return false
            modifier == otherCheckbox.modifier &&
                label == otherCheckbox.label
        }

        is LayoutElement.TextField -> {
            val otherField = other as? LayoutElement.TextField ?: return false
            modifier == otherField.modifier
        }

        is LayoutElement.Slider -> {
            val otherSlider = other as? LayoutElement.Slider ?: return false
            modifier == otherSlider.modifier
        }

        is LayoutElement.SelectableList -> {
            val otherList = other as? LayoutElement.SelectableList ?: return false
            modifier == otherList.modifier &&
                items == otherList.items &&
                rowHeight == otherList.rowHeight &&
                visibleRowCount == otherList.visibleRowCount
        }

        is LayoutElement.Spacer -> {
            val otherSpacer = other as? LayoutElement.Spacer ?: return false
            modifier == otherSpacer.modifier
        }
    }
}

private fun List<LayoutElement>.areLayoutEquivalentTo(other: List<LayoutElement>): Boolean {
    return size == other.size && indices.all { index ->
        this[index].isLayoutEquivalentTo(other[index])
    }
}

