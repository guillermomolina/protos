# PERF001-F — Future/P/Actor concurrency benchmark methodology

Status: methodology/workload audit **APPROVED**; canonical workload implementation **PUBLISHED**; companion harness and retained measurement evidence pending

Live coordination: GitHub Issue #105, parent PERF001 Issue #51
Nature: non-normative performance-engineering methodology and benchmark-contract record

This record persists the focused PERF001-F workload/methodology audit approved by
the project owner on 2026-09-09. It defines what PERF001-F measures and the
reproducibility/evidence boundary for those measurements. It does not define or
change Protos language semantics, scheduling guarantees, performance guarantees,
or implementation architecture.

The applicable normative owners remain:

- `spec/concurrency/FUTURES_AND_TASKS.md` for Future/task semantics;
- `spec/concurrency/PARALLEL_EXECUTION.md` for isolated P semantics; and
- `spec/concurrency/ACTORS.md` for Actor semantics.

`docs/project/work/PERF001/PERF001_BENCHMARKING.md` remains the parent PERF001 methodology
record. This document refines only the concurrency workload family selected for
PERF001-F.

## 1. Question and selected measurement model

PERF001-F asks two distinct questions for each concurrency mechanism:

1. what fixed end-to-end cost does the mechanism add around a small but real unit
   of Protos work; and
2. where the mechanism is semantically intended to permit physical parallel
   progress, how does a fixed total amount of work scale as more physical CPU
   cores become available?

The selected model is therefore **fixed-cost + scalability**, not a single
undifferentiated concurrency score.

PERF001-F is intentionally Protos-native. It does not manufacture Python
`asyncio`, JavaScript Promise/worker, Java executor, goroutine, Erlang process, or
other cross-language analogues and then claim semantic equivalence with Protos
Future, P, or Actor. Such cross-language characterization belongs to PERF004 when
an independently defensible equivalent workload exists.

The three Protos mechanisms remain separate measurement families:

| Mechanism | PERF001-F question | Multicore scaling claim? |
|---|---|---|
| Future/task | Actor-local asynchronous scheduling and deterministic Future coordination cost | No. Ordinary `future()` does not become CPU-parallel work. |
| P | Explicit isolated CPU-parallel computation, including projection/transfer overhead | Yes, for a fixed-work strong-scaling case. |
| Actor | Persistent isolated execution plus mailbox/request/Future communication cost | Yes, across independent Actors; not by making one Actor's turn execution concurrent. |

## 2. Canonical workload contracts

PERF001-F reserves exactly six canonical workload identifiers under
`protos/benchmarks/concurrency/`. The first corpus implementation slice must use
these identifiers and must satisfy these contracts without adding hidden runtime
privileges or scheduler assumptions.

The canonical source corpus is now published under
`protos/benchmarks/concurrency/`. Each source defines an ordinary top-level `run`
Closure and ends with `run()` for direct correctness execution. Actor sources
perform spawn/readiness setup before `run`; the companion harness may therefore
retain the same production-hosted Process and time later ordinary `run`
invocations without folding Actor bootstrap into steady request cost.

### 2.1 `concurrency/future-roundtrip`

Measure repeated end-to-end execution of a small deterministic Closure through
`closure.future().value()` inside one Actor domain. The benchmark must perform a
non-empty deterministic unit of guest work, validate the exact final observable
result, keep the logical work amount fixed across samples, and include Future
creation/submission, asynchronous execution, terminal completion and observation
in the measured operation.

A synchronous equivalent may be retained as a labelled control for interpreting
fixed Future overhead, but it is not a competing concurrency implementation.

### 2.2 `concurrency/future-fanout-all`

Measure repeated fixed-size batches of independently submitted Actor-local
Futures coordinated with `Future.all(...).value()`. Batch width and total guest
work remain fixed; the ordered aggregate result is validated; and the reference
matrix uses one benchmark CPU allocation.

This workload measures scheduling/co-ordination capacity. It must not be reported
as CPU speedup because ordinary Future work remains Actor-local rather than an
implicit P boundary.

### 2.3 `concurrency/parallel-roundtrip`

Measure repeated small deterministic `closure.parallel(arguments...).value()`
operations with explicit transferable input and an ordinary transferable result.
The measured operation deliberately includes Closure projection, argument
validation/snapshot/transfer, isolated execution, result transfer and
result-Future completion/observation. It must not use ambient mutable caller
capture or authority that P does not receive.

The reference case uses one benchmark CPU allocation so the result characterizes
fixed P-boundary cost rather than speedup.

### 2.4 `concurrency/parallel-array-map`

Measure strong scaling of a fixed-size CPU-bound Array transformation through the
standard parallel Array surface backed by isolated P execution. One fixed input,
one deterministic CPU-bound transform and one total logical work amount are used
for every CPU-set width. The exact logical result/order is validated, and work
per item must be large enough that the case is not merely submission overhead.

The reference widths are `1`, `2`, `4`, and `8` physical cores where available.
Unsupported widths are omitted rather than silently replaced with SMT siblings.
For every available width `n` report:

```text
speedup(n)    = median_time(1) / median_time(n)
efficiency(n) = speedup(n) / n
```

### 2.5 `concurrency/actor-request-roundtrip`

Measure repeated request/reply round trips between the initial/root Actor and one
already-ready destination Actor, including mailbox admission/dispatch,
value-transfer boundaries, reply Future completion and caller observation.
Actor bootstrap/startup is outside the steady-state per-request timing window;
any creation/lifecycle timing is a separately labelled class. The requested
operation, response and final observable result are deterministic and validated.

### 2.6 `concurrency/actor-fanout-requests`

Measure strong scaling of a fixed total number of independent requests across a
fixed set of already-ready Actors. Requests are submitted before aggregate
observation so independent Actor domains can make physical progress concurrently;
completion uses ordinary Future coordination.

Total request count, per-request guest work and final aggregate result remain
fixed across CPU widths. The case uses independent concrete Actors rather than
concurrent execution inside one Actor turn and does not depend on unspecified
ordering between unrelated Actors. Reference widths are `1`, `2`, `4`, and `8`
physical cores where available, with the same speedup/efficiency formulas as the
P scaling case.

## 3. Scheduling and CPU topology methodology

Reference PERF001-F evidence uses explicit Docker/host CPU affinity and retains
the complete CPU set made available to the workload. Scaling CPU sets prefer
distinct physical cores before SMT siblings and retain enough topology evidence
to map logical CPUs to physical package/core identities. The canonical width
sequence is `1, 2, 4, 8`, truncated to the eligible physical-core count.

A run must not label eight SMT logical threads on four physical cores as an
`8-core` reference point. An optional separately labelled SMT experiment may be
retained later, but it is outside the primary strong-scaling series.

No workload may use scheduler sleeps, timing delays, busy polling for a hoped-for
schedule, host-thread identity, executor/pool introspection, or implementation
worker-count knowledge to manufacture concurrency. Completion is determined by
ordinary Protos outcomes such as Future observation, deterministic Future
aggregation, Actor replies and Actor lifecycle operations.

## 4. Correctness and measurement classes

Correctness precedes timing. Every canonical workload validates its exact
observable result before timing from that configuration is accepted. A mismatch
invalidates the configuration's timing evidence.

PERF001-F retains the established PERF001 separation: 10 fresh-process startup
samples where startup is meaningful, 20 retained warmup iterations in one runtime
process, and 20 steady-state samples after warmup. If a concurrency-specific case
needs a different count for stable evidence, that count is justified and fixed
before the reference run rather than selected after seeing a preferred result.

Reference summaries use median as primary central value and retain MAD,
nearest-rank p95, minimum, maximum and every raw sample. Strong-scaling cases
also derive speedup and efficiency from medians. Derived metrics never replace
raw timings. Actor creation/lifecycle timing is distinct from steady request cost,
and intrusive diagnostics remain separate from reference timing.

## 5. Production execution-path requirement

PERF001-F reference evidence executes the measured Protos revision through its
production Process-scoped Polyglot/Truffle hosting architecture. A benchmark
helper must not preserve the historical direct
`ProtosSourceCompiler.compile(...).call(...)` path as a second primary runtime
entry merely because older PERF001 evidence used that historical architecture.

Timing helpers may place precise boundaries around production-hosted execution,
but must not bypass semantic Process creation, Process/Context hosting, RootActor
ownership, Actor routing or P routing required by the measured revision. This is
an evidence-integrity rule, not a requirement to include CLI text parsing or
terminal I/O in every steady-state sample.

## 6. Toolchain identity requirement

Historical PERF001-D/E evidence pinned the runtime/toolchain that was correct for
those measured revisions. PERF001-F does not copy those historical coordinates
forward as floating defaults.

For each measured Protos revision, the harness reads or verifies that revision's
repository-owned toolchain contract, currently rooted at `toolchain.json`, and
retains the resulting JDK/GraalVM/Truffle/container identity. A mismatch between
the harness runtime and the measured revision's required primary runtime is an
environment/precondition failure, not a benchmark result.

## 7. I026 production-entry stability gate

Methodology, canonical corpus implementation and companion-harness work may
proceed while I026-A4B3 finishes its selected production-driver cutover. The
**final retained PERF001-F reference run** waits until the production paths
exercised by these workloads no longer depend on the temporary legacy/direct
primary-entry architecture that I026-A4B3 is retiring.

The gate is objective rather than commit-number based: before reference timing is
accepted, re-audit current `origin/main` and verify that the relevant ordinary
module/RootActor initial-module execution and final direct-production-entry
retirement required by I026-A4B3 are published. This does not make later I026
debugger/LSP work a PERF001-F dependency.

## 8. Cross-repository implementation sequence

The approved methodology is decomposed into these safe publication boundaries:

1. persist this methodology and exact workload contracts in `guillermomolina/protos`;
2. implement and validate the six canonical Protos workload sources;
3. extend `guillermomolina/protos-benchmarks` with production-hosted concurrency
   execution, topology-aware CPU selection and retained result aggregation;
4. after the I026 production-entry stability gate, publish reference evidence at
   exact Protos + harness revisions; and
5. reconcile exact companion evidence into PERF001-F and close Issue #105 only
   when its closure rule is satisfied.

These phases are implementation slices inside existing PERF001-F, not new formal
PERF work items by default.

Steps 1 and 2 are now published. Step 3, the companion production-hosted harness,
is the next implementation slice; the I026 gate still applies only to step 4's
retained reference timing.

## 9. Explicit exclusions

PERF001-F does not define new Future/P/Actor/fairness/transfer/cancellation
semantics, expose public scheduler machinery, compare unlike concurrency
abstractions as if equivalent, infer language-wide performance from one workload,
alter runtime implementation to improve baseline scores, force schedules with
host tricks, or use compiler diagnostics as performance scores.

Material optimization opportunities discovered by retained evidence belong to a
separately owned PERF item under the baseline-versus-optimization rule.
