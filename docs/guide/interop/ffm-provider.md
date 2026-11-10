# The optional external FFM provider (`ffi:`)

> **Status:** Non-normative developer guide (I144). The provider operations
> described here are external support machinery, not the public `std:ffi`
> library, which LIB028 will define in Protos.

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
  which dies with the call; it is never a borrowed region and the tests only
  receive it as a private opaque value they never use.
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

## Relationship to LIB028 and LIB029

- **LIB028** (`std:ffi`) will define the public Protos API: ordinary
  descriptor values, Array-based signatures, `FFI.bind/opaque/owned/out/release`,
  and construction of final Arrays from provider results, implemented in
  Protos over these provider operations.
- **LIB029** (SQLite) will provide the idiomatic library: connections,
  statements, transactions, `Closable` lifecycle, and row projection.

Neither exists yet. The I144 tests
(`protos/tests/foreign-provider/ffm-*.protos`) prove only the provider and its
ABI.

## Limits

Linux x86-64 portable JVM only; no Native Image, Windows, macOS, structs,
callbacks, variadic functions, C++, TEXT/BLOB input binding with
`SQLITE_TRANSIENT`, or native sandbox.
