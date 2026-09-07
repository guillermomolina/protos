# Objects, Delegation, and Composition

> **Status:** Non-normative programming guide
>
> **Normative owners:** `spec/semantics/OBJECT_MODEL.md` and
> `spec/PROTOS_GRAMMAR.md`

The previous chapter introduced slots as the common storage idea behind lexical
bindings and object state. This chapter focuses on ordinary objects themselves:
how they are created, how they delegate, and how Protos separates vertical
behavior reuse from horizontal composition.

The most useful starting point is simple:

> In Protos, a prototype is not a special kind of value. It is an ordinary
> object being used as another object's delegation parent.

There are no classes and there is no separate runtime category of "prototype
objects".

## Objects are made of slots

An object contains local slots that associate names with values:

```protos
point: {
    x: 10
    y: 20
}
```

`point` is an ordinary object with local slots `x` and `y`.

A bare object expression delegates directly to `Object`, the unique root of the
standard delegation hierarchy:

```text
point
  ↓
Object
```

`Object` itself is the one object with no delegation parent.

Every other Protos object has exactly one immutable delegation parent.

## The `:` before an object is still slot creation

This spelling:

```protos
dog: animal {
    name: "Rex"
}
```

can look as if `:` were part of prototype syntax. It is not.

Conceptually, it is:

```text
dog : (animal { name: "Rex" })
```

The right-hand expression creates a new object whose parent is `animal`. The
outer `:` then creates the binding named `dog` in the current context.

Keeping those operations separate is useful when reading more complex code:
object construction is an expression, while slot creation stores the resulting
object somewhere.

## Delegation is lookup

Suppose:

```protos
animal: {
    alive: true
}

dog: animal {
    name: "Rex"
}
```

The resulting delegation chain is:

```text
dog
 ↓
animal
 ↓
Object
```

A member read starts at the receiver and may continue through that chain:

```protos
dog.name   // local to dog
dog.alive  // found through animal
```

The important word is **lookup**. Delegation says where behavior or state may be
found when it is absent locally.

It does not say that `dog` becomes an instance of a class named `animal`, because
there is no class system involved.

## The original receiver is preserved

Delegated lookup does not replace the receiver with the object where a slot was
found.

If behavior stored on `animal` is invoked through `dog`, the dynamic receiver is
still `dog`.

Conceptually:

```text
lookup may find behavior in animal
receiver remains dog
```

This is why a method-like Closure inherited from a prototype can operate on the
state of the descendant that received the message.

The details of Closure receiver binding belong in the next guide chapter; for
now, remember that delegation chooses **where lookup finds a slot**, not **what
object receives the message**.

## Reads may delegate; writes do not

The fundamental object-state rule from the first chapter applies directly to
member access:

```protos
dog.alive          // valid delegated read
dog.alive = false  // error: alive is not local to dog
```

A descendant cannot silently mutate an ancestor through an inherited slot.

To give `dog` its own `alive` state, create it explicitly:

```protos
dog.alive: false
dog.alive = true
```

Now the first expression creates a local override and the second modifies that
local slot.

This keeps a prototype usable as shared delegated behavior without making every
descendant write a potential mutation of shared ancestor state.

## A prototype is a role, not a category

Any Protos object may serve as another object's delegation parent.

That includes ordinary objects, standard objects, execution contexts, canonical
values, numbers, strings, and other values. There is no hidden `isPrototype`
flag or parentability type.

For example, a parenthesized value may be used as a parent expression:

```protos
answer: (42) {
    description: "the answer"
}
```

This creates a new ordinary object that delegates to the numeric value `42`.

But delegation does **not** turn the child into that semantic value:

```protos
answer === 42  // false
```

The child is still its own identity-bearing object.

This distinction is important for standard behavior. Finding a Number-family
operation through delegation does not magically make a non-Number receiver into
a semantic Number. Delegation provides lookup; it does not provide coercion or
value-family membership.

## One parent keeps lookup linear

Protos objects have exactly one delegation parent.

That gives ordinary member lookup a simple chain:

```text
receiver
   ↓
parent
   ↓
parent
   ↓
...
   ↓
Object
```

There is no multiple-inheritance method resolution order and no diamond-shaped
parent graph to resolve.

This does not mean an object can reuse behavior from only one source. Protos
handles the other major reuse problem separately through **composition**.

## Composition is horizontal reuse

Inside an object body, `...source` composes the source object's local slots into
the object being built:

```protos
flyable: {
    fly: () => {
        "flying"
    }
}

swimmable: {
    swim: () => {
        "swimming"
    }
}

duck: animal {
    ...flyable
    ...swimmable
}
```

This does not add extra parents.

The delegation relationship remains:

```text
duck
 ↓
animal
 ↓
...
```

After successful composition, the contributed bindings are **local slots of
`duck`**.

So Protos keeps two forms of reuse distinct:

```text
delegation   = vertical lookup through one parent chain
composition  = horizontal copying of local slot bindings
```

## Composition copies bindings, not object graphs

Suppose:

```protos
positionable: {
    x: 0
    y: 0
}

player: { ...positionable }
enemy: { ...positionable }
```

`player` and `enemy` each receive their own local slots named `x` and `y`.

The slot bindings are copied, but the values stored in them are not deep-cloned.
If a composed slot contains a mutable object, both resulting slots initially
refer to that same value object.

A good mental model is:

```text
copy slot names + references
not an implicit deep copy of reachable state
```

This makes composition structural and predictable rather than a hidden cloning
policy.

## Composition conflicts do not use source order as precedence

Consider two reusable objects that both define `move`:

```protos
walker: {
    move: () => {
        "walk"
    }
}

swimmer: {
    move: () => {
        "swim"
    }
}
```

Simply composing both does not mean "last one wins" or "first one wins".

For an unreserved name, a later composition that would contribute a slot already
present locally is a composition conflict.

Protos deliberately avoids turning source order into an implicit method
resolution policy.

## A direct local declaration resolves a composition name explicitly

An object body can declare the final local meaning itself:

```protos
duck: {
    ...walker
    ...swimmer

    move: () => {
        "waddle"
    }
}
```

A directly declared local name is structurally reserved for that declaration.
Composition sources do not contribute that reserved name, regardless of whether
the direct declaration appears before or after the composition items in source
order.

This gives the object an explicit answer to the conflict rather than relying on
an implicit ordering rule.

The declaration still executes at its normal left-to-right position. Reservation
prevents conflicting contributions; it does not create the local slot early.

## Composition is incremental and each item is atomic

Object-body items execute left to right.

A successful composition item contributes its effective slots when that item
completes, so later body items can observe them.

For example:

```protos
base: {
    x: 1
}

example: {
    ...base
    y: x
}
```

When `y: x` executes, the earlier composition has already made `x` local to
`example`.

At the same time, each individual composition item validates its whole effective
contribution before changing the receiving object's structure. If that item has
a conflict, it contributes none of its slots.

That prevents a failed composition item from leaving a partially installed set
of bindings.

## `without` and `alias` are ordinary object operations

Protos does not need special trait syntax for excluding or renaming composed
behavior.

A source can be transformed before composition using ordinary messages:

```protos
duck: {
    ...walker.without("move")
    ...swimmer.alias("move", "swimMove")

    move: () => {
        swimMove()
    }
}
```

`without(name)` and `alias(sourceName, aliasName)` inspect the receiver's **local
slots only** and return fresh ordinary objects representing the resulting local
slot view.

They do not mutate the source object, do not perform delegated lookup to find a
source slot, and do not deep-copy stored values.

The resulting view is just another ordinary object. Composition itself does not
know or care that `without` or `alias` produced it.

That follows a recurring Protos design preference: use ordinary object mechanisms
where they are sufficient instead of creating a second trait-specific universe.

## Object structure can be open, closed, or frozen

Ordinary objects begin open and mutable.

```text
open
  local slots may be created, modified, or removed

closed
  existing local slots may still be modified
  slots cannot be added or removed

frozen
  local structure and existing slot values cannot be modified
```

The standard structural operations are ordinary messages:

```protos
object.close()
object.freeze()
object.removeSlot("name")
```

`close()` and `freeze()` are shallow. They do not recursively close or freeze the
objects reachable through stored slots.

The delegation parent is immutable independently of these state transitions.

## Removing a local override can reveal delegated state

`removeSlot(name)` acts only on local structure.

Consider:

```protos
animal: {
    alive: true
}

dog: animal {
    alive: false
}
```

After:

```protos
dog.removeSlot("alive")
```

`dog` no longer has a local `alive` slot, so a later read can once again find the
one on `animal`:

```protos
dog.alive  // true
```

Nothing was removed from `animal`.

## Reflection separates local structure from delegated lookup

Core provides a deliberately small reflective surface:

```protos
object.hasSlot("name")
object.slotNames()
object.slotValue("name")
object.parent()
```

The slot-oriented operations inspect **local slots only**.

That means:

```text
ordinary member read      may delegate
hasSlot / slotValue       inspect local structure
```

This distinction makes it possible to ask two different questions explicitly:

- "What would ordinary lookup find?"
- "What does this object itself own?"

`parent()` returns the immutable delegation parent. Since `Object` is the unique
root and has no parent, `Object.parent()` signals an Error rather than returning
`null` as a fabricated sentinel.

## A useful mental model

When looking at an ordinary Protos object, keep three ideas separate:

```text
local slots
    what this object owns structurally

delegation parent
    where ordinary lookup continues when a local slot is absent

composition
    how bindings from other objects can become local during construction
```

These mechanisms compose, but they are not interchangeable.

In particular:

- delegation does not copy slots;
- composition does not add parents;
- delegation does not confer semantic type/family membership;
- writes do not climb the delegation chain;
- reflection can inspect local structure without performing ordinary lookup.

## Practical rules to remember

1. Think of a prototype as an ordinary object playing the parent role.
2. Use delegation for one linear fallback lookup chain.
3. Use composition when several sources should contribute local bindings.
4. Do not expect composition order to choose a conflict winner.
5. Declare the intended local slot explicitly when the receiving object owns the
   final meaning of a composed name.
6. Remember that composition copies bindings shallowly, not reachable object
   graphs.
7. Use `hasSlot`, `slotValue`, and `slotNames` when you mean local structure;
   use ordinary member access when you mean delegated lookup.
8. Treat `close()` and `freeze()` as shallow structural state transitions.

## Run the executable lessons

The corresponding executable tutorial material is:

- [`../../protos/tutorials/02-objects/01-object-literals.protos`](../../protos/tutorials/02-objects/01-object-literals.protos)
- [`../../protos/tutorials/02-objects/02-delegation.protos`](../../protos/tutorials/02-objects/02-delegation.protos)

The tutorial sources intentionally stay small. This chapter supplies the mental
model around them; the tutorials remain executable examples of the basic object
surface.

## Normative references

For exact behavior, consult:

- [`../../spec/semantics/OBJECT_MODEL.md`](../../spec/semantics/OBJECT_MODEL.md)
  for object topology, slots, delegation, composition, structural state,
  reflection, `without`, and `alias`;
- [`../../spec/PROTOS_GRAMMAR.md`](../../spec/PROTOS_GRAMMAR.md) for object
  expressions, parent expressions, composition-item syntax, separators, and the
  distinction between the outer slot-creation `:` and the object expression on
  its right-hand side;
- [`01-bindings-contexts-and-state.md`](01-bindings-contexts-and-state.md) for the
  related distinction between lexical bindings and receiver state.

Those documents define the language. This chapter only explains how to reason
about the object model while writing Protos programs.
