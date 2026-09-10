# DOC002-D2 — Retired-history migration

Status: **CLOSED**

Owning live work item: GitHub Issue `#156` — `DOC002 — Documentation information
architecture and repository reorganization`.

Execution-time publication base: `af4e31292e87267c2f2cd608268ef471e583ada5`.

DOC002-D2 moves the frozen pre-GitHub-native backlog snapshot from
`docs/project/OPEN_TASKS.md` to `docs/project/history/OPEN_TASKS.md`.

The snapshot body is preserved **byte-for-byte**. Its historical task states,
identifiers, wording, and embedded historical path text are not modernized by
this migration. Moving the file does not reactivate it and does not make it a
current scheduling or status authority.

## Reference reconciliation

Current active documentation references discovered from this invocation's
`PUBLICATION_BASE` were reconciled to the history path:

- `AGENTS.md`
- `docs/README.md`
- `docs/design/IDEAS.md`
- `docs/project/AUD002_GRAALVM_EDITOR_TOOLING_AUDIT.md`
- `docs/project/I026_A4B3_DRIVER_CUTOVER.md`
- `docs/project/IMPLEMENTATION_STATUS.md`

`CHANGELOG.md`,
`docs/project/DOC002_DOCUMENTATION_ARCHITECTURE_AUDIT.md`, and
`spec/PROTOS_SPEC_CHANGELOG.md` retain earlier path spellings as historical
evidence. In particular, the specification changelog records that
`docs/project/OPEN_TASKS.md` was the canonical concrete-work ledger at an earlier
revision; DOC002 must not rewrite that historical claim after the fact. Bare
policy references to the artifact name `OPEN_TASKS.md` may remain where they do
not claim its old location.

## Continuation

`DOC002-D` remains **IN_PROGRESS**. `DOC002-D3` owns the bounded migration of
immutable or snapshot-like evidence. It must re-discover its candidate set from
its own execution-time `PUBLICATION_BASE`.
