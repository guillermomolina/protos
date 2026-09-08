# Protos Open Tasks

This file records concrete non-normative work that should be done but is not
blocked by an unresolved language-semantic decision.

It is distinct from:

- `docs/project/IMPLEMENTATION_BLOCKERS.md`, which records implementation work that
  cannot proceed until normative semantics are resolved;
- `docs/design/IDEAS.md`, which records exploratory possibilities not yet committed as
  implementation work;
- normative specification Open Design Topics, which track unresolved semantic
  or API design.

An item should move here from `../design/IDEAS.md` only when there is a concrete outcome
worth implementing or investigating. If work becomes blocked on normative
semantics, record that dependency in `IMPLEMENTATION_BLOCKERS.md` instead.

Task states:

- `OPEN`: concrete work remains.
- `IN PROGRESS`: implementation or investigation is actively underway.
- `BLOCKED`: use only for a non-semantic external dependency; normative blockers
  belong in `IMPLEMENTATION_BLOCKERS.md`.
- `CLOSED`: the work is complete or obsolete.

## Open tasks

### AUD001 — Retrospective design-decision ratification audit

Status: **OPEN**
Priority: **HIGH**
Nature: non-normative governance and provenance audit

Audit the provenance and continued suitability of design decisions D001-D045,
excluding D046 because it is already under separate active user review. The
purpose is to distinguish explicit project-owner selection from agent-authored
recommendations, broad implementation instructions, patch execution, and
publication evidence.

Current triage:

- D037 and D038 have explicit project-owner confirmation and need only have that
  evidence recorded.
- D020 and D039-D045 require priority review because no recovered evidence yet
  demonstrates explicit project-owner selection of their complete published
  semantics.
- D021-D036 require provenance and substance review; publication alone is not
  ratification.
- D001-D019 are expected to be predominantly project-owner decisions, but their
  approval evidence must be checked rather than inferred.

Recorded review results:

- D045 remains `NEEDS_USER_DECISION`. Its task-scoped ownership core is the
  recommended design: synchronous Closure/method activations do not create
  structured scopes; returning, storing or wrapping a pending Future does not
  alter its ownership edge; distinct asynchronous child tasks own their own
  descendants; and `detach()` is the explicit operation that removes the edge.
  This avoids per-activation draining, result-shape/escape heuristics and hidden
  implicit detachment while preserving ordinary Future-returning APIs. This is
  an audit recommendation only and is not project-owner ratification.
- The structured-child terminal-outcome policy introduced in specification
  revision `0.1.90` is `RATIFIED`. Its original commit
  `53fd43c7edceaa2fbc93bdada645cc7b79195f0e` contained no recovered evidence of
  explicit project-owner selection, so AUD001 independently compared automatic
  fail-fast propagation, hidden observed/unobserved-failure state, explicit
  policy-bearing scopes and the published lifetime-only rule. On 2026-09-08 the
  project owner explicitly approved retaining the published Core v0.1 policy:
  structured ownership waits for non-detached children and governs cleanup, but
  child failure/cancellation affects owner control flow only through explicit
  Future observation; owner error/cancellation still cancels non-detached
  children and waits for cleanup. No hidden failure-consumption state is added.
  Explicit fail-fast/supervision or aggregation remains possible future
  library/design work rather than universal `future()` behavior. This
  ratification is independent of the still-unratified D045 ownership-scope core.

Required procedure:

1. Work backwards from D045, reviewing D045-D039 first, then D020, D021-D036,
   and finally D001-D019.
2. For each decision, reconstruct the alternatives, recommendation, published
   normative result, downstream implementation, and owner-approval evidence.
3. Classify it as RATIFIED, NEEDS_USER_DECISION, SUPERSEDED, or
   PROVENANCE_UNRESOLVED. Executing or publishing a patch is not sufficient
   approval evidence.
4. Present every substantive unresolved choice to the project owner under the
   current explicit design-approval gate. Do not silently preserve, replace, or
   reopen semantics.
5. Keep D046 outside AUD001 and do not let this audit overwrite or pre-empt its
   separate review.

Next audit work:

1. Complete the separate D044 review already in progress, considering D045 only
   as an unratified dependency where their semantics interact.
2. Continue backwards through D043-D039, then D020, D021-D036 and D001-D019
   under the required procedure above.

AUD001 closes only when D001-D045, except D046, have an explicit classification,
the project owner has decided every NEEDS_USER_DECISION item, relevant
provenance is recorded durably, and all affected project ledgers are reconciled.
Any later normative correction must be a separately approved specification
change; AUD001 itself authorizes no specification or implementation change.
