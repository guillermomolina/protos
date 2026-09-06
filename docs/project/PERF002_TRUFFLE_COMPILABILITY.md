# PERF002 — Truffle compilability and dispatch optimization

Status: IN_PROGRESS
Implementation version: `0.2.162-SNAPSHOT`
PERF002-A publication baseline: `8f363d0146164f99e72210eb44667f4efb7b88e7`

This is a non-normative project record. It documents implementation optimization
work discovered while preparing `PERF001-D`; it does not define Protos language
semantics or performance guarantees.

## Repository split

PERF002 deliberately follows the existing PERF repository boundary:

- `guillermomolina/protos` owns the optimization implementation, Protos-source
  semantic conformance, the canonical PERF002 lifecycle and final status.
- `guillermomolina/protos-benchmarks` owns GraalVM/Truffle/container execution,
  compiler diagnostics and retained external evidence.

The Protos repository does not require Docker, GraalVM, the companion repository,
or external PERF evidence to build, test, package or publish `PERF002-A`.

## Motivation

PERF001 diagnostics exposed independent partial-evaluation problems in ordinary
Core execution:

- evaluator-bridge machinery was paid on ordinary non-task expression execution;
- Sequence and argument-vector fixed child arrays were not exposed to Truffle as
  compilation-constant exploded loops;
- immediate method invocation materialized a fresh bound Closure even though no
  extraction value was observable;
- ordinary member reads retained expensive extracted-Closure materialization
  inside partial evaluation.

Material optimization work discovered by PERF001 belongs to a separate PERF item
rather than being folded into PERF001 baseline evidence.

## Implemented boundary — PERF002-A

`PERF002-A` changes execution machinery while preserving observable Protos
semantics:

- fixed Sequence and argument-vector child arrays use Truffle loop explosion
  with compilation-constant lengths;
- ordinary expression execution bypasses the evaluator continuation bridge, while
  an active Actor-task evaluator segment retains the exact bridge path and
  transfers to the interpreter;
- immediate selected-method invocation carries the original dynamic receiver and
  selected physical `methodHome` in activation metadata instead of manufacturing
  an unobservable extracted Closure;
- a real member read selecting a Closure still creates the required fresh
  receiver-bound Closure; only host-side extraction materialization is kept
  outside partial evaluation;
- there is intentionally no direct `ProtosClosureValue` call bypass, so ordinary
  `call` lookup and local shadowing remain observable.

## Slices

| Slice | Status | Owner | Closure condition |
|---|---|---|---|
| PERF002-A | CLOSED | `guillermomolina/protos` | Implementation, Protos-source semantic conformance, focused validation, full Maven suite, package and publication to `main` complete. |
| PERF002-B | READY | `guillermomolina/protos-benchmarks` | Consume the exact published PERF002-A Protos commit and publish optimizing-Truffle correctness/compilability evidence for the canonical PERF001 workload surface. |

PERF002 remains `IN_PROGRESS` until PERF002-B is published in the companion
repository and its exact external commit is recorded back in the canonical Protos
ledger.

## PERF002-A validation

The Protos publication launcher performs only repository-native validation:

1. static compilation;
2. focused language/execution tests;
3. full `mvn test`;
4. package construction from the exact working tree;
5. APL-1.0/license-header checks;
6. safe concurrent-`origin/main` reconciliation before commit/push.

The Protos-source conformance additions specifically protect:

- ordinary local `call` shadowing on Closure objects;
- fresh identity for repeated method extraction;
- preservation of receiver binding and copied local Closure state.

Optimizing-Truffle/container validation is intentionally deferred to PERF002-B
and is not an execution dependency of this repository.
