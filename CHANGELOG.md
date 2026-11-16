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
