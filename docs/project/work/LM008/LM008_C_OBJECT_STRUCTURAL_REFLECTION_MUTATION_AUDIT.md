# LM008-C — Object structural, reflection and mutation surface audit

Status: BLOCKED_BY_DEPENDENCIES

Parent: `LM008 — Core Language Surface Completeness`

Durable coordination: GitHub Issue `#101`

Implementation dependency: `I031 — Standard Object reflection/mutation/state publication`
(GitHub Issue `#242`)

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

The four LM008-A seed findings remain real on the LM008-C baseline:

- `ProtosObjectValue` already has local-slot snapshot/removal and structural
  close/freeze mechanisms;
- `ProtosStandardObjectProtocol` publishes `parent`, `hasSlot`, `slotValue`,
  `without` and `alias`;
- the same standard protocol does **not** publish `slotNames`, `removeSlot`,
  structural `close`, or structural `freeze`; and
- no retained positive ordinary-Protos conformance can exist for those four
  selectors while that publication path is absent.

These are implementation/publication defects against already-closed semantics,
not unresolved semantic choices. They are therefore owned by I031 rather than by
LM008-C itself.

I031 deliberately keeps the four contracts separate:

- `I031-A` — `Object.slotNames()`;
- `I031-B` — `Object.removeSlot(name)`;
- `I031-C` — structural `Object.close()`;
- `I031-D` — structural `Object.freeze()`.

No Dxxx/PLATxxx decision is required by this checkpoint.

## Evidence matrix

| Surface row | Normative requirement | Current guest-visible / retained Protos evidence | Classification | Owner / next action |
|---|---|---|---|---|
| `Object.parent()` | Return the receiver's delegation parent under the normative root/error taxonomy | `reflection/parent-ordinary.protos`, `parent-singletons.protos`, `parent-number-string.protos`, `root-parent.protos`, plus arity/error probes | `COVERED` | none |
| `Object.hasSlot(name)` | Semantic-String name; local slots only; no delegation | `reflection/has-slot-local-only.protos`, `has-slot-invalid-name.protos`, `has-slot-represented-values.protos`, arity coverage | `COVERED` | none |
| `Object.slotValue(name)` | Semantic-String name; exact local value; local-only lookup; missing local slot signals the defined error | `reflection/slot-value-exact-local-identity.protos`, `slot-value-does-not-delegate.protos`, `slot-value-invalid-name.protos`, represented-absence coverage | `COVERED` | none |
| Object construction / parent / composition structural result | Object-body composition copies the already-defined local structural view and obeys conflict/reservation rules; parent construction remains the B2 contract | Existing `object/composition-*` conformance and LM008-B2 `core-surface/object-parent-composition-evaluation-order.protos` | `COVERED` | cross-check only; no C duplicate |
| `Object.without(name)` | Semantic-String local-only structural view; fresh open ordinary result; exact shallow values; receiver unchanged | `object-structural/without-does-not-delegate.protos`, `without-local-view-is-fresh.protos`; I030 closure evidence | `COVERED` | none |
| `Object.alias(sourceName, aliasName)` | Semantic-String local-only source/collision selection; fresh open ordinary result; exact shallow values; receiver unchanged | `object-structural/alias-publishes-symbolic-selector.protos`, `alias-rejects-local-conflicts.protos`; LM007 consumes ordinary alias publication | `COVERED` | none |
| `Object.slotNames()` | Local slots only; deterministic Unicode-scalar lexical order; fresh independent standard Array on every successful call, including empty/repeated calls (D032) | Runtime snapshot mechanism exists, but no inherited guest selector is published | `TRACKED_IMPLEMENTATION_GAP` | `I031-A`; retain focused positive Protos conformance there |
| `Object.removeSlot(name)` | Semantic-String name; local-only destructive structural removal; never delegates; exact removed local value result; state restrictions apply (D033) | Runtime `removeLocalSlot` mechanism exists, but no inherited guest selector is published | `TRACKED_IMPLEMENTATION_GAP` | `I031-B`; retain focused positive/error Protos conformance there |
| structural `Object.close()` | Shallow structural OPEN→CLOSED transition; idempotent; exact receiver result; later structural add/remove forbidden while permitted non-frozen assignments remain governed normally | Runtime `close()` and Java state mechanism evidence exist, but no inherited structural guest selector is published | `TRACKED_IMPLEMENTATION_GAP` | `I031-C`; retain transition/result/mutation Protos conformance there |
| structural `Object.freeze()` | Shallow structural freeze; idempotent; exact receiver result; later structural and value mutation forbidden as specified | Runtime `freeze()` and Java state mechanism evidence exist, but no inherited structural guest selector is published | `TRACKED_IMPLEMENTATION_GAP` | `I031-D`; retain transition/result/mutation Protos conformance there |
| Open/closed/frozen observable state behavior | Structural mutation and assignment permissions must follow the receiver's state transitions | Internal state machinery exists; positive guest-level transition coverage is blocked specifically by missing `close()` / `freeze()` publication | `TRACKED_IMPLEMENTATION_GAP` | reconciled by `I031-C` / `I031-D` evidence |
| Semantic-String reflection/mutation name domain | `hasSlot`, `slotValue`, `removeSlot` share the already-ratified semantic String-name domain, not host-string identity | Retained invalid-name Protos coverage exists for `hasSlot`/`slotValue`; `removeSlot` cannot yet be reached | mixed: covered except tracked `removeSlot` row | `I031-B` closes remaining guest path |

## Why there is no failing conformance expectation

LM008-C does not add a passing manifest case that expects `SlotNotFound` from
`slotNames`, `removeSlot`, `close`, or `freeze`. That would encode the current
defect as accepted behavior and weaken the maturity expectation to match the
implementation.

The minimal ordinary-Protos reproducers are mechanically obvious:

```text
o: { a: 1 }
o.slotNames()
```

```text
o: { a: 1 }
o.removeSlot("a")
```

```text
o: { a: 1 }
o.close()
```

```text
o: { a: 1 }
o.freeze()
```

On the audited implementation these cannot resolve the promised standard selector
through ordinary Object protocol lookup. I031 owns the repair and the positive
retained conformance that will make those same guest-visible paths executable.

## Dependency and resumption

LM008-C is `BLOCKED_BY_DEPENDENCIES` on I031.

C may resume after I031 has published A-D (and any needed final reconciliation)
with ordinary-Protos regressions. LM008-C then reclassifies the four tracked rows
to `COVERED`, reruns the relevant retained object/reflection/structural corpus, and
closes only if no other C row remains unresolved.

Parent LM008 remains `IN_PROGRESS`; LM008-D and LM008-E remain independent READY
fronts, and LM008-F remains dependency-gated.

## Checkpoint result

- confirmed implementation gaps: **4**;
- implementation owner allocated: **I031 / GitHub #242**;
- new semantic decisions: **0**;
- new platform decisions: **0**;
- specification change: **none**;
- production implementation change: **none**;
- implementation version change: **none**;
- `CHANGELOG.md`: **untouched** because LM008-C is not closed.
