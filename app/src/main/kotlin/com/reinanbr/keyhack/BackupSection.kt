package com.reinanbr.keyhack

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.reinanbr.keyhack.backup.BackupFormat
import com.reinanbr.keyhack.backup.Backups
import com.reinanbr.keyhack.devtools.SnippetStore
import com.reinanbr.keyhack.security.PasswordCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.GeneralSecurityException
import java.time.LocalDate

/**
 * Backup (ADR 0007): settings, learned words, snippets and emoji recents in
 * one file, encrypted with a password the user picks. Files go through the
 * system file picker, so no storage permission is needed.
 */
@Composable
internal fun BackupSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var askPassword by remember { mutableStateOf(false) }
    var restoreFrom by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }

    // Chosen in the password dialog, used once the file picker returns, then wiped.
    var exportPassword by remember { mutableStateOf<CharArray?>(null) }
    val createFile =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MIME)) { uri ->
            val password = exportPassword ?: return@rememberLauncherForActivityResult
            exportPassword = null
            if (uri == null) {
                password.fill(' ')
                return@rememberLauncherForActivityResult
            }
            busy = true
            scope.launch {
                val ok = export(context, uri, password)
                busy = false
                toast(context, if (ok) R.string.backup_exported else R.string.backup_export_failed)
            }
        }
    val openFile =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) restoreFrom = uri
        }

    Section(stringResource(R.string.backup_title)) {
        Text(stringResource(R.string.backup_body), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { askPassword = true }, enabled = !busy) {
                Text(stringResource(R.string.backup_export))
            }
            OutlinedButton(onClick = { openFile.launch(arrayOf("*/*")) }, enabled = !busy) {
                Text(stringResource(R.string.backup_restore))
            }
            if (busy) CircularProgressIndicator(Modifier.size(24.dp))
        }
    }
    if (askPassword) {
        ExportDialog(
            onDismiss = { askPassword = false },
            onConfirm = { password ->
                askPassword = false
                exportPassword = password
                createFile.launch("keyhack-${LocalDate.now()}.$EXTENSION")
            },
        )
    }
    restoreFrom?.let { uri ->
        RestoreDialog(uri, onDone = { restoreFrom = null })
    }
}

@Composable
private fun ExportDialog(onDismiss: () -> Unit, onConfirm: (CharArray) -> Unit) {
    var password by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    val long = password.length >= PasswordCipher.MIN_PASSWORD
    val same = password == again
    val mismatch = again.isNotEmpty() && !same
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_password_title)) },
        confirmButton = {
            TextButton(onClick = { onConfirm(password.toCharArray()) }, enabled = long && same) {
                Text(stringResource(R.string.backup_choose_file))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.backup_password_help, PasswordCipher.MIN_PASSWORD),
                    style = MaterialTheme.typography.bodyMedium,
                )
                PasswordField(password, stringResource(R.string.backup_password)) { password = it }
                PasswordField(
                    again,
                    stringResource(R.string.backup_password_again),
                    error = if (mismatch) stringResource(R.string.backup_password_mismatch) else null,
                ) { again = it }
            }
        },
    )
}

@Composable
private fun RestoreDialog(uri: Uri, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDone() },
        title = { Text(stringResource(R.string.backup_restore)) },
        confirmButton = {
            TextButton(
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        error = restore(context, uri, password.toCharArray())
                        busy = false
                        if (error == null) onDone()
                    }
                },
                enabled = password.isNotEmpty() && !busy,
            ) { Text(stringResource(R.string.backup_restore_action)) }
        },
        dismissButton = {
            TextButton(onClick = onDone, enabled = !busy) { Text(stringResource(android.R.string.cancel)) }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.backup_restore_warning), style = MaterialTheme.typography.bodyMedium)
                PasswordField(password, stringResource(R.string.backup_password), error?.let { stringResource(it) }) {
                    password = it
                    error = null
                }
                if (busy) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            }
        },
    )
}

@Composable
private fun PasswordField(value: String, label: String, error: String? = null, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Writes the backup to [uri]; on failure the half-written file is deleted. Wipes [password]. */
private suspend fun export(context: Context, uri: Uri, password: CharArray): Boolean {
    val snippets = SnippetStore.get(context).encode()
    val version = appVersion(context)
    return withContext(Dispatchers.IO) {
        try {
            val out = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("no output stream")
            out.use { Backups.export(context, snippets, password, version, it) }
            true
        } catch (_: IOException) {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
            false
        } finally {
            password.fill(' ')
        }
    }
}

/** Reads and applies the backup at [uri]; returns an error message id, or null when it worked. Wipes [password]. */
private suspend fun restore(context: Context, uri: Uri, password: CharArray): Int? {
    val backup =
        try {
            withContext(Dispatchers.IO) {
                try {
                    val input = context.contentResolver.openInputStream(uri) ?: throw IOException("no input stream")
                    input.use { Backups.read(it, password) }
                } finally {
                    password.fill(' ')
                }
            }
        } catch (_: PasswordCipher.WrongPassword) {
            return R.string.backup_wrong_password
        } catch (_: BackupFormat.NotABackup) {
            return R.string.backup_not_a_backup
        } catch (_: IOException) {
            return R.string.backup_read_failed
        } catch (_: GeneralSecurityException) {
            return R.string.backup_read_failed
        }
    // SnippetStore is main-thread only; the rest is file and preference work.
    val store = SnippetStore.get(context)
    backup.snippets?.let(store::replaceAll)
    val restored = withContext(Dispatchers.IO) { Backups.restore(context, backup) }
    val text = context.getString(R.string.backup_restored, restored.settings, restored.words, store.all().size)
    Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    return null
}

private fun appVersion(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"

private fun toast(context: Context, text: Int) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

private const val MIME = "application/octet-stream"
private const val EXTENSION = "khbackup"
