# SQLite: `std:sqlite`

Use `std:sqlite` to open a database, prepare a query, bind values, and read
copied rows without managing native pointers. This guide also covers statement
reuse, file-backed connections, and manual transactions.

The normative authority is [SQLITE.md](../../../spec/semantics/SQLITE.md).
This guide explains the Standard Library contract; it adds no Core storage type.

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

Follow these executable lessons and examples in order:

1. [`05-sqlite-protos-first.protos`](../../../protos/tutorials/12-system-resources/05-sqlite-protos-first.protos):
   memory connection, typed parameters and copied results.
2. [`06-sqlite-query-lifecycle.protos`](../../../protos/tutorials/12-system-resources/06-sqlite-query-lifecycle.protos):
   creation, queries, reuse, errors and resource lifecycle.
3. [`07-sqlite-durable-transactions.protos`](../../../protos/tutorials/12-system-resources/07-sqlite-durable-transactions.protos):
   persistent transactions, rollback and read-only reopen.
4. [`sqlite-durable.protos`](../../../protos/examples/ffi/sqlite-durable.protos):
   complete application with typed persistent data and explicit transactions.
5. [`sqlite-wal.protos`](../../../protos/examples/ffi/sqlite-wal.protos):
   explicit WAL and synchronous FULL policy, verified through SQLite.

Additional focused examples cover
[numeric filtering](../../../protos/examples/ffi/sqlite-prepared-numeric.protos),
[copied TEXT/BLOB values](../../../protos/examples/ffi/sqlite-copied-values.protos)
and the [first table/query](../../../protos/examples/ffi/sqlite-library.protos).

To declare your own C functions use the public [`std:ffi` guide](ffi.md); for
backend implementation and native lifetime details see the advanced
[FFM provider guide](../interop/ffm-provider.md).

## Getting started

There are two workflows. In both, SQLite must be authorized explicitly with
`--allow-library sqlite3=libsqlite3.so.0`, and `import("std:sqlite")` alone
loads no SQLite library. The authorized library runs as trusted native code in
the process; Protos `Filesystem` permissions do not confine it.

### Native Image — installed executable

```sh
protos --allow-library sqlite3=libsqlite3.so.0 \
    protos/examples/ffi/sqlite-library.protos
```

Here `protos` is the installed Native executable, and the example path is
relative to a repository checkout. The operating system supplies
`libsqlite3.so.0`; no JDK, provider JAR or dynamic Java plugin is required,
because the generic FFM provider is integrated into the executable. The durable
tutorial takes a disposable database filename:

```sh
protos --allow-library sqlite3=libsqlite3.so.0 \
    protos/tutorials/12-system-resources/07-sqlite-durable-transactions.protos \
    /tmp/protos-sqlite-tutorial.db
```

### Portable JVM — external FFM provider

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

3. Load the provider explicitly on every run with
   `--foreign-provider-path`, and authorize SQLite separately with
   `--allow-library sqlite3=libsqlite3.so.0`; the provider authorizes no
   library by default. Building the JAR authorizes nothing, and
   `import("std:sqlite")` alone loads no SQLite library: `openMemory()` and
   `openFile()` reach the authorized `ffi:sqlite3` provider.

4. Run the introductory example:

   ```sh
   bin/protos --foreign-provider-path target/ffm-provider/protos-ffm-provider.jar --allow-library sqlite3=libsqlite3.so.0 \
       protos/examples/ffi/sqlite-library.protos
   ```

5. Run the tutorial and cookbook programs:

   ```sh
   bin/protos --foreign-provider-path target/ffm-provider/protos-ffm-provider.jar --allow-library sqlite3=libsqlite3.so.0 \
       protos/tutorials/12-system-resources/06-sqlite-query-lifecycle.protos
   bin/protos --foreign-provider-path target/ffm-provider/protos-ffm-provider.jar --allow-library sqlite3=libsqlite3.so.0 \
       protos/examples/ffi/sqlite-prepared-numeric.protos
   bin/protos --foreign-provider-path target/ffm-provider/protos-ffm-provider.jar --allow-library sqlite3=libsqlite3.so.0 \
       protos/examples/ffi/sqlite-copied-values.protos
   ```

   Continue with persistent programs, supplying disposable filenames. Each
   program confines table creation and row changes to its documented table;
   the WAL example also changes the journaling mode of the entire database
   persistently. Use a disposable database, not an existing file whose
   journaling mode must be preserved. These programs do not delete database
   files or SQLite sidecars.

   ```sh
   bin/protos --foreign-provider-path target/ffm-provider/protos-ffm-provider.jar --allow-library sqlite3=libsqlite3.so.0 \
       protos/tutorials/12-system-resources/05-sqlite-protos-first.protos
   bin/protos --foreign-provider-path target/ffm-provider/protos-ffm-provider.jar --allow-library sqlite3=libsqlite3.so.0 \
       protos/tutorials/12-system-resources/07-sqlite-durable-transactions.protos /tmp/protos-sqlite-tutorial.db
   bin/protos --foreign-provider-path target/ffm-provider/protos-ffm-provider.jar --allow-library sqlite3=libsqlite3.so.0 \
       protos/examples/ffi/sqlite-durable.protos /tmp/protos-sqlite-durable.db
   bin/protos --foreign-provider-path target/ffm-provider/protos-ffm-provider.jar --allow-library sqlite3=libsqlite3.so.0 \
       protos/examples/ffi/sqlite-wal.protos /tmp/protos-sqlite-wal.db
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

- Accepted values are exact signed 64-bit Integers, binary64 Floats, Strings
  (TEXT), Bytes (BLOB) and `null`. There is no implicit numeric coercion: a
  Fraction such as `1 / 2`, a Boolean or an out-of-range Integer is rejected.
- `bind` returns the same Statement, so binds can be chained.
- Once a Statement has produced a row or completed, it cannot be rebound
  until `reset()` (see [Reusing a Statement](#reusing-a-statement)).

## TEXT and BLOB parameters

Bind application data as parameters; never interpolate it into SQL text.

```protos
insert: db.prepare("INSERT INTO files (name, payload) VALUES (?1, ?2)")
(() => {
    payload: Encoding.UTF8.encode("")
    for(octet in Array(0, 80, 0, 255)) { payload.add(octet) }
    insert.bind(1, "report; DROP TABLE files; --").bind(2, payload)
    payload[0] = 9                 // later mutation does not change the binding
    insert.step()
}).ensure(() => insert.close().value())
```

- A String binds as SQLite TEXT, encoded as strict UTF-8. Every Unicode scalar,
  including supplementary characters and interior NUL, is preserved; `""` is
  empty TEXT, never SQL NULL.
- Bytes binds as SQLite BLOB. Every octet, including `0x00`, is preserved;
  empty Bytes is a zero-length BLOB, never SQL NULL.
- `null` is SQL NULL. The three are distinct: `typeof(?)` answers `"text"`,
  `"blob"` and `"null"`.
- The value is captured when `bind` runs. SQLite makes its own private copy
  before `bind` returns, so later changes to the Bytes object do not affect the
  binding, and no Protos-visible memory is shared with SQLite.
- Each TEXT or BLOB input is limited to **16 MiB** (16777216 octets, measured
  after UTF-8 encoding). This is a `std:sqlite` input policy, chosen to match
  the copied-column bound; it is not a limit of the FFM provider. Larger values
  are rejected with an ordinary `Error` before SQLite is called, never truncated.

How Bytes reach SQLite is a private implementation detail. The foreign-value
boundary carries no Bytes, so the library's private ABI adapter snapshots the
Bytes as a String through Latin-1, the bijection between the 256 octets and
U+0000..U+00FF. A provider-private input descriptor turns each character back
into exactly one octet. This is a lossless binary transport, not a text
decoding. The FFM provider does not accept Bytes, and the public `std:ffi`
contract has no TEXT/BLOB input descriptor.

## Reusing a Statement

A completed Statement stays completed: repeated `step()` returns `false` and
does not run the SQL again. `reset()` is the explicit transition to a new
execution:

```protos
insert: db.prepare("INSERT INTO items (name, data) VALUES (?1, ?2)")
(() => {
    // First execution: bind, then step.
    insert.bind(1, "a").bind(2, Encoding.Latin1.encode("\u{0}"))
    insert.step()
    // Every further execution: reset, bind, step.
    insert.reset().bind(1, "b").bind(2, Encoding.Latin1.encode("\u{FF}"))
    insert.step()
}).ensure(() => insert.close().value())
```

Use `clearBindings()` only when parameters that are not rebound must become
SQL NULL; rebinding a parameter already replaces its previous value.

| Operation | Effect |
|---|---|
| `statement.reset()` | Calls `sqlite3_reset`; invalidates the current row; keeps every binding; executes nothing; answers the Statement |
| `statement.clearBindings()` | Calls `sqlite3_clear_bindings`; every parameter becomes SQL NULL; does not reset, execute or invalidate a current row; answers the Statement |

`clearBindings()` is not a reset: a completed Statement stays completed until
`reset()`. After `reset()`, new values may be bound and the Statement stepped
again. `reset()` after reading only part of the rows restarts from the first row.

If the previous execution failed, SQLite reports that failure again from
`sqlite3_reset`: the first `reset()` then signals `SQLite.SQLiteError`
(`operation: "reset"`) and the Statement stays failed. It is never resumed
implicitly, and an uncertain provider failure during `reset()` leaves it failed
too. A later `reset()` that SQLite confirms with `SQLITE_OK` makes it reusable.

## Authority and target

Importing `std:sqlite` imports ordinary Protos code and acquires no native
library or authority. `openMemory()` and `openFile()` use the explicitly authorized
`ffi:sqlite3` provider, so only the Process's RootActor can open a database;
other Actors may import `std:sqlite`, but their `openMemory()` and `openFile()`
fail with an ordinary Error before native entry. Every Connection, Statement,
binding and library session belongs to the RootActor. Live resources cannot
cross Actor or P boundaries; ordinary copied rows follow ordinary
value-transfer rules. There is no automatic proxy.

The supported execution profile is Linux x86-64 LP64, the portable JVM, JDK 25
and the supported GraalVM 25.5 toolchain, with `libsqlite3.so.0` installed and
the optional FFM provider built and explicitly supplied by the host:

```sh
bin/protos --foreign-provider-path target/ffm-provider/protos-ffm-provider.jar --allow-library sqlite3=libsqlite3.so.0 \
    protos/examples/ffi/sqlite-library.protos
```

[Provider build instructions](../interop/ffm-provider.md#build-the-provider-jar)
apply unchanged.

With the installed Native Image executable no provider JAR is needed; install
`libsqlite3.so.0` from the operating system and authorize it:

```sh
protos --allow-library sqlite3=libsqlite3.so.0 app.protos
```

Java, GraalVM and a separate Protos FFM provider JAR are not needed. SQLite is
neither bundled nor linked into `protos`; it is loaded only when a program first
opens a connection. Native Image does not load provider plugins dynamically.
A missing or denied provider is a failure.

Under D259, authorizing SQLite accepts its native effects with the operating
system process's permissions. This includes file I/O outside any guest
`Filesystem` capability, including SQLite sidecars, temporary files and SQL file
operations. Guest capabilities remain independent. `std:sqlite` is not a sandbox:
native faults can corrupt memory or terminate the process. There is no SQLite
sandbox or Landlock confinement; guest Filesystem grants do not confine C.

## Persistent connections

```protos
SQLite: import("std:sqlite")
db: SQLite.openFile(process.args()[0])  // mode defaults to "readWriteCreate"
(() => {
    statement: db.prepare("CREATE TABLE IF NOT EXISTS items(id INTEGER PRIMARY KEY, name TEXT)")
    (() => { statement.step() }).ensure(() => statement.close().value())
}).ensure(() => db.close().value())
```

`openFile(path, mode: "readWriteCreate")` has one optional parameter, using
ordinary Protos default-parameter semantics:

| Mode String | Behavior |
| --- | --- |
| `"readOnly"` | Opens an existing database for reading |
| `"readWrite"` | Opens an existing database for reading and writing |
| `"readWriteCreate"` | Opens for reading and writing, creating if absent; default |

Before acquiring SQLite, the library rejects non-String/empty paths, NUL,
`:memory:` (use `openMemory()`), `file:` URI names and any other mode with an
ordinary `Error`. Relative paths use SQLite's OS process working directory;
there is no guest Path conversion or Filesystem permission check. The wrapper
adds no URI/open flags and creates no directories. SQLite performs the actual
path opening and locking under OS authority.

For requested writable modes, a successful open is followed by
`sqlite3_db_readonly(db, "main")`. An effective read-only database is closed
and rejected with `SQLiteError(operation: "open", code: 8)`; an unavailable
schema result is rejected with code 1. Provider failures propagate. Any acquired
handle is released once even if open failed or this check fails, preserving
the primary error over cleanup. A writable open cannot guarantee future writes:
permissions, available space, locks and device errors may change.

## Manual transactions and lock waits

```protos
db.begin()
Assertions.require(db.inTransaction())
insert: db.prepare("INSERT INTO items(name) VALUES(?1)")
(() => {
    insert.bind(1, "parameterized data")
    insert.step()
}).ensure(() => insert.close().value())
db.commit()
Assertions.require(!db.inTransaction())

// A separate transaction can explicitly undo a prepared change.
db.begin()
change: db.prepare("UPDATE items SET name=?1 WHERE name=?2")
(() => {
    change.bind(1, "temporary").bind(2, "parameterized data")
    change.step()
    Assertions.require(db.inTransaction())
}).ensure(() => change.close().value())
db.rollback()
Assertions.require(!db.inTransaction())
```

These snippets run within the Connection's `ensure` scope and use
`Assertions: import("std:test/assertions")`.

`begin()`, `commit()` and `rollback()` prepare and execute exactly `BEGIN`,
`COMMIT` and `ROLLBACK`, finalize the control Statement on every path and answer
the same Connection on success. Execution failure remains primary when
finalization also fails; a sole finalization failure propagates. There is no
automatic retry, cached control Statement, nested-transaction emulation or
implicit rollback after failed COMMIT. These methods work on memory connections
too. Ordinary SQL may also issue transactions or SQLite savepoints.

`inTransaction()` queries `sqlite3_get_autocommit` on every call: disabled
autocommit means `true`. It does not inspect a wrapper-maintained Boolean.
COMMIT can fail with `SQLITE_BUSY` or a deferred constraint while leaving the
transaction active; other SQLite errors may cause automatic rollback. Observe
`inTransaction()` after a failure and choose explicitly whether to retry or
roll back. An entered foreign failure may leave effects uncertain; an error
does not establish that no change occurred. `close()` never commits: after
child Statements are finalized, SQLite rolls back an open transaction.

`db.busyTimeout(milliseconds)` accepts an Integer in `0..2147483647`, invokes
`sqlite3_busy_timeout`, and returns the Connection. Invalid values fail before
native entry. Non-OK status becomes `SQLiteError(operation: "busyTimeout", code: status)`.
Zero disables the native busy handler. Opening sets no timeout automatically.
SQLite can still return BUSY without waiting in some lock situations. A busy
timeout concerns qualifying lock waits; it is neither query cancellation nor
a deadline for arbitrary C operations.

Native calls occupy the invoking Actor synchronously. Task cancellation does
not call `sqlite3_interrupt`, cancel an entered statement or automatically
roll back/commit. There is no implicit Task, executor, Future or suspension.
The existing terminal Future returned by `close()` retains its contract.

## WAL, durability and recovery

**Journal mode**

- `PRAGMA journal_mode=WAL` changes the mode of the entire database, not a table.
- WAL persists after closing and reopening; other connections to the same file
  use that mode.
- `std:sqlite` never changes the journal mode automatically.

**Synchronous**

- `PRAGMA synchronous=FULL` configures the synchronization policy of the
  connection executing it.
- A new connection must configure its own policy when the application requires
  that value explicitly; automatic inheritance must not be assumed.
- `PRAGMA synchronous` returns the effective value; `FULL` corresponds to `2`.
- Final durability still depends on SQLite, VFS, filesystem and storage.

Journal and synchronous policy remain SQLite's native configuration until the
application explicitly changes them through ordinary prepared SQL:

```protos
journal: db.prepare("PRAGMA journal_mode=WAL")
(() => {
    Assertions.require(journal.step())
    Assertions.require(journal.column(0) == "wal")
    Assertions.require(journal.step() === false)
}).ensure(() => journal.close().value())
sync: db.prepare("PRAGMA synchronous=FULL")
(() => { sync.step() }).ensure(() => sync.close().value())
```

SQLite owns WAL/SHM/journal files, lock coordination and recovery. WAL does not
permit simultaneous writers, and a reader can prevent COMMIT in rollback-journal
mode. Applications must handle native failures explicitly. COMMIT durability
depends on SQLite configuration, VFS, filesystem, storage and successful native
I/O; the wrapper promises no universal power-loss guarantee. Connection close
is resource cleanup, not an extra commit or storage sync.

The [WAL example](../../../protos/examples/ffi/sqlite-wal.protos) checks the
selected WAL mode and `synchronous == 2` on its writer connection. After closing
that connection and reopening read-only, it queries `PRAGMA journal_mode` to
check that WAL remains active, then verifies the committed rows. It does not
set journal mode on the read-only connection or assume that FULL is inherited.

The complete [durable example](../../../protos/examples/ffi/sqlite-durable.protos)
creates/replaces its `durable_demo` table, commits prepared inserts using a reused
Statement, rolls back an update, queries and reopens read-only with assertions.
Supply a disposable filename explicitly:

```sh
bin/protos --foreign-provider-path target/ffm-provider/protos-ffm-provider.jar --allow-library sqlite3=libsqlite3.so.0 \
    protos/examples/ffi/sqlite-durable.protos /tmp/sqlite-example.db
```

## Connection and Statement operations

| Operation | Result |
|---|---|
| `SQLite.openMemory()` | Fresh private in-memory Connection |
| `SQLite.openFile(path, mode: "readWriteCreate")` | Fresh persistent Connection |
| `db.prepare(sql)` | Statement, or `null` for empty/comment-only SQL |
| `db.begin()` / `db.commit()` / `db.rollback()` | Same Connection after native transaction control |
| `db.inTransaction()` | Current SQLite transaction state as Boolean |
| `db.busyTimeout(milliseconds)` | Same Connection after configuring native lock waits |
| `statement.bind(index, value)` | Same Statement; one-based parameter index |
| `statement.step()` | `true` for a current row, `false` for completion |
| `statement.reset()` | Same Statement, ready to run again with its bindings |
| `statement.clearBindings()` | Same Statement, every parameter SQL NULL |
| `statement.column(index)` | Independent zero-based current column |
| `statement.row()` | Fresh ordinary Array of independent current columns |
| `statement.each(callback)` | Same Statement after traversing remaining rows |
| `statement.close()` | Fresh terminal standard Future resolving to that Statement |
| `db.close()` | Fresh terminal standard Future resolving to that Connection |

Native work runs synchronously in the current Actor. There is no implicit Task,
Future or parallel execution for open, prepare, binding, step or materialization.
A completed statement stays completed; repeated `step()` returns `false` without
rerunning SQL. A failed step is never retried implicitly; only an explicit
`reset()` confirmed by SQLite starts a new execution. Bindings are accepted before
the first step or after `reset()`, as exact signed 64-bit Integers, binary64
Floats, Strings, Bytes or `null`, with no coercion from Fraction or other values.
TEXT/BLOB inputs are copied by SQLite during the binding call (the private
adapter passes `SQLITE_TRANSIENT`); no `SQLITE_STATIC` custody, native callback
or Protos-visible pointer exists.

## SQL admission

`prepare` admits **exactly one** SQL statement, judged by SQLite's own parser.
Conventional `;` terminators, trailing whitespace and trailing comments are
accepted, as are semicolons inside quoted text, quoted identifiers and comments,
and the internal separators of grammar such as trigger bodies:

```protos
db.prepare("SELECT 'a;b';  -- one statement")       // admitted
db.prepare("CREATE TRIGGER t AFTER INSERT ON a BEGIN INSERT INTO b VALUES (1); END;")
db.prepare("SELECT 1; SELECT 2")                     // ordinary Error
```

The wrapper does not split or parse SQL itself. `sqlite3_prepare_v2` reports
how much input it consumed; that measurement is taken while the call's input copy
is alive, and the tail pointer is never retained. Any remainder is passed to
SQLite again: separators, whitespace and comments yield no statement, while a
second statement is finalized and rejected with an ordinary `Error`, and invalid
trailing SQL signals `SQLite.SQLiteError` (`operation: "prepare"`). Preparing
never executes SQL, so no prefix of a rejected input ever runs. A Statement that
SQLite produced before the rejection is finalized exactly once.

SQL must be a String without NUL; both are checked with an ordinary `Error`
before SQLite is called. Empty, separator-only and comment-only SQL produce
`null`, never a fabricated native Statement.

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
| SQL contains NUL | ordinary `Error`, before SQLite is called |
| SQL contains a second statement after the first | ordinary `Error`; any prepared Statement is finalized once |
| Invalid SQL after a complete first statement | `SQLite.SQLiteError` (`operation: "prepare"`) |
| Parameter index not an Integer, below 1, or above the 32-bit range | ordinary `Error` |
| Parameter value not an exact i64 Integer, Float, String, Bytes or `null` | ordinary `Error` |
| TEXT/BLOB input above 16 MiB, or a String that is not valid Unicode | ordinary `Error`, before SQLite is called |
| `bind` after a row was produced or after completion/failure, without `reset()` | ordinary `Error` |
| `reset()` reporting a previous execution failure or another status | `SQLite.SQLiteError` (`operation: "reset"`); the Statement stays failed |
| `clearBindings()` non-OK status | `SQLite.SQLiteError` (`operation: "clearBindings"`) |
| Parameter index beyond the SQL's parameters | `SQLite.SQLiteError` (`operation: "bind"`) |
| SQL syntax or schema error during preparation | `SQLite.SQLiteError` (`operation: "prepare"`) |
| Execution error during `step()` | `SQLite.SQLiteError` (`operation: "step"`); a later `step()` signals an ordinary `Error` |
| `row()` or `column()` without a current row (before the first step, after completion) | ordinary `Error` |
| Column index not an Integer or out of range | ordinary `Error` |
| TEXT/BLOB value above 16 MiB | `SQLite.SQLiteError` (`operation: "column"`), signaled by the wrapper without truncation |
| Failure inside the generic provider after entry while reading or copying a column | may propagate as `ForeignError`; not necessarily a `SQLiteError` |
| Malformed stored UTF-8 TEXT | standard strict-decoding `Error`, not `SQLiteError` |
| Ordinary operations (`prepare`, `bind`, `step`, `reset`, `clearBindings`, `column`, `row`, `each`) on a closed Statement, or on a Statement or Connection after Connection close | `IOLifecycleError`; `close()` is exempt: repeated close returns a fresh terminal Future observing the previously recorded result |
| Invalid `openFile` path/mode or `busyTimeout` argument | ordinary `Error` before native entry |
| File opening fails | `SQLite.SQLiteError` (`operation: "open"`, native status) |
| Transaction control fails | `SQLite.SQLiteError` naming `prepare`, `step` or `finalize`; execution error precedes finalization error |
| New Connection operations after close | `IOLifecycleError` before native entry |
| Opening without a provider | ordinary resolution `Error` |
| Unauthorized/unavailable native library | D259 `ForeignError`, categories `unauthorized-library` / `library-unavailable` |
| Unsupported execution platform | Provider refusal |
| Failed native finalize/close | the close Future fails with `SQLite.SQLiteError` (`operation: "finalize"` or `"close"`) |

`SQLiteError` has exactly two informative slots: `operation`, a String naming
the wrapper step (`"open"`, `"prepare"`, `"bind"`, `"step"`, `"reset"`,
`"clearBindings"`, `"column"`, `"finalize"`, `"close"` or `"busyTimeout"`), and `code`, the numeric SQLite status. It carries
no native message text, cause chain, host path, native address or Java
exception.

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
is omitted because the existing SQLite adapter supplies no bounded C-string
length operation; R2 aggregate snapshots do not change that adapter.

## Known limitations

- `openFile` accepts filename Strings; `file:` URIs and `:memory:` are rejected.
- Exactly one SQL statement per `prepare`; no script execution.
- Each TEXT/BLOB input and each TEXT/BLOB result value is limited to 16 MiB;
  there is no streaming or incremental BLOB API.
- Linux x86-64 LP64: JDK 25 portable JVM with the generic optional FFM
  provider, or installed Native Image with that provider integrated and exact
  registered signatures; no dynamic provider JAR loading in Native Image.
- SQLite is OS-provided and explicitly host-authorized, never bundled.
- Native authority must be granted explicitly by the host.
- SQLite runs as trusted in-process native code: there is no native C sandbox,
  and Protos Filesystem authority does not confine it.
- Manual transactions only; no scoped transaction helper, pool, native
  callbacks, ORM or query builder.

