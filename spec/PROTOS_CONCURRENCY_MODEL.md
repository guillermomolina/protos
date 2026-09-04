# Protos Concurrency Model v0.1

Language version: 0.1
Document revision: 326
Status: Draft
Last updated: 2026-09-04
# Protos Multithreading Design Ledger


### Normative status of this mixed document

This document intentionally combines normative concurrency semantics with
unresolved design work.

A section whose status is exactly `CLOSED` or `CLOSED --- REVISED` is normative
for the Actor/Future/concurrency domain. A section whose status contains
`OPEN`, `PENDING`, `DIRECTION`, `DETAILS OPEN`, `API OPEN`, or another
design-only qualifier is non-normative unless and until that status is changed
explicitly.

Normative status is section-local. When a CLOSED section explicitly says that
a named API, syntax, policy, mechanism, or implementation detail remains open,
that stated open subtopic remains non-normative; the closed semantic rules in
the section remain normative.

No implementation may use unresolved material in this ledger to choose
observable behavior not otherwise fixed by the normative Protos
specifications.

## 1. General Principle

**CLOSED**

Protos aims to hide concurrency complexity in a way analogous to how
garbage collection hides manual memory management.

Priorities:

-   Ease of use
-   Safety by default
-   Performance
-   Pay for what you use

A simple Protos program must not pay the cost of distributed runtime
infrastructure unless it actually uses it.

A small command-line program such as `ls` should start quickly and
should not require cluster membership, discovery, network listeners,
external configuration, or heavyweight runtime services.

Guiding rule:

> If normal Protos code requires the programmer to reason about locks,
> atomics, memory barriers, ownership graphs, or memory ordering, the
> concurrency model has probably failed.

The runtime should absorb as much complexity as possible without
sacrificing ordinary execution performance.

## 2. Fundamental Model

**CLOSED**

The fundamental programming model remains:

-   Objects
-   Slots
-   Closures
-   Delegation
-   Message dispatch
-   `this`

Actors do not introduce a second object model or a second dispatch
system.

An Actor organizes ordinary Protos objects into an isolated domain
containing:

-   A private object graph
-   Mutable state
-   A current behavior object
-   A mailbox
-   Futures/tasks
-   Lifecycle state

Principle:

> Objects, slots, closures, and dispatch are the molecule. Actors
> organize those molecules into isolated domains of state, execution,
> lifecycle, and parallelism.

The Actor itself is not simply another ordinary Protos object.

Its current behavior is an ordinary Protos object.

## 3. Future Versus Actor

**CLOSED --- REVISED**

Future and Actor solve different scaling problems.

> A Future represents an eventual result and scales with the amount of
> concurrent work.

> An Actor provides a persistent isolated domain of mutable state,
> identity, lifecycle, messaging, and parallel execution relative to
> other Actors.

Ordinary Future/task execution created within an Actor remains
Actor-local and cooperative. Such tasks behave conceptually like green
threads or green tasks and may exist in very large numbers.

Only one segment of ordinary Actor-local Protos code executes at a time.
Therefore ordinary Futures inside the same Actor may interleave, but they
do not execute Actor-local Protos code simultaneously.

Protos additionally permits explicit isolated parallel computation as
defined in Parallel Execution. Such work executes outside the caller's
mutable Actor domain and may run simultaneously on other CPU carriers.
Its eventual result may be represented by an ordinary Future without
making the isolated parallel execution unit a new fundamental public
identity-bearing object.

Different Actors may also execute simultaneously on different CPU cores.

An Actor is therefore a logical serial domain for its own mutable Protos
state, not necessarily one operating-system thread and not necessarily
the smallest unit capable of consuming a CPU core. Mapping Actors,
Actor-local tasks, and isolated parallel work onto operating-system
threads or other carrier mechanisms is a runtime implementation
decision.

## 4. Actor Isolation

**CLOSED**

There is no shared mutable Protos memory between Actors.

Ordinary mutable Protos references never cross an Actor boundary.

Therefore the normal Actor programming model does not require:

-   Mutexes
-   Locks
-   Volatile variables
-   Atomics
-   Memory barriers

The runtime may internally use shared memory, concurrent queues,
atomics, locks, copy-on-write, or other mechanisms as long as those
mechanisms are not observable through Protos semantics.

The same principle applies to isolated parallel computation: physical
storage may be shared internally when semantic isolation can still be
preserved, but arbitrary simultaneously shared mutable Protos identity
is not introduced merely to obtain CPU parallelism.

## 5. Actor Turns and Reentrancy

**CLOSED**

An Actor executes at most one segment or turn of Protos code at a time.

A message handler or task retains exclusive execution within the Actor
until it completes or reaches an explicit suspension point.

For example:

    data: socket.read().value()

If `value()` must wait, the current task is suspended and the Actor may
execute other runnable work.

When the suspended task becomes runnable again, its continuation
executes in a later turn.

Consequently, Actor-local mutable state may have changed across an
explicit suspension point.

Between suspension points, Actor-local state is serialized and race-free
with respect to other work in the same Actor.

The model is therefore sequential but reentrant at explicit suspension
points.

Explicit isolated parallel computation does not weaken this rule. Code
executing in such a computation does not execute as another simultaneous
turn against the Actor's mutable object graph; it crosses a separate
isolation boundary and may only interact with the Actor through the
value/result semantics defined for parallel execution.

### Dynamic error handlers and task suspension

Dynamic error-handler frames are local to the task whose execution installed
them. They are not Actor-global mutable state.

If a task suspends at an explicit suspension point while a protected handler
scope remains active, that handler scope is retained as part of the suspended
task's continuation. When the same task resumes, the scope is active again until
normal completion or unwind leaves it. Other runnable work executed by the Actor
during the suspension does not see or inherit the suspended task's handlers.

Creating a distinct asynchronous Future/task does not inherit or copy the
creator's active dynamic handler frames. This includes a Future continuation
created by `then()`: the continuation is a distinct task and handles only errors
under handlers that are dynamically installed in that continuation's own
execution. If an asynchronous task fails without handling its error, the error is
recorded by its Future and is re-signaled only when a consumer observes that
failure under the ordinary Future rules.

This rule prevents handler installation from becoming hidden Actor-wide state,
prevents unrelated task failures from being intercepted by another task's
temporary scope, and avoids retaining a creator's dynamic stack for the lifetime
of asynchronously spawned work.

### Failed Future observation does not transfer producer control state

Future failure transport carries an Error outcome, not the producer's dynamic
control stack.

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

### Map comparison scopes and Actor-local suspension

The language-level Map comparison restriction composes with Actor reentrancy at
explicit suspension points.

A task that suspends inside a standard normal-`Map` key equality comparison
releases the Actor execution segment in the ordinary way, so other runnable
Actor-local work may execute. It does **not** release the Map-specific
keyed-entry mutation restriction associated with that in-progress comparison.

Consequently, during the suspension:

```text
other task reads same Map
    -> permitted

other task mutates unrelated Map
    -> permitted

other task mutates same Map keyed-entry state
    -> Error before mutation
```

This is intentionally narrower than Actor-wide exclusion and intentionally
different from task-local dynamic error handlers. The comparison restriction
protects one mutable Map's in-progress keyed search and is therefore visible to
other tasks that attempt to mutate that same Map. Error-handler frames instead
control which task catches an error and remain private to their task.

No task waits for a comparison scope as part of this rule. The Actor remains
free to schedule unrelated work, and conflicting Map mutation fails rather than
introducing hidden blocking or lock acquisition.

If the comparison's suspended task is later cancelled, ordinary cancellation
resumption and unwind release the comparison scope. If it remains suspended
indefinitely, the same Map may remain mutation-restricted indefinitely; this
does not prevent unrelated Actor-local work or operations on unrelated Maps.

### Future `then()` continuations

The normative Future/Task contract formerly contained here has moved to `concurrency/FUTURES_AND_TASKS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 6. I/O

**CLOSED --- REVISED**

The normative I/O capability, cancellation, lifecycle, text, filesystem, and Process-standard-I/O semantics are defined in `io/IO_CORE.md`.

Normal Protos I/O should be non-blocking relative to the Actor and
should return a Future or another awaitable operation.

Suspension remains explicit through operations such as:

    data: socket.read().value()

If the result is already available, execution may continue immediately.

If the result is pending, the current task suspends and the Actor may
execute other work.

Ordinary I/O must not transparently introduce hidden suspension points.

Principle:

> Looking at Protos code, the programmer should be able to identify
> where Actor reentrancy may occur.

Internally, an Actor may conceptually receive runnable work from a
unified runtime event source containing:

-   Mailbox messages
-   I/O completions
-   Timers
-   Future resolutions

An I/O completion is not normally exposed as an ordinary message to the
Actor's current behavior. Instead, it makes the corresponding suspended
task runnable.

The implementation may use mechanisms such as epoll, kqueue, io_uring,
callbacks, fibers, virtual threads, or other facilities without changing
language semantics.

## 7. Ordering and Fairness

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 8. Actor Creation

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 9. Actor Initialization and Readiness

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 10. Implicit Event Loop

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 11. Current Behavior

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 11A. Behavior Requirement at the READY Cutover

The normative contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 12. Actor Message Dispatch

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 13. send()

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 14. request()

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 15. send() and request() Share Delivery and Dispatch

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 16. Pass-by-Value Between Actors

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 17. Message Snapshot Time

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 18. Message Transfer Optimizations

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 19. Buffers

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 20. End-to-End Backpressure

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 20A. Same-Host and Shared-Memory Transport Are Semantically Invisible

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 20B. Transport Selection and Switching Are Runtime Policy

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 20C. Physical Locality Discovery Is Runtime Machinery

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 21. Mailbox Bounds

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 22. Timeouts and Deadlines

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 23. Cancellation

The normative Future/Task contract formerly contained here has moved to `concurrency/FUTURES_AND_TASKS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 24. Structured Concurrency

The normative Future/Task contract formerly contained here has moved to `concurrency/FUTURES_AND_TASKS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 24A. Actor Termination Request and Graceful Stop

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 24B. Actor Lifetime Is Not Reachability Garbage Collection

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 24C. Unhandled Errors Are Fatal to the Actor Incarnation

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 24D. Actor-Local CPU Work Is Cooperatively Non-Preemptive

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 24E. Waiting for Multiple Futures with `Future.all(...)`

The normative Future/Task contract formerly contained here has moved to `concurrency/FUTURES_AND_TASKS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 24F. No Generic Future Race/Select in Core

The normative Future/Task contract formerly contained here has moved to `concurrency/FUTURES_AND_TASKS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 24G. Future Ownership and Actor Lifecycle Matrix

The normative Future/Task contract formerly contained here has moved to `concurrency/FUTURES_AND_TASKS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 24H. Non-Local Return Never Crosses an Actor Boundary

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 24I. Dynamic Error Handlers Never Cross Actor Boundaries

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 24J. Blocking Foreign Calls and Physical Offload

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 24K. Foreign Mutable State Does Not Bypass Actor Isolation

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 24L. Non-Transferable Resources Never Auto-Proxy

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 25. Parent Actor Versus Failure Authority

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 26. Supervision and Failure Authority

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 26A. Core Failure-Authority Policy and API Boundary

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 27. Actor Identity Is Incarnation Identity

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 28. Messages Across Actor Failure

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 29. Monitoring Versus Supervision

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 29A. Actor Lifecycle Monitoring API

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 30. Runtime Health and Watchdog

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 31. Runtime Advisor

**CLOSED AS DIRECTION**

The runtime may provide an advisor capable of detecting problematic
patterns such as:

-   Long-running Actor turns
-   Mailbox saturation
-   Stuck Futures
-   Resource saturation
-   CPU-heavy work inside an I/O-oriented Actor
-   Excessive large-value transfers
-   Poor Actor placement
-   Communication hotspots

When an optimization is semantically invisible, the runtime should
prefer applying it automatically.

If repeated transfers or placement remain architecturally expensive, the
advisor may recommend changing Actor boundaries or placement.

## 32. Root Actor

**CLOSED --- REVISED**

Every Protos Process begins with a RootActor. Every Protos execution has a Process execution domain even when the RootActor is the only Actor ever created; the Process itself need not imply heavyweight Actor, Node, Cluster, routing, or distributed-runtime infrastructure.

A trivial program may conceptually consist only of:

    Process
        |
        v
    RootActor

The RootActor owns the initial Actor-local program state, including ordinary
objects, module state, configuration, and Futures. The Process is the
custodian of Process-local host authority such as arguments, environment,
and standard I/O when those facilities are provisioned. Appropriate
Process capabilities are made available to the RootActor at bootstrap;
they are not thereby ordinary mutable state owned by the RootActor.

The RootActor is an ordinary Protos Actor, so its initial program is not
outside the module model merely because it started the Process. When the
initial program corresponds to an importable canonical module, it is
handled like the initial module of any other Actor and follows the same
Actor-local module-cache identity rules.

Within the RootActor, ordinary state access uses normal Protos semantics
and does not incur Actor message-passing overhead.

If the RootActor suffers a fatal unhandled failure in this minimal
configuration, the Process terminates.

No dedicated Supervisor Actor, Group controller, distributed membership
service, or other unnecessary runtime machinery is required.

Creating additional Actors explicitly introduces new isolation and
parallelism boundaries.

If a program never creates another Actor, the runtime or JIT may optimize
away unnecessary Actor infrastructure.

RootActor is strictly Process-scoped and ephemeral with its Process.
Protos does not introduce an intrinsic distributed ApplicationRoot Actor.
Distributed application or service continuity is expressed through
Groups, discovery, Cluster/runtime control state, and explicit durable
state as required.

A future application identity may exist for deployment, configuration,
observability, or ownership without becoming an Actor or a mandatory
execution-hierarchy level.

## 33. Global State

**CLOSED**

Protos does not introduce a special shared mutable global-state
exception.

State that is conceptually global to a simple program can initially
remain ordinary mutable state owned by the RootActor.

If additional Actors require access to that state, they communicate with
the RootActor through Actor communication.

Principle:

> Global state starts as RootActor-local state and becomes distributed
> only when the application introduces parallelism or isolation
> boundaries.

## 34. Actor Module State

**CLOSED --- REVISED**

Each Actor owns its module state: an Actor-local module cache and the
module contexts (module instances) belonging to that Actor.

Actors do not inherit mutable module contexts from their creator.

If two Actors import the same module, mutable module-level state is
logically separate in each Actor.

The runtime may physically share immutable implementation artifacts such
as compiled code, immutable metadata, frozen core objects, or shared
prelude implementation, provided that the sharing is not observable as
shared mutable Protos state.

Per canonical module identity, an Actor has at most one active cached
module instance at a time; the Actor-local module cache is authoritative
for the currently active module instance. Cache membership and ordinary
object reachability are distinct. This is not a lifetime-wide "module
singleton" guarantee: an Actor is not limited to a single historical
object per canonical module identity. A module instance whose
initialization failed and whose cache entry was removed may remain
reachable through ordinary escaped references while a later fresh
instance is the Actor's active cached module instance for the same
canonical identity. Both objects belong to the same Actor, so their
coexistence does not violate Actor isolation.

The full module lifecycle rules (module instance equals its
`moduleContext`, Actor-local cache-before-execute, cache states,
cyclic-import and failure handling, and the initial module of an Actor)
are defined in the canonical module-lifecycle sections of
`PROTOS_LANGUAGE_SPEC.md` and `runtime/ABSTRACT_RUNTIME.md`. This
section states only the Actor isolation and ownership consequences that
the concurrency model depends on.

## 34A. Module Implementation Sharing Is Semantically Invisible

**CLOSED**

Core v0.1 fully separates **module semantic state** from **module implementation
artifacts**.

For a given canonical module identity, each Actor's active module instance,
`moduleContext`, mutable slots, initialization state, and module-cache membership
remain Actor-local exactly as defined by the language/runtime module lifecycle.
No process-global mutable module instance exists.

An implementation may physically share artifacts that do not constitute mutable
Protos module state, including:

- parsed syntax or immutable syntax trees;
- bytecode or other executable intermediate representation;
- machine code/JIT code;
- immutable metadata;
- immutable constant data whose sharing is already semantically permitted;
- read-only loader/compiler/runtime bookkeeping whose identity is not exposed as
  a Protos value.

Such sharing is an implementation optimization only. Programs must not be able
to distinguish, through portable Core observations, whether two Actors execute
one physically shared code object or two physically duplicated ones.

In particular, implementation-artifact sharing must not cause Actors importing
the same canonical module to share:

- mutable module slots;
- lexical execution contexts;
- closure captures;
- mutable object identity created by module initialization;
- initialization progress/failure state;
- module-cache entries;
- dynamic handlers, return homes, Futures/tasks, resources, or Actor-local
  authority.

Likewise, compiling, caching, deduplicating, interning, unloading, recompiling,
or JIT-specializing implementation artifacts must not change the normative
module-instance identity or lifecycle observed by Protos code.

An implementation may choose per-Process, per-Node, or otherwise broader
physical caches for immutable artifacts, or choose no sharing at all. Cache
placement, eviction, code deduplication, compilation tiers, and artifact identity
are not Core semantic surfaces.

If a future facility exposes code identity, hot-update/version selection,
reflection over compiled artifacts, or implementation-level module handles, that
facility must define its own observable contract. It must not retroactively make
ordinary module implementation sharing visible.

This closes the former open ledger item `Module implementation sharing`; the
remaining module semantics are already fixed by the canonical Language and
Runtime module-lifecycle rules.

## 35. Scope Roots

**DIRECTION CLOSED, DETAILS OPEN --- REVISED**

The runtime has one Process execution domain for every Protos execution
and may expose conceptual root capabilities corresponding to larger active
execution scopes:

    ClusterRoot
        |
        v
    NodeRoot
        |
        v
    Process
        |
        v
    RootActor

Only RootActor is an ordinary Protos Actor.

Process, NodeRoot, and ClusterRoot are runtime entities/capabilities rather
than ordinary Actors. Process exists even in the minimal standalone case;
NodeRoot and ClusterRoot may be absent when their scopes are not active.

Conceptual responsibilities include:

ClusterRoot:

-   Cluster membership
-   Discovery
-   Distributed placement coordination
-   Cluster health
-   Distributed authority facilities when required

NodeRoot:

-   Node resources
-   Local Processes
-   Node health
-   Local coordination

Process:

-   Process lifecycle
-   Actor management
-   Root failure authority
-   Runtime services
-   Custody of Process-local application-facing host authority, including
    arguments, environment, and standard I/O when provisioned

RootActor:

-   Application-level mutable state
-   Application code
-   Normal Protos execution

Process-local host facilities may be lazy and need not be materialized if
the application never requests them. Their I/O-facing semantics are defined
in `io/IO_CORE.md`.

A standalone Process does not need active distributed Cluster
infrastructure.

NodeRoot and ClusterRoot facilities should be lazy or absent when they
are not needed.

No intrinsic ApplicationRoot exists above Process RootActors. A higher
level application/service identity may be introduced later for
administrative purposes only if it provides semantics not already
covered by Groups, discovery, durability, and runtime coordination.

## 36. Actor Runtime Context References

**CLOSED AS DIRECTION --- REVISED**

An Actor may have access to a minimal set of implicit runtime references
or capabilities without inheriting mutable state.

These may conceptually include:

-   `selfActor`
-   `parentActor`
-   `rootActor`
-   Failure-authority or supervisor capability
-   Node root capability
-   Cluster root capability

The exact names and APIs remain open.

The Process capability is deliberately not part of this implicit-runtime-reference list. An Actor has Process authority only when that capability was explicitly provisioned to the Actor at creation or explicitly delegated later according to the applicable capability-transfer semantics. Provisioning a subordinate Process-local facility does not implicitly grant the whole Process capability.

These values do not provide direct shared-memory access to another
Actor's mutable object graph.

## 37. Process Boundary

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 37A. Process Failure Knowledge Boundary

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 38. Node

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 38A. Node Failure Knowledge Boundary

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 38B. Network-Partition Knowledge and Reporting Boundary

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 38C. Core Split-Brain Safety Boundary

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 39. Cluster

**DIRECTION CLOSED, DETAILS OPEN --- REVISED**

A Cluster is the logical distributed coordination domain across Protos
Nodes.

Cluster identity is independent of the identity and current count of its
Nodes.

Nodes may join, leave, fail, or be replaced while the logical Cluster
remains the same coordination domain. A Cluster may conceptually have
zero current Nodes.

Cluster functionality may include:

-   Membership
-   Discovery
-   Routing coordination
-   Placement coordination
-   Node health
-   Resource awareness
-   Failure detection
-   Group/controller coordination
-   Authority facilities when required
-   Rebalancing

Cluster identity is logically independent of current Node membership,
but persistence of that identity and its defining authoritative/control
state across complete loss of runtime membership is not intrinsic.

If the state defining the Cluster is explicitly preserved, later Nodes
may rejoin the same logical Cluster. If all defining runtime/control
state is lost, a later bootstrap creates a new Cluster identity rather
than magically reconstructing the old one.

Cluster functionality must remain lazy and must not impose distributed
runtime costs on ordinary standalone programs.

## 39A. Core Cluster Membership Protocol Boundary

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 40. ActorRef

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 41. Actor and Service Discovery

**DIRECTION CLOSED, DETAILS OPEN --- REVISED**

Concrete identity-bearing references and discovery names are separate
concepts.

Conceptually:

    ActorRef(A)       -> one concrete Actor A
    GroupRef(G)       -> one concrete Group G
    discovery name    -> may resolve to an ActorRef or GroupRef

Discovery names may be rebound over time.

Concrete ActorRef and GroupRef values are not rebound when a discovery
name later resolves to a replacement identity.

For example, if `"workers"` resolves to Group G1 and a caller obtains a
GroupRef for G1, that GroupRef remains G1 even if G1 later terminates and
the name `"workers"` is rebound to G2.

Discovery remains pluggable. Protos does not hard-code DNS, Consul,
Kubernetes, or another external service as the semantic discovery model.

Exact discovery APIs, naming rules, persistence, authentication, and
resolution mechanisms remain open.

## 42. Automatic Actor Placement and Capacity

**DIRECTION CLOSED, DETAILS OPEN --- REVISED**

Normal Actor creation expresses the intent to create an Actor, not a
physical placement command.

The programmer does not normally choose separate APIs for local Process,
Node, or Cluster placement.

The scheduler selects among currently available Protos execution capacity
according to the active runtime domain, feasibility, resource state,
locality, affinity, availability policy, and placement policy.

`spawn()` itself does not create Processes, Nodes, Clusters, Pods, VMs,
or machines.

Scheduling and infrastructure provisioning are separate responsibilities.

The scheduler uses capacity already incorporated into Protos through
normal bootstrap/discovery/membership mechanisms.

If no suitable capacity exists, admission may remain pending and may
produce semantic capacity-demand signals. An external or explicitly
integrated Infrastructure Controller may react by provisioning raw
execution capacity, but that capacity becomes usable by Protos only after
normal runtime bootstrap and membership.

External infrastructure does not directly create Actors, Group members,
Protos Nodes, or Cluster membership merely by creating physical
workloads.

Advanced hard placement constraints and soft placement hints may be
provided later, but physical location is not Actor identity.

The same Protos program should scale from a standalone Process to larger
runtime domains without changing the fundamental Actor programming
model.

## 43. Spawn Backpressure

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 44. Hierarchical Runtime Domains

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 45. Dynamic Actor Capacity and Placement

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 46. Adaptive Admission Control

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 47. Multi-Objective Placement and Rebalancing

**DIRECTION CLOSED, DETAILS OPEN --- REVISED**

Protos does not define one universal PACK or SPREAD placement strategy.

Placement balances multiple objectives such as:

-   Performance
-   Scalability
-   Availability/resilience
-   Resource efficiency
-   Locality
-   Stability

PACK and SPREAD are possible resulting strategies rather than universal
language modes.

Communication-heavy Actors may benefit from physical locality. CPU-heavy
Actors may benefit from spreading. High-availability requirements may
override a pure performance optimum.

Rebalancing is the process of improving runtime placement or distribution
over time. It does not imply live Actor migration.

Rebalancing may occur by:

-   Choosing better placement for newly created Actors
-   Choosing better placement for replacement Actors
-   Changing Group membership or cardinality over time
-   Avoiding new placement on capacity being retired
-   Retiring excess or poorly placed members
-   Optionally using live Actor migration if a future implementation can
    preserve the same live Actor incarnation and observable execution
    state

The runtime should avoid churn: rebalancing should occur only when the
expected benefit justifies its cost and disruption.

Exact placement scoring, hysteresis, affinity APIs, and migration
mechanics remain open.

## 48. Failure Domains

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 49. High-Availability Placement Requirements

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 50. Runtime Groups

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 51. Capacity Demand and Infrastructure Integration

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 52. Ephemeral Actor Liveness and Explicit Durability

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 53. Direct ActorRef Versus Stable Group Identity

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 54. Actors Are Replaced, Never Restarted

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 55. Supervision Versus Group Controllers

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 56. Failure Authority Is Pay-As-You-Grow

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 57. Outstanding Direct Interactions Die With the Actor

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 58. Rerouting Belongs to Group Addressing

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 59. Termination, Unreachability, and Unknown State

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 60. Unreachability Applies Backpressure

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 61. Processes Are Ephemeral Capacity

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 62. Nodes Are Ephemeral Runtime Members

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 63. Cluster Identity Is Independent of Node Identity

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 64. Loss of Node Membership Does Not Prove Physical Process Death

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 65. Local Execution May Continue Without Higher-Scope Authority

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 66. Authority

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 67. Authority Is Scoped

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 68. Controllers Are Ephemeral

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 69. Authority and Control State Are Mechanism-Independent

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 70. External Authority and Infrastructure Mechanisms Do Not Define Protos Semantics

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 71. Isolated Parallel Execution

The normative contract formerly contained here has moved to `concurrency/PARALLEL_EXECUTION.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 72. Standard Prelude Sharing

**CLOSED**

The standard prelude is shared between Actors and isolated P domains and is
frozen. Freezing is shallow, so freezing the prelude does not by itself make
arbitrary mutable objects referenced by its slots safe to share across isolation
domains.

Rule:

> Any Protos object physically shared across Actor/P isolation boundaries
> through the standard prelude must be semantically immutable for the duration
> of that sharing. Mutable Protos state reachable through standard facilities
> belongs to the isolation domain that uses it unless another normative rule
> explicitly provides a safe capability boundary.

Consequences:

-   The prelude itself may be physically shared, and its slots may refer
    to immutable Protos objects.
-   A prelude slot must not let two Actor/P isolation domains share mutable
    Protos state.
-   Mutable standard-library or runtime state — such as an Actor's module cache
    and module instances, or P-local mutable library state — belongs to the
    isolation domain that uses it.
-   The implementation may physically share immutable implementation
    artifacts such as parsed syntax, bytecode, machine code, immutable
    metadata, and immutable constant data where the sharing is
    semantically unobservable.

The existing rule that freeze is shallow is unchanged: no deep freeze is
introduced. Actor isolation is not weakened, and implementations are not
required to duplicate immutable data unnecessarily.

## 72A. No Core Application or Service Identity

**CLOSED**

Core v0.1 defines no intrinsic application identity, service identity,
`ApplicationRoot`, deployment identity, or mandatory execution-hierarchy level
above Process/RootActor, Node, Cluster, Group, and the other already-defined
runtime identities.

This is a deliberate semantic boundary rather than a missing hidden runtime
object.

An implementation, deployment system, orchestrator, observability platform, or
administrative tool may associate Protos runtime entities with an external
application/service identifier for purposes such as deployment, configuration,
observability, metrics, ownership, tenancy, rollout grouping, or operational
inventory.

Such an identifier is administrative metadata unless a future normative facility
explicitly promotes it into the Protos semantic universe.

In Core v0.1, administrative application/service identity therefore must not:

- become an implicit Actor sender identity;
- create a mailbox, mutable object graph, execution domain, or lifecycle domain;
- become a parent of Process RootActors;
- alter ActorRef or GroupRef identity;
- imply shared mutable state between Processes or Actors;
- change same-sender FIFO, routing, placement, failure, supervision, or
  cancellation semantics;
- grant Process, Node, Cluster, resource, or capability authority merely by
  association;
- keep Actors, Processes, Groups, Nodes, or Clusters alive;
- cause two otherwise distinct runtime identities to compare as the same Protos
  identity;
- impose distributed-runtime startup or coordination cost on programs that do
  not use a separately defined facility.

The same external application/service label may be associated with multiple
Processes, Nodes, Clusters, Groups, or deployments without creating a new Core
continuity guarantee among them. Conversely, one Protos Process or Cluster may be
classified differently by external tooling over time without changing its Core
identity.

Distributed service continuity remains expressed through the already-defined
semantic mechanisms such as Group identity, discovery, Cluster/runtime control
state, and explicit durable state. An administrative label is not a substitute
for those mechanisms.

A future standard facility may introduce a first-class application/service
identity only if a workload demonstrates a semantic boundary that cannot be
expressed cleanly by the existing universe. Such a facility would need to define
identity, lifetime, authority, topology relationships, transferability,
persistence, failure behavior, and observable operations explicitly.

This closes the former open ledger item `Optional administrative
application/service identity for deployment, configuration, observability, or
ownership` for Core v0.1. External administrative metadata remains permitted but
non-semantic.

## 72B. Service Discovery Implementation Is Not Core Semantics

**CLOSED**

Core v0.1 defines the observable meaning of discovery identities and rebinding
where those semantics are already specified, but it does not standardize one
service-discovery implementation.

A conforming runtime or distributed facility may implement discovery through
mechanisms such as:

- in-process or per-Process registries;
- Cluster control state;
- external registries or naming services;
- DNS-like systems;
- orchestrator/service-platform APIs;
- replicated metadata stores;
- static configuration;
- another mechanism that preserves the normative discovery semantics.

The choice of implementation must not change the identity represented by a
resolved `ActorRef` or `GroupRef`, make an existing concrete reference retarget
after name rebinding, manufacture lifetime or durability, or grant authority not
already carried by the resolved capability.

Discovery implementation also must preserve the pay-as-you-grow rule. A program
that does not use a discovery/distributed facility must not require a network
listener, external registry, Cluster membership, background discovery protocol,
or equivalent distributed runtime merely because an implementation supports
such machinery.

Core v0.1 therefore does not standardize:

- registry protocol or wire format;
- storage engine or replication algorithm;
- cache topology or cache invalidation strategy;
- polling versus push/watch implementation;
- backend selection or fallback order;
- registry server placement;
- health-probe implementation;
- implementation-specific TTL bookkeeping;
- implementation-specific retry/backoff policy.

Those mechanisms may affect performance and availability of the implementation,
but they must not retroactively choose otherwise-unfixed portable semantics.

This closure does not define a new public discovery API, namespace model,
consistency level, TTL contract, watch/notification semantics, federation model,
persistence guarantee, security model, or schema/versioning rule. Any such
portable facility remains subject to its own normative design.

This closes the former open ledger item `Service discovery implementation`.

## 72C. ActorRef Routing Implementation Is Runtime Machinery

**CLOSED**

Core v0.1 defines the semantic identity and communication behavior of an
`ActorRef`, but it does not standardize one internal mechanism for locating the
concrete Actor incarnation denoted by that reference.

A runtime may route an `ActorRef` through mechanisms such as:

- direct in-Process tables;
- Process- or Node-local routing tables;
- Cluster directories;
- distributed location metadata;
- cached routes;
- indirection through runtime-owned routing services;
- transport-specific endpoint metadata;
- another mechanism that preserves the normative ActorRef semantics.

The routing mechanism is not part of ActorRef identity. Updating, invalidating,
or replacing an internal route to the same live incarnation must not create a
new ActorRef identity or make two distinct Actor incarnations compare as one.

Routing machinery must preserve the existing rules that:

- an `ActorRef` denotes exactly one concrete Actor incarnation;
- the reference never retargets to a replacement Actor;
- destination death does not authorize transparent replay to another Actor;
- unreachability, unknown state, and known termination remain distinct;
- same-sender FIFO, snapshot, acceptance, backpressure, cancellation, failure,
  and delivery-uncertainty semantics do not depend on route-cache layout;
- Group routing remains a separate semantic layer and cannot be simulated by
  silently retargeting a concrete ActorRef.

A stale or missing internal route may lead only to outcomes already permitted by
the communication and reachability semantics. It must not cause a runtime to
invent a replacement destination, duplicate an accepted operation, erase
delivery uncertainty, or reinterpret one ActorRef as another.

Core v0.1 therefore does not standardize:

- ActorRef directory protocol;
- route-cache shape or eviction policy;
- route-refresh algorithm;
- routing-table replication;
- endpoint-address format;
- route lookup batching;
- implementation-specific forwarding hops;
- location-cache consistency mechanism.

These choices may affect performance and availability, but not portable ActorRef
semantics.

This closure does not define ActorRef persistence, serialization, external
capability encoding, or durable resolution across runtime restarts. Those remain
separate open design topics.

This closes the former open ledger item `ActorRef routing implementation`.

## 72D. Logical and Physical Topology Are Distinct

**CLOSED**

Core v0.1 distinguishes Protos logical topology from the physical topology used
to host or optimize an implementation.

The logical entities defined by the concurrency model retain only their
normatively specified meanings. In particular, a Protos `Process` is execution
capacity, a `Node` is runtime membership, and a `Cluster` is a coordination
domain. None of those meanings, by itself, creates a portable one-to-one mapping
to an operating-system process, host, virtual machine, container, pod, CPU
package, NUMA domain, rack, availability zone, region, subnet, or equivalent
infrastructure unit.

An implementation or infrastructure adapter may map logical entities onto
physical resources in any conforming way. It may co-locate multiple logical
entities, separate them physically, move implementation resources, or change
internal placement over time, provided every existing Protos-observable
identity, isolation, lifetime, authority, communication, failure, placement, and
continuity rule remains satisfied.

Therefore physical co-location does not:

- merge Actor, Process, Node, Group, or Cluster identity;
- create cross-domain mutable Protos references;
- grant authority or capability;
- make a non-transferable value transferable;
- imply same failure fate, shared lifecycle, or durable continuity;
- authorize ActorRef retargeting or Actor identity migration;
- imply that a particular transport must be used.

Conversely, physical separation does not by itself create a new Protos identity,
failure domain, message semantic, serialization contract, or distributed
authority boundary beyond those explicitly defined by the normative model.

Moving an implementation artifact or changing physical placement is not an Actor
migration unless a normative Actor-migration facility explicitly preserves the
Actor incarnation across that operation. Recreating an Actor elsewhere under the
ordinary lifecycle rules remains a new Actor incarnation with a new ActorRef.

Infrastructure topology may be used internally for placement, scheduling,
transport selection, failure avoidance, resource accounting, and administrative
diagnostics. Such use is implementation or policy machinery unless a separate
portable facility explicitly exposes a topology concept.

This closure does not decide the still-open APIs and policies for affinity,
anti-affinity, hard placement constraints, failure-domain configuration,
placement scoring, rebalancing, migration, capacity provisioning, or external
infrastructure adapters. Those facilities may deliberately refer to selected
physical concepts, but must define their own observable contracts.

This closes the former open ledger item `Relationship between logical Protos
topology and physical infrastructure topology`.

## Open Design Topics

The following topics remain intentionally open. Items whose fundamental
semantics are already closed are listed only for the API, policy,
mechanism, or implementation detail that still requires design.

-   Failure-domain discovery and configuration
-   Exact HA policy API and syntax
-   Exact Group/GroupRef API and syntax
-   Group creation/termination/durability API and ownership mechanics
-   Group controller API and controller-election mechanics
-   Group routing policy API
-   Advanced Group routing policies
-   Group broadcast and multicast semantics
-   Group membership transition protocol
-   Exact `spawn` API and syntax
-   Actor bootstrap representation
-   Exact SpawnOperation API
-   Exact SpawnOperation states
-   SpawnOperation timeout and cancellation API
-   Exact current-behavior installation/replacement API
-   Exact SendOperation API
-   Exact SendOperation states
-   Definition of what SendOperation `.value()` means
-   Delivery acknowledgement levels
-   Delivery guarantees
-   Retry API and policies
-   Message IDs and attempt IDs
-   Deduplication
-   Idempotency support
-   Persistent messaging
-   Special mailbox policies
-   Drop policies
-   Latest-only policies
-   Batching
-   Streaming
-   Async streams
-   Generators and suspendable iteration
-   Pub/sub
-   Advanced routers and load-balancing policies
-   Actor capacity policy
-   Process capacity provisioning policy
-   Node capacity provisioning policy
-   Capacity Demand API
-   Scale-up policy in infrastructure adapters/controllers
-   Scale-down policy in infrastructure adapters/controllers
-   Proactive capacity-demand signals
-   Draining policy and mechanics
-   Infrastructure Controller integration APIs
-   External infrastructure adapters such as Kubernetes or Nomad
-   Authority-scope API/model representation
-   Authority acquisition and transfer
-   Authority leases/election/consensus implementation
-   Authoritative/control-state storage and reconstruction
-   Fencing-token/API design for strict authoritative roles
-   Cluster authentication
-   Placement scoring algorithm
-   Placement stability and hysteresis
-   Placement policy priorities
-   Actor affinity and anti-affinity API
-   Hard placement constraints
-   Rebalancing algorithms and policy
-   Optional live Actor migration mechanism
-   Actor persistence
-   Actor checkpointing
-   Durable-state recovery API/mechanism
-   State replication
-   Replicated Actor/service semantics
-   ActorRef persistence/serialization semantics, if any
-   GroupRef persistence/serialization and capability semantics, if any
-   Message serialization format
-   Serialization versioning
-   Schema evolution
-   Timers
-   Clock semantics
-   Resource limits and quotas
-   Runtime resource-pressure model
-   Actor resource-cost estimation and learning
-   ActorRef/GroupRef capability security and authorization
-   Remote authentication
-   Cluster configuration UX
-   Cluster lazy startup
-   Node lazy activation
-   Durable Cluster bootstrap after zero active Nodes
-   Runtime metrics architecture
-   Scheduler/advisor interaction
-   Scheduler/capacity-demand interaction
-   Code identity for remote Actor bootstrap
-   Code availability and versioning across Nodes
-   Hot code update
