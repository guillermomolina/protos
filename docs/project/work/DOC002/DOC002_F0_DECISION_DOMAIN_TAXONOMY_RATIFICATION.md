# DOC002-F0 — Decision-domain taxonomy refinement ratification

Status: **RATIFIED / CLOSED**

Owning live checkpoint: GitHub Issue `#292` — `DOC002-F0 — Decision-domain
taxonomy refinement for non-language Dxxx records`.

Parent work: GitHub Issue `#156` — `DOC002 — Documentation information
architecture and repository reorganization`.

Execution-time publication base: `c8329f44e97cdd84ddaa206a578023149095c67c`.

Explicit project-owner approval: **2026-09-10**.

Selected option: **C — add `docs/project/decisions/tooling/`**.

## Ratified refinement

DOC002 retains the Option A role-first documentation architecture and refines
only the decision subtree:

```text
docs/project/decisions/
  language/
  tooling/
  platform/
```

The domains are semantic roles, not identifier families:

- `language/` — durable non-normative rationale/outcome records whose primary
  domain is observable language/specification semantics;
- `tooling/` — durable implementation-independent tooling/package-system,
  Package Tool, Test Tool or similar project-tool contracts that neither define
  observable Protos semantics nor select concrete host/runtime architecture;
- `platform/` — durable non-normative host/runtime architecture decisions,
  normally `PLATxxx`.

Existing decision identifiers remain unchanged.

## Current Dxxx classification

The execution-time precondition confirms exactly nine residual flat Dxxx
decision records:

- language: D047, D048, D049, D051, D052;
- tooling: D053, D055, D056, D057.

D053, D055, D056 and D057 each retain explicit evidence that the applicable
Core/specification revision is unchanged.

## Authority boundary

All three `decisions/` roles are non-normative repository records. Observable
Protos language and Standard Library semantics remain authoritative under
`spec/`. The tooling role is not an alternate semantic authority and the
platform role is not an escape hatch for semantic decisions.

F0 does **not** rewrite the content/outcome of any Dxxx or PLATxxx decision and
does not move an existing decision file. The current language D047/D048/D049/
D051/D052 records still contain historical `Nature:` wording that can imply
normative document authority; DOC002-F1 owns the bounded relocation plus
authority-wording reconciliation while preserving their actual decision
outcomes.

## Continuation

`DOC002-E` remains **CLOSED**. `DOC002-F` is **IN_PROGRESS** after this
ratification. `DOC002-F1` is **READY** for the five language-domain Dxxx records.
`DOC002-F2` remains sequenced after F1 for the four tooling-domain Dxxx records.

No specification, language/library semantics, Package Tool/Test Tool contract,
platform architecture, implementation/runtime behavior, implementation version,
decision identifier, or license term changes.
