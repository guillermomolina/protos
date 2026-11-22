<p align="center">
  <img src="../assets/branding/protos-logo.png" alt="Protos" width="280">
</p>

# Protos Programming Guide

Learn to write and run Protos programs, then find the tools and libraries you
need for your application.

## Start here

1. [Install and run Protos](00-try-protos.md). Choose the Linux Native
   installer, Dev Container, or portable JVM distribution for your environment.
2. [Write your first program](first-program.md). Build a small order summary
   using values, a function, an Array, a loop, and a conditional. Each step
   includes the output to expect and something to change yourself.
3. Follow the language lessons below, running the linked tutorial programs as
   you read. Return to the detailed explanations when you need them.

The guide follows the source in this checkout. An older installed release may
not provide every feature described here; check `protos --version` when a
command or example is unavailable.

## Learn the language

Work through the first six lessons before moving to concurrency or I/O.
The chapters explain the mechanisms behind the examples; you do not need to
read every section before writing your next program.

| Lesson | What you will do | Practice |
|---|---|---|
| [1. Values and bindings](01-bindings-contexts-and-state.md) | Create a binding with `:`, update it with `=`, and use local state | [Values and slots](../../protos/tutorials/01-values-and-slots/) |
| [2. Objects](02-objects-delegation-and-composition.md) | Group state in objects and reuse behavior through delegation | [Objects](../../protos/tutorials/02-objects/) |
| [3. Functions and methods](03-closures-methods-and-receivers.md) | Pass arguments, capture state, and invoke behavior on a receiver | [Closures](../../protos/tutorials/03-closures/) |
| [4. Decisions and loops](04-control-flow-through-protocols.md) | Use `if`, `while`, and `for` | [Control flow](../../protos/tutorials/04-control-flow/) |
| [5. Collections](05-values-identity-equality-and-collections.md) | Index Arrays, work with Maps, and choose equality or identity | [Collections](../../protos/tutorials/05-collections/) |
| [6. Modules](06-modules-and-imports.md) | Split a program into files and import a library | [Imports and modules](../../protos/tutorials/08-language-interactions/) |
| [7. Errors and cleanup](07-errors-handlers-ensure-and-resource-lifetime.md) | Handle failures and release resources with `ensure` | [Cleanup pattern](07-errors-handlers-ensure-and-resource-lifetime.md#a-practical-acquisitioncleanup-pattern) |
| [8. Futures](08-futures-and-structured-concurrency.md) | Start asynchronous work and observe its result | [Futures](../../protos/tutorials/09-futures/) |
| [9. Parallel computation](09-isolated-parallel-execution.md) | Choose isolated computation when work should run in parallel | [Concurrency choices](09-isolated-parallel-execution.md) |
| [10. Actors](10-actors-actorrefs-and-groups.md) | Keep independent state and communicate by messages | [Actors](../../protos/tutorials/10-actors/) and [Groups](../../protos/tutorials/11-actor-groups/) |
| [11. Files and I/O](11-process-io-filesystems-and-authority.md) | Read and write through explicitly granted capabilities | [System resources](../../protos/tutorials/12-system-resources/) |
| [12. Matching](12-matching-expressions.md) | Match values and select cases through ordinary messages | [Matcher protocol](12-matching-expressions.md#the-root-matcher-protocol) |

The [tutorial overview](../../protos/tutorials/README.md) explains how to run
lessons and which require additional host permissions. Start with the
permission-free language lessons; filesystem, database, and network programs
have their own setup instructions.

## Find a recipe

Use the [examples cookbook](../../protos/examples/README.md) when you have a
specific task. These are useful next steps after the first program:

- [Return early from a callback](../../protos/examples/closures/non-local-return.protos).
- [Sample a range with a step](../../protos/examples/algorithms/stride-sampling-summary.protos).
- [Use flags and masks](../../protos/examples/numbers/bit-flags-and-masks.protos).
- [Organize a small module project](../../protos/examples/modules/public-module-pricing/package.toml).
- [Query SQLite with prepared parameters](library/sqlite.md).
- [Make or serve an HTTP request](library/http.md).

## Toolchain guides

- [Bundled Tools](tools/README.md) — what an official bundled Tool is, which
  Tools currently ship, and where Tool policy stops and language/runtime
  behavior begins.
- [The `protos` command line](tools/cli.md) — program execution, `format`,
  `lint`, `debug`, `run`, exit codes, and Native/JVM differences.
- [Language server](tools/language-server.md) — LSP capabilities, editor
  configuration, project binding, and what is not provided.
- [Package Tool](tools/package-tool.md) — `protos package manifest` and the
  current boundary of package operations.
- [Test Tool](tools/test-tool.md) — current `protos test` corpus and
  expectation model, isolated case execution, deterministic reporting,
  bounded `--jobs` parallelism, and Case selection/listing.

## Standard Library guides

- [Standard Library overview](library/README.md) — importing `std:` modules
  and Native/JVM behavior.
- [Regular expressions](library/regex.md) — `std:regex/regex`.
- [Dates, times, and durations](library/datetime.md) — `std:datetime/*`.
- [Structured logging](library/logging.md) — `std:logging/*`.
- [Text styling and encodings](library/text.md) — `std:text/*`.
- [Cryptography: SHA-256 and HMAC-SHA256](library/crypto.md) — exact Bytes,
  keyed full-tag verification, limits, and key handling.
- [Semantic versions](library/semver.md) — `std:semver/sem_ver`.
- [HTTP/1.1](library/http.md) — `std:http/server`, `std:http/host`,
  `std:http/client`, `std:http/router`, and `std:http/url_client`, with their
  Network authority and lifetime rules.
- [Explicit networking and name resolution](library/networking.md) —
  `network`, `std:network/resolvers`, `IpAddress`, and `IpEndpoint`.
- [TLS client](library/tls.md) — `std:tls/client`, explicit trust, and
  reference identity.
- [SQLite](library/sqlite.md) — `std:sqlite` learning path:
  connections, prepared parameters, copied rows, transactions, errors and
  cleanup over the explicitly authorized FFM provider.
- [Native C declarations](library/ffi.md) — optional, explicitly authorized
  `std:ffi` interoperability through an external provider.
- [Vectors and matrices](library/linear-algebra.md) — `std:math/linear_algebra`.
- [Explicit foreign operations](library/interop.md) — `std:interop`.
- [Numeric functions](library/README.md) — `std:math/math` and `std:math/integer`;
  see also [numeric values](05-values-identity-equality-and-collections.md).
- [Runtime metadata](library/runtime.md) — `std:runtime` and its four
  portable String fields.
- [Runtime inspection](library/inspect.md) — `std:inspect`, available only
  with explicit inspection authorization (`--inspect` in the CLI).

## Java integration guides

- [Embedding Protos in a Java application](embedding/java.md) — standard
  Polyglot `Context`, evaluation, bindings, Closure invocation, Core location,
  authority, and lifecycle on the portable JVM runtime.
- [Supplying application modules from Java](embedding/application-modules.md)
  — the `app:` catalog installed with `ProtosEmbeddedModules.install`.
- [Foreign interoperability](interop/README.md) — map of resolvers, foreign
  providers, and what is available at the current revision.
- [Writing and registering an external provider](interop/custom-provider.md)
  — the public `spi.foreign` contract and `--foreign-provider-path`.
- [The `java:` provider fixture](interop/java-provider.md) — `java:math` and
  `java:date` through the I085-B test fixture.
- [The optional external FFM provider](interop/ffm-provider.md) — HOWTO for
  the real `ffi:sqlite3` provider (I144): building and authorizing its JAR,
  native lifetime, `out` parameters, owned resources, and borrowed memory.
  For application C declarations, start with the [FFI guide](library/ffi.md).

## Reference

The chapters are explanatory. The [language specification](../../spec/PROTOS_LANGUAGE_SPEC.md)
and [grammar](../../spec/PROTOS_GRAMMAR.md) define syntax and behavior.
[Source style](SOURCE_STYLE.md) explains the repository's conventions for
hand-written examples.
