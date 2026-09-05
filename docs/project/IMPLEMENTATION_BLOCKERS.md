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

Status: BLOCKED

Implementation area:
I011 public/distributed Group acquisition/discovery and any language-visible operation that
creates, resolves, or reacquires a Group or GroupRef by a portable discovery facility.

Normative dependency:
The distributed-runtime specification closes Group identity, GroupRef semantic identity,
transfer, routing, acceptance, rerouting, and communication semantics. However, its Open Design
Topics explicitly leave the exact Group/GroupRef API and syntax unresolved, and §72B explicitly
states that Core v0.1 does not define a new public discovery API, namespace model, consistency
level, TTL contract, watch semantics, federation model, persistence guarantee, security model, or
schema/versioning rule. Choosing selectors, constructors, names, registry behavior, or authority
for acquisition in implementation would therefore invent observable language semantics.

Specification authority:
- `spec/concurrency/DISTRIBUTED_RUNTIME.md` §50 Runtime Groups
- `spec/concurrency/DISTRIBUTED_RUNTIME.md` §72B Service Discovery Implementation Is Not Core Semantics
- `spec/concurrency/DISTRIBUTED_RUNTIME.md` Open Design Topics

Unblock condition:
The current normative specification defines an exact portable Group/GroupRef acquisition and/or
discovery surface sufficiently to implement it without choosing new selector names, syntax,
namespace semantics, authority, rebinding behavior, consistency, or failure outcomes in the
implementation.

Current consequence:
Do not add a public `Group`, `GroupRef` constructor/acquire/lookup selector, magic discovery name,
ambient registry, or implementation-selected discovery namespace. Existing internal runtime
GroupRef acquisition and the already-published language-visible GroupRef `send`/`request` surface
remain valid because their observable semantics are independently closed.

Independent work:
Genuinely remote transport/routing machinery that preserves already-closed acceptance and
uncertainty semantics may continue behind internal runtime boundaries without exposing a new
discovery API. I017 Process/bootstrap integration and unrelated Actor/Group conformance work may
also continue independently.
