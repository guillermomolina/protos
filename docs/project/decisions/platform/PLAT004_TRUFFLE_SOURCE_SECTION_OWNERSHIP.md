# PLAT004 — Truffle SourceSection ownership and materialization

Status: **RATIFIED**

Nature: durable non-normative Truffle runtime/tooling architecture decision

Approved by project owner: **2026-09-09**

Primary consumers: `I026-B`, then `I026-C`, `I026-F`, and `I026-G`

Normative effect: **none** — `SourceSpan` remains implementation/tooling metadata and Protos semantics remain owned by the normative specification.

## Decision boundary

I026-B must expose valid Truffle `SourceSection` values for already-existing exact half-open `SourceSpan(startOffset, endOffset)` ranges. PLAT004 selects only how the current Truffle implementation owns and materializes that source-location metadata. It does not define Protos-visible source identity, debugger semantics, editor protocol behavior, or language syntax.

The decision was opened because the ownership choice materially constrains later Truffle instrumentation, DAP and dynamic-LSP work. It was ratified only after explicit project-owner approval following comparison with current Truffle guidance and representative implementations including SimpleLanguage, TruffleRuby, GraalJS, FastR/Sulong and Apple Pkl.

## Selected architecture

The selected architecture is **root-owned exact Source identity + node-local compact half-open range + on-demand Truffle projection**.

The durable invariants are:

1. Each executable Truffle root is the sole structural owner of the exact Truffle `Source` identity for that executable tree.
2. Each source-bearing execution node retains only its existing compact source range. The current representation is `SourceSpan(startOffset, endOffset)`; PLAT004 does not require a second permanent per-node source object.
3. `SourceSection` is derived tooling metadata, not authoritative runtime state.
4. For an adopted node with a valid range and an exact root source, `getSourceSection()` derives the section from the root source and the node range using `source.createSection(startOffset, endOffset - startOffset)`.
5. An unadopted node, or a node whose executable root has no exact source, reports no fabricated location.
6. A genuinely unavailable source location may use a Truffle unavailable section only when there is a real owning `Source` to which that unavailable section belongs. Absence of ownership must not be disguised as an invented source.
7. The root's own `SourceSection` must describe the source range represented by that executable root. Derived closure/object-body roots therefore remain free to expose their exact retained body/root range rather than being forced to claim the entire file.
8. Execution nodes do not duplicate the root's `Source` reference merely to make `getSourceSection()` independent of adoption.
9. Execution nodes do not eagerly retain one `SourceSection` object each merely because tooling may later request it.
10. A future measured optimization may cache a derived `SourceSection` on a node, but such a cache remains non-authoritative derived state. It must not create a second source-identity owner or change cloning/rebinding semantics.
11. Static/source-oriented tooling may continue to consume `SourceSpan` directly. LSP/static analysis does not become dependent on an adopted Truffle AST merely because runtime instrumentation uses `SourceSection`.
12. `InstrumentableNode`, `StandardTags`, debugger scope/value policy and protocol capability claims remain owned by later I026 slices.

## Why this architecture

The selected shape matches the current Oracle SimpleLanguage pattern and the current TruffleRuby source-location model: compact node-local indices/ranges are retained while the exact `Source` is obtained from the adopted root when tooling asks for a `SourceSection`. Current Truffle documentation explicitly supports this lazy pattern to avoid eager source-section allocation when source locations are tooling metadata rather than language semantics.

Apple Pkl deliberately chooses a different trade-off: its AST nodes retain concrete `SourceSection` values created by the builder. That is coherent for Pkl's diagnostic-heavy runtime representation, but would impose a permanent per-node Truffle object cost on ordinary Protos execution even when instrumentation is unused.

GraalJS uses a more complex hybrid: nodes can retain a `Source` plus offsets and lazily replace that state with a cached `SourceSection`, with explicit source-metadata transfer during clone/replace operations. PLAT004 does not adopt that additional ownership/caching machinery before Protos has evidence that repeated tooling lookup justifies it.

Sulong and FastR demonstrate that specialized source/debug metadata can require other representations, but neither requires Protos to promote `SourceSection` to fundamental source authority.

## Scalability rationale

The baseline cost for an ordinary Protos execution node remains the source range it already retains. No extra permanent `Source` reference and no eagerly allocated `SourceSection` are required per node. This matters as executable trees, modules and Process-scoped Truffle Contexts scale.

Tooling pays the projection cost only when it asks for source metadata. `getSourceSection()` is a tooling/introspection path rather than guest hot-path semantics. If future DAP/instrumentation profiling shows repeated section construction to be material, a derived cache can be added without changing this ownership decision.

Cloning, projection and AST replacement also remain structurally simple: node-local source ranges travel with nodes, while each newly materialized executable root supplies its own exact source identity. The implementation therefore does not need a second per-node source-identity transfer protocol merely to keep source metadata coherent.

## Protos design fit

PLAT004 follows the repository design philosophy:

- **one owner, derived views** — the executable root owns exact Truffle source identity; nodes own only their local range;
- **pay only for what you use** — ordinary execution does not allocate a `SourceSection` per node merely for optional tooling;
- **mechanisms over institutions** — `SourceSection` remains the ordinary Truffle projection `Source × range`, not a new permanent Protos runtime category;
- **ordinary things remain ordinary** — `SourceSpan` continues to serve frontend/static tooling without forcing all tooling through Truffle;
- **scale by composition** — the same source/range relationship works for top-level roots, nested executable roots, DAP and later instrumentation without changing universes; and
- **minimize duplicated state** — exact `Source` identity is not copied throughout the AST.

## Rejected alternatives

### Eager `SourceSection` per execution node

Rejected as the default because it permanently couples every source-bearing node to an allocated Truffle section even when tooling is absent. It remains a valid pattern in languages such as Pkl where source sections are deeply embedded in runtime diagnostic representation, but that trade-off is not required by current Protos semantics or tooling needs.

### Per-node `Source` ownership plus lazy section derivation

Rejected because it duplicates source identity throughout the tree, increases cloning/rebinding obligations and creates a second structural owner for information already owned by the executable root.

### GraalJS-style per-node lazy source/section state and cache

Rejected as the baseline because it buys cache/clone/replace complexity before measurements show that repeated `SourceSection` construction is a bottleneck. A non-authoritative cache remains available later under invariant 10.

## I026-B implementation evidence

I026-B consumes this decision in `0.2.303-SNAPSHOT`. `ProtosRootNode` keeps the exact Truffle `Source` as the
single structural source owner and projects its body `SourceSpan` to the root section. Adopted
`ProtosExpressionNode` instances ask that root to project their own retained half-open spans on
demand; the expression base gains no `Source` or `SourceSection` field. Missing root/source
ownership returns no location, while a retained range outside the exact source fails closed rather
than being clipped. This closes the mapping layer only; `InstrumentableNode` coverage and
`StandardTags` remain I026-C work.

## Deliberately deferred

PLAT004 does not select:

- `InstrumentableNode` coverage or `StandardTags` (`I026-C`);
- debugger scopes, receivers or value views (`I026-D`/`I026-E`);
- DAP or dynamic-LSP capability claims (`I026-F`/`I026-G`);
- a static Protos language-server architecture;
- `ContextPolicy.REUSE` or `ContextPolicy.SHARED`; PLAT001 remains on `EXCLUSIVE`; or
- any Protos-visible source-location, stack-trace or diagnostic semantics.

## Consumer release

Publication of this ratified record releases `I026-B` from `BLOCKED_BY_PLAT004` to implementation work. I026-B must implement and validate the mapping without expanding into I026-C instrumentation/tag policy.
