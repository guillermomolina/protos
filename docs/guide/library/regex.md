# Regular expressions: `std:regex/Regex`

`std:regex/Regex` is a deliberately restricted regular-expression dialect. It
trades features such as backreferences and look-around for three guarantees:

- **portable** — the same pattern means the same thing on every distribution;
  no host regex engine is involved;
- **Unicode-scalar based** — every offset is a String scalar index, using
  Unicode 17.0.0 properties and simple locale-independent case folding;
- **linear** — one search costs at most pattern size times input length; there
  is no exponential backtracking.

The exact contract is the doc comment of
[`Regex.protos`](../../../protos/lib/regex/Regex.protos).

```protos
Regex: import("std:regex/Regex")
```

## Compiling a pattern

| Operation | Answers |
|---|---|
| `Regex.compile(source)` | A fresh frozen Pattern with no flags |
| `Regex.compileWithFlags(source, flags)` | A Pattern under the flag letters in `flags` |
| `Regex.escape(text)` | Pattern source matching exactly `text` under every flag combination |
| `Regex.escapeReplacement(text)` | Replacement text inserting exactly `text` |

Flags are single letters, each at most once, in any order:

| Flag | Meaning |
|---|---|
| `i` | Simple Unicode case-insensitive matching |
| `m` | `^` and `$` also match at line boundaries (CRLF, LF, CR, NEL, LS, PS) |
| `s` | `.` also matches those line terminators |
| `x` | Whitespace and `#` comments are ignored outside classes |

A Pattern exposes `source`, the canonical `flags` String (letters in `imsx`
order), `captureCount`, and `captureNames`, a frozen Map from each name to its
group number. Two compilations of the same source answer distinct Patterns.

A source outside the dialect, an unknown or repeated flag, or a non-String
argument signals an ordinary Error when you compile, not when you match.

## Accepted syntax

Accepted: literals; `\t`, `\n`, `\r`, `\f`; `\u{HEX}`; a backslash before any
printable ASCII character that is not a letter or digit; `.`; classes `[...]`
and `[^...]` with ranges, nesting, intersection `&&` and subtraction `--`; `\d`,
`\s`, `\w` and their negations; `\p{...}` and `\P{...}`; alternation `|`;
numbered `(...)`, non-capturing `(?:...)` and named `(?<name>...)` groups;
greedy `?`, `*`, `+`, `{n}`, `{n,}`, `{n,m}` and their lazy forms; and the
assertions `^`, `$`, `\A`, `\z`, `\b`, `\B`.

Rejected with an Error: backreferences, look-ahead and look-behind, atomic
groups, possessive quantifiers, recursion, conditionals, inline flags, `\G`,
`\K`, `\X`, and POSIX bracket classes. Literal `]`, `{` and `}`, and literal
`[`, `]`, `-` and `&` inside a class, must be escaped.

`\d` is Unicode `Decimal_Number`, not only ASCII `0`–`9`; `\w` is the UTS #18
word set. Use `[0-9]` when you mean ASCII digits.

Inside a Protos String literal each regex backslash is itself escaped, so the
regex `\d+` is written `"\\d+"`.

## Matching

| Pattern operation | Answers |
|---|---|
| `fullMatch(text)` | The match covering all of `text`, or `null` |
| `search(text)` | The leftmost match anywhere, or `null` |
| `searchFrom(text, offset)` | `search` restricted to starts at or after `offset` |
| `eachMatch(text, block)` | Calls `block(match)` for every non-overlapping match; answers `null` |
| `findAll(text)` | A fresh Array of those matches |

The selected match is leftmost-first: the earliest start wins, and for one
start the pattern priority decides (alternatives in source order, greedy
quantifiers prefer more, lazy ones prefer less).

A Match is frozen. `group(key)`, `start(key)` and `end(key)` take a group number
(`0` is the whole match) or a capture name and answer `null` for a group that
did not participate. `groups()` answers an Array of every group's text and
`namedGroups()` a Map from name to text. An unknown group number or name
signals an Error.

```protos
Regex: import("std:regex/Regex")

date: Regex.compile("(?<year>\\d{4})-(?<month>\\d{2})-(\\d{2})")
found: date.search("on 2026-10-06!")

found.group(0)          // "2026-10-06"
found.group("year")     // "2026"
found.start("year")     // 3
found.group(3)          // "06"
found.namedGroups()["month"] // "10"

Regex.compile("z").search("abc") // null
```

## Replacing and splitting

| Pattern operation | Answers |
|---|---|
| `replaceFirst(text, replacement)` | `text` with the `search` match replaced |
| `replaceAll(text, replacement)` | `text` with every `eachMatch` match replaced |
| `split(text)` | The fields between matches; empty fields are kept |

In a replacement, `$$` is a literal `$`, `${0}` the whole match, `${N}` group
`N`, and `${name}` a named group. Any other `$` use, or a reference to a group
the Pattern does not have, signals an Error before anything is replaced, even
when nothing matches. Wrap untrusted replacement text in
`Regex.escapeReplacement`.

```protos
Regex.compile("b+").replaceFirst("abbcbb", "-")        // "a-cbb"
Regex.compile("\\d+").replaceAll("a1b22c333", "#")      // "a#b#c#"
Regex.compile("(\\w+)@(\\w+)").replaceAll("me@host", "${2}:${1}") // "host:me"
Regex.compile(",").split("a,,b")                         // ["a", "", "b"]
```

An empty match inserts the replacement and removes nothing, so
`Regex.compile("").replaceAll("ab", "-")` is `"-a-b-"`, and an empty pattern
splits `"ab"` into `"", "a", "b", ""`.

## Matching literal text

`Regex.escape(text)` builds source that matches exactly `text`, whatever flags
are used:

```protos
needle: Regex.compile(Regex.escape("1+1=2?"))
needle.search("is 1+1=2? yes") === null // false
```

## Limitations

- No backreferences, look-around, or other features listed above; the dialect
  rejects them rather than approximating them.
- No grapheme-cluster matching and no implicit Unicode normalization: `é` as
  one scalar and `e` plus a combining accent are different input.
- Case-insensitive matching uses simple case folding only (no `ß` to `ss`).

## JVM and Native

Behavior is identical. On the Native distribution, guest JIT is disabled
(PLAT045), so long searches run interpreter-only and are slower than on the
portable JVM. The Unicode property data `std:regex` reads when it loads is
embedded in the Native executable of Protos 0.3.312 (DIST015-FIX).
