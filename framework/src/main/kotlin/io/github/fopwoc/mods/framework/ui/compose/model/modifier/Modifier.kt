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
    get() = resolved.padding

  val fillMaxWidth: Boolean
    get() = resolved.fillMaxWidth

  val fillMaxHeight: Boolean
    get() = resolved.fillMaxHeight

  val fixedWidth: UiUnit?
    get() = resolved.fixedWidth

  val fixedHeight: UiUnit?
    get() = resolved.fixedHeight

  val backgroundColor: Color?
    get() = resolved.backgroundColor

  val borderColor: Color?
    get() = resolved.borderColor

  val tooltipLines: List<StyledText>?
    get() = resolved.tooltipLines

  val offsetX: UiUnit
    get() = resolved.offsetX

  val offsetY: UiUnit
    get() = resolved.offsetY

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
  // Chains are immutable, so the single fold that answers every layout query is done once per
  // chain instead of once per property per frame. Not part of equals/hashCode.
  internal val resolvedChain: ResolvedModifier by
      lazy(LazyThreadSafetyMode.NONE) {
        ResolvedModifier.fold(this)
      }

  override fun <R> foldIn(initial: R, operation: (R, Modifier.Element) -> R): R {
    return inner.foldIn(outer.foldIn(initial, operation), operation)
  }

  override fun <R> foldOut(initial: R, operation: (Modifier.Element, R) -> R): R {
    return outer.foldOut(inner.foldOut(initial, operation), operation)
  }
}

/** Every layout-relevant fact about a modifier chain, last element wins per kind. */
internal class ResolvedModifier(
    val padding: PaddingValues,
    val fillMaxWidth: Boolean,
    val fillMaxHeight: Boolean,
    val fixedWidth: UiUnit?,
    val fixedHeight: UiUnit?,
    val backgroundColor: Color?,
    val borderColor: Color?,
    val tooltipLines: List<StyledText>?,
    val offsetX: UiUnit,
    val offsetY: UiUnit,
    val verticalScrollState: ScrollState?,
    val horizontalScrollState: ScrollState?,
    val parentData: Map<ParentDataKey<*>, Any>,
) {
  internal companion object {
    val Empty =
        ResolvedModifier(
            padding = PaddingValues.Zero,
            fillMaxWidth = false,
            fillMaxHeight = false,
            fixedWidth = null,
            fixedHeight = null,
            backgroundColor = null,
            borderColor = null,
            tooltipLines = null,
            offsetX = UiUnit(0),
            offsetY = UiUnit(0),
            verticalScrollState = null,
            horizontalScrollState = null,
            parentData = emptyMap(),
        )

    fun fold(modifier: Modifier): ResolvedModifier {
      var padding = PaddingValues.Zero
      var fillMaxWidth = false
      var fillMaxHeight = false
      var fixedWidth: UiUnit? = null
      var fixedHeight: UiUnit? = null
      var backgroundColor: Color? = null
      var borderColor: Color? = null
      var tooltipLines: List<StyledText>? = null
      var offsetX = UiUnit(0)
      var offsetY = UiUnit(0)
      var verticalScrollState: ScrollState? = null
      var horizontalScrollState: ScrollState? = null
      var parentData: MutableMap<ParentDataKey<*>, Any>? = null
      modifier.foldIn(Unit) { _, element ->
        when (element) {
          is PaddingElement -> padding = element.values
          is FillMaxWidthElement -> fillMaxWidth = true
          is FillMaxHeightElement -> fillMaxHeight = true
          is FixedWidthElement -> fixedWidth = element.width
          is FixedHeightElement -> fixedHeight = element.height
          is BackgroundElement -> backgroundColor = element.color
          is BorderElement -> borderColor = element.color
          is TooltipElement -> tooltipLines = element.lines
          is OffsetElement -> {
            offsetX = element.x
            offsetY = element.y
          }
          // A node scrolls on one axis; the last scroll element decides which.
          is ScrollElement ->
              when (element.direction) {
                ScrollDirection.VERTICAL -> {
                  verticalScrollState = element.state
                  horizontalScrollState = null
                }
                ScrollDirection.HORIZONTAL -> {
                  horizontalScrollState = element.state
                  verticalScrollState = null
                }
              }
          is ParentDataElement<*> ->
              (parentData ?: HashMap<ParentDataKey<*>, Any>().also { parentData = it })[
                  element.key] = element.value
          else -> Unit
        }
      }
      return ResolvedModifier(
          padding = padding,
          fillMaxWidth = fillMaxWidth,
          fillMaxHeight = fillMaxHeight,
          fixedWidth = fixedWidth,
          fixedHeight = fixedHeight,
          backgroundColor = backgroundColor,
          borderColor = borderColor,
          tooltipLines = tooltipLines,
          offsetX = offsetX,
          offsetY = offsetY,
          verticalScrollState = verticalScrollState,
          horizontalScrollState = horizontalScrollState,
          parentData = parentData ?: emptyMap(),
      )
    }
  }
}

internal val Modifier.resolved: ResolvedModifier
  get() =
      when (this) {
        is CombinedModifier -> resolvedChain
        Modifier -> ResolvedModifier.Empty
        else -> ResolvedModifier.fold(this)
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
    resolved.parentData[key] as T?

internal val Modifier.resolvedFixedWidth: Int?
  get() = fixedWidth?.resolved

internal val Modifier.resolvedFixedHeight: Int?
  get() = fixedHeight?.resolved

internal val Modifier.resolvedOffsetX: Int
  get() = offsetX.resolved

internal val Modifier.resolvedOffsetY: Int
  get() = offsetY.resolved

internal val Modifier.verticalScrollState: ScrollState?
  get() = resolved.verticalScrollState

internal val Modifier.horizontalScrollState: ScrollState?
  get() = resolved.horizontalScrollState
