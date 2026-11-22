# Changelog

All notable changes to KeyHack are listed here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow
[Semantic Versioning](https://semver.org/). Each version has an APK on the
[releases page](https://github.com/reinanbr/keyhack/releases).

## [Unreleased]

### Added

- Clipboard history (opt-in): the 📋 button in the toolbar lists what you
  copied; tap to paste, long-press to pin or delete. Pinned items stay,
  the rest are deleted after 1 hour, 1 day or 7 days (settings). Stored
  encrypted with a key kept in the Android Keystore, never backed up, and
  never taken from password fields, incognito, or copies the source app
  marks sensitive (password managers). Turning it off deletes it all.
- Paste what you just copied: for two minutes after a copy, it appears as
  the first item in the suggestion bar.
- Grammar suggestions, on the phone, in Portuguese and English: after you
  finish a word, a ✓ chip in the suggestion bar offers a fix and nothing
  changes until you tap it. Verb agreement ("nós vai" → "nós vamos",
  "eles fala" → "eles falam", "a gente fomos" → "a gente foi"), plural
  agreement ("os menino" → "os meninos", "dois real" → "dois reais"),
  common mistakes ("a muito tempo" → "há muito tempo", "fazem dois anos"
  → "faz dois anos", "se eu ver" → "se eu vir", "pra mim fazer" → "pra eu
  fazer", "à pé" → "a pé", "Porque você não veio?" → "Por que…"), and
  English ("he don't" → "he doesn't", "could of" → "could have", "a apple"
  → "an apple", "your welcome" → "you're welcome"). The rules live in
  plain text files (`suggest/src/main/assets/grammar/`) anyone can extend.
  Can be turned off in settings.
- About 140 common Portuguese and 160 English misspellings are corrected
  on space ("concerteza" → "com certeza", "menas" → "menos", "seje" →
  "seja", "definately" → "definitely", "dont" → "don't").
- Current words the dictionaries lacked: WhatsApp, iFood, TikTok, stories,
  selfie, crush, sextou, print, live, rizz, lowkey and others.

### Changed

- The dev tools panel's editing actions (Copy, Paste, Undo…) are
  translated.

## [0.2.0] - 2026-11-16

### Added

- Shift can be held: keys typed with another finger while Shift is down come
  out shifted, and lifting it turns Shift off. A tap still shifts the next
  letter and a double tap still locks caps.
- Next-word prediction from conversation sentences (Tatoeba, English and
  Portuguese): after "eu te" it offers "amo", after "how are" it offers
  "you". It uses the last two words, and words you often type after another
  still come first. A model only leads after words of its own language
  ("boa" predicts "ideia", not English "constrictor").
- The words before the cursor also rank suggestions while you type
  ("eu te am" offers "amo" before "americano").
- Chat words are no longer autocorrected: vc, kkk, blz, vlw, hj, obg, lol,
  idk, btw, gonna and others.
- An emoji for the word being typed appears at the end of the suggestion
  bar ("amor" ❤️, "kkk" 😂, "pizza" 🍕); tapping it keeps the word and adds
  the emoji.
- Emoji come from the bundled Noto Color Emoji font (Unicode 17.0, the same
  version WhatsApp shows) on Android 13 and later, so new emoji such as 🫩
  and 🫆 appear even where the phone's own font is older; emoji the font
  cannot draw are hidden. Emoji stickers use the same font. Older Android
  versions keep the system emoji.
- Key-press preview: the character you press appears enlarged above your
  finger (and shows the symbol a swipe-up will type). It can be turned off
  in settings.
- One-handed mode: the ✋ button in the toolbar narrows the keyboard to one
  side; the column beside it moves it to the other side (⇄) or back to full
  width (⤢). Also in settings.
- iOS theme: iPhone keyboard colors (light or dark with the system), rounder
  and more spaced keys with a shadow underneath, blue return key, no corner
  symbols (swiping up still types them).

### Changed

- The APK grew from 3.8 MB to about 10 MB: 5 MB is the emoji font, 1.3 MB
  the next-word models.

### Fixed

- Auto-capitalization no longer adds Shift to arrows, Tab, Ctrl/Alt
  shortcuts or the space-bar cursor swipe (an arrow at the start of a
  sentence selected text, Tab moved focus backwards). Shift you turn on
  yourself still does, so Shift+arrows selects text.

## [0.1.0] - 2026-11-16

First public preview.

### Added

- Keyboard: QWERTY, ABNT2, AZERTY, Dvorak and Colemak layouts (JSON);
  long-press accents and symbols; swipe up on a key for the symbol in its
  corner; optional number row; space-bar cursor trackpad; Material You and
  dark themes.
- Two modes, switched with the DEV button: **simple** for chatting and
  **advanced** for programming and terminals (dev row with Esc, Tab, Ctrl,
  Alt and arrows; code symbols on the top row; full PC layout with an Fn
  layer). Terminal and SSH apps open in advanced mode.
- Sticky Ctrl/Alt/Meta sent as real key events, so Ctrl+C and Ctrl+U work in
  Termux.
- English and Brazilian Portuguese dictionaries with autocorrect (undo with
  backspace), completions, accent fixes (voce → você) and next-word
  prediction that learns from use. Learning stores word and word-pair counts
  only, never sentences.
- Emoji with categories, recents, skin tones and EN/PT search; kaomoji and
  text symbols.
- Stickers: import your own images and GIFs (GIFs become animated stickers),
  or send any emoji as a sticker. WhatsApp receives them as real stickers.
- GIF search with your own Giphy API key; results appear while you type.
- Dev tools: case conversion, Base64, URL and HTML escaping, JSON
  pretty/minify, MD5/SHA hashes, UUID v4/v7, timestamps.
- Incognito mode; no suggestions, learning or GIFs in password fields.
- English and Brazilian Portuguese interface.

### Security

- Two permissions only: `VIBRATE` and `INTERNET` (GIF search only). A build
  gate fails on any other permission or exported component.
- All network code is confined to the `:media` module by a lint rule; HTTPS
  only, system CAs only, host allowlist re-checked on redirects.
- No analytics, crash reporting or telemetry; no cloud backup; release builds
  strip logging.

[Unreleased]: https://github.com/reinanbr/keyhack/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/reinanbr/keyhack/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/reinanbr/keyhack/releases/tag/v0.1.0
