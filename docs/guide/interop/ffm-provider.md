# The optional external FFM provider (`ffi:`)

> **Status:** Non-normative developer guide (I144). The provider operations
> described here are external support machinery, not the public `std:ffi`
> library implemented in Protos by LIB028. Applications declaring C functions
> supported by R1 should start with the
> [public `std:ffi` guide](../library/ffi.md). This guide remains useful for
> authors of the generic backend and for private operations not published by R1.

The FFM provider (`providers/ffm`) is an external I085 provider that calls C
functions of host-authorized shared libraries through the Java 25 Foreign
Function and Memory API. Unlike the `java:`, `inventado:`, and `oslib:` test
fixtures, it is a real artifact; like them, it is built and loaded outside the
Protos distribution. See also the provider's own
[README](../../../providers/ffm/README.md).

> **Warning.** Validation catches wrong arities, kinds, ranges, NUL bytes, and
> stale references, but it cannot check that a declared signature matches the
> C function. A wrong declaration can corrupt memory or terminate the
> process. Native code runs in process with full process authority; Protos
> `Filesystem` does not confine it.

Contents:

1. [Architecture](#architecture)
2. [Requirements](#requirements)
3. [Build the provider JAR](#build-the-provider-jar)
4. [Authorize and run](#authorize-and-run)
5. [Protos-first flow](#protos-first-flow)
6. [ABI descriptors and operations](#abi-descriptors-and-operations)
7. [A first call: the SQLite version](#a-first-call-the-sqlite-version)
8. [Output parameters](#output-parameters)
9. [Owned resources](#owned-resources)
10. [Borrowed memory](#borrowed-memory)
11. [Errors](#errors)
12. [Registration (I085)](#registration-i085), [trust boundary](#trust-boundary),
    [resources and borrowed memory](#resources-and-borrowed-memory) (reference)
13. [Security and limits](#security-and-limits)
14. [Relationship to LIB028 and LIB029](#relationship-to-lib028-and-lib029)

Executable material: the tutorials
[`07-ffi-provider-basics.protos`](../../../protos/tutorials/08-language-interactions/07-ffi-provider-basics.protos)
and
[`04-ffi-ownership-and-borrows.protos`](../../../protos/tutorials/12-system-resources/04-ffi-ownership-and-borrows.protos),
and the examples in [`protos/examples/ffi/`](../../../protos/examples/ffi/README.md).

Public application examples using only the supported `std:ffi` surface are
[`sqlite-version-public.protos`](../../../protos/examples/ffi/sqlite-version-public.protos),
[`sqlite-complete-public.protos`](../../../protos/examples/ffi/sqlite-complete-public.protos)
and
[`sqlite-numeric-query-public.protos`](../../../protos/examples/ffi/sqlite-numeric-query-public.protos).


```text
Protos application -> std:ffi (ordinary descriptors, Closures, Arrays)
    |
    | private operations on import("ffi:sqlite3")
    v
I085 foreign provider SPI
    |
    v
Optional FFM provider JAR (I144)
    |
    v
Java 25 Foreign Function & Memory API
    |
    v
Host-authorized libsqlite3.so.0
```

- The provider is **external and optional**. It is not part of
  `lib/protos.jar` and is not shipped in the distribution; you build it
  separately against the distribution's public SPI.
- The JAR carries **no embedded authorization**. Whether it runs at all, and
  which library it may load, is decided by the host at start-up.
- The distributable provider accepts exactly one target, `ffi:sqlite3`. Any
  other alias, file name, or path is refused.
- The CLI currently has **no option channel** for providers. The absolute
  library path option `sqlite3.path` can only be supplied by a Java host
  through `ProtosForeignProviderConfiguration.withProviderOptions`. Without
  it, the provider loads the library by its loader name `libsqlite3.so.0`.
- `std:ffi` is the public Protos layer. Importing it grants no authority;
  the members of `ffi:sqlite3` remain provider-private operations.

## Requirements

- Linux x86-64 (LP64); other platforms are rejected on first use;
- JDK 25 (to build the JAR and to run the portable CLI);
- the portable JVM distribution — the provider is **not** available in
  Native Image, which cannot load providers dynamically;
- a shared SQLite library reachable by the native loader as
  `libsqlite3.so.0`.

## Build the provider JAR

1. Build the portable distribution:

   ```sh
   make dist
   ls target/distributions/
   ```

   The archive is named after the project version in `pom.xml`, as
   `protos-<version>-posix-jvm.zip`. Use the name actually listed; do not
   guess it.

2. Compile the provider against that distribution:

   ```sh
   python3 dist/build_ffm_provider.py \
       target/distributions/protos-<version>-posix-jvm.zip \
       --output target/ffm-provider
   ```

   [`dist/build_ffm_provider.py`](../../../dist/build_ffm_provider.py)
   accepts the ZIP or an extracted installation directory, compiles
   `providers/ffm/src/main/java` with `javac --release 25` against the
   distribution's `lib/protos.jar`, adds the provider's `META-INF/services`
   descriptor, and writes only `protos-ffm-provider.jar` into `--output`. The
   distribution is never modified.

## Authorize and run

Building the JAR authorizes nothing. Each run authorizes it explicitly:

```sh
bin/protos --foreign-provider-path target/ffm-provider/protos-ffm-provider.jar program.protos
```

A JAR on `CLASSPATH`, in the working directory, or inside the installation is
never discovered. Without `--foreign-provider-path`, `import("ffi:sqlite3")`
fails with an ordinary resolution Error.

## Protos-first flow

1. The host builds the JAR (`dist/build_ffm_provider.py`) and starts the
   portable JVM CLI with `--foreign-provider-path`. Registration runs only
   `providerId()` and `scheme()`: nothing native happens.
2. A program imports an authorized logical library, for example
   `import("ffi:sqlite3")`. Only now does the Actor's session open, the
   platform get checked (Linux x86-64 LP64), and the library load.
3. Protos code builds descriptors and signatures from provider handles and
   scalars, binds symbols, and calls them. Control flow, declarations, and
   the reconstruction of Arrays and Bytes are Protos source.

```protos
sqlite: import("ffi:sqlite3")

signature: (returns, parameters) => {
    composed: sqlite.signature(returns)
    parameters.each((parameter) => { composed = sqlite.param(composed, parameter) })
    composed
}

dbType: sqlite.opaque("sqlite3")
db: sqlite.owned(dbType, "sqlite3_close_v2", sqlite.i32)
open: sqlite.bind("sqlite3_open_v2", signature(
    sqlite.i32,
    Array(sqlite.utf8z, sqlite.out(db), sqlite.i32,
          sqlite.nullable(sqlite.opaque("sqlite3_vfs_name")))))

opened: open(":memory:", 6, null)   // [status, connection]
connection: opened[1]
sqlite.release(connection)          // sqlite3_close_v2 result, unchanged
```

`out` positions consume no argument; a binding with outputs answers the
provider-private indexed value `[cResult, out0, ...]`. A non-zero C status is
returned as data.

## ABI descriptors and operations

### Descriptors

| Descriptor | C meaning | Protos side |
|---|---|---|
| `void` | no value (return only) | `null` |
| `i32` | `int` / `int32_t` | Integer in the exact signed 32-bit range |
| `i64` | `int64_t` / `sqlite3_int64` | Integer in the exact signed 64-bit range |
| `f64` | `double` | Float |
| `sizeT` | `size_t` (unsigned 64-bit) | non-negative Integer |
| `utf8z` | `const char *` input, NUL-terminated UTF-8 | String without interior NUL (input only) |
| `opaque(name)` | pointer to a C type of logical name `name` | opaque reference, never an address |
| `nullable(opaque)` | the same pointer, admitting C `NULL` | reference or `null` |
| `owned(opaque, destructorSymbol, destructorReturnType)` | pointer whose lifetime the session takes over; `destructorReturnType` is `void` or `i32` | owned reference |
| `borrowed` | pointer to memory the C library still owns (return only) | borrowed region, read only with `copy` |
| `out(type)` | `type *` output slot | no argument; value returned after the C result |

Integers are never truncated or wrapped, and no Integer converts to a
pointer.

### Operations
These `signature`, `param`, `preserving`, `bind` and `copy` operations are
**private to the I144 provider**, retained for backend authors and boundaries
that are not public in R1. Protos applications should use
[`std:ffi`](../library/ffi.md) for supported C declarations.

| Operation | Purpose |
|---|---|
| `signature(returnType)` | starts a signature with no parameters |
| `param(signature, type)` | answers a new signature with one more parameter |
| `preserving(signature)` | marks a signature whose calls do not invalidate borrowed regions |
| `bind(symbol, signature)` | resolves `symbol` in the authorized library once; answers a callable binding |
| `release(owned)` | releases an owned reference now; answers the destructor's C result |
| `isLive(reference)` | whether a reference or borrowed region is still usable |
| `copy(borrowed, length)` | bounded copy of a borrowed region into an independent indexed value of octets |

Signatures are built one parameter at a time. The usual Protos-first pattern
folds an ordinary Array of descriptors:

```protos
signature: (returns, parameters) => {
    composed: sqlite.signature(returns)
    parameters.each((parameter) => {
        composed = sqlite.param(composed, parameter)
    })
    composed
}

prepare: sqlite.bind("sqlite3_prepare_v2", signature(
    sqlite.i32,
    Array(dbType, sqlite.utf8z, sqlite.i32, sqlite.out(stmt), sqlTail)))
```

The Array exists only in Protos code to make the declaration readable: it is
never passed to the provider as a foreign argument. Only descriptor handles
and scalars cross the SPI.

## A first call: the SQLite version

`int sqlite3_libversion_number(void)` needs only a return descriptor:

```protos
Assertions: import("std:test/assertions")
sqlite: import("ffi:sqlite3")

version: sqlite.bind(
    "sqlite3_libversion_number",
    sqlite.signature(sqlite.i32)
)

Assertions.require(version() >= 3000000)
```

```sh
bin/protos --foreign-provider-path target/ffm-provider/protos-ffm-provider.jar \
    protos/examples/ffi/sqlite-version.protos
```

Success is exit status 0 with no output. This is
[`protos/examples/ffi/sqlite-version.protos`](../../../protos/examples/ffi/sqlite-version.protos).
`FFI.bind` and the other public operations belong to LIB028; the examples
below retain the private I144 contract for backend documentation.

## Output parameters

C often returns a status and writes the real result through a pointer, as in
`sqlite3_open_v2(filename, &db, flags, vfs)`. Declare that position with
`out(type)`:

- an `out` position consumes **no** Protos argument: `open(":memory:", 6, null)`
  passes three arguments to a four-parameter C function;
- the provider allocates and zero-initializes each output slot for the call;
- a binding with `out` positions answers a provider-private indexed value
  `[cResult, out0, out1, ...]`, read with `result[i]` and `result.size`;
- a non-zero C result (for example `SQLITE_ERROR`) is ordinary information
  in `result[0]`, never an automatic `ForeignError`; checking it is the
  program's job;
- LIB028 converts that value into a fresh ordinary public Array.

## Owned resources

`owned(opaque, destructorSymbol, destructorReturnType)` hands a C object's
lifetime to the Actor's provider session:

- every non-null owned result (return or `out`) enters the session's
  **custody before it is published** to Protos code;
- this holds even when the C function reports an error: a failed
  `sqlite3_open_v2` still yields a handle that must be released;
- `release` is **irreversible**: it marks the reference released first, then
  calls the destructor, so the destructor runs **at most once**; its C result
  is answered unchanged;
- resources still held when the session closes (the Actor or Process ends)
  are released then, **most recent first**;
- closing the session invalidates every reference it issued;
- handle identity is per session and acquisition, **not the native
  address**: a released handle stays dead even if C reuses its address.

```protos
dbType: sqlite.opaque("sqlite3")
db: sqlite.owned(dbType, "sqlite3_close_v2", sqlite.i32)
vfsName: sqlite.nullable(sqlite.opaque("sqlite3_vfs_name"))
open: sqlite.bind("sqlite3_open_v2",
    signature(sqlite.i32, Array(sqlite.utf8z, sqlite.out(db), sqlite.i32, vfsName)))

opened: open(":memory:", 6, null)
Assertions.require(opened[0] == 0)              // SQLITE_OK
connection: opened[1]
// ... use the connection ...
Assertions.require(sqlite.release(connection) == 0)  // sqlite3_close_v2
```

Release dependants before what they depend on: finalize statements before
closing their connection. This is a low-level sequence of C calls, not a
connection library; LIB029 will provide that.

`borrowed`, `preserving` and `copy` are private I144 provider machinery, not
public `std:ffi` operations. The existing public examples deliberately avoid
SQLite TEXT/BLOB readers; use the [public guide](../library/ffi.md) for the R1
boundary and the
[public examples](../../../protos/examples/ffi/README.md) for supported calls.

## Borrowed memory

`sqlite3_column_text` and `sqlite3_column_blob` return pointers to memory
that SQLite still owns. Declare them with `borrowed` and copy before the
memory can change:

```text
sqlite3_column_text
        |
        v
sqlite3_column_bytes (preserving)
        |
        v
sqlite.copy(pointer, length)
        |
        v
ordinary Protos Bytes
```

```protos
columnText: sqlite.bind("sqlite3_column_text",
    signature(sqlite.borrowed, Array(stmtType, sqlite.i32)))
columnBytes: sqlite.bind("sqlite3_column_bytes",
    sqlite.preserving(signature(sqlite.i32, Array(stmtType, sqlite.i32))))

text: columnText(statement, 2)
length: columnBytes(statement, 2)
copied: sqlite.copy(text, length)

bytes: Encoding.UTF8.encode("")
index: 0
while(index < copied.size) {
    bytes.add(copied[index])
    index = index + 1
}
```

- **Epochs.** Each borrowed region belongs to the session's current borrow
  epoch. Calling any binding not declared `preserving` advances the epoch
  immediately before entering C; so do `release` and session close. A region
  from an earlier epoch can no longer be copied.
- **`preserving`.** It declares that a call leaves borrowed regions valid,
  as `sqlite3_column_bytes` does after `sqlite3_column_text`. It is a
  **trusted** ABI statement, not a physical check; a wrong one is as unsafe
  as a wrong C signature.
- **Bounds.** A copy is limited to 16 MiB (a provider safeguard, not a
  Protos rule).
- **Bytes, not text.** The copy preserves octets; Java never decodes UTF-8.
  Protos code rebuilds `Bytes` (from `Encoding.UTF8.encode("")` and `add`)
  and decodes if it wants a String.
- **Zero, `NULL`, and SQL `NULL` differ.** A `borrowed` result of C `NULL`
  is Protos `null`; zero-length data copies to an empty value. In SQLite a
  `NULL` pointer can mean SQL `NULL` *or* an empty TEXT/BLOB, so code reading
  untyped columns must consult the column type (`sqlite3_column_type`)
  rather than infer SQL `NULL` from the pointer alone.

### `pzTail` of `sqlite3_prepare_v2`

`pzTail` would point into the `zSql` argument, whose `utf8z` storage belongs
to the call and is gone when it returns. It is therefore never a borrowed
region and must not be received. Declare it as an ordinary nullable pointer
parameter of a private type and pass `null` explicitly, which tells SQLite
not to write it:

```protos
sqlTail: sqlite.nullable(sqlite.opaque("sqlite3_sql_tail"))
prepare: sqlite.bind("sqlite3_prepare_v2", signature(
    sqlite.i32,
    Array(dbType, sqlite.utf8z, sqlite.i32, sqlite.out(stmt), sqlTail)))

prepared: prepare(connection, "SELECT 1", -1, null)
```

Do not declare it as `out(borrowed)` or as an opaque `out`. Recovering the
remaining SQL belongs to LIB029, which must copy it while `zSql` is alive.

## Errors

| Situation | Result |
|---|---|
| Provider not authorized (no `--foreign-provider-path`) | ordinary resolution Error at `import` |
| Unknown alias, file name, or path (`ffi:libc`, `ffi:/lib/...`) | ordinary Error |
| Wrong arity | ordinary Error, before entry |
| Wrong kind (for example an Integer for `utf8z`) | ordinary Error, before entry |
| Integer out of the declared range | ordinary Error, before entry |
| Released, closed-session, or wrongly typed reference; stale borrowed region | ordinary Error, before entry |
| Native symbol cannot be resolved by `bind` | `ForeignError` |
| Provider failure after entry (load, conversion) | `ForeignError` |
| `SQLITE_ERROR` or any other C status | an ordinary Integer result |
| Invalid index on a provider-private result | `ForeignError` (detected after entry, D188) |

`ForeignError` payloads never contain host objects, addresses, paths, or
stacks. The provider **cannot** turn a C crash, or the effects of a wrong
ABI declaration, into a `ForeignError`: those can corrupt memory or end the
process.

## Registration (I085)

The provider implements `ProtosForeignProviderPlugin` with id `protos-ffm` and
scheme `ffi`, listed in its own `META-INF/services` file. It uses only the
public SPI and JDK APIs. Discovery happens only on explicitly configured
provider paths. Its only target is `sqlite3`; library authorization comes
exclusively from the host (`--foreign-provider-path` and provider options such
as `sqlite3.path` received through `ProtosForeignPluginEnvironment.options()`),
never from guest code and never from content packaged in the JAR, so one JAR
serves every host. The CLI supplies no provider options.

The SQLite-independent ABI fixture runs under a test-only registration
(`ffi-test:ffi_test`) of the same backend, hosted by the Java test
`ProtosI144FfmFixtureAbiTest`, which selects the compiled fixture with
`withProviderOptions`.

The I085 SPI gained one optional hook for this provider,
`ProtosForeignValueOperations.acceptsExecution`, run before entry of
projected `call` and `std:interop.invoke`. It lets a provider validate the
complete argument list against a signature (arity, kinds, ranges,
nullability); refusals are ordinary Errors. Its default preserves every
existing provider ([custom providers](custom-provider.md)). Element reads have
no such hook: an invalid index on a provider result is found after entry and
is a `ForeignError`, as the general D188 rule prescribes.

## Trust boundary

`TRUSTED_IN_PROCESS` is not a sandbox. Symbols are resolved only through the
authorized library's `SymbolLookup.libraryLookup`, never through the default
or loader lookups, but the platform `dlsym` also searches that library's
dependencies (libc's `strlen` is found through `ffi:sqlite3`). There is no
symbol whitelist and none would be an isolation guarantee.

## Resources and borrowed memory

- All owned results of one call (return and `out` positions) enter custody
  before any fallible conversion: a failed conversion is a `ForeignError`, and
  the resources it could not publish are released once at session close.
- `sqlite3_prepare_v2`'s `pzTail` points into the call's own `utf8z` input,
  which dies with the call; it is never a borrowed region. The tests and
  examples declare it as a nullable opaque parameter of a private type and
  pass `null`, so it is never received.
- References are opaque, session-bound, and identified per acquisition, never
  by address. Owned references enter custody before they are published, are
  released at most once (`release`, or session close), and are rejected after
  release.
- Borrowed pointers (`borrowed`) are readable only through
  `copy(borrowed, length)` and only until the next non-`preserving` call. The
  SQLite pattern is: `sqlite3_column_text` / `sqlite3_column_blob`, then a
  `preserving` `sqlite3_column_bytes`, then `copy`, then rebuild `Bytes` in
  Protos from an empty `Encoding.UTF8.encode("")` and `add`. `NULL` is `null`; zero-length data copies
  to an empty value. Text is not decoded in Java.
- Invalidating operations: any call of a binding not declared `preserving`
  (the epoch advances just before entering C, so a later native failure
  never restores earlier regions; a call rejected before entry changes
  nothing), any `release` (before the destructor runs), and session close. `copy` is rejected before entry once
  validity is no longer accredited by the declared contract. `preserving` is
  a trusted ABI declaration, not a physical liveness check; a wrong one is as
  unsafe as a wrong C signature.
- Copies are bounded by a private 16 MiB limit; this is a provider safeguard,
  not a Protos rule.
- `sqlite3_open_v2`'s `zVfs` is `const char *`. The tests pass `NULL` through
  a nullable opaque pointer of a private type that no binding produces, so
  only `null` is admitted: a limited NULL-pointer adaptation, not a
  `nullable(utf8z)` extension.

## Security and limits

- `TRUSTED_IN_PROCESS` is **not a sandbox**: the C library executes inside
  the host process with all of its authority.
- A wrong ABI declaration (signature, `preserving`, destructor) can corrupt
  memory or terminate the process.
- `SymbolLookup.libraryLookup` also resolves symbols of the library's
  dependencies; there is no whitelist, and none would be a security
  isolation.
- Not supported: callbacks/upcalls, general structs, variadic functions,
  C++, Native Image, Windows, macOS, TEXT/BLOB input binding with
  `SQLITE_TRANSIENT`, and pointers as Integers.
- C calls are **synchronous**: they introduce no implicit suspension, and a
  blocked call holds its Actor.
- Session close waits for admitted calls, so a blocked C call can delay it.
- No isolation of the process from arbitrary C behavior is guaranteed.

## Relationship to LIB028 and LIB029

- **LIB028** (`std:ffi`) defines the public Protos API: ordinary
  descriptor values, Array-based signatures, `FFI.bind/opaque/owned/out/release`,
  and construction of final Arrays from provider results, implemented in
  Protos over these provider operations.
- **LIB029** (SQLite) will provide the idiomatic library: connections,
  statements, transactions, `Closable` lifecycle, and row projection.

LIB029 remains separate work. `ffm-public-api.protos` exercises the public
LIB028 layer with the existing I144 fixture host; `ffm-abi.protos` retains
private backend and borrowed-memory regression coverage. Public R1 has no
borrowed-memory descriptor or copying operation.

| | I144 (implemented) | LIB028 (implemented) | LIB029 (not yet) |
|---|---|---|---|
| Owner of | external FFM provider, bounded generic ABI, I085 integration | public `std:ffi` in Protos | idiomatic SQLite library |
| Declarations | provider-private `signature`/`param`/`bind` | `FFI.bind`, public descriptors, Array signatures | hidden behind the library |
| Results with `out` | provider-private indexed value | final public Arrays | library values |
| Resources | owned/borrowed custody, `release`, session/Actor control | public owned/out/release abstractions | connections, statements, `Closable` |
| SQL | raw C calls, synchronous | — | transactions, parameterized queries, row mapping |

## Limits

Linux x86-64 portable JVM only; no Native Image, Windows, macOS, structs,
callbacks, variadic functions, C++, TEXT/BLOB input binding with
`SQLITE_TRANSIENT`, or native sandbox.
