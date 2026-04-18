package io.github.fopwoc.mods.framework.ui.compose

import androidx.compose.runtime.Composition
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import io.github.fopwoc.mods.framework.ui.compose.component.Panel
import io.github.fopwoc.mods.framework.ui.compose.component.native.Button
import io.github.fopwoc.mods.framework.ui.compose.component.native.Checkbox
import io.github.fopwoc.mods.framework.ui.compose.component.native.SelectableList
import io.github.fopwoc.mods.framework.ui.compose.component.native.Slider
import io.github.fopwoc.mods.framework.ui.compose.component.native.TextField
import io.github.fopwoc.mods.framework.ui.compose.foundation.Box
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.columnFill
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.columnAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.columnWeight
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.boxAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.boxMatchesParentHeight
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.boxMatchesParentSize
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.boxMatchesParentWidth
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.rowFill
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.rowAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.rowWeight
import io.github.fopwoc.mods.framework.ui.compose.node.NodeApplier
import io.github.fopwoc.mods.framework.ui.compose.node.BoxNode
import io.github.fopwoc.mods.framework.ui.compose.node.CheckboxNode
import io.github.fopwoc.mods.framework.ui.compose.node.ColumnNode
import io.github.fopwoc.mods.framework.ui.compose.node.RootNode
import io.github.fopwoc.mods.framework.ui.compose.node.RowNode
import io.github.fopwoc.mods.framework.ui.compose.node.SliderNode
import io.github.fopwoc.mods.framework.ui.compose.node.SelectableListNode
import io.github.fopwoc.mods.framework.ui.compose.node.TextNode
import io.github.fopwoc.mods.framework.ui.compose.node.TextFieldNode
import io.github.fopwoc.mods.framework.ui.compose.state.TextFieldState
import io.github.fopwoc.mods.framework.ui.compose.text.MinecraftColor
import io.github.fopwoc.mods.framework.ui.compose.text.styledText
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
            assertEquals("First", firstNode.text.plainText)

            state.value = "Second"
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(16L)
            recomposer.awaitIdle()

            val secondNode = assertIs<TextNode>(root.children.single())
            assertEquals("Second", secondNode.text.plainText)
        } finally {
            composition.dispose()
            recomposer.cancel()
            recomposeJob.cancelAndJoin()
        }
    }

    @Test
    fun styledTextComposableCreatesTextNodeWithFormattedContent() = runBlocking {
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
                Text(
                    text = styledText {
                        append("Name: ")
                        withColor(MinecraftColor.Gold) {
                            append("Luna")
                        }
                    }
                )
            }
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(0L)
            recomposer.awaitIdle()

            val textNode = assertIs<TextNode>(root.children.single())
            assertEquals("Name: Luna", textNode.text.plainText)
            assertEquals("Name: §6Luna", textNode.text.formattedString)
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
    fun boxComposableAppliesBoxScopeChildModifiersToChildren() = runBlocking {
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
                Box {
                    Text(
                        text = "Overlay",
                        modifier = Modifier()
                            .align(Alignment.BottomEnd)
                            .matchParentSize()
                    )
                }
            }
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(0L)
            recomposer.awaitIdle()

            val boxNode = assertIs<BoxNode>(root.children.single())
            val textNode = assertIs<TextNode>(boxNode.children.single())
            assertEquals(Alignment.BottomEnd, textNode.modifier.boxAlignment)
            assertEquals(true, textNode.modifier.boxMatchesParentSize)
        } finally {
            composition.dispose()
            recomposer.cancel()
            recomposeJob.cancelAndJoin()
        }
    }

    @Test
    fun boxComposableSupportsAxisSpecificMatchParentModifiers() = runBlocking {
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
                Box {
                    Text(
                        text = "Wide",
                        modifier = Modifier().matchParentWidth()
                    )
                    Text(
                        text = "Tall",
                        modifier = Modifier().matchParentHeight()
                    )
                }
            }
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(0L)
            recomposer.awaitIdle()

            val boxNode = assertIs<BoxNode>(root.children.single())
            val firstTextNode = assertIs<TextNode>(boxNode.children[0])
            val secondTextNode = assertIs<TextNode>(boxNode.children[1])

            assertEquals(true, firstTextNode.modifier.boxMatchesParentWidth)
            assertEquals(false, firstTextNode.modifier.boxMatchesParentHeight)
            assertEquals(false, firstTextNode.modifier.boxMatchesParentSize)
            assertEquals(false, secondTextNode.modifier.boxMatchesParentWidth)
            assertEquals(true, secondTextNode.modifier.boxMatchesParentHeight)
            assertEquals(false, secondTextNode.modifier.boxMatchesParentSize)
        } finally {
            composition.dispose()
            recomposer.cancel()
            recomposeJob.cancelAndJoin()
        }
    }

    @Test
    fun rowComposableAppliesRowScopeChildModifiersToChildren() = runBlocking {
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
                Row {
                    Text(
                        text = "Bottom",
                        modifier = Modifier().align(VerticalAlignment.BOTTOM)
                    )
                }
            }
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(0L)
            recomposer.awaitIdle()

            val rowNode = assertIs<RowNode>(root.children.single())
            val textNode = assertIs<TextNode>(rowNode.children.single())
            assertEquals(VerticalAlignment.BOTTOM, textNode.modifier.rowAlignment)
            assertEquals(null, textNode.modifier.rowWeight)
        } finally {
            composition.dispose()
            recomposer.cancel()
            recomposeJob.cancelAndJoin()
        }
    }

    @Test
    fun columnComposableAppliesColumnScopeChildModifiersToChildren() = runBlocking {
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
                Column {
                    Text(
                        text = "End",
                        modifier = Modifier().align(HorizontalAlignment.END)
                    )
                }
            }
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(0L)
            recomposer.awaitIdle()

            val columnNode = assertIs<ColumnNode>(root.children.single())
            val textNode = assertIs<TextNode>(columnNode.children.single())
            assertEquals(HorizontalAlignment.END, textNode.modifier.columnAlignment)
            assertEquals(null, textNode.modifier.columnWeight)
        } finally {
            composition.dispose()
            recomposer.cancel()
            recomposeJob.cancelAndJoin()
        }
    }

    @Test
    fun rowComposableSupportsWeightedChildren() = runBlocking {
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
                Row {
                    Text(
                        text = "Weighted",
                        modifier = Modifier().weight(2f, fill = false)
                    )
                }
            }
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(0L)
            recomposer.awaitIdle()

            val rowNode = assertIs<RowNode>(root.children.single())
            val textNode = assertIs<TextNode>(rowNode.children.single())
            assertEquals(2f, textNode.modifier.rowWeight)
            assertEquals(false, textNode.modifier.rowFill)
        } finally {
            composition.dispose()
            recomposer.cancel()
            recomposeJob.cancelAndJoin()
        }
    }

    @Test
    fun columnComposableSupportsWeightedChildren() = runBlocking {
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
                Column {
                    Text(
                        text = "Weighted",
                        modifier = Modifier().weight(3f)
                    )
                }
            }
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(0L)
            recomposer.awaitIdle()

            val columnNode = assertIs<ColumnNode>(root.children.single())
            val textNode = assertIs<TextNode>(columnNode.children.single())
            assertEquals(3f, textNode.modifier.columnWeight)
            assertEquals(true, textNode.modifier.columnFill)
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
            assertEquals("Enabled", checkboxNode.label.plainText)
            assertEquals(true, checkboxNode.checked)
        } finally {
            composition.dispose()
            recomposer.cancel()
            recomposeJob.cancelAndJoin()
        }
    }

    @Test
    fun buttonComposableSupportsStyledTextLabel() = runBlocking {
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
                Button(
                    text = styledText {
                        append("Open ")
                        withColor(MinecraftColor.Gold) {
                            append("Menu")
                        }
                    },
                    onClick = {}
                )
            }
            Snapshot.sendApplyNotifications()
            frameClock.sendFrame(0L)
            recomposer.awaitIdle()

            val buttonNode = assertIs<io.github.fopwoc.mods.framework.ui.compose.node.ButtonNode>(root.children.single())
            assertEquals("Open Menu", buttonNode.text.plainText)
            assertEquals("Open §6Menu", buttonNode.text.formattedString)
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
