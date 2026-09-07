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

Status: CLOSED

Implementation area:
Package-tool Filesystem Slice 2B and future package commands that safely publish
`protos.toml` or `protos.lock`.

Normative dependency:
Satisfied by specification revision `0.1.379` / D042 and the closed I021
production implementation. Core Filesystem supplies standard confined
`open`, `replace`, and `remove`; D042 preserves final-entry non-follow selection,
failure-atomic namespace visibility, commitment/cancellation semantics, and
non-recursive removal.

Closure evidence:
Package-tool Filesystem Slice 2B provisions one explicit confined project
Filesystem to the bundled Protos tool with three independent authority sets:
read access to `protos.toml`/`protos.lock`, write-only positioned `createNew`
access to the two exact staging names, and namespace mutation over those target
and staging entries. `self:MetadataPublication` performs content encoding,
standard File staging write/close, atomic `filesystem.replace(...)`, and explicit
`filesystem.remove(...)` discard in Protos code. An existing staging entry is not
silently removed or reused: `createNew` fails before the target is changed.

Current consequence:
B006 is CLOSED. Package metadata has a production-safe publication composition
without in-place target truncation, `PackageNative` filesystem helpers, ambient
host paths, or another package-only privileged mutation path. Future `add`,
`remove`, `resolve`, and `update` command policy may reuse this mechanism without
reopening the atomic-publication blocker. Full package-store/archive operations
remain outside B006 and still require their separately designed capabilities.

History:
B006 was introduced as BLOCKED because Filesystem initially exposed only `open`;
D041 / revision `0.1.378` defined atomic replacement/removal and moved it to
READY, D042 / revision `0.1.379` corrected final-entry selection, and I021-A/B/C
published the general runtime/backend/conformance. Package-tool Filesystem Slice
2B now completes the remaining write/staging-authority integration and closes the
blocker.

Library dependency:
None. B006 remains a general Filesystem capability/integration boundary and does
not allocate a `LIBxxx` item.

## B007 — Standard `while` protocol semantics

Status: BLOCKED

Implementation area:
The Core standard `while` operation named by
`spec/semantics/EXECUTION_AND_CONTROL.md` §17, together with implementation,
conformance, executable tutorial/example material, and programming-guide text
that would present that operation as runnable current behavior.

Normative dependency:
The current normative execution owner states that a `while` operation requires a
reevaluated condition and therefore semantically operates on a Closure, and gives
this shape:

```js
(() => i < 10).while() {
    i = i + 1
}
```

That statement fixes an important direction but does not yet define the complete
observable standard protocol precisely enough for independent implementation.

In particular, the current normative text does not explicitly and uniquely
determine all of the following:

- where the standard `while` selector is installed and its semantic receiver
  domain;
- exact argument arity and the required semantic/callable domain of the loop
  body;
- the exact per-iteration invocation order and argument vectors for condition
  and body;
- which normal condition results continue or terminate the loop, including
  whether canonical Boolean identity is required and how any non-Boolean result
  fails;
- whether and when condition/body callability or arity is validated;
- how the normal result of each body invocation is treated;
- the exact normal result of the complete `while` invocation, including the
  zero-iteration case;
- the complete Error, non-local-return, suspension, cancellation, and Future
  interaction contract required at the loop boundary.

Ordinary call, Closure, Boolean, Error, and task rules constrain many of these
interactions, but §17 does not currently compose those owners into one unique
standard `while` contract. Implementing the missing selector now would therefore
require selecting observable semantics that the specification has not selected.

Specification authority:
- `spec/semantics/EXECUTION_AND_CONTROL.md` §17 `Iteration and Loops` is the
  primary owner of iteration/loop control semantics;
- `spec/semantics/CALLABLES.md` owns ordinary callable invocation, capture,
  return-home, and non-local-return semantics that a loop callback must compose
  with;
- `spec/semantics/VALUES_AND_COLLECTIONS.md` owns the canonical Boolean domain
  and standard Boolean result contracts;
- `spec/semantics/ERRORS.md` owns Error signaling/unwind behavior;
- `spec/concurrency/FUTURES_AND_TASKS.md` owns suspension/cancellation and
  structured task behavior where loop callbacks interact with those mechanisms;
- `spec/PROTOS_GRAMMAR.md` owns ordinary call and trailing-Closure syntax. It
  currently defines no dedicated `while` syntax.

Unblock condition:
The current normative specification explicitly and uniquely determines the
complete observable standard `while` protocol, including selector
placement/receiver domain, exact argument/callback domains, condition and body
invocation order, accepted condition results and failure behavior, validation
timing, body-result treatment, complete normal result, and composition with
ordinary Error/control-transfer/suspension/cancellation/Future semantics.

The unblock condition is semantic, not textual: it is satisfied only when two
independent implementers can derive the same observable behavior without
choosing any of the items above themselves.

Current consequence:
Do not implement or publish a standard `while` selector from the existing
one-example description.

The Programming Guide control-flow chapter is also paused at this point. It
cannot responsibly present the specified `while` shape as runnable current
behavior, and it must not silently invent the missing contract. The published
guide therefore remains complete only through chapter 03 until this blocker is
resolved.

This is not an implementation-tool or environment limitation. The reference
implementation currently has no standard `while` selector, but implementation
absence alone is not the reason for `BLOCKED`; the blocker is the unresolved
observable normative contract above.

Independent work:
Existing `ifTrue` / `ifFalse` / `and` / `or`, trailing-Closure syntax, collection
`each`, and other already-specified and already-implemented behavior may continue
independently. Documentation or implementation work unrelated to the missing
`while` contract is not blocked.

History:
B007 was discovered while auditing the planned Programming Guide control-flow
chapter after chapters 01-03 were published. Repository inspection found the
normative Closure-based `while` example but no complete standard protocol
contract, no current reference-implementation selector, and no executable
tutorial/conformance coverage for `while`. The documentation work stopped rather
than turning that gap into accidental language design.
