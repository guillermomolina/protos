# PERF001 Benchmarking Plan

This document is a non-normative project record for `PERF001 — Core v0.1
baseline benchmark suite`. It defines benchmark ownership, reproducibility rules,
and the boundary between the Protos repository and a future companion benchmark
harness. It does not define Protos language semantics or performance guarantees.

## Repository ownership

`guillermomolina/protos` remains the canonical owner of the `PERFxxx` work-item
lifecycle. GitHub Issues and the `Protos Development` Project own live PERF
coordination/status; `docs/project/registries/IMPLEMENTATION_STATUS.md` retains durable
historical/closure evidence.

The Protos-language workload corpus remains under `protos/benchmarks/`. Those
sources are the canonical Protos versions of workloads used by PERF work and are
pinned by the Protos Git commit recorded for each benchmark run.

The companion repository `guillermomolina/protos-benchmarks` owns:

- Docker/container definitions for benchmark runtimes;
- the external benchmark runner and statistics/reporting tools;
- materially equivalent implementations for comparison languages;
- machine/runtime inventory capture;
- raw benchmark-run results and generated performance reports.

The companion repository is execution evidence, not a competing project-status
system. It must not assign, close, or redefine `PERFxxx` work independently of
the owning Protos GitHub Issues, durable project records, and published evidence.

The companion repository must consume a pinned Protos revision. It must not copy
the Protos workload corpus and then silently evolve those copies independently.
Comparison-language translations are separate implementations of the same
workload contract, not replacements for the canonical Protos sources.

## Required run identity

Every retained benchmark run must record enough information to identify what was
measured. At minimum this includes:

- exact Protos Git commit;
- exact benchmark-harness Git commit;
- benchmark/workload identifier;
- runtime name and exact runtime version;
- JDK/GraalVM version where applicable;
- container image identity or immutable digest when containers are used;
- host CPU model, logical CPU count, memory, kernel and architecture;
- CPU affinity / cpuset and materially relevant resource limits;
- warmup policy, measurement count and aggregation method.

A result missing the exact Protos revision or benchmark-harness revision is not
reference PERF001 evidence.

## Correctness gate

Performance measurement follows correctness validation, never the reverse.
Before timings from a workload are accepted, every compared implementation must
produce the workload's documented observable result for the same logical input.
A failed or mismatched result invalidates that measurement; it is not a slower or
faster benchmark result.

Benchmark-specific Protos semantics, hidden privileged objects, special runtime
fast paths that alter observable behavior, or correctness shortcuts are not
permitted.

## Cross-language equivalence

Cross-language comparisons must use materially equivalent algorithms, inputs,
work amounts and observable results. A comparison must identify deliberate
runtime-mode differences.

The primary algorithm-equivalent suite must not replace an explicit loop,
recursion, dispatch sequence, or collection algorithm in one language with a
host/library primitive that performs materially different work. Idiomatic-library
comparisons may be added separately, but must be labelled as a different
question rather than mixed into algorithm-equivalent results.

No single microbenchmark is evidence for a general claim that one whole language
is faster than another.

## Measurement classes

PERF001 distinguishes three measurement classes.

### Startup

Startup launches a fresh language process for each measured sample and includes
the language/runtime startup, Protos bootstrap, parsing/compilation work required
by the normal command and program execution.

When Docker is used, container creation/startup is outside the language-startup
measurement. Containers may be prepared before samples are taken; otherwise the
runner must place its timing boundary inside the already-started container.
Container-start latency may be measured separately, but it must not be reported
as Protos language startup.

### Warmup

Warmup runs repeated equivalent work inside the same language process and
retains the per-iteration series needed to observe stabilization. JIT-capable
runtimes must not have their early iterations silently merged into steady-state
results.

For Protos, compilation tracing or equivalent Graal/Truffle diagnostics may be
captured in diagnostic runs, but diagnostic instrumentation must be kept
separate when it materially perturbs timing.

### Steady state

Steady-state measurement begins only after the declared warmup policy. Raw
samples are retained. The reference summary reports the median as the primary
central result and also retains enough data to derive dispersion; mean, minimum,
percentiles or standard deviation may be reported as secondary statistics.

## Docker execution policy

Docker is an accepted and preferred reproducibility mechanism for PERF001 on a
Linux host. All compared runtimes should run on the same host for a reference
comparison.

For CPU-focused single-threaded measurements, prefer an explicit cpuset/CPU
affinity over scheduler CPU quota as the primary isolation mechanism. Parallel
benchmarks must record the CPU set made available to the workload.

Networking should be disabled for workloads that do not require it. Memory and
other resource limits must be consistent where they can materially affect the
comparison. Filesystem, networking, process-creation and other environment-heavy
benchmarks require their own explicit methodology because container boundaries
may be part of what is being measured.

## Baseline versus optimization

PERF001 records the unoptimized project baseline as observed at pinned Protos
revisions. Discovering a bottleneck does not authorize changing Protos semantics
or folding an optimization campaign into the baseline evidence.

Material optimization work discovered by PERF001 receives a separate `PERFxxx`
identifier. The unchanged PERF001 suite can then be rerun before and after that
optimization to quantify the effect.

## PERF001 slices

The slices are intentionally split at the repository boundary so that project
work can progress without pretending that a commit is atomic across two Git
repositories.

| Slice | Status | Scope / closure condition |
|---|---|---|
| PERF001-A | CLOSED | Protos-side benchmark ownership, correctness/equivalence rules, Docker timing boundary, reproducibility contract, corpus handoff, and persisted PERF001 slice plan are published and validated. |
| PERF001-B | CLOSED | Companion Docker benchmark harness published at exact external commit `guillermomolina/protos-benchmarks@4e809acd839a0193250140c5b6dde48051d9aa9a`, consuming pinned Protos revision `509b09562233b925d8414ce9a65196efd08da472`; runtime definitions, machine/runtime inventory, CPU-affinity policy, raw-result schema, and correctness-gated smoke validation are present. No timing results are published by this slice. |
| PERF001-C | CLOSED | Companion correctness suite published at exact external commit `guillermomolina/protos-benchmarks@2da26df49f9b0673c56a9150a2d2f8cfc4a77c17`, consuming pinned Protos revision `42b8264a36254dafbd97d80f5181790e28b9de12`; all 11 canonical micro/runtime/algorithm workloads have materially equivalent Python and JavaScript implementations, all 33 Protos/Python/JavaScript correctness cases pass, runtime stack/recursion settings are recorded, and no timing results are published by this slice. |
| PERF001-D | CLOSED | Companion reference measurement evidence published at exact external commit `guillermomolina/protos-benchmarks@52b083cef5f8726f73be869c56f3cd2933e919ab`, produced by harness `0a406373c497df1173ff26a3ed4fcada015e0879`. It compares exact pre/post-PERF002 Protos revisions `8f363d0146164f99e72210eb44667f4efb7b88e7` / `3c93912a5579326374782a43527fbb51046f8f91` with 10 fresh-JVM startup samples, 20 retained warmup iterations and 20 steady-state samples for each of the 11 canonical workloads in interpreter and Truffle modes, plus separate non-timing compilation diagnostics; raw samples and environment/runtime identity are retained. |
| PERF001-E | CLOSED | Companion reference evidence published at exact commit `guillermomolina/protos-benchmarks@4bff9f7f6c5e0e006530f166c188e0e988acf565`, produced by harness `280173d743b2ed838a89be0ad930b20828d89558` against pinned Protos corpus revision `86b35d8bb2d7ab2ad54bc2947e1bf7fbff1fca15`. All 18 Protos/Python/JavaScript correctness cases PASS and 54 startup/warmup/steady result records are retained. Separate diagnostics preserve two baseline optimization findings: `array-reduce` and `array-sort` each have 40 `GraphTooBig` failures with `rc=0`; those findings are routed to PERF003 rather than invalidating or rewriting PERF001-E. |
| PERF001-F | CORPUS_IMPLEMENTED | The approved fixed-cost + scalability methodology and all six canonical Future/P/Actor Protos workload sources are published. Companion production-hosted harness and retained reference evidence remain pending; the final reference run is gated only on the relevant I026-A4B3 production-entry retirement. Live coordination is GitHub Issue #105. |
| PERF001-G | BLOCKED_BY_DEPENDENCIES | Final reproducibility run and baseline report across the completed PERF001 surface. I015 is already CLOSED; PERF001-F retained reference evidence remains the outstanding PERF001 predecessor. |

Dependency outline: `PERF001-A -> PERF001-B -> PERF001-C -> PERF001-D/E`.
The focused PERF001-F audit and canonical six-source Protos concurrency corpus are
now durably published. Companion production-hosted harness work may proceed, while
the final retained F reference run waits only for its documented I026-A4B3
production-entry stability gate. `PERF001-G` closes only after all required preceding
PERF001 slices are closed.

### PERF001-D closure evidence

PERF001-D is closed by exact companion evidence
`guillermomolina/protos-benchmarks@52b083cef5f8726f73be869c56f3cd2933e919ab`, generated by exact harness
`0a406373c497df1173ff26a3ed4fcada015e0879`.

The retained run isolates the material PERF002 optimization by comparing the
adjacent revisions immediately around PERF002-A:

- pre-PERF002: `8f363d0146164f99e72210eb44667f4efb7b88e7`;
- post-PERF002: `3c93912a5579326374782a43527fbb51046f8f91`.

For every one of the 11 canonical PERF001 workloads, both interpreter and
optimizing-Truffle modes retain 10 fresh-JVM startup samples, a 20-iteration
warmup curve, and 20 steady-state samples after warmup. The same host/runtime
family, pinned CPU, GraalVM Community JDK 22, external `truffle-runtime:24.0.0`
and `-Xss128m` configuration are recorded. Compilation tracing is excluded from
timing and retained only in separate non-timing diagnostics.

The post-PERF002 diagnostic set records successful execution for all 11
workloads with zero `opt_failed`, `GraphTooBig`, `FrameWithoutBoxing`,
deep-inlining, `StackOverflowError`, or `BootstrapMethodError` occurrences.
Individual pre/post timing ratios remain workload- and mode-specific
observations and are not generalized into a whole-language performance claim.

### PERF001-E fresh workload audit and corpus phase

The fresh audit selects only closed, deterministic, sequential collection
surfaces that have a credible algorithm-equivalent comparison boundary:

- `std:collections/Array.map`;
- `std:collections/Array.filter`;
- `std:collections/Array.reduce`;
- stable `std:collections/Array.sort`;
- Core `Map.at` / `Map.atPut`;
- `std:collections/Set` union/intersection/difference over its ordinary Map-backed
  representation.

The corpus deliberately excludes concurrency, Future/P/Actor work (PERF001-F),
I/O/resource timing, JSON/text processing, and IdentityMap identity-sensitive
comparisons from this slice. It also avoids introducing a generic iterable
benchmark contract that Protos does not define.

The Protos publication establishes the canonical six workload sources and
expected values. PERF001-E remains `IN_PROGRESS` until the companion repository
publishes materially equivalent Python/JavaScript implementations, correctness
evidence and retained measurements against the exact Protos corpus commit, and
that evidence is reconciled back into this ledger.

### PERF001-E closure evidence

PERF001-E is closed by exact companion evidence
`guillermomolina/protos-benchmarks@4bff9f7f6c5e0e006530f166c188e0e988acf565`, generated by exact harness
`280173d743b2ed838a89be0ad930b20828d89558` against canonical corpus revision `86b35d8bb2d7ab2ad54bc2947e1bf7fbff1fca15`.

All 18 cross-language correctness cases pass and 54 startup/warmup/steady result
records are retained. Separate non-timing diagnostics remain baseline evidence:
`array-map`, `array-filter`, `map-lookup-update` and `set-algebra` have zero
optimization failures; `array-reduce` and `array-sort` execute correctly but each
records 40 `GraphTooBig` failures. Those findings motivate PERF003 and do not
alter the PERF001-E baseline.


### PERF001-F focused concurrency methodology audit

The project owner approved the focused PERF001-F methodology on 2026-09-09. The
durable contract is `docs/project/work/PERF001/PERF001_F_CONCURRENCY_METHODOLOGY.md`. It
selects a Protos-native fixed-cost + scalability model rather than manufactured
cross-language concurrency analogues and reserves six canonical workload
identifiers under `protos/benchmarks/concurrency/`:

- `concurrency/future-roundtrip`;
- `concurrency/future-fanout-all`;
- `concurrency/parallel-roundtrip`;
- `concurrency/parallel-array-map`;
- `concurrency/actor-request-roundtrip`; and
- `concurrency/actor-fanout-requests`.

Future/task measurements characterize Actor-local asynchronous scheduling and
deterministic coordination rather than claiming implicit CPU parallelism. P and
multi-Actor workloads additionally include fixed-work strong-scaling series over
explicit topology-audited physical-core CPU sets. Correctness remains a timing
gate; raw samples plus median/MAD/p95 are retained, with speedup/efficiency
derived only for the scaling cases.

The reference harness must measure the pinned Protos revision through its
production Process-scoped Polyglot/Truffle hosting path and verify that revision's
repository-owned toolchain contract instead of carrying historical JDK22 /
Truffle 24.0.0 PERF pins forward. Methodology/corpus/harness work may proceed
now. Only the final retained reference run is gated on the relevant
I026-A4B3 production-entry cutover/legacy-entry retirement.

## Cross-repository publication rule

A companion-repository slice is implemented and validated there first. Its exact
published commit becomes evidence in the subsequent Protos ledger update. The
Protos-side status update must re-read current `origin/main`, preserve unrelated
concurrent work, and record the external commit rather than a floating branch
name.

No temporary remote branch is required in either repository for this workflow.
