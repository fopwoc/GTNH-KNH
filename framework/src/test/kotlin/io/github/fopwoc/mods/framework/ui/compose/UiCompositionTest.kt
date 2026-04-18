package io.github.fopwoc.mods.framework.ui.compose

import androidx.compose.runtime.Composition
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import io.github.fopwoc.mods.framework.ui.compose.component.Panel
import io.github.fopwoc.mods.framework.ui.compose.component.native.Checkbox
import io.github.fopwoc.mods.framework.ui.compose.component.native.SelectableList
import io.github.fopwoc.mods.framework.ui.compose.component.native.Slider
import io.github.fopwoc.mods.framework.ui.compose.component.native.TextField
import io.github.fopwoc.mods.framework.ui.compose.foundation.Box
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.node.NodeApplier
import io.github.fopwoc.mods.framework.ui.compose.node.BoxNode
import io.github.fopwoc.mods.framework.ui.compose.node.CheckboxNode
import io.github.fopwoc.mods.framework.ui.compose.node.RootNode
import io.github.fopwoc.mods.framework.ui.compose.node.SliderNode
import io.github.fopwoc.mods.framework.ui.compose.node.SelectableListNode
import io.github.fopwoc.mods.framework.ui.compose.node.TextNode
import io.github.fopwoc.mods.framework.ui.compose.node.TextFieldNode
import io.github.fopwoc.mods.framework.ui.compose.state.TextFieldState
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UiCompositionTest {
    @Test
    fun composeTreeRebuildsWhenSnapshotStateChanges() = runBlocking {
        val root = RootNode()
        val state = mutableStateOf("First")
        val frameClock = BroadcastFrameClock()
        val recomposerContext = Dispatchers.Unconfined + frameClock
        val recomposer = Recomposer(recomposerContext)
        val composition = Composition(NodeApplier(root), recomposer)
        val recomposeJob = launch(recomposerContext, start = CoroutineStart.UNDISPATCHED) {
            recomposer.runRecomposeAndApplyChanges()
        }

        try {
            composition.setContent {
                Text(text = state.value)
            }
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(0L)
            recomposer.awaitIdle()

            val firstNode = assertIs<TextNode>(root.children.single())
            assertEquals("First", firstNode.text)

            state.value = "Second"
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(16L)
            recomposer.awaitIdle()

            val secondNode = assertIs<TextNode>(root.children.single())
            assertEquals("Second", secondNode.text)
        } finally {
            composition.dispose()
            recomposer.cancel()
            recomposeJob.cancelAndJoin()
        }
    }

    @Test
    fun textFieldComposableCreatesHostedNodeWithState() = runBlocking {
        val root = RootNode()
        val frameClock = BroadcastFrameClock()
        val recomposerContext = Dispatchers.Unconfined + frameClock
        val recomposer = Recomposer(recomposerContext)
        val composition = Composition(NodeApplier(root), recomposer)
        val recomposeJob = launch(recomposerContext, start = CoroutineStart.UNDISPATCHED) {
            recomposer.runRecomposeAndApplyChanges()
        }

        try {
            val fieldState = TextFieldState("hello")
            composition.setContent {
                TextField(
                    state = fieldState,
                    placeholder = "name"
                )
            }
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(0L)
            recomposer.awaitIdle()

            val textFieldNode = assertIs<TextFieldNode>(root.children.single())
            assertEquals("hello", textFieldNode.state.text)
            assertEquals("name", textFieldNode.placeholder)
        } finally {
            composition.dispose()
            recomposer.cancel()
            recomposeJob.cancelAndJoin()
        }
    }

    @Test
    fun panelContentContainerDoesNotForceFillMaxSize() = runBlocking {
        val root = RootNode()
        val frameClock = BroadcastFrameClock()
        val recomposerContext = Dispatchers.Unconfined + frameClock
        val recomposer = Recomposer(recomposerContext)
        val composition = Composition(NodeApplier(root), recomposer)
        val recomposeJob = launch(recomposerContext, start = CoroutineStart.UNDISPATCHED) {
            recomposer.runRecomposeAndApplyChanges()
        }

        try {
            composition.setContent {
                Panel {
                    Text(text = "Body")
                }
            }
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(0L)
            recomposer.awaitIdle()

            val outerBox = assertIs<BoxNode>(root.children.single())
            val innerBox = assertIs<BoxNode>(outerBox.children.single())
            assertEquals(false, innerBox.modifier.fillMaxWidth)
            assertEquals(false, innerBox.modifier.fillMaxHeight)
        } finally {
            composition.dispose()
            recomposer.cancel()
            recomposeJob.cancelAndJoin()
        }
    }

    @Test
    fun boxComposableKeepsMatchParentSizeModifierFlag() = runBlocking {
        val root = RootNode()
        val frameClock = BroadcastFrameClock()
        val recomposerContext = Dispatchers.Unconfined + frameClock
        val recomposer = Recomposer(recomposerContext)
        val composition = Composition(NodeApplier(root), recomposer)
        val recomposeJob = launch(recomposerContext, start = CoroutineStart.UNDISPATCHED) {
            recomposer.runRecomposeAndApplyChanges()
        }

        try {
            composition.setContent {
                Box(modifier = Modifier().matchParentSize())
            }
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(0L)
            recomposer.awaitIdle()

            val boxNode = assertIs<BoxNode>(root.children.single())
            assertEquals(true, boxNode.modifier.matchParentSize)
        } finally {
            composition.dispose()
            recomposer.cancel()
            recomposeJob.cancelAndJoin()
        }
    }

    @Test
    fun checkboxComposableCreatesNativeCheckboxNode() = runBlocking {
        val root = RootNode()
        val frameClock = BroadcastFrameClock()
        val recomposerContext = Dispatchers.Unconfined + frameClock
        val recomposer = Recomposer(recomposerContext)
        val composition = Composition(NodeApplier(root), recomposer)
        val recomposeJob = launch(recomposerContext, start = CoroutineStart.UNDISPATCHED) {
            recomposer.runRecomposeAndApplyChanges()
        }

        try {
            composition.setContent {
                Checkbox(label = "Enabled", checked = true, onCheckedChange = {})
            }
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(0L)
            recomposer.awaitIdle()

            val checkboxNode = assertIs<CheckboxNode>(root.children.single())
            assertEquals("Enabled", checkboxNode.label)
            assertEquals(true, checkboxNode.checked)
        } finally {
            composition.dispose()
            recomposer.cancel()
            recomposeJob.cancelAndJoin()
        }
    }

    @Test
    fun sliderComposableCreatesHostedSliderNode() = runBlocking {
        val root = RootNode()
        val frameClock = BroadcastFrameClock()
        val recomposerContext = Dispatchers.Unconfined + frameClock
        val recomposer = Recomposer(recomposerContext)
        val composition = Composition(NodeApplier(root), recomposer)
        val recomposeJob = launch(recomposerContext, start = CoroutineStart.UNDISPATCHED) {
            recomposer.runRecomposeAndApplyChanges()
        }

        try {
            composition.setContent {
                Slider(
                    value = 32.0,
                    onValueChange = {},
                    label = "Power",
                    valueRange = 0.0..100.0,
                    showDecimal = false
                )
            }
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(0L)
            recomposer.awaitIdle()

            val sliderNode = assertIs<SliderNode>(root.children.single())
            assertEquals(32.0, sliderNode.value)
            assertEquals("Power", sliderNode.label)
            assertEquals(false, sliderNode.showDecimal)
        } finally {
            composition.dispose()
            recomposer.cancel()
            recomposeJob.cancelAndJoin()
        }
    }

    @Test
    fun selectableListComposableCreatesHostedSelectableListNode() = runBlocking {
        val root = RootNode()
        val selectedIndex = mutableIntStateOf(1)
        val frameClock = BroadcastFrameClock()
        val recomposerContext = Dispatchers.Unconfined + frameClock
        val recomposer = Recomposer(recomposerContext)
        val composition = Composition(NodeApplier(root), recomposer)
        val recomposeJob = launch(recomposerContext, start = CoroutineStart.UNDISPATCHED) {
            recomposer.runRecomposeAndApplyChanges()
        }

        try {
            composition.setContent {
                SelectableList(
                    items = listOf("Alpha", "Beta", "Gamma"),
                    selectedIndex = selectedIndex.intValue,
                    visibleRowCount = 4,
                    onSelectedIndexChange = { selectedIndex.intValue = it }
                )
            }
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(0L)
            recomposer.awaitIdle()

            val selectableListNode = assertIs<SelectableListNode>(root.children.single())
            assertEquals(listOf("Alpha", "Beta", "Gamma"), selectableListNode.items)
            assertEquals(1, selectableListNode.selectedIndex)
            assertEquals(4, selectableListNode.visibleRowCount)
        } finally {
            composition.dispose()
            recomposer.cancel()
            recomposeJob.cancelAndJoin()
        }
    }
}
