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
| AUD003-A4 | OPEN | Benchmarks and remaining ordinary-program indexing audit/migration. |
| AUD003-B1 | OPEN | Ordinary lazy Boolean spelling audit for explicit `and`/`or`, including representative conformance tests whose subject is not the Boolean protocol itself. |
| AUD003-B2 | OPEN | Ordinary unary spelling audit for explicit `not`/`negated`, retaining protocol/lowering tests. |
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
