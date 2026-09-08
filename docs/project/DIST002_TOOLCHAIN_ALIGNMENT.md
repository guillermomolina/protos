# DIST002 — Development/release toolchain alignment

Status: IN_PROGRESS
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

## DIST002-A selected canonical toolchain contract

On 2026-09-08 the project owner explicitly selected the repository's primary
runtime/toolchain direction for DIST002: use the newest currently selected
GraalVM Community/JDK line rather than retaining the historical JDK22 runtime
merely because it already has DIST001/PERF evidence. The selected exact
repository-owned coordinates are persisted in root `toolchain.json`:

```text
Java source/bytecode target: 21
primary GraalVM release:     25.3.4.1
primary JDK feature/version: 25 / 25.0.4.1
GraalVM container channel:   25i3
Graal/Truffle components:    25.3.4.1
Maven:                       3.9.9
```

The Java 21 bytecode target remains an independent compatibility choice; it does
not require routine development or the supported runtime to remain on JDK21 or
JDK22. The primary runtime is exact and non-floating. A later GraalVM/JDK,
Truffle or Maven migration is an explicit validated project change rather than
a side effect of updating one environment.

Historical DIST001/PERF evidence for GraalVM Community JDK22 + Truffle 24.0.0
remains valid evidence for those historical revisions and is not rewritten by
DIST002. Likewise, the companion benchmark repository's isolated JDK17 IGV
analyzer remains a diagnostic-tool compatibility boundary: its older NetBeans /
Java-7-target build requirements do not define the measured Protos runtime or
this repository's primary JDK.

`tools/verify_toolchain.py` owns the machine-checkable v1 contract validation and
static binding audit. During DIST002-A it can report the intentional migration
drift that still exists in Maven dependencies, normal CI and DIST001-derived
distribution metadata. DIST002-B and DIST002-C must remove those drifts; once
alignment is complete, `--mode check` becomes a zero-drift gate. This separation
allows A to establish one authority before later slices change consumers.

## DIST002-B development and ordinary-CI alignment

DIST002-B closes the primary development/ordinary-CI runtime split without yet
changing the live portable-distribution contract. The devcontainer already
matched the selected DIST002-A coordinates at the start of this slice, so B
retains its exact GraalVM Community `25i3` / JDK `25.0.4.1` image and Maven
`3.9.9` binding rather than introducing a redundant container change.

The ordinary `Tests` workflow no longer installs Temurin 21 with
`actions/setup-java`. Its job executes inside the exact primary GraalVM image
recorded by `toolchain.json`, so the JDK/runtime exists before repository
validation begins. A bootstrap step installs only ordinary OS prerequisites and
exact Maven `3.9.9`; Maven's archive checksum is verified before extraction.
Before the suite, CI verifies the actual Java feature/version and GraalVM
identity, verifies Maven's exact version, and runs the repository toolchain
auditor over the development scope.

`tools/verify_toolchain.py --scope development --mode check` is therefore a
zero-drift gate for Java bytecode compatibility, devcontainer image/Maven and
ordinary-CI runtime/Maven bindings. The all-surface audit intentionally remains
non-zero after B because `pom.xml`'s Graal/Truffle dependency and the
DIST001-derived distribution workflow/runtime metadata are assigned to
DIST002-C. B does not rewrite historical DIST001/PERF evidence and does not
change the existing public `v0.2.236` support claim.

## DIST002-C live distribution/runtime migration

DIST002-C migrates the live implementation and portable-distribution runtime
from the historical DIST001 GraalVM JDK22 / Truffle 24.0.0 contract to the
canonical DIST002-A toolchain. The root Maven implementation dependency and the
portable runtime dependency closure now use Graal/Truffle `25.3.4.1` while Java
source/bytecode compatibility remains deliberately at release 21. This is an
implementation/runtime migration, so the Maven implementation version advances
exactly once from `0.2.256-SNAPSHOT` to `0.2.257-SNAPSHOT`.

The distribution snapshot workflow now executes in the same exact
`ghcr.io/graalvm/graalvm-community:25i3-25.0.4.1-ol8-20260825` image used by the
primary development/ordinary-CI contract. It no longer invokes
`graalvm/setup-graalvm` to fetch JDK22 during the repository gate. Exact Maven
3.9.9 is bootstrapped separately with checksum verification before repository
validation, then CI verifies the actual GraalVM/JDK/Maven identity and requires
a zero-drift all-surface toolchain audit before building/testing.

New development distributions record exact `java_feature=25`,
`java_version=25.0.4.1`, `graalvm_release=25.3.4.1` and
`truffle_runtime_version=25.3.4.1` metadata. The distributed launcher now
requires the exact recorded Java version in addition to the existing feature and
GraalVM-vendor gate. B5/B4B validation uses the already-provisioned primary
`JAVA_HOME` by default and still requires the intact optimizer closure to resolve
exact `com.oracle.truffle.runtime.hotspot.HotSpotTruffleRuntime`; no unsupported
runtime override is accepted for that gate.

Historical `v0.2.236`, DIST001 and PERF evidence remains immutable evidence for
its JDK22/Truffle24 revision. DIST002-C changes only live post-DIST001
development/distribution bindings; it does not rewrite the released artifact,
tag, release notes or historical benchmark conclusions. After C the static
repository toolchain audit is zero-drift across development, ordinary CI and
distribution bindings. DIST002-D remains responsible for final cross-environment
closure/reconciliation.

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
| DIST002-A | CLOSED | Selected exact canonical coordinates are persisted in root `toolchain.json`; tested contract/static-binding drift audit is published. |
| DIST002-B | CLOSED | Existing devcontainer binding is verified against A; ordinary Tests CI runs in the exact primary GraalVM image with exact Maven and a development-scope drift/runtime gate. |
| DIST002-C | CLOSED | Live Maven/Truffle dependencies, distribution metadata, launcher/runtime gates and distribution CI now use/check the canonical JDK25.0.4.1/Graal-Truffle25.3.4.1 contract; historical DIST001 evidence is retained. |
| DIST002-D | READY | Cross-environment conformance, documentation/status reconciliation, and DIST002 closure after the zero-drift C migration. |

Opening DIST002 changes no Protos semantics, implementation version, current
DIST001 candidate, runtime support promise, tag, GitHub Release, or release
publication authorization.
