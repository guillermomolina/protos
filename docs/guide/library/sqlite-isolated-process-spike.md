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
| `DurableProtocol.protos` | PLAT074-D2 protocol version 2 with durable identity (§17) | none |
| `durable-helper.protos` | PLAT074-D2 CLI FILE entry of the durable helper JVM (§17) | via `SqliteEngine` |
| `durable-client-driver.protos` | PLAT074-D2 client logic: correlation, local `UNCERTAIN`, reconciliation (§17) | none |

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

## 15. PLAT074-D1 — Pre-exec filesystem confinement of the helper

> **D1 is experimental filesystem confinement, not a complete sandbox.** It
> restricts which paths the helper JVM (and the SQLite C code inside it) can
> open. It does not restrict system calls, network access, signals, memory, CPU,
> or what arbitrary native code can do with descriptors it already holds. D1-T1–T12
> passed in one owner-reported focal run (prototype evidence only). D1 does not mark any D-1–D-11 item
> implemented.

### 15.1 Mechanism

`protos/tests/foreign-provider/isolated/native/sandbox-exec.c` is a test-only
C launcher. The JUnit harness compiles it into its temporary directory with the
same compiler lookup as `ffi_test.c`. It is not a provider, not part of any
distribution, and contains no SQLite or protocol logic. It follows the
documented Linux procedure
(<https://www.kernel.org/doc/html/latest/userspace-api/landlock.html>), written
independently of `samples/landlock/sandboxer.c`:

1. validate every policy path (absolute, existing, canonical; no `/` write
   root; write roots independent of each other and never containing a runtime
   path; the JVM executable inside an execute grant);
2. query the Landlock ABI and require at least ABI 3 (truncation);
3. `landlock_create_ruleset` handling every filesystem right the kernel knows:
   `EXECUTE`, `WRITE_FILE`, `READ_FILE`, `READ_DIR`, `REMOVE_DIR`,
   `REMOVE_FILE`, `MAKE_CHAR`, `MAKE_DIR`, `MAKE_REG`, `MAKE_SOCK`,
   `MAKE_FIFO`, `MAKE_BLOCK`, `MAKE_SYM`, `REFER`, `TRUNCATE`, plus
   `IOCTL_DEV` from ABI 5;
4. one `landlock_add_rule` (path beneath) per grant, each through an
   `O_PATH|O_CLOEXEC` descriptor that is closed immediately;
5. `prctl(PR_SET_NO_NEW_PRIVS, 1)`;
6. `landlock_restrict_self`;
7. close the ruleset and every inherited descriptor above 2 (`close_range`);
8. write the diagnostic `LANDLOCK_APPLIED` to stderr;
9. `execve` the JVM with only the environment the harness provided.

The process is still single-threaded at step 6, before any JVM exists.
Landlock domains survive `execve` and are inherited by every thread and child
the JVM later creates (GraalVM compiler threads, Actor carriers, FFM
downcalls). `LANDLOCK_APPLIED` is a diagnostic only. The evidence is a real
SQLite open that is allowed or denied, compared with an unrestricted control.

### 15.2 Grants

| Grant | Rights | Paths (all chosen by the harness, none from IPC) |
|---|---|---|
| execute + read | `READ_FILE`, `READ_DIR`, `EXECUTE` | `java.home`; the ELF interpreter |
| read | `READ_FILE`, `READ_DIR` | `/usr/lib64`, `/usr/lib` (system and `libsqlite3.so.0`); each test class-path entry; the FFM provider JAR; `$PROTOS_HOME/protos/lib`; `protos/tests/foreign-provider/isolated`; `/etc/ld.so.cache`, `/etc/localtime`, `/etc/nsswitch.conf`, `/etc/passwd`, `/etc/group`; `/proc`; `/sys`; `/dev/urandom`, `/dev/random` |
| device read/write | `READ_FILE`, `WRITE_FILE` (+ `IOCTL_DEV`) | `/dev/null` |
| SQLite directory | `READ_FILE`, `READ_DIR`, `WRITE_FILE`, `TRUNCATE`, `MAKE_REG`, `REMOVE_FILE` | one private directory per test |
| private temporary | SQLite rights + `MAKE_DIR`, `REMOVE_DIR`, `REFER` | a fresh directory per process |

The repository is never writable, and neither is the whole repository, `$HOME`
or `/tmp` readable. The JVM is started with `-XX:-UsePerfData`,
`-Djava.io.tmpdir`, `-Duser.home` and `-Dpolyglot.engine.userResourceCache`
pointing into its private temporary directory. Its environment is exactly
`PROTOS_HOME`, `HOME`, `TMPDIR`, `LANG`, `PATH`. The Protos client is not
launched through the launcher and stays unrestricted.

### 15.3 Fail-closed behaviour

The launcher exits non-zero without starting Protos when arguments or policy
paths are invalid (64), Landlock is missing or its ABI is insufficient (69), a
rule, `no_new_privs` or the restriction fails (70), or `execve` fails (71).
Each failure writes `SANDBOX_REFUSED <reason>`. There is no degraded or
unconfined mode. `--require-abi` can only raise the minimum, never lower it
below 3. Before the D1 tests, the harness runs `sandbox-exec --abi-only`; exit
69 makes every D1 test **UNSUPPORTED** (a JUnit assumption, never a pass).

### 15.4 Tests

| Test | Claim exercised | Status |
|---|---|---|
| D1-T1 `d1T1LandlockIsInstalledBeforeProtosBoot` | `LANDLOCK_APPLIED` then Protos READY; same PID now runs the JVM (exec, not fork); distinct from the client; PING; clean exit | PASS (focal) |
| D1-T2 `d1T2ConfinedSqliteCreatesAndReopensDurableDatabase` | confined INIT/BEGIN/INSERT/COMMIT/COUNT/SUM/INTEGRITY, reopen by another confined JVM, no leftover journal | PASS (focal) |
| D1-T3 `d1T3NativeSqliteCannotOpenExteriorDatabase` | unrestricted probe opens the exterior; confined probe opens its grant and is denied the exterior; exterior unchanged | PASS (focal) |
| D1-T4 `d1T4SeparateHelpersCannotWidenEachOthersGrants` | A and B each denied the other's database (probe and helper boot); controls open both; both keep serving | PASS (focal) |
| D1-T5 `d1T5UnrestrictedClientDoesNotWidenHelperAuthority` | client-profile process opens the control; protocol cannot carry the path; helper grant denied the control | PASS (focal) |
| D1-T6 `d1T6UnauthorizedSqlOperationsRemainRejectedUnderConfinement` | OPEN/EXEC/ATTACH/VACUUM/LOAD/IMPORT rejected; 4096 admitted, 4097 fail-closed; no exterior created or modified | PASS (focal) |
| D1-T7 `d1T7UncommittedTransactionRollsBackAfterSandboxedHelperKill` | D0-T6 under confinement | PASS (focal) |
| D1-T8 `d1T8CommittedButUnacknowledgedTransactionRemainsUncertain` | D0-T7 under confinement; UNCERTAIN; no retry | PASS (focal) |
| D1-T9 `d1T9UnsupportedLandlockProfileRefusesExecution` | impossible ABI: `SANDBOX_REFUSED`, exit 69, no READY, no SQLite file, no retry | PASS (focal) |
| D1-T10 `d1T10HardlinkDoesNotProvideExclusiveDatabaseIdentity` | direct exterior path denied; a pre-existing hardlink inside the grant may open the same inode (reported as a known limitation) | PASS (focal) |
| D1-T11 `d1T11NoResourceOrProcessSurvivesNormalClose` | CLOSE/STOP, exit 0, no descendants or survivors, no journal, reopen | PASS (focal) |
| D1-T12 `d1T12ConfinedHelperPreservesD0ProtocolAndActorSemantics` | D0 admission, id ordering, transaction, CLOSE and post-close rejections unchanged | PASS (focal) |

Owner-reported focal run on 20 November 2026 (`ProtosI144FfmFixtureAbiTest#d1*`): 12/12 PASS, 0 failures, 0 errors, 0 skipped, 63.54 s, Landlock ABI 11. Stderr confirms `LANDLOCK_APPLIED` for every confined JVM, the D1-T4 cross-grant helper failing in `SqliteEngine.openFile`, the D1-T6 fail-closed framing path and the D1-T9 refusal (`SANDBOX_REFUSED LANDLOCK_ABI_INSUFFICIENT abi=11 required=1000000`). The D1-T10 hardlink outcome (alias allowed or denied) was published through `TestReporter` and was not captured in the returned output. This is prototype evidence only.

`isolation-probe.protos` is the probe used by D1-T3/T4/T5/T10. It is a CLI FILE
entry that takes both database paths from `process.args()` and uses the same
`SqliteEngine.openFile` and FFM provider as the helper. It runs only the
read-only `integrity_check` query.

### 15.5 Open limits

- **Path, not identity.** Landlock authorizes path hierarchies. A hardlink
  that already exists inside the grant reaches an exterior inode (B6-T4,
  D1-T10). A bind mount would do the same. After restriction, `REFER` and the
  `MAKE_*` rights stop the helper from creating new links outside the grant.
- **Pre-opened descriptors.** Landlock checks `open`, not descriptors already
  held. The launcher closes every inherited descriptor above 2. This relies
  on the launcher, not on the kernel.
- **Broad read surfaces.** `/proc`, `/sys` and the system library directories
  are readable, so files there can be read even though nothing outside the
  grants is writable.
- **Not all native capabilities.** Network, IPC, signals, `ptrace` (limited
  only by `no_new_privs` and Yama), resource exhaustion and memory corruption
  inside the helper are not confined. Read-only access to `/proc` exposes
  metadata of other same-user processes.
- **Platform.** Linux with Landlock ABI ≥ 3, x86-64 FFM fixture only. The
  read list assumes the FHS layout of the test host; a missing runtime path
  fails the launch and never widens the grant.
- **D-10.** Normal close only. Client-crash cleanup and revocation are not
  tested.
- **D1-T10 outcome** not captured; a rerun that keeps the surefire report would record it.

## 16. PLAT074-D1-R1 — Extended Landlock restrictions

D1-R1 corrects and widens the D1 launcher profile. It reuses the same
launcher (`isolated/native/sandbox-exec.c`) and the same sequence: validation,
ruleset creation, rules, `no_new_privs`, `landlock_restrict_self`, descriptor
closing, `execve`. It adds no fork. The Protos executable, the helper,
`SqliteEngine.protos`, the protocol and the FFM provider are unchanged. All
SQLite and protocol logic stays in Protos. The new C code is test fixtures
only.

### 16.1 Correction: `FS_RESOLVE_UNIX`

The D1 launcher said it handled every known filesystem right, but its list
ended at `LANDLOCK_ACCESS_FS_IOCTL_DEV`. It left out
`LANDLOCK_ACCESS_FS_RESOLVE_UNIX` (ABI 9, bit 16). D1-R1 adds it to the
handled mask and grants it to no path. Connecting to a pathname UNIX socket is
therefore denied everywhere, even beneath a readable `--ro` grant. Being able
to read the directory that holds a socket does not authorize connecting to it.

### 16.2 Network, UNIX IPC and signals

| Mechanism | Field | Bits | ABI | Rule granted |
|---|---|---|---|---|
| TCP bind / connect | `handled_access_net` | `BIND_TCP` (0), `CONNECT_TCP` (1) | 4 | none |
| UDP bind / connect-send | `handled_access_net` | `BIND_UDP` (2), `CONNECT_SEND_UDP` (3) | 10 | none |
| Abstract UNIX sockets | `scoped` | `SCOPE_ABSTRACT_UNIX_SOCKET` (0) | 6 | — |
| Signals | `scoped` | `SCOPE_SIGNAL` (1) | 6 | — |
| Pathname UNIX sockets | `handled_access_fs` | `FS_RESOLVE_UNIX` (16) | 9 | none |

No port rule exists, so every TCP and UDP operation covered by these rights is
denied. There is no loopback exception, and IPC cannot authorize a port. The
scopes stop the helper from connecting to abstract sockets or signalling
processes outside its own domain. The unrestricted parent can still signal and
kill the helper. The signal scope is not protection against every form of IPC.

The ruleset attribute is the ABI 10 layout: `handled_access_fs`,
`handled_access_net`, `scoped`, `quiet_access_fs`, `quiet_access_net`,
`quiet_scoped`, all `uint64_t`. The quiet fields are always zero, so audit is
never silenced. `sizeof` of exactly this structure goes to
`landlock_create_ruleset`. The launcher prints the installed masks as the
diagnostic
`LANDLOCK_PROFILE abi=10 handled_fs=0x1ffff handled_net=0xf scoped=0x3`.

### 16.3 ABI 10 profile

`PROFILE_ABI` (= `MINIMUM_ABI`) is 10, because the profile needs ABI 3
TRUNCATE, ABI 4 TCP, ABI 5 IOCTL_DEV, ABI 6 scopes, ABI 9 RESOLVE_UNIX and
ABI 10 UDP. Below ABI 10 the launcher prints
`SANDBOX_REFUSED LANDLOCK_ABI_INSUFFICIENT` and exits 69. A weaker profile is
never installed.

- `--require-abi` below 10 is refused with `ABI_BELOW_PROFILE_FLOOR` (exit 64).
- A requirement above 10 asks for guarantees the launcher does not implement.
  It is refused with `PROFILE_DOES_NOT_COVER_ABI` (exit 69), or with
  `LANDLOCK_ABI_INSUFFICIENT` if the kernel is older than the requirement.
- On a newer kernel (the owner's host reports ABI 11) the profile is still the
  ABI 10 one, and the launcher prints `LANDLOCK_UNREVIEWED_ABI abi=N profile=10`.
  Rights added after ABI 10 are not handled and are not claimed. A newer ABI
  never grants anything implicitly.
- `--abi-only` checks the same requirement.

### 16.4 Descriptors

`close_range(3, ~0U, 0)` must succeed. Any failure, including `ENOSYS`, prints
`SANDBOX_REFUSED CLOSE_RANGE_FAILED`, exits non-zero and never runs the
executable. The per-descriptor `close` fallback from D1 is removed.
Descriptors 0, 1 and 2 stay open. They are the harness's explicit channels and
they still carry communication authority.

### 16.5 Environment and paths

The environment is explicit and never inherited. The only allowed names are
`PROTOS_HOME`, `HOME`, `TMPDIR`, `LANG` and `PATH`, each at most once, with a
non-empty value and no control characters. `HOME` and `TMPDIR` must equal the
private `--tmp` root. The launcher refuses with exit 64:

- the named JVM/loader variables (`JAVA_TOOL_OPTIONS`, `_JAVA_OPTIONS`,
  `JDK_JAVA_OPTIONS`, `LD_PRELOAD`, `LD_LIBRARY_PATH`, `LD_AUDIT`,
  `GCONV_PATH`) and any other `LD_` name, with `ENV_FORBIDDEN`;
- an unknown name (`ENV_UNKNOWN`);
- a duplicate (`ENV_DUPLICATE`);
- a missing `=` (`ENV_MISSING_SEPARATOR`);
- an empty name (`ENV_EMPTY_NAME`);
- an invalid value (`ENV_INVALID_VALUE`);
- a private-root mismatch (`ENV_NOT_PRIVATE_TMP`).

There is no general environment configuration.

`/` is refused as an `--rx` or `--ro` root, as it already was for the write
roots. The executable after `--` is either the JVM or a native probe. It must
be absolute, canonical, a regular executable file beneath an `--rx` grant and
outside both write roots. It is exec'd directly, with no shell and no lookup.
Paths are checked by name (`realpath`, then `open(O_PATH)`) before the ruleset
exists. That check does not exclude every race with concurrent host namespace
changes, so the provisioning host stays trusted.

### 16.6 Tests

`isolated/native/authority-probe.c` is a fixture that performs one OS
operation per mode: `tcp-connect`, `udp-send`, `unix-path-connect`,
`unix-abstract-listen`, `unix-abstract-connect`, `signal-check`,
`preopen-exec` and `fd-check`. Every restricted claim has an unrestricted
control of the same operation, and that control must succeed first. A
restricted result counts only if two things hold: the launcher printed the full
profile and `LANDLOCK_APPLIED`, and the probe reported `*_DENIED`, meaning
`EACCES` or `EPERM`. `ECONNREFUSED`, `ENOENT`, `ESRCH`, timeouts, a missing
listener or a lost datagram are `*_OTHER_ERROR` / `*_NO_TARGET` and fail the
test. The JVM and the probe share one policy builder (`d1Policy`). The probe
directory is the probe's only extra `--rx`.

| Test | Claim | Result |
|---|---|---|
| D1-R1-T1 `d1R1T1FullSupportedLandlockProfileIsApplied` | ABI ≥ 10; profile masks printed before `LANDLOCK_APPLIED`; helper serves SQLite; probe opens its grant and is denied the exterior | PASS (focal, owner-observed 2026-11-20) |
| D1-R1-T2 `d1R1T2TcpConnectOutsideDomainIsDenied` | control connects to a JUnit loopback listener; restricted `TCP_DENIED`; no restricted connection queued | PASS (focal, owner-observed 2026-11-20) |
| D1-R1-T3 `d1R1T3UdpSendOutsideDomainIsDenied` | control datagram sent and received; restricted `UDP_DENIED`; nothing received | PASS (focal, owner-observed 2026-11-20) |
| D1-R1-T4 `d1R1T4PathnameUnixSocketOutsideDomainIsDenied` | socket in an `--ro` directory, visible to `stat`; control connects; restricted `UNIX_PATH_DENIED` | PASS (focal, owner-observed 2026-11-20) |
| D1-R1-T5 `d1R1T5AbstractUnixSocketOutsideDomainIsDenied` | listener READY; control accepted; restricted `UNIX_ABSTRACT_DENIED`; listener total exactly 1 | PASS (focal, owner-observed 2026-11-20) |
| D1-R1-T6 `d1R1T6SignalsCannotEscapeTheHelperDomain` | `kill(JUnit PID, 0)` allowed unrestricted, `SIGNAL_DENIED` restricted; ESRCH never accepted | PASS (focal, owner-observed 2026-11-20) |
| D1-R1-T7 `d1R1T7AuthorizedProtosSqliteSurvivesExpandedRestrictions` | INIT…STOP and reopen in another confined JVM; values and integrity unchanged | PASS (focal, owner-observed 2026-11-20) |
| D1-R1-T8 `d1R1T8InheritedExteriorFileDescriptorIsClosedBeforeExec` | inheritable fd 9 readable after exec without launcher; `FD_CLOSED` after launcher | PASS (focal, owner-observed 2026-11-20) |
| D1-R1-T9 `d1R1T9UnsafeEnvironmentVariablesAreRejected` | every forbidden/unknown/duplicate/malformed/mismatched entry refused before any JVM; exact set boots | PASS (focal, owner-observed 2026-11-20) |
| D1-R1-T10 `d1R1T10HardlinkOutcomeIsAssertedAndRecorded` | asserts: interior hardlink alias opens, exterior name denied | PASS (focal, owner-observed 2026-11-20) |
| D1-R1-T11 `d1R1T11UnsupportedSecurityProfileRefusesWithoutFallback` | `--require-abi 9` and a requirement above 10 refused; no SQLite file; no retry | PASS (focal, owner-observed 2026-11-20) |
| D1-R1-T12 `d1R1T12ClientDeathDoesNotYetProvideHelperRevocation` | negative control: helper survives forced client death; D-10 unresolved; JUnit kills it | PASS (focal, owner-observed 2026-11-20) |
| D1-R1-T13 `d1R1T13ParentCanTerminateConfinedHelper` | harness SIGKILLs the confined helper; committed state and integrity intact on reopen | PASS (focal, owner-observed 2026-11-20) |

D1-R1-T10 asserts the path-based outcome: the alias inside the grant opens the
exterior inode. If the kernel denies it, the test fails, and that result has
to be reported, not absorbed. D1-T10 is kept unchanged.

Owner-observed focal evidence (20 November 2026):

- Selector `ProtosI144FfmFixtureAbiTest#d1*`: 25/25 PASS (12 D1 tests and 13
  D1-R1 tests), 0 failures, 0 errors, 0 skipped, 86.83 s.
- `mvn -o test-compile`: BUILD SUCCESS. `git diff --check`: clean.
- The ABI 10 profile was installed on a kernel reporting Landlock ABI 11
  (`handled_fs=0x1ffff`, `handled_net=0xf`, `scoped=0x3`).
- D1-R1-T10: the interior hardlink was allowed to reach the exterior inode.
  This is a real identity limitation of path-based authorization, not a
  confinement success.
- D1-R1-T12 (negative control): the helper survived the forced death of its
  client because the harness still held its pipes. D-10 remains pending.

This is a focal run. The complete JUnit class was not run as part of this
evidence.

### 16.7 Still not protected

- Landlock does not make the native C code memory-safe.
- The helper keeps its inherited standard channels (0, 1, 2).
- `/proc` and `/sys` are still broad read surfaces.
- Hardlinks and bind mounts need an identity-provisioning policy.
- Name validation before Landlock does not by itself protect against every
  namespace race.
- A COMMIT without an acknowledgement is still uncertain. There is no automatic
  reconciliation. Durable operation identity is only an experimental D2
  fixture (§17), not yet run.
- The helper is still launched by the Java harness. There is no public OS
  process API and no approved client/helper supervisor. D-10 is still pending.
- `ptrace` (only `no_new_privs`/Yama), resource exhaustion, and syscalls
  outside the Landlock-covered families are not confined.

This profile is not a general container and not complete syscall confinement.

## 17. PLAT074-D2 — Durable operation identity and reconciliation

> **Experimental, test-only, prototype evidence.** D2 adds no public API, no normative
> protocol and no production guarantee. It targets at-most-once **effect** per
> durable key, within one SQLite database and under the SQLite conditions
> verified here. It does **not** claim exactly-once delivery.

### 17.1 Transport id versus durable identity

Protocol version 2 (`isolated/DurableProtocol.protos`) has three positive
signed 64-bit identifiers:

- `id` is transport correlation. It pairs one request with one reply inside one
  helper process, must increase strictly within that process, and restarts at
  any value in a new helper (D2-T13).
- `clientId` and `operationId` together form the durable operation identity.
  They are stored in SQLite. A PID, an Actor, an FFM session or the transport
  id is never used as a durable key.

Version 1 (`Protocol.protos`, `helper.protos`) is unchanged.

### 17.2 Receipts and the single transaction

`SqliteEngine.protos` adds this fixed schema next to the existing `entries`
table, in the same database:

```sql
CREATE TABLE IF NOT EXISTS operation_receipts (
    client_id INTEGER NOT NULL,
    operation_id INTEGER NOT NULL,
    value INTEGER NOT NULL,
    PRIMARY KEY (client_id, operation_id)
)
```

`applyOnce(clientId, operationId, value, beforeCommit, afterCommit)`:

1. validates both identifiers (positive, exact, signed 64-bit) and the value
   (exact signed 64-bit Integer, no doubles);
2. runs `BEGIN IMMEDIATE`;
3. reads `SELECT value FROM operation_receipts WHERE client_id = ? AND
   operation_id = ?`;
4. if a receipt exists, ends the read-only transaction with `ROLLBACK` and
   answers `REPLAYED` (same value) or `CONFLICT` (different value), with the
   persisted value;
5. otherwise inserts `INSERT INTO entries(value) VALUES (?)` and `INSERT INTO
   operation_receipts(client_id, operation_id, value) VALUES (?, ?, ?)`, runs
   the test-only `beforeCommit` hook, runs `COMMIT`, runs the test-only
   `afterCommit` hook and answers `APPLIED`.

The entry and its receipt are committed by one `COMMIT`. There is no second
commit, no Protos `Map` of applied operations and no private journal. Every
value is bound with `sqlite3_bind_int64` and read with `sqlite3_column_int64`;
no value is interpolated into SQL.

`lookupReceipt` answers `null` when no receipt exists and a copied Integer
otherwise, so a missing key is not confused with a stored `0` (D2-T9).

Limitation: the `entries` schema is fixed by D0 and carries no key column. The
pairing of entries and receipts is checked by equal counts and sums together
with per-key `LOOKUP`, not by a foreign key.

### 17.3 Outcomes

| Status | Meaning |
|---|---|
| `APPLIED` | This request committed the entry and its receipt. |
| `REPLAYED` | A receipt with the same key and value already existed. Nothing was written. The value is the persisted one. |
| `CONFLICT` | A receipt with the same key but a different value exists. Nothing was written. The value is the persisted one. |
| `FOUND` / `ABSENT` | `LOOKUP` found the persisted value, or no receipt exists. |
| `OK` | `PING`, `INIT`, `COUNT`, `RECEIPTS`, `SUM`, `INTEGRITY`, `CLOSE`, `STOP`. |
| `ERROR` | A fixed reason; never a Java exception, a path or a native address. |

`UNCERTAIN` is not a helper status. The client derives it locally when the
channel ends before a correlated reply.

### 17.4 Failures and the uncertain COMMIT

- **Before COMMIT:** the engine tries `ROLLBACK` only if a transaction is
  active, and never reports `APPLIED`. If the rollback fails, the session is
  `BROKEN` and the helper ends admission.
- **`BEGIN IMMEDIATE` meets `SQLITE_BUSY`:** no transaction began and nothing
  was written. The reply is `ERROR BUSY`. A bounded native wait
  (`sqlite3_busy_timeout`, 250 ms) precedes it. BUSY is never retried or turned
  into success.
- **COMMIT attempted without an established result:** the session becomes
  `RECONCILE_REQUIRED`. No rollback is claimed, nothing is retried, the session
  writes nothing more, and the helper answers `ERROR RECONCILE_REQUIRED` and
  ends admission. Reconciliation must happen from a new session.
- **COMMIT succeeded but the process died before the acknowledgement:** the
  entry and the receipt are committed together. A later `LOOKUP` finds the
  receipt, and repeating the same key and value answers `REPLAYED` (D2-T8).

### 17.5 Reconciliation and retries

`isolated/durable-client-driver.protos` keeps the durable key apart from the
transport id. A lost reply becomes local `UNCERTAIN`. `RECONCILE` builds a
`LOOKUP` for the same key under a new transport id for a restarted helper.
`RESOLVE` turns its reply into `COMMITTED` (same value) or `CONFLICT` (different
value). Any other reply, including `ERROR` or EOF, leaves the key `UNCERTAIN`.

Correction (PLAT074-D3, §18.6): as first written, `RESOLVE` also turned
`ABSENT` into `NOT_APPLIED`. That conclusion holds only when the helper that
ran the original operation can no longer COMMIT it. Since D3, `ABSENT` gives
`ABSENT_OBSERVED`, and `NOT_APPLIED` additionally needs the harness's explicit
`SOURCE_TERMINATED` evidence. D2-T7 now supplies that evidence, which its
scenario already checked (`d0Kill` waits for exit status 137).

Retry model: retrying the same key and value is safe, because it answers
`APPLIED` exactly once and `REPLAYED` afterwards. Retrying a key with a
different value answers `CONFLICT` and never overwrites the receipt. Nothing
retries COMMIT automatically.

### 17.6 Test-only hooks

`HOLD_BEFORE_COMMIT` writes `DURABLE_PRECOMMIT` to stderr with the entry and
receipt inserted inside the open transaction. `HOLD_AFTER_COMMIT` writes
`DURABLE_COMMITTED_BEFORE_ACK` after a successful COMMIT. Both then wait for
one `CONTINUE` with a larger transport id. Waiting runs no SQL and has no
sleep. A missing or invalid continuation sends no reply. The helper writes
`DURABLE_ACK_WITHHELD` and fails closed. Before COMMIT, the open transaction is
rolled back locally, and no reply claims that outcome. Both hooks use the same
`applyOnce` code path as `APPLY_ONCE`.

### 17.7 Scope and limits

- `clientId` is not authenticated. Any peer on the channel can claim any
  client. This is not yet production authority.
- Each helper session sets `PRAGMA synchronous=FULL` and checks that it reads
  back `2`. The journal is not disabled. The evidence comes from JVM process
  kills and restarts. Power loss and storage that breaks its own guarantees are
  not tested.
- Custody: one connection in the helper's RootActor, sequential requests,
  `CLOSE` irreversible, no Actors added. The native handle is never exported.
- Every D2 helper runs behind the D1-R1 launcher (ABI 10 profile, checked
  before READY). The harness, not the client, still owns the helper's lifetime,
  so D-10 remains pending.

### 17.8 Tests

| Test | Acceptance condition | Result |
|---|---|---|
| D2-T1 `d2T1DurableProtocolRejectsInvalidIdentityAndPayload` | Protos encoder/decoder accept exact vectors and the 4096-octet line; reject fractions, negatives, out-of-range, wrong types, missing/extra fields, unknown ops and uncorrelated or malformed replies | PASS |
| D2-T2 `d2T2ApplyOncePersistsEntryAndReceiptAtomically` | `APPLIED 42`; COUNT=1, RECEIPTS=1, SUM=42, `FOUND 42`; identical after reopen in another confined JVM | PASS |
| D2-T3 `d2T3SameKeyReplayAfterRestartDoesNotDuplicate` | after two restarts the same key and value answer `REPLAYED 42`; counts unchanged | PASS |
| D2-T4 `d2T4ConflictingReplayIsRejectedWithoutWrite` | a different value answers `CONFLICT 42`, before and after restart; receipt and entry unchanged | PASS |
| D2-T5 `d2T5DifferentClientsMayUseSameOperationNumber` | (101,7001) and (202,7001) are both `APPLIED`; two entries and two receipts | PASS |
| D2-T6 `d2T6SameClientDistinctOperationIdsRemainIndependent` | only the repeated key answers `REPLAYED`; four entries, sum 15 | PASS |
| D2-T7 `d2T7KilledBeforeCommitLeavesNoEntryOrReceipt` | SIGKILL at `DURABLE_PRECOMMIT`; reconciliation answers `NOT_APPLIED`; zero rows; retry `APPLIED` once, then `REPLAYED` | PASS |
| D2-T8 `d2T8KilledAfterCommitBeforeAckReconcilesWithoutDuplicate` | SIGKILL at `DURABLE_COMMITTED_BEFORE_ACK`; client `UNCERTAIN`; reconciliation `COMMITTED 42`; retry `REPLAYED`; COUNT=1 | PASS |
| D2-T9 `d2T9MissingReceiptCannotBeMistakenForZeroValue` | `ABSENT` before, `FOUND 0` after applying 0, and after reopen | PASS |
| D2-T10 `d2T10TwoConfinedHelpersCannotApplyTheSameKeyTwice` | A holds the write lock (event); B answers `ERROR BUSY`; A `APPLIED`; B `REPLAYED`; one entry, one receipt | PASS |
| D2-T11 `d2T11UnauthorizedProtocolCannotReachSqlite` | OPEN/EXEC/ATTACH/LOAD, library names, paths, SQL and Protos code refused; v1 lines refused; data unchanged; 4097 octets fail closed; no exterior file | PASS |
| D2-T12 `d2T12FailedOrInterruptedWriteNeverCreatesOrphanReceipt` | SIGKILL before COMMIT and an invalid continuation both leave entries and receipts equal (1/1, sum 7) | PASS |
| D2-T13 `d2T13TransportIdsAreNotDurableOperationIds` | several transport ids, one effect; a reused transport id is `NON_INCREASING_ID`; restarted ids keep the durable key | PASS |
| D2-T14 `d2T14DurableCloseRevokesConnectionCustody` | after `CLOSE` every database operation answers `CLOSED`; clean exit; no survivor; only `d2.db`; receipts recovered by another JVM | PASS |
| D2-T15 `d2T15UncertainReplyNeverMeansConfirmedRollback` | EOF after COMMIT: no reply, empty reply `REJECTED`, key stays `UNCERTAIN` even after an `ERROR` lookup reply; then `COMMITTED 42` | PASS |
| D2-T16 `d2T16ConflictAfterUncertainCommitCannotOverwriteReceipt` | after a lost acknowledgement, value 99 answers `CONFLICT 42` across two restarts; counts and sum intact | PASS |

Observed result of the focal D2 run, reported by the human executor: **16/16
PASS**, zero failures, errors and skips, 104.3 seconds; Maven `test-compile`
BUILD SUCCESS; `git diff --check` clean; Landlock profile ABI 10 on a kernel
with ABI 11. The pre-COMMIT kill (D2-T7, T12), the post-COMMIT lost
acknowledgement (D2-T8, T15, T16) and reconciliation followed by `REPLAYED`
were all observed. This is prototype evidence, not a production guarantee.

Two gaps found in review after that run are corrected by D3 (§18): `ABSENT`
was treated as a final `NOT_APPLIED` without proof that the source helper could
no longer COMMIT, and READY was published without checking a durable identity
of the SQLite file, so a reconciliation against another database could produce
a false `ABSENT`.

State: **PLAT074 OPEN; D256 pending; LIB031 BLOCKED; D-10 pending; D2-T1–T16
PASS (focal, prototype).**

## 18. PLAT074-D3 — OS parent custody and verified reconciliation

> **Experimental, test-only, not run.** D3 adds no public API, no subprocess
> API, no production Java and no normative protocol. It tries one concrete form
> of OS custody of the helper. It does not authorize integrating that form with
> the Actor lifecycle.

### 18.1 Process tree

`isolated/native/owner-exec.c` is a small C fixture. It contains no SQL and no
protocol logic. It builds this tree:

```text
JUnit
  |
  +-- Protos client JVM            (owner-exec replaced by execve; same PID)
         |
         +-- sandbox-exec --execve--> Protos helper JVM
```

Sequence, all single-threaded until each final `execve`:

1. `owner-exec` creates two pipes (`O_CLOEXEC`) and calls `fork` once.
2. The child connects stdin to the client-to-helper pipe and stdout to the
   helper-to-client pipe, closes every original pipe descriptor, and executes
   `sandbox-exec` with `--expected-parent-pid <owner PID>` inserted as its
   first option.
3. `sandbox-exec` checks that `getppid()` equals that PID, calls
   `prctl(PR_SET_PDEATHSIG, SIGKILL)`, reads the setting back with
   `PR_GET_PDEATHSIG`, and checks `getppid()` again. Only then does it build the
   D1-R1 Landlock ruleset, set `no_new_privs`, restrict itself, run
   `close_range(3, ~0)`, check the parent and the signal once more, and execute
   the helper JVM.
4. The parent connects stdin to the helper-to-client pipe and stdout to the
   client-to-helper pipe, closes every original pipe descriptor, writes
   `OWNER_HELPER_PID <pid>` to stderr and executes the Protos client JVM.
   `execve` keeps the PID, so the client JVM is the helper's OS parent.

Any failure stops the process before an unprotected helper exists. A helper is
only ever started through `sandbox-exec` with the custody option. If the client
`execve` fails, the forked child is killed. The custody option is a launcher
argument chosen by the host that builds the tree. No Protos message can set it.
A parent mismatch or an invalid PID gives `SANDBOX_REFUSED
PARENT_OWNERSHIP_FAILED` (exit 72) before the JVM starts. There is no retry
without the option and no periodic PID check. The Landlock profile is exactly
D1-R1's: the option grants no filesystem, network, signal or IPC right.

### 18.2 Direct Protos-to-Protos IPC

`isolated/durable-session-client.protos` is the client, run as a FILE entry of
the real CLI. It imports `std:io/process_streams`, `std:json/json` and
`./DurableProtocol.protos`. It does not import FFI, open SQLite or start
processes. Its stdin carries exactly the helper's protocol lines, and its
stdout carries exactly its own requests. JUnit's stdin and stdout pipes are
replaced in both processes, so JUnit has no data channel and receives EOF on
stdout without a line (D3-T1, T2). Stderr of both processes is the JUnit pipe,
used only for bounded diagnostics and synchronization (`CLIENT_*`,
`DURABLE_*`, launcher lines).

The client reads READY and then DATABASE_IDENTITY, and compares the identity
with its own bootstrap arguments. On a mismatch it writes
`CLIENT_DATABASE_IDENTITY_MISMATCH` and ends without sending any operation.
Scenarios form a closed list given as a CLI argument: `NORMAL`,
`EXIT_AFTER_READY`, `HOLD_AFTER_READY`, `HOLD_BEFORE_COMMIT`,
`HOLD_AFTER_COMMIT` and `EXIT_AFTER_COMMIT`. `HOLD_AFTER_READY` is an addition
to the requested list: it keeps an idle helper alive so that the client can be
killed at a known point (D3-T1, T3, T4, T14). The durable key `(301, 9001)` and
the value `77` are fixed. EOF before a correlated reply is reported as
`CLIENT_CONNECTION_LOST UNCERTAIN`, never as a rollback.

### 18.3 Parent-death signal

`PR_SET_PDEATHSIG` ([PR_SET_PDEATHSIG(2const)](https://man7.org/linux/man-pages/man2/PR_SET_PDEATHSIG.2const.html))
is set in the child after `fork`. It survives `execve` of a binary that is not
set-user-ID or set-group-ID, and no credential change happens later. Linux
sends the signal when the **thread** that created the child ends. In this tree
that thread is `owner-exec`'s only thread. After `execve` it becomes the JVM
launcher's primordial thread, which waits for the Java main thread and lives
until the process exits. If it ended earlier, the helper would be killed early
(fail-closed). It would never be kept alive.

SIGKILL cannot be caught or ignored, and no handler is installed. When the
client JVM dies for any reason (SIGKILL, normal exit or a fatal error), the
kernel kills the helper. When the client exits normally, the helper may also
read EOF and exit on its own first. Either path ends it. D3-T3 separates the two
paths: the harness stops the helper with SIGSTOP, so it cannot run code and
cannot read EOF, and then kills the client. Only the parent-death SIGKILL can
then end the helper.

The signal comes from the kernel on behalf of the dying, unrestricted client.
The Landlock signal scope limits signals sent *by* the helper's domain, not
signals sent *to* it.

### 18.4 OS parent versus Actor

The owner is an OS process. Custody ends when that whole process ends. This is
**not** the revocation of an Actor, a Closure or a connection while the owning
JVM is still alive. Killing or closing one Protos Actor in the client does not
touch the helper. Ending the helper does not cancel a blocked native call
cooperatively: SIGKILL stops the process. SQLite's journal recovery, verified
in D0–D2, is what makes that safe for the database.

### 18.5 Observing termination

JUnit does not use `kill(pid, 0)`. A helper counts as running only while
`/proc/<pid>/stat` exists, its start time equals the one recorded when the PID
was first seen (this detects PID reuse), and its state is not `Z` (zombie) or
`X` (dead). A zombie executes no code. After the client dies, the helper is
reparented and reaped by the reaper of the environment. All waits have a
deadline and no step relies on sleeping for synchronization. Every tracked
helper is killed and checked at the end of each test.

### 18.6 Durable database identity

`SqliteEngine.protos` adds one table in the same database:

```sql
CREATE TABLE IF NOT EXISTS database_identity (
    singleton INTEGER PRIMARY KEY CHECK(singleton = 1),
    identity_hi INTEGER NOT NULL,
    identity_lo INTEGER NOT NULL
)
```

The identity is a pair of signed 64-bit Integers from the trusted helper
bootstrap. It never comes from IPC and is never a PID, a path, a connection, an
Actor or a transport id. `PRAGMA application_id` is not used.

- `provisionDatabaseIdentity(hi, lo)` runs inside `BEGIN IMMEDIATE`. It is
  refused if any schema object already exists. An existing identity is
  therefore never overwritten, a second row cannot be added, and a populated
  database without an identity is never adopted. If the provisioning COMMIT
  result is unknown, the session becomes `RECONCILE_REQUIRED`.
- `requireDatabaseIdentity(hi, lo)` is read-only. It requires exactly one
  `database_identity` table holding exactly one row whose two components are
  equal to the bootstrap pair.

`durable-helper.protos` D3 mode takes `PATH IDENTITY_HI IDENTITY_LO
[PROVISION]`. The identity texts must be canonical signed 64-bit Integers. The
optional fourth argument makes the initialization policy explicit: without it
the identity must already exist, and with it a brand-new database is
provisioned once. This is one argument beyond the three requested. It keeps a
missing or replaced file from ever being provisioned silently. Verification
runs before READY, and a failure writes `DURABLE_DATABASE_IDENTITY_REFUSED`
and publishes nothing on stdout. On success the helper publishes READY, then

```json
{"version":2,"event":"DATABASE_IDENTITY","identityHi":101,"identityLo":202}
```

and enters the unchanged protocol v2 loop. The one-argument D2 mode is
unchanged and publishes no identity. `DurableProtocol.protos` validates the
event exactly: version, event name, exactly four fields, exact Integer
components within signed 64 bits, and at most 4096 octets. It returns a copied,
frozen object. The event does not authenticate the helper.

### 18.7 Finality of reconciliation

`durable-client-driver.protos` now separates two results:

- `ABSENT_OBSERVED`: no receipt was visible during this query. The key stays
  open for reconciliation.
- `NOT_APPLIED`: `ABSENT`, **and** the harness recorded
  `SOURCE_TERMINATED` for this key after observing the effective termination
  of the OS process that ran the operation.

`SOURCE_TERMINATED` is a harness command. It is not a protocol field, not a
helper reply and not a Protos capability. It records evidence and changes no
state by itself. `FOUND` resolves `COMMITTED` or `CONFLICT` with or without it.
Nothing is retried automatically. A retry after `NOT_APPLIED` is still
idempotent through `applyOnce`.

Database binding: after `BIND_DATABASE` (the trusted bootstrap identity), keys
recorded by `LOST` are bound to that identity. `RECONCILE` and `RESOLVE` for
such a key require the current helper's DATABASE_IDENTITY to have been
`VERIFIED` by `HELPER_IDENTITY`, and every READY resets that verification. A
reconnection to another database therefore cannot resolve the key (D3-T12).
Keys recorded while unbound, with the D2 one-argument helper, keep D2 behavior
apart from the finality rule, and their database is not verified. The ledger is
volatile client memory. It is not a persistent intent log.

### 18.8 Not covered

Parent custody of the OS process does not provide:

- revocation of an Actor while its owning JVM stays alive;
- cooperative cancellation of arbitrary C calls;
- safety against uninterruptible kernel waits (a task in `D` state dies only
  when the wait ends);
- production supervision, restart policy or resource limits;
- authentication of `clientId`;
- a database identity that is authenticated against a malicious host, which
  can copy the file or forge the pair;
- persistence of the client's pending requests across a client crash;
- a public durable API;
- survival of power loss or of storage that breaks its own guarantees.

Further limits of this fixture:

- Custody covers the helper only while the client stays its parent. It depends
  on Linux `PR_SET_PDEATHSIG` semantics. If the forking thread ended before the
  process, the helper would end early.
- If a sub-reaper or the environment's PID 1 does not reap, a dead helper
  stays a zombie. It runs no code, but its PID stays allocated.
- If the helper's process group became orphaned while it was stopped, Linux
  would also send SIGHUP and SIGCONT. In the JUnit topology the group is the
  harness's group and is not orphaned.

D-10 is **not fully resolved**. D3 tests one OS-level mechanism for one
topology.

### 18.9 Tests

| Test | Acceptance condition | Result |
|---|---|---|
| D3-T1 `d3T1ParentOwnershipIsEstablishedBeforeJvmStartup` | distinct PIDs; helper's `/proc` parent is the client; both images are the Protos CLI JVM; `PARENT_OWNERSHIP_VERIFIED parent=<client> signal=SIGKILL` precedes the profile, `LANDLOCK_APPLIED` and `CLIENT_READY`; the harness gets no stdout line | NOT RUN |
| D3-T2 `d3T2DirectProtosToProtosCommunication` | `NORMAL`: INIT, `APPLIED`, `FOUND`, STOP, clean exit 0; no relay; helper gone; independent helper sees FOUND 77 and 1/1/77 | NOT RUN |
| D3-T3 `d3T3ParentDeathSignalSurvivesExecve` | custody set before the helper JVM exec; helper stopped with SIGSTOP (state `T`); client SIGKILL (137) still ends it | NOT RUN |
| D3-T4 `d3T4ClientSigkillTerminatesIdleHelper` | idle helper running; client SIGKILL; helper not running within the deadline | NOT RUN |
| D3-T5 `d3T5ClientSigkillBeforeCommitLeavesNothing` | client SIGKILL at `DURABLE_PRECOMMIT`; helper gone; reopened database: `ABSENT`, 0/0/0, integrity ok | NOT RUN |
| D3-T6 `d3T6ClientSigkillAfterCommitBeforeAckReplays` | client SIGKILL at `DURABLE_COMMITTED_BEFORE_ACK`; helper gone; another confined JVM: `FOUND 77`, `REPLAYED 77`, 1/1/77 | NOT RUN |
| D3-T7 `d3T7NormalClientExitDoesNotLeaveHelperRunning` | `EXIT_AFTER_READY` and `EXIT_AFTER_COMMIT` exit 0; neither leaves a running (non-zombie) helper; the commit is durable | NOT RUN |
| D3-T8 `d3T8WrongDatabaseIdentityRefusesReconciliation` | wrong identity, re-provisioning, and an identity-less populated database are refused before READY with every file unchanged; a client with another bootstrap identity stops with `CLIENT_DATABASE_IDENTITY_MISMATCH` and sends nothing | NOT RUN |
| D3-T9 `d3T9SameDatabaseIdentitySurvivesRestart` | identity at the signed 64-bit extremes; after restart READY, DATABASE_IDENTITY `VERIFIED`, `FOUND 77`; second session `REPLAYED` | NOT RUN |
| D3-T10 `d3T10LiveSourcePlusAbsentDoesNotEstablishRollback` | source held before COMMIT and alive; another helper's reconciliation gives `ABSENT_OBSERVED` (or stays `UNCERTAIN`), never `NOT_APPLIED` | NOT RUN |
| D3-T11 `d3T11TerminatedSourcePlusAbsentPermitsSafeRetry` | source helper SIGKILL, termination observed, `SOURCE_TERMINATED`; verified reconciliation `NOT_APPLIED`; retry `APPLIED` once, then `REPLAYED`; 1/1/77 | NOT RUN |
| D3-T12 `d3T12WrongIdentityCannotProduceFalseNotApplied` | after termination evidence, a LOOKUP answered `ABSENT` by a database with another identity is `REJECTED`; key stays `UNCERTAIN`; the verified original then gives `NOT_APPLIED` | NOT RUN |
| D3-T13 `d3T13ParentMismatchRefusesStartup` | PID 1, 0, −7, `abc` and 2147483648: `PARENT_OWNERSHIP_FAILED`, exit 72, no READY, no database file; the true parent passes | NOT RUN |
| D3-T14 `d3T14LossOfClientDoesNotWidenLandlockAuthority` | after a client crash, a fresh confined JVM is still denied the exterior database; the native TCP probe is denied with the custody option; exterior unchanged | NOT RUN |
| D3-T15 `d3T15NoNativeHandleCrossesDirectProtosIpc` | malformed DATABASE_IDENTITY events and object/array/string reply values are refused by Protos; the live helper refuses object/array payloads; a real reply is one flat object of scalars; no FFI import on the client side | NOT RUN |
| D3-T16 `d3T16D2ReconciliationRequiresExplicitSourceTermination` | D2-T7 pattern: `ABSENT_OBSERVED`, then `NOT_APPLIED` only after `SOURCE_TERMINATED`; D2-T8 and D2-T15 patterns resolve `COMMITTED` with explicit evidence | NOT RUN |

State: **PLAT074 OPEN; D256 pending; LIB031 BLOCKED; D-10 not fully resolved;
D2-T1–T16 PASS (focal, prototype); D3-T1–T16 NOT RUN.**
