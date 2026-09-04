# Protos Object Model v0.1

Language version: 0.1
Status: Draft
Last updated: 2026-09-04

This document is the primary normative owner of object identity as objects, delegation, slots, composition, and object open/closed/frozen state.

The material below is migrated without intended semantic change from `../PROTOS_LANGUAGE_SPEC.md`. Legacy section titles and numbering are retained so existing references remain understandable.

## 2. Objects

An object contains slots. A slot associates a name with an object.

`Object` is the unique root of the standard delegation hierarchy and has **no delegation parent**.

Every other object has **exactly one delegation parent**. Therefore every delegation chain eventually terminates at `Object`. There are no disconnected root objects and no sentinel object standing for "no parent".

```js
animal: {
    alive: true
}

dog: animal {
    name: "Rex"
}
```

Here, `dog` is an object whose delegation parent is `animal`.

A bare object body:

```js
{
    ...
}
```

creates an object using `Object`, the standard root prototype, as its parent.

A parent expression followed by an object body creates an object with that parent:

```js
animal {
    name: "Rex"
}
```

Therefore:

```js
dog: animal {
    name: "Rex"
}
```

means conceptually:

```text
create slot dog
value = new object
parent = animal
slots:
    name → "Rex"
```

The delegation parent is fixed at object creation and cannot subsequently be changed. `Object` is the sole exception to the requirement that an object have exactly one parent: it has none.

### Every Object May Serve as a Delegation Parent

Every Protos object may serve as the delegation parent of another object. There is no distinct "prototype object" category and no parentability capability, flag, type, predicate, or hidden classification. "Prototype" describes a role that an object plays when another object delegates to it; it is not a separate kind of object.

The rule applies without exception to ordinary objects, built-in objects, immutable value objects, singleton values, execution-context objects, and every other Protos object. Consequently values such as `this`, `context`, `args`, `true`, `false`, `null`, Number values such as `42`, and String values such as `"hello"` may serve as delegation parents, as may the standard built-in prototype objects such as `Object`, `Number`, `Integer`, `Float`, `String`, and `Boolean`.

Subject to the `parent-expression` grammar (see PROTOS_GRAMMAR.md), all of the following are valid parent expressions:

```js
this {
    ...
}

context {
    ...
}

args {
    ...
}

(true) {
    ...
}

(false) {
    ...
}

(null) {
    ...
}

(42) {
    ...
}

("hello") {
    ...
}

(getParent()) {
    ...
}
```

`this`, `context`, and `args` are valid directly as intrinsic references. `true`, `false`, `null`, numbers, and strings are literals and therefore require parentheses: `(true)`, `(42)`, and `("hello")` are valid parent expressions, while the direct forms `true`, `42`, and `"hello"` are not `parent-expression` forms. A parenthesized expression may compute a parent dynamically.

Using an object as a delegation parent does not make the newly created object identical to that parent, and does not automatically give the child the parent's value semantics. For example:

```js
answer: (42) {
    description: "the answer"
}
```

creates a new ordinary identity-bearing object whose immediate delegation parent is the Number value `42`:

```text
answer
    ↓
42
    ↓
... existing delegation chain of 42 ...
    ↓
Object
```

`answer` and `42` are distinct objects, so:

```js
answer === 42
```

is false. The fact that `42` is an immutable Number value object with value identity does not transfer Number value identity to `answer`. Likewise, `x: ("hello") { ... }` creates a new object delegating to the String value `"hello"`; `x` does not thereby become the String value `"hello"`. Delegation and value-category membership are distinct concepts.

Message lookup through such a parent follows the ordinary delegation rules. If `answer → 42 → ...` and a message sent to `answer` is found through `42` or its ancestors, the original receiver remains `answer`, exactly as with every other delegated message send. Inherited behavior therefore executes with:

```js
this === answer
```

not:

```js
this === 42
```

Delegation guarantees lookup, not semantic membership in the parent's built-in value family. This revision introduces no coercion and no value inheritance: `answer + 1` is not specified to behave as numeric `43` merely because `answer` delegates to `42`. Whether inherited behavior can operate on a particular receiver follows the ordinary contract and behavior of the invoked message.

`Object` is special only in the already-defined sense that it is the unique root and therefore has no parent itself. Being the root does not prevent `Object` from serving as the parent of another object; bare `{ ... }` already creates an object whose parent is `Object`.

### Receiver domains of standard semantic-family behavior

Ordinary delegation determines where a message is found and preserves the
original receiver as `this`. It does not change the receiver's semantic value
family.

Unless a standard behavior explicitly defines itself as generic or declares a
wider receiver domain, a behavior whose normative semantics are defined in
terms of the receiver being a member of a semantic value family is applicable
only when the original receiver is actually a semantic member of that family.
For example, standard Number-family arithmetic, comparison, conversion, and
numeric `hash` behavior require a semantic Number receiver; merely delegating to
a Number value or Number-family prototype does not satisfy that receiver
contract.

If lookup finds such a standard family-specific behavior for an incompatible
receiver, invocation signals an `Error` for the invalid receiver before any
family-specific computation or family-specific state effect occurs. Argument
evaluation and effects that occurred before invocation are not rolled back.

Failure of the located behavior does not resume lookup at a more distant slot
with the same name. In particular, the runtime must not skip an incompatible
Number-family method and silently fall through to an `Object` method merely
because the original receiver is not a Number. Lookup remains ordinary lookup;
receiver-domain validation is part of the invoked behavior's contract.

This rule does not impose family restrictions on user-defined behavior merely
because that behavior is stored on, copied from, or inherited through an object
associated with a built-in family. A program may shadow or override a standard
family-specific message with ordinary behavior that intentionally accepts a
different receiver domain. Likewise, standard behavior explicitly specified as
generic remains governed by its own receiver contract.

Consequently:

```js
answer: (42) {
    description: "the answer"
}

answer === 42     // false
answer.hash()     // ERROR if lookup selects the standard Number-family hash
answer + 1        // ERROR if lookup selects standard Number-family arithmetic
```

The child may define its own `hash`, `==`, arithmetic, or other behavior when it
wants semantics different from the inherited family-specific contract. No
implicit coercion, value inheritance, fallback dispatch, or hidden family
membership is introduced.
## 3. Slot Creation and Modification

`:` creates a slot.

```js
x: 10
```

`=` modifies an existing slot.

```js
x = 20
```

Attempting to create a slot that already exists locally is an error.

Attempting to modify a slot when no valid writable slot exists is an error.

The fundamental rule is:

> **Reads delegate. Writes do not delegate.**

Example:

```js
animal: {
    alive: true
}

dog: animal {
    name: "Rex"
}
```

This is valid:

```js
dog.alive
```

because reading may find `alive` through delegation.

This is not valid:

```js
dog.alive = false
```

because `alive` does not belong locally to `dog`.

To explicitly create a local override:

```js
dog.alive: false
```

After that:

```js
dog.alive = true
```

is valid.

An ancestor prototype is never accidentally mutated through one of its descendants.

`:` is specifically the slot-creation operator and applies only to slot targets — a bare identifier or a member access. It cannot be applied to an indexed target:

```js
object[index]: value     // syntax error
```

Indexed mutation is expressed only through the indexing protocol (see Indexed Access Syntax): `object[index] = value` sends `atPut(index, value)`. The create-versus-modify distinction of `:` versus `=` belongs to the slot model and does not apply to indexing.
## 20. Object Composition

The core supports only one delegation parent. Horizontal reuse is performed through **object composition**.

```js
duck: animal {
    ...flyable
    ...swimmable
}
```

Each `...source` is a **composition item**: an object-body item, not a general expression form. Composition items are valid only inside object bodies. They participate in the same object-body item sequence as ordinary expressions and use the same separator rules: a logical `NEWLINE` separates items written on different logical source lines, and `;` separates items written on the same logical source line. Blank lines are permitted and create no empty items; leading, trailing, and consecutive `;` are syntax errors, and there is no implicit adjacency separator.

Both of these are valid:

```js
{
    ...base
    name: "Rex"
}

{
    ...base; name: "Rex"
}
```

while:

```js
{
    ...base name: "Rex"
}
```

is invalid because there is no separator between the composition item and the following object-body item.

A composition source is an ordinary object. The language has no distinct `Trait` value kind and requires no `trait` declaration. An object may be used as a trait-like source simply by composing its local slots into another object.

Composition copies **all local slot bindings** from the source object, regardless of whether a slot contains a closure, immutable data, mutable state, or any other object. It does not clone the objects stored in those slots. Thus composition copies bindings, not object graphs.

```js
positionable: {
    x: 0
    y: 0
}

player: { ...positionable }
enemy:  { ...positionable }
```

`player` and `enemy` each receive their own local `x` and `y` slots. Initially those slots contain the same immutable numeric values. Modifying `player.x` later modifies only `player`'s local slot. If a composed slot contains an ordinary mutable object, each copied slot initially refers to that same object; composition performs no implicit deep copy.

Composition does not introduce a delegation relationship and never modifies the receiving object's parent. After successful composition, the contributed slots behave exactly as local slots of the receiving object.

Composition order does not resolve conflicts. Explicit local slot declarations
directly contained in the receiving object body structurally reserve their names
for those declarations, independently of textual position. A composition item
does not contribute a source slot whose name is reserved in this way.

The reservation is not itself a slot or binding. It does not make the name
visible before its declaration executes, does not shadow lexical or delegated
lookup, and has no observable value. Its only semantic effect is to exclude that
name from composition contributions while this object body is being constructed.

Object-body items execute strictly from left to right. Each completed item is
visible to subsequent items, and no later item changes the meaning or effects of
an earlier evaluation.

For example:

```js
base: {
    x: 1
}

a: {
    ...base
    y: x
}
```

When `y: x` executes, `x` has already been contributed by `...base`, so `y`
receives the value `1`.

By contrast:

```js
b: {
    ...base
    y: x
    x: 42
}
```

The direct declaration `x: 42` reserves `x` for the receiving object. Therefore
`...base` does not contribute its `x` binding. When `y: x` executes, the local
`x` declaration has not executed yet, so that reservation has no effect on
lookup: `x` is resolved by the ordinary lookup rules as they stand at that
point, and lookup signals an error if no other binding is available. The later
`x: 42` declaration cannot retroactively affect that earlier lookup.

A local declaration therefore resolves composition conflicts structurally
without introducing temporal precedence between composition sources:

```js
walker:  { move: () => { ... } }
swimmer: { move: () => { ... } }

duck: {
    ...walker
    ...swimmer

    move: () => { ... }
}
```

Both composed `move` bindings are excluded because `move` is reserved by the
direct local declaration. The declaration creates the receiving object's
`move` slot when its body item executes.

For names that are not reserved by a direct local declaration, composition is
incremental. A unique contributed binding becomes a local slot of the receiving
object when that composition item successfully completes and is immediately
visible to subsequent body items.

If a later composition item would contribute a non-reserved name that already
exists locally on the receiving object, the composition item signals a
composition conflict. Composition order never selects a winner.

Each individual composition item is atomic with respect to structural changes
to the receiving object. The source expression is evaluated first under the
ordinary left-to-right evaluation rules. The runtime then determines all
effective local-slot contributions from that source, excluding reserved names,
and validates the complete contribution set before adding any of those slots.
If any effective contribution conflicts, that composition item adds none of its
slots. Effects that occurred while evaluating the source expression are not
rolled back.

The order in which a source object's local slots are represented or enumerated
therefore cannot affect whether composition succeeds, which conflict is
semantically present, or which subset of the source is installed.

The same conflict rule applies to every slot; there is no special distinction
between method-like closure slots and state slots. Ordinary delegation is
considered only after the local state visible at the point of lookup.

Conceptually:

```text
explicit local declaration reserves its name
        ↓
composition contributes only unreserved names
        ↓
a unique contribution becomes local when its item completes
        ↓
a second non-reserved local contribution is an error
        ↓
delegated lookup applies only when no local binding is present
```

This avoids composition-order precedence, method resolution orders, diamond
inheritance, deferred whole-body composition resolution, and multiple `super`
chains while preserving structural flattening and left-to-right evaluation.

### Composition Views: `without` and `alias`

Composition sources can be transformed using ordinary messages before they are passed to `...`. No trait-specific exclusion or alias syntax is introduced.

```js
duck: {
    ...walker.without("move")
    ...swimmer.alias("move", "swimMove")

    move: () => {
        swimMove()
    }
}
```

`without(name)` returns a new ordinary object suitable for composition whose local slots are the source object's local slots except for `name`. It does not modify the receiver. If `name` is not a local slot of the receiver, the operation signals an error.

`alias(sourceName, aliasName)` returns a new ordinary object suitable for composition that contains the receiver's local slots and additionally exposes the binding of `sourceName` under `aliasName`. Aliasing **adds** a name; it does not remove or rename the original slot. The two slots initially contain the same object.

`alias` signals an error if `sourceName` is not local to the receiver or if `aliasName` already exists locally in the resulting object.

Both operations copy slot bindings rather than cloning stored objects. Their results are ordinary objects; `...` has no knowledge that `without` or `alias` was used. These operations therefore compose naturally with the normal message model and introduce no separate trait mechanism.
## 22. Open Objects

Objects are initially open and mutable.

An open object permits local slot creation, modification, and removal subject to the normal rules. Slot removal never delegates.

`Object` is the standard root prototype for ordinary objects and provides the ordinary reflective messages `removeSlot(name)`, `close()`, and `freeze()`. These are normal message sends backed by runtime primitives; they are not special grammar forms.

### Return contracts of standard structural Object messages

The standard structural messages inherited from `Object` have the following
normal-result contracts:

```text
receiver.removeSlot(name)  -> exact value removed from the local slot
receiver.close()           -> receiver
receiver.freeze()          -> receiver
```

A successful `removeSlot(name)` returns the exact object that occupied the
removed local slot immediately before removal. It performs no copy, coercion, or
delegated lookup to determine that result. If removal fails under the existing
state/name/local-slot rules, no normal result is produced.

A successful structural `close()` returns the original receiver after applying
the existing object-state transition rules. Calling structural `close()` on an
already closed or frozen object retains the existing idempotent state semantics
and returns that same receiver.

A successful structural `freeze()` returns the original receiver after applying
the existing freeze transition. Calling structural `freeze()` on an already
frozen object is likewise idempotent and returns that same receiver.

These return rules expose no new primitive category and do not change ordinary
message lookup or overriding. In particular, this synchronous structural
`Object.close()` contract is distinct from the I/O-domain `Closable.close()`
protocol defined by `io/IO_CORE.md`, whose standard operation returns a
`Future`. When lookup selects an I/O `Closable.close()` behavior, the I/O
contract applies; inheriting `Object.close()` alone does not grant resource
lifecycle semantics.

```js
dog.removeSlot("age")
```

`removeSlot(name)` removes only a local slot of `this`. If the named slot is not local, the operation signals an error rather than searching the delegation chain. Removing a local overriding slot can therefore expose a delegated slot with the same name on subsequent reads.

```js
animal: { alive: true }
dog: animal { alive: false }

dog.removeSlot("alive")
dog.alive   // true, delegated from animal
```
## 23. Closed Objects

```js
object.close()
```

structurally closes an object.

Once closed, slots cannot be added or removed. Existing slots may still be modified if the object is not frozen.

Closing is shallow.
## 24. Frozen Objects

```js
object.freeze()
```

prevents both structural changes and modification of existing slot values.

Freezing is shallow.

```js
config.freeze()
```

does not automatically freeze `config.database`.

The delegation parent is immutable independently of `close()` or `freeze()`.

## Core Reflection

Core v0.1 provides a deliberately small reflective protocol through ordinary messages.

```js
object.hasSlot("name")
object.slotNames()
object.slotValue("name")
object.parent()
```

The slot-oriented reflective operations inspect only slots local to the receiver:

```text
hasSlot(name)
    true if the receiver has a local slot with that name;
    false otherwise.

slotNames()
    returns the names of the receiver's local slots.

### Deterministic `slotNames()` ordering

`slotNames()` returns an `Array` containing every local slot name exactly once
in ascending lexicographic order of the slot-name String's Unicode scalar-value
sequence.

The comparison is performed directly on the semantic String contents:
the first differing Unicode scalar value determines the order; if one sequence
is an exact prefix of the other, the shorter sequence comes first. No locale,
collation table, host string comparator, source declaration order, object shape,
hash-table order, insertion history, or implementation-specific slot layout
participates.

For example, if an object has the local slot names `"z"`, `"a"`, and `"aa"`,
`slotNames()` returns them in the order:

```text
["a", "aa", "z"]
```

The rule applies uniformly to slots created by ordinary `:`, composition,
runtime-provided standard behavior, or any other normative slot-creation
mechanism. Removing and later recreating a slot does not create a distinct
reflection position because creation history is not part of this ordering.

The returned `Array` is a snapshot of the receiver's local slot-name set at the
time `slotNames()` performs its reflective observation. Subsequent slot
creation, removal, or renaming-like library behavior does not retroactively
change that already-returned Array.

This ordering rule intentionally does not prescribe the receiver's internal slot
storage order. Implementations may use shapes, hash tables, compact arrays,
sorted tables, or any other representation; sorting may be performed lazily
only when reflection requires it. Ordinary non-reflective object access pays no
semantic ordering cost.

slotValue(name)
    returns the value stored in the receiver's local slot;
    signals an error if that local slot does not exist.
```

These operations do not perform delegated lookup. Normal member access remains the operation for lookup through the delegation chain:

```js
object.name
```

`parent()` returns the receiver's immutable delegation parent.

`Object` is the unique structural root and has no parent. Calling:

```js
Object.parent()
```

signals an error rather than manufacturing a sentinel parent value such as `null`.

Core reflection intentionally distinguishes the object's own slot structure from ordinary delegated behavior. Core v0.1 does not require reflective access to implementation-internal activation frames, stacks, `methodHome`, or runtime representation details.
