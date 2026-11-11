# Explicit foreign operations: `std:interop`

`std:interop` provides four explicit operations on **foreign values** — values
that a foreign provider handed to your program, such as a foreign module facade
or a raw foreign reference. It is the escape hatch for the cases ordinary
Protos syntax deliberately does not cover.

```protos
Interop: import("std:interop")
```

The module is a source-backed MODULE,
[`protos/lib/interop/module.protos`](../../../protos/lib/interop/module.protos). Its normative
owner is "Relation to explicit interoperability" in
[`spec/semantics/VALUES_AND_COLLECTIONS.md`](../../../spec/semantics/VALUES_AND_COLLECTIONS.md).
For the overall foreign-value model, start with
[Foreign interoperability](../interop/README.md).

## Operations

| Operation | Effect |
|---|---|
| `Interop.invoke(target, ...arguments)` | Executes the foreign target and answers its admitted result |
| `Interop.instantiate(target, ...arguments)` | Instantiates the foreign target and answers its admitted result |
| `Interop.readMember(target, name)` | Reads foreign member `name` (a String) |
| `Interop.writeMember(target, name, value)` | Writes foreign member `name` and answers exactly `value` |

Why they exist:

- `invoke` and `instantiate` are distinct even when a target supports both;
  ordinary `call` does not choose between them for you.
- `readMember` bypasses ordinary Protos lookup for one operation, so a foreign
  member named `call`, `at`, `each`, `==`, or `hash` is reachable.
- `writeMember` is the only way to write a foreign member: ordinary
  `target.name = value` never writes to the foreign object.

```protos
Interop: import("std:interop")
Remote: import("example:service")   // hypothetical scheme of a loaded provider

Interop.readMember(Remote, "hash")       // the foreign member, not Protos `hash`
Interop.writeMember(Remote, "timeout", 30)
```

Importing `std:interop` opens no provider session, performs no discovery, and
grants no authority. It does not change how ordinary syntax behaves on foreign
values.

## Failures

- A failure detected **before** the foreign operation is entered signals an
  ordinary Error: the target is not foreign, wrong arity, a `name` that is not a
  String, an operation the target does not support, an argument that cannot be
  projected losslessly, or a closed session. For example,
  `Interop.invoke(42)` signals an Error.
- A failure **after** entry signals a fresh `ForeignError` with only safe slots
  (`language`, `operation`, `category`, `foreignCategory`, `message`,
  `cause`); host exceptions and stack traces are never exposed.

A Closure passed as an argument crosses only as a synchronous callback scoped to
that one operation. Callbacks do not cross the public external provider SPI at
this revision, so a provider loaded with `--foreign-provider-path` cannot call
back into Protos.

## Where foreign values come from

No foreign provider ships inside the Protos distribution. To obtain foreign
values today you load an external provider JAR on the **portable JVM** CLI:

```sh
protos --foreign-provider-path provider.jar main.protos
```

See [Writing and registering an external provider](../interop/custom-provider.md)
and the [`java:` provider fixture](../interop/java-provider.md). The Java
standard-library fixture uses the `java:` scheme; `jdk:` is not a provider
scheme and is not an alias for it.

## JVM and Native

| Distribution | `std:interop` |
|---|---|
| Portable JVM | Module available; operates on values from providers loaded with `--foreign-provider-path` |
| Native Linux x86_64 | Part of the shipped Standard Library, but the Native CLI cannot load external providers, so there are no foreign values for it to act on |

Java embedding with a standard Polyglot `Context` is a portable-JVM feature
only, and even there no public API registers foreign providers; see
[Embedding Protos in a Java application](../embedding/java.md).

## Public C declarations: `std:ffi`

`std:interop` operates explicitly on existing foreign values; `std:ffi` is the
separate Protos-first MODULE for public C declarations. The external `ffi:`
provider supplies private backend operations and native authority only when
the host authorizes it. Importing `std:ffi` alone opens no native session and
grants no authority.

Run the program by explicitly authorizing the provider on the portable JVM CLI:

```sh
bin/protos --foreign-provider-path /path/to/protos-ffm-provider.jar \
  protos/examples/ffi/sqlite-version-public.protos
```

```protos
FFI: import("std:ffi")
sqlite: import("ffi:sqlite3")
version: FFI.bind(sqlite, "sqlite3_libversion_number", FFI.i32, Array())
number: version()
```

For descriptor domains, Closure bindings, `out` result Arrays, owned resources,
security boundaries and runnable public examples, see the dedicated
[Native C declarations with `std:ffi` guide](ffi.md). The public contract is
defined by [`spec/semantics/FFI.md`](../../../spec/semantics/FFI.md).

Continue with the [descriptor tutorial](../../../protos/tutorials/08-language-interactions/08-public-ffi-descriptors.protos),
[binding tutorial](../../../protos/tutorials/08-language-interactions/09-public-ffi-bindings.protos),
[owned/out tutorial](../../../protos/tutorials/12-system-resources/05-public-ffi-owned-out.protos),
[SQLite version example](../../../protos/examples/ffi/sqlite-version-public.protos),
[SQL completeness example](../../../protos/examples/ffi/sqlite-complete-public.protos)
or [numeric query example](../../../protos/examples/ffi/sqlite-numeric-query-public.protos).

For the higher-level [SQLite library](sqlite.md) (LIB029), use `std:sqlite`
for in-memory connections, Statements, numeric/NULL binding and copied rows.
Its borrowed-memory adaptation stays private, with a 16 MiB copy bound;
public R1 adds no `FFI.copy`, `FFI.borrowed` or `FFI.preserving`. TEXT/BLOB
input binding remains deferred.
