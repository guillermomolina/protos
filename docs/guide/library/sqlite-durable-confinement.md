# SQLite durable confinement architecture decision packet

**Historical decision packet; current integration update in §28.**

The owner selected architecture D (a separate OS process confined before JVM
startup) and authorized the provisional per-database manifest in the PLAT074-FINAL
continuation. This does not ratify D256. Earlier candidate comparisons below
remain historical; §28 supersedes their current implementation-status wording.

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

### Latest owner-reported B2 execution

**17 tests: 15 PASS, 1 FAIL, 1 SKIPPED.** T5's confinement failure was
reproduced with restricted Linux TID **111351** and destructor TID **111352**.
Corrected T6: **PASS**, limited to admitted-call waiting, no premature
destruction and exactly-once cleanup; it proves no Actor non-reentrancy or
destructor confinement. This latest owner-supplied evidence supersedes the
aggregate status and TIDs in the historical runs retained below. No exact
revision or new timing was supplied. B1 remains **13/13 PASS observed**;
**PLAT074 OPEN, D256 pending final reconciliation, LIB031 BLOCKED**.

### Earlier B2 execution history

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


## 14. PLAT074-B3 — Restricted-thread close-request feasibility

### Latest owner-confirmed B3 execution — 19 November 2026

The owner reports `mvn -DskipTests test-compile`: **BUILD SUCCESS**, followed
by `mvn '-Dtest=ProtosI144FfmFixtureAbiTest#b3T*' test`: **BUILD SUCCESS**,
**6 tests, 0 failures, 0 errors, 0 skipped**, focal duration **3.361 seconds**.
B3 is therefore **6/6 PASS observed** for that run. No exact tested revision or
new TIDs were supplied. This supersedes the corrected-candidate NOT RUN labels
in the historical account below, without deleting the failed runs or their
corrections. Those labels describe the evidence available at those earlier steps.

B3-T5 remains a **successful negative test**: it reproduces the absence of
automatic affinity for an exterior close. Its PASS is not positive confinement
evidence. B1 remains **13/13 PASS observed**; B2's T5 noncompliance remains
preserved. **PLAT074 OPEN; D256 pending final durable-authority decision;
LIB031 BLOCKED.** No alternative is selected and no issue is closed.

### Historical B3 execution and corrections (preserved)

**B3-T1: earlier isolated PASS, owner-reported on 19 November 2026.** The owner
reported clean `git diff --check` and `git diff --cached --check`,
`mvn -DskipTests test-compile`: BUILD SUCCESS, and
`mvn -Dtest=ProtosI144FfmFixtureAbiTest#b3T1ExternallyRequestedCloseMustDestroyOnRestrictedOwnerThread test`:
BUILD SUCCESS, 1 test, 0 failures, 0 errors, 0 skipped, focal duration
**2.300 seconds**. No exact tested revision or TIDs were supplied. This proves
owner-thread close in one controlled experimental path, not automatic dispatch
or a repair of `FfmSession.close()`.

**B3-T2 through B3-T6: owner-reported execution failed; corrected candidate NOT RUN.** This second batch changes only fixture C,
the Protos confinement probe, the existing JUnit class and this section. No
production Java, new Java class, public `std:ffi`/`std:sqlite` API, specification,
B1, B2-T5, B2-T6 or B3-T1 test is changed. It selects no worker/dispatch
architecture. **PLAT074 OPEN, D256 pending final reconciliation,
LIB031 BLOCKED**; B1 remains **13/13 PASS observed**. The latest B2 observation
in §13 remains 17 tests / 15 PASS / 1 FAIL / 1 SKIPPED, including T5's reproduced
111351/111352 mismatch and corrected T6's bounded PASS.

### Owner-reported batch failure and bounded harness correction

On 19 November 2026 the owner supplied a complete B3 focal-run failure:
**6 tests, 3 failures, 3 errors, 0 skipped**, class duration **23.32 seconds**,
Maven total **48.507 seconds**, BUILD FAILURE. T3/T4/T6 were failures;
T1/T2/T5 were errors. No B3 confinement PASS follows from this batch. T1's
earlier isolated PASS remains historical evidence, not a PASS of this candidate.

The first listed case, T6, failed to resolve
`ProtosCoreErrors$StandardError` while producing the expected ordinary admission
Error on the restricted thread. Fatal-unwind handling also failed to resolve
`ProtosIoOperation`. Subsequent cases, including unrestricted post-close
negative assertions in T1/T2, observed the same failed symbolic resolution.
This is consistent with first class loading being denied under Landlock and
poisoning that JVM's later resolution; no filesystem syscall trace was supplied.
T6's twenty-second readiness timeout is secondary; its suppressed worker cause
is retained. A fresh Maven/Surefire JVM is required for retesting the correction.

A separate cleanup error was explicit in T5/T3 as well as suppressed elsewhere:
`Polyglot runtime host cannot close while Process Contexts are active`.
`Fixture.close()` requests semantic termination then immediately closes the
host; a termination request alone does not establish completed Context cleanup.
The existing `ProtosForeignValueFixture.close()` provides a comparable explicit
terminal-disposition boundary. No shared Fixture or production code is changed.

The correction adds class-level `@BeforeAll` preparation of exactly the two
reported host classes before any Landlock installation. The new cases also
exercise ordinary invalid-argument rejections during unrestricted preparation,
without acquiring or releasing owned resources. B3-T1's method and all B1/B2
methods remain unchanged. Shared T2–T6 teardown now requests semantic termination
through an existing platform-thread helper, joins with its existing bound,
checks host active-Context count reaches zero with the existing five-second
bounded condition helper, and only then calls fixture teardown/host close.
Observers use the same gate; their teardown no longer runs before the restricted
owner's join. An active owner/session still prohibits exterior fixture cleanup.
All cleanup failures remain visible; no destructor is retried or moved outside
its domain to manufacture success. T1 retains its existing fixture teardown;
this bounded correction does not claim to repair the shared Fixture generally.

**Correction validation: NOT RUN.** This is a host preparation/teardown fix,
not a confinement result, native interruption mechanism or architecture change.

### Hypothesis and upstream precedents

B2-T5 closes directly on another host thread after the restricted owner exits.
B3-T1 instead keeps that owner alive, sends an external request and has the
owner itself call `probe.session.closeForRuntime()` inside Landlock. A PASS
would establish feasibility of this particular custody/close path only. It
would neither repair T5 nor show that the provider automatically dispatches
shutdown to a resource's authorized domain.

The following source was inspected at the exact oracle/graalpython revision
`5c8486970dead373d888f25210788cca5d79c374`. These are bounded precedents, not
implementations of the proposed Protos confinement boundary:

| Exact method and annotations | Verified behavior and relevance |
|---|---|
| [`PythonContext.createSystemThread(PythonSystemThreadTask)`](https://github.com/oracle/graalpython/blob/5c8486970dead373d888f25210788cca5d79c374/graalpython/com.oracle.graal.python/src/com/oracle/graal/python/runtime/PythonContext.java) (no method annotation) | Builds through `env.newTruffleThreadBuilder(task).threadGroup(getThreadGroup()).build()`, sets daemon/name and registers a weak thread reference; returns the unstarted thread. This is managed thread creation, not `Env.createSystemThread` and not proof that arbitrary raw Java workers are equivalent. |
| [`PythonSystemThreadTask.run()`](https://github.com/oracle/graalpython/blob/5c8486970dead373d888f25210788cca5d79c374/graalpython/com.oracle.graal.python/src/com/oracle/graal/python/util/PythonSystemThreadTask.java), `@Override`, `public final` | Calls `doRun()`, logs normal completion, handles `PythonThreadKillException`, logs/rethrows other failures. Its class contract requires GIL acquisition for Python execution and safepoint polling. It supports explicit thread lifecycle handling, not native teardown affinity. |
| [`AsyncHandler.AsyncRunnable.run()`](https://github.com/oracle/graalpython/blob/5c8486970dead373d888f25210788cca5d79c374/graalpython/com.oracle.graal.python/src/com/oracle/graal/python/runtime/AsyncHandler.java), `@Override` | Obtains an action, targets `ctx.getMainThread()` through `Env.submitThreadLocal(new Thread[]{mainThread}, new ThreadLocalAction(true, false) {...})`. The action's `perform` has `@Override` and `@SuppressWarnings("try")`; it enters the async-handler guard, acquires/releases GIL and drains rescheduled actions. This supports targeted cooperative dispatch, not arbitrary C interruption. |
| [`PythonLanguage.initializeThread(PythonContext, Thread)` / `disposeThread(PythonContext, Thread)`](https://github.com/oracle/graalpython/blob/5c8486970dead373d888f25210788cca5d79c374/graalpython/com.oracle.graal.python/src/com/oracle/graal/python/PythonLanguage.java), both `@Override` | Delegate respectively to `context.attachThread(thread, threadState)` and `context.disposeThread(thread, false)`. These are Truffle lifecycle hooks, not an owned-resource destructor policy. |
| [`PythonContext.finalizeContext()`](https://github.com/oracle/graalpython/blob/5c8486970dead373d888f25210788cca5d79c374/graalpython/com.oracle.graal.python/src/com/oracle/graal/python/runtime/PythonContext.java), `@TruffleBoundary`, `@SuppressWarnings("try")` | Under GIL: normal shutdown threads/hooks unless cancelling, stop async handler, set finalizing, flush streams before C API finalization, join Python threads, free context memory, dispose thread states, then close NativeContext last in that protected native cleanup sequence because it can unload libraries. Afterwards join system threads and clear main-thread reference. This backs explicit cleanup ordering, not affinity of arbitrary native destructors. |

GraalPy is not claimed to implement Landlock. `ThreadLocalAction` delivery
requires the target to service safepoints; it cannot force interruption of a
blocked C call. B3-T1 uses bounded Java latches after completed synchronous
Protos calls, with no `ThreadLocalAction`, Java interrupt or forced termination.
The raw platform thread remains the existing experimental harness; these
precedents do not ratify it as production Truffle infrastructure.

The following additional Espresso precedents are supplied as already-verified
input to this batch; no new upstream investigation is claimed:

- [`EspressoReferenceDrainer.initReferenceDrain`, `startReferenceDrain`,
  `shutdownAndWaitReferenceDrain`, and `ReferenceDrain.run`](https://github.com/oracle/graal/blob/bd884eb3effea2b31f3fbdd7250267ba8e9eca8e/espresso/src/com.oracle.truffle.espresso/src/com/oracle/truffle/espresso/runtime/EspressoReferenceDrainer.java),
  revision `bd884eb3effea2b31f3fbdd7250267ba8e9eca8e`.
- [`EspressoLanguage.finalizeContext`](https://github.com/oracle/graal/blob/bd884eb3effea2b31f3fbdd7250267ba8e9eca8e/espresso/src/com.oracle.truffle.espresso/src/com/oracle/truffle/espresso/EspressoLanguage.java),
  at that same revision.

Together with the pinned GraalPy `PythonContext.createSystemThread`,
`finalizeContext` and `PythonLanguage.disposeThread` above, these precedents
support controlled Truffle threads, cooperative termination and ordered native
cleanup. They neither establish a complete SQLite Landlock worker nor authorize
introducing one automatically. They supply no arbitrary blocked-C interruption
or automatic native destructor affinity guarantee.

### B3-T1 test and observations

[`b3T1ExternallyRequestedCloseMustDestroyOnRestrictedOwnerThread`](../../../src/test/java/com/guillermomolina/protos/execution/foreign/api/ProtosI144FfmFixtureAbiTest.java)
reuses `Fixture`, `Probe`, `startPlatform`, all existing fixture bindings and
the existing `keepForClose`, `retained`, `threadId`, `liveCount` and
`destroyedCount` closures. Both directories/files and the dynamic provider
artifacts use JUnit's temporary directory outside the checkout. Preparation
binds the ABI and resolves provider JAR classes without creating an owned
resource or running a destructor on JUnit's thread. Both sessions establish
positive exterior-open controls before restriction.

The single restricted platform thread records physical Linux `gettid`, matches
it against real-provider `ffi_test_thread_id`, requires an exterior-open
`EACCES` (13), creates/retains one owned counter and signals creation. While it
waits, JUnit requires installed Landlock, a live owner, an open session, exactly
one extra live resource, no destruction and the independent observer's
continued exterior access. The retained handle remains metadata in the owner;
it is never supplied to the observing Actor.

JUnit signals the close request. The owner calls `closeForRuntime` directly,
checks closure and calls it a second time for idempotence before signaling
completion and exiting. After completion, the observer requires baseline live
count, exactly one destructor and a destructor TID matching the restricted
physical TID. Retained-handle increment/release must then fail ordinarily.
JUnit never invokes session close or a native destructor. Diagnostic reporter
entries include TIDs, Landlock state, initial/final counts, request status,
owner liveness and failures; unavailable final observations remain `-1`.

All latch waits and joins are bounded at twenty seconds each. The worker's
`finally` drains any early-failure custody only on that same restricted thread,
without retrying an attempted close. The host's `finally` releases the request
barrier, joins and preserves primary failures with cleanup failures suppressed.
`Fixture.close()` is permitted only before worker creation or after the owner
has exited with its session already closed; it cannot manufacture PASS by
performing out-of-domain destruction. If the owner remains pending, fixture
teardown is deliberately withheld and reported as failure rather than racing
or forcibly terminating native work. Such a timeout is not a successful cleanup.

`FfmSession.close` currently records uncertain destructor failures privately
and suppresses their throwable. The test preserves all exposed Java failures
and checks native TID/count effects; it cannot recover a suppressed native
cause from this unchanged provider. Missing destruction therefore fails the
experiment rather than triggering a destructor retry or a success claim.
There is no new native barrier or outstanding asynchronous native operation in
this case; a successful join follows completed synchronous calls and cleanup.

The owner-reported T1 PASS establishes external signaling, owner survival through cleanup,
same-thread destructor execution, custody and idempotence in this one real-FFM
path. It proves no automatic dispatch, blocked-C cancellation, Actor
non-reentrancy, general destructor syscall confinement, durable SQLite API,
Native Image support or production solution. No permanent worker, queue,
scheduler or policy is introduced.

### Second batch: full experimental resource lifecycle

All five new methods belong to `ProtosI144FfmFixtureAbiTest` and use real
Protos closures through `Probe`. Java orchestrates platform threads, barriers
and assertions; it declares no native signature and fabricates no handle.

| Case / exact JUnit method | Required confinement and custody observations | Result |
|---|---|---|
| B3-T2 `b3T2MultipleOwnedResourcesReleaseAndCloseOnRestrictedThread` | Three acquisitions; `[41, 61, 0]`; one explicit destructor while two resources remain live; externally requested owner-thread close drains both; total destruction delta three, live count restored, cross-thread delta zero, retained first/third handles rejected and no duplicate destruction. | Previous batch ERROR; correction NOT RUN |
| B3-T3 `b3T3CloseRequestDuringNativeCallWaitsUntilOwnerCanClose` | Real admitted `FfmBinding.invoke` and active C barrier; request recorded before barrier release; no premature destruction or session close; successful native status/errno and owner TID; owner closes only after native return; destruction delta one and cross-thread delta zero; bounded join. | Previous batch FAIL; correction NOT RUN |
| B3-T4 `b3T4IndependentActorsAndGrantsCloseOnTheirOwnRestrictedThreads` | Two different Actor sessions and simultaneous physical Landlock domains; own-file success and other-grant EACCES with native TIDs; both ready before requests; actual alien-handle admission and transfer rejection, owner use retained; two total destructions, no remaining added live resource or cross-thread delta; unrestricted observer keeps access. | Previous batch FAIL; correction NOT RUN |
| B3-T5 `b3T5AliveRestrictedOwnerDoesNotConstrainExternalCloser` | Owner remains alive while another unrestricted platform thread closes directly; destructor matches exterior closer and differs from creator; cross-thread and destruction deltas both one, session closed and no repeat when owner exits. PASS would be a negative-control observation of FAIL B confinement, not confinement PASS. | Previous batch ERROR; correction NOT RUN |
| B3-T6 `b3T6RejectedGuestCallsDoNotPreventRestrictedSessionCleanup` | Invalid `incrementOwned`/`releaseOwned` inputs are ordinary admission Errors; no destruction, original handle remains usable by owner; external request leads to owner-thread close; one destruction, zero cross-thread delta and closed-handle refusal. | Previous batch FAIL; correction NOT RUN |

The fixture adds private `ffi_test_counter.creator_tid`, initialized by the
existing Linux `ffi_test_thread_id` at creation. Destruction compares its
physical TID with that field before normal free/accounting. The sole new C
symbol is `int32_t ffi_test_cross_thread_destroy_count(void)`, backed by an
independent C11 `_Atomic int32_t`. Observation never prevents, delays or repeats
a destructor, and existing ABI signatures and status semantics are preserved.
All mismatch assertions compare against the initial counter, including after
previous counterexamples; they never assume process-global zero.

The Protos probe binds that symbol as `FFI.i32` with `Array()` parameters and
exposes `crossThreadDestroyCount`. New lexical `keptSecond`/`keptThird` slots
begin at `null`. `keepManyForClose` creates values 40/50/60, increments the first
and third, explicitly releases the second, and returns ordinary
`Array(firstValue, thirdValue, status)`. `retained` stays compatible and
`retainedThird` permits the additional closed-handle checks. All declarations,
resource acquisition, release composition and native calls remain in Protos.

These cases deliberately separate three paths: an external request executed
by the owner (T1/T2/T4/T6); a request made while C is active and handled only
after return (T3); and direct exterior execution of close while the owner is
still alive (T5). Keeping an owner alive alone does not enforce close affinity.
Multi-resource custody and two independent Actors add no automatic Landlock
policy. Exactly-once retirement/session revocation are FFM custody properties;
Landlock restricts the physical executing thread's filesystem authority.
The native mismatch counter observes destructor affinity, not every syscall
inside arbitrary destructors. The observer's successful exterior access is a
control, never process-wide confinement evidence. B2-T5 remains unchanged as
the strict failing hypothesis.

Shared helpers are confined to these five tests: preparation, bounded latch
waiting, idempotent owner close, an owner-thread `finally`, counter observations
and failure-preserving host teardown. Existing `Probe`, `probe`, `startPlatform`,
`PlatformCall`, `kernelTid`, `awaitGate` and `assertOnStack` are reused. No new
Java class or production dispatch infrastructure is added. Every latch wait and
join is bounded; T3 releases the existing native barrier in `finally` before
joining. Failed owners have no exterior-close fallback. Cleanup exceptions
remain failures or suppressed exceptions of the primary failure, and fixture
teardown is withheld if a started owner/session remains pending. No uncertain
destructor is retried, no custody order is assumed, and no thread-state check
is used to infer Actor non-reentrancy. Reporter entries record counter snapshots,
physical TIDs, Landlock/liveness and worker failures; T3/T5 also state request
and negative-control status explicitly.

### Manual validation handoff and inspection scope

The T1 evidence above belongs to the owner's earlier candidate. For this
corrected candidate, whitespace/static checks, compilation, focal execution and
the integrated suite are all **NOT RUN**; the failed prior batch is recorded above. Run manually in order, stopping at an
unexpected failure. Start with the four-file diff and whitespace gates:

```sh
git diff --check
git diff --cached --check
```

The narrow C static check has no test execution or output artifact:

```sh
cc -std=c11 -Wall -Werror -fsyntax-only protos/tests/foreign-provider/native/ffi_test.c
```

Compile Java tests without executing them, then run only the six B3 methods,
including preserved T1 as a regression:

```sh
mvn -DskipTests test-compile
mvn '-Dtest=ProtosI144FfmFixtureAbiTest#b3T*' test
```

To select only the five new methods:

```sh
mvn '-Dtest=ProtosI144FfmFixtureAbiTest#b3T2*+b3T3*+b3T4*+b3T5*+b3T6*' test
```

Allow up to a few minutes for Maven startup and the existing dynamic Java/C
fixture compilation. Preserve Surefire results and all B3 reporter entries.
These focal commands do not execute the deliberately failing B2-T5. No success
is inferred for any new case. Full executable closure and its integrated
validation obligation remain deferred; no formatter result is claimed.

Material inspection for B3 covered root instructions; `AGENTS.work/DESIGN.md`,
`IMPLEMENTATION.md`, `REFERENCE.md` and `src/AGENTS.md`; `FFI.md` (complete),
`MODULES.md`'s FFI boundary and `ACTORS.md` §24J/native-resource boundary;
this guide's B1/B2 records; `ProtosI144FfmFixtureAbiTest`,
`ProtosExternalForeignProviderTest.Fixture`, `ffm-confinement-probe.protos`;
`ProtosForeignProviderSessionBinding`, `FfmSession` close/destroy,
`FfmBinding` owned conversion/destructor paths, `FfmHandles` resource/custody,
`FfmValueOperations`, and `ProtosLinuxConfinedFilesystemLandlock`;
`pom.xml` compiler/Surefire settings and `LICENSE.TXT` Part 5. These impose
unchanged synchronous entry, Actor-local custody, no transfer or ambient
confinement guarantee, irreversible closure, exactly-once destruction and APL
notice preservation. The remote blocker ledger, D256-C1 decision and handoff
were also read: no SQLite-specific ledger unblock condition was found, and the
explicit authority/cancellation gates remain open. The five pinned GraalPy
methods above were inspected as upstream evidence, without copying upstream
source into the repository.

The failure correction additionally inspected the supplied run's primary and
suppressed causes; `ProtosForeignOperation` ordinary-error admission,
`ProtosCoreErrors.StandardError`, `ProtosActorExecutionDomain` fatal cleanup,
`ProtosProcessRuntime` termination, `ProtosPolyglotRuntimeHost.close`,
`ProtosPolyglotProcessContext.processTerminatedForRuntime`,
`ProtosPolyglotExecutionContext` close disposition, and
`ProtosForeignValueFixture.close`. These distinguish host class preparation,
semantic termination and completed platform cleanup. Only this guide section
and the existing JUnit file are edited for the correction.

The second batch also materially inspected `protos/AGENTS.md`, the current C
resource/destructor/barrier paths, existing Protos binding/custody composition,
the existing B2-T7 session-membership and transfer assertions and B3-T1's
preparation/cleanup. These require valid line-separated Protos slots, continued
use of the generic fixture registration and unchanged pre-entry ordinary Error
semantics. The fixture registration remains generic and needs no new symbol
allowlist. The approved batch is experimental evidence, with no new semantic
choice or design ratification.

Only the four authorized files were edited using Python; guide edits are limited
to §14. Existing source APL notices are retained and `LICENSE.TXT` remains
present; no new source class or packaging change is introduced. No Git, Maven,
compiler, test, benchmark or validation command was executed for this batch.
No temporary audit file, commit, push, index operation or publication was made.


## 15. PLAT074-B4 — Real SQLite through the FFM provider, Protos-first

### Latest owner-confirmed B4 execution — 19 November 2026

The owner confirms the following run of the B4-R1 candidate:

| Gate | Owner-reported result |
|---|---|
| `git diff --check` | PASS |
| `mvn -DskipTests test-compile` | BUILD SUCCESS |
| B4-T1–T10 | 10 tests, 0 failures, 0 errors, 0 skipped; focal duration 8.889 seconds |
| `mvn -DskipTests package` | BUILD SUCCESS |
| `make test-protos-foreign` | 41/41 PASS |
| Portable distribution | PASS |

**B4-R1 is validated by that concrete execution.** The earlier T7 failures and
correction history below are preserved as historical evidence. The latest B4
status is **10/10 PASS observed**. This record does **not** accredit `make test`,
SIGKILL recovery or power-loss durability. B5 below remains a separate NOT RUN
candidate. PLAT074 stays OPEN, D256 pending and LIB031 BLOCKED; B is not selected.

### Historical B4-T7 evidence (preserved)

**Latest focal execution: T7 FAIL at openMemory; required compatibility NOT IMPLEMENTED under the current constraints.**
The owner reports a T7-only run finishing on 19 November 2026 at 19:40:09 UTC:
**1 test, 1 failure, 0 errors, 0 skipped**, class duration **2.707 seconds**,
case duration **1.554 seconds**, Maven total **34.041 seconds**, BUILD FAILURE.
The terminal-error preparation now exposes the guest diagnostic:
`Error { phase="openMemory" original=ProtosObjectValue }`. No new class-loading
failure is reported in this output. T7 remains FAIL; the last complete B4 run
remains seven successes and one failure, not a complete B4 PASS.

Targeted source inspection establishes an incompatible dependency in this
specific standard-resolver harness:

- sqlite/module.protos executes import("./abi.protos") on each openMemory call.
- ProtosStandardImportProtocol's resolve bridge calls resolveModuleKey before
  its separate lookup bridge reads the Actor module cache.
- ProtosModuleRuntime.resolveModuleKey calls the standard resolver again and
  converts its host resolution exceptions into an ordinary slotless Error.
- ProtosStandardLibraryModuleResolver.resolve calls sourceFile for a relative
  SOURCE import. sourceFile calls requireExactChild, which enumerates the
  distribution directories with Files.newDirectoryStream, and confined also
  performs real-path resolution. Warming a module instance does not remove
  these filesystem operations on the next import.
- T7 installs a Landlock rule granting only its new temporary directory. Its
  ruleset handles READ_DIR and READ_FILE; protos/lib is outside that directory.
  The standard resolver therefore cannot enumerate the library directories
  required by the repeated ABI import in the restricted thread.

This source-derived explanation matches the observed openMemory phase and
ordinary Error. The output does not independently expose the original nested
Error's slots or supply a syscall trace; no native SQLite failure code or failed
memory allocation is inferred. More Java class preparation cannot remove this
required resolver I/O.

T7's required successful public-memory compatibility is therefore classified
**NOT IMPLEMENTED with the existing standard resolver and unchanged grant**,
as permitted by the original B4 task's limitation-reporting rule. The executable
test remains active and retains its successful-operation assertions: it is not
disabled, converted into an expected-denial success or counted as PASS. Fixing
this compatibility would require a separately scoped change to source/import
preparation or library/runtime behavior, or additional filesystem authority.
No resolver replacement/cache, ABI injection, library modification, expanded
Landlock grant or execution bypass is introduced in B4. The other seven
reported successes retain their bounded meanings; PLAT074 stays OPEN, D256
pending and LIB031 BLOCKED.

**Previous focal execution: T7 FAIL during terminal diagnostic projection.**
The owner reports a T7-only run finishing on 19 November 2026 at 19:37:43 UTC:
**1 test, 1 failure, 0 errors, 0 skipped**, class duration **2.927 seconds**,
case duration **1.488 seconds**, Maven total **36.219 seconds**, BUILD FAILURE.
The primary NoClassDefFoundError names ProtosDiagnosticTraceCapture$Collector
inside project/capture reached from ProtosBytecodeTaskExecution.runSegment;
Context-closed cleanup remains suppressed. Earlier exceptional-path preparation
was insufficient: a caught guest Error exercises interception and unwind but
never reaches terminal root-task diagnostic projection.

A second preparation-only Protos closure now deliberately lets a labelled Error
cross the ordinary root-task failure boundary outside Landlock. sqliteProbe
asserts that outcome is FAILED with an ordinary Error, then executes the same
successful public-memory warmup as before. This exercises the existing terminal
trace projection instead of loading only its next missing nested class. It
acquires no native resource, adds no grant and does not catch or waive T7's
restricted failure. The production failure boundary and diagnostic implementation
are unchanged. The original T7 guest failure remains unresolved; revised
preparation is NOT RUN and needs a fresh human-run JVM.

**Previous focal execution: T7 FAIL.**
The next owner-reported T7-only run finished on 19 November 2026 at
19:34:40 UTC: **1 test, 1 failure, 0 errors, 0 skipped**, class duration
**2.782 seconds**, case duration **1.409 seconds**, Maven total **33.418
seconds**, BUILD FAILURE. The primary NoClassDefFoundError now names
ProtosBytecodeRootNode$EpilogForException, caused by ClassNotFoundException.
Context-closed cleanup remains suppressed. The earlier preparation therefore
was insufficient; neither this run nor the previous one identifies the guest
Error that began the unwind.

The existing class-preparation hook now also initializes that exact exceptional
epilogue class. A test-only Protos warmPublicErrorPath closure exercises the
same publicAt diagnostic wrapper with a deliberately signalled Error crossing
ensure, wrapping/re-signalling and an outer handler before any restriction.
The host asserts the error was caught, then continues the unchanged successful
memory warmup. No native acquisition or persistent open is added by this error
warmup. Successful memory operations alone had not covered exceptional roots;
this targeted preparation exercises them rather than assuming class
initialization alone completes their lazy dependencies. The underlying T7 guest
failure remains unresolved and the revised candidate remains NOT RUN.

**Previous focal execution: T7 FAIL.**
The owner reports a fresh T7-only run finishing on 19 November 2026 at
19:31:51 UTC: **1 test, 1 failure, 0 errors, 0 skipped**, class duration
**7.299 seconds**, case duration **6.382 seconds**, Maven total **32.297
seconds**, BUILD FAILURE. Preserving the primary exception exposed
NoClassDefFoundError for ProtosDiagnosticTraceCapture at bytecode exception
interception, caused by ClassNotFoundException. Suppressed termination also
failed to load ProtosActorTerminationObservation. Guest cleanup then encountered
a terminated Actor, and Process Context cleanup did not finish within its gate;
host close was withheld. These are retained failures, not a T7 PASS.

The existing prepareRestrictedErrorClasses hook now initializes exactly these
two additional classes with the test class loader before restricting any
thread, following B3's established class-preparation mechanism. No production
code, guest SQLite behavior, grant, lifecycle assertion or destructor policy is
changed. The observed loading failures are consistent with first class loading
under Landlock; this run supplies no syscall trace. Interception of an earlier
guest error triggered the diagnostic path, so the underlying guest failure is
still unresolved. Retest in a fresh Surefire JVM: previously failed symbolic
resolution must not be reused as evidence for this correction.

**Previous complete B4 execution: 7 PASS, 1 FAIL.**
The second run finished on 19 November 2026 at 19:29:04 UTC: **8 tests,
1 failure, 0 errors, 0 skipped**, class duration **8.927 seconds**, Maven total
**33.423 seconds**, BUILD FAILURE. Only T7 failed; T6 now passes in that
reported candidate. No exact tested revision or reporter TIDs were supplied.
The T7 failure visible in this output is an IllegalStateException from
closeForSession entering an already closed Polyglot execution Context. The
B4 helper's unguarded finally could replace the original experiment failure
with this cleanup failure, so the output cannot identify the original cause.

The bounded follow-up mirrors B3's existing primary/suppressed exception rule
inside startB4Owner: attempt exactly the same owner-thread cleanup, append its
failure to an existing primary exception, and propagate cleanup alone only
when the experiment had not already failed. It does not reopen the Context,
skip failed cleanup, retry a destructor or move close to JUnit. T7 remains
unresolved; this change is to retain its original diagnostic evidence.

**Previous execution: 6 PASS, 2 FAIL.** The first run finished on 19 November 2026 at 19:24:06 UTC: **8 tests, 2 failures,
0 errors, 0 skipped**, class duration **12.38 seconds**, Maven total
**43.625 seconds**, BUILD FAILURE. T6 and T7 failed; T1–T5 and T8 completed
without reported failures. This supersedes the initial eight-case NOT RUN state
for that executed candidate only. No exact tested revision, reporter TIDs or
separate static-check result was supplied.

This remains test-only work, with no production Java, public durable method,
specification, version or changelog change.
PLAT074 remains **OPEN**, D256 remains **pending final durable-authority and
execution/cancellation decisions**, and LIB031 remains **BLOCKED**. B1's
**13/13 PASS observed**, B2's reproduced T5 noncompliance and B3's **6/6 PASS
observed** retain their separate meanings. B4 does not select alternative B.

### Independent hypotheses and test methods

All methods belong to `ProtosI144FfmFixtureAbiTest`. SQLite entry, statement
state, native status handling, binding, copying and lifetime composition reside
in `protos/tests/foreign-provider/sqlite-landlock-provider.protos`; T7 uses the
existing `std:sqlite` implementation through a closure in that source.

| Case and exact method | Individual hypothesis and required observations | Status |
|---|---|---|
| B4-T1 `b4T1RealSqlitePersistsThroughConfinedFfmSession` | A database first opened after thread restriction supports exact i64 insertion/copy, explicit ROLLBACK and COMMIT, confined close and reopen with persisted rows; exterior RO/RW open returns native CANTOPEN while an unrestricted observer can read/write there. | PASS in reported run; current candidate NOT RUN |
| B4-T2 `b4T2RealSqliteWalAndShmRemainInsideGrant` | Independent Actor sessions on two physical restricted threads can use the same granted directory; query-confirmed WAL creates live WAL/SHM sidecars, a peer sees COMMIT, and confined reopen retains rows. | PASS in reported run; current candidate NOT RUN |
| B4-T3 `b4T3RealSqliteAttachAndVacuumCannotEscapeLandlock` | ATTACH and VACUUM INTO fail with SQLite CANTOPEN outside the grant, no exterior attachment is registered, ordinary granted operations still work, and final exterior bytes/names remain unchanged; unrestricted positive controls exercise both SQL routes first. | PASS in reported run; current candidate NOT RUN |
| B4-T4 `b4T4RealSqliteBusyAndTransactionRecoveryUnderConfinement` | A held BEGIN IMMEDIATE causes the other independent confined writer to receive native SQLITE_BUSY with 100 ms busy timeout and autocommit=1; after the first COMMIT, the second sees its row and can write. | PASS in reported run; current candidate NOT RUN |
| B4-T5 `b4T5RealSqliteIndependentGrantsRejectCrossAccess` | Two live restricted physical threads with independent Actor sessions can modify their own existing databases, reject cross-grant RO/RW acquisition and close in their owner domains; unrestricted observation confirms own data. | PASS in reported run; current candidate NOT RUN |
| B4-T6 `b4T6RealSqliteCloseRequestPreservesInFlightNativeCall` | While a real contended SQLite step is observed in FFM native entry, an exterior request precedes its normal BUSY completion; the owner finalizes the statement, observes autocommit, and closes session custody inside its restriction without interruption or exterior close. | PASS in second reported run; current candidate NOT RUN |
| B4-T7 `b4T7PublicStdSqliteMemoryOperationsRemainCompatibleWithLandlock` | Existing public std:sqlite openMemory, prepare, bind, step, copied integer columns, statement reset and terminal-Future close execute inside Landlock after dependency preparation. | FAIL observed at openMemory; required compatibility NOT IMPLEMENTED under current constraints |
| B4-T8 `b4T8AuthorizedSqliteNativeAccessIsNotImplicitlyConfined` | Imports alone succeed without a provider but native acquisition/open fails; with explicit authorization the restricted Actor is denied exterior access while an unrestricted independent Actor can open and modify the same exterior database. This is a negative control against implicit provider confinement. | PASS in reported run; current candidate NOT RUN |

T7's required successful compatibility is classified NOT IMPLEMENTED for the
current resolver/grant combination; its active test records failure rather than
relaxing the requirement. The remaining changed candidate is not fully validated. T6 must observe the provider at its downcall callsite while the
request is recorded; inability to observe it fails the test rather than allowing
a synthetic overlap claim.

### First B4 failures and bounded follow-up

T6 timed out after failing to observe the required native stack. Its owner then
reported that the request had not arrived before the native return. The original
observer required both FfmBinding.invoke and an isNativeMethod frame under
jdk.internal.foreign. That additional JDK-frame visibility assumption was not
established by the B3 precedent. The supplied output does not show stack samples,
so it cannot prove which part of that predicate was absent. It proves neither
request overlap nor positive close-during-call behavior.

The correction keeps the actual prepared SQLite step, held conflicting
transaction, finite native timeout, native BUSY assertion, request/return order
and owner-thread cleanup. Before restriction, it locates the unique spread
invokeExact callsite in the same FfmBinding.java source dynamically compiled by
buildFixture. It requires FfmBinding.invoke to appear at that exact source line
both before and after recording the request, without depending on hidden JDK
stub frames. Source/line visibility and an unambiguous callsite are mandatory;
a missing callsite or missed overlap still fails. The request reporter includes
the callsite, request time and an additional stack snapshot. This is an
observation correction, not proof that a new run will pass.

T7 returned only an ordinary Error with no local diagnostic slots from
publicMemory on the restricted thread. The unrestricted warmup had invoked the
source-exported closure, whereas T7 invoked an additional forwarding closure on
the operation object. The supplied output does not identify a failing SQLite
operation, native status, loader denial or resource-lifecycle cause; none is
inferred. The operation object now exports the same closure directly and warmup
invokes that exact exported entry. Test-only Protos diagnostic wrappers label
each prepare/bind/step/reset/column and terminal-Future cleanup phase, retaining
the original Error in the diagnostic payload. All public API operations,
expected copied values and explicit resource closes remain required. This is
entry-path alignment and diagnosis; T7's underlying failure remains unresolved
until a human rerun provides evidence. No production workaround is introduced.

### Six separate obligations

1. **Real SQLite and authorized provider.** A second experimental JAR is
   packaged under the JUnit temporary `work/sqlite-provider` directory using
   the already compiled generic backend and service descriptor
   `com.guillermomolina.protos.ffm.FfmForeignProvider`. The earlier fixture JAR
   and policy remain intact. The host authorizes both plugins, supplies only
   `ffi_test.path` for the fixture, and uses the distributable `ffi:sqlite3`
   policy's `libsqlite3.so.0` loader name with the standard resolver. No guest
   library path or expanded native policy is introduced.
2. **Protos library semantics.** The experimental operations are ordinary
   closures capturing private connection and statement state by reference.
   ABI i32/i64 domains, owned outputs and null VFS/tail pointers use existing
   std:ffi descriptors. Known single test statements deliberately ignore
   pzTail by passing NULL: this is not the complete single-statement admission
   of public std:sqlite. T7 independently runs that existing public API,
   including its terminal close Futures. No public openFile is added.
3. **Physical thread enforcement.** Existing B3 platform-thread and Landlock
   helpers install restrictions before persistent acquisition. Distinct TIDs
   and actual installation are checked where two domains are required. This
   does not arrange automatic Actor carrier affinity, filesystem capability
   propagation, or production worker dispatch.
4. **Resource custody and shutdown.** Statement retirement precedes one native
   finalize; copied integers are read while the row is current. Failed opens
   release a non-null owned result once. Ordinary close retires guest state,
   finalizes dependents and then releases the connection. closeForSession
   deliberately leaves only connection custody to the existing FfmSession
   shutdown on the owner thread. Existing read-only diagnostic counters check
   zero residual custody and zero uncertain destructions after shutdown.
   These counters do not independently trace native destructor syscalls or
   prove physical deallocation; statement ordering and the native close
   contract remain relevant. An active owner/open session prevents exterior
   fixture teardown. No new custody registry, destructor retry or Java SQLite
   downcall is introduced.
5. **Whole native-profile confinement.** B4 covers selected trusted SQLite
   Unix VFS operations only. T8 explicitly demonstrates that authorization
   does not itself apply a Filesystem grant or Landlock to another Actor.
   Independent handles and denied paths do not establish isolation against
   malicious C already executing arbitrary code in the same process, a safe
   extension-loading policy, or confinement of every native-profile effect.
6. **Crash and power-loss durability.** Reopen after orderly close and peer
   COMMIT visibility do not prove SIGKILL recovery, interruption at every
   journal/WAL phase, device-cache flushing or power-loss durability. These
   remain separate acceptance work; no NFS support is claimed.

### Preparation, coordination and observer limits

Before restriction, each Actor resolves modules, creates descriptors/bindings,
preloads backend classes through the existing B3 mechanism, reports SQLite
version/threadsafe configuration, exercises an in-memory database and releases
its resources. The public std:sqlite memory path is also warmed and closed.
The persistent target of T1/T2/T4/T6 does not open during this warmup. Exterior
fixtures and T5's existing databases are prepared explicitly as positive controls.
Opening and using a persistent experimental connection then occurs inside its
restricted thread, except the explicitly labelled unrestricted controls.

JUnit supplies bounded latches and joins, filesystem snapshots, TIDs, stack and
custody observations and assertions. No SQL ABI or connection handle is
returned to Java. Only copied scalar/Array results cross the operation boundary;
Java retains the ordinary operation object and runtime session binding for
coordination. TestReporter records preparation, TIDs, native failures, WAL,
transaction observations and owner/session closure. All B1/B2/B3 test methods
are retained; backend class preloading is extracted unchanged for reuse.

T6 compiles its known INSERT before the readiness latch, then the next native
operation is sqlite3_step. It uses SQLite's finite 2000 ms busy timeout and a
blocking transaction held by another confined owner. The observer requires
FfmBinding.invoke at the actual spread.invokeExact source callsite, records
the request and requires that callsite again before native completion. This
checks the admitted invocation position rather than the presence of hidden JDK
stub frames. A missing line number or missed window is a test failure, not a
PASS or a synthetic substitute. No sleep establishes overlap, no Java
interrupt is sent, and JUnit never invokes the SQLite session close. After the
normal return, the statement is explicitly retired once, autocommit and custody
are observed, then the owner retires guest state and closes the session. A busy
timeout counts native busy-handler waiting; it is not a bound on total SQLite
operation duration, CPU work, disk I/O or shutdown.

T2 checks sidecars while both connections remain alive; their removal after the
last close is permitted. T3 escapes apostrophes in harness-created paths and
checks exterior file bytes and sorted directory names after all confined work.
That final-state comparison is not a complete syscall trace and cannot exclude
transient operations, attempted effects or observations absent from the final
snapshot. The external observer has its own authority and contributes no grant
to the restricted threads. This experiment tests controlled SQL only, with no
security guarantee for an arbitrary public SQL executor.

### Primary sources and partial Truffle precedents

The exact GraalPy baseline is `5c8486970dead373d888f25210788cca5d79c374`:
[PythonContext.createSystemThread and finalizeContext (@TruffleBoundary)](https://github.com/oracle/graalpython/blob/5c8486970dead373d888f25210788cca5d79c374/graalpython/com.oracle.graal.python/src/com/oracle/graal/python/runtime/PythonContext.java),
[PythonLanguage.initializeThread and disposeThread](https://github.com/oracle/graalpython/blob/5c8486970dead373d888f25210788cca5d79c374/graalpython/com.oracle.graal.python/src/com/oracle/graal/python/PythonLanguage.java),
and [PythonSystemThreadTask.run](https://github.com/oracle/graalpython/blob/5c8486970dead373d888f25210788cca5d79c374/graalpython/com.oracle.graal.python/src/com/oracle/graal/python/util/PythonSystemThreadTask.java).
The exact Espresso baseline is `bd884eb3effea2b31f3fbdd7250267ba8e9eca8e`:
[EspressoReferenceDrainer.initReferenceDrain, startReferenceDrain, shutdownAndWaitReferenceDrain and ReferenceDrain.run](https://github.com/oracle/graal/blob/bd884eb3effea2b31f3fbdd7250267ba8e9eca8e/espresso/src/com.oracle.truffle.espresso/src/com/oracle/truffle/espresso/runtime/EspressoReferenceDrainer.java)
and [EspressoLanguage.finalizeContext](https://github.com/oracle/graal/blob/bd884eb3effea2b31f3fbdd7250267ba8e9eca8e/espresso/src/com.oracle.truffle.espresso/src/com/oracle/truffle/espresso/EspressoLanguage.java).
These are the supplied, previously verified partial precedents for controlled
thread lifecycle and ordered cleanup described in §14. They supply no existing
SQLite/Landlock solution, automatic native destructor affinity or arbitrary C
interruption. No production Java is authorized by these analogies.

SQLite's [open](https://www.sqlite.org/c3ref/open.html) and
[close](https://www.sqlite.org/c3ref/close.html) contracts govern failed-open
handle cleanup and the close_v2 zombie/dependent-resource distinction.
[prepare](https://www.sqlite.org/c3ref/prepare.html) and
[step](https://www.sqlite.org/c3ref/step.html) govern statement lifetime and
native statuses. [busy_timeout](https://www.sqlite.org/c3ref/busy_timeout.html)
specifies accumulated busy-handler waiting, not a total-operation deadline.
[WAL](https://www.sqlite.org/wal.html) and
[temporary files](https://www.sqlite.org/tempfiles.html) describe sidecars and
other native file effects; B4 exercises only the recorded subset.
[ATTACH](https://www.sqlite.org/lang_attach.html) and
[VACUUM INTO](https://www.sqlite.org/lang_vacuum.html) motivate the exterior SQL
probes. [Landlock](https://docs.kernel.org/userspace-api/landlock.html) provides
the host enforcement context; [Java 25 memory segments and arenas](https://docs.oracle.com/en/java/javase/25/core/memory-segments-and-arenas.html)
explains lifetime/thread accessibility, which is separate from OS authority.

### Inspection and human validation handoff

Material inspection for this slice covered the supplied root instructions;
AGENTS.work/DESIGN.md, IMPLEMENTATION.md and REFERENCE.md; src/AGENTS.md and
protos/AGENTS.md; normative FFI.md, CALLABLES.md capture, MODULES.md's FFI and
Actor-local facade boundary, ACTORS.md §24J and FILESYSTEM.md §20, and
EXECUTION_AND_CONTROL.md's ensure contract. These require explicit host native
authorization, synchronous calls, identity-preserving private capture,
Actor-local resources, exactly-once retirement and a separate enforced native
filesystem boundary. No normative text is edited.

Implementation inspection covered the existing JUnit B3 lifecycle/Probe/thread
helpers and ProtosExternalForeignProviderTest Fixture/JAR/resolver setup;
ffm-confinement-probe.protos; sqlite/abi.protos, sqlite/module.protos and
sqlite/internal/Resources.protos; FfmForeignProvider.java, FfmLibraries.java,
FfmSession.java, FfmBinding.java and FfmHandles.java; and
ProtosForeignProviderSessionBinding.java. These constrain reuse to the current
provider/policy, existing owned diagnostics, entered guest execution, permanent
session closure and statement-before-connection release. This guide's existing
B1–B3 account and the current D256-C1 decision and handoff were consulted. The
durable IMPLEMENTATION_BLOCKERS.md registry has no SQLite-specific unblock
condition; the D256 authority/cancellation gates remain open. LICENSE.TXT Part 5
and REFERENCE.md impose the preserved Java and new Protos APL notice. No
third-party source or packaging configuration is edited.

Only the three requested files are edited. No Java class, audit directory,
temporary manifest, publication, commit, version or changelog is created. One
read-only git status was inadvertently executed before the attachment's Git
prohibition was read; it reported an empty changed-path set and changed no
repository state. No subsequent Git operation, build, compiler, test, script
validation or benchmark was executed. All static checks and B4 cases remain
**NOT RUN** for the revised candidate. The earlier human-run failures and six
successful cases are recorded above; no success of this correction is inferred.

For the human, the next cheap gate is:

```sh
git diff --check
```

After reporting that result and reviewing the three-file delta, compile without
tests, then run the complete B4 focal set in a fresh Surefire JVM:

```sh
mvn -DskipTests test-compile
mvn '-Dtest=ProtosI144FfmFixtureAbiTest#b4T*' test
```

Allow a few minutes for Maven startup and the existing dynamic Java/C fixture
compilation; the normal T6 native wait adds about two seconds, while failure
paths retain bounded latch/join waits. Compilation/focal execution, any
applicable formatter/legacy-entry guard and broader regression validation remain
human-executed and unreported. The full B1/B2/B3 regression must preserve B2's
known negative/noncompliant outcome rather than relabel it. Integrated closure
validation remains with the still-open parent; B4 does not close it.

The failure follow-up additionally inspected the supplied Surefire output and
local Surefire XML, the current T6/T7 helpers and Protos source, unqualified
lookup in EXECUTION_AND_CONTROL.md, Resources.protos's failure/close paths and
ProtosHttpAsyncFacility.java's existing terminal-facility admission. These
support targeted diagnosis only: ordinary lexical closure entry and public
terminal-Future semantics are preserved, and no native stub visibility or T7
root cause is assumed. Java and Protos source APL notices remain intact;
LICENSE.TXT remains present. Only the same three authorized files are edited.

After the human whitespace/diff review and compile gate, the next affected
focal run is:

```sh
mvn '-Dtest=ProtosI144FfmFixtureAbiTest#b4T6*+b4T7*' test
```

Allow up to a few minutes including existing fixture compilation. On failure,
retain the phase-labelled T7 Error and T6 stack/request/native-status entries.
The complete B4 set remains the later regression gate after these failures are
resolved. No build, test, validation command or Git operation was executed by
the agent during this follow-up.

The second failure follow-up materially inspected the supplied 7/8 run, local
Surefire XML, startB4Owner and startB3Owner, the phase-labelled Protos operation
source and this guide's execution record. The established B3 exception handling
is reused to preserve original failures while leaving owner-domain custody and
cleanup ordering unchanged. Only the existing JUnit file and this guide change;
source APL notices are retained, with no production or specification changes.
No commands for Git, compilation, tests or validation were executed by the agent.
The next human focal run after whitespace review and compilation is T7 alone:

```sh
mvn '-Dtest=ProtosI144FfmFixtureAbiTest#b4T7*' test
```

Preserve the primary cause and all suppressed exceptions. The revised helper
remains NOT RUN; seven successful cases in the earlier candidate do not prove
this diagnostic change or establish a complete B4 PASS.

The class-preparation follow-up inspected the supplied primary/suppressed stack,
prepareRestrictedErrorClasses, ProtosDiagnosticTraceCapture.java's static
initialization and recordOrigin, ProtosActorTerminationObservation.java and
this guide's failure history. These require diagnostic and termination classes
to be available before thread restriction; they do not authorize production
changes or establish the original guest-error cause. Only the existing test
class and this guide are modified; the Java APL notice remains intact. No Git,
compiler, test or validation command was executed by the agent. The correction
remains NOT RUN; repeat the T7-only human focal command after whitespace review
and compilation, preserving the next primary and suppressed causes.

The exceptional-path follow-up inspected the latest supplied stack, the current
class-preparation hook and sqliteProbe, the Protos publicAt/publicMemory helpers,
and ProtosBytecodeRootNode.java's interceptTruffleException and
EpilogForException.perform. The exceptional epilogue preserves published lexical
state and propagates the original guest exception; this test must prepare that
existing path without altering it. Only the three authorized files change;
Java and Protos APL notices remain intact, with no production or specification
change and no agent-executed validation. After the human whitespace and compile
gates, repeat the existing T7-only command in a fresh JVM and preserve primary
and suppressed causes. No T7 PASS is inferred from this diagnostic preparation.

The terminal-projection follow-up inspected the supplied run, sqliteProbe,
warmPublicErrorPath/publicAt, ProtosBytecodeTaskExecution.runSegment's
ProtosSignalException catch and ProtosDiagnosticTraceCapture.capture/project.
The evidence distinguishes locally handled errors from terminal task failures;
only the latter invokes trace projection. Only the three authorized files were
edited, with APL notices preserved and no production/specification change.
No Git, build, test or validation command was executed by the agent. Retest the
same T7-only focal command after the human whitespace and compile gates; retain
the next primary and suppressed causes. No corrected-candidate PASS is claimed.

The openMemory dependency investigation materially inspected the latest owner
output; sqlite/module.protos and Resources.protos acquisition;
ProtosStandardImportProtocol's resolve/lookup bridges;
ProtosModuleRuntime.resolveModuleKey;
ProtosStandardLibraryModuleResolver.resolve/sourceFile/requireExactChild/confined;
ProtosLinuxConfinedFilesystemLandlock's handled read rights and single-root rule;
and the test's current error description. Resolution precedes module-cache
lookup and still requires library directory access. These facts constrain the
conclusion to the current standard-resolver experiment; they do not establish a
new language or durable-authority decision. Only this guide is edited in this
follow-up; existing source APL notices and LICENSE.TXT are unchanged. No Git,
compile, test or validation command was executed by the agent. Further identical
T7 reruns are not requested as a remedy for this source-established dependency.
The documentation delta still needs human whitespace review. No code change is
authorized or made to bypass the limitation.


### B4-R1 — Actor-local deferred ABI retention

**Latest candidate status: validated by the owner-confirmed B4 execution above.**
The following records the pre-validation candidate and its historical evidence.
At that point the earlier B4 result remained **7 PASS / 1 FAIL**;
its T7 failure chronology and conclusion above apply to the old module, whose
repeated ABI import required access to standard-library directories. Inspection
identifies resolution before cache lookup and real directory enumeration as a
cause consistent with the observed `openMemory` Error. No syscall trace proves
that this was the only cause. Success of this correction is not inferred.

`protos/lib/sqlite/module.protos` now creates private `ABI: null` state alongside
its existing dependencies. `openMemory` imports `./abi.protos` only while that
binding is null, assigning it only after the import returns successfully. The
first acquisition still uses ordinary authorized import and resolution. Module
import itself loads no ABI or native library. No public slot, alternate FFM
acquisition route, resolver bypass or second general module cache is added.

The semantic module cache still maps resolved canonical identities to Actor-local
instances. Private retention instead keeps an already acquired dependency through
ordinary lexical capture and assignment in one module initializer. It avoids a
new import expression on subsequent calls; it does not change what an import
means. Each Actor prepares its own module and ABI. Retention grants no authority,
shares no session or connection, and cannot reopen a revoked FFM session. A cold
Actor still needs initial dependency-resolution authority. There are no Java
production changes and no normative changes.

The expanded T7 first proves that the canonical physical `protos/lib/sqlite`
directory is accessible outside restriction, then requires an actual
`Files.newDirectoryStream` access denial inside the unchanged Landlock grant.
It runs the prepared public memory closure three times there, requiring copied
values `9223372036854775806`, `41`, `9223372036854775806`, all existing statement
and connection terminal closes, zero owned/uncertain custody and confined session
close. Directory denial plus successful prepared operations would demonstrate
that this path needs no successful standard-directory read; it is not a syscall
trace proving that no filesystem operation was attempted.

| B4-R1 case | Required evidence | Status |
|---|---|---|
| T7 expanded | Three public memory operations under restriction with directory access denied and owner-thread close | PASS — owner-confirmed B4 run, 19 November 2026 |
| T9 `b4T9PublicSqliteAbiPreparationRemainsActorLocal` | Stable public module identity within each Actor, distinct modules and sessions across Actors; separate restricted physical threads and grants; copied values, no residual custody and owner-thread close | PASS — same B4 run |
| T10 `b4T10CachedSqliteAbiCannotReviveClosedProviderSession` | Prepared public closure refuses after explicit session close; original binding remains closed, session lookup refuses replacement, zero owned/uncertain custody; idempotent cleanup | PASS — same B4 run |
| Public lifecycle regression | Three successive acquisitions, distinct identities and schemas, independent survival after peer close, repeated terminal Future observations and ordinary resource release | PASS — owner-confirmed 41/41 foreign-provider lane |
| Missing-provider regression | Import succeeds and retains public module identity; three acquisitions signal ordinary Errors without yielding a SQLite resource or native authority | PASS — same foreign-provider lane |

The lifecycle regression observes public behavior, not resolver I/O or native
destructor counters. Repeated Future reads and closes observe one recorded
terminal result; native custody in the real-provider experiments supplies separate
release evidence. Host-side identity observations in T9 do not transfer guest
modules or substitute for guest transfer semantics.

Materially inspected for this patch: `AGENTS.work/DESIGN.md`,
`AGENTS.work/IMPLEMENTATION.md`, `AGENTS.work/REFERENCE.md`, `protos/AGENTS.md`,
`src/AGENTS.md`, `spec/semantics/MODULES.md` (private initialization, lexical
capture, Actor-local identity, cache and failure), and
`spec/runtime/ABSTRACT_RUNTIME.md` (ordinary deferred dependency closures).
Implementation evidence includes the seven changed files, `protos/lib/core/import.protos`,
`protos/lib/sqlite/abi.protos`, `protos/lib/sqlite/internal/Resources.protos`,
`protos/lib/ffi/module.protos`, `ProtosForeignProviderSessionBinding.java`,
`ProtosForeignProviderProcessLifecycle.java`, `ProtosPolyglotProcessContext.java`,
`ProtosLanguageContext.java`, `Makefile` and `LICENSE.TXT`. These constrain the
patch to ordinary lexical retention, existing importer authorization, existing
resource/Future cleanup and permanent session revocation. The earlier ledger
assessment recorded in this section remains unchanged; no blocker is discharged.
All modified source files preserve their existing APL Part 5 notices; no new
source, third-party change or packaging change is introduced.

Historical pre-execution status, superseded by the confirmed run above:
B1 remains **13/13 PASS observed**; B2 retains **T5 noncompliance reproduced**;
B3 remains **6/6 PASS observed**; previous B4 remains **7 PASS / 1 FAIL**.
**PLAT074 OPEN; D256 pending; LIB031 BLOCKED.** B4-R1 does not establish SQLite
confinement, authorize a durable API, claim 10/10 PASS or unblock a dependency.

No Git, build, compiler, test or validation command was executed for B4-R1.
The owner should first inspect the changed paths, run `git diff --check` and
review the exact seven-file diff. Then run `mvn -DskipTests test-compile`,
`mvn '-Dtest=ProtosI144FfmFixtureAbiTest#b4T*' test` and the affected public
foreign-provider regressions with `make test-protos-foreign` after packaging the
current candidate. Maven and the dynamic Java/C fixtures may take several
minutes; the external provider lane takes additional build/integration time.
Run each gate only after its predecessor succeeds. Integrated `make test`
validation remains required for final shared-library closure, after cheaper
gates, and is not claimed here.

That was the pre-execution handoff; the owner-confirmed run at the start of §15
supersedes its NOT RUN status. Integrated `make test` remains unaccredited.


## 16. PLAT074-B5 — Independent OS processes, SIGKILL recovery and locking

**Observed B5 result: 7/7 PASS on 20 November 2026.** These are acceptance tests,
not new production machinery or selection of candidate B. PLAT074 remains
**OPEN**, D256 **pending**, LIB031 **BLOCKED**. B1 remains **13/13 PASS**, B2-T5
**noncompliance reproduced**, B3 **6/6 PASS**, B4 **10/10 PASS**, with the historical
failures retained above. No power-loss result follows from those runs.

| Case and Java method | Required, executable acceptance evidence | Status |
|---|---|---|
| B5-T1 `b5T1TwoIndependentOsProcessesReopenCommittedSqlite` | A commits value 10 and exits normally; independent B reopens, checks count 1, maximum/sum 10 and integrity. Both deny exterior read/write opens. | PASS (20 November 2026) |
| B5-T2 `b5T2WalFullCommittedTransactionSurvivesSigkill` | A verifies WAL/FULL, commits 10 and 20, retains a live connection and nonempty WAL plus SHM; parent kills A after COMMITTED; B recovers exact count 2, maximum 20, sum 30 and integrity. | PASS (20 November 2026) |
| B5-T3 `b5T3UncommittedTransactionRollsBackAfterSigkill` | A new writer inserts 20 inside BEGIN IMMEDIATE with autocommit 0; SIGKILL precedes any voluntary rollback/close; recovery retains only committed 10, then commits 30. Another JVM checks count 2, maximum 30, sum 40. | PASS (20 November 2026) |
| B5-T4 `b5T4IndependentOsProcessesRespectSqliteWriteLocks` | A holds an uncommitted 20; independent B sets 100 ms busy timeout, sees SQLITE_BUSY on insert 30, autocommit 1 and only 10 visible; A commits, B explicitly retries and commits; third JVM checks count 3, maximum 30, sum 60 and integrity. | PASS (20 November 2026) |
| B5-T5 `b5T5WalRecoveryWithLiveReaderAndInterruptedWriter` | B pins a read snapshot containing only 10; A commits 20, B still observes count 1/sum 10; A is killed with its connection live, B closes on its owner, C sees 10 and 20, quick/integrity checks and copied checkpoint results. | PASS (20 November 2026) |
| B5-T6 `b5T6RollbackJournalRecoversAfterKilledWriter` | DELETE/FULL initial 10 survives a killed writer that updates rows and spills a 12000-row insertion beyond a five-page cache; populated journal is observed before killing, recovery sees only 10, commits 30, and third JVM verifies the new committed state and integrity. | PASS (20 November 2026) |
| B5-T7 `b5T7ProcessBoundaryDoesNotImplicitlyExtendLandlockGrant` | Confined A opens its own database and denies both exterior open modes; unrestricted B with the same provider opens the exterior database; live owned SQLite references fail the canonical guest transfer gate with NonTransferableValue in both JVMs; A closes confined. | PASS (20 November 2026) |

### Process, thread, dependency and protocol boundaries

A Protos Process and Actor are language/runtime entities, not OS PIDs. Every
scenario participant here runs the existing test class's `main(String[] args)`
in a distinct JVM created by ProcessBuilder. Each child constructs a real
`ProtosExternalForeignProviderTest.Fixture` and an Actor with the existing
plugins. The parent checks positive, distinct PIDs different from its own,
normal exit 0, or Linux SIGKILL exit 137 after destroyForcibly and timed waitFor.
A separate confined control JVM seeds the exterior database with its own grant;
its grant does not belong to the scenario participants. T7's deliberately
unrestricted observer is the only child role without Landlock.

The launcher passes the actual Surefire test classpath (or the effective JVM
classpath), both already-built provider JARs, the C fixture library, absolute
temporary paths and the parent PID. It explicitly supplies the current native
access/Unsafe options and propagates the configured Truffle resource cache.
Missing dependencies fail with child diagnostics; there is no in-parent fallback
or Maven invocation in a child. No extra Java class or production API is added.

Each child prepares `sqliteProbe`, the B4 classes, public SQLite memory paths,
and B5 integrity/checkpoint/transfer failure paths before installing Landlock.
Only memory databases are opened by that preparation. The restricted platform
thread publishes CONFINED after successful installation, proves denied exterior
read/write opens and then opens its persistent database. The parent requires
this sequence before issuing scenario commands. Thread-local confinement is
retained during SQL, statement finalization, connection release and session
close; the child records its physical Linux TID and requires the same TID at
normal connection close. An existing unrestricted JVM sibling does not become
restricted merely because an Actor was created or another thread installed
Landlock. This is the physical-domain experiment of §§13–15, not a production
scheduler design.

Stdout events are complete newline-terminated lines with an explicit flush:
`READY`, `CONFINED`, `TRANSACTION_STARTED`, `COMMITTED`, `UNCOMMITTED`, `BUSY`,
`RELEASED`, `CLOSED`, `FAILED`. READY first means prepared, then persistent open,
and subsequently a verified command result in the parent's fixed sequence.
CLOSED follows owner/session cleanup and Process Context teardown. The stdin
vocabulary is fixed: INIT_WAL, INIT_DELETE, COMMIT, LOCK, UNCOMMITTED, SPILL,
RELEASE, BUSY, RETRY, READ, SNAPSHOT, VERIFY_INITIAL, RECOVER_WRITE, VERIFY_TWO,
VERIFY_THREE, VERIFY_RECOVERED, CHECKPOINT, TRANSFER, CLOSE. No SQL or native
address arrives through that transport. The parent sends no CLOSE to killed
writers. SIGKILL is issued only after the acknowledged condition while the
connection and restricted owner thread remain alive.

Background pipe readers consume output continuously; diagnostic lines and the
retained log are bounded, and only complete recognized event lines reach the
bounded queue. Event/command waits, thread joins and process termination waits
have deadlines. FAILED, EOF at an unexpected phase, nonzero normal exit or
timeout fails the test, without converting child failures into skips. The only
skip gates are the existing Linux/architecture/compiler/confinement platform
preconditions. On every failure the parent kills all survivors and waits for
each; it never closes a native resource owned by another JVM. Logs are retained
only for test execution, with no repository control files or audit artifacts.

### SQLite recovery, integrity and authority limits

All SQL runs through the experimental Protos object's closures. Existing FFI
declarations, owned acquisition, release and session revocation are retained.
New `setWalFull` and `setRollbackJournal` query effective journal mode and require
synchronous level 2 inside Protos, returning copied ordinary Arrays.
`integrityOk` and `quickCheckOk` use table-valued PRAGMAs, require exactly one
affirmative result and propagate query errors. Merely opening the file is no
integrity proof. `committedCount` and `committedValue` use fixed fixture SQL,
with count, maximum, an independent sum and exact membership/multiplicity of
values 10, 20 and 30 asserted by each reader. `checkpoint`
copies SQLite's three status columns before finalizing the statement.
`ownedConnectionForTransferTest` exposes the existing live reference only to the
test host's canonical transfer admission assertion; it neither changes custody
nor transports it to another JVM. No public std:sqlite API is extended.

The transfer test exercises the established guest Actor snapshot gate on the
actual SQLite resource, requiring NonTransferableValue before serialization.
There is no guest native-address reconstruction or cross-process handle import
API in FFI R1. This bounded negative evidence does not claim to audit every
possible host/native IPC mechanism or isolate the process from arbitrary native
code. The unrestricted observer has its own host authority, not an inherited
Landlock directory grant. Descriptor revocation, guest retirement and physical
destruction remain distinct: normal cleanup observes zero owned/uncertain
custody and a closed session on the owner thread; SIGKILL supplies no destructor
or graceful-finalization evidence. The OS reclaims that process's resources,
and SQLite recovery is assessed independently in a fresh JVM.

[SQLite WAL](https://www.sqlite.org/wal.html) describes snapshots, writer locking,
WAL/SHM sidecars, checkpoints and the same-host shared-memory requirement. WAL
on NFS is not supported or tested here. No sidecar is deleted or renamed to
force recovery. A reader may prevent checkpoint completion;
[wal_checkpoint_v2](https://www.sqlite.org/c3ref/wal_checkpoint_v2.html) explains
the busy/progress distinction. CHECKPOINT records all three PRAGMA results and
accepts busy 0 or 1; a busy checkpoint is not interpreted as corruption.
[PRAGMAs](https://www.sqlite.org/pragma.html) govern effective journal/synchronous
settings, quick_check, integrity_check and checkpoint result columns.

[Atomic commit](https://www.sqlite.org/atomiccommit.html) and
[How to corrupt a database](https://www.sqlite.org/howtocorrupt.html) explain why
native locking, flushes, correct journals and filesystem assumptions matter.
A returned COMMIT followed by process termination is different evidence from an
unconfirmed transaction whose changes must disappear. Both WAL/FULL and
DELETE/FULL here test recovery after SIGKILL, **not power interruption**, storage
cache loss, torn sectors or dishonest flushes. No power-loss durability promise
is made for either configuration. Rollback journal removal after successful
recovery is allowed; a journal is never manually deleted to simulate a crash.

[Landlock](https://docs.kernel.org/userspace-api/landlock.html) supplies the
physical-thread enforcement context and authority limits;
[Java 25 Process](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Process.html)
supplies process/termination/pipe contracts. Neither JVM FFM checks nor a trusted
native provider make arbitrary native code memory-safe or grant-confined.
The GraalPy and Espresso precedents and exact baselines in §15 remain partial
thread lifecycle/finalization precedents, with no claim that either implements
an equivalent SQLite/Landlock system. Native Image, Windows and macOS are not
tested. These official sources are incorporated as references; no fresh remote
source verification was performed in this local-files-only editing session.

### Inspection and human validation handoff

Material inspection: supplied root instructions and attachment;
`AGENTS.work/DESIGN.md` (no architecture selection),
`AGENTS.work/IMPLEMENTATION.md` (human-executed checks and existing blocker gates),
`AGENTS.work/REFERENCE.md` and `LICENSE.TXT` (APL Part 5), `src/AGENTS.md`,
`protos/AGENTS.md`, `spec/AGENTS.md`, `spec/PROTOS_LANGUAGE_SPEC.md` (authority map),
`spec/semantics/MODULES.md` (Actor-local foreign modules),
`spec/semantics/CALLABLES.md` (ordinary FFI closures), `spec/PROTOS_GRAMMAR.md`
(source syntax), `spec/semantics/FFI.md`
(exact admission, custody, nontransferability and in-process limits),
`ProtosExternalForeignProviderTest.java` (Fixture lifecycle), the three modified
files (existing experiment, test host and history), and `pom.xml` (effective JVM
options/classpath environment). No normative specification is changed. The
remote blocker ledger and durable D256/PLAT074 records were not freshly read:
this session is restricted to local files. Their prior assessment in §§3/15 is
retained as historical context; no blocker is discharged or design approved.
The Java and Protos source notices are preserved against LICENSE.TXT Part 5;
no source file, generated/third-party file or packaging configuration is added.

Only the three requested files are modified. One read-only `git status --short`
was inadvertently executed at the beginning and reported a clean checkout;
no other Git operation, build, compiler, test or validation was executed.
That editing session left B5 **NOT RUN**; the subsequent owner-supplied run below supersedes that initial status. No case is marked NOT IMPLEMENTED; T7's
transfer assertion has the bounded scope described above.

Human gates, run sequentially only after the preceding gate passes:

```sh
git diff --check
```

Then inspect the exact three-file diff and run the compilation-only gate:

```sh
mvn -DskipTests test-compile
```

The focal suite dynamically compiles its existing Java/C provider fixtures and
starts multiple JVMs; it costs more than the compilation-only check. No duration
is predicted. The original B5 handoff requested the complete affected B1–B5 class:

```sh
mvn -Dtest=ProtosI144FfmFixtureAbiTest test
```

Then package and exercise the affected Protos foreign-provider lane:

```sh
mvn -DskipTests package
make test-protos-foreign
```

Integrated `make test` remains the final FULL gate for shared executable test
infrastructure/closure after these cheaper gates pass. It has not been run or
accredited by this record. Portable distribution success above belongs to the
owner-confirmed B4 candidate, not this new B5 candidate.

### Owner-supplied B5 execution — 20 November 2026

| Gate / observation | Reported result |
|---|---|
| `git diff --check` | PASS |
| `mvn -DskipTests test-compile` | BUILD SUCCESS |
| `mvn -Dtest=ProtosI144FfmFixtureAbiTest test` | 40 tests; 1 failure; 0 errors; 1 skipped |
| Class duration | 58.66 seconds |
| Maven total duration | 1 minute 23 seconds |
| Overall Maven result | **BUILD FAILURE**, exclusively B2-T5 |
| B5-T1–T7 | **7/7 PASS**, derived from the sole class failure belonging to B2-T5 |
| B2-T5 physical evidence | Restricted owner TID **352634**; exterior destructor TID **352635**; affinity assertion failed |

This is not a PASS for the complete class. The B2-T5 failure remains required
adversarial evidence; it is neither disabled nor repaired by this slice.
Packaging (`mvn -DskipTests package`), `make test-protos-foreign` and `make test`
are **not accredited for the B5 candidate** by this execution. B1–B4 evidence
and the B2 chronology above remain unchanged. B5 SIGKILL recovery does not prove
power-loss durability or production-wide native confinement.

## 17. PLAT074-B6 — Native authority boundaries

### Owner-reported B6 execution — 20 November 2026

| Gate / observation | Reported result |
|---|---|
| `git diff --check` | No errors |
| `git diff --cached --check` | No errors |
| `mvn -DskipTests test-compile` | BUILD SUCCESS |
| `mvn '-Dtest=ProtosI144FfmFixtureAbiTest#b6T*' test` | BUILD SUCCESS |
| Focal tests | 7 tests; 0 failures; 0 errors; 0 skipped |
| Focal duration | 5.181 seconds |
| B6-T1–T7 | **7/7 PASS observed** |

This is a **focal** result only. The complete JUnit class, packaging
(`mvn -DskipTests package`), `make test-protos-foreign` and `make test` were
**not run** after B6, so no complete-class or integrated PASS is claimed.
B2-T5 keeps its reproduced destructor-affinity failure (§16 owner-supplied B5
execution: restricted owner TID 352634, exterior destructor TID 352635); B6
neither repairs nor disables it.

SQL and owned acquisition remain in the experimental Protos closures; Java
supplies only controlled files, links, host coordination and assertions. No
production Java, public SQLite API, grant or scheduler changes. The original
hypotheses, required outcomes and negative/scope controls are preserved below;
the status column now records the observed outcome.

| Case / JUnit method | Hypothesis and positive control | Required adversarial outcome | Status |
|---|---|---|---|
| B6-T1 `b6T1PreopenedSqliteConnectionRetainsExteriorReadAccess` | Same physical Actor owner opens exterior value 71 before installing Landlock, retains it and reads the copied marker after restriction. | Read remains 71; close on restricted owner; fresh exterior open returns SQLITE_CANTOPEN; zero custody and owner session close. **Negative control:** PASS exposes retained authority. | PASS observed 2026-11-20 |
| B6-T2 `b6T2UnrestrictedHostEntryCanBypassRestrictedCarrier` | Restricted owner reads/closes granted DB, keeps session alive and parks outside guest code. | Distinct unrestricted platform TID enters the **same Actor/session** sequentially, reads exterior 72 and closes normally; restricted owner closes session. **Negative control:** PASS exposes explicit host-entry authority. | PASS observed 2026-11-20 |
| B6-T3 `b6T3SymlinkOutsideGrantCannotBeOpenedBySqlite` | Observer opens preexisting symlink to exterior value 73; restricted owner reads granted DB. | SQLite open through exterior-resolving link returns SQLITE_CANTOPEN; link target and exterior bytes intact. | PASS observed 2026-11-20 |
| B6-T4 `b6T4PreexistingHardlinkDemonstratesPathNotDatabaseIdentity` | Cleanly closed DELETE/FULL exterior DB, in-grant hardlink and Files.isSameFile establish a shared physical file. | Exterior name denied; in-grant hardlink reads 74; bytes unchanged. **Scope control:** PASS permits content through an already granted name. | PASS observed 2026-11-20 |
| B6-T5 `b6T5ExplicitUriModeStillObeysRestrictedFilesystemGrant` | Observer validates exterior file URI; restricted URI read returns granted 75. | Explicit URI flags 65 and 70 both deny exterior acquisition with SQLITE_CANTOPEN; no live/uncertain custody or byte changes. | PASS observed 2026-11-20 |
| B6-T6 `b6T6UriAttachCannotEscapeRestrictedDirectory` | URI-enabled primary ATTACHes granted secondary, checks schema and value 176, then DETACHes. | Exterior URI ATTACH fails acquisition; no exterior schema; primary read and insert still work; exterior bytes unchanged. | PASS observed 2026-11-20 |
| B6-T7 `b6T7SymlinkRetargetAfterRestrictionCannotExpandGrant` | Restricted owner opens initial in-grant symlink, reads 77 and closes. | Observer deterministically retargets link to exterior 177 between latch phases; fresh link open denied; direct granted path still reads 77. | PASS observed 2026-11-20 |

Reading the PASS results correctly: T3, T5, T6 and T7 PASS means Landlock
denied the exterior acquisition attempted **on the restricted thread** in that
controlled path. T1, T2 and T4 are negative/scope controls: their PASS means
the **escape path exists** — retained pre-grant authority (T1), unrestricted
host entry into the same Actor/session (T2) and a granted hardlink alias to an
exterior-named file (T4). None of these results is a security proof of the
complete runtime.

`openUriReadOnly` and `openUriFile` reuse `acquire` and the existing
`sqlite3_open_v2` binding with flags 65 and 70. Failed-open handles keep the
existing single-release rule and native status. `nativeReadMarker` copies only
`SELECT value FROM t LIMIT 1`; `readOnlyProbe` requires an open connection and
runs that read-only query without opening additional connections or enabling
journals/WAL. `prepareB6` warms these closures with memory databases before
restriction, without changing existing tests or fixture preparation.

T1 intentionally installs the unchanged Landlock policy inside the owner
experiment **after** acquisition. Its explicit installation flag and TID checks
are the evidence; the outer launcher has no preinstalled grant. Cleanup still
runs on that now-restricted physical thread. T2 uses the existing platform
launcher and owner-side custody checks, with an atomic segment admission seal.
On a timeout with an active host segment it withholds owner teardown and reports
failure; `finishB3` withholds exterior Context teardown while a session is open.
It never destroys pending resources concurrently from the exterior thread.
All latch waits and joins are bounded; no sleep or probabilistic race is used.
Cleanup failures remain primary or suppressed failures. No uncertain destructor
is retried. T7 replaces the symlink only after the first SQLite connection closes.
ATTACH SQL contains only fixed schema names and fixture-controlled canonical URI
literals, with SQL quotes doubled; no arbitrary SQL input or extensions are added.

These distinguish **preexisting resource authority**, **physical carrier
restriction**, **symlink resolution**, **hardlink aliases**, **explicit URI
interpretation**, **secondary ATTACH acquisition**, and **later namespace
mutation**. A directory path policy alone cannot establish exclusive logical
SQLite database identity. A preexisting hardlink may expose the same content
through a granted hierarchy. Permitting unrestricted `ffi:sqlite3` on another
physical thread does not create an authority barrier per Actor. T2 tests an
explicit host entry; it makes no claim that the ordinary scheduler migrates an
Actor automatically. Neither T1 nor T2 nor T4 is a positive confinement proof.

### Evidence summary B1–B6

| Campaign | Result |
|---|---|
| B1 | 13/13 PASS |
| B2 | T5 violation reproduced (exterior destructor TID ≠ restricted owner TID) |
| B3 | 6/6 PASS |
| B4 | 10/10 PASS |
| B5 | 7/7 PASS |
| B6 | 7/7 PASS (focal, 20 November 2026) |

A PASS of a negative control (B6-T1, B6-T2, B6-T4) **confirms that an escape
path exists**; it is not evidence that the runtime is secure. A PASS of a
denial case (B6-T3, T5, T6, T7) holds only for the controlled thread, policy,
fixture and sequence exercised. None of B1–B6 is a security proof of the
complete runtime, and the B2-T5 failure remains open.

## 18. Preliminary decision packet — owner decision pending

This evidence update supplements §§4–7; it does not ratify their earlier
speculative scores. For this preliminary packet the matrix below supersedes
§5 wherever evidence is insufficient. Scores are provisional **1 (poor) to 5
(strong)**, with confidence H/M; **NOT ESTABLISHED** means no justified score,
not zero. There is no weighted total or automatic B/D selection. PLAT074 #1122's
twelve dimensions remain distinct. B6 does not change any score below; it
sharpens the reasons recorded for B.

A = trusted native SQLite without confinement; B = normal Unix VFS on physically
Landlock-restricted execution; C = custom SQLite VFS; D = isolated OS process
with IPC; E = other native restrictions; F = defer confined durable access.

| Dimension | A | B | C | D | E | F |
|---|---|---|---|---|---|---|
| Correctness | 1/H: no confinement | 2/H: current lifecycle fails B2-T5; B6-T1/T2/T4 escape paths exist | NOT ESTABLISHED | NOT ESTABLISHED | NOT ESTABLISHED | 5/H: withholds durable acquisition; supplies no durability |
| Protos alignment | 2/M: trusted FFI, missing durable authority | 3/M: ordinary Protos SQL demonstrated B4/B5; execution-domain obligation open | NOT ESTABLISHED | NOT ESTABLISHED | NOT ESTABLISHED | 4/H: preserves current memory-only boundary |
| Present proportionality | NOT ESTABLISHED | NOT ESTABLISHED | NOT ESTABLISHED | NOT ESTABLISHED | NOT ESTABLISHED | 5/H: no additional mechanism |
| Incremental growth | NOT ESTABLISHED | 3/M: B1–B6 extend experiments without production change | NOT ESTABLISHED | NOT ESTABLISHED | NOT ESTABLISHED | 4/M: later additive proposal possible; workloads delayed |
| Future-option resilience | NOT ESTABLISHED | NOT ESTABLISHED | NOT ESTABLISHED | NOT ESTABLISHED | NOT ESTABLISHED | 4/M: no new host mechanism committed |
| Scalability | NOT ESTABLISHED | NOT ESTABLISHED | NOT ESTABLISHED | NOT ESTABLISHED | NOT ESTABLISHED | NOT ESTABLISHED |
| Conceptual simplicity | NOT ESTABLISHED | NOT ESTABLISHED | NOT ESTABLISHED | NOT ESTABLISHED | NOT ESTABLISHED | 5/H: existing memory-only surface retained |
| Portability | NOT ESTABLISHED | 1/H: Landlock mechanism is Linux-specific; tested JVM profile only | NOT ESTABLISHED | NOT ESTABLISHED | NOT ESTABLISHED | 4/M: adds no host dependency; not a portability certification |
| Runtime cost | NOT ESTABLISHED | NOT ESTABLISHED | NOT ESTABLISHED | NOT ESTABLISHED | NOT ESTABLISHED | 5/H: no added durable runtime mechanism |
| Failure and operability | 1/H: in-process native faults share JVM | 2/M: B5 recovery evidence, B2-T5 cleanup-domain violation remains | NOT ESTABLISHED | NOT ESTABLISHED | NOT ESTABLISHED | 3/M: existing service scope; no durable recovery service |
| Deferral and reversibility | NOT ESTABLISHED | NOT ESTABLISHED | NOT ESTABLISHED | NOT ESTABLISHED | NOT ESTABLISHED | 4/M: no new API migration; consumer delay remains |
| Evidence and implementation risk | 2/M: unrestricted access observed B4/B5, unsuitable confinement | 3/M: bounded B1–B6 evidence; complete runtime boundary disproved by B2-T5, B6-T1, B6-T2, B6-T4 | NOT ESTABLISHED | NOT ESTABLISHED | NOT ESTABLISHED | 5/H: deferral requires no new implementation |

### Separated properties for B

B is never given a single combined rating. Its properties are assessed
independently:

| Property | Status for B from B1–B6 |
|---|---|
| SQLite feasibility | Demonstrated in the controlled experimental path (B1, B4, B6 denial cases): the Unix VFS opens, reads, writes and closes granted databases on a Landlock-restricted thread. |
| Transactional correctness | Bounded evidence: B4 commits/rollbacks and B5 independent-process locking and SIGKILL recovery. No power-loss, WAL-at-scale or long-running certification. |
| Filesystem confinement | Holds only for operations executed on the restricted thread after the grant; B6-T1 (pre-grant resource) and B6-T4 (hardlink alias) show the limits of path-based policy. |
| Actor authority | **Not guaranteed.** B6-T2: an unrestricted host thread entered the same Actor/session and read an exterior database. |
| FFM custody | Single release and native status preserved; **destructor affinity not guaranteed** (B2-T5). |
| Complete runtime guarantee | **Not established and currently contradicted** by the four counterexamples below. |

### Material counterexamples against complete B enforcement

1. **B2-T5:** native close executed from an exterior thread (owner TID 352634,
   destructor TID 352635).
2. **B6-T1:** a resource opened before Landlock installation retains exterior
   read access after restriction.
3. **B6-T2:** host entry into the same Actor from another, unrestricted thread
   executes native SQLite with the session's resources outside the domain.
4. **B6-T4:** an authorized hardlink exposes a file whose exterior path
   identity is denied.

While any of these paths remains possible, the matrix cannot give B a positive
rating for complete security correctness.

### Candidate evidence, limitations and strongest counterarguments

| Alternative | Evidence actually available from B1–B6 | Limitations and strongest counterargument |
|---|---|---|
| A — trusted native | B4 unrestricted Actor and B5 unrestricted process controls access exterior SQLite through the authorized provider. | This supplies useful native access but **cannot be described as confined SQLite**. It fails the motivating authority boundary, regardless of convenience. |
| B — Unix VFS plus Landlock execution | B1 13/13 PASS; B3 6/6 PASS for cooperative owner cleanup; B4 10/10 PASS for real Protos SQLite on restricted owners; B5 7/7 PASS for independent process locking and SIGKILL recovery; B6 7/7 PASS (focal) for authority boundaries. **SQLite Unix VFS works under Landlock in the controlled experimental path, but the current runtime does not guarantee the required authority or thread affinity.** | **Strongest counterargument:** the four material counterexamples above (B2-T5, B6-T1, B6-T2, B6-T4). Cooperative experimental sequencing is insufficient. No scale, portability, power-loss or overhead certification. |
| C — custom VFS | B1–B6 exercise the normal VFS, providing no implementation or performance evidence for a custom VFS. | File access alone does not justify replacing mature locking, journal and WAL machinery. Strongest argument for C: it could expose an explicit host capability boundary; callback substrate, locking/SHM correctness and failure handling remain unimplemented and unmeasured. |
| D — isolated OS process and IPC | B5 establishes independent JVM/PID orchestration, locks and recovery across SIGKILL; this is bounded evidence about OS-process separation. **D remains a pending candidate.** | The existence of independent JVMs in B5 does **not** demonstrate a secure IPC implementation or a whole-process sandbox. Strongest counterargument: command uncertainty, cancellation, packaging, handle identity, IPC and startup/copy costs are unimplemented/unmeasured. |
| E — other native restrictions | B1–B6 supply Landlock evidence only, not comparable experiments for other restrictions. Existing §6 references remain precedents. | Strongest counterargument: platform policies and lifecycle mechanisms differ; a portable portfolio cannot be inferred from one Linux experiment. Coverage, compatibility and cost are NOT ESTABLISHED. |
| F — defer | Existing memory-only API can remain unchanged; no experiment requires granting production durable authority. | Strongest counterargument: the requested durable workload remains unavailable. Deferral preserves options but cannot stand in for a durable implementation or claim its correctness. |

### Provisional recommendation for the owner's decision

B is viable for **controlled SQLite operations in the tested Linux/JVM profile**.
It still lacks a runtime guarantee that every operation and destructor executes
under the same authorized physical domain, and B6 adds three further escape
paths (pre-grant resources, unrestricted host entry, hardlink aliases). No
performance score can compensate for these missing invariants. D offers a
different process isolation boundary, but its production IPC, uncertainty
semantics and costs have not been implemented or measured. C is not justified
merely because SQLite needs files. A is trusted native access and cannot be
labelled confined. The owner must **explicitly approve the definitive mechanism
before any production modification**. This packet selects neither B nor D,
closes no issue and unblocks no durable API.

A future high-Actor-count or long-lived service could expose per-domain carrier
costs and in-process native globals for B; escape requires an explicitly approved
host-domain replacement while preserving ordinary library behavior. A future
low-latency deployment could make D's IPC cost unacceptable; escape requires
revisiting deployment/transport before exposing a public contract. Neither cost
has been measured. F delays consumers but avoids shipping a compatibility promise
that later needs tightening. C/E should be revisited only with concrete missing
requirements and an implementation/evidence plan. The invariants of §19 and the
contracts of §20 state what any definitive packet must resolve.

### Primary sources

- [Landlock userspace API](https://www.kernel.org/doc/html/latest/userspace-api/landlock.html): enforcement domain, filesystem hierarchy and existing authority limits.
- [SQLite URI filenames](https://www.sqlite.org/uri.html): explicit connection URI mode and ATTACH URI interpretation.
- [sqlite3_open_v2](https://www.sqlite.org/c3ref/open.html): open flags, status and failed-open handle cleanup.
- [SQLite temporary files](https://www.sqlite.org/tempfiles.html): auxiliary files require separate consideration.
- [SQLite WAL](https://www.sqlite.org/wal.html): sidecars, locks, checkpoints and recovery context; not a B6 execution result.
- [sqlite3_close / close_v2](https://www.sqlite.org/c3ref/close.html): dependent-resource cleanup and deferred physical destruction.

Truffle precedents are recorded with their limits in §21.

## 19. Minimum durable-authority contract

These invariants are obligations any definitive durable-SQLite mechanism must
satisfy. Identifiers are neutral document labels (`DA-1`…`DA-12`), not names
for code entities. **None is implemented.** "Existing evidence" cites only
experimental observations; "future acceptance condition" is what a later,
owner-approved slice must demonstrate.

| ID | Invariant | Existing evidence | Counterexample or pending coverage | Future acceptance condition |
|---|---|---|---|---|
| DA-1 | **Explicit grant.** An Actor cannot acquire a durable connection merely by importing `std:sqlite` or holding a path String; the host must supply an explicit, verifiable durable authority. | Importing `std:sqlite` acquires no native authority; only `openMemory()` exists; `ffi:sqlite3` requires host authorization (§2; [sqlite.md](sqlite.md#authority-and-target)). | No durable grant form exists; B4–B6 use a private test module, not a grant. | An approved grant form without which every durable acquisition fails, and tests showing a String path alone cannot open a file. |
| DA-2 | **Mandatory physical domain.** Every SQLite operation with possible filesystem effects runs inside the authorized domain: open, prepare, step, reset, finalize, checkpoint, close, owned destructors, session cleanup, recovery and reopen. Not only `sqlite3_open_v2`. | B4 runs open/prepare/step/finalize/close on restricted owners; B3 shows cooperative owner-side close. | B2-T5 (exterior close); checkpoint, recovery-reopen and session cleanup under the domain not enforced. | Every listed operation is shown, including on failure and teardown paths, to execute only within the domain, with an exterior attempt rejected before entering C. |
| DA-3 | **Host entry.** Entry from an unauthorized physical thread cannot execute native operations with the confined session's authority. | None positive. | B6-T2: unrestricted host thread entered the same Actor/session and read exterior value 72. Actor-local identity is insufficient. | Host entry from an unauthorized thread is refused or routed into the domain, demonstrated by a T2-style adversarial test failing to reach C outside it. |
| DA-4 | **Custody.** Every release of native owned resources respects the domain of their acquisition. | Single release and native status preserved (B2–B4). | B2-T5: current `FfmSession.close()` does not guarantee destructor affinity. | Destructors (explicit release, session close, Actor/Process teardown) run only in the acquisition domain; B2-T5's adversarial assertion passes without being weakened. |
| DA-5 | **Pre-grant resources.** A native connection or descriptor opened before Landlock installation is not automatically revoked. | — | B6-T1: pre-opened connection still read exterior value 71 after restriction. | A defined initialization/acquisition order in which no durable resource exists before the grant, with a T1-style test showing no pre-grant resource reaches the domain. |
| DA-6 | **Grant identity.** Authority over a directory is not exclusive authority over one SQLite file. | B6-T3/T5/T6/T7: symlink, URI, ATTACH and retarget escapes denied in the controlled path. | B6-T4: in-grant hardlink exposes an exterior-named file. Hardlinks, aliases, namespace changes and alternative names undecided. | An owner decision on the grant's identity unit and its treatment of hardlinks/aliases/namespace changes, with tests for each decided case. |
| DA-7 | **No alternative FFM authority.** An application able to call `ffi:sqlite3` arbitrarily outside the domain cannot be described as confined by the `std:sqlite` API. | Provider is `TRUSTED_IN_PROCESS` (§2). | Trusted and confined profiles are not distinguished today. | Explicitly distinct trusted and confined profiles; in the confined profile, no exterior path to `ffi:sqlite3` for the governed databases. |
| DA-8 | **Non-reentrant Actor.** Confinement introduces no simultaneous guest execution in one Actor and no implicit suspension to hide a blocking SQLite call. | Native calls are synchronous; a blocked call holds its Actor ([ffm-provider.md](../interop/ffm-provider.md#security-and-limits)). | No confined mechanism exists to verify. | Tests showing one guest execution per Actor and no implicit suspension under the confined mechanism, and separately showing that physical-thread restriction and Actor non-reentrancy are each established on their own (neither inferred from the other). |
| DA-9 | **Cancellation.** No promise of safe interruption of arbitrary C through `Thread.interrupt`, `ThreadLocalAction` or Future cancellation. States distinguished: request before admission; native call admitted; call blocked; cancellation requested; operation finished; uncertain outcome. | `ThreadLocalAction` requires safepoint servicing and cannot interrupt blocked C (§14). | State model not defined in any contract. | An approved state model covering all six states, with only pre-admission cancellation guaranteed and other states reported, never silently resolved; tests showing that cancelling a client wait does not cancel an admitted SQLite transaction, and that safepoint polling or `ThreadLocalAction` is never relied on for a blocked C call. |
| DA-10 | **Uncertain COMMIT.** If the process ends before a confirmation reaches the client, the operation is reported neither as committed nor as rolled back; recovery must observe durable state before deciding whether to repeat. | B5 SIGKILL recovery observes durable state after kill. | No client-visible uncertain outcome exists. | An uncertain-outcome result and a recovery procedure that inspects durable state before any retry, tested across process termination. |
| DA-11 | **Final close.** Session close is irreversible and idempotent; normal Actor/Process termination is not necessarily completed physical destruction while SQLite keeps live dependencies. | Single release; closed session not reactivated (§2, B4-R1). | close_v2 deferred destruction and dependent resources not tracked through termination. | Tests showing repeated close is a no-op, no reopen of a closed session, and an explicit distinction between logical close and completed physical destruction. |
| DA-12 | **Compatibility.** Preserve LIB029/LIB030; Protos-first `std:sqlite`; Actor-local owned handles; copied results; terminal Futures; current message semantics; no JDBC or ORM; no historical Java infrastructure; no implicit durable access. | Current in-memory API and tests (LIB029/LIB030). | Any durable mechanism not yet evaluated against these. | Existing LIB029/LIB030 coverage unchanged and passing, with the durable addition purely additive and owner-approved. |

### Reconciliation of DA-2, DA-3 and DA-4 with Truffle finalization

Truffle finalization callbacks (`finalizeThread`, `disposeThread`,
`finalizeContext`; §21) are **not** substitutes for restricted native custody.
Whatever mechanism is later approved, admission must guarantee that:

- no exterior thread executes a SQLite operation of the confined session;
- a close requested from another thread does not move the destructor to the
  requester;
- a context thread being finalized cannot abandon pending owned resources in a
  domain that ceases to exist;
- a blocking native operation prevents claiming that the Context terminated
  cleanly while it still holds live resources.

No implementation mechanism is defined here.

### Reconciliation of DA-8 and DA-9

These facts are distinct and none implies another:

- A restricted physical thread does not imply a restricted Actor.
- A non-reentrant Actor does not imply physical thread affinity.
- `ThreadLocalAction` does not replace the scheduler.
- Safepoint polling does not guarantee progress of a blocked C call.
- Terminating a JVM releases its OS resources but does not automatically
  produce a Protos COMMIT confirmation.
- Cancelling a client wait does not imply cancelling an admitted SQLite
  transaction.

The DA-8 and DA-9 future acceptance conditions above reflect this separation.

## 20. Pending implementation contract

Two candidate contracts are documented. **Neither is selected.** All items are
**NOT IMPLEMENTED**; experimental evidence is cited only as context, never as a
PASS of the contract item.

### Contract B — in-JVM confinement

| Acceptance item | Required property | Current status | Required falsification test |
|---|---|---|---|
| B-1 Physically confined native execution | All SQLite C for a confined session runs on threads under the authorized Landlock domain. | NOT IMPLEMENTED (experimental feasibility only: B1, B4) | FFM operations executed from an unrestricted carrier. |
| B-2 Mandatory affinity of all operations | Every operation of DA-2 executes in the domain. | NOT IMPLEMENTED (contradicted by B2-T5) | prepare, step, reset, checkpoint and finalize physically observed outside the grant. |
| B-3 Close from any requester without destructor displacement | A close requested from any thread executes the destructor in the acquisition domain. | NOT IMPLEMENTED (B3 shows cooperative feasibility only; B2-T5 counterexample) | Repetition of B2-T5 with the exterior destructor. |
| B-4 Host-entry admission | Unauthorized host entry cannot reach C with session authority. | NOT IMPLEMENTED (contradicted by B6-T2) | Repetition of B6-T2 with the same Actor. |
| B-5 In-flight C operations | Admitted, blocked and finishing calls handled per DA-9 without unsafe interruption. | NOT IMPLEMENTED | close requested while a C call blocks and does not service safepoints. |
| B-6 Pre-Landlock resources | No durable resource exists before the grant. | NOT IMPLEMENTED (contradicted by B6-T1) | Repetition of B6-T1 with a pre-opened connection. |
| B-7 Multiple grants | Distinct grants/domains coexist without widening each other. | NOT IMPLEMENTED | Multiple grants, linked files and aliases. |
| B-8 WAL, SHM, locking and recovery | Sidecars, locks, checkpoints and recovery stay inside the domain. | NOT IMPLEMENTED (B4/B5 cover a bounded subset only) | WAL/SHM, two independent JVMs, SIGKILL and recovery. |

### Contract D — isolated OS process

| Acceptance item | Required property | Current status | Required falsification test |
|---|---|---|---|
| D-1 Truly isolated OS process | SQLite runs in a separate OS process. | NOT IMPLEMENTED (B5 shows independent JVM processes only) | Force-terminate the helper: SQLite runs under a PID distinct from the Protos client, in an independent address space, and the helper's forced termination does not terminate the client. (A grant-escape attempt alone is not evidence of process separation.) |
| D-2 Restricted filesystem authority | The process running SQLite holds only the granted filesystem authority. | NOT IMPLEMENTED | Attempts to access paths outside the grant, and access through descriptors opened before the grant was applied; a distinct process is not automatically a sufficiently restricted one. |
| D-3 Bounded communication with Protos | A bounded, validated channel between Protos and the helper. | NOT IMPLEMENTED | Malformed IPC input, oversized messages, unauthorized operations and channel closure mid-message are each rejected without executing any unauthorized SQLite operation. |
| D-4 No transferred native handles | No native handle crosses the boundary. | NOT IMPLEMENTED | No pointer, memory address or owned SQLite handle is usable outside the helper; logical resource identifiers, if a future decision authorizes them, cannot be confused with native handles. |
| D-5 Copied Protos messages and results | Requests and results are copied Protos values. | NOT IMPLEMENTED | Mutating a sent guest value after admission does not alter the admitted request, and mutating a copied response does not alter the helper's native state (distinct from the D-3 malformed-message tests). |
| D-6 Operation identity | Every operation has an identity usable for correlation and recovery. | NOT IMPLEMENTED | Correct request/response correlation, and explicit rejection or handling of a repeated, already-confirmed operation (identifier format not defined here). |
| D-7 Process-crash detection | Helper termination is detected and surfaced. | NOT IMPLEMENTED | Terminate the helper while it processes an operation; the client observes the failure, and connection loss is never reported as success or as a confirmed rollback. |
| D-8 Unacknowledged COMMIT is uncertain | Per DA-10. | NOT IMPLEMENTED | Kill the helper after SQLite has committed but before the client receives the acknowledgement; the result must be uncertain and durable state must be reconciled before any retry. A client timeout does not prove the native call was cancelled. |
| D-9 Recovery and restart | The helper can be restarted with recovery that observes durable state. | NOT IMPLEMENTED (B5 SIGKILL recovery is bounded context) | Restart the helper, recover committed data and run integrity checks, including concurrent reopen from another JVM and rejection of improper duplication. |
| D-10 Process cleanup and revocation | Helper resources and authority are released/revoked on close. | NOT IMPLEMENTED | Terminate the client while the helper holds an active transaction; verify the required finalization, resource release, process shutdown and authority revocation, without assuming automatic guest cleanup after SIGKILL. |
| D-11 No shared Landlock domain assumption | No reliance on a main-process Actor sharing the helper's Landlock domain. | NOT IMPLEMENTED | With the main process unrestricted and the helper restricted, repeat an exterior access attempt from the helper and an authorized access from the main process; the main process's authority must not widen the helper's grant. |

A falsification test passes only when the approved implementation rejects or
safely handles the counterexample that the test attempts to introduce. This is
distinct from the historical B6 negative controls, whose PASS demonstrates that
the current unrestricted path remains possible.

Each future falsification test must **fail** if the mechanism admits the
corresponding counterexample. None is implemented. No production IPC
protocol, Java structure or normative operation identifier is introduced.

No hypothetical Java class or method names are introduced by either contract.

## 21. Truffle precedents and limits

Pinned revisions: GraalPy (`oracle/graalpython`) at
`5c8486970dead373d888f25210788cca5d79c374`; Espresso, Truffle API, NFI and
Sulong (`oracle/graal`) at `bd884eb3effea2b31f3fbdd7250267ba8e9eca8e`. The
behaviors below were supplied as owner-verified input for PLAT074-B7-R1; no
additional upstream investigation is claimed.

### GraalPy

`graalpython/com.oracle.graal.python/src/com/oracle/graal/python/runtime/PythonContext.java`:

- `createSystemThread()` uses `env.newTruffleThreadBuilder(task)`, assigns a
  thread group, a name and daemon status, registers a reference to the
  thread, and returns it **unstarted**. The implementation distinguishes
  system threads that need to run guest code.
- `finalizeContext()` (`@TruffleBoundary`) orders hooks, asynchronous
  shutdown, stream flush and native cleanup; the C dependencies are finalized
  in the order the context prescribes. It is an ordering mechanism, **not** a
  general mechanism of physical destructor affinity.

Retained with their limits: `PythonLanguage.initializeThread` (`@Override`,
delegates to `context.attachThread`) is a per-thread setup hook with no
owned-resource or filesystem policy; `PythonLanguage.disposeThread`
(`@Override`, delegates to `context.disposeThread(thread, false)`) is a
teardown hook that does not show resources acquired on a thread are destroyed
on it; `PythonSystemThreadTask.run` (`@Override`, `public final`) handles the
thread-kill exception and requires safepoint polling, demonstrating neither
native teardown affinity nor interruption of blocked C.

### Espresso

`espresso/src/com.oracle.truffle.espresso/src/com/oracle/truffle/espresso/runtime/EspressoReferenceDrainer.java`:

- `startReferenceDrain()` starts the configured Truffle thread.
- `shutdownAndWaitReferenceDrain()` loops while the thread is alive: it
  submits a `ThreadLocalAction`, interrupts the thread and calls `join(10)`.
  The outer loop has **no global deadline**; termination is not temporally
  bounded.
- `ReferenceDrain.run()` attaches the thread to the guest environment and
  runs the drain loop.
- `ReferenceDrain.drain()` (`@TruffleBoundary`) uses safepoint polling.
- `ReferenceDrain.popQueue()` uses
  `TruffleSafepoint.setBlockedThreadInterruptible` for the managed queue wait.

**Espresso demonstrates a Truffle-managed thread and cooperative termination
of a loop that services safepoints. It does not demonstrate a global time
bound, nor a way to arbitrarily cancel a blocked SQLite C call.**
`Thread.interrupt()` does not guarantee native cancellation.

`espresso/src/com.oracle.truffle.espresso/src/com/oracle/truffle/espresso/EspressoLanguage.java`:
`finalizeContext(EspressoContext)` (`@Override`) performs, in order:
`context.ensureThreadsJoined()`; `TruffleSafepoint.getCurrent()`;
`setAllowActions(false)`; `context.prepareDispose()`;
`context.cleanupNativeEnv()`; restoration of the safepoint state;
`context.setFinalized()`. This demonstrates finalization ordering and context
control. It does **not** show that `cleanupNativeEnv()` runs SQLite-like
destructors on the thread where the resources were acquired.

### Truffle thread lifecycle guarantees and non-guarantees

Source:
[`TruffleLanguage.java`](https://github.com/oracle/graal/blob/bd884eb3effea2b31f3fbdd7250267ba8e9eca8e/truffle/src/com.oracle.truffle.api/src/com/oracle/truffle/api/TruffleLanguage.java).

- **A. `finalizeContext(C context)`.** The API requires the language to stop
  and join threads created with `Env.newTruffleThreadBuilder()` during
  `finalizeContext`. Leaving a daemon thread to outlive the context is not
  sufficient. The documentation warns that strategies relying solely on
  `ExecutorService.awaitTermination` do not reliably detect the end of
  polyglot threads.
- **B. `initializeThread(C context, Thread thread)`.** The per-thread
  initialization hook; the context and thread correspond to that thread's
  actual entry.
- **C. `finalizeThread(C context, Thread thread)`.** For polyglot threads
  created through `Env.newTruffleThreadBuilder`, finalization is documented
  to occur on the thread itself, subject to the cancellation/hard-exit
  restrictions on guest execution. For embedder-created threads, finalization
  may run on the thread finalizing the context. Therefore
  `thread == Thread.currentThread()` is **not** a universal property of
  `finalizeThread`.
- **D. `disposeThread(C context, Thread thread)`.** `Thread.currentThread()`
  may differ from the thread being disposed. Disposal of embedder-thread state
  is not guaranteed in every case.

**Mandatory conclusion:** SQLite destructors must not be executed on the sole
basis of the thread identity passed to `disposeThread` or `finalizeThread`.
The required property is the **physical thread that actually performs the
destruction downcall**.

### NFI and Sulong

- **NFI** —
  `truffle/src/com.oracle.truffle.nfi/src/com/oracle/truffle/nfi/NFILanguage.java`:
  `disposeThread(NFIContext context, Thread thread)` (`@Override`) obtains the
  state via `nfiState.get(thread)` and calls `state.dispose()`. This shows
  native per-thread state managed by thread identity, **not** a guarantee that
  the call physically runs on that thread.
- **Sulong** —
  `sulong/projects/com.oracle.truffle.llvm.runtime/src/com/oracle/truffle/llvm/runtime/LLVMLanguage.java`:
  `disposeThread(LLVMContext context, Thread thread)` (`@Override`) delegates
  to `PlatformCapability.disposeThread` and then to
  `disposeThreadNoGuestCode(LLVMContext context, Thread thread)`, which frees
  thread-specific state including stack and TLS and is documented not to run
  arbitrary guest code; the documentation admits it may use a native helper
  through NFI. This is a precedent for separating per-thread state cleanup
  from guest execution, but it does **not** allow assuming that an owned
  SQLite destructor runs under the original thread's Landlock domain.

`ThreadLocalAction` is **not** a native scheduler and **not** a cancellation
solution for blocking C: delivery requires the target to service safepoints,
which a thread blocked in C does not do. None of these mechanisms authorizes a
production Java change, and no production comment cites them.

### Technical traceability

| Obligation | Verifiable Truffle method/annotation | What it demonstrates | What it does not demonstrate | Current gate |
|---|---|---|---|---|
| Thread creation | `Env.newTruffleThreadBuilder`; GraalPy `PythonContext.createSystemThread()` | Context-managed thread, returned unstarted, registered | Landlock restriction or native affinity | B-1 / DA-2 — NOT IMPLEMENTED |
| Context entry | Espresso `ReferenceDrain.run()` (attach to guest environment) | A dedicated thread entering the guest environment | Refusal of unauthorized host entry | B-4 / DA-3 — contradicted by B6-T2 |
| Per-thread initialization | `TruffleLanguage.initializeThread` (`@Override` in GraalPy `PythonLanguage`) | Per-thread hook on the actual entry | Grant installation or owned-resource policy | B-1, B-6 — NOT IMPLEMENTED |
| Native operation execution | NO VERIFIED EQUIVALENT | — | Physical domain of SQLite downcalls | B-1, B-2 — NOT IMPLEMENTED |
| Thread-directed action | `ThreadLocalAction` in Espresso `shutdownAndWaitReferenceDrain()` | Cooperative delivery to a safepoint-servicing thread | Scheduling or cancellation of C | DA-9 — NOT IMPLEMENTED |
| Blocking native operation | `TruffleSafepoint.setBlockedThreadInterruptible` (Espresso `ReferenceDrain.popQueue()`) | Interruptible managed Java wait | Progress or cancellation of a blocked C call | B-5 / DA-9 — NOT IMPLEMENTED |
| Polyglot thread finalization | `TruffleLanguage.finalizeThread` (polyglot threads) | Runs on the thread itself, with cancel/hard-exit restrictions | Owned SQLite destructor affinity | B-3 / DA-4 — B2-T5 counterexample |
| Embedder thread finalization | `TruffleLanguage.finalizeThread` (embedder threads) | May run on the finalizing thread | `thread == Thread.currentThread()` | B-3, B-4 — NOT IMPLEMENTED |
| Per-thread state disposal | `TruffleLanguage.disposeThread`; NFI `NFILanguage.disposeThread`; Sulong `LLVMLanguage.disposeThread` / `disposeThreadNoGuestCode` | State keyed by thread identity; cleanup separated from guest code | Physical execution on that thread | DA-4 — NOT IMPLEMENTED |
| Owned-resource close | NO VERIFIED EQUIVALENT | — | Destructor in acquisition domain | B-3 / DA-4, DA-11 — B2-T5 counterexample |
| Context finalization | `TruffleLanguage.finalizeContext`; Espresso `EspressoLanguage.finalizeContext` (`@Override`); GraalPy `PythonContext.finalizeContext` (`@TruffleBoundary`) | Ordering, thread join obligation, context control | Physical destruction of SQLite dependencies in domain | DA-11 — NOT IMPLEMENTED |
| Process termination | NO VERIFIED EQUIVALENT | — | Protos-visible COMMIT confirmation | DA-10, B-8 / D-8 — NOT IMPLEMENTED |

## 22. Decision requested from the owner

The principal alternatives presented for later approval:

- **B — In-JVM confinement.** Keeps SQLite in the same process, but requires
  imposing a native execution boundary (Contract B, §20) that the current
  runtime does not yet guarantee (§18 counterexamples).
- **D — Isolated OS process.** Introduces a process boundary (Contract D,
  §20), with pending cost and complexity of IPC, packaging, recovery and
  custody.

Retained: **A** as the trusted, unconfined profile (never labelled confined);
**C/E** as alternatives without sufficient evidence; **F** as legitimate
deferral.

### Technical comparison of B and D — no candidate selected

| Dimension | B — in-JVM confinement | D — isolated OS process |
|---|---|---|
| Safety against arbitrary native C | Limited to the Landlock domain of the executing thread; same address space as the JVM | Address-space and process separation; NOT ESTABLISHED (no helper exists) |
| Process fault isolation | None: a SQLite fault affects the JVM | Helper crash separable from the main process; NOT ESTABLISHED |
| Affinity guarantees | Required (B-1–B-4); contradicted by B2-T5, B6-T2 | Not dependent on main-process thread affinity (D-11) |
| Required host complexity | Native execution boundary, admission and custody in the runtime | Helper lifecycle, IPC channel, packaging |
| Protos guest complexity | NOT ESTABLISHED | NOT ESTABLISHED |
| Actor semantics | Must keep DA-8 while confining threads | Copied messages/results (D-5); NOT ESTABLISHED |
| Blocking operations | Blocked C holds the confined thread; no cancellation (DA-9) | Helper may block; timeout without native cancellation (D-8) |
| Uncertain COMMIT | JVM termination loses confirmation (DA-10) | Crash between COMMIT and acknowledgement (D-8) |
| IPC cost | None | NOT ESTABLISHED |
| Resource consumption | NOT ESTABLISHED | NOT ESTABLISHED |
| Portability | Linux Landlock only | NOT ESTABLISHED |
| Reversibility | NOT ESTABLISHED | NOT ESTABLISHED |
| Existing evidence | B1, B3, B4, B6 (focal) SQLite under Landlock | B5 independent JVM processes, SIGKILL recovery |
| Missing evidence | B-1–B-8 falsification tests; counterexamples B2-T5, B6-T1, B6-T2, B6-T4 unresolved | Isolated helper, IPC protocol, D-1–D-11 falsification tests |

- **B:** sufficient evidence of SQLite operating under Landlock, but material
  counterexamples stand against the complete runtime guarantee.
- **D:** can separate SQLite execution from the main process, but no isolated
  helper implementation and IPC protocol have been sufficiently tested.
- **F:** keeping the public durable API blocked remains a valid alternative
  while no mechanism is approved.

### Production implementation authorization gate

A future approval must explicitly identify:

1. The selected alternative.
2. The supported platform profile.
3. The durable authority unit.
4. Treatment of hardlinks/aliases and pre-opened handles.
5. Physical ownership of native execution.
6. Session admission and close.
7. Policy for blocking C calls.
8. Uncertain COMMIT outcome.
9. **Impact on Actor semantics.** The approved design must explain how Actor
   non-reentrancy, synchronous native calls, permitted suspension, guest
   activation, host re-entry, cancellation and Process termination preserve
   the established Protos semantics. Physical-thread confinement must never be
   inferred from Actor identity alone.
10. **Compatibility with `std:sqlite` and `std:ffi`.** The approved
    implementation must preserve the existing Protos-first module APIs,
    explicit native authorization, Actor-local owned handles, borrowed-resource
    rules, copied results, terminal Futures, irreversible session close and the
    behavior of the existing in-memory SQLite library. Durable access must be
    additive and explicitly authorized.
11. **Exact verified Truffle implementation precedents.** Every proposed Java
    production change must identify the real upstream repository, pinned
    revision, source file, method, relevant annotations, observed behavior and
    precise equivalence to the proposed Protos behavior. An incomplete analogy
    is insufficient. If no equivalent is verified, the implementation is not
    authorized. Protos retains `long`, `double` and `boolean` primitive types
    rather than introducing primitive `int`.
12. **Mandatory falsification tests.** The approval must identify tests that
    would fail if any required security or lifecycle invariant were violated,
    including unauthorized native entry, destructor displacement, pre-grant
    resources, hardlink/alias behavior, cross-Actor authority, blocked C calls,
    cancellation, uncertain COMMIT, process failure, recovery and final
    teardown. Passing cooperative-path tests alone is insufficient.

**Production implementation may begin only after explicit approval by the
project owner of all twelve items.** Neither the existence of Truffle
precedents nor the experimental success of B1–B6 constitutes that approval.

**No owner approval is recorded.** PLAT074 remains **OPEN**, D256 **pending**,
LIB031 **BLOCKED**.

### Scope, inspection and validation handoff

B7 is documentation only. B7-R1 initially corrected only §§19–22 of this
packet; B7-R2 completes the production authorization gate, sharpens the D
falsification tests and adds lifecycle clarifications to the FFI guides. No
production code or test was modified. Files: this packet, [sqlite.md](sqlite.md),
[ffi.md](ffi.md) and [ffm-provider.md](../interop/ffm-provider.md). No source,
test, specification, build or packaging file changed. B6 inputs inspected were
the existing §§14–18 of this packet and the owner-reported B6 execution above.
The earlier B6 inspection record (the three B6 files, instruction files,
`spec/semantics/FFI.md` and the Landlock native classes) is unchanged. The
remote blocker ledger and durable work records were not refreshed.

State: **PLAT074 OPEN; D256 pending; LIB031 BLOCKED; B1 13/13 PASS;
B2-T5 FAIL observed; B3 6/6 PASS; B4 10/10 PASS; B5 7/7 PASS; B6 7/7 PASS
(focal).** No compilation, test or validator was executed by the agent.

Still outstanding (not run after B6): the complete
`ProtosI144FfmFixtureAbiTest` class (B2-T5 expected to fail), packaging,
`make test-protos-foreign` and `make test`.

## 23. PLAT074-D0 — Experimental isolated Protos SQLite helper

**Decision recorded.** On 20 November 2026 the project owner selected
alternative D (isolated helper process) as the **experimental direction**.
This is not approval of a durable public implementation and does **not**
authorize production Java. Alternative B stays documented as investigated and
is not implemented. All B1–B7 evidence above is kept unchanged; the B2
failures and the B6 negative controls are not reinterpreted.

**D0 scope.** Experimental Protos modules plus JUnit additions only:

- the database is a real file managed by SQLite through the existing FFM
  provider (`ffi:sqlite3`);
- requests and replies travel as JSON Lines over the helper's stdin/stdout;
- the helper JVM is launched by the Java test harness
  (`ProtosI144FfmFixtureAbiTest`) running the real Protos CLI on
  `protos/tests/foreign-provider/isolated/helper.protos`;
- Protos has no public subprocess API yet;
- the helper's filesystem confinement is **not** demonstrated: D0 is not a
  sandbox.

Design and protocol: [sqlite-isolated-process-spike.md](sqlite-isolated-process-spike.md).

### D0 evidence

| Test | Claim exercised | Status |
|---|---|---|
| D0-T1 `d0T1ProtocolRoundTripsExactProtosValues` | Protos codec: exact ids/values, 64-bit extremes, malformed JSON, wrong types, unknown ops, 4096-octet bound, mismatched ids and invalid statuses | PASS (focal) |
| D0-T2 `d0T2ProtosHelperRunsInIndependentOsProcess` | helper is a distinct child OS process; Protos READY, PING/OK, CLOSE, STOP, clean exit | PASS (focal) |
| D0-T3 `d0T3ProtosSqliteCommitsAndReopensAcrossHelpers` | explicit-transaction COMMIT survives into an independent JVM; COUNT/SUM/INTEGRITY | PASS (focal) |
| D0-T4 `d0T4MalformedAndUnauthorizedRequestsNeverReachSqlite` | malformed, mistyped, non-increasing, OPEN/EXEC/ATTACH and oversized requests rejected; oversized framing fail-closed; no writes | PASS (focal) |
| D0-T5 `d0T5ExplicitRollbackKeepsCommittedState` | explicit ROLLBACK leaves only committed state, also after reopen | PASS (focal) |
| D0-T6 `d0T6KilledHelperRollsBackUncommittedTransaction` | SIGKILL with an admitted uncommitted INSERT; recovery keeps committed state only | PASS (focal) |
| D0-T7 `d0T7CommittedButUnacknowledgedOperationIsUncertain` | kill between real COMMIT and acknowledgement; client records UNCERTAIN; commit present; no retry | PASS (focal) |
| D0-T8 `d0T8ClosedHelperRejectsFurtherDatabaseOperations` | after CLOSE every database operation is CLOSED; no reopen; clean termination | PASS (focal) |

### Status separation

- **Observed prototype behaviour:** owner-reported focal run of
  `ProtosI144FfmFixtureAbiTest#d0*` on 20 November 2026: 8/8 PASS (39.90 s).
  The D0-T4 helper stderr shows the intended fail-closed path
  (`FRAMING_REJECTED`, `ADMISSION_FAILED_CLOSED`, non-zero exit). This is
  prototype evidence only.
- **Architectural acceptance:** not granted. The presence of the prototype
  does not implement any of D-1–D-11; the §20 table is unchanged. D0-T2 and
  D0-T6 exercise parts of the D-1 falsification test; that alone does not
  mark D-1 implemented. D0 does not address D-2, D-10 or D-11.
- **Production authorization:** not granted (§22 gate unchanged).
- **Platform support:** none claimed beyond the existing Linux x86-64 FFM
  fixture preconditions.

### Next slices

- **D1** must resolve the helper's effective OS authority (filesystem grant,
  inherited descriptors, environment) and falsify D-2/D-11.
- **D2** (production) is possible only after the D tests pass and the owner
  authorizes production work.

State: **PLAT074 OPEN; D256 pending; LIB031 BLOCKED; D0 8/8 PASS (focal).**
Not run after D0: the complete `ProtosI144FfmFixtureAbiTest` class (B2-T5
expected to fail), `make test-protos-foreign` and `make test`.

## 24. PLAT074-D1 — Pre-exec filesystem confinement of isolated helper

**Starting point.** The owner selected alternative D as the experimental
direction (§23). D0 passed 8/8 (focal, owner-reported, 39.90 s), including the
fail-closed D0-T4 framing path. PLAT074 stays **OPEN**, D256 **pending** and
LIB031 **BLOCKED**. Production Java is still not authorized.

**Mechanism.** A test-only C launcher,
`protos/tests/foreign-provider/isolated/native/sandbox-exec.c`, runs before
the helper JVM exists. In a single-threaded process it validates the policy
paths, requires Landlock ABI ≥ 3, and then calls `landlock_create_ruleset` (handling every
filesystem right the kernel knows, including `REFER` and `TRUNCATE`),
`landlock_add_rule` (path beneath), `prctl(PR_SET_NO_NEW_PRIVS)` and
`landlock_restrict_self`. It closes the ruleset and every inherited descriptor
above 2, writes `LANDLOCK_APPLIED` to stderr, and `execve`s the unchanged D0
JVM command. Primary source: the kernel userspace documentation
<https://www.kernel.org/doc/html/latest/userspace-api/landlock.html>; the
kernel's `samples/landlock/sandboxer.c` is a procedural precedent only, and no
code was copied from it.

**Thread inheritance.** Landlock domains are preserved across `execve` and
inherited by every thread created afterwards. Because the domain exists
before the JVM starts, every JVM, GraalVM and Actor carrier thread runs in the
helper's domain, and so does every FFM downcall into SQLite. This is the
property that the in-process B2-T5/B6-T2 counterexamples showed cannot be
obtained by restricting individual threads of a shared JVM. The Protos client
stays unrestricted in its own process.

**Grants** (harness-chosen, never from IPC; details in
[sqlite-isolated-process-spike.md](sqlite-isolated-process-spike.md) §15):

- read/execute: `java.home` and the ELF interpreter;
- read-only: system library directories, the test class path entries, the FFM
  provider JAR, `$PROTOS_HOME/protos/lib`, the `isolated` fixture directory,
  selected `/etc` files, `/proc`, `/sys`, `/dev/urandom` and `/dev/random`;
- read/write: `/dev/null`;
- one private SQLite directory, which may create, write, truncate and unlink regular files;
- one private temporary directory per process for JVM and Truffle state.

The launcher refuses non-canonical paths, `/` as a write root, and write
roots that contain a runtime path. An unsupported platform exits 69 and its
tests are UNSUPPORTED. There is no unconfined fallback.

### D1 evidence

| Test | Claim exercised | Status |
|---|---|---|
| D1-T1 `d1T1LandlockIsInstalledBeforeProtosBoot` | restriction precedes JVM boot; exec in place; READY; PING; clean exit | PASS (focal) |
| D1-T2 `d1T2ConfinedSqliteCreatesAndReopensDurableDatabase` | SQLite works within the grant; reopen by another confined JVM | PASS (focal) |
| D1-T3 `d1T3NativeSqliteCannotOpenExteriorDatabase` | real SQLite open of an exterior DB denied; unrestricted control opens it; exterior unchanged | PASS (focal) |
| D1-T4 `d1T4SeparateHelpersCannotWidenEachOthersGrants` | two confined helpers' grants stay separate | PASS (focal) |
| D1-T5 `d1T5UnrestrictedClientDoesNotWidenHelperAuthority` | unrestricted client authority does not reach the helper | PASS (focal) |
| D1-T6 `d1T6UnauthorizedSqlOperationsRemainRejectedUnderConfinement` | D0 admission and 4096-octet bound unchanged; no exterior effects | PASS (focal) |
| D1-T7 `d1T7UncommittedTransactionRollsBackAfterSandboxedHelperKill` | SIGKILL recovery under confinement | PASS (focal) |
| D1-T8 `d1T8CommittedButUnacknowledgedTransactionRemainsUncertain` | UNCERTAIN commit under confinement; no retry | PASS (focal) |
| D1-T9 `d1T9UnsupportedLandlockProfileRefusesExecution` | impossible ABI refused, no READY, no SQLite, no retry | PASS (focal) |
| D1-T10 `d1T10HardlinkDoesNotProvideExclusiveDatabaseIdentity` | path-based grant versus inode identity (B6-T4 interpretation) | PASS (focal) |
| D1-T11 `d1T11NoResourceOrProcessSurvivesNormalClose` | normal-close cleanup; no surviving process | PASS (focal) |
| D1-T12 `d1T12ConfinedHelperPreservesD0ProtocolAndActorSemantics` | D0 protocol and Actor transitions unchanged | PASS (focal) |

Owner-reported focal run on 20 November 2026 (`ProtosI144FfmFixtureAbiTest#d1*`): 12/12 PASS, 0 failures, 0 errors, 0 skipped, 63.54 s, Landlock ABI 11. Stderr confirms `LANDLOCK_APPLIED` for every confined JVM, the D1-T4 cross-grant helper failing in `SqliteEngine.openFile`, the D1-T6 fail-closed framing path and the D1-T9 refusal (`SANDBOX_REFUSED LANDLOCK_ABI_INSUFFICIENT abi=11 required=1000000`). The D1-T10 hardlink outcome (alias allowed or denied) was published through `TestReporter` and was not captured in the returned output. This is prototype evidence only.

### Contract reconciliation (§20 table unchanged)

- **D-1:** unchanged. D1-T1/T7 exercise the falsification test again, under
  confinement.
- **D-2:** D1-T3/T4/T6 provide the exterior-path part of its falsification
  test (focal PASS). The pre-opened-descriptor part is addressed only by
  the launcher closing inherited descriptors. It is not falsified by a test
  here, and B6-T1 still stands as the counterexample for descriptors opened
  before restriction. Not implemented.
- **D-11:** D1-T5 is the falsification test described in §20. It passed in the
  focal run: experimental evidence only, not a production implementation.
- **D-10:** still pending. D1-T11 covers normal close only. Client-crash
  cleanup and revocation are untested.
- **D-3–D-9:** unchanged from §23.

The B2 failures and the B6 counterexamples (B6-T1 pre-opened descriptor,
B6-T2 unrestricted host entry, B6-T4 hardlink) are kept as they are. D1 avoids
B6-T2 by process separation and does not refute it for a shared JVM.

### Pending risks

- The grant is per directory and path-based: hardlinks or bind mounts inside
  the grant reach exterior inodes (D1-T10).
- Not a complete sandbox: network, IPC, signals, resource limits and memory
  safety of native code are not confined. `/proc` and `/sys` are readable.
- Descriptor hygiene is enforced by the launcher (`close_range`), not by
  Landlock.
- The read list depends on the test host's filesystem layout. Its sufficiency
  is unknown until the owner runs the tests.
- No Protos-level OS-process API exists. Launching remains in the test harness.

State: **PLAT074 OPEN; D256 pending; LIB031 BLOCKED; D0 8/8 PASS (focal);
D1-T1–T12 12/12 PASS (focal).**
Not run after D1: the complete `ProtosI144FfmFixtureAbiTest` class, `make test-protos-foreign`
and `make test`.

## 25. PLAT074-D1-R1 — Network, IPC and inherited-authority falsification

### Starting evidence (owner-reported, 20 November 2026)

- D0: 8/8 PASS (focal).
- D1: 12/12 PASS, 0 failures/errors/skipped, 63.54 s, Landlock ABI 11. D1 was
  installed before the JVM (`LANDLOCK_APPLIED` before the Protos READY).
- Review found that the D1 launcher omitted `LANDLOCK_ACCESS_FS_RESOLVE_UNIX`
  (ABI 9) even though it claimed to handle every known filesystem right. It
  also left `handled_access_net` and `scoped` unset. D1 results are evidence for
  the filesystem profile only. They do not approve D-1–D-11.

### Experimental widening

D1-R1 raises the launcher to an ABI 10 profile:

- handled filesystem rights: everything through RESOLVE_UNIX;
- handled TCP and UDP rights, with no port rule;
- the abstract-UNIX and signal scopes;
- mandatory `close_range`, with no per-descriptor fallback;
- an allowlisted explicit environment;
- `/` refused as a read root;
- refusal of any requirement above the reviewed contract.

Full detail is in `sqlite-isolated-process-spike.md` §16. There is no
production Java, runtime, provider, `std:sqlite` or specification change.

### Distinctions that must stay separate

1. **FS access:** path-based Landlock rights on new opens. They do not identify
   databases (hardlinks, bind mounts).
2. **Network access:** TCP/UDP rights. No port is granted, so every covered
   operation is denied.
3. **UNIX IPC:** pathname sockets via `FS_RESOLVE_UNIX` and abstract sockets via
   the scope. The standard channels 0–2 remain explicit IPC authority.
4. **Signals:** the scope limits signals sent from the domain. It does not limit
   signals sent to the domain (D1-R1-T13).
5. **Inherited descriptors:** Landlock does not check them. Only the launcher's
   `close_range` removes them (D1-R1-T8).
6. **Client and helper are distinct OS processes.** Confining the helper does not
   confine the client, and the client's death does not end the helper
   (D1-R1-T12).
7. **Custody and revocation:** no implemented mechanism ties helper lifetime or
   resource custody to the client. This is D-10, and it is unresolved.

### Tests

| Test | Result |
|---|---|
| D1-R1-T1 `d1R1T1FullSupportedLandlockProfileIsApplied` | PASS (focal, owner-observed 2026-11-20) |
| D1-R1-T2 `d1R1T2TcpConnectOutsideDomainIsDenied` | PASS (focal, owner-observed 2026-11-20) |
| D1-R1-T3 `d1R1T3UdpSendOutsideDomainIsDenied` | PASS (focal, owner-observed 2026-11-20) |
| D1-R1-T4 `d1R1T4PathnameUnixSocketOutsideDomainIsDenied` | PASS (focal, owner-observed 2026-11-20) |
| D1-R1-T5 `d1R1T5AbstractUnixSocketOutsideDomainIsDenied` | PASS (focal, owner-observed 2026-11-20) |
| D1-R1-T6 `d1R1T6SignalsCannotEscapeTheHelperDomain` | PASS (focal, owner-observed 2026-11-20) |
| D1-R1-T7 `d1R1T7AuthorizedProtosSqliteSurvivesExpandedRestrictions` | PASS (focal, owner-observed 2026-11-20) |
| D1-R1-T8 `d1R1T8InheritedExteriorFileDescriptorIsClosedBeforeExec` | PASS (focal, owner-observed 2026-11-20) |
| D1-R1-T9 `d1R1T9UnsafeEnvironmentVariablesAreRejected` | PASS (focal, owner-observed 2026-11-20) |
| D1-R1-T10 `d1R1T10HardlinkOutcomeIsAssertedAndRecorded` | PASS (focal, owner-observed 2026-11-20) |
| D1-R1-T11 `d1R1T11UnsupportedSecurityProfileRefusesWithoutFallback` | PASS (focal, owner-observed 2026-11-20) |
| D1-R1-T12 `d1R1T12ClientDeathDoesNotYetProvideHelperRevocation` | PASS (focal, owner-observed 2026-11-20) |
| D1-R1-T13 `d1R1T13ParentCanTerminateConfinedHelper` | PASS (focal, owner-observed 2026-11-20) |

D1-R1-T12 is a negative architecture control. If it passes, that confirms D-10
is **not** implemented. It is not a cleanup guarantee.

Owner-observed focal evidence (20 November 2026): selector `#d1*` 25/25 PASS
(12 D1 and 13 D1-R1 tests), 0 failures, errors or skips, 86.83 s;
`mvn -o test-compile` BUILD SUCCESS; `git diff --check` clean. The ABI 10
profile was installed on a kernel reporting ABI 11 (`handled_fs=0x1ffff`,
`handled_net=0xf`, `scoped=0x3`). D1-R1-T10 confirmed that the interior
hardlink reaches the exterior inode, a real identity limitation. D1-R1-T12
confirmed that the helper survives its client's death while the harness holds
its pipes, so D-10 stays unresolved. The complete JUnit class was not run as
part of this evidence.

### Pending risks

- The UAPI constants for ABI 9/10 (RESOLVE_UNIX bit 16, UDP bits 2/3, quiet
  fields) are written into the fixture from the referenced kernel header
  revision. The installed headers in the development container stop at ABI 6,
  so the owner's run is the first check against a real ABI 10+ kernel.
- The exact denial errno per mechanism (`EACCES` vs `EPERM`) is accepted as
  either and reported. It has not yet been observed.
- The JVM/GraalVM boot under denied TCP/UDP, RESOLVE_UNIX and the signal scope
  has not yet been observed (for example, a glibc `nscd` socket lookup has to
  fall back to files).
- COMMIT without an acknowledgement stays uncertain. Experimental
  reconciliation and deduplication are added by D2 (§26), not yet run.
- `/proc` and `/sys` are broad read surfaces. Memory safety of native code is
  not covered. There is no identity-provisioning policy.

The B1–B7 record is unchanged.

State: **PLAT074 OPEN; D256 pending; LIB031 BLOCKED; D0 8/8 PASS; D1 12/12
PASS; D1-R1 13/13 PASS (focal); D-10 pending.**

## 26. PLAT074-D2 — Atomic receipts and uncertain-commit reconciliation

D2 is an experimental fixture. Full detail is in
[sqlite-isolated-process-spike.md](sqlite-isolated-process-spike.md) §17. No
production Java, runtime, provider, `std:sqlite`, `std:ffi` or specification
changed.

Observed focal result, reported by the human executor: **D2 16/16 PASS**, zero
failures, errors and skips, 104.3 seconds. COMMIT, a lost acknowledgement after
COMMIT, reconciliation and `REPLAYED` were all observed for real. This is
prototype evidence. Two points were still pending after that run, and §27
addresses them: a durable identity of the database, and demonstrable
termination of the original helper before an `ABSENT` may count as final.

### What a durable operation is

A durable operation is a client request identified by a key that outlives
every process: `(client_id, operation_id)`, stored in SQLite. The transport
`id` only pairs a request with its reply inside one helper process. It restarts
with each helper, so it cannot say whether an earlier request took effect.
Correlation and deduplication are separate problems.

### What the persistent key guarantees

The receipt table's primary key admits at most one receipt per key, and the
entry is written in the same transaction as its receipt. Within one database,
under the verified SQLite conditions, a key therefore has **at most one
effect**. This is not exactly-once delivery. A request can still be lost, and
the client has to ask.

- **Replay with the same value** (`REPLAYED`) is the safe retry: the earlier
  effect is recognized and nothing is written.
- **Replay with a different value** (`CONFLICT`) is rejected without writing.
  Accepting it would either duplicate an effect or silently rewrite a
  confirmed one.

### Why the receipt is in the same transaction

If the entry were committed first and the receipt later, a crash between the
two commits would leave an effect without a receipt. A retry would then apply
it twice. In the opposite order, the crash would leave a receipt without an
effect. One `COMMIT` makes both visible together or neither (D2-T7, D2-T12).

### Why a successful COMMIT without an acknowledgement is still UNCERTAIN

The client knows only what reached it. If the helper dies after `COMMIT`
returned but before the reply was written, the effect exists but the client has
no evidence of it. EOF is therefore `UNCERTAIN`, never `ERROR` and never
rollback (D2-T8, D2-T15). When the helper itself cannot establish a COMMIT
result, it answers `RECONCILE_REQUIRED`, writes nothing more on that session
and claims no rollback.

### Why LOOKUP after restart is needed

The only authority on whether the effect happened is the database. After a
restart, the client asks for the same key under a new transport id. `FOUND`
with the same value means committed. `FOUND` with another value is a conflict.
`ABSENT` means not applied **only** if the queried database is the right one
and the helper that ran the operation can no longer COMMIT it. D2 assumed both.
§27 makes both explicit. Retrying the same key and value
without asking is also safe, because the answer is `REPLAYED` (D2-T8, D2-T16).

### Why an unauthenticated client key is not production authority

`clientId` is a number the peer chooses. Nothing binds it to an authenticated
principal, so one client can read, or block with `CONFLICT`, another client's
keys. A production design needs an authenticated, host-assigned client
identity. D2 does not decide one.

### Durability scope

Each session sets `PRAGMA synchronous=FULL` and checks it. The journal stays
enabled. The evidence comes from JVM process kills and restarts. Power loss and
storage that violates its own guarantees are not tested.

### Reconciliation with D-6–D-10

| Item | D2 experimental coverage (pending) | Production guarantee |
|---|---|---|
| D-6 Operation identity | Durable `(clientId, operationId)` separate from transport `id`; `REPLAYED`/`CONFLICT` (D2-T3–T6, T13, T16) | not approved: identifier format, authentication and scope undecided |
| D-7 Process-crash detection | Lost reply is client-local `UNCERTAIN`, never `ERROR`/rollback (D2-T8, T15) | not approved |
| D-8 Unacknowledged COMMIT | Reconcile by `LOOKUP` before any retry; no automatic COMMIT retry (D2-T8, T15, T16) | not approved |
| D-9 Recovery and restart | Reopen in other confined JVMs; concurrent helper meets `BUSY`, no duplicate (D2-T2, T7, T10, T12) | not approved |
| D-10 Custody and revocation | none in D2: the harness still owns helper lifetime (D3 tries OS parent custody, §27) | **unresolved** |

### Tests

| Test | Result |
|---|---|
| D2-T1 `d2T1DurableProtocolRejectsInvalidIdentityAndPayload` | PASS |
| D2-T2 `d2T2ApplyOncePersistsEntryAndReceiptAtomically` | PASS |
| D2-T3 `d2T3SameKeyReplayAfterRestartDoesNotDuplicate` | PASS |
| D2-T4 `d2T4ConflictingReplayIsRejectedWithoutWrite` | PASS |
| D2-T5 `d2T5DifferentClientsMayUseSameOperationNumber` | PASS |
| D2-T6 `d2T6SameClientDistinctOperationIdsRemainIndependent` | PASS |
| D2-T7 `d2T7KilledBeforeCommitLeavesNoEntryOrReceipt` | PASS |
| D2-T8 `d2T8KilledAfterCommitBeforeAckReconcilesWithoutDuplicate` | PASS |
| D2-T9 `d2T9MissingReceiptCannotBeMistakenForZeroValue` | PASS |
| D2-T10 `d2T10TwoConfinedHelpersCannotApplyTheSameKeyTwice` | PASS |
| D2-T11 `d2T11UnauthorizedProtocolCannotReachSqlite` | PASS |
| D2-T12 `d2T12FailedOrInterruptedWriteNeverCreatesOrphanReceipt` | PASS |
| D2-T13 `d2T13TransportIdsAreNotDurableOperationIds` | PASS |
| D2-T14 `d2T14DurableCloseRevokesConnectionCustody` | PASS |
| D2-T15 `d2T15UncertainReplyNeverMeansConfirmedRollback` | PASS |
| D2-T16 `d2T16ConflictAfterUncertainCommitCannotOverwriteReceipt` | PASS |

The B1–B7 record is unchanged.

Authentication of `clientId`, storage guarantees beyond process kills, and
production supervision remain out of scope, as stated above.

State: **PLAT074 OPEN; D256 pending; LIB031 BLOCKED; D-10 unresolved; D2-T1–T16
PASS (focal, prototype).**

## 27. PLAT074-D3 — Parent-owned helper lifetime and finality of reconciliation

D3 is an experimental fixture. Full detail is in
[sqlite-isolated-process-spike.md](sqlite-isolated-process-spike.md) §18. No
production Java, runtime, provider, `std:sqlite`, `std:ffi`, specification or
public subprocess API changed. The only new C is the test fixture
`isolated/native/owner-exec.c` and the additive `--expected-parent-pid` option
of `sandbox-exec.c`.

This slice tries **one concrete form of OS custody**: the Protos client JVM is
the OS parent of its helper, and the helper carries
`PR_SET_PDEATHSIG(SIGKILL)` from before its JVM existed. It does **not**
authorize integrating that form with the Actor lifecycle in production.

### Eight identities that must not be confused

| Concept | What it is in D3 | What it is not |
|---|---|---|
| Owning process | The client JVM. Its PID is the helper's parent, and its death kills the helper through the kernel. | Not an Actor, not a Protos value, not a capability. |
| Owning Actor | The client's RootActor runs the session logic. | Ending an Actor while its JVM lives revokes nothing in the helper. |
| SQLite connection | One connection, held by the helper's RootActor, never exported. | Not an identity of the database or of an operation. |
| Durable database identity | The `database_identity` pair, provisioned once from the trusted bootstrap and verified before READY. | Not a path, a PID, a connection, an Actor or a transport id. Not authentication. |
| Durable operation identity | `(client_id, operation_id)` in `operation_receipts`. | Not the transport id. Not authenticated. |
| Transport id | Correlates one request with one reply inside one helper process. | Not durable. It restarts with every helper. |
| Evidence of helper death | The host observed that the source process stopped running: `/proc` state and start time, or a reaped exit status. Recorded with the harness command `SOURCE_TERMINATED`. | Not EOF, not a missing reply, not a protocol field, not a helper message. |
| Final reconciliation result | `COMMITTED` or `CONFLICT` (`FOUND`). `NOT_APPLIED` only for `ABSENT` with termination evidence and a verified database identity. | `ABSENT_OBSERVED` is not final: the source might still COMMIT. |

### Why ABSENT alone is not a rollback

A helper held before COMMIT has inserted the entry and the receipt inside an
open transaction that other connections cannot see. Another helper's `LOOKUP`
answers `ABSENT`, yet the source can still COMMIT a moment later. A client that
concluded `NOT_APPLIED` and retried elsewhere could then cause a second effect.
`applyOnce` would prevent that within one database, but the client would still
have reported something false. D3 therefore keeps such a key at
`ABSENT_OBSERVED` (D3-T10). Only after the host observes that the source
process has stopped running can a later `ABSENT` become `NOT_APPLIED` (D3-T11).
A stopped process cannot COMMIT.

### Why the database identity matters

`ABSENT` is a statement about one database. A reconnection to another file,
whether by mistake, after a restore or because the path was swapped, answers
`ABSENT` for every key. Before READY the helper verifies the persisted identity
against its bootstrap and refuses on a mismatch. The client compares the
published identity with its own bootstrap. The reconciliation driver accepts a
reconciliation for a bound key only from a helper whose identity it verified
(D3-T8, T12). This gives consistency. It is not authentication: a malicious
host can copy the file together with its identity.

### What parent custody does not give

It does not revoke a live Actor, cancel a C call cooperatively, interrupt an
uninterruptible kernel wait, supervise or restart helpers, authenticate
clients or databases, persist pending client requests, or survive power loss.
It depends on Linux semantics: the signal follows the forking thread, and a
helper whose parent is still alive stays alive. D-10 is not fully resolved.

### Tests

| Test | Result |
|---|---|
| D3-T1 `d3T1ParentOwnershipIsEstablishedBeforeJvmStartup` | NOT RUN |
| D3-T2 `d3T2DirectProtosToProtosCommunication` | NOT RUN |
| D3-T3 `d3T3ParentDeathSignalSurvivesExecve` | NOT RUN |
| D3-T4 `d3T4ClientSigkillTerminatesIdleHelper` | NOT RUN |
| D3-T5 `d3T5ClientSigkillBeforeCommitLeavesNothing` | NOT RUN |
| D3-T6 `d3T6ClientSigkillAfterCommitBeforeAckReplays` | NOT RUN |
| D3-T7 `d3T7NormalClientExitDoesNotLeaveHelperRunning` | NOT RUN |
| D3-T8 `d3T8WrongDatabaseIdentityRefusesReconciliation` | NOT RUN |
| D3-T9 `d3T9SameDatabaseIdentitySurvivesRestart` | NOT RUN |
| D3-T10 `d3T10LiveSourcePlusAbsentDoesNotEstablishRollback` | NOT RUN |
| D3-T11 `d3T11TerminatedSourcePlusAbsentPermitsSafeRetry` | NOT RUN |
| D3-T12 `d3T12WrongIdentityCannotProduceFalseNotApplied` | NOT RUN |
| D3-T13 `d3T13ParentMismatchRefusesStartup` | NOT RUN |
| D3-T14 `d3T14LossOfClientDoesNotWidenLandlockAuthority` | NOT RUN |
| D3-T15 `d3T15NoNativeHandleCrossesDirectProtosIpc` | NOT RUN |
| D3-T16 `d3T16D2ReconciliationRequiresExplicitSourceTermination` | NOT RUN |

The B1–B7 and D0–D2 records are unchanged apart from the D2 results and the
`ABSENT` correction above.

State: **PLAT074 OPEN; D256 pending; LIB031 BLOCKED; D-10 not fully resolved;
D3-T1–T16 NOT RUN.**


## 28. PLAT074-FINAL — Production integration candidate (first campaign failed; not accepted)

Architecture D and the bounded manifest integration are owner-authorized in the
continuation request. This section records the candidate code, not validation,
publication, specification ratification or work-item closure. No normative
specification, project version or changelog was changed.

### Evidence boundary

| Evidence | State |
| --- | --- |
| D0 | Owner-reported 8/8 PASS |
| D1 + D1-R1 | Owner-reported 25/25 PASS |
| D2 | Owner-reported 16/16 PASS |
| D3 | Owner-reported 16/16 PASS |
| Joint D1/D1-R1/D2/D3 | Owner-reported 57/57 PASS, zero failures/errors/skips |
| Maven package (compilation) | Owner-reported BUILD SUCCESS |
| Lint of the two indicated Protos modules | Owner-reported PASS |
| Portable build | Owner-reported PASS |
| Distribution checks (CRC, layout, runtime, source identity) | Owner-reported PASS |
| `ProtosIsolatedSqliteIntegrationTest`, first campaign | Owner-reported 61 run: 24 PASS, 35 FAIL, 2 ERROR |
| Corrections below, re-run of the focal class | NOT RUN |
| General regression | NOT RUN |

The integration is **not accepted**.

### First integration campaign and corrections (NOT RUN)

The 37 failures fall into four shared-cause families. The corrections below have
not been executed; they are not validated.

| Family | Observed | Cause | Corrected in |
| --- | --- | --- | --- |
| 1. Public path and close | `Error: Object {}` in the Statement/Connection close handlers | Demonstrated: the handlers sent `recognizes` to Error prototypes (`Cancelled`, `SQLiteUncertainError`, `IOError`). Error prototypes define no `recognizes`, so the send signaled SlotNotFound from inside the handler and replaced the primary error. The same pattern was in `call` and in the transaction/applyOnce handlers. Category tests now walk the delegation chain, as ERRORS handler matching does. An unconfirmed FINALIZE after retirement reports `HELPER_TERMINATION_UNCONFIRMED` instead of a generic Error. | `protos/lib/sqlite/internal/IsolatedConnection.protos` |
| 2. IPC and concurrency models | `Error: Object {}` after the frame; missing `BUSY_ASSERTIONS_COMPLETE` | Demonstrated: the generated programs used the same `recognizes` sends (`uncertain`, `SQLiteError`, `Cancelled`) inside handlers, in Tasks reached through `Actor.request`. The concurrency harness now reports the scenario, exit code and guest stderr instead of only EOF/null. | `ProtosIsolatedSqliteIntegrationTest.java` |
| 3. Launch cancellation | Child driver received `FAILED` | Demonstrated: the guest asserted `SQLiteError.recognizes(cancelled)`. The cleanup error "host cannot close while Process Contexts are active" was secondary. The child driver now terminates its Process in `finally`, so the primary failure is the one reported. | `ProtosIsolatedSqliteIntegrationTest.java` |
| 4. Exterior native controls | `NoSuchFileException: outside.db` | Analysis of SQLite behavior, not yet re-executed: `openMemory()` opens with `SQLITE_OPEN_MEMORY`, and SQLite opens every database attached to that connection in memory, so ATTACH created no file. The control now opens `":memory:"` by name with READWRITE plus CREATE through the production ABI and the shared Resources engine. It then attaches, creates, inserts and reads back through SQLite, prints a marker, and Java checks that the file exists and starts with the SQLite header. Landlock is unchanged. | `ProtosIsolatedSqliteIntegrationTest.java` |

`memoryRetainsPublicExampleBehavior` now runs the installed
`sqlite-library.protos` unchanged with the explicit provider. Before, the test
prepended the harness prelude, and the prelude repeated the example's own
top-level declarations. `IsolatedHelper.protos` and `Resources.protos` were
reviewed and left unchanged: the helper removes a statement before
`sqlite3_finalize` and returns the real code, and `openConnection` is a faithful
parameterization of the earlier `openMemory`.

The historical experiments were preserved. Their PASS results do not extend to
production code merely because that code uses the same architecture.

### Candidate implementation

`std:sqlite` captures and removes both private facilities. Import loads neither
FFM nor a process. `openMemory()` keeps its original ABI and flags.
`openDurable(sqliteAuthority, databaseName)` uses `IsolatedConnection`, checks
host authority, starts a confined helper and validates READY against the expected
session **and expected identity** before publishing a Connection. It reuses
`Resources.openConnection`; no second resource engine was added.

The helper uses the existing ABI and real SQLite, READWRITE plus NOFOLLOW (no
CREATE during reopen), synchronous FULL, bounded busy timeout, native autocommit,
private native statements and sequential dispatch. Client and helper use private
protocol v3: structured JSON, strict UTF-8, canonical Base64url, bounded ordered
fragmentation, 4096-octet frames and a 48 MiB logical-message ceiling. This does
not lower the 16 MiB per-value public bound. Binary64 transport uses at most 971
initial halvings, 1074 doublings and 52 significand reductions; decode bounds
canonical exponents to [-1023, 1074] and validates exact round-trip form.

Errors of local admission precede output. Confirmed SQLite codes, host refusals,
transport loss, invalid protocol and helper rejection remain distinguishable.
Unknown post-send results are uncertain, including COMMIT. Closing preserves
finalize/release errors and checks whether helper termination was confirmed.
The durable close Future resolves to the durable public receiver.

### Exact manifest proposed for D256 ratification

For `--allow-sqlite=/srv/private/dbs`, the host-controlled manifest directory is
`/srv/private/dbs.sqlite-identities`; it is not granted to the helper. A database
`application.db` requires `application.db.identity` there:

```text
identity_hi=101
identity_lo=202
provision=CREATE_NEW
```

Exactly three ASCII lines, final LF, maximum 256 octets, two canonical signed i64
components. The manifest must be a regular file, not group/other-writable. No implicit identity generation. Directory grants must exist, be
absolute/canonical, writable by the host, not group/other-writable, and disjoint
from the runtime. The host opens the manifest without following its final
symlink and uses an exclusive **nonblocking** FileChannel lock. Concurrent or
malformed manifests refuse admission. Namespace control stays trusted.

For CREATE_NEW the host consumes authorization to VERIFY and forces it, including
when an existing DB causes refusal. Only an absent file is created exclusively. The helper receives the
pair and PROVISION, requires an empty schema and provisions once inside BEGIN
IMMEDIATE. VERIFY requires the existing regular file and exactly one matching
SQLite identity row before READY. Disappearance never triggers recreation.
Failure after consumption requires explicit host intervention; no repair or
identity adoption is implicit. This is a trusted authorization record, not a
second application journal. Hostile guest SQL cannot be assumed to preserve the
SQLite identity/receipt tables; the expected identity remains outside its grant.

### Owner-approved provisional Connection admission

The continuation owner explicitly approved exclusive admission per Connection,
including its Statements: reject concurrent operations with CONNECTION_BUSY
before IPC/correlation changes; release admission only after a known result;
retain RECONCILE_REQUIRED after uncertainty; reject new work while close is in
progress; and never treat cancellation of a waiter as native completion.
No specification was changed by this provisional decision.

The Protos client owns the reservation, close-in-progress flag, stable close
failures and reconciliation state. Public Statement facades check admission
before invoking the existing Resources state machine. Each IPC call atomically
reserves before its first suspension, validates/copied-decodes its reply before
release, and retains the reservation on an unknown-result unwind while retiring
helper custody. The only permitted action on an uncertain old session is
explicit retirement; SQL reconciliation requires a new identity-verified session.
No queues, locks, new Java implementation, implicit Tasks or schedulers were
introduced for this decision.

Six additional tests run explicit Tasks in one ordinary Actor and gate a pending
protocol read with a standard stdin capability. Another Task verifies that STEP,
COMMIT, CLOSE and Statement operations receive CONNECTION_BUSY without output or
correlation changes. Known replies permit the exact next correlation; cancelling
pending STEP/COMMIT/CLOSE leaves RECONCILE_REQUIRED and cannot report successful
close when helper termination is unconfirmed. These are labelled protocol/client
models using structural I/O and explicit test Tasks, not native SQLite timing
claims. Existing native public/real-helper tests remain separate. All are NOT RUN.

### Owner-approved provisional launch cancellation

The owner approved, for PLAT074 and pending D256 ratification: **cancelling one
SQLite open does not revoke the SQLite authority of the whole Process.** An
open is one attempt to acquire one resource; the authority belongs to the
Process and outlives the cancellation of any single Task. No specification was
changed.

| Event | Effect |
| --- | --- |
| Task cancellation while an open waits for its launch | Cancels and retires only that attempt (`LAUNCH_CANCELLED`) |
| Launch failure of one helper | Retires only that attempt |
| Session retirement (`Connection.close()`, uncertain result) | Retires that Connection and its helper |
| Actor termination | Withdraws that Actor's pending attempts and retires its helpers |
| Process termination | Same for the Process, then revokes its authority |
| RuntimeHost close | Withdraws every attempt, retires every helper, revokes every authority, then ends the launcher thread |
| CREATE_NEW consumption | Independent of cancellation: once consumed and forced it stays VERIFY; never restored, re-issued or re-identified; a database that was never created requires explicit host intervention |
| Uncertain COMMIT/APPLY_ONCE result | RECONCILE_REQUIRED on the old session; reconciliation only through a new identity-verified session |

Host mechanism (`ProtosIsolatedSqliteHostFacility`, same launcher thread, same
monitor, same helper map): each launch first records a reservation keyed by its
session. The launcher thread removes the reservation, re-checks the publication
condition (attempt not withdrawn, authority still bound to the Process, Process
RUNNING, Actor not terminating, host open), starts the process and records custody
in one monitor section. Hence: cancelled before that section, the attempt is never
started and its private temporary is deleted by whoever withdrew it; cancelled
during it, the canceller waits for the monitor and retires the now-recorded helper;
cancelled after it (before the session is returned), the exact session is retired.
A cancelled Future is never taken as evidence that no process exists. Retirement
answers whether termination was confirmed within the bounded wait; unconfirmed
sessions are recorded explicitly and their temporaries are kept. Interrupt status
is cleared only for the bounded cleanup and restored for the caller.

Client classification of transaction results: a COMMIT answered SQLITE_BUSY is a
known SQLiteError (code 5) only when the helper also observed
`sqlite3_get_autocommit` reporting the transaction still open; every other failed
or unestablished COMMIT is SQLiteUncertainError RECONCILE_REQUIRED with the helper
retired once. A native failure escaping COMMIT or APPLY_ONCE (including the
APPLY_ONCE rollback) now also ends the helper session on the helper side, and the
client reports it under the public operation name with the code preserved.
APPLY_ONCE BUSY/FAILED outcomes are known only after the helper confirmed rollback
through autocommit; otherwise RECONCILE_REQUIRED.

| New acceptance test (all NOT RUN) | Acceptance condition |
| --- | --- |
| `cancellingOneOpenDoesNotRevokeProcessAuthority` | Cancellation injected at submission (either interleaving); B gets `LAUNCH_CANCELLED`, A keeps working, C opens; no live/pending/unconfirmed helper, no leaked temporary |
| `openCancelledBeforeStartIsNeverStarted` | Launcher thread held before its start section; B never reaches STARTED, no B helper process exists, temporary removed, authority valid |
| `openCancelledAfterStartRetiresOnlyThatHelper` | B's real helper started then cancelled before publication; that OS process exits, A and C unaffected, no leaked temporary |
| `ownerTerminationDuringLaunchPublishesNothing` | Root Actor, then Process, terminated while a launch is held; no published helper, no live helper; Actor termination keeps the authority, Process termination revokes it |
| `consumedCreateNewSurvivesCancellationAsVerifyOnly` | After a cancelled launch the manifest is VERIFY; with the file removed, reopen is refused and nothing is recreated |
| `realBusyCommitIsKnownAndKeepsTransactionOpen` | Real SHARED lock makes another helper's COMMIT return BUSY (code 5, not uncertain), transaction stays open; explicit commit after releasing the reader succeeds (positive control) |
| `nativeFailureReplyToApplyOnceRemainsUncertainAndRetiresHelperOnce` | Protos model (not native evidence): RECONCILE_REQUIRED, code 10, operation `applyOnce`, one release, later applyOnce/lookup refused without IPC |

The launch tests use a package-private Java observer of launch stages in the
test-only child-JVM driver; it is unreachable from guest code and grants nothing.
Cancellation is delivered as a thread interrupt by that driver: no current runtime
path interrupts a launch waiting in the facility, so how ordinary Protos Task
cancellation reaches a blocked launch remains a D256 question.

### Host boundary and upstream precedents

The one new production class remains `ProtosIsolatedSqliteHostFacility`.
It does not parse JSON, SQL, values, receipts or SQLite status. It grants opaque
identity authority, reads the trusted host manifest, launches through Truffle,
connects existing stream bindings, checks Actor/Process custody and retires OS
processes. Other Java edits propagate this grant through existing bootstrap and
lifecycle paths. Process creation permission now follows the exact bound Process,
not a host-wide Boolean. Copied ordinary connection closures cannot authorize a
session owned by another Actor.

Verifiable precedent:

- [GraalPy EmulatedPosixSupport, commit 5c8486970dead373d888f25210788cca5d79c374](https://github.com/oracle/graalpython/blob/5c8486970dead373d888f25210788cca5d79c374/graalpython/com.oracle.graal.python/src/com/oracle/graal/python/runtime/EmulatedPosixSupport.java):
  `forkExec` is a Truffle boundary; `exec` uses the Env process builder,
  explicit pipes/environment/directory and start, then registers child custody.
  `flock` / `doLockOperation` uses FileChannel locking including tryLock; ordinary
  file-channel operations supply the host-only manifest I/O precedent.
- [Espresso ProcessImpl, commit bd884eb3effea2b31f3fbdd7250267ba8e9eca8e](https://github.com/oracle/graal/blob/bd884eb3effea2b31f3fbdd7250267ba8e9eca8e/espresso/src/com.oracle.truffle.espresso/src/com/oracle/truffle/espresso/libs/libjava/impl/Target_java_lang_ProcessImpl.java):
  `forkAndExec` checks permission before using Env.newProcessBuilder.
- [TruffleProcessBuilder.start](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api/src/com/oracle/truffle/api/io/TruffleProcessBuilder.java)
  retains its associated language Context and delegates process creation through
  engine access. With explicit environment and directory it performs no guest
  execution on the launch thread.
- [EngineAccessor.createSubProcess](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.polyglot/src/com/oracle/truffle/polyglot/EngineAccessor.java)
  uses the retained Context's ProcessHandler and decorates process streams.
  This source inspection supports the host-only launcher-thread approach;
  actual behavior on the selected runtime is still NOT RUN. The stable platform
  thread preserves Linux parent-thread-death custody, without entering Protos.

No new Context, guest executor, scheduler, primitive, JNI, JDBC or Java SQL layer
was added. stdin/stdout remain separate pipes and stderr is drained separately
to a sink. Consuming a launch reservation, starting the process and recording
custody form one monitor section, so an interrupted waiter never orphans a child;
Process/Actor termination rechecks prevent reactivation. Cancelling one launch
retires only that attempt (see the provisional launch-cancellation decision).

### Distribution and acceptance coverage

The existing portable pipeline compiles the C launcher with warnings-as-errors,
PIE, stack protection, FORTIFY and RELRO, builds the existing private provider,
writes the profile/digest, installs helper sources, and incorporates all artifacts
into ZIP/checksum verification. The provider and main JAR retain LICENSE.TXT.
The parent's canonical Maven runtime projection is unchanged.

`ProtosIsolatedSqliteIntegrationTest` adds 61 test methods (54 earlier plus the 7 launch-cancellation and transaction tests above) and exercises an explicitly selected extracted
installation from an unrelated temporary working directory. It covers grant
refusal, copied grants/sessions, import/memory separation, real helper opening,
values and binary64 codec, fragmentation, 16 MiB BLOB boundaries, single SQL,
reset/clearBindings, copied rows, independent-JVM persistence/rollback,
cooperative replay, wrong/missing identity, refusal to adopt populated databases,
close and owner death, installed examples, native ATTACH denial with an
unrestricted real SQLite positive control, interior hardlink limitation,
ABI refusal, missing/modified launcher and protocol correlation/admission.
One COMMIT-without-acknowledgement case is a deterministic EOF model using
production Protos client code. A second uses the real confined helper and a
test-only Protos observation fault: it reads the complete real COMMIT response,
then drops it before the production client observes it. That test requires
SQLiteUncertainError, helper retirement, reopening through public std:sqlite
and persisted value 42. Java only provisions the existing host bridge and
executes the canonical direct-file entry; it parses no SQL or protocol. Neither
case is a power-cut or kernel-crash result.
A separate test checks maximum-size NUL-containing TEXT without JSON expansion
shrinking the public bound.
Existing LIB029/LIB030 model and real-provider tests remain required.

`dist/tests/runtime/test_sqlite_packaging.py` checks installed layout, executable
mode, exact profile/digest, private licensed provider, parent runtime separation
and checksum coverage. Neither suite runs implicitly here. Missing distribution
selection or unsupported Landlock is reported as UNSUPPORTED/skipped, never as
positive acceptance. Missing artifacts, wrong digest, timeouts, absent READY and
SQLite failures on an otherwise selected supported profile fail tests.

### Ratification checklist and remaining limits

D256 must ratify the exact signature, manifest shape and consumption/recovery
policy; opaque Process-local RootActor grant and Actor-local sessions;
synchronous waiting and cancellation limits; Statement/Connection lifecycle;
BEGIN/COMMIT/ROLLBACK and native autocommit; uncertain results and same-identity
reconciliation; effective Linux profile; platform exclusions; LIB029/LIB030
compatibility; and acceptance evidence against the final bytes.

Receipt replay is cooperative only: arbitrary SQL can alter receipts, identity,
transaction boundaries or durability PRAGMAs. Advertised receipt atomicity is
limited to cooperative transactional effects in the verified main database,
including cooperative triggers; attached-database and external-file effects
have no advertised replay guarantee. LOOKUP ABSENT remains provisional, without an inferred
rollback, termination proof or automatic retry. No SQL prefix filter, custom
security parser, second journal or general exactly-once claim was added.
Hardlinks, bind mounts, namespace races, a malicious helper owner, blocking native
calls and kernel D-state waits remain limitations. There is no power-cut test or
universal physical-release deadline. The test-only managed-Process driver exercises Actor termination with its Process
and JVM still alive, semantic Process termination while the JVM stays alive,
and a second Process's Context permission.
It is NOT RUN. The real-helper lost-COMMIT response test is also NOT RUN.
There is no new kernel-crash, storage-failure or power-cut execution evidence.

PLAT074 **OPEN**; D256 **pending ratification**; LIB031 **BLOCKED**.

### Materially inspected authorities and constraints

- Root instructions, DESIGN, IMPLEMENTATION and REFERENCE, plus protos/src scoped
  instructions: human execution only, one production class, preserve owned edits,
  no normative change or version bump in this intervention, APL notice retention.
- Durable D256-C1 decision and implementation handoff: Protos-first SQLite,
  unchanged memory surface, explicit unresolved ratification gates. The current
  durable IMPLEMENTATION_BLOCKERS registry was read; it has no SQLite-specific
  entry discharging those gates. No registry state was changed.
- VALUES_AND_COLLECTIONS numeric conversions and Float identity: exact integral
  Integer(Float), ties-to-even Float(Integer), semantic NaN and signed zero.
  IO_CORE close/termination, TEXT_IO strict decoding/bounded lines, PROCESS_IO
  stream authority, ACTORS termination/reentrancy and FUTURES_AND_TASKS pending
  observation/cancellation/FILE-entry exclusivity: reuse existing ownership, commitment
  and cleanup contracts without inferring rollback from cancellation.
- SQLite module, ABI/resource code, all four isolated Protos sources, current
  Java host/bootstrap/runtime changes, C launcher, portable/provider builders,
  existing LIB029 lifecycle tests, Landlock feasibility tests and D3 guide:
  preserve one engine, existing FFM, statement custody and historical evidence.
- Example index/programs, SQLite/CLI/FFM guides, Makefile and POM resource/test
  configuration, LICENSE.TXT Part 5: preserve conventions, exact validation entry
  points and packaged licenses. No build or validator was executed.


### Inspected file inventory

The following files supplied material constraints or were reconciled with the
candidate. Reads were targeted to the governing sections and affected code;
this is not a claim that every unrelated section of every large file was audited.

| Files | Constraint or use |
| --- | --- |
| Supplied root AGENTS.md; `AGENTS.work/DESIGN.md`, `AGENTS.work/IMPLEMENTATION.md`, `AGENTS.work/REFERENCE.md`; `protos/AGENTS.md`, `src/AGENTS.md`, `spec/AGENTS.md` | Human execution, owner design authority, patch scope, source style, license and validation policy. |
| `spec/semantics/VALUES_AND_COLLECTIONS.md`, `spec/semantics/FFI.md` | Exact conversions, binary64 special identity, no implicit FFI authority, Actor-local native custody, destructor status and one-time retirement. |
| `spec/io/IO_CORE.md`, `spec/io/TEXT_IO.md`, `spec/io/PROCESS_IO.md`, `spec/concurrency/ACTORS.md`, `spec/concurrency/FUTURES_AND_TASKS.md` | Terminal close results, strict text, bounded framing, Process backend retirement, Actor exclusivity/reentrancy, Task cancellation versus native completion. |
| `protos/lib/sqlite/module.protos`, `abi.protos`, `internal/Resources.protos`, `internal/Borrowed.protos`, `internal/IsolatedProtocol.protos`, `internal/IsolatedConnection.protos`, `internal/IsolatedHelper.protos`, `isolated-helper.protos` | One resource engine, existing ABI/bounds/copies, private protocol and helper, admission and lifecycle. Paths in this row share `protos/lib/sqlite/`. |
| `protos/lib/core/Float.protos`, `Integer.protos`, `String.protos`; `ProtosNumericValueSupport.java`, `ProtosStandardIntegerProtocol.java` | Existing numeric operations; no invented String suffix selector or Java numeric serialization. |
| `ProtosCli.java`, `ProtosCoreBootstrap.java`, `ProtosPolyglotExecutionContext.java`, `ProtosPolyglotProcessContext.java`, `ProtosPolyglotRuntimeHost.java`, `ProtosStandaloneHostedExecution.java`, `ProtosStandaloneProcessBootstrap.java`, `ProtosActorBootstrap.java`, `ProtosProcessRuntime.java`, `ProtosIsolatedSqliteHostFacility.java` | Reconcile all received production Java paths; preserve current bootstrap, scope permissions, no new executor of guest code. |
| `ProtosToolchainRoots.java`, `ProtosStandaloneHostedSession.java`, `ProtosDirectFileModuleResolver.java`, `ProtosCanonicalInitialModuleExecution.java`, `ProtosExecutionOutcome.java`, `ProtosHttpAsyncFacility.java`, `ProtosActor.java`, `ProtosProcessStandardStreamBinding.java`, `ProtosIoOperation.java`, `ProtosStandardTextReaderProtocol.java`, `ProtosStandardTextWriterProtocol.java` | Installed roots, canonical entry/test boundary, terminal construction, lifecycle and structural I/O reuse. These existing Java names are unique in `src/main/java/`. |
| `ProtosLib029SqliteLifecycleTest.java`, `ProtosExternalForeignProviderTest.java`, `ProtosPlat074SqliteLandlockFeasibilityTest.java`, `ProtosIsolatedSqliteIntegrationTest.java`; `protos/tests/foreign-provider/sqlite-failure-model.protos` | Existing model/harness conventions, real native positive controls and added acceptance/Task models. Java test names are unique in `src/test/java/`. |
| `dist/sqlite/sandbox-exec.c`, `dist/build_portable.py`, `dist/build_ffm_provider.py`, `dist/verify_portable.py`, `dist/test_external_foreign_providers.py`, `dist/tests/runtime/test_native_launcher.py`, `test_native_elf.py`, `test_sqlite_packaging.py` | Landlock profile, packaging pipeline, private provider, installed checks and validation commands. Last two paths share `dist/tests/runtime/`. |
| `protos/examples/ffi/README.md`, `sqlite-library.protos`, `sqlite-copied-values.protos`, `sqlite-durable-confined.protos`, `sqlite-durable-reopen.protos` | Existing public example style and new paired durable programs. Names in this row share `protos/examples/ffi/`. |
| `docs/guide/library/sqlite.md`, `sqlite-isolated-process-spike.md`, `sqlite-durable-confinement.md`; `docs/guide/tools/cli.md`; `docs/guide/interop/ffm-provider.md` | Existing evidence versus provisional integration; truthful limits and usage. First three paths share `docs/guide/library/`. |
| `Makefile`, `pom.xml`, `LICENSE.TXT`; `scripts/legacy_execution_guard.py` | Exact build/test entry points, current version, license packaging, canonical execution guard interface. |
| Remote `IMPLEMENTATION_BLOCKERS.md`, D256-C1 approval and implementation handoff; pinned GraalPy/Espresso sources and Truffle builder/engine source | No SQLite blocker closure, no ratification inferred, verifiable host-platform precedents. No remote mutation. |

### Human-executed final validation campaign

Run from the current checkout, in order, stopping at the first failure. These
commands were **not executed by the agent**. Compilation/portable construction
and especially the acceptance/full suites can take several minutes or longer;
maximum-value cases transfer 16 MiB payloads and need substantial heap. Report
UNSUPPORTED/skipped cases separately from PASS and require a supported Linux
ABI-10 host for positive confinement acceptance.

```sh
git status --short
git diff --check
git diff --stat
python3 -m py_compile dist/build_portable.py dist/build_ffm_provider.py dist/tests/runtime/test_sqlite_packaging.py
mvn -DskipTests package
bin/protos lint --fail-on-warning protos/lib/sqlite/module.protos
bin/protos lint --fail-on-warning protos/lib/sqlite/internal/Resources.protos
bin/protos lint --fail-on-warning protos/lib/sqlite/internal/IsolatedProtocol.protos
bin/protos lint --fail-on-warning protos/lib/sqlite/internal/IsolatedConnection.protos
bin/protos lint --fail-on-warning protos/lib/sqlite/internal/IsolatedHelper.protos
bin/protos lint --fail-on-warning protos/lib/sqlite/isolated-helper.protos
bin/protos lint --fail-on-warning protos/examples/ffi/sqlite-durable-confined.protos
bin/protos lint --fail-on-warning protos/examples/ffi/sqlite-durable-reopen.protos
python3 dist/build_portable.py --allow-dirty --skip-project-build
python3 dist/verify_portable.py --allow-dirty-source
export PROTOS_SQLITE_DISTRIBUTION=/workspaces/protos/target/distributions/protos-0.4.184-SNAPSHOT
python3 -m unittest discover -s dist/tests/runtime -p test_sqlite_packaging.py
mvn -Dprotos.sqlite.distribution="$PROTOS_SQLITE_DISTRIBUTION" -Dtest=ProtosIsolatedSqliteIntegrationTest,ProtosLib029SqliteLifecycleTest test
make test MVN_FLAGS="-Dprotos.sqlite.distribution=$PROTOS_SQLITE_DISTRIBUTION"
```

The installation selection above matches the inspected POM version; use the
actual current version if the owner changes it before this campaign. Inspect
the exact diff in addition to the changed-path summary before compiling.
Python syntax compilation, the Protos linter and Maven compilation are the
available scoped static gates; the POM declares no dedicated Java style plugin.
The C build itself supplies the warning/hardening compiler gate. No product
version bump or publication is authorized by this continuation.
