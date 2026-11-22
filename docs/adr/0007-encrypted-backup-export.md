# 0007. Manual backup export, encrypted with the user's password

- Status: accepted
- Date: 2026-11-22

## Context

The spec asks for "manual backup/restore to a file" of the user dictionary,
history and settings. Today nothing can leave the app:

- `allowBackup="false"` and the data extraction rules keep Android's cloud
  backup and device transfer out (ADR 0002);
- learned words, snippets and the clipboard history live in
  `noBackupFilesDir`.

So reinstalling KeyHack, or moving to a new phone, loses the learned words,
the snippets and every setting.

A backup file is a new way for user data to leave the sandbox, and a restored
file is a new untrusted input. Both need a decision.

## Decision

- **What goes in:** settings, learned words (counts only, as in
  `UserHistory`), snippets, emoji recents and skin tones.
- **What stays out:**
  - the clipboard history: short-lived by design, encrypted with a Keystore
    key that cannot be exported;
  - stickers and sticker packs: large binaries the user already has as
    images;
  - the GIF API key: a secret that only ever goes to its provider;
  - internal signals such as `learning_clear`.
- **Always encrypted, never optional:**
  - The user picks a password of at least 8 characters each time they
    export. KeyHack never stores it.
  - The key comes from PBKDF2-HMAC-SHA256 with 600,000 rounds and a random
    16-byte salt. The data is sealed with AES-256-GCM under a random 12-byte
    IV, and the header is authenticated as associated data.
  - The plaintext is gzipped JSON. The file is `keyhack-<date>.khbackup`.
- **User-initiated only:**
  - Export and restore go through the system file picker
    (`ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`). No storage
    permission, no automatic or scheduled backups, no network.
- **Restore treats the file as untrusted, even after the password checks
  out:**
  - At most 16 MB read, and at most 32 MB inflated (no gzip bombs).
  - An iteration count outside 100,000–5,000,000 is refused before any key
    is derived.
  - Nothing is parsed until the GCM tag verifies.
  - The JSON parser has a nesting limit. Unknown fields and any newer format
    version are refused or ignored.
  - Settings pass an allowlist with types and ranges: only values the
    settings screen itself could set.
  - Learned words are re-read with the same caps as the keyboard's own file
    (word length, positive counts, at most 20,000 words and 60,000 pairs).
  - Snippets go through `SnippetList.decode` (500 snippets, 10,000 characters
    each).
  - Emoji entries must be short single lines, and tones must be 1–5.
- **Restore replaces; it does not merge.** A part missing from the file is
  left alone.
  - Learned words are written to a staging file (`learned.restore.tsv`).
  - The keyboard swaps that file in on its own suggestion thread, when the
    `learning_restored` setting changes or at its next start, so the
    keyboard's in-memory copy never overwrites a restore.

## Threat-model impact

- **Adversary 6 (accidental leakage):**
  - The backup is the first way learned words and snippets can leave the
    device.
  - It happens only when the user taps Export and picks a place.
  - Wherever the file ends up (Downloads, a cloud drive, a chat), it is
    unreadable without the password.
  - The README row "No cloud backup or device transfer" stays true for
    automatic mechanisms. It now notes the manual, encrypted export.
- **Adversary 3 (malicious files):**
  - A crafted file must first pass GCM authentication with the password the
    user types. A stranger's file does not, unless they also told the user
    its password.
  - Even then, the strict schema means a restore can set only what the user
    could set by hand. It cannot plant files, exceed the learned-word caps,
    or set the GIF key.
- **Adversary 2 (malicious apps):** nothing new. No component is exported,
  and file access is granted per URI by the system picker.
- **Brute force:**
  - Short passwords stay guessable offline, whatever the round count; hence
    the 8-character minimum and the warning in the export dialog.
  - 600,000 rounds is OWASP's 2023 figure for PBKDF2-HMAC-SHA256. On the
    test phone (moto g35) an export, file picker included, takes about four
    seconds, with a progress indicator.

## Consequences

- A user who forgets the password cannot restore. The dialog says so.
- Version 1 of the format must stay readable. New fields are optional, and
  a format change raises `version`, which older builds refuse.
- A new setting is not backed up until it is added to `SettingsSchema` with
  its rule.
