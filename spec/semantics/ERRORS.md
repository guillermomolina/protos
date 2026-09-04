# Protos Errors v0.1

Language version: 0.1
Status: Draft
Last updated: 2026-09-04

This document is the primary normative owner of Core Error objects, signaling, handling, propagation, identity, standard error-prototype taxonomy, and error-control semantics.

The material below is migrated without intended semantic change from `../PROTOS_LANGUAGE_SPEC.md`. Legacy section titles and numbering are retained so existing references remain understandable.

## 25. Errors

Errors are objects.

Expected domain failures may be represented using ordinary values.

Exceptional conditions use signaling:

```js
error.signal()
```

### Standard Signaling Protocol

The standard `Error` prototype provides the ordinary zero-argument message
`signal()`.

An ordinary error object may therefore be signaled with:

```js
error.signal()
```

The standard behavior is inherited through ordinary delegation. Its receiver
must be `Error` itself or an object whose delegation chain contains `Error`.
Calling the standard signaling behavior with any other receiver is a protocol
error; copying, composing, extracting, or otherwise reusing the implementation
of `Error.signal` does not make a non-error object signalable.

The receiver object itself is the object presented to handler matching and to
the selected handler. Signaling does not implicitly clone, wrap, replace, or
convert it, and does not add Protos-visible slots or mutate its ordinary
language-visible state. Implementations may retain implementation-private
diagnostic information such as stack metadata only when that information is not
observable as additional Core object structure or taxonomy.

`Error.signal()` never returns normally to the activation that invoked it. If a
matching handler is found, Core's unwinding semantics transfer control to that
handler and abandon the signaling continuation. If no matching handler is
found, the error reaches the applicable outermost execution boundary according
to the existing unhandled-error rule.

### Standard failure occurrence objects and identity

Core distinguishes signaling an Error object that already exists from a
standard language/runtime/domain failure rule that must manufacture an Error
outcome.

A **standard failure occurrence** is one execution of a normative failure rule
for which the specification does not already identify a particular Error object
to propagate. Every standard failure occurrence produces a fresh ordinary Error
object with fresh identity:

- if the rule says only that it "signals an Error", "signals an error", or
  otherwise promises no narrower standard category, the fresh object's immediate
  delegation parent is the standard `Error` prototype;
- if the rule says that it signals/fails with a named standard error prototype
  `X`, the fresh object's immediate delegation parent is `X`;
- the generated object is never `Error`, `X`, or another standard category
  prototype itself, and it is never an Error instance reused from a distinct
  standard failure occurrence.

Core v0.1 therefore has **no singleton standard failure instances**. Standard
Error prototypes are shared category/protocol objects, not cached instances for
runtime failures. Two independently occurring lookup failures, argument
failures, cancellation observations, I/O failures, or other standard failures
are distinct by `===` even when they have the same standard prototype ancestry.

Freshness is an observable semantic requirement, not a physical-allocation
requirement. An implementation may scalar-replace, virtualize, elide, or otherwise
optimize a generated Error when doing so cannot affect any Core observation. It
must nevertheless preserve distinct identity whenever identity can become
observable through `===`, identity hashing, handler installation, storage,
Future failure, reflection defined elsewhere, or ordinary object reachability.
Pooling or caching that makes two distinct standard failure occurrences observe
the same object is non-conforming.

Conversely, when semantics already identifies an Error object `e`, signaling or
re-signaling that failure uses **exactly `e`**. In particular:

- `e.signal()` signals `e` itself;
- a handler receives that exact `e`;
- a same-domain failed Future records and later re-signals that exact `e` under
  the Future rules;
- re-signaling an Error never manufactures a new instance merely because a new
  handler search begins.

`Error` and every standard Error prototype are themselves ordinary Error objects
and are legal explicit receivers of `signal()`. Thus source code may explicitly
execute `Error.signal()` or `Cancelled.signal()`, in which case the receiver
prototype itself is the exact signaled object. That explicit user-directed act
does not authorize a standard failure rule to reuse the prototype as its failure
instance.

Unless a domain contract explicitly defines visible Error payload slots, a
standard-generated failure instance is not required to have any own
Protos-visible slot. Diagnostic messages, native error codes, stack data, paths,
handles, authority-bearing backend objects, and similar implementation data must
not become visible merely because an implementation finds them useful for
logging. A separately standardized diagnostic/reflection facility may expose only
what its own contract permits.

Error identity and hashing follow the ordinary object identity rules. Once a
particular Error object exists, its identity is stable for as long as the object
is observably reachable. `identityHashOf(error)` therefore has the same stability
and collision rules as for other objects; fresh Error identity does not imply a
unique numeric hash value.

### Boundary crossing and recorded failures

Freshness is defined per failure occurrence in the value/isolation domain where
that occurrence is created. It does not create a cross-boundary identity channel.

When an Error is transferred as ordinary data or as a P failure through a
boundary whose value-transfer contract reconstructs object graphs, the receiving
domain gets the boundary-defined reconstructed Error object. The source-domain
Error and reconstructed receiving-domain Error are not identical. Aliasing and
cycles inside one transferred graph follow that boundary's ordinary graph-transfer
rules; no special remote identity, proxy, or Error singleton is introduced.

Actor fatal failure is different: the unhandled Error that terminates an Actor
incarnation remains in that Actor domain. It is not implicitly transferred,
wrapped, copied, or re-signaled to a caller. A caller observes only the
communication/lifecycle outcome promised by the Actor contract, such as a fresh
caller-domain `RequestOutcomeUncertain` occurrence when applicable.

When a domain explicitly records one Error as one stable failure outcome, later
same-domain observations of **that recorded outcome** preserve the exact recorded
Error rather than creating fresh failures. This applies to `failed(error)`
Futures and to any I/O lifecycle contract that explicitly records one terminal
failure cause. A later operation that merely encounters the same *category* but
is not observing the same recorded outcome is a new failure occurrence and gets
a fresh Error object.

### Core v0.1 non-resumable error model

Core v0.1 has one standard language-level failure family rooted at `Error`.
It does not define a separate standard `Exception` hierarchy or a second
continuable-exception category.

`Error.signal()` is non-resumable. Once signaling begins, the continuation at
the signaling point is abandoned. A matching handler may determine the result
of the enclosing `handle(...)` operation under the existing unwinding rules,
but it cannot return, resume, retry, or supply a value back into the abandoned
`Error.signal()` invocation.

Core v0.1 therefore defines no standard `resume`, `retry`, `restart`,
`useValue`, continuable-signal, or arbitrary continuation-reentry operation for
error handling. A user/library object may of course define ordinary messages
with such names, but those messages have no privileged relationship to Core
error signaling.

This boundary is deliberate rather than a claim that resumable recovery is
intrinsically undesirable. A future standard may define explicit recovery
continuations or restart-style mechanisms, but doing so requires a separate
semantic contract for recovery availability, dynamic extent, effects already
performed, suspension, Futures, Actor/P boundaries, and cancellation. Such a
future mechanism must not retroactively make Core v0.1 `Error.signal()`
resumable.

The standard method takes no arguments. Constructing or enriching an error is
separate ordinary object/protocol behavior and occurs before signaling. Core
does not define string-to-error coercion, prototype-to-instance coercion, or any
other implicit condition designator.

The runtime's own language-defined failures use the same semantic signaling
operation directly; they are not specified as an overridable message send to
`signal()`. Overriding a user object's `signal` slot therefore affects ordinary
message dispatch to that object but cannot redefine how slot lookup failures,
invalid assignments, cancellation observation, or other normative runtime
failures transfer control.

This keeps error signaling inside the ordinary object/delegation model while
preserving one closed semantic category of signalable Core errors rooted at
`Error`.

Handlers are dynamically installed in the execution environment of closures.

No fundamental `try`, `catch`, `throw`, or `finally` keywords are required.

### Standard Handler Installation Protocol

Core v0.1 exposes handler installation through an ordinary message provided by
the standard `Error` prototype:

```js
matchPrototype.handle(body, handler)
```

`matchPrototype` is the receiver. `Error` itself and ordinary error prototypes
below `Error` therefore use the same protocol through normal delegation. The
receiver must be `Error` or have `Error` in its delegation chain.

`body` and `handler` are Closures. The call establishes exactly one dynamically
scoped unwinding handler whose match prototype is `matchPrototype`, then invokes
`body` with no arguments.

Ordinary call evaluation happens before installation. The receiver expression,
the `body` argument expression, and the `handler` argument expression are
evaluated left-to-right before the handler becomes active. Errors signaled while
evaluating those expressions are therefore not handled by the handler being
installed.

If `body` completes normally, its result is the result of `handle`, and the
handler is removed without invoking `handler`.

For handler matching, an Error object's matching chain consists of the signaled
object itself followed by its ordinary delegation parents. A handler matches
when its `matchPrototype` is any object in that chain. Category matching is
therefore ordinary delegation matching; exact identity is the degenerate case in
which `matchPrototype === signaledError`. Core defines no second hidden
class/tag/identity matching channel.

If an error is signaled while the protected dynamic extent is active and a
handler matches by that rule, the dynamically innermost matching handler is
selected before any older matching handler. Core handlers are unwinding:
execution of the signaling continuation and the remaining protected computation
is abandoned. The selected handler frame is removed before `handler` is invoked,
and `handler` receives the exact signaled Error object as its single argument. If
`handler` then completes normally, its result is the result of the `handle` call.

Because the selected handler is inactive while its handler Closure executes, an
error signaled by that Closure is searched only against still-active outer
handlers. The same handler cannot recursively catch its own failure merely
because the new error also matches `matchPrototype`.

### Selected handler deactivation precedes unwind cleanup

Selecting a matching handler consumes that dynamic handler frame before control
begins unwinding protected scopes toward the handler boundary.

Therefore any `ensure` cleanup executed while unwinding toward the selected
handler runs with that selected handler already inactive. If such cleanup
signals a new `Error`, the cleanup Error follows ordinary handler search among
still-active outer handlers and any handlers explicitly installed by the cleanup
itself. It cannot select the already-consumed handler frame.

This ordering applies even when the cleanup Error would also match the selected
handler's `matchPrototype`. The selected handler does not recursively catch a
failure that occurs while unwinding toward itself.

If all crossed cleanup completes normally, the originally selected handler is
invoked with the original Error under the existing rules. If cleanup instead
signals a new Error, the general cleanup-Error precedence rule supersedes the
original transfer; the originally selected handler is not invoked for either
Error unless some separate still-active installation selects it independently.

Nested `handle` calls define ordering structurally: the dynamically innermost
matching handler is selected first. Core v0.1 therefore needs no separate
same-scope handler-list ordering rule; multiple handlers are expressed by
ordinary nesting.

A nonmatching error passes through the installed frame and continues outward
through the normal dynamic handler search. Non-local return, cleanup through
`ensure`, and other existing unwind behavior remain ordinary control transfers:
leaving the protected dynamic extent removes the installed handler.

Dynamic handler state is task-local execution state, not Actor-global state and
not a property captured by a Closure. If the protected task explicitly suspends
while the `handle` call remains active, the handler remains part of that same
task's suspended continuation and is active again when that task resumes. Other
Actor-local tasks that run while it is suspended cannot observe or use that
handler.

Creating a distinct asynchronous Future/task inside the protected scope does not
copy or inherit the handler into that task. An unhandled failure in such a task
fails its Future according to the Future rules. If a consumer later observes
that failed Future through `value()`, the recorded error is re-signaled in the
consumer's then-current dynamic handler context. Actor boundaries likewise never
carry dynamic handlers.

This protocol adds no `try`, `catch`, `throw`, or `finally` syntax and no second
handler type system. User and library error prototypes use ordinary delegation,
and richer handling abstractions may be built from this single primitive
dynamic-scope mechanism.

An unhandled error propagates until an appropriate handler is found or the
outermost execution boundary is reached.

Core v0.1 retains no resumable-condition authority or continuation state for
error signaling. A future standard may add an explicit recovery/restart facility
only through its own normative control-state contract; such a facility must not
reinterpret existing Core `Error.signal()` operations as resumable.

### Core Error Taxonomy

Core v0.1 defines one mandatory root error prototype: `Error`. `Error` is an
ordinary standard-prelude object whose delegation parent is `Object`.

Every object signaled as a Core language/runtime error must have `Error` in its
delegation chain. Standard error prototypes named normatively by Core or a
normative domain model are ordinary objects in that chain. Their portable parent
relations are fixed below; implementations must not insert additional
Protos-visible ancestors between those prototypes.

The standard prototypes required by Core v0.1 are:

| Standard prototype | Immediate parent | Trigger/consequence owner |
| --- | --- | --- |
| `Error` | `Object` | this document |
| `SlotNotFound` | `Error` | `EXECUTION_AND_CONTROL.md` / grammar lookup rules |
| `InvalidReturn` | `Error` | `CALLABLES.md` |
| `Cancelled` | `Error` | `../concurrency/FUTURES_AND_TASKS.md` |
| `FutureResolutionCycle` | `Error` | `../concurrency/FUTURES_AND_TASKS.md` |
| `RequestOutcomeUncertain` | `Error` | `../concurrency/ACTORS.md` |
| `NonTransferableValue` | `Error` | `../concurrency/ACTORS.md` |
| `NonParallelValue` | `Error` | `../concurrency/PARALLEL_EXECUTION.md` |
| `InvalidPredicateResult` | `Error` | `../concurrency/PARALLEL_EXECUTION.md` |
| `InvalidComparatorResult` | `Error` | `../concurrency/PARALLEL_EXECUTION.md` |
| `InvalidComparatorOrder` | `Error` | `../concurrency/PARALLEL_EXECUTION.md` |
| `ParallelRegionOverlap` | `Error` | `../concurrency/PARALLEL_EXECUTION.md` |
| `ParallelRegionInUse` | `Error` | `../concurrency/PARALLEL_EXECUTION.md` |
| `ParallelRegionOutsideP` | `Error` | `../concurrency/PARALLEL_EXECUTION.md` |
| `IOError` | `Error` | `../io/IO_CORE.md` and the modular I/O documents |
| `InvalidIOArgument` | `IOError` | `../io/IO_CORE.md` |
| `IOLifecycleError` | `IOError` | `../io/IO_CORE.md` |
| `IOCapacityExhausted` | `IOError` | `../io/IO_CORE.md` / `../io/BYTE_IO.md` |
| `EncodingError` | `IOError` | `../io/TEXT_IO.md` / `../io/IO_CORE.md` |
| `LineTooLong` | `IOError` | `../io/TEXT_IO.md` / `../io/IO_CORE.md` |

This table owns the existence and parent relation of those standard Error
prototype bindings. The referenced domain document owns when the corresponding
category is used and any domain-specific effect, cancellation, commitment, or
lifecycle consequence. A domain document need not and must not redefine the
identity/construction rule for standard failure instances.

The list is deliberately minimal. In particular, Core v0.1 does **not** promise
separate standard prototypes merely for ordinary assignment/creation failure,
arity/binding failure, frozen/closed object mutation, index/range failure, Map
lookup/update failure, filesystem target absence/existence/permission, native
path failure, or generic backend failure unless another normative rule names one
of the tabled categories. Where those rules say only `Error`, portable code may
match only `Error` (or a user-defined ancestor it deliberately introduced).

This rule makes the portable taxonomy deliberately shallow. A specification may
introduce a deeper standard hierarchy only by stating that hierarchy
normatively; an implementation must not invent extra Protos-visible intermediate
error categories. Such an invented ancestor would change handler matching and
reflection and is therefore observable language behavior, not an implementation
detail.

When a normative rule says only that an operation "signals an error" and does
not name a standard error prototype, Core v0.1 guarantees only the `Error`
category for portable handler matching and the fresh-instance rule above. An
implementation may attach implementation-private diagnostic metadata, but it
must not expose a different Protos delegation ancestry for that failure as
though the additional category were standardized.

Conversely, user code and libraries may create ordinary error prototypes and
arbitrary deeper delegation hierarchies beneath `Error`. Those program-defined
hierarchies use the normal object/delegation model and handler matching rules;
they do not extend the set of standard Core error categories.

A prototype name appearing only as pseudocode notation is not thereby a
standard-prelude binding. A name becomes a portable standard error prototype
only when a normative specification explicitly defines it as such and this
taxonomy (or a later revision of its owning contract) records its parent
relation.

This taxonomy rule does not introduce checked errors, declarations, hidden
classes, or a parallel type system. It exists solely to make the already
observable prototype-based handler matching deterministic across independent
implementations.

### Error precedence during `ensure` cleanup

If execution enters `cleanup` because a control transfer is leaving the
protected scope, normal completion of `cleanup` preserves that pending transfer.

If `cleanup` instead signals an `Error`, the cleanup Error becomes the active
error transfer and supersedes the transfer that caused cleanup to run. This
applies when the prior transfer was normal scope exit, non-local return, Error
unwind, or cancellation unwind.

Therefore, when an Error `original` is already unwinding through an `ensure`
scope and the cleanup signals `cleanupError`, outward handler search observes
`cleanupError`, not `original`.

Core v0.1 does not automatically wrap `cleanupError`, attach `original` as a
language-visible cause, construct a suppressed-error list, or otherwise preserve
both failures as a new composite Error. Libraries may build such reporting
conventions explicitly with ordinary objects and handlers.

This rule does not undo effects already performed before either Error was
signaled. It fixes only which control transfer continues after the cleanup
attempt.
