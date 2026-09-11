# PLAT023 — Async exact-execution carrier topology for Test Tool

Status: **RATIFIED — Candidate A′ selected**

Nature: durable non-normative JVM / Truffle host-carrier architecture decision

Approved by project owner: **2026-09-11**

GitHub Issue: **#335**

Primary consumer: `TOOL002-H2B3` / GitHub #95.

Normative effect: **none**. PLAT023 selects the initial JVM/Truffle mechanism
that transports already-admitted asynchronous exact executions. It does not
change Protos language semantics, TestPlan ordering, Process/Actor/Future
semantics, public resource policy, or the D069 meaning of `--jobs`.

## Selected architecture — Candidate A′

Each exact execution already admitted by bundled-Protos Test Tool scheduling
receives one **fresh named Java platform Thread**. The host carrier is mechanism
only: it starts accepted work, retains custody through host-terminal cleanup and
reports completion back through the existing D055/H2A boundary.

Conceptually:

```text
bundled Protos Test Tool
    |
    | maxInFlight / D069 jobs capacity
    v
accepted exact execution
    |
    v
fresh named platform Thread
    |
    +-- fresh semantic Process
    +-- fresh RootActor
    +-- fresh Polyglot Context
    |
    v
captured inert terminal evidence
    |
    v
caller Actor-domain rematerialization
    |
    v
carrier terminates after host cleanup
```

The durable rule is deliberately independent of the Java helper used to realize
one-thread-per-task submission. `Thread`, a private `ThreadFactory`, or a
session-owned thread-per-task executor are implementation choices so long as the
constraints below hold.

## Composition with D055 and D069

D055 remains authoritative for the asynchronous exact-execution boundary:

- host code owns submission, execution and completion mechanism only;
- each invocation returns one ordinary caller-domain Future;
- host completion retains inert evidence only;
- guest observation is rematerialized only in the caller Actor domain;
- cancellation may withdraw work that has not started but must not falsely claim
  hard preemption of already-started same-runtime execution; and
- facility/session lifetime retains outstanding work until host cleanup is
  terminal.

D069 remains authoritative for public Test Tool outer capacity:

```text
protos test --jobs N
jobs = global logical Test Tool execution-slot capacity
```

`jobs` is not a JVM-thread count. PLAT023 therefore MUST NOT reinterpret numeric
jobs as executor size, CPU count or a second carrier quota. H2B owns admission;
PLAT023 transports only work H2B has already admitted.

For the current one-slot-per-case H2 model, peak outer carrier count is bounded by
current admitted work and is therefore `O(maxInFlight)`, not `O(total suite
size)`.

## Why a fresh carrier rather than a reusable pool

A fixed pool requires another capacity. If smaller than `maxInFlight`, it silently
adds a second admission bound. If equal to `maxInFlight`, it duplicates the same
policy in host code. If deliberately oversized, it provides no scheduling value
and exists only to reuse Thread identity.

A cached/elastic pool avoids a fixed second bound but still reuses carrier Thread
state across otherwise fresh test executions and introduces retention/idle
policy that H2 does not require. Fresh carriers instead align physical lifetime
with the already-selected fresh Process/RootActor/Context isolation boundary and
avoid unnecessary ThreadLocal/native-TLS/tooling-state reuse surface.

The production Actor carrier pool is explicitly excluded. Outer Test Tool
parallelism must remain independent from Actors/Tasks/Futures/P exercised inside
a case; using Actor carriers to launch other cases would couple the two
concurrency domains and can create starvation or hidden scheduling feedback.

## Exhaustive Truffle implementation survey

The approval audit screened the current public Truffle implementation catalogue
of 16 principal implementations:

- Enso;
- Espresso;
- FastR;
- GraalJS;
- GraalPy;
- GraalWasm;
- grCUDA;
- Apple Pkl;
- SimpleLanguage;
- SOMns;
- Sulong / LLVM;
- TRegex;
- TruffleRuby;
- TruffleSOM;
- TruffleSqueak; and
- Yona.

Historical/experimental implementations were also considered where they exposed
a materially different carrier or scheduler pattern. They did not reveal a
stronger modern architecture family for this boundary than direct context/thread
execution, language-owned scheduler pools, or specialized external backends.

### Strongest transferable evidence

- **TruffleRuby** creates normal Ruby Threads through
  `Env.newTruffleThreadBuilder(...).build()` and keeps explicit custody of
  language-managed host Threads through shutdown. Its optional virtual-thread
  path is specific to Fibers rather than a universal replacement for every guest
  execution. This strongly supports explicit carrier lifetime/custody while
  keeping guest scheduling distinct from outer tooling transport.
- **Espresso** models guest Java Threads over host Threads with explicit Truffle
  thread integration. It demonstrates that host Thread identity/lifetime remains
  operationally relevant for stacks, tooling and guest/native interaction even
  inside a highly mature multithreaded Truffle runtime.
- **Sulong / LLVM** creates pthread carriers through Truffle thread machinery and
  retains host-thread IDs, return values and TLS/destructor state. This is strong
  evidence for preserving conservative platform-thread compatibility at a
  semantically invisible outer boundary.
- **GraalPy** materially constrains current virtual-thread use around native
  extensions. This is direct evidence that platform vs virtual carrier choice is
  not yet transparent across all Truffle/native interoperability surfaces.
- **GraalJS** demonstrates many independent Contexts executing concurrently on
  host threads while preserving per-Context isolation. This closely matches H2's
  fresh-Context-per-case topology without requiring a shared outer scheduler.
- **FastR** historically uses a dedicated `EvalThread` for child-Context
  evaluation, aligning carrier lifetime closely with one evaluation lifecycle.
- **Enso** is the strongest serious pool precedent. Its guest-code pools are part
  of Enso's own managed guest scheduling/interruption infrastructure. The lesson
  imported by Protos is explicit pool/thread ownership and shutdown, not a reason
  to duplicate H2B admission below the Test Tool scheduler.
- **SOMns** maintains separate ForkJoin pools for distinct semantic activity
  families. This reinforces separation between scheduler domains; it does not
  justify putting outer Test Tool work onto Protos's Actor pool.
- **TruffleSqueak** uses pools for auxiliary operations while its general
  multithread support remains a separate runtime concern. Auxiliary host pools
  are therefore not evidence that evaluator execution itself should be pooled.
- **Apple Pkl** keeps normal evaluator execution on the entering caller Thread
  and uses a separate scheduled executor for timeout, while high-fanout tooling
  such as `pkldoc` may use virtual threads. Its transferable principle is to keep
  evaluator/carrier lifecycle simple and to introduce concurrency substrates at
  the layer that actually owns the parallel work.
- **GraalWasm, TRegex, grCUDA, SimpleLanguage, TruffleSOM and Yona** were screened
  for contradictory carrier precedents. Their threading/scheduling surfaces are
  either specialized, intentionally minimal or historically weaker evidence for
  this exact outer-execution boundary.

Across the mature implementations, the important convergence is not one universal
Thread API. It is separation of concerns: guest/runtime schedulers own guest
concurrency, Context/thread lifecycle is explicit where guest code executes, and
auxiliary host pools do not silently redefine higher-level semantic admission.

## Current virtual-thread assessment

Virtual-thread-per-task is the strongest future carrier alternative and preserves
the same one-accepted-task/one-carrier conceptual shape. It is not selected as the
initial baseline because current Truffle support still has materially different
maturity around debugging, resource limits, Native Image realization and some
native interoperability paths, while exact Protos execution can be CPU/JIT/guest
work rather than a predominantly blocking-I/O workload.

The `Submission` seam makes deferral cheap: when Truffle virtual-thread support is
mature for Protos's debugger, resource, native and Native Image requirements, the
carrier can change without changing D055, H2B, D069, TestPlan semantics or public
CLI meaning.

## Candidate set and disposition

### A′ — fresh session-owned platform Thread per accepted execution

**Selected.** One accepted exact execution receives one fresh named platform
Thread. No second concurrency limit or host TestPlan queue is introduced.

### B — fresh virtual Thread per accepted execution

Credible future replacement. Strongest raw thread-count scaling, but deferred
because current Truffle/tooling/native/AOT support is less mature and H2 does not
need enormous mostly-blocking fan-out.

### C — fixed platform-thread pool

Not selected. Mature and efficient, but necessarily introduces or duplicates a
pool capacity/queue below the Protos-owned admission boundary.

### D — cached/elastic reusable platform-thread pool

Not selected. Avoids a fixed hidden bound but adds Thread reuse/retention policy
and cross-execution Thread-state surface without a current need.

### E — global/common/ForkJoin work-stealing pool

Rejected. Global lifecycle, unrelated-work interference and work-stealing/fairness
policy escape Test Tool ownership and weaken diagnosability/isolation.

### F — reuse the RuntimeHost Actor carrier pool

**Hard rejected.** It couples outer Test Tool execution to the same carriers used
by Actors inside tests, directly violating the required independence between
outer and inner concurrency.

### G — immediate OS-worker/process backend

Architecturally strong for hard timeout/crash containment and later remote
execution, but premature as the mandatory local baseline. D055 deliberately
keeps this as a replaceable physical backend evolution.

### H — adaptive platform/virtual carrier selection

Deferred. It adds hidden policy and more operational states before evidence shows
that a workload-sensitive carrier choice is needed.

## Required comparative scorecard

Scores are 1–5. Confidence is `H` (high) or `M` (medium). Arithmetic supports the
comparison but is not selection authority.

| Criterion | **A′ platform fresh** | B virtual fresh | C fixed pool | D elastic pool | E common/FJ | G OS worker |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Correctness / invariants | **5/H** | 4/M | 5/H | 5/H | 4/H | 5/H |
| Protos alignment | **5/H** | 5/H | 3/H | 4/H | 2/H | 3/H |
| Future-option resilience | **5/H** | 4/M | 4/H | 4/H | 3/H | **5/H** |
| Scalability | 4/H | **5/H** | 4/H | 5/H | 5/H | **5/H** |
| Conceptual simplicity | **5/H** | **5/H** | 4/H | 4/H | 3/H | 2/H |
| Portability / implementation freedom | **5/H** | 3/M | **5/H** | **5/H** | 4/H | 4/H |
| Runtime / resource cost | 3/H | **5/H** | 4/H | 4/H | **5/H** | 2/H |
| Failure / operability | **5/H** | 3/M | 4/H | 3/H | 2/H | **5/H** |
| Reversibility / migration | **5/H** | **5/H** | 4/H | 4/H | 3/H | 4/H |
| Evidence maturity / implementation risk | **5/H** | 3/M | **5/H** | 4/H | 5/H | 5/H |
| **Total / 50** | **47** | **42** | **42** | **42** | **36** | **40** |

F is disqualified by the outer-vs-inner concurrency invariant. H is deliberately
not scored because its hidden selection policy is itself the unresolved mechanism
and would need a later independent decision if required.

## Focused future / scalability / Protos scoring

The project owner additionally requested explicit long-term scoring on the three
most important dimensions. Scores are 1–10.

| Candidate | Future resilience | Scalability | Protos philosophy | Total |
| --- | ---: | ---: | ---: | ---: |
| **A′ — fresh platform Thread** | **10** | **8.5** | **10** | **28.5/30** |
| B — fresh virtual Thread | 8 | **10** | 9 | 27/30 |
| C — fixed pool | 8 | 8 | 6.5 | 22.5/30 |
| D — elastic reusable pool | 8 | 9 | 7 | 24/30 |
| E — common/ForkJoin | 7 | 9.5 | 5 | 21.5/30 |
| G — OS worker | **10** | **10** | 6 now / 9 when required | 26/30 now |

A′ is selected because it preserves current Truffle/native/tooling compatibility,
keeps exactly one scheduler policy at the H2 boundary, aligns carrier lifetime
with fresh execution isolation, and retains a very cheap migration path when the
physical backend changes.

## Future/scalability stress test

### Large suites

Suite cardinality does not create persistent carriers. Only currently admitted
cases receive Threads. With `jobs = N` and the current one-slot-per-case model,
peak outer carriers are bounded by N; completed carriers terminate.

### Many Actors / Tasks / Futures / P operations inside a case

Inner concurrency continues through the production Actor/Task runtime and its own
carrier substrate. No outer case consumes an Actor carrier merely to exist, and
inner saturation cannot redefine Test Tool admission policy.

### Very large explicit jobs values

Platform-thread stacks/startup become a visible resource cost. PLAT023 does not
hide that cost behind another queue or silently clamp the user's logical capacity.
TOOL002-I resource governance and a future virtual/OS-worker backend remain the
proper evolution points.

### Cancellation and unwind

Accepted-but-not-started work may be withdrawn when the concrete Submission can
prove it never started. Once started, cancellation is terminal at the Protos
Future boundary but does not pretend the same-runtime carrier was hard-killed.
Host work retains custody until real terminal cleanup. Guest Error/non-local
return/cancellation semantics remain owned by their existing runtime decisions.

### Truffle compilation/deoptimization and Bytecode DSL

Carrier selection sits outside guest AST/Bytecode representation and does not
become compilation state. Each fresh Context executes on an ordinary supported
platform Thread while shared Engine/JIT infrastructure remains unchanged.
Migration to Truffle Bytecode DSL therefore does not require revisiting this
boundary.

### Native Image / AOT

The initial baseline uses ordinary platform threads rather than depending on the
current Truffle virtual-thread realization. This preserves a more uniform host
model while Native Image/Truffle virtual-thread support continues to evolve.

### FFI / native interoperability

Fresh platform carriers minimize assumptions about future native TLS, debugger,
profiler or extension-library support. No public FFI semantic contract is created
by PLAT023.

### OS-worker hard containment

Platform Threads do not provide safe hard kill for arbitrary started guest work.
If guaranteed timeout/crash recovery becomes required, the physical backend must
move to OS-worker/process containment rather than using unsafe Thread termination.
`Submission` remains the replacement seam.

### Remote/distributed execution

A later backend may place already-admitted exact executions onto local workers or
remote nodes. D069 remains logical admission capacity; PLAT023's current carrier
identity does not become part of public Test Tool semantics.

## What plausible future requirement would make A′ regrettable?

A mature Test Tool could eventually sustain hundreds or thousands of simultaneous
mostly-blocking exact executions, while Truffle virtual-thread debugging,
resource accounting, Native Image and native interoperability have become fully
mature. Platform-thread stack/start cost would then be unnecessary overhead.

**Escape path:** replace only the host `Submission` implementation with a fresh
virtual-thread-per-task substrate, or with an OS/remote execution backend. D055,
H2B scheduling, D069 numeric capacity, TestPlan order and guest semantics remain
unchanged.

## Strongest argument against A′

Java virtual threads are specifically designed to make thread-per-task code cheap.
Selecting platform threads in 2026 can appear overly conservative and sacrifices
raw carrier-count scalability.

The project accepts that cost because the current workload is already logically
bounded, can perform substantial guest/interpreter/JIT CPU work, and crosses
Truffle Context/tooling/native boundaries where virtual-thread support is not yet
as uniform. Most importantly, the existing `Submission` abstraction makes later
migration unusually cheap, so the conservative initial compatibility choice does
not become public semantic lock-in.

## Durable constraints

1. Bundled Protos Test Tool code remains the sole owner of outer admission.
2. PLAT023 receives only already-admitted exact executions.
3. Each accepted execution gets one fresh named Java platform Thread in the
   initial production backend.
4. No carrier-side fixed capacity, hidden CPU-derived capacity or TestPlan queue
   may redefine `maxInFlight` / D069 jobs.
5. The carrier/session retains accepted work until terminal host cleanup.
6. Cancellation may report pre-start withdrawal only when work was actually
   prevented from starting.
7. Already-started same-runtime work is not interrupted or advertised as hard
   preempted by this mechanism.
8. Host completion executes no guest Test Tool policy and retains only inert
   captured evidence until caller-domain rematerialization.
9. Fresh semantic Process / RootActor / Context isolation remains unchanged.
10. The production Actor carrier pool is never used to transport outer Test Tool
    exact executions.
11. Carrier Thread identity is non-semantic and must not leak into TestPlan,
    results, ordering, public CLI behavior or guest values.
12. No JVM-global/common executor institution is introduced by this decision.
13. Carrier implementation remains replaceable behind
    `ProtosAsyncExactExecutionFacility.Submission`.
14. PLAT023 does not select hard timeout/kill, OS-worker, remote, retry, resource,
    CPU-affinity/NUMA or automatic-jobs policy.

## Implementation release boundary

Ratification releases `TOOL002-H2B3` to implement only the already-selected
production transport and real `protos test` integration:

- provide a production `Submission` consistent with the fresh named platform
  Thread contract;
- wire the existing H2B bounded runner to the real Test Tool path;
- consume D069 `--jobs N` only as Protos-owned logical `maxInFlight` capacity;
- keep execution and inspection routes on the same carrier/submission contract;
- add focused evidence that physical overlap occurs up to the Protos bound while
  final result/report order remains deterministic;
- prove carrier/session shutdown retains accepted work through cleanup;
- do not change Actor scheduler topology or introduce another host admission
  policy.

If H2B3 exposes any materially new semantic or durable architecture decision, it
must stop at a new Dxxx/PLATxxx gate rather than extending PLAT023 implicitly.

## Intentionally deferred

- virtual-thread migration and any trigger for selecting it;
- `jobs=auto` and hardware-derived automatic capacity;
- TOOL002-I resource declarations/weights/exclusive resources;
- OS-worker hard timeout/crash containment;
- remote/distributed transport and placement;
- retry/flaky/sharding policy;
- global/shared carrier institutions;
- CPU affinity, NUMA and host topology tuning;
- any change to production Actor scheduling.
