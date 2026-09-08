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
- D020 and the still-unratified decisions in D039-D044 require priority review
  because no recovered evidence yet demonstrates explicit project-owner selection
  of their complete published semantics.
- D021-D036 require provenance and substance review; publication alone is not
  ratification.
- D001-D019 are expected to be predominantly project-owner decisions, but their
  approval evidence must be checked rather than inferred.

Recorded review results:

- D045 is `RATIFIED`. On 2026-09-08 the project owner explicitly approved retaining
  its task-scoped ownership core: synchronous Closure/method activations do not create
  structured scopes; returning, storing or wrapping a pending Future does not alter
  its ownership edge; distinct asynchronous child tasks own their own descendants;
  and `detach()` is the explicit operation that removes the edge. This avoids
  per-activation draining, result-shape/escape heuristics and hidden implicit
  detachment while preserving ordinary Future-returning APIs. The ratification
  confirms the already-published D045 semantics; it does not change normative
  specification or implementation and is independent of the separately ratified
  specification `0.1.90` structured-child terminal-outcome policy.
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
  ratification is independent of D045's separately ratified ownership-scope core.
- D042 is `RATIFIED`. On 2026-09-08 the project owner explicitly approved
  retaining the complete published race-safe Filesystem namespace-entry
  correction after comparative review against POSIX/Unix, Java NIO, Rust, Go,
  Python and .NET. Final Path components are selected as namespace entries
  without following final symbolic-link/reparse/other indirection and without a
  separate mutable file-kind preclassification; `replace`/`remove` may operate
  on entry kinds when the backend can provide the required atomic transition;
  unsupported atomic entry-kind/source-target combinations fail as `IOError`
  rather than being emulated through check-then-act; and `remove` remains
  non-recursive. This ratifies D042 / specification `0.1.379` only; D041's
  separate atomicity, commitment, cancellation, stable-open-File and durability
  package is independently `RATIFIED` in its effective post-D042 form below.
- D041 is `RATIFIED` in its effective form after D042. On 2026-09-08 the
  project owner explicitly approved retaining D041's still-effective
  Filesystem namespace-mutation package after comparative review against
  POSIX/Unix, Python, Java NIO, Rust, Go and Windows/.NET-style filesystem
  models: both paths remain confined to one explicit Filesystem authority;
  `replace` is one indivisible source-to-target namespace transition with no
  operation-created missing-target window and same-resource replacement is a
  no-op; `replace` is not copy-then-delete or truncate-and-write; `remove` is
  one indivisible namespace transition; already-open File capabilities keep
  their selected resource; cancellation/failure before commitment contributes
  no namespace mutation, while the committed transition is irreversible and
  cannot later be reported as failed/cancelled; implementations that cannot
  provide a determinate conforming transition fail closed; distinct Filesystem
  operations have no implicit FIFO; and live atomic visibility is explicitly
  separate from crash durability, with File `sync()` not serving as a
  namespace-durability barrier. D041's original ordinary-file-only final-entry
  restriction is `SUPERSEDED` by the independently ratified D042 /
  specification `0.1.379` race-safe namespace-entry selection and is not part
  of this ratification. This governance classification changes no normative
  specification or implementation.

Required procedure:

1. Continue backwards through the remaining unresolved decisions in D044-D039,
   then D020, D021-D036, and finally D001-D019.
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
   as a ratified dependency where their semantics interact.
2. Continue backwards through the remaining unresolved decisions in D043-D039, then D020, D021-D036 and D001-D019
   under the required procedure above.

AUD001 closes only when D001-D045, except D046, have an explicit classification,
the project owner has decided every NEEDS_USER_DECISION item, relevant
provenance is recorded durably, and all affected project ledgers are reconciled.
Any later normative correction must be a separately approved specification
change; AUD001 itself authorizes no specification or implementation change.
