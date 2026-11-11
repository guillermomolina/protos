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

The maintainer is running the final integrated `make test`; its outcome has not
yet been reported. No completed full-suite acceptance is recorded. The editing
agent did not execute validation.
