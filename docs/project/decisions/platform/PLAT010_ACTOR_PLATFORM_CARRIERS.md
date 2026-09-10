# PLAT010 — Bounded reusable platform carriers for normal Actor guest execution

Status: **RATIFIED**

Nature: durable non-normative JVM/Truffle runtime architecture decision

Approved by project owner: **2026-09-09**

Primary consumers: Actor runtime scheduler, PERF001-F blocker #239, and later hosted Actor execution work

Normative effect: **none**. Actor, Process, Task, Future, mailbox, isolation,
ordering and failure semantics remain owned by the Protos specification and
already-ratified language decisions. PLAT010 selects only the JVM host-carrier
primitive used to execute semantically eligible Actor guest segments.

GitHub coordination provenance: the investigation was initially recorded as a
D057/D058-style checkpoint in issue #240 while concurrent project work was
allocating language-design identifiers. Current `AGENTS.md` classifies this
choice as platform/runtime architecture because another conforming
implementation may use different host machinery without changing observable
Protos behavior. The approved carrier decision is therefore durably allocated
as PLAT010; the identifier correction does not reopen the approved architecture.

## Existing architecture retained

PLAT010 does not reopen PLAT001. The current JVM/Truffle hosting topology remains:

```text
shared Truffle Engine
    |
    +-- Process A -> one multithread Polyglot Context
    |                  +-- Actors
    |                  `-- P work
    |
    `-- Process B -> one multithread Polyglot Context
                       `-- ...
```

A carrier executing one Actor segment enters the exact owning Process Context
for that segment and leaves it afterward. Carrier identity is implementation
placement only and is never Actor, Process, Task, activation, authority or
failure-domain identity.

PLAT001's prohibitions remain in force:

- no Context per Actor;
- no Context per carrier;
- no global guest-execution lock/GIL that serializes unrelated Actors; and
- no hidden carrier identity or ThreadLocal replacing explicit Protos semantic state.

## Triggering evidence

PERF001-F canonical `concurrency/actor-fanout-requests` at measured Protos
revision `a08844c7ba59f4a213e4d318bcf3bee32393c2a9` exposed a host-runtime failure
while preparing retained reference evidence:

- correctness produced exact result `4096` at widths 1/2/4/8;
- ten fresh direct production launches at width 4 passed;
- ten reused-container direct launches at width 4 passed;
- the first direct production launch at width 8 exceeded a 120-second diagnostic guard; and
- an earlier fresh-startup attempt emitted repeated Truffle
  `[engine::attach]` mount/unmount callback failures on ForkJoin workers and
  terminated with `java.lang.InternalError: java.lang.StackOverflowError`.

A separate diagnostic reproduced the width-8 failure without the benchmark
`StartupDriver`, proving that the incident is not merely a benchmark-parent
process bug. The current `ProtosActorScheduler` default creates workers via
`Thread.startVirtualThread(...)`, so normal Actor scheduling adds a Loom M:N
mount/unmount layer underneath Protos's own Actor/task/mailbox scheduler and
around Truffle Context entry/leave.

This evidence motivates PLAT010 but does **not** by itself prove that replacing
virtual-thread carriers fixes #239. That hypothesis remains an implementation
gate.

## Selected architecture

Normal CPU/guest Actor execution uses a **bounded set of reusable JVM platform
carriers**.

Durable constraints:

1. Normal Actor guest execution is multiplexed over finite reusable platform threads.
2. Virtual threads are not the default Actor execution carrier.
3. Actor count, mailbox count and Future count do not determine host thread count.
4. At most one non-preemptive segment of one Actor incarnation executes at a time,
   preserving the existing Actor scheduler invariant.
5. Distinct semantically eligible Actors remain able to execute concurrently on
   distinct carriers up to admitted physical capacity.
6. Every hosted Actor segment still enters the exact owning Process's Polyglot Context.
7. Platform-thread identity remains unobservable Protos implementation machinery.
8. Exact ownership/sharing of the bounded carriers across multiple local
   Processes is selected separately by PLAT011.
9. The initial JVM backend may use a standard fixed platform-thread executor,
   but `ExecutorService`, ForkJoinPool or another Java executor class is not the
   durable architecture contract.
10. The carrier abstraction must preserve future migration to long-lived
    scheduler-owned workers, sharded queues, work stealing, topology-aware
    placement, affinity/NUMA controls and host capacity accounting without
    changing Protos semantics.
11. Genuinely blocking host work must not justify making every normal Actor
    carrier virtual or unbounded. Such work may use a separately bounded
    blocking/dirty/offload lane under existing semantic authority.
12. No `-Xss` tuning, reduced benchmark width or guest-execution serialization is
    accepted as a substitute for the selected carrier architecture.

## Cross-runtime evidence

The detailed review compared actor, coroutine, green-thread and task runtimes
with substantially different implementation families.

### Erlang / BEAM

BEAM multiplexes large numbers of Erlang processes over a bounded set of
scheduler threads, normally related to available CPU capacity. Run queues,
migration/work stealing and separate dirty CPU/I/O schedulers demonstrate two
important principles retained here: logical Actor/process count does not imply
physical thread count, and potentially blocking/native work deserves a distinct
execution lane rather than forcing the normal scheduler to become unbounded.

### Go

Go explicitly separates goroutines (`G`), OS threads (`M`) and execution
capacity (`P`). `GOMAXPROCS` bounds simultaneous Go execution independently of
goroutine count. Recent container-aware evolution also shows why physical
capacity belongs to runtime policy rather than a semantic process or goroutine.

### Rust / Tokio

Tokio's multithread runtime uses a bounded worker pool with local/global queues
and work stealing, while blocking work uses a separate lane. This is strong
precedent for beginning with a simple bounded backend while preserving a future
sharded/work-stealing scheduler shape.

### Akka and Orleans

Akka's normal actors run through shared dispatchers/executors; pinned per-actor
threads are exceptional. Orleans preserves one-turn-at-a-time grain semantics
while multiplexing many activations over shared .NET physical execution
capacity. Both reinforce that Actor single-threaded semantics do not require
thread ownership.

### Pony, Swift, Kotlin, GHC and OCaml

Pony scheduler threads run/steal Actors over finite physical resources. Swift
actors and tasks target executors rather than owning threads. Kotlin's default
CPU dispatcher is CPU-bounded. GHC separates lightweight Haskell threads from
runtime capabilities. OCaml 5 treats heavy Domains as finite parallel resources
while lighter concurrency is layered above them. These designs differ in
scheduling details but converge on bounded physical capacity beneath abundant
logical concurrency.

### Java Loom

Virtual threads are valuable for large amounts of independently blocking work,
but they do not create CPU throughput beyond available physical cores. For
Protos Actors they also introduce a second M:N scheduler underneath Protos's
already-existing Actor/task/mailbox scheduling. The observed failure occurs in
the mount/unmount/attach area exercised by that layering, making Loom a weak
default carrier dependency for normal CPU/guest Actor execution even though it
may remain useful for other bounded host-integration roles.

### Truffle/GraalVM

Truffle permits concurrent Context access when the language authorizes it;
Protos already does so under the I026-A4B1 audit. PLAT010 therefore changes the
host carrier primitive, not the one-Context-per-Process topology or the existing
multithread guest-execution claim.

## Scalability and future evolution

The selected primitive deliberately bounds physical carriers independently of
Actor count. Together with PLAT011, physical carrier cardinality scales with
runtime CPU capacity rather than `Actors`, `Processes × cores`, or arbitrary
virtual-thread population.

At 64/128+ cores, a single shared ready-Actor lock/queue is not assumed to remain
the final scheduler implementation. PLAT010 therefore permits worker-local
queues, sharding and work stealing, while keeping those choices non-semantic.

The architecture also preserves later CPU affinity, NUMA partitions, cgroup or
container quota awareness, host-level observability and admission control. None
of those become Actor identity or language-visible scheduling promises merely
because the runtime may use them.

## Rejected alternatives

### Keep virtual-thread Actor workers as the default

Rejected as the durable default. Protos already owns the lightweight logical
Actor abstraction and scheduler; Loom adds another scheduling layer without
increasing CPU capacity and couples normal Actor correctness more tightly to
JDK/GraalVM mount/unmount behavior.

### One platform thread per Actor

Rejected because physical resources would scale with logical Actor population
and would make Actor creation an implicit OS-thread allocation policy.

### Serialize Process-Context entry

Rejected by PLAT001 because it would destroy physical Actor/P parallelism while
preserving only superficial logical concurrency.

### Context per carrier or Actor

Rejected by PLAT001 because carrier identity is not semantic identity and
multiplying Contexts would duplicate lifecycle, compilation and runtime state.

### Increase JVM stack or cap PERF001-F at width 4

Rejected as a diagnosis-hiding workaround. Reference evidence must test the
approved scalable runtime architecture rather than tune around a failing carrier
strategy.

## Required implementation evidence

PLAT010 ratification releases implementation work but **does not close #239**.
Before #239 can close, a bounded implementation slice must:

1. replace the default virtual-thread Actor carrier path with the selected
   platform-carrier substrate under PLAT011 ownership;
2. preserve Actor ordering/fairness and Process Context routing tests;
3. run the canonical Actor fan-out through the production path at widths 1/2/4/8;
4. repeat width 8 sufficiently to demonstrate that the original attach/hang
   failure no longer reproduces under the selected carrier backend;
5. run the full correctness suite; and
6. retain this diagnostic as correctness/blocker evidence, not as PERF001-F
   reference timing.

Only after that gate passes may PERF001-F H3/H4 reference-evidence work resume.

## Deliberately deferred

PLAT010 does not select exact Java executor APIs, worker names, thread priority,
queue implementation, work-stealing algorithm, affinity syntax, NUMA policy,
blocking-lane sizing, process weights, cgroup policy, Actor quantum changes,
public scheduling controls, distributed placement or P/Actor capacity sharing.
Substantive choices exposed by those topics must cross their own decision gate.
