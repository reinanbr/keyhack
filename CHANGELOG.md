# Changelog

All notable changes to KeyHack are listed here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow
[Semantic Versioning](https://semver.org/). Each version has an APK on the
[releases page](https://github.com/reinanbr/keyhack/releases).

## [Unreleased]

### Added

- Shift can be held: keys typed with another finger while Shift is down come
  out shifted, and lifting it turns Shift off. A tap still shifts the next
  letter and a double tap still locks caps.
- Next-word prediction from conversation sentences (Tatoeba, English and
  Portuguese): after "eu te" it offers "amo", after "how are" it offers
  "you". It uses the last two words, and words you often type after another
  still come first.
- The words before the cursor also rank suggestions while you type
  ("eu te am" offers "amo" before "americano").
- Chat words are no longer autocorrected: vc, kkk, blz, vlw, hj, obg, lol,
  idk, btw, gonna and others.
- An emoji for the word being typed appears at the end of the suggestion
  bar ("amor" ❤️, "kkk" 😂, "pizza" 🍕); tapping it keeps the word and adds
  the emoji.
- Emoji come from the bundled Noto Color Emoji font (Unicode 17.0, the same
  version WhatsApp shows) on Android 12 and later, so new emoji such as 🫩
  and 🫆 appear even where the phone's own font is older; emoji the font
  cannot draw are hidden. Emoji stickers use the same font. Older Android
  versions keep the system emoji.

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

[Unreleased]: https://github.com/reinanbr/keyhack/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/reinanbr/keyhack/releases/tag/v0.1.0
