# Isolated Parallel Execution

> **Status:** Non-normative programming guide
>
> **Normative owner:** `spec/concurrency/PARALLEL_EXECUTION.md`
>
> Related normative owners: `spec/concurrency/FUTURES_AND_TASKS.md`,
> `spec/concurrency/ACTORS.md`, `spec/semantics/CALLABLES.md`, and
> `spec/semantics/VALUES_AND_COLLECTIONS.md`

Protos has more than one way to make progress concurrently, because the
mechanisms solve different problems.

The central model for explicit CPU parallelism is:

> `parallel(...)` runs computation in an isolated P execution domain. Inputs
> cross by the P transfer/snapshot rules, the projected computation has no
> implicit access to the caller's mutable lexical state or authority, and the
> result is observed through an ordinary Future.

"P" is useful terminology for the semantic isolation domain. It is **not** a
public object, keyword, namespace, worker handle, scheduler handle, or new
programming universe.

## `future()`, `parallel()`, and Actors solve different problems

A Closure can request ordinary asynchronous Actor-local work:

```protos
future: work.future()
```

or isolated CPU-parallel work:

```protos
future: work.parallel(arguments...)
```

An Actor is a third concept.

A useful comparison is:

| Mechanism | Mutable execution domain | Main purpose | Persistent identity / mailbox | Result |
| --- | --- | --- | --- | --- |
| ordinary call | current execution | synchronous computation | no | value/control transfer |
| `closure.future()` | caller Actor domain | cooperative asynchronous work | no | Future |
| `closure.parallel(...)` | fresh isolated P domain | CPU-parallel computation | no | Future |
| Actor | its own Actor domain | persistent isolated state + messaging | yes | communication/lifecycle protocols |

`future()` does not isolate ordinary Actor-local mutable state merely because it
runs asynchronously.

`parallel()` deliberately does isolate the computation.

Actors add persistent identity, mutable state, mailbox/communication, and
lifecycle semantics that P does not have.

## There is no public `P` object

Core v0.1 does not expose:

```text
P
P.spawn
P.current
Worker
Executor
ParallelContext
```

and it adds no parallel-execution keyword.

The public fundamental submission operation is the ordinary message:

```protos
closure.parallel(arguments...)
```

Parallel Array algorithms are Standard Library functions of
`std:collections/Array` built on `closure.parallel(...)`; they are described
later in this chapter.

The implementation can use threads, carriers, pools, work stealing, or other
machinery internally. That machinery is not the programmer-visible model.

## `parallel` is an ordinary Closure-specific protocol

The standard `parallel` selector follows ordinary object lookup.

Its standard behavior requires a semantic Closure receiver.

For example:

```protos
square: (x) => {
    x * x
}

resultFuture: square.parallel(12)
```

The call returns a Future in the caller's execution domain. The isolated
computation itself executes inside a P domain.

The Future therefore composes with the Future operations from the previous
chapter:

```protos
result: resultFuture.value()
```

## The caller Closure is projected, not captured by reference

This is the most important difference from ordinary Closure execution.

Consider:

```protos
factor: 10

work: (x) => {
    x * factor
}
```

An ordinary invocation can use the lexical `factor`.

P does **not** obtain that caller lexical context as mutable shared state merely
because `work` was originally created there.

`parallel(...)` executes a fresh P-local projection of the Closure's executable
definition.

The projected computation has no lexical edge back to the caller's:

```text
mutable captured context
current `this`
return home
methodHome
dynamic Error handlers
module context
Actor-local state
```

If the parallel computation needs data, pass that data explicitly.

## Pass parallel inputs explicitly

Write:

```protos
factor: 10

work: (x, multiplier) => {
    x * multiplier
}

result: work.parallel(4, factor).value()
```

rather than relying on ambient caller capture.

This makes the P boundary visible at the call site.

The explicit arguments are transferred according to the P value-transfer rules.

## Input snapshotting finishes before submission returns

For transferable mutable object graphs, the P input snapshot is established
before a successful `parallel(...)` invocation returns its Future.

Conceptually:

```text
caller values
    |
    | validate + snapshot/transfer
    v
P-local input graph
    |
    | execute independently
    v
P-local result
    |
    | transfer back
    v
caller-domain Future outcome
```

Later caller mutation does not alter the already-transferred P input.

Likewise, P-local mutation does not mutate the caller's original ordinary object
graph through a shared alias.

An implementation may optimize copying internally only when this isolation
remains observationally exact.

## `freeze()` is not a P-transfer declaration

A frozen object is not automatically a general cross-P sharing capability.

Freezing is the ordinary object-mutation operation defined by the object model.

P transferability and physical-sharing freedom are separate rules.

Do not use:

```protos
object.freeze()
```

as a way to "authorize" arbitrary P access to caller state or resources.

## Non-parallel values fail explicitly

Not every value has a P-transfer contract.

In particular, Process authority and open I/O/native/resource capabilities do
not cross into P merely because they are reachable from an argument graph.

Attempting to transfer a value outside the supported P domain fails with the
standard `NonParallelValue` category.

This is an important authority rule:

> P receives only explicitly transferred data that the P contract permits. It
> acquires no ambient Actor, Process, I/O, or scheduler authority.

That keeps CPU-parallel computation from silently inheriting capabilities.

## P failures are Future failures

A parallel computation does not throw synchronously into the caller after
successful submission merely because its P-local body later fails.

The caller receives a Future.

A P-local unhandled Error becomes the appropriate failed Future outcome after
crossing the defined result boundary.

The caller observes it through ordinary Future behavior:

```protos
Error.handle(
    () => {
        resultFuture.value()
    },
    (error) => {
        handleFailure(error)
    }
)
```

Dynamic handler frames from the caller are not copied into P.

## P has no caller return home

A P-projected Closure does not carry the caller's non-local-return authority into
the isolated execution domain.

The P execution gets its own valid local control context.

Parallel computation therefore cannot use a captured caller return home as a
hidden cross-domain control-transfer channel.

This follows the broader rule that concurrency boundaries transfer permitted
values, not arbitrary execution contexts.

## `parallel()` and structured ownership still compose

The Future returned by isolated parallel execution participates in the standard
Future/task ownership model.

If task-backed parallel work is created while an asynchronous task scope is
current, it is owned by that structured scope under the ordinary Future rules.

This means P isolation does **not** create an unrelated lifetime universe.

Isolation answers:

```text
what mutable state can this computation directly see?
```

Structured ownership answers:

```text
which asynchronous scope bounds this child work's lifetime?
```

Keep those questions separate.

## Cancellation is cooperative

Cancelling the Future returned by parallel work requests cancellation under the
ordinary Future and P cancellation contracts.

It does not authorize arbitrary mid-instruction interruption and it does not roll
back effects that have already committed under a specialized P operation.

The first terminal Future transition remains stable.

Applicable `ensure` cleanup and structured-lifetime rules continue to compose as
defined by the Future/Task and Error chapters.

## Nested parallelism stays explicit

P code may itself invoke a standard parallel operation where that operation is
permitted.

That does not expose physical worker identity or require one host thread per
nested operation.

The runtime is free to schedule or help nested P work in any conforming way.

The programmer-visible meaning remains explicit isolated computation plus
deterministic operation semantics.

## Array parallel algorithms are Standard Library functions

Core v0.1 keeps only the minimal isolated-execution mechanism
(`closure.parallel(...)` plus ordinary Futures). Parallel collection algorithms
are Standard Library policy built on it. The `std:collections/Array` module
provides:

```protos
Arrays: import("std:collections/Array")

Arrays.parallelMap(array, worker, arguments...)
Arrays.parallelFilter(array, predicate, arguments...)
Arrays.parallelFindIndex(array, predicate, arguments...)
Arrays.parallelReduce(array, reducer, arguments...)
Arrays.parallelSort(array, less, arguments...)
```

Every operation returns a Future. Core Arrays have no `parallel...` selectors:
`array.parallelMap(...)` signals `SlotNotFound`.

They sit beside the sequential `map`, `filter`, `findIndex`, `reduce`, and
`sort` functions of the same module. The parallel variants run each callback
invocation in isolated P through `closure.parallel(...)` and combine the
resulting Futures with deterministic, scheduling-independent result and failure
rules. Because they are ordinary Protos source, they introduce no privileged
runtime path; the module source documents each contract.

## `parallelMap` preserves logical source order

Worker invocations may execute physically in any order.

The result Array is nevertheless determined by source index.

For:

```protos
values: [1, 2, 3]

result: Arrays.parallelMap(values, (value) => {
    expensiveTransform(value)
}).value()
```

the logical result positions correspond to indexes `0`, `1`, and `2` of the
submission snapshot.

Whichever worker happens to finish first does not reorder the result.

## `parallelFilter` preserves source order

Predicates can execute in parallel, but selected elements appear in their
original logical source order.

A predicate result must be exact canonical `true` or exact canonical `false`.

Any other normal result is an `InvalidPredicateResult` failure for that logical
source position.

There is no truthiness conversion.

## `parallelFindIndex` means lowest matching logical index

`parallelFindIndex` is not "return the worker that wins the race."

Physical execution can happen in any order, while the logical search order is
ascending source index.

Therefore the result is the lowest logical index whose predicate is `true`, once
all earlier positions are known not to determine another outcome.

If no predicate matches, the normal result is `null`.

This distinction preserves deterministic behavior across machines and
schedulers.

## Parallel failure precedence is logical, not scheduler-timing based

When multiple independent parallel callbacks can fail, physical completion time
must not decide which failure becomes observable where the normative operation
defines a logical order.

This is the same design principle seen in `Future.all(...)`:

> Concurrency may change when work becomes available; scheduler accident should
> not redefine which logical outcome the program observes.

The parallel Array functions inherit this from `Future.all(...)`'s ascending
index frontier and from examining outcomes in ascending index order, so a
higher-index callback that finishes first cannot steal lower-index failure
precedence.

## `parallelReduce` uses a fixed reduction tree

Parallel reduction cannot preserve an ordinary sequential left-fold callback
order while also allowing general parallel combination.

`parallelReduce` therefore fixes one deterministic reduction structure: each
round combines adjacent pairs `(0, 1)`, `(2, 3)`, ... from left to right and
carries an odd final value unchanged, rather than combining whichever pair of
workers happens to become ready first.

This means a reducer used with `parallelReduce` must be suitable for that defined
parallel reduction contract.

Do not assume that side effects or non-associative arithmetic will behave like
the sequential Standard Library `reduce`.

If exact left-fold semantics are required, use the sequential operation.

## `parallelSort` is deterministic and stable

`parallelSort` produces exactly the result, comparison order, and failures of
the sequential `sort` with the same comparator, while running every comparator
invocation in its own isolated P domain.

The comparator result must be canonical Boolean in both comparison directions.

Invalid normal comparator results fail with `InvalidComparatorResult`.

A contradictory ordering in which both directions claim strict precedence fails
with `InvalidComparatorOrder`.

Equal/incomparable-by-strict-less elements retain the stable ordering required by
the standard operation.

## Array callbacks receive transferred values

The Array parallel functions snapshot the source elements, the callback, and
the extra arguments when they are called, before returning their Future.

Extra arguments are explicit:

```protos
Arrays.parallelMap(values, worker, scale, offset)
```

They cross the same P boundary rather than becoming an excuse to share caller
lexical state.

This makes the high-level Array operations conveniences over the same isolation
model rather than a separate parallel semantics.

## Ordinary Array mutation is not the shared-write mechanism

P does not generally hand isolated workers mutable aliases into one caller Array
or arbitrary object graph.

Doing so would require a portable alias/ownership model for overlapping mutable
graphs.

Core v0.1 deliberately does not define:

```text
Array.parallelRange
Bytes.parallelRange
Object.partition
borrowed mutable object slice
runtime alias-analysis API
```

or any other writable-partition facility.

General P inputs use snapshot/value isolation.

## Bytes follows the same snapshot rule

`Bytes` has no special writable-partition operation either. There is no
`parallelRange` and no `ByteRegion`.

A P worker that receives `Bytes` receives its own isolated snapshot, exactly as
for any other mutable input. To transform part of a byte sequence in parallel,
pass the relevant bytes as explicit arguments and return the transformed bytes
as the Future result; the caller decides how to combine the results.

Ordinary `Bytes` operations in the caller are never blocked, rejected, or
otherwise affected by P work that is still running.

## Physical carrier count is not program semantics

The current implementation uses a bounded process-local carrier pool.

That is useful implementation evidence, not an API promise that programs may
observe or select a particular worker count.

Correct programs must not depend on:

```text
host thread identity
carrier identity
pool queue order
callback completion timing
work-stealing order
```

The implementation may change those choices while preserving observable Protos
semantics.

## P is local CPU parallelism, not remote placement

`Closure.parallel(...)` is the Core isolated parallel-computation mechanism in
the current Process/runtime domain.

It does not mean:

```text
send this Closure to any cluster node
discover compute workers
pick a remote machine
start an Actor remotely
```

Distributed identity, transport, Actor routing, Node/Cluster policy, and service
discovery are separate concerns.

Keeping them separate lets local CPU parallelism remain useful without making
every parallel operation pay for distributed coordination.

## Choose the mechanism from the state/lifetime problem

Use an ordinary call when the computation is synchronous.

Use `future()` when work should be asynchronous but belongs to the same Actor
mutable execution domain.

Use `parallel()` when CPU work should be isolated from caller mutable state and
can operate on explicitly transferable inputs.

Use an Actor when you need persistent isolated mutable state with identity,
mailbox communication, and lifecycle.

This choice is semantic, not a performance hint attached to otherwise identical
execution.

## Representative implementation evidence

The current reference runtime implements the public isolated-parallel surface in:

- [`../../src/main/java/com/guillermomolina/protos/execution/ProtosParallelRuntime.java`](../../src/main/java/com/guillermomolina/protos/execution/ProtosParallelRuntime.java).

Its focused mechanism tests include:

- [`../../src/test/java/com/guillermomolina/protos/execution/ProtosParallelExecutionTest.java`](../../src/test/java/com/guillermomolina/protos/execution/ProtosParallelExecutionTest.java),

which guards the absence of a public `P`, presence of the closed standard
parallel selectors, bounded implementation carriers, caller-domain Future
results, and scheduler-independent logical failure selection.

The architecture ledger classifies this machinery as a concurrency/runtime
boundary rather than ordinary source-expressible library behavior:

- [`CORE_NATIVE_BOUNDARY.md`](https://github.com/guillermomolina/protos-project-docs/blob/main/docs/project/architecture/CORE_NATIVE_BOUNDARY.md).

These are current implementation evidence. The normative specification remains
authoritative.

## Practical rules to remember

1. `parallel(...)` means isolated P computation, not shared-memory execution of
   the caller Closure.
2. P is a semantic domain name, not a public Protos object or keyword.
3. `closure.future()` remains Actor-local asynchronous work; `closure.parallel`
   creates isolated CPU-parallel work; Actors add persistent identity and
   messaging.
4. The parallel Closure is projected without caller lexical capture, `this`,
   return-home, handler, or module-context authority.
5. Pass required data explicitly as arguments.
6. Transfer/snapshot of accepted inputs completes before successful submission
   returns its Future.
7. Caller mutations after submission do not mutate the P-local input snapshot.
8. Unsupported capabilities/values fail explicitly with `NonParallelValue`;
   P receives no ambient Process/I/O/scheduler authority.
9. Parallel results and failures return through ordinary Futures and structured
   ownership rules.
10. Cancellation is cooperative and does not roll back committed effects.
11. `std:collections/Array` parallel functions return Futures and produce deterministic logical
    outcomes independent of physical worker completion order.
12. `parallelMap` and `parallelFilter` preserve source index/order.
13. `parallelFindIndex` selects the lowest logical matching index, not the
    fastest worker.
14. `parallelReduce` uses its fixed adjacent-pair reduction tree, not
    sequential left-fold callback order.
15. `parallelSort` is stable and enforces strict canonical-Boolean comparator
    laws.
16. No value family, including Arrays, Bytes, and arbitrary mutable object
    graphs, gains a shared-write partitioning API; P inputs are snapshots.
17. Physical carriers, threads, queues, and scheduling order are implementation
    details.
18. P is local isolated computation, not a remote-placement or service-discovery
    API.
19. Choose between call, Future, P, and Actor according to state isolation,
    lifetime, and communication semantics rather than syntax familiarity.

## Normative references

For exact behavior, consult:

- [`../../spec/concurrency/PARALLEL_EXECUTION.md`](../../spec/concurrency/PARALLEL_EXECUTION.md)
  for P isolation, Closure projection, transferability, deterministic
  failure/result rules, the absence of writable partitioning, cancellation, and
  scheduling independence;
- [`../../protos/lib/collections/Array.protos`](../../protos/lib/collections/Array.protos)
  for the Standard Library parallel Array function contracts;
- [`../../spec/concurrency/FUTURES_AND_TASKS.md`](../../spec/concurrency/FUTURES_AND_TASKS.md)
  for the Future result, observation, cancellation, and structured
  ownership model reused by P-produced Futures;
- [`../../spec/concurrency/ACTORS.md`](../../spec/concurrency/ACTORS.md) for the
  contrasting Actor mutable execution domain, identity, and lifecycle;
- [`../../spec/semantics/CALLABLES.md`](../../spec/semantics/CALLABLES.md) for
  ordinary `Object.parallel` selector placement and Closure receiver-domain
  behavior;
- [`../../spec/semantics/VALUES_AND_COLLECTIONS.md`](../../spec/semantics/VALUES_AND_COLLECTIONS.md)
  for Array/Bytes collection ownership;
- [`08-futures-and-structured-concurrency.md`](08-futures-and-structured-concurrency.md)
  for the Future and task-lifetime model used by parallel results.

Those documents define the language. This chapter supplies the programmer-facing
model for choosing and using isolated CPU parallelism without importing a
shared-memory thread model into Protos.
