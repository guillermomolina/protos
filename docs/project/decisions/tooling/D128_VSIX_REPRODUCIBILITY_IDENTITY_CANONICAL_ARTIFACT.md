# D128 — VSIX reproducibility identity and canonical artifact contract

Status: **RATIFIED — Candidate C″ selected**

Allocated: **2026-09-13**

Explicit project-owner approval: **2026-09-13**

Decision issue: GitHub #500

Primary consumer: `LM009-I1-C` / GitHub #474

Depends on: D127 / GitHub #497 — Candidate B′ RATIFIED

Nature: implementation-independent editor packaging, artifact identity,
reproducibility and provenance contract

Normative language effect: **none**.

## Decision boundary

LM009-I1-A and I1-B already established the deterministic dependency graph,
bundled JavaScript client, bounded VSIX payload, package-root APL-1.0 license,
deterministic third-party notices and exact package-content validation.

I1-C must close the durable meaning of:

```text
VSIX_REPRODUCIBLE=YES
```

D128 distinguishes two materially different claims:

1. content reproducibility: the same packaged member paths and uncompressed bytes;
2. artifact reproducibility: the final canonical `.vsix` itself is byte-for-byte
   identical and therefore has the same cryptographic digest.

For Protos, `VSIX_REPRODUCIBLE=YES` means both.

## Current VSIX/tooling facts

- `@vscode/vsce` remains the official VS Code semantic/package-content authority.
- VSIX is a ZIP/OPC package; `[Content_Types].xml`,
  `extension.vsixmanifest` and packaged extension files are semantic members.
- ZIP timestamps, entry order and host attributes are transport representation.
- raw `vsce package` byte identity is not a stable upstream contract because
  archive timestamps vary.
- D127 fixes dependency identity through a committed lockfile, `npm ci` and
  pinned build/package tooling, but fixed inputs alone do not prove final
  artifact byte identity.

D128 must not replace VSCE semantics merely to solve ZIP metadata nondeterminism.

## Exhaustive external implementation/tool audit

The decision audit compared mature build/package systems that already define
reproducibility, hermeticity or package identity.

### Reproducible Builds / SOURCE_DATE_EPOCH

The distribution-neutral Reproducible Builds model standardizes
`SOURCE_DATE_EPOCH` so wall-clock build time can be replaced by an immutable
source/package timestamp.

Transferable lesson: time normalization belongs in the build contract, and source
revision time is preferable to current time.

### Maven

Maven defines reproducible builds around independently recreating specified
artifacts bit-for-bit. `project.build.outputTimestamp` and archive-plugin
timestamp controls normalize output metadata; artifact comparison is a first
class verification concern.

Transferable lesson: final artifact byte identity is a useful release contract,
and independent rebuild evidence is stronger than same-process repetition.

### Gradle

Gradle archive tasks explicitly model reproducible file order, preserved or
normalized timestamps and file/directory permissions.

Transferable lesson: archive ordering, timestamps and permissions are first-class
deterministic inputs, not harmless noise.

### Go

The Go project has independently rebuilt official toolchains byte-for-byte and
removed host/path/time leakage strongly enough to compare distributed binaries
directly.

Transferable lesson: byte-identical independently rebuilt release artifacts are a
strong supply-chain property.

### Nix / NixOS

Nix provides pinned inputs and sandboxed builds, while reproducibility work still
treats timestamps and other leaked state as independent nondeterminism sources.

Transferable lesson: deterministic dependencies and isolation are necessary but
not sufficient.

### Bazel

Bazel hermeticity exists partly so outputs remain stable across distributed CI,
remote execution and caching. Timestamps, absolute paths, undeclared host tools
and nondeterministic actions are explicitly undesirable.

Transferable lesson: exact deterministic outputs scale better than equivalence
rules that ignore hidden environmental inputs.

### SLSA

SLSA distinguishes reproducible from verified reproducible builds and uses the
strong bit-for-bit meaning for reproducibility.

Transferable lesson: payload equivalence alone should not be labeled as the
strongest reproducibility claim.

### Docker / BuildKit

BuildKit supports `SOURCE_DATE_EPOCH`, including deriving it from the Git commit
timestamp, to rewrite timestamp-bearing exported metadata reproducibly.

Transferable lesson: source-derived canonical time is practical even for complex
distribution formats.

### Cargo / Rust packaging

Cargo reproducibility work separately addresses frozen dependency identity and
`.crate` archive determinism, including timestamps and package metadata.

Transferable lesson: lock/dependency determinism and final archive determinism
are separate layers.

### npm

`package-lock.json` plus `npm ci` freezes dependency resolution and installation
without mutating the lockfile.

Transferable lesson: I1-A solved dependency identity; it does not define final
VSIX bytes.

### Debian / Python wheels

Debian reproducible-build work and Python wheel fixes treat ZIP timestamps,
ordering, permissions, locale and timezone as concrete artifact-reproducibility
bugs. Narrow post-processing is accepted when upstream producers cannot yet emit
deterministic archives themselves.

Transferable lesson: transport-only deterministic post-processing is legitimate
when semantic member bytes are preserved exactly.

### JDK / JAR

Modern JDK archive tooling exposes explicit archive timestamps, and reproducible
JAR practice derives them from source/build identity.

Transferable lesson: ZIP-family producers increasingly make transport metadata
explicit.

### Nix VS Code extension ecosystem

Nix VS Code extension packaging uses stable hashes of VSIX artifacts as
operational identity.

Transferable lesson: stable VSIX hashes are useful, but pinning a downloaded
artifact is different from proving source rebuild reproducibility.

### VSCE / yazl / yauzl

VSCE uses mature ZIP-family tooling internally. `yazl` exposes explicit `mtime`,
Unix `mode`, compression choice and entry creation from in-memory bytes.
`compress:false` selects ZIP STORE.

Transferable lesson: Protos can keep canonicalization narrow and use mature ZIP
primitives instead of implementing ZIP or VSIX semantics itself.

## Prior-art fit scores

| Implementation/tool | Aguante de futuro | Escalabilidad | Filosofía Protos | Main lesson |
| --- | ---: | ---: | ---: | --- |
| Reproducible Builds / SOURCE_DATE_EPOCH | 10 | 10 | 10 | source-derived deterministic time |
| Maven reproducible artifacts | 10 | 9.5 | 9.5 | final artifact byte identity + independent compare |
| Gradle reproducible archives | 10 | 10 | 9.5 | normalize time/order/permissions |
| Go verified toolchains | 10 | 10 | 9 | strongest independent byte-for-byte verification |
| Nix / NixOS | 9.5 | 10 | 9.5 | pinned/sandboxed inputs still need output determinism |
| Bazel hermeticity | 10 | 10 | 9.5 | exact outputs scale to remote cache/execution |
| SLSA reproducibility model | 10 | 10 | 10 | reproducible means bit-for-bit |
| Docker / BuildKit | 9.5 | 10 | 9.5 | commit-derived SOURCE_DATE_EPOCH |
| Cargo / Rust packaging | 9 | 9 | 9 | dependency and archive determinism are separate |
| npm lockfile + npm ci | 9 | 10 | 9 | deterministic dependency graph, not final artifact |
| Debian / Python wheel normalization | 9.5 | 9.5 | 10 | narrow archive post-processing is legitimate |
| JDK / JAR timestamp controls | 9 | 9.5 | 9 | explicit deterministic archive timestamps |
| Nix VS Code extension consumption | 8.5 | 9 | 8.5 | stable hashes useful but not source proof |
| raw `vsce package` archive | 5 | 6 | 8 | correct semantic authority; insufficient transport determinism |
| pinned `yazl` STORE canonicalization | 10 | 10 | 10 | exact metadata control without compressor variance |

## Critical refinement from the audit

A generic deterministic `rezip` still leaves a hidden input if it uses ordinary
DEFLATE: compressed bytes may vary with compressor implementation or version
even when all uncompressed member bytes are identical.

D128 therefore does not ratify merely "rewrite timestamps and rezip".

For the small Protos VSIX, compression is not required for operational
scalability. ZIP STORE removes compressor-version variability entirely and makes
the canonical archive identity easier to audit and reproduce independently.

## Candidate comparison

### A — raw `vsce package` byte identity

Two unmodified `vsce package` outputs must have identical SHA-256.

```text
Aguante de futuro: 5/10
Escalabilidad:      6/10
Filosofía Protos:   7/10
```

Rejected because raw byte identity is an unstable upstream implementation
assumption.

### B — content reproducibility only

Two clean builds must contain identical member paths and bytes; the surrounding
ZIP representation may differ.

```text
Aguante de futuro: 8.5/10
Escalabilidad:      8.5/10
Filosofía Protos:   8/10
```

Rejected as the final meaning of `VSIX_REPRODUCIBLE=YES` because artifacts from
the same source could still have different hashes.

### C — deterministic rezip with generic DEFLATE

VSCE supplies semantic members, then timestamps/order/permissions are normalized
and entries recompressed.

```text
Aguante de futuro: 9/10
Escalabilidad:      9/10
Filosofía Protos:   9/10
```

Rejected because compressor implementation/version remains unnecessary input.

### C′ — source-derived canonical archive with compressor/toolchain pinned

Canonicalize archive metadata and explicitly pin compression implementation.

```text
Aguante de futuro: 9.5/10
Escalabilidad:      9.5/10
Filosofía Protos:   9.5/10
```

Strong, but still couples artifact identity to compressor implementation.

### C″ — dual proof + source-derived canonical STORE VSIX — SELECTED

Keep VSCE as the only semantic/package-content authority and add a narrow
canonical transport layer.

```text
immutable source commit + package-lock + pinned build tooling
        |
        v
npm ci / esbuild
        |
        v
vsce package --no-dependencies
        |        # ONLY semantic VSIX/package authority
        v
raw VSIX
        |
        +-- validate I1-B exact paths/content
        v
canonicalizer
        +-- read every raw member as bytes
        +-- preserve every member path and byte sequence
        +-- reject duplicate/unsafe/unexpected paths
        +-- deterministic lexicographic member order
        +-- timestamp = SOURCE_DATE_EPOCH from Git commit time
        +-- ZIP-range clamp only when required by the format
        +-- deterministic fixed regular-file mode/attributes
        +-- no archive comment or host metadata
        +-- ZIP STORE, no DEFLATE compressor variability
        +-- direct exact ZIP reader/writer build dependencies
        v
canonical VSIX
```

Two independent clean worktrees of the same immutable source commit must prove:

```text
VSIX_CONTENT_REPRODUCIBLE=YES
VSIX_ARTIFACT_REPRODUCIBLE=YES
VSIX_CANONICAL_TIMESTAMP_SOURCE=SOURCE_DATE_EPOCH_FROM_GIT_COMMIT
VSIX_CANONICAL_COMPRESSION=STORE
VSIX_CANONICAL_ORDER=LEXICOGRAPHIC
VSIX_CANONICAL_MODE=FIXED
VSIX_SEMANTIC_MEMBER_BYTES_CHANGED=NO
```

Required evidence:

1. exact member path set equal across both builds;
2. SHA-256 of every uncompressed member equal across both builds;
3. raw VSCE to canonical transformation preserves each path and member bytes;
4. final canonical `.vsix` SHA-256 equal across both builds;
5. canonical artifact still passes the I1-B exact-content validator;
6. canonical artifact remains installable/readable by VS Code tooling;
7. wall-clock time, workspace path, username, hostname, filesystem traversal
   order, umask and timezone do not affect output;
8. raw pre-canonicalization VSCE hash equality is not required.

Direct exact `yauzl`/`yazl` development dependencies are preferred over
accidental transitive use or a hand-written ZIP implementation.

```text
Aguante de futuro: 10/10
Escalabilidad:      10/10
Filosofía Protos:   10/10
```

### D — environment/container-pinned reproducibility only

```text
Aguante de futuro: 6.5/10
Escalabilidad:      7.5/10
Filosofía Protos:   6.5/10
```

Rejected because raw VSCE wall-clock metadata can still vary and independent
verification is weak.

### E — replace VSCE with a Protos-owned VSIX builder

```text
Aguante de futuro: 6/10
Escalabilidad:      6.5/10
Filosofía Protos:   3/10
```

Rejected because it creates a second VSIX semantic authority.

## Selected contract

```text
D128_SELECTED_CANDIDATE=C_DOUBLE_PRIME

VSCE_SEMANTIC_AUTHORITY=YES
PROTOS_VSIX_SEMANTIC_BUILDER=NO

VSIX_CONTENT_REPRODUCIBLE=REQUIRED
VSIX_ARTIFACT_REPRODUCIBLE=REQUIRED

VSIX_CANONICAL_TIMESTAMP_SOURCE=SOURCE_DATE_EPOCH_FROM_GIT_COMMIT
VSIX_CANONICAL_COMPRESSION=STORE
VSIX_CANONICAL_ORDER=LEXICOGRAPHIC
VSIX_CANONICAL_MODE=FIXED
VSIX_CANONICAL_ARCHIVE_COMMENT=NONE

VSIX_SEMANTIC_MEMBER_PATHS_CHANGED=NO
VSIX_SEMANTIC_MEMBER_BYTES_CHANGED=NO

INDEPENDENT_CLEAN_WORKTREE_PROOF=REQUIRED
RAW_VSCE_ARCHIVE_HASH_EQUALITY=NOT_REQUIRED

ZIP_READER_WRITER=PINNED_DIRECT_BUILD_DEPENDENCIES
HOST_TIME_INPUT=FORBIDDEN
HOST_PATH_INPUT=FORBIDDEN
HOST_USER_INPUT=FORBIDDEN
HOSTNAME_INPUT=FORBIDDEN
HOST_UMASK_INPUT=FORBIDDEN
HOST_TIMEZONE_INPUT=FORBIDDEN
```

## Implementation consequence

After this governance record is published, LM009-I1-C is released to implement
the bounded reproducibility proof and close I1 only if every selected invariant
is demonstrated.

D128 does not alter Protos language semantics, Java/runtime/native architecture,
D127's external runtime authority, public-registry policy, Maven implementation
version or VS Code extension version.
