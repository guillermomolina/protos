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

LIB028 supplies a separate Protos-first MODULE over the optional I144 FFM
provider. `std:interop` operates explicitly on existing foreign values;
`ffi:` supplies private backend operations and native authority granted by the
host; `std:ffi` composes public descriptors, ordinary Closures and result Arrays.
Its normative owner is [FFI.md](../../../spec/semantics/FFI.md).

Authorize the provider explicitly on the portable JVM CLI (Linux x86-64,
JDK 25 / supported GraalVM 25.5):

```sh
bin/protos --foreign-provider-path /path/to/protos-ffm-provider.jar \
    protos/examples/ffi/sqlite-version-public.protos
```

```protos
FFI: import("std:ffi")            // no native authority or session
sqlite: import("ffi:sqlite3")     // separately authorized by the host
version: FFI.bind(sqlite, "sqlite3_libversion_number", FFI.i32, Array())
number: version()
```

Use `FFI.void` for a void return, `i32` and `i64` for signed exact Integers,
`sizeT` for an unsigned LP64 Integer, `f64` for Float, and `utf8z` for input
String without interior NUL. Numeric coercion, truncation and wrapping are
rejected. `utf8z` is input-only; native pointer addresses are never Integers.
`bind` snapshots its parameter Array and answers a fresh ordinary Closure.
All invocation arguments are passed in order through the existing I085 admission
checks. A void function without outputs returns `null`.

`FFI.opaque(library, nativeTypeName)` declares a non-null logical reference;
`FFI.nullable(opaque)` additionally admits `null`.
`FFI.owned(opaque, releaseSymbol, releaseReturnDescriptor)` declares result
custody with a one-reference destructor returning `FFI.void` or `FFI.i32`.
The opaque descriptor must be plain, and retains its original library/session.
Use the opaque input descriptor to pass an owned handle to another function;
the owned declaration itself is for results.

`FFI.out(descriptor)` declares an output position consuming no Protos argument.
Each native invocation allocates its zero-initialized output storage.
The call returns a fresh ordinary Array
`[nativeReturn, out0, ...]`. Outputs support numeric and opaque/owned types,
not `void`, `utf8z`, or nested `out`. Nonzero C status codes remain data.
An owned output remains under custody even when the accompanying status is an
error or conversion of another result fails.

`FFI.release(handle)` uses private identity-based origin metadata: no library
argument is needed. It retires that entry before delegating to the provider,
returns the exact destructor result, and never retries after a failure or error
status. Duplicate release, arbitrary objects and retired handles are rejected.
The provider releases outstanding owned acquisitions when their session ends.

Validation before native entry signals an ordinary Error; an entered provider
operation failure signals `ForeignError`, following D188. There is no new Error
hierarchy. Calls are synchronous, and native resources/authority cannot silently
cross Actors or P. Importing this module does not inherit native authority.

R1 exposes no `FFI.copy`, `FFI.borrowed` or `FFI.preserving`. Borrowed-memory
copying remains private I144 machinery for later LIB029 work, bounded to 16 MiB
and the provider's borrow epoch. Public `bind` does not publish borrowed-memory
wrappers. There is no public TEXT/BLOB input binding or SQLite connection API.

The ABI is trusted: a wrong signature can corrupt memory or terminate the
process. C runs with process authority, unconstrained by `Filesystem`, and
arbitrary native faults are not recoverable by promise. No pointer arithmetic,
numeric pointers, structs, callbacks/upcalls, variadic calls, C++ ABI,
`SQLITE_TRANSIENT`, arbitrary library paths or Native Image external plugins
are supported. See the [provider guide](../interop/ffm-provider.md) for host
configuration and the retained private low-level examples.
