# Bindings, Contexts, and Object State

> **Status:** Non-normative programming guide
>
> **Normative owners:** `spec/semantics/EXECUTION_AND_CONTROL.md`,
> `spec/semantics/OBJECT_MODEL.md`, `spec/semantics/CALLABLES.md`, and
> `spec/semantics/MODULES.md`

One of the first surprising things a programmer may notice in Protos is the
absence of `var`, `let`, `const`, or a separate local-variable declaration
construct.

The important point is not that Protos lacks local state. It is that Protos does
not create a second semantic category just for local variables.

## The short answer

What programmers commonly call a local variable is a **slot of an execution
context**.

```protos
greet: (name) => {
    message: "Hello " + name
    print(message)
}
```

Conceptually, invoking `greet` gives the activation an execution context with
local slots such as:

```text
activation context
├── name
└── message
```

`name` is a parameter binding. `message` is created while the body executes.
Both are represented by the same language-level idea: a slot on an execution
context object.

Execution contexts are themselves ordinary Protos objects. The language does
not need a separate hidden "local variable" universe.

## Translating familiar concepts

| Familiar concept | Protos model |
| --- | --- |
| local variable | local slot of an execution context |
| parameter | slot of a fresh activation context |
| captured local | slot in a captured lexical execution context |
| lexical scope chain | lexical-parent relation between execution contexts |
| global variable | no separate global-variable category |
| module-level binding | local slot of the module context |
| field / property / instance state | slot of an ordinary object |
| inheritance lookup | delegation |
| local declaration | bare slot creation with `:` |
| assignment | modification with `=` |

These translations are a learning aid, not new terminology that replaces the
specification.

## Two relations, not one

The shared use of slots does **not** mean lexical scope and object delegation are
the same relation.

An execution context has ordinary object delegation, like other objects, but
lexical lookup follows a distinct lexical-parent relation between execution
contexts.

Conceptually:

```text
lexical lookup

current activation context
        ↓ lexical parent
captured enclosing context
        ↓ lexical parent
...
```

Object-state lookup instead uses a receiver and its delegation chain:

```text
member lookup

this
 ↓ delegation parent
prototype
 ↓ delegation parent
...
Object
```

Ordinary delegation of `context` itself is not secretly part of lexical lookup.

This distinction matters throughout the language.

## `:` means create here

For a bare creation:

```protos
x: 10
```

Protos does not search for an existing `x`.

After evaluating the value, it attempts to create a local slot named `x` on the
**current execution context**.

Inside a Closure body, the useful mental model is:

```protos
x: 10
```

conceptually corresponding to:

```protos
context.x: 10
```

Creation can therefore intentionally shadow an outer lexical binding, a prelude
binding, or receiver state without modifying any of them.

Trying to create the same local slot twice is an error rather than silently
turning creation into assignment.

## `=` means modify something that already exists

For a bare assignment:

```protos
x = 20
```

Protos does not create `x`.

The assignment search is conceptually:

```text
current execution context: local x?
        ↓ no
lexical parent: local x?
        ↓ no
...
        ↓ lexical chain exhausted
this: local x?
```

The nearest existing lexical binding wins. If no lexical binding exists, an
eligible local slot of `this` may be selected when the current execution has an
ordinary receiver.

The search does **not** follow ordinary delegation to find a write destination.
An inherited slot can be readable without being writable through a descendant.

If no destination exists, assignment fails. `=` never silently becomes `:`.

## A bare read has a different search

A bare read:

```protos
x
```

first searches local slots through the lexical-context chain.

Only after that lexical chain is exhausted, when the current execution has an
ordinary receiver, lookup may fall back to ordinary receiver lookup starting at
`this` and continuing through delegation.

Conceptually:

```text
current execution context
        ↓ lexical parent
captured lexical contexts
        ↓
standard prelude when present
        ↓ lexical chain exhausted
this
        ↓ delegation
receiver parents
```

At module top level there is no additional implicit global receiver namespace:
the module context and its prelude parent provide the top-level lexical path.

If lookup fails, Protos signals a fresh `SlotNotFound` Error. Missing lookup does
not produce `null`.

Creation, modification, and reading therefore deliberately use related but
different algorithms:

```protos
x: value   // create here
x = value  // modify an existing eligible binding
x          // read through lexical lookup, then receiver lookup when applicable
```

## Why writes do not delegate

Consider:

```protos
animal: {
    alive: true
}

dog: animal {
    name: "Rex"
}
```

Reading inherited state is ordinary:

```protos
dog.alive
```

But this is invalid:

```protos
dog.alive = false
```

`alive` belongs locally to `animal`, not `dog`. A write through `dog` does not
silently mutate the ancestor prototype.

To give `dog` its own state, say so explicitly:

```protos
dog.alive: false
dog.alive = true
```

The first expression creates a local override. The second modifies that local
slot.

This is one reason Protos distinguishes creation from modification instead of
using one declaration/assignment form with context-dependent meaning.

## Receiver state is not lexical state

An object body is not automatically inserted into the lexical environment
captured by methods created inside it.

Consider:

```protos
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

The `name` inside `speak` is not captured as a lexical variable merely because
the Closure was created in the body of `animal`.

When genuine lexical contexts do not provide `name`, the bare read falls back to
the dynamic receiver. The call above therefore resolves `name` against `dog`
and prints `"Rex"`.

That separation is essential:

- lexical bindings belong to execution contexts;
- receiver state belongs to objects;
- a method can still use bare-name syntax because lookup composes those two
  mechanisms in a defined order.

To refer explicitly to receiver state, use a member operation such as:

```protos
this.name
this.name = "Max"
```

To create new receiver state explicitly:

```protos
this.nickname: "Rex"
```

## Closures capture contexts, not snapshots

Closures capture genuine lexical execution contexts **by reference**.

```protos
makeCounter: () => {
    n: 0

    () => {
        n = n + 1
        n
    }
}
```

The returned Closure does not receive a copied numeric snapshot of `n`. It
retains access to the lexical context that owns the `n` slot.

Repeated calls therefore observe and modify the same captured binding while
that context remains alive.

This is another consequence of the same model rather than a special
Closure-only variable system.

## Parameters fit the same model

Parameters do not require their own storage category either.

```protos
add: (a, b) => {
    result: a + b
    result
}
```

A fresh activation context contains the parameter slots `a` and `b`. `result`
is then created as another local slot in that activation context.

The syntactic roles differ, but lookup operates over context slots rather than
over separate parameter and local-variable namespaces.

## Module bindings fit the same model

A module executes in its own `moduleContext`.

Top-level creation:

```protos
answer: 42
```

creates a local slot of that module context. The module context has the frozen
standard prelude as its lexical parent.

There is no separate Process-wide or language-wide mutable "global variable"
namespace hiding behind top-level syntax.

Cross-module access is explicit through module instances and ordinary member
access.

## `context` makes the model visible

`context` is the intrinsic pseudo-identifier for the current execution-context
object.

It can be useful when code deliberately wants to talk about that context as an
object. This does not mean every ordinary object operation participates in
lexical lookup.

The standard `Context` object is the ordinary delegation prototype used by
execution-context objects. It is distinct from the intrinsic `context`
pseudo-identifier.

Keep the two relations separate in your mental model:

```text
lexical parent relation     determines bare lexical lookup
ordinary delegation         determines ordinary member lookup
```

## Practical rules to remember

When writing ordinary Protos code:

1. Use `name: value` when you mean **create a binding here**.
2. Use `name = value` when you mean **modify an existing binding**.
3. Use `this.name` when you specifically mean **receiver state**.
4. Expect Closures to observe later changes to captured lexical bindings because
   contexts are captured by reference.
5. Do not expect an object containing a method to become that method's lexical
   scope.
6. Do not expect a write through a descendant to mutate inherited prototype
   state.
7. Treat missing lookup as an Error, not as `null`.

These rules all follow from the context/slot/delegation model rather than from
independent special cases.

## Run the executable lessons

The corresponding executable tutorial material starts in:

- [`../../protos/tutorials/01-values-and-slots/01-values.protos`](../../protos/tutorials/01-values-and-slots/01-values.protos)
- [`../../protos/tutorials/01-values-and-slots/02-create-slots.protos`](../../protos/tutorials/01-values-and-slots/02-create-slots.protos)
- [`../../protos/tutorials/01-values-and-slots/03-assignment.protos`](../../protos/tutorials/01-values-and-slots/03-assignment.protos)

Continue with [`../../protos/tutorials/README.md`](../../protos/tutorials/README.md)
for the progressive executable sequence.

## Normative references

For exact behavior, consult:

- [`../../spec/semantics/EXECUTION_AND_CONTROL.md`](../../spec/semantics/EXECUTION_AND_CONTROL.md)
  for execution contexts, lexical parents, bare lookup, creation, assignment, and
  receiver fallback;
- [`../../spec/semantics/OBJECT_MODEL.md`](../../spec/semantics/OBJECT_MODEL.md)
  for slots, delegation, member reads/writes, and object state;
- [`../../spec/semantics/CALLABLES.md`](../../spec/semantics/CALLABLES.md) for
  Closure capture, methods, receiver binding, and invocation;
- [`../../spec/semantics/MODULES.md`](../../spec/semantics/MODULES.md) for module
  contexts, top-level bindings, imports, and module isolation.

Those documents define the language. This chapter only explains how their rules
fit together for programmers.
