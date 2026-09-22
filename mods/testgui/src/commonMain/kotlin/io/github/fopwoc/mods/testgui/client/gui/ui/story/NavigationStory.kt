package io.github.fopwoc.mods.testgui.client.gui.ui.story

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import io.github.fopwoc.mods.framework.ui.compose.component.Card
import io.github.fopwoc.mods.framework.ui.compose.component.vanilla.Button
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.navigation.NavHost
import io.github.fopwoc.mods.framework.ui.compose.runtime.BackHandler
import io.github.fopwoc.mods.framework.ui.compose.unit.uu

private sealed interface Page : NavKey {
    data object Home : Page

    data class Detail(val n: Int) : Page
}

/** A nested NavHost: Escape pops it before the screen closes; BackHandler intercepts first. */
@Composable
fun NavigationStory() {
    val backStack = remember { NavBackStack<Page>(Page.Home) }
    Examples {
        Text("stack: " + backStack.joinToString(" > ") { it.toString().substringAfterLast('.') })
        Card(modifier = Modifier.fillMaxWidth()) {
            NavHost(
                backStack = backStack,
                entryProvider =
                    entryProvider {
                        entry<Page.Home> {
                            Column(verticalArrangement = VerticalArrangement.spacedBy(4.uu)) {
                                Text("Home")
                                Row(horizontalArrangement = HorizontalArrangement.spacedBy(4.uu)) {
                                    Button(text = "push Detail(1)") {
                                        backStack.add(Page.Detail(1))
                                    }
                                    Button(text = "push Detail(2)") {
                                        backStack.add(Page.Detail(2))
                                    }
                                }
                            }
                        }
                        entry<Page.Detail> { page ->
                            var guard by remember(page) { mutableStateOf(page.n == 2) }
                            BackHandler(enabled = guard) { guard = false }
                            Column(verticalArrangement = VerticalArrangement.spacedBy(4.uu)) {
                                Text(
                                    "Detail ${page.n}" +
                                        if (page.n == 2)
                                            " — first Escape is swallowed by a BackHandler"
                                        else ""
                                )
                                Row(horizontalArrangement = HorizontalArrangement.spacedBy(4.uu)) {
                                    Button(text = "push next") {
                                        backStack.add(Page.Detail(page.n + 1))
                                    }
                                    Button(text = "replaceTop") {
                                        backStack[backStack.lastIndex] = Page.Detail(page.n * 10)
                                    }
                                    Button(text = "pop", enabled = backStack.size > 1) {
                                        backStack.removeAt(backStack.lastIndex)
                                    }
                                    Button(text = "popToRoot") {
                                        while (backStack.size > 1) backStack.removeAt(
                                            backStack.lastIndex
                                        )
                                    }
                                }
                            }
                        }
                    },
            )
        }
    }
}
