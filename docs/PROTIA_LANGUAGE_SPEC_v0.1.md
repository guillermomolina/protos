# Core Language Specification v0.1

Language version: 0.1  
Document revision: 6  
Status: Draft  
Last updated: 2026-08-30

## 1. Principles

The language is an object-oriented language based exclusively on **prototypes and delegation**.

There are no classes.

Every observable value is an object. This includes numbers, strings, booleans, closures, errors, futures, execution contexts, and `null`.

An implementation may internally represent some objects using immediate values, tagged pointers, specialized memory layouts, or other optimizations, provided that these optimizations are not observable through the language semantics.

The language has a uniform execution model. There is no fundamental semantic distinction between "global code", "local variables", and "properties". Execution always takes place within contexts, and contexts are themselves objects.

The language favors objects and messages over keywords and special syntactic constructs.

## 2. Objects

An object contains slots. A slot associates a name with an object.

Every object has at most **one delegation parent**.

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

creates an object using the standard root prototype as its parent.

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

The delegation parent is fixed at object creation and cannot subsequently be changed.

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

Protia has no special semantic category of global variables.

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

Protia evaluates strict subexpressions from left to right. The receiver or assignment target is evaluated before arguments or the right-hand side, and arguments are evaluated left to right. Parent expressions are evaluated before object bodies. Standard binary operators evaluate their left operand before their right operand.

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

A closure installed as an object slot acts as a method.

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

Reading a method through a receiver produces a bound closure.

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

Protia follows the Smalltalk/Squeak **home activation** model for non-local return.

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

`;` is an explicit horizontal expression separator.

Likewise:

```js
point: { x: 10, y: 20 }
```

may be written:

```js
point: {
    x: 10
    y: 20
}
```

A comma primarily acts as a horizontal separator.

Incomplete expressions continue across line breaks:

```js
result: 1 +
    2 +
    3

result: object
    .foo()
    .bar()
```

## 20. Composition and Traits

The core supports only one delegation parent.

Horizontal reuse is performed through slot composition.

```js
duck: animal {
    ...flyable
    ...swimmable
}
```

Composition does not introduce additional parents.

Composed slots structurally become slots of the resulting object.

Conflicting slots are errors unless explicitly resolved by a local slot declaration.

This avoids method resolution orders, diamond inheritance, and multiple `super` chains.

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

`==` represents semantic equality and may be customized through object behavior. For built-in immutable value objects, `==` and `===` may naturally produce the same result, but they remain different operations: `==` is behavioral and customizable, while `===` is a non-overridable identity primitive.

Identity is never defined by comparing hash codes. A runtime may derive or cache hashes from identity where appropriate, but hash collisions cannot make distinct identity-bearing objects identical.

## 22. Open Objects

Objects are initially open and mutable.

An open object permits slot creation and modification subject to the normal rules.

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

There are no classes.

Every object has at most one delegation parent.

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
