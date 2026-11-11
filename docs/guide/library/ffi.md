# Native C declarations with `std:ffi`

`std:ffi` is the Protos-first public API for declaring supported C functions as
ordinary Protos Closures. It is an optional, explicitly authorized native-code
path, not a SQLite library or a general-purpose memory API. The normative
contract is [`spec/semantics/FFI.md`](../../../spec/semantics/FFI.md).

## Three separate layers

- **`std:ffi`** is a source-backed MODULE made of ordinary frozen descriptors
  and Closures. Protos code declares the ABI, makes calls, checks C results and
  manages owned handles.
- **`std:interop`** provides the four explicit operations `invoke`,
  `instantiate`, `readMember` and `writeMember` on foreign values. It does not
  declare C signatures; see [Explicit foreign operations](interop.md).
- **`ffi:`** is the private scheme of an optional external provider. The I144
  FFM provider supplies generic backend operations and a host-authorized
  `ffi:sqlite3` facade. Its private `signature`/`param`/`bind` API is for
  backend authors, not the recommended application API; see the
  [I144 provider guide](../interop/ffm-provider.md).

The provider JAR is executable Java code and authorized C is trusted native
code. The host chooses whether to load the provider and which library it may
open. Neither importing `std:ffi` nor constructing descriptors grants that
authority.

## Availability and setup

The current external FFM provider supports the **portable JVM CLI** on **Linux
x86-64 LP64**, with **Java 25** and the supported **GraalVM 25.5** toolchain. It
requires an external FFM provider JAR and a shared SQLite library available to
the native loader as `libsqlite3.so.0`. It is not available in Native Image:
Native Image cannot load external provider plugins.

Build the portable distribution and inspect its actual versioned archive:

```sh
make dist
ls target/distributions/
```

`make dist` normally requires a clean working tree. For pre-commit validation
from a dirty tree, the distribution can instead be prepared with:

```sh
python3 dist/build_portable.py --allow-dirty
```

Build the separate provider JAR against the archive produced for that project
version:

```sh
python3 dist/build_ffm_provider.py \
    target/distributions/protos-<version>-posix-jvm.zip \
    --output target/ffm-provider
```

Use the archive name actually present in `target/distributions/`. Building the
JAR grants nothing. Each run must explicitly authorize it with
`--foreign-provider-path`.

## Imports and authority

```protos
FFI: import("std:ffi")
```

This is an ordinary Protos MODULE import. Re-importing the same MODULE in one
Actor returns its cached identity. Importing it does not discover a native
provider, open a native session, load a shared library or grant native
authority.

```protos
sqlite: import("ffi:sqlite3")
```

This is a distinct foreign import. It succeeds only when the host explicitly
loads the external provider, and its logical alias is authorized by that
provider. The application passes the resulting library facade to `FFI`;
`FFI.bind` never accepts a path or a library name String as authority.

```sh
bin/protos --foreign-provider-path target/ffm-provider/protos-ffm-provider.jar \
    protos/examples/ffi/sqlite-version-public.protos
```

The public sample is
[`sqlite-version-public.protos`](../../../protos/examples/ffi/sqlite-version-public.protos).

## The twelve public members

The MODULE exports exactly six scalar descriptor values and six ordinary
Closures:

| Member | Signature or value | Meaning and restrictions |
|---|---|---|
| `FFI.void` | scalar descriptor | Return-only C `void`; direct Protos result is `null`. |
| `FFI.i32` | scalar descriptor | Signed 32-bit Integer descriptor. |
| `FFI.i64` | scalar descriptor | Signed 64-bit Integer descriptor. |
| `FFI.f64` | scalar descriptor | C binary64 `double`; Protos `Float` only. |
| `FFI.sizeT` | scalar descriptor | Initial LP64 `size_t`, unsigned 64-bit Integer. |
| `FFI.utf8z` | scalar descriptor | Input-only String encoded as strict UTF-8 and terminated with NUL. |
| `FFI.opaque(library, nativeTypeName)` | Closure | Declares a non-null opaque reference tied to this library and session. The type name is a C identifier. |
| `FFI.nullable(opaqueDescriptor)` | Closure | Admits `null` for a plain opaque descriptor; does not accept scalar, owned or `out` descriptors. |
| `FFI.owned(opaqueDescriptor, releaseSymbol, releaseReturnDescriptor)` | Closure | Declares an owned opaque result and its one-reference destructor; destructor result descriptor is `void` or `i32`. |
| `FFI.out(descriptor)` | Closure | Declares zero-initialized native output storage and consumes no Protos argument. Targets are `i32`, `i64`, `f64`, `sizeT`, opaque, nullable opaque and owned opaque; nested `out`, `void`, `utf8z` and non-descriptors are rejected. |
| `FFI.bind(library, symbol, returnDescriptor, argumentDescriptors)` | Closure | Resolves a supported declaration through the already-authorized library and returns a new ordinary Protos Closure. The final parameter must be an ordinary Array of descriptors. |
| `FFI.release(ownedHandle)` | Closure | Irreversibly retires a registered owned handle, calls its destructor at most once and returns the exact C result. |

These are the public members, not a public descriptor record layout. Descriptor
objects are ordinary frozen Protos objects; structurally similar objects do not
become descriptors.

## Scalar descriptors

The six scalar descriptors have distinct identities. Each is an ordinary,
frozen Protos object, but each declares a different ABI domain:

| Descriptor | Exact Protos domain | C-side behavior |
|---|---|---|
| `FFI.void` | Return only | No C result; maps to `null`. |
| `FFI.i32` | Integer from `-2147483648` through `2147483647` | Exact signed 32-bit value; no truncation or wrapping. |
| `FFI.i64` | Integer from `-9223372036854775808` through `9223372036854775807` | Exact signed 64-bit value. |
| `FFI.sizeT` | Integer from `0` through `18446744073709551615` in the initial LP64 profile | Unsigned 64-bit `size_t`; values above signed 64-bit range remain exact. |
| `FFI.f64` | Protos `Float` | IEEE binary64; an Integer is not implicitly converted. |
| `FFI.utf8z` | Input Protos `String` | Strict UTF-8 plus a trailing NUL; interior NUL and invalid Unicode are rejected. |

Native integral results are exact Protos Integers. There is no implicit
numeric conversion, pointer-to-Integer conversion or pointer construction.
`void` and `utf8z` are not output-position types.

## First binding

SQLite's `sqlite3_libversion_number` takes no arguments and returns a C `int`.
The public declaration and call stay in Protos:

```protos
FFI: import("std:ffi")
sqlite: import("ffi:sqlite3")

version: FFI.bind(sqlite, "sqlite3_libversion_number", FFI.i32, Array())
number: version()
```

The binding is an ordinary Protos Closure, so normal Closure identity and
invocation rules apply. The SQLite code is data: check it in Protos rather than
expecting the provider to interpret it.

## Arguments and binding snapshots

For a parameter, put its descriptor in the ordinary Array passed to `FFI.bind`:

```protos
complete: FFI.bind(sqlite, "sqlite3_complete", FFI.i32, Array(FFI.utf8z))
result: complete("SELECT 1;")
```

The descriptor Array is shallowly copied when the binding is constructed.
Later changes to the caller's Array do not alter its retained ABI signature.
Parameters are non-void scalar inputs, opaque or nullable opaque references, or
supported `out` descriptors; an owned descriptor is for a result, not a direct
input parameter.

At invocation, arguments are admitted in order and must match the declared
arity, exact kind, range, nullability and resource lifetime. Invalid calls are
rejected before C entry.

See the runnable [public binding tutorial](../../../protos/tutorials/08-language-interactions/09-public-ffi-bindings.protos)
and the [SQL completeness example](../../../protos/examples/ffi/sqlite-complete-public.protos).

## Output parameters

An `FFI.out(T)` descriptor declares a C output parameter but consumes no
Protos call argument. Zero-initialized output storage is allocated for each
invocation; creating the descriptor does not acquire or expose native memory.
For example,
the four-parameter C function `sqlite3_open_v2` receives only three Protos
arguments when its second parameter is `FFI.out(dbOwned)`.

When a binding has output positions, every invocation returns a fresh ordinary
Protos Array:

```text
[nativeReturn, out0, out1, ...]
```

The first element is `null` for a `void` return. Outputs follow C parameter
order. The provider's private indexed result is not exposed as the public
result. Nonzero C status codes remain values that Protos code must inspect.

## Owned resources

Use `FFI.opaque` for a logical C reference type and `FFI.owned` for the
descriptor of a return or `out` result that transfers custody to the provider.
Use the corresponding plain opaque descriptor when passing that handle back to
C. No native address is exposed, and identity belongs to each acquisition in
its originating library, session and generation, not to an address.

Every non-null owned acquisition enters provider custody before publication,
including a handle returned alongside an error status or a result that cannot
be converted for Protos. A C error status does not cancel that custody. The
provider also releases remaining owned resources at session shutdown.

`FFI.release(handle)` removes the public identity association before invoking
the original destructor. Retirement is irreversible: the exact destructor
result is returned, but an exception or non-success result does not permit a
retry. A duplicate release and any later attempt to pass the released handle
to C are rejected. For SQLite statements and connections, finalize the
statement before closing its connection. These declarations do not create a
`Closable` protocol or a Future.

The runnable
[owned/out tutorial](../../../protos/tutorials/12-system-resources/05-public-ffi-owned-out.protos)
and [numeric query example](../../../protos/examples/ffi/sqlite-numeric-query-public.protos)
show this sequence using only public declarations.

## C results and Protos errors

A SQLite status other than `SQLITE_OK` is an ordinary C result. For example,
`sqlite3_step` returns `SQLITE_ROW` and later `SQLITE_DONE`; neither is
automatically an Error. The program decides what those values mean.

An argument or descriptor rejection detected before entering C signals an
ordinary Protos `Error`. A failure after entering a provider operation, such
as resolving a missing symbol, signals `ForeignError` under the existing
foreign-value rules. Native crashes, memory corruption and process termination
are not recoverable Protos errors.

## Borrowed memory is not public in R1

There is no public borrowed-memory descriptor or public `FFI.copy`,
`FFI.borrowed` or `FFI.preserving` operation. SQLite TEXT and BLOB column
readers return pointers into memory owned by SQLite. Because R1 does not expose
the required lifetime-bounded copying operation, those functions cannot be
taught as available public `std:ffi` calls. The private I144 guide retains its
historical borrowed-memory material for backend authors and existing tests;
that private contract is not an application API.

R1 also does not expose a SQLite connection abstraction, TEXT/BLOB input
binding, callbacks, structs, variadic calls, C++, arbitrary allocators or
`SQLITE_TRANSIENT`. Public `std:ffi` itself has no SQLite Connection or
Statement objects. Those abstractions are implemented separately in ordinary
Protos by LIB029 as [`std:sqlite`](sqlite.md), which application developers
should use for SQLite. Its SQLite-specific borrowed-memory adaptation remains
private to that library: it adds no public `FFI.copy`, `FFI.borrowed` or
`FFI.preserving`, and TEXT/BLOB input binding remains unsupported there too.

## Security and limits

Native execution is trusted and in-process. The C ABI is declared manually;
the provider can check argument admission but cannot prove that the declared
signature matches the native function. An incorrect signature can corrupt
memory or terminate the process. There is no sandbox, and Protos `Filesystem`
authority does not confine C code. The provider accepts only host-authorized
logical library aliases, not paths supplied to `FFI.bind`.

This FFM profile is not a Native Image portability promise. Native Image cannot
load this external provider JAR. Platform support is limited to the portable
JVM, Java 25, supported GraalVM 25.5 and Linux x86-64 LP64.

## Actors and isolated parallel execution

Closures that capture a native binding, live native resources and native
authority do not automatically cross Actor boundaries or isolated P execution.
Importing `std:ffi` in another Actor produces that Actor's ordinary local
MODULE; it does not transfer a provider session or host grant. Native calls are
synchronous and do not implicitly schedule parallel work.

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| `import("ffi:sqlite3")` reports an ordinary resolution Error | The host did not pass the external JAR with `--foreign-provider-path`, or provider registration failed. Importing `std:ffi` alone is not authorization. |
| An alias is denied | The distributable I144 provider authorizes `ffi:sqlite3`, not an arbitrary scheme, filename or path. |
| Binding a symbol signals `ForeignError` | The symbol may not exist in the authorized library or its searched dependencies; check the C symbol spelling and library available to the loader. |
| A call signals ordinary `Error` before C entry | Check argument count, exact descriptor kinds and ranges, `utf8z` interior NUL, nullability, handle type and handle liveness. |
| A released resource is rejected | Release is one-way. Do not retry its destructor or pass that handle to another binding. |
| The provider reports an unsupported platform | Use the supported portable JVM on Linux x86-64 LP64 with Java 25/GraalVM 25.5; Native Image and other platforms are outside this profile. |

Further runnable programs: the existing
[SQLite version example](../../../protos/examples/ffi/sqlite-version-public.protos),
the [descriptors tutorial](../../../protos/tutorials/08-language-interactions/08-public-ffi-descriptors.protos),
the [bindings tutorial](../../../protos/tutorials/08-language-interactions/09-public-ffi-bindings.protos),
the [owned/out tutorial](../../../protos/tutorials/12-system-resources/05-public-ffi-owned-out.protos),
the [SQL completeness example](../../../protos/examples/ffi/sqlite-complete-public.protos)
and the [numeric query example](../../../protos/examples/ffi/sqlite-numeric-query-public.protos).
