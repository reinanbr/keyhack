# 0001. Two-app architecture: an offline core and an optional networked companion

- Status: accepted
- Date: 2026-11-15

## Context

An input method reads every keystroke, passwords included. Users have no way
to audit what a keyboard does with that text at runtime. The strongest
guarantee Android offers is structural: an app without the `INTERNET`
permission cannot open sockets.

Some features (GIF search, ML-based sticker background removal) need the
network or large proprietary models.

## Decision

Ship two apps:

- **KeyHack** (`com.reinanbr.keyhack`): the IME and all offline features. It
  never declares `INTERNET`, `RECORD_AUDIO`, contacts, storage or overlay
  permissions. Only `VIBRATE` is allowed.
- **KeyHack Online** (`com.reinanbr.keyhack.online`): optional, holds
  `INTERNET`, has no IME service and never sees keystrokes.

They talk over a bound AIDL service (`:online-api`) guarded by a
`signature`-level permission, plus a signing-certificate digest check on each
call (designed in M6, with its own ADR). The core sends only the text typed
into the GIF search box, the locale and a pagination cursor. Everything the
companion returns is untrusted input.

## Threat-model impact

- Adversary 1 (keyboard as keylogger): mitigated by construction; enforced by
  the build gate in ADR 0002.
- Adversary 2 (malicious apps spoofing the companion): mitigated by the
  signature permission and digest check. Both apps must be signed with the
  same key, so F-Droid must publish them with the developer's signature
  (reproducible builds).
- Adversary 4 (network attackers): confined to the companion, which uses
  HTTPS only with system CAs only.

## Consequences

- Features needing the network must be built in the companion, behind the
  IPC contract, or not at all.
- Users who never install the companion get a fully working keyboard.
- Both apps must be built reproducibly and signed with the same key.
