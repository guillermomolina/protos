# D049 — Shared standard-object publication and root immutability

Status: **RATIFIED**
Specification revision: **`0.1.389`**
Explicit project-owner approval: **2026-09-09**
Nature: normative object/isolation design decision resolving `B010`
Primary implementation consumer: `I026-A4B2B3`

## Decision

Protos selects the **shared frozen standard graph** model for standard objects that cross Actor isolation through the shared prelude. A standard object whose ordinary structural state is observable may be physically shared only after construction is complete and the object itself is frozen.

The unique root `Object` is therefore published `FROZEN` before any guest code can observe it. The rule is not an identity-based runtime exception: it is the general consequence of combining the existing shared-prelude immutability invariant with the ordinary open/closed/frozen object model.

A physically shared standard Closure is an object for this purpose. Its Protos-visible slots/structural state are frozen independently of the object that stores it. D049 does not deep-freeze the object graph and does not prohibit semantically invisible implementation caches or executable metadata.

## Rejected alternatives

- **JVM-global mutable root plus synchronization:** race freedom is not Actor isolation; writes would still cross semantic heaps.
- **One mutable root per hosted Process:** multiple Actors of one Process would still observe shared mutable Protos state.
- **One mutable root/Core graph per Actor:** preserves mutation but turns a bounded standard graph into per-Actor state and scales poorly for large Actor counts.
- **Actor-local copy-on-write/overlay root:** preserves apparent monkey-patching only by adding hidden lookup/state/identity machinery and an additional mutation universe.

## Scaling result

The semantic standard graph remains O(1) with Actor count. Mutable application state remains Actor-local. Under the separately ratified A+ Truffle architecture, executable material that is sharing-layer-bound remains owned by the active `ProtosLanguageContext`, so executable cost scales with hosted Process Contexts rather than Actors and no global guest-execution lock is introduced.

## Observable consequences

From first guest-observable access, standard `Object` is frozen. Root slot creation, assignment, composition contribution and removal therefore fail through the existing frozen-object rules. Existing idempotent structural `close()` / `freeze()` behavior remains unchanged. Child prototypes, composition into program-owned objects, local prelude shadowing and ordinary Actor-local mutation remain available.

## Intentionally not introduced

D049 introduces no deep-freeze operation, Realm/isolate object model, owner-Actor mutation privilege, root overlay, new Error family, new syntax, new value kind, new identity rule, new shared-memory model, or new Truffle context policy.

## Implementation handoff

D049 satisfies B010's normative unblock condition. I026-A4B2B3 is now mechanically split into A/B implementation slices: A is CLOSED with one-time frozen Core publication and shared-standard graph sealing; B is READY for the already-approved A+ `ProtosLanguageContext` executable projection and final concurrent multi-Process evidence. B010 deliberately remains READY until B closes the complete B2B3 obligation, after which A4B2B/A4B2 may close and A4B3 may become READY.
