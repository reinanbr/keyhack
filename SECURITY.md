# Security policy

KeyHack is an input method: it sees everything you type, passwords included.
We take reports seriously and fix them first.

## Reporting a vulnerability

**Do not open a public issue.** Report privately through GitHub Security
Advisories: open the repository's **Security** tab and choose
**Report a vulnerability**.

Please include:

- the KeyHack version or commit;
- the Android version and device;
- steps to reproduce, or a proof of concept;
- what an attacker gains (for example: reads typed text, reads the clipboard,
  writes files, makes network requests it shouldn't).

We aim to acknowledge reports within 3 days and to ship a fix or mitigation
within 30 days for high-severity issues. We will credit you in the advisory
unless you prefer otherwise.

## Scope

In scope:

- anything that lets typed text, clipboard contents, learned vocabulary,
  stickers or the GIF API key leave the device or reach another app;
- any network request other than GIF search to the configured provider, or
  any request that carries text from the field being edited;
- the app gaining any permission besides `VIBRATE` and `INTERNET`;
- abuse of exported components, the WhatsApp sticker provider or the share
  target;
- parser bugs in imported layouts, themes, snippets, dictionaries, images,
  GIFs, videos or stickers;
- supply-chain issues in the build, CI or release process.

Out of scope: issues that require a rooted device or physical access to an
unlocked phone, and the behaviour of third-party GIF providers.

## Design guarantees you can check

- The APK's merged release manifest is checked on every build; the build
  fails if it requests any permission other than `VIBRATE` and `INTERNET`
  (plus permissions it defines itself with `protectionLevel="signature"`), or
  exports a component outside a fixed allowlist. See
  `docs/adr/0002-core-permission-and-export-policy.md`.
- All network code lives in the `:media` module; detekt rejects network
  imports anywhere else. See `docs/adr/0004-single-app-gif-network.md`.
- `aapt2 dump permissions <apk>` on a release APK lists what it can do.
- Release builds strip all `android.util.Log` calls.
