# Protos Implementation Blockers

This file records implementation work that is blocked by unresolved normative
semantics. It is implementation state, not a normative specification.

Agents must re-check every blocker against the current normative specification
on the current `main` branch. The specification state recorded when a blocker
was created is historical context only.

Blocker states:

- `BLOCKED`: the normative unblock condition is not yet satisfied.
- `READY`: the current normative specification satisfies the unblock condition,
  but the blocked implementation work has not yet been completed.
- `CLOSED`: the dependency was resolved and the corresponding implementation
  work was completed or made obsolete.

Do not mark a blocker `READY` merely because a relevant specification file
changed. Verify the stated unblock condition against the current normative text.

## B001 — Empty Sequence execution

Status: CLOSED

Implementation area:
Truffle lowering / execution of a `CanonicalSequence` containing zero
expressions.

Normative dependency:
The normative execution semantics now state that a semantic `Sequence` containing
zero expressions completes normally with canonical `null`.

Specification authority:
- `spec/semantics/EXECUTION_AND_CONTROL.md`

Unblock condition:
The current normative specifications explicitly and uniquely determine the
result of evaluating an empty `Sequence`.

Current consequence:
Implemented. Empty `CanonicalSequence` lowering/execution completes normally
with the canonical `null` value, matching the normative contract.

Independent work:
Literal/value representation and all other execution work whose observable
semantics are already defined may continue. Internal host representation is an
implementation choice and is not, by itself, a normative blocker.

History:
B001 was temporarily broadened to "Runtime value materialization". That was too
broad: repository policy and the runtime specification explicitly permit
implementation-specific internal representations when observable Protos
semantics are preserved. The blocker is therefore narrowed back to the actual
unresolved observable case: the result of an empty `Sequence`.

## B003 — Delegation parent / lookup chain of canonical Boolean values

Status: CLOSED

Implementation area:
Standard prototype/delegation bridge for the canonical `true` and `false` runtime
representations, including ordinary member lookup and polymorphic invocation
through their delegation chains.

Normative dependency:
D027 closed the portable Core topology. Canonical `true` and canonical `false`
delegate directly to the unique root `Object`; Core v0.1 defines no standard
prelude binding, object, or prototype named `Boolean`.

Specification authority:
- `spec/semantics/OBJECT_MODEL.md`
- `spec/semantics/VALUES_AND_COLLECTIONS.md`

Unblock condition:
The current normative specification explicitly and uniquely determines the
delegation parent / ordinary lookup chain of canonical `true` and `false`,
including whether any standard Boolean prototype object exists.

Current consequence:
Implemented. The runtime value-lookup bridge maps both canonical Boolean host
singletons directly to `Object` for ordinary lookup. Inherited Object behavior
therefore preserves the original canonical Boolean receiver during dispatch,
and polymorphic invocation follows the same ordinary lookup path. No synthetic
or Protos-visible `Boolean` prototype is introduced.

Independent work:
Numeric and other value families whose standard prototype/delegation hierarchy
is already normatively closed may continue independently.

History:
B003 was originally `BLOCKED` while D027 still owned the unresolved parent
topology. Once D027 closed that topology, the blocker became logically `READY`.
This implementation completes the previously excluded Boolean bridge, so the
final transition is `BLOCKED -> READY -> CLOSED`.

## B002 — Delegation parent of `without` / `alias` result objects

Status: CLOSED

Implementation area:
Standard `Object.without(name)` and `Object.alias(sourceName, aliasName)` message
behavior and any runtime helper that constructs their result objects.

Normative dependency:
The normative object model requires both operations to return a new ordinary
object containing copied local-slot bindings, but it does not currently state
what delegation parent that result object has. The delegation parent is
observable through ordinary lookup and therefore cannot be chosen as an
implementation detail.

Specification authority:
- `spec/semantics/OBJECT_MODEL.md`

Unblock condition:
The current normative specification explicitly and uniquely defines the
delegation parent of the ordinary object returned by both `without(name)` and
`alias(sourceName, aliasName)`.

Current consequence:
Implemented. Runtime structural-view helpers now construct both results as fresh
open ordinary objects whose immediate delegation parent is the unique root
`Object`, copy only local bindings shallowly, preserve exact stored values, and
never inherit the receiver's structural state or parent.

Independent work:
Composition conflict validation, local-slot snapshots, atomic contribution
application, parser/canonical AST work, and unrelated execution/runtime work may
continue independently.

## B004 — Public Group/GroupRef acquisition and discovery API

Status: CLOSED

Implementation area:
I011 public ActorGroup acquisition through the exact Core v0.1 surface closed by D039.
Portable service discovery remains explicitly outside Core v0.1 and is not an I011 closure
requirement.

Normative dependency:
Specification revision `0.1.376` / D039 defines exactly
`Actor.group(firstMember, additionalMembers...) -> GroupRef`: one or more explicit ActorRefs,
synchronous fresh Group/GroupRef creation, caller-Process ownership, initial membership only,
communication-only GroupRef authority, and no Core name/identity lookup or discovery registry.
Section 72B explicitly keeps service discovery outside Core v0.1.

Specification authority:
- `spec/concurrency/DISTRIBUTED_RUNTIME.md` §50 Runtime Groups
- `spec/concurrency/DISTRIBUTED_RUNTIME.md` §50A Core ActorGroup Acquisition
- `spec/concurrency/DISTRIBUTED_RUNTIME.md` §72B Service Discovery Implementation Is Not Core Semantics
- `spec/concurrency/ACTORS.md` §8 Core public Actor surface

Unblock condition:
Satisfied by specification revision `0.1.376` / D039. Two independent implementations
can now implement the same portable acquisition selector, argument domain, creation cutover,
ownership/lifetime, result identity, authority boundary, and explicit absence of Core discovery
without choosing new observable semantics.

Current consequence:
Implemented and published by I011-21. The frozen Core `Actor` object exposes exactly
`spawn`, `current`, and `group`; `Actor.group(firstMember, additionalMembers...)` validates the
complete ActorRef vector before cutover, establishes one Process-owned Group with set membership,
and returns one fresh GroupRef acquisition. Owning-Process termination terminates the Group
without stopping members. No public Group object, lookup/reacquisition selector, post-creation
membership/controller API, registry, discovery namespace, endpoint syntax, placement policy, or
transport-selection surface was introduced.

History:
B004 moved `BLOCKED -> READY` when specification revision `0.1.376` / D039 closed the exact
portable acquisition surface and explicitly excluded Core service discovery. I011-21 implements
that surface and its Process-owned lifetime integration, so the blocker is now `CLOSED`.

Independent work:
Optional service discovery, post-creation Group control/membership, desired-cardinality/controller
APIs, durability, explicit Group termination, placement policy, and richer distributed Authority
remain future extension/design work and do not block completion of the now-closed Core v0.1
ActorGroup acquisition requirement.


## B005 — `super` without a physical methodHome

Status: CLOSED

Implementation area:
I020-D execution/failure semantics for a syntactically valid `super.message(...)`
whose current activation has no physical `methodHome`, including execution in an
unbound role or after a semantic boundary that intentionally removes caller
method metadata.

Normative dependency:
Satisfied by specification revision `0.1.377` / D040.
`spec/semantics/EXECUTION_AND_CONTROL.md` §8 defines super validity as a
dynamic invocation property. After the ordinary caller-supplied argument/spread
vector completes, absence of `methodHome` signals one fresh standard
`InvalidSuper` occurrence and performs no lookup. A present `methodHome` with no
delegation parent instead has an empty super lookup search and signals
`SlotNotFound`.

Specification authority:
- `spec/semantics/EXECUTION_AND_CONTROL.md` §8 `super`
- `spec/semantics/ERRORS.md` for `InvalidSuper` parentage and fresh standard
  failure identity
- `spec/PROTOS_GRAMMAR.md` for the unchanged syntactic validity/scope of
  super-message-send
- `spec/semantics/CALLABLES.md` for the ordinary caller-supplied argument vector,
  the single Closure value kind, dynamic method role, and captured method metadata

Unblock condition:
Satisfied by specification revision `0.1.377` / D040. Independent
implementations can determine the exact validity point, argument-before-dispatch
precedence, absence of lookup on the invalid-context path, standard failure
category and freshness, and the distinct `SlotNotFound` result for an empty or
exhausted valid super lookup.

Current consequence:
Implemented by I020-D. Core source publishes `InvalidSuper` as a standard direct
child of `Error`, the frozen prelude exposes that exact prototype, and the typed
runtime Error factory creates one fresh occurrence per missing-`methodHome`
failure. The super-send execution path evaluates the complete ordinary
argument/spread vector before dispatch and then translates absent `methodHome`
into the standard Protos `InvalidSuper` control transfer without attempting
lookup or exposing the former host `IllegalStateException`. I020-A valid
method-bound receiver/methodHome behavior and `SlotNotFound` outcomes remain
unchanged.

History:
B005 moved `BLOCKED -> READY` when specification revision `0.1.377` / D040 made
the missing-`methodHome` behavior normative. I020-D implements that rule and
publishes focused Java plus language-level conformance, completing the transition
`READY -> CLOSED`. The earlier `InvalidSuper()` name in
`spec/runtime/ABSTRACT_RUNTIME.md` remains informative only and was not used as
independent normative authority.

Independent work:
No implementation work remains blocked by B005.

## B006 — Atomic package metadata replacement

Status: READY

Implementation area:
Package-tool Filesystem Slice 2B and every future `protos add`, `protos remove`,
`protos resolve`, or `protos update` path that must safely publish changes to
`protos.toml` or `protos.lock`.

Normative dependency:
Satisfied by specification revision `0.1.379` / D042, which corrects D041's
final-entry type restriction. Core Filesystem defines two general namespace-entry
operations:

```text
filesystem.replace(sourcePath, targetPath) -> Future<Filesystem>
filesystem.remove(path)                     -> Future<Filesystem>
```

`replace` performs one confined failure-atomic source-to-target namespace
transition, and `remove` performs one confined failure-atomic namespace-entry
removal. The contract fixes Path validation, authority, final-entry non-follow
selection, atomicity/visibility, commitment, cancellation, failure aftermath,
stable open File binding, concurrency, non-recursive removal, and the explicit
separation between live namespace atomicity and crash durability.

Specification authority:
- `spec/io/FILESYSTEM.md` §20 Filesystem Authority and Path, especially §20.1
  confinement and §20.3 atomic namespace-entry replacement/removal
- `spec/io/BYTE_IO.md` for File/Syncable durability and its namespace-durability
  exclusion
- `spec/io/IO_CORE.md` for I/O Future identity, commitment, cancellation,
  lifecycle, and failure rules

Unblock condition:
The normative portion is satisfied by revision `0.1.379` / D042: independent
implementations can now agree on the general operation shape, final-entry
selection rule, and every programmer-visible success/failure/cancellation outcome
needed for safe metadata publication without choosing package-specific semantics.

B006 closes only after a faithful production implementation of that general
Filesystem surface is available to the bundled package tool and package metadata
mutation uses it without an ambient/native package-only escape hatch. That
implementation work was tracked as I021 and is now CLOSED.

Current consequence:
I021 is CLOSED: I021-A/B/C are published, including Protos-source integrated
conformance over the confined production NIO `Filesystem.replace`/`remove` backend.
The package tool still deliberately uses its read-only CLI provisioning, so B006
remains READY until a subsequent package-tool slice explicitly grants staging/write
and namespace-mutation authority and performs metadata publication through the
standard Filesystem operations. No path may
fall back to in-place truncate/write, `PackageNative.rename(...)`, ambient host
filesystem access, or another package-only privileged path.

Once I021 is available, the intended package metadata publication composition is
ordinary Protos code: create/write the staging file through the granted
Filesystem/File capabilities, complete the required File sequencing, atomically
replace the target through `filesystem.replace(...)`, and use
`filesystem.remove(...)` to clean an uncommitted staging entry when required.
D042 does not prescribe staging-name policy or package-command policy.

Independent work:
Read-only TOML parsing and manifest validation, lock parsing/canonical validation,
semantic resolution-input modeling, in-memory version/constraint resolution, and
read-only execution preflight can continue independently. Work that needs only
already-open File byte/text behavior also remains independent.

History:
B006 was introduced as BLOCKED because Filesystem v0.1 exposed only `open` and
File operations; truncate-and-write could expose partial package metadata and no
general namespace replacement contract existed. D041 / revision `0.1.378`
closed the operation shape and moved B006 `BLOCKED -> READY`; D042 / revision
`0.1.379` then corrected the ordinary-file-only preclassification without changing
the API, atomicity, or package composition. B006 remains READY, not CLOSED, until
the package-tool integration satisfies the remaining implementation side of the
blocker.

Library dependency:
None. B006 is a general Filesystem semantic/capability boundary, not a missing
Standard Library package and does not allocate a `LIBxxx` item.
