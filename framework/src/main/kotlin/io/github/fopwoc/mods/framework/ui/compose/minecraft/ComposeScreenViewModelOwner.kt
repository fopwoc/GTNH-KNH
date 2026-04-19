package io.github.fopwoc.mods.framework.ui.compose.minecraft

import io.github.fopwoc.mods.framework.ui.compose.runtime.ComposeViewModelOwner

internal class ComposeScreenViewModelOwner : ComposeViewModelOwner() {
	fun attachToScreen() {
		onCreate()
	}

	fun showOnScreen() {
		onStart()
		onResume()
	}

	fun resumeOnScreen() {
		onResume()
	}
}

