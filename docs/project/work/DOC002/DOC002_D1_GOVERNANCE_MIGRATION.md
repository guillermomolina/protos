# DOC002-D1 — Governance record migration

Status: **CLOSED**

Owning live work item: GitHub Issue `#156` — `DOC002 — Documentation information
architecture and repository reorganization`.

Execution-time publication base: `cd09cb6c502649062ba7ce48c06324ba9db6664a`.

DOC002-D1 is the first physical migration under the ratified role-first path
contract. It moves only the two records already classified unambiguously as
project governance/rationale:

- `docs/project/LICENSING_RATIONALE.md` →
  `docs/project/governance/LICENSING_RATIONALE.md`
- `docs/project/STANDARD_LIBRARY_NAMING.md` →
  `docs/project/governance/STANDARD_LIBRARY_NAMING.md`

The move changes documentation location only. It does not alter the APL-1.0
license terms, Standard Library semantics, public module identity, implementation
behavior, or implementation version.

## Reference reconciliation

Current repository documentation references discovered from this invocation's
`PUBLICATION_BASE` were rewritten to the new paths. Relative local links inside
the moved documents were recomputed from their new parent directory so they
continue to resolve to the same targets.

Reference-bearing files rewritten by the bounded migration:

- `CONTRIBUTING.md`
- `README.md`
- `docs/project/LIB001_COLLECTIONS_DESIGN.md`
- `docs/project/LIB002_TEXT_ENCODING_DESIGN.md`

`docs/project/DOC002_DOCUMENTATION_ARCHITECTURE_AUDIT.md` deliberately retains
the old paths because DOC002-A is the historical inventory snapshot that records
the pre-migration tree. Historical changelog prose is likewise not rewritten
merely because a later migration changed a path.

## Continuation

`DOC002-D` remains **IN_PROGRESS**. `DOC002-D2` owns the retired-history
migration, including `OPEN_TASKS.md`; `DOC002-D3` owns immutable/snapshot-like
release evidence. Each later slice re-discovers candidates and references from
its own execution-time `PUBLICATION_BASE`.
