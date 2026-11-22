package com.reinanbr.keyhack

import android.app.Activity
import android.content.ActivityNotFoundException
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.reinanbr.keyhack.stickers.StickerPack
import com.reinanbr.keyhack.stickers.StickerPackList
import com.reinanbr.keyhack.stickers.StickerPackStore
import com.reinanbr.keyhack.stickers.StickerStore
import com.reinanbr.keyhack.stickers.WhatsApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Sticker packs for WhatsApp, inside the stickers section: the user groups
 * library stickers into packs and adds each one to WhatsApp, which reads it
 * from WhatsAppStickerProvider. [libraryVersion] changes when the library does.
 */
@Composable
internal fun StickerPacksSection(stickers: StickerStore, libraryVersion: Int) {
    val context = LocalContext.current
    val store = remember { StickerPackStore.get(context) }
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    val packs by produceState(emptyList<StickerPack>(), libraryVersion, refresh) {
        value = withContext(Dispatchers.IO) { store.all() }
    }
    val library by produceState(emptyList<File>(), libraryVersion) {
        value = withContext(Dispatchers.IO) { stickers.list() }
    }
    val whatsapps = remember { WhatsApp.installed(context) }
    // Pack id -> the WhatsApp apps that already have it.
    val added by produceState(emptyMap<String, Set<String>>(), packs) {
        value = withContext(Dispatchers.IO) { packs.associate { it.id to WhatsApp.havingPack(context, it.id) } }
    }
    val addToWhatsApp = rememberAddToWhatsApp { refresh++ }
    var editorOpen by remember { mutableStateOf(false) }
    // The pack being edited; null in an open editor: a new pack.
    var editing by remember { mutableStateOf<StickerPack?>(null) }

    // Closes the editor and runs [work] off the main thread; false means it failed.
    fun inBackground(work: () -> Boolean) {
        editorOpen = false
        scope.launch {
            if (!withContext(Dispatchers.IO) { work() }) {
                Toast.makeText(context, R.string.pack_save_failed, Toast.LENGTH_SHORT).show()
            }
            refresh++
        }
    }

    Text(stringResource(R.string.packs_title), style = MaterialTheme.typography.titleSmall)
    Text(stringResource(R.string.packs_body), style = MaterialTheme.typography.bodySmall)
    if (whatsapps.isEmpty()) {
        Text(stringResource(R.string.packs_no_whatsapp), style = MaterialTheme.typography.bodySmall)
    }
    val byName = remember(library) { library.associateBy { it.name } }
    for (pack in packs) {
        PackRow(
            pack = pack,
            first = byName[pack.stickers.first()],
            installed = whatsapps,
            have = added[pack.id].orEmpty(),
            onAdd = { addToWhatsApp(pack, it) },
            onEdit = {
                editing = pack
                editorOpen = true
            },
        )
    }
    when {
        library.size < StickerPackList.MIN_STICKERS ->
            Text(stringResource(R.string.pack_need_stickers), style = MaterialTheme.typography.bodySmall)

        packs.size >= StickerPackList.MAX_PACKS ->
            Text(stringResource(R.string.pack_limit), style = MaterialTheme.typography.bodySmall)

        else -> {
            val onNew = {
                editing = null
                editorOpen = true
            }
            OutlinedButton(onClick = onNew) { Text(stringResource(R.string.pack_new)) }
        }
    }
    if (editorOpen) {
        val pack = editing
        PackEditor(
            pack = pack,
            library = library,
            onSave = { name, chosen, emoji ->
                inBackground {
                    val saved =
                        if (pack == null) {
                            store.create(name, chosen, emoji)
                        } else {
                            store.update(pack.id, name, chosen, emoji)
                        }
                    saved != null
                }
            },
            onDelete = {
                inBackground {
                    pack?.let { store.remove(it.id) }
                    true
                }
            },
            onDismiss = { editorOpen = false },
        )
    }
}

/** Starts WhatsApp's own "add this pack?" dialog and reports how it went; [onDone] runs after. */
@Composable
private fun rememberAddToWhatsApp(onDone: () -> Unit): (StickerPack, String) -> Unit {
    val context = LocalContext.current
    val resources = LocalResources.current
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val error = result.data?.getStringExtra(WhatsApp.EXTRA_VALIDATION_ERROR)
            when {
                result.resultCode == Activity.RESULT_OK ->
                    Toast.makeText(context, R.string.pack_added, Toast.LENGTH_SHORT).show()

                error != null ->
                    Toast.makeText(context, resources.getString(R.string.pack_refused, error), Toast.LENGTH_LONG).show()
            }
            onDone()
        }
    return { pack, whatsapp ->
        try {
            launcher.launch(WhatsApp.addIntent(context, pack, whatsapp))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.pack_whatsapp_missing, Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun PackRow(
    pack: StickerPack,
    first: File?,
    installed: List<String>,
    have: Set<String>,
    onAdd: (String) -> Unit,
    onEdit: () -> Unit,
) {
    val valid = StickerPackList.problems(pack.name, pack.stickers, pack.emoji).isEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(TRAY_DP.dp)) { first?.let { Thumb(it, Modifier.fillMaxSize()) } }
            Column(Modifier.weight(1f)) {
                Text(pack.name, style = MaterialTheme.typography.bodyLarge)
                val count = pluralStringResource(R.plurals.pack_count, pack.stickers.size, pack.stickers.size)
                val kind = if (pack.animated) " · " + stringResource(R.string.pack_animated) else ""
                val emoji = pack.allEmoji().take(ROW_EMOJI).joinToString(" ")
                Text(count + kind + " · " + emoji, style = MaterialTheme.typography.bodySmall)
                if (!valid) {
                    Text(
                        stringResource(R.string.pack_invalid),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (valid) {
                for (w in installed - have) {
                    val label = if (w == WhatsApp.BUSINESS) R.string.pack_add_business else R.string.pack_add
                    Button(onClick = { onAdd(w) }) { Text(stringResource(label)) }
                }
                if (have.isNotEmpty()) {
                    Text(
                        stringResource(R.string.pack_in_whatsapp),
                        modifier = Modifier.align(Alignment.CenterVertically),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            OutlinedButton(onClick = onEdit) { Text(stringResource(R.string.pack_edit)) }
        }
    }
}

/**
 * Full-screen editor: the name and the stickers picked from the library, in
 * the order picked. Tapping a picked sticker opens its emoji.
 */
@Composable
private fun PackEditor(
    pack: StickerPack?,
    library: List<File>,
    onSave: (String, List<String>, Map<String, List<String>>) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val defaultName = stringResource(R.string.pack_default_name)
    var name by remember { mutableStateOf(pack?.name ?: defaultName) }
    val chosen = remember { mutableStateListOf<String>().apply { pack?.let { addAll(it.stickers) } } }
    val emoji = remember { mutableStateMapOf<String, List<String>>().apply { pack?.let { putAll(it.emoji) } } }
    // New picks get the emoji chosen last, so a pack of one mood needs one choice.
    var lastEmoji by remember {
        mutableStateOf(pack?.stickers?.lastOrNull()?.let(pack::emojiOf) ?: listOf(StickerPackList.DEFAULT_EMOJI))
    }
    var emojiFor by remember { mutableStateOf<File?>(null) }
    val problems = StickerPackList.problems(name, chosen, emoji)
    val mixedLibrary = remember(library) { library.map { StickerPackList.isAnimated(it.name) }.distinct().size > 1 }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(if (pack == null) R.string.pack_new else R.string.pack_edit),
                    style = MaterialTheme.typography.titleLarge,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= StickerPackList.MAX_NAME) name = it },
                    label = { Text(stringResource(R.string.pack_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.pack_selected, chosen.size, StickerPackList.MAX_STICKERS),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(stringResource(R.string.pack_emoji_hint), style = MaterialTheme.typography.bodySmall)
                if (mixedLibrary) {
                    Text(stringResource(R.string.pack_mixed_rule), style = MaterialTheme.typography.bodySmall)
                }
                StickerGrid(library, chosen, emoji, Modifier.weight(1f)) { file ->
                    if (file.name in chosen) {
                        emojiFor = file
                    } else {
                        chosen += file.name
                        emoji[file.name] = lastEmoji
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (pack != null) TextButton(onClick = onDelete) { Text(stringResource(R.string.pack_delete)) }
                    Box(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
                    Button(onClick = { onSave(name, chosen.toList(), emoji.toMap()) }, enabled = problems.isEmpty()) {
                        Text(stringResource(R.string.pack_save))
                    }
                }
            }
        }
    }
    emojiFor?.let { file ->
        StickerEmojiDialog(
            file = file,
            emoji = emoji[file.name].orEmpty(),
            onDone = { list ->
                emoji[file.name] = list
                lastEmoji = list
                emojiFor = null
            },
            onApplyToAll = { list ->
                for (f in chosen) emoji[f] = list
                lastEmoji = list
                emojiFor = null
            },
            onRemove = {
                chosen.remove(file.name)
                emoji.remove(file.name)
                emojiFor = null
            },
            onDismiss = { emojiFor = null },
        )
    }
}

/** One sticker's emoji (one to three, WhatsApp's limit), picked from the keyboard's emoji. */
@Composable
private fun StickerEmojiDialog(
    file: File,
    emoji: List<String>,
    onDone: (List<String>) -> Unit,
    onApplyToAll: (List<String>) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val family = remember { AppEmoji.family(context) }
    val picked = remember { mutableStateListOf<String>().apply { addAll(emoji) } }
    val ok = StickerPackList.isEmojiList(picked)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(EMOJI_THUMB_DP.dp)) { Thumb(file, Modifier.fillMaxSize()) }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.pack_sticker_emoji), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.pack_emoji_help), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (e in picked.toList()) {
                        Text(
                            e,
                            fontFamily = family,
                            fontSize = PICKED_SP.sp,
                            modifier =
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clickable { picked.remove(e) }
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                    if (picked.isEmpty()) {
                        Text(stringResource(R.string.pack_emoji_none), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                EmojiPicker(marked = picked, modifier = Modifier.weight(1f), onPick = { e ->
                    when {
                        e in picked -> picked.remove(e)

                        picked.size < StickerPackList.MAX_EMOJI -> picked += e

                        // Full: the newest pick replaces the last one.
                        else -> picked[picked.lastIndex] = e
                    }
                })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onRemove) { Text(stringResource(R.string.pack_sticker_remove)) }
                    TextButton(onClick = { onApplyToAll(picked.toList()) }, enabled = ok) {
                        Text(stringResource(R.string.pack_emoji_all))
                    }
                    Button(
                        onClick = { onDone(picked.toList()) },
                        enabled = ok,
                    ) { Text(stringResource(R.string.pack_ok)) }
                }
            }
        }
    }
}

/**
 * The library, tapped to pick a sticker or open a picked one's emoji. Once
 * one kind (animated or still) is picked the other is dimmed: WhatsApp does
 * not mix them.
 */
@Composable
private fun StickerGrid(
    library: List<File>,
    chosen: List<String>,
    emoji: Map<String, List<String>>,
    modifier: Modifier,
    onTap: (File) -> Unit,
) {
    val animated = chosen.firstOrNull()?.let(StickerPackList::isAnimated)
    val full = chosen.size >= StickerPackList.MAX_STICKERS
    val family = AppEmoji.family(LocalContext.current)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(CELL_DP.dp),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(library, key = { it.name }) { file ->
            val order = chosen.indexOf(file.name)
            val isAnimated = StickerPackList.isAnimated(file.name)
            val allowed = order >= 0 || (!full && (animated == null || animated == isAnimated))
            StickerCell(file, order, isAnimated, allowed, emoji[file.name]?.joinToString(""), family) { onTap(file) }
        }
    }
}

@Composable
private fun StickerCell(
    file: File,
    order: Int,
    animated: Boolean,
    allowed: Boolean,
    emoji: String?,
    family: FontFamily?,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val selected = order >= 0
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(shape)
            .border(
                if (selected) {
                    BorderStroke(SELECTED_BORDER_DP.dp, MaterialTheme.colorScheme.primary)
                } else {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                },
                shape,
            )
            .alpha(if (allowed) 1f else DISABLED_ALPHA)
            .clickable(enabled = allowed, onClick = onClick),
    ) {
        Thumb(file, Modifier.fillMaxSize().padding(4.dp))
        if (selected) {
            Text(
                "${order + 1}",
                modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                    .padding(horizontal = 6.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        if (selected && emoji != null) {
            Text(
                emoji,
                fontFamily = family,
                modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                    .padding(horizontal = 3.dp),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        if (animated) {
            Text(
                "GIF",
                modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/** A small copy of a sticker (its first frame when animated), decoded off the main thread. */
@Composable
private fun Thumb(file: File, modifier: Modifier) {
    val bitmap by produceState<ImageBitmap?>(null, file) {
        value =
            withContext(Dispatchers.IO) {
                val opts = BitmapFactory.Options().apply { inSampleSize = THUMB_SAMPLE }
                BitmapFactory.decodeFile(file.path, opts)?.asImageBitmap()
            }
    }
    bitmap?.let { Image(it, contentDescription = null, modifier = modifier) }
}

private const val TRAY_DP = 48
private const val CELL_DP = 88
private const val THUMB_SAMPLE = 4
private const val DISABLED_ALPHA = 0.3f
private const val SELECTED_BORDER_DP = 3
private const val EMOJI_THUMB_DP = 72
private const val PICKED_SP = 28
private const val ROW_EMOJI = 6
