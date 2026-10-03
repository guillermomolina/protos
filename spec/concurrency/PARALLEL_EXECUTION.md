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

The semantic rules in this section are normative. Core v0.1 has no public value,
prototype, capability, namespace, prelude binding, reserved word, or syntax form
named `P`. `P` is specification shorthand for the isolated parallel execution
domain entered through the standard operations below; implementation mechanisms
remain unobservable.

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

### Ordinary ownership of `parallel`

The standard `parallel` selector is the ordinary local Closure-valued
`Object.parallel` slot specified by `../semantics/CALLABLES.md`. Ordinary lookup,
reflection, extraction, shadowing/override behavior, and the semantic-Closure
receiver-domain check are owned there. Merely inheriting or finding that selector
does not confer Closure-family membership. If ordinary lookup selects the
standard behavior for a non-Closure receiver, invocation fails under that
receiver-domain contract before projection, transfer, isolated execution, or
result-Future creation begins.

This document owns the isolated-parallel semantics after successful standard
receiver-domain validation. Worker pools, runtime intrinsics, cached dispatch,
and other host mechanisms remain implementation machinery and must not replace
the observable ordinary `Object.parallel` slot.

In particular, ordinary `closure.future()` semantics are not changed by
this design. Actor-local Future work remains serialized with other
Actor-local Protos execution according to the normal Actor turn model.

The runtime may use internal concepts such as parallel jobs, work items,
worker pools, region capabilities, or work-stealing queues. Such concepts
are implementation machinery unless separately standardized later.

### 71.1 Public API closure

The complete Core v0.1 entry surface is ordinary dispatch on existing values:

```text
Closure.parallel(arguments...)
```

No `P(...)`, `P.spawn`, region constructor, worker/join/executor handle, current-P
accessor, or implicit parallel operator exists. Nested P is another explicit call
to this operation. Process and open I/O/native/resource capabilities have
no P-transfer contract and fail under `NonParallelValue`; P acquires no ambient
Actor, Process, I/O, or scheduler authority.

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
cancellation, waiting, failure propagation, and Actor/P lifetime
rules are defined semantically through task-scoped structured execution,
Futures, and execution domains. An implementation may track richer internal parent/child task state as
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

Parallel algorithms may wish to modify disjoint parts of a large value
efficiently. Protos does not open arbitrary shared mutable memory for that
purpose.

The governing rule is:

> Physical storage may be shared, but two parallel computations must not
> simultaneously hold mutable authority over the same logical state.

Core v0.1 standardizes no writable partitioning facility. Standard `Bytes` has
no `parallelRange` operation, Core has no `ByteRegion` value family, and ordinary
`Bytes` carries no parallel reservation state: no `Bytes` operation fails,
blocks, suspends, or otherwise changes behavior because of P work. A P
computation that transforms byte-indexed or other mutable state does so on its
own isolated snapshot under §71.3 and returns a fresh result through the
ordinary P value-transfer boundary. Physical sharing remains the
semantically invisible optimization of §71.4.

Disjoint physical ranges, Array indexes, or storage addresses are not by
themselves sufficient to establish disjoint mutable authority. Two Array
indexes that contain references to the same mutable object do not acquire
independent authority over that referenced object merely because the indexes
themselves do not overlap.

### 71.5A No generic writable graph partitioning in Core

Core v0.1 does not standardize a writable-partition facility for `Bytes`,
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

- `Bytes.parallelRange(...)`, `Array.parallelRange(...)`, or other
  writable-region operation;
- `ByteRegion` or other writable-region value family;
- generic `Object.partition(...)` or graph-region capability;
- runtime alias-analysis API that grants writable P authority;
- borrow/ownership annotation system;
- user-visible uniqueness, move-only, affine, or linear reference mode;
- dynamic "prove disjoint" operation whose success depends on
  implementation-selected heap analysis.

This does not prohibit parallel algorithms over Arrays or objects. Such
algorithms may use ordinary P snapshot/value semantics, produce fresh results,
use immutable/read-only inputs, or internally exploit semantics-preserving
representation optimizations. What Core does not provide is simultaneous
writable authority over arbitrary logical object graphs merely because a
container representation can be physically partitioned.

A future facility may add writable partitioning only if it introduces a
portable semantic proof of disjoint mutable authority. That proof must be
language/runtime-defined rather than dependent on one implementation's escape,
alias, GC, pointer, or storage analysis. If broader ownership/capability
semantics are ever introduced, they must justify their global language cost
independently rather than being smuggled in as an Array optimization.

### 71.6 Library-Level Parallel Patterns

High-level parallel algorithms are library facilities built on the minimal
runtime guarantees rather than separate language primitives, Core selectors, or
separate fundamental task kinds.

Core v0.1 standardizes no parallel collection algorithm. In particular, the
standard Array prototype has no `parallelMap`, `parallelFilter`,
`parallelFindIndex`, `parallelReduce`, `parallelSort`, or other parallel
algorithm selector, and no compatibility alias for such a selector exists.
Invoking one through ordinary lookup on a standard Array therefore follows the
ordinary missing-slot rules.

The Standard Library module `std:collections/Array` provides parallel Array
algorithms as ordinary module functions. They are Standard Library policy, not
Core semantics: they are composed only from the public substrate defined by this
document and `FUTURES_AND_TASKS.md` (`Closure.parallel(...)`, Future observation,
`Future.all(...)`, and structured ownership/cancellation) and receive no
privileged runtime helper, batching/chunking authority, scheduler/worker/task
object, or writable Array/object partition authority. Each isolated callback
invocation they perform is an ordinary `Closure.parallel(...)` submission and is
governed by §71.2–§71.5, §71.8, and §71.13–§71.15. Their operation-level
contracts (argument shape, result ordering, reduction structure, sort
stability, predicate/comparator validation, failure selection, and
cancellation) are owned by that Standard Library module's documentation.

The standard `InvalidPredicateResult`, `InvalidComparatorResult`, and
`InvalidComparatorOrder` Error prototypes (`../semantics/ERRORS.md`) remain
available to such library predicate/comparator contracts. Core attaches no
parallel-algorithm trigger to them.

A standard or third-party library may choose chunking, reduction trees,
partition strategy, batching, or algorithm-specific policy only where the
operation's contract leaves that choice unobservable or defines it explicitly,
and the runtime continues to enforce the underlying isolation and scheduling
guarantees.

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

### 71.6E No standard `Array.parallelEach(...)`

Neither Core v0.1 nor the standard library standardizes `Array.parallelEach(...)`
or another parallel iteration operation whose element-worker results are
discarded.

This is an API boundary derived from the existing P effect model rather than a
restriction on physical execution.

A Core P computation is an isolated CPU-computation domain. It does not inherit
the caller Actor's mutable state, sender identity, mailbox, ambient I/O
capabilities, Process/Node/Cluster authority, or another standard external-effect
channel merely because work is eligible to run simultaneously.

Consequently, a generic parallel iteration operation would have no additional
standard publication channel beyond the per-invocation P result/failure boundary
already available through `Closure.parallel(...)` and through result-producing
library algorithms such as the `std:collections/Array` parallel map. Discarding
those normal results would remove information without adding a new semantic
capability. A caller may ignore a successfully resolved result when its values
are not needed.

This decision also prevents an iteration-shaped API from implying that P workers
may rely on hidden shared mutation, Actor messaging, I/O, native global state, or
other externally observable side effects. Those capabilities remain governed by
their existing P-transfer/effect rules and are not made valid by choosing an
`each`-like spelling.

A future P-safe effect capability or a future API with independently useful
completion/failure/resource semantics may justify a parallel iteration facility.
If introduced, that facility must define its effect authority, result/failure
meaning, cancellation, ownership, ordering, and P transfer semantics explicitly
rather than inheriting them from an otherwise result-discarding loop.

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
- P isolation, publication, and fairness guarantees.

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
owned under the ordinary structured-concurrency rules of the current
task-scoped structured execution context. The successful return is the normative input snapshot point.

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
lifetime under the structured-ownership rules in `FUTURES_AND_TASKS.md` §24.
Such work has no persistent identity, mailbox semantics, Actor identity, or
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


## D047 networking P boundary

D047 / specification revision `0.1.388` adds no P-transfer contract for
live networking authority or resources. Creating isolated P work does not inherit
a `Network`, `TcpConnection`, or `TcpListener`, and those live capabilities are
not valid P snapshot results/inputs merely because a backend could technically
proxy or share a host socket.

`IpAddress` and `IpEndpoint` remain authority-free data and may participate in P
value copying once their standard construction contract is implemented. Physical
network transports used by distributed P/runtime machinery remain implementation
details and do not become application Network authority.
