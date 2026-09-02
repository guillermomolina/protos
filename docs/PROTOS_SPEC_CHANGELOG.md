# Protos Language Specification Changelog

All notable changes to the Protos language specification will be documented in this file.

Specification version follows the document revision number: 0.1.X where X is the revision.

## [0.1.64] - 2026-09-02

### Changed
- Closed issue C4: invalid or incomplete String escape sequences are now classified as lexical errors rather than syntax errors.
- Escape validation is part of String-token lexing: the lexer rejects malformed escape syntax before the parser receives a String token, and the parser does not inspect the interior of a successfully formed String token in order to validate escape syntax.
- The existing Core v0.1 valid escape set is unchanged: `\\`, `\'`, `\"`, `\n`, `\r`, `\t`, `\b`, `\f`, and `\u{HEX}`.
- `\u{HEX}` retains its existing requirements: exactly 1 to 6 hexadecimal digits, and the resulting value must denote a valid Unicode scalar value. Values outside the Unicode scalar-value range and surrogate code points are invalid escapes and produce a lexical error.
- No valid escape sequence is added, removed, or reinterpreted; `\xNN`, octal, `\0`, named Unicode, and interpolation escapes remain unsupported.
- Raw-newline behavior is unchanged: a logical source newline before the matching closing quote in single-quoted and double-quoted String literals remains a lexical error.
- Triple-double-quoted String newline and indentation behavior is unchanged, and triple-double-quoted strings remain non-raw strings using the same escape set.
- String interpolation remains absent, and triple-single-quoted strings remain unsupported.
- No runtime semantics changed: malformed source literals are rejected during lexing, before parser/runtime semantics.

### Unresolved
- C3c (`single-quoted-string`, `double-quoted-string`, `triple-double-quoted-string`) remains unresolved and is unchanged by this revision.
- D3 (triple-double-quoted String indentation details: TAB width, visual columns, TAB-to-SPACE equivalence, how common indentation is computed when SPACE and TAB are mixed, and whether mixed SPACE/TAB indentation is legal) remains unresolved and is unchanged by this revision.

## [0.1.63] - 2026-09-02

### Changed
- Closed issue C3b by giving `custom-binary-operator` a complete normative lexical definition; it is no longer an undefined EBNF reference.
- Formalized the existing custom symbolic operator character alphabet unchanged: `! $ % & * + - / < = > ? @ \ ^ | ~`.
- The normative lexical grammar now defines `operator-character`, `symbolic-operator-spelling`, and `custom-binary-operator`; the candidate maximal symbolic token is a non-empty sequence of consecutive `operator-character` code points.
- Maximal-munch and reserved-spelling classification are formalized without semantic changes: the lexer first forms the longest valid symbolic spelling at a source position, then classifies the complete spelling as a reserved/standard token when it exactly matches a reserved/standard symbolic spelling and as `CUSTOM_OPERATOR` otherwise; a longer custom spelling is never split in order to prefer a shorter reserved/standard token.
- Exact standalone `!` remains prefix logical negation (`not()` lowering) and exact standalone `^` remains non-local return; neither is a `custom-binary-operator`.
- Standard operator spellings (`=>`, `=`, `==`, `===`, `!=`, `!==`, `<=`, `>=`, `&&`, `||`, `+`, `-`, `*`, `/`, `%`, `<`, `>`) and the exact `!` and `^` remain excluded from the custom operator category and keep their dedicated grammar roles and precedence.
- Longer non-reserved symbolic spellings, including those containing or beginning with standard-operator characters, remain custom binary operators where already specified (for example `!!`, `^^`, `!^`, `^!`, `@`, `|>`, `<=>`).
- Precedence and associativity are unchanged: all custom binary operators share one precedence domain, associate left-to-right, and mixing them with standard binary operators without explicit grouping remains a syntax error.
- Structural punctuation and special syntax are unchanged: `.`, `:`, `;`, `,`, `(`, `)`, `{`, `}`, `[`, `]`, and the `...` ellipsis token do not participate in custom operator tokens; decimal-dot, ellipsis, closure `=>`, assignment `=`, and slot-creation `:` behavior are unchanged.
- No runtime semantics changed: message-send lowering, operator dispatch, Boolean laziness, non-local return, and precedence behavior are unchanged.

### Unresolved
- The remaining C3 items (`single-quoted-string`, `double-quoted-string`, `triple-double-quoted-string`, and broader normative-grammar self-containment) remain unresolved and are unchanged by this revision.

## [0.1.62] - 2026-09-02

### Changed
- Closed issue C3a by giving `number-literal` a complete normative lexical grammar; `number-literal` is no longer an undefined EBNF reference.
- The new grammar makes the numeric token families explicit: `decimal-number-literal`, `binary-integer-literal`, `octal-integer-literal`, and `hexadecimal-integer-literal`, with helper productions for decimal digits, digit sequences, fractional parts, exponent parts, radix prefixes, and radix digits.
- Existing numeric syntax was formalized without semantic changes: decimal integers (including leading zeroes), decimal fractional literals, decimal exponent forms, binary/octal/hexadecimal integer literals, and `_` digit separators as previously specified.
- The previously accepted radix prefix case behavior is preserved exactly: `0x`/`0X`, `0b`/`0B`, and `0o`/`0O`.
- Decimal-dot tokenization is unchanged: a `.` belongs to a decimal numeric literal only when immediately followed by a decimal digit, so `1.` remains an integer literal followed by a structural dot and `.5` remains a structural dot followed by an integer literal.
- Malformed numeric continuations remain lexical errors rather than token splitting: `0x`, `0xG`, `0b2`, `0o8`, `2e`, `2e+`, `1__2`, `1_`, `0x_FF`, and `123abc` remain lexical errors.
- Unsupported radix floating-point attempts remain lexical errors: `0b10.5`, `0o17.2`, and `0xFF.1` are not split into radix integer, dot, and integer tokens.
- No runtime semantics changed: numeric value families, literal value production, equality, identity, and arithmetic behavior are unchanged, and no runtime lexing machinery is added.

### Unresolved
- The remaining C3 items (`custom-binary-operator`, `single-quoted-string`, `double-quoted-string`, `triple-double-quoted-string`, and broader normative-grammar self-containment) remain unresolved and are unchanged by this revision.

## [0.1.61] - 2026-09-01

### Changed
- Closed issue C2 by unifying the conflicting `object-body` definitions: the normative grammar now defines `object-body = "{", object-body-sequence, "}"`, and the stale `object-body = "{", expression-sequence, "}"` definition is removed.
- Object bodies use `object-body-sequence`, with `object-body-line-items`, `object-body-line`, `object-body-item`, and `composition-item` productions that mirror the ordinary `expression-sequence` separator structure.
- An object-body item is either an ordinary `expression` or a contextual `composition-item` of the form `...expression`; composition items and ordinary expressions share the same logical-`NEWLINE`/inline-`;` separator rules, and there is no implicit adjacency separator.
- `...expression` remains contextual to object bodies and is not a general expression: it is not added to `expression`, `primary-expression`, or `closure-body`. Closure bodies therefore continue to contain only ordinary expressions, and `() => { ...base }` does not become valid merely because closure bodies use braces.
- No runtime composition semantics changed: conflict handling, binding copying, evaluation order, error behavior, and the existing composition representation are unchanged.

### Unresolved
- Issue D3 remains unresolved and is unchanged by this revision.

## [0.1.60] - 2026-09-01

### Changed
- Resolved newline placement for trailing closures (issue B7): a trailing closure must have no intervening logical `NEWLINE` token after the completed call. `foo() { ... }` attaches the braces as a parameterless trailing closure appended as the final call argument, while `foo()` followed by `{ ... }` on a later source line does not attach them.
- A completed call is syntactically complete, so a following logical `NEWLINE` acts as the ordinary expression separator under the B2 complete-expression newline rule; repeated separating `NEWLINE` tokens (blank lines) under B4 likewise do not permit trailing-closure attachment, and a `;` between the call and the braces does not attach them.
- `{` is not added as a complete-expression newline continuation exception: the only complete-before-newline continuation exception remains the existing leading structural/member `.` rule from revision 55 (B2).
- Horizontal whitespace between the completed call and the closure body remains ordinary lexical separation and is permitted.
- Block comments may appear between the call and the trailing closure, including block comments containing source newlines, because newlines inside a block comment produce no `NEWLINE` tokens (A6). A line comment prevents same-sequence attachment because its terminating newline remains tokenized as a separating `NEWLINE`.
- Indentation plays no role in trailing-closure attachment: the decision concerns logical `NEWLINE` tokens, not physical source formatting.
- The trailing-closure production `trailing-closure = closure-body ;` and the call-suffix form `argument-list, [ trailing-closure ]` are preserved; the no-intervening-`NEWLINE` restriction is stated normatively rather than through new grammar machinery, and no special `same-line` lexical token is introduced.
- B2, B4, A4, and A6 semantics are unchanged, and revision 59 semantics are unchanged: trailing closures remain parameterless and B6 remains closed; `foo() (x) { body }` is still not trailing-closure syntax, and `foo((x) => { body })` remains an ordinary call.
- No runtime mechanism is added: only syntactically attached trailing closures reach trailing-closure lowering.

### Unresolved
- Issue D3 remains unresolved and is unchanged by this revision.

## [0.1.59] - 2026-09-01

### Changed
- Simplified trailing-closure syntax (resolves issue B6): a trailing closure is now always parameterless. `foo(args...) { body }` remains supported and still appends `() => { body }` as the final call argument.
- The parameterized trailing-closure form `foo(args...) (params...) { body }` is removed from Core v0.1: it is no longer recognized as trailing-closure syntax.
- A closure that requires parameters is written as an ordinary explicit closure expression in ordinary call-argument position, for example `items.each((item) => { print(item) })` and `collection.reduce(initial, (acc, item) => { ... })`.
- A trailing closure never has a parameter list. `(x)` remains an ordinary parenthesized expression and `(x) => { body }` remains an ordinary closure expression; there is no third interpretation of `(x)` as trailing-closure parameters.
- B6 is therefore resolved structurally: parentheses are no longer reused as a trailing-closure parameter list, so no parser lookahead, no speculative parsing, and no semantic/type-based disambiguation rule is required for trailing-closure parameters.
- This revision supersedes only the parameterized-trailing-closure portion of revision 54; the parameterless trailing-closure portion of revision 54 is unchanged.

### Unresolved
- Newline placement between a completed call and a trailing closure remains unresolved (issue B7).
- Issue D3 remains unresolved and is unchanged by this revision.

## [0.1.58] - 2026-09-01

### Changed
- Resolved indexed access and assignment versus slot creation (issue B5): slot/member access and indexed access are distinct mechanisms. `object.name` performs ordinary slot lookup, while `object[key]` lowers to the `at(key)` message and is not dynamic slot access.
- `object["foo"]` is not defined to be equivalent to `object.foo`; the two expressions may return completely different values, and an object does not automatically become indexable merely because it has slots.
- Indexable objects remain ordinary objects and may have ordinary slots, methods, delegation, and openness/frozen state; indexed contents and object slots are independent, so `map.description: "users"` and `map["description"] = user` may coexist and refer to entirely different things.
- `:` is specifically the slot-creation operator and can no longer target an index: `object[index]: value`, `object["foo"]: value`, and `object.foo[index]: value` are syntax errors. There is no indexed slot creation and no `atCreate`-style protocol.
- Indexed assignment `object[index] = value` remains valid and lowers to `atPut(index, value)`; the `=` in indexed assignment does not require an already-existing indexed entry, and whether `atPut` creates, replaces, extends, or rejects a missing key/index is defined by the receiver's `atPut` protocol.
- The grammar now distinguishes the legal final targets of `:` and `=`: `slot-creation-target` is a bare identifier or a member target, while `assignment-target` may additionally end in an index suffix. Chained postfix forms whose final operation is a member (for example `object[index].member: value`) remain valid slot-creation targets, and indexed assignment retains its existing evaluation order and written-value result.

### Unresolved
- Issues B6 and D3 remain unresolved and are unchanged by this revision.
- Newline placement between a completed call and a trailing closure, and before a trailing closure's own parameter list, remains unresolved (issue B7).

## [0.1.57] - 2026-09-01

### Changed
- Resolved expression-separation multiplicity and blank-line grammar (issue B4): Core v0.1 has two distinct expression-separation mechanisms — `;` is the inline expression separator between expressions on the same logical source line, and a logical `NEWLINE` is the ordinary separator between expressions on different logical source lines. They are distinct syntactic roles, not interchangeable spellings of one generic separator.
- `;` is a separator, not a terminator: it requires an expression before it and an expression after it on the same logical source line. Leading, trailing, and consecutive semicolons are syntax errors, and a `;` cannot separate an expression from an expression on a following source line; `;\n` is not a redundant separator pair.
- Repeated separating logical `NEWLINE` tokens have the same effect as one separating `NEWLINE`: blank lines are permitted between, before, and after expressions and create no empty expressions, no semantic AST nodes, and no runtime behavior.
- The grammar's `layout` production now permits one or more consecutive logical `NEWLINE` tokens (`newline, { newline }`): blank lines inside open delimited constructs such as argument lists and parameter lists are layout formatting with the same effect as a single layout newline, and never substitute for a required comma.
- The generic `separator = ";" | newline` expression-sequence model is replaced by productions that distinguish same-line `;`-separated expressions from cross-line newline separation; no production permits an optional trailing `;`, and no production creates empty expressions.
- Neither `;` nor a separating, continuation, or layout `NEWLINE` becomes a semantic AST node; expression separation still produces the existing `Sequence(expressions)` representation with strictly left-to-right evaluation.
- The revision 55 newline-continuation rules (B2) and the revision 56 comma-separated list rules (B3) are unchanged: trailing commas remain syntax errors, and newlines remain non-separators inside comma-separated lists.

### Unresolved
- Newline placement between a completed call and a trailing closure, and before a trailing closure's own parameter list, remains unresolved (issue B7).

## [0.1.56] - 2026-09-01

### Changed
- Resolved comma-separated list separators (issue B3): `,` is the only separator between elements of Core v0.1 comma-separated lists, including call arguments and closure parameters.
- A comma is strictly a separator between two list elements; it is not a terminator and does not represent an empty or omitted element. A comma must have a list element on both sides within the same list, so a trailing comma before the closing delimiter is a syntax error.
- A logical `NEWLINE` is no longer an argument or parameter separator. Newlines inside the delimiters of a list are continuation/layout under the revision 55 (B2) rules: formatting within a necessarily-incomplete construct, never a substitute for a required comma.
- Multiline calls and parameter lists remain valid through B2 continuation/layout: elements are separated by commas on their lines, and the closing delimiter may appear on a following line without a trailing comma.
- Removed the `argument-separator = "," | newline` grammar production; argument and parameter items are separated by `,` only, with an optional `layout` continuation-newline helper production for formatting inside the delimiters.
- Indexing is unchanged: Core v0.1 indexing contains one expression, not a comma-separated list, and this revision introduces no multi-index syntax.
- Trailing-closure semantics from revision 54 are unchanged: call arguments and trailing-closure parameters are distinct lists that each follow the comma-only separator rule.
- Commas and continuation newlines are resolved entirely during parsing and introduce no runtime behavior.

### Unresolved
- Separator multiplicity and blank-line grammar remain unresolved (issue B4).
- Newline placement between a completed call and a trailing closure, and before a trailing closure's own parameter list, remains unresolved (issue B7).

## [0.1.55] - 2026-09-01

### Changed
- Resolved newline continuation (issue B2): a logical `NEWLINE` token normally separates expressions when the expression before it may legally end at that point, and does not separate expressions while the syntactic construct before it is necessarily incomplete and requires further input.
- Added the explicit leading-dot postfix continuation: a logical newline immediately before a leading structural `.` continues the preceding postfix/member expression. This is the sole accepted complete-before-newline continuation exception in B2.
- No general leading-operator continuation: a binary or custom symbolic operator at the beginning of the following line does not continue a preceding complete expression.
- Newline continuation is a syntactic/parser rule based on grammatical incompleteness, not on a hard-coded list of token spellings; it is independent of indentation, visual alignment, tab width, and source line-ending spelling.
- No Automatic Semicolon Insertion: the parser decides whether an existing logical `NEWLINE` token separates expressions or is consumed as continuation. An explicit `;` remains an expression separator.
- Newline continuation is resolved entirely during parsing and has no runtime semantic effect; only expressions actually separated by parser-level expression separators become distinct sequence elements.

### Unresolved
- Separators before closing `)` / `]`, trailing commas, and related list-end questions remain unresolved (issue B3).
- Separator multiplicity and blank-line grammar remain unresolved (issue B4).
- Newline placement between a completed call and a trailing closure, and before a trailing closure's own parameter list, remains unresolved (issue B7).

## [0.1.54] - 2026-09-01

### Changed
- Unambiguous trailing-closure call syntax (resolves issue B1): the parentheses of a call always contain call arguments; they are never contextually reinterpreted as the parameter list of a following trailing closure.
- A parameterized trailing closure has its own parameter list placed after the completed call: `foo(args...) (params...) { body }` appends `(params...) => { body }` as the final call argument.
- `foo(args...) { body }` appends a parameterless `() => { body }` closure as the final call argument.
- Fixed the contradictory `items.each(item) { print(item) }` example/desugaring: the form now means one explicit `item` call argument plus a parameterless trailing closure, i.e. `items.each(item, () => { print(item) })`. It is not `items.each((item) => { print(item) })`.
- The parameterized form is written `items.each() (item) { print(item) }`, which desugars to `items.each((item) => { print(item) })`.
- Trailing closures remain ordinary Closure arguments after desugaring: no new runtime value kind and no special runtime trailing-block construct are introduced.
- The object-construction distinction is unchanged: `foo { ... }` creates an object whose parent expression is `foo`; `foo() { ... }` invokes `foo` with a parameterless trailing closure.

### Unresolved
- Newline placement between a completed call and a trailing closure, and before a trailing closure's own parameter list, remains unresolved (issue B7).

## [0.1.53] - 2026-09-01

### Added
- A `/* ... */` block comment is one lexical construct that consumes all source characters from its opening `/*` through its matching closing `*/`; Core v0.1 block comments do not nest and the first `*/` terminates the comment (resolves audit item A6).
- Logical source newlines inside a block comment are consumed as part of the comment: embedded `LF`, `CR`, and `CRLF` emit no `NEWLINE` token. An embedded `CRLF` remains one logical source newline for source-position and logical-line accounting.
- Single-line and multiline block comments have the same token-separation effect: logical newlines inside `/* ... */` cannot themselves separate expressions.
- Newlines outside a block comment remain governed by the revision 51 logical-newline rules.
- `//` line comments are unchanged: they terminate immediately before their terminating logical source newline, which remains available for ordinary `NEWLINE` tokenization.
- Comments remain lexical constructs with whitespace-like token-separation behavior; they do not add code points to the horizontal-whitespace set (revision 52: exactly `SPACE` and `TAB`).

### Unresolved
- Separator multiplicity and blank-line grammar remain unresolved (issue B4).

## [0.1.52] - 2026-09-01

### Added
- Core v0.1 horizontal whitespace is exactly `SPACE` (U+0020) and `CHARACTER TABULATION` (U+0009, TAB); no other code point is horizontal whitespace, and the set does not depend on Unicode whitespace properties or host whitespace classification.
- Outside lexical constructs that consume their own contents, SPACE and TAB are insignificant horizontal whitespace: they separate tokens where separation is required and otherwise emit no parser token.
- Logical source newlines remain a separate lexical category under the revision 51 `LF` / `CR` / `CRLF` rules; they are not horizontal whitespace.
- Other Unicode whitespace-like code points are not implicitly accepted as whitespace: in particular U+000B VERTICAL TAB, U+000C FORM FEED, U+0085 NEXT LINE, U+00A0 NO-BREAK SPACE, U+1680 OGHAM SPACE MARK, U+2000..U+200A Unicode space characters, U+2028 LINE SEPARATOR, U+2029 PARAGRAPH SEPARATOR, U+202F NARROW NO-BREAK SPACE, U+205F MEDIUM MATHEMATICAL SPACE, U+3000 IDEOGRAPHIC SPACE, and U+FEFF ZERO WIDTH NO-BREAK SPACE are not Core v0.1 whitespace (illustrative list, not an open-ended definition).
- A source code point that is neither part of a valid lexical token, nor SPACE or TAB horizontal whitespace, nor a logical source newline, nor consumed inside a lexical construct such as a String or comment is a lexical error; the lexer must not silently discard unknown Unicode whitespace-like or format characters.
- Multiline triple-double-quoted String indentation whitespace consists only of SPACE (U+0020) and TAB (U+0009). Excluded whitespace-like characters remain valid String content where the ordinary String literal rules permit them.
- U+FEFF is not defined as lexical whitespace; source-byte decoding and source-encoding signature behavior remain outside this revision.
- Comments continue to consume their contents according to the existing comment lexical rules.

### Unresolved
- Tab width, visual columns, whether a TAB is equivalent to some number of SPACE characters, and how common indentation is computed when SPACE and TAB are mixed remain open (part of the separate multiline-String indentation question).
- Newline behavior inside `/* ... */` block comments remains unresolved (audit item A6).
- Separator multiplicity and blank-line grammar remain unresolved (issue B4).

## [0.1.51] - 2026-09-01

### Added
- Logical source-newline definition: a logical source newline is exactly one of `LF` (U+000A), `CR` (U+000D), or `CRLF` (U+000D U+000A); `CRLF` is consumed atomically as one logical newline, never two.
- Each logical source newline that is not consumed by another lexical construct produces exactly one `NEWLINE` token for the parser; the parser-level `newline` used in grammar productions denotes this logical `NEWLINE` token.
- Mixed line-ending styles (`LF`, `CR`, and `CRLF`) within one source file are permitted and are not lexical errors.
- `//` line comments terminate immediately before the next logical source newline or at end of file; the terminating logical source newline is not consumed by the comment and remains available for ordinary newline tokenization.
- Single-quoted and double-quoted String literals reject any logical source newline (`LF`, `CR`, or `CRLF`) before the matching closing quote as a lexical error.
- Triple-double-quoted String literals count each logical source newline as one logical newline for structural processing (delimiter placement, content-line splitting, indentation normalization), while retained source newlines preserve their original source code points in the resulting String: `LF` remains U+000A, `CR` remains U+000D, and `CRLF` remains U+000D U+000A; there is no implicit newline normalization of String content.
- Opening/trailing newline removal in triple-double-quoted String literals removes the complete logical newline sequence, including both code points of a removable `CRLF`.
- Newline handling is independent of the host operating system, editor settings, Git line-ending conversion, and host line-separator conventions.

### Unresolved
- Newline behavior inside `/* ... */` block comments remains a separate open question (audit item A6); this revision decides nothing about it.
- Separator multiplicity and blank-line grammar (consecutive or mixed separators) remain a separate open question (issue B4); this revision does not change separator multiplicity.

## [0.1.50] - 2026-09-01

### Added
- Standalone `!` and `^` are standard/reserved symbolic tokens; their existing prefix and non-local-return meanings are unchanged.
- The exact one-character spellings `!` and `^` are not custom binary operators: `a ! b` and `a ^ b` are syntax errors.
- `!` and `^` remain characters in the custom operator alphabet and may participate in longer custom spellings such as `!!`, `^^`, `!^`, and `^!`.
- Symbolic token classification follows maximal munch (longest spelling first) and is independent of parser position; there is no prefix-position exception, so `!!x` tokenizes as `CUSTOM_OPERATOR("!!")` `IDENTIFIER("x")` and is a syntax error.

### Changed
- Reconciled the stale statement that the custom symbolic operator alphabet "remains to be finalized separately" with the already-fixed Core v0.1 alphabet; the alphabet is fixed by the language grammar.

## [0.1.49] - 2026-09-01

### Added
- Radix-Integer-dot boundary: a `.` immediately following a complete radix-prefixed Integer literal is a structural `.` token when not immediately followed by a decimal digit; `0b10.foo` tokenizes as `INTEGER("0b10")` `.` `IDENTIFIER("foo")`. When the `.` is immediately followed by a decimal digit, the source sequence is an attempted unsupported radix Float literal and is a lexical error: `0b10.5`, `0o17.25`, and `0x1.8` are lexical errors rather than being split into `INTEGER` `.` `INTEGER` tokens. The decimal-point vs. member-access lexing rules for decimal literals are unchanged.

## [0.1.48] - 2026-09-01

### Added
- Numeric token termination and malformed numeric boundaries: once a source sequence has begun as a numeric literal, a malformed continuation or an invalid numeric/identifier boundary is a lexical error; the lexer must not split the malformed sequence into otherwise valid tokens in order to recover it.
- Radix prefixes (`0x`, `0X`, `0b`, `0B`, `0o`, `0O`) must be followed by a valid digit for that radix: `0x`, `0xG`, `0b2`, and `0o8` are lexical errors, without fallback to `INTEGER("0")` plus another token.
- An exponent begun by `e` or `E` must be complete: `2e`, `2e+`, and `2e-` are lexical errors.
- Invalid underscore placement inside or immediately adjacent to a numeric literal is a lexical error: `1__2`, `1_`, and `0x_FF`.
- An identifier cannot begin immediately after a numeric literal without a lexical boundary: `123abc` is a lexical error, not `INTEGER("123")` followed by `IDENTIFIER("abc")`.
- Valid token boundaries (punctuation, whitespace, structural delimiters, operators) remain unaffected.

## [0.1.47] - 2026-09-01

### Changed
- Clarified decimal-point vs. member-access dot lexing: a `.` belongs to a decimal numeric literal only when it is immediately followed by a decimal digit. `1.0` is a `Float` literal; `1.` tokenizes as `INTEGER("1")` followed by `.` and `.5` as `.` followed by `INTEGER("5")`; `1.to(10)` tokenizes as `INTEGER("1")` `.` `IDENTIFIER("to")` `(` `INTEGER("10")` `)`. `1.` and `.5` are not numeric literals as complete source sequences; this does not make either sequence necessarily a lexical error — whether the resulting token sequence is syntactically valid is the parser's responsibility.

## [0.1.46] - 2026-08-31

### Added
- Core v0.1 ellipsis token (`...`) definition: single lexical token, greedy recognition, context-dependent meaning.
- Maximal-munch tokenization rule for symbolic operators.
- String escape validation as part of lexical analysis: invalid, incomplete, or unsupported escapes are lexical errors.
- Validation of `\u{HEX}` format and Unicode scalar value constraints.
- Clarification that no new operator semantics are introduced in this revision.

## [0.1.45] - 2026-08-31

### Added
- Core v0.1 newline handling rules for String literals.
- Single-quoted and double-quoted String literals are single-line; raw newlines are lexical errors in these forms.
- Newlines may be represented using `\n` and `\r` escape sequences in single-line literals.
- Triple-double-quoted String literals support raw source newlines as part of multiline content.
- Lexical rules for enforcing single-line constraints on non-multiline String forms.

## [0.1.44] - 2026-08-31

### Added
- Complete Core v0.1 reserved-word set definition: `this`, `context`, `args`, `super`, `true`, `false`, `null`.
- Reserved-word recognition rules: case-sensitive matching after lexical identifier recognition.
- Clarification that prelude names (`Object`, `Future`, `Number`, `String`, `Map`, `IdentityMap`, etc.) are not reserved words.
- Clarification that Core v0.1 does not reserve control-flow or declaration keywords such as `if`, `else`, `while`, `for`, `class`, `function`, `try`, `catch`, `throw`, `async`, or `await`.

## [0.1.43] - 2026-08-31

### Added
- Core v0.1 identifier syntax definition: Unicode-aware, case-sensitive, begin with `_` or `XID_Start`, continue with `XID_Continue`.
- Core v0.1 identifier normalization requirement: all identifiers must be in Unicode NFC form, implementations must reject non-NFC identifiers.
- Clarification that identifier normalization applies to spelling only, not to `String` values.
- Reserved word recognition rules after lexical identifier recognition.

## [0.1.42] - 2026-08-31

### Added
- Core v0.1 numeric literal syntax definition: decimal and radix literals (`0x`, `0b`, `0o`), digit separators, decimal point and exponent handling.
- Core v0.1 prefix operator semantics: prefix `-` lowers to `negated()`, prefix `!` lowers to `not()`, prefix `+` is unsupported, operators apply to arbitrary expressions.

### Changed
- Clarified numeric literal constraints: no leading sign as part of literal, no type suffixes, no `NaN`/`Infinity` literal syntax, unsupported hex/binary/octal Float forms.
- Clarified prefix operator lowering as protocol-based message dispatch rather than privileged intrinsics.

## [0.1.41] - 2026-08-31

### Added
- Core v0.1 string literal syntax: single-quoted, double-quoted, and triple-double-quoted forms with unified escape rules.
- Core v0.1 escape sequences: `\\`, `\'`, `\"`, `\n`, `\r`, `\t`, `\b`, `\f`, and `\u{HEX}`.
- Core v0.1 multiline string indentation normalization rule.
- Core v0.1 comment syntax: `//` line comments, `/* ... */` block comments, no nesting, no `#` syntax, no documentation-comment feature.

### Changed
- Specified constraint: no octal escapes, no `\xNN` escapes, no string interpolation.
- Clarified that Triple-single-quoted strings are not supported.
