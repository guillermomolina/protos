# Protos Concurrency Model v0.1

Language version: 0.1 Document revision: 04 Status: Draft Last updated:
2026-09-02

# Protos Multithreading Design Ledger v1

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

**CLOSED**

Future and Actor solve different scaling problems.

> A Future scales with the amount of concurrent work.

> An Actor scales with the amount of parallelism and isolation.

Within one Actor, Futures behave conceptually like green threads or
green tasks.

An Actor may contain very large numbers of Futures.

However, only one segment of Protos code belonging to an Actor executes
at a time.

Therefore Futures inside the same Actor may interleave, but they do not
execute Protos code simultaneously.

Different Actors may execute simultaneously on different CPU cores.

An Actor is therefore a logical thread in the language model.

It is not defined as one operating-system thread. Mapping Actors to
operating-system threads or other carrier mechanisms is a runtime
implementation decision.

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

## 6. I/O

**CLOSED**

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

**CLOSED**

Messages from the same sender to the same Actor preserve FIFO ordering.

Within one Future or task, normal sequential execution order applies.

No global ordering is guaranteed between unrelated sources of work,
including different message senders, I/O completions, timers,
reactivated Futures, or other independently runnable work.

The runtime provides an abstract no-starvation guarantee among runnable
work that yields control.

The language does not currently specify a particular round-robin
algorithm, time quantum, or scheduler implementation.

A Protos computation that runs indefinitely without completing or
reaching a suspension point may monopolize its Actor.

Arbitrary preemption of Protos execution is not currently part of the
model.

## 8. Actor Creation

**CLOSED**

A newly created Actor starts clean with respect to the mutable state of
its creator.

It does not inherit:

-   The creator's live execution context
-   `this`
-   `moduleContext`
-   Return homes
-   Dynamic error handlers
-   Pending Futures
-   Mutable object graph

Only explicitly supplied initialization values cross the Actor boundary,
using normal Actor pass-by-value semantics.

A normal Closure cannot silently carry actor-local lexical captures into
another Actor.

Actor bootstrap code may use closure-like syntax or code descriptors,
but cross-Actor creation must not alter the existing semantics that
Closures capture lexical contexts by reference.

The exact bootstrap API and syntax remain open.

## 9. Actor Initialization and Readiness

**CLOSED**

The conceptual initial lifecycle is:

    CREATED
        |
        v
    INITIALIZING
        |
        v
    READY
        |
        v
    implicit event loop

An Actor processes no external messages until initialization completes
successfully.

Messages arriving while the Actor is INITIALIZING may be queued, but
they are not dispatched until the Actor reaches READY.

If initialization fails with an unhandled error, the Actor never reaches
READY.

Normal completion of initialization does not terminate the Actor.

Instead, successful initialization transitions the Actor to READY and
its implicit event loop begins.

## 10. Implicit Event Loop

**CLOSED**

The Actor event loop is runtime machinery.

Normal Protos Actor code does not require an explicit receive loop.

The programmer defines behavior. The runtime dispatches Actor turns
automatically.

A lower-level receive/event API may be considered later, but it is not
the normal Actor programming model.

## 11. Current Behavior

**CLOSED**

Each Actor has a replaceable current behavior.

The current behavior is an ordinary Protos object.

External messages are dispatched against the behavior that is current
when the corresponding Actor turn begins.

Changing the current behavior affects subsequent turns. It does not
alter the receiver or semantics of an activation that is already
running.

Messages already queued in the mailbox are therefore dispatched using
the behavior current when their turn begins, not necessarily the
behavior that existed when the message was sent.

An ActorRef identifies the Actor, not its current behavior.

The exact API for installing or replacing the current behavior remains
open.

## 12. Actor Message Dispatch

**CLOSED**

An external Actor message conceptually consists of:

    selector + arguments

The message is dispatched against the Actor's current behavior using the
ordinary Protos dispatch rules.

The same normal Protos rules apply:

-   Slot lookup
-   Delegation
-   `this`
-   Closure activation
-   Message-not-understood behavior
-   Normal error semantics

No second Actor-specific method or message dispatch system exists.

During handler execution, `this` is the current behavior object
according to normal Protos activation semantics.

## 13. send()

**CLOSED**

`send()` represents one-way Actor communication.

It returns a local identity-bearing communication operation object,
provisionally called `SendOperation`.

A SendOperation represents the logical send and may expose information
or operations concerning:

-   Status
-   Progress
-   Waiting
-   Cancellation
-   Retry
-   Attempts
-   Logical message identity
-   Destination
-   Last error
-   Delivery uncertainty

A SendOperation is communication-specific and is therefore distinct from
a generic Future.

Retry is explicit rather than an invisible default because retrying an
uncertain delivery may create duplicates.

Protos does not promise exactly-once delivery by default.

The exact SendOperation API and status set remain open.

## 14. ask()

**CLOSED**

`ask()` represents request/reply communication.

It returns a Future.

The runtime automatically manages the ephemeral reply capability and any
required correlation, routing, and reply delivery.

The normal final result of the Actor handler becomes the reply value.

One `ask()` produces one logical response.

Streaming or multi-response communication will use a separate
abstraction rather than stretching `ask()` into a streaming protocol.

Timeout and cancellation follow the general Future and communication
rules.

A timeout while waiting does not imply that remote work did not execute.

## 15. send() and ask() Share Dispatch

**CLOSED**

`send()` and `ask()` use the same Actor message dispatch mechanism.

For `send()`, the handler result is ignored.

For `ask()`, the handler result resolves the caller Future.

There are no separate send handlers and ask handlers.

## 16. Pass-by-Value Between Actors

**CLOSED**

Messages between Actors have pass-by-value semantics.

No ordinary Protos reference crosses an Actor boundary.

Transferability currently follows these rules:

-   Number: transferable by value
-   String: transferable by value
-   Boolean: transferable by value
-   null: transferable by value
-   Ordinary mutable object: transferable as a logical value copy
-   Array: transferable as a logical value copy
-   Cyclic object graph: transferable while preserving graph structure
    and aliasing conceptually
-   ActorRef: transferable as a special communication capability
-   Closure: not transferable
-   Future: not transferable
-   ExecutionContext: not transferable
-   Socket: not transferable
-   Open file: not transferable
-   Native resource: not transferable
-   Java object: not transferable by default

ActorRef is deliberately transferable because it provides communication
capability rather than direct access to another Actor's mutable heap.

Closures are not transferable because they capture actor-local lexical
execution contexts by reference.

The destination Actor executes code that it already owns rather than
receiving arbitrary executable closures carrying another Actor's lexical
environment.

## 17. Message Snapshot Time

**CLOSED**

A message captures its logical value snapshot at `send()` time.

This snapshot rule is semantic. It does not prescribe when or how the
runtime physically copies memory.

## 18. Message Transfer Optimizations

**CLOSED**

Pass-by-value is an observable semantic rule, not a requirement to
physically duplicate every byte immediately.

The runtime may use optimizations including:

-   Copy-on-write
-   Immutable physical sharing
-   Shared backing storage
-   Shared memory
-   Zero-copy
-   Page remapping
-   Serialization
-   Streaming
-   Scatter/gather I/O

provided that the program observes exactly the semantics of a snapshot
taken at send time and cannot observe shared mutable identity between
Actors.

## 19. Buffers

**CLOSED**

Buffers retain the same pass-by-value and snapshot semantics as other
transferable mutable values.

Protos does not initially introduce a special shared mutable Buffer
exception.

It also does not initially require Rust-like explicit move or borrow
semantics merely to obtain efficient Buffer transfer.

The runtime may specialize Buffer storage aggressively using
copy-on-write, zero-copy, shared immutable backing, scatter/gather, or
streaming.

Principle:

> First optimize automatically what is semantically invisible; require
> programmer-visible architectural changes only when they materially
> matter.

## 20. End-to-End Backpressure

**CLOSED**

Backpressure applies to the complete delivery path, not only to the
destination mailbox.

No intermediate queue may grow indefinitely in order to hide a slow
final consumer.

Pressure must be capable of propagating back toward the originating
SendOperation.

The underlying transport may be in-process, shared memory, IPC, TCP,
QUIC, or another transport without changing the Actor communication
semantics.

## 21. Mailbox Bounds

**CLOSED**

Every Actor mailbox has an effective finite bound.

The bound may be runtime-managed, explicitly configured, or adaptive,
but a mailbox is never conceptually an unlimited sink.

When the delivery path cannot currently accept additional messages,
`send()` enters backpressure through its SendOperation.

It does not block an operating-system thread and does not silently
discard the message.

Special explicit policies such as dropping, latest-only delivery,
bounded loss, batching, or telemetry-oriented behavior may be considered
later. They are not the default.

## 22. Timeouts and Deadlines

**CLOSED**

Awaitable operations may support a wait timeout.

A wait timeout affects waiting. It does not automatically cancel the
underlying operation.

A timeout must never be interpreted as proof that an operation did not
occur.

For remote communication, uncertainty may result in a state such as
`deliveryUnknown`.

Communication operations may additionally support delivery deadlines or
cancellation where appropriate.

The exact APIs remain open.

## 23. Cancellation

**CLOSED**

Cancellation is cooperative.

Protos does not arbitrarily interrupt Actor code in the middle of an
instruction by injecting asynchronous exceptions.

Cancellation requests are observed at safe points such as suspension
points, runtime operations, or cooperative checkpoints.

Cancelling a Future requests cancellation of its work.

Cancelling a SendOperation attempts to prevent further delivery while
that remains safe.

If the destination has already accepted the message, cancellation cannot
unsend it.

Principle:

> Cancellation never reverses effects that have already occurred.

## 24. Structured Concurrency

**CLOSED**

The existing structured-concurrency semantics for Futures remain.

Asynchronous child work created inside an execution context is owned by
that context by default unless explicitly detached.

Actor creation does not automatically establish the same ownership
relationship.

Structured concurrency therefore governs Futures within an Actor, not
Actor lifetime in general.

## 25. Parent Actor Versus Failure Authority

**CLOSED**

Actor creation and Actor failure authority are separate relationships.

`parentActor` identifies the Actor that created the current Actor.

It represents genealogy/origin and provides an initial communication
capability toward the creator.

It does not imply ownership or automatic lifecycle propagation.

The entity with failure authority is responsible for applying failure
policy. That authority need not be an ordinary Actor.

If Actor A creates Actor B and A later terminates, B does not
automatically terminate merely because A was its creator.

Lifecycle consequences are defined by failure policy, not parenthood.

## 26. Supervision and Failure Authority

**CLOSED --- REVISED**

Every Actor has a failure authority, but this does not imply a dedicated
Supervisor Actor.

A runtime root, controller, Actor, or other runtime entity may fulfill
the supervision role.

When an Actor suffers an unhandled fatal failure, its failure authority
receives or observes structured failure information and applies the
relevant policy.

Possible policies may include:

-   Replace
-   Stop
-   Escalate
-   Ignore

`restart` is not a distinct Actor-lifecycle semantic operation.

Actors are replaced, never restarted.

Replacement creates a new Actor. The replacement does not inherit the
failed Actor's identity, ActorRef, private mutable heap, pending
interactions, or mailbox.

The exact policy API remains open.

For a trivial Process containing only its RootActor, no dedicated
supervisor machinery is required.

By default:

    RootActor fatal failure
        -> Process terminates

This preserves the pay-as-you-grow principle.

## 27. Actor Identity Is Incarnation Identity

**CLOSED --- REVISED**

Actor identity is the identity of one concrete Actor incarnation.

An ActorRef identifies that concrete Actor.

If the Actor terminates, its ActorRef does not become a reference to a
replacement.

Conceptually:

    Actor A
        |
        +-- ActorRef(A)
        |
        X terminates

    ActorRef(A) -> TERMINATED

If policy creates a replacement, that replacement is another Actor with
another ActorRef.

There is no hidden logical Actor identity spanning multiple
incarnations.

Stable logical service identity belongs to higher-level abstractions
such as Actor Groups or service discovery, not to an individual Actor.

## 28. Messages Across Actor Failure

**CLOSED --- REVISED**

Messages that have already been processed retain their effects.

Messages addressed directly to a terminated Actor are never retargeted
to another Actor.

Messages that have definitely not been accepted remain governed by
normal SendOperation semantics, but the runtime does not change their
destination to a replacement.

Messages accepted by an Actor before it dies are not automatically
reinjected into another Actor.

Automatic reinjection or transparent retry could duplicate effects if
the failed Actor partially processed the operation.

Stronger guarantees require explicit mechanisms such as:

-   Idempotency
-   Acknowledgements
-   Persistence
-   Deduplication
-   Transactional protocols

These mechanisms remain to be designed.

## 29. Monitoring Versus Supervision

**CLOSED --- REVISED**

Supervision/failure authority and monitoring are distinct.

Failure authority controls or determines the reaction to Actor failure.

Monitoring or watching observes lifecycle without controlling it.

SendOperation reports the state of a particular communication operation.

Potential lifecycle observations include:

-   Started
-   Ready
-   Failed
-   Terminated
-   Unreachable
-   Unknown

Replacement is the creation of another Actor and is not a lifecycle
continuation of the terminated Actor.

For distributed Actors:

    TERMINATED != UNREACHABLE != UNKNOWN

A network partition or routing failure cannot by itself prove that a
remote Actor has terminated.

## 30. Runtime Health and Watchdog

**CLOSED --- REVISED**

The runtime provides inexpensive always-on health information.

The mandatory fast path must remain O(1), non-blocking, and free from
global coordination.

Always-on information may include:

-   Actor lifecycle state
-   Approximate progress epoch
-   Mailbox depth
-   Failure count
-   Failure authority/root ownership
-   A small number of counters

More expensive analysis should use sampling or optional instrumentation.

The runtime must not silently repair arbitrary mutable Actor state.

If policy requires continued capacity or service membership after
failure, replacement creates a fresh Actor rather than repairing the
failed Actor's heap.

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

Every Protos Process begins with a RootActor.

A trivial program may conceptually consist only of:

    Process
        |
        v
    RootActor

The RootActor owns the initial program state, including ordinary
objects, module state, configuration, and Futures.

Within the RootActor, ordinary state access uses normal Protos semantics
and does not incur Actor message-passing overhead.

If the RootActor suffers a fatal unhandled failure in this minimal
configuration, the Process terminates.

No dedicated Supervisor Actor, Group controller, distributed membership
service, or other unnecessary runtime machinery is required.

Creating additional Actors explicitly introduces new isolation and
parallelism boundaries.

If a program never creates another Actor, the runtime or JIT may
optimize away unnecessary Actor infrastructure.

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

**CLOSED**

Each Actor has its own logical module registry and module contexts.

Actors do not inherit mutable module contexts from their creator.

If two Actors import the same module, mutable module-level state is
logically separate in each Actor.

The runtime may physically share immutable implementation artifacts such
as compiled code, immutable metadata, frozen core objects, or shared
prelude implementation, provided that the sharing is not observable as
shared mutable Protos state.

A module singleton is therefore logically per Actor unless a future
explicit distributed abstraction says otherwise.

## 35. Scope Roots

**CLOSED --- REVISED**

The distributed runtime has the conceptual hierarchy:

    ClusterRoot
        |
        v
    NodeRoot
        |
        v
    ProcessRoot
        |
        v
    RootActor

Only RootActor is necessarily an ordinary Protos Actor.

ProcessRoot, NodeRoot, and ClusterRoot should normally be runtime
services or capabilities rather than ordinary Actors.

Conceptual responsibilities include:

ClusterRoot:

-   Cluster membership
-   Discovery
-   Placement coordination
-   Cluster health
-   Cluster authority facilities when required

NodeRoot:

-   Node resources
-   Local Processes
-   Node health
-   Local coordination

ProcessRoot:

-   Process lifecycle
-   Actor management
-   Root failure authority
-   Runtime services

RootActor:

-   Application-level mutable state
-   Application code
-   Normal Protos execution

A standalone Process does not need active distributed Cluster
infrastructure.

NodeRoot and ClusterRoot facilities should be lazy or absent when they
are not needed.

## 36. Actor Runtime Context References

**CLOSED AS DIRECTION --- REVISED**

An Actor may have access to a minimal set of implicit runtime references
or capabilities without inheriting mutable state.

These may conceptually include:

-   `selfActor`
-   `parentActor`
-   `rootActor`
-   Failure-authority or supervisor capability
-   Process root capability
-   Node root capability
-   Cluster root capability

The exact names and APIs remain open.

These values do not provide direct shared-memory access to another
Actor's mutable object graph.

## 37. Process Boundary

**CLOSED --- REVISED**

Process is a stronger isolation boundary than Actor.

It provides an address-space and failure boundary.

Actor-to-Actor and Process-to-Process communication use the same
fundamental pass-by-value message semantics.

Two Protos Processes on the same physical host may communicate using
optimized mechanisms such as shared memory, mmap, IPC, or local sockets
only when the runtime has actually established the required physical
locality and capability.

Different machines may use network transports.

The transport must not change observable message value semantics or
expose cross-process mutable references.

A Process is ephemeral execution capacity, not a durable application
identity.

If a Process terminates, the Actors hosted by that Process terminate.

A later Process is new capacity, not a reincarnation of the previous
Process.

Failure of a remote Process or communication channel does not
automatically terminate a local Process.

## 38. Node

**CLOSED --- REVISED**

A Node is a logical, ephemeral member of the Protos distributed runtime
capable of coordinating one or more Protos Processes.

Conceptually:

    Node
        |
        +-- Process
        +-- Process
        `-- Process

A Protos Node does not semantically correspond to a physical machine,
VM, container host, Kubernetes Node, or other infrastructure identity.

Multiple Protos Nodes may exist on one physical host, and infrastructure
may map Protos Nodes to physical resources in different ways.

Node is primarily a logical scope for:

-   Process coordination
-   Placement
-   Resources
-   Membership
-   Runtime health
-   Routing and transport coordination

Physical host topology and host-level failure are separate
topology/failure-domain information and must not be inferred from Node
identity.

Node identity does not survive loss, removal, or replacement.

If a Node disappears and later equivalent capacity joins, the new member
is another Node.

Infrastructure identity does not imply Protos Node identity.

If a Node becomes unreachable, remote entities become UNKNOWN or
UNREACHABLE before the runtime can legitimately conclude that they are
terminated.

## 39. Cluster

**DIRECTION CLOSED, DETAILS OPEN --- REVISED**

A Cluster is the logical distributed coordination domain across Protos
Nodes.

Cluster identity is independent of the identity of its current Nodes.

Nodes may join, leave, fail, or be replaced while the logical Cluster
remains the same coordination domain.

Cluster functionality may include:

-   Membership
-   Discovery
-   Routing
-   Placement
-   Node health
-   Resource awareness
-   Failure detection
-   Group/controller coordination
-   Authority
-   Rebalancing

Whether Cluster identity can survive the loss of all members remains
open and depends on the future bootstrap, membership, authority, and
persistence model.

Cluster functionality must remain lazy and must not impose
distributed-runtime costs on ordinary standalone programs.

## 40. ActorRef

**CLOSED --- REVISED**

ActorRef is an opaque communication capability identifying exactly one
concrete Actor.

It is not:

-   A pointer
-   A host and port
-   A URL
-   A physical Process identifier
-   A Node address
-   A stable service identity

ActorRef identity is independent of physical placement but not
independent of Actor lifetime.

An Actor may move if migration is eventually supported without changing
its ActorRef, provided it remains the same Actor.

If the Actor terminates, the ActorRef identifies a terminated Actor and
is never transparently rebound to a replacement.

Possessing an ActorRef does not imply that the target Actor is currently
reachable.

## 41. Actor Discovery

**CLOSED AS DIRECTION --- REVISED**

Actor identity and stable service naming are separate.

ActorRef represents one concrete Actor identity/capability.

A service name or Actor Group may represent a stable logical destination
whose concrete Actor membership can change.

Discovery must be pluggable.

The Protos language semantics must not hard-code DNS, Consul,
Kubernetes, or a specific service registry.

Those mechanisms may implement discovery without becoming part of the
language model.

## 42. Automatic Actor Placement and Capacity

**CLOSED --- REVISED**

The normal programmer creates a logical Actor without selecting its
physical location.

Conceptually:

    worker: spawn(Worker, args).value()

means:

> Create this Actor.

It does not normally mean:

> Create this Actor in this Process, on this Node, or on this physical
> host.

The runtime scheduler determines placement automatically within the
currently active execution domain and currently available capacity.

Conceptually:

-   If only the current Process is active, the Actor is placed in that
    Process.
-   If a Node coordinates multiple available Processes, the Actor may be
    placed in any suitable Process within that Node.
-   If a Cluster coordinates multiple Nodes and Processes, the Actor may
    be placed in any suitable location within that Cluster.

The same Actor creation code therefore scales according to the runtime
domain that is already active.

Normal `spawn()` does not itself create or activate higher runtime
domains or new infrastructure capacity.

In particular, `spawn()` does not by itself:

-   start another Process;
-   create or activate a Node;
-   create or join a Cluster;
-   provision a machine, VM, container, Pod, or infrastructure workload.

Scheduling and infrastructure capacity provisioning are separate
responsibilities.

> Scheduling decides where to place work within existing capacity.

> Capacity provisioning decides how much execution/infrastructure
> capacity exists.

The Protos scheduler uses capacity that is already available.

The core Protos runtime does not provision infrastructure capacity by
default.

External or explicitly integrated infrastructure mechanisms may create
or remove Processes, Nodes, Pods, VMs, machines, or other workload
units.

Cluster membership determines which active Protos Nodes participate in
distributed placement.

The scheduler may consider CPU/runnable pressure, memory, communication
locality, affinity, resources, health, network cost, failure domains,
and placement stability.

Physical topology remains observable for diagnostics, administration,
and optimization, but it is not normally part of application logic or
Actor identity.

## 43. Spawn Backpressure

**CLOSED --- REVISED**

If the scheduler cannot admit a new Actor within currently available
capacity and placement policies, Actor creation applies backpressure
rather than failing immediately or forcing unlimited oversubscription.

Conceptually:

    spawn(...)
        -> SpawnOperation
        -> PENDING
        -> capacity becomes available
        -> Actor initialization
        -> READY

A pending SpawnOperation may be waited on, cancelled, or subject to a
timeout.

Actor capacity does not imply one CPU or other fixed resource
reservation per Actor. Many Actors may share the same Process.

The scheduler places Actors using capacity that already exists.

Pending demand may contribute to Protos capacity-demand signals.

An external or explicitly integrated Infrastructure Controller may react
by provisioning additional capacity.

`spawn()` itself does not create Processes, Nodes, Clusters, Pods, VMs,
or machines.

## 44. Hierarchical Runtime Domains

**CLOSED**

The Protos execution hierarchy is intrinsic to the runtime:

    Actor
        -> Process
        -> Node
        -> Cluster

Each level coordinates multiple units of the level below when such
coordination is required.

A Process is a concrete address-space and runtime execution boundary.

A Node coordinates one or more Protos Processes.

A Cluster coordinates multiple Protos Nodes.

External infrastructure managers may provision, place, restart, or scale
physical workloads, but they do not replace Protos runtime membership,
ActorRef routing, health, or logical coordination.

Physical co-location must never be inferred solely from Protos logical
topology.

Optimizations depending on physical locality must only be used when the
runtime can establish that the required locality or capability actually
exists.

The hierarchy is pay-for-what-you-use: levels unnecessary for the active
execution domain need not incur distributed-runtime machinery.

## 45. Dynamic Actor Capacity and Placement

**CLOSED**

Actor capacity is primarily dynamic rather than based on fixed
declarative resource reservations.

Normal Actors are not expected to declare Kubernetes-like CPU or memory
requests.

Placement occurs conceptually in two stages:

1.  Hard feasibility filtering.
2.  Scoring of feasible destinations using dynamic runtime information.

Hard constraints may include unavailable/draining Process, hard
memory/resource limit, incompatible runtime/code, required special
resource, or isolation/placement constraint.

Scoring may consider CPU/runnable pressure, memory pressure, scheduler
latency, mailbox pressure, communication locality, affinity, resource
locality, and failure domains.

Manual resource requirements and placement constraints may exist when
genuinely required but are exceptional.

The runtime may learn from observed Actor behaviour instead of requiring
the programmer to predict resource consumption in advance.

## 46. Adaptive Admission Control

**CLOSED --- REVISED**

The ability to technically fit another Actor and the desirability of
admitting more work are distinct concepts.

Soft resource pressure normally affects placement scoring and
contributes to proactive capacity-demand signals.

When pressure becomes sufficiently severe, the runtime may temporarily
stop admitting additional Actors, causing SpawnOperations to remain
pending and applying backpressure.

Admission decisions are adaptive and multidimensional.

No fixed CPU, memory, or utilization threshold is part of the language
semantics.

The runtime separates conceptually:

    placement pressure
    capacity-demand pressure
    admission pressure

while allowing all three to use common runtime observations.

Capacity demand should be observable before admission is necessarily
refused, allowing infrastructure mechanisms to react proactively.

## 47. Multi-Objective Placement

**CLOSED**

Protos does not define a universal PACK or SPREAD placement strategy.

Placement is multi-objective and balances:

-   Performance and scalability
-   Availability and resilience
-   Efficiency
-   Stability

Actors that communicate heavily may be placed together when locality is
more valuable, while independent CPU-intensive Actors may be spread to
exploit parallel execution.

High-availability requirements may intentionally override
performance-optimal placement.

The scheduler should not continuously chase a theoretically optimal
placement. Rebalancing should occur only when expected benefit
sufficiently exceeds cost.

Placement may use hard constraints and soft affinity or anti-affinity
hints when application intent cannot be inferred safely.

## 48. Failure Domains

**CLOSED**

Protos models failure domains explicitly but generically.

A failure domain represents a known shared-fate relationship: failure of
the domain may simultaneously affect all runtime entities placed within
it.

Failure domains are not required to form a strict hierarchy and may
overlap.

Process is intrinsically a failure domain because failure of a Process
affects the Actors hosted by that Process.

Additional physical or infrastructure failure domains may include host,
rack, power domain, availability zone, site, or infrastructure-specific
domains.

General topology information and failure-domain information are related
but distinct concepts.

Logical Protos topology must not imply physical failure independence.

Unknown topology means unknown failure independence, not independent
failure.

Protos only claims availability guarantees that can be demonstrated from
failure-domain information currently known to the runtime.

The exact mechanisms for failure-domain discovery, configuration, and
topology providers remain open.

## 49. High-Availability Placement Requirements

**CLOSED --- REVISED**

High-availability placement requirements belong primarily to logical
groups of Actors representing a common service or responsibility rather
than to individual Actors.

A Group expresses availability intent declaratively, for example:

    desired members = 3
    tolerate one host failure
    tolerate one availability-zone failure

Application code normally expresses required redundancy or failure
tolerance rather than selecting concrete Processes, Nodes, hosts, zones,
or physical locations.

The scheduler uses known topology/failure-domain information for
placement.

The Protos capacity-demand model may express additional independent
capacity required to satisfy availability objectives.

Availability objectives and mandatory availability requirements are
distinct.

Availability status is conceptually:

    SATISFIED
    UNSATISFIED
    UNKNOWN

The runtime must never report an availability guarantee as SATISFIED
unless it can demonstrate that guarantee from available information.

High-availability placement does not by itself provide replicated Actor
state, persistence, consensus, transactional replication, failover of
mutable state, or exactly-once processing.

Actor liveness is ephemeral by default; durability is explicit.

## 50. Runtime Groups

**CLOSED --- REVISED**

Groups are logical policy and management units over homogeneous runtime
entities.

A Group is not an additional level in the intrinsic runtime hierarchy:

    Actor -> Process -> Node -> Cluster

Conceptually, groups may exist for entities at different levels:

    Group<Actor>
    Group<Process>
    Group<Node>

This notation is conceptual and does not imply generic type syntax.

Each group-managed entity belongs to one management Group at its
corresponding level. Each grouping level provides an implicit default
Group.

Specialized Groups are introduced only when a set of entities requires
policy different from the default, such as availability, affinity,
placement, scaling/capacity policy, routing, or resource policy.

Group and Group Controller are distinct concepts.

The Group identifies the managed set and its policy.

A Group Controller may maintain or modify Group membership according to
that policy.

Not every Group requires active membership management.

A Group may define desired cardinality.

Desired cardinality only specifies the desired number of members. It
does not imply identical state, state replication, shared history,
transparent failover, consensus, or persistent service state.

### ActorGroup Communication

An Actor Group may optionally act as a communication destination.

This capability is lazy and follows the Protos pay-as-you-grow
principle.

ActorRef semantics are:

    ActorRef -> one concrete Actor

Communication with an Actor Group instead addresses the logical Group
and allows its routing policy to select a member.

Normal `send` or `ask` directed to an Actor Group selects exactly one
eligible member.

Broadcast/multicast is a distinct explicit operation.

The Group retains stable logical identity while concrete membership may
change.

While the Group still owns routing responsibility and no concrete Actor
has accepted an operation, it may wait for capacity or choose another
eligible member.

Once a particular Actor has accepted a message, normal Actor
delivery/failure semantics apply.

Actor Group communication does not imply transparent retry on another
member after acceptance and does not introduce exactly-once delivery
semantics.

If an Actor Group currently has no eligible member, communication
applies backpressure.

Demand for a Group with insufficient eligible membership may contribute
to capacity-demand signals.

Sending to a Group does not itself create Actors, Processes, Nodes, or
infrastructure capacity.

## 51. Capacity Demand and Infrastructure Integration

**CLOSED**

The core Protos runtime does not provision infrastructure capacity by
default.

Protos observes runtime state, performs placement/admission decisions,
and exposes semantic capacity demand.

Capacity demand may reflect conditions such as:

-   Pending SpawnOperations
-   Group demand with insufficient eligible members
-   Resource pressure
-   Hard placement/resource constraints
-   Unsatisfied availability objectives
-   Missing independent failure-domain capacity

Capacity demand is information, not an imperative provisioning order.

An external or explicitly integrated Infrastructure Controller decides
whether and how to satisfy that demand according to the capabilities,
policy, cost, timing, and topology of its environment.

Conceptually:

    Protos runtime
        |
        | semantic capacity demand
        v
    Capacity Demand API
        |
        +--> metrics/OpenMetrics adapter
        +--> Kubernetes custom/external metrics adapter
        +--> KEDA-style external scaler
        +--> Nomad adapter
        `--> custom Infrastructure Controller

Metrics are one possible representation of Protos demand. They are not
the semantic model itself.

Newly provisioned capacity is incorporated into Protos through the
runtime's normal bootstrap, discovery, and membership mechanisms.

The Infrastructure Controller does not directly mutate Protos logical
topology.

For example, an external orchestrator may create a new workload
containing a Protos runtime configured with sufficient bootstrap
information. That runtime then joins the appropriate Protos Node or
Cluster through normal membership mechanisms.

As new Processes or Nodes become available, Protos may dynamically
activate the required higher coordination layers.

This preserves pay-as-you-grow.

Processes and Nodes may disappear at any time.

Correctness must not depend on graceful removal.

Draining is an optimization for planned capacity removal, not a
correctness requirement.

A draining Process or Node may stop accepting new placement and may
attempt to reduce disruption before infrastructure removal, but
unexpected loss follows the same fundamental failure model.

Exact Capacity Demand API, infrastructure adapters, scale-up/down
policy, and draining mechanics remain open.

## 52. Ephemeral Actor Liveness and Explicit Durability

**CLOSED**

Actor liveness is ephemeral by default; durability is explicit.

An Actor may die at any time.

The programming model must not assume that an Actor incarnation, its
hosting Process, or its hosting Node will live indefinitely.

If an Actor dies, its private mutable state dies with it unless that
state was explicitly externalized through a durability mechanism.

Possible future durability mechanisms may include:

-   Persistent state
-   Checkpointing
-   Event logs
-   Replication
-   External databases
-   Other recoverable storage

These are not implicit properties of an ordinary Actor.

Protos does not attempt to make every Actor fault tolerant by default.

The basic runtime may restore required service capacity by creating
replacement Actors, but that does not constitute transparent
continuation of the failed Actor.

Principle:

> Actor incarnations are disposable. Durability, when required, must
> live outside the ephemeral incarnation.

## 53. Direct ActorRef Versus Stable Group Identity

**CLOSED**

An ActorRef addresses one concrete Actor.

If that Actor dies, the ActorRef remains associated with that terminated
Actor.

It is not rebound to a replacement.

If application code keeps a direct ActorRef, it has explicitly chosen to
communicate with that individual Actor and therefore observes that
Actor's mortality.

Stable service continuity belongs to a higher-level abstraction such as
an Actor Group.

Conceptually:

    ActorRef
        -> individual, ephemeral destination

    ActorGroup
        -> stable logical destination
        -> changing membership

Communication directed to a Group may continue across member replacement
according to Group routing rules.

Communication directed to an individual Actor does not.

## 54. Actors Are Replaced, Never Restarted

**CLOSED**

`restart` is not a distinct semantic continuation of an Actor.

When policy requires continued service after an Actor dies, the runtime
creates another Actor.

Conceptually:

    Actor A
        X

    replacement policy
        |
        v

    Actor B

Actor B may use the same behavior, bootstrap code, configuration, or
role, but it is another Actor.

It has:

-   A new Actor identity
-   A new ActorRef
-   Fresh private state
-   Fresh initialization
-   No inherited mailbox
-   No inherited pending interactions

Administrative tooling may use the word "restart" informally, but
semantically the operation is termination followed by creation of
another Actor.

Principle:

> Actors are replaced, never restarted.

## 55. Supervision Versus Group Controllers

**CLOSED**

Supervision/failure authority reacts to failures.

Group Controllers maintain desired Group state.

These responsibilities are distinct.

A failure authority may observe an Actor failure and apply failure
policy.

A Group Controller observes Group policy and current membership and acts
when the desired state is not satisfied.

For example:

    desired members = 4
    actual members = 3
        -> Group Controller may create one new Actor

For a Group member, replacement capacity may arise simply because the
Group Controller observes that desired membership is no longer
satisfied.

The replacement is not a continuation of the failed member.

The runtime may optimize supervision and Group control together
internally, but the semantic responsibilities remain distinct.

## 56. Failure Authority Is Pay-As-You-Grow

**CLOSED**

Every Actor has a failure authority, but this does not imply a dedicated
Supervisor Actor or separate runtime machinery.

The failure authority may be provided by the nearest
runtime/root/controller relationship already required by the active
execution domain.

For the minimal program:

    Process
        |
        `-- RootActor

the default fatal-failure behavior is:

    RootActor fatal failure
        -> Process terminates

No additional Actor, mailbox, controller, or distributed service is
required merely to supervise the RootActor.

As applications introduce more Actors, Groups, Nodes, or Cluster
coordination, richer failure-policy machinery may become active only
where required.

Supervisor is therefore a semantic role, not necessarily an Actor.

This follows the pay-as-you-grow principle.

## 57. Outstanding Direct Interactions Die With the Actor

**CLOSED**

Actor death terminates direct outstanding interactions with that Actor.

Replacement does not inherit them.

For a direct `ask`, if Protos can determine that the destination Actor
has terminated before producing the reply, the returned Future fails
according to the communication failure semantics.

The runtime does not transparently find a replacement and replay the
request.

For Group-addressed communication, routing may choose another member
only while the Group still owns routing responsibility and no concrete
Actor has accepted the operation.

Once a concrete Actor has accepted an operation, its subsequent death
does not cause transparent replay against another Actor.

## 58. Rerouting Belongs to Group Addressing

**CLOSED**

Messages addressed directly to a terminated Actor are never retargeted
to another Actor.

This remains true even if the runtime can prove that a particular
message had not yet been accepted by the Actor before termination.

The destination of that operation was the concrete ActorRef, not
"whoever replaces it."

The SendOperation may distinguish states such as
definitely-not-delivered, delivery-unknown, accepted, failed, or other
future statuses, but none of those states authorize implicit destination
substitution.

For Actor Group communication, the Group may choose or re-choose an
eligible member while routing responsibility still belongs to the Group
and no concrete Actor has accepted the operation.

Principle:

> Rerouting is a property of Group addressing, never of ActorRef failure
> recovery.

## 59. Termination, Unreachability, and Unknown State

**CLOSED**

Actor termination is a known fact.

Unreachability is an observation.

Unknown means that the runtime lacks sufficient information to determine
the Actor's current state.

Conceptually:

    TERMINATED
        The runtime has sufficient evidence that this Actor incarnation no longer exists.

    UNREACHABLE
        The runtime currently cannot communicate with the Actor.

    UNKNOWN
        The runtime lacks sufficient information to determine its state.

Therefore:

    UNREACHABLE != TERMINATED

and:

    UNKNOWN != TERMINATED

A timeout, network partition, routing failure, or missing heartbeat does
not by itself prove Actor termination.

Protos never equates unreachability with termination without sufficient
evidence.

The exact failure-detection and membership mechanisms used to establish
such evidence remain open.

## 60. Unreachability Applies Backpressure

**CLOSED**

Temporary unreachability applies backpressure; it does not by itself
terminate or retarget an operation.

For a direct send to an Actor that is currently unreachable:

    send
        -> PENDING / backpressured
        -> target reachable again: delivery may continue
        -> target known TERMINATED: operation fails
        -> deadline expires: deadline/failure semantics apply
        -> caller cancels: cancellation semantics apply

This does not imply unbounded buffering.

End-to-end bounded queues and backpressure remain in force.

Unreachability does not authorize retry against a replacement or
redirection to another Actor.

For Group communication, another member may be selected only while the
Group still owns routing responsibility and no concrete Actor has
accepted the operation.

## 61. Processes Are Ephemeral Capacity

**CLOSED**

Processes are ephemeral execution capacity, not durable application
identities.

If a Process dies, the Actors hosted by that Process die.

Their ActorRefs terminate according to the normal Actor failure
semantics.

Higher-level mechanisms react independently:

-   Group Controllers may restore desired membership
-   Failure authorities may apply policy
-   Capacity-demand signals may increase
-   External Infrastructure Controllers may provision additional
    capacity

A newly created Process is new capacity.

It is not semantically the reincarnation of the Process that
disappeared, even if external infrastructure considers it a replacement
workload.

Protos does not require resurrection of a particular Process identity.

## 62. Nodes Are Ephemeral Runtime Members

**CLOSED**

Nodes are ephemeral runtime members.

Node identity does not survive loss, removal, or replacement.

Infrastructure identity does not imply Protos Node identity.

If Node N1 disappears and later equivalent capacity joins as N2, N2 is
another Protos Node.

A physical VM, host, container, Kubernetes resource, or other
infrastructure identity may be long-lived or pet-like without making the
corresponding Protos Node identity durable.

Before membership/failure detection has sufficient evidence of loss, an
unreachable Node remains distinct from a known removed/lost Node.

Principle:

> Node is cattle too.

## 63. Cluster Identity Is Independent of Node Identity

**CLOSED**

Cluster identity is a logical coordination-domain identity, independent
of the identity of its current Nodes.

Nodes may enter and leave while the Cluster remains the same logical
domain.

No individual Node is required to be the durable identity anchor for the
Cluster.

Whether Cluster identity can survive the loss of all members remains
open.

That stronger property depends on the future membership, bootstrap,
authority, and persistence model.

The Cluster is therefore different from Actor, Process, and Node
identities: it represents the logical field in which ephemeral members
participate.

## 64. Loss of Node Membership Does Not Prove Physical Process Death

**CLOSED**

Loss of Node membership removes the Processes hosted by that Node from
the usable capacity of that Cluster view.

It does not prove that those Processes have physically terminated.

A Node or its Processes may continue executing while isolated by a
network partition or membership disagreement.

Protos therefore keeps separate:

-   Membership
-   Reachability
-   Physical existence

A Cluster view must not treat Processes behind a lost/unreachable Node
as usable Cluster capacity unless the relevant membership/authority
rules permit it.

The behavior of isolated runtime domains is governed by the partition
and Cluster-authority rules.

## 65. Local Execution May Continue Without Cluster Authority

**CLOSED**

Loss of Cluster authority does not imply loss of local execution.

A partitioned or isolated Node may continue operations that do not
require Cluster authority, including purely local Actor execution, local
Futures, local I/O, and other valid local computation.

However, a runtime domain must not perform operations requiring Cluster
authority unless it can demonstrate that it currently possesses the
required authority.

Conceptually:

    Cluster authority lost
        |
        +-- local Actor execution may continue
        +-- local Process/Node work may continue
        |
        `-- authoritative Cluster decisions are disabled

Protos does not choose either extreme:

-   A partition does not automatically kill all local computation.
-   Every partition does not automatically behave as an independent
    authoritative Cluster.

This allows graceful degradation according to the runtime level actually
available.

## 66. Cluster Authority

**CLOSED**

Cluster authority is the exclusive capability to make authoritative
decisions within a Cluster authority scope.

For a particular authoritative decision scope, Protos requires
exclusivity: conflicting partitions must not both be valid authorities
for the same exclusive decision.

Protos does not prescribe the mechanism used to establish that
authority.

Possible mechanisms may include:

-   Quorum
-   Consensus
-   Leader election
-   Leases
-   Witnesses
-   External coordination services
-   Other mechanisms providing the required properties

Observation does not necessarily require exclusive authority.

Authoritative mutations or decisions may include, depending on scope:

-   Definitive membership changes
-   Placement ownership
-   Group membership management
-   Replacement decisions
-   Topology-changing operations

If a runtime domain cannot demonstrate authority for an operation that
requires it, it must refrain from performing that authoritative
operation.

Failure to prove authority does not automatically stop unrelated local
computation.

## 67. Cluster Authority Is Scoped

**CLOSED**

Cluster authority is scoped, not necessarily global.

Protos does not require one global "master" that serializes every
Cluster decision.

Different authority scopes may exist for different responsibilities.

Conceptually:

    Cluster membership authority
    Group G1 control authority
    Group G2 control authority
    Placement-domain authority
    other independent authority scopes

Exclusivity is required within an authority scope, not across unrelated
Cluster responsibilities.

The runtime may therefore distribute or shard control responsibilities
while preserving the rule that conflicting authorities cannot
simultaneously make the same exclusive decision.

Membership may require special treatment because other authority scopes
can depend on it, but the exact implementation remains open.

## 68. Controllers Are Ephemeral

**CLOSED**

Controllers are ephemeral.

Correctness must not depend on the lifetime of the particular controller
instance that currently exercises an authority.

If a controller disappears, another eligible controller may acquire the
corresponding authority and continue control operations.

The important durable concept is the authority and required control
state, not the controller instance.

Any control state required to transfer authority must survive or be
reconstructible independently of the controller instance that previously
held authority.

For example, Group desired cardinality, availability policy, or other
authoritative control information must not exist solely in irreplaceable
controller RAM if continued control requires that information.

The exact mechanism by which such state survives or is reconstructed
remains open.

Principle:

> The controller is cattle too.

## 69. Authoritative Control State Is Mechanism-Independent

**CLOSED**

Protos specifies the consistency and survivability properties required
of authoritative control state, not a mandatory storage or consensus
implementation.

Depending on the active runtime domain, the required mechanism may range
from ordinary local state to distributed coordination.

Conceptually:

    single Process
        -> local runtime state may be sufficient

    multiple Processes / Node
        -> Node-level coordination may be sufficient

    distributed Cluster
        -> stronger coordination may be required

Possible implementations may include replicated consensus state, a
durable store, leases, an external authority service, or a Protos-native
mechanism.

No specific technology such as Raft, etcd, or another consensus/store
implementation is part of Protos semantics at this stage.

The mechanism scales with the active runtime domain.

No distributed coordination mechanism is required when no distributed
authority exists.

This preserves pay-as-you-grow.

## 70. External Authority Mechanisms Do Not Define Protos Semantics

**CLOSED**

Protos may delegate the mechanism used to establish, demonstrate, or
persist authority to external infrastructure.

For example, an implementation may use:

-   Kubernetes Lease or API primitives
-   Nomad coordination facilities
-   Consul or another coordination service
-   A cloud coordination service
-   A Protos-native mechanism

However, external infrastructure does not define the meaning of Protos
authority.

Conceptually:

    authority mechanism
        may be external

    semantic authority
        belongs to Protos

An external orchestrator may help discover instances, establish leases,
persist control metadata, or provision capacity.

It does not thereby become the semantic definition of:

-   Actor
-   ActorRef
-   Actor Group
-   Protos Node
-   Protos Cluster
-   Group membership
-   Protos routing
-   Protos failure semantics
-   Protos authority

For example, a Kubernetes Deployment is not semantically a Protos
ActorGroup merely because it may provision workloads that host Group
members.

The same Protos program and runtime model should remain valid across
standalone execution, Kubernetes, Nomad, or future infrastructure
environments.

## Open Design Topics

The following topics have been identified but are not yet closed:

-   Failure-domain discovery and configuration
-   Exact HA policy API and syntax
-   Exact Group API and syntax
-   Group controller API
-   Group lifecycle
-   Group routing policy API
-   Advanced Group routing policies
-   Group broadcast and multicast semantics
-   Group membership transition semantics
-   Exact `spawn` API and syntax
-   Actor bootstrap representation
-   Exact SpawnOperation API
-   Exact SpawnOperation states
-   SpawnOperation timeout and cancellation semantics
-   Exact current-behavior installation/replacement API
-   Exact SendOperation API
-   Exact SendOperation states
-   Definition of what SendOperation `.value()` means
-   Delivery acknowledgement levels
-   Delivery guarantees
-   Retry semantics
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
-   Whether Task should become observable
-   Pub/sub
-   Routers and load balancing
-   Actor capacity policy
-   Process capacity provisioning policy
-   Node capacity provisioning policy
-   Capacity Demand API
-   Scale-up policy in infrastructure adapters/controllers
-   Scale-down policy in infrastructure adapters/controllers
-   Proactive capacity-demand signals
-   Draining Processes and Nodes
-   Infrastructure Controller integration
-   External infrastructure adapters such as Kubernetes or Nomad
-   Actor graceful shutdown
-   Actor stop semantics
-   Actor garbage collection
-   Monitoring API
-   Fatal versus non-fatal handler errors
-   Which errors terminate an Actor
-   Failure-authority API
-   Process failure detection
-   Node failure detection
-   Network partitions
-   Split-brain behavior
-   Cluster membership protocol
-   Cluster authority-scope model
-   Authority acquisition and transfer
-   Authority leases/election/consensus implementation
-   Authoritative control-state storage/reconstruction
-   Cluster authentication
-   Placement scoring algorithm
-   Placement stability and hysteresis
-   Placement policy priorities
-   Actor affinity and anti-affinity API
-   Hard placement constraints
-   Actor rebalancing
-   Actor migration
-   Actor persistence
-   Actor checkpointing
-   State recovery
-   State replication
-   Replicated Actor/service semantics
-   ActorRef routing
-   ActorRef persistence semantics, if any
-   Service discovery implementation
-   Physical-locality discovery
-   Cross-process same-host optimization
-   Shared-memory transport eligibility and lifecycle
-   Transport selection and switching
-   Message serialization format
-   Serialization versioning
-   Schema evolution
-   Non-transferable resource capabilities
-   Foreign-resource proxies
-   Java interoperability isolation
-   Java static mutable state
-   Native global state
-   Blocking foreign calls
-   Blocking-operation offload
-   Non-local return across Actor boundaries
-   Dynamic error handlers across Actor boundaries
-   Future ownership interaction with Actor lifecycle
-   Timers
-   Clock semantics
-   Waiting on multiple Futures
-   Select/race operations
-   CPU-bound Future monopolization
-   Resource limits and quotas
-   Runtime resource-pressure model
-   Actor resource-cost estimation and learning
-   ActorRef security and authorization
-   Remote authentication
-   Cluster configuration UX
-   Cluster lazy startup
-   Node lazy activation
-   Cluster identity after loss of all members
-   Relationship between logical Protos topology and physical
    infrastructure topology
-   Module implementation sharing
-   Behavior requirements before READY
-   Runtime metrics architecture
-   Scheduler/advisor interaction
-   Scheduler/capacity-demand interaction
-   Code identity for remote Actor bootstrap
-   Code availability and versioning across Nodes
-   Hot code update
-   NUMA-aware scheduling
-   Application-root versus process-root distinction in distributed
    deployments
