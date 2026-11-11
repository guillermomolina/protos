# FFM provider examples (`ffi:sqlite3`)

These programs call C functions of the real shared SQLite library from
Protos through the optional external FFM provider (I144). Every C declaration
and all control flow are Protos source; the provider contains no
SQLite-specific Java.

## Public `std:sqlite` example

| Example | Purpose |
| --- | --- |
| [`sqlite-library.protos`](sqlite-library.protos) | Public `std:sqlite` table creation, numeric inserts, copied rows and deterministic cleanup. |

See the [SQLite library guide](../../../docs/guide/library/sqlite.md) for the
bounded LIB029 API and its validation evidence.

## Public `std:ffi` examples

Use these programs to learn or apply the public LIB028 API:

| Example | Purpose |
| --- | --- |
| [`sqlite-version-public.protos`](sqlite-version-public.protos) | Query the SQLite version with public descriptors and an ordinary Closure. |
| [`sqlite-complete-public.protos`](sqlite-complete-public.protos) | Check complete and incomplete SQL with `sqlite3_complete`. |
| [`sqlite-numeric-query-public.protos`](sqlite-numeric-query-public.protos) | Bind numeric parameters, read one numeric row and release owned handles. |

See the [public `std:ffi` guide](../../../docs/guide/library/ffi.md) for the
declaration, authority, ownership and safety model.

## Low-level I144 provider examples

These retained programs exercise private backend operations and remain useful
for understanding I144 or its unpublished borrowed-memory boundary. They are
not the public application learning path:

| Example | Purpose |
| --- | --- |
| [`sqlite-version.protos`](sqlite-version.protos) | Low-level provider check that resolves and calls `sqlite3_libversion_number`. |
| [`sqlite-memory-query.protos`](sqlite-memory-query.protos) | Low-level in-memory query, including the historical borrowed TEXT/BLOB copying path. |

Unlike most examples in this tree, they do **not** run with a plain
`bin/protos <file>`: the provider must be passed explicitly.

## Requirements

- Linux x86-64 (LP64) and JDK 25;
- the portable JVM distribution (Native Image is not supported);
- a shared SQLite library that the native loader finds as
  `libsqlite3.so.0`.

## Build the provider

```sh
make dist
ls target/distributions/        # protos-<version>-posix-jvm.zip
python3 dist/build_ffm_provider.py \
    target/distributions/protos-<version>-posix-jvm.zip \
    --output target/ffm-provider
```

Replace `<version>` with the ZIP actually listed (the project version from
`pom.xml`). The tool writes `target/ffm-provider/protos-ffm-provider.jar`.
Details: [Build the provider JAR](../../../docs/guide/interop/ffm-provider.md#build-the-provider-jar).

## Run

```sh
bin/protos --foreign-provider-path target/ffm-provider/protos-ffm-provider.jar \
    protos/examples/ffi/sqlite-library.protos
bin/protos --foreign-provider-path target/ffm-provider/protos-ffm-provider.jar \
    protos/examples/ffi/sqlite-version-public.protos
bin/protos --foreign-provider-path target/ffm-provider/protos-ffm-provider.jar \
    protos/examples/ffi/sqlite-complete-public.protos
bin/protos --foreign-provider-path target/ffm-provider/protos-ffm-provider.jar \
    protos/examples/ffi/sqlite-numeric-query-public.protos
bin/protos --foreign-provider-path target/ffm-provider/protos-ffm-provider.jar \
    protos/examples/ffi/sqlite-memory-query.protos
```

All programs validate their results with `std:test/assertions`. Success means
**exit status 0 and no standard output**. A broken expectation fails an
assertion and exits non-zero with a diagnostic on standard error.

Without `--foreign-provider-path` (or with the JAR only on `CLASSPATH`), no
provider owns the `ffi` scheme: `import("ffi:sqlite3")` fails with an
ordinary resolution Error and the exit status is non-zero. Nothing native is
loaded.

## Why `:memory:`

The query example uses SQLite's in-memory database so it creates no files and
needs no Protos `filesystem` grant. Note that this is about Protos authority
only: the C library itself runs with the whole process's authority.

## What these examples are (and are not)

The two original examples use the provider-private I144 operations directly: `signature`, `param`,
`preserving`, `bind`, `opaque`, `nullable`, `owned`, `out`, `borrowed`,
`copy` and `release`. These are support machinery, not a stable API:

- `sqlite-version-public.protos` uses LIB028 `std:ffi`: ordinary frozen
  descriptors, Closure bindings and ordinary result Arrays. Import alone
  grants no authority. R1 has no public borrowed-memory copy method;
- `sqlite-library.protos` uses LIB029 `std:sqlite`, which owns connections,
  statements, numeric/NULL parameters, copied rows and `Closable` lifecycle.
  SQL must contain no semicolon or NUL. TEXT/BLOB input binding and transaction
  abstractions remain outside this bounded surface.

Applications can use the [SQLite library](../../../docs/guide/library/sqlite.md)
without copying low-level declarations. The maintainer reports **34/34 PASS**
for the complete external foreign-provider integration, including the LIB029
real SQLite cases and this public example. The final integrated `make test`
outcome has not yet been reported.

## Limitations and security

- The provider is `TRUSTED_IN_PROCESS`, not a sandbox: SQLite runs inside the
  Protos process with its full authority.
- The provider validates arity, kinds, ranges and handle state before a call,
  but cannot check that a declared signature matches the C function. A wrong
  declaration can corrupt memory or terminate the process.
- No callbacks, structs, variadic functions, C++, TEXT/BLOB input binding, or
  Native Image.
- C calls are synchronous; a blocked call delays the Actor and session close.

## See also

- [The optional external FFM provider guide](../../../docs/guide/interop/ffm-provider.md)
- [Provider README](../../../providers/ffm/README.md)
- Tutorials: [`07-ffi-provider-basics.protos`](../../tutorials/08-language-interactions/07-ffi-provider-basics.protos)
  and [`04-ffi-ownership-and-borrows.protos`](../../tutorials/12-system-resources/04-ffi-ownership-and-borrows.protos)
- Regression programs: [`ffm-sqlite.protos`](../../tests/foreign-provider/ffm-sqlite.protos)
  and [`ffm-denied.protos`](../../tests/foreign-provider/ffm-denied.protos),
  run by [`dist/test_external_foreign_providers.py`](../../../dist/test_external_foreign_providers.py)
