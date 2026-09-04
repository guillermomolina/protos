# Protos Execution and Control v0.1

Language version: 0.1
Document revision: 327
Status: Draft
Last updated: 2026-09-04

This document is the primary normative owner of execution contexts, lookup/control foundations, intrinsic execution references, and related control semantics.

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
current activation context
        ↓
genuine captured lexical contexts
        ↓
this
        ↓
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

`this` is an intrinsic pseudo-identifier supplied by the execution context.
## 6. Unqualified Lookup

An expression such as:

```js
name
```

performs implicit contextual lookup.

Lookup conceptually proceeds through:

```text
current context
        ↓
captured lexical contexts
        ↓
this
        ↓
parent of this
        ↓
parent of parent
        ↓
...
```

Lookup stops at the first matching slot.

If no slot is found, a lookup error is signaled. A failed lookup never implicitly produces `null`.
## 7. Unqualified Assignment

An assignment:

```js
x = value
```

first searches writable lexical contexts.

If `x` is not found there, assignment may modify a slot belonging **locally to the receiver `this`**.

Assignment never traverses the delegation parents of `this`.

If no writable destination exists, the operation fails.

Creation:

```js
x: value
```

creates `x` in the current local context.

Inside a function, it is conceptually equivalent to:

```js
context.x: value
```

To explicitly create state on the receiver:

```js
this.x: value
```
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
## 9. Closures

A closure is written with parameters before `=>` and a body after it:

```js
() => {
    ...
}

(a, b) => {
    ...
}
```

Two purely syntactic conveniences extend these spellings without changing what a Closure is.

- **Expression bodies.** The body may be exactly one ordinary expression instead of a braced sequence: `(x) => x * 2` is exactly equivalent to `(x) => { x * 2 }`. An expression body is exactly one `expression`, not an `expression-sequence`: `x => print(x); foo()` is a Closure whose body is `print(x)` followed by the separate expression `foo()`, and multiple expressions still require a braced body. The body ends where the ordinary expression grammar ends it — a separating logical `NEWLINE` after a complete body expression or an inline `;` ends the Closure; no ASI-like or Closure-specific continuation rule is introduced (see the grammar's Closures section).
- **Single-parameter shorthand.** Parentheses may be omitted when the Closure has exactly one parameter that is neither a default nor a rest parameter: `x => x * 2` is exactly equivalent to `(x) => x * 2`. Parentheses remain required for zero parameters, two or more parameters, a default parameter, and a rest parameter: `() => value`, `(a, b) => a + b`, `(x = 10) => x`, `(...items) => items`, and `(first, ...rest) => rest`. Because the shorthand parameter is an ordinary `identifier`, reserved words remain invalid as parameter names.

All of these spellings — `(x) => { ... }`, `x => { ... }`, `(x) => expression`, and `x => expression` — create the same kind of Closure and obey precisely the same invocation semantics. There is no JavaScript-style split between a `function` and an arrow callable: Protos has one Closure semantics. Expression-bodied and braced forms behave identically with respect to lexical capture by reference, `this`, `context`, `args`, `super`, method binding, return homes, non-local return `^`, evaluation order, Future/async behavior, and error propagation. Creating a Closure never invokes it: `double: x => x * 2` stores the Closure object in slot `double`, `f = x => x + 1` assigns it to `f`, and `applyLater(x => x * 2)` passes it as an argument; only an explicit call such as `(x => x * 2)(10)` invokes it. Nested shorthand Closures associate to the right: `x => y => x + y` is `x => (y => (x + y))`.

The `{` immediately after `=>` always begins the Closure's braced body, so a Closure whose body is an object expression is written with parenthesized grouping, `x => ({ ... })`. Trailing-closure syntax is unchanged and remains parameterless, and no new keyword or new callable category is introduced.

Closures capture their lexical contexts **by reference**, not by value.

```js
makeCounter: () => {
    n: 0

    () => {
        n = n + 1
        n
    }
}
```

Therefore:

```js
counter: makeCounter()

counter()   // 1
counter()   // 2
counter()   // 3
```

works because the context containing `n` remains alive while a closure still references it.
