# D076 — Test Tool resource reservation and capability provisioning architecture

Status: **RATIFIED — Candidate D selected**

Allocated: **2026-09-11**

Explicit project-owner approval: **2026-09-11**

Decision issue: GitHub #359

Follow-up representation decision: `D077` / GitHub #361

Nature: implementation-independent Test Tool resource scheduling and capability-provisioning architecture

Triggered by: `TOOL002-I` / GitHub #96 after TOOL002-H closure.

Primary consumer: `TOOL002-I`

Normative language effect: **none**.

## Decision boundary

D076 decides the durable architecture connecting:

1. one case's logical resource requirements;
2. run/placement-visible resource capacity and locality;
3. scheduler admission/reservation;
4. materialization of the actual resource lease/capability;
5. authority provisioned to the fresh child Process; and
6. cleanup plus infrastructure-failure reporting.

It deliberately does **not** select concrete manifest/config syntax, resource-key
carrier, TestPlan tuple/Array/Map layout, resource catalog file format, CLI
configuration, fairness, priority, retry, timeout/kill, sharding, remote protocol,
CAS, `jobs=auto`, physical worker topology or provider implementation.

Those representation questions are owned by D077 / GitHub #361.

## Ratified architecture — two planes

The selected architecture is:

```text
inert CaseSpec / TestPlan requirement
        |
        v
bundled-Protos scheduler admission
        |
        v
run / placement resource catalog
        |
        v
atomic reservation
        |
        v
environment-owned provider
        |
        v
attempt-private lease / capability
        |
        v
fresh Protos Process / RootActor
        |
        v
terminal cleanup and release
```

The TestPlan carries stable logical requirements only. The run or placement
environment owns available capacity, scope/locality and the provider that can
materialize the concrete authority after reservation and placement.

No live capability, Java/JVM object, OS handle, ActorRef, worker identity,
provider instance or lease enters the inert TestPlan.

## Required invariants

### Requirements remain inert

A case may carry zero or more logical resource requirements.

Requirements must remain suitable for future serialization/streaming and must not
capture a physical worker, host object or live capability.

### D069 jobs remains independent

D069 `jobs` remains global logical Test Tool execution-slot capacity.

A case starts only when both are satisfied:

```text
one available jobs admission slot
AND
the case's complete resource requirement set
```

Resource reservations neither replace nor redefine `jobs`.

### Atomic full-set reservation

One case's complete resource set is reserved atomically before execution starts.

The Test Tool must not expose a state where one case holds a proper subset of its
required scarce resources while waiting for the rest.

This prevents guest-visible hold-and-wait acquisition order from becoming a test
semantic and removes the principal scheduler-level deadlock mode for multiple
resource requirements.

### Initial conflict model

The selected bounded semantic model is:

```text
shared(resourceKey, positiveUnits)
exclusive(resourceKey)
```

A shared reservation consumes positive capacity units and may coexist while the
resource pool has sufficient capacity.

An exclusive reservation requires sole reservation of that logical resource pool
for the attempt.

These names are conceptual semantics only. D076 does not select the final public
spelling or manifest carrier.

Read/write authority is **not** inferred from scheduling mode. Actual functional
authority belongs to the capability provisioned to the child Process.

### Scope and locality belong to the catalog

Scope/locality belongs to the resource definition in the environment-owned
catalog rather than being independently repeated or overridden by each CaseSpec.

The architecture must remain capable of distinguishing concepts such as:

```text
case-private
worker-local
host-local
run-local
cluster/run-global
external-global
```

D076 selects the ownership boundary, not these exact public enum names.

### Provision after placement

A live resource capability or lease is created only after:

1. the case is admitted;
2. its complete requirements are reserved; and
3. the applicable placement is known.

This prevents a logical TestPlan from capturing an authority belonging to the
wrong host/worker/runtime.

### Explicit Process authority

The fresh child Process receives only the capabilities explicitly provisioned for
that attempt.

No guest-global `ResourceManager`, mutable cross-Process resource registry or
ambient hidden resource authority is selected.

### Custody through terminal cleanup

Resource reservation and live-lease custody continue through terminal attempt
cleanup, not merely until guest code produces a value/error.

This composes with D055's already-ratified rule that accepted host work remains
session-owned through terminal cleanup.

### Infrastructure outcome remains distinct

Examples such as:

```text
resource unavailable
provider failed
worker unavailable
allocated device lost
lease cleanup failed
placement disappeared
```

are infrastructure outcomes.

They are not automatically fabricated into semantic Protos `Error` values in the
child Process.

## Relationship to D055, D069 and PLAT023

D055 assigns scheduling/admission/resources/aggregation policy to bundled Protos
while host code owns exact execution mechanism.

D076 preserves that boundary. The provider boundary may perform environment-
specific mechanism, but it does not gain a second independent TestPlan admission
queue or resource scheduling policy.

D069 remains the public global execution-slot capacity.

PLAT023 remains a replaceable physical carrier mechanism for already-admitted
exact execution and must not reinterpret resource requirements or impose a
second resource scheduler.

## Comparative evidence

The project-owner approval followed exhaustive comparison across language test
frameworks, CI systems, build/test orchestrators, HPC schedulers and distributed
execution systems.

### Swift Testing

Swift Testing demonstrates a small logical parallel/serialized policy over
ordinary language concurrency.

Useful lesson: user-facing scheduling policy should not expose carrier threads.

Limit: Boolean overlap control does not model capacity, locality or capability
provisioning.

### Go testing

Go separates outer test scheduling from concurrency exercised inside a test.

Useful lesson: D069 jobs and inner Actor/Future/P concurrency must remain
independent.

Limit: external scarce-resource planning is outside the test scheduler model.

### Elixir ExUnit

ExUnit uses the language runtime's lightweight isolation plus global concurrency
and serial groups.

Useful lesson: preserve the language's native semantic isolation unit; Protos
should keep one fresh Process/RootActor per case.

Limit: groups act primarily as mutexes and do not model resource quantity,
placement or provisioning.

### pytest-xdist

xdist provides worker distribution and affinity/grouping.

Useful lesson: placement and logical test identity are separable.

Limit: assigning work to one worker is not a resource reservation model.

### xUnit / NUnit / MSTest / Gradle

These systems provide mature process/worker isolation, concurrency ceilings and
serialization controls.

Useful lesson: semantic isolation and physical containment are separate layers.

Limit: worker counts and serial annotations do not model heterogeneous scarce
resources.

### GitHub Actions / GitLab resource groups

Concurrency/resource groups provide distributed mutual exclusion.

Useful lesson: named logical exclusion can work without exposing a physical
mutex.

Limit: binary exclusion alone scales poorly to weighted resources, multiple
instances and locality.

### JUnit ResourceLock

JUnit demonstrates that conflict semantics can be more expressive than one
global parallel/not-parallel switch.

Useful lesson: resource compatibility belongs in scheduler-visible policy.

Not copied: Java READ/READ_WRITE names conflate a locking convention with
functional authority. D076 instead separates scheduling compatibility from the
actual capability granted to a Process.

### cargo-nextest

nextest composes global execution capacity, per-test `threads-required` weight and
named test-group capacity.

Useful lesson: global jobs capacity and local/scarce resource budgets are
orthogonal and composable.

Limit: test groups/weights do not by themselves identify physical resources,
scope/locality or provision capabilities.

### CTest resource allocation

CTest separates per-test logical requirements from an external resource
specification describing actual resource instances and slots, reserves capacity
before execution and reports selected resource IDs to the child process.

This is the closest test-runner precedent for D076's two-plane model.

Protos improves the authority boundary by provisioning explicit capabilities
rather than requiring the test to interpret environment-variable resource IDs.

### Slurm TRES/GRES

Slurm validates heterogeneous quantified resource accounting at HPC scale,
including CPUs, memory, GPUs, licenses and node-local resources.

Useful lesson: scalable scheduling is fundamentally a resource-vector/capacity
problem and locality is material.

Not copied: partitions, QoS, accounting and global cluster administration are
institutions far beyond TOOL002's requirement.

### Kubernetes Extended Resources and DRA

Kubernetes separates workload resource request, published resource inventory,
scheduler placement/allocation and post-allocation device/resource preparation.

DRA makes this split especially explicit through claims, resource inventory and
drivers.

Useful lesson: logical requirement, physical inventory, allocation and concrete
access are separate concepts.

Not copied: API-server objects, controllers, DeviceClass/ResourceClaim
institutions and cluster-global control planes.

### Buck2

Buck2 separates test orchestration from replaceable local/remote execution
mechanisms and can use provider/resource information without making the test
description itself own host-specific live authority.

Useful lesson: high-level scheduling policy can remain backend-neutral and
survive execution-backend replacement.

This aligns strongly with D055 and D076.

### Bazel Remote Execution API

Remote Execution separates inert action/platform requirements from physical
workers and keeps infrastructure states such as resource exhaustion,
unavailability and cancellation distinct from program execution results.

Useful lesson: future remote placement must not force resource/worker failures
into semantic guest Errors; physical exactly-once execution also cannot be
assumed.

## Candidate comparison

Scores 1–5.

| Candidate | Correctness | Protos | Future | Scale | Simplicity | Portability | Cost | Failure/operability | Reversibility | Evidence | Total |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| A groups/mutexes | 4.0 | 4.0 | 3.0 | 3.0 | 5.0 | 4.5 | 5.0 | 4.0 | 3.5 | 4.5 | 40.5 |
| B capacity vector only | 4.5 | 4.5 | 4.0 | 4.5 | 4.5 | 5.0 | 4.5 | 4.0 | 4.0 | 5.0 | 45.0 |
| C capability-only | 4.0 | 4.5 | 4.0 | 3.5 | 4.0 | 4.5 | 4.0 | 3.0 | 3.5 | 4.0 | 39.0 |
| **D two-plane requirements + catalog/provider** | **5.0** | **5.0** | **5.0** | **5.0** | 4.0 | **5.0** | 4.5 | **5.0** | 4.5 | **5.0** | **48.0** |
| E guest-global ResourceManager | 3.0 | 2.0 | 2.5 | 2.5 | 3.5 | 2.5 | 4.0 | 2.0 | 2.0 | 4.0 | 28.0 |

Focused project-owner criteria:

| Candidate | Future resilience | Scalability | Protos philosophy |
| --- | ---: | ---: | ---: |
| A groups/mutexes | 2.5 | 2.5 | 4.0 |
| B capacity vector | 4.0 | 4.5 | 4.5 |
| C capability-only | 3.5 | 3.0 | 4.0 |
| **D two-plane** | **5.0** | **5.0** | **5.0** |
| E guest registry | 2.0 | 2.0 | 1.5 |

## Why B is insufficient

A capacity vector is a strong scheduler model but does not answer:

- which physical resource instance is used;
- on which host/worker it exists;
- how the authority is provisioned to the Process;
- who owns cleanup; or
- how loss/provider failure is represented.

Answering those questions introduces a catalog/provider boundary, converging on
D.

## Why C is insufficient

A capability-only design provides explicit authority, but hides scarce capacity
from the scheduler until provisioning time.

That risks introducing a second admission queue inside providers and would weaken
D055/PLAT023's single scheduling-policy ownership.

## Why D is most Protos-aligned

D assigns each concept to a small, composable mechanism:

```text
TestPlan       -> what the case logically needs
Runner         -> when the case may start
Catalog        -> what capacity exists and where
Provider       -> how concrete authority is materialized
Capability     -> what authority the child Process actually has
Session        -> custody and release
```

It avoids a guest-global registry, preserves fresh Process isolation, leaves host
mechanism replaceable, pays provider/capability cost only for resourceful tests
and allows future local/remote backends without redefining CaseSpec semantics.

## Future stress test

### Millions of cases

Requirements remain inert and can travel with streamed CaseSpecs. No live
resource object is created until admission.

### Multiple scarce resources

Atomic full-set reservation avoids visible hold-and-wait and order-dependent
deadlock.

### Inner Actors / Futures / P

Outer jobs capacity and resource budgets remain independent.

### Hardened workers

Worker/host-local catalog scope can drive placement; capabilities are materialized
only after that placement.

### Remote execution

Logical requirements remain transportable; remote/provider-specific handles stay
outside the plan.

### Alternative runtime / post-Truffle

Only logical requirement/reservation/provisioning semantics are durable. Threads,
JVM handles and Truffle objects remain replaceable implementation details.

## Strongest argument against D

D contains more concepts than a mutex/group model and risks over-designing before
the project has many resourceful integration tests.

The selected mitigation is to ratify only the ownership boundaries and minimal
shared/exclusive reservation semantics. D077 separately decides concrete
representation only when needed by TOOL002-I. No cluster institution, remote
protocol or general-purpose resource subsystem is introduced by D076.

## What could make D regrettable?

If Protos tests permanently remain small, local and almost entirely resource-free,
D may prove richer than necessary.

The cost remains bounded because resource-free cases carry no live provider/lease
machinery and the ordinary H scheduler remains valid. The architecture does not
require every implementation to build distributed infrastructure.

A second possibility is a future execution backend whose native scheduler already
owns all resource placement. The escape path remains to adapt that backend as the
environment-owned catalog/provider while preserving the same inert logical
requirements.

## Intentionally deferred

D076 does not decide:

- manifest syntax;
- resource-key ordinary-value carrier;
- CaseSpec resource tuple/Array/Map representation;
- catalog file/config/CLI representation;
- public names for shared/exclusive forms;
- provider implementation API;
- fairness, priority or starvation policy;
- `jobs=auto`;
- retry/replay;
- hard timeout/kill;
- sharding;
- remote execution protocol;
- CAS;
- CPU/memory automatic accounting;
- physical worker topology.

The immediate representation decision is D077 / GitHub #361.

## Ratification effect

D076 ratification is governance/design only.

It changes no:

- executable production implementation;
- Protos specification;
- Maven implementation version;
- native boundary;
- D069 jobs contract;
- PLAT023 carrier topology.

TOOL002-I remains blocked until D077 selects the concrete ordinary Test Tool
representation needed to instantiate this architecture.
