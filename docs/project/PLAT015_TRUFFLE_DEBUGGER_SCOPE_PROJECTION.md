# PLAT015 — Truffle debugger scope projection topology

Status: **RATIFIED**

Nature: durable non-normative Truffle tooling architecture decision

Approved by project owner: **2026-09-10**

GitHub Issue: **#270**

Primary consumer: `I026-E` under GitHub #42

Normative effect: **none**. Protos lookup, lexical-context, delegation, receiver,
module, Process, Actor, identity, mutation and concurrency semantics remain
unchanged. PLAT015 selects only how the current implementation projects an
already-existing suspended Protos activation into Truffle debugger scope APIs.

## Problem

Truffle exposes two related but distinct tooling surfaces:

- `NodeLibrary.getScope(node, frame, nodeEnter)` for execution-location-local
  scopes; and
- optional `TruffleLanguage.getScope(context)` for a frame-independent language
  top scope when the guest language genuinely has one.

Protos does not currently have one Context-wide implicit global namespace. One
Process-scoped Truffle Context may execute multiple Actors, modules and
activations, while bare-name lookup is activation-relative. Promoting one
Prelude, module, Actor, Process object, first activation or last activation into
a context-global debugger scope would therefore create platform state that does
not correspond to Protos semantics.

The durable question is how to expose useful debugger scopes without creating a
second lookup universe, a false global namespace, a named receiver that Protos
does not define, or AST-specific architecture that obstructs a future Truffle
Bytecode DSL backend.

## Existing authority

The runtime authority is `ProtosActivation` and its existing lookup order:

1. current execution Context local slots;
2. captured lexical Context local slots, nearest first; then
3. ordinary receiver lookup through the existing Protos value/delegation
   machinery.

`ProtosActivation.lookup(name)` remains the sole value-resolution authority for
bare-name reads. PLAT015 does not define another resolver.

Ratified PLAT013 remains authoritative for value projection: debugger scopes are
synthetic tooling adapters, not Protos guest values, and value inspection does
not implicitly enable mutation, Closure execution or richer interop facets.

## Cross-language Truffle review

The selected topology was explicitly reviewed against maintained or relevant
Truffle implementations, including:

- Oracle SimpleLanguage in both AST and Bytecode DSL forms;
- Apple Pkl;
- TruffleRuby;
- GraalJS;
- GraalPy, including its current Bytecode DSL `TagTreeNode` scope exports;
- FastR;
- Sulong/LLVM; and
- Espresso.

The recurring pattern is semantic adaptation rather than API-shaped invention:
Ruby, JavaScript, Python, R, LLVM and Java expose top scopes because those
languages already own corresponding top/global authorities. Their local debugger
scopes adapt real frames, bindings, lexical environments or debug metadata.
Apple Pkl demonstrates the complementary case: a Truffle language need not
manufacture `NodeLibrary`/language-top scopes simply because the APIs exist.

GraalPy and current SimpleLanguage Bytecode DSL are especially relevant to
future-proofing: their tooling entry point may be a Bytecode DSL location while
the language-level scope projection remains a separable semantic adapter. The
backend location is a bridge, not the scope authority.

## Reviewed alternatives

### A — Prelude as context-global top scope plus structured local parents

Rejected. A Process-scoped Truffle Context has no unique Prelude authority and
may host unrelated Actors/modules. This would require accidental global state,
retention and synchronization and would falsely promote one lexical environment
to Process-wide meaning.

### B — no artificial top scope plus flattened activation scope

Strong baseline: expose only a synthetic local scope for a real suspended
activation, flatten visible names in ordinary lookup precedence, and route reads
through the existing activation lookup.

### C — empty or administrative context top scope

Rejected. It satisfies an API shape without corresponding guest semantics and
creates a misleading institution solely for tooling.

### D — context top scope if a context-stable authority can be found

Deferred as a possible future additive amendment only if Protos itself later
acquires an approved frame-independent Context-wide lookup authority. Tooling is
not allowed to create that authority pre-emptively.

### B′ — activation-native debugger scope projection without artificial top scope

Selected. B′ strengthens B by making **logical Protos activation** the durable
scope authority and explicitly treating AST nodes, Truffle frames, Java threads,
CallTargets and future Bytecode DSL locations only as backend-specific bridges
to that authority.

## Selected architecture — B′

The debugger projects the exact suspended `ProtosActivation` through one bounded
synthetic read-only scope adapter. It does not create a language-global namespace
and does not reinterpret receiver delegation as lexical parentage.

Conceptually:

```text
instrumentable AST node / future Bytecode DSL location
                    |
                    v
           Truffle NodeLibrary
                    |
                    v
       debugger-scope adapter
                    |
                    v
          ProtosActivation
          /      |       \
 current Context lexical* receiver/delegation
```

The upper bridge may change with the execution backend. The lower authority and
observable tooling result remain stable.

## Durable constraints

1. **Activation is the scope authority.** A local debugger scope is defined by
   the exact suspended `ProtosActivation`, not by AST identity, Truffle
   `FrameDescriptor`, Java thread identity or a tooling-owned variable store.

2. **No artificial language top scope.** Protos does not initially override
   `TruffleLanguage.getScope(context)` with Prelude/module/Actor/Process/REPL or
   administrative state. A top scope may be added only after an independently
   approved Protos/runtime architecture provides a genuine frame-independent
   Context-wide bare-name authority.

3. **One flattened local view.** The initial scope exposes names in ordinary
   Protos bare-name precedence: current Context locals, captured lexical Context
   locals in order, then bare-readable receiver/delegation names. Shadowing is
   represented nearest-first without inventing a second resolution rule.

4. **One lookup implementation.** `readMember(name)` resolves through
   `ProtosActivation.lookup(name)`. Scope tooling may enumerate candidate names,
   but it must not independently decide which value wins.

5. **Receiver fallback stays receiver fallback.** Enumeration may include names
   reachable through the receiver/delegation chain because bare lookup can see
   them, but receiver delegation is not represented as lexical scope ancestry.

6. **No named receiver.** The initial NodeLibrary projection does not expose
   `this`, `self` or another synthetic receiver member. Protos has a receiver
   mechanism but no corresponding magic bare binding; tooling must not invent
   one.

7. **No scope-parent hierarchy initially.** `hasScopeParent/getScopeParent` is
   not used merely to visualize receiver delegation. A later additive lexical
   parent view is allowed only if it represents real Protos lexical structure
   without changing the flattened visible-name/value contract.

8. **Read-only baseline.** Scope members are observable only. PLAT015 does not
   authorize debugger write/insert/remove, expression evaluation, message send,
   Closure execution or mutation.

9. **No guest execution during enumeration.** Listing names may inspect bounded
   runtime-owned local-slot/delegation structure but must not send Protos
   messages, invoke Closures, trigger arbitrary guest code or use Java
   reflection as fallback.

10. **Suspension-bounded lifetime.** A scope adapter is created only on tooling
    request for a real suspended execution and is valid only for that suspension
    lifetime. It must not be retained as Process-, Actor-, Context- or
    language-global state.

11. **No global cache or registry.** PLAT015 introduces no activation registry,
    current/last activation slot, first-Actor binding, debugger GIL or global
    object-to-scope cache.

12. **Pay only for observation.** Ordinary execution allocates no debugger scope
    objects merely because scope support exists. Adapter allocation and optional
    name snapshots occur only when tooling asks for them.

13. **Bounded scaling.** Creating a scope is O(1) apart from any explicit
    on-demand enumeration. Name enumeration is proportional to the actually
    inspected visible lexical/delegation structure; reading one name uses the
    ordinary Protos lookup path rather than scanning a retained Process-wide
    registry.

14. **Actor/Process isolation is preserved.** Concurrent suspended work maps to
    independent activation-bound adapters. No Actor/module wins a race to become
    the Truffle Context's debugger-global namespace.

15. **Backend-independent contract.** A future Truffle Bytecode DSL backend may
    obtain the activation from a different execution representation and export
    `NodeLibrary` from backend-native locations such as `TagTreeNode`. That
    bridge may change without changing which Protos names/values the debugger
    sees.

16. **PLAT014 remains independent.** PLAT015 does not select an execution backend,
    continuation representation or migration plan. Any future backend only has
    to supply the exact suspended activation-equivalent authority required by
    this contract.

17. **DAP/LSP remain evidence gates.** I026-F/G must report actual compatibility
    evidence. If a real downstream tool proves that a useful capability requires
    a top-scope facility that Protos does not currently own, that is an explicit
    additive PLAT015 amendment or a separate semantic decision as appropriate;
    it is not permission to fabricate global state inside I026-E.

18. **No Protos semantic change.** This topology remains observational. If an
    implementation attempt would require changing bare lookup, binding,
    delegation, receiver, namespace, lifetime or concurrency semantics, I026-E
    stops and routes that question through the appropriate Dxxx/PLATxxx approval
    gate.

## Scalability assessment

The selected architecture scales with **suspended work actually inspected**, not
with total Process size:

- zero debugger-scope allocation on ordinary execution paths;
- no global lock or Context-wide activation coordination;
- one adapter can retain only its exact suspended activation for the valid
  observation window;
- unrelated Actors/modules remain independent;
- no Context-per-Actor topology is introduced; and
- large visible structures are enumerated lazily/on demand instead of eagerly
  converted into a second debugger object graph.

This preserves the project's pay-only-for-what-you-use and minimize-shared-state
principles and avoids a scaling cliff when one Process hosts many Actors or when
future hosts increase physical parallelism.

## Why this is the most Protos option

B′ preserves one conceptual universe. The debugger uses the same activation,
Context, lexical-context and receiver/delegation mechanisms that Protos already
uses rather than adding `globals`, `self`, `this`, a privileged debugger root or
a Process-global variable registry.

It therefore follows the project principles directly:

- mechanisms over institutions;
- no unnecessary privileged objects;
- ordinary structures remain ordinary;
- one semantic lookup rule rather than a tooling-specific resolver;
- pay only for observed tooling state;
- scale by composition rather than by switching to a separate global model;
- minimize shared mutable state; and
- keep Truffle/backend differences at the implementation boundary.

## I026-E implementation boundary

PLAT015 ratification releases I026-E to implement the selected bridge, but does
not itself include implementation.

I026-E must prove at minimum:

- real frame/activation acquisition at instrumentable suspension points;
- exact nearest-first visible-name ordering and shadowing behavior;
- `readMember` value equality with ordinary `ProtosActivation.lookup` results;
- receiver/delegation fallback without claiming lexical parentage;
- no named receiver and no artificial language top scope;
- read-only interop and no arbitrary guest execution while enumerating;
- suspension-bounded adapter lifetime with no global registry/cache;
- concurrent independent activation observations inside one Process-scoped
  Truffle Context; and
- retained backend-independence guards so AST machinery is not elevated into the
  durable semantic contract.

If any of these requires a new substantive semantic or durable architectural
choice outside PLAT015, the affected I026-E slice must stop at that boundary.

## Deliberately deferred

- any language-global/top-scope concept not already present in Protos semantics;
- debugger mutation or expression evaluation;
- Closure execution interop;
- richer Map/IdentityMap hash interop;
- DAP/LSP support claims before I026-F/G evidence;
- lexical parent-scope visualization beyond the flattened required view;
- Truffle ContextPolicy REUSE/SHARED; and
- PLAT014 backend/continuation selection.
