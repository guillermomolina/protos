# Core Language Grammar v0.1

Language version: 0.1  
Document revision: 46  
Status: Draft  
Last updated: 2026-08-31


## Prelude Binding Note

Prelude bindings introduce no additional grammar. The shared standard prelude is frozen by runtime semantics. Therefore `name = value` cannot modify a binding found only in the prelude; `name: value` creates a local slot and may explicitly shadow that name.

## 1. Scope

This document defines the lexical grammar, expression grammar, precedence rules, and mandatory syntactic desugarings of the language.

It does not redefine the object model or runtime semantics specified in `PROTOS_LANGUAGE_SPEC.md`.

The grammar is written in EBNF:

```text
{ x }     zero or more repetitions
[ x ]     optional
x | y     alternative
"..."     literal token
```

## 2. Identifiers

Protos identifiers are Unicode-aware and case-sensitive.

An identifier must begin with:
- `_` (underscore), OR
- A Unicode character with the `XID_Start` property.

Subsequent characters must have the Unicode `XID_Continue` property.

Decimal digits are therefore allowed after the first character when permitted by `XID_Continue`.

Every identifier must be in Unicode NFC (Canonical Decomposition, Followed by Canonical Composition) normalization form. Implementations must reject non-NFC identifiers rather than silently normalizing them.

Examples:

```text
name
_private
café
año
π
日本語
```

Identifier normalization applies to identifier spelling only and does not imply normalization of `String` values.

Reserved intrinsic identifiers:

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

Reserved-word recognition happens after lexical identifier recognition. The lexer first recognizes a valid Unicode identifier according to the identifier rules above. If the identifier spelling exactly matches one of the reserved words, it is tokenized as that reserved word rather than as an ordinary identifier. Reserved-word matching is case-sensitive. For example, `this` is reserved but `This` is an ordinary identifier.

Reserved words cannot be used as ordinary identifier names where the grammar expects an identifier.

Names provided by the standard prelude, such as `Object`, `Future`, `Number`, `String`, `Map`, or `IdentityMap`, are not reserved words. Error object names are not reserved.

Core v0.1 defines no additional reserved words such as `if`, `else`, `while`, `for`, `class`, `function`, `try`, `catch`, `throw`, `async`, or `await`.

## 3. Literals

```ebnf
literal =
      number-literal
    | string-literal
    | "true"
    | "false"
    | "null";

string-literal =
      single-quoted-string
    | double-quoted-string
    | triple-double-quoted-string ;
```

Exact number formats are defined separately from the core string escape set.

The backslash escape is displayed unambiguously as `\\`.

Core v0.1 String escape sequences are exactly:

```text
\\
\'
\"
\n
\r
\t
\b
\f
\u{HEX}
```

`\u{HEX}` requires 1 to 6 hexadecimal digits and must denote a valid Unicode scalar value.

Invalid or incomplete escape sequences are syntax errors. Octal escapes are not supported. `\xNN` escapes are not supported.

Single-quoted, double-quoted, and triple-double-quoted String literals use the same escape rules. Triple-double-quoted strings are multiline String literals, not raw strings. Triple-single-quoted strings are not supported.

**Newline Handling:**

Single-quoted and double-quoted String literals are single-line literals. A raw source newline is not permitted inside a single-quoted or double-quoted String literal. Encountering a raw newline before the matching closing quote is a lexical error. Newline characters may be represented in these literals using the existing `\n` and `\r` escape sequences.

Triple-double-quoted String literals are the Core v0.1 syntax for source-level multiline text. Raw newlines are permitted and are part of the literal content, subject to the multiline indentation normalization rule.

Protos has no separate character literal or character type. `'a'` and `"a"` both evaluate to a String containing the single-character text `a`.

String interpolation is not part of Core v0.1. Inside a String, `${...}` has no special meaning and is treated as ordinary literal text.

For triple-double-quoted String literals, indentation normalization is defined as follows:

- If the opening `"""` is immediately followed by a newline, that newline is not part of the resulting String.
- If the closing `"""` is immediately preceded by a newline whose preceding content on that line is only indentation whitespace, that final newline and indentation are not part of the resulting String.
- Determine the minimum common indentation of all non-empty content lines.
- Remove that common indentation from every content line.
- Empty lines do not participate in determining the common indentation.
- Relative indentation beyond the common indentation is preserved.
- Escape processing follows the already-defined Core v0.1 String escape rules.
- Triple-double-quoted strings remain non-raw strings.
- A multiline String may also begin or end on the same line as its delimiters; in that case no implicit leading or trailing newline removal applies.

Examples:

```js
"""
    hello
    world
"""
```

produces:

```text
hello
world
```

and:

```js
"""
    hello
        world
"""
```

produces:

```text
hello
    world
```

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

A comma is not an expression separator. It is reserved for list-like syntactic forms such as arguments and parameters. Thus:

```js
foo()
bar()
baz()
```

is equivalent to:

```js
foo(); bar(); baz()
```

The same rule applies inside object bodies:

```js
point: { x: 10; y: 20 }
```

A comma cannot be substituted for `;` here.

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


> Root invariant: `Object` is the only object without a delegation parent; every other object has exactly one.

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

A closure uses a closure body. A closure body contains ordinary expressions; object-composition items are not valid merely because braces are used.

```ebnf
closure-expression =
    parameter-list, "=>", closure-body ;

closure-body =
    "{", expression-sequence, "}" ;

parameter-list =
    "(", [ parameter-items ], ")" ;

parameter-items =
      rest-parameter
    | parameter, { argument-separator, parameter },
      [ argument-separator, rest-parameter ] ;

parameter =
    identifier, [ "=", expression ] ;

rest-parameter =
    "...", identifier ;
```

A rest parameter, when present, is final. Parameter names within one parameter list must be unique. Duplicate names, including collisions with the rest parameter name, are rejected during parsing or static validation.

## 17. Calls and Arguments

```ebnf
argument-list =
    "(", [ argument-items ], ")" ;

argument-items =
    argument, { argument-separator, argument } ;

argument =
      expression
    | "...", expression ;

argument-separator =
      ","
    | newline ;
```

Argument expressions, including spread operands, are evaluated left-to-right.

The reserved intrinsic `args` is not call syntax. It is supplied by invocation runtime semantics and is not an ordinary writable identifier.

## 18. Member Access, Calls, Indexing, and Postfix Expressions

Postfix operations have high precedence and associate left-to-right.

```ebnf
postfix-expression =
    primary-expression,
    { postfix-operation } ;

postfix-operation =
      member-suffix
    | call-suffix
    | index-suffix ;

member-suffix =
    ".", identifier ;

call-suffix =
    argument-list, [ trailing-closure ] ;

index-suffix =
    "[", expression, "]" ;
```

Examples:

```js
dog.speak()
foo().bar().baz
matrix[row][column]
objects[index].name
factory()[index]
```

Bracket syntax lowers to the ordinary `at` / `atPut` protocol.

Receiver-aware semantic lowering still distinguishes a member invocation such as `dog.speak()` from a plain invocation such as `f()`, so that `this` and `methodHome` are preserved correctly.

## 19. Trailing Closures

A trailing closure is permitted only as part of a call suffix.

```ebnf
trailing-closure =
    [ parameter-list ], closure-body ;
```

Examples:

```js
items.each(item) {
    print(item)
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

A trailing closure introduces no new runtime concept.

## 20. Object Construction vs Trailing Closure

The distinction is intentional:

```js
foo { ... }       // object whose parent is foo
foo() { ... }     // invoke foo with a trailing closure
```

`foo() { ... }` is not a combined object-construction form. It desugars as a call whose final argument is a closure.

Likewise, if:

```js
Point(args) { ... }
```

is valid under trailing-closure syntax, it means invocation of `Point` with a trailing closure. It never means "construct Point(args) and then evaluate this object body".

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

## 21.1 Custom Binary Operators

Custom symbolic binary operators are ordinary message-send syntax.

All custom binary operators share one precedence level with each other and associate left-to-right.

```js
a @ b |> c
```

parses as:

```js
(a @ b) |> c
```

There is intentionally no implicit precedence relationship between custom binary operators and standard binary operator groups. Therefore mixed unparenthesized forms such as these are syntax errors:

```js
a + b @ c
a @ b * c
```

Explicit grouping is required:

```js
(a + b) @ c
a @ (b * c)
```

Parser precedence cannot be changed at runtime or by modules/imports. The lexical character set for custom symbolic operators is fixed by the Custom Operator Lexing rules.

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

Composition is valid only as an object-body item.

```ebnf
object-body =
    "{", object-body-sequence, "}" ;

object-body-sequence =
    [ object-body-item,
      { separator, object-body-item },
      [ separator ] ] ;

object-body-item =
      composition-item
    | expression ;

composition-item =
    "...", expression ;
```

Example:

```js
duck: animal {
    ...flyable
    ...swimmable

    name: "Donald"
}
```

The expression following `...` evaluates to an ordinary object. There is no separate trait declaration or trait value category.

`...` is not a standalone expression operator. Its object-composition meaning exists only while parsing an object body.

Composition semantics, including binding copying and conflict resolution, are defined by the language/runtime specification.

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

The language has no separate `Method` value type. A closure stored in a slot remains a `Closure`; method behavior arises from receiver-aware lookup and invocation.

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
    "//", { any-character-except-newline } ;

block-comment =
    "/*",
    { any-character-except-block-end },
    "*/" ;
```

`//` starts a line comment. A line comment continues until the next newline or end of file.

`/*` starts a block comment. `*/` ends a block comment. Block comments do not nest in Core v0.1.

An unterminated block comment is a lexical error.

Comment delimiters inside String literals have no special meaning.

Comments are lexically equivalent to whitespace and do not produce language-level values.

`#` is not a comment delimiter.

Core v0.1 defines no special documentation-comment syntax.

## 39. Compact EBNF

The compact grammar below incorporates the syntax decisions made through revision 24. Semantic validation still applies after parsing.

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
    | assignable-postfix-expression ;

assignable-postfix-expression =
    primary-expression,
    { postfix-operation },
    assignable-postfix-operation ;

assignable-postfix-operation =
      ".", identifier
    | "[", expression, "]" ;

binary-expression =
      logical-or-expression
    | custom-binary-expression ;

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

custom-binary-expression =
    unary-expression,
    custom-binary-operator,
    unary-expression,
    { custom-binary-operator, unary-expression } ;

unary-expression =
      unary-operator, unary-expression
    | postfix-expression ;

unary-operator =
      "!"
    | "-" ;

postfix-expression =
    primary-expression,
    { postfix-operation } ;

postfix-operation =
      ".", identifier
    | call-suffix
    | "[", expression, "]" ;

call-suffix =
    argument-list, [ trailing-closure ] ;

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
    | "args"
    | "null"
    | "true"
    | "false" ;

super-message-send =
    "super", ".", identifier, argument-list ;

parenthesized-expression =
    "(", expression, ")" ;

object-expression =
      object-body
    | parent-expression, object-body ;

object-body =
    "{", object-body-sequence, "}" ;

object-body-sequence =
    [ object-body-item,
      { separator, object-body-item },
      [ separator ] ] ;

object-body-item =
      composition-item
    | expression ;

composition-item =
    "...", expression ;

parent-expression =
      identifier
    | intrinsic-reference
    | member-expression
    | parenthesized-expression ;

member-expression =
    primary-expression,
    { postfix-operation },
    ".", identifier ;

closure-expression =
    parameter-list, "=>", closure-body ;

closure-body =
    "{", expression-sequence, "}" ;

parameter-list =
    "(", [ parameter-items ], ")" ;

parameter-items =
      rest-parameter
    | parameter, { argument-separator, parameter },
      [ argument-separator, rest-parameter ] ;

parameter =
    identifier, [ "=", expression ] ;

rest-parameter =
    "...", identifier ;

argument-list =
    "(", [ argument-items ], ")" ;

argument-items =
    argument, { argument-separator, argument } ;

argument =
      expression
    | "...", expression ;

argument-separator =
      ","
    | newline ;

trailing-closure =
    [ parameter-list ], closure-body ;

literal =
      number-literal
    | string-literal
    | "true"
    | "false"
    | "null" ;
```

A parser may implement the expression portion using recursive descent plus Pratt parsing. Custom symbolic operators form their own precedence domain: mixing them with standard binary operators requires parentheses.

Indexed assignment is recognized because an assignable postfix expression may end in `[ expression ]`; it lowers to `atPut` rather than a slot `Assign`.

Composition is intentionally connected only through `object-body-item`, preserving the contextual meaning of `...`.

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

Call(receiver, arguments)

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
    receiver = Lookup("f"),
    arguments = [
        Literal(1),
        Literal(2)
    ]
)
```

This distinction preserves the language's receiver semantics without runtime hacks.

## 40.1 Dynamic Typing Grammar Note

Core Grammar v0.1 defines no mandatory type-annotation syntax for slots, parameters, return values, or expressions.

Dynamic typing is a language semantic property rather than a parser feature. Tooling may infer types without changing this grammar or runtime behavior.

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

## Conditional Protocol Note

The grammar introduces no truthiness conversion and no Boolean-only receiver restriction for conditional messages.

Conditional behavior is ordinary message syntax. Where logical infix operators are supported, they desugar lazily:

```js
a && b
a || b
```

to the semantic equivalent of:

```js
a.and(() => b)
a.or(() => b)
```

The grammar does not require the receiver of `and`, `or`, `ifTrue`, or `ifFalse` to be `true` or `false`. Receiver behavior is determined by ordinary message lookup.


## Error Handling Syntax Note

Core Grammar v0.1 introduces no mandatory `try`, `catch`, `throw`, or `finally` syntax.

Error objects are signaled and handlers are dynamically installed through the object/runtime protocol. The exact convenience syntax, if any, for installing handlers is intentionally left outside the core grammar at this stage.

Handler matching by delegation and unwinding behavior are runtime semantics and require no special parser production.


## Module Import Grammar Note

Core Grammar v0.1 does not require dedicated `import` or `export` syntax.

A module-loading facility may be exposed through ordinary call/message syntax, for example:

```js
module: import("./module.pt")
```

The parser treats the module specifier expression like any other argument expression. Canonical module identity, caching, initialization states, cycle detection, and host-specific resolution are runtime/module-loader semantics rather than grammar rules.

No grammar rule implicitly injects imported bindings into the current lexical scope.


## Indexed Access

Bracket indexing is postfix syntax.

Conceptually:

```ebnf
indexed-read =
    postfix-expression, "[", expression, "]" ;
```

Semantic lowering:

```js
receiver[index]
```

becomes:

```text
Send(receiver, "at", [index])
```

Assignment to an indexed expression is recognized specially at the syntactic lowering boundary:

```js
receiver[index] = value
```

becomes:

```text
Send(receiver, "atPut", [index, value])
```

rather than a slot `Assign` node.

Indexed access has the same high postfix-binding role as member access and calls. Chained forms are permitted when otherwise grammatically valid, for example:

```js
matrix[row][column]
objects[index].name
factory()[index]
```

The receiver expression and index expression are each evaluated exactly once.


## Parameters, Rest Parameters, and Argument Spread

Closure parameter lists may contain ordinary parameters, parameters with defaults, and at most one trailing rest parameter.

The normative productions are defined in Sections 16-17 and in the Compact EBNF. A rest parameter must be the final parameter. Defaults, rest capture, and spread are part of the canonical grammar rather than post-parse extensions.

Call argument lists may contain ordinary arguments and spread arguments:

```ebnf
argument =
    expression
  | "...", expression ;
```

For:

```js
f(a, ...values, z)
```

argument expressions are evaluated left-to-right. A spread argument contributes the elements of its evaluated collection in iteration/index order defined by the spread protocol.

The reserved intrinsic `args` is not special call syntax; it is an invocation-context binding exposed by runtime semantics and is not an ordinary writable identifier.

Default expressions are evaluated when an argument is absent, in parameter-binding order, in the invocation's execution context.


## Polymorphic Invocation Syntax

Call syntax is not restricted grammatically to closures. Invocation is represented canonically by the `call-suffix` postfix production defined above.

Any evaluated receiver expression may syntactically appear in call position:

```js
f(1, 2)
Point(10, 20)
factory(...args)
```

Whether the resulting object supports invocation is determined by the ordinary runtime call protocol.

Object-literal construction remains separately expressed as:

```js
Parent {
    ...
}
```

Core v0.1 does not interpret:

```js
Parent(args) {
    ...
}
```

as a combined construction form. When accepted by the trailing-closure grammar, the braces are a trailing closure argument to `Parent(args)`.

## Contextual Ellipsis

The token `...` is valid only in structural contexts defined by the grammar:

```text
parameter list   rest capture
argument list    argument spread
object body      slot composition
```

Examples:

```js
foo: (...args) => { ... }
foo(...args)

obj: {
    ...source
}
```

Standalone forms such as these are not valid expressions:

```js
x: ...value
return ...value
```


## Resource Cleanup Syntax Note

Core Grammar v0.1 introduces no destructor syntax and no mandatory `try` / `finally` construct.

Cleanup may be exposed through ordinary message syntax such as:

```js
body.ensure(cleanup)
```

or through higher-level library protocols built from closures and ordinary sends.

The guaranteed execution of cleanup during scope exit is runtime control-flow semantics, not a parser-level special form.


## Numeric Literals

A leading sign is never part of a numeric literal. Prefix `-` and prefix `!` are ordinary operators, not numeric-literal syntax.

Decimal integer literals use digits `0` through `9`.

Leading zeroes are allowed and have no radix significance. For example, `007` is decimal `7`.

Hexadecimal integer literals use `0x` or `0X`.

Binary integer literals use `0b` or `0B`.

Octal integer literals use `0o` or `0O`.

`_` may be used as a visual separator between digits. It cannot appear at the beginning or end of a digit sequence and cannot appear consecutively.

Radix-prefixed literals produce `Integer` values.

Decimal literals containing a decimal point or exponent produce `Float` values.

A decimal point requires at least one digit on both sides:

```js
1.0    // valid
1.     // invalid numeric literal
.5     // invalid numeric literal
```

Decimal exponents use `e` or `E`, optionally followed by `+` or `-`, and require at least one exponent digit:

```js
1e10
1e-10
1.5e+20
```

Hexadecimal, binary, and octal Float literals are not supported in Core v0.1.

Numeric type suffixes such as `L`, `f`, or `d` are not supported.

`NaN` and `Infinity` are not special numeric literal syntax.

Examples:

```js
0
007
0xFF
0b1010
0o77
1_000
1.5
2e3
1.5e-3
```

## String Literals and Byte Representation Note

Single-quoted and double-quoted forms are equivalent String literals:

```js
"hello"
'hello'
```

A String literal denotes a `String` value, not an encoded byte sequence. Protos has no separate character literal or character type.

The standard Protos escape sequences are exactly:

```text
\
\'
\"
\n
\r
\t
\b
\f
\u{HEX}
```

`\u{HEX}` requires 1 to 6 hexadecimal digits and must denote a valid Unicode scalar value. Invalid or incomplete escape sequences are syntax errors. Octal escapes and `\xNN` escapes are not supported.

The same escape rules apply to single-quoted, double-quoted, and triple-double-quoted String literals. Triple-double-quoted strings are multiline String literals, not raw strings:

```js
"""
line one
line two
"""
```

Triple-single-quoted strings are not supported.

String interpolation is not supported in Core v0.1. `${...}` is literal text inside a String and carries no special meaning.

For triple-double-quoted String literals, indentation normalization follows the Core v0.1 rule defined above: remove the common leading indentation of all non-empty content lines, preserve relative indentation beyond that common baseline, and omit the leading/trailing newline only when the delimiter placement matches the rule. Escape processing still follows the standard Core v0.1 String escape rules, and triple-double-quoted strings remain non-raw strings.

Character encoding is not determined by the source-level string literal syntax. Conversion to or from encoded bytes is performed explicitly through ordinary protocols such as:

```js
text.encode(UTF8)
bytes.decode(UTF8)
```

Core Grammar v0.1 does not require separate literal forms for UTF-8, UTF-16, or other text encodings.


## String and Bytes Indexing Note

Indexed syntax remains ordinary protocol sugar:

```js
text[i]        // text.at(i)
bytes[i]       // bytes.at(i)
bytes[i] = v   // bytes.atPut(i, v)
```

For `String`, `at` indexes Unicode grapheme clusters.

`String` is immutable and therefore does not provide ordinary mutation through indexed assignment.

`Bytes` is mutable and may provide both `at` and `atPut`.

Other encoded or external string-like representations determine their own indexed-access and mutability behavior through the messages they implement.


## Map Indexing Note

Maps require no dedicated indexing grammar.

The existing indexed-access lowering applies unchanged:

```js
map[key]          // map.at(key)
map[key] = value  // map.atPut(key, value)
```

Missing-key behavior, equality, hashing, and iteration order are collection protocol semantics rather than parser semantics.

## Equality and Comparison Result Contract Note

The grammar does not encode result types, but the standard equality and comparison operators have a semantic Boolean-result contract:

```text
==  !=  <  <=  >  >=
```

Each operation returns canonical `true` or `false`, or signals an error. No truthiness conversion is applied to arbitrary returned objects.

## Numeric Equality Grammar Note

No additional syntax is required for cross-family numeric equality. The standard `==` and `===` operators retain their existing grammar and precedence.

Their numeric semantics differ:

```text
==   mathematical numeric value
===  numeric value plus semantic numeric family
```

The exact Float special cases for NaN and signed zero remain semantic questions, not grammar questions.

## Float Special Values Grammar Note

Core v0.1 introduces no required `NaN`, `Infinity`, or `-Infinity` literals or keywords.

NaN and infinities are semantic Float values. They may be obtained through ordinary Float protocol or produced by floating-point operations.

The grammar therefore requires no new reserved words for these values.

## Float Signed Zero Grammar Note

Signed-zero behavior requires no new grammar. `-0.0` is parsed through the ordinary unary-minus and Float-literal rules.

Its semantics are:

```text
0.0 == -0.0   -> true
0.0 === -0.0  -> false
```

## Parameter Name Validation

The EBNF describes the syntactic shape of parameter lists. A semantic validation rule additionally requires every parameter name in one list to be unique, including the rest parameter.

```js
(a, a) => { ... }       // invalid
(a, ...a) => { ... }    // invalid
```

This validation occurs before execution.

## Custom Operator Lexing

**Ellipsis Token:**

`...` is a single lexical token representing three consecutive periods. It is recognized greedily and is not parsed as three separate `.` tokens. Its meaning remains context-dependent according to the existing grammar:

- Rest parameter syntax
- Argument spread
- Object composition

`..` (two periods) has no special Core v0.1 meaning. A single `.` remains punctuation used by the existing grammar, including member access and decimal point literals where applicable.

**Maximal-Munch Tokenization:**

Core v0.1 uses maximal-munch tokenization for symbolic operators. When multiple valid symbolic operator tokens can begin at the same source position, the lexer must consume the longest valid token.

Standard punctuation and structural tokens defined by the grammar, such as parentheses, braces, brackets, commas, semicolons, colons, and periods, are tokenized separately from symbolic operators. The `...` ellipsis token is handled before ordinary period tokenization.

**Custom Operator Character Alphabet:**

Custom symbolic binary operators use the fixed character alphabet:

```text
! $ % & * + - / < = > ? @ \ ^ | ~
```

Structural punctuation is excluded:

```text
. : ; , ( ) { } [ ]
```

The lexer must prefer reserved and standard tokens before producing `CUSTOM_OPERATOR`.

Reserved and standard symbolic tokens include:

```text
=>  =  ==  ===  !=  !==  <=  >=  &&  ||
+   -  *   /   %   <   >
```

Any remaining non-empty sequence made exclusively from operator characters may be tokenized as `CUSTOM_OPERATOR`. The characters `.`, `:`, and `;` never participate in a custom operator token.

Conceptually:

```ebnf
operator-character =
      "!" | "$" | "%" | "&" | "*" | "+"
    | "-" | "/" | "<" | "=" | ">" | "?"
    | "@" | "\\" | "^" | "|" | "~" ;

custom-binary-operator =
    operator-character, { operator-character } ;
```

After token formation, reserved and standard operator spellings are classified according to their dedicated grammar roles rather than as custom operators.

## Decoding Policy Grammar Note

Malformed-text handling introduces no special syntax.

Examples such as:

```js
bytes.decode(UTF8)
bytes.decode(UTF8, ReplaceInvalid)
```

use ordinary message-send and argument syntax. Strict decoding is the default semantic behavior; tolerant recovery is selected explicitly through ordinary objects/protocol arguments.

## Standard Encoding Names Grammar Note

The standard encoding names:

```text
UTF8 UTF16LE UTF16BE UTF32LE UTF32BE ASCII Latin1
```

are ordinary prelude/library bindings rather than grammar keywords. No new lexical category is introduced for them.

## Error Handler Installation Grammar Note

Dynamic error handling introduces no special grammar.

```js
protectedClosure.handle(errorPrototype, handlerClosure)
```

is an ordinary message send using existing closure and argument syntax. The language does not require `try`, `catch`, or `throw` syntax for Core v0.1.

## Future and Concurrency Grammar Note

Future creation, cancellation, waiting, and error observation require no special syntax.

Examples:

```js
future: work.future()
future.cancel()
value: future.value()
```

These are ordinary message sends. Concurrency memory semantics and cancellation behavior are runtime concerns rather than grammar features.


## Reflection Grammar Note

Core reflection introduces no dedicated syntax.

```js
object.hasSlot("name")
object.slotNames()
object.slotValue("name")
object.parent()
```

are ordinary message sends parsed by the existing member/call grammar.
