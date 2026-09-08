# Protos portable development distribution

This directory owns the build-time layout for `DIST001`.

It does not create a Git tag or GitHub Release. The resulting archive is a
development distribution until a later explicitly selected release candidate
passes DIST001-B/D/E.

## Build

From the repository root:

```sh
make dist
```

or:

```sh
python3 dist/build_portable.py
```

The build first packages the current Maven project, resolves the selected
optimizing Truffle runtime dependency closure, and creates:

```text
target/distributions/protos-<implementation-version>-posix-jvm.zip
```

The archive contains a relocatable root:

```text
protos-<version>/
├── bin/protos
├── lib/
│   ├── protos.jar
│   └── runtime/
├── protos/
│   ├── lib/
│   ├── tools/
│   ├── tests/
│   ├── examples/
│   └── tutorials/
├── LICENSE.TXT
├── README.md
├── SOURCE.txt
├── RUNTIME.txt
├── DEPENDENCIES.txt
└── SHA256SUMS
```

`bin/protos` sets `PROTOS_HOME` to the extracted distribution root but preserves
the caller's current working directory. Project-relative paths therefore remain
relative to the project invoking Protos rather than to the toolchain install.

## Initial runtime contract

DIST001-A deliberately uses the exact optimizing stack already retained by
`PERF002-B`:

```text
GraalVM Community Edition for JDK 22
org.graalvm.truffle:truffle-runtime:24.0.0
expected optimizing runtime: HotSpotTruffleRuntime
```

The project Maven bytecode target remains Java 21, but that is not by itself a
distribution support claim.

The extracted launcher defaults to rejecting a different Java feature version or
a runtime whose vendor metadata does not identify GraalVM. Developers may set:

```sh
PROTOS_ALLOW_UNSUPPORTED_RUNTIME=1
```

to experiment on another runtime, but that execution is outside the DIST001-A
supported runtime contract.

The repository development container may use a newer GraalVM/JDK. Development
build compatibility and end-user optimizing-runtime support are intentionally
different claims. Moving the distribution to a newer GraalVM/Truffle stack
requires explicit revalidation rather than silently following the build
container.

## Archive identity verification

`DIST001-B2` adds a non-executing archive verifier:

```sh
python3 dist/build_portable.py
python3 dist/verify_portable.py --require-clean-source
```

The verifier opens the ZIP directly. It checks the ZIP CRCs and one-root layout,
requires `SOURCE.txt` to identify the exact repository `HEAD` and a clean source
tree, and verifies that `SHA256SUMS` covers every distributed file except itself
with no missing, extra, duplicate, unsafe, or mismatched entries.

This slice deliberately does not extract or execute Protos.

## Outside-checkout CWD smoke

`DIST001-B3` validates relocation and caller-working-directory behavior:

```sh
python3 dist/build_portable.py
python3 dist/verify_portable.py --require-clean-source
sh dist/smoke_cwd_package.sh
```

The smoke first relies on B2's direct archive identity/checksum verification,
then extracts the ZIP into a temporary toolchain tree outside the Git checkout
and creates a distinct temporary project directory. From that project directory
it executes a relative `.protos` source path and runs `protos package manifest`
against a `protos.toml` that exists only in the caller project.

If the validation host matches the selected GraalVM Community JDK 22 contract,
the extracted launcher runs unchanged with the bundled optimizing runtime. If the
host does not match, B3 sets the documented unsupported-runtime override and
moves the optimizing-runtime JARs out of `lib/runtime` **only inside the
disposable extracted smoke copy**. This forces B3 to exercise the fallback
Truffle path instead of loading a runtime/JDK combination outside the selected
contract. The original archive is not modified.

This isolation is intentional: B3 owns relocation/CWD behavior, while
`DIST001-B4` owns the bundled Test Tool and exact `HotSpotTruffleRuntime` proof
for the selected optimizing-runtime contract. The smoke prints captured launcher
stdout/stderr before failing so a runtime or command failure is diagnosable.


## Bundled Test Tool smoke

`DIST001-B4A` validates that the Test Tool bundled in the extracted distribution
can bootstrap and execute its current bundled plan:

```sh
python3 dist/build_portable.py
python3 dist/verify_portable.py --require-clean-source
sh dist/smoke_test_tool.sh
```

The smoke extracts a disposable toolchain outside the checkout and invokes public
`protos test` from a separate temporary project directory. It requires the
current public Test Tool bootstrap marker and argument marker after successful
execution.

If the validation JDK does not match the selected GraalVM Community JDK 22
contract, B4A applies the same disposable-copy optimizer isolation established by
B3 and exercises fallback Truffle. That proves bundled Test Tool portability
without treating the host runtime as supported.

Exact optimizing-runtime validation is deliberately separate:
`DIST001-B4B` owns the intact optimizer classpath and exact
`com.oracle.truffle.runtime.hotspot.HotSpotTruffleRuntime` proof.


## Exact optimizing-runtime smoke

`DIST001-B4B` validates the optimizer path separately from the fallback smokes:

```sh
PROTOS_DIST001_B4B_JAVA_HOME=/path/to/graalvm-community-jdk-22.0.0 \
    sh dist/smoke_optimizing_runtime.sh
```

The smoke requires exact GraalVM Community JDK `22.0.0`; it does not accept the
unsupported-runtime override. It extracts the verified distribution outside the
checkout, keeps `lib/runtime` intact, verifies the bundle still declares Truffle
`24.0.0`, and runs the extracted launcher through its normal supported-runtime
gate.

It then compiles `Dist001RuntimeProbe.java` against the exact extracted
distribution classpath and requires `Truffle.getRuntime()` to resolve exactly:

```text
com.oracle.truffle.runtime.hotspot.HotSpotTruffleRuntime
```

The publication launcher may provision the pinned GraalVM JDK into a user cache
before modifying the repository when the development JDK is different. The JDK
archive and its official SHA-256 are fetched from the GraalVM Community JDK
`22.0.0` release; the JDK is an external validation dependency and is not added
to the Protos distribution.


## Complete extracted-distribution gate

`DIST001-B5` composes the previously independent evidence against one exact ZIP:

```sh
python3 dist/build_portable.py
PROTOS_DIST001_B4B_JAVA_HOME=/path/to/graalvm-community-jdk-22.0.0 \
    sh dist/validate_portable.sh --require-clean-source
```

The gate verifies B2 archive/source/checksum identity, B3 outside-checkout
caller-CWD and Package Tool behavior, B4A bundled Test Tool execution, and B4B
exact supported optimizing-runtime selection. Every sub-gate receives the same
archive path. The outer ZIP SHA-256 is checked before and after the full run so
disposable fallback isolation cannot mutate the validated artifact.

This is the reusable complete distribution-conformance entry point for
development artifacts. `DIST001-D` may call this gate from CI, but a green run
does not create a tag or GitHub Release.


## CI development snapshots

`DIST001-D1` defines `.github/workflows/distribution.yml`. On every push to
`main`, and on explicit `workflow_dispatch`, the job:

1. checks out the exact workflow revision;
2. selects GraalVM Community JDK `22.0.0`;
3. runs the full Maven test suite;
4. builds the portable distribution from the clean checkout;
5. runs `dist/validate_portable.sh --require-clean-source` against that exact
   revision and selected optimizing runtime;
6. writes an outer SHA-256 file for the portable ZIP; and
7. uploads the ZIP plus checksum as a transient GitHub Actions artifact named
   `protos-snapshot-<full-source-sha>`.

Snapshots are retained for 14 days. They are development artifacts, not Git tags
or GitHub Releases, and their presence does not authorize public release
publication.

`DIST001-D2` owns observed closure: D does not close merely because the workflow
definition exists. The first D1 run (`34100296135`, source
`dc64fb8c4103b44020cd1b718d6850028f08b3a5`) completed successfully and uploaded
the expected artifact, but D2 inspection found that the external `.sha256`
recorded the runner's absolute archive path. The digest value was correct, but
`sha256sum -c` therefore failed after download on another machine.

`DIST001-D2A` corrects artifact portability by generating the checksum from the
archive directory so the checksum records only the portable ZIP basename, and
the workflow verifies that file with `sha256sum -c` before upload.

`DIST001-D2B` closes the observed-artifact boundary with real CI evidence. The
`Distribution snapshot` run `34101588533` for source `994429173b6ec0fc086f307f4a49815f219c6523` completed
successfully, including the full Maven suite, portable build, complete B5 gate,
checksum preparation, and artifact upload. GitHub published artifact
`protos-snapshot-994429173b6ec0fc086f307f4a49815f219c6523` (artifact id `10010752033`, Actions digest
`sha256:ada6593e40efee2e78981d0d0881817b5061250db6dbbd8946ad93b786e049f4`).

Independent downloaded-artifact inspection found exactly:

```text
protos-0.2.230-SNAPSHOT-posix-jvm.zip
protos-0.2.230-SNAPSHOT-posix-jvm.zip.sha256
```

The external checksum records only the portable basename:

```text
f66f011ba9a7c579f81b5aad7098bd4ec421ebc8117b3c4c8954374441e94337  protos-0.2.230-SNAPSHOT-posix-jvm.zip
```

and `sha256sum -c protos-0.2.230-SNAPSHOT-posix-jvm.zip.sha256` succeeds after download outside the
runner workspace. DIST001-D2 and parent DIST001-D are therefore closed. This CI
artifact remains a transient development snapshot, not a Git tag or GitHub
Release.


## Public pre-release candidate build mode

`DIST001-E3A` adds a release-candidate metadata path without changing the default
development build.

Normal development remains:

```sh
python3 dist/build_portable.py
```

and requires the project version to retain canonical
`MAJOR.MINOR.PATCH-SNAPSHOT`; its `SOURCE.txt` remains
`artifact_kind=development-distribution` and `public_release=false`.

A future E4 candidate whose checked-out project version is public `V` is built
explicitly with:

```sh
python3 dist/build_portable.py \
    --public-prerelease \
    --release-baseline <exact-40-hex-baseline-sha>
```

The release mode fails closed unless:

- the candidate worktree is clean;
- the project version is canonical `MAJOR.MINOR.PATCH` with no `-SNAPSHOT`;
- the baseline is an exact 40-hex commit;
- the baseline `pom.xml` version is exactly `V-SNAPSHOT`; and
- the baseline is an ancestor of the candidate source revision.

Release-mode `SOURCE.txt` records both identities and the selected E2 mapping:

```text
artifact_kind=public-prerelease
public_release=true
release_baseline_revision=<baseline-sha>
release_baseline_version=V-SNAPSHOT
release_version=V
release_tag=vV
source_revision=<candidate-sha>
```

This mechanism does not select a baseline/candidate/version. E3A unit fixtures
exercise the version/provenance rules generically; E4 owns the first real
candidate build and complete end-to-end release validation.


## Release notes and asset envelope

`DIST001-E3B` prepares deterministic user-facing metadata for an already-built
public-prerelease candidate archive. It does not select or build a real
candidate.

Generic use:

```sh
python3 dist/prepare_release_metadata.py \
    --archive /path/to/protos-V-posix-jvm.zip \
    --spec-revision X.Y.Z \
    --capability "Important capability claim" \
    --limitation "Important limitation claim" \
    --output-dir /path/to/release-envelope
```

The generator reads candidate identity and runtime facts from the ZIP's own
`SOURCE.txt` and `RUNTIME.txt`; those are not retyped as command-line claims. It
fails closed unless the bundle is clean `artifact_kind=public-prerelease`,
`public_release=true`, has coherent release version/tag/baseline/candidate
provenance, and carries the runtime fields needed for release notes.

Capabilities and limitations are always explicit inputs. The generator does not
infer product claims from open/closed ledger rows, source files, or test names.

For identical archive bytes and identical ordered claim inputs, output is
byte-for-byte deterministic:

```text
RELEASE_NOTES.md
RELEASE_MANIFEST.txt
protos-V-posix-jvm.zip.sha256
```

The checksum records only the portable ZIP basename and is suitable for
`sha256sum -c` after download. The manifest records release/source/baseline/spec
identity, runtime identity, archive SHA-256, checksum SHA-256, and notes SHA-256.
E3C owns end-to-end validation of this envelope against the candidate archive.


## Release-aware complete distribution gate

`DIST001-E3C1` extends the existing B2/B5 validation machinery without changing
its development-snapshot default.

Development validation remains:

```sh
PROTOS_DIST001_B4B_JAVA_HOME=/path/to/graalvm-community-jdk-22.0.0 \
    sh dist/validate_portable.sh --require-clean-source
```

A future E4 candidate checkout whose project version is public `V` uses:

```sh
PROTOS_DIST001_B4B_JAVA_HOME=/path/to/graalvm-community-jdk-22.0.0 \
    sh dist/validate_portable.sh \
        --archive /path/to/protos-V-posix-jvm.zip \
        --public-prerelease \
        --release-baseline <exact-V-SNAPSHOT-baseline-sha> \
        --require-clean-source
```

The release-aware B2 identity step requires the candidate ZIP to identify the
current candidate `HEAD`, clean source, `artifact_kind=public-prerelease`,
`public_release=true`, public version `V`, tag `vV`, and the exact selected
baseline whose committed `pom.xml` is `V-SNAPSHOT` and which is an ancestor of
the candidate commit.

After that identity check, B3 caller-CWD/Package Tool, B4A Test Tool, and B4B
exact optimizing-runtime validation run against the same immutable ZIP exactly
as they do for development snapshots.

E3C1 does not select or build a real candidate. It only makes the already-proven
B5 machinery capable of validating one once E4 supplies an explicitly approved
candidate.


## Independent release-envelope verification

`DIST001-E3C2` adds an independent verifier for the metadata produced by E3B:

```sh
python3 dist/verify_release_metadata.py \
    --archive /path/to/protos-V-posix-jvm.zip \
    --envelope-dir /path/to/release-envelope
```

The verifier does not regenerate the expected files. It reads the candidate ZIP
and checks the already-produced envelope against it:

- the envelope contains exactly `RELEASE_NOTES.md`, `RELEASE_MANIFEST.txt`, and
  the basename-only `<archive>.sha256`;
- the manifest has the exact v1 key set and matches candidate SOURCE/RUNTIME
  identity, baseline provenance, public version/tag, runtime, archive name and
  SHA-256;
- the external checksum content is exactly `<archive-sha256>  <archive-basename>`;
- manifest digests for the checksum and release notes match their actual bytes;
- release notes repeat the exact candidate/baseline/spec/runtime/archive
  identity from the machine-verifiable metadata; and
- both Important capabilities and Important limitations contain at least one
  explicit claim.

Changing the archive, checksum, notes, manifest, or envelope file set after E3B
therefore invalidates the envelope unless all dependent metadata is deliberately
re-prepared and revalidated.

E3C2 still does not decide whether the editorial claims are truthful for a real
candidate. That candidate-specific blocker/claim audit belongs to E3C3/E4.


## Composed public pre-release candidate gate

`DIST001-E3C3` composes the generic first-pre-release validation machinery into
one candidate gate:

```sh
PROTOS_DIST001_B4B_JAVA_HOME=/path/to/graalvm-community-jdk-22.0.0 \
    python3 dist/validate_release_candidate.py \
        --archive /path/to/protos-V-posix-jvm.zip \
        --envelope-dir /path/to/release-envelope \
        --candidate-audit /path/to/RELEASE_CANDIDATE_AUDIT.txt
```

The candidate audit is candidate-specific E4 input. Its exact v1 form is:

```text
release_candidate_audit_format=protos-release-candidate-audit-v1
candidate_selection_authorized=true
selection_authorization_basis=explicit-user-decision
release_publication_authorized=false
source_revision=<candidate-sha>
release_baseline_revision=<selected-main-baseline-sha>
release_version=V
release_tag=vV
specification_revision=X.Y.Z
capabilities_review=PASS
limitations_review=PASS
known_blockers_review=PASS
```

The composed gate fails closed unless:

- candidate `SOURCE.txt` identifies the clean checkout `HEAD`;
- candidate lineage from the selected baseline changes exactly `pom.xml`,
  and that file differs only by the exact `V-SNAPSHOT -> V` project-version
  transition;
- E3C2 independently validates the E3B envelope and its specification revision
  equals the current candidate checkout specification revision;
- the audit exactly matches candidate/baseline/version/tag/current specification;
- capability, limitation, and known-blocker review are all explicit `PASS`;
- candidate selection records an explicit user decision while release publication
  remains explicitly unauthorized;
- `vV` is absent both locally and on `origin`; and
- the same ZIP passes the E3C1 release-aware complete B5 gate.

The tag checks are availability guards only. The script contains no tag creation,
GitHub Release creation, asset upload, or push operation.

E3C3 closes generic DIST001-E3 preparation. The first real candidate audit and
candidate execution remain E4 work after an explicit exact baseline/public-version
selection.


## Detached selected-baseline candidate worktree

`DIST001-E4B1` provides the only supported local worktree preparation primitive
for the selected first-pre-release baseline:

```sh
python3 dist/prepare_release_candidate_worktree.py \
    --selection docs/project/DIST001_E4_SELECTION.txt \
    --destination /absolute/path/outside/the/main/checkout
```

The selected baseline remains `3c23eaaccecbdcc7c2bcd86bc30c445403cfb047` / `0.2.236-SNAPSHOT` and the derived
public version remains `0.2.236`. The helper validates the complete E4A
selection record, requires the selected commit to remain an ancestor of
`origin/main`, verifies the baseline POM version, refuses an existing or in-repo
destination, and creates the checkout only with `git worktree add --detach`.

After creation it proves that the new worktree is clean, detached, at the exact
selected baseline, still reports `0.2.236-SNAPSHOT`, and did not create or move any
local branch ref. If a post-creation invariant fails, it removes only the
worktree that the current invocation created before failing.

E4B1 itself does not create the real release candidate worktree. It publishes
and tests this primitive with isolated temporary Git fixtures. E4B2 owns the
first candidate-specific use plus the exact `0.2.236-SNAPSHOT -> 0.2.236`
POM transition.

The helper never creates or pushes a branch, creates a tag, changes the selected
baseline, authorizes publication, or publishes a GitHub Release/asset.


## Exact candidate POM version transition

`DIST001-E4B2` owns only the mechanical project-version mutation after E4B1 has
created an exact clean detached worktree at the selected baseline.

Generic use:

```sh
python3 dist/transition_release_candidate_version.py     --selection docs/project/DIST001_E4_SELECTION.txt     --candidate /path/to/detached-candidate-worktree
```

The helper requires the candidate to remain a registered detached worktree whose
`HEAD` is the exact selected baseline and whose worktree is clean. It then
changes only the root Protos Maven project version from the selected
`V-SNAPSHOT` to public `V`.

The resulting `pom.xml` must be byte-for-byte equal to the selected baseline
`pom.xml` except for that one exact project-version token. `git diff --name-only`
must contain only `pom.xml`, nothing is staged, `HEAD` remains the selected
baseline, and detached state is preserved.

E4B2 does not create the real candidate worktree during publication, does not
stage or commit the transition, and does not create a branch, tag, remote
candidate ref, GitHub Release, or release asset. E4B3 owns the first candidate
commit.


## Detached candidate commit primitive

`DIST001-E4B3A` publishes and tests the commit primitive that will later be used
by E4B3B to materialize the real selected candidate.

Generic use after E4B1 + E4B2:

```sh
python3 dist/commit_release_candidate.py     --selection docs/project/DIST001_E4_SELECTION.txt     --candidate /path/to/detached-candidate-worktree
```

The helper requires exactly one unstaged `pom.xml` change whose bytes equal the
E4B2 `V-SNAPSHOT -> V` transition. It rejects extra dirty/untracked paths,
pre-staged input, attached HEAD, the wrong baseline, or altered POM bytes.

Before staging it verifies local Git author/committer identity. It stages only
`pom.xml`, suppresses local hooks and commit signing for this mechanical
candidate commit, commits with the deterministic message
`release: materialize Protos V candidate`, then verifies:

- the new detached HEAD has the selected baseline as its single parent;
- baseline -> candidate changes only `pom.xml`;
- committed POM bytes equal the exact E4B2 transition;
- the worktree is clean;
- HEAD remains detached; and
- local branch and tag refs are unchanged.

The primitive performs no push, branch creation, tag creation, GitHub Release,
or release-asset publication. E4B3A itself uses only isolated fixture candidates;
E4B3B owns the first real candidate commit and exact SHA capture.


## Candidate materialization composition and recovery

`DIST001-E4B3B1` composes the already-published E4B1, E4B2 and E4B3A
mechanisms into one fail-closed local candidate materializer:

```sh
python3 dist/materialize_release_candidate.py     --selection docs/project/DIST001_E4_SELECTION.txt     --candidate /path/to/detached-candidate-worktree
```

The helper still requires the E4A selection record to say
`candidate_source_revision=UNMATERIALIZED` and
`release_publication_authorized=false`. It does not mutate that record itself.

A fresh invocation creates the exact detached baseline worktree, applies the
exact root `V-SNAPSHOT -> V` POM transition and creates the guarded detached
candidate commit. Recovery is explicit and bounded:

- an exact clean B1 baseline worktree resumes through B2+B3A;
- an exact dirty-but-unstaged B2 POM transition resumes through B3A;
- an already-created exact candidate commit is verified and reused unchanged;
- any other dirty state, existing non-worktree path, wrong lineage/version,
  attached HEAD, or local branch/tag ref to the candidate fails closed.

If this invocation created only an incomplete baseline/dirty preparation
worktree and then fails, it removes that worktree. Once a candidate commit
exists, it intentionally leaves the detached worktree registered so the commit
remains reachable and can be verified/reused by later E4 slices.

The helper creates no branch or tag, performs no push, and does not create a
GitHub Release or release asset. E4B3B1 publishes/tests this composition only;
E4B3B2 owns its first use against the real frozen `0.2.236` selection and the
subsequent persistence of that exact candidate SHA.


## Independent persisted-candidate lineage verification

`DIST001-E4B4` independently verifies the persisted candidate identity after
E4B3B2. It does not call the B1/B2/B3A/B3B1 materialization helpers.

```sh
python3 dist/verify_release_candidate_lineage.py     --selection docs/project/DIST001_E4_SELECTION.txt
```

For the frozen candidate `957b1e16793a682de1d6406e37b5734c44d32d19`, the verifier reconstructs the proof from
Git objects and the persisted selection: one parent exactly `3c23eaaccecbdcc7c2bcd86bc30c445403cfb047`, only
`pom.xml` modified, and byte-exact root project-version transition
`0.2.236-SNAPSHOT -> 0.2.236`. It discovers exactly one registered worktree at the
candidate SHA, requires clean detached state, rejects any local branch/tag or
remote-tracking ref to the candidate, requires future tag `v0.2.236` to remain
absent locally and on origin, and requires release-publication authorization and
publication-state flags to remain false.

E4B4 closes detached candidate materialization only. E4C owns archive, release
notes, envelope and audit preparation. No tag, GitHub Release or release asset is
created here.


## Persisted candidate archive identity verifier

`DIST001-E4C2` adds an independent verifier for the exact archive persisted by
E4C1. It does not rebuild or mutate the archive:

```sh
python3 dist/verify_candidate_archive_identity.py   --repository-root .   --selection docs/project/DIST001_E4_SELECTION.txt   --artifact-record docs/project/DIST001_E4_CANDIDATE_ARTIFACT.txt   --expect-verification-state true
```

For `protos-0.2.236-posix-jvm.zip` / `b1a58ba445d082156bd4eb637ee6df70c046abdee600d468c0fac29be065e296`, the verifier requires the exact external
SHA-256, one `protos-0.2.236/` root, valid ZIP CRC, exact public-prerelease
`SOURCE.txt`, exact GraalVM/JDK/Truffle `RUNTIME.txt`, complete/value-correct
internal `SHA256SUMS`, and `lib/protos.jar` manifest
`Implementation-Version: 0.2.236`. It locates the candidate worktree by exact
persisted SHA rather than by a hardcoded path.

The expected verification-state argument makes the same verifier usable both
before closure (`false`) and after closure (`true`). It never regenerates the
archive and performs no tag, GitHub Release, envelope, audit, or asset
publication.

## DIST001 boundary

DIST001-A proves that the distribution can be constructed and that its archive
contains the required relocatable toolchain assets and exact source/runtime
metadata.

DIST001-B separately executes the extracted archive outside the repository
checkout and proves CWD-sensitive behavior plus required bundled facilities.
Until DIST001-B closes, this archive must not be described as a validated public
release.
