# DOC002-F1 — Language decision migration and authority reconciliation

Status: **CLOSED**

Owning live work item: GitHub Issue `#156` — `DOC002 — Documentation information
architecture and repository reorganization`.

Classification checkpoint: GitHub Issue `#292` — DOC002-F0, **CLOSED** after
explicit project-owner ratification of Option C.

Execution-time publication base: `1179259b50d62e67426cf0a880823359ea5c5398`.

DOC002-F1 migrates the exact five language/specification-domain Dxxx records
classified by F0:

- `docs/project/D047_NETWORKING_DECISION.md` → `docs/project/decisions/language/D047_NETWORKING_DECISION.md`
- `docs/project/D048_IP_ADDRESS_ENDPOINT_CONSTRUCTION.md` → `docs/project/decisions/language/D048_IP_ADDRESS_ENDPOINT_CONSTRUCTION.md`
- `docs/project/D049_SHARED_STANDARD_OBJECT_PUBLICATION.md` → `docs/project/decisions/language/D049_SHARED_STANDARD_OBJECT_PUBLICATION.md`
- `docs/project/D051_CONDITIONAL_SURFACE_BOUNDARY.md` → `docs/project/decisions/language/D051_CONDITIONAL_SURFACE_BOUNDARY.md`
- `docs/project/D052_TCP_LIVE_RESOURCE_OBJECT_TOPOLOGY.md` → `docs/project/decisions/language/D052_TCP_LIVE_RESOURCE_OBJECT_TOPOLOGY.md`

## Authority wording reconciliation

The moved files are durable **non-normative decision/rationale records**.
DOC002-F1 corrects only legacy metadata wording that described the repository
record itself as `normative`:

- D047: non-normative record; normative networking authority is
  `spec/io/NETWORK.md`;
- D048: non-normative record; normative authority remains the applicable
  `spec/` material at revision `0.1.391`;
- D049: non-normative record; normative authority remains the applicable
  `spec/` material at revision `0.1.389`; its final historical wording is also
  clarified from “ratified normative decision” to “ratified decision and its
  normative specification revision”;
- D051 and D052: `Nature:` is made explicitly non-normative while their existing
  `Primary normative owner:` lines remain unchanged.

No selected semantic rule, rejected alternative, future/scale conclusion,
implementation handoff, specification revision, or project-owner approval is
changed.

## Reference reconciliation

Current active Markdown references to the moved concrete paths, discovered from
this invocation's `PUBLICATION_BASE`, were reconciled:

- none

Historical chronology, the DOC002-A inventory snapshot, retired history,
specification changelog chronology and prior DOC002 migration records preserve
old path spellings when they describe earlier repository state.

There is no approved non-Markdown compatibility exception in F1. Discovery of
any executable/configuration consumer of one of the old paths makes publication
fail closed.

## Continuation

`DOC002-E` remains **CLOSED**. `DOC002-F0` remains **RATIFIED / CLOSED**.
`DOC002-F1` is **CLOSED**. `DOC002-F` remains **IN_PROGRESS** and `DOC002-F2`
is **READY** for the four tooling-domain Dxxx records D053/D055/D056/D057.

No specification, language/library semantics, decision outcome, implementation,
runtime/tooling behavior, implementation version, platform architecture,
decision identifier, or license term changes.
