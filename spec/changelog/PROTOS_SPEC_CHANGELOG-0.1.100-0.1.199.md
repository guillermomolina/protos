# Protos Language Specification Changelog — 0.1.100–0.1.199

Archived from [`../PROTOS_SPEC_CHANGELOG.md`](../PROTOS_SPEC_CHANGELOG.md).

## [0.1.199] - 2026-09-03

### Fixed
- Defined standard Array indexed state as receiver-owned, finite, dense, and
  zero-based at each observation point.
- Defined `Array.at` / `atPut` to accept exact semantic Integer indices only and
  to fail on negative or out-of-range indices without coercion or wrapping.
- Defined standard `Array.atPut` as replacement of an existing element only,
  with no implicit append, growth, holes, shifting, or length change.
- Defined incompatible inherited/copied Array methods to fail rather than
  borrowing ancestor storage or granting hidden Array state.
- Defined open/closed/frozen interaction for Array element replacement and kept
  standard Array equality/hash on the existing identity defaults.

### Changed
- Synchronized all revisioned specification documents to revision 199.
  Only `PROTOS_LANGUAGE_SPEC.md` and `PROTOS_RUNTIME_SEMANTICS.md` gain
  normative semantic content in this revision.

## [0.1.198] - 2026-09-03

### Fixed
- Defined `creation: existing` as one race-free select-and-open operation rather
  than leaving a probe/acquisition TOCTOU gap to implementations.
- Required the namespace state at one selection point to determine absence or
  the concrete resource selected by the open.
- Prevented a successful helper existence/metadata probe from reserving resource
  identity or being combined observably with acquisition from a later namespace
  state.
- Allowed genuinely concurrent namespace operations to determine the state seen
  before the selection point without imposing a global filesystem order.
- Applied stable File-resource binding immediately after successful selection so
  later namespace changes cannot retarget the returned File or this open's
  truncate-on-open effect.
- Required uncertain backends to fail rather than expose a standard File whose
  confined resource selection cannot be established race-safely.

### Changed
- Synchronized all revisioned specification documents to revision 198. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.197] - 2026-09-03

### Fixed
- Defined filesystem `create` as one race-free open-or-create selection rather
  than leaving an observable existence-check/acquisition gap to implementations.
- Required an existing target at the selection point to be opened and an absent
  target to be created and selected as one semantic operation.
- Prevented helper-primitive races from leaking as spurious already-exists or
  not-found failures when the standard open-or-create operation has a valid
  result.
- Allowed genuinely concurrent namespace changes to determine the namespace
  state seen at the operation's selection point without imposing a global
  filesystem order.
- Applied the stable File-resource binding rule immediately after resource
  selection so later namespace changes cannot retarget the resulting File or
  this open's truncate-on-open effect.
- Required backends that cannot provide or emulate the race-free invariant
  together with Filesystem confinement to reject standard `create` semantics.

### Changed
- Synchronized all revisioned specification documents to revision 197. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.196] - 2026-09-03

### Closed
- Closed `Monitoring API`.
- Standardized `ActorRef.termination()` as an independent non-task-backed
  Future observation of one concrete Actor incarnation.
- Defined monitoring as known-termination observation only; unreachable,
  unknown, timeout, routing loss, and network partition do not fabricate death.
- Defined independent cancellation for each observation without any lifecycle
  authority over the target Actor.
- Closed the registration-versus-termination lost-wakeup race.
- Required terminal/cancelled observation registrations to be removed or made
  inert to prevent unbounded retention.
- Preserved ActorRef incarnation identity across replacement and kept failure
  diagnostics separate from lifecycle observation.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 196.

## [0.1.195] - 2026-09-03

### Fixed
- Defined the lifetime of the same-Map keyed-entry mutation restriction when a
  user `==` callback explicitly suspends.
- Required an in-progress Map comparison scope to remain active across Actor
  turns until that comparison returns or unwinds.
- Defined conflicting keyed-entry mutation by another Actor-local task to fail
  before mutation rather than race with the suspended search or wait on a lock.
- Preserved read-only same-Map work, unrelated Map mutation, ordinary Actor
  scheduling, and exact scope release on return/error/non-local-return/cancellation
  unwind.
- Distinguished Map-scoped comparison protection from task-local dynamic error
  handlers without introducing cross-Actor synchronization.

### Changed
- Synchronized all revisioned specification documents to revision 195.
  `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_RUNTIME_SEMANTICS.md`, and
  `docs/design/CONCURRENCY_DESIGN.md` gain normative semantic content in this revision.

## [0.1.194] - 2026-09-03

### Fixed
- Defined Process standard-stream and standard-stream-encoding accessors as
  synchronous, non-waiting lookups over bootstrap-established bindings.
- Prohibited hidden external acquisition, readiness waits, remote negotiation,
  or other potentially suspending provisioning behind those non-Future calls.
- Kept pay-as-you-grow representation laziness: implementations may still defer
  local wrapper/proxy/descriptor allocation when that work is non-waiting.
- Required potentially waiting standard-stream provisioning to occur in
  Process-host/bootstrap machinery or through a separately specified
  asynchronous acquisition protocol.
- Clarified that accessor failure observes already-established unavailability
  or invalid bootstrap configuration rather than launching a delayed discovery
  of eventual host availability.

### Changed
- Synchronized all revisioned specification documents to revision 194. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.193] - 2026-09-03

### Closed
- Closed `Fatal versus non-fatal handler errors` and `Which errors terminate an Actor`.
- Defined fatality structurally: any `Error` escaping the outermost dynamic
  handler boundary of an ordinary Actor turn is fatal to that Actor incarnation.
- Defined handled errors as non-fatal unless later execution triggers another
  lifecycle cause.
- Preserved asynchronous task isolation: an unhandled child-task error fails its
  Future and becomes Actor-fatal only if later observation re-signals the error
  and it escapes an Actor turn unhandled.
- Kept cancellation distinct from fatal error semantics.
- Preserved accepted-request uncertainty instead of exposing a destination's
  internal unhandled error as an implicit reply.
- Rejected implementation-specific fatal-error whitelists and error-name/type
  taxonomies.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 193.

## [0.1.192] - 2026-09-03

### Fixed
- Defined a Core String semantic value as an exact ordered sequence of Unicode
  scalar values.
- Made String semantic identity, default equality, and identity hashing depend
  on that exact sequence rather than implementation-selected Unicode
  normalization, host encoding, locale, case folding, or grapheme processing.
- Defined canonically equivalent but differently represented scalar sequences as
  distinct Core String values unless a program explicitly normalizes them.
- Preserved the existing exact `LF` / `CR` / `CRLF` String-content distinction.

### Changed
- Synchronized all revisioned specification documents to revision 192.
  Only `PROTOS_LANGUAGE_SPEC.md` and `PROTOS_RUNTIME_SEMANTICS.md` gain
  normative semantic content in this revision.

## [0.1.191] - 2026-09-03

### Fixed
- Defined one logical encoder-state/output-ordering domain for all standard
  `TextWriter.writeText()` and `writeLine()` operations on the same writer.
- Preserved per-Actor text-write invocation order even while earlier Futures
  remain pending.
- Defined genuinely concurrent cross-Actor text writes as initially unordered,
  with routing/admission choosing one stable order for encoding validation,
  encoder-state evolution, cancellation/failure aftermath, and byte output.
- Prevented host scheduling, speculative encoding, or downstream byte-I/O timing
  from selecting a different observable encoder order.
- Composed any `Flushable` frontier and `Closable` cutover exposed by a
  TextWriter with that same logical text-output flow.
- Kept implementation freedom for staging, pipelining, buffering, and
  overlapping backend work without requiring a global lock or one native write
  at a time.

### Changed
- Synchronized all revisioned specification documents to revision 191. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.190] - 2026-09-03

### Fixed
- Generalized the ordinary `Object` equality/hash default from only
  identity-bearing Core objects to every Core object lacking an explicit
  normative specialization.
- Closed the remaining implementation-choice gap for value-identity objects
  such as String values, canonical Booleans, and `null`.
- Required absent-specialization standard `==` to use `===` and standard
  `hash` to use `identityHashOf`, preserving each value category's semantic
  identity.
- Preserved explicit Number equality/hash specialization, the documented
  Map/IdentityMap consequence, and ordinary user-defined overrides.

### Changed
- Synchronized all revisioned specification documents to revision 190.
  Only `PROTOS_LANGUAGE_SPEC.md` and `PROTOS_RUNTIME_SEMANTICS.md` gain
  normative semantic content in this revision.

## [0.1.189] - 2026-09-03

### Closed
- Closed `Actor garbage collection`.
- Defined live Actor lifetime as explicit lifecycle rather than ordinary
  reachability garbage collection.
- Prohibited hidden termination from loss of known ActorRefs, idleness, or
  memory-pressure heuristics.
- Allowed post-termination Actor implementation state to be reclaimed or
  compacted once remaining ActorRef, identity, monitoring, routing, and
  communication semantics are preserved.
- Kept virtual-actor/passivation models as possible future higher-level
  abstractions rather than changing concrete Actor incarnation semantics.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 189.

## [0.1.188] - 2026-09-03

### Fixed
- Distinguished an absent Environment binding from a Protos lookup String that
  cannot be losslessly represented as one valid native environment-variable
  name.
- Required both `Environment.get(name)` and `contains(name)` to fail for an
  unrepresentable/invalid native query name instead of returning `null` or
  `false`.
- Required query-name validation to occur before value decoding and ordinary
  lookup-result selection.
- Prohibited lossy replacement, truncation, normalization, delimiter
  reinterpretation, or implementation-selected conversion from silently
  changing the lookup name.
- Kept the concrete native name repertoire host-dependent while making the
  portable failure-vs-absence distinction deterministic.

### Changed
- Synchronized all revisioned specification documents to revision 188. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.187] - 2026-09-03

### Closed
- Closed fundamental graceful Actor termination semantics.
- Defined one irreversible stop cutover that prevents new concrete-Actor
  acceptance and new ordinary turns.
- Defined accepted-but-not-started interactions as lost accepted work instead
  of draining arbitrary mailbox backlog or pretending non-acceptance.
- Preserved non-preemptive execution for an already-running turn until normal
  completion or its next portable cancellation boundary.
- Reused existing Actor-termination cancellation for Actor-local tasks and
  non-task-backed producer Futures.
- Distinguished graceful stop from fatal failure and preserved independent
  Group desired-state reconciliation.
- Removed `Actor graceful shutdown` and `Actor stop API and exact lifecycle
  mechanics` from Open Design Topics; exact administrative/public API shape
  remains explicitly open inside the CLOSED lifecycle section.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 187.

## [0.1.186] - 2026-09-03

### Fixed
- Defined a uniform default equality/hash rule for identity-bearing Core objects
  that have no explicitly specialized normative behavior.
- Required such objects to use semantic identity for standard `==` and
  `identityHashOf` for standard `hash`.
- Prevented built-in containers, buffers, Futures, errors, contexts, modules,
  prototypes, and other identity-bearing objects from acquiring
  implementation-selected structural/content equality or hashing.
- Preserved explicit specializations such as Number hashing and ordinary
  user-defined `==` / `hash` overrides.

### Changed
- Synchronized all revisioned specification documents to revision 186.
  Only `PROTOS_LANGUAGE_SPEC.md` and `PROTOS_RUNTIME_SEMANTICS.md` gain
  normative semantic content in this revision.

## [0.1.185] - 2026-09-03

### Fixed
- Defined `sync()` as a logical durability frontier within the receiver's
  existing sequence-state ordering domain.
- Required receiver changes ordered before a sync frontier to be covered by a
  successful sync even when their Futures were still pending in implementation
  queues.
- Defined genuinely concurrent cross-Actor change/sync requests as initially
  unordered, with routing/admission choosing a stable relative order.
- Required that stable order to determine whether a competing receiver change
  belongs to the sync frontier or is later state outside it.
- Kept sync local to the logical receiver: it does not create a global
  durability or Actor-memory barrier across independently opened aliasing Files.
- Allowed a backend to persist extra later/independent state as an
  implementation side effect without promoting that extra persistence into a
  portable Protos ordering guarantee.

### Changed
- Synchronized all revisioned specification documents to revision 185. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.184] - 2026-09-03

### Fixed
- Defined the receiver domain of standard `Map` and `IdentityMap` keyed
  behavior in terms of receiver-owned keyed-entry state rather than delegation.
- Made inherited/copied standard Map methods fail on incompatible receivers
  before hashing, equality callbacks, identity-key search, snapshot capture, or
  keyed-entry mutation.
- Prevented delegation from borrowing ancestor Map entries or lazily granting
  hidden associative storage to ordinary child objects.
- Preserved ordinary user-defined behavior and explicitly generic standard
  behavior without introducing a class hierarchy or second delegation model.

### Changed
- Synchronized all revisioned specification documents to revision 184.
  Only `PROTOS_LANGUAGE_SPEC.md` and `PROTOS_RUNTIME_SEMANTICS.md` gain
  normative semantic content in this revision.

## [0.1.183] - 2026-09-03

### Fixed
- Defined the aftermath of a failed `ByteReadable.read` when lower-level bytes
  were obtained and preserved before the failure outcome was reported.
- Required preserved bytes to remain ordinary unread input while treating the
  already-reported error as the outcome of the failed read, not as a queued
  stream element that must automatically fail a later read again.
- Required later reads to fail only when their then-current receiver/backend
  state independently requires failure, including persistent or distinct
  backend errors.
- Prevented implementations from diverging solely over whether one previously
  reported host error is replayed after preserved bytes are drained.

### Changed
- Synchronized all revisioned specification documents to revision 183. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.182] - 2026-09-03

### Fixed
- Defined Map key-search state as fixed control state without snapshotting
  mutable key objects.
- Defined visibility of mutations performed during equality callbacks to later
  candidate comparisons.
- Prohibited implicit stored-key hash recomputation, query-hash recomputation,
  lookup restart, candidate reordering, and semantic key snapshots.

### Changed
- Synchronized all revisioned specification documents to revision 182.
  Only `PROTOS_LANGUAGE_SPEC.md` and `PROTOS_RUNTIME_SEMANTICS.md` gain
  normative semantic content in this revision.

## [0.1.181] - 2026-09-03

### Fixed
- Defined `flush()` as a logical propagation frontier within one ordered output
  flow rather than leaving write/flush inclusion to native scheduling.
- Required writes ordered before a flush to belong to that flush frontier and
  prevented successful flush completion from omitting such accepted output.
- Defined genuinely concurrent cross-Actor write/flush requests as initially
  unordered, with routing/admission choosing a stable relative order.
- Required the chosen order to determine whether the competing write is covered
  by the flush or is later output outside that frontier.
- Kept the frontier local to the logical output flow without introducing a
  global Actor memory-ordering or synchronization primitive.

### Changed
- Synchronized all revisioned specification documents to revision 181. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.180] - 2026-09-03

### Fixed
- Defined an atomic append-placement boundary for standard append writes
  selecting the same underlying filesystem resource.
- Allowed nondeterministic ordering of genuinely concurrent append writes while
  prohibiting overlap or byte-level interleaving of their contributed
  sequences.
- Defined partial failed append behavior so only the contributed prefix affects
  file end; an implementation may not reserve an entire requested sequence and
  thereby create an observable semantic hole.
- Required backends to provide or emulate the standard append-placement
  invariant, or reject standard append mode for resources where they cannot.
- Kept the rule local to append placement without creating a general ordering
  domain between independently opened File capabilities.

### Changed
- Synchronized all revisioned specification documents to revision 180. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.179] - 2026-09-03

### Fixed
- Defined the semantic boundary between separately opened File capabilities
  that alias the same underlying filesystem resource.
- Preserved independent Protos ordering and lifecycle domains for each File even
  when the host identifies their underlying resource as the same inode, file
  object, or equivalent identity.
- Explicitly declined to promise portable cross-File ordering, atomicity,
  visibility latency, or byte-level non-interleaving unless a stronger concrete
  protocol provides such guarantees.
- Prevented host-reported resource identity from silently becoming a global
  Protos ordering primitive.
- Clarified that cross-capability observations remain governed by the
  underlying Filesystem resource semantics while each File's own Protos
  contract remains normative.

### Changed
- Synchronized all revisioned specification documents to revision 179. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.178] - 2026-09-03

### Fixed
- Defined a receiver-visible `close()` lifecycle cutover for operations that
  require the resource to remain open.
- Closed the ambiguity between concurrent I/O operations and `close()` when
  different Actors access the same logical receiver through Actor-safe proxies.
- Required a stable routing/admission choice for genuinely concurrent operation
  versus close, preventing host/native scheduling from retroactively moving an
  operation across the close cutover.
- Preserved the existing distinction between close-induced operation failure and
  operation cancellation, and preserved already-committed effects.
- Made the lifecycle cutover a property of the logical receiver rather than
  proxy object identity.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 178.

## [0.1.177] - 2026-09-03

### Fixed
- Defined successful `File` open as a binding to the concrete filesystem
  resource selected by that open operation rather than to a continuing Path
  lookup.
- Required namespace changes after open, including rename, removal,
  replacement, relinking, or equivalent directory-entry changes, not to
  retarget an already-open standard `File`.
- Distinguished separate successful opens as separate File capabilities with
  independent lifecycle, cursor, buffering, shutdown, and logical ordering
  state unless a stronger protocol explicitly couples them.
- Allowed separately opened Files to select the same underlying resource through
  filesystem aliases without merging their Protos capabilities or inventing a
  global ordering domain.
- Kept resource identity semantic and opaque: no portable inode, file ID,
  descriptor, handle, or other host identity token is introduced.
- Required a backend that cannot preserve or emulate stable resource binding
  not to expose the weaker host handle as a standard `File` capability.

### Changed
- Synchronized all revisioned specification documents to revision 177. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.176] - 2026-09-03

### Fixed
- Defined the standard equality/hash behavior of `Map` and `IdentityMap`
  explicitly as the ordinary identity-based `Object` defaults rather than
  leaving structural collection equality/hashing to implementations.
- Made distinct Maps unequal under standard `==` even when their current
  associations are structurally alike, and made standard Map `hash()` use
  `identityHashOf` without traversing entries.
- Prevented ordinary Map mutation, insertion order, recorded hashes, cycles,
  or key/value callbacks from affecting the default Map equality/hash class.
- Preserved ordinary user overrides while keeping `===`, `identityHashOf`,
  and `IdentityMap` key matching primitive and unaffected.
- Avoided hidden deep-comparison policy, recursive traversal, snapshots,
  cycle detectors, or collection locks in the default protocol.

### Changed
- Synchronized all revisioned specification documents to revision 176.
  No grammar, Actor/Future, or I/O semantics change.

## [0.1.175] - 2026-09-03

### Fixed
- Defined Actor termination as a cancellation-request boundary for pending
  Actor-originated asynchronous operations represented by non-task-backed
  Futures, including applicable I/O and communication operations.
- Prevented dead Actors from leaving uncommitted producer operations running
  solely because those Futures had no producing `Task`.
- Preserved producer-specific commitment semantics: Actor termination cannot
  roll back committed I/O effects, unsend accepted messages, or invent stronger
  cancellation than the operation already supports.
- Kept Actor termination liveness bounded to task cleanup: termination requests
  producer cancellation but does not generally wait for every producer Future
  to become terminal.
- Clarified that residual committed/cancellation backend work remains under
  runtime/producer custody and cannot resurrect or execute ordinary Protos code
  in the terminated Actor.
- Clarified that Actor termination is not implicit I/O close/flush/sync/shutdown
  and does not revoke Process-local shared stream capabilities.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 175.

## [0.1.174] - 2026-09-03

### Fixed
- Defined a receiver-visible `shutdownWrite()` cutover for one logical output
  direction rather than leaving concurrent write/shutdown outcomes to native
  scheduling.
- Preserved Protos-defined ordering: a write ordered before shutdown remains an
  accepted preceding operation and shutdown waits behind it.
- Required a write ordered after the write-shutdown cutover to fail with zero
  byte contribution rather than racing a later backend/native write.
- Defined genuinely concurrent cross-Actor write/shutdown requests as initially
  unordered, with routing/admission choosing one stable relative order.
- Required that stable choice to decide whether the competing write is accepted
  before shutdown or rejected after the cutover.
- Shared the cutover across Actor-local proxies for the same output direction
  without imposing a global Actor scheduler order or one native syscall at a
  time.

### Changed
- Synchronized all revisioned specification documents to revision 174. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.173] - 2026-09-03

### Fixed
- Defined Protos Process termination as an I/O resource-custody and authority
  boundary independently of operating-system process exit.
- Required termination to revoke/detach the terminating Process's I/O
  capabilities and Actor-safe proxies even in a managed runtime that keeps the
  hosting OS process alive.
- Prevented termination from implying successful `close`, `flush`, `sync`, or
  half-close operations, synthesized lifecycle Futures, arbitrary Protos cleanup
  callbacks, or guaranteed buffered-output/durability completion.
- Required residual resources held solely for the terminating Process to
  transfer to implementation/host cleanup custody without relying on GC or
  execution of code in the dead Process.
- Preserved already committed I/O effects and prevented termination from
  inventing rollback semantics for pending/committed operations.
- Distinguished Process-local authority revocation from destruction of backend
  resources that are independently shared with other Processes or principals.
- Kept exact post-transfer physical cleanup timing/backend consequences
  host-dependent while requiring identical Protos-level authority revocation in
  standalone and managed runtimes.

### Changed
- Synchronized all revisioned specification documents to revision 173. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.172] - 2026-09-03

### Fixed
- Clarified that `request()` reply formation performs Actor-boundary value
  transfer before resolving the caller's request Future.
- Closed the ambiguity between generic local Future flattening and the rule that
  `Future` values are non-transferable across Actors.
- Defined a handler-returned Future as a non-transferable reply value that fails
  the request Future with `NonTransferableValue`.
- Prohibited cross-Actor Future adoption edges created implicitly by request/reply
  machinery.
- Preserved explicit suspension: a handler that wants to reply with an eventual
  local Future result must explicitly observe it and return the resulting
  transferable value.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 172.

## [0.1.171] - 2026-09-03

### Fixed
- Defined `Map.each(block)` and `IdentityMap.each(block)` as standard
  insertion-order iteration over a shallow logical association snapshot
  captured at invocation start.
- Required callbacks to receive the representative key and snapshot value
  as two arguments and made successful `each` return the receiver Map.
- Made later insertion, removal, and mapped-value replacement unable to
  alter an already-established iteration, including across task suspension.
- Avoided hidden Map-wide iteration locks and mutation prohibitions, so
  other Actor-local tasks remain free to mutate the Map while an iterator
  task is suspended under ordinary Actor semantics.
- Kept the snapshot shallow and implementation-independent; persistent,
  versioned, copy-on-write, or equivalent representations may avoid eager
  physical copying while preserving the same observable iteration.

### Changed
- Synchronized all revisioned specification documents to revision 171.
  No grammar, Future, or I/O semantics change.

## [0.1.170] - 2026-09-03

### Fixed
- Defined the standardized `Environment` snapshot as a single-valued mapping
  under the represented environment's native name-identity rules.
- Required `process.environment()` to fail when the native bootstrap environment
  contains duplicate-equivalent names rather than selecting an
  implementation-dependent first/last winner.
- Made duplicate detection use native name identity, preserving POSIX-like
  case-sensitive distinctions and rejecting Windows-like case-insensitive
  duplicates.
- Rejected duplicates even when their values are equal, because callback count,
  retained spelling, and native-boundary behavior would still be ambiguous.
- Prevented host enumeration order, case-preserving spelling, or runtime
  deduplication policy from becoming accidental portable Protos semantics.
- Kept raw/ambiguous host environment representations available only through a
  separately defined host-specific/native boundary.

### Changed
- Synchronized all revisioned specification documents to revision 170. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.169] - 2026-09-03

### Fixed
- Corrected Future waiter bookkeeping so a waiter denotes the suspended execution
  continuation rather than assuming every Future observer is a `Task`.
- Preserved `Future.value()` suspension for ordinary Actor/root/bootstrap
  execution contexts without manufacturing hidden task/Future identities.
- Kept cooperative cancellation associated only with task-backed waiting
  executions while retaining the existing cancellation-runnable semantics.
- Preserved the atomic waiter-registration/terminal-transition race rule for both
  task-backed and non-task-backed Future observers.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 169.

## [0.1.168] - 2026-09-03

### Fixed
- Defined `remove(key)` as the standard keyed-removal selector for `Map`
  and `IdentityMap`.
- Required successful removal to return the exact previously stored value.
- Required a successful no-match search to signal an Error rather than
  returning `null`, `false`, or an implementation sentinel.
- Composed removal with the existing deterministic key search,
  open/closed/frozen failure timing, and post-callback state revalidation.
- Preserved `containsKey(key)` as the non-failing presence query and kept
  `NOT_FOUND` strictly internal to runtime control flow.

### Changed
- Synchronized all revisioned specification documents to revision 168.
  No grammar, Actor/Future, or I/O semantics change.

## [0.1.167] - 2026-09-03

### Fixed
- Defined each available stdin/stdout/stderr binding as one Process-local
  logical byte stream for the Process lifetime.
- Required repeated standard-stream accessor calls to preserve that same logical
  input sequence or output-ordering domain rather than opening independent
  semantic streams.
- Kept physical capability-object identity non-normative: implementations may
  return the same object or distinct views/Actor-local proxies.
- Required duplicated native descriptors, rematerialized proxies, or internal
  backend replacement to preserve the binding's logical ordering, state, and
  backpressure semantics.
- Kept stdout and stderr as distinct Protos logical flows even if a host happens
  to route them to the same backend destination.
- Defined repeated standard-stream Encoding accessor results by equivalent
  descriptor semantics rather than object identity.
- Aligned the multi-Actor stdin rule with repeated accessor results so every
  capability for the binding shares one ByteReadable consumption domain.

### Changed
- Synchronized all revisioned specification documents to revision 167. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.166] - 2026-09-03

### Fixed
- Closed the lost-wakeup race between observing a pending Future and registering
  the current task as a waiter.
- Defined Future waiter registration and the first terminal transition as one
  semantic race: either completion wins and no suspension persists, or waiter
  registration wins and completion wakes that waiter.
- Required the explicit-suspension cancellation boundary to run before installing
  a live Future waiter.
- Allowed locks, CAS, generation counters, register-then-recheck, or equivalent
  implementation strategies while forbidding observable lost notifications.
- Preserved stable Future observation by re-entering the ordinary Future-state
  switch after a wait or a completion-won race.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 166.

## [0.1.165] - 2026-09-03

### Fixed
- Closed a second-order Map object-state hole caused by callback-capable
  key search: an initial open/not-frozen check no longer authorizes a later
  keyed-entry mutation after `hash` or `==` changes the Map state.
- Required `atPut` to revalidate state immediately before replacing a
  matched value; a callback-induced `freeze()` therefore prevents the
  replacement while callback-induced `close()` still permits replacement.
- Kept the existing post-search insertion check authoritative, so a
  callback-induced `close()` or `freeze()` prevents a no-match insertion.
- Required keyed-entry removal to check permission both before search and
  again immediately before removing a matched entry.
- Preserved completed callback effects, existing Map comparison guards, and
  callback-free IdentityMap optimization freedom without adding locks,
  snapshots, or transactions.

### Changed
- Synchronized all revisioned specification documents to revision 165.
  Updated 3 canonical runtime entry-update path(s). No grammar,
  Actor/Future, or I/O semantics change.

## [0.1.164] - 2026-09-03

### Fixed
- Defined the observable runtime contract of `wakeWaiters(future)`.
- Required all still-registered waiters of a Future's first terminal transition
  to become eligible for resumption.
- Required waiter registrations to be cleared or made inert after terminal
  transition so terminal Futures do not retain suspended-task continuations
  indefinitely.
- Prevented duplicate semantic resumption from repeated terminal wake-up
  bookkeeping.
- Clarified that wake-up makes a waiter runnable but does not bypass cancellation
  observation or weak-fairness scheduling.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 164.

## [0.1.163] - 2026-09-03

### Fixed
- Generalized finite speculative read-ahead retention from buffering/text
  adapters to every standard `ByteReadable` implementation.
- Required Protos-managed unread bytes/native-read results retained solely by
  implementation-chosen read-ahead to have an effective finite per-flow bound.
- Clarified that `maxBytes` bounds the successful Protos result but need not
  equal the size of one native/backend read, preserving bounded prefetch,
  batching, and shared-buffer implementations.
- Prevented repeated small reads from an open-ended source from authorizing
  unbounded implementation-controlled unread retention.
- Distinguished underlying ByteReadable speculation from memory intrinsically
  required by higher-level operations such as unbounded `readLine()`.
- Kept host-kernel, filesystem-cache, device, peer, and independently managed
  backend buffering outside the Protos-managed retention guarantee.
- Recast the existing TextReader/BufferedReader rule as a specialization of the
  general ByteReadable invariant rather than a separate resource model.

### Changed
- Synchronized all revisioned specification documents to revision 163. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.162] - 2026-09-03

### Fixed
- Removed stale `ByteReadable` cancellation/failure wording that still claimed
  all pending reads were globally ordered by invocation.
- Made preservation after cancellation/failure follow the same input-consumption
  order already defined for the receiver: Protos invocation order where one
  exists, otherwise the stable routing/admission order chosen for genuinely
  concurrent cross-Actor reads.
- Prevented section 5.1 from contradicting the cross-Actor ordering semantics
  introduced for shared ByteReadable receivers.
- Preserved the existing no-bypass rule for bytes retained after a failed or
  cancelled earlier read.

### Changed
- Synchronized all revisioned specification documents to revision 162. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.161] - 2026-09-03

### Fixed
- Defined how standard `Map` and `IdentityMap` keyed-entry mutation composes
  with the existing open/closed/frozen object-state model.
- Made closed Maps permit replacement of existing mapped values while
  rejecting entry insertion and removal; frozen Maps reject every keyed-entry
  mutation.
- Defined deterministic failure timing: frozen `atPut` fails before key
  search, closed `atPut` searches because update may succeed, and closed/frozen
  keyed removal fails before search.
- Kept `close()`/`freeze()` shallow: keys and values are not recursively
  closed or frozen, and read-only lookup/presence operations remain valid.
- Preserved ordinary user-defined indexed protocols; these state rules are
  the standard Map/IdentityMap contract rather than syntax-wide magic.

### Changed
- Synchronized all revisioned specification documents to revision 161.
  No grammar, Actor/Future, or I/O semantics change.

## [0.1.160] - 2026-09-03

### Fixed
- Defined one logical input-consumption ordering domain for outstanding
  `ByteReadable.read` operations on the same logical receiver.
- Preserved reads issued sequentially by one Actor in that Actor's invocation
  order even while earlier read Futures remain pending.
- Defined genuinely concurrent cross-Actor reads as having no predetermined
  relative order, while requiring routing/admission to establish one stable
  order once chosen.
- Prevented a later chosen read from bypassing an earlier chosen pending read to
  consume earlier bytes merely because a host/native operation completes first.
- Applied the same rule explicitly to standard input delegated through distinct
  Actor-local proxies.
- Kept native syscall overlap, buffering, prefetch, and routing as implementation
  freedoms when they cannot change logical byte/EOF/failure assignment.

### Changed
- Synchronized all revisioned specification documents to revision 160. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.159] - 2026-09-03

### Fixed
- Defined cancellation of a not-yet-started asynchronous task to make that task
  cancellation-runnable even when an unmet semantic prerequisite was preventing
  its first turn.
- Closed the `Future.then()` liveness hole where cancelling a continuation could
  otherwise remain pending forever while its source Future never completed.
- Required a cancelled pre-start `then()` continuation to reach the existing
  first-execution cancellation boundary without inspecting or modifying its
  still-pending source Future.
- Generalized cancellation readiness through an idempotent conceptual scheduler
  operation that also covers already-suspended tasks without duplicate semantic
  execution.
- Preserved cooperative cancellation: currently executing non-suspending Protos
  code is not preempted and acquires no hidden cancellation boundary.
- Preserved downstream-only cancellation: making a task cancellation-runnable
  does not satisfy or cancel its unrelated prerequisite or upstream Future.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 159.

## [0.1.158] - 2026-09-03

### Fixed
- Bounded implementation-controlled speculative read-ahead retention for
  standard buffering/decoding readers.
- Required unread bytes, decoded text, checkpoints, and equivalent state retained
  solely because of speculative read-ahead to have an effective finite
  implementation bound per logical reader/flow.
- Distinguished speculative retention from intrinsically unbounded operation
  results: ordinary `readLine()` may still require unbounded line accumulation.
- Prevented `readLine(maxBytes)` from using its explicit safety bound while
  continuing to consume/retain an unbounded suffix after the limit condition can
  already be established.
- Kept `readText()` chunk boundaries implementation-selectable while prohibiting
  an ever-growing implementation-chosen chunk as a prerequisite for progress.
- Applied the same principle to `BufferedReader` and other standard read
  adapters without prescribing one fixed buffer size or one native read at a
  time.
- Scoped the resource rule to Protos-managed retention rather than kernel,
  filesystem-cache, remote-peer, or independently managed backend buffering.

### Changed
- Synchronized all revisioned specification documents to revision 158. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.157] - 2026-09-03

### Fixed
- Replaced the ambiguous `shutdownRead()` "wins the ordering race" wording with
  one receiver-visible read-shutdown cutover point.
- Required read results committed before cutover to survive and every accepted
  but still-uncommitted read at cutover to complete as local EOF (`null`).
- Clarified that read shutdown intentionally terminates earlier pending,
  uncommitted reads rather than waiting behind them like write shutdown.
- Prevented host/native callback scheduling from deciding whether a read belongs
  before or after the shutdown boundary.
- Preserved the absence of a global cross-Actor arrival order: routing/admission
  may determine which competing operation reaches the receiver first, but the
  established cutover is stable.
- Allowed bytes obtained internally before result commitment to be discarded by
  shutdown without later escaping through the terminated receiver.

### Changed
- Synchronized all revisioned specification documents to revision 157. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.156] - 2026-09-03

### Fixed
- Unified Core Future cancellation-request recording through one conceptual
  runtime operation used by both `Future.cancel()` and structured cancellation.
- Aligned executable pseudocode with the normative rule that cancelling an
  already-suspended task makes that task runnable for cancellation.
- Prevented repeated cancellation requests from scheduling duplicate semantic
  cancellation events.
- Removed the undefined edge-trigger-like `onCancellationRequest` dependency
  from Future adoption.
- Defined pending Future adoption to observe a cancellation request whether the
  request is recorded before or after adoption installs its dependency, avoiding
  a lost-cancellation race.
- Preserved downstream-only adoption cancellation: cancelling the destination
  never cancels the adopted source.
- Preserved producer-specific cancellation for non-task Futures such as I/O;
  recording a request does not itself invent a cancelled outcome where the
  producer's commitment contract does not permit one.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 156.

## [0.1.155] - 2026-09-03

### Fixed
- Defined missing-key lookup for standard `Map` and `IdentityMap`: `at(key)`
  signals an Error after a successful no-match search rather than returning
  `null`, `false`, or an implementation sentinel.
- Defined `containsKey(key)` as the non-failing presence query returning
  canonical `true`/`false` independently of the stored value.
- Made mappings to `null`, `false`, and every other ordinary Protos object
  observably distinct from key absence.
- Kept pseudocode `NOT_FOUND` strictly internal and preserved existing
  normal-Map hash/equality effects and IdentityMap callback-free search.

### Changed
- Synchronized all revisioned specification documents to revision 155.
  No grammar, Actor/Future, or I/O semantics change.

## [0.1.154] - 2026-09-03

### Fixed
- Defined deterministic failure precedence for explicit owning-wrapper close.
- Made wrapper finalization/propagation failure the primary wrapper-close failure
  when it occurs before the mandated owned-target close step.
- Required the owned target's close lifecycle to still be invoked and committed
  before exposing that wrapper-finalization failure.
- Prevented a later owned-target close failure from replacing an already
  established wrapper-finalization failure.
- Defined target-close failure as the wrapper-close failure when wrapper
  finalization succeeds.
- Avoided introducing a universal aggregate/suppressed-error institution;
  secondary cleanup failures may be retained only through separately specified
  diagnostic facilities.
- Allowed the wrapper close Future to report its established primary failure
  after owned-target close has been committed, without waiting solely for that
  target close lifecycle to become terminal.
- Kept release custody with the target's ordinary `Closable` lifecycle and
  prohibited skipping owned-target close for fail-fast behavior.

### Changed
- Synchronized all revisioned specification documents to revision 154. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.153] - 2026-09-03

### Fixed
- Defined successful standard `Map.atPut(key, value)` and
  `IdentityMap.atPut(key, value)` to return the exact supplied `value`
  regardless of whether the operation inserts or replaces an entry.
- Removed the normal-Map pseudocode's observable previous-value/`ABSENT`
  return split, which relied on an undefined non-language absence sentinel
  and differed from `IdentityMap`.
- Kept bracket assignment independently defined to evaluate to the assigned
  value and left user-defined `atPut` direct-return contracts unconstrained
  unless another normative protocol specifies them.

### Changed
- Synchronized all revisioned specification documents to revision 153.
  No grammar, Actor/Future, or I/O semantics change.

## [0.1.152] - 2026-09-03

### Fixed
- Aligned executable `honorCancellation` pseudocode with the already normative
  cancellation-safe `ensure` semantics.
- Made a cleanup error during cancellation fail the task Future instead of being
  overwritten by an unconditional `CANCELLED` completion.
- Preserved `CANCELLED` as the terminal outcome only when the cancellation unwind
  and every applicable cleanup scope complete without a superseding error.
- Reused the existing `ErrorTransfer` and `failFuture` machinery rather than
  introducing another cancellation-specific outcome path.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 152.

## [0.1.151] - 2026-09-03

### Fixed
- Defined object reachability as separate from I/O lifecycle: becoming
  unreachable or GC-eligible does not semantically invoke `close()`.
- Prohibited programs from relying on GC, reference-count transitions, heap
  pressure, safepoints, or finalizer timing for deterministic resource release.
- Required deterministic release effects to come from explicit lifecycle
  mechanisms such as `close()` or a separately normative structured-cleanup
  facility.
- Allowed best-effort reclamation of unreachable native/backend resources only
  as implementation/host cleanup, not as a second Protos close lifecycle.
- Prohibited such cleanup from executing arbitrary Protos user code, fabricating
  close outcomes, or surfacing close errors to unrelated code.
- Made externally visible timing of emergency unreachable-resource reclamation
  explicitly non-portable, so lock/descriptor/socket release cannot be used as
  synchronization without explicit close.
- Clarified that owning wrappers do not close owned targets merely because the
  wrapper becomes unreachable.
- Kept resource-exhaustion behavior from abandoned resources outside portable
  reclamation-timing guarantees.

### Changed
- Synchronized all revisioned specification documents to revision 151. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.150] - 2026-09-03

### Fixed
- Defined cancellation of an already-suspended task to make that task runnable
  for cancellation without waiting for its original suspension condition.
- Prevented structured cancellation from becoming permanently stuck solely
  because a child is suspended on a Future, timer, I/O operation, or other
  condition that never becomes ready.
- Defined suspended-consumer cancellation as downstream-only: waking/cancelling
  the waiter does not implicitly cancel or otherwise modify the awaited Future or
  its producer.
- Defined the resume race through the existing portable resume boundary:
  cancellation pending at that boundary is honored before a successful suspended
  result can be delivered to consumer code.
- Clarified that awaited Future completion remains stable and cannot re-enter or
  rewrite a consumer task that has cancelled.
- Permitted eager waiter removal or inert bookkeeping while forbidding unbounded
  retention of dead waiters.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 150.

## [0.1.149] - 2026-09-03

### Fixed
- Made `Environment.each(block)` prevalidate complete portable `(String, String)`
  representability before invoking user code.
- Required an invalid native environment name/value to fail an enumeration with
  zero callback invocations instead of exposing a host-order-dependent prefix of
  otherwise valid entries.
- Preserved unspecified iteration order for fully representable environments.
- Scoped failure atomicity to Environment-to-String representation validation;
  errors/non-local effects raised by the user block retain ordinary callback
  semantics and do not roll back prior callbacks.
- Kept the rule representation-neutral: eager snapshot validation, cached
  validation, retained native entries, or first-enumeration validation remain
  valid implementations.
- Preserved the separate host/native boundary for lossless access to environment
  entries that portable Protos String cannot represent.

### Changed
- Synchronized all revisioned specification documents to revision 149. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.148] - 2026-09-03

### Fixed
- Removed stale host-process wording that contradicted the newly defined
  Protos-execution scope of observable standard hashes.
- Defined `identityHashOf` for Core value-identity categories on the same
  Protos-execution semantic scope, preventing Actor/process/worker/machine
  placement from changing the identity hash of the same semantic value.
- Preserved local identity-hash allocation/caching for identity-bearing
  objects and clarified that pass-by-value copies with new identity need not
  preserve the source object's identity hash.
- Avoided any requirement for a global mutable identity-hash registry or
  global lock; immutable execution-scoped configuration remains sufficient
  for value-identity hashing.

### Changed
- Synchronized all revisioned specification documents to revision 148.
  No grammar, Actor/Future, or I/O semantics change.

## [0.1.147] - 2026-09-03

### Fixed
- Replaced the vague concrete-receiver ordering sentence for `ByteSized` /
  `Truncatable` with one normative sequence-state ordering domain.
- Required reads, writes, append writes, position/seeks, size, truncate, and
  relevant flush/sync frontiers on one logical byte-sequence receiver to
  preserve every Protos-defined invocation order.
- Required `size()` after an earlier ordered write to observe the state produced
  by that write before the size query's evaluation, rather than bypassing a
  still-pending Future.
- Required later truncate/seek/size operations to evaluate against all earlier
  ordered sequence-state effects.
- Composed the shared ordering rule with each operation's existing
  cancellation/failure aftermath instead of inventing rollback.
- Defined genuinely concurrent Actor-routed sequence operations as unordered
  initially but stably ordered once the receiver/routing layer chooses.
- Preserved implementation freedom for pipelining, batching, speculative work,
  positional native APIs, and external independently authorized changes.

### Changed
- Synchronized all revisioned specification documents to revision 147. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.146] - 2026-09-03

### Fixed
- Replaced host-process-scoped hash wording with a Protos-execution-scoped
  observable hash domain.
- Required standard built-in value hashes to remain coherent across Actors,
  workers, operating-system processes, and machines participating in the same
  Protos execution rather than leaking host placement into language semantics.
- Renamed conceptual `processLocalHashInteger` to
  `executionLocalHashInteger` to match the semantic scope.
- Preserved per-execution salting while allowing additional per-Map, per-Actor,
  per-worker, or per-process mixing for unobservable physical table layout.
- Avoided requiring a global mutable hash registry or global lock; immutable
  execution-scoped configuration or equivalent mechanisms remain sufficient.

### Changed
- Synchronized all revisioned specification documents to revision 146.
  No grammar, Actor/Future, or I/O semantics change.

## [0.1.145] - 2026-09-03

### Fixed
- Put `readText`, `readLine`, and `readLine(maxBytes)` on one logical
  TextReader decoded-input operation-ordering domain.
- Required sequentially ordered text reads to consume/evaluate input in their
  Protos invocation order even when multiple Futures are simultaneously pending.
- Defined genuinely concurrent Actor-routed text reads as having no predetermined
  relative order, while requiring the chosen order to become stable.
- Prevented mixed readText/readLine operations from racing independent decoder,
  buffering, or line-framing states.
- Composed ordering with existing cancellation semantics: a cancelled earlier
  operation consumes zero text and the next operation receives the same earliest
  logical input.
- Composed ordering with permanent TextReader failure: later outstanding reads
  cannot bypass an earlier committed decoding/I/O/line-too-long failure.
- Preserved implementation freedom for speculative read-ahead, buffering,
  decoding, and pipelining when observable result assignment remains identical.

### Changed
- Synchronized all revisioned specification documents to revision 145. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.144] - 2026-09-03

### Fixed
- Defined `Future.detach()` as an idempotent ownership operation that always
  returns the same Future object.
- Defined successful detachment to remove only the structured activation-owner
  edge of a still-pending task-backed Future.
- Defined repeated `detach()` calls as state-preserving no-ops.
- Defined `detach()` on non-task-backed Futures, including direct I/O Futures, as
  a no-op because no structured task ownership edge exists.
- Defined `detach()` on already terminal Futures as a no-op.
- Prevented detachment from implicitly cancelling, re-parenting, abandoning, or
  otherwise altering a non-task producer or Future terminal outcome.
- Preserved Actor-local lifetime: detachment still does not escape the Actor
  execution/lifecycle domain.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 144.

## [0.1.143] - 2026-09-03

### Fixed
- Removed the contradiction between the general File capability-shape rule and
  section 18.4's unconditional `ByteSeekable`/`ByteSized`/`Truncatable`
  requirements.
- Made read/write open mode guarantee only the corresponding `ByteReadable` /
  `ByteWritable` access capability plus `Closable`.
- Made `ByteSeekable`, `ByteSized`, `Truncatable`, and `Syncable` conditional on
  the backend being able to satisfy each protocol's normative semantics.
- Clarified that truncate-on-open support does not imply later `Truncatable`
  support on the returned File.
- Required a File's exposed capability set to remain stable for that File's
  lifetime rather than varying according to whether an individual operation
  happens to be usable at the moment.
- Prohibited advertising an optional protocol merely because the host has a
  similarly named primitive when its normative Protos contract cannot be met.

### Changed
- Synchronized all revisioned specification documents to revision 143. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.142] - 2026-09-03

### Fixed
- Defined receiver-domain validation for standard behavior whose semantics
  require membership in a built-in semantic value family.
- Made delegation to a Number, String, or other family value/prototype
  insufficient to grant that semantic-family membership.
- Required incompatible receivers to signal an Error after ordinary lookup
  selects the behavior, without ancestor fallback or implicit coercion.
- Kept user-defined overrides ordinary and allowed standard behavior to be
  generic only when its own normative contract explicitly says so.
- Applied the rule explicitly to standard Number-family hashing and
  family-specific arithmetic/comparison behavior while preserving
  optimization freedom when receiver membership is already proven.

### Changed
- Synchronized all revisioned specification documents to revision 142.
  No grammar, Actor/Future, or I/O semantics change.

## [0.1.141] - 2026-09-03

### Fixed
- Defined detachment as removal from activation-structured ownership only, not
  escape from the Actor execution/lifecycle domain.
- Defined that detached Actor-local tasks may outlive their creating activation
  but cannot outlive their Actor incarnation.
- Required semantic Actor termination, while runtime cleanup remains executable,
  to request cooperative cancellation of every pending Actor-local task,
  including detached tasks.
- Prevented detached tasks from being silently re-parented to RootActor, Process,
  replacement Actors, or another execution domain after Actor termination.
- Reused the existing cancellation/`ensure` rules for Actor-termination cleanup:
  successful cleanup yields `cancelled`, while cleanup failure yields `failed`.
- Clarified that Actor replacement inherits no task/Future from the terminated
  incarnation.
- Scoped the cleanup guarantee so catastrophic loss of the hosting execution
  substrate cannot imply impossible cleanup, while still forbidding task
  continuation in another domain.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 141.

## [0.1.140] - 2026-09-03

### Fixed
- Defined invocation-time capture of the complete semantic configuration for
  `filesystem.open`.
- Prevented later mutation of a mutable options builder/object from changing an
  already-invoked open's access, creation, truncation, append, or resulting File
  capability shape.
- Required option-combination validation to use the captured configuration.
- Kept option capture separate from I/O commitment so snapshotting configuration
  does not itself prevent cancellation before filesystem effects or File-result
  commitment.
- Made the rule representation-neutral and prohibited hidden caller-visible
  borrow/freeze requirements while an open Future is pending.

### Changed
- Synchronized all revisioned specification documents to revision 140. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.139] - 2026-09-03

### Fixed
- Defined append-mode logical-position aftermath for successful, failed, and
  cancelled writes using the existing ByteWritable contributed-prefix model.
- Required zero-contribution failed/cancelled appends to leave logical position
  unchanged even if a backend cursor was tentatively moved to EOF.
- Required any append that contributes bytes to leave logical position
  immediately after that operation's last contributed byte, including when the
  write Future later fails.
- Defined successful zero-length append as position-preserving.
- Prevented external-writer interleaving from being incorrectly modeled as
  `oldPosition + k` or `initialEOF + k`; later external changes do not
  retroactively alter an already established logical position.
- Kept backend-dependent external-writer atomicity outside the portable promise
  while making the Protos handle's own position deterministic.

### Changed
- Synchronized all revisioned specification documents to revision 139. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.138] - 2026-09-03

### Fixed
- Defined deterministic `IdentityMap` key search using primitive `identityHashOf`
  and `===`, independent of physical hash-table layout.
- Defined existing-key update to replace only the value while retaining the
  representative key and insertion position.
- Defined removal followed by reinsertion of the same semantic key as a new
  insertion at the end of observable insertion order.
- Applied the same identity-key search semantics to lookup, containment,
  removal, and indexed insertion/update while preserving implementation
  freedom for physical hashing and caching.
- Kept `IdentityMap` matching free of ordinary Protos callback dispatch and
  separate from normal `Map` equality-callback reentrancy semantics.

### Changed
- Synchronized all revisioned specification documents to revision 138.
  No grammar, Actor/Future, or I/O semantics change.

## [0.1.137] - 2026-09-03

### Fixed
- Made `GroupRef` explicitly transferable through ordinary Actor/Process
  pass-by-value message transfer.
- Defined transferred GroupRefs as preserving the same concrete Group target and
  the same effective communication capability/restrictions without authority
  amplification.
- Clarified that GroupRef transfer copies/transfers only the communication
  capability, never mutable membership, routing, controller, policy, or
  control-plane state.
- Preserved the existing distinction between GroupRef object identity and Group
  identity, allowing implementation-local reference representations.
- Defined that transferred GroupRefs do not extend Group lifetime and never
  retarget after Group termination or discovery-name rebinding.
- Aligned GroupRef with the complete transfer-graph capability rule already used
  by ActorRef.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 137.

## [0.1.136] - 2026-09-03

### Fixed
- Defined permanent release custody transfer when `close()` begins: a failed
  close never gives the program an open/retryable resource back through the
  same receiver.
- Made explicit that close failure does not portably prove whether a backend
  resource is still open or already released when the backend reports an
  uncertain release outcome.
- Prohibited blind retry of native close/release using an identifier that might
  already have been released and reused for an unrelated resource.
- Allowed a native retry only when backend state/contract establishes that the
  identifier still denotes the same resource and retry is safe.
- Kept residual backend release state and bookkeeping under implementation/host
  custody rather than requiring a later program `close()` call for safety.
- Clarified that stronger effects such as durability remain separate protocols
  such as `sync()` rather than being inferred from close success/failure.

### Changed
- Synchronized all revisioned specification documents to revision 136. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.135] - 2026-09-03

### Fixed
- Clarified that the pre-comparison `hash` phase of a Map search does not
  suspend an already-active same-Map comparison restriction established by
  an enclosing equality callback.
- Prevented nested same-Map lookups from using their `hash` callback to mutate
  the Map and bypass the reentrant-mutation rule.
- Preserved ordinary outermost behavior: when no enclosing comparison scope
  exists, query-key `hash` effects still complete before candidate traversal
  and may mutate the target Map according to ordinary semantics.

### Changed
- Synchronized all revisioned specification documents to revision 135.
  No grammar, Actor/Future, or I/O semantics change.

## [0.1.134] - 2026-09-03

### Fixed
- Removed stale normative wording that described `IdentityMap` in terms of the
  overridable `identityHash` message after semantic identity hashing had already
  been moved to the primitive `identityHashOf` operation.
- Made all `IdentityMap` machinery wording consistently refer to
  `identityHashOf` together with primitive `===`.
- Preserved the separate ordinary `identityHash()` convenience message for
  explicit source-level dispatch only; no observable semantics are changed from
  the already-defined primitive identity-hash boundary.

### Changed
- Synchronized all revisioned specification documents to revision 134.
  No grammar, Actor/Future, or I/O semantics change.

## [0.1.133] - 2026-09-03

### Fixed
- Defined `TextWriter.writeText` and `writeLine` as ordered logical text-write
  operations, with `writeLine` text plus LF forming one non-interleaved operation.
- Required complete encoding validation before any byte from a text-write
  operation becomes observable at the target.
- Made strict encoding failure failure-atomic: it contributes zero output bytes
  and preserves the pre-operation per-flow encoder state.
- Kept this guarantee representation-neutral: implementations may validate,
  checkpoint, dry-run, or stage encoding without requiring one native write or
  mandatory eager full-output allocation.
- Defined text-write cancellation to win only while both zero output contribution
  and the pre-operation encoder state can still be preserved.
- Reused the existing ByteWritable/wrapper unknown-progress rules for downstream
  failures after output commitment rather than inventing TextWriter-specific
  retry heuristics.

### Changed
- Synchronized all revisioned specification documents to revision 133. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.132] - 2026-09-03

### Fixed
- Defined `adoptFuture` and made Future flattening a complete normative
  outcome-adoption operation rather than undefined runtime pseudocode.
- Defined resolved, failed, and cancelled adopted-source propagation.
- Made adoption one-way: destination cancellation, ownership, and detachment do
  not propagate to the adopted source.
- Made pending Future adoption cancellation-aware so a destination can complete
  cancelled after its initiating task body has returned.
- Defined source-completion versus destination-cancellation races by the first
  terminal transition of the destination.
- Added conceptual `adoptedSource` bookkeeping without adding a fifth Future
  state or a language-visible slot.
- Defined direct and transitive adoption cycles to fail with the standard
  `FutureResolutionCycle` error instead of remaining indefinitely pending.
- Required adoption bookkeeping not to execute ordinary Protos code inline.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 132.

## [0.1.131] - 2026-09-03

### Fixed
- Defined reentrant mutation of a Map's keyed-entry state during that Map's
  user-defined key-equality callback to signal an Error before mutation.
- Scoped the restriction to the particular Map and comparison dynamic extent,
  preserving unrelated Map mutation and read-only same-Map operations.
- Defined query-key `hash` effects to complete before candidate traversal, so
  target-Map mutations performed by `hash` are observed deterministically by
  the subsequent search.
- Preserved mutable-key semantics while preventing live hash-table iterator,
  rehashing, or bucket-layout behavior from leaking into language semantics.
- Avoided requiring snapshots, global locks, Actor-wide locks, or permanent
  per-entry reentrancy metadata.

### Changed
- Synchronized all revisioned specification documents to revision 131.
  No grammar, Actor/Future, or I/O semantics change.

## [0.1.130] - 2026-09-03

### Fixed
- Extended TextReader cancellation semantics from line reads to all standard
  text-reading operations, including `readText()`.
- Required successful cancellation to consume zero logical text and preserve the
  remaining decoded sequence and line-framing state.
- Required internally fetched bytes, decoded characters, partial encoded
  characters, decoder/BOM state, and read-ahead to be retained/reconciled rather
  than becoming text loss, duplication, reordering, or a spurious later error.
- Clarified that zero-consumption applies at the TextReader logical-text layer;
  ordinary wrapper read-ahead does not imply rewinding a separately accessible
  wrapped byte source.
- Defined `readText()` result commitment consistently with its intentionally
  unspecified chunk boundaries, while keeping successful cancellation
  non-poisoning and committed I/O/decoding failures subject to the existing
  permanent TextReader failure lifecycle.

### Changed
- Synchronized all revisioned specification documents to revision 130. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.129] - 2026-09-03

### Fixed
- Added a portable cancellation-observation boundary before the first ordinary
  Protos instruction of every newly created asynchronous task.
- Defined cancellation requested before a `then()` continuation starts to cancel
  that continuation before inspecting the source result or invoking `transform`.
- Prevented scheduler timing from making a never-started cancelled continuation
  execute observable Protos side effects in one implementation but not another.
- Preserved the existing rule that, after first task execution begins, ordinary
  non-suspending code does not gain hidden cancellation checkpoints.
- Kept `then()` cancellation downstream-only: cancelling the destination still
  does not cancel or change ownership of the source Future.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 129.

## [0.1.128] - 2026-09-03

### Fixed
- Defined `seek`, `seekBy`, and `seekToEnd` as failure-atomic with respect to the
  receiver's logical sequence position.
- Required failed and successfully cancelled seeks to leave logical position
  unchanged even when a backend cursor moved tentatively.
- Defined `seekBy` and `seekToEnd` targets at the seek's ordered evaluation point
  so concurrent/earlier position or size effects cannot be interpreted
  differently by independent implementations.
- Defined seek commitment only when the complete logical position change is
  established; after commitment the seek succeeds rather than exposing the new
  position behind a failed Future.
- Required implementations to restore, virtualize, or reconcile weaker backend
  cursor behavior, or else not expose standard `ByteSeekable`.
- Clarified that a failed `position()` query has no position-changing effect.

### Changed
- Synchronized all revisioned specification documents to revision 128. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.127] - 2026-09-03

### Fixed
- Made the observable return value of `Future.cancel()` normative: it returns
  the same Future object.
- Defined repeated cancellation requests on a pending Future as idempotent.
- Defined `cancel()` on resolved, failed, or already-cancelled Futures as a
  state-preserving no-op that still returns the same Future.
- Clarified that a normal return from `cancel()` acknowledges request recording,
  not successful cancellation, a guaranteed `cancelled` terminal state, or
  reversal of already-committed effects.
- Removed the implementation-defined escape hatch that allowed the standard
  protocol to choose a different `cancel()` result.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 127.

## [0.1.126] - 2026-09-03

### Fixed
- Defined standard `Truncatable.truncate` as failure-atomic with respect to the
  sequence size and contents changed by that operation.
- Prohibited a failed truncate from leaving a partially truncated observable
  sequence state.
- Defined truncation commitment only when the complete requested size-reduction
  effect is established; after that point cancellation cannot undo it and the
  truncate operation completes successfully.
- Kept no-extension truncation requests as successful no-ops.
- Required backends unable to provide or emulate failure-atomic truncation not to
  expose standard `Truncatable` merely because the host offers a similarly named
  primitive.
- Kept independently authorized concurrent resource changes outside the
  truncate operation's own failure-atomicity guarantee.

### Changed
- Synchronized all revisioned specification documents to revision 126. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.125] - 2026-09-03

### Fixed
- Defined the existing no-starvation promise as weak fairness over continuously
  runnable eligible work.
- Defined when Actor-local continuations and accepted mailbox messages count as
  runnable for the fairness rule.
- Extended the same weak-fairness obligation to continuously runnable live
  Actors at scheduler-selection boundaries.
- Defined that later-arriving work cannot starve a continuously runnable item.
- Explicitly excluded equal-share, round-robin, bounded-latency, bounded-turn,
  and strong-fairness guarantees.
- Scoped the liveness guarantee to situations where execution continues to
  regain applicable scheduler control, preserving the existing non-preemptive
  semantics of a non-yielding Protos segment.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 125.

## [0.1.124] - 2026-09-03

### Fixed
- Closed the accidental networking-semantics gap around the standard `Socket`
  capability shape by defining it only as an already-provisioned connected
  byte-stream endpoint.
- Made socket creation, connect/bind/listen/accept, datagram addressing, DNS/name
  resolution, transport configuration, and network-authority acquisition
  explicitly outside the v0.1 I/O model.
- Prevented possession of one Socket from implying authority to create sibling
  sockets, reconnect elsewhere, resolve names, inspect ambient network state, or
  bypass Process-host policy.
- Required a future networking model to make authority and host-dependent
  namespace behavior explicit rather than inheriting BSD/POSIX, WinSock, Java,
  or ambient DNS behavior as Protos semantics.

### Changed
- Synchronized all revisioned specification documents to revision 124. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.123] - 2026-09-03

### Fixed
- Defined Number-family `hash` behavior by numeric semantic equality rather than
  by the identity-based `Object.hash` default.
- Required equal numeric values to have equal normal hashes across Integer,
  fixed-width Integer, and Float families.
- Required `0.0` and `-0.0` to share a normal hash while preserving their
  distinct semantic identities.
- Prevented Float NaN payload/representation details from leaking through
  standard hashing by assigning all Core NaNs one normal numeric hash class.
- Preserved per-execution hash salting, legal collisions, exact-Integer hashing,
  and separate `identityHashOf` semantics for `IdentityMap`.

### Changed
- Synchronized all revisioned specification documents to revision 123.
  No grammar, Actor/Future, or I/O semantics change.

## [0.1.122] - 2026-09-03

### Fixed
- Defined invocation of `shutdownWrite()` and `shutdownRead()` as the irreversible
  commitment boundary for permanent termination of the corresponding direction.
- Prevented Future cancellation or shutdown failure from reopening a direction
  after its shutdown lifecycle has begun.
- Defined deterministic interaction with pending reads: uncommitted reads lose
  the race to read shutdown as local EOF (`null`), while committed results remain
  unchanged.
- Defined post-failure and repeated half-close behavior as one idempotent
  lifecycle rather than implementation-dependent retries.
- Kept input and output half-close lifecycles independent unless a stronger
  concrete protocol explicitly couples them.

### Changed
- Synchronized all revisioned specification documents to revision 122. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.121] - 2026-09-03

### Fixed
- Defined an unhandled initialization error as a fatal failure of that Actor
  incarnation rather than an Actor that remains indefinitely non-READY.
- Defined the fate of messages accepted while an Actor is INITIALIZING.
- Prohibited dispatch, transfer, or transparent replay of those accepted
  operations after initialization failure.
- Reused the ordinary pre-acceptance and Messages Across Actor Failure rules
  rather than creating initialization-specific delivery semantics.
- Defined accepted `request()` operations lost to initialization failure to use
  the existing `RequestOutcomeUncertain` outcome.
- Clarified that a replacement Actor has fresh initialization/mailbox state and
  never inherits accepted messages from the failed incarnation.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 121.

## [0.1.120] - 2026-09-03

### Fixed
- Defined `Object.!=` as ordinary customizable behavior whose default dynamically
  invokes the receiver's current `==` behavior and returns its strict Boolean
  complement.
- Defined error and invalid-result propagation for the default `!=` behavior.
- Defined `!==` as the non-overridable primitive Boolean complement of semantic
  identity `===`, with no user-message dispatch.
- Preserved independent user override of ordinary `!=` without allowing it to
  redefine semantic identity or `IdentityMap`.

### Changed
- Synchronized all revisioned specification documents to revision 120.
  No grammar, Actor/Future, or I/O semantics change.

## [0.1.119] - 2026-09-03

### Fixed
- Replaced implementation-defined `TextReader` recovery after line-too-long,
  decoding, or underlying I/O failure with a deterministic failed text-reading
  lifecycle.
- Prohibited implicit draining/scanning to a later line boundary after an
  overlong line, avoiding hidden unbounded work and backend-dependent recovery.
- Required later `readText()`/`readLine()` calls on a failed TextReader to fail
  without consuming additional source input.
- Kept successful cancellation non-poisoning and preserved the separate
  cancellation/rebuffering contract.
- Preserved wrapper ownership boundaries: TextReader failure does not itself
  close or poison the wrapped byte source, and close/release remains available.

### Changed
- Synchronized all revisioned specification documents to revision 119. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.118] - 2026-09-03

### Fixed
- Defined `Object`'s default ordinary `==` behavior as semantic identity (`===`).
- Defined `Object`'s default ordinary `hash` behavior as `identityHashOf(this)`.
- Ensured ordinary objects are usable as normal Map keys without implicit
  structural equality or structural hashing.
- Kept `==`/`hash` overrideable as ordinary messages while preserving
  non-overridable `===`, `identityHashOf`, and `IdentityMap` semantics.
- Made custom equality/hash coherence remain the responsibility of the behavior
  that overrides the defaults.

### Changed
- Synchronized all revisioned specification documents to revision 118.
  No grammar, Actor/Future, or I/O semantics change.

## [0.1.117] - 2026-09-03

### Fixed
- Defined Actor pass-by-value transferability over the complete transitive value
  graph rather than only over top-level objects.
- Made local-slot references and immutable delegation-parent edges part of the
  transfer graph.
- Required graph-copy semantics to preserve cycles and aliasing without merging
  distinct source identities.
- Defined capability/shared-immutable boundary values as governed by their own
  cross-Actor semantics rather than by copying mutable referent state.
- Added standard `NonTransferableValue` error semantics.
- Made transfer validation atomic before concrete-Actor acceptance, preventing
  partial message delivery or source mutation on validation failure.
- Applied the same transferability rule to `request()` reply values.
- Corrected the stale wording `An request()` to `A request()` when present.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 117.

## [0.1.116] - 2026-09-03

### Fixed
- Defined semantic identity hashing as a non-overridable primitive operation
  `identityHashOf(value)`, matching the non-overridable nature of `===`.
- Required `IdentityMap` to use primitive identity hashing rather than an
  overridable `identityHash` message send.
- Allowed a standard ordinary `identityHash()` convenience message to delegate
  to the primitive while preserving normal user override behavior for explicit
  source-level sends only.
- Prevented user overrides, method lookup, or implementation dispatch choices
  from changing identity-keyed collection behavior.

### Changed
- Synchronized all revisioned specification documents to revision 116.
  No grammar, Actor/Future, or I/O semantics change.

## [0.1.115] - 2026-09-03

### Changed
- Renamed the Actor request/reply operation from `ask()` to `request()`.
- Renamed the standard uncertainty error from `AskOutcomeUncertain` to
  `RequestOutcomeUncertain`.
- Preserved all existing delivery, snapshot, ordering, cancellation, timeout,
  reply, Actor-failure, and uncertainty semantics; this is an API vocabulary
  change, not a behavior change.
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 115.

### Rationale
- `ask` is established actor-framework jargon, especially in Akka, but is not
  self-explanatory outside that ecosystem.
- `call` commonly implies synchronous waiting in Erlang/Elixir and would obscure
  Protos's explicit Future-returning semantics.
- `request` states the semantic role directly, pairs naturally with one-way
  `send`, and is already established for asynchronous request/reply actor APIs.

## [0.1.114] - 2026-09-03

### Fixed
- Defined the `hash` protocol result domain as semantic `Integer` rather than
  leaving Map consumers free to accept host integers, Floats, or arbitrary
  objects.
- Defined invalid hash-result handling before Map mutation and prohibited
  implicit truncation, masking, host-word-size coercion, or Float conversion.
- Defined logical Map recorded hashes as exact mathematical Integer values while
  preserving implementation-private bounded reductions for table layout.
- Defined `identityHash` to return semantic `Integer`, remain stable within one
  execution, and satisfy `a === b => identityHash(a) == identityHash(b)`.
- Kept collisions legal and ordinary hash/identityHash explicitly
  non-persistent across executions.

### Changed
- Synchronized all revisioned specification documents to revision 114.
  No grammar, Actor/Future, or I/O semantics change.

## [0.1.113] - 2026-09-03

### Fixed
- Defined how `ask()` maps communication uncertainty into its ordinary
  four-state Future without inventing a fifth Future state.
- Added the standard `AskOutcomeUncertain` error prototype for cases where no
  normal reply is available and remote effects cannot be ruled out.
- Distinguished definite pre-acceptance failure from uncertain or known
  post-acceptance loss of the reply.
- Defined cancellation before acceptance, cancellation after acceptance,
  cancellation/acceptance races, late replies, and reply/cancellation terminal
  races.
- Kept wait timeout separate from cancellation and from ask-Future completion.
- Defined Actor termination after accepted/possibly accepted asks as
  `AskOutcomeUncertain` rather than pretending non-delivery or transparently
  replaying the request.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 113.

## [0.1.112] - 2026-09-03

### Fixed
- Defined a portable filesystem-independent `Path` value model as rootedness plus
  an ordered component sequence.
- Separated normal names from parent traversal and prohibited lexical collapsing
  across parent components where backend indirection could change resolution.
- Defined Path equality structurally, independent of host case folding, Unicode
  normalization, native prefixes, symlinks, or resource identity.
- Made Path immutable/non-authoritative and safe to transfer under ordinary
  immutable-value Actor rules without transferring Filesystem authority.
- Required Filesystem backends to treat each portable normal component as one
  logical child name, rejecting unrepresentable names rather than reinterpreting
  them as native separators, roots, drives, devices, or authority-changing syntax.
- Kept public parsing/display/native conversion APIs outside v0.1 while forbidding
  them from silently embedding host-native identity rules into portable Path.

### Changed
- Synchronized all revisioned specification documents to revision 112. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.111] - 2026-09-03

### Fixed
- Defined `slotNames()` to return local slot names in canonical ascending
  lexicographic Unicode-scalar order rather than implementation-dependent slot
  table, object-shape, hash, or creation order.
- Defined the returned Array as a snapshot of the local slot-name set.
- Kept delegated slots excluded and made remove/recreate history irrelevant to
  reflection ordering.
- Preserved implementation freedom to keep any internal slot layout and sort
  only when reflection requires it.

### Changed
- Synchronized all revisioned specification documents to revision 111.
  No grammar, concurrency, Future, or I/O semantics change.

## [0.1.110] - 2026-09-03

### Fixed
- Defined the sender used by concrete-Actor FIFO ordering as the originating
  Actor incarnation rather than an Actor-local task, Future, activation, or turn.
- Defined same-sender ordering by Protos-visible communication invocation order
  within that originating Actor.
- Defined explicitly initiated retry attempts to take their ordering position
  when the retry is initiated rather than reclaiming an earlier queue position.
- Prevented host/runtime/bootstrap activity from accidentally acquiring an
  Actor sender identity and hidden ordering guarantees.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 110.

## [0.1.109] - 2026-09-03

### Fixed
- Defined the aftermath of a failed `sync()`: durability already reached is not
  rolled back, while the exact partially durable subset remains unexposed.
- Clarified that synchronization failure alone does not universally poison or
  close the receiver.
- Required a later successful `sync()` to cover its complete later frontier,
  including still-undurable changes that belonged to an earlier failed frontier.
- Defined sync retry as another durability request rather than replay of logical
  data changes, preserving optimization freedom for already-durable state.
- Kept stronger post-failure lifecycle rules explicit to concrete receiver
  contracts rather than inheriting host-specific error conventions.

### Changed
- Synchronized all revisioned specification documents to revision 109. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.108] - 2026-09-03

### Fixed
- Removed implementation-defined behavior after a normal `Map` key's `hash` or
  `==` behavior changes while the key is stored.
- Made an entry's insertion-time recorded hash stable until removal and prohibited
  automatic rehashing, relocation, representative-key replacement, or repair.
- Required all later searches to keep using the deterministic query-hash,
  recorded-hash, insertion-order equality algorithm even for unstable or
  contract-violating keys.
- Defined deterministic behavior for keys that become unreachable by themselves,
  entries that later become equal, and equality/hash-contract violations.
- Preserved mutable keys without hidden freezing or mutation tracking and kept
  optional diagnostics outside ordinary Core semantics.

### Changed
- Synchronized all revisioned specification documents to revision 108.
  No grammar, Actor, Future, or I/O semantics change.

## [0.1.107] - 2026-09-03

### Fixed
- Defined failed ordinary `ByteReadable.read` operations to consume zero bytes
  from the observable input sequence rather than permitting hidden partial
  consumption behind a failed Future.
- Required bytes already obtained before cancellation or failure to be
  preserved/rebuffered as the earliest unread bytes for later logical reads.
- Required shared logical read position to remain unchanged across failed or
  cancelled reads even when a native/backend cursor advanced internally.
- Prevented later outstanding reads from bypassing bytes an earlier failed read
  was required to preserve.
- Clarified that read failure alone does not universally close or poison the
  receiver; later operations follow the concrete receiver state.

### Changed
- Synchronized all revisioned specification documents to revision 107. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.106] - 2026-09-03

### Fixed
- Made the Runtime Future/Task conceptual model internally consistent with the
  already-normative structured-concurrency and cancellation semantics.
- Linked every task-backed Future to its producing Task and vice versa.
- Registered ordinary `closure.future()` work with its creating activation,
  matching the existing structured-ownership rule.
- Made `future.cancel()` and structured child cancellation feed the same
  Future-owned cooperative cancellation request observed at portable task
  cancellation boundaries.
- Linked `then()` continuation Tasks back to their destination Futures.
- Clarified that non-task Future producers such as I/O operations may use the
  same Future cancellation-request state under their own domain contracts.
- Made cancellation requests on already-terminal Futures semantically inert.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 106.

## [0.1.105] - 2026-09-03

### Fixed
- Defined cooperative cancellation as an `ensure`-triggering unwind.
- Prevented an already-honored cancellation request from re-triggering at
  suspension boundaries inside cleanup for that same unwind.
- Allowed asynchronous `ensure` cleanup to suspend while releasing resources.
- Defined cleanup-error precedence over cancellation: cleanup failure replaces
  cancellation and fails the task Future.
- Preserved structured cleanup completion before cancellation becomes terminal.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 105.

## [0.1.104] - 2026-09-03

### Fixed
- Defined the receiver direction of normal `Map` equality as
  `queryKey == storedKey`, eliminating implementation-dependent reversal or
  symmetrization of user-defined equality.
- Defined deterministic insertion-order comparison among same-hash candidate
  entries, so hash-table layout and probing strategy cannot change observable
  user `==` effects, errors, or the selected entry.
- Defined one query-key hash operation per key search/insertion semantic
  operation and conceptual per-entry recorded hashes while preserving
  implementation freedom for observationally equivalent layouts.
- Required equal-key updates to retain the original stored key object, recorded
  hash, and insertion position while replacing only the value.
- Required a mutating Map operation to leave the map unchanged when key hashing
  or comparison signals, without rolling back effects performed by user protocol
  code.
- Kept `IdentityMap` separate and explicitly rejected an implicit `===` shortcut
  in normal `Map` matching.

### Changed
- Synchronized all revisioned specification documents to revision 104.
  No grammar, Actor, Future, or I/O semantics change.

## [0.1.103] - 2026-09-03

### Fixed
- Defined failed-flush propagation as ordered progress through a prefix of the
  flush frontier rather than permitting holes or later-output reordering.
- Prohibited a later flush from duplicating output/effects already propagated by
  an earlier failed flush.
- Closed the generic buffering-adapter recovery ambiguity: when an underlying
  ordinary `ByteWritable.write` fails without revealing enough progress to know
  the exact remaining suffix, the wrapper output side becomes failed/unusable
  instead of guessing, replaying, or dropping bytes.
- Allowed recovery only through a stronger downstream protocol that exposes
  sufficient semantic progress, without depending on host-specific error lore.
- Preserved wrapper ownership rules: output failure does not implicitly close or
  transfer ownership of the wrapped target.

### Changed
- Synchronized all revisioned specification documents to revision 103. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.102] - 2026-09-03

### Fixed
- Defined the observable contribution of a failed `ByteWritable.write` as one
  contiguous prefix of the captured write sequence, including the possible
  zero-length and full-length-prefix cases.
- Made failed-write ordering explicit so later writes cannot precede, fill holes
  inside, or byte-interleave with the failed write's committed prefix.
- Clarified that ordinary `write` does not expose the committed prefix length,
  so failure never makes whole-write retry automatically safe.
- Defined shared-position advancement after partial failed writes and preserved
  committed prefixes for later ordered flush/sync/position-sensitive operations.
- Clarified that write failure alone does not implicitly close or universally
  poison a `ByteWritable`; later operations follow the concrete receiver state.

### Changed
- Synchronized all revisioned specification documents to revision 102. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.101] - 2026-09-03

### Fixed
- Defined `Error.signal()` as the portable zero-argument user signaling protocol.
- Required the signaled receiver itself to belong to the `Error` delegation
  hierarchy and to be the exact object matched and delivered to handlers.
- Made signaling non-returning at the original signaling point under Core's
  unwinding model, whether control transfers to a handler or reaches an outer
  execution boundary.
- Separated runtime semantic signaling from overridable source-level message
  dispatch, so user overrides cannot redefine normative runtime failures.
- Prohibited implicit String/prototype/arbitrary-object coercion into errors and
  language-visible mutation merely for signaling.
- Removed known literal-newline formatting artifacts left by the earlier
  error-taxonomy applier where present.

### Changed
- Synchronized all revisioned specification documents to revision 101.
  No syntax, reserved words, Actor scheduling, or I/O semantics change.

## [0.1.100] - 2026-09-03

### Fixed
- Made Filesystem authority confinement normative for complete path resolution,
  rather than relying only on lexical path interpretation.
- Required parent traversal, absolute forms, symlinks/reparse points, aliases,
  mounts/redirections, and other backend indirections not to escape the
  authorized Filesystem namespace.
- Required uncertain confinement under races or backend limitations to fail
  rather than fall back to ambient host authority.
- Kept virtual, mounted, remote, and mediated filesystems valid by defining the
  boundary in terms of capability authority rather than host-directory ancestry.

### Changed
- Synchronized all revisioned specification documents to revision 100. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.
