# In-memory SQLite: `std:sqlite`

This guide describes the bounded LIB029 source implementation. It implements
the approved D238-B1-SQL-R1 wrapper scope; it does not extend Core or the public
FFI descriptor contract.

```protos
SQLite: import("std:sqlite")
db: SQLite.openMemory()
(() => {
    statement: db.prepare("SELECT ?1, ?2, ?3, 'text', x'00ff'")
    (() => {
        statement.bind(1, 42).bind(2, 1.5).bind(3, null)
        statement.each((row) => { /* row is an independent ordinary Array */ })
    }).ensure(() => statement.close().value())
}).ensure(() => db.close().value())
```

See the assertion-validated [table/insert/query example](../../../protos/examples/ffi/sqlite-library.protos)
and [tutorial](../../../protos/tutorials/12-system-resources/05-sqlite-protos-first.protos).

This page is the practical learning path for application developers. The
DOC024 programs continue it:

- [`06-sqlite-query-lifecycle.protos`](../../../protos/tutorials/12-system-resources/06-sqlite-query-lifecycle.protos):
  table creation, numeric inserts, ordered traversal, error cases and close
  semantics in one assertion-validated lesson;
- [`sqlite-prepared-numeric.protos`](../../../protos/examples/ffi/sqlite-prepared-numeric.protos):
  filtering numeric records with prepared parameters;
- [`sqlite-copied-values.protos`](../../../protos/examples/ffi/sqlite-copied-values.protos):
  independent TEXT/BLOB/NULL results.

To declare your own C functions use the public [`std:ffi` guide](ffi.md); for
backend implementation and native lifetime details see the advanced
[I144 FFM provider guide](../interop/ffm-provider.md).

## Getting started

1. Build the portable JVM distribution and note the archive name, which comes
   from the project version in `pom.xml`:

   ```sh
   make dist
   ls target/distributions/        # protos-<version>-posix-jvm.zip
   ```

2. Build the optional generic FFM provider against that archive, using the
   name actually listed:

   ```sh
   python3 dist/build_ffm_provider.py \
       target/distributions/protos-<version>-posix-jvm.zip \
       --output target/ffm-provider
   ```

3. Authorize the provider explicitly on every run with
   `--foreign-provider-path`. Building the JAR authorizes nothing, and
   `import("std:sqlite")` alone loads no SQLite library: only `openMemory()`
   reaches the authorized `ffi:sqlite3` provider.

4. Run the introductory example:

   ```sh
   bin/protos --foreign-provider-path target/ffm-provider/protos-ffm-provider.jar \
       protos/examples/ffi/sqlite-library.protos
   ```

5. Run the DOC024 tutorial and cookbook programs:

   ```sh
   bin/protos --foreign-provider-path target/ffm-provider/protos-ffm-provider.jar \
       protos/tutorials/12-system-resources/06-sqlite-query-lifecycle.protos
   bin/protos --foreign-provider-path target/ffm-provider/protos-ffm-provider.jar \
       protos/examples/ffi/sqlite-prepared-numeric.protos
   bin/protos --foreign-provider-path target/ffm-provider/protos-ffm-provider.jar \
       protos/examples/ffi/sqlite-copied-values.protos
   ```

   Each program validates with assertions: success is exit status 0 with no
   output.

## First connection and statement

```protos
Assertions: import("std:test/assertions")
SQLite: import("std:sqlite")
db: SQLite.openMemory()
(() => {
    statement: db.prepare("SELECT 6 * 7, 'answer'")
    (() => {
        Assertions.require(statement.step())       // true: a current row
        Assertions.require(statement.column(0) == 42)
        Assertions.require(statement.column(1) == "answer")
        Assertions.require(statement.step() === false)
    }).ensure(() => statement.close().value())
}).ensure(() => db.close().value())
```

Open, prepare, bind, step and column reads are synchronous calls in the
current Actor: each returns its result directly. Only `close()` returns a
Future, and that Future is already terminal when returned; `.value()` reads
its outcome (or signals its failure) without waiting for background work.
Each `ensure` installs cleanup before the work that could fail, so the
Statement closes before its Connection on every exit.

## Prepared numeric parameters

Parameters are written `?1`, `?2`, `?3` and bound by **one-based** position
before the first `step()`:

```protos
query: db.prepare("SELECT id, reading FROM measurements WHERE reading >= ?1 AND id <= ?2 AND ?3 IS NULL")
(() => {
    query.bind(1, 2.0).bind(2, 10).bind(3, null)
    query.each((row) => { /* row[0] Integer, row[1] Float */ })
}).ensure(() => query.close().value())
```

- Accepted values are exact signed 64-bit Integers, binary64 Floats and
  `null`. There is no implicit numeric coercion: a Fraction such as `1 / 2`,
  a String, a Boolean or an out-of-range Integer is rejected.
- TEXT and BLOB values cannot be bound as input; write them as SQL literals
  when an example needs them.
- `bind` returns the same Statement, so binds can be chained.
- Once a Statement has produced a row or completed, it cannot be rebound,
  and there is no reset. To execute the same SQL again (for example, a
  repeated INSERT), prepare a fresh Statement.

## Authority and target

Importing `std:sqlite` imports ordinary Protos code and acquires no native
library or authority. `openMemory()` imports the existing explicitly authorized
`ffi:sqlite3` provider. Every Connection, Statement, binding and library session
belongs to its originating Actor. Live resources cannot cross Actor or P
boundaries; ordinary copied rows follow ordinary value-transfer rules.

The supported execution profile is Linux x86-64 LP64, the portable JVM, JDK 25
and the supported GraalVM 25.5 toolchain, with `libsqlite3.so.0` installed and
the optional FFM provider built and explicitly supplied by the host:

```sh
bin/protos --foreign-provider-path target/ffm-provider/protos-ffm-provider.jar \
    protos/examples/ffi/sqlite-library.protos
```

[Provider build instructions](../interop/ffm-provider.md#build-the-provider-jar)
apply unchanged. No Native Image plugin support or other platform promise is
introduced. A missing or denied provider is a failure; an unavailable test
platform is UNSUPPORTED, never passing SQLite acceptance.

Only `:memory:` acquisition is exposed; there is no database filename API or
implicit Protos Filesystem grant. This is not SQL sandboxing: explicitly supplied
SQL still runs in a trusted in-process C library with process authority. Native
faults can corrupt memory or terminate the process.

## Connection and Statement operations

| Operation | Result |
|---|---|
| `SQLite.openMemory()` | Fresh private in-memory Connection |
| `db.prepare(sql)` | Statement, or `null` for empty/comment-only SQL |
| `statement.bind(index, value)` | Same Statement; one-based parameter index |
| `statement.step()` | `true` for a current row, `false` for completion |
| `statement.column(index)` | Independent zero-based current column |
| `statement.row()` | Fresh ordinary Array of independent current columns |
| `statement.each(callback)` | Same Statement after traversing remaining rows |
| `statement.close()` | Fresh terminal standard Future resolving to that Statement |
| `db.close()` | Fresh terminal standard Future resolving to that Connection |

Native work runs synchronously in the current Actor. There is no implicit Task,
Future or parallel execution for open, prepare, binding, step or materialization.
A completed statement stays completed; repeated `step()` returns `false` without
rerunning SQL. A failed step cannot be retried. There is no reset/reuse API.
Bindings are accepted before stepping, as exact signed 64-bit Integers, binary64
Floats or `null`, with no coercion from Fraction, String or other values.
TEXT/BLOB input binding is deferred pending a separately approved lifetime policy;
there is no `SQLITE_STATIC`, `SQLITE_TRANSIENT` or native callback adaptation.

## SQL admission

SQL must be a String containing **no semicolon and no NUL**. The restriction
also rejects semicolons within quoted text, identifiers and comments, including
a conventional trailing terminator. Semicolon-free quoted text, quoted
identifiers, whitespace and comments are passed unchanged to SQLite. Empty and
comment-only SQL produce `null`, never a fabricated native Statement.

This intentionally conservative single-statement subset rejects scripts and
trigger bodies requiring separators. It does not parse SQLite grammar or claim
that SQLite executed an entire multi-statement script. `pzTail` is passed as C
NULL and is never retained, dereferenced or copied from expired call storage.

## Copied rows

SQLite INTEGER becomes exact Integer, FLOAT becomes Float, TEXT becomes a
strict UTF-8 String, BLOB becomes fresh independent Bytes, and SQL NULL becomes
`null`. Empty BLOB remains distinct from NULL. Embedded zero octets are retained
in both TEXT and BLOB. Each `row()` reads and copies all columns before its result
is published; `each` invokes its ordinary Protos Closure only after that copy.
Rows remain unchanged after stepping or closing native resources. Repeated
column/row calls produce independent Bytes storage.

The private adapter calls the appropriate byte accessor, then the preserving
byte-length accessor, then copies exactly that many octets before any
invalidating native call. Each TEXT/BLOB value is limited to **16 MiB**; larger
values fail without truncation. NULL pointers for nonempty values, NULL TEXT,
allocation failures and unknown column types fail closed. Strict UTF-8 errors
keep their existing standard Encoding behavior. No native addresses, borrowed
regions, provider-private buffers or handles are returned by this API.

Column reads require a current row and a valid zero-based Integer index. Reads
before the first row, after completion or after close fail. Callback failures
and non-local returns propagate normally; callers should use `ensure` as above
to close resources on every exit.

## Row reading and materialization

Result columns use **zero-based** indices, unlike one-based parameters.
`column(index)` copies one column of the current row; `row()` copies every
column into a fresh Array. `each(callback)` repeatedly calls `step()` and,
for every remaining row, materializes `row()` and passes it to the callback;
it starts from the current position, so rows already consumed by `step()` are
not revisited.

The callback is an ordinary Protos Closure invoked by Protos code after the
copy completes. It is not a native C callback: SQLite never calls back into
Protos, and no borrowed native memory is reachable from the row. Because the
copies are independent, values remain valid after `step()` advances and after
the Statement or Connection is closed, and mutating one materialized Bytes
value does not affect another copy of the same column.

SQL NULL is always `null`; empty TEXT is `""` and empty BLOB is an empty
Bytes value, never `null`. Embedded NUL octets are kept in both TEXT and BLOB.
TEXT is decoded as strict UTF-8: malformed stored TEXT fails the read with the
standard strict-decoding Error rather than being replaced. Each TEXT/BLOB
value is copied as a whole and is limited to 16 MiB; there is no streaming or
incremental BLOB API, and a larger value fails without truncation.

## Error handling

Not every failure is a `SQLiteError`. Argument and state errors are ordinary
`Error` occurrences; only nonzero SQLite statuses become `SQLite.SQLiteError`.

| Situation | Signaled |
|---|---|
| `prepare` argument is not a String | ordinary `Error` |
| SQL contains a semicolon or NUL (anywhere, including quoted text or comments) | ordinary `Error`, before SQLite is called |
| Parameter index not an Integer, below 1, or above the 32-bit range | ordinary `Error` |
| Parameter value not an exact i64 Integer, Float or `null` | ordinary `Error` |
| `bind` after a row was produced or after completion/failure | ordinary `Error` |
| Parameter index beyond the SQL's parameters | `SQLite.SQLiteError` (`operation: "bind"`) |
| SQL syntax or schema error during preparation | `SQLite.SQLiteError` (`operation: "prepare"`) |
| Execution error during `step()` | `SQLite.SQLiteError` (`operation: "step"`); a later `step()` signals an ordinary `Error` |
| `row()` or `column()` without a current row (before the first step, after completion) | ordinary `Error` |
| Column index not an Integer or out of range | ordinary `Error` |
| TEXT/BLOB value above 16 MiB | `SQLite.SQLiteError` (`operation: "column"`), signaled by the wrapper without truncation |
| Failure inside the generic provider after entry while reading or copying a column | may propagate as `ForeignError`; not necessarily a `SQLiteError` |
| Malformed stored UTF-8 TEXT | standard strict-decoding `Error`, not `SQLiteError` |
| Ordinary operations (`prepare`, `bind`, `step`, `column`, `row`, `each`) on a closed Statement, or on a Statement or Connection after Connection close | `IOLifecycleError`; `close()` is exempt: repeated close returns a fresh terminal Future observing the previously recorded result |
| `openMemory()` without the authorized provider | ordinary `Error` from provider resolution |
| Unsupported execution platform or unavailable shared SQLite | `openMemory()` fails (provider refusal is an ordinary `Error`; a failure after entering the provider is `ForeignError`); test harnesses report UNSUPPORTED |
| Failed native finalize/close | the close Future fails with `SQLite.SQLiteError` (`operation: "finalize"` or `"close"`) |

`SQLiteError` has exactly two informative slots: `operation`, a String naming
the wrapper step (`"open"`, `"prepare"`, `"bind"`, `"step"`, `"column"`,
`"finalize"` or `"close"`), and `code`, the numeric SQLite status. It carries
no native message text, cause chain, host path, native address or Java
exception. Missing-provider behavior is validated separately by
[`sqlite-missing.protos`](../../../protos/tests/foreign-provider/sqlite-missing.protos),
which runs without any provider.

## Ownership and cleanup

```protos
db: SQLite.openMemory()
(() => {
    statement: db.prepare("SELECT 1")
    (() => {
        statement.step()
    }).ensure(() => statement.close().value())   // first: the Statement
}).ensure(() => db.close().value())              // then: its Connection
```

- `close()` is a permanent cutover: the resource is unusable from that point,
  whether or not native release succeeds.
- The native destructor (`sqlite3_finalize` or `sqlite3_close_v2`) runs at
  most once, synchronously, inside the first `close()` call. It is never
  retried.
- Every `close()` call returns a fresh Future identity, and every one of them
  observes the same recorded terminal outcome: the resource on success, or
  the same Error object on failure.
- Connection close first closes every Statement still registered under it,
  then closes the database, preserving the first failure.
- Cleanup is explicit, not a garbage-collector finalizer: collection timing
  is nondeterministic, so native release would no longer happen at a known
  point in the owning Actor's execution. Use `ensure` for every resource.

## Cleanup and failures

Close commits permanent unusability before native release and returns an
already terminal standard Future. It cannot subsequently become cancelled.
Statements finalize at most once, including when finalization reports an earlier
execution error. Connection close finalizes every still-tracked Statement before
`sqlite3_close_v2`, continues mandatory cleanup after failures, and preserves the
first cleanup Error. Parent custody retains private child cleanup Closures, so
changing a public Statement method does not revoke mandatory release.

Every repeated close returns a fresh Future observing the same recorded outcome.
A failed close preserves its exact domain-local Error object and never retries
an uncertain destructor or resurrects a retired handle. Prefer explicit Statement
close followed by Connection close; the latter also retires any remaining children.

Invalid arguments signal ordinary Errors. Provider admission refusal remains an
ordinary Error; an entered provider failure retains `ForeignError`. The wrapper
interprets SQLite failure statuses as fresh ordinary `SQLite.SQLiteError`
occurrences with safe `operation` and `code` fields. No native error text, host
paths, native address or Java exception is included. Failed acquisition cleans up
any non-null owned output before signaling the primary error. `sqlite3_errmsg`
is omitted because R1 supplies no bounded C-string length operation.

## Validation evidence

The maintainer reports successful Java compilation and focused Java tests.

`ProtosLib029SqliteLifecycleTest` executes the deterministic lifecycle and
borrowed-memory failure models in `sqlite-failure-model.protos` and
`sqlite-borrow-model.protos`. These exercise wrapper cleanup, first-error
preservation, column-access/copy ordering, NULL/allocation handling and bounds
using Protos ABI doubles. They do not prove actual SQLite native failure paths.
Existing I144 fixtures separately cover generic native custody, borrow-epoch
revocation and native-destructor counters.

`dist/test_external_foreign_providers.py` executes the real SQLite query, row,
lifecycle, alternative-alias rejection, example and tutorial cases through the
explicitly authorized external FFM provider. It also runs the provider-free
missing-provider case and retains separate PASS, FAIL and UNSUPPORTED outcomes.
The maintainer reports **34/34 PASS** for the complete external foreign-provider
integration, including the LIB029 real SQLite cases. The alias case rejects an
unauthorized alternative alias while confirming that the separately authorized
`ffi:sqlite3` alias remains available; it does not test denial of `ffi:sqlite3`.

For the published LIB029 implementation
`90fd4ece800f6d0e007aa9a2c735ee2f7659919a`, the maintainer also reports
`make check` PASS and `make test` PASS.

The DOC024 tutorial and cookbook programs are registered in the same harness as
`DOC024_TUTORIAL_SQLITE_LIFECYCLE`, `DOC024_EXAMPLE_NUMERIC` and
`DOC024_EXAMPLE_COPIED_VALUES`. Their validation is pending until the maintainer
runs it.

## Known limitations

- Only in-memory connections (`openMemory()`); there is no supported
  arbitrary database path API.
- Only SQL free of semicolons and NUL.
- Only Integer, Float and `null` input parameters; TEXT/BLOB results are
  copied out, never bound in.
- Each TEXT/BLOB result value is limited to 16 MiB of borrowed-copy data.
- Linux x86-64 LP64, JDK 25 portable JVM and the generic optional FFM
  provider only; no Native Image loading of external provider JARs.
- Native authority must be granted explicitly by the host.
- SQLite runs as trusted in-process native code: there is no native C sandbox,
  and Protos Filesystem authority does not confine it.
- No general transaction abstraction, native callbacks, ORM or query builder.
