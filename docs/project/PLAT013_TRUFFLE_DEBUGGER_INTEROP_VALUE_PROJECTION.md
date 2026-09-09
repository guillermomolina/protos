# PLAT013 — Truffle debugger/interop value projection architecture

Status: **RATIFIED**

Nature: durable non-normative Truffle/JVM tooling architecture decision

Approved by project owner: **2026-09-09**

GitHub Issue: **#250**

Primary consumer: `I026-D` / GitHub #90

Normative effect: **none**. Protos object, slot, lookup, reflection, identity,
equality, hashing, Closure extraction/invocation, mutation and concurrency
semantics remain defined by the normative specification and existing ratified
language decisions. PLAT013 selects only how the Truffle implementation projects
already-existing runtime values to tooling through interop.

## Identifier reconciliation

This checkpoint was initially allocated as `PLAT011` while concurrent workstreams
were also allocating platform decisions. Before this debugger/interop decision
was ratified or implemented, `main` durably assigned:

- `PLAT011` to the RuntimeHost-owned shared Actor carrier substrate; and
- `PLAT012` to verified external package custody/source resolution.

The debugger/interop checkpoint is therefore collision-safely reallocated to the
next free identifier, **PLAT013**, retaining GitHub #250 as the same decision
history. No debugger/interop architecture was ever durably published as PLAT011.

## Problem

I026-D must make Protos runtime values useful to Truffle debugger/tooling without
creating a second semantic object model.

The current Protos runtime already owns the relevant authorities:

- `ProtosObjectValue` owns local slots, delegation and mutation state;
- Core distinguishes local-only reflection from delegated lookup;
- Closure-valued delegated/member access can create receiver/methodHome
  extraction metadata;
- Protos identity/equality/hash are language semantics and are not defined by
  Java or Truffle identity.

A debugger expansion is therefore not allowed to implement a structural read by
performing ordinary delegated lookup, invoking a Closure, reflecting Java fields,
or fabricating a tooling-specific guest value.

## Reviewed architecture families

### A — intrinsic interop everywhere

Ordinary runtime values directly export all plausible interop facets.

This has good allocation and identity properties, but interpreted broadly it
mixes guest values with scope-only institutions and risks prematurely exporting
invocation, mutation, hash and other protocols that I026-D does not need.

### B — universal tooling wrapper values

Every inspected value is wrapped in a `ProtosDebugValue`-style projection.

This creates a second value universe, wrapper identity/forwarding obligations,
cache/lifetime/cycle questions and allocation proportional to debugger traversal.
It also risks leaking wrapper semantics into nested values.

### C′ — semantic-value-native interop plus bounded synthetic/contextual adapters

Real Protos values are the authoritative interop receivers for facets that
faithfully describe those values. Synthetic debugger institutions such as local
or top scopes, member-name containers, and genuinely contextual views remain
separate bounded adapters and never become Protos guest values.

This is the selected architecture.

### D — display-only values

Opaque display is safe but too weak as I026-D's durable endpoint and would merely
postpone the same architecture decision until scope/DAP work.

## Cross-language and runtime review

The approved review contrasted the design with maintained or relevant Truffle
languages/runtimes including:

- Oracle SimpleLanguage;
- TruffleRuby;
- GraalJS;
- GraalPy;
- FastR;
- Espresso;
- Sulong;
- GraalWasm;
- TruffleSqueak;
- SOMns;
- Enso; and
- Apple Pkl.

The strongest reusable pattern is not "everything is a wrapper" and not "every
runtime institution is a guest object". Mature dynamic languages commonly let
real guest values provide their faithful interop contract while representing
synthetic scopes or tooling-only views separately.

Sulong is an important boundary case: dedicated debugger values are appropriate
when the debugger must construct a source-level representation that is not
already the language's ordinary runtime value. PLAT013 keeps that escape hatch
for a genuine representation boundary without making it the default architecture.

Apple Pkl reinforces a complementary constraint: do not expose more evaluator
internals than the tooling contract requires. PLAT013 therefore selects direct
interop only for faithful semantic facets, not indiscriminate exposure of the
runtime graph.

## Selected architecture — C′

Select **semantic-value-native, side-effect-free minimum interop on real Protos
runtime values, with synthetic/contextual adapters only at genuine non-value
boundaries**.

The initial I026-D implementation is deliberately read-only and additive.

## Durable constraints

1. **One runtime truth.** Real Protos runtime values are the authoritative
   receivers for their faithful general interop facets. No universal
   `ProtosDebugValue` wrapper layer is introduced.

2. **Synthetic things stay synthetic.** Local scopes, top scopes and other
   debugger-only containers are separate adapters owned by I026-E. They are not
   Protos guest values and never participate in guest lookup, identity or
   mutation semantics.

3. **Object members are local reflection only.** For ordinary
   `ProtosObjectValue`, interop member enumeration/read projects the current
   local-slot structure from the same authority used by Core local reflection.

4. **No delegated read through interop.** Object `readMember` must not perform
   ordinary delegated lookup, message send, Closure invocation, or
   receiver/methodHome extraction.

5. **No host leakage.** Java fields, Java superclass members, Truffle nodes,
   scheduler state, host handles and implementation sentinels are never exposed
   as guest members.

6. **Read-only I026-D baseline.** I026-D does not add interop member insertion,
   write/removal, array mutation, hash mutation, debugger expression evaluation
   or other mutation authority. Any later debugger-write capability requires its
   own evidence and, if durably architectural, an explicit decision.

7. **Arrays may expose faithful indexed structure.** An array-like semantic
   value may expose read-only array-element interop directly when backed by its
   exact semantic indexed state. Internal capacity/storage remains invisible.

8. **Map/IdentityMap hash interop is initially deferred.** The first D tranche
   does not force hash-entry interop. It may be added only after a focused audit
   demonstrates an exact side-effect-free projection preserving each map
   family's key/equality rules without invoking arbitrary guest behavior.

9. **Closure execution is initially deferred.** Inspection of a Closure does not
   create a new `InteropLibrary.execute`/foreign-call path. Safe display/source
   metadata may be exposed, but invocation/argument/result conversion is a
   separate capability.

10. **Primitive/value facets must be faithful.** Numbers, Strings, Booleans and
    null may expose their corresponding standard interop facets only where that
    projection is exact and side-effect-free.

11. **Truffle identity is not Protos identity authority.** Interop identity must
    never redefine or substitute for Protos `===`, equality or hashing.
    `ProtosIdentity` and the normative language rules remain authoritative.

12. **Display is pure and bounded.** `toDisplayString` must not call arbitrary
    Protos methods, traverse an unbounded/cyclic guest graph or expose host
    internals. Richer pretty-printing may be layered later.

13. **Unsupported richer facets fail closed.** They appear absent/unsupported
    rather than being synthesized through Java reflection or arbitrary guest
    invocation.

14. **No global registry or wrapper cache.** PLAT013 introduces no global mutable
    interop registry, object-to-wrapper cache or cross-Context mutable authority.

15. **Pay only for inspected structure.** Ordinary execution allocates no
    debugger wrapper graph merely because interop support exists. Debugger
    expansion is lazy and proportional to the structure actually inspected.

16. **Cycles require no parallel identity graph.** A local slot that refers back
    to the same object returns that same guest value. PLAT013 does not require
    graph memoization or cycle-detection solely to preserve debugger identity.

17. **Context/Process independence is preserved.** Interop does not create a
    cross-Context identity registry, synchronization domain or Process-global
    tooling authority. Existing runtime ownership/isolation rules remain
    unchanged.

18. **Tooling does not add guest concurrency semantics.** PLAT013 does not
    authorize otherwise-invalid cross-thread guest access or add a debugger GIL.
    I026-D must compose with existing Truffle/runtime observation and lifecycle
    rules without changing Protos-visible concurrency behavior.

19. **Contextual views are adapters, not replacement values.** If a later
    location/scope-specific filtered view is required, the Truffle contextual
    view mechanism may be used without replacing the base guest-value
    architecture.

20. **Representation independence.** A future Truffle Bytecode DSL backend or
    non-JVM implementation may realize equivalent tooling projections using
    different classes/data structures. Java annotations/classes are machinery,
    not the durable contract.

## Baseline family matrix

| Runtime family | I026-D baseline |
|---|---|
| ordinary Object | direct read-only local members; no delegated lookup |
| Array-like value | direct read-only indexed elements when exact |
| String / Number / Boolean / null | faithful primitive/value facets |
| Closure | safe value/display metadata; executable interop deferred |
| Map / IdentityMap | ordinary safe value/display only initially; hash-entry interop deferred |
| Error / Future / Actor / resource values | expose only facets proven faithful and side-effect-free; no host internals |
| local/top debugger scope | synthetic adapter in I026-E, not a guest value |
| member-name/container helper | bounded tooling adapter allowed; not semantic identity |

This table is intentionally a minimum, not a permanent prohibition on richer
interop. Additional facets are additive only after proving they preserve the
same semantic boundaries.

## Scalability

The selected architecture has no per-value wrapper allocation in ordinary
execution and no mandatory global cache.

For a debugger-expanded object with `n` local slots:

- member enumeration is O(n) in the local structure requested;
- reading one member does not require traversing the reachable object graph;
- nested values remain their existing guest values;
- cycles do not require wrapper memoization;
- Process/Context count does not multiply a global interop registry.

Large arrays preserve indexed access rather than requiring eager conversion to a
debugger collection. Large object graphs are expanded lazily by the tooling
consumer.

The architecture therefore scales with **observed structure**, not with total
reachable heap size.

## Bytecode DSL and backend evolution

PLAT013 deliberately separates **value projection** from **execution-location /
frame / scope acquisition**.

A future migration from the current AST implementation to Truffle Bytecode DSL
may change nodes, locations, frames, continuations and I026-E scope machinery
without requiring a second Protos value representation. Real Protos values can
continue to provide the same faithful interop facets.

Likewise a future non-JVM implementation is free to use another debugger
protocol internally as long as it preserves Protos semantics; PLAT013 is
non-normative platform architecture, not a language requirement.

## Why this is the Protos choice

The selected design best preserves the project principles:

- **one small universe:** no parallel debugger-value ontology;
- **ordinary things remain ordinary:** real Protos values remain the values;
- **mechanisms over institutions:** interop is an observation mechanism, not a
  new guest institution;
- **semantic distinctions stay visible:** local reflection, delegated lookup,
  Closure extraction and invocation remain distinct;
- **pay only for what you use:** no eager wrapper graph or global registry;
- **minimal shared mutable state:** no cross-Context cache/identity authority;
- **scale by composition:** richer facets can be added independently without
  replacing the baseline architecture; and
- **platform differences stay at the boundary:** Truffle machinery does not
  redefine Protos semantics.

## Explicitly deferred

PLAT013 does **not** decide:

- debugger mutation/write protocols;
- debugger expression evaluation;
- Map/IdentityMap hash-entry interop until the focused side-effect/equality audit;
- Closure executable/foreign-call interop;
- rich pretty-printing;
- metaobject/source-location surfaces beyond what I026-D proves necessary;
- I026-E scope topology details;
- DAP/LSP compatibility claims, which remain I026-F/G evidence gates;
- `ContextPolicy.REUSE` / `SHARED`; or
- any Protos-visible semantics.

If implementation of I026-D exposes a new substantive semantic or durable
architecture choice outside these approved bounds, that slice must stop at the
decision boundary and allocate/use the appropriate Dxxx/PLATxxx process.

## Consequence for I026

With PLAT013 ratified, I026-D is **READY** to implement the read-only
semantic-minimum interop slice under the constraints above.

I026-E remains blocked on I026-C + I026-D. I026-F/G remain their existing
compatibility evidence gates.

No I026-D implementation is included in this ratification.