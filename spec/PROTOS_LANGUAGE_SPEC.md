# Core Language Specification v0.1

Language version: 0.1  
Document revision: 225
Status: Draft  
Last updated: 2026-09-04
Normative I/O-domain semantics are defined in `PROTOS_IO_MODEL.md`.

Normative Actor/Future/concurrency-domain semantics are also defined by the
CLOSED sections of `PROTOS_CONCURRENCY_MODEL.md`; unresolved sections and
explicitly open subtopics in that mixed document are non-normative.
## Language Name

The language defined by this specification is named **Protos**.

## 1. Principles

The language is an object-oriented language based exclusively on **prototypes and delegation**.

There are no classes.

Every observable value is an object. This includes numbers, strings, booleans, closures, errors, futures, execution contexts, and `null`.

An implementation may internally represent some objects using immediate values, tagged pointers, specialized memory layouts, or other optimizations, provided that these optimizations are not observable through the language semantics.

The language has a uniform execution model. There is no fundamental semantic distinction between "global code", "local variables", and "properties". Execution always takes place within contexts, and contexts are themselves objects.

The language favors objects and messages over keywords and special syntactic constructs.

## 1.1 Identifier Syntax and Reserved Words

Protos identifiers are Unicode-aware and case-sensitive.

An identifier begins with `_` or a Unicode character with the `XID_Start` property. Subsequent characters must have the Unicode `XID_Continue` property.

Every identifier must be in Unicode NFC normalization form. Implementations must reject non-NFC identifiers rather than silently normalizing them. Identifier normalization applies to identifier spelling only; it does not imply normalization of `String` values.

The Core v0.1 reserved-word set is exactly:

```text
this
context
args
super
true
false
null
```

Reserved-word recognition is case-sensitive. Reserved words cannot be used as ordinary identifiers where the grammar expects an identifier.

A reserved-word spelling is nevertheless a valid contextual member name in the structural position immediately following a member-access `.`. In that position it denotes an ordinary slot or message name and does not retain its expression-level intrinsic, literal, or special meaning: `obj.true` denotes the member named `"true"`, and `obj.true()` invokes the executable stored in that member. This applies to super message sends as well: `super.true()`, `super.this()`, and `super.super()` are valid super message sends whose message names are respectively `true`, `this`, and `super`. Reserved words remain invalid everywhere the grammar expects `identifier`, including parameter names, rest-parameter names, bare assignment targets, and bare slot-creation targets.

Names from the standard prelude such as `Object`, `Future`, `Number`, `String`, `Map`, `IdentityMap`, or `Context` are not reserved words.

## 1.2 Dynamic Typing

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

Like every execution context, a `moduleContext` delegates through the standard `Context` prototype to `Object` (see Execution Context).

Each module has its own module context. Modules do not implicitly share mutable global state.

Cross-module visibility is established explicitly: a module obtains another module's instance through `import(specifier)` and accesses its top-level bindings as slots of that module instance through ordinary member lookup. Core v0.1 introduces no dedicated import declaration syntax and no export declaration syntax or separate export mechanism. Module instance identity, caching, initialization, cycle, and failure semantics are defined in the section Module Loading, Identity, and Cycles; only host-specific module-specifier resolution remains outside Core v0.1.

Universal language facilities such as core prototypes and standard behavior may be supplied through a shared prelude or root lexical environment. Such facilities remain part of the ordinary context and lookup model rather than introducing a global-variable namespace.

The standard prelude is shared but **frozen**. Its slots may be read through ordinary lexical lookup, but unqualified `=` must never mutate a prelude slot. Attempting to modify a binding that resolves only to the frozen prelude signals an assignment error. A module that wants to shadow a prelude binding creates a new local slot with `:`.

```js
Object             // reads the prelude binding
Object = myObject   // ERROR: the prelude binding is frozen
Object: myObject    // OK: creates a module-local binding that shadows it
```

Freezing is shallow, so freezing the prelude is not by itself sufficient to make arbitrary objects referenced by its slots safe to share between Actors. The governing invariant is therefore:

> Any Protos object physically shared between Actors through the standard prelude must be semantically immutable for the duration of that sharing. Mutable Protos state reachable through standard facilities must be Actor-local.

The implementation may physically share immutable implementation artifacts — parsed syntax, bytecode, machine code, immutable metadata, and immutable constant data — where the sharing is semantically unobservable. Mutable standard-library or runtime state belongs to the Actor that uses it. This rule does not change the existing shallow-freeze semantics, does not introduce deep freeze, does not weaken Actor isolation, and does not require implementations to duplicate immutable data unnecessarily.

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

In an expression-bodied closure, the single body expression is the final expression and supplies that value, exactly as in the equivalent braced form: `square: (x) => x * x`.

Early return is expressed using:

```js
^value
```

The language follows the Smalltalk/Squeak **home activation** model for non-local return.

A top-level function or method invocation establishes a return home. Closures created lexically during that invocation capture that same home rather than creating a new one merely because they are called.

```js
find: (items) => {
    items.each((item) => {
        item.valid.ifTrue() {
            ^item
        }
    })

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

## 18. Trailing Closures

The parentheses of a call always contain call arguments. A call may be followed by one trailing closure, which is appended as the final argument of the invocation.

A trailing closure is always parameterless:

```js
transaction(options) {
    work()
}
```

is exactly equivalent to:

```js
transaction(
    options,
    () => {
        work()
    }
)
```

A trailing closure never has its own parameter list. Core v0.1 provides no parameterized trailing-closure syntax: the former form `foo(args...) (params...) { body }` is not part of Core v0.1, and `items.each() (item) { print(item) }` is not trailing-closure syntax. Such forms do not become two adjacent same-line expressions merely because parameterized trailing closures were removed: whitespace alone does not separate expressions, and no implicit adjacency-based expression separation exists.

A closure that requires parameters is passed explicitly as an ordinary closure expression in ordinary call-argument position:

```js
items.each((item) => {
    print(item)
})
```

Likewise, instead of a parameterized trailing closure after `collection.reduce(initial)`, write an explicit closure argument according to ordinary call syntax:

```js
collection.reduce(initial, (acc, item) => {
    ...
})
```

The exact position of the closure among a particular API's arguments is defined by that API. The trailing-closure sugar only appends a parameterless closure as the final argument.

The call parentheses are never reinterpreted as the parameter list of a trailing closure. Therefore:

```js
items.each(item) {
    print(item)
}
```

means:

```js
items.each(
    item,
    () => {
        print(item)
    }
)
```

The `item` inside the call parentheses is an ordinary explicit call argument. It is not a parameter declaration for the trailing closure.

A parameter list exists only where ordinary closure syntax requires it, before `=>`. `(x)` is always an ordinary parenthesized expression, and `(x) => { body }` is always an ordinary closure expression; there is no third interpretation of `(x)` as the parameter declaration of a trailing closure. This resolves issue B6 structurally: the parser needs no special lookahead to distinguish `(x)` from a trailing-closure parameter list, no parameter list is inferred from a parenthesized expression, and no semantic/type-based interpretation decides whether parentheses contain closure parameters. When a Closure has exactly one simple parameter, its parentheses may be omitted (see Closures); the result, such as `items.each(item => print(item))`, is an ordinary explicit Closure in ordinary call-argument position and never a trailing closure.

A trailing closure introduces no new runtime value kind: it is syntactic sugar for an ordinary Closure appended as the final call argument. Trailing-closure syntax does not alter closure semantics.

A trailing closure is attached only when no logical `NEWLINE` token intervenes between the completed call and the closure body. The call is complete when its `argument-list` ends; a `NEWLINE` token at that point acts as an expression separator under the complete-expression newline rule (see Separators, Line Breaks, and Comments). Therefore:

```js
foo() {
    body
}
```

is a call with a parameterless trailing closure, while:

```js
foo()
{
    body
}
```

does not attach the braces to `foo()` as a trailing closure. `foo()` is syntactically complete, so the logical `NEWLINE` after it separates expressions. `{` is not a complete-before-newline continuation exception: the only such exception remains the leading structural member-access `.` rule, and it does not generalize to `{`. What the separated `{ ... }` may mean, if anything, is governed by the ordinary grammar independently; the normative claim here is only that it is not attached as a trailing closure to the preceding call. This closes issue B7.

Blank lines and semicolons do not attach a trailing closure: repeated separating `NEWLINE` tokens have the same effect as one separating `NEWLINE`, and `;` is an expression separator, so `foo(); { body }` is not a trailing closure on `foo()`.

Indentation plays no role in the decision: the rule concerns logical `NEWLINE` tokens, not physical source formatting. The two forms:

```js
foo()
{
    body
}
```

and:

```js
foo()
    {
        body
    }
```

are equivalent with respect to the newline rule; both contain a separating logical `NEWLINE` after the completed call, so neither attaches the braces as a trailing closure. Horizontal whitespace between the completed call and the closure body is permitted: `foo()    { body }` remains valid trailing-closure syntax.

Comments follow the existing lexical rules. A block comment behaves as whitespace and consumes any logical newlines inside it without producing `NEWLINE` tokens, so `foo() /* comment */ { body }` and:

```js
foo() /*
    comment
*/ {
    body
}
```

both attach. A line comment does not consume its terminating logical newline: that newline is tokenized normally, so `foo() // comment` followed by `{ body }` on the next source line does not attach. No special comment-sensitive trailing-closure rule exists; the result follows entirely from tokenization.

A trailing closure is therefore permitted only when the parser sees the closure body as part of the same continuing token sequence after the completed call suffix, with no intervening `NEWLINE` token. A valid trailing closure remains a parameterless closure appended as the final call argument; this revision does not restore parameterized trailing closures.

## 19. Separators, Line Breaks, and Comments

There is no Automatic Semicolon Insertion.

Horizontal whitespace consists of exactly two code points: `SPACE` (U+0020) and `CHARACTER TABULATION` (U+0009, TAB). No other code point is horizontal whitespace. This set is closed: it does not depend on Unicode whitespace properties or on any host-language, host-library, or host-operating-system whitespace classification. Outside lexical constructs that consume their own contents (String literals and comments), SPACE and TAB are insignificant horizontal whitespace: they separate tokens where separation is required and otherwise produce no parser token.

Horizontal whitespace is distinct from logical source newlines, which are a separate lexical category and are not horizontal whitespace.

A logical source newline is exactly one of `LF` (U+000A), `CR` (U+000D), or `CRLF` (U+000D U+000A). `CRLF` is consumed atomically as one logical source newline, never as two. Source files may freely mix `LF`, `CR`, and `CRLF` logical newlines; mixed line-ending styles are not lexical errors.

Each logical source newline that is not consumed by another lexical construct produces exactly one `NEWLINE` token for the parser. In particular, logical source newlines inside a block comment are consumed as part of the comment and produce no `NEWLINE` token. Newline handling does not depend on the host operating system, editor settings, Git line-ending conversion, or any host line-separator convention.

No other Unicode code point is implicitly ignored as whitespace merely because Unicode, Java, an operating system, or another host API classifies it as whitespace or space. In particular, the following are not Core v0.1 whitespace: U+000B VERTICAL TAB, U+000C FORM FEED, U+0085 NEXT LINE, U+00A0 NO-BREAK SPACE, U+1680 OGHAM SPACE MARK, U+2000..U+200A Unicode space characters, U+2028 LINE SEPARATOR, U+2029 PARAGRAPH SEPARATOR, U+202F NARROW NO-BREAK SPACE, U+205F MEDIUM MATHEMATICAL SPACE, U+3000 IDEOGRAPHIC SPACE, and U+FEFF ZERO WIDTH NO-BREAK SPACE. This list is illustrative of important exclusions, not an alternative open-ended definition. U+2028 LINE SEPARATOR and U+2029 PARAGRAPH SEPARATOR are neither horizontal whitespace nor logical source newlines.

A source code point that is neither part of a valid lexical token, nor SPACE or TAB horizontal whitespace, nor a logical source newline, nor consumed inside a lexical construct such as a String or comment is a lexical error. The lexer must not silently discard unknown Unicode whitespace-like or format characters.

Characters such as NBSP, Unicode space characters, U+2028, U+2029, and U+FEFF may occur as ordinary String content where the String literal rules permit them. Their exclusion from lexical whitespace applies outside String content.

Core v0.1 defines two comment forms:

- `//` starts a line comment and continues until the next logical source newline or end of file. The terminating logical source newline is not consumed as part of the comment; it remains available for ordinary newline tokenization.
- `/*` starts a block comment and `*/` ends it. A block comment is one lexical construct: it consumes all source characters from its opening `/*` through its matching closing `*/`, including logical source newlines. The first `*/` after the opening `/*` terminates the comment.

Logical source newlines (`LF`, `CR`, or `CRLF`) inside a block comment are consumed as part of the comment and do not produce `NEWLINE` tokens. A `CRLF` inside a block comment remains one logical source newline for source-position and logical-line accounting, but no `NEWLINE` token is emitted for it. Newlines inside a `/* ... */` comment cannot themselves separate expressions.

Block comments do not nest in Core v0.1. An unterminated block comment is a lexical error. Comment delimiters inside String literals have no special meaning.

A block comment has the token-separation effect of insignificant whitespace regardless of whether it contains logical source newlines. The two forms

```js
a() /* comment */ b()
```

and

```js
a() /*
    comment
*/ b()
```

have the same token-separation effect: the internal logical newlines do not become expression separators. Newlines outside a block comment remain governed by the normal logical-newline rules and are consumed or act as expression separators according to the parser-level newline-continuation rules below.

Comments produce no parser token and no language-level value. They are lexical constructs with whitespace-like token-separation behavior; they do not add code points to the horizontal-whitespace set, which remains exactly `SPACE` and `TAB`.

`#` is not a comment delimiter. Core v0.1 defines no special documentation-comment syntax.

Expression separation across line breaks is a parser-level rule: a logical `NEWLINE` token normally separates expressions when the expression before it may legally end at that point, and it does not separate expressions when the syntactic construct before it is necessarily incomplete and the parser must consume more input to complete that construct. The parser decides whether an existing logical `NEWLINE` token acts as an expression separator or is consumed as continuation; it never inserts a separator that is absent from the token stream. There is no Automatic Semicolon Insertion.

Three cases are distinguished:

1. **Syntactically incomplete continuation.** When the syntax before a newline necessarily requires further input — for example, an expression ending after a binary operator, after `:` or `=`, or inside an open call, parenthesized, or indexed construct — the newline is insignificant for expression separation.
2. **Complete-expression newline separation.** When the expression before a newline is syntactically complete, the newline normally acts as an expression separator. A binary or custom symbolic operator at the beginning of the following line does not cause the preceding complete expression to continue.
3. **Explicit leading-dot continuation.** A logical newline immediately before a leading structural member-access `.` is consumed as continuation of the preceding postfix/member expression. This is the only complete-before-newline continuation exception: it does not generalize to binary operators, custom symbolic operators, `(`, `[`, `{`, `=>`, or any other token merely because that token could somehow be attached to the expression on the previous line.

The rule is based on grammatical incompleteness, not on a hard-coded list of token spellings. Indentation, visual alignment, tab width, and source line-ending spelling have no syntactic significance for newline continuation, and the rule has no runtime dependency.

An explicit `;` is the inline expression separator: it separates two expressions written on the same logical source line. It is a separator, not a terminator: it must have an expression immediately before it and an expression after it on the same logical source line, with no `NEWLINE` token between the `;` and either expression. Leading, trailing, and consecutive semicolons are syntax errors, and a `;` does not acquire terminator meaning merely because a newline follows it. There is no semicolon continuation analogous to newline continuation; a semicolon cannot be ignored merely because formatting or the next token suggests continuation.

This section does not classify `{` or `(` as leading-token continuation exceptions. A completed call is syntactically complete, so a logical `NEWLINE` after it acts as a separator under the complete-expression newline rule above, and the braces of a following `{ ... }` do not attach to the completed call as a trailing closure (see Trailing Closures, where issue B7 is closed). Separator multiplicity and blank-line grammar (issue B4) are defined below: a run of separating `NEWLINE` tokens has the effect of a single separating `NEWLINE`, blank lines are permitted and create no empty expressions, and this multiplicity rule does not create continuation behavior this section does not permit.

```js
foo()
bar()
baz()
```

contains the same three expressions as:

```js
foo(); bar(); baz()
```

`;` is the inline expression separator: it separates expressions written on the same logical source line. A logical `NEWLINE` is the ordinary separator between expressions written on different logical source lines. The two mechanisms are distinct syntactic roles, not interchangeable spellings of one generic separator: `;` requires an expression on both sides of it on the same logical source line, while a separating `NEWLINE` ends the current source line and the next expression begins on a later line. There is no requirement to write `;` at the end of a source line, and a `;` at the end of a line is a syntax error, not an optional terminator. A comma is **not** an expression separator.

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

Both separation mechanisms produce the same ordered expressions. These are valid:

```js
a: 1; b: 2

a: 1; b: 2; c: 3

a: 1
b: 2

a: 1


b: 2

a: 1; b: 2
c: 3

a: 1; b: 2


c: 3
```

These are syntax errors:

```js
; a: 1

a: 1;

a: 1;; b: 2

a: 1; ; b: 2

a: 1;
b: 2

a: 1;

b: 2
```

The first four fail because a `;` lacks an expression before it or after it on the same logical source line. The last two fail because `;` is same-line only: the `NEWLINE` ends the current line, and the semicolon cannot take the following line's expression as its right-hand expression. A `;` never acquires terminator meaning merely because a newline follows it.

A separating logical `NEWLINE` ends the current line's expression sequence. Consecutive separating logical `NEWLINE` tokens have the same effect as one: blank lines are valid between expressions and create no empty expression, no omitted expression, and no `null` value. Blank lines are likewise permitted at the beginning or end of an expression sequence, such as around the expressions of a block or program, provided the surrounding grammar otherwise permits the sequence:

```js
{

    a: 1
    b: 2

}
```

contains exactly two expressions. Repeated separating `NEWLINE` tokens produce no semantic AST nodes and no runtime behavior.

Whether a `NEWLINE` token acts as separation at all is decided by the newline-continuation rules above: a `NEWLINE` consumed as continuation inside a necessarily-incomplete construct, or immediately before a leading structural `.`, never acts as separation. The multiplicity rule applies once a `NEWLINE` is functioning as line separation; blank lines do not create continuation behavior those rules do not permit.

Comment forms do not change the same-line requirement of `;`. A `//` line comment does not consume its terminating logical source newline, so `a: 1; // comment` followed by `b: 2` on the next line still leaves a `NEWLINE` token between the `;` and `b` and is invalid. A `/* ... */` block comment consumes embedded logical source newlines and behaves whitespace-like, so it does not itself supply a separating `NEWLINE`.

The source-level separator choice does not change what the expressions are: `a: 1; b: 2` and `a: 1` followed by `b: 2` on the next line contain the same ordered expressions, and both forms produce the same `Sequence` structure in the semantic AST. Neither `;` nor a separating `NEWLINE` becomes an AST node, and evaluation remains left-to-right.

`,` is reserved for list-like syntax such as argument lists and parameter lists, and may also be used by future collection literal syntax. It never sequences arbitrary expressions.

Within a comma-separated list, `,` is the only separator between elements. A comma is strictly a separator between two list elements: it is not a terminator and does not represent an empty or omitted element. A comma must therefore have a list element on both sides within the same list, and a trailing comma before the closing delimiter is a syntax error.

This applies to call arguments and closure parameters, including spread arguments and rest parameters. A logical `NEWLINE` is not an element separator and does not substitute for a required comma: multiline formatting works through the newline-continuation rules above, where newlines inside a necessarily-incomplete construct are layout, not through newline separation.

These are valid:

```js
foo()

foo(a)

foo(a, b)

foo(
    a,
    b
)

foo(

    a,

    b

)

(a, b) => {
    ...
}

(
    a,
    b
) => {
    ...
}

(

    a,

    b

) => {
    ...
}
```

These are syntax errors, either because a required comma is missing between two elements or because a comma precedes the closing delimiter with no element after it:

```js
foo(a b)

foo(
    a
    b
)

foo(a,)

foo(a, b,)

foo(
    a,
    b,
)

foo(
    a,
    b,

)

(a b) => {
    ...
}

(a,) => {
    ...
}
```

The newlines between the final element and the closing delimiter — a single newline or a run of blank lines — are layout inside the open construct: they are not a separator and not an empty element, and they do not make a trailing comma legal. Indexing contains one expression rather than a comma-separated list, so no multi-index comma syntax is introduced by this rule.

A comma never becomes a general expression operator or an expression-sequence separator; it remains list syntax only. Separator multiplicity and blank-line grammar are resolved by this revision (issue B4): a run of separating logical newlines acts as one separator, blank lines create no empty expressions, and none of this affects the comma-only list-element rule.

Incomplete expressions continue across line breaks. These are each one expression:

```js
result: 1 +
    2 +
    3

x:
    value

x =
    value

foo(
    a,
    b
)

array[
    index
]

(
    a +
    b
)
```

The first example is equivalent to `result: 1 + 2 + 3`, the second to `x: value`, and the third to `x = value`; the last three continue because their enclosing delimiters must be closed. In all of these, the expression before the newline is necessarily incomplete, so the newline is insignificant for expression separation.

When the expression before a newline is syntactically complete, the newline normally separates expressions. A binary operator at the beginning of the following line does not retroactively continue the preceding expression:

```js
a
+ b

a
&& b

a
== b
```

In each of these, the newline separates the expression `a`, and the following line then begins with an operator where the grammar requires an operand. There is no general leading-operator continuation rule.

The one explicit complete-before-newline continuation exception is a leading structural `.`, which continues the preceding postfix/member expression:

```js
result: object
    .foo()
    .bar()

a
    .foo()
```

The first example is one expression, equivalent to `object.foo().bar()`; the second is equivalent to `a.foo()`. Indentation is irrelevant to all of these rules.

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

Some Core values have **value identity**: their semantic value determines
identity rather than a particular allocation. The Core v0.1 value-identity set
is closed and consists exactly of:

- numeric values in the `Number` family;
- `String` values;
- the canonical Boolean values `true` and `false`;
- the canonical `null` value.

No implementation, host platform, optimization, standard-library extension, or
ordinary Protos program may add another Core v0.1 value-identity category.
Future language versions may extend this set only by an explicit normative
language change.

```js
1 === 1                    // true
"hello" === "hello"        // true
("hel" + "lo") === "hello" // true
true === true              // true
null === null              // true
```

Being immutable, closed, frozen, interned, canonicalized, structurally equal,
or backed by the same host representation does not by itself grant value
identity. Every object outside the closed set above has individual object
identity.

In particular, ordinary objects, closed or frozen ordinary objects, Closures,
Arrays, Maps, Futures, errors, execution contexts, module instances, and
standard prototype objects remain identity-bearing objects unless a normative
rule explicitly places the value itself in one of the closed value-identity
families above.

Delegation does not transfer value identity. An ordinary object whose parent is
a Number, String, Boolean, or `null` value remains an ordinary identity-bearing
object, as already required by the delegation rules.

`===` is non-overridable, so user code cannot opt an object into value identity
by defining equality behavior. `==` remains the customizable protocol for
semantic equality.


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

### Custom Operator Lexical Alphabet

Custom symbolic binary operators are formed from the following operator characters:

```text
! $ % & * + - / < = > ? @ \ ^ | ~
```

The following punctuation is structural and is not part of the custom-operator alphabet:

```text
. : ; , ( ) { } [ ]
```

In particular, `.` is reserved for member access, `:` for slot creation, and `;` for explicit expression separation.

The lexer recognizes reserved and standard operator tokens before classifying a remaining valid symbolic sequence as a custom operator.

Reserved or standard symbolic tokens include:

```text
=>  =  ==  ===  !=  !==  <=  >=  &&  ||
+   -  *   /   %   <   >   !   ^
```

The exact one-character spellings `!` and `^` are reserved/standard tokens and are not custom binary selectors: `a ! b` and `a ^ b` are syntax errors. Their existing roles are unchanged wherever they appear: `!` lowers to `not()` as a prefix operator and `^` performs a non-local return. Symbolic token classification is purely lexical and independent of parser position: maximal munch first forms the longest valid spelling, which is classified as a reserved/standard token when it exactly matches a reserved/standard spelling and as `CUSTOM_OPERATOR` otherwise. The characters `!` and `^` remain members of the custom operator alphabet, so longer spellings containing them, such as `!!`, `^^`, `!^`, and `^!`, are `CUSTOM_OPERATOR` tokens and may be used as custom binary selectors, for example `a !! b` and `a ^^ b`.

A symbolic sequence composed from the operator alphabet that is not otherwise reserved or standard may be used as a custom binary selector, for example:

```js
a @ b
a |> b
a <=> b
a ~~ b
a ** b
```

The lexical alphabet is fixed by the language grammar. Modules, imports, runtime objects, or operator declarations cannot extend it.

The formal lexical definition of `custom-binary-operator` — its `operator-character` alphabet, `symbolic-operator-spelling` candidate form, maximal-munch formation, and reserved-spelling classification — is normative in the grammar's Custom Operator Lexing rules.


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

Modules, imports, declarations, or runtime mutation cannot change parser precedence.

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

### Standard Signaling Protocol

The standard `Error` prototype provides the ordinary zero-argument message
`signal()`.

An ordinary error object may therefore be signaled with:

```js
error.signal()
```

The standard behavior is inherited through ordinary delegation. Its receiver
must be `Error` itself or an object whose delegation chain contains `Error`.
Calling the standard signaling behavior with any other receiver is a protocol
error; copying, composing, extracting, or otherwise reusing the implementation
of `Error.signal` does not make a non-error object signalable.

The receiver object itself is the object presented to handler matching and to
the selected handler. Signaling does not implicitly clone, wrap, replace, or
convert it, and does not add Protos-visible slots or mutate its ordinary
language-visible state. Implementations may retain implementation-private
diagnostic information such as stack metadata only when that information is not
observable as additional Core object structure or taxonomy.

`Error.signal()` never returns normally to the activation that invoked it. If a
matching handler is found, Core's unwinding semantics transfer control to that
handler and abandon the signaling continuation. If no matching handler is
found, the error reaches the applicable outermost execution boundary according
to the existing unhandled-error rule.

The standard method takes no arguments. Constructing or enriching an error is
separate ordinary object/protocol behavior and occurs before signaling. Core
does not define string-to-error coercion, prototype-to-instance coercion, or any
other implicit condition designator.

The runtime's own language-defined failures use the same semantic signaling
operation directly; they are not specified as an overridable message send to
`signal()`. Overriding a user object's `signal` slot therefore affects ordinary
message dispatch to that object but cannot redefine how slot lookup failures,
invalid assignments, cancellation observation, or other normative runtime
failures transfer control.

This keeps error signaling inside the ordinary object/delegation model while
preserving one closed semantic category of signalable Core errors rooted at
`Error`.

Handlers are dynamically installed in the execution environment of closures.

No fundamental `try`, `catch`, `throw`, or `finally` keywords are required.


### Standard Handler Installation Protocol

Core v0.1 exposes handler installation through an ordinary message provided by
the standard `Error` prototype:

```js
matchPrototype.handle(body, handler)
```

`matchPrototype` is the receiver. `Error` itself and ordinary error prototypes
below `Error` therefore use the same protocol through normal delegation. The
receiver must be `Error` or have `Error` in its delegation chain.

`body` and `handler` are Closures. The call establishes exactly one dynamically
scoped unwinding handler whose match prototype is `matchPrototype`, then invokes
`body` with no arguments.

Ordinary call evaluation happens before installation. The receiver expression,
the `body` argument expression, and the `handler` argument expression are
evaluated left-to-right before the handler becomes active. Errors signaled while
evaluating those expressions are therefore not handled by the handler being
installed.

If `body` completes normally, its result is the result of `handle`, and the
handler is removed without invoking `handler`.

If an error is signaled while the protected dynamic extent is active and
`matchPrototype` occurs in the signaled error object's delegation chain, that
handler is selected before any older matching handler. Core handlers are
unwinding: execution of the signaling continuation and the remaining protected
computation is abandoned. The selected handler frame is removed before
`handler` is invoked, and `handler` receives the signaled error as its single
argument. If `handler` then completes normally, its result is the result of the
`handle` call.

Because the selected handler is inactive while its handler Closure executes, an
error signaled by that Closure is searched only against still-active outer
handlers. The same handler cannot recursively catch its own failure merely
because the new error also matches `matchPrototype`.

Nested `handle` calls define ordering structurally: the dynamically innermost
matching handler is selected first. Core v0.1 therefore needs no separate
same-scope handler-list ordering rule; multiple handlers are expressed by
ordinary nesting.

A nonmatching error passes through the installed frame and continues outward
through the normal dynamic handler search. Non-local return, cleanup through
`ensure`, and other existing unwind behavior remain ordinary control transfers:
leaving the protected dynamic extent removes the installed handler.

Dynamic handler state is task-local execution state, not Actor-global state and
not a property captured by a Closure. If the protected task explicitly suspends
while the `handle` call remains active, the handler remains part of that same
task's suspended continuation and is active again when that task resumes. Other
Actor-local tasks that run while it is suspended cannot observe or use that
handler.

Creating a distinct asynchronous Future/task inside the protected scope does not
copy or inherit the handler into that task. An unhandled failure in such a task
fails its Future according to the Future rules. If a consumer later observes
that failed Future through `value()`, the recorded error is re-signaled in the
consumer's then-current dynamic handler context. Actor boundaries likewise never
carry dynamic handlers.

This protocol adds no `try`, `catch`, `throw`, or `finally` syntax and no second
handler type system. User and library error prototypes use ordinary delegation,
and richer handling abstractions may be built from this single primitive
dynamic-scope mechanism.
An unhandled error propagates until an appropriate handler is found or the outermost execution boundary is reached.

The architecture should allow resumable conditions to be added later without redesigning the execution model.

### Core Error Taxonomy

Core v0.1 defines one mandatory root error prototype: `Error`. `Error` is an
ordinary standard-prelude object whose delegation parent is `Object`.

Every object signaled as a Core language/runtime error must have `Error` in its
delegation chain. Standard error prototypes named normatively by Core or a
normative domain model are ordinary objects in that chain and must delegate
directly to `Error` unless that same normative specification explicitly defines
another parent relation.

This rule makes the portable taxonomy deliberately shallow. A specification may
introduce a deeper standard hierarchy only by stating that hierarchy
normatively; an implementation must not invent extra Protos-visible intermediate
error categories. Such an invented ancestor would change handler matching and
reflection and is therefore observable language behavior, not an implementation
detail.

When a normative rule says only that an operation "signals an error" and does
not name a standard error prototype, Core v0.1 guarantees only the `Error`
category for portable handler matching. An implementation may attach
implementation-private diagnostic metadata, but it must not expose a different
Protos delegation ancestry for that failure as though the additional category
were standardized.

Conversely, user code and libraries may create ordinary error prototypes and
arbitrary deeper delegation hierarchies beneath `Error`. Those program-defined
hierarchies use the normal object/delegation model and handler matching rules;
they do not extend the set of standard Core error categories.

A prototype name appearing only as pseudocode notation is not thereby a
standard-prelude binding. A name becomes a portable standard error prototype
only when a normative specification explicitly defines it as such.

This taxonomy rule does not introduce checked errors, declarations, hidden
classes, or a parallel type system. It exists solely to make the already
observable prototype-based handler matching deterministic across independent
implementations.

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

### Cancellation of asynchronous execution

A Future-producing task may be asked to cancel through the ordinary Future
cancellation operation. Cancellation is cooperative: requesting cancellation does
not forcibly terminate arbitrary running Protos code.

Cancellation observation is portable rather than implementation-selected. Every
explicit suspension point is a mandatory cancellation observation boundary. If a
cancellation request is pending when a task would suspend, or when suspended work
would otherwise resume into ordinary Protos code, the task observes cancellation
before executing further ordinary Protos code.

An operation whose normative contract is cancellation-aware may also observe a
pending cancellation request while its underlying work is pending, subject to that
operation's own commitment and effect rules.

Ordinary non-suspending Protos execution is not an implicit cancellation
checkpoint. Method calls, allocations, loop back-edges, interpreter polls, JIT
safepoints, garbage-collection points, host calls, or other implementation-defined
runtime boundaries do not by themselves make cancellation observable. Consequently,
CPU-bound code that reaches no explicit suspension point or cancellation-aware
operation may complete normally despite an outstanding cancellation request.

When cancellation becomes effective, the task exits through the ordinary unwind
machinery, so applicable `ensure` cleanup runs before its Future reaches the
cancelled terminal state. An implementation may poll cancellation more frequently
internally only when doing so cannot change the Protos-observable cancellation
boundary.

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

A Future supports transformation:

```js
future.then(value => {
    transform(value)
})
```

`then` returns another Future.

Calling `then` creates a distinct continuation task and its destination Future. That
continuation is asynchronous work created by the activation that calls `then`, so
the ordinary structured-concurrency ownership rule applies to it: unless detached,
the continuation task belongs to that calling activation.

`then` itself does not invoke the transformation closure. Source completion only
makes the continuation eligible to run. The transformation closure executes later
as ordinary task work in the Actor/execution domain of the activation that called
`then`; it never executes as an inline or reentrant consequence of completing the
source Future, including when the source Future was already terminal when `then`
was called.

When the continuation runs:

- if the source Future is resolved, the transformation closure is invoked with the
  resolved value;
- if the source Future is failed, the transformation closure is not invoked and
  the destination Future fails with the same error;
- if the source Future is cancelled, the transformation closure is not invoked
  and the destination Future becomes cancelled.

If the closure returns an ordinary value, the resulting Future resolves with it.

If the closure returns another Future, the result is automatically flattened rather
than producing a nested Future.

Cancellation and detachment apply to the continuation independently of the source
Future. Cancelling the destination Future requests cancellation of the continuation
task but does not cancel or otherwise alter the source Future. Detaching the
destination Future detaches only the continuation task from its structured owner;
it does not detach or otherwise alter the source Future.

These rules do not impose a global ordering among independent continuations.
They require only that a `then` transformation never run before its source Future
is terminal and never run inline as part of the `then` call or source-completion
transition.

## 31. Structured Concurrency

Asynchronous executions belong by default to the execution context that creates them.

Non-detached child tasks are structurally owned by that execution context.

An activation cannot complete while it still owns non-detached child tasks.

On normal completion, the activation waits for all non-detached child tasks to reach a terminal state before the activation itself completes.

That structural wait is a lifetime guarantee, not an implicit result observation. A
failed or cancelled child does not by itself change an otherwise normal result of
the owning activation. Child failure remains stored in the child's Future and
becomes an error in the owner only if owner code explicitly observes that Future
through an operation such as `value()`. Likewise, cancellation of a child does not
implicitly cancel an otherwise normally completing owner.

This rule is independent of whether the child Future was otherwise referenced or
ignored. Implementations must not add hidden "unobserved failure" tracking that
changes the owner's result at structured-scope exit.

On exit caused by error or cancellation, the activation requests cooperative cancellation of all non-detached child tasks, waits for those tasks to unwind and complete their cleanup, and only then continues its own unwind.

This means `ensure` cleanup in child tasks completes before the owning activation finishes unwinding.

An explicit operation:

```js
future.detach()
```

removes that task from the owner's structured-concurrency lifetime. Detached work may continue independently and is not awaited or automatically cancelled merely because the former owner completes.

Detachment is explicit; ordinary `future()` execution remains structured by default.

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

Every object may serve as a delegation parent.

An object's delegation parent cannot change after creation.

Reads may delegate.

Writes never delegate.

: creates.
= modifies.

null is the only absence value.

A missing slot is not null; it is an error.

Execution contexts are objects; their standard prototype is `Context`.

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

Actors share no mutable Protos state; any Protos object physically shared between Actors through the standard prelude is semantically immutable for the duration of that sharing.
```

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

Each module executes inside a `moduleContext`, an ordinary execution-context object, as described in Module Contexts and Top-Level Bindings. The importable unit is the **module instance**. A module instance is an ordinary object, and in Core v0.1 it is the module's own `moduleContext` object:

- the module body executes with the module instance as its current execution context;
- a top-level binding created with `:` becomes a local slot of the module instance;
- `import(specifier)` yields the module instance;
- reading a member of a module instance therefore observes the module's top-level binding slots exactly as they exist at that moment.

There is no separate namespace object, wrapper, copy, or proxy. Module identity is ordinary object identity (`===`), and a module's observable surface is its current top-level slot state.

### Actor-Local Module Instances

A module instance belongs to one Actor. Each Actor owns an independent module cache.

An Actor is an isolated domain of mutable Protos state and execution. Core v0.1 assumes the Actor isolation principle:

> No shared mutable Protos memory exists between Actors.

The broader Actor concurrency model is developed in `PROTOS_CONCURRENCY_MODEL.md`. This section depends only on the isolation and ownership consequences stated here and introduces no Actor syntax.

Importing a module does not provide access to mutable module state belonging to another Actor. Conceptually:

```text
compiled/code representation of module foo
                 |
        +--------+--------+
        |                 |
     Actor A           Actor B
       |                 |
     foo@A             foo@B
       |                 |
 moduleContext A     moduleContext B
```

`foo@A` and `foo@B` are distinct module instances with distinct mutable state. There are no process-global mutable module instances. Because a module's `moduleContext` is Actor-local and is captured by closures through the ordinary lexical-context mechanism, closures created during module initialization do not create cross-Actor shared mutable lexical state; closure capture semantics are unchanged.

The runtime may physically share immutable implementation artifacts between Actors, including parsed syntax, bytecode, machine code, immutable metadata, and immutable constant data where otherwise semantically valid. Such sharing must not expose shared mutable Protos state. The observable `moduleContext` and mutable module state remain Actor-local.

### Canonical Module Identity

Module import uses three distinct concepts:

```text
module specifier
    value written by the program

ModuleKey
    canonical internal identity produced by the module resolver

module instance
    Actor-local module object returned by import
```

A module specifier is resolved relative to the importing module and the host/module-resolution environment. Resolution produces a canonical `ModuleKey`.

The exact external form of a `ModuleKey` is host-defined, but it must provide stable identity. Examples might include canonicalized file URIs, standard-library identifiers, or package identifiers. Two import requests that the resolver determines refer to the same module must produce the same `ModuleKey`.

`ModuleKey` is an internal loader/runtime concept. It is not required to be exposed as a normal language object.

The distinction is:

```text
specifier resolution / locating code
        may be host-defined

module identity / Actor-local instance / cache /
initialization / cycles / failure
        defined by Protos semantics
```

Once a canonical `ModuleKey` has been determined, the current Actor's module cache is consulted. Within one Actor:

```js
a: import("foo")
b: import("foo")
```

when both imports resolve to the same canonical `ModuleKey`, they refer to the same Actor-local module instance, so `a === b` holds. The module body is not executed again for the second import. Across Actors, the same canonical `ModuleKey` produces distinct Actor-local module instances with distinct mutable `moduleContext`s:

```text
Actor A: canonical foo -> foo@A
Actor B: canonical foo -> foo@B
```

### Cache Before Execution

When an Actor imports a canonical module that is absent from that Actor's module cache:

1. Create the module instance, creating its `moduleContext`.
2. Insert that module instance into the Actor-local module cache in state `INITIALIZING`.
3. Execute the module body in that `moduleContext`.
4. If initialization completes successfully, transition the module to `READY`.
5. If initialization fails, apply the failure semantics defined below.

The critical invariant is:

> **Cache before execute.**

The module must be discoverable through recursive imports before its body has finished executing. This prevents import recursion from repeatedly creating fresh instances.

Module initialization states conceptually include:

```text
INITIALIZING
READY
```

A module that is absent from the cache is not loaded in that Actor. A transient failure state may exist internally while a failed initialization is being handled, but a failed initialization must not remain in the cache as a successfully importable module. These states are semantic concepts and are not exposed through a public state-inspection or reflection API.

The same cache-before-execute invariant applies to an Actor's initial module when, at the start of that module's execution, it has an importable canonical identity. Before its body executes, the runtime resolves that canonical `ModuleKey` and inserts the module instance into the Actor-local module cache in state `INITIALIZING`; an importable initial module is not a mutable module instance that exists outside the cache. See The Initial Module of an Actor.

The Actor-local module cache is authoritative for the currently active module instance. Within one Actor, the cache maps canonical identity to the current active module record:

```text
ModuleKey -> ModuleRecord
```

with at most one record for a given `ModuleKey` at a time. A cache entry whose state is `INITIALIZING` or `READY` represents the active module instance for that key. Removing the entry after a failed initialization ends that instance's status as the active cached module instance. Object reachability and cache membership are distinct concepts: an object is not revoked or invalidated by the removal of its cache entry, and cache membership does not limit whether an otherwise reachable object can remain observable.

### Cyclic Imports

Cyclic module dependencies are valid. For example:

```text
A imports B
B imports A
```

must not recurse indefinitely creating new module instances. When B imports A while A is already `INITIALIZING`, B obtains the same Actor-local A module instance already present in the cache. Conceptually:

```text
import A
   |
   v
cache A as INITIALIZING
   |
   v
execute A
   |
   +--> import B
           |
           v
       cache B as INITIALIZING
           |
           v
       execute B
           |
           +--> import A
                   |
                   v
              return existing
              INITIALIZING A
```

A cycle is not rejected merely because it is cyclic.

### Partially Initialized Modules Are Observable

An import of a module already in state `INITIALIZING` returns that same module instance immediately; it does not wait for the module to become `READY`. The observable state is exactly the state of that module's `moduleContext` at that point in sequential execution.

For example, suppose module `a` executes:

```js
x: 10
b: import("./b")
y: 20
```

and module `b` executes:

```js
a: import("./a")
```

When `b` obtains `a` during the cycle:

- `a.x` exists and is readable, because `x: 10` has already executed.
- `a.y` does not yet exist, because `y: 20` has not executed.

Reading a slot that has not yet been created follows the ordinary Protos missing-slot / lookup error semantics. There is no module-specific temporal-dead-zone mechanism, no predeclaration of module slots, and no hoisting of future slot creations merely because the source text contains them later. Normal Protos slot semantics remain authoritative, and there is no special "uninitialized module binding" value.

> **A partially initialized module is the real module instance in its current state, not a placeholder copy.**

### Recursive Import Does Not Suspend

When a recursive import discovers an existing `INITIALIZING` module in the current Actor's cache, the import returns that module instance immediately. It must not suspend waiting for initialization to finish, because suspending would deadlock ordinary cyclic imports within the same Actor. No hidden Actor reentrancy or hidden suspension point is introduced for this case. Actor reentrancy remains identifiable only from explicit suspension operations.

### Successful Initialization

When execution of the module body completes normally, the cached module instance transitions:

```text
INITIALIZING -> READY
```

The same module instance and the same `moduleContext` remain cached. Subsequent imports in that Actor return that instance without re-executing the module body. No new module identity is created merely because initialization completed.

### Failed Initialization

If module initialization terminates with an unhandled error:

- the initiating `import()` fails with that error according to the normal error-propagation model;
- the failed module instance does not remain as a successfully cached module;
- the module's cache entry is removed for that failed initialization attempt;
- a later import may attempt initialization again and may create a fresh module instance.

A failed initialization attempt does not permanently poison the Actor's module cache. A failed partial module instance is not defined as reusable by a later independent import. If cyclic participants obtained a reference to the partially initialized instance before failure, no rollback of already-executed observable effects or object references is invented. Protos errors do not reverse effects that already occurred unless an existing rule explicitly says otherwise.

> Removing the failed module from the module cache does not time-travel or undo side effects already performed during its failed initialization.

A later import after cache removal creates a new initialization attempt and therefore a new module instance:

```text
import foo
    create foo#1
    cache foo#1 as INITIALIZING
    execute
    unhandled error
    remove foo#1 from cache
    import fails

later:

import foo
    create foo#2
    cache foo#2 as INITIALIZING
    ...
```

If a reference to a failed partial instance escaped during initialization, that object remains an ordinary, reachable Protos object after its cache entry is removed. It is the same object it was before failure: it is not revoked, not rolled back, and does not enter a hidden invalid-object state. It is only no longer the Actor's active cached instance for that canonical `ModuleKey`, and its continued existence does not prevent a later import from creating a fresh instance.

The failed instance and a later successful instance may therefore both remain reachable in the same Actor:

```text
foo#1
    state INITIALIZING
    a reference escapes during partial initialization
    initialization fails
    cache entry removed

later:

foo#2
    created by a new import
    becomes READY
```

with:

```text
foo#1 !== foo#2
```

Only `foo#2` is the current active cached module instance for that `ModuleKey`. This coexistence does not violate Actor isolation, because both objects belong to the same Actor. No tombstone, revocation, identity mutation, or hidden invalid-object state is introduced for a failed instance.

### Actor Lifetime and Module Lifetime

An Actor's module cache and its Actor-local module instances belong to that Actor's isolated runtime state. When the Actor dies, its module instances die with it unless some implementation artifact is independently retained for non-observable purposes. Another Actor does not inherit or take ownership of those mutable module instances. Creating a new Actor does not inherit the creator's module cache or live module contexts.

### The Initial Module of an Actor

The initial module executed by an Actor follows the same conceptual module-context model: it executes in a `moduleContext` that belongs to that Actor, and its module state is Actor-local rather than process-global. A new Actor does not inherit its creator's initial module context.

The Actor's initial module is executed directly by the Actor that owns it rather than obtained through `import()`. That direct execution does not place the initial module outside the module model when the initial module has an importable canonical identity.

Whether the Actor's initial entry is treated as an importable initial module or as a standalone non-importable entry point is determined when execution of that entry begins; the choice is not revisited while the instance exists. The importable path below always starts a module through the ordinary cached lifecycle and never adopts or re-registers an instance that was already executed as a standalone entry point. Standalone execution is described in Initial Modules Without an Importable Canonical Identity.

If the initial module can be resolved by `import()` to a canonical `ModuleKey`, then before executing its body the runtime:

1. determines or assigns that canonical `ModuleKey`;
2. creates the module instance and its `moduleContext`;
3. inserts that instance into the Actor-local module cache in state `INITIALIZING`;
4. executes the module body in that `moduleContext`;
5. transitions the instance to `READY` on successful initialization;
6. on failed initialization, removes that cache entry according to the failure semantics defined above.

The same cache-before-execute invariant that applies to imported modules therefore applies to an importable initial module, preventing creation of a second instance when another module imports the Actor's initial module during its initialization. For example:

```text
main imports b
b imports main
```

behaves conceptually as:

```text
Actor startup:

canonical main -> ModuleKey(main)

create main#1
cache ModuleKey(main) -> main#1 / INITIALIZING

execute main#1
    |
    +--> import b
            |
            v
        create b#1
        cache ModuleKey(b) -> b#1 / INITIALIZING
            |
            +--> import main
                    |
                    v
              return existing main#1
```

The recursive import must not create `main#2`.

If the Actor's initial module was registered in the cache because it has an importable canonical `ModuleKey`, and its initialization fails, the same failure rule applies as to any other failed module initialization: the cache entry is removed, no successful-looking cache entry is preserved, effects already performed are not rolled back, and the runtime does not invent an automatic retry as part of Actor startup. Whether failure of the Actor's initial module terminates that Actor or its Process remains governed by the existing Actor and root failure semantics, which this section does not redefine.

### Initial Modules Without an Importable Canonical Identity

Core v0.1 does not require the host to assign a fabricated filesystem or package identity to every possible host entry point. If, when execution of an Actor's initial entry begins, that entry is not importable through the host's module resolver and therefore has no canonical `ModuleKey` reachable by `import()`, it executes as a standalone entry point and remains outside the normal import lookup namespace. The instance created for that standalone execution:

- is Actor-local: it executes in a `moduleContext` belonging to the Actor that launched it, and that mutable `moduleContext` is not shared with another Actor;
- has no canonical `ModuleKey` and is not registered in the Actor-local module cache, so no `import()` can address it and it never aliases an imported module;
- is never retroactively assigned a canonical identity or adopted into the module cache, even if the host's resolution capabilities change after that execution began.

No fake registration under an invented import identity is required for such an entry point. Whether the Actor's initial entry is importable or standalone is determined when execution of that entry begins and is not recomputed later.

If the host later makes code equivalent to that standalone entry importable under a canonical `ModuleKey`, that does not change the identity or status of the standalone instance already created; the host may add resolution capability, but the standalone instance does not thereby acquire a `ModuleKey`. A subsequent `import()` that resolves to that canonical `ModuleKey` follows the ordinary Actor-local module-cache rules of the previous subsections: on a cache miss the runtime creates a new module instance, caches it as `INITIALIZING`, and executes its body through the lifecycle described in The Initial Module of an Actor. The standalone instance and that later cached instance are therefore distinct objects under `===`, and the module body and its side effects may execute again. This is not a double initialization of a single module instance, because the standalone instance never was the cached module instance for that `ModuleKey`. No retroactive cache registration, module-instance adoption, identity mutation, cache migration, source-code deduplication, or rollback of the standalone execution is introduced.

### `import()` and Bindings

Imports are eager by default. Lazy dependencies are expressed explicitly using ordinary language mechanisms such as closures rather than by implicit lazy-import semantics.

The module specifier is an ordinary expression. The language does not require import syntax to introduce names into the current lexical scope; an import operation simply yields the module instance, which the program can bind explicitly:

```js
math: import("math")
math.sin(x)
```

No ES-module-style static binding declarations, CommonJS `exports`, Python namespaces, or another language's module syntax are introduced. Core v0.1 defines no export declaration syntax and no separate export mechanism: a module's directly observable surface is its top-level binding slots, accessed through the module instance. Cross-module visibility remains explicit.

The exact resolver rules for files, packages, standard-library modules, search paths, and other host-specific sources are outside Core Language v0.1.


## Indexed Access Syntax

Bracket indexing is syntactic sugar over ordinary message sends. Indexing is not a privileged runtime operation and is not restricted to arrays.

Slot/member access and indexed access are distinct mechanisms. Member syntax accesses the object's slot/delegation model: `object.name` performs ordinary slot lookup. Indexed syntax invokes the indexing protocol: `object[key]` lowers to `object.at(key)`. Indexed access is **not** dynamic slot access. In particular, `object["foo"]` is not defined to be equivalent to `object.foo`; the two expressions may return completely different values. An object does not automatically become indexable merely because it has slots — `object[key]` works only according to the `at` protocol implemented or inherited by that object.

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

The `=` in indexed assignment does **not** mean "modify an already-existing indexed entry". The syntax itself imposes no universal existence requirement on the key or index. Whether `atPut` creates a new indexed entry, replaces an existing one, extends a collection, requires an existing or in-range index, rejects the operation, or implements some other domain-specific behavior is defined by the receiver's `atPut` protocol. For example, a `Map` may define `map[key] = value` to create the key/value entry when the key is absent and replace its value when the key is already present, while an `Array` may require the index to be within a permitted range. User-defined objects may implement their own `at` / `atPut` behavior; the indexing syntax does not impose `Map` semantics on every indexable object.

Any object may support bracket syntax by implementing the corresponding messages.

Conversely, an indexable object remains an ordinary Protos object and may have ordinary slots, methods, delegation, and openness/frozen state in addition to indexed contents. Indexed contents are not automatically object slots, and a slot is not automatically indexed content. For example:

```js
map.description: "users"
map["description"] = user
```

may coexist and refer to entirely different things: the first operation concerns a slot of `map`, the second concerns `map`'s indexing protocol. Likewise, adding a slot named `foo` does not imply that `object["foo"]` will return that slot; only the receiver's `at` implementation determines the result. Indexability is protocol-based behavior, not a special object kind.

Indexed slot creation is forbidden. `:` is specifically the slot-creation operator and cannot be applied to an indexed target:

```js
object[index]: value     // syntax error
object["foo"]: value     // syntax error
object.foo[index]: value // syntax error
```

There is no indexed equivalent of slot creation in Core v0.1 and no `atCreate`-style protocol. Indexed mutation is expressed solely through `atPut`. `:` operates on the slot model while `[]` operates on the indexing protocol, and the two mechanisms remain distinct. There is no automatic relationship between String-valued indexes and slot names: `object["foo"]: value` must not be interpreted as `object.foo: value`.

The bracket forms do not bypass normal message lookup, mutability rules, or error signaling. The meaning of an index, accepted index types, bounds behavior, and storage semantics are defined by the receiver's protocol.

Evaluation order is left-to-right. For:

```js
getReceiver()[getIndex()] = makeValue()
```

the runtime evaluates `getReceiver()`, then `getIndex()`, then `makeValue()`, and finally performs the `atPut` message send.

The selector names `at` and `atPut` are part of the Core v0.1 indexed-access protocol.

The slot openness rules that govern `:` — for example, creating a slot on an object that does not permit slot creation fails according to the existing semantics — do not automatically apply to `atPut`. `atPut` is an ordinary protocol operation; its behavior is defined by the receiver/protocol and any existing mutability or frozen rules. This revision does not redesign `open`, `closed`, or `frozen` semantics.

These are valid:

```js
foo: value
object.foo: value
object[index].foo: value

foo = value
object.foo = value
object[index] = value
object[index].foo = value
object.foo[index] = value
```

These are syntax errors:

```js
object[index]: value
object["foo"]: value
object.foo[index]: value
```

`object.foo` and `object["foo"]` are not equivalent unless the object's own `at` implementation deliberately makes them behave that way. Reflection facilities, if they provide dynamic slot access or creation, remain separate from `[]`.


## Standard Array Indexed Semantics

A standard `Array` is an identity-bearing object with receiver-owned indexed
element state. Its indexed contents are distinct from its ordinary local slots,
in the same sense that standard Map entries are distinct from ordinary slots.

At any observation point, an Array's indexed state is a finite dense sequence of
element references with logical indices:

```text
0, 1, 2, ... length - 1
```

Core v0.1 defines no holes and no negative-from-end indexing for standard
Arrays.

The standard indexed read:

```js
array.at(index)
```

requires `index` to be a semantic `Integer`. Any Integer family is accepted
according to its mathematical Integer value; no Float-to-Integer conversion,
String parsing, truncation, wrapping, or host-sized coercion is performed. The
Integer must satisfy:

```text
0 <= index < current Array length
```

Otherwise the operation signals an `Error`. A successful `at(index)` returns the
exact element object stored at that position.

The standard indexed update:

```js
array.atPut(index, value)
```

has the same Integer and bounds requirement and replaces exactly the existing
element at that index. Standard `Array.atPut` does not append, grow the Array,
create a hole, shift elements, or otherwise change the Array's indexed length.
A successful call returns the exact `value` object supplied to it.

Consequently bracket syntax has the same standard Array behavior:

```js
array[index]          // standard array.at(index)
array[index] = value  // standard array.atPut(index, value)
```

while retaining the existing syntax-level rule that indexed assignment itself
evaluates to `value`.

Standard Array indexed behavior operates only on an original receiver that owns
standard Array indexed state. Delegation, copying, aliasing, composition, or
otherwise obtaining a standard Array method does not confer Array element
storage on an ordinary object and does not redirect the operation to an
ancestor's Array state. An incompatible receiver signals an `Error` before
performing Array-indexed work.

Array element replacement follows the ordinary object state boundary:

```text
open Array
    -> existing elements may be replaced

closed Array
    -> existing elements may still be replaced

frozen Array
    -> element replacement is prohibited
```

Closing or freezing remains shallow and does not close or freeze element
objects. For `atPut`, a receiver already frozen when the standard method begins
signals an `Error` before index validation or element mutation. A closed Array
still validates the index and may replace the existing element.

Read-only `at` remains available on open, closed, and frozen Arrays.

This section defines the standard semantics of Array indexed state and the
already-existing `at` / `atPut` protocol. It does not add Array literal syntax,
a constructor API, insertion/removal selectors, slicing, negative indexing,
automatic growth, or a second collection hierarchy. Such facilities require
their own explicit contracts if standardized later.

Standard Array `==` and `hash` remain governed by the existing Core default:
without an explicit user override, Arrays use semantic object identity and
`identityHashOf`; element contents are not traversed merely for equality or
hashing.

### Standard Array size

The standard `Array.size` operation returns a semantic `Integer` equal to the
receiver's current indexed element count.

For an Array whose indexed positions are:

```text
0, 1, 2, ... length - 1
```

the result is exactly the mathematical Integer `length`. Core does not require a
particular fixed-width Integer family and does not permit host-size overflow,
wrapping, saturation, or truncation to alter the result.

`size` is a read-only observation. It does not invoke element behavior, does not
traverse or copy the elements merely to establish the semantic result, and does
not mutate the Array. It is available for open, closed, and frozen Arrays.

The existing standard Array receiver-domain rule applies: an object that merely
delegates to an Array or obtains the standard `size` behavior does not thereby
own Array indexed state.

### Standard Array iteration

The standard iteration selector for `Array` is:

```js
array.each(block)
```

`block` must be invokable through the ordinary polymorphic invocation protocol. It need not be a Closure: any value that an ordinary parenthesized call can invoke is accepted. Standard `Array.each` validates this callability after ordinary receiver and argument evaluation and standard Array receiver validation, but before establishing the iteration snapshot or invoking callback behavior. The standard operation then invokes `block` through ordinary polymorphic invocation once with one argument for each Array element.

At the start of `each`, after ordinary receiver and argument evaluation and
after standard Array receiver validation, the operation establishes a shallow
logical snapshot of the receiver's current indexed element sequence. Snapshot
order is ascending Array index order:

```text
0, 1, 2, ... snapshotLength - 1
```

Each snapshot element is the exact object stored at that Array position at the
snapshot point. `each` then invokes `block` once for every snapshot element, in
that order, passing only that element object as the callback argument.

If every callback returns normally, `each` returns the original Array receiver.

The snapshot is shallow. It does not clone, freeze, or otherwise isolate element
objects. Mutating a mutable element through any ordinary reference remains
ordinary mutation of that element and is visible normally.

Replacing an Array element after the snapshot has been established does not
change the value visited for that snapshot position. Likewise, any future
standard operation that changes Array length cannot retroactively add or remove
visits from an already-established `each` snapshot unless that future operation
is explicitly specified to do so.

The callback may replace elements of the same Array through `atPut` whenever
that mutation is otherwise permitted by the Array's open/closed/frozen state.
Such replacement does not alter the current iteration snapshot. Mutations of
other objects likewise remain ordinary effects.

If a callback reaches an explicit suspension point, the Actor may execute other
runnable Actor-local work. Other work may read or mutate the Array according to
the ordinary Array and Actor rules; `each` introduces no Array-wide lock,
mutation prohibition, or hidden scheduling dependency. When the callback
resumes, the current `each` invocation continues with its already-established
snapshot.

If a callback signals an error or exits the `each` invocation through an
ordinary non-local control transfer, no later snapshot element is visited.
Effects already completed by earlier callbacks are not rolled back.

The standard Array receiver-domain rule applies: inheriting, copying, composing,
aliasing, or otherwise obtaining the standard `each` behavior does not confer
Array indexed state on an incompatible receiver.

An implementation need not allocate an eager copied Array merely to implement
the snapshot. Persistent element storage, versioned views, copy-on-write state,
or another representation is valid when the observable shallow-snapshot
behavior is identical. Arrays that are never iterated pay no semantic cost for
iteration snapshots.

## Invocation Arguments, Defaults, Rest, and Spread

Every invocation exposes the arguments supplied by the caller through the reserved intrinsic `args`.

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
args.size == 1
```

A closure may declare one trailing rest parameter:

```js
foo: (first, ...rest) => {
    ...
}
```

The rest parameter is bound to the standard invocation-argument collection defined below, containing the remaining caller-supplied arguments.

Argument spread is supported at call sites:

```js
pack: (...items) => items
values: pack(10, 20, 30)
f(...values)
```

which invokes `f` with the elements of `values` as individual positional arguments, preserving their order. Here `pack` is a rest-capturing closure: its rest parameter binds the ordinary collection containing `10`, `20`, and `30`, and the spread expands that collection's elements into `f`'s arguments.

These facilities are intended to make invocation forwarding and dynamic arity ordinary language operations:

```js
forward: (...args) => {
    target(...args)
}
```

Protocols analogous to Smalltalk block invocation helpers may therefore be implemented using ordinary callable objects and spread, for example conceptually:

```js
f.value(10, 20)
f.values(pack(10, 20))
```

where such protocol methods delegate to normal invocation. No overload resolution by argument type is introduced by these facilities.

Argument lists and parameter lists are comma-separated lists: `,` is the only separator between elements, a comma is a separator rather than a terminator, and trailing commas are syntax errors. Newlines inside the delimiters are continuation/layout under the newline-continuation rules in Separators, Line Breaks, and Comments; they never substitute for a required comma.


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

When the token sequence `Point(args) { ... }` is otherwise valid under trailing-closure syntax, the braces denote a parameterless trailing closure appended to the invocation's arguments: the form desugars as `Point(args, () => { ... })`. The braces do not become an object-construction body.

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


### Numeric hash coherence

Number-family values provide standard `hash` behavior specialized for numeric
semantic equality rather than inheriting `Object`'s identity-based default hash.

For all Core numeric values `a` and `b`:

```text
if a == b:
    a.hash == b.hash
```

This guarantee applies across numeric families. In particular:

```text
1.hash == 1.0.hash
UInt8(1).hash == Int32(1).hash
0.0.hash == (-0.0).hash
```

whenever the corresponding numeric `==` comparison is true.

The semantic hash input for a finite Number is its exact mathematical numeric
value, not its semantic numeric family, storage width, signedness, boxing,
machine representation, or source spelling. Therefore an Integer and a Float
that compare equal numerically must enter the same normal-`Map` hash class even
though they are not semantically identical under `===`.

Float signed zero has one normal numeric hash class because `0.0 == -0.0` is
true, despite the existing identity distinction between the two zeros.

Core Float NaN values have one standard normal-hash class within an execution.
This is not an equality claim: NaN remains unequal under `==`, including to
itself. The canonical NaN hash requirement only prevents IEEE payload, signaling
state, boxing, or host representation from becoming observable through the
standard hash protocol.

The exact Integer returned by standard numeric `hash` is intentionally not fixed
across separate executions. An implementation may salt or randomize numeric
hashing per execution, and unequal numeric values may collide. Within one
execution, however, the result for an immutable numeric value is stable and the
cross-family equality implication above is mandatory.

Standard numeric `hash` must not be implemented as `identityHashOf(this)`,
because semantic numeric identity distinguishes some values that numeric `==`
intentionally equates. `IdentityMap` remains unaffected and continues to use
`identityHashOf` together with `===`.

## Float Special Values and Identity

`NaN` is a special semantic value of the `Float` family, not a language-level singleton object analogous to `null`.

Different IEEE 754 NaN bit patterns, payloads, and NaN sign bits do not create distinct Core language-level semantic values. Core v0.1 has one semantic NaN value in the `Float` family. An implementation may use any convenient NaN representation internally, and Core code cannot observe or depend on an internal NaN payload or sign bit.

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

### Exact call-spread semantics

Core v0.1 call spread:

```js
f(...values)
```

accepts only an original receiver that owns standard `Array` indexed state.
Delegation, copying, composition, or user-defined `at` / `size` / `each`
behavior does not make an otherwise incompatible object spreadable.

The spread operand expression is evaluated exactly once at its ordinary
left-to-right argument position. After successful standard Array receiver
validation, the spread operation captures a shallow logical snapshot of the
Array's current indexed element references in ascending index order:

```text
0, 1, 2, ... size - 1
```

Those captured element objects are appended, in that order, to the outgoing
positional argument vector.

The snapshot is established at the point where the spread argument itself is
evaluated. Later argument expressions may mutate the source Array when ordinary
state rules permit it, but those later mutations do not alter the argument
objects already contributed by that spread.

For example, in:

```js
f(...values, mutate(values))
```

the spread captures `values` before `mutate(values)` is evaluated, because
argument evaluation remains strictly left-to-right.

Spread capture is shallow. It does not clone, freeze, or otherwise transform
the element objects. If an element is a mutable object, the outgoing argument
vector contains that same object reference.

Call spread performs no user-message iteration. In particular, it does not
invoke `each`, `at`, `size`, an iterator method, conversion behavior, or any
other user-defined protocol while extracting standard Array elements. A
non-Array operand signals an `Error` after the operand expression has been
evaluated but before any later argument expression is evaluated.

An empty standard Array contributes zero positional arguments.

The source Array may be open, closed, or frozen; spread is read-only. No Array
mutation, lock, suspension point, iterator object, or hidden callback is
introduced merely by expansion.

This Core rule intentionally does not standardize a general iterable/spreadable
protocol. A future generic iteration protocol may generalize call spread only
through an explicit normative revision defining traversal order, failure,
effects, suspension, mutation visibility, and interaction with existing
collection protocols.

### Invocation argument collections are frozen Arrays

The ordinary immutable collection exposed by `args` is specifically a **fresh
frozen standard `Array`** created for that invocation.

Its indexed elements are exactly the caller-supplied positional argument
objects, after evaluation and in source order. Its `size` is therefore the
number of caller-supplied positional arguments. Default-parameter substitution
does not append or replace elements in `args`.

Each invocation has a distinct `args` Array identity, including zero-argument
invocations. An implementation may avoid a physical allocation when escape,
identity, reflection, and all other observable behavior remain exactly as if the
fresh frozen Array existed.

A rest parameter is likewise bound to a fresh frozen standard Array containing
exactly the remaining caller-supplied positional argument objects, in order.
The rest Array is a distinct object from that invocation's `args` Array even
when their contents happen to be the same, and distinct rest bindings created by
different invocations are distinct objects.

Because these objects are standard Arrays, their read behavior follows the
standard Array contracts for `at`, `size`, and `each`. Because they are frozen,
standard `atPut`, ordinary slot mutation, slot creation/removal, and any other
mutation prohibited by frozen-object semantics fail normally.

Freezing is shallow: mutable argument objects are not frozen merely because a
reference to them occurs in `args` or a rest Array. Parameter bindings and the
argument Arrays therefore refer to the same supplied argument objects; no
deep-copy or alias isolation is introduced.

This uses the existing Array and frozen-object mechanisms rather than defining a
second privileged argument-collection object model.

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

Numeric literal syntax is defined as follows:

- A leading sign is never part of a numeric literal.
- Decimal integer literals use digits `0` through `9`.
- Leading zeroes are allowed and have no radix significance; for example, `007` is decimal `7`.
- Hexadecimal integer literals use `0x` or `0X`.
- Binary integer literals use `0b` or `0B`.
- Octal integer literals use `0o` or `0O`.
- `_` may be used as a visual separator between digits; it cannot appear at the beginning or end of a digit sequence and cannot appear consecutively.
- Radix-prefixed literals produce `Integer` values.
- Decimal literals containing a decimal point or exponent produce `Float` values.
- A `.` belongs to a decimal numeric literal only when it is immediately followed by a decimal digit: `1.0` is a `Float` literal; `1.` and `.5` are not numeric literals as complete source sequences. The lexer tokenizes `1.` as `INTEGER("1")` followed by a `.` token and `.5` as a `.` token followed by `INTEGER("5")`, so for example `1.to(10)` tokenizes as `INTEGER("1")` `.` `IDENTIFIER("to")` `(` `INTEGER("10")` `)`. This does not make either complete sequence necessarily a lexical error; whether the resulting token sequence is syntactically valid is the parser's responsibility.
- Decimal exponents use `e` or `E`, optionally followed by `+` or `-`, and require at least one exponent digit.
- Hexadecimal, binary, and octal `Float` literals are not supported in Core v0.1.
- A `.` immediately following a complete radix-prefixed Integer literal is a structural `.` token when it is not immediately followed by a decimal digit; for example, `0b10.foo` tokenizes as `INTEGER("0b10")` `.` `IDENTIFIER("foo")` and `0xFF.toString()` tokenizes as `INTEGER("0xFF")` `.` `IDENTIFIER("toString")` `(` `)`. When the `.` is immediately followed by a decimal digit, the source sequence is an attempted unsupported radix Float literal and is a lexical error; for example, `0b10.5`, `0o17.25`, and `0x1.8` are lexical errors rather than being split into `INTEGER` `.` `INTEGER` tokens.
- Numeric type suffixes such as `L`, `f`, or `d` are not supported.
- `NaN` and `Infinity` are not special numeric literal syntax.
- Once a source sequence has begun as a numeric literal, if its immediately adjacent continuation makes that numeric form malformed or creates an invalid numeric/identifier boundary, the lexer reports a lexical error. It must not split the malformed sequence into otherwise valid tokens in order to recover it.
- A radix prefix (`0x`, `0X`, `0b`, `0B`, `0o`, `0O`) must be followed by at least one valid digit for that radix. Once a radix prefix has been recognized, an invalid digit or identifier-like continuation does not cause the lexer to fall back to an `INTEGER("0")` token plus another token; for example, `0x`, `0xG`, `0b2`, and `0o8` are lexical errors.
- A `.` immediately following a complete radix-prefixed Integer literal is a structural `.` token unless it is immediately followed by a decimal digit; `0b10.5` is an attempted unsupported radix Float literal and is a lexical error, not `INTEGER("0b10")` `.` `INTEGER("5")`.
- Once `e` or `E` has begun the exponent part of a decimal numeric literal, the exponent must be complete; `2e`, `2e+`, and `2e-` are lexical errors.
- Invalid underscore placement inside or immediately adjacent to a numeric literal is a lexical error; for example, `1__2`, `1_`, and `0x_FF`.
- An identifier cannot begin immediately after a numeric literal without a lexical boundary; `123abc` is a lexical error, not `INTEGER("123")` followed by `IDENTIFIER("abc")`.
- Valid token boundaries remain valid and are not affected by this rule: punctuation, whitespace, structural delimiters, and operators may terminate a numeric token according to the existing lexical grammar. The decimal-point vs. member-access dot rules above are unchanged.

For example:

```js
255
007
0xFF
0b11111111
0o377
1_000
1.5
2e3
1.5e-3
```

denote the same numeric values described by the rules above. Literal radix is syntactic only.

`/` denotes ordinary numeric division and may produce a `Float` from integer operands:

```js
5 / 2    // 2.5
```

Integer quotient/remainder behavior is exposed explicitly through integer protocol messages such as `div` and `mod`.

Conversions between numeric families are explicit when representation or information may change. Operations such as `floor`, `truncate`, and `round` express the intended conversion behavior rather than relying on silent coercion.

`Float` has one fixed Core v0.1 semantic format. The Float semantic value set is
exactly IEEE 754-2019 `binary64` (double precision), with the language-level NaN
model described below. The choice is part of Protos semantics and is not
implementation-defined.

Consequently, every finite Float has the precision and exponent range of
`binary64`; positive and negative zero, positive and negative infinity, and
subnormal values are required. Implementations must support gradual underflow
and must not flush subnormal operands or results to zero.

The standard Float behaviors corresponding to IEEE basic binary arithmetic
(`+`, `-`, `*`, and `/`) and unary negation operate as `binary64`. Each primitive
operation produces the result required by IEEE 754-2019 for those operands using
`roundTiesToEven` when rounding is required. The result of each such operation
is a Float value before any later Protos operation observes or consumes it.

An implementation may use wider registers, fused instructions, constant
folding, vector instructions, JIT specialization, or another internal strategy
only when the observable result is the same as the required sequence of
`binary64` operations. Extra intermediate precision and contraction of separate
operations into a fused operation must not change a Protos result.

Core v0.1 exposes no mutable floating-point rounding mode and no ambient
floating-point status flags. Host thread-local floating-point environment state
must not change Protos results.

For the IEEE basic arithmetic above, overflow, underflow, division by zero, and
invalid floating-point arithmetic produce the corresponding `binary64` infinity,
signed zero/subnormal, or NaN result rather than signaling a Protos error merely
because the IEEE condition occurred.

This rule fixes the semantics of Core floating-point arithmetic; it does not
silently specify unrelated numerical algorithms. Transcendental functions and
other higher-level numerical operations require their own contracts if exact
cross-implementation results are intended.

Endianness is not a property of a numeric value. It belongs to binary encoding and decoding. The same numeric value may be represented as bytes using objects/protocol values such as `BigEndian` and `LittleEndian`.

For example:

```js
value.toBytes(BigEndian)
UInt32.fromBytes(bytes, LittleEndian)
```

or equivalent buffer-oriented protocols.

This follows the general rule that semantic values are distinct from their external binary representation.


### Exact String semantic value and identity

A Core `String` semantic value is exactly a finite sequence of Unicode scalar
values, in order. String semantic identity compares that sequence exactly.

Therefore two String values are semantically identical exactly when they contain
the same number of Unicode scalar values and the scalar value at every position
is the same:

```text
stringIdentity(a, b)
    = exactUnicodeScalarSequence(a) == exactUnicodeScalarSequence(b)
```

No Unicode normalization is implicit in String construction, semantic identity,
ordinary default `==`, or ordinary default `hash`. Canonically equivalent but
differently encoded scalar sequences are distinct String semantic values unless
a program explicitly normalizes them.

For example, a String containing U+00E9 LATIN SMALL LETTER E WITH ACUTE is not
semantically identical to a String containing U+0065 LATIN SMALL LETTER E
followed by U+0301 COMBINING ACUTE ACCENT:

```text
"é" !== "e\u{301}"
```

when the first source spelling denotes the single precomposed scalar U+00E9.
Their standard `==` results are likewise false under the ordinary unspecialized
String equality default, and their standard hashes are not required to be equal.

Case folding, locale-sensitive comparison, canonical-equivalence comparison,
compatibility-equivalence comparison, collation, grapheme-cluster processing,
and Unicode normalization are higher-level text policies. They require explicit
protocols or library operations and do not alter Core String identity.

The rule is independent of internal representation. An implementation may store
Strings as UTF-8, UTF-16, UTF-32, ropes, slices, interned objects, or another
representation, but encoding units, surrogate pairs used internally, storage
sharing, and normalization choices must not change the observable scalar
sequence or semantic identity.

This exact-sequence rule also preserves retained source newline distinctions:
a retained `LF`, `CR`, and `CRLF` denote respectively U+000A, U+000D, and the
two-scalar sequence U+000D U+000A, as already required by the String-literal
rules.

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
UTF8.decode(bytes)
UTF8.encode(text)
```

Decoding interprets a byte sequence using the selected encoding and produces a `String`. Encoding converts a `String` into a `Bytes` value using the selected encoding.

The standard encoding catalogue, strict/replacement decoding rules, BOM behavior, and text-I/O semantics are defined normatively in `PROTOS_IO_MODEL.md`. Those encoding objects and I/O facilities remain outside the required Core prelude unless another specification explicitly says otherwise.

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


## Prefix Operators and Protocol-Based Negation

Core v0.1 supports prefix `-` and prefix `!`.

Prefix `+` is not supported.

Prefix operators are not part of numeric literal syntax.

These operators lower to ordinary message sends, not privileged runtime primitive behavior:

```js
-x    // equivalent to x.negated()
!x    // equivalent to x.not()
```

This is protocol-based rather than a special numeric/Boolean operator. The operators therefore apply to arbitrary expressions according to the normal expression grammar, not only to literals.

## String Literal Semantics

Core v0.1 defines String literals as ordinary `String` values.

- The three supported String source forms — single-quoted (`'...'`), double-quoted (`"..."`), and triple-double-quoted (`"""..."""`) — are formally defined by the lexical grammar in `PROTOS_GRAMMAR.md`.
- Single-quoted and double-quoted forms are equivalent String literals.
- Protos has no separate character literal or character type. `'a'` and `"a"` both denote a `String` containing the single-character text `a`.
- Single-quoted, double-quoted, and triple-double-quoted String literals share the same escape rules.
- The backslash escape is `\\`.
- The supported escape sequences are exactly: `\\`, `\'`, `\"`, `\n`, `\r`, `\t`, `\b`, `\f`, and `\u{HEX}`.
- `\u{HEX}` requires 1 to 6 hexadecimal digits and must denote a valid Unicode scalar value.
- Invalid or incomplete escape sequences are lexical errors.
- Octal escapes and `\xNN` escapes are not supported.
- Triple-double-quoted strings are multiline String literals, not raw strings.
- A triple-double-quoted String starts with exactly three consecutive unescaped double-quote characters (`"""`). When three consecutive double quotes occur at the current lexical position outside a String, triple-double opening recognition takes priority over an ordinary double-quoted String opener.
- Inside a triple-double-quoted String, the first three consecutive unescaped double-quote characters form the closing delimiter, which consumes exactly those three quotes; one or two consecutive unescaped quotes that do not begin a closing delimiter are ordinary content, quotes remaining after a closing delimiter are lexed normally from that position, and an escaped double quote (`\"`) is content and does not participate in a closing delimiter. Core v0.1 defines no implicit concatenation of adjacent String literals.
- Triple-single-quoted strings are not supported.
- String interpolation is not part of Core v0.1.
- `${...}` has no special meaning inside a String and is treated as literal text.
- Reaching the end of source before the required closing delimiter of any supported String literal is a lexical error. This applies to single-quoted (`'...'`), double-quoted (`"..."`), and triple-double-quoted (`"""..."""`) forms, and an unterminated literal never produces a partial String token or a String value.

**Newline Handling in String Literals:**

A logical source newline is one `LF` (U+000A), one `CR` (U+000D), or one `CRLF` (U+000D U+000A) sequence, as defined in Separators, Line Breaks, and Comments.

- Single-quoted and double-quoted String literals are single-line literals.
- A logical source newline (`LF`, `CR`, or `CRLF`) is not permitted inside a single-quoted or double-quoted String literal.
- Encountering a logical source newline before the matching closing quote is a lexical error.
- Newline characters may be represented in single-quoted and double-quoted literals using the `\n` and `\r` escape sequences; these escapes denote String content and are distinct from raw source newlines.
- Triple-double-quoted String literals are multiline String literals and permit logical source newlines as part of the literal content.
- Each logical source newline inside a triple-double-quoted literal counts as one logical newline for structural processing: opening/closing delimiter placement, content-line splitting, and indentation normalization.
- Retained source newlines in a triple-double-quoted literal preserve their original source code points in the resulting String: `LF` remains U+000A, `CR` remains U+000D, and `CRLF` remains U+000D U+000A. There is no implicit newline normalization of String content.
- Opening/trailing newline removal removes the complete logical newline sequence, so a removable `CRLF` is removed as one logical newline.
- In a triple-double-quoted literal, the logical source newline immediately following the opening `"""`, when present, is not part of the resulting String.
- When the closing `"""` is preceded on its source line only by indentation whitespace (possibly none) after a logical source newline, that closing newline and its indentation-only trailing line are not part of the resulting String. A multiline String whose content begins or ends on the same line as a delimiter receives no implicit leading or trailing newline removal.

**Multiline Indentation Normalization:**

- The Core v0.1 multiline indentation normalization rule applies to triple-double-quoted String literals. Indentation is normalized as exact source characters: `SPACE` (U+0020) and `CHARACTER TABULATION` (U+0009, TAB) are distinct code points, they are never considered equivalent for indentation purposes, and Core v0.1 defines no semantic tab width. Normalization is never computed from visual columns or editor tab stops, and never from a minimum-indent rule, a common-visual-column rule, or the longest common whitespace prefix among the content lines.
- The closing delimiter alone establishes the structural indentation prefix. When the closing `"""` terminates an indentation-only trailing line (the case excluded above), the structural indentation prefix is exactly the sequence of `SPACE` and `TAB` characters on that source line immediately preceding the closing delimiter; the prefix may be empty, which is the case when the closing delimiter begins its line. When content flows into the closing delimiter on its source line rather than ending at an indentation-only trailing line, no structural indentation prefix exists and no indentation normalization is performed.
- Indentation normalization applies only where a structural indentation prefix exists. Where no structural indentation prefix exists, no indentation or other whitespace is removed from any content line; this includes whitespace-only content lines, whose `SPACE` and `TAB` characters are ordinary String content and are preserved verbatim. Blank-line whitespace stripping is part of multiline indentation normalization and never applies unconditionally. No structural indentation prefix ⇒ no indentation normalization.
- Where the closing delimiter establishes a structural indentation prefix, the remaining content is split into content lines at each retained logical source newline, and every non-blank content line must begin with exactly that prefix, compared as exact source characters. The prefix is removed exactly once from the beginning of each non-blank content line; the remainder of the line, including any further leading `SPACE` or `TAB` characters, is preserved.
- A structural indentation prefix may contain both `SPACE` and `TAB` characters, and mixed `SPACE`/`TAB` indentation is legal when each content line begins with exactly the same prefix. A `TAB` never equals any number of `SPACE` characters regardless of how an editor displays it. For example, a closing delimiter preceded by `TAB` `SPACE` `SPACE` requires each non-blank content line to begin with exactly `TAB` `SPACE` `SPACE`; a line beginning with `SPACE` `SPACE` `SPACE` `SPACE` does not satisfy that prefix.
- Where the closing delimiter establishes a structural indentation prefix, a non-blank content line that does not begin with the exact prefix — because it has fewer prefix characters, uses `SPACE` where the prefix requires `TAB`, uses `TAB` where the prefix requires `SPACE`, or otherwise differs from it — makes the triple-double-quoted String invalid. Consistent with the existing String-literal lexical-error model, this is a lexical error: the literal produces no String token and no String value, and no recovery behavior is defined.
- Where a structural indentation prefix exists, a blank content line — a content line containing no characters other than `SPACE` and `TAB` (possibly none) — is exempt from the prefix requirement and need not contain the complete structural indentation prefix. All `SPACE` and `TAB` characters on such a blank content line are removed as incidental indentation, so a source blank line contributes an empty logical line rather than whitespace caused solely by source indentation. No intentional whitespace is removed from a non-blank content line beyond the single structural prefix.
- Indentation matching and stripping operate on the raw source characters at the beginning of each content line, before escape sequences are interpreted. An escape sequence that denotes a `TAB` or any other character is not a source `SPACE` or `TAB` and never satisfies the structural indentation prefix. The Core v0.1 escape rules themselves are unchanged by this rule.

Example literals:

```js
"hello"
'hello'
"""
    hello
    world
    """
"${notInterpolated}"
"line\nfeed"
"\u{1F600}"
```

The first multiline example above evaluates to:

```text
hello
world
```

Another example:

```js
"""
    hello
        world
    """
```

evaluates to:

```text
hello
    world
```


Blank-line whitespace stripping occurs only where the closing `"""` establishes a structural indentation prefix. In the following literal the closing delimiter establishes a four-`SPACE` structural prefix, and the intermediate source line between `one` and `two` is whitespace-only and therefore a blank content line:

```js
"""
    one
    
    two
    """
```

The two non-blank content lines each lose the four-`SPACE` prefix exactly once. The whitespace-only intermediate line is exempt from prefix matching, and all of its `SPACE` characters are removed as incidental indentation, so it contributes an empty logical line. The resulting String is conceptually:

```text
one

two
```

equivalently:

```text
"one\n\ntwo"
```

When content flows into the closing delimiter on its source line, no structural indentation prefix exists and no indentation normalization is performed. In the following literal the intermediate source line contains exactly seven `SPACE` characters and is a whitespace-only line; because no structural prefix exists it is preserved verbatim:

```js
"""one
       
two"""
```

The resulting String is conceptually:

```text
"one\n       \ntwo"
```


### Standard Bytes indexed semantics

A standard `Bytes` object is an identity-bearing mutable object with
receiver-owned byte-sequence state. Its byte contents are distinct from its
ordinary local slots.

At any observation point, standard Bytes state is a finite dense sequence of
octets with logical indices:

```text
0, 1, 2, ... byteLength - 1
```

Each stored octet has the mathematical value range `0 .. 255`. Bytes carry no
implicit text, character, signed-integer, Unicode, or host-native interpretation.

The standard indexed read:

```js
bytes.at(index)
```

requires `index` to be a semantic `Integer`. Any Integer family is accepted by
its mathematical Integer value. No Float-to-Integer conversion, String parsing,
truncation, wrapping, modulo reduction, or host-sized coercion is performed.
The index must satisfy:

```text
0 <= index < current byteLength
```

Otherwise the operation signals an `Error`.

A successful read returns a semantic `Integer` whose mathematical value is the
stored octet value in `0 .. 255`. Core does not require one fixed-width Integer
family such as `UInt8` for this result; observable correctness is the exact
mathematical Integer value.

The standard indexed update:

```js
bytes.atPut(index, value)
```

requires the same valid semantic Integer index and additionally requires
`value` to be a semantic `Integer` with mathematical value in `0 .. 255`.
Invalid value objects and out-of-range Integers signal an `Error`; the standard
operation never truncates, masks, wraps, takes modulo 256, parses text, or
coerces a Float.

A successful `atPut` replaces exactly the existing octet at that position,
changes no other position, leaves the byte-sequence length unchanged, and
returns the exact `value` object supplied to the invocation.

Consequently:

```js
bytes[index]          // standard bytes.at(index)
bytes[index] = value  // standard bytes.atPut(index, value)
```

use those same contracts, while indexed assignment itself retains the general
language rule that the assignment expression evaluates to the assigned value.

Standard Bytes indexed behavior applies only to an original receiver that owns
standard Bytes byte-sequence state. Delegation, copying, aliasing, composition,
or otherwise obtaining a standard Bytes method does not confer byte storage on
an ordinary object and does not redirect access to an ancestor's Bytes state.
An incompatible receiver signals an `Error` before byte-indexed work.

Byte replacement follows the ordinary object-state boundary:

```text
open Bytes
    -> existing octets may be replaced

closed Bytes
    -> existing octets may still be replaced

frozen Bytes
    -> octet replacement is prohibited
```

Closing or freezing is shallow. For standard `atPut`, a receiver already frozen
when the standard method begins signals an `Error` before index/value
validation or byte mutation. A closed Bytes object still validates the index
and value and may replace an existing octet. Read-only `at` remains available
on open, closed, and frozen Bytes.

This rule defines only the already-existing standard indexed protocol. It does
not introduce byte literals, resizing, append, insert/remove, slicing, numeric
endianness, text decoding, or a second binary-buffer hierarchy. Such facilities
require explicit protocols if standardized separately.

Standard Bytes `==` and `hash` remain governed by the existing Core default for
identity-bearing objects. Two distinct Bytes objects do not become equal merely
because their current octets are equal, and ordinary standard hashing does not
traverse byte contents.

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


### Exact standard String indexing semantics

Core v0.1 uses the **default extended grapheme-cluster** boundary rules of
Unicode Standard Annex #29, Unicode Text Segmentation, as synchronized with
**The Unicode Standard, Version 17.0.0** (UAX #29 revision 47), for standard
`String.size` and `String.at`.

These are the untailored default Unicode rules. A host locale, ICU version,
operating-system text service, rendering engine, editor convention, language-
specific tailoring, or later Unicode release must not change Core-visible
String boundaries. Implementations may use such facilities only when they
produce exactly the Unicode 17.0.0 default extended-grapheme-cluster result.

For a String whose exact semantic value is the scalar sequence `S`, let:

```text
G = defaultExtendedGraphemeClusters17(S)
```

where concatenating the scalar sequences of the elements of `G`, in order,
reconstructs `S` exactly. Segmentation does not normalize, case-fold, replace,
or otherwise alter the String's scalar values.

The standard `size` result is:

```text
String.size -> semantic Integer equal to length(G)
```

The result is an exact semantic `Integer`; Core does not require a fixed-width
Integer family or host-sized representation.

The standard indexed read:

```js
text.at(index)
text[index]
```

requires `index` to be a semantic `Integer`. Any Integer family is accepted
according to its mathematical Integer value. No Float-to-Integer conversion,
String parsing, truncation, wrapping, modulo reduction, or host-sized coercion
is performed.

The index must satisfy:

```text
0 <= index < text.size
```

Otherwise the operation signals an `Error`.

A successful read returns a `String` whose semantic scalar sequence is exactly
the scalar subsequence forming `G[index]`. The returned String is not normalized
or rewritten. Therefore indexing preserves the exact-scalar String model even
when the selected grapheme contains several scalars.

For example, if:

```text
S = U+0065 U+0301
```

and Unicode 17.0.0 default extended-grapheme segmentation treats that sequence
as one cluster, then `text.size` is `1` and `text[0]` is the two-scalar String
`U+0065 U+0301`, not an implicitly normalized U+00E9 String.

Standard String indexing behavior applies only to an original receiver that is
a semantic String value. Delegating to a String value or String prototype does
not confer String semantic membership. An incompatible receiver that invokes
the standard behavior signals an `Error` under the existing semantic-family
receiver rule.

`String` is immutable and Core defines no standard indexed mutation behavior
that changes a String in place. Bracket assignment does not acquire a hidden
String mutation primitive merely because bracket-read syntax is supported.
User-defined objects remain free to define their own ordinary `atPut` protocol.

This rule defines only the already-visible `String.size` and `String.at`
semantics. It does not standardize the illustrative `graphemes()`,
`codePoints()`, normalization, locale-sensitive segmentation, collation, or
text-editing protocols mentioned elsewhere.

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

### Hash Result Contract

The language-level `hash` protocol returns a semantic `Integer` value.

A `Map` operation that consumes a key's `hash` result must validate that result
before using it. Any semantic `Integer` value is valid, including fixed-width
Integer-family values; the protocol does not require one particular Integer
representation, width, signedness, or implementation layout. A Float, String,
Boolean, `null`, ordinary identity-bearing object, or an object that merely
delegates to an Integer value is not an Integer hash result.

No implicit conversion, truncation, masking, modulo reduction, host-word-size
coercion, or Float-to-Integer conversion is part of the language protocol. An
implementation may reduce or mix a valid Integer internally for its own table
layout only if that reduction is unobservable and preserves the specified
`Map` matching semantics.

If a `Map` key's `hash` behavior returns a non-Integer value, the Map operation
signals an error before performing its own Map mutation. Effects already
performed while evaluating the user-defined `hash` behavior are ordinary
effects and are not rolled back.

For correctly behaving hashable values, repeated `hash` observations during one
execution must be stable whenever the state relevant to `==`/`hash` has not
changed, and:

```text
a == b  =>  a.hash == b.hash
```

The equality in the hash contract compares the mathematical Integer hash values;
different semantic Integer families representing the same mathematical Integer
therefore satisfy the contract.

`identityHash` likewise produces a semantic `Integer`. It is the hash companion
to semantic identity (`===`): if `a === b`, their `identityHash` values must be
the same during that execution. The converse is not required; identity-hash
collisions are permitted.

For an identity-bearing object, `identityHashOf` remains stable for that
object's lifetime within the current Protos execution. For Core value-identity
categories, semantically identical values receive equal identity hashes
independently of boxing, allocation, interning, representation, Actor placement,
worker placement, operating-system process, or machine placement within that
same Protos execution.

The observable standard identity-hash domain is the Protos execution, not a host
process. Separate Protos executions need not choose the same identity hashes.
Within one Protos execution, host placement alone must not change the
`identityHashOf` result of a semantic value whose identity is preserved.

This does not require a global mutable identity-hash registry or a global lock.
For value-identity categories an implementation may derive identity hashes from
semantic identity plus immutable execution-scoped configuration. For
identity-bearing objects it may allocate or cache identity hashes locally where
the object lives, provided the object's required semantic identity and lifetime
rules are preserved. An Actor pass-by-value copy that is a new identity-bearing
object is not required to retain the source object's identity hash merely because
its copied state is equal.

Persistent, distributed, cryptographic, or interoperable hashing requires a
separate explicit algorithm/protocol. Ordinary `hash`, `identityHash()`, and
`identityHashOf` do not define a persistent object identifier or externally
stable fingerprint.

### Identity-hash dispatch boundary

Semantic identity hashing is non-overridable in the same sense as semantic
identity itself.

Core defines a primitive semantic operation, written conceptually as:

```text
identityHashOf(value)
```

`identityHashOf` is not a Protos message lookup and ordinary program code cannot
replace, shadow, intercept, or override it. It returns the semantic `Integer`
identity hash governed by the `identityHash` contract above.

The standard prelude may expose ordinary convenience behavior such as:

```js
object.identityHash()
```

whose standard implementation returns `identityHashOf(this)`. Such an ordinary
message remains subject to the normal Protos object model: a program may shadow
or override the `identityHash` slot for explicit message sends.

That customization does **not** redefine semantic identity hashing. In
particular, `IdentityMap` uses `identityHashOf(key)` together with primitive
`===`; it does not send the overridable `identityHash` message to a key.
Overriding `key.identityHash()` therefore cannot change whether the key is found
in an `IdentityMap`, create or remove identity-key collisions at the language
level, or violate the invariant that semantically identical values have the same
semantic identity hash.

Likewise, implementation optimizations may compute or cache semantic identity
hashes internally, but may not route `IdentityMap` behavior through user-defined
message dispatch merely because the same spelling `identityHash` exists as a
convenience protocol.

This distinction does not create a second observable notion of identity:
`identityHashOf` is the hash companion of the already non-overridable `===`
relation. The ordinary `identityHash()` message is only a way to expose that
primitive result when its standard implementation is used.

Stable `hash`/`==` behavior remains the correctness contract for keys whose
programmer intends ordinary associative-map behavior. Core nevertheless defines
the result of violating that contract; it does not make the `Map` implementation
strategy observable.

A `Map` entry retains the hash recorded when that entry was first inserted.
Subsequent mutation of the stored key, mutation of state consulted by its
`hash`/`==` behavior, or any other change does not recompute that recorded hash,
move the entry, replace its representative key, or cause automatic reindexing.

### Standard Map receiver domain

Standard `Map` and `IdentityMap` keyed behavior operates on keyed-entry state
owned by the original receiver. Ordinary delegation can make a standard Map
method visible to another object, but delegation alone does not create, copy,
borrow, or redirect keyed-entry state.

A standard keyed behavior whose contract requires normal-Map state is applicable
only when the original receiver owns standard normal-`Map` keyed-entry state.
Likewise, a standard behavior whose contract requires `IdentityMap` state is
applicable only when the original receiver owns standard `IdentityMap`
keyed-entry state, unless that behavior is explicitly specified as generic over
both standard Map kinds.

This applies to the standard keyed protocols defined by Core, including `at`,
`atPut`, `containsKey`, `remove`, `each`, and any other standard behavior whose
normative semantics inspect or mutate the receiver's keyed-entry state.

For an incompatible receiver, invocation signals an `Error` after ordinary
receiver/argument evaluation and ordinary message lookup have selected the
behavior, but before the behavior performs keyed-state work. In particular, the
failing invocation performs no key `hash`, no key `==`, no `identityHashOf`
operation for key search, no iteration-snapshot capture, and no keyed-entry
mutation.

Failure does not resume lookup at a more distant slot with the same name.
Lookup remains ordinary delegation lookup; receiver-domain validation belongs
to the selected standard behavior's contract.

Consequently, if an ordinary object delegates to a Map object, Map prototype, or
another object exposing standard Map methods, that object does not thereby
become a Map and does not gain hidden associative storage:

```text
ordinaryChild -> someMapOrMapPrototype -> ...

ordinaryChild.at(key)
    -> Error if the selected standard behavior requires Map keyed-entry state
       that ordinaryChild does not own
```

Copying, aliasing, composing, or otherwise reusing a standard Map method does not
confer Map keyed-entry state either. User-defined behavior remains ordinary
Protos behavior and may intentionally implement a wider receiver contract.

This rule does not introduce a second delegation relation or a class/type
hierarchy. It makes explicit the receiver-owned semantic state already required
by standard keyed collections and prevents implementations from borrowing an
ancestor's entries, allocating hidden storage on first inherited use, or making
delegation itself grant collection membership.

### Map key-state visibility during search

Map lookup fixes the candidate sequence and the lookup hash information, but it
does not snapshot the mutable state of key objects.

During one normal `Map` lookup:

- the candidate sequence is fixed for that lookup;
- a stored key's recorded hash is not recomputed merely because user code
  mutates that key;
- the query key's single `hash` result is the hash used by that lookup;
- mutations performed by equality code remain ordinary visible mutations;
- later candidate comparisons observe the current state of the relevant objects;
- the lookup does not restore, clone, freeze, or otherwise snapshot key objects;
- mutation does not restart or reorder the lookup and does not silently trigger
  another query-key hash.

This distinguishes fixed search-control state from live object state. An
implementation must not copy mutable key objects merely to make lookup easier
to implement.

For identity lookup, no user equality callback is performed. The same fixed
candidate-order and no-key-snapshot rules nevertheless apply.

Implementations may use hash tables, ordered arrays, trees, or other internal
representations, provided the specified candidate order, hash behavior, and
visibility of intervening mutations are preserved.

Every later key search continues to use the deterministic matching algorithm
defined below: compute the query key's current hash once, consider only entries
whose recorded hash equals that query hash, and compare those candidates in
insertion order using the query key's current `==` behavior. Therefore a changed
stored key may cease to be findable by itself, may later coexist with another
key that currently compares equal, or may become findable by a different query.
Those outcomes follow from the specified recorded hashes and current protocol
results rather than from hash-table layout.

If several stored entries currently compare equal to a query and have the same
recorded hash as that query, the earliest such entry in insertion order is the
one found. Entries with different recorded hashes are not equality candidates
for that search even if their current `==` behavior would report equality.

The invariant

```text
a == b  =>  a.hash == b.hash
```

therefore remains a programmer-facing contract required for conventional map
semantics, not a license for implementations to choose arbitrary behavior when
it is violated. The same is true of the recommendation that a stored key's
relevant equality/hash behavior remain stable.

Core does not prohibit mutable objects from being keys, does not implicitly
freeze keys, and does not require hidden mutation tracking. Implementations may
diagnose unstable or inconsistent keys in optional debugging facilities only if
doing so does not replace the normative Core behavior of ordinary execution.

Protocol violations cannot cause host-language memory unsafety or corruption of
the Protos runtime. They also do not authorize implementation-dependent
exceptions, aborts, nontermination, silent reindexing, or other behavior that
would differ from the deterministic `Map` search/update rules.


### Default equality and hash behavior

`Object` provides the default ordinary `==` and `hash` behavior for receivers
that do not provide more specific behavior through ordinary delegation.

The default `==` behavior is semantic identity:

```text
defaultObjectEquals(a, b) = (a === b)
```

Therefore two distinct ordinary identity-bearing objects compare unequal by
default even if they currently contain the same slots, while two references to
the same ordinary object compare equal. No structural slot comparison,
delegation-chain comparison, prototype comparison, serialization comparison, or
host-representation comparison is implied.

The default `hash` behavior returns the receiver's semantic identity hash:

```text
defaultObjectHash(a) = identityHashOf(a)
```

Consequently the inherited defaults satisfy the normal Map contract:

```text
a == b  =>  a.hash == b.hash
```

without requiring per-object user code.

Both are ordinary messages exposed through the object model. A more specific
object or prototype may override `==` and/or `hash` through ordinary slots. If
custom `==` behavior makes two non-identical values equal, the programmer or
library defining that behavior is responsible for providing coherent `hash`
behavior as already required by the Map contract.

Overriding ordinary `==` does not change `===`. Overriding ordinary `hash` does
not change `identityHashOf` or `IdentityMap`.

The default does not make `==` globally symmetric, transitive, or reflexive for
all user-defined behavior. Those properties follow only where the specific
equality protocol in use guarantees them. The default inherited behavior itself
has the corresponding properties because it delegates to semantic identity.


### Inequality semantics

`!=` is the ordinary customizable inequality message protocol. `Object`
provides its default behavior in terms of the receiver's current `==` behavior:

```text
Object.!=(other):
    result = this == other
    return booleanNot(result)
```

`booleanNot` accepts only canonical `true` or `false`; an error from `==` or an
invalid equality result propagates rather than being interpreted through
truthiness. Consequently, an object that overrides `==` but inherits the
default `!=` automatically obtains the logical complement of its customized
equality.

A program may override `!=` independently as ordinary object behavior. If it
does, Core does not impose a global law that the custom `!=` must remain the
complement of custom `==`; both operations retain their existing strict
Boolean-result contracts. Code that requires complementary custom behavior must
define it accordingly.

`!==` is different: it is the non-overridable logical complement of semantic
identity `===`.

```text
a !== b  =  not (a === b)
```

`!==` performs no `!=`, `==`, `not`, or other user-overridable message dispatch.
It returns canonical `true` exactly when `a === b` is false, and canonical
`false` exactly when `a === b` is true.

Therefore overriding `==` or `!=` cannot change `===` or `!==`, and overriding
ordinary equality cannot change identity-sensitive mechanisms such as
`IdentityMap`.

### Default equality and hashing when Core defines no specialization

The ordinary `Object` equality/hash behavior is the default for every Core
object for which no normative rule explicitly defines more specific standard
`==` or `hash` behavior. This default is not limited to identity-bearing object
kinds.

Therefore, absent an explicit normative specialization:

```text
receiver == other
    -> receiver === other

receiver.hash()
    -> identityHashOf(receiver)
```

For identity-bearing objects, this means ordinary object identity and an
identity-derived hash. For Core value-identity objects, the same inherited
default operates on their semantic identity: semantically identical String
values, the canonical Boolean values, and `null` therefore use `===` for the
standard equality result and `identityHashOf` for the standard hash unless a
more specific normative rule is explicitly defined.

A built-in family, standard prototype, container, buffer, executable object,
runtime coordination object, context, module, singleton, or other Core object
does not acquire structural, content-derived, case-folded, locale-sensitive,
state-derived, or otherwise specialized equality/hashing merely because an
implementation or host language commonly provides such behavior.

A normative section may deliberately define specialized standard equality or
hashing where semantics require it. Number is explicitly specialized: numeric
`==` is numeric equality rather than semantic identity, and standard numeric
`hash` is correspondingly required to preserve numeric-equality coherence.
Other explicit specializations, if added normatively, likewise take precedence
over this default.

The existing standard Map/IdentityMap equality/hash rule is a documented
collection-specific consequence of this general default, not a separate
identity relation.

User-defined ordinary `==` and `hash` overrides remain unaffected. Libraries may
provide structural, recursive, content-based, locale-sensitive, or
domain-specific equality and hashing explicitly; those policies are not inferred
by Core.

### Standard Map equality and hashing

`Map` and `IdentityMap` are identity-bearing mutable objects. The standard Map
prototypes do not introduce structural collection equality or structural
collection hashing.

Unless a program deliberately overrides the ordinary protocols, both standard
Map kinds use the ordinary `Object` defaults:

```text
map1 == map2
    -> map1 === map2

map.hash()
    -> identityHashOf(map)
```

Consequently two distinct Maps remain unequal under standard `==` even when
they currently contain the same associations in the same insertion order, and
even when their keys and values compare equal. The same rule applies to two
distinct `IdentityMap` objects.

Standard Map equality and hashing therefore do not:

- enumerate or compare entries;
- invoke key `hash` or `==` protocols;
- invoke equality or hashing on stored values;
- depend on insertion order, current capacity, physical table layout, or
  recorded entry hashes;
- recurse through Maps stored as keys or values;
- change merely because a Map is closed or frozen.

This keeps the standard `hash` of a Map stable for that Map's semantic identity
during the current execution even while the Map's contents mutate. A standard
Map may therefore itself be used as a normal `Map` key without its own content
mutation silently changing its default equality/hash class.

The word "identity" in `IdentityMap` describes how that collection matches its
keys. It does not grant `IdentityMap` a second object-identity relation and does
not cause two distinct IdentityMaps with identical entries to compare equal.

`==` and `hash` remain ordinary customizable messages. A program may define
structural or domain-specific Map comparison and hashing deliberately, but such
overrides are then governed by the existing general contracts: custom equality
must return the required Boolean result where applicable, and keys intended for
ordinary associative behavior must satisfy equality/hash coherence and the
existing stability rules. Such customization does not affect `===`,
`identityHashOf`, or `IdentityMap` key matching.

Core intentionally does not provide an implicit deep Map equality institution.
A library that wants structural, recursive, order-sensitive, order-insensitive,
cycle-aware, or application-specific collection comparison must expose that
policy explicitly rather than making ordinary mutable Map identity depend on
entry traversal.

### Standard `Map.atPut` result

For the standard `Map` and `IdentityMap` indexed-update protocols,
`atPut(key, value)` returns the exact `value` object supplied to that invocation
after the update succeeds.

This result is independent of whether the operation inserted a new entry or
updated an existing entry. It does not return the previous mapped value and does
not use `null`, a hidden sentinel, or another absence marker to distinguish
insertion from replacement.

Consequently an explicit ordinary message send:

```js
result: map.atPut(key, value)
```

has:

```js
result === value
```

after successful completion.

Bracket assignment remains governed by the existing indexed-assignment rule and
also evaluates to the assigned value:

```js
map[key] = value
```

The syntax-level result does not depend on the `atPut` return value, so
user-defined indexing protocols remain free to define a different direct
`atPut` result unless their own normative protocol says otherwise. This section
fixes only the standard `Map` and `IdentityMap` protocol results.

If key search, hashing, equality, receiver validation, or the mutation itself
signals an error, `atPut` has no normal return. Existing rules for effects and
Map mutation before failure remain unchanged.

### Standard Map missing-key semantics

For the standard `Map` and `IdentityMap` protocols, indexed lookup through
`at(key)` requires a matching entry. If the key search completes normally and no
entry matches, `at(key)` signals an `Error`; it does not return `null`, `false`,
a hidden sentinel, or another ordinary value to represent absence.

This rule is necessary because every Protos object, including `null`, is a valid
stored Map value. A mapping whose value is `null` is present and is observably
different from an absent mapping.

`containsKey(key)` is the non-failing presence query. If its key search completes
normally, it returns canonical `true` exactly when a matching entry exists and
canonical `false` otherwise. It does not retrieve or interpret the mapped value,
so a key mapped to `null`, `false`, or any other value is still present.

Conceptually:

```text
map.at(key)
    matching entry -> entry.value
    no match       -> Error

map.containsKey(key)
    matching entry -> true
    no match       -> false
```

Hashing, equality, and identity-key matching remain exactly those already
specified for the receiver's Map kind. If a required `hash`, `==`, identity-hash
operation, or other key-search step signals, that error propagates; the
missing-key rule applies only after a search completes normally with no match.

Core introduces no special absence value and does not reserve any ordinary
object as an out-of-band Map result. Libraries that want lookup-with-default,
optional-result, or `ifAbsent` behavior may expose a distinct ordinary protocol
without changing standard `at(key)` semantics.

### Standard Map interaction with `close()` and `freeze()`

`Map` and `IdentityMap` are ordinary objects and participate in the existing
open/closed/frozen object-state model. Their keyed-entry state is receiver-owned
mutable state even though indexed entries are not object slots.

For the standard Map protocols:

```text
open Map
    may insert entries
    may remove entries
    may replace values of existing entries

closed Map
    may not insert entries
    may not remove entries
    may replace values of existing entries

frozen Map
    may not insert entries
    may not remove entries
    may not replace values of existing entries
```

The same rules apply to `IdentityMap`.

Closing or freezing a Map is shallow. It changes mutation permissions on that
Map's own keyed-entry state and ordinary local slots; it does not close or freeze
stored keys or values and does not change their identity, equality, or hash
behavior.

Read-only Map operations, including `at(key)` and `containsKey(key)`, remain
available on closed and frozen Maps and use the same deterministic key-search
semantics.

For `atPut(key, value)`, state validation is ordered as follows after ordinary
receiver/argument evaluation:

- if the Map is frozen, the operation signals an `Error` before invoking the
  key's `hash`, `==`, or any other key-search callback, because no successful
  keyed-entry mutation is permitted;
- if the Map is open, ordinary key search runs and the operation may either
  replace a matched entry's value or append a new entry;
- if the Map is closed, ordinary key search runs because replacing an existing
  entry is still permitted. A match may have its value replaced; a no-match
  result signals an `Error` before appending a new entry.

For a standard operation whose purpose is removal of a keyed entry, a closed or
frozen Map signals an `Error` before beginning key search because that operation
cannot perform a permitted keyed-entry mutation in either state. An open Map
uses the ordinary key-search and removal rules.

These state checks do not roll back receiver/argument-evaluation effects that
occurred before the standard Map method begins. Where key search is permitted,
its ordinary `hash`/`==` effects and errors remain governed by the existing Map
rules.

This is a standard collection contract, not a change to indexed syntax.
User-defined `atPut` or other indexed protocols remain ordinary behavior and are
not automatically constrained by Map-specific keyed-state rules merely because
they use bracket syntax.

### Deterministic `Map` key matching

Because `==` and `hash` are ordinary Protos protocol operations, the direction
and observable order in which a `Map` uses them are part of `Map` semantics.
Implementations must not let hash-table layout, bucket order, probing strategy,
or another storage detail choose which user-defined equality operations occur.

For every standard `Map` operation that searches for an argument key `queryKey`,
the portable behavior is as if the map performs the following search:

```text
queryHash = queryKey.hash

for each stored entry in insertion order:
    if entry.recordedHash == queryHash:
        if queryKey == entry.key:
            match that entry and stop

no entry matches
```

`queryKey` is therefore the receiver of `==`; the stored key is its argument.
Core does not silently reverse the comparison, invoke both directions, or
symmetrize a user-defined `==`. The existing Boolean-result contract applies to
every comparison.

When a new entry is created, the hash value obtained from that insertion key is
recorded conceptually with the entry. An implementation may represent this
metadata differently, but a correctly behaving key observes semantics
equivalent to the search above. This does not weaken the existing requirement
that the equality/hash behavior relevant to a key remain stable while it is
stored.

If evaluating the query's `hash` or one of the required `==` comparisons signals
an error, the `Map` operation signals that error. A mutating key operation does
not perform its own structural/value mutation before the key search completes
successfully; side effects already performed by user `hash` or `==` behavior are
not rolled back.

For insertion/update through `map[key] = value`, if the search finds an existing
entry, only that entry's value is replaced. The originally stored key object,
its recorded hash, and its insertion position are retained. Supplying a
different identity-bearing object that compares equal therefore does not replace
the representative key visible during iteration.

If no entry matches, a new entry containing the supplied key and value is added
at the end of insertion order and stores the query hash obtained for that
operation.

The same matching rule applies to standard operations such as direct lookup,
`containsKey`, removal by key, and any later standard `Map` protocol that is
defined in terms of finding a key. A library operation that deliberately wants
different matching semantics must expose a distinct protocol rather than rely
on implementation-specific `Map` internals.

`IdentityMap` is unchanged. Its matching operation is based on `===` and
`identityHashOf`, not this `Map` `==` protocol.

This rule intentionally does not turn general user-defined `==` into an
equivalence relation. If user code defines asymmetric equality, `Map` remains
deterministic: only `queryKey == storedKey` is relevant. Numeric equality keeps
its separately specified symmetry guarantee.

Likewise, `Map` does not add an identity shortcut before `==`. Values such as
Float NaN therefore retain their ordinary `==` semantics when used as normal
`Map` keys. Code that requires identity-keyed behavior uses `IdentityMap`.
The ordinary `hash` operation is not required to be stable across separate
Protos executions. Standard built-in hash behavior may use per-execution
randomization or salting for security, but host placement is not a semantic hash
boundary: moving otherwise equivalent execution across operating-system
processes, threads, workers, or machines within the same Protos execution must
not by itself change a standard built-in value's observable `hash` result.

For standard immutable value families whose hash is defined from semantic value,
the mapping from the specified semantic hash key to the observable Integer hash
is therefore coherent for the duration of one Protos execution. Separate Protos
executions need not choose the same mapping.

This requirement does not impose a global mutable hash table or a global lock.
An implementation may derive the mapping from immutable execution-scoped
configuration and may additionally use per-Map, per-Actor, per-worker, or
per-process mixing for physical table layout when that mixing is not observable
through the language-level `hash` result or logical Map matching semantics.

Persistent or interoperable hashing must use a separate explicit protocol or
algorithm.

`IdentityMap` follows the same insertion-order rule unless a more specialized collection explicitly documents otherwise.



### Standard Map iteration

The standard iteration selector for `Map` and `IdentityMap` is:

```js
map.each(block)
```

At the start of the standard `each` invocation, after ordinary receiver and
argument evaluation, the operation establishes a shallow logical snapshot of
the receiver's current associations in insertion order. Each snapshot element
contains exactly the representative key object stored by the Map and the exact
value object associated with that entry at the snapshot point.

`each` then invokes `block` once for every snapshot element, in snapshot
insertion order, with two ordinary arguments:

```text
block(key, value)
```

If every callback returns normally, `each` returns the receiver Map object.

The snapshot is an iteration semantic boundary, not a physical representation
requirement. While the callbacks execute, code may mutate the same Map whenever
those mutations are otherwise permitted by the existing open/closed/frozen,
hash/equality, and reentrancy rules. Such later Map mutations do not alter the
current iteration snapshot:

- entries inserted after the snapshot are not visited by that invocation;
- entries removed after the snapshot are still visited if their snapshot
  position has not yet been visited;
- replacing a Map entry's value after the snapshot does not change the value
  argument stored in the current snapshot;
- removing and later reinserting a semantically equal key does not create a
  second visit in the current snapshot;
- nested `each` calls establish their own independent snapshots.

The snapshot is shallow. It preserves the key and value object references that
were stored at the snapshot point; it does not clone, freeze, or otherwise
isolate those objects. Mutating a mutable key or value object through some
ordinary reference remains mutation of that object and is observable normally.
Only later changes to the Map's association set or replacement of an entry's
mapped value are excluded from the already-established snapshot.

This rule also applies when a callback reaches an explicit suspension point.
Another Actor-local task may mutate the Map while the iterating task is
suspended, subject to ordinary Actor/task semantics, without acquiring a hidden
iteration lock and without changing the suspended iteration's established
snapshot. Standard Map iteration therefore introduces no Map-wide lock,
mutation prohibition, or scheduling dependency.

If a callback signals an error or otherwise exits the `each` invocation by an
ordinary non-local control transfer, no later snapshot element is visited.
Effects already completed by earlier callbacks are not rolled back.

`IdentityMap.each` has exactly the same snapshot and result semantics. Its
snapshot contains the representative key objects and values stored by the
IdentityMap; no identity re-search is performed merely to iterate.

An implementation need not allocate an eager copied Array or pair objects.
Persistent entry structures, versioned cursors, copy-on-write state, or any
other representation are valid when they produce the same shallow-snapshot
behavior. The cost of preserving this semantics is incurred only when iteration
requires it; Core does not mandate snapshot copies for Maps that are never
iterated.

### Deterministic `IdentityMap` key semantics

`IdentityMap` uses semantic identity rather than ordinary equality. Its logical
key search is:

```text
queryIdentityHash = identityHashOf(queryKey)

for each stored entry in insertion order:
    if entry.recordedIdentityHash == queryIdentityHash:
        if queryKey === entry.key:
            match that entry and stop

no entry matches
```

Both `identityHashOf` and `===` in this algorithm are the primitive,
non-overridable semantic operations already defined for identity-sensitive
machinery. No `identityHash`, `hash`, `==`, or other user-overridable message is
sent while finding an `IdentityMap` key.

Each newly inserted entry logically records the semantic identity hash obtained
for its key. Because semantic identity hashes are stable for the lifetime
required by the identity-hash contract, an implementation may recompute,
cache, reduce, or store this information differently when the difference is not
observable; the algorithm above defines matching behavior, not physical table
layout.

For indexed insertion or update, if a matching entry already exists, only that
entry's value is replaced. Its representative key and insertion position are
retained. If no entry matches, a new entry containing the supplied key and value
is appended at the end of insertion order.

Removal by key removes the matching entry. A later insertion of the same
semantic key after that removal is a new insertion and therefore appears at the
end of insertion order.

The same identity-key search rule applies to direct lookup, `containsKey`,
removal by key, indexed insertion/update, and any later standard `IdentityMap`
operation defined in terms of finding a key.

Identity-hash collisions do not make distinct semantic identities match.
Conversely, two values that are semantically identical under `===` denote the
same `IdentityMap` key even if an implementation represents them using different
allocations, boxes, or immediate-value encodings.

This rule does not require a particular hash-table representation or a physical
linear scan. An implementation may use any indexing strategy that produces the
same observable matching, update, removal, and insertion-order behavior.

### Standard keyed removal

The standard keyed-removal selector for `Map` and `IdentityMap` is:

```js
map.remove(key)
```

It removes exactly the entry selected by the receiver's existing deterministic
key-search semantics and returns that entry's previously stored value.

If key search completes normally and no entry matches, `remove(key)` signals an
`Error`. It does not return `null`, `false`, a hidden sentinel, or another
ordinary value to represent absence. A mapping whose stored value is `null`
therefore remains distinguishable from an absent mapping.

Conceptually:

```text
map.remove(key)
    matching entry -> remove entry, return previous stored value
    no match       -> Error
```

The same result contract applies to `IdentityMap.remove(key)`, using primitive
identity-key matching.

`remove(key)` is a mutating standard Map operation and therefore follows the
existing open/closed/frozen rules and state-revalidation rules:

- an already closed or frozen Map signals before key search;
- on an open normal Map, permitted key search may execute user-defined `hash`
  and `==` behavior;
- if such behavior closes or freezes the Map before the actual removal, the
  operation signals before removing the matched entry;
- successful removal returns the exact stored value object that belonged to the
  removed entry.

Effects already performed by key-search callbacks are not rolled back if the
outer removal later fails.

`containsKey(key)` remains the non-failing way to ask whether a key is present.
Libraries that want remove-if-present, default-return, or conditional-removal
behavior may expose distinct ordinary protocols rather than overloading the
standard `remove(key)` absence result.

### Map state transitions during key search

The object-state checks above are semantic checks at the point where the
corresponding keyed-entry mutation is about to occur; an earlier successful
check does not grant permission that survives later user code.

This matters for normal `Map`, because key search can execute user-defined
`hash` and `==` behavior. The existing outermost-hash rule permits ordinary
effects before candidate traversal, and comparison callbacks may perform
ordinary operations that do not themselves mutate the Map's keyed-entry state.
Those effects may include calling `close()` or `freeze()` on the Map.

Therefore a standard mutating Map operation must revalidate the receiver state
after every user-code phase that can precede its own keyed-entry mutation:

- `atPut(key, value)` still rejects a Map that is already frozen before key
  search, preserving the existing fail-before-callback rule;
- after a successful matching search, immediately before replacing the matched
  entry's value, `atPut` checks the then-current Map state again. Replacement is
  permitted only while the Map is open or closed; if a key callback froze the
  Map, the operation signals an `Error` before replacing the value;
- on the no-match path, the existing insertion check occurs after key search and
  therefore uses the then-current state. A callback that closes or freezes the
  Map prevents insertion;
- a keyed-entry removal checks removal permission before key search as already
  specified and, if a matching entry is found, checks again immediately before
  removing it. A callback that closes or freezes the Map therefore prevents the
  removal.

State changes already completed by user behavior are ordinary effects and are
not rolled back merely because the outer Map operation subsequently fails.
Likewise, key-search effects other than the denied keyed-entry mutation remain
completed.

`IdentityMap` key search executes no user-defined callback, so the same
point-of-mutation rule normally observes the same state as its initial check.
Implementations may elide a redundant recheck when they can prove that no
semantic operation between the checks can change the receiver state.

No lock, transaction, snapshot, or reservation of future mutation permission is
introduced. The rule simply applies the receiver's actual state at the semantic
mutation point.

### Reentrant mutation during `Map` key comparison

A normal `Map` search may execute user-defined `==` behavior while it is
examining candidate entries. That callback is ordinary Protos code, but it must
not make the candidate sequence of the same in-progress search depend on the
implementation's table iterator.

While a `Map` is executing a `queryKey == storedKey` comparison on behalf of a
search of that same Map, any operation that would mutate that Map's entry set,
entry values, recorded hashes, or insertion-order state signals an `Error`
before performing the Map mutation.

This restriction is scoped to the particular Map and only to the dynamic extent
of its key-comparison callback. It does not block:

- mutation of unrelated Maps;
- mutation of the query or stored key objects themselves;
- mutation of objects stored as Map values;
- read-only operations on the same Map;
- ordinary non-Map slot mutation that does not alter the Map's keyed-entry
  state.

If the attempted reentrant Map mutation signals an error that user code handles,
the comparison may continue and return a Boolean in the ordinary way. If the
error escapes the comparison, the outer Map operation fails. No Map-entry
mutation attempted while the restriction was active has occurred.

The query key's `hash` call happens before that search enters any of its own
candidate-comparison scopes. In the ordinary outermost case, effects performed
by `hash`, including mutations of the target Map, complete according to ordinary
semantics before the search examines its candidate entries. Consequently the
candidate search observes the Map state that exists after the query hash has
returned.

This does not suspend an already-active comparison restriction. If an outer
comparison scope for that same Map is already active — for example because a
key's `==` implementation performs a nested read-only lookup on the same Map —
the nested lookup's `hash` call executes while that outer scope remains active.
Any attempt by that `hash` behavior to mutate the same Map therefore signals the
ordinary reentrant-mutation `Error` before mutation. A `hash` call is exempt only
from a comparison scope that would otherwise be created by its own search; it
does not escape comparison scopes established by enclosing operations.

The Map's own requested mutation, if any, still occurs only after key search
succeeds as specified elsewhere.

Mutation of key objects during `hash` or `==` remains governed by the existing
mutable-key rules: the query hash is computed once, stored entries retain their
recorded hashes, and later equality comparisons use then-current key behavior.

The implementation may enforce the comparison restriction with an operation
stack, reentrancy flag, iterator discipline, or any other mechanism. No
Actor-wide lock, global lock, snapshot copy of the Map, or permanent per-entry
metadata is required by Core semantics.

## Future Cancellation

Future cancellation is explicit and cooperative.

```js
future.cancel()
```

`cancel()` requests cancellation; it does not forcibly terminate an arbitrary running activation.

A task observes cancellation only at the portable cancellation boundaries defined
for Future-producing asynchronous execution above, including mandatory explicit
suspension boundaries and normatively cancellation-aware operations. Runtime,
interpreter, JIT, GC, allocation, call, and loop checkpoints are not additional
language-level cancellation boundaries merely because an implementation uses them
internally. When cancellation becomes effective, the task exits through the normal
unwind machinery so that `ensure` cleanup executes.

A cancelled Future completes in the cancelled state. Observing its result:

```js
future.value()
```

signals the standard cancellation condition/error object, conceptually `Cancelled`.

Core v0.1 does not define unsafe asynchronous thread-kill semantics.

## Future Failure and Dynamic Error Context

Errors signaled inside a Future do not asynchronously transfer control into the task that created the Future.

If an error is not handled inside the asynchronous task, the Future records failure.

```js
future: work.future()
```

If `work` fails, the creator may continue executing. The failure becomes observable when the Future result is consumed:

```js
future.value()
```

At that point the recorded error is re-signaled in the dynamic context of the consumer.

The handler rule is therefore:

```text
while executing the asynchronous task
    -> handlers dynamically installed inside that task apply

when observing a failed Future through value()
    -> handlers dynamically surrounding value() apply
```

A Future does not retain the creator's dynamic handler stack as an indefinitely active error context.

A handler that remains dynamically active in the same task across an explicit suspension is retained only as part of that task's suspended continuation; it is not visible to other Actor-local tasks and is not copied into newly created asynchronous work.

## Concurrency Memory Semantics

Core v0.1 concurrency follows the Actor model: an Actor is a serialized domain of mutable Protos state, and there is no shared mutable Protos memory between Actors. Concurrent execution is organized as follows.

- **Actor-local Future/task concurrency.** Ordinary Future/task execution created within an Actor remains Actor-local and cooperative. Only one segment of Actor-local Protos code executes at a time. Tasks may interleave with other Actor-local work only at explicit suspension points; they never execute Protos code simultaneously against the same mutable Actor state, and between suspension points Actor-local state is serialized, so no locks or other explicit synchronization are required to protect it.
- **Different Actors.** Actors do not share mutable Protos references. Another Actor's mutable state is never accessed by direct reference; it is reached only through Actor communication, which has pass-by-value semantics.
- **Explicit isolated parallel computation.** Standard `Closure.parallel(arguments...)` explicitly submits isolated parallel computation and returns a Future. It may execute Protos code simultaneously on other CPU carriers, but it crosses an explicit isolation boundary. The receiver Closure supplies executable code through the parallel-projection semantics below rather than carrying its caller lexical environment into P. Mutable inputs are fixed as logical boundary values before a successful submission returns, P does not semantically mutate the calling Actor's original mutable values, partial P-owned state is not published on failure or cancellation, and completed results cross back by value. P has no implicit Actor sender identity or ambient Actor/Process/Node/Cluster/I/O authority; any future P-safe effect capability requires its own normative contract.

### Standard isolated parallel submission

`parallel` is standard behavior of Closure values. It introduces no keyword, no
new expression grammar, no second executable value kind, and no public
identity-bearing parallel-task object.

```js
square: (x) => x * x
future: square.parallel(12)
result: future.value()
```

The message send itself is ordinary lookup and invocation. A user-defined
`parallel` slot selected by ordinary lookup is ordinary user behavior; the rules
in this section apply when lookup selects the standard Closure `parallel`
behavior.

The explicit arguments of `parallel(arguments...)` are evaluated left-to-right
by the ordinary call rules before the standard behavior begins. The standard
behavior then:

1. validates that the supplied argument count is admissible for the receiver
   Closure's parameter form under the ordinary Closure argument-binding rules;
2. forms one atomic logical P-boundary snapshot over the receiver Closure's
   projectable user-visible state and all explicit argument graphs;
3. signals `NonParallelValue` synchronously if that complete boundary graph
   contains a value that cannot cross into P;
4. otherwise creates the parallel computation and its result Future, registers
   it under the ordinary structured-ownership rules of the creating activation,
   and returns that Future.

No Future or partially submitted P computation is produced when synchronous
argument/boundary validation fails. Because ordinary argument evaluation happens
first, effects from evaluating the receiver and arguments are not rolled back.

#### Parallel Closure projection

A Closure used as the `parallel` receiver does **not** cross into P with its
caller lexical environment. Instead, the boundary creates a fresh P-local
ordinary Closure that is the **parallel projection** of the source Closure.

Parallel projection preserves:

- the executable body and parameter form;
- ordinary user-visible local slots of the Closure object, transferred through
  the P value graph;
- repeated-reference and cycle identity within one boundary snapshot.

Parallel projection does not transfer:

- the source Closure's captured lexical execution contexts;
- its captured caller `this`;
- its caller return home;
- its caller `methodHome`;
- caller dynamic error-handler state;
- any other hidden caller-domain execution state.

The projected Closure is conceptually created in a fresh P root execution
domain. That root has a fresh execution context whose lexical parent is the
standard frozen prelude, `this` is `null`, and its return home is new and local
to that P computation. The projected Closure therefore captures only this P root
environment. It cannot reach the source module context, source activation, source
Actor state, or another caller lexical context merely because the source Closure
could.

This is an explicit operation of `parallel`, not a change to ordinary Closure
creation or invocation. Calling the source Closure with `()` continues to use
its normal captured contexts by reference. Calling `future()` continues to run
the ordinary Closure in its current execution domain. Only `parallel()` requests
parallel projection.

Consequently:

```js
factor: 10
work: (x) => x * factor

work(5)            // ordinary Closure semantics: may read captured factor
work.parallel(5)   // P has no caller/module capture named factor
```

If evaluation in P reaches a bare lookup that cannot be resolved through P-local
contexts, the P root, the standard prelude, or the ordinary receiver lookup rules
available there, normal lookup failure occurs inside P and fails the result
Future. Protos performs no hidden capture-by-value analysis and no
implementation-selected static "safe capture" heuristic.

To make caller data available, pass it explicitly:

```js
work: (x, factor) => x * factor
future: work.parallel(5, factor)
```

Closures created **inside** P are ordinary Closures and capture P-local lexical
contexts by reference exactly as usual.

The P-local root return home remains active for the lifetime of the P
computation. Non-local return `^` executed by a projected Closure targets that
P-local home, never the caller's discarded home. A projected Closure has no
caller `methodHome`; `super` therefore cannot use a method home from outside P.
Ordinary method binding established entirely inside P may establish P-local
receiver/method-home semantics in the normal way.

#### Values crossing the P boundary

This concurrency domain defines the standard error prototype
`NonParallelValue`, delegating directly to `Error`.

P-boundary value formation follows the logical value-copy, cycle-preservation,
alias-preservation, and atomic whole-graph principles used by Actor
pass-by-value transfer, but P has its own transferability set.

Ordinary values that can be represented as isolated logical value copies may
cross. P transferability is a logical semantic property and does not expose
whether an implementation copied, shared, remapped, or copy-on-wrote physical
storage. Semantically immutable standard-prelude objects may be physically shared
when the standard prelude-sharing rule permits it.

Core v0.1 exposes no `isShareable` predicate, immutable-sharing marker, deep-freeze
operation, ownership-transfer flag, or other public control over this physical
choice. Existing shallow `freeze()` is neither a promise that an entire reachable
graph will be physically shared nor a requirement for an implementation to use
safe immutable backing internally. Inability to perform a sharing optimization
must not by itself make an otherwise P-transferable logical value fail.

The following do not become P-transferable merely because they are reachable
from an otherwise copyable object:

- ActorRef or GroupRef;
- pending Future/task identity or an ExecutionContext;
- open I/O/resource capabilities;
- Process, Node, Cluster, placement, lifecycle, or administrative authority;
- native/host objects whose semantics do not define P transfer.

A Closure appearing in the bootstrap receiver or explicit P input graph crosses
only by the same parallel-projection rule above: its executable code and
user-visible value state may be projected, while its caller capture metadata
does not cross.

Input snapshot validation is atomic over the complete combined graph. A repeated
source object maps to one destination object in that snapshot; two distinct
source objects do not merge merely because they are equal.

A normally completed P result crosses back under the same P-boundary value
rules. If the result cannot cross, the result Future fails with
`NonParallelValue` and exposes no partial result.

An Error signaled inside P is likewise transferred as the Future's failure value
when its logical graph can cross. If the Error graph itself cannot cross, the
caller Future fails with caller-domain `NonParallelValue` instead. No reference
to P-local mutable state is leaked merely to report failure.

#### Cooperative work inside P

P is an isolated mutable execution domain, not a permanently single-stack
region. Ordinary `closure.future()` created while executing inside P creates a
cooperative P-local task. Such tasks may interleave at explicit suspension
points but never execute Protos code simultaneously against the same P-local
mutable state.

Nested `closure.parallel(...)` crosses a fresh isolation boundary and may execute
simultaneously, exactly as P created from an Actor may.

Detachment never promotes P-local cooperative work or nested P work into a
persistent independent identity. The P domain's lifetime remains bounded by the
parallel computation and its structured cleanup/cancellation rules.

Future completion establishes a visibility boundary:

```text
all effects performed by a task before successful, failed,
or cancelled completion happen-before observation of that
completion through Future.value()
```

Consequently, after `future.value()` completes normally, writes performed by the Future task before completion are visible to the observing task.

Example:

```js
state: { value: 0 }

future: (() => {
    state.value = 42
}).future()

future.value()

print(state.value)   // observes 42
```

`state.value = 42` is serialized Actor-local work: the Future task and the observing task interleave only at suspension points, and the completion visibility rule guarantees that the observing task sees the write after `future.value()` returns.

Core v0.1 intentionally does not yet specify a full low-level memory model comparable to a platform VM memory model. Implementations must nevertheless preserve the Future completion visibility guarantee, the Actor turn model, and Actor isolation.

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

### Map comparison restriction across suspension

A normal `Map` candidate equality comparison is ordinary Protos code and may
reach an explicit suspension point. Suspension does not end, mask, or transfer
the same-Map comparison restriction.

If a `queryKey == storedKey` comparison suspends while executing on behalf of a
search of a particular Map, that Map remains under the existing keyed-entry
mutation restriction until the comparison invocation completes normally or
leaves through ordinary unwind. Other runnable Actor-local work may execute
while the comparison is suspended, but any such work that attempts to mutate
that same Map's keyed-entry state signals the same reentrant-mutation `Error`
before mutation.

The restriction therefore follows the lifetime of the in-progress comparison,
not merely the currently executing Actor turn or task segment. It remains
Map-specific and does not prevent:

- other Actor-local tasks from running;
- read-only operations on that Map;
- mutation of unrelated Maps;
- mutation of key objects or objects stored as values;
- ordinary non-Map slot mutation that does not alter that Map's keyed-entry
  state.

A read-only same-Map search started by another Actor-local task while the
comparison is suspended executes under the already-active restriction. Its own
query-key `hash` call therefore cannot mutate that Map merely because the new
search has not yet entered one of its own candidate comparisons.

The restriction is not a blocking lock. A conflicting keyed-entry mutation
fails according to the existing reentrant-mutation rule; it does not wait for
the suspended comparison to resume. No global Actor or execution-wide exclusion
is introduced.

When the suspended comparison later resumes, the same restriction is still
active. Normal return, error unwind, non-local return, or cancellation unwind
that leaves the comparison releases that comparison's contribution to the
restriction exactly once. An implementation must not leave the Map permanently
restricted after the comparison has ceased to exist.

This rule deliberately avoids snapshotting the Map's keyed-entry state across a
suspending equality callback and avoids letting unrelated Actor-local
interleaving silently change the candidate associations of an already-active
comparison. A key equality implementation that suspends for an unbounded time
can consequently keep that particular Map mutation-restricted for the same
unbounded time; this is an observable consequence of choosing to suspend inside
a protocol whose dynamic extent protects that Map, not permission for the
runtime to block unrelated Actor work.

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

