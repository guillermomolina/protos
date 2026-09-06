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

Status: BLOCKED

Implementation area:
I020-D execution/failure semantics for a syntactically valid `super.message(...)`
whose current activation has no physical `methodHome`, such as execution outside
a method-bound invocation.

Normative dependency:
`spec/semantics/EXECUTION_AND_CONTROL.md` §8 defines valid super lookup as
preserving the current receiver while starting lookup at
`parent(context.methodHome)`, but it does not define the observable result when
`context.methodHome` is absent. The normative Error taxonomy likewise defines no
standard `InvalidSuper` identity. `spec/runtime/ABSTRACT_RUNTIME.md` contains an
informative `InvalidSuper()` pseudocode branch, but that document is explicitly
non-normative and cannot introduce a new standard Error family.

Specification authority:
- `spec/semantics/EXECUTION_AND_CONTROL.md` §8 `super`
- `spec/semantics/ERRORS.md` for standard Error construction/identity if failure
  is the selected behavior
- `spec/PROTOS_GRAMMAR.md` for the syntactic validity/scope of super-message-send

Unblock condition:
The normative specification explicitly and uniquely defines whether executing a
super message with no `methodHome` is dynamically invalid and, if it fails, the
exact standard Error family/identity and failure timing. Independent
implementations must be able to produce the same observable result without
consulting `ABSTRACT_RUNTIME.md`.

Current consequence:
I020-A implements the fully determined case where `methodHome` exists, including
ordinary argument/spread evaluation, lookup after that home, preservation of the
dynamic receiver, nested-Closure capture and `SlotNotFound` when lookup from the
defined origin is exhausted. The missing-`methodHome` path remains an explicit
implementation limitation and I020-D stays BLOCKED.

Independent work:
I020-B concurrent test-harness reliability, I020-C ledger reconciliation, valid
method-bound super execution/conformance, Standard Library work and unrelated
implementation/performance work may proceed independently.
