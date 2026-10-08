# Standard Library guides

These guides explain selected `std:` modules that ship with Protos. They are
non-normative: each module's own doc comments under
[`protos/lib/`](../../../protos/lib/) describe its exact contract, and the
specification under [`spec/`](../../../spec/) wins over any guide prose.

The Standard Library is ordinary Protos code distributed with the toolchain.
Importing a module grants no authority. A module that writes text, reads a
time, or touches a file does so only through a value you pass to it.

## Importing a module

Every module is imported by its exact `std:` path and bound to a name:

```protos
Regex: import("std:regex/Regex")
SemVer: import("std:semver/SemVer")
Interop: import("std:interop")
```

There is no wildcard import and no package-level re-export: `std:datetime` alone
is not a module; import `std:datetime/Date`, `std:datetime/Instant`, and so on.

## Guides

| Guide | Modules | Use it for |
|---|---|---|
| [Regular expressions](regex.md) | `std:regex/Regex` | Portable, linear-time pattern matching, captures, replacement, and splitting |
| [Dates, times, and durations](datetime.md) | `std:datetime/*` | Civil dates and times, instants, fixed offsets, durations, periods, ISO 8601 and RFC 3339 text |
| [Structured logging](logging.md) | `std:logging/*` | Explicit loggers, levels, structured fields, text/JSON/colored formatting, and sinks |
| [Text styling and encodings](text.md) | `std:text/*` | Styled text, ANSI rendering, color-mode policy, and UTF-8/UTF-16/Latin-1 helpers |
| [Semantic versions](semver.md) | `std:semver/SemVer` | Strict Semantic Versioning 2.0.0 parsing, formatting, equality, and precedence |
| [Explicit foreign operations](interop.md) | `std:interop` | `invoke`, `instantiate`, `readMember`, and `writeMember` on foreign values |

## Native and portable JVM distributions

Every module in these guides is written in Protos and behaves the same in both
release distributions:

- **Native Linux x86_64** — a self-contained executable for Linux x86_64 with
  dynamic glibc 2.39 or newer. It needs no external JDK. Guest code runs
  interpreter-only: guest JIT compilation is disabled under PLAT045, so
  compute-heavy library work (for example large regex searches) is slower than
  on the JVM, but results are identical.
- **Portable JVM** — runs on the supported external runtime, GraalVM
  25.4.4.1.1 (JDK 25.0.4.1.1), with the optimizing runtime and guest JIT.

`std:regex` depends on Unicode property data. Since Protos 0.3.312 that data is
embedded in the Native executable, so `import("std:regex/Regex")` works in both
distributions.

The one module whose usefulness differs is `std:interop`: it acts only on
foreign values, and foreign values come from external providers that only the
portable JVM CLI can load. See [Explicit foreign operations](interop.md).

See [Try Protos](../00-try-protos.md) for downloading and running either
distribution.

## Other `std:` modules

Further modules exist under [`protos/lib/`](../../../protos/lib/) (for example
`std:json`, `std:toml`, `std:csv`, `std:collections`, `std:io`, `std:crypto`,
`std:cli`, `std:uri`, and `std:test`). Their doc comments are the current
reference; they have no dedicated guide yet. Collection behavior is covered in
[chapter 05](../05-values-identity-equality-and-collections.md) and Process
streams in [chapter 11](../11-process-io-filesystems-and-authority.md).
