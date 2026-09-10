# Protos benchmark corpus

The benchmark corpus is non-normative. It provides deterministic workloads for comparing Protos implementations without making performance characteristics part of language semantics or conformance.

Benchmark source files use `.protos`. Portable workloads do not self-time: an external runner controls warm-up, repetitions, timing, profiling, and implementation-specific metrics. A measurement is valid only when the workload produces its documented result.

Benchmark correctness is exposed as the program's final expression value. The
external launcher/runner owns rendering and capture of that value; corpus
workloads do not depend on an unqualified `print` binding.

Runtime configuration must accommodate the canonical workload shape rather than
rewrite it to avoid host defaults. In particular, the fixed-count micro/runtime
drivers intentionally retain their recursive repetition structure. A benchmark
runtime may provision a larger call stack when required, but that runtime option
must be fixed and recorded as part of the retained run identity.

The first corpus intentionally uses only mechanisms already defined clearly enough by the current normative specifications. Algorithm variants that require a standardized growable sequence or additional iteration protocols should be added only after those contracts are defined; benchmark code must not invent missing library semantics.

Initial families:

- `micro/` — small language/runtime mechanisms.
- `algorithms/` — implementation-independent algorithms.
- `runtime/` — workloads especially relevant to delegation and dispatch.
- `collections/` — sequential closed Core/Standard-Library collection workloads.
- `concurrency/` — reserved for concurrency workloads grounded in the normative concurrency model.
- `io/` — reserved for workloads grounded in the normative I/O model.

## PERF001-E collection workload contracts

The first PERF001-E corpus extension is intentionally limited to already-closed
sequential collection behavior. It adds no collection semantics.

| Workload | Fixed work shape | Expected final value |
|---|---|---:|
| `collections/array-map` | 500 eager maps over 16 Integers; transform `value * 3` | `408` |
| `collections/array-filter` | 250 eager filters over 32 Integers; retain values `< 17` | `136` |
| `collections/array-reduce` | 1000 eager left reductions over 32 Integers | `528` |
| `collections/array-sort` | 100 stable merge-sort runs over the same 32-Integer permutation | `321601` |
| `collections/map-lookup-update` | 1000 `Map.at`/`atPut` update-and-lookup steps over 16 Integer keys | `1250` |
| `collections/set-algebra` | 250 `union`/`intersection`/`difference` rounds over two overlapping 16-member Sets | `240808` |

Comparison-language implementations must preserve these explicit loops,
collection sizes, callback work, inputs and observable results. They must not
replace the Array or Set algorithms with host bulk primitives when that would
perform materially different work.

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
recorded in `docs/project/work/PERF001/PERF001_BENCHMARKING.md`.
