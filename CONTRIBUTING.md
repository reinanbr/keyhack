# Contributing to KeyHack

Thanks for helping. KeyHack handles everything people type, so the bar for
changes is "safe first, then useful".

## Ground rules

1. **Network only in `:net`, only for GIFs.** No network code in any other
   module, no WebViews, no dynamic code loading, no reflection-based plugins.
   The build enforces the permission list and the import ban (ADR 0004);
   reviewers enforce the rest.
2. **ADR required** (`docs/adr/`, copy `0000-template.md`) for any change that
   adds a permission, an exported component, a native parser or a new data
   store, and for hard-to-reverse formats (dictionary, Room schema, layout
   schema, IPC contract).
3. **Never log user data** (typed text, clipboard, file contents, keys) at any
   level, including in debug builds and exception messages.
4. **Untrusted input** (imports, shared files, GIF provider responses): cap the
   size before parsing, sniff MIME types by magic bytes, validate strictly,
   copy into app-private storage before processing.
5. **Licensing:** Apache-2.0 only. You may reuse AOSP LatinIME, Hacker's
   Keyboard or FlorisBoard code: keep the original header and add the file to
   `NOTICE`. Never copy GPL code (HeliBoard, GPL OpenBoard forks,
   AnySoftKeyboard).

## Workflow

```sh
./gradlew check assembleRelease          # must pass before you open a PR
./gradlew detekt -PdetektAutoCorrect     # fix formatting
```

- Kotlin style: ktlint official, checked by detekt.
- No `!!` without a `@Suppress("UnsafeCallOnNullableType")` and a comment
  explaining why it can't be null.
- Add unit tests for logic; keep Android-free logic in plain Kotlin classes so
  it can be tested on the JVM.
- Strings go in `strings.xml`. English is the source language; add pt-BR
  (`values-pt-rBR`) when you can.
- If you change dependencies, regenerate `gradle/verification-metadata.xml`
  (see README) and explain new entries in the PR.

By contributing you agree that your contribution is licensed under
Apache-2.0, and you agree to follow the [Code of Conduct](CODE_OF_CONDUCT.md).
