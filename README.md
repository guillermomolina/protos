# Prototype-Based Language

> **Working title:** Prototype-Based Language\
> **Status:** Early language design / specification draft\
> **Language version:** 0.1

An experimental object-oriented programming language built around
**prototypes, delegation, objects, messages, and closures**.

The language is being designed from first principles rather than as a
class-based language with prototypes added on top. There are **no
classes**: objects delegate directly to other objects, and `Object` is
the unique root of the delegation hierarchy.

The project is currently in the language-design phase. The
specification, grammar, and runtime semantics are being developed before
committing to a full implementation.

## Design goals

-   **Everything is an object.**
-   **Prototype-based delegation only.** No classes or class hierarchy.
-   **Single, immutable delegation parent.**
-   **Reads delegate; writes do not delegate.**
-   **Uniform execution contexts.** Parameters, temporaries, and
    top-level bindings are slots of context objects.
-   **Closures are the single executable value.** A method is a role a
    closure takes when invoked through an object, not a separate kind of
    value.
-   **Dynamic typing.** Behavior matters; declared static types do not
    exist in the core language.
-   **Messages over special syntax.** Language mechanisms should be
    ordinary object behavior whenever practical.
-   **Minimal historical baggage.** No `class`, `new`, `var`, `let`,
    `const`, `undefined`, `async`, `await`, or automatic semicolon
    insertion.
-   **Predictable semantics.** Evaluation is left-to-right, object
    delegation is explicit, and implementation optimizations must not
    change observable behavior.

## A first look

Objects are created directly from other objects:

``` js
animal: {
    alive: true

    speak: () => {
        print(name)
    }
}

dog: animal {
    name: "Rex"
}

dog.speak()
```

`dog` delegates to `animal`. The inherited `speak` closure executes with
`dog` as `this`, so the bare `name` resolves to `"Rex"`.

There are no classes and no constructors involved in this example.

## Slots

A slot associates a name with an object.

The language deliberately distinguishes **creation** from
**modification**:

``` js
x: 10
x = 20
```

The rule is:

``` text
: creates
= modifies
```

Reads may follow the delegation chain, but writes never do:

``` js
animal: {
    alive: true
}

dog: animal {
    name: "Rex"
}

dog.alive          // true
dog.alive = false  // error: alive is not local to dog
dog.alive: false   // explicit local override
dog.alive = true   // OK
```

This prevents a descendant from accidentally mutating an ancestor
prototype.

## Delegation

`Object` is the unique root object.

``` text
Object
  ↑
animal
  ↑
dog
```

`Object` has no delegation parent. Every other object has exactly one
delegation parent, and that parent is fixed when the object is created.

A bare object literal delegates directly to `Object`:

``` js
point: {
    x: 10
    y: 20
}
```

An explicit parent expression creates an object that delegates to that
object:

``` js
dog: animal {
    name: "Rex"
}
```

## Closures and methods

Closures use a single syntax:

``` js
add: (a, b) => {
    a + b
}
```

There is no separate `Method` value in the core language.

A closure stored in a slot acts as a method when invoked through an
object:

``` js
dog.speak()
```

Reading the slot does not execute it:

``` js
f: dog.speak
```

The extracted closure retains the receiver binding, so it can later be
invoked as:

``` js
f()
```

while preserving the receiver associated with the method extraction.

## `this`, `context`, and `super`

`this` is the current receiver:

``` js
this.name
```

`context` is the current execution-context object. Parameters, temporary
bindings, and local slots live in execution contexts.

`super` is deliberately **not a value**. It is special lookup syntax for
continuing a message lookup after the object where the current method
was found while preserving the original receiver:

``` js
super.speak()
```

## Non-local return

The language uses:

``` js
^value
```

for non-local return.

Its semantics follow the home-activation idea used by Smalltalk-family
block closures: a nested closure can return from the active invocation
that lexically owns it.

``` js
find: (items) => {
    items.each(item) {
        item.valid.ifTrue() {
            ^item
        }
    }

    null
}
```

An attempted non-local return after its home activation has already
finished is an error.

## `null`

There is exactly one absence value:

``` js
null
```

A missing slot is **not** `null`; attempting to read a missing slot is
an error.

`null` is itself an object and participates in the ordinary object
model.

## Value identity

`===` represents semantic identity and is not overridable.

Immutable value objects such as numbers and strings have identity
derived from their value:

``` js
1 === 1                    // true
"hello" === "hello"        // true
("hel" + "lo") === "hello" // true
```

Ordinary mutable objects have individual identity:

``` js
a: { x: 1 }
b: { x: 1 }

a === a  // true
a === b  // false
```

`==` is semantic/value equality and may be customized independently.

Strings are immutable. Mutable text-building facilities can be provided
by separate objects rather than making `String` itself mutable.

## Object state

Objects begin open.

Core object behavior includes operations conceptually equivalent to:

``` js
object.close()
object.freeze()
object.removeSlot("name")
```

-   **Open:** slots may be created, modified, or removed.
-   **Closed:** existing slots may be modified, but the object's
    structure cannot change.
-   **Frozen:** neither structure nor slot values may change.

`removeSlot` only operates on local slots. It never deletes through
delegation.

## Expression sequencing

Newlines and semicolons separate expressions:

``` js
x: 10
y: 20
```

or:

``` js
x: 10; y: 20
```

Commas are reserved for list-like syntax such as arguments and
parameters:

``` js
add(10, 20)
```

They are not general expression separators.

There is no JavaScript-style automatic semicolon insertion.

## Evaluation order

Evaluation is strict and left-to-right unless a construct is explicitly
lazy.

``` js
foo().bar(a(), b())
```

evaluates the receiver first, followed by arguments from left to right.

Lazy boolean operations may avoid evaluating their right-hand side.

## Modules and the prelude

There is no special global-variable category.

Each module executes in its own context object, and top-level bindings
are slots of that context.

A shared standard prelude provides common language facilities. The
prelude is frozen: modules may read its bindings but cannot mutate them.

A module can explicitly shadow a prelude binding by creating a local
slot:

``` js
print: myPrint
```

## What is intentionally not in the core

The core language does not introduce constructs such as:

``` text
class
extends
new
var
let
const
function
async
await
for
undefined
try
catch
throw
finally
```

Some familiar syntax may eventually exist as syntactic sugar, but it
should lower to the smaller semantic core rather than introduce parallel
mechanisms.

## Current specification

The design is currently described by three documents:

-   `PROTIA_LANGUAGE_SPEC_v0.1.md` --- object model and language
    semantics
-   `PROTIA_GRAMMAR_v0.1.md` --- lexical and syntactic grammar
-   `PROTIA_RUNTIME_SEMANTICS_v0.1.md` --- executable runtime model and
    pseudocode

The filenames still contain an earlier working name and will be renamed
once the language receives its final name.

## Work in progress

Version 0.1 is still a draft. Important areas are deliberately being
resolved before implementation, including:

-   composition and trait conflict semantics
-   collection and array syntax, including possible `[]` sugar for
    indexed access/update
-   constructors and destructors / object lifecycle
-   module import/export semantics
-   error handlers and conditions
-   futures, cancellation, and structured concurrency
-   numeric and string literal details
-   reflection and standard-library protocols

The goal is to stabilize the semantic model first, then implement it
without having implementation details silently define the language.


## Recent Design Decisions

- Bracket access is ordinary `at` / `atPut` protocol sugar.
- Invocation arguments support defaults, rest capture, spread, and reflective `args`.
- `()` is polymorphic invocation syntax.
- Ordinary prototypes inherit default construction from `Object`: create a child object, send `init(...args)`, and return the instance.
- Alternative constructors are ordinary named messages.
- `...` is contextual structural syntax for rest capture, argument spread, and slot composition.


## Resource Lifetime

- Deterministic destructors are not part of Core v0.1.
- External resources are released through explicit protocols such as `close()`.
- The runtime provides unwind-safe `ensure` semantics for normal completion, `^`, and error unwind.
- GC finalization is not considered a reliable resource-management mechanism.


## Numeric Model

- `Number` is the common numeric prototype.
- `Integer` values are exact and semantically arbitrary precision.
- `Float` is a separate numeric specialization.
- Fixed-width integers such as `UInt8` and `Int32` are explicit and range-checked.
- Ordinary fixed-width arithmetic does not silently wrap.
- Bit operations belong naturally to integer protocols.
- Endianness belongs to binary encoding/decoding, not to the numeric value itself.
- Float literals require digits after the decimal point; `2.` is not a valid float literal.
