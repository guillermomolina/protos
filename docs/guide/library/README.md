# Standard Library guides

These guides explain selected `std:` modules that ship with Protos. They are
non-normative: each module's own doc comments under
[`protos/lib/`](../../../protos/lib/) describe its exact contract, and the
specification under [`spec/`](../../../spec/) wins over any guide prose.

The Standard Library is Protos source distributed with the toolchain. A few
source-backed modules (for example `std:math/Math`, `std:interop`,
`std:runtime`, and `std:inspect`) also use a private native facility that the
runtime hands to that module alone; it is never part of the module's surface.

Importing a module never grants authority, but modules differ in whether they
need authority that already exists:

- Almost every module requires none. A module that writes text, reads a time,
  or touches a file does so only through a value you pass to it.
- `std:inspect` requires authority the host must already have granted to the
  Context (the `protos.Inspect` option). Without it, the import itself signals
  an ordinary `Error`; importing never creates that authority.

`std:runtime` answers portable, semantic facts with a specified shape.
`std:inspect` answers internal diagnostics whose text is implementation-specific,
may change between versions, and may be unavailable on some runtimes.

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
| [Numeric arithmetic and roots](../05-values-identity-equality-and-collections.md#working-with-integer-float-fraction-and-complex) | `std:math/Math`, `std:math/Integer` | `Math.sqrt(value)` over Numbers, and exact Integer algorithms (`gcd`, `lcm`, `factorial`, `pow`, `powMod`) |
| [Regular expressions](regex.md) | `std:regex/Regex` | Portable, linear-time pattern matching, captures, replacement, and splitting |
| [Dates, times, and durations](datetime.md) | `std:datetime/*` | Civil dates and times, instants, fixed offsets, durations, periods, ISO 8601 and RFC 3339 text |
| [Structured logging](logging.md) | `std:logging/*` | Explicit loggers, levels, structured fields, text/JSON/colored formatting, and sinks |
| [Text styling and encodings](text.md) | `std:text/*` | Styled text, ANSI rendering, color-mode policy, and UTF-8/UTF-16/Latin-1 helpers |
| [Semantic versions](semver.md) | `std:semver/SemVer` | Strict Semantic Versioning 2.0.0 parsing, formatting, equality, and precedence |
| [Explicit foreign operations](interop.md) | `std:interop` | `invoke`, `instantiate`, `readMember`, and `writeMember` on foreign values |
| [Runtime metadata](runtime.md) | `std:runtime` | Four read-only String fields: `implementation`, `implementationVersion`, `engineVersion`, `platform` (no Context mode) |
| [Runtime inspection](inspect.md) | `std:inspect` | Host-authorized diagnostics of value representations, Closures, Bytecode and Truffle trees |

## Runtime facts versus inspection

`Runtime: import("std:runtime")` and `Runtime.info()` require no special
authority. `info()` returns a fresh frozen snapshot with exactly four
String slots: `implementation`, `implementationVersion`, `engineVersion`,
and `platform`. It does **not** expose an execution or debugging mode.
See the [runtime guide](runtime.md) and the executable
[`info.protos` example](../../../protos/examples/runtime-introspection/info.protos).

In contrast, `Inspect: import("std:inspect")` requires explicit host
authorization; even importing the module fails without it. For standalone
use, launch the existing example from the product repository root:

```sh
bin/protos --inspect protos/examples/runtime-introspection/inspect.protos
```

This is a diagnostic API, not a portable representation or JIT-status
API. See the [inspection guide](inspect.md) and the
[`inspect.protos` example](../../../protos/examples/runtime-introspection/inspect.protos).

The math entries above describe the **current `main` source**, not the
feature set of the previously published Protos 0.3.312 artifacts. The next
distribution will establish its own release-specific module availability.

## Native and portable JVM distributions

Modules included in Native and portable JVM builds from the same source
revision are written in Protos, sometimes over a private native facility,
and follow the same published semantic contracts. The one exception is
`std:inspect`: its public contract is the same in both, but its
diagnostic text is implementation-specific, and a physical representation it
describes may be unavailable on a given runtime, in which case the operation
signals an ordinary `Error` (see [Runtime inspection](inspect.md)):

- **Native Linux x86_64** — a self-contained executable for Linux x86_64 with
  dynamic glibc 2.39 or newer. It needs no external JDK. Guest code runs
  interpreter-only: guest JIT compilation is disabled under PLAT045, so
  compute-heavy library work (for example large regex searches) is slower than
  on the JVM, but results are identical. `std:runtime` deliberately reports which
  distribution is running through its `platform` field.
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
