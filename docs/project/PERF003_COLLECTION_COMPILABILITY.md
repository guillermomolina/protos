# PERF003 — Collection algorithm Truffle compilability

Status: IN_PROGRESS

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
| PERF003-A | IN_PROGRESS | Implementation phase `0.2.170-SNAPSHOT` rewrites only the ordinary Protos-source traversal shape of Array `reduce` and the `sort` merge pass: balanced recursive range traversal replaces the internal `Array.each` callback loop while preserving exact left-to-right reducer/comparator effects, stable merge order, snapshot and failure laws. Repository focal/full/package/license validation is required here; exact GraalVM/Truffle diagnostics against the published commit remain the closure gate before PERF003-A can become CLOSED. |
| PERF003-B | BLOCKED_BY_DEPENDENCIES | After A, publish companion correctness-first external validation against the exact optimized Protos revision, retain pre/post evidence and diagnostics, and do not rewrite PERF001-E baseline evidence. |

PERF003 closes only after PERF003-B evidence is published and reconciled into the
canonical Protos ledger.

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
