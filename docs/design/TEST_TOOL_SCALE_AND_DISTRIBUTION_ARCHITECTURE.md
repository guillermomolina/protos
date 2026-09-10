# Protos Test Tool Scale and Distribution Architecture

Status: selected architecture; non-normative

Work item: `TOOL002`

Architecture checkpoint: follow-on to the expanded comparative audit published by
`3221b53edd51f0c46a7f660abc66bf45ef7041b6`.

Related Protos records:

- `docs/design/TOOLCHAIN_TOOL_ARCHITECTURE.md`
- `docs/design/TEST_TOOL_ARCHITECTURE.md`
- `docs/design/TEST_TOOL_COMPARATIVE_AUDIT.md`
- `docs/project/TOOL002_TEST_TOOL.md`
- `docs/project/registries/IMPLEMENTATION_STATUS.md`
- `spec/io/PROCESS_IO.md`
- `spec/concurrency/ACTORS.md`
- `spec/concurrency/PARALLEL_EXECUTION.md`
- `spec/concurrency/DISTRIBUTED_RUNTIME.md`

## Purpose

The initial TOOL002 architecture establishes a bundled Test Tool written primarily
in Protos, one fresh semantic Protos Process / RootActor per ordinary case, an
inert TestPlan above a general exact-entry executor, private streams, explicit
capabilities, deterministic reporting and capacity-aware future scheduling.

This document answers a different question:

> **Can the same architecture scale from a small local suite to very large,
> highly parallel and eventually distributed test execution without changing
> the semantic meaning of a test or creating a second test-specific runtime
> universe?**

The selected answer is **yes, if logical test identity, semantic Process
isolation, physical worker containment, scheduling policy and infrastructure
outcome are kept as separate layers**.

This document deliberately selects those architecture boundaries now while
leaving concrete CLI spelling, data serialization format, worker protocol,
remote-execution provider and resource-declaration syntax open.

Nothing here changes Core semantics. The document does not standardize a public
Test object, test syntax, distributed-test protocol, remote-execution API,
scheduler handle, worker identity or new Process/Node/Cluster operation.

## Executive architecture

The long-lived shape is:

```text
                         protos test
                             |
                             v
              discovery / manifests / filters
                             |
                             v
                     logical TestPlan
                stable, inert, serializable
                             |
                             v
                     test scheduler
            order / resources / shards / history
                             |
                             v
                        CaseAttempt
                             |
             +---------------+----------------+
             |               |                |
             v               v                v
       local backend    hardened backend   remote backend
       same runtime       OS workers       worker pool
             |               |                |
             +---------------+----------------+
                             |
                             v
                 ExactExecutionDescriptor
                             |
                             v
                  fresh Protos Process
                             |
                       fresh RootActor
                             |
             Actors / Futures / P / I/O
                             |
                             v
                   semantic outcome
                             |
                             v
                 structured attempt result
                             |
                 events + output artifacts
                             |
                             v
                   reporter / CI / UI
```

The key invariant is:

> **Physical placement may change. The semantic case contract does not.**

A local execution, an execution inside an amortized operating-system worker and a
future remote execution all run the selected exact entry in a fresh semantic
Protos Process with the same explicitly provisioned arguments, streams,
capabilities and production semantics.

## 1. Why the architecture should scale rather than fork

Many test systems accumulate separate execution paths over time:

```text
local test runner
CI sharding scripts
special flaky-test runner
remote executor
GPU runner
integration-test runner
watch runner
```

That makes correctness depend on where a test happens to run.

Protos should instead scale by composing independent layers:

```text
selection
planning
scheduling
placement
execution
aggregation
reporting
```

The low-level exact-entry executor must not know whether its caller is running a
single developer test, a CI shard, a retry, a remote attempt, a benchmark
correctness gate or another tooling flow.

This is the main future-proofing rule for TOOL002-B.

## 2. Expanded systems comparison

The follow-on analysis emphasizes scale and execution topology rather than
framework API style.

| System / family | Scaling lesson | Protos consequence |
|---|---|---|
| pytest + pytest-xdist | Worker processes can scale across CPUs/hosts, but every worker performs full collection and high-scope fixtures may execute per worker. | Do not require worker rediscovery. Build one inert TestPlan that can travel. Avoid hidden global mutable fixture semantics. |
| cargo-nextest | Process-per-test provides killability, output isolation, retries, sharding and resource groups. | Preserve those benefits as policy, but use fresh semantic Protos Processes as the normal unit and amortized OS workers only when physical containment is needed. |
| Go `go test` | Package-level parallelism and in-test `T.Parallel` are separate multipliers; excessive inner parallelism can degrade performance. | Outer test scheduling must account for nested Actor/P work rather than treating `jobs=N` as the entire CPU model. |
| Gradle/JVM runners | A pool of forked JVM workers amortizes startup; `forkEvery=1` is explicitly expensive. | Separate semantic Process lifetime from physical worker lifetime. Reuse physical workers while keeping every case a fresh Protos Process. |
| JUnit Jupiter | Concurrent execution composes with named READ/READ_WRITE resource locks and isolated cases. | Resources need more than a Boolean “parallel/not parallel” bit; access mode and scheduling consequence belong in policy. |
| Node test runner | Process isolation is the default at file granularity and bounded child-process concurrency supplies strong containment. | Physical process containment is useful but need not be one OS process per Protos case. |
| Erlang/Elixir ExUnit | Native lightweight isolation plus scheduler-aware `max_cases` and reproducible seeds scales cheaply inside one VM. | Prefer the native semantic isolation unit. Measure Protos Process cost before claiming BEAM-like cardinalities. |
| CTest | `PROCESSORS`, resource locks/groups, affinity and OS-process timeouts model weighted tests and scarce resources. | Generalize scheduling to capacity vectors and explicit resource allocation; hard timeout belongs to a killable physical boundary. |
| Bazel Remote Execution | Large-scale actions are inert, reproducible descriptions with declared inputs/platform constraints, CAS and remote action execution. | Keep TestPlan/ExecutionDescriptor serializable and host-neutral. Do not cache until hermetic inputs/capabilities can be fingerprinted. |
| Remote Execution API | A server is not required to provide at-most-once execution; the same Action may execute redundantly, even in parallel. | Distributed test attempts cannot assume exactly-once side effects. Attempt identity and replay safety must remain explicit. |
| Buck2 | Test runner policy is separate from local/remote execution and local resources can be managed as pools. | Keep test policy out of the backend. Resource allocation is a scheduler/backend composition problem. |
| Pants | Native outer concurrency may be combined with pytest-xdist inner concurrency, with concurrency adjusted to avoid multiplying both levels blindly. | Outer Process scheduling and inner P/Actor concurrency need one capacity budget or conservative accounting. |
| Nx | Affected analysis and caching remove work before distribution; remaining tasks can be assigned dynamically using dependencies/history. Per-machine non-parallel constraints do not imply cluster-global serialization. | Scale first by selection/cache when safe, then dynamic scheduling. Resource constraints need an explicit locality/scope. |

No one system is copied wholesale. The value of the comparison is the set of
boundaries that recur across systems built for different languages and scales.

## 3. Semantic Process isolation remains the normal case boundary

The selected ordinary isolation remains:

```text
one logical case attempt
        |
        v
fresh Protos Process
        |
        v
fresh RootActor
```

A Protos Process is already a semantic execution, isolation and failure domain.
It is not normatively an operating-system process. That makes it the correct
language-level test boundary even if many Processes are hosted inside one JVM or
future native runtime.

The case therefore starts with clean Process-local state:

- fresh RootActor;
- Process lifecycle/failure state;
- application-argument/environment bootstrap snapshots;
- private standard-stream bindings and Encoding associations;
- only the explicit capabilities provisioned for that execution;
- independently initialized Actor-local module instances and mutable state.

A test remains free to exercise real production semantics inside that Process:

```text
Process case-A
    +-- Future/task work
    +-- Actors
    +-- ActorGroups
    +-- Filesystem / I/O capabilities
    `-- isolated P computations
```

The runner must not replace these facilities with a test-only scheduler or fake
concurrency model.

## 4. P is inner computation, never the general test sandbox

`Closure.parallel(...)` / P is intentionally an isolated CPU-parallel
computation domain. It does not inherit Process, Actor, I/O or ambient resource
authority and it has no persistent test-case identity.

Therefore this is rejected:

```text
TestPlan
  -> P(test A)
  -> P(test B)
```

The correct composition is:

```text
scheduler
  -> Process test A
       -> may use P internally

  -> Process test B
       -> may use Actors internally
```

This preserves the ability to test Process, Actor, Group, Future, I/O and P
itself.

## 5. Separate semantic isolation from physical containment

Two different problems must never be conflated.

### Semantic isolation

Normal case execution requires a fresh Protos Process.

### Physical containment

Hard recovery from:

- non-suspending infinite execution;
- runtime/JVM/native crash;
- corrupted host process;
- hard memory limit;
- externally enforced deadline;

requires a physical boundary that the controller can terminate.

The selected future topology is:

```text
OS worker 1
    +-- fresh Protos Process A
    +-- fresh Protos Process B
    `-- fresh Protos Process C

OS worker 2
    +-- fresh Protos Process D
    `-- fresh Protos Process E
```

The physical worker is deliberately amortized across multiple semantic Processes.
This keeps startup/JIT/toolchain costs out of the per-test contract.

An implementation may later recycle a worker after a bounded number of cases,
memory pressure, suspicious diagnostics or policy-defined contamination without
changing semantic Process identity.

## 6. Execution backends are replaceable mechanism

The architecture allows at least three backend classes:

```text
LOCAL_FAST
    fresh Processes hosted directly in the current runtime

LOCAL_HARDENED
    fresh Processes hosted in amortized killable OS workers

REMOTE
    fresh Processes hosted by remote workers / distributed runtime capacity
```

Only the local backend is needed by the initial TOOL002 slices.

The backend boundary must remain test-neutral. A backend receives an exact
mechanical execution descriptor and produces a structured execution result. It
must not interpret assertions, manifests, tags, flaky policy, CaseIds or human
reporting.

Future backend selection is scheduling/placement policy, not Protos language
semantics.

## 7. Identity model: run, case, variant and attempt

A single stable CaseId is not sufficient once retries, platform matrices or
distributed uncertainty exist.

The conceptual hierarchy is:

```text
RunId
  one logical invocation / suite run

CaseId
  stable logical identity of the test case

VariantId
  concrete execution variant
  e.g. platform/runtime/configuration/toolchain mode

AttemptId
  one physical/temporal attempt of that case variant
```

Examples of variants may include:

```text
linux-x64 / interpreter
linux-x64 / truffle
windows-x64 / truffle
feature-set-X
```

These are architecture concepts, not selected public types or textual formats.

### Required properties

- `CaseId` is stable independently of physical scheduling order.
- `VariantId` distinguishes materially different execution contracts.
- `AttemptId` is fresh for each retry/replay/re-execution attempt.
- `RunId` groups the evidence of one logical run.
- physical worker identity is diagnostic/placement metadata, not case identity.

A retry never overwrites the earlier attempt.

## 8. Semantic outcome and infrastructure outcome are separate planes

A distributed or hardened runner must not map every host problem to a Protos
failure.

Conceptually an attempt result contains two independent categories.

### Semantic outcome

Examples:

```text
completed with value
failed with Protos Error
cancelled through defined semantic cancellation
Process terminated through semantic lifecycle
```

### Infrastructure outcome

Examples:

```text
not started
worker unavailable
worker crashed
worker lost / reachability unknown
deadline enforcement
artifact transport failure
scheduler cancellation
resource unavailable
```

Exact enum names are deliberately not selected.

The invariant is:

> **Infrastructure uncertainty is not a fabricated semantic Protos Error.**

In particular:

```text
worker lost != test failed
```

This mirrors the Protos distributed-runtime distinction between authoritative
termination knowledge and mere unreachability/unknown state.

## 9. Exactly-once execution must not be assumed

Remote/distributed execution systems cannot in general promise that an action is
physically executed exactly once. The Remote Execution API explicitly permits a
server to execute one requested Action redundantly, including in parallel.

That matters for tests with external side effects.

A worker may:

```text
start case X
perform external effect
lose controller connectivity
```

The controller may not know whether the attempt:

- never started;
- is still running;
- completed;
- completed the external effect but lost the result;
- crashed after a partial effect.

Therefore future replay/retry policy must distinguish **replay-safe** from
capabilityful/side-effecting cases.

No initial TOOL002 retry policy is selected, but the architecture must preserve
attempt evidence so such a policy can be correct later.

## 10. TestPlan must be inert, serializable and streamable

The TestPlan is logical test-tool data, not a live runtime object graph.

It must not require transporting:

```text
Closure
ActorRef
Process
Filesystem capability
moduleContext
live Java object
worker handle
```

Conceptually a CaseSpec may describe:

```text
stable CaseId
exact executable / module identity
entry
arguments
expectation-policy reference
variant constraints
resource requirements
capability profile
scheduling hints
```

The exact representation remains ordinary Test Tool policy.

### Serializable

A plan or selected portion of it must be capable of crossing a Process/worker/
machine boundary without requiring the destination to rediscover the suite.

### Streamable

Logical plan order does **not** require millions of CaseSpec records to be
materialized simultaneously in controller memory.

A conforming future implementation may page or stream plan records while
preserving:

- stable identity;
- deterministic filtering;
- deterministic seeded ordering where selected;
- deterministic shard membership;
- independent reporting identity.

This matters when the test count becomes very large.

## 11. Worker rediscovery is not the selected distributed model

pytest-xdist demonstrates a valid framework-driven approach in which every worker
performs full collection and the controller verifies that workers discovered the
same tests in the same order.

Protos intentionally selects a different long-term boundary:

```text
manifest / discovery producer
        |
        v
inert TestPlan
        |
        v
workers execute selected CaseSpecs
```

A worker may resolve exact preselected execution inputs, but it does not
independently decide what the suite contains.

This reduces duplicated discovery cost and avoids making worker-local fixture or
plugin state part of case identity.

## 12. Massive parallelism is a capacity problem, not a worker-count problem

There are at least three parallelism layers:

```text
suite parallelism
    many case Processes

        x

Actor parallelism
    Actors inside each case

        x

P parallelism
    isolated CPU work inside each case
```

A naive `jobs = availableProcessors` policy can oversubscribe badly when each
active test itself creates Actors or P work.

The selected scheduler model is therefore capacity accounting, not merely a
number of worker threads.

Conceptually:

```text
execution capacity
    cpu slots
    memory budget
    Process slots

named/scarce resources
    gpu
    simulator
    database lease
    fixed port
    device
```

A case may consume more than one unit of a resource.

The exact initial `jobs=auto` formula remains open and must be benchmarked.

## 13. Resources need quantity, access mode and locality/scope

The prior capacity model is refined further.

A declaration such as:

```text
db:integration capacity = 1
```

is ambiguous in distributed execution unless its scope is known.

The architecture must be able to distinguish conceptual scopes such as:

```text
case-private
worker-local
host-local
Node/local-runtime scope
Run-local
Cluster/distributed-run scope
external-global
```

Examples:

```text
temporary directory     private per case
TCP port 8080           host-local capacity 1
GPU                      host/worker-local capacity N
integration DB pool      Run/Cluster capacity N
hardware device          external-global capacity 1
commercial license       external-global capacity N
```

Access semantics may also matter:

```text
READ
READ_WRITE / exclusive
quantity N
```

The exact syntax is not selected.

### Why this matters

A “non-parallel” flag that only serializes work on one machine is insufficient
for a distributed resource that is globally exclusive.

Conversely, a host-local port conflict should **not** serialize otherwise
independent tests running on different hosts.

## 14. Resource allocation should yield capabilities, not ambient authority

The scheduler may know that a case requires a resource category. The test should
receive only the concrete authority allocated to that attempt.

Conceptually:

```text
scheduler allocates database lease
        |
        v
backend provisions explicit database capability/config
        |
        v
fresh Process receives only that authority
```

This is a strong fit for Protos: capability confinement can make resource
allocation and test authority the same architecture rather than two unrelated
systems.

Case-private capabilities remain preferable to shared mutable suite fixtures.

## 15. Hermetic and capabilityful tests are both valid

The architecture should not pretend every test can be hermetic.

### Hermetic case

Conceptually:

```text
exact code/package closure
exact toolchain/runtime variant
declared environment
private filesystem/input tree
no undeclared external authority
```

Such a case is a candidate for:

- remote execution;
- safe replay;
- input fingerprinting;
- result caching.

### Capabilityful case

Conceptually:

```text
database
network/service authority
hardware
external filesystem
other side-effecting service
```

Such a case may require:

- placement constraints;
- resource leases;
- no result caching;
- explicit replay policy;
- stronger uncertainty reporting.

Caching/remotability/replayability should eventually be derived from the actual
declared execution contract as much as possible, not from an optimistic
`@hermetic` label alone.

## 16. Result caching is downstream of hermeticity

Stable CaseIds are necessary for a useful cache but are not sufficient.

A safe test-result cache needs an execution fingerprint covering the relevant
inputs, for example:

```text
case identity
variant/toolchain identity
exact code/package closure
declared environment
declared input artifacts
relevant capability/resource contract
selected execution policy that changes outcome
```

A case with undeclared external authority must not become cacheable merely because
its source file is unchanged.

No initial TOOL002 result cache is selected.

## 17. The fastest massive test is the test not executed

At small scale, scheduler throughput dominates.

At very large scale, selection becomes equally important:

```text
all known cases
    |
    v
affected/dependency selection
    |
    v
cases whose inputs may have changed
    |
    v
safe cache lookup
    |
    v
cache misses
    |
    v
scheduler / execution
```

Future affected analysis belongs above TestPlan construction or as a plan filter
fed by an explicit dependency graph.

It must not become hidden behavior inside the exact-entry executor.

## 18. Deterministic sharding and dynamic scheduling should coexist

Static deterministic sharding is useful for:

- simple CI;
- reproducibility;
- independent shard retry;
- external orchestration.

But fixed shards suffer from stragglers when test durations vary.

The future scheduler should be able to support both conceptual modes.

### Deterministic shard selection

```text
stable CaseId
    -> deterministic partition
```

### Dynamic assignment

```text
ready CaseSpecs
    -> available capacity
    -> history-assisted placement
```

Historical duration may improve physical placement but must never change case
semantics, identity or pass/fail meaning.

Dynamic work assignment or work stealing is a scheduling optimization, not a new
test model.

## 19. Output capture must scale beyond in-memory byte arrays

Private stdout/stderr remains selected, but a massive runner cannot assume all
captured output stays in controller memory.

Conceptually:

```text
private stream
    |
    v
bounded capture
    |
    +-- small -> inline
    |
    `-- large -> spill/artifact store
                   |
                   v
              digest/reference
```

A structured result may therefore carry:

- bounded inline text/bytes;
- truncation metadata;
- artifact references;
- content digests;
- stream byte counts.

Exact storage and machine-report formats remain open.

Human reporter policy remains outside the executor.

## 20. Fixtures should not defeat Process isolation or distribution

Broad hidden mutable fixtures make distribution difficult.

Future fixture categories should preferentially map to:

### Case-private setup

Created inside the fresh case Process.

### Immutable/prepared artifact

Produced once and supplied as immutable input where physically shareable.

### Expensive external resource

Managed as an explicit leased resource/capability.

### Suite preparation

Produces artifacts or explicit resource/capability descriptions rather than
sharing a live mutable Protos object graph between case Processes.

A future fixture API may offer ergonomic scopes, but the semantic implementation
must preserve explicit authority and the scheduling consequences of sharing.

## 21. Alignment with Protos Node / Cluster architecture

The Protos distributed runtime already separates physical host topology from
logical Node identity and defines a Node as a scope capable of coordinating
Processes, placement and resources.

That makes a future Protos-native remote backend conceptually natural:

```text
test scheduler/controller
        |
        v
available execution capacity
        |
   +----+----+
   |         |
 Node A    Node B
   |         |
 Process   Process
 case 1    case 2
```

However TOOL002 must **not** invent private Core semantics for:

- failure detection;
- Cluster membership;
- split-brain resolution;
- downing;
- fencing;
- global Authority.

The current distributed specification deliberately keeps important policy and
knowledge boundaries explicit.

A future distributed Test Tool backend may use runtime/host facilities only
through their defined contracts.

## 22. Remote Execution API compatibility is an option, not a commitment

Bazel's Remote Execution API and Buck2 demonstrate a useful generic separation:

```text
content-addressed inputs
repeatable Action
platform constraints
worker execution
structured ActionResult
```

A future hermetic Protos CaseAttempt could potentially be translated to an
REAPI-like action without making REAPI part of Protos semantics.

Capabilityful Protos cases may instead require a Protos-aware backend.

The architecture therefore preserves the possibility of:

```text
generic hermetic remote backend
Protos-aware capabilityful remote backend
```

without selecting either implementation now.

## 23. Infrastructure uncertainty and replay

When remote reachability is lost, the controller may not have authoritative
knowledge of what the worker executed.

The attempt record must therefore be able to preserve infrastructure states such
as unknown/lost separately from the semantic outcome.

Automatic replay after uncertainty is allowed only by future policy that can
justify it.

A later successful retry does not erase the uncertain attempt.

This is especially important for external side effects.

## 24. Structured events should precede human formatting

At scale, one terminal line per case is insufficient for tooling and CI.

The eventual runner should have an internal structured event/result model that
can represent at least:

```text
run started/finished
case/variant selected
attempt queued/started/finished
semantic outcome
infrastructure outcome
captured-output metadata
resource allocation metadata
timing
worker/backend diagnostics
artifact references
retry/replay relation
```

Exact event schema is open.

The CLI reporter is a consumer of these facts, not their semantic owner.

## 25. Scalability stages

The architecture should support growth without changing the case contract.

### Stage 1 — small local suite

```text
one runtime
sequential or low-concurrency fresh Processes
```

### Stage 2 — large workstation

```text
one runtime
many fresh Processes
capacity-aware scheduler
```

### Stage 3 — hardened CI

```text
several OS workers
each worker hosts many fresh Processes
worker recycling / hard containment
```

### Stage 4 — distributed massive suite

```text
serializable/streamable TestPlan
many remote workers
dynamic capacity-aware assignment
artifact/output pipeline
```

### Stage 5 — compute avoidance

```text
affected analysis
safe cache
distributed execution only for remaining work
```

The semantic test boundary remains a fresh Protos Process throughout.

## 26. Scale claims require benchmarks, not analogy

The architecture permits high cardinality but does not yet prove that the current
implementation can cheaply host enormous numbers of fresh Processes.

Do not claim “millions of test Processes” merely because Erlang/Elixir can host
large numbers of BEAM processes.

Before promoting massive-scale implementation work, benchmark at least:

| Benchmark | Question |
|---|---|
| Process churn | Cost of creating/terminating many fresh semantic Processes |
| Process concurrency | Memory/scheduler behavior with many live Processes |
| Tiny-case throughput | Runner overhead relative to very small tests |
| Mixed-duration suite | Scheduler behavior with many short cases and stragglers |
| Nested P | Oversubscription from outer cases x inner P |
| Actor-heavy cases | Interaction with production Actor scheduling |
| Output storm | Memory/artifact behavior when many cases emit large logs |
| Resource contention | Capacity accounting, fairness and utilization |
| Deterministic replay | Same plan/seed/shard selects the same logical cases |
| Worker recycling | Semantic isolation remains intact across reused OS workers |
| Worker loss | Infrastructure uncertainty never fabricates semantic failure |
| Remote latency | Controller/worker throughput and batching requirements |

If fresh Process overhead is too high, optimize the Process implementation while
preserving its semantics before weakening ordinary test isolation.

## 27. Consequences for TOOL002-B

TOOL002-B is CLOSED by the local general fresh-Process execution mechanism.

The published implementation boundary is:

```text
ExactExecutionDescriptor
        |
        v
local general execution backend
        |
        v
fresh Protos Process
        |
        v
fresh RootActor
        |
        v
ordinary production execution
        |
        v
inert execution outcome
```

TOOL002-B must:

1. remain test-neutral;
2. create a genuinely fresh semantic Process/RootActor;
3. receive an exact already-selected module/execution plan and entry;
4. provision exact arguments;
5. provision only explicit capabilities;
6. provision independent standard streams/Encodings;
7. run ordinary production execution to a semantic terminal result;
8. return inert outcome data;
9. avoid global mutable executor state that would prevent concurrent independent
   executions;
10. avoid embedding physical worker placement into semantic identity;
11. avoid an API shape that requires output to remain permanently in memory.

TOOL002-B does **not** implement:

```text
TestPlan
RunId / CaseId / VariantId / AttemptId policy
parallel scheduler
resource allocator
sharding
remote execution
OS workers
retry
hard timeout
cache
affected analysis
human reporter
```

Those are layers above or around the executor.

The purpose of recording them now is to prevent B from choosing a local-only
mechanism that later makes those layers impossible.

## 28. Consequences for later TOOL002 work

The existing initial TOOL002 slice sequence remains valid.

Later work should incorporate these refinements when its scope arrives:

- TestPlan construction uses stable case identity and remains serializable;
- parallel scheduling uses capacity accounting;
- named resource constraints include locality/scope where necessary;
- output capture is bounded/artifact-friendly;
- retries preserve Attempt history;
- sharding and dynamic scheduling operate on CaseSpecs rather than executor
  internals;
- distributed execution keeps semantic and infrastructure outcomes separate.

Hard OS-worker containment, distributed workers, result caching, affected
analysis and large-scale artifact infrastructure remain separately promoted work
unless the parent TOOL002 scope is explicitly expanded later.

## 29. Decisions selected by this checkpoint

```text
fresh semantic Protos Process per ordinary attempt          YES
OS process per ordinary case                               NO
P as general test isolation                                NO
P/Actors/Futures/Groups usable normally inside cases       YES

logical plan separate from physical execution              YES
TestPlan concept inert/serializable                        YES
TestPlan implementation may stream/page at large scale     YES
worker-local full rediscovery required                     NO

Run / Case / Variant / Attempt identity layers             YES conceptually
attempt evidence preserved across retries/replays          YES
worker identity is not case identity                       YES

semantic outcome separate from infrastructure outcome      YES
worker lost/unknown fabricated as semantic failure         NO
exactly-once distributed execution assumed                 NO

replaceable local/hardened/remote backend architecture     YES
only local backend required initially                      YES
amortized killable OS worker layer allowed later           YES
remote backend allowed later                               YES

scheduler based on capacity rather than worker count only  YES
resource quantity/capacity                                 YES
resource access mode may matter                            YES
resource locality/scope is required conceptually           YES
private capabilities preferred over shared mutable state   YES

static deterministic sharding may coexist with dynamic     YES
duration history may influence placement only              YES
affected analysis belongs above execution                  YES
cache requires explicit hermetic/fingerprint model         YES
all tests assumed cacheable                                NO

private output capture                                     YES
capture must be capable of bounded/artifact-backed form    YES
human formatting owned by low-level executor               NO

remote execution protocol selected now                     NO
public worker protocol selected now                        NO
new Core testing/distribution semantics                    NO
```

## 30. Decisions deliberately still open

```text
exact RunId/CaseId/VariantId/AttemptId representation
exact TestPlan/CaseSpec serialization format
whether the first implementation materializes or streams plans
machine-readable event/report schema
initial jobs=auto formula
capacity units and scheduler fairness policy
resource declaration syntax
resource scope spelling and exact scope taxonomy
READ/READ_WRITE or richer resource-access vocabulary
worker recycling policy
hard-timeout CLI/policy
OS worker protocol
remote worker protocol/provider
REAPI compatibility/adapter implementation
retry/replay policy
replay-safety declaration/derivation
deterministic shard algorithm
dynamic scheduler algorithm
duration-history persistence
affected dependency graph/source
hermetic execution fingerprint
result-cache format/provider
artifact store/protocol
output inline/spill thresholds
fixture/helper API
```

None of these open decisions blocked TOOL002-B, and none is introduced by its closure.

## Official references reviewed

### pytest / pytest-xdist

- https://pytest-xdist.readthedocs.io/en/stable/how-it-works.html
- https://pytest-xdist.readthedocs.io/en/stable/how-to.html

Key scaling evidence: workers perform full collection; controller verifies the
same collected tests/order; high-scope fixtures execute independently per worker
unless separately coordinated.

### cargo-nextest

- https://nexte.st/docs/design/why-process-per-test/
- https://nexte.st/docs/configuration/test-groups/
- https://nexte.st/docs/configuration/reference/
- https://nexte.st/docs/ci-features/partitioning/
- https://www.nexte.st/docs/machine-readable/list/

Key scaling evidence: physical process isolation enables killability and output
containment; groups provide mutual exclusion/rate limiting; deterministic
partitioning and machine-readable enumeration support CI scheduling.

### Go

- https://go.dev/src/cmd/go/internal/test/test.go
- https://pkg.go.dev/testing

Key scaling evidence: package-level and in-test parallelism are separate; the
Go tool warns that setting in-test parallelism above available CPU capacity can
degrade performance.

### Gradle / JUnit

- https://docs.gradle.org/current/userguide/java_testing.html
- https://docs.gradle.org/current/dsl/org.gradle.api.tasks.testing.Test.html
- https://docs.junit.org/5.14.2/writing-tests/parallel-execution
- https://docs.junit.org/5.14.0/api/org.junit.jupiter.api/org/junit/jupiter/api/parallel/ResourceLock.html

Key scaling evidence: forked JVM workers isolate the build process and can run in
parallel; restarting a JVM for every test class is expensive; JUnit resource
locks distinguish read and exclusive access.

### Node

- https://nodejs.org/api/test.html

Key scaling evidence: process-level test isolation runs test files in child
processes and bounds physical concurrency.

### Elixir / ExUnit

- https://hexdocs.pm/ex_unit/ExUnit.html

Key scaling evidence: the runner uses native runtime concurrency, scheduler-aware
maximum case counts and reproducible seeds.

### CTest

- https://cmake.org/cmake/help/latest/prop_test/PROCESSORS.html
- https://cmake.org/cmake/help/latest/prop_test/RESOURCE_LOCK.html
- https://cmake.org/cmake/help/latest/prop_test/RESOURCE_GROUPS.html
- https://cmake.org/cmake/help/latest/prop_test/TIMEOUT.html

Key scaling evidence: tests consume weighted processor slots and named resources;
hard timeout is implemented by killing the physical test process.

### Bazel / Remote Execution API

- https://bazel.build/docs/remote-execution
- https://github.com/bazelbuild/remote-apis
- https://github.com/bazelbuild/remote-apis/blob/main/build/bazel/remote/execution/v2/remote_execution.proto

Key scaling evidence: remote execution distributes test/build actions across
worker pools using declared inputs/platforms and content-addressed data. The
protocol explicitly does not require at-most-once execution of an Action.

### Buck2

- https://buck2.build/docs/users/remote_execution/
- https://buck2.build/docs/rule_authors/test_execution/
- https://buck2.build/docs/rule_authors/local_resources/

Key scaling evidence: test-runner policy is separated from local/remote execution
and expensive local resources can be represented as managed resource pools.

### Pants

- https://www.pantsbuild.org/stable/docs/python/goals/test

Key scaling evidence: outer Pants concurrency and inner pytest-xdist concurrency
must be coordinated rather than blindly multiplied.

### Nx

- https://nx.dev/docs/concepts/ci-concepts/parallelization-distribution
- https://nx.dev/docs/features/ci-features/distribute-task-execution
- https://nx.dev/docs/features/ci-features/affected
- https://nx.dev/docs/reference/project-configuration

Key scaling evidence: affected analysis and caching remove work before execution;
remaining tasks can be assigned dynamically; per-machine parallelism constraints
are distinct from distributed capacity.

## Conclusion

The Test Tool can scale without redefining what a test means.

The durable abstraction is not “one thread”, “one JVM”, “one worker” or “one
machine”. It is:

```text
stable logical case attempt
        |
        v
exact execution contract
        |
        v
fresh semantic Protos Process
        |
        v
structured semantic + infrastructure evidence
```

Everything around that contract — local concurrency, hardened workers, remote
placement, sharding, dynamic scheduling, affected selection, caching and artifact
transport — is replaceable policy/mechanism.

That separation is the architecture that TOOL002-B must preserve.
