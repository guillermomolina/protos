# Protos Execution and Control v0.1

Language version: 0.1
Status: Draft
Last updated: 2026-09-04

This document is the primary normative owner of execution contexts, lookup/control foundations, intrinsic execution references, evaluation order, iteration/loop control, and related execution semantics.

The material below is migrated without intended semantic change from `../PROTOS_LANGUAGE_SPEC.md`. Legacy section titles and numbering are retained so existing references remain understandable.

## 4. Execution Context

Every execution has a `context`.

`context` is an object. Parameters, temporary bindings, and local slots belong to this object.

```js
greet: (name) => {
    message: "Hello " + name
    print(message)
}
```

Conceptually:

```text
context
├── name
└── message
```

There is no separate semantic category called a local variable. Local variables are slots of an execution context.

Execution contexts are ordinary Protos objects. Their standard prototype is `Context`, provided by the standard prelude:

```text
activationContext
        ↓
Context
        ↓
Object
```

`Context` is not a reserved word, and it is distinct from the reserved intrinsic pseudo-identifier `context`, which denotes the current execution context. Behavior provided by `Context` is inherited through ordinary Protos delegation; there is no separate runtime object category for execution contexts and no special lookup mechanism associated with `Context`.

### Lexical Parent Relation

Every execution context that participates in lexical lookup has an immediate **lexical parent context**, or no lexical parent when it is the lexical root. This semantic relation is distinct from ordinary object delegation and is not the `Context -> Object` delegation chain.

For Core v0.1:

- a Closure invocation's fresh activation context has the Closure's captured lexical context as its immediate lexical parent;
- an object-construction context uses the genuine lexical context of the enclosing execution as its lexical parent while the object body executes, but the object under construction is not thereby captured as a lexical parent by methods created in that body;
- a module's `moduleContext` has the frozen standard prelude context as its immediate lexical parent while the module body executes;
- the frozen standard prelude context has no lexical parent.

Following lexical parents therefore visits execution-context objects, but it never follows any visited context object's ordinary delegation parent. In particular, `Context`, `Object`, or behavior inherited through them does not become an unqualified lexical binding merely because execution contexts delegate through `Context`.

Implementations may represent lexical-parent associations in activation metadata, environment records, links between context objects, or another form. That representation is not observable; the lexical traversal defined in §6 is.

### Object Construction Is Not a Lexical Capture Scope

An object body executes with the object being constructed as its current slot-creation context, but the object itself does **not** become a lexical environment captured by method closures declared in that body.

This distinction is fundamental. Object slots are receiver state, not lexical variables. A method inherited through delegation must therefore resolve bare state names against its dynamic receiver after genuine lexical contexts have been searched.

```js
animal: {
    name: "animal"

    speak: () => {
        print(name)
    }
}

dog: animal {
    name: "Rex"
}

dog.speak()
```

The bare `name` in `speak` resolves to `dog.name`, so the call prints `"Rex"`. The local `name` slot of `animal` is a distinct slot and remains reachable through ordinary delegation when the receiver does not provide a nearer slot.

A method may still capture genuine enclosing lexical contexts, such as module bindings or locals of an enclosing closure. Those lexical bindings have priority over receiver lookup.

Conceptually, bare-name lookup inside a method is therefore:

```text
current activation context (local slots only)
        ↓ lexical parent
genuine captured lexical contexts (local slots only)
        ↓ lexical parent
standard prelude when present in that lexical chain (local slots only)
        ↓ after lexical exhaustion
this
        ↓ ordinary delegation
parent of this
        ↓
...
```

The object in whose body the method closure was created is not inserted into the lexical portion of that chain merely because it owns the method slot.
## 5. `this`

`this` represents the current receiver.

During:

```js
dog.speak()
```

if `speak` is invoked as a method:

```js
this === dog
```

This remains true even when `speak` is found through delegation.

Given:

```text
rex → dog → animal
```

executing:

```js
rex.speak()
```

keeps:

```js
this === rex
```

`this` is an intrinsic pseudo-identifier supplied by the current execution state. It is not an ordinary bare identifier and does not execute the unqualified-name algorithm in §6. The same distinction applies to the intrinsic pseudo-identifiers `context` and `args`. `super` is governed separately by §8 and is not a bare-name lookup.
## 6. Unqualified Lookup

An ordinary bare identifier expression such as:

```js
name
```

performs unqualified contextual lookup. This section is the primary normative owner of that lookup algorithm.

The algorithm is exactly:

1. Start with the current execution context as the current lexical context.
2. Inspect **only the local slots** of that lexical context for `name`. Do not consult that context object's ordinary delegation parent.
3. If a local slot named `name` exists, return its exact current value and stop.
4. Otherwise move to the immediate lexical parent defined in §4 and repeat steps 2-4 until the lexical chain is exhausted. The frozen standard prelude participates when, and only when, it is on that lexical chain.
5. If the lexical chain is exhausted and the current execution has an ordinary receiver `this`, perform an ordinary member read for `name` starting at `this`. This receiver phase searches `this` and then its ordinary delegation parents under `OBJECT_MODEL.md`; when the selected value is a method Closure, the binding rules in `CALLABLES.md` preserve the original receiver.
6. If no lexical local slot exists and the receiver phase is absent or also finds no slot, signal a fresh standard `SlotNotFound` failure under the Error-object construction and identity rules owned by `ERRORS.md`. Failed lookup never implicitly produces `null`.

The order above is fixed. In particular:

- ordinary delegation of an execution context never participates in the lexical phase, so a slot available only through `context -> Context -> Object` does not satisfy a bare read;
- the entire lexical chain is searched before receiver fallback, so any lexical binding — including a prelude binding when the prelude is on that chain — shadows a same-named receiver slot;
- receiver fallback is ordinary member lookup and may therefore find a slot inherited through the receiver's delegation chain;
- no Process-wide, Actor-wide, module-cache-wide, host-global, or other implicit global namespace is searched;
- an explicit member read such as `context.name` or `object.name` is a different operation: it follows the ordinary member-lookup rules of `OBJECT_MODEL.md`, including ordinary delegation.

Closure capture does not snapshot these slot values. Closures capture their genuine lexical execution contexts by reference under `CALLABLES.md`, so later mutation of an existing captured slot, and later creation of a slot in a still-captured context where creation is otherwise valid, is observed by subsequent bare lookup through that same context.

At module top level, the current lexical context is the module's `moduleContext` and its lexical parent is the frozen standard prelude. Module execution has no additional implicit global receiver namespace. Consequently the module-top-level bare-name path is exactly: local module binding, then local prelude binding, then absence. Module-specific ownership, freezing, isolation, and import behavior remain owned by `MODULES.md`.
## 7. Unqualified Assignment and Creation

Bare read, bare assignment, and bare creation are distinct operations and do not share one generic lookup walk.

For bare assignment:

```js
x = value
```

the right-hand side is evaluated under the ordinary evaluation-order rules, then the destination is selected as follows:

1. Starting at the current execution context, inspect only local slots for `x`; if absent, continue through immediate lexical parents, again inspecting local slots only.
2. The first lexical context with a local slot named `x` is the selected destination. If that selected slot cannot be modified under the ordinary object-state rules, assignment fails there; it does not continue to an outer lexical context or to `this`.
3. If the complete lexical chain contains no local slot named `x` and the current execution has an ordinary receiver `this`, inspect **only `this`'s own local slots**. If `this` has a local `x`, that slot is the selected destination.
4. Do not follow ordinary delegation from any lexical context or from `this` while selecting an assignment destination. In particular, an inherited receiver slot is readable through the §6 receiver fallback but is not a destination for bare `=`.
5. If no destination exists, signal a fresh standard `SlotNotFound` failure under the Error-object construction and identity rules owned by `ERRORS.md`. Bare assignment never creates a slot.

This search is for the nearest existing local binding, not for the nearest writable binding. A local binding that exists but is frozen, closed against the requested mutation, or otherwise non-modifiable wins the search and then causes the ordinary assignment failure; lookup does not skip it in search of a farther binding.

Bare creation:

```js
x: value
```

performs no lookup. After evaluating `value`, it attempts ordinary slot creation named `x` **only on the current execution context**. Normal local creation/open/frozen/conflict rules apply. A successful bare creation may therefore shadow an outer lexical binding, a prelude binding, a receiver slot, or a receiver-delegated slot without modifying any of them.

Inside a function, bare creation is conceptually equivalent to creating the slot directly on the current `context` object:

```js
context.x: value
```

To explicitly create or assign receiver state, source uses an explicit member operation such as:

```js
this.x: value
this.x = value
```

Those explicit member operations are governed by `OBJECT_MODEL.md`; they are not bare-name lookup and do not alter the lexical-parent relation.
## 8. `super`

`super` is not another receiver and is not a first-class value. It is special lookup syntax.

It means:

> Continue lookup after the object where the currently executing method was found, while preserving the original receiver.

Only a super message send is valid in the core language, for example `super.speak()` or `super.move(x, y)`. The message name following `super.` is a contextual member name and may be a reserved-word spelling, so `super.true()`, `super.this()`, and `super.super()` are valid super message sends whose message names are respectively `true`, `this`, and `super`; this does not make `super` a first-class value. Expressions such as `x: super`, `foo(super)`, bare `super`, or method extraction such as `f: super.speak` are invalid.

Conceptually, `super.message(args...)` is syntactic sugar for a context-aware send operation using `context`: the receiver remains `context.receiver`, while lookup starts at `parent(context.methodHome)`. `super` therefore does not need to exist as a runtime object.

Given:

```text
rex → dog → animal → Object
```

if a method defined in `dog` executes:

```js
super.speak()
```

lookup begins at `animal`, while `this === rex` remains true.

Conceptually:

```text
receiver     = this
lookupOrigin = parent(methodHome)
```
## 8.1 Evaluation Order

The language evaluates strict subexpressions from left to right. The receiver or assignment target is evaluated before arguments or the right-hand side, and arguments are evaluated left to right. Parent expressions are evaluated before object bodies. Standard binary operators evaluate their left operand before their right operand.

```js
getObject().x = makeValue()
```

evaluates `getObject()` first, then `makeValue()`, then performs the assignment. Lazy operations such as `&&` and `||` are exceptions because their right-hand expression is evaluated only when required by their lazy semantics.
## 8.2 Sequence Evaluation

The semantic `Sequence(expressions)` produced from an `expression-sequence` evaluates its expressions strictly from left to right in the current execution context.

If the Sequence contains one or more expressions and completes normally, its result is the exact value produced by its final expression. If the Sequence contains zero expressions and completes normally, its result is the canonical `null` value.

The `null` result is only the result of **normal completion** of the empty Sequence. Sequence evaluation does not convert a non-local return, Error signaling/unwind, cooperative cancellation unwind, or any other control transfer into `null`; when such a transfer leaves the Sequence, that Sequence produces no normal result.

This rule applies to the semantic Sequence corresponding to the grammar's `expression-sequence`, including a source module/program body and a braced Closure body. It does not redefine `object-body-sequence` or the result of object construction; object-body construction semantics remain owned independently by `OBJECT_MODEL.md`.

Implementations need not allocate a runtime Sequence object and may erase, inline, constant-fold, or otherwise optimize an empty Sequence, provided the observable normal result remains canonical `null` and all surrounding control-flow, cleanup, task/Future, and construction semantics remain unchanged.

## 17. Iteration and Loops

No primitive `for` construct is required.

```js
users.each((user) => {
    print(user.name)
})

users.map((user) => {
    user.name
})

1.to(10).each((i) => {
    print(i)
})
```

A `while` operation requires a reevaluated condition and therefore semantically operates on a closure:

```js
(() => i < 10).while() {
    i = i + 1
}
```

A future `while (...) { ... }` form may be syntactic sugar.

## Resource Cleanup and `ensure`

This section owns deterministic control-flow cleanup/unwind behavior. Error signaling/handler selection during cleanup composes with `ERRORS.md`; `ensure` syntax/lowering is owned by `../PROTOS_GRAMMAR.md`.


Core v0.1 defines no deterministic object destructor.

Resource release is explicit protocol behavior, for example:

```js
file.close()
socket.close()
```

The runtime provides unwind-safe cleanup semantics through an `ensure`-style protocol. Conceptually:

```js
body.ensure(cleanup)
```

executes `cleanup` whenever execution leaves the protected scope, whether by:

- normal completion,
- non-local return with `^`,
- error signaling and unwind,
- cooperative cancellation unwind.

Cleanup is part of the unwind that triggered it, not fresh ordinary execution
subject to re-delivery of that same control transfer. In particular, once a
pending cancellation request has been honored and cancellation unwind has begun,
that already-honored request is not observed again at suspension boundaries
reached while running `ensure` cleanup for that unwind. Cleanup may therefore
perform ordinary asynchronous operations and suspend while releasing resources.

This shielding is only from the cancellation request already being delivered by
the current unwind. It is not a general cancellation-masking facility and does
not turn failures or independently observed Future outcomes into successful
cleanup. An implementation may represent this with masking, a cancellation phase,
or other machinery, but the distinction must be unobservable.

If `cleanup` completes normally, the original completion or control transfer
continues unchanged. For cancellation unwind, cancellation resumes after cleanup
and the task's Future reaches the cancelled state only after all applicable
cleanup has completed.

If `cleanup` signals an error, that new error becomes the active control transfer.
Any previously active return, error unwind, or cancellation unwind is abandoned
in favor of the newly signaled error. Thus a cleanup failure during cancellation
makes the task fail with that cleanup error rather than complete as cancelled.

Core v0.1 Error signaling is non-resumable as owned by `ERRORS.md`. Every Error
transfer that reaches this cleanup rule has abandoned the signaling continuation;
a handler cannot keep the protected computation active by returning, resuming,
retrying, or supplying a value to the signal point. A future recovery facility,
if standardized, must be a distinct control mechanism with its own cleanup
contract rather than an alternate interpretation of Core `Error.signal()`.

Higher-level resource protocols such as `use`, `withOpen`, or similar APIs may be implemented on top of this guarantee using ordinary messages and closures.

Garbage-collector finalization is not a resource-management guarantee and must not be relied upon for deterministic release of external resources.

### Error precedence during `ensure` cleanup

Cleanup-triggered Error precedence and handler-search consequences are owned by `ERRORS.md`. If cleanup signals an Error while another control transfer is pending, the applicable Error-domain rule determines which transfer continues; this section does not independently redefine that contract.
