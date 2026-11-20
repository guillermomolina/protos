# PLAT074-D0 — Experimental isolated Protos SQLite helper

> **D0 is not a sandbox.** The helper runs an authorized C library (SQLite)
> in its own OS process, but no mandatory reduction of that process's
> filesystem authority has been implemented or demonstrated. An independent
> process can still reach exterior files if its host grants it access. The
> JSON protocol and the operation allowlist are **not** substitutes for an OS
> sandbox: the absence of arbitrary SQL narrows the protocol surface, but does
> not show that the helper lacks exterior native authority. D0 does not
> establish D-2, D-10 or D-11. Alternative D is approved as a *direction*;
> conformance to the D-1–D-11 contract is **not** established.

Status: experimental, test-only. PLAT074 **OPEN**, D256 **pending**, LIB031
**BLOCKED**. Nothing here is public API, a normative protocol or a production
format. See [sqlite-durable-confinement.md](sqlite-durable-confinement.md) §23
for the decision record and the D0-T1–T8 evidence table.

## 1. Objective

Demonstrate, with real SQLite and the existing FFM provider, a Protos-first
helper that runs in a separate JVM (a separate OS process) and serves
durable-file requests over stdin/stdout. SQLite logic, the message protocol and
request validation are written in Protos. Java contributes only the existing
JUnit harness, which launches, relays, kills and observes processes.

## 2. Protos modules

All under `protos/tests/foreign-provider/isolated/`:

| File | Role | Native authority |
|---|---|---|
| `Protocol.protos` | JSON Lines encode/decode and admission validation (`std:json/json`) | none |
| `SqliteEngine.protos` | `openFile(path)` → closed operations over one SQLite connection (`std:ffi`, `ffi:sqlite3`) | SQLite through the provider |
| `helper.protos` | CLI FILE entry of the helper JVM; bootstrap, request loop, dispatch | via `SqliteEngine` |
| `Client.protos` | client logic: build requests, decode and correlate replies | none |
| `client-driver.protos` | CLI FILE entry that runs `Client.protos` for the JUnit harness | none |

`client-driver.protos` exists because the in-process JUnit fixture resolves
only `std:` modules, not the relative `./` imports `Client.protos` uses; the
real CLI's direct-file resolver does. It adds no capability and no Java.

Each SOURCE keeps its helpers in a private closure scope and binds only its
public closures at top level.

## 3. Protocol (JSON Lines)

Compact JSON, one message per UTF-8 line. Request:

```json
{"version":1,"id":1,"op":"PING","value":null}
```

Reply:

```json
{"version":1,"id":1,"status":"OK","value":0,"error":null}
{"version":1,"id":6,"status":"ERROR","value":null,"error":{"reason":"INVALID_VALUE"}}
```

Startup event (not a reply; no id):

```json
{"version":1,"event":"READY"}
```

Request admission rules (`Protocol.decodeRequest`):

- exactly the four members `version`, `id`, `op`, `value`; no extra or missing
  fields; no nested structure;
- `version` is exactly the JSON integer 1;
- `id` is a positive exact integer in the signed 64-bit domain;
- `op` is a non-empty String of at most 32 code units;
- `value` is `null` or an exact integer in the signed 64-bit domain;
- an exact integer is a JSON number with Integer coefficient and exponent zero;
  fractions (`1.5`, `1.0`) and out-of-domain values are rejected, never
  converted;
- at most 4096 UTF-8 octets before the line terminator.

The decoded result is a fresh frozen object of copied Protos values
(`admitted`, `id`, `operation`, `value`, `reason`); the parsed JSON tree is
never used as execution authority. A rejection is an admission result, never
an operation. It echoes the request `id` only when that `id` was itself valid;
otherwise the reply `id` is `null`.

Reply statuses: `OK`, `ERROR`, `UNCERTAIN`. The helper emits only `OK` and
`ERROR`. `UNCERTAIN` is a client-side outcome (`Client.connectionLost(id)`)
for a request whose reply was lost; it never asserts that SQL was rolled back.
Error reasons are a fixed set: `MALFORMED`, `OVERSIZED`, `INVALID_VERSION`,
`INVALID_ID`, `INVALID_OPERATION`, `INVALID_VALUE`, `NON_INCREASING_ID`,
`UNSUPPORTED_OPERATION`, `CLOSED`, `NO_TRANSACTION`, `SQLITE_FAILURE`,
`CONNECTION_LOST`. No reason carries addresses, private paths or Java
exceptions.

## 4. The helper process

The JUnit harness starts a child JVM running the real CLI:

```text
java ... -cp <test classpath> com.guillermomolina.protos.cli.ProtosCli \
    --foreign-provider-path <FFM provider JAR> \
    protos/tests/foreign-provider/isolated/helper.protos <absolute database path>
```

`helper.protos` receives the CLI's `process` binding as the initial FILE entry.
It requires exactly one application argument (`process.args()`), the database
path provisioned by the harness inside the fixture's temporary directory. It
builds the standard text reader/writers with `std:io/process_streams`, opens
the connection through `SqliteEngine.openFile(path)`, writes `READY` and then
serves one request at a time in its single RootActor. No message can name or
change the path.

stdout carries only structured protocol lines, each confirmed with
`writeLine(...).value()` and `flush().value()`. Diagnostics and test events go
to stderr. The helper never uses `print`.

## 5. FFM provider

The helper reuses the existing distributable `FfmForeignProvider` JAR built by
the B4/B5 fixture, authorized explicitly through the CLI option
`--foreign-provider-path`. The provider loads `libsqlite3.so.0` by its fixed
loader name. Neither the provider nor `std:sqlite` was changed.

## 6. Protos process versus OS process

A Protos `Process` is a language execution domain. D0 needs an *OS* process:
the helper is a separate JVM with its own PID and address space, launched by
the Java test harness. Protos has no public OS-process creation API, so the
client logic (`Client.protos`) does not launch anything, and no fake Process
object or new host capability was introduced.

## 7. SQLite custody

`SqliteEngine.protos` restates the B4 declarations (`sqlite3_open_v2`,
`sqlite3_prepare_v2`, `sqlite3_step`, `sqlite3_bind_int64`,
`sqlite3_column_int64`, `sqlite3_get_autocommit`) with owned
`sqlite3_close_v2`/`sqlite3_finalize` destructors:

- Connection and Statement values stay inside the private scope; no native
  address or handle is exported.
- Every statement is finalized in an `ensure`; scalar results are copied
  before finalization. A finalize failure after a successful step is
  signalled; after a failed step the original Error is preserved.
- `close()` retires guest custody before the native destructor runs:
  irreversible, idempotent, never a double release; a failed destructor result
  is signalled, not hidden.
- No reopen path exists; no custom VFS, journal or JDBC.

## 8. Operation model, no arbitrary SQL

The helper dispatches only `PING`, `INIT`, `BEGIN`, `INSERT`, `COMMIT`,
`ROLLBACK`, `COUNT`, `SUM`, `INTEGRITY`, `CLOSE`, `STOP` and the test-only
`COMMIT_HOLD_ACK`. Every other operation (`OPEN`, `ATTACH`, `VACUUM`, `EXEC`,
`EVAL`, `LOAD`, `IMPORT`, …) is `UNSUPPORTED_OPERATION` without touching
SQLite. SQL is fixed in `SqliteEngine.protos`:

```sql
CREATE TABLE IF NOT EXISTS entries(value INTEGER NOT NULL)
BEGIN IMMEDIATE
INSERT INTO entries(value) VALUES (?)   -- sqlite3_bind_int64
COMMIT / ROLLBACK
SELECT count(*) FROM entries
SELECT coalesce(sum(value), 0) FROM entries
SELECT count(*) FROM pragma_integrity_check WHERE integrity_check='ok'
```

`INSERT` requires an Integer value; every other operation requires `null`.
No symbol, library, path, SQL or Protos code is accepted from the channel.

## 9. The 4096-octet limit

Lines are read with `readLine(4096)`. A longer line fails the reader
permanently (TEXT_IO §16.1). The helper then writes `FRAMING_REJECTED` to
stderr, sends no reply, closes the connection and exits with a failure status:
framing errors are fail-closed, never partially executed.

## 10. Operation identity

Each admitted `id` must be strictly greater than the last admitted one; a
duplicate or decreasing `id` is `NON_INCREASING_ID` and is not dispatched.
Replies are correlated by `Client.requireMatchingResponse(id, text)`, which
refuses replies for any other request and uncorrelated (`id: null`) replies.
This is per-helper identity only; there is no durable deduplication across
restarts.

## 11. Crash recovery

Killing the helper with SIGKILL during an uncommitted transaction leaves only
the committed state after the next open (D0-T6): SQLite's own journal
recovery, not a Protos journal, provides this.

## 12. Uncertain COMMIT

`COMMIT_HOLD_ACK` (test-only) requires an active transaction, performs a real
COMMIT, writes `COMMITTED_BEFORE_ACK` to stderr and withholds `OK` until a
`CONTINUE_ACK` request arrives; waiting executes no SQL. D0-T7 kills the
helper inside that window: the client receives no reply and records
`UNCERTAIN`, yet the next helper sees the committed row. EOF therefore never
implies rollback. D0 demonstrates the window only; it does not provide a
reconciliation or deduplication API, and the event does not claim power-loss
durability.

## 13. Process lifecycle

- `CLOSE` closes and revokes the connection; later database operations answer
  `CLOSED`; `CLOSE` again is a no-op.
- `STOP` closes the connection, replies `OK` and ends the loop.
- EOF on stdin ends admission and closes the connection.
- A close failure is irreversible: admission ends and the process fails after
  the cleanup attempt.
- The harness bounds every wait, drains stdout and stderr on separate threads,
  and kills and reaps every child in a `finally` block.

## 14. Known limits

- Not a sandbox; no filesystem confinement of the helper (D-2 not met).
- No forced cleanup or revocation guarantees beyond process exit (D-10).
- No Landlock or other restriction of the helper, so D-11 is untested.
- Launch exists only in the Java test harness; no Protos subprocess API.
- One connection, one Actor, sequential requests; Integer values only.
- No reconciliation of uncertain commits; no durable request identity.
- Linux x86-64 with the FFM provider only. D0-T1–T8 passed in a focal run
  (owner-reported); see the §23 evidence table.
