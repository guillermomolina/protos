# Protos Language Specification Changelog

All notable changes to the Protos language specification and the concurrency design ledger will be documented in this file.

Specification version follows the document revision number: 0.1.X where X is the revision.

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
- Updated `PROTOS_CONCURRENCY_MODEL.md`.
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
  `PROTOS_CONCURRENCY_MODEL.md`.
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
- Updated `PROTOS_LANGUAGE_SPEC.md` and `PROTOS_CONCURRENCY_MODEL.md`.
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
  `PROTOS_CONCURRENCY_MODEL.md`.
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
  `PROTOS_CONCURRENCY_MODEL.md`.
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
  `PROTOS_CONCURRENCY_MODEL.md`.
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
- Updated `PROTOS_CONCURRENCY_MODEL.md`.
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
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
  `PROTOS_CONCURRENCY_MODEL.md`.
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
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
- Updated `PROTOS_CONCURRENCY_MODEL.md`.
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
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
- Updated `PROTOS_CONCURRENCY_MODEL.md` and, when its current stable semantic
  anchor is present, `PROTOS_RUNTIME_SEMANTICS.md`.
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
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
- Updated `PROTOS_CONCURRENCY_MODEL.md`.
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
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
- Updated `PROTOS_CONCURRENCY_MODEL.md` and, where the current structure permits
  a stable anchor, `PROTOS_RUNTIME_SEMANTICS.md`.
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
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
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
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
  `PROTOS_CONCURRENCY_MODEL.md`, and `PROTOS_IO_MODEL.md` for the byte-region
  authority boundary.
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
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
  `PROTOS_CONCURRENCY_MODEL.md` with the closed physical-sharing boundary.
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
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
  `PROTOS_CONCURRENCY_MODEL.md` with the closed P bootstrap/API semantics.
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
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
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
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
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
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
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
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
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
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
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
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
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
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
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
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
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
  `PROTOS_IO_MODEL.md` to document revision 200.

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
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
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
  `PROTOS_CONCURRENCY_MODEL.md` gain normative semantic content in this revision.

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
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
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
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
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
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
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
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
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
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
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
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
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
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
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
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
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
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
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
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
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
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
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
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
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
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
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
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
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
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
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
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
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
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
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
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
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
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
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
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
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
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
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
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
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
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
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
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
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
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
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
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
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
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
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


## [0.1.99] - 2026-09-03

### Fixed
- Removed the remaining normative references to implementation-defined Future
  cancellation "safe points" from the older Language and Runtime cancellation
  sections.
- Made those older sections explicitly refer to the portable cancellation
  boundaries already defined by the concurrency model.
- Prevented an implementation from treating VM/JIT/GC/allocation/call/loop
  checkpoints as additional observable cancellation points.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
  `PROTOS_IO_MODEL.md` to document revision 99.

## [0.1.98] - 2026-09-03

### Fixed
- Defined deterministic `readLine(maxBytes)` precedence between line-length,
  decoding, I/O, EOF, and terminator conditions independently of buffering and
  read-ahead strategy.
- Required decoding validity of each next encoded character to be established
  before its source octets count as valid line content for the size bound.
- Defined CR as an immediate line terminator while still consuming a following LF
  as part of CRLF, without allowing later EOF/errors to retroactively fail the
  completed CR-terminated line.
- Required read-ahead across a completed line to preserve following bytes and
  deferred errors for subsequent reads.

### Changed
- Synchronized all revisioned specification documents to revision 98. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.97] - 2026-09-03

### Fixed
- Replaced implementation-defined Future cancellation safepoints with portable
  cancellation-observation boundaries.
- Made every explicit suspension point a mandatory cancellation boundary and
  required pending cancellation to be observed before suspended work resumes
  ordinary Protos execution.
- Defined that normatively cancellation-aware operations may observe cancellation
  while pending, subject to their commitment/effect rules.
- Excluded method calls, allocations, loop back-edges, VM/JIT polls, GC points,
  host calls, and other implementation machinery from becoming hidden observable
  cancellation points.
- Preserved cooperative cancellation: CPU-bound code with no explicit suspension or
  cancellation-aware operation may finish normally despite a pending request.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
  `PROTOS_IO_MODEL.md` to document revision 97.

## [0.1.96] - 2026-09-03

### Fixed
- Defined the portable Core handler-installation API as the ordinary
  `matchPrototype.handle(body, handler)` protocol inherited through `Error`.
- Fixed dynamic extent, innermost-match ordering, unwinding, handler deactivation
  before handler execution, and the value returned after normal handling.
- Defined handler state as task-local: the same suspended task retains its active
  scopes, while distinct asynchronous tasks and Future continuations do not
  inherit them.
- Aligned Future failure consumption with the rule that `value()` re-signals in
  the consumer's current dynamic handler context.
- Removed formatting artifacts left by the earlier error-taxonomy applier where
  present.

### Changed
- Synchronized all revisioned specification documents to revision 96.
  No new syntax or reserved words are introduced.

## [0.1.95] - 2026-09-03

### Fixed
- Removed implementation-dependent rollback behavior after a committed
  `filesystem.open` later fails before returning a `File`.
- Required the failed open itself not to compensate by deleting a target it
  already created or restoring content it already truncated.
- Clarified that this does not freeze later filesystem state: independent
  Actors, external processes, or backend activity may still change the target
  under their own authority and ordering.

### Changed
- Synchronized all revisioned specification documents to revision 95. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.94] - 2026-09-03

### Fixed
- Defined ordering when distinct capability objects or Actor-local proxies denote
  one logical output flow, so proxy identity cannot accidentally create separate
  ordering domains.
- Preserved each Actor's invocation order while leaving concurrent cross-Actor
  write order nondeterministic until the shared flow admits/routes the writes.
- Required successful writes on one logical flow to contribute whole logical byte
  sequences without byte-level interleaving, while permitting partial native
  writes and other implementation strategies underneath.

### Changed
- Synchronized all revisioned specification documents to revision 94. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.93] - 2026-09-03

### Fixed
- Closed the Core error-taxonomy ambiguity that previously allowed
  implementations to expose different intermediate error prototypes while
  still satisfying the same failure rule.
- Defined `Error` as the mandatory standard root error prototype and made the
  default standard taxonomy shallow: normatively named standard error
  prototypes delegate directly to `Error` unless a normative specification
  explicitly defines another parent relation.
- Clarified that a failure specified only as "signals an error" guarantees only
  the `Error` category for portable handler matching, and that pseudocode
  constructor names do not automatically become standard-prelude bindings.
- Preserved ordinary user/library extensibility: programs may build arbitrary
  error-prototype hierarchies below `Error` using normal delegation.

### Changed
- Synchronized the normative document revisions to 93. No syntax,
  Future scheduling, Actor-transfer, or I/O operation semantics change in this
  revision.

## [0.1.92] - 2026-09-03

### Fixed
- Required finite end-to-end admission/backpressure for `ByteWritable` output so
  invocation-time snapshots cannot turn pending writes into an implementation-
  managed unbounded memory queue.
- Required pressure to propagate through Protos-managed adapters, Actor-safe
  proxies, routing layers, and backend buffering while preserving bounded
  pipelining and implementation freedom.
- Clarified that programs may still retain arbitrarily many Futures or arguments
  themselves; the bounded-retention guarantee applies to state retained by the
  I/O delivery path.

### Changed
- Synchronized all revisioned specification documents to revision 92. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.91] - 2026-09-03

### Fixed
- Defined `filesystem.open(path, options)` commitment and cancellation semantics.
  Cancellation may win only before any portable create/truncate effect and before
  the successful `File` result commits.
- Made create/truncate failure aftermath explicit: once creation or truncation
  becomes observable, a later open failure does not imply rollback and cannot be
  reported as cancellation with zero effect.
- Closed undisclosed-handle lifetime ambiguity: an open that terminates without
  returning a `File` retains implementation custody of internal resources and
  must release them before a cancelled/failed terminal result.

### Changed
- Synchronized all revisioned specification documents to revision 91. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.90] - 2026-09-03

### Fixed
- Closed the structured-child terminal-outcome ambiguity. Normal owner completion
  now waits for every non-detached child to become terminal without implicitly
  observing or propagating that child's result.
- Defined that a failed non-detached child does not automatically fail an otherwise
  normally completing owner, and a cancelled child does not automatically cancel
  that owner. Future failure/cancellation remains observable through the ordinary
  Future API such as `value()`.
- Explicitly rejected hidden "unobserved failure" / "failure consumed" state whose
  history could otherwise make scope-exit behavior implementation-dependent.
- Kept the existing opposite-direction rule unchanged: owner error or cancellation
  still requests cancellation of non-detached children and waits for cleanup.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
  `PROTOS_IO_MODEL.md` to document revision 90.

## [0.1.89] - 2026-09-03

### Fixed
- Closed the observable execution-context and ownership ambiguity of `Future.then()`.
  A `then` call now creates a distinct structured continuation task owned by the
  activation that calls `then`, unless the destination Future is detached.
- Defined non-reentrant continuation execution: source completion only makes the
  continuation runnable and never invokes the transform inline, including when
  the source Future is already terminal at the `then()` call.
- Defined terminal-state propagation: resolved sources invoke the transform,
  failed sources fail the destination without invoking it, and cancelled sources
  cancel the destination without invoking it.
- Defined downstream-only cancellation and detachment for composition: cancelling
  or detaching the destination continuation does not cancel, detach, or otherwise
  alter the source Future.
- Preserved automatic Future flattening and avoided imposing any new global order
  between independent continuations.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
  `PROTOS_IO_MODEL.md` to document revision 89.

## [0.1.88] - 2026-09-03

### Fixed
- Made `Closable.close()` lifecycle semantics deterministic: invoking `close()`
  commits permanent lifecycle termination, so the close Future cannot later
  report `cancelled`.
- Removed implementation freedom over pending operations displaced by close:
  closure-induced termination fails them as closing/closed; an independent
  cancellation may still win under that operation's own cancellation contract.
- Required successful close to leave no previously accepted I/O operation
  pending and repeated close calls to observe one consistent lifecycle outcome.

### Changed
- Synchronized all revisioned specification documents to revision 88. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.87] - 2026-09-03

### Fixed
- Closed the Core v0.1 value-identity classification. Value identity now applies
  exactly to Number values, String values, the canonical Boolean values, and
  `null`; every other object has individual object identity.
- Removed the previous "includes at least" wording that allowed independent
  implementations to invent additional value-identity categories and therefore
  disagree on observable `===` results.
- Clarified that immutability, `close()`, `freeze()`, interning,
  canonicalization, host representation, and delegation to a value object do
  not grant value identity.
- Clarified that standard prototype objects, Closures, collections, Futures,
  errors, contexts, module instances, and other objects remain identity-bearing
  unless a future normative language revision explicitly adds a new
  value-identity family.

### Changed
- Made the runtime identity classifier exhaustive and semantic rather than
  implementation-extensible. Numeric and String identity continue to use their
  existing family-specific semantic rules.
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
  `PROTOS_IO_MODEL.md` to document revision 87. No syntax,
  concurrency-model, or I/O-model semantics change in this revision.


## [0.1.86] - 2026-09-03

### Fixed
- Closed the concurrency specification-authority ambiguity. `PROTOS_CONCURRENCY_MODEL.md`
  remains a mixed design ledger, but sections whose status is exactly `CLOSED` or
  `CLOSED --- REVISED` are now explicitly normative for Actor/Future/concurrency
  semantics. Sections carrying OPEN, PENDING, DIRECTION, DETAILS OPEN, API OPEN,
  or other design-only qualifiers remain non-normative.
- Defined the boundary for partially closed sections: an API, syntax, policy,
  mechanism, or implementation detail explicitly stated to remain open inside a
  CLOSED section remains non-normative without weakening the closed semantic
  rules around it.
- Made `AGENTS.md`, `PROTOS_LANGUAGE_SPEC.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, and `PROTOS_IO_MODEL.md` agree on that
  authority model, so an independent implementation no longer has to choose
  between treating the concurrency ledger as merely advisory and treating it as
  the owner of Actor/Future semantics.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
  `PROTOS_IO_MODEL.md` to document revision 86. No Actor, Future,
  scheduling, ordering, cancellation, I/O, or grammar behavior changes in this
  revision.

## [0.1.85] - 2026-09-03

### Fixed
- Defined `ByteWritable.write(bytes)` argument capture precisely: every invocation
  takes a logical snapshot of the supplied mutable `Bytes` size and octet contents
  at invocation time, so later caller mutation cannot change an already-issued
  write.
- Kept the snapshot semantic rather than representational: implementations may
  use copy-on-write, immutable backing, retained storage, scatter/gather, or other
  optimizations, but `write` creates no caller-visible borrow/freeze lifetime and
  snapshot capture is not the I/O commitment boundary.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
  `PROTOS_IO_MODEL.md` to document revision 85. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision; the
  other revisioned documents receive the common revision/date synchronization
  only.


## [0.1.84] - 2026-09-03

### Fixed
- Defined the Core v0.1 `Float` semantic value set exactly as IEEE 754-2019
  `binary64` instead of the previous implementation-dependent
  "IEEE-754-style" formulation.
- Required strict `binary64` results for standard Float basic arithmetic,
  `roundTiesToEven` rounding, gradual underflow, and observable equivalence
  across implementations. Excess precision, flush-to-zero modes, host rounding
  state, and fused-operation contraction may not change Protos results.
- Made positive and negative infinity, signed zero, subnormal values, and NaN
  mandatory parts of the Float model rather than depending on a chosen host
  floating representation.
- Defined decimal Float literal conversion as exact-decimal to IEEE 754-2019
  `binary64` using `roundTiesToEven`, independently of host parsing behavior.
- Closed the remaining NaN representation leak: Core Float has one semantic NaN
  value, while payload/sign bits are non-semantic implementation representation
  and are not required to survive operations or storage.

### Changed
- Clarified that IEEE floating-point conditions arising from the standard Float
  basic arithmetic produce IEEE Float results rather than signaling Protos
  errors merely because overflow, underflow, division by zero, or an invalid
  floating-point operation occurred.
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
  `PROTOS_IO_MODEL.md` to document revision 84. No syntax,
  concurrency-model, or I/O-model semantics change in this revision.


## [0.1.83] - 2026-09-03

### Fixed
- Defined object-composition visibility during construction. Object-body items
  execute strictly left to right, and a successfully completed composition item
  makes its unique effective contributions immediately visible to subsequent
  body items. Later body items never retroactively affect earlier evaluation.
- Replaced deferred whole-body composition resolution with structural
  reservation of names declared directly by the receiving object body. A direct
  local slot declaration reserves its name against composition independently of
  textual position, but the reservation is not a binding and does not affect
  lookup before the declaration executes.
- Made each composition item atomic with respect to target structural mutation.
  The source expression is evaluated first; all effective contributions are
  then validated before any are installed. A conflict therefore cannot leave a
  partially composed target, and source-slot enumeration order cannot become
  observable through partial installation.
- Clarified that composition has neither first-wins nor last-wins semantics.
  Non-reserved contributions that collide with an existing local target slot
  signal `CompositionConflict`.

### Changed
- Simplified the executable composition model in
  `PROTOS_RUNTIME_SEMANTICS.md`: implementations no longer conceptually collect
  all composition contributions and defer final slot resolution until after the
  object body. Composition may be performed incrementally while preserving the
  structural priority of direct local declarations.
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and
  `PROTOS_IO_MODEL.md` to document revision 83. No grammar,
  concurrency-model, or I/O-model semantics change in this revision.


## [0.1.82] - 2026-09-03

### Changed
- Fixed Core v0.1 to The Unicode Standard, Version 17.0.0 as the normative Unicode repertoire and property version. `XID_Start` and `XID_Continue` used by identifier recognition are now explicitly the Unicode 17.0.0 properties, and identifier NFC conformance is tied to the Unicode normalization specification applicable to Unicode 17.0.0.
- Made host Unicode support non-normative: a JDK, VM, operating system, library, or other host Unicode database may be used only when it produces behavior equivalent to the Core v0.1 Unicode 17.0.0 requirements. Host-version differences must not change whether the same Protos source is lexically valid.
- Defined the normative Unicode version as part of the Protos language version rather than the document revision. Changing the normative Unicode version therefore requires a language-version change.
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`, `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and `PROTOS_IO_MODEL.md` to document revision 82. Only `PROTOS_GRAMMAR.md` gains normative semantic content in this revision; the other revisioned documents receive the common revision/date synchronization only.

### Implementation note
- The current Java 21 lexer implementation derives Unicode identifier properties and NFC checks from host JDK Unicode facilities. After this specification change, that implementation remains incomplete for full Core v0.1 Unicode 17.0.0 conformance until its Unicode data path is made independent of host-version differences.

## [0.1.81] - 2026-09-02

### Fixed
- Removed the remaining ambiguity in `PROTOS_CONCURRENCY_MODEL.md` that listed the Process capability among runtime references an Actor may have implicitly. Process authority is not ambient and is available to an Actor only when explicitly provisioned at creation or explicitly delegated later; granting a subordinate Process-local facility does not implicitly grant the whole Process capability.
- Made `Closable.close()` cancellation lifecycle semantics explicit in `PROTOS_IO_MODEL.md`: invoking `close()` begins permanent closing, and successful cancellation of the close operation never restores the receiver to the open state or re-enables operations requiring an open resource. Cancellation can only prevent close effects that have not yet become irreversible.
- Made the default BOM rule deterministic: UTF8, UTF16LE, and UTF16BE consume an initial matching BOM by default; an explicit configuration may preserve it as U+FEFF.
- Defined successful completion values for completion-only I/O operations. `write`, `flush`, `close`, `sync`, `writeText`, `writeLine`, `shutdownRead`, and `shutdownWrite` resolve their Futures to the receiver. Existing operations with value-bearing results, including `read`, `position`, `seek`, `size`, `truncate`, `readText`, and `readLine`, retain their previously specified result semantics.
- Restored `PROTOS_CONCURRENCY_MODEL.md` Scope Roots to `DIRECTION CLOSED, DETAILS OPEN --- REVISED`; revision 80 closed the Process direction but did not close every remaining NodeRoot/ClusterRoot API/detail.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`, `PROTOS_RUNTIME_SEMANTICS.md`, `PROTOS_CONCURRENCY_MODEL.md`, and `PROTOS_IO_MODEL.md` to document revision 81. No grammar production, core evaluator rule, or language syntax changes in this revision.

## [0.1.80] - 2026-09-02

### Added
- Added `PROTOS_IO_MODEL.md` as the normative domain model for Protos I/O. It defines byte-oriented asynchronous I/O capabilities (`ByteReadable`, `ByteWritable`, `Flushable`, `Closable`, `ByteSeekable`, `ByteSized`, `Truncatable`, `Syncable`, `ReadShutdown`, and `WriteShutdown`), I/O-operation commitment/cancellation, wrapper ownership, text adapters, encoding semantics, line reading, file-open dimensions, filesystem authority, Path/URL separation, Process I/O bootstrap, environment/argument snapshots, standard-stream availability/encoding, Actor-safe capability delegation, and pay-as-you-grow requirements.

### Changed
- Refined `Process` in `PROTOS_CONCURRENCY_MODEL.md`: a Protos Process is an execution/isolation/failure domain, not normatively an operating-system process or address-space boundary. Every Protos execution has a lightweight Process and one RootActor even when no additional Actor is ever created. The former conceptual `ProcessRoot` role is folded into the Process runtime entity/capability itself.
- Defined Process as custodian of Process-local application-facing host authority. The RootActor owns initial Actor-local application state but receives appropriate Process capabilities at bootstrap; additional Actors do not implicitly inherit host/Process capabilities and receive only explicitly provisioned or delegated capabilities.
- Added the I/O-domain cross-reference to `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_RUNTIME_SEMANTICS.md`, and the concurrency I/O section. Clarified in Runtime Semantics that I/O `COMMITTED` is an operation-level concept and does not add a fifth Future state.
- Replaced the stale frozen-prelude example based on an assumed `print` binding with an `Object` binding example. `print` is not made a required Core-prelude binding by this revision.
- Updated the Language Specification encoding example to use encoding-owned one-shot operations (`UTF8.decode(bytes)` / `UTF8.encode(text)`) and delegated the standard encoding catalogue, decoding policy, BOM, and text-I/O rules to `PROTOS_IO_MODEL.md`.
- Updated `AGENTS.md` so normative domain models are treated as source-of-truth documents for their semantic domains, while preserving the special design-ledger status of `PROTOS_CONCURRENCY_MODEL.md`.
- Synchronized all revisioned specification/design documents to document revision 80. `PROTOS_GRAMMAR.md` receives only the common revision bump; revision 80 introduces no new I/O syntax and no grammar production changes.

### Scope
- External-process creation/control, process IDs, `kill`, POSIX signals, process groups/sessions, terminal-control APIs, and the exact `print`/object-representation protocol remain outside `PROTOS_IO_MODEL.md`. Future process/host-execution facilities must reuse the I/O protocols for any streams they expose.

## [0.1.79] - 2026-09-02

### Changed
- Corrected the canonical evaluator's evaluation order for explicit-target slot creation and assignment in `PROTOS_RUNTIME_SEMANTICS.md`: `Create(targetExpr?, name, valueExpr)` and `Assign(targetExpr?, name, valueExpr)` now evaluate the target expression before the value expression, matching the normative left-to-right order already stated in `PROTOS_LANGUAGE_SPEC.md`. `getObject().x = makeValue()` and `getObject().x: makeValue()` evaluate the target first, then the RHS, then perform the operation. When no target expression exists, only the RHS is evaluated, as before. Indexed Access Lowering already followed the same left-to-right order and is unchanged. No other assignment or slot-creation semantics changed.
- Made the lexical-binding model of `PROTOS_RUNTIME_SEMANTICS.md` unambiguous. The conceptual operation `lexicalParentOf(context)` now formally returns the immediate lexical parent context of an execution context, and the association is established when the context is created: activation contexts take the closure's captured lexical context, construction contexts take the genuine lexical context chain of the enclosing activation, module contexts take the frozen prelude context, and the prelude context is the root of the lexical chain. `Activation.lexicalParent` holds `lexicalParentOf(activation.context)`, `lookupName` and `assignName` traverse the lexical chain only through `lexicalParentOf`, and `lexicalContextForClosureCreation` returns `activation.lexicalParent` for construction activations. Object delegation and lexical parenthood are now formally distinct: `Context` is the delegation prototype of execution contexts (`activationContext → Context → Object`), never their lexical parent, and bare-name lookup never walks a context's delegation chain. No observable lookup, capture, assignment, or construction semantics changed.
- Replaced the shared-mutable-memory formulation of concurrency memory semantics with the Actor model already decided in `PROTOS_CONCURRENCY_MODEL.md`. `PROTOS_LANGUAGE_SPEC.md` (Concurrency Memory Semantics) and `PROTOS_RUNTIME_SEMANTICS.md` (Future Completion Visibility) no longer promise a general model of shared mutable objects, arbitrary data races, and explicit synchronization primitives. Both documents now state: ordinary Actor-local Future/task execution is cooperative and interleaves only at explicit suspension points, never executing Protos code simultaneously against the same mutable Actor state; between suspension points Actor-local state is serialized; different Actors share no mutable Protos references; explicit isolated parallel computation (whose API remains open) may execute simultaneously but crosses an isolation boundary and receives no arbitrary live mutable aliases to the calling Actor's state. The Future completion visibility guarantee is preserved. The empty `Futures and Concurrency` heading in `PROTOS_LANGUAGE_SPEC.md` was removed.
- Replaced argument-spread examples in `PROTOS_LANGUAGE_SPEC.md` that used array-literal syntax Core v0.1 does not define (`values: [10, 20, 30]` and the neighboring `f.values([10, 20])`) with Core-valid code: the rest-capturing closure `pack: (...items) => items` produces the ordinary collection that is then spread. The `args == [1]` pseudo-example in the same section became `args.size == 1`. Core v0.1 still has no array literal syntax, and no grammar production changed.
- Updated document revisions: `PROTOS_LANGUAGE_SPEC.md` 78 -> 79 and `PROTOS_RUNTIME_SEMANTICS.md` 78 -> 79. `PROTOS_GRAMMAR.md` 78 -> 79 and `PROTOS_CONCURRENCY_MODEL.md` 07 -> 79 are synchronized to the common revision 79: no semantic content changed in either document. All four documentation documents now share the same `Document revision`.
- `spec/PROTOS_CONCURRENCY_MODEL.md` is now explicitly part of the documentation, with design-ledger status: it carries the shared `Document revision`, it no longer has its own independent ledger version (the title `Protos Multithreading Design Ledger v1` became `Protos Multithreading Design Ledger`), and every change to the document must be recorded in this changelog. The governing policy is stated in `AGENTS.md`.

### Fixed
- `PROTOS_CONCURRENCY_MODEL.md` still carried `Document revision: 07` although revision 0.1.78 synchronized all four documents to the common revision 78. Its content (including the CLOSED section 72 added in 0.1.78) corresponds to the revision described by that changelog entry, so the header now reads 79, sharing the common documentation revision; no semantic content changed. The document remains a design ledger with CLOSED and OPEN sections, and nothing OPEN in it was promoted to Core by this revision.

## [0.1.78] - 2026-09-02

### Changed
- Synchronized document revisions: all four canonical documents now carry document revision 78. `PROTOS_LANGUAGE_SPEC.md` 77 -> 78, `PROTOS_RUNTIME_SEMANTICS.md` 76 -> 78, `PROTOS_GRAMMAR.md` 75 -> 78, and `PROTOS_CONCURRENCY_MODEL.md` from its former independent document revision 07 to the common revision 78. Language version remains 0.1.
- Formalized `Context` as the standard prototype for execution-context objects (design option A). Execution contexts remain ordinary Protos objects with ordinary delegation: an activation context and a `moduleContext` each delegate through `Context` to `Object`. `Context` is a standard prelude prototype, is not a reserved word, and is distinct from the reserved intrinsic pseudo-identifier `context`. Runtime pseudocode no longer refers to an undefined `standardContextPrototype`: activation contexts and module contexts are created with `parent = Context`, and behavior provided by `Context` is inherited through ordinary Protos delegation with no special lookup mechanism.
- Closed the shared-prelude / shallow-freeze / Actor-isolation hole. New invariant: any Protos object physically shared between Actors through the standard prelude must be semantically immutable for the duration of that sharing, and mutable Protos state reachable through standard facilities must be Actor-local. Freezing the prelude remains shallow and is not by itself sufficient to make objects referenced by its slots safe to share. No deep freeze is introduced; Actor isolation is not weakened; and implementations may physically share immutable implementation artifacts (parsed syntax, bytecode, machine code, immutable metadata, immutable constant data) where sharing is semantically unobservable, without being required to duplicate immutable data unnecessarily.
- Removed stale export terminology from the module rules. Core v0.1 has no export declarations, no export namespace, no `exports` object, and no separate export mechanism: `import(specifier)` is an ordinary operation exposed by the standard environment, it yields the module instance, and cross-module access occurs explicitly by obtaining a module instance and accessing its slots through ordinary member lookup. The obsolete wording "module/import/export mechanism" and "Import/export syntax is intentionally not defined ... and will be specified with the module system" was replaced consistently. Host-specific resolution of files, packages, standard-library modules, and search paths remains outside Core v0.1.
- Fixed two Future-composition examples that used the invalid parameterized trailing-closure form `future.then() (value) { ... }`. Both now use an ordinary explicit Closure argument: `future.then(value => { transform(value) })`. Parameterized trailing closures are not restored; trailing-closure syntax remains parameterless and unchanged.
- Renumbered `PROTOS_RUNTIME_SEMANTICS.md` into a consistent monotonically increasing sequence, resolving the duplicated section numbers 8 (Prefix Operator Lowering / Slot Creation) and 25 (Identity / Semantic Equality). No semantics changed during renumbering.

### Fixed
- Corrected broken "the language" wording artifacts in `PROTOS_RUNTIME_SEMANTICS.md` (e.g., "a the language object" -> "a language object", "ordinary the language error object" -> "ordinary language error object").
- `PROTOS_CONCURRENCY_MODEL.md` gains a CLOSED section 72, Standard Prelude Sharing, making the Actor-isolation rule and the standard-prelude rule explicitly agree; the document's module-state section already permitted sharing immutable implementation artifacts.

## [0.1.77] - 2026-09-02

### Changed
- Added expression-bodied Closures: a Closure body may now be exactly one ordinary expression instead of a braced sequence, e.g. `(x) => x * 2`. This is an exact mandatory desugaring: `closure-parameters => expression` is exactly equivalent to `closure-parameters => { expression }` (a braced body whose `Sequence` contains exactly that one expression), for every parameter form.
- Added optional parentheses for exactly one simple parameter: when a Closure has exactly one parameter and that parameter is neither a default parameter nor a rest parameter, the parentheses may be omitted and the parameter written as a bare identifier before `=>`: `x => x * 2` and `x => { ... }` are exactly equivalent to `(x) => x * 2` and `(x) => { ... }`. Parentheses remain mandatory for zero parameters, two or more parameters, a default parameter, and a rest parameter: `() => value`, `(a, b) => a + b`, `(x = 10) => x`, `(...items) => items`, and `(first, ...rest) => rest`. A bare parameter must satisfy the ordinary `identifier` rules, so reserved words remain invalid as parameter names.
- All Closure spellings — parenthesized or bare single-parameter, braced or expression body — create the same kind of Closure with identical runtime semantics. No new keyword, no new reserved word, and no new callable category was introduced; in particular there is no JavaScript-style distinction between `function` and arrow callables, and all forms have the same `this`, capture, `context`, `args`, `super`, method-binding, return-home, evaluation, Future/async, and error-propagation behavior.
- Nested shorthand Closures associate to the right: `x => y => x + y` means `x => (y => (x + y))` and is equivalent to `(x) => { (y) => { x + y } }`. This is specified normatively rather than left to accidental parser behavior.
- An expression body contains exactly one ordinary `expression`, never an `expression-sequence`: `x => print(x); foo()` is a Closure whose body is `print(x)` followed by the separate expression `foo()`, and `x => print(x)` followed by `foo()` on the next logical source line does not absorb `foo()`. Multiple expressions still require a braced body.
- The expression body is a full ordinary `expression`, not an artificially restricted subset: assignment, slot creation, non-local return `^`, nested Closures, and other ordinary expression forms are all permitted in an expression body (`x => this.value = x`, `x => ^x`, `x => y => x + y`).
- The shorthand composes with the existing expression grammar by ordinary precedence: `double: x => x * 2` means `double: (x => x * 2)`, and `f = x => x + 1` assigns the Closure object to `f`. Creating a Closure never invokes it: `applyLater(x => x * 2)` passes the Closure as an argument, and invoking an expression-bodied Closure directly requires the grouping already required by the postfix grammar, e.g. `(x => x * 2)(10)`.
- `{` immediately after `=>` continues to begin the Closure's braced body and is never reinterpreted as an object expression; an object-expression body is written with ordinary parenthesized grouping, `x => ({ ... })`. The choice between the braced and single-expression body forms is structural (the body's first token), with no parser heuristic and no semantic/type-based disambiguation.
- Newline handling follows the existing general rules and introduces no ASI-like or Closure-specific continuation mechanism. An identifier followed by a separating logical `NEWLINE` is a completed expression, so a following line that begins with `=>` is a syntax error (`x` alone on a line never becomes a single-parameter Closure). A logical `NEWLINE` immediately after `=>` is ordinary incomplete-construct continuation, so the body may begin on the next logical source line (`x =>` followed by an indented body is a Closure).
- Trailing-closure syntax is unchanged and remains parameterless and braced-only: `foo() { ... }` remains sugar for passing one final parameterless Closure, parameterized trailing closures are not restored, and the new shorthand is an ordinary explicit Closure in ordinary call-argument position (`items.each(item => print(item))`), never a trailing closure.
- `PROTOS_GRAMMAR.md` revision 73 -> 74: `closure-expression = closure-parameters, "=>", closure-body`, with `closure-parameters = parameter-list | identifier`; `closure-body = braced-closure-body | expression`; `braced-closure-body = "{", expression-sequence, "}"`; and `trailing-closure = braced-closure-body`. Closures §16 was extended with subsections specifying parameter forms, body forms and exact equivalence, composition with the expression grammar, right association of nested expression-bodied Closures, newline handling, and valid/invalid examples; the Whitespace and Newlines section now makes the `=>` continuation and identifier/`=>` newline cases explicit; the Compact EBNF and the trailing-closure section were updated consistently.
- `PROTOS_LANGUAGE_SPEC.md` revision 76 -> 77: the Closures section now presents the expression-body and single-parameter shorthand spellings and their exact equivalence and identical runtime semantics; Return Semantics notes that in an expression-bodied Closure the single body expression supplies the normal return value; the Trailing Closures section clarifies that the shorthand never reintroduces trailing closures.
- `PROTOS_RUNTIME_SEMANTICS.md` was not modified and remains at revision 76: no normative runtime behavior changed. Expression-bodied Closures are pure syntax and desugar before reaching the runtime; the existing Closure Creation, method binding, non-local return, Future, and error semantics apply unchanged to every spelling.
- `PROTOS_CONCURRENCY_MODEL.md` was not modified and remains at its independent document revision 07.
- Closed audit defect G1: the rev-77 production `closure-body = braced-closure-body | expression` formally admitted a second derivation for any Closure body whose first parser token is `{` — `x => { value: x }` could derive through `expression` reaching `object-expression` -> `object-body` as well as through `braced-closure-body` — although this revision's normative text already specifies that the `{` immediately after `=>` always begins the Closure's braced body. G1 is a formalization defect, not a semantic one: no Closure semantics are changed, and no new semantic decision is made. The grammar now expresses the already-decided rule itself: `closure-body = braced-closure-body | [ lookahead != "{" ], expression`. The single-expression alternative carries a first-token guard (new EBNF notation defined in the grammar's Scope section) permitting entry only when the next parser token in the continuing token sequence is not `{`; because `braced-closure-body` begins with the literal `{`, the two alternatives are disjoint on the body's first token, and a `{`-initial body has exactly one derivation — the braced form.
- The correction does not change any other expression: object expressions keep their full grammar and semantics (`x => parent { value: x }` remains an expression-bodied Closure returning an object whose parent is `parent`); trailing-closure syntax remains `trailing-closure = braced-closure-body`, parameterless and braced-only; and every expression-bodied spelling valid in 0.1.77 remains valid (`x => x + 1`, `x => foo(x)`, `x => this.value = x`, `x => ^x`, `x => y => x + y`, `x => ({ ... })`, `(x => x * 2)(10)`, and newline-continued bodies such as `x =>` followed by an indented body). No new keyword, no new callable category, no parser heuristic, no speculative parse, and no type-based or semantic disambiguation is introduced; the boundary is decided by the formal grammar. `x => { value: x }` is a braced Closure whose body is the slot-creation expression `value: x`, and `x => ({ value: x })` is the expression-bodied Closure whose parenthesized body evaluates to the object.
- `PROTOS_GRAMMAR.md` revision 74 -> 75: the normative `closure-body` production in Closures §16 and its copy in the Compact EBNF now carry the `[ lookahead != "{" ]` guard on the single-expression alternative; the EBNF notation list in Scope defines the guard; Closures §16 states that the two body forms are disjoint on the body's first token and adds the boundary examples `x => { value: x }` (braced body) versus `x => ({ value: x })` (expression body yielding an object); the Compact EBNF explanatory note is updated consistently.
- `PROTOS_LANGUAGE_SPEC.md` was not modified and remains at revision 77: its normative statement that the `{` immediately after `=>` always begins the Closure's braced body is the already-decided semantics, and no normative rule needed to change for a grammar formalization correction. `PROTOS_RUNTIME_SEMANTICS.md` was not modified and remains at revision 76. `PROTOS_CONCURRENCY_MODEL.md` was not modified and remains at its independent document revision 07.

## [0.1.76] - 2026-09-02

### Changed
- Closed audit issue D7: the classification of an Actor's initial entry point as either an importable initial module or a standalone non-importable entry point is fixed when execution of that entry begins; it is not recomputed later, and a standalone instance is never retroactively adopted into the Actor-local module cache.
- When, at the start of an initial entry's execution, the host/resolver has a canonical importable identity for it, the entry starts through the ordinary importable-initial-module lifecycle unchanged from revision 75: determine the canonical `ModuleKey`, use `ensureModuleInstance`, cache as `INITIALIZING` before body execution, execute the body, and transition to `READY` on success. A cycle back to such an initial module returns the same instance (`main#1`); no `main#2` is created.
- When, at the start of an initial entry's execution, the entry has no canonical `ModuleKey` through which `import()` could address it, it executes as a standalone entry point. That instance is Actor-local, executes its body in its own `moduleContext`, is not registered in the Actor-local module cache, and has no `ModuleKey`. The absence of a `ModuleKey` is determined for that execution when the entry begins; it does not change retroactively when the host's resolution capabilities change.
- There is no retroactive adoption of a standalone instance. The runtime does not later give a standalone instance a `ModuleKey`, does not register an already-executed standalone instance in the module cache, and does not convert it into the active cached module instance of any `ModuleKey`. No retroactive cache registration, module-instance adoption, identity mutation, cache migration, source-code deduplication, or rollback of standalone effects is introduced.
- If the host later changes its configuration, resolver, mounted sources, packages, or other host-defined mechanisms so that code equivalent to a previously executed standalone entry becomes importable under a canonical `ModuleKey`, that does not change the identity or status of the standalone instance already created. A later `import()` that resolves to that `ModuleKey` operates exclusively on the Actor-local module cache: a cache miss creates a new module instance through the ordinary lifecycle (`ensureModuleInstance` / `executeInitialModule`), so the standalone instance and the later cached instance are distinct objects (`E#1 !== E#2`).
- The module body corresponding to a previously standalone entry may execute again and its side effects may occur again when it is later imported. This does not violate cache-before-execute or the D6 invariant of at most one active cached module instance per `ModuleKey`, because the standalone instance never was the cached module instance of that `ModuleKey`.
- The rev-75 formulation that the "normal canonical-identity rules apply if the host later gives it a canonical identity" is superseded: the later appearance of an importable canonical identity does not apply those rules retroactively to an existing standalone instance.
- No syntax and no grammar production changed; D5 and D6 semantics remain unchanged in full (module instance = `moduleContext`; Actor-local module instances and caches; canonical `ModuleKey` identity; cache-before-execute; `INITIALIZING` and `READY`; at most one active cached instance per `ModuleKey` per Actor; cache membership distinct from object reachability; cyclic imports legal; recursive import of an `INITIALIZING` module returns the same partial instance immediately; no hidden suspension; no module TDZ, slot predeclaration, or hoisting; ordinary missing-slot semantics; successful initialization retains the cached instance; failed initialization removes the cache entry and a later import may retry with a fresh instance; escaped failed partial instances remain ordinary reachable objects; no rollback or revocation; importable initial module cached as `INITIALIZING` before execution; host-specific specifier resolution outside Core v0.1; `import()` remains eager and injects no lexical bindings).
- Updated canonical documents to revision 76: `PROTOS_LANGUAGE_SPEC.md` (entry classification fixed at execution start; standalone instances never retroactively adopted; later import of equivalent code may create a distinct instance) and `PROTOS_RUNTIME_SEMANTICS.md` (clarity that the standalone/importable choice is made before the entry executes, that `executeStandaloneEntry` instances remain outside the cache and cannot be adopted later, and that `executeInitialModule` never adopts an existing standalone instance). `PROTOS_GRAMMAR.md` was not modified and remains at revision 73. `PROTOS_CONCURRENCY_MODEL.md` required no change for D7 and remains at its independent document revision 07 (revision 07 was established earlier for the unrelated Isolated Parallel Execution design section); its module/ownership statements are already consistent with D7.

## [0.1.75] - 2026-09-02

### Changed
- Closed audit issue D6: an Actor's initial module participates in module identity and caching exactly like an imported module when it has an importable canonical identity. When the initial module can be resolved by `import()` to a canonical `ModuleKey`, the runtime determines or assigns that key, creates the module instance and its `moduleContext`, inserts it into the Actor-local module cache in state `INITIALIZING`, executes the module body, transitions it to `READY` on success, and applies the same cache-removal rule as any other failed module initialization. The cache-before-execute invariant therefore covers an importable initial module, which is not a special mutable module instance that exists outside the cache.
- A cyclic import back to the initial module returns that same cached instance and cannot create a second initial-module instance. When the initial module `main` imports `b` and `b` imports `main`, the recursive import returns the original `main#1`; no `main#2` is created.
- A host entry point with no importable canonical identity (a host-defined startup mechanism that the module resolver cannot map to a `ModuleKey`) need not be assigned a fabricated filesystem/package identity. It remains Actor-local, its mutable `moduleContext` is not shared with another Actor, it must not alias an imported module, and the normal canonical-identity rules apply if the host later gives it a canonical identity.
- "Module singleton per Actor" is clarified to mean at most one active cached module instance per canonical `ModuleKey`: the Actor-local module cache maps `ModuleKey` to the current active module record, with at most one record per key at a time. The phrase does not guarantee that only one object per canonical module identity can ever remain reachable during the Actor's lifetime.
- Cache membership and ordinary object reachability are distinct concepts. Escaped references to a failed partial instance may remain reachable after its cache entry is removed, and such an instance may coexist with a later fresh cached instance (`foo#1 !== foo#2`); only the later instance is the Actor's active cached module instance for that `ModuleKey`. Both objects belong to the same Actor, so coexistence does not violate Actor isolation.
- Failed-instance references are not revoked, not rolled back, and do not enter a hidden invalid-object state; removing a cache entry does not invalidate the instance object. No tombstone, revocation, identity mutation, or hidden invalidation is introduced.
- Runtime pseudocode in `PROTOS_RUNTIME_SEMANTICS.md` is factored so that ordinary import and Actor startup of an importable initial module share one module-instance lifecycle (`ensureModuleInstance`, called by `importModule` and `executeInitialModule`), making creation of a duplicate instance for the same canonical identity impossible. A non-importable host entry point is executed directly by `executeStandaloneEntry` without fake cache registration.
- No syntax and no grammar production changed; D5 semantics remain unchanged in full (module instance = `moduleContext`; Actor-local module caches; canonical `ModuleKey`; cache-before-execute; `INITIALIZING` and `READY`; cyclic imports are legal; recursive import of an `INITIALIZING` module returns the same partial instance immediately; no hidden suspension; no module TDZ, slot predeclaration, or hoisting; ordinary missing-slot semantics; successful initialization retains the cached instance; failed initialization removes the cache entry and a later import may retry with a fresh instance; no rollback of side effects; escaped references to failed partial instances are not revoked; host-specific module-specifier resolution remains host-defined; module instances remain Actor-local; immutable compiled/code artifacts may be shared invisibly).
- Updated canonical documents to revision 75: `PROTOS_LANGUAGE_SPEC.md` (initial-module cache registration, module-cache authority, escaped-failed-instance coexistence) and `PROTOS_RUNTIME_SEMANTICS.md` (factored module-instance lifecycle pseudocode). `PROTOS_GRAMMAR.md` was not modified. `PROTOS_CONCURRENCY_MODEL.md` (a design ledger, not a canonical document) was updated to its independent document revision 06 to replace the imprecise "module singleton per Actor" wording with the active-cached-instance invariant and to confirm that the RootActor's initial module is not outside the module model merely because it started the Process.

## [0.1.74] - 2026-09-02

### Changed
- Closed audit issue D5: module instances are Actor-local. A module instance belongs to exactly one Actor, and each Actor owns an independent module cache. Importing a module never provides access to mutable module state belonging to another Actor, and there are no process-global mutable module instances. The rule is explicitly consistent with the Actor isolation principle that no shared mutable Protos memory exists between Actors.
- Normative decision: a module instance is the module's `moduleContext` object. The module body executes with the module instance as its current execution context, top-level `:`-created bindings are local slots of the module instance, `import(specifier)` yields the module instance, and reading a member of a module instance observes the top-level binding slots exactly as they exist at that moment. There is no separate namespace object, wrapper, copy, or proxy, and module identity is ordinary object identity (`===`).
- Immutable compiled/code artifacts (parsed syntax, bytecode, machine code, immutable metadata, immutable constant data where otherwise semantically valid) may be physically shared between Actors, provided such sharing never exposes shared mutable Protos state. The observable `moduleContext` and mutable module state remain Actor-local.
- Normative decision: cache-before-execute. When an Actor imports a canonical module absent from that Actor's cache, the runtime creates the module instance, inserts it into the Actor-local module cache in state `INITIALIZING`, and only then executes the module body in that instance's `moduleContext`. The module is therefore discoverable through recursive imports before its body finishes executing.
- Module initialization states conceptually include `INITIALIZING` and `READY`. These are semantic concepts and are not exposed through a public state-inspection or reflection API. A transient internal failure state is permitted while a failed initialization is handled, but a failed initialization must not remain cached as a successfully importable module.
- Repeated imports within one Actor of the same canonical module identity reuse the same Actor-local module instance (`a === b` for `a: import("foo")` followed by `b: import("foo")` when both resolve to the same canonical identity); the module body is not executed again for the second import. Across Actors the same canonical module identity produces distinct Actor-local module instances with distinct mutable `moduleContext`s.
- Cyclic imports are legal. A cycle is not rejected merely because it is cyclic, and no `ModuleInitializationCycle` error is signaled. Recursive import of a module already `INITIALIZING` returns the same partially initialized module instance immediately and does not create a hidden suspension point; suspending would deadlock ordinary cyclic imports within the same Actor, and Actor reentrancy remains identifiable only from explicit suspension operations.
- Partially initialized modules are observable: only slots whose creating top-level statement has already executed are present, and reading a slot that has not yet been created follows the ordinary Protos missing-slot / lookup error semantics. No module-specific temporal-dead-zone mechanism, no predeclaration of module slots, and no hoisting of future slot creations is introduced; normal Protos slot semantics remain authoritative. A partially initialized module is the real module instance in its current state, not a placeholder copy.
- Successful initialization transitions the cached module instance from `INITIALIZING` to `READY`. The same module instance and the same `moduleContext` remain cached, and a later import in that Actor returns that instance without re-executing the module body. No new module identity is created because initialization completed.
- Failed initialization removes that attempt's entry from the Actor's module cache; the initiating `import()` fails with that error according to the normal error-propagation model, and a later import may attempt initialization again and may create a fresh module instance. A failed attempt does not permanently poison the Actor's module cache, and a failed partial module instance is not defined as reusable by a later independent import.
- Failed initialization does not roll back effects already performed. Removing the failed module from the module cache does not time-travel or undo side effects already executed during its failed initialization, and references to a partially initialized instance obtained by cyclic participants before failure are not revoked.
- Actor lifetime and module lifetime coincide: an Actor's module cache and its Actor-local module instances die with the Actor, and creating a new Actor does not inherit the creator's module cache or live module contexts. An Actor's initial module follows the same module-context model and is Actor-local rather than process-global.
- `import()` is not redefined to inject bindings into the importing lexical context. The module specifier remains an ordinary expression, cross-module visibility remains explicit, and no ES-module-style static binding declarations, CommonJS `exports`, Python namespaces, or analogous module syntax is introduced. Host-specific module-specifier resolution remains host-defined; module identity, Actor-local instance, cache, initialization, cycles, and failure are defined by Protos semantics.
- Updated canonical documents to revision 74: `PROTOS_LANGUAGE_SPEC.md` (Actor-local module instance identity, caching, initialization, cycles, and failure) and `PROTOS_RUNTIME_SEMANTICS.md` (Actor-local module cache pseudocode and module states). No grammar production and no grammar prose changed; `PROTOS_GRAMMAR.md` was not modified.

## [0.1.73] - 2026-09-02

### Changed
- Closed audit issue D4: blank-content-line whitespace stripping is now explicitly scoped to multiline String literals whose closing `"""` delimiter establishes a structural indentation prefix.
- Normative decision: blank-line whitespace stripping is part of multiline indentation normalization and occurs only when a structural indentation prefix exists. Where such a prefix exists, blank content lines remain exempt from prefix matching and need not contain the complete structural prefix, and their SPACE/TAB characters are removed as incidental source-formatting indentation so that a source blank line contributes an empty logical line.
- Where content flows into the closing delimiter on its source line and no structural indentation prefix exists, no indentation normalization is performed and no indentation or other whitespace is removed from any content line, including whitespace-only content lines, whose SPACE and TAB characters are ordinary String content and are preserved verbatim. No separate unconditional blank-line-cleanup rule is introduced.
- The conceptual invariant is now explicit in all canonical documents: no structural indentation prefix ⇒ no indentation normalization.
- No other multiline String semantics changed. Revision 72 semantics are preserved in full: the closing delimiter is the sole source of the structural indentation prefix; there is no minimum-common-indent, longest-common-prefix, visual-column, or editor-TAB-width computation; SPACE and TAB remain distinct exact source characters; mixed SPACE/TAB prefixes remain legal when matched exactly; prefix mismatch on a non-blank content line remains a lexical error; prefix matching/removal precedes escape interpretation and escape sequences never satisfy the prefix; opening structural-newline removal and closing structural newline/trailing-line removal are unchanged; retained LF/CR/CRLF code points are unchanged; same-line delimiter behavior remains valid; triple-double-quoted Strings remain non-raw; single-quoted and double-quoted String semantics are unchanged; String interpolation remains unsupported; and the String lexical grammar and quote-run rules are unchanged. No grammar production was modified.
- Updated canonical documents to revision 73: `PROTOS_LANGUAGE_SPEC.md` (normative indentation-normalization semantics and examples), `PROTOS_GRAMMAR.md` (lexical indentation rules, notes, and Compact EBNF explanatory prose), and `PROTOS_RUNTIME_SEMANTICS.md` (String-value semantics).

## [0.1.72] - 2026-09-02

### Changed
- Closed audit issue D3 (triple-double-quoted String indentation): the structural indentation prefix is established solely by the closing `"""` delimiter, never by the content lines.
- When the closing delimiter terminates an indentation-only trailing line, the structural indentation prefix is exactly the sequence of SPACE and TAB characters on that source line immediately preceding the closing delimiter and may be empty. When content flows into the closing delimiter on its source line, no structural indentation prefix exists and no indentation is removed.
- Matching and removal operate on exact source characters. Every non-blank content line must begin with exactly the structural indentation prefix; the prefix is removed exactly once from each such line, and any additional indentation after the prefix is content and is preserved.
- SPACE and TAB are distinct source characters and are never equivalent for indentation purposes; a TAB equals no number of SPACE characters, and Core v0.1 defines no semantic TAB width. Matching is by exact source-character prefix, not by visual column, and no editor-tab-stop rule exists. Mixed SPACE/TAB prefixes are legal when each content line begins with exactly the same prefix.
- No minimum-indent, common-visual-column, or longest-common-prefix algorithm is used to compute indentation from the content lines.
- A non-blank content line that does not begin with the exact structural indentation prefix — fewer prefix characters, SPACE where the prefix requires TAB, TAB where the prefix requires SPACE, or any other difference — makes the triple-double-quoted String invalid. Consistent with the existing String-literal lexical-error model this is a lexical error: no String token and no String value is produced, and no recovery behavior is defined.
- Blank content lines are exempt from the prefix requirement and need not contain the complete structural indentation prefix; their SPACE/TAB characters are removed as incidental source-formatting indentation, so a source blank line contributes an empty logical line. No intentional whitespace is removed from a non-blank content line beyond the single structural prefix.
- Indentation matching and stripping are based on the raw source characters at the beginning of each content line, before escape processing; an escape sequence never counts as source indentation and never satisfies the structural prefix.
- The existing triple-String delimiter, quote-run, escape-set, unterminated-literal, and opening/closing structural-newline rules are unchanged, and single-quoted and double-quoted String semantics are unchanged.
- Updated canonical documents to revision 72: `PROTOS_LANGUAGE_SPEC.md` (normative multiline indentation semantics and examples), `PROTOS_GRAMMAR.md` (lexical indentation rules, notes, and examples), and `PROTOS_RUNTIME_SEMANTICS.md` (String-value semantics).

## [0.1.71] - 2026-09-02

### Changed
- Closed audit issue D2: every Protos object may serve as the delegation parent of another object.
- "Prototype" describes a role that an object plays when another object delegates to it; it is not a distinct object category. No parentability capability, flag, type, predicate, or hidden classification is introduced.
- The rule applies without exception to ordinary objects, built-in objects, immutable value objects, singleton values, execution-context objects, and every other Protos object. Values such as `this`, `context`, `args`, `true`, `false`, `null`, Number values such as `42`, and String values such as `"hello"` may serve as delegation parents, as may the standard built-in prototype objects (`Object`, `Number`, `Integer`, `Float`, `String`, `Boolean`).
- Using an object as a delegation parent does not make the newly created child identical to that parent and does not transfer the parent's value identity or value-category membership to the child. For example, `answer: (42) { ... }` creates an ordinary identity-bearing object delegating to the Number value `42`, so `answer === 42` is false. No coercion or value inheritance is introduced: `answer + 1` is not specified to behave as numeric `43` merely because `answer` delegates to `42`.
- Delegated message lookup through such a parent preserves the original receiver under the existing receiver-preserving delegation rules; `this` remains the child.
- No parentability classification or runtime check is introduced, and the runtime is not required to allocate a unique heap object for a value parent such as `42`; immediate/tagged representations of value parents remain permitted.
- The `parent-expression` grammar is unchanged and was neither broadened nor restricted; no inconsistency requiring correction was discovered during audit. Its broad forms (`identifier`, `intrinsic-reference`, `member-expression`, `parenthesized-expression`) are confirmed as intentional, and literal parents continue to require parentheses (`(true)`, `(42)`, `("hello")`, ...).
- `Object` remains the unique root and has no delegation parent, but may still serve as the parent of another object; bare `{ ... }` already creates an object whose parent is `Object`.
- Value identity of `Number`/`String`/`Boolean`/`null`, `true`/`false`/`null` singleton semantics, immediate/tagged representation freedom, receiver-preserving delegation, `Object` as unique root, immutable delegation parents, and open/closed/frozen object semantics are unchanged.

### Unresolved
- D3 (triple-double-quoted String indentation details: TAB width, visual columns, TAB-to-SPACE equivalence, how common indentation is computed when SPACE and TAB are mixed, and whether mixed SPACE/TAB indentation is legal) remains unresolved and is unchanged by this revision.

## [0.1.70] - 2026-09-02

### Changed
- Closed audit issue D1: all seven Core v0.1 reserved-word spellings (`this`, `context`, `args`, `super`, `true`, `false`, `null`) are now valid contextual member names in the structural position immediately following a member-access `.`.
- Introduced the grammar category `member-name` in `PROTOS_GRAMMAR.md`: `member-name = identifier | "this" | "context" | "args" | "super" | "true" | "false" | "null"`. `member-name` is used only where the grammar structurally expects a name immediately after `.`: `member-suffix`, `member-expression`, and `super-message-send`. The Compact EBNF contains the same definition and updates. `identifier` remains the ordinary lexical/binding-name grammar category and was not globally replaced by `member-name`.
- A reserved spelling used as a `member-name` denotes an ordinary slot or message name and does not retain its expression-level intrinsic/literal/special meaning. Therefore `obj.this`, `obj.context`, `obj.args`, `obj.super`, `obj.true`, `obj.false`, `obj.null`, `obj.true()`, `obj.null = value`, `obj.super: value`, `obj.a.this`, and `f: obj.true` are valid structural member operations, subject to the existing runtime rules for reading, invoking, modifying, or creating the selected slot.
- Lexical reserved-word classification is unchanged: the lexer continues to tokenize the seven spellings as their dedicated reserved tokens rather than as ordinary identifier tokens. This revision introduces no contextual lexing; the parser accepts either an identifier token or one of the seven reserved tokens when parsing `member-name`.
- Bare reserved-word semantics are unchanged: `this`, `context`, `args`, `true`, `false`, and `null` retain their ordinary expression-level meanings, and bare `super` remains invalid. Reserved words remain invalid where the grammar expects `identifier`, including parameter names, rest-parameter names, bare assignment targets, and bare slot-creation targets: `this: value`, `context: value`, `args: value`, `super: value`, `true: value`, `false: value`, `null: value`, `(a, true) => { ... }`, `(...super) => { ... }`, bare `super`, `foo(super)`, and `f: super.foo` all remain invalid.
- The leading `super` of `super.foo()` continues to introduce the existing `super-message-send`; the name after the dot is now a `member-name`, so reserved spellings are valid super message names: `super.true()`, `super.this()`, and `super.super()` are syntactically valid super message sends whose message names are `true`, `this`, and `super`, respectively. This does not make `super` a first-class value, and method extraction (`f: super.foo`) remains unsupported.
- No reflection API and no arbitrary-String slot-name rule are introduced by this revision. The revision decides only the relationship between ordinary identifier spellings, the seven reserved-word spellings, and structural member syntax after `.`.
- No runtime semantics changed: `member-name` is a grammar-level category, and the runtime continues to operate on ordinary slot/message names.

### Unresolved
- D3 (triple-double-quoted String indentation details: TAB width, visual columns, TAB-to-SPACE equivalence, how common indentation is computed when SPACE and TAB are mixed, and whether mixed SPACE/TAB indentation is legal) remains unresolved and is unchanged by this revision.

## [0.1.69] - 2026-09-02

### Changed
- Closed audit issue C3d: the normative grammar in `PROTOS_GRAMMAR.md` is now self-contained for parser productions. Every parser nonterminal referenced by a normative EBNF production is now defined in the normative body, either directly in the relevant normative section or by an explicit normative cross-reference to a production defined elsewhere in the same document.
- Added the normative operator/expression hierarchy to the Operators section, preserving exactly the existing Compact EBNF structure and precedence semantics: `binary-expression`, `logical-or-expression`, `logical-and-expression`, `equality-expression`, `equality-operator`, `comparison-expression`, `comparison-operator`, `additive-expression`, `additive-operator`, `multiplicative-expression`, `multiplicative-operator`, `custom-binary-expression`, `unary-expression`, and `unary-operator`.
- Added the normative `member-expression` production in the Member Access, Calls, Indexing, and Postfix Expressions section, preserving the existing grammar exactly: `member-expression = primary-expression, { postfix-operation }, ".", identifier`.
- Added the normative `intrinsic-reference` production in the Primary Expressions section, preserving revision 68 / C6 exactly: `intrinsic-reference = "this" | "context" | "args"`. `true`, `false`, and `null` remain literals only; `this`, `context`, and `args` remain intrinsic references only; `super` remains governed exclusively by `super-message-send`.
- No syntax, precedence, associativity, tokenization, parsing behavior, or desugaring changed: the normative additions are the same productions already present in the Compact EBNF, moved/aligned into the normative body, and no new parser alternative or lexical rule was introduced.
- The Compact EBNF remains the compact consolidated view of the same grammar and is unchanged apart from its revision meta-note, updated to revision 69.
- No runtime semantics changed.

### Unresolved
- D3 (triple-double-quoted String indentation details: TAB width, visual columns, TAB-to-SPACE equivalence, how common indentation is computed when SPACE and TAB are mixed, and whether mixed SPACE/TAB indentation is legal) remains unresolved and is unchanged by this revision.

## [0.1.68] - 2026-09-02

### Changed
- Closed audit issue C6: removed the duplicate syntactic classification of `true`, `false`, and `null`.
- `true`, `false`, and `null` are literals only; `this`, `context`, and `args` are intrinsic references only. `primary-expression` continues to contain both `literal` and `intrinsic-reference`, so each of `true`, `false`, and `null` now has exactly one syntactic derivation from `primary-expression`, through `literal`, while `this`, `context`, and `args` remain valid through `intrinsic-reference`.
- The Compact EBNF `intrinsic-reference` production now contains only `"this"`, `"context"`, and `"args"`.
- The `literal` production is unchanged and remains `number-literal | string-literal | "true" | "false" | "null"`.
- `super` remains governed exclusively by `super-message-send` and is not added to `intrinsic-reference`.
- All seven reserved words (`this`, `context`, `args`, `super`, `true`, `false`, `null`) are unchanged: the lexical reserved-word rules are untouched.
- No runtime semantics changed: the values produced by these literals and intrinsic references are unchanged.

### Unresolved
- C3d (broader normative-grammar self-containment beyond the String forms) remains unresolved and is unchanged by this revision.

## [0.1.67] - 2026-09-02

### Changed
- Closed issue C3c: all three supported Core v0.1 String literal forms now have formal lexical grammar.
- The normative lexical grammar in `PROTOS_GRAMMAR.md` now formally defines `single-quoted-string`, `double-quoted-string`, and `triple-double-quoted-string`, together with the helper productions they require.
- The valid escape set is formally encoded unchanged: `\\`, `\'`, `\"`, `\n`, `\r`, `\t`, `\b`, `\f`, and `\u{HEX}`. No escape is added, removed, or reinterpreted; `\0`, `\xNN`, octal escapes, named Unicode escapes, and interpolation escapes remain unsupported.
- The Unicode escape source shape is formally encoded as `\u{` followed by 1 to 6 hexadecimal digits followed by `}`; hexadecimal digits are `0`-`9`, `a`-`f`, and `A`-`F`, and underscore separators are not part of the Unicode escape shape.
- Unicode scalar validity remains lexically enforced: a `\u{HEX}` value that is not a valid Unicode scalar value (surrogates, or values greater than U+10FFFF) is a lexical error.
- Single-quoted and double-quoted raw-newline restrictions are unchanged: a logical source newline before the matching closing quote remains a lexical error, and both forms remain single-line literals.
- Triple-double-quoted String logical-newline and indentation behavior is unchanged; the lexical grammar permits logical source newlines in triple-double content and does not alter opening/trailing newline removal or indentation normalization.
- C8 quote-run behavior is unchanged: triple-double opening priority, first-three-unescaped-quotes closing, exact-three-quote delimiters, escaped-quote non-participation, and post-delimiter quote lexing are all preserved by the formal grammar, with the C8 prose remaining authoritative.
- C7 unterminated behavior is unchanged: the valid-token grammar requires a closing delimiter, and no EOF alternative or partial String token is introduced.
- C4 malformed/incomplete escape behavior is unchanged: only valid escape sequences are listed, and a backslash followed by anything outside the valid escape grammar remains a lexical error.
- Triple-single-quoted strings remain unsupported.
- String interpolation remains unsupported.
- No implicit String-literal concatenation is introduced.
- Indentation normalization is unchanged.
- The Compact EBNF now defines `string-literal` and references the normative String lexical productions instead of leaving them dangling; the Compact EBNF revision meta-note was updated to revision 67.
- No runtime semantics changed: this revision formalizes source spelling only.

### Unresolved
- D3 (triple-double-quoted String indentation details: TAB width, visual columns, TAB-to-SPACE equivalence, how common indentation is computed when SPACE and TAB are mixed, and whether mixed SPACE/TAB indentation is legal) remains unresolved and is unchanged by this revision.
- C3d (broader normative-grammar self-containment beyond the String forms) remains unresolved and is unchanged by this revision.

## [0.1.66] - 2026-09-02

### Changed
- Closed issue C8: triple-double quote-run tokenization is now deterministic.
- Triple-double-quoted String opening-delimiter recognition takes priority when `"""` occurs at the current lexical position outside a String: three consecutive unescaped double quotes begin a triple-double-quoted String rather than an ordinary double-quoted String opener followed by another double quote.
- Inside a triple-double-quoted String, the first three consecutive unescaped double-quote characters form the closing delimiter.
- Opening and closing delimiters consume exactly three double quotes; there is no greedy rule that consumes a run of four, five, six, or more quotes as one delimiter.
- One or two consecutive unescaped double quotes inside triple-double content are ordinary content when they do not begin a closing delimiter.
- Quotes remaining after a closing delimiter are lexed normally from the next lexical position.
- An escaped double quote (`\"`) is String content and does not participate in closing-delimiter recognition; no new triple-quote escape is introduced, and the meaning of `\"` is unchanged.
- No lexical backtracking occurs to rescue later malformed tokenization: quote-run decisions are not revised based on whether later tokenization or parsing succeeds.
- Empty triple-double-quoted Strings remain valid (`""""""`).
- No implicit adjacent String-literal concatenation was introduced; a lexically valid sequence of adjacent String tokens remains subject to the ordinary expression grammar.
- C7 behavior is unchanged: if quote-run tokenization begins a new String lexical construct that reaches the end of source before its required closing delimiter, the source is rejected as a lexical error.
- C4 escape behavior is unchanged: malformed or incomplete escape sequences remain lexical errors, and quote-run recognition must not reinterpret characters already consumed as part of an escape.
- Multiline newline and indentation behavior is unchanged: logical source newlines, retained newline code points, opening/trailing newline removal, and indentation normalization in triple-double-quoted Strings are unaffected.
- No runtime semantics changed: quote-run recognition is lexical behavior.

### Unresolved
- C3c (`single-quoted-string`, `double-quoted-string`, `triple-double-quoted-string`) remains unresolved and is unchanged by this revision.
- D3 (triple-double-quoted String indentation details: TAB width, visual columns, TAB-to-SPACE equivalence, how common indentation is computed when SPACE and TAB are mixed, and whether mixed SPACE/TAB indentation is legal) remains unresolved and is unchanged by this revision.

## [0.1.65] - 2026-09-02

### Changed
- Closed issue C7: reaching the end of source before the required closing delimiter of a String literal is now a lexical error.
- The rule applies to all three Core v0.1 String forms: single-quoted (`'...'`), double-quoted (`"..."`), and triple-double-quoted (`"""..."""`).
- An unterminated String literal never produces a partial String token. The lexer must not recover by treating the opening quote as another token, emitting the accumulated content as a partial String, splitting the malformed literal into otherwise valid tokens, implicitly inserting a closing delimiter, or interpreting the end of source as the closing delimiter; the parser never receives a successfully formed String token for an unterminated literal.
- Existing single-quoted and double-quoted raw-newline lexical errors are unchanged: a logical source newline before the matching closing quote remains a lexical error and terminates String recognition before any end-of-source determination.
- Existing malformed/incomplete escape lexical errors are unchanged. Reaching the end of source after a backslash or during an incomplete escape while String recognition is active is lexically invalid, without requiring a normative diagnostic-category precedence between an "incomplete escape" and an "unterminated String".
- Triple-double-quoted multiline String newline, indentation, and delimiter behavior is unchanged.
- No runtime semantics changed: malformed source literals are rejected during lexing, before parser/runtime semantics.

### Unresolved
- C3c (`single-quoted-string`, `double-quoted-string`, `triple-double-quoted-string`) remains unresolved and is unchanged by this revision.
- D3 (triple-double-quoted String indentation details: TAB width, visual columns, TAB-to-SPACE equivalence, how common indentation is computed when SPACE and TAB are mixed, and whether mixed SPACE/TAB indentation is legal) remains unresolved and is unchanged by this revision.

## [0.1.64] - 2026-09-02

### Changed
- Closed issue C4: invalid or incomplete String escape sequences are now classified as lexical errors rather than syntax errors.
- Escape validation is part of String-token lexing: the lexer rejects malformed escape syntax before the parser receives a String token, and the parser does not inspect the interior of a successfully formed String token in order to validate escape syntax.
- The existing Core v0.1 valid escape set is unchanged: `\\`, `\'`, `\"`, `\n`, `\r`, `\t`, `\b`, `\f`, and `\u{HEX}`.
- `\u{HEX}` retains its existing requirements: exactly 1 to 6 hexadecimal digits, and the resulting value must denote a valid Unicode scalar value. Values outside the Unicode scalar-value range and surrogate code points are invalid escapes and produce a lexical error.
- No valid escape sequence is added, removed, or reinterpreted; `\xNN`, octal, `\0`, named Unicode, and interpolation escapes remain unsupported.
- Raw-newline behavior is unchanged: a logical source newline before the matching closing quote in single-quoted and double-quoted String literals remains a lexical error.
- Triple-double-quoted String newline and indentation behavior is unchanged, and triple-double-quoted strings remain non-raw strings using the same escape set.
- String interpolation remains absent, and triple-single-quoted strings remain unsupported.
- No runtime semantics changed: malformed source literals are rejected during lexing, before parser/runtime semantics.

### Unresolved
- C3c (`single-quoted-string`, `double-quoted-string`, `triple-double-quoted-string`) remains unresolved and is unchanged by this revision.
- D3 (triple-double-quoted String indentation details: TAB width, visual columns, TAB-to-SPACE equivalence, how common indentation is computed when SPACE and TAB are mixed, and whether mixed SPACE/TAB indentation is legal) remains unresolved and is unchanged by this revision.

## [0.1.63] - 2026-09-02

### Changed
- Closed issue C3b by giving `custom-binary-operator` a complete normative lexical definition; it is no longer an undefined EBNF reference.
- Formalized the existing custom symbolic operator character alphabet unchanged: `! $ % & * + - / < = > ? @ \ ^ | ~`.
- The normative lexical grammar now defines `operator-character`, `symbolic-operator-spelling`, and `custom-binary-operator`; the candidate maximal symbolic token is a non-empty sequence of consecutive `operator-character` code points.
- Maximal-munch and reserved-spelling classification are formalized without semantic changes: the lexer first forms the longest valid symbolic spelling at a source position, then classifies the complete spelling as a reserved/standard token when it exactly matches a reserved/standard symbolic spelling and as `CUSTOM_OPERATOR` otherwise; a longer custom spelling is never split in order to prefer a shorter reserved/standard token.
- Exact standalone `!` remains prefix logical negation (`not()` lowering) and exact standalone `^` remains non-local return; neither is a `custom-binary-operator`.
- Standard operator spellings (`=>`, `=`, `==`, `===`, `!=`, `!==`, `<=`, `>=`, `&&`, `||`, `+`, `-`, `*`, `/`, `%`, `<`, `>`) and the exact `!` and `^` remain excluded from the custom operator category and keep their dedicated grammar roles and precedence.
- Longer non-reserved symbolic spellings, including those containing or beginning with standard-operator characters, remain custom binary operators where already specified (for example `!!`, `^^`, `!^`, `^!`, `@`, `|>`, `<=>`).
- Precedence and associativity are unchanged: all custom binary operators share one precedence domain, associate left-to-right, and mixing them with standard binary operators without explicit grouping remains a syntax error.
- Structural punctuation and special syntax are unchanged: `.`, `:`, `;`, `,`, `(`, `)`, `{`, `}`, `[`, `]`, and the `...` ellipsis token do not participate in custom operator tokens; decimal-dot, ellipsis, closure `=>`, assignment `=`, and slot-creation `:` behavior are unchanged.
- No runtime semantics changed: message-send lowering, operator dispatch, Boolean laziness, non-local return, and precedence behavior are unchanged.

### Unresolved
- The remaining C3 items (`single-quoted-string`, `double-quoted-string`, `triple-double-quoted-string`, and broader normative-grammar self-containment) remain unresolved and are unchanged by this revision.

## [0.1.62] - 2026-09-02

### Changed
- Closed issue C3a by giving `number-literal` a complete normative lexical grammar; `number-literal` is no longer an undefined EBNF reference.
- The new grammar makes the numeric token families explicit: `decimal-number-literal`, `binary-integer-literal`, `octal-integer-literal`, and `hexadecimal-integer-literal`, with helper productions for decimal digits, digit sequences, fractional parts, exponent parts, radix prefixes, and radix digits.
- Existing numeric syntax was formalized without semantic changes: decimal integers (including leading zeroes), decimal fractional literals, decimal exponent forms, binary/octal/hexadecimal integer literals, and `_` digit separators as previously specified.
- The previously accepted radix prefix case behavior is preserved exactly: `0x`/`0X`, `0b`/`0B`, and `0o`/`0O`.
- Decimal-dot tokenization is unchanged: a `.` belongs to a decimal numeric literal only when immediately followed by a decimal digit, so `1.` remains an integer literal followed by a structural dot and `.5` remains a structural dot followed by an integer literal.
- Malformed numeric continuations remain lexical errors rather than token splitting: `0x`, `0xG`, `0b2`, `0o8`, `2e`, `2e+`, `1__2`, `1_`, `0x_FF`, and `123abc` remain lexical errors.
- Unsupported radix floating-point attempts remain lexical errors: `0b10.5`, `0o17.2`, and `0xFF.1` are not split into radix integer, dot, and integer tokens.
- No runtime semantics changed: numeric value families, literal value production, equality, identity, and arithmetic behavior are unchanged, and no runtime lexing machinery is added.

### Unresolved
- The remaining C3 items (`custom-binary-operator`, `single-quoted-string`, `double-quoted-string`, `triple-double-quoted-string`, and broader normative-grammar self-containment) remain unresolved and are unchanged by this revision.

## [0.1.61] - 2026-09-01

### Changed
- Closed issue C2 by unifying the conflicting `object-body` definitions: the normative grammar now defines `object-body = "{", object-body-sequence, "}"`, and the stale `object-body = "{", expression-sequence, "}"` definition is removed.
- Object bodies use `object-body-sequence`, with `object-body-line-items`, `object-body-line`, `object-body-item`, and `composition-item` productions that mirror the ordinary `expression-sequence` separator structure.
- An object-body item is either an ordinary `expression` or a contextual `composition-item` of the form `...expression`; composition items and ordinary expressions share the same logical-`NEWLINE`/inline-`;` separator rules, and there is no implicit adjacency separator.
- `...expression` remains contextual to object bodies and is not a general expression: it is not added to `expression`, `primary-expression`, or `closure-body`. Closure bodies therefore continue to contain only ordinary expressions, and `() => { ...base }` does not become valid merely because closure bodies use braces.
- No runtime composition semantics changed: conflict handling, binding copying, evaluation order, error behavior, and the existing composition representation are unchanged.

### Unresolved
- Issue D3 remains unresolved and is unchanged by this revision.

## [0.1.60] - 2026-09-01

### Changed
- Resolved newline placement for trailing closures (issue B7): a trailing closure must have no intervening logical `NEWLINE` token after the completed call. `foo() { ... }` attaches the braces as a parameterless trailing closure appended as the final call argument, while `foo()` followed by `{ ... }` on a later source line does not attach them.
- A completed call is syntactically complete, so a following logical `NEWLINE` acts as the ordinary expression separator under the B2 complete-expression newline rule; repeated separating `NEWLINE` tokens (blank lines) under B4 likewise do not permit trailing-closure attachment, and a `;` between the call and the braces does not attach them.
- `{` is not added as a complete-expression newline continuation exception: the only complete-before-newline continuation exception remains the existing leading structural/member `.` rule from revision 55 (B2).
- Horizontal whitespace between the completed call and the closure body remains ordinary lexical separation and is permitted.
- Block comments may appear between the call and the trailing closure, including block comments containing source newlines, because newlines inside a block comment produce no `NEWLINE` tokens (A6). A line comment prevents same-sequence attachment because its terminating newline remains tokenized as a separating `NEWLINE`.
- Indentation plays no role in trailing-closure attachment: the decision concerns logical `NEWLINE` tokens, not physical source formatting.
- The trailing-closure production `trailing-closure = closure-body ;` and the call-suffix form `argument-list, [ trailing-closure ]` are preserved; the no-intervening-`NEWLINE` restriction is stated normatively rather than through new grammar machinery, and no special `same-line` lexical token is introduced.
- B2, B4, A4, and A6 semantics are unchanged, and revision 59 semantics are unchanged: trailing closures remain parameterless and B6 remains closed; `foo() (x) { body }` is still not trailing-closure syntax, and `foo((x) => { body })` remains an ordinary call.
- No runtime mechanism is added: only syntactically attached trailing closures reach trailing-closure lowering.

### Unresolved
- Issue D3 remains unresolved and is unchanged by this revision.

## [0.1.59] - 2026-09-01

### Changed
- Simplified trailing-closure syntax (resolves issue B6): a trailing closure is now always parameterless. `foo(args...) { body }` remains supported and still appends `() => { body }` as the final call argument.
- The parameterized trailing-closure form `foo(args...) (params...) { body }` is removed from Core v0.1: it is no longer recognized as trailing-closure syntax.
- A closure that requires parameters is written as an ordinary explicit closure expression in ordinary call-argument position, for example `items.each((item) => { print(item) })` and `collection.reduce(initial, (acc, item) => { ... })`.
- A trailing closure never has a parameter list. `(x)` remains an ordinary parenthesized expression and `(x) => { body }` remains an ordinary closure expression; there is no third interpretation of `(x)` as trailing-closure parameters.
- B6 is therefore resolved structurally: parentheses are no longer reused as a trailing-closure parameter list, so no parser lookahead, no speculative parsing, and no semantic/type-based disambiguation rule is required for trailing-closure parameters.
- This revision supersedes only the parameterized-trailing-closure portion of revision 54; the parameterless trailing-closure portion of revision 54 is unchanged.

### Unresolved
- Newline placement between a completed call and a trailing closure remains unresolved (issue B7).
- Issue D3 remains unresolved and is unchanged by this revision.

## [0.1.58] - 2026-09-01

### Changed
- Resolved indexed access and assignment versus slot creation (issue B5): slot/member access and indexed access are distinct mechanisms. `object.name` performs ordinary slot lookup, while `object[key]` lowers to the `at(key)` message and is not dynamic slot access.
- `object["foo"]` is not defined to be equivalent to `object.foo`; the two expressions may return completely different values, and an object does not automatically become indexable merely because it has slots.
- Indexable objects remain ordinary objects and may have ordinary slots, methods, delegation, and openness/frozen state; indexed contents and object slots are independent, so `map.description: "users"` and `map["description"] = user` may coexist and refer to entirely different things.
- `:` is specifically the slot-creation operator and can no longer target an index: `object[index]: value`, `object["foo"]: value`, and `object.foo[index]: value` are syntax errors. There is no indexed slot creation and no `atCreate`-style protocol.
- Indexed assignment `object[index] = value` remains valid and lowers to `atPut(index, value)`; the `=` in indexed assignment does not require an already-existing indexed entry, and whether `atPut` creates, replaces, extends, or rejects a missing key/index is defined by the receiver's `atPut` protocol.
- The grammar now distinguishes the legal final targets of `:` and `=`: `slot-creation-target` is a bare identifier or a member target, while `assignment-target` may additionally end in an index suffix. Chained postfix forms whose final operation is a member (for example `object[index].member: value`) remain valid slot-creation targets, and indexed assignment retains its existing evaluation order and written-value result.

### Unresolved
- Issues B6 and D3 remain unresolved and are unchanged by this revision.
- Newline placement between a completed call and a trailing closure, and before a trailing closure's own parameter list, remains unresolved (issue B7).

## [0.1.57] - 2026-09-01

### Changed
- Resolved expression-separation multiplicity and blank-line grammar (issue B4): Core v0.1 has two distinct expression-separation mechanisms — `;` is the inline expression separator between expressions on the same logical source line, and a logical `NEWLINE` is the ordinary separator between expressions on different logical source lines. They are distinct syntactic roles, not interchangeable spellings of one generic separator.
- `;` is a separator, not a terminator: it requires an expression before it and an expression after it on the same logical source line. Leading, trailing, and consecutive semicolons are syntax errors, and a `;` cannot separate an expression from an expression on a following source line; `;\n` is not a redundant separator pair.
- Repeated separating logical `NEWLINE` tokens have the same effect as one separating `NEWLINE`: blank lines are permitted between, before, and after expressions and create no empty expressions, no semantic AST nodes, and no runtime behavior.
- The grammar's `layout` production now permits one or more consecutive logical `NEWLINE` tokens (`newline, { newline }`): blank lines inside open delimited constructs such as argument lists and parameter lists are layout formatting with the same effect as a single layout newline, and never substitute for a required comma.
- The generic `separator = ";" | newline` expression-sequence model is replaced by productions that distinguish same-line `;`-separated expressions from cross-line newline separation; no production permits an optional trailing `;`, and no production creates empty expressions.
- Neither `;` nor a separating, continuation, or layout `NEWLINE` becomes a semantic AST node; expression separation still produces the existing `Sequence(expressions)` representation with strictly left-to-right evaluation.
- The revision 55 newline-continuation rules (B2) and the revision 56 comma-separated list rules (B3) are unchanged: trailing commas remain syntax errors, and newlines remain non-separators inside comma-separated lists.

### Unresolved
- Newline placement between a completed call and a trailing closure, and before a trailing closure's own parameter list, remains unresolved (issue B7).

## [0.1.56] - 2026-09-01

### Changed
- Resolved comma-separated list separators (issue B3): `,` is the only separator between elements of Core v0.1 comma-separated lists, including call arguments and closure parameters.
- A comma is strictly a separator between two list elements; it is not a terminator and does not represent an empty or omitted element. A comma must have a list element on both sides within the same list, so a trailing comma before the closing delimiter is a syntax error.
- A logical `NEWLINE` is no longer an argument or parameter separator. Newlines inside the delimiters of a list are continuation/layout under the revision 55 (B2) rules: formatting within a necessarily-incomplete construct, never a substitute for a required comma.
- Multiline calls and parameter lists remain valid through B2 continuation/layout: elements are separated by commas on their lines, and the closing delimiter may appear on a following line without a trailing comma.
- Removed the `argument-separator = "," | newline` grammar production; argument and parameter items are separated by `,` only, with an optional `layout` continuation-newline helper production for formatting inside the delimiters.
- Indexing is unchanged: Core v0.1 indexing contains one expression, not a comma-separated list, and this revision introduces no multi-index syntax.
- Trailing-closure semantics from revision 54 are unchanged: call arguments and trailing-closure parameters are distinct lists that each follow the comma-only separator rule.
- Commas and continuation newlines are resolved entirely during parsing and introduce no runtime behavior.

### Unresolved
- Separator multiplicity and blank-line grammar remain unresolved (issue B4).
- Newline placement between a completed call and a trailing closure, and before a trailing closure's own parameter list, remains unresolved (issue B7).

## [0.1.55] - 2026-09-01

### Changed
- Resolved newline continuation (issue B2): a logical `NEWLINE` token normally separates expressions when the expression before it may legally end at that point, and does not separate expressions while the syntactic construct before it is necessarily incomplete and requires further input.
- Added the explicit leading-dot postfix continuation: a logical newline immediately before a leading structural `.` continues the preceding postfix/member expression. This is the sole accepted complete-before-newline continuation exception in B2.
- No general leading-operator continuation: a binary or custom symbolic operator at the beginning of the following line does not continue a preceding complete expression.
- Newline continuation is a syntactic/parser rule based on grammatical incompleteness, not on a hard-coded list of token spellings; it is independent of indentation, visual alignment, tab width, and source line-ending spelling.
- No Automatic Semicolon Insertion: the parser decides whether an existing logical `NEWLINE` token separates expressions or is consumed as continuation. An explicit `;` remains an expression separator.
- Newline continuation is resolved entirely during parsing and has no runtime semantic effect; only expressions actually separated by parser-level expression separators become distinct sequence elements.

### Unresolved
- Separators before closing `)` / `]`, trailing commas, and related list-end questions remain unresolved (issue B3).
- Separator multiplicity and blank-line grammar remain unresolved (issue B4).
- Newline placement between a completed call and a trailing closure, and before a trailing closure's own parameter list, remains unresolved (issue B7).

## [0.1.54] - 2026-09-01

### Changed
- Unambiguous trailing-closure call syntax (resolves issue B1): the parentheses of a call always contain call arguments; they are never contextually reinterpreted as the parameter list of a following trailing closure.
- A parameterized trailing closure has its own parameter list placed after the completed call: `foo(args...) (params...) { body }` appends `(params...) => { body }` as the final call argument.
- `foo(args...) { body }` appends a parameterless `() => { body }` closure as the final call argument.
- Fixed the contradictory `items.each(item) { print(item) }` example/desugaring: the form now means one explicit `item` call argument plus a parameterless trailing closure, i.e. `items.each(item, () => { print(item) })`. It is not `items.each((item) => { print(item) })`.
- The parameterized form is written `items.each() (item) { print(item) }`, which desugars to `items.each((item) => { print(item) })`.
- Trailing closures remain ordinary Closure arguments after desugaring: no new runtime value kind and no special runtime trailing-block construct are introduced.
- The object-construction distinction is unchanged: `foo { ... }` creates an object whose parent expression is `foo`; `foo() { ... }` invokes `foo` with a parameterless trailing closure.

### Unresolved
- Newline placement between a completed call and a trailing closure, and before a trailing closure's own parameter list, remains unresolved (issue B7).

## [0.1.53] - 2026-09-01

### Added
- A `/* ... */` block comment is one lexical construct that consumes all source characters from its opening `/*` through its matching closing `*/`; Core v0.1 block comments do not nest and the first `*/` terminates the comment (resolves audit item A6).
- Logical source newlines inside a block comment are consumed as part of the comment: embedded `LF`, `CR`, and `CRLF` emit no `NEWLINE` token. An embedded `CRLF` remains one logical source newline for source-position and logical-line accounting.
- Single-line and multiline block comments have the same token-separation effect: logical newlines inside `/* ... */` cannot themselves separate expressions.
- Newlines outside a block comment remain governed by the revision 51 logical-newline rules.
- `//` line comments are unchanged: they terminate immediately before their terminating logical source newline, which remains available for ordinary `NEWLINE` tokenization.
- Comments remain lexical constructs with whitespace-like token-separation behavior; they do not add code points to the horizontal-whitespace set (revision 52: exactly `SPACE` and `TAB`).

### Unresolved
- Separator multiplicity and blank-line grammar remain unresolved (issue B4).

## [0.1.52] - 2026-09-01

### Added
- Core v0.1 horizontal whitespace is exactly `SPACE` (U+0020) and `CHARACTER TABULATION` (U+0009, TAB); no other code point is horizontal whitespace, and the set does not depend on Unicode whitespace properties or host whitespace classification.
- Outside lexical constructs that consume their own contents, SPACE and TAB are insignificant horizontal whitespace: they separate tokens where separation is required and otherwise emit no parser token.
- Logical source newlines remain a separate lexical category under the revision 51 `LF` / `CR` / `CRLF` rules; they are not horizontal whitespace.
- Other Unicode whitespace-like code points are not implicitly accepted as whitespace: in particular U+000B VERTICAL TAB, U+000C FORM FEED, U+0085 NEXT LINE, U+00A0 NO-BREAK SPACE, U+1680 OGHAM SPACE MARK, U+2000..U+200A Unicode space characters, U+2028 LINE SEPARATOR, U+2029 PARAGRAPH SEPARATOR, U+202F NARROW NO-BREAK SPACE, U+205F MEDIUM MATHEMATICAL SPACE, U+3000 IDEOGRAPHIC SPACE, and U+FEFF ZERO WIDTH NO-BREAK SPACE are not Core v0.1 whitespace (illustrative list, not an open-ended definition).
- A source code point that is neither part of a valid lexical token, nor SPACE or TAB horizontal whitespace, nor a logical source newline, nor consumed inside a lexical construct such as a String or comment is a lexical error; the lexer must not silently discard unknown Unicode whitespace-like or format characters.
- Multiline triple-double-quoted String indentation whitespace consists only of SPACE (U+0020) and TAB (U+0009). Excluded whitespace-like characters remain valid String content where the ordinary String literal rules permit them.
- U+FEFF is not defined as lexical whitespace; source-byte decoding and source-encoding signature behavior remain outside this revision.
- Comments continue to consume their contents according to the existing comment lexical rules.

### Unresolved
- Tab width, visual columns, whether a TAB is equivalent to some number of SPACE characters, and how common indentation is computed when SPACE and TAB are mixed remain open (part of the separate multiline-String indentation question).
- Newline behavior inside `/* ... */` block comments remains unresolved (audit item A6).
- Separator multiplicity and blank-line grammar remain unresolved (issue B4).

## [0.1.51] - 2026-09-01

### Added
- Logical source-newline definition: a logical source newline is exactly one of `LF` (U+000A), `CR` (U+000D), or `CRLF` (U+000D U+000A); `CRLF` is consumed atomically as one logical newline, never two.
- Each logical source newline that is not consumed by another lexical construct produces exactly one `NEWLINE` token for the parser; the parser-level `newline` used in grammar productions denotes this logical `NEWLINE` token.
- Mixed line-ending styles (`LF`, `CR`, and `CRLF`) within one source file are permitted and are not lexical errors.
- `//` line comments terminate immediately before the next logical source newline or at end of file; the terminating logical source newline is not consumed by the comment and remains available for ordinary newline tokenization.
- Single-quoted and double-quoted String literals reject any logical source newline (`LF`, `CR`, or `CRLF`) before the matching closing quote as a lexical error.
- Triple-double-quoted String literals count each logical source newline as one logical newline for structural processing (delimiter placement, content-line splitting, indentation normalization), while retained source newlines preserve their original source code points in the resulting String: `LF` remains U+000A, `CR` remains U+000D, and `CRLF` remains U+000D U+000A; there is no implicit newline normalization of String content.
- Opening/trailing newline removal in triple-double-quoted String literals removes the complete logical newline sequence, including both code points of a removable `CRLF`.
- Newline handling is independent of the host operating system, editor settings, Git line-ending conversion, and host line-separator conventions.

### Unresolved
- Newline behavior inside `/* ... */` block comments remains a separate open question (audit item A6); this revision decides nothing about it.
- Separator multiplicity and blank-line grammar (consecutive or mixed separators) remain a separate open question (issue B4); this revision does not change separator multiplicity.

## [0.1.50] - 2026-09-01

### Added
- Standalone `!` and `^` are standard/reserved symbolic tokens; their existing prefix and non-local-return meanings are unchanged.
- The exact one-character spellings `!` and `^` are not custom binary operators: `a ! b` and `a ^ b` are syntax errors.
- `!` and `^` remain characters in the custom operator alphabet and may participate in longer custom spellings such as `!!`, `^^`, `!^`, and `^!`.
- Symbolic token classification follows maximal munch (longest spelling first) and is independent of parser position; there is no prefix-position exception, so `!!x` tokenizes as `CUSTOM_OPERATOR("!!")` `IDENTIFIER("x")` and is a syntax error.

### Changed
- Reconciled the stale statement that the custom symbolic operator alphabet "remains to be finalized separately" with the already-fixed Core v0.1 alphabet; the alphabet is fixed by the language grammar.

## [0.1.49] - 2026-09-01

### Added
- Radix-Integer-dot boundary: a `.` immediately following a complete radix-prefixed Integer literal is a structural `.` token when not immediately followed by a decimal digit; `0b10.foo` tokenizes as `INTEGER("0b10")` `.` `IDENTIFIER("foo")`. When the `.` is immediately followed by a decimal digit, the source sequence is an attempted unsupported radix Float literal and is a lexical error: `0b10.5`, `0o17.25`, and `0x1.8` are lexical errors rather than being split into `INTEGER` `.` `INTEGER` tokens. The decimal-point vs. member-access lexing rules for decimal literals are unchanged.

## [0.1.48] - 2026-09-01

### Added
- Numeric token termination and malformed numeric boundaries: once a source sequence has begun as a numeric literal, a malformed continuation or an invalid numeric/identifier boundary is a lexical error; the lexer must not split the malformed sequence into otherwise valid tokens in order to recover it.
- Radix prefixes (`0x`, `0X`, `0b`, `0B`, `0o`, `0O`) must be followed by a valid digit for that radix: `0x`, `0xG`, `0b2`, and `0o8` are lexical errors, without fallback to `INTEGER("0")` plus another token.
- An exponent begun by `e` or `E` must be complete: `2e`, `2e+`, and `2e-` are lexical errors.
- Invalid underscore placement inside or immediately adjacent to a numeric literal is a lexical error: `1__2`, `1_`, and `0x_FF`.
- An identifier cannot begin immediately after a numeric literal without a lexical boundary: `123abc` is a lexical error, not `INTEGER("123")` followed by `IDENTIFIER("abc")`.
- Valid token boundaries (punctuation, whitespace, structural delimiters, operators) remain unaffected.

## [0.1.47] - 2026-09-01

### Changed
- Clarified decimal-point vs. member-access dot lexing: a `.` belongs to a decimal numeric literal only when it is immediately followed by a decimal digit. `1.0` is a `Float` literal; `1.` tokenizes as `INTEGER("1")` followed by `.` and `.5` as `.` followed by `INTEGER("5")`; `1.to(10)` tokenizes as `INTEGER("1")` `.` `IDENTIFIER("to")` `(` `INTEGER("10")` `)`. `1.` and `.5` are not numeric literals as complete source sequences; this does not make either sequence necessarily a lexical error — whether the resulting token sequence is syntactically valid is the parser's responsibility.

## [0.1.46] - 2026-08-31

### Added
- Core v0.1 ellipsis token (`...`) definition: single lexical token, greedy recognition, context-dependent meaning.
- Maximal-munch tokenization rule for symbolic operators.
- String escape validation as part of lexical analysis: invalid, incomplete, or unsupported escapes are lexical errors.
- Validation of `\u{HEX}` format and Unicode scalar value constraints.
- Clarification that no new operator semantics are introduced in this revision.

## [0.1.45] - 2026-08-31

### Added
- Core v0.1 newline handling rules for String literals.
- Single-quoted and double-quoted String literals are single-line; raw newlines are lexical errors in these forms.
- Newlines may be represented using `\n` and `\r` escape sequences in single-line literals.
- Triple-double-quoted String literals support raw source newlines as part of multiline content.
- Lexical rules for enforcing single-line constraints on non-multiline String forms.

## [0.1.44] - 2026-08-31

### Added
- Complete Core v0.1 reserved-word set definition: `this`, `context`, `args`, `super`, `true`, `false`, `null`.
- Reserved-word recognition rules: case-sensitive matching after lexical identifier recognition.
- Clarification that prelude names (`Object`, `Future`, `Number`, `String`, `Map`, `IdentityMap`, etc.) are not reserved words.
- Clarification that Core v0.1 does not reserve control-flow or declaration keywords such as `if`, `else`, `while`, `for`, `class`, `function`, `try`, `catch`, `throw`, `async`, or `await`.

## [0.1.43] - 2026-08-31

### Added
- Core v0.1 identifier syntax definition: Unicode-aware, case-sensitive, begin with `_` or `XID_Start`, continue with `XID_Continue`.
- Core v0.1 identifier normalization requirement: all identifiers must be in Unicode NFC form, implementations must reject non-NFC identifiers.
- Clarification that identifier normalization applies to spelling only, not to `String` values.
- Reserved word recognition rules after lexical identifier recognition.

## [0.1.42] - 2026-08-31

### Added
- Core v0.1 numeric literal syntax definition: decimal and radix literals (`0x`, `0b`, `0o`), digit separators, decimal point and exponent handling.
- Core v0.1 prefix operator semantics: prefix `-` lowers to `negated()`, prefix `!` lowers to `not()`, prefix `+` is unsupported, operators apply to arbitrary expressions.

### Changed
- Clarified numeric literal constraints: no leading sign as part of literal, no type suffixes, no `NaN`/`Infinity` literal syntax, unsupported hex/binary/octal Float forms.
- Clarified prefix operator lowering as protocol-based message dispatch rather than privileged intrinsics.

## [0.1.41] - 2026-08-31

### Added
- Core v0.1 string literal syntax: single-quoted, double-quoted, and triple-double-quoted forms with unified escape rules.
- Core v0.1 escape sequences: `\\`, `\'`, `\"`, `\n`, `\r`, `\t`, `\b`, `\f`, and `\u{HEX}`.
- Core v0.1 multiline string indentation normalization rule.
- Core v0.1 comment syntax: `//` line comments, `/* ... */` block comments, no nesting, no `#` syntax, no documentation-comment feature.

### Changed
- Specified constraint: no octal escapes, no `\xNN` escapes, no string interpolation.
- Clarified that Triple-single-quoted strings are not supported.
