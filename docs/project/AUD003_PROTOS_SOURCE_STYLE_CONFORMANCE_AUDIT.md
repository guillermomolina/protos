# AUD003 — Protos source-style conformance audit

Status: **IN_PROGRESS**

Nature: non-normative repository source-style audit and bounded migration

Policy authority: `docs/guide/SOURCE_STYLE.md` and the repository-level
`AGENTS.md` source-style rule.

Started: 2026-09-09

Authoring evidence base: `6aa3c58b69c5a1c9bc97839a3603d81c4bed84ff`

Tracking-foundation publication base: `0a10a29495573a86d3b4428c4ad2d544290becc4`

## Purpose

Persist the repository-wide work required to make hand-written Protos source
conform to the already-approved idiomatic source-style policy.

The policy itself is already selected. AUD003 does not reopen that decision and
does not create new syntax or semantics. Its job is to turn historical
source-style debt into an explicit, reviewable migration with bounded slices,
recorded exceptions, and a concrete closure gate.

Without this audit, individual rewrites can be locally correct while the
repository remains globally inconsistent and future agents have no durable way
to distinguish:

- migrated source;
- known debt;
- deliberate canonical/protocol spelling; and
- unsafe lookalikes that are not actually syntactic-sugar equivalences.

## Governing rule

Ordinary hand-written Protos should normally use stable, specified idiomatic
surface syntax when it is semantically appropriate. Explicit canonical/protocol
spelling remains valid when the protocol/lowering itself is the subject, when
bootstrap/layering requires it, when reflection/dispatch is being exercised, or
when it is materially clearer.

AUD003 therefore audits *intent*, not just tokens. It MUST NOT become a blanket
ban on canonical forms.

## Confirmed equivalence families in scope

The current audit may migrate these already-specified surface forms when the
local semantics are preserved:

- indexed read: `receiver[index]` instead of an ordinary explicit
  `receiver.at(index)` used only to spell the indexing protocol;
- indexed assignment: `receiver[index] = value` instead of explicit
  `receiver.atPut(index, value)` only where the indexed-assignment result
  contract is compatible with the use site; an `atPut` call whose own returned
  value is observed is not mechanically interchangeable;
- lazy conjunction: `left && right` instead of an explicit standard
  `left.and(() => right)` / equivalent parameterless trailing Closure spelling;
- lazy disjunction: `left || right` instead of the corresponding explicit
  standard `or` spelling;
- unary negation: `-value` instead of an ordinary explicit `value.negated()`
  used only to expose the desugaring;
- Boolean negation: `!value` instead of an ordinary explicit `value.not()` used
  only to expose the desugaring.

Every rewrite remains subject to ordinary precedence, evaluation-order,
dispatch, callback-laziness, and expression-result semantics.

## Explicit non-equivalences / review traps

The audit MUST NOT infer syntactic sugar from similar method names.

In particular:

- `div(...)` has no general surface-sugar replacement and is not style debt;
- `add(...)` is not a general spelling of binary `+`; arithmetic operators
  dispatch their symbolic selectors and collection `add` operations are normal
  APIs;
- `mod(...)` is not a repository-wide mechanical `%` replacement target merely
  because standard Integer `%` and `mod` have related numeric semantics;
- direct `atPut(...)` calls whose returned value or direct protocol dispatch is
  under test must remain explicit;
- protocol/lowering/dispatch conformance tests may deliberately retain
  `and`/`or`/`not`/`negated`/`at`/`atPut`.

If a future audit finds another candidate family, it must first establish the
specified equivalence before treating occurrences as style debt.

## Repository scope

AUD003 covers hand-written Protos source intended to model ordinary code:

1. tutorials and examples;
2. Standard Library modules;
3. bundled tools;
4. benchmarks and other ordinary repository programs;
5. Protos-source tests that are not specifically testing the canonical protocol,
   parser lowering, dispatch, bootstrap/layering, or an expression-result
   distinction;
6. documentation snippets intended to teach ordinary Protos source.

Generated/lowered/intermediate forms are outside this audit.

## Current findings

The initiating audit found substantial historical style debt predating the
approved policy:

- widespread explicit `.at(...)` and `.atPut(...)` in ordinary libraries,
  tools, examples, tutorials, benchmarks, and tests;
- widespread nested `.and() { ... }` in ordinary conformance code where `&&`
  expresses the intended lazy conjunction directly;
- smaller ordinary-code populations of explicit `.or(...)` and
  `.negated()`;
- explicit `.not()` occurrences concentrated more heavily in tests that
  intentionally exercise the Boolean protocol and therefore require
  classification rather than blind replacement.

The audit also confirmed that current surface-sugar conformance already covers
the language mechanisms themselves. The migration is therefore repository
source-style debt, not a missing parser/runtime feature.

## Work decomposition

The partitions below are mechanical audit/migration boundaries, not new language
decisions. They may be further subdivided when cost or file overlap warrants it,
and independent partitions need not be serialized merely because they are
listed in one table.

| Slice | Status | Scope / exit condition |
|---|---|---|
| AUD003-A1 | CLOSED | User-facing indexing cleanup in examples/tutorials, published historically as `SOURCE-STYLE-INDEXING-A1` at `a848371a6428cb1d80b688a9a27e6b74ec61f28a`. |
| AUD003-A2 | CLOSED | Standard Library indexing audit/migration: ordinary indexing debt migrated across six reviewed modules; no current stdlib result-sensitive/direct-protocol/bootstrap exception remained; validated under `0.2.293-SNAPSHOT`. |
| AUD003-A3 | CLOSED | Bundled-tool indexing audit/migration: ordinary indexing debt migrated across 16 execution-time Package/Test Tool modules; no result-sensitive/protocol/bootstrap exception was rewritten; validated under `0.2.295-SNAPSHOT`. |
| AUD003-A4 | CLOSED | Benchmarks and remaining ordinary-program indexing audit/migration: the final reviewed debt across two benchmark workloads and one user-facing tutorial was migrated; the complete benchmarks/examples/tutorials executable-source rescan is clean. |
| AUD003-B1 | CLOSED | Lazy Boolean spelling audit closed: B1a migrated ordinary explicit parameterless-Closure `and`/`or` spellings while retaining direct Boolean-protocol evidence; B1b migrated the reviewed ordinary trailing-Closure population, retained protocol-teaching exceptions, and closed on a fail-closed executable/guide rescan. |
| AUD003-B2 | CLOSED | Unary spelling audit closed: ordinary `negated()` uses, including concurrent I032-A general fixed-width arithmetic coverage, migrated to unary `-`; no ordinary `.not()` debt remained; direct protocol/lowering/error/slot-visibility evidence and protocol-teaching documentation remain explicit by purpose. |
| AUD003-C | OPEN | Conformance-corpus exception classification: make deliberate canonical/protocol cases explicit and migrate ordinary-code cases. |
| AUD003-D | OPEN | Prevention gate: add a bounded source-style guard that understands path/purpose exceptions or an explicit allowlist; a repository-wide dumb grep that bans canonical forms is not acceptable. |
| AUD003-E | OPEN | Final repository rescan, exception review, documentation reconciliation, and closure evidence. |

### AUD003-A1 retained evidence

`SOURCE-STYLE-INDEXING-A1` is adopted as the first closed AUD003 slice rather
than repeated.

Published commit: `a848371a6428cb1d80b688a9a27e6b74ec61f28a`

Changed source:

- `protos/examples/collections/maps.protos`
- `protos/examples/collections/path-keys.protos`
- `protos/examples/collections/identity-map.protos`
- `protos/examples/closures/rest-arguments.protos`
- `protos/tutorials/05-collections/01-maps.protos`
- `protos/tutorials/05-collections/02-identity-maps.protos`
- `protos/tutorials/09-futures/03-wait-for-many.protos`

The slice used idiomatic bracket indexing/assignment in user-facing source and
changed no specification or implementation version.

### AUD003-A2 retained evidence

GitHub coordination: Issue `#111` (`AUD003-A2`).

The execution-time Standard Library inventory found explicit `.at(...)` /
`.atPut(...)` indexing debt only in the six reviewed modules below:

- `protos/lib/collections/Array.protos`
- `protos/lib/collections/Set.protos`
- `protos/lib/collections/IdentitySet.protos`
- `protos/lib/crypto/SHA256.protos`
- `protos/lib/io/Files.protos`
- `protos/lib/json/JSON.protos`

Every migrated `atPut` use is a standalone statement whose own return value is
ignored. Reads are ordinary indexing uses. No direct indexing-protocol test,
reflection/dispatch site, bootstrap/layering requirement, or result-sensitive
`atPut` exception remained under current `protos/lib/**`.

The launcher rejects any execution-time indexing occurrence outside that
reviewed file set, rejects non-standalone `atPut`, and rescans the entire
Standard Library after materialization.

Executable validation covers collections, SHA-256, JSON and Files focal
conformance plus the complete Maven test suite. No specification or public API
changes. Implementation version: `0.2.293-SNAPSHOT`.

### AUD003-A3 retained evidence

GitHub coordination: Issue `#112` (`AUD003-A3`).

The execution-time bundled-tool inventory classified explicit `.at(...)` /
`.atPut(...)` occurrences under `protos/tools/**` before migration. The
migrated modules were:

- `protos/tools/package/ContentIdentity.protos`
- `protos/tools/package/DependencyConstraint.protos`
- `protos/tools/package/ExecutionPlan.protos`
- `protos/tools/package/LockDocument.protos`
- `protos/tools/package/LockSyntax.protos`
- `protos/tools/package/Main.protos`
- `protos/tools/package/ManifestSchemaV1.protos`
- `protos/tools/package/ReleaseVersion.protos`
- `protos/tools/package/ResolutionInput.protos`
- `protos/tools/package/ResolutionRoot.protos`
- `protos/tools/package/RuntimeNames.protos`
- `protos/tools/package/TomlDocument.protos`
- `protos/tools/package/TomlSyntax.protos`
- `protos/tools/test/Main.protos`
- `protos/tools/test/Manifest.protos`
- `protos/tools/test/Runner.protos`

All migrated reads are ordinary indexing. Every migrated `atPut` is a complete
standalone statement whose own return value is ignored; the materializer rejects
embedded/result-sensitive writes, unexpected arity, or a new indexing-bearing
tool file outside the reviewed A3 inventory. No direct indexing-protocol,
reflection/dispatch, bootstrap/layering, or result-contract exception was
rewritten.

The post-materialization scan covers the complete current `protos/tools/**`
domain and requires zero remaining explicit indexing calls in this confirmed
equivalence family.

Executable validation covers the Package Tool Protos corpus and Test Tool
implementation tests, followed by the complete Maven test suite. No
specification or public API change. Implementation version: `0.2.295-SNAPSHOT`.

### AUD003-A4 retained evidence

GitHub coordination: Issue `#113` (`AUD003-A4`).

The execution-time ordinary-program/user-facing executable inventory rescanned
all Protos source under `protos/benchmarks/**`, `protos/examples/**`, and
`protos/tutorials/**`. Explicit indexing remained only in these three reviewed
files:

- `protos/benchmarks/collections/array-sort.protos`
- `protos/benchmarks/collections/map-lookup-update.protos`
- `protos/tutorials/08-language-interactions/03-equality-and-identity-keys.protos`

The benchmark reads and tutorial writes are ordinary indexing. All migrated
`atPut` calls are complete standalone statements whose returned value is ignored.
No indexing-protocol, parser/lowering, reflection/dispatch, bootstrap/layering,
or result-contract exception was rewritten.

The launcher rejects any newly indexing-bearing file outside this reviewed A4
set and requires a zero-debt post-materialization rescan over all three domains.

Focal executable validation builds the current runtime and directly executes the
two benchmark workloads plus the tutorial through `bin/protos`, followed by the
complete Maven test suite. This ordinary benchmark/tutorial source-style cleanup
does not change the implementation version. Execution-time version: `0.2.301-SNAPSHOT`.
No specification or public API change.

### AUD003-B1a retained evidence

GitHub coordination: Issue `#114` (`AUD003-B1`).

B1 is intentionally subdivided mechanically rather than treating all historical
lazy-Boolean spellings as one blind rewrite. B1a classified the explicit
parameterless-Closure forms first.

Execution-time ordinary migration set:

- `protos/tests/conformance/control/ensure-error-cleanup-preserves-original.protos`
- `protos/tests/conformance/library/collections/array-filter-snapshot.protos`
- `protos/tests/conformance/library/collections/array-reduce-trivial-inputs-do-not-inspect-reducer.protos`
- `protos/tests/conformance/library/collections/array-results-are-open.protos`
- `protos/tests/conformance/library/collections/array-sort-stability.protos`
- `protos/tests/conformance/library/collections/set-empty-each-does-not-inspect-callback.protos`
- `protos/tests/conformance/library/json/parse-unicode-escapes-and-surrogate-pair.protos`

Each migrated use has an already-reviewed single-expression RHS Closure and
therefore maps directly to the grammar-owned lowering `left && right ->
left.and(() => right)` or `left || right -> left.or(() => right)`. The slice
does not rewrite arbitrary selectors named `and`/`or`, parameterized Closures,
or trailing braced Closure bodies.

Direct Boolean-protocol conformance remains deliberate canonical evidence under
`protos/tests/conformance/boolean/**`. The execution-time explicit-Closure
exception files retained by this tranche are:

- `protos/tests/conformance/boolean/and-invalid-result.protos`
- `protos/tests/conformance/boolean/and-selected-false.protos`
- `protos/tests/conformance/boolean/or-invalid-result.protos`
- `protos/tests/conformance/boolean/or-selected-true.protos`

The launcher rescans every current `protos/**/*.protos` file and aborts if a new
explicit `and(() => ...)` / `or(() => ...)` occurrence appears outside the
reviewed B1a source set and the Boolean-protocol exception root.

B1 remains `IN_PROGRESS`: B1b owns the substantially larger `.and() { ... }`
trailing-Closure population and must classify Closure-body shape before rewrite.
No implementation-version change. Execution-time version: `0.2.302-SNAPSHOT`.

### AUD003-B1b1 retained evidence

GitHub coordination: Issue `#114` (`AUD003-B1`).

B1b is mechanically subdivided because trailing braced Closures require body-shape
classification before a sugar rewrite is safe. B1b1 migrated only reviewed chains
in which every trailing parameterless Closure body contains exactly one expression;
there is therefore no sequence-to-expression conversion and no additional Closure
introduced by the source cleanup.

Execution-time B1b1 migration set:

- `protos/tests/conformance/call/args-zero-fresh-empty.protos`
- `protos/tests/conformance/encoding/latin1-roundtrip.protos`
- `protos/tests/conformance/library/text/import-cache.protos`
- `protos/tests/conformance/process/args-sequential.protos`
- `protos/tests/conformance/process/empty-snapshots-each.protos`
- `protos/tests/conformance/process/snapshot-identity.protos`

Each nested `left.and() { right }` level maps directly to the already-specified
lazy conjunction lowering of `left && right`. The rewritten chains preserve
left-to-right receiver evaluation and short-circuit behavior. This tranche does
not classify or rewrite multi-expression trailing Closure bodies, custom/direct
Boolean protocol tests, or unrelated canonical spellings.

B1 remains `IN_PROGRESS` after B1b1. Subsequent B1b tranches continue classifying
the remaining ordinary trailing-Closure population. No implementation-version
change. Execution-time version: `0.2.302-SNAPSHOT`.

### AUD003-B1b closure retained evidence

GitHub coordination: Issue `#114` (`AUD003-B1`).

B1b continued as bounded source-style microtranches recorded in the Issue work
log. Each executable tranche rewrote only reviewed trailing `.and() { ... }` /
`.or() { ... }` uses whose parameterless RHS Closure represented the ordinary
single-expression lazy Boolean operand, preserving left-to-right evaluation,
short-circuiting, callback laziness, result semantics, and the subject of each
conformance test.

The closure reconciliation classifies the remaining user-facing documentation
cases by purpose:

- `docs/guide/05-values-identity-equality-and-collections.md` is ordinary example
  source in a section about custom `==`; its final `.and() { ... }` spelling is
  therefore migrated to idiomatic `&&`;
- `docs/guide/04-control-flow-through-protocols.md` deliberately retains explicit
  `and` / `or` trailing-Closure spellings because that chapter is directly
  explaining those lazy protocol operations, Closure invocation, and their
  observable short-circuit behavior.

The B1b closure gate rescans every current `*.protos` source and fails on any
remaining trailing Boolean spelling outside the direct Boolean-protocol
conformance root. It separately rescans the complete Programming Guide and
requires the only retained trailing spellings there to be the three classified
protocol-teaching examples in chapter 04.

With B1a already closed, B1b closure also closes `AUD003-B1`. No language
specification, runtime behavior, implementation version, public API, native
boundary, or license terms change.

### AUD003-B2 retained evidence

GitHub coordination: Issue `#115` (`AUD003-B2`).

B2 audited the specified unary surface spellings `!value -> value.not()` and
`-value -> value.negated()` across current hand-written Protos source.

The execution-time inventory found no ordinary `.not()` migration target. The
three retained executable `.not()` occurrences are direct Boolean protocol
conformance cases: true, false, and invalid non-Boolean receiver behavior.

For `.negated()`, B2a migrated incidental negative-value construction in three
Test Tool test sources. While B2 remained open, I032-A published source-backed
fixed-width `negated` behavior and added six further explicit selector uses.
B2 classifies its three uses in
`fixed-width/arithmetic-a-positive.protos` as ordinary general-arithmetic source
and migrates them to unary `-`. The three I032-A files whose own purpose is
`negated` receiver/failure behavior remain explicit canonical evidence.

B2b also migrates the remaining ordinary distributable Test Tool occurrence in
`protos/tools/test/Runner.protos` from `result.negated()` to `-result`; the
operator is the already-specified surface lowering to the same ordinary
`negated` dispatch, so Float sign/negative-zero behavior and Test Tool
observation semantics are unchanged.

The retained executable `.negated()` occurrences are deliberate evidence:

- `protos/tests/conformance/integer/prototype-local-slot-visible.protos` directly
  proves the source-backed Integer prototype slot is visible;
- the Integer and Float `negated-delegated-non*-receiver-error.protos` cases
  directly exercise receiver-family enforcement through the selector;
- fixed-width `arithmetic-a-negated-delegated-receiver-error.protos`,
  `arithmetic-a-signed-min-negated-error.protos`, and
  `arithmetic-a-unsigned-positive-negated-error.protos` directly exercise the
  newly published fixed-width `negated` selector's receiver and checked-range
  failure contracts.

The Programming Guide retains four explicit `.not()` occurrences in chapter 04
because that chapter directly teaches Boolean `not`, its exact true/false
results, the mandatory `!` lowering, and the canonical/surface equivalence
example. `docs/guide/SOURCE_STYLE.md` retains one further canonical spelling
specifically to explain the idiomatic-source policy itself.

The B2 closure gate rescans every current `protos/**/*.protos` file plus the
complete Programming Guide and requires the remaining explicit unary protocol
spellings to equal exactly those classified exceptions. No Protos specification,
public API, native boundary, or license-term change. Because B2b changes
distributable Test Tool source, normal implementation versioning applies.

## Migration discipline

Each executable-source slice must:

1. start from current `origin/main` and inspect the actual use sites;
2. keep the change bounded to one coherent source domain;
3. classify every touched canonical form as either migration target or deliberate
   exception;
4. preserve evaluation order, laziness, dispatch, expression result and
   failure/control semantics;
5. run the focal tests for the affected source plus the repository-required full
   executable validation for that publication class;
6. bump the implementation version and changelog when the repository versioning
   policy requires it for changed distributable Standard Library/tool source;
7. avoid unrelated style normalization outside the selected slice.

Documentation-only AUD003 tracking/reconciliation slices do not by themselves
bump the implementation version.

## Exception register

AUD003 closes only when deliberate canonical spellings are understandable from
their test/module purpose or are recorded by a maintainable prevention mechanism.
The initial exception categories are:

- parser/canonicalizer/lowering equivalence tests;
- direct protocol dispatch and custom-dispatch tests;
- explicit `atPut` return/result-contract tests;
- Boolean `and`/`or`/`not` protocol tests;
- unary `negated` protocol/domain tests;
- Core bootstrap/layering code where surface spelling would create circularity
  or obscure the mechanism;
- reflection/metaprogramming tests whose purpose is the selector itself.

The register records categories rather than freezing every current line forever.
A later edit can make a formerly justified explicit form ordinary debt, and the
guard must allow that distinction to evolve.

## Closure criteria

AUD003 may be marked CLOSED only when all of the following are true:

- every repository scope listed above has been rescanned against current main;
- ordinary occurrences in the confirmed equivalence families have either been
  migrated or have a reviewed local reason to remain explicit;
- no candidate has been rewritten on the basis of an invented equivalence;
- representative user-facing examples/tutorials, Standard Library, bundled
  tools, benchmarks, and ordinary tests model the approved idiom;
- a prevention mechanism exists that catches likely regressions without banning
  legitimate canonical/protocol source;
- all required validation for the executable slices and the final integrated
  closure is green;
- the owning AUD003 GitHub Issue, this durable record, published repository
  evidence, and `docs/guide/SOURCE_STYLE.md` agree on final closure.

No normative specification revision is required merely to close AUD003 because
the audit consumes already-specified syntax and the already-approved
non-normative source-style policy.
