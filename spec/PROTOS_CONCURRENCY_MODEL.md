# Protos Concurrency Model v0.1

Language version: 0.1
Document revision: 317
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

This section is the primary normative owner of the Core v0.1
`Future.then(transform) -> Future` concurrency-domain semantics.

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

### Actor bootstrap representation

Core v0.1 bootstrap code is identified by destination-loadable module code, not
by transferring a caller Closure or caller execution context.

Conceptually, Actor creation fixes:

```text
bootstrap module identity
bootstrap binding name
explicit initialization argument values
```

The bootstrap module identity denotes one canonical module identity under the
existing module-resolution rules. The destination Actor obtains its own
Actor-local module instance for that identity through the ordinary module
loading/cache lifecycle. No creator module instance or module context is
transferred or shared.

The bootstrap binding name identifies one top-level binding on that destination
module instance. A top-level module binding is a local slot of that module
instance, so bootstrap requires that exact name to exist as a local slot there.
It does not use delegated lookup for bootstrap entry-point selection.

After the module is ready, bootstrap reads that local slot directly and requires
the resulting value to be ordinarily invokable. A same-named slot inherited from
`Context`, `Object`, the prelude, or any other delegation ancestor does not
satisfy the bootstrap-binding requirement.

The explicit initialization arguments cross the Actor boundary under the
existing Actor pass-by-value rules before bootstrap invocation. Bootstrap then
invokes the destination-local binding with those transferred argument values
during `INITIALIZING`.

The normal result of that bootstrap invocation is the Actor incarnation's
initial behavior object. That exact destination-local object is installed as the
current behavior used for the `INITIALIZING -> READY` cutover and is then subject
to the stable-behavior rule of §11.

Bootstrap does not receive the creator's `this`, lexical context, `moduleContext`,
return home, dynamic handler stack, pending Futures, mutable graph, or ambient
capabilities. Any capability needed by bootstrap must be provisioned explicitly
under the existing Actor/capability rules.

Failure to load/initialize the bootstrap module, missing bootstrap binding,
non-invokable bootstrap binding, an unhandled Error from bootstrap invocation,
or failure to produce the required behavior prevents `READY` and is ordinary
Actor initialization failure.

This representation deliberately resembles a code-identity-plus-arguments model:
code is resolved and executed inside the destination Actor while only permitted
values cross the isolation boundary. It does not create a second transferable
function kind.

The exact public creation message/API name and surface syntax remain open.

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

### Fatal Actor Error does not become an implicit remote Error channel

An `Error` that escapes the outermost dynamic handler boundary of an ordinary
Actor turn is local failure state of that Actor incarnation.

For a non-root Core Actor, the failure-authority consequence is Actor
termination itself. Core does not additionally transfer, copy, snapshot, proxy,
re-signal, or otherwise expose that internal Error object to another Actor merely
because the failure is fatal.

Observers outside the failed Actor see only the outcomes already defined by the
Actor lifecycle and communication contracts. In particular, an accepted
`request()` that cannot produce its normal reply follows the existing
`RequestOutcomeUncertain` rule rather than failing with the destination's
internal Error.

For the RootActor, the escaping Error may be used internally as the cause of the
required Process termination. This does not make the Error an Actor-transferred
value, does not establish cross-Actor `===` identity, and does not create a
portable remote-error delivery API.

A future supervision/failure-reporting facility may explicitly define a
transferable failure report or Error snapshot, but Core v0.1 does not synthesize
one implicitly.

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

Each Actor incarnation has one current behavior object established before
the `INITIALIZING -> READY` cutover.

The current behavior is an ordinary Protos object.

Core v0.1 does not define a post-READY operation that replaces, stacks, swaps, or
otherwise changes the Actor's current behavior reference. Once the Actor reaches
`READY`, that reference remains the same for the lifetime of the incarnation.

External messages are therefore dispatched against that same behavior object for
every ordinary Actor turn of the incarnation.

This does not make Actor behavior immutable. The behavior object may use the
ordinary Protos object model to change its own local state, delegate through its
existing parent, mutate other Actor-local objects, or otherwise represent
different application modes. Such changes are ordinary object semantics, not an
Actor-specific behavior-transition mechanism.

In particular, Core defines no standard `become`, `unbecome`, behavior stack,
implicit Actor-control binding, or special interpretation of a handler's return
value as "next behavior". A handler's ordinary result retains its existing
meaning; for `request()` it is the reply value, and for `send()` it is ignored.

An ActorRef identifies the Actor incarnation, not the behavior object and not an
application-defined mode represented inside that behavior.

The exact bootstrap API or syntax by which initialization establishes the
initial behavior remains a separate open topic.

## 11A. Behavior Requirement at the READY Cutover

**CLOSED**

Core v0.1 does not require a newly created Actor to possess a dispatchable
current behavior throughout its entire `INITIALIZING` state.

No external message is dispatched while the Actor is `INITIALIZING`, so requiring
a fully installed behavior before initialization code has established one would
create no additional portable observation and would unnecessarily constrain Actor
bootstrap representation.

The normative requirement is instead at the lifecycle cutover:

```text
INITIALIZING -> READY
    requires one valid current behavior
```

An Actor must not enter `READY` without a current behavior object available for
ordinary message dispatch.

The behavior that satisfies this requirement is an ordinary Protos object. Core
does not introduce a special bootstrap-behavior kind, placeholder behavior,
sentinel object, hidden default behavior, or magic "uninitialized behavior"
object.

During `INITIALIZING`, the runtime may internally represent the not-yet-installed
behavior state however it chooses, including absence of a behavior reference,
provided that representation is not exposed as a Protos value and no external
message is dispatched against it.

If initialization completes normally without establishing a valid current
behavior, the Actor cannot perform the `INITIALIZING -> READY` transition. This
is an initialization failure of that incarnation under the ordinary Actor
initialization/failure rules; the Actor never becomes `READY`.

Messages already accepted while the Actor is `INITIALIZING` remain governed by
the existing readiness and failure rules. They are not dispatched merely because
a partial/bootstrap behavior exists internally, and they are lost with the
incarnation if initialization fails before `READY`.

Once the Actor is `READY`, §11 applies normally: the incarnation retains the
behavior object established for the READY cutover, and each external message turn
dispatches against that same object.

This section closes the former open ledger item `Behavior requirements before
READY`. The exact bootstrap API or syntax by which initialization establishes
that initial behavior remains a separate open topic.

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

## 24D. Actor-Local CPU Work Is Cooperatively Non-Preemptive

**CLOSED**

Actor-local asynchronous work created by ordinary `closure.future()` remains in
the same mutable Actor execution domain as the code that created it.

Within that domain, Protos concurrency is cooperative. A running Actor-local task
continues ordinary Protos execution until it reaches an already-defined explicit
suspension/cancellation-aware boundary, completes, fails, or otherwise leaves its
current execution segment according to existing semantics.

Core does not introduce hidden semantic preemption points at loop back-edges,
method-call boundaries, allocations, interpreter/JIT polls, garbage collection,
timer ticks, carrier time slices, host-thread scheduling boundaries, or similar
implementation events.

Consequently, an Actor-local task that performs CPU-bound Protos computation
without reaching an explicit suspension point may monopolize that Actor's Protos
execution domain for an unbounded amount of time. While that task remains in the
same non-suspending execution segment:

- another Actor-local task cannot simultaneously execute Protos code against the
  same Actor mutable state;
- queued Actor message handlers do not acquire an implementation-selected
  interleaving point inside that segment;
- cancellation requests remain pending until an existing portable cancellation
  boundary is reached;
- runtime scheduling fairness between different runnable Actor-local tasks does
  not imply arbitrary preemption of the currently executing segment.

This is a semantic consequence of C's shared-state/serialized-execution model,
not a recommendation that CPU-heavy work should normally run this way.

When CPU-bound work should make progress independently of Actor-local cooperative
suspension, the standard Core mechanism is the explicit P boundary:

```text
closure.parallel(arguments...)
```

That operation creates isolated parallel execution rather than silently changing
the semantics of `closure.future()`.

An implementation may physically interrupt, migrate, time-slice, compile, or
resume an Actor-local task internally, but such machinery must be
observationally equivalent to uninterrupted execution of the same Protos segment.
In particular, it must not allow another Actor-local Protos task to observe or
mutate the shared Actor domain at a point where the program did not explicitly
suspend.

A future explicit yield/scheduling facility may add a new portable cooperative
boundary if independently justified. Until then, Core defines no implicit
`yield`, quantum expiration, fairness poll, or automatic conversion of CPU-bound
Actor-local work into P.

## 24E. Waiting for Multiple Futures with `Future.all(...)`

**CLOSED**

This section is the primary normative owner of the Core v0.1
`Future.all(futures...) -> Future` concurrency-domain semantics.

Core v0.1 standardizes the ordinary Future-protocol operation:

```text
Future.all(futures...)
    -> Future
```

`Future` here denotes the standard Future prototype object; `all` is an ordinary
message on that object and introduces no new syntax, Task kind, wait-set object,
or scheduler identity.

After ordinary argument evaluation left-to-right, every supplied argument must
be a Future value in the current execution domain. Validation is synchronous in
ascending argument-index order. The first non-Future argument signals an `Error`
and no aggregate Future is created.

The returned aggregate is a fresh non-task-backed Future representing only this
multi-Future observation. It does not own, re-parent, detach, cancel, or otherwise
change any source Future or its producer.

For zero arguments:

```text
Future.all()
```

returns an already-resolved Future whose value is a fresh empty standard Array.

For one or more source Futures, let their argument positions be `0 .. n-1`.
The aggregate observes each source's stable terminal outcome and applies one
deterministic ascending-index frontier.

For each source position, the logical outcomes are:

```text
resolved(value)
failed(error)
cancelled
```

A resolved source contributes its resolved value at the same index of the final
result Array.

The aggregate may resolve successfully only when every source Future is resolved.
Its value is then one fresh standard Array:

```text
[result0, result1, ..., resultN]
```

with exactly the source-argument order, independent of source completion order.

Failure/cancellation selection is also argument-order deterministic. The
aggregate becomes terminal at the lowest source index whose outcome is not
`resolved`, but only after every lower index is known to be `resolved`.

Therefore:

- a failed source at index `i` fails the aggregate with that same Error only when
  every source `0 .. i-1` is resolved;
- a cancelled source at index `i` cancels the aggregate only when every source
  `0 .. i-1` is resolved;
- a later failure/cancellation cannot overtake an unresolved lower index;
- if a lower source later fails/cancels, that lower outcome wins;
- physical completion order, callback scheduling, carrier choice, or registration
  order cannot select the aggregate terminal outcome.

This ordered frontier intentionally differs from completion-race semantics.
`Future.all(...)` is the deterministic wait-for-all combinator; first-completion
selection remains a separate `select`/`race` design topic.

Cancelling the aggregate Future abandons only the aggregate observation. It does
not request cancellation of any source Future, does not alter any source terminal
state, and does not propagate upstream cancellation. Once aggregate cancellation
wins its ordinary first-terminal transition, later source completions are ignored
by that aggregate.

Repeated source Future identity is allowed. Each argument position is one logical
observation slot. If the same Future appears more than once and resolves, its same
resolved value occupies each corresponding result position; no source is
duplicated or re-executed.

The aggregate itself has no structured task-ownership edge. `detach()` therefore
has the existing non-task-backed Future no-op behavior.

A pending aggregate must not retain arbitrary completed-source implementation
state beyond what is necessary to produce the specified result or deterministic
frontier decision. Once the aggregate becomes terminal or its cancellation is
honored, source registrations must be removed or made inert so long-lived source
Futures cannot retain dead aggregate observation state without bound.

The implementation may realize the operation with callbacks, compact waiter
registrations, bitmaps, counters, continuation records, polling already-terminal
state, or another mechanism. Such choices are non-observable provided the
ordered result, terminal-outcome selection, cancellation isolation, and
registration-lifetime rules above are preserved.

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
`PROTOS_LANGUAGE_SPEC.md` and `PROTOS_RUNTIME_SEMANTICS.md`. This
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
