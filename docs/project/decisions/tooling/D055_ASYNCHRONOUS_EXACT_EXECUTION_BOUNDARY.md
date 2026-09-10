# D055 — Asynchronous exact-execution boundary for Test Tool outer parallelism

Status: **RATIFIED**
Allocated: **2026-09-09**
Explicit project-owner approval: **2026-09-09**
Nature: implementation-independent public Test Tool execution/concurrency contract
Triggered by: `TOOL002-H`
Primary consumer: `TOOL002-H2`
Specification revision: **UNCHANGED** — no Core-language or normative specification change

## Decision boundary

TOOL002-H requires bounded outer parallel execution of independent ordinary test
cases while retaining one fresh semantic Protos Process / RootActor per case,
private case output and deterministic reporting.

TOOL002-H1 proved the implementation substrate can host two simultaneous fresh
semantic Processes on one shared `ProtosPolyglotRuntimeHost`, with distinct
Polyglot Contexts and private stdout per execution.

The remaining boundary is how ordinary bundled Protos tooling requests one exact
execution without blocking the Test Tool RootActor. The existing bootstrap-local
`execution(...)` / `executionInspect(...)` calls are synchronous. Wrapping one in
ordinary `Closure.future()` does not create physical outer overlap: the Closure
Future is a cooperative RootActor-local task and the synchronous exact execution
occupies that task's dispatch segment until the child Process returns.

D055 therefore owns the implementation-independent asynchronous exact-execution
contract. It does not select JVM thread/executor machinery.

## Ratified decision — one exact asynchronous operation, ordinary Future result

D055 selects a general, bootstrap-local, test-neutral asynchronous exact-execution
capability:

```text
exact execution request
        |
        v
asynchronous execution boundary
        |
        +--> fresh semantic Process / distinct Polyglot Context
        |        private stdin/stdout/stderr
        |        production Actor/Future/P semantics inside
        |
        v
captured inert execution evidence
        |
        v
caller-domain completion/rematerialization
        |
        v
ordinary Protos Future
```

The logical contract is one exact execution request to one eventual ordinary
caller-domain Future result. Batch scheduling is not part of this boundary.

## Durable invariants

1. **Ordinary Future.** The asynchronous result is an ordinary caller-domain
   Future. D055 introduces no Test-specific async handle, Future subtype,
   privileged Test object or second asynchronous universe.

2. **Mechanism versus policy.** Host code owns only irreducible
   submission/execution/completion mechanics. It does not own TestPlan, CaseId,
   expectation, aggregation, reporting, `jobs`, resource, priority or retry
   policy.

3. **Bounded admission remains Protos policy.** TOOL002-H Protos code owns the
   maximum number of case attempts simultaneously submitted. A concrete host
   carrier does not silently become the semantic concurrency limit.

4. **Caller-domain completion.** A host carrier must not construct or mutate
   caller-domain guest objects directly. Host execution first yields inert
   captured evidence. Completion is marshalled back into the caller Actor domain;
   only there may detached/rematerialized values be constructed and the caller
   Future be resolved, failed or cancelled.

5. **Private output.** Each exact execution retains independent private
   stdout/stderr capture. Completion order cannot merge or redirect one case's
   bytes into another case.

6. **Completion order is not report order.** Physical completion order does not
   define logical case order. TOOL002-H associates each result with its
   pre-existing stable plan position/CaseSpec and deterministic reporting follows
   logical plan order.

7. **Physical carrier is replaceable.** D055 does not select platform threads,
   virtual threads, a fixed executor, a work-stealing executor, a reactor or any
   other JVM carrier. A durable JVM-specific carrier choice belongs to a separate
   `PLATxxx` decision.

8. **No false hard preemption.** Same-runtime execution that has already started
   is not claimed to be forcibly killable. Cancellation may prevent work that has
   not started. Once child execution starts, the child must still reach required
   terminal cleanup; a late result is discarded if the caller Future is already
   terminal.

9. **Outstanding-work custody.** The asynchronous facility/session retains
   custody of submitted executions until host and child-Process cleanup is
   complete. A shared RuntimeHost cannot be closed while facility-owned child
   execution is still live merely because a caller-side Future became terminal.

10. **Hard containment is a backend evolution, not a new logical API.** A future
    amortized OS-worker or remote backend may provide hard timeout/crash
    containment while implementing the same one-exact-execution asynchronous
    contract.

## Layering and scalability contract

D055 separates three layers:

```text
1. Protos Test Tool policy
   TestPlan / stable cases / max-in-flight / later capacities / aggregation /
   deterministic reporting

2. One-exact-execution asynchronous contract
   submit exact execution -> ordinary caller-domain Future

3. Replaceable physical execution backend
   local Context on shared Engine
       -> future amortized OS worker
       -> future remote/distributed worker
```

The expensive logical capacity unit is the in-flight semantic execution
(Process/Context/output state), not the JVM carrier thread. For local execution,
a bound `N` therefore implies at most `N` admitted case-attempt executions and
private captures in flight under H policy.

TOOL002-I can later add resource/capacity admission before submission without
changing the exact-execution backend contract. Capacity-1 resources, weighted
CPU or memory budgets, exclusive external services and remote worker slots can
remain scheduler policy rather than Java executor topology.

A future hardened or remote backend preserves:

```text
CaseSpec
    -> exact asynchronous Process execution
    -> eventual captured result
    -> stable logical CaseRun
```

Physical completion order and placement remain non-semantic.

## Cross-runtime and test-runner review

Before approval, the decision was stress-tested against mature execution models:

- **Java / Loom:** logical task carriers are separated from application
  concurrency limits.
- **GraalVM Polyglot:** isolated Contexts may share an Engine while retaining
  distinct execution state and stream bindings, matching H1's local topology.
- **BEAM / Erlang:** work that would block the logical scheduler is displaced to
  dirty/external execution machinery.
- **Go:** goroutine identity is separated from the physical OS-thread carrier and
  bounded admission remains higher-level policy.
- **Rust / Tokio:** blocking work is moved behind an awaitable completion
  boundary and capacity is separately bounded; started blocking work is not
  falsely advertised as forcibly abortable.
- **.NET:** `Task` is the logical eventual-result contract while ThreadPool
  topology remains implementation machinery.
- **Node.js / Python asyncio:** blocking work is displaced from the logical
  scheduler and represented back to the caller asynchronously.
- **JUnit:** same-runtime parallel execution demonstrates the efficient local
  case.
- **pytest-xdist:** controller/worker distribution demonstrates preserving
  scheduler policy while moving physical execution to worker processes/hosts.
- **cargo-nextest:** process-per-test demonstrates why hard timeout/crash
  containment and robust output custody may justify a stronger future physical
  boundary without making test semantics process-specific.

The common scalable pattern is a stable logical scheduling contract over
replaceable physical execution.

## Why this is the Protos choice

The selected model preserves the existing Protos universe:

- **ordinary things remain ordinary** — asynchronous observation is an ordinary
  Future;
- **mechanisms over institutions** — one general async exact-execution mechanism
  supports the Test Tool without becoming a Java-owned test scheduler;
- **pay only for what you use** — local bounded parallelism does not require
  distributed/OS-worker machinery;
- **scale by composition** — the same Future/Process/capacity concepts extend
  from local Contexts to hardened/remote workers;
- **keep platform differences at the boundary** — JVM carrier choices do not
  become public Test Tool semantics;
- **prefer independence over coordination** — unrelated case Processes progress
  independently up to explicit scheduler capacity;
- **minimize shared mutable state** — per-case Process/output state remains
  isolated and orchestration state remains bounded.

`Process` remains the semantic isolation boundary. `Future` remains the
asynchronous observation boundary. Bundled Protos remains the owner of scheduling
policy. The host remains mechanism.

## Rejected alternatives

### A — `future()` around synchronous `execution(...)`
Rejected because it looks asynchronous without creating physical outer overlap.

### B — Actor workers as the Test Tool worker pool
Rejected because it couples outer case scheduling to the production Actor
scheduler that cases themselves may exercise.

### C — host batch primitive with a concurrency bound
Rejected as the primary contract because it moves admission policy toward the
host and makes later resource-aware scheduling less composable.

### D — Test-specific Java executor
Rejected because it recreates a Java-owned Test runner/scheduler universe.

### E — immediate OS-worker/process-only contract
Rejected as mandatory initial behavior because H1 proves useful local
same-runtime isolation. OS workers remain an important future hard-containment
backend.

### F — a new opaque asynchronous execution handle
Rejected because ordinary Future already expresses eventual completion and
composition without a second async concept.

## Intentionally deferred

D055 does not decide:

- public `--jobs` spelling, placement or default;
- the `jobs=auto` formula;
- Test Tool resource/capacity syntax or policy (TOOL002-I);
- JVM platform thread versus virtual thread versus executor/reactor mechanics;
- hard timeout duration, TERM/KILL escalation or same-runtime hard interruption;
- OS-worker pooling/lifetime policy;
- remote transport, worker discovery, placement or authentication;
- retry/flaky policy;
- result caching/affected-test analysis;
- public Test assertion/prototype/API design;
- priority/fairness policy beyond bounded admission and deterministic reporting.

Any new substantive choice in those areas follows the normal Dxxx/PLATxxx
approval gate.

## Ratification closure

The project owner explicitly approved the selected D055 architecture on
2026-09-09 after cross-runtime, VM, test-runner, future-scalability and
Protos-design review.

D055 is therefore `RATIFIED`.

Ratification itself changes no Core normative specification, executable runtime,
Maven implementation version, native boundary or license terms. It releases
TOOL002-H2 to bounded implementation decomposition.

Result:

```text
D055           RATIFIED
TOOL002-H      IN_PROGRESS
TOOL002-H1     CLOSED — 7bc0d7a4dbc6788be14b33ea29b8ad8c2b12b794
TOOL002-H2     READY
TOOL002-I      BLOCKED_BY_DEPENDENCIES: TOOL002-H
```
