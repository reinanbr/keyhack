# 0005. Sticker library in app-private storage

- Status: accepted
- Date: 2026-11-16

## Context

Users want to send stickers (WhatsApp first) from the keyboard: their own
images and emoji drawn large. This adds a data store (the imported images)
and a parser for untrusted files (any image format the user picks).

## Decision

- **Import** goes through the system photo picker (`PickMultipleVisualMedia`)
  in the settings app. No storage permission is requested; the app sees only
  the images the user picks.
- **Untrusted input:** each image is read with a 20 MB cap, decoded by the
  platform `ImageDecoder` with power-of-two subsampling (never more than about
  1024 px per side in memory), drawn onto a 512×512 transparent canvas and
  re-encoded as WebP under 100 KB. The original bytes are never stored or
  shared.
- **Storage:** `filesDir/stickers` (imported) and `cacheDir/stickers` (emoji
  stickers, regenerated on demand). Both are app-private and excluded from
  backup (`allowBackup="false"`, data extraction rules).
- **Sharing:** the existing non-exported FileProvider (`<app>.media`) gains
  those two directories. The keyboard sends a sticker with `commitContent`,
  granting read access to that one URI, as `image/webp.wasticker` when the
  editor accepts it (WhatsApp), else `image/webp`, else through the share
  sheet.

## Threat-model impact

- Adversary 3 (malicious files): mitigated by the size cap, subsampled
  platform decoding and re-encoding; no native parser of our own.
- Adversary 2 (malicious apps): no new exported component; URIs are granted
  per send.
- Assets: the sticker library is a new asset. It never leaves the device
  except as the sticker the user chose to send.

## Consequences

- Animated stickers and WhatsApp sticker packs (the `ContentProvider` that
  WhatsApp's "Add to WhatsApp" flow needs) are not covered; packs would add an
  exported provider and need their own ADR.
