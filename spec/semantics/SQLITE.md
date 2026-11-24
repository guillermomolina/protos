# Standard Library SQLite

The project owner ratified this normative `std:sqlite` Standard Library
contract on 2026-11-22 under D256-C1R. This document owns the public SQLite
operations, results, admission, errors and lifecycle described below. It does
not introduce a Core type or a global storage capability. Ratification records
the approved contract; executable acceptance remains separate evidence.

## Scope and governing owners

`std:sqlite` is an ordinary SQLite-specific Standard Library MODULE. Protos
objects, Closures and slots implement its public policy; native SQLite owns
database files, transactions, locks, journaling, WAL and recovery. There is no
new Core Storage, general database abstraction, VFS, worker, IPC or Java SQLite
contract.

The following owners retain their authority without modification:

- [FFI](FFI.md): D259 host authorization, synchronous C calls, ABI admission,
  resource custody and provider errors. `i32` is a C ABI descriptor whose
  Protos values are exact Integers; it introduces no Core `int` primitive.
- [Modules](MODULES.md): ordinary import, private MODULE initializer bindings
  and Actor-local instances.
- [Actors](../concurrency/ACTORS.md), especially §§24J and 24K.1: synchronous
  execution segments, trusted native effects and non-transferable handles.
- [Process I/O](../io/PROCESS_IO.md) and [Filesystem](../io/FILESYSTEM.md):
  independently provisioned guest authority. Native authorization does not
  create or widen a guest capability.
- [Errors](ERRORS.md) and [execution/control](EXECUTION_AND_CONTROL.md):
  ordinary signaling, dynamic handlers and `ensure` cleanup.
- [Futures/tasks](../concurrency/FUTURES_AND_TASKS.md): terminal Future
  observation and cancellation. SQLite introduces no new suspension boundary.
- [Values/collections](VALUES_AND_COLLECTIONS.md) and [text I/O](../io/TEXT_IO.md):
  ordinary Integer/Float/String/Bytes/Array and strict encoding semantics.

## Native admission and authority

Importing `std:sqlite` acquires no native library, grants no authority and is
permitted in every Actor. Only opening a Connection acquires the existing
`ffi:sqlite3` facade. Opening requires the RootActor's native FFM authority
(`FFI.md`, RootActor-only native authority) and, independently, the host's
explicit authorization of that alias under D259, independently of supplying
the optional generic FFM provider. In any other Actor, `openMemory` and
`openFile` fail with an ordinary Error before native entry. Connections and
Statements remain local to the RootActor that opened them; copied rows keep
their ordinary transfer semantics, and no automatic proxy is introduced.

The trusted library can perform every native effect permitted to the OS
process, including filesystem access beyond guest Filesystem directories.
Database filenames are not confined capabilities. SQLite sidecars, temporary
files and SQL file operations remain native effects. This library promises no
SQLite sandbox, kernel confinement or protection against arbitrary native
faults. Guest Filesystem and other guest APIs retain their own authority rules.

The initial execution profile is the portable JVM on Linux x86-64 LP64,
JDK 25, the supported GraalVM toolchain and the optional generic FFM provider,
with an authorized shared SQLite library. Other ABIs, platforms and Native
Image external provider loading acquire no support promise here.

## Public module and acquisition

The MODULE exposes `SQLiteError`, `openMemory()` and
`openFile(path, mode: "readWriteCreate")`. The latter uses one ordinary default
parameter, not method overloading. Explicit `null` is not omission.

`openMemory()` preserves the existing private `:memory:` database acquisition.
`openFile` requires a nonempty String path without NUL. The exact special name
`:memory:` and names beginning with the case-sensitive prefix `file:` are
rejected. The mode must be exactly one of the following Strings:

| Mode | Native open flags |
| --- | --- |
| `readOnly` | `SQLITE_OPEN_READONLY` (1) |
| `readWrite` | `SQLITE_OPEN_READWRITE` (2) |
| `readWriteCreate` | `SQLITE_OPEN_READWRITE + SQLITE_OPEN_CREATE` (6); default |

Argument admission precedes native acquisition. No directories are created by
the wrapper; no guest Path/Filesystem conversion or permission check is added.
SQLite resolves filenames under OS process authority. URI flags, custom VFS
names and user configuration objects are not accepted by this surface.

Opening passes exactly the filename, flags and a null VFS name to
`sqlite3_open_v2`. After successful open for either writable mode,
`sqlite3_db_readonly(database, "main")` must report 0 before the Connection is
published. Result 1 rejects acquisition with `SQLiteError` operation `open`,
code `SQLITE_READONLY` (8); any other result rejects it with operation `open`,
code `SQLITE_ERROR` (1). This is an effective-mode check, not a guarantee that
every subsequent write will succeed.

Any non-null output remains in custody even when open also reports failure.
Failed acquisition releases that output once, preserving the primary failure
over a cleanup failure. A successful status with null output is rejected using
the existing `SQLiteError(operation: "open", code: 0)` failure convention.
The provider's independently governed custody applies if result conversion
itself fails before an output is returned to Protos.

## Connection operations

| Operation | Result and effects |
| --- | --- |
| `prepare(sql)` | One Statement, or null for blank/comment-only input |
| `begin()` | Executes `BEGIN`; answers the same Connection |
| `commit()` | Executes `COMMIT`; answers the same Connection |
| `rollback()` | Executes `ROLLBACK`; answers the same Connection |
| `inTransaction()` | Boolean observation of SQLite's current autocommit state |
| `busyTimeout(milliseconds)` | Sets SQLite lock-wait policy; answers the Connection |
| `close()` | Fresh terminal standard Future observing the recorded close outcome |

The transaction methods share one private control operation that prepares
exactly one statement through normal admission, steps it to the expected DONE
result and always finalizes it. It does not use `sqlite3_exec`, a control
statement cache, a second SQL engine or retries. An unexpected ROW is a wrapper
state Error. Execution failure remains primary when finalization also fails;
a sole finalization failure propagates. Native error operations are `prepare`,
`step` or `finalize`, matching the actual failing wrapper stage.

`BEGIN` uses SQLite's default transaction mode. Nested BEGIN and COMMIT/ROLLBACK
without an active transaction retain SQLite's failure behavior. Ordinary SQL
transaction/savepoint commands are also permitted; no wrapper state machine
emulates them.

Every `inTransaction()` calls `sqlite3_get_autocommit`: result 0 means true,
otherwise false. There is no mirrored transaction-active/committed/rolled-back
flag. A failed COMMIT may leave a transaction active, including after BUSY or
deferred constraint failure. Other SQLite errors may automatically roll back.
The wrapper performs neither implicit rollback nor retry. An entered foreign
failure does not establish that an operation had no effect.

`busyTimeout` admits only an Integer in `0..2147483647`, then calls
`sqlite3_busy_timeout`. Zero disables the native busy handler; opening sets no
timeout. SQLite's busy mechanism concerns qualifying native lock waits and may
decline to wait in some situations. It is not a general operation deadline,
query cancellation or a promise that every conflict waits for the given period.

## Statements, data and admission preserved

`prepare` accepts a String without NUL and delegates complete admission to
SQLite's parser. It prepares the remainder until the entire source is judged:
trailing semicolons/whitespace/comments are accepted; a second executable
statement is finalized and rejected. Invalid trailing SQL preserves SQLite's
status. No prefix of rejected SQL is executed.

The public Statement operations are:

| Operation | Result |
| --- | --- |
| `bind(index, value)` | Same Statement after binding one parameter |
| `step()` | Boolean: true for ROW, false for DONE |
| `reset()` | Same Statement after successful reset |
| `clearBindings()` | Same Statement after setting all bindings to SQL NULL |
| `column(index)` | Independent value from the current row |
| `row()` | Fresh Array of all current column values |
| `each(callback)` | Same Statement after traversing remaining rows |
| `close()` | Fresh terminal standard Future observing the recorded close outcome |

Binding uses one-based Integer indices and accepts
exact signed i64 Integer, Float, String, Bytes or null without coercion. String
and Bytes inputs are independently copied by SQLite before bind returns, with
strict UTF-8 for TEXT, exact octets for BLOB and embedded zeros retained.
TEXT/BLOB inputs and copied result values are each bounded to 16 MiB.

`step` answers true for a current row and false for DONE. DONE is terminal
until explicit successful reset; failed/uncertain execution is not implicitly
replayed. `reset` keeps bindings and invalidates the row; only SQLite-confirmed
success makes a failed Statement reusable. `clearBindings` sets every binding
to SQL NULL and neither resets nor executes.

Zero-based column reads require a current row. INTEGER/FLOAT/TEXT/BLOB/NULL
become ordinary Integer/Float/String/independent Bytes/null. Each row is a fresh
Array of independent data; `each` invokes its ordinary Closure after copying
the complete row. `each(callback)` repeatedly steps and copies each resulting
row before invoking the callback; it does not revisit a row already obtained
by `step()`. Callback failures and non-local returns propagate under ordinary
language rules. There are no native callbacks, Java row objects, exported
pointers or borrowed buffers. Existing copied-data and SQL admission contracts
are preserved for both memory and file Connections.

## Errors, execution and lifecycle

`SQLiteError` remains an ordinary Error prototype. Occurrences carry safe
`operation` String and numeric `code` slots, with no native message, address,
host path or Java exception payload.

| Failure | Mapping |
| --- | --- |
| Invalid filename, mode or timeout | Ordinary Error before native entry |
| SQLite status failure | SQLiteError naming the failing stage and native status |
| Requested writable open is effectively read-only | SQLiteError `open`, code 8 |
| Effective main-schema mode cannot be established | SQLiteError `open`, code 1 |
| Non-OK busy-timeout status | SQLiteError `busyTimeout`, native status |
| Missing provider / admission refusal | Existing resolution/FFI Error behavior |
| D259 authorization/load failure | Existing ForeignError categories from FFI |
| Entered foreign failure | Existing ForeignError, not flattened into SQLiteError |
| Ordinary operation after resource close | IOLifecycleError before native entry |
| Destructor failure | Failed terminal close Future, retaining its exact Error |

Operations execute synchronously within the invoking Actor segment; blocking
C occupies that segment. There is no implicit Task, suspension, Future for
ordinary work, background executor, automatic native interruption or
`sqlite3_interrupt` on Task cancellation. Ordinary language unwind and cleanup
rules still apply at their existing boundaries.

Connections, Statements, bindings and their native session remain Actor-local
and non-transferable. Copied data follow their ordinary transfer contracts.
Changing a public Statement method cannot revoke the Connection's private
child cleanup custody.

Close cuts over to permanent unusability before destruction. Connection close
finalizes every remaining Statement before calling `sqlite3_close_v2`, continues
cleanup after failures and preserves the first failure. Native resources retire
once even after failed destruction; no destructor retry is permitted. Every
close call answers a fresh already-terminal standard Future resolving to the
resource or failing with the same recorded Error identity. It cannot later be
cancelled. Admission and cleanup use the existing resource lifecycle facility.

Close never issues COMMIT. SQLite determines rollback of a connection with an
open transaction after its outstanding Statements have been finalized. Explicit
cleanup uses `ensure`; garbage collection timing supplies no new guarantee.

## Durability and recovery

SQLite is the only journal, transaction, locking and recovery engine. The
library changes no journal, synchronous or busy configuration automatically.
Applications may explicitly configure WAL and synchronous policy through normal
prepared SQL and inspect SQLite's reported result. No WAL, SHM or journal is
implemented or independently managed by Protos.

Successful COMMIT is SQLite's acknowledgment under its selected configuration.
Persistence and failure guarantees depend on SQLite, its VFS, OS/filesystem and
storage. A failed/uncertain commit must not be reported as successful or assumed
rolled back. Closing does not add an implicit successful commit or sync.

Acceptance includes independent Protos OS-process reopen with exact typed
values, real read-only/open-mode failures, explicit commit/rollback and failed
commit state, rollback-journal and WAL lock contention, active-Statement close,
and recovery after synchronized termination of an uncommitted writer. Such a
termination test demonstrates process recovery, not physical power-loss safety.
Platform absence is UNSUPPORTED; wrong data, timeouts, failed cleanup, failed
child startup/termination and harness exceptions are FAIL.
