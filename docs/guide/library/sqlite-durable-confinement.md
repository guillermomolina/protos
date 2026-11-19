# SQLite durable confinement architecture decision packet

**PROPOSED — OWNER APPROVAL PENDING**

PLAT074: **OPEN**. D256: **pending final reconciliation**. LIB031:
**BLOCKED**. This is a non-normative research/proposal document, not a new
Standard Library contract or a ratification record. No spike, benchmark, build,
test, Git command or validation script was executed for this packet.

## 1. Status, scope and evidence vocabulary

[D256-C1](https://github.com/guillermomolina/protos-project-docs/blob/main/docs/project/decisions/language/D256_C1_PROTOS_FIRST_SQLITE_ARCHITECTURE_APPROVED_2026_11_11.md)
approved a SQLite-specific, Protos-first library. Its
[implementation handoff](https://github.com/guillermomolina/protos-project-docs/blob/main/docs/project/work/D256/D256_C1_IMPLEMENTATION_BOUNDARIES_2026_11_11.md)
explicitly reserves durable authority and blocking/cancellation decisions.
LIB029 and LIB030 supply the memory-only implementation and extensions described
in [the current guide](sqlite.md). Their behavior is preserved. PLAT074 must
still select effective durable confinement; LIB031 cannot publish persistent
acquisition before both authority and native-blocking behavior are resolved.

Three properties require separate evidence:

| Property | Question | What does not establish it |
|---|---|---|
| Access authority | Which resource and effects did the host authorize? | A filename, import or possession of a Path |
| Native-code isolation | What prevents C from exercising authority beyond that grant? | A Protos Filesystem wrapper or safe handle lifetime |
| SQLite mutable-state isolation | Which Actor owns connection state; which native globals are shared? | A mutex, separate facades or a shared arena |

A grant for Protos Filesystem operations does not confine arbitrary SQLite C.
Neither `std:ffi` nor `ffi:sqlite3` may become an indirect bypass of a future
confined persistent API. Today host authorization of that alias authorizes trusted
native execution; this packet does not retroactively turn R1 into a sandbox.

Evidence labels used below:

- **IMPLEMENTED**: directly inspected repository code; not a new execution claim.
- **PRIMARY**: behavior documented in the linked upstream specification/source.
- **DERIVED**: reasoning from those boundaries; not empirical confirmation.
- **PROPOSED**: candidate policy or mechanism requiring owner approval.
- **UNTESTED**: a property still requiring the specified experiment.
- **PENDING APPROVAL**: no implementation authorization follows from this packet.

Source documents can describe guarantees without proving this composition. A
future implementation must preserve existing Truffle infrastructure and the
internal specialized execution of ordinary Protos message semantics. There is
no justification here for inserting sends into hot paths, restoring old
infrastructure, bypassing current mechanisms or changing primitive value
representations: those remain `long`, `double` and `boolean`. Native ABI `int`
carriers are a separate boundary concern.

## 2. Current implementation and governing boundaries

Paths below are relative to this repository. Java provider paths share
`providers/ffm/src/main/java/com/guillermomolina/protos/ffm/`; filesystem paths
share `src/main/java/com/guillermomolina/protos/execution/host/io/`.

| Inspected file | IMPLEMENTED boundary and consequence |
|---|---|
| `protos/lib/sqlite/module.protos` | Publishes `SQLiteError` and `openMemory`; ABI acquisition occurs inside that Closure, not on module import. No public durable path parameter. |
| `protos/lib/sqlite/abi.protos` | Declares `sqlite3_open_v2` with a UTF-8 filename and a null-only private VFS-name type. Owned databases use `sqlite3_close_v2`; statements use `sqlite3_finalize`. A native filename parameter is not authority. |
| `protos/lib/sqlite/internal/Resources.protos` | Opens `:memory:` with READWRITE, CREATE and MEMORY flags. Ordinary Protos owns admission, Statement state, copied rows, child cleanup and terminal-Future close. It supplies no persistent grant or SQL sandbox. |
| `protos/lib/sqlite/internal/Borrowed.protos` | Copies column bytes within the borrow window; byte count/status use preserving declarations. No native region is public. Routing only `step` would miss these native calls. |
| `FfmForeignProvider.java` | `canonicalTarget` accepts the policy target; `openSession` captures immutable host options in an Actor-owned session. Provider construction itself loads nothing. |
| `FfmLibraries.java` | Distributable policy resolves only logical `sqlite3`, using `libsqlite3.so.0` or host-only absolute `sqlite3.path`. Host registration/options authorize acquisition, not JAR contents or guest filenames. |
| `FfmSession.java` | `acquireModule` lazily obtains `Linker.nativeLinker`, checks the platform, creates `Arena.ofShared`, then loads through `SymbolLookup.libraryLookup`. `bind` resolves against that lookup, never a default/loader fallback. Dependency symbols may still be visible. |
| `FfmBinding.java` | `invoke` uses a per-call `Arena.ofConfined` for marshalled inputs/outputs; adopts owned outputs before fallible conversion; advances borrow epochs before non-preserving calls. `invokeDestructor` invokes the native target directly. |
| `ProtosLinuxConfinedReadWriteTreeFilesystemBackend.java` | Pins a directory descriptor and selects descendants with confined `openat2`; owns its files and worker. This confines capability operations, not the JVM or SQLite. |
| `ProtosLinuxConfinedFilesystemWorker.java` | Two platform threads per grant: an unrestricted orchestration driver and a restricted syscall executor. The driver routes explicit backend syscalls; arbitrary libc/SQLite syscalls are not intercepted. |
| `ProtosLinuxConfinedFilesystemNative.java` | `openBeneath` uses BENEATH, NO_SYMLINKS, NO_MAGICLINKS and NO_XDEV. Its routing boundary is explicit native backend invocation. SQLite's normal Unix VFS does not call this Java routing method. |
| `ProtosLinuxConfinedFilesystemLandlock.java` | `restrictCurrentThreadBeneath` queries ABI, handles rights and installs the rule on the calling thread. Existing minimum ABI 3 is not evidence of a sufficient SQLite policy on every kernel. |
| `spec/semantics/FFI.md` | Trusted in-process R1, Actor-local authority, synchronous calls, custody and permanent retirement. No public callbacks/upcalls or arbitrary library paths. |
| `spec/concurrency/ACTORS.md` §§16, 24J–24L | No automatic live-native transfer; physical offload preserves the occupied synchronous segment; synchronized native state is not automatically Actor-isolated. |
| `spec/io/FILESYSTEM.md` §§18, 20–21 | Capability confinement covers complete resolution; stable opened resource is distinct from pathname identity; Path and URL confer no authority. |
| `spec/io/PROCESS_IO.md` §§22, 26, 28 | Explicit bootstrap grants, no automatic resource proxying; Protos Process is not an OS process; termination transfers residual resources to host cleanup without guaranteeing persistence. |
| `spec/semantics/MODULES.md` | Actor-local module/facade identity and private initializer contexts; imports do not manufacture FFM or durable authority. |

`FfmSession` explicitly explains the shared arena: an Actor may resume on another
host thread. Shared accessibility is a lifetime/thread-access property, not an
OS security domain or connection-affinity guarantee. A confined arena cannot
simply replace it while arbitrary Actor carriers access the segments.

`beginCall` rejects admission once closing starts; active calls keep the arena
alive. `close` waits for admitted calls, revokes borrowed regions, drains custody,
retires and destroys remaining owned resources, clears caches, then closes the
arena. Explicit release retires before destroying and does not retry uncertain
destruction. The lifecycle monitor is not held while C runs. Nothing inspected
routes loading, binding, downcalls, destructors or arena closure to a SQLite
restricted worker. The existing cleanup mechanism must be extended coherently,
never replaced by a second custody registry or GC-driven persistence finalizer.

The native [close contract](https://www.sqlite.org/c3ref/close.html) requires a
further distinction: with outstanding prepared statements, BLOB handles or
unfinished backups, `sqlite3_close_v2` can return `SQLITE_OK` while leaving an
unusable zombie connection. Physical deallocation is deferred until the
dependent statements are finalized, BLOB handles closed and backups finished.
Successful native close invocation is therefore not proof of physical
connection destruction. BLOB and backup APIs are not added to the current
library by this observation.

Keep four lifecycle events distinct: retirement of the connection from the
public API; finalization of its dependent native resources; physical destruction
of the SQLite connection; and termination of its confinement domain. The
current Protos wrapper closes tracked statements before calling
`sqlite3_close_v2`; the native contract must nevertheless be accounted for by
any future confinement design. The domain must remain effective through every
deferred destruction effect, including destruction triggered by later dependent
resource cleanup. Neither `SQLITE_OK` nor public retirement permits earlier
domain termination. This obligation belongs to the existing custody mechanism,
not a second resource manager.

The durable blocker registry in `guillermomolina/protos-project-docs`,
[`IMPLEMENTATION_BLOCKERS.md`](https://github.com/guillermomolina/protos-project-docs/blob/main/docs/project/registries/IMPLEMENTATION_BLOCKERS.md),
does not supply a SQLite unblock condition; the specific open gates are in the
D256-C1 handoff. No status is changed here. The PLAT074 work directory was not
available at the consulted external path; no missing record is fabricated.

## 3. Threat model and required mitigations

Assume untrusted guest SQL/options, mutable namespaces and independent authorized
Actors/processes; the host controls grants and approved native binaries. A
malicious host/kernel is outside the grant's protection. Distinguish malicious
native code from SQL-triggered effects of trusted C: B shares JVM memory and
process descriptors, so Landlock on one thread alone cannot promise containment
of arbitrary malicious C that can enlist unrestricted threads. D is the stronger
candidate if that threat is in scope. Each row is a required guarantee for the
future contract, not a claim of an implemented mitigation.

Source keys S/L/J/T refer to §6; R1 refers to the local
`spec/semantics/FFI.md` contract inspected in §2. **Gap** is UNTESTED unless
stated otherwise.

| Threat | Required guarantee | Candidate mechanism | Evidence | Gap / falsifier |
|---|---|---|---|---|
| Authorized directory vs exact database | No accidental grant over sibling data | Host-owned dedicated DB directory, or separately proved file-plus-sidecar policy | L1, S1 | B directory grants do not enforce one exact DB identity |
| Symlinks | No exterior target through any SQLite open | Kernel boundary plus secure selection; reject unsafe layout | L1–L2 | Unix VFS does not inherit backend `openat2` flags |
| Hardlinks | No alias exposing an unauthorized inode | Host-exclusive provisioning and explicit alias policy, or reject | L1–L2; DERIVED | NO_SYMLINKS does not detect hardlinks; lexical location is insufficient |
| Bind mounts | No unintended mounted resource | Pin verified scope; forbid crossing where enforceable | L2 | Landlock hierarchy alone is not NO_XDEV on every SQLite lookup |
| Rename / parent substitution | Main DB and sidecars remain in one authorized identity domain | Stable root custody, adversarial namespace checks; fail if binding cannot be maintained | L1–L2 | Reusing an absolute path after `openat2` reintroduces a race |
| Preopened descriptors | No inherited exterior read/write authority | Audit every reachable descriptor; D can close inherited descriptors before entry | L1 | Threads share an FD table; B cannot close unrelated JVM FDs safely |
| Revocation after open | Stop new admission; define disposition of already entered effects | Existing custody cut, drain and restricted destruction; stronger revocation needs separate proof | L1, R1 | Landlock does not revoke existing read/write FDs or mappings |
| Temporary directories / implicit paths | All spill/sort/temp effects confined | Dedicated authorized temp scope or tested memory-temp configuration plus kernel denial | S3 | `temp_store` alone does not eliminate all journal/sidecar effects |
| Process permissions / umask | No world-readable unintended files or host policy mutation | Provision directory ownership/modes at host; audit SQLite-created modes | S3; DERIVED | Global umask changes affect other Actors; no per-call umask trick |
| Main database | Correct target and stable open | Approved acquisition under effective boundary | S1, S2 | A prevalidated string is not a confined open |
| Rollback journal / super-journal | Atomicity and recovery side effects stay authorized | Preserve Unix VFS; grant required create/remove/sync operations | S3, S8 | Exact-file-only authority may prevent recovery |
| `-wal` | WAL remains with database, no lost committed records | Native VFS with tested writable sidecar directory | S2 | Denial cannot be silently converted to success or weaker journaling |
| `-shm` | Shared index and locks work across processes | Native mapping/locking inside boundary | S2 | Per-session heap emulation does not supply cross-process SHM |
| Mmap / shared memory | No exterior mapping; lifetimes extend through SQLite use | Admitted FDs and SQLite-owned mapping; close after in-flight work | S2, J1 | Arena lifetime is not lifetime of SQLite's internal mappings |
| `ATTACH DATABASE` | Cannot reach exterior DBs or enlarge authority | Kernel enforcement; additional SQL policy may deny all auxiliary DBs | S5, S9 | Authorizer/filter alone is not sandboxing |
| `VACUUM INTO` | Cannot create exterior output | Same effective native boundary and explicit public policy | S9; DERIVED | Destination evaluated during native execution, not just initial open |
| URI filenames | No VFS/locking/authority bypass | Deny guest URI options; verify effective build/global/open configuration | S4 | `vfs`, `nolock`, `immutable`, encoded names need negative tests |
| Extension loading | No new code or authority | Disable by supported build/configuration; deny SQL enablement and alternative FFI binding | S9 | Trusted library loading is already native execution, not safe symbol discovery |
| Side-effecting PRAGMAs | Policy holds at prepare and step | Bounded Protos configuration plus native enforcement throughout | S6 | Some PRAGMAs act during prepare; preparation is not an effect-free security phase |
| Prepare / reprepare | Authorization survives schema changes and recompilation | Entire prepare/step/reprepare path in domain; keep policy installed if used | S5 | `prepare_v2` may recompile during step; one-time text inspection fails |
| Untrusted schema / functions | No privileged execution through schema or callbacks | Defensive/trusted-schema settings, no unapproved functions/extensions | S9 | Schema hardening is defense in depth, not C isolation |
| Checkpoint / close | All writes/deletes/syncs and finalization remain confined | Same domain through final destructor and library teardown | S2, R1 | Restricting only open or step misses cleanup effects |
| Zombie connections / deferred destructors | No pending cleanup executes outside the originally authorized domain | Preserve existing custody and confinement through dependent-resource cleanup and physical connection destruction | [SQLite close contract](https://www.sqlite.org/c3ref/close.html); DERIVED lifetime obligation | `sqlite3_close_v2` returning `SQLITE_OK` does not prove destruction; ending the domain before deferred effects invalidates confinement |
| Network filesystem | No unsupported locking/durability claim | Initially reject until backend-specific evidence | S2, S8 | WAL requires same-host shared-memory coordination; NFS-like behavior not assumed |
| Native library load / initializers | No uncontrolled initialization effects | Host pins trusted binary/dependencies; define bootstrap trust before restriction | J2, T1 | Preloading is explicitly trusted work outside domain, not proof of full-session confinement |
| FFM symbol resolution | No alternate libc/dependency escape | Session policy must cover every binding, not merely known SQLite symbols | J2, R1 | Lookup scoped to a library may still expose dependency symbols |
| C global state | Actors cannot mutate shared globals through ordinary local wrappers | Restrict exposed operations; immutable config or explicit approved service boundary | T1, S7; ACTORS §24K | Serialized SQLite is thread-safe, not automatically Actor-isolated |
| Arenas / segments | No use-after-close or wrong-thread marshalling | Retain existing admission/lifetime discipline; marshal within owning call extent | J1, R1 | Queuing call-confined segments to another thread is invalid |
| Destructors | Same authority as original acquisition | Route explicit release, failed acquisition and session teardown through same domain | R1, T1 | Native/library unload finalizers may execute on the closing thread |
| Host-thread changes | Connection remains safe when Actor carrier changes | Stable backend affinity with synchronous handoff, if evidenced | S7, J1 | Shared arena alone does not route execution |
| Multiple Actor sessions | No cross-session handle/global/config alias | Existing Actor-local identity, session generation and explicit grant binding | R1, T1 | Same `.so` can share global C state despite distinct sessions |
| Unrestricted `ffi:sqlite3` | No parallel unrestricted route in a confined deployment | Host must deny incompatible acquisition or enforce domain at every provider entry | R1 | A library-only wrapper cannot protect a separately authorized general FFI facade |
| Independent Actors | Independent local state with explicit external DB contention | Separate connections and approved grants; SQLite arbitrates external state | ACTORS §24K, S7 | Locks must not become implicit shared mutable Protos objects |
| Two OS processes | Correct native file locks and crash recovery | Unix VFS and real cross-process trials | S2, S8 | JVM-only locks cannot replace SQLite locks |
| `SQLITE_BUSY` / contention | Bounded declared waiting and honest transaction state | Approved busy policy in Protos; SQLite native status/autocommit | S10 | Busy timeout is not a bound on disk sync or all query time |
| Deadlocks | No wait cycle between Actor, worker, callbacks and close | No guest execution in worker; ordered native admission; no lifecycle monitor held in C | R1; DERIVED | No bounded-progress claim if native call never returns |
| Actor cancellation | No hidden suspension, interruption or invented rollback | Preserve ACTORS §24J; cancellation remains pending under synchronous contract | R1 | Thread interrupt is not safe SQLite cancellation |
| Close during native call | No use-after-close, duplicate destroy or effect erasure | Admission cut, drain entered call, retire once | R1 | Non-returning C can indefinitely delay close |
| Failed `COMMIT` | Never assume transaction ended or replay write | Read actual autocommit/transaction state when safe; surface uncertainty | S10 | BUSY may leave transaction active; errors may auto-rollback |
| `ENOSPC` / I/O failure | Honest failure and recoverable database where SQLite guarantees it | SQLite journal/recovery; no wrapper-level pretend rollback | S8, S10 | Failure injection and cleanup evidence absent |
| Corruption | No silent repair or success | Report SQLite errors; isolate resource; explicit recovery policy | S9 | B cannot contain all native memory faults |
| SIGKILL | Reopen yields valid recovered committed state or explicit error | Native journal/WAL recovery | S8 | Killing a helper can leave commit outcome unknown |
| Power loss | Guarantee only the chosen sync/VFS/storage profile | Approved journaling/synchronous configuration and storage fault tests | S8 | SIGKILL does not test cache loss, torn sectors or faulty flushes |

`sqlite3_set_authorizer`, SQL filtering and checked names can express public
policy. They do not mediate every native filesystem operation. There is also a
documentation caveat in the current guide's SQL-admission discussion: the broad
statement that preparation never executes SQL must not be used as a security
premise for PRAGMAs. S6 documents compile-time effects. This packet records the
limitation without changing memory behavior or claiming a new SQL admission rule.

## 4. Architecture candidates: exactly A–F

### A — SQLite C in a trusted process

Host provisions a location; normal Unix VFS uses the process's explicitly
acknowledged ambient authority. This is the smallest durable implementation, but
only a **weak trust profile**, not effective native confinement. Other authorized
C interfaces can access the same ambient filesystem. Native corruption kills the
host; all calls remain synchronous. Pay only for the connection and existing
provider. Growth to confinement later requires a new host authority boundary and
possibly deployment changes; publishing arbitrary paths now would create an
avoidable compatibility migration. Disqualified for an advertised confined API.

### B — SQLite C in a restricted Landlock worker

PROPOSED evaluation target: a host execution domain of fixed bounded authority
runs **all** relevant native SQLite work while preserving the normal Unix VFS.
It is neither a Protos Actor nor a Protos Process. No new worker implementation
is authorized by this description or by the existing Filesystem worker.

The required lifecycle, to be falsified rather than assumed implementable:

1. Capture one host-approved immutable grant identity and pin its scope. Establish
   whether this is a dedicated directory or a stronger exact-DB authority.
2. Fix trusted library/dependency identities. Compare preloading (unrestricted
   initializers must be explicitly trusted) against loading after restriction
   (loader/dependency paths and initializers must fit the grant). No extra library
   or symbol may later widen authority. A domain that can ask an unrestricted
   loader/executor for arbitrary work is not confined.
3. Install and confirm restrictions on the actual native executor before admitting
   DB work. The current driver-plus-syscall worker does not do this for SQLite.
   Class/linkage/FFM initialization after restriction must not require broader
   file authority or a bypassing fallback.
4. Bind each connection, statement, borrowed-copy window and native acquisition to
   its original session/grant. Keep operation order and one active connection
   operation at a time. Evaluate both multiple connections within one fixed grant
   and independent domains for distinct grants. Never merge grants into a union
   worker merely for reuse. Landlock restriction cannot be removed to recycle an
   executor into a broader grant.
5. Preserve the Actor's occupied synchronous segment until completion. Protos
   callbacks, handlers and dynamic state remain with that segment; R1 has no
   native callback API. Reject reentrant/foreign-thread guest entry rather than
   adding an invented callback lane. Do not insert Actor messages for native work.
6. Keep marshalling, downcall, output capture and borrowed copying within valid
   thread/lifetime extents. Shared arenas do not enforce affinity; confined arenas
   cannot cross the handoff. Whether marshalling must move with execution remains
   an exact implementation proof obligation, subject to §6's precedent gate.
7. Preserve existing custody through rejected publication, explicit release,
   session close and native-library teardown. Drain started operations before
   destruction; then terminate the worker. No cleaner/finalizer may destroy on
   an unrestricted carrier, and no second resource manager is introduced.

Unresolved structural risks include shared C globals, SQLite's process-local Unix
lock bookkeeping, process-wide descriptors, optional native-created threads,
library unload behavior and interactions with other authorized FFM interfaces.
Keep native global configuration fixed; do not expose process-global mutation
as Actor-local state. Retaining native WAL/SHM/interprocess locks is a goal, not
a proved consequence of Landlock. Existing ABI-3 Filesystem enforcement cannot
substitute for auditing all SQLite rights and effects on the actual kernel.

The [Landlock documentation](https://docs.kernel.org/userspace-api/landlock.html)
also describes `LANDLOCK_RESTRICT_SELF_TSYNC`, available from ABI 8: unlike
restriction of only the calling thread, this flag synchronizes application of
the restriction to the process's existing threads. This process-wide effect is
material to a JVM hosting multiple Actors, independent grants and other host
services. A common restriction can deny operations needed by those services;
allowing the union of their authorities does not establish independent
confinement for each grant. Process-wide synchronization is not a substitute for
isolated per-grant authority, nor permission to widen a grant or weaken other
services' boundaries.

Any evaluation must detect the kernel's actual Landlock ABI, supported flag and
effective policy rather than infer support from a kernel release string or
assume that an existing ABI-3 backend provides TSYNC. If confinement of one
independent grant requires restricting the entire JVM, that invalidates B's
per-grant isolation proposal. TSYNC is an evaluated constraint, not a selected
mechanism or authorization for new Java infrastructure.

Users without SQLite pay no worker cost. Durable users would pay bounded worker,
queue and handoff costs; exact counts are not selected. Many grants can create a
thread/memory scaling cliff. Begin with one grant/connection only if that scope
meets the approved use case; preserve multiple-grant separation from the start.
Changing to D later can retain Protos policy/value APIs if raw paths, threads and
native handles never become public, but requires replacing the host transport.

### C — Custom SQLite VFS

Implement `sqlite3_vfs` and `sqlite3_io_methods`, replacing the native filesystem
integration. This conflicts with preserving the unchanged Unix VFS and requires
an explicit necessity finding before reconsideration; reuse of Filesystem alone
does not justify it.

| Required callback surface | Work that cannot be omitted |
|---|---|
| `xOpen`, `xDelete`, `xAccess`, `xFullPathname` | All DB/temp/sidecar names, authority, access flags, canonical identity, deletion sync |
| `xRead`, `xWrite`, `xTruncate`, `xFileSize`, `xClose` | Offset/short-read semantics, exact errors, lifetime and close ordering |
| `xSync`, `xSectorSize`, `xDeviceCharacteristics` | Honest storage barriers and atomicity assumptions |
| `xLock`, `xUnlock`, `xCheckReservedLock` | Real interoperable interprocess locking, not Java mutexes |
| `xFileControl` | Opcode-specific policy; reject unsupported operations honestly |
| `xShmMap`, `xShmLock`, `xShmBarrier`, `xShmUnmap` | Shared mapping lifetime, memory ordering, process-shared locks and cleanup |
| `xFetch`, `xUnfetch` | Optional mapped access with valid pointer/lifetime guarantees |

Also address VFS loading hooks, randomness, time and sleep; a file-only adapter is
not the complete VFS boundary. FFM upcalls require new audited native callback
machinery absent from public R1. SQLite calls its VFS synchronously; awaiting an
ordinary asynchronous Filesystem Future in an upcall cannot silently permit
Actor reentrancy or block waiting for the same occupied Actor. Native pointers,
upcall arena ownership, exception containment and shutdown add substantial cost.
No VFS may claim SQLite transactions merely because it can store bytes.
The smallest usable C is already a substantial native interoperability project;
WAL cannot be a speculative later add-on to false locking guarantees. Porting
this substrate later is a major rewrite. No Java callback design is authorized.

### D — Isolated OS process

A confined helper runs SQLite and its Unix VFS; host-private bounded IPC carries
operations and copied results. No global ambient-authority daemon. A helper can
own one fixed grant/security domain, with separate connections where justified.
It is an OS implementation detail, distinct from Protos Process identity.

The protocol needs versioned framing, operation ordering, bounded messages/rows
and memory, generation-scoped remote connection/statement handles and no native
addresses. Authenticate the local peer (prefer inherited private endpoints),
limit inherited FDs, reject malformed messages and prevent clients from selecting
new paths/providers. Bound queued requests and per-connection retained resources.
Keep transport faults separate from SQLite statuses. A disconnect after COMMIT
entry can mean **unknown outcome**, never safe automatic replay. Restart creates
a fresh generation; old remote handles stay retired. Recovery must finish before
new acquisition is acknowledged.

SQLite synchronization, sidecars and locks stay native; helper confinement still
needs OS policy, library loading and temp-file proof. IPC serialization adds
latency/copying; packaging adds a binary/protocol/lifecycle compatibility burden.
Only durable users should pay helper cost. Preserve ACTORS §24J across IPC:
synchronous caller does not interleave while waiting. A process boundary improves
crash containment but does not guarantee storage correctness. D is the fallback
for a separately approved analysis if B fails structurally, not selected here.

### E — Alternative native isolation

| Platform / mechanism | Relevant property | Limit relative to B/D |
|---|---|---|
| Linux seccomp | Reduces allowed syscall surface | Classic filters cannot inspect pathname pointees; not a filesystem sandbox alone |
| Linux namespaces | Separate selected namespace views, mounts and credentials | Mount/user setup, inherited FDs and privileges need audit; not automatic data-grant identity |
| [FreeBSD Capsicum](https://man.freebsd.org/cgi/man.cgi?query=cap_enter&sektion=2) | Capability mode restricts global namespace access; descriptor rights | Native Unix VFS pathname/dependency assumptions need adaptation or helper provisioning |
| OpenBSD [pledge](https://man.openbsd.org/pledge.2) / [unveil](https://man.openbsd.org/unveil.2) | Restricts operation classes and visible paths; policy can be locked | Process-oriented constraints and platform semantics differ from per-worker Linux use |
| macOS Seatbelt / sandbox-exec / [App Sandbox](https://developer.apple.com/documentation/security/app-sandbox) | OS sandbox policy is the relevant boundary | The consulted Apple page required JavaScript; no stable embedding recipe was verified. No Protos backend or sandbox-exec support promise follows. |
| [Windows AppContainer](https://learn.microsoft.com/en-us/windows/win32/secauthz/appcontainer-isolation) | Process token/capability and resource isolation | Requires Windows SQLite VFS and ACL/handle provisioning; cannot preserve Unix VFS literally |

These are alternatives within E, not equivalent guarantees or additional
selected architectures. Pay for platform-specific maintenance only when a
supported deployment needs it. Grow by adding a separately proved host backend;
deferring it changes platform coverage, not current memory API semantics.
Reject unsupported durable provisioning instead of weakening confinement.

### F — Defer durable SQLite

Retain only memory acquisition. Avoid new native authority, confinement machinery,
IPC and persistence promises. Functional cost: no durable SQLite for consumers;
restart recovery and the pending application migration remain blocked. This does
not sandbox the already-authorized trusted FFM profile. Memory functionality can
grow independently; later durable work adds the approved host boundary and Protos
policy without rewriting `openMemory`. F is today's smallest safe shipped scope,
not a substitute for the required durable capability forever.

## 5. Provisional decision matrix

Scores are **1 (poor) to 5 (strong)**; H/M/L mean HIGH/MEDIUM/LOW confidence.
Each cell gives score, confidence and justification. These are architectural
judgments, not measurements, test outcomes or benchmark results. E scores assess
the cost of a platform portfolio, not a fictitious universal sandbox. F's high
safety scores reflect withholding the capability; F does not satisfy durability.

| Dimension | A | B | C | D | E | F |
|---|---|---|---|---|---|---|
| Correctness / invariants | 1/H: ambient C fails confinement | 3/L: full execution coverage unproved | 2/M: new locking/SHM correctness burden | 4/M: stronger process boundary; IPC gaps | 3/L: policies differ by host | 5/H: withholds unsafe acquisition |
| Protos-first alignment | 3/H: small wrapper, weak authority | 4/M: Protos policy, narrow host mechanism | 2/M: callback substrate displaces ordinary library work | 4/M: explicit boundary, ordinary library | 3/M: policy remains local, many substrates | 5/H: preserves current universe |
| Present proportionality | 4/H: little mechanism, wrong security scope | 3/L: worker cost may exceed present demand | 1/H: full VFS project | 2/M: helper/protocol packaging | 1/M: many platform backends now | 5/H: no unused machinery |
| Incremental growth | 2/M: ambient API hard to strengthen | 4/M: fixed-grant start, bounded extension | 2/M: small subset cannot fake full locking | 4/M: one helper scope then more | 3/M: add hosts independently | 4/H: additive durable surface later |
| Future options | 2/M: path contract risks lock-in | 4/M: keep transport private | 2/M: custom VFS becomes durable burden | 4/M: host protocol can evolve privately | 4/M: preserves multiple OS paths | 5/H: no premature host commitment |
| Scalability | 3/M: low overhead, process globals | 3/L: per-grant threads and C globals | 2/L: callbacks and distributed locks | 3/M: helper memory/IPC per domain | 3/L: platform-specific limits | 2/H: no persistent workload support |
| Conceptual simplicity | 4/H: explicit weak trust | 3/M: hidden affinity and grant lifecycle | 1/H: large callback/locking contract | 2/M: remote handles/failure protocol | 2/M: several incompatible policies | 5/H: memory-only contract |
| Portability | 3/M: SQLite portable, current provider bounded | 1/H: Linux-specific enforcement | 3/L: portable interface, difficult backends | 3/M: helpers port, sandbox policy differs | 3/L: broad reach, no single mechanism | 4/H: adds no durable host requirement |
| Runtime / resource cost | 5/M: direct synchronous entry | 3/L: handoff and retained threads | 2/L: upcalls, possible extra copies | 2/M: IPC/startup/copies | 3/L: mechanism-dependent | 5/H: no durable execution overhead |
| Failure / operability | 1/H: native fault shares JVM | 2/M: stall/crash still shares JVM | 2/L: callback failures and recovery complexity | 4/M: kill/restart containment; commit uncertainty | 3/L: mechanism-specific failure modes | 4/H: existing failures, absent durable service |
| Reversibility / deferral cost | 2/M: later authority migration | 4/M: private host replacement possible | 1/M: persisted VFS assumptions costly | 3/M: protocol/deployment migration | 3/M: host policies replace independently | 4/H: consumer delay, additive API later |
| Evidence / implementation risk | 4/H: mature native pattern, no confinement | 2/L: no equivalent complete worker proved | 1/M: no eligible Java VFS precedent | 3/M: mature process boundary, Protos protocol unproved | 2/L: uneven API/precedent maturity | 5/H: current scope directly inspected |

Do not select by sum. A fails the required security profile even with low cost;
C fails the unchanged-Unix-VFS objective and carries an overengineering red flag.
E as an immediate cross-platform framework is disproportionate. F delays the
motivating durable workload. B's possible execution/authority escape is a
non-compensating veto, not something a performance score can offset. D's process
boundary is stronger but carries real present packaging/protocol cost.

Dominant uncertainties: exact grant identity versus SQLite's many filenames;
all native entry/cleanup coverage; same-process FFM/FD escape paths; Actor-safe
synchronous handoff; eligible Truffle method-level precedent; crash recovery
and WAL locks. These require targeted proofs, not another generic survey.

## 6. Verifiable precedents and limits

Sources were consulted in read-only mode for this packet. Upstream `master`,
`latest` and `current` documentation can move; the GraalPy source baseline below
is pinned. Nothing here upgrades the repository toolchain. PRIMARY describes
the source's own behavior; adopting a composition is DERIVED and UNTESTED.

### SQLite primary sources

| Key | Source | Contribution and limit |
|---|---|---|
| S1 | [VFS overview](https://www.sqlite.org/vfs.html), [VFS object](https://www.sqlite.org/c3ref/vfs.html), [I/O methods](https://www.sqlite.org/c3ref/io_methods.html) | Native Unix VFS supplies filesystem integration. Replacing it means implementing far more than file reads/writes; these interfaces do not confer authority. |
| S2 | [WAL](https://www.sqlite.org/wal.html) | Sidecars, shared index, locking, checkpoints and same-host restrictions. Preserving this VFS avoids reimplementing its protocol, but does not prove the restricted executor can run it. |
| S3 | [Temporary files](https://www.sqlite.org/tempfiles.html) | Rollback, statement and super-journals, temporary databases, indices and VACUUM work can introduce additional files. Temp configuration is not confinement. |
| S4 | [URI filenames](https://www.sqlite.org/uri.html), [open](https://www.sqlite.org/c3ref/open.html) | URI interpretation depends on build/global/open settings; native flags and filenames need deliberate policy. |
| S5 | [Authorizer](https://www.sqlite.org/c3ref/set_authorizer.html), [prepare](https://www.sqlite.org/c3ref/prepare.html) | SQL authorization is compile-time policy; v2 statements can reprepare during step. Neither is a native filesystem sandbox. |
| S6 | [PRAGMAs](https://www.sqlite.org/pragma.html) | Some effects occur during preparation, depending on pragma/version. Do not postpone the security boundary until execution. |
| S7 | [Threading modes](https://www.sqlite.org/threadsafe.html) | Single-thread, multi-thread and serialized modes provide different native concurrency rules. None defines Protos Actor ownership. |
| S8 | [Rollback locking](https://www.sqlite.org/lockingv3.html), [atomic commit](https://www.sqlite.org/atomiccommit.html) | Native recovery depends on real locking, ordered storage operations and flush assumptions. A successful close is not an independent universal durability barrier. |
| S9 | [Security guidance](https://www.sqlite.org/security.html) | Defensive settings, limits and untrusted-schema hardening complement OS enforcement. They do not contain arbitrary native code. |
| S10 | [Transactions](https://www.sqlite.org/lang_transaction.html) | Failed COMMIT and automatic rollback require actual SQLite state observation, not a shadow Java transaction Boolean. |
| S11 | [WASM/OPFS persistence](https://sqlite.org/wasm/doc/trunk/persistence.md) | Demonstrates a different storage authority/VFS environment. Its worker, synchronous-access and locking constraints differ; documented WAL support has exclusive-locking caveats. It is not evidence of unchanged Unix-VFS multi-process behavior. |

### Linux primary sources

- **L1 — [Landlock kernel documentation](https://docs.kernel.org/userspace-api/landlock.html):**
  without TSYNC, restriction applies to the enforcing thread and future children;
  existing sibling threads are not thereby restricted. ABI-8 TSYNC applies the
  restriction to existing process threads, with the shared-JVM limitations in
  §4 B. Directory hierarchy rights and
  ABI negotiation matter. Existing descriptors retain access properties; this
  is not retroactive read/write revocation. DERIVED: a dedicated thread must
  cover all relevant execution, and same-process unrestricted cooperation remains
  a threat. No policy promises malicious-C isolation merely from this mechanism.
- **L2 — [openat2](https://man7.org/linux/man-pages/man2/openat2.2.html):**
  BENEATH, NO_SYMLINKS, NO_MAGICLINKS and NO_XDEV constrain the particular lookup.
  They are not a persistent policy over future SQLite filename resolutions.
  A held descriptor pins a resource; reopening its original text does not.
- **L3 — [seccomp](https://docs.kernel.org/userspace-api/seccomp_filter.html),
  [namespaces](https://docs.kernel.org/admin-guide/namespaces/index.html):**
  useful building blocks with distinct scope; seccomp documentation expressly
  distinguishes filtering from a complete sandbox. A namespace design still
  needs a concrete mount/credential/descriptor policy.

### Java 25 primary sources

- **J1 — [Arena](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/foreign/Arena.html):**
  `ofShared()` allows multi-thread access with explicit lifetime; `ofConfined()`
  limits access/close to its owner thread. Neither limits OS file authority nor
  automatically synchronizes a SQLite connection. A worker handoff must not
  outlive or cross the ownership constraints of call-local segments.
- **J2 — [SymbolLookup](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/foreign/SymbolLookup.html):**
  `libraryLookup` ties lookup/library lifetime to an arena. Library loading and
  initializers can themselves have native effects; resolving an exported symbol
  does not sandbox it. Dependency visibility must be audited.
- **J3 — [Linker](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/foreign/Linker.html):**
  `nativeLinker()` and downcall handles implement native ABI calls. Invocation
  stays synchronous; neither offload nor a Future is implicit. Upcalls add a
  separate lifetime/thread/reentrancy surface. FFM memory checks do not make
  arbitrary C memory-safe or filesystem-confined.

### Other systems

| System and primary source | Direct precedent | Extrapolation / absence |
|---|---|---|
| [Python sqlite3](https://docs.python.org/3/library/sqlite3.html) | Connection ownership, thread-use checks, transactions, busy waiting and explicit close | Disabling a same-thread check is not Actor isolation or filesystem confinement. Python semantics are not imported into Protos. |
| [rusqlite Connection](https://docs.rs/rusqlite/latest/rusqlite/struct.Connection.html) | Connection/Statement lifetimes, explicit close, busy timeout and autocommit observation | Rust ownership does not isolate C syscalls or replace the native VFS. |
| [SQLite JDBC](https://github.com/xerial/sqlite-jdbc) | Mature Java/native distribution and platform-library packaging | Native loading/distribution does not establish a bounded data grant. A Java persistence framework is not proposed for Protos. |
| SQLite WASM/OPFS (S11) | Browser-origin storage plus a purpose-built VFS and explicit concurrency limits | Different authority substrate; not a ready Unix-VFS confinement solution. |
| [PostgreSQL process service](https://www.postgresql.org/docs/current/server-start.html) | Separate server process, dedicated OS user, local endpoint and failure/startup administration | Evidence for separation and operations, not proof of a confined SQLite helper or a reason to add a global daemon. OS policy must still restrict the dedicated account/process. |

### Truffle survey and Java eligibility gate

**T1 — GraalPy preferred baseline:**
[`44d2714e40ae5c20371e4d37d7d33057c6caa4c8`](https://github.com/oracle/graalpython/tree/44d2714e40ae5c20371e4d37d7d33057c6caa4c8).
The concrete native-access source supports comparison of mechanisms, not an
assertion that Protos should copy all its available authority or numeric types.

| Mechanism under consideration | Verifiable class / method / annotation | Equivalent behavior and strict limit |
|---|---|---|
| Native lifetime and load/unload boundary | [`runtime.nativeaccess.NativeContext`](https://github.com/oracle/graalpython/blob/44d2714e40ae5c20371e4d37d7d33057c6caa4c8/graalpython/com.oracle.graal.python/src/com/oracle/graal/python/runtime/nativeaccess/NativeContext.java): constructor `@TruffleBoundary`; `loadLibrary` and `close` call `CompilerAsserts.neverPartOfCompilation()` | Native context retains loaded libraries and closes them before its arena. Direct evidence for keeping lifecycle work outside compiled guest paths; no Landlock or exact-grant worker equivalence. Default lookup in that implementation does not authorize adding a Protos fallback. |
| Native symbols | [`runtime.nativeaccess.NativeLibrary.lookupSymbol`, `lookupOptionalSymbol`](https://github.com/oracle/graalpython/blob/44d2714e40ae5c20371e4d37d7d33057c6caa4c8/graalpython/com.oracle.graal.python/src/com/oracle/graal/python/runtime/nativeaccess/NativeLibrary.java), no method annotation | Resolves via its owning native context/library. Evidence for scoped native lookup, not capability enforcement or dependency exclusion. |
| Downcall construction | [`runtime.nativeaccess.NativeAccessSupport.createDowncallHandle`, `libraryLookup`, `createArena`, `closeArena`](https://github.com/oracle/graalpython/blob/44d2714e40ae5c20371e4d37d7d33057c6caa4c8/graalpython/com.oracle.graal.python/src/com/oracle/graal/python/runtime/nativeaccess/NativeAccessSupport.java), no method annotations | Concrete native-access abstraction. The inspected facade delegates backend work; it does not by itself demonstrate thread-affine FFM marshalling or prove equivalence of a new arena implementation. |
| Native cleanup within specialized execution | [`ExternalFunctionNodes.ReleaseNativeArgsTupleNode.doGeneric`](https://github.com/oracle/graalpython/blob/44d2714e40ae5c20371e4d37d7d33057c6caa4c8/graalpython/com.oracle.graal.python/src/com/oracle/graal/python/builtins/objects/cext/capi/ExternalFunctionNodes.java), `@Specialization`; class `@GenerateUncached`, `@GenerateInline(false)` | Specialized native-reference release invokes a native deallocator. Useful custody/transition precedent; reference counting is not copied into Protos as a new resource manager, and destructor confinement is not demonstrated. |
| Foreign-call execution ownership | [`runtime.GilNode.Interop.acquire/release`](https://github.com/oracle/graalpython/blob/44d2714e40ae5c20371e4d37d7d33057c6caa4c8/graalpython/com.oracle.graal.python/src/com/oracle/graal/python/runtime/GilNode.java), no method annotations; factory `create` has `@NeverDefault` | Explicit release/reacquisition around interop avoids some lock cycles. This is a counterexample to blind copying: releasing the Python GIL permits behavior different from the occupied Protos Actor segment. It is not an eligible implementation of hidden Actor reentrancy. |
| Native mutable globals across contexts | [`cext.copying.NativeLibraryLocator` constructor, `resolve`, `replicate`](https://github.com/oracle/graalpython/blob/44d2714e40ae5c20371e4d37d7d33057c6caa4c8/graalpython/com.oracle.graal.python/src/com/oracle/graal/python/builtins/objects/cext/copying/NativeLibraryLocator.java), no method annotations | Relocated native-library copies address extension/context state. This shows why separate managed contexts alone are insufficient. Copying libraries is not selected, not filesystem confinement, and has compatibility/resource cost. |

| Other Truffle system / inspected primary source | Contribution | What it does not demonstrate |
|---|---|---|
| [GraalJS Java interoperability](https://github.com/oracle/graaljs/blob/master/docs/user/JavaInteroperability.md) | Explicit host access/class-lookup configuration controls managed interop exposure | Native SQLite sandbox, complete destructor confinement or an eligible worker algorithm |
| [TruffleRuby thread-safe extensions](https://github.com/truffleruby/truffleruby/blob/master/doc/user/thread-safe-extensions.md) | Distinguishes extension locking, thread-safe declarations and native library thread safety | Actor isolation; its lock release/thread switching must not be copied as Protos reentrancy |
| [Sulong interoperability](https://github.com/oracle/graal/blob/master/sulong/docs/contributor/INTEROP.md) | Native/managed pointers and interop execution have explicit representation boundaries | An arbitrary native-library filesystem sandbox; managed bitcode is a different execution choice |
| [Espresso implementation](https://github.com/oracle/graal/blob/master/espresso/docs/how-espresso-works.md) | JNI/NFI transitions and native-vs-managed execution modes have different isolation properties | Java-context isolation as proof of confined native SQLite; historical mode details are not a current deployment promise |
| [SimpleLanguage SLContext](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.sl/src/com/oracle/truffle/sl/runtime/SLContext.java) | Context-owned environment/resources and interop; a context is not a process-global singleton | A production SQLite confinement/executor design |
| [Apple Pkl SecurityManager](https://github.com/apple/pkl/blob/main/pkl-core/src/main/java/org/pkl/core/SecurityManager.java) | Checks module/resource resolution and secure-path policy | Enforcement over arbitrary C syscalls; useful authority comparison, not a native-executor precedent |

**No complete equivalent for B's Landlock SQLite worker, D's bounded helper
protocol or C's Java SQLite VFS was established by these sources.** This is an
evidence gap, not permission to invent the missing production Java. Existing
infrastructure must remain in use. Before any later Java patch, its author must
map **every** proposed production mechanism to a pinned Truffle class, method,
applicable annotation and equivalent behavior, preferably GraalPy; identify the
semantic differences, thread model and failure paths. A general architecture
analogy, JDK API or OS syscall citation cannot discharge this gate. If equivalent
evidence is absent, that mechanism remains unauthorized even if B is preferred.
Names and comments in future production code must mention neither issues nor
other Truffle languages. Documentation may retain the evidence links.

Apply four distinct evidence classifications to these references:

- **Complete implementation precedent:** verified source covers the proposed
  mechanism's full behavior, ownership, execution, lifetime and failure boundary.
- **Partial mechanism precedent:** concrete methods establish only the identified
  part, such as symbol lookup or native cleanup; they do not establish the whole
  SQLite confinement composition.
- **Architectural analogy:** a comparable boundary motivates analysis but does
  not provide an equivalent implementation.
- **No verifiable precedent:** the required functionality lacks established
  equivalent source evidence and remains unauthorized for production Java.

References to moving Truffle branches alone do not satisfy the production gate.
Retain them as survey references; any future Java mechanism still needs a pinned,
verified class, method, applicable annotation and equivalent behavior, preferably
in GraalPy. The existing partial precedents and analogies do not fill missing
implementation evidence. There is still no established evidence of a complete
SQLite-Landlock worker equivalent in GraalPy. No method or annotation is inferred
to exist merely because the architecture appears plausible.

## 7. Proposed recommendation, falsifiers and future stress

**PENDING APPROVAL: prioritize falsification of B**, preserving the Unix VFS and
moving only irreducible native enforcement/execution into the host. This selects
neither B nor a new Java executor. F remains the actual shipped durable policy:
no persistent acquisition. If a structural condition fails, **D is the preferred
alternative for a new exact approval analysis**. Do not choose C merely to reuse
Filesystem.

B must not be implemented or used to unblock LIB031 until all of the following
have evidence sufficient for the owner:

1. Every relevant operation executes in the restricted domain, including open,
   prepare/reprepare, binding, stepping, column access/copy, failed acquisition,
   finalize, checkpoint, close and teardown; bootstrap trust is explicitly scoped.
2. No session, dependency symbol, alternative provider/alias, native-created thread
   or unrestricted executor can escape or widen the domain. If general FFI stays
   independently unrestricted in that deployment, no confined-profile claim.
3. Destructors and native unloading obey the same authority throughout close,
   cancellation/unwind and shutdown. No cleanup on an unrestricted carrier.
4. WAL, SHM, locks, rollback and recovery remain correct across independent Actors
   and OS processes; native globals do not become an Actor-sharing escape.
5. FFM segment lifetimes, thread ownership and borrowed-copy windows survive the
   handoff without cross-thread access to confined segments or retained raw pointers.
6. Blocking does not introduce Actor reentrancy, hidden Futures, task creation,
   interruption or stronger cancellation promises. Connection safety survives
   ordinary Actor carrier changes without widening native authority.
7. Grant/alias/revocation policy is precise, fail-closed and implementable with
   verified Truffle equivalents under §6. Failure of that precedent gate blocks
   production Java independently of OS feasibility.
8. Deferred connection or native-resource destruction never executes outside the
   originally authorized restricted domain. A zombie connection after
   `sqlite3_close_v2` returns `SQLITE_OK` does not discharge this obligation;
   deferred destruction outside that domain invalidates B.
9. Confining an independent grant does not require restricting the whole JVM.
   Such a requirement invalidates the proposed per-grant isolation, including
   when process-wide restriction is applied with TSYNC.

The strongest argument **against** B is structural: a thread-restricted native
executor inside a JVM still shares memory, FDs, native globals and unrestricted
threads. If the required threat model includes arbitrary malicious native code,
or if Unix-VFS identity cannot be bound securely without modifying the VFS, the
proposed worker is insufficient. A process boundary may be necessary despite its
larger present cost. Passing a path-denial demo cannot resolve that objection.

### Incremental-design answers

The smallest safe capability today is F plus the existing memory library. The
current durable use case justifies researching one bounded native authority
boundary; it does not justify a pool, ORM, generic persistence host, multi-OS
framework, application journal or duplicate transaction engine. A proves that
small implementation size alone is insufficient under the confinement invariant.

Durability can be added later without changing `openMemory` or native-handle
transfer rules. Deferral requires adding approved acquisition/policy, native
enforcement and acceptance evidence; it does not require rewriting the Core
object/message model. The cost paid now is consumer delay, including the blocked
durable application work. Publishing ambient filenames now would instead force
later authority/API migration. Keep authority and transport private to avoid it.

What would cause regret now: per-grant threads for thousands of barely used DBs,
an IPC protocol before in-process feasibility is known, or implementing a VFS to
reuse an asynchronous protocol whose locking guarantees are insufficient.
What would cause regret later: B cannot isolate a newly required untrusted native
extension or cannot maintain bounded resources with many grants. Escape path:
deny that new capability, retain existing memory behavior, and submit D (or a
separately proved platform E backend) for approval while preserving Protos policy.

| Future scenario | Stress / escape path |
|---|---|
| Many Actors, Tasks, grants or long-lived databases | No global serialization of unrelated grants; bound queues, connection count, retained native resources and worker cost. If per-grant threads do not scale, approve a different host implementation; do not merge authority domains. |
| Large queries, big results or lock contention | Keep declared limits in Protos and native status honest. Busy waiting occupies the synchronous segment; reject overload rather than add hidden asynchrony. |
| Multiple Contexts / Protos Processes in one JVM | Audit shared `.so` state and cleanup independently of Context identity. Never use a process-global mutable registry as public Actor state. |
| Multiple OS processes / distributed deployment | Native locks must interoperate locally. No transfer of live native handles and no WAL-over-network promise; remote DB services require a separate explicit contract. |
| Cancellation, failure or unwind | Preserve occupied segment and original dynamic state; retire once. A non-returning C call may stall the Actor; D can contain crashes but cannot erase commit uncertainty. |
| Alternative Actor schedulers | No carrier identity as authority. Backend affinity remains private and must survive migration without Protos interleaving. |
| Native Image / AOT / other OS | Current provider promises only the verified JVM profile. New loading/linkage/confinement needs separate evidence; fail unsupported rather than fall back. |
| Truffle partial evaluation / Bytecode DSL evolution | Keep ordinary optimized message operations and existing transition infrastructure; lifecycle/native policy remains at explicit boundaries. No new dispatch path or deoptimization scheme is authorized. |
| Alternative library or host runtime | Keep Connection/Statement policy and copied values in Protos; replace only the approved host mechanism. Persistence format remains SQLite-owned. |
| Future serialization / new guest features | A durable file is data, not serialized Actor/native identity. No callback/extension feature silently acquires more authority. |

## 8. Reproducible falsification specification — not executed

This is an experimental protocol, **not a spike implementation**. Before writing
even experimental Java, satisfy the mechanism-level precedent gate or return to
the owner; this document does not waive it. Use an isolated disposable test
environment provisioned by the human, never production data. Implementing and
running this experiment are later work, not commands executed for this slice.

Fix a test-owned directory `allowed`, a separate sibling `outside`, a selected
database `allowed/main.db`, two independently provisioned grants and a local
filesystem with known sync/locking behavior. Seed `outside` with recognizably
different content. Include a trusted unrestricted control process so a denial
cannot be mistaken for missing OS permissions or absent test input. Treat any
unsupported setup as **UNSUPPORTED**, never PASS. Use deterministic barriers
around native entry/return/close; do not infer ordering from sleeps.

Record exact product/provider revision and changed bytes, SQLite version/source
ID/compile flags, actual VFS/threading mode, URI/extension settings, journal mode,
synchronous setting, page size, timeout/limits, JDK/GraalVM build, OS/kernel,
queried Landlock ABI and handled rights, CPU architecture/ABI, filesystem/mount
options, directory ownership/modes, umask, inherited descriptor inventory,
library/dependency identities and grant identity/scope. Unknown values prevent a
reproducible acceptance claim. Observation must attribute native events to real
OS threads/processes, not merely show the Java submission thread.

For every case capture native result/error, relevant syscall paths/FDs/locks and
thread/domain attribution, before/after database and exterior state, lifecycle
order and actual transaction state when safely observable. Raw tracing is future
test evidence with controlled access, not guest diagnostics. No logs, temporary
directories, fixtures or audit manifests are created by this documentation slice.

| Case | Preparation | Operation | Expected result | Failure condition | Evidence to capture |
|---|---|---|---|---|---|
| 1. Authorized open | Empty approved DB scope, fixed native library | Acquire, create table, insert bound i64/double/TEXT/BLOB/NULL, close and reopen | Correct independent copied values; only approved resources | Wrong target, wider access or lost values | Grant identity, full native entry/close trace, reopened rows |
| 2. WAL / SHM | Same scope; second independent connection held open | Enable candidate WAL settings; write and read concurrently | Native sidecars and shared locks operate within grant | Exterior sidecar, lock emulation or disabling WAL silently | Actual mode, sidecar paths/mappings and lock events |
| 3. Exterior paths | Existing exterior DB and writable exterior parent; unrestricted control succeeds | Try absolute, relative traversal and parent-replacement races | Refusal without exterior access/effect | Any unauthorized successful read/write, including tentative creation | Control result, denied syscalls and unchanged exterior content |
| 4. Exterior ATTACH | Authorized connection and exterior DB | Prepare/step ATTACH with direct and bound destination forms | Refusal at effective boundary; existing connection stays valid as contract permits | Exterior DB acquired or information exposed | Prepare and step events, database list/status, exterior observation |
| 5. Exterior VACUUM INTO | Populated authorized DB, absent exterior output | Attempt exterior output and raced parent replacement | Refusal without exterior file creation | Even a transient unauthorized output | Creation/deletion trace, statuses and external directory state |
| 6. URI escapes | Recorded URI build/global flags | Try encoded traversal, `vfs`, `nolock`, `immutable`, alternate authority names and ATTACH URIs | No policy/lock/authority bypass; denied unsupported options | Option creates broader scope or weaker locking | Effective URI interpretation, VFS selection, locks and paths |
| 7. Links / mounts / rename | Exterior DB aliased by symlink/hardlink; bind-mount and parent-swap cases where supported | Race open, journal creation and checkpoint against namespace changes | Reject unsafe identities/layouts or prove exact approved authority | NO_SYMLINKS claimed to reject hardlink; exterior effects through aliases | Inode/mount/parent identity from trusted observer and event sequence |
| 8. Preopened FDs | Control opens exterior FDs before restriction; enumerate permitted inheritance | Attempt reuse/duplication through every reachable native/provider interface | No reachable unauthorized FD effect; otherwise B falsified for that scope | Reliance on Landlock retroactive revocation | FD provenance, cross-thread availability and observed effects |
| 9. Two Actors | Distinct Actor-local connections, explicit grants; include disjoint-grant run | Overlap operations; migrate Actor carriers; attempt handle transfer and alternative FFI acquisition | Local identities remain isolated; forbidden transfer rejected; no grant union | Shared mutable handle/config state, reentrancy, unrestricted route | Actor segment trace, OS-thread attribution, session/grant generations |
| 10. Two OS processes | Independently started hosts, same approved DB; run rollback and WAL modes | Overlap read/write transactions and reopen each side | Correct native locking/visibility under chosen profile | JVM-only locking, incompatible sidecars or lost committed rows | Cross-process locks, transaction ordering and reopened values |
| 11. Rollback / commit / checkpoint | Known records; deterministic transaction boundaries | Roll back one change, commit another, force checkpoint; inject COMMIT refusal | Rollback changes absent; acknowledged commits meet the chosen persistence profile; unobserved commits may persist; actual state checked after errors | Wrapper assumes failed COMMIT always ended transaction or replays it | Status, autocommit, checkpoint result and reopened records |
| 12. Busy / deadlocks | Independent process holds write lock; chosen finite busy policy | Contend and release at a barrier; also keep lock beyond timeout | Declared busy outcome, no hidden Actor interleaving or automatic replay | Unbounded queue, invented cancellation, monitor/worker cycle | Timing as observation, native lock/status and Actor trace |
| 13. Close in flight / domain lifetime | Hold an entered native call at an external deterministic barrier; prepare a separate native lifecycle case with pending statements | Start host/session close, attempt new admission, then release the call; in the separate case invoke `sqlite3_close_v2` with pending statements and attempt domain close before finalizing them | New work refused; admitted call drains; `SQLITE_OK` may leave a zombie connection; domain termination cannot precede pending native destruction | Use-after-close, duplicate destructor, or domain termination based only on API success while dependent cleanup remains | Separate public-retirement/API-return, zombie interval, dependent-finalization, physical-destruction and domain-termination events |
| 14. Finalization / FFM shutdown | Leave statements live; include failed acquisition/publication and destructor failure; retain a zombie connection after `sqlite3_close_v2` returns `SQLITE_OK` | Exercise explicit close and separate session shutdown; later finalize pending statements, attempt premature domain close and repeat release attempts | Existing custody retires once; final statement cleanup triggers deferred connection destruction under the original restriction; API success remains distinct from physical destruction | Cleanup outside the original domain, early domain termination, leaked live authority or retried uncertain destruction | Per-resource counters/generations, actual final-destruction thread and its effective restriction, dependent-finalization and domain-termination order |
| 15. SIGKILL / reopen | Committed baseline and separate uncommitted work; rollback and WAL runs | Kill OS host/helper before, during and after COMMIT acknowledgement; reopen | SQLite recovery, no partial transaction; unacknowledged commit may be uncertain | False rollback certainty, silent replay or invalid recovered state | Kill point, acknowledgement boundary, recovered rows and integrity result |
| 16. Full disk / corruption / power failure | Disposable capacity-limited storage and separate corrupted DB copies; fault-capable storage harness for power-loss run | Fail writes/sync/COMMIT; reopen; separately simulate torn/lost writes | Honest errors, no fabricated durability or automatic repair; exact selected profile assessed | Failure reported as success or SIGKILL mislabeled power-loss proof | Injected fault boundary, SQLite status/state, recovered data and integrity result |
| 17. Unsupported confinement | Missing/disabled/insufficient Landlock ABI, refused privilege setup, unsupported OS/architecture | Request durable provisioning; separately exercise memory profile | Durable grant refused before acquisition, no fallback; memory behavior preserved where supported | Trusted ambient route silently substituted or unsupported counted PASS | Provisioning result, zero durable-native entries, independent memory outcome |

Add focused adversarial subcases for extension loading, dangerous PRAGMAs during
prepare, schema-triggered reprepare, native global configuration, cancellation
during busy/COMMIT and library initializers/unloading. They inherit the same
required evidence fields. Network-filesystem configurations must be rejected by
the initial proposal; demonstrating a local filesystem does not support them.

The zombie subcases in 13 and 14 exercise the native lifecycle separately from
the public wrapper's normal child-first close; they do not add an API that leaves
public statements usable after connection retirement. A `SQLITE_OK` observation
alone cannot mark physical-destruction coverage successful. Observe the final
dependent cleanup and the resulting destruction thread's effective restriction
before declaring the domain safely terminated. These remain unexecuted future
tests using the existing custody mechanism.

Stop at the first security/invariant failure and retain its counterexample for
owner review. A timing measurement is not a benchmark conclusion. Acceptance
requires both source-level coverage proof and observed denial/correctness, plus
the unchanged memory regression set; successful happy paths alone cannot prove
no escape. No scenario in this section has a reported PASS.

## 9. Future implementation responsibilities

| Owner | Responsibilities | Excluded duplication |
|---|---|---|
| Ordinary Protos library | Connection API, transaction protocols, parameters, option validation, resource state, error mapping, public policy, declarative limits, autocommit observation and interpretation | No new Core Storage, `std:db`, ORM, application journal or raw ABI mirror |
| Host / platform | Capture authentic authority, enforce native confinement, safe C execution, irreducible custody and physical session control, admission/revocation and shutdown routing | No Java transaction engine, connection-policy framework or second resource manager |
| SQLite | Native locks, WAL, rollback journals, recovery, checkpoints and persistence guarantees of the configured VFS/storage | No Java reimplementation of SQLite atomicity or application-level shadow WAL |

Keep existing Protos resource/FFI lifecycle mechanisms. Any unavoidable host delta
must be individually justified and backed by §6; a diagram or worker name is not
implementation provenance. No new production names, annotations, scheduler,
fallback, callbacks or persistent stubs are introduced here.

## 10. Exact contract questions pending owner approval

These are explicit decisions to make before the dependent public API or backend;
no final `std:sqlite` signatures are invented.

| Decision | Proposed choice to evaluate | Alternative / approval consequence |
|---|---|---|
| Threat model | Trusted fixed SQLite binary; untrusted SQL/options; no claim against arbitrary same-JVM malicious C | If malicious native code must be contained, B's shared process is inadequate; analyze D |
| Grant identity | Host-authentic immutable acquisition tied to owner/session and fixed root identity | Decide whether identity denotes one database resource or an authorized directory namespace; a path string is neither |
| Directory vs file | Dedicated host-controlled DB directory including required sidecars/temp scope | Exact-file scope needs proof for creation/recovery/aliases without modifying Unix VFS; otherwise reject |
| Open authority | Explicit host provisioning, exact read/write/create policy and safe namespace selection | Filesystem possession/import does not automatically authorize durable SQLite; no ambient fallback |
| Auxiliary databases | Initially deny guest-selected ATTACH/VACUUM destinations, extension loading and URI/VFS switches | Allowing them requires explicit additional authority and tested side-effect policy |
| Journal / sync | Explicit bounded options with read-back verification; only profiles with documented recovery guarantees | Owner must choose allowed/default journal and synchronous settings; no universal power-loss promise |
| Timeouts / cancellation | Preserve synchronous occupied segment; finite declared busy waiting, no implicit retry | Select exact wait bounds and whether any separately specified interrupt mechanism exists; busy timeout is not total-call deadline |
| Commit uncertainty | Distinguish known SQLite status/state from unobserved native outcome; never replay automatically | Approve exact public error/result representation and recovery guidance; no new error family ratified here |
| Close / revocation | Cut new admission, drain entered work, destroy once under original authority; handle remains retired on failure | Decide pending-work outcomes, residual effects, indefinite-call policy and scope of revocation; never claim FD/mmap retroactive revocation |
| FFM provider governance | Confined deployment must prevent every independently unrestricted acquisition/binding path | Decide host policy and compatibility with R1 trusted deployments; any changed public R1 contract needs separate approval |
| Supported platforms | Initially consider only existing Linux x86-64 LP64 JVM/JDK25 profile with effective tested confinement | Decide required Landlock rights/ABI and filesystem allowlist; reject unproved OS/ABI, Native Image and network storage |
| Mechanism eligibility | B falsification first; all Java deltas require equivalent verified Truffle source | If required mechanism lacks precedent, withhold it and return to owner; preference cannot waive the gate |

Approve the mechanism, grant, failure and platform contract explicitly, then
reconcile the normative owners through D256. Approval of this packet's research
direction alone does not approve any unresolved choice in this table.

## 11. Consequences, invariant reconciliation and handoff

| Existing invariant | Preservation in this packet |
|---|---|
| SQLite-specific Protos-first direction (D256-C1) | All six candidates retain ordinary library policy; generic storage/Java persistence machinery excluded |
| Unix VFS objective | A/B/D preserve it; C requires explicit reconsideration, E platform scope is honest, F changes nothing |
| Existing memory behavior | Only explanatory comments and guide text change; all bindings and executable source expressions retained |
| Explicit authority and no native bypass | Proposed durable boundary includes alternative FFI routes, destruction and initialization trust; no grant is shipped |
| Actor isolation / synchronous semantics | ACTORS §§24J–24L retained; no hidden interleaving, resource transfer or new Process meaning |
| Existing Truffle machinery / primitive representations | No Java changes or new execution paths; future mechanism-level provenance remains mandatory |
| Owner decision authority | PLAT074 OPEN, D256 pending final reconciliation, LIB031 BLOCKED; LIB029/LIB030 behavior preserved |

What approval of each alternative could authorize, **after its exact public
contract and Java evidence gates are satisfied**:

| Alternative | Bounded future implementation scope | Specific remaining evidence |
|---|---|---|
| A | Explicitly weak-trust host-provisioned durable wrapper only | Owner must accept ambient C authority; cannot label it confined |
| B | Minimal native execution/enforcement delta plus ordinary Protos durable policy | §7 coverage/escape/custody proof, §8 falsification, exact Truffle equivalents; no general Java worker authorized now |
| C | Separately justified full VFS/native-callback work | Need to reopen Unix-VFS objective, prove full sync/locks/SHM/recovery and obtain eligible Truffle callback precedent |
| D | Confined helper plus bounded host-private transport and Protos policy | Exact OS confinement, protocol/authentication/generations/commit uncertainty, packaging and Truffle mechanism mappings |
| E | One named platform/backend at a time | Platform-specific authority, loader/descriptor/VFS behavior and equivalent Truffle implementation evidence; no portability equivalence assumed |
| F | Keep memory scope and defer durable implementation | No durable implementation unlocked; track consumer delay and trigger re-evaluation when evidence changes |

No external repository or GitHub state is modified. Durable ratification/work
records remain in `guillermomolina/protos-project-docs`; this requested guide
packet is not a substitute for publishing those records after the exact product
revision exists. No product version/spec revision is assigned: the source delta
is comments only and the FFI addition is an approved-contract scope clarification,
not new semantics.

Material instruction files inspected: the supplied root `AGENTS.md`,
`AGENTS.work/DESIGN.md`, `AGENTS.work/IMPLEMENTATION.md`,
`AGENTS.work/DOCUMENTATION.md`, `AGENTS.work/REFERENCE.md`, `spec/AGENTS.md`
and `protos/AGENTS.md`. They require owner approval, normative authority before
implementation, minimal edits, human execution and retained APL notices. The
material implementation/specification inspection is enumerated in §2; the guide,
`LICENSE.TXT` Part 5, D256 records, blocker registry and primary sources in §6
complete the task-specific evidence. The owner's clarification authorized
read-only external research and confirmed no pre-existing work to preserve.

Validation handoff: **not run**. The next cheap human check is
`git diff --check`, followed by reviewing the exact five owned paths (including
this newly created file). The supplied task forbids agent Git/tests/builds/scripts;
none is claimed successful. No formatter/compiler/test execution is needed to
establish a comments-only behavior delta, and no such run is claimed. A later
executable implementation requires its full affected validation and spike
evidence. The original APL Part 5 notices remain intact in both modified Protos
files; `LICENSE.TXT` exists and matches their notice text. No third-party material
or packaging metadata was modified.

## 12. Executable Landlock/SQLite feasibility evidence

PLAT074-B1 adds a test-only executable experiment in
[`ProtosPlat074SqliteLandlockFeasibilityTest.java`](../../../src/test/java/com/guillermomolina/protos/execution/integration/filesystem/ProtosPlat074SqliteLandlockFeasibilityTest.java).
The owner's explicit B1 request authorizes this bounded experiment, superseding
§8's earlier deferral of experimental Java. It does **not** waive the
mechanism-level Truffle/GraalPy precedent gate for future production Java,
select B, resolve D256, unblock LIB031 or establish a durable SQLite API.
The original packet's execution statements refer to that earlier packet.

**Current evidence: 13 observed PASS results**, reported by the project owner
on 19 November 2026. At the time this experiment was authored, all cases were
NOT RUN; the new execution evidence does not alter historical statements in
the earlier sections. Results apply only to each case's bounded hypothesis.
The outcome classes are:

- **PASS:** observed behavior satisfies the specific, bounded hypothesis.
- **FAIL:** observed behavior contradicts that hypothesis. Preserve the
  counterexample and investigate before claiming architectural feasibility.
- **UNPROVEN/UNSUPPORTED:** evidence is insufficient or the environment cannot
  execute the case. JUnit assumption aborts/skips are UNSUPPORTED, never PASS.
  A missing child launch/barrier or incomplete cleanup cannot establish success.

### Cases and architectural questions

All method names refer to the linked class. Individual execution outcomes from
the owner's report are recorded below; the architectural questions and limits
remain in the subsequent table.

| JUnit method | Observed outcome reported on 19 November 2026 |
|---|---|
| `t1NativeSqliteOpensOnlyAfterRestriction` | **PASS (observed)** |
| `t2ExteriorReadAndWriteHaveUnrestrictedPositiveControls` | **PASS (observed)** |
| `t3WalAndShmSupportIndependentConnections` | **PASS (observed)** |
| `t4AttachCannotModifyExistingExteriorDatabase` | **PASS (observed)** |
| `t5VacuumIntoCannotCreateExteriorOutput` | **PASS (observed)** — end-state hypothesis only; no complete syscall tracing. |
| `t6PreopenedExteriorDescriptorRemainsUsable` | **PASS (observed)** — demonstrates continued exterior FD use, a security limitation, not complete confinement. |
| `t7TwoSimultaneousGrantsDenyEachOthersPaths` | **PASS (observed)** |
| `t8BusyIsSqliteStatusWithFiniteBusyWait` | **PASS (observed)** |
| `t9CloseAndCloseV2HaveDifferentPendingStatementLifetimes` | **PASS (observed)** |
| `t10DomainFinishesOnlyAfterNativeResourcesAndCallArenaClose` | **PASS (observed)** |
| `t11RestrictionDoesNotSpreadToConcurrentUnrestrictedThread` | **PASS (observed)** |
| `twoOsProcessesRollbackJournalLockVisibilityAndKillRecovery` | **PASS (observed)** |
| `twoOsProcessesWalShmLockVisibilityAndKillRecovery` | **PASS (observed)** |

| JUnit method | Architectural question and instrumented evidence |
|---|---|
| `t1NativeSqliteOpensOnlyAfterRestriction` | Can C SQLite open/create through its default VFS after Landlock installation? Create, insert and read a signed 64-bit value, close, and observe the database file. The authorized file is never opened during warm-up. |
| `t2ExteriorReadAndWriteHaveUnrestrictedPositiveControls` | Are new exterior read-only and read/write acquisitions rejected? Seed and successfully access the same resource without restriction first; expect `SQLITE_CANTOPEN` inside and compare exterior bytes afterward. |
| `t3WalAndShmSupportIndependentConnections` | Can real WAL/SHM and independent connections operate under the existing policy? Verify committed rows through another connection, hold both open at a barrier, and observe nonempty `main.db-wal` and `main.db-shm` inside the grant. A successful PRAGMA alone is insufficient. |
| `t4AttachCannotModifyExistingExteriorDatabase` | Does the OS boundary reject an existing exterior ATTACH target without an authorizer? Check SQLite refusal, the remaining database list and unchanged exterior bytes/directory. |
| `t5VacuumIntoCannotCreateExteriorOutput` | Does exterior VACUUM INTO fail with no output remaining? Establish exterior directory writability, start with an absent target, inspect SQLite status and the empty exterior directory afterward. Transient create/unlink absence still requires syscall tracing; it is UNPROVEN by end-state observation alone. |
| `t6PreopenedExteriorDescriptorRemainsUsable` | Does restriction revoke previously acquired descriptors? Separately deny new SQLite opens, then read and overwrite one byte through a preopened exterior read/write FD using the existing native bridge. Expected continued FD use demonstrates a sandbox limitation, not satisfactory security confinement. |
| `t7TwoSimultaneousGrantsDenyEachOthersPaths` | Can distinct domains coexist? Two overlapping platform threads each create their own database, then reject read/write opens of the other's existing database. No JVM-global restriction is installed. |
| `t8BusyIsSqliteStatusWithFiniteBusyWait` | Can native contention be reported honestly? Hold BEGIN IMMEDIATE, configure a 100 ms busy timeout, assert native `SQLITE_BUSY` and autocommit states, roll back and demonstrate subsequent write success. Java exceptions or the harness wait bound do not count as BUSY. |
| `t9CloseAndCloseV2HaveDifferentPendingStatementLifetimes` | Can dependent cleanup remain in-domain? Observe close returning BUSY with a statement, finalize and close normally; separately retire with close_v2, use only the outstanding statement, then finalize it before thread termination. No connection pointer is used after close_v2. Its OK result does not prove immediate physical deallocation. |
| `t10DomainFinishesOnlyAfterNativeResourcesAndCallArenaClose` | Does the fixture finish its cleanup before reporting domain completion? Close tracked statements/connections, close the thread's arena, join the platform thread and check it is dead. Unknown native cleanup or a retained thread fails the harness. |
| `t11RestrictionDoesNotSpreadToConcurrentUnrestrictedThread` | Is installation local to the calling OS thread? Keep it alive while the unrestricted JUnit thread creates an exterior database; subsequently deny both open modes on the restricted thread. This enables distinct grants and simultaneously demonstrates why every alternative native execution route must be excluded in a secure integration. |
| `twoOsProcessesRollbackJournalLockVisibilityAndKillRecovery` | Do Unix VFS locks, commit visibility, orderly reopen and killed-writer recovery work across OS JVM processes in DELETE mode? Barriers hold an uncommitted writer while the other observes BUSY; verify acknowledged commits, kill another uncommitted writer, read/rewrite from the survivor and reopen independently. |
| `twoOsProcessesWalShmLockVisibilityAndKillRecovery` | Does that cross-process protocol also work with WAL/SHM? Repeat it in WAL mode and inspect live sidecars at the writer barrier. Both processes open SQLite only inside their own restricted thread. |

The process fixture accepts exactly `peer <fixture-directory>`, and a fixed
stdin command vocabulary; it does not accept guest SQL or provide a reusable
service. The parent uses the running JDK's `java` and Surefire's test classpath,
falling back to the ordinary Java classpath. BOOT/READY, HELD, BUSY, COMMITTED,
row observations and CLOSED are explicit pipe barriers, with bounded waits and
no ordering sleeps. CLOSED follows native cleanup, arena closure and domain
termination. Each wait/join is bounded at 20 seconds; busy waiting is separately
configured at 100 ms. Exceeding a harness bound fails the experiment, not a claim
that SQLite supports whole-operation deadlines. SIGKILL is sent only after HELD;
Linux exit status 137 is checked. Failure cleanup may kill a test subprocess,
but never forcibly interrupts a native call in the parent JVM.

### Environment and lifecycle assumptions

The experiment requires Linux x86-64 LP64, JDK 25 FFM/native access, an available
Landlock ABI at least 3 with successful installation, a local filesystem with
working SQLite Unix VFS locking/shared memory, and loadable `libsqlite3.so.0`.
SQLite must be at least 3.27 for VACUUM INTO, support WAL and have a nonzero
`sqlite3_threadsafe()` result. That result describes the library build, not a
proof of all runtime configuration or safe sharing of a connection. Tests never
share connection pointers between threads. Missing admission primitives,
library/symbols or these build requirements abort admission; child startup or
post-admission failures remain visible failures requiring diagnosis. Restricted
classpath-loading failures are not silently retried outside confinement.

The fixture loads and links only the thirteen requested SQLite functions before
Landlock using `Linker.nativeLinker`, `SymbolLookup.libraryLookup` and an
`Arena.ofShared`. It warms adapters with `:memory:`; ordinary authorized database
acquisition still occurs after successful `restrictCurrentThreadBeneath` on a
fresh platform thread. Per-thread strings/output slots use `Arena.ofConfined`.
No confined-arena segment or database/statement pointer is returned to JUnit.
The existing Native bridge and TestSupport admission checks are reused; the
Filesystem worker driver never executes SQLite. Native ABI int carriers remain
C int; copied SQLite integer values use Java long, with no Protos representation
change.

Statements and connections are released inside their original domain, including
failed-open handles and the final dependent statement after close_v2. Cleanup
errors fail the case; the shared library is retained if a native thread remains
alive or tracked native cleanup is unresolved. Threads are daemon fixtures so a
stuck C call cannot prevent JVM shutdown; that is failed/incomplete cleanup,
never PASS. The shared library arena closes on the unrestricted owner only after
all domains terminate normally. Library load/unload, initializers, destructors,
untracked SQLite internals and process-global state are consequently **not**
proved confined. These tests provide no allocator-level observation of physical
connection deallocation; dependent finalization is the bounded lifecycle evidence.

The preopened-FD case proves only continued positioned read/write on one regular
file opened read/write before restriction. It proves neither universal FD
usability nor revocation of inherited mappings, descriptors or native globals.
The child protocol itself deliberately uses preopened stdin/stdout descriptors.
The fixtures do not enumerate all inherited descriptors or trace all syscalls.
Default VFS behavior is exercised without a custom VFS; exact compiled VFS,
SQLite source/build flags, kernel/mount configuration and native dependency
identity still need recording for reproducible architectural acceptance under
§8. No result here certifies network filesystems or other platforms.

### Three separate approval obligations

**Physical feasibility:** SQLite C can use Unix VFS, WAL, SHM and locking on a
restricted OS thread. The owner's reported execution supplies bounded physical
evidence for the cases above; it does not cover every adversarial §8 scenario.

**Integration feasibility:** the FFM provider, Actors, ownership and resource
lifecycle can guarantee that acquisition, binding, every call, dependent cleanup,
shutdown and all permitted initialization/destruction execute in the correct
domain while preserving synchronous Actor segments. This fixture bypasses the
provider intentionally and leaves that obligation **UNPROVEN**. It proves no
exact FfmSession equivalence, carrier migration policy or complete GraalPy worker
precedent. Production Java changes still require concrete Truffle implementation
precedents, preferably GraalPy, before implementation.

**Security confinement:** no accessible native route can bypass restriction under
an explicitly approved threat model. This remains **UNPROVEN**: unrestricted
threads retain authority, preopened FDs remain usable, arbitrary same-JVM C can
corrupt memory, and the experiment does not govern the existing unrestricted FFM
surface. SIGKILL recovery is not power-loss durability evidence.

B becomes a candidate for approval only when **all three** properties are
justified, including explicit threat-model limitations. Physical feasibility
alone never authorizes production. Successful exterior acquisition/effects,
incorrect cross-process locking or lost acknowledged commits, cleanup required
outside the proper domain, broken Actor isolation, an unavoidable accessible
unrestricted native path, or a threat model requiring containment of arbitrary
malicious same-process native code would invalidate the corresponding B claim.
Unsupported platform/build admission leaves feasibility UNPROVEN there; it is
not evidence of portable support.

### Owner-reported execution evidence — 19 November 2026

The original B1 slice added only the test file and this guide, with no normative
specification, production Java, provider, library module, build configuration,
dependency or version changes. Its execution handoff was initially NOT RUN.
This subsequent update changes only this guide and records the owner's report;
the agent has not executed or independently repeated these commands.

| Command or metric | Owner-reported evidence |
|---|---|
| `mvn -DskipTests test-compile` | **BUILD SUCCESS** |
| `mvn -Dtest=ProtosPlat074SqliteLandlockFeasibilityTest test` | **BUILD SUCCESS** |
| Tests run | 13 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |
| Focal duration | 2.242 seconds |
| Exact tested revision | Not recorded in the supplied evidence |
| Kernel | Not recorded in the supplied evidence |
| Landlock ABI | Not recorded in the supplied evidence |
| SQLite flags | Not recorded in the supplied evidence |

The missing revision and environment details limit reproducibility; they are
not inferred from the fixture's admission requirements. No integrated-suite,
dedicated static-check or complete syscall-tracing result was supplied. The
reported compilation and focal results do not imply those checks passed.

**PLAT074-B1 demonstrates bounded physical feasibility of SQLite Unix VFS under
Landlock. It does not yet demonstrate integration feasibility or complete
confinement of the FFM provider.**

FfmSession/Actor integration remains unproven; native loading and destruction
outside the domain have not been ruled out, alternative FFM routes remain
uncontrolled, complete syscall tracing is absent, and no power-loss test was
performed. T6's observed PASS explicitly confirms a security limitation rather
than removing it. **PLAT074 remains OPEN, D256 remains pending final
reconciliation, and LIB031 remains BLOCKED.** No production approval, normative
ratification or work-item closure follows from these results.

The APL notice and licensing/packaging configuration are unchanged by this
documentation-only update. External decision records and the durable blocker
ledger were not consulted or modified in this session. No remote state is
inferred. Before executable closure, the broader `make test` gate remains
pending reported evidence under repository policy. Whitespace/diff review
remains human-executed under the session's Git ban.

## 13. Real-provider confinement experiment — PLAT074-B2

**B2 owner-reported execution: 17 tests, 14 passed, 2 failed, 0 errors,
1 skipped**, on 19 November 2026, before the T6 correction below. The corrected
T6 subsequently **PASS observed** in a separate focal run on the same date:
1 test, 0 failures, 0 errors, 0 skipped, 2.233 seconds. This does not establish
a successful complete-suite run. B1 remains **13/13 PASS observed**, exactly
as recorded in §12.
No production Java, Actor scheduling/segments, primitive representations,
provider authorization, B1 test, or normative specification is changed.
**PLAT074 remains OPEN, D256 pending final reconciliation, LIB031 BLOCKED.**

The experiment extends the existing
[`ProtosI144FfmFixtureAbiTest`](../../../src/test/java/com/guillermomolina/protos/execution/foreign/api/ProtosI144FfmFixtureAbiTest.java)
and its dynamically compiled fixture registration of the actual `providers/ffm`
backend. It reuses `ProtosExternalForeignProviderTest.Fixture`. All fixture ABI
declarations and resource composition live in
[`ffm-confinement-probe.protos`](../../../protos/tests/foreign-provider/ffm-confinement-probe.protos),
using `std:ffi`, `ffi-test:ffi_test`, `FFI.bind`, `FFI.owned`, `FFI.out`,
`FFI.release` and existing public descriptors. The Java host invokes its
source-backed Closures through the existing entered Process Context and
`ProtosRootTaskExecution.executeClosure`, with no replacement execution engine.
Each acquired bridge is checked to contain the dynamically loaded `FfmSession`.
Evaluating the Protos source prepares only the authority-free phase closures.
`acquireLibrary`, `bindThreadId` and `invokeBound` provide separate completed
phase boundaries for T3; `prepare` composes the other probes before restriction.
No new fixture ABI declaration is embedded in the Java experiment.

### Separate obligations and observed results

| Obligation | Required evidence | B2 result |
|---|---|---|
| Confined FFM downcall | C TID equals the restricted platform thread's Linux TID; exact exterior positive controls become Landlock denials; real `FfmBinding.invoke` frame | T1/T2 PASS observed, within their bounded hypotheses |
| Library and symbols | Actual load/binding authority and timing, separately from host/class preparation and invocation | T3 acquisition/binding cases PASS observed; restricted host initialization skipped/UNPROVEN |
| Explicit release | C destructor on the authorized thread/domain, unchanged destructor result and exactly-once custody | T4 PASS observed |
| Session close | Deferred C destructor remains in the authorized domain when another host thread closes the real session | T5 FAIL observed: restricted TID 105669, destructor TID 105670 |
| Actors | Distinct sessions, rejected live-handle transfer/use, and no native route broadening the supposed grant | T7 PASS as a counterexample/control; unrestricted native access remains possible; Actor non-reentrancy is not established by T6 |
| Host and general FFM | A host policy eliminating accessible native routes with greater authority than the durable grant | T8 PASS as a native-route control; the future exclusion policy remains UNPROVEN and unimplemented |
| Lifecycle | Zero duplicate destruction and no premature release during an admitted native call | T4/T9 PASS observed; original T6 failed its invalid second-entry check; corrected T6 PASS observed for admitted C entry, close waiting, no premature destruction and exactly-once cleanup |

### Executable cases

All methods below belong to the existing linked JUnit class. Their initial
NOT RUN state is superseded by the owner-reported runs below. The corrected T6
passed separately; the complete class has not been reported successful.
JUnit failures, skips and recorded counterexamples remain distinct from
successful confinement.

| Method | Bounded hypothesis or observation | Result |
|---|---|---|
| `b2T1NativeTidMatchesThePhysicalCallerAndActorCarrier` | Independent host `gettid` versus C TID, on JUnit's thread and a dedicated platform thread running the same Actor session. The direct root segment has no suspension. | PASS observed |
| `b2T2RealProviderDowncallMustRemainInTheLandlockDomain` | Install `restrictCurrentThreadBeneath` on an authorized root FD, then read/create inside, deny read/create outside, preserve an in-grant ENOENT control, match all C TIDs and witness `FfmBinding.invoke` while C is pending. | PASS observed |
| `b2T3ColdAcquisitionAndPreparedBindingHaveSeparateAuthority` | A unique exterior library copy cannot be acquired under restriction. Require the provider's `library-unavailable` category, not an arbitrary import/class-loading error. Acquire outside as a separate control; bind a previously unbound root-session symbol and invoke it under restriction. Another Actor warms binding classes first. | PASS observed |
| `b2T3GrantLocalLibraryLoadAndBindingRunAfterRestriction` | A separate library copy lies inside the grant; acquisition, binding and native TID invocation start only after restriction. Host/bootstrap and the empty session are prepared beforehand. | PASS observed |
| `b2T3HostInitializationInsideRestrictionIsAnIndependentExperiment` | Attempt host/bootstrap and authority-free `std:ffi` initialization inside the domain. A refusal is reported as requiring preparation/UNPROVEN and aborts the case, never a confined-native PASS. | SKIPPED / UNPROVEN |
| `b2T4ExplicitReleaseMustDestroyInsideTheRestrictedDomain` | Create, increment and explicitly release owned custody under restriction; check native value/status, destructor TID, live/destruction deltas and repeated session close. | PASS observed |
| `b2T5OtherHostThreadCloseMustNotMoveTheDestructorOutsideTheDomain` | Create owned custody under restriction; after the Protos call/domain thread finishes, close the real session on a different unrestricted host thread. Observe through an authorized Actor session, check revocation and exactly-once cleanup, then require the destructor TID to match the authorized domain. | FAIL observed: destructor TID 105670 differs from restricted TID 105669 |
| `b2T6CloseWaitsForARealAdmittedDowncallWithoutProvingDestructorConfinement` | Hold actual C entry at a bounded barrier, witness its real provider frame and `FfmSession.close` waiting, verify no destruction, then release normally and count one destruction. Actor non-reentrancy is outside this case. No Landlock is installed in this independent wait test. | Original version FAIL observed; corrected version PASS observed only for admitted C entry, close waiting, no premature destruction and exactly-once cleanup |
| `b2T7ActorSessionsRejectHandlesButDoNotConfineUnrestrictedNativeEntry` | Two `newActorActivation()` sessions reject alien/released handles and Actor transfer, preserve new-acquisition identity, and run on different physical threads. The first domain stays alive while the second Actor, and then the first session on a new unrestricted carrier, attempt exterior access. Successful access is an explicit counterexample to B confinement, permitted by R1. | PASS observed as a counterexample/control |
| `b2T8AuthorizedFfiIsANativeRouteWithoutImportingSqlite` | Authority-free `std:ffi` import stays lazy; authorized FFI then opens a file without importing `std:sqlite`. Assert the real host descriptor is `TRUSTED_IN_PROCESS` without enforcement; reuse existing unauthorized-target refusals. | PASS observed as a native-route control |
| `b2T9ExistingAbiAdmissionBorrowCustodyAndIrreversibleCloseRemainIntact` | Reuse complete existing ABI/public-FFI/close regressions: result order, pre-C admission, borrowed epochs, adoption before failed publication, session mismatch, permanent revocation and exactly-once cleanup. T6 provides no Actor non-reentrancy proof. | PASS observed for existing regressions; Actor non-reentrancy remains outside T6 |

**T5 deliberately tests B's required property strictly.** The inspected
`FfmSession.close` currently calls the destructor on the closing thread after
waiting for admitted calls. The owner-reported run confirmed a destructor TID
of **105670**, differing from the restricted TID **105669**, and T5 failed its
strict identity assertion. That is an **impediment to future architecture B
until an authorized mechanism removes it**, not a defect in the current trusted
FFM profile. Do not change production close, substitute JDK fixture calls, widen
the grant, or weaken the assertion to manufacture a confined PASS. T7 and T8
are negative/control observations: a green JUnit counterexample test never
certifies the corresponding security obligation.

### Owner-reported execution and bounded T6 correction — 19 November 2026

| Command or metric | Observed evidence before the T6 correction |
|---|---|
| `mvn -DskipTests test-compile` | BUILD SUCCESS, reported by the owner |
| `mvn -Dtest=ProtosI144FfmFixtureAbiTest test` | BUILD FAILURE: 17 tests, 2 failures, 0 errors, 1 skipped |
| Passed cases | 14, including existing fixture regressions; not 14 confinement proofs |
| Focal class duration | 9.434 seconds |
| Maven total duration | 34.275 seconds |
| T5 failure | Required restricted TID 105669; observed destructor TID 105670 |
| Original T6 failure | `second host entry never waited for the occupied execution segment` |
| Host-initialization skip | `no foreign provider plugin found on the configured provider paths`; UNPROVEN, not PASS |
| Corrected T6 compilation/execution | Recorded separately below; not part of this historical complete-class run |
| Integrated suite and dedicated static checks | No reported results |
| Exact tested revision | Not supplied |

The nine parsing errors from the earlier attempt were resolved before this run;
the reported run has zero errors. The agent inspected the existing Surefire XML
and source, but executed no tests or commands that validate the repository.

The original T6 incorrectly expected a second direct Java host entry to wait
in `Thread.State.WAITING`. `ProtosPolyglotExecutionContext.callEntered` holds
the shared read side of its lifecycle lock; it protects against Context close,
not concurrent execution. `ProtosActorExecutionDomain.runFreshRootTaskDirectly`
expects an Actor-owning caller. Starting another direct root entry from Java
is consequently not a valid test of Actor non-reentrancy.

The bounded correction removes only that additional concurrent entry, its
latches, thread-state/completion assertions and cleanup, plus its now-unused
`ordinarySegment` warm-up in T6. It preserves the real C entry barrier,
`FfmBinding.invoke` stack evidence, waiting `FfmSession.close`, live owned
resource/no-premature-destruction assertions, barrier release in `finally`,
native-call and close completion, Protos terminal-result classification,
exactly-once destruction, repeated-close check, native TIDs and diagnostic
publication. T5 is unchanged. No replacement lock, executor, artificial
serialization, production change or moved non-reentrancy check is introduced.

Assertions reached before the original T6 failure provide partial observations,
not a completed original T6 PASS. The subsequent corrected-T6 run is recorded
below. Actor non-reentrancy requires its own mechanism-appropriate evidence and
is outside T6; neither the corrected test nor T9's previously passing
regressions proves it.

### Corrected T6 owner-reported focal execution — 19 November 2026

| Command or metric | Owner-reported evidence after the T6 correction |
|---|---|
| `mvn -DskipTests test-compile` | BUILD SUCCESS |
| `mvn -Dtest=ProtosI144FfmFixtureAbiTest#b2T6CloseWaitsForARealAdmittedDowncallWithoutProvingDestructorConfinement test` | BUILD SUCCESS |
| Tests run | 1 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |
| Focal duration | 2.233 seconds |
| Exact tested revision | Not supplied |

**Corrected T6: PASS observed**, exclusively for the real admitted C call,
session close waiting for that call, absence of premature owned-resource
destruction, and exactly-once cleanup. This observation proves neither Actor
non-reentrancy nor destructor confinement. The original T6 failure remains
historical evidence of the invalid second-entry check; it is not erased or
retroactively reclassified as PASS.

The complete focal class has not been reported successful after this correction.
**T5 remains FAIL for confinement**, with restricted TID **105669** and
destructor TID **105670**. Its strict assertion is unchanged. B1 remains
**13/13 PASS observed**; **PLAT074 OPEN, D256 pending final reconciliation,
LIB031 BLOCKED**. No production or architectural approval follows from the
corrected T6 result. These are owner-reported results, not agent executions.

### Instrumentation and concrete lifecycle boundaries

`ffi_test_thread_id` reports Linux `SYS_gettid`; no `Thread.threadId()` or
pthread identity substitutes for it. The Java host uses an independently linked
libc `gettid` solely to identify the expected physical thread; **no fixture
operation bypasses the real provider**. T2/T6 inspect the blocked thread's Java
stack for `FfmBinding.invoke` while the C barrier establishes native entry.

`ffi_test_open_probe(path, mode, errnoOut, tidOut)` returns `[status, errno, TID]`
through the public FFI output protocol. Mode 0 opens read-only; mode 1 uses
exclusive creation with mode 0600 and never truncates. Missing in-grant input
returns ENOENT (2); exact unrestricted read/create controls before restriction
and renewed controls afterward distinguish EACCES (13) from absence and ordinary
permissions. No ACL, credentials or fixture authorization is changed.

Mode 2 performs an actual read-only open/close, then holds the admitted native
call at an atomic barrier with a twenty-second bound. Modes 3/4/5 only
observe/release/reset that diagnostic barrier. Those control modes are never
counted as successful filesystem operations. The observer is a separate real
Fixture/Context for T2/T6 so the tested Context lock cannot block observation.
All use the same authorized shared-library file. The existing live/destruction
counters and the new last-destructor TID are atomic for concurrent observation;
their public ABI and lifetime behavior remain unchanged.

| Concrete method | Current inspected role; what the experiment distinguishes |
|---|---|
| `FfmSession.acquireModule` | Admits acquisition, lazily creates the linker/shared arena and returns/caches the library module. Host/session creation alone does not call it. |
| `FfmSession.load` | Calls `SymbolLookup.libraryLookup` under the same acquisition operation. Library initializers/dependencies may exercise native authority. No public Protos operation isolates load from acquisition; T3 observes this coupled stage and requires the loader-specific failure category. |
| `FfmSession.bind` | Resolves the symbol in the exact authorized library and constructs/caches a downcall binding. A warmed class or another Actor's binding does not populate this session's binding cache. |
| `FfmBinding.invoke` | Admits the real call, marshals into its per-call arena, enters C, adopts owned results before fallible conversion and releases active-call admission on exit. T2/T6 witness this method on the blocked carrier. |
| `FfmSession.release` | Retires/forgets custody before exactly one destructor attempt; T4 checks explicit release on the restricted thread. |
| `FfmSession.destroy` | Dispatches to the resource's declared destructor; records uncertain native failure without retry. The experiment does not introduce another resource manager. |
| `FfmBinding.invokeDestructor` | Invalidates borrowed epochs and calls the bound C destructor directly on the current physical thread. C records that thread before normal counter destruction. |
| `FfmSession.close` | Irreversibly rejects new admission, waits for active calls, drains remaining custody through `destroy`, then closes the library arena. T5 separates the destructor's domain from T6's wait guarantee. |

### Limits and validation handoff

The results above distinguish the owner-reported pre-correction class run from
the subsequent corrected-T6 focal PASS. Tests cover the existing standalone
Fixture/direct root-segment boundary, not
every embedded scheduler, suspension/resumption, Native Image or future carrier
policy. T6 establishes no Actor non-reentrancy guarantee. Host/class initialization
results depend on already-open JARs and warmed JVM state. Unique library copies
prevent reuse of that exact library's loaded pathname; dependencies may already
be resident. T3 does not trace every dependency lookup, initializer, library
unload or process-global native state, or establish a fully cold-process result.

The counter destructor reports its execution TID, not a complete syscall trace
of arbitrary native teardown. No future native-route exclusion policy, durable
SQLite API, security against malicious same-process C, power-loss durability or
production repair is supplied. A production Java proposal still requires
verified equivalent methods, annotations and behavior in a real Truffle
implementation, preferably GraalPy; this slice proposes none.

T6 also records the public call's terminal outcome separately from C completion.
`closeForRuntime` retires the facade generation before waiting for the admitted
provider call. The public `std:ffi` adapter reads private indexed outputs after
native return; that later access can consequently signal the existing ordinary
Error for a closed generation. The test accepts only a completed, correctly
ordered result or that ordinary Error, preserving both observations. It does
not reinterpret output revocation as premature C destruction, promise successful
result publication during close, or silently turn a ForeignError into success.

Run the following **manually, in order, stopping at an unexpected failure**.
No command below has been run by the agent. The cheap first gate is:

```sh
git diff --check
git diff --cached --check
```

For this correction, review only the JUnit file and this guide, preserving
existing staged B1 work. The owner has reported BUILD SUCCESS for compilation
of the corrected Java tests without executing them:

```sh
mvn -DskipTests test-compile
```

The owner also reported BUILD SUCCESS for the corrected T6 alone using:

```sh
mvn -Dtest=ProtosI144FfmFixtureAbiTest#b2T6CloseWaitsForARealAdmittedDowncallWithoutProvingDestructorConfinement test
```

The focal class dynamically compiles its existing provider JAR and the C
fixture at test time; allow up to a few minutes including Maven startup.
Diagnostic barriers wait at most twenty seconds and platform-thread joins at
most twenty seconds per call. Run all focal methods so an expected T5
counterexample does not hide the independent cases:

```sh
mvn -Dtest=ProtosI144FfmFixtureAbiTest test
```

Preserve Surefire XML, per-case reporter entries, every failed/skipped method,
TIDs, error categories and statuses. A T5 failure matching the documented leak
is evidence against B; it does not waive unrelated test errors. Report it before
any broader gate. Applicable formatter/static checks and the integrated
`make test` gate remain **NOT RUN**; this test-infrastructure delta needs FULL
validation for eventual executable closure, after interpreting the focal
experiment. No claim of a green integrated suite follows from B1 or this slice.

The pre-edit material inspection included root/work-type/path instructions;
`spec/semantics/FFI.md` (also read from remote main), `MODULES.md`'s FFI boundary
and `ACTORS.md` §24J/native-resource boundary; the existing ABI/public FFI
programs and `protos/lib/ffi/module.protos`; the fixture C, JUnit host and
`ProtosExternalForeignProviderTest.Fixture`; provider lifecycle/transfer tests;
`FfmSession`, `FfmBinding`, `FfmFailure`, `FfmValueOperations`, `FfmLibraries`,
`FfmForeignProvider` and fixture registration; runtime session bridge,
registry/profile, foreign-error projection and canonical root-task/Context entry;
the existing Landlock/Native/TestSupport and B1 domain helpers; Maven test
configuration, legacy-execution guard and `LICENSE.TXT` Part 5. These constrain
the slice to existing synchronous entry, host-authorized trusted native code,
Actor-local metadata/custody, strict pre-entry admission and unchanged licensing.

The remote
[blocker registry](https://github.com/guillermomolina/protos-project-docs/blob/main/docs/project/registries/IMPLEMENTATION_BLOCKERS.md),
[D256-C1 decision](https://github.com/guillermomolina/protos-project-docs/blob/main/docs/project/decisions/language/D256_C1_PROTOS_FIRST_SQLITE_ARCHITECTURE_APPROVED_2026_11_11.md)
and [handoff](https://github.com/guillermomolina/protos-project-docs/blob/main/docs/project/work/D256/D256_C1_IMPLEMENTATION_BOUNDARIES_2026_11_11.md)
and the platform architecture registry were read for B2. The ledger contains no SQLite-specific unblock condition;
the handoff's authority/cancellation gates remain open. The queried PLAT074
work-directory path returned 404 and repository search returned no match; no
missing durable record is invented. No remote content was changed.

APL Part 5 notices are retained in the modified C/JUnit files and reproduced in
the new Protos source; `LICENSE.TXT` remains present. No third-party material or
license/packaging configuration was changed. The owner's staged B1 test and B1
results are preserved. No Git, compiler, build, test, script, commit or push was
executed by the agent.
