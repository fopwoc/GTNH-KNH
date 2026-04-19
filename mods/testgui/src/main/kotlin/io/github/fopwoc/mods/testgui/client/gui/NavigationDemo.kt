package io.github.fopwoc.mods.testgui.client.gui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.fopwoc.mods.framework.ui.compose.component.Panel
import io.github.fopwoc.mods.framework.ui.compose.component.native.Button
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextStyle
import io.github.fopwoc.mods.framework.ui.compose.navigation.NavHost
import io.github.fopwoc.mods.framework.ui.compose.navigation.NavKey
import io.github.fopwoc.mods.framework.ui.compose.navigation.entryProvider
import io.github.fopwoc.mods.framework.ui.compose.navigation.rememberNavBackStack
import io.github.fopwoc.mods.framework.ui.compose.navigation.rememberNavigator
import io.github.fopwoc.mods.framework.ui.compose.text.MinecraftColor
import io.github.fopwoc.mods.framework.ui.compose.text.styledText
import io.github.fopwoc.mods.framework.ui.compose.unit.uu
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private sealed interface NavigationDemoDestination : NavKey {
    data object Home : NavigationDemoDestination
    data class Postcard(val title: String, val body: String) : NavigationDemoDestination
    data class Counter(val label: String) : NavigationDemoDestination
}

private data class NavigationDemoCard(
    val title: String,
    val body: String,
    val footer: String
)

private val navigationDemoCards = listOf(
    NavigationDemoCard(
        title = "Lantern Walk",
        body = "This postcard destination can be pushed twice with the same key value, and each stack entry still gets its own ViewModelStore.",
        footer = "Duplicate keys keep unique entry ids"
    ),
    NavigationDemoCard(
        title = "Orchard Steps",
        body = "Pop the stack and the previous entry comes back with the same ViewModel instance id because it was kept alive underneath the top destination.",
        footer = "Previous entry state survives"
    )
)

private sealed interface NavigationCounterNestedDestination : NavKey {
    data object Overview : NavigationCounterNestedDestination
    data object Timeline : NavigationCounterNestedDestination
}

@Composable
fun NavigationDemoPanel() {
    val backStack = rememberNavBackStack<NavigationDemoDestination>(NavigationDemoDestination.Home)
    val navigator = rememberNavigator(backStack)
    val lifecyclePulse = remember { MutableStateFlow(0) }
    val destinationProvider = remember {
        entryProvider<NavigationDemoDestination> {
            entry<NavigationDemoDestination.Home> {
                NavigationHomeEntry(
                    firstCard = navigationDemoCards.first(),
                    onOpenPostcard = {
                        push(
                            NavigationDemoDestination.Postcard(
                                title = navigationDemoCards.first().title,
                                body = navigationDemoCards.first().body
                            )
                        )
                    },
                    onOpenCounter = {
                        push(NavigationDemoDestination.Counter(label = "Home"))
                    }
                )
            }
            entry<NavigationDemoDestination.Postcard> { postcard ->
                NavigationPostcardEntry(
                    postcard = postcard,
                    canPop = navigator.canPop,
                    onDuplicatePush = {
                        push(postcard)
                    },
                    onOpenCounter = {
                        push(NavigationDemoDestination.Counter(label = postcard.title))
                    },
                    onPop = {
                        pop()
                    }
                )
            }
            entry<NavigationDemoDestination.Counter>(retainSaveableState = true) { counter ->
                NavigationCounterEntry(
                    counter = counter,
                    coverCard = navigationDemoCards.last(),
                    lifecyclePulse = lifecyclePulse,
                    canPop = navigator.canPop,
                    onCoverWithPostcard = {
                        push(
                            NavigationDemoDestination.Postcard(
                                title = navigationDemoCards.last().title,
                                body = navigationDemoCards.last().body
                            )
                        )
                    },
                    onPop = {
                        pop()
                    }
                )
            }
        }
    }
    val stackPreview = navigator.entries.joinToString(separator = " -> ") { entry ->
        when (val destination = entry.key) {
            NavigationDemoDestination.Home -> "Home#${entry.id}"
            is NavigationDemoDestination.Postcard -> "${destination.title}#${entry.id}"
            is NavigationDemoDestination.Counter -> "Counter(${destination.label})#${entry.id}"
        }
    }

    Panel(modifier = Modifier().fillMaxWidth()) {
        Column(
            modifier = Modifier().fillMaxWidth(),
            verticalArrangement = VerticalArrangement.spacedBy(6.uu),
            horizontalAlignment = HorizontalAlignment.START
        ) {
            Text(
                text = "Minimal Nav3-inspired stack",
                modifier = Modifier().fillMaxWidth(),
                style = TextStyle(
                    color = Color.rgb(red = 0xFF, green = 0xFF, blue = 0xFF),
                    alignment = HorizontalAlignment.CENTER
                )
            )
            Text(
                text = "This demo uses the new navigator helper API, wires Escape/back into NavHost automatically, supports nested hosts, lets entries opt into rememberSaveable retention while they stay hidden on the same back stack, and also shows AndroidX lifecycle-runtime-compose collection against the active NavHost entry lifecycle.",
                modifier = Modifier().fillMaxWidth(),
                style = TextStyle(
                    color = Color.rgb(red = 0xD8, green = 0xD8, blue = 0xD8),
                    wrap = true
                )
            )
            Text(
                text = stackPreview,
                modifier = Modifier().fillMaxWidth(),
                style = TextStyle(
                    color = Color.rgb(red = 0xB8, green = 0xD7, blue = 0xFF),
                    alignment = HorizontalAlignment.CENTER,
                    wrap = true
                )
            )
            Text(
                text = "Toolbar lifecycle flow emissions: ${lifecyclePulse.value}. Open a Counter route, cover it with a postcard, press Emit flow here, then go back to see collectAsStateWithLifecycle resume when that entry becomes RESUMED again.",
                modifier = Modifier().fillMaxWidth(),
                style = TextStyle(
                    color = Color.rgb(red = 0xD6, green = 0xE8, blue = 0xF2),
                    wrap = true
                )
            )
            Row(
                modifier = Modifier().fillMaxWidth(),
                horizontalArrangement = HorizontalArrangement.spacedBy(6.uu),
                verticalAlignment = VerticalAlignment.CENTER
            ) {
                Button(
                    text = "Back",
                    modifier = Modifier().width(88.uu),
                    enabled = navigator.canPop,
                    onClick = {
                        navigator.navigateBack()
                    }
                )
                Button(
                    text = "Root",
                    modifier = Modifier().width(88.uu),
                    enabled = navigator.canPop,
                    onClick = {
                        navigator.popToRoot()
                    }
                )
                Button(
                    text = "Counter",
                    modifier = Modifier().width(96.uu),
                    onClick = {
                        navigator.navigate(NavigationDemoDestination.Counter(label = "Toolbar"))
                    }
                )
            }
            Button(
                text = "Emit flow",
                modifier = Modifier().fillMaxWidth(),
                onClick = {
                    lifecyclePulse.value = lifecyclePulse.value + 1
                }
            )
            Panel(modifier = Modifier().fillMaxWidth()) {
                NavHost(
                    backStack = backStack,
                    entryProvider = destinationProvider,
                    emptyContent = {
                        Text(
                            text = "The stack is empty. Push a destination to start again.",
                            modifier = Modifier().fillMaxWidth(),
                            style = TextStyle(
                                color = Color.rgb(red = 0xE6, green = 0xE6, blue = 0xE6),
                                alignment = HorizontalAlignment.CENTER,
                                wrap = true
                            )
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun NavigationHomeEntry(
    firstCard: NavigationDemoCard,
    onOpenPostcard: () -> Unit,
    onOpenCounter: () -> Unit
) {
    val entryViewModel: NavDemoEntryViewModel = viewModel(NavDemoEntryViewModel::class)

    Column(
        modifier = Modifier().fillMaxWidth(),
        verticalArrangement = VerticalArrangement.spacedBy(5.uu),
        horizontalAlignment = HorizontalAlignment.START
    ) {
        Text(
            text = styledText {
                append("Home entry · ")
                withColor(MinecraftColor.Gold) {
                    append("ViewModel creation #${entryViewModel.creationSequence}")
                }
                append(" · taps ${entryViewModel.tapCount}")
            },
            modifier = Modifier().fillMaxWidth(),
            style = TextStyle(
                color = Color(0xFFB8D7FF).copy(alpha = entryViewModel.bannerAlpha),
                alignment = HorizontalAlignment.CENTER,
                wrap = true
            )
        )
        Text(
            text = entryViewModel.note,
            modifier = Modifier().fillMaxWidth(),
            style = TextStyle(
                color = Color.rgb(red = 0xD6, green = 0xE8, blue = 0xF2),
                wrap = true
            )
        )
        Text(
            text = "First postcard: ${firstCard.title} · ${firstCard.footer}",
            modifier = Modifier().fillMaxWidth(),
            style = TextStyle(
                color = Color.rgb(red = 0xC7, green = 0xD9, blue = 0xF4),
                wrap = true
            )
        )
        Row(
            modifier = Modifier().fillMaxWidth(),
            horizontalArrangement = HorizontalArrangement.spacedBy(6.uu),
            verticalAlignment = VerticalAlignment.CENTER
        ) {
            Button(
                text = "Tap home VM",
                modifier = Modifier().width(112.uu),
                onClick = {
                    entryViewModel.recordTap("Home")
                }
            )
            Button(
                text = "Open postcard",
                modifier = Modifier().width(112.uu),
                onClick = onOpenPostcard
            )
        }
        Button(
            text = "Open counter entry",
            modifier = Modifier().fillMaxWidth(),
            onClick = onOpenCounter
        )
    }
}

@Composable
private fun NavigationPostcardEntry(
    postcard: NavigationDemoDestination.Postcard,
    canPop: Boolean,
    onDuplicatePush: () -> Unit,
    onOpenCounter: () -> Unit,
    onPop: () -> Unit
) {
    val entryViewModel: NavDemoEntryViewModel = viewModel(NavDemoEntryViewModel::class)

    Column(
        modifier = Modifier().fillMaxWidth(),
        verticalArrangement = VerticalArrangement.spacedBy(5.uu),
        horizontalAlignment = HorizontalAlignment.START
    ) {
        Text(
            text = styledText {
                append(postcard.title)
                append(" · ViewModel creation ")
                withColor(MinecraftColor.Gold) {
                    append("#${entryViewModel.creationSequence}")
                }
            },
            modifier = Modifier().fillMaxWidth(),
            style = TextStyle(
                color = Color(0xFFF6F0D8).copy(alpha = entryViewModel.bannerAlpha),
                alignment = HorizontalAlignment.CENTER,
                wrap = true
            )
        )
        Text(
            text = postcard.body,
            modifier = Modifier().fillMaxWidth(),
            style = TextStyle(
                color = Color.rgb(red = 0xE6, green = 0xE6, blue = 0xE6),
                wrap = true
            )
        )
        Text(
            text = "Tap count ${entryViewModel.tapCount}. Push the same postcard again and the stack will contain a second entry with a different ViewModel instance.",
            modifier = Modifier().fillMaxWidth(),
            style = TextStyle(
                color = Color.rgb(red = 0xC7, green = 0xD9, blue = 0xF4),
                wrap = true
            )
        )
        Row(
            modifier = Modifier().fillMaxWidth(),
            horizontalArrangement = HorizontalArrangement.spacedBy(6.uu),
            verticalAlignment = VerticalAlignment.CENTER
        ) {
            Button(
                text = "Tap detail VM",
                modifier = Modifier().width(108.uu),
                onClick = {
                    entryViewModel.recordTap(postcard.title)
                }
            )
            Button(
                text = "Duplicate push",
                modifier = Modifier().width(108.uu),
                onClick = onDuplicatePush
            )
        }
        Row(
            modifier = Modifier().fillMaxWidth(),
            horizontalArrangement = HorizontalArrangement.spacedBy(6.uu),
            verticalAlignment = VerticalAlignment.CENTER
        ) {
            Button(
                text = "Counter route",
                modifier = Modifier().width(108.uu),
                onClick = onOpenCounter
            )
            Button(
                text = "Back",
                modifier = Modifier().width(88.uu),
                enabled = canPop,
                onClick = onPop
            )
        }
    }
}

@Composable
private fun NavigationCounterEntry(
    counter: NavigationDemoDestination.Counter,
    coverCard: NavigationDemoCard,
    lifecyclePulse: StateFlow<Int>,
    canPop: Boolean,
    onCoverWithPostcard: () -> Unit,
    onPop: () -> Unit
) {
    val entryViewModel: NavDemoEntryViewModel = viewModel(NavDemoEntryViewModel::class)
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val collectedPulse by lifecyclePulse.collectAsStateWithLifecycle(
        lifecycleOwner = lifecycleOwner,
        minActiveState = Lifecycle.State.STARTED
    )
    var localClicks by rememberSaveable { mutableStateOf(0) }
    val nestedBackStack = rememberNavBackStack<NavigationCounterNestedDestination>(
        NavigationCounterNestedDestination.Overview
    )
    val nestedNavigator = rememberNavigator(nestedBackStack)
    val nestedStackPreview = nestedNavigator.entries.joinToString(separator = " -> ") { entry ->
        when (entry.key) {
            NavigationCounterNestedDestination.Overview -> "Overview#${entry.id}"
            NavigationCounterNestedDestination.Timeline -> "Timeline#${entry.id}"
        }
    }

    Column(
        modifier = Modifier().fillMaxWidth(),
        verticalArrangement = VerticalArrangement.spacedBy(5.uu),
        horizontalAlignment = HorizontalAlignment.START
    ) {
        Text(
            text = "Counter route from ${counter.label} · entry ViewModel creation #${entryViewModel.creationSequence}",
            modifier = Modifier().fillMaxWidth(),
            style = TextStyle(
                color = Color.rgb(red = 0xFF, green = 0xE5, blue = 0x9A),
                alignment = HorizontalAlignment.CENTER,
                wrap = true
            )
        )
        Text(
            text = "Back-stack-retained rememberSaveable clicks: $localClicks · ViewModel taps: ${entryViewModel.tapCount}",
            modifier = Modifier().fillMaxWidth(),
            style = TextStyle(
                color = Color.rgb(red = 0xD8, green = 0xD8, blue = 0xD8),
                wrap = true
            )
        )
        Text(
            text = "Lifecycle owner state: $lifecycleState · Toolbar flow now: ${lifecyclePulse.value} · collectAsStateWithLifecycle sees: $collectedPulse",
            modifier = Modifier().fillMaxWidth(),
            style = TextStyle(
                color = Color.rgb(red = 0xB8, green = 0xD7, blue = 0xFF),
                wrap = true
            )
        )
        Text(
            text = "This Counter entry opted into saveable-state retention, so its local rememberSaveable count survives while this route stays on the same back stack and you push ${coverCard.title} on top. While covered, this entry lifecycle drops below STARTED, so collectAsStateWithLifecycle pauses here until you return.",
            modifier = Modifier().fillMaxWidth(),
            style = TextStyle(
                color = Color.rgb(red = 0xC7, green = 0xD9, blue = 0xF4),
                wrap = true
            )
        )
        Text(
            text = "Nested stack: $nestedStackPreview",
            modifier = Modifier().fillMaxWidth(),
            style = TextStyle(
                color = Color.rgb(red = 0xB8, green = 0xD7, blue = 0xFF),
                wrap = true
            )
        )
        Row(
            modifier = Modifier().fillMaxWidth(),
            horizontalArrangement = HorizontalArrangement.spacedBy(6.uu),
            verticalAlignment = VerticalAlignment.CENTER
        ) {
            Button(
                text = "Tap local",
                modifier = Modifier().width(88.uu),
                onClick = {
                    localClicks += 1
                }
            )
            Button(
                text = "Tap ViewModel",
                modifier = Modifier().width(88.uu),
                onClick = {
                    entryViewModel.recordTap(counter.label)
                }
            )
            Button(
                text = "Cover route",
                modifier = Modifier().width(96.uu),
                onClick = onCoverWithPostcard
            )
        }
        Row(
            modifier = Modifier().fillMaxWidth(),
            horizontalArrangement = HorizontalArrangement.spacedBy(6.uu),
            verticalAlignment = VerticalAlignment.CENTER
        ) {
            Button(
                text = "Nested detail",
                modifier = Modifier().width(108.uu),
                onClick = {
                    if (nestedNavigator.currentKey != NavigationCounterNestedDestination.Timeline) {
                        nestedNavigator.navigate(NavigationCounterNestedDestination.Timeline)
                    }
                }
            )
            Button(
                text = "Back",
                modifier = Modifier().width(88.uu),
                enabled = canPop,
                onClick = onPop
            )
        }
        Panel(modifier = Modifier().fillMaxWidth()) {
            NavHost(
                backStack = nestedBackStack,
                entryProvider = entryProvider {
                    entry<NavigationCounterNestedDestination.Overview> {
                        Column(
                            modifier = Modifier().fillMaxWidth(),
                            verticalArrangement = VerticalArrangement.spacedBy(4.uu),
                            horizontalAlignment = HorizontalAlignment.START
                        ) {
                            Text(
                                text = "Nested host overview for ${counter.label}. Open the nested detail, then press Escape or Back to see the inner host consume the event before the outer stack does.",
                                modifier = Modifier().fillMaxWidth(),
                                style = TextStyle(
                                    color = Color.rgb(red = 0xD6, green = 0xE8, blue = 0xF2),
                                    wrap = true
                                )
                            )
                            Button(
                                text = "Open nested detail",
                                modifier = Modifier().fillMaxWidth(),
                                onClick = {
                                    navigate(NavigationCounterNestedDestination.Timeline)
                                }
                            )
                        }
                    }
                    entry<NavigationCounterNestedDestination.Timeline> {
                        var nestedClicks by rememberSaveable { mutableStateOf(0) }

                        Column(
                            modifier = Modifier().fillMaxWidth(),
                            verticalArrangement = VerticalArrangement.spacedBy(4.uu),
                            horizontalAlignment = HorizontalAlignment.START
                        ) {
                            Text(
                                text = "Nested detail · rememberSaveable taps $nestedClicks",
                                modifier = Modifier().fillMaxWidth(),
                                style = TextStyle(
                                    color = Color.rgb(red = 0xFF, green = 0xF4, blue = 0xC7),
                                    wrap = true
                                )
                            )
                            Text(
                                text = "While this nested destination is visible, Escape/back pops only this inner host. The outer counter route stays on screen until you press back again.",
                                modifier = Modifier().fillMaxWidth(),
                                style = TextStyle(
                                    color = Color.rgb(red = 0xD8, green = 0xD8, blue = 0xD8),
                                    wrap = true
                                )
                            )
                            Row(
                                modifier = Modifier().fillMaxWidth(),
                                horizontalArrangement = HorizontalArrangement.spacedBy(6.uu),
                                verticalAlignment = VerticalAlignment.CENTER
                            ) {
                                Button(
                                    text = "Tap nested",
                                    modifier = Modifier().width(100.uu),
                                    onClick = {
                                        nestedClicks += 1
                                    }
                                )
                                Button(
                                    text = "Back nested",
                                    modifier = Modifier().width(100.uu),
                                    onClick = {
                                        pop()
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    }
}

class NavDemoEntryViewModel : ViewModel() {
    val creationSequence: Int = nextCreationSequence++

    var tapCount by mutableStateOf(0)
        private set

    var bannerAlpha by mutableStateOf(120)
        private set

    var note by mutableStateOf("Each NavHost entry gets its own ViewModelStore, so identical destination keys can still create different ViewModel instances.")
        private set

    fun recordTap(label: String) {
        tapCount += 1
        bannerAlpha = if (bannerAlpha == 120) 210 else 120
        note = "$label entry tap #$tapCount is stored inside ViewModel creation #$creationSequence."
    }

    private companion object {
        var nextCreationSequence: Int = 1
    }
}


