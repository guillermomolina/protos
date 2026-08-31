# Changelog

All notable changes to this project will be documented in this file.

## [0.1.1-SNAPSHOT] - 2026-08-31

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
- Project licensing metadata by adding the license text in [LICENSE](LICENSE) and referencing it from the README.

### Changed
- Added JUnit 5 to support lexer-focused test coverage.
- Updated project version from `0.1.0-SNAPSHOT` to `0.1.1-SNAPSHOT` as a conservative patch bump for the completed milestone.
- Revised the canonical Protos specification documents to define the Core v0.1 string literal rules: single- and double-quoted literals are equivalent, there is no character literal/type, triple-double-quoted strings are multiline literals, interpolation is unsupported, and multiline indentation normalization remains intentionally unspecified pending a later design decision.
- Documented the chosen license in the project README for this version.

### Notes
- This change is intentionally limited to lexical analysis only.
- No parser or language semantics were implemented in this session.
