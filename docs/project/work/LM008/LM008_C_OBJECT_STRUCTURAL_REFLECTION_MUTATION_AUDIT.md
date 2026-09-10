# LM008-C — Object structural, reflection and mutation surface audit

Status: CLOSED

Parent: `LM008 — Core Language Surface Completeness`

Durable coordination: GitHub Issue `#101`

Implementation dependency: `I031 — Standard Object reflection/mutation/state publication`
(GitHub Issue `#242`) — CLOSED by `5c6eee9f90b2e5dc8542bd616c02c8f1d728cd20`

Nature: non-normative audit/evidence record

## Scope

LM008-C audits the already-normative Core object surface from
`spec/semantics/OBJECT_MODEL.md` through the current guest-visible implementation
and retained ordinary-Protos conformance.

This audit does not define object behavior. A runtime primitive or Java test is
only mechanism evidence; a positive Core surface row is `COVERED` only when an
ordinary Protos program can reach it and retained Protos-level evidence proves the
observable contract.

Object construction/composition syntax and its evaluation order were already
audited by LM008-B2. C cross-checks the resulting object-structural behavior rather
than duplicating that grammar audit.

## Revalidation result

The original four LM008-C findings are repaired and revalidated on the closure
baseline:

- `ProtosStandardObjectProtocol` now publishes the inherited `slotNames`,
  `removeSlot`, structural `close`, and structural `freeze` selectors in addition
  to the already-covered `parent`, `hasSlot`, `slotValue`, `without`, and `alias`;
- I031 published the four contracts independently as A `d50ab69f`, B `6b5d029f`,
  C `c9677779`, and D `42f6e0b9`, with final durable reconciliation in
  `5c6eee9f90b2e5dc8542bd616c02c8f1d728cd20`;
- the central conformance manifest retains positive/error ordinary-Protos probes
  for each of the four formerly missing selectors and for their state/result
  boundaries; and
- the remaining rows that were already `COVERED` at the checkpoint remain
  guest-visible and retain their earlier evidence.

No unresolved C row remains. The I031 work repaired implementation/publication
against already-closed semantics; this LM008-C closure selects no new Dxxx or
PLATxxx decision and changes no normative specification.

## Evidence matrix

| Surface row | Normative requirement | Current guest-visible / retained Protos evidence | Classification | Owner / next action |
|---|---|---|---|---|
| `Object.parent()` | Return the receiver's delegation parent under the normative root/error taxonomy | `reflection/parent-ordinary.protos`, `parent-singletons.protos`, `parent-number-string.protos`, `root-parent.protos`, plus arity/error probes | `COVERED` | none |
| `Object.hasSlot(name)` | Semantic-String name; local slots only; no delegation | `reflection/has-slot-local-only.protos`, `has-slot-invalid-name.protos`, `has-slot-represented-values.protos`, arity coverage | `COVERED` | none |
| `Object.slotValue(name)` | Semantic-String name; exact local value; local-only lookup; missing local slot signals the defined error | `reflection/slot-value-exact-local-identity.protos`, `slot-value-does-not-delegate.protos`, `slot-value-invalid-name.protos`, represented-absence coverage | `COVERED` | none |
| Object construction / parent / composition structural result | Object-body composition copies the already-defined local structural view and obeys conflict/reservation rules; parent construction remains the B2 contract | Existing `object/composition-*` conformance and LM008-B2 `core-surface/object-parent-composition-evaluation-order.protos` | `COVERED` | cross-check only; no C duplicate |
| `Object.without(name)` | Semantic-String local-only structural view; fresh open ordinary result; exact shallow values; receiver unchanged | `object-structural/without-does-not-delegate.protos`, `without-local-view-is-fresh.protos`; I030 closure evidence | `COVERED` | none |
| `Object.alias(sourceName, aliasName)` | Semantic-String local-only source/collision selection; fresh open ordinary result; exact shallow values; receiver unchanged | `object-structural/alias-publishes-symbolic-selector.protos`, `alias-rejects-local-conflicts.protos`; LM007 consumes ordinary alias publication | `COVERED` | none |
| `Object.slotNames()` | Local slots only; deterministic Unicode-scalar lexical order; fresh independent standard Array on every successful call, including empty/repeated calls (D032) | I031-A `d50ab69f`; retained `reflection/slot-names-local-order.protos`, `slot-names-unicode-scalar-order.protos`, `slot-names-fresh-independent-snapshot.protos`, empty/represented/arity probes | `COVERED` | I031-A CLOSED |
| `Object.removeSlot(name)` | Semantic-String name; local-only destructive structural removal; never delegates; exact removed local value result; state restrictions apply (D033) | I031-B `6b5d029f`; retained exact-result/delegated-only/invalid-name/frozen/represented-receiver/arity probes under `reflection/remove-slot-*` | `COVERED` | I031-B CLOSED |
| structural `Object.close()` | Shallow structural OPEN→CLOSED transition; idempotent; exact receiver result; later structural add/remove forbidden while permitted non-frozen assignments remain governed normally | I031-C `c9677779`; retained exact-receiver/idempotence, creation/removal rejection, existing-assignment, shallow, frozen-root, Array and arity probes under `reflection/close-*` | `COVERED` | I031-C CLOSED; BUG004 compatibility repair closed in same slice |
| structural `Object.freeze()` | Shallow structural freeze; idempotent; exact receiver result; later structural and value mutation forbidden as specified | I031-D `42f6e0b9`; retained exact-receiver/idempotence, CLOSED→FROZEN, creation/removal/assignment rejection, shallow, root, Array/Map and arity probes under `reflection/freeze-*` | `COVERED` | I031-D CLOSED |
| Open/closed/frozen observable state behavior | Structural mutation and assignment permissions must follow the receiver's state transitions | Combined retained `reflection/close-*` and `reflection/freeze-*` guest probes plus existing Object/Array/Map mechanism tests | `COVERED` | none |
| Semantic-String reflection/mutation name domain | `hasSlot`, `slotValue`, `removeSlot` share the already-ratified semantic String-name domain, not host-string identity | Retained invalid-name ordinary-Protos coverage for all three paths, including I031-B `remove-slot-invalid-name-preserves-local.protos` | `COVERED` | none |
## Repair and positive-evidence reconciliation

The checkpoint deliberately did not add passing tests that expected the four
missing selectors to fail. I031 instead published each already-normative selector
through the existing Object protocol bridge and retained ordinary-Protos
regressions for its observable contract. That preserves the audit invariant that
maturity expectations follow the specification rather than accepting a temporary
implementation defect.

I031-C additionally exposed and repaired BUG004: resource-owning Text/Buffered
I/O guards had treated any inherited `close` as resource lifecycle authority.
The repair preserves the normative distinction between structural `Object.close`
and the nearer/local I/O `Closable.close` capability; this compatibility finding
is therefore part of the positive closure evidence for the structural selector,
not a new LM008-C semantic rule.

## Closure reconciliation

LM008-C is no longer dependency-blocked. I031/#242 is CLOSED and the four former
`TRACKED_IMPLEMENTATION_GAP` rows are now `COVERED` with retained language-level
evidence. The closure publication reruns the central Test Tool corpus together
with focused Object/Array/Map and native-boundary mechanism guards before this
record may be marked complete.

Parent LM008 remains `IN_PROGRESS`. LM008-D and LM008-E keep their independent
audit ownership; LM008-F remains dependency-gated on the unfinished fronts. This
closure merely releases the I031 dependency and does not pre-classify any LM008-D
or LM008-E row.

## Checkpoint result

- unresolved implementation gaps in LM008-C: **0**;
- repaired implementation owner: **I031 / GitHub #242 — CLOSED**;
- I031 implementation evidence: **A `d50ab69f`; B `6b5d029f`; C `c9677779`; D `42f6e0b9`; E `5c6eee9f`**;
- LM008-C classification: **CLOSED**;
- new semantic decisions: **0**;
- new platform decisions: **0**;
- specification change: **none**;
- production implementation change in this closure slice: **none**;
- implementation version change in this closure slice: **none**;
- native-boundary change in this closure slice: **none**;
- `CHANGELOG.md`: **updated with the LM008-C closure record**.

<!-- LM008-C closure reconciliation -->
