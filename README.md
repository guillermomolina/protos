# Protos

## Name

The language is named **Protos**.

The name reflects its prototype-based object model and derives naturally from the root associated with *prototype*: the first, original, or archetypal object from which behavior can be derived through delegation.

Canonical project documentation uses the `PROTOS_` filename prefix.

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

The normative Core v0.1 specification is modular. `PROTOS_GRAMMAR.md` owns
lexical/syntactic rules and mandatory lowering; `PROTOS_LANGUAGE_SPEC.md` is the
core language overview and compatibility/navigation surface; primary semantic
owners live under `semantics/`, `concurrency/`, and `io/`.

`runtime/ABSTRACT_RUNTIME.md` is an informative, non-normative execution model
and pseudocode aid. It must remain aligned with the normative owners but does
not independently define observable Protos behavior.

## License

Protos is licensed under the Adaptive Public License 1.0 (APL-1.0), an
OSI-approved open-source license. The complete Protos-specific license,
including the completed Exhibit A, is in [LICENSE.TXT](LICENSE.TXT).

Programs written in Protos are not automatically governed by the APL
merely because they are compiled or run using Protos.

Independent modules or plugins may use separate licenses when they
qualify as Independent Modules or Larger Work components under the APL.

## Specification Status

Version 0.1 remains a draft, but the major semantic decisions currently tracked by the design are specified. The normative specification modules are periodically audited for single ownership and consistency; the informative runtime pseudocode is kept aligned with those normative owners.

## Recent Design Decisions

- Structured concurrency is strict by default: an activation waits for non-detached child tasks on normal completion, and cancels then waits for them during error/cancellation unwind.
- `Future.then` is part of the standard Core v0.1 Future protocol and flattens nested Futures.
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


## Text and Binary Data

- `String` represents abstract Unicode text.
- `Bytes` represents raw byte sequences.
- Character encodings such as UTF-8 or UTF-16 are separate protocol objects/concepts.
- Encoding and decoding are explicit operations.
- A String's internal storage format is not part of its language-level semantics.


## String Semantics

- `String` is immutable.
- `String.size` and `String.at` operate on Unicode grapheme clusters.
- Lower-level code-point and byte access is explicit through separate protocols.
- `StringBuilder`-style objects handle efficient mutable text construction.
- `Bytes` is mutable.
- Encoded text representations may be first-class objects and define their own mutability through protocol support such as `atPut`.


## Maps and Hashing

- `Map` uses `==` plus `hash`.
- `IdentityMap` uses `===` plus `identityHash`.
- Equal keys must have equal hashes.
- Hash/equality behavior must remain stable while a key is stored.
- Mutable objects may be keys when the state relevant to hash/equality remains stable.
- Missing-key indexed lookup signals an error rather than returning `null`.
- Maps preserve insertion order.
- Ordinary hashes need not be stable across process executions.

## Equality and Comparison Results

The standard equality and comparison protocol is Boolean-valued:

```text
==  !=  <  <=  >  >=
```

These operations return canonical `true` or `false`, or signal an error. The language never interprets arbitrary objects as truthy comparison results.

This also means `Map` can rely directly on `==` without introducing a separate key-equality convention.

## Numeric Equality and Identity

Numeric `==` compares mathematical numeric value across numeric families without requiring coercion:

```js
1 == 1.0               // true
UInt8(1) == 1          // true
```

The comparison must not create false equality through lossy conversion.

Numeric `===` is stricter and includes the semantic numeric family:

```js
1 === 1.0               // false
UInt8(1) === 1          // false
Int32(1) === UInt32(1)  // false
```

Float special cases such as NaN and signed zero are still being specified.

## Float NaN Semantics

`NaN` is a special semantic `Float` value, not a singleton language object like `null`.

```js
nanA == nanB    // false
nanA === nanB   // true
```

IEEE NaN payloads and host-level representations are treated as representation details unless inspected through an explicit low-level protocol.

Core v0.1 does not require `NaN` or infinity literals. Standard Float protocol may expose values such as `Float.nan`, `Float.infinity`, and `Float.negativeInfinity`.

## Float Signed Zero

Signed zero follows IEEE-style numeric equality while remaining visible to semantic identity:

```js
0.0 == -0.0    // true
0.0 === -0.0   // false
```

The sign bit is therefore part of Float semantic identity, unlike NaN payload differences.

## Parameter Names

Parameter names in a closure signature must be unique. Duplicate required/default parameter names and collisions with the rest parameter are rejected before execution.

## Custom Operator Alphabet

Custom symbolic binary operators use the fixed character set:

```text
! $ % & * + - / < = > ? @ \ ^ | ~
```

Structural punctuation such as `.`, `:`, `;`, `,`, and brackets/braces/parentheses is excluded. Reserved and standard operator tokens are recognized before remaining symbolic sequences are classified as custom operators.

## Malformed Text Decoding

Text decoding is strict by default:

```js
bytes.decode(UTF8)  // malformed input signals an error
```

Loss-tolerant decoding must be requested explicitly, for example with a replacement policy:

```js
bytes.decode(UTF8, ReplaceInvalid)
```

The exact standard policy names remain a library detail.

## Standard Encodings

Core v0.1 requires these first-class encoding objects:

```text
UTF8
UTF16LE
UTF16BE
UTF32LE
UTF32BE
ASCII
Latin1
```

Additional encodings belong to the standard library or optional modules. Endian-unspecified `UTF16`/`UTF32` are not Core aliases; if provided later, their BOM and endianness rules must be explicit.

## Dynamic Error Handling

Core v0.1 installs dynamic handlers through the standard closure protocol:

```js
(() => {
    riskyOperation()
}).handle(IOError, (error) => {
    recover(error)
})
```

`handle` uses the existing prototype-chain matching and unwinding semantics. The expression evaluates to the protected result on success or the handler result when a matching error is handled.

## Futures and Concurrency

Core v0.1 defines cooperative Future cancellation:

```js
future.cancel()
```

Cancellation never means unsafe forced termination; normal unwinding and `ensure` cleanup still apply.

Unhandled task errors are stored by the Future and re-signaled when a consumer executes:

```js
future.value()
```

Handlers inside the task apply during task execution; handlers surrounding `future.value()` apply when a stored failure is observed.

Future completion also establishes a visibility boundary: effects performed before completion are visible after that completion is observed through `future.value()`. Shared mutable state otherwise requires explicit synchronization for correct concurrent access.


## Core Reflection

Core v0.1 exposes a small reflective protocol:

```js
object.hasSlot("name")
object.slotNames()
object.slotValue("name")
object.parent()
```

The slot operations inspect only slots local to the receiver; they never perform delegated lookup. Normal member access continues to use delegation.

`Object.parent()` signals an error because `Object` is the unique structural root and has no parent.
