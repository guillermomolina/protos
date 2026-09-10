# Reserved benchmark family

Workloads belong here only when they can be expressed without adding assumptions beyond the corresponding normative Protos model.
## PERF001-F canonical contracts

`PERF001-F` owns the first canonical concurrency workload family. Its approved
methodology is documented in
`docs/project/work/PERF001/PERF001_F_CONCURRENCY_METHODOLOGY.md`.

The six canonical workload identifiers are materialized by these sources:

- `concurrency/future-roundtrip` -> `future-roundtrip.protos`
- `concurrency/future-fanout-all` -> `future-fanout-all.protos`
- `concurrency/parallel-roundtrip` -> `parallel-roundtrip.protos`
- `concurrency/parallel-array-map` -> `parallel-array-map.protos`
- `concurrency/actor-request-roundtrip` -> `actor-request-roundtrip.protos`
- `concurrency/actor-fanout-requests` -> `actor-fanout-requests.protos`

Each source defines one ordinary top-level `run` Closure and ends by invoking
`run()` so the corpus remains directly executable for correctness checks. Actor
sources perform spawn/readiness setup before `run`; a steady-state harness may
retain that production-hosted Process and time later ordinary `run` invocations
without including Actor bootstrap in the per-request timing window.

These are Protos-native workload contracts, not cross-language concurrency
analogues. They must remain expressible entirely through the corresponding
normative Protos Future/P/Actor model and ordinary language-visible operations.
The two scaling workloads keep total logical work fixed while the harness varies
an explicit topology-audited physical-core CPU set.

Do not add sleeps, scheduler polling, host-thread/executor introspection, hidden
benchmark-only authority, or benchmark-only semantics to make these workloads
run or scale.
