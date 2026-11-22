package com.reinanbr.keyhack

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reinanbr.keyhack.devtools.Snippet
import com.reinanbr.keyhack.devtools.SnippetList
import com.reinanbr.keyhack.devtools.SnippetStore

/** Snippets: saved texts the keyboard offers when their shortcut is typed. */
@Composable
internal fun SnippetsSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { SnippetStore.get(context) }
    var list by remember { mutableStateOf(store.all()) }
    DisposableEffect(store) {
        val listener: () -> Unit = { list = store.all() }
        store.addListener(listener)
        onDispose { store.removeListener(listener) }
    }
    // null: closed; a snippet with id 0: adding a new one.
    var editing by remember { mutableStateOf<Snippet?>(null) }
    Section(stringResource(R.string.section_snippets), modifier) {
        Text(stringResource(R.string.snippets_summary), style = MaterialTheme.typography.bodySmall)
        for (s in list) {
            Row(
                Modifier.fillMaxWidth().clickable { editing = s }.padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    s.trigger.ifEmpty { "—" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    s.text.replace('\n', ' '),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        OutlinedButton(onClick = { editing = Snippet(0, "", "") }) { Text(stringResource(R.string.snippet_add)) }
    }
    editing?.let { s ->
        SnippetDialog(
            snippet = s,
            onSave = { trigger, text ->
                if (s.id == 0L) store.add(trigger, text) else store.update(s.id, trigger, text)
                editing = null
            },
            onDelete = {
                store.remove(s.id)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun SnippetDialog(
    snippet: Snippet,
    onSave: (String, String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var trigger by remember { mutableStateOf(snippet.trigger) }
    var text by remember { mutableStateOf(snippet.text) }
    val taken = remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onSave(trigger, text) }, enabled = text.isNotBlank() && !taken.value) {
                Text(stringResource(R.string.snippet_save))
            }
        },
        dismissButton = {
            Row {
                if (snippet.id != 0L) {
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.snippet_delete)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val store = SnippetStore.get(LocalContext.current)
                OutlinedTextField(
                    value = trigger,
                    onValueChange = {
                        trigger = it
                        val other = store.forTrigger(it)
                        taken.value = other != null && other.id != snippet.id
                    },
                    label = { Text(stringResource(R.string.snippet_trigger)) },
                    singleLine = true,
                    isError = taken.value,
                    supportingText = {
                        val key = SnippetList.normalizeTrigger(trigger)
                        Text(
                            when {
                                taken.value -> stringResource(R.string.snippet_trigger_taken)
                                key.isEmpty() -> stringResource(R.string.snippet_trigger_help)
                                else -> stringResource(R.string.snippet_trigger_preview, key)
                            },
                        )
                    },
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= SnippetList.MAX_CHARS) text = it },
                    label = { Text(stringResource(R.string.snippet_text)) },
                    minLines = 3,
                    maxLines = MAX_LINES,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

private const val MAX_LINES = 8
