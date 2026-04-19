package io.github.fopwoc.mods.testgui.client.gui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
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
import io.github.fopwoc.mods.framework.ui.compose.minecraft.ComposeBackgroundStyle
import io.github.fopwoc.mods.framework.ui.compose.minecraft.ComposeGuiScreen
import io.github.fopwoc.mods.framework.ui.compose.runtime.LocalComposeGuiScreen
import io.github.fopwoc.mods.framework.ui.compose.unit.uu

@SideOnly(Side.CLIENT)
class ViewModelLifecycleProofScreen : ComposeGuiScreen() {
    override val composeBackgroundStyle: ComposeBackgroundStyle = ComposeBackgroundStyle.VanillaDefault

    override fun doesGuiPauseGame(): Boolean = false

    @Composable
    override fun Content() {
        val screen = LocalComposeGuiScreen.current
        val lifecycleViewModel: ViewModelLifecycleProofViewModel = viewModel(ViewModelLifecycleProofViewModel::class)

        Panel(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.uu)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = VerticalArrangement.spacedBy(8.uu),
                horizontalAlignment = HorizontalAlignment.CENTER
            ) {
                Text(
                    text = "ViewModel lifecycle proof",
                    modifier = Modifier.fillMaxWidth(),
                    style = TextStyle(
                        color = Color.rgb(red = 0xFF, green = 0xFF, blue = 0xFF),
                        alignment = HorizontalAlignment.CENTER
                    )
                )
                Text(
                    text = "Close this child screen or open a fresh copy to watch the real AndroidX ViewModel get cleared and recreated. The creation sequence changes, and the local state resets because this demo is using plain screen-scoped ViewModel state only.",
                    modifier = Modifier.fillMaxWidth(),
                    style = TextStyle(
                        color = Color.rgb(red = 0xD8, green = 0xD8, blue = 0xD8),
                        alignment = HorizontalAlignment.CENTER,
                        wrap = true
                    )
                )

                Panel(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = VerticalArrangement.spacedBy(5.uu),
                        horizontalAlignment = HorizontalAlignment.CENTER
                    ) {
                        Text(
                            text = "Current ViewModel creation #${lifecycleViewModel.creationSequence}",
                            modifier = Modifier.fillMaxWidth(),
                            style = TextStyle(
                                color = Color(0xFF7BE0FF).copy(alpha = lifecycleViewModel.bannerAlpha),
                                alignment = HorizontalAlignment.CENTER,
                                wrap = true
                            )
                        )
                        Text(
                            text = "Last cleared creation: ${lifecycleViewModel.previousClearedInstanceId?.toString() ?: "none yet"}",
                            modifier = Modifier.fillMaxWidth(),
                            style = TextStyle(
                                color = Color.rgb(red = 0xF6, green = 0xD9, blue = 0x8E),
                                alignment = HorizontalAlignment.CENTER,
                                wrap = true
                            )
                        )
                        Text(
                            text = lifecycleViewModel.note,
                            modifier = Modifier.fillMaxWidth(),
                            style = TextStyle(
                                color = Color.rgb(red = 0xD6, green = 0xE8, blue = 0xF2),
                                alignment = HorizontalAlignment.CENTER,
                                wrap = true
                            )
                        )
                        Text(
                            text = "Tap count ${lifecycleViewModel.tapCount} · open a fresh child copy to get a new ViewModel creation number and a reset counter.",
                            modifier = Modifier.fillMaxWidth(),
                            style = TextStyle(
                                color = Color.rgb(red = 0xC7, green = 0xD9, blue = 0xF4),
                                alignment = HorizontalAlignment.CENTER,
                                wrap = true
                            )
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = HorizontalArrangement.spacedBy(6.uu),
                    verticalAlignment = VerticalAlignment.CENTER
                ) {
                    Button(
                        text = "Tap ViewModel",
                        modifier = Modifier.width(88.uu),
                        onClick = {
                            lifecycleViewModel.recordTap()
                        }
                    )
                    Button(
                        text = "Tint",
                        modifier = Modifier.width(88.uu),
                        onClick = {
                            lifecycleViewModel.toggleBannerAlpha()
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = HorizontalArrangement.spacedBy(6.uu),
                    verticalAlignment = VerticalAlignment.CENTER
                ) {
                    Button(
                        text = "Open fresh child",
                        modifier = Modifier.width(110.uu),
                        onClick = {
                            screen.mc.displayGuiScreen(ViewModelLifecycleProofScreen())
                        }
                    )
                    Button(
                        text = "Back",
                        modifier = Modifier.width(88.uu),
                        onClick = {
                            screen.mc.displayGuiScreen(TestGuiScreen())
                        }
                    )
                    Button(
                        text = "Close",
                        modifier = Modifier.width(88.uu),
                        onClick = {
                            screen.mc.displayGuiScreen(null)
                        }
                    )
                }
            }
        }
    }
}

class ViewModelLifecycleProofViewModel : ViewModel() {
    val creationSequence: Int = nextCreationSequence++

    var tapCount by mutableStateOf(0)
        private set

    var bannerAlpha by mutableStateOf(120)
        private set

    var note by mutableStateOf(
        "This child screen owns its own ViewModelStore. Closing it clears the old ViewModel, and opening it again creates a fresh instance with reset local state."
    )
        private set

    val previousClearedInstanceId: Int?
        get() = lastClearedInstanceId

    fun recordTap() {
        tapCount += 1
        note = "Tap #$tapCount lives in the child ViewModel. Open the child again to prove the old instance was cleared and a new one was created."
    }

    fun toggleBannerAlpha() {
        bannerAlpha = if (bannerAlpha == 120) 220 else 120
        note = "Banner alpha toggled on creation #$creationSequence. Opening the child again resets this local value because the demo is not restoring screen state right now."
    }

    override fun onCleared() {
        lastClearedInstanceId = creationSequence
    }


    private companion object {
        var nextCreationSequence: Int = 1
        var lastClearedInstanceId: Int? = null
    }
}
