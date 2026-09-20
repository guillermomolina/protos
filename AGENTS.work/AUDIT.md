# Protos audit work

These instructions apply to `AUDxxx` and to any audit-type work.

Investigation contract: exhaustive. The purpose of this work class is the
audit itself: cross-document and cross-repository investigation is
legitimately required. For large normative documents, apply the
exhaustive-read discipline in `AGENTS.work/DESIGN.md`.

A ratified `Dxxx`/`PLATxxx` record discharges the corresponding design/audit
work unless concrete current-repository evidence conflicts with it; in that
case, report the conflict explicitly and reopen only through the approval
gate in `AGENTS.work/DESIGN.md`.

Retrospective audits classify each reviewed mechanism per the section below.
A recommended KEEP or REMOVE outcome that changes semantics, architecture,
or compatibility crosses the design approval gate before any change.

Already-ratified, semantics-preserving mechanical reconciliation children may
use a focal validation profile per the section below.

Durable records follow the DOC002 role-first path policy in
`AGENTS.work/REFERENCE.md`.

## Retrospective complexity and necessity audits

When auditing an already implemented feature or architecture, historical
development effort is sunk cost and MUST NOT by itself justify keeping or
removing the design. Evaluate the complexity the project is still paying.

The audit MUST distinguish, where applicable:

- programmer/user cognitive cost;
- semantic, syntax, protocol, or API surface cost;
- ongoing implementation and maintenance cost;
- drag imposed on unrelated or future feature development;
- duplicated semantic or execution machinery;
- runtime, memory, startup, synchronization, or resource cost;
- compatibility and migration cost;
- capability currently obtained from the mechanism;
- robustness and failure containment;
- scalability and future-option resilience; and
- the cost and feasibility of removing the mechanism now and reintroducing a
  capability later if real evidence appears.

A retrospective audit SHOULD classify each reviewed mechanism into one of three
outcomes:

1. **KEEP** — its current or foundational value justifies the complexity that
   remains.
2. **REMOVE_NOW / RECONSIDER_LATER** — its current cost or surface is not
   justified, but future evidence could make substantially the same capability
   worth designing again.
3. **REMOVE_PERMANENTLY** — the concept conflicts with Protos philosophy,
   duplicates a better mechanism, creates an undesirable semantic category, or
   is fundamentally inferior even if related future requirements appear.

`REMOVE_NOW / RECONSIDER_LATER` does not reserve syntax, semantics, implementation
hooks, compatibility behavior, parser productions, runtime branches, dormant
abstractions, or other scaffolding "just in case". Remove the mechanism fully
within the approved scope. If future evidence justifies the capability, redesign
it from the then-current Protos model and evidence.

To distinguish the two removal outcomes, explicitly ask:

**If future evidence justified this capability, would we want to reintroduce
substantially the same concept?**

If yes, prefer `REMOVE_NOW / RECONSIDER_LATER`. If no, prefer
`REMOVE_PERMANENTLY`.

A useful retrospective evidence matrix is:

    FEATURE
    CURRENT_REAL_USE
    USER_VISIBLE_COMPLEXITY
    ONGOING_MAINTENANCE_COST
    RUNTIME_COST
    PAY_FOR_WHAT_YOU_NEED
    GROW_AS_YOU_NEED
    COST_OF_DEFERRAL / REVERSIBILITY
    FUTURE_RESILIENCE
    SCALABILITY
    PROTOS_PHILOSOPHY
    OUTCOME

The classification itself does not authorize removal. If removing or retaining a
mechanism crosses the substantive design approval gate, present the evidence and
recommended classification to the project owner before changing semantics,
architecture, or compatibility.

## Focal validation for mechanical reconciliation children

Already-ratified, semantics-preserving mechanical reconciliation work SHOULD pay
only for the validation needed to prove the bounded child delta. In particular,
AUD003 source-style reconciliation children may use a declared focal validation
profile instead of a broad Tool-family or repository-wide integrated suite when
all of the following are true:

- the child changes only already-classified source spellings whose semantic
  equivalence is already authoritative;
- the change proves the complete scoped inventory before modification and the
  required clean/postcondition inventory afterwards;
- `scripts/source_style_guard.py` passes for the candidate;
- any executable corpus directly affected by the changed source is exercised by
  the smallest deterministic retained runner/test entry that covers that corpus;
- the child does not change specification, public API, runtime implementation,
  implementation version, or a semantic/platform decision; and
- the owning top-level executable/audit closure retains responsibility for the
  broader integrated validation required before final closure.

This is not a generic skip-tests mechanism. Each focal profile MUST name its
retained executable evidence and its validation-dependency closure. If a slice
cannot establish those facts, it falls back to the ordinary adaptive selector.

If a maintainer deliberately chooses a Pull Request for a change, the normal PR
rules still apply:

- use `Closes #N` / `Fixes #N` / `Resolves #N` only for a leaf Issue fully
  completed by merge;
- use `Refs #N` for parents/related work that must remain open;
- report the local validation performed;
- let the repository's PR CI run; and
- do not call an open PR `PUBLISHED`; publication occurs when the accepted change
  reaches `main`.

`main` is intentionally not PR-only protected at this stage. Reconsider required
PRs/checks/reviews when sustained external contribution volume or maintainer
concurrency makes the additional coordination cost worthwhile.
