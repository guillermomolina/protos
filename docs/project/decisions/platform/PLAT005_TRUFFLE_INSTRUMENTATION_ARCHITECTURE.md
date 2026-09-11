# PLAT005 — Truffle instrumentation coverage and StandardTags architecture

Status: **RATIFIED**

Nature: durable non-normative Truffle runtime/tooling architecture decision

Approved by project owner: **2026-09-09**

Primary consumers: `I026-C`, then `I026-E`, `I026-F`, and `I026-G`

Normative effect: **none** — this decision selects Truffle instrumentation machinery and tooling metadata only. It does not define Protos-visible statement, call, source-location, scheduling, suspension, value, scope, or debugger semantics.

## Decision boundary

I026-C must expose a faithful minimum execution surface to Truffle instrumentation after I026-B/PLAT004 established exact source mapping. PLAT005 selects which common instrumentation mechanism and standard-tag projection the current Truffle implementation uses, where probes sit relative to Protos replay, and which richer tooling surfaces remain deliberately deferred.

The decision was opened because mechanically instrumenting every AST node or every physical Truffle root would turn implementation structure into debugger-visible structure. It was ratified only after explicit project-owner approval following comparison with current Truffle guidance and representative maintained implementations including Oracle SimpleLanguage, TruffleRuby, GraalJS, Apple Pkl, GraalPy/Bytecode DSL, Sulong and Espresso.

## Selected architecture

The selected architecture is **layered semantic-minimum instrumentation: one common instrumentation mechanism plus explicit minimal semantic tag membership**.

The durable invariants are:

1. `ProtosExpressionNode` owns the common Truffle `InstrumentableNode` / generated-wrapper mechanism for source execution nodes. Protos does not create separate wrapper institutions for statements, calls, sends, debugger stepping, profiling, or later tooling features.
2. Instrumentation remains subordinate to PLAT004 source ownership. A node without a real adopted PLAT004 source location must not fabricate an instrumentable source location merely to satisfy tooling.
3. Tag membership is explicit, compact, immutable tooling metadata assigned from the canonical/lowering role of the node. It is not inferred from physical Java/Truffle node identity, line-number heuristics, or the mere existence of a `RootNode`/`CallTarget`. The exact compact encoding is implementation detail and does not become semantic authority.
4. The I026-C baseline provides exactly `StandardTags.StatementTag` and `StandardTags.CallTag`. Adding another standard or custom tag crosses from zero to one for that tooling category and requires evidence that it faithfully represents existing Protos execution structure.
5. `StatementTag` denotes each direct executable child expression of a `CanonicalSequence`: the already-existing source-order sequencing boundary. The `CanonicalSequence` container itself is not a second statement point, and receiver/argument/literal/helper subexpressions are not separate statement points merely because they execute. This keeps a single-expression body as one step, permits two sequential expressions on one physical line to remain two steps, and keeps one multiline expression as one step.
6. `CallTag` is attached to the source-level guest invocation/dispatch locations represented by `ProtosCallNode`, `ProtosSendNode`, and `ProtosSuperSendNode`. Those nodes are the current source-level points that select/invoke guest Closure behavior; helper/native implementation calls are not tagged merely because Java invokes a method.
7. `RootTag` and `RootBodyTag` are **not** provided by the baseline. Physical `ProtosRootNode` cardinality is not semantic-frame cardinality: Closure parameter binding and body execution currently use distinct CallTargets, and object bodies also use auxiliary roots. Root tags remain deferred until a later decision/evidence can identify semantic roots without promoting helper roots into language frames or violating Truffle's root-prolog contract.
8. `ExpressionTag`, `ReadVariableTag`, `WriteVariableTag`, debugger-only custom Protos tags and other optional tag families are also deferred. PLAT005 does not invent expression/variable institutions solely to obtain debugger or LSP features.
9. Generated instrumentation wraps the actual node execution below the `ProtosEvaluatorContinuation` replay gate — the current `executeDirect(VirtualFrame)` execution boundary — rather than replacing the final replay-aware `execute(...)` entry contract. A completed replay entry that returns an already-recorded result therefore emits no duplicate execution event.
10. Replay-tape identity remains the original guest `ProtosExpressionNode` delegate. A Truffle wrapper/probe is derived tooling machinery and must never become the identity retained by `ProtosEvaluatorContinuation`, affect replay divergence checks, or alter task/Future ownership, Actor/P scheduling, evaluation order, effects, failures, or guest identity.
11. Until I026-D supplies faithful debugger/interop value views, instrumentation event return values are suppressed with the generated wrapper's outgoing conversion boundary (reported as no tooling value) while guest execution still returns the exact Protos value. C must not invent a parallel `DebugValue` hierarchy or expose implementation objects merely to satisfy probes.
12. Instrument-introduced incoming values fail closed until the separately owned interop/value bridge can convert them faithfully. Tooling must not inject arbitrary host/interop objects into guest execution as if they were ordinary Protos values.
13. Protos cooperative suspension/replay is **not** mapped to Truffle `yield`/`resume` by PLAT005. `ProtosEvaluatorSuspension` remains host-only scheduler machinery. I026-F must first prove real DAP behavior across suspension; a future PLAT amendment is warranted only if that evidence shows a dedicated yield/resume bridge is required.
14. Source/tag metadata copied into generated wrappers or any future replacement/materialized nodes is derived tooling state. PLAT004 root-owned exact `Source` identity and canonical source ranges remain authoritative.
15. If later optimization/fusion removes a tagged source-level execution boundary, `materializeInstrumentableNodes(...)` or explicit derived tag/source transfer may reconstruct that tooling boundary. Such materialization must preserve the same canonical tag authority and cannot change Protos semantics.
16. Ordinary execution without an attached instrument must not allocate permanent probe/wrapper objects or acquire debugger-specific synchronization/coordination. The persistent baseline cost is limited to the already-required source span plus compact tag classification.
17. The decision is representation-independent enough to survive a future Truffle Bytecode DSL backend: the authoritative mapping remains canonical execution role -> selected tooling tags, while the concrete AST wrapper/bit representation may later become Bytecode DSL operation/tag declarations.

## Why this architecture

### Oracle SimpleLanguage

SimpleLanguage demonstrates the useful split between one common instrumentable/wrapper mechanism and explicit tag membership assigned by parsing/lowering. It also demonstrates that `ExpressionTag` is optional rather than a prerequisite for ordinary stepping.

### TruffleRuby

TruffleRuby uses a common source-bearing instrumentable node family with compact explicit flags while retaining lighter non-source/non-instrumentable bases to save footprint. Its design is strong evidence that instrumentation can scale without making every runtime helper a debugger event.

### GraalJS

GraalJS uses a common wrapper/base with explicit compact Statement/Call/RootBody/Expression metadata and deliberate transfer/materialization rules during AST replacement. It validates the layered approach, while also showing the maintenance cost of adopting richer tag families before they are required.

### Apple Pkl

Apple Pkl broadly instruments expression nodes, but its current primary instrumentation contract uses a custom Pkl expression tag for Pkl-specific value tracking rather than the standard Statement/Call contract needed as the baseline for generic stepping. Pkl therefore supports the viability of a common mechanism, not a requirement to tag every Protos expression as a debugger statement.

### GraalPy / Bytecode DSL

Modern GraalPy/Bytecode DSL code provides a broad standard-tag palette but attaches tags explicitly to semantic operations/regions. The Bytecode DSL also treats correct root/prolog instrumentation as a specialized concern. This supports keeping Protos root tags deferred rather than mechanically mapping each physical CallTarget to a semantic frame.

### Sulong and Espresso

Sulong and Espresso provide only the standard tags that accurately fit their source/debug execution models. Sulong in particular separates source-level statement metadata from generic executable machinery. They reinforce the fail-closed rule: absence of a faithful semantic correspondence is better than a convenient but false tag.

## Scalability rationale

The architecture keeps ordinary execution cost bounded and local. Source-bearing execution nodes already retain `SourceSpan`; PLAT005 adds only compact classification metadata. Generated wrappers/probes are materialized only when instrumentation attaches, so programs that do not use tooling do not pay persistent per-probe allocation or coordination costs.

Step-event cardinality scales with meaningful sequential execution boundaries rather than with every AST subexpression. This prevents instrumentation volume from exploding as expressions become structurally richer, while still retaining independent sequential steps and explicit call sites.

Replay and asynchronous execution remain independent from tooling. Completed replay events do not re-execute the wrapped boundary, so attaching a debugger cannot duplicate already-completed Protos effects. Future/Actor/P scheduling retains its existing ownership model, and no global instrumentation registry or shared mutable tag map is introduced.

The design also scales forward: optional expression/variable/root tags, richer interop values, replacement materialization and a Bytecode DSL backend can be added compositionally without replacing the baseline source/tag authority.

## Protos design fit

PLAT005 follows the repository design philosophy:

- **mechanisms over institutions** — one instrumentation mechanism supports multiple future tools without creating separate debugger/profiler AST universes;
- **ordinary things remain ordinary** — `CanonicalSequence` already defines source-order execution, so stepping projects that existing structure rather than inventing a `Statement` language category;
- **no pets** — physical helper roots do not become privileged semantic roots because they happen to be CallTargets;
- **pay only for what you use** — compact classification is persistent, while wrappers/probes and event traffic exist only when tooling attaches;
- **one runtime truth** — C does not invent values/scopes; I026-D/E remain the owners of faithful tooling views;
- **fail where the invariant is violated** — unsupported incoming tooling values are rejected rather than silently reinterpreted;
- **minimize shared mutable state** — tag authority is local immutable metadata, not a process/global registry; and
- **scale by composition** — the same canonical-role mapping can feed the current AST, later materialization and a future Bytecode DSL representation.

## Rejected alternatives

### Broad Pkl-like expression instrumentation plus broad standard tagging

Rejected as the baseline because it would turn structural subexpressions into debugger stops, increase event cardinality, prematurely require expression/value tooling semantics and couple I026-C to I026-D.

### Narrow per-node-family instrumentation implementations

Rejected because creating separate wrapper mechanisms only on today's Statement/Call node classes saves little persistent state while making later instrumentation features require a structural rewrite or proliferating wrapper implementations.

### Mechanically tagging every physical root

Rejected because Protos currently uses helper CallTargets for Closure parameter binding and object bodies. Physical root identity is implementation machinery and is not sufficient evidence of a user-visible semantic frame.

### Truffle yield/resume mapping in I026-C

Rejected for now because Protos suspension uses its own task/replay contract. A debugger-driven mapping without real DAP evidence could couple scheduler semantics to tooling. The decision remains deliberately open for I026-F evidence.

## Deliberately deferred

PLAT005 does not select:

- `RootTag` / `RootBodyTag` semantics;
- `ExpressionTag`, read/write-variable tags or custom Protos tags;
- debugger-visible runtime value representation (`I026-D`);
- top/local debugger scopes (`I026-E`);
- a Protos-suspension to Truffle-yield/resume mapping;
- DAP or dynamic-LSP capability claims (`I026-F` / `I026-G`);
- a static Protos language-server architecture or editor UX;
- `ContextPolicy.REUSE` or `ContextPolicy.SHARED`; PLAT001 remains on `EXCLUSIVE`; or
- any Protos-visible statement, call, source-location, debugger, stack, scheduling or suspension semantics.

## Consumer release

Publication of this ratified record releases `I026-C` from `BLOCKED_BY_PLAT005` to implementation work. I026-C must implement and validate only the selected semantic-minimum baseline; any newly exposed durable platform choice must return through the PLAT decision gate before dependent implementation proceeds.


## I026-C executable consumption

I026-C consumes this ratified architecture in `0.2.311-SNAPSHOT`. The implementation keeps the decision non-normative and semantically invisible: generated instrumentation wrappers are derived machinery, canonical-role tags remain the approved minimum surface, and continuation replay uses PLAT008 logical site identity rather than physical wrapper identity. Focused and full executable validation are publication gates for the consuming slice.

## Later resolution — PLAT026

PLAT026, explicitly approved on 2026-09-11, resolves the `RootTag` part of this
decision's deliberate deferral after real PERF006-B6 production/DAP evidence established
the need for generic ROOT support.

The later ratified boundary preserves PLAT005's semantic-minimum rule:

- `RootTag` is provided only for truthful semantic top-level/module/Closure activation
  roots;
- Bytecode helper roots such as Object-construction/body children remain untagged;
- semantic roots use the Bytecode DSL automatic root-tag mechanism so the framework owns
  the correct pre-prolog probe boundary; and
- `RootBodyTag` remains deferred.

PLAT026 does not change StatementTag or CallTag semantics and does not make physical
`RootNode` / `CallTarget` identity a tooling authority.
