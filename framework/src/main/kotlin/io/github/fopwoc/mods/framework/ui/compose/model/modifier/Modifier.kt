package io.github.fopwoc.mods.framework.ui.compose.model.modifier

import androidx.compose.runtime.Stable
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.state.ScrollState
import io.github.fopwoc.mods.framework.ui.compose.text.StyledText
import io.github.fopwoc.mods.framework.ui.compose.unit.UiUnit
import io.github.fopwoc.mods.framework.ui.compose.unit.resolved

internal enum class ScrollDirection {
  VERTICAL,
  HORIZONTAL,
}

@Stable
sealed interface Modifier {
  fun <R> foldIn(initial: R, operation: (R, Element) -> R): R

  fun <R> foldOut(initial: R, operation: (Element, R) -> R): R

  infix fun then(other: Modifier): Modifier =
      if (other === Modifier) {
        this
      } else {
        CombinedModifier(this, other)
      }

  val padding: PaddingValues
    get() =
        foldIn(PaddingValues.Zero) { current, element ->
          when (element) {
            is PaddingElement -> element.values
            else -> current
          }
        }

  val fillMaxWidth: Boolean
    get() =
        foldIn(false) { current, element ->
          current || element is FillMaxWidthElement
        }

  val fillMaxHeight: Boolean
    get() =
        foldIn(false) { current, element ->
          current || element is FillMaxHeightElement
        }

  val fixedWidth: UiUnit?
    get() =
        foldIn<UiUnit?>(null) { current, element ->
          when (element) {
            is FixedWidthElement -> element.width
            else -> current
          }
        }

  val fixedHeight: UiUnit?
    get() =
        foldIn<UiUnit?>(null) { current, element ->
          when (element) {
            is FixedHeightElement -> element.height
            else -> current
          }
        }

  val backgroundColor: Color?
    get() =
        foldIn<Color?>(null) { current, element ->
          when (element) {
            is BackgroundElement -> element.color
            else -> current
          }
        }

  val borderColor: Color?
    get() =
        foldIn<Color?>(null) { current, element ->
          when (element) {
            is BorderElement -> element.color
            else -> current
          }
        }

  val tooltipLines: List<StyledText>?
    get() =
        foldIn<List<StyledText>?>(null) { current, element ->
          when (element) {
            is TooltipElement -> element.lines
            else -> current
          }
        }

  val offsetX: UiUnit
    get() =
        foldIn(UiUnit(0)) { current, element ->
          when (element) {
            is OffsetElement -> element.x
            else -> current
          }
        }

  val offsetY: UiUnit
    get() =
        foldIn(UiUnit(0)) { current, element ->
          when (element) {
            is OffsetElement -> element.y
            else -> current
          }
        }

  companion object : Modifier {
    override fun <R> foldIn(initial: R, operation: (R, Element) -> R): R = initial

    override fun <R> foldOut(initial: R, operation: (Element, R) -> R): R = initial

    override infix fun then(other: Modifier): Modifier = other

    override fun toString(): String = "Modifier"
  }

  interface Element : Modifier {
    override fun <R> foldIn(initial: R, operation: (R, Modifier.Element) -> R): R =
        operation(initial, this)

    override fun <R> foldOut(initial: R, operation: (Modifier.Element, R) -> R): R =
        operation(this, initial)

    override infix fun then(other: Modifier): Modifier =
        if (other === Modifier) {
          this
        } else {
          CombinedModifier(this, other)
        }
  }

  fun padding(all: UiUnit): Modifier = padding(all, all, all, all)

  fun padding(horizontal: UiUnit = UiUnit(0), vertical: UiUnit = UiUnit(0)): Modifier =
      padding(
          left = horizontal,
          top = vertical,
          right = horizontal,
          bottom = vertical,
      )

  fun padding(
      left: UiUnit = UiUnit(0),
      top: UiUnit = UiUnit(0),
      right: UiUnit = UiUnit(0),
      bottom: UiUnit = UiUnit(0),
  ): Modifier {
    val values = PaddingValues(left, top, right, bottom)
    return replaceSingleElement<PaddingElement>(
        replacement = values.takeUnless { it == PaddingValues.Zero }?.let(::PaddingElement)
    )
  }

  fun fillMaxWidth(): Modifier = replaceSingleElement<FillMaxWidthElement>(FillMaxWidthElement)

  fun fillMaxHeight(): Modifier = replaceSingleElement<FillMaxHeightElement>(FillMaxHeightElement)

  fun fillMaxSize(): Modifier = fillMaxWidth().fillMaxHeight()

  fun width(width: UiUnit): Modifier =
      replaceSingleElement<FixedWidthElement>(FixedWidthElement(width))

  fun height(height: UiUnit): Modifier =
      replaceSingleElement<FixedHeightElement>(FixedHeightElement(height))

  fun size(width: UiUnit, height: UiUnit): Modifier = width(width).height(height)

  fun size(size: UiUnit): Modifier = size(size, size)

  fun background(color: Color): Modifier =
      replaceSingleElement<BackgroundElement>(BackgroundElement(color))

  fun border(color: Color): Modifier = replaceSingleElement<BorderElement>(BorderElement(color))

  fun tooltip(text: String): Modifier =
      replaceSingleElement<TooltipElement>(
          replacement =
              text
                  .takeIf(String::isNotEmpty)
                  ?.let(StyledText::of)
                  ?.let(::listOf)
                  ?.let(::TooltipElement)
      )

  fun tooltip(lines: List<String>): Modifier =
      replaceSingleElement<TooltipElement>(
          replacement =
              lines.takeIf(List<String>::isNotEmpty)?.map(StyledText::of)?.let(::TooltipElement)
      )

  fun tooltip(text: StyledText): Modifier =
      replaceSingleElement<TooltipElement>(
          replacement = text.takeIf { it != StyledText.Empty }?.let(::listOf)?.let(::TooltipElement)
      )

  fun tooltip(vararg lines: StyledText): Modifier =
      replaceSingleElement<TooltipElement>(
          replacement =
              lines
                  .filter { it != StyledText.Empty }
                  .takeIf(List<StyledText>::isNotEmpty)
                  ?.let(::TooltipElement)
      )

  fun offset(x: UiUnit = UiUnit(0), y: UiUnit = UiUnit(0)): Modifier =
      replaceSingleElement<OffsetElement>(
          replacement = OffsetElement(x, y).takeUnless { it.x == UiUnit(0) && it.y == UiUnit(0) }
      )

  fun verticalScroll(state: ScrollState): Modifier =
      replaceSingleElement<ScrollElement>(
          ScrollElement(state = state, direction = ScrollDirection.VERTICAL)
      )

  fun horizontalScroll(state: ScrollState): Modifier =
      replaceSingleElement<ScrollElement>(
          ScrollElement(state = state, direction = ScrollDirection.HORIZONTAL)
      )
}

internal data class CombinedModifier(
    private val outer: Modifier,
    private val inner: Modifier,
) : Modifier {
  override fun <R> foldIn(initial: R, operation: (R, Modifier.Element) -> R): R {
    return inner.foldIn(outer.foldIn(initial, operation), operation)
  }

  override fun <R> foldOut(initial: R, operation: (Modifier.Element, R) -> R): R {
    return outer.foldOut(inner.foldOut(initial, operation), operation)
  }
}

private data class PaddingElement(val values: PaddingValues) : Modifier.Element

private object FillMaxWidthElement : Modifier.Element

private object FillMaxHeightElement : Modifier.Element

private data class FixedWidthElement(val width: UiUnit) : Modifier.Element

private data class FixedHeightElement(val height: UiUnit) : Modifier.Element

private data class BackgroundElement(val color: Color) : Modifier.Element

private data class BorderElement(val color: Color) : Modifier.Element

private data class TooltipElement(val lines: List<StyledText>) : Modifier.Element

private data class OffsetElement(val x: UiUnit, val y: UiUnit) : Modifier.Element

private data class ScrollElement(
    val state: ScrollState,
    val direction: ScrollDirection,
) : Modifier.Element

private data class ParentDataElement<T : Any>(
    val key: ParentDataKey<T>,
    val value: T,
) : Modifier.Element

private inline fun <reified T : Modifier.Element> Modifier.replaceSingleElement(
    replacement: Modifier.Element? = null
): Modifier {
  return withoutElementsMatching { it is T }
      .let { updated ->
        if (replacement == null) {
          updated
        } else {
          updated.then(replacement)
        }
      }
}

private fun Modifier.withoutElementsMatching(predicate: (Modifier.Element) -> Boolean): Modifier {
  return foldIn(Modifier as Modifier) { current, element ->
    if (predicate(element)) {
      current
    } else {
      current.then(element)
    }
  }
}

internal fun <T : Any> Modifier.withParentData(
    key: ParentDataKey<T>,
    defaultValue: () -> T,
    transform: (T) -> T,
): Modifier {
  val currentValue = parentDataOrNull(key) ?: defaultValue()
  return withoutElementsMatching { element ->
        element is ParentDataElement<*> && element.key == key
      }
      .then(ParentDataElement(key, transform(currentValue)))
}

@Suppress("UNCHECKED_CAST")
internal fun <T : Any> Modifier.parentDataOrNull(key: ParentDataKey<T>): T? =
    foldIn<T?>(null) { current, element ->
      when {
        element is ParentDataElement<*> && element.key == key -> element.value as T
        else -> current
      }
    }

internal val Modifier.resolvedFixedWidth: Int?
  get() = fixedWidth?.resolved

internal val Modifier.resolvedFixedHeight: Int?
  get() = fixedHeight?.resolved

internal val Modifier.resolvedOffsetX: Int
  get() = offsetX.resolved

internal val Modifier.resolvedOffsetY: Int
  get() = offsetY.resolved

internal val Modifier.verticalScrollState: ScrollState?
  get() =
      foldIn<ScrollState?>(null) { current, element ->
        when (element) {
          is ScrollElement -> element.state.takeIf { element.direction == ScrollDirection.VERTICAL }
          else -> current
        }
      }

internal val Modifier.horizontalScrollState: ScrollState?
  get() =
      foldIn<ScrollState?>(null) { current, element ->
        when (element) {
          is ScrollElement ->
              element.state.takeIf { element.direction == ScrollDirection.HORIZONTAL }
          else -> current
        }
      }
