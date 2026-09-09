# I026-A4B2B3 — Concurrent Core bootstrap closure

Status: **IN_PROGRESS**

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
- **I026-A4B2B3B — A+ executable projection + final concurrency closure:** READY.
  Move sharing-layer-bound execution plans for the globally shared source-backed
  root Closures into the current `ProtosLanguageContext`, retain EXCLUSIVE, prove
  distinct Process Contexts never execute one another's CallTarget, and publish
  the final concurrent multi-Process evidence. Only B may close B010, B2B3,
  A4B2B and A4B2 and release A4B3.

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

B010 remains READY because its recorded exit condition intentionally includes the
A+ multi-Process executable-layer evidence owned by B. `I026-A4B3` therefore
remains dependency-blocked after A.
