# FFM provider examples (`ffi:sqlite3`)

These programs call C functions of the real shared SQLite library from
Protos through the optional external FFM provider (I144). Every C declaration
and all control flow are Protos source; the provider contains no
SQLite-specific Java.

## Public `std:sqlite` examples

| Example | Purpose |
| --- | --- |
| [`sqlite-library.protos`](sqlite-library.protos) | Public `std:sqlite` introduction: one table, one numeric insert, one query and deterministic cleanup. |
| [`sqlite-wal.protos`](sqlite-wal.protos) | WAL selection persistently changes the entire database's journal mode; synchronous FULL configures the writer connection. Verifies WAL after read-only reopen and committed prepared writes; table operations affect only `wal_demo`. |
| [`sqlite-durable.protos`](sqlite-durable.protos) | Filename argument, persistent table, committed prepared inserts, Statement reuse, rolled-back update and verified read-only reopen; owns/replaces `durable_demo`. |
| [`sqlite-prepared-numeric.protos`](sqlite-prepared-numeric.protos) | Query and filter numeric records: several inserts with fresh Statements, a parameterized numeric predicate and deterministic ordered `each` traversal with Float and NULL checks. |
| [`sqlite-copied-values.protos`](sqlite-copied-values.protos) | Read independent TEXT/BLOB results: multibyte and NUL-containing TEXT, BLOB octets, empty TEXT/BLOB versus NULL, and copies that survive stepping and close. |

See the [SQLite library guide](../../../docs/guide/library/sqlite.md) for the
current memory/file API and its practical learning path.

## Public `std:ffi` examples

Use these programs to learn or apply the public `std:ffi` API:

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
`bin/protos <file>`: the provider must be passed explicitly with
`--foreign-provider-path`, and SQLite must be authorized separately with
`--allow-library sqlite3=libsqlite3.so.0`. No native library is authorized by
default, and the library authorization grants no Protos capability.

## Requirements

- Linux x86-64 (LP64) and JDK 25;
- the portable JVM distribution (Native Image is not supported);
- a shared SQLite library that the native loader finds as
  `libsqlite3.so.0`, authorized with `--allow-library sqlite3=libsqlite3.so.0`
  (an absolute path such as
  `--allow-library sqlite3=/usr/lib/x86_64-linux-gnu/libsqlite3.so.0` also
  works). The library runs trusted in process; this is not a sandbox.

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
bin/protos --foreign-provider-path target/ffm-provider/protos-ffm-provider.jar --allow-library sqlite3=libsqlite3.so.0 \
    protos/examples/ffi/sqlite-wal.protos /tmp/sqlite-wal-example.db
bin/protos --foreign-provider-path target/ffm-provider/protos-ffm-provider.jar --allow-library sqlite3=libsqlite3.so.0 \
    protos/examples/ffi/sqlite-library.protos
bin/protos --foreign-provider-path target/ffm-provider/protos-ffm-provider.jar --allow-library sqlite3=libsqlite3.so.0 \
    protos/examples/ffi/sqlite-durable.protos /tmp/sqlite-example.db
bin/protos --foreign-provider-path target/ffm-provider/protos-ffm-provider.jar --allow-library sqlite3=libsqlite3.so.0 \
    protos/examples/ffi/sqlite-prepared-numeric.protos
bin/protos --foreign-provider-path target/ffm-provider/protos-ffm-provider.jar --allow-library sqlite3=libsqlite3.so.0 \
    protos/examples/ffi/sqlite-copied-values.protos
bin/protos --foreign-provider-path target/ffm-provider/protos-ffm-provider.jar --allow-library sqlite3=libsqlite3.so.0 \
    protos/examples/ffi/sqlite-version-public.protos
bin/protos --foreign-provider-path target/ffm-provider/protos-ffm-provider.jar --allow-library sqlite3=libsqlite3.so.0 \
    protos/examples/ffi/sqlite-complete-public.protos
bin/protos --foreign-provider-path target/ffm-provider/protos-ffm-provider.jar --allow-library sqlite3=libsqlite3.so.0 \
    protos/examples/ffi/sqlite-numeric-query-public.protos
bin/protos --foreign-provider-path target/ffm-provider/protos-ffm-provider.jar --allow-library sqlite3=libsqlite3.so.0 \
    protos/examples/ffi/sqlite-memory-query.protos
```

Supply disposable filenames for the persistent examples.
For the WAL example, use a disposable database, not an existing database whose
journaling mode you want to preserve.

All programs validate their results with `std:test/assertions`. Success means
**exit status 0 and no standard output**. A broken expectation fails an
assertion and exits non-zero with a diagnostic on standard error.

Without `--foreign-provider-path` (or with the JAR only on `CLASSPATH`), no
provider owns the `ffi` scheme: `import("ffi:sqlite3")` fails with an
ordinary resolution Error and the exit status is non-zero. With the provider
but without `--allow-library sqlite3=...`, the import fails with
`ForeignError` (`foreignCategory` `unauthorized-library`). In both cases
nothing native is loaded.

## Why `:memory:`

The query example uses SQLite's in-memory database so it creates no files and
needs no Protos `filesystem` grant. Note that this is about Protos authority
only: the C library itself runs with the whole process's authority.

## What these examples are (and are not)

The two original examples use the provider-private I144 operations directly: `signature`, `param`,
`preserving`, `bind`, `opaque`, `nullable`, `owned`, `out`, `borrowed`,
`copy` and `release`. These are support machinery, not a stable API:

- `sqlite-version-public.protos` uses `std:ffi`: ordinary frozen
  descriptors, Closure bindings and ordinary result Arrays. Import alone
  grants no authority. R1 has no public borrowed-memory copy method;
- `sqlite-library.protos` uses `std:sqlite`, which owns connections,
  statements, parameters, copied rows and `Closable` lifecycle.
  `bind` also accepts String (TEXT) and Bytes (BLOB), `prepare` admits exactly
  one statement with conventional `;` terminators and trailing comments, and
  `reset()`/`clearBindings()` reuse a Statement explicitly. The durable example
  adds file-backed open modes, manual transactions and verified persistence;
- `sqlite-complete-public.protos` contrasts public `sqlite3_complete` with the
  single-statement admission of `std:sqlite` `prepare`.

Applications can use the [SQLite library](../../../docs/guide/library/sqlite.md)
without copying low-level native declarations.

## Limitations and security

- The provider is `TRUSTED_IN_PROCESS`, not a sandbox: SQLite runs inside the
  Protos process with its full authority.
- The provider validates arity, kinds, ranges and handle state before a call,
  but cannot check that a declared signature matches the C function. A wrong
  declaration can corrupt memory or terminate the process.
- No callbacks, structs, variadic functions, C++, or Native Image.
  Public `std:ffi` lacks specialized TEXT/BLOB input descriptors; `std:sqlite`
  supports TEXT/BLOB parameters through its private adapter.
- C calls are synchronous; a blocked call delays the Actor and session close.

## See also

- [The optional external FFM provider guide](../../../docs/guide/interop/ffm-provider.md)
- [Provider README](../../../providers/ffm/README.md)
- Tutorials: [`07-ffi-provider-basics.protos`](../../tutorials/08-language-interactions/07-ffi-provider-basics.protos)
  and [`04-ffi-ownership-and-borrows.protos`](../../tutorials/12-system-resources/04-ffi-ownership-and-borrows.protos)
- Regression programs: [`ffm-sqlite.protos`](../../tests/foreign-provider/ffm-sqlite.protos)
  and [`ffm-denied.protos`](../../tests/foreign-provider/ffm-denied.protos)
