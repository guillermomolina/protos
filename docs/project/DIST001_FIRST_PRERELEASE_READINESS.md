# DIST001-E1 — First pre-release readiness envelope

Status: CLOSED when this document and its ledger transitions are published.

This document is project/release-engineering state. It is not normative Protos
language specification and it does not select, tag, or publish a release
candidate.

## E1 conclusion

DIST001-A, DIST001-B, DIST001-C, and DIST001-D are closed. The repository now
has:

- a relocatable POSIX/JVM distribution;
- independent extracted-distribution conformance;
- an explicit release-selection policy;
- continuous CI snapshot construction;
- exact GraalVM Community JDK 22 + Truffle 24.0.0 optimizing-runtime evidence;
- an observed downloadable CI artifact whose external checksum remains portable
  after download.

This is sufficient to begin bounded first-pre-release preparation.

It is **not** sufficient to select or publish a release automatically.

At E1 closure:

```text
release candidate source revision: UNSELECTED
public pre-release version:         UNSELECTED
Git tag:                            NOT CREATED
GitHub Release:                     NOT CREATED
release assets:                     NOT PUBLISHED
```

No revision becomes a candidate merely because it is `main`, has a green CI
snapshot, advances the Maven SNAPSHOT version, or closes another project item.

## Candidate eligibility gate

A future exact source revision may be proposed as the first public pre-release
candidate only when all of the following are true for that same revision:

1. the source SHA is explicit and immutable;
2. repository-required validation for that SHA is green;
3. the portable distribution is built from that exact clean revision;
4. the complete extracted-distribution gate passes;
5. the selected runtime remains explicit and validated;
6. license, source-reference, dependency, runtime, and checksum metadata are
   present and internally consistent;
7. the public version reported by the distributed toolchain, the Git tag, release
   title/metadata, and asset names follow one coherent version decision;
8. release notes accurately identify capabilities and limitations;
9. no known blocker makes an advertised capability materially false; and
10. the user explicitly selects the exact candidate revision/version before any
    tag, GitHub Release, or release-asset publication occurs.

The current CI snapshot mechanism is evidence that a revision can satisfy much
of this gate. A CI artifact remains a transient development artifact and is not
itself a release candidate selection.

## Current limitation audit

The first pre-release does not require every tracked project item to be closed.
It does require truthful scope.

At E1, known open work includes `I023 — Standard while protocol`, whose
structured-ownership closure is blocked by `B008 — Structured ownership when a
task-backed Future escapes an activation`.

If those items remain open at candidate selection time, release notes must not
claim that this area is complete. In particular they must not imply that the
unresolved returned-Future structured-ownership boundary is fully specified and
implemented merely because other `while` behavior is already present.

This condition is not, by itself, a blanket prohibition on a pre-release. It is
a release-claim boundary. Candidate-specific E4 validation must re-audit all
then-current blockers and decide whether each one is:

- outside the advertised release scope and accurately disclosed; or
- material to an advertised capability, in which case publication is blocked.

Package Tool, Test Tool, Core, libraries, examples, and documentation are also
evaluated from the exact candidate revision. E1 does not freeze their present
development state as release claims.

## Runtime and platform scope

Until a later validated runtime-contract change says otherwise, the first
portable pre-release preparation inherits the DIST001 runtime boundary:

```text
distribution form:      portable POSIX/JVM ZIP
host runtime:           GraalVM Community Edition for JDK 22
Truffle runtime:        org.graalvm.truffle:truffle-runtime:24.0.0
expected runtime class: com.oracle.truffle.runtime.hotspot.HotSpotTruffleRuntime
project bytecode:       Java 21 target
```

The external JDK is not bundled in the current portable ZIP.

A release must not broaden this into a generic "Java 21+" support claim without
separate evidence.

## DIST001-E bounded execution plan

The first public pre-release is split into these publishable slices:

| Slice | Purpose | Publication boundary |
|---|---|---|
| DIST001-E1 | Readiness audit, candidate eligibility envelope, limitation audit, and bounded decomposition. | No candidate selection, tag, release, or asset publication. |
| DIST001-E2 | Define the coherent public pre-release version transition and mechanical version contract. | No candidate selection or release publication. |
| DIST001-E3 | Define/build release metadata, notes, checksums, asset manifest, and release-candidate validation entry point. | No tag or GitHub Release. |
| DIST001-E4 | After an explicit exact-candidate user selection, materialize and validate that immutable candidate revision against the complete release gate. | Validation only; no GitHub Release publication. |
| DIST001-E5 | Publish the explicitly approved, fully validated candidate as the first GitHub pre-release with the exact tag/assets/metadata prepared by E2-E4. | This is the first slice allowed to create the tag and GitHub Release. |
| DIST001-E6 | Independently verify the published tag, GitHub pre-release metadata, downloadable assets/checksums/source identity, then close DIST001. | Verification/closure only. |

Each slice must re-fetch current `origin/main` and re-audit the assumptions it
owns before implementation. Concurrent development may continue; earlier E
slices do not freeze `main`.

## Explicit approval boundary

E1-E3 may prepare release machinery without selecting a release candidate.

E4 must not begin candidate-specific mutation or freeze work until the user has
explicitly selected an exact source revision and public pre-release version (or
has explicitly authorized a procedure that resolves both unambiguously).

E5 must never reinterpret a green build, a passing E4 gate, an implementation
version bump, or passage of time as release authorization. Public release
publication is a separate action tied to the explicitly selected candidate.
