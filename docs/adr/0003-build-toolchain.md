# 0003. Build toolchain and SDK levels

- Status: accepted
- Date: 2026-11-15

## Decision

- Gradle 9.9.1 (wrapper, checksum-pinned), AGP 9.5.1 with built-in Kotlin
  (`org.jetbrains.kotlin.android` is never applied), Kotlin 2.4.21.
- `minSdk 28`, `targetSdk 36`, **`compileSdk 37`**. The spec asked for compileSdk
  36, but current AndroidX releases (core 1.19, activity 1.13, Compose BOM
  2026.09) refuse to build against anything older than 37. compileSdk only
  changes which APIs the code can reference; runtime behaviour follows
  targetSdk, which stays at 36.
- Shared configuration lives in convention plugins in `build-logic/`
  (`keyhack.android.application`, `keyhack.android.library`, `keyhack.compose`,
  `keyhack.manifest-policy`). Library namespaces derive from the module name.
- Lint: detekt 2.0.0-alpha.6 with the ktlint wrapper rules, since detekt 1.23
  cannot parse Kotlin 2.4. Revisit when detekt 2.0 is stable.
- JDK 21 runs the build; bytecode targets Java 17.
- Gradle dependency verification (`gradle/verification-metadata.xml`) checks
  SHA-256 for every artifact and PGP signatures where they are published.

## Consequences

Bumping minSdk or targetSdk changes the security baseline and needs an ADR.
