package io.github.fopwoc.mods.testgui.client.gui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.ui.compose.component.Panel
import io.github.fopwoc.mods.framework.ui.compose.component.SegmentedControl
import io.github.fopwoc.mods.framework.ui.compose.component.Tabs
import io.github.fopwoc.mods.framework.ui.compose.component.ToggleButton
import io.github.fopwoc.mods.framework.ui.compose.component.native.Button
import io.github.fopwoc.mods.framework.ui.compose.component.native.Checkbox
import io.github.fopwoc.mods.framework.ui.compose.component.native.SelectableList
import io.github.fopwoc.mods.framework.ui.compose.component.native.Slider
import io.github.fopwoc.mods.framework.ui.compose.component.native.TextField
import io.github.fopwoc.mods.framework.ui.compose.foundation.Box
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
import io.github.fopwoc.mods.framework.ui.compose.foundation.Spacer
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextStyle
import io.github.fopwoc.mods.framework.ui.compose.minecraft.ComposeBackgroundStyle
import io.github.fopwoc.mods.framework.ui.compose.minecraft.ComposeGuiScreen
import io.github.fopwoc.mods.framework.ui.compose.runtime.LocalComposeGuiScreen
import io.github.fopwoc.mods.framework.ui.compose.runtime.rememberScrollState
import io.github.fopwoc.mods.framework.ui.compose.runtime.rememberTextFieldState
import io.github.fopwoc.mods.framework.ui.compose.text.MinecraftColor
import io.github.fopwoc.mods.framework.ui.compose.text.styledText
import io.github.fopwoc.mods.framework.ui.compose.unit.uu

private enum class DemoTab {
    WELCOME,
    HOSTED,
    FORMS,
    GALLERY,
    LISTS,
    NAVIGATION
}

private data class StoryCard(
    val title: String,
    val body: String,
    val footer: String
)

private val galleryCards = listOf(
    StoryCard(
        title = "Lantern Walk",
        body = "Lorem ipsum dolor sit amet, soft rain on cobblestone and a warm little glow drifting past the window.",
        footer = "Warm cups · narrow streets · late dusk"
    ),
    StoryCard(
        title = "Orchard Steps",
        body = "Vestibulum ante ipsum primis in faucibus orci luctus et ultrices posuere cubilia curae; apples, moss, and a quiet bench.",
        footer = "Green leaves · slow breeze · bright noon"
    ),
    StoryCard(
        title = "Paper Boats",
        body = "Integer posuere erat a ante venenatis dapibus posuere velit aliquet, with folded notes sailing along the canal.",
        footer = "Blue water · tiny bells · midnight hush"
    )
)

private val listEntries = listOf(
    StoryCard(
        title = "Amber Alley",
        body = "Lorem ipsum dolor sit amet, one crooked lamp and a baker still humming through the open door.",
        footer = "Honey rolls"
    ),
    StoryCard(
        title = "Rose Market",
        body = "Praesent commodo cursus magna, vel scelerisque nisl consectetur et, with pink paper signs and drifting petals.",
        footer = "Ribbon stalls"
    ),
    StoryCard(
        title = "Moss Corner",
        body = "Curabitur blandit tempus porttitor; green stone, tiny puddles, and a cat that refuses to move from the step.",
        footer = "Rainy nook"
    ),
    StoryCard(
        title = "Blue Wharf",
        body = "Aenean lacinia bibendum nulla sed consectetur, where the crates smell like salt and ink-stamped postcards.",
        footer = "Canal breeze"
    ),
    StoryCard(
        title = "Willow Gate",
        body = "Donec id elit non mi porta gravida at eget metus, half hidden behind leaves and ribbon ties.",
        footer = "Quiet shortcut"
    ),
    StoryCard(
        title = "Glass Arcade",
        body = "Etiam porta sem malesuada magna mollis euismod, with warm reflections stretching across the floor.",
        footer = "Window light"
    ),
    StoryCard(
        title = "Copper Stairs",
        body = "Sed posuere consectetur est at lobortis; footsteps echo here even when the square is nearly empty.",
        footer = "Echo hall"
    ),
    StoryCard(
        title = "Lilac Porch",
        body = "Maecenas faucibus mollis interdum, plus one sleepy lantern and two chipped cups left on the railing.",
        footer = "Porch swing"
    ),
    StoryCard(
        title = "North Pantry",
        body = "Cras mattis consectetur purus sit amet fermentum, stacked with flour sacks and scribbled shopping lists.",
        footer = "Pantry door"
    ),
    StoryCard(
        title = "Soft Bell Lane",
        body = "Nulla vitae elit libero, a pharetra augue, and every doorframe seems to collect another little bell.",
        footer = "Tin chimes"
    )
)

private val hostedShowcaseEntries = listOf(
    StoryCard(
        title = "Native button",
        body = "Pressing the hosted button fires the real Forge button callback and updates Compose state right away.",
        footer = "Clicks + callbacks"
    ),
    StoryCard(
        title = "Hosted checkbox",
        body = "The checkbox is a live hosted GuiCheckBox, so its checked state and label stay in sync while Compose redraws around it.",
        footer = "Boolean state"
    ),
    StoryCard(
        title = "Text focus",
        body = "The text field is a real GuiTextField, including focus, cursor handling, placeholder text, and typed input.",
        footer = "Focus + typing"
    ),
    StoryCard(
        title = "Slider drag",
        body = "Dragging the hosted slider updates the value continuously and the Compose preview reflects the new percentage immediately.",
        footer = "Pointer dragging"
    ),
    StoryCard(
        title = "Wheel scrolling",
        body = "The selectable list is backed by a native GuiSlot, so wheel scrolling and row selection work like a vanilla menu.",
        footer = "Mouse wheel"
    ),
    StoryCard(
        title = "Disabled states",
        body = "You can pause the live form widgets to confirm that disabled rendering and interaction blocking are still correct.",
        footer = "Enabled flags"
    ),
    StoryCard(
        title = "Selection mirroring",
        body = "Selecting an item in the hosted list can copy its note into the hosted text field, which is a nice end-to-end state test.",
        footer = "Cross-widget state"
    ),
    StoryCard(
        title = "Placeholder rendering",
        body = "Clear the hosted field and click away to verify the placeholder text reappears inside the native input box.",
        footer = "Empty text"
    ),
    StoryCard(
        title = "Programmatic focus",
        body = "Use the focus button to request focus from Compose state and watch the hosted text field pick it up live.",
        footer = "Focus bridge"
    )
)

@SideOnly(Side.CLIENT)
class TestGuiScreen : ComposeGuiScreen() {
    override val composeBackgroundStyle: ComposeBackgroundStyle = ComposeBackgroundStyle.VanillaDefault

    override fun doesGuiPauseGame(): Boolean = false

    @Composable
    override fun Content() {
        var pouredCups by remember { mutableStateOf(2) }
        var selectedSnippet by remember { mutableStateOf("The hosted controls will narrate their changes here as you click around.") }
        var showWelcomeNote by remember { mutableStateOf(true) }
        var compactMode by remember { mutableStateOf(false) }
        var receiveLetters by remember { mutableStateOf(true) }
        var selectedTab by remember { mutableStateOf(DemoTab.HOSTED) }
        var selectedSegment by remember { mutableStateOf("Dusk") }
        var selectedNativeIndex by remember { mutableStateOf(-1) }
        var hostedControlsEnabled by remember { mutableStateOf(true) }
        var hostedMirrorSelection by remember { mutableStateOf(true) }
        var hostedButtonClicks by remember { mutableStateOf(0) }
        var hostedSelectedIndex by remember { mutableStateOf(0) }
        var highlightedCardIndex by remember { mutableStateOf(0) }
        var sliderValue by remember { mutableStateOf(68.0) }
        var hostedSliderValue by remember { mutableStateOf(35.0) }
        val contentScrollState = rememberScrollState()
        val listScrollState = rememberScrollState()
        val nameField = rememberTextFieldState("Luna")
        val noteField = rememberTextFieldState("Lorem ipsum dolor sit amet, meet me where the lanterns turn gold.")
        val searchField = rememberTextFieldState("")
        val hostedTitleField = rememberTextFieldState("Hosted widgets on the porch.")
        val hostedSearchField = rememberTextFieldState("")
        val filteredEntries = remember(searchField.text) {
            val query = searchField.text.trim()
            if (query.isEmpty()) {
                listEntries
            } else {
                listEntries.filter { entry ->
                    entry.title.contains(query, ignoreCase = true) ||
                        entry.body.contains(query, ignoreCase = true) ||
                        entry.footer.contains(query, ignoreCase = true)
                }
            }
        }
        val hostedFilteredEntries = remember(hostedSearchField.text) {
            val query = hostedSearchField.text.trim()
            if (query.isEmpty()) {
                hostedShowcaseEntries
            } else {
                hostedShowcaseEntries.filter { entry ->
                    entry.title.contains(query, ignoreCase = true) ||
                        entry.body.contains(query, ignoreCase = true) ||
                        entry.footer.contains(query, ignoreCase = true)
                }
            }
        }
        val currentCard = galleryCards[highlightedCardIndex.coerceIn(galleryCards.indices)]
        val safeSelectedNativeIndex = selectedNativeIndex.takeIf { it in filteredEntries.indices } ?: -1
        val safeHostedIndex = hostedSelectedIndex.takeIf { it in hostedFilteredEntries.indices } ?: -1
        val selectedHostedEntry = hostedFilteredEntries.getOrNull(safeHostedIndex)

        Box(modifier = Modifier().fillMaxSize()) {
            Panel(
                modifier = Modifier()
                    .fillMaxSize()
                    .padding(10.uu),
            ) {
                Column(
                    modifier = Modifier().fillMaxSize(),
                    verticalArrangement = VerticalArrangement.spacedBy(8.uu),
                    horizontalAlignment = HorizontalAlignment.CENTER,
                    scrollState = contentScrollState
                ) {
                    ShowcaseHeading(
                        modifier = Modifier().tooltip( styledText {
                            withBold {
                                append( "hi!")
                            }

                            withItalic {
                                append( "hi!")
                            }

                            withObfuscated {
                                append( "hi!")
                            }

                            withColor(MinecraftColor.Red) {
                                append( "hi!")
                            }

                        }),
                        title = "Pocket Postcards",
                        subtitle = "A small wandering screen with placeholder copy, playful inputs, and a few tiny scenes to tap through."
                    )

                    ViewModelShowcasePanel()

                    Tabs(
                        options = DemoTab.entries,
                        selected = selectedTab,
                        modifier = Modifier().fillMaxWidth(),
                        labelOf = { tab ->
                            when (tab) {
                                DemoTab.WELCOME -> "Welcome"
                                DemoTab.HOSTED -> "Hosted"
                                DemoTab.FORMS -> "Forms"
                                DemoTab.GALLERY -> "Gallery"
                                DemoTab.LISTS -> "Lists"
                                DemoTab.NAVIGATION -> "Navigation"
                            }
                        },
                        onSelected = { tab ->
                            selectedTab = tab
                        }
                    ) { activeTab ->
                        when (activeTab) {
                            DemoTab.WELCOME -> {
                                Column(
                                    modifier = Modifier().fillMaxWidth(),
                                    verticalArrangement = VerticalArrangement.spacedBy(6.uu),
                                    horizontalAlignment = HorizontalAlignment.START
                                ) {
                                    Panel(modifier = Modifier().fillMaxWidth()) {
                                        Column(
                                            modifier = Modifier().fillMaxWidth(),
                                            verticalArrangement = VerticalArrangement.spacedBy(6.uu),
                                            horizontalAlignment = HorizontalAlignment.START
                                        ) {
                                            Text(
                                                text = "Front porch",
                                                modifier = Modifier().fillMaxWidth(),
                                                style = TextStyle(
                                                    color = Color.rgb(red = 0xFF, green = 0xFF, blue = 0xFF),
                                                    alignment = HorizontalAlignment.CENTER
                                                )
                                            )
                                            Box(
                                                modifier = Modifier()
                                                    .fillMaxWidth()
                                                    .height((if (compactMode) 56 else 72).uu)
                                                    .background(Color(0x66353B4D))
                                                    .border(Color(0xFF7C87A1))
                                            ) {
                                                Text(
                                                    text = "${currentCard.title} · $selectedSegment",
                                                    modifier = Modifier()
                                                        .fillMaxWidth()
                                                        .padding(8.uu)
                                                        .align(Alignment.Center),
                                                    style = TextStyle(
                                                        color = Color(0xFFF6F0D8),
                                                        alignment = HorizontalAlignment.CENTER,
                                                        wrap = true
                                                    )
                                                )
                                            }
                                            if (showWelcomeNote) {
                                                Text(
                                                    text = "Lorem ipsum dolor sit amet, soft rain by the railing and one warm window still glowing across the lane.",
                                                    modifier = Modifier().fillMaxWidth(),
                                                    style = TextStyle(
                                                        color = Color.rgb(red = 0xD8, green = 0xD8, blue = 0xD8),
                                                        wrap = true
                                                    )
                                                )
                                            }
                                            Text(
                                                text = "${nameField.text.ifEmpty { "A quiet guest" }} has set out $pouredCups cups for a $selectedSegment walk.",
                                                modifier = Modifier().fillMaxWidth(),
                                                style = TextStyle(
                                                    color = Color.rgb(red = 0xC8, green = 0xD5, blue = 0xE6),
                                                    wrap = true
                                                )
                                            )
                                            Row(
                                                modifier = Modifier().fillMaxWidth(),
                                                horizontalArrangement = HorizontalArrangement.spacedBy(6.uu),
                                                verticalAlignment = VerticalAlignment.CENTER
                                            ) {
                                                Button(
                                                    text = "Pour one",
                                                    modifier = Modifier().width(96.uu),
                                                    onClick = {
                                                        pouredCups += 1
                                                        selectedSnippet = "Another cup was placed by the window ledge."
                                                    }
                                                )
                                                Button(
                                                    text = "Next card",
                                                    modifier = Modifier().width(96.uu),
                                                    onClick = {
                                                        highlightedCardIndex = (highlightedCardIndex + 1) % galleryCards.size
                                                        selectedSnippet = galleryCards[highlightedCardIndex].body
                                                    }
                                                )
                                                Button(
                                                    text = "Close",
                                                    modifier = Modifier().width(96.uu),
                                                    onClick = {
                                                        mc.displayGuiScreen(null)
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    Panel(modifier = Modifier().fillMaxWidth()) {
                                        Column(
                                            modifier = Modifier().fillMaxWidth(),
                                            verticalArrangement = VerticalArrangement.spacedBy(6.uu),
                                            horizontalAlignment = HorizontalAlignment.START
                                        ) {
                                            Text(
                                                text = "Little toggles",
                                                modifier = Modifier().fillMaxWidth(),
                                                style = TextStyle(
                                                    color = Color.rgb(red = 0xFF, green = 0xFF, blue = 0xFF),
                                                    alignment = HorizontalAlignment.CENTER
                                                )
                                            )
                                            Checkbox(
                                                label = "Keep the welcome note",
                                                checked = showWelcomeNote,
                                                modifier = Modifier().fillMaxWidth(),
                                                onCheckedChange = {
                                                    showWelcomeNote = it
                                                    selectedSnippet = if (it) {
                                                        "The porch note was pinned back under the ribbon."
                                                    } else {
                                                        "The porch note was folded away for later."
                                                    }
                                                }
                                            )
                                            ToggleButton(
                                                label = "Compact cards",
                                                checked = compactMode,
                                                modifier = Modifier().fillMaxWidth(),
                                                onCheckedChange = {
                                                    compactMode = it
                                                    selectedSnippet = if (it) {
                                                        "Everything tucked itself into a smaller stack."
                                                    } else {
                                                        "The cards stretched out and took a longer breath."
                                                    }
                                                }
                                            )
                                            Checkbox(
                                                label = "Send tiny letters",
                                                checked = receiveLetters,
                                                modifier = Modifier().fillMaxWidth(),
                                                onCheckedChange = {
                                                    receiveLetters = it
                                                    selectedSnippet = if (it) {
                                                        "A new envelope waited by the door."
                                                    } else {
                                                        "The letter basket was left empty tonight."
                                                    }
                                                }
                                            )
                                            Text(
                                                text = selectedSnippet,
                                                modifier = Modifier().fillMaxWidth(),
                                                style = TextStyle(
                                                    color = Color.rgb(red = 0xD6, green = 0xD6, blue = 0xD6),
                                                    wrap = true
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            DemoTab.HOSTED -> {
                                Column(
                                    modifier = Modifier().fillMaxWidth(),
                                    verticalArrangement = VerticalArrangement.spacedBy(6.uu),
                                    horizontalAlignment = HorizontalAlignment.START
                                ) {
                                    Panel(modifier = Modifier().fillMaxWidth()) {
                                        Column(
                                            modifier = Modifier().fillMaxWidth(),
                                            verticalArrangement = VerticalArrangement.spacedBy(6.uu),
                                            horizontalAlignment = HorizontalAlignment.START
                                        ) {
                                            Text(
                                                text = "Hosted controls live",
                                                modifier = Modifier().fillMaxWidth(),
                                                style = TextStyle(
                                                    color = Color.rgb(red = 0xFF, green = 0xFF, blue = 0xFF),
                                                    alignment = HorizontalAlignment.CENTER
                                                )
                                            )
                                            Text(
                                                text = "This tab is the direct smoke test for the hosted widgets: native Forge buttons, checkboxes, text fields, sliders, and selectable lists all running through the Compose screen host.",
                                                modifier = Modifier().fillMaxWidth(),
                                                style = TextStyle(
                                                    color = Color.rgb(red = 0xE0, green = 0xE0, blue = 0xE0),
                                                    wrap = true
                                                )
                                            )
                                            Text(
                                                text = "Clicks $hostedButtonClicks · field ${if (hostedTitleField.focused) "focused" else "idle"} · list ${selectedHostedEntry?.title ?: "none selected"} · glow ${hostedSliderValue.toInt()}%",
                                                modifier = Modifier().fillMaxWidth(),
                                                style = TextStyle(
                                                    color = Color.rgb(red = 0xB8, green = 0xD7, blue = 0xFF),
                                                    alignment = HorizontalAlignment.CENTER,
                                                    wrap = true
                                                )
                                            )
                                        }
                                    }

                                    Panel(modifier = Modifier().fillMaxWidth()) {
                                        Column(
                                            modifier = Modifier().fillMaxWidth(),
                                            verticalArrangement = VerticalArrangement.spacedBy(6.uu),
                                            horizontalAlignment = HorizontalAlignment.START
                                        ) {
                                            Text(
                                                text = "Live hosted form widgets",
                                                modifier = Modifier().fillMaxWidth(),
                                                style = TextStyle(
                                                    color = Color.rgb(red = 0xFF, green = 0xFF, blue = 0xFF),
                                                    alignment = HorizontalAlignment.CENTER
                                                )
                                            )
                                            Checkbox(
                                                label = "Enable live form widgets",
                                                checked = hostedControlsEnabled,
                                                modifier = Modifier().fillMaxWidth(),
                                                onCheckedChange = {
                                                    hostedControlsEnabled = it
                                                    selectedSnippet = if (it) {
                                                        "The hosted form widgets woke back up and started listening again."
                                                    } else {
                                                        "The hosted form widgets were paused so you can check their disabled rendering."
                                                    }
                                                }
                                            )
                                            Checkbox(
                                                label = "Mirror list selection into the hosted field",
                                                checked = hostedMirrorSelection,
                                                modifier = Modifier().fillMaxWidth(),
                                                onCheckedChange = {
                                                    hostedMirrorSelection = it
                                                    selectedSnippet = if (it) {
                                                        "Selecting a hosted list row will now copy its note into the field."
                                                    } else {
                                                        "List picks will stay separate from the hosted field for now."
                                                    }
                                                }
                                            )
                                            TextField(
                                                state = hostedTitleField,
                                                modifier = Modifier().fillMaxWidth().height(20.uu),
                                                placeholder = "Type into the hosted GuiTextField",
                                                enabled = hostedControlsEnabled
                                            )
                                            Slider(
                                                value = hostedSliderValue,
                                                onValueChange = {
                                                    hostedSliderValue = it
                                                    selectedSnippet = "The hosted slider was dragged to ${it.toInt()}%."
                                                },
                                                modifier = Modifier().fillMaxWidth().height(20.uu),
                                                valueRange = 0.0..100.0,
                                                label = "Hosted glow",
                                                suffix = "%",
                                                enabled = hostedControlsEnabled,
                                                showDecimal = false
                                            )
                                            Row(
                                                modifier = Modifier().fillMaxWidth(),
                                                horizontalArrangement = HorizontalArrangement.spacedBy(6.uu),
                                                verticalAlignment = VerticalAlignment.CENTER
                                            ) {
                                                Button(
                                                    text = "Tap native button",
                                                    modifier = Modifier().width(118.uu),
                                                    enabled = hostedControlsEnabled,
                                                    onClick = {
                                                        hostedButtonClicks += 1
                                                        selectedSnippet = "The hosted button fired click #$hostedButtonClicks."
                                                    }
                                                )
                                                Button(
                                                    text = "Focus field",
                                                    modifier = Modifier().width(96.uu),
                                                    enabled = hostedControlsEnabled,
                                                    onClick = {
                                                        hostedTitleField.requestFocus()
                                                        selectedSnippet = "Compose requested focus for the hosted text field."
                                                    }
                                                )
                                                Button(
                                                    text = "Reset",
                                                    modifier = Modifier().width(72.uu),
                                                    onClick = {
                                                        hostedControlsEnabled = true
                                                        hostedMirrorSelection = true
                                                        hostedButtonClicks = 0
                                                        hostedSelectedIndex = 0
                                                        hostedSliderValue = 35.0
                                                        hostedSearchField.text = ""
                                                        hostedTitleField.text = "Hosted widgets on the porch."
                                                        hostedTitleField.clearFocus()
                                                        selectedSnippet = "The hosted demo was reset to its default state."
                                                    }
                                                )
                                            }
                                            Text(
                                                text = "Field text: ${hostedTitleField.text.ifEmpty { "(empty)" }}",
                                                modifier = Modifier().fillMaxWidth(),
                                                style = TextStyle(
                                                    color = Color.rgb(red = 0xD6, green = 0xD6, blue = 0xD6),
                                                    wrap = true
                                                )
                                            )
                                        }
                                    }

                                    Panel(modifier = Modifier().fillMaxWidth()) {
                                        Column(
                                            modifier = Modifier().fillMaxWidth(),
                                            verticalArrangement = VerticalArrangement.spacedBy(6.uu),
                                            horizontalAlignment = HorizontalAlignment.START
                                        ) {
                                            Text(
                                                text = "Hosted selectable list",
                                                modifier = Modifier().fillMaxWidth(),
                                                style = TextStyle(
                                                    color = Color.rgb(red = 0xFF, green = 0xFF, blue = 0xFF),
                                                    alignment = HorizontalAlignment.CENTER
                                                )
                                            )
                                            TextField(
                                                state = hostedSearchField,
                                                modifier = Modifier().fillMaxWidth().height(20.uu),
                                                placeholder = "Filter the hosted list by feature name"
                                            )
                                            SelectableList(
                                                items = if (hostedFilteredEntries.isEmpty()) {
                                                    listOf("No hosted test cases match that filter")
                                                } else {
                                                    hostedFilteredEntries.map { entry -> "${entry.title} · ${entry.footer}" }
                                                },
                                                selectedIndex = if (hostedFilteredEntries.isEmpty()) -1 else safeHostedIndex,
                                                modifier = Modifier()
                                                    .fillMaxWidth()
                                                    .height((if (compactMode) 92 else 128).uu),
                                                rowHeight = 18.uu,
                                                visibleRowCount = if (compactMode) 4 else 6,
                                                onSelectedIndexChange = { index ->
                                                    if (index in hostedFilteredEntries.indices) {
                                                        hostedSelectedIndex = index
                                                        val entry = hostedFilteredEntries[index]
                                                        if (hostedMirrorSelection) {
                                                            hostedTitleField.text = entry.body
                                                        }
                                                        selectedSnippet = "${entry.title}: ${entry.body}"
                                                    }
                                                }
                                            )
                                            Row(
                                                modifier = Modifier().fillMaxWidth(),
                                                horizontalArrangement = HorizontalArrangement.spacedBy(6.uu),
                                                verticalAlignment = VerticalAlignment.CENTER
                                            ) {
                                                Button(
                                                    text = "Load selected note",
                                                    modifier = Modifier().width(120.uu),
                                                    enabled = hostedControlsEnabled && selectedHostedEntry != null,
                                                    onClick = {
                                                        selectedHostedEntry?.let { entry ->
                                                            hostedTitleField.text = entry.body
                                                            hostedTitleField.requestFocus()
                                                            selectedSnippet = "Loaded ${entry.title.lowercase()} into the hosted field and requested focus."
                                                        }
                                                    }
                                                )
                                                Button(
                                                    text = "Clear field",
                                                    modifier = Modifier().width(96.uu),
                                                    enabled = hostedControlsEnabled && hostedTitleField.text.isNotEmpty(),
                                                    onClick = {
                                                        hostedTitleField.text = ""
                                                        hostedTitleField.clearFocus()
                                                        selectedSnippet = "The hosted field was cleared so the placeholder can show again."
                                                    }
                                                )
                                            }
                                            Text(
                                                text = selectedHostedEntry?.body
                                                    ?: "Scroll the hosted list with the wheel and select an entry to inspect it here.",
                                                modifier = Modifier().fillMaxWidth(),
                                                style = TextStyle(
                                                    color = Color.rgb(red = 0xE6, green = 0xE6, blue = 0xE6),
                                                    wrap = true
                                                )
                                            )
                                            Text(
                                                text = selectedSnippet,
                                                modifier = Modifier().fillMaxWidth(),
                                                style = TextStyle(
                                                    color = Color.rgb(red = 0xC7, green = 0xD9, blue = 0xF4),
                                                    wrap = true
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            DemoTab.FORMS -> {
                                Column(
                                    modifier = Modifier().fillMaxWidth(),
                                    verticalArrangement = VerticalArrangement.spacedBy(6.uu),
                                    horizontalAlignment = HorizontalAlignment.START
                                ) {
                                    Panel(modifier = Modifier().fillMaxWidth()) {
                                        Column(
                                            modifier = Modifier().fillMaxWidth(),
                                            verticalArrangement = VerticalArrangement.spacedBy(6.uu),
                                            horizontalAlignment = HorizontalAlignment.START
                                        ) {
                                            Text(
                                                text = "Write a postcard",
                                                modifier = Modifier().fillMaxWidth(),
                                                style = TextStyle(
                                                    color = Color.rgb(red = 0xFF, green = 0xFF, blue = 0xFF),
                                                    alignment = HorizontalAlignment.CENTER
                                                )
                                            )
                                            TextField(
                                                state = nameField,
                                                modifier = Modifier().fillMaxWidth().height(20.uu),
                                                placeholder = "Name on the envelope"
                                            )
                                            TextField(
                                                state = noteField,
                                                modifier = Modifier().fillMaxWidth().height(20.uu),
                                                placeholder = "A short note for later"
                                            )
                                            Slider(
                                                value = sliderValue,
                                                onValueChange = { sliderValue = it },
                                                modifier = Modifier().fillMaxWidth().height(20.uu),
                                                valueRange = 0.0..100.0,
                                                label = "Lantern glow",
                                                suffix = "%",
                                                showDecimal = false
                                            )
                                            SegmentedControl(
                                                options = listOf("Morning", "Dusk", "Midnight"),
                                                selected = selectedSegment,
                                                modifier = Modifier().fillMaxWidth(),
                                                onSelected = { option ->
                                                    selectedSegment = option
                                                    selectedSnippet = "The postcard was tucked into the $option pile."
                                                }
                                            )
                                            Row(
                                                modifier = Modifier().fillMaxWidth(),
                                                horizontalArrangement = HorizontalArrangement.spacedBy(6.uu),
                                                verticalAlignment = VerticalAlignment.CENTER
                                            ) {
                                                Button(
                                                    text = "Borrow line",
                                                    modifier = Modifier().width(118.uu),
                                                    onClick = {
                                                        noteField.text = "Sed posuere consectetur est at lobortis, leave the gate open and the tea warm."
                                                        selectedSnippet = "A borrowed line was slipped onto the card."
                                                    }
                                                )
                                                Button(
                                                    text = "Clear note",
                                                    modifier = Modifier().width(118.uu),
                                                    enabled = noteField.text.isNotEmpty(),
                                                    onClick = {
                                                        noteField.text = ""
                                                        selectedSnippet = "The note was cleared for a fresh start."
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    Panel(modifier = Modifier().fillMaxWidth()) {
                                        Column(
                                            modifier = Modifier().fillMaxWidth(),
                                            verticalArrangement = VerticalArrangement.spacedBy(6.uu),
                                            horizontalAlignment = HorizontalAlignment.START
                                        ) {
                                            Text(
                                                text = "Preview",
                                                modifier = Modifier().fillMaxWidth(),
                                                style = TextStyle(
                                                    color = Color.rgb(red = 0xFF, green = 0xFF, blue = 0xFF),
                                                    alignment = HorizontalAlignment.CENTER
                                                )
                                            )
                                            Text(
                                                text = "To: ${nameField.text.ifEmpty { "Somebody waiting by the window" }}",
                                                modifier = Modifier().fillMaxWidth(),
                                                style = TextStyle(
                                                    color = Color.rgb(red = 0xF1, green = 0xE8, blue = 0xD1),
                                                    wrap = true
                                                )
                                            )
                                            Text(
                                                text = noteField.text.ifEmpty {
                                                    "Lorem ipsum dolor sit amet, consectetur adipiscing elit, and a little blank space for the rest."
                                                },
                                                modifier = Modifier().fillMaxWidth(),
                                                style = TextStyle(
                                                    color = Color.rgb(red = 0xD8, green = 0xD8, blue = 0xD8),
                                                    wrap = true
                                                )
                                            )
                                            ToggleButton(
                                                label = "Letters",
                                                checked = receiveLetters,
                                                modifier = Modifier().fillMaxWidth(),
                                                onCheckedChange = {
                                                    receiveLetters = it
                                                    selectedSnippet = if (it) {
                                                        "The ribbon basket opened for more letters."
                                                    } else {
                                                        "The ribbon basket was tied shut."
                                                    }
                                                }
                                            )
                                            Text(
                                                text = "Mood: $selectedSegment · glow ${sliderValue.toInt()}% · letters ${if (receiveLetters) "on" else "off"}",
                                                modifier = Modifier().fillMaxWidth(),
                                                style = TextStyle(
                                                    color = Color.rgb(red = 0xB8, green = 0xD7, blue = 0xFF),
                                                    wrap = true
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            DemoTab.GALLERY -> {
                                Column(
                                    modifier = Modifier().fillMaxWidth(),
                                    verticalArrangement = VerticalArrangement.spacedBy(6.uu),
                                    horizontalAlignment = HorizontalAlignment.START
                                ) {
                                    Panel(modifier = Modifier().fillMaxWidth()) {
                                        Column(
                                            modifier = Modifier().fillMaxWidth(),
                                            verticalArrangement = VerticalArrangement.spacedBy(6.uu),
                                            horizontalAlignment = HorizontalAlignment.START
                                        ) {
                                            Text(
                                                text = "Pinned scene",
                                                modifier = Modifier().fillMaxWidth(),
                                                style = TextStyle(
                                                    color = Color.rgb(red = 0xFF, green = 0xFF, blue = 0xFF),
                                                    alignment = HorizontalAlignment.CENTER
                                                )
                                            )
                                            Box(
                                                modifier = Modifier()
                                                    .fillMaxWidth()
                                                    .height((if (compactMode) 52 else 68).uu)
                                                    .background(Color(0x664A3342))
                                                    .border(Color(0xFF9A7286))
                                            ) {
                                                Text(
                                                    text = currentCard.title,
                                                    modifier = Modifier()
                                                        .fillMaxWidth()
                                                        .padding(8.uu)
                                                        .align(Alignment.Center),
                                                    style = TextStyle(
                                                        color = Color(0xFFF3D6E7),
                                                        alignment = HorizontalAlignment.CENTER
                                                    )
                                                )
                                            }
                                            Text(
                                                text = currentCard.body,
                                                modifier = Modifier().fillMaxWidth(),
                                                style = TextStyle(
                                                    color = Color.rgb(red = 0xD8, green = 0xD8, blue = 0xD8),
                                                    wrap = true
                                                )
                                            )
                                            Text(
                                                text = currentCard.footer,
                                                modifier = Modifier().fillMaxWidth(),
                                                style = TextStyle(
                                                    color = Color.rgb(red = 0xC7, green = 0xAF, blue = 0xC0),
                                                    alignment = HorizontalAlignment.CENTER,
                                                    wrap = true
                                                )
                                            )
                                        }
                                    }

                                    Spacer(height = 2.uu)

                                    galleryCards.forEachIndexed { index, card ->
                                        Panel(modifier = Modifier().fillMaxWidth()) {
                                            Column(
                                                modifier = Modifier().fillMaxWidth(),
                                                verticalArrangement = VerticalArrangement.spacedBy(4.uu),
                                                horizontalAlignment = HorizontalAlignment.START
                                            ) {
                                                Text(
                                                    text = card.title,
                                                    modifier = Modifier().fillMaxWidth(),
                                                    style = TextStyle(
                                                        color = if (index == highlightedCardIndex) Color(0xFFF9E7AE) else Color.rgb(red = 0xFF, green = 0xFF, blue = 0xFF),
                                                        alignment = HorizontalAlignment.CENTER
                                                    )
                                                )
                                                Text(
                                                    text = card.body,
                                                    modifier = Modifier().fillMaxWidth(),
                                                    style = TextStyle(
                                                        color = Color.rgb(red = 0xE6, green = 0xE6, blue = 0xE6),
                                                        wrap = true
                                                    )
                                                )
                                                Row(
                                                    modifier = Modifier().fillMaxWidth(),
                                                    horizontalArrangement = HorizontalArrangement.spacedBy(6.uu),
                                                    verticalAlignment = VerticalAlignment.CENTER
                                                ) {
                                                    Button(
                                                        text = if (index == highlightedCardIndex) "Pinned" else "Pin here",
                                                        modifier = Modifier().width(96.uu),
                                                        enabled = index != highlightedCardIndex,
                                                        onClick = {
                                                            highlightedCardIndex = index
                                                            selectedSnippet = card.body
                                                        }
                                                    )
                                                    Button(
                                                        text = "Copy line",
                                                        modifier = Modifier().width(96.uu),
                                                        onClick = {
                                                            noteField.text = card.body
                                                            selectedSnippet = "A line from ${card.title} was copied into the postcard."
                                                        }
                                                    )
                                                }
                                                Text(
                                                    text = card.footer,
                                                    modifier = Modifier().fillMaxWidth(),
                                                    style = TextStyle(
                                                        color = Color.rgb(red = 0xB8, green = 0xC6, blue = 0xD7),
                                                        wrap = true
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            DemoTab.LISTS -> {
                                Column(
                                    modifier = Modifier().fillMaxWidth(),
                                    verticalArrangement = VerticalArrangement.spacedBy(6.uu),
                                    horizontalAlignment = HorizontalAlignment.START
                                ) {
                                    Panel(modifier = Modifier().fillMaxWidth()) {
                                        Column(
                                            modifier = Modifier().fillMaxWidth(),
                                            verticalArrangement = VerticalArrangement.spacedBy(6.uu),
                                            horizontalAlignment = HorizontalAlignment.START
                                        ) {
                                            Text(
                                                text = "Pick a stop",
                                                modifier = Modifier().fillMaxWidth(),
                                                style = TextStyle(
                                                    color = Color.rgb(red = 0xFF, green = 0xFF, blue = 0xFF),
                                                    alignment = HorizontalAlignment.CENTER
                                                )
                                            )
                                            TextField(
                                                state = searchField,
                                                modifier = Modifier().fillMaxWidth().height(20.uu),
                                                placeholder = "Search lanes, porches, and market stalls"
                                            )
                                            SelectableList(
                                                items = if (filteredEntries.isEmpty()) {
                                                    listOf("No little places match that search")
                                                } else {
                                                    filteredEntries.map { entry -> "${entry.title} · ${entry.footer}" }
                                                },
                                                selectedIndex = if (filteredEntries.isEmpty()) -1 else safeSelectedNativeIndex,
                                                modifier = Modifier()
                                                    .fillMaxWidth()
                                                    .height((if (compactMode) 92 else 128).uu),
                                                rowHeight = 18.uu,
                                                visibleRowCount = if (compactMode) 4 else 6,
                                                onSelectedIndexChange = { index ->
                                                    if (index in filteredEntries.indices) {
                                                        selectedNativeIndex = index
                                                        val entry = filteredEntries[index]
                                                        selectedSnippet = "${entry.title}: ${entry.body}"
                                                    }
                                                }
                                            )
                                        }
                                    }

                                    Panel(modifier = Modifier().fillMaxWidth()) {
                                        Column(
                                            modifier = Modifier().fillMaxWidth(),
                                            verticalArrangement = VerticalArrangement.spacedBy(6.uu),
                                            horizontalAlignment = HorizontalAlignment.START
                                        ) {
                                            Text(
                                                text = "Long stroll",
                                                modifier = Modifier().fillMaxWidth(),
                                                style = TextStyle(
                                                    color = Color.rgb(red = 0xFF, green = 0xFF, blue = 0xFF),
                                                    alignment = HorizontalAlignment.CENTER
                                                )
                                            )
                                            Column(
                                                modifier = Modifier()
                                                    .fillMaxWidth()
                                                    .height((if (compactMode) 132 else 176).uu)
                                                    .padding(6.uu)
                                                    .background(Color(0x8821262F))
                                                    .border(Color(0xFF59606E)),
                                                verticalArrangement = VerticalArrangement.spacedBy(4.uu),
                                                horizontalAlignment = HorizontalAlignment.START,
                                                scrollState = listScrollState
                                            ) {
                                                if (filteredEntries.isEmpty()) {
                                                    Panel(modifier = Modifier().fillMaxWidth()) {
                                                        Text(
                                                            text = "Lorem ipsum dolor sit amet, no matching porch or market stall turned up this time.",
                                                            modifier = Modifier().fillMaxWidth(),
                                                            style = TextStyle(
                                                                color = Color.rgb(red = 0xE6, green = 0xE6, blue = 0xE6),
                                                                wrap = true
                                                            )
                                                        )
                                                    }
                                                } else {
                                                    filteredEntries.forEachIndexed { index, entry ->
                                                        Panel(modifier = Modifier().fillMaxWidth()) {
                                                            Column(
                                                                modifier = Modifier().fillMaxWidth(),
                                                                verticalArrangement = VerticalArrangement.spacedBy(4.uu),
                                                                horizontalAlignment = HorizontalAlignment.START
                                                            ) {
                                                                Text(
                                                                    text = entry.title,
                                                                    modifier = Modifier().fillMaxWidth(),
                                                                    style = TextStyle(
                                                                        color = Color(0xFFF1D7A8),
                                                                        wrap = true
                                                                    )
                                                                )
                                                                Text(
                                                                    text = entry.body,
                                                                    modifier = Modifier().fillMaxWidth(),
                                                                    style = TextStyle(
                                                                        color = Color.rgb(red = 0xE6, green = 0xE6, blue = 0xE6),
                                                                        wrap = true
                                                                    )
                                                                )
                                                                Button(
                                                                    text = "Keep this line ${index + 1}",
                                                                    modifier = Modifier().fillMaxWidth(),
                                                                    onClick = {
                                                                        selectedSnippet = entry.body
                                                                    }
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            Text(
                                                text = selectedSnippet,
                                                modifier = Modifier().fillMaxWidth(),
                                                style = TextStyle(
                                                    color = Color.rgb(red = 0xD6, green = 0xD6, blue = 0xD6),
                                                    wrap = true
                                                )
                                            )
                                            Text(
                                                text = "Native pick: ${filteredEntries.getOrNull(safeSelectedNativeIndex)?.title ?: "none yet"} · scroll ${listScrollState.value}px",
                                                modifier = Modifier().fillMaxWidth(),
                                                style = TextStyle(
                                                    color = Color.rgb(red = 0xB8, green = 0xB8, blue = 0xB8),
                                                    alignment = HorizontalAlignment.CENTER,
                                                    wrap = true
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            DemoTab.NAVIGATION -> {
                                Column(
                                    modifier = Modifier().fillMaxWidth(),
                                    verticalArrangement = VerticalArrangement.spacedBy(6.uu),
                                    horizontalAlignment = HorizontalAlignment.START
                                ) {
                                    NavigationDemoPanel()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

class TestGuiViewModel : ViewModel() {
    var tapCount by mutableStateOf(0)
        private set

    var bannerAlpha by mutableStateOf(100)
        private set

    var bannerTitle by mutableStateOf("screen scoped")
        private set

    var note by mutableStateOf("This block is powered by the real androidx.lifecycle ViewModel + viewModel() API.")
        private set

    fun recordTap(message: String) {
        tapCount += 1
        bannerTitle = if (tapCount % 2 == 0) "still alive" else "remembered"
        note = "$message Tap #$tapCount survived recomposition without remember()."
    }

    fun toggleBannerAlpha() {
        bannerAlpha = if (bannerAlpha == 100) 180 else 100
        note = "Banner alpha now uses Color.copy(alpha = $bannerAlpha) from the ViewModel-backed state."
    }

    fun renameBanner() {
        bannerTitle = when (bannerTitle) {
            "screen scoped" -> "child lookup"
            "child lookup" -> "same instance"
            else -> "screen scoped"
        }
        note = "A nested composable resolved the same real viewModel() instance without prop drilling."
    }
}

@Composable
private fun ViewModelShowcasePanel() {
    val screenViewModel: TestGuiViewModel = viewModel(TestGuiViewModel::class)
    val screen = LocalComposeGuiScreen.current

    Panel(modifier = Modifier().fillMaxWidth()) {
        Column(
            modifier = Modifier().fillMaxWidth(),
            verticalArrangement = VerticalArrangement.spacedBy(4.uu),
            horizontalAlignment = HorizontalAlignment.CENTER
        ) {
            Text(
                text = styledText {
                    append("AndroidX ViewModel · ")
                    withColor(MinecraftColor.Gold) {
                        append(screenViewModel.bannerTitle)
                    }
                    append(" · taps ")
                    withBold {
                        append(screenViewModel.tapCount.toString())
                    }
                },
                modifier = Modifier().fillMaxWidth(),
                style = TextStyle(
                    color = Color(0xFF2596BE).copy(alpha = screenViewModel.bannerAlpha),
                    alignment = HorizontalAlignment.CENTER,
                    wrap = true
                )
            )
            Text(
                text = screenViewModel.note,
                modifier = Modifier().fillMaxWidth(),
                style = TextStyle(
                    color = Color.rgb(red = 0xD6, green = 0xE8, blue = 0xF2),
                    alignment = HorizontalAlignment.CENTER,
                    wrap = true
                )
            )
            ViewModelStatusLine()
            Row(
                modifier = Modifier().fillMaxWidth(),
                horizontalArrangement = HorizontalArrangement.spacedBy(6.uu),
                verticalAlignment = VerticalAlignment.CENTER
            ) {
                Button(
                    text = "VM ping",
                    modifier = Modifier().width(96.uu),
                    onClick = {
                        screenViewModel.recordTap("The real AndroidX ViewModel kept its state inside this screen scope.")
                    }
                )
                Button(
                    text = "Fade",
                    modifier = Modifier().width(96.uu),
                    onClick = {
                        screenViewModel.toggleBannerAlpha()
                    }
                )
                Button(
                    text = "Rename",
                    modifier = Modifier().width(96.uu),
                    onClick = {
                        screenViewModel.renameBanner()
                    }
                )
            }
            Button(
                text = "Open VM child",
                modifier = Modifier().width(120.uu),
                onClick = {
                    screen.mc.displayGuiScreen(ViewModelLifecycleProofScreen())
                }
            )
        }
    }
}

@Composable
private fun ViewModelStatusLine() {
    val screenViewModel: TestGuiViewModel = viewModel(TestGuiViewModel::class)

    Text(
        text = "Nested child lookup sees the same instance: ${screenViewModel.bannerTitle} · alpha ${screenViewModel.bannerAlpha}",
        modifier = Modifier().fillMaxWidth(),
        style = TextStyle(
            color = Color.rgb(red = 0xB8, green = 0xD7, blue = 0xFF),
            alignment = HorizontalAlignment.CENTER,
            wrap = true
        )
    )
}

@Composable
private fun ShowcaseHeading(
    modifier: Modifier = Modifier(),
    title: String,
    subtitle: String
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = VerticalArrangement.spacedBy(3.uu),
        horizontalAlignment = HorizontalAlignment.CENTER
    ) {
        Text(
            text = title,
            modifier = Modifier().fillMaxWidth(),
            style = TextStyle(
                color = Color.rgb(red = 0xFF, green = 0xFF, blue = 0xFF),
                alignment = HorizontalAlignment.CENTER
            )
        )
        Text(
            text = subtitle,
            modifier = Modifier().fillMaxWidth(),
            style = TextStyle(
                color = Color.rgb(red = 0xD0, green = 0xD0, blue = 0xD0),
                alignment = HorizontalAlignment.CENTER,
                wrap = true
            )
        )
    }
}

