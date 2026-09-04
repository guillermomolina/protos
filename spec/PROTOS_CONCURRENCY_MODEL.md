# Protos Concurrency Model v0.1

Language version: 0.1
Document revision: 324
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

The normative I/O capability, cancellation, lifecycle, text, filesystem, and Process-standard-I/O semantics are defined in `PROTOS_IO_MODEL.md`.

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

**CLOSED**

Buffers retain the same pass-by-value and snapshot semantics as other
transferable mutable values.

Protos does not initially introduce a special arbitrary shared mutable
Buffer exception.

It also does not initially require Rust-like explicit move or borrow
semantics merely to obtain efficient Buffer transfer.

The runtime may specialize Buffer storage aggressively using
copy-on-write, zero-copy, shared immutable backing, scatter/gather, or
streaming.

The isolated parallel-execution facility may additionally support
exclusive mutable partitioning of a Buffer or other suitable value. Such
partitioning does not create multiple simultaneous mutable aliases to the
same logical region: each writable logical region has at most one
parallel owner at a time, even if several disjoint regions share one
physical backing allocation.

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

## 20A. Same-Host and Shared-Memory Transport Are Semantically Invisible

**CLOSED**

Core v0.1 does not define a separate communication semantic universe for Actors
that happen to reside in different Processes on the same physical host.

Same-host placement may permit optimized transport, including shared memory,
memory mapping, page remapping, zero-copy representations, shared immutable
backing, local IPC, or equivalent mechanisms. These are physical transport
choices only.

The observable contract remains the ordinary Actor communication contract:

```text
sender value graph
    -> logical snapshot at the normative snapshot point
    -> ordinary transferability validation
    -> routing/admission/acceptance
    -> destination-local value graph/capability view
```

A same-host optimization must therefore preserve all existing semantics,
including:

- no shared mutable Protos identity between Actors;
- the same logical message snapshot;
- the same graph aliasing/cycle preservation rules;
- the same transferability and `NonTransferableValue` behavior;
- the same sender identity and FIFO guarantees;
- the same backpressure and acceptance boundaries;
- the same cancellation, uncertainty, failure, and Actor-death behavior;
- the same ActorRef/GroupRef capability semantics;
- the same message/reply publication rules.

Physical shared-memory eligibility is not a portable property of a value. Core
defines no `isSharedMemoryTransferable`, pinning API, locality predicate, backing
allocation identity, page identity, zero-copy flag, or same-host transport
requirement.

The runtime may choose shared-memory transport only when it can preserve the
ordinary logical transfer semantics. If it cannot do so for a particular value,
resource, topology, runtime state, or security boundary, it must use another
representation or transport rather than weakening the semantics.

The lifecycle of physical shared-memory artifacts is likewise not a Core
semantic surface. Segment creation, mapping, reference counting, reclamation,
unmapping, handle duplication, file-descriptor passing, page lifetime, cache
coherence, and equivalent implementation machinery must not be observable as
additional Protos identities, capabilities, or lifetime guarantees.

In particular, destination lifetime must not depend on keeping the sender's
mutable object alive, and sender mutation after the snapshot must not become
observable merely because both sides still reference one physical backing
allocation internally.

Transport switching is also permitted. A runtime may move between in-process,
shared-memory, IPC, or network transport during the lifetime of runtime entities
when every observable Actor semantic remains unchanged. A switch must not reset
sender FIFO, duplicate accepted operations, erase uncertainty, change identity,
or alter snapshot semantics.

This section closes the former open ledger items `Cross-process same-host
optimization` and `Shared-memory transport eligibility and lifecycle`. Exact
transport implementation and selection policy remain implementation concerns.

## 20B. Transport Selection and Switching Are Runtime Policy

**CLOSED**

Core v0.1 exposes no portable API or observable state for choosing, pinning,
querying, or constraining the physical transport used for Actor communication.

Transport selection is runtime policy. An implementation may select among
in-process queues, shared memory, local IPC, TCP, QUIC, or another transport
mechanism according to locality, topology, security, resource pressure,
capability, performance, or other implementation criteria.

The runtime may also switch transport while a logical ActorRef, GroupRef,
SendOperation, request Future, or other communication relationship remains live.

Selection or switching is conforming only when the existing communication
semantics remain unchanged. In particular it must not:

- alter ActorRef or GroupRef identity;
- reset or weaken same-sender FIFO ordering;
- duplicate or replay an already accepted operation;
- change the logical message snapshot;
- change transferability or graph-copy semantics;
- create shared mutable Protos identity;
- change acceptance/backpressure boundaries;
- erase or manufacture delivery uncertainty;
- change cancellation or failure outcomes;
- silently retry because one transport failed;
- change the authority carried by a communication capability;
- make physical locality, transport choice, or switching itself observable as a
  new Core value or event.

A transport failure may of course contribute to an already-defined communication
failure or uncertainty outcome according to the ordinary communication rules.
The runtime must not expose implementation-specific transport distinctions as
new Core outcomes unless another normative facility explicitly defines them.

Core therefore defines no `transport()`, `preferredTransport`,
`requireSharedMemory`, `sameHost`, `pinTransport`, or equivalent portable
communication control surface.

Administrative diagnostics may report transport choices outside portable Core.
Such diagnostics do not change language semantics and do not become valid input
to portable program logic merely because an implementation exposes them.

Message serialization format, serialization versioning, and schema evolution
remain separate open topics because they concern the representation contract
needed when a transport serializes values, not which transport is selected.

This closes the former open ledger item `Transport selection and switching`.

## 20C. Physical Locality Discovery Is Runtime Machinery

**CLOSED**

Core v0.1 does not expose physical-locality discovery as a portable language
facility.

A runtime may determine or infer physical relationships needed for internal
optimization or administration, including whether two runtime entities are:

- in the same operating-system process;
- on the same host;
- on different hosts;
- connected through shared-memory-capable infrastructure;
- associated with particular CPU packages, NUMA domains, racks, zones, regions,
  networks, or other physical/infrastructure locality domains.

How that information is discovered is implementation machinery. The runtime may
use operating-system information, deployment metadata, orchestrator APIs,
network topology, runtime registration, static configuration, probes, or other
mechanisms.

Physical-locality knowledge does not itself create a new Protos semantic
relationship. In particular, discovering that two entities are physically close
must not:

- merge Actor, Process, Node, Group, or Cluster identity;
- make an otherwise non-transferable value transferable;
- create shared mutable Protos identity;
- grant communication, resource, lifecycle, placement, or administrative
  authority;
- alter message snapshot, ordering, acceptance, failure, cancellation, or
  uncertainty semantics;
- imply that a shared-memory or other optimized transport must be selected;
- create a portable locality guarantee for future operations.

Likewise, failure to discover or retain locality information is not a Core
semantic failure. The runtime may fall back to any other conforming placement or
transport mechanism.

Core therefore defines no portable `sameHost`, `physicalLocation`,
`localityOf`, `rack`, `zone`, `region`, `numaNode`, or equivalent introspection
surface merely for transport/runtime optimization.

Administrative diagnostics may expose physical topology outside portable Core.
A future normative placement/topology facility may expose selected locality
concepts only by defining their own identity, stability, authority, failure, and
placement semantics explicitly.

This closes the former open ledger item `Physical-locality discovery`.
The broader relationship between logical Protos topology and physical
infrastructure topology remains a separate open design topic.

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

**CLOSED --- REVISED**

Awaitable operations may support a wait timeout.

A wait timeout affects waiting. It does not automatically cancel the
underlying operation.

A timeout must never be interpreted as proof that an operation did not
occur.

For remote communication, uncertainty may result when the runtime cannot
determine whether the acceptance boundary was crossed.

    UNKNOWN != NOT DELIVERED

Such uncertainty does not authorize transparent replay or destination
substitution.

Communication operations may additionally support delivery deadlines or
cancellation where appropriate.

The exact APIs remain open.

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

**CLOSED**

Core v0.1 does not standardize a generic `Future.race(...)`,
`Future.select(...)`, wait-any object, or another Future combinator whose result
is chosen by whichever independent source Future happens to become terminal
"first".

This is a semantic boundary, not an implementation omission.

Independent Future producers may become terminal because of Actor scheduling,
P scheduling, I/O completion, communication, cancellation, host events, or other
concurrent causes. Core deliberately does not define one global portable clock or
total event order across such independent terminal transitions.

Therefore a generic first-completion API would have to choose one of two
undesirable semantics:

- expose implementation observation order, callback order, carrier timing,
  kernel completion order, or scheduler timing as a language-visible winner; or
- impose an unrelated fixed priority such as argument index and call that
  priority a "race", even when another source actually became terminal earlier.

Core v0.1 does neither.

In particular, implementations must not add a standard-looking race/select
operation whose winner may differ solely because of thread scheduling, callback
registration order, polling cadence, work stealing, I/O backend choice, or other
non-semantic machinery.

`Future.all(...)` remains the standardized deterministic multi-Future
coordination primitive. It preserves argument order and deterministic
failure/cancellation selection without exposing completion timing.

Libraries and future standard facilities may define selection when they have an
independent semantic ordering rule. Examples include an explicit caller-supplied
priority, a protocol-defined message/order position, a timer/clock contract, or
another domain-specific event ordering. Such a facility must specify:

- what events are eligible;
- the exact selection/priority rule;
- behavior when several eligible outcomes are already available;
- behavior for semantically unordered concurrent events;
- result/failure/cancellation representation;
- whether losing operations continue, are merely unobserved, or receive explicit
  cancellation requests;
- registration/removal lifetime and resource bounds.

A future facility may use ordinary Futures internally, but Future terminalization
alone does not manufacture a portable total order between independent producers.

This closure preserves scheduler independence: concurrency may affect when an
answer becomes available, but generic Core Future coordination does not expose
implementation-selected timing as which answer is observed.

## 24G. Future Ownership and Actor Lifecycle Matrix

**CLOSED**

The interaction between Future ownership and Actor lifecycle is fully determined
in Core v0.1 by the existing structured-concurrency and Actor-termination rules.
It is not an implementation-selectable policy.

The normative cases are:

```text
Actor-local task-backed Future
    -> producing task belongs to that Actor execution domain
    -> Actor termination requests cooperative cancellation
    -> Actor waits for required task cancellation unwind/ensure cleanup
    -> task cannot continue as ordinary Protos work after Actor termination

detached Actor-local task-backed Future
    -> activation ownership edge is removed
    -> Actor-domain ownership is not removed
    -> Actor termination still requests cooperative cancellation
    -> task is not re-parented to Process, RootActor, replacement Actor, or runtime

Actor-originated non-task-backed Future
    -> no structured task ownership edge exists
    -> Actor termination records a cancellation request on the pending operation
    -> producer-specific commitment/cancellation semantics remain authoritative
    -> Actor generally does not wait for backend work that must safely continue
    -> no later continuation may resume ordinary code in the terminated Actor

P-result Future created from Actor-local code
    -> structured ownership follows the creating activation
    -> P execution remains isolated from Actor mutable state
    -> Actor termination requests cancellation through the existing structured
       ownership/lifecycle rules
    -> P work does not gain an independent Actor-like lifetime

pure observation Future with no Actor-originated producer ownership
    -> follows the explicit contract of that observation facility
    -> `detach()` remains a no-op when no task ownership edge exists
    -> merely holding the Future does not extend Actor lifetime
```

A Future object surviving as an ordinary value after its originating Actor has
terminated does not keep that Actor incarnation alive, resurrect it, or authorize
continuation execution inside its former mutable domain. The Future may still
carry a stable terminal outcome or, for producer/backend work whose contract
allows it, may later become terminal under runtime/producer custody.

Likewise, Actor replacement does not inherit pending tasks, continuation state,
Future producer ownership, or Actor-local mutable execution from the terminated
incarnation. A replacement is a fresh Actor domain.

Core defines no hidden "orphan Future" policy that may choose among continuing,
cancelling, re-parenting, abandoning, or migrating Actor-local work according to
runtime convenience. Each case above follows its already-defined ownership and
producer contract.

This section is a consolidation of existing normative rules, not a new lifetime
mechanism. It closes the former open ledger item `Future ownership interaction
with Actor lifecycle`.

## 24H. Non-Local Return Never Crosses an Actor Boundary

**CLOSED**

Core v0.1 non-local return is always confined to the execution domain that owns
the captured return home. An Actor boundary never transports, proxies, preserves,
or remotely targets another Actor's return home.

A `^` executed in destination-Actor code may therefore unwind only to a return
home that belongs to that destination Actor's own current execution/closure
structure. It cannot return into:

- the sender's message-send or request activation;
- the sender's Closure invocation;
- a creator Actor's activation;
- an activation that supplied bootstrap/initialization values;
- a suspended task or continuation in another Actor;
- a replacement incarnation of an Actor that once owned the original home.

Actor messaging transfers ordinary permitted values under the Actor snapshot
rules. It does not transfer execution contexts, call stacks, dynamic
continuations, return homes, or lexical-control authority.

Likewise, Actor bootstrap does not execute a transported caller Closure with its
caller capture intact. The destination Actor executes code it already owns, with
only explicitly supplied initialization values crossing by ordinary Actor
transfer semantics.

Consequently no implementation may realize cross-Actor control flow by treating
a non-local return as a hidden reply, exception, continuation jump, remote stack
unwind, or callback into the sender. `send()`/`request()` outcomes remain governed
solely by their communication contracts.

If a value graph that would require transferring a caller execution context,
continuation, or equivalent return-home authority is otherwise offered for Actor
transfer, it remains non-transferable under the existing Actor value-transfer
rules. This section introduces no special cross-Actor return error category.

Actor replacement cannot revive a dead return home. Return homes belong to
concrete execution structure, not stable ActorRef or GroupRef identity.

This rule is the Actor-domain counterpart of the existing P projection rule:
P explicitly creates a fresh P-local return home rather than preserving the
caller's. Across Actors the boundary is stricter still: no caller executable
continuation crosses at all.

A future explicit continuation or remote-workflow abstraction would need its own
value, lifetime, authority, failure, cancellation, and distribution semantics.
It must not retroactively turn ordinary `^` into cross-Actor control transfer.

## 24I. Dynamic Error Handlers Never Cross Actor Boundaries

**CLOSED**

Core v0.1 dynamic error handlers are execution-local control state. They belong
to the task/continuation whose dynamic execution installed them and never become
Actor-global or cross-Actor state.

An Actor boundary therefore never copies, inherits, snapshots, serializes,
proxies, forwards, or remotely consults another Actor's active dynamic handler
frames.

Consequently:

- a sender's active handlers do not become active while the destination Actor
  handles `send()` or `request()` work;
- a destination Actor's active handlers do not catch an Error merely because
  that Error later becomes observable to the sender;
- Actor bootstrap/initialization receives only its defined transferred values,
  not the creator's dynamic handler stack;
- Actor replacement starts with no dynamic handler frames inherited from the
  terminated incarnation;
- ActorRef/GroupRef transfer never carries handler authority;
- routing, transport, placement, same-host optimization, or shared-memory
  implementation does not weaken this boundary.

An Error that escapes destination Actor execution follows the already-defined
Actor failure/request uncertainty semantics. Core does not transport the
destination's dynamic handler stack back to the sender.

Likewise, if a Future later re-signals a stored Error through `value()`, handler
selection occurs only in the consumer's then-current dynamic context. The
producer's handler frames are not reconstructed or consulted.

The same principle already applies inside one Actor across distinct asynchronous
tasks: a new task does not inherit the creator's active dynamic handler frames.
The Actor boundary is therefore not a special exception but a stricter instance
of the same execution-local rule.

Implementations may internally represent dynamic handlers using host exception
frames, continuation metadata, stack records, tables, or another mechanism. Such
representation must not make cross-Actor handler propagation observable.

A future explicit distributed error-routing or workflow facility could transfer
an Error value or define recovery policy, but it would need its own protocol.
It must not retroactively make ordinary dynamic handler frames cross Actor
boundaries.

This closes the former open ledger item `Dynamic error handlers across Actor
boundaries`; there is no remaining implementation-selectable behavior in Core
v0.1 for this topic.

## 24J. Blocking Foreign Calls and Physical Offload

**CLOSED**

Core v0.1 does not standardize a general Java/FFI/native-call API. However, any
implementation extension that invokes foreign or host code from ordinary Protos
execution must preserve the existing Protos execution-segment semantics.

A foreign operation exposed as a **synchronous** Protos call is part of the
current Protos execution segment. If the foreign operation blocks, the calling
Actor's semantic execution segment remains occupied until that call returns,
fails, or otherwise completes according to the extension's synchronous contract.

An implementation may physically offload that blocking host work to another OS
thread, carrier, native worker, helper process, or equivalent mechanism to avoid
blocking a runtime scheduler thread. Such physical offload is semantically
invisible.

In particular, physical offload of a synchronous foreign call does **not** create
an implicit Protos suspension point. While that synchronous call is outstanding:

- no other Actor-local Protos task or message handler may interleave in the same
  Actor merely because the host call is running elsewhere;
- Actor-local mutable state remains protected by the same uninterrupted segment
  boundary as if the host call were executed directly on the current carrier;
- cancellation is not newly observable inside the call unless the extension
  explicitly defines a cancellation-aware foreign operation;
- dynamic handlers, `this`, lexical state, return homes, and other execution
  context remain those of the original synchronous call;
- runtime carrier availability must not change whether Actor-local reentrancy is
  observable.

If an extension wants a foreign operation to permit Actor-local reentrancy while
the external work is pending, it must expose an **explicit asynchronous
boundary**, normally by returning a Future or another separately standardized
asynchronous operation whose suspension/cancellation/commitment semantics are
defined. Waiting through the ordinary Future protocol may then suspend at the
already-defined explicit boundary.

Therefore these two implementation strategies are semantically equivalent for a
synchronous foreign operation:

```text
call host operation directly and block carrier
```

and:

```text
offload host operation physically
park current implementation continuation
resume same Protos segment when host result is ready
```

They are **not** equivalent to:

```text
offload host operation
run unrelated Actor-local Protos work
resume original call later
```

unless the language/extension contract explicitly introduced an asynchronous
suspension boundary before that interleaving.

This rule prevents implementation-selected host integration from weakening C's
serialized mutable-state semantics or making Actor reentrancy depend on the
runtime's foreign-call executor architecture.

The number, size, queueing discipline, admission policy, thread identity,
pinning, and scheduling of foreign-call offload workers remain implementation
details unless an extension explicitly makes them semantic.

A catastrophically non-returning foreign call may therefore stall the calling
Actor indefinitely under a synchronous contract. Core does not invent hidden
preemption, timeout, cancellation, or migration to recover from such a call.

Future interoperability facilities may define stronger contracts, including
interruptible calls, deadlines, host cancellation, resource isolation, process
offload, or dedicated blocking pools. Such facilities must define their own
observable semantics and must not retroactively change the meaning of an
ordinary synchronous call.

This closes the former open ledger items `Blocking foreign calls` and
`Blocking-operation offload`.

## 24K. Foreign Mutable State Does Not Bypass Actor Isolation

**CLOSED**

Core v0.1 does not standardize a general Java/FFI/native interoperability API.
However, any implementation or extension that exposes foreign objects, Java
objects, native objects, static fields, process globals, library globals, or
equivalent host state to Protos must preserve the closed Actor-isolation rules.

A host/native reference is not permitted to become an accidental shared-memory
escape hatch merely because the mutable storage it reaches is outside the Protos
heap.

In particular, ordinary Protos code in two different Actors must not acquire
foreign wrappers/capabilities that allow both Actors to synchronously read or
mutate the same underlying mutable host state as though that state were ordinary
Actor-local object state.

This includes, unless mediated by a separately defined safe boundary:

- Java static mutable fields;
- mutable Java singleton objects reachable from static state;
- JNI/native process globals;
- mutable C/C++ library globals;
- host-language module/class globals;
- mutable singleton registries or caches whose contents are directly exposed as
  application state;
- foreign object identity whose methods provide unrestricted shared mutation
  across Actor domains.

Such state may still exist inside an implementation. The restriction is on what
portable Protos code may observe and mutate through ordinary Actor-local object
semantics.

A conforming interoperability facility must use one of the following semantic
shapes, or another shape with equivalent isolation:

```text
copy/value boundary
    -> each Actor receives an isolated logical value/snapshot

immutable shared boundary
    -> underlying state is semantically immutable for the sharing duration

capability/service boundary
    -> mutable external state is accessed through an explicit resource/service
       capability with its own concurrency, ordering, failure, and lifetime rules

Actor ownership boundary
    -> mutable foreign state is exclusively owned by one Actor and other Actors
       interact through ordinary Actor communication
```

An implementation may internally synchronize host globals with locks, atomics,
thread confinement, JNI monitors, native queues, or equivalent mechanisms.
Internal synchronization alone does not make unrestricted shared mutable access
a valid Protos semantic surface.

Likewise, making a foreign wrapper thread-safe does not make it Actor-isolated.
Thread safety prevents low-level races; Actor isolation requires that one Actor's
ordinary mutable state cannot be directly observed or mutated by another Actor
without crossing an explicit semantic boundary.

Foreign object identity also cannot silently become cross-Actor Protos identity.
If two Actor-local wrappers happen to refer to the same host object internally,
portable Protos observations must still match the declared copy, immutable,
capability/service, or ownership contract. Host pointer/reference equality is not
thereby a Core identity relation.

A bridge must not choose at runtime between copying and shared mutable aliasing in
a way that changes Protos-observable behavior. Any sharing policy that is
observable must be part of the explicit interoperability contract.

The same boundary applies to P. A host/global capability is not P-transferable
merely because it is thread-safe or process-global. P crossing requires an
explicit P-safe contract consistent with §71.

This section closes the former open ledger items `Java interoperability
isolation`, `Java static mutable state`, and `Native global state`.

## 24L. Non-Transferable Resources Never Auto-Proxy

**CLOSED**

Core v0.1 distinguishes **transfer of an existing value** from **provisioning a
new capability**.

When an Actor message/reply value graph contains a non-transferable live resource
or foreign-resource value, the ordinary Actor transfer rule applies exactly as
defined in §16:

```text
attempt to transfer existing non-transferable resource
    -> NonTransferableValue
    -> no partial message/reply transfer
```

The runtime must not silently replace that value with a proxy, broker handle,
remote stub, service reference, duplicate open handle, reopened resource, or
other newly created capability in order to make transfer succeed.

Such substitution would change observable semantics including identity,
authority, lifetime, failure, ordering, cancellation, close behavior, resource
position/state, and possibly external effects. It is therefore not an
implementation optimization of value transfer.

This rule applies to, among other non-transferable values:

- open files;
- sockets and other live stream/resource endpoints;
- native resource handles;
- foreign objects without an explicit Actor-transfer contract;
- resource wrappers whose semantics depend on Actor-/Process-local ownership;
- any capability that another normative rule declares non-transferable.

A proxy or routed capability may still be a valid design, but it is a **distinct
provisioning operation** with its own contract. Such a facility must define, at
minimum:

- how the new proxy/capability is obtained;
- what resource/service it denotes;
- whether it preserves or replaces identity;
- its authority scope and whether authority is attenuated;
- operation ordering and concurrency;
- failure and unreachability behavior;
- cancellation/commitment behavior;
- close/lifetime/ownership semantics;
- whether state such as file position is shared, copied, virtualized, or
  independently maintained;
- Actor/P transferability of the resulting proxy value itself.

A separately provisioned Actor-local proxy may route operations to an owning
Actor, Process service, resource broker, host service, or another explicit
authority boundary. That does not make the original live resource transferable.

Likewise, "equivalent access" is not "the same value transferred". Opening the
same path again, reconnecting to the same network address, duplicating an OS
handle, or routing through a service can all have semantics different from
transferring the original capability and must never be substituted implicitly.

Physical same-host placement, shared memory, OS handle-passing support, Java
reference reachability, or native pointer reachability do not weaken this rule.

The same principle applies to P: a non-P-transferable resource cannot be made
valid P input/result by silently inserting a proxy. A future P-safe capability
must have an explicit P crossing contract.

This closes the former open ledger items `Non-transferable resource capabilities`
and `Foreign-resource proxies`.

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

## 26A. Core Failure-Authority Policy and API Boundary

**CLOSED**

Core v0.1 does not expose a public failure-authority configuration object,
supervisor Actor, policy callback, or mutable supervision tree.

The failure-authority relationship remains a semantic/runtime relationship used
to ensure that every unhandled fatal Actor failure has one well-defined place
responsible for applying the Core policy. It is not itself a new language object
kind and does not grant ordinary Protos code implicit authority over another
Actor.

For an Actor that is not the Process RootActor, the Core v0.1 failure-authority
policy after an unhandled fatal failure is:

```text
failed Actor incarnation
    -> terminates under the existing failure/cleanup rules
    -> no automatic replacement by failure authority
    -> no automatic escalation to creator/parentActor
    -> no automatic termination of siblings or descendants
```

This does not suppress independent higher-level reconciliation. In particular,
if the failed Actor was a member selected to satisfy an ActorGroup's desired
state, the Group Controller may independently create a fresh replacement
incarnation under the existing Group rules. That creation is Group
reconciliation, not failure-authority replacement, and the new Actor receives a
new ActorRef.

For the Process RootActor, the existing Core rule remains authoritative:

```text
RootActor unhandled fatal failure
    -> Process terminates
```

Therefore two Core implementations must not differ by silently choosing
`Replace`, `Escalate`, or sibling/subtree restart for an otherwise identical
non-root Actor failure.

The previously listed `Replace`, `Stop`, `Escalate`, and `Ignore` choices are
design vocabulary for possible future policy-bearing abstractions, not four
implementation-selectable Core behaviors. Within Core v0.1 the observable
default is the fixed policy above.

A future supervision or controller facility may expose configurable failure
policy, restart-intensity limits, one-for-one/one-for-all relationships, or
other recovery mechanisms. Such a facility must define its own authority,
lifetime, isolation, transfer, ordering, resource, and failure semantics
explicitly. It must not retroactively make Core v0.1 failure behavior
implementation-defined.

No dedicated failure-authority API is therefore part of Core v0.1. Runtime
diagnostics may internally retain structured failure information, but ordinary
application access to policy diagnostics or configurable supervision is a
future higher-level API rather than an unresolved requirement for Core
compatibility.

## 27. Actor Identity Is Incarnation Identity

**CLOSED --- REVISED**

Actor identity is the identity of one concrete Actor incarnation.

An ActorRef identifies exactly that incarnation.

If the Actor dies, that Actor identity ends permanently and its ActorRef
never rebinds, resurrects, or retargets to another Actor.

Replacement creates another Actor with a new Actor identity and a new
ActorRef.

Durable state, if later used to initialize a replacement, does not
resurrect the dead Actor identity.

Live Actor migration is not required by Protos semantics. If an
implementation supports migration as an optimization, it may preserve an
ActorRef only when it preserves the same live Actor incarnation and its
observable execution state. Killing an Actor and creating another one is
replacement, not migration.

Stable service continuity belongs to higher-level abstractions such as
Actor Groups or discovery names, not to an individual Actor incarnation.

## 28. Messages Across Actor Failure

**CLOSED --- REVISED**

Effects of messages that completed before Actor failure remain effects of
those completed operations.

A direct message addressed to an ActorRef is never retargeted to another
Actor after the destination Actor dies.

For Group-addressed communication, the Group may choose or re-choose a
member only before a concrete Actor accepts the operation.

Acceptance occurs when the Actor takes ownership of the message for
processing. It is distinct from handler start and handler completion.

If an accepted-but-not-completed operation is lost because the Actor
fails, Protos does not transparently replay it against a replacement,
because doing so could duplicate effects. This includes operations already
accepted while that Actor was INITIALIZING if initialization ends in an
unhandled fatal failure.

If the runtime cannot determine whether acceptance occurred before a
failure or partition, delivery is uncertain. Uncertainty does not imply
non-delivery and does not authorize transparent replay.

Exactly-once processing, durable inboxes, deduplication, idempotency, and
workflow recovery remain explicit higher-level facilities rather than
properties of ordinary Actor messaging.

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

## 29A. Actor Lifecycle Monitoring API

**CLOSED**

Core exposes Actor lifecycle observation through the existing `Future`
abstraction rather than introducing a second event-stream, monitor-handle, or
mailbox-message universe.

`ActorRef.termination()` returns a new non-task-backed Future local to the
calling execution domain. The returned Future represents only this observation
of the concrete Actor incarnation identified by that ActorRef.

If termination of that incarnation is already known when `termination()` is
called, the returned Future is resolved with that same ActorRef. Otherwise it
remains pending until termination becomes known, then resolves with that same
ActorRef.

Each call creates an independent observation Future. Cancelling one such Future
only abandons that observation. It never requests termination of the target
Actor, never cancels another observer, and never affects supervision, failure
authority, Group policy, routing, or the Actor's lifecycle.

The first terminal transition of the observation Future wins under ordinary
Future semantics. If observer cancellation races with notification of known
termination, whichever transition reaches that Future first determines its
stable terminal state.

Monitoring reports **known termination**, not mere loss of communication.
`UNREACHABLE`, `UNKNOWN`, routing failure, timeout, connection loss, or network
partition must not resolve or fail the observation Future as though Actor
termination had been proved. A future Process/Node failure-detection mechanism
may establish stronger knowledge, but only its own normative contract may turn
such evidence into known Actor termination.

The observation does not expose the target Actor's private unhandled `Error`,
mutable state, mailbox contents, cleanup state, or failure-authority
information. Callers that need failure-policy diagnostics require a separate
capability/API. `termination()` answers only the lifecycle question: whether
this concrete Actor incarnation is known to have ended.

A monitor installed while the target is INITIALIZING, READY, or TERMINATING
therefore remains tied to that same incarnation until known TERMINATED. Actor
replacement does not retarget the observation. If a replacement is created, it
has a different ActorRef and requires a different call to `termination()`.

Observation registration and target termination form an atomic semantic race:
either registration observes that termination was already known and returns an
already-resolved Future, or registration becomes live before the termination
notification and that notification resolves it. An implementation must not
permit the classic lost-wakeup outcome in which termination occurs between a
state check and registration and the Future remains pending forever.

A pending observation must not retain arbitrary caller state. The runtime may
represent registrations by compact internal continuations or tokens. Once an
observation Future becomes terminal or its cancellation is honored, its live
registration must be removed or made inert so terminated Actors and long-lived
observers do not retain dead observation state without bound.

The implementation may use direct local registration, distributed monitor
protocols, routing metadata, tombstones, or another mechanism. Those choices
are non-observable provided the rules above are preserved.

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
in `PROTOS_IO_MODEL.md`.

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

**CLOSED --- REVISED**

A Process is a Protos execution, isolation, and failure domain containing
one RootActor and any additional Actors hosted in that domain.

A Protos Process is not normatively an operating-system process or an
address-space boundary. A standalone launcher may map one Protos Process
to one operating-system process, while a managed runtime may host Protos
Processes differently, provided that all Protos isolation and failure
semantics are preserved.

Actor-to-Actor and Process-to-Process communication use the same
fundamental pass-by-value message semantics.

Two Protos Processes with established physical locality may communicate
using optimized mechanisms such as shared memory, mmap, IPC, local
sockets, or in-runtime transports. Different machines may use network
transports.

The transport and physical hosting arrangement must not change observable
message value semantics or expose cross-Process mutable references.

A Process is ephemeral execution capacity, not a durable application
identity.

If a Protos Process terminates, the Actors hosted by that Process
terminate. Terminating that Process does not semantically require
terminating an operating-system process that may host other Protos runtime
services or execution domains.

A later Process is new capacity, not a reincarnation of the previous
Process.

Failure of a remote Process or communication channel does not
automatically terminate a local Process.

## 37A. Process Failure Knowledge Boundary

**CLOSED**

Core v0.1 does not define or require an automatic distributed failure detector
for remote Processes.

A runtime may know that a Process has terminated when that fact is established
by direct lifecycle authority already inside the runtime's custody, such as the
runtime completing termination of a Process it hosts. That is lifecycle
knowledge, not failure detection by inference.

For a Process that is only remotely reachable, loss of communication is not
proof of Process termination. In particular, none of the following by itself
permits Core to conclude `TERMINATED`:

- heartbeat silence;
- connection closure or reset;
- routing failure;
- request or communication timeout;
- failure to reconnect;
- DNS, transport, or host reachability failure;
- observation that the containing Node is currently unreachable; or
- a local suspicion threshold derived from elapsed time or missed traffic.

Such evidence may justify `UNREACHABLE` or `UNKNOWN` according to the existing
distributed-state distinction, but it must not fabricate terminal lifecycle
knowledge.

Therefore Core v0.1 has no implementation-selectable timeout, heartbeat
interval, phi threshold, retry count, host-probe rule, or operating-system
heuristic whose expiry turns a remote Process into `TERMINATED`.

This rule composes with Actor lifecycle monitoring. Loss of a remote Process or
its transport does not resolve `ActorRef.termination()` for Actors hosted there
merely because communication was lost. Without independently authoritative
termination knowledge, those Actors remain non-`TERMINATED` from the observer's
point of view.

A future distributed-runtime facility may define Process failure detection,
leases, epochs, quorum-backed membership, fencing, explicit downing, or another
mechanism. Such a facility must distinguish suspicion/unreachability from any
decision that makes termination authoritative, and must specify the scope and
observable consequences of that decision.

Until such a facility is defined, the absence of a Core Process failure
detector is normative rather than an implementation gap. Implementations may
collect health telemetry internally, but they must not let implementation-
specific heuristics change Core lifecycle, ActorRef, monitoring, messaging, or
replacement behavior.

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

## 38A. Node Failure Knowledge Boundary

**CLOSED**

Core v0.1 does not define or require an automatic distributed failure detector
that turns loss of contact with a remote Node into authoritative Node
termination.

A runtime may know that a Node incarnation has ended when that fact is
established by lifecycle authority already inside the runtime's custody. For
example, a runtime that is itself terminating a local Node incarnation may know
when that termination has completed. This is direct lifecycle knowledge, not
failure detection inferred from remote silence.

For a Node known only through distributed communication, the following evidence
is insufficient by itself to establish `TERMINATED`:

- missed heartbeats or probes;
- transport disconnect, reset, or refusal;
- routing failure;
- timeout;
- inability to reconnect;
- host, VM, container, pod, or infrastructure reachability failure;
- loss of every currently known Process connection hosted by that Node; or
- any implementation-selected elapsed-time, retry-count, or suspicion threshold.

Such evidence may establish or maintain `UNREACHABLE` or `UNKNOWN` under the
existing distributed-state distinction. It must not fabricate terminal
lifecycle knowledge.

Core therefore defines no implementation-selectable heartbeat interval, phi
threshold, retry count, grace period, lease duration, infrastructure probe, or
host-specific rule whose expiry changes a remote Node incarnation to
`TERMINATED`.

Node identity remains incarnation identity. If connectivity is later restored
to the same still-live Node incarnation, Core must not have killed that
incarnation merely because a detector timeout expired. If equivalent capacity
appears as a new Node incarnation, that new Node remains distinct under the
existing identity rule.

This boundary deliberately does not decide Cluster membership removal,
partition downing, quorum policy, fencing, split-brain resolution, or authority
for declaring a member permanently removed. Those are separate distributed
coordination mechanisms and remain governed by their own sections and open
design topics.

A future normative Cluster/distributed-runtime facility may define a failure
detector and a separate authoritative downing/removal decision. If it does, it
must specify at least:

- what evidence produces suspicion or `UNREACHABLE`;
- what authority may convert suspicion into an irreversible membership or
  lifecycle decision;
- the incarnation/epoch being decided;
- partition and stale-observer behavior;
- fencing or equivalent protection against two live sides both acting as the
  same authority; and
- consequences for hosted Processes, ActorRefs, monitoring, routing, and
  replacement.

Until such a facility exists, the absence of automatic Node failure detection in
Core is normative rather than an implementation gap. Internal health telemetry
may use arbitrary heuristics provided those heuristics do not alter Core-visible
Node, Process, ActorRef, monitoring, messaging, or replacement semantics.

## 38B. Network-Partition Knowledge and Reporting Boundary

**CLOSED**

Core v0.1 does not expose `NETWORK_PARTITION` as a distinct proven lifecycle or
reachability state.

From one distributed runtime domain, loss of communication with another domain
is not sufficient to distinguish a network partition from remote pause,
transport failure, routing failure, overload, local connectivity failure, or
remote failure. Core therefore reports only the knowledge it can justify:
`UNREACHABLE` or `UNKNOWN`, subject to the existing reachability rules.

An implementation may internally diagnose that a partition is likely, and
administrative telemetry may describe transport observations or suspicion.
Such diagnostics must not change Core-visible lifecycle, membership, Authority,
ActorRef, monitoring, messaging, or replacement semantics merely because the
implementation labels the condition a partition.

Core also defines no implementation-specific partition timeout after which
`UNREACHABLE` becomes a stronger state. Elapsed time does not turn inability to
communicate into proof of the cause of that inability.

This rule is symmetric with respect to partitions: two sides may simultaneously
observe the other as unreachable. Neither observation grants Authority over the
other side, proves that the other side terminated, or permits a conflicting
exclusive decision that the observer could not otherwise authorize.

If communication is restored without an authoritative membership/lifecycle
decision having ended the relevant incarnation, ordinary communication may
resume with that same still-live identity. Core does not create a new Node,
Process, or Actor incarnation merely because reachability was temporarily lost.

A future distributed coordination facility may expose richer partition
reporting or topology diagnostics. If it claims that a partition is
authoritatively identified rather than merely suspected, it must define the
evidence, observation scope, staleness rules, and consequences. Split-brain
resolution, membership removal, fencing, quorum decisions, and Authority
acquisition remain separate mechanisms and are not implied by partition
reporting.

Thus Core's portable observable report for ambiguous communication loss is
reachability knowledge, not guessed network topology.

## 38C. Core Split-Brain Safety Boundary

**CLOSED**

Core v0.1 does not define an automatic split-brain resolver, partition winner,
majority side, oldest side, static quorum, lease-majority strategy, or
implementation-selected downing policy.

When distributed communication is lost, Core safety follows the existing
Authority rule rather than inventing a universal partition policy:

```text
operation does not require Authority
    -> may continue if all of its other semantic prerequisites hold

operation requires Authority(scope)
    and currently valid Authority(scope) can be demonstrated
    -> may proceed

operation requires Authority(scope)
    and currently valid Authority(scope) cannot be demonstrated
    -> must not proceed
```

Consequently, a partitioned runtime domain does not become authoritative merely
because it is still executing, has more Nodes, has an older Node, retains a
particular infrastructure identity, or has waited for an implementation-defined
timeout.

Core also does not automatically terminate, down, fence, restart, replace, or
remove the opposite side of an ambiguous partition. Those are authoritative
control decisions when correctness depends on exclusivity and therefore require
a separately defined mechanism capable of demonstrating the required Authority.

This deliberately favors portable safety over hidden availability policy. A
partition may make some authoritative operations unavailable while unrelated
local computation continues under the existing rule. Core provides no guarantee
that every partition can continue making progress for operations whose
correctness requires distributed exclusivity.

When connectivity heals, the same still-live incarnations may resume ordinary
communication if no independent authoritative membership or lifecycle decision
has removed or terminated them. Core itself performs no partition-healing
reconciliation that rewrites identity, replays uncertain messages, or merges
divergent mutable Actor state.

This rule does not claim that arbitrary application effects on two isolated
sides can be automatically reconciled. Applications and future distributed
facilities that require exclusive external side effects, singleton writers,
durable replicated state, or other non-convergent invariants must use an
Authority/fencing mechanism whose semantics cover those effects.

A future Cluster facility may standardize a split-brain resolver or one or more
explicit policies. Such a facility must define its decision scope, evidence,
Authority acquisition, fencing, stale-holder behavior, membership consequences,
recovery after healing, and interaction with Processes, Actors, Groups,
messages, and external effects. Until then, implementations must not silently
choose a split-brain/downing strategy as Core semantics.

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

**CLOSED**

Core v0.1 defines the semantic meaning and consequences of Cluster membership,
but it does not standardize a distributed membership protocol, wire format,
gossip algorithm, consensus algorithm, discovery transport, join handshake, or
failure-detector algorithm.

A Node is a member of a Cluster only when the active runtime has established
that membership through a mechanism provided by the runtime configuration or a
future/extended distributed facility. Merely discovering an address, opening a
transport connection, sharing infrastructure, or observing another Node does
not itself create Cluster membership.

Membership is distinct from Node identity, reachability, physical existence,
and Authority. In particular:

```text
member && unreachable
    -> still the same Node incarnation unless an authoritative membership
       or lifecycle decision says otherwise

not-member
    -> not usable as capacity in that Cluster view
    -> does not by itself prove that the Node or its Processes terminated

reachable
    -> does not by itself imply membership

member
    -> does not by itself grant Authority for an exclusive decision
```

Core does not define a portable application-level operation such as
`cluster.join()`, `cluster.leave()`, `down(node)`, or an automatic bootstrap
rule. Core also defines no portable ordering, latency, convergence, or
simultaneous-view guarantee for implementation-specific membership dissemination.

Consequently, two independent Core implementations are not required to
interoperate at the Cluster-membership wire-protocol level. A runtime that
provides distributed Cluster membership in v0.1 does so as an implementation or
extension facility, but that facility must not alter any already-closed Core
semantic rule for Actor/Process/Node identity, reachability, message uncertainty,
monitoring, Authority, or split-brain safety.

Portable Core behavior must therefore not depend on which unspecified
membership algorithm a runtime happens to use. Where a Core semantic operation
depends on Cluster membership, it operates against the membership knowledge that
the active runtime has validly established; it must not infer stronger facts
than that knowledge supports.

Voluntary membership removal, administrative removal, failure-driven removal,
and replacement capacity are also not conflated:

- voluntary or administrative removal changes Cluster membership;
- removal does not resurrect, retarget, or reuse Node identity;
- removal alone does not prove physical Node/Process death;
- equivalent capacity joining later is a new Node incarnation; and
- any removal decision whose correctness requires exclusivity must satisfy the
  scoped Authority rules before it is treated as authoritative.

A future standardized Cluster facility may define interoperable membership,
join/leave APIs, dissemination, epochs, quorum/consensus, convergence, and
failure-driven removal. Such a facility must make those properties normative
and must compose with the already-closed failure-knowledge, partition,
split-brain, and Authority boundaries.

The `Cluster membership protocol` topic is therefore outside Core v0.1 rather
than an implementation-selectable hole in Core semantics.

## 40. ActorRef

**CLOSED --- REVISED**

ActorRef is an opaque communication capability identifying exactly one
concrete Actor incarnation.

ActorRef is not a pointer, host:port, URL, Process address, Node address,
or stable service name.

Its semantics are independent of physical placement or transport while
the same live Actor incarnation continues to exist.

If the Actor dies, the ActorRef remains associated with that terminated
Actor and is never rebound or retargeted to a replacement.

Node membership loss, routing failure, or temporary unreachability does
not by itself terminate the ActorRef because those observations do not
prove Actor termination.

Live migration is optional runtime optimization rather than a semantic
requirement. It may preserve an ActorRef only when the same live Actor
incarnation and observable execution state are preserved.

ActorRef is a communication capability. Possessing an ActorRef does not
by itself demonstrate Authority for a Group, Cluster, exclusive role, or
other authoritative scope.

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

A Process is a concrete Protos runtime execution, isolation, and failure domain. It is not required to map one-to-one to an operating-system process or address space.

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

Groups are stable logical policy and management units over homogeneous
runtime entities.

A Group is not an additional level in the intrinsic runtime hierarchy:

    Actor -> Process -> Node -> Cluster

Conceptually, groups may exist for entities at different levels:

    Group<Actor>
    Group<Process>
    Group<Node>

This notation is conceptual and does not imply generic type syntax.

Each group-managed entity belongs to one management Group at its
corresponding level. Each grouping level may provide an implicit default
Group.

Specialized Groups are introduced only when a set of entities requires
policy different from the default, such as availability, affinity,
placement, scaling/capacity policy, routing, or resource policy.

### Group Identity and Lifecycle

Group identity is independent of its current membership and of the
controller instance currently managing it.

A Group may remain valid with zero current members, and complete
membership replacement does not create a new Group.

Group lifetime is determined by an owning runtime/control scope, not by
global reachability of GroupRef values. Remote GroupRefs do not keep a
Group alive by themselves.

A Group may outlive the runtime scope that originally created it only
when durable or external ownership is explicitly configured. Such
continuity requires preservation or reconstruction of the defining Group
identity, policy, and control state.

Stable does not mean durable. If the state defining a non-durable Group
is lost, that Group identity ends. A later Group created under the same
discovery name is a new Group.

### Group and Group Controller

Group and Group Controller are distinct concepts.

The Group identifies the logical managed set, policy, and stable Group
identity.

A Group Controller is an ephemeral control-plane executor that may
reconcile Group policy and membership. Controller replacement or
Authority transfer does not create a new Group.

The Group Controller is not a mandatory data path for every message.
Runtime routing may use cached or locally available routing views while
controllers reconcile policy and membership separately.

Not every Group requires active membership management or distributed
Authority.

Groups do not intrinsically require Authority. Authority is introduced
only where conflicting decisions could violate an explicit authoritative
invariant.

### Desired Cardinality

A Group may define desired cardinality.

Desired cardinality is a convergent control objective, not a strict
instantaneous global invariant.

It does not imply identical state, state replication, shared history,
transparent failover, consensus, persistent service state, or an exact
physical Actor count at every instant.

During partitions, delayed observations, concurrent reconciliation, or
membership uncertainty, physical membership may temporarily be below or
above the desired cardinality. Normal reconciliation converges toward the
desired state when sufficient coordination becomes available.

A strict singleton or maximum-authoritative-role invariant applies to
valid Authority, not necessarily to the number of physically existing
Actors, and requires the corresponding Authority/fencing mechanism.

Stopping one concrete Group member terminates that Actor incarnation. It
does not implicitly reduce Group desired cardinality. If desired state
still requires capacity, the Group Controller may create a replacement
Actor.

Failure policy and Group desired-state reconciliation are independent. A
failure policy such as Stop determines what happens to the failed Actor
incarnation; it does not suppress Group reconciliation. Where multiple
policies request equivalent replacement capacity, the runtime may
reconcile those intents rather than creating redundant replacements.

### ActorGroup Communication

Only an Actor Group is an application communication target by default.
Process and Node Groups are management, placement, capacity,
availability, monitoring, or administrative constructs unless future
explicit APIs define otherwise.

An ActorGroup may optionally act as a location-transparent communication
destination. This capability is lazy and follows the Protos
pay-as-you-grow principle.

Conceptually:

    ActorRef -> one concrete Actor
    GroupRef -> one concrete ActorGroup identity; routing selects a member

ActorRef and GroupRef are both communication targets and expose the same
fundamental send/request communication model while retaining different
destination semantics.

Multiple GroupRefs may refer to the same Group. GroupRef identity is not
Group identity, and implementations need not globally intern GroupRef
objects.

A GroupRef may carry a restricted communication capability for its Group.
Group identity is independent of the permissions carried by a particular
GroupRef. Authority remains a separate capability.

GroupRef is transferable through ordinary Actor/Process message value transfer.
The transferred value denotes the same concrete Group identity and preserves the
same effective communication capability and restrictions as the source GroupRef.
Transfer cannot grant broader communication permission and cannot implicitly
grant Group, Cluster, role, fencing, or other Authority.

Transfer of a GroupRef does not copy or expose mutable Group membership,
controller, routing-cache, policy, or control-plane state. A receiving runtime
may materialize a distinct local GroupRef representation; because GroupRef object
identity is not Group identity and global interning is not required, such
representation differences are unobservable except through the already-defined
GroupRef communication semantics.

Receiving or retaining a transferred GroupRef does not extend the Group's
lifetime. The existing rule that remote GroupRefs do not keep a Group alive
continues to apply. If that Group later terminates, every transferred GroupRef
remains bound to that terminated Group identity and never retargets to a Group
later created or discovered under the same name.

A GroupRef is location-transparent and remains associated with the same
Group across member creation, removal, replacement, temporary empty
membership, controller replacement, and routing-path changes.

Discovery names may rebind; a concrete GroupRef never rebinds to another
Group identity.

Normal `send` or `request` directed to an ActorGroup selects exactly one
eligible concrete Actor by default. Broadcast/multicast is a distinct
explicit operation.

There is no Group-wide FIFO guarantee. If multiple operations happen to
route to the same concrete Actor, that Actor's normal ordering guarantees
apply.

### Routing, Membership, and Authority

Group routing may use locally available, cached, or eventually consistent
membership information when stronger consistency is not required for
correctness.

Routing eligibility does not imply Authority. Receiving a Group-routed
message does not grant, renew, or demonstrate Authority for an exclusive
role or decision scope.

Operations whose correctness requires Authority must independently
demonstrate valid Authority for the relevant scope.

Liveness, Group membership, routing eligibility, and Authority are
separate concepts. An Actor may remain alive and useful for
non-authoritative work even when it is no longer part of the current
authoritative membership or role view.

### Routing and Acceptance

While the Group still owns routing responsibility and no concrete Actor
has accepted an operation, it may wait for capacity, select another
eligible member, or re-route according to its routing policy.

Acceptance occurs when a concrete Actor takes ownership of the message
for processing. Acceptance does not imply handler start or completion.

Once accepted, normal concrete-Actor delivery and failure semantics
apply. Group communication does not imply transparent retry, replay, or
exactly-once delivery after acceptance.

If the runtime cannot determine whether acceptance occurred, the
operation becomes uncertain. Uncertainty does not authorize transparent
replay.

The logical message snapshot remains the snapshot taken at the original
`send()` or `request()` invocation across all pre-acceptance waiting and
rerouting.

### Empty, Unreachable, and Terminated Groups

Group lifetime, member availability, routing reachability, controller
reachability, and Authority availability are distinct.

If a live ActorGroup currently has no eligible member, communication
applies bounded backpressure and may remain pending according to normal
timeout, deadline, cancellation, and capacity-demand semantics.

Temporary inability to reach Group routing/control information does not
by itself prove that the Group has terminated.

If the Group is known to have terminated before a pending operation has
been accepted by a concrete Actor, that operation fails.

If a concrete Actor already accepted the operation, subsequent Group
termination does not revoke ownership of that accepted operation; normal
Actor semantics apply.

Demand for a live Group with insufficient eligible membership may
contribute to capacity-demand signals.

Sending to a Group does not itself provision infrastructure. Group
reconciliation may create Actors when policy requires them, and external
Infrastructure Controllers may separately provision Process/Node
capacity in response to semantic capacity demand.

## 51. Capacity Demand and Infrastructure Integration

**CLOSED --- REVISED**

The core Protos runtime does not provision infrastructure capacity by
default.

Protos observes runtime state, performs semantic placement/admission and
Group-control decisions, and exposes semantic capacity demand.

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

    application/runtime intent
            |
            v
    Protos semantic control
            |
            v
    capacity demand
            |
            v
    external infrastructure
            |
            v
    new raw capacity
            |
            v
    Protos bootstrap/discovery/membership
            |
            v
    available scheduling capacity
            |
            v
    Actors

Metrics are one possible representation of Protos demand. They are not
the semantic model itself.

Newly provisioned capacity is incorporated into Protos only through the
runtime's normal bootstrap, discovery, and membership mechanisms.

The Infrastructure Controller does not directly mutate Protos logical
topology or define Actor, Group, Process, Node, Cluster, routing,
membership, failure, or Authority semantics.

A Kubernetes Deployment replica is not a Protos ActorGroup member merely
because the workload may host Protos runtime capacity. One Process may
host many Actors, so infrastructure replica cardinality is distinct from
ActorGroup desired cardinality.

Processes and Nodes may disappear at any time.

Correctness must not depend on graceful removal.

Draining is advisory and opportunistic: it may reduce disruption during
planned removal, but the runtime must remain correct if a Process or Node
disappears immediately without draining.

Exact Capacity Demand APIs, infrastructure adapters, scale-up/down policy,
and draining mechanics remain open.

## 52. Ephemeral Actor Liveness and Explicit Durability

**CLOSED --- REVISED**

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

Higher-level runtime mechanisms may restore required service capacity by
creating replacement Actors, but that does not constitute transparent
continuation of the failed Actor.

If durable state is later used for recovery, recovery initializes a new
Actor incarnation from that durable state. It does not resurrect the dead
Actor identity or ActorRef.

Principle:

> Actor incarnations are disposable. Durability, when required, must live
> outside the ephemeral incarnation.

## 53. Direct ActorRef Versus Stable Group Identity

**CLOSED --- REVISED**

An ActorRef addresses one concrete Actor incarnation.

If that Actor dies, the ActorRef remains associated with that terminated
Actor and is not rebound to a replacement.

If application code keeps a direct ActorRef, it has explicitly chosen to
communicate with that individual Actor and therefore observes that
Actor's mortality.

Stable service continuity belongs to a higher-level abstraction such as
an ActorGroup.

Conceptually:

    ActorRef
        -> individual, ephemeral destination

    GroupRef
        -> one stable Group identity
        -> changing membership

    discovery name
        -> may later resolve to another identity

Communication directed to a live Group may continue across member
replacement according to Group routing rules. Communication directed to
an individual Actor does not.

Group identity is stable across membership/controller changes but is not
intrinsically durable across loss of the state that defines the Group.

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

**CLOSED --- REVISED**

Supervision/failure authority reacts to the failure of one Actor
incarnation.

Group Controllers maintain desired Group state.

These responsibilities are distinct.

A failure authority may observe an Actor failure and apply failure
policy such as Replace, Stop, Escalate, or Ignore.

A Group Controller independently observes Group policy and current
membership and reconciles when desired state is not satisfied.

For example:

    desired members = 4
    actual members = 3
        -> Group Controller may create one new Actor

A failure policy such as Stop terminates or leaves terminated the failed
Actor incarnation. It does not mean `desired cardinality -= 1` and does
not suppress independent Group reconciliation.

Conversely, a Group membership deficit does not alter the failure policy
of the Actor that terminated.

If both failure policy and Group policy request equivalent replacement
capacity, the runtime may reconcile those intents rather than creating
redundant replacements.

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

For a direct `request`, if Protos can determine that the destination Actor
terminated before accepting the request, the returned Future fails according to
the ordinary communication failure semantics.

If acceptance occurred, or may have occurred and Protos cannot prove otherwise,
termination before a normal reply makes the returned Future fail with
`RequestOutcomeUncertain`. This includes an Actor turn that ends in an unhandled fatal
failure after accepting the request: the caller is not automatically given the
destination's internal failure object as though it were a normal reply, and the
runtime does not pretend that the accepted request had no effects.

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

**CLOSED --- REVISED**

Processes are ephemeral execution capacity, not durable application
identities.

If a Process is known to have terminated, every Actor still hosted by
that Process terminates with it.

This inference requires actual Process termination evidence. Node
unreachability, loss of Cluster membership, routing failure, or partition
does not by itself prove Process death.

If live Actor migration had already completed before Process death, the
migrated Actor is no longer hosted by the failed Process and therefore is
not terminated by that Process failure.

Higher-level mechanisms react independently:

-   Group Controllers may restore desired membership
-   Failure authorities may apply policy
-   Capacity-demand signals may increase
-   External Infrastructure Controllers may provision additional capacity

A newly created Process is new capacity.

It is not semantically the reincarnation of the Process that disappeared,
even if external infrastructure considers it a replacement workload.

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

**CLOSED --- REVISED**

Cluster identity is a logical coordination-domain identity, independent
of the identity or current number of its Nodes.

Nodes may enter and leave while the Cluster remains the same logical
domain. A Cluster may conceptually have zero current Nodes.

No individual Node is required to be the durable identity anchor for the
Cluster.

Persistence of Cluster identity and authoritative/control state across
complete loss of runtime membership is explicit/configurable rather than
intrinsic.

If the defining state is durably preserved, later Nodes may participate
in the same logical Cluster. If all defining state is lost, a later
bootstrap creates a new Cluster identity rather than resurrecting the old
one.

The Cluster is therefore different from Actor, Process, and Node
identities: it represents the logical coordination field in which
ephemeral members participate.

## 64. Loss of Node Membership Does Not Prove Physical Process Death

**CLOSED --- REVISED**

Loss of Node membership removes the Processes hosted by that Node from
the usable capacity of the affected Cluster view.

It does not prove that those Processes have physically terminated.

A Node or its Processes may continue executing while isolated by a
network partition or membership disagreement.

Protos therefore keeps separate:

-   Membership
-   Reachability
-   Physical existence
-   Authority

A Cluster view must not treat Processes behind a lost/unreachable Node as
usable Cluster capacity unless the relevant membership, routing, and
Authority rules permit it.

Known Process death may establish termination of Actors still hosted by
that Process; Node membership loss alone may not.

## 65. Local Execution May Continue Without Higher-Scope Authority

**CLOSED --- REVISED**

Loss of Authority in one scope does not imply loss of unrelated local
execution.

A partitioned or isolated runtime domain may continue operations that do
not require the lost Authority, including purely local Actor execution,
local Futures, local I/O, and other valid local computation.

However, a runtime domain must not perform an operation whose correctness
requires Authority unless it can demonstrate the required currently valid
Authority for that operation's scope.

Conceptually:

    Authority(scope X) lost
        |
        +-- unrelated execution may continue
        +-- non-authoritative work may continue
        |
        `-- operations requiring Authority(scope X) are disabled

Protos does not choose either extreme:

-   A partition does not automatically kill all local computation.
-   Every partition does not automatically become independently
    authoritative.

Being alive or reachable does not imply being authoritative.

## 66. Authority

**CLOSED --- REVISED**

Authority is the exclusive capability required to make an authoritative
decision within a particular decision scope.

For one authoritative invariant and scope, conflicting participants must
not simultaneously possess valid Authority to make incompatible
exclusive decisions.

Authority is distinct from communication capability:

    ActorRef / GroupRef
        = communication capability

    Authority
        = exclusive capability for authoritative decisions

Possessing an ActorRef or GroupRef does not by itself demonstrate
Authority.

An operation requires Authority only when conflicting concurrent
decisions within the same scope could violate an authoritative invariant.

Membership observation, Group membership management, replacement,
placement, routing, and topology operations are not intrinsically
authoritative. Particular forms of those operations require Authority
only when their correctness depends on exclusivity or another explicit
authoritative invariant.

Examples that may require Authority depending on the policy include:

-   Granting an exclusive writer role
-   Enforcing a strict singleton or maximum-authoritative-role invariant
-   Committing a definitive membership decision when conflicting commits
    would be invalid
-   Transferring an exclusive lease or fencing token
-   Other decisions whose conflicting simultaneous validity would violate
    correctness

Observation and ordinary convergent reconciliation need not require
exclusive Authority.

If a runtime domain cannot demonstrate Authority for an operation that
requires it, it must refrain from performing that authoritative operation.
Failure to prove Authority does not automatically stop unrelated work.

Protos does not prescribe the mechanism used to establish Authority.
Possible mechanisms may include quorum, consensus, leader election,
leases, witnesses, external coordination services, or other mechanisms
providing the required exclusivity properties.

## 67. Authority Is Scoped

**CLOSED --- REVISED**

Authority is scoped to the smallest runtime/control domain in which
conflicting authoritative decisions are possible.

Authority is not necessarily Cluster-global, and using the same
abstraction in a Cluster does not force local uses to pay Cluster-level
coordination costs.

Conceptually:

    local Group entirely inside one Process
        -> Process-local Authority if exclusivity is required

    Group coordinated across Processes of one Node
        -> Node-scoped Authority if exclusivity is required

    Group coordinated across Nodes
        -> distributed/Cluster-scoped Authority if exclusivity is required

Different Authority scopes may exist independently for different
responsibilities.

Exclusivity is required within an Authority scope, not across unrelated
responsibilities.

The runtime may therefore distribute or shard control responsibilities
while preserving the rule that conflicting authorities cannot
simultaneously make the same exclusive decision.

Principle:

> Authority cost grows with the coordination scope actually required.

## 68. Controllers Are Ephemeral

**CLOSED --- REVISED**

Controllers are ephemeral control-plane executors.

Correctness must not depend on the lifetime of the particular controller
instance currently performing reconciliation or exercising Authority.

A Group Controller is not part of Group identity.

If a controller disappears, another eligible controller may continue
control operations, acquiring any required Authority according to the
rules of that Authority scope.

Control state needed for continued control must survive or be
reconstructible independently of the controller instance that previously
held it.

For example, Group desired cardinality, availability policy, Group
identity, or other control information that must survive controller
replacement must not exist solely in irreplaceable controller RAM.

Such control information is not intrinsically authoritative merely
because it must survive controller replacement.

The exact mechanism by which control state survives or is reconstructed
remains open.

Principle:

> The controller is cattle too.

## 69. Authority and Control State Are Mechanism-Independent

**CLOSED --- REVISED**

Protos specifies the consistency, exclusivity, and survivability
properties required by Authority and control state, not a mandatory
storage or consensus implementation.

Depending on the active runtime domain and invariant, the required
mechanism may range from ordinary local state to distributed
coordination.

Conceptually:

    single Process
        -> local state may be sufficient

    multiple Processes / one Node
        -> Node-level coordination may be sufficient

    distributed Cluster
        -> stronger coordination may be required

Possible implementations may include replicated consensus state, a
durable store, leases, an external authority service, or a Protos-native
mechanism.

No specific technology such as Raft, etcd, or another consensus/store
implementation is part of Protos semantics at this stage.

No distributed coordination mechanism is required merely because Groups
or multiple Actors exist. Distributed coordination is paid for only when
an active invariant or durability requirement actually needs it.

This preserves pay-as-you-grow.

## 70. External Authority and Infrastructure Mechanisms Do Not Define Protos Semantics

**CLOSED --- REVISED**

Protos may delegate mechanisms used to establish, demonstrate, or persist
Authority and control state to external infrastructure.

For example, an implementation may use:

-   Kubernetes Lease or API primitives
-   Nomad coordination facilities
-   Consul or another coordination service
-   A cloud coordination service
-   A Protos-native mechanism

However, external infrastructure does not define the meaning of Protos
Authority or logical topology.

Conceptually:

    external mechanism
        may establish evidence / persist state / provide raw capacity

    Protos semantics
        define Actor, Group, membership, routing, failure and Authority

An external orchestrator may help discover instances, establish leases,
persist control metadata, or provision capacity.

It does not thereby become the semantic definition of:

-   Actor
-   ActorRef
-   ActorGroup / GroupRef
-   Process
-   Protos Node
-   Protos Cluster
-   Group membership
-   Protos routing
-   Protos failure semantics
-   Protos Authority

For example, a Kubernetes Deployment is not semantically a Protos
ActorGroup merely because it may provision workloads that host Group
members.

External infrastructure disappearance is an observed capacity/failure
event from Protos' perspective, not a direct Protos logical mutation
performed by the orchestrator.

The same Protos program and runtime model should remain valid across
standalone execution, Kubernetes, Nomad, or future infrastructure
environments.

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
