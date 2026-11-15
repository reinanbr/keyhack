# Security policy

KeyHack is an input method: it sees everything you type, passwords included.
We take reports seriously and fix them first.

## Reporting a vulnerability

**Do not open a public issue.** Report privately through GitHub Security
Advisories: open the repository's **Security** tab and choose
**Report a vulnerability**.

Please include:

- the affected app (KeyHack or KeyHack Online) and version or commit;
- the Android version and device;
- steps to reproduce, or a proof of concept;
- what an attacker gains (for example: reads typed text, reads the clipboard,
  writes files, spoofs the companion app).

We aim to acknowledge reports within 3 days and to ship a fix or mitigation
within 30 days for high-severity issues. We will credit you in the advisory
unless you prefer otherwise.

## Scope

In scope:

- anything that lets typed text, clipboard contents, learned vocabulary,
  stickers or the GIF API key leave the device or reach another app;
- the core app gaining network access or any permission besides `VIBRATE`;
- abuse of exported components, the WhatsApp sticker provider, the share
  target, or the KeyHack Online IPC service;
- parser bugs in imported layouts, themes, snippets, dictionaries, images,
  GIFs, videos or stickers;
- supply-chain issues in the build, CI or release process.

Out of scope: issues that require a rooted device or physical access to an
unlocked phone, and the behaviour of third-party GIF providers.

## Design guarantees you can check

- The core APK's merged release manifest is checked on every build; the build
  fails if it requests any permission other than `VIBRATE` (plus permissions
  it defines itself with `protectionLevel="signature"`), or exports a component
  outside a fixed allowlist. See `docs/adr/0002-core-permission-and-export-policy.md`.
- `aapt2 dump permissions <apk>` on a release APK lists what it can do.
- Release builds strip all `android.util.Log` calls.
