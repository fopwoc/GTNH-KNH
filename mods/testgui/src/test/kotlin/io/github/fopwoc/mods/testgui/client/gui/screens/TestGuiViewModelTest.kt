package io.github.fopwoc.mods.testgui.client.gui.screens

import io.github.fopwoc.mods.testgui.client.gui.ui.testGuiFeatureCatalog
import io.github.fopwoc.mods.testgui.client.gui.ui.screens.controls.ControlsTab
import io.github.fopwoc.mods.testgui.client.gui.ui.screens.controls.ControlsViewModel
import io.github.fopwoc.mods.testgui.client.gui.ui.screens.controls.PowerPreset
import io.github.fopwoc.mods.testgui.client.gui.ui.screens.hostedstress.HostedStressViewModel
import io.github.fopwoc.mods.testgui.client.gui.ui.screens.inputs.InputsViewModel
import io.github.fopwoc.mods.testgui.client.gui.ui.screens.layout.BoxAlignmentPreset
import io.github.fopwoc.mods.testgui.client.gui.ui.screens.layout.LayoutTab
import io.github.fopwoc.mods.testgui.client.gui.ui.screens.layout.LayoutViewModel
import io.github.fopwoc.mods.testgui.client.gui.ui.screens.navigation.NavigationViewModel
import io.github.fopwoc.mods.testgui.client.gui.ui.screens.overview.OverviewViewModel
import io.github.fopwoc.mods.testgui.client.gui.ui.screens.scrollclipstress.ScrollClipStressViewModel
import io.github.fopwoc.mods.testgui.client.gui.ui.screens.state.StateMode
import io.github.fopwoc.mods.testgui.client.gui.ui.screens.state.StateViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestGuiViewModelTest {
    @Test
    fun overviewViewModelTracksOpenedFeature() {
        val viewModel = OverviewViewModel()

        viewModel.onFeatureOpened(testGuiFeatureCatalog.first())

        assertEquals(1, viewModel.stateFlow.value.openCount)
        assertEquals(testGuiFeatureCatalog.first().title, viewModel.stateFlow.value.lastOpenedTitle)
    }

    @Test
    fun controlsViewModelUpdatesSelectionAndPower() {
        val viewModel = ControlsViewModel()

        viewModel.selectTab(ControlsTab.Selection)
        viewModel.setPreset(PowerPreset.Overclock)
        viewModel.setPowerLevel(91.0)
        viewModel.toggleAutomation(true)

        val state = viewModel.stateFlow.value
        assertEquals(ControlsTab.Selection, state.activeTab)
        assertEquals(PowerPreset.Overclock, state.preset)
        assertEquals(91.0, state.powerLevel)
        assertTrue(state.automationEnabled)
    }

    @Test
    fun inputsViewModelLoadsSelectedItemIntoField() {
        val viewModel = InputsViewModel()

        viewModel.selectIndex(3)
        viewModel.loadSelectionIntoField()
        viewModel.commitDraft()

        val state = viewModel.stateFlow.value
        assertEquals("Delta controls", state.fieldState.text)
        assertEquals("Delta controls", state.lastCommittedText)
        assertEquals(1, state.commitCount)
    }

    @Test
    fun layoutViewModelTracksTabAlignmentAndChipCount() {
        val viewModel = LayoutViewModel()

        viewModel.selectTab(LayoutTab.Scroll)
        viewModel.setAlignmentPreset(BoxAlignmentPreset.BottomEnd)
        viewModel.addScrollChip()
        viewModel.removeScrollChip()

        val state = viewModel.stateFlow.value
        assertEquals(LayoutTab.Scroll, state.activeTab)
        assertEquals(BoxAlignmentPreset.BottomEnd, state.alignmentPreset)
        assertEquals(10, state.scrollChipCount)
    }

    @Test
    fun stateViewModelRetainsTokenAndPrependsEvents() {
        val first = StateViewModel()
        val second = StateViewModel()

        first.increment()
        first.setMode(StateMode.Replay)
        first.recordCoverNavigation()

        assertEquals(1, first.stateFlow.value.counter)
        assertEquals(StateMode.Replay, first.stateFlow.value.mode)
        assertTrue(first.stateFlow.value.eventLog.first().contains("Opened another top-level destination"))
        assertTrue(first.stateFlow.value.viewModelToken != second.stateFlow.value.viewModelToken)
    }

    @Test
    fun navigationViewModelTracksOuterAndInnerEvents() {
        val viewModel = NavigationViewModel()

        viewModel.recordSelfPush()
        viewModel.recordInnerEvent("Inner push: detail A")

        assertEquals(1, viewModel.stateFlow.value.selfPushes)
        assertEquals("Inner push: detail A", viewModel.stateFlow.value.innerEvents.first())
        assertTrue(viewModel.stateFlow.value.outerEvents.first().contains("Pushed another Navigation entry"))
    }

    @Test
    fun hostedStressViewModelCyclesFocusAndCommitsSnapshot() {
        val viewModel = HostedStressViewModel()

        viewModel.selectIndex(4)
        viewModel.loadSelectionIntoFocusedField()
        viewModel.cycleFocus()
        viewModel.commitSnapshot()

        val state = viewModel.stateFlow.value
        assertEquals(1, state.focusedIndex)
        assertEquals("Overflow epsilon", state.fields.first().text)
        assertEquals(1, state.commits)
        assertTrue(state.lastSnapshot.contains("Overflow epsilon"))
    }

    @Test
    fun scrollClipStressViewModelAdjustsDensityAndOffsetBounds() {
        val viewModel = ScrollClipStressViewModel()

        repeat(20) { viewModel.increaseBadgeOffset() }
        repeat(20) { viewModel.removeLane() }
        viewModel.toggleCompactMode(true)
        viewModel.toggleAlternatingBadges(false)

        val state = viewModel.stateFlow.value
        assertEquals(36, state.badgeOffset)
        assertEquals(4, state.laneCount)
        assertTrue(state.compactMode)
        assertTrue(!state.alternatingBadges)
    }
}

