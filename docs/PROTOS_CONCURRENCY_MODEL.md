# Protos Concurrency Model v0.1

Language version: 0.1
Document revision: 02
Status: Draft
Last updated: 2026-09-02

# Protos Multithreading Design Ledger v1

## 1. General Principle

**CLOSED**

Protos aims to hide concurrency complexity in a way analogous to how garbage collection hides manual memory management.

Priorities:

- Ease of use
- Safety by default
- Performance
- Pay for what you use

A simple Protos program must not pay the cost of distributed runtime infrastructure unless it actually uses it.

A small command-line program such as `ls` should start quickly and should not require cluster membership, discovery, network listeners, external configuration, or heavyweight runtime services.

Guiding rule:

> If normal Protos code requires the programmer to reason about locks, atomics, memory barriers, ownership graphs, or memory ordering, the concurrency model has probably failed.

The runtime should absorb as much complexity as possible without sacrificing ordinary execution performance.

## 2. Fundamental Model

**CLOSED**

The fundamental programming model remains:

- Objects
- Slots
- Closures
- Delegation
- Message dispatch
- `this`

Actors do not introduce a second object model or a second dispatch system.

An Actor organizes ordinary Protos objects into an isolated domain containing:

- A private object graph
- Mutable state
- A current behavior object
- A mailbox
- Futures/tasks
- Lifecycle state

Principle:

> Objects, slots, closures, and dispatch are the molecule. Actors organize those molecules into isolated domains of state, execution, lifecycle, and parallelism.

The Actor itself is not simply another ordinary Protos object.

Its current behavior is an ordinary Protos object.

## 3. Future Versus Actor

**CLOSED**

Future and Actor solve different scaling problems.

> A Future scales with the amount of concurrent work.

> An Actor scales with the amount of parallelism and isolation.

Within one Actor, Futures behave conceptually like green threads or green tasks.

An Actor may contain very large numbers of Futures.

However, only one segment of Protos code belonging to an Actor executes at a time.

Therefore Futures inside the same Actor may interleave, but they do not execute Protos code simultaneously.

Different Actors may execute simultaneously on different CPU cores.

An Actor is therefore a logical thread in the language model.

It is not defined as one operating-system thread. Mapping Actors to operating-system threads or other carrier mechanisms is a runtime implementation decision.

## 4. Actor Isolation

**CLOSED**

There is no shared mutable Protos memory between Actors.

Ordinary mutable Protos references never cross an Actor boundary.

Therefore the normal Actor programming model does not require:

- Mutexes
- Locks
- Volatile variables
- Atomics
- Memory barriers

The runtime may internally use shared memory, concurrent queues, atomics, locks, copy-on-write, or other mechanisms as long as those mechanisms are not observable through Protos semantics.

## 5. Actor Turns and Reentrancy

**CLOSED**

An Actor executes at most one segment or turn of Protos code at a time.

A message handler or task retains exclusive execution within the Actor until it:

- Completes, or
- Reaches an explicit suspension point.

For example:

    data: socket.read().value()

If `value()` must wait, the current task is suspended and the Actor may execute other runnable work.

When the suspended task becomes runnable again, its continuation executes in a later turn.

Consequently, Actor-local mutable state may have changed across an explicit suspension point.

Between suspension points, Actor-local state is serialized and race-free with respect to other work in the same Actor.

The model is therefore sequential but reentrant at explicit suspension points.

## 6. I/O

**CLOSED**

Normal Protos I/O should be non-blocking relative to the Actor and should return a Future or another awaitable operation.

Suspension remains explicit through operations such as:

    data: socket.read().value()

If the result is already available, execution may continue immediately.

If the result is pending, the current task suspends and the Actor may execute other work.

Ordinary I/O must not transparently introduce hidden suspension points.

Principle:

> Looking at Protos code, the programmer should be able to identify where Actor reentrancy may occur.

Internally, an Actor may conceptually receive runnable work from a unified runtime event source containing:

- Mailbox messages
- I/O completions
- Timers
- Future resolutions

An I/O completion is not normally exposed as an ordinary message to the Actor's current behavior.

Instead, it makes the corresponding suspended task runnable.

The implementation may use mechanisms such as epoll, kqueue, io_uring, callbacks, fibers, virtual threads, or other facilities without changing language semantics.

## 7. Ordering and Fairness

**CLOSED**

Messages from the same sender to the same Actor preserve FIFO ordering.

Within one Future or task, normal sequential execution order applies.

No global ordering is guaranteed between unrelated sources of work, including:

- Different message senders
- I/O completions
- Timers
- Reactivated Futures
- Other independently runnable work

The runtime provides an abstract no-starvation guarantee among runnable work that yields control.

The language does not currently specify a particular round-robin algorithm, time quantum, or scheduler implementation.

A Protos computation that runs indefinitely without completing or reaching a suspension point may monopolize its Actor.

Arbitrary preemption of Protos execution is not currently part of the model.

## 8. Actor Creation

**CLOSED**

A newly created Actor starts clean with respect to the mutable state of its creator.

It does not inherit:

- The creator's live execution context
- `this`
- `moduleContext`
- Return homes
- Dynamic error handlers
- Pending Futures
- Mutable object graph

Only explicitly supplied initialization values cross the Actor boundary, using normal Actor pass-by-value semantics.

A normal Closure cannot silently carry actor-local lexical captures into another Actor.

Actor bootstrap code may use closure-like syntax or code descriptors, but cross-Actor creation must not alter the existing semantics that Closures capture lexical contexts by reference.

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

An Actor processes no external messages until initialization completes successfully.

Messages arriving while the Actor is INITIALIZING may be queued, but they are not dispatched until the Actor reaches READY.

If initialization fails with an unhandled error, the Actor never reaches READY.

Normal completion of initialization does not terminate the Actor.

Instead, successful initialization transitions the Actor to READY and its implicit event loop begins.

## 10. Implicit Event Loop

**CLOSED**

The Actor event loop is runtime machinery.

Normal Protos Actor code does not require an explicit construct such as:

    while (receive()) {
        ...
    }

The programmer defines behavior.

The runtime dispatches Actor turns automatically.

A lower-level receive/event API may be considered later, but it is not the normal Actor programming model.

## 11. Current Behavior

**CLOSED**

Each Actor has a replaceable current behavior.

The current behavior is an ordinary Protos object.

External messages are dispatched against the behavior that is current when the corresponding Actor turn begins.

Changing the current behavior affects subsequent turns.

It does not alter the receiver or semantics of an activation that is already running.

Messages already queued in the mailbox are therefore dispatched using the behavior current when their turn begins, not necessarily the behavior that existed when the message was sent.

An ActorRef identifies the Actor, not its current behavior.

The exact API for installing or replacing the current behavior remains open.

## 12. Actor Message Dispatch

**CLOSED**

An external Actor message conceptually consists of:

    selector + arguments

The message is dispatched against the Actor's current behavior using the ordinary Protos dispatch rules.

Conceptually:

    worker.ask("resize", image, 800)

causes dispatch equivalent to:

    currentBehavior.resize(image, 800)

The same normal Protos rules apply:

- Slot lookup
- Delegation
- `this`
- Closure activation
- Message-not-understood behavior
- Normal error semantics

No second Actor-specific method or message dispatch system exists.

During handler execution, `this` is the current behavior object according to normal Protos activation semantics.

## 13. send()

**CLOSED**

`send()` represents one-way Actor communication.

It returns a local identity-bearing communication operation object, provisionally called `SendOperation`.

A SendOperation represents the logical send and may expose information or operations concerning:

- Status
- Progress
- Waiting
- Cancellation
- Retry
- Attempts
- Logical message identity
- Destination
- Last error
- Delivery uncertainty

A SendOperation is communication-specific and is therefore distinct from a generic Future.

Retry is explicit rather than an invisible default because retrying an uncertain delivery may create duplicates.

Protos does not promise exactly-once delivery by default.

The exact SendOperation API and status set remain open.

## 14. ask()

**CLOSED**

`ask()` represents request/reply communication.

It returns a Future.

Conceptually:

    future: worker.ask("calculate", input)
    result: future.value()

The runtime automatically manages the ephemeral reply capability and any required:

- Correlation
- Routing
- Reply delivery

The normal final result of the Actor handler becomes the reply value.

One `ask()` produces one logical response.

Streaming or multi-response communication will use a separate abstraction rather than stretching `ask()` into a streaming protocol.

Timeout and cancellation follow the general Future and communication rules.

A timeout while waiting does not imply that remote work did not execute.

## 15. send() and ask() Share Dispatch

**CLOSED**

`send()` and `ask()` use the same Actor message dispatch mechanism.

For `send()`:

    message
        |
        v
    normal behavior dispatch
        |
        v
    handler result ignored

For `ask()`:

    message
        |
        v
    same normal behavior dispatch
        |
        v
    handler result resolves caller Future

There are no separate send handlers and ask handlers.

## 16. Pass-by-Value Between Actors

**CLOSED**

Messages between Actors have pass-by-value semantics.

No ordinary Protos reference crosses an Actor boundary.

Transferability currently follows these rules:

- Number: transferable by value
- String: transferable by value
- Boolean: transferable by value
- null: transferable by value
- Ordinary mutable object: transferable as a logical value copy
- Array: transferable as a logical value copy
- Cyclic object graph: transferable while preserving graph structure and aliasing conceptually
- ActorRef: transferable as a special communication capability
- Closure: not transferable
- Future: not transferable
- ExecutionContext: not transferable
- Socket: not transferable
- Open file: not transferable
- Native resource: not transferable
- Java object: not transferable by default

ActorRef is deliberately transferable because it provides communication capability rather than direct access to another Actor's mutable heap.

Closures are not transferable because they capture actor-local lexical execution contexts by reference.

The destination Actor executes code that it already owns rather than receiving arbitrary executable closures carrying another Actor's lexical environment.

## 17. Message Snapshot Time

**CLOSED**

A message captures its logical value snapshot at `send()` time.

For example:

    x: (value: 1)

    B.send("foo", x)

    x.value = 2

Actor B conceptually receives the state in which:

    value = 1

even if B does not process the message until much later.

This snapshot rule is semantic.

It does not prescribe when or how the runtime physically copies memory.

## 18. Message Transfer Optimizations

**CLOSED**

Pass-by-value is an observable semantic rule, not a requirement to physically duplicate every byte immediately.

The runtime may use optimizations including:

- Copy-on-write
- Immutable physical sharing
- Shared backing storage
- Shared memory
- Zero-copy
- Page remapping
- Serialization
- Streaming
- Scatter/gather I/O

provided that the program observes exactly the semantics of a snapshot taken at send time and cannot observe shared mutable identity between Actors.

## 19. Buffers

**CLOSED**

Buffers retain the same pass-by-value and snapshot semantics as other transferable mutable values.

Protos does not initially introduce a special shared mutable Buffer exception.

It also does not initially require Rust-like explicit move or borrow semantics merely to obtain efficient Buffer transfer.

The runtime may specialize Buffer storage aggressively using techniques such as:

- Copy-on-write
- Zero-copy
- Shared immutable backing
- Scatter/gather
- Streaming

If large Buffer transfer patterns are expensive, the runtime advisor may detect them and suggest architectural changes.

Principle:

> First optimize automatically what is semantically invisible; require programmer-visible architectural changes only when they materially matter.

## 20. End-to-End Backpressure

**CLOSED**

Backpressure applies to the complete delivery path, not only to the destination mailbox.

Conceptually:

    sender Actor
        |
        v
    local outbound queue
        |
        v
    transport/channel
        |
        v
    remote ingress
        |
        v
    target mailbox

No intermediate queue may grow indefinitely in order to hide a slow final consumer.

Pressure must be capable of propagating back toward the originating SendOperation.

This is conceptually similar to TCP flow control, but operates at the Actor/message semantic level rather than being tied to a particular network transport.

The underlying transport may be:

- In-process
- Shared memory
- IPC
- TCP
- QUIC
- Another transport

without changing the Actor communication semantics.

## 21. Mailbox Bounds

**CLOSED**

Every Actor mailbox has an effective finite bound.

The bound may be:

- Runtime-managed
- Explicitly configured
- Adaptive

but a mailbox is never conceptually an unlimited sink.

When the delivery path cannot currently accept additional messages, `send()` enters backpressure through its SendOperation.

It does not block an operating-system thread and does not silently discard the message.

A caller may explicitly choose to wait for progress through the SendOperation.

Special explicit policies such as dropping, latest-only delivery, bounded loss, batching, or telemetry-oriented behavior may be considered later.

They are not the default.

## 22. Timeouts and Deadlines

**CLOSED**

Awaitable operations may support a wait timeout.

A wait timeout affects waiting.

It does not automatically cancel the underlying operation.

Conceptually:

    future.value(5s)

means:

> Stop waiting after five seconds if the Future is still unresolved.

It does not mean:

> Guarantee that the underlying work stopped after five seconds.

Communication operations may additionally support delivery deadlines or cancellation where appropriate.

A timeout must never be interpreted as proof that an operation did not occur.

For remote communication, uncertainty may result in a state such as `deliveryUnknown`.

The exact APIs remain open.

## 23. Cancellation

**CLOSED**

Cancellation is cooperative.

Protos does not arbitrarily interrupt Actor code in the middle of an instruction by injecting asynchronous exceptions.

Cancellation requests are observed at safe points such as:

- Suspension points
- Runtime operations
- Explicit or implicit cooperative checkpoints

Cancelling a Future requests cancellation of its work.

Cancelling a SendOperation attempts to prevent further delivery while that remains safe.

If a message is still in a local queue, cancellation may be able to remove it.

If the message has crossed a boundary where delivery can no longer be determined safely, the result may become `deliveryUnknown`.

If the destination has already accepted the message, cancellation cannot unsend it.

Principle:

> Cancellation never reverses effects that have already occurred.

## 24. Structured Concurrency

**CLOSED**

The existing structured-concurrency semantics for Futures remain.

Asynchronous child work created inside an execution context is owned by that context by default unless explicitly detached.

Actor creation does not automatically establish the same ownership relationship.

Actors may live for days, months, or longer than the Actor that originally created them.

Structured concurrency therefore governs Futures within an Actor, not Actor lifetime in general.

## 25. Parent Actor Versus Supervisor

**CLOSED**

Actor creation and Actor supervision are separate relationships.

`parentActor` identifies the Actor that created the current Actor.

It represents genealogy/origin and provides an initial communication capability toward the creator.

It does not imply ownership or automatic lifecycle propagation.

`supervisorActor` identifies the entity responsible for lifecycle policy.

If Actor A creates Actor B and A later terminates permanently, B does not automatically terminate merely because A was its creator.

B's parent reference may continue to identify the logical Actor A and communication with it may report that it is terminated or otherwise unavailable.

Lifecycle consequences are defined by supervision, not parenthood.

## 26. Supervision

**CLOSED**

Every Actor has supervision.

A default or root supervisor exists when no explicit supervisor is selected.

When an Actor suffers an unhandled fatal failure, its supervisor receives structured failure information and applies lifecycle policy.

Possible policies may include:

- Restart
- Replace
- Stop
- Escalate
- Ignore

The exact policy API remains open.

Restart creates a fresh Actor generation and runs initialization again.

The runtime does not automatically reuse the failed Actor's private mutable heap because that state may be inconsistent.

## 27. Logical Actor Identity Across Restart

**CLOSED**

A supervised restart preserves logical Actor identity and existing ActorRefs.

Conceptually:

    ActorRef X
        |
        v
    generation 41
        |
        X crashes
        |
        v
    generation 42

Existing users of ActorRef X continue to address the same logical Actor.

Internally, the runtime distinguishes:

- Logical Actor identity
- Concrete Actor generation or incarnation

Generation identity does not need to be part of normal application programming.

## 28. Messages Across Actor Failure

**CLOSED**

Messages that have already been processed retain their effects.

Messages that have definitely not been accepted remain governed by normal SendOperation semantics.

Messages that were accepted but remained unprocessed when an Actor generation crashed are not automatically reinjected into the replacement generation by default.

Automatic reinjection could duplicate effects if the previous generation partially processed the operation before failure.

Stronger guarantees require explicit mechanisms such as:

- Idempotency
- Acknowledgements
- Persistence
- Deduplication
- Transactional protocols

These mechanisms remain to be designed.

## 29. Monitoring Versus Supervision

**CLOSED**

Supervision and monitoring are distinct.

Supervision has authority over Actor lifecycle.

Monitoring or watching observes Actor lifecycle without controlling it.

SendOperation reports the state of a particular communication operation.

Potential lifecycle observations include:

- Started
- Ready
- Failed
- Restarting
- Restarted
- Terminated
- Unreachable

The exact monitoring API remains open.

For distributed Actors:

    FAILED != UNREACHABLE

A network partition or routing failure cannot by itself prove that a remote Actor has terminated.

## 30. Runtime Health and Watchdog

**CLOSED**

The runtime provides inexpensive always-on health information.

The mandatory fast path must remain O(1), non-blocking, and free from global coordination.

Always-on information may include:

- Actor lifecycle state
- Approximate progress epoch
- Mailbox depth
- Failure count
- Supervisor/root ownership
- A small number of counters

More expensive analysis should use sampling or optional instrumentation.

Examples include:

- Detailed latency analysis
- Hotspot detection
- Long-turn profiling
- Future-stall analysis
- Communication-pattern analysis

The runtime must not silently repair arbitrary mutable Actor state.

Strong recovery replaces the isolated execution domain and runs initialization again.

## 31. Runtime Advisor

**CLOSED AS DIRECTION**

The runtime may provide an advisor capable of detecting problematic patterns such as:

- Long-running Actor turns
- Mailbox saturation
- Stuck Futures
- Resource saturation
- CPU-heavy work inside an I/O-oriented Actor
- Excessive large-value transfers
- Poor Actor placement
- Communication hotspots

The advisor may provide stronger instrumentation in development environments while production can retain cheap health monitoring and self-healing with expensive profiling disabled or sampled.

When an optimization is semantically invisible, the runtime should prefer applying it automatically.

For example, a large transfer may automatically use copy-on-write or zero-copy.

If repeated transfers remain architecturally expensive, the advisor may recommend changing Actor boundaries or placement.

## 32. Root Actor

**CLOSED**

Every Protos process begins with a Root Actor.

A trivial program may conceptually consist only of:

    Process
        |
        v
    Root Actor

The Root Actor owns the initial program state, including ordinary objects, module state, configuration, and Futures.

Within the Root Actor, ordinary state access uses normal Protos semantics and does not incur Actor message-passing overhead.

Creating additional Actors explicitly introduces new isolation and parallelism boundaries.

If a program never creates another Actor, the runtime or JIT may optimize away unnecessary Actor infrastructure.

## 33. Global State

**CLOSED**

Protos does not introduce a special shared mutable global-state exception.

State that is conceptually global to a simple program can initially remain ordinary mutable state owned by the Root Actor.

If additional Actors require access to that state, they communicate with the Root Actor through Actor communication.

As an application grows, responsibilities may be moved into specialized Actors such as:

- Cache Actors
- Database Actors or pools
- Metrics Actors
- Session Actors
- Other domain-specific Actors

Principle:

> Global state starts as Root Actor local state and becomes distributed only when the application introduces parallelism or isolation boundaries.

## 34. Actor Module State

**CLOSED**

Each Actor has its own logical module registry and module contexts.

Actors do not inherit mutable module contexts from their creator.

If two Actors import the same module, mutable module-level state is logically separate in each Actor.

The runtime may physically share immutable implementation artifacts such as:

- Compiled code
- Immutable metadata
- Frozen core objects
- Shared prelude implementation

provided that the sharing is not observable as shared mutable Protos state.

A module singleton is therefore logically per Actor unless a future explicit distributed abstraction says otherwise.

## 35. Scope Roots

**CLOSED**

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

ProcessRoot, NodeRoot, and ClusterRoot should normally be runtime services or capabilities rather than ordinary Actors.

This avoids making essential runtime infrastructure dependent on a potentially saturated mailbox or hung Protos Actor.

Conceptual responsibilities include:

ClusterRoot:

- Cluster membership
- Discovery
- Placement
- Cluster health

NodeRoot:

- Node resources
- Local processes
- Node health
- Local transport

ProcessRoot:

- Process lifecycle
- Actor management
- Root supervision
- Runtime services

RootActor:

- Application-level mutable state
- Application code
- Normal Protos execution

A standalone process does not need active distributed cluster infrastructure.

NodeRoot and ClusterRoot facilities should be lazy or absent when they are not needed.

## 36. Actor Runtime Context References

**CLOSED AS DIRECTION**

An Actor may have access to a minimal set of implicit runtime references or capabilities without inheriting mutable state.

These may conceptually include:

- `selfActor`
- `parentActor`
- `rootActor`
- `supervisorActor`
- Process root capability
- Node root capability
- Cluster root capability

The exact names and APIs remain open.

These values are capabilities or references to execution/runtime domains.

They do not provide direct shared-memory access to another Actor's mutable object graph.

Creator and supervisor remain distinct concepts.

## 37. Process Boundary

**CLOSED**

Process is a stronger isolation boundary than Actor.

It provides an address-space and failure boundary.

Actor-to-Actor and Process-to-Process communication use the same fundamental pass-by-value message semantics.

Two Protos processes on the same machine may communicate using optimized mechanisms such as:

- Shared memory
- mmap
- IPC
- Local sockets

Different machines may use network transports.

The transport must not change observable message value semantics or expose cross-process mutable references.

Failure of a remote process or communication channel does not automatically terminate the local process.

## 38. Node

**CLOSED**

A Node is a logical member of the Protos distributed runtime capable of hosting one or more Protos processes.

Conceptually:

    Node
        |
        +-- Process
        |
        +-- Process
        |
        +-- Process

A Node commonly corresponds to a machine, VM, or container host, but this is not a strict language requirement.

Node is primarily a scope for:

- Placement
- Resources
- Membership
- Host-level health
- Local transport
- Host-level failure

Multiple administratively independent Protos Nodes could theoretically exist on one physical machine.

If a Node becomes unreachable, remote Actors on that Node become UNKNOWN or UNREACHABLE before the runtime can legitimately conclude that they are dead.

Network partition must not be confused with confirmed Actor failure.

## 39. Cluster

**DIRECTION CLOSED, DETAILS OPEN**

The cluster provides distributed scope across Nodes.

It is expected eventually to support concepts such as:

- Membership
- Discovery
- Routing
- Placement
- Node health
- Resource awareness
- Failure detection
- Recovery
- Rebalancing

Important details remain open, including:

- Node failure semantics
- Network partitions
- Split-brain handling
- Membership protocol
- Placement
- Rebalancing
- Actor migration
- Persistent Actor state
- Recovery policy
- Consensus requirements

Cluster functionality must remain lazy and must not impose distributed-runtime costs on ordinary standalone programs.

## 40. ActorRef

**CLOSED**

ActorRef is an opaque logical communication capability.

It is not:

- A pointer
- A host and port
- A URL
- A physical process identifier
- A Node address

ActorRef identifies a logical Actor independently of its current physical location.

The runtime may internally maintain routing metadata, generation information, and location information.

Existing ActorRefs may continue to work across supervised restart and, where supported, Actor movement.

Possessing an ActorRef does not imply that the target Actor is currently reachable.

## 41. Actor Discovery

**CLOSED AS DIRECTION**

Logical Actor identity and service naming are separate.

ActorRef represents logical identity/capability.

A service name represents a resolvable alias.

Conceptually:

    service name
        |
        v
    ActorRef or Actor group

Discovery must be pluggable.

The Protos language semantics must not hard-code infrastructure such as:

- DNS
- Consul
- Kubernetes
- A specific service registry

Those mechanisms may implement discovery without becoming part of the language model.

## 42. Automatic Actor Placement and Capacity Scaling

**CLOSED**

The normal programmer creates a logical Actor without selecting its physical
location.

Conceptually:

    worker: spawn(Worker, args).value()

means:

> Create this logical Actor.

It does not normally mean:

> Create this Actor in this Process, on this Node, or on this physical host.

The runtime scheduler determines placement automatically within the currently
active execution domain and currently available capacity.

Conceptually:

- If only the current Process is active, the Actor is placed in that Process.
- If a Node coordinates multiple available Processes, the Actor may be placed
  in any suitable Process within that Node.
- If a Cluster coordinates multiple Nodes and Processes, the Actor may be
  placed in any suitable location within that Cluster.

The same Actor creation code therefore scales according to the runtime domain
that is already active.

Normal `spawn()` does not itself create or activate higher runtime domains or
new infrastructure capacity.

In particular, `spawn()` does not by itself:

- start another Process;
- create or activate a Node;
- create or join a Cluster;
- provision a machine, VM, container, or infrastructure workload.

Scheduling and scaling are separate responsibilities.

> Scheduling decides where to place work within existing capacity.

> Scaling decides how much capacity exists.

The Actor scheduler uses capacity that is already available.

Separate runtime or infrastructure mechanisms may increase or decrease that
capacity.

For example:

- a Process scaler may create or remove Protos Processes;
- a Node scaler or infrastructure manager may create or remove Protos Nodes;
- Kubernetes, Nomad, or another external orchestrator may provision or remove
  infrastructure workload units;
- Cluster membership determines which active Nodes participate in distributed
  placement.

The scheduler may consider factors such as:

- CPU and runnable-work pressure;
- memory availability and pressure;
- communication locality;
- Actor affinity and anti-affinity;
- resource requirements;
- runtime health;
- network cost;
- observed communication patterns;
- failure domains;
- placement stability.

Physical topology remains observable for diagnostics, administration, and
optimization, but it is not normally part of application logic or Actor
identity.

Advanced hard constraints or soft placement hints may exist for exceptional
cases such as:

- requirement for a GPU or another special resource;
- requirement for a non-transferable local resource;
- requirement to remain inside a particular isolation boundary;
- affinity or anti-affinity requirements;
- high-availability placement requirements.

Such constraints are secondary mechanisms rather than mandatory arguments to
normal Actor creation.

Scale-up, scale-down, draining, Actor migration, rebalancing, and the exact
interaction with external infrastructure managers remain open.

## 43. Spawn Backpressure

**CLOSED**

If the scheduler cannot admit a new Actor within the currently available
capacity and placement policies, Actor creation applies backpressure rather
than failing immediately or forcing unlimited oversubscription.

Conceptually:

    spawn(...)
        -> SpawnOperation
        -> PENDING
        -> capacity becomes available
        -> Actor initialization
        -> READY

A pending SpawnOperation may be waited on, cancelled, or subject to a timeout.

Actor capacity does not imply one CPU or other fixed resource reservation per
Actor. Many Actors may share the same Process.

Scheduling and scaling remain separate concerns:

- the scheduler places Actors using capacity that already exists;
- a scaler may react to pending demand or resource pressure by provisioning
  additional capacity;
- spawn itself does not create Processes, Nodes, or Clusters.


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

External infrastructure managers such as Kubernetes may provision, place,
restart, or scale the physical infrastructure in which Protos runtime domains
execute, but they do not replace Protos runtime membership, ActorRef routing,
health, or logical coordination.

For example, a Kubernetes deployment may choose to map each Pod to a Protos
Node containing a single Protos Process. Kubernetes may place two such Pods on
the same physical machine or on different machines without changing their
Protos topology.

Physical co-location must therefore never be inferred solely from Protos
logical topology.

Optimizations that depend on physical locality, such as shared-memory
transport, must only be used when the runtime can establish that the required
locality or capability actually exists.

The hierarchy is pay-for-what-you-use: levels that are unnecessary for the
active execution domain need not incur distributed-runtime machinery.

## 45. Dynamic Actor Capacity and Placement

**CLOSED**

Actor capacity is primarily dynamic rather than based on fixed declarative
resource reservations.

Normal Actors are not expected to declare Kubernetes-like CPU or memory
requests.

Placement occurs conceptually in two stages.

First, hard feasibility filtering removes destinations where the Actor cannot
be admitted. Examples may include:

- unavailable or draining Process;
- hard memory or resource limit;
- incompatible runtime or code;
- required special resource;
- isolation or placement constraint.

Second, the scheduler scores feasible destinations using dynamic runtime
information such as:

- CPU and runnable-work pressure;
- memory pressure;
- scheduler latency;
- mailbox pressure;
- communication locality;
- affinity;
- resource locality;
- failure domains.

Manual resource requirements and placement constraints may exist for cases
where they are genuinely required, but are exceptional rather than the normal
Actor programming model.

The runtime may learn from observed Actor behaviour instead of requiring the
programmer to predict resource consumption in advance.

## 46. Adaptive Admission Control

**CLOSED**

The ability to technically fit another Actor and the desirability of admitting
more work are distinct concepts.

Soft resource pressure normally affects placement scoring and provides a
signal for proactive scaling.

When pressure becomes sufficiently severe, the runtime may temporarily stop
admitting additional Actors, causing their SpawnOperations to remain pending
and applying backpressure.

Admission decisions are adaptive and multidimensional. They may consider
runtime information such as:

- runnable work;
- CPU pressure;
- memory pressure;
- scheduler latency;
- mailbox growth;
- I/O suspension;
- allocation pressure;
- communication pressure.

No fixed CPU, memory, or utilization threshold is part of the language
semantics.

For example, high CPU utilization alone does not necessarily mean that a
Process is overloaded. A runtime with healthy scheduling latency and many
Actors suspended on I/O may still be able to admit additional Actors.

Scaling should not wait until admission is refused.

Increasing resource pressure may proactively signal the scaler so that new
capacity can be provisioned before SpawnOperations need to be backpressured.

The runtime therefore separates:

    placement pressure
    scaling pressure
    admission pressure

while allowing all three to use common runtime observations.


## 47. Multi-Objective Placement

**CLOSED**

Protos does not define a universal PACK or SPREAD placement strategy.

Placement is multi-objective and balances several distinct goals.

Performance and scalability may favor:

- distributing runnable CPU work;
- exploiting additional execution capacity;
- reducing scheduler pressure;
- preserving communication locality;
- exploiting cache, memory, NUMA, or transport locality where known.

Availability and resilience may favor:

- separating failure domains;
- anti-affinity between redundant components;
- reducing failure blast radius;
- preserving required redundancy.

Efficiency may favor:

- consolidation;
- improved resource utilization;
- leaving capacity that can be scaled down.

Stability favors avoiding unnecessary placement changes.

PACK and SPREAD are therefore consequences of workload characteristics and
policy rather than fundamental Protos scheduling policies.

Actors that communicate heavily may be placed together when locality is more
valuable, while independent CPU-intensive Actors may be spread to exploit
parallel execution.

High-availability requirements may intentionally override the placement that
would otherwise be optimal for performance.

The scheduler should not continuously chase a theoretically optimal
placement. Placement stability has value, and rebalancing should occur only
when the expected benefit sufficiently exceeds its cost.

Placement may use hard constraints and soft affinity or anti-affinity hints
when application intent cannot be inferred safely.


## 48. Failure Domains

**OPEN**

Proposed direction:

Protos should model failure domains explicitly but generically.

Process is intrinsically a failure domain because failure of a Process affects
the Actors hosted by that Process.

Additional physical or infrastructure failure domains may include concepts
such as:

- host;
- rack;
- availability zone;
- site;
- infrastructure-specific domains.

Such domains may be discovered from the environment or supplied by
configuration.

Logical Protos topology must not imply physical failure independence.

For example, two Protos Nodes may execute on the same physical host. Unless
the runtime knows otherwise, placing redundant Actors on those Nodes cannot be
claimed to provide host-failure tolerance.

Unknown topology means unknown failure independence, not independent failure.

The exact failure-domain model and its relationship with HA placement remain
OPEN.

## Open Design Topics

The following topics have been identified but are not yet closed:

- Failure-domain model
- Failure-domain discovery and configuration
- HA placement requirements and policies
- Exact `spawn` API and syntax
- Actor bootstrap representation
- Exact SpawnOperation API
- Exact SpawnOperation states
- SpawnOperation timeout and cancellation semantics
- Exact current-behavior installation/replacement API
- Exact SendOperation API
- Exact SendOperation states
- Definition of what SendOperation `.value()` means
- Delivery acknowledgement levels
- Delivery guarantees
- Retry semantics
- Message IDs and attempt IDs
- Deduplication
- Idempotency support
- Persistent messaging
- Special mailbox policies
- Drop policies
- Latest-only policies
- Batching
- Streaming
- Pub/sub
- Actor pools and groups
- Routers and load balancing
- Actor autoscaling
- Process scaling
- Node scaling
- Scale-up policy
- Scale-down policy
- Proactive scaling signals
- Draining Processes and Nodes
- Interaction between Protos scaling and external infrastructure managers
- External infrastructure adapters such as Kubernetes or Nomad
- Actor graceful shutdown
- Actor stop semantics
- Actor garbage collection
- Monitoring API
- Fatal versus non-fatal handler errors
- Which errors terminate an Actor
- Node failure
- Process failure handling
- Network partitions
- Split-brain behavior
- Cluster membership protocol
- Cluster authentication
- Placement scoring algorithm
- Placement stability and hysteresis
- Placement policy priorities
- Actor affinity and anti-affinity API
- Hard placement constraints
- Actor rebalancing
- Actor migration
- Actor persistence
- Actor checkpointing
- State recovery
- ActorRef routing
- ActorRef persistence
- Service discovery implementation
- Physical-locality discovery
- Cross-process same-host optimization
- Shared-memory transport eligibility and lifecycle
- Transport selection and switching
- Message serialization format
- Serialization versioning
- Schema evolution
- Non-transferable resource capabilities
- Foreign-resource proxies
- Java interoperability isolation
- Java static mutable state
- Native global state
- Blocking foreign calls
- Blocking-operation offload
- Non-local return across Actor boundaries
- Dynamic error handlers across Actor boundaries
- Future ownership interaction with Actor lifecycle
- Timers
- Clock semantics
- Waiting on multiple Futures
- Select/race operations
- CPU-bound Future monopolization
- Resource limits and quotas
- Runtime resource-pressure model
- Actor resource-cost estimation and learning
- ActorRef security and authorization
- Remote authentication
- Cluster configuration UX
- Cluster lazy startup
- Node lazy activation
- Relationship between logical Protos topology and physical infrastructure topology
- Module implementation sharing
- Behavior requirements before READY
- Runtime metrics architecture
- Scheduler/advisor interaction
- Scheduler/scaler interaction
- Code identity for remote Actor bootstrap
- Code availability and versioning across Nodes
- Hot code update
- NUMA-aware scheduling
- Application-root versus process-root distinction in distributed deployments