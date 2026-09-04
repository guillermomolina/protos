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
