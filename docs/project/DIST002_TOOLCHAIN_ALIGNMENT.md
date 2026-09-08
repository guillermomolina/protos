# DIST002 — Development/release toolchain alignment

Status: OPEN
Nature: non-normative build, development-environment and release-engineering project work

## Problem

The repository currently has multiple independently selected Java/Graal runtime
coordinates:

- the development container runs a newer GraalVM/JDK than the first DIST001
  portable-release runtime;
- Maven deliberately emits Java 21 bytecode;
- DIST001 independently freezes the supported GraalVM/JDK and Truffle runtime
  for a public distribution.

That separation is technically valid, but when it is accidental it increases
the chance that ordinary development and test runs exercise a different VM,
JVMCI/Truffle integration, reflection/runtime behavior, or host boundary than
the supported release. It also caused release validation to need a late,
on-demand download of the frozen JDK.

DIST002 exists to make those relationships explicit and reproducible.

## Goal

Provide one authoritative toolchain/runtime coordinate set for ordinary Protos
development, CI and distribution validation, while keeping Java bytecode
compatibility as an independently intentional target.

The default policy after DIST002 should be:

```text
Java source/bytecode target
    independently selected compatibility level

primary development runtime
primary CI runtime
supported portable-distribution runtime
    one explicitly selected GraalVM/JDK family/version unless a documented
    matrix deliberately tests additional runtimes

Truffle/runtime dependency version
    one explicit coordinate consumed by the same toolchain policy
```

## Required outcome

DIST002 is not closed until all of the following are true:

1. One repository-owned source of truth records at least:
   - Java bytecode target;
   - primary GraalVM/JDK identity/version;
   - Truffle runtime version;
   - Maven version where the repository requires an exact Maven toolchain.

2. The devcontainer derives its primary Java/Graal runtime from that selected
   toolchain contract rather than floating ahead independently.

3. Normal CI build/test and distribution validation use the same primary
   runtime. Additional JDK/runtime matrix entries are allowed only when they are
   explicitly secondary compatibility experiments or supported-runtime checks.

4. Normal local/CI/release gates do **not** download a JDK/runtime on demand.
   Required primary runtimes are provisioned by the development image, CI image
   or another explicit environment-bootstrap step before repository validation
   begins. A one-off recovery/bootstrap path must not become the ordinary gate.

5. `pom.xml`, devcontainer configuration, CI workflows, portable builder/runtime
   metadata and B4B/B5-style validation consume or are checked against the same
   selected coordinates so drift fails early.

6. A future runtime migration (for example JDK22 -> JDK25) is an explicit
   project change with retained validation evidence; updating the devcontainer
   alone must never silently redefine the supported release runtime.

## Current observed mismatch

At the time this item was opened:

```text
devcontainer primary runtime:
    GraalVM Community JDK 25 image

Maven bytecode target:
    Java 21

first DIST001 portable runtime contract:
    GraalVM Community JDK 22
    Truffle runtime 24.0.0
```

The Java 21 bytecode target is not itself considered a defect. The problem is
the accidental primary-runtime split between routine development/testing and
release validation.

## Relationship to DIST001

DIST002 is deliberately **not a dependency of the frozen DIST001 0.2.236
candidate**. DIST001 must finish against its already selected and validated
GraalVM JDK22 / Truffle24 contract.

The JDK22 cache/download introduced while validating that frozen candidate is a
bounded compatibility workaround for the already-selected release. It must not
be treated as the desired steady-state development or release architecture.

After DIST001 closes, DIST002 should be evaluated before the next public
candidate so subsequent release gates do not repeat this split-runtime/bootstrap
pattern.

## Proposed slices

| Slice | Status | Scope / closure condition |
|---|---|---|
| DIST002-A | OPEN | Select and persist the canonical repository toolchain coordinate source and drift checks. |
| DIST002-B | BLOCKED_BY_DEPENDENCIES | Align devcontainer and ordinary CI primary runtime with A; required runtime is pre-provisioned, not downloaded by normal gates. |
| DIST002-C | BLOCKED_BY_DEPENDENCIES | Make distribution/runtime metadata and validation consume/check the same coordinates; remove ordinary on-demand-JDK gate dependence. |
| DIST002-D | BLOCKED_BY_DEPENDENCIES | Cross-environment conformance, documentation/status reconciliation, and DIST002 closure. |

Opening DIST002 changes no Protos semantics, implementation version, current
DIST001 candidate, runtime support promise, tag, GitHub Release, or release
publication authorization.
