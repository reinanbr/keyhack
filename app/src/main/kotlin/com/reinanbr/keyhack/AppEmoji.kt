package com.reinanbr.keyhack

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reinanbr.keyhack.emoji.EmojiData
import com.reinanbr.keyhack.emoji.EmojiFont
import com.reinanbr.keyhack.emoji.EmojiStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The keyboard's emoji table, for pickers in the settings app; loaded once. */
internal object AppEmoji {
    @Volatile private var data: EmojiData? = null

    /** Blocking: reads the table (about 240 KB) the first time. */
    fun load(context: Context): EmojiData = data ?: synchronized(this) {
        data ?: context.assets.open("emoji.tsv").bufferedReader().use(EmojiData::read).also { data = it }
    }

    /** The emoji font as a Compose family, or null for the system font. */
    fun family(context: Context): FontFamily? = EmojiFont.typeface(context)?.let { FontFamily(it) }
}

/**
 * An emoji picker like the keyboard's: recents, the Unicode groups, and
 * search in English and Portuguese. Emoji in [marked] are highlighted.
 * Only emoji the bundled font draws are offered, as on the keyboard.
 */
@Composable
internal fun EmojiPicker(marked: Collection<String>, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val data by produceState<EmojiData?>(null) { value = withContext(Dispatchers.IO) { AppEmoji.load(context) } }
    val family = remember { AppEmoji.family(context) }
    val recents = remember { EmojiStore(context).recents() }
    var group by remember { mutableStateOf(RECENT) }
    var query by remember { mutableStateOf("") }
    val shown =
        remember(data, group, query) {
            val d = data ?: return@remember emptyList()
            val list =
                when {
                    query.isNotBlank() -> d.search(query, SEARCH_LIMIT).map { it.value }
                    group == RECENT -> recents
                    else -> d.group(group).map { it.value }
                }
            list.filter { EmojiFont.canDraw(context, it) }
        }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.emoji_search)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        if (query.isBlank()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val colors = MaterialTheme.colorScheme
                for ((id, icon) in GROUPS) {
                    val selected = id == group
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) colors.secondaryContainer else colors.surface,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { group = id },
                    ) {
                        Text(icon, fontFamily = family, fontSize = TAB_SP.sp, modifier = Modifier.padding(8.dp))
                    }
                }
            }
        }
        LazyVerticalGrid(columns = GridCells.Adaptive(CELL_DP.dp), modifier = Modifier.weight(1f)) {
            items(shown, key = { it }) { e ->
                val on = e in marked
                Box(
                    Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .clickable { onPick(e) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(e, fontFamily = family, fontSize = EMOJI_SP.sp)
                }
            }
        }
    }
}

private const val RECENT = "recent"
private val GROUPS =
    listOf(
        RECENT to "🕘", "smileys" to "😀", "people" to "👋", "nature" to "🐻", "food" to "🍔", "travel" to "🚗",
        "activities" to "⚽", "objects" to "💡", "symbols" to "🔣", "flags" to "🏁",
    )
private const val SEARCH_LIMIT = 120
private const val CELL_DP = 44
private const val EMOJI_SP = 26
private const val TAB_SP = 20
