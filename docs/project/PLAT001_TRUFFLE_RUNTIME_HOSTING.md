# PLAT001 — Truffle runtime hosting topology

Status: **RATIFIED**

Nature: durable non-normative platform/runtime architecture decision

Approved by project owner: **2026-09-08**

Primary consumer: `I026-A4`

Origin: the I026-A4B cutover audit exposed that the A4A host-owned Polyglot
`Context` was intentionally thread-confined staging machinery while Protos
Actors and isolated `P` work already execute on real multiple carriers. A durable
Truffle hosting topology therefore had to be selected before the production
runtime-entry cutover could continue.

## Decision

For the current GraalVM/Truffle implementation, Protos selects a layered hosting
model:

```text
host runtime
    |
    `-- shared Truffle Engine
          |
          +-- Process-hosted multithread Context
          |     +-- RootActor
          |     +-- Actor carriers
          |     `-- P execution carriers
          |
          `-- another Process-hosted multithread Context
                `-- ...
```

The current hosting strategy is therefore **one multithread Truffle `Context`
per hosted Protos Process, with a Truffle `Engine` shareable across multiple such
Contexts**.

This mapping is implementation placement, not semantic identity. The following
relations are explicitly false:

```text
Truffle Engine  == Protos Node
Truffle Context == Protos Process
Java Thread     == Protos Actor
```

A Protos Process, Actor, Task, `ModuleKey`, `ProtosActivation`, ActorRef, and
other semantic entities keep their existing specification-defined identities,
lifetimes, authority, isolation, and failure behavior independently of Truffle
objects.

## Multithreading contract

The selected topology does not add a global execution lock or a language-level
GIL. Distinct Protos Actors that are semantically allowed to progress in
parallel must remain able to execute on distinct carriers, and isolated `P`
execution must retain its existing physical parallelism.

Accordingly, the Truffle language implementation may authorize concurrent
thread access only after its implementation state has been audited and proven
safe for the accesses that the Protos scheduler/runtime performs. Carrier
threads enter and leave the appropriate Context around guest execution rather
than becoming semantic owners of Actors or Processes.

`ContextReference` or other Truffle context-local machinery may locate Truffle
infrastructure. It MUST NOT replace the explicit Protos activation/task/Actor
state with a hidden semantic `ThreadLocal`, global singleton, or carrier identity.

## Why this topology

The decision was selected after comparison with production Truffle language
implementations and the Protos execution model:

- Espresso/Java demonstrates a genuinely multithreaded guest runtime with
  context-local VM state and shared language/runtime machinery;
- TruffleRuby demonstrates real parallel guest threads while the language
  implementation owns synchronization of its mutable runtime state;
- Sulong/LLVM and GraalWasm demonstrate Truffle languages that explicitly allow
  multithread access for workloads that are not single-thread interpreter loops;
- GraalPy is useful counter-evidence: Truffle can permit multiple threads while
  a language may still impose its own GIL, showing that Truffle permission does
  not define language concurrency semantics;
- JavaScript worker-style context separation is a poor match for Protos Actors
  because Protos already owns Actor isolation, transfer, scheduling, and
  identity. A Context per Actor would introduce a second artificial isolation
  universe.

A Context per carrier is also rejected because Actor placement on a physical
carrier is not semantic identity and may change over time. A single global
Context for every hosted Protos Process is technically viable but unnecessarily
couples independent Process lifecycle/failure domains to one Truffle Context.
Serializing all Protos execution around one Context is rejected because it would
silently destroy existing physical concurrency.

## Scalability and future evolution

This topology is intended to scale by composition:

- many Actors do not imply many Contexts;
- a Process can use many carriers through one multithread Context;
- many hosted Processes may use separate Contexts while sharing an Engine and
  therefore platform-level code/instrumentation infrastructure where Truffle
  permits it;
- distributed placement remains independent of Engine/Context identity;
- debugger/instrumentation infrastructure can observe multiple Contexts and
  threads without making them Protos semantic identities.

The **one Context per hosted Process** mapping is a ratified current platform
architecture, not a permanent language promise. A later performance/scalability
investigation may justify pooling, sharding, multiplexing, or another hosting
strategy. Such a change requires a new or superseding explicit `PLATxxx`
decision and evidence that all Protos-visible semantics remain unchanged; it does
not require a language-specification revision merely because hosting machinery
changed.

## Implementation safety gate

Returning `true` from Truffle `isThreadAccessAllowed(...)` is a correctness
claim about implementation state, not a switch that manufactures thread safety.
Before the runtime cutover relies on it, implementation work must audit at least:

- mutable fields on `ProtosLanguage` and `ProtosLanguageContext`;
- parser/compiler/lowering state shared by a language instance;
- Core/bootstrap global or static mutable state;
- caches and module-runtime state reached concurrently;
- Actor scheduler and root-task entry interactions;
- `P` carrier interactions;
- any mutable standard prototype/backing shared across domains; and
- lifecycle/close races between Process termination and guest execution.

The implementation must preserve existing Actor/Task/Process rules rather than
making physical Truffle scheduling observable.

## I026-A4 decomposition

PLAT001 refines the remaining A4B implementation into three ordered slices:

| Slice | Initial status | Scope / exit condition |
|---|---|---|
| I026-A4B1 | READY | Audit and establish Truffle multithread safety; replace A4A owner-thread confinement with bounded per-carrier enter/leave; authorize only validated thread access; prove no global execution lock/GIL, no semantic ThreadLocal, and concurrency parity. |
| I026-A4B2 | BLOCKED_BY_DEPENDENCIES | Introduce shared-Engine / Process-scoped Context lifecycle, prove multiple independent Process Contexts and concurrent Actors, and validate termination/lifecycle isolation. |
| I026-A4B3 | BLOCKED_BY_DEPENDENCIES | Cut CLI, REPL, bundled-tool, workspace and remaining production drivers over to the PLAT001 substrate and retire direct compiler/call entry as a separate primary production architecture. |

`I026-A4` closes only after A4B3 publishes and the old direct primary runtime
entry architecture is retired.

## A4B1 implementation evidence

I026-A4B1 closes the first PLAT001 safety gate in `0.2.267-SNAPSHOT`. The
Truffle language now authorizes concurrent external thread access, and the A4A
owner-thread/permanent-entry bridge is replaced by per-execution Context
enter/leave. A fair per-Context read/write lifecycle lock permits concurrent
executions under shared read ownership while `close()` alone takes exclusive
ownership; this is lifecycle coordination rather than a guest-execution GIL.

The retained audit is `docs/project/I026_A4B1_TRUFFLE_MULTITHREAD_SAFETY.md`. It
records the stateless/immutable compiler and language-context evidence and one
important remaining B2 requirement: Core bootstrap still mutates the static root
Object during protocol installation, so concurrent bootstrap of multiple hosted
Processes must be proven or bounded before PLAT001 multi-Process hosting closes.
Actor/P carrier binding also remains B2 because Process-to-Context ownership is
not established until that slice.

`ContextPolicy.SHARED` remains deferred. A4B1 does not require or approve it.

## A4B2A implementation evidence

I026-A4B2 is mechanically decomposed into A4B2A-A4B2B without changing PLAT001.
A4B2A closes in `0.2.269-SNAPSHOT` and establishes an explicit
`ProtosPolyglotRuntimeHost` that owns one Engine without making it a JVM singleton. Multiple
semantic Processes may bind once to distinct `ProtosPolyglotProcessContext` instances created
from that same Engine. The Process runtime depends only on a host-neutral internal execution-host
interface; Context identity never becomes Process identity or authority.

Semantic Process termination remains authoritative and happens first. Only after the Process has
reached `TERMINATED` does the host binding request physical Context close; a request originating
from an entered carrier is deferred until that carrier leaves. Closing the Engine owner while any
Process Context is still active fails instead of implicitly terminating or cancelling a live Protos
Process. Terminating one Process closes only its Context and leaves sibling Process Contexts on the
same Engine usable.

A4B2A deliberately retains A4B3's staged unbound/direct Process path. Remaining A4B2B work is
mechanically decomposed into A4B2B1 Actor routing, A4B2B2 P routing and A4B2B3 concurrent
Core-root bootstrap closure.

## A4B2B1 implementation evidence

I026-A4B2B1 closes in `0.2.271-SNAPSHOT`. `ProtosActorScheduler` remains Truffle-neutral and wraps
exactly the selected non-preemptive Actor segment in the owning Process's host-neutral
`callInExecutionHostForRuntime` boundary. Initialization/control turns, runnable Task segments and
accepted mailbox turns therefore use the same Process placement without binding Actor identity to
a carrier or creating a Context per Actor. Actors without a Process and staged unbound Processes
retain their existing direct path.

Focused integration evidence runs two Actors of one hosted Process concurrently on two distinct
carrier threads and proves both observe one exact `ProtosLanguageContext`. No global execution
lock, semantic ThreadLocal or carrier affinity is introduced. A4B2B2 subsequently closes the P carrier placement requirement; A4B2B3 and A4B2 are now CLOSED
in `0.2.280-SNAPSHOT`: A publishes frozen Core state and B publishes A+ Context-local executable projection
with final multi-Process no-cross-CallTarget/no-global-lock evidence. A4B3 is READY.

`ContextPolicy.SHARED` remains deferred; explicit Engine sharing here does not change the default
EXCLUSIVE language-context policy.

## A4B2B2 implementation evidence

I026-A4B2B2 closes in `0.2.273-SNAPSHOT`. The existing P-domain registry now retains
implementation-only Process host placement in addition to P membership. Snapshot creation obtains
that placement from the originating Actor's Process host or, for nested P, from the parent P domain.
The placement pointer never enters the P value-transfer graph and exposes no Process, Actor, I/O,
scheduler or Context capability to Protos code.

Guest invocation inside each P domain enters the retained Process execution host. Before the P root
invokes guest code, its module activation is attached to that exact root `ProtosTask`; nested
`Future.value()` therefore uses the existing cooperative suspension/replay machinery and nested P
producer work remains structurally owned by the current P task rather than becoming an unowned root.
Two sibling P computations remain physically concurrent on distinct carriers while observing the exact
same `ProtosLanguageContext` as their hosted Process, and nested P creates a fresh isolated P domain
while inheriting that same hosting placement. Queueing, snapshotting and deterministic result
bookkeeping remain outside Context entry when they do not execute guest code. Standalone/unbound
migration paths remain direct.

A4B2B3 and A4B2 are CLOSED in `0.2.280-SNAPSHOT`. A publishes atomic frozen Core state; B implements the
already-approved A+ context-local executable projection and final multi-Process evidence. A4B3
is READY. `ContextPolicy.SHARED` remains deferred.


<!-- I026-A4B2B3-A-PLUS: v1 -->
## A4B2B3 executable-layer ownership amendment

Approved by project owner: **2026-09-09**

Blocker: **B010**

The A4B2B3 Truffle execution-layer question was re-audited against current
Truffle practice, including Apple Pkl, GraalJS, GraalPy, GraalWasm and Truffle
SimpleLanguage, and against the official `EXCLUSIVE` / `REUSE` / `SHARED`
context-policy progression. The project owner explicitly approved the resulting
**A+** direction after scalability review.

For the current I026 implementation:

- `TruffleLanguage.ContextPolicy.EXCLUSIVE` remains the active policy;
- the standard `Object` role and its source-backed `init`, `==` and `!=`
  behavior remain semantic/runtime values rather than being replaced by native
  per-Context builtins;
- a source-backed Closure that can be reached from more than one Process Context
  must not own one JVM-global sharing-layer-bound `ExecutionPlan` or
  `CallTarget`;
- the executable projection for such a Closure belongs to the current
  `ProtosLanguageContext` under `EXCLUSIVE`, is cached only within that Context,
  and becomes unreachable with that Context;
- no JVM-global `Context -> ExecutionPlan` map is introduced on the Closure or
  root object;
- synchronization may protect narrow one-time Core publication, but ordinary
  guest invocation must not pass through a global execution lock or GIL.

The intended current shape is therefore:

```text
semantic source-backed behavior
            |
            +-- Process Context A -> execution plan / CallTarget A
            +-- Process Context B -> execution plan / CallTarget B
```

This is intentionally an implementation-layer split: Protos Closure semantics,
receiver binding, lexical capture, root delegation and source provenance do not
change merely because the Truffle executable material is context-local.

### Scalability and future policy progression

Under the current policy, executable memory scales with the number of active
Process Contexts times the bounded set of shared source-backed roots that need a
context-local projection. Ordinary invocation has no global lock and no scan of
other Contexts. Context teardown also tears down its executable cache by normal
reachability.

`ContextPolicy.REUSE` is now recorded as an explicit future intermediate audit
point: it can validate context-independent AST reuse after a Context is disposed
without yet permitting concurrent active Contexts to share one language
instance. `ContextPolicy.SHARED` remains the later, stronger optimization and
still requires a complete context-independence / concurrent-AST audit plus
benchmarks. Neither policy is approved by this amendment.

The A+ boundary is deliberately compatible with that progression: future
`REUSE` or `SHARED` work may widen the owner of executable material without
changing Protos callable semantics if and only if the stronger Truffle contracts
are proven.

### Resolved root-state semantic gate

The A+ amendment itself did not choose observable root mutability. That separate
question was recorded as B010 and is now normatively resolved by **D049** /
specification revision `0.1.389`: a standard object physically shared through the
prelude and exposing ordinary structural state is published frozen before guest
observation, and the unique root `Object` is explicitly covered. B010 therefore
remains READY while A4B2B3 implements that semantic boundary. B2B3A publishes the frozen Core-publication half and B2B3B closes the A+ executable half in
`0.2.280-SNAPSHOT`; B010 and A4B2 are now CLOSED and A4B3 is READY.

D049 remains normative language authority, not a PLAT001 decision. PLAT001 still
owns only the Truffle execution-layer consequence: shared semantic behavior may
exist while sharing-layer-bound executable material remains context-local under
`ContextPolicy.EXCLUSIVE`.

## A4B2B3B implementation evidence

I026-A4B2B3B closes in `0.2.280-SNAPSHOT` without changing the ratified A+ architecture. The
globally shared source-backed `Object.init`, `Object.==` and `Object.!=` Closures retain
their semantic identity, canonical definition, captures and one shared executable template.
Before root publication they are marked as requiring Context-local execution projection.

An entered invocation resolves the current `ProtosLanguageContext` and uses a cache owned
by that Context, keyed by the shared template plan. The cached value is rebuilt from the
canonical definition using the current EXCLUSIVE `ProtosLanguage`, retaining the template's
Truffle Source when present. Bound-method materialization carries the marker and exact same
template, so repeated receiver binding does not increase cache cardinality. The cache has no
static/JVM-global owner and becomes unreachable with its Context.

The staged unentered path continues to execute the existing template directly until A4B3
retires direct compiler/call entry as a primary production architecture. Hosted invocation
never writes a projected plan into the shared Closure. No guest-visible identity, lookup,
callable or Process rule changes.

Focused evidence hosts two distinct Process Contexts on one explicit shared Engine, invokes
the same global source-backed `Object.!=` behavior on separate carriers and blocks inside
ordinary guest dispatch until both Contexts have arrived. This proves physical overlap rather
than serialized admission. The two Context caches contain distinct projected plans with
distinct parameter-binding and body CallTargets owned by their respective language instances;
the shared template remains unchanged. There is no global guest execution lock or GIL.

B010, A4B2B3, A4B2B and A4B2 are CLOSED; A4B3 is READY. EXCLUSIVE remains active and
REUSE/SHARED remain deferred exactly as ratified.

## Explicitly deferred choices

PLAT001 does **not** ratify any of the following:

- `TruffleLanguage.ContextPolicy.REUSE`;
- `TruffleLanguage.ContextPolicy.SHARED`;
- an exact number of Engines per host/JVM/Node;
- Context pooling or reuse after Process termination;
- preinitialization strategy;
- Native Image hosting/layout policy;
- Engine code-cache sizing/eviction policy;
- Truffle resource-limit policy;
- exact DAP/LSP product UX;
- a permanent requirement that every future Protos implementation use Truffle;
  or
- a language-visible Process/Context relationship.

`ContextPolicy.SHARED` in particular remains an implementation option to be
audited during/after A4B2; PLAT001 must not be cited as prior approval for it.

## Specification boundary

PLAT001 changes no normative Protos specification. If implementation of this
hosting topology exposes a missing language rule or would require changing
observable Process/Actor/P behavior, dependent implementation must stop and use
the normal `Dxxx`/specification approval process for that semantic question.
