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
- D044 remains the priority review item because no recovered evidence yet
  demonstrates explicit project-owner selection of its complete published semantics.
  is not ratification.
- D002 and D005-D006 and D008-D019 are expected to be predominantly project-owner decisions, but their
  approval evidence must be checked rather than inferred.

Recorded review results:

- D006 is `RATIFIED`. On 2026-09-08 the project owner explicitly approved
  retaining D006 / specification `0.1.344` after cross-language comparison
  spanning Python, Julia, Swift, ECMAScript BigInt, Rust and Self plus adversarial
  and future-scalability review. Core v0.1 keeps ordinary `Integer` exact and
  semantically unbounded, `Float` exactly IEEE 754-2019 binary64, and the eight
  fixed-width signed/unsigned integer families as distinct semantic families.
  Standard arithmetic performs no implicit cross-family promotion, widening,
  narrowing or coercion; family changes use explicit conversion factories, and
  fixed-width ordinary arithmetic signals `Error` rather than silently wrapping
  when its specified result is not representable. Exact-integer `/` retains the
  specified correctly rounded binary64 result computed from the exact rational
  quotient, while `div`, `mod` and `%` retain their same-family exact-integer
  contracts. Numeric `==` and ordering remain cross-family mathematical
  comparisons without operand conversion, `===` remains sensitive to semantic
  numeric family and Float special-value identity, and standard numeric hashing
  remains coherent with `==`. `SmallInteger`, `BigInteger`, tagging, boxing and
  other storage strategies remain unobservable implementation representation.
  Future Rational, Decimal, Complex, bit/wrapping protocols, relaxed/SIMD math
  and any promotion framework remain separate explicit design decisions rather
  than consequences of D006. The original normative publication is commit
  `22bf764b04d9118b2e5c182a21e6f4b46840a80a`; publication alone was not treated
  as owner approval. This governance classification changes no normative
  specification or implementation and does not classify D004-D005 or D007-D019
  by transitivity.

- D003 is `RATIFIED AS AMENDED`. On 2026-09-08 the project owner explicitly approved
  retaining D003's deterministic Closure argument/default/rest/spread binding core
  from specification `0.1.340` after comparison with Self, Ruby, Kotlin,
  JavaScript and Python plus adversarial and future-scalability review, while
  correcting one unusable positional signature shape in specification `0.1.386`.
  Caller arguments/spreads still form one left-to-right supplied vector before
  binding; parameters bind left-to-right in the real activation; defaults run
  exactly once only when their position is unsupplied and see earlier established
  parameters plus ordinary `this`/`context`/`args`; current/later not-yet-bound
  names still use ordinary bare-name lookup; `args` remains caller-supplied only;
  rest remains the unconsumed supplied suffix; and existing Error, return,
  suspension and partial-effect behavior is unchanged. The amendment requires all
  required non-rest parameters to precede every defaulted parameter, with optional
  rest final, because under Core v0.1 positional-only invocation a default before
  a later required parameter can never be selected by an otherwise successful
  call. No named arguments, omitted-position marker, hidden missing/undefined
  value, TDZ or second parameter namespace is added. The original complete-binding
  publication was agent-authored commit
  `d37fc392d4b7b0d8c9df6d52bea0ecd39328ea8a`, so publication alone was not
  treated as approval. `I025` is allocated `READY` for parser/conformance alignment;
  this governance/specification publication does not claim that implementation
  work complete. D002 and D004-D019 remain independent AUD001 decisions.

- D001 is `RATIFIED`. On 2026-09-08 the project owner explicitly approved retaining D001 / specification `0.1.341` after cross-language comparison, adversarial review and dedicated future-scalability analysis. The semantic `Sequence` remains one general left-to-right expression-sequence mechanism: on normal completion, a non-empty Sequence returns the exact result of its final expression and a zero-expression Sequence returns canonical `null`. Error signaling/unwind, non-local return, cooperative cancellation and other control transfers that leave the Sequence are not converted into `null` and yield no normal Sequence result. The same rule applies to source module/program bodies, braced Closure bodies and other semantic expression-sequences, while `object-body-sequence` and object construction remain independently owned and are not redefined by empty-Sequence semantics. Core v0.1 therefore introduces no `Unit`, `undefined`, EmptySequence value, empty-body Error or receiver/context-dependent result merely to represent zero expressions. Implementations may erase, inline, constant-fold or otherwise optimize Sequence machinery provided the observable result/control behavior stays exact. A future type system may separately design a `Unit` or no-useful-result abstraction without being pre-created by D001. The original normative D001 publication is commit `6724c6e63750cd777ea74fc5d8ae24de7c02cf34`; this governance classification changes no normative specification or implementation and does not classify D002-D019 by transitivity.

- D024 is `RATIFIED`. On 2026-09-08 the project owner explicitly approved
  retaining D024 / specification `0.1.367` after comparative, adversarial and
  future-scalability review spanning Python bound methods, JavaScript ordinary
  extraction/`bind`, Ruby Method objects, .NET delegates and Self-style method
  activation. Every successful ordinary member read whose selected slot value is
  a Closure continues to produce one fresh identity-bearing Closure representing
  that receiver-bound extraction, carrying the original receiver and selected
  lookup home/`methodHome` required by the current callable model. Repeating the
  same extraction never canonicalizes by receiver, slot, stored Closure or
  `methodHome`; ordinary aliasing, argument passing, local assignment and other
  reference-preserving operations retain the exact already-extracted Closure.
  Storing an extracted Closure does not mutate or rebind it, while a later
  ordinary member read of a slot containing that Closure is a new extraction and
  therefore produces a fresh Closure with the new read's receiver/lookup binding.
  Primitive `===`, `!==`, `identityHashOf` and `IdentityMap` keep their ordinary
  identity semantics; no bound-method equality, interning table or global identity
  registry is introduced. Immediate message invocation remains distinct from
  extraction and need not materialize an unobservable bound Closure, preserving
  implementation freedom for dispatch caching, allocation elision, scalar
  replacement and copy-on-write/persistent local-slot backing where semantics stay
  exact. D024 retains Closure as the single Core executable value kind and adds no
  Method/BoundMethod value kind or standard Closure prototype. Stable callback
  identity can be expressed by retaining one extraction (or by a future explicit
  subscription/capability API) rather than by implicit canonicalization. Future
  behavior-reflection/equality facilities, multiple-delegation lookup metadata,
  cross-isolation Closure transfer rules and physical bound-Closure representation
  remain separate explicit design/implementation work. This governance
  classification changes no normative specification or implementation and does
  not classify D025 or D026 by transitivity.

- D022 is `RATIFIED`. On 2026-09-08 the project owner explicitly approved retaining D022 / specification `0.1.362` after cross-language comparison, adversarial review and dedicated future-scalability analysis. The inherited standard `Object.init()` continues to accept zero arguments and, on normal completion, returns its exact receiver (`this`); a non-empty vector handled by that inherited standard behavior signals the ordinary argument-count Error. An overriding `init` remains an ordinary Closure method with ordinary return semantics and is not required to return `this`. Default construction remains deliberately separate from initializer return value: inherited `Object.call` creates one fresh child of the invocation receiver, sends ordinary `init` with the supplied arguments, ignores any normal initializer result, and returns that fresh instance; initialization Error or other control transfer propagates and prevents a successful construction result. This keeps direct initialization composable without making `init` a hidden factory or special initializer category. Alternative constructors remain ordinary named messages. This governance classification changes no normative specification or implementation and does not classify D021, D023, D024 or any future constructor/initializer abstraction.

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
  other invisible optimizations remain allowed. D031 is independently `RATIFIED` under AUD001 as recorded below. No normative specification or
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
- D034 is `RATIFIED`. On 2026-09-08 the project owner explicitly approved
  retaining D034 / specification `0.1.372` after comparative, adversarial and
  future-scalability review. `Error.handle(body, handler)` remains a dynamic
  control boundary whose `body` and `handler` parameters must be semantic
  Closures; an object that is merely ordinarily invokable through `call` is
  intentionally insufficient. Ordinary receiver/body/handler expression
  evaluation completes left-to-right first; the resulting body is then
  Closure-validated, then the handler, and no HandlerFrame is installed until
  both validations succeed, so an invalid handler cannot allow the protected
  body to begin. Closure declared arity is not a D034 preflight: body and
  handler parameter/default/rest binding remains ordinary activation-time
  behavior, preserving the dynamic handler boundary for failures raised by
  actual body activation and the consumed-handler rule for handler activation.
  Once selected, the handler frame is inactive before the handler Closure runs
  and the handler receives the exact signaled Error object. This deliberate
  narrowness is distinct from D030's ordinary-invokable `Future.then` callback
  domain: dataflow callbacks and dynamic-control extents need not share one
  receiver/argument category. No Handler value kind, hidden callback protocol,
  duck typing, `try`/`catch` syntax, distributed handler state or callable
  category is introduced. Future resumable recovery/restarts/effects, new
  executable value kinds, generic control-region callable domains and static
  signature/arity introspection remain separate explicit design decisions.
  This governance classification changes no normative specification or
  implementation.

- D030 is `RATIFIED`. On 2026-09-08 the project owner explicitly approved retaining
  D030 / specification `0.1.372` after comparison with Java, Scala, Rust, C#,
  JavaScript, Python, Self and Smalltalk plus adversarial and future-scalability
  review. `Future.then(transform)` continues to accept the existing ordinary-
  invokable protocol rather than requiring semantic Closure identity or creating a
  hidden Callback/Function category. After ordinary receiver/argument evaluation,
  the exact transform value is eagerly validated by read-only ordinary `call` lookup
  before any destination Future, continuation task, registration or scheduling state
  is created, independently of whether the source Future is pending, resolved, failed
  or cancelled. This validation does not invoke the transform and does not preflight
  declared arity, defaults or rest binding. It also does not pin/cache the Closure
  selected during inspection: when a resolved source later runs the continuation,
  ordinary polymorphic invocation performs a fresh `call` lookup, so legitimate
  intervening mutation or shadowing remains observable. A program that wants to keep
  a currently selected behavior can explicitly retain the ordinary extracted Closure
  instead of receiving implicit `then`-specific capture semantics. This keeps dataflow
  composition distinct from the separately ratified D034 Closure-only dynamic-control
  boundary and leaves future executable kinds, static signature introspection, remote
  continuation transfer and other callable institutions as separate explicit designs.
  This governance classification changes no normative specification or implementation.

- D032 is `RATIFIED`. On 2026-09-08 the project owner explicitly approved retaining
  D032 / specification `0.1.370` after comparative review across JavaScript, Python,
  Ruby, Smalltalk, Self, Java/.NET and Go plus dedicated future-scalability analysis.
  Every successful `slotNames()` invocation continues to return one fresh identity-
  bearing ordinary standard Array snapshot, including repeated observations of an
  unchanged receiver and empty results. Independently returned reflection Arrays are
  semantically distinct and own independent indexed mutable state: mutating one cannot
  mutate the reflected receiver, another reflection result or a later observation.
  D032 does not add deep-copy semantics for contained slot-name Strings and does not
  change the pre-existing shallow snapshot contents or deterministic ordering rule.
  Fresh identity constrains observable semantics rather than physical storage, so lazy,
  virtual, persistent, shared immutable backing, copy-on-write, scalar replacement and
  allocation-elision strategies remain permitted when identity and state independence
  stay exact. The simple reflection query remains detached ordinary data rather than a
  live view, Mirror, iterator-only API or observable result cache; future incremental
  reflection or explicit reflective-authority mechanisms remain separate designs. This
  governance ratification does not assert current runtime implementation completeness;
  any `slotNames()` implementation/reconciliation gap remains separate implementation
  work. No normative specification or implementation change is introduced by this
  classification.

- D025 is `RATIFIED`. On 2026-09-08 the project owner explicitly approved retaining
  D025 / specification `0.1.366` after comparative review spanning Smalltalk, Self,
  JavaScript, Ruby and Python plus adversarial and future-scalability analysis. Core
  v0.1 keeps the standard Closure-specific `future` and `parallel` selectors as
  ordinary local Closure-valued slots of `Object`; Core Closures reach them through
  the independently ratified D027 direct parent edge to `Object`. D025 adds no
  standard `Closure` or `Callable` prototype, hidden method table, per-Closure slot
  materialization or second dispatch path. The standard behaviors have the semantic
  Closure family as receiver domain: non-Closure receivers may find the inherited
  selector by ordinary lookup, but invoking that selected standard behavior signals
  the ordinary invalid-receiver Error before Task/Future creation or isolated-P
  projection/transfer/execution and does not resume lookup. `future` and `parallel`
  remain ordinary non-reserved names with ordinary nearer-slot shadowing and
  user-defined override contracts; member reads reuse the existing receiver-bound
  Closure extraction and `methodHome` semantics rather than creating an async-method
  value kind. `FUTURES_AND_TASKS.md` continues to own asynchronous Future/task
  behavior after receiver validation and `PARALLEL_EXECUTION.md` continues to own
  isolated-parallel behavior; host scheduler/worker machinery is implementation-only.
  The ratification is bounded to D025 `future`/`parallel`: it does not ratify D024 or
  D026, does not retroactively own the independently decided `ensure`/`while`
  selectors, does not make every ordinarily invokable object asynchronously/parallel
  executable, and is not a permanent prohibition on a future explicitly approved
  Closure/callable prototype or other abstraction if later requirements justify one.
  This governance classification changes no normative specification or implementation.

- D020 is `RATIFIED`. On 2026-09-08 the project owner explicitly approved
  retaining D020 / specification `0.1.359` after cross-language comparison,
  adversarial review and future-scalability analysis. Core v0.1 continues not
  to standardize `uppercase()` or `replace(...)` as String operations. String
  immutability remains the Core invariant: an operation that produces different
  text produces a new semantic String value, while libraries, implementation
  extensions and ordinary user objects remain free to define transformation
  names through normal slots and invocation. The ratified boundary deliberately
  avoids importing one mandatory Core contract for Unicode case mapping/version
  evolution, locale tailoring, normalization, literal-versus-pattern matching,
  overlap, empty-pattern/needle behavior, replacement callbacks or streaming and
  resource policy. This is a Core-boundary decision, not a rejection of useful
  text APIs: casing, case folding, replacement, split/join, normalization,
  collation, locale, pattern facilities and streaming transformations remain
  separately designable future Standard Library or later-version work. The later
  LIB002 text/encoding audit independently preserves the same separation but does
  not become normative authority for D020. This governance ratification changes
  no normative specification or implementation.

- D031 is `RATIFIED`. On 2026-09-08 the project owner explicitly approved retaining
  D031 / specification `0.1.368` after comparative, adversarial and future-
  scalability review spanning JavaScript Promises, Java CompletableFuture/
  CompletionStage, Python asyncio/coroutines, Kotlin Job, .NET Task/ValueTask
  and Rust Future models. D031 is retained as the idempotent-lifecycle
  specialization of the already-ratified D036 Future-result identity rule:
  every successfully dispatched invocation of a standardized Future-returning
  idempotent lifecycle operation has its own fresh semantic standard Future
  identity unless the operation expressly returns an existing Future, while
  repeated `close()`, `shutdownRead()` and `shutdownWrite()` calls observe one
  irreversible logical lifecycle and never begin independent retry attempts.
  Calls made while pending and after terminalization observe that lifecycle's
  single logical success/failure outcome; where the lifecycle records a stable
  terminal Error cause, every same-domain re-observation preserves that exact
  Error object. Fresh Future identity remains distinct from lifecycle identity
  and outcome identity and is a semantic guarantee rather than a physical-
  allocation mandate: implementations may use completion-state sharing,
  virtualization, scalar replacement, pooling or other invisible representation
  optimizations while preserving Future identity and Future-local effects. No
  canonical lifecycle Future, Future subtype, wrapper, hidden lifecycle token or
  distributed Future identity is introduced. A future API that genuinely needs
  to return one pre-existing/shared Future remains expressible through D036's
  existing explicit-result exception. The current runtime follower-list
  representation is implementation machinery and may be optimized separately;
  its retention characteristics are not ratified as language semantics. This
  governance classification changes no normative specification or implementation.

- D027 is `RATIFIED`. On 2026-09-08 the project owner explicitly approved retaining
  D027 / specification `0.1.365` after comparative review spanning JavaScript, Self,
  Smalltalk, Lua and Python plus adversarial and future-scalability analysis. Core
  v0.1 keeps one portable default topology rule: a Core-standard visible object whose
  immediate parent is not fixed by a more-specific normative owner delegates directly
  to the unique root `Object`, and `Object` itself has no parent. Explicit semantic
  topology remains authoritative where specified, including numeric and Error
  hierarchies, Context ancestry, semantic String/Future value parentage, factory-result
  parentage and domain-owned capability prototypes. Canonical `true`, canonical
  `false`, canonical `null` and every Closure remain direct children of `Object`; Core
  v0.1 introduces no organizational `Boolean`, `Closure`, `Value`, `Collection`,
  `Callable` or `AsyncValue` ancestor solely to classify values. Standard `Array`,
  `Map`, `IdentityMap`, `Future` and other ordinary Core prototype objects likewise
  delegate directly to `Object` unless a narrower owner specifies otherwise; in
  particular, `IdentityMap` does not implicitly inherit `Map`. Semantic-family
  membership remains distinct from delegation, and implementations may use arbitrary
  hidden host/JIT hierarchy or metadata only when `parent()`, lookup, `super` and
  reflection cannot observe an extra Protos ancestor. This is the selected Core v0.1
  topology, not a permanent ban on a future explicitly approved prototype category,
  trait/multiple-delegation system or other object-model evolution. Ratifying these
  D027 parent edges does not ratify D025 or D026 by transitivity; their remaining
  semantics keep their own AUD001 provenance/decision status. This governance
  classification changes no normative specification or implementation.

- D029 is `RATIFIED`. On 2026-09-08 the project owner explicitly approved
  retaining D029 / specification `0.1.368` after cross-language comparison,
  adversarial review and future-scalability analysis. For every Core-standard
  contract whose normal result or Future resolution is specified simply as
  `Integer`, without naming a more-specific numeric family, the result remains
  an ordinary unbounded Integer. This rule is deliberately result-only: it does
  not narrow an operation's accepted input domain, and a contract that explicitly
  names `UInt8`, `Int64` or another numeric family continues to own that result
  family. The rule keeps host pointer/index width, collection backing limits and
  small/big/tagged/machine-word representation out of portable semantics; such
  representations may still be used, changed, scalar-replaced or elided when
  unobservable. It therefore scales from ordinary counts to large/virtual data
  models without introducing host-shaped `size_t`/Int32/Int64 choices or new
  `Size`, `Natural` or `Index` semantic families merely to represent a count. A
  future API for which width is itself semantic remains free to name the exact
  fixed-width family explicitly. This governance ratification changes no normative
  specification or implementation and does not classify D030 or D031.

- D028 is `RATIFIED`. On 2026-09-08 the project owner explicitly approved
  retaining D028 / specification `0.1.369` after cross-language comparison
  and dedicated future/scalability review. The general Core `parent()`
  selector remains structural reflection of the receiver's immediate
  delegation parent; standard `Path` does not overload or shadow it with
  filesystem/path-construction behavior. `path.parentComponent()` remains
  the distinct immutable Path constructor that appends exactly one semantic
  parent-traversal component. That operation does not resolve a Filesystem,
  compute a lexical containing path, drop the preceding component, or
  lexically collapse the new parent component, and Core v0.1 retains no
  `Path.parent()` compatibility alias for traversal. This keeps ordinary
  object reflection uniform and preserves the structural Path model needed
  for capability-confined resolution in the presence of backend indirection.
  Future Path parsers/literals, lexical-parent/drop-last operations,
  normalization, first-class component APIs, multiple-delegation reflection
  and persistent/rope Path representations remain separate design or
  implementation choices rather than consequences of D028. The current
  component-list copying representation is not ratified as semantics and may
  be optimized invisibly. This governance classification changes no
  normative specification or implementation.

- D026 is `RATIFIED`. On 2026-09-08 the project owner explicitly approved
  retaining D026 / specification `0.1.364` after cross-language comparison,
  adversarial review and future-scalability analysis. Core v0.1 continues to
  define exactly the canonical singleton objects `true` and `false` as the
  Boolean semantic family and no standard prelude binding, object or prototype
  named `Boolean`. `Boolean` remains an ordinary, non-reserved identifier: a
  program or library may bind or shadow that name through ordinary lookup/slot
  mechanisms, but such an object does not thereby acquire Boolean-family
  membership. Delegation, copying, composition, freezing, similarly named
  protocol slots or implementation representation likewise cannot create a
  third Boolean value. The selected boundary introduces no truthiness, Boolean
  conversion constructor, nominal Boolean type descriptor or primitive/wrapper
  duality merely to organize two canonical values. Future Boolean utility
  libraries and explicit family/introspection mechanisms remain separately
  designable. This ratification does not classify D027: the immediate delegation
  parent of `true`/`false` and the general portable Core topology remain D027's
  separately audited decision. This governance classification changes no
  normative specification or implementation.

- D023 is `RATIFIED`. On 2026-09-08 the project owner explicitly approved
  retaining D023 / specification `0.1.363` after comparative review spanning
  Self, Smalltalk, JavaScript, Ruby, Python, Java/C#, Rust, Swift and Go plus
  adversarial and future-scalability analysis. The four Core slot-write
  expression forms `x: rhs`, `object.x: rhs`, `x = rhs`, and `object.x = rhs`
  continue to return, after a successful write, the same exact object produced
  by RHS evaluation and stored by that write. The expression result is not
  canonical `null`, not the target/receiver and not a value obtained by reading
  the slot again; no result-forming conversion, copying, canonicalization or
  wrapping is introduced. This preserves identity-bearing RHS objects exactly
  and avoids a second observation/race window after mutation. If required target
  or RHS evaluation transfers control, or the subsequent slot write fails or
  transfers control, the expression has no normal result; already-completed
  evaluation effects are not rolled back. D023 does not change destination
  selection, delegation or object-state/write-validity rules. Indexed assignment
  `object[index] = value` remains a separate indexing-protocol operation and is
  not classified by this rule. Future computed properties, validation setters or
  other write protocols remain separately designable rather than retroactively
  changing ordinary slot-write semantics. The exact-RHS requirement is semantic,
  not a physical representation mandate: JIT/SSA reuse, result elision and slot
  storage optimizations remain free when observable identity, effects and failure
  behavior remain exact. This governance classification changes no normative
  specification or implementation.

- D021 is `RATIFIED`. On 2026-09-08 the project owner explicitly approved
  retaining D021 / specification `0.1.358` after comparative review spanning
  Akka, Orleans, Java RMI, Erlang, E/object-capability systems and Cap'n Proto
  plus adversarial and future-scalability analysis. D021 keeps three identities
  distinct: target Group identity, semantic GroupRef capability identity and
  physical proxy/wrapper/address/cache representation. A particular acquired
  GroupRef remains the same identity-bearing communication capability across
  ordinary Actor/Process transfer, repeated transfer, round-trip return and
  rematerialization. Any materialization of that same semantic GroupRef therefore
  preserves primitive `===`, `identityHashOf(...)` and IdentityMap key identity
  even if the runtime uses different physical proxies. Conversely, independently
  acquired GroupRefs are not collapsed merely because they denote the same Group
  or currently carry equivalent effective communication permissions; distinct
  effective capability/restriction state necessarily denotes distinct GroupRef
  identity. The selected rule does not make Group identity, routing or membership
  state observable through reference identity and requires no global interning,
  permanent wrapper registry, stable address, Group-to-GroupRef registry, proxy
  cache lifetime tied to Group lifetime or network coordination. Retaining or
  transferring a GroupRef does not extend Group lifetime or grant Group/Cluster
  Authority. Future attenuation/revocation, discovery/reacquisition, durable
  service naming and any explicit target-equality/joining facility remain
  separately designable rather than being hidden inside `===`. This governance
  classification changes no normative specification or implementation and does
  not classify D024.

- D004 is `RATIFIED`. On 2026-09-08 the project owner explicitly approved
  retaining D004 / specification `0.1.342` after comparison with Self,
  Smalltalk, Java, Python, Ruby and ECMAScript plus adversarial and dedicated
  future-scalability review. The standard Boolean protocol remains the four
  ordinary one-argument messages `ifTrue`, `ifFalse`, `and` and `or`. When
  ordinary lookup selects standard Boolean behavior, the original receiver must
  be exactly canonical `true` or canonical `false`; a custom object may define
  or override the same selectors without becoming a semantic Boolean and without
  creating language-wide truthiness. Receiver and argument expressions retain
  ordinary left-to-right call evaluation. A callback is callability-validated
  only on a path that selects it, then invoked exactly once with zero positional
  arguments through the ordinary polymorphic invocation protocol; it need not be
  a Closure. An unselected callback object is neither validated nor invoked, while
  the argument expression that produced it was still evaluated ordinarily.
  Standard `ifTrue`/`ifFalse` return the selected callback's exact normal result
  unchanged and return canonical `null` on the unselected path. Standard `and`/
  `or` short-circuit and, when they invoke the callback, require its normal result
  to be exactly canonical `true` or canonical `false`; there is no truthiness
  conversion, coercion, implicit invocation, implicit awaiting or Future adoption.
  Error signaling, non-local return, cancellation and other non-normal control
  transfers propagate exactly as from ordinary invocation, and the Boolean
  protocol introduces no hidden task, lock, suspension point or scheduling
  boundary. Grammar-owned `&&` and `||` retain the one mandatory lowering
  `a.and(() => b)` / `a.or(() => b)`: standard Booleans therefore short-circuit
  through the same message/Closure mechanisms, while a custom receiver may observe
  the generated RHS Closure through ordinary dispatch. Implementations may inline,
  constant-fold, eliminate unobservable Closure allocation or otherwise specialize
  canonical Boolean cases only when the full observable protocol remains exact.
  Future `if`/`else` sugar, additional callable kinds and unrelated Boolean-family
  surface decisions remain separate work rather than alternate truthiness/control
  semantics. This governance classification changes no normative specification or
  implementation and does not classify any other Dxxx decision by transitivity.

- D007 is `RATIFIED`. On 2026-09-08 the project owner explicitly approved
  retaining D007 / specification `0.1.346` after a corrected review (the prior
  audit turn had misidentified D007), with comparison against ECMAScript,
  Python, Go and Java plus package-system, adversarial and future-scalability
  analysis. `import(specifier)` remains an ordinary call whose evaluated
  argument must be exactly a semantic String. Core performs no coercion,
  formatting, `toString` dispatch, duck typing, implicit invocation or
  String-like delegation to manufacture the specifier, and invalid non-String
  values fail with the ordinary Core Error before resolver entry. Core passes
  the exact String value across the host/module-resolution boundary without
  assigning intrinsic path, URI, URL, package, filesystem or registry semantics,
  without normalization/rewriting and without universally rejecting the empty
  String. The host resolver owns interpretation, policy, authority and
  canonicalization of that text. Successful resolution yields the canonical
  ModuleKey from which Core module identity, Actor-local caching,
  cache-before-execute, initialization and cycle semantics proceed; the original
  spelling is not module identity, so distinct spellings may converge on one
  key. A valid String that cannot be resolved produces a language Error, not a
  host exception/status/sentinel, and failed resolution creates/caches no module
  instance. Import validation/resolution adds no implicit Protos suspension,
  Future, scheduler or re-entry boundary. The later package architecture
  validates the scalability of this boundary: dependency/version selection,
  registries/network acquisition, immutable artifact fetching and exact
  execution-plan preparation remain explicit host/tooling work, while the
  execution-time resolver maps already-valid requests to canonical ModuleKeys.
  Future genuinely asynchronous module/artifact acquisition remains separately
  designable rather than a retroactive reinterpretation of ordinary Core import.
  This governance classification changes no normative specification or
  implementation and does not classify D006 or D008 by transitivity.

Required procedure: 1. Continue backwards through the remaining unresolved decisions in D044-D040, and finally D001-D003 and D005-D006 and D008-D019.
2. For each decision, reconstruct the alternatives, recommendation, published normative result, downstream implementation, and owner-approval evidence.
3. Classify it as RATIFIED, NEEDS_USER_DECISION, SUPERSEDED, or PROVENANCE_UNRESOLVED. Executing or publishing a patch is not sufficient approval evidence.
4. Present every substantive unresolved choice to the project owner under the current explicit design-approval gate. Do not silently preserve, replace, or reopen semantics.
5. Keep D046 outside AUD001 and do not let this audit overwrite or pre-empt its separate review. Next audit work: 1. Complete the separate D044 review already in progress, considering D045 only as a ratified dependency where their semantics interact.
2. Continue backwards through the remaining unresolved decisions in D043-D040, then D001-D003 and D005-D006 and D008-D019 under the required procedure above. AUD001 closes only when D001-D003 and D005-D006 and D008-D020, D025, D027-D028 and D030-D045, except D046, have an explicit classification,
the project owner has decided every NEEDS_USER_DECISION item, relevant
provenance is recorded durably, and all affected project ledgers are reconciled.
Any later normative correction must be a separately approved specification
change; AUD001 itself authorizes no specification or implementation change.
