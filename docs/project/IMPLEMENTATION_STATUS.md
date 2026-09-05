# Implementation Status

<!-- BEGIN CANONICAL IMPLEMENTATION STATUS -->

This file is the canonical repository-level status view for implementation and
the formally tracked project work that drives it. It is project state, not
normative language specification.

Agents must verify this ledger against the current `origin/main` before acting
on it. Git history, the current implementation, tests, and normative dependency
owners remain the evidence used to verify a row.

Initialized: 2026-09-05
Repository implementation version at initialization: `0.2.83-SNAPSHOT`


<!-- PROJECT-STATUS-FAMILY-DISCOVERY: v5 -->
## Status vocabulary

- `OPEN` — known implementation item not yet ready or not yet started.
- `READY` — dependencies are satisfied and implementation may begin.
- `IN_PROGRESS` — work is actively underway; advisory only, never a lock.
- `BLOCKED` — a recorded blocker prevents progress; see the blocker ledger.
- `BLOCKED_BY_DEPENDENCIES` — prerequisite implementation items remain open.
- `CLOSED` — required implementation, validation, and publication are complete.

`CLOSED` requires successful required tests and publication to `main`. Patch
generation, static validation, or `READY_FOR_USER_VALIDATION` alone do not close
an item.

## Core implementation

| Item | Description | Status | Closure evidence | Dependencies / notes |
|---|---|---|---|---|
| I001 | args as Array | CLOSED | historical; not backfilled | — |
| I002 | uniform represented-value lookup | CLOSED | historical; not backfilled | — |
| I003 | Standard String | CLOSED | historical; not backfilled | — |
| I004 | Array completion | CLOSED | historical; not backfilled | — |
| I005 | Standard Map | CLOSED | historical; not backfilled | — |
| I006 | IdentityMap / identity hashing | CLOSED | historical; not backfilled | — |
| I007 | Core Error infrastructure | CLOSED | historical; not backfilled | — |
| I008 | Modules | CLOSED | historical; see git history / CHANGELOG | — |
| I009 | Future / Task | CLOSED | historical; see git history / CHANGELOG | I009A + I009B complete |
| I010 | Parallel Execution | CLOSED | `f94362d50f9c809e62d2a84665f75b091bead2ca` | I009 |
| I011 | Actors complete | IN_PROGRESS | — | build on I009/I010; see the verified I011 slice ledger below |
| I012 | Standard Bytes | CLOSED | historical; see git history / CHANGELOG | — |
| I013 | Standard Path | CLOSED | historical; see git history / CHANGELOG | — |
| I014 | Standard Byte I/O | CLOSED | `2462ba74298e94181489e13de4e25dbbb82b21f9` | I009 + I012 |
| I015 | Encoding / Text I/O | IN_PROGRESS | — | I014 closed; I015-A Encoding closed; streaming TextReader/TextWriter slices remain |
| I016 | Filesystem / File | CLOSED | `SAME_COMMIT` | I013 + I014; I016-A/B/C/D1/D2/D3/D4 complete |
| I017 | Process I/O / bootstrap | IN_PROGRESS | — | I017-A/B/C/D1/D2/E1/E2/E3 closed; coordinating I017-E CLOSED; I017-F READY for final authority/termination/native-boundary conformance |
| I018 | Core self-hosting / bootstrap minimization | CLOSED | `SAME_COMMIT` | I018-L exhaustive native-boundary inventory and architectural guard complete; I016-D pause lifted |

### I011 — Actors

Status: IN_PROGRESS

Description: Core Actor runtime and communication semantics, implemented incrementally on the
existing I009 Future/Task and I010 isolated-parallel infrastructure.

Normative owners:
- `spec/concurrency/ACTORS.md`
- `spec/semantics/MODULES.md` for Actor-local bootstrap module loading
- `spec/concurrency/FUTURES_AND_TASKS.md` for Future/Task integration
- `spec/concurrency/PARALLEL_EXECUTION.md` for Actor/P isolation interaction

Published slices:

| Slice | Status | Version | Closure evidence | Implemented surface |
|---|---|---|---|---|
| I011-1 | CLOSED | `0.2.81-SNAPSHOT` | `518aac11e2d3815a6bc790b66d93f214250cfd1d` | Actor incarnation identity; centralized lifecycle state machine; ActorRef permanently bound to one incarnation; deterministic lifecycle/concurrency coverage. |
| I011-2 | CLOSED | `0.2.82-SNAPSHOT` | `0c65053e4f6fbc1c090087c2bde6cd5ccd4322bd` | ActorRef as opaque communication capability; Actor-boundary rematerialization preserving semantic identity, identityHash, delegation parent, and target; no retargeting after termination. |
| I011-3 | CLOSED | `0.2.84-SNAPSHOT` | `SAME_COMMIT` | Destination-local bootstrap by canonical ModuleKey; exact local bootstrap-binding selection; invocation with already-transferred arguments; exact behavior installation and READY cutover; Actor execution-domain ownership/current-ActorRef substrate; initialization-failure termination. |
| I011-4 | CLOSED | `0.2.85-SNAPSHOT` | `SAME_COMMIT` | Actor-boundary graph snapshot/value-transfer foundation: atomic copy/validation for currently integrated transferable value families; alias/cycle preservation across roots; ActorRef capability rematerialization; NonTransferableValue rejection for non-transferable execution/resource values. |
| I011-5 | CLOSED | `0.2.86-SNAPSHOT` | `SAME_COMMIT` | Public frozen Actor prelude surface with spawn/current; creator-side canonical module resolution and synchronous pre-creation validation; Actor-transfer-backed initialization vector; creation cutover with post-cutover destination-local bootstrap kickoff. |
| I011-6 | CLOSED | `0.2.88-SNAPSHOT` | `SAME_COMMIT` | Bounded accepted-message mailbox ownership; READY-gated implicit event-loop dispatch; automatic Actor-local scheduler wakeups; weak-fair cross-Actor scheduling with one non-preemptive segment per selection and no same-incarnation parallel execution. |
| I011-7 | CLOSED | `0.2.91-SNAPSHOT` | `SAME_COMMIT` | Concrete-Actor pre-acceptance delivery admission/backpressure foundation: pending operation ownership outside the bounded accepted mailbox; deterministic capacity wakeups; known pre-acceptance cancellation; same-sender FIFO preservation and weak admission fairness. |
| I011-8 | CLOSED | `0.2.93-SNAPSHOT` | `SAME_COMMIT` | Public `ActorRef.send(selector, arguments...)` over I011-7 admission: exact semantic-String validation, synchronous whole-graph snapshot, local identity-bearing SendOperation with exactly cancel/retry, normal behavior dispatch with ignored send result, and fresh explicit retry over the original snapshot after known delivery failure. |
| I011-9 | CLOSED | `0.2.99-SNAPSHOT` | `SAME_COMMIT` | Public `ActorRef.request(selector, arguments...)` over the shared delivery path: fresh caller-domain Future, reply-value Actor transfer without Future adoption/flattening, deterministic pre/post-acceptance cancellation mapping, `NonTransferableValue` reply failure, and `RequestOutcomeUncertain` for known accepted work lost before a normal reply. |
| I011-10 | CLOSED | `0.2.105-SNAPSHOT` | `SAME_COMMIT` | Graceful Actor lifecycle: public idempotent `ActorRef.stop()` and fresh independent `ActorRef.termination()` observation Futures; TERMINATING-cutover cancellation of Actor-local tasks, Actor-originated pending non-task Futures and I/O; accepted-undispatched loss preservation; TERMINATED only after required task cancellation unwind. |
| I011-11 | CLOSED | `0.2.107-SNAPSHOT` | `SAME_COMMIT` | Actor-boundary `Map`/`IdentityMap` keyed-state transfer with alias/cycle and mutation-state preservation; destination hash/identity-hash bookkeeping rebuilt without executing user comparison/hash code; default Object hash uses semantic identity so rematerialized ActorRef capabilities remain valid keyed identities. |
| I011-12 | CLOSED | `0.2.108-SNAPSHOT` | `SAME_COMMIT` | GroupRef capability-identity and Actor-transfer foundation: semantic GroupRef identity is distinct from Group identity and physical wrappers; repeated transfer/rematerialization preserves identityHash, target Group identity, and effective restriction descriptor; independent GroupRef acquisitions remain distinct. No public Group routing/acquisition surface is introduced. |
| I011-13 | CLOSED | `0.2.112-SNAPSHOT` | `SAME_COMMIT` | Internal Process failure-domain / failure-authority substrate: one RootActor plus hosted Actor set; non-root fatal failure terminates only that incarnation; RootActor fatal failure terminates the Process and all hosted Actors; Process termination waits for Actor-required cancellation unwind; process-bound `Actor.spawn` retains local Process hosting. No public Process capability or launcher provisioning yet. |
| I011-14 | CLOSED | `0.2.114-SNAPSHOT` | `SAME_COMMIT` | Process capability Actor-delegation foundation: runtime-provisioned represented proxies carry authority into one existing logical Process; Actor transfer rematerializes a fresh wrapper to that same authority, preserves aliases within one graph transfer, and rebuilds authority-bearing descendants over the destination proxy. Process has no P-transfer contract. No public Process prototype/bootstrap slot or I/O surface is introduced. |
| I011-15 | CLOSED | `0.2.116-SNAPSHOT` | `SAME_COMMIT` | Internal ActorGroup membership/routing foundation: stable Group identity independent of membership; live Groups may have zero members; explicit membership changes do not change Group identity; only READY members are routing-eligible; runtime GroupRef acquisition binds to that concrete Group while preserving independent GroupRef identity and transfer rematerialization. No public Group acquisition/send/request/controller/Authority surface is introduced. |
| I011-16 | CLOSED | `0.2.121-SNAPSHOT` | `SAME_COMMIT` | Local ActorGroup communication-operation foundation: pending Group routing survives zero eligible membership; INITIALIZING->READY wakes routing; selected-member removal/pre-acceptance loss may requeue only before concrete acceptance; Group termination fails only still-pre-acceptance work; accepted work is never rerouted; Group request accepted-loss maps to RequestOutcomeUncertain; Group send reuses standard SendOperation cancel/retry control; all SendOperation implementations remain Actor/P non-transferable. No public Group acquisition/GroupRef selector installation or remote transport is introduced. |
| I011-17 | CLOSED | `0.2.124-SNAPSHOT` | `SAME_COMMIT` | Language-visible GroupRef communication surface: hidden source-backed GroupRef prototype with exactly `send`/`request`; shared audited communication-Closure construction helpers dispatch by receiver without adding native construction sites; Group calls reuse I011-16 local routing/backpressure/rerouting, concrete acceptance authority, SendOperation cancel/retry, request reply transfer and accepted-loss uncertainty. No public Group acquisition/discovery, controller/Authority, broadcast, or genuinely remote transport is introduced. |
| I011-18 | CLOSED | `0.2.126-SNAPSHOT` | `SAME_COMMIT` | Final local/cross-Process ActorGroup race/conformance slice: GroupRef continuity across member replacement; Process-loss rerouting only before concrete acceptance; accepted Group request loss maps to RequestOutcomeUncertain with no fallback replay; READY/cancel races admit exactly one legal outcome without duplicate execution. B004 records the still-undefined public Group/GroupRef acquisition/discovery API. |
| I011-19 | CLOSED | `0.2.129-SNAPSHOT` | `SAME_COMMIT` | Host-neutral remote ActorRef communication-route foundation: preserved ActorRef identity; transport-owned acceptance knowledge; send cancellation/retry over known failure or uncertainty; request uncertainty (including cancellation ambiguity) -> RequestOutcomeUncertain; normal replies re-snapshot at the caller boundary. No public transport/discovery API or physical transport policy is added. |

Remaining implemented-surface gaps before top-level closure:
- B004 blocks only public/distributed Group acquisition/discovery because exact Group/GroupRef API/syntax and any new public discovery API remain normatively undefined; I011-19 now wires the host-neutral remote ActorRef transport/acceptance-uncertainty boundary, while GroupRef remote routing over that boundary remains independent implementation work;
- RootActor launcher/bootstrap provisioning and standard Process authority exposure remain to I017; the internal Process failure domain plus runtime Process-capability materialization/Actor delegation are wired, and Process remains excluded from P transfer;
- I011-18 closed the final deterministic local/cross-Process race/conformance pass; top-level closure now depends only on remaining remote Group routing, I017 reconciliation, and B004's deliberately unresolved public acquisition/discovery surface.


### I015 — Encoding / Text I/O

Status: IN_PROGRESS

Normative owners:
- `spec/io/TEXT_IO.md`
- `spec/io/IO_CORE.md`
- `spec/io/BYTE_IO.md` for underlying byte capability/Future/lifecycle semantics
- `spec/semantics/OBJECT_MODEL.md` for Encoding semantic-family receiver domains

Implementation plan:

| Slice | Status | Version | Closure evidence | Implemented surface |
|---|---|---|---|---|
| I015-A | CLOSED | `0.2.122-SNAPSHOT` | `SAME_COMMIT` | Source-backed standard Encoding factory/prototype identity; four mandatory portable immutable descriptors; exact semantic-family receiver checks; strict synchronous one-shot encode/decode; fresh Bytes results; UTF validity and initial matching-BOM consumption; ISO-8859-1 Latin1; explicit host Encoding-provisioning boundary; authority-free Actor/P transfer; reviewed two-site I018 representation bridge. |
| I015-B | READY | — | — | TextReader factory/prototype and borrowing/owning construction validation; per-flow incremental decoder/input state; ordered progress-oriented `readText`; EOF/error deferral, cancellation zero-consumption, ownership/lifecycle integration. |
| I015-C | BLOCKED_BY_DEPENDENCIES | — | — | `readLine()` / `readLine(maxBytes)` framing over the same decoder domain: LF/CR/CRLF, EOF-final line, exact encoded-octet budget, LineTooLong lifecycle, decoding/replacement accounting and deterministic ordering. |
| I015-D | BLOCKED_BY_DEPENDENCIES | — | — | TextWriter factory/prototype and borrowing/owning construction; per-flow encoder state; ordered `writeText`, empty-write zero-transition behavior, failure aftermath, flush/close ownership/lifecycle rules and capability honesty. |
| I015-E | BLOCKED_BY_DEPENDENCIES | — | — | Final cross-slice Text I/O conformance, portable/host Encoding integration, post-I015 I018 boundary re-audit and canonical I015 closure. |

Dependency chain: `I015-A -> I015-B -> I015-C -> I015-D -> I015-E`.

Coordination with I017:
- I017-D2 requires the standardized Encoding semantic family/descriptors and host-provisioning boundary, all closed by I015-A.
- TextReader/TextWriter are ordinary explicit layering facilities and are not prerequisites for Process standard-stream Encoding association.
- Therefore I017-D2 is READY after I015-A even while top-level I015 remains IN_PROGRESS.

Current implementation boundary after I015-A:
- `Encoding` is a required frozen prelude factory/prototype, source-created in `protos/lib/core/encoding.protos`;
- the only mandatory descriptors are `UTF8`, `UTF16LE`, `UTF16BE`, and `Latin1`; no public name constructor/registry/aliases exist;
- one-shot encode/decode are synchronous strict operations with exact String/Bytes domains and Encoding-family receiver validation;
- descriptors are immutable and reusable with no shared mutable per-flow codec state or I/O authority;
- TextReader, readLine and TextWriter remain unimplemented and begin at I015-B.

### I016 — Filesystem / File

Status: CLOSED

Coordination result: I016-D resumed only after a post-I018 current-main audit. Because I018-L guards every Java native-Closure provider, each I016-D substep that changes that boundary must update the inventory and executable guard in the same published change.

Description: Core Filesystem/File semantics implemented incrementally on I013 Path and I014 byte-I/O commitment/lifecycle infrastructure.

Normative owners:
- `spec/io/FILESYSTEM.md`
- `spec/io/IO_CORE.md`
- `spec/io/BYTE_IO.md`
- `spec/io/PROCESS_IO.md` for Root bootstrap provisioning and authority-transfer boundaries

Published slices:

| Slice | Status | Version | Closure evidence | Implemented surface |
|---|---|---|---|---|
| I016-A | CLOSED | `0.2.87-SNAPSHOT` | `SAME_COMMIT` | Host-neutral Filesystem open preflight/acquisition substrate: exact local-option snapshot/defaults, invalid-combination precedence before backend authority, independent asynchronous opens, cancellation/portable-effect commitment handshake, backend result-custody cleanup, and Actor-termination cancellation integration. No public Filesystem/File capability surface is installed yet. |
| I016-B | CLOSED | `0.2.89-SNAPSHOT` | `SAME_COMMIT` | Positioned File capability core: stable zero-based logical cursor independent of native cursors; ordered read/write/position/seek/seekToEnd/size/truncate/sync; exact access/optional capability shape; bounded write admission; Closable lifecycle; Actor-termination/pre-commit cancellation integration. No public Filesystem.open surface and no append mode yet. |
| I016-C | CLOSED | `0.2.90-SNAPSHOT` | `SAME_COMMIT` | Append-mode File semantics: each non-empty write selects then-current EOF at contribution time; empty append preserves the cursor; failed-prefix aftermath is exact; same-resource File aliases use a backend-wide atomic append-placement boundary with nondeterministic relative order and no overlap/interleaving; pre-commit cancellation/Actor termination contribute nothing. |
| I016-D1 | CLOSED | `0.2.109-SNAPSHOT` | `SAME_COMMIT` | Host-provisioned open-only Filesystem capability; `open` integration with I016-A and standard File materialization from B/C; exact ordinary options domain; authority/confinement/stable-resource backend contract; minimal mandatory I018 native-boundary registration. |
| I016-D2 | CLOSED | `0.2.110-SNAPSHOT` | `SAME_COMMIT` | Explicit runtime File/Filesystem authority markers; actual standard protocol materialization uses those markers; direct live authority and authority-bearing ordinary descendants fail Actor transfer with `NonTransferableValue` and P transfer with `NonParallelValue`; no native-Closure boundary expansion. |
| I016-D3 | CLOSED | `0.2.111-SNAPSHOT` | `SAME_COMMIT` | Integrated authority/open conformance covers pre/post-commit cancellation, late custody cleanup, stable selected-resource binding, independent out-of-order opens, backend confinement-policy rejection, and descriptor-owned optional capability shape; post-D2 I018 boundary re-audit confirms 23 providers / 91 sites and no Filesystem prelude binding. |
| I016-D4 | CLOSED | `0.2.113-SNAPSHOT` | `SAME_COMMIT` | Final cross-slice focal validation plus full-suite publication; canonical I016 closure; post-D3 I018 boundary remains 23 providers / 91 sites; fresh I017 dependency audit found no relevant unresolved implementation blocker and released I017 to READY. |

Current I016-D completion plan:
- **D1 — Filesystem open integration (CLOSED by this slice):** expose only a host-provisioned `open` capability, connect A to B/C, enforce exact ordinary options, and register the unavoidable resource bridge with the I018 guard.
- **D2 — authority/transfer boundaries (CLOSED):** explicit File/Filesystem runtime authority markers now back the real standard capabilities; Actor/P reject direct and delegation-carried live authority without broadening the standard message surface.
- **D3 — deterministic authority/race conformance + I018 re-audit (CLOSED):** integrated tests now cover cancellation commitment, stable selected-resource custody, independent opens, backend authority rejection, and descriptor-owned capability shape; the post-D2 native boundary remains exactly 23 providers / 91 sites.
- **D4 — final I016 closure (CLOSED):** final cross-slice focal validation and the complete Maven suite passed on the definitive publication baseline; I016 is CLOSED and I017 is READY after a fresh dependency audit.

Dependency chain: `I016-D1 -> I016-D2 -> I016-D3 -> I016-D4`.

Closure result:
- I016-A closes exact `Filesystem.open` option capture, validation precedence, independent acquisition, cancellation, commitment and result-custody semantics;
- I016-B closes positioned File cursor, read/write, seek/size/truncate/sync capability shape, lifecycle, close cutover and Actor-originated cancellation;
- I016-C closes append placement at then-current EOF, alias-wide no-overlap/no-interleaving, empty-append and failed-prefix aftermath;
- I016-D1 exposes only host-provisioned `Filesystem.open`, binds successful opens to stable selected standard File resources, and records the authority/confinement backend contract without creating a Core-global Filesystem;
- I016-D2 makes File/Filesystem live authority explicit and rejects direct or delegation-carried authority at Actor/P copy boundaries without implicit proxy/reopen behavior;
- I016-D3 provides deterministic integrated authority/open conformance and re-audits I018 at exactly 23 native providers / 91 construction sites;
- I016-D4 validates all I016 slices together plus the full suite on the definitive baseline, closes I016, and releases I017 to READY. The current baseline already includes I011-13's internal Process failure-domain / RootActor substrate; I017 still requires its own mandatory current-main audit before implementation and coordination with whatever additional I011 work is then current.


### I017 — Process I/O / bootstrap

Status: IN_PROGRESS

Audit/reconciliation result:
- I016 is CLOSED and no relevant implementation blocker is currently BLOCKED;
- I011-13 already owns the internal Process failure domain, unique RootActor and hosted-Actor termination authority;
- I011-14 landed concurrently before I017-A and already implements the exact Process capability Actor-delegation/P-exclusion foundation originally planned for I017-A, so I017 reuses that published substrate instead of creating a competing representation;
- I015 Encoding / Text I/O is not yet guaranteed CLOSED on every I017 baseline, so the standard `stdinEncoding()`, `stdoutEncoding()`, and `stderrEncoding()` integration has an explicit external dependency on I015.

Normative owners:
- `spec/io/PROCESS_IO.md`
- `spec/io/IO_CORE.md`
- `spec/io/BYTE_IO.md` for standard-stream byte protocols
- `spec/io/TEXT_IO.md` for Encoding values returned by the `*Encoding()` accessors
- `spec/concurrency/ACTORS.md` for explicit Process delegation during Actor transfer/spawn
- `spec/concurrency/PARALLEL_EXECUTION.md` for the absence of a Process P-transfer contract
- `spec/semantics/MODULES.md` for RootActor initial-module `moduleContext` provisioning

Published / planned slices:

| Slice | Status | Version | Closure evidence | Implemented surface |
|---|---|---|---|---|
| I017-A | CLOSED | `0.2.115-SNAPSHOT` | `SAME_COMMIT + I011-14_REUSED` | Reconciled/validated Process capability foundation already published by I011-14: runtime-provisioned Actor-local proxies; explicit fresh Actor rematerialization to the same logical Process authority; graph alias preservation; authority-bearing descendants rebuilt over the destination proxy; Process excluded from P; provisioning rejected after Process termination begins. No duplicate Process representation or public accessor surface introduced. |
| I017-B | CLOSED | `0.2.117-SNAPSHOT` | `SAME_COMMIT` | Canonical immutable Process-argument snapshot: one-time complete host capture with stable representability outcome; exact `size`/zero-based `at`/polymorphic ordered `each`; same canonical identity on repeated Process acquisition; fresh destination identity with alias preservation for ordinary Actor/P transfer; reviewed I018 representation bridge at 3 native sites. |
| I017-C | CLOSED | `0.2.118-SNAPSHOT` | `SAME_COMMIT` | Canonical read-only Environment snapshot with stable Process acquisition outcome/identity; duplicate-native-name rejection; exact query representability and native identity; selective value decoding for get/contains; polymorphic each with whole-snapshot String prevalidation and canonical Unicode-scalar ordering; ordinary Actor/P value-copy identity; reviewed 3-site I018 representation bridge. |
| I017-D1 | CLOSED | `0.2.119-SNAPSHOT` | `SAME_COMMIT` | Stable independently optional stdin/stdout/stderr bindings; repeated/Actor-delegated views share one logical per-binding queue; exact read-only/write-only surface with no implicit lifecycle/File/text authority; Actor-local Futures use I014 cancellation/commitment machinery; termination blocks new work; P rejects live stream authority; reviewed 2-site I018 resource bridge. |
| I017-D2 | CLOSED | `0.2.123-SNAPSHOT` | `SAME_COMMIT` | Stable exactly-once stdin/stdout/stderr Encoding association states coupled to D1 stream availability; portable or host-provided immutable Encoding accepted; unavailable is distinct from invalid availability/Encoding mismatch; no hidden host lookup/default and no native-boundary expansion. |
| I017-E | CLOSED | `0.2.130-SNAPSHOT` | `SAME_COMMIT` | E1 public Process accessors, E2 RootActor bootstrap-local authority provisioning/import confinement, and E3 standalone host/CLI bootstrap capture/wiring are all published and integrated. |
| I017-E1 | CLOSED | `0.2.127-SNAPSHOT` | `SAME_COMMIT` | Source-backed frozen authority-free `Process` prototype; exactly eight synchronous Process accessors over A/B/C/D1/D2 state; exact represented-capability receiver domain; canonical snapshot acquisition; stable stream/Encoding lookup failure; all proxies rejected after Process termination; one audited native Closure construction helper. |
| I017-E2 | CLOSED | `0.2.128-SNAPSHOT` | `SAME_COMMIT` | RootActor canonical initial-module context receives bootstrap-local `process` before first source expression and optional exact host-granted `filesystem`; cache-before-execute/cycles preserved; ordinary imports and non-root Actor bootstrap receive no ambient Process/Filesystem authority; explicit Process Actor delegation remains the only transfer path; no I018 boundary expansion. |
| I017-E3 | CLOSED | `0.2.130-SNAPSHOT` | `SAME_COMMIT` | Standalone host/CLI captures trailing application args excluding launcher identity, one native Environment snapshot/domain, stdin/stdout/stderr byte bindings and explicit UTF-8 host associations before first source expression; non-importable entries use the E2 RootActor authority model; optional Filesystem grants are accepted explicitly but the standard CLI grants none rather than converting launcher source-read authority into ambient application authority; exact hidden ActorRef/Bytes Core identities retained; no I018 expansion. |
| I017-F | READY | — | — | Final Process/Actor/termination authority conformance, post-I017 I018 native-boundary re-audit, CLI/runtime integration regression suite and canonical I017 closure. |

Dependency chain: `I017-A -> I017-B -> I017-C -> I017-D1 -> I017-D2 -> I017-E1 -> I017-E2 -> I017-E3 -> I017-F`. I017-E is the coordinating parent for E1/E2/E3. The Encoding-family dependency of D2 was satisfied by published I015-A; remaining I015 TextReader/TextWriter work is independent.

Current implementation boundary after I017-E3:
- I017 uses one source-backed authority-free public `Process` prototype and one runtime `ProtosProcessCapabilityValue` representation; the standalone CLI creates exactly one logical Process/RootActor pair per one-shot execution or REPL session;
- file and `-e` launcher/source identities are excluded from `process.args()` while all trailing arguments are captured atomically before the first source expression; REPL args are the stable empty snapshot;
- Environment is captured once from the launcher host; native name validity/identity is delegated to an isolated JDK ProcessBuilder environment-map probe, while portable String conversion and duplicate-equivalent validation remain the I017-C snapshot rules;
- stdin/stdout/stderr are established from the CLI-supplied byte streams and remain independent Process bindings; the CLI host explicitly selects UTF-8 for all three text Encoding associations without changing the byte-oriented stream API;
- the exact hidden source-backed Core Bytes and ActorRef prototypes are retained as runtime-only Prelude metadata, not public bindings, so standalone Process bootstrap does not fabricate parallel standard identities;
- both importable RootActor initial modules (E2) and non-importable standalone entry contexts (E3) receive bootstrap-local `process` and only an explicitly supplied optional `filesystem` before first source execution; imports and non-root Actors remain ambient-authority-free;
- the standard CLI intentionally supplies no default Filesystem capability because its launcher authority to locate/read program source is not application Filesystem authority; embedding hosts may pass an already-provisioned restricted/default Filesystem explicitly;
- one-shot CLI execution terminates its Process after evaluation and the REPL terminates its Process on session exit; E3 adds no native Closure construction site, so I018 remains 28 providers / 102 sites;
- final cross-slice authority, lifecycle/termination, Actor delegation, P exclusion, CLI regression and post-I017 native-boundary conformance remains I017-F.
### I018 — Core self-hosting / bootstrap minimization

Status: CLOSED

Description: Reduce Java-side Core bootstrap to irreducible host/runtime machinery and narrow representation/selector bridges, moving faithfully expressible distributable Core behavior to `protos/lib/core/` without changing observable Protos semantics.

Architecture owner:
- `docs/project/CORE_BOOTSTRAP_ARCHITECTURE.md`

Normative constraints:
- applicable normative semantic owners remain authoritative;
- I018 changes implementation placement only and must not redefine Protos behavior;
- source-backed Core behavior must continue to use ordinary objects, Closures, lookup, invocation, errors, and delegation.

Published slices:

| Slice | Status | Version | Closure evidence | Implemented surface |
|---|---|---|---|---|
| I018-A | CLOSED | `0.2.92-SNAPSHOT` | `SAME_COMMIT` | Source-backed standard `Object.init` and `Object.!=` bodies loaded from `protos/lib/core/object.protos`; isolated frozen capture context prevents the process-global `Object` from retaining the main Core-construction bindings; Java retains only the current installation bridge and host-backed primitives needed by this slice. |
| I018-B | CLOSED | `0.2.94-SNAPSHOT` | `SAME_COMMIT` | Source-backed standard `Actor` prelude object loaded from `protos/lib/core/actor.protos`; Java no longer allocates the public Actor entry object and now only installs the host-backed `spawn`/`current` bridges into that exact source-created object before freezing it. |
| I018-C | CLOSED | `0.2.95-SNAPSHOT` | `SAME_COMMIT` | Source-backed standard `BufferedReader` and `BufferedWriter` frozen-prelude factory/prototype objects loaded from `protos/lib/core/buffered_reader.protos` and `protos/lib/core/buffered_writer.protos`; Java no longer allocates their factory identities and only installs the host-backed `call`/`owning` bridges into those exact source-created objects. |
| I018-D | CLOSED | `0.2.96-SNAPSHOT` | `SAME_COMMIT` | Source-backed standard `import` frozen-prelude facility loaded from `protos/lib/core/import.protos`; Java no longer allocates the public import-facility identity and only installs the host-backed `call` bridge that preserves exact semantic-String validation, host resolution, Actor-local module caching, cycles, and failure behavior. |
| I018-E | CLOSED | `0.2.97-SNAPSHOT` | `SAME_COMMIT` | Source-backed internal standard `Bytes` factory/prototype loaded from `protos/lib/core/bytes.protos`; Java no longer allocates the bootstrap Bytes identity used by buffered byte wrappers, installs the existing native Bytes protocol into the exact source-created object, and removes the construction-only `Bytes` binding before building the frozen prelude so Core v0.1 still exposes no required `Bytes` prelude binding. |
| I018-F | CLOSED | `0.2.98-SNAPSHOT` | `SAME_COMMIT` | Source-backed standard ordinary `Integer.negated()` body defined in `protos/lib/core/integer.protos` as `0 - this`; Java no longer implements unary Integer negation directly and now only verifies that the installed `negated` slot is source-backed, while the exact Integer subtraction primitive remains host-backed and preserves the semantic Integer receiver domain. |
| I018-G | CLOSED | `0.2.100-SNAPSHOT` | `SAME_COMMIT` | Source-backed standard ordinary `Integer.%` body defined in `protos/lib/core/integer.protos` through a temporary named Closure and installed under the symbolic `%` selector by a narrow Java selector bridge; the source body validates the original receiver through standard native Integer `+` before dispatching native `mod`, preserving Integer receiver-domain rejection while removing the duplicated native remainder body for `%`. |
| I018-H | CLOSED | `0.2.101-SNAPSHOT` | `SAME_COMMIT` | Source-backed standard `Float.negated()` body defined in `protos/lib/core/float.protos` as `(0.0 - 1.0) * this`; Java no longer performs Float unary sign inversion directly and now only verifies source provenance, while native binary64 subtraction/multiplication preserve strict Float receiver-domain validation, signed-zero inversion, infinities, subnormals, and Core NaN semantics. |
| I018-I | CLOSED | `0.2.102-SNAPSHOT` | `SAME_COMMIT` | Source-backed standard frozen-prelude bindings object constructed in `protos/lib/core/prelude.protos` with direct `Context` parent and the exact existing Core binding surface; Java no longer allocates or populates the prelude object slot-by-slot, and Error-taxonomy export is source-owned while Java retains taxonomy validation and the final freeze/ProtosPrelude boundary. |
| I018-J | CLOSED | `0.2.103-SNAPSHOT` | `SAME_COMMIT` | Source-backed default `Object.==` body defined in `protos/lib/core/object.protos` as primitive semantic identity (`this === other`) and promoted through the existing narrow symbolic-selector installation path; Java no longer duplicates default equality logic, while primitive `===`, `identityHash`, generic call/object construction, and other host/runtime boundaries remain native. |
| I018-K | CLOSED | `0.2.104-SNAPSHOT` | `SAME_COMMIT` | Source-backed internal standard `ActorRef` and `SendOperation` delegation prototypes are constructed in `protos/lib/core/actor.protos` and supplied to the Actor runtime installer; Java no longer allocates those standard prototype identities and retains only the Actor/runtime communication bridges (`send`/`request`, `cancel`/`retry`) plus representation, scheduling, transfer, and lifecycle machinery. The construction-only helper bindings are removed before prelude construction and remain absent from the public prelude. |
| I018-L | CLOSED | `0.2.106-SNAPSHOT` | `SAME_COMMIT` | Final I018 closure: exhaustive inventory of all remaining Java native-Closure providers classifies every retained boundary as host-irreducible, representation-backed, concurrency/runtime-backed, or resource/capability-backed; no source-expressible standard slot remains. A regression guard fixes the exact 22-provider/90-construction-site native boundary, verifies helper-backed runtime selector surfaces and migrated source provenance, and prevents silent Java-only Core growth. I018 is CLOSED and the I016-D coordination pause is lifted. |\n
Closure result:
- exhaustive audit found no remaining source-expressible standard slot implemented only in Java;
- every retained Java-native standard Closure is recorded in `docs/project/CORE_NATIVE_BOUNDARY.md` as an irreducible host, representation, concurrency/runtime, or resource/capability bridge;
- `ProtosCoreNativeBoundaryArchitectureTest` guards the exact provider/construction-site boundary, helper-backed standard selector surfaces, source-backed provenance, and Core-bootstrap allocation boundary;
- I018 is complete. The temporary coordination pause after published I016-C is lifted; I016 may resume from I016-D after re-auditing the then-current `origin/main`.


## CLI implementation

| Item | Description | Status | Version | Closure evidence | Notes |
|---|---|---|---|---|---|
| CLI001 | Basic CLI + persistent REPL | CLOSED | — | historical; not backfilled | — |
| CLI002 | Interactive terminal UX | CLOSED | — | historical; not backfilled | — |
| CLI003 | Multiline REPL input | CLOSED | `0.2.80-SNAPSHOT` | `254c80c0fb9e70f1dd07ef711f06ce71faa93829` | published multiline REPL input; parser-EOF accumulation, one-unit bracketed paste, persistent top-level context, recovery/history/stream coverage; recursive factorial regression is covered after standard numeric ordering completion |
| CLI004 | Standard-library module resolution | CLOSED | `0.2.120-SNAPSHOT` | `SAME_COMMIT` | official CLI host resolver for reserved `std:<logical-name>` distribution modules; logical relocation-independent ModuleKey identity; no search-path shadowing/fallback; bootstrap `core/` excluded; no normative Core module change |
| CLI005 | Portable Standard Library naming | CLOSED | `0.2.131-SNAPSHOT` | `SAME_COMMIT` | case-significant ASCII `std:` identities; exact distributed path spelling independent of host filesystem case rules; case-fold ambiguity, bootstrap `core`, and Windows reserved device-name segments rejected; canonical LIB001 `Set`/`IdentitySet` spellings |


## Standard Library

The `LIBxxx` family records distributable standard-library functionality
implemented primarily as ordinary Protos modules outside `protos/lib/core/`.
Library work builds on existing language/Core semantics; it does not define new
normative language behavior. If a library requires a missing Core/runtime
semantic prerequisite, that prerequisite must be resolved and tracked through
the applicable specification/design and implementation work before the library
relies on it.

Core implementation source under `protos/lib/core/` remains owned by the
applicable `Ixxx` work. Its location in `protos/lib/` does not by itself make it
Standard Library work.

LIB identifiers are stable work-item identities, not a mandatory execution
order. Later library work uses the next unused `LIBxxx` identifier; do not
renumber existing items merely to express priority. Readiness and actual
implementation order follow real dependencies, so independently ready library
work may proceed without waiting for an earlier-numbered roadmap item.

| Item | Description | Status | Closure evidence | Dependencies / notes |
|---|---|---|---|---|
| LIB001 | Collections library | IN_PROGRESS | — | I004 + I005 + I006 + I008 + CLI004 + CLI005 closed; LIB001-A is CLOSED at `0.2.125-SNAPSHOT`, LIB001-B is READY, and later slices remain dependency/audit gated. |
| LIB002 | Text / encoding conveniences | BLOCKED_BY_DEPENDENCIES | — | I015 is not closed; ordinary library conveniences may build on Encoding/Text I/O but must not redefine their Core semantics. |
| LIB003 | JSON / serialization | BLOCKED_BY_DEPENDENCIES | — | LIB001 is not closed; design exact text/encoding and stream-adapter dependencies from the then-current repository rather than assuming roadmap order is dependency order. |
| LIB004 | Filesystem / process conveniences | BLOCKED_BY_DEPENDENCIES | — | I016 and I017 are not closed; convenience APIs must preserve Filesystem/Process authority boundaries and may not manufacture ambient host authority. |
| LIB005 | Networking | OPEN | — | Roadmap item only; `spec/io/IO_CORE.md` currently leaves network authority acquisition, socket APIs, DNS/name resolution, and transport configuration outside its standardized scope. Re-audit and establish prerequisites before implementation. |

### LIB001 — Collections

Status: IN_PROGRESS

Description: General-purpose collection data structures and algorithms built as
ordinary Protos library modules on top of the existing Core collection and
module facilities.

Design record:
- `docs/project/LIB001_COLLECTIONS_DESIGN.md` records the completed comparative
  architecture audit and the focused Set/IdentitySet API audit;
- the record is non-normative: it constrains Standard Library implementation but
  does not redefine Core semantics; CLI004 introduced the `std:` host/distribution boundary and CLI005 closes
  its portable case-significant naming policy; neither changes Core module
  semantics;
- the focused audit closes the initial Set/IdentitySet representation and public
  contract, including the correction from "arbitrary Map role" to a well-formed
  ordinary Map/IdentityMap `key -> true` library invariant.

Implementation boundary:
- distributable library source belongs under `protos/lib/collections/`;
- existing Core `Array`, `Map`, and `IdentityMap` semantics and prototypes remain
  Core and are not reclassified as library work;
- Set/IdentitySet add no runtime value family, tag, wrapper, privileged transfer
  identity, generic Collection hierarchy, or native boundary;
- portable modules are `std:collections/Set` and
  `std:collections/IdentitySet`;
- Set stores every introduced member as an ordinary Map key mapped to canonical
  `true`; IdentitySet does the same over IdentityMap;
- module invocation is the constructor (`sets(...)` / `identitySets(...)`), and
  the initial closed Set surface is `contains`, `add`, `remove`, `size`, `each`,
  `union`, `intersection`, `difference`, `sameMembers`, `isSubset`,
  `isSuperset`, and `isDisjoint`;
- `add`/`remove` return the exact Set argument after success; removal absence
  follows the underlying Map Error; `each` invokes callbacks with exactly one
  member and uses the underlying keyed snapshot/order;
- Set algebra returns fresh open Sets with deterministic left-derived order as
  recorded in the design document; ordinary Map `==`/`hash` remain unchanged;
- initial Array algorithms remain eager/module-centric candidates and require the
  focused API audit recorded below before their implementation slices become
  ready.

Planned slices:

| Slice | Status | Version | Closure evidence | Scope / unblock condition |
|---|---|---|---|---|
| LIB001-A | CLOSED | `0.2.125-SNAPSHOT` | `SAME_COMMIT` | Set/IdentitySet ordinary modules: `key -> true` representation, variadic module-call construction, `contains`, `size`, focal real-`std:` conformance. |
| LIB001-B | READY | — | — | Add `add`, `remove`, one-argument `each`, state/order/snapshot/failure conformance; LIB001-A is CLOSED. |
| LIB001-C | BLOCKED_BY_DEPENDENCIES | — | — | Fresh Set algebra + predicates + Set-area Actor/identity/order/final conformance; depends on LIB001-B CLOSED. |
| LIB001-D | OPEN | — | — | Eager Array `map`/`filter`/`findIndex`; becomes READY only after a fresh current-main audit closes callback, snapshot/mutation, and `findIndex` absence contracts. |
| LIB001-E | BLOCKED_BY_DEPENDENCIES | — | — | Eager Array `reduce`/`sort` plus final cross-slice LIB001 validation/closure; requires LIB001-D CLOSED and the reduce/sort contracts audited before implementation. |

Dependencies:
- I004 Array completion — CLOSED;
- I005 Standard Map — CLOSED;
- I006 IdentityMap / identity hashing — CLOSED;
- I008 Modules — CLOSED;
- CLI004 Standard-library module resolution — CLOSED;
- CLI005 Portable Standard Library naming — CLOSED.

### LIB002 — Text / encoding conveniences

Status: BLOCKED_BY_DEPENDENCIES

Description: Ergonomic text and encoding helpers implemented as ordinary Protos
library functionality on top of finalized Core Encoding/Text I/O semantics.

Planning boundary:
- Core Encoding, TextReader, and TextWriter semantics remain owned by I015 and
  the normative I/O specification;
- reciprocal or convenience operations may be library functionality only when
  they preserve the canonical Core operations and argument/result semantics;
- no concrete API, import spelling, module layout, or implementation slices are
  assigned by this roadmap entry; derive them from the current repository when
  LIB002 work begins.

Dependencies:
- I003 Standard String — CLOSED;
- I012 Standard Bytes — CLOSED;
- I015 Encoding / Text I/O — not CLOSED.


### LIB003 — JSON / serialization

Status: BLOCKED_BY_DEPENDENCIES

Description: Structured-data encoding, decoding, and serialization facilities
implemented through ordinary Protos values, collections, modules, and explicit
text/byte adaptation where required.

Planning boundary:
- no special JSON syntax, implicit conversion, hidden object serialization, or
  new Core semantic category is implied by this roadmap item;
- roadmap numbering does not create a dependency on LIB002 by itself;
- when LIB003 design begins, re-audit whether particular textual, encoded-byte,
  streaming, or file adapters depend on LIB002, I015, or later I/O work;
- no concrete data model, API, import spelling, module layout, or slices are
  assigned yet.

Dependencies:
- existing Core Array, Map, String, Bytes, and Modules foundations are available;
- LIB001 Collections — not CLOSED;
- exact optional adapter dependencies must be established by the future LIB003
  audit rather than guessed here.


### LIB004 — Filesystem / process conveniences

Status: BLOCKED_BY_DEPENDENCIES

Description: Higher-level filesystem and Process conveniences layered over the
standard capability-based File/Filesystem and Process I/O surfaces.

Planning boundary:
- convenience code must preserve capability confinement, lifecycle, commitment,
  and authority-transfer semantics;
- library code must not obtain ambient filesystem, process, subprocess, or other
  host authority merely because a host API exists;
- no shell API, path utility surface, stream helper API, import spelling, module
  layout, or implementation slices are assigned yet.

Dependencies:
- I013 Standard Path — CLOSED;
- I014 Standard Byte I/O — CLOSED;
- I017 Process I/O / bootstrap — not CLOSED;
- re-audit I015/LIB002 dependencies for text-oriented conveniences when LIB004
  work begins.


### LIB005 — Networking

Status: OPEN

Description: Future standard-library networking facilities, to be designed only
after Protos has an explicit portable authority and transport substrate suitable
for library code to consume.

Planning boundary:
- the current Core I/O model does not standardize network authority acquisition,
  socket creation/connect/bind/listen/accept, DNS/name resolution, datagram
  addressing, or transport-configuration APIs;
- LIB005 must not smuggle those missing host/runtime capabilities into ordinary
  library code;
- if portable networking requires new normative Core/runtime semantics, establish
  and track that prerequisite outside LIB005 before the library relies on it;
- no protocol stack, socket API, HTTP API, import spelling, module layout, or
  implementation slices are assigned by this roadmap entry.

Dependencies:
- exact Core/runtime networking prerequisites are not yet established;
- re-audit the then-current specification and implementation before changing
  LIB005 from OPEN to a ready/blocked implementation state.


## Language Maturity

The `LMxxx` family records bounded language-maturity / dogfooding work:
language-level conformance programs, executable examples, tutorials, and
interaction/regression coverage that exercise already-implemented semantics
without itself defining new normative language behavior.

The first four LM identifiers were assigned retrospectively by the v6 historical
reconciliation because the work was published on `main` before the repository
persisted its operational LM identifiers. Their closure evidence is therefore
the already-published implementation commit.

| Item | Description | Status | Closure evidence | Notes |
|---|---|---|---|---|
| LM001 | Language-level coverage and examples | CLOSED | `99845b791a8e27798bc2c9cc8e47dc917d739a70` | Retrospective canonical ID; Map, IdentityMap, Path and regression conformance/examples. |
| LM002 | Language-level conformance and tutorials | CLOSED | `c0ba8b3f5bf3dc0a0997ca5fbfae6035db04d1d9` | Retrospective canonical ID; call-argument, Array and tutorial/example coverage. |
| LM003 | Language interaction conformance | CLOSED | `dbc086ed294ead0b4219952c02b00ec28a492472` | Retrospective canonical ID; delegation, dynamic receiver, captured-state and inherited-call interaction coverage. |
| LM004 | Extended language interaction conformance | CLOSED | `d390c0c642c5d2d907fcf9e384d1cbc080dd4783` | Retrospective canonical ID; deeper delegation/call-argument interactions plus Map/IdentityMap Path-key behavior. |

New Language Maturity work MUST allocate and persist its `LMxxx` identifier in
the repository at publication time rather than relying on chat/prompt history.

## P-label classification

`P57` and similar `Pnn` references found in historical conformance/changelog
text are specification/requirement paragraph labels, not a repository
project-work family analogous to `Ixxx`, `Bxxx`, `Dxxx`, `CLIxxx`, `LIBxxx`, or `LMxxx`.

For example, the changelog records “P57 Integer conformance programs” as tests
covering the P57 Integer requirement. That evidence MUST NOT be auto-promoted
to a project-status item named `P057` or `P57`.

If a future project-work family named `Pxxx` is introduced, it must be declared
explicitly by a canonical project ledger; identifier resemblance alone is not
sufficient.

<!-- PROJECT-STATUS-HISTORICAL-RECONCILIATION: v6 -->

<!-- BEGIN AUTO-DISCOVERED WORK REGISTRY -->

## Formally tracked project work

This auto-generated registry complements, but does not duplicate, the curated implementation, CLI, Standard Library, and Language Maturity tables above. It indexes other formal work families from authoritative project records and published specification decisions.

Identifier shape alone is insufficient: incidental IDs from design ideas, tests, benchmarks, examples, and arbitrary prose are intentionally excluded.

`RECORDED` means the owning project record contains the item without an explicit lifecycle state. Published decisions in `spec/PROTOS_SPEC_CHANGELOG.md` are `CLOSED`.

### B family

| Item | Title | Status | What it records / establishes | Owning source(s) |
|---|---|---|---|---|
| B001 | Empty Sequence execution | CLOSED | Implementation area: Truffle lowering / execution of a `CanonicalSequence` containing zero expressions. | `docs/project/IMPLEMENTATION_BLOCKERS.md` |
| B002 | Delegation parent of `without` / `alias` result objects | CLOSED | Implementation area: Standard `Object.without(name)` and `Object.alias(sourceName, aliasName)` message behavior and any runtime helper that constructs their result objects. | `docs/project/IMPLEMENTATION_BLOCKERS.md` |
| B003 | Delegation parent / lookup chain of canonical Boolean values | CLOSED | Implementation area: Standard prototype/delegation bridge for the canonical `true` and `false` runtime representations, including ordinary member lookup and polymorphic invocation through their delegation chains. | `docs/project/IMPLEMENTATION_BLOCKERS.md` |

### D family

| Item | Title | Status | What it records / establishes | Owning source(s) |
|---|---|---|---|---|
| D017 | Actor API closure cleanup | CLOSED | Removed residual wording that presented already-closed Core Actor API decisions as open or implementation-selectable. | `spec/PROTOS_SPEC_CHANGELOG.md` |
| D018 | Canonical Process bootstrap snapshot identity | CLOSED | Administratively records the already-published Process bootstrap snapshot identity semantics: each logical Process has one canonical identity-bearing `process.args()` snapshot and one canonical identity-bearing `proce... | `spec/PROTOS_SPEC_CHANGELOG.md` |
| D019 | Actor creator capability discipline | CLOSED | Removed the stale `parentActor` ambient capability from Core Actor semantics: creation genealogy alone grants no reverse `ActorRef`, creator lookup, or implicit reply channel to the created Actor. | `spec/PROTOS_SPEC_CHANGELOG.md` |
| D020 | String transformation surface | CLOSED | Closed the Core v0.1 status of `uppercase()` and `replace(...)`: neither selector is a standard Core String operation. | `spec/PROTOS_SPEC_CHANGELOG.md` |
| D021 | GroupRef semantic identity | CLOSED | Distinguished Group identity, semantic `GroupRef` object identity, and physical proxy/wrapper representation. Same-Group references are not automatically the same `GroupRef`. | `spec/PROTOS_SPEC_CHANGELOG.md` |
| D022 | Standard Object.init normal result | CLOSED | Defines the inherited standard `Object.init()` normal result as its receiver (`this`), making direct invocation portable. | `spec/PROTOS_SPEC_CHANGELOG.md` |
| D023 | Slot-write expression normal result | CLOSED | Defines the normal result of `x: value`, `object.x: value`, `x = value`, and `object.x = value` as the exact object produced by right-hand-side evaluation. | `spec/PROTOS_SPEC_CHANGELOG.md` |
| D024 | Receiver-bound Closure semantic identity | CLOSED | Defines every successful receiver member-read selecting a Closure as producing a fresh identity-bearing Closure value distinct from both the stored Closure and every other extraction result, including repeated identic... | `spec/PROTOS_SPEC_CHANGELOG.md` |
| D025 | Closure asynchronous-method ownership | CLOSED | Fixes the standard Closure-specific selectors `future` and `parallel` as ordinary local Closure-valued slots of `Object`; every Core Closure reaches them through its D027 direct delegation edge to `Object`, with no st... | `spec/PROTOS_SPEC_CHANGELOG.md` |
| D026 | Boolean standard-object surface | CLOSED | Resolves the normative contradiction over a standard `Boolean` object: Core v0.1 defines exactly the canonical Boolean values `true` and `false` and installs no standard prelude binding, object, or prototype named `Bo... | `spec/PROTOS_SPEC_CHANGELOG.md` |
| D027 | Portable Core delegation topology | CLOSED | Closes the observable standard-object topology with one general rule: every Core-standard visible object whose immediate parent is not otherwise specified delegates directly to `Object`. | `spec/PROTOS_SPEC_CHANGELOG.md` |
| D028 | Path parent-component selector disambiguation | CLOSED | Renames the standard Path operation that appends one parent-traversal component from `parent()` to `parentComponent()`. | `spec/PROTOS_SPEC_CHANGELOG.md` |
| D029 | Standard Integer result family | CLOSED | Defines one general result-only rule in the Values and Collections numeric owner: when a Core-standard operation returns or resolves simply to `Integer`, without naming a more specific numeric family, the result is an... | `spec/PROTOS_SPEC_CHANGELOG.md` |
| D030 | Callback-domain and eager-validation closure | CLOSED | Defines `Future.then(transform)` against the existing ordinary-invokable protocol rather than a hidden or Closure-only callback category. | `spec/PROTOS_SPEC_CHANGELOG.md` |
| D031 | Idempotent lifecycle Future identity | CLOSED | Defines one cross-cutting I/O lifecycle rule: every invocation of a standardized Future-returning idempotent lifecycle operation produces a fresh standard Future identity, even when calls observe the same pending or a... | `spec/PROTOS_SPEC_CHANGELOG.md` |
| D032 | Fresh reflection Array identity | CLOSED | Defines every successful `slotNames()` call as producing a fresh identity-bearing standard Array, including repeated observations of an unchanged object and empty results. | `spec/PROTOS_SPEC_CHANGELOG.md` |
| D033 | Reflective local-slot name argument domain | CLOSED | Defines the standard `hasSlot(name)`, `slotValue(name)`, and `removeSlot(name)` argument domain uniformly as semantic `String` values. | `spec/PROTOS_SPEC_CHANGELOG.md` |
| D034 | Callback-domain and eager-validation closure | CLOSED | Defines `Future.then(transform)` against the existing ordinary-invokable protocol rather than a hidden or Closure-only callback category. | `spec/PROTOS_SPEC_CHANGELOG.md` |
| D035 | Fresh and independent standard Bytes results | CLOSED | Defines every successful Core-standard operation that produces a logical new `Bytes` result as returning a fresh open standard Bytes identity, including empty results, unless that operation expressly returns an existi... | `spec/PROTOS_SPEC_CHANGELOG.md` |
| D036 | Future result identity semantics | CLOSED | Defines the general Core-standard Future result-identity rule: unless an operation expressly returns an already-existing Future, every successfully dispatched invocation that produces a Future result produces a fresh... | `spec/PROTOS_SPEC_CHANGELOG.md` |
| D037 | Path equality versus semantic identity | CLOSED | Clarifies that portable Path equality is structural and filesystem-independent, using rootedness plus the ordered component sequence, while Path semantic identity remains ordinary individual object identity. | `spec/PROTOS_SPEC_CHANGELOG.md` |
| D038 | Encoding semantic-family membership and receiver domain | CLOSED | Defines Encoding descriptors positively as Encoding semantic values produced or provisioned by normative Encoding-producing operations or explicit permitted host Encoding-provisioning boundaries. | `spec/PROTOS_SPEC_CHANGELOG.md` |

<!-- END AUTO-DISCOVERED WORK REGISTRY -->

## Update protocol

When publishing an implementation item:

1. verify this file against the current `origin/main`;
2. update the item's status in the same patch whenever practical;
3. record the implementation version if the item changes it;
4. record closure evidence:
   - use the concrete implementation SHA when it is already known, or
   - use `SAME_COMMIT` when the implementation and ledger update are the same
     commit;
5. update dependency transitions made possible by the closure;
6. keep unrelated rows unchanged;
7. do not use this ledger as a substitute for normative audit;
8. when any formal tracked work item or family is added or materially changes
   lifecycle state, update the appropriate curated table/ledger or regenerate
   the tracked-work registry in the same change whenever practical;
9. keep item explanations concise and point to the owning source rather than
   duplicating normative/design text.

For Language Maturity work, allocate the next unused `LMxxx` identifier before
publication and include it in the same repository change. Do not create LM IDs
only in prompts or chat history.

For Standard Library work, allocate the next unused `LIBxxx` identifier when the
work is formally introduced and record it in the curated Standard Library
section. Keep Core/runtime prerequisites under their applicable implementation
families rather than hiding them inside a library item.

If an item is implemented through slices, the top-level item remains
`IN_PROGRESS` until every requirement assigned to that item is integrated,
validated, and published. Slice progress may be recorded in a dedicated
subsection when useful, but partial slice publication does not imply top-level
closure.

## Related project ledgers

- `docs/project/IMPLEMENTATION_BLOCKERS.md` — normative implementation blockers.
- `docs/project/OPEN_TASKS.md` — operational/project backlog.
- `docs/project/IMPLEMENTATION_STATUS.md` — implementation progress and
  dependency state.

<!-- END CANONICAL IMPLEMENTATION STATUS -->
