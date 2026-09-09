# PLAT011 — RuntimeHost-owned shared Actor carrier substrate across local Processes

Status: **RATIFIED**

Nature: durable non-normative JVM/Truffle runtime architecture decision

Approved by project owner: **2026-09-09**

Primary consumers: Actor runtime hosting, PERF001-F blocker #239, future local multi-Process hosting and host capacity governance

Normative effect: **none**. Protos Process failure/authority identity, Actor
identity, mailbox semantics, P isolation, scheduling observability and
distributed placement remain unchanged. PLAT011 selects only where finite JVM
physical Actor-execution capacity is owned and shared.

GitHub coordination provenance: the ownership question emerged during the
exhaustive review of the carrier primitive and was initially recorded as a
D058/D059-style checkpoint in issue #241. Current project policy classifies this
as durable host/runtime architecture rather than language semantics. The
approved ownership decision is therefore durably allocated as PLAT011.

## Problem

PLAT010 selects bounded reusable platform carriers for normal Actor guest
execution. A second independent question determines whether that design scales
when one JVM runtime host contains many semantic Protos Processes:

```text
Who owns the finite physical carriers?
```

A CPU-sized pool per Process would scale physical platform threads approximately
as `Processes × configured_parallelism`. With 100 local Processes that implies
roughly 800 platform threads on 8 cores, 6,400 on 64 cores and 12,800 on 128
cores before accounting for unrelated runtime work. Process creation would
silently become host-thread allocation policy.

That is incompatible with the project goal that simple and abundant semantic
entities should not impose unused physical-runtime cost.

## Existing architecture retained

PLAT011 does not reopen the PLAT001 relation:

```text
one hosted Protos Process -> one multithread Polyglot Context
```

Multiple Process Contexts may share one `ProtosPolyglotRuntimeHost` / Engine
owner. A carrier selected to execute one Actor segment enters that Actor's exact
owning Process Context. Sharing physical carriers therefore does not share
Process authority, object identity, module state, failure state or Context
identity.

The following remain false:

```text
carrier == Actor
carrier == Process
carrier pool == Process
Polyglot Context == carrier
RuntimeHost == semantic Protos Node
```

## Selected architecture

The **`ProtosPolyglotRuntimeHost` capacity boundary owns one bounded normal Actor
carrier substrate shared by the local Processes it hosts**.

Durable constraints:

1. Physical normal-Actor carrier count is bounded by effective runtime CPU
   capacity, not by Actor count or Process count.
2. Multiple hosted Process Contexts may consume that shared physical capacity
   while retaining independent semantic failure/authority domains.
3. A carrier may execute Actor work for different Processes over time, entering
   the exact corresponding Context for each selected segment.
4. Process creation does not allocate a CPU-sized platform-thread pool.
5. Process termination does not own or terminate RuntimeHost carrier threads;
   it invalidates/removes its schedulable work under existing lifecycle rules.
6. No JVM-global static executor is the architecture owner. Separate embedded
   RuntimeHosts/tests must remain independently configurable and disposable.
7. Initial implementation may use one bounded fixed platform-thread executor per
   RuntimeHost as a backend mechanism.
8. The substrate abstraction must allow later replacement by long-lived
   carrier workers with local queues, sharding/work stealing, topology-aware
   placement and runtime capacity governance without changing Protos semantics.
9. Fair progress across busy hosted Processes must be preserved; one Process
   must not monopolize RuntimeHost carriers through an unbounded worker loop.
10. Host capacity/admission, weighted fairness, quotas or affinity remain
    implementation policy unless separately exposed by an approved Protos
    semantic capability.
11. Potentially blocking host work uses a separately bounded offload/dirty lane
    when necessary rather than expanding or virtualizing the normal CPU/guest
    carrier set.
12. P and Actor execution need not share one semantic scheduler or executor.
    Future host-level capacity governance may coordinate their physical CPU
    budgets without changing P isolation or Actor semantics.

## Cross-runtime evidence

The review found strong convergence across mature runtimes on the ownership
principle even though queue algorithms differ.

### BEAM

Finite scheduler threads are runtime resources, not resources owned by each
Erlang process. Huge process populations are multiplexed across scheduler run
queues, with migration/stealing and separate dirty work lanes.

### Go

`GOMAXPROCS`/`P` represents runtime execution capacity rather than goroutine
ownership. Large goroutine populations consume the same bounded capacity. This
is close to the PLAT011 distinction between semantic Process/Actor population
and host execution capacity.

### Tokio

A runtime owns its worker set; tasks do not each own pools. Worker-local queues
and stealing provide a natural future evolution from an initial fixed executor
without changing task semantics.

### Akka and Orleans

Default actor/grain execution uses shared dispatcher/ThreadPool infrastructure.
Dedicated thread ownership is exceptional rather than the scaling baseline.

### Pony, Swift and Kotlin

Pony runtime scheduler threads, Swift global/concurrent executors and Kotlin
CPU-bounded dispatchers similarly establish physical capacity above the logical
Actor/task population.

The common result is stronger than any one implementation API: **logical entity
cardinality must not determine physical thread cardinality**.

## Why RuntimeHost is the ownership boundary

`ProtosPolyglotRuntimeHost` already owns the shared Engine used to host multiple
Process Contexts and therefore represents one explicit implementation/runtime
lifetime without becoming a semantic Protos object. It is a better resource
boundary than:

- individual Actors, which are abundant semantic entities;
- individual Processes, whose failure/authority identity must not imply a
  CPU-sized thread allocation;
- `Core`/Prelude/Actor-protocol instances, whose construction identity is not a
  resource-governance model; or
- JVM-global static machinery, which would hide lifetime and interfere with
  multiple independently embedded RuntimeHosts.

RuntimeHost ownership also aligns the carrier lifetime with the host mechanism
that can later observe effective CPU/cgroup capacity and can reject close while
hosted execution remains live.

## Scalability

The architectural target is:

```text
physical normal Actor carriers = O(effective RuntimeHost CPU capacity)
```

rather than:

```text
O(Actors)
O(Processes × cores)
O(Core bootstraps × cores)
```

At small scale, an ordinary fixed executor is sufficient. At 64/128+ cores, the
current single locked ready-Actor structure is not assumed to remain optimal.
PLAT011 intentionally permits a future progression such as:

```text
bounded fixed executor
        -> long-lived carriers
        -> worker-local queues + stealing
        -> topology/NUMA-aware shards
```

This evolution must preserve Actor at-most-one-segment execution, mailbox/task
rules and Process Context routing.

## Fairness and many-Process behavior

Sharing finite physical capacity introduces an implementation fairness concern
that per-Process pools would hide by oversubscription. The solution is not to
return to per-Process pools; the RuntimeHost scheduler/substrate must instead
bound work quanta/submission so one continuously runnable Process cannot retain
all physical carriers forever.

PLAT011 does not prescribe weighted round robin, deficit scheduling, per-Process
queues or another exact algorithm now. It requires only that the implementation
remain capable of independent progress and that future tuning not alter
specified Actor ordering or create public scheduling promises accidentally.

## Blocking and I/O

Normal carriers are CPU/guest-execution capacity. Existing and future asynchronous
I/O should continue to use host I/O machinery that does not block normal Actor
carriers while waiting. If a host integration genuinely requires blocking work,
a distinct bounded blocking/dirty/offload lane is permitted and should retain
late-completion/cancellation custody under the owning semantic operation.

This follows the same broad pattern used by BEAM dirty schedulers and Tokio
blocking workers and prevents exceptional blocking behavior from dictating the
normal Actor execution model.

## Distributed and multiprocessing evolution

Distributed Actors do not require a new local execution universe. Each local
RuntimeHost/node can own finite physical capacity and multiplex its local hosted
Processes/Actors. Remote Actor routing, Process placement, supervision and
failure semantics remain semantic/distributed layers above that local substrate.

The same design therefore scales by composition:

```text
Node / host runtime
    +-- RuntimeHost -> bounded local carrier capacity
    |      +-- Process A -> Context A -> Actors
    |      `-- Process B -> Context B -> Actors
    `-- another RuntimeHost if the embedding deliberately creates one
```

No Java thread identity must cross a distribution boundary.

## Rejected alternatives

### CPU-sized pool per Process

Rejected because physical threads scale as `Processes × cores`, Process creation
implicitly allocates host scheduling resources, and many-Process embedding
oversubscribes the machine.

### Pool per Core/Prelude/Actor protocol

Rejected because that ownership follows current construction accident rather
than a durable resource boundary and can still multiply physical pools across
bootstraps.

### JVM-global static pool

Rejected because it hides authority/lifetime, prevents independent embedded
RuntimeHosts and makes testing/configuration/resource isolation difficult.

### Unbounded or virtual-thread normal carrier population

Rejected by PLAT010 for normal CPU/guest Actor execution. Logical concurrency is
already provided by Actors/Tasks/Futures; physical CPU capacity stays finite.

## Required implementation shape for #239

The first consumer should remain bounded:

- introduce RuntimeHost-owned platform carrier capacity;
- route the existing Actor scheduler's selected segments through it without
  changing Actor semantics;
- avoid an unbounded long-lived worker per Process;
- preserve Process-specific Context entry;
- close/shutdown carrier resources with RuntimeHost lifetime, not Process lifetime;
- add focused multi-Process evidence that two Processes share bounded physical
  capacity while retaining distinct Contexts; and
- satisfy PLAT010's repeated width-8 blocker reproduction before #239 closes.

This implementation evidence is a prerequisite for resuming retained PERF001-F
reference timing.

## Deliberately deferred

PLAT011 does not select exact effective-CPU detection, executor class, queue
layout, work-stealing algorithm, Process weights, host quota syntax, cgroup
refresh policy, thread affinity, NUMA partitioning, blocking-pool size, P/Actor
budget coupling, admission controls, distributed scheduler or public resource
API. Any new durable architecture or observable semantic choice exposed by those
topics requires its own approval gate.
