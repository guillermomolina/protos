# LM008-B — Grammar, evaluation, binding and callable surface audit

Status: IN_PROGRESS

Parent: `LM008 — Core Language Surface Completeness`

Durable coordination: GitHub Issue `#100`

Nature: non-normative audit/evidence record

## Scope decomposition

`LM008-B` remains one durable work item. For bounded execution it is audited in
four mechanical checkpoints that do not allocate new formal project identifiers:

- `B1` — lexical, literal, separator and basic grammar surface;
- `B2` — binding, writes, member/index/operator lowering, object construction/composition syntax and evaluation order;
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

## B2 checkpoint

Checkpoint state: COMPLETE

Validation class: `TEST_IMPACT`

Normative authorities:

- `spec/PROTOS_GRAMMAR.md` for accepted object/index/operator source forms and
  mandatory desugaring;
- `spec/semantics/EXECUTION_AND_CONTROL.md` for lookup, creation, assignment and
  observable evaluation order;
- `spec/semantics/OBJECT_MODEL.md` for explicit member writes, object parents and
  composition;
- applicable callable/value modules only for the ordinary messages reached by
  already-defined lowering.

B2 includes object construction and composition syntax. The original LM008-B
scope already named composition, while the first mechanical B1-B4 decomposition
failed to repeat it explicitly. Assigning it to B2 is a scope-accounting
correction only; it selects no new semantics.

### Evidence matrix

| Surface row | Normative requirement | Retained language-level evidence | Supplementary mechanism evidence | Classification |
|---|---|---|---|---|
| Bare-name lookup precedence | Current local context, then captured lexical locals, then ordinary receiver lookup; no prelude shortcut ahead of receiver semantics | existing `regression/lexical-read-beats-receiver-slot.protos` plus retained I027 receiver-fallback regression evidence | `ProtosActivation.lookup` | `COVERED` |
| Bare slot creation | `x: rhs` creates only in the current context, performs no destination lookup, rejects duplicate local creation and returns the exact RHS object on success | existing `object/duplicate-local-create-error.protos` plus `core-surface/binding-slot-write-exact-rhs.protos` | `CanonicalBareSlotMutationExecutionTest` | `COVERED` |
| Bare assignment destination | `x = rhs` selects the nearest existing current/captured lexical local, then receiver-local slot, before RHS; it never delegates or implicitly creates and never re-resolves after RHS effects | existing `control/bare-assignment-target-fixed-before-rhs.protos`, `control/bare-assignment-missing-target-before-rhs.protos` and `object/local-shadow-assignment-stays-local.protos` | `ProtosBareAssignNode`, `ProtosActivation.writableLexicalContext` | `COVERED` |
| Explicit member create/assign | Member writes operate on the evaluated ordinary target's local slots, do not mutate delegation parents, and successful writes return the exact RHS object | existing `regression/inherited-member-assignment-signals-error.protos`, `object/local-shadow-assignment-stays-local.protos` and `core-surface/binding-slot-write-exact-rhs.protos` | `CanonicalExplicitMemberMutationExecutionTest` | `COVERED` |
| Explicit member target before RHS | Target expression is evaluated before RHS and the selected target is not replaced by later RHS effects | `core-surface/member-assignment-target-before-rhs.protos` | `ProtosMemberAssignNode` | `COVERED` |
| Indexed read lowering | `receiver[index]` is ordinary `receiver.at(index)` dispatch, not dynamic slot access | existing `surface-sugar/indexed-read-ordinary-dispatch.protos` | `Canonicalizer` lowers `SurfaceIndex` to `CanonicalSend("at", ...)` | `COVERED` |
| Indexed assignment lowering/result/order | `receiver[index] = value` performs ordinary `atPut(index, value)` protocol dispatch; receiver, index and value are evaluated left-to-right; the syntax returns the exact RHS rather than the `atPut` result | existing `collections/array-indexed-assignment-exact-rhs.protos`, `collections/array-indexed-assignment-ignores-atput-return.protos` plus `core-surface/indexed-assignment-evaluation-order.protos` | `CanonicalIndexedAssign` / `ProtosIndexedAssignNode` | `COVERED` |
| Indexed slot creation absence | `object[index]: value` is not a slot-creation form | `lm008-b2-indexed-slot-creation-rejected.protos` through the B2 parser-source harness | existing parser assignment tests | `INTENTIONAL_ABSENCE_COVERED` |
| Eager unary/binary operator lowering | Unary `-`/`!` and ordinary arithmetic/comparison/custom symbolic binary syntax lower to the specified ordinary selectors; eager binary operands evaluate left-to-right | existing `surface-sugar/unary-negated-ordinary-dispatch.protos`, `surface-sugar/unary-not-ordinary-dispatch.protos`, `surface-sugar/arithmetic-ordinary-dispatch.protos`, `surface-sugar/comparison-ordinary-dispatch.protos`, `surface-sugar/custom-binary-ordinary-dispatch.protos`, plus `core-surface/binary-operator-evaluation-order.protos` | `Canonicalizer`; parser custom-operator tests | `COVERED` |
| Lazy Boolean operator lowering | `&&` / `||` dispatch through ordinary `and` / `or` with generated RHS Closure and preserve selected-only evaluation | existing `surface-sugar/lazy-and-custom-dispatch.protos`, `lazy-and-selected-exactly-once.protos`, `lazy-and-short-circuit.protos`, and corresponding `lazy-or-*` probes | `Canonicalizer` Boolean lowering | `COVERED` |
| Identity/non-identity syntax | `===` / `!==` reach their dedicated non-overridable identity operations and preserve ordinary left-before-right evaluation requirements | retained `equality/*identity*` conformance; `core-surface/binding-slot-write-exact-rhs.protos` uses identity to observe exact write results | `CanonicalIdentity` / `CanonicalNotIdentity` lowering | `COVERED` |
| Object parent expression and construction order | Bare object uses the standard Object parent; an explicit allowed parent expression is evaluated before object-body items and becomes the delegation parent | `core-surface/object-parent-composition-evaluation-order.protos`; existing ordinary object/delegation corpus | `CanonicalObjectExecutionTest`; parser object-expression tests | `COVERED` |
| Parent-expression grammar boundary | Grouped call parent expressions are allowed; unparenthesized indexed parents are not | positive grouped-call parent in `core-surface/object-parent-composition-evaluation-order.protos`; negative `lm008-b2-unparenthesized-index-parent-rejected.protos` | `ProtosParserObjectExpressionTest` | `INTENTIONAL_ABSENCE_COVERED` for the forbidden unparenthesized form |
| Composition syntax, reservations and order | `...source` is object-body-only; source evaluates before copying; copied locals are visible to later items; direct local declarations reserve names independently of textual position; conflicts fail rather than overwrite | existing `object/composition-reservations.protos`, `object/composition-conflict-error.protos`, positive `core-surface/object-parent-composition-evaluation-order.protos`, and negative `lm008-b2-composition-outside-object-rejected.protos` | `CanonicalCompositionExecutionTest`; parser/canonicalizer object tests | `COVERED` |

The existing `surface-sugar` family is deliberately reused rather than cloned
under `core-surface`: those ordinary Protos programs directly prove that the
syntactic forms dispatch through guest-visible protocols, including a custom
symbolic selector published with ordinary `Object.alias`.

The B2 parser-negative files remain outside the runtime manifest because their
required outcome is frontend rejection before semantic execution.

### B2 audit result

No B2 row requires a new semantic or architectural decision.

No already-normative B2 promise was found missing from the current guest-visible
implementation. The newly added programs close evidence gaps around exact slot-
write result identity and observable evaluation order; the remaining rows reuse
already-retained Protos conformance instead of duplicating it.

`LM008-B` remains `IN_PROGRESS`. `B3` is next: Closure forms, invocation,
parameters/default/rest/spread and trailing Closure syntax. B3 must cross-check
the closed I025 parameter-ordering evidence instead of duplicating it.
