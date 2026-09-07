# Closures, Methods, and Receivers

> **Status:** Non-normative programming guide
>
> **Normative owners:** `spec/semantics/CALLABLES.md`,
> `spec/semantics/EXECUTION_AND_CONTROL.md`, and `spec/PROTOS_GRAMMAR.md`

The previous chapters separated lexical bindings from receiver state and then
introduced delegation as ordinary object lookup. Closures are where those two
parts of the language meet.

The central idea is:

> Protos has one executable value kind: **Closure**. A "method" is not a second
> kind of value; it is the role a Closure takes when lookup and invocation give
> it a receiver.

This keeps functions, callbacks, methods, captured code, and later asynchronous
operations inside one callable model.

## One executable value kind

A Closure may be written with a braced body:

```protos
add: (a, b) => {
    a + b
}
```

or, for one expression, with an expression body:

```protos
double: x => x * 2
```

These spellings create the same semantic kind of value.

The language does not create separate value categories such as:

```text
Function
Method
Lambda
Block
Callback
```

Those words can still be useful descriptions of how a Closure is being used,
but they do not denote different Core executable value kinds.

## Creating a Closure does not invoke it

This:

```protos
double: x => x * 2
```

creates a Closure and stores it in `double`.

Invocation is a separate operation:

```protos
double(21)
```

The distinction matters because Closures are ordinary values. They can be
stored, passed, returned, captured, compared by identity where applicable, or
placed in object slots without being executed merely because they are read.

## Closures capture lexical contexts by reference

The binding model from chapter 1 applies directly to Closures:

```protos
makeCounter: () => {
    value: 0

    () => {
        value = value + 1
        value
    }
}
```

The returned Closure does not receive a snapshot of `value`.

It retains the lexical execution context that owns the `value` slot. Repeated
invocations therefore observe and update that same captured binding.

Conceptually:

```text
makeCounter activation context
└── value
      ↑
      captured by returned Closure
```

Calling the returned Closure creates a fresh activation context for that call,
whose lexical parent is the captured context.

So `context` inside the new call denotes the **current** activation context. It
is not another spelling for the captured parent context.

## A method is a Closure reached through a receiver

Consider:

```protos
animal: {
    speak: () => {
        name
    }
}

dog: animal {
    name: "Rex"
}
```

The slot `animal.speak` stores a Closure.

When invoked as:

```protos
dog.speak()
```

that Closure acts as a method because it was selected by receiver-aware lookup
and immediately invoked with `dog` as the original receiver.

There is still no separate `Method` object kind.

A useful mental model is:

```text
stored value       = Closure
lookup role        = selected through receiver
invocation role    = method
dynamic receiver   = dog
```

## `this` is the original dynamic receiver

Delegation may decide **where** a method-like Closure is found, but it does not
replace the receiver.

With:

```text
dog
 ↓
animal
 ↓
Object
```

a call:

```protos
dog.speak()
```

may find `speak` on `animal`, while inside the invoked Closure:

```protos
this === dog
```

remains true.

This is the same receiver-preservation rule introduced in the object-model
chapter.

It lets shared behavior live on a prototype while naturally operating on the
state of the descendant that received the message.

## Bare state lookup and `this` are related but not identical

Inside a method:

```protos
animal: {
    speak: () => {
        name
    }
}
```

the bare `name` first follows the lexical-context lookup rules.

Only after the genuine lexical chain is exhausted can lookup fall back to the
dynamic receiver `this` and its delegation chain.

That is why:

```protos
dog.speak()
```

can find `dog.name` even though the Closure itself is stored on `animal`.

The object containing the Closure does not become its lexical scope.

Use an explicit member operation when you specifically mean receiver state:

```protos
this.name
this.name = "Max"
```

The two forms can arrive at the same receiver slot in some situations, but they
express different lookup operations.

## Nested Closures retain the receiver they were created with

A Closure created while a method is executing preserves the receiver information
needed by its callable semantics.

For example:

```protos
animal: {
    speaker: () => {
        () => {
            name
        }
    }
}

dog: animal {
    name: "Rex"
}

speaker: dog.speaker()
speaker()
```

The nested Closure retains the method execution's receiver, so receiver fallback
inside it still sees `dog`.

This composes with ordinary lexical capture rather than creating a second
"method closure" category.

## Reading a method-like slot does not invoke it

This:

```protos
f: dog.speak
```

is a member read, not a call.

Because the selected slot contains a Closure, the read produces a receiver-bound
Closure that remembers:

```text
receiver   = dog
methodHome = object where lookup found the selected slot
```

A later plain invocation:

```protos
f()
```

therefore retains the receiver semantics of the extraction.

Unlike JavaScript-style detached method references, extracting the method does
not accidentally lose `this`.

## Each method extraction is a fresh Closure

Receiver binding is observable through Closure identity.

Two independent reads:

```protos
a: dog.speak
b: dog.speak

a === b  // false
```

produce two distinct extracted Closure objects, even though both came from the
same receiver, selected the same stored Closure, and found it at the same lookup
home.

By contrast, ordinary aliasing of one already-extracted Closure preserves that
exact object:

```protos
a: dog.speak
b: a

a === b  // true
```

So remember:

```text
repeat member read      -> new receiver-bound Closure
ordinary alias/copy     -> same Closure object
```

This freshness does not introduce a `Method` type. The result of extraction is
still a Closure.

## Immediate invocation and extraction are different observations

These two forms preserve compatible receiver semantics:

```protos
dog.speak()
```

and:

```protos
f: dog.speak
f()
```

but they do not expose the same intermediate observations.

The first form performs immediate message invocation. Since no extracted value
is exposed to the program, an implementation does not have to manufacture an
observable bound Closure object merely to make the call.

The second form performs an actual member read first, so the fresh extracted
Closure identity is observable.

This is another example of Protos keeping semantically distinct operations
distinct even when an implementation may optimize them similarly.

## Re-reading a stored Closure performs a new extraction

A subtle consequence follows from ordinary member-read rules.

Suppose:

```protos
bound: dog.speak
holder: {
    saved: bound
}
```

The local binding `bound` denotes the exact Closure extracted from `dog.speak`.

But:

```protos
again: holder.saved
```

is a **new member read of a Closure-valued slot**.

It therefore produces another fresh extracted Closure, now bound according to
that lookup through `holder`.

Storing a Closure does not mutate it. Re-reading a Closure-valued member performs
the ordinary extraction operation again.

If you want to preserve the exact already-extracted Closure identity, ordinary
lexical aliasing does that:

```protos
same: bound
```

## `this` and `context` answer different questions

Inside an invocation:

```text
this
    which object is receiving method-style behavior?

context
    which execution-context object owns this activation's local bindings?
```

They are both intrinsic references, but they are not interchangeable.

A method invocation can therefore have:

```text
this     -> dog
context  -> fresh activation context
```

with the activation context lexically linked to captured contexts while `dog`
participates in receiver lookup through its ordinary delegation chain.

Keeping those two dimensions separate explains much of Protos's callable model.

## `super` is lookup syntax, not a value

`super` does not denote an object.

The valid Core form is a super message send:

```protos
super.speak()
```

It means:

> Continue lookup after the object where the currently executing method was
> found, while preserving the original receiver.

If the delegation path is:

```text
rex
 ↓
dog
 ↓
animal
 ↓
Object
```

and a method found on `dog` executes:

```protos
super.speak()
```

then lookup starts at `animal`, but:

```protos
this === rex
```

remains true.

Conceptually:

```text
receiver     = this
lookup start = parent(methodHome)
```

`methodHome` is invocation metadata describing where the currently executing
method was found. It is not a second receiver.

## `super` cannot be extracted or passed around

Because `super` is lookup syntax rather than a first-class value, forms such as
these are invalid:

```protos
x: super
foo(super)
f: super.speak
```

A super operation must perform the message send:

```protos
super.speak()
```

If a syntactically valid super send executes without the method metadata required
to establish a lookup origin, it signals `InvalidSuper`.

That failure is different from having a valid lookup origin but failing to find
the requested slot, which signals the ordinary `SlotNotFound` failure.

## Nested Closures can retain `super` context

A Closure created while a method is executing retains the receiver and
`methodHome` information needed for a later super send while that callable state
is valid.

For example:

```protos
base: {
    read: () => {
        value
    }
}

middle: base {
    read: () => {
        nested: () => {
            super.read()
        }

        nested()
    }
}

leaf: middle {
    value: 9
}

leaf.read()
```

The nested Closure can continue lookup after `middle`, while the original
receiver remains `leaf`.

This works without introducing a special nested-method value kind.

## Normal return is the final expression

A braced Closure normally returns the value of its final expression:

```protos
square: x => {
    x * x
}
```

An expression-bodied Closure has exactly the corresponding one-expression
behavior:

```protos
square: x => x * x
```

A braced Closure with no body expressions:

```protos
empty: () => {}
```

completes normally with canonical `null`, following the ordinary empty-Sequence
rule.

## `^` is a non-local return

Early return uses:

```protos
^value
```

A nested Closure created inside an active function or method invocation captures
that invocation's **return home**.

For example:

```protos
find: (items) => {
    items.each((item) => {
        item.valid.ifTrue() {
            ^item
        }
    })

    null
}
```

The `^item` returns from the active invocation of `find`, not merely from the
innermost Closure.

That is different from languages where every lambda invocation automatically
creates a new local return target.

## Escaped non-local return is an error

A captured return home has a lifetime.

Consider:

```protos
make: () => {
    () => {
        ^42
    }
}

f: make()
f()
```

When `f()` eventually executes, the invocation of `make` that owned the return
home has already completed.

Protos does not reinterpret `^42` as a local return from `f`. It signals
`InvalidReturn`.

This keeps the meaning of `^` stable instead of changing it depending on whether
a Closure escaped.

## Practical rules to remember

1. Treat every executable language value as a Closure; "method" describes an
   invocation role.
2. Creating or reading a Closure does not invoke it.
3. Expect Closures to capture lexical contexts by reference.
4. In method-style invocation, `this` is the original receiver even when lookup
   found the Closure on an ancestor.
5. Keep `this` and `context` separate: receiver state and activation-local state
   are different dimensions.
6. Extracting a Closure-valued member preserves receiver semantics and creates a
   fresh Closure identity.
7. Ordinary aliasing of an already-extracted Closure preserves that exact
   identity.
8. Treat `super` as a message-send lookup form, never as a value.
9. Remember that nested Closures can retain receiver, `methodHome`, and return
   home information from the invocation in which they were created.
10. Use `^` only when you intend a non-local return to the captured home.

## Run the executable lessons

The corresponding introductory executable tutorial material is:

- [`../../protos/tutorials/03-closures/01-closure.protos`](../../protos/tutorials/03-closures/01-closure.protos)
- [`../../protos/tutorials/03-closures/02-captured-state.protos`](../../protos/tutorials/03-closures/02-captured-state.protos)

The current conformance corpus also contains focused executable examples for
receiver-preserving `super` behavior:

- [`../../protos/tests/conformance/regression/super-send-preserves-dynamic-receiver.protos`](../../protos/tests/conformance/regression/super-send-preserves-dynamic-receiver.protos)
- [`../../protos/tests/conformance/regression/nested-closure-super-retains-method-home.protos`](../../protos/tests/conformance/regression/nested-closure-super-retains-method-home.protos)

The tutorials remain intentionally small; this guide supplies the broader mental
model around them.

## Normative references

For exact behavior, consult:

- [`../../spec/semantics/CALLABLES.md`](../../spec/semantics/CALLABLES.md) for
  Closure capture, method invocation, extracted Closures, receiver binding,
  `methodHome`, return homes, and non-local return;
- [`../../spec/semantics/EXECUTION_AND_CONTROL.md`](../../spec/semantics/EXECUTION_AND_CONTROL.md)
  for `this`, `context`, unqualified receiver fallback, `super`, evaluation
  order, and Sequence completion;
- [`../../spec/PROTOS_GRAMMAR.md`](../../spec/PROTOS_GRAMMAR.md) for Closure
  syntax, intrinsic references, non-local-return syntax, and the exact
  `super.message(...)` grammar;
- [`01-bindings-contexts-and-state.md`](01-bindings-contexts-and-state.md) for
  lexical contexts and receiver-state lookup;
- [`02-objects-delegation-and-composition.md`](02-objects-delegation-and-composition.md)
  for delegation and receiver-preserving lookup.

Those documents define the language. This chapter explains how their callable
rules fit together while writing Protos programs.
