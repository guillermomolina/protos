# Protos Concurrency Model v0.1

Language version: 0.1
Document revision: 200
Status: Draft
Last updated: 2026-09-03
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

A call to `future.then(transform)` creates a distinct continuation task and a
destination Future. The continuation is asynchronous work created by the
activation that calls `then`, and therefore belongs to that activation under the
ordinary structured-concurrency rule unless the destination Future is detached.

Completion of the source Future only makes the continuation runnable. It does not
execute the transformation closure inline, reentrantly, or inside the task or Actor
turn that completes the source Future. This remains true when the source Future was
already terminal when `then()` was called.

The continuation executes as ordinary task work in the Actor/execution domain of
the activation that created it. A resolved source invokes the transformation with
the source value; a failed source fails the destination with the same error without
invoking the transformation; a cancelled source cancels the destination without
invoking the transformation. A Future returned by the transformation is flattened
through the ordinary Future-resolution rule.

Flattening adopts only the returned Future's eventual terminal outcome. It does
not transfer ownership, task identity, or detachment, and it does not create
upstream cancellation: cancelling the destination while it is adopting the
returned Future never cancels that returned Future. Source resolution, failure,
or cancellation is mirrored by the destination. A direct or transitive adoption
cycle fails the destination with the standard `FutureResolutionCycle` error
rather than leaving the cycle permanently pending. `FutureResolutionCycle`
delegates directly to `Error`.

If cancellation of an adopting destination races with completion of the adopted
Future, the first terminal transition of the destination wins and later
completion/cancellation bookkeeping cannot rewrite it.

Cancellation and detachment are downstream-only for this composition edge:
cancelling the destination requests cancellation of the continuation but does not
cancel the source Future, and detaching the destination detaches only the
continuation task. Neither operation changes ownership or lifetime of the source
Future.

The first execution of every newly created asynchronous task is a portable
cancellation-observation boundary before any ordinary Protos code in that task
executes. A cancellation request for a not-yet-started task must make that task
eligible to reach this boundary even when the task was waiting for a semantic
prerequisite that has not become ready. Cancellation does not satisfy, complete,
or otherwise modify that prerequisite.

Therefore, if the destination of `then()` is cancelled while its continuation is
waiting for a still-pending source Future, the continuation becomes
cancellation-runnable without waiting for the source. The request is honored
before inspecting the source outcome or invoking `transform`; the destination
becomes cancelled and `transform` is not invoked. The source Future remains
unchanged.

The same rule applies when the source had already completed but the continuation
had not yet begun its first turn. This prevents scheduler or source-completion
timing from deciding whether a never-started continuation nevertheless performs
ordinary Protos side effects.

Once a task has begun ordinary Protos execution, no additional cancellation
boundary is implied merely by method calls, closure invocation, allocations, loop
back-edges, or other non-suspending execution. The existing explicit-suspension,
resume, and cancellation-aware-operation boundaries remain unchanged.

No additional total ordering is introduced between independent continuations.

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

**CLOSED --- REVISED**

Messages from the same sender to the same concrete Actor preserve FIFO
ordering.

For ordinary Actor messaging, the sender is the **originating Actor
incarnation**, not the individual Actor-local task, Future, activation, or turn
that happens to invoke `send()` or `request()`. All Actor-local work executing inside
one Actor therefore shares that Actor's sender identity for this ordering rule.

For two delivery attempts issued from one Actor incarnation to the same concrete
destination Actor, their order is the Protos-visible invocation order in the
originating Actor. Because Actor-local Protos execution is serialized between
explicit suspension points, this issuance order is well-defined even when
different Actor-local tasks interleave.

A retry that is explicitly initiated after an earlier delivery attempt has
failed, become uncertain, or otherwise requires retry is ordered at the point
that retry attempt is initiated. Retry does not regain the earlier attempt's
former queue position and does not force later already-issued operations to wait
behind an indefinitely retried logical operation. This does not change the
logical message identity or snapshot rules of the communication operation.

Runtime, host, bootstrap, or infrastructure activity that is not itself execution
inside a Protos Actor does not silently acquire the identity of some Actor for
FIFO purposes. Unless another normative domain explicitly defines such activity
as one ordered sender, independently generated operations from those sources have
no same-sender ordering guarantee.

Within one Future or task, normal sequential execution order applies.

No global ordering is guaranteed between unrelated sources of work,
including different message senders, I/O completions, timers,
reactivated Futures, or other independently runnable work.

ActorGroup routing does not introduce Group-wide FIFO ordering. Messages
from the same sender to the same Group may be routed to different Actors
and therefore have no ordering guarantee relative to each other beyond
the ordering guarantees of any concrete Actor that receives them.

The runtime provides an abstract no-starvation guarantee for runnable work,
defined as **weak fairness**.

A work item is runnable for this rule only when all semantic prerequisites for
its next Protos turn are satisfied. Examples include an Actor-local task
continuation whose awaited condition has completed and an accepted mailbox
message eligible for dispatch. Work still waiting for I/O, a Future, a timer,
backpressure relief, routing, acceptance, or another semantic prerequisite is
not runnable merely because it exists.

If a work item belonging to a live Actor remains continuously runnable, is not
cancelled or otherwise made ineligible, and execution repeatedly returns to a
runtime scheduling point capable of selecting work in that item's scheduling
scope, that work item must eventually receive an execution turn. Later-arriving
runnable work must not postpone such a continuously runnable item forever.

Admission/backpressure liveness is a separate obligation from runnable-task
fairness. A pre-acceptance `send()` or `request()` operation that is waiting
only for delivery-path admission is not runnable Actor work, but it must not be
starved merely because later delivery attempts keep arriving.

A pending delivery operation is **continuously admission-eligible** when, from
some point onward, all semantic prerequisites other than the availability of an
admission opportunity remain satisfied in its current routing/admission scope,
the operation is not cancelled or otherwise terminal, and admission
opportunities for that scope continue to occur.

If a delivery operation remains continuously admission-eligible and admission
opportunities repeatedly occur in a scope capable of admitting it, the runtime
must eventually either:

- advance that operation across the applicable acceptance boundary; or
- make the operation terminal for an independently defined semantic reason,
  such as cancellation, destination termination, routing failure, or delivery
  uncertainty.

Later-arriving operations must not consume recurring admission opportunities in
a way that postpones a continuously admission-eligible operation forever.

For operations from the same originating Actor incarnation to the same concrete
Actor, this admission rule composes with same-sender FIFO: a later still-live
delivery attempt must not cross concrete-Actor acceptance ahead of an earlier
still-live attempt from that sender merely because both experienced
backpressure. Cancellation or terminal failure of the earlier operation removes
that ordering constraint; an explicit retry takes its ordinary new issuance
position.

ActorGroup routing does not gain Group-wide FIFO from this rule. A Group
operation may be routed or re-routed according to the existing Group rules, and
different operations may still reach different members. The fairness obligation
only prevents a continuously eligible pending operation from being bypassed
forever while its applicable routing/admission scope repeatedly has an
opportunity to make progress.

This is weak admission fairness, not a throughput or latency contract. It does
not require equal producer shares, round-robin admission, a fixed queue
discipline, a bounded number of bypasses, a wall-clock deadline, or progress
while no compatible admission opportunity occurs. Operations whose eligibility
repeatedly appears and disappears do not receive a strong-fairness guarantee.


The same weak-fairness obligation applies to selection among live Actors that
remain continuously runnable when execution repeatedly returns to a scheduling
point capable of selecting among those Actors. An implementation may organize
scheduling hierarchically, use work stealing, per-Actor queues, priorities that
preserve this guarantee, or another mechanism; those choices are not observable
semantics.

Weak fairness does not require equal CPU shares, round-robin order, a bounded
number of intervening turns, a wall-clock latency bound, or any ordering between
otherwise unrelated runnable items. A runtime may execute one runnable item many
times before another provided the latter is not postponed forever.

Work that repeatedly becomes runnable and non-runnable is not covered by a
strong-fairness guarantee merely because it becomes runnable infinitely often.
No such strong-fairness guarantee is currently part of Core.

This liveness rule assumes that Protos execution continues to regain applicable
scheduler control. It cannot make progress while a currently executing
non-preemptible Protos segment monopolizes the only carrier capable of running
the affected scope, nor can it override external host or operating-system
failure to schedule the Protos process itself.

The language does not specify a particular round-robin algorithm, time quantum,
scheduler queue structure, carrier mapping, or work-stealing policy.

A Protos computation that runs indefinitely without completing or reaching a
suspension point may monopolize its Actor and may occupy its current carrier
indefinitely. Arbitrary preemption of ordinary Protos execution is not currently
part of the model.

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

Host/Process capabilities are not implicitly inherited from the creator.
A new Actor receives only capabilities explicitly provisioned at creation
or explicitly delegated later according to the applicable capability and
Actor-transfer semantics. The runtime may provision an Actor-local proxy
or equivalent routed capability rather than sharing a live mutable host
resource object.

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

If initialization fails with an unhandled error, that is an unhandled fatal
failure of that Actor incarnation. The Actor never reaches READY and instead
terminates. Its failure authority observes the failure and applies the ordinary
Actor-failure policy.

Initialization failure does not create a special message-delivery universe.
Operations not yet accepted by the Actor remain governed by the ordinary
pre-acceptance routing, failure, cancellation, and uncertainty rules.

Any message already accepted into the INITIALIZING Actor's ownership but not yet
dispatched is an accepted-but-not-completed operation lost with that Actor
incarnation. It is never dispatched after the initialization failure and is never
transparently transferred or replayed to a replacement Actor. Its sender-visible
outcome follows the ordinary Messages Across Actor Failure rules; in particular,
an accepted `request()` that cannot produce a normal reply fails with
`RequestOutcomeUncertain`.

A replacement created by failure policy is a fresh Actor incarnation. It starts
with its own initialization and mailbox state and does not inherit accepted
messages from the failed incarnation.

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

**CLOSED --- REVISED**

`send()` represents one-way Actor communication.

It returns a local identity-bearing communication operation object,
provisionally called `SendOperation`.

A SendOperation represents the logical delivery operation and may expose
information or operations concerning:

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

For Group-addressed communication, routing may choose or re-choose a
concrete Actor only before that Actor accepts the operation.

Acceptance occurs when a concrete Actor takes ownership of the message
for processing. Acceptance does not imply that handler execution has
started or completed.

Once accepted, the message belongs to that Actor and is not
transparently replayed or rerouted if that Actor subsequently fails.

If the runtime cannot determine whether the acceptance boundary was
crossed, the operation enters an explicit uncertain state rather than
assuming either delivery or non-delivery. Such uncertainty does not
authorize transparent replay.

The exact SendOperation API and status set remain open.

## 14. request()

**CLOSED --- REVISED**

`request()` represents request/reply communication.

It returns a Future.

The delivery portion of `request()` has exactly the same semantics as
`send()`. `request()` additionally establishes an ephemeral reply capability
and a Future that represents the eventual reply.

Conceptually:

    send(message) = delivery
    request(message)  = same delivery + reply Future

The underlying delivery operation need not be separately exposed to the
caller of `request()`.

A `request()` Future may remain pending before a concrete Actor has been
selected or accepted the message. Its resolution represents the reply,
not merely successful delivery.

The runtime automatically manages the ephemeral reply capability and any
required correlation, routing, and reply delivery.

The normal final result of the Actor handler becomes the reply value.

Reply formation is an Actor-boundary value transfer, not generic local Future
resolution. The handler's normal result is first interpreted as the value graph
to be transferred under the Pass-by-Value Between Actors rules. Only a
successfully formed transferable reply value may then resolve the caller's
request Future.

Consequently, if the handler's normal result is itself a `Future`, that result is
not automatically flattened, awaited, adopted, or linked to the caller's request
Future. `Future` is non-transferable across Actor boundaries, so such a normal
handler result fails the request Future with `NonTransferableValue` under the
ordinary reply-transfer rule.

This is intentionally different from local `resolveFuture(destination, sourceFuture)`
flattening. Local Future adoption never creates a cross-Actor adoption edge and
does not override Actor transferability. A handler that wants its eventual local
Future result to become the reply must explicitly observe that Future in the
handler's own execution, for example through the ordinary explicit suspension
operation `.value()`, and return the resulting transferable value.

One `request()` produces one logical response.

Streaming or multi-response communication will use a separate
abstraction rather than stretching `request()` into a streaming protocol.

Because `request()` exposes an ordinary four-state Future rather than the richer
`SendOperation`, communication uncertainty must map deterministically into that
Future instead of creating a fifth Future state.

This concurrency domain therefore defines the standard error prototype
`RequestOutcomeUncertain`, delegating directly to `Error`. It denotes that the request
cannot produce its normal reply and Protos cannot guarantee that the remote
request had no effect. The request Future completes `failed(RequestOutcomeUncertain)`
when, for example:

- acceptance may or may not have occurred and the runtime cannot determine which;
- acceptance is known to have occurred but the destination terminates before a
  reply is produced;
- a reply becomes unavailable after remote handling may already have produced
  effects and the caller cannot determine the handler's normal result.

`RequestOutcomeUncertain` describes caller-visible uncertainty, not an assertion that
the request definitely executed and not permission for transparent retry.
Libraries may build idempotency, deduplication, or application-level retry policy
above it.

A failure known to have prevented concrete-Actor acceptance is an ordinary failed
Future according to the communication failure rules and is not
`RequestOutcomeUncertain`. Conversely, once acceptance is known to have occurred,
failure to obtain a normal reply must not be reported as though non-delivery were
proved.

Cancellation has a separate deterministic mapping. Before concrete-Actor
acceptance, cancellation may win the delivery race; if the runtime establishes
that acceptance did not occur, the request Future may become `cancelled`. Once
acceptance is known, cancellation can abandon the caller's wait/reply capability
and the request Future may become `cancelled`, but it cannot unsend the request or
imply that remote effects did not occur. A later reply to an already-cancelled
request is discarded and cannot change the terminal Future state.

If cancellation races with delivery and the runtime cannot determine whether
acceptance occurred, the Future fails with `RequestOutcomeUncertain` rather than
becoming `cancelled`, because `cancelled` must not erase delivery uncertainty.

A reply and cancellation race is resolved by the first terminal transition of the
request Future. Once resolved, failed, or cancelled, later reply/cancellation events
cannot rewrite that terminal state.

A wait timeout affects only the wait under the general timeout rule. It does not
itself complete, fail, or cancel the request Future, and it does not imply that remote
work did not execute.

## 15. send() and request() Share Delivery and Dispatch

**CLOSED --- REVISED**

`send()` and `request()` use the same message-delivery semantics and the same
Actor message dispatch mechanism.

For `send()`, the handler result is ignored.

For `request()`, the handler result resolves the caller Future.

There are no separate send handlers and request handlers.

The same snapshot, routing, acceptance, backpressure, cancellation,
uncertainty, and failure rules apply to the delivery portion of both
operations.

## 16. Pass-by-Value Between Actors

**CLOSED --- REVISED**

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
-   GroupRef: transferable as a special communication capability
-   Closure: not transferable
-   Future: not transferable
-   ExecutionContext: not transferable
-   Socket: not transferable
-   Open file: not transferable
-   Native resource: not transferable
-   Java object: not transferable by default

ActorRef and GroupRef are deliberately transferable because they provide
communication capabilities rather than direct access to another Actor's or
Group's mutable runtime/control state.

Transferring either reference preserves the same logical communication target
and the capability restrictions carried by that reference. Transfer does not
amplify authority, expose the target's mutable heap/control state, or turn the
reference into ownership of its target.

Closures are not transferable because they capture actor-local lexical
execution contexts by reference.

### Transfer graph closure and failure

Transferability is a property of the complete logical value graph, not only of
the top-level argument object.

For a copied ordinary object, the transfer graph includes:

- every value stored in a local slot of that object; and
- the object's immutable delegation-parent edge.

Traversal is transitive. The same source object encountered more than once maps
to the same logical destination object, so cycles and aliasing are preserved.
Two distinct source objects never become one destination object merely because
their contents happen to be equal.

An edge to a value whose normative semantics permit cross-Actor sharing or
capability transfer is handled by that value's own rule rather than by copying
the referent's mutable implementation state. `ActorRef` and `GroupRef` are the
Core communication-capability examples: the capability crosses, not the target
Actor heap or Group control/membership state. Likewise, semantically immutable
standard-prelude objects may be physically shared when
the existing prelude-sharing rule permits it; that optimization must remain
unobservable.

Every other reachable edge must itself be transferable. In particular, an
ordinary object is not transferable merely because its own local slots are
copyable if its delegation parent, or any value transitively reachable through
its local slots, is non-transferable.

This concurrency domain defines the standard error prototype
`NonTransferableValue`, delegating directly to `Error`.

Message snapshot formation is atomic at the language level. `send()` and
`request()` must validate the complete logical transfer graph before the message
can cross the concrete-Actor acceptance boundary. If any required reachable
value is non-transferable:

- the communication invocation signals `NonTransferableValue` in the caller;
- no partial message snapshot becomes deliverable;
- no destination Actor accepts any prefix or fragment of that message; and
- no source object is detached, invalidated, or otherwise mutated merely because
  transfer validation was attempted.

The same rule applies to the reply value of `request()`: if the handler's normal
result cannot form a transferable reply graph, the request Future fails with
`NonTransferableValue`; no partial reply is exposed to the requester.

Reply transferability is checked before the caller's request Future is resolved.
In particular, a handler-returned `Future` is a non-transferable reply value, not
an instruction to invoke generic Future flattening across the Actor boundary.
No runtime may create an adoption edge from the caller's request Future to a
callee-local Future merely because both abstractions use Future objects.

Implementations may validate eagerly, serialize incrementally, use copy-on-write,
or use another representation, but must behave as though the complete logical
graph was validated atomically against these rules.

The destination Actor executes code that it already owns rather than
receiving arbitrary executable closures carrying another Actor's lexical
environment.

## 17. Message Snapshot Time

**CLOSED --- REVISED**

A message captures its logical value snapshot when the caller invokes
`send()` or `request()`.

The snapshot is fixed before routing, member selection, backpressure,
capacity waiting, or acceptance.

Delayed routing or pre-acceptance rerouting does not change the logical
snapshot.

This snapshot rule is semantic. It does not prescribe when or how the
runtime physically copies memory; copy-on-write or other optimizations
may defer physical work while preserving the original logical value.

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

**CLOSED --- REVISED**

Cancellation is cooperative.

Protos does not arbitrarily interrupt Actor code in the middle of an
instruction by injecting asynchronous exceptions.

Cancellation requests are observed at portable cancellation boundaries,
not at implementation-selected runtime safepoints.

Every explicit suspension point is a mandatory cancellation observation boundary.
A pending request is observed before the task suspends or, if suspension has
already occurred, before that task resumes ordinary Protos execution. An operation
whose normative contract is cancellation-aware may additionally observe a request
while its underlying work is pending, subject to that operation's commitment and
effect rules.

A cancellation request made while a task is already suspended must make that
task eligible to resume for cancellation without waiting for the condition that
originally suspended it to complete. Otherwise structured cancellation could
wait forever for a child suspended on a Future, timer, I/O operation, or other
condition that never becomes ready.

Likewise, cancellation of a task that has been created but has not yet begun its
first ordinary Protos instruction must not wait indefinitely for an unmet
pre-start scheduling prerequisite. The task becomes eligible to reach its
mandatory first-execution cancellation boundary without that prerequisite being
made true. Making the task cancellation-runnable is idempotent and does not
authorize duplicate execution, preemption of currently executing ordinary code,
or satisfaction/cancellation of an unrelated prerequisite or upstream Future.

This cancellation wake-up resumes only the waiting task's control flow. It does
not by itself cancel, fail, complete, or otherwise modify the Future or other
condition being awaited, and it does not request cancellation of an upstream
producer. Multiple tasks may wait on the same Future without cancellation of one
waiter affecting the others or the Future itself.

The existing resume boundary remains authoritative. If a task was suspended and
its cancellation request is pending when it is selected to resume into Protos,
cancellation is honored before the suspended operation can return a successful
result or execute further ordinary Protos code. Thus, if awaited completion made
the task runnable but cancellation became pending before that resume boundary,
the consumer observes cancellation; the awaited Future retains its own terminal
outcome unchanged. If the task already crossed the resume boundary and completed
the suspension operation before cancellation was requested, that completed
observation is not retroactively rewritten.

This is a prompt cancellation guarantee for the suspended consumer, not upstream
cancellation propagation. Producer-specific cancellation remains explicit
through that producer's own Future or capability contract.

Ordinary non-suspending execution does not acquire hidden cancellation points from
method calls, allocations, loop back-edges, interpreter/JIT polls, garbage
collection, host calls, or similar runtime machinery. CPU-bound Protos code that
does not explicitly suspend or enter a cancellation-aware operation may therefore
complete normally after cancellation was requested.

An implementation may use additional internal polling or carrier interruption only
when it cannot change the Protos-observable point at which cancellation takes
effect.

Cancelling a Future requests cancellation of its work.

`Future.cancel()` is an idempotent request operation and returns the same Future
object on every call. If the Future is already terminal, `cancel()` is a no-op
with respect to Future state and outcome and still returns that Future. For a
pending Future, returning normally means only that the cancellation request was
recorded; it does not assert that cancellation has already taken effect or that
the eventual terminal state must be `cancelled`.

Cancelling a SendOperation attempts to prevent further delivery while
that remains safe.

Before concrete-Actor acceptance, cancellation may remove or stop the
operation where the runtime can still do so safely.

If the destination has already accepted the message, cancellation cannot
unsend it.

If the runtime cannot determine whether acceptance occurred,
cancellation may end in an uncertain delivery state rather than assuming
that the message was cancelled before delivery. For `request()`, whose public result
is an ordinary Future and therefore has no uncertain terminal state, this outcome
is represented by failure with the standard `RequestOutcomeUncertain` error.

Principle:

> Cancellation never reverses effects that have already occurred.

## 24. Structured Concurrency

**CLOSED --- REVISED**

The existing structured-concurrency semantics for Futures remain.

Asynchronous child work created inside an execution context is owned by
that context by default unless explicitly detached.

Structured ownership bounds child lifetime but does not implicitly observe child
results. When an owner reaches otherwise normal completion, it waits for every
non-detached child to become terminal. A child's failed or cancelled terminal
state does not by itself fail or cancel that normally completing owner. Failure
or cancellation becomes observable to owner code only through the ordinary
Future observation operations, such as `value()`.

This deliberately avoids hidden "unobserved failure" or "failure consumed" state:
whether an owner completes normally cannot depend on whether some previous read of
a child Future happened to observe its failure. The Future remains an ordinary
eventual-result object whose terminal outcome is stable and may be observed more
than once.

Cancellation unwind includes the task's applicable `ensure` cleanup.
Once a cancellation request has been honored and has begun that unwind, the same
request is not re-delivered at suspension boundaries reached by cleanup for that
unwind. This permits resource cleanup to suspend without being immediately
cancelled by the cancellation it is already handling. If cleanup fails, that
failure replaces the cancellation transfer and the task fails; if cleanup
completes, cancellation continues and the Future becomes cancelled only after
cleanup is complete. This rule does not create a general user-visible
cancellation-mask facility.

Detachment removes a task from the structured lifetime of its creating
activation only. `Future.detach()` always returns the same Future object and is
idempotent. On a still-pending task-backed Future that is not already detached,
it removes that task's activation-ownership edge. Repeated detachment is a
state-preserving no-op.

A non-task-backed Future, including one produced directly by an I/O facility,
has no structured task ownership edge to remove; `detach()` on such a Future is
therefore a state-preserving no-op. Calling `detach()` on an already terminal
Future is also a no-op. These cases do not signal merely because no detachable
ownership edge remains.

Detachment never changes Future terminal outcome, requests cancellation, alters
an I/O producer's lifecycle, or manufactures a new owner. It does not move
Actor-local work out of the Actor execution domain, promote it to Process-global
work, or give it an independent Actor-like lifecycle. A detached Actor-local task
may outlive the activation that created it, but it cannot outlive the Actor
incarnation whose mutable state and serial execution domain it uses.

When termination of an Actor incarnation begins while its hosting runtime remains
able to execute Protos cleanup, every pending Actor-local task belonging to that
incarnation receives a cooperative cancellation request, including detached
tasks.

Termination also records a cancellation request on every still-pending
non-task-backed Future representing an asynchronous operation initiated by that
Actor incarnation. This includes, when applicable, I/O-operation Futures and
communication-operation Futures such as `request()`. Actor termination is
therefore a cancellation-request boundary for outstanding Actor-originated
asynchronous operations; it is not permission for such operations to continue
or disappear according to implementation accident.

This Actor-lifecycle request does not invent stronger cancellation than the
producer already provides. An operation that can still satisfy its ordinary
cancellation contract may become cancelled. An operation that has already crossed
its commitment/acceptance boundary keeps the effects and outcome constraints of
its own protocol; Actor termination cannot roll those effects back, convert them
into pre-commit cancellation, or transparently retry them.

The incarnation stops accepting or dispatching new ordinary message work. Task
execution that occurs thereafter is limited to reaching the existing portable
cancellation boundaries and performing the cancellation unwind and applicable
`ensure` cleanup required to terminate those tasks.

A detached task is never silently re-parented to the RootActor, Process, a
replacement Actor, or another runtime scope merely so it can continue running
after its Actor terminates. Its Future follows the ordinary cancellation-unwind
rule: successful cleanup permits the Future to become `cancelled`; a cleanup
failure makes that Future `failed` with the cleanup error. Replacement creates a
fresh Actor execution domain and inherits none of these tasks.

Actor termination waits for the required cancellation unwind of Actor-local
tasks, but does not generally wait for every non-task-backed producer Future to
become terminal. Once the cancellation request has been recorded, any residual
producer/backend work that cannot yet terminate safely remains under
runtime/producer custody and may proceed only as allowed by that operation's
existing commitment and cancellation rules. No continuation of the terminated
Actor is resumed merely to observe such a later outcome.

Termination cleanup may itself suspend and therefore has no bounded-time liveness
guarantee. Catastrophic loss of the Process, runtime, or underlying execution
capacity may make further Protos cleanup impossible; this does not authorize the
task to continue in another Actor or execution domain. The portable guarantee
here governs semantic Actor termination while the runtime remains capable of
executing the required cleanup.

When an explicit isolated parallel operation creates work whose result
is represented by a Future, that work participates in the same
structured ownership, waiting, failure, and cooperative-cancellation
model unless an API explicitly provides different ownership semantics.
Its isolated execution does not turn it into an Actor and does not give
it Actor identity, a mailbox, independent supervision, or independent
lifecycle semantics.

Actor creation does not automatically establish the same ownership
relationship.

Structured concurrency therefore governs Future-producing child work,
including isolated parallel computations, but not Actor lifetime in
general.

## 24A. Actor Termination Request and Graceful Stop

**CLOSED**

Core distinguishes a **known Actor termination request** from failure, unreachability,
and ordinary message handling. This section closes the lifecycle semantics of a
graceful stop without standardizing a particular public `stop` spelling, return type,
timeout option, or administrative API.

A graceful-stop request establishes one irreversible lifecycle cutover for the
target Actor incarnation. Once that cutover is established:

- the Actor is terminating and can never return to READY;
- no new ordinary message delivery may cross that Actor's concrete-Actor acceptance
  boundary;
- no new ordinary Actor turn may begin;
- no replacement Actor inherits this lifecycle or this ActorRef.

Operations that had not crossed concrete-Actor acceptance before the cutover remain
pre-acceptance operations. They follow the ordinary routing, cancellation, failure,
and Group re-routing rules. In particular, Group-addressed work may select another
member only while Group routing still owns the operation and no concrete Actor has
accepted it.

Operations already accepted by the Actor but whose handler turn has not begun are
not drained as new ordinary work after the cutover. They are accepted-but-not-
completed operations lost with that incarnation and follow the same sender-visible
failure/uncertainty rules as accepted work lost by Actor failure. In particular, an
accepted `request()` that cannot produce its normal reply fails with
`RequestOutcomeUncertain`. Graceful stop therefore never fabricates proof of
non-execution merely because a handler had not started.

If an ordinary Actor turn is executing when the cutover is established, Protos does
not asynchronously preempt arbitrary non-suspending code. That turn may continue
until it either completes normally or reaches an existing portable cancellation
boundary. If it completes normally before observing termination cancellation, its
already-defined effects and, for an accepted `request()`, its normal transferable
reply remain valid. If it reaches a cancellation boundary first, the existing
Actor-termination cancellation and `ensure`-cleanup rules apply and no further
ordinary code from that turn executes.

Establishing graceful stop requests cancellation of all remaining Actor-local tasks,
including detached tasks, and of all still-pending non-task-backed asynchronous
operations initiated by that Actor, exactly as defined by Structured Concurrency.
Producer-specific commitment and acceptance boundaries remain authoritative:
graceful stop does not undo committed I/O, unsend accepted communication, close
Process-owned resources, or strengthen an operation's cancellation contract.

The Actor reaches TERMINATED only after every Actor-local task that the runtime is
still semantically required to clean up has completed its cancellation unwind and
applicable `ensure` cleanup. Graceful stop does not generally wait for
non-task-backed producer Futures whose backend work must continue under an
already-committed operation contract; such residual work remains under
runtime/producer custody and cannot resume ordinary code in the terminated Actor.

Cleanup has no bounded-time guarantee. A task that never reaches a cancellation
boundary, or cleanup that suspends forever, may therefore delay graceful termination
forever while the runtime remains otherwise operational. A timeout on an API that
waits for graceful termination is only a wait policy unless that API separately
defines a stronger escalation mechanism; timeout alone does not mutate the Actor
into a different semantic kind of termination.

Repeated graceful-stop requests for the same live incarnation are idempotent with
respect to lifecycle: they observe the same termination cutover and cannot create a
second shutdown sequence, reopen acceptance, or cause already-committed effects to
be replayed. Exact request-operation object identity, administrative API shape, and
whether callers can wait on a dedicated stop operation remain API design questions.

A graceful stop is not Actor failure. It does not by itself invoke failure policy as
though an unhandled fatal error occurred. Group desired-state reconciliation remains
independent: if a Group still requires capacity, its controller may create a new
member, which is a new Actor incarnation with a new ActorRef.

## 24B. Actor Lifetime Is Not Reachability Garbage Collection

**CLOSED**

Actor lifetime is explicit runtime lifecycle, not ordinary object reachability.

A live Actor incarnation is not automatically terminated merely because the
runtime can no longer find an ordinary Protos reference, `ActorRef`, local
variable, message payload, Group membership entry, monitor, discovery binding,
or other application-level reference that currently names it.

Likewise, loss of the last presently known `ActorRef` is not evidence that the
Actor is unreachable from all current or future runtime participants. In a
distributed system such a proof would require global coordination, would race
with in-flight capability transfer and discovery, and would make Actor lifetime
depend on implementation-specific reference tracking.

Therefore Core v0.1 defines no reachability-based Actor garbage collection and
no idle-time or memory-pressure rule that silently terminates a live Actor
incarnation. An implementation must not reclaim a live Actor by treating it as
ordinary garbage, nor may it synthesize graceful stop, fatal failure, or
replacement merely because the Actor appears unused.

A live Actor ends only through an already-defined lifecycle cause, including an
explicit termination request, unhandled fatal failure and resulting policy,
known termination of the containing Process, or another future mechanism whose
normative contract explicitly terminates that incarnation.

This rule does not require terminated Actor implementation state to remain in
memory forever. Once an Actor is TERMINATED, the runtime may reclaim its private
heap, mailbox storage, task bookkeeping, routing state, monitoring metadata,
tombstones, or other implementation data as soon as doing so cannot change any
remaining Protos-observable behavior.

In particular, an `ActorRef` to a terminated incarnation must continue to denote
that terminated incarnation and must never retarget to a replacement, but the
runtime need not preserve the terminated Actor's former mutable heap merely to
implement that reference. It may represent terminal identity using compact
metadata, distributed routing knowledge, a tombstone, or any other mechanism
that preserves the existing ActorRef, communication-failure, monitoring, and
identity semantics.

The amount, placement, retention duration, and compaction strategy of such
post-termination metadata are implementation choices except where another
normative contract makes particular information observable.

This distinction preserves both sides of Protos' resource model:

- live semantic resources are not destroyed by hidden reachability heuristics;
- dead implementation storage is not kept alive merely because the language
  exposes stable identity for a terminated Actor.

If a future higher-level abstraction wants Orleans-like idle activation
collection, virtual identity, passivation/reactivation, or durable service
activation, it must define that lifecycle explicitly. Such an abstraction must
not retroactively change the semantics of a concrete Protos Actor incarnation
or `ActorRef`.

## 24C. Unhandled Errors Are Fatal to the Actor Incarnation

**CLOSED**

Core does not define a separate taxonomy of "fatal handler errors" and
"non-fatal handler errors". The semantic distinction is instead whether an
`Error` is handled before it escapes the current Actor turn.

If an `Error` escapes the outermost dynamic error-handling boundary of an
ordinary Actor turn, that is an **unhandled fatal failure of that Actor
incarnation**. This rule applies uniformly to message-handler turns and other
ordinary Actor-local task turns. The Actor enters failure termination, its
failure authority observes structured failure information, and the already
defined Actor-failure, cancellation, accepted-work-loss, request-uncertainty,
cleanup, replacement, and ActorRef rules apply.

An `Error` that is handled within the turn is not an Actor failure merely
because it occurred. After the handler resumes, substitutes a value, retries,
transforms the error, or otherwise completes according to the ordinary Protos
error semantics, Actor execution continues normally unless the resulting code
itself triggers another lifecycle cause.

This rule does not make asynchronous child-task failure implicitly fatal to the
Actor. A distinct asynchronous task records its unhandled error in its Future
under the existing Future rules. Such a failure affects the Actor incarnation
only if ordinary Protos execution later observes that failed Future and the
re-signaled `Error` then escapes an Actor turn unhandled.

Cancellation is not converted into an Actor fatal error by this rule.
Cancellation continues to follow the existing cooperative cancellation and
cleanup semantics. Likewise, communication failure, timeout, request
uncertainty, or an I/O-operation failure only becomes an Actor fatal failure if
it is represented as an `Error` in ordinary Actor execution and that `Error`
escapes the Actor turn unhandled.

For `send()`, an unhandled handler error therefore fails the destination Actor
incarnation even though `send()` has no reply result. For `request()`, the same
destination failure occurs, and sender-visible outcome remains governed by the
existing acceptance rule: once the request was accepted, failure before a
normal reply yields `RequestOutcomeUncertain`; the destination's internal error
object is not sent to the requester as an implicit reply.

Initialization remains the same rule applied to the initialization turn:
an unhandled initialization `Error` is fatal to that Actor incarnation and the
Actor never reaches READY.

Core does not assign special fatality to particular `Error` delegation
subtrees, names, implementation exceptions, Java throwable classes, resource
errors, arithmetic errors, lookup errors, or programmer-defined errors. A
future abstraction may define an explicit lifecycle mechanism that terminates
an Actor, but ordinary error fatality remains based on unhandled escape rather
than an implementation-specific error whitelist.

This choice preserves local reasoning: application code may recover from any
ordinary Protos `Error` it intentionally handles, while an error that leaves a
turn without a defined recovery path cannot silently preserve possibly
inconsistent Actor-local mutable state.

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
`PROTOS_LANGUAGE_SPEC.md` and `PROTOS_RUNTIME_SEMANTICS.md`. This
section states only the Actor isolation and ownership consequences that
the concurrency model depends on.

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

**DIRECTION CLOSED, API AND MECHANISMS OPEN**

Protos provides a runtime capability for explicit CPU-parallel
computation that does not require the programmer to create persistent
Actors merely to use multiple CPU cores.

This capability fills the semantic gap between Actor-local cooperative
Future/task execution and persistent Actor isolation.

It does not introduce arbitrary shared mutable Protos memory and does
not weaken the Actor turn rule.

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

The fundamental public abstractions remain Future and Actor unless later
API design demonstrates that a separately observable parallel-task
object is necessary.

A parallel operation may therefore return a normal Future representing
its eventual result. The fact that the computation is eligible to run in
parallel is an execution property of the explicitly requested operation,
not a new meaning silently attached to every Future.

In particular, ordinary `closure.future()` semantics are not changed by
this design. Actor-local Future work remains serialized with other
Actor-local Protos execution according to the normal Actor turn model.

The runtime may use internal concepts such as parallel jobs, work items,
worker pools, region capabilities, or work-stealing queues. Such concepts
are implementation machinery unless separately standardized later.

### 71.2 Explicit Isolation Boundary

Parallel execution crosses an explicit semantic isolation boundary.

Code executing in isolated parallel computation must not receive direct
mutable aliases into the caller Actor's object graph, execution context,
module context, `this`, return home, dynamic handlers, pending Futures,
or other Actor-local mutable runtime state.

A normal Closure captures lexical execution contexts by reference.
Parallel execution must not silently redefine that Closure to capture by
value merely because a parallel API was invoked.

Therefore the exact parallel bootstrap/callable representation remains
open, but it must preserve existing Closure semantics. Safe designs may
include explicit value inputs, a restricted callable representation,
runtime validation of capture safety, or another mechanism that does
not transform ordinary by-reference lexical captures into hidden
cross-boundary mutable access.

### 71.3 Value and Snapshot Semantics

Values supplied across the isolated parallel boundary have logical
value/snapshot semantics analogous to other Protos isolation boundaries.
For mutable values, the parallel computation must observe the logical
input state established by the parallel operation rather than a live
mutable alias into the caller Actor.

The exact snapshot point is defined by the eventual parallel API, but it
must be explicit and deterministic from that API's invocation semantics;
delayed worker scheduling must not cause the input to drift with later
Actor-local mutations.

Results cross back by value. Completion, failure, or cooperative
cancellation resolves the corresponding Future according to the normal
Future model.

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

### 71.5 Exclusive Mutable Partitioning

Parallel algorithms may require several CPU cores to modify disjoint
parts of a large value efficiently. Protos may support this without
opening arbitrary shared mutable memory.

The governing rule is:

> Physical storage may be shared, but two parallel computations must not
> simultaneously hold mutable authority over the same logical state.

A suitable Buffer, Array, or future partitionable value may therefore be
split into logically disjoint writable regions. Several parallel
computations may operate simultaneously on different regions while the
runtime guarantees that the writable regions do not overlap.

Conceptually:

    one large physical backing
        |
        +-- region A -> exclusive parallel writer A
        +-- region B -> exclusive parallel writer B
        +-- region C -> exclusive parallel writer C

The programmer is not required to introduce mutexes, atomics, volatile
state, memory ordering, or general-purpose borrow checking merely to use
this model.

The exact region/partition representation, eligibility rules, alias
validation, merge semantics, and surface API remain open.

### 71.6 Library-Level Parallel Patterns

High-level parallel algorithms should normally be library facilities
built on the minimal runtime guarantees rather than separate language
primitives or separate fundamental task kinds.

Examples may include:

-   Parallel map
-   Parallel filter
-   Parallel reduce
-   Parallel search
-   Parallel sort
-   Parallel iteration
-   Partitioned Buffer/Array processing
-   Parallel pipelines

The exact names and APIs are not decided by this ledger.

A standard or third-party library may choose chunking, reduction trees,
partition strategy, batching, or algorithm-specific policy while the
runtime enforces the underlying isolation and scheduling guarantees.

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

### 71.8 Failure and Cancellation

Failure of an isolated parallel computation fails its result Future
according to normal Future error semantics. It does not by itself invoke
Actor supervision or Actor replacement semantics because the parallel
work is not an Actor.

Cancellation is cooperative and follows Future structured-concurrency
rules. The exact safe points and runtime cancellation mechanics remain
implementation/API details.

If the owning structured context or Actor terminates, outstanding
parallel child work follows the corresponding Future ownership and
cancellation rules. A completed parallel result does not acquire an
independent lifetime merely because it was computed on another CPU
carrier.

### 71.9 Locality and Distribution

The purpose of this facility is efficient CPU parallelism without
requiring persistent Actor structure.

The initial semantic direction does not require isolated parallel work
to become a distributed execution abstraction. Process-local execution
is sufficient to realize the core benefit and avoids imposing remote
placement, discovery, delivery, and failure semantics on fine-grained
parallel computation.

A future explicit remote-compute facility may reuse compatible value and
isolation rules, but remote placement is not implied by the existence of
parallel execution and remains open.

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

## 72. Standard Prelude Sharing

**CLOSED**

The standard prelude is shared between Actors and is frozen. Freezing is
shallow, so freezing the prelude does not by itself make arbitrary
mutable objects referenced by its slots safe to share between Actors.

Rule:

> Any Protos object physically shared between Actors through the standard
> prelude must be semantically immutable for the duration of that
> sharing. Mutable Protos state reachable through standard facilities
> must be Actor-local.

Consequences:

-   The prelude itself may be physically shared, and its slots may refer
    to immutable Protos objects.
-   A prelude slot must not let two Actors share mutable Protos state.
-   Mutable standard-library or runtime state — such as an Actor's
    module cache and module instances — belongs to the Actor that uses
    it.
-   The implementation may physically share immutable implementation
    artifacts such as parsed syntax, bytecode, machine code, immutable
    metadata, and immutable constant data where the sharing is
    semantically unobservable.

The existing rule that freeze is shallow is unchanged: no deep freeze is
introduced. Actor isolation is not weakened, and implementations are not
required to duplicate immutable data unnecessarily.

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
-   Whether Task should become observable
-   Exact isolated parallel-execution API and bootstrap representation
-   Parallel callable/capture-safety validation mechanism
-   Parallel snapshot-point API details
-   Immutable-sharing eligibility and representation for parallel work
-   Exclusive mutable partition/region API and representation
-   Partition overlap/alias validation mechanism
-   Parallel map/filter/reduce/sort/iteration standard-library APIs
-   Parallel scheduling, work-stealing, and granularity heuristics
-   Nested-parallelism admission and fairness
-   Interaction between isolated parallel work and SIMD/vectorization
-   Whether remote isolated parallel execution is ever supported
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
-   Failure-authority API
-   Process failure detection mechanism
-   Node failure detection mechanism
-   Network-partition detection and reporting
-   Split-brain mitigation mechanisms
-   Cluster membership protocol
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
-   ActorRef routing implementation
-   ActorRef persistence/serialization semantics, if any
-   GroupRef persistence/serialization and capability semantics, if any
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
-   Actor-local CPU-bound Future monopolization
-   Resource limits and quotas
-   Runtime resource-pressure model
-   Actor resource-cost estimation and learning
-   ActorRef/GroupRef capability security and authorization
-   Remote authentication
-   Cluster configuration UX
-   Cluster lazy startup
-   Node lazy activation
-   Durable Cluster bootstrap after zero active Nodes
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
-   Optional administrative application/service identity for deployment,
    configuration, observability, or ownership
