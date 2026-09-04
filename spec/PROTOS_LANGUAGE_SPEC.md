# Core Language Specification v0.1

Language version: 0.1  
Document revision: 327
Status: Draft  
Last updated: 2026-09-04
Normative I/O-domain semantics are defined in `io/IO_CORE.md`.

Normative semantic-domain ownership is modularized under `semantics/`: `OBJECT_MODEL.md`, `EXECUTION_AND_CONTROL.md`, `CALLABLES.md`, `MODULES.md`, `ERRORS.md`, and `VALUES_AND_COLLECTIONS.md`. Compatibility headings retained in this document are navigation only.


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

The primary normative contract formerly contained here has moved to `semantics/OBJECT_MODEL.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## 3. Slot Creation and Modification

The primary normative contract formerly contained here has moved to `semantics/OBJECT_MODEL.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## 4. Execution Context

The primary normative contract formerly contained here has moved to `semantics/EXECUTION_AND_CONTROL.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## Module Contexts and Top-Level Bindings

The primary normative contract formerly contained here has moved to `semantics/MODULES.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## 5. `this`

The primary normative contract formerly contained here has moved to `semantics/EXECUTION_AND_CONTROL.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## 6. Unqualified Lookup

The primary normative contract formerly contained here has moved to `semantics/EXECUTION_AND_CONTROL.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## 7. Unqualified Assignment

The primary normative contract formerly contained here has moved to `semantics/EXECUTION_AND_CONTROL.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## 8. `super`

The primary normative contract formerly contained here has moved to `semantics/EXECUTION_AND_CONTROL.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## 8.1 Evaluation Order

The primary normative contract formerly contained here has moved to `semantics/EXECUTION_AND_CONTROL.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## 9. Closures

The primary normative contract formerly contained here has moved to `semantics/EXECUTION_AND_CONTROL.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## 10. Closures and Methods

The primary normative contract formerly contained here has moved to `semantics/CALLABLES.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## 11. Extracted Methods

The primary normative contract formerly contained here has moved to `semantics/CALLABLES.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## 12. Closures and `super`

The primary normative contract formerly contained here has moved to `semantics/CALLABLES.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## 13. Return Semantics

The primary normative contract formerly contained here has moved to `semantics/CALLABLES.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## 14. Return from Escaped Closures

The primary normative contract formerly contained here has moved to `semantics/CALLABLES.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## 15. `null`

The primary normative contract formerly contained here has moved to `semantics/VALUES_AND_COLLECTIONS.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## 16. Booleans

The primary normative contract formerly contained here has moved to `semantics/VALUES_AND_COLLECTIONS.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## 17. Iteration and Loops

The primary normative contract formerly contained here has moved to `semantics/VALUES_AND_COLLECTIONS.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## 18. Trailing Closures

The primary normative contract formerly contained here has moved to `semantics/VALUES_AND_COLLECTIONS.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

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

The primary normative contract formerly contained here has moved to `semantics/OBJECT_MODEL.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## 21. Equality and Identity

The primary normative contract formerly contained here has moved to `semantics/VALUES_AND_COLLECTIONS.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## 21.1 Custom Symbolic Binary Operators

The primary normative contract formerly contained here has moved to `semantics/VALUES_AND_COLLECTIONS.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## 22. Open Objects

The primary normative contract formerly contained here has moved to `semantics/OBJECT_MODEL.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## 23. Closed Objects

The primary normative contract formerly contained here has moved to `semantics/OBJECT_MODEL.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## 24. Frozen Objects

The primary normative contract formerly contained here has moved to `semantics/OBJECT_MODEL.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## 25. Errors

The primary normative contract formerly contained here has moved to `semantics/ERRORS.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## 26. Futures

The normative contract formerly contained in this section has moved to `concurrency/FUTURES_AND_TASKS.md`. This heading remains as a compatibility anchor during modularization; it defines no independent duplicate contract.

## 27. Asynchronous Execution

The normative contract formerly contained in this section has moved to `concurrency/FUTURES_AND_TASKS.md`. This heading remains as a compatibility anchor during modularization; it defines no independent duplicate contract.

## 28. Future State, Resolution, Failure, and Adoption

The normative contract formerly contained in this section has moved to `concurrency/FUTURES_AND_TASKS.md`. This heading remains as a compatibility anchor during modularization; it defines no independent duplicate contract.

## 29. Obtaining a Future's Value

The normative contract formerly contained in this section has moved to `concurrency/FUTURES_AND_TASKS.md`. This heading remains as a compatibility anchor during modularization; it defines no independent duplicate contract.

## 30. Future Composition

The normative contract formerly contained in this section has moved to `concurrency/FUTURES_AND_TASKS.md`. This heading remains as a compatibility anchor during modularization; it defines no independent duplicate contract.

## 31. Structured Concurrency

The normative contract formerly contained in this section has moved to `concurrency/FUTURES_AND_TASKS.md`. This heading remains as a compatibility anchor during modularization; it defines no independent duplicate contract.

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

The primary normative contract formerly contained here has moved to `semantics/MODULES.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## Indexed Access Syntax

The primary normative contract formerly contained here has moved to `semantics/VALUES_AND_COLLECTIONS.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## Standard Array Indexed Semantics

The primary normative contract formerly contained here has moved to `semantics/VALUES_AND_COLLECTIONS.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

## Standard Array Parallel Operations

The primary normative contract formerly contained here has moved to `semantics/VALUES_AND_COLLECTIONS.md`. This heading remains as a compatibility and navigation anchor; it is not a second normative owner.

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


### Error precedence during `ensure` cleanup

If execution enters `cleanup` because a control transfer is leaving the
protected scope, normal completion of `cleanup` preserves that pending transfer.

If `cleanup` instead signals an `Error`, the cleanup Error becomes the active
error transfer and supersedes the transfer that caused cleanup to run. This
applies when the prior transfer was normal scope exit, non-local return, Error
unwind, or cancellation unwind.

Therefore, when an Error `original` is already unwinding through an `ensure`
scope and the cleanup signals `cleanupError`, outward handler search observes
`cleanupError`, not `original`.

Core v0.1 does not automatically wrap `cleanupError`, attach `original` as a
language-visible cause, construct a suppressed-error list, or otherwise preserve
both failures as a new composite Error. Libraries may build such reporting
conventions explicitly with ordinary objects and handlers.

This rule does not undo effects already performed before either Error was
signaled. It fixes only which control transfer continues after the cleanup
attempt.

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
### Canonical one-shot text/byte conversion dispatch

The standard one-shot encoding/decoding receiver is the `Encoding` object:

```js
UTF8.encode(text)
UTF8.decode(bytes)
```

In abstract form, the standardized operations are
`encoding.encode(text)` and `encoding.decode(bytes)`.

Core v0.1 does not additionally standardize `String.encode(encoding)` or
`Bytes.decode(encoding)` convenience messages. Libraries may provide such
ordinary messages, but portable Core code cannot rely on them.

UTF8.decode(bytes)
### Canonical one-shot encoding dispatch

Core v0.1 has one canonical standard one-shot encoding/decoding dispatch
direction: the `Encoding` object is the receiver.

```js
UTF8.encode(text)
UTF8.decode(bytes)
```

The corresponding abstract form is `encoding.encode(text)` and
`encoding.decode(bytes)`, as defined normatively by the I/O model.

Core v0.1 does **not** additionally standardize reciprocal convenience messages
`String.encode(encoding)` or `Bytes.decode(encoding)`. A library may provide
such ordinary conveniences, but portable Core code cannot rely on them unless a
later standard explicitly adds them.

This choice introduces no special syntax. `UTF8` and other standardized
encodings are ordinary Encoding objects when available through the applicable
standard-library/I/O environment, and ordinary polymorphic message dispatch
applies.

UTF8.encode(text)
```

Decoding interprets a byte sequence using the selected encoding and produces a `String`. Encoding converts a `String` into a `Bytes` value using the selected encoding.

The standard encoding catalogue, strict/replacement decoding rules, BOM behavior, and text-I/O semantics are defined normatively in `io/IO_CORE.md`. Those encoding objects and I/O facilities remain outside the required Core prelude unless another specification explicitly says otherwise.

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

### Standard Bytes size

### Complete standard Bytes sequence semantics

Standard `Bytes` is dynamically resizable through the explicit standard
operations `add(value)` and `removeAt(index)`. This does not change the existing
`atPut(index, value)` contract: `atPut` replaces one existing octet and never
changes sequence length.

#### Standard empty Bytes construction

Where the standardized `Bytes` factory object is available, its ordinary
polymorphic invocation behavior accepts exactly zero positional arguments:

```js
Bytes()
```

and creates a fresh **open**, empty standard Bytes object with receiver-owned
byte-sequence state.

Core v0.1 does not require `Bytes` to be a binding of the Core prelude; that
availability boundary remains owned by the I/O model and standard-library
environment. This rule defines the semantics of the standardized factory when
it is exposed; it does not introduce a new mandatory prelude binding.

Each successful `Bytes()` invocation creates a fresh identity. A non-empty
argument vector fails with the ordinary argument-count error after ordinary
left-to-right argument evaluation and before the new Bytes object is created.
The standard factory sends no `init` message.

If a prototype inherits the standard Bytes-factory invocation behavior, the
fresh Bytes object's delegation parent is the actual invocation receiver. The
prototype itself does not acquire byte-sequence state merely by inheriting the
factory.

#### Standard `Bytes.add`

```js
bytes.add(value)
```

requires `value` to be an exact semantic `Integer` in the inclusive range
`0..255`. No Float, numeric coercion, masking, wrapping, truncation, or
implementation-native byte conversion is permitted.

The original receiver must own standard Bytes state and must be **open**.
A closed or frozen receiver signals an `Error` before mutation.

On success, `add` appends the exact supplied semantic Integer value after the
current last octet, increases `size` by exactly one, preserves all existing
octets and their relative order, and returns the exact supplied `value` object.

Validation completes before sequence mutation. A failing `add` changes neither
length nor contents.

#### Standard `Bytes.removeAt`

```js
bytes.removeAt(index)
```

requires an exact semantic `Integer` index in the current range
`0 .. bytes.size - 1`. No coercion, truncation, wrapping, or negative indexing
is defined.

The original receiver must own standard Bytes state and must be **open**.
A closed or frozen receiver signals an `Error` before indexed removal.

On success, `removeAt` removes exactly the octet currently at `index`, shifts
every later octet left by one position while preserving order, decreases `size`
by exactly one, and returns the exact semantic Integer octet value that was
removed.

The index is validated against the sequence state applicable to this operation
before mutation. A failing `removeAt` changes neither length nor contents.

#### Standard `Bytes.each`

```js
bytes.each(block)
```

requires `block` to be invokable through the ordinary polymorphic invocation
protocol. It need not be a Closure.

After ordinary receiver and argument evaluation, standard `Bytes.each` first
validates the original receiver as standard Bytes, then validates `block`
callability, and only then captures a shallow logical snapshot of the current
octets in ascending index order.

Each snapshot octet is supplied to one ordinary invocation:

```text
block(octet)
```

The octet argument is the exact semantic Integer value stored at snapshot time.
Because byte values are semantic Integer values, no host byte/signed-byte object
is exposed.

The iteration snapshot is fixed for the invocation. Later `atPut`, `add`, or
`removeAt` operations performed by callbacks or by other Actor-local work at
explicit suspension points do not change which snapshot octets this invocation
will visit or their order. Such mutations remain governed by the receiver's
ordinary open/closed/frozen rules.

If every callback completes normally, `each` returns the receiver Bytes object.
A callback error or non-local control effect stops further callbacks and
propagates normally; callbacks already completed and independently permitted
mutations are not rolled back.

`Bytes.each` introduces no hidden Map-style mutation guard, lock, transaction,
or suspension point. Snapshot representation is implementation-private provided
the observable ascending-index snapshot semantics are preserved.

#### State consequences

Standard Bytes state therefore has these mutation permissions:

```text
open:
    atPut     allowed for an existing index
    add       allowed
    removeAt  allowed

closed:
    atPut     allowed for an existing index
    add       ERROR
    removeAt  ERROR

frozen:
    atPut     ERROR
    add       ERROR
    removeAt  ERROR
```

`size`, `at`, and `each` remain read-only observations available for open,
closed, and frozen Bytes.



The standard `Bytes.size` operation returns a semantic `Integer` equal to the
receiver's current number of octets.

For standard Bytes whose valid indexed positions are:

```text
0, 1, 2, ... byteLength - 1
```

`bytes.size` returns exactly the mathematical Integer `byteLength`.

Core does not require a particular fixed-width Integer family for this result.
An implementation must not expose host index width, native buffer-size limits,
overflow, wrapping, saturation, or truncation through `Bytes.size`.

`size` is a read-only observation. It does not read or decode octet contents,
does not invoke user behavior, and does not mutate the Bytes object. It is
available for open, closed, and frozen Bytes.

The existing standard Bytes receiver-domain rule applies. Merely delegating to
a Bytes object, copying a `size` behavior, or otherwise obtaining that behavior
does not confer receiver-owned byte-sequence state.

`Bytes.size` counts octets only. It does not report Unicode scalar values,
grapheme clusters, encoded characters, storage capacity, reserved capacity,
host buffer length, or any other representation-dependent quantity.



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

This keeps ordinary text operations aligned with user-perceived characters.

Core v0.1 does not standardize separate `String.graphemes()` or
`String.codePoints()` convenience protocols. Libraries may provide ordinary
messages with those names, and a later standard may define explicit lower-level
text views, but portable Core code cannot rely on either protocol.

Explicit conversion between text and encoded bytes remains standardized through
the Encoding-object contract defined by the I/O model, for example:

```js
UTF8.encode(text)
```

Because `String` is immutable, operations that conceptually modify text produce a new `String`:

```js
text.uppercase()
text.replace("a", "b")
text + otherString
```

Here `otherString` denotes another semantic String value. The standard
String-family `+` operation does not accept an arbitrary non-String object.

Efficient incremental text construction may be provided by separate mutable library objects or buffer-oriented abstractions. Core v0.1 does not standardize a `StringBuilder` binding, prototype, constructor, or protocol.

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



### Standard String concatenation with `+`

The standard String-family behavior for binary `+` concatenates two semantic
String values.

```js
"hel" + "lo"     // "hello"
```

The original receiver must be a semantic String value, and the right operand
must also be a semantic String value. No Number, Boolean, `null`, arbitrary
object, prototype, or other value is implicitly converted to String.

The result is the String whose Unicode-scalar sequence is exactly the receiver's
scalar sequence followed by the right operand's scalar sequence. Concatenation
performs no Unicode normalization, grapheme resegmentation beyond that implied
later by ordinary String `size` / `at`, locale transformation, encoding, or
decoding.

String values are immutable, so concatenation mutates neither operand. The
result obeys ordinary String value identity and equality. Consequently, when
the concatenated scalar sequence equals an existing String value's scalar
sequence, `===` observes the same String value semantics:

```js
("hel" + "lo") === "hello"   // true
```

The standard operation performs no user callback, textual coercion, `hash`,
`==`, Encoding operation, or hidden suspension.

This is a standard String-family specialization of the ordinary `+` message.
Ordinary lookup and overriding rules remain unchanged. Merely delegating to a
String value or String-family prototype does not make an incompatible receiver
a semantic String.

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

### Standard Map construction through ordinary invocation

### Standard Map size

Standard `Map.size` and `IdentityMap.size` return the exact semantic `Integer`
number of associations currently stored in the receiver's keyed-entry state.

An empty newly constructed Map therefore has size `0`. Inserting a previously
absent key increases size by exactly one. Replacing the mapped value of an
already-matching key leaves size unchanged. Successfully removing one stored
association decreases size by exactly one.

For normal `Map`, size counts stored associations, not distinct current
`==`-equivalence classes. Therefore if mutable-key behavior has caused two
stored representative keys to become currently equal while both entries remain
stored, both associations count toward `size`.

For `IdentityMap`, size likewise counts stored associations. Identity-hash
collisions do not merge entries and do not affect the count.

The result is a mathematical semantic `Integer`. Core does not require a
particular fixed-width Integer family, and an implementation must not expose
host container width, bucket count, load factor, capacity, tombstones, sparse
representation, or overflow/truncation through the result.

`size` is a read-only observation. It performs no key `hash`, key `==`,
`identityHashOf`, `===` comparison, callback, iteration snapshot, insertion,
removal, or mapped-value access. It is available for open, closed, and frozen
Maps.

The existing standard keyed receiver-domain rule applies. Merely delegating to
`Map` or `IdentityMap`, or copying/inheriting a `size` behavior onto an object
without the corresponding receiver-owned keyed state, does not make that object
a valid standard Map-size receiver.



The standard prelude objects `Map` and `IdentityMap` specialize the ordinary
polymorphic invocation protocol as empty-map factories.

The standard calls:

```js
Map()
IdentityMap()
```

each create a fresh **open** standard keyed object with no entries.

`Map()` creates ordinary Map keyed state, whose standard key matching uses the
existing `hash` plus `==` rules. `IdentityMap()` creates IdentityMap keyed state,
whose standard key matching uses the existing primitive identity-hash plus
`===` rules.

Both standard factories accept exactly zero positional arguments. A non-empty
argument vector signals the ordinary argument-count error after all call
arguments have already been evaluated under the existing left-to-right call
rules. No Map object is created by that failing invocation, and argument effects
are not rolled back.

Core v0.1 deliberately defines no constructor form that consumes alternating
key/value arguments, an Array of pairs, another Map, an iterator, `each`, or any
other implicit entry source. Programs populate a newly created Map explicitly
through ordinary keyed operations such as:

```js
m: Map()
m.atPut(key, value)
```

This avoids introducing a hidden iterable/pair protocol or constructor-specific
duplicate-key rule.

Each successful invocation creates a fresh Map identity, including two
successive empty-map calls. The new keyed state is empty and its insertion order
therefore contains zero entries.

The created object's delegation parent is the object whose standard Map-factory
or IdentityMap-factory invocation behavior was selected as the invocation
receiver. Consequently ordinary prototype specialization composes with Map
construction:

```js
MyMap: Map {
    label: "custom"
}

m: MyMap()
```

`m` owns fresh ordinary Map keyed state and delegates to `MyMap`; `MyMap` itself
does not acquire keyed state merely by delegating to `Map`. The analogous rule
applies to prototypes inheriting the `IdentityMap` factory behavior.

Factory inheritance never changes map kind. Behavior inherited from `Map`
creates ordinary Map keyed state; behavior inherited from `IdentityMap` creates
IdentityMap keyed state.

This rule does not weaken the standard keyed receiver-domain invariant.
Inheriting or copying `at`, `atPut`, `containsKey`, `remove`, `each`, or other
ordinary keyed behavior still does not confer keyed state on the receiver. The
factory instead creates a distinct new object that owns that state.

The standard Map factories do not send `init` to the created object and perform
no key hashing, equality comparison, identity hashing, callback, iteration,
entry insertion, or hidden suspension after invocation begins.

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

`block` must be invokable through the ordinary polymorphic invocation protocol.
It need not be a Closure: any value accepted by an ordinary parenthesized call
is a valid standard Map iteration callback.

After ordinary receiver and argument evaluation, standard `Map.each` /
`IdentityMap.each` first validates the receiver under the existing standard Map
receiver-domain rule, then validates `block` callability, and only then
establishes the iteration snapshot. A non-invokable `block` therefore signals an
`Error` before snapshot establishment and before any callback invocation.

Callability validation does not validate callback arity in advance. Each
snapshot association is subsequently supplied through one ordinary polymorphic
invocation with exactly two positional arguments:

```text
block(key, value)
```

Any arity or invocation error from that actual call propagates normally and
stops iteration under the existing `each` error/unwind rule.

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

#### Error outcomes crossing the P boundary

An unhandled `Error` produced by isolated P execution is a failure value that
must cross the P boundary under the same P value-transfer rules that govern a
normal result.

If the Error object graph is transferable, the caller-side P result Future fails
with the transferred destination Error value. For identity-bearing Error objects,
that destination object is not `===` to the P-local source object merely because
it represents the same failure. P-local object identity never crosses the
isolation boundary.

If the Error object graph is not transferable, the caller-side operation fails
with standard `NonParallelValue` according to the existing P result-transfer
failure rule. Core does not leak a live P-local Error reference, silently proxy
the Error, or preserve otherwise non-transferable state merely because the value
represents a failure.

Only the Error value graph participates in transfer. P-local dynamic handlers,
activation frames, return homes, stacks, continuations, scheduler state, and
other execution-control metadata are not part of the Error value graph and
never cross with the failure.

When the caller later observes the failed Future through `value()`, ordinary
failed-Future semantics signal the caller-side transferred Error as a new
non-resumable consumer-side signaling event.

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

#### Exclusive mutable byte regions

Core v0.1 standardizes exclusive writable partitioning only for byte-indexed
state whose mutable authority is intrinsically range-local: standard `Bytes` and
the byte-region values created by the operation below.

Inside an isolated P domain, standard `Bytes` provides:

```text
bytes.parallelRange(start, length, worker, arguments...)
    -> Future
```

The operation is invalid outside P. `start` and `length` must be semantic
Integers, `start >= 0`, `length >= 0`, and `start + length <= bytes.size`.
`worker` must be a Closure.

A successful submission reserves exactly the half-open indexed interval
`[start, start + length)` of that receiver until the returned Future reaches a
terminal state.

The worker executes in a child P domain using ordinary Closure projection. Its
first argument is a fresh standard `ByteRegion` capability representing exactly
the reserved bytes, indexed locally from `0` through `length - 1`; the caller's
explicit `arguments...` follow it.

`ByteRegion` is an ordinary standard object capability with fixed `size`,
`at(index)`, and `atPut(index, value)` behavior equivalent to the corresponding
byte-indexed operations over its local interval. It has no operation that changes
its length. It does not expose the parent `Bytes`, physical address, backing
allocation, global offset, storage ownership, or another region.

The child mutates only its isolated `ByteRegion`. On normal child completion,
after the child result has itself been validated for P-boundary transfer, the
region's final byte sequence replaces the reserved parent interval atomically and
the parent Future resolves with the transferred child result.

Successful byte-region publication and cancellation are one semantic commitment race.
The runtime must not expose committed parent-region mutation together with a
`cancelled` result Future. If cancellation terminalizes the Future before
successful publication commits, the reservation is released with no parent-byte
publication. If successful publication commits first, the parent replacement and
successful Future resolution are one indivisible semantic outcome and later
cancellation is a no-op. On child failure,
cancellation, or an untransferable result, no region mutation is published to
the parent.

While a reservation is active, overlapping `parallelRange` submission signals
`ParallelRegionOverlap`; parent `at`/`atPut` touching a reserved index signals
`ParallelRegionInUse`; a parent operation that changes byte-sequence length or
shifts indexes signals `ParallelRegionInUse`; unrelated accesses outside every
active interval remain ordinary. `size` remains readable. No rule waits or
suspends. Zero-length ranges reserve no indexed state and never overlap.

`ParallelRegionOverlap`, `ParallelRegionInUse`, and `ParallelRegionOutsideP` are
standard Error prototypes delegating directly to `Error`.

A `ByteRegion` may itself use `parallelRange` inside its owning P domain, so
exclusive byte authority composes recursively. It is not ordinarily P-/Actor-
transferable and cannot escape as an ordinary result or explicit argument; only
the dedicated parent-to-child region operation transfers its authority.

Core v0.1 deliberately does not generalize this writable-region contract to
ordinary `Array` or arbitrary objects. Array indexes may contain aliases to
mutable object graphs, so disjoint index ranges alone do not establish disjoint
mutable authority.

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
