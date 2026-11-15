package com.reinanbr.keyhack

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** Launcher activity: onboarding that walks the user through enabling the IME. */
class MainActivity : ComponentActivity() {
    private var status by mutableStateOf(ImeStatus(enabled = false, selected = false))

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            KeyHackTheme {
                Onboarding(
                    status = status,
                    onEnable = { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) },
                    onSelect = { getSystemService(InputMethodManager::class.java).showInputMethodPicker() },
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

@Composable
private fun Onboarding(status: ImeStatus, onEnable: () -> Unit, onSelect: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
            Modifier
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.onboarding_privacy), style = MaterialTheme.typography.bodyMedium)
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
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.step_try_title), style = MaterialTheme.typography.titleMedium)
                    var text by rememberSaveable { mutableStateOf("") }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.step_try_hint)) },
                    )
                }
            }
        }
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
