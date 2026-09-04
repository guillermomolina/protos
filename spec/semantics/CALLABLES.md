# Protos Callables v0.1

Language version: 0.1
Document revision: 330
Status: Draft
Last updated: 2026-09-04

This document is the primary normative owner of Closures, methods, invocation, arguments, receiver binding, trailing-call closure semantics, custom symbolic message-send semantics, and return/control behavior owned by callable execution.

The material below is migrated without intended semantic change from `../PROTOS_LANGUAGE_SPEC.md`. Legacy section titles and numbering are retained so existing references remain understandable.

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
