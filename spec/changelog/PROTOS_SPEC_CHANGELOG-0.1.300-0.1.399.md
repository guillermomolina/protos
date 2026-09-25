# Protos Language Specification Changelog — 0.1.300–0.1.399

Archived from [`../PROTOS_SPEC_CHANGELOG.md`](../PROTOS_SPEC_CHANGELOG.md).

## [0.1.399] - 2026-09-11

### D083 — Standard composite capture composition
- Ratifies one-level D072 carrier concatenation for standard composites: each immediate child remains opaque and is consumed only through `child.match(childSubject)`; canonical `false` stops the composite as mismatch, canonical `true` contributes zero captures, and a non-empty child capture Array contributes exactly its top-level elements in carrier order.
- Captured values are never recursively flattened. An Array-valued capture remains one ordinary capture (`[[1, 2]]` is one captured `[1, 2]` value), preserving D072's existing `[[]]` distinction.
- Ordered standard children execute in deterministic semantic order, left-to-right for ordered child lists, exactly once until mismatch. Valid child carriers are shallowly consumed before the next child; invalid outcomes signal ordinary `Error`; Error/control/cancellation/explicit suspension propagate and prior effects are not rolled back.
- All-success with zero total captures returns canonical `true`; all-success with captures returns one standard non-empty Array containing the concatenated shallow capture sequence.
- Rest/repetition/whole-subject and similar aggregation remain explicit pattern-family behavior: a pattern that wants an aggregate to be one capture publishes that ordinary aggregate value as one D072 capture.
- No capture-tree reflection, fixed `captureArity`, capture-name Map, `CaptureFrame`, mutable sink/CPS path, second matcher authority, or mandatory signature protocol is introduced.

### Compatibility and implementation state
- Normative owner changed: `semantics/MATCHING.md`; adds durable non-normative D083 decision record under `docs/project/decisions/language/`.
- Existing D071-D075, D078, D080 and D081 matching semantics remain unchanged.
- Concrete match/arm grammar, source binding spelling and duplicate-name policy, alternative/or binding-interface mechanics, guards, exhaustivity, sequence/Map remainder semantics, repetition/optional/rest semantics, whole-subject alias syntax, identity-pattern syntax, and recognition-only fast paths remain unresolved.
- No parser, production implementation, Maven implementation version, native boundary, license term, or standard-library source changes in this publication slice.
- D082 is unrelated concurrent work and is excluded; D084 and later matching-design checkpoints are excluded.

## [0.1.398] - 2026-09-11

### D081 — Default ordinary value-pattern matching
- Ratifies standard inherited value-pattern behavior through the existing D073 authority: `Object.match(subject)` performs exactly one ordinary pattern-side `this == subject` and returns that canonical Boolean result unchanged.
- Canonical `false` is D072 no-match and canonical `true` is zero-capture success. The default root behavior creates no capture Array.
- No `===` pre-check, `subject == this` fallback, hashing, truthiness, coercion, retry, implicit await/Future adoption, hidden `ValuePattern`, second matching operator, matcher registry, or literal-family exception table is introduced.
- Objects needing richer recognition/capture semantics override or shadow ordinary `match(subject)`; they need not redefine `==` as pattern recognition.
- Standard Number/String/Boolean/null cases require no matching-specific semantic exception; implementations may specialize only when observationally equivalent to the ordinary exactly-once matcher/equality path.

### Compatibility and implementation state
- Normative owner changed: `semantics/MATCHING.md`; adds durable non-normative D081 decision record under `docs/project/decisions/language/`.
- Existing D071-D075, D078 and D080 matching semantics remain unchanged.
- D079-owned nested-capture composition is not changed or resolved by this publication slice.
- Concrete match/arm grammar, which source expressions denote ordinary value patterns, named bindings, guards, exhaustivity, sequence/Map patterns, identity-pattern syntax, future Float/NaN-specific pattern semantics, and recognition-only fast paths remain unresolved.
- No parser, production implementation, Maven implementation version, native boundary, license term, or standard-library source changes in this publication slice.
- D082 and later matching-design checkpoints are excluded.

## [0.1.397] - 2026-09-11

### D080 — No generic positional subject-deconstruction protocol
- Ratifies that Core v0.1 does not require arbitrary objects to expose a universal positional logical view or generic subject-side positional deconstruction protocol.
- D075 `deconstructFields(...names)` remains the generic subject-side object structural protocol; domain-specific positional extraction remains pattern-owned through ordinary `pattern.match(subject)` and D072 captures.
- Intrinsically ordered values remain under their own collection/indexing or future sequence/tuple-like semantics; no positional order is inferred from slots, delegation, declaration order, D075 name order, indexed state, prototype ancestry, or host representation.
- No required `deconstruct()`, `deconstructPositions`, `componentN`, ordered positional-schema metadata, dedicated positional-view object, or request-polymorphic deconstruction selector is introduced.
- A future opt-in positional subject protocol remains possible only through a separate explicit decision backed by ecosystem evidence.

### Compatibility and implementation state
- Normative owner changed: `semantics/MATCHING.md`; adds durable non-normative D080 decision record under `docs/project/decisions/language/`.
- Existing D071-D075 and D078 matcher/named structural semantics remain unchanged.
- D079 nested-capture composition is neither changed nor resolved by this publication slice.
- Concrete match grammar, named bindings, literal/equality patterns, guards, exhaustivity, Map/sequence remainder semantics, future sequence/tuple pattern syntax, and recognition-only fast paths remain unresolved.
- No grammar, parser, production implementation, Maven implementation version, native boundary, license term, or standard-library source changes in this publication slice.
- D081 and later matching-design checkpoints are excluded.

## [0.1.396] - 2026-09-11

### D078 — Open/subset named-object structural matching
- Ratifies generic named object structural matching as open/subset: only logical field names explicitly requested through D075 `deconstructFields(...names)` participate; additional logical fields do not cause mismatch, are not enumerated/materialized by matching, and are not implicitly captured.
- Core v0.1 does not standardize generic named-object `**rest` / remainder capture or complete logical-field enumeration. D075 remains the single generic named-projection authority and gains no full-view mode or sentinel.
- No required `deconstructAllFields`, `deconstructFieldNames`, `deconstructFieldsAndRest`, `DeconstructionView`, slot/delegation enumeration, indexed-state reinterpretation, or host-reflection fallback is introduced.
- A future whole-subject alias may be considered separately without implying enumeration; collection-specific Map/sequence remainder semantics also remain separate. A future complete-view protocol requires another explicit decision backed by concrete use evidence.

### Compatibility and implementation state
- Normative owner changed: `semantics/MATCHING.md`; adds durable non-normative D078 decision record under `docs/project/decisions/language/`.
- Existing D071-D075 matcher and selective-projection semantics remain unchanged.
- Concrete match grammar, named/whole-subject binding syntax, Map/sequence remainder semantics, positional subject deconstruction, nested capture flattening, literal/equality patterns, guards, exhaustivity, and recognition-only fast paths remain unresolved.
- No grammar, parser, production implementation, Maven implementation version, native boundary, license term, or standard-library source changes in this publication slice.
- D079 and later matching-design checkpoints are excluded.

## [0.1.395] - 2026-09-11

### D075 — Named projection request/result/failure contract
- Ratifies `deconstructFields(...names)` as one ordinary variadic named-projection call over zero or more pairwise-distinct semantic String names; ordinary argument order defines response correspondence and call spread handles dynamic name lists without a request object.
- Exact normal results are canonical `false` for no named structural view, canonical `null` when projection exists but at least one requested name is unavailable, canonical `true` for successful zero-name projection, and a non-empty standard Array of exactly N values for N requested names in request order. Invalid requests/results signal ordinary `Error` at the violated protocol boundary.
- Standardizes `Object.deconstructFields(...names) -> false` for valid requests, making participation an ordinary override/shadow rather than registry/reflection/type membership.
- Requires exactly one projection call per structural attempt and an immediate shallow ordered snapshot of successful projected references before nested subpatterns. Error/control/cancellation/explicit suspension propagate normally; no implicit await, retry, transaction, deep copy, or atomicity is introduced.

### Compatibility and implementation state
- Normative owner changed: `semantics/MATCHING.md`; adds durable non-normative D075 decision record under `docs/project/decisions/language/`.
- Complete-view/remainder (`**rest`-like) projection, universal positional subject deconstruction, concrete match grammar, named bindings, nested capture flattening, guards, exhaustivity, literal/equality patterns, and recognition-only fast paths remain unresolved.
- No grammar, parser, production implementation, Maven implementation version, native boundary, license term, or standard-library source changes in this publication slice.

## [0.1.394] - 2026-09-11

### D071-D074 — Extensible matching protocol architecture
- Publishes the project-owner-ratified matching architecture after exhaustive comparative review:
  pattern-owned recognition, explicit subject-owned structural projection, source-order / first-success
  matching, ordinary selected-branch invocation, no truthiness and no implicit await.
- Standardizes the single required public matcher authority as `pattern.match(subject)` and the exact
  matcher outcome carrier: canonical `false` for no match, canonical `true` for zero-capture success,
  and a non-empty standard Array for positional captures. Every other normal outcome is invalid at the
  standard matcher-consuming boundary; Error and non-normal control propagate normally.
- Standardizes the subject-side architecture for generic object structural matching as a named,
  selective logical projection. Structural matching does not implicitly enumerate slots, delegated
  members, prototype ancestry, indexed contents, or host representation, and ordinary objects acquire
  no universal positional product layout.
- Leaves syntax, arms/defaults, literal/equality patterns, guards, exhaustivity, built-in pattern
  taxonomy, nested capture flattening, named bindings, exact structural-projection selector/request/
  result/failure carrier, full-view/remainder semantics, positional subject deconstruction, and any
  recognition-only optimization protocol explicitly unresolved.

### Compatibility and implementation state
- New primary normative owner: `semantics/MATCHING.md`; `PROTOS_LANGUAGE_SPEC.md` and root `AGENTS.md`
  register that modular owner for navigation and future-agent authority.
- Adds durable non-normative decision records D071-D074 under
  `docs/project/decisions/language/` and indexes them from that decision domain.
- No grammar, parser, production implementation, Maven implementation version, native boundary,
  license term, or standard-library source is changed by this publication slice.
- D075 and all later matching-design checkpoints are excluded.

## [0.1.393] - 2026-09-09

### D052 — TCP live-resource object topology
- Records explicit project-owner ratification after comparative review spanning Rust/Tokio, Go,
  Java NIO, Node.js, Erlang/OTP, .NET, WASI, libuv/Asio and the existing Protos object/I/O model.
- Acquired `TcpConnection` and `TcpListener` capabilities are ordinary identity-bearing Protos
  objects, structurally OPEN at acquisition, delegating immediately to canonical frozen
  authority-free family protocol prototypes whose immediate parent is `Object`.
- The TCP family prototypes are observable through ordinary delegation/reflection from an acquired
  resource but require no public Prelude binding or constructor. Standard selectors live on those
  shared prototypes; ordinary delegation from a prototype/resource does not confer TCP family
  membership or authority, and standard behaviors validate the actual receiver family.
- Resource `close()` remains the existing `Closable` lifecycle operation and does not become the
  structural `Object.close()` transition. Ordinary slots, shadowing, structural state and `super`
  continue to follow the general object model.
- D047 endpoint structural-equality requirements are preserved while stronger endpoint `===`
  identity relations remain intentionally unspecified.

### Architecture / implementation state
- Separately ratifies PLAT003 as durable JVM/Truffle architecture: ordinary
  `ProtosObjectValue`-derived TCP resource representations with opaque host state, one shared
  protocol surface per family, host-neutral acquisition commitment/late-custody reuse and
  independent read/write progress lanes with shared lifecycle.
- PLAT003 selects no NIO/epoll/io_uring/IOCP/backend, thread/event-loop identity, public TCP family
  binding, transferable resource proxy or generic HostResource hierarchy.
- Normative document changed: `io/NETWORK.md`.
- No production implementation, Maven implementation-version, native-boundary or license-term
  change is included in this ratification checkpoint. I028-C remains the implementation consumer.

## [0.1.392] - 2026-09-09

### D051 — Conditional surface syntax boundary
- Records explicit project-owner approval after comparative review of no-sugar,
  reserved-keyword, contextual-keyword, ternary, ordinary-helper and general
  Closure/syntax-extensibility approaches.
- Core v0.1 defines no dedicated `if` / `else` conditional syntax. `if` and
  `else` remain ordinary identifiers and are not added to the reserved-word set.
- `if(condition) { ... }` retains its existing ordinary
  call-plus-trailing-Closure grammar and must not be reinterpreted from the
  spelling `if`; `else` has no special continuation role and Core v0.1 defines
  no `if (...) { ... } else { ... }` pairing.
- Canonical conditional execution remains the strict ordinary Boolean protocol
  (`ifTrue`, `ifFalse`, `ifTrueIfFalse`) completed by D050. No truthiness,
  hidden conditional primitive, second branch semantics or syntax-specific
  dispatch rule is introduced.
- A future language version may reconsider conditional surface ergonomics only
  as a separate explicit compatibility-aware design decision; D051 does not
  approve multi-trailing-Closure syntax, macros, ternary syntax or another
  general syntax-extension mechanism.

### Compatibility and implementation state
- The current parser already conforms; no production implementation, AST node,
  lexer token, reserved word, runtime primitive or new `Ixxx` is required.
- Add two Protos-source conformance cases proving ordinary `if` call behavior and
  ordinary bare `else` binding behavior.
- Normative documents changed: `PROTOS_GRAMMAR.md` and
  `PROTOS_LANGUAGE_SPEC.md`.
- Maven implementation version is unchanged.

## [0.1.391] - 2026-09-09

### D048 — IpAddress / IpEndpoint construction and recognition
- Records explicit project-owner approval of the bounded construction/recognition checkpoint intentionally deferred by D047. `IpAddress` and `IpEndpoint` are standard frozen prelude factory/prototype objects that carry no Network authority and use ordinary polymorphic invocation: `IpAddress(version, bits)` and `IpEndpoint(address, port)`.
- Each successful construction returns a fresh ordinary frozen object whose immediate parent is exactly the corresponding canonical standard factory/prototype and whose own local data slots are exactly `version`/`bits` or `address`/`port`. `IpAddress` requires ordinary unbounded Integer version 4 or 6 plus in-range exact bits; `IpEndpoint` requires a recognized standard IpAddress plus an ordinary unbounded Integer port in 1..65535. Invalid construction signals ordinary synchronous Error before any network/DNS/host effect.
- Selects transparent structural recognition rather than a hidden runtime brand. A recognized standard value must be an ordinary frozen object with the exact immediate canonical parent, exact own-slot shape and valid canonical state. Manually constructing that ordinary shape through normal object/delegation/freeze mechanisms is sufficient; factory provenance is not required. Extra own slots, transitive-only ancestry, mutable/closed-but-not-frozen values and coincidental shape under another parent are not recognized.
- Adds `IpAddress.recognizes(value)` and `IpEndpoint.recognizes(value)` as standard one-argument canonical-Boolean predicates. Recognition directly observes the standard structural/frozen-state invariant without invoking candidate callbacks, getters, equality or hashing and performs no DNS/network/host I/O.
- Reaffirms D047 equality/hash laws: recognized standard addresses compare/hash by version+bits and recognized endpoints by address+port, while `===` remains ordinary object identity. The public data slots remain ordinary member-readable state so later LIB005 textual parse/format conveniences can remain ordinary Protos composition rather than requiring a native address representation.

### Compatibility and implementation state
- This revision closes the only entry checkpoint intentionally left open by D047. `I028 — Core networking foundation` becomes READY and `I028-A` may implement the approved ordinary-object factory/recognition surface; no I028 production implementation is included in this publication.
- D049's shared-standard-object publication rule applies normally to the canonical factory/prototypes: when physically shared through the standard prelude they are published frozen before guest observation. D048 introduces no new value-identity family, hidden brand, IPv4/IPv6 prototype split, String/DNS coercion, Network authority, TCP backend, UDP/TLS/HTTP facility, native socket API or Maven implementation-version change.

## [0.1.390] - 2026-09-09

### D050 — Standard Boolean protocol completion
- Records explicit project-owner approval after comparison with Smalltalk/Self
  Boolean selection and alternative selector spellings.
- Adds exactly two ordinary standard Boolean messages: zero-argument `not()` and
  `ifTrueIfFalse(trueBlock, falseBlock)`.
- `true.not()` returns canonical `false`; `false.not()` returns canonical `true`.
  Existing unary `!value` is confirmed as the mandatory lowering to
  `value.not()`.
- `ifTrueIfFalse` keeps ordinary receiver/argument evaluation. Both callback-
  producing argument expressions are evaluated left-to-right before invocation;
  canonical `true` selects only `trueBlock`, canonical `false` selects only
  `falseBlock`; only the selected callback is callability-validated and it is
  invoked exactly once with zero supplied positional arguments.
- The exact normal result of the selected callback is returned unchanged.
  Existing Error, non-local return, cancellation, suspension, Future and other
  control/lifetime semantics compose through ordinary invocation.
- Standard behavior remains restricted to canonical `true`/`false`; no
  truthiness, coercion, Boolean prototype, ternary, `unless`, `xor`, inverse
  two-branch selector, hidden task/scheduler boundary or new reserved word is
  introduced.
- Future `if`/`else` surface sugar remains a separate design decision.

### Compatibility and implementation state
- `I029 — Standard Boolean protocol completion` implements D050 atomically in
  this publication at implementation version `0.2.275-SNAPSHOT`.
- Protos-source conformance covers direct/operator negation, branch selection,
  selected-only validation, exact-once invocation, ordinary eager argument-
  expression effects and non-Boolean receiver rejection.
- The existing single `ProtosStandardBooleanProtocol` native Closure construction
  helper remains one site, so the audited Core total remains 113 sites across 30
  providers.
- Normative documents changed: `semantics/VALUES_AND_COLLECTIONS.md` and
  `PROTOS_GRAMMAR.md`.

## [0.1.389] - 2026-09-09

### D049 — shared standard-object publication and root immutability
- Records explicit project-owner approval of D049 after the B010 cross-language, Actor-isolation, adversarial and future-scalability review. Standard Protos objects physically shared through the standard prelude must be semantically immutable; when their ordinary structural state is observable, the shared object itself is published `FROZEN` before any guest observation and remains frozen for the sharing interval.
- The unique standard root `Object` is therefore completely constructed before publication and is `FROZEN` from first guest-observable access. Guest attempts to create, assign, compose into, or remove root local slots use the existing frozen-object failure rules; ordinary `close()` / `freeze()` idempotence is unchanged.
- The rule is object-by-object and shallow, not a new deep-freeze operation. Standard Closures or other standard objects that are themselves physically shared must independently satisfy the same semantic-immutability boundary. Hidden implementation caches/executable artifacts may remain implementation state only when they are semantically unobservable and do not carry Actor-local mutable state across isolation boundaries.
- Rejects a Process-local mutable root (which would still share mutation between Actors), an Actor-local replicated Core/root graph, hidden copy-on-write/overlay mutation, and a JVM-global mutable root protected only by locks. Those designs add state/identity/coordination cost or fail the existing Actor isolation invariant.

### Compatibility and implementation state
- D049 resolves B010's normative dependency and makes `I026-A4B2B3` READY; it does not itself implement concurrent Core publication, A+ context-local Truffle executable projection, or close B010/A4B2B3.
- Existing source that relied on mutating the standard root `Object` after bootstrap becomes invalid under the ordinary frozen-object rules. Programs remain free to create child prototypes, compose objects, shadow prelude names locally, and mutate their own Actor-local objects.
- This is a specification/governance publication only. It changes no production source, persisted tests, Maven implementation version, native-Closure boundary, license terms, `ContextPolicy`, Truffle Engine/Context topology, or I028 networking work.

## [0.1.388] - 2026-09-09

### D047 — explicit capability-oriented TCP networking foundation
- Records explicit project-owner approval of D047 after comparative operating-system, language/runtime, capability-security, adversarial, future-scalability and Protos-philosophy review. Adds `spec/io/NETWORK.md` as the normative owner of explicit `Network` authority, numeric IP/endpoint data laws and the initial TCP connection/listener model.
- Selects materialized `TcpConnection`/`TcpListener` roles over a mutable universal Socket, reuses Future/Byte I/O/Closable/half-close protocols, keeps IPv4/IPv6 explicit, permits multiple concurrent pending accepts, separates DNS and UDP, and keeps host event-loop/socket-handle machinery non-semantic.
- Keeps `IpAddress` / `IpEndpoint` as ordinary frozen structural data with ordinary object identity rather than extending Core value identity. IPv6 routing/interface scope belongs to Network authority; knowing address data never conveys network authority.
- Defines optional bootstrap-local `network` provisioning independently of Process, and keeps live `Network`, `TcpConnection` and `TcpListener` non-transferable across Actor/P in the initial contract.
- Intentionally leaves exact public address/endpoint constructor/factory selector spellings and recognition/construction protocol for a later explicit bounded checkpoint. `I028` is therefore allocated `OPEN`; `LIB005` becomes dependency-blocked on the Core networking foundation.

### Compatibility and implementation state
- This revision defines new normative networking semantics but publishes no networking runtime/backend implementation and does not change the Maven implementation version (`0.2.274-SNAPSHOT`).
- Existing programs without Network authority are unaffected. DNS, UDP, TLS/QUIC/HTTP, public interface discovery, formal policy attenuation, generic socket options and socket-local deadlines remain separate future designs.

## [0.1.387] - 2026-09-08

### D002 bare-assignment target-selection timing correction
- Corrects one accidental timing mismatch in the already-published D002 bare-assignment contract after explicit project-owner approval under AUD001. For `x = rhs`, destination selection now occurs before right-hand-side evaluation: the nearest existing lexical local slot wins, otherwise an own local slot of `this` may win, and delegated slots never become assignment destinations.
- If no destination exists, a fresh standard `SlotNotFound` is signaled before `rhs` begins. Once a destination exists, that exact local slot remains selected while `rhs` executes; same-named slots created, removed, shadowed, or otherwise changed by `rhs` do not retarget the in-progress assignment.
- After `rhs` completes normally, the exact produced object is written to that preselected destination. Ordinary mutation-state validation occurs at the write attempt; failure does not resume lookup at another binding and completed RHS effects are not rolled back. If `rhs` transfers control, no write is attempted.
- Bare reads, lexical-parent traversal, receiver fallback, bare `:` creation, the prohibition on delegated writes, Closure capture, module/prelude lookup and execution intrinsics are unchanged.

### Compatibility and implementation state
- This amendment aligns the normative contract with the target-before-RHS evaluator behavior already present when D002/0.1.348 was published and still present in the current implementation; no production runtime change or implementation-version bump is required.
- Adds Protos-level conformance for stable destination selection across RHS shadow creation and for missing-destination failure before RHS effects. D002's final AUD001 governance classification remains a separate follow-up publication.

## [0.1.386] - 2026-09-08

### AUD001 D003 ratification amendment — useful positional default ordering
- Records explicit project-owner approval of D003 after comparative review
  spanning Self, Ruby, Kotlin, JavaScript and Python plus adversarial and
  future-scalability analysis. Retains the deterministic binding core introduced
  by specification `0.1.340`: caller argument/spread evaluation is left-to-right;
  one caller-supplied positional vector is formed before activation binding;
  parameters bind incrementally left-to-right; defaults evaluate exactly once in
  the real invocation activation only when their position is unsupplied; earlier
  parameters are ordinary established local slots; `this`, `context`, `args`,
  Error/control-transfer, suspension and return-home behavior remain ordinary;
  `args` contains only caller-supplied values; and rest captures only the
  unconsumed caller-supplied suffix.
- Amends only the accepted Closure signature ordering. Every required non-rest
  parameter must precede every defaulted parameter, and the optional rest
  parameter remains final. A required parameter after a defaulted parameter is a
  syntax error.
- Rejects the former dead-default shape instead of adding named arguments,
  omitted-position markers, `undefined`, a hidden missing sentinel, a parameter
  TDZ, or a second binding namespace. Under positional-only binding, a default
  before a later required parameter cannot be selected by any otherwise
  successful call.
- Keeps references from defaults to the current or later not-yet-bound parameter
  names under ordinary Protos bare-name lookup; no special lookup exception is
  added. Future named arguments or richer signature facilities, if ever desired,
  remain a separate explicit design decision and may re-evaluate ordering without
  retroactively changing this Core v0.1 rule.

### Compatibility and implementation state
- Existing source that declares a required non-rest parameter after a defaulted
  parameter becomes syntactically invalid. The callable binding/runtime semantics
  for every still-valid signature are unchanged.
- The current reference parser predates this amendment and accepts the now-invalid
  ordering, so implementation alignment is tracked separately by `I025`, initially
  `READY`. This specification/governance publication does not claim parser
  conformance and does not change the Maven implementation version.
- No runtime binder, `args`, rest, spread, Closure identity, concurrency,
  native-boundary, license-term, named-argument, or new reserved-word mechanism is
  introduced.

## [0.1.385] - 2026-09-08

### AUD001 D043 ratification clarification — cleanup escape, handler destination, and lifetime boundaries
- Records explicit project-owner ratification of D043 after comparative, adversarial and future-scale review. The published ordinary Closure `ensure(cleanup)` model remains selected: eager Closure validation, one protected dynamic extent, suspension/replay stability, exactly-once LIFO cleanup on semantic exit, exact normal-result preservation, explicit asynchronous waiting, and later escaping-transfer precedence.
- Clarifies that later Error precedence is triggered only by an Error transfer that escapes the cleanup Closure. An Error signaled and completely handled inside cleanup does not replace the previously pending completion/transfer; if cleanup then completes normally, the original pending outcome continues.
- Clarifies selected-handler ordering as a one-shot unwind destination: selection consumes that handler frame before crossed cleanup runs. Normal crossed cleanup reaches the selected destination; a later transfer that escapes cleanup supersedes the original transfer and abandons that destination, so the replacement transfer searches only still-active outer handlers or handlers explicitly installed by cleanup.
- Clarifies cancellation shielding composition with the existing idempotent `Future.cancel()` contract: repeated calls do not create a distinct or stronger cancellation request and therefore do not pierce shielding of the already-honored request while its cleanup is running. This remains narrow shielding, not a general cancellation mask.
- Clarifies the lifetime/scale boundary: D043 exactly-once cleanup is a semantic-unwind guarantee while the owning execution remains capable of executing Protos cleanup. Fail-stop loss or forced termination that prevents Protos execution is outside that guarantee. Live dynamic `ensure` state is not transferred across asynchronous execution-domain, Actor, Process/Node, restart, or distribution boundaries merely because values/Futures cross them.
- Keeps future resumable recovery, continuations/effects, durable workflows, distributed compensation, hard termination and multi-shot continuation duplication as separately designed mechanisms. They may compose with D043 only through explicit contracts and cannot retroactively reinterpret Core v0.1 `Error.signal()` or silently clone a live `ensure` installation.

### Compatibility and implementation state
- This amendment clarifies the already-published D043 semantics and records owner approval; it does not require a runtime behavior change. Existing I022 conformance already covers selected-handler inactivity, cleanup suspension/replay, Error/non-local-return precedence, cancellation cleanup and task-local control isolation.
- No implementation version, native-boundary, license-term, syntax, public Task, destructor, suppressed-error/cause-chain, implicit Future-await, distributed-cleanup or restart mechanism is introduced.

## [0.1.384] - 2026-09-08

### D046 re-evaluation — scalable enumeration, authority reuse, and captured Filesystem lifetime
- Records the explicit user design-approval checkpoint after re-evaluating only
  three previously under-justified D046 choices against additional prior art,
  future extensibility, scalability, and the established Protos design
  philosophy.
- Reaffirms `filesystem.entries(path) -> Future<Array>` for Core v0.1 as a
  deliberately eager complete direct-child observation. The operation accepts
  its O(number-of-direct-children) result materialization cost; Core v0.1 does
  not introduce a filesystem-specific iterator, stream, cursor, callback walker,
  pagination protocol, or chunked variant. A separately designed future
  incremental facility may coexist without changing `entries`.
- Reaffirms `Filesystem` as the namespace-authority abstraction and retains
  `filesystem.captureTree(path) -> Future<Filesystem>`. No standard `Directory`
  or `DirectoryEntry` capability family is introduced. Host directory handles,
  secure relative descriptors, or equivalent objects remain permitted backend
  mechanisms rather than new Protos-visible identities.
- Clarifies the captured-result lifetime boundary: successful `captureTree`
  imposes no programmer-managed release obligation and does not make the returned
  captured Filesystem a standard `Closable` receiver. Source traversal and
  tentative capture resources remain implementation custody; completed immutable
  backing representation and reclamation remain implementation strategy. Files
  subsequently opened from the captured Filesystem retain their ordinary File /
  `Closable` lifetime.
- Retains D046's existing exact-name/no-follow-kind rules, unspecified Array
  ordering, at-most-one descriptor per exact direct-child name, fail-closed
  handling when a finite non-duplicated result cannot be represented, immutable
  read-only capture, opaque link/other capture, and non-point-in-time source
  capture semantics.

### Compatibility and implementation state
- This is an amendment/re-evaluation of D046 rather than a new Filesystem design
  family. Existing `open`, `replace`, `remove`, Path, File, Process, authority,
  Future and cancellation semantics remain unchanged.
- B009 remains normatively `READY`: the approved amendment strengthens the
  captured Filesystem lifetime contract without changing Package Tool's
  capture-verify-use requirement.
- Published I024-A remains historical implementation evidence, not design
  authority. New `I024-A2` is `READY` to re-audit A against D046/0.1.384 before
  public materialization proceeds; I024-B becomes dependency-blocked on A2.
- No implementation version, runtime/source, native-boundary or license-term
  change is introduced by this specification/design publication.

## [0.1.383] - 2026-09-08

### Capability-confined directory observation and captured trees (D046)
- Resolves the normative part of B009 by adding exactly two general
  Filesystem operations: `filesystem.entries(path) -> Future<Array>` and
  `filesystem.captureTree(path) -> Future<Filesystem>`.
- `entries` returns a fresh Array of fresh frozen ordinary descriptor objects
  with exactly local `name` and `kind` slots. `name` is the exact stored
  one-component String; `kind` is exactly one of `"regular"`, `"directory"`,
  `"link"`, or `"other"`. Child kind observation never follows the child
  indirection, descriptors carry no authority, synthetic `.`/`..` entries are
  excluded, and result ordering is deliberately unspecified.
- `captureTree` selects a directory under the receiver Filesystem authority
  without following the final selected indirection and recursively captures a
  finite authority-confined tree into a fresh Filesystem capability. It follows
  no captured child link, copies exact regular-file bytes from stable selected
  resources, preserves directory structure and opaque link/other entry kinds,
  and returns a permanently read-only immutable captured namespace.
- The capture is intentionally not a source transaction: concurrent source
  mutation may affect what is selected while capture runs, and Protos does not
  require all captured source names/bytes to have coexisted at one instant.
  The returned capability itself is the stable result; after success its
  namespace, entry kinds and regular-file bytes never change.
- Both operations remain Future-shaped, capability-confined and side-effect-free
  with respect to the source namespace. Invalid invocation/Path domains use the
  existing `InvalidIOArgument` Future failure boundary; unsupported,
  non-directory, confinement, traversal, resource or capture failures use the
  existing `IOError` family.
- No `Directory`, `DirectoryEntry`, metadata/stat object, ambient Filesystem,
  path-string API, symlink-target API, recursive mutation API, new syntax, or
  package-specific native primitive is introduced.

### Compatibility and implementation state
- Existing `open`, `replace`, `remove`, Path, File, Process and authority
  semantics are unchanged. Programs that do not invoke the new operations pay
  no semantic/runtime cost for tree capture.
- B009 moves `BLOCKED -> READY`: the normative contract is independently
  implementable. New implementation item `I024 — Filesystem directory
  observation + captured-tree capability` is READY.
- `TOOL001-F2E2` remains blocked by implementation dependency I024.
- No implementation version, license term or existing native boundary changes.

## [0.1.382] - 2026-09-07

### Task-scoped structured Future ownership (D045)
- Resolves B008 by defining the structured owner of task-backed Future-producing
  child work as the current asynchronous task execution scope, not every ordinary
  synchronous Closure/method activation nested inside that task. No public Task or
  scope object is introduced.
- Ordinary synchronous invocation may return a pending Future immediately. Returning,
  storing, wrapping, or otherwise exposing that Future does not wait, detach,
  transfer, re-parent, duplicate, or remove its ownership edge, and implementations
  must not use escape analysis or result-shape inspection to infer ownership.
- A distinct asynchronous child task has its own structured execution scope for its
  descendants. The enclosing owning asynchronous computation waits for its
  non-detached task-backed children only when that owning computation itself reaches
  terminal completion; child failure/cancellation remains unobserved unless ordinary
  Future observation exposes it.
- `Future.detach()` removes only the existing task-scoped ownership edge. Future
  adoption still transfers only eventual outcome and does not transfer ownership.
  `Future.then()` continuations and isolated P work use the same task-scoped rule.
- Reconciles D043/D044 composition wording: synchronous `ensure` body/cleanup and
  `while` condition/body activations are not implicit concurrency scopes. A Future
  returned by a `while` body remains an ignored result with no loop-specific await,
  adoption, flattening, cancellation, detachment, re-parenting, or scheduler point.

### Compatibility and implementation state
- This closes the specification contradiction without requiring a new runtime model:
  ordinary Future-shaped library APIs remain capable of returning pending Futures,
  while the existing enclosing asynchronous computation retains structured lifetime.
- B008 moves `BLOCKED -> READY`; I023-B2D2 returns to `READY` for conformance and
  cross-B2 closure. B008 becomes `CLOSED` only when that implementation/conformance
  work is published or made obsolete.
- No syntax, Future identity/state, adoption outcome, Actor/P isolation, I/O producer
  ownership, implementation version, or license term changes.

## [0.1.381] - 2026-09-07

### Standard Closure `while` protocol (D044)
- Resolves B007 by defining one exact Core v0.1 pre-test loop protocol as the
  ordinary Closure-specific message `condition.while(body)`, with no `while`
  keyword, statement form, dedicated grammar production, `Closure` prototype,
  truthiness rule, or second executable-value kind.
- Adds `while` beside `future`, `parallel`, and `ensure` as an ordinary local
  Closure-valued `Object` slot. The standard behavior accepts only a semantic
  Closure receiver and exactly one semantic Closure body; ordinary lookup,
  reflection, extraction, shadowing, and user override rules remain unchanged.
- Requires ordinary receiver/argument evaluation first, followed by receiver,
  exact-arity, and body-Closure validation before the first condition activation.
  Callback declared arity is not preflighted: condition and body are each
  activated with zero supplied arguments when their path is reached, and ordinary
  Closure binding failures occur at that actual activation.
- Defines exact pre-test order: activate condition; canonical `false` terminates;
  canonical `true` activates body and repeats; every other normal condition value
  signals a fresh standard `Error`. No truthiness, coercion, implicit call,
  awaiting, or Future adoption is permitted.
- Ignores every normal body result and returns canonical `null` on every normal
  loop termination, including zero iterations. A Future returned by condition is
  therefore an invalid non-Boolean result; a Future returned by body is merely an
  ignored ordinary value.
- Composes the loop boundary with existing Error, non-local-return, suspension,
  replay, cooperative-cancellation, `ensure`, and structured-task rules. The
  loop adds no hidden task, Future, handler, cleanup scope, cancellation mask,
  preemption point, scheduler boundary, or suspension point, and replay must not
  duplicate completed condition/body effects.
- Clarifies in the grammar owner that `condition.while() { ... }` is only the
  existing empty argument list plus trailing-Closure desugaring; future
  `while (condition) { ... }` sugar is outside Core v0.1.

### Compatibility and implementation state
- Existing programs not selecting the new standard `while` slot retain their
  behavior and pay no loop-specific runtime/scheduling cost.
- D044 satisfies B007's normative unblock condition. B007 therefore becomes
  `READY`, not `CLOSED`: reference implementation, conformance, suspension/
  cancellation evidence, and final integration are tracked by newly allocated
  `I023 — Standard while protocol`.
- DOC001-E is no longer blocked by unresolved language design, but remains
  `BLOCKED_BY_DEPENDENCIES` until I023 publishes runnable current behavior; the
  Programming Guide must not present the selector as implemented before then.

## [0.1.380] - 2026-09-06

### Standard Closure `ensure` protocol (D043)
- Closes the remaining public cleanup-protocol ambiguity by standardizing
  `body.ensure(cleanup)` as an ordinary message with no new grammar category,
  keyword, statement form, `Closure` prototype, destructor, or resource type.
- Adds `ensure` beside `future` and `parallel` as an ordinary local
  Closure-valued `Object` slot whose standard behavior accepts only a semantic
  Closure receiver; ordinary lookup, reflection, extraction, shadowing, and
  override rules remain unchanged.
- Requires exactly one semantic Closure `cleanup` argument and completes all
  receiver/argument evaluation plus standard-operation validation before the
  protected body starts or any cleanup scope becomes active.
- Defines exactly-once cleanup on normal completion, non-local return, Error
  unwind, and cooperative cancellation unwind; suspension/replay is not scope
  exit, nested cleanup is LIFO, and task/Future structured ownership remains
  governed by the existing Future/Task contract.
- Preserves the protected body's exact normal result when cleanup completes
  normally and makes the cleanup's normal result irrelevant. Returning a Future
  from body or cleanup does not implicitly extend the scope, wait, adopt, or
  flatten it.
- Generalizes the already-defined cleanup-Error precedence into one consistent
  later-transfer rule: a control transfer initiated by cleanup supersedes the
  pending completion/return/Error/cancellation transfer that triggered cleanup.
  Invalid cleanup non-local return continues to signal ordinary `InvalidReturn`.
- Retains the existing narrow cancellation shielding: the already-honored
  cancellation request is not redelivered at suspension points reached by its
  cleanup, without introducing a general cancellation-mask facility.
- Keeps handler/cleanup composition unchanged: a handler selected before unwind
  reaches cleanup is already inactive, so cleanup cannot recursively re-select
  that consumed handler.

### Compatibility
- Existing programs that do not use the new standard `ensure` selector retain
  their behavior and pay no cleanup-frame/runtime cost merely because the
  selector exists.
- Existing higher-level resource designs gain one exact portable primitive but
  no automatic resource ownership, File-specific behavior, implicit Future
  cancellation, hidden `detach`, or ambient authority.
- D043 resolves the normative prerequisite for implementing the already-defined
  dynamic Error-handler and unwind-safe cleanup machinery. `I022` is therefore
  READY; resource-owning LIB004 work remains implementation-gated until I022 is
  CLOSED.

## [0.1.379] - 2026-09-06

### Race-safe Filesystem namespace-entry selection (D042)
- Corrects D041's initial ordinary-file-only final-entry restriction after implementation audit showed that common capability-relative namespace primitives atomically rename/unlink the entry selected at mutation time but cannot also atomically preclassify that mutable final name as a regular file.
- Keeps the exact public `Filesystem.replace(sourcePath, targetPath)` and `Filesystem.remove(path)` selectors, Future identities/results, authority confinement, atomic visibility, commitment/cancellation/failure aftermath, independent-operation ordering, stable already-open File binding, and namespace-durability boundary introduced by D041.
- Defines the final Path component of `replace`/`remove` as the namespace entry itself. Final symbolic-link/reparse/other indirection entries are not followed; directories and other entry kinds may be selected when the backend can perform the required atomic transition without escaping authority.
- Allows a backend to fail with `IOError` for entry kinds or source/target-kind combinations that its atomic namespace primitive cannot support, but forbids a separate check-then-act type preflight whose checked entry can be replaced before mutation.
- Defines `remove` as non-recursive even when the selected entry is a directory; recursive tree deletion remains outside Core v0.1.

### Compatibility
- Ordinary prepared-file publication, which motivated B006, is unchanged: staging files are still prepared through File sequencing and published through the same atomic `replace` selector.
- D042 broadens which final namespace entries may participate rather than changing the outcome of a successful ordinary-file replacement/removal. I021-A's host-neutral dispatch/commitment substrate remains valid because it did not encode D041's file-kind restriction.
- I021-B remains READY and must implement the corrected namespace-entry contract. Package-tool metadata mutation and B006 closure remain implementation-gated; D042 authorizes no package-specific native escape.

## [0.1.378] - 2026-09-06

### Failure-atomic Filesystem namespace replacement/removal (D041)
- Adds the minimal general file-entry namespace mutation surface `filesystem.replace(sourcePath, targetPath) -> Future<Filesystem>` and `filesystem.remove(path) -> Future<Filesystem>`; both use only the explicit Filesystem receiver's authority and preserve the existing confinement model.
- Defines `replace` as one indivisible source-to-target namespace transition: an existing target remains continuously bound to either the old target resource or the selected source resource, while an absent target appears in the same transition that removes the source name. Copy-then-delete, truncate-and-write, ambient paths, and package-specific rename escapes are not conforming substitutes.
- Defines `remove` as one indivisible file-entry removal and keeps already-open File capabilities bound to their previously selected resource across replace/remove, preserving the existing stable-resource rule.
- Restricts the initial new surface to ordinary file entries. Directories and final-component symbolic-link/reparse/other indirection entries remain outside these operations, avoiding an accidental general rename/symlink policy.
- Makes invalid Path-domain arguments pre-effect `InvalidIOArgument` failures and leaves source/target absence, confinement, unsupported entry/backend and other operational failures in the existing `IOError` family.
- Fixes cancellation/failure at one commitment point: before commitment a cancelled/failed operation contributes no namespace mutation; the atomic transition is irreversible commitment; after it commits the standard Future resolves successfully rather than exposing an implementation-selectable uncertain replacement outcome.
- Keeps distinct Filesystem operations independently asynchronous unless ordinary Protos sequencing orders them; no global Filesystem lock, per-Path queue, or same-Actor implicit FIFO is introduced.
- Separates live atomic visibility from crash durability. Neither successful replace/remove nor File `sync()` implies a namespace-durability barrier; a later namespace-sync capability remains a separate design question.

### Compatibility
- Existing code that does not use the new Filesystem selectors is unchanged and receives no new ambient Filesystem authority. Filesystem remains host-provisioned rather than a Core-prelude singleton or constructor.
- The new surface is deliberately narrower than a general move/rename/stat/directory API and does not close package-store enumeration, mkdir, symlink, directory-removal, or network prerequisites.
- This revision resolves B006's normative dependency and makes I021 implementation work READY. Package-tool metadata mutation remains implementation-gated until the faithful general Filesystem replace/remove capability is available; D041 does not authorize a package-specific native escape hatch.

## [0.1.377] - 2026-09-06

### Dynamic super-dispatch context (D040)
- Defines super-send validity as a dynamic invocation property rather than a lexical Method category: Protos retains one executable value kind, Closure, and the same Closure source may execute with or without `methodHome` depending on its invocation role.
- Requires the complete ordinary caller-supplied argument/spread vector to be evaluated left-to-right before super dispatch. If argument evaluation transfers control, no super-context failure or lookup is performed.
- Defines execution with no current `methodHome` to signal one fresh standard `InvalidSuper` occurrence and perform no slot lookup.
- Defines a present `methodHome` with no delegation parent as a valid super context with an empty continuation search, therefore signaling fresh `SlotNotFound`; ordinary lookup exhaustion after a non-root home remains `SlotNotFound`.
- Adds `InvalidSuper` as one minimal standard Error prototype directly below `Error`, with no required payload and with occurrence freshness inherited from the existing standard-failure rules.
- Applies the same rule when another semantic boundary deliberately omits caller method metadata, including isolated-parallel Closure projection; no P-specific super exception is introduced.

### Compatibility
- Valid method-bound super sends retain their existing receiver, lookup-origin, extraction, nested-Closure, and invocation semantics.
- Programs that reach the previously unspecified no-`methodHome` case now have one deterministic portable failure category and timing. No syntax, first-class `super` value, static Method category, delegation rule, or hidden fallback lookup is added.
- This revision resolves B005's normative dependency only. Runtime/Core publication remains I020-D implementation work, so B005 and I020-D become READY rather than CLOSED.

## [0.1.376] - 2026-09-05

### Public ActorGroup acquisition (D039)
- Defines the only Core v0.1 portable ActorGroup creation/acquisition operation as `Actor.group(firstMember, additionalMembers...) -> GroupRef`, requiring one or more explicit ActorRef communication capabilities and creating one fresh Group identity plus one fresh GroupRef acquisition identity synchronously after whole-argument validation.
- Makes the caller Actor's Process the owning lifecycle/control scope for a Core-created Group; Process termination terminates the Group without stopping its members, while empty or temporarily unroutable membership does not terminate the Group and GroupRefs never keep it alive by reachability alone.
- Keeps GroupRef communication authority separate from Group control authority: creation establishes only the initial routing membership, Core v0.1 exposes no public post-creation membership mutation, Group controller/desired-cardinality surface, explicit Group termination selector, placement selector, or Group identity handle.
- Closes public acquisition without creating service discovery: Core v0.1 has no `Group`, `GroupRef` constructor/lookup object, name-to-GroupRef or identity-to-GroupRef reacquisition, registry, namespace, TTL/watch/federation/rebinding contract, endpoint syntax, or ambient discovery authority.
- Preserves existing ActorRef/GroupRef transfer, routing, acceptance, uncertainty, replacement, and identity rules; explicit ActorRefs may denote local or remote members without making transport or topology part of the acquisition API.

### Compatibility
- Adds one new portable selector, `Actor.group`, to the standard frozen `Actor` facility. Existing programs that do not use ActorGroup acquisition retain their behavior and pay no registry, Cluster, discovery, or transport-coordination cost.
- This specification revision resolves the language-design dependency only; implementation and publication of the new selector remains I011 work, so B004 becomes READY rather than CLOSED.

## [0.1.375] - 2026-09-04

### Path equality versus semantic identity (D037)
- Clarifies that portable Path equality is structural and filesystem-independent,
  using rootedness plus the ordered component sequence, while Path semantic
  identity remains ordinary individual object identity.
- Therefore independently created structurally equal Paths compare equal through
  the Path equality contract but remain distinct through `===`, `!==`,
  `identityHashOf`, and `IdentityMap`; Path is not added to the closed Core
  value-identity set.

### Encoding semantic-family membership and receiver domain (D038)
- Defines Encoding descriptors positively as Encoding semantic values produced
  or provisioned by normative Encoding-producing operations or explicit
  permitted host Encoding-provisioning boundaries.
- Clarifies that delegation, copying, composition, similarly named slots, and
  structural/protocol compatibility do not confer Encoding membership, and that
  Encoding-descriptor parameters perform neither duck typing nor implicit
  coercion.
- Applies the general standard semantic-family receiver-domain rule to standard
  Encoding behavior, so inherited or copied behavior cannot bypass an
  incompatible-receiver check.

### Compatibility
- No standard prototype, delegation topology, physical Encoding representation,
  implementation version, or Java implementation behavior is changed.

## [0.1.374] - 2026-09-04

### Future result identity semantics (D036)
- Defines the general Core-standard Future result-identity rule: unless an
  operation expressly returns an already-existing Future, every successfully
  dispatched invocation that produces a Future result produces a fresh standard
  Future identity belonging to that invocation.
- Makes distinct invocation results observably distinct through `===`, `!==`,
  `identityHashOf`, and `IdentityMap`, including immediately resolved, failed,
  or cancelled results; implementations may not cache one terminal Future or
  accidentally share one pending Future as the semantic result of distinct
  invocations.
- Separates Future identity from outcome identity: adoption, resolved values,
  and stored Error identity continue to follow their existing contracts, so
  fresh result Futures do not imply fresh values or fresh Errors.
- Excludes operations whose normative contract expressly returns a pre-existing
  Future, including a receiver or another already-existing Future required by
  that operation's own result rule.
- Recasts idempotent I/O lifecycle identity as a specialization of the general
  Future rule: distinct invocations keep distinct Future identities while still
  observing the same single logical lifecycle and, where required, the same
  recorded terminal Error.

### Compatibility
- Closes implementation-selectable Future-result aliasing and cross-invocation
  cancellation/continuation coupling. Scalar replacement, allocation elision,
  virtualization, pooling, shared immutable backing, canonical terminal-state
  representation, cached completion metadata, and equivalent physical
  optimizations remain permitted when observable Future identity and Future-local
  effects are preserved.

## [0.1.373] - 2026-09-04

### Fresh and independent standard Bytes results (D035)
- Defines every successful Core-standard operation that produces a logical new
  `Bytes` result as returning a fresh open standard Bytes identity, including
  empty results, unless that operation expressly returns an existing object.
- Makes each result's ordinary slots and mutable byte-sequence state independent
  of other results, source/argument objects, producing receivers, and internal
  runtime buffers; identity operations, default equality/hashing, and structural
  mutation consequently observe distinct ordinary Bytes objects.
- Applies the general value-family rule to successful non-EOF
  `ByteReadable.read(maxBytes)` results and to every successful one-shot
  `Encoding.encode(text)` result.
- Keeps Actor and isolated-P transfer semantics separate: a later boundary
  crossing performs the already-defined logical copy/reconstruction and does not
  preserve the source-domain result identity.
- Exposes no partially produced Bytes result from a failed or cancelled
  operation before successful result commitment.

### Compatibility
- Closes implementation-selectable Bytes identity, aliasing, and structural
  state. Implementations may retain immutable backing, copy-on-write, slicing,
  zero-copy, persistent storage, lazy materialization, scalar replacement, or
  other physical sharing when fresh identity and independent observable mutable
  state are preserved.

## [0.1.372] - 2026-09-04

### Callback-domain and eager-validation closure (D030, D034)
- Defines `Future.then(transform)` against the existing ordinary-invokable
  protocol rather than a hidden or Closure-only callback category.
- Requires eager read-only callability validation after ordinary argument
  evaluation and before creation of any continuation task or destination Future,
  independently of the source Future's current state.
- Preserves ordinary polymorphic invocation at continuation execution time,
  including a fresh `call` lookup so validation does not pin a Closure across
  intervening mutation or shadowing.
- Defines `Error.handle(body, handler)` as intentionally Closure-only, with
  left-to-right expression evaluation followed by `body` Closure validation and
  then `handler` Closure validation before any handler frame is installed.
- Invalid `body` or `handler` therefore fails before handler installation and
  before `body` invocation; handler validation is never deferred until an Error
  is actually signaled.

### Compatibility
- Closes implementation-selectable callback domains and validation timing for
  `Future.then` and `Error.handle`. Conforming implementations may optimize
  inspection, scheduling, and handler representation only when the specified
  failure timing, side-effect visibility, fresh invocation lookup, and absence
  of partially created continuation/handler state remain unchanged.

## [0.1.371] - 2026-09-04

### Reflective local-slot name argument domain (D033)
- Defines the standard `hasSlot(name)`, `slotValue(name)`, and
  `removeSlot(name)` argument domain uniformly as semantic `String` values.
- Rejects non-String names before local-slot inspection and before any
  `removeSlot` structural mutation, with no implicit conversion, stringification,
  String-like delegation, selector coercion, or host adaptation.
- Defines a valid name by its exact semantic String scalar sequence and preserves
  the existing local-only behavior: `hasSlot` returns `false` for absence, while
  `slotValue` and `removeSlot` signal on a missing local slot.
- Keeps delegated slots irrelevant to all three operations and leaves ordinary
  member lookup as the separate delegating mechanism.

### Compatibility
- Closes previously unspecified reflective-name coercion/validation behavior.
  Implementations accepting non-String designators for these standard operations
  must reject them under the common semantic-String contract.

## [0.1.370] - 2026-09-04

### Fresh reflection Array identity (D032)
- Defines every successful `slotNames()` call as producing a fresh
  identity-bearing standard Array, including repeated observations of an
  unchanged object and empty results.
- Makes reflection snapshots mutually independent: changing one returned Array
  cannot mutate the reflected receiver, another reflection result, or a later
  snapshot.
- Preserves the existing canonical lexicographic slot-name ordering and shallow
  snapshot contents while prohibiting semantic Array-result reuse/canonicalization.
- Retains implementation freedom for lazy, virtual, persistent, or shared backing
  representations when fresh Array identity and independent observable state are
  preserved.

### Compatibility
- Closes previously implementation-selectable result identity for `slotNames()`.
  Implementations that reused one semantic Array object across calls must instead
  preserve fresh per-call Array identity; physical storage may still be shared
  invisibly.

## [0.1.369] - 2026-09-04

### Path parent-component selector disambiguation (D028)
- Renames the standard Path operation that appends one parent-traversal component
  from `parent()` to `parentComponent()`.
- Preserves `parent()` as the Core structural-reflection selector owned by
  `semantics/OBJECT_MODEL.md`; Path does not overload that selector with
  filesystem/path-construction semantics.
- Makes `path.parent()` and `path.parentComponent()` observably distinct:
  the former reports the immutable delegation parent, while the latter returns
  the immutable Path value with one additional parent component.
- Defines no Core v0.1 compatibility alias from `Path.parent()` to traversal,
  preventing standard Path behavior from shadowing structural reflection.

### Compatibility
- Portable code using the former ambiguous `path.parent()` spelling for Path
  traversal must use `path.parentComponent()`. Structural reflection through
  `parent()` remains unchanged.

## [0.1.368] - 2026-09-04

### Standard Integer result family (D029)
- Defines one general result-only rule in the Values and Collections numeric
  owner: when a Core-standard operation returns or resolves simply to
  `Integer`, without naming a more specific numeric family, the result is an
  ordinary unbounded Integer.
- Keeps input-domain contracts independent from that result rule; accepted
  argument families are not narrowed merely because a standard result is
  specified as `Integer`.
- Removes the previous `Array.size()` freedom to choose a fixed-width Integer
  family and aligns informative runtime pseudocode with the normative rule.
- Existing contracts that explicitly name another numeric family remain
  unchanged.

### Idempotent lifecycle Future identity (D031)
- Defines one cross-cutting I/O lifecycle rule: every invocation of a
  standardized Future-returning idempotent lifecycle operation produces a fresh
  standard Future identity, even when calls observe the same pending or already
  terminal lifecycle.
- Keeps the lifecycle itself singular and idempotent: fresh Futures do not retry
  close/shutdown effects and must observe the same logical lifecycle outcome.
- Preserves exact recorded Error identity wherever the lifecycle owner already
  requires it, including repeated observation of one failed close lifecycle.
- Applies the general rule to `close()`, `shutdownWrite()`, and
  `shutdownRead()` without introducing canonical Futures, Future subtypes,
  wrappers, or hidden lifecycle tokens.

### Compatibility
- Closes implementation-selectable result-family behavior for standard
  `Integer` results and implementation-selectable Future identity for repeated
  idempotent I/O lifecycle calls. Implementations remain free to optimize
  physical representation when `===`, numeric-family behavior, lifecycle
  outcome, and required Error identity remain unchanged.

## [0.1.367] - 2026-09-04

### Receiver-bound Closure semantic identity (D024)
- Defines every successful receiver member-read selecting a Closure as producing
  a fresh identity-bearing Closure value distinct from both the stored Closure
  and every other extraction result, including repeated identical lookups.
- Distinguishes aliasing from re-extraction: ordinary aliases preserve the exact
  extracted Closure identity, while storing and later receiver-reading a Closure
  performs a new extraction without mutating the stored value.
- Fixes same-receiver, different-receiver, and inherited-lookup behavior:
  extraction identity is never canonicalized by receiver, stored Closure, slot,
  or `methodHome`; each extraction preserves its own original receiver and
  selected slot owner for later invocation.
- Derives `===`, `identityHashOf`, and `IdentityMap` behavior from the existing
  general identity contracts: aliases are identical, separate extractions are
  not, identity-hash collisions remain legal, and IdentityMap distinguishes
  separate extraction identities through primitive identity.
- Distinguishes immediate message invocation from extraction: `receiver.m()`
  preserves receiver/`methodHome` without exposing a bound Closure value, while
  `receiver.m` creates the fresh bound Closure whose identity can be observed.
- Keeps Closure as the single executable value kind, introduces no `Method`
  value type, no bound-method prototype, no standard `Closure` prototype, and no
  interning/global identity registry.

### Compatibility
- Closes previously implementation-selectable identity behavior for extracted
  receiver-bound Closures. Implementations that reused one semantic bound
  Closure for repeated lookups, returned the stored Closure identity itself, or
  keyed extracted-method identity by receiver/slot/`methodHome` must instead
  preserve the fresh per-extraction semantics while remaining free to optimize
  physical representation invisibly.

## [0.1.366] - 2026-09-04

### Closure asynchronous-method ownership (D025)
- Fixes the standard Closure-specific selectors `future` and `parallel` as
  ordinary local Closure-valued slots of `Object`; every Core Closure reaches
  them through its D027 direct delegation edge to `Object`, with no standard
  `Closure` prototype, hidden method table, or per-Closure slot materialization.
- Defines the standard behaviors' receiver domain as semantic Closure values.
  Other receivers may find the inherited selectors through ordinary lookup, but
  invoking the selected standard behavior signals the normal invalid-receiver
  Error before asynchronous/parallel effects and does not resume lookup.
- Keeps `future` and `parallel` ordinary, non-reserved selector names: nearer
  slots shadow normally, user Closure-valued overrides retain their own receiver
  contracts, and Closure instances may define local overrides when ordinary
  object-state rules permit.
- Applies the existing extracted-method semantics unchanged: reading either
  Closure-valued slot preserves the original receiver and selected slot owner as
  receiver/`methodHome` binding metadata.
- Keeps operation-specific ownership modular: `FUTURES_AND_TASKS.md` owns
  asynchronous Future/task behavior after receiver validation and
  `PARALLEL_EXECUTION.md` owns isolated-parallel behavior after validation.
  Host primitives and scheduler/worker machinery remain implementation details
  that must preserve ordinary lookup, reflection, shadowing, extraction,
  receiver-domain behavior, and portable parent topology.

### Compatibility
- Closes previously implementation-selectable ownership/reflection behavior for
  `future` and `parallel` without adding a Core object, prototype, executable
  kind, dispatch path, or hidden semantic universe.

## [0.1.365] - 2026-09-04

### Portable Core delegation topology (D027)
- Closes the observable standard-object topology with one general rule: every
  Core-standard visible object whose immediate parent is not otherwise specified
  delegates directly to `Object`.
- Preserves existing explicit exceptions, including the numeric hierarchy,
  standard Error taxonomy, `Context` ancestry, factory-result parentage, and
  capability/value relations explicitly owned by their domain specifications.
- Fixes canonical `true`, `false`, `null`, and Closure objects as direct children
  of `Object`; no standard `Boolean`, `Closure`, `Value`, `Collection`,
  `Callable`, or `AsyncValue` object is introduced.
- Fixes `String.parent() === Object` and semantic String-value parentage through
  `String`; fixes `Array`, `Map`, and `IdentityMap` themselves as direct children
  of `Object` while retaining factory-produced collection parentage through the
  actual invocation receiver.
- Fixes `Future.parent() === Object` and Core-produced Future values as direct
  children of `Future`.
- Makes explicit that `IdentityMap` does not implicitly delegate to `Map` and
  forbids implementation-defined Protos-visible intermediate ancestors.

### Compatibility
- Implementations that inserted unnamed or non-standard visible ancestors into
  Core delegation chains must remove them. Hidden representation remains free
  provided `parent()`, lookup, `super`, receiver-domain behavior, and reflection
  observe the normative topology.

## [0.1.364] - 2026-09-04

### Boolean standard-object surface (D026)
- Resolves the normative contradiction over a standard `Boolean` object:
  Core v0.1 defines exactly the canonical Boolean values `true` and `false` and
  installs no standard prelude binding, object, or prototype named `Boolean`.
- Makes the observable consequence explicit: absent an ordinary program/library
  binding named `Boolean`, bare lookup of `Boolean` signals the normal
  missing-lookup `Error`; `Boolean.parent()` and `Boolean.slotNames()` therefore
  fail while resolving their receiver rather than inspecting a hidden standard
  Boolean object.
- Keeps `Boolean` available as an ordinary, non-reserved identifier name.
  User/library objects bound to that name do not acquire Boolean-family
  membership; the standard Boolean family remains exactly canonical `true` and
  canonical `false`.
- Removes `Boolean` from the `OBJECT_MODEL.md` illustrative list of standard
  built-in prototype objects and leaves Boolean-family surface ownership in
  `VALUES_AND_COLLECTIONS.md`.

### Compatibility
- Eliminates a direct contradiction between normative owners. Implementations
  that exposed a standard Core `Boolean` prelude object were relying on the
  contradicted interpretation and must remove that Core binding; ordinary
  user/library bindings named `Boolean` remain valid.

## [0.1.363] - 2026-09-04

### Slot-write expression normal result (D023)
- Defines the normal result of `x: value`, `object.x: value`, `x = value`, and
  `object.x = value` as the exact object produced by right-hand-side evaluation.
- Requires successful writes to return that same RHS object without re-reading
  the slot, converting, copying, canonicalizing, wrapping, or substituting
  `null` or the target/receiver.
- Defines failed writes and target/RHS control transfers as having no normal
  expression result while preserving already-completed evaluation effects.
- Keeps destination selection, delegation, and object-state validity in their
  existing owners and explicitly excludes indexed `object[index] = value`,
  which remains an indexing-protocol operation.
- Confirms the informative Abstract Runtime's existing RHS-returning pseudocode
  is aligned with the now-normative contract and requires no runtime-document
  change.

### Compatibility
- Closes previously unspecified observable expression-result behavior without
  changing where slots are created or assigned, evaluation order, write failure
  conditions, object state, delegation, or indexed assignment semantics.

## [0.1.362] - 2026-09-04

### Standard Object.init normal result (D022)
- Defines the inherited standard `Object.init()` normal result as its receiver
  (`this`), making direct invocation portable.
- Keeps overriding `init` methods under ordinary Closure return semantics; they
  are not required to return `this`.
- Preserves default construction semantics: `Object.call` ignores the normal
  result of `init` and returns the fresh instance; initialization Errors and
  other control transfers continue to propagate normally.

### Compatibility
- Closes previously unspecified observable behavior for direct calls to the
  standard inherited `Object.init`; it does not change custom initialization or
  construction result semantics.

## [0.1.361] - 2026-09-04

### Canonical Process bootstrap snapshot identity (D018)
- Administratively records the already-published Process bootstrap snapshot
  identity semantics: each logical Process has one canonical identity-bearing
  `process.args()` snapshot and one canonical identity-bearing
  `process.environment()` snapshot, and repeated successful acquisition through
  capabilities/proxies denoting that Process preserves that semantic identity.
- Consequently, repeated successful acquisitions preserve `===`,
  `identityHashOf(...)`, and `IdentityMap` key identity; no special
  `IdentityMap` rule is introduced.
- Keeps physical representation implementation-private: wrappers/views, shared
  backing, lazy materialization, caching, eviction/rematerialization, scalar
  replacement, and moving storage remain permitted when they preserve the
  required semantic observations.
- Introduces no new value-identity category or special transfer capability;
  ordinary isolation/pass-by-value identity rules remain authoritative when a
  snapshot is transferred.

### Compatibility
- This revision only records D018 administratively. The corresponding normative
  semantics were already published in `spec/io/PROCESS_IO.md`; this revision
  does not modify them.

## [0.1.360] - 2026-09-04

### Actor creator capability discipline (D019)
- Removed the stale `parentActor` ambient capability from Core Actor semantics:
  creation genealogy alone grants no reverse `ActorRef`, creator lookup, or
  implicit reply channel to the created Actor.
- Distinguished runtime creator/ancestry bookkeeping from program-visible
  authority. Implementations may retain genealogy internally without exposing a
  capability or synthesizing reverse distributed routing/lifetime state.
- Required any capability toward a creator to arise through the existing
  explicit provisioning/Actor-transfer mechanisms, including an explicitly
  supplied `Actor.current()` value when permitted.
- Kept failure authority separate from both genealogy and communication
  authority; no supervisor API, restart/linking protocol, or new capability kind
  is introduced.

### Compatibility
- Aligns §25 with the existing clean Actor bootstrap and capability discipline in
  §8. `Actor.spawn(...) -> ActorRef`, `Actor.current()`, D015 error semantics,
  and the D017 Core failure-policy/API boundary are unchanged.

## [0.1.359] - 2026-09-04

### String transformation surface (D020)
- Closed the Core v0.1 status of `uppercase()` and `replace(...)`: neither
  selector is a standard Core String operation.
- Kept String immutability as the relevant Core rule while allowing libraries,
  implementation extensions, and ordinary user objects to define those names
  through normal slots and invocation.
- Avoided importing platform/Unicode case-mapping, locale, normalization,
  pattern, overlap, or empty-needle semantics into Core.

### Compatibility
- No existing Core String operation changes semantics; this only removes the
  possibility of treating illustrative transformation names as portable Core.

## [0.1.358] - 2026-09-04

### GroupRef semantic identity (D021)
- Distinguished Group identity, semantic `GroupRef` object identity, and physical
  proxy/wrapper representation. Same-Group references are not automatically the
  same `GroupRef`.
- Defined `GroupRef` transfer as preservation of the same identity-bearing
  capability object: repeated transfer and round-trip rematerialization preserve
  `===`, primitive `identityHashOf`, and therefore `IdentityMap` key identity.
- Kept proxy caching, rematerialization, addresses, tokens, and representation
  implementation-private; no global interning or permanent wrapper registry is
  required.

### Compatibility
- Group membership/routing evolution and Group lifetime remain independent of
  `GroupRef` semantic identity. No new Group API, equality-by-target rule,
  capability amplification, or Authority mechanism is introduced.

## [0.1.357] - 2026-09-04

### Actor API closure cleanup (D017)
- Removed residual wording that presented already-closed Core Actor API decisions
  as open or implementation-selectable.
- Aligned the introductory `SendOperation` description with its closed minimal
  Core `cancel()` / `retry()` protocol and kept extra diagnostics explicitly
  outside portable Core.
- Aligned graceful stop with the closed `ActorRef.stop() -> null` contract and
  its absence of a dedicated stop Future/operation, while preserving
  `ActorRef.termination()` as the independent lifecycle-observation mechanism.
- Removed the stale open failure-policy API statement and made §26 defer to the
  fixed Core policy/API boundary already owned by §26A.

### Compatibility
- No new Actor API or lifecycle state is introduced. D015 non-resumable Error
  handling and D016 `Actor.spawn(...) -> ActorRef` semantics are unchanged.

## [0.1.356] - 2026-09-04

### Ordinary computed-operation invocation spelling
- Closed D014 by making standard `size`, `hash`, and exposed `identityHash` behaviors ordinary zero-argument Closure-valued slots invoked with parentheses; ordinary member reads such as `obj.hash` only retrieve/extract the selected value and never auto-invoke it.
- Aligned Array, Map, IdentityMap, Bytes, String, numeric hashing, and parallel-result examples so actual operation execution uses `size()` / `hash()` consistently.
- Kept `identityHashOf(value)` as the existing non-overridable primitive semantic operation used by identity-sensitive machinery; an exposed `identityHash()` convenience remains an ordinary overridable message and is not used by `IdentityMap`.
- Clarified shadowing and extraction: a nearer non-Closure `size`/`hash` slot is readable normally but `obj.size()` / `obj.hash()` fails under ordinary invocation, with no ancestor fallback after the nearer slot was selected.

### Compatibility
- Removes computed-property/auto-call ambiguity without adding getters, descriptors, hidden built-in exceptions, or a second invocation protocol.

## [0.1.355] - 2026-09-04

### String normative ownership and migration cleanup
- Closed D012 by making `semantics/VALUES_AND_COLLECTIONS.md` the sole primary normative owner of Core String value/indexing semantics and reducing the legacy monolith String section to a navigation anchor.
- Repaired the malformed/duplicated Encoding-conversion migration block and made `io/TEXT_IO.md` the referenced owner of the standard Encoding conversion contract.
- Kept String literal syntax ownership in `PROTOS_GRAMMAR.md` and avoided standardizing the separate D020 `uppercase()` / `replace(...)` APIs.

### Compatibility
- This is an ownership/migration cleanup: existing String semantics are preserved while duplicate normative authority and Markdown corruption are removed.

## [0.1.354] - 2026-09-04

### Actor creation admission model
- Closed D016 by preserving `Actor.spawn(...) -> ActorRef` as the sole Core
  creation result and removing the residual public `SpawnOperation` model.
- Fixed the creation cutover: after synchronous resolution/transfer validation,
  exactly one Actor incarnation, identity, and `ActorRef` exist; capacity
  admission may delay that incarnation in `INITIALIZING` but cannot suspend or
  synchronously fail `spawn` merely because capacity is unavailable.
- Defined pre-`READY` messaging through the existing bounded
  routing/acceptance/failure rules, with no bootstrap mailbox or second delivery
  protocol, and retained initialization failure/stop as lifecycle outcomes of the
  already-created incarnation.
- Recast spawn backpressure, adaptive admission, semantic capacity demand, and
  infrastructure provisioning around live `INITIALIZING` incarnations rather
  than creation-operation objects, including weak actor-admission fairness and
  the absence of implicit spawn timeout/deadline/cancellation.
- Required Group reconciliation to account for known in-flight Actor candidates
  without making them routing-eligible before readiness, preventing repeated
  creation for the same observed deficit while preserving the existing
  convergent/temporarily-over-or-under-cardinality model.
- Removed stale open-design entries for the already-closed spawn/bootstrap and
  `SpawnOperation` APIs.

### Compatibility
- Removes contradictory residual `SpawnOperation` semantics from the normative
  distributed runtime. Implementations must return the `ActorRef` at the Actor
  creation cutover and keep placement/admission/provisioning bookkeeping
  unobservable except through already-defined lifecycle, communication, and
  capacity-demand semantics.

## [0.1.353] - 2026-09-04

### Non-resumable Error handling in Actors
- Closed D015 by removing the residual resumable/retry/substitution model from Actor fatal-failure semantics.
- Actor code now explicitly follows Core's non-resumable Error protocol: handler selection unwinds and abandons the signal continuation; a normal handler result is the enclosing `handle` result, never a value injected at the signal point.
- Clarified that handled Errors do not imply Actor failure, rollback, replay, or retry; pre-Error effects remain subject to their ordinary contracts, and only an Error escaping the Actor turn unhandled is fatal.
- Clarified that an Error signaled by the handler follows ordinary outer-handler search after the selected handler frame has been removed.

### Compatibility
- Removes contradictory recovery wording without changing Core Error identity, Future failure, I/O commitment, Actor request uncertainty, or Actor replacement semantics.

## [0.1.352] - 2026-09-04

### Polymorphic invocation protocol
- Closed D013 by defining parenthesized invocation through ordinary delegating lookup of the `call` slot; no hidden callable property, registry, wrapper, or callable hierarchy exists.
- Defined selected-`call` Closure validation, original-receiver/`methodHome` binding, non-executing `obj.call` reads and extracted bindings, inheritance/shadowing/copy/composition/aliasing, error precedence, and non-recursive terminal Closure activation.
- Defined callability inspection as read-only ordinary `call` lookup plus Closure-value validation, shared by collection/Boolean callbacks, Actor bootstrap, and isolated-P boundaries.
- Defined inherited `Object.call` as standard Closure execution for Closure receivers and default object construction otherwise; standard Array/Map/IdentityMap/numeric factories specialize through nearer ordinary `call` slots.
- Aligned the informative Abstract Runtime so `lookupInvocationBehavior` is explicitly only shorthand for the normative ordinary `call` lookup rather than a second hidden invocation property.

### Compatibility
- Closes previously implementation-selectable invocation behavior without adding a new value kind, prototype hierarchy, wrapper, or runtime registry. Existing factory/callback semantics now use one portable ordinary-slot protocol.
- D014 (`size`/`hash` spelling) remains unchanged and outside this revision.

## [0.1.351] - 2026-09-04

### Public Filesystem and I/O surface
- Closed D011 with portable Path construction, ordinary Filesystem open option objects/defaults, and exact invocation-time option snapshot/validation.
- Added authority-free prelude acquisition for Path, Encoding, BufferedReader/BufferedWriter, TextReader/TextWriter while keeping Filesystem authority explicitly host-provisioned and non-ambient.
- Defined borrowed versus owning wrapper factories and portable InvalidIOArgument mapping for invalid open arguments/options without duplicating Error/Future semantics.
- Integrated D010 by provisioning optional default Filesystem authority as a separate initial-module local capability rather than authority implicit in Process, imports, or prelude.

### Compatibility
- Closes previously unspecified public construction/acquisition spellings without changing existing I/O commitment, lifecycle, Future, Error-identity, Actor, or P owners.

## [0.1.350] - 2026-09-04

### Public Actor, P, and Process bootstrap surface
- Closed D010 with portable `Actor.spawn`, `Actor.current`, `ActorRef.send`, `ActorRef.request`, `ActorRef.stop`, and minimal `SendOperation.cancel` / `retry`.
- Closed P without a `P` object/syntax: entry remains `Closure.parallel`, `Bytes.parallelRange`, and `ByteRegion.parallelRange`.
- Closed Process acquisition with a host-provisioned RootActor initial-module local `process` capability slot; imports/prelude cannot recover its authority.
- Defined explicit Actor delegation of Process without implicit inheritance, authority amplification, P transfer, or automatic filesystem/network/subprocess authority.

### Compatibility
- Removes implementation-selectable Actor/P/Process acquisition names while keeping scheduler, mailbox, transport, workers, and host machinery unobservable.

## [0.1.349] - 2026-09-04

### Normative ownership and authority
- Closed D009 by restoring single-owner authority across the modular Core v0.1
  specification without changing observable language behavior.
- Removed the remaining normative attribution to informative
  `runtime/ABSTRACT_RUNTIME.md` from the Bytes contract and made
  `semantics/VALUES_AND_COLLECTIONS.md` the explicit owner there.
- Converted the duplicated Future-cancellation contract in the language overview
  into a navigation anchor to `concurrency/FUTURES_AND_TASKS.md`, with Error
  construction/category semantics owned by `semantics/ERRORS.md`.
- Redirected Grammar's module and object-composition semantic references to
  `semantics/MODULES.md` and `semantics/OBJECT_MODEL.md`, preserving Grammar as
  the sole syntax/lowering owner.
- Made Encoding encode/decode ownership explicit in `io/TEXT_IO.md`, removed
  residual Language/Runtime wording from module lifecycle self-references, and
  clarified repository documentation that the Abstract Runtime is informative.

### Compatibility
- No programmer-visible semantics are changed. This revision removes duplicate or
  stale authority claims so independent implementations have one normative owner
  for each affected rule.

## [0.1.348] - 2026-09-04

### Fixed
- Closed D002 by making `semantics/EXECUTION_AND_CONTROL.md` the complete normative owner of ordinary bare-identifier lookup.
- Defined lexical lookup as local-slot-only traversal of the current execution context and its lexical parents, followed only after lexical exhaustion by ordinary delegating member lookup from `this`.
- Defined bare `:` as local creation with no lookup and bare `=` as nearest local lexical assignment followed only by an own-slot receiver fallback; bare assignment never delegates or creates.
- Fixed module/prelude lookup, Closure capture-by-reference consequences, execution-state intrinsics, structural `super`, and the absence of implicit global lookup without changing ordinary member operations.
- Confirmed the informative `runtime/ABSTRACT_RUNTIME.md` already matches this algorithm and requires no change.

### Compatibility
- This revision makes the existing intended evaluator contract normative and closes D002 without unrelated semantic change.

## [0.1.347] - 2026-09-04

### Fixed
- Closed D008 by making `semantics/OBJECT_MODEL.md` the single normative owner of
  the complete `without` / `alias` local-slot-view contract.
- Defined both operations as ordinary non-mutating `Object` messages over local
  slot structure only, with semantic String name arguments and no delegated
  lookup, coercion, inherited-slot materialization, or trait-specific mechanism.
- Fixed successful results as fresh open ordinary objects with immediate parent
  `Object`; receiver parent and open/closed/frozen state are not copied.
- Required shallow binding identity preservation, including Closure identity, and
  specified that aliased methods obtain `this` and `methodHome` only from the
  ordinary later lookup/invocation on the result.
- Closed missing-source, delegated-only source, alias collision, identical-name,
  frozen-receiver, reflection/order, and composition-chaining behavior through
  the existing general object, reflection, invocation, and error rules.
- Updated the informative Abstract Runtime to reflect the normative parent/state
  choice and marked implementation blocker B002 READY.

### Compatibility
- Closes previously implementation-selectable result-parent/state, name-domain,
  inherited-slot, method rebinding, and frozen-receiver behavior. Implementations
  must not preserve the source parent or materialize delegated slots in a view.

## [0.1.346] - 2026-09-04

### Fixed
- Closed D007: defined the exact `import(specifier)` argument domain as semantic
  `String` values only, after ordinary argument evaluation and without implicit
  conversion or String-like delegation.
- Fixed the Core/host boundary: Core validates the semantic String value, while
  the host resolver owns interpretation of its exact text, including empty,
  path-like, URI-like, package-like, filesystem, network, sandbox, permission,
  and canonical-resolution policy.
- Required invalid non-String specifiers to signal a Core `Error` before resolver
  entry, and otherwise-valid but unresolved/rejected specifiers to surface as a
  language `Error` rather than host exceptions or sentinel values.
- Clarified that module cache identity begins at the resolver-produced canonical
  `ModuleKey`, not at the original String spelling, so distinct spellings may
  resolve to one Actor-local module instance and repeated canonical imports do
  not re-run initialization.
- Clarified that specifier validation/resolution adds no implicit Core suspension
  point and that failed resolution creates no cached module instance.

### Compatibility
- This closes previously implementation-selectable specifier-domain, coercion,
  empty-String, resolver-entry, resolution-failure, and textual-spelling/cache
  behavior without standardizing a filesystem, URL/URI scheme, package manager,
  registry, or concrete resolver algorithm.

## [0.1.345] - 2026-09-04

### Error identity and portable taxonomy
- Made `semantics/ERRORS.md` the single normative owner of standard Error-object
  construction, identity, portable prototype taxonomy, handler matching, and
  Core v0.1 non-resumable signaling semantics.
- Required every independent standard failure occurrence to create a fresh Error
  instance delegating to the promised prototype; standard Error prototypes are
  category/protocol objects and are never implicitly reused as singleton failures.
- Preserved exact identity when an existing Error is signaled or re-signaled and
  when one same-domain failure outcome records an Error, including repeated
  observation of a failed Future.
- Defined cancelled-Future observation as a fresh `Cancelled` Error instance per
  `value()` call while `failed(error)` retains and re-signals the exact stored
  Error within one isolation domain.
- Standardized the minimal portable I/O Error family rooted at `IOError`, with
  `InvalidIOArgument`, `IOLifecycleError`, `IOCapacityExhausted`, `EncodingError`,
  and `LineTooLong`; other operational/backend/open/path/filesystem failures
  remain `IOError` unless a narrower category is explicitly named.
- Reaffirmed that fatal Actor Errors remain Actor-local, P/value-transfer
  boundaries reconstruct Error values under ordinary transfer rules, and Core
  handlers cannot resume or retry an abandoned signaling point.
- Kept retry safety dependent on I/O commitment/effect contracts rather than
  Error category. The informative Abstract Runtime already conforms and required
  no duplicate normative authority.

### Compatibility
- Closes previously implementation-selectable Error identity and I/O category
  behavior. Implementations that reused standard Error prototypes/singletons for
  runtime failures must create semantically fresh instances where required;
  portable programs may distinguish recorded identity with `===` and categories
  by ordinary delegation.

## [0.1.344] - 2026-09-04

### Fixed
- Made `semantics/VALUES_AND_COLLECTIONS.md` the single normative owner of the
  complete Core v0.1 numeric arithmetic compatibility and conversion model.
- Defined the exact portable numeric inventory: unbounded ordinary `Integer`,
  IEEE 754-2019 binary64 `Float`, and the eight fixed-width signed/unsigned
  integer families. Core v0.1 defines no generic `Int` / `UInt` family and no
  portable `SmallInteger` / `BigInteger` representation prototypes.
- Prohibited implicit arithmetic promotion between distinct numeric families and
  fixed result families for same-family `+`, `-`, `*`, unary negation, `/`,
  `div`, `mod`, and `%`, including checked fixed-width overflow.
- Defined explicit numeric conversion factories, including exact/range-checked
  integer conversion and normative `roundTiesToEven` precision loss for explicit
  exact-integer-to-`Float` conversion.
- Defined exact-integer `/` by direct exact-rational-to-binary64 rounding,
  including infinity, subnormal, and signed-zero outcomes; exact-integer division
  by zero signals an `Error`.
- Completed cross-family numeric ordering without promotion and reconciled it
  with existing exact numeric `==`, family-sensitive `===`, and cross-family
  hash coherence.
- Made arbitrary-precision Integer representation non-observable across
  delegation, dispatch, reflection, equality, identity, hashing, Actor/P
  transfer, optimization, and external encoding.
- Scoped numeric arithmetic result-family terminology so independently owned
  result contracts such as `Array.size`, `Map.size`, `IdentityMap.size`, and
  `Bytes.size` remain unchanged.

### Compatibility
- Closes previously implementation-selectable numeric promotion, result-family,
  fixed-width overflow, conversion precision, and internal-representation
  behavior. Portable code requiring a family change must use explicit numeric
  conversion rather than mixed-family standard arithmetic.
- The existing numeric equality/hash model is preserved and completed for
  ordering. The informative Abstract Runtime already requires exact
  Integer/Float comparison and therefore needs no duplicate numeric matrix.

## [0.1.343] - 2026-09-04

### Clarified
- Clarified that Core v0.1 requires exactly the two canonical semantic Boolean
  values `true` and `false`; the Boolean-family terminology does not itself
  require a third standard prelude object or prototype named `Boolean`.
- Delegation to `true`, `false`, or an object supplying standard Boolean protocol
  behavior does not confer canonical Boolean membership. Standard checks that
  require a Boolean accept exactly canonical `true` or canonical `false`.
- Preserved ordinary dispatch and custom `ifTrue` / `ifFalse` / `and` / `or`
  implementations without introducing truthiness or changing the Boolean protocol
  contract established by the preceding revision.

### Compatibility
- This is a terminology/domain clarification only; it closes a possible inference
  of an otherwise-unspecified mandatory `Boolean` prototype and changes no
  `ifTrue`, `ifFalse`, `and`, `or`, `&&`, or `||` behavior.
## [0.1.342] - 2026-09-04

### Fixed
- Made `semantics/VALUES_AND_COLLECTIONS.md` §16 the single normative owner of
  the complete standard Boolean protocol for canonical `true` and `false`.
- Defined exact `ifTrue`, `ifFalse`, `and`, and `or` results, selected-only
  callback callability validation/invocation, and strict canonical-Boolean result
  validation for `and` / `or` without introducing truthiness.
- Distinguished ordinary argument evaluation and Closure creation from lazy
  callback-body execution, including unselected non-invokable callbacks.
- Defined propagation of Error, non-local control, callback effects, Future
  results, and explicit suspension, with no hidden Boolean suspension point.
- Strengthened Grammar-owned `&&` / `||` lowering from conceptual wording to one
  mandatory ordinary-message lowering through generated zero-argument Closures.
- Preserved ordinary selector dispatch and custom overriding while applying the
  standard semantic-family receiver-domain rule only to the standard Boolean
  behavior. The informative Abstract Runtime was already compatible and required
  no semantic rewrite.

### Compatibility
- Closes previously underspecified observable Boolean behavior and operator
  short-circuit details; no syntax, language-wide truthiness, or new executable
  value category is introduced.
## [0.1.341] - 2026-09-04

### Fixed
- Defined ordinary semantic `Sequence` normal-completion results in
  `semantics/EXECUTION_AND_CONTROL.md`: the final expression value for a
  non-empty Sequence and canonical `null` for a zero-expression Sequence.
- Made zero-expression source module/program bodies and braced Closure bodies
  deterministic without converting non-local return, Error unwind,
  cancellation, or other control transfer into `null`.
- Kept `object-body-sequence` outside this rule so empty object construction
  retains its independent `OBJECT_MODEL.md` construction semantics.
- Updated `semantics/CALLABLES.md` to derive normal Closure return from the
  owned Sequence contract and marked implementation blocker B001 `READY`.
- The informative Abstract Runtime already initializes Sequence evaluation to
  `null`, so no runtime semantic rewrite is required.

### Compatibility
- This revision closes previously unspecified observable behavior: an empty
  semantic `Sequence` that completes normally now produces canonical `null`.

## [0.1.340] - 2026-09-04

### Deterministic Closure parameter binding
- Made `spec/semantics/CALLABLES.md` the normative owner of the complete
  Closure invocation binding algorithm for supplied positional arguments,
  defaults, rest, `args`, spread, and trailing-closure contributions.
- Fixed left-to-right incremental parameter-slot establishment and default
  evaluation in the real invocation activation, including visibility of
  earlier bindings and ordinary lookup for not-yet-bound later names.
- Defined exact `args`/rest contents, spread composition, arity/error
  precedence, partial-effect behavior, `this`/`context` visibility, handler
  interaction, and default-expression non-local return semantics.
- Aligned `spec/runtime/ABSTRACT_RUNTIME.md` so return-home state is
  established before binding and owned homes are active while defaults run.

### Compatibility
- Previously underspecified combinations of default/rest/spread binding now
  have one deterministic observable result; implementations relying on a
  different default-binding order or environment must conform to this rule.

## [0.1.339] - 2026-09-04

### Documentation taxonomy cleanup
- Organized non-normative documentation into `docs/design/` and `docs/project/`
  according to document purpose and added `docs/README.md` as the taxonomy owner.
- Moved design philosophy and exploratory ideas under `docs/design/`, and project
  task/blocker ledgers under `docs/project/`.
- Removed the empty root `TODO.md`; `docs/project/OPEN_TASKS.md` is the canonical
  concrete-work ledger.
- Updated specification and agent references to the new documentation paths.

### Compatibility
- No observable Protos behavior is changed.

## [0.1.338] - 2026-09-04

### Abstract Runtime ownership cleanup
- Removed duplicated lexer, tokenization, String-literal, newline/comment, and
  parser-separator contracts from `spec/runtime/ABSTRACT_RUNTIME.md`.
- Replaced those copies with references to the normative grammar owner while
  retaining only runtime-relevant evaluator facts.

### Compatibility
- No observable Protos behavior is changed; this removes duplicate explanatory
  authority created or exposed by specification modularization.

## [0.1.337] - 2026-09-04

### Post-modularization structural cleanup
- Removed migration-only H1 markers from `spec/concurrency/ACTORS.md`,
  `spec/concurrency/DISTRIBUTED_RUNTIME.md`, and `spec/semantics/MODULES.md`.
- Reworded the distributed-runtime reference to concurrency design notes so its
  non-normative status is stated directly rather than tied to migration revision 328.

### Compatibility
- No observable Protos behavior is changed.

## [0.1.336] - 2026-09-04

### Specification revision governance simplified
- Established one revision stream for the complete Protos specification: the
  newest `0.1.N` entry in this changelog.
- Removed per-document `Document revision` metadata from specification files.
- Changelog entries identify the documents/domains affected by each revision;
  Git history is the authoritative exact per-file history.
- Updated root and specification agent guidance to prohibit independent or
  artificially synchronized document revision numbers.

### Compatibility
- No observable Protos behavior is changed.

## [0.1.335] - 2026-09-04

### Design-governance cleanup
- Corrected the provenance text in `docs/design/CONCURRENCY_DESIGN.md` after the
  legacy ledger retirement.
- Removed obsolete root-agent rules that could make `CLOSED` sections in the
  non-normative design notes appear normative.
- Established that all design-note status labels are non-normative and that a
  design decision becomes language semantics only when incorporated into its
  owning specification documents.

### Compatibility
- No observable Protos behavior is changed.

## [0.1.334] - 2026-09-04

### Legacy concurrency ledger retired
- Removed `docs/design/CONCURRENCY_DESIGN.md` after completing normative
  concurrency modularization and ownership verification.
- Preserved substantive unresolved/directional design material in the
  non-normative `docs/design/CONCURRENCY_DESIGN.md`.
- Discarded migration-only redirect sections; Git history remains the record of
  those relocations.
- Updated active authority/navigation references so no normative document
  depends on the retired ledger.

### Compatibility
- No observable Protos behavior is changed.

## [0.1.333] - 2026-09-04

### Migration ownership fixed
- Corrected two nested subsections that were carried with the wrong parent H2
  during revision 332.
- Moved `Map comparison restriction across suspension` from Object Model to
  `VALUES_AND_COLLECTIONS.md`, where Map keyed-state semantics are owned.
- Removed duplicate `ensure` cleanup Error-precedence authority from
  `EXECUTION_AND_CONTROL.md`; Error precedence remains owned by `ERRORS.md`.
- Kept a compact cross-domain reference in Execution so cleanup/unwind and Error
  semantics compose without becoming duplicate normative authorities.

### Compatibility
- No observable behavior is intentionally changed.

## [0.1.332] - 2026-09-04

### Migration completed
- Completed the residual modularization of `PROTOS_LANGUAGE_SPEC.md` left by the
  original section-number-based migration.
- Replaced duplicated identifier/separator/operator/error/Future/concurrency
  contracts in Language with direct ownership anchors.
- Moved remaining callable/invocation contracts to `semantics/CALLABLES.md`.
- Moved remaining cleanup/control contracts to `semantics/EXECUTION_AND_CONTROL.md`.
- Moved remaining numeric, String/Bytes/Encoding, Boolean conditional, and Map
  contracts to `semantics/VALUES_AND_COLLECTIONS.md`.
- Moved Core reflection to `semantics/OBJECT_MODEL.md` and parameter-name
  uniqueness to `PROTOS_GRAMMAR.md`.
- Corrected the Standard Array parallel-operations anchor to
  `concurrency/PARALLEL_EXECUTION.md`.
- Left Language as the global core/principles document plus compatibility and
  cross-domain navigation anchors rather than a second domain specification.

### Compatibility
- This revision completes normative ownership relocation without intentionally
  changing observable Protos behavior.

## [0.1.331] - 2026-09-04

### Ownership clarified
- Split syntax/desugaring ownership from callable semantics after the modular
  ownership audit.
- Re-established `PROTOS_GRAMMAR.md` as the sole primary owner of Closure source
  forms, trailing-closure attachment, custom symbolic operator lexing/parsing,
  precedence/associativity, and mandatory syntactic desugarings.
- Reduced `CALLABLES.md` §§9, 18, and 21.1 to callable-semantic consequences and
  direct references to the Grammar instead of duplicating syntax contracts.
- Updated Language compatibility anchors and root authority guidance to reflect
  the split ownership boundary.

### Compatibility
- No observable behavior is intentionally changed; duplicated normative wording
  is removed so independent implementations have one syntax authority.

## [0.1.330] - 2026-09-04

### Ownership corrected
- Corrected semantic-module placement exposed by the post-migration audit.
- Moved §9 `Closures` from `EXECUTION_AND_CONTROL.md` to `CALLABLES.md`.
- Moved §17 `Iteration and Loops` from `VALUES_AND_COLLECTIONS.md` to
  `EXECUTION_AND_CONTROL.md`.
- Moved §18 `Trailing Closures` and §21.1 `Custom Symbolic Binary Operators`
  from `VALUES_AND_COLLECTIONS.md` to `CALLABLES.md`.
- Corrected Language compatibility anchors, module ownership descriptions, and
  active cross-references to those sections.

### Compatibility
- This revision changes normative ownership/location only. The migrated section
  bodies are preserved without intended observable semantic change.

## [0.1.329] - 2026-09-04

### Cleaned
- Completed post-migration authority cleanup after revision 328.
- Removed stale root-AGENTS text that still described unmigrated normative
  concurrency sections.
- Removed `runtime/ABSTRACT_RUNTIME.md` from the normative core authority set and
  documented it consistently as informative.
- Expanded root authority guidance to list all five normative I/O modules.
- Rewrote stale cross-document citations that still treated
  `docs/design/CONCURRENCY_DESIGN.md` as a normative owner.
- Updated `DISTRIBUTED_RUNTIME.md` to state that the legacy ledger is entirely
  non-normative and contains only unresolved design/history material.
- Updated specification-agent guidance from ongoing-migration language to the
  completed modular ownership model.

### Compatibility
- This revision changes references, authority descriptions, and documentation
  consistency only. It does not intentionally change observable Protos behavior.

## [0.1.328] - 2026-09-04

### Migrated
- Migrated the remaining exact-CLOSED concurrency-ledger contracts.
- Moved legacy §§1-4, §6, §32, and §33 to `concurrency/ACTORS.md`.
- Split mixed legacy §5 by primary ownership: Actor-turn semantics to
  `ACTORS.md`, task/error-handler integration to `FUTURES_AND_TASKS.md`,
  P failure transfer to `PARALLEL_EXECUTION.md`, and Map comparison/suspension
  integration to `semantics/VALUES_AND_COLLECTIONS.md`.
- Moved legacy §34 and §34A Actor/module-state contracts to
  `semantics/MODULES.md`.
- Moved legacy §72 standard-prelude sharing to `semantics/MODULES.md`.
- Moved legacy §72A-§72D application/service-identity, discovery, ActorRef-routing,
  and logical-vs-physical-topology boundaries to
  `concurrency/DISTRIBUTED_RUNTIME.md`.

### Authority
- Retired `docs/design/CONCURRENCY_DESIGN.md` as a normative source. It is now an
  entirely non-normative historical/design ledger.
- Reclassified `runtime/ABSTRACT_RUNTIME.md` as informative non-normative
  pseudocode constrained by the modular normative specifications.
- Updated Language and AGENTS authority descriptions accordingly.

### Compatibility
- This revision changes specification ownership and organization only; it does
  not intentionally change observable Protos behavior.

## [0.1.327] - 2026-09-04

### Migrated
- Promoted all six `spec/semantics/` migration-index files to normative Draft
  modules in one macro migration.
- Moved the Language object-model family into `semantics/OBJECT_MODEL.md`.
- Moved execution/control sections into `semantics/EXECUTION_AND_CONTROL.md`.
- Moved Closure/method/invocation/return sections into `semantics/CALLABLES.md`.
- Moved module-context and module-loading/lifecycle sections into
  `semantics/MODULES.md`.
- Moved Core Error semantics into `semantics/ERRORS.md`.
- Moved immutable-value, equality/identity, indexed-access, Array/Map/Bytes and
  related collection semantics into `semantics/VALUES_AND_COLLECTIONS.md`.
- Replaced migrated Language bodies with compatibility/navigation anchors.

### Ownership
- `PROTOS_LANGUAGE_SPEC.md` remains the language front door and integration
  specification; migrated semantic-domain rules have exactly one primary owner
  under `spec/semantics/`.
- Updated root specification-authority guidance for the six semantic modules.

### Compatibility
- This revision changes specification location/ownership only and does not
  intentionally change observable Protos behavior.

## [0.1.326] - 2026-09-04

### Migrated
- Performed a macro migration of two specification families in one revision.
- Promoted `concurrency/DISTRIBUTED_RUNTIME.md` to the primary normative owner
  of every legacy concurrency-ledger section numbered 35-70 whose section-local
  status was exactly `CLOSED` or `CLOSED --- REVISED`.
- Design-qualified distributed sections remain non-normative in the transitional
  concurrency ledger.
- Split the complete former `PROTOS_IO_MODEL.md` into five normative modules:
  `io/IO_CORE.md`, `io/BYTE_IO.md`, `io/TEXT_IO.md`, `io/FILESYSTEM.md`, and
  `io/PROCESS_IO.md`.
- Removed `PROTOS_IO_MODEL.md`; legacy section numbers remain in the modular
  files for citation continuity.

### Compatibility
- This revision changes specification location and ownership only; it does not
  intentionally change observable Protos behavior.
- Historical changelog references retain historical filenames.

## [0.1.325] - 2026-09-04

### Migrated
- Completed the remaining CLOSED Actor-domain migration from the mixed
  concurrency ledger into `concurrency/ACTORS.md`: Buffer transfer policy,
  end-to-end backpressure, transport transparency/policy/locality, mailbox
  bounds, communication timeout/deadline semantics, Actor-boundary control and
  handler confinement, foreign-call/isolation/resource boundaries, failure
  authority, incarnation identity, failure delivery consequences, lifecycle
  monitoring, and Actor runtime-health semantics.
- Moved the remaining CLOSED Future-specific §24F race/select boundary and §24G
  Future/Actor ownership matrix into `concurrency/FUTURES_AND_TASKS.md`.
- Replaced every migrated ledger body with a compatibility/navigation anchor;
  no migrated rule remains independently normative in the mixed ledger.

### Documentation
- This revision changes specification location/ownership only and does not
  intentionally change observable Protos behavior.
- Updated `docs/design/CONCURRENCY_DESIGN.md`, `concurrency/ACTORS.md`, and
  `concurrency/FUTURES_AND_TASKS.md` to document revision 325.
- Other revisioned normative documents are unaffected.

## [0.1.324] - 2026-09-04

### Migrated
- Promoted `concurrency/PARALLEL_EXECUTION.md` from a non-normative migration
  index to the primary normative owner of the complete legacy §71 isolated
  parallel-execution family.
- Replaced legacy ledger §71 with a compact compatibility/navigation anchor.
- Updated current Language and Abstract Runtime references from concurrency-ledger
  §71 to `concurrency/PARALLEL_EXECUTION.md` §71.

### Fixed
- P9 audit found that legacy Actor section §11A was not moved because its
  alphanumeric section number fell outside the numeric 7-18 extraction loop.
  Moved §11A into `concurrency/ACTORS.md` and replaced the ledger copy with a
  compatibility anchor.
- Updated root `AGENTS.md` specification-authority guidance to describe the
  modular Future, Actor, and Parallel owners that now exist, while retaining the
  mixed concurrency ledger only for still-unmigrated CLOSED material and
  unresolved design work.

### Documentation
- This revision changes specification location/ownership only and does not
  intentionally change observable Protos behavior.
- Updated `docs/design/CONCURRENCY_DESIGN.md`, `concurrency/ACTORS.md`,
  `concurrency/PARALLEL_EXECUTION.md`, `PROTOS_LANGUAGE_SPEC.md`, and
  `runtime/ABSTRACT_RUNTIME.md` to document revision 324.
- Other revisioned normative documents are unaffected.

## [0.1.323] - 2026-09-04

### Migrated
- Promoted `concurrency/ACTORS.md` from a non-normative migration index to the
  primary normative Actor-domain specification.
- Moved the contiguous Actor communication family from legacy ledger sections
  7-18 into `ACTORS.md`: ordering/fairness, Actor bootstrap and initialization,
  behavior/readiness, message dispatch, `send()`, `request()`, shared delivery,
  pass-by-value transfer, message snapshot timing, and transfer optimizations.
- Moved Actor lifecycle sections 24A-24D into `ACTORS.md`: graceful termination,
  reachability versus lifetime, fatal unhandled Actor errors, and Actor-local
  cooperative non-preemption.
- Replaced the corresponding mixed-ledger bodies with compact compatibility
  anchors that explicitly define no duplicate normative contract.

### Changed
- Updated `concurrency/FUTURES_AND_TASKS.md` to reference `ACTORS.md` for
  Actor-local cooperative non-preemption.
- This revision changes specification location/ownership only and does not
  intentionally change observable Protos behavior.

### Documentation
- Updated `docs/design/CONCURRENCY_DESIGN.md`, `concurrency/ACTORS.md`, and
  `concurrency/FUTURES_AND_TASKS.md` to document revision 323.
- Other normative documents are unaffected.

## [0.1.322] - 2026-09-04

### Added / Migrated
- Began the physical modularization of `spec/`; this is the first revision in
  which the target directory structure exists in the repository rather than only
  as an architectural plan.
- Created `spec/semantics/`, `spec/concurrency/`, `spec/io/`, and `spec/runtime/`.
- Moved `PROTOS_RUNTIME_SEMANTICS.md` to
  `runtime/ABSTRACT_RUNTIME.md` and updated current repository references.
- Created `concurrency/FUTURES_AND_TASKS.md` as the primary normative owner of
  the Future/task family already canonicalized in revisions 316-320.
- Moved the full Language Future sections 26-31 and the concurrency-owned
  `Future.then`, cancellation §23, structured-task §24, and `Future.all` §24E
  contracts into `concurrency/FUTURES_AND_TASKS.md`.
- Left compact compatibility/navigation anchors in the former Language and mixed
  concurrency documents; those anchors explicitly define no duplicate contract.

### Structure
- Materialized the planned target files for semantic, Actor/parallel/distributed,
  and I/O modules as explicit non-normative migration indexes.
- Those migration-index files do not acquire normative authority merely by
  existing; each domain becomes normative there only when a later migration
  revision transfers its actual contract.
- The modular tree now includes:
  `semantics/{OBJECT_MODEL,EXECUTION_AND_CONTROL,CALLABLES,MODULES,ERRORS,VALUES_AND_COLLECTIONS}.md`,
  `concurrency/{FUTURES_AND_TASKS,ACTORS,PARALLEL_EXECUTION,DISTRIBUTED_RUNTIME}.md`,
  `io/{IO_CORE,BYTE_IO,TEXT_IO,FILESYSTEM,PROCESS_IO}.md`, and
  `runtime/ABSTRACT_RUNTIME.md`.

### Documentation
- This revision is primarily structural/ownership migration; it does not
  intentionally change observable Protos behavior.
- Updated `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `docs/design/CONCURRENCY_DESIGN.md`, `PROTOS_IO_MODEL.md`, and the moved
  `runtime/ABSTRACT_RUNTIME.md` to document revision 322 because each current
  document/path reference changes in this revision.
- Updated current README/AGENTS/documentation references to the new Runtime path.
- Historical changelog entries retain historical filenames.

## [0.1.321] - 2026-09-04

### Changed
- Canonicalized the large Runtime concurrency/Actor lifecycle integration block.
- Removed duplicate conceptual Runtime authority for Actor graceful termination,
  Actor-local cancellation cleanup, failure authority, termination observation,
  Actor bootstrap, delivery admission fairness, Process/Node reachability and
  termination classification, Cluster membership, and split-brain/Authority
  handling.
- Replaced those algorithms with a compact integration section referring to
  `docs/design/CONCURRENCY_DESIGN.md` as the primary normative owner and
  `PROTOS_IO_MODEL.md` for I/O-specific commitment/cancellation specialization.
- Preserved runtime freedom to use internal lifecycle records, queues, membership
  views, callbacks, probes, epochs, compact terminal metadata, or distributed
  protocols only when programmer-visible behavior remains that of the owning
  specifications.
- Fixed the duplicate `# 34. Future Composition` heading accidentally introduced
  during revision 320.
- Removed a malformed open pseudocode fence that had caused subsequent Runtime
  Markdown subsections to be presented as if they were part of one code block.

### Documentation
- This revision changes specification ownership/presentation and removes duplicate
  authority; it does not intentionally change observable Protos behavior.
- Updated only `PROTOS_RUNTIME_SEMANTICS.md` to document revision 321.
- `PROTOS_LANGUAGE_SPEC.md` remains at document revision 320.
- `docs/design/CONCURRENCY_DESIGN.md` remains at document revision 319.
- `PROTOS_GRAMMAR.md` and `PROTOS_IO_MODEL.md` are unaffected.

## [0.1.320] - 2026-09-04

### Added / Closed
- Closed the remaining `Future.value()` specification gap.
- Defined `Cancelled` as a standard Error prototype and standard-prelude binding
  delegating directly to `Error`.
- Defined `Future.value()` on a cancelled Future to signal the standard
  `Cancelled` object as a fresh non-resumable consumer-side signaling event.
- Made `PROTOS_LANGUAGE_SPEC.md` §29 the primary normative owner of
  `Future.value()` observation semantics.
- Moved the existing lost-wakeup exclusion into that owning contract: observing
  `pending` and registering the waiting continuation are semantically atomic with
  respect to the Future's first terminal transition.
- Required every still-live waiter registered before the first terminal transition
  to become eligible to resume and required terminal waiter registrations to be
  cleared or made inert.
- Integrated waiting-task cancellation by reference to the existing concurrency
  §23 boundaries without propagating cancellation upstream to the observed Future.

### Changed
- Removed the duplicate conceptual `wakeWaiters`, `awaitFutureValue`, and
  `suspendOnPendingFuture` algorithms from `PROTOS_RUNTIME_SEMANTICS.md`; runtime
  waiter machinery is now explicitly implementation freedom subject to Language
  §29 and Concurrency §23.
- Retained only the implementation-boundary rule that internal task/fiber/
  continuation records are not Protos values.
- Fixed two duplicate introductory lines accidentally left in Runtime during the
  ownership-migration patches.
- This revision deliberately standardizes the previously pseudocode-only
  `Cancelled` observation category; it closes accidental implementation freedom
  rather than adding a second cancellation mechanism.

### Documentation
- Updated `PROTOS_LANGUAGE_SPEC.md` and `PROTOS_RUNTIME_SEMANTICS.md` to document
  revision 320.
- `docs/design/CONCURRENCY_DESIGN.md` remains at document revision 319 because its
  normative content is unchanged.
- `PROTOS_GRAMMAR.md` and `PROTOS_IO_MODEL.md` are unaffected and remain
  byte-for-byte unchanged.

## [0.1.319] - 2026-09-04

### Changed
- Canonicalized the Core Future state/resolution/adoption family.
- Made `PROTOS_LANGUAGE_SPEC.md` §28 the primary normative owner of the four
  Future states, first-terminal-transition stability, normal resolution,
  domain-local failure/Error identity, Future outcome adoption/flattening,
  adoption cancellation direction, and `FutureResolutionCycle`.
- Removed the duplicate conceptual Future resolution/adoption and failure-storage
  algorithms from `PROTOS_RUNTIME_SEMANTICS.md`; runtime representation of those
  mechanisms is now explicitly implementation freedom subject to the owning
  contracts.
- Reduced the `Future.then()` concurrency section to its genuine
  continuation-task/ownership/scheduling specialization and made generic
  flattening reference Language §28.
- Retained Future waiter bookkeeping in Runtime temporarily because the remaining
  `Future.value()` ownership migration is blocked by an existing specification
  gap described below.

### Specification gap
- `PROTOS_RUNTIME_SEMANTICS.md` currently uses pseudocode `Cancelled` when
  `Future.value()` observes a cancelled Future, but no normative specification
  defines `Cancelled` as a standard Error prototype or otherwise defines the
  exact observable cancellation-observation Error category.
- Under the Core Error-taxonomy rule, a pseudocode-only name does not become a
  portable standard Error prototype merely by appearing in Runtime pseudocode.
- This revision therefore does not invent or silently standardize `Cancelled`.
  The remaining `Future.value()` canonicalization must close that semantic gap
  explicitly before Runtime's waiter/observation contract can be fully demoted.

### Documentation
- This revision changes specification ownership and exposes an existing gap; it
  does not intentionally change observable Protos behavior.
- Updated `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_RUNTIME_SEMANTICS.md`, and
  `docs/design/CONCURRENCY_DESIGN.md` to document revision 319.
- `PROTOS_GRAMMAR.md` and `PROTOS_IO_MODEL.md` are unaffected and remain
  byte-for-byte unchanged.

## [0.1.318] - 2026-09-04

### Changed
- Canonicalized the broader Core Future cancellation and structured-ownership
  family in one migration unit.
- Made `docs/design/CONCURRENCY_DESIGN.md` §23 the explicit primary normative owner of
  cooperative cancellation, `Future.cancel()`, portable cancellation boundaries,
  cancellation-runnable pre-start/suspended work, and cancellation wake-up rules.
- Made `docs/design/CONCURRENCY_DESIGN.md` §24 the explicit primary normative owner of
  structured Future/task ownership, structured completion/unwind, cleanup, and
  `Future.detach()` semantics.
- Kept Actor-local cooperative non-preemption under the existing §24D ownership.
- Replaced the duplicated cancellation and structured-concurrency contracts in
  `PROTOS_LANGUAGE_SPEC.md` with compact language-surface/cross-domain references.
- Removed the duplicate conceptual cancellation, structured ownership, and
  detachment algorithms from `PROTOS_RUNTIME_SEMANTICS.md` while retaining
  runtime-oriented Actor-lifecycle integration.
- Left Future resolution/adoption and the mixed language/concurrency semantics of
  `Future.value()` for separate ownership canonicalization.

### Documentation
- This revision changes specification ownership and removes duplicate authority;
  it does not intentionally change observable Protos behavior.
- Updated `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_RUNTIME_SEMANTICS.md`, and
  `docs/design/CONCURRENCY_DESIGN.md` to document revision 318.
- `PROTOS_GRAMMAR.md` and `PROTOS_IO_MODEL.md` are unaffected and remain
  byte-for-byte unchanged.

## [0.1.317] - 2026-09-04

### Changed
- Continued canonicalization of duplicated normative authority under the
  one-primary-owner discipline.
- Made `docs/design/CONCURRENCY_DESIGN.md` §24E the explicit primary normative owner
  of `Future.all(futures...) -> Future` concurrency-domain semantics.
- Replaced the duplicated full `Future.all(...)` contract in
  `PROTOS_LANGUAGE_SPEC.md` with a compact language-surface and cross-domain
  integration reference.
- Removed the duplicate conceptual aggregate-observation algorithm from
  `PROTOS_RUNTIME_SEMANTICS.md`; runtime observation/frontier machinery remains
  free subject to the owning concurrency contract.
- Kept the separate Core boundary excluding generic first-completion
  `Future.race(...)` / `Future.select(...)` intact.

### Documentation
- This revision changes specification ownership and removes duplicate authority;
  it does not intentionally change observable Protos behavior.
- Updated `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_RUNTIME_SEMANTICS.md`, and
  `docs/design/CONCURRENCY_DESIGN.md` to document revision 317.
- `PROTOS_GRAMMAR.md` and `PROTOS_IO_MODEL.md` are unaffected and remain
  byte-for-byte unchanged.

## [0.1.316] - 2026-09-04

### Changed
- Continued canonicalization of duplicated normative authority under the
  one-primary-owner discipline.
- Made the `Future then() continuations` section of
  `docs/design/CONCURRENCY_DESIGN.md` the explicit primary normative owner of
  `Future.then(transform) -> Future` concurrency-domain semantics.
- Replaced the duplicated full `Future.then(...)` contract in
  `PROTOS_LANGUAGE_SPEC.md` with a compact language-surface and cross-domain
  integration reference.
- Removed the duplicate conceptual `futureThen` algorithm from
  `PROTOS_RUNTIME_SEMANTICS.md`; runtime continuation machinery remains free
  subject to the owning concurrency contract.
- Kept `Future.all(...)`, Future observation, cancellation, detachment,
  structured ownership, and general Future resolution/adoption outside this
  migration unit so their ownership can be canonicalized independently.

### Documentation
- This revision changes specification ownership and removes duplicate authority;
  it does not intentionally change observable Protos behavior.
- Updated `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_RUNTIME_SEMANTICS.md`, and
  `docs/design/CONCURRENCY_DESIGN.md` to document revision 316.
- `PROTOS_GRAMMAR.md` and `PROTOS_IO_MODEL.md` are unaffected and remain
  byte-for-byte unchanged.

## [0.1.315] - 2026-09-04

### Changed
- Began canonicalization of existing duplicated normative authority under the
  one-primary-owner discipline introduced by revision 0.1.314.
- Made `docs/design/CONCURRENCY_DESIGN.md` §71.6A–§71.6E the primary normative owner
  of the concurrency-domain semantics for the standard
  `Array.parallelMap(...)`, `Array.parallelFilter(...)`,
  `Array.parallelFindIndex(...)`, `Array.parallelReduce(...)`, and
  `Array.parallelSort(...)` operations.
- Replaced the duplicated full parallel-Array contracts in
  `PROTOS_LANGUAGE_SPEC.md` with a compact cross-domain integration reference
  preserving ordinary Array receiver-domain and invocation semantics.
- Removed the duplicate conceptual parallel-Array algorithms from
  `PROTOS_RUNTIME_SEMANTICS.md`; runtime implementations remain constrained by
  the owning concurrency contract without acquiring a second pseudocode
  authority.
- Reconciled §71.6's stale statement that exact names/APIs were undecided with
  the already-closed standard APIs in §71.6A–§71.6E.
- Clarified that other high-level parallel patterns remain library/API design
  space unless standardized explicitly.

### Documentation
- This revision changes specification ownership and removes contradictory or
  duplicate authority; it does not intentionally change observable Protos
  language or concurrency behavior.
- Updated `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_RUNTIME_SEMANTICS.md`, and
  `docs/design/CONCURRENCY_DESIGN.md` to document revision 315.
- `PROTOS_GRAMMAR.md` and `PROTOS_IO_MODEL.md` are unaffected and remain
  byte-for-byte unchanged.

## [0.1.314] - 2026-09-04

### Changed
- Decoupled per-document `Document revision` values from the global specification
  revision to remove artificial cross-domain edit collisions.
- Defined the newest changelog entry as the single global specification revision.
- Required each normative document revision to advance only when that document's
  content actually changes; unaffected normative documents now remain
  byte-for-byte unchanged.
- Established one-primary-owner discipline for observable normative rules and
  prohibited duplicated cross-document normative authority.
- Clarified that cross-domain documents should reference owned semantics and add
  only genuine domain-specific specializations.
- Clarified that abstract runtime pseudocode must not become an independent
  second authority for programmer-visible behavior.
- Recorded existing duplicated normative material as technical debt to be
  canonicalized during the specification modularization.

### Documentation
- Updated repository and `spec/` agent instructions plus the specification
  changelog policy. No Protos language, grammar, runtime, concurrency, or I/O
  semantics changed in this revision.
- No normative document `Document revision` was advanced solely for this
  governance change.

## [0.1.313] - 2026-09-04

### Fixed
- Closed the cancellation/publication race in `Bytes.parallelRange(...)` and
  recursive `ByteRegion.parallelRange(...)`.
- Defined successful reserved-byte publication and successful Future
  terminalization as one indivisible semantic commitment with respect to
  cancellation.
- Required cancellation that terminalizes the Future first to release the
  reservation without publishing parent-region mutation.
- Required successful publication that commits first to replace exactly the
  reserved bytes and resolve the Future successfully; later `cancel()` is the
  ordinary terminal-Future no-op.
- Prohibited the observable combination of committed parent-region bytes with a
  `cancelled` result Future.
- Kept the atomicity semantic rather than prescribing locks, CAS, scheduler
  serialization, or any other physical implementation mechanism.

### Changed
- Synchronized all revisioned specification documents to revision 313.
  `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_RUNTIME_SEMANTICS.md`, and
  `docs/design/CONCURRENCY_DESIGN.md` gain normative clarification in this revision.

## [0.1.312] - 2026-09-04

### Fixed
- Reconciled invocation-time `ByteWritable.write` snapshot semantics with finite
  end-to-end resource bounds and the no-hidden-suspension rule.
- Defined write admission to include finite retention/reservation sufficient to
  preserve the admitted write's immutable logical snapshot.
- Permitted an already-failed Future for implementation/host resource-capacity
  exhaustion when such bounded admission capacity is unavailable.
- Required capacity rejection to occur before output admission, contribute zero
  bytes/effects/frontiers, and leave an otherwise usable receiver unpoisoned.
- Prohibited implementations from returning a pending write whose snapshot they
  cannot preserve, blocking/suspending inside `write()` for capacity, or imposing
  hidden borrow/freeze/pin restrictions on the caller's mutable `Bytes`.
- Preserved argument-validation precedence where invalidity is already determined
  from the supplied semantic values.
- Clarified that pending-Future backpressure applies only to writes whose admitted
  snapshot state fits within the finite retained-state bound; excess invocations
  may instead terminate through the explicit capacity-failure channel.

### Changed
- Synchronized all revisioned specification documents to revision 312. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.311] - 2026-09-04

### Fixed
- Required Actor bootstrap entry-point selection to use the destination module
  instance's own local top-level slot rather than ordinary delegated lookup.
- Clarified that a same-named binding inherited from `Context`, `Object`, the
  prelude, or another delegation ancestor cannot satisfy the bootstrap binding.
- Updated the runtime model to validate/read the local module slot directly
  before ordinary invokability validation.
- Preserved ordinary invocation semantics after the bootstrap value itself has
  been selected.

### Changed
- Synchronized all revisioned specification documents to revision 311.
  `PROTOS_RUNTIME_SEMANTICS.md` and `docs/design/CONCURRENCY_DESIGN.md` gain normative
  clarification in this revision.

## [0.1.310] - 2026-09-04

### Fixed
- Removed the contradictory statement in isolated-parallel §71.8 that left P
  cancellation safe points to implementation/API choice.
- Required P to use the same portable cancellation-observation boundaries as
  other task-backed asynchronous work, independent of internal Task
  representation.
- Made the pre-first-instruction P boundary mandatory when cancellation is
  already pending.
- Reaffirmed that method calls, allocations, loop back-edges, JIT/GC safepoints,
  carrier/worker-pool checks, work-stealing boundaries, SIMD/vectorization
  boundaries, and host-thread interruption do not create hidden P cancellation
  observation points.
- Clarified that CPU-bound P code with no later portable boundary may complete
  normally after cancellation is requested.

### Changed
- Synchronized all revisioned specification documents to revision 310.
  `docs/design/CONCURRENCY_DESIGN.md` and `PROTOS_RUNTIME_SEMANTICS.md` gain normative
  clarification in this revision.

## [0.1.309] - 2026-09-04

### Closed
- Closed the Actor bootstrap representation while leaving only the public
  creation API spelling/syntax open.
- Defined bootstrap code by canonical module identity plus one top-level
  destination binding name and explicit initialization argument values.
- Required the destination Actor to load/use its own Actor-local module instance,
  perform ordinary binding lookup/callability validation, and invoke bootstrap
  during `INITIALIZING`.
- Required initialization arguments to cross only through existing Actor
  pass-by-value semantics; no caller Closure, lexical context, module instance,
  handler stack, return home, pending Future, or ambient capability crosses.
- Defined the bootstrap invocation's normal result as the exact destination-local
  behavior object installed for the `INITIALIZING -> READY` cutover.
- Defined module-load, binding lookup/callability, bootstrap Error, and missing
  behavior failures as ordinary Actor initialization failure.
- Avoided introducing a second transferable function/code-value category.

### Changed
- Synchronized all revisioned specification documents to revision 309.
  `PROTOS_RUNTIME_SEMANTICS.md` and `docs/design/CONCURRENCY_DESIGN.md` gain normative
  semantic content in this revision.

## [0.1.308] - 2026-09-04

### Fixed
- Defined compositional `Flushable` semantics for nested standard output wrappers.
- Required a successful wrapper `flush()` to establish an ordered deeper
  `flush()` whenever its immediate target itself exposes `Flushable`, after the
  wrapper has delivered its own frontier to that target.
- Required the same rule to compose recursively through standard flushable
  wrapper chains, preventing an outer successful flush from leaving its frontier
  stranded in a deeper Protos-managed buffer.
- Kept targets without `Flushable` at their actual `ByteWritable` boundary rather
  than inventing host flush, durability, drain, or acknowledgement semantics.
- Preserved target-local ordering: a deeper flush may also propagate independently
  originated target output ordered before its own frontier without creating a
  global or outer-wrapper ordering relation.
- Composed deeper failure/cancellation with existing `Flushable` aftermath rules
  and prohibited outer success when a required deeper flush fails.
- Clarified that this rule does not make `Flushable` automatically inherited by
  arbitrary wrappers.

### Changed
- Synchronized all revisioned specification documents to revision 308. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.307] - 2026-09-04

### Closed
- Closed the relationship between logical Protos topology and physical
  infrastructure topology as an explicit semantic boundary.
- Reaffirmed `Process` as execution capacity, `Node` as runtime membership, and
  `Cluster` as a coordination domain rather than aliases for host infrastructure.
- Defined no implicit one-to-one mapping from Protos identities to OS processes,
  hosts, VMs, containers, pods, CPU/NUMA topology, racks, zones, regions, or
  equivalent infrastructure units.
- Allowed conforming implementations to co-locate, separate, or move physical
  resources without changing logical identity, isolation, authority, lifetime,
  communication, failure, placement, or continuity semantics.
- Clarified that physical relocation is not Actor migration and cannot preserve
  an Actor incarnation unless a future normative migration facility says so.
- Preserved affinity, hard placement, failure-domain, rebalancing, migration,
  capacity, and infrastructure-adapter APIs/policies as separate open topics.
- Removed `Relationship between logical Protos topology and physical
  infrastructure topology` from Open Design Topics.

### Changed
- Updated `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized all five revisioned specification documents to document revision
  307.

## [0.1.306] - 2026-09-04

### Closed
- Closed the open Core Actor behavior-replacement API topic by removing
  post-READY behavior-reference replacement from Core v0.1.
- Defined one ordinary behavior object per Actor incarnation, established before
  the `INITIALIZING -> READY` cutover and retained for that incarnation's
  lifetime.
- Kept application mode/state changes in the ordinary Protos object model rather
  than adding `become`, `unbecome`, behavior stacks, implicit Actor-control
  bindings, or another Actor-specific state-transition mechanism.
- Preserved existing handler-result semantics: `request()` handler results remain
  reply values and `send()` handler results remain ignored, rather than acquiring
  a second "next behavior" interpretation.
- Clarified that ActorRef identifies the Actor incarnation, not its behavior
  object or application-defined mode.
- Left only the exact bootstrap API/syntax for establishing the initial behavior
  as a separate open topic.

### Changed
- Synchronized all revisioned specification documents to revision 306.
  Only `docs/design/CONCURRENCY_DESIGN.md` gains normative semantic content in this
  revision.

## [0.1.305] - 2026-09-04

### Fixed
- Removed the accidental implication that Core v0.1 already standardizes concrete
  `PipeReader` / `PipeWriter` endpoint types merely by listing capability shapes.
- Kept readable and writable pipe-like endpoints free to expose ordinary
  `ByteReadable`/`ByteWritable` plus `Closable` when provided by a library/host.
- Explicitly left pipe creation/pairing and cross-endpoint lifecycle semantics
  outside Core v0.1, including writer-close-to-reader-EOF, reader-close/broken-pipe,
  buffering/capacity, readiness, peer cardinality, and pipe-specific atomic-write
  guarantees.
- Prohibited implementations from importing POSIX, Java, Windows, or another host
  pipe model as portable Protos semantics merely from the generic I/O Traits.
- Required any future standard pipe facility to define those cross-endpoint
  semantics explicitly.

### Changed
- Synchronized all revisioned specification documents to revision 305. Only
  `PROTOS_IO_MODEL.md` gains normative clarification in this revision.


## [0.1.304] - 2026-09-04

### Fixed
- Reconciled recoverable `Flushable` failure with `WriteShutdown` clean-frontier
  semantics.
- Preserved a failed flush Future as failed while allowing a later ordered
  successful flush to repair its propagation requirement when the later frontier
  fully covers the earlier failed flush frontier.
- Required shutdown to wait for that recovering flush's terminal success before
  relying on the recovered frontier.
- Clarified that a recovering flush may cover later output as well and establish
  one combined propagation frontier for all output inside its frontier.
- Kept failed `ByteWritable.write` distinct: later flush of its committed prefix
  does not repair the failed write for WriteShutdown.
- Prohibited `shutdownWrite()` from implicitly retrying/replaying a failed flush;
  without a later successful covering flush, the failed propagation remains an
  unsatisfied shutdown prerequisite.
- Preserved hidden-progress opacity: recovery does not expose the earlier flush's
  partial propagation or rewrite its historical Future outcome.

### Changed
- Synchronized all revisioned specification documents to revision 304. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.303] - 2026-09-04

### Fixed
- Defined failed-Future Error object identity inside one Protos value/isolation
  domain.
- Required ordinary Future failure recording to preserve the exact Error object
  rather than clone, wrap, snapshot, reconstruct, or substitute it.
- Made repeated failed-Future observations re-signal that same Error object while
  preserving the already-defined rule that each observation is a new
  non-resumable signaling event.
- Distinguished object identity from control state: preserving the Error object
  does not preserve producer continuations, handlers, activations, or stacks.
- Clarified that explicit boundaries remain authoritative: P transfers the Error
  into the caller domain before caller-Future failure, while Actor-fatal Errors
  do not implicitly cross Actor boundaries.
- Aligned ordinary Future combinator "same Error" propagation with exact
  domain-local Error identity unless a boundary-specific rule says otherwise.

### Changed
- Synchronized all revisioned specification documents to revision 303.
  `PROTOS_LANGUAGE_SPEC.md` and `PROTOS_RUNTIME_SEMANTICS.md` gain normative
  clarification in this revision.

## [0.1.302] - 2026-09-04

### Fixed
- Defined the exact argument domains of standard one-shot Encoding operations:
  `Encoding.encode` requires a Protos `String`, and `Encoding.decode` requires a
  Protos `Bytes` value.
- Required invalid argument types to fail synchronously before conversion work,
  preserving the explicitly non-Future API shape.
- Prohibited implicit stringification, numeric/character collection conversion,
  host-buffer adaptation, duck-typed extraction, and other
  implementation-selected coercions.
- Kept malformed-input, representability, replacement, and BOM policy evaluation
  after successful establishment of the required semantic argument type.

### Changed
- Synchronized all revisioned specification documents to revision 302. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.301] - 2026-09-04

### Fixed
- Clarified that a fatal unhandled Actor Error remains local failure state of the
  failed Actor incarnation rather than becoming an implicit remote Error channel.
- Defined non-root Actor termination itself as the complete Core failure-authority
  consequence, with no automatic Error transfer/copy/snapshot/proxy/re-signal to
  another Actor.
- Preserved accepted-request `RequestOutcomeUncertain` semantics instead of
  exposing the destination Actor's internal fatal Error to the requester.
- Clarified that RootActor failure may use the Error internally as Process
  termination cause without granting cross-Actor Error identity or transfer.
- Left any future supervision/failure-reporting facility to define an explicit
  transferable report contract rather than synthesizing one in Core v0.1.

### Changed
- Synchronized all revisioned specification documents to revision 301.
  `PROTOS_RUNTIME_SEMANTICS.md` and `docs/design/CONCURRENCY_DESIGN.md` gain normative
  clarification in this revision.

## [0.1.300] - 2026-09-04

### Fixed
- Defined explicit BOM emission for fresh one-shot `Encoding.encode(text)`
  conversions independently of payload emptiness.
- Required an explicitly BOM-emitting one-shot conversion to prepend exactly one
  matching BOM before the encoded text.
- Defined `encode("")` under explicit BOM emission to return exactly the BOM
  bytes, while the default no-BOM configuration returns `Bytes()`.
- Prohibited implementations from suppressing a requested BOM merely because the
  one-shot payload is empty or emitting multiple BOMs for one conversion.
- Kept streaming `TextWriter.writeText("")` semantics unchanged: an empty text
  write remains zero-byte/zero-state-transition and does not itself trigger BOM
  emission, finalization, flush, or reset.

### Changed
- Synchronized all revisioned specification documents to revision 300. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.
