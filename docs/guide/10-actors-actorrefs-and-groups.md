# Actors, ActorRefs, and Actor Groups

> **Status:** Non-normative programming guide
>
> **Primary normative owners:** `spec/concurrency/ACTORS.md` and
> `spec/concurrency/DISTRIBUTED_RUNTIME.md`
>
> Related normative owners: `spec/semantics/MODULES.md`,
> `spec/concurrency/FUTURES_AND_TASKS.md`,
> `spec/concurrency/PARALLEL_EXECUTION.md`, and
> `spec/semantics/ERRORS.md`

Actors are Protos's persistent isolation boundary for mutable application state.

The essential mental model is:

> An Actor owns a private mutable object graph and serialized Protos execution.
> Other Actors do not call into that graph directly. They communicate through
> explicit `ActorRef` capabilities and value-transfer boundaries.

Actor Groups add a different abstraction:

> A Group has a stable identity independent of its current members. A `GroupRef`
> is a communication capability to that Group, and each ordinary Group message
> is routed to one eligible concrete Actor member.

Neither abstraction introduces a second object model. Actor behavior is still an
ordinary Protos object with ordinary slots, Closures, lookup, and dispatch.

## Future, P, Actor, and Group solve different problems

The previous chapters introduced Future/task concurrency and isolated P
parallelism. Actors add persistent state and identity.

A useful comparison is:

| Mechanism | Mutable state domain | Identity/lifetime | Communication model | Typical use |
| --- | --- | --- | --- | --- |
| ordinary call | current execution | call/Closure lifetime | ordinary invocation | synchronous local work |
| `closure.future()` | current Actor | Future/task lifetime | result Future | Actor-local async work |
| `closure.parallel(...)` | isolated P domain | Future/task lifetime | transferred inputs/result Future | CPU-parallel isolated work |
| Actor | private Actor domain | concrete Actor incarnation | `ActorRef.send/request` | persistent isolated mutable state |
| Actor Group | member Actors | stable Group identity | `GroupRef.send/request` routes to one eligible member | service/capacity indirection |

A Future is not an Actor merely because it lives for a while.

A P computation is not an Actor merely because it runs in isolation.

An Actor adds a persistent private state domain, mailbox, lifecycle, current
behavior, and communication capability.

A Group adds stable routing identity across a changing eligible-member set. It is
not a shared mutable object and it is not a broadcast channel.

## The Actor behavior is an ordinary Protos object

The runtime Actor domain contains:

```text
private object graph
mutable Actor-local state
current behavior object
mailbox / delivery state
Futures and tasks
lifecycle state
```

The behavior itself is an ordinary Protos object.

When an accepted message is dispatched, ordinary Protos lookup and invocation
run against that behavior.

This means familiar object-language rules remain useful inside the Actor:
delegation, `this`, slots, Closures, Error handling, modules, and Futures do not
become a separate "Actor language".

The Actor domain is the isolation/lifecycle container around those ordinary
objects.

## `Actor.current()` gives the current communication capability

Inside Actor execution:

```protos
me: Actor.current()
```

returns the `ActorRef` for the current concrete Actor incarnation.

Repeated observations identify the same incarnation.

This is useful when another Actor must be given an explicit capability back to
the current Actor.

It is not equivalent to exposing the Actor's private heap.

An `ActorRef` is an opaque communication capability, not a mutable handle to the
target's internal behavior object.

Runnable companion:

- [`../../protos/tutorials/10-actors/01-current-actor.protos`](../../protos/tutorials/10-actors/01-current-actor.protos)

## `Actor.spawn(...)` starts destination-owned code

The standard creation form identifies destination-loadable module code, a
bootstrap binding, and initialization values.

For example, the shipped tutorial uses:

```protos
worker: Actor.spawn("std:collections/Set", "call", 1, 2, 3)
```

The important boundary is what **does not** happen:

```text
caller Closure + caller lexical context
              X
              |
              v
        destination Actor
```

Actor creation does not transport a caller Closure with its lexical context,
return home, dynamic handlers, or mutable caller state intact.

Instead, the destination Actor loads its own module code and receives only the
explicit initialization values permitted by Actor transfer semantics.

Runnable companion:

- [`../../protos/tutorials/10-actors/02-spawn-and-request.protos`](../../protos/tutorials/10-actors/02-spawn-and-request.protos)

## READY means the Actor has a valid behavior

A newly created Actor may pass through initialization before it can dispatch
external messages.

The important lifecycle cutover is:

```text
INITIALIZING
    |
    | successful initialization + valid behavior
    v
READY
```

A READY Actor must have one valid current behavior object.

External messages are not dispatched against an incomplete bootstrap behavior
while the Actor is still INITIALIZING.

If initialization fails before the READY cutover, that incarnation never becomes
READY.

This keeps the programmer-visible model simple: accepted work waits for a real
behavior rather than observing a partially constructed one.

## Creation genealogy is not authority

If Actor A creates Actor B, A receives B's `ActorRef`.

B does **not** automatically receive a capability back to A.

Core defines no ambient:

```text
parentActor
creator()
parentRef
implicit reply-to creator
```

If B needs to communicate with A, A must pass a capability explicitly, for
example as an initialization argument:

```protos
selfRef: Actor.current()
child: Actor.spawn(moduleName, bootstrapName, selfRef)
```

The transferred `ActorRef` is then an ordinary explicitly provisioned
capability.

This rule matters for authority reasoning:

> creation history does not silently grant communication authority.

Likewise, termination of a creator does not automatically terminate a child
merely because of genealogy.

## Actor identity is incarnation identity

An Actor has the identity of one concrete incarnation.

An `ActorRef` permanently identifies exactly that incarnation.

Conceptually:

```text
ActorRef A ───────> incarnation A

incarnation A terminates

ActorRef A ───────> terminated incarnation A
                    (never retargeted)

replacement Actor:
ActorRef B ───────> incarnation B
```

A replacement is a new Actor identity with a new `ActorRef`.

Restarting implementation machinery must not make a dead incarnation appear to
have continued under the old identity.

Durable state restored into a replacement does not resurrect the old Actor.

This is why a direct `ActorRef` is appropriate when the program means "this
specific Actor", while a Group is the higher-level tool when stable service
routing across membership changes is desired.

## Actor messages cross a snapshot boundary

Actors do not share arbitrary mutable Protos object identity.

When values cross an Actor boundary, the runtime performs the defined logical
value transfer/snapshot.

For ordinary transferable mutable data, think:

```text
sender graph
   |
   | validate + logical snapshot
   v
destination-local graph
```

The destination can mutate its transferred graph without thereby mutating the
sender's original graph.

The transfer preserves graph structure that matters inside the transferred
snapshot, including aliases and cycles, rather than flattening every edge into
unrelated values.

Implementation techniques such as copy-on-write, immutable backing, shared
memory, or zero-copy transport are allowed only when they remain
observationally equivalent to this logical isolation.

## The message snapshot is established synchronously

For standard Actor communication, the caller does not hand the runtime a live
mutable graph that may be sampled later at an arbitrary scheduling time.

The complete transferable argument graph is logically captured at the message
operation's defined snapshot point before the call successfully returns its
communication result.

This means code can reason about:

```protos
payload: someMutableObject
operation: target.send("consume", payload)

// Later mutation does not retroactively rewrite the already-captured message.
mutate(payload)
```

The destination observes the transferred message snapshot, not whichever
version happens to exist when a carrier eventually dispatches the handler.

## Capabilities are transferred by their own contracts

Ordinary mutable objects are copied/snapshotted across Actors.

Some capability values have explicit cross-Actor semantics.

`ActorRef` and `GroupRef` are important examples: crossing the Actor boundary
rematerializes the capability in the destination while preserving the semantic
reference identity that its contract defines.

That does **not** expose the referenced Actor or Group's private implementation.

It means the destination receives authority to communicate according to the same
capability contract.

Physical wrapper identity is not the semantic identity rule.

## Non-transferable resources fail instead of auto-proxying

Open files, live streams, native/resource handles, and other values without an
Actor-transfer contract cannot silently cross just because the runtime could
invent some host-level workaround.

Attempting to transfer such a value fails with `NonTransferableValue`.

The transfer is not partially completed.

The runtime must not silently substitute:

```text
remote proxy
reopened file
duplicated OS handle
broker reference
new service capability
```

for the original value.

Those would be new capabilities with potentially different authority, identity,
ordering, lifetime, failure, cancellation, and resource-position semantics.

If an application needs a resource service, an explicit Actor/service capability
can be designed around that resource. That is different from pretending the
original live resource was transferable.

## `send(...)` is asynchronous one-way communication

The public shape is:

```protos
target.send(selector, arguments...)
```

The selector names behavior to dispatch at the destination.

`send` does not return the handler's ordinary result.

It returns a local `SendOperation` describing the communication operation.

That distinction is important:

```text
handler result       -> ignored by send
communication state  -> represented by SendOperation
```

Use `send` when the protocol is intentionally one-way.

## SendOperation makes backpressure and retry explicit

Actor mailboxes and the complete delivery path are bounded.

They are not conceptually infinite queues.

When capacity is unavailable, communication enters backpressure instead of:

```text
blocking an OS thread forever
growing an unbounded mailbox
silently dropping the message
```

The `SendOperation` is the local operation object through which the standard
send cancellation/retry behavior is expressed.

Cancellation only claims success when it establishes known cancellation before
the concrete acceptance boundary.

It does not pretend to undo already accepted work.

Retry is explicit new communication behavior; the runtime does not silently
replay accepted operations.

## `request(...)` returns a fresh Future

Use request/reply when the caller needs one normal reply:

```protos
future: target.request("size")
value: future.value()
```

Every request returns a fresh Future in the caller's execution domain.

The destination handler's normal result crosses the Actor transfer boundary
back to the requester.

The reply therefore has the same isolation discipline as message arguments.

Runnable companion:

- [`../../protos/tutorials/10-actors/02-spawn-and-request.protos`](../../protos/tutorials/10-actors/02-spawn-and-request.protos)

## Request replies do not flatten returned Futures

If the destination handler normally returns a Future value, Actor request does
not silently turn that remote/internal Future into an adopted continuation of
the requester.

The reply-transfer contract remains the authority.

A transferable reply value crosses as defined.

A non-transferable reply fails under the Actor transfer rules.

This avoids making ordinary request/reply a hidden distributed task-adoption
protocol.

## Same sender to the same concrete Actor has FIFO

Actor communication preserves the defined same-sender ordering to one concrete
destination.

The shipped lifecycle tutorial demonstrates the useful case:

```protos
worker.send("atPut", "ready", true)
print(worker.request("size").value())
```

Both operations are issued by the same Actor to the same concrete Actor, so the
later request cannot overtake the earlier send.

Runnable companion:

- [`../../protos/tutorials/10-actors/03-send-and-lifecycle.protos`](../../protos/tutorials/10-actors/03-send-and-lifecycle.protos)

Do not generalize this into a global ordering guarantee among unrelated senders,
Actors, Groups, or destinations.

## Acceptance is the critical delivery boundary

Actor messaging distinguishes:

```text
not yet accepted by a concrete Actor
accepted by a concrete Actor
handler started
handler completed
```

Acceptance means the concrete Actor has taken ownership of the operation.

Before that cutover, cancellation or Group routing may still have outcomes
defined for pre-acceptance work.

After acceptance, Protos does not transparently replay the operation against a
replacement merely because the Actor later fails.

Why?

Because the handler may already have produced effects.

Automatic replay could duplicate them.

The safe rule is:

> accepted work is never silently replayed as though acceptance proved no effect
> occurred.

## Unknown delivery is not known non-delivery

Distributed communication can encounter uncertainty.

If the runtime cannot prove whether the concrete acceptance boundary was crossed,
the state is not equivalent to "the message did not happen".

For request/reply, accepted work lost before a normal reply produces
`RequestOutcomeUncertain` under the standard contract.

This does not authorize automatic retry.

The application can decide whether a higher-level operation is idempotent,
deduplicated, transactional, or safe to retry.

Core does not guess.

## Destination Errors are not implicit request replies

An Error handled inside an Actor turn is ordinary local control flow.

If an Error escapes a destination Actor turn unhandled, it is a fatal failure of
that Actor incarnation under the Core Actor rules.

For an already accepted request, that destination failure does **not** serialize
the private Error object as an implicit reply to the requester.

The sender-visible outcome remains governed by the request acceptance/uncertainty
contract.

This separates:

```text
destination internal failure semantics
```

from:

```text
sender communication outcome semantics
```

## Dynamic Error handlers never cross Actor boundaries

A caller's active dynamic handlers are execution-local control state.

They are not sent to the destination.

Likewise, destination handler frames are not sent back to the caller.

When a failed Future later re-signals an Error through `value()`, matching occurs
in the consumer's then-current dynamic context.

This is consistent with the broader task rule: dynamic handler frames are not
ambient Actor-global state.

## Non-local return never crosses an Actor boundary

A `^` return is tied to a return home in one execution structure.

Actor messaging does not transfer:

```text
call stacks
continuations
return homes
lexical control authority
dynamic execution contexts
```

Destination code may use its own valid non-local returns.

It cannot return into the sender's suspended call, creator activation, or a dead
Actor incarnation.

A request reply is an explicit value-transfer protocol, not a disguised remote
stack unwind.

## Actor-local work is cooperatively serialized

Inside one Actor, ordinary Protos execution remains serialized.

Actor-local tasks created with `future()` can interleave only at defined
suspension/cancellation-aware boundaries.

A long CPU-bound segment that never reaches such a boundary can monopolize that
Actor domain.

Core does not invent hidden semantic preemption just because the runtime has
multiple host threads.

If CPU work should execute independently, use the isolated P boundary described
in [chapter 09](09-isolated-parallel-execution.md).

This separation preserves the simple Actor invariant:

> no two ordinary Protos execution segments simultaneously mutate the same
> Actor-local object graph.

## `stop()` requests graceful termination

The standard public lifecycle request is:

```protos
actorRef.stop()
```

It returns canonical `null`.

Repeated calls for the same terminating or already terminated incarnation are
idempotent.

Core does not turn `stop()` into:

```text
a stop Future
a timeout-bearing stop
a force-kill control
a general administrative Actor handle
```

The stop cutover makes termination irreversible for that incarnation.

Actor-local tasks and other Actor-owned asynchronous work are cancelled according
to their existing lifecycle rules, including required `ensure` cleanup.

An already running non-suspending Protos segment is still governed by cooperative
execution; graceful stop is not a hidden arbitrary preemption primitive.

## `termination()` observes known termination

Lifecycle observation uses the existing Future abstraction:

```protos
finished: actorRef.termination()
```

Each call returns a fresh independent non-task-backed Future in the caller's
domain.

When termination of that exact incarnation becomes known, the Future resolves
with that same `ActorRef`.

If termination is already known when observation is registered, the Future is
already resolved accordingly.

Cancelling the observation Future only abandons that observation.

It does not stop the target Actor and it does not cancel other observers.

## TERMINATED, UNREACHABLE, and UNKNOWN are different

A network failure or lost route does not prove a remote Actor has terminated.

Therefore:

```text
TERMINATED != UNREACHABLE != UNKNOWN
```

`ActorRef.termination()` observes **known termination**.

A timeout, partition, or transport failure alone cannot falsely resolve that
Future as if death were proven.

Replacement also does not retarget an existing observation. A new Actor
incarnation has a new `ActorRef` and requires a new observation.

## Creator lifetime and Actor lifetime are separate

Actor creation is not an implicit parent-child lifetime tree.

If A creates B and A later terminates, B does not automatically terminate solely
because A was the creator.

Every Actor belongs to the relevant Process/failure-domain model, but genealogy
by itself does not grant communication authority or define automatic child
lifetime.

This prevents accidental lifetime coupling from being smuggled in through
`spawn`.

## Fatal failure is per incarnation

Core v0.1 has a fixed baseline failure-authority policy rather than a public
configurable supervision API.

For an ordinary non-root Actor:

```text
unhandled fatal Actor failure
    -> that incarnation terminates
    -> no automatic Core replacement
    -> no automatic creator escalation
    -> no automatic sibling/subtree termination
```

For the Process RootActor:

```text
unhandled fatal RootActor failure
    -> Process termination
```

Higher-level reconciliation may create another Actor, but that is a new
incarnation, not restoration of the dead identity.

## There is no public Core supervisor-policy object

Core does not expose a mandatory public:

```text
Supervisor
FailureAuthority
restart strategy
one-for-one tree
one-for-all tree
failure policy callback
```

Runtime machinery still has a well-defined failure authority so failures have a
deterministic policy.

A future higher-level supervision abstraction could expose richer policy, but
portable Core programs should not assume one exists today.

## Group identity solves a different continuity problem

A Group is not an Actor and not a collection whose identity equals its member
list.

Group identity is stable independently of current membership.

Conceptually:

```text
Group G
members: A, B

membership changes

Group G
members: B, C
```

It is still Group G.

The Group may even be live with zero eligible members.

That is the key contrast with `ActorRef`, whose identity is permanently one
concrete Actor incarnation.

## `Actor.group(...)` is the Core Group acquisition surface

Core v0.1 provides:

```protos
workers: Actor.group(firstMember, additionalMembers...)
```

At least one member is required.

The supplied initial values must denote `ActorRef`s. The complete member vector
is validated before the Group creation cutover.

Duplicate references to the same Actor incarnation do not create weighted
duplicate membership.

A successful invocation synchronously creates:

```text
one fresh Group identity
one initial membership set
one fresh GroupRef acquisition identity
```

and returns the `GroupRef`.

Runnable companion:

- [`../../protos/tutorials/11-actor-groups/01-create-group.protos`](../../protos/tutorials/11-actor-groups/01-create-group.protos)

## Group creation does not require ownership of member Actors

Possessing member communication capabilities is sufficient for the Core
acquisition operation.

Creating a Group does not:

```text
move members
restart members
rehost members
make the caller their supervisor
make member lifetimes children of the Group
```

The Group is routing/control state over existing Actor identities.

Its lifetime is owned by the caller Actor's Process under the Core contract.

## Group creation does not wait for all members to be READY

`Actor.group(...)` returns after the Group creation cutover.

It does not need to wait for every supplied member to become READY, reachable, or
currently routable.

Routing later uses the then-current eligible member set.

This avoids turning Group acquisition into a cluster-wide barrier.

## A GroupRef is a communication capability

The language-visible Core `GroupRef` communication surface is intentionally
small:

```protos
groupRef.send(selector, arguments...)
groupRef.request(selector, arguments...)
```

It does not expose general membership/controller authority.

The Group itself is not surfaced as an ordinary mutable Group object through
which callers directly edit membership.

Core v0.1 therefore has no standard public post-creation:

```text
group.addMember(...)
group.removeMember(...)
group.resize(...)
group.stop(...)
group.members()
group.controller()
```

The absence is intentional, not an invitation to infer hidden mutable methods.

## A Group message targets one eligible member

Ordinary Group `send` and `request` are routing operations.

They select one eligible member.

They are not broadcast.

For example, the shipped tutorial says explicitly not to rely on which eligible
Actor receives the request:

```protos
first: Actor.spawn("std:collections/Set", "call", "Ada", "Grace")
second: Actor.spawn("std:collections/Set", "call", "Linus", "Ken")
workers: Actor.group(first, second)

print(workers.request("size").value())
```

Runnable companion:

- [`../../protos/tutorials/11-actor-groups/02-request-through-group.protos`](../../protos/tutorials/11-actor-groups/02-request-through-group.protos)

If an application needs multicast or broadcast, that is a different protocol.

## Group routing may change only before concrete acceptance

A Group may choose or re-choose a member while an operation remains
pre-acceptance.

For example, a selected member may become unavailable before accepting the
operation, allowing the Group routing machinery to select another eligible
member under the standard rules.

Once a concrete Actor accepts the operation, the Group does not transparently
reroute it if that Actor subsequently fails.

The same duplicate-effect argument applies as for direct Actor messaging.

Acceptance turns a Group-routed operation into concrete Actor-owned work.

## There is no Group-wide FIFO guarantee

A Group may route successive messages to different members.

Therefore Core does not promise one total FIFO order over the entire Group.

If two operations from the same sender happen to route to the same concrete
Actor, that Actor's same-sender FIFO rules apply there.

But portable code must not assume:

```text
group.send(A)
group.send(B)
```

means every possible member observes A before B.

There may not even be one member that observes both.

## A live empty Group can preserve pending routing

A Group identity may remain live while no member is currently eligible.

The communication path remains bounded; it does not become an unlimited queue.

Depending on the operation state, routing may remain pending until a member
becomes eligible or another standard terminal outcome occurs.

This distinction lets Group identity survive transient capacity changes without
pretending zero members means the Group identity vanished.

## GroupRef identity is not Group identity

There are three concepts to keep separate:

```text
Group identity
GroupRef semantic identity
physical runtime wrapper/proxy
```

Two separately acquired `GroupRef`s can refer to the same Group while remaining
distinct `GroupRef` identities.

Conversely, transferring/rematerializing the **same** GroupRef capability across
an Actor boundary preserves its semantic GroupRef identity.

The shipped identity tutorial demonstrates an even simpler case: two independent
`Actor.group(...)` calls over the same member list create different Groups and
different GroupRefs:

- [`../../protos/tutorials/11-actor-groups/03-groupref-identity.protos`](../../protos/tutorials/11-actor-groups/03-groupref-identity.protos)

Do not infer identity from the current member list.

## GroupRefs never retarget

A concrete `GroupRef` remains bound to one Group identity.

If a future discovery/name service points a name at some other Group, an already
acquired `GroupRef` does not silently change target.

This mirrors the ActorRef rule:

```text
ActorRef -> one Actor incarnation forever
GroupRef -> one Group identity forever
```

The difference is that Group membership itself can change while Group identity
remains stable.

## Remote possession does not extend Group lifetime

Transferring a `GroupRef` to another Actor or Process preserves communication
authority under its contract.

It does not give the holder ownership of the Group's lifecycle.

Core Group lifetime is owned by the Process that created the Group.

When that owning Process terminates, the Group terminates under the Core
lifetime rule.

Keeping another physical wrapper or remote GroupRef alive cannot keep the Group
alive beyond that authority boundary.

## Group termination does not stop member Actors

The Group and its member Actors have independent identities and lifetimes.

When the Group ends, Core does not thereby stop its member Actors.

Likewise, losing all members does not by itself mean the Group identity has
terminated.

This prevents routing topology from silently becoming Actor ownership.

## Core v0.1 does not provide portable service discovery

A Group is useful as a stable communication/routing capability once acquired.

Core v0.1 does not standardize a public:

```text
service registry
global Group name
lookup-by-name
reacquire GroupRef after loss
placement policy API
durable Group registry
post-creation membership controller
```

Those are higher-level distributed-runtime concerns.

Portable application code should not pretend `Actor.group(...)` is a hidden
service-discovery system.

## Physical transport and locality are not semantic

ActorRef and GroupRef communication may be implemented using:

```text
in-process queues
shared memory
local IPC
TCP
QUIC
other conforming transports
```

The runtime may even change transport while a logical capability remains live.

Portable semantics must remain unchanged:

```text
same ActorRef/GroupRef identity
same message snapshot
same transferability
same acceptance boundary
same FIFO guarantees where applicable
same uncertainty/cancellation behavior
no shared mutable Protos identity
```

Core exposes no portable transport-pinning or locality-query API merely to make
those optimizations observable.

## Choose direct ActorRef versus GroupRef deliberately

Use a direct `ActorRef` when the protocol targets one specific incarnation:

```text
this cache worker
this session Actor
this concrete owner
this lifecycle to monitor
```

Use a `GroupRef` when the protocol targets a stable service/capacity identity and
any eligible member may process an individual operation:

```text
worker pool
stateless service replicas
sharded/routed capacity where the Group contract is appropriate
```

Do not use Group routing when correctness depends on one particular Actor's
private state unless the higher-level protocol makes that relationship explicit.

## Runnable learning path

The existing executable tutorials are deliberately split so each step introduces
one concept at a time.

Actors:

1. [`10-actors/01-current-actor.protos`](../../protos/tutorials/10-actors/01-current-actor.protos)
   — current ActorRef identity.
2. [`10-actors/02-spawn-and-request.protos`](../../protos/tutorials/10-actors/02-spawn-and-request.protos)
   — module-backed spawn and request/reply.
3. [`10-actors/03-send-and-lifecycle.protos`](../../protos/tutorials/10-actors/03-send-and-lifecycle.protos)
   — send ordering plus stop/termination observation.

Actor Groups:

1. [`11-actor-groups/01-create-group.protos`](../../protos/tutorials/11-actor-groups/01-create-group.protos)
   — Group creation.
2. [`11-actor-groups/02-request-through-group.protos`](../../protos/tutorials/11-actor-groups/02-request-through-group.protos)
   — member-agnostic request routing.
3. [`11-actor-groups/03-groupref-identity.protos`](../../protos/tutorials/11-actor-groups/03-groupref-identity.protos)
   — independent Group/GroupRef identities.

The current language-maturity corpus also retains Actor/Group conformance
coverage under [`LM005`](../project/registries/IMPLEMENTATION_STATUS.md#lm005--concurrent-language-maturity).

## Representative implementation evidence

The current implementation ledger records `I011 — Actors` CLOSED through its
complete incremental runtime/conformance sequence:

- [`I011 — Actors`](../project/registries/IMPLEMENTATION_STATUS.md#i011--actors).

That closure covers Actor incarnation identity, ActorRef transfer, module-backed
bootstrap, value transfer, bounded mailboxes, send/request, lifecycle, GroupRef
routing, local/cross-Process/remote uncertainty, and the final
`Actor.group(...)` acquisition surface.

This implementation record is evidence of what the reference runtime currently
supports. It does not override the normative specifications.

## Practical rules to remember

1. An Actor is a persistent isolated mutable state/execution/lifecycle domain;
   its behavior is an ordinary Protos object.
2. `Actor.current()` gives the current incarnation's communication capability,
   not direct access to private Actor state.
3. `Actor.spawn(...)` identifies destination-owned module/bootstrap code and
   transfers explicit initialization values; it does not transport caller
   execution context.
4. Creation genealogy grants no reverse communication capability.
5. `ActorRef` identity is concrete incarnation identity and never retargets.
6. Replacement always creates a new Actor identity and ActorRef.
7. Actor communication snapshots/transfers values; ordinary mutable state is not
   shared across Actor domains.
8. `ActorRef`/`GroupRef` cross Actor boundaries only through their explicit
   capability-transfer contracts.
9. Non-transferable live resources fail with `NonTransferableValue`; Core never
   silently auto-proxies them.
10. `send` is one-way and returns a `SendOperation`; it does not return the
    destination handler result.
11. Backpressure is bounded and explicit; mailboxes are not unlimited sinks.
12. `request` returns a fresh caller-domain Future whose normal reply crosses the
    Actor value-transfer boundary.
13. Accepted operations are never transparently replayed against replacements.
14. Delivery uncertainty is not proof of non-delivery and does not authorize
    automatic retry.
15. Same-sender FIFO applies to the same concrete Actor, not globally.
16. Dynamic Error handlers and non-local return never cross Actor boundaries.
17. An unhandled Error escaping a destination turn is fatal to that Actor
    incarnation; a handled Error is ordinary local control flow.
18. Actor-local ordinary Protos work is cooperatively serialized; use P for
    isolated CPU parallelism.
19. `stop()` is an idempotent graceful termination request returning `null`, not
    a force-kill Future.
20. Each `termination()` call creates an independent Future observing known
    termination of that exact incarnation.
21. Unreachable/unknown does not equal known terminated.
22. `Actor.group(...)` creates one fresh Group and one fresh GroupRef over at
    least one explicitly supplied ActorRef.
23. Group identity is independent of current membership.
24. A GroupRef is communication authority with standard `send`/`request`, not a
    public membership/controller handle.
25. One Group operation routes to one eligible member; ordinary Group messaging
    is not broadcast.
26. Group routing may reselect only before concrete acceptance; accepted work is
    never transparently rerouted.
27. There is no Group-wide FIFO; concrete Actor FIFO applies only when operations
    route to the same Actor.
28. GroupRef identity, Group identity, and physical wrapper identity are
    different concepts.
29. A GroupRef never retargets, and holding one does not extend Group lifetime.
30. Group termination does not stop member Actors.
31. Core v0.1 does not provide public service discovery, post-creation Group
    membership control, transport pinning, or topology APIs.
32. Choose ActorRef for one concrete incarnation and GroupRef for stable
    member-agnostic routing authority.

## Normative references

For exact behavior, consult:

- [`../../spec/concurrency/ACTORS.md`](../../spec/concurrency/ACTORS.md) for
  Actor isolation, behavior, creation, transfer, ActorRef identity,
  send/request, backpressure, ordering, failure, stop/termination, and
  Actor-local task semantics;
- [`../../spec/concurrency/DISTRIBUTED_RUNTIME.md`](../../spec/concurrency/DISTRIBUTED_RUNTIME.md)
  for Group/GroupRef identity, routing, acceptance, lifetime, distributed
  communication, and the exact Core `Actor.group(...)` acquisition boundary;
- [`../../spec/semantics/MODULES.md`](../../spec/semantics/MODULES.md) for
  Actor-local module identity/cache/bootstrap behavior;
- [`../../spec/concurrency/FUTURES_AND_TASKS.md`](../../spec/concurrency/FUTURES_AND_TASKS.md)
  for Futures used by request, lifecycle observation, task ownership, and
  cancellation;
- [`../../spec/concurrency/PARALLEL_EXECUTION.md`](../../spec/concurrency/PARALLEL_EXECUTION.md)
  for the P boundary contrasted with Actor-local cooperative execution;
- [`../../spec/semantics/ERRORS.md`](../../spec/semantics/ERRORS.md) for ordinary
  Error handling and the dynamic-control rules reused inside Actors;
- [chapter 08](08-futures-and-structured-concurrency.md) for Future observation
  and structured ownership;
- [chapter 09](09-isolated-parallel-execution.md) for isolated CPU parallelism.

Those documents define Protos. This chapter supplies the programmer-facing model
for choosing Actor and Group capabilities without importing a shared-memory,
implicit-supervision, or transparent-retry model into the language.
