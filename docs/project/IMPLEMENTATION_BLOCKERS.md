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

Status: READY

Implementation area:
The Core standard `while` operation defined by D044 / specification revision
`0.1.381`, together with I023 reference implementation, conformance, executable
tutorial/example material, and programming-guide text that presents the operation
as runnable current behavior.

Normative dependency:
Satisfied by specification revision `0.1.381` / D044.

`spec/semantics/EXECUTION_AND_CONTROL.md` §17 now defines the complete standard
Closure `while(body)` behavior: Closure-only receiver and body domain, exact
arity, validation timing, zero-argument condition/body activation order, strict
canonical Boolean condition results, fresh Error on every other normal condition
result, ignored body results, canonical `null` normal result, and exact
Error/non-local-return/suspension/replay/cancellation/Future composition.

`spec/semantics/CALLABLES.md` fixes `while` as an ordinary local Closure-valued
`Object` slot with standard semantic-Closure receiver-domain behavior, ordinary
reflection/extraction/shadowing, and no `Closure` prototype. The grammar owner
confirms that `condition.while() { ... }` is only ordinary call plus trailing
Closure and adds no `while` keyword or dedicated loop syntax.

Specification authority:
- `spec/semantics/EXECUTION_AND_CONTROL.md` §17 `Iteration and Loops` — primary
  owner of standard loop execution semantics;
- `spec/semantics/CALLABLES.md` — standard `Object.while` placement, Closure
  receiver domain, extraction/shadowing and ordinary Closure activation rules;
- `spec/semantics/VALUES_AND_COLLECTIONS.md` — canonical `true` / `false` domain;
- `spec/semantics/ERRORS.md` — fresh Error occurrences and Error unwind;
- `spec/concurrency/FUTURES_AND_TASKS.md` — suspension, cancellation and
  structured task behavior composed by the loop;
- `spec/PROTOS_GRAMMAR.md` — unchanged ordinary call/trailing-Closure syntax.

Unblock condition:
Satisfied by specification revision `0.1.381` / D044. Two independent
implementers can derive the same selector location and receiver domain, argument
domain/validation order, exact callback activation count/order and argument
vectors, accepted condition values/failure behavior, body-result treatment,
normal result, and Error/control-transfer/suspension/cancellation/Future behavior
without selecting new observable semantics.

Current consequence:
B007 is `READY`, not `CLOSED`. `I023 — Standard while protocol` is allocated and
READY for implementation. No reference implementation selector is published by
D044 itself.

The Programming Guide control-flow slice DOC001-E remains
`BLOCKED_BY_DEPENDENCIES` on I023. D044 makes the semantics explainable, but the
guide must not present the standard `while` form as runnable current behavior
until I023 implementation/conformance is published.

Independent work:
Existing `ifTrue` / `ifFalse` / `and` / `or`, trailing-Closure syntax, collection
`each`, and all unrelated already-defined work remain independent. I023 may now
proceed in its recorded slices without another language-design decision unless
implementation audit exposes a genuine contradiction in D044.

History:
B007 was discovered while auditing the planned Programming Guide control-flow
chapter after chapters 01-03 were published. The initial normative text named a
Closure-based shape but left selector placement, callback domains/order,
Boolean/result rules and control/concurrency composition implementation-selectable,
so documentation stopped rather than inventing semantics. D044 / specification
revision `0.1.381` closes that normative gap and transitions B007
`BLOCKED -> READY`; final `READY -> CLOSED` requires I023 implementation,
validation, and publication.

## B008 — Structured ownership when a task-backed Future escapes an activation

Status: BLOCKED

Implementation area:
I023-B2D2 structured-ownership/cross-B2 closure and any runtime change that
would make an ordinary synchronous Closure activation wait for, transfer,
re-parent, or otherwise alter ownership of a task-backed Future merely because
that Future was produced inside the activation or returned as its exact normal
result.

Normative dependency:
The current normative sources do not uniquely determine the lifetime boundary
for this case. `spec/concurrency/FUTURES_AND_TASKS.md` states both that an
ordinary function may return a Future and that asynchronous child work is owned
by its creating execution context by default, while §24 says an owner reaching
otherwise normal completion waits for every non-detached child. The same section
defines `detach()` as removing a task from the structured lifetime of its
creating activation. Separately, `spec/concurrency/PARALLEL_EXECUTION.md`
requires P to create and return a normal Future owned under the ordinary
structured-concurrency rules of the creating activation, with successful return
itself being a normative cutover point.

D044 composes those existing rules without adding a loop-specific answer:
`spec/semantics/EXECUTION_AND_CONTROL.md` requires a normal Future returned by a
`while` body to be ignored exactly like any other body result, with no implicit
await/adoption/flatten/cancellation, while work created during the callback
continues to use the ordinary structured-ownership rules.

The specification does not currently say, for a task-backed Future produced by
`future()`, `then()`, P, or equivalent ordinary work and returned from the same
synchronous activation, whether normal return:

- waits for that Future to become terminal before the invocation can return;
- transfers/re-parents the ownership edge to a caller or enclosing structured
  execution context;
- permits the activation to finish while retaining an ownership edge whose
  lifetime is defined elsewhere; or
- follows another explicit general rule.

Those choices are observably different and cannot be selected as implementation
machinery.

Specification authority:
- `spec/concurrency/FUTURES_AND_TASKS.md` §§27, 30 and 24;
- `spec/concurrency/PARALLEL_EXECUTION.md` for P result-Future creation/return
  and ordinary structured ownership;
- `spec/semantics/EXECUTION_AND_CONTROL.md` standard Closure `while` operation;
- `spec/semantics/CALLABLES.md` for ordinary synchronous Closure activation and
  normal result semantics.

Unblock condition:
The current normative specification must explicitly and uniquely define the
structured-lifetime rule for a task-backed Future that escapes as the exact
normal result of the activation that created it, including at least:

1. the precise owner/lifetime boundary before and after the activation returns;
2. whether ownership is waited, transferred/re-parented, retained, or removed;
3. how the rule composes with `Future.then`, P, `Future.detach()`, adoption and
   ordinary functions that intentionally expose pending Future-shaped APIs;
4. whether task-backed work created but not returned follows a different rule;
5. the ordering consequence for D044 `while`: ignoring a body Future result must
   not secretly introduce a loop-specific await, adoption, cancellation, or
   scheduler boundary.

Two independent implementations must be able to implement the same observable
ordering and lifetime without inferring programmer intent from escape analysis,
API naming, library conventions, or current runtime behavior.

Current consequence:
I023-B2D2 is BLOCKED. B2D1 remains valid evidence for D044's result boundary:
`while` itself neither awaits/adopts/flattens/cancels a body Future result. The
remaining structured-ownership closure cannot be claimed until B008 is resolved.
No activation-scoped ownership experiment produced while investigating B2D2 was
published to `main`.

Diagnostic evidence that exposed the ambiguity is intentionally non-normative:
an activation-drain experiment made the existing Future-shaped
`JSON.readEvents(...).read()` overlap conformance stop observing an outstanding
operation, because the first call could not return while its newly-created
continuation remained pending. That result demonstrates observability of the
choice; it does not choose the language rule.

Independent work:
Unrelated implementation work may continue. I023-C and I023-D remain dependency
blocked through I023-B/B2/B2D. B007 remains READY because D044 itself is already
normatively resolved; B008 is a separate structured-concurrency ambiguity
exposed while completing D044 conformance.
