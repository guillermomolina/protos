# DOC002 — Documentation path contract

Status: **DOC002-B RATIFIED / CLOSED; OPTION A SELECTED**

Owning live work item: GitHub Issue `#156` — `DOC002 — Documentation information architecture and repository reorganization`.

Ratification date: **2026-09-10**.

Ratification basis: the project owner explicitly approved **Option A — role-first durable project tree** after the comparison and recommendation published by the [`DOC002-A documentation architecture audit`](../../DOC002_DOCUMENTATION_ARCHITECTURE_AUDIT.md).

This document is non-normative repository governance. It defines where durable repository documentation belongs; it does not define observable Protos semantics. Normative language and Standard Library semantics remain under `spec/`. GitHub Issues and the Protos Development Project remain the live coordination, scheduling, assignment, and priority surfaces.

## Selected destination architecture

The durable project-documentation destination model is role-first:

```text
docs/project/
  README.md
  work/
    <formal-work-item>/
  decisions/
    language/
    platform/
  architecture/
  governance/
  registries/
  evidence/
    <formal-work-item>/
  history/
```

The role directory and the formal identifier are separate axes. A document does not gain a new work-item identifier merely because it needs a place in the documentation tree.

## Path responsibilities

### `docs/project/work/<formal-work-item>/`

Durable records whose primary ownership is one formally tracked work item belong under a directory for that **individual item**, not a broad family bucket. Examples include `work/I026/`, `work/LIB001/`, `work/TOOL001/`, and `work/DOC002/`.

This applies to newly created durable work-item records when their classification is unambiguous. Existing flat legacy records are not duplicated merely to satisfy the new layout; they remain at their current paths until an explicit bounded migration moves them.

The selected architecture constrains the parent-path responsibility, not a new filename convention. Existing identifier-bearing filename styles may be retained. Any future filename simplification is a separate bounded compatibility/naming choice.

### `docs/project/decisions/language/`

Non-normative durable `Dxxx` decision records belong here as records of language/specification decisions. Their location does not make them normative; observable semantic authority remains in the applicable ratified material under `spec/`.

### `docs/project/decisions/platform/`

Durable `PLATxxx` platform/runtime architecture decision records belong here. They remain non-normative implementation-architecture records and must not be used as an alternate semantic authority.

### `docs/project/architecture/`

Cross-cutting implementation architecture that is durable project knowledge but is not primarily owned by one ordinary work item belongs here, including the class currently represented by `CORE_*` architecture records.

### `docs/project/governance/`

Repository/project rationale and maintained policy records that do not need an independent formal work lifecycle belong here. Classification alone must not invent families such as `LICxxx`.

### `docs/project/registries/`

Durable registries and closure/evidence ledgers belong here. A registry is not a live scheduling surface; GitHub retains live work state. Exact destinations for existing registry files are assigned by later bounded migration slices so path compatibility can be evaluated per file.

### `docs/project/evidence/<formal-work-item>/`

Immutable or snapshot-like evidence belongs under `evidence/`, grouped by genuine owning item when one exists. Evidence is not promoted into maintained design prose and does not become semantic authority by location.

### `docs/project/history/`

Retired repository snapshots and superseded historical records belong here when they remain useful as history but must no longer look like live project state.

### `docs/project/README.md`

This is the durable navigation entry point for the role-first project tree. DOC002-C owns creating the navigation foundation and role indexes; DOC002-B does not pre-empt that slice by bulk-populating navigation files.

## Existing top-level boundaries preserved

DOC002 does not replace the established top-level split:

- `docs/guide/` remains programmer-facing, non-normative guidance;
- `docs/design/` remains cross-cutting/exploratory non-normative design material;
- `docs/project/` remains durable project documentation, now with role-first internal classification;
- `spec/` remains the normative home of Protos language and Standard Library semantics.

Repository-root files whose discoverability or platform convention depends on root placement — including `README.md`, `ROADMAP.md`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md`, `SUPPORT.md`, `THIRD_PARTY_NOTICES.md`, `LICENSE.TXT`, and `AGENTS.md` — are not pulled into `docs/` merely for symmetry.

## Effective rule for new documentation

After DOC002-B publication, a **new** durable record under `docs/project/` must use the selected role-first destination when its role and owner are unambiguous. The first such record is this contract under `docs/project/work/DOC002/`.

This does not require agents to move an existing document before editing it. Existing authoritative/durable content is edited in place until its migration slice owns relocation. Do not create a second copy under the new path as a compatibility shortcut.

If a new document does not fit one selected role cleanly, do not create an ad-hoc category or identifier to force symmetry. Resolve the classification through DOC002 or the owning tracked work before establishing a durable new path.

## Compatibility and migration contract

Selection of the destination architecture **does not authorize a bulk rename or move**.

Migration follows these rules:

1. migrate in bounded slices ordered by classification confidence and path/link cost;
2. validate repository-relative Markdown links in every slice that moves paths;
3. search for durable repository references to moved paths before publication;
4. preserve high-cost or historically cited legacy paths until a dedicated migration justifies the churn;
5. do not create redirect/stub copies that duplicate authoritative content unless a concrete compatibility requirement is demonstrated;
6. do not infer authority changes from a path move;
7. do not use DOC002 migration to alter language, platform, library, or tooling decisions.

Commit-fixed historical references remain historical evidence; branch/path compatibility and unknown external links must be assessed explicitly when a candidate legacy path is considered for relocation.

<!-- DOC002 CONCURRENT-CUTOVER-RESIDUAL-RECONCILIATION -->
## Concurrent cutover and residual reconciliation

DOC002-A's 107-file inventory is the audit snapshot that justified the selected
architecture; it is **not** a frozen migration manifest. Protos development may
continue concurrently while DOC002 migrates the documentation tree.

Each migration slice must therefore discover and classify its candidates from
that invocation's execution-time `PUBLICATION_BASE`, rather than assuming that
the DOC002-A file list is exhaustive. A durable flat record introduced by
concurrent work during the B-to-C1 propagation window is transitional migration
debt: preserve its content and history, then absorb it into the appropriate
bounded migration/reconciliation pass instead of forcing unrelated agents to
rewrite already-published work.

Publication of DOC002-C1 propagates this contract into `AGENTS.md` as the agent
cutover. Work created after an agent has observed that policy must follow the
role-first destination for new durable records. Existing legacy records continue
to be edited in place until their explicit migration slice owns relocation.

Before DOC002 closure, the current `docs/project/` tree must be re-inventoried.
Every residual flat legacy/straggler path must be either migrated to its selected
role or deliberately retained with a concrete compatibility/path-stability
reason. DOC002-G owns this residual reconciliation as part of the final
navigation/compatibility audit; if non-trivial moves remain, G may be decomposed
into a bounded residual-migration sub-slice followed by a closure-audit
sub-slice. This decomposition does not change the ratified Option A architecture.

## Staged continuation

The approved sequence remains:

1. **DOC002-B — taxonomy ratification and path contract:** this document; no existing file moves.
2. **DOC002-C — navigation foundation:** create `docs/project/README.md` and role navigation/index structure without moving high-cost legacy files.
3. **DOC002-D — low-risk governance/history/evidence separation:** bounded low/medium-cost migrations with link validation.
4. **DOC002-E — formal work-item records:** migrate owner batches incrementally.
5. **DOC002-F — decisions and cross-cutting architecture:** move `Dxxx`, `PLATxxx`, `CORE_*`, and registries only after durable-reference review and authority-wording verification.
6. **DOC002-G — final navigation/compatibility audit:** repository-wide link/stale-path review and closure evidence.

## Intentionally deferred

DOC002-B does not decide:

- that every legacy high-cost path must eventually move;
- that compatibility stubs are required for unknown external links;
- a new filename-shortening convention inside owner directories;
- whether a future documentation linter should enforce path classes automatically; or
- exact per-file destinations where the DOC002-A classification identified path-stability concerns that require migration-time evidence.

Those questions remain bounded follow-up choices. If a later slice exposes a substantive compatibility or architecture decision beyond the approved Option A contract, it must stop at the project design-approval gate rather than deciding silently inside a move.

## DOC002-B closure criteria

DOC002-B is closed when this ratified contract is published, `docs/README.md` points to it, DOC002-A records the later ratification without rewriting its historical comparison, the changelog records the governance change, and no existing documentation file has been moved or renamed.
