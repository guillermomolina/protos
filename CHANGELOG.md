# Changelog

All notable changes to the Protos implementation project will be documented in this file.

For specification changes, see [spec/PROTOS_SPEC_CHANGELOG.md](spec/PROTOS_SPEC_CHANGELOG.md).

## [0.2.0-SNAPSHOT] - 2026-09-01

### Added

- Added canonical String literal execution as ordinary immutable Protos String values.

- Added initial Canonical-to-Truffle lowering for canonical `true`, `false`, and `null` singleton literals and non-empty sequences.

- Added a Truffle CallTarget entry point for executing Protos expression trees through the root-node boundary.
- Added the initial Truffle root execution boundary, delegating directly to the executable expression tree without introducing language-value semantics.

- Added the first executable Truffle node: non-empty expression sequences execute strictly left-to-right and return the final child result.
- Added the initial Truffle execution-node boundary with source-span preservation and the Truffle API dependency.
- Added canonical call-spread lowering with a contextual `CanonicalSpread(expression)` marker for ordinary, member, and super invocation arguments.
- Added dedicated canonical indexed-assignment lowering that preserves receiver/index/value evaluation structure and the distinct `atPut` assignment-result semantics.
- Added dedicated canonical lowering for non-spread `super.message(arguments...)` operations, preserving super lookup semantics separately from ordinary message sends.
- Added dedicated canonical intrinsic nodes for the reserved execution-context expressions `this`, `context`, and `args`, keeping them distinct from ordinary lexical lookup.
- Added canonical lowering for non-spread calls, preserving the semantic distinction between ordinary `Call(receiver, arguments)` and member message `Send(receiver, message, arguments)` forms.
- Added canonical object lowering with optional explicit parent, canonical object-body sequencing, and `Compose(object)` nodes for contextual composition items.
- Added canonical `Return(value)` lowering for the `^ expression` non-local return form.
- Added canonical `Create`/`Assign` lowering for bare and explicit-member slot writes while leaving indexed assignment for its distinct `atPut` semantics.
- Added canonical lowering for indexed reads as ordinary one-argument `at` message sends.
- Added canonical lowering for lazy `&&` and `||` as `and`/`or` message sends whose right-hand side is wrapped in a parameterless canonical Closure.
- Added canonical Closure and parameter lowering, including mandatory normalization of expression-bodied Closures to a one-expression canonical Sequence.
- Added canonical lowering for semantic equality/inequality and non-overridable identity/non-identity, with a dedicated canonical identity node.
- Added canonical lowering for the standard comparison operators `<`, `<=`, `>`, and `>=` as ordinary one-argument message sends.
- Added canonical lowering for the standard arithmetic operators `+`, `-`, `*`, `/`, and `%` as ordinary one-argument message sends.
- Added canonical lowering for custom symbolic binary operators as ordinary one-argument message sends while leaving standard binary operators for dedicated semantic lowering.
- Added canonical lowering for Core prefix `-` and `!` as ordinary zero-argument `negated` and `not` sends.
- Added the canonical semantic AST foundation and the first Surface AST canonicalization slice for literals, name lookup, grouping, member reads, and sequences.
- Established `.protos` as the project source-file extension and added initial non-normative tutorial, task-oriented example, and portable benchmark corpora.
- Added benchmark workloads for recursion, slots, closure and method calls, object creation, delegation depth, and monomorphic/polymorphic dispatch.
- Added parser support for same-line parameterless trailing closures as the final argument of ordinary call suffixes.
- Added closure surface AST integration and deterministic parsing for closure parameters, defaults, rest parameters, and braced or expression bodies.
- Added parser support for object expressions, parent expressions, and contextual object composition items.
- Added parser support for structural super message sends without making `super` a first-class expression.
- Added parsing for slot creation and assignment with grammar-defined target restrictions.
- Added deterministic parsing for the grammar-defined non-local return expression.
- Added parser support for the separate custom binary-operator precedence domain and its required standard/custom mixing errors.
- Added deterministic parsing for the standard unary and binary operator precedence ladder.
- Added parser support for same-line semicolon expression separators with the grammar-defined error cases.
- Extended the parser foundation with parenthesized expressions, member access, calls, indexing, argument spread, and leading-dot continuation.
- Added the first deterministic parser foundation with source-aware errors and a portable surface AST.
- Added source-aware lexer token occurrences with portable half-open source spans as parser infrastructure.
- Expanded lexer conformance coverage for raw Unicode scalar handling across every Core String form, including rejection of unpaired surrogates.
- Expanded lexer conformance coverage for numeric termination at structural delimiters, logical newlines, and standard and custom operators.
- Expanded lexer conformance coverage for uppercase radix-prefix commitment and case-insensitive exponent completion errors.
- Expanded lexer conformance coverage for malformed numeric/identifier adjacency across ASCII, reserved-word, underscore, and Unicode identifier continuations.
- Expanded lexer conformance coverage for single-line String raw-newline rejection, raw-source indentation matching, and interpolation-looking escape rejection.
- Expanded lexer conformance coverage for triple-double String CR/CRLF delimiter-newline handling and shared escape semantics.
- Expanded lexer conformance coverage for Unicode escape digit-count boundaries, hexadecimal case handling, and the closed String escape set.
- Expanded lexer conformance coverage for exact, case-sensitive reserved-word recognition and the closed Core v0.1 reserved-word set.
- Expanded lexer conformance coverage for triple-double String structural indentation, including absent and empty prefixes, blank-line exemption, and exact SPACE/TAB matching.
- Expanded lexer conformance coverage for the symbolic-operator alphabet, maximal-munch classification, and comment/operator lexical precedence.
- Expanded lexer conformance coverage for numeric separator placement, unsupported suffixes and radix floats, special-value identifiers, and valid token boundaries.
- Expanded lexer conformance coverage for line-comment termination, non-nesting block comments, first-delimiter closure, and comment delimiters inside Strings.
- Expanded lexer conformance coverage for String normalization independence, raw Unicode scalar content, quote-run boundaries, and the absence of triple-single String syntax.
- Expanded lexer conformance coverage for Core v0.1 String quote-run boundaries, unterminated String forms, and the closed whitespace rules.
- Added bundled Unicode 17.0.0 character-property and normalization data used by the lexer independently of the host JDK Unicode version.
- Added conformance tests against the official Unicode 17.0.0 `XID_Start`, `XID_Continue`, and normalization test data.

### Changed

- Decoupled canonical literal kinds from parser surface literal kinds with an explicit Surface-to-Canonical mapping.
- Corrected malformed parser imports introduced by slot-creation and assignment support.
- Restored the canonical NEWLINE token value after adding source-aware token occurrences.
- Fixed source-aware newline token occurrence emission so canonical NEWLINE lexemes remain valid Java Strings.
- Updated GitHub Actions CI to current supported `actions/checkout` and `actions/setup-java` major versions.
- Relicensed Protos from the Server Side Public License (SSPL) v1 to the OSI-approved Adaptive Public License 1.0 (APL-1.0). The complete license, including the completed Exhibit A, is in [LICENSE.TXT](LICENSE.TXT).
- Project implementation version changed from `0.1.6-SNAPSHOT` to `0.2.0-SNAPSHOT`.

### Notes

- Protos Core language version remains 0.1.
- No language semantics changed.

### Fixed

- Fixed parser newline continuation while a member suffix or structural `super` message send is necessarily incomplete.
- Fixed parser newline continuation after `...` in spread arguments and rest parameters, matching the grammar's necessarily-incomplete construct rule.
## [0.1.6-SNAPSHOT] - 2026-08-31

### Added

- Expanded lexer support to cover the lexical rules defined by the current Core v0.1 specification.
- Added lexer support for comments, Unicode-aware identifiers, reserved words, numeric literal forms, multiline strings, ellipsis, and custom symbolic operators.
- Added lexical validation for malformed string escape sequences and invalid Unicode scalar values.
- Added regression coverage for supplementary Unicode escape sequences.

### Changed

- Updated lexer tests to match the current specification for reserved words, period tokenization, and numeric literals adjacent to periods.
- Unicode escape decoding now preserves supplementary Unicode code points instead of truncating them to Java `char` values.

### Notes

- The lexer implementation is still under specification-compliance review.
- Unicode identifier handling requires further review for exact `XID_Start` and `XID_Continue` compliance.
- Specification changes are documented separately in [spec/PROTOS_SPEC_CHANGELOG.md](spec/PROTOS_SPEC_CHANGELOG.md).

## [0.1.5-SNAPSHOT] - 2026-08-31

### Notes
- No implementation changes in this session.
- Specification changes documented in [spec/PROTOS_SPEC_CHANGELOG.md](spec/PROTOS_SPEC_CHANGELOG.md).

## [0.1.4-SNAPSHOT] - 2026-08-31

### Added
- Initial minimal Protos lexer implementation under `com.guillermomolina.protos.lexer`.
- Token model and token type definitions to represent the current lexical grammar.
- Basic lexical support for:
  - identifiers and reserved intrinsic keywords
  - numeric literals, including radix literals (`0x`, `0b`, `0o`)
  - string literals with escape handling
  - punctuation and structural tokens
  - operators and custom symbolic operators
  - newline-delimited token separation
- Focused lexer regression tests covering the implemented lexical behavior.
- Project licensing metadata by adding the license text in the then-current `LICENSE` file and referencing it from the README.

### Changed
- Added JUnit 5 to support lexer-focused test coverage.
- Updated project version from `0.1.3-SNAPSHOT` to `0.1.4-SNAPSHOT` as a conservative patch bump for the completed milestone.
- Documented the chosen license in the project README for this version.

### Notes
- Specification changes documented in [spec/PROTOS_SPEC_CHANGELOG.md](spec/PROTOS_SPEC_CHANGELOG.md).
