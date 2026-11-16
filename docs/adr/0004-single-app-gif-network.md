# 0004. One app; network access only for GIF search

- Status: accepted (supersedes 0001)
- Date: 2026-11-16

## Context

ADR 0001 split KeyHack into an offline keyboard and a networked companion
(KeyHack Online) so that the IME could never hold `INTERNET`. In practice the
split doubles the install and signing work, needs an IPC contract, and the
maintainer asked for a single app that combines Gboard-style features
(emoji, GIFs, suggestions) with Hacker's Keyboard (full PC layout, Ctrl/Alt,
arrows).

GIF search is the only feature that needs the network. Sticker background
removal with ML models is dropped from the roadmap for now.

## Decision

Ship one app, `com.reinanbr.keyhack`, that holds `VIBRATE` and `INTERNET`.

The network is fenced in by these rules:

1. **One module, one client.** All network code lives in `:media`
   (`HttpFetcher`). detekt's `ForbiddenImport` rule rejects `java.net.URL`,
   `URLConnection`, sockets, `javax.net`, OkHttp and WebView imports in every
   other module, so reviewing `:media` covers every request the app can make.
2. **Only on explicit action.** Requests happen only while the GIF panel is
   open, and only with the provider API key the user entered (BYOK). Without
   a key, or with GIFs turned off in settings, nothing is sent. The GIF button
   is hidden, and the panel refuses to open, in incognito and password fields.
3. **Minimal payload.** A request carries the text typed into the GIF search
   box (never the text field the user is editing), the language, a page
   offset and the API key. No cookies, no HTTP cache, a generic
   `User-Agent`, an empty `Accept-Language`.
4. **Pinned destinations.** `HttpFetcher` accepts HTTPS URLs only, on the
   active provider's host allowlist (Giphy: `api.giphy.com` and
   `*.giphy.com` media hosts). Tenor was removed after Google shut its
   public API down on 2026-06-30. Redirects are followed by hand
   and re-checked against the allowlist. Responses are size-capped and parsed
   as untrusted input.
5. **Transport.** `network_security_config.xml` forbids cleartext and trusts
   system CAs only (no user-installed CAs).
6. **Still enforced at build time.** The manifest gate (ADR 0002) now allows
   `VIBRATE` and `INTERNET`, nothing else.

## Threat-model impact

- Adversary 1 (keyboard as keylogger): the structural guarantee of ADR 0001
  is gone. It is replaced by an auditable one: network code is confined to one
  small module by a build rule, and the requests it can make are fixed URL
  templates on allowlisted hosts. Users who want the old guarantee can turn
  GIFs off; a follow-up may add an `offline` build flavor without `:media`.
- Adversary 2 (malicious apps): no IPC service is exported, which removes the
  companion-spoofing risk of ADR 0001.
- Adversary 4 (network attackers): HTTPS only, system CAs only, host
  allowlist re-checked on redirects.
- The provider (Giphy) sees the search terms, the API key and the
  device's IP address. This is stated in the settings screen.

## Consequences

- No companion app, no AIDL contract, no shared signing requirement.
- Any new network use needs a new ADR and a change to `:media`'s allowlist.
- An `offline` flavor (no `:media`, no `INTERNET`) is the planned answer for
  users who want the strict guarantee back.
