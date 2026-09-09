# I026-A4B2B3 — Concurrent Core bootstrap closure

Status: **CLOSED in `0.2.280-SNAPSHOT`**

Normative dependency: D049 / specification `0.1.389` — RATIFIED
Platform dependency: PLAT001 A+ — RATIFIED

## Mechanical implementation decomposition

The already-approved B2B3 outcome is implemented through two ordered mechanical
slices. This split introduces no new semantic or platform decision.

- **I026-A4B2B3A — frozen Core publication:** CLOSED in `0.2.277-SNAPSHOT`. Establish one
  narrow synchronized publication cutover for the JVM-global standard `Object`
  root, install every root-owned standard selector before that cutover, freeze
  every published root Closure, freeze the root itself, and seal the per-bootstrap
  standard graph (including semantic Closure captures) before the Prelude is
  exposed. Concurrent bootstraps reuse and validate the already-published required root surface;
  ordinary guest execution acquires no global lock.
- **I026-A4B2B3B — A+ executable projection + final concurrency closure:** CLOSED in `0.2.280-SNAPSHOT`.
  Globally shared source-backed root Closures keep one semantic/template identity,
  while each entered `ProtosLanguageContext` owns a bounded projection cache keyed
  by that template and therefore owns distinct language-bound execution plans and
  CallTargets. EXCLUSIVE remains active; B010/B2B3/A4B2B/A4B2 are CLOSED and A4B3
  is READY.

## A — publication boundary

The root publication critical section contains only root construction/publication:
`Object` native behavior, source-backed `init`/`==`/`!=`, default hash,
`Object.future`, `Object.parallel`, validation, graph sealing and the final frozen
state. It does not serialize the remainder of Core construction and is never used
for normal guest invocation.

All root selectors are complete before `Object` becomes frozen. A later bootstrap
that enters the publication boundary after another bootstrap has won the race
validates the exact frozen surface and returns without attempting mutation.

After each bootstrap builds its own standard prototypes, an implementation-only
graph walk freezes the objects that bootstrap is about to share. The walk follows
ordinary local-slot object edges and the semantic capture edges of standard
Closures. It is not a recursive Protos `freeze()` operation, exposes no new guest
message and does not change D049's shallow-freeze semantics.

## Evidence

Focused conformance includes:

- ordinary Protos source attempting both `Object._d049Probe: 1` and
  `Integer._d049Probe: 1` after bootstrap and receiving the existing standard
  Error with no slot created;
- complete standard-graph traversal proving every published object and semantic
  Closure capture is frozen, plus migration of all six identified legacy lookup/
  receiver-binding probes that previously mutated shared numeric prototypes;
- concurrent Core bootstraps proving every result observes the exact same
  complete frozen root; and
- the Test Tool whole-corpus owner reporting exact failed CaseIds rather than
  collapsing any remaining corpus incompatibility into one opaque Boolean; and
- the retained native-boundary architecture guard proving no B2B3A-owned standard
  native selector/provider expansion.

## B — A+ executable ownership boundary

The three globally shared source-backed root behaviors (`init`, `==`, `!=`) are
marked with implementation-only projection metadata before root publication. The
shared Closure keeps its exact semantic identity, canonical definition, captures and
template plan. A bound method keeps the same projection requirement and template key,
so cache cardinality is bounded by shared behavior rather than lookup/receiver count.

When such a Closure is invoked inside an entered Polyglot Process Context, the current
`ProtosLanguageContext` uses a per-Context concurrent cache to rebuild a fresh
language-bound execution plan from the shared canonical definition. Source provenance
is retained when the template has one. No projected plan is written back to the shared
Closure and no static Context-to-plan registry exists. When there is no entered Protos
Context, the staged direct template remains usable until A4B3 retires that primary path.

Focused multi-Process evidence executes the exact same global source-backed `Object.!=`
behavior simultaneously from two distinct Process Contexts sharing one Engine. Both
executions reach a blocking guest-dispatch probe before either is released, excluding a
global guest lock around the source-backed invocation. Each Context retains exactly one
projection for the shared template used by the test; the projected plans, parameter
binding CallTargets and body CallTargets are pairwise distinct and owned by the
corresponding EXCLUSIVE `ProtosLanguage` instance, while the shared template remains
unchanged.

B010 is CLOSED by this final evidence. `I026-A4B2B3`, `I026-A4B2B` and `I026-A4B2`
are CLOSED in `0.2.280-SNAPSHOT`; `I026-A4B3` is READY.
