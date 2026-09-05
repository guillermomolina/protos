# PERF001 Benchmarking Plan

This document is a non-normative project record for `PERF001 — Core v0.1
baseline benchmark suite`. It defines benchmark ownership, reproducibility rules,
and the boundary between the Protos repository and a future companion benchmark
harness. It does not define Protos language semantics or performance guarantees.

## Repository ownership

`guillermomolina/protos` remains the canonical owner of the `PERFxxx` work-item
lifecycle. `docs/project/IMPLEMENTATION_STATUS.md` owns PERF status and closure
evidence.

The Protos-language workload corpus remains under `protos/benchmarks/`. Those
sources are the canonical Protos versions of workloads used by PERF work and are
pinned by the Protos Git commit recorded for each benchmark run.

A future companion repository, currently intended as
`guillermomolina/protos-benchmarks`, may own:

- Docker/container definitions for benchmark runtimes;
- the external benchmark runner and statistics/reporting tools;
- materially equivalent implementations for comparison languages;
- machine/runtime inventory capture;
- raw benchmark-run results and generated performance reports.

The companion repository is execution evidence, not a competing project-status
ledger. It must not assign, close, or redefine `PERFxxx` items independently of
the canonical Protos status ledger.

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
| PERF001-B | READY | Create the companion Docker benchmark harness with pinned runtime images, machine/runtime inventory, CPU-affinity policy, raw-result schema, and a smoke benchmark consuming a pinned `guillermomolina/protos` revision. |
| PERF001-C | BLOCKED_BY_DEPENDENCIES | After B, implement correctness-gated cross-language equivalents and runner integration for the existing micro/runtime/algorithm corpus. |
| PERF001-D | BLOCKED_BY_DEPENDENCIES | After C, establish startup, warmup-curve and steady-state Protos measurements, including interpreter-versus-Truffle-compilation and non-timing compilation diagnostics. |
| PERF001-E | BLOCKED_BY_DEPENDENCIES | After C, extend comparable coverage for closed collection semantics and other sequential Core workloads selected by the then-current audit. |
| PERF001-F | BLOCKED_BY_DEPENDENCIES | After B and the relevant workload audit, add Future/P/Actor concurrency measurements with explicit CPU-set and scheduling methodology. |
| PERF001-G | BLOCKED_BY_DEPENDENCIES | Final reproducibility run and baseline report across the completed PERF001 surface. A report labelled the complete Core v0.1 baseline additionally requires I015 to be CLOSED. |

Dependency outline: `PERF001-A -> PERF001-B -> PERF001-C -> PERF001-D/E`, with
`PERF001-F` depending on B plus its focused concurrency audit; `PERF001-G` closes
only after all required preceding PERF001 slices are closed.

## Cross-repository publication rule

A companion-repository slice is implemented and validated there first. Its exact
published commit becomes evidence in the subsequent Protos ledger update. The
Protos-side status update must re-read current `origin/main`, preserve unrelated
concurrent work, and record the external commit rather than a floating branch
name.

No temporary remote branch is required in either repository for this workflow.
