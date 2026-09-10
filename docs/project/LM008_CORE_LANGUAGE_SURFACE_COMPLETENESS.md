# LM008 — Core Language Surface Completeness

Status: IN_PROGRESS

## Objective

LM008 audits whether already-normative Protos Core language surface is actually
reachable from ordinary Protos programs and backed by executable Protos
conformance evidence.

LM008 does **not** define new language behavior. It exists to catch a class of
failure that ordinary implementation-unit tests can miss:

```text
normative Core promise
        |
        v
runtime mechanism exists
        |
        X
guest-visible path is absent or untested
```

A Java/runtime test proving that an internal primitive works is not, by itself,
evidence that a Protos program can reach that primitive through the normative
language surface.

When LM008 finds a reproducible mismatch against already-closed semantics, the
production repair belongs to the proper implementation owner (`Ixxx` or another
existing owner). LM008 retains or adds ordinary-Protos regression evidence after
that repair closes. If the audit instead uncovers a genuinely unresolved semantic
choice, dependent work stops at the normal Dxxx approval gate.

## Scope

The audit follows the current normative ownership map rather than a hand-written
feature checklist:

- `spec/PROTOS_GRAMMAR.md` — lexical/syntactic surface and mandatory lowering;
- `spec/semantics/OBJECT_MODEL.md` — objects, slots, delegation, composition,
  reflection and open/closed/frozen object state;
- `spec/semantics/EXECUTION_AND_CONTROL.md` — evaluation, contexts, lookup,
  assignment, `this`, `super`, control and `ensure`;
- `spec/semantics/CALLABLES.md` — Closures, invocation, receiver binding,
  parameters/default/rest/spread and non-local return;
- `spec/semantics/VALUES_AND_COLLECTIONS.md` — canonical values, Boolean
  protocols, equality/identity/hash, numbers, strings and Core collections;
- `spec/semantics/ERRORS.md` — Error signaling, matching and dynamic handlers;
- `spec/semantics/MODULES.md` — module contexts, import and module identity.

Concurrency and system/resource domains are still part of Core, but LM008 does
not duplicate the integrated maturity work already owned by LM005 and LM006.
The final reconciliation cross-checks their retained evidence and only opens new
work when a normative guest-visible surface is not covered there.

`spec/runtime/ABSTRACT_RUNTIME.md` is informative implementation pseudocode and
can be useful for triangulation, but it is never treated as normative authority.

## What counts as a surface row

A matrix row is one programmer-observable Core capability or mandatory surface
rule. Depending on the owner, a row may be:

- a syntax form or mandatory lowering;
- an intrinsic reference;
- an ordinary standard message/protocol selector;
- a standard prelude binding/factory;
- a contextual execution rule whose effect is observable from Protos;
- an intentionally absent/forbidden surface when Core normatively requires the
  absence.

Rows should be no broader than the evidence needed to distinguish a real gap.
Related selectors may share one row only when the normative owner gives them one
inseparable contract and one executable probe can falsify the whole claim.

## Classification

Every audited row receives one of these states:

| State | Meaning |
|---|---|
| `COVERED` | Normative rule identified, guest-visible implementation path exists, and retained Protos-level conformance exercises it. |
| `RUNNABLE_UNCOVERED` | Guest-visible implementation appears present, but no retained Protos-level conformance directly proves the row. |
| `SPECIFIED_NOT_GUEST_VISIBLE` | Normative Core promises the surface, but the current reference implementation does not expose a usable Protos path. |
| `TRACKED_IMPLEMENTATION_GAP` | The mismatch is already owned by an existing open/ready implementation item; LM008 must not allocate a duplicate owner. |
| `INTENTIONAL_ABSENCE_COVERED` | Core normatively excludes or withholds a surface and retained conformance proves that absence where useful. |
| `DEFERRED_NOT_NORMATIVE` | The capability is deliberately outside current Core semantics; it is not an implementation defect. |
| `NEEDS_DESIGN_DECISION` | Independent implementation is impossible without a new semantic choice; dependent audit work stops at the Dxxx approval gate. |

`COVERED` requires executable language-level evidence. Java tests may supplement
that evidence for host/runtime mechanics but cannot substitute for it.

## Evidence hierarchy

For a positive guest-visible Core promise, preferred evidence is:

1. an ordinary `.protos` program retained by the central Test Tool corpus;
2. a specialized Protos program plus the smallest host harness necessary only to
   provision authority/scheduling that ordinary source cannot create;
3. Java/runtime tests as additional mechanism evidence.

A Java-only test is never enough to move a positive guest-visible row to
`COVERED`.

For intentional absence, a focused negative Protos program is appropriate when
the absence could regress silently (for example, a forbidden prelude binding).

## LM008-A seed findings

LM008-A establishes the audit method and records only findings already supported
by current repository state. It does not repair them.

| Surface | Normative status | Current implementation/evidence | LM008-A classification | Next owner |
|---|---|---|---|---|
| `Object.slotNames()` | Normative local-slot reflection; D032 additionally fixes fresh Array result identity. | Runtime can snapshot local slots, but current `ProtosStandardObjectProtocol` does not publish `slotNames`; the current `core-surface` corpus has no positive probe. | `SPECIFIED_NOT_GUEST_VISIBLE` | Verify/reproduce in LM008-C, then allocate/use the proper Ixxx repair owner. |
| `Object.removeSlot(name)` | Normative local-only structural mutation with semantic-String name domain. | `ProtosObjectValue.removeLocalSlot` exists, but the ordinary inherited selector is not currently published by `ProtosStandardObjectProtocol`; no positive Protos surface probe was found. | `SPECIFIED_NOT_GUEST_VISIBLE` | Verify/reproduce in LM008-C, then allocate/use the proper Ixxx repair owner. |
| `Object.close()` | Normative structural state transition. | `ProtosObjectValue.close()` and Java state tests exist, but the ordinary inherited selector is not currently published; Java primitive evidence does not prove guest reachability. | `SPECIFIED_NOT_GUEST_VISIBLE` | Verify/reproduce in LM008-C, then allocate/use the proper Ixxx repair owner. |
| `Object.freeze()` | Normative structural state transition. | `ProtosObjectValue.freeze()` and Java state tests exist, but the ordinary inherited selector is not currently published; Java primitive evidence does not prove guest reachability. | `SPECIFIED_NOT_GUEST_VISIBLE` | Verify/reproduce in LM008-C, then allocate/use the proper Ixxx repair owner. |
| required parameter after first defaulted parameter | Normatively rejected by D003 / specification `0.1.386`. | `I025` is `CLOSED` at the LM008-A publication baseline (`0.2.281-SNAPSHOT`) with parser rejection plus Protos-source syntax/conformance evidence. | `COVERED` baseline | LM008-B cross-checks retained evidence and does not duplicate I025. |
| Boolean `not()` / unary `!` / two-way conditional selection | Normative and already implemented after D050/I029. | Retained ordinary-Protos Boolean conformance exists. | `COVERED` baseline | LM008-D verifies matrix completeness only. |
| loop-local `break` / `continue` | Explicitly outside the current D044 `while` contract and left for separate design. | No current Core promise requires them. | `DEFERRED_NOT_NORMATIVE` | None under LM008. |

The four `Object` rows are deliberately kept separate: reflection, destructive
slot mutation, closing and freezing have distinct observable contracts and may
not be repaired or tested as though they were one operation.

## Slices

| Slice | State | Purpose |
|---|---|---|
| `LM008-A` | CLOSED | Establish normative-owner inventory, audit classifications, evidence rules, decomposition and seed findings from current repository state. Documentation/governance only. |
| `LM008-B` | CLOSED | Grammar/evaluation/binding/callable surface audit complete through B1-B4: lexical/literal/separator grammar; binding/writes/lowering/evaluation/object composition; Closure/invocation/parameter/default/rest/spread/trailing-Closure surface; receiver binding/extraction, `super`, non-local return and final B reconciliation. No new semantic decision or production implementation gap was found; retained Protos evidence is recorded in `LM008_B_GRAMMAR_EVALUATION_BINDING_CALLABLE_AUDIT.md`. |
| `LM008-C` | BLOCKED_BY_DEPENDENCIES | Object structural/reflection/mutation audit has confirmed four already-normative guest-publication gaps: `Object.slotNames()`, `removeSlot(name)`, structural `close()` and `freeze()`. They are tracked by I031 / GitHub #242 as separate A-D implementation slices; all other audited C rows are covered by retained Protos evidence. C resumes after I031 closure for positive regression reconciliation. |
| `LM008-D` | IN_PROGRESS | D1 canonical `null`/Boolean values, Boolean protocols and general equality/identity/hash audit is complete with retained guest-visible evidence and no production defect or design decision; D2-D4 (numeric, String and Core collections) remain. |
| `LM008-E` | READY | Audit control/errors/modules/prelude surface: `while`, `ensure`, Error signaling/handling, module contexts/import/cache-visible rules, and required/forbidden Core prelude bindings. |
| `LM008-F` | BLOCKED_BY_DEPENDENCIES | Final reconciliation: cross-check retained LM005/LM006 advanced-domain evidence, require every matrix row to have an explained state, require all discovered implementation gaps to have owners/regressions, rerun the complete retained surface corpus and close LM008 without inventing semantics. |

B-E are independent audit fronts after A. F depends on B-E and on any
implementation owners opened by their confirmed findings.

## LM008-B detailed audit record

The bounded B audit matrix and checkpoint evidence live in
`docs/project/LM008_B_GRAMMAR_EVALUATION_BINDING_CALLABLE_AUDIT.md`.
`LM008-B` is CLOSED: B1-B4 are complete with retained guest-visible
evidence, no new design decision and no production implementation finding.
Parent LM008 remains IN_PROGRESS for independent C/D/E fronts and final F
reconciliation.

## LM008-C detailed audit record

The bounded object structural/reflection/mutation audit matrix lives in
`docs/project/LM008_C_OBJECT_STRUCTURAL_REFLECTION_MUTATION_AUDIT.md`.
LM008-C has confirmed four implementation/publication gaps against
already-closed Object semantics and allocated I031 / GitHub #242 as the
single implementation owner with separate A-D slices. LM008-C is
`BLOCKED_BY_DEPENDENCIES` until I031 publishes positive ordinary-Protos
regressions; no new Dxxx/PLATxxx decision is required by this checkpoint.

## LM008-D detailed audit record

The bounded values/Core-collections audit matrix lives in
`docs/project/LM008_D_VALUES_CORE_COLLECTIONS_AUDIT.md`.
LM008-D is `IN_PROGRESS`: D1 is complete for canonical `null`/Booleans, Boolean
control protocols and general equality/identity/hash, with two focused
ordinary-Protos evidence additions and no production implementation finding or
new design decision. D2-D4 remain.

## Interaction with implementation findings

LM008 does not publish production fixes inside an LM slice.

For a confirmed `SPECIFIED_NOT_GUEST_VISIBLE` or other implementation mismatch:

1. keep the minimal reproducer outside the passing central manifest while the
   defect is open, or run it as a diagnostic that is expected to expose the gap;
2. allocate or reuse the narrow proper implementation owner;
3. implement and validate the repair there under the ordinary specification and
   adaptive-validation rules;
4. retain a focused ordinary-Protos regression when the repair is published;
5. return to the originating LM008 slice and classify the row `COVERED`.

A maturity test must never weaken its expectation to match the implementation.

## Core-surface corpus rule

The existing `protos/tests/conformance/core-surface/` directory currently proves
several intentional missing bindings. LM008 extends the concept in the opposite
direction as well: positive Core promises need positive guest-visible probes.

Folder placement alone is not the authority. A surface probe may live in the
most semantically appropriate existing conformance family, but the LM008 matrix
must point to retained executable evidence for every `COVERED` row.

## Closure criteria

LM008 may close only when:

- every in-scope normative Core surface row has been classified;
- no row remains `SPECIFIED_NOT_GUEST_VISIBLE`, `RUNNABLE_UNCOVERED`, or
  `NEEDS_DESIGN_DECISION` without an explicit owner/blocker and stated reason;
- every positive `COVERED` row has retained Protos-level executable evidence;
- intentional absences that are important to the Core surface are represented by
  negative conformance where regression would otherwise be plausible;
- every implementation defect discovered by LM008 is repaired under its proper
  owner and its minimal regression is retained before the row is closed;
- LM005/LM006 evidence has been cross-checked rather than pointlessly duplicated;
- the complete Test Tool corpus and repository-required final validation pass on
  the same publication candidate used for LM008-F closure; and
- LM008 introduces no new normative language semantics merely to complete its
  matrix.
