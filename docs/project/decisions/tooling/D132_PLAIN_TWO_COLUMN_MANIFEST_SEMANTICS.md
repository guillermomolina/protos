# D132 — Plain two-column Test Tool manifest semantics

Status: RATIFIED — Candidate B′ selected
Issue: #506
Primary consumer: TOOL005-B3A
Depends on: D122, D123, D125, D126, D129

## Decision

The Test Tool keeps the existing strict `Manifest.load(...)` contract for the
current three-column manifest schema and introduces a separate generic semantic
loader for explicit case outcomes.

```text
Manifest.load()
    -> existing strict three-column loader

Manifest.loadCaseOutcomes(filesystem, caseNamespace)
    -> generic explicit two-column outcome loader
```

The two-column source shape is:

```text
relative-case-key<TAB>true
relative-case-key<TAB>error
```

It is normalized into the existing logical TestPlan/CaseSpec model.

## Candidate

```text
D132_SELECTED_CANDIDATE=B_PRIME
D132_FUTURE_RESILIENCE=10/10
D132_SCALABILITY=10/10
D132_PROTOS_PHILOSOPHY=10/10
```

## Case identity

Case identity is explicit and independent from the physical filesystem path.

For two-column manifests:

```text
caseNamespace + relative-case-key
    -> stable logical CaseId
```

The namespace is supplied by the corpus/TestPlan authority. The generic loader
must not contain Package Tool-specific names.

Therefore:

```text
CorpusId != CaseId
CaseId != filesystem path
CaseId != temporary path
CaseId != worker identity
CaseId != execution requirement
```

## Existing three-column loader

The current `Manifest.load(...)` remains strict:

```text
path<TAB>expectation<TAB>expected
```

No automatic shape guessing is added. Malformed three-column records fail closed.

## New two-column semantic loader

The new loader accepts only:

```text
relative-case-key<TAB>true
relative-case-key<TAB>error
```

and normalizes:

```text
true  -> boolean expectation with expected=true
error -> error expectation
```

Reject:

- empty case keys;
- missing columns;
- more than two columns;
- unknown outcome values;
- malformed records;
- duplicate logical CaseIds.

The loader must be generic and must not contain Package Tool literals.

## D126 boundary

CorpusBinding remains authoritative for CorpusId, source authority, plan-loader
selection and the logical case namespace. The loader acquires no execution
authority.

```text
EXECUTION_BINDING_OWNS_SOURCE=NO
CORPUS_BINDING_OWNS_EXECUTION=NO
```

## D125 boundary

The loader does not infer execution requirements. Package Tool plain corpora
remain bound to:

```text
protos/test/package
```

## D129 boundary

D129 remains authoritative for project-tree provisioning. D132 introduces no
CaseAuthority and does not alter project-tree lifecycle.

## Relationship to package-toml

The existing package-toml loader remains unchanged. It is not generalized into
the new semantic loader and its historical Package Tool-specific namespace
behavior remains isolated.

## Explicit selection / no autodetection

Loader selection is explicit.

Do not make the existing `Manifest.load()` guess a schema from column count.

The TestPlan/CorpusBinding selects the semantic loader explicitly.

## No corpus-format migration

The existing Package Tool two-column manifests remain unchanged. The Test Tool
adapts to their established representation rather than rewriting corpus data.

## Identity model

A conceptual example:

```text
caseNamespace:
    protos/package-tool/version

relative-case-key:
    release-version-parse-core.protos

CaseId:
    protos/package-tool/version/release-version-parse-core
```

The exact canonical serialization must follow existing Test Tool CaseSpec
identity conventions; the normative requirement is that identity is stable,
explicit and independent from physical location.

## Governance consequence

TOOL005-B3A is authorized to continue using the three existing two-column
Package Tool plain corpora:

```text
protos/corpus/package-tool/version
protos/corpus/package-tool/lock
protos/corpus/package-tool/resolution-input
```

with the new generic semantic loader.

No further decision is required merely to support those existing manifests.

A new Dxxx decision remains required for a materially independent architecture
axis discovered during implementation.

## Implementation reconciliation

B3A must:

1. add the generic two-column semantic loader;
2. keep `Manifest.load(...)` strict three-column;
3. provide an explicit case namespace;
4. update the three plain Package Tool bindings to use the new loader;
5. keep the three CorpusIds and `protos/test/package` unchanged;
6. leave D129 project-tree implementation deferred to B3B.

## Closure contract

```text
SPECIFICATION_CHANGED=NO
IMPLEMENTATION_VERSION_CHANGED=NO
```

The decision itself is governance/tooling documentation only.
