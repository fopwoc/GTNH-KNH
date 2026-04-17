package io.github.fopwoc.mods.framework.ui.compose.minecraft

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import cpw.mods.fml.client.config.GuiButtonExt
import cpw.mods.fml.client.config.GuiCheckBox
import cpw.mods.fml.client.config.GuiSlider
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.ui.compose.layout.LayoutEngine
import io.github.fopwoc.mods.framework.ui.compose.layout.LayoutNode
import io.github.fopwoc.mods.framework.ui.compose.layout.Rect
import io.github.fopwoc.mods.framework.ui.compose.layout.RenderContext
import io.github.fopwoc.mods.framework.ui.compose.layout.ScrollDragSession
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextFieldStyle
import io.github.fopwoc.mods.framework.ui.compose.node.NodeApplier
import io.github.fopwoc.mods.framework.ui.compose.node.RootNode
import io.github.fopwoc.mods.framework.ui.compose.runtime.LocalComposeGuiScreen
import io.github.fopwoc.mods.framework.ui.compose.state.TextFieldState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.minecraft.client.gui.FontRenderer
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.gui.GuiTextField
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse
import org.lwjgl.opengl.GL11
import java.util.IdentityHashMap

@SideOnly(Side.CLIENT)
abstract class ComposeGuiScreen : GuiScreen() {
    private val rootNode = RootNode()
    private val frameClock = BroadcastFrameClock()

    private var rootLayout: LayoutNode? = null
    private var compositionScope: CoroutineScope? = null
    private var recomposer: Recomposer? = null
    private var recomposeJob: Job? = null
    private var composition: Composition? = null
    private val hostedButtons = IdentityHashMap<Any, HostedButton>()
    private val hostedSelectableLists = IdentityHashMap<Any, HostedSelectableList>()
    private val hostedTextFields = IdentityHashMap<TextFieldState, HostedTextField>()
    private val hostedSliders = IdentityHashMap<Any, HostedSlider>()
    private val renderedHostedButtons = mutableListOf<HostedButton>()
    private val renderedHostedSelectableLists = mutableListOf<HostedSelectableList>()
    private var renderEpoch: Int = 0
    private var activeScrollDrag: ScrollDragSession? = null
    private var activeHostedButton: HostedButton? = null
    private var activeHostedSelectableList: HostedSelectableList? = null
    private var activeHostedSlider: HostedSlider? = null

    @Composable
    protected abstract fun Content()

    protected open val composeBackgroundStyle: ComposeBackgroundStyle
        get() = ComposeBackgroundStyle.Color(0xA0101010.toInt())

    protected open fun drawComposeBackground() {
        when (val style = composeBackgroundStyle) {
            is ComposeBackgroundStyle.Color -> {
                drawRect(0, 0, width, height, style.argb)
            }
            ComposeBackgroundStyle.VanillaDefault -> {
                drawDefaultBackground()
            }
            ComposeBackgroundStyle.None -> Unit
        }
    }

    override fun initGui() {
        super.initGui()

        if (composition != null) {
            return
        }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + frameClock)
        val recomposer = Recomposer(scope.coroutineContext)
        val composition = Composition(NodeApplier(rootNode), recomposer)
        val recomposeJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            recomposer.runRecomposeAndApplyChanges()
        }

        composition.setContent {
            CompositionLocalProvider(
                LocalComposeGuiScreen provides this@ComposeGuiScreen
            ) {
                Content()
            }
        }

        this.compositionScope = scope
        this.recomposer = recomposer
        this.recomposeJob = recomposeJob
        this.composition = composition
    }

    override fun onGuiClosed() {
        composition?.dispose()
        composition = null

        recomposer?.cancel()
        recomposer = null

        recomposeJob?.cancel()
        recomposeJob = null

        compositionScope?.cancel()
        compositionScope = null

        rootNode.children.clear()
        rootLayout = null
        hostedButtons.clear()
        hostedSelectableLists.clear()
        hostedTextFields.clear()
        hostedSliders.clear()
        renderedHostedButtons.clear()
        renderedHostedSelectableLists.clear()
        activeScrollDrag = null
        activeHostedButton = null
        activeHostedSelectableList = null
        activeHostedSlider = null
        super.onGuiClosed()
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        val focusedEntry = hostedTextFields.entries.firstOrNull { (state, _) -> state.focused }
        if (focusedEntry != null) {
            val (state, hosted) = focusedEntry
            if (keyCode == Keyboard.KEY_ESCAPE) {
                state.clearFocus()
                hosted.widget.setFocused(false)
                Snapshot.sendApplyNotifications()
                return
            }

            val handled = hosted.widget.textboxKeyTyped(typedChar, keyCode)
            if (handled) {
                state.text = hosted.widget.text
                state.syncFocus(hosted.widget.isFocused)
                Snapshot.sendApplyNotifications()
                return
            }
        }

        super.keyTyped(typedChar, keyCode)
    }

    override fun handleMouseInput() {
        super.handleMouseInput()

        val wheelDelta = Mouse.getEventDWheel()
        if (wheelDelta == 0 || width <= 0 || height <= 0 || mc == null) {
            return
        }

        val mouseX = Mouse.getEventX() * width / mc.displayWidth
        val mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1
        if (dispatchHostedSelectableListScroll(mouseX, mouseY, wheelDelta)) {
            return
        }
        if (rootLayout?.dispatchScroll(mouseX, mouseY, wheelDelta) == true) {
            Snapshot.sendApplyNotifications()
        }
    }

    override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
        if (mouseButton == 0) {
            clearTextFieldFocus()
            activeScrollDrag = rootLayout?.startScrollDrag(mouseX, mouseY)
            if (activeScrollDrag != null) {
                Snapshot.sendApplyNotifications()
                return
            }

            if (dispatchHostedSelectableListClick(mouseX, mouseY)) {
                Snapshot.sendApplyNotifications()
                return
            }

            if (dispatchHostedButtonClick(mouseX, mouseY)) {
                Snapshot.sendApplyNotifications()
                return
            }

            if (dispatchHostedSliderClick(mouseX, mouseY)) {
                Snapshot.sendApplyNotifications()
                return
            }
        }

        if (mouseButton == 0 && rootLayout?.dispatchClick(mouseX, mouseY) == true) {
            syncHostedTextFieldFocus()
            hostedTextFields.values.forEach { hosted ->
                if (hosted.clipRect?.contains(mouseX, mouseY) != false) {
                    hosted.widget.mouseClicked(mouseX, mouseY, mouseButton)
                    hosted.state.syncFocus(hosted.widget.isFocused)
                    hosted.state.text = hosted.widget.text
                }
            }
            Snapshot.sendApplyNotifications()
            return
        }
        super.mouseClicked(mouseX, mouseY, mouseButton)
    }

    override fun mouseClickMove(mouseX: Int, mouseY: Int, clickedMouseButton: Int, timeSinceLastClick: Long) {
        if (clickedMouseButton == 0 && activeHostedSelectableList?.handleDrag(mouseY) == true) {
            return
        }
        if (clickedMouseButton == 0 && activeScrollDrag?.dragTo(mouseY) == true) {
            Snapshot.sendApplyNotifications()
            return
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick)
    }

    override fun mouseMovedOrUp(mouseX: Int, mouseY: Int, state: Int) {
        var handled = false
        if (state != -1) {
            activeScrollDrag = null
            activeHostedButton?.widget?.mouseReleased(mouseX, mouseY)
            activeHostedButton = null
            activeHostedSelectableList?.handleRelease()
            activeHostedSelectableList = null
            activeHostedSlider?.widget?.mouseReleased(mouseX, mouseY)
            activeHostedSlider = null
            handled = true
        }
        if (handled) {
            Snapshot.sendApplyNotifications()
            return
        }
        super.mouseMovedOrUp(mouseX, mouseY, state)
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        drawComposeBackground()

        val font = fontRendererObj ?: run {
            super.drawScreen(mouseX, mouseY, partialTicks)
            return
        }


        frameClock.sendFrame(System.nanoTime())
        Snapshot.sendApplyNotifications()
        renderEpoch += 1
        renderedHostedButtons.clear()
        renderedHostedSelectableLists.clear()
        val renderContext = MinecraftRenderContext(font, mouseX, mouseY)
        rootLayout = LayoutEngine.layout(rootNode.toLayoutElement(), renderContext, width, height)
        rootLayout?.draw(renderContext)
        pruneHostedButtons()
        pruneHostedSelectableLists()
        pruneHostedTextFields()
        pruneHostedSliders()
        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    private fun clearTextFieldFocus() {
        hostedTextFields.keys.forEach(TextFieldState::clearFocus)
        syncHostedTextFieldFocus()
    }

    private fun syncHostedTextFieldFocus() {
        hostedTextFields.forEach { (state, hosted) ->
            hosted.widget.setFocused(state.focused)
        }
    }

    private fun pruneHostedButtons() {
        val iterator = hostedButtons.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.lastSeenEpoch != renderEpoch) {
                if (activeHostedButton === entry.value) {
                    activeHostedButton = null
                }
                iterator.remove()
            }
        }
    }

    private fun pruneHostedSelectableLists() {
        val iterator = hostedSelectableLists.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.lastSeenEpoch != renderEpoch) {
                if (activeHostedSelectableList === entry.value) {
                    activeHostedSelectableList = null
                }
                iterator.remove()
            }
        }
    }

    private fun pruneHostedTextFields() {
        val iterator = hostedTextFields.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.lastSeenEpoch != renderEpoch) {
                entry.key.clearFocus()
                iterator.remove()
            }
        }
    }

    private fun pruneHostedSliders() {
        val iterator = hostedSliders.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.lastSeenEpoch != renderEpoch) {
                if (activeHostedSlider === entry.value) {
                    activeHostedSlider = null
                }
                iterator.remove()
            }
        }
    }

    private fun dispatchHostedButtonClick(mouseX: Int, mouseY: Int): Boolean {
        renderedHostedButtons.asReversed().forEach { hosted ->
            if (hosted.clipRect?.contains(mouseX, mouseY) == false) {
                return@forEach
            }
            if (hosted.widget.mousePressed(mc, mouseX, mouseY)) {
                activeHostedButton = hosted
                hosted.onClick()
                return true
            }
        }
        return false
    }

    private fun dispatchHostedSelectableListClick(mouseX: Int, mouseY: Int): Boolean {
        renderedHostedSelectableLists.asReversed().forEach { hosted ->
            if (hosted.clipRect?.contains(mouseX, mouseY) == false) {
                return@forEach
            }
            if (hosted.handleClick(mouseX, mouseY)) {
                activeHostedSelectableList = hosted
                return true
            }
        }
        return false
    }

    private fun dispatchHostedSelectableListScroll(mouseX: Int, mouseY: Int, wheelDelta: Int): Boolean {
        renderedHostedSelectableLists.asReversed().forEach { hosted ->
            if (hosted.clipRect?.contains(mouseX, mouseY) == false) {
                return@forEach
            }
            if (hosted.handleWheel(wheelDelta)) {
                return true
            }
        }
        return false
    }

    private fun dispatchHostedSliderClick(mouseX: Int, mouseY: Int): Boolean {
        hostedSliders.values.forEach { hosted ->
            if (hosted.clipRect?.contains(mouseX, mouseY) == false) {
                return@forEach
            }
            if (hosted.widget.mousePressed(mc, mouseX, mouseY)) {
                activeHostedSlider = hosted
                return true
            }
        }
        return false
    }


    private inner class MinecraftRenderContext(
        private val font: FontRenderer,
        override val mouseX: Int,
        override val mouseY: Int
    ) : RenderContext {
        private var activeClipRect: Rect? = null

        override val viewportWidth: Int
            get() = width

        override val viewportHeight: Int
            get() = height

        override val lineHeight: Int
            get() = font.FONT_HEIGHT

        override fun textWidth(text: String): Int = font.getStringWidth(text)

        override fun wrapText(text: String, maxWidth: Int): List<String> {
            if (maxWidth <= 0) {
                return listOf(text)
            }

            return text
                .split('\n')
                .flatMap { segment ->
                    if (segment.isEmpty()) {
                        listOf("")
                    } else {
                        @Suppress("UNCHECKED_CAST")
                        (font.listFormattedStringToWidth(segment, maxWidth) as? List<String>)
                            ?.ifEmpty { listOf(segment) }
                            ?: listOf(segment)
                    }
                }
        }

        override fun fillRect(left: Int, top: Int, right: Int, bottom: Int, color: Int) {
            drawRect(left, top, right, bottom, color)
        }

        override fun drawHorizontalLine(startX: Int, endX: Int, y: Int, color: Int) {
            this@ComposeGuiScreen.drawHorizontalLine(startX, endX, y, color)
        }

        override fun drawVerticalLine(x: Int, startY: Int, endY: Int, color: Int) {
            this@ComposeGuiScreen.drawVerticalLine(x, startY, endY, color)
        }

        override fun drawText(text: String, x: Int, y: Int, color: Int, shadow: Boolean) {
            if (shadow) {
                font.drawStringWithShadow(text, x, y, color)
            } else {
                font.drawString(text, x, y, color)
            }
        }

        override fun drawVanillaButton(
            bounds: Rect,
            hostKey: Any,
            text: String,
            enabled: Boolean,
            onClick: () -> Unit
        ) {
            if (bounds.width <= 0 || bounds.height <= 0) {
                return
            }

            val hosted = hostedButtons.getOrPut(hostKey) {
                HostedButton(
                    widget = GuiButtonExt(0, bounds.x, bounds.y, bounds.width, bounds.height, text),
                    onClick = onClick
                )
            }

            hosted.lastSeenEpoch = renderEpoch
            hosted.clipRect = activeClipRect
            hosted.onClick = onClick
            hosted.widget.xPosition = bounds.x
            hosted.widget.yPosition = bounds.y
            hosted.widget.width = bounds.width
            hosted.widget.height = bounds.height
            hosted.widget.displayString = text
            hosted.widget.enabled = enabled
            hosted.widget.visible = true
            renderedHostedButtons += hosted
            hosted.widget.drawButton(mc, mouseX, mouseY)
        }

        override fun drawVanillaCheckbox(bounds: Rect, label: String, checked: Boolean, enabled: Boolean) {
            if (bounds.width <= 0 || bounds.height <= 0) {
                return
            }

            val widget = GuiCheckBox(0, bounds.x, bounds.y, label, checked)
            widget.enabled = enabled
            widget.visible = true
            widget.width = bounds.width
            widget.height = bounds.height.coerceAtLeast(11)
            widget.drawButton(mc, mouseX, mouseY)
        }

        override fun drawVanillaTextField(
            bounds: Rect,
            state: TextFieldState,
            placeholder: String,
            enabled: Boolean,
            style: TextFieldStyle
        ) {
            val hosted = hostedTextFields.getOrPut(state) {
                val widget = GuiTextField(font, bounds.x, bounds.y, bounds.width, bounds.height)
                widget.setCanLoseFocus(false)
                HostedTextField(state, widget)
            }

            hosted.lastSeenEpoch = renderEpoch
            hosted.clipRect = activeClipRect
            hosted.widget.xPosition = bounds.x
            hosted.widget.yPosition = bounds.y
            hosted.widget.width = bounds.width
            hosted.widget.height = bounds.height
            hosted.widget.setEnabled(enabled)
            hosted.widget.setMaxStringLength(style.maxLength)
            hosted.widget.setTextColor(style.textColor)
            hosted.widget.setDisabledTextColour(style.disabledTextColor)
            hosted.widget.setEnableBackgroundDrawing(style.drawBackground)
            if (hosted.widget.text != state.text) {
                hosted.widget.text = state.text
            }
            hosted.widget.setFocused(state.focused)
            hosted.widget.drawTextBox()

            if (state.text.isEmpty() && !state.focused && placeholder.isNotEmpty()) {
                val placeholderX = bounds.x + if (style.drawBackground) 4 else 0
                val placeholderY = bounds.y + ((bounds.height - font.FONT_HEIGHT) / 2).coerceAtLeast(0)
                font.drawStringWithShadow(placeholder, placeholderX, placeholderY, 0x808080)
            }
        }

        override fun drawVanillaSlider(
            bounds: Rect,
            hostKey: Any,
            value: Double,
            valueRangeStart: Double,
            valueRangeEnd: Double,
            label: String,
            suffix: String,
            enabled: Boolean,
            showDecimal: Boolean,
            onValueChange: (Double) -> Unit
        ) {
            if (bounds.width <= 0 || bounds.height <= 0) {
                return
            }

            val start = minOf(valueRangeStart, valueRangeEnd)
            val end = maxOf(valueRangeStart, valueRangeEnd)
            val coercedValue = value.coerceIn(start, end)
            val prefix = sliderPrefix(label)
            val hosted = hostedSliders[hostKey]
                ?.takeUnless {
                    it.label != label ||
                        it.suffix != suffix ||
                        it.valueRangeStart != start ||
                        it.valueRangeEnd != end ||
                        it.showDecimal != showDecimal
                }
                ?: createHostedSlider(
                    hostKey = hostKey,
                    bounds = bounds,
                    prefix = prefix,
                    label = label,
                    suffix = suffix,
                    valueRangeStart = start,
                    valueRangeEnd = end,
                    value = coercedValue,
                    showDecimal = showDecimal,
                    onValueChange = onValueChange
                )

            hosted.lastSeenEpoch = renderEpoch
            hosted.clipRect = activeClipRect
            hosted.onValueChange = onValueChange
            hosted.widget.xPosition = bounds.x
            hosted.widget.yPosition = bounds.y
            hosted.widget.width = bounds.width
            hosted.widget.height = bounds.height
            hosted.widget.enabled = enabled
            hosted.widget.visible = true
            hosted.widget.dispString = prefix
            hosted.widget.suffix = suffix
            hosted.widget.showDecimal = showDecimal
            if (kotlin.math.abs(hosted.widget.getValue() - coercedValue) > 1e-9) {
                hosted.suppressCallback = true
                hosted.widget.setValue(coercedValue)
                hosted.widget.updateSlider()
                hosted.suppressCallback = false
            }
            hosted.widget.drawButton(mc, mouseX, mouseY)
        }

        override fun drawVanillaSelectableList(
            bounds: Rect,
            hostKey: Any,
            items: List<String>,
            selectedIndex: Int,
            rowHeight: Int,
            onSelectedIndexChange: (Int) -> Unit
        ) {
            if (bounds.width <= 0 || bounds.height <= 0) {
                return
            }

            val hosted = hostedSelectableLists[hostKey]
                ?.takeUnless { it.slotHeight != rowHeight.coerceAtLeast(12) }
                ?: HostedSelectableList(mc, rowHeight).also { hostedSelectableLists[hostKey] = it }

            hosted.lastSeenEpoch = renderEpoch
            hosted.clipRect = activeClipRect
            hosted.update(
                bounds = bounds,
                items = items,
                selectedIndex = selectedIndex,
                onSelectedIndexChange = onSelectedIndexChange
            )
            renderedHostedSelectableLists += hosted
            hosted.render(mouseX, mouseY)
        }

        override fun withClipRect(rect: Rect, block: () -> Unit) {
            val previousClipRect = activeClipRect
            val nextClipRect = previousClipRect?.intersect(rect) ?: rect
            applyClipRect(nextClipRect)
            try {
                block()
            } finally {
                applyClipRect(previousClipRect)
            }
        }

        private fun applyClipRect(rect: Rect?) {
            activeClipRect = rect
            if (rect == null || rect.width <= 0 || rect.height <= 0) {
                GL11.glDisable(GL11.GL_SCISSOR_TEST)
                return
            }

            val scaleX = mc.displayWidth.toDouble() / width.toDouble().coerceAtLeast(1.0)
            val scaleY = mc.displayHeight.toDouble() / height.toDouble().coerceAtLeast(1.0)
            val scissorX = (rect.x * scaleX).toInt()
            val scissorY = (mc.displayHeight - ((rect.y + rect.height) * scaleY)).toInt()
            val scissorWidth = (rect.width * scaleX).toInt().coerceAtLeast(0)
            val scissorHeight = (rect.height * scaleY).toInt().coerceAtLeast(0)

            GL11.glEnable(GL11.GL_SCISSOR_TEST)
            GL11.glScissor(scissorX, scissorY, scissorWidth, scissorHeight)
        }

        private fun createHostedSlider(
            hostKey: Any,
            bounds: Rect,
            prefix: String,
            label: String,
            suffix: String,
            valueRangeStart: Double,
            valueRangeEnd: Double,
            value: Double,
            showDecimal: Boolean,
            onValueChange: (Double) -> Unit
        ): HostedSlider {
            lateinit var hosted: HostedSlider
            val widget = GuiSlider(
                0,
                bounds.x,
                bounds.y,
                bounds.width,
                bounds.height,
                prefix,
                suffix,
                valueRangeStart,
                valueRangeEnd,
                value,
                showDecimal,
                true,
                { slider ->
                    if (!hosted.suppressCallback) {
                        hosted.onValueChange(slider.getValue())
                    }
                }
            )
            hosted = HostedSlider(
                widget = widget,
                label = label,
                suffix = suffix,
                valueRangeStart = valueRangeStart,
                valueRangeEnd = valueRangeEnd,
                showDecimal = showDecimal,
                onValueChange = onValueChange
            )
            hostedSliders[hostKey] = hosted
            return hosted
        }

        private fun sliderPrefix(label: String): String {
            return if (label.isBlank()) {
                ""
            } else {
                "$label: "
            }
        }
    }
}
