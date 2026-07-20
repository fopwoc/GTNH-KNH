package io.github.fopwoc.mods.testgui.client.gui.ui.screens.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.fopwoc.mods.framework.ui.compose.component.Panel
import io.github.fopwoc.mods.framework.ui.compose.component.native.Button
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextStyle
import io.github.fopwoc.mods.framework.ui.compose.navigation.NavEntryScope
import io.github.fopwoc.mods.framework.ui.compose.navigation.NavHost
import io.github.fopwoc.mods.framework.ui.compose.navigation.entryProvider
import io.github.fopwoc.mods.framework.ui.compose.navigation.rememberNavBackStack
import io.github.fopwoc.mods.framework.ui.compose.runtime.collectAsStateWithLifecycle
import io.github.fopwoc.mods.testgui.client.gui.ui.TestGuiDestination
import io.github.fopwoc.mods.framework.ui.compose.unit.uu

@Composable
fun NavigationRoute(
    scope: NavEntryScope<TestGuiDestination>,
    viewModel: NavigationViewModel = viewModel(NavigationViewModel::class)
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val innerBackStack = rememberNavBackStack(
        NavigationInnerDestination.Home,
        keySaver = navigationInnerDestinationSaver
    )

    NavigationView(
        state = state,
        innerCurrentLabel = innerBackStack.currentKey?.label ?: "None",
        onPushSelf = {
            viewModel.recordSelfPush()
            scope.push(TestGuiDestination.Navigation)
        },
        onReplaceTop = {
            viewModel.recordOuterEvent("Replaced Navigation with Overview")
            scope.replaceTop(TestGuiDestination.Overview)
        },
        onPopToRoot = {
            viewModel.recordOuterEvent("Popped outer stack back to root")
            scope.popToRoot()
        },
        innerHost = {
            NavHost(
                backStack = innerBackStack,
                entryProvider = entryProvider {
                    entry<NavigationInnerDestination.Home>(retainSaveableState = true) {
                        InnerHome(
                            onPushDetail = { label ->
                                viewModel.recordInnerEvent("Inner push: $label")
                                push(NavigationInnerDestination.Detail(label))
                            }
                        )
                    }
                    entry<NavigationInnerDestination.Detail>(retainSaveableState = true) { detail ->
                        InnerDetail(
                            label = detail.label,
                            onPushSibling = {
                                val sibling = if (detail.label.endsWith("A")) "detail B" else "detail A"
                                viewModel.recordInnerEvent("Inner sibling push: $sibling")
                                push(NavigationInnerDestination.Detail(sibling))
                            },
                            onReplaceWithHome = {
                                viewModel.recordInnerEvent("Inner replaceTop -> home")
                                replaceTop(NavigationInnerDestination.Home)
                            },
                            onPopInner = {
                                viewModel.recordInnerEvent("Inner pop from ${detail.label}")
                                pop()
                            }
                        )
                    }
                }
            )
        }
    )
}

@Composable
private fun InnerHome(
    onPushDetail: (String) -> Unit
) {
    Panel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = VerticalArrangement.spacedBy(6.uu)
        ) {
            Text(
                text = "Inner Home",
                style = TextStyle(color = Color.rgb(red = 0xFF, green = 0xD5, blue = 0x4A))
            )
            Text(
                text = "Back from a detail route is consumed by this nested NavHost before the outer stack sees it.",
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(wrap = true)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = HorizontalArrangement.spacedBy(4.uu),
                verticalAlignment = VerticalAlignment.CENTER
            ) {
                Button(
                    text = "Push detail A",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onPushDetail("detail A")
                    }
                )
                Button(
                    text = "Push detail B",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onPushDetail("detail B")
                    }
                )
            }
        }
    }
}

@Composable
private fun InnerDetail(
    label: String,
    onPushSibling: () -> Unit,
    onReplaceWithHome: () -> Unit,
    onPopInner: () -> Unit
) {
    Panel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = VerticalArrangement.spacedBy(6.uu)
        ) {
            Text(
                text = "Inner Detail: $label",
                style = TextStyle(color = Color.rgb(red = 0x8F, green = 0xD0, blue = 0xFF))
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = HorizontalArrangement.spacedBy(4.uu),
                verticalAlignment = VerticalAlignment.CENTER
            ) {
                Button(
                    text = "Push sibling",
                    modifier = Modifier.weight(1f),
                    onClick = onPushSibling
                )
                Button(
                    text = "Replace home",
                    modifier = Modifier.weight(1f),
                    onClick = onReplaceWithHome
                )
                Button(
                    text = "Pop inner",
                    modifier = Modifier.weight(1f),
                    onClick = onPopInner
                )
            }
        }
    }
}

