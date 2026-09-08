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

## Explicitly deferred choices

PLAT001 does **not** ratify any of the following:

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
