# Core Language Grammar v0.1

Language version: 0.1  
Document revision: 186
Status: Draft  
Last updated: 2026-09-03
## Prelude Binding Note

Prelude bindings introduce no additional grammar. The shared standard prelude is frozen by runtime semantics. Therefore `name = value` cannot modify a binding found only in the prelude; `name: value` creates a local slot and may explicitly shadow that name.

Freezing is shallow: because the prelude is shared between Actors, any Protos object physically shared through it must be semantically immutable for the duration of that sharing, and mutable standard-library state remains Actor-local.

## 1. Scope

This document defines the lexical grammar, expression grammar, precedence rules, and mandatory syntactic desugarings of the language.

It does not redefine the object model or runtime semantics specified in `PROTOS_LANGUAGE_SPEC.md`.

The grammar is written in EBNF:

```text
{ x }     zero or more repetitions
[ x ]     optional
x | y     alternative
"..."     literal token
[ lookahead != "t" ]          alternative guard: an alternative carrying this guard may be
                              entered only when the next parser token in the continuing token
                              sequence is not the literal token "t". A guard selects nothing
                              and contributes no token to the parse; it is not an optional
                              element and never derives an empty alternative.
```

## 2. Identifiers

Protos identifiers are Unicode-aware and case-sensitive.

### 2.1 Normative Unicode Version

Core v0.1 adopts **The Unicode Standard, Version 17.0.0** as its normative Unicode repertoire and property version.

Every Unicode character property used normatively by Core v0.1 is interpreted according to Unicode 17.0.0 unless this specification explicitly states otherwise. In particular, the `XID_Start` and `XID_Continue` properties used by identifier recognition are those defined by Unicode 17.0.0.

Identifier NFC conformance is determined by Unicode Normalization Form C as defined by the Unicode normalization specification applicable to Unicode 17.0.0.

The Unicode database, normalization implementation, or character-property tables supplied by a host runtime, standard library, operating system, virtual machine, or implementation platform are not part of Protos semantics. An implementation may use such facilities only when their observable result is equivalent to the normative Unicode 17.0.0 rules required by Core v0.1.

The normative Unicode version is fixed for a Protos language version. Changing it changes the lexical language accepted by Protos and therefore requires a language-version change; a document-revision update alone must not change the normative Unicode version.

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

Reserved-word spellings are nevertheless valid structural member names in the contextual position immediately following a member-access `.`. The grammar category `member-name` is:

```ebnf
member-name =
      identifier
    | "this"
    | "context"
    | "args"
    | "super"
    | "true"
    | "false"
    | "null" ;
```

`member-name` is used only where the grammar structurally expects a name immediately after `.`, in `member-suffix`, `member-expression`, and `super-message-send` (see Member Access, Calls, Indexing, and Postfix Expressions and Super Message Send). In that position a reserved spelling denotes an ordinary slot or message name and does not retain its expression-level intrinsic, literal, or special meaning. Therefore these are valid structural member accesses:

```text
obj.name
obj.this
obj.context
obj.args
obj.super
obj.true
obj.false
obj.null
```

and reserved spellings may be read, invoked, modified, or created through member operations like any other member name:

```text
obj.true()
obj.null = value
obj.super: value
obj.a.this
f: obj.true
```

The leading `super` of `super.foo()` continues to introduce the special super message send; the name following `super.` is a `member-name`, so reserved spellings are valid super message names as well. Thus `super.true()`, `super.this()`, and `super.super()` are syntactically valid super message sends whose message names are respectively `true`, `this`, and `super`. This does not make `super` a first-class value.

Reserved words remain invalid wherever the grammar expects `identifier`: as parameter names, rest-parameter names, bare assignment targets, bare slot-creation targets, or any other non-member name position. These remain invalid:

```text
this: value
context: value
args: value
super: value
true: value
false: value
null: value

(a, true) => { ... }
(...super) => { ... }

super
foo(super)
f: super.foo
```

Bare `super`, `x: super`, `foo(super)`, and method extraction such as `f: super.foo` remain invalid; only the member-name position following the `.` of a valid `super-message-send` is generalized.

This revision does not introduce contextual lexing. The lexer continues to tokenize the seven reserved spellings as their dedicated reserved tokens rather than as ordinary identifier tokens; it does not need to inspect whether a token follows `.` or to reclassify reserved tokens. The parser accepts either an identifier token or one of the seven reserved tokens when parsing `member-name`.

Names provided by the standard prelude, such as `Object`, `Future`, `Number`, `String`, `Map`, `IdentityMap`, or `Context`, are not reserved words. In particular, the standard prelude prototype `Context` is not a reserved word and is distinct from the reserved intrinsic `context`. Error object names are not reserved.

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

Exact number formats are defined separately from the core string escape set; the normative `number-literal` grammar is defined in the Numeric Literals section.

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

Invalid or incomplete escape sequences are lexical errors. Escape validation occurs as part of String literal lexical recognition. Octal escapes are not supported. `\xNN` escapes are not supported.

Single-quoted, double-quoted, and triple-double-quoted String literals use the same escape rules. Triple-double-quoted strings are multiline String literals, not raw strings. Triple-single-quoted strings are not supported.

**Formal String Token Grammar:**

The following EBNF defines the valid lexical token shapes for the three Core v0.1 String forms. It describes source spelling, not the resulting String value: an escape sequence such as `\n` is two source characters forming one escape and evaluates to String content containing U+000A according to the existing String semantics. Error recovery and diagnostic classification are not part of the valid-token grammar; the invalid/incomplete-escape rule above and the Unterminated String Literals and Triple-Double Quote-Run Recognition rules below govern what happens when String recognition begins but the source does not form a valid String token.

```ebnf
single-quoted-string =
    "'", { single-string-item }, "'" ;

single-string-item =
      escape-sequence
    | single-string-ordinary-character ;

double-quoted-string =
    "\"", { double-string-item }, "\"" ;

double-string-item =
      escape-sequence
    | double-string-ordinary-character ;

triple-double-quoted-string =
    "\"\"\"", triple-double-string-content, "\"\"\"" ;

triple-double-string-content =
    { triple-double-string-item } ;

triple-double-string-item =
      escape-sequence
    | triple-double-string-ordinary-character
    | logical-source-newline
    | "\""
    | "\"", "\"" ;

escape-sequence =
    "\\", escape-continuation ;

escape-continuation =
      "\\"
    | "'"
    | "\""
    | "n"
    | "r"
    | "t"
    | "b"
    | "f"
    | unicode-escape-tail ;

unicode-escape-tail =
    "u", "{", unicode-hex-digits, "}" ;

unicode-hex-digits =
    hexadecimal-digit, { hexadecimal-digit } ;
```

The `single-string-ordinary-character`, `double-string-ordinary-character`, and `triple-double-string-ordinary-character` symbols are lexical metavariables over single source code points, defined exactly as follows:

- `single-string-ordinary-character` denotes any single source code point (Unicode scalar value) that is not `'` (U+0027), not `\` (U+005C), and not part of a logical source newline. Because `LF` (U+000A) and `CR` (U+000D) are excluded individually, the `CRLF` logical newline is excluded as a whole; single-quoted String content therefore contains no raw logical source newline.
- `double-string-ordinary-character` denotes any single source code point that is not `"` (U+0022), not `\` (U+005C), and not part of a logical source newline, with the same raw-logical-newline exclusion as single-quoted String content.
- `triple-double-string-ordinary-character` denotes any single source code point that is not `"` (U+0022), not `\` (U+005C), and not part of a logical source newline. Because `LF` (U+000A) and `CR` (U+000D) are excluded individually, the `CRLF` logical newline is excluded as a whole; raw logical source newlines in triple-double String content are recognized exclusively through the separate `logical-source-newline` item below.
- `logical-source-newline` is the logical source newline defined in the Whitespace and Newlines section: exactly one `LF` (U+000A), one `CR` (U+000D), or one `CRLF` (U+000D U+000A), consumed atomically as one logical newline.
- `hexadecimal-digit` is the hexadecimal digit production defined in the Numeric Literals section: exactly `0`-`9`, `a`-`f`, and `A`-`F`.

The ordinary-character metavariables place no restriction on other Unicode source code points. Ordinary String text may contain any Unicode source characters subject only to the exclusions above; String content is not ASCII-restricted and is not governed by the identifier `XID_Start`/`XID_Continue` rules.

The valid escape set is exactly the set encoded by `escape-sequence`; there are no other escapes. In particular, `\0`, `\xNN`, octal escapes, named Unicode escapes, and interpolation escapes are not valid escape sequences.

`unicode-hex-digits` contains at least one hexadecimal digit and at most six; a `\u{HEX}` escape with more than six digits is a lexical error. `\u{HEX}` must denote a valid Unicode scalar value: surrogate code points (U+D800..U+DFFF) and values greater than U+10FFFF are invalid. The digit-count and scalar-value restrictions are normative lexical validation rules applied during String recognition.

The `double-quoted-string` production describes the valid ordinary double-quoted token shape. Whether a source position beginning with `"""` opens a triple-double-quoted String or an ordinary double-quoted String is decided by the Triple-Double Quote-Run Recognition rules below; that opening priority is not encoded in this EBNF.

`triple-double-string-content` permits `"` and `""` as content items, but content ends before the first sequence of three consecutive unescaped double-quote characters; that first `"""` is the closing delimiter and consumes exactly those three quotes. A single `"` item is therefore used only when it is not followed by two further unescaped double-quote characters, and a `""` item only when it is not followed by a third unescaped double-quote character. Escaped double quotes (`\"`) are String content and do not participate in closing-delimiter recognition. This is the quote-run rule restated in grammar terms; the authoritative rule is Triple-Double Quote-Run Recognition below.

**Newline Handling:**

A logical source newline is one `LF` (U+000A), one `CR` (U+000D), or one `CRLF` (U+000D U+000A) sequence; the normative definition is in the Whitespace and Newlines section below.

Single-quoted and double-quoted String literals are single-line literals. A logical source newline is not permitted inside a single-quoted or double-quoted String literal. Encountering `LF`, `CR`, or `CRLF` before the matching closing quote is a lexical error. Newline characters may be represented in these literals using the existing `\n` and `\r` escape sequences, which denote String content and are distinct from raw source newlines.

Triple-double-quoted String literals are the Core v0.1 syntax for source-level multiline text. Logical source newlines are permitted and are part of the literal content, subject to the multiline indentation normalization rule. Retained source newlines preserve their original source code points in the resulting String: `LF` remains U+000A, `CR` remains U+000D, and `CRLF` remains U+000D U+000A. There is no implicit newline normalization of String content.

**Unterminated String Literals:**

Once String-literal recognition has begun, reaching the end of source before the required closing delimiter is a lexical error. This applies to all three Core v0.1 String forms: single-quoted (`'...'`), double-quoted (`"..."`), and triple-double-quoted (`"""..."""`). An unterminated String literal never produces a partial String token. The lexer must not recover by treating the opening quote as another token, emitting the accumulated content as a partial String, splitting the malformed literal into otherwise valid tokens, implicitly inserting a closing delimiter, or interpreting the end of source as the closing delimiter. The parser never receives a successfully formed String token for an unterminated literal.

The existing raw-newline rule is unchanged: a logical source newline encountered before the matching closing quote in a single-quoted or double-quoted String literal is already a lexical error and terminates String recognition before any end-of-source determination. The end-of-source rule applies when the end of source is reached while String recognition is still active and no earlier lexical error has already terminated recognition.

The existing incomplete-escape rule is unchanged. If the end of source is reached after a backslash or during an incomplete escape while String recognition is still active, the source is a lexical error and no String token is emitted. Core v0.1 does not require a normative priority between an "incomplete escape" classification and an "unterminated String" classification; lexical rejection is the required observable behavior.

Protos has no separate character literal or character type. `'a'` and `"a"` both evaluate to a String containing the single-character text `a`.

String interpolation is not part of Core v0.1. Inside a String, `${...}` has no special meaning and is treated as ordinary literal text.

**Triple-Double Quote-Run Recognition:**

Outside a String lexical construct, when the lexer is positioned at a double quote and the next three source characters are `"""`, they begin a triple-double-quoted String. This takes priority over recognizing that position as an ordinary double-quoted String opener followed by another double quote. The triple-double opening delimiter is exactly three double quotes, and the lexer consumes exactly those three. Do not reinterpret the first two quotes as an empty ordinary double-quoted String in order to avoid opening a triple-double String.

Inside an open triple-double-quoted String, the first sequence of exactly three consecutive unescaped double-quote characters encountered is the closing delimiter:

- one unescaped `"` that is not the start of `"""` is ordinary String content;
- two consecutive unescaped `"` characters that are not followed by a third unescaped `"` are ordinary String content;
- three consecutive unescaped `"` characters form the closing delimiter.

The closing delimiter consumes exactly those three double-quote characters. Any source characters immediately following it, including additional double quotes, are outside the completed String and are lexed normally from that point. There is no rule that greedily consumes a run of four, five, six, or more double quotes as one delimiter, and quote-run decisions are not backtracked.

Escape recognition occurs while scanning String content. An escaped double quote (`\"`) is String content and is not an unescaped quote participating in a closing delimiter; escaping at least one participating double quote prevents three consecutive source quotes from forming a closing delimiter. The `\"` escape keeps its ordinary meaning, and no triple-quote escape is introduced.

Examples:

```text
""""""          ->   """ + """          one empty triple-double-quoted String token
"""text""""     ->   """text""" + "     the remaining " begins an ordinary String opener
"""text"""""    ->   """text""" + ""    the remaining "" form an empty ordinary String
"""text""""""   ->   """text""" + """   the remaining """ begin another triple-double String
""""text        ->   """ + "text        the fourth " is the first content character
"""""text       ->   """ + ""text       the two remaining " are ordinary content
```

After a closing delimiter, remaining quotes begin the lexical construct the ordinary rules produce from that position. If that construct is a String and the end of source is reached before its required closing delimiter, the complete source is a lexical error under the Unterminated String Literals rules above. A lexically valid sequence of adjacent String tokens that the parser does not accept is a parser syntax error, not a lexical error; Core v0.1 defines no implicit String-literal concatenation.

For triple-double-quoted String literals, indentation normalization is defined as follows:

- If the opening `"""` is immediately followed by a logical source newline, that newline is not part of the resulting String. A removable `CRLF` is removed as one logical newline: both U+000D and U+000A.
- If the closing `"""` is immediately preceded by a logical source newline whose preceding content on that line is only indentation whitespace (exactly SPACE or TAB; see Whitespace and Newlines), that final newline and indentation are not part of the resulting String. A removable `CRLF` is removed as one logical newline: both U+000D and U+000A.
- The literal is split into logical content lines at each logical source newline; `LF`, `CR`, and `CRLF` each count as one logical newline for this splitting.
- The closing delimiter alone establishes the structural indentation prefix. When the closing delimiter terminates an indentation-only trailing line (the case excluded above), the structural indentation prefix is exactly the sequence of SPACE and TAB characters on that line immediately before the closing delimiter; the prefix may be empty, which is the case when the closing delimiter begins its line. When content flows into the closing delimiter on its source line, no structural indentation prefix exists and no indentation normalization is performed.
- Indentation normalization applies only where a structural indentation prefix exists. Where no structural indentation prefix exists, no indentation or other whitespace is removed from any content line; this includes whitespace-only content lines, whose SPACE and TAB characters are ordinary String content and are preserved verbatim. Blank-line whitespace stripping is part of multiline indentation normalization and never applies unconditionally: no structural indentation prefix ⇒ no indentation normalization.
- Indentation is never computed from the content lines: no minimum-indent rule, common-visual-column rule, longest-common-prefix computation, or editor-tab-stop rule is used.
- Where the closing delimiter establishes a structural indentation prefix, every non-blank content line must begin with exactly that prefix, compared as exact source characters. The prefix is removed exactly once from the beginning of each non-blank content line; the remainder of the line, including any further leading SPACE or TAB characters, is preserved.
- SPACE and TAB are distinct source characters and are never equivalent for indentation: a TAB does not equal any number of SPACE characters, Core v0.1 defines no semantic tab width, and matching is by exact source-character prefix rather than by visual column. A prefix may contain both SPACE and TAB characters; mixed indentation is legal when every applicable content line begins with exactly the same prefix.
- Where the closing delimiter establishes a structural indentation prefix, a non-blank content line that does not begin with the exact prefix — fewer prefix characters, SPACE where the prefix requires TAB, TAB where the prefix requires SPACE, or any other difference — makes the literal invalid. Consistent with the existing String-literal lexical-error model, this is a lexical error: no String token and no String value is produced, and no recovery behavior is defined.
- Where a structural indentation prefix exists, a blank content line — a content line containing no characters other than SPACE and TAB (possibly none) — is exempt from the prefix requirement and need not contain the complete structural indentation prefix; its SPACE and TAB characters are removed as incidental source-formatting indentation, so a source blank line contributes an empty logical line rather than whitespace caused solely by source indentation. No whitespace is removed from a non-blank content line beyond the single structural prefix.
- Escape processing follows the already-defined Core v0.1 String escape rules. Indentation matching and stripping operate on the raw source characters at the beginning of each content line before escape sequences are interpreted; an escape sequence is not a source SPACE or TAB and never satisfies the structural indentation prefix.
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

Wherever the multiline triple-double-quoted String rules refer to "indentation whitespace", the characters that may constitute indentation whitespace are exactly SPACE (U+0020) and TAB (U+0009). SPACE and TAB are distinct and are never equivalent for indentation purposes, Core v0.1 defines no semantic tab width, indentation matching is by exact source-character prefix rather than by visual column, and mixed SPACE/TAB indentation is legal when the exact structural prefix matches (see the triple-double-quoted indentation normalization rule above).

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

A Closure's `=>` likewise requires a closure body after it, so a logical newline after `=>` is continuation: the body may begin on a later logical source line (see Closures). The same continuation rule never lets a later line's `=>` attach to a preceding complete expression: an `identifier` before a separating logical `NEWLINE` is a complete expression, so the `=>` on the following line cannot begin an expression.

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

contain the same three expressions in the same order. The same rule applies inside object bodies, for ordinary expressions and composition items alike:

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

Core Grammar v0.1 defines no dedicated `import` declaration syntax and no `export` declaration syntax or separate export mechanism. `import(specifier)` is an ordinary call/message operation exposed by the standard environment; it yields the module instance, and cross-module access occurs explicitly by obtaining a module instance and accessing its slots through ordinary member lookup. Module identity, caching, initialization states, cycle handling, and host-specific module-specifier resolution are runtime/module-loader semantics rather than grammar rules (see the module rules in `PROTOS_LANGUAGE_SPEC.md` and `PROTOS_RUNTIME_SEMANTICS.md`).

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
    "super", ".", member-name, argument-list;
```

The name following `super.` is a `member-name` (see Identifiers), so reserved-word spellings are valid super message names: `super.true()`, `super.this()`, and `super.super()` are syntactically valid super message sends whose message names are `true`, `this`, and `super`, respectively. This does not make `super` a first-class value.

`super` is not a value. Consequently bare `super`, passing `super` as an argument, assigning it to a slot, and extracting `super.member` without invoking it are syntax errors in the core grammar.

## 12.2 Intrinsic References

```ebnf
intrinsic-reference =
      "this"
    | "context"
    | "args" ;
```

`this`, `context`, and `args` are intrinsic references, not ordinary identifiers. `true`, `false`, and `null` are literals only (see Literals), and `super` is governed exclusively by `super-message-send` (see Super Message Send); none of them is an intrinsic reference.

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

An object body contains a sequence of `object-body-item`. An object-body item is either an ordinary `expression` or a contextual `composition-item` of the form `...expression`.

Object-body items and ordinary expressions share the same separator rules: a logical `NEWLINE` separates items written on different logical source lines, and `;` separates items written on the same logical source line. Blank lines are permitted and create no empty items; leading, trailing, and consecutive `;` are syntax errors. There is no implicit adjacency separator: two items must be separated by a logical `NEWLINE` or a `;`.

Composition is contextual. `...expression` is valid only as an object-body item, is not a general expression form, and does not become valid merely because braces are present. Closure bodies continue to contain only ordinary expressions (see Closures).

Examples:

```js
{
    name: "Rex"
}

animal {
    name: "Rex"
}

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

this {
}

(getPrototype()) {
}

(true) {
}

(42) {
}

("hello") {
}
```

The alternatives are intentionally broad. `this`, `context`, and `args` are valid through `intrinsic-reference`; identifiers and member expressions such as `Object` or `library.models.animal` are valid directly; and a parenthesized expression may compute a parent dynamically. Literals such as `true`, `false`, `null`, numbers, and strings are not themselves `parent-expression` forms, so a literal parent must be written as a parenthesized expression: `(true)`, `(42)`, and `("hello")` are valid while the direct spellings `true`, `42`, and `"hello"` are not.

The grammar determines which source forms can denote a parent expression. Whether an evaluated parent expression is usable as a parent is not a separate grammar or runtime category: every successfully evaluated Protos expression produces a Protos object (see PROTOS_LANGUAGE_SPEC.md), and every Protos object may serve as another object's delegation parent. There is no parentability capability, classification, or secondary validation after evaluation.

## 16. Closures

A closure uses a closure body. A closure body contains ordinary expressions; object-composition items are not valid merely because braces are used. A closure body is written either as a braced sequence of expressions or as exactly one ordinary expression (Body forms below).

```ebnf
closure-expression =
    closure-parameters, "=>", closure-body ;

closure-parameters =
      parameter-list
    | identifier ;

closure-body =
      braced-closure-body
    | [ lookahead != "{" ], expression ;

braced-closure-body =
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

The `[ lookahead != "{" ]` guard (see the EBNF notation in Scope) expresses the choice between the two body forms formally in the grammar: `braced-closure-body` begins with the literal `{`, and the single-expression alternative may be entered only when the next parser token in the continuing token sequence after `=>` is not `{`. Every braced body therefore begins with `{`, no expression body may begin with `{`, and a body whose first token is `{` has exactly one derivation — the braced form. An `expression` can begin with `{` only as an `object-expression` with no parent (its `object-body`), so the same rule is what excludes a bare object expression from beginning an expression body; an object-expression body is written with ordinary parenthesized grouping (see Composition with the expression grammar).

The parenthesized `parameter-list` is required for a Closure with zero parameters, two or more parameters, a default parameter, or a rest parameter. When a Closure has exactly one parameter and that parameter is neither a default parameter nor a rest parameter, the parentheses may be omitted and the parameter written as a bare `identifier` before `=>`. The bare form is exactly equivalent to a `parameter-list` containing that one parameter:

```text
x => expression    ==  (x) => expression
x => { body }      ==  (x) => { body }
```

These are valid:

```js
x => x * 2
(x) => x * 2
() => value
(a, b) => a + b
(x = 10) => x
(...items) => items
(first, ...rest) => rest
```

and these are invalid:

```js
=> value

a, b => a + b

x = 10 => x

...items => items
```

Because a bare parameter is an `identifier`, it must satisfy the ordinary identifier rules; a reserved word is not an `identifier` and remains invalid as a parameter name (`true => value` is not a Closure parameter form). A parser distinguishes a bare-parameter Closure from an ordinary identifier expression purely syntactically: when the parser token immediately following an `identifier` is `=>`, the identifier is the parameter of a `closure-expression`; otherwise it is an ordinary identifier expression. This is ordinary one-token syntactic lookahead over the token stream; it involves no semantic, type-based, or runtime-value interpretation, and it never inspects tokens across a separating logical `NEWLINE` (see Newlines below).

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

### 16.1 Body forms and exact equivalence

A Closure body is written either as a braced `{ expression-sequence }` — the existing form — or as exactly one ordinary `expression`:

```js
(x) => x * 2
```

The single-expression form is an exact mandatory desugaring of a braced Closure whose body is a `Sequence` containing exactly that one expression:

```text
(x) => expression     ==  (x) => { expression }
x => expression       ==  x => { expression }
```

The equivalence holds for every parameter form. It is exact with respect to all Closure semantics: lexical capture by reference, `this`, `context`, `args`, `super`, method binding and `methodHome`, captured-receiver behavior, return homes and non-local return `^`, evaluation behavior, Future/async behavior, and error propagation. Expression-bodied and braced Closures are the same kind of Closure; the desugaring introduces no new callable category, no new runtime concept, and no difference in invocation semantics. Protos continues to have exactly one executable language value kind: Closure.

An expression body is exactly one ordinary `expression`, never an `expression-sequence`. The body therefore ends exactly where that `expression` ends under the Expression Separators and Whitespace and Newlines rules: a `;` after the body expression is the inline expression separator, a separating logical `NEWLINE` after a complete body expression ends the Closure, and a closing `)`, `]`, or `}` or a list comma bounds the body when the Closure appears in a delimited position. Thus `x => print(x); foo()` is a Closure whose body is `print(x)`, followed by the separate expression `foo()` after the `;`; it is not a two-expression Closure body. Likewise:

```js
x => print(x)
foo()
```

does not absorb `foo()` into the Closure body: the body `print(x)` is complete before the separating logical `NEWLINE`, so the Closure ends there and `foo()` is a later expression. Multiple expressions still require a braced body:

```js
x => {
    print(x)
    foo()
}
```

The expression body is a full ordinary `expression`, not an artificially restricted subset of expression forms: the single-expression alternative restricts nothing about the body's expression except its first token, which may not be `{` — the spelling class that the braced form claims (see Composition with the expression grammar). These are valid where semantically valid:

```js
x => x + 1

x => foo(x)

x => this.value = x

x => ^x

x => y => x + y
```

Assignment, slot creation, non-local return, nested Closures, and other ordinary expression forms are not prohibited merely because the Closure uses an expression body.

The body expression is parsed maximally: every token after `=>` that can continue an ordinary `expression` belongs to the body rather than to a surrounding expression. `double: x => x * 2` therefore binds the whole `x * 2` as the body and is never read as `(x => x) * 2`; conversely `x => x * 2(10)` binds the call `2(10)` inside the body, which is why invoking the Closure itself needs the grouping shown in Composition with the expression grammar.

### 16.2 Composition with the expression grammar

The shorthand composes with the rest of the expression grammar by ordinary precedence and ordinary expression separation. Creating a Closure never invokes it:

```js
double: x => x * 2          // double : (x => x * 2)
f = x => x + 1              // assigns the Closure object to f
foo(x => x * 2)             // passes the Closure object as an argument
```

Invoking an expression-bodied Closure requires explicit grouping where the postfix grammar would otherwise attach the call inside the body. `x => x * 2(10)` parses `2(10)` as a call inside the body, so a call of the Closure itself is written with grouping under the parenthesized-expression grammar:

```js
(x => x * 2)(10)
```

The `{` immediately after `=>` always begins the Closure's braced body; it is never reinterpreted as an object expression merely because expression bodies now exist. The `closure-body` production expresses this decision formally (see the grammar above): `braced-closure-body` requires the literal `{` as its first token, and the single-expression alternative carries the `[ lookahead != "{" ]` guard, so an expression body can never begin with `{`. The two alternatives are therefore disjoint on the token that begins the closure body in the continuing token stream after `=>`: a `{` there is derivable only as the braced form, and any other token begins the single-expression form. An `expression` begins with `{` only as an `object-expression` with no parent, so the same disjointness is what keeps a bare object expression out of expression-body position. An object-expression body therefore requires the ordinary parenthesized grouping that is already valid under the parenthesized-expression grammar:

```js
x => ({
    value: x
})
```

The boundary between the two body forms is:

```js
x => { value: x }        // braced body: the body is the slot-creation expression value: x
x => ({ value: x })      // expression body: the parenthesized expression yields an object
```

`x => { value: x }` is a Closure whose braced body is the ordinary slot-creation expression `value: x`; it is not a Closure that returns an object expression. `x => ({ value: x })` is an expression-bodied Closure whose body is the parenthesized expression, which evaluates to the object `{ value: x }`. No parser heuristic, no speculative parse, and no semantic/type-based disambiguation decides these cases; the grammar admits exactly one derivation for each spelling.

### 16.3 Right association of nested expression-bodied Closures

Nested expression-bodied Closures associate to the right:

```js
x => y => x + y    ==    x => (y => (x + y))
```

so the example above is exactly equivalent to:

```js
(x) => {
    (y) => {
        x + y
    }
}
```

Right association follows from the grammar: the body of the outer Closure is an `expression`, which may itself be a `closure-expression`.

### 16.4 Newlines around Closure syntax

Newlines around Closure syntax follow the ordinary Whitespace and Newlines and Expression Separators rules; no Closure-specific continuation, no ASI-like mechanism, and no leading-token continuation exception is introduced.

**Bare parameter adjacency.** The bare parameter requires `=>` to be the next parser token after the `identifier` in the same continuing token sequence. An `identifier` followed by a separating logical `NEWLINE` is a completed expression, and the parser does not look across a separating `NEWLINE` to decide that the identifier opened a Closure; the `=>` on the following line cannot begin an expression:

```js
x
=> x + 1
```

is a syntax error. The first line is the completed expression `x`, the logical `NEWLINE` after it is a separator, and `=>` is not a valid expression start.

**Newline after `=>`.** A logical `NEWLINE` immediately after `=>` is continuation under the ordinary incomplete-construct rule, because `=>` requires a closure body. The body may therefore begin on the next logical source line:

```js
x =>
    x + 1
```

is a Closure whose body is `x + 1`. No Closure-specific exception is needed; this is the same incomplete-construct continuation that already applies after `:` and `=`. The rule is independent of indentation and applies to both body forms.

**Member-chain continuation.** Because an expression body is undelimited and is parsed as a maximal `expression`, the ordinary leading-dot continuation rule keeps a member chain that begins on the following logical source line inside the body. `x => x` followed by a newline and then `.foo()` is a Closure whose body is `x.foo()`, not a member access on the Closure object. Attaching a member or other postfix operation to the Closure expression itself requires a braced body or grouping, for example `(x => x).foo()`.

### 16.5 Examples

A Closure stored in an object slot is an ordinary slot whose value is a Closure; it is not a syntactically separate "method declaration". These are valid:

```js
identity: x => x

double: x => x * 2

getter: () => value

sum: (a, b) => a + b

setter: x => this.value = x

makeAdder: x => y => x + y

people
    .filter(person => person.age >= 18)
    .map(person => person.name)
    .each(name => print(name))

greet: other => {
    print("Hello " + other.name)
    print("I'm " + name)
}

items.each(item => print(item))

dog: animal {
    name: "Rex"
    speak: () => print(name)
    greet: other => print(other.name)
}

applyLater(x => x * 2)
```

An anonymous Closure passed as an argument is passed as a Closure object and is not executed merely by being created: `applyLater(x => x * 2)` passes the Closure to `applyLater`, and only an explicit call invokes it.

These are invalid:

```js
=> x

a, b => a + b

x = 1 => x

...xs => xs

x
=> x + 1
```

An object return in an expression-bodied Closure must use the parenthesized form `x => ({ ... })` described above; the braced `x => { ... }` is always a braced Closure body, never an object return.

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
    ".", member-name ;

call-suffix =
    argument-list, [ trailing-closure ] ;

index-suffix =
    "[", expression, "]" ;

member-expression =
    primary-expression,
    { postfix-operation },
    ".", member-name ;
```

The name following a member-access `.` is a `member-name`: an `identifier` or one of the seven reserved-word spellings, which in this structural position denote ordinary slot or message names (see Identifiers).

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
    braced-closure-body ;
```

A trailing closure therefore always uses a braced closure body; the single-expression body form of an ordinary Closure is never available as a trailing closure. This production preserves the call-suffix form:

```ebnf
call-suffix =
    argument-list, [ trailing-closure ] ;
```

The `trailing-closure` may be present only when no logical `NEWLINE` token separates the completed `argument-list` from the trailing closure's braced body: the next parser token after a completed call suffix must be the `{` of the braced body. Horizontal whitespace and comments that produce no `NEWLINE` tokens remain ordinary lexical separation and need no grammar production here.

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

A `parameter-list` exists only where ordinary closure syntax requires it, before `=>`:

```ebnf
closure-expression =
    closure-parameters, "=>", closure-body ;
```

The `=>` explicitly distinguishes closure parameters from a parenthesized expression. Therefore `(x)` is always a parenthesized expression, `(x) => { body }` is always an ordinary closure expression, and there is no third interpretation of `(x)` as the parameter declaration of a trailing closure. No parameter list is inferred from a parenthesized expression, and no semantic/type-based interpretation decides whether parentheses contain closure parameters. Because parameterized trailing closures no longer exist, the grammar requires no parser lookahead and no speculative parsing to distinguish `(x)` from a trailing-closure parameter list: issue B6 is resolved structurally. The single-parameter shorthand introduced in Closures changes nothing here: it is an ordinary explicit closure in ordinary call-argument position (`items.each(item => print(item))`), never a trailing closure, and its one-token `identifier`/`=>` lookahead concerns ordinary closure expressions only.

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

The normative operator/expression hierarchy preserves the existing precedence order:

```ebnf
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
```

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

is a non-overridable runtime identity operation. Its result is defined by the language semantics rather than by physical allocation or host-language reference identity. The Core v0.1 value-identity categories are a closed semantic set: Number
values, String values, the canonical Boolean values, and `null` use value
identity; every other object uses individual object identity. This
classification is not extensible by implementations or libraries and is
independent of allocation, interning, freezing, delegation, or host
representation.

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

Composition is valid only as an object-body item: `composition-item` is defined by the object-body grammar in Object Expressions and is not an alternative of `expression`.

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

Object bodies and executable bodies intentionally use the same expression grammar; object bodies additionally permit contextual composition items as object-body items (see Object Expressions and Composition Syntax).

```js
object: {
    x: 10
    y: 20

    total: () => {
        x + y
    }
}
```

A body may contain ordinary expressions executed during construction; an object body may also contain composition items (see Composition Syntax).

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

A closure requires parameters before `=>` and a body after it:

```js
() => {
    ...
}

x => x * 2
```

The parameter parentheses may be omitted only for exactly one simple parameter, and the body may be a braced sequence or a single expression (see Closures). Therefore:

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

The compact grammar below incorporates the syntax decisions made through revision 78. Semantic validation still applies after parsing. String literal lexical forms are defined normatively in the Literals section and referenced here rather than duplicated.

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

custom-binary-operator =
    symbolic-operator-spelling ;

symbolic-operator-spelling =
    operator-character,
    { operator-character } ;

operator-character =
      "!" | "$" | "%" | "&" | "*" | "+"
    | "-" | "/" | "<" | "=" | ">" | "?"
    | "@" | "\\" | "^" | "|" | "~" ;

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
      ".", member-name
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
    | "args" ;

super-message-send =
    "super", ".", member-name, argument-list ;

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

member-name =
      identifier
    | "this"
    | "context"
    | "args"
    | "super"
    | "true"
    | "false"
    | "null" ;

member-expression =
    primary-expression,
    { postfix-operation },
    ".", member-name ;

closure-expression =
    closure-parameters, "=>", closure-body ;

closure-parameters =
      parameter-list
    | identifier ;

closure-body =
      braced-closure-body
    | [ lookahead != "{" ], expression ;

braced-closure-body =
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
    braced-closure-body ;

literal =
      number-literal
    | string-literal
    | "true"
    | "false"
    | "null" ;

string-literal =
      single-quoted-string
    | double-quoted-string
    | triple-double-quoted-string ;

number-literal =
      decimal-number-literal
    | binary-integer-literal
    | octal-integer-literal
    | hexadecimal-integer-literal ;

decimal-number-literal =
      decimal-integer-literal
    | decimal-float-literal ;

decimal-integer-literal =
    decimal-digits ;

decimal-float-literal =
      decimal-fractional-literal, [ decimal-exponent-part ]
    | decimal-integer-literal, decimal-exponent-part ;

decimal-fractional-literal =
    decimal-digits, ".", decimal-digits ;

decimal-exponent-part =
    exponent-marker, [ exponent-sign ], decimal-digits ;

exponent-marker =
      "e"
    | "E" ;

exponent-sign =
      "+"
    | "-" ;

decimal-digits =
    decimal-digit, { [ "_" ], decimal-digit } ;

decimal-digit =
      "0" | "1" | "2" | "3" | "4" | "5" | "6" | "7" | "8" | "9" ;

binary-integer-literal =
    binary-prefix, binary-digits ;

binary-prefix =
      "0b"
    | "0B" ;

binary-digits =
    binary-digit, { [ "_" ], binary-digit } ;

binary-digit =
      "0"
    | "1" ;

octal-integer-literal =
    octal-prefix, octal-digits ;

octal-prefix =
      "0o"
    | "0O" ;

octal-digits =
    octal-digit, { [ "_" ], octal-digit } ;

octal-digit =
      "0" | "1" | "2" | "3" | "4" | "5" | "6" | "7" ;

hexadecimal-integer-literal =
    hexadecimal-prefix, hexadecimal-digits ;

hexadecimal-prefix =
      "0x"
    | "0X" ;

hexadecimal-digits =
    hexadecimal-digit, { [ "_" ], hexadecimal-digit } ;

hexadecimal-digit =
      "0" | "1" | "2" | "3" | "4" | "5" | "6" | "7" | "8" | "9"
    | "a" | "b" | "c" | "d" | "e" | "f"
    | "A" | "B" | "C" | "D" | "E" | "F" ;
```

A parser may implement the expression portion using recursive descent plus Pratt parsing. Custom symbolic operators form their own precedence domain: mixing them with standard binary operators requires parentheses.

A `custom-binary-operator` is a `symbolic-operator-spelling` that is not itself a reserved or standard symbolic token. Maximal-munch formation of the complete spelling and reserved-spelling classification are governed normatively by the Custom Operator Lexing rules: the complete maximal spelling is classified as a reserved/standard token when it exactly matches a reserved/standard spelling — including the exact one-character spellings `!` and `^` — and as `CUSTOM_OPERATOR` otherwise.

`layout` denotes one or more consecutive logical `NEWLINE` tokens consumed as continuation inside a necessarily-incomplete delimited construct (see Whitespace and Newlines). It is formatting, not an element separator: commas are the only separators between list elements, and trailing commas are not permitted.

As in the normative grammar, `trailing-closure` may follow a completed `argument-list` in `call-suffix` only when no logical `NEWLINE` token intervenes between them; the restriction is stated normatively in Trailing Closures and needs no additional production here.

In `closure-parameters`, a bare `identifier` is distinguished from an ordinary identifier expression by the token immediately following it: an `identifier` whose next parser token is `=>` introduces a `closure-expression`, and an `identifier` whose next token is anything else is an ordinary identifier expression. This is ordinary syntactic lookahead over the token stream, never a semantic/type-based decision. In `closure-body`, the `[ lookahead != "{" ]` guard carried by the single-expression alternative (the notation defined in Scope) expresses formally that a `{` token in the body's first position begins the braced form and any other token begins the single-expression form: because `braced-closure-body` begins with the literal `{`, the two alternatives are disjoint on the body's first token and each spelling has exactly one derivation, so an object-expression body is written parenthesized as `x => ({ ... })`. Nested single-expression bodies associate to the right (`x => y => x + y` is `x => (y => (x + y))`). All such forms are governed normatively by the Closures section.

Indexed assignment is recognized because an `assignment-target` may end in `[ expression ]`; it lowers to `atPut` rather than a slot `Assign`. A `slot-creation-target` may never end in an index suffix, so an indexed `:` has no parse.

Composition is intentionally connected only through `object-body-item`, preserving the contextual meaning of `...`.

The `number-literal` productions above define the valid numeric token forms. Token commitment is governed normatively by the Numeric Literals section: an adjacent continuation that matches no valid numeric form is a lexical error rather than a split into separate tokens, and `1.` and `.5` are not numeric literals.

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

Core Grammar v0.1 defines no dedicated `import` declaration syntax and no `export` declaration syntax or separate export mechanism.

The module-loading operation is exposed through ordinary call/message syntax, for example:

```js
module: import("./module.pt")
```

`import(specifier)` yields the module instance; cross-module access occurs explicitly by obtaining a module instance and accessing its slots through ordinary member lookup. The parser treats the module specifier expression like any other argument expression. Canonical module identity, caching, initialization states, cycle detection, and host-specific resolution are runtime/module-loader semantics rather than grammar rules.

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

The normative lexical grammar for `number-literal` is:

```ebnf
number-literal =
      decimal-number-literal
    | binary-integer-literal
    | octal-integer-literal
    | hexadecimal-integer-literal ;

decimal-number-literal =
      decimal-integer-literal
    | decimal-float-literal ;

decimal-integer-literal =
    decimal-digits ;

decimal-float-literal =
      decimal-fractional-literal, [ decimal-exponent-part ]
    | decimal-integer-literal, decimal-exponent-part ;

decimal-fractional-literal =
    decimal-digits, ".", decimal-digits ;

decimal-exponent-part =
    exponent-marker, [ exponent-sign ], decimal-digits ;

exponent-marker =
      "e"
    | "E" ;

exponent-sign =
      "+"
    | "-" ;

decimal-digits =
    decimal-digit, { [ "_" ], decimal-digit } ;

decimal-digit =
      "0" | "1" | "2" | "3" | "4" | "5" | "6" | "7" | "8" | "9" ;

binary-integer-literal =
    binary-prefix, binary-digits ;

binary-prefix =
      "0b"
    | "0B" ;

binary-digits =
    binary-digit, { [ "_" ], binary-digit } ;

binary-digit =
      "0"
    | "1" ;

octal-integer-literal =
    octal-prefix, octal-digits ;

octal-prefix =
      "0o"
    | "0O" ;

octal-digits =
    octal-digit, { [ "_" ], octal-digit } ;

octal-digit =
      "0" | "1" | "2" | "3" | "4" | "5" | "6" | "7" ;

hexadecimal-integer-literal =
    hexadecimal-prefix, hexadecimal-digits ;

hexadecimal-prefix =
      "0x"
    | "0X" ;

hexadecimal-digits =
    hexadecimal-digit, { [ "_" ], hexadecimal-digit } ;

hexadecimal-digit =
      "0" | "1" | "2" | "3" | "4" | "5" | "6" | "7" | "8" | "9"
    | "a" | "b" | "c" | "d" | "e" | "f"
    | "A" | "B" | "C" | "D" | "E" | "F" ;
```

The productions above define the complete set of valid numeric literal forms. They do not by themselves define lexical commitment: once a source sequence has begun as a numeric literal, an adjacent continuation that makes the numeric form malformed, or that creates an invalid numeric/identifier boundary, is a lexical error rather than a split into otherwise-valid tokens. The Malformed Numeric Literals and Numeric Token Termination subsection below states these rules normatively.

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

Decimal Float literal evaluation is defined independently of the host parser,
host floating-point library, or compiler constant-folder. After removing `_`
digit separators and interpreting the accepted decimal spelling as an exact
mathematical decimal value, the literal denotes the nearest IEEE 754-2019
`binary64` value using `roundTiesToEven`.

If the exact magnitude is too large for finite `binary64`, the literal denotes
positive infinity. If it is nonzero but too small for a normal value, conversion
uses gradual underflow and may produce a subnormal value or positive zero. A
leading negative sign is not part of the literal token; unary `-` is applied
after literal evaluation, so negating positive zero produces negative zero
according to Float semantics.

Two implementations must therefore produce the same Float value for every valid
decimal Float literal even when their host numeric parsers or intermediate
floating-point formats differ.

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

`\u{HEX}` requires 1 to 6 hexadecimal digits and must denote a valid Unicode scalar value. Invalid or incomplete escape sequences are lexical errors. Octal escapes and `\xNN` escapes are not supported.

The same escape rules apply to single-quoted, double-quoted, and triple-double-quoted String literals. Triple-double-quoted strings are multiline String literals, not raw strings:

```js
"""
line one
line two
"""
```

Triple-single-quoted strings are not supported.

String interpolation is not supported in Core v0.1. `${...}` is literal text inside a String and carries no special meaning.

For triple-double-quoted String literals, indentation normalization follows the Core v0.1 rule defined above: the closing delimiter alone establishes the structural indentation prefix, every non-blank content line must begin with exactly that SPACE/TAB prefix and has it removed once, further indentation beyond the prefix is preserved, blank content lines are exempt from prefix matching, blank-line whitespace stripping is conditional on a structural indentation prefix — no structural indentation prefix ⇒ no indentation normalization, so whitespace-only content lines are preserved verbatim — a content line that does not match the prefix is a lexical error, and the leading/trailing newline removal applies only when the delimiter placement matches the rule. Retained logical source newlines preserve their original source code points in the resulting String; no newline normalization is applied. Escape processing still follows the standard Core v0.1 String escape rules, and triple-double-quoted strings remain non-raw strings.

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

The normative lexical grammar for `custom-binary-operator` is:

```ebnf
operator-character =
      "!" | "$" | "%" | "&" | "*" | "+"
    | "-" | "/" | "<" | "=" | ">" | "?"
    | "@" | "\\" | "^" | "|" | "~" ;

symbolic-operator-spelling =
    operator-character, { operator-character } ;

custom-binary-operator =
    symbolic-operator-spelling ;
```

A `symbolic-operator-spelling` is the candidate maximal symbolic token: the longest non-empty sequence of consecutive `operator-character` code points that can begin at a source position. Classification applies to the complete maximal spelling: it is a `custom-binary-operator` only when it does not exactly match a reserved or standard symbolic token — including the exact one-character spellings `!` and `^` — as defined above. Reserved and standard operator spellings are classified according to their dedicated grammar roles rather than as custom operators.

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
