# Futures and Structured Concurrency

> **Status:** Non-normative programming guide
>
> **Normative owner:** `spec/concurrency/FUTURES_AND_TASKS.md`
>
> Related normative owners: `spec/semantics/CALLABLES.md`,
> `spec/semantics/ERRORS.md`, `spec/semantics/EXECUTION_AND_CONTROL.md`, and
> `spec/concurrency/ACTORS.md`

Protos separates an eventual result from the lifetime of the work that produces
it.

The central model is:

> A Future is an ordinary eventual-result object. A task-backed Future may also
> represent asynchronous child work whose lifetime is owned by the current
> asynchronous task scope unless explicitly detached.

There is no `async` function category and no `await` keyword. Ordinary Closures
can execute synchronously or asynchronously, and ordinary Future messages provide
observation, composition, cancellation, and detachment.

## A Closure chooses synchronous or asynchronous execution at the call site

The same Closure can run synchronously:

```protos
work: () => {
    42
}

result: work()
```

or asynchronously:

```protos
future: work.future()
```

`future()` requests asynchronous execution of that Closure and immediately
returns a Future representing the result.

Asynchrony belongs to that execution, not permanently to the Closure definition.

There is therefore no separate `async` Closure type.

## `future()` is ordinary protocol behavior

The standard `future` selector participates in ordinary lookup on `Object`, with
a semantic Closure receiver requirement.

This means normal lookup rules still matter:

```text
lookup
reflection
extraction
shadowing
override behavior
```

Merely possessing or inheriting a slot named `future` does not turn an arbitrary
object into a semantic Closure.

The runtime/scheduler boundary is implementation machinery behind the ordinary
language-visible protocol.

## Each Future-producing invocation normally gets a fresh Future identity

Unless an operation explicitly promises to return an already-existing Future,
two separate Future-producing invocations produce distinct Future objects.

For example:

```protos
work: () => 42

a: work.future()
b: work.future()

a === b
```

is false.

That remains true even if both Futures eventually resolve to the same value.

Future identity and result identity are separate questions.

## A Future has four states

The semantic states are exactly:

```text
pending
resolved(value)
failed(error)
cancelled
```

Only `pending` is non-terminal.

The first terminal transition wins and remains stable.

A Future does not later switch from resolved to failed, from cancelled to
resolved, or otherwise rewrite its terminal outcome because some later producer,
cancellation, or adoption bookkeeping arrives.

## Resolution preserves the result object

When asynchronous work completes normally with an ordinary non-Future value, its
Future resolves with that exact value.

Conceptually:

```protos
object: {}

f: (() => object).future()
result: f.value()

result === object
```

The Future does not clone or canonicalize the result merely because it crossed an
asynchronous completion boundary inside the same value/isolation domain.

Boundary-specific value transfer rules, such as Actor or isolated-parallel value
transfer, remain owned by those boundaries.

## Returning a Future causes adoption, not a nested Future result

If Future resolution is given another Future, the destination adopts the source
Future's eventual terminal outcome.

For example:

```protos
source: (() => 20).future()

result: source.then((value) => {
    (() => value * 2 + 2).future()
})

result.value()
```

produces `42`.

Conceptually:

```text
transform returns sourceFuture
        |
        v
destination remains pending
        |
        v
sourceFuture resolves/fails/cancels
        |
        v
destination mirrors that outcome
```

The destination does not resolve to a Future whose value is another Future.

## Adoption transfers outcome, not identity or ownership

Adoption does **not** make the two Futures identical.

It also does not transfer:

```text
task identity
structured ownership
detachment state
upstream cancellation authority
```

Cancelling an adopting destination does not automatically cancel the adopted
source.

Detaching the destination does not detach the adopted source.

Adoption is an outcome relationship, not ownership re-parenting.

## Future-adoption cycles fail

A Future cannot validly adopt itself, directly or transitively.

An adoption cycle fails the destination with a fresh standard
`FutureResolutionCycle` Error occurrence rather than leaving it pending forever.

This is a semantic failure rule, not a timeout or implementation deadlock
heuristic.

## `value()` observes the stable Future outcome

The ordinary observation operation is:

```protos
future.value()
```

Its behavior depends on the stable state.

For:

```text
resolved(value)
```

it returns the exact resolved value.

For:

```text
failed(error)
```

it signals the recorded Error in the consumer's current dynamic handler context.

For:

```text
cancelled
```

it signals a fresh standard `Cancelled` occurrence for that observation.

For:

```text
pending
```

it explicitly suspends the current execution until the Future becomes terminal
or cancellation of the waiting task is observed.

## Waiting does not require blocking an OS thread

A pending `value()` is a language-level suspension point.

The implementation may resume the task later without dedicating a blocked host
thread to the wait.

The semantic guarantee is about task suspension/resumption and observable
ordering, not the scheduler representation.

## Waiting cannot lose a terminal notification

The registration of a pending `value()` waiter and the Future's first terminal
transition are coordinated so that no lost-wakeup gap is observable.

Semantically, one of these happens:

```text
Future terminalizes first
    -> consumer does not remain suspended

waiter registration happens first
    -> terminalization makes that waiter eligible to resume
```

Multiple waiters can observe one Future independently.

One waiter must not be skipped merely because others are also waiting.

## Failed-Future observation re-signals the recorded Error

Suppose asynchronous work fails with an existing Error `problem`.

The failed Future records that exact domain-local Error.

Later:

```protos
Error.handle(
    () => {
        future.value()
    },
    (error) => {
        error === problem
    }
)
```

can observe that same Error identity in the same value domain.

The new `value()` observation is nevertheless a **new signaling event in the
consumer**.

It does not resume the producer, restore the producer's handler stack, or
recreate its continuation.

Repeated observations re-signal the same recorded Error object.

## Cancelled-Future observation is deliberately different

A cancelled Future stores the terminal state:

```text
cancelled
```

It does not store one permanent `Cancelled` Error instance.

Each call to:

```protos
cancelledFuture.value()
```

creates and signals a fresh standard Error occurrence delegating to
`Cancelled`.

Therefore repeated observations of one cancelled Future receive distinct Error
identities.

This keeps "stored failed Error" distinct from "observation of cancelled state".

## `then()` creates asynchronous continuation work

The composition operation is:

```protos
destination: source.then(transform)
```

After eager validation, `then()` creates a distinct continuation task plus a
fresh destination Future.

When a resolved source makes that continuation runnable, the transform is
invoked asynchronously with the source value:

```protos
source.then((value) => {
    value + 1
})
```

Completion of the source does not run transform inline inside the source's
terminalization path.

## A `then` transform is ordinarily invokable, not Closure-only

Unlike some Closure-specific Core protocols, `then(transform)` accepts a value
that satisfies the ordinary invocation/callability protocol.

It is not restricted to semantic Closure identity.

Validation is eager and does not invoke the transform.

The later continuation performs ordinary invocation when it actually runs, so
normal call lookup/mutation rules remain observable.

## Source failure or cancellation skips the transform

For a `then` continuation:

```text
source resolved(value)
    -> invoke transform(value)

source failed(error)
    -> destination fails with that Error

source cancelled
    -> destination becomes cancelled
```

The transform is not invoked for failed or cancelled source outcomes.

If the transform returns a Future, ordinary Future adoption flattens its eventual
outcome into the destination.

## `then` cancellation is downstream-only

Cancelling the destination Future requests cancellation of the continuation task.

It does **not** cancel the source Future.

Likewise, detaching the destination detaches only the continuation task represented
by that destination.

This is a useful general rule:

> Dependency does not imply upstream cancellation ownership.

## `Future.all(...)` coordinates multiple Futures deterministically

The standard aggregate operation is:

```protos
Future.all(a, b, c)
```

It returns a fresh non-task-backed aggregate Future.

When all sources resolve successfully, the aggregate resolves to a fresh Array:

```text
[aValue, bValue, cValue]
```

in **argument order**, not completion order.

For zero arguments:

```protos
Future.all()
```

returns an already-resolved Future containing a fresh empty Array.

## `Future.all` does not own its source Futures

The aggregate is an observation Future.

It does not:

```text
re-parent sources
detach sources
cancel sources
take ownership of their tasks
```

Cancelling the aggregate abandons only the aggregate observation.

The source Futures continue according to their own producers and ownership
contracts.

## `Future.all` failure/cancellation selection uses argument order

Core deliberately avoids choosing failure based on scheduler timing.

The aggregate uses a deterministic ascending-index frontier.

Conceptually, for:

```protos
Future.all(a, b, c)
```

a non-resolved outcome at `b` cannot become the aggregate outcome until `a` is
known to have resolved.

If `a` later fails, `a` wins because it has the lower argument index.

Physical completion order, callback timing, carrier choice, or polling order does
not choose the result.

## Core has no generic `Future.race` or `Future.select`

Core v0.1 deliberately does not expose a generic "whichever Future happened to
finish first" combinator.

Independent Future producers do not acquire a portable global total completion
order just because a program wants to select one.

A generic race based on scheduler or host callback timing would expose
implementation machinery as language semantics.

`Future.all(...)` is the deterministic Core multi-Future coordination primitive.

A future library/domain-specific selection facility can define its own explicit
semantic priority or ordering contract.

## Cancellation is cooperative

`Future.cancel()` requests cancellation.

It does not mean:

```text
interrupt arbitrary code immediately
roll back completed effects
guarantee cancelled terminal state before returning
```

For a pending task-backed Future, the request is recorded and observed at
portable cancellation boundaries.

`cancel()` is idempotent and returns the same Future object.

Calling it on an already-terminal Future leaves that terminal state unchanged.

## Cancellation has explicit observation boundaries

Portable cancellation boundaries include:

- before a newly created task begins ordinary Protos code;
- explicit suspension points;
- resume from suspension before ordinary Protos code continues;
- operations whose normative contract explicitly adds a cancellation-aware
  pending boundary.

Ordinary non-suspending code does not gain hidden cancellation points merely from:

```text
method calls
Closure calls
allocations
loop back-edges
JIT/interpreter polls
garbage collection
```

A CPU-bound computation that never reaches a cancellation boundary may therefore
finish normally after cancellation was requested.

## Cancellation never reverses completed effects

This principle is fundamental:

> Cancellation never reverses effects that have already occurred.

If an operation already crossed its semantic commitment boundary, cancellation
cannot pretend that commitment never happened.

Individual I/O and communication operations define their own commitment and
uncertainty rules.

Future cancellation composes with those contracts rather than overriding them.

## Cancelling a waiter does not cancel what it waits for

Suppose two tasks call:

```protos
source.value()
```

on the same pending Future.

Cancelling one waiting task only changes that consumer's control flow.

It does not automatically:

```text
cancel source
fail source
wake the other waiter as cancelled
rewrite source outcome
```

Waiting and producer ownership are separate.

## Structured concurrency is task-scoped

The most important ownership rule is:

> Structured execution scope belongs to asynchronous task execution, not to each
> ordinary synchronous invocation frame.

A task-backed asynchronous computation has one current structured scope.

Ordinary nested:

```text
Closure calls
methods
while callbacks
ensure body/cleanup calls
```

execute inside that same scope unless they start new asynchronous work.

Core does not expose a public Task/scope object merely to represent this
relationship.

## Child task-backed work is owned by default

When current task execution creates asynchronous task-backed work, that child is
owned by the current structured scope by default.

For example:

```protos
parent: (() => {
    child: (() => {
        slowWork()
    }).future()

    42
}).future()
```

The synchronous body of `parent` can reach its normal result while `child` is
still pending.

But the **parent asynchronous computation cannot terminalize normally** until its
non-detached owned child becomes terminal.

## Returning a Future from a synchronous call does not detach it

This is a subtle but essential rule.

Suppose a helper called inside an asynchronous computation creates a child:

```protos
makeChild: () => {
    (() => slowWork()).future()
}
```

and returns it:

```protos
child: makeChild()
```

The helper's synchronous return does not create or close a structured scope.

It does not:

```text
detach child
transfer child ownership
re-parent child
remove the ownership edge
```

The child remains owned by the surrounding asynchronous task scope that was
current when it was created.

No escape analysis or "returned value" rule changes that.

## Structured ownership limits lifetime, not result propagation

If an owner reaches otherwise-normal completion, it waits for every non-detached
owned child to become terminal.

However, a child's terminal:

```text
failed(error)
cancelled
```

does **not automatically** fail or cancel the owner merely because ownership
exists.

The owner observes child outcome explicitly through normal Future operations such
as:

```protos
child.value()
```

This avoids hidden "unobserved child failure" state.

Ownership says **how long work may remain outstanding**, not **which result the
parent must choose**.

## Returning, storing, or wrapping a child Future does not transfer ownership

All of these are ordinary value operations:

```text
return Future
store Future in slot
put Future in Array
wrap Future in another object
pass Future to synchronous helper
```

None of them automatically changes structured ownership.

Ownership changes only through a semantic rule that explicitly changes it, such
as `detach()` for task-backed Futures.

## `detach()` removes one structured ownership edge

For a still-pending task-backed Future:

```protos
child.detach()
```

removes the child task from its current parent's structured lifetime.

`detach()`:

- returns the same Future;
- is idempotent;
- does not cancel the Future;
- does not change its eventual outcome;
- does not manufacture a new owner.

Detachment is therefore a lifetime operation, not result transformation.

## `detach()` is a no-op for non-task-backed or terminal Futures

Not every Future has a task ownership edge.

For example, some I/O operations return non-task-backed Futures representing
backend work.

Calling `detach()` when there is no task ownership edge is a state-preserving
no-op.

Calling `detach()` on a terminal Future is likewise a no-op.

The absence of a detachable edge is not itself an Error.

## Detachment does not escape the Actor domain

A detached Actor-local child task may outlive the activation that created it.

It does **not** thereby become:

```text
Process-global work
RootActor work
a new Actor
runtime-global work
```

It still belongs to the same Actor execution domain.

If that Actor incarnation terminates, detached Actor-local tasks are still
subject to Actor-lifecycle cancellation.

Detachment removes a structured parent edge; it does not grant an independent
Actor-like lifetime.

## Cancellation unwind composes with `ensure`

When task cancellation is observed, unwind runs applicable `ensure` cleanup.

The Future cannot become terminally cancelled until required cleanup and
structured-cancellation obligations have completed.

As described in the previous chapter, the same already-delivered cancellation
request is not repeatedly delivered at suspension boundaries inside that cleanup.

If cleanup itself fails, that later Error can replace cancellation and the task's
Future becomes failed instead.

## A new child task creates its own structured scope

The ownership tree is recursive.

Conceptually:

```text
parent task scope
    |
    +-- child task
            |
            +-- grandchild task
```

When the child starts asynchronous execution, it has its own structured scope for
work that it creates.

This keeps the tree tied to asynchronous computations rather than synchronous
call-stack depth.

## Actor creation is not ordinary Future-child ownership

Actor lifetime is a different semantic boundary.

Creating an Actor does not automatically mean that Actor is a structured child
task whose lifetime is governed by the creator's Future scope.

Actors have their own lifecycle and supervision/communication model, covered in
the Actors chapter.

This distinction prevents structured task ownership and Actor identity from
becoming the same concept.

## Practical patterns

### Start asynchronous work and wait explicitly

```protos
work: (() => {
    calculate()
}).future()

result: work.value()
```

### Transform a successful result

```protos
answer: (() => 20).future().then((value) => {
    value + 22
})

answer.value()
```

### Flatten asynchronous transformation automatically

```protos
answer: (() => 20).future().then((value) => {
    (() => value + 22).future()
})

answer.value()
```

### Coordinate deterministic results

```protos
left: (() => 20).future()
right: (() => 22).future()

values: Future.all(left, right).value()
values[0] + values[1]
```

### Explicitly observe child failure when it matters

```protos
child: (() => {
    riskyWork()
}).future()

child.value()
```

Structured ownership keeps the child within the task lifetime, while `value()`
makes its result or failure relevant to the caller's control flow.

## Current executable lessons

The repository contains a focused Future tutorial:

- [`../../protos/tutorials/09-futures/01-async-closure.protos`](../../protos/tutorials/09-futures/01-async-closure.protos)
  shows the same Closure executed with `future()` and observed with `value()`;
- [`../../protos/tutorials/09-futures/02-compose-with-then.protos`](../../protos/tutorials/09-futures/02-compose-with-then.protos)
  demonstrates `then()` plus automatic adoption/flattening;
- [`../../protos/tutorials/09-futures/03-wait-for-many.protos`](../../protos/tutorials/09-futures/03-wait-for-many.protos)
  demonstrates deterministic `Future.all(...)` result ordering.

## Representative conformance evidence

The retained Future conformance suite includes focused cases such as:

- [`../../protos/tests/conformance/future/fresh-future-identity.protos`](../../protos/tests/conformance/future/fresh-future-identity.protos);
- [`../../protos/tests/conformance/future/failed-value-original-error-identity.protos`](../../protos/tests/conformance/future/failed-value-original-error-identity.protos);
- [`../../protos/tests/conformance/future/failed-value-repeated-error-identity.protos`](../../protos/tests/conformance/future/failed-value-repeated-error-identity.protos);
- [`../../protos/tests/conformance/future/cancelled-value-fresh-error.protos`](../../protos/tests/conformance/future/cancelled-value-fresh-error.protos);
- [`../../protos/tests/conformance/future/cancel-before-start.protos`](../../protos/tests/conformance/future/cancel-before-start.protos);
- [`../../protos/tests/conformance/future/all-preserves-input-order.protos`](../../protos/tests/conformance/future/all-preserves-input-order.protos);
- [`../../protos/tests/conformance/future/all-empty.protos`](../../protos/tests/conformance/future/all-empty.protos).

These are executable evidence for current implementation behavior. The
specification remains authoritative.

## Practical rules to remember

1. There are no `async` functions or `await` keyword in Core v0.1.
2. Execute a Closure asynchronously with ordinary `closure.future()`.
3. Distinct Future-producing invocations normally produce distinct Future
   identities.
4. A Future is exactly pending, resolved, failed, or cancelled; first terminal
   transition wins.
5. `value()` returns a resolved value, re-signals a stored failed Error, creates
   a fresh `Cancelled` Error per cancelled observation, or suspends while pending.
6. A failed Future stores the exact same-domain Error; observing it does not
   restore producer control state.
7. Future resolution automatically adopts/ flattens a Future result.
8. Adoption transfers eventual outcome, not Future identity, ownership,
   detachment, or upstream cancellation.
9. `then()` creates asynchronous continuation work; source failure/cancellation
   bypasses the transform.
10. `then` cancellation and detachment are downstream-only.
11. `Future.all(...)` preserves argument order and uses deterministic
    argument-index failure/cancellation selection.
12. Core intentionally has no generic scheduler-timing-based Future race/select.
13. Cancellation is cooperative and never reverses completed effects.
14. Cancelling a waiter does not cancel the Future it is observing.
15. Structured ownership belongs to asynchronous task scopes, not synchronous
    call frames.
16. Returning, storing, or wrapping a task-backed Future does not detach or
    transfer its ownership edge.
17. Normal owner completion waits for non-detached children to become terminal,
    but child failure/cancellation is not implicitly adopted as the owner's
    result.
18. `detach()` removes only the current structured task ownership edge and never
    grants an Actor-independent lifetime.
19. Cancellation unwind runs applicable `ensure` cleanup before terminal
    cancellation.
20. Treat ownership/lifetime and result/error observation as separate concerns.

## Normative references

For exact behavior, consult:

- [`../../spec/concurrency/FUTURES_AND_TASKS.md`](../../spec/concurrency/FUTURES_AND_TASKS.md)
  for Future identity/state, `future`, `value`, adoption, `then`, `all`,
  cancellation, structured ownership, detachment, and Actor-lifecycle
  integration;
- [`../../spec/semantics/CALLABLES.md`](../../spec/semantics/CALLABLES.md) for
  ordinary `Object.future` placement and invocation/callability rules;
- [`../../spec/semantics/ERRORS.md`](../../spec/semantics/ERRORS.md) for stored
  Error identity and consumer-side re-signaling;
- [`../../spec/semantics/EXECUTION_AND_CONTROL.md`](../../spec/semantics/EXECUTION_AND_CONTROL.md)
  for `ensure` cleanup during cancellation unwind;
- [`../../spec/concurrency/ACTORS.md`](../../spec/concurrency/ACTORS.md) for
  Actor-domain execution and termination consequences;
- [`07-errors-handlers-ensure-and-resource-lifetime.md`](07-errors-handlers-ensure-and-resource-lifetime.md)
  for the Error/cleanup model that Future observation and cancellation reuse.

Those documents define the language. This chapter supplies the programmer-facing
model for eventual results, cooperative cancellation, and structured task
lifetime.
