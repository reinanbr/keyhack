package com.reinanbr.keyhack

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.reinanbr.keyhack.data.KeyHackSettings
import com.reinanbr.keyhack.stickers.StickerStore
import java.io.File
import kotlin.math.roundToInt
import com.reinanbr.keyhack.data.KeyHackSettings.Companion as K

/** Launcher activity: setup steps, then every KeyHack setting. */
class MainActivity : ComponentActivity() {
    private var status by mutableStateOf(ImeStatus(enabled = false, selected = false))
    private val stickers by lazy { StickerStore(this) }
    private var stickerCount by mutableStateOf(0)

    // The system photo picker: no storage permission, the user picks each image.
    private val pickImages =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_STICKER_IMPORT)) { uris ->
            if (uris.isNotEmpty()) importStickers(uris)
        }

    private fun openStickerPicker() {
        pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun importStickers(uris: List<Uri>) {
        Toast.makeText(this, R.string.stickers_importing, Toast.LENGTH_SHORT).show()
        Thread({
            val ok = uris.count { stickers.import(it).isSuccess }
            runOnUiThread {
                stickerCount = stickers.list().size
                val failed = uris.size - ok
                val text =
                    if (failed == 0) {
                        resources.getQuantityString(R.plurals.stickers_imported, ok, ok)
                    } else {
                        getString(R.string.stickers_imported_some_failed, ok, failed)
                    }
                Toast.makeText(this, text, Toast.LENGTH_LONG).show()
            }
        }, "keyhack-sticker-import").start()
    }

    /** The keyboard's ＋ button opens this screen asking for an import. */
    private fun handleImportRequest(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_IMPORT_STICKERS, false) == true) {
            intent.removeExtra(EXTRA_IMPORT_STICKERS)
            openStickerPicker()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleImportRequest(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val settings = KeyHackSettings(this)
        stickerCount = stickers.list().size
        if (savedInstanceState == null) handleImportRequest(intent)
        setContent {
            KeyHackTheme {
                SettingsScreen(
                    status = status,
                    settings = settings,
                    stickerCount = stickerCount,
                    onImportStickers = ::openStickerPicker,
                    onDeleteStickers = {
                        stickers.deleteAll()
                        stickerCount = 0
                    },
                    onEnable = { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) },
                    onSelect = { getSystemService(InputMethodManager::class.java).showInputMethodPicker() },
                    onClearLearning = {
                        File(noBackupFilesDir, "learned.tsv").delete()
                        settings.edit { putLong(LEARNING_CLEAR, System.currentTimeMillis()) }
                        Toast.makeText(this, R.string.pref_clear_learning_done, Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        status = ImeStatus.read(this)
    }

    // The input method picker is a dialog: it changes focus, not lifecycle.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) status = ImeStatus.read(this)
    }

    companion object {
        /** Written when the user clears learned words; the IME listens for it. */
        const val LEARNING_CLEAR = "learning_clear"

        /** Sent by the keyboard's sticker panel (KeyHackImeService.EXTRA_IMPORT_STICKERS). */
        const val EXTRA_IMPORT_STICKERS = "com.reinanbr.keyhack.IMPORT_STICKERS"
        const val MAX_STICKER_IMPORT = 30
    }
}

@Composable
private fun KeyHackTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

            dark -> darkColorScheme()

            else -> lightColorScheme()
        }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** A preference bound to SharedPreferences, recomposed on change. */
private class Prefs(val settings: KeyHackSettings) {
    val version = mutableStateOf(0)

    fun bool(key: String, default: Boolean): Boolean {
        version.value
        return settings.prefs.getBoolean(key, default)
    }

    fun string(key: String, default: String): String {
        version.value
        return settings.prefs.getString(key, default) ?: default
    }

    fun int(key: String, default: Int): Int {
        version.value
        return settings.prefs.getInt(key, default)
    }

    fun set(block: android.content.SharedPreferences.Editor.() -> Unit) {
        settings.edit(block)
        version.value++
    }
}

@Composable
private fun SettingsScreen(
    status: ImeStatus,
    settings: KeyHackSettings,
    stickerCount: Int,
    onImportStickers: () -> Unit,
    onDeleteStickers: () -> Unit,
    onEnable: () -> Unit,
    onSelect: () -> Unit,
    onClearLearning: () -> Unit,
) {
    val p = remember { Prefs(settings) }
    // The keyboard changes prefs too (the DEV button): keep the screen in sync.
    DisposableEffect(settings) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> p.version.value++ }
        settings.prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { settings.prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
            Modifier
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.onboarding_privacy), style = MaterialTheme.typography.bodyMedium)
            if (!status.enabled || !status.selected) {
                Step(
                    title = stringResource(R.string.step_enable_title),
                    body = stringResource(R.string.step_enable_body),
                    action = stringResource(R.string.step_enable_action),
                    done = status.enabled,
                    onClick = onEnable,
                )
                Step(
                    title = stringResource(R.string.step_select_title),
                    body = stringResource(R.string.step_select_body),
                    action = stringResource(R.string.step_select_action),
                    done = status.selected,
                    enabled = status.enabled,
                    onClick = onSelect,
                )
            }
            TryIt()
            Section(stringResource(R.string.help_title)) {
                Text(stringResource(R.string.help_body), style = MaterialTheme.typography.bodyMedium)
            }
            KeyboardSection(p)
            TypingSection(p, onClearLearning)
            PcSection(p)
            GifSection(p)
            StickerSection(stickerCount, onImportStickers, onDeleteStickers)
            Section(stringResource(R.string.section_privacy)) {
                Text(stringResource(R.string.privacy_body), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun TryIt() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.step_try_title), style = MaterialTheme.typography.titleMedium)
            var text by rememberSaveable { mutableStateOf("") }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.step_try_hint)) },
                minLines = 2,
            )
        }
    }
}

@Composable
private fun ModeChoice(p: Prefs) {
    val mode = p.string(K.MODE, K.MODE_SIMPLE)
    Choice(
        stringResource(R.string.pref_mode),
        mode,
        mapOf(
            K.MODE_SIMPLE to stringResource(R.string.mode_simple),
            K.MODE_ADVANCED to stringResource(R.string.mode_advanced),
        ),
    ) { v -> p.set { putString(K.MODE, v) } }
    Text(stringResource(R.string.pref_mode_help), style = MaterialTheme.typography.bodySmall)
    if (mode != K.MODE_ADVANCED) return
    val devRow = p.bool(K.DEV_ROW, true)
    SwitchRow(stringResource(R.string.pref_dev_row), stringResource(R.string.pref_dev_row_summary), devRow) {
        p.set { putBoolean(K.DEV_ROW, it) }
    }
    if (devRow) {
        TextPref(
            stringResource(R.string.pref_dev_row_keys),
            p.string(K.DEV_ROW_KEYS, K.DEFAULT_DEV_ROW),
            stringResource(R.string.pref_dev_row_keys_help),
        ) { v -> p.set { putString(K.DEV_ROW_KEYS, v.ifBlank { K.DEFAULT_DEV_ROW }) } }
    }
}

@Composable
private fun KeyboardSection(p: Prefs) {
    Section(stringResource(R.string.section_keyboard)) {
        ModeChoice(p)
        val layoutLabels =
            mapOf(
                "qwerty" to R.string.layout_qwerty,
                "abnt2" to R.string.layout_abnt2,
                "azerty" to R.string.layout_azerty,
                "dvorak" to R.string.layout_dvorak,
                "colemak" to R.string.layout_colemak,
                "pc" to R.string.layout_pc,
            ).mapValues { stringResource(it.value) }
        Choice(
            stringResource(R.string.pref_layout),
            p.string(K.LAYOUT, "qwerty"),
            layoutLabels,
        ) { v -> p.set { putString(K.LAYOUT, v) } }
        SwitchRow(
            stringResource(R.string.pref_number_row),
            stringResource(R.string.pref_number_row_summary),
            p.bool(K.NUMBER_ROW, true),
        ) {
            p.set { putBoolean(K.NUMBER_ROW, it) }
        }
        SwitchRow(
            stringResource(R.string.pref_key_hints),
            null,
            p.bool(K.KEY_HINTS, true),
        ) { p.set { putBoolean(K.KEY_HINTS, it) } }
        SliderRow(
            stringResource(R.string.pref_height),
            p.int(K.HEIGHT, K.DEFAULT_HEIGHT),
            70..140,
            "%",
        ) { p.set { putInt(K.HEIGHT, it) } }
        SliderRow(
            stringResource(R.string.pref_long_press),
            p.int(K.LONG_PRESS, K.DEFAULT_LONG_PRESS),
            150..700,
            " ms",
        ) {
            p.set { putInt(K.LONG_PRESS, it) }
        }
        val themes =
            mapOf(
                "system" to R.string.theme_system,
                "dynamic" to R.string.theme_dynamic,
                "light" to R.string.theme_light,
                "dark" to R.string.theme_dark,
                "amoled" to R.string.theme_amoled,
            ).mapValues { stringResource(it.value) }
        Choice(
            stringResource(R.string.pref_theme),
            p.string(K.THEME, "system"),
            themes,
        ) { v -> p.set { putString(K.THEME, v) } }
        FeedbackRows(p)
    }
}

/** Key preview, one-handed mode, vibration and sound. */
@Composable
private fun FeedbackRows(p: Prefs) {
    SwitchRow(
        stringResource(R.string.pref_key_preview),
        null,
        p.bool(K.KEY_PREVIEW, true),
    ) { p.set { putBoolean(K.KEY_PREVIEW, it) } }
    Choice(
        stringResource(R.string.pref_one_handed),
        p.string(K.ONE_HANDED, K.ONE_HANDED_OFF),
        mapOf(
            K.ONE_HANDED_OFF to R.string.one_handed_off,
            "left" to R.string.one_handed_left,
            "right" to R.string.one_handed_right,
        ).mapValues { stringResource(it.value) },
    ) { v -> p.set { putString(K.ONE_HANDED, v) } }
    SwitchRow(
        stringResource(R.string.pref_haptics),
        null,
        p.bool(K.HAPTICS, true),
    ) { p.set { putBoolean(K.HAPTICS, it) } }
    SwitchRow(
        stringResource(R.string.pref_sound),
        null,
        p.bool(K.SOUND, false),
    ) { p.set { putBoolean(K.SOUND, it) } }
}

@Composable
private fun TypingSection(p: Prefs, onClearLearning: () -> Unit) {
    Section(stringResource(R.string.section_typing)) {
        SwitchRow(
            stringResource(R.string.pref_suggestions),
            null,
            p.bool(K.SUGGESTIONS, true),
        ) { p.set { putBoolean(K.SUGGESTIONS, it) } }
        SwitchRow(
            stringResource(R.string.pref_autocorrect),
            stringResource(R.string.pref_autocorrect_summary),
            p.bool(K.AUTOCORRECT, true),
        ) {
            p.set { putBoolean(K.AUTOCORRECT, it) }
        }
        SwitchRow(
            stringResource(R.string.pref_auto_caps),
            null,
            p.bool(K.AUTO_CAPS, true),
        ) { p.set { putBoolean(K.AUTO_CAPS, it) } }
        SwitchRow(
            stringResource(R.string.pref_double_space),
            null,
            p.bool(K.DOUBLE_SPACE, true),
        ) { p.set { putBoolean(K.DOUBLE_SPACE, it) } }
        SwitchRow(
            stringResource(R.string.pref_learning),
            stringResource(R.string.pref_learning_summary),
            p.bool(K.LEARNING, true),
        ) {
            p.set { putBoolean(K.LEARNING, it) }
        }
        Text(stringResource(R.string.pref_languages), style = MaterialTheme.typography.bodyLarge)
        val langs = p.string(K.LANGUAGES, "en,pt").split(',').filter { it.isNotBlank() }
        for ((code, label) in listOf("en" to R.string.lang_en, "pt" to R.string.lang_pt)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = code in langs,
                    onCheckedChange = { on ->
                        val next = if (on) (langs + code).distinct() else langs - code
                        if (next.isNotEmpty()) p.set { putString(K.LANGUAGES, next.joinToString(",")) }
                    },
                )
                Text(stringResource(label))
            }
        }
        OutlinedButton(onClick = onClearLearning) { Text(stringResource(R.string.pref_clear_learning)) }
    }
}

@Composable
private fun PcSection(p: Prefs) {
    Section(stringResource(R.string.section_pc)) {
        Text(stringResource(R.string.pc_help), style = MaterialTheme.typography.bodyMedium)
        TextPref(
            stringResource(R.string.pref_pc_apps),
            p.string(K.PC_APPS, K.DEFAULT_PC_APPS),
            stringResource(R.string.pref_pc_apps_help),
        ) { v ->
            p.set { putString(K.PC_APPS, v) }
        }
        SwitchRow(stringResource(R.string.pref_pc_no_autocorrect), null, p.bool(K.PC_NO_AUTOCORRECT, true)) {
            p.set { putBoolean(K.PC_NO_AUTOCORRECT, it) }
        }
    }
}

@Composable
private fun StickerSection(count: Int, onImport: () -> Unit, onDeleteAll: () -> Unit) {
    Section(stringResource(R.string.section_stickers)) {
        Text(stringResource(R.string.stickers_body), style = MaterialTheme.typography.bodyMedium)
        Text(
            LocalContext.current.resources.getQuantityString(R.plurals.stickers_count, count, count),
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onImport) { Text(stringResource(R.string.stickers_import)) }
            if (count > 0) OutlinedButton(onClick = onDeleteAll) { Text(stringResource(R.string.stickers_delete_all)) }
        }
    }
}

@Composable
private fun GifSection(p: Prefs) {
    Section(stringResource(R.string.section_gif)) {
        val enabled = p.bool(K.GIF_ENABLED, true)
        SwitchRow(
            stringResource(R.string.pref_gif_enabled),
            stringResource(R.string.pref_gif_enabled_summary),
            enabled,
        ) {
            p.set { putBoolean(K.GIF_ENABLED, it) }
        }
        if (!enabled) return@Section
        // Giphy is the only provider left (Tenor's API closed on 2026-06-30).
        val provider = "giphy"
        TextPref(
            stringResource(R.string.pref_gif_key),
            p.string(K.GIF_KEY_PREFIX + provider, ""),
            stringResource(R.string.pref_gif_key_help),
            secret = true,
        ) { v -> p.set { putString(K.GIF_KEY_PREFIX + provider, v.trim()) } }
        Text(stringResource(R.string.pref_gif_privacy), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun SwitchRow(title: String, summary: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (summary != null) Text(summary, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(title: String, value: Int, range: IntRange, unit: String, onChange: (Int) -> Unit) {
    var v by remember(value) { mutableStateOf(value.toFloat()) }
    Column {
        Text("$title: ${v.roundToInt()}$unit", style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = v,
            onValueChange = { v = it },
            onValueChangeFinished = { onChange(v.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
        )
    }
}

@Composable
private fun Choice(title: String, value: String, options: Map<String, String>, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                options[value] ?: value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column {
                    for ((k, label) in options) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                onChange(k)
                                open = false
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = k == value, onClick = {
                                onChange(k)
                                open = false
                            })
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { open = false },
                ) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun TextPref(title: String, value: String, help: String?, secret: Boolean = false, onChange: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    var reveal by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                onChange(it)
            },
            label = { Text(title) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = secret,
            visualTransformation = if (secret && !reveal) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions =
            if (secret) {
                KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false,
                )
            } else {
                KeyboardOptions.Default
            },
            trailingIcon =
            if (secret) {
                {
                    TextButton(
                        onClick = { reveal = !reveal },
                    ) { Text(stringResource(if (reveal) R.string.hide else R.string.show)) }
                }
            } else {
                null
            },
        )
        if (help != null) Text(help, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Step(
    title: String,
    body: String,
    action: String,
    done: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            if (done) {
                Text("✓ " + stringResource(R.string.step_done), color = MaterialTheme.colorScheme.primary)
            } else {
                Button(onClick = onClick, enabled = enabled) { Text(action) }
            }
        }
    }
}
