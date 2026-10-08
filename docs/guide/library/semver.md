# Semantic versions: `std:semver/SemVer`

`std:semver/SemVer` parses, formats, and compares versions under strict
[Semantic Versioning 2.0.0](https://semver.org/spec/v2.0.0.html). The exact
contract is the doc comment of
[`SemVer.protos`](../../../protos/lib/semver/SemVer.protos).

```protos
SemVer: import("std:semver/SemVer")
```

## Operations

| Operation | Answers |
|---|---|
| `SemVer.parse(text)` | A fresh version object |
| `SemVer.format(version)` | Canonical text; `format(parse(text)) == text` |
| `SemVer.comparePrecedence(left, right)` | `-1`, `0`, or `1` by SemVer precedence |

A version has Integer `major`, `minor`, and `patch` slots, a `prerelease` Array
of Integers (numeric identifiers) and Strings (alphanumeric identifiers), and a
`build` Array of Strings. Integers are unbounded, so very large components are
exact.

```protos
v: SemVer.parse("1.2.3-alpha.7+001.linux")

v.major         // 1
v.prerelease    // ["alpha", 7]
v.build         // ["001", "linux"]
SemVer.format(v) // "1.2.3-alpha.7+001.linux"
```

`parse` is strict. These all signal an Error: `"v1.2.3"`, `" 1.2.3"`,
`"1.2"`, `"01.2.3"`, `"1.2.3-01"`, `"1.2.3-"`, and `"1.2.3+a..b"`.

## Equality versus precedence

The module separates two relations:

- `==` is complete equality, **including build metadata**. `hash` is coherent
  with it, so independently parsed equal versions are the same Map key. `===`
  is still object identity.
- `comparePrecedence` is the SemVer ordering, which **ignores build metadata**.

```protos
linux: SemVer.parse("1.2.3+linux")
windows: SemVer.parse("1.2.3+windows")

SemVer.comparePrecedence(linux, windows) // 0
linux == windows                          // false

SemVer.comparePrecedence(SemVer.parse("1.0.0-alpha"), SemVer.parse("1.0.0")) // -1
SemVer.comparePrecedence(SemVer.parse("1.0.0-alpha.2"), SemVer.parse("1.0.0-alpha.10")) // -1
```

Precedence compares core numbers numerically; a release outranks its
prereleases; numeric prerelease identifiers compare numerically and rank below
alphanumeric ones; alphanumeric ones compare by ASCII order.

## Limitations

- No version ranges or constraint syntax (`^1.2`, `~1.2`, `>=1.0 <2.0`).
- No lenient parsing (`v` prefixes, missing components, whitespace).
- No increment helpers; build a new version string and `parse` it.

## JVM and Native

Pure Protos; identical on both distributions.
