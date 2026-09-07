# Truffle/Graal optimization investigation reference

This document is a non-normative engineering reference for investigating
optimization and compilability problems in the Protos Truffle implementation.
It is intentionally not a step-by-step guide and does not define language
semantics, project status, benchmark closure criteria, or performance guarantees.

Its purpose is to preserve the investigation model, evidence hierarchy,
interpretation rules, and failure modes that proved useful while working on
PERF003, so that later performance problems can reuse the reasoning without
repeating the same exploratory mistakes.

## Governing principle

Optimization work starts from observable Protos semantics and must preserve them.
A compiler/JIT symptom is implementation evidence, not permission to change the
language contract.

The useful mental model is:

```text
Protos source
    -> Truffle AST / call-target structure
    -> partial evaluation
    -> Graal IR
    -> machine code or bailout
```

Graal optimizes the program representation that the Truffle implementation makes
visible to partial evaluation. A bailout therefore does not automatically imply
that the guest algorithm is wrong or even slow; it says that one attempted
compilation could not be completed under the current implementation/runtime
constraints.

The investigation should distinguish four questions:

1. Is observable Protos behavior correct?
2. Which compilation actually fails?
3. What implementation structure makes its graph grow?
4. Which change reduces that structure without changing semantics or merely
   moving an implementation limit?

## Evidence classes

Different evidence answers different questions. Mixing them leads easily to
false conclusions.

### Semantic correctness evidence

Interpreter and optimizing-runtime executions must first produce the exact
expected observable result. A compilation bailout with `rc=0` and the expected
result is not a language-correctness failure.

Correctness evidence should exercise semantic boundaries directly, especially
when an optimization rewrites control structure. For collection algorithms this
can include callback order/count, initial-value behavior, snapshot timing,
mutation visibility, error propagation, and repeated invocation state.

### Compilation symptom evidence

`engine.TraceCompilation` is useful for identifying:

- which call target failed;
- tier;
- successful versus failed compilations;
- AST and IR sizes where available;
- bailout reason;
- generated-code size for successful targets.

Treat `opt_failed` as the count of failed compilations. Do not infer this number
from the number of textual occurrences of an exception in a diagnostic dump:
`DiagnoseFailure`, stack traces, and repeated diagnostic sections can print the
same failure several times.

### Root ownership evidence

A raw name such as `ProtosRootNode@4e56ec74` is weak evidence. A useful diagnostic
runtime should, when necessary, attach enough temporary metadata to map the
failing root to:

- source identity;
- Truffle body-node class;
- source span;
- exact guest source fragment.

This attribution may be implemented only in a diagnostic image and need not be a
production runtime feature.

Root ownership and graph contribution are different concepts. If the root is the
body of `Array.reduce`, that says where compilation was scheduled; it does not by
itself prove that the `reduce` source text is the dominant graph-growth owner.
Nested calls, parameter binding, dispatch, activation creation, runtime helper
methods, and other expanded machinery may dominate the IR.

### Partial-evaluation structure evidence

When source-level attribution is no longer enough, use Truffle/Graal structural
diagnostics rather than continuing blind source micro-edits.

Useful diagnostic surfaces include, subject to the exact Truffle/Graal version:

- `engine.TraceCompilation`;
- `compiler.TraceInlining`;
- `compiler.TraceInliningDetails`;
- `compiler.TraceMethodExpansion`;
- `compiler.TraceNodeExpansion`;
- `compiler.MethodExpansionStatistics`;
- `compiler.NodeExpansionStatistics`;
- `compiler.NodeSourcePositions`;
- `compiler.TracePerformanceWarnings`;
- `compiler.DiagnoseFailure`;
- Graal `Dump=Truffle:2` / file graph dumps for Ideal Graph Visualizer;
- Graal node source-position tracking.

Option names are runtime-version-specific engineering interfaces. Verify them
against the Truffle/Graal version being measured rather than copying a historic
command line blindly.

The important outputs are not merely graph files. Retain compact textual evidence
that answers:

- which host methods expanded most;
- which Truffle nodes expanded most;
- which call targets were considered/inlined;
- which control-flow splits or indirections came from the implementation rather
  than the guest program;
- where graph growth occurs before the bailout.

### Controlled falsification evidence

Before publishing an architectural change, prefer a temporary diagnostic variant
that changes one suspected mechanism while keeping the workload, Protos revision,
harness, runtime, stack policy, and expected result fixed.

A useful experiment has a prediction. Examples:

```text
Hypothesis:
  recursive guest-target inlining dominates graph growth.

Prediction:
  disabling or constraining that inlining should materially reduce graph size.

Result:
  graph is byte-for-byte / node-for-node unchanged.

Conclusion:
  this configuration-controlled inlining mechanism is not the dominant owner;
  do not publish an optimization based on that hypothesis.
```

A no-change result is valuable evidence. Record it.

## When to stop microoptimizing guest source

Small source rewrites are justified while they demonstrate a material structural
trend and preserve semantics. They become poor engineering when the investigation
turns into trying to cross a compiler threshold by deleting incidental
expressions.

Warning signs include:

- several distinct rewrites converge on essentially the same graph size;
- improvements shrink from hundreds of graph units to tens or single digits;
- the graph remains dominated by expanded runtime machinery;
- a candidate would be published only because it happens to produce `149999`
  instead of `150001`;
- the proposed change cannot explain why the graph is large.

At that point move from source editing to attribution and IR inspection.

The target is not "make the number fall below the current limit by any means".
The target is to understand and improve the representation that generated the
large graph.

## Interpreting common observations

| Observation | What it supports | What it does not prove |
|---|---|---|
| Correct result plus `GraphTooBig` | semantics survived; one compilation bailed out | workload is necessarily slow |
| Source rewrite greatly reduces failures | guest source/control shape materially influences partial evaluation | source is the only remaining owner |
| Failing root maps to one guest function | compilation is scheduled for that function body | every large IR contribution originates in that source body |
| Runtime helper dominates method-expansion statistics | implementation machinery is materially present in the graph | that helper alone is the root cause |
| Inlining-policy experiment changes nothing | that particular configurable inlining mechanism is not controlling the graph | all forms of inlining/call-target expansion are irrelevant |
| Raising Graal graph limit makes compilation succeed | failure is threshold-related | implementation architecture is healthy |
| `@TruffleBoundary` removes bailout | boundary prevents expansion | resulting code is faster or architecturally correct |

Presence is not causality. A method appearing prominently in an expansion tree is
a candidate for investigation, not an automatic patch target.

## Boundary and limit experiments

Two experiments are often diagnostically useful but weak as final solutions.

### Raising graph limits

Increasing the maximum Graal graph size can establish that compilation would
otherwise complete. It does not explain the graph and can simply move the next
failure to a larger workload.

Use it to classify the failure, not to declare the language optimized.

### Truffle boundaries

A boundary can demonstrate that expansion across a host method is responsible for
a large portion of the graph. It also prevents optimization across that boundary.

A boundary is therefore a useful isolation experiment. Publishing it requires
performance evidence showing that the lost optimization opportunity is acceptable
and that the boundary represents a sound general implementation boundary rather
than a benchmark-specific escape hatch.

## Call structure deserves separate analysis

Guest-language calls are especially important because the implementation shape
can determine how much host/runtime machinery partial evaluation sees.

For each hot callable path, distinguish:

- guest-level lookup/dispatch;
- closure invocation preparation;
- activation creation;
- argument/default/rest binding;
- return-home / non-local-return machinery;
- the actual guest body call target;
- whether Truffle call nodes (`DirectCallNode`, `IndirectCallNode`, or an
  equivalent cache) model stable call sites;
- direct Java invocation of `CallTarget.call(...)` or other generic runtime
  entry paths;
- recursive guest calls versus recursive host expansion.

Do not assume that replacing a `CallTarget.call(...)` with a call node is
correct merely because a dump contains `OptimizedCallTarget` methods. The dump
motivates the experiment; a controlled variant must prove that the call
representation materially changes the graph and preserves behavior.

## Reproducibility boundary

Every retained performance/compilability claim should identify at least:

- exact Protos revision;
- exact companion harness/evidence revision;
- exact workload and expected result;
- GraalVM/JDK identity;
- Truffle runtime version;
- stack policy and relevant VM/compiler options;
- CPU affinity/resource policy where material;
- whether instrumentation was enabled.

Keep timing runs separate from heavy compilation diagnostics when the diagnostic
instrumentation can materially perturb timing.

Diagnostic images may temporarily replace runtime classes to add source labels or
instrumentation, but such runs must be explicitly labelled as diagnostic and must
not be confused with reference performance numbers.

## Useful retained artifacts

For a difficult optimization investigation, useful retained evidence can include:

```text
summary.txt
compilation-trace.txt
graph-too-big.txt
root-attribution.txt
method-expansion-statistics.txt
node-expansion-statistics.txt
inlining-details.txt
performance-warnings.txt
graal_dumps/*.bgv
experiment-matrix.tsv
```

The compact textual summary should be sufficient to recover the main conclusion
without reopening IGV. The raw dump exists for deeper inspection and later
reanalysis.

A diagnostic artifact is especially valuable when it records not only a positive
finding but also experiments that disproved plausible hypotheses.

## PERF003 investigation as a worked reference

The following is historical engineering evidence from the PERF003 investigation.
It is included only to preserve the reasoning pattern; canonical PERF003 status
and closure evidence remain owned by the project ledger.

The original `array-reduce` evidence recorded 40 `GraphTooBig` compilation
failures while still producing the correct result.

A source-level redesign to balanced ordinary-Protos traversal reduced the failure
count to 2. Subsequent semantics-preserving refinements moved the failing graph
through approximately:

```text
150500
150069
150036
150026
```

A read-only candidate matrix produced approximately `150017` for several
different micro-rewrites. That convergence was the signal to stop publishing
small source edits: the investigation was approaching a compiler threshold
without explaining the residual graph.

Root attribution then established that the failing target was the complete
`Array.reduce` body, not a separately scheduled `foldRange` or reducer root.

A full Graal/Truffle structural dump reproduced the same semantic result and the
same `150026 / 150000` bailout while generating IGV graphs and method/node
expansion statistics. The textual expansion evidence prominently included
`ProtosParameterBindingNode` and `OptimizedCallTarget` machinery. This made
closure/call-target representation a reasonable architectural hypothesis, but
not yet a conclusion.

A subsequent controlled inlining matrix tested:

```text
default
compiler.Inlining=false
compiler.InliningRecursionDepth=0
compiler.FirstTierInliningPolicy=None
```

All variants preserved the result and produced exactly the same observed
compilation outcome (`25` successful optimizing compilations, `2` failed
compilations, graph size `150026`, node count `50681`). Therefore those
configuration-controlled guest-target inlining policies provided no causal
signal and should not be revisited without new evidence.

This sequence is the main reusable lesson:

```text
correctness
  -> symptom count
  -> material source-shape improvement
  -> diminishing returns
  -> root attribution
  -> full partial-evaluation / IR dump
  -> controlled hypothesis falsification
  -> only then choose an architectural candidate
```

## Open architectural hypotheses after the PERF003 snapshot

The PERF003 evidence at the time this reference was written leaves several
questions intentionally unresolved:

- whether direct `CallTarget.call(...)` use in closure execution exposes avoidable
  Truffle runtime machinery compared with explicit call nodes/call-site caching;
- how much parameter binding contributes independently of the call-target entry
  path;
- whether activation/return-home setup materially contributes to hot-path graph
  growth;
- whether a general callable dispatch specialization can reduce graph growth
  across unrelated workloads, not only collection benchmarks;
- whether the zero-bailout closure criterion should remain the right long-term
  compilability criterion after actual performance impact is measured.

These are hypotheses, not accepted design decisions.

## Reference decision model

When a future Truffle/Graal problem appears, the most useful classification is
usually not "what optimization can we try?" but "what kind of evidence do we
currently have?"

```text
Only symptom evidence
    -> attribute the failing compilation.

Known failing guest root, unknown graph owner
    -> collect method/node expansion and IR dumps.

Plausible graph owner
    -> construct a controlled temporary variant with a concrete prediction.

Experiment changes graph materially
    -> audit generality and semantics; design production candidate.

Experiment changes nothing
    -> record falsification and move to a different owner/mechanism.

Production candidate passes correctness and compilability
    -> measure actual performance separately.

Performance improves without semantic/regression cost
    -> retain reproducible before/after evidence and publish.
```

This is a reasoning reference, not a mandatory sequence. Some problems can be
localized much earlier; others require deeper compiler/runtime tooling. The
important invariant is that each implementation change should be justified by
specific evidence rather than by proximity to a compiler threshold.

## External implementation references

For the exact runtime being investigated, consult the corresponding version of
Oracle Graal/Truffle documentation rather than assuming current option names or
defaults. The most relevant upstream references are the Truffle optimization and
compiler-options documentation, including the sections on observing compilations,
method/node expansion, inlining diagnostics, graph dumps, and Ideal Graph
Visualizer.
