# Reserved benchmark family

Workloads belong here only when they can be expressed without adding assumptions beyond the corresponding normative Protos model.
## PERF001-F canonical contracts

`PERF001-F` owns the first canonical concurrency workload family. Its approved
methodology is documented in
`docs/project/PERF001_F_CONCURRENCY_METHODOLOGY.md`.

The implementation slice must materialize exactly these canonical workload
identifiers here:

- `concurrency/future-roundtrip`
- `concurrency/future-fanout-all`
- `concurrency/parallel-roundtrip`
- `concurrency/parallel-array-map`
- `concurrency/actor-request-roundtrip`
- `concurrency/actor-fanout-requests`

These are Protos-native workload contracts, not cross-language concurrency
analogues. They must remain expressible entirely through the corresponding
normative Protos Future/P/Actor model and ordinary language-visible operations.
The two scaling workloads keep total logical work fixed while the harness varies
an explicit topology-audited physical-core CPU set.

Do not add sleeps, scheduler polling, host-thread/executor introspection, hidden
benchmark-only authority, or benchmark-only semantics to make these workloads
run or scale.
