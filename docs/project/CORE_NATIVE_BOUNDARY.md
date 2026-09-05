# Core Native Boundary

Status: non-normative implementation architecture inventory.

This document records the Java-backed standard-behavior boundary after I018 Core
self-hosting/bootstrap minimization. It does not define Protos semantics; the
normative owners under `spec/` remain authoritative.

The inventory exists for one reason: a Java-native standard operation must be an
explicitly reviewed boundary, not a convenient place to accumulate ordinary Core
library behavior. If a standard operation can be expressed faithfully as normal
Protos objects and Closures without changing receiver validation, identity,
ordering, failure, suspension, capability, or representation semantics, it
belongs in `protos/lib/core/`.

## Classification

The final I018 audit uses these implementation categories:

- **host-irreducible** — fundamental execution/control machinery needed to run
  ordinary Protos source at all;
- **representation bridge** — behavior whose contract depends on an opaque or
  implementation-selected representation of a semantic Core value;
- **concurrency/runtime bridge** — behavior coupled to Tasks, Futures, Actors,
  isolation, scheduling, suspension, transfer, cancellation, or commitment;
- **resource/capability bridge** — behavior coupled to resource custody,
  capability shape, I/O lifecycle, buffering, or backend effects.

There are no remaining entries classified as source-expressible. I018 already
moved such behavior and standard identities to distributable Core source where
the current language can express them faithfully.

## Audited Java native-Closure providers

The `nativeClosure` count is intentionally a construction-site count rather than
a selector count. Some providers use one audited helper to install several
selectors. `ProtosCoreNativeBoundaryArchitectureTest` complements this table with
runtime selector-surface checks so helper-based expansion cannot silently widen
the standard native boundary.

| Provider | Native Closure sites | Classification | Audited reason for remaining native |
|---|---:|---|---|
| `ProtosStandardObjectProtocol.java` | 2 | host-irreducible | Generic polymorphic `call` performs Closure invocation or ordinary instance construction; `identityHash` exposes semantic identity without dynamic-dispatch substitution. |
| `ProtosStandardBooleanProtocol.java` | 1 | host-irreducible | `ifTrue`/`ifFalse`/`and`/`or` are the primitive selective-control surface used to express branching itself, including path-sensitive callback validation. |
| `ProtosStandardHashSupport.java` | 3 | representation bridge | Object identity hashing and Number/String hashing depend on semantic identity or exact represented values and must not be redefined through overrideable message sends. |
| `ProtosStandardNumberEqualityProtocol.java` | 1 | representation bridge | Exact cross-family Number equality needs Integer/fixed/binary64 representation knowledge, including NaN and exact-integral Float handling. |
| `ProtosStandardNumberOrderingProtocol.java` | 1 | representation bridge | Exact cross-family ordering and unordered NaN behavior require representation-aware comparison. |
| `ProtosStandardIntegerProtocol.java` | 3 | representation bridge | `+`, `-`, `*`, `/`, `div`, and `mod` are exact numeric representation primitives; derived `negated` and `%` are already source-backed. |
| `ProtosStandardFloatProtocol.java` | 1 | representation bridge | Binary64 arithmetic is the primitive represented-value boundary; derived `negated` is already source-backed. |
| `ProtosStandardNumericConversionProtocol.java` | 1 | representation bridge | Numeric factory conversion performs exact family/range/binary64 conversion over host representations. |
| `ProtosStandardStringProtocol.java` | 3 | representation bridge | String size/indexing use required Unicode grapheme segmentation and `+` constructs semantic String representation values. |
| `ProtosStandardArrayProtocol.java` | 5 | representation bridge | Array factory/index mutation/size own indexed representation state; `each` also requires eager callability validation and a start-of-operation snapshot. |
| `ProtosStandardMapProtocol.java` | 7 | representation bridge | Map storage, recorded hashes, reentrancy restrictions, mutation state, lookup equality callbacks, and iteration snapshots are receiver-owned keyed representation semantics. |
| `ProtosStandardIdentityMapProtocol.java` | 7 | representation bridge | IdentityMap storage and lookup require primitive semantic identity/identityHash plus keyed representation state and iteration snapshots. |
| `ProtosStandardBytesProtocol.java` | 7 | representation bridge | Bytes owns octet-indexed mutable state, reservation state, exact octet validation, snapshot iteration, and P-region interaction. Its standard prototype identity is already source-backed and construction-only. |
| `ProtosStandardPathProtocol.java` | 6 | representation bridge | Path construction, components, structural equality, and structural hash operate on the immutable Path representation. |
| `ProtosStandardErrorProtocol.java` | 1 | host-irreducible | `Error.signal` performs the language Error control transfer with exact signaled-object preservation. |
| `ProtosStandardFutureProtocol.java` | 2 | concurrency/runtime bridge | `future`, `value`, `cancel`, `detach`, `then`, and `all` depend on Task ownership, suspension, observation, terminal states, cancellation, and Actor-local execution domains. |
| `ProtosParallelRuntime.java` | 2 | concurrency/runtime bridge | `parallel`, Array parallel operations, Bytes/ByteRegion `parallelRange`, snapshot transfer, reservations, commitment, and bounded host carriers form the P execution substrate. |
| `ProtosStandardActorProtocol.java` | 8 | concurrency/runtime bridge | `spawn`/`current`, ActorRef `send`/`request`/`stop`/`termination`, and SendOperation `cancel`/`retry` cross Actor incarnation, transfer, admission, scheduler, uncertainty, and lifecycle boundaries. All three ordinary prototype identities are source-backed after I018-K. |
| `ProtosStandardImportProtocol.java` | 1 | resource/capability bridge | The source-backed `import` facility's `call` crosses the host resolver, canonical ModuleKey, Actor-local module cache, loading, cycle, and initialization boundary. |
| `ProtosStandardByteIoProtocol.java` | 12 | resource/capability bridge | Byte I/O operations are capability-honest wrappers over ordered flow state, Future commitment, positioning, sizing, truncation, sync, and directional shutdown. |
| `ProtosStandardBufferedByteIoProtocol.java` | 6 | resource/capability bridge | Source-backed factories retain native construction bridges because wrappers attach buffering, ownership, underlying-capability validation, Future, and lifecycle state. |
| `ProtosStandardFileProtocol.java` | 10 | resource/capability bridge | File objects are acquired resource capabilities whose exact local surface depends on backend-provided authority and whose operations own cursor/append/sync/close/commitment state. |
| `ProtosStandardFilesystemProtocol.java` | 1 | resource/capability bridge | Host-provisioned Filesystem authority exposes the standard `open` bridge; its backend owns confined/race-free namespace selection, create/truncate commitment, stable-resource acquisition, cancellation cleanup, and standard File materialization. |

Total audited production construction sites: **91 across 23 providers**.

I018-L closed with the 90-site/22-provider baseline. I016-D1 is an explicitly
reviewed post-I018 resource/capability extension adding exactly one
`Filesystem.open` native-Closure construction site. I016-D3 must re-audit the
complete then-current boundary before I016 final closure.

## Source-backed I018 invariants

I018 specifically prevents the following ordinary derived behavior from
regressing to Java-only implementation:

- `Object.init`, default `Object.==`, and `Object.!=`;
- `Integer.negated` and `Integer.%`;
- `Float.negated`.

It also moved Java-allocated standard identities into Core source for:

- the public `Actor`, `BufferedReader`, `BufferedWriter`, and `import` objects;
- the construction-only standard `Bytes`, `ActorRef`, and `SendOperation`
  prototypes;
- the final frozen prelude bindings object itself.

The remaining primitive/native operations are still installed as ordinary
Closure-valued slots. There is no parallel hidden Java dispatch surface.

## Architectural guard

`ProtosCoreNativeBoundaryArchitectureTest` is the executable I018 closure guard.

It checks:

1. the exact set of production Java files that construct native Closures and the
   exact current construction-site count for every provider;
2. the exact native local-selector surface of statically reachable Core
   prototypes, including helper-installed numeric, Future, parallel, Actor, and
   factory surfaces;
3. the internal Bytes, ActorRef, and SendOperation native surfaces;
4. that migrated Object/Integer/Float behavior still has source definition and
   execution-plan provenance and no native body;
5. that `ProtosCoreBootstrap` retains only its two direct host construction
   contexts and does not reconstruct the prelude slot-by-slot in Java.

A change that intentionally adds or reclassifies a native standard boundary must
therefore update both this inventory and the executable guard in the same
reviewed change. A change that can instead be expressed faithfully in ordinary
Protos must place that behavior under `protos/lib/core/`.
