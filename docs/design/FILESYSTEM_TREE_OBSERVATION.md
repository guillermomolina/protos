# Filesystem Tree Observation and Captured-Tree Capability

Status: **RATIFIED by explicit user re-evaluation; D046 amended by specification revision 0.1.384; implementation pending I024**
Nature: non-normative design rationale for the normative `spec/io/FILESYSTEM.md`
Owner: B009 / D046
Primary consumer discovered by: TOOL001-F2E2
Original selection: 2026-09-08 / specification revision 0.1.383
Explicit re-evaluation approval: 2026-09-08 / specification revision 0.1.384

## Purpose of this record

D046 was originally selected before the project adopted the explicit user
design-approval gate. The project owner subsequently reopened the decision and
required a focused re-evaluation before I024 could continue.

The re-evaluation deliberately covered only three choices that were materially
under-justified:

1. eager `entries -> Future<Array>` versus incremental enumeration;
2. a public `Directory` capability versus reusing `Filesystem` plus
   `captureTree`;
3. programmer-visible lifetime/release semantics of the captured Filesystem.

The project owner explicitly approved the final recommendations after the
comparative/scalability review. Other D046 semantics were not reopened by that
approval and remain as already specified.

## Problem that D046 solves

The closed `protos-package-tree-v1` ContentIdentity contract requires a verifier
to observe exact child names, regular/directory/link/special entry distinction,
regular-file bytes, and directory structure, then execute only the content that
was actually verified.

Before D046, Core Filesystem exposed `open`, `replace`, and `remove`. Library or
bundled-tool code could not portably enumerate a directory or determine that an
entry was a symbolic/reparse indirection without following it.

Using Java/NIO directly inside Package Tool would solve one caller while creating
a package-specific privileged filesystem universe.

The security/integrity requirement is not merely secure traversal. The required
shape is:

```text
mutable / untrusted source authority
            |
            v
finite immutable captured authority C
            |
            +--> verify ContentIdentity(C)
            |
            +--> consume exactly C
```

A stable live directory handle alone does not solve the final verify-then-use
problem when source bytes remain mutable.

## Focused prior-art re-evaluation

### POSIX and C/C++ directory iteration

POSIX `readdir` and C++ `directory_iterator` expose incremental observation of a
live directory. They demonstrate bounded-memory traversal but do not provide a
portable point-in-time directory transaction. Host mutation may affect what is
observed, and host directory/cursor resources are implementation-visible in the
API family.

Primary references:
- https://pubs.opengroup.org/onlinepubs/9799919799/functions/readdir.html
- https://en.cppreference.com/w/cpp/filesystem/directory_iterator.html

Lesson for Protos: incremental traversal is valuable at extreme fan-out, but it
comes with state/cursor/lifetime semantics that are not free.

### Java NIO

`DirectoryStream` is incremental and `Closeable`. `SecureDirectoryStream`
provides security-sensitive operations relative to an open directory so later
renames/replacements of the reaching path need not retarget operations.

Primary references:
- https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/nio/file/DirectoryStream.html
- https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/nio/file/SecureDirectoryStream.html

Lesson for Protos: secure open-directory handles are excellent backend machinery
for a confined Filesystem. Exposing them as a public value would also expose a
real lifecycle and a second namespace-authority identity.

### Rust std and cap-std

Rust `std::fs::read_dir` is iterator-based. `cap-std::fs::Dir` is the more
important authority precedent: operations are relative to an explicit directory
capability rather than assuming ambient "the filesystem" authority.

Primary references:
- https://doc.rust-lang.org/std/fs/fn.read_dir.html
- https://docs.rs/cap-std/latest/cap_std/fs/struct.Dir.html

Lesson for Protos: cap-std strongly validates explicit namespace capability.
However, Protos `Filesystem` already occupies that semantic role, so importing a
second public `Directory` authority would duplicate a boundary unless a distinct
future responsibility earns it.

### Go `io/fs` and `os.Root`

Go demonstrates that eager and incremental APIs can coexist. `fs.ReadDir`
returns a complete list, while `ReadDirFile.ReadDir(n)` supports bounded
incremental reads. `os.Root` represents authority confined to one filesystem
subtree and normally uses an OS directory handle internally.

Primary references:
- https://pkg.go.dev/io/fs
- https://go.dev/blog/osroot
- https://pkg.go.dev/os#Root

Lesson for Protos: choosing an eager v0.1 convenience operation does not prevent
a later scalable incremental facility. `os.Root` also reinforces that Protos can
keep host directory handles behind the already-existing `Filesystem` authority
boundary.

### .NET

.NET explicitly offers both families: `GetFileSystemEntries` materializes, while
`EnumerateFileSystemEntries` is lazy and intended for large result sets.

Primary references:
- https://learn.microsoft.com/dotnet/api/system.io.directory.getfilesystementries
- https://learn.microsoft.com/dotnet/api/system.io.directory.enumeratefilesystementries

Lesson for Protos: one simple complete-result operation can remain stable even if
an independently designed incremental surface is added later.

### Swift/Foundation

Foundation separates complete shallow directory listing from
`DirectoryEnumerator` traversal.

Primary references:
- https://developer.apple.com/documentation/foundation/filemanager/contentsofdirectory(atpath:)
- https://developer.apple.com/documentation/foundation/filemanager/directoryenumerator

Lesson for Protos: eager listing and scalable traversal solve different usage
levels and need not be forced into one abstraction from the start.

### Python and Node.js

Python `os.scandir()` and Node `fs.Dir`/`opendir` expose incremental traversal
and explicit resource-management concerns. Node additionally exposes buffering
as an operational tuning dimension.

Primary references:
- https://docs.python.org/3/library/os.html#os.scandir
- https://nodejs.org/api/fs.html#class-fsdir

Lesson for Protos: streaming avoids whole-list materialization but necessarily
creates a live stateful operation whose close/error/progress behavior must be
specified.

### WASI filesystem

WASI uses explicit filesystem descriptors/capabilities and has evolved directory
iteration toward streaming forms.

Primary reference:
- https://github.com/WebAssembly/wasi-filesystem

Lesson for Protos: capability-rooted observation is portable architecture;
streaming remains separable from the authority model itself.

### OCaml Eio

Eio represents filesystem locations relative to explicit directory authority and
can derive narrower subtree authority. Live resources are attached to explicit
structured lifetime management.

Primary reference:
- https://ocaml-multicore.github.io/eio/eio/Eio/Path/index.html

Lesson for Protos: restricted subtree authority is useful, but importing a live
directory-resource lifetime solely for I024 would create a new lifecycle category
where Protos already has a Filesystem capability.

### Pony object capabilities

Pony's object-capability model reinforces the principle that external authority
should travel through explicit capabilities rather than ambient path-like data.

Primary reference:
- https://tutorial.ponylang.io/object-capabilities/object-capabilities.html

Lesson for Protos: keep authority in capability objects. Protos already does that
with Filesystem; Path remains authority-free.

### Git, Nix, and Bazel CAS/remote execution

Git trees/blobs, Nix immutable store objects, and Bazel content-addressed input
roots all separate mutable acquisition/materialization from immutable material
that is identified and later consumed.

Primary references:
- https://git-scm.com/book/en/v2/Git-Internals-Git-Objects
- https://nix.dev/manual/nix/latest/store/
- https://github.com/bazelbuild/remote-apis/blob/main/build/bazel/remote/execution/v2/remote_execution.proto

Lesson for Protos: the logical captured tree can be stable and immutable without
requiring its public object to expose the lifetime of whichever backing strategy
an implementation uses.

## Approved decision 1 — eager `entries` in Core v0.1

The approved v0.1 surface remains:

```text
Filesystem.entries(path)
    -> Future<Array<ordinary frozen {name, kind}>>
```

### Why this is acceptable at scale

The direct result materialization cost is:

```text
memory ~= O(number of direct children of the selected directory)
```

A recursive consumer need not retain every directory's Array simultaneously, so
normal tree traversal can remain approximately bounded by maximum directory
fan-out plus the consumer's own retained state rather than total tree size.

The real scaling cliff is a single directory with very high fan-out or a remote
backend where time-to-first-entry/backpressure dominates. That limitation is
real and is deliberately accepted for v0.1 rather than hidden.

### Rejected alternatives for v0.1

A filesystem-specific iterator/stream would introduce cursor identity, partial
failure, concurrent `next`, cancellation, backpressure and deterministic release
semantics before Protos has evidence for a general incremental-sequence
mechanism.

A callback operation such as conceptual `eachEntry(closure)` appears to reuse
existing Closure machinery, but actually creates a larger interaction matrix: it
must define arbitrary user-code Error/non-local-return/suspension/cancellation,
re-entrancy and namespace mutation while a live enumeration is held.

Pagination/cursor tokens introduce similar mutation/version/lifetime questions
without providing a more coherent Protos mechanism.

### Future viability

The eager operation does not reserve the future design space. If independent
real workloads justify incremental directory consumption, Protos may add a
separate bounded/incremental capability designed under the then-current general
I/O/sequence mechanisms. It may coexist with `entries`; v0.1 does not standardize
its name or semantics now.

This follows **pay as you grow** and **generality must be earned**.

## Approved decision 2 — reuse `Filesystem`; no public `Directory`

The approved surface remains:

```text
Filesystem.captureTree(path)
    -> Future<Filesystem>
```

No standard `Directory` or `DirectoryEntry` family is introduced.

### Why this is the Protos boundary

Protos already defines:

```text
Path       = structural value, no authority
Filesystem = authority over a namespace view
File       = acquired resource capability
```

A `Directory` capability whose main distinguishing property were "authority over
a namespace subtree" would substantially overlap `Filesystem`. Secure directory
handles from Java, cap-std, Go, WASI or Eio remain valuable implementation
strategies for realizing a confined Filesystem without becoming a second public
universe.

A new Directory abstraction may still be justified in the future if a genuinely
distinct public lifecycle/identity/responsibility appears that cannot be modeled
as a narrower Filesystem. The present motivating case does not earn that 0-to-1
category.

### Why `captureTree` is still needed

Secure live subtree authority solves confinement and stable relative traversal,
but not the package verify-then-use requirement while underlying bytes remain
mutable. The required operation derives an immutable authority/result and then
consumers verify and use that same authority.

Returning another Filesystem preserves the existing namespace model while
reducing authority and mutability:

```text
mutable Filesystem M
        |
        | captureTree(path)
        v
immutable read-only Filesystem C
```

This follows **scale by composition, not by changing universes**.

### Future subtree delegation

If future workloads require delegating live mutable authority over a subtree,
the first design direction to evaluate is deriving/restricting
`Filesystem -> Filesystem` rather than automatically adding `Directory`. This is
a future design question, not part of D046.

## Approved decision 3 — captured Filesystem has no caller-managed release

The approved semantic distinction is:

```text
live traversal/acquisition machinery
        !=
completed immutable captured authority
```

A successful captured Filesystem is not made `Closable` merely because one
implementation may use directory handles, temporary storage, CAS pins, mapped
objects, copy-on-write state, remote immutable versions, or another resourceful
backing strategy.

The caller does not acquire a hidden obligation to call `close()`/`release()` or
to retain a source handle for the returned Filesystem to remain valid.

### Implementation freedom and scalability

The runtime/backend may represent the immutable capture using:

- an in-memory immutable tree;
- a content-addressed store;
- shared/deduplicated immutable blobs;
- copy-on-write or versioned filesystem state;
- managed temporary backing;
- a remote immutable object/version;
- another representation preserving the normative logical tree.

The Filesystem capability may therefore remain small even when the represented
tree is very large. Implementations may stream source content into managed
backing during capture rather than retaining the entire tree in heap memory.

Physical backing reclamation remains implementation strategy just as storage,
caching, deduplication and garbage collection generally do. A future need for
deterministic reclamation of large immutable datasets must earn a general
resource/store/lease mechanism rather than leaking one backend's temporary-file
strategy into D046.

Files opened from the captured Filesystem remain ordinary File capabilities and
retain their normal `Closable` lifecycle. The lifecycle clarification applies to
the captured Filesystem itself.

This follows **logical truth vs physical strategy** and **ordinary things should
remain ordinary**.

## Retained D046 semantics outside the focused re-evaluation

The approved re-evaluation does not alter these existing rules:

- entry descriptors are fresh frozen ordinary `{name, kind}` data and carry no
  authority;
- names are exact semantic String spellings and unrepresentable child names fail
  closed rather than being normalized, omitted or replaced;
- kinds remain exactly `regular`, `directory`, `link`, `other`;
- child classification is final-entry no-follow;
- Array order is deliberately unspecified;
- a successful `entries` result has at most one descriptor for each exact child
  name and a backend unable to produce a finite non-duplicated result fails;
- `entries` on a mutable source is observational/non-transactional;
- `captureTree` follows no captured child link;
- link/other entries are retained opaquely rather than importing package policy
  into Core;
- capture is finite and all-or-nothing from the caller's result perspective;
- source mutation may produce a capture whose members did not all coexist at one
  source instant;
- the completed captured Filesystem itself is immutable and is the object that
  verify-then-use code must validate and subsequently consume;
- the captured Filesystem is permanently read-only;
- ordinary Future failure/cancellation and authority-confinement rules continue
  to apply.

## Why non-point-in-time source capture remains sufficient

D046 does not promise:

```text
captured tree == source tree at timestamp T
```

For verify-then-use the relevant invariant is:

```text
captureTree(source) -> immutable C
hash(C) == expected identity
use(C)
```

A concurrent race may cause a capture whose digest is unexpected, which is an
availability/retry outcome. It does not permit unverified source bytes to become
executable when the caller hashes and later consumes the same immutable C.

Requiring a universal atomic recursive source snapshot would either exclude many
backends or make host/storage machinery part of portable semantics without being
required by the integrity invariant.

## Implementation boundary after the re-evaluation

The earlier I024-A publication remains useful implementation evidence but is not
design authority. It was written before this explicit re-evaluation and must be
checked against the amended contract before dependent public work proceeds.

Current planned boundary after specification revision 0.1.384:

```text
I024-A   host-neutral result/custody flow                     CLOSED
I024-A2  post-0.1.384 compatibility re-audit                  READY
I024-B   standard Filesystem public materialization           BLOCKED_BY_DEPENDENCIES
I024-C   secure NIO + immutable captured backend              BLOCKED_BY_DEPENDENCIES
I024-D   integrated Protos conformance + B009 closure         BLOCKED_BY_DEPENDENCIES
```

A2 must verify at least:

- the eager complete-result `List<Entry>` substrate remains consistent with the
  approved v0.1 Array contract;
- duplicate exact-name rejection matches the already-normative successful-result
  uniqueness/fail-closed rule;
- captured-result custody/release callbacks govern only tentative/backend custody
  before result transfer and do not accidentally create a Protos-visible
  `Filesystem.close()` obligation;
- no existing A machinery assumes a public `Directory`/stream identity;
- cancellation/failure cleanup still releases tentative resources without making
  immutable backing strategy observable.

If A satisfies those checks, A2 may close without rewriting A. If not, A2 owns
the minimum implementation correction before B becomes READY.
