# GITHUB006 — Native Issue hierarchy authority and migration closure

Policy state: **RATIFIED / ACTIVE**

Owning live Issue: GitHub #316.

This record changes repository/project coordination only. It does not define
Protos language semantics, specification authority, runtime behavior, package or
tool behavior, implementation version, release contents, or design approval.

## Authority

GitHub native Issue parent/sub-issue relationships are the canonical **live**
parent/child coordination structure for formal Protos work items.

The relationship is orthogonal to:

- `family:<FAMILY>` — formal work-family classification;
- `status:*` — live lifecycle state;
- `priority:*` — live scheduling priority;
- Assignees — active responsibility; and
- durable repository records — implementation/design/history evidence.

Project `Parent issue` and `Sub-issues progress` are derived presentation of the
native Issue hierarchy and do not form a second hierarchy authority.

## Migration evidence

On 2026-09-10 the project-owner-approved reconciliation pass scanned both open
and closed Issues and reported:

```text
NATIVE_HIERARCHY_RECONCILED: YES
RELATIONS_TOTAL=106
RELATIONS_ALREADY_NATIVE=68
RELATIONS_ADDED=38
RELATION_CONFLICTS=0
MAX_HIERARCHY_DEPTH=4
CURRENT_WORK_PARENT_ISSUE_FIELD=VISIBLE
CURRENT_WORK_VIEW=9
REPOSITORY_FILES_CHANGED=NO
```

At the 2026-09-10 migration checkpoint, the primary saved view was named
`Current work` and exposed:

```text
Title | Parent issue | Status | Priority | Labels | Assignees | Sub-issues progress
```

That saved-view name is **historical evidence**, not part of the native hierarchy
authority. GITHUB012 later made `Work queue` the canonical actionable top-level
surface and, on 2026-09-13, the saved view formerly named `Current work` was
repurposed/renamed to `History / Done`.

The hierarchy contract is unaffected: native Parent/Sub-issue relationships
remain authoritative, while `Parent issue` and `Sub-issues progress` are derived
presentation fields that may be shown in any useful Project view or Issue detail.

## Steady-state rule

A new formal child Issue that satisfies the repository's durable-granularity rule
must have its native parent relation established at creation time when possible,
or immediately afterward before the child is considered fully reconciled.

Text such as `Parent: #N` may be retained for explanation or historical
migration context, but it is not live hierarchy authority and cannot substitute
for the native relation.

If a textual declaration and native hierarchy disagree, coordination must fail
closed until the discrepancy is reconciled.

If an execution environment lacks native hierarchy mutation capability, the
agent must report that limitation and leave native linkage visibly pending rather
than claiming completion from prose alone.

Mechanical implementation phases, launcher revisions, diagnostic attempts, and
cost-driven micro-slices do not become GitHub sub-Issues merely because they are
named internally. Existing durable-granularity rules continue to decide whether a
formal child Issue should exist.

## Historical-content boundary

The migration intentionally does not rewrite every older Issue body that records
the capability available at the time of migration. Once native linkage exists,
such prose is historical context rather than authority. Bulk historical cleanup
would create noise and is outside GITHUB006.

## Closure condition

GITHUB006 closed after this policy was published on `main` and live verification
showed the migrated hierarchy conflict-free. Later Project view renames or
repurposing do not change the native hierarchy authority and do not reopen
GITHUB006.
