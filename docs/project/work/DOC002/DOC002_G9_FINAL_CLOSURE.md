# DOC002-G9 — Final role-first documentation closure

Status: **CLOSED**

Owning live work item: GitHub Issue `#156` — `DOC002 — Documentation information
architecture and repository reorganization`.

Execution-time publication base: `c8b8508c261d7231fb33e510a65b18b1619826ae`.

DOC002-G9 performs the mandatory final execution-time rescan after G8 and closes
the staged repository documentation reorganization selected by DOC002-B/F0.

## Final repository rescan

At the execution-time base:

- `docs/project/` has exactly the seven ratified role directories:
  `work/`, `decisions/`, `architecture/`, `governance/`, `registries/`,
  `evidence/`, and `history/`;
- direct tracked files under `docs/project/` consist only of `README.md`;
- residual flat durable project records: **0**;
- canonical Dxxx decision records discovered: **9**, all under
  `decisions/language/` or `decisions/tooling/`, with unique identifiers;
- canonical PLATxxx records discovered: **16**, all under
  `decisions/platform/`, with unique identifiers;
- canonical `CORE_*` architecture records discovered: **2**, all under
  `architecture/`;
- high-value registries verified: **3**, all under `registries/`;
- owner-scoped records discovered below `work/<formal-work-item>/`: **92**.

Counts are execution-time audit evidence, not frozen manifests. Future canonical
records may increase them without reopening DOC002.

## Final compatibility and navigation reconciliation

G9 retires migration-era instructions from `AGENTS.md`, `docs/README.md`,
`docs/project/README.md`, and `docs/project/work/README.md` and expresses the
same ratified architecture as steady-state policy:

- new durable records use their role-first destination immediately;
- direct unclassified durable records under `docs/project/` are non-compliant;
- a later unexpected legacy path is handled by an explicit bounded
  classification/migration change, not opportunistic movement or duplication;
- historical DOC002 records, repository history and chronology may retain path
  spellings that are evidence of the state they documented;
- current references use canonical role-first paths.

The final active-repository stale-path scan excludes explicit historical
surfaces, DOC002's own migration/closure evidence, and exactly two
DOC002-E8-documented DIST001-E4C2 verifier literals that intentionally preserve
the historical `docs/project/PERF002_TRUFFLE_COMPILABILITY.md` value embedded in
the persisted `0.2.236` candidate archive contract. Those two literals are
compatibility evidence, not current locators. No other current consumer of a
legacy `docs/project/<basename>` path for a migrated role-first record remains.

The final active Markdown-link audit covers current documentation outside
explicit history/evidence and DOC002 migration records. The validator excludes
fenced and inline code before parsing Markdown links, so source/API examples are
not mistaken for navigation. All checked local links resolve after the navigation
reconciliation.

## GitHub coordination boundary

GitHub Issues remain the live coordination/presentation layer and are not made
semantic authority by this closure. Current live authority/evidence pointers for
the migrated DOC001, AUD003, LM008, PERF001, PERF004, TOOL001 and TOOL002 owner
records were reconciled around the corresponding G publications. Historical
retrospective Issue prose may retain publication-time paths when it is clearly
describing earlier repository state.

Issue #156 itself is intentionally closed only after this repository publication
is confirmed, so an unpublished candidate can never claim GitHub closure.

## Closure

DOC002-A through DOC002-G are complete. `DOC002-G9` is **CLOSED**,
`DOC002-G` is **CLOSED**, and `DOC002` is **CLOSED**.

The ratified role-first information architecture is now steady-state repository
policy. DOC002 closure does not freeze the documentation corpus or prevent later
explicit migrations; it closes this reorganization program and its migration-era
compatibility rules.

No specification, observable semantics, language/tool/platform decision,
work-item state, implementation/runtime behavior, implementation version, public
API, registry/blocker meaning, performance guarantee, or license term changes.
