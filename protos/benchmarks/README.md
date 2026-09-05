# Protos benchmark corpus

The benchmark corpus is non-normative. It provides deterministic workloads for comparing Protos implementations without making performance characteristics part of language semantics or conformance.

Benchmark source files use `.protos`. Portable workloads do not self-time: an external runner controls warm-up, repetitions, timing, profiling, and implementation-specific metrics. A measurement is valid only when the workload produces its documented result.

The first corpus intentionally uses only mechanisms already defined clearly enough by the current normative specifications. Algorithm variants that require a standardized growable sequence or additional iteration protocols should be added only after those contracts are defined; benchmark code must not invent missing library semantics.

Initial families:

- `micro/` — small language/runtime mechanisms.
- `algorithms/` — implementation-independent algorithms.
- `runtime/` — workloads especially relevant to delegation and dispatch.
- `concurrency/` — reserved for concurrency workloads grounded in the normative concurrency model.
- `io/` — reserved for workloads grounded in the normative I/O model.

## PERF001 corpus ownership and external-runner boundary

This directory is the canonical Protos-language workload corpus for PERF work.
A benchmark run must pin the exact Protos Git commit from which these sources are
consumed.

External benchmark infrastructure may live in a companion repository. That
infrastructure owns containers, runners, comparison-language implementations,
raw measurements and reports; it does not replace these Protos workload sources
or own PERF lifecycle state.

For algorithm-equivalent comparisons, the companion implementation must preserve
the same logical input, work shape and observable result. Benchmark execution
must validate correctness before accepting timing data.

These `.protos` programs do not self-time. Startup, warmup and steady-state timing
boundaries are owned by the external runner. When Docker is used, container
creation/start latency is outside the Protos language-startup measurement unless
a separately labelled container-start metric is intentionally being measured.

The complete PERF001 methodology and cross-repository publication contract are
recorded in `docs/project/PERF001_BENCHMARKING.md`.
