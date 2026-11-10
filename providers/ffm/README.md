# Protos FFM provider (`ffi:`)

Optional, external foreign provider (I144, decision D238-B1-SQL-R1) that lets
Protos code declare and call C functions of a **host-authorized** shared
library through the Java 25 Foreign Function and Memory API
(`java.lang.foreign`). It is built separately from Protos, against the public
I085 SPI (`com.guillermomolina.protos.spi.foreign`) only, and is never part of
`lib/protos.jar`.

```text
Ordinary Protos application
    | import("std:ffi")
    v
Protos descriptors, Closures and Arrays (LIB028)
    | private provider operations
    v
I085 external SPI           <- this provider: module ffi:<library>
    v
Java 25 java.lang.foreign
    v
host-authorized native library (trusted, in process)
```

## Quickstart (from the repository)

```sh
make dist
ls target/distributions/        # protos-<version>-posix-jvm.zip
python3 dist/build_ffm_provider.py target/distributions/protos-<version>-posix-jvm.zip \
    --output target/ffm-provider
bin/protos --foreign-provider-path target/ffm-provider/protos-ffm-provider.jar \
    protos/examples/ffi/sqlite-version-public.protos
```

Building the JAR and authorizing it are separate steps: the build produces an
inert artifact, and only `--foreign-provider-path` on each run lets a host use
it. A successful run of the example prints nothing and exits with status 0.

Next steps:

- tutorials [`07-ffi-provider-basics.protos`](../../protos/tutorials/08-language-interactions/07-ffi-provider-basics.protos)
  (signatures, scalar calls, pre-entry validation) and
  [`04-ffi-ownership-and-borrows.protos`](../../protos/tutorials/12-system-resources/04-ffi-ownership-and-borrows.protos)
  (owned handles, `out`, borrowed memory);
- examples in [`protos/examples/ffi/`](../../protos/examples/ffi/README.md);
- the full HOWTO: [ABI descriptors](../../docs/guide/interop/ffm-provider.md#abi-descriptors-and-operations),
  [output parameters](../../docs/guide/interop/ffm-provider.md#output-parameters),
  [owned resources](../../docs/guide/interop/ffm-provider.md#owned-resources),
  [borrowed memory](../../docs/guide/interop/ffm-provider.md#borrowed-memory),
  [errors](../../docs/guide/interop/ffm-provider.md#errors).

The Java provider knows no SQLite function. It supplies generic ABI
descriptors and operations; every C function is declared by name and
signature in Protos source, which also owns all control flow. Each Actor that
imports `ffi:sqlite3` gets its own session, opened on first import and closed
with the Actor or Process, releasing any owned resources it still holds.

> **Trusted C only.** The authorized library runs inside the Protos process
> with its full authority. Authorize only libraries you trust, and declare
> signatures exactly: a mistake can corrupt memory or terminate the process.

## Requirements and limits

- Portable JVM CLI, Java 25 (GraalVM 25), Linux x86-64 (LP64) only. Other
  platforms are rejected on first use.
- Not available: Native Image (no dynamic provider loading), Windows, macOS,
  C structs, callbacks/upcalls, variadic functions, C++ ABI, arbitrary
  allocators, unbounded native memory, and any sandboxing of native code.

## Build and load

```sh
python3 dist/build_ffm_provider.py target/distributions/protos-<version>-posix-jvm.zip --output /path/to/out
protos --foreign-provider-path /path/to/out/protos-ffm-provider.jar program.protos
```

The tool compiles `src/main/java` with `javac --release 25` against the
distribution's `lib/protos.jar`, adds the provider's `META-INF/services`
descriptor, and writes only into `--output`. Nothing is discovered unless the
host passes the JAR with `--foreign-provider-path`; a JAR on `CLASSPATH` or in
the working directory authorizes nothing.

## Authorized libraries and host options

The distributable provider (id `protos-ffm`) accepts exactly one target:

| Target | Library |
|---|---|
| `ffi:sqlite3` | `libsqlite3.so.0` by loader name, or the absolute path in provider option `sqlite3.path` |

Authorization comes only from the host: passing the JAR with
`--foreign-provider-path`, and options received through
`ProtosForeignPluginEnvironment.options()` (set by an embedding host with
`ProtosForeignProviderConfiguration.withProviderOptions("protos-ffm", ...)`;
the CLI supplies none). Nothing packaged in the JAR authorizes a library, so
the same JAR is reusable between hosts unchanged. File names and paths written
in a Protos program are never targets.

The ABI test fixture uses a separate, test-only registration
(`protos/tests/foreign-provider/plugins/ffm-fixture`, scheme `ffi-test`,
target `ffi_test`) of the same generic backend, compiled into a test JAR only;
its Java test host selects the fixture library with the provider option
`ffi_test.path`.

### Trust boundary

`TRUSTED_IN_PROCESS` is not a sandbox. An authorized library runs with the
whole process's authority; Protos `Filesystem` authority does not confine it,
and the provider does not make C memory-safe: a wrong declared signature can
corrupt memory or terminate the process. Symbols are resolved only through
the `SymbolLookup.libraryLookup` of the authorized library (never
`Linker.defaultLookup()`, `SymbolLookup.loaderLookup()`, or a fallback chain),
but the platform `dlsym` on a library handle also searches the libraries it
depends on: for example, `strlen` from libc is found through `ffi:sqlite3`.
No symbol whitelist is provided, and none would be a security isolation.

## Private provider contract (not `std:ffi`)

`import("ffi:<library>")` answers a module handle whose members are
provider-private support operations for Protos-source `std:ffi`
(LIB028). They are not a normative or stable public API.

| Member | Meaning |
|---|---|
| `void`, `i32`, `i64`, `f64`, `sizeT`, `utf8z`, `borrowed` | ABI descriptors (`sizeT` is unsigned 64-bit; `utf8z` is input-only NUL-terminated UTF-8) |
| `opaque(name)` / `nullable(opaque)` | opaque C reference of a logical type, tied to this library |
| `owned(opaque, destructorSymbol, void\|i32)` | owned reference released exactly once by its destructor |
| `out(type)` | output position: consumes no argument, zeroed, returned after the C result |
| `signature(returnType)`, `param(signature, type)`, `preserving(signature)` | signatures composed one parameter at a time (no Array crosses the SPI) |
| `bind(symbol, signature)` | resolves and links the symbol once; answers an executable binding |
| `release(owned)` | marks released, then calls the destructor; answers its C result |
| `isLive(reference)` | whether a reference or borrowed region is still usable |
| `copy(borrowed, length)` | bounded copy of borrowed memory into an independent indexed value |

A binding without `out` positions answers its scalar, reference, or `null`
(C `void` / NULL). With `out` positions it answers a provider-private indexed
value `[cResult, out0, out1, ...]` (`result[i]`, `result.size`); a copy is an
indexed value of octets. Protos code rebuilds ordinary Arrays and Bytes from
them. A non-zero C status is data, never an automatic Error. An out-of-range
index on these values is detected inside the entered element read and is
therefore a `ForeignError`, under the general D188 rule (there is no
pre-entry hook for element reads).

Every executable operation is validated before entry by the I085
`acceptsExecution` hook: arity, kinds, exact ranges (no truncation or
wrapping), nullability, interior NUL, invalid Unicode, session, declared
reference type, and resource state. A refusal is the ordinary Protos `Error`.
Load, resolution, and native failures after entry are `ForeignError`s whose
payload never contains host objects, addresses, paths, or stacks.

## Lifetime guarantees

- One session per Actor; sessions share no state, no global registry exists.
  Nothing is loaded and no `Linker` is created until the first import.
- Reference identity is per session and acquisition, never the address; no
  address is ever visible, and no Integer converts to an address.
- An owned result (including an `out` result accompanied by an error status)
  enters the session's custody before it is published. A call's results are
  handled in three stages: every native carrier (`[return, out0, ...]`) is
  captured while the call arena still holds the out slots; then every
  non-null owned result is adopted into custody; only then are the fallible
  conversions (nullability, types) applied. If a conversion fails (a
  `ForeignError`), the adopted resources stay in custody until session close,
  which releases each of them once. `release` marks it
  released before calling the destructor, so the destructor runs at most once;
  a released reference is rejected even if C reuses its address.
- Close is a cut: no new call is admitted, admitted calls finish, then the
  remaining owned resources are released (most recent first) and the
  library arena is closed. A destructor failure is recorded as an uncertain
  release, never retried.
- Borrowed memory is valid only while the session's borrow epoch is
  unchanged. A non-`preserving` call advances the epoch after its arguments
  are validated and marshalled and immediately before entering C, so a
  native failure afterwards never restores earlier regions; a destructor
  advances it once, before it runs; a call rejected before entry never
  advances it. Invalidating operations: every call of a binding not declared
  `preserving`, every `release` (destructor call), and session close. Once
  validity is no longer accredited by the declared contract, `copy` is
  rejected before entry; it also checks a private 16 MiB bound. `preserving`
  is a trusted ABI declaration (for example for `sqlite3_column_bytes`), not a
  physical check of memory lifetime: a wrong declaration has the same
  consequences as a wrong C signature.
- Close never holds the session monitor during a C call; it waits for
  admitted calls without holding it, then releases resources and closes the
  arena, so no admitted call sees a closed arena.

## `sqlite3_prepare_v2` and `pzTail`

`pzTail` points into the `zSql` input, whose `utf8z` storage belongs to the
call's own arena and is gone when the call returns. It is therefore never a
borrowed region: the tests receive it only as a private nullable opaque type
that no other declaration accepts, and never read or pass it on.
Interpreting the remaining SQL is LIB029's, which must copy it while `zSql`
is still alive.

## `sqlite3_open_v2` and `zVfs`

The fourth parameter is `const char *zVfs`. R1 has no nullable text
descriptor, so the tests pass C `NULL` through
`nullable(opaque("sqlite3_vfs_name"))`, a private logical type no binding
produces: only `null` is ever admitted. This is a limited adaptation to the
NULL-pointer ABI, not a way to pass VFS names, and not a `nullable(utf8z)`
extension.
