# PLAT012 — Verified external package custody and source-resolution architecture

Status: **RATIFIED**

Nature: durable non-normative package/runtime host architecture decision

Approved by project owner: **2026-09-09**

GitHub Issue: **#248**

Primary consumer: `TOOL001-F2E4` / GitHub #92

Normative effect: **none**. D053 remains authoritative for
PackageExecutionPlanV2 and exact immutable package/module identity. PLAT012
selects only host ownership and source-reading architecture.

## Identifier reconciliation

This checkpoint was initially opened as PLAT010 while another concurrent
workstream was publishing Actor-carrier architecture. Before this package
architecture was ratified or implemented, `main` durably assigned PLAT010 and
PLAT011 to that unrelated runtime work. The package checkpoint is therefore
reallocated to the next free identifier, PLAT012. No package-custody decision
was ever published as PLAT010.

## Selected architecture — A+

Select a **run-owned exact immutable package index plus a lazy host-neutral
verified immutable-resource reader**.

```text
PackageExecutionPlanV2 exact external identities
        |
        v
run-owned package-resource scope
        |
        +-- ExactExternalPackageIdentity -> verified custody / reader lease
        |
        v
canonical ExternalModuleKey
        |
        v
package-relative immutable resource key
        |
        v
verified immutable bytes -> strict UTF-8
        |
        v
ProtosModuleSource.fromCharacters(...)
```

Exact external identity remains:

```text
registry = PackageId + exact ReleaseVersion + ContentIdentity
Git      = PackageId + exact revision       + ContentIdentity
```

Canonical external ModuleKey is exactly that immutable package instance plus
its internal logical module. Alias, registry locator, Git URL, mirror, cache or
store path, Filesystem, custody object, reader object, Process, Actor and
Activation are excluded from identity.

## Durable constraints

1. A run/session resource owner contains exactly one authority entry per exact
   external package admitted from detached V2 data.
2. Detached external-package identities and supplied verified custodies are
   reconciled 1:1 before application authority begins; missing, extra,
   duplicate or mismatched entries fail closed.
3. Logical authority is keyed by complete exact package identity, never only
   PackageId, ContentIdentity, alias, URL or physical path.
4. Equal ContentIdentity may be physically deduplicated below this layer without
   collapsing distinct package identities.
5. ModuleKey encoding is canonical: exact package identity + internal logical
   module only.
6. Logical-module to package-relative resource conversion is confined and
   canonical; traversal and alternative physical-path spellings are rejected.
7. Source reading is lazy: only requested modules are read/decoded.
8. Verified custody exposes a narrow host-only resource-read projection that
   does not require guest Filesystem, Activation, Process or Actor machinery.
9. Immutable reads support concurrent eligible callers without a mandatory
   global mutable cursor or global serialization lock.
10. The run resource owner retains custody for as long as future loads may occur
    and deterministically releases it at run teardown. F2E5 owns final public-run
    lifecycle placement.
11. Resolver/source caches borrow from that owner and do not redefine lifetime.
12. Cache policy, including single-flight and eviction, is an optimization, not
    identity or semantic authority.
13. The original selected/store Path is never reopened after F2E2 verification.
14. Normal source resolution performs no solving, registry lookup, Git fetch,
    lock mutation or acquisition.
15. Backing may evolve from captured NIO to memory, mmap, local/shared CAS,
    deduplicated blobs, brokered readers or distributed/remote CAS leases without
    changing plan or ModuleKey semantics.

## Cross-runtime / ecosystem review

The approved review covered Java ClassLoader and JPMS ModuleReader, .NET
AssemblyLoadContext, Python importlib, Lua searchers, Erlang/BEAM, Node/Deno,
Ruby, OCaml, GHC package/unit identity, Cargo, Go modules, Bazel/Bzlmod, Gradle,
npm/pnpm, Nix, SwiftPM and Mix/Hex.

The strongest reusable pattern separates:

```text
canonical identity
    != acquisition/provenance/backing
    != scoped reader/loader
    != cache policy
```

JPMS ModuleReader is a close analogue: a scoped reader obtains resources by
abstract names without requiring a physical source path as module identity.
Python/Lua/BEAM independently show that loaders may produce source/code from an
arbitrary backing. GHC and Bazel reinforce exact package-instance/canonical
identity. Cargo/Go/Deno/Gradle/pnpm reinforce integrity plus physical cache/store
separation.

Node/Ruby URL/path-coupled loading and Nix store-path identity remain useful
counterexamples: those ecosystems are coherent, but importing their physical
locator into Protos ModuleKey would violate D053 relocation/mirror/store
independence.

## Scalability

For `P` external packages, `E` edges, `U` unique loaded modules and source size
`S`:

```text
bind/reconcile:         O(P + E)
run authority:          O(P)
package lookup:         O(1) expected
first module load:      O(1) + O(S)
optional lazy cache:    O(U + used-source-bytes)
```

The design avoids startup/memory proportional to all package source bytes.
Multiple versions/revisions of one PackageId coexist naturally. Many aliases to
one exact package share one custody entry. Equal content can deduplicate in a
CAS without merging package identities. Many Actors can share immutable source
backing while retaining Actor-local module instances. Distributed workers can
reconstruct the same exact identity -> local verified custody association from
an inert plan plus verified artifacts.

## Rejected alternatives

- **Persistent loader Process/Activation:** unnecessary guest institution and
  lifecycle/coordination cost for immutable bytes.
- **Eager all-source snapshot:** startup and memory scale with unused sources.
- **Process/Activation per load:** unacceptable per-import overhead.
- **Add Activation/Context to generic ModuleResolver:** makes unrelated resolvers
  pay for one backend's execution-domain representation.
- **Reopen source/store Paths:** violates F2E2 same-capture and reintroduces
  TOCTOU/physical-layout authority.
- **Global ContentIdentity -> custody registry:** ambient cross-run authority and
  incorrect risk of conflating storage identity with package identity.
- **ModuleKey -> custody for every possible module:** eager enumeration or
  package authority duplicated per module.

## Why this is the most Protos architecture

A+ preserves a small universe, prefers a narrow mechanism over a privileged
loader institution, keeps identity/backing/authority/cache distinct, fails at
exact invariant boundaries, pays only for modules used, keeps authority
run-scoped instead of global, and allows NIO/CAS/distributed implementations to
compose behind one semantically invisible boundary.

## TOOL001-F2E4 consequence

PLAT012 ratification clears the architecture gate. F2E4 may now mechanically
implement V2 defensive detach, exact external package host identities, canonical
ModuleKeys, run-owned exact package-resource scope, 1:1 custody reconciliation
and lazy source resolution via `ProtosModuleSource.fromCharacters`.

F2E4 must not add public `protos run` lifecycle integration, fresh acquisition,
networking, lock mutation or new package semantics. F2E5 remains responsible for
public run integration and final F2 closure.

## Deliberately deferred

PLAT012 does not select exact Java collections, cache algorithm/size, precise
reader method names, mmap/CAS/broker transport, remote staging/prefetch,
distributed acquisition, public-run teardown placement, debug/source-map policy,
binary/native resource policy or new package source kinds.
