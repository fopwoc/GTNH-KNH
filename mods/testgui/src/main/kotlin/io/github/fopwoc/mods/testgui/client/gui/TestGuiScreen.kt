package io.github.fopwoc.mods.testgui.client.gui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextStyle
import io.github.fopwoc.mods.framework.ui.compose.minecraft.ComposeBackgroundStyle
import io.github.fopwoc.mods.framework.ui.compose.minecraft.ComposeGuiScreen
import io.github.fopwoc.mods.framework.ui.compose.runtime.rememberScrollState
import io.github.fopwoc.mods.framework.ui.compose.runtime.rememberTextFieldState
import io.github.fopwoc.mods.framework.ui.compose.unit.uu

private enum class DemoTab {
    WELCOME,
    FORMS,
    GALLERY,
    LISTS
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

@SideOnly(Side.CLIENT)
class TestGuiScreen : ComposeGuiScreen() {
    override val composeBackgroundStyle: ComposeBackgroundStyle = ComposeBackgroundStyle.VanillaDefault

    override fun doesGuiPauseGame(): Boolean = false

    @Composable
    override fun Content() {
        var pouredCups by remember { mutableStateOf(2) }
        var selectedSnippet by remember { mutableStateOf("A little note will appear here once you tap around.") }
        var showWelcomeNote by remember { mutableStateOf(true) }
        var compactMode by remember { mutableStateOf(false) }
        var receiveLetters by remember { mutableStateOf(true) }
        var selectedTab by remember { mutableStateOf(DemoTab.WELCOME) }
        var selectedSegment by remember { mutableStateOf("Dusk") }
        var selectedNativeIndex by remember { mutableStateOf(-1) }
        var highlightedCardIndex by remember { mutableStateOf(0) }
        var sliderValue by remember { mutableStateOf(68.0) }
        val contentScrollState = rememberScrollState()
        val listScrollState = rememberScrollState()
        val nameField = rememberTextFieldState("Luna")
        val noteField = rememberTextFieldState("Lorem ipsum dolor sit amet, meet me where the lanterns turn gold.")
        val searchField = rememberTextFieldState("")
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
        val currentCard = galleryCards[highlightedCardIndex.coerceIn(galleryCards.indices)]
        val safeSelectedNativeIndex = selectedNativeIndex.takeIf { it in filteredEntries.indices } ?: -1

        Box(modifier = Modifier().fillMaxSize()) {
            Panel(
                modifier = Modifier()
                    .fillMaxSize()
                    .padding(10.uu),
            ) {
                Column(
                    modifier = Modifier().fillMaxSize(),
                    spacing = 8.uu,
                    horizontalAlignment = HorizontalAlignment.CENTER,
                    scrollState = contentScrollState
                ) {
                    ShowcaseHeading(
                        title = "Pocket Postcards",
                        subtitle = "A small wandering screen with placeholder copy, playful inputs, and a few tiny scenes to tap through."
                    )

                    Tabs(
                        options = DemoTab.entries,
                        selected = selectedTab,
                        modifier = Modifier().fillMaxWidth(),
                        labelOf = { tab ->
                            when (tab) {
                                DemoTab.WELCOME -> "Welcome"
                                DemoTab.FORMS -> "Forms"
                                DemoTab.GALLERY -> "Gallery"
                                DemoTab.LISTS -> "Lists"
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
                                    spacing = 6.uu,
                                    horizontalAlignment = HorizontalAlignment.START
                                ) {
                                    Panel(modifier = Modifier().fillMaxWidth()) {
                                        Column(
                                            modifier = Modifier().fillMaxWidth(),
                                            spacing = 6.uu,
                                            horizontalAlignment = HorizontalAlignment.START
                                        ) {
                                            Text(
                                                text = "Front porch",
                                                modifier = Modifier().fillMaxWidth(),
                                                style = TextStyle(
                                                    color = 0xFFFFFF,
                                                    alignment = HorizontalAlignment.CENTER
                                                )
                                            )
                                            Box(
                                                modifier = Modifier()
                                                    .fillMaxWidth()
                                                    .height((if (compactMode) 56 else 72).uu)
                                                    .background(0x66353B4D)
                                                    .border(0xFF7C87A1.toInt())
                                            ) {
                                                Text(
                                                    text = "${currentCard.title} · $selectedSegment",
                                                    modifier = Modifier()
                                                        .fillMaxWidth()
                                                        .padding(8.uu)
                                                        .align(
                                                            horizontal = HorizontalAlignment.CENTER,
                                                            vertical = VerticalAlignment.CENTER
                                                        ),
                                                    style = TextStyle(
                                                        color = 0xFFF6F0D8.toInt(),
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
                                                        color = 0xD8D8D8,
                                                        wrap = true
                                                    )
                                                )
                                            }
                                            Text(
                                                text = "${nameField.text.ifEmpty { "A quiet guest" }} has set out $pouredCups cups for a $selectedSegment walk.",
                                                modifier = Modifier().fillMaxWidth(),
                                                style = TextStyle(
                                                    color = 0xC8D5E6,
                                                    wrap = true
                                                )
                                            )
                                            Row(
                                                modifier = Modifier().fillMaxWidth(),
                                                spacing = 6.uu,
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
                                            spacing = 6.uu,
                                            horizontalAlignment = HorizontalAlignment.START
                                        ) {
                                            Text(
                                                text = "Little toggles",
                                                modifier = Modifier().fillMaxWidth(),
                                                style = TextStyle(
                                                    color = 0xFFFFFF,
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
                                                    color = 0xD6D6D6,
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
                                    spacing = 6.uu,
                                    horizontalAlignment = HorizontalAlignment.START
                                ) {
                                    Panel(modifier = Modifier().fillMaxWidth()) {
                                        Column(
                                            modifier = Modifier().fillMaxWidth(),
                                            spacing = 6.uu,
                                            horizontalAlignment = HorizontalAlignment.START
                                        ) {
                                            Text(
                                                text = "Write a postcard",
                                                modifier = Modifier().fillMaxWidth(),
                                                style = TextStyle(
                                                    color = 0xFFFFFF,
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
                                                spacing = 6.uu,
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
                                            spacing = 6.uu,
                                            horizontalAlignment = HorizontalAlignment.START
                                        ) {
                                            Text(
                                                text = "Preview",
                                                modifier = Modifier().fillMaxWidth(),
                                                style = TextStyle(
                                                    color = 0xFFFFFF,
                                                    alignment = HorizontalAlignment.CENTER
                                                )
                                            )
                                            Text(
                                                text = "To: ${nameField.text.ifEmpty { "Somebody waiting by the window" }}",
                                                modifier = Modifier().fillMaxWidth(),
                                                style = TextStyle(
                                                    color = 0xF1E8D1.toInt(),
                                                    wrap = true
                                                )
                                            )
                                            Text(
                                                text = noteField.text.ifEmpty {
                                                    "Lorem ipsum dolor sit amet, consectetur adipiscing elit, and a little blank space for the rest."
                                                },
                                                modifier = Modifier().fillMaxWidth(),
                                                style = TextStyle(
                                                    color = 0xD8D8D8,
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
                                                    color = 0xB8D7FF,
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
                                    spacing = 6.uu,
                                    horizontalAlignment = HorizontalAlignment.START
                                ) {
                                    Panel(modifier = Modifier().fillMaxWidth()) {
                                        Column(
                                            modifier = Modifier().fillMaxWidth(),
                                            spacing = 6.uu,
                                            horizontalAlignment = HorizontalAlignment.START
                                        ) {
                                            Text(
                                                text = "Pinned scene",
                                                modifier = Modifier().fillMaxWidth(),
                                                style = TextStyle(
                                                    color = 0xFFFFFF,
                                                    alignment = HorizontalAlignment.CENTER
                                                )
                                            )
                                            Box(
                                                modifier = Modifier()
                                                    .fillMaxWidth()
                                                    .height((if (compactMode) 52 else 68).uu)
                                                    .background(0x664A3342)
                                                    .border(0xFF9A7286.toInt())
                                            ) {
                                                Text(
                                                    text = currentCard.title,
                                                    modifier = Modifier()
                                                        .fillMaxWidth()
                                                        .padding(8.uu)
                                                        .align(
                                                            horizontal = HorizontalAlignment.CENTER,
                                                            vertical = VerticalAlignment.CENTER
                                                        ),
                                                    style = TextStyle(
                                                        color = 0xFFF3D6E7.toInt(),
                                                        alignment = HorizontalAlignment.CENTER
                                                    )
                                                )
                                            }
                                            Text(
                                                text = currentCard.body,
                                                modifier = Modifier().fillMaxWidth(),
                                                style = TextStyle(
                                                    color = 0xD8D8D8,
                                                    wrap = true
                                                )
                                            )
                                            Text(
                                                text = currentCard.footer,
                                                modifier = Modifier().fillMaxWidth(),
                                                style = TextStyle(
                                                    color = 0xC7AFC0.toInt(),
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
                                                spacing = 4.uu,
                                                horizontalAlignment = HorizontalAlignment.START
                                            ) {
                                                Text(
                                                    text = card.title,
                                                    modifier = Modifier().fillMaxWidth(),
                                                    style = TextStyle(
                                                        color = if (index == highlightedCardIndex) 0xFFF9E7AE.toInt() else 0xFFFFFF,
                                                        alignment = HorizontalAlignment.CENTER
                                                    )
                                                )
                                                Text(
                                                    text = card.body,
                                                    modifier = Modifier().fillMaxWidth(),
                                                    style = TextStyle(
                                                        color = 0xE6E6E6,
                                                        wrap = true
                                                    )
                                                )
                                                Row(
                                                    modifier = Modifier().fillMaxWidth(),
                                                    spacing = 6.uu,
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
                                                        color = 0xB8C6D7,
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
                                    spacing = 6.uu,
                                    horizontalAlignment = HorizontalAlignment.START
                                ) {
                                    Panel(modifier = Modifier().fillMaxWidth()) {
                                        Column(
                                            modifier = Modifier().fillMaxWidth(),
                                            spacing = 6.uu,
                                            horizontalAlignment = HorizontalAlignment.START
                                        ) {
                                            Text(
                                                text = "Pick a stop",
                                                modifier = Modifier().fillMaxWidth(),
                                                style = TextStyle(
                                                    color = 0xFFFFFF,
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
                                            spacing = 6.uu,
                                            horizontalAlignment = HorizontalAlignment.START
                                        ) {
                                            Text(
                                                text = "Long stroll",
                                                modifier = Modifier().fillMaxWidth(),
                                                style = TextStyle(
                                                    color = 0xFFFFFF,
                                                    alignment = HorizontalAlignment.CENTER
                                                )
                                            )
                                            Column(
                                                modifier = Modifier()
                                                    .fillMaxWidth()
                                                    .height((if (compactMode) 132 else 176).uu)
                                                    .padding(6.uu)
                                                    .background(0x8821262F.toInt())
                                                    .border(0xFF59606E.toInt()),
                                                spacing = 4.uu,
                                                horizontalAlignment = HorizontalAlignment.START,
                                                scrollState = listScrollState
                                            ) {
                                                if (filteredEntries.isEmpty()) {
                                                    Panel(modifier = Modifier().fillMaxWidth()) {
                                                        Text(
                                                            text = "Lorem ipsum dolor sit amet, no matching porch or market stall turned up this time.",
                                                            modifier = Modifier().fillMaxWidth(),
                                                            style = TextStyle(
                                                                color = 0xE6E6E6,
                                                                wrap = true
                                                            )
                                                        )
                                                    }
                                                } else {
                                                    filteredEntries.forEachIndexed { index, entry ->
                                                        Panel(modifier = Modifier().fillMaxWidth()) {
                                                            Column(
                                                                modifier = Modifier().fillMaxWidth(),
                                                                spacing = 4.uu,
                                                                horizontalAlignment = HorizontalAlignment.START
                                                            ) {
                                                                Text(
                                                                    text = entry.title,
                                                                    modifier = Modifier().fillMaxWidth(),
                                                                    style = TextStyle(
                                                                        color = 0xFFF1D7A8.toInt(),
                                                                        wrap = true
                                                                    )
                                                                )
                                                                Text(
                                                                    text = entry.body,
                                                                    modifier = Modifier().fillMaxWidth(),
                                                                    style = TextStyle(
                                                                        color = 0xE6E6E6,
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
                                                    color = 0xD6D6D6,
                                                    wrap = true
                                                )
                                            )
                                            Text(
                                                text = "Native pick: ${filteredEntries.getOrNull(safeSelectedNativeIndex)?.title ?: "none yet"} · scroll ${listScrollState.value}px",
                                                modifier = Modifier().fillMaxWidth(),
                                                style = TextStyle(
                                                    color = 0xB8B8B8,
                                                    alignment = HorizontalAlignment.CENTER,
                                                    wrap = true
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShowcaseHeading(
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier().fillMaxWidth(),
        spacing = 3.uu,
        horizontalAlignment = HorizontalAlignment.CENTER
    ) {
        Text(
            text = title,
            modifier = Modifier().fillMaxWidth(),
            style = TextStyle(
                color = 0xFFFFFF,
                alignment = HorizontalAlignment.CENTER
            )
        )
        Text(
            text = subtitle,
            modifier = Modifier().fillMaxWidth(),
            style = TextStyle(
                color = 0xD0D0D0,
                alignment = HorizontalAlignment.CENTER,
                wrap = true
            )
        )
    }
}

