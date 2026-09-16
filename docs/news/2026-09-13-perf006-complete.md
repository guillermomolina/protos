# PERF006 complete: Protos reaches the optimizing Truffle runtime

**September 13, 2026**

Today Protos closes one of the most important runtime milestones in the
project so far: **PERF006 is complete**.

Ordinary intended JVM execution now resolves the selected optimizing
Graal/Truffle runtime, the old replay-heavy continuation architecture has been
retired from the production path, and retained exact-source measurements show a
real steady-state benefit from the optimizer.

This is more than a dependency or packaging fix. PERF006 ended up spanning the
execution architecture itself: how Protos suspends and resumes Tasks, composes
Closure calls, preserves `ensure`/Error/non-local-return behavior, exposes
debugger/source identity, and finally reaches Truffle compilation without
replaying already-completed guest work.

## The result

The final PERF006 evidence is pinned to:

```text
Protos revision       4a03efc15620b37b2e418b3df30b4a26486446ec
implementation        0.2.492-SNAPSHOT
Graal / Truffle       25.3.4.1
JDK                   25.0.4.1
runtime               com.oracle.truffle.runtime.hotspot.HotSpotTruffleRuntime
warning suppression   NO
```

Across the intended Maven/Surefire, checkout CLI and portable-distribution
surfaces, Protos now resolves the exact optimizing runtime without suppressing
the fallback warning.

## Controlled optimizer vs fallback measurements

PERF006-D2 compared the optimizing runtime with a deliberately constructed
fallback control using the same Protos source, host JDK, workload sources,
container topology and measurement policy. Only the Truffle optimizer
runtime/compiler closure changed.

The ratio below is:

```text
fallback median / optimizer median
```

so values above `1.0x` mean the optimizer is faster.

| Workload | Startup | Warmup | Steady |
| --- | ---: | ---: | ---: |
| `micro/closure-call` | 0.4718x | 1.3827x | **1.3662x** |
| `micro/method-call` | 0.4835x | 1.3761x | **1.7325x** |
| `runtime/monomorphic-dispatch` | 0.4627x | 1.2808x | **1.4366x** |
| `runtime/polymorphic-dispatch` | 0.5357x | 0.9745x | **1.5049x** |
| `algorithms/fibonacci/recursive` | 0.8555x | 1.0806x | **1.0561x** |

The result is deliberately not reduced to one headline number:

- the optimizer has a real cold/startup cost in this harness;
- warmup behavior is mixed and remains a separate measurement class;
- **all five retained workloads are faster in steady state**;
- the retained steady-state median benefit ranges from about
  **1.06x to 1.73x**.

The raw D2 evidence lives in
[`guillermomolina/protos-benchmarks`](https://github.com/guillermomolina/protos-benchmarks)
at commit
[`7e3c2a9554d7ac48d30e74572460e14aaecb8fec`](https://github.com/guillermomolina/protos-benchmarks/commit/7e3c2a9554d7ac48d30e74572460e14aaecb8fec).

## The replay bottleneck is gone

PERF006 also recovered an older profile from before the C′ cutover.

That historical `bin/protos test --jobs 2` run was dominated by the old replay
cleanup path:

```text
ProtosEvaluatorContinuation.compactCompletedChildExecution()
    -> invocationActivations.keySet().removeIf(...)
```

Its structural profile looked like this:

| Signal | Historical pre-C′ | Current D3 |
| --- | ---: | ---: |
| CPU samples in `main` | 98.530% | 39.411% |
| `HashMap$KeyIterator.next` | 92.201% | **0.000%** |
| `jdk.Deoptimization` | 1,824,883 | **191** |
| Truffle deoptimization | 1,824,544 | **6** |

The two old replay-cleanup symbols are also absent from the current production
source generation.

That is strong evidence that the C′ / PERF006-B6 cutover eliminated the old
serial replay bottleneck.

## What changed architecturally

The decisive change was not “add one Graal dependency”.

PERF006-B moved normal source-backed cooperative execution onto the ratified C′
architecture based on Truffle Bytecode DSL continuations. The work covered,
among other things:

- Bytecode execution roots and canonical lowering;
- Closure/call composition;
- Future/Task suspension and resume;
- cancellation;
- `ensure`, Error handlers and non-local return;
- source sections, instrumentation and debugger compatibility;
- production cutover and retirement of replay-only machinery.

Only after that cutover was it safe to close the optimizer runtime plane across
ordinary execution surfaces.

The retained semantic closure evidence includes:

```text
PERF006 retained suite   267 / 267 PASS
full Maven suite         1913 / 1913 PASS
checkout guest           PASS
public Test Tool stage   PASS
```

## What we are *not* claiming

There are two tempting historical comparisons:

```text
old diagnostic Test Tool run   ~471.985 s
current D3 diagnostic run        77.613 s
```

and an informal complete-Maven observation of roughly:

```text
~20 minutes -> ~8 minutes
```

Neither is an optimizer-only benchmark.

Between those points Protos changed the continuation/replay architecture,
runtime composition and harness generation. The historical numbers are useful
structural evidence, but it would be incorrect to claim that
`HotSpotTruffleRuntime` alone caused those end-to-end improvements.

The controlled optimizer claim is the D2 result above: same-source
optimizer-vs-fallback measurements, with startup, warmup and steady state kept
separate.

## There is already a next performance question

The current real-workload JFR profile no longer shows the replay pathology, but
it does expose a new lead:

```text
ProtosBytecodeRootNodeGen$CachedBytecodeNode.continueAt
32.995658% of current execution samples
```

That is **not** being smuggled into PERF006 as one more optimization.

It now belongs to
[PERF008 — Post-C′ Bytecode `continueAt` hotspot characterization](https://github.com/guillermomolina/protos/issues/496),
which starts audit-first. A hot generated frame is not automatically a defect,
and no cache, runtime change or semantic change is pre-authorized.

## Why this milestone matters

PERF006 gives Protos something much more valuable than a flattering benchmark:

- a production continuation architecture that no longer depends on replaying
  ordinary guest execution;
- a verified optimizing Truffle runtime on intended JVM surfaces;
- retained semantic evidence across calls, suspension, cancellation and unwind;
- reproducible optimizer-vs-fallback measurements;
- current JFR/TraceCompilation evidence showing real guest compilation;
- a clean boundary between runtime integrity and future optimization work.

The runtime can now be optimized from a much healthier starting point.

**PERF006 is closed. C′ is the production execution generation. The optimizing
runtime is real, measured, and no longer hidden behind the old replay
architecture.**

## Evidence and project history

- [PERF006 parent issue](https://github.com/guillermomolina/protos/issues/262)
- [PERF006-B — C′ production continuation backend cutover](https://github.com/guillermomolina/protos/issues/276)
- [PERF006-D — reproducible optimizer performance evidence](https://github.com/guillermomolina/protos/issues/487)
- [PERF006 retained evidence](https://github.com/guillermomolina/protos-project-docs/blob/d1739808ac5ae0f95d46cad98e2629a4e0396b2a/docs/project/evidence/PERF006/README.md)
- [PERF008 follow-up](https://github.com/guillermomolina/protos/issues/496)
- [`protos-benchmarks`](https://github.com/guillermomolina/protos-benchmarks)
