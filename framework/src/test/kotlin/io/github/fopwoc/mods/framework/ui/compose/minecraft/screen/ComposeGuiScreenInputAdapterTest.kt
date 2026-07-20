package io.github.fopwoc.mods.framework.ui.compose.minecraft.screen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.fopwoc.mods.framework.ui.compose.component.native.Button
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.layout.render.RenderContext
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTargetKind
import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputPressResult
import io.github.fopwoc.mods.framework.ui.compose.layout.core.LayoutEngine
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import io.github.fopwoc.mods.framework.ui.compose.minecraft.session.ComposeRenderLayoutState
import io.github.fopwoc.mods.framework.ui.compose.minecraft.hosted.MinecraftHostedWidgetRegistry
import io.github.fopwoc.mods.framework.ui.compose.minecraft.session.ComposeRenderRuntimeSync
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.navigation.NavHost
import io.github.fopwoc.mods.framework.ui.compose.runtime.BackCallback
import io.github.fopwoc.mods.framework.ui.compose.runtime.ComposeBackDispatcher
import io.github.fopwoc.mods.framework.ui.compose.runtime.ComposeGuiRuntime
import io.github.fopwoc.mods.framework.ui.compose.runtime.ComposeViewModelOwner
import io.github.fopwoc.mods.framework.ui.compose.navigation.NavKey
import io.github.fopwoc.mods.framework.ui.compose.navigation.entryProvider
import io.github.fopwoc.mods.framework.ui.compose.navigation.rememberNavBackStack
import io.github.fopwoc.mods.framework.ui.compose.navigation.rememberNavigator
import io.github.fopwoc.mods.framework.ui.compose.node.RootNode
import io.github.fopwoc.mods.framework.ui.compose.node.TextNode
import io.github.fopwoc.mods.framework.ui.compose.render.HostedElementRenderer
import io.github.fopwoc.mods.framework.ui.compose.runtime.collectAsStateWithLifecycle as collectAsStateWithFrameworkLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.lwjgl.input.Keyboard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ComposeGuiScreenInputAdapterTest {
    @Test
    fun keyTypedUsesBackDispatcherForEscapeBeforeFallback() {
        val runtime = ComposeGuiRuntime(onCompositionChanged = {})
        val backDispatcher = ComposeBackDispatcher()
        val inputAdapter = ComposeGuiScreenInputAdapter(
            backDispatcher = backDispatcher,
            interactionState = ComposeGuiScreenInteractionState(MinecraftHostedWidgetRegistry()),
            renderedInputTargets = emptyList(),
            runtimeSync = ComposeRenderRuntimeSync(runtime)
        )
        var backCount = 0
        var fallbackCalled = false
        val registration = backDispatcher.register(
            BackCallback(
                enabled = true,
                onBack = {
                    backCount += 1
                    true
                }
            )
        )

        try {
            inputAdapter.keyTyped('\u0000', Keyboard.KEY_ESCAPE) {
                fallbackCalled = true
            }

            assertEquals(1, backCount)
            assertFalse(fallbackCalled)
        } finally {
            registration.dispose()
        }
    }

    @Test
    fun resolveMouseWheelEventReturnsNullWhenInputCannotBeScaled() {
        assertNull(
            resolveMouseWheelEvent(
                width = 100,
                height = 80,
                displayWidth = 200,
                displayHeight = 100,
                event = MouseWheelEvent(wheelDelta = 0, eventX = 40, eventY = 20)
            )
        )
        assertNull(
            resolveMouseWheelEvent(
                width = 100,
                height = 80,
                displayWidth = null,
                displayHeight = 100,
                event = MouseWheelEvent(wheelDelta = -120, eventX = 40, eventY = 20)
            )
        )
        assertNull(
            resolveMouseWheelEvent(
                width = 100,
                height = 80,
                displayWidth = 0,
                displayHeight = 100,
                event = MouseWheelEvent(wheelDelta = -120, eventX = 40, eventY = 20)
            )
        )
    }

    @Test
    fun resolveMouseWheelEventScalesDisplayCoordinatesIntoGuiSpace() {
        val resolved = resolveMouseWheelEvent(
            width = 100,
            height = 80,
            displayWidth = 200,
            displayHeight = 160,
            event = MouseWheelEvent(wheelDelta = -120, eventX = 50, eventY = 40)
        )

        assertEquals(ResolvedMouseWheelEvent(wheelDelta = -120, mouseX = 25, mouseY = 59), resolved)
    }

    @Test
    fun handleMouseInputRunsBaseFirstAndDispatchesToTopmostWheelTarget() {
        val runtime = ComposeGuiRuntime(onCompositionChanged = {})
        val events = mutableListOf<String>()
        val inputAdapter = ComposeGuiScreenInputAdapter(
            backDispatcher = ComposeBackDispatcher(),
            interactionState = ComposeGuiScreenInteractionState(MinecraftHostedWidgetRegistry()),
            renderedInputTargets = listOf(
                InputTarget(
                    kind = InputTargetKind.SCROLL_WHEEL,
                    bounds = Rect(0, 0, 80, 80),
                    onWheel = { _, _, _ ->
                        events += "bottom"
                        true
                    }
                ),
                InputTarget(
                    kind = InputTargetKind.SELECTABLE_LIST,
                    bounds = Rect(0, 0, 80, 80),
                    onWheel = { mouseX, mouseY, wheelDelta ->
                        events += "top:$mouseX,$mouseY,$wheelDelta:${events.firstOrNull()}"
                        true
                    }
                )
            ),
            runtimeSync = ComposeRenderRuntimeSync(runtime),
            mouseEventReader = object : MouseEventReader {
                override fun readWheelEvent(): MouseWheelEvent {
                    return MouseWheelEvent(wheelDelta = -120, eventX = 50, eventY = 40)
                }
            }
        )

        inputAdapter.handleMouseInput(
            width = 100,
            height = 80,
            displayWidth = 200,
            displayHeight = 160
        ) {
            events += "base"
        }

        assertEquals(listOf("base", "top:25,59,-120:base"), events)
    }

    @Test
    fun handleMouseInputIgnoresWheelDispatchWhenResolvedPointHasNoTarget() {
        val runtime = ComposeGuiRuntime(onCompositionChanged = {})
        var baseCalled = false
        var dispatched = false
        val inputAdapter = ComposeGuiScreenInputAdapter(
            backDispatcher = ComposeBackDispatcher(),
            interactionState = ComposeGuiScreenInteractionState(MinecraftHostedWidgetRegistry()),
            renderedInputTargets = listOf(
                InputTarget(
                    kind = InputTargetKind.SCROLL_WHEEL,
                    bounds = Rect(70, 70, 10, 10),
                    onWheel = { _, _, _ ->
                        dispatched = true
                        true
                    }
                )
            ),
            runtimeSync = ComposeRenderRuntimeSync(runtime),
            mouseEventReader = object : MouseEventReader {
                override fun readWheelEvent(): MouseWheelEvent {
                    return MouseWheelEvent(wheelDelta = -120, eventX = 10, eventY = 10)
                }
            }
        )

        inputAdapter.handleMouseInput(
            width = 100,
            height = 80,
            displayWidth = 100,
            displayHeight = 80
        ) {
            baseCalled = true
        }

        assertTrue(baseCalled)
        assertFalse(dispatched)
    }

    @Test
    fun mouseClickedPumpsComposeAfterHandledInputForPlainSnapshotState() {
        val root = RootNode()
        val runtime = ComposeGuiRuntime(onCompositionChanged = {})
        val label = mutableStateOf("before")
        val renderedTargets = mutableListOf<InputTarget>()
        val runtimeSync = ComposeRenderRuntimeSync(runtime)
        val inputAdapter = ComposeGuiScreenInputAdapter(
            backDispatcher = ComposeBackDispatcher(),
            interactionState = ComposeGuiScreenInteractionState(MinecraftHostedWidgetRegistry()),
            renderedInputTargets = renderedTargets,
            runtimeSync = runtimeSync
        )

        try {
            runtime.start(root) {
                Text(text = label.value)
            }
            renderedTargets += InputTarget(
                kind = InputTargetKind.BUTTON,
                bounds = Rect(0, 0, 40, 20),
                onPress = { _, _, _ ->
                    label.value = "after"
                    InputPressResult.Consumed
                }
            )

            inputAdapter.mouseClicked(mouseX = 10, mouseY = 10, mouseButton = 0) {
                error("fallback should not be used when press target consumes the click")
            }
            assertEquals("after", label.value)

            runtimeSync.updateScreen(frameTimeNanos = 16L)
            assertEquals("after", (root.children.single() as TextNode).text.plainText)
        } finally {
            runtime.dispose()
        }
    }

    @Test
    fun mouseClickedPumpsComposeAfterHandledInputForNavLikeBackStackMutation() {
        val root = RootNode()
        val runtime = ComposeGuiRuntime(onCompositionChanged = {})
        val backStack = io.github.fopwoc.mods.framework.ui.compose.navigation.navBackStackOf<TestDestination>(
            TestDestination.Overview,
            TestDestination.Overview
        )
        val renderedTargets = mutableListOf<InputTarget>()
        val runtimeSync = ComposeRenderRuntimeSync(runtime)
        val inputAdapter = ComposeGuiScreenInputAdapter(
            backDispatcher = ComposeBackDispatcher(),
            interactionState = ComposeGuiScreenInteractionState(MinecraftHostedWidgetRegistry()),
            renderedInputTargets = renderedTargets,
            runtimeSync = runtimeSync
        )

        try {
            runtime.start(root) {
                Text(text = backStack.currentKey?.label ?: "none")
            }
            renderedTargets += InputTarget(
                kind = InputTargetKind.BUTTON,
                bounds = Rect(0, 0, 40, 20),
                onPress = { _, _, _ ->
                    backStack.replaceTop(TestDestination.Controls)
                    InputPressResult.Consumed
                }
            )

            inputAdapter.mouseClicked(mouseX = 10, mouseY = 10, mouseButton = 0) {
                error("fallback should not be used when press target consumes the click")
            }
            assertEquals(TestDestination.Controls, backStack.currentKey)

            runtimeSync.updateScreen(frameTimeNanos = 16L)
            assertEquals("controls", (root.children.single() as TextNode).text.plainText)
        } finally {
            runtime.dispose()
        }
    }

    @Test
    fun syncBeforeRenderDeliversPendingSnapshotChangesWithoutWaitingForUpdateTick() {
        val root = RootNode()
        val runtime = ComposeGuiRuntime(onCompositionChanged = {})
        val label = mutableStateOf("before")
        val runtimeSync = ComposeRenderRuntimeSync(runtime)

        try {
            runtime.start(root) {
                Text(text = label.value)
            }

            label.value = "after"
            runtimeSync.syncBeforeRender()

            assertEquals("after", (root.children.single() as TextNode).text.plainText)
        } finally {
            runtime.dispose()
        }
    }

    @Test
    fun mouseClickedUpdatesRenderedNavHostWhenTargetComesFromComposedButton() {
        val root = RootNode()
        val layoutState = ComposeRenderLayoutState()
        val runtime = ComposeGuiRuntime(onCompositionChanged = layoutState::invalidateComposition)
        val renderedTargets = mutableListOf<InputTarget>()
        val runtimeSync = ComposeRenderRuntimeSync(runtime)
        var clickCount = 0
        var lastRenderedDestinationLabel = ""
        val inputAdapter = ComposeGuiScreenInputAdapter(
            backDispatcher = ComposeBackDispatcher(),
            interactionState = ComposeGuiScreenInteractionState(MinecraftHostedWidgetRegistry()),
            renderedInputTargets = renderedTargets,
            runtimeSync = runtimeSync
        )

        try {
            runtime.start(root) {
                val backStack = rememberNavBackStack<TestDestination>(TestDestination.Overview)
                val navigator = rememberNavigator(backStack)
                lastRenderedDestinationLabel = navigator.currentKey?.label ?: ""

                Column {
                    Button(
                        text = "Open controls",
                        onClick = {
                            clickCount += 1
                            navigator.replaceTop(TestDestination.Controls)
                        }
                    )
                    NavHost(
                        backStack = backStack,
                        entryProvider = entryProvider {
                            entry<TestDestination.Overview> {
                                Text(text = TestDestination.Overview.label)
                            }
                            entry<TestDestination.Controls> {
                                Text(text = TestDestination.Controls.label)
                            }
                        }
                    )
                }
            }

            renderComposeTree(root, layoutState, renderedTargets)
            assertEquals("overview", ((root.children.single() as io.github.fopwoc.mods.framework.ui.compose.node.ColumnNode).children[1] as TextNode).text.plainText)

            val buttonBounds = renderedTargets
                .single { it.kind == InputTargetKind.BUTTON }
                .bounds

            inputAdapter.mouseClicked(
                mouseX = buttonBounds.x + 1,
                mouseY = buttonBounds.y + 1,
                mouseButton = 0
            ) {
                error("fallback should not be used when the composed button consumes the click")
            }

            assertEquals(1, clickCount)
            assertEquals("controls", lastRenderedDestinationLabel)

            renderComposeTree(root, layoutState, renderedTargets)
            assertEquals("controls", ((root.children.single() as io.github.fopwoc.mods.framework.ui.compose.node.ColumnNode).children[1] as TextNode).text.plainText)
        } finally {
            runtime.dispose()
        }
    }

    @Test
    fun mouseClickedUpdatesRenderedTextWhenComposedButtonMutatesRememberState() {
        val root = RootNode()
        val layoutState = ComposeRenderLayoutState()
        val runtime = ComposeGuiRuntime(onCompositionChanged = layoutState::invalidateComposition)
        val renderedTargets = mutableListOf<InputTarget>()
        val runtimeSync = ComposeRenderRuntimeSync(runtime)
        val inputAdapter = ComposeGuiScreenInputAdapter(
            backDispatcher = ComposeBackDispatcher(),
            interactionState = ComposeGuiScreenInteractionState(MinecraftHostedWidgetRegistry()),
            renderedInputTargets = renderedTargets,
            runtimeSync = runtimeSync
        )

        try {
            runtime.start(root) {
                var counter by remember { mutableIntStateOf(0) }

                Column {
                    Button(
                        text = "+1",
                        onClick = {
                            counter += 1
                        }
                    )
                    Text(text = "counter=$counter")
                }
            }

            renderComposeTree(root, layoutState, renderedTargets)
            assertEquals("counter=0", ((root.children.single() as io.github.fopwoc.mods.framework.ui.compose.node.ColumnNode).children[1] as TextNode).text.plainText)

            val buttonBounds = renderedTargets.single { it.kind == InputTargetKind.BUTTON }.bounds
            inputAdapter.mouseClicked(
                mouseX = buttonBounds.x + 1,
                mouseY = buttonBounds.y + 1,
                mouseButton = 0
            ) {
                error("fallback should not be used when the composed button consumes the click")
            }

            renderComposeTree(root, layoutState, renderedTargets)
            assertEquals("counter=1", ((root.children.single() as io.github.fopwoc.mods.framework.ui.compose.node.ColumnNode).children[1] as TextNode).text.plainText)
        } finally {
            runtime.dispose()
        }
    }

    @Test
    fun mouseClickedUpdatesRenderedTextWhenComposedButtonMutatesStateFlowCollectedByLaunchedEffect() {
        val root = RootNode()
        val layoutState = ComposeRenderLayoutState()
        val runtime = ComposeGuiRuntime(onCompositionChanged = layoutState::invalidateComposition)
        val renderedTargets = mutableListOf<InputTarget>()
        val runtimeSync = ComposeRenderRuntimeSync(runtime)
        val inputAdapter = ComposeGuiScreenInputAdapter(
            backDispatcher = ComposeBackDispatcher(),
            interactionState = ComposeGuiScreenInteractionState(MinecraftHostedWidgetRegistry()),
            renderedInputTargets = renderedTargets,
            runtimeSync = runtimeSync
        )
        val flow = MutableStateFlow(0)

        try {
            runtime.start(root) {
                var observedCounter by remember { mutableIntStateOf(flow.value) }

                LaunchedEffect(Unit) {
                    flow.collect { value ->
                        observedCounter = value
                    }
                }

                Column {
                    Button(
                        text = "+1",
                        onClick = {
                            flow.value = flow.value + 1
                        }
                    )
                    Text(text = "counter=$observedCounter")
                }
            }

            runtimeSync.updateScreen(frameTimeNanos = 0L)
            renderComposeTree(root, layoutState, renderedTargets)
            assertEquals("counter=0", ((root.children.single() as io.github.fopwoc.mods.framework.ui.compose.node.ColumnNode).children[1] as TextNode).text.plainText)

            val buttonBounds = renderedTargets.single { it.kind == InputTargetKind.BUTTON }.bounds
            inputAdapter.mouseClicked(
                mouseX = buttonBounds.x + 1,
                mouseY = buttonBounds.y + 1,
                mouseButton = 0
            ) {
                error("fallback should not be used when the composed button consumes the click")
            }

            renderComposeTree(root, layoutState, renderedTargets)
            assertEquals("counter=1", ((root.children.single() as io.github.fopwoc.mods.framework.ui.compose.node.ColumnNode).children[1] as TextNode).text.plainText)
        } finally {
            runtime.dispose()
        }
    }

    @Test
    fun mouseClickedUpdatesRenderedTextWhenComposedButtonUsesViewModelLifecycleCollectionAndLaunchedEffect() {
        val root = RootNode()
        val layoutState = ComposeRenderLayoutState()
        val runtime = ComposeGuiRuntime(onCompositionChanged = layoutState::invalidateComposition)
        val renderedTargets = mutableListOf<InputTarget>()
        val runtimeSync = ComposeRenderRuntimeSync(runtime)
        val inputAdapter = ComposeGuiScreenInputAdapter(
            backDispatcher = ComposeBackDispatcher(),
            interactionState = ComposeGuiScreenInteractionState(MinecraftHostedWidgetRegistry()),
            renderedInputTargets = renderedTargets,
            runtimeSync = runtimeSync
        )
        val owner = ComposeViewModelOwner()
        var observedLocalCallbackCount = -1
        var observedLifecycleCounter = -1
        var observedPlainCounter = -1

        try {
            owner.onCreate()
            runtime.start(root) {
                CompositionLocalProvider(
                    LocalLifecycleOwner provides owner,
                    LocalViewModelStoreOwner provides owner
                ) {
                    val viewModel: TestProbeViewModel = viewModel(TestProbeViewModel::class)
                    val lifecycleCounter by viewModel.stateFlow.collectAsStateWithLifecycle()
                    var plainObservedCounter by remember(viewModel) {
                        mutableIntStateOf(-1)
                    }
                    var localCallbackCount by remember { mutableIntStateOf(0) }

                    LaunchedEffect(viewModel) {
                        viewModel.stateFlow.collect { observed ->
                            plainObservedCounter = observed
                            observedPlainCounter = observed
                        }
                    }

                    LaunchedEffect(localCallbackCount) {
                        observedLocalCallbackCount = localCallbackCount
                    }
                    LaunchedEffect(lifecycleCounter) {
                        observedLifecycleCounter = lifecycleCounter
                    }

                    Column {
                        Button(
                            text = "+1",
                            onClick = {
                                localCallbackCount += 1
                                viewModel.increment()
                            }
                        )
                        Text(text = "local=$localCallbackCount lifecycle=$lifecycleCounter plain=$plainObservedCounter")
                    }
                }
            }
            owner.onStart()
            owner.onResume()

            runtimeSync.updateScreen(frameTimeNanos = 0L)
            runtimeSync.syncBeforeRender()
            renderComposeTree(root, layoutState, renderedTargets)
            assertEquals(
                "local=0 lifecycle=0 plain=0",
                ((root.children.single() as io.github.fopwoc.mods.framework.ui.compose.node.ColumnNode).children[1] as TextNode).text.plainText
            )
            assertEquals(0, observedLocalCallbackCount)
            assertEquals(0, observedLifecycleCounter)
            assertEquals(0, observedPlainCounter)

            val buttonBounds = renderedTargets.single { it.kind == InputTargetKind.BUTTON }.bounds
            inputAdapter.mouseClicked(
                mouseX = buttonBounds.x + 1,
                mouseY = buttonBounds.y + 1,
                mouseButton = 0
            ) {
                error("fallback should not be used when the composed button consumes the click")
            }

            runtimeSync.syncBeforeRender()
            renderComposeTree(root, layoutState, renderedTargets)
            assertEquals(
                "local=1 lifecycle=1 plain=1",
                ((root.children.single() as io.github.fopwoc.mods.framework.ui.compose.node.ColumnNode).children[1] as TextNode).text.plainText
            )
            assertEquals(1, observedLocalCallbackCount)
            assertEquals(1, observedLifecycleCounter)
            assertEquals(1, observedPlainCounter)
        } finally {
            owner.clear()
            runtime.dispose()
        }
    }

    @Test
    fun mouseClickedUpdatesRenderedTextWhenComposedButtonUsesFrameworkLifecycleCollectionAndLaunchedEffect() {
        val root = RootNode()
        val layoutState = ComposeRenderLayoutState()
        val runtime = ComposeGuiRuntime(onCompositionChanged = layoutState::invalidateComposition)
        val renderedTargets = mutableListOf<InputTarget>()
        val runtimeSync = ComposeRenderRuntimeSync(runtime)
        val inputAdapter = ComposeGuiScreenInputAdapter(
            backDispatcher = ComposeBackDispatcher(),
            interactionState = ComposeGuiScreenInteractionState(MinecraftHostedWidgetRegistry()),
            renderedInputTargets = renderedTargets,
            runtimeSync = runtimeSync
        )
        val owner = ComposeViewModelOwner()
        var observedLocalCallbackCount = -1
        var observedLifecycleCounter = -1
        var observedPlainCounter = -1

        try {
            owner.onCreate()
            runtime.start(root) {
                CompositionLocalProvider(
                    LocalLifecycleOwner provides owner,
                    LocalViewModelStoreOwner provides owner
                ) {
                    val viewModel: TestProbeViewModel = viewModel(TestProbeViewModel::class)
                    val lifecycleCounter by viewModel.stateFlow.collectAsStateWithFrameworkLifecycle()
                    var plainObservedCounter by remember(viewModel) {
                        mutableIntStateOf(-1)
                    }
                    var localCallbackCount by remember { mutableIntStateOf(0) }

                    LaunchedEffect(viewModel) {
                        viewModel.stateFlow.collect { observed ->
                            plainObservedCounter = observed
                            observedPlainCounter = observed
                        }
                    }

                    LaunchedEffect(localCallbackCount) {
                        observedLocalCallbackCount = localCallbackCount
                    }
                    LaunchedEffect(lifecycleCounter) {
                        observedLifecycleCounter = lifecycleCounter
                    }

                    Column {
                        Button(
                            text = "+1",
                            onClick = {
                                localCallbackCount += 1
                                viewModel.increment()
                            }
                        )
                        Text(text = "local=$localCallbackCount lifecycle=$lifecycleCounter plain=$plainObservedCounter")
                    }
                }
            }
            owner.onStart()
            owner.onResume()

            runtimeSync.updateScreen(frameTimeNanos = 0L)
            runtimeSync.syncBeforeRender()
            renderComposeTree(root, layoutState, renderedTargets)
            assertEquals(
                "local=0 lifecycle=0 plain=0",
                ((root.children.single() as io.github.fopwoc.mods.framework.ui.compose.node.ColumnNode).children[1] as TextNode).text.plainText
            )
            assertEquals(0, observedLocalCallbackCount)
            assertEquals(0, observedLifecycleCounter)
            assertEquals(0, observedPlainCounter)

            val buttonBounds = renderedTargets.single { it.kind == InputTargetKind.BUTTON }.bounds
            inputAdapter.mouseClicked(
                mouseX = buttonBounds.x + 1,
                mouseY = buttonBounds.y + 1,
                mouseButton = 0
            ) {
                error("fallback should not be used when the composed button consumes the click")
            }

            runtimeSync.syncBeforeRender()
            renderComposeTree(root, layoutState, renderedTargets)
            assertEquals(
                "local=1 lifecycle=1 plain=1",
                ((root.children.single() as io.github.fopwoc.mods.framework.ui.compose.node.ColumnNode).children[1] as TextNode).text.plainText
            )
            assertEquals(1, observedLocalCallbackCount)
            assertEquals(1, observedLifecycleCounter)
            assertEquals(1, observedPlainCounter)
        } finally {
            owner.clear()
            runtime.dispose()
        }
    }

    private sealed interface TestDestination : NavKey {
        val label: String

        data object Overview : TestDestination {
            override val label: String = "overview"
        }

        data object Controls : TestDestination {
            override val label: String = "controls"
        }
    }

    private fun renderComposeTree(
        root: RootNode,
        layoutState: ComposeRenderLayoutState,
        renderedTargets: MutableList<InputTarget>,
        width: Int = 320,
        height: Int = 180
    ) {
        renderedTargets.clear()
        val renderContext = RecordingRenderContext(
            viewportWidth = width,
            viewportHeight = height,
            appendInputTarget = renderedTargets::add
        )
        layoutState.ensureLayout(root, renderContext, width, height)
            .draw(renderContext, InputOnlyHostedElementRenderer(renderedTargets::add))
    }

    private class InputOnlyHostedElementRenderer(
        private val registerInputTarget: (InputTarget) -> Unit
    ) : HostedElementRenderer {
        override fun drawButton(bounds: Rect, element: LayoutElement.Button) {
            registerInputTarget(
                InputTarget(
                    kind = InputTargetKind.BUTTON,
                    bounds = bounds,
                    onPress = { mouseX, mouseY, button ->
                        if (element.enabled && button == 0 && bounds.contains(mouseX, mouseY)) {
                            element.onClick()
                            InputPressResult.Consumed
                        } else {
                            InputPressResult.Ignored
                        }
                    }
                )
            )
        }

        override fun drawCheckbox(bounds: Rect, element: LayoutElement.Checkbox) = Unit

        override fun drawTextField(bounds: Rect, element: LayoutElement.TextField) = Unit

        override fun drawSlider(bounds: Rect, element: LayoutElement.Slider) = Unit

        override fun drawSelectableList(bounds: Rect, element: LayoutElement.SelectableList) = Unit
    }

    private class RecordingRenderContext(
        override val viewportWidth: Int,
        override val viewportHeight: Int,
        private val appendInputTarget: (InputTarget) -> Unit
    ) : RenderContext {
        override val mouseX: Int = 0
        override val mouseY: Int = 0
        override val lineHeight: Int = 9

        private var activeClipRect: Rect? = null

        override fun textWidth(text: String): Int = text.length * 6

        override fun wrapText(text: String, maxWidth: Int): List<String> {
            if (maxWidth <= 0) {
                return emptyList()
            }
            if (textWidth(text) <= maxWidth) {
                return listOf(text)
            }
            val maxChars = (maxWidth / 6).coerceAtLeast(1)
            return text.chunked(maxChars)
        }

        override fun fillRect(left: Int, top: Int, right: Int, bottom: Int, color: Color) = Unit

        override fun drawHorizontalLine(startX: Int, endX: Int, y: Int, color: Color) = Unit

        override fun drawVerticalLine(x: Int, startY: Int, endY: Int, color: Color) = Unit

        override fun drawText(text: String, x: Int, y: Int, color: Color, shadow: Boolean) = Unit

        override fun registerInputTarget(target: InputTarget) {
            val combinedClipRect = when {
                activeClipRect == null -> target.clipRect
                target.clipRect == null -> activeClipRect
                else -> activeClipRect!!.intersect(target.clipRect)
            }
            appendInputTarget(target.copy(clipRect = combinedClipRect))
        }

        override fun withClipRect(rect: Rect, block: () -> Unit) {
            val previousClipRect = activeClipRect
            activeClipRect = previousClipRect?.intersect(rect) ?: rect
            try {
                block()
            } finally {
                activeClipRect = previousClipRect
            }
        }
    }

    class TestProbeViewModel : ViewModel() {
        val stateFlow = MutableStateFlow(0)

        fun increment() {
            stateFlow.update { it + 1 }
        }
    }
}

