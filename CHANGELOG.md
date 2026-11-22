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
- Suggestions agree with the subject: after "nós", "eles não" or "a gente
  não se", predicted and completed verbs come in the right person ("nós
  est" → "estamos" first, "eles fal" → "falam", after "nós não" →
  "temos", "podemos", never "é" or "sei"). A finished verb in the wrong
  person ("nós vai") gets the right one next to it; autocorrect still only
  fixes spelling.
- Phrase suggestions: when the next-word models are confident about more
  than one word, the bar offers a short phrase first ("obrigado" → "por
  me ajudar", "a gente" → "se vê", "eu não" → "sei o que", "thank you" →
  "very much"); one tap writes it all.
- About 140 common Portuguese and 160 English misspellings are corrected
  on space ("concerteza" → "com certeza", "menas" → "menos", "seje" →
  "seja", "definately" → "definitely", "dont" → "don't").
- Current words the dictionaries lacked: WhatsApp, iFood, TikTok, stories,
  selfie, crush, sextou, print, live, rizz, lowkey and others.
- Delete whole words: slide ⌫ to the left and the words to delete are
  selected, one more for each step (slide back to take them off); lift to
  delete them. Holding ⌫ also switches from letters to whole words after
  about two seconds. Either way, "↶ Undo" in the suggestion bar brings the
  text back for five seconds.
- Snippets: saved texts (an address, a Pix key, a reply you send often),
  stored encrypted. Give one a shortcut ("end") and typing it offers the
  text in the suggestion bar; all of them are in the </> panel's Snippets
  tab, which can also save the selected or copied text as a new one.
  Created and edited in settings.
- Command palette: 🔍 in the </> panel searches every panel, toggle, edit
  action, dev tool and snippet by name ("base64", "anônimo", "área");
  tap a result or press Enter to run the best one.
- Glide typing: slide across the letters of a word without lifting the
  finger. The word is matched on the phone against the dictionaries (the
  path's shape against each word's path over the keys, with frequency and
  the words before it), accents and apostrophes come out right ("voce" →
  "você", "dont" → "don't"), words you have learned can be glided too, and
  alternatives show in the suggestion bar. A space goes in before the next
  glided word; one backspace removes a glided word. A short swipe up on a
  key still types its corner symbol. On by default; can be turned off.
- Floating keyboard: a smaller keyboard that floats over the app, moved
  by the handle on top (▭ on the handle docks it back). The app keeps
  its full size and stays touchable around the keyboard; the position is
  remembered. Turn it on from the ✋ one-handed bar (⧉), the command
  palette or settings.
- Theme editor: a "Custom" theme where you pick the background, keys,
  function keys, letters and highlight colors (hue, saturation and
  brightness sliders, a hex field or ready colors), starting from any
  built-in theme, with a live preview and a warning when letters would be
  hard to read. Pressed keys and secondary labels are derived from your
  picks.
- Vibration length setting (0–60 ms). 0 keeps the system's keyboard
  vibration, which some phones mute along with touch feedback.

- Offline build: `KeyHack-<version>-offline.apk` has no network code and no
  `INTERNET` permission, so it cannot reach the network at all. Everything
  but GIF search works the same. The two builds share the package name:
  install one or the other.
- WhatsApp sticker packs: in Settings → Stickers, group 3 to 30 of your
  stickers into a pack (all still or all animated), give it a name, give
  each sticker 1 to 3 emoji from an emoji picker (with search, or "Use for
  all"), and tap "Add to WhatsApp". The pack then appears in WhatsApp's own
  sticker tray. Up to 10 packs; changing a pack updates it in WhatsApp.
  WhatsApp reads the packs through a read-only provider that only WhatsApp
  can open (ADR 0006).

### Changed

- More emoji in the suggestion bar: up to three for the word you are typing
  ("amor" → ❤️ 😍 🥰, "kkkk" → 😂 🤣 😆), and two more after you finish it
  (tap one to add it after the space). A chat word list (Brazilian slang
  such as vlw, tmj, blz, niver; laughs like kkkk, hahaha, rsrs) picks
  them before the Unicode keywords, and short words like "de" or "isso" no
  longer bring up unrelated emoji.
- Kaomoji: over 200 text faces (from 44), in nine moods with their own
  tabs under ツ: happy, love, sad, angry, surprised, animals, actions and
  table flips, dancing, and shrugs.
- Unicode 18 emoji: the bundled Noto Color Emoji font is now 2.057, which
  adds 🫫 cracking face, 🫹 🫺 thumb signs, 🫌 monarch butterfly, 🫝 pickle,
  🛙 lighthouse, 🪋 meteor, 🪌 eraser and 🪍 net. People whose phone or app
  does not have Unicode 18 yet may see a box instead.

- The HTTP client moved to its own module, `:net`, which only the online
  build includes; `:media` no longer has network code.
- ⌫ deletes when released (or when held), not when touched, so a slide
  to delete words does not take a letter first.
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
