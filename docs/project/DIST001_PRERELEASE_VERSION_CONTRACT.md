# DIST001-E2 — Public pre-release version contract

Status: CLOSED when this document and its ledger transitions are published.

This document is project/release-engineering state. It does not select a release
candidate, create a release-candidate commit, create a tag, publish a GitHub
Release, or publish release assets.

## Selected version mapping

Protos keeps its existing internal implementation trace points:

```text
0.2.N-SNAPSHOT
```

For the first selected public GitHub pre-release, the public version is derived
from the selected development baseline by removing exactly the terminal
`-SNAPSHOT` suffix:

```text
development baseline version: 0.2.N-SNAPSHOT
public release version:        0.2.N
Git tag:                       v0.2.N
GitHub Release title:          Protos 0.2.N
GitHub prerelease flag:        true
```

Example only:

```text
0.2.230-SNAPSHOT -> 0.2.230
```

The example is not a candidate selection. The actual `N`, baseline SHA, candidate
SHA, tag, and release remain unselected until the E4/E5 approval boundaries.

The GitHub `prerelease=true` state carries the current release-channel status.
DIST001 does not introduce a second `alpha`, `beta`, or `rc` counter merely to
repeat the same state in the version string. A later project decision may adopt
different release channels, but the first-release contract does not pre-commit
future releases to them.

## Coherent public identity

For a public version `V`, every user-visible release identity must agree:

```text
pom.xml project version:                 V
JAR Implementation-Version:              V
protos --version:                        Protos V
distribution root directory:             protos-V/
portable archive:                        protos-V-posix-jvm.zip
external checksum file:                  protos-V-posix-jvm.zip.sha256
Git tag:                                 vV
GitHub Release title:                    Protos V
GitHub Release prerelease flag:          true
SOURCE.txt implementation_version:       V
SOURCE.txt public_release:                true
SOURCE.txt artifact_kind:                 public-prerelease
```

A public bundle must not retain `-SNAPSHOT` in any of those public identity
positions.

The tag prefix `v` is tag syntax only. The project/tool/archive version remains
`V`, never `vV`.

## Development baseline versus release candidate

Release preparation must not force active development on `main` to stop or
temporarily convert `main` to a non-SNAPSHOT version.

E2 therefore distinguishes:

```text
development baseline
    an explicitly selected immutable commit from main whose project version is
    exactly V-SNAPSHOT

release candidate commit
    one immutable commit derived from that selected baseline by the E3/E4
    release-preparation mechanism, with public project/tool/distribution version V
    and candidate/release metadata
```

The candidate commit is the source revision ultimately identified by
`SOURCE.txt`, the tag, and the GitHub Release.

The selected development baseline remains separately recorded as provenance.
E3 must define a release metadata field for this relationship, conceptually:

```text
release_baseline_revision=<selected-main-sha>
source_revision=<candidate-commit-sha>
```

The candidate commit is not merged into `main` merely to publish the release.
`main` continues on the ordinary `0.2.N-SNAPSHOT`, `0.2.(N+1)-SNAPSHOT`, ...
development sequence as concurrent implementation work requires.

Before E5, the candidate commit may exist only as validated local release state
or another explicitly temporary preparation reference. E5 makes the accepted
candidate permanently reachable through the public `vV` tag.

## Candidate transition scope

E3 must make the transition mechanically reproducible. E4 may not hand-edit an
arbitrary tree until it happens to report `V`.

The release-candidate transition is allowed to change only release-owned
identity/metadata surfaces defined by E3. It must not silently incorporate
unrelated implementation work after the selected baseline.

At minimum E3 must own and validate:

- the exact `V-SNAPSHOT -> V` project-version transition;
- release-mode distribution metadata (`public_release=true`,
  `artifact_kind=public-prerelease`);
- baseline/candidate source provenance;
- release asset naming;
- release notes/manifest generation; and
- a guard proving the candidate's code/content lineage is the selected baseline
  plus only the declared release-preparation changes.

If an executable fix is required after baseline selection, that fix belongs on
normal development `main`, receives its ordinary implementation version
treatment, and requires a new explicit release baseline selection. It is not
smuggled into the candidate commit.

## Version derivation rules

Given a selected baseline project version `S`, E4 may derive a public version only
when all of these hold:

1. `S` matches exact numeric `MAJOR.MINOR.PATCH-SNAPSHOT`;
2. `MAJOR`, `MINOR`, and `PATCH` contain canonical decimal integers;
3. public `V` is exactly `S` with the final `-SNAPSHOT` removed;
4. `vV` does not already exist as a Git tag;
5. no GitHub Release already uses `vV`;
6. candidate tool output, POM/JAR metadata, distribution names, and release
   metadata all resolve to exactly `V`; and
7. the selected baseline SHA and candidate SHA are both recorded explicitly.

No implementation counter is renumbered merely for presentation. Public releases
may therefore skip numeric values naturally when intermediate implementation
snapshots are never selected as milestones.

## Immutability and retry rules

Once `vV` has been publicly created, `V` is immutable and must never be reused
for different candidate bytes or a different source revision.

A candidate rejected before E5 has no public release identity. A later attempt
may reuse the same derived `V` only if no public tag/Release exists and E4
revalidates a newly authorized candidate procedure from the selected baseline.
If the underlying implementation must change, select a new development baseline
instead.

E5 must fail closed if the tag or GitHub Release already exists.

## Relationship to package ReleaseVersion

The Package Tool has a separate `ReleaseVersion` value model. DIST001-E2 does not
make compiler/tool releases into package-registry releases and does not couple
toolchain version policy to package identity.

The chosen `MAJOR.MINOR.PATCH` public toolchain version is nevertheless a
canonical version shape and does not require introducing a parallel package
version syntax.

## Approval boundary remains unchanged

E2 selects only the mechanical mapping. It does not select `N`, a baseline SHA,
a candidate SHA, or a public release.

At E2 closure:

```text
release baseline revision:        UNSELECTED
release candidate source revision: UNSELECTED
public release version:            UNSELECTED
Git tag:                           NOT CREATED
GitHub Release:                    NOT CREATED
release assets:                    NOT PUBLISHED
```

E3 may implement release preparation and validation machinery generically.

E4 remains candidate-specific and cannot begin until the user explicitly selects
an exact development baseline and authorizes the mechanically derived public
version/candidate procedure defined here.

E5 remains the first slice allowed to create the public tag or GitHub Release.

## E3A implementation checkpoint

E3A implements the generic build-time half of this contract.

`dist/build_portable.py` retains development mode as its default and adds only an
explicit `--public-prerelease --release-baseline <sha>` path. The release path
requires a clean candidate, public `V`, exact baseline SHA, baseline project
version `V-SNAPSHOT`, and baseline ancestry. `SOURCE.txt` then records candidate
and baseline provenance separately together with `public_release=true`,
`artifact_kind=public-prerelease`, `release_version=V`, and `release_tag=vV`.

`dist/release_identity.py` owns those mechanical guards so E3/E4 validation does
not reconstruct version/provenance rules independently.

E3A still selects no concrete baseline, candidate, or public version. The first
real release-mode archive remains an E4 candidate-specific validation event.

## E3B implementation checkpoint

E3B adds deterministic release-note and release-asset metadata generation for an
already-built public-prerelease archive. Candidate identity and runtime facts
come from the archive's own `SOURCE.txt`/`RUNTIME.txt`; the user/maintainer must
supply the candidate-specific specification revision plus explicit capability
and limitation claims.

This separation is intentional: identity/runtime facts are machine-verifiable,
while release claims are editorial assertions that must be consciously selected
and then checked by E3C/E4 against blockers and the exact candidate.

The output envelope contains `RELEASE_NOTES.md`, `RELEASE_MANIFEST.txt`, and a
portable `<archive>.sha256`. E3B still selects no concrete baseline, candidate,
or public version and publishes no Git tag, GitHub Release, or release asset.

## E3C1 implementation checkpoint

E3C1 threads the E2/E3A release identity through the already-closed B2/B5
distribution-conformance machinery. Development `V-SNAPSHOT` verification
remains the default. Explicit public-prerelease mode requires public `V`, clean
candidate `HEAD`, exact `V-SNAPSHOT` baseline provenance, and the E3A SOURCE
release fields before the existing extracted B3/B4A/B4B checks run against the
same immutable archive.

No candidate is selected by this implementation. E3C2 owns independent E3B
envelope verification; E3C3 then composes both validation surfaces into the
candidate-specific entry point used by E4.

## E3C2 implementation checkpoint

E3C2 independently verifies the E3B release envelope against one exact
public-prerelease ZIP. The verifier binds archive bytes, SOURCE/RUNTIME identity,
baseline provenance, release version/tag, specification revision, outer checksum,
release notes, and their manifest digests without regenerating the envelope.

The verifier also requires both capability and limitation sections to remain
explicitly populated, but it intentionally does not infer whether those claims
are true from implementation status. E3C3/E4 own the candidate-specific
claim/blocker audit.

E3C2 selects no concrete baseline, candidate, or public version and creates no
tag, GitHub Release, or release asset.

## E3C3 implementation checkpoint

E3C3 closes the generic release-preparation mechanism by composing E3C1
release-aware B5 validation and E3C2 independent envelope verification with
candidate checkout identity, release-only baseline lineage, current specification
identity, local/remote tag
availability, and an explicit candidate audit.

The audit deliberately distinguishes two decisions:

```text
candidate_selection_authorized=true
selection_authorization_basis=explicit-user-decision
release_publication_authorized=false
```

E4 may create that audit only after the user explicitly selects the exact
development baseline/public version and the resulting candidate is reviewed.
A green E4 candidate gate therefore does not authorize E5 publication.

At E3 closure no concrete baseline, candidate, or public version has been
selected and no tag, GitHub Release, or release asset has been created.

## E4A exact selection checkpoint

After I023 and blocker B007 closed, the user explicitly authorized continuing
with the deterministic first-post-closure `origin/main` selection procedure.
E4A therefore freezes this exact candidate basis:

```text
release_baseline_revision=3c23eaaccecbdcc7c2bcd86bc30c445403cfb047
release_baseline_version=0.2.236-SNAPSHOT
release_version=0.2.236
release_tag=v0.2.236
specification_revision=0.1.382
candidate_source_revision=UNMATERIALIZED
release_publication_authorized=false
```

At the selected baseline, I023 is CLOSED and B007 is CLOSED. Subsequent movement
of `main` does not change this selection: E4B must derive the candidate from the
exact selected baseline or fail closed. If an implementation fix is required,
this selection is abandoned and a new baseline must be explicitly selected.

E4 is further decomposed into E4A selection freeze, E4B detached candidate
materialization, E4C archive/envelope/audit preparation, and E4D immutable full
validation. E4A creates no candidate commit, tag, GitHub Release, or release
asset.
