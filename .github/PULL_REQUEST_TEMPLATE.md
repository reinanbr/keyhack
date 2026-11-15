## What and why

<!-- What does this change, and why? Link the issue or milestone. -->

## Security checklist

- [ ] Adds no permission, exported component, native parser or data store, **or** includes an ADR in `docs/adr/` covering the threat-model impact
- [ ] Does not log typed text, clipboard contents, file contents or keys at any level
- [ ] Respects the sensitive-field policy (no learning, suggestions, clipboard capture or snippet expansion in password / no-suggestion / incognito fields)
- [ ] Treats imported files and companion responses as untrusted (size caps, magic-byte sniffing, strict schemas)
- [ ] Copies no GPL code; Apache-2.0 code keeps its header and is listed in `NOTICE`

## Testing

<!-- `./gradlew check assembleRelease` output, devices tested, screenshots. -->
