# Protos Language Specification Changelog

All notable changes to the Protos language specification will be documented in this file.

Specification version follows the document revision number: 0.1.X where X is the revision.

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
