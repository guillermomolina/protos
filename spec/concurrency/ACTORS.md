# Protos Actors v0.1

Language version: 0.1
Document revision: 324
Status: Draft
Last updated: 2026-09-04

This document is the primary normative owner of the Core Actor-domain semantics
migrated from `../PROTOS_CONCURRENCY_MODEL.md`.

Legacy section numbers are intentionally retained during modularization so
existing citations remain understandable. Compatibility headings left in the
mixed concurrency ledger are references only and define no duplicate normative
authority.

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
