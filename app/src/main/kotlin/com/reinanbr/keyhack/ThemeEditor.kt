package com.reinanbr.keyhack

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reinanbr.keyhack.theme.CustomTheme
import com.reinanbr.keyhack.theme.KeyboardColors
import com.reinanbr.keyhack.theme.KeyboardThemes

/**
 * The custom theme: a live preview, the five colors to pick, and built-in
 * themes to start from. Saved on every change; the keyboard picks it up
 * the next time it opens.
 */
@Composable
internal fun ThemeEditor(encoded: String, onChange: (String) -> Unit) {
    val start = if (isSystemInDarkTheme()) KeyboardThemes.DARK else KeyboardThemes.LIGHT
    val picks = CustomTheme.decode(encoded) ?: CustomTheme.picks(start)
    val colors = CustomTheme.colors(picks)
    var editing by remember { mutableStateOf<CustomTheme.Slot?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KeyboardPreview(colors)
        Text(stringResource(R.string.theme_start_from), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((label, base) in PRESETS) {
                OutlinedButton(onClick = { onChange(CustomTheme.encode(CustomTheme.picks(base))) }) {
                    Text(stringResource(label), maxLines = 1)
                }
            }
        }
        for (slot in CustomTheme.Slot.entries) {
            Row(
                Modifier.fillMaxWidth().clickable { editing = slot }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Swatch(picks[slot.ordinal])
                Text(stringResource(SLOT_LABELS.getValue(slot)), style = MaterialTheme.typography.bodyLarge)
                Text(hex(picks[slot.ordinal]), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (CustomTheme.contrast(colors.label, colors.key) < MIN_CONTRAST) {
            Text(
                stringResource(R.string.theme_low_contrast),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    editing?.let { slot ->
        ColorDialog(picks[slot.ordinal], onDismiss = { editing = null }) { color ->
            onChange(CustomTheme.encode(picks.toMutableList().also { it[slot.ordinal] = color }))
            editing = null
        }
    }
}

/** A few rows of keys in [c], like the keyboard. */
@Composable
private fun KeyboardPreview(c: KeyboardColors) {
    Column(
        Modifier.fillMaxWidth().background(Color(c.background), RoundedCornerShape(10.dp)).padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
            for (w in listOf("olá", "você", "amanhã")) {
                Text(w, Modifier.weight(1f), color = Color(c.label), fontSize = 13.sp, maxLines = 1)
            }
        }
        PreviewRow { "qwertyuiop".forEach { PreviewKey(it.toString(), c.key, c.label) } }
        PreviewRow { "asdfghjkl".forEach { PreviewKey(it.toString(), c.key, c.label) } }
        PreviewRow {
            PreviewKey("⇧", c.functionKey, c.label, 1.5f)
            "zxcvbnm".forEach { PreviewKey(it.toString(), c.key, c.label) }
            PreviewKey("⌫", c.functionKey, c.label, 1.5f)
        }
        PreviewRow {
            PreviewKey("?123", c.functionKey, c.label, 1.5f)
            PreviewKey(",", c.key, c.label)
            PreviewKey("", c.key, c.secondaryLabel, 5f)
            PreviewKey(".", c.key, c.label)
            PreviewKey("⏎", c.accentKey, c.onAccent, 1.5f)
        }
    }
}

@Composable
private fun PreviewRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().height(32.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), content = content)
}

@Composable
private fun RowScope.PreviewKey(label: String, bg: Int, fg: Int, weight: Float = 1f) {
    Box(
        Modifier.weight(weight).height(32.dp).background(Color(bg), RoundedCornerShape(5.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color(fg), fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun Swatch(color: Int, size: Int = 28, onClick: (() -> Unit)? = null) {
    Box(
        Modifier
            .size(size.dp)
            .background(Color(color), CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
    )
}

/** Hue, saturation and brightness sliders, a hex field and some ready colors. */
@Composable
private fun ColorDialog(initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val start = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var h by remember { mutableStateOf(start[0]) }
    var s by remember { mutableStateOf(start[1]) }
    var v by remember { mutableStateOf(start[2]) }
    val color = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
    var text by remember(color) { mutableStateOf(hex(color).removePrefix("#")) }

    fun set(c: Int) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(c, hsv)
        h = hsv[0]
        s = hsv[1]
        v = hsv[2]
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(color) }) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.fillMaxWidth().height(40.dp).background(Color(color), RoundedCornerShape(8.dp)))
                Text(stringResource(R.string.color_hue))
                Slider(value = h, onValueChange = { h = it }, valueRange = 0f..HUE_MAX)
                Text(stringResource(R.string.color_saturation))
                Slider(value = s, onValueChange = { s = it })
                Text(stringResource(R.string.color_brightness))
                Slider(value = v, onValueChange = { v = it })
                OutlinedTextField(
                    value = text,
                    onValueChange = { t ->
                        text = t.removePrefix("#").take(HEX_DIGITS)
                        CustomTheme.parseColor(text)?.let(::set)
                    },
                    label = { Text("#RRGGBB") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                for (row in SWATCHES.chunked(SWATCHES_PER_ROW)) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        for (c in row) Swatch(c.toInt(), size = 26) { set(c.toInt()) }
                    }
                }
            }
        },
    )
}

private fun hex(color: Int) = "#%06X".format(color and RGB_MASK)

private const val RGB_MASK = 0xFFFFFF

private const val MIN_CONTRAST = 3.0
private const val HUE_MAX = 360f
private const val HEX_DIGITS = 6
private const val SWATCHES_PER_ROW = 8

private val PRESETS =
    listOf(
        R.string.theme_light to KeyboardThemes.LIGHT,
        R.string.theme_dark to KeyboardThemes.DARK,
        R.string.theme_amoled to KeyboardThemes.AMOLED,
        R.string.theme_ios to KeyboardThemes.IOS_LIGHT,
    )

private val SLOT_LABELS =
    mapOf(
        CustomTheme.Slot.BACKGROUND to R.string.theme_slot_background,
        CustomTheme.Slot.KEY to R.string.theme_slot_key,
        CustomTheme.Slot.FUNCTION_KEY to R.string.theme_slot_function,
        CustomTheme.Slot.LABEL to R.string.theme_slot_label,
        CustomTheme.Slot.ACCENT to R.string.theme_slot_accent,
    )

private val SWATCHES =
    listOf(
        0xFF000000, 0xFF1F1F21, 0xFF3B3C3F, 0xFF808080, 0xFFCBD0D6, 0xFFE8EAED, 0xFFFFFFFF, 0xFF263238,
        0xFFE53935, 0xFFFF7043, 0xFFFFC107, 0xFF8BC34A, 0xFF26A69A, 0xFF1A73E8, 0xFF7E57C2, 0xFFEC407A,
        0xFF3E2723, 0xFF1B5E20, 0xFF0D47A1, 0xFF4A148C, 0xFFFFF8E1, 0xFFE8F5E9, 0xFFE3F2FD, 0xFFFCE4EC,
    )
