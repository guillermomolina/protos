# Protos Actors v0.1

Language version: 0.1
Status: Draft
Last updated: 2026-09-04

This document is the primary normative owner of the Core Actor-domain semantics
migrated from `docs/design/CONCURRENCY_DESIGN.md`.

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

### Core public Actor surface

Core v0.1 exposes the ordinary frozen-prelude object `Actor` with exactly these
portable entry operations:

```text
Actor.spawn(moduleSpecifier, bindingName, arguments...) -> ActorRef
Actor.current() -> ActorRef
```

`Actor` is not syntax, an Actor instance, a scheduler handle, or host authority.
`moduleSpecifier` and `bindingName` must be semantic `String` values after ordinary
left-to-right argument evaluation; no coercion or String-like delegation occurs.
The specifier is resolved once in the creator's module-resolution environment
using the `import(specifier)` host boundary. Failure signals `Error` and creates no
Actor. Success fixes the canonical module identity used by the destination; the
destination does not reinterpret the original spelling.

The complete initialization argument graph is then transferred/delegated before
`spawn` returns. `NonTransferableValue` is synchronous and creates no observable
partial Actor. A capability with an explicit Actor-delegation contract, including
Process, uses that contract; nothing is inherited merely because the creator has
it. After these validations, one incarnation is created and its `ActorRef` is
returned even if initialization is still pending. Later bootstrap failure is
therefore Actor initialization failure, not retroactive failure of `spawn`.

`Actor.current()` returns the `ActorRef` of the current incarnation. Repeated calls
in that incarnation denote the same semantic identity under `===`; the operation
does not expose behavior, mailbox, scheduler, Process, or mutable runtime state.
Core defines no Closure-taking Actor constructor, public Actor ID, mailbox object,
worker/thread selector, or second creation syntax.

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

Actor creation/bootstrap acquisition is the `Actor.spawn(...)` surface of §8; no second behavior-installation API exists.

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

`ActorRef.send(selector, arguments...)` is the standard one-way Actor communication operation. `selector` must be a semantic `String`; no coercion is performed. Selector validation and complete message snapshot/transfer validation occur synchronously after ordinary argument evaluation. Invalid selector values signal `Error`; a non-transferable graph signals `NonTransferableValue`.

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

The standard `SendOperation` protocol is deliberately minimal:

```text
operation.cancel() -> Boolean
operation.retry() -> SendOperation
```

`cancel()` returns `true` exactly when that call establishes known pre-acceptance
cancellation; otherwise it returns `false` and never claims to undo accepted work.
`retry()` is valid only after terminal failure or delivery uncertainty and returns
a fresh logical delivery operation for the same destination, selector, and original
logical message snapshot, ordered at the retry invocation point; otherwise it
signals `Error`. Retry never re-evaluates source argument expressions. Distinct
successful send/retry invocations have distinct `SendOperation` identities. Core
exposes no standard status enum, attempt counter, destination ID, mailbox capacity,
progress, last-error, blocking wait, or transport detail.

## 14. request()

**CLOSED --- REVISED**

`ActorRef.request(selector, arguments...)` is the standard request/reply operation. It uses the same semantic-String selector domain, ordinary argument evaluation, synchronous validation, snapshot formation, and `NonTransferableValue` rule as `ActorRef.send`.

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
-   Process capability: transferable only through the explicit Actor-delegation contract owned by `../io/PROCESS_IO.md`; the destination receives Actor-local capability authority, not a copied host resource
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

Core distinguishes a **known Actor termination request** from failure, unreachability, and ordinary message handling. The standard public operation is `ActorRef.stop()`. It establishes the graceful-stop cutover below and returns canonical `null`. Repeated calls on the same terminating/terminated incarnation are idempotent and also return `null`; Core exposes no stop Future, timeout parameter, force-kill operation, or administrative Actor-control API.

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
explicit P-safe contract consistent with `PARALLEL_EXECUTION.md` §71.

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
