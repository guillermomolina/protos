# Protos Language Specification Changelog — 0.1.200–0.1.299

Archived from [`../PROTOS_SPEC_CHANGELOG.md`](../PROTOS_SPEC_CHANGELOG.md).

## [0.1.299] - 2026-09-04

### Closed
- Closed `ActorRef routing implementation` as runtime machinery rather than a
  portable Core semantic facility.
- Allowed direct tables, local/Cluster directories, cached routes, distributed
  location metadata, routing services, transport endpoint metadata, or equivalent
  mechanisms to locate one concrete Actor incarnation.
- Required route updates/refreshes to preserve ActorRef identity, no-retargeting,
  same-sender FIFO, snapshot, acceptance, backpressure, cancellation, failure,
  reachability, and uncertainty semantics.
- Prohibited stale/missing routes from authorizing replacement retargeting,
  replay of accepted operations, duplication, or erasure of delivery uncertainty.
- Left route-cache, directory, replication, endpoint, refresh, forwarding, and
  lookup mechanisms as implementation concerns.
- Kept ActorRef persistence/serialization/capability encoding as separate open
  topics.
- Removed `ActorRef routing implementation` from Open Design Topics.

### Changed
- Updated `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized all five revisioned specification documents to document revision
  299.

## [0.1.298] - 2026-09-04

### Closed
- Closed `Service discovery implementation` as runtime/infrastructure machinery
  rather than portable Core semantics.
- Allowed discovery to use local registries, Cluster control state, external
  naming services, orchestrator APIs, replicated stores, static configuration,
  or other conforming mechanisms.
- Required implementation choice to preserve resolved ActorRef/GroupRef identity,
  discovery-name rebinding semantics, capability authority, lifetime, and
  durability boundaries.
- Preserved pay-as-you-grow: unused discovery must not require distributed
  runtime infrastructure.
- Left registry protocols, storage/replication, caching, watches/polling,
  backend selection, health probing, TTL bookkeeping, and retry/backoff as
  implementation concerns.
- Explicitly left public discovery API, namespace, consistency, TTL, watches,
  federation, persistence, security, and schema/versioning semantics outside
  this closure.
- Removed `Service discovery implementation` from Open Design Topics.

### Changed
- Updated `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized all five revisioned specification documents to document revision
  298.

## [0.1.297] - 2026-09-04

### Fixed
- Made P Error propagation explicitly use the ordinary P value-transfer boundary
  rather than an implicit privileged exception channel.
- Required transferable P Error graphs to become caller-domain transferred Error
  values, without preserving P-local identity for identity-bearing objects.
- Required an untransferable P Error graph to produce standard
  `NonParallelValue` under the existing P result-transfer failure rule.
- Prohibited leaking/sharing a live P-local Error object, implicit remote-error
  proxies, or hidden P execution-control state merely because the transferred
  value represents failure.
- Clarified that dynamic handlers, stacks, return homes, activations,
  continuations, and scheduler state never form part of P Error transfer.
- Connected the transferred caller-domain Error to the existing failed-Future
  rule: later `value()` observation performs a new consumer-side non-resumable
  signal.

### Changed
- Synchronized all revisioned specification documents to revision 297.
  `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_RUNTIME_SEMANTICS.md`, and
  `docs/design/CONCURRENCY_DESIGN.md` gain normative clarification in this revision.

## [0.1.296] - 2026-09-04

### Closed
- Closed `Physical-locality discovery` as runtime/administrative machinery rather
  than a portable Core semantic facility.
- Allowed runtimes to discover same-process, same-host, NUMA, rack, zone, region,
  network, or equivalent physical relationships using implementation-specific
  mechanisms.
- Prohibited discovered locality from changing Protos identity, transferability,
  mutable-state isolation, authority, ordering, acceptance, failure,
  cancellation, uncertainty, or transport requirements.
- Defined inability to discover locality as non-semantic; runtimes may fall back
  to any other conforming transport/placement mechanism.
- Kept physical-topology diagnostics outside portable Core.
- Preserved the broader logical-vs-physical topology relationship as a separate
  open topic.
- Removed `Physical-locality discovery` from Open Design Topics.

### Changed
- Updated `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized all five revisioned specification documents to document revision
  296.

## [0.1.295] - 2026-09-04

### Fixed
- Defined deterministic precedence between ordinary read cancellation and the
  `shutdownRead()` cutover for the same pending read.
- Required a cancellation that already won and committed `cancelled` before the
  shutdown cutover to retain that terminal outcome.
- Required the shutdown cutover to commit `null` when it reaches a read that is
  still uncommitted and not already terminally cancelled.
- Prohibited a later cancellation from rewriting shutdown-committed `null`, and
  prohibited shutdown from rewriting an already-terminal cancellation.
- Defined genuinely concurrent cancellation/shutdown races by whichever existing
  semantic commitment event commits first, independent of host callback timing.
- Preserved cancellation's zero-consumption guarantee and ReadShutdown's existing
  permission to discard uncommitted internal/native input.

### Changed
- Synchronized all revisioned specification documents to revision 295. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.294] - 2026-09-04

### Closed
- Closed `Transport selection and switching` as runtime policy already bounded by
  the transport-independent Actor communication semantics.
- Defined no portable Core API for choosing, pinning, querying, or constraining
  physical communication transport.
- Allowed runtimes to select and switch among in-process, shared-memory, IPC,
  network, or equivalent transports only when identity, ordering, snapshots,
  transferability, backpressure, acceptance, cancellation, failure, uncertainty,
  and authority remain unchanged.
- Prohibited transport switching from duplicating/replaying accepted operations
  or silently retrying due solely to a transport change.
- Kept administrative transport diagnostics outside portable Core semantics.
- Preserved message serialization format/versioning/schema evolution as separate
  open topics.
- Removed `Transport selection and switching` from Open Design Topics.

### Changed
- Updated `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized all five revisioned specification documents to document revision
  294.

## [0.1.293] - 2026-09-04

### Closed
- Closed `Cross-process same-host optimization` and `Shared-memory transport
  eligibility and lifecycle`.
- Defined same-host/shared-memory transport as an implementation optimization
  that must preserve ordinary Actor pass-by-value/snapshot semantics.
- Required identical transferability, aliasing/cycle, sender-ordering,
  backpressure, acceptance, cancellation, uncertainty, failure, and capability
  behavior regardless of physical transport.
- Prohibited shared-memory eligibility, pinning, backing-allocation identity,
  zero-copy state, or same-host placement from becoming portable Core
  observations.
- Defined shared-memory segment/mapping/reclamation/handle/page lifecycle as
  non-semantic implementation machinery.
- Permitted transport switching only when it preserves the same Actor-observable
  semantics without resetting ordering, duplicating accepted work, changing
  identity, or erasing uncertainty.
- Removed both corresponding items from Open Design Topics.

### Changed
- Updated `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized all five revisioned specification documents to document revision
  293.

## [0.1.292] - 2026-09-04

### Fixed
- Removed the residual vague architectural promise that Core should preserve the
  ability to add resumable conditions without redesigning the execution model.
- Reconciled Language and Runtime with the closed Core v0.1 non-resumable Error
  model: no resumable-condition continuation authority is retained by current
  signaling semantics.
- Preserved future extensibility only through an explicit recovery/restart
  facility with its own normative control-state contract.
- Required any such future facility not to reinterpret existing Core
  `Error.signal()` operations as resumable.

### Changed
- Synchronized all revisioned specification documents to revision 292.
  `PROTOS_LANGUAGE_SPEC.md` and `PROTOS_RUNTIME_SEMANTICS.md` gain normative
  clarification in this revision.

## [0.1.291] - 2026-09-04

### Closed
- Closed the optional administrative application/service identity topic for Core
  v0.1.
- Defined no intrinsic `ApplicationRoot`, application identity, service identity,
  deployment identity, or mandatory execution-hierarchy level above the
  already-defined runtime identities.
- Permitted deployment/orchestration/observability tooling to associate external
  application/service metadata with Protos entities only as non-semantic
  administrative metadata.
- Prohibited such metadata from changing sender identity, routing, placement,
  lifecycle, supervision, authority, continuity, identity comparison, or
  pay-as-you-grow behavior.
- Reaffirmed that distributed service continuity belongs to Group/discovery/
  Cluster-control/durable-state mechanisms rather than an administrative label.
- Left any future first-class application/service identity to earn and define its
  own semantic boundary explicitly.
- Removed the corresponding item from Open Design Topics.

### Changed
- Updated `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized all five revisioned specification documents to document revision
  291.

## [0.1.290] - 2026-09-04

### Fixed
- Defined `flush()` admission and ordering relative to `shutdownWrite()` when one
  logical output direction exposes both `Flushable` and `WriteShutdown`.
- Required a flush admitted before the write-shutdown cutover to remain a
  preceding accepted output operation that shutdown cannot overtake or
  retroactively move to the post-shutdown side.
- Required a flush ordered after the cutover to fail under the write-shut-down
  lifecycle without establishing a new propagation frontier or becoming an
  implementation-selected successful no-op.
- Defined genuinely concurrent cross-Actor flush/shutdown requests to receive one
  stable routing/admission order, matching the existing shared-flow ordering
  model.
- Preserved the ordinary success/failure/cancellation aftermath of a preceding
  admitted flush and deliberately left flush-failure recovery to the already
  existing Flushable/wrapper/clean-frontier semantics.
- Preserved the rule that `shutdownWrite()` does not imply one universal flush.

### Changed
- Synchronized all revisioned specification documents to revision 290. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.289] - 2026-09-04

### Fixed
- Fixed the observable ordering between handler selection and `ensure` cleanup
  during unwind.
- Defined handler selection to consume/deactivate the selected dynamic frame
  before cleanup runs on the path to that handler boundary.
- Required a cleanup Error raised during that unwind to search only still-active
  outer handlers and handlers explicitly installed by cleanup itself.
- Prohibited the already-selected handler from recursively catching a cleanup
  failure merely because the cleanup Error also matches its prototype.
- Aligned `transferToHandler` Runtime pseudocode with this ordering.
- Combined this rule with the existing cleanup-Error precedence: a cleanup Error
  supersedes the pending original transfer and prevents invocation of the
  consumed originally selected handler.

### Changed
- Synchronized all revisioned specification documents to revision 289.
  `PROTOS_LANGUAGE_SPEC.md` and `PROTOS_RUNTIME_SEMANTICS.md` gain normative
  clarification in this revision.

## [0.1.288] - 2026-09-04

### Closed
- Closed `NUMA-aware scheduling` as an implementation-only scheduler concern
  already constrained by the closed P scheduler-policy rules.
- Defined no portable Core meaning for NUMA-node identity, memory-node identity,
  CPU/package/socket/cache locality, or P-work affinity to physical topology.
- Allowed runtimes to use or ignore NUMA-aware scheduling, placement, migration,
  replication, pinning, and topology-sensitive cost models only when existing
  isolation, determinism, fairness, progress, and Actor-turn semantics remain
  unchanged.
- Prohibited portable Core code from requesting or observing NUMA placement or
  treating topology-sensitive optimization choices as semantic outcomes.
- Left any future explicit hardware-placement/performance-control facility to
  define its own contract.
- Removed `NUMA-aware scheduling` from Open Design Topics.

### Changed
- Updated `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized all five revisioned specification documents to document revision
  288.

## [0.1.287] - 2026-09-04

### Fixed
- Defined general `ensure` cleanup Error precedence rather than leaving
  competing unwind outcomes implementation-selected.
- Required normal cleanup completion to preserve the pending scope-exit transfer.
- Required an Error signaled by cleanup to supersede the pending transfer,
  including an earlier Error unwind, non-local return, normal scope exit, or
  cancellation unwind.
- Clarified that handling the cleanup Error later does not resurrect the
  superseded earlier transfer.
- Defined no automatic composite/suppressed-error object or language-visible
  causal link between the cleanup Error and the superseded Error.
- Generalized the already-existing cleanup-supersedes-cancellation rule rather
  than creating a separate cancellation-only exception.

### Changed
- Synchronized all revisioned specification documents to revision 287.
  `PROTOS_LANGUAGE_SPEC.md` and `PROTOS_RUNTIME_SEMANTICS.md` gain normative
  clarification in this revision.

## [0.1.286] - 2026-09-04

### Closed
- Closed `Behavior requirements before READY`.
- Defined that Core does not require a dispatchable current behavior throughout
  the whole `INITIALIZING` state because no external message is dispatched then.
- Required exactly one valid ordinary behavior object at the
  `INITIALIZING -> READY` cutover.
- Prohibited hidden/default/bootstrap sentinel behavior objects from becoming a
  portable semantic requirement.
- Defined normal completion of initialization without an installed behavior as
  initialization failure; the Actor never reaches `READY`.
- Preserved the existing queued/accepted-message behavior for initialization
  failure.
- Left the exact behavior installation/replacement API and syntax open.

### Changed
- Updated `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized all five revisioned specification documents to document revision
  286.

## [0.1.285] - 2026-09-04

### Fixed
- Reconciled output-wrapper close/finalization with the general deterministic
  `Closable` cutover semantics.
- Defined wrapper finalization/propagation to cover adapter state/output already
  semantically committed before the close cutover, plus the normal aftermath of
  preceding operations that had already crossed their own commitment boundary.
- Clarified that accepted-but-uncommitted adapter operations are closure-terminated
  at the cutover and do not become committed merely because close begins.
- Prohibited reversible validation, speculative encoding, staged bytes, reserved
  buffer space, or queued requests from leaking into wrapper finalization after
  their owning operation is closure-terminated.
- Required already-committed buffered/output state to remain subject to the
  wrapper's normal finalization/propagation obligations rather than being discarded
  by close.
- Applied the same distinction to explicit wrapper ownership close order and to
  `TextWriter.close()` encoder/final-byte finalization.
- Preserved the existing rule that close is not a universal drain of reversible
  pending work and did not change wrapper ownership of the underlying target.

### Changed
- Synchronized all revisioned specification documents to revision 285. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.284] - 2026-09-04

### Closed
- Closed `Module implementation sharing` as an implementation-only concern
  already constrained by Actor-local module-state semantics.
- Distinguished Actor-local module instances, contexts, caches, mutable slots,
  initialization state, and captures from physically shareable immutable
  implementation artifacts.
- Allowed parsed syntax, bytecode, machine code, immutable metadata, and similar
  artifacts to be shared or duplicated only when the choice is unobservable.
- Prohibited artifact sharing from creating shared mutable module state,
  execution contexts, initialization state, Futures/tasks, resources, or
  Actor-local authority.
- Left artifact cache placement, eviction, deduplication, compilation tiers, JIT
  specialization, and physical artifact identity as implementation details.
- Removed `Module implementation sharing` from Open Design Topics.

### Changed
- Updated `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized all five revisioned specification documents to document revision
  284.

## [0.1.283] - 2026-09-04

### Closed
- Closed `Non-transferable resource capabilities` and `Foreign-resource proxies`.
- Distinguished transfer of an existing value from provisioning a new
  proxy/routed capability.
- Required ordinary Actor transfer of a non-transferable live resource to fail
  with `NonTransferableValue`; no runtime may silently proxy, reopen, duplicate,
  broker, or substitute the resource to make transfer succeed.
- Defined proxy/capability provisioning as a separate semantic operation that
  must specify identity, authority, ordering, failure, cancellation,
  close/lifetime, and state-sharing behavior.
- Clarified that equivalent access to an external resource is not equivalent to
  transferring the original capability.
- Applied the same no-auto-proxy rule to P.
- Removed both corresponding items from Open Design Topics.

### Changed
- Updated `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md`.
- Synchronized all five revisioned specification documents to document revision
  283.

## [0.1.282] - 2026-09-04

### Fixed
- Defined failed-Future observation through `value()` as a new non-resumable
  Error signaling event in the consumer's then-current dynamic handler context.
- Prohibited Future failure records from preserving or restoring the producer's
  abandoned signaling continuation, activation frames, dynamic handlers, return
  homes, or other resumption authority.
- Clarified that a consumer handler result belongs only to the consumer-side
  handler boundary and cannot resume, retry, or inject a value into the failed
  producer computation.
- Defined repeated failed-Future observations as repeated consumer-side signals,
  never revival or re-entry of the failed producer.
- Applied the rule across same-Actor tasks, Actor boundaries, and P isolation so
  Future failure transport cannot become an implicit continuation-transfer
  mechanism.

### Changed
- Synchronized all revisioned specification documents to revision 282.
  `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_RUNTIME_SEMANTICS.md`, and
  `docs/design/CONCURRENCY_DESIGN.md` gain normative clarification in this revision.

## [0.1.281] - 2026-09-04

### Fixed
- Completed the `size()` / `at(index)` contract of the immutable sequence
  returned by `process.args()`.
- Defined `size()` as the exact non-negative Integer application-argument count
  over already-established bootstrap data, with no hidden suspension.
- Reused the standard `Array.at` zero-based index domain: exact semantic Integer
  only, `0 <= index < size()`, with no negative indexing, coercion, wrapping,
  truncation, saturation, or host-width interpretation.
- Defined valid `at(index)` to return the exact String at that snapshot position.
- Defined invalid type/negative/out-of-range indexing to use the same standard
  indexed-access failure semantics as `Array.at`, never `null` as an invalid-index
  sentinel.
- Kept Process-argument snapshots free to use non-Array representation/object
  identity and preserved their immutability.

### Changed
- Synchronized all revisioned specification documents to revision 281. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.280] - 2026-09-04

### Closed
- Closed `Java interoperability isolation`, `Java static mutable state`, and
  `Native global state`.
- Defined foreign mutable host/global state as unable to bypass Actor isolation
  merely because the storage lives outside the Protos heap.
- Prohibited ordinary cross-Actor wrappers from exposing unrestricted shared
  mutation of the same Java/native/global object.
- Distinguished host thread safety from Protos Actor isolation.
- Allowed safe interoperability through isolated copies/snapshots, semantically
  immutable sharing, explicit capability/service boundaries, or single-Actor
  ownership with ordinary Actor communication.
- Clarified that host reference identity does not become cross-Actor Protos
  identity and that observable copy-vs-share choices cannot be implementation
  selected.
- Applied the same rule to P: thread-safe/process-global does not imply
  P-transferable.
- Removed all three corresponding items from Open Design Topics.

### Changed
- Updated `PROTOS_RUNTIME_SEMANTICS.md` and `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized all five revisioned specification documents to document revision
  280.

## [0.1.279] - 2026-09-04

### Closed
- Closed `Blocking foreign calls` and `Blocking-operation offload`.
- Defined a synchronous foreign/host call as part of the current uninterrupted
  Protos execution segment even when its physical work is offloaded.
- Permitted runtimes to move blocking host work to helper threads/carriers or
  equivalent machinery only as an observationally invisible optimization.
- Prohibited physical offload from creating hidden Actor-local reentrancy or an
  implicit Protos suspension point.
- Required an explicit asynchronous/Future-returning extension contract when
  foreign work should release the Actor for other runnable Protos work.
- Clarified that a non-returning synchronous foreign call may stall its Actor
  indefinitely; Core adds no hidden timeout, cancellation, or preemption.
- Left worker-pool sizing, queueing, scheduling, and similar offload machinery as
  implementation details.
- Removed both corresponding items from Open Design Topics.

### Changed
- Updated `PROTOS_RUNTIME_SEMANTICS.md` and `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized all five revisioned specification documents to document revision
  279.

## [0.1.278] - 2026-09-04

### Fixed
- Completed the observable `each(block)` contract of the immutable sequence
  returned by `process.args()`.
- Reused the ordinary polymorphic callback-invocation domain of standard Core
  `each` operations rather than making Process-argument iteration Closure-only.
- Required callback callability validation before any argument callback executes.
- Defined exactly one callback per argument String in ascending logical index
  order, with no dependence on host-native argv layout or later host mutation.
- Defined successful iteration to ignore callback return values and return the
  Process-argument snapshot receiver itself, including for the empty snapshot.
- Defined callback error/non-local-control prefix behavior: completed lower-index
  callbacks are not rolled back and higher-index callbacks are not invoked.
- Kept the returned snapshot free to use a non-Array representation/object
  identity while preserving these sequence semantics.

### Changed
- Synchronized all revisioned specification documents to revision 278. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.277] - 2026-09-04

### Closed
- Closed dynamic error handlers across Actor boundaries as already determined by
  the existing task-local dynamic-handler and Actor value-transfer rules.
- Defined dynamic handler frames as execution-local control state that is never
  copied, inherited, serialized, proxied, forwarded, or remotely consulted
  across an Actor boundary.
- Clarified that sender handlers never become destination handlers, destination
  handlers never propagate back to the sender, and Actor bootstrap/replacement
  never inherits dynamic handler frames.
- Reaffirmed that a Future re-signals its stored Error only in the consumer's
  then-current dynamic handler context.
- Prohibited transport, placement, routing, same-host, or shared-memory
  optimizations from making handler propagation observable.
- Removed `Dynamic error handlers across Actor boundaries` from Open Design
  Topics.

### Changed
- Updated `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized all five revisioned specification documents to document revision
  277.

## [0.1.276] - 2026-09-04

### Closed
- Closed non-local return across Actor boundaries as impossible in Core v0.1.
- Defined Actor return homes as execution-domain-local control metadata that is
  never transferred, proxied, remapped, or remotely targeted.
- Clarified that destination Actor `^` may unwind only within destination-owned
  execution structure and can never return into sender/creator/bootstrap
  activations or another Actor's suspended continuation.
- Preserved ordinary Actor communication as value transfer rather than hidden
  continuation, reply, exception, or remote-stack control transfer.
- Clarified that Actor replacement cannot resurrect a return home from the dead
  incarnation.
- Mirrored the existing P principle that isolation boundaries do not preserve
  caller return-home authority.
- Removed `Non-local return across Actor boundaries` from Open Design Topics.

### Changed
- Updated `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_RUNTIME_SEMANTICS.md`, and
  `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized all five revisioned specification documents to document revision
  276.

## [0.1.275] - 2026-09-04

### Fixed
- Replaced implementation-selected `Environment.each(block)` enumeration order
  with one portable canonical order.
- Defined Environment entries to be enumerated by lexicographic comparison of
  their already-represented name Strings' Unicode scalar sequences.
- Defined first differing scalar by numeric scalar value and exact-prefix ties by
  shorter-name-first ordering.
- Kept the ordering local to Environment enumeration; no general String ordering
  operator is introduced.
- Prohibited Unicode normalization, locale collation, native case folding,
  host-environment ordering, hash-table layout, and materialization order from
  changing callback order.
- Defined callback error/non-local-control prefix behavior relative to that
  canonical order, while preserving the existing non-transactional callback
  semantics.

### Changed
- Synchronized all revisioned specification documents to revision 275. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.274] - 2026-09-04

### Fixed
- Removed the stale Grammar example
  `protectedClosure.handle(errorPrototype, handlerClosure)`, which contradicted
  the already-standardized Core handler API.
- Aligned `PROTOS_GRAMMAR.md` with Language and Runtime on the canonical ordinary
  message form `matchPrototype.handle(body, handler)`.
- Clarified that Grammar assigns no special handler semantics: receiver and
  arguments are parsed through ordinary message-send syntax, and dynamic handler
  behavior remains defined by Language/Runtime.
- Preserved the absence of dedicated `try`, `catch`, or `throw` syntax.

### Changed
- Synchronized all revisioned specification documents to revision 274.
  Only `PROTOS_GRAMMAR.md` gains normative clarification in this revision.

## [0.1.273] - 2026-09-04

### Closed
- Closed `Future ownership interaction with Actor lifecycle` as already fully
  determined by existing structured-concurrency and Actor-termination semantics.
- Added one normative ownership/lifecycle matrix covering Actor-local task-backed
  Futures, detached Actor-local tasks, Actor-originated non-task-backed Futures,
  Actor-originated P result Futures, and pure observation Futures.
- Clarified that detachment removes only activation ownership, never Actor-domain
  lifetime ownership.
- Clarified that surviving Future values do not keep terminated Actors alive or
  authorize continuation execution in their former mutable domains.
- Clarified that Actor replacement inherits no pending task/Future producer
  ownership from the terminated incarnation.
- Prohibited implementation-selected orphan-Future policies such as silent
  re-parenting, migration, abandonment, or continuation after Actor termination.
- Removed the corresponding item from Open Design Topics.

### Changed
- Updated `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized all five revisioned specification documents to document revision
  273.

## [0.1.272] - 2026-09-04

### Closed
- Closed Core v0.1 error signaling as strictly non-resumable: once
  `Error.signal()` begins, its signaling continuation is abandoned and a
  matching handler cannot return or inject a value back into that signal point.
- Defined one standard failure family rooted at `Error`; Core v0.1 introduces no
  separate standard `Exception` hierarchy or implicit continuable-exception
  category.
- Defined no privileged `resume`, `retry`, `restart`, `useValue`, or equivalent
  recovery operation in Core v0.1.
- Clarified that a handler's normal result is the result of the enclosing handler
  boundary, not a result returned to the abandoned signaling call.
- Reserved restart/recovery-style continuable handling for a future explicit
  semantic design without retroactively changing Core v0.1 `Error.signal()`.
- Prohibited host exception/continuation machinery from leaking implicit
  resumability into portable Protos semantics.

### Changed
- Synchronized all revisioned specification documents to revision 272.
  `PROTOS_LANGUAGE_SPEC.md` and `PROTOS_RUNTIME_SEMANTICS.md` gain normative
  clarification in this revision.

## [0.1.271] - 2026-09-04

### Closed
- Closed generic Future `race`/`select` out of Core v0.1.
- Rejected first-completion semantics based on implementation observation,
  callback, carrier, scheduler, polling, kernel, or I/O-backend timing.
- Declined to invent a global portable total order across independent Future
  terminal transitions solely to support a generic race primitive.
- Kept `Future.all(...)` as the deterministic standard multi-Future coordination
  operation.
- Required any future wait-any/select facility to define an independent semantic
  priority/event-ordering contract, including tie handling, loser lifecycle,
  cancellation, and resource-registration semantics.
- Removed `Select/race operations` from Open Design Topics.

### Changed
- Updated `PROTOS_LANGUAGE_SPEC.md` and `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized all five revisioned specification documents to document revision
  271.

## [0.1.270] - 2026-09-04

### Fixed
- Completed the standard `Environment.each(block)` callback contract.
- Reused the ordinary polymorphic callback-invocation domain of Core `each`
  operations rather than making Environment iteration Closure-only.
- Defined deterministic validation precedence: callback callability is validated
  first without invocation, complete Environment `(String, String)`
  representability second, and callbacks begin only after both succeed.
- Preserved the existing zero-callback guarantee when portable Environment
  representation validation fails.
- Defined successful `Environment.each(block)` to return the Environment receiver
  itself and to ignore callback return values for the operation result.
- Defined an empty valid Environment to invoke the callback zero times and still
  return its receiver.
- Removed implementation freedom to return `null`, the last callback result, or
  another implementation-selected value from successful Environment iteration.

### Changed
- Synchronized all revisioned specification documents to revision 270. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.269] - 2026-09-04

### Added / Closed
- Standardized `Future.all(futures...) -> Future` for deterministic waiting on
  multiple Futures.
- Defined `Future.all` as an ordinary message on the standard Future prototype,
  with no new syntax, Task kind, or wait-set object.
- Required synchronous left-to-right Future-argument validation.
- Defined the returned aggregate as a fresh non-task-backed observation Future
  that neither owns nor cancels its sources.
- Defined zero arguments to resolve immediately with a fresh empty standard
  Array and successful non-empty completion to preserve source argument order.
- Defined deterministic failure/cancellation selection by an ascending argument
  frontier rather than physical completion order.
- Defined aggregate cancellation as observation-only, with no upstream source
  cancellation.
- Defined repeated source-Future identity as valid and source registrations as
  removable/inert after aggregate terminalization to avoid unbounded retention.
- Kept first-completion `select`/`race` semantics as a separate open topic.
- Removed `Waiting on multiple Futures` from Open Design Topics.

### Changed
- Updated `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_RUNTIME_SEMANTICS.md`, and
  `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized all five revisioned specification documents to document revision
  269.

## [0.1.268] - 2026-09-04

### Fixed
- Reconciled the programmer-visible Language contract for the standard
  structural `Object` messages with the return values already fixed by Runtime.
- Defined successful `removeSlot(name)` to return the exact removed local-slot
  value, without copy, coercion, or delegated lookup.
- Defined successful structural `close()` and `freeze()` to return the original
  receiver, including their existing idempotent repeated-transition cases.
- Distinguished synchronous structural `Object.close()` from the I/O-domain
  `Closable.close()` operation, which follows its separate Future-returning
  resource-lifecycle contract when that behavior is selected by ordinary lookup.

### Changed
- Synchronized all revisioned specification documents to revision 268.
  Only `PROTOS_LANGUAGE_SPEC.md` gains normative clarification in this revision.

## [0.1.267] - 2026-09-04

### Fixed
- Removed the implementation-selected choice in `close()` between waiting for a
  previously accepted pending operation and forcing that operation to fail.
- Defined the close cutover in terms of the existing I/O commitment boundary:
  accepted-but-uncommitted operations fail with the closing-or-closed error,
  while already-committed operations retain their ordinary terminal aftermath.
- Required close to wait only for previously accepted operations whose semantic
  effects had already committed before the cutover.
- Kept closure-induced failure distinct from cancellation and preserved an
  independent cancellation outcome when cancellation wins before the cutover.
- Applied the same commitment-based classification to operations admitted before
  close through genuinely concurrent cross-Actor routing.
- Preserved rejection of operations ordered after the close cutover.
- Prevented backend/native scheduling policy from deciding whether reversible
  accepted work drains or is terminated by close.

### Changed
- Synchronized all revisioned specification documents to revision 267. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.266] - 2026-09-04

### Closed
- Closed Actor-local CPU-bound Future monopolization as an explicit consequence
  of cooperative C semantics.
- Defined ordinary Actor-local `closure.future()` execution segments as
  semantically non-preemptive with respect to other Protos work in the same
  Actor mutable domain.
- Defined no hidden suspension/preemption points at loop back-edges, calls,
  allocations, GC/JIT polls, timer ticks, host-thread quanta, or similar runtime
  machinery.
- Clarified that CPU-bound Actor-local work which never suspends may monopolize
  the Actor indefinitely and may delay queued tasks/messages and cancellation
  observation.
- Permitted physical carrier interruption/time slicing only when observationally
  equivalent to uninterrupted Protos-segment execution.
- Identified explicit `closure.parallel(...)` as the standard Core mechanism
  when CPU-bound work needs isolated parallel progress.
- Removed `Actor-local CPU-bound Future monopolization` from Open Design Topics.

### Changed
- Updated `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_RUNTIME_SEMANTICS.md`, and
  `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized all five revisioned specification documents to document revision
  266.

## [0.1.265] - 2026-09-04

### Fixed
- Reconciled the remaining generic `text + other` String example with the
  strict standard String concatenation rule by making the right operand
  explicitly another semantic String.
- Clarified that standard String `+` does not accept an arbitrary non-String
  object merely because an earlier illustrative variable name was generic.
- Removed the accidental implication that `StringBuilder` is a standardized
  Core v0.1 binding/prototype/protocol; efficient mutable text builders remain
  ordinary library abstractions unless explicitly standardized later.

### Changed
- Synchronized all revisioned specification documents to revision 265.
  Only `PROTOS_LANGUAGE_SPEC.md` gains normative clarification in this revision.

## [0.1.264] - 2026-09-04

### Closed
- Closed P scheduler policy, work stealing, and granularity as non-semantic Core
  implementation policy rather than an unresolved language/API surface.
- Defined no portable Core API or introspection for worker count, queue topology,
  work-stealing algorithm, victim selection, grain size, adaptive thresholds,
  inline/help-first/work-first policy, NUMA/locality preference, priority
  heuristics, carrier affinity, or scheduler cost model.
- Allowed runtimes to adapt those mechanisms dynamically to hardware/load while
  preserving all existing P snapshot, isolation, determinism, fairness,
  bounded-carrier nested-progress, cancellation, lifetime, locality, effect, and
  ByteRegion publication contracts.
- Clarified that scheduler-policy changes alone may not alter deterministic
  results/failures, create starvation, or introduce carrier-exhaustion deadlock.
- Clarified that Core promises neither equal CPU shares nor bounded latency,
  fixed parallelism, dedicated workers, work stealing, or a particular speedup.
- Kept implementation/admin scheduler diagnostics outside portable Core
  semantics.
- Removed the corresponding scheduling/work-stealing/granularity item from Open
  Design Topics.

### Changed
- Updated `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized all five revisioned specification documents to document revision
  264.

## [0.1.263] - 2026-09-04

### Fixed
- Defined standard `TextWriter.writeText("")` as an empty logical text payload
  with zero encoded-byte contribution and zero encoder-state transition.
- Prohibited ordinary empty text writes from acting as implicit encoder flush,
  finalization, reset, BOM emission, or stateful shift/reset control.
- Required host/native encoder calls whose empty-input behavior emits control
  bytes or resets state to be adapted so those effects do not leak through
  ordinary `writeText("")`.
- Kept empty text writes inside the normal TextWriter ordering/lifecycle domain:
  they cannot resolve successfully ahead of an earlier ordered operation that can
  still permanently fail the writer.
- Clarified that `writeLine("")` is not empty because its logical payload
  contains LF and is encoded normally.
- Kept encoder finalization attached to the explicit lifecycle operation that
  requires it, such as TextWriter close.

### Changed
- Synchronized all revisioned specification documents to revision 263. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.262] - 2026-09-04

### Added / Closed
- Standardized `Array.parallelSort(less, arguments...) -> Future` as the final
  Core v0.1 high-level parallel Array API in the §71 collection set.
- Defined a canonical stable logical merge-sort tree so physical algorithm,
  worker count, chunking, or work stealing cannot change observable ordering.
- Required comparator results to be exactly canonical `true`/`false`; other
  normal results fail with standard `InvalidComparatorResult`.
- Defined each merge decision by both `less(a,b,...)` and `less(b,a,...)`:
  one true selects that side, two false values are a stable tie, and two true
  values fail with standard `InvalidComparatorOrder`.
- Defined both comparator errors as delegating directly to `Error`.
- Defined deterministic failure precedence across recursive child sorts, merge
  positions, and forward/reverse comparator calls.
- Kept all comparator calls isolated P computations and all source/result values
  governed by ordinary P snapshot/transfer rules.
- Defined empty and singleton behavior without unnecessary comparator calls and
  prohibited partial sorted-Array publication on failure/cancellation.
- Removed parallel sort from §71 examples/Open Design Topics, closing the
  map/filter/search/reduce/sort/iteration Core collection-API pass.

### Changed
- Updated `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_RUNTIME_SEMANTICS.md`, and
  `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized all five revisioned specification documents to document revision
  262.

## [0.1.261] - 2026-09-04

### Closed
- Closed parallel iteration out of Core v0.1: no standard
  `Array.parallelEach(...)` operation is defined.
- Derived that boundary from the existing P effect model: isolated P work has no
  implicit Actor-local mutation, messaging, ambient I/O, runtime authority, or
  other external-effect channel merely because results are discarded.
- Kept `Array.parallelMap(...)` as the composable standard per-element parallel
  operation when callers need independent computation and may ignore its final
  result if appropriate.
- Prevented an `each`-shaped API from implicitly weakening P transfer/effect
  restrictions or legitimizing hidden shared/global side effects.
- Allowed implementations to eliminate unused result materialization only as an
  observationally invisible optimization.
- Removed parallel iteration from §71.6 examples and narrowed the remaining
  parallel collection API topic to parallel sort only.

### Changed
- Updated `PROTOS_LANGUAGE_SPEC.md` and `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized all five revisioned specification documents to document revision
  261.

## [0.1.260] - 2026-09-04

### Fixed
- Defined the standard String-family binary `+` behavior as exact String
  concatenation, making existing examples such as `("hel" + "lo") === "hello"`
  normative rather than illustrative-but-undefined.
- Required both operands to be semantic String values and prohibited implicit
  conversion of numbers, booleans, null, prototypes, or arbitrary objects.
- Defined the result as exact receiver scalar sequence followed by right-operand
  scalar sequence, with no Unicode normalization, locale processing, encoding,
  decoding, callback, hash/equality dispatch, or hidden suspension.
- Preserved String immutability, value identity, ordinary operator/message
  overriding, and the semantic-family receiver-domain rule.

### Changed
- Synchronized all revisioned specification documents to revision 260.
  `PROTOS_LANGUAGE_SPEC.md` and `PROTOS_RUNTIME_SEMANTICS.md` gain normative
  semantic content in this revision.

## [0.1.259] - 2026-09-04

### Fixed
- Defined a preceding `ByteWritable.write` whose cancellation succeeds as a
  clean zero-contribution outcome for `shutdownWrite()`.
- Required write shutdown to wait for that preceding write's terminal cancelled
  state without failing merely because cancellation occurred.
- Applied the same rule to a cross-Actor write that routing/admission placed
  before the shutdown cutover and that is then successfully cancelled.
- Preserved the distinction between cancellation and write failure: a failed
  preceding write still makes shutdown fail even when its hidden committed prefix
  happens to be zero.
- Clarified that a cancellation request that loses after write commitment does
  not erase that write; shutdown composes with the write's actual later
  success/failure aftermath.

### Changed
- Synchronized all revisioned specification documents to revision 259. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.258] - 2026-09-04

### Added / Closed
- Standardized `Array.parallelReduce(reducer, arguments...) -> Future`.
- Defined empty input as resolved `null`, singleton input as the isolated
  snapshot/transfer of its sole element without invoking the reducer, and
  non-empty multi-element input through isolated P reducer nodes.
- Defined one canonical adjacent-pair reduction tree with odd final values
  carried unchanged to the next round.
- Made the canonical tree independent of worker count, chunking, scheduling, and
  work stealing so non-associative reducers remain deterministic.
- Defined each reducer invocation as its own P isolation domain over left/right
  operands plus the reducer and explicit arguments.
- Defined logical round barriers: a later round exists only if all combine nodes
  in the preceding round succeed.
- Defined multiple failures within one round by the leftmost failing pair.
- Allowed physical pipelining/speculation only when it cannot alter canonical
  parenthesization, failure selection, publication, or other observable state.
- Required intermediate/final reducer values to cross according to ordinary P
  result/value rules and prohibited partial reduction publication.
- Narrowed the remaining parallel collection API topic to sort/iteration.

### Changed
- Updated `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_RUNTIME_SEMANTICS.md`, and
  `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized all five revisioned specification documents to document revision
  258.

## [0.1.257] - 2026-09-04

### Fixed
- Made ordinary `ByteReadable.read(maxBytes)` preserve logical byte/error order
  independently of implementation read-ahead depth.
- Required already-returnable bytes that logically precede a later EOF or I/O
  failure to be delivered before that terminal condition can become a read
  outcome.
- Kept successful read chunk boundaries implementation-selectable while requiring
  all bytes preceding a later error to remain ahead of that error across
  successive ordered reads.
- Defined a later read-ahead failure as preserved/deferred receiver error state,
  rather than allowing it to fail the current read and rebuffer earlier bytes.
- Required the deferred failure to be reported exactly once when it reaches the
  head of the logical input/error order, preserving the existing no-auto-replay
  rule after actual reporting.
- Prevented buffering, native batching, or prefetch from changing observable
  `bytes -> error` into `error -> bytes` solely because the implementation
  discovered the later error earlier internally.

### Changed
- Synchronized all revisioned specification documents to revision 257. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.256] - 2026-09-04

### Added / Closed
- Standardized `Array.parallelFindIndex(predicate, arguments...) -> Future`.
- Chose an Integer index or `null` result so absence remains unambiguous even
  when an Array element is itself `null`.
- Reused the existing parallelMap/parallelFilter per-index P input snapshot,
  isolation, polymorphic predicate-callability, and strict Boolean-result rules.
- Defined deterministic ascending-index search semantics independent of physical
  execution order: `false` continues, while `true` and failure are decisive.
- Required the first decisive logical index to determine the terminal outcome
  only after every lower index is known to be `false`.
- Defined lower-index failure to beat a higher-index match while failures after
  the first established match are irrelevant to the search result.
- Allowed higher-index work to be pruned/cancelled only after it cannot affect
  the specified terminal outcome.
- Defined empty input to resolve with `null` without crossing a P boundary.
- Narrowed the remaining parallel collection API topic to
  reduce/sort/iteration.

### Changed
- Updated `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_RUNTIME_SEMANTICS.md`, and
  `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized all five revisioned specification documents to document revision
  256.

## [0.1.255] - 2026-09-04

### Fixed
- Removed the accidental implication that `String.graphemes()` and
  `String.codePoints()` are standardized Core v0.1 protocols.
- Clarified that Core String's normative grapheme access remains the existing
  `size` / `at` contract rather than an additional collection/view API.
- Left libraries free to provide ordinary `graphemes` / `codePoints`
  conveniences and left any future lower-level text-view protocol to a later
  explicit standardization decision.
- Preserved Encoding-object one-shot conversion (`UTF8.encode(text)`) as the
  standardized text/byte boundary.

### Changed
- Synchronized all revisioned specification documents to revision 255.
  Only `PROTOS_LANGUAGE_SPEC.md` gains normative clarification in this revision.

## [0.1.254] - 2026-09-04

### Fixed
- Defined the native/bootstrap Environment represented by `process.environment()`
  as one stable Process-lifetime snapshot, not a live host re-query.
- Required repeated successful acquisitions to preserve the same native-name
  domain, entries, identity relationships, and standardized lookup/enumeration
  semantics while leaving returned-object identity non-normative.
- Made Environment acquisition validity a stable bootstrap outcome, including
  duplicate-equivalent native-name rejection.
- Prevented one acquisition from failing and a later one succeeding merely
  because the host environment mutated, enumeration order changed, or a
  different first/last-winner policy was used.
- Prevented later host/native environment mutation from changing an already
  established successful standardized Environment.
- Preserved lazy materialization and per-operation String validation when they
  remain observationally equivalent to one captured bootstrap snapshot.
- Kept future live/raw native-environment inspection outside the standardized
  Process Environment.

### Changed
- Synchronized all revisioned specification documents to revision 254. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.253] - 2026-09-04

### Added / Closed
- Standardized `Array.parallelFilter(predicate, arguments...) -> Future`.
- Reused the `parallelMap` per-index P-isolation model: one logical isolated
  predicate invocation per source index, with all non-empty child inputs
  validated/snapshotted before any child becomes eligible.
- Kept predicate callability polymorphic rather than Closure-only.
- Required predicate results to be exactly canonical `true` or `false`, matching
  Protos' absence of language-wide truthiness; other normal results fail the
  corresponding index with standard `InvalidPredicateResult`.
- Defined `InvalidPredicateResult` as delegating directly to `Error`.
- Preserved stable ascending source-index order in the fresh filtered result
  independently of physical execution/completion order.
- Required selected values to cross back under ordinary P result rules while
  allowing rejected values to avoid unnecessary result transfer.
- Defined deterministic multiple-failure selection by lowest failing source
  index and prohibited partial result publication on failure/cancellation.
- Left batching, fusion, chunking, SIMD, work stealing, worker count, and actual
  overlap as unobservable implementation choices.
- Narrowed the remaining parallel collection API topic to
  reduce/search/sort/iteration.

### Changed
- Updated `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_RUNTIME_SEMANTICS.md`, and
  `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized all five revisioned specification documents to document revision
  253.

## [0.1.252] - 2026-09-04

### Fixed
- Defined `process.args()` as one stable Process-bootstrap argument snapshot for
  the entire Protos Process lifetime.
- Required repeated successful calls to observe the same argument count, order,
  and String values while leaving returned-object physical identity non-normative.
- Made portable argument representability a stable bootstrap outcome: an invalid
  native argument set cannot fail one call and later succeed because host argv
  storage or conversion behavior changed.
- Prevented later host-native argv/process-title mutation from changing an
  already-established successful Protos argument snapshot.
- Allowed eager or lazy validation/materialization only when every call remains
  observationally equivalent to one bootstrap-time logical snapshot.
- Distinguished standardized application arguments from future live/best-effort
  host process-inspection facilities.

### Changed
- Synchronized all revisioned specification documents to revision 252. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.251] - 2026-09-04

### Fixed
- Repaired the incomplete rev247 encoding-dispatch cleanup: stale
  `text.encode(UTF8)` / `bytes.decode(UTF8)` examples remained in normative
  Language/Grammar text despite the changelog claiming they had been removed.
- Replaced every remaining stale concrete example with
  `UTF8.encode(text)` / `UTF8.decode(bytes)`.
- Added explicit normative clarification that the standard one-shot receiver is
  the `Encoding` object and Core v0.1 does not additionally require reciprocal
  String/Bytes convenience messages.
- Preserved ordinary syntax for user/library-defined messages with those names
  and preserved Encoding's existing availability boundary outside the mandatory
  Core prelude.

### Changed
- Synchronized all revisioned specification documents to revision 251.
  `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`, and `PROTOS_IO_MODEL.md` gain
  normative repair/clarification in this revision.

## [0.1.250] - 2026-09-04

### Fixed
- Reconciled §71.5 with §71.5A so generic writable Array/object partitioning is described as excluded from Core v0.1 rather than open.
- Reconciled §71.9 with §71.9A so remote execution of Core P is described as excluded rather than open or implementation-selectable.
- Reconciled §71.20 with §71.5A so byte-region closure no longer says generic writable Array/object partitioning remains open.
- Made no new concurrency design decision; this revision only removes stale
  open-state wording that contradicted already-closed §71.5A and §71.9A
  semantics.

### Changed
- Updated `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 250.

## [0.1.249] - 2026-09-04

### Fixed
- Closed the flush-frontier race for writes that are logically before a flush but
  have not yet committed output when the flush begins.
- Required successful flush to wait until every preceding-frontier write has a
  terminal semantic aftermath and to propagate exactly the contribution left by
  that outcome: complete sequence on success, permitted prefix on failure, or
  zero bytes on successful cancellation.
- Prohibited a flush from resolving successfully and then allowing an earlier
  frontier write to commit new output behind the completed frontier.
- Applied the same rule to writes admitted before a flush under cross-Actor
  routing/admission ordering.
- Kept pipelining and overlapping backend work implementation-defined while
  making terminal contribution knowledge, rather than native call timing, the
  semantic completion condition.
- Clarified that an earlier write failure does not mechanically force flush
  failure when the receiver remains usable and can still establish the complete
  propagation contract for the exact committed prefix.

### Changed
- Synchronized all revisioned specification documents to revision 249. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.248] - 2026-09-04

### Added / Closed
- Standardized `Array.parallelMap(worker, arguments...) -> Future` as the first
  Core high-level parallel collection operation.
- Kept callback eligibility polymorphic like `Array.each`: the worker need only
  be ordinarily invokable, not specifically a Closure, while all actually used
  worker/element/argument graphs must satisfy P transfer/projection rules.
- Defined one logical child P isolation domain per source index, with no shared
  mutable Protos identity between worker invocations.
- Required all non-empty child inputs to be validated/snapshotted before the
  successful call returns and before any child becomes eligible; invalid P input
  fails synchronously with `NonParallelValue`.
- Defined empty input to create no P work and require no P-transferability for
  otherwise-unused worker/extra arguments, while still validating ordinary
  worker callability.
- Preserved source-index order in the fresh result Array independently of
  physical worker execution/completion order.
- Defined deterministic multiple-failure selection by the lowest failing source
  index and prohibited scheduler timing from selecting the reported failure.
- Required cancellation/failure to publish no partial result Array.
- Left worker count, chunking, batching, fusion, SIMD, work stealing, and actual
  simultaneous execution as unobservable implementation choices.
- Narrowed the remaining collection-API open item to
  filter/reduce/search/sort/iteration.

### Changed
- Updated `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_RUNTIME_SEMANTICS.md`, and
  `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 248.

## [0.1.247] - 2026-09-04

### Fixed
- Reconciled Language/Grammar encoding examples with the normative I/O
  one-shot Encoding API.
- Made the Encoding object the single canonical standard dispatch receiver:
  `encoding.encode(text)` and `encoding.decode(bytes)`.
- Replaced misleading `text.encode(UTF8)` / `bytes.decode(UTF8)` examples with
  `UTF8.encode(text)` / `UTF8.decode(bytes)`.
- Explicitly declined to standardize reciprocal String/Bytes convenience
  messages in Core v0.1; libraries remain free to provide ordinary conveniences.
- Preserved ordinary message syntax and the existing rule that Encoding objects
  are outside the required Core prelude.

### Changed
- Synchronized all revisioned specification documents to revision 247.
  `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`, and `PROTOS_IO_MODEL.md` gain
  normative clarification in this revision.

## [0.1.246] - 2026-09-04

### Fixed
- Defined `ByteSeekable.position()` and `ByteSized.size()` as observation-only
  Future-returning I/O queries with explicit cancellation boundaries.
- Allowed cancellation to win until the query's result or failure outcome
  commits, since these queries have no irreversible Protos sequence effect.
- Defined successful cancellation to contribute no position/size/content state
  change and no additional observation/frontier in the sequence-state domain.
- Required residual backend/native query work that cannot be physically stopped
  to remain under implementation/runtime custody and to discard its eventual
  value/error rather than resurrecting a cancelled Future.
- Clarified that cancelled queries reach an ordinary terminal Future state, so
  later ordered operations compose with the actual aftermath of state-changing
  predecessors rather than an implementation-selected query residue.
- Prevented post-cancellation backend query completion from mutating sequence
  state or retroactively changing later Protos observations.

### Changed
- Synchronized all revisioned specification documents to revision 246. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.245] - 2026-09-04

### Closed
- Closed generic writable Array/object partitioning out of Core v0.1.
- Defined non-overlapping container indexes, slots, physical ranges, or storage
  regions as insufficient proof of disjoint mutable authority over reachable
  Protos object graphs.
- Kept `Bytes`/`ByteRegion` as the sole standardized Core writable-partition
  facility because its authority is explicitly bounded to byte-indexed state.
- Defined no Core `Array.parallelRange(...)`, generic object/graph partition
  capability, implementation-dependent alias-proof API, or borrow/ownership
  annotation system.
- Preserved ordinary parallel algorithms over Arrays/objects through P
  snapshot/value semantics, fresh-result construction, read-only inputs, and
  semantics-preserving internal optimizations.
- Required any future broader writable partition facility to provide a portable
  language/runtime proof of disjoint mutable authority rather than depending on
  implementation-selected heap/alias analysis.
- Removed the now-closed generic writable Array/object partitioning item from
  Open Design Topics.

### Changed
- Updated `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 245.

## [0.1.244] - 2026-09-04

### Closed
- Closed `Task` as non-observable Core v0.1 execution machinery rather than a
  second public concurrency identity.
- Kept `Future` as the public eventual-result/coordination abstraction regardless
  of whether its producer is task-backed, I/O-backed, communication-backed,
  continuation-backed, or isolated P work.
- Defined no Core `Task` prototype, constructor, current-task intrinsic, task
  identity, public parent/child task graph, scheduler handle, priority/affinity
  API, task enumeration, or Future-to-task conversion.
- Clarified that structured ownership, detachment, cancellation, waiting,
  failure propagation, and Actor/P lifetime semantics do not require exposing
  runtime task records.
- Required task/fiber/continuation splitting, fusion, inlining, migration, and
  carrier assignment to remain unobservable when Future/domain semantics are
  preserved.
- Removed the now-closed `Whether Task should become observable` item from Open
  Design Topics.

### Changed
- Updated `docs/design/CONCURRENCY_DESIGN.md` and, when its current stable semantic
  anchor is present, `PROTOS_RUNTIME_SEMANTICS.md`.
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 244.

## [0.1.243] - 2026-09-04

### Fixed
- Reconciled the normative Bytes model across Language, Runtime, and I/O:
  `Bytes()`, `each`, `add`, and `removeAt` are no longer required by I/O while
  semantically undefined elsewhere.
- Defined standardized `Bytes()` as a zero-argument factory for a fresh open
  empty Bytes value wherever that standardized factory is exposed, without
  making `Bytes` a mandatory Core-prelude binding.
- Kept `atPut` replacement-only while defining `add` and `removeAt` as the only
  standard Bytes operations in this set that change sequence length.
- Defined exact octet validation, `add` return value, `removeAt` shifting and
  removed-value result, and failure-before-mutation behavior.
- Defined `Bytes.each` through ordinary polymorphic invocation over one
  ascending-index snapshot of octet values.
- Defined open/closed/frozen consequences: closed Bytes allow existing-index
  replacement but reject resize; frozen Bytes reject all standard mutation.
- Required I/O's Bytes section to defer detailed sequence semantics to the
  Language/Runtime contracts rather than maintaining a competing partial model.

### Changed
- Synchronized all revisioned specification documents to revision 243.
  `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_RUNTIME_SEMANTICS.md`, and
  `PROTOS_IO_MODEL.md` gain normative semantic content in this revision.

## [0.1.242] - 2026-09-04

### Fixed
- Defined remote/end-of-stream EOF on a standard connected `Socket` input
  direction as permanent for that logical byte stream.
- Prohibited temporary-EOF treatment, implicit reconnect/replacement, or later
  backend input resurrection after a Socket read has committed remote EOF.
- Kept remote EOF directional: it does not invoke read shutdown, write shutdown,
  or whole-resource close and does not terminate otherwise-usable output.
- Distinguished remote EOF from local `shutdownRead()` while preserving their
  common later-read `null` behavior before whole-resource close.
- Preserved Closable precedence so reads issued after the whole-resource close
  cutover fail under the close lifecycle rather than being rewritten as EOF.
- Required buffered bytes preceding remote EOF to be delivered before the EOF
  result and allowed BufferedReader to memoize this source-guaranteed EOF.

### Changed
- Synchronized all revisioned specification documents to revision 242. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.241] - 2026-09-04

### Closed
- Closed Core isolated parallel execution as strictly Protos-Process-local.
- Defined `Closure.parallel(...)` not to imply remote placement, Node/Cluster
  routing, code shipment, network transport, remote failure detection, retry, or
  distributed result recovery.
- Clarified that P-transferability is not a general network-serialization
  contract and Closure projection is not a remote code-availability/versioning
  contract.
- Allowed arbitrary CPU/core/thread/NUMA/accelerator realization only while the
  work remains inside the same Protos Process semantic execution domain.
- Required any future remote-compute facility to be explicit and to define its
  own placement, code identity/availability, serialization, transport,
  authentication, cancellation, retry/uncertainty, failure, and lifecycle
  semantics rather than silently extending `Closure.parallel(...)`.
- Removed the now-closed remote isolated-parallel-execution item from Open Design
  Topics.

### Changed
- Updated `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 241.

## [0.1.240] - 2026-09-04

### Closed
- Closed the interaction between isolated parallel work and SIMD/vectorization
  without introducing a new public SIMD/vector value universe.
- Defined SIMD/vectorization as a permitted physical optimization only when it
  is observationally equivalent to the already-specified scalar/logical Protos
  execution.
- Required vectorized execution to preserve result values, identity/aliasing,
  evaluation and mutation order, dispatch/invocation behavior, failure
  precedence, explicit suspension/cancellation boundaries, P
  isolation/publication, and fairness.
- Prohibited SIMD-driven reassociation from changing the logical result of an
  observably non-associative reduction unless the invoked API explicitly defines
  different semantics.
- Left vector width, target instructions, masking, alignment strategy, cost
  model, and scalar fallback as implementation details.
- Removed the now-closed SIMD/vectorization interaction item from Open Design
  Topics.

### Changed
- Updated `docs/design/CONCURRENCY_DESIGN.md` and, where the current structure permits
  a stable anchor, `PROTOS_RUNTIME_SEMANTICS.md`.
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 240.

## [0.1.239] - 2026-09-04

### Fixed
- Defined semantic argument-validation failures of standardized Future-returning
  I/O operations to be reported through a failed returned Future after successful
  operation dispatch, rather than through an implementation-selected synchronous
  failure channel.
- Covered invalid read bounds, non-Bytes writes, seek/truncate numeric arguments,
  bounded line reads, and standard text-write payload validation through the same
  general rule.
- Required invalid requests to fail before operation-attributable I/O effects,
  position/content/lifecycle changes, frontiers, or external-authority exercise.
- Allowed already-failed Futures when validation depends only on already-evaluated
  Protos argument values.
- Kept ordinary lookup/receiver-domain/call-arity failures outside this rule when
  they prevent the standardized I/O operation from being dispatched at all.
- Kept explicitly synchronous APIs such as one-shot Encoding operations and
  Process bootstrap accessors under their existing non-Future contracts.

### Changed
- Synchronized all revisioned specification documents to revision 239. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.238] - 2026-09-04

### Fixed / Closed
- Restored monotonic source order for §71.13 through §71.20 without changing their already-closed semantics.
- Defined deterministic synchronous failure precedence for standard `parallelRange` validation.
- Closed weak fairness for runnable isolated P work so later submissions or scheduler policy cannot starve it indefinitely.
- Required nested-P descendant progress without needing an additional unused OS thread/carrier.

### Changed
- Updated normative P semantics from the repository state observed by this
  application attempt, without assuming a particular starting revision or SHA.
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 238.

## [0.1.237] - 2026-09-04

### Fixed
- Defined standard `Map.size` and `IdentityMap.size` as the exact semantic
  Integer number of currently stored associations.
- Defined insertion of a new association to increase size by one, value
  replacement to preserve size, and successful removal to decrease size by one.
- Made size count stored entries rather than current equality classes, so
  mutable keys that later become equal still count as separate stored
  associations.
- Prohibited hash-table capacity, buckets, tombstones, load factor, host width,
  overflow, or representation details from affecting the result.
- Defined size observation to perform no key hashing/equality, identity hashing,
  iteration snapshot, callback, search, or mutation.
- Preserved the existing keyed receiver-domain rule and open/closed/frozen
  read-only availability.

### Changed
- Synchronized all revisioned specification documents to revision 237.
  Only `PROTOS_LANGUAGE_SPEC.md` and `PROTOS_RUNTIME_SEMANTICS.md` gain
  normative semantic content in this revision.

## [0.1.236] - 2026-09-04

### Fixed
- Defined standard `TextReader.readText()` as a progress-oriented chunk read:
  once non-empty decoded text is returnable without additional source/backend
  progress, the operation cannot remain pending solely to build a larger chunk.
- Kept chunk boundaries implementation-selectable while preventing buffered
  decoded text from being hidden behind an implementation-preferred chunk size.
- Distinguished incomplete encoded characters and state-only decoder progress,
  which may still require more input before any String is returnable.
- Defined a later I/O/decoding error discovered by read-ahead not to leap ahead
  of valid decoded text that logically precedes it.
- Required all valid decoded text preceding such an error to be exposed, in any
  permitted finite chunking, before the error becomes a text-read outcome.
- Reconciled permanent TextReader failure so deferred read-ahead errors poison
  the text-reading side only when they become the committed failure outcome of
  the applicable ordered text-reading operation.
- Kept `readLine()` complete-line framing and its existing error precedence
  unchanged.

### Changed
- Synchronized all revisioned specification documents to revision 236. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.235] - 2026-09-04

### Fixed
- Defined standard `Map()` and `IdentityMap()` as zero-argument ordinary
  invocation factories for fresh open empty keyed objects.
- Kept normal Map and IdentityMap construction distinct so factory inheritance
  preserves the corresponding key-matching semantics.
- Defined non-empty factory argument vectors to fail with ordinary
  argument-count semantics after argument evaluation and before Map allocation.
- Deliberately avoided constructor consumption of pairs, Arrays, Maps, `each`,
  iterators, or other implicit entry sources while Core has no general iterable
  protocol.
- Composed inherited factory behavior with prototypes by using the actual
  invocation receiver as the new Map's delegation parent without granting the
  prototype keyed state.
- Defined factory creation to perform no `init`, key hashing/equality, identity
  hashing, callbacks, iteration, or entry insertion.

### Changed
- Synchronized all revisioned specification documents to revision 235.
  Only `PROTOS_LANGUAGE_SPEC.md` and `PROTOS_RUNTIME_SEMANTICS.md` gain
  normative semantic content in this revision.

## [0.1.234] - 2026-09-04

### Fixed
- Defined `ByteReadable.read(maxBytes)` `maxBytes` as a maximum result size, not
  an exact-fill or minimum-fill requirement.
- Required an ordered ordinary read to stop waiting once at least one next
  logical octet is returnable without additional source/backend progress.
- Prevented implementation buffering/read-ahead policy from withholding already
  unread logical input merely to construct a larger preferred chunk.
- Preserved backend/native waiting freedom until useful input, EOF, or failure is
  established, without introducing polling or a portable `available()` API.
- Specialized standard File reads so a position before current EOF returns a
  non-empty prefix of currently readable file bytes rather than waiting to fill
  `maxBytes` or for later file growth.
- Kept exact-fill/delimiter behavior available only to stronger protocols that
  explicitly define it.

### Changed
- Synchronized all revisioned specification documents to revision 234. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.233] - 2026-09-04

### Fixed
- Defined captured standard open-configuration validation as a preflight semantic
  step before filesystem namespace resolution, acquisition, creation, truncation,
  or other target/backend I/O.
- Required a standard-invalid configuration to fail through the open Future with
  zero target filesystem effect and without exercising filesystem authority
  against the supplied Path.
- Prevented invalid combinations such as append+truncate or read-only truncate
  from being discovered only after a target has been looked up or modified.
- Defined invalid-configuration precedence over target-dependent outcomes for an
  otherwise valid Path value.
- Preserved the asynchronous `filesystem.open` API by permitting an already-
  failed Future rather than introducing a special synchronous exception path.
- Distinguished globally invalid configuration tuples from semantically valid
  configurations that a selected backend/resource may later be unable to support.

### Changed
- Synchronized all revisioned specification documents to revision 233. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.232] - 2026-09-04

### Fixed
- Defined standard `Bytes.size` as the exact semantic Integer number of current
  octets in receiver-owned Bytes state.
- Made `Bytes.size` use the same logical octet length that defines valid
  `Bytes.at` / `atPut` indexes.
- Prohibited host buffer width, capacity, overflow, wrapping, saturation,
  signed-byte representation, or storage layout from changing the result.
- Defined `size` as read-only and available for open, closed, and frozen Bytes
  without decoding contents or invoking user behavior.
- Preserved the existing Bytes receiver-domain rule and the existing I/O rule
  that `Bytes` need not be a mandatory Core-prelude binding.

### Changed
- Synchronized all revisioned specification documents to revision 232.
  Only `PROTOS_LANGUAGE_SPEC.md` and `PROTOS_RUNTIME_SEMANTICS.md` gain
  normative semantic content in this revision.

## [0.1.231] - 2026-09-04

### Fixed
- Defined standard BufferedReader to preserve the underlying ByteReadable
  source's EOF permanence/resumability instead of making one observed EOF
  permanently sticky merely because buffering is present.
- Required a later BufferedReader read, after a non-permanent source EOF and with
  no earlier buffered bytes, to re-evaluate the source under ordinary ordered
  ByteReadable semantics.
- Preserved pull semantics: a read that reaches current EOF completes `null`
  rather than waiting, polling, or subscribing for hypothetical future data.
- Required unread buffered bytes to remain ahead of bytes made readable by a
  later source-state change and prohibited stale EOF markers from bypassing them.
- Allowed permanent-EOF memoization only when the source contract itself makes
  EOF permanent.
- Kept TextReader's explicitly stronger permanent text-EOF lifecycle unchanged.

### Changed
- Synchronized all revisioned specification documents to revision 231. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.230] - 2026-09-04

### Fixed
- Defined the standard `Array` invocation behavior as an ordinary polymorphic
  factory for fresh open standard Arrays.
- Defined supplied positional arguments as the new Array's exact elements in
  order, with no numeric-length overload: `Array(3)` is one element `3`.
- Defined each call, including `Array()`, to create a fresh Array identity with
  shallow element references.
- Composed inherited Array-factory behavior with prototypes by making the actual
  invocation receiver the new Array's delegation parent without granting that
  receiver indexed state.
- Kept indexed-state receiver-domain rules intact: ordinary Array methods do not
  confer state; the factory creates a separate new state-owning object.
- Defined the standard Array factory not to send `init`, run callbacks, perform
  conversions, or introduce hidden suspension after ordinary argument
  evaluation.

### Changed
- Synchronized all revisioned specification documents to revision 230.
  Only `PROTOS_LANGUAGE_SPEC.md` and `PROTOS_RUNTIME_SEMANTICS.md` gain
  normative semantic content in this revision.

## [0.1.229] - 2026-09-04

### Closed
- Standardized Core exclusive mutable partitioning for byte-indexed P-local state
  through `Bytes.parallelRange(start, length, worker, arguments...)`.
- Added fixed-size P-local `ByteRegion` capabilities with local zero-based byte
  indexing and recursive `parallelRange`.
- Restricted region creation to P so Actor source values remain unchanged.
- Defined half-open range validation, zero-length behavior, reservation lifetime,
  synchronous overlap rejection, and parent-access rejection without blocking.
- Allowed unrelated unreserved byte access to proceed while disjoint children run.
- Defined atomic successful publication of exactly the reserved bytes only after
  normal child completion and successful child-result transfer.
- Defined failure/cancellation/result-transfer failure to release authority without
  publishing partial region mutation.
- Defined `ByteRegion` as scoped P-local authority that cannot escape by ordinary
  P/Actor transfer or serialization.
- Deliberately left generic writable Array/object partitioning open because
  disjoint indexes do not establish disjoint reachable mutable graphs.
- Replaced the broad partition API/overlap open items with that narrower unresolved
  generic-partition question.

### Added
- Added `ParallelRegionOverlap`, `ParallelRegionInUse`, and
  `ParallelRegionOutsideP`, each delegating directly to `Error`.

### Changed
- Updated `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_RUNTIME_SEMANTICS.md`,
  `docs/design/CONCURRENCY_DESIGN.md`, and `PROTOS_IO_MODEL.md` for the byte-region
  authority boundary.
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 229.

## [0.1.228] - 2026-09-04

### Fixed
- Defined every resource newly created by standard `create` or `createNew` to
  begin as an empty logical byte sequence with size zero.
- Made the empty initial content part of the creation semantic result rather than
  an implementation/backend-selected default.
- Defined `preserve` and `truncate` consistently for newly created resources:
  there is no prior content to preserve and truncate introduces no second
  destructive effect when creation already established size zero.
- Allowed sparse, lazy, remote, virtual, copy-on-write, and other physical
  representations when their Protos-visible initial byte sequence is empty.
- Clarified that later independently authorized writes may change the resource
  after creation commitment without changing what the creation itself established.
- Required backends with non-empty/template/residual creation defaults to emulate
  empty creation or reject standard create/createNew semantics.

### Changed
- Synchronized all revisioned specification documents to revision 228. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.227] - 2026-09-04

### Fixed
- Defined truncate-on-open as an indivisible Protos content effect: before its
  commitment the open contributes no truncation; at commitment it establishes
  complete logical size zero.
- Prohibited failed or cancelled opens from exposing an intermediate nonzero
  truncation attributable to implementation/backend stepwise resizing.
- Required tentative truncate work to be restored, virtualized, deferred, or
  otherwise hidden when the complete zero-size effect has not committed.
- Preserved the existing rule that a later failure after committed truncation
  does not restore discarded content.
- Defined already-empty and newly-created-empty resources so `truncate` does not
  invent a second destructive commitment when no content change is needed.
- Kept independently authorized resource changes outside this operation-specific
  failure-atomicity and avoided requiring the returned File to expose Truncatable.

### Changed
- Synchronized all revisioned specification documents to revision 227. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.226] - 2026-09-04

### Fixed
- Defined standard `Map.each` and `IdentityMap.each` callbacks through the same
  ordinary polymorphic invocation domain already used by `Array.each`.
- Allowed user-defined invokable objects as callbacks instead of leaving
  callback eligibility implementation-selected or accidentally Closure-only.
- Defined receiver validation before callback-callability validation and
  callability validation before association-snapshot establishment.
- Kept callback arity checking at the actual two-argument invocation rather
  than inventing a separate preflight signature mechanism.
- Required non-invokable callbacks to fail before snapshot capture and without
  key search, hashing, equality, identity hashing, or keyed-state mutation.

### Changed
- Synchronized all revisioned specification documents to revision 226.
  Only `PROTOS_LANGUAGE_SPEC.md` and `PROTOS_RUNTIME_SEMANTICS.md` gain
  normative semantic content in this revision.

## [0.1.225] - 2026-09-04

### Closed
- Closed immutable physical-sharing eligibility/representation for P as an
  implementation optimization rather than a new public Protos capability.
- Defined physical sharing as permitted only when every Protos observation is
  equivalent to the required isolated logical values.
- Clarified that logically mutable values may still use immutable backing,
  copy-on-write, remapping, exclusivity-proven reuse, or equivalent invisible
  optimizations.
- Clarified that shallow `freeze()` neither grants transitive P shareability nor
  is required for safe implementation-level immutable sharing.
- Prohibited public/runtime-observable shareability predicates, pinning,
  zero-copy state, copy-on-write state, or backing-allocation identity in Core
  v0.1.
- Required an implementation to fall back to another semantics-preserving
  representation when a sharing optimization is unavailable rather than fail an
  otherwise P-transferable value.
- Removed the now-closed immutable-sharing eligibility/representation item from
  Open Design Topics.
- Repaired remaining Actor-only wording in standard-prelude sharing consequences
  so the already-closed Actor/P isolation rule is stated consistently.

### Changed
- Updated `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_RUNTIME_SEMANTICS.md`, and
  `docs/design/CONCURRENCY_DESIGN.md` with the closed physical-sharing boundary.
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 225.

## [0.1.224] - 2026-09-04

### Fixed
- Defined successful reads on a receiver whose ByteReadable and ByteSeekable
  capabilities share one logical sequence position to advance that position by
  exactly the number of octets actually returned.
- Defined EOF to advance the shared logical position by zero.
- Reaffirmed successful cancellation and ordinary read failure as zero-position-
  advance outcomes even when implementation read-ahead moved a native cursor.
- Prevented `maxBytes`, native read size, prefetch size, or hidden buffering from
  changing the observable post-read logical position.
- Kept independently authorized mutable-resource changes under their existing
  visibility semantics while making each successful read's position aftermath
  deterministic.

### Changed
- Synchronized all revisioned specification documents to revision 224. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.223] - 2026-09-04

### Fixed
- Defined Core call spread to accept standard Array indexed state only rather
  than leaving the spreadable-object domain implementation-selected.
- Defined each spread argument to evaluate once at its ordinary left-to-right
  position and contribute a shallow ascending-index snapshot of current Array
  element references.
- Defined later argument effects and later source-Array mutation not to rewrite
  elements already contributed by an earlier spread.
- Prohibited hidden `each`, `at`, `size`, iterator, conversion, callback, or
  suspension behavior during standard call-spread extraction.
- Preserved empty-Array expansion, shallow element identity, open/closed/frozen
  read-only behavior, and implementation freedom to avoid physical snapshots.
- Reserved any future generic iterable/spreadable protocol for an explicit
  normative design rather than implicitly inventing one through call syntax.

### Changed
- Synchronized all revisioned specification documents to revision 223.
  Only `PROTOS_LANGUAGE_SPEC.md` and `PROTOS_RUNTIME_SEMANTICS.md` gain
  normative semantic content in this revision.

## [0.1.222] - 2026-09-04

### Closed
- Standardized `Closure.parallel(arguments...)` as the Core v0.1 public isolated
  parallel-submission API returning an ordinary Future.
- Closed the bootstrap representation through parallel Closure projection:
  executable code and user-visible Closure value state may cross, while caller
  lexical contexts, caller `this`, caller return home, caller `methodHome`, and
  caller dynamic handlers do not.
- Defined every P computation to use a fresh P root execution environment with
  the frozen standard prelude, `this === null`, and a P-local return home.
- Rejected implementation-selected capture-safety analysis: caller captures
  never cross; attempted dependence on unavailable caller lexical names fails
  through ordinary lookup inside P.
- Defined explicit P arguments and the bootstrap Closure as one atomic combined
  input graph with preserved cycles and aliasing.
- Added standard `NonParallelValue` for synchronous input-boundary rejection and
  for untransferable normal results or failure values.
- Defined Closure values inside P input graphs as projectable ordinary Closures,
  not capture-transferable Closures.
- Defined P failure-value transfer and deterministic fallback when a P Error
  graph itself cannot cross the boundary.
- Defined non-local return from projected Closures to target only the fresh
  P-local return home, never the caller.
- Defined ordinary `closure.future()` created inside P as cooperative P-local
  work serialized against the same P mutable state.
- Defined nested `closure.parallel(...)` as a fresh isolation boundary and
  clarified that detachment cannot make P-local work survive the P domain as a
  persistent identity.
- Extended standard-prelude sharing semantics from Actor-only wording to Actor/P
  isolation domains while preserving semantic immutability of physically shared
  Protos objects.
- Removed the now-closed parallel API/bootstrap, capture-safety mechanism, and
  snapshot-point items from the Open Design Topics list.

### Changed
- Updated `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_RUNTIME_SEMANTICS.md`, and
  `docs/design/CONCURRENCY_DESIGN.md` with the closed P bootstrap/API semantics.
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 222.

## [0.1.221] - 2026-09-04

### Fixed
- Clarified generic ByteReadable EOF as an observation of current sequence state,
  not an unconditional permanent latch for every mutable-sequence receiver.
- Defined standard readable File EOF at each ordered read evaluation point.
- Required EOF reads to leave File position unchanged and allowed later reads to
  observe bytes made visible by later authorized growth without an intervening seek.
- Kept growth visibility/order under existing backend/cross-capability semantics.
- Defined current-EOF reads to complete with `null` rather than wait for future growth.
- Preserved TextReader's stronger permanent text-EOF lifecycle.

### Changed
- Synchronized all revisioned specification documents to revision 221. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.220] - 2026-09-03

### Fixed
- Removed the accidental Closure-only restriction from standard `Array.each`.
- Defined iteration callbacks using the same polymorphic invocation domain as
  ordinary parenthesized calls, including user-defined invokable objects.
- Required callability validation after ordinary receiver/argument evaluation
  and Array receiver validation but before snapshot establishment or callback
  execution.
- Kept non-invokable callback failure explicit without introducing a second
  callback/type hierarchy.

### Changed
- Synchronized all revisioned specification documents to revision 220.
  Only `PROTOS_LANGUAGE_SPEC.md` and `PROTOS_RUNTIME_SEMANTICS.md` gain
  normative semantic content in this revision.

## [0.1.219] - 2026-09-04

### Fixed
- Defined standard positioned File writes as overwrite-at-current-logical-position
  operations rather than leaving overwrite versus insertion behavior implicit.
- Defined a contributed prefix of length `k` to occupy the contiguous offsets
  starting at the write's ordered starting position and to advance the File's
  logical position by exactly `k`.
- Defined positioned writes to grow the file when contributed bytes extend past
  EOF.
- Required logical gap octets created when a write begins beyond EOF to read as
  zero, independent of sparse-file, extent, explicit-fill, or other backend
  representation.
- Required zero-contribution cancellation/failure to leave file size/content
  unchanged even when the logical position was beyond EOF, preventing tentative
  backend extension from leaking through a failed Future.
- Kept independently opened Files and external/backend changes under their
  existing cross-capability semantics without introducing a new global file
  ordering or atomicity domain.

### Changed
- Synchronized all revisioned specification documents to revision 219. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.218] - 2026-09-04

### Closed
- Closed the semantic core of isolated parallel execution while keeping its
  exact public API, syntax, bootstrap representation, partition representation,
  and implementation mechanisms open.
- Defined successful parallel submission to establish deterministic input
  snapshot state before control returns to the caller, independent of delayed
  scheduling.
- Defined that P never semantically mutates the calling Actor's original mutable
  input and publishes mutable result state only through successful completion.
- Defined failure and cancellation to publish no partially mutated P-owned state.
- Defined exclusive writable partitioning over logically disjoint mutable state,
  not merely non-overlapping physical ranges or top-level Array indexes.
- Defined nested partition authority as derived disjoint authority and required
  nested parallel work to remain capable of progress over bounded CPU carriers.
- Required deterministic parallel operations to keep observable result,
  combination ordering, and failure selection independent of worker scheduling,
  carrier count, chunk timing, and work-stealing choices.
- Defined isolated parallel execution as a CPU-computation domain rather than a
  second Actor-like effects domain.
- Prohibited P from implicitly inheriting Actor sender identity, Actor-local
  continuations, I/O authority, Process/Node/Cluster authority, or other ambient
  caller authority.
- Clarified that ActorRef/GroupRef transferability across Actor messaging does
  not imply transferability into P; any future P-safe effect capability requires
  its own normative contract.
- Preserved ordinary Closure capture-by-reference semantics: a future P API must
  validate/avoid unsafe captures or introduce an explicit bootstrap mechanism,
  never silently reinterpret captures as by-value.

### Changed
- Updated the canonical language and runtime concurrency summaries and the
  normative I/O capability model to reflect the closed P isolation,
  publication, and authority boundaries.
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 218.

## [0.1.217] - 2026-09-03

### Fixed
- Defined distinct `filesystem.open` calls as independent asynchronous namespace
  acquisitions rather than an implicit per-Filesystem or per-Path FIFO.
- Clarified that same-Actor sequential invocation without awaiting an earlier
  open does not order the opens' namespace selection/commitment points.
- Defined interacting pending opens, including createNew/existing, concurrent
  createNew, and truncate-on-open cases, by each operation's own race-free
  selection and commitment point.
- Required programs needing acquisition dependency to establish it explicitly
  through ordinary Future sequencing or a stronger normative protocol.
- Preserved implementation freedom to serialize, batch, or overlap opens when
  that machinery does not create stronger portable ordering.
- Avoided requiring a global Filesystem lock, per-Path queue, or same-Actor
  namespace FIFO while preserving post-open File ordering and append-placement
  guarantees.

### Changed
- Synchronized all revisioned specification documents to revision 217. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.216] - 2026-09-03

### Closed
- Closed `Cluster membership protocol` for Core v0.1 by classifying the
  interoperable membership protocol/API as outside Core rather than leaving an
  implementation-selectable semantic hole.
- Defined the minimal Core membership contract: membership is distinct from
  reachability, Node identity, physical existence, and Authority.
- Prohibited transport connectivity/reachability from implicitly creating
  membership and prohibited membership loss from proving Node/Process death.
- Defined that implementation-specific membership algorithms may establish
  Cluster membership knowledge but cannot weaken closed Core identity,
  uncertainty, partition, split-brain, or Authority semantics.
- Explicitly stated that Core v0.1 does not standardize join/leave/down APIs,
  wire format, gossip/consensus algorithm, convergence timing, or
  cross-implementation Cluster-membership interoperability.
- Reserved interoperable membership, epochs, quorum/consensus, and
  failure-driven removal for a future normative Cluster facility.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 216.

## [0.1.215] - 2026-09-03

### Fixed
- Defined `Array.size` as the exact semantic Integer count of current indexed
  elements, independent of host index width or representation.
- Defined every invocation's `args` as a fresh frozen standard Array containing
  exactly the caller-supplied positional arguments in source order.
- Defined each rest-parameter binding as its own fresh frozen standard Array of
  the remaining caller-supplied positional arguments.
- Made argument/rest Array identity explicit so implementations cannot
  observably reuse one shared empty or content-equal collection object.
- Preserved shallow aliasing of argument objects while eliminating a separate
  implementation-selected argument-collection kind.
- Preserved optimization freedom through scalar replacement and virtual Arrays
  when fresh identity, frozen behavior, ordering, `at`, `size`, and `each`
  semantics remain observable as specified.

### Changed
- Synchronized all revisioned specification documents to revision 215.
  Only `PROTOS_LANGUAGE_SPEC.md` and `PROTOS_RUNTIME_SEMANTICS.md` gain
  normative semantic content in this revision.

## [0.1.214] - 2026-09-03

### Fixed
- Reconciled strict/fatal decoding with the already-standardized explicit
  U+FFFD replacement policy instead of treating malformed input as
  unconditionally fatal in line reading and one-shot EOF handling.
- Defined portable UTF8/UTF16LE/UTF16BE replacement segmentation by Unicode
  17.0.0 maximal subparts, with exactly one U+FFFD per consumed maximal subpart.
- Defined incomplete final input to fail under strict decoding and to use the
  same malformed-input replacement rule under replacement decoding.
- Made replacement output invariant under native read, buffering, decoder-call,
  vectorization, and other implementation-selected chunk boundaries.
- Required host-provided non-portable Encodings to make replacement segmentation
  deterministic within their host-bound contract rather than leaking converter
  call boundaries or library-version heuristics.
- Defined malformed source octets consumed for U+FFFD replacement to participate
  in `readLine(maxBytes)` source-byte accounting, while preserving strict-mode
  decoding-error precedence.

### Changed
- Synchronized all revisioned specification documents to revision 214. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.213] - 2026-09-03

### Closed
- Closed `Split-brain mitigation mechanisms` for Core v0.1 by defining that
  Core has no automatic partition winner or implementation-selected downing
  strategy.
- Reduced Core split-brain safety to the existing scoped Authority invariant:
  non-authoritative work may continue, while an operation requiring Authority
  must not proceed unless current valid Authority can be demonstrated.
- Prohibited majority/oldest/local-side/time-based heuristics from silently
  granting Authority or terminating the opposite partition.
- Defined that Core performs no implicit partition-healing state merge,
  uncertain-message replay, identity rewrite, or remote-side replacement.
- Kept Cluster membership, Authority acquisition/transfer, quorum/consensus,
  leases, fencing, and explicit future split-brain policies as separate
  mechanisms requiring their own normative contracts.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 213.

## [0.1.212] - 2026-09-03

### Fixed
- Defined the initial logical sequence position of every newly opened standard
  File with position-sensitive read/write behavior as byte offset zero.
- Applied the same rule across existing/create/createNew, preserve/truncate, and
  positioned/append open configurations.
- Clarified that append mode changes each write's placement to current EOF but
  does not initialize the File's Protos logical position at EOF.
- Prevented host/runtime append helpers or native cursors with different initial
  positions from leaking platform-dependent first-read, first-position, or
  post-open sequencing behavior.
- Kept the rule representation-independent: implementations may use positional
  I/O, virtual cursors, or native cursor adjustment when the observable logical
  position remains zero.
- Required backends unable to provide or emulate the standard initial position
  not to expose a position-sensitive standard File with a host-selected cursor.

### Changed
- Synchronized all revisioned specification documents to revision 212. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.211] - 2026-09-03

### Fixed
- Defined standard String indexing and `size` with the untailored Unicode 17.0.0
  default extended-grapheme-cluster rules from UAX #29 revision 47.
- Defined `String.size` to return the exact semantic Integer grapheme count.
- Defined `String.at` to accept exact semantic Integer indices only, reject
  negative/out-of-range/coerced indices, and return the exact scalar subsequence
  forming the selected grapheme cluster as a String.
- Prohibited host locale, ICU/Unicode upgrades, normalization, rendering
  tailoring, and internal encoding representation from changing Core-visible
  String boundaries.
- Kept String immutable and made explicit that bracket-read support does not
  imply a hidden standard in-place `atPut` mutation protocol.

### Changed
- Synchronized all revisioned specification documents to revision 211.
  Only `PROTOS_LANGUAGE_SPEC.md` and `PROTOS_RUNTIME_SEMANTICS.md` gain
  normative semantic content in this revision.

## [0.1.210] - 2026-09-03

### Closed
- Closed `Network-partition detection and reporting` for Core v0.1.
- Defined that Core has no proven `NETWORK_PARTITION` semantic state: ambiguous
  communication loss is reported as `UNREACHABLE` or `UNKNOWN`.
- Prohibited implementation-specific partition timeouts and diagnostics from
  changing lifecycle, membership, Authority, ActorRef, monitoring, messaging,
  or replacement semantics.
- Defined that symmetric partition suspicion grants neither side Authority and
  does not prove remote termination.
- Preserved identity across transient reachability loss when no independent
  authoritative decision ended or removed the incarnation.
- Kept split-brain resolution, membership removal, fencing, quorum decisions,
  and Authority acquisition as separate open distributed mechanisms.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 210.

## [0.1.209] - 2026-09-03

### Fixed
- Closed the TextWriter failure-frontier ambiguity for later outstanding ordered
  text writes when an earlier committed write can still poison the wrapper.
- Prohibited a later text write from making target-visible output before every
  earlier ordered text write has reached an aftermath that leaves the writer
  usable for later output.
- Allowed speculative validation, encoding, checkpointing, staging, and other
  reversible pipelining while keeping irreversible byte contribution ordered
  behind the predecessor's failure frontier.
- Required permanent predecessor failure to make later outstanding and new text
  writes fail with zero contribution of their own and no committed encoder-state
  advance.
- Prevented proxy routing and backend/native completion timing from deciding
  whether bytes from a later text write escape after an earlier writer-poisoning
  failure.
- Preserved recovery through a stronger downstream protocol only when that
  protocol supplies enough semantic information to establish exact remaining
  output and encoder/output state.

### Changed
- Synchronized all revisioned specification documents to revision 209. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.208] - 2026-09-03

### Closed
- Closed `Node failure detection mechanism` for Core v0.1.
- Defined that communication loss and failure-detector suspicion cannot by
  themselves establish authoritative Node `TERMINATED`.
- Prohibited implementation-specific heartbeat, phi, retry, grace-period,
  infrastructure-probe, or host-status thresholds from changing Core-visible
  Node lifecycle.
- Preserved reversible `UNREACHABLE`/`UNKNOWN` state for remote Node
  communication loss.
- Kept Node incarnation identity stable across transient connectivity loss.
- Kept Cluster membership removal, partition downing, fencing, and split-brain
  resolution separate and explicitly outside this Core closure.
- Required any future distributed failure detector to separate suspicion from
  an authoritative downing/removal decision and to define its authority and
  consequences normatively.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 208.

## [0.1.207] - 2026-09-03

### Fixed
- Defined standard Bytes indexed state as receiver-owned, finite, dense,
  zero-based octet storage.
- Defined `Bytes.at` to require an exact semantic Integer index and to return
  the stored octet as an exact semantic Integer in `0 .. 255`.
- Defined `Bytes.atPut` to require an in-range Integer index and Integer byte
  value in `0 .. 255`, rejecting truncation, masking, wrapping, parsing, and
  implicit conversion.
- Defined standard byte replacement as fixed-length existing-position mutation,
  with receiver-domain and open/closed/frozen behavior parallel to other
  receiver-owned indexed state.
- Kept Bytes equality/hash identity-based and separated raw-octet semantics from
  text, host byte signedness, endianness, and storage representation.

### Changed
- Synchronized all revisioned specification documents to revision 207.
  Only `PROTOS_LANGUAGE_SPEC.md` and `PROTOS_RUNTIME_SEMANTICS.md` gain
  normative semantic content in this revision.

## [0.1.206] - 2026-09-03

### Closed
- Closed `Process failure detection mechanism` for Core v0.1.
- Defined that Core has no automatic distributed failure detector for remote
  Processes and no implementation-specific timeout/heartbeat heuristic may
  fabricate `TERMINATED`.
- Distinguished direct runtime lifecycle knowledge from inferred remote
  failure suspicion.
- Required communication loss, timeout, reconnect failure, heartbeat silence,
  and Node unreachability to remain `UNREACHABLE`/`UNKNOWN` absent independent
  authoritative termination knowledge.
- Composed this boundary with `ActorRef.termination()` so remote transport loss
  cannot masquerade as Actor death.
- Reserved leases, epochs, quorum membership, fencing, explicit downing, and
  similar mechanisms for a future normative distributed-runtime facility.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 206.

## [0.1.205] - 2026-09-03

### Fixed
- Repaired the normative paragraph boundary in filesystem creation semantics
  after the `existing` race-free-selection change was integrated.
- Removed wording that accidentally attached `create`'s absent-target behavior
  to the summary sentence for `existing`, which could be read as allowing
  `creation: existing` to create a missing target.
- Restored the intended disjoint semantics: `existing` selects an already
  existing resource or fails; `create` selects an existing resource or creates
  one when absent.
- Preserved the already-defined race-free selection, confinement, stable File
  binding, and concurrent namespace-operation rules without introducing a new
  filesystem mechanism.

### Changed
- Synchronized all revisioned specification documents to revision 205. Only
  `PROTOS_IO_MODEL.md` gains normative corrective content in this revision.


## [0.1.204] - 2026-09-03

### Fixed
- Defined `readLine(maxBytes)` byte accounting for stateful and multibyte
  encodings in terms of the encoded source interval for the current line.
- Required every valid pre-terminator source octet to count, including
  state/shift/control octets that produce no Unicode scalar value.
- Excluded the encoded LF/CR/CRLF terminator extent itself and avoided charging
  decoder state bytes consumed before the current line begins a second time.
- Prevented stateful encodings from bypassing the safety bound with arbitrarily
  many non-text-emitting shift/control sequences.
- Defined line-too-long versus decoding-error precedence for valid state-only
  input without relying on converter-specific notions of which byte "belongs"
  to a character.
- Preserved implementation freedom for buffering and converter internals while
  making the observable byte-boundary result independent of those internals.

### Changed
- Synchronized all revisioned specification documents to revision 204. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.203] - 2026-09-03

### Fixed
- Defined standard `Array.each(block)` as deterministic ascending-index
  iteration over a shallow element-reference snapshot captured before callbacks.
- Defined same-Array element replacement during callbacks or suspension as
  permitted when ordinary Array state rules allow it, without changing the
  current iteration snapshot.
- Required `each` to return the receiver after normal completion and to stop
  immediately on error or ordinary non-local unwind without rolling back prior
  effects.
- Prohibited hidden Array-wide iteration locks, mutation guards, and scheduler
  dependencies while preserving implementation freedom for snapshot storage.
- Applied the existing standard Array receiver-domain rule to `each`.

### Changed
- Synchronized all revisioned specification documents to revision 203.
  Only `PROTOS_LANGUAGE_SPEC.md` and `PROTOS_RUNTIME_SEMANTICS.md` gain
  normative semantic content in this revision.

## [0.1.202] - 2026-09-03

### Closed
- Closed `Failure-authority API` for Core v0.1 by defining that Core exposes no
  configurable public failure-authority API.
- Fixed the non-root Actor default: an unhandled fatal failure terminates that
  incarnation and failure authority performs no automatic replacement,
  escalation, sibling restart, or subtree restart.
- Preserved the existing RootActor rule: fatal RootActor failure terminates the
  Process.
- Kept ActorGroup desired-state reconciliation independent; a Group may create a
  fresh incarnation without that becoming Actor restart or failure-authority
  replacement.
- Reclassified Replace/Stop/Escalate/Ignore as future policy vocabulary rather
  than implementation-selectable Core behavior.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 202.

## [0.1.201] - 2026-09-03

### Fixed
- Defined file-URL conversion to establish URL components and path-segment
  hierarchy before percent-decoding segment data.
- Prevented percent-decoded separators, prefixes, device syntax, or other
  hierarchy-looking data from creating extra Path structure.
- Prevented percent-encoded data that decodes to `"."` or `".."` from being
  silently reclassified as portable current/parent traversal.
- Required lossless percent/text conversion and failure instead of replacement,
  truncation, normalization, delimiter reinterpretation, or other lossy native
  filename conversion.
- Confined file-URL authority interpretation to mappings already authorized by
  the receiving Filesystem capability.
- Prohibited ambient DNS, network acquisition, UNC authority expansion, or host
  root access merely to interpret a file URL.

### Changed
- Synchronized all revisioned specification documents to revision 201. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.200] - 2026-09-03

### Fixed
- Closed an admission-starvation hole left outside runnable scheduler fairness.
- Added weak fairness for continuously admission-eligible `send()` and
  `request()` operations under recurring compatible admission opportunities.
- Required later arrivals not to bypass such an operation forever.
- Made same-sender FIFO explicit across backpressure for still-live operations
  targeting the same concrete Actor.
- Preserved ActorGroup routing freedom without introducing Group-wide FIFO.
- Kept admission fairness distinct from Actor-task runnability and avoided
  mandating queue structure, round-robin scheduling, equal shares, or latency
  bounds.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 200.
