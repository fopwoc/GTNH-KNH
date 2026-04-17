package io.github.fopwoc.mods.framework.ui.compose.minecraft

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.ObserverHandle
import androidx.compose.runtime.snapshots.Snapshot
import cpw.mods.fml.client.config.GuiButtonExt
import cpw.mods.fml.client.config.GuiCheckBox
import cpw.mods.fml.client.config.GuiSlider
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.ui.compose.layout.ActivePointerSession
import io.github.fopwoc.mods.framework.ui.compose.layout.InputDispatcher
import io.github.fopwoc.mods.framework.ui.compose.layout.InputPressResult
import io.github.fopwoc.mods.framework.ui.compose.layout.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.layout.InputTargetKind
import io.github.fopwoc.mods.framework.ui.compose.layout.LayoutEngine
import io.github.fopwoc.mods.framework.ui.compose.layout.LayoutNode
import io.github.fopwoc.mods.framework.ui.compose.layout.Rect
import io.github.fopwoc.mods.framework.ui.compose.layout.RenderContext
import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
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
    private var cachedLayoutElement: LayoutElement? = null
    private var compositionScope: CoroutineScope? = null
    private var recomposer: Recomposer? = null
    private var recomposeJob: Job? = null
    private var composition: Composition? = null
    private val hostedButtons = IdentityHashMap<Any, HostedButton>()
    private val hostedCheckboxes = IdentityHashMap<Any, HostedCheckbox>()
    private val hostedSelectableLists = IdentityHashMap<Any, HostedSelectableList>()
    private val hostedTextFields = IdentityHashMap<TextFieldState, HostedTextField>()
    private val hostedSliders = IdentityHashMap<Any, HostedSlider>()
    private val renderedInputTargets = mutableListOf<InputTarget>()
    private var renderEpoch: Int = 0
    private var activePointerSession: ActivePointerSession? = null
    private var snapshotApplyObserverHandle: ObserverHandle? = null
    private var snapshotWriteObserverHandle: ObserverHandle? = null
    private var layoutElementDirty: Boolean = true
    private var layoutDirty: Boolean = true
    private var snapshotNotificationsPending: Boolean = true
    private var lastLayoutWidth: Int = -1
    private var lastLayoutHeight: Int = -1

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

        cachedLayoutElement = null
        layoutElementDirty = true
        layoutDirty = true
        snapshotNotificationsPending = true
        lastLayoutWidth = -1
        lastLayoutHeight = -1
        snapshotWriteObserverHandle = Snapshot.registerGlobalWriteObserver {
            snapshotNotificationsPending = true
        }
        snapshotApplyObserverHandle = Snapshot.registerApplyObserver { _, _ ->
            layoutElementDirty = true
            layoutDirty = true
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

        snapshotApplyObserverHandle?.dispose()
        snapshotApplyObserverHandle = null
        snapshotWriteObserverHandle?.dispose()
        snapshotWriteObserverHandle = null

        rootNode.children.clear()
        rootLayout = null
        cachedLayoutElement = null
        hostedButtons.clear()
        hostedCheckboxes.clear()
        hostedSelectableLists.clear()
        hostedTextFields.clear()
        hostedSliders.clear()
        renderedInputTargets.clear()
        activePointerSession = null
        layoutElementDirty = true
        layoutDirty = true
        snapshotNotificationsPending = true
        lastLayoutWidth = -1
        lastLayoutHeight = -1
        super.onGuiClosed()
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        val focusedEntry = hostedTextFields.entries.firstOrNull { (state, _) -> state.focused }
        if (focusedEntry != null) {
            val (state, hosted) = focusedEntry
            if (keyCode == Keyboard.KEY_ESCAPE) {
                state.clearFocus()
                hosted.widget.setFocused(false)
                flushSnapshotNotifications()
                return
            }

            val handled = hosted.widget.textboxKeyTyped(typedChar, keyCode)
            if (handled) {
                state.text = hosted.widget.text
                state.syncFocus(hosted.widget.isFocused)
                flushSnapshotNotifications()
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
        val target = InputDispatcher.findTopmostWheelTarget(renderedInputTargets, mouseX, mouseY)
        if (target?.onWheel?.invoke(mouseX, mouseY, wheelDelta) == true) {
            flushSnapshotNotifications()
        }
    }

    override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
        val target = InputDispatcher.findTopmostPressTarget(renderedInputTargets, mouseX, mouseY)
        val pressResult = target?.onPress?.invoke(mouseX, mouseY, mouseButton) ?: InputPressResult.Ignored
        if (InputDispatcher.shouldBlurFocusedTextFieldAfterPress(mouseButton, target, pressResult)) {
            clearTextFieldFocus()
        }
        if (pressResult.consumed) {
            activePointerSession = pressResult.session
            flushSnapshotNotifications()
            return
        }

        activePointerSession = null
        if (InputDispatcher.shouldBlurFocusedTextFieldAfterPress(mouseButton, target, pressResult)) {
            flushSnapshotNotifications()
        }
        super.mouseClicked(mouseX, mouseY, mouseButton)
    }

    override fun mouseClickMove(mouseX: Int, mouseY: Int, clickedMouseButton: Int, timeSinceLastClick: Long) {
        val session = currentActivePointerSession()?.takeIf { it.button == clickedMouseButton }
        if (session != null) {
            if (session.onDrag(mouseX, mouseY)) {
                flushSnapshotNotifications()
            }
            return
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick)
    }

    override fun mouseMovedOrUp(mouseX: Int, mouseY: Int, state: Int) {
        if (state != -1) {
            val session = currentActivePointerSession()?.takeIf { it.button == state }
            activePointerSession = null
            if (session != null) {
                session.onRelease(mouseX, mouseY, state)
                flushSnapshotNotifications()
                return
            }
        }
        if (state == -1) {
            currentActivePointerSession()
        }
        if (state != -1 && snapshotNotificationsPending) {
            flushSnapshotNotifications()
        }
        super.mouseMovedOrUp(mouseX, mouseY, state)
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        drawComposeBackground()

        val font = fontRendererObj ?: run {
            super.drawScreen(mouseX, mouseY, partialTicks)
            return
        }

        flushSnapshotNotifications()
        frameClock.sendFrame(System.nanoTime())
        flushSnapshotNotifications()
        renderEpoch += 1
        renderedInputTargets.clear()
        val renderContext = MinecraftRenderContext(font, mouseX, mouseY)
        if (layoutElementDirty || cachedLayoutElement == null) {
            cachedLayoutElement = rootNode.toLayoutElement()
            layoutElementDirty = false
            layoutDirty = true
        }
        if (layoutDirty || rootLayout == null || width != lastLayoutWidth || height != lastLayoutHeight) {
            rootLayout = LayoutEngine.layout(
                cachedLayoutElement ?: rootNode.toLayoutElement(),
                renderContext,
                width,
                height
            )
            layoutDirty = false
            lastLayoutWidth = width
            lastLayoutHeight = height
        }
        rootLayout?.draw(renderContext)
        pruneHostedButtons()
        pruneHostedCheckboxes()
        pruneHostedSelectableLists()
        pruneHostedTextFields()
        pruneHostedSliders()
        activePointerSession = activePointerSession?.takeIf(ActivePointerSession::isValid)
        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    private fun clearTextFieldFocus() {
        hostedTextFields.keys.forEach(TextFieldState::clearFocus)
        syncHostedTextFieldFocus()
    }

    private fun focusTextField(target: TextFieldState) {
        hostedTextFields.forEach { (state, hosted) ->
            val focused = state === target
            if (focused) {
                state.requestFocus()
            } else {
                state.clearFocus()
            }
            hosted.widget.setFocused(focused)
        }
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
                iterator.remove()
            }
        }
    }

    private fun pruneHostedCheckboxes() {
        val iterator = hostedCheckboxes.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.lastSeenEpoch != renderEpoch) {
                iterator.remove()
            }
        }
    }

    private fun pruneHostedSelectableLists() {
        val iterator = hostedSelectableLists.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.lastSeenEpoch != renderEpoch) {
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
                iterator.remove()
            }
        }
    }

    private fun currentActivePointerSession(): ActivePointerSession? {
        val session = activePointerSession ?: return null
        if (!session.isValid()) {
            activePointerSession = null
            return null
        }
        return session
    }

    private fun flushSnapshotNotifications() {
        if (!snapshotNotificationsPending) {
            return
        }

        snapshotNotificationsPending = false
        Snapshot.sendApplyNotifications()
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
            hosted.onClick = onClick
            hosted.widget.xPosition = bounds.x
            hosted.widget.yPosition = bounds.y
            hosted.widget.width = bounds.width
            hosted.widget.height = bounds.height
            hosted.widget.displayString = text
            hosted.widget.enabled = enabled
            hosted.widget.visible = true
            hosted.widget.drawButton(mc, mouseX, mouseY)
            registerInputTarget(
                InputTarget(
                    kind = InputTargetKind.BUTTON,
                    bounds = bounds,
                    onPress = { clickX, clickY, button ->
                        if (!hosted.widget.mousePressed(mc, clickX, clickY)) {
                            InputPressResult.Ignored
                        } else {
                            hosted.onClick()
                            InputPressResult.captured(
                                ActivePointerSession(
                                    button = button,
                                    validityCheck = { hostedButtons[hostKey] === hosted },
                                    onReleaseHandler = { releaseX, releaseY, releaseButton ->
                                        hosted.widget.mouseReleased(releaseX, releaseY)
                                        releaseButton == button
                                    }
                                )
                            )
                        }
                    }
                )
            )
        }

        override fun drawVanillaCheckbox(
            bounds: Rect,
            hostKey: Any,
            label: String,
            checked: Boolean,
            enabled: Boolean,
            onCheckedChange: (Boolean) -> Unit
        ) {
            if (bounds.width <= 0 || bounds.height <= 0) {
                return
            }

            val hosted = hostedCheckboxes.getOrPut(hostKey) {
                HostedCheckbox(
                    widget = GuiCheckBox(0, bounds.x, bounds.y, label, checked),
                    onCheckedChange = onCheckedChange
                )
            }

            hosted.lastSeenEpoch = renderEpoch
            hosted.onCheckedChange = onCheckedChange
            hosted.widget.xPosition = bounds.x
            hosted.widget.yPosition = bounds.y
            hosted.widget.width = bounds.width
            hosted.widget.height = bounds.height.coerceAtLeast(11)
            hosted.widget.displayString = label
            hosted.widget.enabled = enabled
            hosted.widget.visible = true
            if (hosted.widget.isChecked() != checked) {
                hosted.widget.setIsChecked(checked)
            }
            hosted.widget.drawButton(mc, mouseX, mouseY)
            registerInputTarget(
                InputTarget(
                    kind = InputTargetKind.CHECKBOX,
                    bounds = bounds,
                    onPress = { clickX, clickY, button ->
                        if (!hosted.widget.mousePressed(mc, clickX, clickY)) {
                            InputPressResult.Ignored
                        } else {
                            hosted.onCheckedChange(hosted.widget.isChecked())
                            InputPressResult.captured(
                                ActivePointerSession(
                                    button = button,
                                    validityCheck = { hostedCheckboxes[hostKey] === hosted },
                                    onReleaseHandler = { releaseX, releaseY, releaseButton ->
                                        hosted.widget.mouseReleased(releaseX, releaseY)
                                        releaseButton == button
                                    }
                                )
                            )
                        }
                    }
                )
            )
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

            if (enabled) {
                registerInputTarget(
                    InputTarget(
                        kind = InputTargetKind.TEXT_FIELD,
                        bounds = bounds,
                        onPress = { clickX, clickY, button ->
                            if (button != 0) {
                                InputPressResult.Ignored
                            } else {
                                focusTextField(state)
                                hosted.widget.mouseClicked(clickX, clickY, button)
                                state.syncFocus(hosted.widget.isFocused)
                                state.text = hosted.widget.text
                                InputPressResult.Consumed
                            }
                        }
                    )
                )
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
            registerInputTarget(
                InputTarget(
                    kind = InputTargetKind.SLIDER,
                    bounds = bounds,
                    onPress = { clickX, clickY, button ->
                        if (!hosted.widget.mousePressed(mc, clickX, clickY)) {
                            InputPressResult.Ignored
                        } else {
                            InputPressResult.captured(
                                ActivePointerSession(
                                    button = button,
                                    validityCheck = { hostedSliders[hostKey] === hosted },
                                    onReleaseHandler = { releaseX, releaseY, releaseButton ->
                                        hosted.widget.mouseReleased(releaseX, releaseY)
                                        releaseButton == button
                                    }
                                )
                            )
                        }
                    }
                )
            )
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
            hosted.update(
                bounds = bounds,
                items = items,
                selectedIndex = selectedIndex,
                onSelectedIndexChange = onSelectedIndexChange
            )
            hosted.render(mouseX, mouseY)
            registerInputTarget(
                InputTarget(
                    kind = InputTargetKind.SELECTABLE_LIST,
                    bounds = bounds,
                    onPress = { clickX, clickY, button ->
                        if (!hosted.handleClick(clickX, clickY)) {
                            InputPressResult.Ignored
                        } else {
                            InputPressResult.captured(
                                ActivePointerSession(
                                    button = button,
                                    validityCheck = { hostedSelectableLists[hostKey] === hosted },
                                    onDragHandler = { _, dragY -> hosted.handleDrag(dragY) },
                                    onReleaseHandler = { _, _, releaseButton ->
                                        hosted.handleRelease()
                                        releaseButton == button
                                    }
                                )
                            )
                        }
                    },
                    onWheel = { _, _, wheelDelta -> hosted.handleWheel(wheelDelta) }
                )
            )
        }

        override fun registerInputTarget(target: InputTarget) {
            val combinedClipRect = when {
                activeClipRect == null -> target.clipRect
                target.clipRect == null -> activeClipRect
                else -> activeClipRect!!.intersect(target.clipRect)
            }
            renderedInputTargets += target.copy(clipRect = combinedClipRect)
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
