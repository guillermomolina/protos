# GITHUB013 — Concurrent formal identifier allocation and uniqueness guard

Status: **IN PROGRESS**

Owning live Issue: GitHub #471.

This record hardens project coordination only. It changes no Protos semantics.

## Incident

Concurrent Issue creation exposed a real allocation race:

- #450 owns AUD004;
- #451 was the first GitHub reservation of AUD005;
- #452 independently also reserved AUD005 moments later;
- #453 already owns AUD006.

The live reconciliation preserves the earlier valid reservations and reallocates
#452 to **AUD007**. Historical body/comment references may record the creation-time
AUD005 name, but all new durable work for #452 must use AUD007.

## Durable rule

Top-level family numbers remain monotonic consumed identifiers. Closed,
cancelled, superseded and historical allocations are not a reusable pool.

The existing allocation protocol remains:

1. inspect durable repository allocation state and relevant GitHub Issues;
2. choose the next number after the greatest allocated top-level family number;
3. create the Issue immediately;
4. recheck the candidate after creation;
5. when concurrent Issues collide, the lower GitHub Issue number keeps the
   GitHub reservation unless durable repository authority already owns the
   identifier; every later Issue must reallocate before publication.

A read-then-create client sequence is not atomic. GITHUB013 therefore adds a
second fail-closed machine gate to `scripts/issue_intake.py`: before one-Issue or
full reconciliation, it enumerates authorized formal Issue titles across both
open and closed Issues and rejects any exact duplicate identifier. Exact children
remain distinct identities (`TEST001` != `TEST001-A`).

This GitHub-wide scan is intentionally not an auto-number allocator. Repository
durable allocation can contain authority that a GitHub-only scan cannot infer,
so automatic renumbering would risk replacing one race with another. The machine
guard detects and blocks; explicit reconciliation chooses the correct new number.

## Validation

The network-free intake self-test retains evidence that:

- exact top-level duplicates fail closed;
- parent and formal child identifiers do not falsely collide;
- a closed Issue still consumes its identifier;
- the lower GitHub Issue number is reported as the current GitHub owner for an
  accidental duplicate.

## Closure gate

GITHUB013 can close after publication, a live full intake reconciliation reports
no identifier collision, and repository/GitHub duplicate review finds no other
unresolved formal identifier conflict.
