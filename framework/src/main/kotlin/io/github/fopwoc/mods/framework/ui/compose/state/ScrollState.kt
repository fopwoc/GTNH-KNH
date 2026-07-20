package io.github.fopwoc.mods.framework.ui.compose.state

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.Saver

@Stable
class ScrollState(initial: Int = 0) {
    var value by mutableStateOf(initial.coerceAtLeast(0))
        private set

    var maxValue by mutableStateOf(0)
        private set

    fun scrollBy(delta: Int): Boolean {
        if (delta == 0) {
            return false
        }

        val updatedValue = (value + delta).coerceIn(0, maxValue)
        if (updatedValue == value) {
            return false
        }

        value = updatedValue
        return true
    }

    fun scrollTo(offset: Int): Boolean {
        val updatedValue = offset.coerceIn(0, maxValue)
        if (updatedValue == value) {
            return false
        }

        value = updatedValue
        return true
    }

    internal fun updateMaxValue(maxValue: Int) {
        val coercedMaxValue = maxValue.coerceAtLeast(0)
        if (this.maxValue != coercedMaxValue) {
            this.maxValue = coercedMaxValue
        }
        if (value > coercedMaxValue) {
            value = coercedMaxValue
        }
    }

    companion object {
        val Saver: Saver<ScrollState, Int> = Saver(
            save = { state -> state.value },
            restore = { savedValue -> ScrollState(initial = savedValue) }
        )
    }
}

