# DOC002-F2 — Tooling decision migration

Status: **CLOSED**

Owning live work item: GitHub Issue `#156` — `DOC002 — Documentation information
architecture and repository reorganization`.

Classification checkpoint: GitHub Issue `#292` — DOC002-F0, **CLOSED** after
explicit project-owner ratification of Option C.

Execution-time publication base: `1acdab3a7e9d50205ab5118aaa3d99b809cf53e9`.

DOC002-F2 migrates the exact four residual tooling/package-system Dxxx records
classified by F0:

- `docs/project/D053_PACKAGE_EXECUTION_PLAN_ABI_EVOLUTION.md` → `docs/project/decisions/tooling/D053_PACKAGE_EXECUTION_PLAN_ABI_EVOLUTION.md`
- `docs/project/D055_ASYNCHRONOUS_EXACT_EXECUTION_BOUNDARY.md` → `docs/project/decisions/tooling/D055_ASYNCHRONOUS_EXACT_EXECUTION_BOUNDARY.md`
- `docs/project/D056_EXTERNAL_PACKAGE_PATH_DEPENDENCY_SEMANTICS.md` → `docs/project/decisions/tooling/D056_EXTERNAL_PACKAGE_PATH_DEPENDENCY_SEMANTICS.md`
- `docs/project/D057_WORKSPACE_SEMANTICS_FOR_IMMUTABLE_EXTERNAL_PACKAGES.md` → `docs/project/decisions/tooling/D057_WORKSPACE_SEMANTICS_FOR_IMMUTABLE_EXTERNAL_PACKAGES.md`

## Authority audit

All four source records were already correctly framed before relocation:

- each is `RATIFIED`;
- each describes itself as an `implementation-independent` Package Tool,
  package-model, or Test Tool decision/contract;
- D053 and D055 state `Specification revision: UNCHANGED`;
- D056 and D057 state `Core specification revision: UNCHANGED`;
- none uses a `Nature:` line that describes the repository record itself as
  normative.

Therefore F2 performs **no authority-wording rewrite**. Content continuity is
strict: each destination must equal its execution-time source except for
deterministic relative-link/path rebasing caused by the relocation.

The tooling directory remains non-normative repository documentation. Observable
Protos language and Standard Library semantics remain authoritative under
`spec/`; concrete runtime/host architecture remains a separate platform role.

## Reference reconciliation

Current active Markdown references to the moved concrete paths, discovered from
this invocation's `PUBLICATION_BASE`, were reconciled:

- `docs/project/TOOL001_F2E_EXTERNAL_MATERIALIZATION.md`

Historical chronology, the DOC002-A inventory snapshot, retired history,
specification changelog chronology and prior DOC002 migration records preserve
old path spellings when they describe earlier repository state.

There is no approved non-Markdown compatibility exception in F2. Discovery of
an executable/configuration consumer of an old path makes publication fail
closed.

## Continuation

`DOC002-E` remains **CLOSED**. `DOC002-F0` remains **RATIFIED / CLOSED**.
`DOC002-F1` remains **CLOSED**. `DOC002-F2` is **CLOSED**. `DOC002-F` remains
**IN_PROGRESS** and `DOC002-F3` is **READY** for the bounded `PLATxxx` platform
decision migration/authority review.

No specification, Core semantics, Package Tool/Test Tool/package-model decision
outcome, implementation/runtime behavior, implementation version, platform
architecture, decision identifier, or license term changes.
