# Protos Language Specification Changelog

All notable changes to the normative Protos language specification are
documented in this file. Non-normative design notes, including
`../docs/design/CONCURRENCY_DESIGN.md`, do not advance the specification revision
by themselves. When a design item becomes normative, update its owning
specification document and record that normative change here.

Specification revision is global: the newest changelog entry `0.1.X` is the
current specification revision. Each new entry identifies the normative
documents or domains changed by that revision. Git history is the authoritative
exact per-file history; normative documents do not carry independent
`Document revision` metadata and otherwise-unaffected documents are not edited
merely to synchronize revision metadata.

## Historical specification changelog

Older global specification revisions are archived without changing their
entry text:

- [0.1.300–0.1.399](changelog/PROTOS_SPEC_CHANGELOG-0.1.300-0.1.399.md)
- [0.1.200–0.1.299](changelog/PROTOS_SPEC_CHANGELOG-0.1.200-0.1.299.md)
- [0.1.100–0.1.199](changelog/PROTOS_SPEC_CHANGELOG-0.1.100-0.1.199.md)
- [0.1.041–0.1.099](changelog/PROTOS_SPEC_CHANGELOG-0.1.041-0.1.099.md)

## [0.1.433] - 2026-09-24

### D179 — Execution Context Object-Model Capability Boundary (Candidate C3: monotonic context membership)
- Implements ratified D179 Candidate C3. `spec/semantics/EXECUTION_AND_CONTROL.md`
  §4 adds "Monotonic Local-Slot Membership": once a local slot of a genuine
  execution context (the object bound to a Closure invocation's fresh
  activation, or to a module's `moduleContext`) becomes present, it can never
  become absent again. `removeSlot(name)` on such an execution context is
  rejected whenever `name` currently identifies a local slot, regardless of
  open/closed/frozen state, and signals the same ordinary `Error` that
  `OBJECT_MODEL.md` §22 already defines for a rejected structural mutation. No
  new Error family or public selector is introduced. Absent-to-present
  creation while open, present-to-present value mutation while writable, late
  creation and its effect on subsequent lexical lookup, closure
  capture-by-reference, context escape, `close()`, `freeze()`, and the
  existing present-`null`-distinct-from-absent invariant are unchanged.
- `spec/semantics/OBJECT_MODEL.md` §22 cross-references this specialization
  from the general `removeSlot(name)` contract it owns; the general contract
  for ordinary objects, including the object under construction that
  temporarily serves as an object-literal body's slot-creation context, is
  unchanged.
- Candidate C0 (retaining unrestricted execution-context slot removal) is
  deferred, not rejected, pending concrete evidence from the Bytecode DSL
  platform work.

## [0.1.432] - 2026-09-21

### D159 — Remove `Future.detach()`; strict structured child lifetime
- Implements ratified D159 Candidate B. Core exposes no public
  `Future.detach()` operation, and no Core operation removes, transfers, or
  re-parents the structured-ownership edge of a task-backed child Future after
  creation. Task-backed child work remains owned by the creating asynchronous
  task scope for the child's complete lifetime.
- `spec/concurrency/FUTURES_AND_TASKS.md` §24, §24E, §24G, §26, §28, §30, §31
  and the `Future then() continuations` contract are reconciled: detachment
  clauses are removed and the complete-lifetime ownership invariant is stated
  directly, including the Actor-termination and non-task-backed consequences.
- `spec/semantics/EXECUTION_AND_CONTROL.md`: the `ensure` structured-scope rule
  no longer contrasts detached work, and the D121 termination-cleanup authority
  rule no longer names a detached-computation category.
- `spec/concurrency/ACTORS.md`: Actor graceful-stop cancellation no longer
  names detached tasks, and the D121 cleanup-authorization rule drops the
  detached-job category.
- `spec/concurrency/PARALLEL_EXECUTION.md`: P-local cooperative tasks lose the
  detachment escape clause; the ownership edge cannot be removed.
- `spec/io/IO_CORE.md`: the D121 authority-transfer rule drops the detached-job
  category; adapter lifecycle rules are unchanged.
- `spec/runtime/ABSTRACT_RUNTIME.md` (informative): the Task model drops the
  `detached` participation flag and remaining detachment cross-references are
  removed.

## [0.1.431] - 2026-09-20

### D177 — Non-local return target reachability across a Task/Future boundary
- Generalizes `spec/semantics/CALLABLES.md` §14 ("Return from Escaped
  Closures"): a captured return-home activation is reachable only from the
  Task that owns it. A `^` whose captured return home belongs to a different
  Task than the one currently executing the `^` is unreachable exactly as an
  already-completed home is, regardless of whether the owning Task itself has
  completed, and signals `InvalidReturn`.
- Does not reopen the already-ratified D162 fixed fatal-failure authority
  (non-root fatal failure terminates that Actor incarnation; RootActor fatal
  failure terminates the Process); this revision only settles what an
  escaping `^` itself signals before that already-fixed consequence applies.
- Normative owner changed by this revision: `spec/semantics/CALLABLES.md`.

## [0.1.430] - 2026-09-19

### D156 / I052 — Remove Core fixed-width integer families
- Removes `UInt8`, `Int8`, `UInt16`, `Int16`, `UInt32`, `Int32`, `UInt64`, and
  `Int64` as Core numeric semantic families, prototype objects, and prelude
  bindings.
- Defines the Core numeric hierarchy as exactly the common `Number` prototype,
  the single unbounded exact mathematical `Integer` family, and the IEEE
  binary64 `Float` family.
- Removes the former fixed-width conversion factories, checked same-family
  arithmetic/division/quotient/remainder rules, cross-family identity examples,
  and width/signedness participation in standard numeric equality, ordering, and
  hashing.
- Keeps width/range-specific host or binary representations outside Core
  semantics. Implementations may preserve such machinery internally or at an
  interop/FFI boundary, but this revision selects no guest-visible fixed-width
  value model or public FFI surface.
- Requires standard Integer-domain APIs, indexed access, Bytes, Map hashes, and
  IP-address/endpoint validation to consume the single semantic `Integer`
  family rather than a wider family set.
- Normative owners changed by this revision:
  `spec/semantics/VALUES_AND_COLLECTIONS.md`, `spec/io/BYTE_IO.md`, and
  `spec/io/NETWORK.md`.
- Informative/runtime alignment is reconciled in
  `spec/runtime/ABSTRACT_RUNTIME.md`.

### Compatibility and implementation state
- Programs that reference the removed fixed-width Core names now encounter the
  ordinary missing-binding behavior unless a program or library defines those
  names itself.
- The former Test Tool `fixed-integer` expectation kind is retired because its
  value family no longer exists in Core.
- Executable implementation is delivered by I052 in the same repository change.
- Reusable exact host-width projection remains internal implementation machinery
  and does not constitute a public FFI contract.
- Maven implementation version becomes `0.3.56-SNAPSHOT`.

## [0.1.429] - 2026-09-19

### D157 / I053 — Scalar Core String indexing with preserved grapheme capability
- Defines every semantic `String` value as an exact ordered sequence of Unicode
  scalar values.
- Defines standard `String.size` as the exact semantic Integer count of Unicode
  scalars and standard `String.at(index)` / bracket read as returning the
  one-scalar String at the zero-based semantic-Integer index.
- Supplementary-plane scalars therefore occupy one Core String position even
  when represented by a UTF-16 surrogate pair; negative and out-of-range indexes
  continue to signal `Error`.
- Removes Unicode extended-grapheme segmentation from Core `String.size` /
  `String.at` semantics while preserving exact scalar identity, immutability,
  no implicit normalization, strict String-family receiver rules, `String +`,
  `String.concat`, explicit Encoding boundaries, and `String != Bytes`.
- Preserves the existing Unicode-17 default extended-grapheme segmentation
  capability for separate Standard Library use; the final public grapheme
  module/prototype/view topology and selector spelling remain deferred.
- Normative owners changed by this revision:
  `spec/semantics/VALUES_AND_COLLECTIONS.md` and `spec/PROTOS_GRAMMAR.md`.
- Informative/public alignment documents reconciled by the implementation:
  `spec/runtime/ABSTRACT_RUNTIME.md` and `README.md`.

### Compatibility and implementation state
- This is an observable Core String indexing/counting semantic change for
  multi-scalar grapheme clusters; ASCII behavior and single-scalar supplementary
  characters retain their prior visible results.
- Executable implementation is delivered by I053 in the same repository change.
- `ProtosStringValue` enforces the semantic Unicode-scalar-sequence invariant by
  rejecting host strings with unpaired UTF-16 surrogates.
- Maven implementation version becomes `0.3.54-SNAPSHOT`.

## [0.1.428] - 2026-09-19

### D154 / I051 — Remove public semantic identity-hash selector
- Removes the standard ordinary `Object.identityHash()` selector under ratified
  D154 Candidate A.
- Keeps primitive `identityHashOf(value)` as the non-overridable semantic
  identity-hash authority used by identity-sensitive Core/runtime machinery.
- Preserves primitive `===` / `!==`, `IdentityMap` identity lookup semantics,
  default `Object.hash()` behavior, execution-scoped identity-hash stability and
  collision rules, and ActorRef/GroupRef semantic identity.
- A program-defined ordinary slot named `identityHash` remains ordinary message
  behavior only and has no semantic identity-hash authority.
- `IdentityMap` continues to use primitive `identityHashOf(key)` together with
  primitive `===` and does not dispatch a user-defined `identityHash` message.
- Core v0.1 exposes no replacement guest-visible numeric identity-hash accessor.
- Normative owner changed by this revision:
  `spec/semantics/VALUES_AND_COLLECTIONS.md`.
- Informative/runtime-alignment documents reconciled by the implementation:
  `spec/runtime/ABSTRACT_RUNTIME.md`,
  `spec/concurrency/DISTRIBUTED_RUNTIME.md`, and `README.md`.

### Compatibility and implementation state
- This revision removes one previously standard guest-visible Object selector.
- Executable implementation is delivered by I051 in the same repository change.
- Maven implementation version becomes `0.3.52-SNAPSHOT`.

## [0.1.427] - 2026-09-18

### D144 / I048-A — Null-aware standard Object control
- Adds ordinary standard `Object.ifNull(block)` and
  `Object.ifNotNull(block)` behavior under ratified D144 Candidate B-prime.
- Branch selection tests only exact canonical `null` identity of the original
  receiver; there is no truthiness, and delegation to canonical `null` does not
  confer null identity.
- `null.ifNull(block)` invokes the reached callback exactly once with zero
  supplied arguments and returns its exact normal result; non-null receivers do
  not callability-validate or invoke the callback and return the exact receiver.
- `null.ifNotNull(block)` does not callability-validate or invoke the callback
  and returns canonical `null`; non-null receivers invoke the reached callback
  exactly once with the exact receiver as its sole supplied argument and return
  its exact normal result.
- Preserves ordinary eager receiver/argument evaluation, path-sensitive
  callability validation, polymorphic callback invocation, Error and non-local
  control propagation, and unchanged Future results with no implicit await,
  adoption, wrapping, conversion, or hidden suspension.
- Preserves ordinary failed-lookup errors and D142 Map
  absent-versus-present-with-null semantics.
- Adds no optional-navigation or null-coalescing syntax, `Optional`/`Maybe`,
  `undefined`, truthiness, failed-lookup-to-null conversion, or combined
  null-aware operation.
- Normative owner changed by this revision:
  `spec/semantics/VALUES_AND_COLLECTIONS.md`.

### Compatibility and implementation state
- This is a specification-only publication of already-ratified D144 semantics.
- Executable implementation remains I048 follow-up work and must occur only
  after this revision is published to `main`.
- The Maven implementation version is unchanged by this revision.


## [0.1.426] - 2026-09-18

### D147 / I045-A — Exact standard-owner `recognizes(value)` predicates
- Adds the ordinary selector `recognizes(value)` as standard local behavior on
  exactly the canonical standard owners `String`, `Integer`, `Float`, and
  `Array`.
- `String.recognizes` recognizes exactly semantic String values.
- `Integer.recognizes` recognizes exactly ordinary unbounded Integer values;
  all fixed-width Integer-family values are rejected and internal
  SmallInteger/BigInteger representation is irrelevant.
- `Float.recognizes` recognizes exactly semantic Float values and performs no
  numeric promotion or conversion.
- `Array.recognizes` recognizes ownership of standard Array indexed state;
  immediate delegation-parent identity is irrelevant, descendant-factory
  Arrays remain recognized, and ordinary delegation to `Array` is insufficient.
- Each recognizer requires its exact canonical standard owner as receiver,
  accepts exactly one arbitrary candidate value, returns canonical `true` or
  `false`, and treats candidate mismatch as `false`.
- Recognition performs no candidate message lookup/dispatch, `parent()` lookup,
  equality/hash dispatch, callback, conversion, coercion, or delegation-based
  membership inference.
- Standard owner objects themselves are not thereby members of the domains they
  recognize; in particular `String.recognizes(String)`,
  `Integer.recognizes(Integer)`, `Float.recognizes(Float)`, and
  `Array.recognizes(Array)` are all `false`.
- Adds no recognizer to `Number`, fixed-width Integer owners, `Map`,
  `IdentityMap`, `Bytes`, or any other owner by symmetry, and adds no generic
  family/type descriptor or user-extensible recognition mechanism.
- Normative owners changed by this revision:
  `spec/semantics/OBJECT_MODEL.md` and
  `spec/semantics/VALUES_AND_COLLECTIONS.md`.
- Informative runtime pseudocode is reconciled in
  `spec/runtime/ABSTRACT_RUNTIME.md`.

### Compatibility and implementation state
- This revision exposes already-approved exact Core semantic/state domains
  without changing ordinary protocol-oriented polymorphism or making delegation
  equivalent to semantic-family membership.
- This is a specification-only publication. Executable implementation and
  production probe migration remain I045-B follow-up work and must occur only
  after this revision is published to `main`.
- The Maven implementation version is unchanged by this revision.


## [0.1.425] - 2026-09-18

### D149 / I044 — Remove custom symbolic binary operators
- Removes arbitrary custom symbolic binary operators from valid Protos source.
- Removes the custom symbolic token/category, custom-binary grammar alternative,
  custom precedence domain, and custom/standard mixing rule while preserving the
  fixed standard symbolic surface and its existing precedence/associativity.
- Defines unsupported maximal symbolic spellings as lexical errors. Former
  custom spellings such as `@`, `|>`, `!!`, `^^`, `--`, `-!`, and `!-` are not
  split into shorter standard tokens; `!!x`, `^^x`, `--x`, `-!x`, and `!-x`
  therefore do not become stacked-prefix syntax.
- Preserves D148-derived `!=`, ordinary named messages/calls including
  one-argument sends, and `Object.alias`.
- Introduces no direct symbolic declaration syntax, named-infix mechanism,
  fixity/precedence declarations, precedence groups, or spare custom-operator
  reservation.
- Normative/public owners reconciled by this revision:
  `spec/PROTOS_GRAMMAR.md`, `spec/PROTOS_LANGUAGE_SPEC.md`,
  `spec/semantics/CALLABLES.md`, `spec/runtime/ABSTRACT_RUNTIME.md`, and
  `README.md`.

### Compatibility and implementation state
- Source that relied on arbitrary custom symbolic binary operators is no longer
  valid and must use ordinary named messages/calls or another independently
  specified current language construct.
- The fixed standard operator surface, standard precedence ladder, and D148
  inequality semantics are unchanged.
- Executable implementation is aligned in I044. Maven implementation version is
  `0.3.42-SNAPSHOT`.


## [0.1.424] - 2026-09-18

### D141 — Strict variadic standard String `concat`
- Adds ordinary standard receiver-oriented `String.concat` for zero or more
  already-evaluated positional String values, including values contributed by
  ordinary call spread. The original receiver and every supplied value must be
  semantic Strings; delegation alone does not confer eligibility.
- Preserves composition/conversion separation: `concat` performs no implicit
  textual conversion, normalization, encoding/decoding, locale operation,
  equality/hash dispatch, callback, mutation, or hidden suspension.
- Defines the successful result as the exact receiver Unicode-scalar sequence
  followed by every supplied String scalar sequence in positional order. The
  zero-argument form therefore preserves the receiver's String semantic value.
- Preserves ordinary call evaluation: receiver/target plus all explicit
  arguments/spreads are evaluated under the existing left-to-right call rules
  before standard `concat` validates the resulting supplied vector.
- Keeps standard binary String `+` unchanged. `concat` is not repeated `+`
  dispatch; the two selectors remain independent ordinary behaviors even though
  `a.concat(b)` and standard `a + b` produce the same String semantic value for
  valid standard String inputs.
- Leaves physical construction strategy implementation-private and adds no
  interpolation, syntactic sugar, implicit conversion protocol, collection join,
  public StringBuilder, stream, rope, or iodata-like public abstraction.
- Normative owner changed by this revision:
  `spec/semantics/VALUES_AND_COLLECTIONS.md`.
- Informative runtime pseudocode updated in:
  `spec/runtime/ABSTRACT_RUNTIME.md`.

### Compatibility and implementation state
- Existing String literals, strict binary `+`, identity/equality/hash/indexing,
  encoding semantics, and all source grammar remain unchanged.
- This is a specification-only publication. Executable implementation of the new
  `concat` selector is separate follow-up work; the Maven implementation version
  is unchanged.
- D141 intentionally defers syntactic sugar and textual conversion to separate
  later decisions.

## [0.1.423] - 2026-09-18

### D146 / I046-A — Standard prelude `fail()` semantics
- Adds `fail` as one ordinary zero-argument standard-prelude callable.
- Each reached valid `fail()` invocation creates one fresh generic standard
  Error occurrence whose immediate parent is the canonical standard `Error`
  prototype, then signals that exact object through the existing non-resumable
  Error semantics.
- The callable keeps canonical Error provenance under caller-local `Error`
  shadowing and after extraction/storage; separate reached invocations preserve
  distinct observable identity.
- Supplied arguments follow the ordinary standard-callable arity-failure path
  without first executing the generic failure occurrence.
- Adds no syntax, keyword, reserved identifier, intrinsic, payload/cause/rethrow
  shorthand, Error-taxonomy category, assertion meaning, or guest-visible
  stack/source payload.
- Preserves exact `Error.signal()` / `error.signal()` receiver identity,
  handler matching, unwind/`ensure`, Future failure identity, Actor/P boundary
  rules, and the existing shallow non-resumable Error model.
- Normative owners changed by this revision:
  `spec/semantics/ERRORS.md`, `spec/semantics/MODULES.md`, and
  `spec/PROTOS_GRAMMAR.md`.

### Compatibility and implementation state
- A previously absent bare `fail` lookup is now specified to resolve to the
  standard prelude callable when no nearer lexical binding shadows it.
- Existing explicit local `fail` bindings remain valid and shadow the standard
  prelude binding normally.
- This is a specification-only publication. Executable implementation and any
  bounded equivalent-source migration remain I046-B follow-up work.
- The Maven implementation version is unchanged by this revision.

## [0.1.422] - 2026-09-18

### D143 — Fixed-prefix standard-Array multiple slot creation
- Adds the dedicated slot-creation target `(a, b): source` for two or more bare
  identifiers while keeping `(a, b)` invalid as a general expression, tuple,
  comma-expression, or reusable pattern.
- Evaluates the right-hand side exactly once, requires receiver-owned standard
  Array indexed state and `length >= targetCount`, then shallowly observes the
  first `targetCount` element references in ascending index order before any
  target slot is created. Extra elements are ignored and no remainder is built.
- Creates target slots left-to-right with the existing ordinary bare `:` rules in
  the current slot-creation context. There is no duplicate-name preflight,
  transaction, or rollback; later creation failure leaves earlier successful
  creations in place.
- A successful expression returns the exact original right-hand-side object.
- Adds no multiple assignment, rest/nested/Map/object destructuring, wildcard,
  matcher-binding surface, or arbitrary-object positional-deconstruction
  protocol. D080-A and D131-C remain preserved.
- Normative owners changed by this revision:
  `spec/PROTOS_GRAMMAR.md` and
  `spec/semantics/EXECUTION_AND_CONTROL.md`.

### Compatibility and implementation state
- A previously invalid source form, `(a, b): source`, becomes valid with the
  exact D143 semantics above.
- Parser/runtime implementation is intentionally unchanged by this
  specification-only publication and remains follow-up work.
- Implementation version is unchanged.

## [0.1.421] - 2026-09-18

### D142 — Standard Map expected-absence fallback
- Adds the standard non-mutating `Map.atIfAbsent(key, fallback)` and
  `IdentityMap.atIfAbsent(key, fallback)` protocols selected by ratified D142
  Candidate C.
- Requires exactly one logical key search under the receiver's existing key law:
  normal `Map` retains deterministic query `hash` plus directed
  `queryKey == storedKey`, while `IdentityMap` retains semantic identity-hash
  plus `===` matching.
- Preserves `ABSENT != PRESENT_WITH_NULL`: a present association returns its
  exact stored value, including `null` or `false`, without validating or invoking
  the fallback.
- On absence, requires path-sensitive fallback callability and exactly one
  ordinary zero-argument invocation whose exact normal result is returned.
  Search/fallback effects, suspension, errors, and non-local control transfer
  retain their existing semantics with no rollback.
- Defines `atIfAbsent` itself as non-mutating. Fallback execution begins only
  after the missing-key search completes, may perform ordinary mutations when
  existing rules permit them, and never causes the outer operation to re-search
  or reinterpret the established miss.
- Adds no eager `atOr`, mutating `atIfAbsentPut`, implicit key/receiver callback
  arguments, ambient Map default state, presence-result carrier, Optional/Maybe,
  hidden sentinel, truthiness, syntax, transaction, or atomic compute/install
  guarantee.

### Compatibility and implementation state
- Existing `at(key)`, indexed access, `containsKey`, `atPut`, `remove`, Map
  insertion order, normal Map hashing/equality, IdentityMap identity matching,
  receiver-domain, reentrancy, and open/closed/frozen semantics remain unchanged.
- Normative changes are confined to `semantics/VALUES_AND_COLLECTIONS.md` plus
  this global specification changelog entry.
- This is a specification-only revision. Executable implementation of the new
  selectors remains separate work, and the Maven implementation version is
  unchanged by this revision.


## [0.1.420] - 2026-09-18

### D145 / I042 — Remove Closure `args` intrinsic
- Removes the ambient Closure-invocation `args` intrinsic selected by D145
  Candidate A. The spelling `args` is now an ordinary identifier throughout
  Core source syntax and ordinary unqualified lookup.
- Removes `args` from the reserved-word set and from `intrinsic-reference`.
  `args` may therefore be used as a parameter name, rest-parameter name, local
  slot name, captured binding, ordinary lookup name, and member name without a
  contextual exception.
- Preserves the caller-supplied positional vector as implementation-internal
  activation/binding state where required for arity, required/default/rest
  parameter binding, spread composition, dispatch, debugging, or equivalent
  runtime machinery. That internal vector is no longer a guest-visible
  semantic object merely by virtue of invocation.
- Preserves required parameters, default parameters, rest parameters, call
  spread, receiver binding, `this`, `context`, `super`, return homes, and
  ordinary invocation semantics. A rest parameter continues to receive a
  fresh frozen standard Array containing exactly the unconsumed caller-supplied
  suffix.
- Preserves `process.args()` unchanged. Process bootstrap argument snapshots are
  a separate capability API and are not the removed Closure intrinsic.
- Adds no replacement call-metadata API, parameter-suppliedness API, full-vector
  capture marker, or reserved future meaning for the old spelling.

### Compatibility and implementation state
- Bare `args` no longer exposes the complete caller-supplied argument vector.
  Existing source that needs forwarding must bind an ordinary parameter/rest
  name explicitly, for example `(...args) => target(...args)`.
- The parser/runtime removal and conformance reconciliation are owned by I042.
- Implementation version becomes `0.3.37-SNAPSHOT`.

## [0.1.419] - 2026-09-18

### I043 — D148 derived inequality semantics
- Reconciles the normative specification with ratified D148 Candidate A-prime:
  source `a != b` remains available but is the mandatory logical complement of
  the receiver's validated ordinary `a == b` behavior.
- Makes `==` the sole ordinary customization point for Core semantic equality.
  Source `!=` no longer dispatches an ordinary selector named `!=`, and the
  standard `Object` protocol no longer includes `Object.!=`.
- Requires left-to-right exactly-once operand evaluation, exactly one ordinary
  `==` invocation, the existing strict canonical-Boolean-or-Error equality
  result boundary, and canonical Boolean inversion.
- Preserves `===` / `!==`, Map/hash equality contracts, and the absence of
  truthiness.

### Compatibility and implementation state
- Ordinary source using `a != b` retains its spelling and normal not-equal
  meaning when it did not depend on an independently customized `!=` selector.
- A structural slot named `!=` is no longer part of the Core equality protocol
  and is not selected by source `a != b`.
- Implementation is owned by I043 under ratified D148 A-prime.

## [0.1.418] - 2026-09-18

### I041-E — D131 final grammar/documentation consistency correction
- Removes one stale pre-D131 sentence in `PROTOS_GRAMMAR.md` that still referred
  to a separate pattern-position Map grammar after revision `0.1.417` had
  already removed that grammar.
- Clarifies that `%{...}` is Map construction only and that structural Map
  matching is ordinary `matcherMap.match(subject)` behavior owned by
  `semantics/MATCHING.md`.
- This is a consistency correction to the already-ratified D131 model; it
  introduces no new matching syntax, semantics, matcher capability, or
  implementation behavior.
- Programmer-facing chapter 12 is reconciled from the superseded I038 dedicated
  matching language to the protocol-first D131 surface.

### Compatibility and implementation state
- The dedicated pre-D131 matching syntax remains removed, not deprecated or
  dormant.
- Implementation behavior and implementation version are unchanged by this
  specification/documentation-only revision.

## [0.1.417] - 2026-09-17

### D131 / I041-A2 — Remove dedicated matching grammar
- Removes the dedicated postfix `match` expression, `case` arms, `when` guards,
  binder/wildcard, Array/Map pattern, remainder, exact-Map, OR, alias and
  `captures(...)` productions from the normative Core v0.1 grammar.
- Restores the ordinary expression root to `slot-creation | assignment |
  non-local-return | binary-expression`; matching no longer owns a separate
  expression category.
- `pattern.match(subject)` and `value.caseOf(cases)` are ordinary member/call
  syntax rather than keywords or structural grammar forms.
- `match`, `case`, `when`, `exact`, and `captures` no longer carry
  matching-specific contextual structural meaning and remain ordinary
  identifier/member-name spellings where otherwise admitted.
- `_`, `@`, `|`, and `...` receive no matching-specific grammar under D131.
- Ordinary Array construction under D130 and Map construction under D136 remain
  the source forms used to build structural matcher values.

### Compatibility and implementation state
- `PROTOS_GRAMMAR.md` is normatively reconciled to the D131 protocol-first model.
- Parser/AST/lowering/runtime behavior still accepts/implements the old matching
  language until later I041 executable-removal slices; that temporary mismatch
  is owned by I041 and is not a compatibility promise.
- `semantics/MATCHING.md` remains the primary normative owner of matching
  behavior from specification revision `0.1.416`.
- Implementation behavior and implementation version are unchanged by I041-A2.

## [0.1.416] - 2026-09-17

### D131 / I041-A1 — Protocol-first matching semantic authority cutover
- Replaces the dedicated Core v0.1 matching-language semantic model with the
  ratified D131 Candidate C protocol-first foundation.
- Retains the single public matcher authority `pattern.match(subject)`, inherited
  `Object.match(subject) -> this == subject`, and the exact
  `false | true | non-empty Array` matcher-result carrier.
- Defines direct fixed exact-length structural recognition on standard
  `Array.match` and open/subset structural recognition on normal standard
  `Map.match`, including receiver eligibility, shallow attempt observation,
  deterministic child order, normal Map key-search semantics, and positional
  capture composition.
- Defines the initial standard helper matchers `Any` and `Capture`.
- Defines ordinary multi-way selection through `value.caseOf(cases)`, where
  `cases` is a normal insertion-ordered Map of matcher keys to callable values;
  normal Map hash/equality/unique-key semantics are deliberately part of that
  representation.
- Removes the dedicated match/case/when language, pattern-owned binding syntax,
  OR/remainder/exact/alias/captures institutions, and matching-specific static
  coverage framework from the normative Core v0.1 model.
- Removed capabilities are not dormant compatibility features; future recovery
  requires a new explicit decision.

### Compatibility and implementation state
- `semantics/MATCHING.md` is cut over normatively to D131 in I041-A1.
- Grammar, parser/AST/lowering/runtime, tests/conformance, and programmer
  documentation remain to be reconciled by later I041 slices; this temporary
  normative/executable mismatch is owned by I041 and is not a compatibility
  promise.
- Implementation behavior and implementation version are unchanged by I041-A1.

## [0.1.415] - 2026-09-17

### D136 — F′ Map initial-association construction correction
- Supersedes the `0.1.414` E′ interpretation of `%{...}` while preserving the
  already-ratified source syntax and layout.
- `%{...}` defines construction-time initial Map associations; it is not
  repeated post-construction `atPut` dispatch.
- Ordinary `Map` and `call` lookup remain observable, but construction accepts
  only the canonical standard Map factory behavior selected directly or by
  inheritance/delegation. A nearer arbitrary or copied `call` behavior signals
  `Error` before that behavior or any entry executes.
- The eligible factory is invoked once with zero arguments and produces a fresh
  standard normal Map whose parent is the actual factory receiver.
- Each entry evaluates key then value exactly once before initial association
  definition. The query key's hash is then computed exactly once and prior
  equal-hash initial associations are tested in insertion order using directed
  `queryKey == storedRepresentativeKey`.
- A duplicate/equal initial key signals `Error`; it does not replace the first
  representative or value.
- Initial association definition does not dispatch `atPut`. Later
  `map[key] = value` remains ordinary `atPut` mutation and is unchanged.
- No Association value, generic keyed-literal protocol, `IdentityMap` syntax,
  or matching semantic change is introduced.

### Compatibility and implementation state
- `PROTOS_GRAMMAR.md` and `semantics/VALUES_AND_COLLECTIONS.md` are reconciled
  to the owner-approved F′ D136 record.
- I040-C cuts both direct AST and C-prime Map-construction execution over to the
  same F′ semantics and replaces the stale E′ conformance expectations.
- Implementation version becomes `0.3.8-SNAPSHOT`.

## [0.1.414] - 2026-09-16

### D136 — Map populated construction syntax
- Adds expression-position `%{...}` Map construction as a primary expression.
- Construction resolves ordinary shadow-sensitive `Map`, invokes it exactly once
  with zero arguments before entry evaluation, then processes entries strictly
  left-to-right as key evaluation, value evaluation, and one fresh ordinary
  `atPut` dispatch before the next entry.
- Entry key/value evaluation remains in the enclosing activation; construction
  creates no guest-visible temporary binding, Closure, lexical scope,
  construction activation, receiver change, or non-local-return home.
- Empty `%{}` still performs ordinary `Map()` lookup/invocation. Custom or
  shadowed `Map` may return any object supporting the reached `atPut` dispatches;
  syntax itself does not guarantee standard-Map freshness.
- Construction is fail-fast and non-transactional. Earlier effects/insertions
  are not rolled back when a later key, value, hash/equality callback,
  insertion, Error, cancellation, suspension, or control transfer interrupts
  construction.
- Entry layout follows ordinary sequential-body style: newline between logical
  lines and `;` on one line. Comma is not an entry separator; leading/trailing/
  consecutive `;` are invalid. Bare key expressions are evaluated rather than
  converted to String names.
- Adds no Association value, spread/merge/comprehension form, generic keyed
  literal protocol, implicit key conversion, `IdentityMap` sugar, or matching
  semantic change.

### Compatibility and implementation state
- Normative changes are confined to `PROTOS_GRAMMAR.md` and
  `semantics/VALUES_AND_COLLECTIONS.md`.
- Existing standard Map factory, `atPut`, equality/hash, insertion-order,
  mutation, comparison-scope and matching contracts remain authoritative.
- I040/#543 implements the syntax with dedicated surface/canonical construction
  forms and equivalent direct-AST/C-prime execution, plus parser/tooling
  regressions and Protos/Test-Tool conformance.
- Implementation version becomes `0.3.3-SNAPSHOT`.

## [0.1.413] - 2026-09-16

### D130 — Array construction syntax as ordinary-call sugar
- Adds primary-expression construction forms `[]`, `[a]`, `[a, b]`, and spread
  forms such as `[a, ...items, b]`.
- Requires exact lowering to the corresponding ordinary `Array(...)` call.
  `Array` remains an ordinary identifier lookup, so local shadowing and ordinary
  non-invokable failure are preserved rather than bypassed by a privileged Core
  constructor.
- Reuses the existing call argument grammar and semantics, including comma/layout
  rules, no trailing comma, spread, left-to-right and exact-once evaluation,
  Error propagation, and control flow.
- Construction remains a primary expression, so ordinary postfix indexing and
  nesting compose mechanically: `[a][0]` lowers to `Array(a)[0]`, and
  `[[a], b]` lowers to `Array(Array(a), b)`.
- Adds no holes, comprehensions, repetition/fill form, generic collection-literal
  protocol, expected-type conversion, implicit iterable expansion, reserved
  word, or second runtime construction category.
- Existing Array match-pattern brackets remain a separate matching grammar and
  D130 changes no matching semantics.

### Compatibility and implementation state
- Normative changes are confined to `PROTOS_GRAMMAR.md` and
  `semantics/VALUES_AND_COLLECTIONS.md`.
- Standard Array factory, indexing, identity, mutability, and delegation
  semantics are unchanged; D130 only adds source syntax whose behavior is the
  existing ordinary-call path.
- I039/#539 implements the syntax with a surface-only AST form that canonicalizes
  to ordinary lookup/call semantics, with parser/tooling regressions and TOOL002
  conformance for construction, spread/order, postfix use, nesting, and
  shadow-sensitive `Array` lookup.
- Implementation version becomes `0.3.2-SNAPSHOT`.

## [0.1.412] - 2026-09-13

### D121 — Resource close initiated during Actor termination cleanup
- Ratifies D121 Candidate A′: a first lifecycle `close()` after Actor termination
  cutover is admissible only when invoked from the exact dynamic execution extent
  already authorized as termination cleanup.
- The authority follows ordinary nested calls and suspension/resumption of that
  same cleanup continuation. `TERMINATING` state alone is insufficient, and the
  authority is not inherited merely by creating a new Task, Future producer,
  mailbox turn, detached job or unrelated callback.
- A close admitted this way commits as one ordinary lifecycle close. If its
  release requires Actor-local guest execution, PLAT030 release-owned C′ runs in
  the same Actor domain and that release becomes another termination-cleanup
  obligation that delays `TERMINATED`.
- D112 remains authoritative for close commitments that predate termination
  cutover. D121 governs only first-close commitment after cutover from an
  already-authorized cleanup flow.
- Actor termination remains non-implicit with respect to resource close. D121
  adds no hidden Task, system Actor, domain migration, post-`TERMINATED` guest
  lane, timeout or hard-kill semantics.

### Compatibility and implementation state
- Existing `ensure` cancellation shielding and async-cleanup semantics remain
  authoritative; D121 specifies their lifecycle-close consequence rather than
  introducing a second cleanup mechanism.
- Existing close commitment/follower/Error rules, D112 and PLAT030 ownership
  remain intact.
- D121 releases the remaining semantic gate for PERF006-B PLAT030
  lifecycle-release C′ implementation.
- This ratification changes specification/governance only. It changes no Java
  implementation, Maven implementation version, native boundary or
  standard-library source and executes no Protos tests.

## [0.1.411] - 2026-09-12

### D112 — Actor termination versus committed I/O lifecycle release requiring guest re-entry
- Ratifies D112 Candidate A′: a lifecycle `close()` committed before Actor
  termination cutover becomes an Actor termination-cleanup obligation only while
  its remaining release requires ordinary guest execution.
- Required guest release remains in the same Actor execution domain under
  PLAT030 lifecycle-release-owned C′. It is not a Task, not a new ordinary Actor
  turn and does not reopen admission after the termination cutover.
- The Actor remains `TERMINATING` while required pre-cutover committed guest
  release suspends/resumes and reaches `TERMINATED` only after those obligations
  and the already-required Task cancellation/unwind cleanup have settled.
- Backend-only residual work that needs no guest execution does not by itself
  keep the Actor alive and still may not re-enter guest code after `TERMINATED`.
- Actor termination remains non-implicit with respect to `close`, `flush`,
  `sync` and shutdown; D112 applies only to a lifecycle already committed before
  the termination cutover.
- D112 adds no system-Actor migration, post-`TERMINATED` guest lane, hidden Task,
  timeout or force-kill semantics. A future hard-stop policy requires a separate
  normative decision.

### Compatibility and implementation state
- Existing Future states, I/O commitment rules, close follower semantics,
  Actor isolation, Error identity/precedence, PLAT029 operation-owned C′,
  PLAT030 lifecycle-release ownership, PLAT031 buffered operation identity and
  D117 delegated-effect arbitration remain intact.
- D112 releases the previously blocked `TextWriter.close` and equivalent
  buffered lifecycle-release C′ implementation work under PERF006-B.
- This ratification slice changes specification/governance only. It changes no
  Java implementation, Maven implementation version, native boundary or
  standard-library source and executes no Protos tests.

## [0.1.410] - 2026-09-12

### D117 — Buffered wrapper delegated-effect commitment across cancellation and close cutover
- Ratifies D117 Candidate C-prime: standard output wrappers arbitrate a delegated first irreversible effect using three semantic evidence classes — proven zero effect, known irreversible effect, and failure with unknown effect.
- A pending lower Future does not itself expose producer commitment. While first-effect aftermath is unknown, cancellation, Actor termination and close cutover may be recorded but cannot publish an outer terminal outcome that promises zero irreversible effect.
- A standard lower cancelled outcome can provide zero-effect evidence because cancellation is terminal only when that lower operation's cancellation contract permits the corresponding zero-effect result.
- A failed ordinary `ByteWritable.write` may have contributed hidden prefix `k` with `0 <= k <= N`; because `k` is not exposed, failure proves neither `k == 0` nor `k > 0`. The wrapper therefore fails with unknown-effect aftermath, does not let a competing zero-effect cancellation/closure overwrite that failure, and must not replay uncertain output.
- Once the outer wrapper operation is committed, cancellation of a later required delegated operation is committed failure aftermath rather than outer cancellation.
- Stronger lower contracts may provide stronger zero-effect evidence, but ordinary Future failure does not imply failure atomicity.

### Compatibility and implementation state
- `Future` retains exactly `pending`, `resolved`, `failed`, and `cancelled`; D117 adds no public progress/commitment state, partial-write result, byte-count exposure, Error category, hidden Task, scheduler or shadow operation.
- Existing `IO_CORE.md` commitment/cancellation/close rules and `BYTE_IO.md` hidden-prefix, flush-frontier and no-replay rules remain authoritative; D117 supplies the missing wrapper-composition rule.
- PLAT009 remains the internal first-effect-gate precedent; PLAT029/PLAT031 remain authoritative for operation-owned C-prime and one `ProtosIoOperation`/one lifecycle buffered ownership.
- D117 releases the PLAT031 buffered-operation/lifecycle convergence portion of PERF006-B; D112/PLAT030 lifecycle-release execution remains independently unresolved.
- No executable implementation, Maven implementation-version, native boundary or standard-library source change is included in this ratification slice.

## [0.1.409] - 2026-09-12

### D103 — Dynamic capture rest composition and arm-binding interface
- Ratifies D103-A: a variable-arity segment introduced by `captures(...rest)` is valid in a composed pattern only when that segment is terminal in the final ordered arm-binding interface.
- Terminality is evaluated over the whole logical binding order, not merely within the local `captures(...)` spelling.
- A standard structural remainder binding such as Array `...@middle` contributes one ordinary capture whose value is the D084 fresh frozen remainder Array; it is not a D103 variable-arity segment. The analogous D086 Map remainder remains one Map capture.
- Non-terminal dynamic segments are static source/interface errors. Core does not repartition captures from both ends, silently aggregate the segment, pad/drop captures, or introduce a richer binding carrier.
- D072 remains the outcome carrier, D083 remains the shallow ordered composition rule, and D088 remains the ordinary positional selected-arm Closure/callable ABI.

### Compatibility and implementation state
- Existing top-level/terminal `captures(required..., ...rest)` remains valid.
- Existing fixed structural binder, alias, Array/Map remainder and fixed-capture interfaces retain their ratified meaning.
- No matcher-result carrier, Pattern hierarchy, CaptureFrame/Signature, named-argument mechanism, runtime implementation or Maven implementation-version change is introduced by this ratification slice.

## [0.1.408] - 2026-09-12

### D100 — Match-arm guard / Closure-arrow disambiguation
- Ratifies D100-A-prime: within a D093 `when` guard, the first `=>` at the guard's own structural nesting level is the match-arm delimiter.
- An ordinary Closure expression is not admitted as the ungrouped root guard expression. Grouped and nested Closures remain ordinary and legal.
- `case p when x => y => body` has one Core v0.1 parse: guard `x`; arm body `y => body`.
- The rule is structural rather than newline/layout based; no rightmost-arrow search, root-Closure greediness, parser backtracking, type-directed parsing or runtime-value disambiguation is permitted.
- D092 runtime guard semantics are unchanged and no new token, reserved word, runtime Guard/Match/Case object or alternate guard path is introduced.

### Compatibility and implementation state
- D093's postfix surface and `=>` arm boundary are preserved.
- D095 pattern syntax and D096 coverage semantics are unchanged.
- No parser/runtime implementation or Maven implementation-version change is included.

## [0.1.407] - 2026-09-11

### D096 — Match exhaustiveness, redundancy and static no-match analysis
- Ratifies D096-C-prime: static matching coverage is a sound tri-state proof layer with `PROVEN_EXHAUSTIVE`, `PROVEN_NON_EXHAUSTIVE`, and `UNKNOWN`.
- Core v0.1 does not require every matching expression to be statically exhaustive. `UNKNOWN` and proven partiality remain valid source; D092 terminal fresh ordinary `Error` remains the runtime no-selection semantics.
- Arbitrary matcher/value patterns, D081 ordinary equality, D095 `captures(...)`, D092 guards, Map query-key evaluation, `hash` and ordinary `==` provide no implicit static coverage contract.
- `_`, `@name`, and their parenthesized/irrefutable aliases provide syntax-stable irrefutability. An unguarded universal arm proves totality from that point.
- Arms after an unguarded syntactically universal-irrefutable arm, and OR alternatives after an already reachable syntactically universal-irrefutable alternative, are fixed structural source errors.
- Richer soundly proven redundancy/subsumption and proven non-exhaustiveness are warning/lint territory rather than Core validity errors; `UNKNOWN` must never be mislabeled non-exhaustive.
- Standard Array/Map patterns may contribute only conservative D084/D086 language-owned shape/domain facts; key/equality/effect-sensitive reasoning remains opaque.
- Delegation/prototype state is never inferred to be a closed universe. Future sealed/enum facilities may add separately ratified static proof facts.
- Analysis is resource-bounded and degrades to `UNKNOWN` on complexity-budget exhaustion rather than rejecting valid source.
- Coverage proof never changes D071-D095 observable matcher/guard ordering or effects; only a stable exhaustive proof may remove the unreachable final D092 no-selection branch.

### Compatibility and implementation state
- D071-D095 runtime matching semantics are preserved and not reopened.
- No new matching syntax, Pattern hierarchy, CoverageSignature, CaptureSignature, matcher registry, reflection protocol, closed delegation taxonomy, MatchFailure subtype or runtime metadata is introduced.
- No parser/runtime implementation, Java implementation, Maven implementation version, native boundary, standard-library source or license change is included in this ratification slice.

## [0.1.406] - 2026-09-11

### D095 — Match-pattern source grammar and explicit binding surface
- Ratifies D095-A-prime and completes D093's pattern grammar parameter.
- Bare literal/reference/call/member forms remain ordinary matcher/value patterns; bare identifiers are never implicit binders.
- Adds contextual `@name`, `_`, `@name: pattern`, standard Array `[ ... ]`, standard Map `%{ ... }`, `exact %{ ... }`, D090 `|`, contextual remainder `...`, and consumer-side `captures(...)`.
- Array syntax maps only to D084; Map syntax maps only to D086.
- Map query keys evaluate once left-to-right after the D086 snapshot and before child matching; all keys resolve before mapped-value children run.
- Fixed binders are unique/linear; fixed OR branches expose the same ordered logical binding interface.
- `captures(...)` adds no matcher-side names/arity metadata and does not change D072/D088.
- Activates the normative D093 matching-expression grammar in `PROTOS_GRAMMAR.md`.

### Compatibility and implementation state
- D071-D093 semantics are preserved.
- `exact` and `captures` remain contextual, `_` remains an ordinary identifier outside pattern position, and `@`/`|` remain ordinary symbolic-operator characters outside pattern position.
- No Pattern runtime class, registry, CaptureSignature, BindingMap, generic object inspection, or generic deconstruction protocol is introduced.
- Parser/runtime implementation remains separate; no Java implementation or implementation-version change is included.

## [0.1.405] - 2026-09-11

### D093 — Postfix match-expression surface and lowering envelope
- Ratifies the outer matching surface as a low-precedence postfix expression envelope: `subjectExpression match { case PATTERN [when guard] => closureBody ... }`.
- `match`, `case`, and `when` remain lexical identifier spellings and are structural only in their exact matching positions; the global reserved-word set is unchanged and ordinary `.match(...)` / source-defined `match` names remain valid.
- The subject is one complete `binary-expression`; the matching result is expression-valued, and source may parenthesize the complete match to use its result as a later operand/receiver.
- Every matching envelope has at least one explicit `case` arm. Arms follow the existing newline/semicolon source-line convention; `=>` reuses the existing Closure-body boundary. D093 adds no privileged `default`/`else` arm and no fallthrough form.
- `when` is the source attachment point for exactly D092's strict-Boolean post-pattern guard semantics.
- The surface lowers only onto D071-D092: subject exactly once, ordered D073 matcher attempts, D072/D083 outcomes, D088 bindings/selected Closure invocation, D090 first-success OR commitment, D092 guard continuation and terminal fresh ordinary Error.
- No runtime `Match`, `Case`, arm descriptor, registry, binding carrier or second matcher authority is introduced.
- Concrete internal pattern grammar remains deferred. `PROTOS_GRAMMAR.md` records the D093 outer envelope with an explicit `MATCH-PATTERN` grammar parameter; this revision does not yet activate the envelope in the executable `expression` production or authorize parser/runtime implementation.

### Compatibility and implementation state
- Normative owners changed: `PROTOS_GRAMMAR.md` for the outer surface envelope and `semantics/MATCHING.md` for its semantic lowering; adds durable non-normative D093 decision record under `docs/project/decisions/language/`.
- D051 ordinary-call compatibility and D073 ordinary `pattern.match(subject)` remain intact because contextual `match` does not reinterpret `match(subject) { ... }` or reserve the identifier globally.
- Existing D071-D092 matching semantics remain authoritative and are not reopened.
- Array/Map/binder/alias/OR/irrefutable pattern source spelling, exhaustivity/redundancy, optional/repetition/search/backtracking patterns, a dedicated no-match Error subtype and implementation remain unresolved.
- No parser/runtime production implementation, Maven implementation version, native boundary, license term, or standard-library source changes in this publication slice.
- D091 is unrelated Test Tool work and is excluded; later matching checkpoints are excluded.

## [0.1.404] - 2026-09-11

### D092 — Guard evaluation, arm continuation and terminal no-selection
- Ratifies guards as ordinary post-pattern Protos evaluation: a guard runs only after complete D072-valid pattern success, with that arm's D088 logical bindings available.
- Guard results are strict canonical Booleans: `true` selects the arm; `false` rejects only that arm and continues with the next arm. Every other normal guard result signals ordinary `Error`; there is no truthiness or implicit Future adoption/await.
- Guard `false` never reopens an already-successful D090 alternative composite. Error, non-local control, cancellation and explicit suspension propagate normally and never become arm rejection.
- Matching/guard effects are not transactional or rolled back. Rejected-arm bindings remain arm-local while ordinary reachable state mutations remain visible to later arms.
- A selected arm body executes only after pattern success plus guard acceptance; D088 remains the capture-to-callable binding authority and the selected body's normal result becomes the matching operation's normal result.
- If no arm is selected, the matching operation signals one fresh ordinary `Error` under the existing standard failure-occurrence rules. D092 adds no `MatchFailure` prototype, `null` fallback, implicit default, or mandatory general exhaustivity institution.
- Any future catch-all/default surface must be semantically an ordinary irrefutable arm rather than a privileged fallback path.

### Compatibility and implementation state
- Normative owner changed: `semantics/MATCHING.md`; adds durable non-normative D092 decision record under `docs/project/decisions/language/`.
- Existing D071-D090 matcher, capture, collection-pattern, binding and ordered-alternative semantics remain authoritative; D092 refines guarded-arm selection without reopening D090 alternatives or replacing D088.
- Concrete matching/guard/default grammar, exhaustivity/redundancy analysis, whole-subject aliases, optional/repetition/search/backtracking patterns, pattern reflection and a dedicated no-match Error subtype remain unresolved.
- No parser, grammar, production implementation, Maven implementation version, native boundary, license term, or standard-library source changes in this publication slice.
- D091 is unrelated concurrent Test Tool work and is excluded; later matching-design checkpoints are excluded.

## [0.1.403] - 2026-09-11

### D090 — Ordered OR / alternative-pattern semantics
- Ratifies standard alternatives as deterministic ordered choice: alternatives are attempted in semantic order through ordinary `alternative.match(subject)`, and only canonical `false` advances to the next alternative.
- The first D072-valid success commits immediately. Canonical `true` succeeds with zero captures; a non-empty capture Array succeeds with its capture interface; invalid normal results signal ordinary `Error`.
- Error, non-local control, cancellation and explicit suspension propagate from the currently attempted matcher and never trigger another alternative. Alternative execution is neither speculative nor parallel.
- Effects from an alternative that later mismatches are not rolled back; a later alternative observes ordinary program state after those effects.
- Later outer failure does not reopen an already-successful alternative composite. In particular, a future guard failure cannot resume D090 at a later branch; guard semantics otherwise remain deferred.
- D072/D083 remain the sole standard capture representation. D090 adds no branch tag, named matcher carrier, `CaptureFrame`, `CaptureSignature`, binding Map, rollback log, mutable sink or second matcher authority.
- D088 remains authoritative for bindings: alternatives feeding one fixed arm must be projectable to the same ordered logical arm-binding ABI, while arbitrary matchers retain dynamic capture arity and require no static capture-name/signature metadata.
- Nested standard alternatives preserve the same ordered leaf-attempt sequence regardless of grouping; alternative choice is not commutative. Optimizations may flatten or specialize only when all ordered matcher/effect/control/capture behavior is observationally unchanged.

### Compatibility and implementation state
- Normative owner changed: `semantics/MATCHING.md`; adds durable non-normative D090 decision record under `docs/project/decisions/language/`.
- Existing D072/D073/D083/D084/D086/D088 matching, capture and callable semantics remain authoritative and are not reopened.
- Concrete alternative/match/arm grammar, OR spelling, guard semantics beyond no-reopen, exhaustivity/redundancy analysis, repetition/optional/search/backtracking patterns, whole-subject alias and pattern reflection remain unresolved.
- No parser, grammar, production implementation, Maven implementation version, native boundary, license term, or standard-library source changes in this publication slice.
- D089 is unrelated concurrent tooling work and is excluded; later matching-design checkpoints are excluded.

## [0.1.402] - 2026-09-11

### D088 — Capture-to-arm binding ABI
- Ratifies D072/D083 positional captures as the sole standard runtime capture payload for source arm binding; no named matcher-result carrier, binding Map, `CaptureFrame`, `CaptureSignature`, or required capture-name metadata is introduced.
- After one candidate pattern succeeds completely and its D072 result is validated, the selected arm is invoked through ordinary Protos callable/Closure semantics: `true` supplies zero capture actuals and `[c1, ..., cn]` supplies `c1 ... cn` as ordinary positional actual arguments.
- Source-visible binder names belong to the source/arm binding interface and become ordinary selected-arm parameter slots; no source-visible arm binding exists during tentative/failing matching and no binding rollback mechanism is introduced.
- One D072 capture remains one arm argument. Captured Arrays, Maps and other aggregate values remain opaque ordinary values, preserving D083/D084/D086 capture boundaries.
- Fixed source binding interfaces are linear; duplicate binder names are invalid rather than hidden equality/conjunction or shadowing semantics.
- Arbitrary matchers may retain dynamic capture arity. Consumers intentionally accepting variable capture counts may use existing ordinary Closure rest-parameter semantics.
- Recognition success followed by selected-arm arity/binding incompatibility is ordinary callable invocation Error, not mismatch and not permission to try another arm.
- Future OR/alternative patterns exposed through one fixed arm interface must map every successful alternative onto the same ordered logical binding interface; OR recognition/backtracking itself remains unresolved.

### Compatibility and implementation state
- Normative owner changed: `semantics/MATCHING.md`; adds durable non-normative D088 decision record under `docs/project/decisions/language/`.
- Existing D072/D073/D083/D084/D086 matching and callable semantics remain unchanged.
- Named binding syntax, whole-subject alias, OR recognition mechanics, guards, exhaustivity, repetition/optional semantics and sequence-search/stream semantics remain unresolved.
- No parser, grammar, production implementation, Maven implementation version, native boundary, license term, or standard-library source changes in this publication slice.
- D087 is unrelated concurrent work and is excluded; D089 and later matching checkpoints are excluded.

## [0.1.401] - 2026-09-11

### D086 — Standard Map keyed-pattern semantics
- Ratifies standard keyed matching as pattern-owned semantics specialized to subjects owning normal standard `Map` keyed-entry state; `IdentityMap`, arbitrary `at`/`containsKey` objects, delegated/copied Map behavior, slots, and generic mapping duck types do not automatically participate.
- One stable shallow logical snapshot of current Map associations is established before query-key `hash` / `==` behavior or nested mapped-value child matching. Snapshot associations preserve representative key references, mapped value references, recorded hashes, and relative insertion order.
- Each keyed requirement uses an ordinary query key value and the existing normal-Map search relation: compute that query's current standard hash once, filter by recorded hash, inspect candidates in snapshot insertion order, and send `queryKey == storedRepresentativeKey`. Missing requested keys are mismatch, not `Map.at` Error.
- All keyed requirements resolve before mapped-value child matching. Repeated/equivalent requirements are allowed as repeated constraints and may select the same subject association; residue accounting counts a selected association once.
- Residue policy is explicit: `ignore` is open/subset default, `require-empty` provides exact keyed matching, and `match-remainder` supplies unmatched associations to a remainder child.
- A requested remainder is a fresh frozen normal standard `Map` preserving unmatched snapshot associations, recorded hashes and relative insertion order without ordinary user-visible `hash`, `==`, `atPut`, or iteration sends merely to rebuild the residue. Remainder allocation may be deferred or omitted when unobservable.
- D072/D073/D083 capture/failure/control semantics remain authoritative; a captured remainder Map is one ordinary captured value and is never flattened into entries.

### Compatibility and implementation state
- Normative owner changed: `semantics/MATCHING.md`; adds durable non-normative D086 decision record under `docs/project/decisions/language/`.
- Existing D071-D075, D078, D080, D081, D083 and D084 matching semantics remain unchanged.
- `IdentityMap` keyed patterns, arbitrary key-pattern entry search, optional/defaulted missing keys, generic keyed projection, repetition/quantification, concrete grammar, source binding spelling, guards and exhaustivity remain unresolved.
- No parser, grammar, production implementation, Maven implementation version, native boundary, license term, or standard-library source changes in this publication slice.
- D085 and D087 are unrelated concurrent work and are excluded; D088 and later matching-design checkpoints are excluded.

## [0.1.400] - 2026-09-11

### D084 — Standard Array sequence-pattern semantics
- Ratifies standard finite sequence matching as pattern-owned semantics specialized to subjects that own standard Array indexed state; arbitrary `at`/`size`/`each` behavior, iteration, delegation to Array, String, Bytes, Map, and other objects do not automatically confer sequence-pattern eligibility.
- Fixed sequence patterns are exact-length by default. A standard sequence pattern may contain at most one semantic remainder component, representing exactly the contiguous unmatched middle between fixed prefix/suffix children and allowing zero elements.
- Before any child matcher runs, the sequence matcher establishes a shallow logical observation of every Array element reference semantically needed by that attempt. It does not obtain standard Array structure by sending ordinary `size`, `at`, `each`, iterator, or deconstruction messages.
- A discard-only remainder need not traverse/materialize the middle. When the unmatched remainder must be supplied as one value, it is a fresh frozen standard Array containing the shallow unmatched references in order, including a fresh frozen empty Array for an empty remainder.
- Child execution and captures remain governed by D072/D073/D083. A captured remainder Array is one ordinary capture and is never recursively flattened.
- No generic `Sequence` family, registration mechanism, universal positional subject protocol, borrowed Slice/View type, iterator rollback rule, subsequence-search rule, or sequence-deconstruction protocol is introduced.

### Compatibility and implementation state
- Normative owner changed: `semantics/MATCHING.md`; adds durable non-normative D084 decision record under `docs/project/decisions/language/`.
- Existing D071-D075, D078, D080, D081 and D083 matching semantics remain unchanged.
- Map/keyed patterns and Map remainder capture, repetition/optional patterns, sequence find/subsequence search, iterator/generator/stream matching, String/Bytes patterns, generic sequence interoperability, concrete grammar, source binding spelling, guards and exhaustivity remain unresolved.
- No parser, grammar, production implementation, Maven implementation version, native boundary, license term, or standard-library source changes in this publication slice.
- D085 and later matching-design checkpoints are excluded.
