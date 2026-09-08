# Protos Package ContentIdentity — canonical logical tree v1

Status: **IN_PROGRESS through CLOSED TOOL001-F2E1A**
Nature: non-normative Package Tool/package-artifact design
Owning work: `TOOL001-F2E1`

Related records:
- `docs/design/PACKAGE_IDENTITY_VERSIONING.md`
- `docs/design/PACKAGE_LOCKFILE_FORMAT.md`
- `docs/design/PACKAGE_DISTRIBUTION.md`
- `docs/project/TOOL001_F2E_EXTERNAL_MATERIALIZATION.md`

## Purpose

`ContentIdentity` is the immutable identity of one logical package content tree.
It is deliberately separate from:

```text
PackageId
ReleaseVersion / exact Git revision
PackageLocator / fetch URL
AuthorityIdentity
ArtifactDigest
cache/store path
archive bytes
```

Lock-format 1 already records the conceptual shape:

```text
content protos-package-tree-v1 sha256:<digest>
```

but prior design intentionally did not define what bytes are hashed.

`TOOL001-F2E1` closes that missing contract before any external store path may
become executable source authority.

## Cost-aware F2E1 decomposition

```text
F2E1A  logical-tree domain + portable path/entry-kind contract     CLOSED
F2E1B  canonical byte stream + method/hash contract                READY
F2E1C  independent conformance vectors + F2E1 closure              BLOCKED_BY_DEPENDENCIES
```

E1A answers **what the logical tree is**. It does not answer how that tree is
serialized or which digest algorithm is initially mandatory.

## Prior-art audit

### Git trees

Git tree objects identify file/subtree names together with object IDs and a small
mode domain. Git therefore deliberately retains distinctions such as ordinary
versus executable files and symbolic links.

Useful lesson:
- names and content identity are structural and independent of checkout order.

Rejected for Protos v1:
- executable-bit and symlink semantics are unnecessary host metadata for the
  initial source-oriented Protos package model.

Primary reference:
- https://git-scm.com/book/en/v2/Git-Internals-Git-Objects.html

### Nix NAR

NAR is a canonical serialization of filesystem trees. Directory entries are
ordered; regular file contents, symlink targets and an executable marker are
semantic; most other filesystem metadata is omitted.

Useful lesson:
- canonical identity should specify exactly which filesystem facts count and
  erase all others.

Rejected for Protos v1:
- symlink identity and execute-bit identity would make the package contract
  broader and more host-shaped than current Protos package execution needs.

Primary reference:
- https://nix.dev/manual/nix/2.35/protocols/nix-archive/

### Go Modules

Go's module artifact model is the closest v1 portability precedent:
- module hashes depend deterministically on file names and contents rather than
  ZIP ordering/compression metadata;
- timestamps and modes are ignored;
- empty directories have no extracted identity;
- symbolic links and irregular files are not allowed in module ZIPs;
- path portability and case-fold collisions are validated.

Useful lesson:
- immutable source packages can be defined as portable regular-file trees rather
  than host filesystem images.

Primary references:
- https://go.dev/ref/mod
- https://go.dev/cmd/vendor/golang.org/x/mod/zip/

### Cargo packaging

Cargo demonstrates that **source-checkout membership policy** and the immutable
package payload are separate concerns. Cargo can use manifest include/exclude
rules and VCS ignore information while assembling a distributable payload.

Protos deliberately does not copy Cargo's ambient VCS-sensitive default into
ContentIdentity: user/global Git ignore configuration must not change the
identity of an already-materialized Protos package payload.

Primary references:
- https://doc.rust-lang.org/cargo/reference/manifest.html
- https://doc.rust-lang.org/cargo/commands/cargo-package.html

## F2E1A selected model

### The identity input is a logical payload, not a checkout

The `protos-package-tree-v1` logical package tree is a finite map from canonical relative paths to exact regular-file byte sequences.

Conceptually:

```text
LogicalPackageTreeV1 =
    Map<CanonicalPackagePathV1, Bytes>
```

The map describes one **already-materialized package payload root**.

It is not:
- a Git checkout;
- a registry archive byte stream;
- a package-store metadata directory;
- a build tree;
- a recursive scan of an arbitrary user project chosen by ambient policy.

Source-specific packaging/materialization decides which payload is produced.
ContentIdentity then identifies that payload exactly.

This split prevents one hash method from accumulating Git, registry, archive,
workspace and user-ignore policy.

### Every valid regular file in the payload is included

There is no file-extension allowlist.

If valid regular files exist in the materialized payload, all of them contribute
to ContentIdentity, including:
- `.protos` source;
- exact root `protos.toml`;
- text/binary resources;
- license/readme files;
- test data if the materializer chose to include it;
- `protos.lock` if a future packaging policy intentionally includes it.

The last case does **not** grant that nested/published lock any dependency-graph
authority when the package is consumed. Runtime graph authority still comes from
the consuming root's lock.

Likewise, this rule means E1A does not by itself decide the still-separate
publication question "which resources should `protos package` include?". It only
makes any included regular payload file identity-bearing.

### No implicit ignore policy

The ContentIdentity method does not consult `.gitignore`, `.git/info/exclude`,
global Git configuration, current VCS status, editor settings, environment
variables, build-tool configuration, timestamps or conventional directory names.

Names such as these have no automatic identity exemption:

```text
target
build
dist
.gitignore
.github
tests
docs
```

A materializer/publication operation may have an explicit source-selection
contract, but once a file is present inside the selected payload it is included.

Administrative VCS directories such as a checkout's `.git` must therefore stay
outside the materialized package payload rather than being silently skipped by
the hash algorithm.

### Root manifest requirement

A logical package tree is valid only when its root contains exactly named:

```text
protos.toml
```

as a regular file.

Its exact bytes are identity-bearing like every other file. ContentIdentity does
not canonicalize TOML whitespace/comments or hash a reconstructed ManifestV1
instead of the file bytes.

Files named `protos.toml` below the package root have no special interpretation
for ContentIdentity; they are ordinary included payload files. The package root
is selected explicitly before tree observation and is not rediscovered by
recursive manifest search.

## Entry-kind contract

### Regular files

Regular files are the only identity-bearing leaf entry kind in v1.

For each path, identity uses:
- the exact canonical relative path selected below;
- the exact finite file byte sequence.

Text encoding, newline style and Unicode normalization of file **contents** are
not interpreted. A one-byte content difference is a logical tree difference.

### Directories

Directories are structural containers only.

They are represented by the relative file paths beneath them, not as independent
identity records in E1A's abstract tree.

Consequences:
- an empty directory contributes nothing;
- creating/removing an empty directory does not change ContentIdentity;
- directory mode, timestamp, owner and other metadata do not contribute.

E1B may use explicit framing for serialization, but it must not make an empty
directory semantic after E1A has declared it non-semantic.

### Symbolic links and special entries

Symbolic links, junction/reparse traversal entries, device nodes, FIFOs, sockets and other non-regular/non-directory entry kinds are invalid in `protos-package-tree-v1`.

They are rejected; the verifier must not:
- follow them and hash the target bytes;
- hash a symlink target string;
- flatten them implicitly;
- ignore them silently.

This removes host-dependent traversal and package-root escape ambiguity from the
initial immutable package format.

If a later package model genuinely requires link semantics, that is a new
canonicalization-method decision rather than a silent reinterpretation of v1.

### Hard links

Hard-link/inode identity is not semantic.

If a materialized backend exposes two canonical regular-file paths with equal
bytes that happen to share physical storage, the logical tree contains two
path->bytes entries. Link count, inode/file-key identity and storage deduplication
are excluded metadata.

## Metadata contract

For valid regular files/directories, `protos-package-tree-v1` ignores all
host/storage metadata other than entry kind needed to validate the domain.

In particular identity excludes:
- executable/read/write permission bits;
- owner/group/security principal;
- creation/access/modification/change timestamps;
- ACLs;
- extended attributes;
- filesystem flags;
- inode/file IDs;
- hard-link count;
- sparse allocation/layout;
- compression/deduplication state;
- archive timestamps/modes;
- absolute source/store path.

This is intentional. Initial Protos packages are source/data payloads with no
install hooks or native executable-mode contract.

A future need for executable/native payload metadata must not retroactively make
that metadata part of `protos-package-tree-v1`.

## CanonicalPackagePathV1

### Abstract form

A package file path is relative to the selected package root and consists of one
or more segments joined by literal ASCII `/`.

```text
path = segment *("/" segment)
```

The root directory itself has no path value and is never hashed as machine-local
text.

Backslash is never a separator and is not valid in a segment.

### Segment character domain

For v1, each segment contains only:

```text
ASCII letters: A-Z a-z
ASCII digits:  0-9
punctuation:   . _ -
```

Formally:

```text
segment = 1*( ALPHA / DIGIT / "." / "_" / "-" )
```

This conservative domain is package-artifact policy, not a restriction on
general Protos `String` or `Path`.

It intentionally avoids Unicode normalization, locale collation and
host-filesystem encoding becoming ContentIdentity semantics. A future broader
portable filename domain can use a new canonicalization method after independent
design.

### Segment rejection rules

A segment is invalid when:
- it is exactly `.` or `..`;
- it ends in `.`;
- its ASCII-case-insensitive basename before the first `.` is one of:

```text
CON PRN AUX NUL
COM1 COM2 COM3 COM4 COM5 COM6 COM7 COM8 COM9
LPT1 LPT2 LPT3 LPT4 LPT5 LPT6 LPT7 LPT8 LPT9
```

The reserved-name check reuses the portability direction already established for
runtime logical names while accounting for artifact filename extensions such as
`CON.txt`.

There is no implicit `.`/`..` cleanup, separator conversion, case conversion,
Unicode normalization, drive parsing, URI decoding or percent decoding.

### Exact spelling and case-fold collisions

Canonical identity preserves the exact ASCII spelling of every segment.

However, one valid logical tree must be safely representable without ambiguous
aliasing on common case-insensitive filesystems.

Therefore, within each directory, no two child names may be equal under simple
ASCII case folding (`A-Z` -> `a-z`).

This is an **ASCII-case-fold collision** check only; it is not Unicode case
folding because v1 path segments contain no non-ASCII characters.

Examples rejected in one directory:

```text
Parser.protos
parser.protos

Data
DATA
```

Exact case remains identity-bearing when there is no collision.

### No ambient path limits in ContentIdentity

E1A does not encode a particular host's `NAME_MAX`, `PATH_MAX`, Windows path
length, inode or archive limit into logical identity.

Paths/files are finite. A future transport/store may impose explicit resource
limits and a host may be unable to materialize an otherwise valid logical
package, but such operational limits must not change how another implementation
computes ContentIdentity for the same valid tree.

## Snapshot / mutation boundary

E1A defines the identity of one immutable logical snapshot.

A verifier must not combine observations from different materialized states and
claim they form one ContentIdentity.

If concurrent mutation can occur and the implementation cannot establish or
validate one stable snapshot, verification fails rather than hashing an
implementation-dependent mixture.

F2E2 owns the concrete capability/store mechanism for establishing this invariant
after E1 is fully closed.

## Deliberate non-decisions in E1A

E1A does **not** yet define:
- canonical entry ordering;
- binary/text serialization framing;
- length encoding;
- domain-separation bytes;
- streaming/chunking implementation;
- initial mandatory hash algorithm;
- exact digest encoding beyond the already-selected lock lexical shape;
- fixed conformance digests;
- package archive format;
- source checkout include/exclude rules;
- registry/Git acquisition;
- package-store physical layout.

Those belong to E1B/E1C or later F2E work.

## F2E1B exact next scope

E1B must serialize exactly E1A's finite map and no additional filesystem facts.

It must freeze:
- one total canonical order over canonical path bytes;
- unambiguous framing/domain separation for paths and file contents;
- exact integer/length encoding;
- method identifier `protos-package-tree-v1`;
- initial supported hash algorithm and digest computation;
- algorithm agility without allowing the same method token to mean different
  tree semantics;
- streaming equivalence: a streaming verifier and in-memory reference algorithm
  must hash exactly the same canonical byte stream.

E1B must not reopen E1A's file membership, metadata, symlink or path rules.

## F2E1C closure requirement

E1 cannot close from prose alone.

E1C must publish independent positive and negative vectors sufficient to catch:
- path-order dependence;
- path/content framing collisions;
- empty-file handling;
- empty-directory non-semantics;
- case changes and case-fold collisions;
- reserved/invalid paths;
- symlink/special-entry rejection;
- content mutation;
- manifest-byte mutation;
- metadata-only non-changes;
- same logical tree delivered through different transport/store layouts.

At least one reference vector must carry an exact expected
`protos-package-tree-v1` digest so independent implementations can be checked
without trusting repository production code.

## E1A closure

E1A closes the logical-tree/path domain only.

After publication:

```text
TOOL001-F2E1   IN_PROGRESS
TOOL001-F2E1A  CLOSED
TOOL001-F2E1B  READY
TOOL001-F2E1C  BLOCKED_BY_DEPENDENCIES: TOOL001-F2E1B
TOOL001-F2E2   BLOCKED_BY_DEPENDENCIES: TOOL001-F2E1
```

No Protos language semantics, Core Filesystem surface, native boundary,
implementation version, registry protocol or executable external-node behavior
changes in this slice.
