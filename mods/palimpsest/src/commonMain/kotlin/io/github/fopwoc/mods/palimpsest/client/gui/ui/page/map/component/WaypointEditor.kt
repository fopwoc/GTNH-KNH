package io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.fopwoc.mods.framework.minecraft.ItemId
import io.github.fopwoc.mods.framework.ui.compose.component.vanilla.Button
import io.github.fopwoc.mods.framework.ui.compose.component.vanilla.Checkbox
import io.github.fopwoc.mods.framework.ui.compose.component.vanilla.TextField
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.IconItem
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.runtime.rememberScrollState
import io.github.fopwoc.mods.framework.ui.compose.state.TextFieldState
import io.github.fopwoc.mods.framework.ui.compose.unit.uu
import io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map.WaypointEditorModel

/** Edits one waypoint while the terrain map remains visible behind it. */
@Composable
internal fun WaypointEditor(
    model: WaypointEditorModel,
    modifier: Modifier,
    heldItemId: () -> ItemId?,
    onSave: (String, Int, Int, Int, ItemId, Boolean) -> Boolean,
    onDelete: () -> Boolean,
    onClose: () -> Unit,
) {
    val name = remember { TextFieldState(model.name) }
    val x = remember { TextFieldState(model.x.toString()) }
    val y = remember { TextFieldState(model.y.toString()) }
    val z = remember { TextFieldState(model.z.toString()) }
    var icon by remember { mutableStateOf(model.icon) }
    var tracked by remember { mutableStateOf(model.tracked) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier =
            modifier
                .background(Color(0xE8181A25))
                .border(Color(0xFF59637A))
                .padding(6.uu)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = VerticalArrangement.spacedBy(5.uu),
    ) {
        Text(if (model.isNew) "New waypoint" else "Edit waypoint")
        Text("Name")
        TextField(name, modifier = Modifier.fillMaxWidth())
        Text("X")
        TextField(x, modifier = Modifier.fillMaxWidth())
        Text("Y")
        TextField(y, modifier = Modifier.fillMaxWidth())
        Text("Z")
        TextField(z, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = HorizontalArrangement.spacedBy(5.uu)) {
            IconItem(icon)
            Text("Icon")
        }
        Button("Use held item", modifier = Modifier.fillMaxWidth()) {
            heldItemId()?.let { icon = it } ?: run { error = "Hold an item first" }
        }
        Checkbox("Show in world", tracked) { tracked = it }
        error?.let { Text(it) }
        Row(horizontalArrangement = HorizontalArrangement.spacedBy(4.uu)) {
            Button(
                "Save",
                enabled =
                    name.text.isNotBlank() &&
                        x.text.toIntOrNull() != null &&
                        y.text.toIntOrNull() != null &&
                        z.text.toIntOrNull() != null,
            ) {
                val worldX = x.text.toIntOrNull() ?: return@Button
                val height = y.text.toIntOrNull() ?: return@Button
                val worldZ = z.text.toIntOrNull() ?: return@Button
                if (!onSave(name.text, worldX, height, worldZ, icon, tracked))
                    error = "Could not save waypoint"
            }
            if (!model.isNew) {
                Button("Delete") {
                    if (!onDelete()) error = "Could not delete waypoint"
                }
            }
            Button("Cancel") { onClose() }
        }
    }
}
