# KeyHack

An open source, privacy-first Android keyboard for power users, written in Kotlin.

"Hack" means hacker culture, the tradition of keyboards like Hacker's Keyboard
that give you a full PC layout for terminals, SSH, Vim and code. It does not
mean intrusion. Every keyboard can read everything you type, so KeyHack is
built to be **verifiably trustworthy**: the keyboard app has no network access,
stores nothing it doesn't need, and anyone can audit it.

> **Status: milestone M0 (skeleton and security baseline).** KeyHack is an
> activatable IME that types basic QWERTY. See the [roadmap](#roadmap).

## Two apps, one security boundary

| | **KeyHack** (`com.reinanbr.keyhack`) | **KeyHack Online** (`com.reinanbr.keyhack.online`) |
|---|---|---|
| Role | The keyboard: typing, suggestions, emoji, stickers, clipboard, dev tools | Optional companion: GIF search, ML sticker background removal |
| Network | **None.** No `INTERNET` permission | `INTERNET`, HTTPS only, system CAs only |
| Sees keystrokes | Yes | **Never.** Receives only what you type into the GIF search box |
| Permissions | `VIBRATE` only | `INTERNET` only |

The core can't send your typing anywhere because Android won't let an app
without `INTERNET` open a socket. This isn't left to review: every build checks
the **merged** release manifest, with all library manifests included, and fails
if anything beyond `VIBRATE` appears or if a component is exported outside a
fixed allowlist. See [ADR 0001](docs/adr/0001-two-app-architecture.md) and
[ADR 0002](docs/adr/0002-core-permission-and-export-policy.md).

Check a release APK yourself:

```sh
aapt2 dump permissions app-release.apk
```

## Threat model

**Assets:** everything you type (passwords included), clipboard contents,
learned vocabulary, your sticker library, the GIF provider API key.

| # | Adversary or risk | Main mitigations |
|---|---|---|
| 1 | The keyboard itself as a keylogger | No `INTERNET` in the core, enforced at build time; learning stores word/n-gram counts only, never sentences |
| 2 | Malicious apps on the device | Minimal exported components (allowlisted at build time); signature-level IPC permission plus certificate-digest checks; read-only, traversal-safe sticker provider |
| 3 | Malicious files (layouts, themes, snippets, dictionaries, media) | Size caps, magic-byte MIME sniffing, strict schemas, copy-then-process, memory-safe decoders, fuzzed parsers |
| 4 | Network attackers | Network only in the companion; HTTPS only, no cleartext, no user CAs |
| 5 | Compromised dependencies or CI | Gradle dependency verification (checksums + PGP), SHA-pinned Actions, least-privilege workflows, reproducible builds, SBOM |
| 6 | Accidental leakage | No cloud backup or device transfer; release builds strip `android.util.Log`; opt-in, encrypted, expiring clipboard history; no learning in sensitive fields |

**Sensitive fields** (password input types, `TYPE_TEXT_FLAG_NO_SUGGESTIONS`,
`IME_FLAG_NO_PERSONALIZED_LEARNING`, or incognito) get no suggestions, no
autocorrect, no learning, no clipboard capture and no snippet expansion
(from M1 onwards).

## Architecture

```
:app          settings app, onboarding, IME activation flow
:ime          InputMethodService, InputConnection, EditorInfo, field policy
:keyboard     layout model, Canvas rendering, touch, accessibility
:layouts      bundled JSON layouts + validated loader            (M1)
:suggest      dictionary, autocorrect, prediction, glide         (M3, M8)
:emoji        emoji data, search, skin tones, kaomoji            (M4)
:stickers     sticker library and converters                     (M5)
:commit       commitContent, FileProvider, fallbacks             (M5)
:devtools     snippets, transforms, macros, terminal mode        (M7)
:clipboard    opt-in encrypted history                           (M4)
:security     Keystore crypto, field policy, validators, MIME    (M1+)
:data         Room, DataStore, repositories
:theme        themes, Material You, theme editor                 (M1, M9)
:core         shared utilities
:online-api   AIDL contract shared with the companion            (M6)
companion/    KeyHack Online
build-logic/  convention plugins and the manifest policy gate
```

The key grid is a custom `View` drawn on a `Canvas` (no allocation in
`onDraw`); panels and settings use Jetpack Compose. Each key is exposed to
TalkBack as a virtual view.

## Building

Requirements: JDK 21 and the Android SDK (platform 37).

```sh
./gradlew assembleDebug                 # debug APKs for both apps
./gradlew check                         # unit tests, lint, detekt/ktlint, manifest gate
./gradlew assembleRelease               # R8-minified, unsigned unless signing env is set
./gradlew detekt -PdetektAutoCorrect    # apply formatting fixes
```

Release signing reads `KEYHACK_KEYSTORE`, `KEYHACK_KEYSTORE_PASSWORD`,
`KEYHACK_KEY_ALIAS` and `KEYHACK_KEY_PASSWORD` from the environment (CI
secrets). Keys never live in the repository.

After changing dependencies, regenerate the verification metadata and review
the diff:

```sh
./gradlew --write-verification-metadata pgp,sha256 --export-keys check assembleRelease
```

## Roadmap

| | Milestone | Status |
|---|---|---|
| M0 | Skeleton, CI, dependency verification, permission gate, backup off, log stripping, basic QWERTY IME | ✅ |
| M1 | JSON layouts, long-press, symbols, themes, settings, sensitive-field policy | |
| M2 | PC mode: full layout, sticky modifiers, extra row, per-app profiles | |
| M3 | Dictionaries, autocorrect, prediction, count-only learning, incognito | |
| M4 | Emoji, opt-in encrypted clipboard | |
| M5 | Stickers, GIF→animated WebP, commitContent, WhatsApp export | |
| M6 | KeyHack Online: IPC, GIF providers, BYOK | |
| M7 | Dev tools: trackpad, selection, snippets, transforms, command palette | |
| M8 | Glide typing | |
| M9 | Floating/one-handed modes, theme editor, backup export, accessibility, F-Droid | |

## Contributing and security

See [CONTRIBUTING.md](CONTRIBUTING.md). Report vulnerabilities privately as
described in [SECURITY.md](SECURITY.md), never in public issues.

## License

[Apache License 2.0](LICENSE). Third-party origins are listed in [NOTICE](NOTICE).
