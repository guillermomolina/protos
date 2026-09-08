# PERF004 — Cross-language runtime performance characterization

Status: OPEN

This is a non-normative performance-engineering project record. It does not
define Protos language semantics, performance guarantees, or an optimization
mandate.

PERF004 is intentionally opened but **not started**. It exists so the next
performance effort has a stable question, methodology boundary, and closure
condition when higher-priority work allows it to begin.

## Question

Produce reproducible evidence that answers:

> How fast or slow is current Protos relative to algorithm-equivalent Python,
> JavaScript, and Java workloads; where are the material differences; and which
> runtime mechanisms explain the largest actionable gaps?

The answer must be workload-specific. PERF004 must not generalize one
microbenchmark into a language-wide speed claim.

## Prior evidence and constraints

PERF004 builds on, but does not rewrite:

- `PERF001` corpus, cross-language equivalence discipline, CPU affinity,
  correctness-first measurement, and separation of startup/warmup/steady-state;
- the companion `guillermomolina/protos-benchmarks` harness/evidence repository;
- `PERF003`'s final lesson that compiler diagnostics such as `GraphTooBig` are
  implementation evidence and are not reliable proxies for actual execution
  performance.

The baseline phase may extend the benchmark harness only when required for
reproducible measurement. It must not change canonical Protos semantics or
runtime implementation merely to improve a score.

## Measurement principles

Reference evidence must:

- pin the exact Protos revision and exact companion-harness revision;
- use the same host and an explicit CPU affinity/resource policy for compared
  runtimes where practical;
- validate the exact documented observable result before accepting timing;
- compare materially algorithm-equivalent work and inputs;
- keep startup, warmup, and steady-state as distinct measurement classes;
- retain raw samples and environment/runtime identities;
- report at least median plus a dispersion measure, not a single best run;
- keep heavy compilation/profiling diagnostics separate from timing when they
  perturb execution;
- distinguish measured facts from causal interpretation.

Python, JavaScript, and Java runtime identities/versions are part of retained
evidence, not floating assumptions in this project record.

## Planned slices

| Slice | Status | Scope / closure condition |
|---|---|---|
| PERF004-A | OPEN | Publish the cross-language baseline for the selected existing algorithm-equivalent corpus: correctness, startup/warmup/steady-state separation, raw samples, medians, dispersion and Protos-to-Python/JavaScript/Java ratios on one reproducible host policy. No Protos optimization is allowed in A. |
| PERF004-B | BLOCKED_BY_DEPENDENCIES | Depends on A. Attribute only material Protos gaps using the least intrusive evidence needed: CPU/hotspots, allocation/GC, warmup/JIT behavior and relevant runtime/compiler diagnostics. Produce mechanism-level explanations; do not optimize yet. |
| PERF004-C | BLOCKED_BY_DEPENDENCIES | Depends on B. Rank the 3–5 highest-value optimization opportunities by measured impact, generality, semantic risk and expected return. This slice produces priorities, not implementation changes. |

PERF004 remains OPEN until work deliberately starts. Publishing this planning
record alone must not move PERF004-A to IN_PROGRESS.

## Material-gap rule

PERF004-B must not profile every benchmark indiscriminately. A must define a
documented material-gap threshold from its observed distribution and measurement
noise. B investigates only workloads whose gap clears that threshold or whose
behavior is otherwise necessary to explain a contradictory result.

This prevents profiling effort from becoming another open-ended search.

## Attribution vocabulary

When supported by evidence, B should attribute costs to mechanisms such as:

- Closure invocation / argument and parameter binding;
- activation creation and retained execution state;
- slot lookup / polymorphic dispatch;
- Array/Map representation and access;
- allocation pressure and garbage collection;
- warmup / compilation convergence;
- interpreter-to-optimizing-runtime transition;
- other runtime mechanisms directly demonstrated by profiles.

These are investigation categories, not presumed root causes.

## Closure criteria

PERF004 closes only when published retained evidence can answer all five
questions:

1. Where does current Protos stand relative to Python, JavaScript, and Java for
   each selected algorithm-equivalent workload and measurement class?
2. Which differences are material relative to the observed measurement noise?
3. Which runtime mechanisms explain the largest material Protos gaps?
4. Which workloads are already competitive or show no actionable deficit?
5. What are the 3–5 highest-value optimization opportunities, ordered by
   evidence-backed expected return?

The canonical ledger must then reconcile the companion evidence. Closing PERF004
does not require implementing those optimizations.

## Non-goals

PERF004 does not:

- promise that Protos will beat another language;
- use `GraphTooBig`, node count, or compiler success count as a speed score;
- publish benchmark-specific semantic shortcuts or privileged runtime paths;
- alter the Protos specification;
- optimize before baseline and attribution evidence exist;
- require PERF001's unrelated future/concurrency benchmark continuation to close
  before this characterization can begin.

Any later executable optimization should receive its own PERF work item or other
appropriate canonical implementation item and cite PERF004 evidence.
