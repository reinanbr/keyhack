# Layouts

Keyboard layouts are JSON files in `src/main/assets/layouts/`, loaded at
runtime by `LayoutParser` (`:keyboard`). Edit them or add your own without
touching Kotlin. `LayoutAssetsTest` parses every file here on each build.

## Files

| File | Role |
|---|---|
| `common.json` | Shared rows (`@alpha-bottom`, `@number-row`, `@pc-bottom`), the long-press table and `symbolHints` |
| `qwerty.json`, `abnt2.json`, `azerty.json`, `dvorak.json`, `colemak.json` | Letter layouts |
| `pc.json`, `fn.json` | Full PC layout (Hacker's Keyboard style) and its Fn layer (F1–F12, navigation) |
| `symbols.json`, `symbols2.json` | Symbol layers: `?123` (digits, code symbols, punctuation) and `=÷€` (math, currency) |
| `numpad.json`, `phone.json` | Number and phone fields |

## Format

A layout is `{"id": "...", "rows": [...]}`. A row is an array of keys,
`{"h": 0.85, "keys": [...]}` for a shorter row, or `"@name"` for a shared row.

A key is a string (`"q"`, a character key) or an object:

| Field | Meaning |
|---|---|
| `t` | Text committed (`"q"`) |
| `s` | Shifted text (default: uppercase of `t`) |
| `code` | Function key instead of text (list below) |
| `l` | Label (required for `layer:` codes) |
| `w` | Width in key units (default 1) |
| `pop` | Long-press alternatives: a string (one per character) or an array |
| `hint` | Small corner label; also added as the first long-press option |
| `lp` | Long-press action: a code, or text |

Codes: `shift delete enter space ctrl alt meta esc tab left right up down home
end pgup pgdn ins fdel prtsc pause f1`…`f12 emoji settings pc nextime picker
tools gif clipboard layer:<id>` (`layer:alpha` returns to the letter layout).

## Symbols on the letter keys

`common.json` has `symbolHints`: one string per letter row, top to bottom. The
n-th character key of each row gets the n-th symbol as its corner hint. Swipe
up on the key to type it, or long-press it (the symbol is the first option).

```json
"symbolHints": ["`~\\|{}[]<>", "@#$_&-+()/", "*\"':;!?"]
```

The top row's symbols replace its digit hints only while the number row is
shown; without the number row the digit stays and the symbol becomes the
second long-press option. Any key with its own `hint` keeps it (the period
key's `/`, for example).
