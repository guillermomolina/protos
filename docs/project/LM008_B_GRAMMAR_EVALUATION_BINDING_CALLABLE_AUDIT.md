# LM008-B — Grammar, evaluation, binding and callable surface audit

Status: IN_PROGRESS

Parent: `LM008 — Core Language Surface Completeness`

Durable coordination: GitHub Issue `#100`

Nature: non-normative audit/evidence record

## Scope decomposition

`LM008-B` remains one durable work item. For bounded execution it is audited in
four mechanical checkpoints that do not allocate new formal project identifiers:

- `B1` — lexical, literal, separator and basic grammar surface;
- `B2` — binding, writes, member/index/operator lowering and evaluation order;
- `B3` — Closure forms, invocation, parameters/default/rest/spread and trailing
  Closure syntax;
- `B4` — receiver binding/extraction, `super`, non-local return and final B
  reconciliation.

This decomposition selects no semantics. A newly exposed substantive choice still
stops at the normal Dxxx/PLATxxx approval gate.

## B1 checkpoint

Checkpoint state: COMPLETE

Validation class: `TEST_IMPACT`

Normative authority: `spec/PROTOS_GRAMMAR.md`.

B1 changes no normative specification, production implementation, public API or
implementation version. It adds executable evidence where the guest-visible
surface was previously proven mainly by lexer/parser unit tests.

### Evidence matrix

| Surface row | Normative requirement | Retained language-level evidence | Supplementary mechanism evidence | Classification |
|---|---|---|---|---|
| Unicode identifiers / Unicode 17 XID / NFC | `_` or Unicode 17 `XID_Start`, followed by `XID_Continue`; spelling is case-sensitive and NFC | `core-surface/grammar-identifiers-and-member-names.protos` exercises NFC non-ASCII identifiers, `_`, and digit continuation in ordinary execution | `UnicodeXid17ConformanceTest` exhaustively checks Unicode 17 XID tables | `COVERED` |
| Non-NFC identifier rejection | Reject rather than silently normalize | `lm008-b1-non-nfc-identifier-rejected.protos` through the source-level rejection harness | existing focused lexer tests | `INTENTIONAL_ABSENCE_COVERED` |
| Exact reserved set and contextual member names | Exactly `this context args super true false null` are reserved; ordinary names such as `if`, `else`, `while`, `class` remain identifiers; all seven reserved spellings are valid after `.` | `core-surface/grammar-identifiers-and-member-names.protos` creates/reads all seven contextual member names and uses representative non-reserved names as bindings | existing lexer/parser reserved/member-name tests | `COVERED` |
| Reserved spelling as bare creation target | Reserved words remain invalid where grammar requires `identifier` | `lm008-b1-reserved-bare-target-rejected.protos` | parser tests | `INTENTIONAL_ABSENCE_COVERED` |
| Integer literal forms | Decimal plus binary/octal/hex radix spelling and separators are guest-visible numeric literals | `core-surface/grammar-integer-literals.protos` | lexer numeric-token tests | `COVERED` |
| Decimal floating literal | Decimal exponent form reaches ordinary Float evaluation | `core-surface/grammar-decimal-float-literal.protos` with exact binary64 expectation | existing numeric tests | `COVERED` |
| Malformed radix commitment | malformed radix prefixes may not be split into recoverable tokens | `lm008-b1-malformed-radix-rejected.protos` | lexer malformed-number tests | `INTENTIONAL_ABSENCE_COVERED` |
| String literal forms, escapes, quote-run and multiline indentation | single, double and triple-double forms share the exact escape set; triple-double form owns multiline/indentation and exact quote-run behavior | `core-surface/grammar-string-literals.protos` exercises all three forms, Unicode/newline escapes, the six-quote empty-triple case and structural indentation normalization | focused lexer quote-run/string tests | `COVERED` |
| Invalid/unterminated String rejection | invalid escape and unterminated literal are lexical errors, never partial tokens | `lm008-b1-invalid-string-escape-rejected.protos` and `lm008-b1-unterminated-triple-string-rejected.protos` | lexer rejection tests | `INTENTIONAL_ABSENCE_COVERED` |
| Horizontal whitespace / logical newline / comments / separators | horizontal whitespace is exactly SPACE/TAB; LF/CR/CRLF are logical newlines; block-comment newlines are consumed; semicolon/newline are expression separators as specified | `core-surface/grammar-separators-comments-continuation.protos` deliberately mixes LF/CR/CRLF, line/block comments and semicolon | lexer/parser separator tests | `COVERED` |
| Non-Core whitespace outside lexical constructs | NBSP and other excluded whitespace-like code points are not silently ignored | `lm008-b1-non-core-whitespace-rejected.protos` | lexer whitespace tests | `INTENTIONAL_ABSENCE_COVERED` |
| Newline continuation vs separation | newline continues only while the construct is grammatically incomplete; a later `=>` cannot attach to a completed prior expression | positive continuation cases in `core-surface/grammar-separators-comments-continuation.protos`; negative `lm008-b1-newline-closure-attachment-rejected.protos` | expression-separator/parser tests | `COVERED` |

The source-invalid fixtures intentionally do **not** enter the runtime conformance
manifest. Their required observation point is lexer/parser rejection before a
semantic Process exists. This follows the already-published I025 precedent:
invalid Protos source is retained as source and the smallest Java harness observes
the frontend rejection; it is not mislabeled as a runtime `Error` expectation.

### B1 audit result

No B1 row requires a new semantic or architectural choice.

No already-normative B1 promise was found to be absent from the current
guest-visible implementation. The B1 gaps were evidence gaps, so LM008 can close
this checkpoint by retaining tests without allocating an implementation owner.

`LM008-B` itself remains `IN_PROGRESS`. `B2` is next and must audit binding,
writes, mandatory member/index/operator lowering and observable evaluation order.
Supporting operations used by B1 probes are not thereby pre-classified for B2-B4.
