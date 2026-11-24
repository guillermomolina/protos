# Protos FFM provider (`ffi:`)

Generic foreign provider (I144, decision D238-B1-SQL-R1) that lets
Protos code declare and call C functions of a **host-authorized** shared
library through the Java 25 Foreign Function and Memory API
(`java.lang.foreign`). It has two delivery forms of the same generic
implementation:

- **Portable JVM:** an optional external artifact, built separately from
  Protos against the public I085 SPI
  (`com.guillermomolina.protos.spi.foreign`) and the GraalVM Native Image SDK
  API (compile time only), never part of `lib/protos.jar`, and loaded with
  `--foreign-provider-path`.
- **Installed Native executable:** integrated, with no JAR and no dynamic Java
  loading, restricted to a finite registered physical signature set; see
  [Native Image: integrated provider](#native-image-integrated-provider).

In both forms the private backend operations are the same, applications use
the Protos-first `std:ffi` and `std:sqlite` layers, and every native library
needs an explicit `--allow-library` authorization and runs as trusted native
code in process.

```text
Ordinary Protos application
    | import("std:ffi")
    v
Protos descriptors, Closures and Arrays (LIB028)
    | private provider operations
    v
generic FFM provider        <- module ffi:<library>
    | portable JVM: external JAR through the I085 SPI
    | installed Native: integrated, finite signature set
    v
Java 25 java.lang.foreign
    v
host-authorized native library (trusted, in process)
```

## Quickstart (from the repository)

Portable JVM, with the external provider JAR:

```sh
make dist
ls target/distributions/        # protos-<version>-posix-jvm.zip
python3 dist/build_ffm_provider.py target/distributions/protos-<version>-posix-jvm.zip \
    --output target/ffm-provider
bin/protos --foreign-provider-path target/ffm-provider/protos-ffm-provider.jar --allow-library sqlite3=libsqlite3.so.0 \
    protos/examples/ffi/sqlite-version-public.protos
```

Building the JAR and authorizing it are separate steps: the build produces an
inert artifact, only `--foreign-provider-path` on each run lets a host use it,
and only `--allow-library` authorizes a native library such as SQLite. A successful run of the example prints nothing and exits with status 0.

Installed Native executable, with the integrated provider and no JAR:

```sh
protos --allow-library sqlite3=libsqlite3.so.0 \
    protos/examples/ffi/sqlite-version-public.protos
```

## For Protos application authors

For application code that declares C functions supported by R1, start with the
[public `std:ffi` guide](../../docs/guide/library/ffi.md). `FFI.bind` accepts an
authorized library facade, a symbol, a return descriptor and an Array of public
parameter descriptors; declarations and control flow remain ordinary Protos.

The provider's `signature`/`param`/`bind` contract documented below is private
I144 backend machinery. It remains relevant to provider implementers and the
retained low-level examples, but it is not the recommended application API.

Next steps:

- the [Protos-first SQLite library](../../docs/guide/library/sqlite.md) and its
  [public example](../../protos/examples/ffi/sqlite-library.protos):
  `std:sqlite` owns memory/file connections, statements, typed parameters,
  copied rows and manual transactions. The [durable example](../../protos/examples/ffi/sqlite-durable.protos)
  accepts a filename argument and verifies commit, rollback and reopen. Native
  SQLite uses OS process authority under D259, outside guest Filesystem
  confinement; library loading remains explicitly authorized and calls synchronous;

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
signature in Protos source, which also owns all control flow. Only the
Process's RootActor can acquire the provider: its session opens on its first
`ffi:` import and closes with the RootActor or Process, releasing any owned
resources it still holds. The same import in any other Actor fails with an
ordinary Error before a session is opened or a library is loaded.

> **Trusted C only.** An authorized library runs `TRUSTED_IN_PROCESS`, inside
> the operating-system process with its full authority: native file and
> network access, transitive dependencies and symbols, native initializers,
> global state shared by every Actor, indefinite blocking, memory corruption
> from a wrong ABI signature, and process termination. `--allow-library` is
> not a sandbox. Authorizing a C++ library does not add C++ ABI, Qt, Boost,
> callbacks, arbitrary structs or virtual methods to R1.

## Requirements and limits

- External JAR: portable JVM CLI, Java 25 (GraalVM 25). Integrated form: the
  installed Native executable. Linux x86-64 (LP64) only. Other
  platforms are rejected on first use.
- Native Image: integrated into the installed executable, without dynamic
  provider loading and with a finite signature set; see
  [Native Image: integrated provider](#native-image-integrated-provider).
- Not available: Windows, macOS,
  C structs, callbacks/upcalls, variadic functions, C++ ABI, arbitrary
  allocators, unbounded native memory, and any sandboxing of native code.

## Native Image: integrated provider

The installed Native Image `protos` executable contains this same generic
provider; no provider JAR is loaded or needed:

```sh
protos --allow-library sqlite3=libsqlite3.so.0 app.protos
```

- The operating system must provide `libsqlite3.so.0` (or whichever library is
  authorized). Java, GraalVM and a separate Protos FFM provider JAR are not
  needed. No third-party C library is bundled into or linked by `protos`.
- Without `--allow-library` no `ffi:` provider is registered. Authorization
  alone loads nothing: a library is loaded only when the program first imports
  its `ffi:<alias>` module, and only from the exact authorized target.
- `--foreign-provider-path` is refused by the Native executable: it never loads
  Java code dynamically and never falls back to the JVM.
- The Native executable links only a finite, registered set of physical C
  signatures. Letters name the carrier: `I` 32-bit int, `L` 64-bit integer
  (`i64`, `sizeT`), `D` double, `P` address (`utf8z`, opaque, owned, borrowed,
  `out`, private inputs), `V` void return. The set is:
  `I()`, `I(I)`, `I(I,I)`, `I(P)`, `I(P,I)`, `I(P,P)`, `I(P,I,L)`, `I(P,I,D)`, `I(P,I,P)`, `I(P,P,L)`, `I(L,I,P)`, `I(I,I,P,P)`, `I(P,P,I,P)`, `I(P,P,I,P,P)`, `I(P,I,P,I,P)`, `L()`, `L(L)`, `L(P)`, `L(P,I)`, `D(P,I)`, `D(D,D)`, `P(I)`, `P(L)`, `P(P,I)`, `V()`, `V(L)`, `V(P)`.
  It covers every current `std:sqlite` declaration and owned destructor. Any
  other exact signature is valid on the portable JVM but is refused on the
  Native executable with an ordinary `Error` before linking, so C is never
  entered.
- Linux x86-64 LP64 only. No callbacks, structs by value, variadic functions,
  C++ ABI, platform portability or native sandboxing.

## Build and load

```sh
python3 dist/build_ffm_provider.py target/distributions/protos-<version>-posix-jvm.zip --output /path/to/out
protos --foreign-provider-path /path/to/out/protos-ffm-provider.jar --allow-library sqlite3=libsqlite3.so.0 program.protos
```

The tool compiles `src/main/java` with `javac --release 25` against the
distribution's `lib/protos.jar` and the official GraalVM Native Image SDK API JAR of the same distribution's
`lib/runtime/` (the single JAR containing `org.graalvm.nativeimage.ImageInfo`).
That JAR is a compile-time dependency only: the provider JAR contains no GraalVM
class, and the installed Native executable requires neither Java nor GraalVM. It adds the provider's `META-INF/services`
descriptor, and writes only into `--output`. Nothing is discovered unless the
host passes the JAR with `--foreign-provider-path`; a JAR on `CLASSPATH` or in
the working directory authorizes nothing.

## Authorized libraries and host options

The distributable provider (id `protos-ffm`) accepts any logical alias
matching `[A-Za-z_][A-Za-z0-9_]{0,127}` (case-sensitive), but **authorizes no
library by default**. Provider presence does not authorize `sqlite3` or any
other library. The host supplies an allowlist that maps each alias to exactly
one native target:

- an exact load name such as `libsqlite3.so.0`, passed unchanged to the
  operating-system loader; or
- an absolute path, passed unchanged.

Protos searches no extra directories, derives no names, adds no prefixes,
suffixes or versions, and rejects relative paths. `import("ffi:<alias>")`
names an alias only; a file name or path in an import is an ordinary import
error and never reaches the loader.

| Situation | Result |
|---|---|
| Provider not loaded | no `ffi` scheme: ordinary import Error |
| Valid alias not in the allowlist | `ForeignError`, `foreignCategory` `unauthorized-library` |
| Authorized target cannot be loaded | `ForeignError`, `foreignCategory` `library-unavailable` |
| Syntactically invalid alias | ordinary import Error |

From the portable JVM CLI, the allowlist comes from repeatable
`--allow-library alias=target` options given before the file, `-e`, or REPL
input:

```sh
protos --foreign-provider-path protos-ffm-provider.jar \
    --allow-library sqlite3=libsqlite3.so.0 \
    --allow-library zlib=/usr/lib/x86_64-linux-gnu/libz.so.1 \
    program.protos
```

The same options select the application RuntimeHost of a workspace `run` or a
`debug` session:

```sh
protos run --foreign-provider-path provider.jar \
  --allow-library sqlite3=libsqlite3.so.0 Main

protos debug --foreign-provider-path provider.jar \
  --allow-library sqlite3=libsqlite3.so.0 app.protos

protos --foreign-provider-path provider.jar \
  --allow-library sqlite3=libsqlite3.so.0 run Main

protos --foreign-provider-path provider.jar \
  --allow-library sqlite3=libsqlite3.so.0 debug app.protos
```

- Both options may precede the subcommand or follow it, but always before the
  `run` entry or the `debug` file; the two positions are combined, and an
  alias may still be given only once. After the entry or file the same
  spellings are ordinary `process.args()` elements.
- Authorizing a library is independent of trusting the provider JAR: the JAR
  is trusted by `--foreign-provider-path`, each library by its own
  `--allow-library`, and neither implies the other or any Protos capability.
- `TRUSTED_IN_PROCESS` provides no confinement: the provider and every
  authorized library run with the full authority of the operating-system
  process.
- `run` and `debug` create no new class of guest permission: the options only
  select the application RuntimeHost. Package preflight and external package
  materialization keep their existing mechanisms and never receive provider
  or library authority.
- Bundled CLI Tools (`test`, `package`, `format`, `lint`, `language-server`)
  never receive these options; giving them is a usage error (exit 2).
- `--inspect` remains limited to file, `-e`, and REPL execution.

An embedding host passes the same table as immutable provider options:

```java
ProtosForeignProviderConfiguration.trustedInProcess(providerPaths)
    .withProviderOptions("protos-ffm", Map.of(
        "library.sqlite3", "libsqlite3.so.0",
        "library.zlib", "/usr/lib/x86_64-linux-gnu/libz.so.1"))
```

Three separate decisions are involved, and none implies another:

1. `--foreign-provider-path` trusts the provider's Java code in process;
2. `--allow-library` (or `library.<alias>`) authorizes one specific native
   library for that provider;
3. Protos capabilities (`--allow-filesystem`, `--allow-network`, ...) grant
   guest APIs. Authorizing a library grants none of them, and they authorize
   no library.

Nothing packaged in the JAR authorizes a library, so the same JAR is reusable
between hosts unchanged.

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
| `pointerType(voidPointee)` | fresh plain typed pointer descriptor; its object is the pointee identity |
| `nullablePointerType(pointer)`, `borrowedPointerType(pointer)` | nullable / borrowed-result variant retaining that identity |
| `addressOf(owned, voidPointer)` | pass-only `void` pointer capability retaining a live owned reference |

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

- One session, owned by the RootActor; other Actors cannot acquire one, and no
  global registry exists.
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
- Typed pointer results are pass-only capabilities: no address, extent,
  member or indexed access, no destructor, never in custody, rejected by
  `copy` and `release`. A capability is admitted only while its session is
  usable, its retained owner (from `addressOf`) is live, and, for a borrowed
  result, the borrow epoch is unchanged; it is marshalled before the epoch
  advances, so a non-preserving call still receives it. A NULL non-nullable
  pointer result is a `null-result` failure after owned outputs were adopted.
  A borrowed pointer passed to a preserving function stays valid, but this
  never lets Protos dereference it (`std:ffi` documentation).
- Close never holds the session monitor during a C call; it waits for
  admitted calls without holding it, then releases resources and closes the
  arena, so no admitted call sees a closed arena.

## `sqlite3_prepare_v2` and `pzTail`

`pzTail` points into the `zSql` input, whose `utf8z` storage belongs to the
call's own arena and is gone when the call returns. It is therefore never a
borrowed region: the tests receive it only as a private nullable opaque type
that no other declaration accepts, and never read or pass it on.
LIB029 passes NULL for `pzTail` and rejects every semicolon-containing SQL
String before native entry. It never dereferences expired input storage, adds
pointer arithmetic or silently discards a second statement. See the
[admitted SQL subset](../../docs/guide/library/sqlite.md#sql-admission).

## `sqlite3_open_v2` and `zVfs`

The fourth parameter is `const char *zVfs`. R1 has no nullable text
descriptor, so the tests pass C `NULL` through
`nullable(opaque("sqlite3_vfs_name"))`, a private logical type no binding
produces: only `null` is ever admitted. This is a limited adaptation to the
NULL-pointer ABI, not a way to pass VFS names, and not a `nullable(utf8z)`
extension.
