package com.reinanbr.keyhack.stickers

import android.content.ContentProvider
import android.content.ContentValues
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/**
 * Serves the user's sticker packs to WhatsApp, in the format of WhatsApp's
 * sticker app contract (github.com/WhatsApp/stickers):
 *
 * - `metadata`, `metadata/<id>`: one row per pack;
 * - `stickers/<id>`: one row per sticker (file name, emoji);
 * - `stickers_asset/<id>/<file>`: a sticker or the tray icon.
 *
 * Exported by necessity, so it is read-only and doubly guarded (ADR 0006):
 * the manifest requires WhatsApp's `com.whatsapp.sticker.READ` permission,
 * and every call must come from a WhatsApp package, so an app that declared
 * that permission itself gets nothing. Only packs the user made are visible,
 * and only their own files.
 */
class WhatsAppStickerProvider : ContentProvider() {
    private val store by lazy { StickerPackStore.get(requireNotNull(context)) }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        if (!fromWhatsApp()) return null
        return when (val r = route(uri)) {
            Route.AllPacks -> packCursor(store.servable())
            is Route.Pack -> packCursor(listOfNotNull(store.servable(r.id)))
            is Route.Stickers -> store.servable(r.id)?.let(::stickerCursor)
            else -> null
        }
    }

    override fun getType(uri: Uri): String? {
        val authority = uri.authority
        return when (val r = route(uri)) {
            Route.AllPacks -> "vnd.android.cursor.dir/vnd.$authority.$METADATA"
            is Route.Pack -> "vnd.android.cursor.item/vnd.$authority.$METADATA"
            is Route.Stickers -> "vnd.android.cursor.dir/vnd.$authority.$STICKERS"
            is Route.Asset -> if (r.file.endsWith(".png")) "image/png" else StickerStore.MIME_WEBP
            null -> null
        }
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        val r = route(uri)
        if (mode != "r" || !fromWhatsApp() || r !is Route.Asset) throw FileNotFoundException(uri.path)
        val pack = store.servable(r.id) ?: throw FileNotFoundException(uri.path)
        // Only names the pack lists, never a path built from the request.
        val file =
            when (r.file) {
                pack.trayFile -> File(store.trayDir, pack.trayFile)
                in pack.stickers -> File(store.stickerDir, r.file)
                else -> throw FileNotFoundException(uri.path)
            }
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return AssetFileDescriptor(fd, 0, file.length())
    }

    private sealed interface Route {
        data object AllPacks : Route

        data class Pack(val id: String) : Route

        data class Stickers(val id: String) : Route

        data class Asset(val id: String, val file: String) : Route
    }

    private fun route(uri: Uri): Route? {
        val s = uri.pathSegments
        val id = s.getOrNull(1)
        return when {
            s == listOf(METADATA) -> Route.AllPacks
            id == null -> null
            s.size == 2 && s[0] == METADATA -> Route.Pack(id)
            s.size == 2 && s[0] == STICKERS -> Route.Stickers(id)
            s.size == ASSET_SEGMENTS && s[0] == ASSET -> Route.Asset(id, s.last())
            else -> null
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) =
        throw UnsupportedOperationException()

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) =
        throw UnsupportedOperationException()

    /** The platform checks the caller's package name against its uid. */
    private fun fromWhatsApp(): Boolean = callingPackage in WhatsApp.PACKAGES

    private fun packCursor(packs: List<StickerPack>): Cursor {
        val c = MatrixCursor(PACK_COLUMNS)
        for (p in packs) {
            c.addRow(
                arrayOf<Any?>(
                    p.id, p.name, PUBLISHER, p.trayFile,
                    // Store links, publisher email, website, privacy policy, license: none.
                    "", "", "", "", "", "",
                    p.version.toString(),
                    0,
                    if (p.animated) 1 else 0,
                ),
            )
        }
        return c
    }

    private fun stickerCursor(p: StickerPack): Cursor {
        val c = MatrixCursor(STICKER_COLUMNS)
        for (f in p.stickers) c.addRow(arrayOf<Any?>(f, p.emojiOf(f).joinToString(","), null))
        return c
    }

    private companion object {
        const val METADATA = "metadata"
        const val STICKERS = "stickers"
        const val ASSET = "stickers_asset"

        /** stickers_asset/<id>/<file> */
        const val ASSET_SEGMENTS = 3
        const val PUBLISHER = "KeyHack"

        /** In the order of WhatsApp's sample provider. */
        val PACK_COLUMNS =
            arrayOf(
                "sticker_pack_identifier",
                "sticker_pack_name",
                "sticker_pack_publisher",
                "sticker_pack_icon",
                "android_play_store_link",
                "ios_app_download_link",
                "sticker_pack_publisher_email",
                "sticker_pack_publisher_website",
                "sticker_pack_privacy_policy_website",
                "sticker_pack_license_agreement_website",
                "image_data_version",
                "whatsapp_will_not_cache_stickers",
                "animated_sticker_pack",
            )
        val STICKER_COLUMNS = arrayOf("sticker_file_name", "sticker_emoji", "sticker_accessibility_text")
    }
}
