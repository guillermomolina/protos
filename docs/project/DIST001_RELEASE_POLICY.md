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

DIST001-C can close before the executable distribution slices because it
constrains how those slices may publish artifacts. DIST001 as a whole remains
open until the required distribution and first-release work is complete.

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
