# Protos Errors v0.1

Language version: 0.1
Status: Draft
Last updated: 2026-09-04

This document is the primary normative owner of Core Error objects, signaling, handling, propagation, and error-control semantics.

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

If an error is signaled while the protected dynamic extent is active and
`matchPrototype` occurs in the signaled error object's delegation chain, that
handler is selected before any older matching handler. Core handlers are
unwinding: execution of the signaling continuation and the remaining protected
computation is abandoned. The selected handler frame is removed before
`handler` is invoked, and `handler` receives the signaled error as its single
argument. If `handler` then completes normally, its result is the result of the
`handle` call.

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
An unhandled error propagates until an appropriate handler is found or the outermost execution boundary is reached.

Core v0.1 retains no resumable-condition authority or continuation state for
error signaling. A future standard may add an explicit recovery/restart facility
only through its own normative control-state contract; such a facility must not
reinterpret existing Core `Error.signal()` operations as resumable.

### Core Error Taxonomy

Core v0.1 defines one mandatory root error prototype: `Error`. `Error` is an
ordinary standard-prelude object whose delegation parent is `Object`.

Every object signaled as a Core language/runtime error must have `Error` in its
delegation chain. Standard error prototypes named normatively by Core or a
normative domain model are ordinary objects in that chain and must delegate
directly to `Error` unless that same normative specification explicitly defines
another parent relation.

This rule makes the portable taxonomy deliberately shallow. A specification may
introduce a deeper standard hierarchy only by stating that hierarchy
normatively; an implementation must not invent extra Protos-visible intermediate
error categories. Such an invented ancestor would change handler matching and
reflection and is therefore observable language behavior, not an implementation
detail.

When a normative rule says only that an operation "signals an error" and does
not name a standard error prototype, Core v0.1 guarantees only the `Error`
category for portable handler matching. An implementation may attach
implementation-private diagnostic metadata, but it must not expose a different
Protos delegation ancestry for that failure as though the additional category
were standardized.

Conversely, user code and libraries may create ordinary error prototypes and
arbitrary deeper delegation hierarchies beneath `Error`. Those program-defined
hierarchies use the normal object/delegation model and handler matching rules;
they do not extend the set of standard Core error categories.

A prototype name appearing only as pseudocode notation is not thereby a
standard-prelude binding. A name becomes a portable standard error prototype
only when a normative specification explicitly defines it as such.

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
