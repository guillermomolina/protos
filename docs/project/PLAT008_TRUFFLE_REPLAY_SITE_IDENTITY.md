# PLAT008 — Truffle replay-site identity across wrappers, rewrites and continuations

Status: **RATIFIED**

Nature: durable non-normative Truffle runtime/continuation architecture decision

Approved by project owner: **2026-09-09**

Primary consumers: `I026-C`, `I026-F`, and future replay/backend evolution

Normative effect: **none** — this decision selects internal replay identity machinery only. It does not define Protos-visible identity, source-location, scheduling, suspension, continuation, debugger, serialization, distribution, stack, or object semantics.

## Decision boundary

I026-C must insert Truffle instrumentation wrappers below the existing replay-aware execution entry selected by PLAT005. That exposes a representation conflict: `ProtosEvaluatorContinuation` currently detects replay divergence using exact `ProtosExpressionNode` object identity, while Truffle instrumentation inserts a `WrapperNode` between a guest execution node and its parent. Treating the wrapper object as the replay identity would make attaching tooling alter continuation behavior.

The decision also has a forward-compatibility consequence. AST specialization/replacement and a future Truffle Bytecode DSL backend can change the physical Java object that performs execution even when the logical execution site remains the same. PLAT008 therefore defines replay identity independently of derived wrapper machinery and independently of one concrete backend representation.

This question was first allocated informally as PLAT007 in GitHub coordination, but the durable registry already assigned PLAT007 to JVM NIO IPv6-only TCP listener enforcement. The tooling decision was therefore reallocated to PLAT008 before durable publication; the identifier correction does not alter the approved architecture.

## Selected architecture

The selected architecture is **logical replay-site identity with a zero-allocation delegate-backed AST representation and a representation-independent backend contract**.

The durable invariants are:

1. `ProtosEvaluatorContinuation` compares a **logical replay-site identity**, never the physical instrumentation wrapper/probe object that happens to execute.
2. In the current AST backend, the zero-allocation representation of a replay-site identity is the original guest `ProtosExpressionNode` itself after recursively unwrapping derived Truffle `InstrumentableNode.WrapperNode` layers.
3. Wrapper normalization is recursive and fail-closed. A recognized instrumentation wrapper resolves to its guest delegate; an unsupported wrapper/identity shape must not silently fall back to source coordinates, wrapper identity, class names or another heuristic.
4. Physical execution and replay identity remain distinct responsibilities. The current physical path still executes through the wrapper when instrumentation is attached so probes observe real execution; the replay tape records/compares only the normalized logical site.
5. A replay entry already marked complete returns its retained result before wrapper/delegate execution. Reconstructing the host stack therefore emits no duplicate instrumentation execution event and cannot repeat a completed Protos effect.
6. The current AST implementation adds no permanent `ReplaySite` object, per-node numeric replay id, global registry, side map, lock, atomic counter, source-derived key, UUID or cross-Context coordination structure.
7. The durable contract is **not** “Java node object identity forever”. It is “the active backend provides a replay-site identity that remains stable for the lifetime of one resumable execution”. The current guest-node representation is only the cheapest faithful implementation of that contract today.
8. An AST optimization, clone, materialization or replacement that can change the guest node representing a replay site while a resumable execution is still live must preserve or explicitly remap that site's logical replay identity before it is allowed to cross the suspension/replay boundary. A replacement that cannot affect any live resumable execution need not pay that preservation cost.
9. A future Truffle Bytecode DSL backend may represent the same logical contract using `BytecodeLocation`, root-plus-location data, or another backend-native stable execution location. Moving from AST delegate identity to such a representation must not change Protos replay semantics.
10. `SourceSpan` and Truffle `SourceSection` remain source-location metadata under PLAT004 and are not replay identities. Source locations can be shared, synthesized, unavailable, or insufficiently unique for execution-site identity.
11. Replay-site identity is runtime/task-local implementation state. It is not serialized, persisted, distributed, globally coordinated, exposed through debugger protocols, embedded in Protos object identity, or made visible to Protos programs.
12. PLAT008 does not map Protos suspension to Truffle `yield`/`resume`; PLAT005's deferral remains in force pending I026-F DAP evidence.
13. `ContextPolicy.EXCLUSIVE` remains unchanged; `REUSE` and `SHARED` remain deliberately deferred by PLAT001.

## Cross-runtime evidence

### Truffle wrapper contract

Truffle wrappers are derived interposition nodes around one instrumentable guest node and expose that guest node through `getDelegateNode()`. PLAT008 follows that abstraction boundary rather than making wrapper identity observable to continuation replay.

### Apple Pkl

Current Apple Pkl explicitly unwraps `WrapperNode` to its delegate for source and runtime/semantic inspection. `PklNode.getSourceSection()` delegates through the wrapper, and other runtime paths similarly unwrap before relating Truffle nodes to language structures. This is strong evidence that a modern Truffle language treats instrumentation wrappers as transparent derived machinery rather than guest-node identity.

### TruffleRuby

TruffleRuby provides `RubyNode.unwrapNode(...)` and explicitly ignores instrumentation wrappers when validating uninitialized AST clones. It also distinguishes source/instrumentable nodes from lighter runtime nodes for footprint reasons. This supports both wrapper transparency and the decision not to allocate an additional replay token for every node without demonstrated need.

### GraalJS

GraalJS centralizes wrapper removal in `JSNodeUtil.getWrappedNode(...)`, including several layers of instrumentation/runtime wrappers. Its generator resume path unwraps an instrumentation wrapper before resuming the underlying `ResumableNode`, while resumability state is kept separately in a state slot. This directly supports separating physical wrapper execution from logical continuation state.

### FastR

FastR documents that instrumentation wrappers may appear anywhere in an AST and must be unwrapped before casts or semantic/type inspection. The wrapper is therefore not treated as the authoritative language node.

### Sulong

Sulong keeps source/debug statement metadata on the guest `LLVMInstrumentableNode` and explicitly unwraps instrumentation wrappers before reading or mutating that metadata. Again, the delegate owns the durable guest-level information.

### Espresso

Espresso combines instrumentation and continuation-aware execution. Its debugger/JDWP paths unwrap instrumentable method wrappers to recover the real method node, while generated wrappers can independently participate in continuation/yield machinery. This reinforces the separation between tooling interposition and runtime continuation identity.

### GraalPy and the Truffle Bytecode DSL

Current GraalPy Bytecode-DSL generator/coroutine execution uses `ContinuationRootNode`, `BytecodeLocation`, materialized frames and bytecode-index/location infrastructure rather than durable Java AST-node object identity. The Bytecode DSL can enable yield and tag instrumentation together. This is the strongest forward signal for PLAT008: the architectural contract should be a logical execution-site identity whose concrete representation belongs to the backend, not an AST object type forever.

## Scalability rationale

For the current AST backend, ordinary execution without instrumentation pays effectively the same identity comparison as before. A normal guest node is already its own replay-site identity. With instrumentation attached, normalization performs bounded wrapper unwrapping, normally depth zero or one, before the same task-local identity comparison.

No per-node object/reference, registry, hash lookup, global id allocation, synchronization or Context-wide mutable identity map is introduced. Large ASTs and programs that never suspend therefore do not pay permanent memory for hypothetical future backend needs. Multiple Processes and Tasks remain independently scalable because replay identity is scoped to each resumable execution rather than globally coordinated.

The architecture also scales through optimization. Only replacements that can cross a live suspension/replay boundary must preserve or remap replay identity. This avoids imposing universal cloning/transfer state while still failing closed if an optimization would otherwise invalidate a live continuation.

The representation-independent contract provides a direct future path to Bytecode DSL `BytecodeLocation`-style identity without changing the continuation semantics or retrofitting a source-derived global identifier scheme.

## Protos design fit

PLAT008 follows the repository design philosophy:

- **mechanisms over institutions** — define one logical replay-site contract without creating a permanent hierarchy/registry of replay-site objects;
- **ordinary things remain ordinary** — the current guest execution node is already sufficient identity, so ordinary AST nodes receive no new object or id;
- **no pets** — Truffle wrappers do not become privileged semantic/replay identities merely because the framework inserts them;
- **pay only for what you use** — zero permanent per-node allocation now, bounded unwrap cost only when wrappers exist, and preservation/remapping only for replacements that can affect live replay;
- **one runtime truth** — source location, instrumentation wrapper and replay identity remain orthogonal authorities instead of overloading `SourceSpan` or debugger metadata;
- **fail where the invariant is violated** — an unsupported identity-preservation case fails rather than guessing by source, type or wrapper object;
- **minimize shared mutable state** — replay identity remains task/runtime-local with no global registry; and
- **scale by composition** — the same contract works with the current AST, future AST transformations and a later Bytecode DSL backend without changing the Protos universe.

## Rejected alternatives

### Physical wrapper/executing-node identity

Rejected because enabling tooling would change the object seen by replay and could create divergence or make debugger attachment semantically observable.

### SourceSpan / SourceSection identity

Rejected because source locations do not own execution identity and are not guaranteed unique across helper, collocated, synthesized or repeated execution sites.

### Eager stable ReplaySite object/token for every node

Rejected for the current backend because it would impose permanent memory and transfer/cloning rules on all source execution nodes before a demonstrated need. PLAT008 deliberately preserves the option for a future backend to use an explicit native location/token when that backend requires one.

### Raw guest Java-node identity as the permanent architecture

Rejected as the durable wording even though it is the selected current representation. It would unnecessarily couple continuation architecture to the present AST interpreter and create migration debt for Bytecode DSL or other future representations.

## Deliberately deferred

PLAT008 does not select:

- Truffle `yield`/`resume` mapping for Protos cooperative suspension;
- debugger-visible value/scope representation;
- semantic root/frame tagging;
- serialization, persistence or migration of live continuations;
- distributed/global replay-site identifiers;
- a specific future AST replacement/splitting optimization;
- a Bytecode DSL migration schedule or exact future location type; or
- `ContextPolicy.REUSE` / `ContextPolicy.SHARED`.

## Consumer release

Publication of this ratified record releases `I026-C` from `BLOCKED_BY_PLAT008` to implementation work. I026-C must implement PLAT005 and PLAT008 together: semantic-minimum instrumentation plus wrapper-transparent logical replay identity, with focused evidence for attached/detached instrumentation, completed replay without duplicate events and unchanged ordinary execution semantics/cost boundaries. Any newly exposed durable platform choice must return through the PLAT decision gate.
