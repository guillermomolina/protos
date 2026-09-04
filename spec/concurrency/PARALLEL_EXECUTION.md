# Protos Parallel Execution v0.1

Language version: 0.1
Status: Draft
Last updated: 2026-09-04

This document is the primary normative owner of Core isolated parallel execution
(P) semantics migrated from `docs/design/CONCURRENCY_DESIGN.md`.

Legacy §71 numbering is intentionally retained during modularization so existing
citations remain understandable. The compatibility heading left in the mixed
concurrency ledger is navigation only and defines no duplicate normative
authority.

## 71. Isolated Parallel Execution

**CLOSED --- REVISED**

Protos provides a runtime capability for explicit CPU-parallel
computation that does not require the programmer to create persistent
Actors merely to use multiple CPU cores.

This capability fills the semantic gap between Actor-local cooperative
Future/task execution and persistent Actor isolation.

It does not introduce arbitrary shared mutable Protos memory and does
not weaken the Actor turn rule.

The semantic rules in this section are normative. The exact public API,
surface syntax, bootstrap representation, standard-library names, and
implementation mechanisms remain open unless this section states otherwise.
Those open surfaces must preserve the closed rules below and may not choose
different observable semantics.

Conceptually:

    ordinary call
        |
        v
    Actor-local Future / task
        concurrent, cooperative, same mutable Actor domain
        |
        v
    isolated parallel computation
        may execute simultaneously on another CPU carrier
        no persistent identity, mailbox, or independent lifecycle
        |
        v
    Actor
        persistent isolated mutable state + identity + mailbox + lifecycle

### 71.1 Public Model

The fundamental public abstractions remain Future and Actor. Core v0.1 does
not introduce a separately observable parallel-task object.

The standard public submission operation is `Closure.parallel(arguments...)`.
It returns a normal Future representing the eventual result. The fact that the
computation is eligible to run in parallel is an execution property of this
explicitly requested operation, not a new meaning silently attached to every
Future.

In particular, ordinary `closure.future()` semantics are not changed by
this design. Actor-local Future work remains serialized with other
Actor-local Protos execution according to the normal Actor turn model.

The runtime may use internal concepts such as parallel jobs, work items,
worker pools, region capabilities, or work-stealing queues. Such concepts
are implementation machinery unless separately standardized later.

### 71.1A Task is not a Core public identity

Core v0.1 does not expose `Task` as a separate ordinary Protos value, handle,
identity, lifecycle object, scheduling capability, or introspection surface.

The public result/coordination abstraction for asynchronous and isolated work is
`Future`. A Future may be backed by task execution, by an I/O producer, by
communication, by a continuation, by isolated P work, or by another producer
whose implementation does not require an observable task object.

Consequently, Core v0.1 defines no standard:

- `Task` prototype or constructor;
- current-task intrinsic/reference;
- task identity or identity comparison;
- task-parent/child object graph exposed to application code;
- task-local mailbox, slots, or mutable state object;
- task handle distinct from the Future that represents eventual outcome;
- task enumeration, lookup, join handle, scheduler handle, carrier affinity, or
  priority API;
- conversion from a Future to an underlying task identity.

Runtime concepts such as task records, fibers, continuations, stacklets, worker
jobs, scheduler nodes, coroutine frames, or carrier assignments remain
implementation machinery. Two conforming runtimes may organize the same Future
semantics using different internal execution objects without creating a
Protos-visible difference.

Structured concurrency does not require a public Task object. Ownership,
detachment, cancellation, waiting, failure propagation, and Actor/P lifetime
rules are defined semantically through activations, Futures, and execution
domains. An implementation may track richer internal parent/child task state as
needed to realize those rules.

This boundary also prevents accidental identity from leaking out of
implementation structure. Code must not be able to distinguish two executions
merely because one runtime used one internal task record and another runtime
used several continuations or inlined work.

A future facility may introduce an explicit task-like abstraction only if it
provides independently justified semantics that Future/activation/domain
mechanisms cannot express, such as a deliberately exposed scheduling,
cancellation-scope, task-group, diagnostic, or resource-governance capability.
Such a future facility must define its own identity, lifetime, ownership,
transferability, cancellation, failure, and isolation rules rather than exposing
Core scheduler objects retroactively.

### 71.2 Explicit Isolation Boundary

Parallel execution crosses an explicit semantic isolation boundary.

Code executing in isolated parallel computation must not receive direct
mutable aliases into the caller Actor's object graph, execution context,
module context, `this`, return home, dynamic handlers, pending Futures,
or other Actor-local mutable runtime state.

A normal Closure captures lexical execution contexts by reference.
Parallel execution must not silently redefine that Closure to capture by
value merely because a parallel API was invoked.

Core v0.1 closes the bootstrap with **parallel Closure projection**.

Calling the standard `closure.parallel(arguments...)` operation does not invoke
the source Closure with a different scheduler and does not copy its caller
captures. The source Closure supplies executable body/parameter semantics to a
fresh ordinary P-local Closure whose lexical root belongs to the new P domain.

The projected Closure has no lexical edge to the source Closure's captured
caller contexts, no captured caller `this`, no caller return home, no caller
`methodHome`, and no inherited dynamic handlers. Its fresh P root uses the
standard frozen prelude as lexical parent, `null` as the absent caller receiver,
and a new return home local to the P computation.

This is an explicit semantic operation of `parallel`, not a redefinition of
ordinary Closure capture. The source Closure remains unchanged and continues to
capture lexical contexts by reference for ordinary invocation and `future()`.

No implementation-selected static capture-safety analysis participates in
whether caller lexical state crosses. It never crosses. If projected code later
performs a bare lookup that is not satisfied by P-local contexts, the standard
prelude, or ordinary P-local receiver lookup, ordinary lookup failure occurs
inside P and fails the result Future.

Closures created while P code is executing are ordinary Closures and capture
P-local execution contexts by reference.

### 71.3 Value and Snapshot Semantics

Values supplied across the isolated parallel boundary have logical
value/snapshot semantics analogous to other Protos isolation boundaries.
For mutable values, the parallel computation must observe the logical
input state established by the parallel operation rather than a live
mutable alias into the caller Actor.

For standard `Closure.parallel(arguments...)`, the logical P input snapshot is
completed before that invocation successfully returns its Future. Delayed worker
scheduling must not cause the input to drift with later caller-domain mutations.

Results cross back by value. Completion, failure, or cooperative
cancellation resolves the corresponding Future according to the normal
Future model.

For every successful parallel submission, the logical input state of every
cross-boundary value is fixed before control returns from that successful
submission to the caller. Worker admission, queueing, CPU availability, work
stealing, or delayed execution must not move that logical snapshot point.

Mutable input supplied by an Actor is not semantically mutated by the parallel
computation. The parallel computation operates on isolated logical state. The
caller's original mutable value retains the state established at the parallel
boundary and remains governed by ordinary Actor-local rules.

This rule does not prohibit an implementation from reusing physical storage when
it can do so without changing any observable source-object identity, contents,
aliasing, or later behavior. Copy-on-write, storage stealing followed by
unobservable reconstruction, page remapping, uniqueness analysis, and equivalent
optimizations remain implementation choices.

Publication back to the caller occurs only through a completed cross-boundary
result. Partial mutable state produced inside parallel execution is not visible
to the caller merely because some physical work has completed. Failure or
cancellation does not publish partially computed mutable state.

### 71.4 Safe Physical Sharing

Logical isolation does not require eager physical copying.

The runtime may preserve the required semantics through mechanisms such
as:

-   Copy-on-write
-   Immutable physical sharing
-   Shared immutable backing storage
-   Zero-copy transfer
-   Page remapping
-   Storage ownership transfer where semantically invisible
-   Other equivalent implementation optimizations

The implementation may therefore let multiple parallel computations
read the same immutable physical storage without producing semantic
shared mutable identity.

Physical sharing eligibility is defined only by observational equivalence to the
required isolated logical values. It is not a new Protos capability.

An implementation may physically share an object representation, backing store,
page, code artifact, immutable node, or other storage across P domains when no
permitted Protos operation in either domain can use that sharing to observe
shared mutable semantic state, identity collapse, different lookup/delegation,
different equality/identity, different failure behavior, or any other result
that would differ from the specified isolated values.

Conversely, semantic mutability of a logical value does not force eager copying.
A runtime may still share immutable backing, use copy-on-write, remap pages,
reuse storage after proving exclusivity, or use another representation that
preserves the same observable logical state.

The existing shallow `freeze()` operation does not introduce transitive
shareability. Freezing one object does not freeze mutable objects reachable
through its slots or delegation graph, and Core does not reinterpret `freeze()`
as a parallel ownership/transfer primitive. A frozen source object may be copied;
an unfrozen logical value may use physically shared immutable backing. Programs
cannot portably distinguish these implementation choices.

### 71.5 Exclusive Mutable Partitioning

Parallel algorithms may require several CPU cores to modify disjoint
parts of a large value efficiently. Protos may support this without
opening arbitrary shared mutable memory.

The governing rule is:

> Physical storage may be shared, but two parallel computations must not
> simultaneously hold mutable authority over the same logical state.

Core v0.1 standardizes this mechanism for byte-indexed state through standard
`Bytes.parallelRange(...)` and recursive `ByteRegion.parallelRange(...)`.
Several child P computations may operate simultaneously on disjoint byte ranges
while the runtime guarantees that writable intervals do not overlap.

Ordinary `Array` is not granted the same Core writable-region authority merely
because its indexes are disjoint: its elements may alias arbitrary mutable object
graphs. Future Array/object partition facilities require a stronger semantic
proof than non-overlapping indexes.

Conceptually:

    one large physical backing
        |
        +-- region A -> exclusive parallel writer A
        +-- region B -> exclusive parallel writer B
        +-- region C -> exclusive parallel writer C

The programmer is not required to introduce mutexes, atomics, volatile
state, memory ordering, or general-purpose borrow checking merely to use
this model.

Disjoint physical ranges, Array indexes, or storage addresses are not by
themselves sufficient to establish disjoint mutable authority. Partitioning is
valid only when the abstract mutable state governed by each writable partition
is logically disjoint from the mutable state governed by every simultaneously
writable sibling partition.

In particular, two Array regions that contain references to the same mutable
object do not acquire independent authority over that referenced object merely
because the Array indexes themselves do not overlap. A partition may provide
exclusive mutation of its own indexed state without thereby granting mutation
authority over arbitrary mutable objects reachable through its elements.

Exclusive writable partitioning applies to isolated state owned by the parallel
computation, not to live mutable state that remains semantically owned by the
calling Actor. The runtime may physically reuse source storage only when the
caller continues to observe the original source value exactly as required by the
Value and Snapshot Semantics rules.

An exclusive mutable authority may be subdivided into child authorities only
when those child authorities are mutually disjoint. While a child authority is
live, its parent authority must not be used concurrently to read or mutate the
same logical mutable state in a way forbidden by that child's exclusivity. When
all relevant child authorities complete, the parent authority may be
reconstituted according to the eventual partition API.

For Core byte regions, representation, validation, recomposition, and public
surface are closed by §§71.18-71.20 below. Generic writable partitioning for Array or arbitrary object graphs is not
part of Core v0.1, as closed explicitly by §71.5A; the byte mechanism does
not imply such authority.

### 71.5A No generic writable graph partitioning in Core

Core v0.1 does not standardize a general writable-partition facility for
`Array`, arbitrary objects, or arbitrary reachable mutable object graphs.

This is a semantic boundary, not an implementation omission.

Non-overlapping container indexes, slot names, byte addresses, storage pages, or
implementation-level memory ranges do not by themselves prove that the mutable
state reachable through those positions is logically disjoint. In particular:

```text
array[0] -> sharedMutableObject
array[1] -> sharedMutableObject
```

means that disjoint Array indexes still reach the same mutable authority.
Granting independent writable partition authority to those indexes would expose
simultaneous shared mutable Protos state and would violate the P isolation model.

Core therefore provides no standard:

- `Array.parallelRange(...)` writable-region analogue;
- generic `Object.partition(...)` or graph-region capability;
- runtime alias-analysis API that grants writable P authority;
- borrow/ownership annotation system;
- user-visible uniqueness, move-only, affine, or linear reference mode;
- dynamic "prove disjoint" operation whose success depends on
  implementation-selected heap analysis.

`Bytes`/`ByteRegion` remains the standardized Core writable-partition facility
because its mutable authority is defined exactly over byte-indexed state and can
be bounded by explicit non-overlapping intervals without granting authority over
arbitrary reachable mutable objects.

This does not prohibit parallel algorithms over Arrays or objects. Such
algorithms may use ordinary P snapshot/value semantics, produce fresh results,
use immutable/read-only inputs, or internally exploit semantics-preserving
representation optimizations. What Core does not provide is simultaneous
writable authority over arbitrary logical object graphs merely because a
container representation can be physically partitioned.

A future facility may add broader writable partitioning only if it introduces a
portable semantic proof of disjoint mutable authority. That proof must be
language/runtime-defined rather than dependent on one implementation's escape,
alias, GC, pointer, or storage analysis. If broader ownership/capability
semantics are ever introduced, they must justify their global language cost
independently rather than being smuggled in as an Array optimization.

### 71.6 Library-Level Parallel Patterns

High-level parallel algorithms should normally be library facilities built on
the minimal runtime guarantees rather than separate language primitives or
separate fundamental task kinds.

This section is the primary normative owner of the concurrency-domain semantics
for the Core v0.1 standard parallel Array operations defined in §71.6A–§71.6E:

-   `Array.parallelMap(...)`
-   `Array.parallelFilter(...)`
-   `Array.parallelFindIndex(...)`
-   `Array.parallelReduce(...)`
-   `Array.parallelSort(...)`

Those standardized operations have the exact names and contracts defined below.
Other high-level parallel patterns, including additional partitioned collection
processing or parallel pipelines, remain library/API design space unless another
normative section standardizes them explicitly.

A standard or third-party library may choose chunking, reduction trees,
partition strategy, batching, or algorithm-specific policy only where the
operation's normative contract leaves that choice unobservable and the runtime
continues to enforce the underlying isolation and scheduling guarantees.

Physical scheduling policy must not accidentally become an observable semantic
choice. If a standard parallel library operation promises a deterministic
result, every ordering, combination, conflict, or failure-selection decision
capable of changing that result must be defined by a logical rule independent of
worker count, carrier count, chunk timing, queue order, or work-stealing order.

For example, a deterministic parallel reduction whose operator is observably
non-associative must define a canonical logical combination structure or other
equivalent deterministic rule; an implementation may not choose a different
observable parenthesization merely because it used a different number of
workers. A library may expose intentionally nondeterministic behavior only when
that nondeterminism is part of the library operation's specified contract rather
than an accidental consequence of runtime scheduling.

### 71.6A Standard `Array.parallelMap(...)`

Core v0.1 standardizes:

```text
array.parallelMap(worker, arguments...)
    -> Future
```

`parallelMap` is standard Array behavior reached through ordinary message lookup.
It introduces no syntax, iterator object, stream/pipeline object, Task object, or
new executable value kind.

The original receiver must satisfy the standard Array receiver-domain contract.
`worker` must be invokable through the ordinary polymorphic invocation protocol;
it need not be a Closure. When a Closure occurs in a P input graph, the existing
Closure-projection rules apply.

The logical operation is indexed by the source Array's ascending indexes. At the
operation boundary, after ordinary receiver/argument evaluation, Array receiver
validation, and worker-callability validation, the operation captures the
source Array's current element-reference sequence in ascending index order.

For a non-empty source, before `parallelMap` successfully returns its Future, it
must establish all logical P inputs needed by the per-index worker invocations.
For source index `i`, the worker is invoked in an isolated child P domain as:

```text
worker(sourceSnapshot[i], arguments...)
```

Each index is a separate P isolation domain. Mutable worker state, mutable
element state, and mutable explicit arguments therefore do not become shared
mutable authority between worker invocations. If the same mutable source object
appears at several Array indexes, each child receives an isolated logical value
for its own invocation; no cross-child mutable alias is created.

Within one child invocation graph, ordinary P graph rules preserve cycles and
aliasing among that element, the worker, and explicit arguments. Across distinct
child P domains there is no shared mutable Protos identity.

All required non-empty child input graphs are validated before any child becomes
eligible to execute and before the operation returns its Future. If an input
needed by any child cannot cross P, the call synchronously signals
`NonParallelValue`, creates no result Future, and makes no child eligible.
Validation is conceptually in ascending source-index order when an observable
choice is required.

For an empty source Array, no worker invocation exists and no P boundary is
crossed. The worker is still required to be ordinarily invokable, but
P-transferability of the otherwise-unused worker/explicit arguments is not
required. The operation returns a Future already resolved with a fresh empty
standard Array.

For a non-empty source, successful return of the result Future is the complete
logical input snapshot point. Later caller-domain mutation of the original Array,
its former elements, the original worker object, or explicit argument objects
cannot change any child input.

Worker invocations may execute simultaneously and may begin or complete in any
physical order. The result, however, is deterministic by source index:

```text
result.size == sourceSnapshot.size
result[i] == successful result of worker(sourceSnapshot[i], arguments...)
```

The resolved value is one fresh standard Array. No partial result Array is
published before successful completion of every logical worker invocation and
successful P-boundary transfer of every worker result.

A worker's normal result crosses from its child P domain by the ordinary P result
rules. An untransferable worker result is a failure for that source index with
caller-domain `NonParallelValue`.

If more than one source index fails, including worker-signaled Error,
cancellation-originating failure where applicable, or untransferable-result
failure, the operation's deterministic failure is the failure belonging to the
lowest failing source index. Scheduler timing, carrier count, chunking, or worker
completion order must not select a different failure.

The result Future cannot resolve successfully while any logical source-index
invocation remains incomplete. An implementation may stop/cancel work at indexes
whose outcomes can no longer affect the specified final result, provided that
doing so cannot change any Protos-observable behavior.

Cancelling the result Future requests cooperative cancellation of unfinished
child P work under the ordinary structured-concurrency rules. Cancellation
publishes no partial result Array. Races between cancellation and an already
available terminal operation outcome follow the ordinary first-terminal-
transition Future rule.

`parallelMap` does not promise a particular worker count, chunk size, task count,
carrier count, SIMD width, or actual overlap. An implementation may batch,
fuse, inline, vectorize, or sequentialize physical execution when the complete
observable contract above is preserved.

### 71.6B Standard `Array.parallelFilter(...)`

Core v0.1 standardizes:

```text
array.parallelFilter(predicate, arguments...)
    -> Future
```

`parallelFilter` is standard Array behavior reached through ordinary message
lookup. It introduces no new syntax, iterator/stream identity, Task identity, or
writable Array partition authority.

The receiver must satisfy the standard Array receiver-domain contract.
`predicate` must be invokable through the ordinary polymorphic invocation
protocol and need not be a Closure. Closure values that cross P follow the
existing projection rules.

After ordinary receiver/argument evaluation, Array receiver validation, and
predicate-callability validation, the operation captures the source Array's
current element-reference sequence in ascending index order.

For every source index `i`, the logical predicate invocation is:

```text
predicate(sourceSnapshot[i], arguments...)
```

executed in an isolated child P domain. Each source index is a separate P
isolation domain exactly as for `Array.parallelMap`; no mutable Protos identity
is shared between predicate invocations merely because the same source element,
predicate object, or explicit argument object appeared more than once.

For a non-empty source, all logical child P input graphs must be validated and
snapshotted before any child becomes eligible and before the operation
successfully returns its Future. If any required child input cannot cross P, the
call synchronously signals `NonParallelValue`, creates no result Future, and
makes no child eligible. When an observable validation choice is required,
source indexes are considered in ascending order.

For an empty source, no predicate invocation exists and no P boundary is
crossed. Ordinary receiver and predicate-callability validation still occurs,
but P-transferability of otherwise-unused predicate/argument values is not
required. The operation returns a Future already resolved with a fresh empty
standard Array.

A predicate invocation must complete normally with exactly the canonical
Boolean object `true` or `false`. Protos has no language-wide truthiness
conversion. A normal predicate result that is neither canonical `true` nor
canonical `false` is an indexed `InvalidPredicateResult` failure. The standard
`InvalidPredicateResult` error delegates directly to `Error`.

A `true` result selects the corresponding snapshotted source element. A `false`
result rejects it. On successful completion of all predicate invocations, the
result Future resolves with one fresh standard Array containing exactly the
selected source elements in ascending original source-index order.

Selection order is therefore stable and independent of predicate start order,
completion order, carrier count, work stealing, or physical chunking. The
operation does not return predicate results; it returns the selected source
values after their ordinary P result/publication crossing needed to construct
the caller-domain result.

No partial result Array is published. If more than one source index fails —
whether through a predicate-signaled Error, `InvalidPredicateResult`,
untransferable selected result, or another indexed P failure — the operation
fails with the failure belonging to the lowest failing source index. Scheduler
timing never chooses the reported failure.

A source element rejected by a `false` predicate need not be transferred back as
a result value. A selected element must be publishable under the ordinary P
result/value rules; if it is not, that source index fails with caller-domain
`NonParallelValue`.

Cancelling the result Future requests cooperative cancellation of unfinished
predicate P work under the ordinary structured-concurrency rules and publishes
no partial result Array. The normal first-terminal-transition Future rule governs
races with an already available terminal operation outcome.

`parallelFilter` does not promise a worker count, chunk size, task count,
carrier count, SIMD width, or actual simultaneous execution. Implementations may
batch, fuse, inline, vectorize, or sequentialize physical execution only when
the complete per-index isolation, stable-selection, deterministic-failure, and
publication contract remains observationally identical.

### 71.6C Standard `Array.parallelFindIndex(...)`

Core v0.1 standardizes:

```text
array.parallelFindIndex(predicate, arguments...)
    -> Future
```

The Future resolves with the semantic Integer index of the first matching source
element, or with `null` when no source element matches.

Returning an index rather than the selected element keeps `null` unambiguous:
`null` means absence of a matching index even when an actual Array element is
itself `null`.

`parallelFindIndex` is standard Array behavior reached through ordinary message
lookup. The receiver must satisfy the standard Array receiver-domain contract.
`predicate` must be invokable through the ordinary polymorphic invocation
protocol and need not be a Closure. Closure values crossing P follow the existing
projection rules.

After ordinary receiver/argument evaluation, Array receiver validation, and
predicate-callability validation, the operation captures the source Array's
current element-reference sequence in ascending index order.

For every source index `i`, the logical predicate invocation is:

```text
predicate(sourceSnapshot[i], arguments...)
```

in an isolated child P domain. Per-index input isolation, Closure projection,
graph preservation within one child, and absence of shared mutable identity
between distinct child invocations are exactly the same as for
`Array.parallelMap` and `Array.parallelFilter`.

For a non-empty source, all logical child P input graphs are validated and
snapshotted before any child becomes eligible and before the operation
successfully returns its Future. Any required input that cannot cross P causes
synchronous `NonParallelValue`; no result Future is created and no child becomes
eligible. Observable validation order is ascending source-index order.

For an empty source, no predicate invocation exists and no P boundary is crossed.
Ordinary receiver and predicate-callability validation still occurs, but
P-transferability of otherwise-unused predicate/argument values is not required.
The operation returns a Future already resolved with `null`.

Predicate results use the same strict Boolean contract as
`Array.parallelFilter`: only canonical `true` and canonical `false` are valid.
Any other normal predicate result records `InvalidPredicateResult` for that
source index.

The logical search order is ascending source index. Physical execution may occur
in any order, but the operation behaves as if indexes were examined in ascending
order until the first decisive outcome.

For an index, the outcomes are:

```text
false
    -> continue logical search

true
    -> successful decisive outcome: resolve with that index

failure
    -> failing decisive outcome: fail with that indexed failure
```

The terminal result is determined by the lowest source index whose completed
logical outcome is either `true` or failure, once every lower index is known to
have completed with `false`.

Therefore:

- a `true` at index `i` cannot resolve the operation while any lower index is
  still unresolved;
- a failure at index `j < i` defeats a later `true` at index `i`;
- a failure at index `j > i` is irrelevant once index `i` is established as the
  first match;
- if every index completes with `false`, the Future resolves with `null`;
- if no earlier `true` exists and failures occur, the lowest failing index is the
  reported failure.

This rule is deterministic and independent of worker start/completion order,
carrier count, chunking, work stealing, or scheduler timing.

Once a decisive index is established, an implementation may cancel, abandon, or
avoid still-unneeded higher-index work when doing so cannot change any
Protos-observable behavior. Such pruning is an optimization, not a different
search result or failure rule.

Cancelling the result Future requests cooperative cancellation of unfinished
predicate P work under the ordinary structured-concurrency rules. The ordinary
first-terminal-transition Future rule governs races with an already established
search outcome.

`parallelFindIndex` publishes no partial collection and grants no writable Array
partition authority. It does not promise any worker count, chunk size, task
count, carrier count, SIMD width, or actual simultaneous execution. Batching,
fusion, vectorization, sequential execution, and work stealing are allowed only
when observationally equivalent to the logical per-index search defined above.

### 71.6D Standard `Array.parallelReduce(...)`

Core v0.1 standardizes:

```text
array.parallelReduce(reducer, arguments...)
    -> Future
```

The Future resolves with the canonical reduction result, or with `null` for an
empty source Array.

`parallelReduce` is a parallel reduction, not a promise to reproduce an
unspecified sequential fold. Core does not require the reducer to be associative
and does not let worker count or scheduler policy choose the parenthesization.
Instead, Core defines one canonical logical reduction tree.

The receiver must satisfy the standard Array receiver-domain contract. `reducer`
must be invokable through the ordinary polymorphic invocation protocol and need
not be a Closure. Closure values crossing P follow the ordinary projection
rules.

After ordinary receiver/argument evaluation, Array receiver validation, and
reducer-callability validation, the operation fixes a logical submission
snapshot of the source element-reference sequence, reducer state, and explicit
argument state. Any value that must cross a P boundary is governed by the
ordinary P copy/projection/transfer rules. Later caller-domain mutation cannot
change any logical reduction input.

For an empty source, no reducer invocation exists and no P boundary is crossed.
P-transferability of otherwise-unused reducer/argument values is not required.
The operation returns a Future already resolved with `null`.

For a one-element source, no reducer invocation occurs. The sole source value is
nevertheless snapshotted through the ordinary P value boundary so the successful
call fixes its reduction value independently of later caller mutation. If that
value cannot cross P, the call synchronously signals `NonParallelValue`. On
success the Future resolves with the corresponding caller-domain transferred
value.

For two or more source elements, all source values and the reducer/explicit
argument submission state required to begin the canonical reduction must be
validated/snapshotted before any reduction child becomes eligible and before the
operation successfully returns its Future. A required input that cannot cross P
causes synchronous `NonParallelValue`, creates no result Future, and makes no
child eligible.

The canonical reduction proceeds in logical rounds. A round consumes its input
sequence from left to right in adjacent pairs:

```text
[x0, x1, x2, x3, x4]

round 1:
    reducer(x0, x1, arguments...)
    reducer(x2, x3, arguments...)
    x4

round 2:
    reducer(r01, r23, arguments...)
    x4

round 3:
    reducer(r0123, x4, arguments...)
```

More generally, pair positions `(0,1)`, `(2,3)`, `(4,5)`, ... are combined.
When a round has an odd final value, that value is carried unchanged into the
next logical round. Rounds repeat until exactly one value remains.

Every reducer invocation executes in its own isolated child P domain. Its two
logical operand values plus the reducer and explicit arguments form that child's
P input graph. Aliasing and cycles among values that enter the same child are
preserved by ordinary P graph rules; distinct reducer invocations do not acquire
shared mutable Protos identity.

A reducer normal result crosses out of its child by ordinary P result rules and
becomes the logical value supplied to a later canonical node. An untransferable
normal result records caller-domain `NonParallelValue` as that node's failure.

Logical rounds impose a deterministic failure boundary. A later logical round
does not exist unless every combine node in the preceding round completed
successfully. If more than one combine node in one round fails, the failure from
the leftmost failing pair in that round is the operation failure. Thus scheduler
timing and worker completion order never select among concurrent failures.

A conforming implementation may pipeline or speculatively execute work from a
later canonical round only when doing so is observationally invisible. Such
speculation cannot replace the specified earlier-round failure, publish a value
that the canonical tree would not reach, or expose mutable state/effects from
logically nonexistent later work.

On successful completion, the last canonical value crosses to the caller domain
under the ordinary P result rules and resolves the result Future. No intermediate
partial reduction state is published.

Because the canonical tree is fixed, non-associative reducers are deterministic.
For example, subtraction follows the specified adjacent-pair tree rather than an
implementation-selected chunking tree. An implementation may reassociate only
when the invoked API's semantics independently make that reassociation
unobservable.

Cancelling the result Future requests cooperative cancellation of unfinished P
reduction work under the ordinary structured-concurrency rules and publishes no
partial result. The ordinary first-terminal-transition Future rule governs races
with an already established terminal reduction outcome.

`parallelReduce` does not promise a worker count, chunk size, Task count, carrier
count, SIMD width, or actual simultaneous execution. Physical batching, fusion,
vectorization, sequential execution, work stealing, and storage reuse are
allowed only when observationally equivalent to the canonical logical tree.

### 71.6E No standard `Array.parallelEach(...)` in Core

Core v0.1 does not standardize `Array.parallelEach(...)` or another parallel
iteration operation whose element-worker results are discarded.

This is an API boundary derived from the existing P effect model rather than a
restriction on physical execution.

A Core P computation is an isolated CPU-computation domain. It does not inherit
the caller Actor's mutable state, sender identity, mailbox, ambient I/O
capabilities, Process/Node/Cluster authority, or another standard external-effect
channel merely because work is eligible to run simultaneously.

Consequently, a generic operation of the form:

```text
array.parallelEach(worker, arguments...)
```

would have no additional standard publication channel beyond the same per-index
P result/failure boundary already provided by `Array.parallelMap(...)`. Discarding
those normal results would remove information without adding a new semantic
capability.

Core therefore prefers the existing composable operation:

```text
array.parallelMap(worker, arguments...)
    -> Future<Array>
```

when independent per-element parallel computation is required. A caller may
ignore the successfully resolved result Array when its values are not needed,
but Core does not add a second standard operation merely to suppress that result.

This decision also prevents an iteration-shaped API from implying that P workers
may rely on hidden shared mutation, Actor messaging, I/O, native global state, or
other externally observable side effects. Those capabilities remain governed by
their existing P-transfer/effect rules and are not made valid by choosing an
`each`-like spelling.

An implementation may internally avoid materializing result storage when it can
prove that doing so is observationally equivalent to the actual standard
operation being executed. Such dead-result elimination is an optimization, not
a distinct Core protocol.

A future P-safe effect capability or a future API with independently useful
completion/failure/resource semantics may justify a parallel iteration facility.
If introduced, that facility must define its effect authority, result/failure
meaning, cancellation, ownership, ordering, and P transfer semantics explicitly
rather than inheriting them from an otherwise result-discarding loop.

### 71.6F Standard `Array.parallelSort(...)`

Core v0.1 standardizes:

```text
array.parallelSort(less, arguments...)
    -> Future
```

The Future resolves with one fresh standard Array containing the source values in
the canonical stable sorted order defined below. The source Array is not mutated.

`less` must be invokable through the ordinary polymorphic invocation protocol and
need not be a Closure. Each comparator result must be exactly canonical `true` or
canonical `false`; another normal result is `InvalidComparatorResult`. The
standard `InvalidComparatorResult` error delegates directly to `Error`.

Core does not let the implementation choose an observable sorting algorithm.
`parallelSort` is defined by one canonical logical stable merge-sort tree.

After ordinary receiver/argument evaluation, standard Array receiver validation,
and comparator-callability validation, the operation captures the source Array's
element-reference sequence in ascending index order.

For empty input, no comparator invocation or P boundary exists. The operation
returns a Future already resolved with a fresh empty standard Array.

For singleton input, no comparator invocation occurs. The sole element is
snapshotted/transferred through the ordinary P value rules before successful
submission completes. An untransferable singleton causes synchronous
`NonParallelValue`. On success, the Future resolves with a fresh one-element
standard Array containing the caller-domain transferred value.

For two or more elements, all source values plus comparator and explicit argument
state required by the canonical sort are validated/snapshotted before any sort
child becomes eligible and before the operation successfully returns its Future.
A required non-transferable input causes synchronous `NonParallelValue`, creates
no result Future, and makes no child eligible.

The canonical logical split of a sequence of length `n >= 2` is:

```text
leftLength  = floor(n / 2)
rightLength = n - leftLength

left  = first leftLength values
right = remaining rightLength values
```

Both halves are recursively sorted by the same rule. Their successful sorted
results are then merged from left to right.

For each current merge pair `(leftValue, rightValue)`, Core evaluates two
isolated comparator invocations:

```text
lr = less(leftValue, rightValue, arguments...)
rl = less(rightValue, leftValue, arguments...)
```

Each invocation is a separate child P domain governed by the ordinary P
copy/projection/result rules. Both Boolean outcomes belong to one logical merge
decision.

The decision table is:

```text
lr == true  && rl == false
    -> take leftValue

lr == false && rl == true
    -> take rightValue

lr == false && rl == false
    -> values are equivalent for this merge decision
    -> take leftValue first (stable tie)

lr == true  && rl == true
    -> fail with InvalidComparatorOrder
```

`InvalidComparatorOrder` delegates directly to `Error`.

When one side of a merge is exhausted, the remaining values of the other side
are appended unchanged. Stability is therefore normative: values that compare
equivalent preserve their original source-index order.

The two recursive child sorts of one logical node may execute simultaneously.
The merge of that node exists only after both child sorts succeed. For failures
from the two child sorts, the left child has deterministic precedence over the
right child. Within one merge decision, `lr` has failure precedence over `rl`.
Across successive merge decisions, the earlier output position has precedence
over later positions. Thus scheduler timing never selects the reported failure.

A comparator result that cannot cross its child P boundary is treated as
caller-domain `NonParallelValue` at that comparator invocation and participates
in the same canonical failure ordering.

The canonical merge tree and comparison schedule are semantic. A conforming
implementation may use another physical algorithm, vectorization, chunking,
sampling, fusion, in-place temporary buffers, or work stealing only when every
observable result, stable ordering, comparator invocation/failure decision, and
publication outcome is identical to the canonical definition.

This requirement intentionally favors portable semantics over giving arbitrary
stateful or inconsistent comparators implementation-dependent behavior. A
well-behaved strict ordering naturally satisfies the canonical contract, while
encountered contradictory pair ordering (`a < b` and `b < a`) fails
deterministically rather than being resolved by sort internals.

On success, every selected output value crosses to the caller domain according
to ordinary P result/value semantics and the Future resolves with the fresh
sorted Array. Failure or cancellation publishes no partial Array.

Cancelling the result Future requests cooperative cancellation of unfinished P
sort/comparator work under ordinary structured-concurrency rules. The ordinary
first-terminal-transition Future rule governs races with an already established
terminal sort outcome.

### 71.7 Scheduling and Oversubscription

Requesting many parallel computations does not imply creating the same
number of operating-system threads.

The runtime owns CPU admission and scheduling for isolated parallel work
and must be able to multiplex many logical work items over bounded CPU
carrier resources.

The scheduler may use a shared worker pool, work stealing, locality-aware
queues, adaptive granularity, inline execution, or other mechanisms.

Parallel eligibility is not a semantic promise that another core will
always be used. If executing a small operation inline or sequentially is
more efficient, the runtime may do so provided that all observable
semantics remain unchanged.

This preserves the pay-for-what-you-use principle and prevents nested or
multi-Actor parallelism from requiring unbounded operating-system-thread
creation.

Nested isolated parallelism must remain capable of progress with bounded CPU
carriers. A parent parallel computation waiting for child parallel work must not
semantically require an additional unused operating-system thread or carrier to
exist before that child can run. An implementation may satisfy this requirement
through continuation scheduling, helping, work stealing, inline execution,
carrier release, or another mechanism whose choice is not observable.

This is a progress requirement, not a promise that every submitted parallel
operation begins immediately or receives a dedicated core.

### 71.7A P admission and weak fairness

A successfully submitted isolated parallel computation is an admitted logical P
work item. Admission does not imply a dedicated carrier, immediate execution, or
simultaneous execution relative to its creator.

For scheduling fairness, a P work item is **runnable** when it is live, not
terminal, and all semantic prerequisites for its next P execution segment are
satisfied. A successfully submitted `Closure.parallel(...)` computation is
runnable for its initial segment unless cancellation makes only its portable
cancellation-observation boundary runnable. A P task suspended on a pending
Future or other explicit semantic prerequisite is not runnable until that
prerequisite is satisfied.

Weak fairness applies to runnable P work:

> If a live P work item remains continuously runnable and the Process repeatedly
> reaches scheduling points capable of running P work, that item must eventually
> receive an execution segment or become non-runnable/terminal for an
> independently defined semantic reason.

Later submissions, work stealing, locality preference, granularity choices,
different originating Actors, or nested submission depth must not postpone one
continuously runnable P item forever.

This is weak fairness only. It does not promise equal CPU shares, bounded
latency, round-robin scheduling, a dedicated carrier, a particular worker-pool
size, or actual simultaneous execution.

Nested P must satisfy the same rule using bounded carriers. In particular:

```text
parent P waits on child P Future
    -> parent is not runnable while the child is pending
    -> runnable child/descendant work may use any P-capable carrier
    -> progress must not require an additional unused carrier
```

If every occupied carrier reaches a state in which its P computation is waiting
for runnable descendant P work, the runtime must make descendant progress
possible using those bounded carrier resources. It may release a carrier,
schedule a continuation, help/steal descendant work, execute a child inline, or
use another observationally equivalent mechanism. Deadlock caused solely by
"all carriers are occupied by ancestors waiting for descendants" violates this
rule.

This closes semantic admission/fairness, not scheduler policy. Queue topology,
work-stealing algorithm, carrier count, locality policy, priority heuristics,
chunk size, adaptive granularity, and similar mechanisms remain implementation
choices subject to the fairness and scheduler-independence rules.

### 71.7B Scheduling policy is not a Core semantic surface

Core v0.1 does not standardize a portable P scheduler policy beyond the
observable progress and determinism constraints already defined by this section.

In particular, Core defines no portable semantic value, API, option, directive,
annotation, environment setting, or introspection result for any of the
following:

- worker-pool size or carrier count;
- queue topology or queue discipline;
- work-first versus help-first execution;
- local versus global queues;
- work-stealing deque representation or victim-selection policy;
- steal frequency, batch size, or steal threshold;
- chunk size or partition grain;
- adaptive granularity thresholds;
- inline/sequential fallback thresholds;
- task fusion, splitting, batching, or coalescing policy;
- NUMA/locality preference;
- CPU affinity or carrier pinning;
- priority or aging heuristic;
- load-sampling interval;
- hardware-sensitive or workload-sensitive cost model.

A runtime may choose or change any of these policies dynamically according to
hardware, current load, profiling, historical measurements, nesting depth,
allocation pressure, locality, or another implementation concern.

Such choices are conforming only while preserving every already-defined Core
observable constraint, including:

- P input snapshot and isolation semantics;
- per-operation deterministic result/order/failure contracts;
- weak fairness for continuously runnable P work;
- bounded-carrier nested progress;
- structured ownership and lifetime;
- cooperative cancellation semantics;
- Actor turn isolation;
- P process-locality;
- effect/authority boundaries;
- ByteRegion reservation/publication rules;
- the requirement that physical scheduling not become an accidental semantic
  selector.

Changing scheduler policy alone must therefore never change the semantic result
of a deterministic Core program, select a different observable failure, expose a
different mutation/publication order where Core has fixed one, permit starvation
for continuously runnable admitted P work, or introduce a deadlock that exists
only because ancestors occupy all carriers while runnable descendants wait.

Conversely, Core does not promise equal CPU shares, bounded scheduling latency,
a specific amount of parallel speedup, a dedicated worker for any logical P
operation, a fixed number of simultaneous workers, a particular cache/NUMA
placement, or the use of work stealing at all.

Implementations may expose administrative diagnostics about their current
scheduler configuration or runtime behavior outside the portable Core language
surface. Such diagnostics are implementation facts and must not become inputs to
portable Core semantics.

A future explicit resource-governance or performance-control facility may expose
selected scheduling controls only if it defines their observable contract,
scope, ownership, portability, interaction with fairness, and failure behavior.
That future facility is not implied by the existence of the internal P
scheduler.

### 71.7C NUMA-Aware Scheduling Is Not a Core Semantic Surface

Core v0.1 assigns no portable semantic meaning to NUMA topology, memory-node
identity, socket/package topology, cache hierarchy, CPU locality, carrier
affinity, or the placement of P work relative to those physical resources.

A runtime may use NUMA-aware scheduling, memory placement, work stealing,
replication, migration, pinning, or topology-sensitive cost models internally.
It may also ignore NUMA entirely. Either choice is conforming only while
preserving the already-defined P isolation, snapshot, deterministic
result/failure, weak-fairness, bounded-carrier progress, Actor-turn isolation,
and scheduler-policy rules.

In particular, portable Core code cannot:

- request a NUMA node or memory domain;
- observe which NUMA node executed a P work item;
- require data allocation on a particular NUMA node;
- infer NUMA placement from Actor, Process, Node, or Cluster identity;
- require a stable affinity between one logical P work item and one physical CPU,
  package, socket, cache, or NUMA node;
- treat a topology-sensitive optimization choice as a semantic success/failure
  condition.

Physical locality may influence performance, but not the result, failure
selection, publication order, transfer semantics, or progress obligations already
fixed by Core.

A future explicit hardware-placement/performance-control extension may expose
selected topology information or placement controls only by defining their own
portable contract, scope, failure behavior, and interaction with isolation and
fairness. Such an extension is not implied by Core's internal scheduler.

This closes the former open ledger item `NUMA-aware scheduling`.

### 71.8 Failure and Cancellation

Failure of an isolated parallel computation fails its result Future
according to normal Future error semantics. It does not by itself invoke
Actor supervision or Actor replacement semantics because the parallel
work is not an Actor.

Cancellation is cooperative and follows the same portable Future and
structured-concurrency observation boundaries as other task-backed asynchronous
work. P does not introduce implementation-selected cancellation safe points.

Before the first ordinary Protos instruction of newly started P work, a pending
cancellation request is observed at the mandatory first-execution cancellation
boundary. After ordinary P execution has begun, cancellation becomes observable
only at already-defined explicit suspension/resume boundaries or at operations
whose normative contract is cancellation-aware.

Method calls, allocations, loop back-edges, interpreter/JIT polls, garbage
collection, carrier time slices, worker-pool checks, work-stealing boundaries,
SIMD/vectorization boundaries, host-thread interruption, or similar physical
runtime events do not by themselves create P cancellation observation points.

Consequently, CPU-bound P code that has already begun and reaches no explicit
suspension or cancellation-aware operation may complete normally despite an
outstanding cancellation request. A runtime may poll, interrupt, migrate, or
discard physical work internally only when doing so cannot change which
Protos-visible cancellation boundary wins or otherwise change the Future's
specified observable outcome.

If the owning structured context or Actor terminates, outstanding
parallel child work follows the corresponding Future ownership and
cancellation rules. A completed parallel result does not acquire an
independent lifetime merely because it was computed on another CPU
carrier.

Failure and cancellation preserve the publication boundary defined above:
partially mutated isolated parallel state does not become a mutation of the
caller's original value and is not published as a successful result.

When one logical parallel library operation contains several child computations,
runtime race timing must not silently select one of several concurrently
available observable failures. A deterministic operation must define a
deterministic logical failure-selection rule or an explicit aggregate failure
contract. An operation may expose nondeterministic failure selection only when
that nondeterminism is part of its specified semantics.

This rule does not impose one universal failure-selection policy on all future
parallel APIs. It prohibits an otherwise deterministic API from making
worker-completion timing, carrier scheduling, or queue order the hidden selector.

### 71.9 Locality and Distribution

The purpose of this facility is efficient CPU parallelism without
requiring persistent Actor structure.

The initial semantic direction does not require isolated parallel work
to become a distributed execution abstraction. Process-local execution
is sufficient to realize the core benefit and avoids imposing remote
placement, discovery, delivery, and failure semantics on fine-grained
parallel computation.

A future explicit remote-compute facility may reuse compatible value and
isolation rules, but Core remote placement is excluded by §71.9A rather than
left to implementation choice.

### 71.9A Core P is process-local

Core v0.1 `Closure.parallel(...)` executes only within the current Protos
Process. Its semantic contract does not include remote placement, Node selection,
Cluster routing, code shipment, remote bootstrap, network transport, remote
failure detection, or distributed result recovery.

This is a normative locality boundary, not merely a minimum implementation
requirement.

A conforming implementation must not choose to execute a Core P computation in
another Protos Process when doing so could introduce distributed-observable
behavior that Core P does not define. In particular, Core P must not make any of
the following newly observable merely because a runtime has remote capacity
available:

- network reachability or partition state;
- remote Process/Node/Cluster lifecycle;
- remote code availability/version mismatch;
- transport serialization format or schema compatibility;
- placement/routing policy;
- remote authentication/authorization;
- retry, duplicate execution, or delivery uncertainty;
- distributed clock/timeout behavior;
- failure distinctions that do not exist for process-local P.

The existing P value/snapshot rules are therefore process-local isolation rules,
not an implicit distributed-serialization contract. A value being P-transferable
does not imply that it is serializable for an arbitrary network transport, and a
projectable Closure does not imply that its executable body is remotely
available under a portable code-identity/versioning scheme.

An implementation may physically execute P work on any CPU carrier, OS thread,
core, NUMA node, accelerator, or equivalent execution resource that belongs to
the same Protos Process execution domain, provided every existing P semantic rule
is preserved. Physical machine topology is not itself the semantic boundary; the
Protos Process is.

A future explicit remote-compute facility may reuse compatible P isolation,
snapshot, projection, determinism, or Future-result rules, but it must define its
own remote placement, code identity/availability, serialization, transport,
authentication, cancellation, retry, uncertainty, failure, and lifecycle
semantics. Such a facility is not `Closure.parallel(...)` with an
implementation-selected remote scheduler.

### 71.10 Architectural Boundary

The runtime/kernel must provide only the mechanisms that libraries cannot
safely implement on their own, including:

-   Eligibility for true simultaneous CPU execution
-   Enforcement of isolation from Actor-local mutable state
-   Safe value/snapshot crossing
-   Immutable physical sharing where valid
-   Exclusive mutable partition guarantees where supported
-   Bounded CPU scheduling/admission
-   Integration with Future completion, failure, ownership, and
    cancellation

Higher-level parallel algorithms and policies belong in libraries unless
they require additional fundamental semantic guarantees.

This design deliberately does not add arbitrary shared mutable memory,
locks, atomics, memory-order annotations, or Rust-style general ownership
syntax to normal Protos code.

Principle:

> Protos should permit efficient physical sharing and parallel execution
> wherever the runtime can preserve simple logical isolation, while
> exposing programmer-visible synchronization machinery only if a future
> workload proves that the simpler model is fundamentally insufficient.

### 71.11 Effect and Authority Boundary

Isolated parallel computation is a CPU-computation domain, not a second
Actor-like effects domain.

A parallel computation does not inherit the originating Actor's sender identity,
mailbox identity, lifecycle authority, Process authority, Node or Cluster
authority, ambient I/O resources, active dynamic handlers, or other
Actor-/runtime-local authority merely because the computation was created by
that Actor.

In particular, parallel execution does not silently perform Actor `send()` or
`request()` operations as though they had been issued by the originating Actor.
Doing so would make simultaneous parallel scheduling observable through
same-sender FIFO and would violate the originating Actor's serialized issuance
model.

Core parallel execution therefore permits ordinary isolated computation,
allocation, mutation of its own isolated state, and nested parallel computation
subject to this section. Objects whose meaning is an authority to affect or
observe state outside that isolated computation are not automatically valid
parallel-boundary values.

Until a later normative facility defines otherwise, the following must not be
made usable inside isolated parallel execution merely by copying or forwarding a
caller-held reference:

-   `ActorRef` and `GroupRef` communication capabilities
-   pending Future/task identity or Actor-local continuations
-   Process, Node, Cluster, placement, lifecycle, or administrative authority
-   open filesystem, network, process, terminal, or other I/O capabilities
-   any capability whose operation would use the caller Actor's identity or
    mutate/observe caller-local mutable runtime state

A future facility may define a specifically P-safe capability and its crossing,
ordering, failure, and authority semantics. Such a facility is not implied by
ordinary Actor transferability. In particular, the fact that `ActorRef` or
`GroupRef` may cross an Actor message boundary does not make it valid across the
parallel-computation boundary.

The normal architectural pattern is:

```text
Actor / cooperative code
    -> establish isolated parallel inputs
    -> P computes
    -> completed value or failure returns
    -> Actor / cooperative continuation performs messaging or I/O
```

This preserves Actor sender ordering, keeps I/O and external effects in domains
whose authority and lifetime are already defined, and prevents P from acquiring
persistent identity merely to explain effects.

### 71.12 Scheduler Independence

Parallel eligibility grants permission for simultaneous execution; it does not
grant the scheduler authority to choose otherwise unspecified observable
behavior.

For a deterministic parallel operation, changing any of the following alone must
not change its semantic result:

-   number of CPU cores;
-   number of runtime carriers;
-   worker-pool size;
-   work-stealing decisions;
-   queue order;
-   chunk timing;
-   whether eligible work runs inline or on another carrier.

This does not prohibit APIs whose contract explicitly includes nondeterministic
selection. It requires such nondeterminism to be semantic and documented rather
than an accidental leak of implementation scheduling.

### 71.12A SIMD/vectorization is semantically invisible

Core v0.1 introduces no SIMD value kind, vector register object, lane-count
property, vector-width query, alignment requirement, target-instruction-set
capability, or programmer-visible distinction between scalar and vectorized
execution.

An implementation may use SIMD, SLP vectorization, loop vectorization, masked
lanes, vector reductions, target-specific vector instructions, or equivalent
data-parallel machinery only as an observationally invisible optimization of
otherwise valid Protos execution.

The legality rule is:

> Replacing scalar/logical execution with vectorized physical execution is
> permitted only when every Protos-observable result is the same as under the
> specified scalar/logical semantics.

This includes preserving every observable property that may matter to Protos,
including:

- result values and standard Number/Float semantics;
- object identity and aliasing;
- ordinary left-to-right evaluation requirements;
- message lookup and dispatch selection;
- the number and observable order of user-defined message/Closure invocations;
- error selection and failure precedence;
- slot/index mutations and their observable ordering;
- explicit suspension/cancellation boundaries;
- dynamic-handler behavior;
- P isolation, publication, reservation, and fairness guarantees.

A vectorizer may therefore batch or widen operations only when doing so cannot
change those observations. If legality is uncertain, the implementation must use
a semantics-preserving scalar or otherwise equivalent execution strategy.

In particular, a reduction or reassociation whose operator is observably
non-associative may not change the logical combination order merely because a
SIMD instruction or wider reduction tree is available. A standard/library
parallel operation that defines a canonical logical reduction structure remains
bound to that structure. A future API may explicitly define relaxed,
approximate, target-sensitive, or otherwise different numeric semantics, but
ordinary Core execution does not acquire them implicitly from vectorization.

SIMD width, instruction selection, masking strategy, scalar fallback, vector
cost model, alignment handling, and whether vectorization occurs at all are
implementation details. Programs must not be able to infer a portable semantic
fact from the presence or absence of SIMD hardware.

This rule applies equally inside ordinary Actor/cooperative execution and inside
isolated P work. P grants permission for simultaneous isolated execution; SIMD
is merely one possible physical implementation of computation within such work.
Neither mechanism changes the semantics of the other.

### 71.13 Standard `Closure.parallel(...)`

The Core v0.1 public submission surface is:

```text
closure.parallel(arguments...)
    -> Future
```

`parallel` is standard Closure behavior reached through ordinary message lookup.
No new keyword, grammar form, callable category, or public parallel-task identity
is introduced.

Ordinary invocation evaluates the receiver and explicit arguments left-to-right
before the standard behavior runs. The standard behavior then checks ordinary
Closure argument-count validity and forms one complete P-boundary snapshot.

An argument-count/binding error takes precedence over P graph validation once
the already-evaluated arguments have entered the standard behavior. If the
combined input graph cannot cross P, the invocation synchronously signals the
standard `NonParallelValue` error, which delegates directly to `Error`. No result
Future is returned and no partial P computation becomes eligible.

Once input formation succeeds, the operation creates and returns a normal Future
owned under the ordinary structured-concurrency rules of the creating
activation. The successful return is the normative input snapshot point.

### 71.14 P value graph and Closure projection

P input formation considers the bootstrap Closure and all explicit arguments as
one logical graph so repeated references and cycles are preserved across the
whole submission.

Ordinary mutable values cross as isolated logical value copies subject to their
normal semantic-family rules. Physically immutable standard-prelude state may be
shared only where the standard sharing rule permits it.

The following are not P-transferable in Core v0.1:

- `ActorRef` and `GroupRef`;
- Future/task identity and `ExecutionContext`;
- open I/O/native/resource capabilities;
- Process, Node, Cluster, placement, lifecycle, or administrative authority;
- other host/native values without an explicit P-transfer contract.

A Closure encountered as the bootstrap receiver or inside the explicit P input
graph is **projectable**, not capture-transferable. The destination is a fresh
ordinary Closure with the same executable body and parameter form and with its
ordinary user-visible local slot state copied through the same P graph. Its
caller capture metadata is replaced by the new P root described in §71.2.

The source Closure is never detached, invalidated, or mutated by projection.
Different source Closures remain different destination Closures; repeated
references to one source Closure map to one projected destination Closure within
that submission.

A normally completed result crosses back through the same P value rules. A
non-transferable normal result fails the result Future with caller-domain
`NonParallelValue` and publishes no partial result.

A signaled P Error crosses as the Future failure value when its logical graph is
P-transferable. If the Error graph itself cannot cross, the Future instead fails
with caller-domain `NonParallelValue`. This fallback does not expose the
untransferable P-local Error graph.

### 71.15 P root execution environment

Each isolated P computation has a fresh root execution environment whose
lifetime is the computation's lifetime.

The root:

- has a fresh execution context;
- sees the standard frozen prelude through the ordinary lexical chain;
- has `this === null` before any P-local method binding establishes another
  receiver;
- has no caller `methodHome`;
- has no caller dynamic handlers;
- owns a fresh return home local to P.

Projected Closures capture this root rather than the source caller environment.
A `^` that targets the projected Closure's captured home therefore unwinds only
within P and may complete that P computation; it cannot return into the caller.

`super` cannot use a method home discarded at the P boundary. Method binding and
`super` relationships created entirely from P-local objects/behavior continue to
follow ordinary rules.

### 71.16 Cooperative tasks inside P

C composes inside P.

An ordinary `closure.future()` created while executing in one P domain creates a
cooperative task in that same P domain. Such work may interleave only at explicit
suspension points and never executes Protos code simultaneously against that
domain's mutable state.

A nested `closure.parallel(...)` creates another isolated P domain and may run
simultaneously.

P-local cooperative tasks and nested P children remain bounded by the P domain's
lifetime. Detachment removes an applicable structured activation ownership edge
but does not create persistent identity, mailbox semantics, Actor identity, or a
right to survive termination of the enclosing P domain.

### 71.17 Physical sharing is not a public capability

Core v0.1 standardizes the isolation result, not the physical sharing mechanism.

There is no standard API that asks whether a value is physically shareable, pins
a value into shared storage, requests zero-copy transfer, exposes copy-on-write
state, reveals whether two isolation domains use one backing allocation, or
requires a particular storage-transfer strategy.

Failure to obtain a particular physical optimization is therefore not a semantic
failure condition. If a logical P snapshot is otherwise valid, an implementation
must realize it through some semantics-preserving representation available to
that implementation.

This leaves implementations free to exploit immutable representation aggressively
without adding a second user-visible immutability/ownership system to Protos.

### 71.18 Standard exclusive byte regions

Inside P, standard `Bytes` provides `parallelRange(start, length, worker,
arguments...) -> Future`. `ByteRegion` values created by this mechanism provide
the same operation recursively.

The operation is valid only in P. Outside P it signals
`ParallelRegionOutsideP`. `start` and `length` are semantic Integers and define
the half-open interval `[start, start + length)`, with non-negative bounds inside
the receiver. `worker` must be a Closure and executes as a projected child-P
Closure whose first argument is the fixed-size local `ByteRegion`.


After ordinary receiver/argument evaluation has completed left-to-right, the
standard behavior performs synchronous validation in exactly this order:

1. require that the current execution domain is P, otherwise signal
   `ParallelRegionOutsideP`;
2. validate `start` as a semantic Integer and require `start >= 0`;
3. validate `length` as a semantic Integer and require `length >= 0`;
4. require `start + length <= receiver.size`;
5. require `worker` to be a Closure;
6. reject overlap with an already-active non-empty reservation on the same
   logical receiver using `ParallelRegionOverlap`;
7. validate the projected worker and remaining explicit argument graph for the
   child P boundary.

The first failing check in this sequence determines the synchronous failure.
No reservation or Future exists before all seven checks succeed. Effects already
performed while evaluating the receiver or arguments are not rolled back.

### 71.19 Reservation and overlap semantics

A successful non-empty submission creates one exclusive reservation until its
Future becomes terminal. Two non-empty intervals overlap exactly when each begins
before the other ends. Overlap signals `ParallelRegionOverlap` synchronously and
creates no Future/reservation. Zero-length intervals reserve nothing.

While reserved, parent access inside the interval signals
`ParallelRegionInUse`; access wholly outside active intervals remains ordinary;
`size` remains readable; operations that can change length or shift indexed
positions signal `ParallelRegionInUse` while any reservation exists. These rules
fail rather than block or suspend.

A `ByteRegion` exposes only local zero-based byte indexing, fixed `size`, and
recursive `parallelRange`. It exposes no parent identity, absolute offset,
physical backing, address, or sibling authority.

### 71.20 Commit, failure, and recursive subdivision

The child mutates isolated region state. Parent mutation occurs only at successful
publication, after both normal child completion and successful P-boundary result
transfer.

Successful publication is one indivisible semantic commitment with respect to
cancellation and Future terminalization. At that commitment, while the Future is
still pending, the operation atomically chooses the successful outcome: exactly
the region's fixed bytes replace the reserved parent interval, the reservation is
released, and the Future resolves with the already-transferred child result.
Cancellation cannot win after the parent bytes have become visible, and parent
bytes cannot become visible if cancellation has already won the Future's terminal
race.

If cancellation wins before that successful-publication commitment, or if the
child fails or its result cannot cross the P boundary, the reservation is
released without publishing region mutation. A cancellation request that arrives
after successful publication has committed is a terminal-Future no-op under the
ordinary `Future.cancel()` rule.

This atomicity is only the reserved-byte publication boundary, not a transaction
over arbitrary P state. Disjoint commits have no added total order. Recursive
`ByteRegion.parallelRange` subdivides authority with the same rules.

`ByteRegion` is scoped P-local authority, not an ordinary transferable/serializable
value. It moves only through the dedicated region operation that defines the
authority transfer.

Core v0.1 deliberately excludes generic writable Array/object partitioning,
as closed by §71.5A, because disjoint indexes do not prove disjoint mutable
reachable graphs.

# Failure-transfer integration migrated from legacy §5

### P failure outcomes use ordinary value transfer

A P computation's unhandled Error is not a privileged cross-domain exception
channel. It is the failure value of the P computation and crosses toward the
caller only through the ordinary P value-transfer boundary.

A transferable Error graph is reconstructed/projected as the caller-side Error
value according to the same isolation-preserving rules as any other transferable
P result. P-local identity-bearing objects therefore do not retain `===`
identity across the boundary.

If the Error graph cannot cross, P exposes `NonParallelValue` as the
caller-visible failure under the existing result-transfer rule. Implementations
must not fall back to sharing the P-local Error object, serializing hidden
continuation state, or creating an implicit remote-error proxy.

Handler frames and all other dynamic control state remain local to the P
execution domain. A caller-side Future observation signals only the transferred
Error value under the caller's then-current handler context.

The producer task or isolated computation has already reached its failure
outcome. A later `value()` observation creates only a consumer-side Error
signaling event. Handling that event cannot resume the producer task, restart a
producer Actor turn, re-enter a terminated P child computation, or reconstruct
producer dynamic handlers or return homes.

This rule applies regardless of whether producer and consumer execute in the
same Actor, different tasks of one Actor, different Actors, or across a P
boundary. Future failure transport is never an implicit continuation-transfer
mechanism.
