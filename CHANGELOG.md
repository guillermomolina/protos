# Changelog

All notable changes to the Protos implementation project will be documented in this file.

For specification changes, see [spec/PROTOS_SPEC_CHANGELOG.md](spec/PROTOS_SPEC_CHANGELOG.md).

## [0.2.0-SNAPSHOT] - 2026-09-01

### Added

- Added bundled Unicode 17.0.0 character-property and normalization data used by the lexer independently of the host JDK Unicode version.
- Added conformance tests against the official Unicode 17.0.0 `XID_Start`, `XID_Continue`, and normalization test data.

### Changed

- Relicensed Protos from the Server Side Public License (SSPL) v1 to the OSI-approved Adaptive Public License 1.0 (APL-1.0). The complete license, including the completed Exhibit A, is in [LICENSE.TXT](LICENSE.TXT).
- Project implementation version changed from `0.1.6-SNAPSHOT` to `0.2.0-SNAPSHOT`.

### Notes

- Protos Core language version remains 0.1.
- No language semantics changed.

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
