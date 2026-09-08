# Filesystem Tree Observation and Captured-Tree Capability

Status: **SELECTED by D046; implementation pending I024**
Nature: non-normative design rationale for the normative `spec/io/FILESYSTEM.md`
Owner: B009 / D046
Primary consumer discovered by: TOOL001-F2E2
Date: 2026-09-08

## Problem

The closed `protos-package-tree-v1` ContentIdentity contract requires a verifier
to observe exact child names, regular/directory/link/special entry distinction,
regular-file bytes, and directory structure, then execute only the content that
was actually verified.

Before D046, Core Filesystem exposed `open`, `replace`, and `remove`. Library or
bundled-tool code could not portably enumerate a directory or determine that an
entry was a symbolic/reparse indirection without following it.

Using Java/NIO directly inside Package Tool would solve one caller while creating
a package-specific privileged filesystem universe.

## Prior-art audit

### POSIX

`readdir` provides stored directory names but explicitly permits concurrent
directory mutation while iteration proceeds. The `lstat`/`fstatat` family shows
why final-entry no-follow classification is distinct from opening/following.

Useful:
- exact stored child names are a natural observation primitive;
- no-follow classification is separate from traversal.

Rejected:
- process-global filesystem authority;
- a broad inode/mode/uid/timestamp stat record;
- probe-then-act recipes as the portable security boundary.

### Java NIO

`DirectoryStream` and `Files.readAttributes(..., NOFOLLOW_LINKS)` show that hosts
can expose directory iteration plus final-link classification. Java also makes
clear that weakly consistent walking/attribute atomicity is provider-dependent.

Useful:
- secure directory-relative handles and NOFOLLOW selection are viable backend
  mechanisms.

Rejected:
- Java Path/default-filesystem/provider behavior as Protos semantics.

### WASI

WASI reinforces capability-rooted filesystem observation. Current WASI exposes
directory-entry streams through explicitly granted filesystem descriptors rather
than ambient host paths.

Useful:
- directory observation belongs on an authority-bearing capability;
- async/streaming implementation does not require ambient authority.

Rejected:
- copying WASI descriptor/errno vocabulary into Protos.

## Alternatives

### General `stat` + `list`

Rejected for D046. It would force unrelated decisions about timestamps, owners,
permissions, sizes, file IDs and platform metadata.

### Package-only Java tree walker

Rejected architecturally. Package Tool must consume general capabilities.

### New Directory/DirectoryEntry prototype family

Deferred. It can model stable handles elegantly but introduces new public value
families and lifetime/transfer rules not required for v0.1.

### Atomic point-in-time recursive source snapshot

Rejected as a portability requirement. Many backends cannot provide it cheaply.

### `entries` + `captureTree`

Selected.

`entries` is the cheap direct observation mechanism. `captureTree` pays the
stronger capture cost only when requested and returns a stable immutable
Filesystem suitable for verify-then-use.

## Selected model

```text
Filesystem.entries(path)
    -> Future<Array<ordinary frozen {name, kind}>>

Filesystem.captureTree(path)
    -> Future<Filesystem>
```

Kinds are exact Strings:

```text
regular
directory
link
other
```

Descriptors are inert data and carry no authority.

The source-side `entries` result is race-aware rather than transactional.

`captureTree` recursively binds exact children and regular resources without
following captured child links, then returns a fresh immutable read-only
Filesystem containing the captured logical tree.

## Why a non-transactional source capture is sufficient

D046 does not promise:

```text
captured tree == source tree at timestamp T
```

For a verify-then-use consumer the relevant invariant is:

```text
captureTree(source) -> immutable C
hash(C) == expected identity
use(C)
```

If C matches the expected cryptographic ContentIdentity, C is the expected
logical payload. The caller then uses C itself and never reopens mutable source
Paths.

This removes check/use TOCTOU without requiring a universal filesystem snapshot
primitive.

## Authority properties

The returned Filesystem:
- is a fresh capability;
- is rooted at the captured directory;
- is permanently read-only;
- exposes no source host path/handle;
- follows no captured child links during capture;
- retains only captured namespace/data.

## Pay only for what is used

Ordinary open/read/write/replace/remove does not enumerate or capture a tree.
Programs pay capture cost only when invoking `captureTree`.

Backends may implement capture through immutable object stores, copy-on-write,
secure directory-relative copying, in-memory trees, remote immutable versions, or
another mechanism satisfying D046.

## Implementation boundary

D046 is specification only.

I024 should be freshly audited and decomposed. Expected independent boundaries
are host-neutral entries/capture lifecycle and representation, public Filesystem
protocol exposure, secure NIO captured-tree backend, and final Protos integrated
conformance/B009 closure.

Those are implementation planning hints, not normative machinery.
