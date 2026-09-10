# DOC002-C2 — Role-first navigation foundation

Status: **CLOSED**

Owning live work item: GitHub Issue `#156` — `DOC002 — Documentation information architecture and repository reorganization`.

Publication base: `d588fa597e5ad148476030e14a54bca3ba2bc03b`.

DOC002-C2 establishes the navigation surface required by the ratified
[DOC002 path contract](DOC002_DOCUMENTATION_PATH_CONTRACT.md). It is a
non-normative repository-documentation change and does not alter Protos
semantics, implementation, public API, or implementation version.

## Materialized navigation

The slice creates the root [`../../README.md`](../../README.md) project index and
role indexes for `work`, `decisions/language`, `decisions/platform`,
`architecture`, `governance`, `registries`, `evidence`, and `history`.

The indexes describe responsibilities and compatibility rules rather than
materializing a frozen list of every legacy file. This is intentional: Protos
continues to develop concurrently, and DOC002 migration candidates are discovered
from each slice's execution-time `PUBLICATION_BASE`.

## Compatibility boundary

DOC002-C2 moves or renames **no existing documentation file**. Flat legacy and
pre-cutover straggler paths remain valid compatibility locations until a later
bounded DOC002 migration owns them. Navigation must not be used as permission to
copy a legacy record into a second authoritative location.

DOC002-C is closed by C1 + C2. DOC002-D is the next migration phase and owns the
first low-risk governance/history/evidence relocations, subject to current-tree
classification and link validation at execution time.
