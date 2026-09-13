# PERF006 immutable validation evidence

## PERF006-C3 — optimizer-enabled retained/full validation

Status: **CLOSED**

This evidence records the validation-only PERF006-C3 closure. It changes no
Protos implementation, specification, runtime architecture, dependency
authority, Graal/Truffle version, or implementation version.

The validated source revision already contains the published C3C full-suite
harness reconciliation, C3D P/Bytecode Context-projection closure, C3F PLAT030
release-suspension carrier correction, and C3E post-cutover full-suite harness
and Package Tool reconciliation. C3 does not reimplement those prerequisites;
it proves the complete retained and Maven test surfaces over their published
result.

```text
validated_source_revision=c15ac425fcc976e42b6d97638c5a890e97fdd035
implementation_version=0.2.492-SNAPSHOT
validated_at_utc=2026-09-13T13:50:06Z
optimizing_runtime=com.oracle.truffle.runtime.hotspot.HotSpotTruffleRuntime
graal_truffle_version=25.3.4.1
warning_suppression=NO
c3d_required_ancestor=a1697479733b881789e694b46250dd310c9b9c42
c3c_required_ancestor=b6ce0154a0b6e26fc48e20afa8456abf48df2d81
c3f_required_ancestor=020a8206061f70f89f13fb298f47a37f000ce1b9
c3e_required_ancestor=c15ac425fcc976e42b6d97638c5a890e97fdd035
```

### Retained PERF006 validation

The complete retained Java test family selected by `ProtosPerf006*Test` ran
under Maven/Surefire with C1 proving the exact optimizing runtime.

```text
tests=267
failures=0
errors=0
skipped=0
surefire_reports=62
result=PASS
```

### Complete Maven validation

The complete `mvn test` suite then ran from the same immutable source revision.
The full run itself included the C1 runtime-identity assertion, so the green
suite is evidence under `HotSpotTruffleRuntime`, not merely under the fallback
Truffle runtime.

```text
tests=1913
failures=0
errors=0
skipped=0
surefire_reports=421
result=PASS
```

### Ordinary guest and self-hosted test-tool validation

After the full Maven suite:

- `mvn -DskipTests package` rebuilt the checkout artifact and canonical runtime
  plane;
- `bin/protos -e` executed a real guest program successfully through the
  checkout launcher;
- no fallback-runtime warning was observed on that guest execution; and
- `bin/protos test --jobs 2` passed as the repository's second-stage Protos Test
  Tool validation.

### Debt paid by C3

The deferred retained/full semantic test debt accumulated through PERF006-B,
PERF006-C1 and PERF006-C2 is paid for the validated source revision above.

C3 does **not** close PERF006-C or parent PERF006. The next bounded slice is
PERF006-C4, which owns final runtime-identity/warning closure across intended
ordinary surfaces. Final performance characterization remains PERF006-D.

## PERF006-C4 — runtime identity and fallback-warning closure

Status: **CLOSED**

C4 validates the final PLAT033 runtime-identity contract after C3 full semantic
validation. It changes no Protos implementation, specification, runtime
architecture, dependency authority, Graal/Truffle version, or implementation
version.

```text
validated_source_revision=428e46523e8fa0b3f0260b5a6e76c198725c041b
implementation_version=0.2.492-SNAPSHOT
validated_at_utc=2026-09-13T13:59:43Z
optimizing_runtime=com.oracle.truffle.runtime.hotspot.HotSpotTruffleRuntime
graal_truffle_version=25.3.4.1
warning_suppression=NO
truffle_fallback_warning=ABSENT_ALL_INTENDED_SURFACES
```

### Intended optimizer surfaces

```text
Maven/Surefire                         PASS exact HotSpotTruffleRuntime
checkout runtime plane                 PASS exact HotSpotTruffleRuntime
checkout ordinary guest execution      PASS no fallback warning
checkout Package Tool                  PASS no fallback warning
checkout Test Tool                     PASS no fallback warning
portable runtime plane                 PASS exact HotSpotTruffleRuntime
portable ordinary guest execution      PASS no fallback warning
portable Package Tool                  PASS no fallback warning
portable Test Tool                     PASS no fallback warning
checkout/dist runtime manifest         PASS byte-for-byte identity
```

The fallback-warning detector rejects both the historical
`No optimizing Truffle runtime found` form and the current Polyglot
interpreter-only/runtime-compilation warning family. Neither
`polyglot.engine.WarnInterpreterOnly=false` nor
`truffle.UseFallbackRuntime=true` is used.

JDK native-access and `sun.misc.Unsafe::objectFieldOffset` warnings are
classified separately as host-JDK/upstream runtime deprecation diagnostics.
They are not interpreter-fallback evidence and are not suppressed by C4.

### PERF006-C consequence

PERF006-C1, C2, C3 and C4 are now closed. The PLAT033 Candidate A-prime
runtime plane is proven across all intended ordinary JVM surfaces. PERF006-C is
therefore complete. Parent PERF006 remains open for the dedicated performance
evidence/closure track; no parent closure is claimed by C4.

## PERF006-D4 — interpretation and parent closure

Status: **CLOSURE EVIDENCE READY**

D4 is governance/documentation-only. It does not rerun or reinterpret the
measurement machinery, change Protos implementation or specification, alter the
selected runtime plane, change Graal/Truffle, or change the implementation
version.

The canonical performance evidence is intentionally pinned to the PERF006
runtime baseline rather than to the later repository head on which this closure
document is published:

```text
protos_evidence_revision=4a03efc15620b37b2e418b3df30b4a26486446ec
protos_evidence_version=0.2.492-SNAPSHOT
graal_truffle=25.3.4.1
jdk=25.0.4.1
runtime=com.oracle.truffle.runtime.hotspot.HotSpotTruffleRuntime
warning_suppression=NO
```

### D2 — controlled optimizer versus fallback result

Retained evidence is published in `guillermomolina/protos-benchmarks` at
`7e3c2a9554d7ac48d30e74572460e14aaecb8fec`, using exact D2A harness
`1a752e92569b4ed42d3f9f55f67d1a7447eae308`.

The ratio is `fallback median / optimizer median`, so values above 1 mean that
the optimizer is faster.

| Workload | Startup | Warmup | Steady |
| --- | ---: | ---: | ---: |
| `micro/closure-call` | 0.4718x | 1.3827x | **1.3662x** |
| `micro/method-call` | 0.4835x | 1.3761x | **1.7325x** |
| `runtime/monomorphic-dispatch` | 0.4627x | 1.2808x | **1.4366x** |
| `runtime/polymorphic-dispatch` | 0.5357x | 0.9745x | **1.5049x** |
| `algorithms/fibonacci/recursive` | 0.8555x | 1.0806x | **1.0561x** |

The controlled evidence therefore supports all of these conclusions:

- the optimizing runtime has a real cold/startup cost in this harness;
- the optimizing runtime is faster in steady state for all five retained
  canonical workloads;
- the observed steady median benefit ranges from approximately **1.06x to
  1.73x**;
- warmup behavior is mixed enough that it must remain separate from steady
  state; and
- optimizer availability is materially relevant to ordinary Protos guest
  execution, but it is not a universal claim that every Protos invocation is
  faster from process start.

D2 retains ten startup samples per runtime/workload and five persistent forks
per runtime/workload, each with twenty warmup and twenty steady samples.
Correctness and exact runtime identity were gates before timing was accepted.

### D3 — current structural/runtime diagnosis

Retained D3 evidence is published in `guillermomolina/protos-benchmarks` at
`ecfa8fb3a23f5661524b7eb812e16e75c833ed7c`, from exact D3A harness
`297ccb4fc94a0f0b0c9e0a65422aba2e223c4a83`.

The representative repository-real workload is `bin/protos test --jobs 2` on
two physical cores. Its current JDK-25 JFR/TraceCompilation headline is:

```text
diagnostic_wall_seconds=77.613
execution_samples=8292
main_thread_percent=39.411481
top_frame=ProtosBytecodeRootNodeGen$CachedBytecodeNode.continueAt
top_frame_percent=32.995658
HashMap$KeyIterator.next_percent=0.000000
jdk.Deoptimization=191
jdk.graal.compiler.truffle.Deoptimization=6
compactCompletedChildExecution_present=FALSE
invocationActivations.keySet().removeIf_present=FALSE
TraceCompilation_successful_compilations=12
TraceCompilation_failure_markers=6
TraceCompilation_bailout_markers=15
```

The recovered historical pre-C-prime profile remains structural context only:

```text
historical_wall_seconds_approx=471.985
historical_execution_samples=44568
historical_main_thread_percent=98.530
historical_HashMap$KeyIterator.next_percent=92.201
historical_jdk.Deoptimization=1824883
historical_truffle_Deoptimization=1824544
historical_hotspot=ProtosEvaluatorContinuation.compactCompletedChildExecution
historical_operation=invocationActivations.keySet().removeIf(...)
```

D4 therefore concludes that the old replay bottleneck is no longer present in
the current C-prime runtime:

- the exact historical `HashMap$KeyIterator.next` hotspot has zero sampled share;
- both historical replay-cleanup symbols are statically absent;
- CPU is no longer almost completely concentrated in `main`;
- the historical deoptimization storm is absent by orders of magnitude; and
- bounded current-toolchain TraceCompilation proves real guest compilation is
  occurring.

This is evidence that the C-prime / PERF006-B6 cutover eliminated the old
serial replay bottleneck. It is **not** evidence that the optimizer alone
caused a `~471.985 s -> 77.613 s` speedup. Those wall times span different
runtime architecture, replay behavior and diagnostic/harness generations and
must not be used as an optimizer-only ratio.

Likewise, the informal historical complete-Maven observation of roughly
20 minutes versus roughly 8 minutes is not controlled PERF006 evidence and
remains excluded from causal optimizer claims.

### Remaining hotspot ownership

The current real-workload profile has a new dominant frame,
`ProtosBytecodeRootNodeGen$CachedBytecodeNode.continueAt`, at approximately
32.996%, with additional generated Builder frames visible below it.

That observation does not invalidate PERF006 runtime integrity and is not
optimized inside PERF006-D. It is transferred to independent
**PERF008 / GitHub #496 — Post-C-prime Bytecode continueAt hotspot
characterization**, which is audit-first and does not pre-authorize a cache,
runtime change or semantic change.

### PERF006-B deferred closure debt

PERF006-B deliberately left final retained/full validation to later PERF006
closure work. That debt is now satisfied by the combined evidence:

- PERF006-C3: retained PERF006 `267/267` PASS;
- PERF006-C3: complete Maven `1913/1913` PASS;
- PERF006-C3: checkout guest execution PASS;
- PERF006-C3: public Test Tool second stage PASS;
- PERF006-C4: exact optimizing runtime on every intended JVM surface, with no
  fallback-warning suppression; and
- PERF006-D3: old replay cleanup path and its historical runtime hotspot are
  absent from the current production generation.

The PERF006-B closure rule is therefore satisfied without reopening or changing
the already-published C-prime implementation.

### Final PERF006 conclusion

PERF006's closure rule is satisfied:

1. intended ordinary Maven/Surefire, checkout and portable-distribution
   execution resolve the selected exact `HotSpotTruffleRuntime`;
2. fallback warnings are absent on intended surfaces without suppression;
3. exact-source controlled timing demonstrates material steady-state optimizer
   benefit while keeping startup/warmup separate;
4. the current representative real workload reaches Truffle compilation and
   does not exhibit the historical replay/deoptimization pathology;
5. raw timing and diagnostic evidence is retained under exact benchmark harness
   and Protos SHAs;
6. PLAT014 and PLAT033 are already ratified and no unresolved platform/runtime
   decision remains; and
7. the remaining post-C-prime optimization lead is isolated under PERF008
   rather than extending PERF006 indefinitely.

Accordingly PERF006-B, PERF006-D and parent PERF006 are ready to close as
`completed`. PERF008 remains independent follow-up work.
