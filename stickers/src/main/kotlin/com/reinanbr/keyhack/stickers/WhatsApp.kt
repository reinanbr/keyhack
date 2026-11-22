package com.reinanbr.keyhack.stickers

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri

/** WhatsApp's side of the sticker pack contract: adding a pack and asking whether it was added. */
object WhatsApp {
    const val CONSUMER = "com.whatsapp"
    const val BUSINESS = "com.whatsapp.w4b"
    val PACKAGES = setOf(CONSUMER, BUSINESS)

    /** Set by WhatsApp on a cancelled result when it refused the pack. */
    const val EXTRA_VALIDATION_ERROR = "validation_error"

    private const val ACTION_ENABLE = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
    private const val CHECK_PROVIDER = ".provider.sticker_whitelist_check"

    fun authority(context: Context) = "${context.packageName}.stickers"

    /** The WhatsApp apps on this device (visible through the manifest's <queries>). */
    fun installed(context: Context): List<String> =
        listOf(CONSUMER, BUSINESS).filter { context.packageManager.getLaunchIntentForPackage(it) != null }

    /**
     * The installed WhatsApp apps that have [pack]. Blocking: asks each one
     * (and may start it). A WhatsApp too old to answer counts as not having it.
     */
    fun havingPack(context: Context, pack: String): Set<String> =
        installed(context).filterTo(HashSet()) { has(context, it, pack) }

    private fun has(context: Context, whatsapp: String, pack: String): Boolean {
        val checker = whatsapp + CHECK_PROVIDER
        val uri =
            Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(checker).appendPath("is_whitelisted")
                .appendQueryParameter("authority", authority(context))
                .appendQueryParameter("identifier", pack)
                .build()
        // Null cursor: no such provider (WhatsApp too old).
        return runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                c.moveToFirst() && c.getInt(c.getColumnIndexOrThrow("result")) == 1
            } == true
        }.getOrDefault(false)
    }

    /**
     * The intent that asks [whatsapp] to add [pack]; WhatsApp shows its own
     * confirmation. Start it for a result: RESULT_OK when added.
     */
    fun addIntent(context: Context, pack: StickerPack, whatsapp: String): Intent = Intent(ACTION_ENABLE)
        .setPackage(whatsapp)
        .putExtra("sticker_pack_id", pack.id)
        .putExtra("sticker_pack_authority", authority(context))
        .putExtra("sticker_pack_name", pack.name)
}
