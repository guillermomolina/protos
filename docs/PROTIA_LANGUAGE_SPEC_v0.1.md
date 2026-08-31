# Core Language Specification v0.1

Language version: 0.1  
Document revision: 29  
Status: Draft  
Last updated: 2026-08-31

## 1. Principles

The language is an object-oriented language based exclusively on **prototypes and delegation**.

There are no classes.

Every observable value is an object. This includes numbers, strings, booleans, closures, errors, futures, execution contexts, and `null`.

An implementation may internally represent some objects using immediate values, tagged pointers, specialized memory layouts, or other optimizations, provided that these optimizations are not observable through the language semantics.

The language has a uniform execution model. There is no fundamental semantic distinction between "global code", "local variables", and "properties". Execution always takes place within contexts, and contexts are themselves objects.

The language favors objects and messages over keywords and special syntactic constructs.

## 1.1 Dynamic Typing

Core v0.1 is dynamically typed.

Slots and parameters do not carry mandatory static type declarations. A slot may hold objects with different behavior over time, subject only to the normal object-state and assignment rules.

Message validity is determined dynamically by receiver behavior and delegation. The language does not introduce overload resolution by declared argument type.

Implementations and tools may infer types, specialize code, or expose optional analysis, but such information must not change observable language semantics.

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


## Module Contexts and Top-Level Bindings

The language has no special semantic category of global variables.

Every module executes inside a `moduleContext`, which is an ordinary execution-context object. Bindings created at the top level of a module are local slots of that module context.

```js
version: "0.1"

printVersion: () => {
    print(version)
}
```

Conceptually:

```text
moduleContext
├── version
└── printVersion
```

Closures created during module execution capture the module context through the ordinary lexical-context mechanism. No separate global lookup or global assignment rule exists.

Each module has its own module context. Modules do not implicitly share mutable global state.

Cross-module visibility must be established explicitly by the module/import/export mechanism. The exact module loading, import, export, initialization, and cyclic-dependency semantics are specified separately.

Universal language facilities such as core prototypes and standard behavior may be supplied through a shared prelude or root lexical environment. Such facilities remain part of the ordinary context and lookup model rather than introducing a global-variable namespace.

The standard prelude is shared but **frozen**. Its slots may be read through ordinary lexical lookup, but unqualified `=` must never mutate a prelude slot. Attempting to modify a binding that resolves only to the frozen prelude signals an assignment error. A module that wants to shadow a prelude binding creates a new local slot with `:`.

```js
print("hello")     // reads the prelude binding
print = myPrint     // ERROR: the prelude binding is frozen
print: myPrint      // OK: creates a module-local binding that shadows it
```

This preserves module isolation: modules may share immutable standard facilities, but they do not acquire shared mutable global state through the prelude.

Therefore, at module top level:

```js
x: value
```

creates `x` as a local slot of the current `moduleContext`.

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

Only a super message send is valid in the core language, for example `super.speak()` or `super.move(x, y)`. Expressions such as `x: super`, `foo(super)`, bare `super`, or method extraction such as `f: super.speak` are invalid.

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

A closure is written as:

```js
() => {
    ...
}
```

or:

```js
(a, b) => {
    ...
}
```

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

## 10. Closures and Methods

The language has one executable value kind in the core language: **Closure**. There is no separate `Method` value type.

A closure installed as an object slot acts as a method when it is reached through a message send. "Method" therefore describes an invocation role, not a distinct kind of object.

```js
animal: {
    speak: () => {
        print(name)
    }
}
```

A call:

```js
dog.speak()
```

dynamically binds `this` to `dog`.

A closure created during that execution captures this receiver lexically:

```js
animal: {
    speaker: () => {
        () => {
            print(name)
        }
    }
}

f: dog.speaker()
f()
```

`f` retains `this === dog`.

## 11. Extracted Methods

Reading a closure-valued slot does not execute it. It reads the executable value. When that value is obtained through receiver lookup, The language preserves the receiver and lookup origin as binding metadata so that a later plain call has the same receiver semantics as the original method reference.

This does not create a distinct `Method` object type; the resulting value is still a closure semantically, with receiver binding metadata.

```js
f: dog.speak
```

A subsequent:

```js
f()
```

retains `this === dog`.

The language therefore does not reproduce JavaScript's lost-`this` behavior when a method is extracted.

## 12. Closures and `super`

A closure created inside a method retains the information required to resolve `super`.

```js
dog: animal {
    action: () => {
        f: () => {
            super.action()
        }

        f()
    }
}
```

The closure preserves both the original receiver and the `methodHome` required to continue delegation correctly.

## 13. Return Semantics

The value of the final expression in a closure is its normal return value.

```js
square: (x) => {
    x * x
}
```

Early return is expressed using:

```js
^value
```

The language follows the Smalltalk/Squeak **home activation** model for non-local return.

A top-level function or method invocation establishes a return home. Closures created lexically during that invocation capture that same home rather than creating a new one merely because they are called.

```js
find: (items) => {
    items.each(item) {
        item.valid.ifTrue() {
            ^item
        }
    }

    null
}
```

`^item` returns from the active invocation of `find`, not merely from either nested closure.

A direct `^` in `find` targets the same home activation:

```js
find: (items) => {
    ^42
    null
}
```

Closures defined at module level have no enclosing function return home. When such a closure is invoked as a function, that invocation establishes its own return home. Method invocation likewise establishes a fresh return home for the invoked method.

A closure created inside an active function or method invocation captures that invocation's return home. Calling such a closure as an ordinary nested block does not replace the captured home.

`return value` may eventually exist as syntactic sugar for `^value`, but `return` is not part of the fundamental semantics.

## 14. Return from Escaped Closures

A nested closure may outlive the activation that owns its captured return home.

```js
make: () => {
    () => {
        ^42
    }
}

f: make()
f()
```

The closure returned by `make` still refers to the completed invocation of `make`. Therefore executing `^42` later signals `InvalidReturn`.

The runtime must not reinterpret that operation as a local return from `f`.

## 15. `null`

Exactly one object represents absence:

```js
null
```

There is no `undefined`.

```js
x: null
```

means the slot `x` exists and contains `null`.

A failed lookup signals an error. It does not evaluate to `null`.

`null` is a singleton object and may respond to messages like any other object.

## 16. Booleans

`true` and `false` are singleton objects.

Conditional control flow is semantically implemented through messages sent to these objects.

Conceptually:

```js
condition.ifTrue() {
    ...
}

condition.ifFalse() {
    ...
}
```

Closures provide lazy evaluation.

Possible `if`/`else` syntax may exist as sugar, but does not define the fundamental semantics.

## 17. Iteration and Loops

No primitive `for` construct is required.

```js
users.each(user) {
    print(user.name)
}

users.map(user) {
    user.name
}

1.to(10).each(i) {
    print(i)
}
```

A `while` operation requires a reevaluated condition and therefore semantically operates on a closure:

```js
(() => i < 10).while() {
    i = i + 1
}
```

A future `while (...) { ... }` form may be syntactic sugar.

## 18. Trailing Blocks

A closure may be passed as the final argument using trailing-block syntax.

```js
users.each(user) {
    print(user)
}
```

is conceptually equivalent to:

```js
users.each((user) => {
    print(user)
})
```

Trailing-block syntax does not alter closure semantics.

## 19. Separators and Line Breaks

There is no Automatic Semicolon Insertion.

A line break may separate expressions when the grammar determines that the preceding expression is complete.

```js
foo()
bar()
baz()
```

is equivalent to:

```js
foo(); bar(); baz()
```

`;` is the explicit horizontal expression separator. A comma is **not** an expression separator.

Therefore an object body written on one line uses `;`:

```js
point: { x: 10; y: 20 }
```

and is equivalent to:

```js
point: {
    x: 10
    y: 20
}
```

`,` is reserved for list-like syntax such as argument lists and parameter lists, and may also be used by future collection literal syntax. It never sequences arbitrary expressions.

Incomplete expressions continue across line breaks:

```js
result: 1 +
    2 +
    3

result: object
    .foo()
    .bar()
```

## 20. Object Composition

The core supports only one delegation parent. Horizontal reuse is performed through **object composition**.

```js
duck: animal {
    ...flyable
    ...swimmable
}
```

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

Composition order does not resolve conflicts. If multiple composed objects provide the same slot name, the composition is invalid unless the receiving object explicitly declares that slot locally in its own body. The position of that explicit declaration relative to the composition expressions is irrelevant.

```js
walker:  { move: () => { ... } }
swimmer: { move: () => { ... } }

duck: {
    ...walker
    ...swimmer

    move: () => { ... }   // explicitly resolves the conflict
}
```

The same conflict rule applies to every slot; there is no special distinction between method-like closure slots and state slots. A local declaration has priority over composed contributions, composed contributions must agree on uniqueness, and ordinary delegation is considered only after local/composed slots have been established.

Conceptually:

```text
explicit local slot > unique composed slot > delegated lookup
```

This avoids composition-order precedence, method resolution orders, diamond inheritance, and multiple `super` chains while preserving structural flattening.

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

## 21. Equality and Identity

`===` represents **semantic identity** and is not customizable. Its result must not depend on allocation, interning, boxing, tagged values, or any other implementation strategy.

For ordinary identity-bearing objects, identity means that both expressions denote the same individual object:

```js
a: { x: 1 }
b: { x: 1 }
c: a

a === b  // false
a === c  // true
```

Some built-in immutable objects have **value identity**. For these objects, the semantic value itself determines identity rather than a particular allocation. In v0.1 this includes at least `Number`, `String`, `Boolean`, and `null`.

```js
1 === 1                    // true
"hello" === "hello"        // true
("hel" + "lo") === "hello" // true
true === true              // true
null === null              // true
```

`Number` objects are immutable value objects.

`String` objects are immutable value objects. An operation on a String never changes that String in place; an operation that produces different text produces another String value. Implementations may freely share or intern String storage because such sharing cannot change observable identity semantics.

`true`, `false`, and `null` are canonical singleton values.

Ordinary mutable objects, closures, arrays and other identity-bearing objects retain individual object identity even when their contents happen to be equal. The exact collection model is specified separately.

`==` represents semantic equality and may be customized through object behavior.

The equality protocol has a strict result contract:

```text
==  -> true | false | error
!=  -> true | false | error
```

An implementation of `==` must return one of the canonical Boolean objects `true` or `false`, or signal an error. Returning any other object is an invalid equality result.

The same Boolean-result contract applies to the standard comparison operators:

```text
<   <=   >   >=
```

They return canonical `true` or `false`, or signal an error. The language defines no truthiness conversion for interpreting arbitrary comparison results. For built-in immutable value objects, `==` and `===` may naturally produce the same result, but they remain different operations: `==` is behavioral and customizable, while `===` is a non-overridable identity primitive.

Identity is never defined by comparing hash codes. A runtime may derive or cache hashes from identity where appropriate, but hash collisions cannot make distinct identity-bearing objects identical.

## 21.1 Custom Symbolic Binary Operators

User-defined symbolic binary operators are permitted as ordinary message selectors.

A custom operator expression:

```js
a @ b
```

lowers to an ordinary receiver-based send of the symbolic selector to `a`, with `b` as its argument.

All custom binary operators have the same precedence relative to one another and associate left-to-right:

```js
a @ b |> c
```

means:

```js
(a @ b) |> c
```

Core v0.1 deliberately defines no implicit precedence relationship between custom binary operators and the standard operator groups. Mixing them without explicit grouping is therefore invalid:

```js
a + b @ c      // invalid
a @ b * c      // invalid
```

Parentheses make the intended grouping explicit:

```js
(a + b) @ c
a @ (b * c)
```

Modules, imports, declarations, or runtime mutation cannot change parser precedence. The exact lexical set of characters permitted in custom symbolic selectors remains to be finalized separately.

## 22. Open Objects

Objects are initially open and mutable.

An open object permits local slot creation, modification, and removal subject to the normal rules. Slot removal never delegates.

`Object` is the standard root prototype for ordinary objects and provides the ordinary reflective messages `removeSlot(name)`, `close()`, and `freeze()`. These are normal message sends backed by runtime primitives; they are not special grammar forms.

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

## 25. Errors

Errors are objects.

Expected domain failures may be represented using ordinary values.

Exceptional conditions use signaling:

```js
error.signal()
```

Handlers are dynamically installed in the execution environment of closures.

No fundamental `try`, `catch`, `throw`, or `finally` keywords are required.

An unhandled error propagates until an appropriate handler is found or the outermost execution boundary is reached.

The architecture should allow resumable conditions to be added later without redesigning the execution model.

## 26. Futures

`Future` is an ordinary object representing the eventual result of an execution.

There are no `async` functions and no `await` keyword.

An ordinary function may simply return a Future.

## 27. Asynchronous Execution

A closure may request asynchronous execution:

```js
work: () => {
    calculate()
}

resultFuture: work.future()
```

`future()` is a visible message whose fundamental implementation reaches a runtime primitive.

Conceptually:

```text
closure
   ↓
create pending Future
   ↓
create/schedule closure execution
   ↓
return Future immediately
```

The same closure may execute synchronously:

```js
result: work()
```

or asynchronously:

```js
future: work.future()
```

Asynchrony belongs to **the execution of a closure**, not to its definition.

## 28. Future Resolution

Normal completion:

```text
Future → resolved(result)
```

Unhandled error:

```text
Future → failed(error)
```

The error uses the ordinary signaling system. There is no separate promise-rejection mechanism.

## 29. Obtaining a Future's Value

```js
result: future.value()
```

returns immediately when resolved.

If pending, the current activation is suspended. This does not require blocking an operating-system thread.

The implementation may use threads, fibers, coroutines, an event loop, or another scheduling mechanism without changing language semantics.

If the Future completed with an error, `value()` signals that error in the waiting activation.

## 30. Future Composition

A Future may support transformation:

```js
future.then(value) {
    transform(value)
}
```

`then` returns another Future.

If the block returns an ordinary value, the resulting Future resolves with it.

If the block returns another Future, the result is automatically flattened rather than producing a nested Future.

## 31. Structured Concurrency

Asynchronous executions belong by default to the execution context that creates them.

This provides a defined lifetime and cancellation structure.

When an owning context is cancelled or can no longer maintain its child tasks, associated pending asynchronous executions may be cancelled.

An explicit operation such as:

```js
future.detach()
```

may detach asynchronous work when an independent lifetime is deliberately required.

The exact scheduler implementation is outside the language specification.

## 32. Primitives

Expressing an operation through messages does not require its entire implementation to be written in the language.

For example, arithmetic messages may ultimately execute native arithmetic primitives.

Likewise:

```js
closure.future()
```

may ultimately invoke a scheduler primitive.

A primitive is an implementation mechanism, not an exception to the object model.

## 33. Constructs Outside the Semantic Core

The core does not require these as semantic primitives:

```text
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

`return`, `if`, `else`, and `while` may eventually exist as syntactic sugar.

## 34. Core Language Invariants

```text
Everything is an object.

The language is dynamically typed.

There are no classes.

Object is the unique root object and has no delegation parent.

Every other object has exactly one delegation parent.

Every delegation chain terminates at Object.

An object's delegation parent cannot change after creation.

Reads may delegate.

Writes never delegate.

: creates.
= modifies.

null is the only absence value.

A missing slot is not null; it is an error.

Execution contexts are objects.

Closures capture lexical contexts by reference.

Methods dynamically receive their receiver.

Inner closures capture that receiver.

Extracted methods remain bound to their receiver.

super preserves both the receiver and the lookup origin.

^ performs a non-local return.

Errors are objects and are signaled.

Futures are objects.

Asynchrony is a property of an execution,
not a separate category of function.

Objects may be closed or frozen.

Horizontal composition does not modify the delegation chain.

Top-level bindings are slots of a module execution context.

There is no special global-variable category.

Modules do not implicitly share mutable global state.


## Conditional Protocol and Truthiness

The language defines **no language-wide truthiness conversion**.

Conditional behavior is expressed through ordinary messages. The standard Boolean objects `true` and `false` provide the standard conditional protocol, including behavior corresponding to:

```js
condition.ifTrue(block)
condition.ifFalse(block)
```

A receiver is not required by the language to be a Boolean in order to receive these messages. Any object may implement messages such as `ifTrue`, `ifFalse`, `and`, or `or` and define behavior appropriate to that object.

Consequently, values such as `0`, `""`, `null`, arrays, and arbitrary objects are neither inherently truthy nor inherently falsy. If they do not implement the requested conditional message, ordinary message lookup fails in the usual way.

Equality and comparison protocols have a Boolean-result contract. Implementations of `==`, `!=`, `<`, `<=`, `>`, and `>=` must return the canonical Boolean objects `true` or `false`, or signal an error. User-defined implementations remain ordinary message behavior, but returning any other object violates the protocol contract.

Logical operator syntax, where provided, lowers to ordinary message sends with explicit laziness. For example:

```js
a && b
a || b
```

lower conceptually to:

```js
a.and(() => b)
a.or(() => b)
```

so the right-hand side is evaluated only if the receiver's implementation chooses to invoke the supplied closure.

Implementations may specialize common Boolean receivers and standard operations in the interpreter or JIT, provided that such specialization preserves the observable semantics of ordinary message sends.


## Error Signaling and Handling

Errors are ordinary objects.

Exceptional language and runtime failures are signaled through ordinary error objects. Conceptually:

```js
error.signal()
```

Handlers are dynamically scoped. When an error is signaled, the runtime searches the dynamically active handlers from nearest to farthest and selects the nearest handler whose match prototype occurs in the signaled error object's delegation chain.

Thus error categories require no classes or static types. For example, an error object delegating through `FileNotFound` and `IOError` can be handled by a handler matching `FileNotFound`, `IOError`, or a more general error prototype present in that chain.

Handling in Core v0.1 is **unwinding**. A matching handler transfers control out of the signaling computation to the handler. Normal return from the handler does not resume execution at the original signaling point.

Core v0.1 does not define resumable conditions, `resume`, `retry`, or equivalent control operations. The runtime representation of signaling and handlers should nevertheless avoid assumptions that would make explicit resumable-condition facilities impossible to add in a later language version.

The exact surface syntax or standard-library protocol used to install a dynamic handler is specified separately; handler matching and unwinding behavior are semantic requirements independent of that syntax.


## Module Loading, Identity, and Cycles

Each module executes in a private `moduleContext`. The module's externally visible result is the value produced by successful module initialization. That value may be any object; it is not required to be a namespace object.

Module import uses three distinct concepts:

```text
module specifier
    value written by the program

ModuleKey
    canonical internal identity produced by the module resolver

module value
    value produced by successful initialization
```

A module specifier is resolved relative to the importing module and the host/module-resolution environment. Resolution produces a canonical `ModuleKey`.

The exact external form of a `ModuleKey` is host-defined, but it must provide stable identity. Examples might include canonicalized file URIs, standard-library identifiers, or package identifiers. Two import requests that the resolver determines refer to the same module must produce the same `ModuleKey`.

`ModuleKey` is an internal loader/runtime concept. It is not required to be exposed as a normal language object.

Modules are initialized at most once per module registry. The registry tracks module state conceptually as:

```text
UNLOADED
LOADING
LOADED
FAILED
```

The first import of an `UNLOADED` module marks it `LOADING`, creates its private module context, executes it, records the produced module value, then marks it `LOADED`.

A later import of a `LOADED` module returns the cached module value.

Partially initialized module values are never observable. If an import attempts to require the value of a module that is currently `LOADING` through the active dependency chain, the runtime signals `ModuleInitializationCycle`.

Failed module initialization is remembered. A later import of the same canonical module re-signals the stored initialization failure rather than silently retrying.

Imports are eager by default. Lazy dependencies are expressed explicitly using ordinary language mechanisms such as closures rather than by implicit lazy-import semantics.

The language does not require import syntax to introduce names into the current lexical scope. An import operation may simply produce the module value, which the program can bind explicitly:

```js
math: import("math")
math.sin(x)
```

The exact resolver rules for files, packages, standard-library modules, search paths, and other host-specific sources are outside Core Language v0.1.


## Indexed Access Syntax

Bracket indexing is syntactic sugar over ordinary message sends. Indexing is not a privileged runtime operation and is not restricted to arrays.

Indexed read:

```js
receiver[index]
```

lowers conceptually to:

```js
receiver.at(index)
```

Indexed write:

```js
receiver[index] = value
```

lowers conceptually to:

```js
receiver.atPut(index, value)
```

Any object may support bracket syntax by implementing the corresponding messages.

The bracket forms do not bypass normal message lookup, mutability rules, or error signaling. The meaning of an index, accepted index types, bounds behavior, and storage semantics are defined by the receiver's protocol.

Evaluation order is left-to-right. For:

```js
getReceiver()[getIndex()] = makeValue()
```

the runtime evaluates `getReceiver()`, then `getIndex()`, then `makeValue()`, and finally performs the `atPut` message send.

The selector names `at` and `atPut` are part of the Core v0.1 indexed-access protocol.


## Invocation Arguments, Defaults, Rest, and Spread

Every invocation exposes the arguments supplied by the caller as an ordinary immutable collection through the reserved intrinsic `args`.

`args` is not an ordinary writable identifier and cannot be shadowed by a parameter or local slot.

`args` contains exactly the explicit argument expressions from the call site, after evaluation and in source order. It does not contain the receiver and does not contain the caller activation.

For example:

```js
dog.move(10, 20)
```

inside `move`:

```js
this       // dog
args[0]    // 10
args[1]    // 20
args.size  // 2
```

The receiver remains available through `this`. Caller introspection, if exposed in the future, belongs to execution-context reflection rather than the argument collection.

Default parameters are supported. Defaults apply only when the corresponding argument was not supplied by the caller.

```js
foo: (a, b = 10) => {
    ...
}
```

For:

```js
foo(1)
```

`b` is bound to `10`, while `args` still contains only the caller-supplied value:

```text
args == [1]
```

A closure may declare one trailing rest parameter:

```js
foo: (first, ...rest) => {
    ...
}
```

The rest parameter is bound to an ordinary collection containing the remaining caller-supplied arguments.

Argument spread is supported at call sites:

```js
values: [10, 20, 30]
f(...values)
```

which invokes `f` with the elements of `values` as individual positional arguments, preserving their order.

These facilities are intended to make invocation forwarding and dynamic arity ordinary language operations:

```js
forward: (...args) => {
    target(...args)
}
```

Protocols analogous to Smalltalk block invocation helpers may therefore be implemented using ordinary callable objects and spread, for example conceptually:

```js
f.value(10, 20)
f.values([10, 20])
```

where such protocol methods delegate to normal invocation. No overload resolution by argument type is introduced by these facilities.


## Polymorphic Invocation and Object Construction

Parentheses are a polymorphic invocation syntax. Invoking an object uses that object's call protocol; callability is therefore behavior, not a special static category reserved exclusively for closures.

Conceptually:

```js
receiver(a, b)
```

performs the receiver's ordinary invocation protocol with the evaluated arguments.

`Closure` provides the standard executable implementation of that protocol. `Object` provides the standard object-construction implementation inherited by ordinary prototypes.

The default construction behavior is conceptually:

```text
Object.call(...args):
    instance = createObject(parent = this)
    send(instance, "init", args)
    return instance
```

Thus:

```js
Point(10, 20)
```

creates a fresh object whose immutable delegation parent is `Point`, sends `init(10, 20)` to the fresh object, and returns that fresh object.

`init` is an ordinary overridable message. Its return value is ignored by the default construction protocol; the construction expression returns the created instance.

If initialization signals an error, construction fails and the construction expression produces no successful instance result.

`Object` provides a default `init` behavior that accepts zero arguments and signals an argument-count error when arguments are supplied. Therefore:

```js
Thing()
```

works for a prototype that does not define its own `init`, while:

```js
Thing(1, 2)
```

requires compatible initialization behavior.

Alternative constructors are ordinary named messages rather than overloads:

```js
Point.fromPolar(radius, angle)
Point.fromJson(data)
Point.origin()
```

Such messages may internally invoke the normal construction protocol.

Object-literal/prototype syntax remains distinct:

```js
Point {
    x: 10
    y: 20
}
```

directly creates an object whose parent is `Point` and evaluates the object body as slot definitions. It does not implicitly send `init`.

Core v0.1 does not define a combined object-construction form in which `Point(args) { ... }` means "construct and then evaluate this object body".

When the token sequence `Point(args) { ... }` is otherwise valid under trailing-closure syntax, the braces denote a trailing closure passed to the invocation. They do not become an object-construction body.

## Contextual Meaning of `...`

`...` is structural syntax whose meaning is determined by syntactic context:

```text
parameter list   -> capture remaining arguments
argument list    -> spread a collection into arguments
object body      -> compose local slots from an object
```

It is not a general standalone expression operator and has no universal runtime meaning outside those contexts.


## Resource Cleanup and `ensure`

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
- error signaling and unwind.

If `cleanup` completes normally, the original completion or control transfer continues unchanged.

If `cleanup` signals an error, that new error becomes the active control transfer. Any previously active return or error unwind is abandoned in favor of the newly signaled error.

A future resumable-condition mechanism is compatible with this rule: a condition that is handled and resumed without leaving the protected scope does not trigger cleanup merely because it was signaled.

Higher-level resource protocols such as `use`, `withOpen`, or similar APIs may be implemented on top of this guarantee using ordinary messages and closures.

Garbage-collector finalization is not a resource-management guarantee and must not be relied upon for deterministic release of external resources.


## Numeric Equality Across Families

Numeric semantic equality compares mathematical numeric value across numeric families.

Examples:

```js
1 == 1.0               // true
UInt8(1) == 1          // true
Int32(1) == UInt32(1)  // true
```

This does **not** imply conversion of either operand into the other's numeric family. Equality must not introduce rounding merely to perform a comparison.

For numeric values, cross-family equality is symmetric:

```text
a == b  iff  b == a
```

when both operations complete normally.

Implementations must compare exactly enough to avoid false equality caused by lossy conversion. For example, an arbitrary-precision Integer must not be rounded to Float merely to compare it with a Float.

Semantic identity remains stricter:

```js
1 === 1.0               // false
UInt8(1) === 1          // false
Int32(1) === UInt32(1)  // false
```

For numeric values, `===` includes the semantic numeric family in identity. Equal mathematical value across distinct numeric families does not imply identity.

This yields the general distinction:

```text
==   compares numeric value
===  compares numeric value plus semantic numeric family
```

Special floating-point cases such as NaN and signed zero are specified separately.

## Float Special Values and Identity

`NaN` is a special semantic value of the `Float` family, not a language-level singleton object analogous to `null`.

Different IEEE-754 NaN bit patterns or payloads do not create distinct language-level semantic values unless an explicit representation-inspection protocol is used.

Consequently:

```js
a: someNaNProducingOperation()
b: someOtherNaNProducingOperation()

a == b    // false
a === b   // true
```

Numeric equality follows IEEE-style NaN behavior: a NaN is not numerically equal to any value, including another NaN.

Numeric semantic identity treats all NaN values of the same semantic Float family as the same semantic special value, independent of runtime payload, sign bit, allocation, boxing, or host representation.

`NaN` need not be a reserved literal or a global singleton binding. Standard-library protocol may expose an ordinary way to obtain it, for example:

```js
Float.nan
```

Similarly, infinities are special Float values rather than new language literals. A standard library may expose ordinary protocol such as:

```js
Float.infinity
Float.negativeInfinity
```

`null` remains fundamentally different:

```text
null
    canonical singleton language object

NaN
    special semantic value of Float
    potentially many runtime representations
```

## Float Signed Zero Semantics

IEEE-style signed zero is semantically observable in the `Float` family.

Numeric equality ignores the distinction:

```js
0.0 == -0.0    // true
```

Numeric semantic identity preserves it:

```js
0.0 === -0.0   // false
```

The sign of zero is therefore part of Float semantic identity even though it does not affect numeric equality.

This distinction matters because signed zero can influence later floating-point behavior, for example the sign of infinity produced by reciprocal-style operations:

```js
1.0 / 0.0     // +Infinity
1.0 / -0.0    // -Infinity
```

The general rule is:

```text
==   compares numeric value and ignores the signed-zero distinction
===  preserves the signed-zero distinction within Float
```

This rule is semantic and must not depend on boxing, allocation, host references, or implementation-specific representation.

## Parameter Name Uniqueness

Parameter names within a single closure parameter list must be unique.

This applies to required parameters, parameters with defaults, and the rest parameter:

```js
(a, a) => { ... }             // invalid
(a, a = 10) => { ... }        // invalid
(a, ...a) => { ... }          // invalid
(a, b = 10, ...rest) => { }   // valid
```

Duplicate parameter names are rejected during parsing or static validation before execution begins.

This is consistent with activation binding: parameters become local slots in the invocation context, and creating the same local slot twice is not permitted.

## Numeric Model

Core v0.1 distinguishes numeric behavior through prototype delegation rather than static types or overload resolution.

The conceptual hierarchy includes:

```text
Number
├── Integer
│   ├── fixed-width integer prototypes such as UInt8, Int16, UInt32, ...
│   └── implementation-specific exact-integer representations
└── Float
```

`Integer` denotes mathematically exact integers. Integer semantics are arbitrary precision: ordinary integer arithmetic does not expose machine overflow. An implementation may optimize small values using machine integers or tagged values and transparently promote to an arbitrary-precision representation when necessary. Whether objects such as `SmallInteger` or `BigInteger` are exposed as standard prototypes remains an implementation/library design choice unless otherwise specified.

Integer-only protocols may include bit manipulation operations such as shifts, masks, bitwise conjunction/disjunction/XOR, and bit access. `Float` need not implement those messages.

Fixed-width integer prototypes such as `UInt8`, `Int8`, `UInt16`, `Int16`, `UInt32`, `Int32`, `UInt64`, and `Int64` have width and signedness as part of their semantics. They delegate through `Integer` and may specialize arithmetic and bit-oriented behavior.

For fixed-width integers:

- conversion of an out-of-range value signals an error;
- ordinary arithmetic does not silently wrap;
- arithmetic that cannot be represented in the fixed-width result signals an error;
- explicit wrapping operations may be provided as separate messages.

Literal radix is syntactic only. For example:

```js
255
0xFF
0b11111111
0o377
```

denote the same integer value. Digit separators such as `_` may be accepted by the grammar without changing the value.

`/` denotes ordinary numeric division and may produce a `Float` from integer operands:

```js
5 / 2    // 2.5
```

Integer quotient/remainder behavior is exposed explicitly through integer protocol messages such as `div` and `mod`.

Conversions between numeric families are explicit when representation or information may change. Operations such as `floor`, `truncate`, and `round` express the intended conversion behavior rather than relying on silent coercion.

`Float` follows IEEE-754-style floating-point behavior, including NaN and infinities where supported by the chosen floating representation.

Endianness is not a property of a numeric value. It belongs to binary encoding and decoding. The same numeric value may be represented as bytes using objects/protocol values such as `BigEndian` and `LittleEndian`.

For example:

```js
value.toBytes(BigEndian)
UInt32.fromBytes(bytes, LittleEndian)
```

or equivalent buffer-oriented protocols.

This follows the general rule that semantic values are distinct from their external binary representation.


## Text, Bytes, and Character Encodings

Core v0.1 separates abstract text from its external binary representation.

`String` denotes Unicode text as a semantic value. A `String` is not semantically UTF-8, UTF-16, Latin-1, or any other particular byte encoding, even if an implementation chooses one of those representations internally.

`Bytes` denotes a raw sequence of bytes. Byte values carry no implicit text interpretation.

Character encodings are represented independently through encoding objects or protocols, conceptually including values such as:

```text
UTF8
UTF16LE
UTF16BE
Latin1
```

Conversion between text and bytes is explicit:

```js
bytes.decode(UTF8)
text.encode(UTF8)
```

Decoding interprets a byte sequence using the selected encoding and produces a `String`. Encoding converts a `String` into a `Bytes` value using the selected encoding.

Malformed-input handling, replacement policy, strict versus permissive decoding, and the exact standard encoding catalogue remain library/protocol design decisions unless later promoted into Core semantics.

This follows the same general principle used for numeric endianness:

```text
semantic value ≠ external binary representation
```

Therefore:

```text
String ≠ UTF-8 bytes
UInt32 ≠ little-endian bytes
```

An implementation may use any internal String representation provided observable language semantics remain unchanged.


## String Indexing, Mutability, and Encoded Representations

`String` is an immutable Unicode text value.

Core String indexing and size operate on Unicode grapheme clusters rather than bytes, encoding code units, or raw Unicode code points:

```js
text.size
text[0]
```

conceptually correspond to grapheme-count and grapheme-at-index operations.

This keeps ordinary text operations aligned with user-perceived characters while preserving explicit lower-level access through separate protocols such as:

```js
text.graphemes()
text.codePoints()
text.encode(UTF8)
```

Because `String` is immutable, operations that conceptually modify text produce a new `String`:

```js
text.uppercase()
text.replace("a", "b")
text + other
```

Efficient incremental text construction belongs to separate mutable objects such as `StringBuilder` or equivalent buffer-oriented abstractions.

`Bytes` is a mutable raw byte sequence by default. Indexed access therefore naturally follows the existing protocol:

```js
bytes[i]          // bytes.at(i)
bytes[i] = value  // bytes.atPut(i, value)
```

Encoded textual representations such as UTF-8 data, UTF-16 data, C strings, memory-backed strings, or similar objects may be first-class values with their own protocols.

Their mutability is not globally fixed by Core. An encoded representation may expose `atPut` if mutation is meaningful and supported, or omit it if the representation is immutable or read-only.

Therefore mutability is expressed behaviorally through supported messages rather than through a universal collection mutability flag.

Examples of possible first-class representations include:

```text
UTF8EncodedString
UTF16EncodedString
CString
MappedText
```

These names are illustrative; Core v0.1 does not require this exact library taxonomy.


## Maps, Hashing, and Key Equality

`Map` is an ordinary keyed collection whose indexed syntax uses the existing `at` / `atPut` protocol:

```js
map[key]
map[key] = value
```

A normal `Map` uses semantic equality and hashing:

```text
key equality  -> ==
key hash      -> hash
```

Map relies on the language-wide Boolean-result contract of `==`: equality returns canonical `true` or `false`, or signals an error. Map introduces no separate truthiness or Map-specific interpretation rule.

The required contract is:

```text
a == b  =>  a.hash == b.hash
```

While an object is stored as a key in a `Map`, the observable behavior relevant to its `hash` and `==` operations must remain stable.

Core does not prohibit mutable objects from being keys. A mutable object is valid as a key when the state participating in its hash/equality remains stable for as long as it is stored.

If this contract is violated, subsequent map operations involving that key are not guaranteed to behave as if the key had remained stable. Observable consequences may include failure to find an existing entry, apparently duplicate equal keys, or surprising contains/remove results. Such misuse must not imply host-language memory unsafety or runtime corruption.

Implementations may optionally diagnose some unstable-key cases in debugging modes, but such detection is not required by Core semantics.

For identity-based keyed storage, the standard model includes `IdentityMap`:

```text
IdentityMap equality -> ===
IdentityMap hash     -> identityHash
```

Identity equality and identity hashing are stable for the lifetime of an object.

Missing-key lookup does not silently return `null`, because `null` is a valid language value. A direct lookup such as:

```js
map[key]
```

signals an absence/key error when the key is not present.

Alternative lookup behavior is explicit through ordinary messages, for example:

```js
map.containsKey(key)
map.atIfAbsent(key, block)
```

The exact convenience protocol may grow in the standard library without changing the core lookup rule.

`Map` preserves insertion order for iteration. Updating an existing key's value does not create a new insertion position unless a library protocol explicitly specifies otherwise.

The ordinary `hash` operation is not required to be stable across separate process executions. Implementations may use per-process randomization or salting for security. Persistent or interoperable hashing must use a separate explicit protocol or algorithm.

`IdentityMap` follows the same insertion-order rule unless a more specialized collection explicitly documents otherwise.
