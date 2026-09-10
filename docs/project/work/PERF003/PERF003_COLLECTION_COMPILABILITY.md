# PERF003 — Collection algorithm Truffle compilability

Status: CLOSED

This is a non-normative project record. It tracks performance engineering
discovered by `PERF001-E`; it does not define Protos language or Standard Library
semantics.

## Originating evidence

- harness: `guillermomolina/protos-benchmarks@280173d743b2ed838a89be0ad930b20828d89558`;
- evidence: `guillermomolina/protos-benchmarks@4bff9f7f6c5e0e006530f166c188e0e988acf565`;
- pinned Protos corpus revision: `86b35d8bb2d7ab2ad54bc2947e1bf7fbff1fca15`;
- correctness: 18/18 Protos/Python/JavaScript cases PASS;
- retained result records: 54;
- runtime: GraalVM Community JDK 22 + external `truffle-runtime:24.0.0`;
- Protos stack: `-Xss128m`.

| Workload | rc | opt done | opt failed | GraphTooBig |
|---|---:|---:|---:|---:|
| `collections/array-reduce` | 0 | 236 | 40 | 40 |
| `collections/array-sort` | 0 | 650 | 40 | 40 |

The baseline remains valid: both workloads execute correctly. The bailout is a
performance finding, not a correctness failure.

## Objective

Improve optimizing-Truffle compilability of the already-defined sequential Array
algorithms while preserving every observable Protos and Standard Library
contract. No benchmark-specific or privileged execution path is allowed.

## Semantic invariants

The optimization must preserve:

- Array shallow pre-callback snapshot semantics;
- exact `reduce` left-fold and zero-or-one initial-value behavior;
- ordinary polymorphic reducer invocation and callback effects/failures;
- stable `sort` merge-tree result/order;
- strict two-direction comparator Boolean validation;
- `InvalidComparatorResult` / `InvalidComparatorOrder`;
- ordinary Closure/lookup/invocation/receiver/lexical-context semantics;
- the generic semantic path against which the optimization is tested.

## Planned slices

| Slice | Status | Scope / closure condition |
|---|---|---|
| PERF003-A | CLOSED | Final A4 evidence exhausted the justified local production hypotheses. A4g proved combined immediate-method preparation plus `invokePrepared` expansion causality, while A4h and A4i falsified the production-shaped sync/task split and preparation-only boundary. The residual is accepted as a characterized compiler-threshold limitation rather than converted into a broad opaque runtime boundary. |
| PERF003-B | CLOSED | Companion evidence is published through `guillermomolina/protos-benchmarks@433ebb8075148493d4eae8da701803b21ab50c09`, including exact correctness `528`, control reproduction, retained A4g/A4h/A4i diagnostics, and separate no-trace timing evidence. |

PERF003 is CLOSED. PERF003-B evidence is published and reconciled into the
canonical Protos ledger below.

## Investigation boundary

The A audit must identify the real graph-growth owner before choosing an
optimization: source-backed `Array.each`, reducer/comparator invocation
structure, recursive/merge algorithm shape, CallTarget/node structure, or another
implementation-only source revealed by diagnostics.

## PERF003-A implementation-phase finding

The current source audit identifies the shared high-growth shape as a large
user-callback body nested inside standard native `Array.each`. `array-map` also
uses `each` but its callback body is materially smaller and did not hit the
PERF001-E graph limit.

Rather than adding a per-element Truffle boundary to generic Core `Array.each`,
this implementation keeps the optimization entirely in ordinary Standard
Library Protos source. `reduce` uses a balanced recursive range fold whose left
half completes before the right half, preserving strict left-fold order with
O(log n) helper recursion depth. The `sort` merge pass likewise visits output
positions in strictly ascending order through a balanced range traversal,
preserving the existing left/right cursor transitions, comparator call pairs,
stable-equality choice, errors and writes. The outer stable merge tree is
unchanged.

No Java native protocol, benchmark identity check, Truffle boundary, runtime
value family or normative behavior is added. Exact external optimizing-runtime
evidence remains required before closing PERF003-A.

## PERF003-A external diagnostic and reduce refinement

The exact external diagnostic against Protos
`4b2d1c661ed943e51253ec44a324b45e798e1666` with companion harness/evidence
`guillermomolina/protos-benchmarks@4bff9f7f6c5e0e006530f166c188e0e988acf565`,
GraalVM Community JDK 22, external Truffle `24.0.0`, and `-Xss128m` established:

- `collections/array-reduce` interpreter correctness: PASS, exact result `528`;
- optimizing-runtime correctness: PASS, exact result `528`;
- optimizing compilations completed: 25;
- optimization failures: 2, both `GraphTooBig`;
- failing graph node count: 51951; graph size: 150500; configured limit: 150000.

This is a large reduction from PERF001-E's 40 `GraphTooBig` failures, but it does
not satisfy PERF003-A's zero-bailout closure condition. PERF003-A therefore remains
IN_PROGRESS and PERF003-B remains BLOCKED_BY_DEPENDENCIES. The valid diagnostic
stopped at the failing reduce gate, so no conclusion about the optimized `sort`
rewrite is recorded from that run.

The 0.2.175-SNAPSHOT corrective phase changes only ordinary Protos source in
`std:collections/Array.reduce`. The balanced range helper still visits the left
half completely before the right half, but it now updates the enclosing
invocation-local `accumulator` binding exactly as the pre-PERF003 `Array.each`
implementation did. Removing the recursive accumulator parameter, local result,
left-result temporary, and recursive value return reduces graph shape without a
new runtime primitive, Truffle boundary, benchmark identity check, or semantic
shortcut. A 32-element repeated-reduction Protos conformance case guards exact
left-fold results, reducer invocation count, and non-leakage of accumulator state
between calls.

Exact external optimizing-runtime evidence against the commit produced by this
corrective phase is still required before PERF003-A may close.

## PERF003-A second external diagnostic and cardinality refinement

The exact external diagnostic against Protos
`eb8b9c588bb363309da5193bf8d98d64d5456cee` with companion harness/evidence
`guillermomolina/protos-benchmarks@4bff9f7f6c5e0e006530f166c188e0e988acf565`,
GraalVM Community JDK 22, external Truffle `24.0.0`, and `-Xss128m` established:

- `collections/array-reduce` interpreter correctness: PASS, exact result `528`;
- optimizing-runtime correctness: PASS, exact result `528`;
- optimizing compilations completed: 25;
- optimization failures: 2, both `GraphTooBig`;
- failing graph node count: 51446; graph size: 150069; configured limit: 150000;
- graph-size reduction relative to the first PERF003-A diagnostic: 431;
- remaining margin to the limit: 69.

The first correction therefore moved the same semantic workload materially closer
to compilability without introducing a new failure mode, but PERF003-A still does
not satisfy its zero-bailout closure condition. The diagnostic again stopped at
the failing reduce gate, so no new optimized `sort` conclusion is recorded from
that run.

The 0.2.178-SNAPSHOT second corrective phase remains ordinary Protos source only.
`initial` is the invocation-local rest Array and `snapshot` is the fresh internal
pre-callback shallow snapshot; neither cardinality can change during the reduce
invocation. Their `size()` results are therefore computed once as `initialSize`
and `snapshotSize` and reused by the existing validation/selection/fold logic.
The recursive helper also drops its final explicit `null`: its result is wholly
internal and ignored, and both existing branches already complete with `null` on
the paths where that result exists. Reducer order, invocation count, exact
accumulator flow, callback effects/failures, initial-value behavior and snapshot
semantics are unchanged.

No `sort`, Java/runtime, native protocol, Truffle boundary, benchmark identity
check, or normative rule changes. Existing 32-element repeated-reduction Protos
conformance remains the focal semantic guard. Exact external optimizing-runtime
evidence against the commit produced by this second corrective phase is still
required before PERF003-A may close.

## PERF003-A third external diagnostic and initialization-state refinement

The exact external diagnostic against Protos
`5404667964dec84b8b8de2ff8dbe7923a5d1dd2e` with companion harness/evidence
`guillermomolina/protos-benchmarks@4bff9f7f6c5e0e006530f166c188e0e988acf565`,
GraalVM Community JDK 22, external Truffle `24.0.0`, and `-Xss128m` established:

- `collections/array-reduce` interpreter correctness: PASS, exact result `528`;
- optimizing-runtime correctness: PASS, exact result `528`;
- optimizing compilations completed: 25;
- optimization failures: 2, both `GraphTooBig`;
- failing graph node count: 50471; graph size: 150036; configured limit: 150000;
- graph-size reduction relative to the preceding diagnostic: 33;
- cumulative graph-size reduction from the first PERF003-A diagnostic: 464;
- remaining margin to the limit: 36.

The second correction therefore preserves the same semantic workload and again
reduces the failing graph, but PERF003-A still does not satisfy its zero-bailout
closure condition. The diagnostic stopped at the failing reduce gate, so no new
optimized `sort` conclusion is recorded from this run.

The `0.2.180-SNAPSHOT` third corrective phase remains ordinary Protos
source only. After the existing `initialSize > 1` failure guard completes
normally, `initialSize` can only be zero or one. The implementation therefore no
longer stores that fact again in `hasInitial` or tracks a mutable `startIndex`.
It executes the same seeded/unseeded initialization branches directly and derives
the fold start as `1 - initialSize` (zero for a seeded fold, one for an unseeded
fold). The balanced helper remains outside those branch callbacks and still
visits its left half completely before its right half. Snapshot timing, exact
accumulator flow, reducer invocation count/order, callback effects/failures,
empty/singleton behavior, and initial-value semantics remain unchanged.

No `sort`, Java/runtime, native protocol, Truffle boundary, benchmark identity
check, or normative rule changes. Existing 32-element repeated-reduction Protos
conformance remains the focal semantic guard. Exact external optimizing-runtime
evidence against the commit produced by this third corrective phase is still
required before PERF003-A may close.


## Final A4 structural evidence and closure decision

The retained A4 evidence pins Protos `d66841adb0b820047ed079f0bc7d643873f23194`. The stable control reproduced `40` deterministic `GraphTooBig` failures at `50681:150026:150000` while preserving exact result `528`.

- A4a (`invokePrepared` boundary only): `40`, `48153:150001:150000`.
- A4g (preparation plus `invokePrepared` boundaries): `0` failures; sufficient causal isolation, but too broad to justify as a production optimization by itself.
- A4h (production-shaped sync/task split): `40`, `48823:150002:150000`; not a sufficient production rewrite.
- A4i (preparation boundary only): `40`, `51502:150053:150000`; not a sufficient compilability frontier.

A4i also retained separate no-trace timing: control median `9950715696 ns`, preparation-boundary median `5752923868 ns`, ratio `0.578142x`. This workload-specific result is not a general speed claim, but it demonstrates that the residual bailout count is not a reliable performance proxy.

The earlier zero-bailout closure gate is therefore retired for PERF003. Continuing to shave the `150000` threshold or publishing a broad opaque `TruffleBoundary` would optimize the gate rather than a justified general architecture. PERF003 closes with no additional canonical runtime change. The residual bailout remains a known optimizing-runtime limitation, not a correctness defect. Future work should reopen this area only with materially new runtime/compiler machinery or broader evidence.

Closure evidence: A4g `bc7e649458e0c66f6a9de6367610af3020839aa6`; A4h `9737e21a0f040bf81e82f06e8a43a6808f50f80c`; A4i `433ebb8075148493d4eae8da701803b21ab50c09`.

No language specification, implementation source, implementation version, native boundary, or license term changes as part of this reconciliation.
