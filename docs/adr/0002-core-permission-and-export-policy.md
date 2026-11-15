# 0002. Core permission and exported-component policy, enforced at build time

- Status: accepted
- Date: 2026-11-15

## Context

ADR 0001 relies on the core app never gaining `INTERNET`. Permissions can enter
the APK silently through library manifests merged at build time, so reviewing
`app/src/main/AndroidManifest.xml` is not enough.

## Decision

The `keyhack.manifest-policy` Gradle plugin (`build-logic/`) checks the
**merged release manifest** of each app and fails both `check` and
`assembleRelease`/`bundleRelease` on any violation:

1. **Permissions:** every `<uses-permission>` (and `-sdk-23`) must be on the
   app's allowlist, or be a permission the same manifest declares under the
   app's own package name with `protectionLevel="signature"`.
   - Core allowlist: `android.permission.VIBRATE`.
   - Companion allowlist: `android.permission.INTERNET`.
   - The own-package signature exception covers
     `<applicationId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, which AndroidX
     Core generates so that only this app can send to its non-exported dynamic
     receivers. It grants no capability outside the app. It will also cover the
     M6 IPC permission.
2. **Declared permissions** must use `protectionLevel="signature"`.
3. **Exported components** (activities, aliases, services, receivers,
   providers, including implicit export through an intent filter) must be on an
   allowlist. Core M0: the IME service and the launcher activity. The IME
   service must also be guarded by `android.permission.BIND_INPUT_METHOD`.
4. **Application flags:** `allowBackup="false"`, `dataExtractionRules` set,
   `usesCleartextTraffic` not `true`, not `debuggable`.

`androidx.profileinstaller.ProfileInstallReceiver` (exported, guarded by
`DUMP`, pulled in by Compose) is removed with `tools:node="remove"`. It is only
used by benchmarking tools; baseline profiles still install through
androidx.startup.

The policy logic is plain Kotlin with unit tests (`ManifestPolicyTest`), and
the build was checked end to end by adding `INTERNET` to `:ime` and confirming
`:app:assembleRelease` fails.

## Threat-model impact

Directly mitigates adversary 1 (keylogger) and adversary 2 (abuse of exported
components). It does not cover code that is never declared in a manifest, such
as dynamic code loading, which is banned separately (lint and review).

## Consequences

- Adding a permission or exported component means editing the allowlist in
  `app/build.gradle.kts` or `companion/build.gradle.kts`, which by project rule
  requires a new ADR.
- M5 must add the WhatsApp sticker provider and share-target activity to the
  core allowlist; M6 adds the companion's IPC service.
