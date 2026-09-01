# Core Language Grammar v0.1

Language version: 0.1  
Document revision: 60  
Status: Draft  
Last updated: 2026-09-01


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

A logical source newline is one `LF` (U+000A), one `CR` (U+000D), or one `CRLF` (U+000D U+000A) sequence; the normative definition is in the Whitespace and Newlines section below.

Single-quoted and double-quoted String literals are single-line literals. A logical source newline is not permitted inside a single-quoted or double-quoted String literal. Encountering `LF`, `CR`, or `CRLF` before the matching closing quote is a lexical error. Newline characters may be represented in these literals using the existing `\n` and `\r` escape sequences, which denote String content and are distinct from raw source newlines.

Triple-double-quoted String literals are the Core v0.1 syntax for source-level multiline text. Logical source newlines are permitted and are part of the literal content, subject to the multiline indentation normalization rule. Retained source newlines preserve their original source code points in the resulting String: `LF` remains U+000A, `CR` remains U+000D, and `CRLF` remains U+000D U+000A. There is no implicit newline normalization of String content.

Protos has no separate character literal or character type. `'a'` and `"a"` both evaluate to a String containing the single-character text `a`.

String interpolation is not part of Core v0.1. Inside a String, `${...}` has no special meaning and is treated as ordinary literal text.

For triple-double-quoted String literals, indentation normalization is defined as follows:

- If the opening `"""` is immediately followed by a logical source newline, that newline is not part of the resulting String. A removable `CRLF` is removed as one logical newline: both U+000D and U+000A.
- If the closing `"""` is immediately preceded by a logical source newline whose preceding content on that line is only indentation whitespace (exactly SPACE or TAB; see Whitespace and Newlines), that final newline and indentation are not part of the resulting String. A removable `CRLF` is removed as one logical newline: both U+000D and U+000A.
- The literal is split into logical content lines at each logical source newline; `LF`, `CR`, and `CRLF` each count as one logical newline for this splitting.
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

Horizontal whitespace consists of exactly two code points:

```text
U+0020  SPACE
U+0009  CHARACTER TABULATION (TAB)
```

No other code point is horizontal whitespace. This set is closed: it does not depend on Unicode whitespace properties or on any host-language, host-library, or host-operating-system whitespace classification.

Outside lexical constructs that consume their own contents (String literals and comments), SPACE and TAB are insignificant horizontal whitespace. They separate tokens where separation is required and otherwise produce no parser token.

Logical source newlines are a separate lexical category. They are not horizontal whitespace.

A logical source newline is exactly one of `LF` (U+000A), `CR` (U+000D), or `CRLF` (U+000D U+000A). `CRLF` is consumed atomically as one logical source newline, never as two.

Each logical source newline that is not consumed by another lexical construct produces exactly one `NEWLINE` token for the parser. `LF`, `CR`, and `CRLF` therefore each produce one `NEWLINE` token; `CRLF` never produces two.

A block comment is a lexical construct that consumes its own contents, including embedded logical source newlines; those newlines produce no `NEWLINE` token (see Comments).

Source files may freely mix `LF`, `CR`, and `CRLF` logical newlines. Mixed line-ending styles are not lexical errors.

In the EBNF, the terminal `newline` denotes this logical `NEWLINE` token. Parser productions using `newline` do not depend on whether the source spelling was `LF`, `CR`, or `CRLF`.

Newline handling does not depend on the host operating system, editor settings, Git line-ending conversion, or any host line-separator convention.

No other Unicode code point is implicitly ignored as whitespace merely because Unicode, Java, an operating system, or another host API classifies it as whitespace or space. In particular, the following are not Core v0.1 whitespace:

```text
U+000B  VERTICAL TAB
U+000C  FORM FEED
U+0085  NEXT LINE
U+00A0  NO-BREAK SPACE
U+1680  OGHAM SPACE MARK
U+2000..U+200A  Unicode space characters
U+2028  LINE SEPARATOR
U+2029  PARAGRAPH SEPARATOR
U+202F  NARROW NO-BREAK SPACE
U+205F  MEDIUM MATHEMATICAL SPACE
U+3000  IDEOGRAPHIC SPACE
U+FEFF  ZERO WIDTH NO-BREAK SPACE
```

This list is illustrative of important exclusions, not an alternative open-ended definition. The normative rule is that only U+0020 and U+0009 are horizontal whitespace. In particular, U+2028 LINE SEPARATOR and U+2029 PARAGRAPH SEPARATOR are neither horizontal whitespace nor logical source newlines.

A source code point that is neither part of a valid lexical token, nor SPACE or TAB horizontal whitespace, nor a logical source newline, nor consumed inside a lexical construct such as a String or comment is a lexical error. The lexer must not silently discard unknown Unicode whitespace-like or format characters.

Characters such as NBSP, Unicode space characters, U+2028, U+2029, and U+FEFF may occur as ordinary String content where the String literal rules permit them. Their exclusion from lexical whitespace applies outside String content.

Wherever the multiline triple-double-quoted String rules refer to "indentation whitespace", the characters that may constitute indentation whitespace are exactly SPACE (U+0020) and TAB (U+0009). This defines only which characters count as indentation whitespace; it does not define tab width, visual columns, whether a TAB is equivalent to some number of SPACE characters, how common indentation is computed when SPACE and TAB are mixed, or whether mixed SPACE/TAB indentation is permitted.

Expression separation across logical newlines is a parser rule, not a lexical rule. A logical `NEWLINE` token normally separates expressions when the expression before it may legally end at that point. A logical `NEWLINE` does not separate expressions when the syntactic construct before the newline is necessarily incomplete and the parser must consume more input to complete that construct. This rule is based on grammatical incompleteness, not on a hard-coded list of token spellings. It does not depend on indentation, visual alignment, tab width, source line-ending spelling, or runtime semantics.

There is no Automatic Semicolon Insertion. The parser is deciding whether an existing logical `NEWLINE` token acts as an expression separator or is consumed as continuation whitespace in a syntactically incomplete construct. It is not inserting a separator that is absent from the token stream.

When the syntax before a newline necessarily requires further input, the newline is insignificant for expression separation. An expression ending after an infix/binary operator is necessarily incomplete because the operator requires a right operand:

```js
a: 1 +
    2
```

contains one expression, equivalent to `a: 1 + 2`. The same applies to every binary operator, including `&&` and `==`.

Slot creation and assignment also continue after their operator because `:` and `=` require a right-hand expression:

```js
x:
    value
```

is equivalent to `x: value`, and:

```js
x =
    value
```

is equivalent to `x = value`.

An open syntactic delimiter continues until the corresponding construct is complete. Ordinary multiline calls are therefore valid:

```js
foo(
    a,
    b
)
```

as are multiline parenthesized expressions:

```js
(
    a +
    b
)
```

and multiline indexed expressions:

```js
array[
    index
]
```

The logical newlines used to lay out these necessarily incomplete constructs do not terminate the surrounding expression. A comma separates two elements of a comma-separated list and necessarily requires another list element after it, so a newline after a comma is continuation according to the list grammar.

If the expression before a logical newline is syntactically complete, the newline normally acts as an expression separator:

```js
a: 1
b: 2
```

contains two expressions. A binary operator at the beginning of the following line does not normally cause the preceding complete expression to continue. Therefore:

```js
a
+ b
```

does not mean `a + b`: the newline terminates the expression `a`, and the following line then begins with `+`, which is invalid in Core v0.1 because standalone unary `+` does not exist. There is no general "operator at the beginning of the line continues the previous expression" rule.

Core v0.1 has one explicit exception to the normal complete-before-newline rule: a leading structural member-access `.` continues the preceding expression as a postfix/member chain. Therefore:

```js
result: object
    .foo()
    .bar()
```

is one expression, equivalent to `object.foo().bar()`. Conceptually, a logical newline immediately before a leading member-access `.` is consumed as continuation rather than as an expression separator. This exception is deliberate and specific: it does not generalize to binary operators, custom symbolic operators, `(`, `[`, `{`, `=>`, or any other token merely because that token could somehow be attached to the expression on the previous line. The `.` must have its ordinary structural/member-access meaning under the existing lexical and grammar rules; this rule does not alter decimal-dot tokenization or any other lexical rule.

Indentation has no syntactic significance for these rules: the equivalences above hold regardless of indentation.

An explicit `;` is the inline expression separator: it separates two expressions written on the same logical source line. It is a separator, not a terminator: it must have an expression before it and an expression after it on the same logical source line, with no `NEWLINE` token between the `;` and either expression. Leading, trailing, and consecutive semicolons are syntax errors, and a `;` does not acquire terminator meaning merely because a newline follows it. There is no semicolon continuation analogous to newline continuation; a semicolon cannot be ignored merely because formatting or the next token suggests continuation.

A completed call is syntactically complete, so a logical `NEWLINE` after it acts as a separator under the complete-expression newline rule above, and the braces of a following `{ ... }` do not attach to the completed call as a trailing closure (see Trailing Closures, where issue B7 is closed). `{` is not a complete-before-newline continuation exception: the only such exception remains the leading structural `.` rule. Once a `NEWLINE` token is functioning as line separation under the rules above, any positive run of separating `NEWLINE` tokens has the effect of one separating `NEWLINE`: blank lines are permitted and create no empty expressions (see Expression Separators). This multiplicity rule does not create continuation behavior that this section does not permit. Comma-separated list elements and trailing commas are defined in Closures and Calls and Arguments: `,` is the only list-element separator and trailing commas are syntax errors.

## 5. Expression Separators

Core v0.1 has two distinct expression-separation mechanisms with distinct syntactic roles:

- A logical `NEWLINE` separates expressions written on different logical source lines. It is the ordinary cross-line expression separator.
- `;` separates expressions written on the same logical source line. It is the inline expression separator.

The two mechanisms are not interchangeable spellings of one generic separator. `;` requires an expression on both sides of it on the same logical source line; a separating `NEWLINE` ends the current source line and the next expression begins on a later line. There is no requirement to write `;` at the end of a source line, and a `;` at the end of a line is a syntax error, not an optional terminator.

```ebnf
expression-sequence =
    [ newline-run ],
    [ expression-line-items ],
    [ newline-run ] ;

expression-line-items =
    expression-line,
    { newline-run, expression-line } ;

expression-line =
    expression,
    { ";", expression } ;

newline-run =
    newline,
    { newline } ;
```

An `expression-line` is one or more expressions separated by `;` on one logical source line. `;` is a separator, not a terminator: each `;` must have an expression before it and an expression after it within the same `expression-line`. Leading, trailing, and consecutive semicolons are therefore syntax errors, and a `;` cannot reach an expression on a following source line: since every logical source newline that is not consumed by another lexical construct produces a `NEWLINE` token, and no production permits a `newline` token between a `;` and the expressions on either side of it, the same-line requirement follows from the token structure. `;` is not affected by the newline-continuation rules: there is no semicolon continuation, and a `;` is never ignored merely because formatting or the next token suggests continuation. Comment forms do not change this rule: `//` does not consume its terminating logical source newline, so a `;` before a `//` comment still fails to reach an expression on the next line, while a `/* ... */` comment consumes embedded logical source newlines and behaves whitespace-like.

A `newline-run` is one or more consecutive `NEWLINE` tokens functioning as line separation. Between two `expression-line`s, a `newline-run` of any positive length separates them with the same effect as a single separating `NEWLINE`; a `newline-run` at the beginning or end of an `expression-sequence` is blank-line formatting. Blank lines create no empty, omitted, or `null` expressions, produce no semantic AST nodes, and have no runtime behavior.

Which `NEWLINE` tokens reach an `expression-sequence` at all is decided by the newline-continuation rules in Whitespace and Newlines: a `NEWLINE` is consumed as continuation while the construct before it is necessarily incomplete, or when it is immediately followed by a leading structural `.`; only the remaining `NEWLINE` tokens act as line separation. The multiplicity rule here does not create continuation behavior those rules do not permit.

Both mechanisms produce the same semantic representation: the expressions of an `expression-sequence` become the ordered expressions of `Sequence(expressions)` in the canonical AST. Neither `;` nor a separating `NEWLINE` becomes an AST node, and the source-level separator choice does not change the ordered expressions.

A comma is not an expression separator. It is reserved for list-like syntactic forms such as arguments and parameters, where it separates elements and is the only element separator; a trailing comma is not permitted (see Closures and Calls and Arguments). Thus:

```js
foo()
bar()
baz()
```

and:

```js
foo(); bar(); baz()
```

contain the same three expressions in the same order. The same rule applies inside object bodies:

```js
point: { x: 10; y: 20 }
```

A comma cannot be substituted for `;` here.

These are valid:

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

The first four fail because a `;` lacks an expression before it or after it on the same logical source line. The last two fail because `;` is same-line only: the `NEWLINE` ends the current line, and the semicolon cannot take the following line's expression as its right-hand expression.

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
    slot-creation-target, ":", expression;
```

`:` is specifically the slot-creation operator. Its target must be a bare identifier or a member target (see Slot-Creation and Assignment Targets): the final postfix operation of the target may not be an index suffix.

Examples:

```js
x: 10
person.name: "Guille"
this.cache: {}
object[index].name: value
foo().bar: value
```

`:` always creates a new local slot at the selected destination.

These are syntax errors because the final target operation is an index suffix:

```js
object[index]: value
object["foo"]: value
object.foo[index]: value
```

There is no indexed slot creation: `:` is never lowered to a message.

## 9. Assignment

```ebnf
assignment =
    assignment-target, "=", expression;
```

`=` applied to a bare identifier or member target modifies an existing writable slot and never creates a missing one (see Slot-Creation and Assignment Targets). When the final target operation is an index suffix, the assignment is indexed assignment and lowers to the `atPut` message instead of a slot `Assign` (see Indexed Access).

Examples:

```js
x = 20
person.name = "Guillermo"
matrix[0] = value
matrix[0].name = value
factory()[i] = value
```

The create-versus-modify distinction expressed by `:` versus `=` belongs to the slot model. It does not apply to the indexing protocol: `=` in indexed assignment does not require an already-existing indexed entry, and whether `atPut` creates, replaces, extends, or rejects a missing key or index is defined by the receiver's `atPut` protocol.

## 10. Slot-Creation and Assignment Targets

Slot creation and assignment have distinct target categories.

```ebnf
slot-creation-target =
      identifier
    | member-expression;

assignment-target =
      identifier
    | member-expression
    | indexed-target;

indexed-target =
    postfix-expression, "[", expression, "]";
```

A `slot-creation-target` is a bare identifier or a postfix chain whose **final** operation is a member suffix. An `assignment-target` may additionally end in an index suffix. The final postfix operation therefore determines which operator may follow the target: a final member target may participate in slot creation or assignment, while a final index target may participate only in indexed assignment, never in slot creation.

Examples of valid targets:

```js
x
person.name
this.name
context.value
object[index].name
matrix[0]
```

`object[index]` is an `indexed-target`, not a `slot-creation-target`, so `object[index]: value` is a syntax error. Chained postfix forms whose final operation is a member remain valid slot-creation targets, for example `object[index].name: value` and `foo().bar: value`. A final index target remains a valid assignment target, for example `object.name[index] = value`.

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
    "(", [ layout ], [ parameter-items ], [ layout ], ")" ;

parameter-items =
      rest-parameter
    | parameter, { ",", [ layout ], parameter },
      [ ",", [ layout ], rest-parameter ] ;

parameter =
    identifier, [ "=", expression ] ;

rest-parameter =
    "...", identifier ;

layout =
    newline,
    { newline } ;
```

Parameters are comma-separated: exactly one comma is required between each two consecutive parameters. A comma is strictly a separator between two list elements; it is not a terminator and does not represent an empty or omitted element. A comma must have a parameter on both sides within the same list, so a trailing comma before the closing `)` is a syntax error: `(a,)` and `(a, b,)` are invalid. Default parameters and rest parameters use the same separator rule.

A `layout` logical `NEWLINE` is a newline consumed as continuation/layout inside the necessarily-incomplete delimited construct under the Whitespace and Newlines rules. `layout` denotes one or more consecutive logical `NEWLINE` tokens: at each layout position, a run of newlines — including blank lines — is formatting with the same effect as a single layout newline. It is not an element separator: it never separates parameters, never substitutes for the required comma between two consecutive parameters, and never creates an empty or omitted parameter.

Multiline parameter lists remain valid when the commas are present:

```js
(
    a,
    b
) => {
    body
}

(

    a,

    b

) => {
    body
}

(a = 1, b = 2) => {
    body
}

(a, ...rest) => {
    body
}
```

Omitting the comma between two parameters is a syntax error, even when the parameters are on separate lines, and a comma immediately before the closing `)` is a syntax error:

```js
(a b) => {
    body
}

(a ...rest) => {
    body
}

(
    a
    b
) => {
    body
}

(
    a,
    b,
) => {
    body
}
```

A rest parameter, when present, is final. Parameter names within one parameter list must be unique. Duplicate names, including collisions with the rest parameter name, are rejected during parsing or static validation.

## 17. Calls and Arguments

```ebnf
argument-list =
    "(", [ layout ], [ argument-items ], [ layout ], ")" ;

argument-items =
    argument, { ",", [ layout ], argument } ;

argument =
      expression
    | "...", expression ;
```

Call arguments are comma-separated: exactly one comma is required between each two consecutive arguments. A comma is strictly a separator between two list elements; it is not a terminator and does not represent an empty or omitted element. A comma must have an argument on both sides within the same list, so a trailing comma before the closing `)` is a syntax error: `foo(a,)` and `foo(a, b,)` are invalid. Spread arguments use the same separator rule.

A logical `NEWLINE` is not an argument separator and does not substitute for a required comma. Newlines inside the delimiters of an open argument list are `layout` (see Closures): continuation/formatting within the necessarily-incomplete construct under the Whitespace and Newlines rules. One or more consecutive layout newlines — blank lines — are permitted at any layout position. A newline immediately after the opening delimiter or before the closing delimiter is layout as well and does not imply an empty or omitted element.

Multiline calls are therefore valid when the commas are present:

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

foo(
    a,
    ...rest
)
```

and invalid when a comma is missing between two arguments or follows the final argument:

```js
foo(a b)

foo(
    a
    b
)

foo(a ...rest)

foo(
    a
    ...rest
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
```

The newlines between the final argument and the closing delimiter — a single newline or a run of blank lines — are layout inside the open construct: they are not an element separator, not a trailing list separator, and not an empty element, and they do not make a trailing comma legal.

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

Bracket syntax lowers to the ordinary `at` / `atPut` protocol. Indexed access is not dynamic slot access: `object["foo"]` is not automatically equivalent to `object.foo`, and an object does not become indexable merely because it has slots.

The final postfix operation of a chain determines its role as a target: a final member suffix permits `:` or `=`; a final index suffix permits `=` only (see Slot-Creation and Assignment Targets).

Receiver-aware semantic lowering still distinguishes a member invocation such as `dog.speak()` from a plain invocation such as `f()`, so that `this` and `methodHome` are preserved correctly.

## 19. Trailing Closures

A trailing closure is permitted only as part of a call suffix, immediately after a completed call, and is always parameterless. The closure body must follow the completed call's `argument-list` in the same continuing token sequence: when a logical `NEWLINE` token intervenes between the completed call and the closure body, the braces are not attached as a trailing closure. A bare expression followed by a closure body does not gain trailing-closure meaning unless the preceding expression has just completed a call suffix.

```ebnf
trailing-closure =
    closure-body ;
```

This production preserves the call-suffix form:

```ebnf
call-suffix =
    argument-list, [ trailing-closure ] ;
```

The `trailing-closure` may be present only when no logical `NEWLINE` token separates the completed `argument-list` from the `closure-body`: the next parser token after a completed call suffix must be the `{` of the closure body. Horizontal whitespace and comments that produce no `NEWLINE` tokens remain ordinary lexical separation and need no grammar production here.

A trailing closure never has its own parameter list. The parentheses of the call's `argument-list` always contain call arguments; they are never a parameter list for the trailing closure. Core v0.1 provides no parameterized trailing-closure syntax: a form such as:

```text
foo(call-arguments...) (closure-parameters...) {
    closure-body
}
```

is not recognized as a trailing closure.

A call may be followed by at most one trailing closure.

A trailing closure:

```js
foo(args...) {
    body
}
```

is exactly equivalent to:

```js
foo(
    args...,
    () => {
        body
    }
)
```

The trailing closure is appended as the final argument of the invocation.

A closure that requires parameters is written as an ordinary explicit closure expression in ordinary call-argument position:

```js
items.each((item) => {
    print(item)
})

collection.reduce(initial, (acc, item) => {
    ...
})
```

The exact position of the closure among a particular API's arguments is defined by that API. The trailing-closure sugar only appends a parameterless closure as the final argument.

A `parameter-list` exists only where ordinary closure syntax requires it:

```ebnf
closure-expression =
    parameter-list, "=>", closure-body ;
```

The `=>` explicitly distinguishes closure parameters from a parenthesized expression. Therefore `(x)` is always a parenthesized expression, `(x) => { body }` is always an ordinary closure expression, and there is no third interpretation of `(x)` as the parameter declaration of a trailing closure. No parameter list is inferred from a parenthesized expression, and no semantic/type-based interpretation decides whether parentheses contain closure parameters. Because parameterized trailing closures no longer exist, the grammar requires no parser lookahead and no speculative parsing to distinguish `(x)` from a trailing-closure parameter list: issue B6 is resolved structurally.

Under the expression-separator rules, the removal of parameterized trailing closures does not make a form such as:

```text
foo() (x) {
    body
}
```

valid as two adjacent same-line expressions: whitespace alone does not separate expressions (see Expression Separators).

A parameterless trailing closure may also follow a call without ordinary arguments:

```js
condition.ifTrue() {
    print("yes")
}
```

is equivalent to:

```js
condition.ifTrue(
    () => {
        print("yes")
    }
)
```

A trailing closure introduces no new runtime concept: it is syntactic sugar for an ordinary Closure appended as the final call argument.

Newline placement between the completed call and the trailing closure (issue B7): a completed call is syntactically complete, so a logical `NEWLINE` after its `argument-list` is a separating newline under the expression-separation and newline-continuation rules (see Whitespace and Newlines and Expression Separators), and a `{` following that `NEWLINE` does not attach as a trailing closure. `{` is not a complete-before-newline continuation exception; the only such exception remains the leading structural `.` rule. Repeated separating `NEWLINE` tokens (blank lines) and `;` separators likewise prevent attachment. This closes issue B7.

These are valid trailing closures:

```js
foo() {
    body
}

foo(a, b) {
    body
}

foo()    {
    body
}

foo() /* comment */ {
    body
}

foo() /*
    comment
*/ {
    body
}
```

In these forms the braces do not attach as a trailing closure to the preceding call:

```text
foo()
{
    body
}

foo()

{
    body
}

foo() // comment
{
    body
}

foo();
{
    body
}
```

A block comment behaves as whitespace and consumes embedded logical source newlines without producing `NEWLINE` tokens (see Comments), so it may appear between the call and the trailing closure, including as a multiline comment. A line comment does not consume its terminating logical source newline, so a `//` comment leaves a separating `NEWLINE` token and prevents attachment. Indentation plays no role in the decision: the rule concerns logical `NEWLINE` tokens, not physical source formatting. No special comment-sensitive trailing-closure rule exists; the result follows entirely from tokenization.

What the braces of a non-attached `{ ... }` after a separating newline may mean, if anything, is governed by the ordinary grammar independently; the normative claim is only that they are not attached as a trailing closure to the preceding call. This revision does not restore parameterized trailing closures: a trailing closure remains parameterless, and `foo() (x) { body }` is not trailing-closure syntax.

## 20. Object Construction vs Trailing Closure

The distinction is intentional:

```js
foo { ... }       // object whose parent is foo
foo() { ... }     // invoke foo with a parameterless trailing closure
```

`foo() { ... }` is not a combined object-construction form. It desugars as `foo(() => { ... })`: a call whose final argument is a parameterless closure.

Likewise, if:

```js
Point(args) { ... }
```

is valid under trailing-closure syntax, it means invocation of `Point` with a parameterless trailing closure. The `args` are ordinary call arguments and the braces desugar as `() => { ... }`, so the form means `Point(args, () => { ... })`. It never means "construct Point(args) and then evaluate this object body".

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
    [ newline-run ],
    [ object-body-line-items ],
    [ newline-run ] ;

object-body-line-items =
    object-body-line,
    { newline-run, object-body-line } ;

object-body-line =
    object-body-item,
    { ";", object-body-item } ;

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

There is no indexed slot-creation lowering: a final index suffix is not a slot-creation target, so `object[index]: value` is a syntax error and is never represented as `CreateSlot` or as a message send.

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

`//` starts a line comment. A line comment continues until the next logical source newline or end of file. The comment ends immediately before the newline, so none of the newline's code points — including the `CR` of a `CRLF` sequence — are consumed by the comment. The terminating logical source newline remains available for ordinary newline tokenization and produces the usual `NEWLINE` token.

`/*` starts a block comment. `*/` ends a block comment. A block comment is one lexical construct: it consumes all source characters from its opening `/*` through its matching closing `*/`, including logical source newlines. The first `*/` after the opening `/*` terminates the comment. Block comments do not nest in Core v0.1.

A logical source newline (`LF`, `CR`, or `CRLF`) occurring inside a block comment is consumed as part of the comment and does not produce a `NEWLINE` token. A `CRLF` inside a block comment remains one logical source newline for source-position and logical-line accounting, but no `NEWLINE` token is emitted for it.

This contrasts with `//`: a line comment terminates immediately before its terminating logical source newline, leaving that newline available for ordinary `NEWLINE` tokenization, whereas a block comment consumes the logical source newlines inside it.

A block comment has the token-separation effect of insignificant whitespace regardless of whether it contains logical source newlines. The two forms

```js
a() /* comment */ b()

a() /*
    comment
*/ b()
```

have the same token-separation effect: the internal logical newlines do not become expression separators. Newlines outside a block comment remain governed by the normal logical-newline rules and may separate expressions according to the grammar.

An unterminated block comment is a lexical error.

Comment delimiters inside String literals have no special meaning.

Comments produce no parser token and no language-level value. They are lexical constructs with whitespace-like token-separation behavior; they do not add code points to the horizontal-whitespace set, which remains exactly SPACE and TAB.

`#` is not a comment delimiter.

Core v0.1 defines no special documentation-comment syntax.

## 39. Compact EBNF

The compact grammar below incorporates the syntax decisions made through revision 60. Semantic validation still applies after parsing.

```ebnf
program =
    expression-sequence ;

expression-sequence =
    [ newline-run ],
    [ expression-line-items ],
    [ newline-run ] ;

expression-line-items =
    expression-line,
    { newline-run, expression-line } ;

expression-line =
    expression,
    { ";", expression } ;

newline-run =
    newline,
    { newline } ;

expression =
      slot-creation
    | assignment
    | non-local-return
    | binary-expression ;

slot-creation =
    slot-creation-target, ":", expression ;

assignment =
    assignment-target, "=", expression ;

non-local-return =
    "^", expression ;

slot-creation-target =
      identifier
    | member-expression ;

assignment-target =
      identifier
    | member-expression
    | indexed-target ;

indexed-target =
    postfix-expression, "[", expression, "]" ;

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
    [ newline-run ],
    [ object-body-line-items ],
    [ newline-run ] ;

object-body-line-items =
    object-body-line,
    { newline-run, object-body-line } ;

object-body-line =
    object-body-item,
    { ";", object-body-item } ;

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
    "(", [ layout ], [ parameter-items ], [ layout ], ")" ;

parameter-items =
      rest-parameter
    | parameter, { ",", [ layout ], parameter },
      [ ",", [ layout ], rest-parameter ] ;

parameter =
    identifier, [ "=", expression ] ;

rest-parameter =
    "...", identifier ;

argument-list =
    "(", [ layout ], [ argument-items ], [ layout ], ")" ;

argument-items =
    argument, { ",", [ layout ], argument } ;

argument =
      expression
    | "...", expression ;

layout =
    newline,
    { newline } ;

trailing-closure =
    closure-body ;

literal =
      number-literal
    | string-literal
    | "true"
    | "false"
    | "null" ;
```

A parser may implement the expression portion using recursive descent plus Pratt parsing. Custom symbolic operators form their own precedence domain: mixing them with standard binary operators requires parentheses.

`layout` denotes one or more consecutive logical `NEWLINE` tokens consumed as continuation inside a necessarily-incomplete delimited construct (see Whitespace and Newlines). It is formatting, not an element separator: commas are the only separators between list elements, and trailing commas are not permitted.

As in the normative grammar, `trailing-closure` may follow a completed `argument-list` in `call-suffix` only when no logical `NEWLINE` token intervenes between them; the restriction is stated normatively in Trailing Closures and needs no additional production here.

Indexed assignment is recognized because an `assignment-target` may end in `[ expression ]`; it lowers to `atPut` rather than a slot `Assign`. A `slot-creation-target` may never end in an index suffix, so an indexed `:` has no parse.

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

rather than a slot `Assign` node. The `=` in indexed assignment does not mean "modify an already-existing indexed entry": whether `atPut` creates a new entry, replaces an existing one, extends a collection, requires an existing or in-range index, rejects the operation, or implements other domain-specific behavior is defined by the receiver's `atPut` protocol. The receiver expression and the index expression are each evaluated exactly once.

Slot creation has no indexed form. The final postfix operation determines the target category (see Slot-Creation and Assignment Targets): `receiver[index]: value`, `object["foo"]: value`, and `object.foo[index]: value` are syntax errors and have no lowering, while `object[index].member: value` remains a valid slot creation on the object produced by the indexed access.

Indexed access has the same high postfix-binding role as member access and calls. Chained forms are permitted when otherwise grammatically valid, for example:

```js
matrix[row][column]
objects[index].name
factory()[index]
```


## Parameters, Rest Parameters, and Argument Spread

Closure parameter lists may contain ordinary parameters, parameters with defaults, and at most one trailing rest parameter.

The normative productions are defined in Sections 16-17 and in the Compact EBNF. A rest parameter must be the final parameter. Defaults, rest capture, and spread are part of the canonical grammar rather than post-parse extensions.

Elements of parameter and argument lists are separated by commas only. A comma is strictly a separator between two list elements, not a terminator: trailing commas are syntax errors, and one or more logical newlines inside the delimiters are continuation/layout under the newline-continuation rules in Whitespace and Newlines, never a substitute for a missing comma.

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

as a combined construction form. When accepted by the trailing-closure grammar, the braces are a parameterless trailing closure argument to `Parent(args)`.

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

A `.` belongs to a decimal numeric literal only when it is immediately followed by a decimal digit:

```text
1.0       -> FLOAT("1.0")
1.        -> INTEGER("1") DOT
.5        -> DOT INTEGER("5")
1.to(10)  -> INTEGER("1") DOT IDENTIFIER("to") LPAREN INTEGER("10") RPAREN
```

`1.` and `.5` are not numeric literals as complete source sequences. This does not mean that either complete sequence is necessarily a lexical error: the lexer tokenizes them as shown above, and whether the resulting token sequence is syntactically valid is the parser's responsibility.

Decimal exponents use `e` or `E`, optionally followed by `+` or `-`, and require at least one exponent digit:

```js
1e10
1e-10
1.5e+20
```

Hexadecimal, binary, and octal Float literals are not supported in Core v0.1.

A `.` immediately following a complete radix-prefixed Integer literal is a structural `.` token when it is not immediately followed by a decimal digit: `0b10.foo` tokenizes as `INTEGER("0b10")` `.` `IDENTIFIER("foo")` and `0xFF.toString()` tokenizes as `INTEGER("0xFF")` `.` `IDENTIFIER("toString")` `(` `)`. When the `.` is immediately followed by a decimal digit, the source sequence is an attempted unsupported radix Float literal and is a lexical error: `0b10.5`, `0o17.25`, and `0x1.8` are lexical errors rather than being split into `INTEGER` `.` `INTEGER` tokens.

Numeric type suffixes such as `L`, `f`, or `d` are not supported.

`NaN` and `Infinity` are not special numeric literal syntax.

**Malformed Numeric Literals and Numeric Token Termination:**

Once a source sequence has begun as a numeric literal, if its immediately adjacent continuation makes that numeric form malformed or creates an invalid numeric/identifier boundary, the lexer reports a lexical error. It must not split the malformed sequence into otherwise valid tokens in order to recover it.

- A radix prefix (`0x`, `0X`, `0b`, `0B`, `0o`, `0O`) must be followed by at least one valid digit for that radix. Once a radix prefix has been recognized, an invalid digit or identifier-like continuation does not cause the lexer to fall back to an `INTEGER("0")` token plus another token.
- A `.` immediately following a complete radix-prefixed Integer literal is a structural `.` token unless it is immediately followed by a decimal digit; a `.` immediately followed by a decimal digit is an attempted unsupported radix Float literal and a lexical error.
- Once `e` or `E` has begun the exponent part of a decimal numeric literal, the exponent must be complete according to the exponent rules above.
- Invalid underscore placement inside or immediately adjacent to a numeric literal is a lexical error according to the underscore rules above.
- An identifier cannot begin immediately after a numeric literal without a lexical boundary.

The following source sequences are therefore lexical errors:

```text
0x
0xG
0b2
0o8

0b10.5
0o17.25
0x1.8

2e
2e+
2e-

1__2
1_
0x_FF

123abc
```

For example, `0xG` is a lexical error rather than `INTEGER("0")` followed by `IDENTIFIER("xG")`, and `123abc` is a lexical error rather than `INTEGER("123")` followed by `IDENTIFIER("abc")`. Similarly, `0b10.5` is a lexical error rather than `INTEGER("0b10")` followed by `.` and `INTEGER("5")`.

Valid token boundaries remain valid and are not affected by this rule: punctuation, whitespace, structural delimiters, and operators may terminate a numeric token according to the existing lexical grammar. The decimal-point vs. member-access dot rules above are unchanged.

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

For triple-double-quoted String literals, indentation normalization follows the Core v0.1 rule defined above: remove the common leading indentation of all non-empty content lines, preserve relative indentation beyond that common baseline, and omit the leading/trailing newline only when the delimiter placement matches the rule. Retained logical source newlines preserve their original source code points in the resulting String; no newline normalization is applied. Escape processing still follows the standard Core v0.1 String escape rules, and triple-double-quoted strings remain non-raw strings.

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
+   -  *   /   %   <   >   !   ^
```

The exact one-character spellings `!` and `^` are reserved/standard tokens and are never custom binary operators. They are classified as such wherever they appear; the grammar assigns their roles (prefix `!`, non-local-return `^`), not the lexer. Consequently `a ! b` and `a ^ b` are syntax errors rather than custom binary operator expressions.

The characters `!` and `^` remain members of the custom operator alphabet. Longer symbolic spellings containing them, such as `!!`, `^^`, `!^`, and `^!`, do not exactly match any reserved/standard spelling and are therefore `CUSTOM_OPERATOR` tokens, so `a !! b`, `a ^^ b`, `a !^ b`, and `a ^! b` are custom binary operator expressions.

Symbolic token classification is purely lexical and does not depend on parser position. Maximal munch first forms the longest valid symbolic spelling at a source position; that complete spelling is then classified as a reserved/standard token when it exactly matches a reserved/standard spelling, and as `CUSTOM_OPERATOR` otherwise. A longer custom spelling is never broken up in order to prefer a shorter reserved/standard token, and there is no prefix-position exception. For example:

```text
!x      -> ! IDENTIFIER("x")
!!x     -> CUSTOM_OPERATOR("!!") IDENTIFIER("x")
^value  -> ^ IDENTIFIER("value")
^^x     -> CUSTOM_OPERATOR("^^") IDENTIFIER("x")
a ! b   -> IDENTIFIER("a") ! IDENTIFIER("b")
a !! b  -> IDENTIFIER("a") CUSTOM_OPERATOR("!!") IDENTIFIER("b")
a ^ b   -> IDENTIFIER("a") ^ IDENTIFIER("b")
a ^^ b  -> IDENTIFIER("a") CUSTOM_OPERATOR("^^") IDENTIFIER("b")
```

Whether the resulting token sequence is syntactically valid is the parser's responsibility. `!x` parses as prefix `!` applied to `x`; `^value` parses as a non-local return of `value`. `!!x` and `^^x` are syntax errors because a custom binary operator requires a left operand; `a ! b` and `a ^ b` are syntax errors because `!` and `^` are not custom binary operator tokens; `a !! b` and `a ^^ b` parse as custom binary expressions with selectors `"!!"` and `"^^"`. The same maximal-munch rule applies to other adjacent symbolic spellings such as `^^x`, `--x`, `-!x`, and `!-x`; they are not split into stacked prefix operators. Explicitly nested prefix operations are written with structural punctuation, for example `!(!x)`.

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
