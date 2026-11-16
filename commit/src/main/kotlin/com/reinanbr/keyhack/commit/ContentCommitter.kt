package com.reinanbr.keyhack.commit

import android.content.ClipDescription
import android.net.Uri
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat

/** Sends images (GIFs, stickers) to the focused app through the Commit Content API. */
object ContentCommitter {
    /** Whether the editor declared it accepts [mime] (e.g. "image/gif"; wildcards honored). */
    fun accepts(editor: EditorInfo?, mime: String): Boolean {
        if (editor == null) return false
        return EditorInfoCompat.getContentMimeTypes(editor).any { ClipDescription.compareMimeTypes(mime, it) }
    }

    /**
     * Commits [uri] (a FileProvider content:// URI) as [mime]. The target app
     * gets temporary read access to that URI only. Returns false when the
     * editor refuses or does not support rich content.
     */
    fun commit(
        ic: InputConnection,
        editor: EditorInfo,
        uri: Uri,
        mime: String,
        description: String,
        linkUri: Uri? = null,
    ): Boolean {
        if (!accepts(editor, mime)) return false
        val info = InputContentInfoCompat(uri, ClipDescription(description, arrayOf(mime)), linkUri)
        return InputConnectionCompat.commitContent(
            ic,
            editor,
            info,
            InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,
            null,
        )
    }
}
