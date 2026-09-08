# Protos Open Tasks

This file records concrete non-normative work that should be done but is not
blocked by an unresolved language-semantic decision.

It is distinct from:

- `docs/project/IMPLEMENTATION_BLOCKERS.md`, which records implementation work that
  cannot proceed until normative semantics are resolved;
- `docs/design/IDEAS.md`, which records exploratory possibilities not yet committed as
  implementation work;
- normative specification Open Design Topics, which track unresolved semantic
  or API design.

An item should move here from `../design/IDEAS.md` only when there is a concrete outcome
worth implementing or investigating. If work becomes blocked on normative
semantics, record that dependency in `IMPLEMENTATION_BLOCKERS.md` instead.

Task states:

- `OPEN`: concrete work remains.
- `IN PROGRESS`: implementation or investigation is actively underway.
- `BLOCKED`: use only for a non-semantic external dependency; normative blockers
  belong in `IMPLEMENTATION_BLOCKERS.md`.
- `CLOSED`: the work is complete or obsolete.

## Open tasks

### AUD001 — Retrospective design-decision ratification audit

Status: **OPEN**
Priority: **HIGH**
Nature: non-normative governance and provenance audit

Audit the provenance and continued suitability of design decisions D001-D045,
excluding D046 because it is already under separate active user review. The
purpose is to distinguish explicit project-owner selection from agent-authored
recommendations, broad implementation instructions, patch execution, and
publication evidence.

Current triage:

- D038 has explicit project-owner confirmation and needs only have that evidence
  recorded.
- D020 and the still-unratified decisions in D040-D044 require priority review
  because no recovered evidence yet demonstrates explicit project-owner selection
  of their complete published semantics.
- D021-D032 and D034 require provenance and substance review; publication alone
  is not ratification.
- D001-D019 are expected to be predominantly project-owner decisions, but their
  approval evidence must be checked rather than inferred.

Recorded review results:

- D037 is `RATIFIED`. AUD001 had already recovered explicit project-owner
  confirmation of the published D037 / specification `0.1.375` choice when
  the audit was opened; this entry persists that evidence rather than
  inferring approval from authorship, patch execution, or publication. The
  ratified contract keeps portable Path equality structural and filesystem-
  independent: equality compares rootedness plus the ordered component
  sequence. Path semantic identity remains ordinary individual object
  identity, so independently created structurally equal Paths remain
  distinct through `===`, `!==`, `identityHashOf`, and `IdentityMap`; Path
  is not added to the closed Core value-identity set. Filesystem namespace
  lookup identity, host syntax/normalization, and resource identity remain
  separate from portable Path equality. The normative D037 publication is
  commit `6af40a7f013a3cb792740f991c64641d9d5e53c0`; the existing AUD001
  owner-confirmation record was opened in commit
  `a8e77489c3989d4ffe03da8539c6e688c03bfaad`. This governance
  classification changes no normative specification or implementation.

- D043 is `RATIFIED`. On 2026-09-08 the project owner explicitly approved the
  complete reviewed standard Closure `ensure(cleanup)` design and the bounded
  clarification published as specification `0.1.385`. `ensure` remains an ordinary
  Closure/message protocol with eager validation, one task-local protected dynamic
  extent, suspension/replay stability, exactly-once LIFO cleanup on normal, `^`,
  Error and cooperative-cancellation exits, exact normal-result preservation, no
  implicit Future wait/adoption, no automatic Error mutation/cause/suppression/
  composite wrapping, and precedence for a later transfer only when it escapes
  cleanup. An Error handled completely inside cleanup does not supersede
  the pending transfer. A selected Error handler is a consumed one-shot unwind
  destination: normal crossed cleanup reaches it, while an escaping replacement
  transfer abandons it and searches only still-active handlers. The already-honored
  cancellation request remains narrowly shielded during cleanup; repeated
  idempotent `Future.cancel()` calls are not a stronger request. D043 guarantees
  semantic-unwind cleanup while execution remains capable of running Protos code,
  not deterministic GC/destructor cleanup or cleanup after fail-stop/forced loss,
  and live dynamic cleanup state does not
  become cross-Task/P/Actor/Process/Node or restart/distribution state. Future
  resumable recovery, continuations/effects, multi-shot duplication, hard
  termination, durable workflows and distributed compensation remain separate
  explicit design work rather than reinterpretations of Core v0.1 `ensure`.
- D045 is `RATIFIED`. On 2026-09-08 the project owner explicitly approved retaining
  its task-scoped ownership core: synchronous Closure/method activations do not create
  structured scopes; returning, storing or wrapping a pending Future does not alter
  its ownership edge; distinct asynchronous child tasks own their own descendants;
  and `detach()` is the explicit operation that removes the edge. This avoids
  per-activation draining, result-shape/escape heuristics and hidden implicit
  detachment while preserving ordinary Future-returning APIs. The ratification
  confirms the already-published D045 semantics; it does not change normative
  specification or implementation and is independent of the separately ratified
  specification `0.1.90` structured-child terminal-outcome policy.
- The structured-child terminal-outcome policy introduced in specification
  revision `0.1.90` is `RATIFIED`. Its original commit
  `53fd43c7edceaa2fbc93bdada645cc7b79195f0e` contained no recovered evidence of
  explicit project-owner selection, so AUD001 independently compared automatic
  fail-fast propagation, hidden observed/unobserved-failure state, explicit
  policy-bearing scopes and the published lifetime-only rule. On 2026-09-08 the
  project owner explicitly approved retaining the published Core v0.1 policy:
  structured ownership waits for non-detached children and governs cleanup, but
  child failure/cancellation affects owner control flow only through explicit
  Future observation; owner error/cancellation still cancels non-detached
  children and waits for cleanup. No hidden failure-consumption state is added.
  Explicit fail-fast/supervision or aggregation remains possible future
  library/design work rather than universal `future()` behavior. This
  ratification is independent of D045's separately ratified ownership-scope core.
- D042 is `RATIFIED`. On 2026-09-08 the project owner explicitly approved
  retaining the complete published race-safe Filesystem namespace-entry
  correction after comparative review against POSIX/Unix, Java NIO, Rust, Go,
  Python and .NET. Final Path components are selected as namespace entries
  without following final symbolic-link/reparse/other indirection and without a
  separate mutable file-kind preclassification; `replace`/`remove` may operate
  on entry kinds when the backend can provide the required atomic transition;
  unsupported atomic entry-kind/source-target combinations fail as `IOError`
  rather than being emulated through check-then-act; and `remove` remains
  non-recursive. This ratifies D042 / specification `0.1.379` only; D041's
  separate atomicity, commitment, cancellation, stable-open-File and durability
  package is independently `RATIFIED` in its effective post-D042 form below.
- D041 is `RATIFIED` in its effective form after D042. On 2026-09-08 the
  project owner explicitly approved retaining D041's still-effective
  Filesystem namespace-mutation package after comparative review against
  POSIX/Unix, Python, Java NIO, Rust, Go and Windows/.NET-style filesystem
  models: both paths remain confined to one explicit Filesystem authority;
  `replace` is one indivisible source-to-target namespace transition with no
  operation-created missing-target window and same-resource replacement is a
  no-op; `replace` is not copy-then-delete or truncate-and-write; `remove` is
  one indivisible namespace transition; already-open File capabilities keep
  their selected resource; cancellation/failure before commitment contributes
  no namespace mutation, while the committed transition is irreversible and
  cannot later be reported as failed/cancelled; implementations that cannot
  provide a determinate conforming transition fail closed; distinct Filesystem
  operations have no implicit FIFO; and live atomic visibility is explicitly
  separate from crash durability, with File `sync()` not serving as a
  namespace-durability barrier. D041's original ordinary-file-only final-entry
  restriction is `SUPERSEDED` by the independently ratified D042 /
  specification `0.1.379` race-safe namespace-entry selection and is not part
  of this ratification. This governance classification changes no normative
  specification or implementation.
- D040 is `RATIFIED`. On 2026-09-08 the project owner explicitly approved
  retaining D040 / specification `0.1.377` after comparative review against
  Smalltalk, Self, JavaScript, Python and Java plus a separate future-
  scalability review covering traits/mixins, multiple delegation, method
  combination, metaprogramming, isolation and JIT optimization. Retain the
  published model in which `super` is special lookup syntax that preserves the
  original receiver and continues lookup after the invocation's `methodHome`;
  Method remains an invocation role of the single Closure value kind rather
  than a static executable category; the complete ordinary argument/spread
  vector is formed before super dispatch; missing `methodHome` signals one
  fresh `InvalidSuper` without lookup; and a valid home with an empty/exhausted
  continuation remains ordinary `SlotNotFound`. No first-class `super`, hidden
  fallback origin, static Method category or P-specific super error is added.
  The scalability review records `parent(methodHome)` as the exact Core v0.1
  realization under the current exactly-one-parent object model; this
  ratification neither prohibits nor authorizes a future multiple-parent,
  linearized, directed-resend, or next-applicable-method generalization. Any
  such evolution remains a separate explicitly approved normative design
  decision. This governance classification changes no normative specification
  or implementation.

- D039 is `RATIFIED`. On 2026-09-08 the project owner explicitly approved retaining
  the complete published Core v0.1 public ActorGroup acquisition design after
  comparative and scalability review. `Actor.group(firstMember,
  additionalMembers...) -> GroupRef` remains the only direct Core acquisition
  surface; it accepts one or more explicit ActorRef communication capabilities,
  validates the complete vector before one synchronous creation cutover, creates
  one fresh Group identity plus one fresh GroupRef acquisition, and treats initial
  membership as a set of concrete Actor incarnations rather than weighted duplicate
  references. The caller Actor's Process remains the Core-created Group lifetime
  scope; creator-Actor death alone does not terminate the Group, owning-Process
  termination does, Group termination does not terminate member Actors, and remote
  or aliased GroupRefs do not extend Group lifetime or retarget after termination.
  Creation grants routing/communication capability only: it does not create, move,
  restart or rehost members, does not wait for READY/reachability/routability, and
  does not grant member lifecycle or Group-control Authority. Core v0.1 continues
  to expose no public post-creation membership/controller/desired-cardinality/
  termination/placement surface, no Group identity handle or GroupRef reacquisition
  selector, and no registry, service-discovery, name/rebinding, endpoint or
  transport-selection API. Durable service ownership, discovery, dynamic Group
  control and related distributed institutions remain separate future design work.
  This ratification confirms D039 / specification `0.1.376` without normative or
  implementation change.
- D038 is `RATIFIED` from recovered explicit project-owner selection. The
  recovered 2026-09-04 decision sequence shows that the project owner accepted
  the bounded D038 Encoding membership/receiver-domain closure package before
  publication and then explicitly confirmed D038 as published/closed. Retain
  specification `0.1.375`: Encoding descriptors are semantic Encoding values
  produced/provisioned only through normative Encoding-producing operations or
  explicitly permitted host Encoding-provisioning boundaries; delegation,
  copying, composition, similarly named slots and structural/protocol
  compatibility do not confer membership; Encoding-descriptor parameters do
  neither duck typing nor implicit coercion; and standard Encoding-family
  behavior uses the general semantic-family receiver-domain rule, validating
  the original receiver after ordinary lookup so an incompatible receiver
  signals the ordinary invalid-receiver `Error` before family-specific
  computation/state effects and without fallback dispatch. User-defined
  overrides remain ordinary behavior under their own receiver contracts. This
  records already-selected/published semantics; it changes no normative
  specification or implementation.

- D035 is `RATIFIED` from recovered explicit project-owner selection. The
  recovered 2026-09-04 decision sequence shows that, immediately after the
  concrete recommendation to close the Bytes identity/mutable-state gap with
  one general standard Bytes-producing result rule plus the `read` and
  `encode` specializations, the project owner accepted that bounded proposal
  and requested the first publication prompt. Retain D035 / specification
  `0.1.373`: unless an operation expressly returns an existing object, every
  successful Core-standard operation whose normal result is `Bytes` returns
  one fresh open standard Bytes identity with independent receiver-owned
  mutable state, including empty results. A result is not identical to or
  mutable state shared with source/argument Bytes, the producer, another
  invocation's result or an implementation buffer; ordinary `===`/`!==`,
  `identityHashOf`, `IdentityMap`, default equality/hash and open/closed/frozen
  observations follow from those distinct ordinary Bytes identities. The rule
  constrains observable semantics rather than physical copying, so immutable
  backing, copy-on-write, slicing, persistent storage, zero-copy, lazy
  materialization and scalar replacement remain permitted when unobservable.
  Failed/cancelled operations expose no partial Bytes result, while later
  Actor/P transfer remains governed independently by the existing value-
  transfer rules. This classification records already-selected and still-
  current semantics; it changes no normative specification or implementation.

- D036 is `RATIFIED`. On 2026-09-08 the project owner explicitly approved retaining
  D036 / specification `0.1.374` after comparative, adversarial and scalability
  review. Unless a Core-standard operation expressly returns an already-existing
  Future, every successfully dispatched Future-producing invocation keeps its own
  fresh semantic standard Future identity, including results that are immediately
  resolved, failed or cancelled. Distinct invocation results remain distinct under
  `===`, `!==`, `identityHashOf` and `IdentityMap`; implementations may not make
  identity depend on pending-work sharing, terminal-result caching, scheduling or
  completion timing. Future identity remains independent from eventual value/Error/
  outcome identity. An operation whose normative contract expressly returns an
  existing Future preserves that Future instead of manufacturing another identity,
  which keeps receiver-preserving operations such as `cancel()`/`detach()` and future
  explicit memoized/shared-work APIs expressible without weakening the default rule.
  Freshness is semantic rather than an allocation mandate: scalar replacement,
  allocation elision, virtualization, pooling, canonical terminal-state backing and
  other invisible optimizations remain allowed. This ratifies D036 only; D031
  remains independently subject to AUD001 review. No normative specification or
  implementation change is introduced by this classification.

- D033 is `RATIFIED`. On 2026-09-08 the project owner explicitly approved
  retaining D033 / specification `0.1.371` after comparative review spanning
  Self/Smalltalk, Python, Java/.NET, Ruby and JavaScript plus a dedicated
  future-scalability review. Standard `hasSlot(name)`, `slotValue(name)`, and
  `removeSlot(name)` continue to accept exactly semantic `String` values. No
  implicit conversion, stringification, selector coercion, delegation-based
  String-like acceptance, host-name adaptation or hidden Unicode normalization
  is introduced. After ordinary argument evaluation, an invalid non-String
  name fails before local-slot inspection and, for `removeSlot`, before any
  structural mutation. A valid String denotes exactly its Unicode scalar-value
  sequence as the slot name. The operations remain local-only: `hasSlot` returns
  canonical `false` for a valid absent local name, `slotValue` and `removeSlot`
  retain their ordinary missing-local-slot Error, and delegated slots do not
  satisfy any of the three operations. The public String contract does not
  prescribe lookup machinery: implementations remain free to intern/canonicalize
  storage, cache hashes, use internal name/slot IDs, shapes or inline caches when
  Protos cannot observe a semantic difference. A future first-class Symbol,
  Selector or broader name facility remains separately designable; introducing
  one does not implicitly widen these Core v0.1 APIs. This ratification changes
  no normative specification or implementation.

Required procedure:

1. Continue backwards through the remaining unresolved decisions in D044-D040,
   then D020, D034, D021-D032, and finally D001-D019.
2. For each decision, reconstruct the alternatives, recommendation, published
   normative result, downstream implementation, and owner-approval evidence.
3. Classify it as RATIFIED, NEEDS_USER_DECISION, SUPERSEDED, or
   PROVENANCE_UNRESOLVED. Executing or publishing a patch is not sufficient
   approval evidence.
4. Present every substantive unresolved choice to the project owner under the
   current explicit design-approval gate. Do not silently preserve, replace, or
   reopen semantics.
5. Keep D046 outside AUD001 and do not let this audit overwrite or pre-empt its
   separate review.

Next audit work:

1. Complete the separate D044 review already in progress, considering D045 only
   as a ratified dependency where their semantics interact.
2. Continue backwards through the remaining unresolved decisions in D043-D040, then D020, D034, D021-D032 and D001-D019
   under the required procedure above.

AUD001 closes only when D001-D045, except D046, have an explicit classification,
the project owner has decided every NEEDS_USER_DECISION item, relevant
provenance is recorded durably, and all affected project ledgers are reconciled.
Any later normative correction must be a separately approved specification
change; AUD001 itself authorizes no specification or implementation change.
