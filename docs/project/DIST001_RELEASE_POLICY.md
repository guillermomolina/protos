# DIST001 — End-user distribution and release policy

Status: selected project policy; non-normative

This document defines how Protos development revisions become user-facing
distributions and GitHub Releases. It is project/release-engineering policy, not
Protos language semantics and not a compatibility guarantee.

## Goal

Make Protos easy to try without turning every internal implementation revision
into a public release.

The project deliberately separates:

```text
implementation revision
    exact development state tracked by the Maven implementation version

CI snapshot artifact
    transient build output for testing a development state

public release
    selected, immutable, user-facing milestone with explicit release metadata
```

Those concepts must not be collapsed merely because they can contain the same
compiler/runtime code.

## Implementation snapshots are not releases

During active `0.x` development, implementation work may advance through Maven
versions such as:

```text
0.2.217-SNAPSHOT
0.2.218-SNAPSHOT
0.2.219-SNAPSHOT
...
```

These versions provide exact implementation traceability. They do **not** create
an obligation to publish a corresponding GitHub Release, tag, downloadable
distribution, compatibility promise, or support window.

There is no requirement for every implementation version to have a public
release. Public releases may therefore have gaps between their numeric
implementation revisions.

For example, a selected public milestone may derive from the implementation
state around `0.2.231-SNAPSHOT`, while the next selected release may derive from
a substantially later implementation revision. Intermediate snapshots remain
valid historical implementation states without becoming releases.

The exact release-version transition used by the first public bundle must ensure
that the version reported by the distributed toolchain, the release metadata,
and the Git tag are coherent. A non-`SNAPSHOT` public tag must not silently ship
a toolchain that identifies itself as an unrelated or ambiguous development
version.

## Release selection is milestone-based

A public release is selected because the toolchain represents a coherent
user-facing milestone, not because a counter changed.

Useful release triggers include:

- a substantial Core implementation/conformance milestone;
- a bundled Package Tool or Test Tool capability becoming practically usable;
- a meaningful Standard Library milestone;
- an adoption-oriented distribution or installation improvement;
- a material diagnostics, correctness, or performance improvement worth testing
  outside `main`; or
- enough accumulated validated improvement that a new external reference point
  is useful.

Calendar cadence is advisory only. While Protos is changing quickly, maintainers
should periodically review release readiness roughly every three to six weeks,
but there is no requirement to release on a timer and a coherent milestone may
justify an earlier release.

Likewise, the absence of a major named feature does not require delaying a
release indefinitely when the accumulated toolchain is useful to external
testers.

## Pre-release status during the current phase

While Core v0.1 remains a draft and the reference implementation is under active
development, user-facing GitHub Releases should normally be marked as
**pre-releases**.

A later project decision may introduce stable release channels, compatibility
windows, release candidates, or long-term support expectations. DIST001 does not
pre-commit the project to those policies.

Calling an artifact a pre-release does not weaken its reproducibility
requirements: the published artifact must still correspond to one exact
validated source revision.

## Release readiness gate

Before a Protos public release is created, all of the following must be true for
the exact selected revision:

1. The selected source revision is immutable and identifiable.
2. Repository-required validation for that revision is green.
3. The distributable bundle has been built from that exact revision.
4. The bundle passes its distribution smoke/conformance checks after extraction
   outside the repository checkout.
5. The supported host runtime/JDK requirement is explicit and validated.
6. The artifact contains the license/notices and source-reference information
   required by project policy and the applicable license.
7. Release notes identify important capabilities, important limitations, the
   exact source revision, implementation version, specification revision when
   relevant, and supported runtime.
8. Checksums are published for downloadable artifacts.
9. No known blocker makes an advertised release capability materially false.

A release need not claim that every work item in Protos is complete. It must be
truthful about the surface it advertises.

## Runtime support must be explicit

Protos uses Truffle/Graal infrastructure and the current Maven build targets
Java 21 bytecode. A shaded JAR carrying Java dependencies is not itself a JDK or
a complete GraalVM runtime.

Therefore a distribution must not be advertised merely as requiring
"Java 21+" unless that claim has been deliberately tested and selected as the
support policy.

Each release must declare the exact supported runtime family/range needed for
the distributed Protos build. The first portable distribution may require an
externally installed supported GraalVM/JDK. Future platform-specific bundles may
include a runtime so that users do not need to install Java/GraalVM separately.

Runtime support and optimization support are also distinct. If a runtime can
execute Protos only through a non-optimizing/fallback Truffle path, the release
documentation must not present it as equivalent to the optimizing runtime
without evidence.

## Initial DIST001-A runtime contract

DIST001-A selects the first distribution runtime from retained optimizing
evidence rather than from whichever JDK happens to build the repository.

The initial portable POSIX/JVM development distribution uses:

```text
host JDK:             GraalVM Community Edition for JDK 22
Truffle runtime:      org.graalvm.truffle:truffle-runtime:24.0.0
expected runtime:     HotSpotTruffleRuntime
project bytecode:     Java 21 target
```

`docs/project/PERF002_TRUFFLE_COMPILABILITY.md` records successful optimizing
validation for that GraalVM/JDK + external Truffle-runtime combination.

The external optimizing runtime is a **distribution dependency**, not a new
normal Maven runtime dependency of the Protos repository. The ordinary project
build/test classpath therefore remains unchanged by DIST001-A.

The distribution launcher defaults to requiring Java feature 22 and GraalVM
vendor metadata. `PROTOS_ALLOW_UNSUPPORTED_RUNTIME=1` exists only as an explicit
developer experiment escape hatch; using it does not extend the supported
runtime contract or provide optimization evidence.

A future move to a newer GraalVM/JDK or Truffle runtime is expected and should
be treated as a separately validated runtime-contract update. The repository
development-container JDK does not silently redefine end-user runtime support.

## CI snapshot artifacts are not GitHub Releases

Once DIST001-D exists, CI may build transient downloadable artifacts from green
development revisions.

Those artifacts are useful for:

- external testing before a release;
- reproducing a reported issue against an exact development revision;
- validating bundle construction continuously; and
- giving contributors a no-Maven test path without creating release ceremony.

A CI snapshot artifact:

- is not a Git tag;
- is not a GitHub Release;
- need not be retained indefinitely;
- does not imply compatibility or support commitments; and
- must retain enough revision/version metadata to identify exactly what it
  contains.

Agents and documentation must not call ordinary CI artifacts "releases".

### DIST001-D execution slices

DIST001-D is deliberately split so repository configuration is not mistaken for
observed artifact publication:

- `DIST001-D1` — publish the CI snapshot workflow definition. The workflow runs
  on `main` pushes and explicit manual dispatch, pins GraalVM Community JDK
  22.0.0, runs the full suite and the complete B5 gate, writes an outer ZIP
  checksum, and uploads a 14-day `protos-snapshot-<source-sha>` Actions artifact.
- `DIST001-D2` — observed CI snapshot artifact closure — CLOSED:
  - `DIST001-D2A` — portable external checksum repair — CLOSED; the workflow
    writes only the ZIP basename into `.sha256` and verifies it with
    `sha256sum -c` before upload;
  - `DIST001-D2B` — observed repaired artifact closure — CLOSED from real run
    `34101588533` for exact source `994429173b6ec0fc086f307f4a49815f219c6523`. The run completed green and
    uploaded `protos-snapshot-994429173b6ec0fc086f307f4a49815f219c6523` (artifact id `10010752033`). Downloaded-artifact
    inspection found `protos-0.2.230-SNAPSHOT-posix-jvm.zip` plus its `.sha256`; the checksum contained
    only the ZIP basename, matched SHA-256 `f66f011ba9a7c579f81b5aad7098bd4ec421ebc8117b3c4c8954374441e94337`, and passed
    `sha256sum -c` outside the runner workspace.

DIST001-D is CLOSED. CI now continuously produces validated transient snapshot
artifacts without creating tags or GitHub Releases. DIST001-E may be audited as
READY work, but public prerelease publication still requires an explicit release
decision for an exact candidate revision.

Neither slice creates or authorizes a Git tag or GitHub Release.

## Release publication is an explicit action

Implementation publication to `main` and public release publication are separate
actions.

A normal implementation patch may:

```text
validate
commit
push to main
```

without:

```text
create tag
create GitHub Release
publish release assets
```

Creating a public release requires an explicit release decision for a specific
revision. An implementation-version bump, a green build, closure of an
implementation slice, or passage of time does not provide that authorization.

Release automation may eventually perform the mechanical steps, but it must
still require an explicitly selected release candidate and must never
automatically release every successful `main` revision.

### DIST001-E execution slices

DIST001-E is deliberately decomposed so preparation cannot silently become
publication:

- `DIST001-E1` — readiness/candidate-envelope audit — CLOSED. A-D are sufficient
  to begin pre-release preparation, but no exact source revision or public
  version is selected. Current open work such as I023/B008 is a candidate-time
  release-claim constraint rather than an automatic blanket ban: any limitation
  that remains must be disclosed, and any blocker that makes an advertised
  capability materially false blocks publication.
- `DIST001-E2` — coherent public pre-release version contract — READY. Define how
  the internal Maven `0.2.N-SNAPSHOT` development identity transitions to one
  coherent public pre-release identity across tool output, tag, release metadata,
  and asset names. This slice does not select a candidate.
- `DIST001-E3` — release metadata/assets/validation preparation —
  `BLOCKED_BY_DEPENDENCIES` on E2. Prepare release-note metadata, checksums,
  asset manifest, and a candidate validation entry point without creating a tag
  or GitHub Release.
- `DIST001-E4` — exact candidate selection and validation —
  `BLOCKED_BY_DEPENDENCIES` on E2/E3 and additionally requires an explicit user
  decision selecting the exact source revision/public version. E4 validates and
  freezes that candidate but does not publish the GitHub Release.
- `DIST001-E5` — first GitHub pre-release publication —
  `BLOCKED_BY_DEPENDENCIES` on the explicitly selected, fully validated E4
  candidate. This is the first slice allowed to create the public tag, GitHub
  pre-release, and release assets.
- `DIST001-E6` — post-publication verification and DIST001 closure —
  `BLOCKED_BY_DEPENDENCIES` on E5. Verify tag/source identity, release metadata,
  assets/checksums and downloadability, then close DIST001.

The detailed E1 readiness envelope is recorded in
`docs/project/DIST001_FIRST_PRERELEASE_READINESS.md`.

No E1-E3 result authorizes release publication. A candidate must remain
explicitly unselected until the user makes the exact-candidate decision required
by policy.

## Expected release metadata

A release should make it possible for an external tester to answer:

```text
What exact Protos source revision is this?
What implementation/release version does the tool report?
What Core specification state is relevant?
What runtime/JDK is supported?
What archive did I download?
How can I verify its checksum?
What important limitations are known?
Where is the corresponding source?
```

The exact manifest/file format remains implementation work for DIST001-A/D/E.

## DIST001 slices

The selected work decomposition is:

| Slice | Purpose |
|---|---|
| DIST001-A | Define and build the relocatable portable distribution layout, launcher, runtime contract, and included toolchain/source assets. |
| DIST001-B | Prove the extracted distribution works outside the repository checkout, including CWD-sensitive execution and required bundled facilities. |
| DIST001-C | Define release selection, snapshot-vs-release, runtime-disclosure, readiness, and publication policy. |
| DIST001-D | Produce CI snapshot artifacts without treating every green implementation revision as a public release. |
| DIST001-E | Cut the first selected GitHub pre-release from an explicitly approved, fully validated release candidate. |

DIST001-C closed before the executable distribution slices so release automation
cannot accidentally interpret every implementation revision as a publication
event.

DIST001-A now owns the constructible relocatable POSIX/JVM development archive,
initial optimizing-runtime contract, exact source/runtime metadata, checksums,
and launcher layout.

DIST001-B remains the independent extracted-execution gate, but is intentionally
split into bounded validation slices so failures are isolated and publication
does not depend on one large all-or-nothing launcher:

- `DIST001-B1` — validation hygiene and B1..B5 decomposition — CLOSED;
- `DIST001-B2` — clean-source archive identity/checksums — CLOSED after direct
  ZIP verification of exact clean source revision and complete internal checksum
  coverage;
- `DIST001-B3` — outside-checkout caller-CWD and Package Tool execution — CLOSED
  after extracting the bundle outside the checkout and proving a relative Protos
  source plus `package manifest` resolve from a distinct caller project CWD; on
  a validation JDK outside the selected JDK22 contract, B3 disables optimizer
  JARs only in the disposable extracted copy and exercises fallback Truffle;
- `DIST001-B4` — bundled Test Tool and exact optimizing-runtime probe — CLOSED:
  - `DIST001-B4A` — extracted bundled Test Tool smoke — CLOSED;
  - `DIST001-B4B` — intact optimizer classpath + exact runtime probe — CLOSED
    using exact GraalVM Community JDK 22.0.0 with the bundle's Truffle 24.0.0
    runtime and exact `HotSpotTruffleRuntime` class evidence;
- `DIST001-B5` — cross-slice closure and DIST001-D readiness — CLOSED after
  the exact same clean-source ZIP passes B2 identity/checksums, B3 CWD/Package
  Tool, B4A bundled Test Tool, and B4B exact optimizing-runtime evidence in one
  composed validation run; parent DIST001-B is CLOSED and DIST001-D is READY.

A does not claim that the archive has passed outside-checkout execution merely
because construction and structural validation pass. Parent B closes only after
B5.

DIST001 as a whole remains open until the required distribution and first-release
work is complete.

## Non-goals

This policy does not:

- define Protos language semantics;
- define package-registry release/version semantics;
- promise semantic-version compatibility for the current draft language;
- require a public release for every implementation version;
- select every future OS/architecture bundle;
- require a bundled JDK/GraalVM in the first portable archive;
- create a Git tag or GitHub Release; or
- authorize an agent to publish a release without an explicit release decision.
