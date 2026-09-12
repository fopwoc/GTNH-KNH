package io.github.fopwoc.mods.testgui.client.gui.ui.page.gallery

import io.github.fopwoc.mods.testgui.client.gui.ui.story.ButtonStory
import io.github.fopwoc.mods.testgui.client.gui.ui.story.CheckboxStory
import io.github.fopwoc.mods.testgui.client.gui.ui.story.DenseWidgetsStory
import io.github.fopwoc.mods.testgui.client.gui.ui.story.HudStory
import io.github.fopwoc.mods.testgui.client.gui.ui.story.LayoutStory
import io.github.fopwoc.mods.testgui.client.gui.ui.story.LazyColumnStory
import io.github.fopwoc.mods.testgui.client.gui.ui.story.ModifiersStory
import io.github.fopwoc.mods.testgui.client.gui.ui.story.NavigationStory
import io.github.fopwoc.mods.testgui.client.gui.ui.story.ScrollClipStory
import io.github.fopwoc.mods.testgui.client.gui.ui.story.ScrollStory
import io.github.fopwoc.mods.testgui.client.gui.ui.story.SegmentedControlStory
import io.github.fopwoc.mods.testgui.client.gui.ui.story.SelectableListStory
import io.github.fopwoc.mods.testgui.client.gui.ui.story.SliderStory
import io.github.fopwoc.mods.testgui.client.gui.ui.story.StateStory
import io.github.fopwoc.mods.testgui.client.gui.ui.story.SurfacesStory
import io.github.fopwoc.mods.testgui.client.gui.ui.story.TabsStory
import io.github.fopwoc.mods.testgui.client.gui.ui.story.TextFieldStory
import io.github.fopwoc.mods.testgui.client.gui.ui.story.TextStory
import io.github.fopwoc.mods.testgui.client.gui.ui.story.ThemeStory
import io.github.fopwoc.mods.testgui.client.gui.ui.story.ToggleButtonStory

/** Sidebar order. Add a story here and it shows up. */
val storyCatalog: List<Story> =
    listOf(
        Story("Button") { ButtonStory() },
        Story("Checkbox") { CheckboxStory() },
        Story("Slider") { SliderStory() },
        Story("TextField") { TextFieldStory() },
        Story("SelectableList") { SelectableListStory() },
        Story("LazyColumn") { LazyColumnStory() },
        Story("Scroll") { ScrollStory() },
        Story("Tabs") { TabsStory() },
        Story("SegmentedControl") { SegmentedControlStory() },
        Story("ToggleButton") { ToggleButtonStory() },
        Story("Surfaces") { SurfacesStory() },
        Story("Text & tooltips") { TextStory() },
        Story("Theme") { ThemeStory() },
        Story("Layout") { LayoutStory() },
        Story("Modifiers") { ModifiersStory() },
        Story("State & ViewModel") { StateStory() },
        Story("Navigation") { NavigationStory() },
        Story("HUD overlay") { HudStory() },
        Story("Stress: dense widgets") { DenseWidgetsStory() },
        Story("Stress: scroll & clip") { ScrollClipStory() },
    )
