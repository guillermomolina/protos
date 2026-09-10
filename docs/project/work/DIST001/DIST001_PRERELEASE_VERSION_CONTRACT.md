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


## E4B1 detached-worktree checkpoint

E4B is subdivided before candidate mutation:

```text
E4B1  selected-baseline detached-worktree guard
E4B2  exact POM V-SNAPSHOT -> V transition
E4B3  candidate commit creation
E4B4  release-only lineage verification
```

E4B1 publishes a fail-closed local worktree primitive. It consumes the frozen
E4A selection, verifies exact baseline `3c23eaaccecbdcc7c2bcd86bc30c445403cfb047` and `0.2.236-SNAPSHOT`, and may
create only a clean detached worktree outside the main checkout. Branch refs
must remain unchanged. The primitive performs no candidate mutation and no
publication operation.

The real candidate source revision therefore remains `UNMATERIALIZED` at E4B1
closure. E4B2 is the first slice allowed to mutate the selected-baseline
worktree, and only for the exact project POM transition `0.2.236-SNAPSHOT` ->
`0.2.236`.

## E4B2 exact POM transition checkpoint

E4B2 closes the mutation boundary between the selected detached baseline
worktree and the future candidate commit.

For the selected first pre-release:

```text
release_baseline_revision=3c23eaaccecbdcc7c2bcd86bc30c445403cfb047
release_baseline_version=0.2.236-SNAPSHOT
release_version=0.2.236
release_tag=v0.2.236
```

the only E4B2 change permitted in the candidate worktree is the root Maven
project-version token:

```text
0.2.236-SNAPSHOT -> 0.2.236
```

No dependency/plugin/property version that merely contains the same text may be
changed. The post-transition worktree remains detached and uncommitted with
exactly `pom.xml` modified. E4B3 owns staging and creation of the candidate
commit; E4B4 owns independent release-only lineage proof.

E4B2 itself materializes no real release worktree or candidate commit during
publication and keeps release publication unauthorized.

## E4B3A candidate commit checkpoint

E4B3 is subdivided so commit mechanics and the first real candidate identity are
not conflated:

```text
DIST001-E4B3A  candidate-commit primitive + guards
DIST001-E4B3B  real detached candidate materialization
```

E4B3A binds the commit primitive to the frozen selection:

```text
release_baseline_revision=3c23eaaccecbdcc7c2bcd86bc30c445403cfb047
release_baseline_version=0.2.236-SNAPSHOT
release_version=0.2.236
release_tag=v0.2.236
```

The primitive accepts only the exact uncommitted E4B2 `pom.xml` transition,
stages only that file and creates one detached commit whose parent is exactly the
selected baseline. It must not create or move branch/tag refs and must not push.

E4B3A publishes/tests this mechanism only. `candidate_source_revision` therefore
remains `UNMATERIALIZED` in the E4A selection record. E4B3B owns creation and
capture of the real candidate SHA; release publication remains separately
unauthorized.

## E4B3B1 materialization composition checkpoint

E4B3B is subdivided so recovery/idempotency mechanics are proven independently
from creation of the first real candidate identity:

```text
DIST001-E4B3B1  composition + recovery/idempotency guard
DIST001-E4B3B2  real candidate creation + SHA persistence
```

B3B1 composes B1 -> B2 -> B3A while the frozen selection remains:

```text
release_baseline_revision=3c23eaaccecbdcc7c2bcd86bc30c445403cfb047
release_baseline_version=0.2.236-SNAPSHOT
release_version=0.2.236
release_tag=v0.2.236
candidate_source_revision=UNMATERIALIZED
release_publication_authorized=false
```

The materializer may safely resume an exact B1, B2 or already-created B3A local
state, but accepts no broader recovery state. An exact candidate commit remains
detached and must have no local branch/tag ref; the registered candidate
worktree is the temporary reachability anchor until later E4/E5 ownership
changes it.

B3B1 itself uses isolated fixture repositories only. B3B2 owns the first real
candidate materialization and the main-ledger transition from
`candidate_source_revision=UNMATERIALIZED` to its exact 40-hex SHA. No E4 result
authorizes E5 publication.

## E4B3B2 real candidate identity checkpoint

The first real candidate for the frozen E4A selection is now materialized:

```text
release_baseline_revision=3c23eaaccecbdcc7c2bcd86bc30c445403cfb047
release_baseline_version=0.2.236-SNAPSHOT
release_version=0.2.236
release_tag=v0.2.236
candidate_source_revision=957b1e16793a682de1d6406e37b5734c44d32d19
release_publication_authorized=false
```

The candidate is one detached single-parent commit whose parent is exactly the
selected baseline. Baseline -> candidate changes only the root Protos Maven
project version from `0.2.236-SNAPSHOT` to `0.2.236`. No branch or tag names the candidate;
its registered detached worktree remains the temporary local reachability anchor
until later E4/E5 work establishes the next policy-owned reachability state.

E4B3B2 persists the exact candidate identity but does not independently close the
release-only lineage proof: E4B4 is READY and owns that separate verification.
No Git tag, GitHub Release, release asset, or release-publication authorization is
created by this checkpoint.

## E4B4 independent lineage closure checkpoint

Independent verification closes E4B for:

```text
release_baseline_revision=3c23eaaccecbdcc7c2bcd86bc30c445403cfb047
candidate_source_revision=957b1e16793a682de1d6406e37b5734c44d32d19
release_baseline_version=0.2.236-SNAPSHOT
release_version=0.2.236
release_tag=v0.2.236
release_publication_authorized=false
```

The candidate has exactly one parent, the selected baseline, and the complete
baseline-to-candidate tree delta is exactly one modified `pom.xml`. Candidate
POM bytes equal baseline POM bytes except for the one root Protos project-version
transition `0.2.236-SNAPSHOT -> 0.2.236`. The candidate remains clean, detached and
reachable through exactly one registered local worktree with no branch/tag or
remote-tracking ref naming it. The future public tag remains absent.

This closes candidate materialization, not release publication. E4C is the next
parent and remains entirely preparatory.

## E4C1 public-prerelease archive build checkpoint

The exact persisted candidate has produced its first real public-prerelease
portable archive:

```text
release_baseline_revision=3c23eaaccecbdcc7c2bcd86bc30c445403cfb047
candidate_source_revision=957b1e16793a682de1d6406e37b5734c44d32d19
release_version=0.2.236
archive_name=protos-0.2.236-posix-jvm.zip
archive_sha256=b1a58ba445d082156bd4eb637ee6df70c046abdee600d468c0fac29be065e296
```

The archive is generated from the clean detached candidate through the already
published release mode:

```text
python3 dist/build_portable.py   --public-prerelease   --release-baseline 3c23eaaccecbdcc7c2bcd86bc30c445403cfb047
```

E4C1 records only build identity. The builder's own archive/layout/source/runtime
checks must pass, but E4C2 deliberately owns the independent archive identity and
`SOURCE.txt` / `RUNTIME.txt` verification. E4C1 does not create the E3B release
envelope, candidate audit, Git tag, GitHub Release, or release assets.

## E4C2 independent archive identity checkpoint

The exact E4C1 bytes have now passed independent verification:

```text
release_baseline_revision=3c23eaaccecbdcc7c2bcd86bc30c445403cfb047
candidate_source_revision=957b1e16793a682de1d6406e37b5734c44d32d19
release_version=0.2.236
archive_name=protos-0.2.236-posix-jvm.zip
archive_sha256=b1a58ba445d082156bd4eb637ee6df70c046abdee600d468c0fac29be065e296
archive_identity_independently_verified=true
```

Independent verification does not call `dist/build_portable.py` and therefore
does not silently replace missing or changed bytes. It checks the persisted
external SHA-256, exact archive root/CRC, exact public-prerelease `SOURCE.txt`,
exact supported `RUNTIME.txt`, full internal checksum coverage/values, and the
shaded JAR `Implementation-Version`.

E4C3 is the next owner and may select only truthful release-note capabilities
and limitations against this fixed candidate/archive identity. No E4C2 result
authorizes release publication.

## E4C3 release-note claim selection checkpoint

Release-note claims are now frozen against the exact candidate/archive identity:

```text
release_baseline_revision=3c23eaaccecbdcc7c2bcd86bc30c445403cfb047
candidate_source_revision=957b1e16793a682de1d6406e37b5734c44d32d19
release_version=0.2.236
specification_revision=0.1.382
archive_name=protos-0.2.236-posix-jvm.zip
archive_sha256=b1a58ba445d082156bd4eb637ee6df70c046abdee600d468c0fac29be065e296
claims_record=docs/project/evidence/DIST001/DIST001_E4_RELEASE_CLAIMS.txt
known_blockers_review=PASS
release_publication_authorized=false
```

The selected capabilities deliberately describe only behavior supported by the
candidate source and verified portable distribution. The limitations explicitly
preserve the experimental/draft status, exact POSIX/GraalVM/JDK runtime scope,
the candidate-time incompleteness of Package Tool and Test Tool, and incomplete
Programming Guide coverage.

E4C3 does not render `RELEASE_NOTES.md`. E4C4 owns deterministic E3B envelope
generation from this exact ordered claim set. No claim-selection result creates
a tag, GitHub Release, release asset, or release-publication authorization.

## E4C4 deterministic release-envelope checkpoint

The frozen E4C3 claims have been rendered through the already-published E3B
metadata generator into one persistent candidate-local envelope:

```text
release_baseline_revision=3c23eaaccecbdcc7c2bcd86bc30c445403cfb047
candidate_source_revision=957b1e16793a682de1d6406e37b5734c44d32d19
release_version=0.2.236
specification_revision=0.1.382
archive_name=protos-0.2.236-posix-jvm.zip
archive_sha256=b1a58ba445d082156bd4eb637ee6df70c046abdee600d468c0fac29be065e296
claims_sha256=ad0b77ae5bd41bc16e306487072620c99581643d68ed5141011e7537c0439e33
envelope_directory_name=release-envelope-0.2.236
release_notes_sha256=98684922feebb1cec41da2a51fca6776cd76aa5c99561b37adb733e5b944ed36
release_manifest_sha256=2d71e48bf27e52d76bd4bd9166dca4298487532b1de08e832758c7f2abe7dcd4
portable_checksum_sha256=34b2d9a86c0e136ac2f9d92c8edf94563b9ea941c896f54eddce929680969035
deterministic_generation_verified=true
e3c2_envelope_verification=PASS
release_publication_authorized=false
```

E4C4 generated the envelope twice from the same fixed archive and ordered C3
claim record and required byte-identical outputs before retaining the persistent
copy. It then ran the already-published independent E3C2 envelope verifier and
checked that the rendered capability and limitation bullets preserve the C3
ordering and text exactly.

The envelope remains local release-preparation state under the detached candidate
worktree. E4C5 owns candidate-audit materialization. No Git tag, GitHub Release,
asset upload, or release-publication authorization is created here.

## E4C5 candidate-audit checkpoint

The explicit E3C3 candidate audit is now materialized byte-identically in
`docs/project/evidence/DIST001/DIST001_E4_CANDIDATE_AUDIT.txt` and in separate candidate-local release-preparation state as
`target/release-candidate-audit-0.2.236/RELEASE_CANDIDATE_AUDIT.txt`:

```text
release_candidate_audit_format=protos-release-candidate-audit-v1
candidate_selection_authorized=true
selection_authorization_basis=explicit-user-decision
release_publication_authorized=false
source_revision=957b1e16793a682de1d6406e37b5734c44d32d19
release_baseline_revision=3c23eaaccecbdcc7c2bcd86bc30c445403cfb047
release_version=0.2.236
release_tag=v0.2.236
specification_revision=0.1.382
capabilities_review=PASS
limitations_review=PASS
known_blockers_review=PASS
audit_sha256=0f3ea9a321a462e977f4f33a2b4c24754a5cacbd8e45e0df8bc6639d4286692b
```

E4C5 validates those exact bytes with the already-published E3C3 audit verifier
against the candidate archive SOURCE identity. It intentionally does not run the
full candidate/B5 gate; immutable full candidate validation remains E4D.

E4C is now closed. E4D is decomposed into independent validation leaves starting
with E4D1 envelope/audit consistency. Release publication remains separately
unauthorized; no tag, GitHub Release, or asset upload exists.

## E4D1 immutable consistency checkpoint

E4D begins with an independent consistency pass over the already prepared
candidate state. The pass is intentionally narrower than later E4D leaves: it
does not execute release-aware B5, re-audit claim truth, or check tag/GitHub
Release collisions.

The fixed identity is:

```text
release_baseline_revision=3c23eaaccecbdcc7c2bcd86bc30c445403cfb047
candidate_source_revision=957b1e16793a682de1d6406e37b5734c44d32d19
release_version=0.2.236
release_tag=v0.2.236
specification_revision=0.1.382
archive_sha256=b1a58ba445d082156bd4eb637ee6df70c046abdee600d468c0fac29be065e296
claims_sha256=ad0b77ae5bd41bc16e306487072620c99581643d68ed5141011e7537c0439e33
release_notes_sha256=98684922feebb1cec41da2a51fca6776cd76aa5c99561b37adb733e5b944ed36
release_manifest_sha256=2d71e48bf27e52d76bd4bd9166dca4298487532b1de08e832758c7f2abe7dcd4
portable_checksum_sha256=34b2d9a86c0e136ac2f9d92c8edf94563b9ea941c896f54eddce929680969035
candidate_audit_sha256=0f3ea9a321a462e977f4f33a2b4c24754a5cacbd8e45e0df8bc6639d4286692b
d1_record_consistency=PASS
release_publication_authorized=false
```

The independent D1 verifier cross-checks the selection, artifact, claims,
envelope and audit schemas against that frozen identity, then verifies the exact
candidate-local archive/envelope/audit bytes and the byte-identical main/local
candidate audit. It also requires the candidate worktree to remain clean and
detached. The result is persisted in `docs/project/evidence/DIST001/DIST001_E4_VALIDATION.txt`.

E4D2 is now READY and owns the extracted release-aware B5 candidate gate.

## E4D2 release-aware B5 checkpoint

The frozen public-prerelease archive has passed the already-published E3C1/B5
distribution gate directly from the clean detached candidate:

```text
release_baseline_revision=3c23eaaccecbdcc7c2bcd86bc30c445403cfb047
candidate_source_revision=957b1e16793a682de1d6406e37b5734c44d32d19
release_version=0.2.236
archive_name=protos-0.2.236-posix-jvm.zip
archive_sha256=b1a58ba445d082156bd4eb637ee6df70c046abdee600d468c0fac29be065e296
artifact_mode=public-prerelease
source_mode=require-clean
d2_release_b5=PASS
```

D2 runs exactly one heavy B5 pass on the normal publication path. The B5
surface independently re-checks public-prerelease archive/source identity,
outside-checkout Package Tool behavior, bundled Test Tool behavior, and the
selected optimizing GraalVM Community JDK 22 / Truffle 24 runtime against the
same immutable ZIP. The launcher requires the archive SHA-256 to remain
`b1a58ba445d082156bd4eb637ee6df70c046abdee600d468c0fac29be065e296` before and after B5.

D2 does not re-audit the truth of the frozen release claims or current blocker/
specification evidence; E4D3 owns that work. It also does not decide tag/GitHub
Release collision or publication availability; E4D4 owns those guards.

If `origin/main` moves after the candidate B5 pass, D2 rebases only its
documentation ledger and re-checks the frozen identities. It does not rerun B5
merely because unrelated `main` moved: the validated subject is the immutable
candidate/archive pair above.

E4D3 is now READY. Release publication remains explicitly unauthorized.

## E4D3 candidate claims/blockers/spec checkpoint

The frozen E4C3 editorial claims have been independently re-audited against the
exact candidate source rather than current moving `main`:

```text
release_baseline_revision=3c23eaaccecbdcc7c2bcd86bc30c445403cfb047
candidate_source_revision=957b1e16793a682de1d6406e37b5734c44d32d19
release_version=0.2.236
specification_revision=0.1.382
archive_name=protos-0.2.236-posix-jvm.zip
archive_sha256=b1a58ba445d082156bd4eb637ee6df70c046abdee600d468c0fac29be065e296
claims_sha256=ad0b77ae5bd41bc16e306487072620c99581643d68ed5141011e7537c0439e33
capability_count=4
limitation_count=5
b001_through_b008=CLOSED
d3_claims_blockers_spec=PASS
release_publication_authorized=false
```

The D3 verifier first requires the detached candidate commit to have exact parent
`3c23eaaccecbdcc7c2bcd86bc30c445403cfb047` and to differ from that release baseline only in `pom.xml`.
All claim evidence is then read with `git show 957b1e16793a682de1d6406e37b5734c44d32d19:<path>`; later
documentation or implementation work on `main` cannot retroactively strengthen
or invalidate the historical 0.2.236 claim audit.

The four capability claims are checked against candidate-time portable CLI/
distribution evidence, Core/control implementation and normative owners,
Future/parallel/Actor implementation and task-scoped ownership, and the closed
standard value/collection/I/O/filesystem/process surfaces plus physical numeric
protocol installation.

The five limitation claims are checked against the candidate's experimental/
draft status, exact GraalVM JDK22 + Truffle24 / Java21-bytecode distribution
contract, incomplete Package Tool, incomplete Test Tool and deferred hard-timeout
work, and the candidate-time three-chapter Programming Guide whose control-flow
chapter was still unpublished.

B001 through B008 are parsed independently by blocker section and each must be
`CLOSED`. The candidate specification changelog must have global revision
`0.1.382` as its newest revision, with D043/D044/D045 present, and the relevant
normative owner documents must remain Core v0.1 `Draft`.

E4D4 is now READY for tag/GitHub Release collision and publication-state guards.
D3 creates no tag, Release, asset, candidate change, or publication authorization.
