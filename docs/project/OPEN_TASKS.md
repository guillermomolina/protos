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

### AUD003 — Protos source-style conformance audit

Status: **IN PROGRESS**
Priority: **NORMAL**
Nature: non-normative repository source-style audit and bounded migration

Track repository-wide conformance with the already-approved idiomatic Protos
source-style policy instead of performing isolated syntactic-sugar rewrites with
no durable owner. The audit distinguishes ordinary style debt from deliberate
canonical/protocol spelling and forbids inferred equivalences such as treating
`div` or generic `add` as surface sugar.

`AUD003-A1` is already CLOSED by historical publication
`SOURCE-STYLE-INDEXING-A1` at `a848371a6428cb1d80b688a9a27e6b74ec61f28a`, covering user-facing examples and
tutorials. `AUD003-A2` is READY for the Standard Library indexing audit; later
partitions cover bundled tools, benchmarks/ordinary programs, lazy Boolean and
unary forms, conformance-test exception classification, a prevention gate, and
final integrated closure.

No language/specification decision is reopened by AUD003. See
`docs/project/AUD003_PROTOS_SOURCE_STYLE_CONFORMANCE_AUDIT.md` and
`docs/guide/SOURCE_STYLE.md`.

### AUD002 — GraalVM / Truffle editor-tooling compatibility audit

Status: **CLOSED**
Priority: **NORMAL**
Nature: non-normative implementation/tooling architecture audit

Evidence collection completed in the published AUD002 audit. On 2026-09-08 the
project owner explicitly approved Alternative C, the hybrid Truffle-first
architecture: Protos will expose its real runtime through standard Truffle
language/source/instrumentation/interop mechanisms, prove GraalVM DAP and dynamic
LSP reuse on actual Protos execution, keep static language intelligence tied to
the real Protos parser/resolver, and keep editor integration thin rather than
duplicating Protos semantics in TypeScript.

The runtime/compiler foundation is allocated as `I026` and starts `READY`.
`TOOL003` is not allocated by this decision. Public CLI UX, the VS Code extension
and a Protos-specific static language service remain separately classified future
work after I026 produces real compatibility evidence.

See `docs/project/AUD002_GRAALVM_EDITOR_TOOLING_AUDIT.md` and
`docs/project/I026_TRUFFLE_TOOLING_FOUNDATION.md`.

### AUD001 — Retrospective design-decision ratification audit

Status: **CLOSED**
Priority: **HIGH**
Nature: non-normative governance and provenance audit

Audit the provenance and continued suitability of design decisions D001-D045,
excluding D046 because it is already under separate active user review. The
purpose is to distinguish explicit project-owner selection from agent-authored
recommendations, broad implementation instructions, patch execution, and
publication evidence.

Closure result:

- D001-D045 all have an explicit AUD001 classification and every project-owner
  decision required by this audit is resolved.
- D044 is the final classification and is `RATIFIED` by explicit project-owner
  approval on 2026-09-08 after comparative, adversarial and future-scalability
  review; specification `0.1.381` is retained without normative amendment.
- D046 remains outside AUD001 and is not classified by this audit.

Recorded review results:

- D044 is `RATIFIED`. On 2026-09-08 the project owner explicitly approved
  retaining D044 / specification `0.1.381` after retrospective comparison with
  Self, Smalltalk/Pharo, Kotlin, Scala, Rust, JavaScript and Ruby plus adversarial
  and future-scalability review. Core v0.1 keeps one pre-test loop as the ordinary
  Closure-specific message `condition.while(body)`: `while` is an ordinary local
  Closure-valued `Object` slot, the standard behavior requires a semantic Closure
  receiver and exactly one semantic Closure body, and ordinary lookup, reflection,
  extraction, shadowing and user overrides remain authoritative. Receiver/argument
  evaluation and standard receiver/exact-arity/body validation complete before
  the first condition activation; condition and body are activated with zero
  supplied arguments only when reached, and callback-declared arity/default/rest
  behavior remains ordinary activation-time binding. Each condition result must
  be exactly canonical `true` or `false`; every other normal result, including a
  Future, signals a fresh standard Error rather than truthiness, coercion, implicit
  invocation, awaiting or Future adoption. Canonical `false` terminates, canonical
  `true` activates the body and repeats, body normal results are ignored, and every
  normal loop completion returns canonical `null`. Error, valid non-local return,
  InvalidReturn, suspension/replay, cooperative cancellation, `ensure` and task/
  Future ownership compose through their existing rules; `while` adds no hidden
  task, Future, handler, cleanup scope, scheduler boundary, cancellation mask,
  polling/preemption point or implicit asynchronous scope. D045 remains the
  independent owner of task-scoped structured Future ownership and confirms that
  synchronous `while` callback activations are not concurrency scopes. I023's
  published implementation/conformance closure, including bounded replay retention
  whose retained state depends on the active callback trace rather than completed
  iteration count, supplies downstream scalability evidence without constraining
  D044 to that implementation shape. Implementations remain free to inline,
  specialize or compile the loop while preserving observations. Future `while (...)`
  syntax, loop-local `break`/`continue`, value-producing loops, pattern loops and
  async-specific loop facilities remain separate explicit designs. The original
  normative D044 publication is commit
  `4ab6c48d31a969ac0d8ad050bf3fb3beaab6c62b`. This governance classification
  changes no normative specification, implementation, blocker, implementation
  version, runtime, native boundary or license terms and creates no implementation
  follow-up.

- D019 is `RATIFIED`. On 2026-09-08 the project owner explicitly approved
  retaining D019 / specification `0.1.360` after retrospective reconstruction,
  comparison with Erlang/OTP, Akka Classic/Typed, Ray, Orleans and Pony plus
  adversarial, capability-security, distributed and future-scalability review.
  Actor creation genealogy, communication capability and failure/lifecycle
  authority remain separate relationships. `Actor.spawn(...)` gives the creator
  the new concrete Actor's `ActorRef`, but creation alone gives the created Actor
  no `parentActor`, creator lookup, reverse `ActorRef`, durable implicit reply
  channel or other authority toward the creator. A capability back to the creator
  must be provisioned explicitly through an existing permitted mechanism, for
  example by passing `Actor.current()` in initialization data; that value is then
  an ordinary ActorRef with no special parent identity or lifetime semantics.
  Absence of ambient creator authority is transitive and also applies when
  creation is coordinated by bootstrap, Group reconciliation, runtime
  infrastructure or remote placement. Implementations may retain genealogy,
  placement, scheduling, diagnostics or failure-accounting metadata internally,
  but such metadata does not become a Core capability and need not synthesize
  reverse routing or program-visible lifetime retention. Creator termination
  alone does not terminate a child, and failure authority remains independently
  owned. D019 introduces no Supervisor/SupervisorRef, actor ownership/fate-sharing
  scope, link/monitor API, durable service identity, replacement-following
  reference, creator-tree reflection or new capability kind; those remain
  separate explicit future designs. The original normative D019 publication is
  commit `9cb567a0626414e2dac7351fc49174b3dc9cb581`. This governance classification changes no normative
  specification, implementation, blocker, implementation version, runtime,
  native boundary or license terms and does not classify D018 by transitivity.

- D018 is `RATIFIED`. On 2026-09-08 the project owner explicitly approved
  retaining the canonical Process-bootstrap snapshot identity decision after
  cross-language, concurrency, isolation, distribution, adversarial and
  future-scalability review. The normative semantics were originally published
  in `spec/io/PROCESS_IO.md` by commit
  `8a27fbfb3519126fecf3559a74f9ce62116d6ec6`; specification revision `0.1.361`
  subsequently recorded that already-published decision administratively as
  D018 in commit `580f79bb39ed468a7e3eac27d25c0b28a7ba42df`.
  Each logical Process continues to have exactly one canonical identity-bearing
  `process.args()` snapshot and one distinct canonical identity-bearing
  `process.environment()` snapshot for its lifetime. Repeated successful
  acquisition through any Actor-local Process capability/proxy denoting that
  same logical Process preserves the corresponding semantic identity, so the
  ordinary `===`, `identityHashOf`, default equality/hash and `IdentityMap`
  consequences follow without a special snapshot-identity subsystem. Equal
  contents belonging to distinct Processes do not merge semantic identity.
  Canonical semantic identity does not require one permanent physical wrapper,
  stable address or global registry: immutable backing may be shared and
  wrappers may be lazily materialized, cached, evicted/rematerialized,
  virtualized, scalar-replaced or moved when all semantic identity observations
  remain exact. Canonical acquisition remains distinct from value transfer:
  when an already-acquired snapshot crosses an Actor or P isolation boundary
  under an independently applicable pass-by-value rule, that boundary's ordinary
  destination-identity semantics remain authoritative; acquiring the snapshot
  through a delegated Process capability is not such a copy. D018 grants no new
  Process, Actor or P transferability and creates no canonical identity relation
  among separate Error occurrences when bootstrap acquisition fails. Standard
  stream capability/view identity remains separately owned and is not
  reclassified by this decision. Future live/native environment inspection,
  child-process environment construction, checkpoint/restore policy and other
  Process facilities remain separate explicit designs. This governance
  classification changes no normative specification, implementation, blocker,
  implementation version, runtime, native boundary or license terms and creates
  no implementation follow-up.

- D017 is `RATIFIED`. On 2026-09-08 the project owner explicitly approved
  retaining D017 / specification `0.1.357` after retrospective reconstruction,
  cross-ecosystem comparison and dedicated adversarial/future-scalability review
  spanning Erlang/OTP, Akka and Orleans-style Actor lifecycle/supervision models.
  D017 is retained as an Actor API-boundary cleanup, not as a transitive
  re-ratification of the surrounding Actor model. Portable Core `SendOperation`
  continues to expose only the minimal `cancel()` / `retry()` control surface;
  status, progress, waiting, attempt counts, destination IDs, last transport
  errors and transport introspection remain non-portable diagnostics unless a
  separately approved facility standardizes them. Delivery uncertainty remains
  distinct from proof of non-delivery and never authorizes transparent replay;
  explicit retry creates the separately specified fresh operation over the
  already-formed message snapshot. Graceful lifecycle control remains separated
  from observation: `ActorRef.stop()` returns canonical `null` and creates no
  dedicated stop Future/operation, while `ActorRef.termination()` is the
  independent Future-based observation mechanism. Core v0.1 continues to expose
  no configurable public supervisor/failure-authority policy API. An Actor
  incarnation that fails terminates; `restart` is not identity-preserving Actor
  lifecycle semantics, and any replacement is a distinct Actor/ActorRef that does
  not inherit the failed incarnation's identity, mutable heap, pending
  interactions or mailbox. This does not prohibit a future explicitly designed
  Supervisor/Controller, durable service/role identity, restart policy,
  discovery/rebinding, diagnostics/tracing or recovery layer; each must define
  its own authority, lifetime, transfer, ordering, failure and distribution
  semantics rather than retroactively retargeting an ActorRef. The original D017
  normative cleanup publication is commit
  `0a5a54fdf9e6e24eb0f8205a2586321fde285bb1`. This governance classification
  changes no normative specification or implementation and does not classify
  D015, D016, D018 or D019 by transitivity.


- D016 is `RATIFIED`. On 2026-09-08 the project owner explicitly approved
  retaining D016 / specification `0.1.354` after comparison with Erlang/OTP,
  Akka Typed, Ray, Orleans and Elixir/GenServer plus adversarial, distributed and
  future-scalability review. `Actor.spawn(...) -> ActorRef` remains the sole Core
  Actor-creation result. Creator-side module resolution, argument validation and
  complete transfer/delegation occur synchronously before the semantic creation
  cutover; failure there creates no observable partial Actor. At the cutover
  exactly one Actor incarnation, incarnation identity and `ActorRef` exist, and
  the returned reference denotes that same incarnation for its lifetime. Runtime
  placement/admission, bootstrap execution and the `READY` transition occur after
  that cutover and cannot replace the result with a `SpawnOperation`, Future,
  admission handle, placement handle or other public coordination identity.
  Temporary capacity shortage delays progress of the already-created incarnation
  through `INITIALIZING`; it is not a synchronous `spawn` failure, does not imply
  unlimited oversubscription, and does not create an implicit timeout, deadline
  or creation-specific cancellation protocol. `ActorRef.stop()` remains the
  ordinary public termination request even while initialization is pending.
  Pre-`READY` send/request activity uses the existing bounded routing,
  acceptance, backpressure, cancellation, failure and uncertainty rules, with no
  bootstrap mailbox or second message-delivery universe; application behavior is
  not dispatched before `READY`. Admission retains weak fairness for a
  continuously eligible incarnation when compatible opportunities recur, while
  queueing, scheduler, placement, resource-accounting and provisioning machinery
  remain unobservable implementation choices. Group reconciliation may count a
  known live in-flight Actor candidate toward one observed membership deficit
  without making that candidate routing-eligible before ordinary readiness and
  membership eligibility. Future readiness observation, explicit placement or
  reservation facilities, admission deadlines/priorities, virtual/durable Actor
  layers, autoscaling controls and infrastructure-controller APIs remain separate
  explicit designs rather than retroactive changes to Core `spawn`. The original
  normative D016 publication is commit
  `d4e20835078429b291db00aad4554dab3218a473`. This governance classification
  changes no normative specification, implementation, blocker, implementation
  version, runtime, native boundary or license terms, creates no implementation
  follow-up, and does not classify D015 or D017-D019 by transitivity.

- D014 is `RATIFIED`. On 2026-09-08 the project owner explicitly approved
  retaining D014 / specification `0.1.356` after recovered historical owner
  selection plus renewed cross-language, adversarial and future-scalability
  review spanning Python methods/properties/descriptors, JavaScript methods and
  accessors, C#/Kotlin properties, Java ordinary methods, and Self/Smalltalk/Ruby
  message models. Standard computed operations such as `size`, `hash`, and the
  exposed `identityHash` convenience remain ordinary zero-argument Closure-valued
  slots invoked with parentheses. An ordinary member read such as `obj.hash`
  retrieves/extracts the selected value and never auto-invokes it; `obj.hash()`
  performs ordinary invocation. Ordinary lookup and shadowing remain decisive:
  if a nearer slot with the selected name contains a non-invokable value, reading
  that value succeeds normally while parenthesized invocation fails under the
  ordinary invocation contract and does not resume lookup at a more distant
  homonymous slot. `identityHashOf(value)` remains the separate non-overridable
  primitive semantic operation used by identity-sensitive machinery including
  `IdentityMap`; ordinary overridable `identityHash()` is only a convenience
  message and cannot redefine semantic identity hashing. D014 introduces no
  getter/property/descriptor category, zero-argument auto-call rule, hidden
  built-in exception or second invocation protocol. Immediate invocation may be
  specialized, inlined or avoid materializing an unobservable extracted Closure
  when all lookup/receiver/result/error semantics remain exact. A future explicit
  property/computed-slot/descriptor facility remains separately designable and
  does not retroactively change these existing selectors. The original normative
  publication is commit `a113d4d66a253e691f8f09b1fc1b978b19a66cc3`.
  This governance classification changes no normative specification or
  implementation and does not classify D013 or D015-D019 by transitivity.


- D013 is `RATIFIED`. On 2026-09-08 the project owner explicitly approved
  retaining D013 / specification `0.1.352` after comparison with Python,
  ECMAScript, Kotlin, Scala, Ruby, Lua, Self and Smalltalk/Pharo-style object
  construction plus adversarial and future-scalability review. Parenthesized
  invocation remains one ordinary structural protocol: after ordinary target and
  argument evaluation, lookup selects the nearest ordinary `call` slot through
  the receiver's delegation chain; the selected value must be a semantic Closure,
  and that already-selected Closure is then activated directly as the terminal
  executable operation with the original invocation receiver as `this` and the
  selected slot owner as `methodHome`. This is not a source-level recursive
  rewrite through `call.call...`, and Core adds no hidden callable bit/property,
  callable registry, metatable/metaclass route, `Callable` hierarchy, `Method`
  value kind or second dispatch universe. A `call` slot remains fully ordinary:
  it may be inherited, shadowed (including by a non-Closure value that makes that
  invocation fail), copied, composed, assigned, aliased and read/extracted under
  the normal slot and Closure-binding rules; reading `obj.call` never invokes it.
  Non-executing callability inspection remains exactly read-only ordinary `call`
  lookup plus Closure-value validation, performs no activation/arity/default work
  and does not pin the selected behavior for a later invocation. The inherited
  ordinary `Object.call` continues to unify standard Closure execution with
  default prototype construction: Closure receivers execute their Closure value;
  other receivers construct a fresh child delegating to the receiver, send
  ordinary `init` with the supplied arguments, and return that child on normal
  construction. Standard Array/Map/IdentityMap/numeric factories continue to
  specialize through nearer ordinary `call` slots. D022's independently ratified
  initializer/result details remain independently owned and are not reclassified
  here. Future static callable types, overload/multimethod systems, RPC/remote-call
  policy, partial-application helpers or an explicitly justified future callable
  abstraction remain separate designs. The original normative D013 publication
  is commit `afe6c07557e630f6570404506fee7094a37ad72b`. This governance
  classification changes no normative specification, implementation, blocker,
  implementation version, runtime, native boundary or license terms and creates
  no implementation follow-up.

- D008 is `RATIFIED`. On 2026-09-08 the project owner explicitly approved
  retaining D008 / specification `0.1.347` after cross-language comparison,
  adversarial review and dedicated future-scalability analysis spanning
  Smalltalk/Traits, Self structural reuse, JavaScript own-property copying and
  PHP/Scala trait-style composition. This present approval reaffirms the complete
  D008 package for which explicit project-owner selection was also recovered
  before its original normative publication in commit `1f72b589d838c3f0633638b34d8cd12a2bb62f9f`.
  Standard `Object.without(name)` and `Object.alias(sourceName, aliasName)` remain
  ordinary messages over local slot structure only: semantic String names are
  required, delegated lookup and coercion do not participate, `without` excludes
  one required local binding, and `alias` adds a second local name while
  preserving the source name and rejecting local collisions. Successful results
  remain fresh identity-bearing open ordinary objects whose immediate delegation
  parent is `Object`; the source parent and open/closed/frozen state are not
  copied. Retained and aliased bindings are shallow and preserve the exact stored
  value objects, including Closure identity. `alias` does not clone, re-home or
  rebind a Closure; ordinary later lookup/invocation on the eventual receiver
  establishes `this` and `methodHome`. No delegated bindings are materialized, no
  source-to-view live dependency is created, and no Trait/TraitView value kind,
  trait-specific syntax, deep-copy policy, precedence rule or global view/cache
  institution is introduced. The observable contract does not require physical
  O(n) copying: persistent maps, structural sharing, copy-on-write, shapes,
  virtualization, allocation elision or other representations remain permitted
  when fresh identity, independent mutable result state, reflection and exact
  stored-value identity remain observable as specified. Future first-class
  Traits/Roles, required-slot contracts, Symbol/Selector name domains, bulk
  structural transformations, multiple-delegation policy and shared-memory
  concurrency semantics remain separate explicit design decisions. This
  governance classification changes no normative specification or implementation
  and does not classify D002 or D005-D019 by transitivity.

- D010 is `RATIFIED`. On 2026-09-08 the project owner explicitly approved retaining
  D010 / specification `0.1.350` after comparison with Erlang/OTP, Akka Typed,
  Pony, Node.js, Julia, Rust/Rayon, E/object-capability systems, WASI and
  Capsicum plus adversarial and future-scalability review. Actor creation keeps
  the destination-loadable code-identity-plus-explicit-values model:
  `Actor.spawn(moduleSpecifier, bindingName, arguments...)` resolves one canonical
  module identity in the creator environment, transfers only the permitted
  initialization graph and returns the new incarnation's `ActorRef` without
  transferring a creator Closure/context or exposing scheduler/worker machinery.
  `Actor.current()` remains the minimal self-reference acquisition operation;
  `ActorRef.send`, `request` and graceful `stop` retain their ordinary public roles,
  while `SendOperation.cancel` / `retry` remain the deliberately small explicit
  control surface for pre-acceptance cancellation and user-selected retry rather
  than hidden replay/status/transport APIs. P remains a semantic isolated parallel
  execution domain entered through ordinary `Closure.parallel`,
  `Bytes.parallelRange` and `ByteRegion.parallelRange`; Core v0.1 gains no public
  `P` object, capability, namespace, keyword, worker/pool handle or syntax.
  `Process` remains an authority-free standard prototype, while the actual
  Process capability is provisioned only as the RootActor initial-module local
  `process` endowment. Imports and newly created Actors receive no Process
  authority implicitly; explicit Actor delegation rematerializes only the same
  logical authority without amplification; Process does not imply Filesystem,
  network, subprocess, Node, Cluster or arbitrary native authority and has no P
  transfer contract. D010's historical statements that the then-current surface
  was complete/exact are understood at the `0.1.350` publication point: they do
  not roll back or reclassify later independently owned compatible extensions
  such as `Actor.group(...)` and `ActorRef.termination()`. Future placement/resource
  Spawner capabilities, supervision/discovery, parallel-executor/QoS facilities,
  capability attenuation and similar mechanisms remain separate designs that
  must earn their own semantics. The original D010 publication is commit
  `c99d12625b5536927bb91782872194adc8d9e282`. This governance classification
  changes no normative specification or implementation and does not classify
  D008-D009, D011-D019, D039 or other later Actor/Process decisions by transitivity.

- D011 is `RATIFIED` in its effective current form. On 2026-09-08 the
  project owner explicitly approved retaining the public Filesystem/I/O surface
  introduced by specification `0.1.351` after comparison with WASI/capability
  filesystem design, Rust and Deno open options, Java NIO/Charset, Python pathlib
  and codecs, and wrapper-lifecycle approaches in Java, Rust and Go, plus
  adversarial and future-scalability review. `Path` remains a portable immutable
  authority-free value; Filesystem authority remains an explicit non-ambient
  capability with no standard public authority-manufacturing constructor, and an
  optional Root default is bootstrap-local rather than prelude/global/imported
  state. Portable `filesystem.open(path[, options])` keeps its closed six-slot
  local-only Boolean option surface, deterministic defaults, fail-closed unknown
  options/combinations and invocation-time semantic snapshot before asynchronous
  namespace/backend work. Portable Path construction remains synchronous and free
  of implicit String/native-path coercion; host separators, drives, UNC syntax and
  normalization do not enter portable Path identity. Standard Encoding values
  remain authority-free descriptors, and standard byte/text wrappers continue to
  borrow by default with `.owning(...)` as the explicit lifecycle-ownership form.
  D011 is not recorded as literally unchanged from `0.1.351`: its original
  `path.parent()` construction spelling was later superseded by independently
  ratified D028 / specification `0.1.369`, so the effective retained constructor
  is `path.parentComponent()` while ordinary `path.parent()` remains delegation
  reflection. That prior D028 change is not reopened or reclassified by this
  ratification. Future private/virtual Filesystem construction, additional open
  capabilities/options, codec discovery/registries, convenience Path parsers or
  native-path boundaries remain separate explicit designs. The original D011
  publication is commit `b9f97758fb5083957d44df827aac0c65ddd3d574`.
  This governance classification changes no normative specification,
  implementation, blocker, implementation version, runtime, native boundary or
  license terms and creates no implementation follow-up.

- D009 is `RATIFIED`. AUD001 recovered explicit project-owner approval of
  specification `0.1.349`, and on 2026-09-08 the project owner reaffirmed the
  decision after comparison with Java/JVMS, ECMAScript abstract operations,
  WebAssembly specification layering and Rust plus adversarial and
  future-scalability review. Every observable normative rule continues to have
  one primary owning specification/domain document. Other normative documents
  may reference and depend on that rule, define a genuine domain-specific
  specialization, or own additional cross-domain interaction semantics, but they
  do not duplicate the complete contract as an independent authority. Grammar
  remains the owner of syntax and mandatory lowering rather than semantic rules
  owned by other domains, and the current `runtime/ABSTRACT_RUNTIME.md` remains
  informative pseudocode that must follow normative owners rather than override
  them. This ratification does not freeze the current physical document split:
  ownership may be moved explicitly, documents may be split or merged, generated
  combined views may be added, and a future formal semantics may become normative
  through an explicit design change, provided the same observable rule does not
  acquire conflicting independent authorities. The original normative D009
  publication is commit `6d0606c19eea3c77bffd67a69a42417ec8f6e0b1`.
  This governance classification changes no normative specification,
  implementation, implementation version, runtime, native boundary or license
  terms and does not classify adjacent Dxxx decisions by transitivity.

- D005 is `RATIFIED`. On 2026-09-08 the project owner explicitly approved retaining D005 / specification `0.1.345` after cross-language comparison, adversarial review and dedicated future-scalability analysis. Every independent standard failure occurrence continues to produce a fresh ordinary Error identity delegating to the normatively promised prototype; standard Error prototypes remain shared category/protocol objects, never implicit singleton runtime-failure instances. When semantics already identifies an Error object `e`, signaling, re-signaling, handler delivery and same-domain recording preserve exactly `e`. A same-domain FAILED Future therefore re-signals its exact stored Error, while a CANCELLED Future stores only cancellation state and every `value()` observation creates its own fresh `Cancelled` occurrence. Error identity remains ordinary object identity within its value/isolation domain, with no global ID, interning registry or cross-domain identity channel; reconstruction boundaries use their ordinary graph-transfer rules and Actor-fatal Errors remain Actor-local unless another protocol explicitly transfers data. Core `Error.signal()` remains non-resumable, and category/handler matching remains ordinary delegation rather than a hidden tag/class matcher. The portable Core taxonomy stays deliberately shallow: D005's explicit Error/I/O categories remain portable, host errno/status/detail categories do not leak into Core ancestry merely because a backend exposes them, and retry safety remains a property of operation commitment/effects rather than Error prototype. Fresh semantic identity does not prescribe physical allocation, so scalar replacement/virtualization/elision remain permitted when identity cannot be observed. Diagnostics, cause/tracing facilities, explicit recovery/restart mechanisms, later Error prototypes and later handler/`ensure` clarifications remain separate decisions. The original normative D005 publication is commit `8563dcb670e538de3f54f60d1c8a67d0ec9a911e`; this governance classification changes no normative specification or implementation and does not classify D002/D006-D019 or later Error-design decisions by transitivity.

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
  treated as approval. `I025` is `CLOSED` in `0.2.281-SNAPSHOT` by parser/conformance implementation; the earlier governance/specification publication itself did not claim that implementation work complete. D002 and D004-D019 were reviewed independently under AUD001; this D003 classification did not classify them by transitivity.

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

- D012 is `RATIFIED`. On 2026-09-08 the project owner explicitly approved
  retaining D012 / specification `0.1.355` after comparison with Java, Rust,
  ECMAScript and Go plus adversarial and future-scalability review. D012 is an
  ownership/migration decision, not a transitive ratification of every historical
  String semantic rule moved by that revision. The current primary normative
  owner of Core String value/indexing behavior remains
  `semantics/VALUES_AND_COLLECTIONS.md`; String-literal source spelling, lexical
  validation and grammar remain owned by `PROTOS_GRAMMAR.md`; and the standard
  Encoding/text-to-bytes conversion contract remains owned by `io/TEXT_IO.md`.
  The former monolithic language-spec String headings remain navigation and
  compatibility anchors rather than a second normative authority. This preserves
  the semantic separation between abstract text values, source syntax and encoded
  byte representations and avoids duplicating Encoding policy across String and
  text-I/O domains. D012 does not classify D020 or, by transitivity, historical
  grapheme-indexing, Unicode-version, String identity/equality, concatenation or
  other String choices. It also does not freeze today's physical file names or
  granularity: future explicitly approved owner migrations may split, merge or
  rename documents coherently while preserving the independently ratified D009
  single-primary-owner invariant. This governance classification changes no
  normative specification or implementation and does not classify D011 or D013.

- D015 is `RATIFIED`. On 2026-09-08 the project owner explicitly approved
  retaining D015 / specification `0.1.353` after comparison with Erlang/OTP,
  Java, Smalltalk resumable exceptions and Akka supervision plus adversarial and
  future-scalability review. Actor code continues to use the single Core
  non-resumable Error protocol: selecting a matching handler abandons the
  continuation at `Error.signal()` and unwinds to the handler boundary; a normal
  handler result is the result of the enclosing `handle` operation and is never
  a replacement value injected at the abandoned signal point. A handled Error
  is not Actor failure merely because it occurred; only an Error that ultimately
  escapes the Actor turn unhandled reaches the fatal Actor-failure boundary.
  Mutation, I/O, messages and other effects completed before the Error retain
  their ordinary contracts, with no implicit rollback, replay or retry; any
  retry or compensation is a new explicit program action under the relevant
  operation's commitment semantics. An Error signaled by the selected handler
  follows ordinary outer-handler search rather than reusing that one-shot
  handler destination. Synchronous Actor Error escape remains distinct from an
  asynchronous child-Future failed outcome. D015 does not classify D017
  failure-authority/supervision policy, D034 exact handler validation/timing,
  D043 ensure/unwind cleanup, D045 structured task ownership, or future explicit
  restart, resumable-condition, transactional or compensation mechanisms.
  Those remain independent authorities/designs. This governance classification
  changes no normative specification or implementation and creates no
  implementation follow-up.

- D002 is `RATIFIED` in its effective current form. On 2026-09-08 the
  project owner explicitly approved retaining D002 after cross-language review
  spanning Self, ECMAScript, Python, Ruby, Lua, Elixir and Rust, plus adversarial,
  concurrency/distribution and future-scalability analysis against the Protos
  design philosophy. The effective contract is specification `0.1.348` as
  corrected by the explicitly owner-approved `0.1.387` assignment-timing
  amendment; the earlier RHS-before-destination wording is not retained.
  Ordinary bare reads continue to inspect only local slots of the current
  execution context and its lexical parents, then fall back after lexical
  exhaustion to ordinary member lookup from `this`, including receiver
  delegation. Bare creation `x: value` performs no lookup and creates only in the
  current execution context. Bare assignment `x = rhs` selects the nearest
  existing local lexical binding, otherwise only an own local slot of `this`;
  it never follows delegation and never creates. Selection is for the nearest
  existing binding rather than the nearest writable binding, so an invalid
  mutation fails at that selected binding instead of silently reaching farther
  state. The exact assignment destination is selected before RHS evaluation; a
  missing destination signals fresh `SlotNotFound` before RHS effects, and RHS
  creation, removal, shadowing, freezing or other same-name effects never
  retarget the in-progress assignment. After normal RHS completion the exact
  result is written to that preselected slot under ordinary mutation validation;
  control transfer performs no write, write failure does not resume lookup, and
  already-completed RHS effects are not rolled back. `this`, `context` and
  `args` remain execution-state intrinsics rather than bare-name bindings;
  current `super` semantics and later independently owned execution/control
  decisions are not reclassified by transitivity. The original D002 publication
  is commit `3f60e8fdc8ade1b856f2fd32114d2d429e715e58`; the timing correction is
  commit `28de4244ed1e3478cbae4bcf03c7af7d8da076f6`. This governance
  classification changes no normative specification, implementation, blocker,
  implementation version, runtime, native boundary or license terms and creates
  no implementation follow-up.

AUD001 closure:

D001-D045 all have an explicit classification under the required retrospective
procedure, every `NEEDS_USER_DECISION` decision presented by the audit has been
resolved by the project owner, and the final unresolved decision D044 is now
`RATIFIED`. D046 remains outside AUD001 and is not reclassified by this closure.
Relevant provenance is recorded in the per-decision entries above and the
canonical implementation-status ledger is reconciled in the same publication.

Any later normative correction must be a separately approved specification
change; AUD001 itself authorizes no specification or implementation change.
