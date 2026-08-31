# Core Language Grammar v0.1

Language version: 0.1  
Document revision: 8  
Status: Draft  
Last updated: 2026-08-30


## Prelude Binding Note

Prelude bindings introduce no additional grammar. The shared standard prelude is frozen by runtime semantics. Therefore `name = value` cannot modify a binding found only in the prelude; `name: value` creates a local slot and may explicitly shadow that name.

## 1. Scope

This document defines the lexical grammar, expression grammar, precedence rules, and mandatory syntactic desugarings of the language.

It does not redefine the object model or runtime semantics specified in `LANGUAGE_SPEC.md`.

The grammar is written in EBNF:

```text
{ x }     zero or more repetitions
[ x ]     optional
x | y     alternative
"..."     literal token
```

## 2. Identifiers

```ebnf
identifier =
    identifier-start,
    { identifier-part };

identifier-start =
    letter | "_";

identifier-part =
    letter | digit | "_";
```

Reserved intrinsic identifiers:

```text
this
context
null
true
false
```

They are not ordinary writable identifiers. `super` is a reserved keyword for super-message-send syntax and is not an expression value.

## 3. Literals

```ebnf
literal =
      number-literal
    | string-literal
    | "true"
    | "false"
    | "null";
```

Exact number formats and string escape rules are defined separately.

## 4. Whitespace and Newlines

Spaces and tabs are token separators.

Line breaks may separate expressions when the current expression is syntactically complete.

There is no Automatic Semicolon Insertion.

```js
a: 1
b: 2
```

contains two expressions.

```js
a: 1 +
    2
```

contains one expression because `+` requires a right operand.

Likewise:

```js
result: object
    .foo()
    .bar()
```

is one expression.

## 5. Expression Separators

```ebnf
expression-sequence =
    [ expression,
      { separator, expression },
      [ separator ] ];

separator =
      ";"
    | newline;
```

Thus:

```js
foo()
bar()
baz()
```

is equivalent to:

```js
foo(); bar(); baz()
```

when each expression is complete at the newline.

## 6. Program

```ebnf
program =
    expression-sequence;
```

There is no special global grammar. A source module is an expression sequence evaluated with its own module execution context as the initial `context`.


## Module Contexts and Top-Level Grammar

The grammar introduces no special syntax for global variables.

A source module is still parsed as an ordinary `expression-sequence`. The distinction between module top level and a nested execution context is supplied by the execution environment, not by a different grammar.

Thus:

```js
x: value
```

uses exactly the same slot-creation syntax at module top level as it does elsewhere. When evaluated at module top level, the current `context` is the module's `moduleContext`, so `x` becomes a local slot of that object.

No `global`, `var`, `let`, `const`, or equivalent declaration form is introduced.

Import/export syntax is intentionally not defined by Core Grammar v0.1 and will be specified with the module system.

## 7. Expressions

```ebnf
expression =
      slot-creation
    | assignment
    | non-local-return
    | binary-expression;
```

Slot creation and assignment have the lowest precedence.

## 8. Slot Creation

```ebnf
slot-creation =
    assignable, ":", expression;
```

Examples:

```js
x: 10
person.name: "Guille"
this.cache: {}
```

`:` always creates a new local slot at the selected destination.

## 9. Assignment

```ebnf
assignment =
    assignable, "=", expression;
```

Examples:

```js
x = 20
person.name = "Guillermo"
```

`=` modifies an existing writable slot and never creates one.

## 10. Assignable Expressions

```ebnf
assignable =
      identifier
    | member-expression;
```

Examples:

```js
x
person.name
this.name
context.value
```

## 11. Non-local Return

```ebnf
non-local-return =
    "^", expression;
```

`^a + b` means `^(a + b)`.

## 12. Primary Expressions

```ebnf
primary-expression =
      literal
    | identifier
    | intrinsic-reference
    | super-message-send
    | object-expression
    | closure-expression
    | parenthesized-expression;
```

## 12.1 Super Message Send

```ebnf
super-message-send =
    "super", ".", identifier, argument-list;
```

`super` is not a value. Consequently bare `super`, passing `super` as an argument, assigning it to a slot, and extracting `super.member` without invoking it are syntax errors in the core grammar.

## 13. Parenthesized Expressions

```ebnf
parenthesized-expression =
    "(", expression, ")";
```

Parentheses affect grouping only and do not create an execution context.

## 14. Object Expressions

```ebnf
object-expression =
      object-body
    | parent-expression, object-body;

object-body =
    "{", expression-sequence, "}";
```

Examples:

```js
{
    name: "Rex"
}

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

parses conceptually as:

```text
dog : (animal { name: "Rex" })
```

The `:` belongs to slot creation, not prototype syntax.

## 15. Parent Expressions

```ebnf
parent-expression =
      identifier
    | intrinsic-reference
    | member-expression
    | parenthesized-expression;
```

Examples:

```js
animal {
}

library.models.animal {
}

(getPrototype()) {
}
```

## 16. Closures

```ebnf
closure-expression =
    parameter-list, "=>", object-body;

parameter-list =
    "(", [ parameters ], ")";

parameters =
    identifier,
    { argument-separator, identifier };

argument-separator =
      ","
    | newline;
```

Examples:

```js
() => {
    42
}

(x) => {
    x * x
}

(a, b) => {
    a + b
}
```

## 17. Calls and Arguments

```ebnf
argument-list =
    "(", [ arguments ], ")";

arguments =
    expression,
    { argument-separator, expression };
```

Examples:

```js
foo()
foo(1)
foo(1, 2)

foo(
    1
    2
)
```

## 18. Member Access and Postfix Expressions

Postfix operations have high precedence and associate left-to-right.

Conceptually:

```ebnf
postfix-expression =
    primary-expression,
    { postfix-operation };

postfix-operation =
      ".", identifier
    | argument-list;
```

This supports:

```js
dog.speak()
foo().bar().baz
```

Member access associates left-to-right.

## 19. Trailing Closures

A trailing closure is permitted only after a call.

```ebnf
trailing-closure =
    [ parameter-list ], object-body;
```

Examples:

```js
items.each(item) {
    print(item)
}

future.then(value) {
    transform(value)
}

condition.ifTrue() {
    print("yes")
}
```

Mandatory desugaring:

```js
items.each(item) {
    print(item)
}
```

becomes:

```js
items.each(
    (item) => {
        print(item)
    }
)
```

and:

```js
condition.ifTrue() {
    foo()
}
```

becomes:

```js
condition.ifTrue(
    () => {
        foo()
    }
)
```

Trailing closures introduce no new runtime concept.

## 20. Object Construction vs Trailing Closure

The following distinction is intentional:

```js
foo { ... }       // object whose parent is foo
foo() { ... }     // call foo with a trailing closure
```

A trailing closure therefore requires a preceding call.

This removes ambiguity between object construction and block passing.

## 21. Operators

Operators are syntactic forms for message sends where appropriate.

For example:

```js
a + b
```

conceptually dispatches the `+` behavior on `a`.

Core precedence, highest to lowest:

```text
postfix:
    .
    ()

unary:
    !
    -
    +

multiplicative:
    *
    /
    %

additive:
    +
    -

comparison:
    <
    <=
    >
    >=

equality:
    ==
    ===
    !=
    !==

logical:
    &&
    ||

non-local return:
    ^

slot creation / modification:
    :
    =
```

Binary operators associate left-to-right unless otherwise specified.

`:` and `=` are right-associative.

Both slot creation and assignment evaluate to the value written.

## 22. Equality Lowering

Semantic equality:

```js
a == b
```

is dispatchable object behavior.

Identity:

```js
a === b
```

is a non-overridable runtime identity operation. Its result is defined by the language semantics rather than by physical allocation or host-language reference identity. Built-in immutable value objects such as `Number`, `String`, `Boolean`, and `null` use value identity; ordinary identity-bearing objects use individual object identity.

Examples:

```js
1 === 1                    // true
"hello" === "hello"        // true
("hel" + "lo") === "hello" // true

{ x: 1 } === { x: 1 }     // false
```

`String` is immutable; syntax never denotes an in-place mutation of a String value.

```js
a !== b
```

means:

```text
!(a === b)
```

and:

```js
a != b
```

means:

```text
!(a == b)
```

## 23. Boolean Operator Laziness

`&&` and `||` preserve lazy evaluation.

Conceptually:

```js
a && b
```

lowers to:

```text
a.and(() => b)
```

and:

```js
a || b
```

lowers to:

```text
a.or(() => b)
```

## 24. Composition Syntax

```ebnf
composition-expression =
    "...", expression;
```

Example:

```js
duck: animal {
    ...flyable
    ...swimmable

    name: "Donald"
}
```

Composition occurs while constructing the object and does not modify its delegation chain.

If composed sources contribute conflicting slots, the conflict is an error unless an explicit local declaration resolves it.

## 25. Uniform Object Bodies

Object bodies and executable bodies intentionally use the same expression grammar.

```js
object: {
    x: 10
    y: 20

    total: () => {
        x + y
    }
}
```

A body may contain ordinary expressions executed during construction.

`{}` is not a special declaration language.


## 25.1 Reflective Object Messages

Slot removal, closing, and freezing introduce no special syntax. Calls such as:

```js
object.removeSlot("name")
object.close()
object.freeze()
```

are parsed as ordinary message sends. `removeSlot` is inherited from the standard root prototype `Object` and affects only a local slot of the receiver; its structural semantics are defined by the runtime specification.

## 26. Objects vs Closures

A bare:

```js
{
    ...
}
```

is an object expression.

It is not a closure.

A closure requires:

```js
() => {
    ...
}
```

Therefore:

```js
x: {
    value: 10
}
```

stores an object, while:

```js
x: () => {
    value
}
```

stores a closure.

## 27. Unqualified Lookup Lowering

Unqualified:

```js
name
```

does not literally desugar to `this.name`, because lexical contexts are searched first.

Conceptually:

```text
Lookup("name", currentActivation)
```

Lookup searches lexical contexts before the receiver/delegation chain.

## 28. Slot Creation Lowering

```js
x: value
```

inside an activation conceptually becomes:

```text
CreateSlot(context, "x", value)
```

while:

```js
this.x: value
```

becomes:

```text
CreateSlot(this, "x", value)
```

and:

```js
obj.x: value
```

becomes:

```text
CreateSlot(obj, "x", value)
```

## 29. Assignment Lowering

```js
x = value
```

conceptually:

```text
target = ResolveWritableSlot("x", currentActivation)

if target does not exist:
    signal SlotNotFound

WriteSlot(target, value)
```

Explicit member assignment:

```js
obj.x = value
```

requires `obj` to have a local writable `x`. Delegation is not used for writing.

## 30. Member Read Lowering

```js
obj.x
```

conceptually performs:

```text
LookupSlot(
    receiver = obj,
    name = "x"
)
```

Lookup may traverse the parent chain.

Failure signals `SlotNotFound`; it never yields `null` merely because the slot is absent.

## 31. Method Invocation Lowering

```js
dog.speak()
```

must preserve both receiver and lookup origin.

Conceptually:

```text
Send(
    receiver = dog,
    message = "speak",
    arguments = []
)
```

Lookup returns at least:

```text
value
methodHome
```

Invocation then knows:

```text
this       = dog
methodHome = object where speak was found
```

This is required for correct `this` and `super` behavior.

## 32. Extracted Closure / Method Lowering

Protia has no separate `Method` value type. A closure stored in a slot remains a `Closure`; method behavior arises from receiver-aware lookup and invocation.

```js
f: dog.speak
```

reads the closure-valued slot without executing it and produces a receiver-bound closure view/value carrying the receiver and lookup origin.

Conceptually it retains:

```text
closure
receiver   = dog
methodHome = lookup result origin
```

Therefore `f()` retains the original receiver.

## 33. Closure Creation Lowering

Creating:

```js
(x) => {
    body
}
```

produces behavior equivalent to a closure carrying:

```text
code
lexicalContext
capturedThis
capturedMethodHome
capturedReturnHome
```

The exact representation is implementation-defined.

Contexts are captured by reference.


When a closure literal is evaluated while constructing an object, the object under construction is **not** captured as a lexical environment merely because it is the current slot-creation context. The closure captures the genuine enclosing lexical context instead. Object slots referenced by a method are resolved through the dynamic receiver (`this`) and its delegation chain after lexical lookup.

Thus a method declared in `animal` and invoked through `dog` does not lexically bind bare slot names to `animal`; it observes `dog` first through ordinary receiver lookup.

## 34. Method Installation

A closure installed as an object slot acts as an unbound method template for message sends.

When reached through:

```js
dog.speak()
```

message dispatch supplies the receiver dynamically.

Closures created during that invocation capture the resulting `this`.

## 35. Non-local Return Lowering

```js
^value
```

conceptually performs:

```text
NonLocalReturn(
    target = activation.returnHome,
    value  = value
)
```

The return home follows the Smalltalk/Squeak model:

- a module-level function invocation establishes a fresh return home;
- a method invocation establishes a fresh return home;
- a closure created inside an active function or method captures that home;
- calling such a nested closure as an ordinary block preserves the captured home rather than establishing a new one.

Therefore a `^` inside nested trailing closures may unwind across those closures to the function or method invocation that lexically owns them.

If the captured home activation has already completed, `InvalidReturn` is signaled.

This is control flow, not an ordinary message send.

## 36. Optional Control-flow Sugar

The following forms are not part of Core Grammar v0.1 but may later be defined as sugar.

```js
if (condition) {
    yes()
}
```

may lower to:

```js
condition.ifTrue() {
    yes()
}
```

A `while` form may lower to a message sent to a condition closure.

```js
return value
```

may lower exactly to:

```js
^value
```

There is no second return mechanism.

## 37. Futures and Errors Require No New Grammar

Asynchronous execution:

```js
work.future()
```

is ordinary message syntax.

Waiting:

```js
future.value()
```

is ordinary message syntax.

Error signaling:

```js
error.signal()
```

is ordinary message syntax.

The parser therefore requires no `async`, `await`, `try`, `catch`, `throw`, or `finally` constructs.

## 38. Comments

```ebnf
line-comment =
    "//", { any-character-except-newline };

block-comment =
    "/*",
    { any-character },
    "*/";
```

Comments behave as whitespace.

Block comments do not nest.

## 39. Compact EBNF

```ebnf
program =
    expression-sequence ;

expression-sequence =
    [ expression,
      { separator, expression },
      [ separator ] ] ;

separator =
      ";"
    | newline ;

expression =
      slot-creation
    | assignment
    | non-local-return
    | binary-expression ;

slot-creation =
    assignable, ":", expression ;

assignment =
    assignable, "=", expression ;

non-local-return =
    "^", expression ;

assignable =
      identifier
    | member-expression ;

binary-expression =
    logical-or-expression ;

logical-or-expression =
    logical-and-expression,
    { "||", logical-and-expression } ;

logical-and-expression =
    equality-expression,
    { "&&", equality-expression } ;

equality-expression =
    comparison-expression,
    { equality-operator, comparison-expression } ;

equality-operator =
      "=="
    | "!="
    | "==="
    | "!==" ;

comparison-expression =
    additive-expression,
    { comparison-operator, additive-expression } ;

comparison-operator =
      "<"
    | "<="
    | ">"
    | ">=" ;

additive-expression =
    multiplicative-expression,
    { additive-operator, multiplicative-expression } ;

additive-operator =
      "+"
    | "-" ;

multiplicative-expression =
    unary-expression,
    { multiplicative-operator, unary-expression } ;

multiplicative-operator =
      "*"
    | "/"
    | "%" ;

unary-expression =
      unary-operator, unary-expression
    | postfix-expression ;

unary-operator =
      "!"
    | "+"
    | "-" ;

postfix-expression =
    primary-expression,
    { postfix-operation } ;

postfix-operation =
      ".", identifier
    | argument-list ;

primary-expression =
      literal
    | identifier
    | intrinsic-reference
    | super-message-send
    | object-expression
    | closure-expression
    | parenthesized-expression ;

intrinsic-reference =
      "this"
    | "context"
    | "null"
    | "true"
    | "false" ;

parenthesized-expression =
    "(", expression, ")" ;

object-expression =
      object-body
    | parent-expression, object-body ;

object-body =
    "{", expression-sequence, "}" ;

parent-expression =
      identifier
    | intrinsic-reference
    | member-expression
    | parenthesized-expression ;

closure-expression =
    parameter-list, "=>", object-body ;

parameter-list =
    "(", [ parameters ], ")" ;

parameters =
    identifier,
    { argument-separator, identifier } ;

argument-list =
    "(", [ arguments ], ")" ;

arguments =
    expression,
    { argument-separator, expression } ;

argument-separator =
      ","
    | newline ;

trailing-closure =
    [ parameter-list ], object-body ;

composition-expression =
    "...", expression ;

literal =
      number-literal
    | string-literal
    | "true"
    | "false"
    | "null" ;
```

The parser handles trailing closures contextually: `{ ... }` following a call is a trailing closure; `{ ... }` following a parent expression without a call is object construction.

## 40. Canonical AST

After parsing and mandatory desugaring, the language can be reduced to a small canonical AST such as:

```text
Literal(value)

Lookup(name)

Member(receiver, name)

Create(target?, name, value)

Assign(target?, name, value)

Object(parent, body)

Closure(parameters, body)

Send(receiver, message, arguments)

Call(closure, arguments)

Sequence(expressions)

Compose(object)

Return(value)
```

For example:

```js
dog.speak(1, 2)
```

lowers to:

```text
Send(
    receiver = Lookup("dog"),
    message = "speak",
    arguments = [
        Literal(1),
        Literal(2)
    ]
)
```

while:

```js
f(1, 2)
```

lowers to:

```text
Call(
    closure = Lookup("f"),
    arguments = [
        Literal(1),
        Literal(2)
    ]
)
```

This distinction preserves the language's receiver semantics without runtime hacks.

## 41. Syntactic Core

The genuinely special core syntax is intentionally small:

```text
{}       object construction/context
() => {} closure creation

:        slot creation
=        slot modification

.        explicit member lookup
()       invocation

^        non-local return

...      slot composition
```

Control flow, iteration, errors, futures, collections, numbers, and strings can largely be expressed as object behavior and message sends.