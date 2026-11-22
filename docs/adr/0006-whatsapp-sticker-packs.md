# 0006. WhatsApp sticker packs through an exported provider

- Status: accepted
- Date: 2026-11-22

## Context

Stickers sent from the keyboard (ADR 0005) reach WhatsApp one at a time,
through `commitContent`. Users also want their stickers in WhatsApp's own
sticker tray, where they can be found and sent without KeyHack. WhatsApp
supports this only through its sticker app contract
(github.com/WhatsApp/stickers):

- the app exports a `ContentProvider` that WhatsApp queries for pack
  metadata, the sticker list, and the image files;
- the provider requires the `com.whatsapp.sticker.READ` permission;
- the user taps "Add to WhatsApp", which starts
  `com.whatsapp.intent.action.ENABLE_STICKER_PACK`, and confirms in WhatsApp;
- packs hold 3 to 30 stickers, all still or all animated. Stickers are
  512×512 WebP: still ones up to 100 KB, animated ones up to 500 KB and 10 s.
  The tray icon is about 96×96 and at most 50 KB, and an app may have at
  most 10 packs.

This is the first exported component besides the IME service and the
launcher activity, so ADR 0002 requires its own decision.

## Decision

- **Packs** are user-made groups of library stickers, kept in
  `filesDir/sticker_packs.json`: id, name, sticker file names, 1–3 emoji, and a
  version. The settings screen creates and edits them.
  - Each pack's tray icon is its first sticker drawn at 96×96 PNG, in
    `filesDir/sticker_trays`.
  - Ids come from the clock and are never reused, so WhatsApp never confuses
    a new pack with a deleted one.
  - Deleting a sticker from the library removes it from its packs; a pack left
    empty is deleted.
- **`WhatsAppStickerProvider`** (in `:stickers`, authority
  `<applicationId>.stickers`) answers WhatsApp's four paths:
  - `metadata`
  - `metadata/<id>`
  - `stickers/<id>`
  - `stickers_asset/<id>/<file>`
- **Guards:**
  - The manifest sets `android:permission="com.whatsapp.sticker.READ"`, which
    covers reads and writes.
  - Every call must come from `com.whatsapp` or `com.whatsapp.w4b`
    (`getCallingPackage()`, which the platform verifies against the calling
    uid). An app that declares WhatsApp's permission itself, because WhatsApp
    is not installed, therefore still gets nothing.
  - The provider is read-only: insert, update and delete throw.
  - It serves only packs that pass WhatsApp's rules and whose files exist.
  - It opens only files whose names the pack lists, matched by exact name and
    never as a path built from the request. Read mode only.
- The manifest policy allowlists the provider and requires that permission on
  it.
- **`<queries>`** names `com.whatsapp` and `com.whatsapp.w4b`. It is used only
  to show the "Add to WhatsApp" buttons and to ask WhatsApp's
  `sticker_whitelist_check` provider whether a pack is already added.

## Threat-model impact

- **Adversary 2 (malicious apps):** a new exported component.
  - It exposes only sticker images that the user put into a pack, in order to
    give them to WhatsApp, along with the pack names and emoji.
  - It exposes no typing data, clipboard data or settings.
  - The permission and calling-package checks keep other apps out, including
    one that squats WhatsApp's permission name.
- **Adversary 3 (malicious files):** nothing new. The files are the
  re-encoded stickers from ADR 0005, and the provider parses no input except
  path segments, which it compares with stored names.
- **Assets:** the pack list is a new, small store with no personal content
  beyond names the user typed. Like the library, it is excluded from backup.

## Consequences

- WhatsApp keeps its own copy of an added pack. Removing a pack from it is
  done in WhatsApp; KeyHack can only stop serving it.
- Changing a pack raises its version, so WhatsApp fetches it again.
- A change in WhatsApp's contract (columns, paths, permission name) breaks
  this feature without breaking anything else. The contract is kept in one
  file (`WhatsAppStickerProvider` plus `WhatsApp`).
