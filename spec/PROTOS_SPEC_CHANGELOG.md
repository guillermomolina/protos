# Protos Language Specification Changelog

All notable changes to the Protos language specification and the concurrency design ledger will be documented in this file.

Specification revision is global: the newest changelog entry `0.1.X` is the
current specification revision. Each revisioned normative document carries the
most recent global revision that changed that document; document revisions are
not synchronized, and an otherwise-unaffected document is not edited merely to
advance its revision.

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


## [0.1.299] - 2026-09-04

### Closed
- Closed `ActorRef routing implementation` as runtime machinery rather than a
  portable Core semantic facility.
- Allowed direct tables, local/Cluster directories, cached routes, distributed
  location metadata, routing services, transport endpoint metadata, or equivalent
  mechanisms to locate one concrete Actor incarnation.
- Required route updates/refreshes to preserve ActorRef identity, no-retargeting,
  same-sender FIFO, snapshot, acceptance, backpressure, cancellation, failure,
  reachability, and uncertainty semantics.
- Prohibited stale/missing routes from authorizing replacement retargeting,
  replay of accepted operations, duplication, or erasure of delivery uncertainty.
- Left route-cache, directory, replication, endpoint, refresh, forwarding, and
  lookup mechanisms as implementation concerns.
- Kept ActorRef persistence/serialization/capability encoding as separate open
  topics.
- Removed `ActorRef routing implementation` from Open Design Topics.

### Changed
- Updated `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized all five revisioned specification documents to document revision
  299.

## [0.1.298] - 2026-09-04

### Closed
- Closed `Service discovery implementation` as runtime/infrastructure machinery
  rather than portable Core semantics.
- Allowed discovery to use local registries, Cluster control state, external
  naming services, orchestrator APIs, replicated stores, static configuration,
  or other conforming mechanisms.
- Required implementation choice to preserve resolved ActorRef/GroupRef identity,
  discovery-name rebinding semantics, capability authority, lifetime, and
  durability boundaries.
- Preserved pay-as-you-grow: unused discovery must not require distributed
  runtime infrastructure.
- Left registry protocols, storage/replication, caching, watches/polling,
  backend selection, health probing, TTL bookkeeping, and retry/backoff as
  implementation concerns.
- Explicitly left public discovery API, namespace, consistency, TTL, watches,
  federation, persistence, security, and schema/versioning semantics outside
  this closure.
- Removed `Service discovery implementation` from Open Design Topics.

### Changed
- Updated `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized all five revisioned specification documents to document revision
  298.

## [0.1.297] - 2026-09-04

### Fixed
- Made P Error propagation explicitly use the ordinary P value-transfer boundary
  rather than an implicit privileged exception channel.
- Required transferable P Error graphs to become caller-domain transferred Error
  values, without preserving P-local identity for identity-bearing objects.
- Required an untransferable P Error graph to produce standard
  `NonParallelValue` under the existing P result-transfer failure rule.
- Prohibited leaking/sharing a live P-local Error object, implicit remote-error
  proxies, or hidden P execution-control state merely because the transferred
  value represents failure.
- Clarified that dynamic handlers, stacks, return homes, activations,
  continuations, and scheduler state never form part of P Error transfer.
- Connected the transferred caller-domain Error to the existing failed-Future
  rule: later `value()` observation performs a new consumer-side non-resumable
  signal.

### Changed
- Synchronized all revisioned specification documents to revision 297.
  `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_RUNTIME_SEMANTICS.md`, and
  `docs/design/CONCURRENCY_DESIGN.md` gain normative clarification in this revision.

## [0.1.296] - 2026-09-04

### Closed
- Closed `Physical-locality discovery` as runtime/administrative machinery rather
  than a portable Core semantic facility.
- Allowed runtimes to discover same-process, same-host, NUMA, rack, zone, region,
  network, or equivalent physical relationships using implementation-specific
  mechanisms.
- Prohibited discovered locality from changing Protos identity, transferability,
  mutable-state isolation, authority, ordering, acceptance, failure,
  cancellation, uncertainty, or transport requirements.
- Defined inability to discover locality as non-semantic; runtimes may fall back
  to any other conforming transport/placement mechanism.
- Kept physical-topology diagnostics outside portable Core.
- Preserved the broader logical-vs-physical topology relationship as a separate
  open topic.
- Removed `Physical-locality discovery` from Open Design Topics.

### Changed
- Updated `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized all five revisioned specification documents to document revision
  296.

## [0.1.295] - 2026-09-04

### Fixed
- Defined deterministic precedence between ordinary read cancellation and the
  `shutdownRead()` cutover for the same pending read.
- Required a cancellation that already won and committed `cancelled` before the
  shutdown cutover to retain that terminal outcome.
- Required the shutdown cutover to commit `null` when it reaches a read that is
  still uncommitted and not already terminally cancelled.
- Prohibited a later cancellation from rewriting shutdown-committed `null`, and
  prohibited shutdown from rewriting an already-terminal cancellation.
- Defined genuinely concurrent cancellation/shutdown races by whichever existing
  semantic commitment event commits first, independent of host callback timing.
- Preserved cancellation's zero-consumption guarantee and ReadShutdown's existing
  permission to discard uncommitted internal/native input.

### Changed
- Synchronized all revisioned specification documents to revision 295. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.294] - 2026-09-04

### Closed
- Closed `Transport selection and switching` as runtime policy already bounded by
  the transport-independent Actor communication semantics.
- Defined no portable Core API for choosing, pinning, querying, or constraining
  physical communication transport.
- Allowed runtimes to select and switch among in-process, shared-memory, IPC,
  network, or equivalent transports only when identity, ordering, snapshots,
  transferability, backpressure, acceptance, cancellation, failure, uncertainty,
  and authority remain unchanged.
- Prohibited transport switching from duplicating/replaying accepted operations
  or silently retrying due solely to a transport change.
- Kept administrative transport diagnostics outside portable Core semantics.
- Preserved message serialization format/versioning/schema evolution as separate
  open topics.
- Removed `Transport selection and switching` from Open Design Topics.

### Changed
- Updated `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized all five revisioned specification documents to document revision
  294.

## [0.1.293] - 2026-09-04

### Closed
- Closed `Cross-process same-host optimization` and `Shared-memory transport
  eligibility and lifecycle`.
- Defined same-host/shared-memory transport as an implementation optimization
  that must preserve ordinary Actor pass-by-value/snapshot semantics.
- Required identical transferability, aliasing/cycle, sender-ordering,
  backpressure, acceptance, cancellation, uncertainty, failure, and capability
  behavior regardless of physical transport.
- Prohibited shared-memory eligibility, pinning, backing-allocation identity,
  zero-copy state, or same-host placement from becoming portable Core
  observations.
- Defined shared-memory segment/mapping/reclamation/handle/page lifecycle as
  non-semantic implementation machinery.
- Permitted transport switching only when it preserves the same Actor-observable
  semantics without resetting ordering, duplicating accepted work, changing
  identity, or erasing uncertainty.
- Removed both corresponding items from Open Design Topics.

### Changed
- Updated `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized all five revisioned specification documents to document revision
  293.

## [0.1.292] - 2026-09-04

### Fixed
- Removed the residual vague architectural promise that Core should preserve the
  ability to add resumable conditions without redesigning the execution model.
- Reconciled Language and Runtime with the closed Core v0.1 non-resumable Error
  model: no resumable-condition continuation authority is retained by current
  signaling semantics.
- Preserved future extensibility only through an explicit recovery/restart
  facility with its own normative control-state contract.
- Required any such future facility not to reinterpret existing Core
  `Error.signal()` operations as resumable.

### Changed
- Synchronized all revisioned specification documents to revision 292.
  `PROTOS_LANGUAGE_SPEC.md` and `PROTOS_RUNTIME_SEMANTICS.md` gain normative
  clarification in this revision.

## [0.1.291] - 2026-09-04

### Closed
- Closed the optional administrative application/service identity topic for Core
  v0.1.
- Defined no intrinsic `ApplicationRoot`, application identity, service identity,
  deployment identity, or mandatory execution-hierarchy level above the
  already-defined runtime identities.
- Permitted deployment/orchestration/observability tooling to associate external
  application/service metadata with Protos entities only as non-semantic
  administrative metadata.
- Prohibited such metadata from changing sender identity, routing, placement,
  lifecycle, supervision, authority, continuity, identity comparison, or
  pay-as-you-grow behavior.
- Reaffirmed that distributed service continuity belongs to Group/discovery/
  Cluster-control/durable-state mechanisms rather than an administrative label.
- Left any future first-class application/service identity to earn and define its
  own semantic boundary explicitly.
- Removed the corresponding item from Open Design Topics.

### Changed
- Updated `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized all five revisioned specification documents to document revision
  291.

## [0.1.290] - 2026-09-04

### Fixed
- Defined `flush()` admission and ordering relative to `shutdownWrite()` when one
  logical output direction exposes both `Flushable` and `WriteShutdown`.
- Required a flush admitted before the write-shutdown cutover to remain a
  preceding accepted output operation that shutdown cannot overtake or
  retroactively move to the post-shutdown side.
- Required a flush ordered after the cutover to fail under the write-shut-down
  lifecycle without establishing a new propagation frontier or becoming an
  implementation-selected successful no-op.
- Defined genuinely concurrent cross-Actor flush/shutdown requests to receive one
  stable routing/admission order, matching the existing shared-flow ordering
  model.
- Preserved the ordinary success/failure/cancellation aftermath of a preceding
  admitted flush and deliberately left flush-failure recovery to the already
  existing Flushable/wrapper/clean-frontier semantics.
- Preserved the rule that `shutdownWrite()` does not imply one universal flush.

### Changed
- Synchronized all revisioned specification documents to revision 290. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.289] - 2026-09-04

### Fixed
- Fixed the observable ordering between handler selection and `ensure` cleanup
  during unwind.
- Defined handler selection to consume/deactivate the selected dynamic frame
  before cleanup runs on the path to that handler boundary.
- Required a cleanup Error raised during that unwind to search only still-active
  outer handlers and handlers explicitly installed by cleanup itself.
- Prohibited the already-selected handler from recursively catching a cleanup
  failure merely because the cleanup Error also matches its prototype.
- Aligned `transferToHandler` Runtime pseudocode with this ordering.
- Combined this rule with the existing cleanup-Error precedence: a cleanup Error
  supersedes the pending original transfer and prevents invocation of the
  consumed originally selected handler.

### Changed
- Synchronized all revisioned specification documents to revision 289.
  `PROTOS_LANGUAGE_SPEC.md` and `PROTOS_RUNTIME_SEMANTICS.md` gain normative
  clarification in this revision.

## [0.1.288] - 2026-09-04

### Closed
- Closed `NUMA-aware scheduling` as an implementation-only scheduler concern
  already constrained by the closed P scheduler-policy rules.
- Defined no portable Core meaning for NUMA-node identity, memory-node identity,
  CPU/package/socket/cache locality, or P-work affinity to physical topology.
- Allowed runtimes to use or ignore NUMA-aware scheduling, placement, migration,
  replication, pinning, and topology-sensitive cost models only when existing
  isolation, determinism, fairness, progress, and Actor-turn semantics remain
  unchanged.
- Prohibited portable Core code from requesting or observing NUMA placement or
  treating topology-sensitive optimization choices as semantic outcomes.
- Left any future explicit hardware-placement/performance-control facility to
  define its own contract.
- Removed `NUMA-aware scheduling` from Open Design Topics.

### Changed
- Updated `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized all five revisioned specification documents to document revision
  288.

## [0.1.287] - 2026-09-04

### Fixed
- Defined general `ensure` cleanup Error precedence rather than leaving
  competing unwind outcomes implementation-selected.
- Required normal cleanup completion to preserve the pending scope-exit transfer.
- Required an Error signaled by cleanup to supersede the pending transfer,
  including an earlier Error unwind, non-local return, normal scope exit, or
  cancellation unwind.
- Clarified that handling the cleanup Error later does not resurrect the
  superseded earlier transfer.
- Defined no automatic composite/suppressed-error object or language-visible
  causal link between the cleanup Error and the superseded Error.
- Generalized the already-existing cleanup-supersedes-cancellation rule rather
  than creating a separate cancellation-only exception.

### Changed
- Synchronized all revisioned specification documents to revision 287.
  `PROTOS_LANGUAGE_SPEC.md` and `PROTOS_RUNTIME_SEMANTICS.md` gain normative
  clarification in this revision.

## [0.1.286] - 2026-09-04

### Closed
- Closed `Behavior requirements before READY`.
- Defined that Core does not require a dispatchable current behavior throughout
  the whole `INITIALIZING` state because no external message is dispatched then.
- Required exactly one valid ordinary behavior object at the
  `INITIALIZING -> READY` cutover.
- Prohibited hidden/default/bootstrap sentinel behavior objects from becoming a
  portable semantic requirement.
- Defined normal completion of initialization without an installed behavior as
  initialization failure; the Actor never reaches `READY`.
- Preserved the existing queued/accepted-message behavior for initialization
  failure.
- Left the exact behavior installation/replacement API and syntax open.

### Changed
- Updated `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized all five revisioned specification documents to document revision
  286.

## [0.1.285] - 2026-09-04

### Fixed
- Reconciled output-wrapper close/finalization with the general deterministic
  `Closable` cutover semantics.
- Defined wrapper finalization/propagation to cover adapter state/output already
  semantically committed before the close cutover, plus the normal aftermath of
  preceding operations that had already crossed their own commitment boundary.
- Clarified that accepted-but-uncommitted adapter operations are closure-terminated
  at the cutover and do not become committed merely because close begins.
- Prohibited reversible validation, speculative encoding, staged bytes, reserved
  buffer space, or queued requests from leaking into wrapper finalization after
  their owning operation is closure-terminated.
- Required already-committed buffered/output state to remain subject to the
  wrapper's normal finalization/propagation obligations rather than being discarded
  by close.
- Applied the same distinction to explicit wrapper ownership close order and to
  `TextWriter.close()` encoder/final-byte finalization.
- Preserved the existing rule that close is not a universal drain of reversible
  pending work and did not change wrapper ownership of the underlying target.

### Changed
- Synchronized all revisioned specification documents to revision 285. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.284] - 2026-09-04

### Closed
- Closed `Module implementation sharing` as an implementation-only concern
  already constrained by Actor-local module-state semantics.
- Distinguished Actor-local module instances, contexts, caches, mutable slots,
  initialization state, and captures from physically shareable immutable
  implementation artifacts.
- Allowed parsed syntax, bytecode, machine code, immutable metadata, and similar
  artifacts to be shared or duplicated only when the choice is unobservable.
- Prohibited artifact sharing from creating shared mutable module state,
  execution contexts, initialization state, Futures/tasks, resources, or
  Actor-local authority.
- Left artifact cache placement, eviction, deduplication, compilation tiers, JIT
  specialization, and physical artifact identity as implementation details.
- Removed `Module implementation sharing` from Open Design Topics.

### Changed
- Updated `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized all five revisioned specification documents to document revision
  284.

## [0.1.283] - 2026-09-04

### Closed
- Closed `Non-transferable resource capabilities` and `Foreign-resource proxies`.
- Distinguished transfer of an existing value from provisioning a new
  proxy/routed capability.
- Required ordinary Actor transfer of a non-transferable live resource to fail
  with `NonTransferableValue`; no runtime may silently proxy, reopen, duplicate,
  broker, or substitute the resource to make transfer succeed.
- Defined proxy/capability provisioning as a separate semantic operation that
  must specify identity, authority, ordering, failure, cancellation,
  close/lifetime, and state-sharing behavior.
- Clarified that equivalent access to an external resource is not equivalent to
  transferring the original capability.
- Applied the same no-auto-proxy rule to P.
- Removed both corresponding items from Open Design Topics.

### Changed
- Updated `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md`.
- Synchronized all five revisioned specification documents to document revision
  283.

## [0.1.282] - 2026-09-04

### Fixed
- Defined failed-Future observation through `value()` as a new non-resumable
  Error signaling event in the consumer's then-current dynamic handler context.
- Prohibited Future failure records from preserving or restoring the producer's
  abandoned signaling continuation, activation frames, dynamic handlers, return
  homes, or other resumption authority.
- Clarified that a consumer handler result belongs only to the consumer-side
  handler boundary and cannot resume, retry, or inject a value into the failed
  producer computation.
- Defined repeated failed-Future observations as repeated consumer-side signals,
  never revival or re-entry of the failed producer.
- Applied the rule across same-Actor tasks, Actor boundaries, and P isolation so
  Future failure transport cannot become an implicit continuation-transfer
  mechanism.

### Changed
- Synchronized all revisioned specification documents to revision 282.
  `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_RUNTIME_SEMANTICS.md`, and
  `docs/design/CONCURRENCY_DESIGN.md` gain normative clarification in this revision.

## [0.1.281] - 2026-09-04

### Fixed
- Completed the `size()` / `at(index)` contract of the immutable sequence
  returned by `process.args()`.
- Defined `size()` as the exact non-negative Integer application-argument count
  over already-established bootstrap data, with no hidden suspension.
- Reused the standard `Array.at` zero-based index domain: exact semantic Integer
  only, `0 <= index < size()`, with no negative indexing, coercion, wrapping,
  truncation, saturation, or host-width interpretation.
- Defined valid `at(index)` to return the exact String at that snapshot position.
- Defined invalid type/negative/out-of-range indexing to use the same standard
  indexed-access failure semantics as `Array.at`, never `null` as an invalid-index
  sentinel.
- Kept Process-argument snapshots free to use non-Array representation/object
  identity and preserved their immutability.

### Changed
- Synchronized all revisioned specification documents to revision 281. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.280] - 2026-09-04

### Closed
- Closed `Java interoperability isolation`, `Java static mutable state`, and
  `Native global state`.
- Defined foreign mutable host/global state as unable to bypass Actor isolation
  merely because the storage lives outside the Protos heap.
- Prohibited ordinary cross-Actor wrappers from exposing unrestricted shared
  mutation of the same Java/native/global object.
- Distinguished host thread safety from Protos Actor isolation.
- Allowed safe interoperability through isolated copies/snapshots, semantically
  immutable sharing, explicit capability/service boundaries, or single-Actor
  ownership with ordinary Actor communication.
- Clarified that host reference identity does not become cross-Actor Protos
  identity and that observable copy-vs-share choices cannot be implementation
  selected.
- Applied the same rule to P: thread-safe/process-global does not imply
  P-transferable.
- Removed all three corresponding items from Open Design Topics.

### Changed
- Updated `PROTOS_RUNTIME_SEMANTICS.md` and `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized all five revisioned specification documents to document revision
  280.

## [0.1.279] - 2026-09-04

### Closed
- Closed `Blocking foreign calls` and `Blocking-operation offload`.
- Defined a synchronous foreign/host call as part of the current uninterrupted
  Protos execution segment even when its physical work is offloaded.
- Permitted runtimes to move blocking host work to helper threads/carriers or
  equivalent machinery only as an observationally invisible optimization.
- Prohibited physical offload from creating hidden Actor-local reentrancy or an
  implicit Protos suspension point.
- Required an explicit asynchronous/Future-returning extension contract when
  foreign work should release the Actor for other runnable Protos work.
- Clarified that a non-returning synchronous foreign call may stall its Actor
  indefinitely; Core adds no hidden timeout, cancellation, or preemption.
- Left worker-pool sizing, queueing, scheduling, and similar offload machinery as
  implementation details.
- Removed both corresponding items from Open Design Topics.

### Changed
- Updated `PROTOS_RUNTIME_SEMANTICS.md` and `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized all five revisioned specification documents to document revision
  279.

## [0.1.278] - 2026-09-04

### Fixed
- Completed the observable `each(block)` contract of the immutable sequence
  returned by `process.args()`.
- Reused the ordinary polymorphic callback-invocation domain of standard Core
  `each` operations rather than making Process-argument iteration Closure-only.
- Required callback callability validation before any argument callback executes.
- Defined exactly one callback per argument String in ascending logical index
  order, with no dependence on host-native argv layout or later host mutation.
- Defined successful iteration to ignore callback return values and return the
  Process-argument snapshot receiver itself, including for the empty snapshot.
- Defined callback error/non-local-control prefix behavior: completed lower-index
  callbacks are not rolled back and higher-index callbacks are not invoked.
- Kept the returned snapshot free to use a non-Array representation/object
  identity while preserving these sequence semantics.

### Changed
- Synchronized all revisioned specification documents to revision 278. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.277] - 2026-09-04

### Closed
- Closed dynamic error handlers across Actor boundaries as already determined by
  the existing task-local dynamic-handler and Actor value-transfer rules.
- Defined dynamic handler frames as execution-local control state that is never
  copied, inherited, serialized, proxied, forwarded, or remotely consulted
  across an Actor boundary.
- Clarified that sender handlers never become destination handlers, destination
  handlers never propagate back to the sender, and Actor bootstrap/replacement
  never inherits dynamic handler frames.
- Reaffirmed that a Future re-signals its stored Error only in the consumer's
  then-current dynamic handler context.
- Prohibited transport, placement, routing, same-host, or shared-memory
  optimizations from making handler propagation observable.
- Removed `Dynamic error handlers across Actor boundaries` from Open Design
  Topics.

### Changed
- Updated `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized all five revisioned specification documents to document revision
  277.

## [0.1.276] - 2026-09-04

### Closed
- Closed non-local return across Actor boundaries as impossible in Core v0.1.
- Defined Actor return homes as execution-domain-local control metadata that is
  never transferred, proxied, remapped, or remotely targeted.
- Clarified that destination Actor `^` may unwind only within destination-owned
  execution structure and can never return into sender/creator/bootstrap
  activations or another Actor's suspended continuation.
- Preserved ordinary Actor communication as value transfer rather than hidden
  continuation, reply, exception, or remote-stack control transfer.
- Clarified that Actor replacement cannot resurrect a return home from the dead
  incarnation.
- Mirrored the existing P principle that isolation boundaries do not preserve
  caller return-home authority.
- Removed `Non-local return across Actor boundaries` from Open Design Topics.

### Changed
- Updated `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_RUNTIME_SEMANTICS.md`, and
  `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized all five revisioned specification documents to document revision
  276.

## [0.1.275] - 2026-09-04

### Fixed
- Replaced implementation-selected `Environment.each(block)` enumeration order
  with one portable canonical order.
- Defined Environment entries to be enumerated by lexicographic comparison of
  their already-represented name Strings' Unicode scalar sequences.
- Defined first differing scalar by numeric scalar value and exact-prefix ties by
  shorter-name-first ordering.
- Kept the ordering local to Environment enumeration; no general String ordering
  operator is introduced.
- Prohibited Unicode normalization, locale collation, native case folding,
  host-environment ordering, hash-table layout, and materialization order from
  changing callback order.
- Defined callback error/non-local-control prefix behavior relative to that
  canonical order, while preserving the existing non-transactional callback
  semantics.

### Changed
- Synchronized all revisioned specification documents to revision 275. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.274] - 2026-09-04

### Fixed
- Removed the stale Grammar example
  `protectedClosure.handle(errorPrototype, handlerClosure)`, which contradicted
  the already-standardized Core handler API.
- Aligned `PROTOS_GRAMMAR.md` with Language and Runtime on the canonical ordinary
  message form `matchPrototype.handle(body, handler)`.
- Clarified that Grammar assigns no special handler semantics: receiver and
  arguments are parsed through ordinary message-send syntax, and dynamic handler
  behavior remains defined by Language/Runtime.
- Preserved the absence of dedicated `try`, `catch`, or `throw` syntax.

### Changed
- Synchronized all revisioned specification documents to revision 274.
  Only `PROTOS_GRAMMAR.md` gains normative clarification in this revision.

## [0.1.273] - 2026-09-04

### Closed
- Closed `Future ownership interaction with Actor lifecycle` as already fully
  determined by existing structured-concurrency and Actor-termination semantics.
- Added one normative ownership/lifecycle matrix covering Actor-local task-backed
  Futures, detached Actor-local tasks, Actor-originated non-task-backed Futures,
  Actor-originated P result Futures, and pure observation Futures.
- Clarified that detachment removes only activation ownership, never Actor-domain
  lifetime ownership.
- Clarified that surviving Future values do not keep terminated Actors alive or
  authorize continuation execution in their former mutable domains.
- Clarified that Actor replacement inherits no pending task/Future producer
  ownership from the terminated incarnation.
- Prohibited implementation-selected orphan-Future policies such as silent
  re-parenting, migration, abandonment, or continuation after Actor termination.
- Removed the corresponding item from Open Design Topics.

### Changed
- Updated `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized all five revisioned specification documents to document revision
  273.

## [0.1.272] - 2026-09-04

### Closed
- Closed Core v0.1 error signaling as strictly non-resumable: once
  `Error.signal()` begins, its signaling continuation is abandoned and a
  matching handler cannot return or inject a value back into that signal point.
- Defined one standard failure family rooted at `Error`; Core v0.1 introduces no
  separate standard `Exception` hierarchy or implicit continuable-exception
  category.
- Defined no privileged `resume`, `retry`, `restart`, `useValue`, or equivalent
  recovery operation in Core v0.1.
- Clarified that a handler's normal result is the result of the enclosing handler
  boundary, not a result returned to the abandoned signaling call.
- Reserved restart/recovery-style continuable handling for a future explicit
  semantic design without retroactively changing Core v0.1 `Error.signal()`.
- Prohibited host exception/continuation machinery from leaking implicit
  resumability into portable Protos semantics.

### Changed
- Synchronized all revisioned specification documents to revision 272.
  `PROTOS_LANGUAGE_SPEC.md` and `PROTOS_RUNTIME_SEMANTICS.md` gain normative
  clarification in this revision.

## [0.1.271] - 2026-09-04

### Closed
- Closed generic Future `race`/`select` out of Core v0.1.
- Rejected first-completion semantics based on implementation observation,
  callback, carrier, scheduler, polling, kernel, or I/O-backend timing.
- Declined to invent a global portable total order across independent Future
  terminal transitions solely to support a generic race primitive.
- Kept `Future.all(...)` as the deterministic standard multi-Future coordination
  operation.
- Required any future wait-any/select facility to define an independent semantic
  priority/event-ordering contract, including tie handling, loser lifecycle,
  cancellation, and resource-registration semantics.
- Removed `Select/race operations` from Open Design Topics.

### Changed
- Updated `PROTOS_LANGUAGE_SPEC.md` and `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized all five revisioned specification documents to document revision
  271.

## [0.1.270] - 2026-09-04

### Fixed
- Completed the standard `Environment.each(block)` callback contract.
- Reused the ordinary polymorphic callback-invocation domain of Core `each`
  operations rather than making Environment iteration Closure-only.
- Defined deterministic validation precedence: callback callability is validated
  first without invocation, complete Environment `(String, String)`
  representability second, and callbacks begin only after both succeed.
- Preserved the existing zero-callback guarantee when portable Environment
  representation validation fails.
- Defined successful `Environment.each(block)` to return the Environment receiver
  itself and to ignore callback return values for the operation result.
- Defined an empty valid Environment to invoke the callback zero times and still
  return its receiver.
- Removed implementation freedom to return `null`, the last callback result, or
  another implementation-selected value from successful Environment iteration.

### Changed
- Synchronized all revisioned specification documents to revision 270. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.269] - 2026-09-04

### Added / Closed
- Standardized `Future.all(futures...) -> Future` for deterministic waiting on
  multiple Futures.
- Defined `Future.all` as an ordinary message on the standard Future prototype,
  with no new syntax, Task kind, or wait-set object.
- Required synchronous left-to-right Future-argument validation.
- Defined the returned aggregate as a fresh non-task-backed observation Future
  that neither owns nor cancels its sources.
- Defined zero arguments to resolve immediately with a fresh empty standard
  Array and successful non-empty completion to preserve source argument order.
- Defined deterministic failure/cancellation selection by an ascending argument
  frontier rather than physical completion order.
- Defined aggregate cancellation as observation-only, with no upstream source
  cancellation.
- Defined repeated source-Future identity as valid and source registrations as
  removable/inert after aggregate terminalization to avoid unbounded retention.
- Kept first-completion `select`/`race` semantics as a separate open topic.
- Removed `Waiting on multiple Futures` from Open Design Topics.

### Changed
- Updated `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_RUNTIME_SEMANTICS.md`, and
  `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized all five revisioned specification documents to document revision
  269.

## [0.1.268] - 2026-09-04

### Fixed
- Reconciled the programmer-visible Language contract for the standard
  structural `Object` messages with the return values already fixed by Runtime.
- Defined successful `removeSlot(name)` to return the exact removed local-slot
  value, without copy, coercion, or delegated lookup.
- Defined successful structural `close()` and `freeze()` to return the original
  receiver, including their existing idempotent repeated-transition cases.
- Distinguished synchronous structural `Object.close()` from the I/O-domain
  `Closable.close()` operation, which follows its separate Future-returning
  resource-lifecycle contract when that behavior is selected by ordinary lookup.

### Changed
- Synchronized all revisioned specification documents to revision 268.
  Only `PROTOS_LANGUAGE_SPEC.md` gains normative clarification in this revision.

## [0.1.267] - 2026-09-04

### Fixed
- Removed the implementation-selected choice in `close()` between waiting for a
  previously accepted pending operation and forcing that operation to fail.
- Defined the close cutover in terms of the existing I/O commitment boundary:
  accepted-but-uncommitted operations fail with the closing-or-closed error,
  while already-committed operations retain their ordinary terminal aftermath.
- Required close to wait only for previously accepted operations whose semantic
  effects had already committed before the cutover.
- Kept closure-induced failure distinct from cancellation and preserved an
  independent cancellation outcome when cancellation wins before the cutover.
- Applied the same commitment-based classification to operations admitted before
  close through genuinely concurrent cross-Actor routing.
- Preserved rejection of operations ordered after the close cutover.
- Prevented backend/native scheduling policy from deciding whether reversible
  accepted work drains or is terminated by close.

### Changed
- Synchronized all revisioned specification documents to revision 267. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.266] - 2026-09-04

### Closed
- Closed Actor-local CPU-bound Future monopolization as an explicit consequence
  of cooperative C semantics.
- Defined ordinary Actor-local `closure.future()` execution segments as
  semantically non-preemptive with respect to other Protos work in the same
  Actor mutable domain.
- Defined no hidden suspension/preemption points at loop back-edges, calls,
  allocations, GC/JIT polls, timer ticks, host-thread quanta, or similar runtime
  machinery.
- Clarified that CPU-bound Actor-local work which never suspends may monopolize
  the Actor indefinitely and may delay queued tasks/messages and cancellation
  observation.
- Permitted physical carrier interruption/time slicing only when observationally
  equivalent to uninterrupted Protos-segment execution.
- Identified explicit `closure.parallel(...)` as the standard Core mechanism
  when CPU-bound work needs isolated parallel progress.
- Removed `Actor-local CPU-bound Future monopolization` from Open Design Topics.

### Changed
- Updated `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_RUNTIME_SEMANTICS.md`, and
  `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized all five revisioned specification documents to document revision
  266.

## [0.1.265] - 2026-09-04

### Fixed
- Reconciled the remaining generic `text + other` String example with the
  strict standard String concatenation rule by making the right operand
  explicitly another semantic String.
- Clarified that standard String `+` does not accept an arbitrary non-String
  object merely because an earlier illustrative variable name was generic.
- Removed the accidental implication that `StringBuilder` is a standardized
  Core v0.1 binding/prototype/protocol; efficient mutable text builders remain
  ordinary library abstractions unless explicitly standardized later.

### Changed
- Synchronized all revisioned specification documents to revision 265.
  Only `PROTOS_LANGUAGE_SPEC.md` gains normative clarification in this revision.

## [0.1.264] - 2026-09-04

### Closed
- Closed P scheduler policy, work stealing, and granularity as non-semantic Core
  implementation policy rather than an unresolved language/API surface.
- Defined no portable Core API or introspection for worker count, queue topology,
  work-stealing algorithm, victim selection, grain size, adaptive thresholds,
  inline/help-first/work-first policy, NUMA/locality preference, priority
  heuristics, carrier affinity, or scheduler cost model.
- Allowed runtimes to adapt those mechanisms dynamically to hardware/load while
  preserving all existing P snapshot, isolation, determinism, fairness,
  bounded-carrier nested-progress, cancellation, lifetime, locality, effect, and
  ByteRegion publication contracts.
- Clarified that scheduler-policy changes alone may not alter deterministic
  results/failures, create starvation, or introduce carrier-exhaustion deadlock.
- Clarified that Core promises neither equal CPU shares nor bounded latency,
  fixed parallelism, dedicated workers, work stealing, or a particular speedup.
- Kept implementation/admin scheduler diagnostics outside portable Core
  semantics.
- Removed the corresponding scheduling/work-stealing/granularity item from Open
  Design Topics.

### Changed
- Updated `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized all five revisioned specification documents to document revision
  264.

## [0.1.263] - 2026-09-04

### Fixed
- Defined standard `TextWriter.writeText("")` as an empty logical text payload
  with zero encoded-byte contribution and zero encoder-state transition.
- Prohibited ordinary empty text writes from acting as implicit encoder flush,
  finalization, reset, BOM emission, or stateful shift/reset control.
- Required host/native encoder calls whose empty-input behavior emits control
  bytes or resets state to be adapted so those effects do not leak through
  ordinary `writeText("")`.
- Kept empty text writes inside the normal TextWriter ordering/lifecycle domain:
  they cannot resolve successfully ahead of an earlier ordered operation that can
  still permanently fail the writer.
- Clarified that `writeLine("")` is not empty because its logical payload
  contains LF and is encoded normally.
- Kept encoder finalization attached to the explicit lifecycle operation that
  requires it, such as TextWriter close.

### Changed
- Synchronized all revisioned specification documents to revision 263. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.262] - 2026-09-04

### Added / Closed
- Standardized `Array.parallelSort(less, arguments...) -> Future` as the final
  Core v0.1 high-level parallel Array API in the §71 collection set.
- Defined a canonical stable logical merge-sort tree so physical algorithm,
  worker count, chunking, or work stealing cannot change observable ordering.
- Required comparator results to be exactly canonical `true`/`false`; other
  normal results fail with standard `InvalidComparatorResult`.
- Defined each merge decision by both `less(a,b,...)` and `less(b,a,...)`:
  one true selects that side, two false values are a stable tie, and two true
  values fail with standard `InvalidComparatorOrder`.
- Defined both comparator errors as delegating directly to `Error`.
- Defined deterministic failure precedence across recursive child sorts, merge
  positions, and forward/reverse comparator calls.
- Kept all comparator calls isolated P computations and all source/result values
  governed by ordinary P snapshot/transfer rules.
- Defined empty and singleton behavior without unnecessary comparator calls and
  prohibited partial sorted-Array publication on failure/cancellation.
- Removed parallel sort from §71 examples/Open Design Topics, closing the
  map/filter/search/reduce/sort/iteration Core collection-API pass.

### Changed
- Updated `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_RUNTIME_SEMANTICS.md`, and
  `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized all five revisioned specification documents to document revision
  262.

## [0.1.261] - 2026-09-04

### Closed
- Closed parallel iteration out of Core v0.1: no standard
  `Array.parallelEach(...)` operation is defined.
- Derived that boundary from the existing P effect model: isolated P work has no
  implicit Actor-local mutation, messaging, ambient I/O, runtime authority, or
  other external-effect channel merely because results are discarded.
- Kept `Array.parallelMap(...)` as the composable standard per-element parallel
  operation when callers need independent computation and may ignore its final
  result if appropriate.
- Prevented an `each`-shaped API from implicitly weakening P transfer/effect
  restrictions or legitimizing hidden shared/global side effects.
- Allowed implementations to eliminate unused result materialization only as an
  observationally invisible optimization.
- Removed parallel iteration from §71.6 examples and narrowed the remaining
  parallel collection API topic to parallel sort only.

### Changed
- Updated `PROTOS_LANGUAGE_SPEC.md` and `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized all five revisioned specification documents to document revision
  261.

## [0.1.260] - 2026-09-04

### Fixed
- Defined the standard String-family binary `+` behavior as exact String
  concatenation, making existing examples such as `("hel" + "lo") === "hello"`
  normative rather than illustrative-but-undefined.
- Required both operands to be semantic String values and prohibited implicit
  conversion of numbers, booleans, null, prototypes, or arbitrary objects.
- Defined the result as exact receiver scalar sequence followed by right-operand
  scalar sequence, with no Unicode normalization, locale processing, encoding,
  decoding, callback, hash/equality dispatch, or hidden suspension.
- Preserved String immutability, value identity, ordinary operator/message
  overriding, and the semantic-family receiver-domain rule.

### Changed
- Synchronized all revisioned specification documents to revision 260.
  `PROTOS_LANGUAGE_SPEC.md` and `PROTOS_RUNTIME_SEMANTICS.md` gain normative
  semantic content in this revision.

## [0.1.259] - 2026-09-04

### Fixed
- Defined a preceding `ByteWritable.write` whose cancellation succeeds as a
  clean zero-contribution outcome for `shutdownWrite()`.
- Required write shutdown to wait for that preceding write's terminal cancelled
  state without failing merely because cancellation occurred.
- Applied the same rule to a cross-Actor write that routing/admission placed
  before the shutdown cutover and that is then successfully cancelled.
- Preserved the distinction between cancellation and write failure: a failed
  preceding write still makes shutdown fail even when its hidden committed prefix
  happens to be zero.
- Clarified that a cancellation request that loses after write commitment does
  not erase that write; shutdown composes with the write's actual later
  success/failure aftermath.

### Changed
- Synchronized all revisioned specification documents to revision 259. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.258] - 2026-09-04

### Added / Closed
- Standardized `Array.parallelReduce(reducer, arguments...) -> Future`.
- Defined empty input as resolved `null`, singleton input as the isolated
  snapshot/transfer of its sole element without invoking the reducer, and
  non-empty multi-element input through isolated P reducer nodes.
- Defined one canonical adjacent-pair reduction tree with odd final values
  carried unchanged to the next round.
- Made the canonical tree independent of worker count, chunking, scheduling, and
  work stealing so non-associative reducers remain deterministic.
- Defined each reducer invocation as its own P isolation domain over left/right
  operands plus the reducer and explicit arguments.
- Defined logical round barriers: a later round exists only if all combine nodes
  in the preceding round succeed.
- Defined multiple failures within one round by the leftmost failing pair.
- Allowed physical pipelining/speculation only when it cannot alter canonical
  parenthesization, failure selection, publication, or other observable state.
- Required intermediate/final reducer values to cross according to ordinary P
  result/value rules and prohibited partial reduction publication.
- Narrowed the remaining parallel collection API topic to sort/iteration.

### Changed
- Updated `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_RUNTIME_SEMANTICS.md`, and
  `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized all five revisioned specification documents to document revision
  258.

## [0.1.257] - 2026-09-04

### Fixed
- Made ordinary `ByteReadable.read(maxBytes)` preserve logical byte/error order
  independently of implementation read-ahead depth.
- Required already-returnable bytes that logically precede a later EOF or I/O
  failure to be delivered before that terminal condition can become a read
  outcome.
- Kept successful read chunk boundaries implementation-selectable while requiring
  all bytes preceding a later error to remain ahead of that error across
  successive ordered reads.
- Defined a later read-ahead failure as preserved/deferred receiver error state,
  rather than allowing it to fail the current read and rebuffer earlier bytes.
- Required the deferred failure to be reported exactly once when it reaches the
  head of the logical input/error order, preserving the existing no-auto-replay
  rule after actual reporting.
- Prevented buffering, native batching, or prefetch from changing observable
  `bytes -> error` into `error -> bytes` solely because the implementation
  discovered the later error earlier internally.

### Changed
- Synchronized all revisioned specification documents to revision 257. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.256] - 2026-09-04

### Added / Closed
- Standardized `Array.parallelFindIndex(predicate, arguments...) -> Future`.
- Chose an Integer index or `null` result so absence remains unambiguous even
  when an Array element is itself `null`.
- Reused the existing parallelMap/parallelFilter per-index P input snapshot,
  isolation, polymorphic predicate-callability, and strict Boolean-result rules.
- Defined deterministic ascending-index search semantics independent of physical
  execution order: `false` continues, while `true` and failure are decisive.
- Required the first decisive logical index to determine the terminal outcome
  only after every lower index is known to be `false`.
- Defined lower-index failure to beat a higher-index match while failures after
  the first established match are irrelevant to the search result.
- Allowed higher-index work to be pruned/cancelled only after it cannot affect
  the specified terminal outcome.
- Defined empty input to resolve with `null` without crossing a P boundary.
- Narrowed the remaining parallel collection API topic to
  reduce/sort/iteration.

### Changed
- Updated `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_RUNTIME_SEMANTICS.md`, and
  `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized all five revisioned specification documents to document revision
  256.

## [0.1.255] - 2026-09-04

### Fixed
- Removed the accidental implication that `String.graphemes()` and
  `String.codePoints()` are standardized Core v0.1 protocols.
- Clarified that Core String's normative grapheme access remains the existing
  `size` / `at` contract rather than an additional collection/view API.
- Left libraries free to provide ordinary `graphemes` / `codePoints`
  conveniences and left any future lower-level text-view protocol to a later
  explicit standardization decision.
- Preserved Encoding-object one-shot conversion (`UTF8.encode(text)`) as the
  standardized text/byte boundary.

### Changed
- Synchronized all revisioned specification documents to revision 255.
  Only `PROTOS_LANGUAGE_SPEC.md` gains normative clarification in this revision.

## [0.1.254] - 2026-09-04

### Fixed
- Defined the native/bootstrap Environment represented by `process.environment()`
  as one stable Process-lifetime snapshot, not a live host re-query.
- Required repeated successful acquisitions to preserve the same native-name
  domain, entries, identity relationships, and standardized lookup/enumeration
  semantics while leaving returned-object identity non-normative.
- Made Environment acquisition validity a stable bootstrap outcome, including
  duplicate-equivalent native-name rejection.
- Prevented one acquisition from failing and a later one succeeding merely
  because the host environment mutated, enumeration order changed, or a
  different first/last-winner policy was used.
- Prevented later host/native environment mutation from changing an already
  established successful standardized Environment.
- Preserved lazy materialization and per-operation String validation when they
  remain observationally equivalent to one captured bootstrap snapshot.
- Kept future live/raw native-environment inspection outside the standardized
  Process Environment.

### Changed
- Synchronized all revisioned specification documents to revision 254. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.253] - 2026-09-04

### Added / Closed
- Standardized `Array.parallelFilter(predicate, arguments...) -> Future`.
- Reused the `parallelMap` per-index P-isolation model: one logical isolated
  predicate invocation per source index, with all non-empty child inputs
  validated/snapshotted before any child becomes eligible.
- Kept predicate callability polymorphic rather than Closure-only.
- Required predicate results to be exactly canonical `true` or `false`, matching
  Protos' absence of language-wide truthiness; other normal results fail the
  corresponding index with standard `InvalidPredicateResult`.
- Defined `InvalidPredicateResult` as delegating directly to `Error`.
- Preserved stable ascending source-index order in the fresh filtered result
  independently of physical execution/completion order.
- Required selected values to cross back under ordinary P result rules while
  allowing rejected values to avoid unnecessary result transfer.
- Defined deterministic multiple-failure selection by lowest failing source
  index and prohibited partial result publication on failure/cancellation.
- Left batching, fusion, chunking, SIMD, work stealing, worker count, and actual
  overlap as unobservable implementation choices.
- Narrowed the remaining parallel collection API topic to
  reduce/search/sort/iteration.

### Changed
- Updated `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_RUNTIME_SEMANTICS.md`, and
  `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized all five revisioned specification documents to document revision
  253.

## [0.1.252] - 2026-09-04

### Fixed
- Defined `process.args()` as one stable Process-bootstrap argument snapshot for
  the entire Protos Process lifetime.
- Required repeated successful calls to observe the same argument count, order,
  and String values while leaving returned-object physical identity non-normative.
- Made portable argument representability a stable bootstrap outcome: an invalid
  native argument set cannot fail one call and later succeed because host argv
  storage or conversion behavior changed.
- Prevented later host-native argv/process-title mutation from changing an
  already-established successful Protos argument snapshot.
- Allowed eager or lazy validation/materialization only when every call remains
  observationally equivalent to one bootstrap-time logical snapshot.
- Distinguished standardized application arguments from future live/best-effort
  host process-inspection facilities.

### Changed
- Synchronized all revisioned specification documents to revision 252. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.251] - 2026-09-04

### Fixed
- Repaired the incomplete rev247 encoding-dispatch cleanup: stale
  `text.encode(UTF8)` / `bytes.decode(UTF8)` examples remained in normative
  Language/Grammar text despite the changelog claiming they had been removed.
- Replaced every remaining stale concrete example with
  `UTF8.encode(text)` / `UTF8.decode(bytes)`.
- Added explicit normative clarification that the standard one-shot receiver is
  the `Encoding` object and Core v0.1 does not additionally require reciprocal
  String/Bytes convenience messages.
- Preserved ordinary syntax for user/library-defined messages with those names
  and preserved Encoding's existing availability boundary outside the mandatory
  Core prelude.

### Changed
- Synchronized all revisioned specification documents to revision 251.
  `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`, and `PROTOS_IO_MODEL.md` gain
  normative repair/clarification in this revision.

## [0.1.250] - 2026-09-04

### Fixed
- Reconciled §71.5 with §71.5A so generic writable Array/object partitioning is described as excluded from Core v0.1 rather than open.
- Reconciled §71.9 with §71.9A so remote execution of Core P is described as excluded rather than open or implementation-selectable.
- Reconciled §71.20 with §71.5A so byte-region closure no longer says generic writable Array/object partitioning remains open.
- Made no new concurrency design decision; this revision only removes stale
  open-state wording that contradicted already-closed §71.5A and §71.9A
  semantics.

### Changed
- Updated `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 250.

## [0.1.249] - 2026-09-04

### Fixed
- Closed the flush-frontier race for writes that are logically before a flush but
  have not yet committed output when the flush begins.
- Required successful flush to wait until every preceding-frontier write has a
  terminal semantic aftermath and to propagate exactly the contribution left by
  that outcome: complete sequence on success, permitted prefix on failure, or
  zero bytes on successful cancellation.
- Prohibited a flush from resolving successfully and then allowing an earlier
  frontier write to commit new output behind the completed frontier.
- Applied the same rule to writes admitted before a flush under cross-Actor
  routing/admission ordering.
- Kept pipelining and overlapping backend work implementation-defined while
  making terminal contribution knowledge, rather than native call timing, the
  semantic completion condition.
- Clarified that an earlier write failure does not mechanically force flush
  failure when the receiver remains usable and can still establish the complete
  propagation contract for the exact committed prefix.

### Changed
- Synchronized all revisioned specification documents to revision 249. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.248] - 2026-09-04

### Added / Closed
- Standardized `Array.parallelMap(worker, arguments...) -> Future` as the first
  Core high-level parallel collection operation.
- Kept callback eligibility polymorphic like `Array.each`: the worker need only
  be ordinarily invokable, not specifically a Closure, while all actually used
  worker/element/argument graphs must satisfy P transfer/projection rules.
- Defined one logical child P isolation domain per source index, with no shared
  mutable Protos identity between worker invocations.
- Required all non-empty child inputs to be validated/snapshotted before the
  successful call returns and before any child becomes eligible; invalid P input
  fails synchronously with `NonParallelValue`.
- Defined empty input to create no P work and require no P-transferability for
  otherwise-unused worker/extra arguments, while still validating ordinary
  worker callability.
- Preserved source-index order in the fresh result Array independently of
  physical worker execution/completion order.
- Defined deterministic multiple-failure selection by the lowest failing source
  index and prohibited scheduler timing from selecting the reported failure.
- Required cancellation/failure to publish no partial result Array.
- Left worker count, chunking, batching, fusion, SIMD, work stealing, and actual
  simultaneous execution as unobservable implementation choices.
- Narrowed the remaining collection-API open item to
  filter/reduce/search/sort/iteration.

### Changed
- Updated `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_RUNTIME_SEMANTICS.md`, and
  `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 248.

## [0.1.247] - 2026-09-04

### Fixed
- Reconciled Language/Grammar encoding examples with the normative I/O
  one-shot Encoding API.
- Made the Encoding object the single canonical standard dispatch receiver:
  `encoding.encode(text)` and `encoding.decode(bytes)`.
- Replaced misleading `text.encode(UTF8)` / `bytes.decode(UTF8)` examples with
  `UTF8.encode(text)` / `UTF8.decode(bytes)`.
- Explicitly declined to standardize reciprocal String/Bytes convenience
  messages in Core v0.1; libraries remain free to provide ordinary conveniences.
- Preserved ordinary message syntax and the existing rule that Encoding objects
  are outside the required Core prelude.

### Changed
- Synchronized all revisioned specification documents to revision 247.
  `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`, and `PROTOS_IO_MODEL.md` gain
  normative clarification in this revision.

## [0.1.246] - 2026-09-04

### Fixed
- Defined `ByteSeekable.position()` and `ByteSized.size()` as observation-only
  Future-returning I/O queries with explicit cancellation boundaries.
- Allowed cancellation to win until the query's result or failure outcome
  commits, since these queries have no irreversible Protos sequence effect.
- Defined successful cancellation to contribute no position/size/content state
  change and no additional observation/frontier in the sequence-state domain.
- Required residual backend/native query work that cannot be physically stopped
  to remain under implementation/runtime custody and to discard its eventual
  value/error rather than resurrecting a cancelled Future.
- Clarified that cancelled queries reach an ordinary terminal Future state, so
  later ordered operations compose with the actual aftermath of state-changing
  predecessors rather than an implementation-selected query residue.
- Prevented post-cancellation backend query completion from mutating sequence
  state or retroactively changing later Protos observations.

### Changed
- Synchronized all revisioned specification documents to revision 246. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.245] - 2026-09-04

### Closed
- Closed generic writable Array/object partitioning out of Core v0.1.
- Defined non-overlapping container indexes, slots, physical ranges, or storage
  regions as insufficient proof of disjoint mutable authority over reachable
  Protos object graphs.
- Kept `Bytes`/`ByteRegion` as the sole standardized Core writable-partition
  facility because its authority is explicitly bounded to byte-indexed state.
- Defined no Core `Array.parallelRange(...)`, generic object/graph partition
  capability, implementation-dependent alias-proof API, or borrow/ownership
  annotation system.
- Preserved ordinary parallel algorithms over Arrays/objects through P
  snapshot/value semantics, fresh-result construction, read-only inputs, and
  semantics-preserving internal optimizations.
- Required any future broader writable partition facility to provide a portable
  language/runtime proof of disjoint mutable authority rather than depending on
  implementation-selected heap/alias analysis.
- Removed the now-closed generic writable Array/object partitioning item from
  Open Design Topics.

### Changed
- Updated `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 245.

## [0.1.244] - 2026-09-04

### Closed
- Closed `Task` as non-observable Core v0.1 execution machinery rather than a
  second public concurrency identity.
- Kept `Future` as the public eventual-result/coordination abstraction regardless
  of whether its producer is task-backed, I/O-backed, communication-backed,
  continuation-backed, or isolated P work.
- Defined no Core `Task` prototype, constructor, current-task intrinsic, task
  identity, public parent/child task graph, scheduler handle, priority/affinity
  API, task enumeration, or Future-to-task conversion.
- Clarified that structured ownership, detachment, cancellation, waiting,
  failure propagation, and Actor/P lifetime semantics do not require exposing
  runtime task records.
- Required task/fiber/continuation splitting, fusion, inlining, migration, and
  carrier assignment to remain unobservable when Future/domain semantics are
  preserved.
- Removed the now-closed `Whether Task should become observable` item from Open
  Design Topics.

### Changed
- Updated `docs/design/CONCURRENCY_DESIGN.md` and, when its current stable semantic
  anchor is present, `PROTOS_RUNTIME_SEMANTICS.md`.
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 244.

## [0.1.243] - 2026-09-04

### Fixed
- Reconciled the normative Bytes model across Language, Runtime, and I/O:
  `Bytes()`, `each`, `add`, and `removeAt` are no longer required by I/O while
  semantically undefined elsewhere.
- Defined standardized `Bytes()` as a zero-argument factory for a fresh open
  empty Bytes value wherever that standardized factory is exposed, without
  making `Bytes` a mandatory Core-prelude binding.
- Kept `atPut` replacement-only while defining `add` and `removeAt` as the only
  standard Bytes operations in this set that change sequence length.
- Defined exact octet validation, `add` return value, `removeAt` shifting and
  removed-value result, and failure-before-mutation behavior.
- Defined `Bytes.each` through ordinary polymorphic invocation over one
  ascending-index snapshot of octet values.
- Defined open/closed/frozen consequences: closed Bytes allow existing-index
  replacement but reject resize; frozen Bytes reject all standard mutation.
- Required I/O's Bytes section to defer detailed sequence semantics to the
  Language/Runtime contracts rather than maintaining a competing partial model.

### Changed
- Synchronized all revisioned specification documents to revision 243.
  `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_RUNTIME_SEMANTICS.md`, and
  `PROTOS_IO_MODEL.md` gain normative semantic content in this revision.

## [0.1.242] - 2026-09-04

### Fixed
- Defined remote/end-of-stream EOF on a standard connected `Socket` input
  direction as permanent for that logical byte stream.
- Prohibited temporary-EOF treatment, implicit reconnect/replacement, or later
  backend input resurrection after a Socket read has committed remote EOF.
- Kept remote EOF directional: it does not invoke read shutdown, write shutdown,
  or whole-resource close and does not terminate otherwise-usable output.
- Distinguished remote EOF from local `shutdownRead()` while preserving their
  common later-read `null` behavior before whole-resource close.
- Preserved Closable precedence so reads issued after the whole-resource close
  cutover fail under the close lifecycle rather than being rewritten as EOF.
- Required buffered bytes preceding remote EOF to be delivered before the EOF
  result and allowed BufferedReader to memoize this source-guaranteed EOF.

### Changed
- Synchronized all revisioned specification documents to revision 242. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.241] - 2026-09-04

### Closed
- Closed Core isolated parallel execution as strictly Protos-Process-local.
- Defined `Closure.parallel(...)` not to imply remote placement, Node/Cluster
  routing, code shipment, network transport, remote failure detection, retry, or
  distributed result recovery.
- Clarified that P-transferability is not a general network-serialization
  contract and Closure projection is not a remote code-availability/versioning
  contract.
- Allowed arbitrary CPU/core/thread/NUMA/accelerator realization only while the
  work remains inside the same Protos Process semantic execution domain.
- Required any future remote-compute facility to be explicit and to define its
  own placement, code identity/availability, serialization, transport,
  authentication, cancellation, retry/uncertainty, failure, and lifecycle
  semantics rather than silently extending `Closure.parallel(...)`.
- Removed the now-closed remote isolated-parallel-execution item from Open Design
  Topics.

### Changed
- Updated `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 241.

## [0.1.240] - 2026-09-04

### Closed
- Closed the interaction between isolated parallel work and SIMD/vectorization
  without introducing a new public SIMD/vector value universe.
- Defined SIMD/vectorization as a permitted physical optimization only when it
  is observationally equivalent to the already-specified scalar/logical Protos
  execution.
- Required vectorized execution to preserve result values, identity/aliasing,
  evaluation and mutation order, dispatch/invocation behavior, failure
  precedence, explicit suspension/cancellation boundaries, P
  isolation/publication, and fairness.
- Prohibited SIMD-driven reassociation from changing the logical result of an
  observably non-associative reduction unless the invoked API explicitly defines
  different semantics.
- Left vector width, target instructions, masking, alignment strategy, cost
  model, and scalar fallback as implementation details.
- Removed the now-closed SIMD/vectorization interaction item from Open Design
  Topics.

### Changed
- Updated `docs/design/CONCURRENCY_DESIGN.md` and, where the current structure permits
  a stable anchor, `PROTOS_RUNTIME_SEMANTICS.md`.
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 240.

## [0.1.239] - 2026-09-04

### Fixed
- Defined semantic argument-validation failures of standardized Future-returning
  I/O operations to be reported through a failed returned Future after successful
  operation dispatch, rather than through an implementation-selected synchronous
  failure channel.
- Covered invalid read bounds, non-Bytes writes, seek/truncate numeric arguments,
  bounded line reads, and standard text-write payload validation through the same
  general rule.
- Required invalid requests to fail before operation-attributable I/O effects,
  position/content/lifecycle changes, frontiers, or external-authority exercise.
- Allowed already-failed Futures when validation depends only on already-evaluated
  Protos argument values.
- Kept ordinary lookup/receiver-domain/call-arity failures outside this rule when
  they prevent the standardized I/O operation from being dispatched at all.
- Kept explicitly synchronous APIs such as one-shot Encoding operations and
  Process bootstrap accessors under their existing non-Future contracts.

### Changed
- Synchronized all revisioned specification documents to revision 239. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.238] - 2026-09-04

### Fixed / Closed
- Restored monotonic source order for §71.13 through §71.20 without changing their already-closed semantics.
- Defined deterministic synchronous failure precedence for standard `parallelRange` validation.
- Closed weak fairness for runnable isolated P work so later submissions or scheduler policy cannot starve it indefinitely.
- Required nested-P descendant progress without needing an additional unused OS thread/carrier.

### Changed
- Updated normative P semantics from the repository state observed by this
  application attempt, without assuming a particular starting revision or SHA.
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 238.

## [0.1.237] - 2026-09-04

### Fixed
- Defined standard `Map.size` and `IdentityMap.size` as the exact semantic
  Integer number of currently stored associations.
- Defined insertion of a new association to increase size by one, value
  replacement to preserve size, and successful removal to decrease size by one.
- Made size count stored entries rather than current equality classes, so
  mutable keys that later become equal still count as separate stored
  associations.
- Prohibited hash-table capacity, buckets, tombstones, load factor, host width,
  overflow, or representation details from affecting the result.
- Defined size observation to perform no key hashing/equality, identity hashing,
  iteration snapshot, callback, search, or mutation.
- Preserved the existing keyed receiver-domain rule and open/closed/frozen
  read-only availability.

### Changed
- Synchronized all revisioned specification documents to revision 237.
  Only `PROTOS_LANGUAGE_SPEC.md` and `PROTOS_RUNTIME_SEMANTICS.md` gain
  normative semantic content in this revision.

## [0.1.236] - 2026-09-04

### Fixed
- Defined standard `TextReader.readText()` as a progress-oriented chunk read:
  once non-empty decoded text is returnable without additional source/backend
  progress, the operation cannot remain pending solely to build a larger chunk.
- Kept chunk boundaries implementation-selectable while preventing buffered
  decoded text from being hidden behind an implementation-preferred chunk size.
- Distinguished incomplete encoded characters and state-only decoder progress,
  which may still require more input before any String is returnable.
- Defined a later I/O/decoding error discovered by read-ahead not to leap ahead
  of valid decoded text that logically precedes it.
- Required all valid decoded text preceding such an error to be exposed, in any
  permitted finite chunking, before the error becomes a text-read outcome.
- Reconciled permanent TextReader failure so deferred read-ahead errors poison
  the text-reading side only when they become the committed failure outcome of
  the applicable ordered text-reading operation.
- Kept `readLine()` complete-line framing and its existing error precedence
  unchanged.

### Changed
- Synchronized all revisioned specification documents to revision 236. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.235] - 2026-09-04

### Fixed
- Defined standard `Map()` and `IdentityMap()` as zero-argument ordinary
  invocation factories for fresh open empty keyed objects.
- Kept normal Map and IdentityMap construction distinct so factory inheritance
  preserves the corresponding key-matching semantics.
- Defined non-empty factory argument vectors to fail with ordinary
  argument-count semantics after argument evaluation and before Map allocation.
- Deliberately avoided constructor consumption of pairs, Arrays, Maps, `each`,
  iterators, or other implicit entry sources while Core has no general iterable
  protocol.
- Composed inherited factory behavior with prototypes by using the actual
  invocation receiver as the new Map's delegation parent without granting the
  prototype keyed state.
- Defined factory creation to perform no `init`, key hashing/equality, identity
  hashing, callbacks, iteration, or entry insertion.

### Changed
- Synchronized all revisioned specification documents to revision 235.
  Only `PROTOS_LANGUAGE_SPEC.md` and `PROTOS_RUNTIME_SEMANTICS.md` gain
  normative semantic content in this revision.

## [0.1.234] - 2026-09-04

### Fixed
- Defined `ByteReadable.read(maxBytes)` `maxBytes` as a maximum result size, not
  an exact-fill or minimum-fill requirement.
- Required an ordered ordinary read to stop waiting once at least one next
  logical octet is returnable without additional source/backend progress.
- Prevented implementation buffering/read-ahead policy from withholding already
  unread logical input merely to construct a larger preferred chunk.
- Preserved backend/native waiting freedom until useful input, EOF, or failure is
  established, without introducing polling or a portable `available()` API.
- Specialized standard File reads so a position before current EOF returns a
  non-empty prefix of currently readable file bytes rather than waiting to fill
  `maxBytes` or for later file growth.
- Kept exact-fill/delimiter behavior available only to stronger protocols that
  explicitly define it.

### Changed
- Synchronized all revisioned specification documents to revision 234. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.233] - 2026-09-04

### Fixed
- Defined captured standard open-configuration validation as a preflight semantic
  step before filesystem namespace resolution, acquisition, creation, truncation,
  or other target/backend I/O.
- Required a standard-invalid configuration to fail through the open Future with
  zero target filesystem effect and without exercising filesystem authority
  against the supplied Path.
- Prevented invalid combinations such as append+truncate or read-only truncate
  from being discovered only after a target has been looked up or modified.
- Defined invalid-configuration precedence over target-dependent outcomes for an
  otherwise valid Path value.
- Preserved the asynchronous `filesystem.open` API by permitting an already-
  failed Future rather than introducing a special synchronous exception path.
- Distinguished globally invalid configuration tuples from semantically valid
  configurations that a selected backend/resource may later be unable to support.

### Changed
- Synchronized all revisioned specification documents to revision 233. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.232] - 2026-09-04

### Fixed
- Defined standard `Bytes.size` as the exact semantic Integer number of current
  octets in receiver-owned Bytes state.
- Made `Bytes.size` use the same logical octet length that defines valid
  `Bytes.at` / `atPut` indexes.
- Prohibited host buffer width, capacity, overflow, wrapping, saturation,
  signed-byte representation, or storage layout from changing the result.
- Defined `size` as read-only and available for open, closed, and frozen Bytes
  without decoding contents or invoking user behavior.
- Preserved the existing Bytes receiver-domain rule and the existing I/O rule
  that `Bytes` need not be a mandatory Core-prelude binding.

### Changed
- Synchronized all revisioned specification documents to revision 232.
  Only `PROTOS_LANGUAGE_SPEC.md` and `PROTOS_RUNTIME_SEMANTICS.md` gain
  normative semantic content in this revision.

## [0.1.231] - 2026-09-04

### Fixed
- Defined standard BufferedReader to preserve the underlying ByteReadable
  source's EOF permanence/resumability instead of making one observed EOF
  permanently sticky merely because buffering is present.
- Required a later BufferedReader read, after a non-permanent source EOF and with
  no earlier buffered bytes, to re-evaluate the source under ordinary ordered
  ByteReadable semantics.
- Preserved pull semantics: a read that reaches current EOF completes `null`
  rather than waiting, polling, or subscribing for hypothetical future data.
- Required unread buffered bytes to remain ahead of bytes made readable by a
  later source-state change and prohibited stale EOF markers from bypassing them.
- Allowed permanent-EOF memoization only when the source contract itself makes
  EOF permanent.
- Kept TextReader's explicitly stronger permanent text-EOF lifecycle unchanged.

### Changed
- Synchronized all revisioned specification documents to revision 231. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.230] - 2026-09-04

### Fixed
- Defined the standard `Array` invocation behavior as an ordinary polymorphic
  factory for fresh open standard Arrays.
- Defined supplied positional arguments as the new Array's exact elements in
  order, with no numeric-length overload: `Array(3)` is one element `3`.
- Defined each call, including `Array()`, to create a fresh Array identity with
  shallow element references.
- Composed inherited Array-factory behavior with prototypes by making the actual
  invocation receiver the new Array's delegation parent without granting that
  receiver indexed state.
- Kept indexed-state receiver-domain rules intact: ordinary Array methods do not
  confer state; the factory creates a separate new state-owning object.
- Defined the standard Array factory not to send `init`, run callbacks, perform
  conversions, or introduce hidden suspension after ordinary argument
  evaluation.

### Changed
- Synchronized all revisioned specification documents to revision 230.
  Only `PROTOS_LANGUAGE_SPEC.md` and `PROTOS_RUNTIME_SEMANTICS.md` gain
  normative semantic content in this revision.

## [0.1.229] - 2026-09-04

### Closed
- Standardized Core exclusive mutable partitioning for byte-indexed P-local state
  through `Bytes.parallelRange(start, length, worker, arguments...)`.
- Added fixed-size P-local `ByteRegion` capabilities with local zero-based byte
  indexing and recursive `parallelRange`.
- Restricted region creation to P so Actor source values remain unchanged.
- Defined half-open range validation, zero-length behavior, reservation lifetime,
  synchronous overlap rejection, and parent-access rejection without blocking.
- Allowed unrelated unreserved byte access to proceed while disjoint children run.
- Defined atomic successful publication of exactly the reserved bytes only after
  normal child completion and successful child-result transfer.
- Defined failure/cancellation/result-transfer failure to release authority without
  publishing partial region mutation.
- Defined `ByteRegion` as scoped P-local authority that cannot escape by ordinary
  P/Actor transfer or serialization.
- Deliberately left generic writable Array/object partitioning open because
  disjoint indexes do not establish disjoint reachable mutable graphs.
- Replaced the broad partition API/overlap open items with that narrower unresolved
  generic-partition question.

### Added
- Added `ParallelRegionOverlap`, `ParallelRegionInUse`, and
  `ParallelRegionOutsideP`, each delegating directly to `Error`.

### Changed
- Updated `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_RUNTIME_SEMANTICS.md`,
  `docs/design/CONCURRENCY_DESIGN.md`, and `PROTOS_IO_MODEL.md` for the byte-region
  authority boundary.
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 229.

## [0.1.228] - 2026-09-04

### Fixed
- Defined every resource newly created by standard `create` or `createNew` to
  begin as an empty logical byte sequence with size zero.
- Made the empty initial content part of the creation semantic result rather than
  an implementation/backend-selected default.
- Defined `preserve` and `truncate` consistently for newly created resources:
  there is no prior content to preserve and truncate introduces no second
  destructive effect when creation already established size zero.
- Allowed sparse, lazy, remote, virtual, copy-on-write, and other physical
  representations when their Protos-visible initial byte sequence is empty.
- Clarified that later independently authorized writes may change the resource
  after creation commitment without changing what the creation itself established.
- Required backends with non-empty/template/residual creation defaults to emulate
  empty creation or reject standard create/createNew semantics.

### Changed
- Synchronized all revisioned specification documents to revision 228. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.227] - 2026-09-04

### Fixed
- Defined truncate-on-open as an indivisible Protos content effect: before its
  commitment the open contributes no truncation; at commitment it establishes
  complete logical size zero.
- Prohibited failed or cancelled opens from exposing an intermediate nonzero
  truncation attributable to implementation/backend stepwise resizing.
- Required tentative truncate work to be restored, virtualized, deferred, or
  otherwise hidden when the complete zero-size effect has not committed.
- Preserved the existing rule that a later failure after committed truncation
  does not restore discarded content.
- Defined already-empty and newly-created-empty resources so `truncate` does not
  invent a second destructive commitment when no content change is needed.
- Kept independently authorized resource changes outside this operation-specific
  failure-atomicity and avoided requiring the returned File to expose Truncatable.

### Changed
- Synchronized all revisioned specification documents to revision 227. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.226] - 2026-09-04

### Fixed
- Defined standard `Map.each` and `IdentityMap.each` callbacks through the same
  ordinary polymorphic invocation domain already used by `Array.each`.
- Allowed user-defined invokable objects as callbacks instead of leaving
  callback eligibility implementation-selected or accidentally Closure-only.
- Defined receiver validation before callback-callability validation and
  callability validation before association-snapshot establishment.
- Kept callback arity checking at the actual two-argument invocation rather
  than inventing a separate preflight signature mechanism.
- Required non-invokable callbacks to fail before snapshot capture and without
  key search, hashing, equality, identity hashing, or keyed-state mutation.

### Changed
- Synchronized all revisioned specification documents to revision 226.
  Only `PROTOS_LANGUAGE_SPEC.md` and `PROTOS_RUNTIME_SEMANTICS.md` gain
  normative semantic content in this revision.

## [0.1.225] - 2026-09-04

### Closed
- Closed immutable physical-sharing eligibility/representation for P as an
  implementation optimization rather than a new public Protos capability.
- Defined physical sharing as permitted only when every Protos observation is
  equivalent to the required isolated logical values.
- Clarified that logically mutable values may still use immutable backing,
  copy-on-write, remapping, exclusivity-proven reuse, or equivalent invisible
  optimizations.
- Clarified that shallow `freeze()` neither grants transitive P shareability nor
  is required for safe implementation-level immutable sharing.
- Prohibited public/runtime-observable shareability predicates, pinning,
  zero-copy state, copy-on-write state, or backing-allocation identity in Core
  v0.1.
- Required an implementation to fall back to another semantics-preserving
  representation when a sharing optimization is unavailable rather than fail an
  otherwise P-transferable value.
- Removed the now-closed immutable-sharing eligibility/representation item from
  Open Design Topics.
- Repaired remaining Actor-only wording in standard-prelude sharing consequences
  so the already-closed Actor/P isolation rule is stated consistently.

### Changed
- Updated `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_RUNTIME_SEMANTICS.md`, and
  `docs/design/CONCURRENCY_DESIGN.md` with the closed physical-sharing boundary.
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 225.

## [0.1.224] - 2026-09-04

### Fixed
- Defined successful reads on a receiver whose ByteReadable and ByteSeekable
  capabilities share one logical sequence position to advance that position by
  exactly the number of octets actually returned.
- Defined EOF to advance the shared logical position by zero.
- Reaffirmed successful cancellation and ordinary read failure as zero-position-
  advance outcomes even when implementation read-ahead moved a native cursor.
- Prevented `maxBytes`, native read size, prefetch size, or hidden buffering from
  changing the observable post-read logical position.
- Kept independently authorized mutable-resource changes under their existing
  visibility semantics while making each successful read's position aftermath
  deterministic.

### Changed
- Synchronized all revisioned specification documents to revision 224. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.223] - 2026-09-04

### Fixed
- Defined Core call spread to accept standard Array indexed state only rather
  than leaving the spreadable-object domain implementation-selected.
- Defined each spread argument to evaluate once at its ordinary left-to-right
  position and contribute a shallow ascending-index snapshot of current Array
  element references.
- Defined later argument effects and later source-Array mutation not to rewrite
  elements already contributed by an earlier spread.
- Prohibited hidden `each`, `at`, `size`, iterator, conversion, callback, or
  suspension behavior during standard call-spread extraction.
- Preserved empty-Array expansion, shallow element identity, open/closed/frozen
  read-only behavior, and implementation freedom to avoid physical snapshots.
- Reserved any future generic iterable/spreadable protocol for an explicit
  normative design rather than implicitly inventing one through call syntax.

### Changed
- Synchronized all revisioned specification documents to revision 223.
  Only `PROTOS_LANGUAGE_SPEC.md` and `PROTOS_RUNTIME_SEMANTICS.md` gain
  normative semantic content in this revision.

## [0.1.222] - 2026-09-04

### Closed
- Standardized `Closure.parallel(arguments...)` as the Core v0.1 public isolated
  parallel-submission API returning an ordinary Future.
- Closed the bootstrap representation through parallel Closure projection:
  executable code and user-visible Closure value state may cross, while caller
  lexical contexts, caller `this`, caller return home, caller `methodHome`, and
  caller dynamic handlers do not.
- Defined every P computation to use a fresh P root execution environment with
  the frozen standard prelude, `this === null`, and a P-local return home.
- Rejected implementation-selected capture-safety analysis: caller captures
  never cross; attempted dependence on unavailable caller lexical names fails
  through ordinary lookup inside P.
- Defined explicit P arguments and the bootstrap Closure as one atomic combined
  input graph with preserved cycles and aliasing.
- Added standard `NonParallelValue` for synchronous input-boundary rejection and
  for untransferable normal results or failure values.
- Defined Closure values inside P input graphs as projectable ordinary Closures,
  not capture-transferable Closures.
- Defined P failure-value transfer and deterministic fallback when a P Error
  graph itself cannot cross the boundary.
- Defined non-local return from projected Closures to target only the fresh
  P-local return home, never the caller.
- Defined ordinary `closure.future()` created inside P as cooperative P-local
  work serialized against the same P mutable state.
- Defined nested `closure.parallel(...)` as a fresh isolation boundary and
  clarified that detachment cannot make P-local work survive the P domain as a
  persistent identity.
- Extended standard-prelude sharing semantics from Actor-only wording to Actor/P
  isolation domains while preserving semantic immutability of physically shared
  Protos objects.
- Removed the now-closed parallel API/bootstrap, capture-safety mechanism, and
  snapshot-point items from the Open Design Topics list.

### Changed
- Updated `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_RUNTIME_SEMANTICS.md`, and
  `docs/design/CONCURRENCY_DESIGN.md` with the closed P bootstrap/API semantics.
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 222.

## [0.1.221] - 2026-09-04

### Fixed
- Clarified generic ByteReadable EOF as an observation of current sequence state,
  not an unconditional permanent latch for every mutable-sequence receiver.
- Defined standard readable File EOF at each ordered read evaluation point.
- Required EOF reads to leave File position unchanged and allowed later reads to
  observe bytes made visible by later authorized growth without an intervening seek.
- Kept growth visibility/order under existing backend/cross-capability semantics.
- Defined current-EOF reads to complete with `null` rather than wait for future growth.
- Preserved TextReader's stronger permanent text-EOF lifecycle.

### Changed
- Synchronized all revisioned specification documents to revision 221. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.220] - 2026-09-03

### Fixed
- Removed the accidental Closure-only restriction from standard `Array.each`.
- Defined iteration callbacks using the same polymorphic invocation domain as
  ordinary parenthesized calls, including user-defined invokable objects.
- Required callability validation after ordinary receiver/argument evaluation
  and Array receiver validation but before snapshot establishment or callback
  execution.
- Kept non-invokable callback failure explicit without introducing a second
  callback/type hierarchy.

### Changed
- Synchronized all revisioned specification documents to revision 220.
  Only `PROTOS_LANGUAGE_SPEC.md` and `PROTOS_RUNTIME_SEMANTICS.md` gain
  normative semantic content in this revision.

## [0.1.219] - 2026-09-04

### Fixed
- Defined standard positioned File writes as overwrite-at-current-logical-position
  operations rather than leaving overwrite versus insertion behavior implicit.
- Defined a contributed prefix of length `k` to occupy the contiguous offsets
  starting at the write's ordered starting position and to advance the File's
  logical position by exactly `k`.
- Defined positioned writes to grow the file when contributed bytes extend past
  EOF.
- Required logical gap octets created when a write begins beyond EOF to read as
  zero, independent of sparse-file, extent, explicit-fill, or other backend
  representation.
- Required zero-contribution cancellation/failure to leave file size/content
  unchanged even when the logical position was beyond EOF, preventing tentative
  backend extension from leaking through a failed Future.
- Kept independently opened Files and external/backend changes under their
  existing cross-capability semantics without introducing a new global file
  ordering or atomicity domain.

### Changed
- Synchronized all revisioned specification documents to revision 219. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.218] - 2026-09-04

### Closed
- Closed the semantic core of isolated parallel execution while keeping its
  exact public API, syntax, bootstrap representation, partition representation,
  and implementation mechanisms open.
- Defined successful parallel submission to establish deterministic input
  snapshot state before control returns to the caller, independent of delayed
  scheduling.
- Defined that P never semantically mutates the calling Actor's original mutable
  input and publishes mutable result state only through successful completion.
- Defined failure and cancellation to publish no partially mutated P-owned state.
- Defined exclusive writable partitioning over logically disjoint mutable state,
  not merely non-overlapping physical ranges or top-level Array indexes.
- Defined nested partition authority as derived disjoint authority and required
  nested parallel work to remain capable of progress over bounded CPU carriers.
- Required deterministic parallel operations to keep observable result,
  combination ordering, and failure selection independent of worker scheduling,
  carrier count, chunk timing, and work-stealing choices.
- Defined isolated parallel execution as a CPU-computation domain rather than a
  second Actor-like effects domain.
- Prohibited P from implicitly inheriting Actor sender identity, Actor-local
  continuations, I/O authority, Process/Node/Cluster authority, or other ambient
  caller authority.
- Clarified that ActorRef/GroupRef transferability across Actor messaging does
  not imply transferability into P; any future P-safe effect capability requires
  its own normative contract.
- Preserved ordinary Closure capture-by-reference semantics: a future P API must
  validate/avoid unsafe captures or introduce an explicit bootstrap mechanism,
  never silently reinterpret captures as by-value.

### Changed
- Updated the canonical language and runtime concurrency summaries and the
  normative I/O capability model to reflect the closed P isolation,
  publication, and authority boundaries.
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 218.

## [0.1.217] - 2026-09-03

### Fixed
- Defined distinct `filesystem.open` calls as independent asynchronous namespace
  acquisitions rather than an implicit per-Filesystem or per-Path FIFO.
- Clarified that same-Actor sequential invocation without awaiting an earlier
  open does not order the opens' namespace selection/commitment points.
- Defined interacting pending opens, including createNew/existing, concurrent
  createNew, and truncate-on-open cases, by each operation's own race-free
  selection and commitment point.
- Required programs needing acquisition dependency to establish it explicitly
  through ordinary Future sequencing or a stronger normative protocol.
- Preserved implementation freedom to serialize, batch, or overlap opens when
  that machinery does not create stronger portable ordering.
- Avoided requiring a global Filesystem lock, per-Path queue, or same-Actor
  namespace FIFO while preserving post-open File ordering and append-placement
  guarantees.

### Changed
- Synchronized all revisioned specification documents to revision 217. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.216] - 2026-09-03

### Closed
- Closed `Cluster membership protocol` for Core v0.1 by classifying the
  interoperable membership protocol/API as outside Core rather than leaving an
  implementation-selectable semantic hole.
- Defined the minimal Core membership contract: membership is distinct from
  reachability, Node identity, physical existence, and Authority.
- Prohibited transport connectivity/reachability from implicitly creating
  membership and prohibited membership loss from proving Node/Process death.
- Defined that implementation-specific membership algorithms may establish
  Cluster membership knowledge but cannot weaken closed Core identity,
  uncertainty, partition, split-brain, or Authority semantics.
- Explicitly stated that Core v0.1 does not standardize join/leave/down APIs,
  wire format, gossip/consensus algorithm, convergence timing, or
  cross-implementation Cluster-membership interoperability.
- Reserved interoperable membership, epochs, quorum/consensus, and
  failure-driven removal for a future normative Cluster facility.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 216.

## [0.1.215] - 2026-09-03

### Fixed
- Defined `Array.size` as the exact semantic Integer count of current indexed
  elements, independent of host index width or representation.
- Defined every invocation's `args` as a fresh frozen standard Array containing
  exactly the caller-supplied positional arguments in source order.
- Defined each rest-parameter binding as its own fresh frozen standard Array of
  the remaining caller-supplied positional arguments.
- Made argument/rest Array identity explicit so implementations cannot
  observably reuse one shared empty or content-equal collection object.
- Preserved shallow aliasing of argument objects while eliminating a separate
  implementation-selected argument-collection kind.
- Preserved optimization freedom through scalar replacement and virtual Arrays
  when fresh identity, frozen behavior, ordering, `at`, `size`, and `each`
  semantics remain observable as specified.

### Changed
- Synchronized all revisioned specification documents to revision 215.
  Only `PROTOS_LANGUAGE_SPEC.md` and `PROTOS_RUNTIME_SEMANTICS.md` gain
  normative semantic content in this revision.

## [0.1.214] - 2026-09-03

### Fixed
- Reconciled strict/fatal decoding with the already-standardized explicit
  U+FFFD replacement policy instead of treating malformed input as
  unconditionally fatal in line reading and one-shot EOF handling.
- Defined portable UTF8/UTF16LE/UTF16BE replacement segmentation by Unicode
  17.0.0 maximal subparts, with exactly one U+FFFD per consumed maximal subpart.
- Defined incomplete final input to fail under strict decoding and to use the
  same malformed-input replacement rule under replacement decoding.
- Made replacement output invariant under native read, buffering, decoder-call,
  vectorization, and other implementation-selected chunk boundaries.
- Required host-provided non-portable Encodings to make replacement segmentation
  deterministic within their host-bound contract rather than leaking converter
  call boundaries or library-version heuristics.
- Defined malformed source octets consumed for U+FFFD replacement to participate
  in `readLine(maxBytes)` source-byte accounting, while preserving strict-mode
  decoding-error precedence.

### Changed
- Synchronized all revisioned specification documents to revision 214. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.213] - 2026-09-03

### Closed
- Closed `Split-brain mitigation mechanisms` for Core v0.1 by defining that
  Core has no automatic partition winner or implementation-selected downing
  strategy.
- Reduced Core split-brain safety to the existing scoped Authority invariant:
  non-authoritative work may continue, while an operation requiring Authority
  must not proceed unless current valid Authority can be demonstrated.
- Prohibited majority/oldest/local-side/time-based heuristics from silently
  granting Authority or terminating the opposite partition.
- Defined that Core performs no implicit partition-healing state merge,
  uncertain-message replay, identity rewrite, or remote-side replacement.
- Kept Cluster membership, Authority acquisition/transfer, quorum/consensus,
  leases, fencing, and explicit future split-brain policies as separate
  mechanisms requiring their own normative contracts.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 213.

## [0.1.212] - 2026-09-03

### Fixed
- Defined the initial logical sequence position of every newly opened standard
  File with position-sensitive read/write behavior as byte offset zero.
- Applied the same rule across existing/create/createNew, preserve/truncate, and
  positioned/append open configurations.
- Clarified that append mode changes each write's placement to current EOF but
  does not initialize the File's Protos logical position at EOF.
- Prevented host/runtime append helpers or native cursors with different initial
  positions from leaking platform-dependent first-read, first-position, or
  post-open sequencing behavior.
- Kept the rule representation-independent: implementations may use positional
  I/O, virtual cursors, or native cursor adjustment when the observable logical
  position remains zero.
- Required backends unable to provide or emulate the standard initial position
  not to expose a position-sensitive standard File with a host-selected cursor.

### Changed
- Synchronized all revisioned specification documents to revision 212. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.211] - 2026-09-03

### Fixed
- Defined standard String indexing and `size` with the untailored Unicode 17.0.0
  default extended-grapheme-cluster rules from UAX #29 revision 47.
- Defined `String.size` to return the exact semantic Integer grapheme count.
- Defined `String.at` to accept exact semantic Integer indices only, reject
  negative/out-of-range/coerced indices, and return the exact scalar subsequence
  forming the selected grapheme cluster as a String.
- Prohibited host locale, ICU/Unicode upgrades, normalization, rendering
  tailoring, and internal encoding representation from changing Core-visible
  String boundaries.
- Kept String immutable and made explicit that bracket-read support does not
  imply a hidden standard in-place `atPut` mutation protocol.

### Changed
- Synchronized all revisioned specification documents to revision 211.
  Only `PROTOS_LANGUAGE_SPEC.md` and `PROTOS_RUNTIME_SEMANTICS.md` gain
  normative semantic content in this revision.

## [0.1.210] - 2026-09-03

### Closed
- Closed `Network-partition detection and reporting` for Core v0.1.
- Defined that Core has no proven `NETWORK_PARTITION` semantic state: ambiguous
  communication loss is reported as `UNREACHABLE` or `UNKNOWN`.
- Prohibited implementation-specific partition timeouts and diagnostics from
  changing lifecycle, membership, Authority, ActorRef, monitoring, messaging,
  or replacement semantics.
- Defined that symmetric partition suspicion grants neither side Authority and
  does not prove remote termination.
- Preserved identity across transient reachability loss when no independent
  authoritative decision ended or removed the incarnation.
- Kept split-brain resolution, membership removal, fencing, quorum decisions,
  and Authority acquisition as separate open distributed mechanisms.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 210.

## [0.1.209] - 2026-09-03

### Fixed
- Closed the TextWriter failure-frontier ambiguity for later outstanding ordered
  text writes when an earlier committed write can still poison the wrapper.
- Prohibited a later text write from making target-visible output before every
  earlier ordered text write has reached an aftermath that leaves the writer
  usable for later output.
- Allowed speculative validation, encoding, checkpointing, staging, and other
  reversible pipelining while keeping irreversible byte contribution ordered
  behind the predecessor's failure frontier.
- Required permanent predecessor failure to make later outstanding and new text
  writes fail with zero contribution of their own and no committed encoder-state
  advance.
- Prevented proxy routing and backend/native completion timing from deciding
  whether bytes from a later text write escape after an earlier writer-poisoning
  failure.
- Preserved recovery through a stronger downstream protocol only when that
  protocol supplies enough semantic information to establish exact remaining
  output and encoder/output state.

### Changed
- Synchronized all revisioned specification documents to revision 209. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.208] - 2026-09-03

### Closed
- Closed `Node failure detection mechanism` for Core v0.1.
- Defined that communication loss and failure-detector suspicion cannot by
  themselves establish authoritative Node `TERMINATED`.
- Prohibited implementation-specific heartbeat, phi, retry, grace-period,
  infrastructure-probe, or host-status thresholds from changing Core-visible
  Node lifecycle.
- Preserved reversible `UNREACHABLE`/`UNKNOWN` state for remote Node
  communication loss.
- Kept Node incarnation identity stable across transient connectivity loss.
- Kept Cluster membership removal, partition downing, fencing, and split-brain
  resolution separate and explicitly outside this Core closure.
- Required any future distributed failure detector to separate suspicion from
  an authoritative downing/removal decision and to define its authority and
  consequences normatively.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 208.

## [0.1.207] - 2026-09-03

### Fixed
- Defined standard Bytes indexed state as receiver-owned, finite, dense,
  zero-based octet storage.
- Defined `Bytes.at` to require an exact semantic Integer index and to return
  the stored octet as an exact semantic Integer in `0 .. 255`.
- Defined `Bytes.atPut` to require an in-range Integer index and Integer byte
  value in `0 .. 255`, rejecting truncation, masking, wrapping, parsing, and
  implicit conversion.
- Defined standard byte replacement as fixed-length existing-position mutation,
  with receiver-domain and open/closed/frozen behavior parallel to other
  receiver-owned indexed state.
- Kept Bytes equality/hash identity-based and separated raw-octet semantics from
  text, host byte signedness, endianness, and storage representation.

### Changed
- Synchronized all revisioned specification documents to revision 207.
  Only `PROTOS_LANGUAGE_SPEC.md` and `PROTOS_RUNTIME_SEMANTICS.md` gain
  normative semantic content in this revision.

## [0.1.206] - 2026-09-03

### Closed
- Closed `Process failure detection mechanism` for Core v0.1.
- Defined that Core has no automatic distributed failure detector for remote
  Processes and no implementation-specific timeout/heartbeat heuristic may
  fabricate `TERMINATED`.
- Distinguished direct runtime lifecycle knowledge from inferred remote
  failure suspicion.
- Required communication loss, timeout, reconnect failure, heartbeat silence,
  and Node unreachability to remain `UNREACHABLE`/`UNKNOWN` absent independent
  authoritative termination knowledge.
- Composed this boundary with `ActorRef.termination()` so remote transport loss
  cannot masquerade as Actor death.
- Reserved leases, epochs, quorum membership, fencing, explicit downing, and
  similar mechanisms for a future normative distributed-runtime facility.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 206.

## [0.1.205] - 2026-09-03

### Fixed
- Repaired the normative paragraph boundary in filesystem creation semantics
  after the `existing` race-free-selection change was integrated.
- Removed wording that accidentally attached `create`'s absent-target behavior
  to the summary sentence for `existing`, which could be read as allowing
  `creation: existing` to create a missing target.
- Restored the intended disjoint semantics: `existing` selects an already
  existing resource or fails; `create` selects an existing resource or creates
  one when absent.
- Preserved the already-defined race-free selection, confinement, stable File
  binding, and concurrent namespace-operation rules without introducing a new
  filesystem mechanism.

### Changed
- Synchronized all revisioned specification documents to revision 205. Only
  `PROTOS_IO_MODEL.md` gains normative corrective content in this revision.


## [0.1.204] - 2026-09-03

### Fixed
- Defined `readLine(maxBytes)` byte accounting for stateful and multibyte
  encodings in terms of the encoded source interval for the current line.
- Required every valid pre-terminator source octet to count, including
  state/shift/control octets that produce no Unicode scalar value.
- Excluded the encoded LF/CR/CRLF terminator extent itself and avoided charging
  decoder state bytes consumed before the current line begins a second time.
- Prevented stateful encodings from bypassing the safety bound with arbitrarily
  many non-text-emitting shift/control sequences.
- Defined line-too-long versus decoding-error precedence for valid state-only
  input without relying on converter-specific notions of which byte "belongs"
  to a character.
- Preserved implementation freedom for buffering and converter internals while
  making the observable byte-boundary result independent of those internals.

### Changed
- Synchronized all revisioned specification documents to revision 204. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.203] - 2026-09-03

### Fixed
- Defined standard `Array.each(block)` as deterministic ascending-index
  iteration over a shallow element-reference snapshot captured before callbacks.
- Defined same-Array element replacement during callbacks or suspension as
  permitted when ordinary Array state rules allow it, without changing the
  current iteration snapshot.
- Required `each` to return the receiver after normal completion and to stop
  immediately on error or ordinary non-local unwind without rolling back prior
  effects.
- Prohibited hidden Array-wide iteration locks, mutation guards, and scheduler
  dependencies while preserving implementation freedom for snapshot storage.
- Applied the existing standard Array receiver-domain rule to `each`.

### Changed
- Synchronized all revisioned specification documents to revision 203.
  Only `PROTOS_LANGUAGE_SPEC.md` and `PROTOS_RUNTIME_SEMANTICS.md` gain
  normative semantic content in this revision.

## [0.1.202] - 2026-09-03

### Closed
- Closed `Failure-authority API` for Core v0.1 by defining that Core exposes no
  configurable public failure-authority API.
- Fixed the non-root Actor default: an unhandled fatal failure terminates that
  incarnation and failure authority performs no automatic replacement,
  escalation, sibling restart, or subtree restart.
- Preserved the existing RootActor rule: fatal RootActor failure terminates the
  Process.
- Kept ActorGroup desired-state reconciliation independent; a Group may create a
  fresh incarnation without that becoming Actor restart or failure-authority
  replacement.
- Reclassified Replace/Stop/Escalate/Ignore as future policy vocabulary rather
  than implementation-selectable Core behavior.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 202.

## [0.1.201] - 2026-09-03

### Fixed
- Defined file-URL conversion to establish URL components and path-segment
  hierarchy before percent-decoding segment data.
- Prevented percent-decoded separators, prefixes, device syntax, or other
  hierarchy-looking data from creating extra Path structure.
- Prevented percent-encoded data that decodes to `"."` or `".."` from being
  silently reclassified as portable current/parent traversal.
- Required lossless percent/text conversion and failure instead of replacement,
  truncation, normalization, delimiter reinterpretation, or other lossy native
  filename conversion.
- Confined file-URL authority interpretation to mappings already authorized by
  the receiving Filesystem capability.
- Prohibited ambient DNS, network acquisition, UNC authority expansion, or host
  root access merely to interpret a file URL.

### Changed
- Synchronized all revisioned specification documents to revision 201. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.200] - 2026-09-03

### Fixed
- Closed an admission-starvation hole left outside runnable scheduler fairness.
- Added weak fairness for continuously admission-eligible `send()` and
  `request()` operations under recurring compatible admission opportunities.
- Required later arrivals not to bypass such an operation forever.
- Made same-sender FIFO explicit across backpressure for still-live operations
  targeting the same concrete Actor.
- Preserved ActorGroup routing freedom without introducing Group-wide FIFO.
- Kept admission fairness distinct from Actor-task runnability and avoided
  mandating queue structure, round-robin scheduling, equal shares, or latency
  bounds.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 200.

## [0.1.199] - 2026-09-03

### Fixed
- Defined standard Array indexed state as receiver-owned, finite, dense, and
  zero-based at each observation point.
- Defined `Array.at` / `atPut` to accept exact semantic Integer indices only and
  to fail on negative or out-of-range indices without coercion or wrapping.
- Defined standard `Array.atPut` as replacement of an existing element only,
  with no implicit append, growth, holes, shifting, or length change.
- Defined incompatible inherited/copied Array methods to fail rather than
  borrowing ancestor storage or granting hidden Array state.
- Defined open/closed/frozen interaction for Array element replacement and kept
  standard Array equality/hash on the existing identity defaults.

### Changed
- Synchronized all revisioned specification documents to revision 199.
  Only `PROTOS_LANGUAGE_SPEC.md` and `PROTOS_RUNTIME_SEMANTICS.md` gain
  normative semantic content in this revision.

## [0.1.198] - 2026-09-03

### Fixed
- Defined `creation: existing` as one race-free select-and-open operation rather
  than leaving a probe/acquisition TOCTOU gap to implementations.
- Required the namespace state at one selection point to determine absence or
  the concrete resource selected by the open.
- Prevented a successful helper existence/metadata probe from reserving resource
  identity or being combined observably with acquisition from a later namespace
  state.
- Allowed genuinely concurrent namespace operations to determine the state seen
  before the selection point without imposing a global filesystem order.
- Applied stable File-resource binding immediately after successful selection so
  later namespace changes cannot retarget the returned File or this open's
  truncate-on-open effect.
- Required uncertain backends to fail rather than expose a standard File whose
  confined resource selection cannot be established race-safely.

### Changed
- Synchronized all revisioned specification documents to revision 198. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.197] - 2026-09-03

### Fixed
- Defined filesystem `create` as one race-free open-or-create selection rather
  than leaving an observable existence-check/acquisition gap to implementations.
- Required an existing target at the selection point to be opened and an absent
  target to be created and selected as one semantic operation.
- Prevented helper-primitive races from leaking as spurious already-exists or
  not-found failures when the standard open-or-create operation has a valid
  result.
- Allowed genuinely concurrent namespace changes to determine the namespace
  state seen at the operation's selection point without imposing a global
  filesystem order.
- Applied the stable File-resource binding rule immediately after resource
  selection so later namespace changes cannot retarget the resulting File or
  this open's truncate-on-open effect.
- Required backends that cannot provide or emulate the race-free invariant
  together with Filesystem confinement to reject standard `create` semantics.

### Changed
- Synchronized all revisioned specification documents to revision 197. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.196] - 2026-09-03

### Closed
- Closed `Monitoring API`.
- Standardized `ActorRef.termination()` as an independent non-task-backed
  Future observation of one concrete Actor incarnation.
- Defined monitoring as known-termination observation only; unreachable,
  unknown, timeout, routing loss, and network partition do not fabricate death.
- Defined independent cancellation for each observation without any lifecycle
  authority over the target Actor.
- Closed the registration-versus-termination lost-wakeup race.
- Required terminal/cancelled observation registrations to be removed or made
  inert to prevent unbounded retention.
- Preserved ActorRef incarnation identity across replacement and kept failure
  diagnostics separate from lifecycle observation.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 196.

## [0.1.195] - 2026-09-03

### Fixed
- Defined the lifetime of the same-Map keyed-entry mutation restriction when a
  user `==` callback explicitly suspends.
- Required an in-progress Map comparison scope to remain active across Actor
  turns until that comparison returns or unwinds.
- Defined conflicting keyed-entry mutation by another Actor-local task to fail
  before mutation rather than race with the suspended search or wait on a lock.
- Preserved read-only same-Map work, unrelated Map mutation, ordinary Actor
  scheduling, and exact scope release on return/error/non-local-return/cancellation
  unwind.
- Distinguished Map-scoped comparison protection from task-local dynamic error
  handlers without introducing cross-Actor synchronization.

### Changed
- Synchronized all revisioned specification documents to revision 195.
  `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_RUNTIME_SEMANTICS.md`, and
  `docs/design/CONCURRENCY_DESIGN.md` gain normative semantic content in this revision.

## [0.1.194] - 2026-09-03

### Fixed
- Defined Process standard-stream and standard-stream-encoding accessors as
  synchronous, non-waiting lookups over bootstrap-established bindings.
- Prohibited hidden external acquisition, readiness waits, remote negotiation,
  or other potentially suspending provisioning behind those non-Future calls.
- Kept pay-as-you-grow representation laziness: implementations may still defer
  local wrapper/proxy/descriptor allocation when that work is non-waiting.
- Required potentially waiting standard-stream provisioning to occur in
  Process-host/bootstrap machinery or through a separately specified
  asynchronous acquisition protocol.
- Clarified that accessor failure observes already-established unavailability
  or invalid bootstrap configuration rather than launching a delayed discovery
  of eventual host availability.

### Changed
- Synchronized all revisioned specification documents to revision 194. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.193] - 2026-09-03

### Closed
- Closed `Fatal versus non-fatal handler errors` and `Which errors terminate an Actor`.
- Defined fatality structurally: any `Error` escaping the outermost dynamic
  handler boundary of an ordinary Actor turn is fatal to that Actor incarnation.
- Defined handled errors as non-fatal unless later execution triggers another
  lifecycle cause.
- Preserved asynchronous task isolation: an unhandled child-task error fails its
  Future and becomes Actor-fatal only if later observation re-signals the error
  and it escapes an Actor turn unhandled.
- Kept cancellation distinct from fatal error semantics.
- Preserved accepted-request uncertainty instead of exposing a destination's
  internal unhandled error as an implicit reply.
- Rejected implementation-specific fatal-error whitelists and error-name/type
  taxonomies.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 193.

## [0.1.192] - 2026-09-03

### Fixed
- Defined a Core String semantic value as an exact ordered sequence of Unicode
  scalar values.
- Made String semantic identity, default equality, and identity hashing depend
  on that exact sequence rather than implementation-selected Unicode
  normalization, host encoding, locale, case folding, or grapheme processing.
- Defined canonically equivalent but differently represented scalar sequences as
  distinct Core String values unless a program explicitly normalizes them.
- Preserved the existing exact `LF` / `CR` / `CRLF` String-content distinction.

### Changed
- Synchronized all revisioned specification documents to revision 192.
  Only `PROTOS_LANGUAGE_SPEC.md` and `PROTOS_RUNTIME_SEMANTICS.md` gain
  normative semantic content in this revision.

## [0.1.191] - 2026-09-03

### Fixed
- Defined one logical encoder-state/output-ordering domain for all standard
  `TextWriter.writeText()` and `writeLine()` operations on the same writer.
- Preserved per-Actor text-write invocation order even while earlier Futures
  remain pending.
- Defined genuinely concurrent cross-Actor text writes as initially unordered,
  with routing/admission choosing one stable order for encoding validation,
  encoder-state evolution, cancellation/failure aftermath, and byte output.
- Prevented host scheduling, speculative encoding, or downstream byte-I/O timing
  from selecting a different observable encoder order.
- Composed any `Flushable` frontier and `Closable` cutover exposed by a
  TextWriter with that same logical text-output flow.
- Kept implementation freedom for staging, pipelining, buffering, and
  overlapping backend work without requiring a global lock or one native write
  at a time.

### Changed
- Synchronized all revisioned specification documents to revision 191. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.190] - 2026-09-03

### Fixed
- Generalized the ordinary `Object` equality/hash default from only
  identity-bearing Core objects to every Core object lacking an explicit
  normative specialization.
- Closed the remaining implementation-choice gap for value-identity objects
  such as String values, canonical Booleans, and `null`.
- Required absent-specialization standard `==` to use `===` and standard
  `hash` to use `identityHashOf`, preserving each value category's semantic
  identity.
- Preserved explicit Number equality/hash specialization, the documented
  Map/IdentityMap consequence, and ordinary user-defined overrides.

### Changed
- Synchronized all revisioned specification documents to revision 190.
  Only `PROTOS_LANGUAGE_SPEC.md` and `PROTOS_RUNTIME_SEMANTICS.md` gain
  normative semantic content in this revision.

## [0.1.189] - 2026-09-03

### Closed
- Closed `Actor garbage collection`.
- Defined live Actor lifetime as explicit lifecycle rather than ordinary
  reachability garbage collection.
- Prohibited hidden termination from loss of known ActorRefs, idleness, or
  memory-pressure heuristics.
- Allowed post-termination Actor implementation state to be reclaimed or
  compacted once remaining ActorRef, identity, monitoring, routing, and
  communication semantics are preserved.
- Kept virtual-actor/passivation models as possible future higher-level
  abstractions rather than changing concrete Actor incarnation semantics.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 189.

## [0.1.188] - 2026-09-03

### Fixed
- Distinguished an absent Environment binding from a Protos lookup String that
  cannot be losslessly represented as one valid native environment-variable
  name.
- Required both `Environment.get(name)` and `contains(name)` to fail for an
  unrepresentable/invalid native query name instead of returning `null` or
  `false`.
- Required query-name validation to occur before value decoding and ordinary
  lookup-result selection.
- Prohibited lossy replacement, truncation, normalization, delimiter
  reinterpretation, or implementation-selected conversion from silently
  changing the lookup name.
- Kept the concrete native name repertoire host-dependent while making the
  portable failure-vs-absence distinction deterministic.

### Changed
- Synchronized all revisioned specification documents to revision 188. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.187] - 2026-09-03

### Closed
- Closed fundamental graceful Actor termination semantics.
- Defined one irreversible stop cutover that prevents new concrete-Actor
  acceptance and new ordinary turns.
- Defined accepted-but-not-started interactions as lost accepted work instead
  of draining arbitrary mailbox backlog or pretending non-acceptance.
- Preserved non-preemptive execution for an already-running turn until normal
  completion or its next portable cancellation boundary.
- Reused existing Actor-termination cancellation for Actor-local tasks and
  non-task-backed producer Futures.
- Distinguished graceful stop from fatal failure and preserved independent
  Group desired-state reconciliation.
- Removed `Actor graceful shutdown` and `Actor stop API and exact lifecycle
  mechanics` from Open Design Topics; exact administrative/public API shape
  remains explicitly open inside the CLOSED lifecycle section.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 187.

## [0.1.186] - 2026-09-03

### Fixed
- Defined a uniform default equality/hash rule for identity-bearing Core objects
  that have no explicitly specialized normative behavior.
- Required such objects to use semantic identity for standard `==` and
  `identityHashOf` for standard `hash`.
- Prevented built-in containers, buffers, Futures, errors, contexts, modules,
  prototypes, and other identity-bearing objects from acquiring
  implementation-selected structural/content equality or hashing.
- Preserved explicit specializations such as Number hashing and ordinary
  user-defined `==` / `hash` overrides.

### Changed
- Synchronized all revisioned specification documents to revision 186.
  Only `PROTOS_LANGUAGE_SPEC.md` and `PROTOS_RUNTIME_SEMANTICS.md` gain
  normative semantic content in this revision.

## [0.1.185] - 2026-09-03

### Fixed
- Defined `sync()` as a logical durability frontier within the receiver's
  existing sequence-state ordering domain.
- Required receiver changes ordered before a sync frontier to be covered by a
  successful sync even when their Futures were still pending in implementation
  queues.
- Defined genuinely concurrent cross-Actor change/sync requests as initially
  unordered, with routing/admission choosing a stable relative order.
- Required that stable order to determine whether a competing receiver change
  belongs to the sync frontier or is later state outside it.
- Kept sync local to the logical receiver: it does not create a global
  durability or Actor-memory barrier across independently opened aliasing Files.
- Allowed a backend to persist extra later/independent state as an
  implementation side effect without promoting that extra persistence into a
  portable Protos ordering guarantee.

### Changed
- Synchronized all revisioned specification documents to revision 185. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.184] - 2026-09-03

### Fixed
- Defined the receiver domain of standard `Map` and `IdentityMap` keyed
  behavior in terms of receiver-owned keyed-entry state rather than delegation.
- Made inherited/copied standard Map methods fail on incompatible receivers
  before hashing, equality callbacks, identity-key search, snapshot capture, or
  keyed-entry mutation.
- Prevented delegation from borrowing ancestor Map entries or lazily granting
  hidden associative storage to ordinary child objects.
- Preserved ordinary user-defined behavior and explicitly generic standard
  behavior without introducing a class hierarchy or second delegation model.

### Changed
- Synchronized all revisioned specification documents to revision 184.
  Only `PROTOS_LANGUAGE_SPEC.md` and `PROTOS_RUNTIME_SEMANTICS.md` gain
  normative semantic content in this revision.

## [0.1.183] - 2026-09-03

### Fixed
- Defined the aftermath of a failed `ByteReadable.read` when lower-level bytes
  were obtained and preserved before the failure outcome was reported.
- Required preserved bytes to remain ordinary unread input while treating the
  already-reported error as the outcome of the failed read, not as a queued
  stream element that must automatically fail a later read again.
- Required later reads to fail only when their then-current receiver/backend
  state independently requires failure, including persistent or distinct
  backend errors.
- Prevented implementations from diverging solely over whether one previously
  reported host error is replayed after preserved bytes are drained.

### Changed
- Synchronized all revisioned specification documents to revision 183. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.182] - 2026-09-03

### Fixed
- Defined Map key-search state as fixed control state without snapshotting
  mutable key objects.
- Defined visibility of mutations performed during equality callbacks to later
  candidate comparisons.
- Prohibited implicit stored-key hash recomputation, query-hash recomputation,
  lookup restart, candidate reordering, and semantic key snapshots.

### Changed
- Synchronized all revisioned specification documents to revision 182.
  Only `PROTOS_LANGUAGE_SPEC.md` and `PROTOS_RUNTIME_SEMANTICS.md` gain
  normative semantic content in this revision.

## [0.1.181] - 2026-09-03

### Fixed
- Defined `flush()` as a logical propagation frontier within one ordered output
  flow rather than leaving write/flush inclusion to native scheduling.
- Required writes ordered before a flush to belong to that flush frontier and
  prevented successful flush completion from omitting such accepted output.
- Defined genuinely concurrent cross-Actor write/flush requests as initially
  unordered, with routing/admission choosing a stable relative order.
- Required the chosen order to determine whether the competing write is covered
  by the flush or is later output outside that frontier.
- Kept the frontier local to the logical output flow without introducing a
  global Actor memory-ordering or synchronization primitive.

### Changed
- Synchronized all revisioned specification documents to revision 181. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.180] - 2026-09-03

### Fixed
- Defined an atomic append-placement boundary for standard append writes
  selecting the same underlying filesystem resource.
- Allowed nondeterministic ordering of genuinely concurrent append writes while
  prohibiting overlap or byte-level interleaving of their contributed
  sequences.
- Defined partial failed append behavior so only the contributed prefix affects
  file end; an implementation may not reserve an entire requested sequence and
  thereby create an observable semantic hole.
- Required backends to provide or emulate the standard append-placement
  invariant, or reject standard append mode for resources where they cannot.
- Kept the rule local to append placement without creating a general ordering
  domain between independently opened File capabilities.

### Changed
- Synchronized all revisioned specification documents to revision 180. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.179] - 2026-09-03

### Fixed
- Defined the semantic boundary between separately opened File capabilities
  that alias the same underlying filesystem resource.
- Preserved independent Protos ordering and lifecycle domains for each File even
  when the host identifies their underlying resource as the same inode, file
  object, or equivalent identity.
- Explicitly declined to promise portable cross-File ordering, atomicity,
  visibility latency, or byte-level non-interleaving unless a stronger concrete
  protocol provides such guarantees.
- Prevented host-reported resource identity from silently becoming a global
  Protos ordering primitive.
- Clarified that cross-capability observations remain governed by the
  underlying Filesystem resource semantics while each File's own Protos
  contract remains normative.

### Changed
- Synchronized all revisioned specification documents to revision 179. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.178] - 2026-09-03

### Fixed
- Defined a receiver-visible `close()` lifecycle cutover for operations that
  require the resource to remain open.
- Closed the ambiguity between concurrent I/O operations and `close()` when
  different Actors access the same logical receiver through Actor-safe proxies.
- Required a stable routing/admission choice for genuinely concurrent operation
  versus close, preventing host/native scheduling from retroactively moving an
  operation across the close cutover.
- Preserved the existing distinction between close-induced operation failure and
  operation cancellation, and preserved already-committed effects.
- Made the lifecycle cutover a property of the logical receiver rather than
  proxy object identity.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 178.

## [0.1.177] - 2026-09-03

### Fixed
- Defined successful `File` open as a binding to the concrete filesystem
  resource selected by that open operation rather than to a continuing Path
  lookup.
- Required namespace changes after open, including rename, removal,
  replacement, relinking, or equivalent directory-entry changes, not to
  retarget an already-open standard `File`.
- Distinguished separate successful opens as separate File capabilities with
  independent lifecycle, cursor, buffering, shutdown, and logical ordering
  state unless a stronger protocol explicitly couples them.
- Allowed separately opened Files to select the same underlying resource through
  filesystem aliases without merging their Protos capabilities or inventing a
  global ordering domain.
- Kept resource identity semantic and opaque: no portable inode, file ID,
  descriptor, handle, or other host identity token is introduced.
- Required a backend that cannot preserve or emulate stable resource binding
  not to expose the weaker host handle as a standard `File` capability.

### Changed
- Synchronized all revisioned specification documents to revision 177. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.176] - 2026-09-03

### Fixed
- Defined the standard equality/hash behavior of `Map` and `IdentityMap`
  explicitly as the ordinary identity-based `Object` defaults rather than
  leaving structural collection equality/hashing to implementations.
- Made distinct Maps unequal under standard `==` even when their current
  associations are structurally alike, and made standard Map `hash()` use
  `identityHashOf` without traversing entries.
- Prevented ordinary Map mutation, insertion order, recorded hashes, cycles,
  or key/value callbacks from affecting the default Map equality/hash class.
- Preserved ordinary user overrides while keeping `===`, `identityHashOf`,
  and `IdentityMap` key matching primitive and unaffected.
- Avoided hidden deep-comparison policy, recursive traversal, snapshots,
  cycle detectors, or collection locks in the default protocol.

### Changed
- Synchronized all revisioned specification documents to revision 176.
  No grammar, Actor/Future, or I/O semantics change.

## [0.1.175] - 2026-09-03

### Fixed
- Defined Actor termination as a cancellation-request boundary for pending
  Actor-originated asynchronous operations represented by non-task-backed
  Futures, including applicable I/O and communication operations.
- Prevented dead Actors from leaving uncommitted producer operations running
  solely because those Futures had no producing `Task`.
- Preserved producer-specific commitment semantics: Actor termination cannot
  roll back committed I/O effects, unsend accepted messages, or invent stronger
  cancellation than the operation already supports.
- Kept Actor termination liveness bounded to task cleanup: termination requests
  producer cancellation but does not generally wait for every producer Future
  to become terminal.
- Clarified that residual committed/cancellation backend work remains under
  runtime/producer custody and cannot resurrect or execute ordinary Protos code
  in the terminated Actor.
- Clarified that Actor termination is not implicit I/O close/flush/sync/shutdown
  and does not revoke Process-local shared stream capabilities.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 175.

## [0.1.174] - 2026-09-03

### Fixed
- Defined a receiver-visible `shutdownWrite()` cutover for one logical output
  direction rather than leaving concurrent write/shutdown outcomes to native
  scheduling.
- Preserved Protos-defined ordering: a write ordered before shutdown remains an
  accepted preceding operation and shutdown waits behind it.
- Required a write ordered after the write-shutdown cutover to fail with zero
  byte contribution rather than racing a later backend/native write.
- Defined genuinely concurrent cross-Actor write/shutdown requests as initially
  unordered, with routing/admission choosing one stable relative order.
- Required that stable choice to decide whether the competing write is accepted
  before shutdown or rejected after the cutover.
- Shared the cutover across Actor-local proxies for the same output direction
  without imposing a global Actor scheduler order or one native syscall at a
  time.

### Changed
- Synchronized all revisioned specification documents to revision 174. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.173] - 2026-09-03

### Fixed
- Defined Protos Process termination as an I/O resource-custody and authority
  boundary independently of operating-system process exit.
- Required termination to revoke/detach the terminating Process's I/O
  capabilities and Actor-safe proxies even in a managed runtime that keeps the
  hosting OS process alive.
- Prevented termination from implying successful `close`, `flush`, `sync`, or
  half-close operations, synthesized lifecycle Futures, arbitrary Protos cleanup
  callbacks, or guaranteed buffered-output/durability completion.
- Required residual resources held solely for the terminating Process to
  transfer to implementation/host cleanup custody without relying on GC or
  execution of code in the dead Process.
- Preserved already committed I/O effects and prevented termination from
  inventing rollback semantics for pending/committed operations.
- Distinguished Process-local authority revocation from destruction of backend
  resources that are independently shared with other Processes or principals.
- Kept exact post-transfer physical cleanup timing/backend consequences
  host-dependent while requiring identical Protos-level authority revocation in
  standalone and managed runtimes.

### Changed
- Synchronized all revisioned specification documents to revision 173. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.172] - 2026-09-03

### Fixed
- Clarified that `request()` reply formation performs Actor-boundary value
  transfer before resolving the caller's request Future.
- Closed the ambiguity between generic local Future flattening and the rule that
  `Future` values are non-transferable across Actors.
- Defined a handler-returned Future as a non-transferable reply value that fails
  the request Future with `NonTransferableValue`.
- Prohibited cross-Actor Future adoption edges created implicitly by request/reply
  machinery.
- Preserved explicit suspension: a handler that wants to reply with an eventual
  local Future result must explicitly observe it and return the resulting
  transferable value.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 172.

## [0.1.171] - 2026-09-03

### Fixed
- Defined `Map.each(block)` and `IdentityMap.each(block)` as standard
  insertion-order iteration over a shallow logical association snapshot
  captured at invocation start.
- Required callbacks to receive the representative key and snapshot value
  as two arguments and made successful `each` return the receiver Map.
- Made later insertion, removal, and mapped-value replacement unable to
  alter an already-established iteration, including across task suspension.
- Avoided hidden Map-wide iteration locks and mutation prohibitions, so
  other Actor-local tasks remain free to mutate the Map while an iterator
  task is suspended under ordinary Actor semantics.
- Kept the snapshot shallow and implementation-independent; persistent,
  versioned, copy-on-write, or equivalent representations may avoid eager
  physical copying while preserving the same observable iteration.

### Changed
- Synchronized all revisioned specification documents to revision 171.
  No grammar, Future, or I/O semantics change.

## [0.1.170] - 2026-09-03

### Fixed
- Defined the standardized `Environment` snapshot as a single-valued mapping
  under the represented environment's native name-identity rules.
- Required `process.environment()` to fail when the native bootstrap environment
  contains duplicate-equivalent names rather than selecting an
  implementation-dependent first/last winner.
- Made duplicate detection use native name identity, preserving POSIX-like
  case-sensitive distinctions and rejecting Windows-like case-insensitive
  duplicates.
- Rejected duplicates even when their values are equal, because callback count,
  retained spelling, and native-boundary behavior would still be ambiguous.
- Prevented host enumeration order, case-preserving spelling, or runtime
  deduplication policy from becoming accidental portable Protos semantics.
- Kept raw/ambiguous host environment representations available only through a
  separately defined host-specific/native boundary.

### Changed
- Synchronized all revisioned specification documents to revision 170. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.169] - 2026-09-03

### Fixed
- Corrected Future waiter bookkeeping so a waiter denotes the suspended execution
  continuation rather than assuming every Future observer is a `Task`.
- Preserved `Future.value()` suspension for ordinary Actor/root/bootstrap
  execution contexts without manufacturing hidden task/Future identities.
- Kept cooperative cancellation associated only with task-backed waiting
  executions while retaining the existing cancellation-runnable semantics.
- Preserved the atomic waiter-registration/terminal-transition race rule for both
  task-backed and non-task-backed Future observers.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 169.

## [0.1.168] - 2026-09-03

### Fixed
- Defined `remove(key)` as the standard keyed-removal selector for `Map`
  and `IdentityMap`.
- Required successful removal to return the exact previously stored value.
- Required a successful no-match search to signal an Error rather than
  returning `null`, `false`, or an implementation sentinel.
- Composed removal with the existing deterministic key search,
  open/closed/frozen failure timing, and post-callback state revalidation.
- Preserved `containsKey(key)` as the non-failing presence query and kept
  `NOT_FOUND` strictly internal to runtime control flow.

### Changed
- Synchronized all revisioned specification documents to revision 168.
  No grammar, Actor/Future, or I/O semantics change.

## [0.1.167] - 2026-09-03

### Fixed
- Defined each available stdin/stdout/stderr binding as one Process-local
  logical byte stream for the Process lifetime.
- Required repeated standard-stream accessor calls to preserve that same logical
  input sequence or output-ordering domain rather than opening independent
  semantic streams.
- Kept physical capability-object identity non-normative: implementations may
  return the same object or distinct views/Actor-local proxies.
- Required duplicated native descriptors, rematerialized proxies, or internal
  backend replacement to preserve the binding's logical ordering, state, and
  backpressure semantics.
- Kept stdout and stderr as distinct Protos logical flows even if a host happens
  to route them to the same backend destination.
- Defined repeated standard-stream Encoding accessor results by equivalent
  descriptor semantics rather than object identity.
- Aligned the multi-Actor stdin rule with repeated accessor results so every
  capability for the binding shares one ByteReadable consumption domain.

### Changed
- Synchronized all revisioned specification documents to revision 167. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.166] - 2026-09-03

### Fixed
- Closed the lost-wakeup race between observing a pending Future and registering
  the current task as a waiter.
- Defined Future waiter registration and the first terminal transition as one
  semantic race: either completion wins and no suspension persists, or waiter
  registration wins and completion wakes that waiter.
- Required the explicit-suspension cancellation boundary to run before installing
  a live Future waiter.
- Allowed locks, CAS, generation counters, register-then-recheck, or equivalent
  implementation strategies while forbidding observable lost notifications.
- Preserved stable Future observation by re-entering the ordinary Future-state
  switch after a wait or a completion-won race.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 166.

## [0.1.165] - 2026-09-03

### Fixed
- Closed a second-order Map object-state hole caused by callback-capable
  key search: an initial open/not-frozen check no longer authorizes a later
  keyed-entry mutation after `hash` or `==` changes the Map state.
- Required `atPut` to revalidate state immediately before replacing a
  matched value; a callback-induced `freeze()` therefore prevents the
  replacement while callback-induced `close()` still permits replacement.
- Kept the existing post-search insertion check authoritative, so a
  callback-induced `close()` or `freeze()` prevents a no-match insertion.
- Required keyed-entry removal to check permission both before search and
  again immediately before removing a matched entry.
- Preserved completed callback effects, existing Map comparison guards, and
  callback-free IdentityMap optimization freedom without adding locks,
  snapshots, or transactions.

### Changed
- Synchronized all revisioned specification documents to revision 165.
  Updated 3 canonical runtime entry-update path(s). No grammar,
  Actor/Future, or I/O semantics change.

## [0.1.164] - 2026-09-03

### Fixed
- Defined the observable runtime contract of `wakeWaiters(future)`.
- Required all still-registered waiters of a Future's first terminal transition
  to become eligible for resumption.
- Required waiter registrations to be cleared or made inert after terminal
  transition so terminal Futures do not retain suspended-task continuations
  indefinitely.
- Prevented duplicate semantic resumption from repeated terminal wake-up
  bookkeeping.
- Clarified that wake-up makes a waiter runnable but does not bypass cancellation
  observation or weak-fairness scheduling.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 164.

## [0.1.163] - 2026-09-03

### Fixed
- Generalized finite speculative read-ahead retention from buffering/text
  adapters to every standard `ByteReadable` implementation.
- Required Protos-managed unread bytes/native-read results retained solely by
  implementation-chosen read-ahead to have an effective finite per-flow bound.
- Clarified that `maxBytes` bounds the successful Protos result but need not
  equal the size of one native/backend read, preserving bounded prefetch,
  batching, and shared-buffer implementations.
- Prevented repeated small reads from an open-ended source from authorizing
  unbounded implementation-controlled unread retention.
- Distinguished underlying ByteReadable speculation from memory intrinsically
  required by higher-level operations such as unbounded `readLine()`.
- Kept host-kernel, filesystem-cache, device, peer, and independently managed
  backend buffering outside the Protos-managed retention guarantee.
- Recast the existing TextReader/BufferedReader rule as a specialization of the
  general ByteReadable invariant rather than a separate resource model.

### Changed
- Synchronized all revisioned specification documents to revision 163. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.162] - 2026-09-03

### Fixed
- Removed stale `ByteReadable` cancellation/failure wording that still claimed
  all pending reads were globally ordered by invocation.
- Made preservation after cancellation/failure follow the same input-consumption
  order already defined for the receiver: Protos invocation order where one
  exists, otherwise the stable routing/admission order chosen for genuinely
  concurrent cross-Actor reads.
- Prevented section 5.1 from contradicting the cross-Actor ordering semantics
  introduced for shared ByteReadable receivers.
- Preserved the existing no-bypass rule for bytes retained after a failed or
  cancelled earlier read.

### Changed
- Synchronized all revisioned specification documents to revision 162. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.161] - 2026-09-03

### Fixed
- Defined how standard `Map` and `IdentityMap` keyed-entry mutation composes
  with the existing open/closed/frozen object-state model.
- Made closed Maps permit replacement of existing mapped values while
  rejecting entry insertion and removal; frozen Maps reject every keyed-entry
  mutation.
- Defined deterministic failure timing: frozen `atPut` fails before key
  search, closed `atPut` searches because update may succeed, and closed/frozen
  keyed removal fails before search.
- Kept `close()`/`freeze()` shallow: keys and values are not recursively
  closed or frozen, and read-only lookup/presence operations remain valid.
- Preserved ordinary user-defined indexed protocols; these state rules are
  the standard Map/IdentityMap contract rather than syntax-wide magic.

### Changed
- Synchronized all revisioned specification documents to revision 161.
  No grammar, Actor/Future, or I/O semantics change.

## [0.1.160] - 2026-09-03

### Fixed
- Defined one logical input-consumption ordering domain for outstanding
  `ByteReadable.read` operations on the same logical receiver.
- Preserved reads issued sequentially by one Actor in that Actor's invocation
  order even while earlier read Futures remain pending.
- Defined genuinely concurrent cross-Actor reads as having no predetermined
  relative order, while requiring routing/admission to establish one stable
  order once chosen.
- Prevented a later chosen read from bypassing an earlier chosen pending read to
  consume earlier bytes merely because a host/native operation completes first.
- Applied the same rule explicitly to standard input delegated through distinct
  Actor-local proxies.
- Kept native syscall overlap, buffering, prefetch, and routing as implementation
  freedoms when they cannot change logical byte/EOF/failure assignment.

### Changed
- Synchronized all revisioned specification documents to revision 160. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.159] - 2026-09-03

### Fixed
- Defined cancellation of a not-yet-started asynchronous task to make that task
  cancellation-runnable even when an unmet semantic prerequisite was preventing
  its first turn.
- Closed the `Future.then()` liveness hole where cancelling a continuation could
  otherwise remain pending forever while its source Future never completed.
- Required a cancelled pre-start `then()` continuation to reach the existing
  first-execution cancellation boundary without inspecting or modifying its
  still-pending source Future.
- Generalized cancellation readiness through an idempotent conceptual scheduler
  operation that also covers already-suspended tasks without duplicate semantic
  execution.
- Preserved cooperative cancellation: currently executing non-suspending Protos
  code is not preempted and acquires no hidden cancellation boundary.
- Preserved downstream-only cancellation: making a task cancellation-runnable
  does not satisfy or cancel its unrelated prerequisite or upstream Future.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 159.

## [0.1.158] - 2026-09-03

### Fixed
- Bounded implementation-controlled speculative read-ahead retention for
  standard buffering/decoding readers.
- Required unread bytes, decoded text, checkpoints, and equivalent state retained
  solely because of speculative read-ahead to have an effective finite
  implementation bound per logical reader/flow.
- Distinguished speculative retention from intrinsically unbounded operation
  results: ordinary `readLine()` may still require unbounded line accumulation.
- Prevented `readLine(maxBytes)` from using its explicit safety bound while
  continuing to consume/retain an unbounded suffix after the limit condition can
  already be established.
- Kept `readText()` chunk boundaries implementation-selectable while prohibiting
  an ever-growing implementation-chosen chunk as a prerequisite for progress.
- Applied the same principle to `BufferedReader` and other standard read
  adapters without prescribing one fixed buffer size or one native read at a
  time.
- Scoped the resource rule to Protos-managed retention rather than kernel,
  filesystem-cache, remote-peer, or independently managed backend buffering.

### Changed
- Synchronized all revisioned specification documents to revision 158. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.157] - 2026-09-03

### Fixed
- Replaced the ambiguous `shutdownRead()` "wins the ordering race" wording with
  one receiver-visible read-shutdown cutover point.
- Required read results committed before cutover to survive and every accepted
  but still-uncommitted read at cutover to complete as local EOF (`null`).
- Clarified that read shutdown intentionally terminates earlier pending,
  uncommitted reads rather than waiting behind them like write shutdown.
- Prevented host/native callback scheduling from deciding whether a read belongs
  before or after the shutdown boundary.
- Preserved the absence of a global cross-Actor arrival order: routing/admission
  may determine which competing operation reaches the receiver first, but the
  established cutover is stable.
- Allowed bytes obtained internally before result commitment to be discarded by
  shutdown without later escaping through the terminated receiver.

### Changed
- Synchronized all revisioned specification documents to revision 157. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.156] - 2026-09-03

### Fixed
- Unified Core Future cancellation-request recording through one conceptual
  runtime operation used by both `Future.cancel()` and structured cancellation.
- Aligned executable pseudocode with the normative rule that cancelling an
  already-suspended task makes that task runnable for cancellation.
- Prevented repeated cancellation requests from scheduling duplicate semantic
  cancellation events.
- Removed the undefined edge-trigger-like `onCancellationRequest` dependency
  from Future adoption.
- Defined pending Future adoption to observe a cancellation request whether the
  request is recorded before or after adoption installs its dependency, avoiding
  a lost-cancellation race.
- Preserved downstream-only adoption cancellation: cancelling the destination
  never cancels the adopted source.
- Preserved producer-specific cancellation for non-task Futures such as I/O;
  recording a request does not itself invent a cancelled outcome where the
  producer's commitment contract does not permit one.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 156.

## [0.1.155] - 2026-09-03

### Fixed
- Defined missing-key lookup for standard `Map` and `IdentityMap`: `at(key)`
  signals an Error after a successful no-match search rather than returning
  `null`, `false`, or an implementation sentinel.
- Defined `containsKey(key)` as the non-failing presence query returning
  canonical `true`/`false` independently of the stored value.
- Made mappings to `null`, `false`, and every other ordinary Protos object
  observably distinct from key absence.
- Kept pseudocode `NOT_FOUND` strictly internal and preserved existing
  normal-Map hash/equality effects and IdentityMap callback-free search.

### Changed
- Synchronized all revisioned specification documents to revision 155.
  No grammar, Actor/Future, or I/O semantics change.

## [0.1.154] - 2026-09-03

### Fixed
- Defined deterministic failure precedence for explicit owning-wrapper close.
- Made wrapper finalization/propagation failure the primary wrapper-close failure
  when it occurs before the mandated owned-target close step.
- Required the owned target's close lifecycle to still be invoked and committed
  before exposing that wrapper-finalization failure.
- Prevented a later owned-target close failure from replacing an already
  established wrapper-finalization failure.
- Defined target-close failure as the wrapper-close failure when wrapper
  finalization succeeds.
- Avoided introducing a universal aggregate/suppressed-error institution;
  secondary cleanup failures may be retained only through separately specified
  diagnostic facilities.
- Allowed the wrapper close Future to report its established primary failure
  after owned-target close has been committed, without waiting solely for that
  target close lifecycle to become terminal.
- Kept release custody with the target's ordinary `Closable` lifecycle and
  prohibited skipping owned-target close for fail-fast behavior.

### Changed
- Synchronized all revisioned specification documents to revision 154. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.153] - 2026-09-03

### Fixed
- Defined successful standard `Map.atPut(key, value)` and
  `IdentityMap.atPut(key, value)` to return the exact supplied `value`
  regardless of whether the operation inserts or replaces an entry.
- Removed the normal-Map pseudocode's observable previous-value/`ABSENT`
  return split, which relied on an undefined non-language absence sentinel
  and differed from `IdentityMap`.
- Kept bracket assignment independently defined to evaluate to the assigned
  value and left user-defined `atPut` direct-return contracts unconstrained
  unless another normative protocol specifies them.

### Changed
- Synchronized all revisioned specification documents to revision 153.
  No grammar, Actor/Future, or I/O semantics change.

## [0.1.152] - 2026-09-03

### Fixed
- Aligned executable `honorCancellation` pseudocode with the already normative
  cancellation-safe `ensure` semantics.
- Made a cleanup error during cancellation fail the task Future instead of being
  overwritten by an unconditional `CANCELLED` completion.
- Preserved `CANCELLED` as the terminal outcome only when the cancellation unwind
  and every applicable cleanup scope complete without a superseding error.
- Reused the existing `ErrorTransfer` and `failFuture` machinery rather than
  introducing another cancellation-specific outcome path.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 152.

## [0.1.151] - 2026-09-03

### Fixed
- Defined object reachability as separate from I/O lifecycle: becoming
  unreachable or GC-eligible does not semantically invoke `close()`.
- Prohibited programs from relying on GC, reference-count transitions, heap
  pressure, safepoints, or finalizer timing for deterministic resource release.
- Required deterministic release effects to come from explicit lifecycle
  mechanisms such as `close()` or a separately normative structured-cleanup
  facility.
- Allowed best-effort reclamation of unreachable native/backend resources only
  as implementation/host cleanup, not as a second Protos close lifecycle.
- Prohibited such cleanup from executing arbitrary Protos user code, fabricating
  close outcomes, or surfacing close errors to unrelated code.
- Made externally visible timing of emergency unreachable-resource reclamation
  explicitly non-portable, so lock/descriptor/socket release cannot be used as
  synchronization without explicit close.
- Clarified that owning wrappers do not close owned targets merely because the
  wrapper becomes unreachable.
- Kept resource-exhaustion behavior from abandoned resources outside portable
  reclamation-timing guarantees.

### Changed
- Synchronized all revisioned specification documents to revision 151. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.150] - 2026-09-03

### Fixed
- Defined cancellation of an already-suspended task to make that task runnable
  for cancellation without waiting for its original suspension condition.
- Prevented structured cancellation from becoming permanently stuck solely
  because a child is suspended on a Future, timer, I/O operation, or other
  condition that never becomes ready.
- Defined suspended-consumer cancellation as downstream-only: waking/cancelling
  the waiter does not implicitly cancel or otherwise modify the awaited Future or
  its producer.
- Defined the resume race through the existing portable resume boundary:
  cancellation pending at that boundary is honored before a successful suspended
  result can be delivered to consumer code.
- Clarified that awaited Future completion remains stable and cannot re-enter or
  rewrite a consumer task that has cancelled.
- Permitted eager waiter removal or inert bookkeeping while forbidding unbounded
  retention of dead waiters.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 150.

## [0.1.149] - 2026-09-03

### Fixed
- Made `Environment.each(block)` prevalidate complete portable `(String, String)`
  representability before invoking user code.
- Required an invalid native environment name/value to fail an enumeration with
  zero callback invocations instead of exposing a host-order-dependent prefix of
  otherwise valid entries.
- Preserved unspecified iteration order for fully representable environments.
- Scoped failure atomicity to Environment-to-String representation validation;
  errors/non-local effects raised by the user block retain ordinary callback
  semantics and do not roll back prior callbacks.
- Kept the rule representation-neutral: eager snapshot validation, cached
  validation, retained native entries, or first-enumeration validation remain
  valid implementations.
- Preserved the separate host/native boundary for lossless access to environment
  entries that portable Protos String cannot represent.

### Changed
- Synchronized all revisioned specification documents to revision 149. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.148] - 2026-09-03

### Fixed
- Removed stale host-process wording that contradicted the newly defined
  Protos-execution scope of observable standard hashes.
- Defined `identityHashOf` for Core value-identity categories on the same
  Protos-execution semantic scope, preventing Actor/process/worker/machine
  placement from changing the identity hash of the same semantic value.
- Preserved local identity-hash allocation/caching for identity-bearing
  objects and clarified that pass-by-value copies with new identity need not
  preserve the source object's identity hash.
- Avoided any requirement for a global mutable identity-hash registry or
  global lock; immutable execution-scoped configuration remains sufficient
  for value-identity hashing.

### Changed
- Synchronized all revisioned specification documents to revision 148.
  No grammar, Actor/Future, or I/O semantics change.

## [0.1.147] - 2026-09-03

### Fixed
- Replaced the vague concrete-receiver ordering sentence for `ByteSized` /
  `Truncatable` with one normative sequence-state ordering domain.
- Required reads, writes, append writes, position/seeks, size, truncate, and
  relevant flush/sync frontiers on one logical byte-sequence receiver to
  preserve every Protos-defined invocation order.
- Required `size()` after an earlier ordered write to observe the state produced
  by that write before the size query's evaluation, rather than bypassing a
  still-pending Future.
- Required later truncate/seek/size operations to evaluate against all earlier
  ordered sequence-state effects.
- Composed the shared ordering rule with each operation's existing
  cancellation/failure aftermath instead of inventing rollback.
- Defined genuinely concurrent Actor-routed sequence operations as unordered
  initially but stably ordered once the receiver/routing layer chooses.
- Preserved implementation freedom for pipelining, batching, speculative work,
  positional native APIs, and external independently authorized changes.

### Changed
- Synchronized all revisioned specification documents to revision 147. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.146] - 2026-09-03

### Fixed
- Replaced host-process-scoped hash wording with a Protos-execution-scoped
  observable hash domain.
- Required standard built-in value hashes to remain coherent across Actors,
  workers, operating-system processes, and machines participating in the same
  Protos execution rather than leaking host placement into language semantics.
- Renamed conceptual `processLocalHashInteger` to
  `executionLocalHashInteger` to match the semantic scope.
- Preserved per-execution salting while allowing additional per-Map, per-Actor,
  per-worker, or per-process mixing for unobservable physical table layout.
- Avoided requiring a global mutable hash registry or global lock; immutable
  execution-scoped configuration or equivalent mechanisms remain sufficient.

### Changed
- Synchronized all revisioned specification documents to revision 146.
  No grammar, Actor/Future, or I/O semantics change.

## [0.1.145] - 2026-09-03

### Fixed
- Put `readText`, `readLine`, and `readLine(maxBytes)` on one logical
  TextReader decoded-input operation-ordering domain.
- Required sequentially ordered text reads to consume/evaluate input in their
  Protos invocation order even when multiple Futures are simultaneously pending.
- Defined genuinely concurrent Actor-routed text reads as having no predetermined
  relative order, while requiring the chosen order to become stable.
- Prevented mixed readText/readLine operations from racing independent decoder,
  buffering, or line-framing states.
- Composed ordering with existing cancellation semantics: a cancelled earlier
  operation consumes zero text and the next operation receives the same earliest
  logical input.
- Composed ordering with permanent TextReader failure: later outstanding reads
  cannot bypass an earlier committed decoding/I/O/line-too-long failure.
- Preserved implementation freedom for speculative read-ahead, buffering,
  decoding, and pipelining when observable result assignment remains identical.

### Changed
- Synchronized all revisioned specification documents to revision 145. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.144] - 2026-09-03

### Fixed
- Defined `Future.detach()` as an idempotent ownership operation that always
  returns the same Future object.
- Defined successful detachment to remove only the structured activation-owner
  edge of a still-pending task-backed Future.
- Defined repeated `detach()` calls as state-preserving no-ops.
- Defined `detach()` on non-task-backed Futures, including direct I/O Futures, as
  a no-op because no structured task ownership edge exists.
- Defined `detach()` on already terminal Futures as a no-op.
- Prevented detachment from implicitly cancelling, re-parenting, abandoning, or
  otherwise altering a non-task producer or Future terminal outcome.
- Preserved Actor-local lifetime: detachment still does not escape the Actor
  execution/lifecycle domain.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 144.

## [0.1.143] - 2026-09-03

### Fixed
- Removed the contradiction between the general File capability-shape rule and
  section 18.4's unconditional `ByteSeekable`/`ByteSized`/`Truncatable`
  requirements.
- Made read/write open mode guarantee only the corresponding `ByteReadable` /
  `ByteWritable` access capability plus `Closable`.
- Made `ByteSeekable`, `ByteSized`, `Truncatable`, and `Syncable` conditional on
  the backend being able to satisfy each protocol's normative semantics.
- Clarified that truncate-on-open support does not imply later `Truncatable`
  support on the returned File.
- Required a File's exposed capability set to remain stable for that File's
  lifetime rather than varying according to whether an individual operation
  happens to be usable at the moment.
- Prohibited advertising an optional protocol merely because the host has a
  similarly named primitive when its normative Protos contract cannot be met.

### Changed
- Synchronized all revisioned specification documents to revision 143. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.142] - 2026-09-03

### Fixed
- Defined receiver-domain validation for standard behavior whose semantics
  require membership in a built-in semantic value family.
- Made delegation to a Number, String, or other family value/prototype
  insufficient to grant that semantic-family membership.
- Required incompatible receivers to signal an Error after ordinary lookup
  selects the behavior, without ancestor fallback or implicit coercion.
- Kept user-defined overrides ordinary and allowed standard behavior to be
  generic only when its own normative contract explicitly says so.
- Applied the rule explicitly to standard Number-family hashing and
  family-specific arithmetic/comparison behavior while preserving
  optimization freedom when receiver membership is already proven.

### Changed
- Synchronized all revisioned specification documents to revision 142.
  No grammar, Actor/Future, or I/O semantics change.

## [0.1.141] - 2026-09-03

### Fixed
- Defined detachment as removal from activation-structured ownership only, not
  escape from the Actor execution/lifecycle domain.
- Defined that detached Actor-local tasks may outlive their creating activation
  but cannot outlive their Actor incarnation.
- Required semantic Actor termination, while runtime cleanup remains executable,
  to request cooperative cancellation of every pending Actor-local task,
  including detached tasks.
- Prevented detached tasks from being silently re-parented to RootActor, Process,
  replacement Actors, or another execution domain after Actor termination.
- Reused the existing cancellation/`ensure` rules for Actor-termination cleanup:
  successful cleanup yields `cancelled`, while cleanup failure yields `failed`.
- Clarified that Actor replacement inherits no task/Future from the terminated
  incarnation.
- Scoped the cleanup guarantee so catastrophic loss of the hosting execution
  substrate cannot imply impossible cleanup, while still forbidding task
  continuation in another domain.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 141.

## [0.1.140] - 2026-09-03

### Fixed
- Defined invocation-time capture of the complete semantic configuration for
  `filesystem.open`.
- Prevented later mutation of a mutable options builder/object from changing an
  already-invoked open's access, creation, truncation, append, or resulting File
  capability shape.
- Required option-combination validation to use the captured configuration.
- Kept option capture separate from I/O commitment so snapshotting configuration
  does not itself prevent cancellation before filesystem effects or File-result
  commitment.
- Made the rule representation-neutral and prohibited hidden caller-visible
  borrow/freeze requirements while an open Future is pending.

### Changed
- Synchronized all revisioned specification documents to revision 140. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.139] - 2026-09-03

### Fixed
- Defined append-mode logical-position aftermath for successful, failed, and
  cancelled writes using the existing ByteWritable contributed-prefix model.
- Required zero-contribution failed/cancelled appends to leave logical position
  unchanged even if a backend cursor was tentatively moved to EOF.
- Required any append that contributes bytes to leave logical position
  immediately after that operation's last contributed byte, including when the
  write Future later fails.
- Defined successful zero-length append as position-preserving.
- Prevented external-writer interleaving from being incorrectly modeled as
  `oldPosition + k` or `initialEOF + k`; later external changes do not
  retroactively alter an already established logical position.
- Kept backend-dependent external-writer atomicity outside the portable promise
  while making the Protos handle's own position deterministic.

### Changed
- Synchronized all revisioned specification documents to revision 139. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.138] - 2026-09-03

### Fixed
- Defined deterministic `IdentityMap` key search using primitive `identityHashOf`
  and `===`, independent of physical hash-table layout.
- Defined existing-key update to replace only the value while retaining the
  representative key and insertion position.
- Defined removal followed by reinsertion of the same semantic key as a new
  insertion at the end of observable insertion order.
- Applied the same identity-key search semantics to lookup, containment,
  removal, and indexed insertion/update while preserving implementation
  freedom for physical hashing and caching.
- Kept `IdentityMap` matching free of ordinary Protos callback dispatch and
  separate from normal `Map` equality-callback reentrancy semantics.

### Changed
- Synchronized all revisioned specification documents to revision 138.
  No grammar, Actor/Future, or I/O semantics change.

## [0.1.137] - 2026-09-03

### Fixed
- Made `GroupRef` explicitly transferable through ordinary Actor/Process
  pass-by-value message transfer.
- Defined transferred GroupRefs as preserving the same concrete Group target and
  the same effective communication capability/restrictions without authority
  amplification.
- Clarified that GroupRef transfer copies/transfers only the communication
  capability, never mutable membership, routing, controller, policy, or
  control-plane state.
- Preserved the existing distinction between GroupRef object identity and Group
  identity, allowing implementation-local reference representations.
- Defined that transferred GroupRefs do not extend Group lifetime and never
  retarget after Group termination or discovery-name rebinding.
- Aligned GroupRef with the complete transfer-graph capability rule already used
  by ActorRef.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 137.

## [0.1.136] - 2026-09-03

### Fixed
- Defined permanent release custody transfer when `close()` begins: a failed
  close never gives the program an open/retryable resource back through the
  same receiver.
- Made explicit that close failure does not portably prove whether a backend
  resource is still open or already released when the backend reports an
  uncertain release outcome.
- Prohibited blind retry of native close/release using an identifier that might
  already have been released and reused for an unrelated resource.
- Allowed a native retry only when backend state/contract establishes that the
  identifier still denotes the same resource and retry is safe.
- Kept residual backend release state and bookkeeping under implementation/host
  custody rather than requiring a later program `close()` call for safety.
- Clarified that stronger effects such as durability remain separate protocols
  such as `sync()` rather than being inferred from close success/failure.

### Changed
- Synchronized all revisioned specification documents to revision 136. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.135] - 2026-09-03

### Fixed
- Clarified that the pre-comparison `hash` phase of a Map search does not
  suspend an already-active same-Map comparison restriction established by
  an enclosing equality callback.
- Prevented nested same-Map lookups from using their `hash` callback to mutate
  the Map and bypass the reentrant-mutation rule.
- Preserved ordinary outermost behavior: when no enclosing comparison scope
  exists, query-key `hash` effects still complete before candidate traversal
  and may mutate the target Map according to ordinary semantics.

### Changed
- Synchronized all revisioned specification documents to revision 135.
  No grammar, Actor/Future, or I/O semantics change.

## [0.1.134] - 2026-09-03

### Fixed
- Removed stale normative wording that described `IdentityMap` in terms of the
  overridable `identityHash` message after semantic identity hashing had already
  been moved to the primitive `identityHashOf` operation.
- Made all `IdentityMap` machinery wording consistently refer to
  `identityHashOf` together with primitive `===`.
- Preserved the separate ordinary `identityHash()` convenience message for
  explicit source-level dispatch only; no observable semantics are changed from
  the already-defined primitive identity-hash boundary.

### Changed
- Synchronized all revisioned specification documents to revision 134.
  No grammar, Actor/Future, or I/O semantics change.

## [0.1.133] - 2026-09-03

### Fixed
- Defined `TextWriter.writeText` and `writeLine` as ordered logical text-write
  operations, with `writeLine` text plus LF forming one non-interleaved operation.
- Required complete encoding validation before any byte from a text-write
  operation becomes observable at the target.
- Made strict encoding failure failure-atomic: it contributes zero output bytes
  and preserves the pre-operation per-flow encoder state.
- Kept this guarantee representation-neutral: implementations may validate,
  checkpoint, dry-run, or stage encoding without requiring one native write or
  mandatory eager full-output allocation.
- Defined text-write cancellation to win only while both zero output contribution
  and the pre-operation encoder state can still be preserved.
- Reused the existing ByteWritable/wrapper unknown-progress rules for downstream
  failures after output commitment rather than inventing TextWriter-specific
  retry heuristics.

### Changed
- Synchronized all revisioned specification documents to revision 133. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.132] - 2026-09-03

### Fixed
- Defined `adoptFuture` and made Future flattening a complete normative
  outcome-adoption operation rather than undefined runtime pseudocode.
- Defined resolved, failed, and cancelled adopted-source propagation.
- Made adoption one-way: destination cancellation, ownership, and detachment do
  not propagate to the adopted source.
- Made pending Future adoption cancellation-aware so a destination can complete
  cancelled after its initiating task body has returned.
- Defined source-completion versus destination-cancellation races by the first
  terminal transition of the destination.
- Added conceptual `adoptedSource` bookkeeping without adding a fifth Future
  state or a language-visible slot.
- Defined direct and transitive adoption cycles to fail with the standard
  `FutureResolutionCycle` error instead of remaining indefinitely pending.
- Required adoption bookkeeping not to execute ordinary Protos code inline.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 132.

## [0.1.131] - 2026-09-03

### Fixed
- Defined reentrant mutation of a Map's keyed-entry state during that Map's
  user-defined key-equality callback to signal an Error before mutation.
- Scoped the restriction to the particular Map and comparison dynamic extent,
  preserving unrelated Map mutation and read-only same-Map operations.
- Defined query-key `hash` effects to complete before candidate traversal, so
  target-Map mutations performed by `hash` are observed deterministically by
  the subsequent search.
- Preserved mutable-key semantics while preventing live hash-table iterator,
  rehashing, or bucket-layout behavior from leaking into language semantics.
- Avoided requiring snapshots, global locks, Actor-wide locks, or permanent
  per-entry reentrancy metadata.

### Changed
- Synchronized all revisioned specification documents to revision 131.
  No grammar, Actor/Future, or I/O semantics change.

## [0.1.130] - 2026-09-03

### Fixed
- Extended TextReader cancellation semantics from line reads to all standard
  text-reading operations, including `readText()`.
- Required successful cancellation to consume zero logical text and preserve the
  remaining decoded sequence and line-framing state.
- Required internally fetched bytes, decoded characters, partial encoded
  characters, decoder/BOM state, and read-ahead to be retained/reconciled rather
  than becoming text loss, duplication, reordering, or a spurious later error.
- Clarified that zero-consumption applies at the TextReader logical-text layer;
  ordinary wrapper read-ahead does not imply rewinding a separately accessible
  wrapped byte source.
- Defined `readText()` result commitment consistently with its intentionally
  unspecified chunk boundaries, while keeping successful cancellation
  non-poisoning and committed I/O/decoding failures subject to the existing
  permanent TextReader failure lifecycle.

### Changed
- Synchronized all revisioned specification documents to revision 130. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.129] - 2026-09-03

### Fixed
- Added a portable cancellation-observation boundary before the first ordinary
  Protos instruction of every newly created asynchronous task.
- Defined cancellation requested before a `then()` continuation starts to cancel
  that continuation before inspecting the source result or invoking `transform`.
- Prevented scheduler timing from making a never-started cancelled continuation
  execute observable Protos side effects in one implementation but not another.
- Preserved the existing rule that, after first task execution begins, ordinary
  non-suspending code does not gain hidden cancellation checkpoints.
- Kept `then()` cancellation downstream-only: cancelling the destination still
  does not cancel or change ownership of the source Future.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 129.

## [0.1.128] - 2026-09-03

### Fixed
- Defined `seek`, `seekBy`, and `seekToEnd` as failure-atomic with respect to the
  receiver's logical sequence position.
- Required failed and successfully cancelled seeks to leave logical position
  unchanged even when a backend cursor moved tentatively.
- Defined `seekBy` and `seekToEnd` targets at the seek's ordered evaluation point
  so concurrent/earlier position or size effects cannot be interpreted
  differently by independent implementations.
- Defined seek commitment only when the complete logical position change is
  established; after commitment the seek succeeds rather than exposing the new
  position behind a failed Future.
- Required implementations to restore, virtualize, or reconcile weaker backend
  cursor behavior, or else not expose standard `ByteSeekable`.
- Clarified that a failed `position()` query has no position-changing effect.

### Changed
- Synchronized all revisioned specification documents to revision 128. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.127] - 2026-09-03

### Fixed
- Made the observable return value of `Future.cancel()` normative: it returns
  the same Future object.
- Defined repeated cancellation requests on a pending Future as idempotent.
- Defined `cancel()` on resolved, failed, or already-cancelled Futures as a
  state-preserving no-op that still returns the same Future.
- Clarified that a normal return from `cancel()` acknowledges request recording,
  not successful cancellation, a guaranteed `cancelled` terminal state, or
  reversal of already-committed effects.
- Removed the implementation-defined escape hatch that allowed the standard
  protocol to choose a different `cancel()` result.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 127.

## [0.1.126] - 2026-09-03

### Fixed
- Defined standard `Truncatable.truncate` as failure-atomic with respect to the
  sequence size and contents changed by that operation.
- Prohibited a failed truncate from leaving a partially truncated observable
  sequence state.
- Defined truncation commitment only when the complete requested size-reduction
  effect is established; after that point cancellation cannot undo it and the
  truncate operation completes successfully.
- Kept no-extension truncation requests as successful no-ops.
- Required backends unable to provide or emulate failure-atomic truncation not to
  expose standard `Truncatable` merely because the host offers a similarly named
  primitive.
- Kept independently authorized concurrent resource changes outside the
  truncate operation's own failure-atomicity guarantee.

### Changed
- Synchronized all revisioned specification documents to revision 126. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.125] - 2026-09-03

### Fixed
- Defined the existing no-starvation promise as weak fairness over continuously
  runnable eligible work.
- Defined when Actor-local continuations and accepted mailbox messages count as
  runnable for the fairness rule.
- Extended the same weak-fairness obligation to continuously runnable live
  Actors at scheduler-selection boundaries.
- Defined that later-arriving work cannot starve a continuously runnable item.
- Explicitly excluded equal-share, round-robin, bounded-latency, bounded-turn,
  and strong-fairness guarantees.
- Scoped the liveness guarantee to situations where execution continues to
  regain applicable scheduler control, preserving the existing non-preemptive
  semantics of a non-yielding Protos segment.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 125.

## [0.1.124] - 2026-09-03

### Fixed
- Closed the accidental networking-semantics gap around the standard `Socket`
  capability shape by defining it only as an already-provisioned connected
  byte-stream endpoint.
- Made socket creation, connect/bind/listen/accept, datagram addressing, DNS/name
  resolution, transport configuration, and network-authority acquisition
  explicitly outside the v0.1 I/O model.
- Prevented possession of one Socket from implying authority to create sibling
  sockets, reconnect elsewhere, resolve names, inspect ambient network state, or
  bypass Process-host policy.
- Required a future networking model to make authority and host-dependent
  namespace behavior explicit rather than inheriting BSD/POSIX, WinSock, Java,
  or ambient DNS behavior as Protos semantics.

### Changed
- Synchronized all revisioned specification documents to revision 124. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.123] - 2026-09-03

### Fixed
- Defined Number-family `hash` behavior by numeric semantic equality rather than
  by the identity-based `Object.hash` default.
- Required equal numeric values to have equal normal hashes across Integer,
  fixed-width Integer, and Float families.
- Required `0.0` and `-0.0` to share a normal hash while preserving their
  distinct semantic identities.
- Prevented Float NaN payload/representation details from leaking through
  standard hashing by assigning all Core NaNs one normal numeric hash class.
- Preserved per-execution hash salting, legal collisions, exact-Integer hashing,
  and separate `identityHashOf` semantics for `IdentityMap`.

### Changed
- Synchronized all revisioned specification documents to revision 123.
  No grammar, Actor/Future, or I/O semantics change.

## [0.1.122] - 2026-09-03

### Fixed
- Defined invocation of `shutdownWrite()` and `shutdownRead()` as the irreversible
  commitment boundary for permanent termination of the corresponding direction.
- Prevented Future cancellation or shutdown failure from reopening a direction
  after its shutdown lifecycle has begun.
- Defined deterministic interaction with pending reads: uncommitted reads lose
  the race to read shutdown as local EOF (`null`), while committed results remain
  unchanged.
- Defined post-failure and repeated half-close behavior as one idempotent
  lifecycle rather than implementation-dependent retries.
- Kept input and output half-close lifecycles independent unless a stronger
  concrete protocol explicitly couples them.

### Changed
- Synchronized all revisioned specification documents to revision 122. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.121] - 2026-09-03

### Fixed
- Defined an unhandled initialization error as a fatal failure of that Actor
  incarnation rather than an Actor that remains indefinitely non-READY.
- Defined the fate of messages accepted while an Actor is INITIALIZING.
- Prohibited dispatch, transfer, or transparent replay of those accepted
  operations after initialization failure.
- Reused the ordinary pre-acceptance and Messages Across Actor Failure rules
  rather than creating initialization-specific delivery semantics.
- Defined accepted `request()` operations lost to initialization failure to use
  the existing `RequestOutcomeUncertain` outcome.
- Clarified that a replacement Actor has fresh initialization/mailbox state and
  never inherits accepted messages from the failed incarnation.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 121.

## [0.1.120] - 2026-09-03

### Fixed
- Defined `Object.!=` as ordinary customizable behavior whose default dynamically
  invokes the receiver's current `==` behavior and returns its strict Boolean
  complement.
- Defined error and invalid-result propagation for the default `!=` behavior.
- Defined `!==` as the non-overridable primitive Boolean complement of semantic
  identity `===`, with no user-message dispatch.
- Preserved independent user override of ordinary `!=` without allowing it to
  redefine semantic identity or `IdentityMap`.

### Changed
- Synchronized all revisioned specification documents to revision 120.
  No grammar, Actor/Future, or I/O semantics change.

## [0.1.119] - 2026-09-03

### Fixed
- Replaced implementation-defined `TextReader` recovery after line-too-long,
  decoding, or underlying I/O failure with a deterministic failed text-reading
  lifecycle.
- Prohibited implicit draining/scanning to a later line boundary after an
  overlong line, avoiding hidden unbounded work and backend-dependent recovery.
- Required later `readText()`/`readLine()` calls on a failed TextReader to fail
  without consuming additional source input.
- Kept successful cancellation non-poisoning and preserved the separate
  cancellation/rebuffering contract.
- Preserved wrapper ownership boundaries: TextReader failure does not itself
  close or poison the wrapped byte source, and close/release remains available.

### Changed
- Synchronized all revisioned specification documents to revision 119. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.118] - 2026-09-03

### Fixed
- Defined `Object`'s default ordinary `==` behavior as semantic identity (`===`).
- Defined `Object`'s default ordinary `hash` behavior as `identityHashOf(this)`.
- Ensured ordinary objects are usable as normal Map keys without implicit
  structural equality or structural hashing.
- Kept `==`/`hash` overrideable as ordinary messages while preserving
  non-overridable `===`, `identityHashOf`, and `IdentityMap` semantics.
- Made custom equality/hash coherence remain the responsibility of the behavior
  that overrides the defaults.

### Changed
- Synchronized all revisioned specification documents to revision 118.
  No grammar, Actor/Future, or I/O semantics change.

## [0.1.117] - 2026-09-03

### Fixed
- Defined Actor pass-by-value transferability over the complete transitive value
  graph rather than only over top-level objects.
- Made local-slot references and immutable delegation-parent edges part of the
  transfer graph.
- Required graph-copy semantics to preserve cycles and aliasing without merging
  distinct source identities.
- Defined capability/shared-immutable boundary values as governed by their own
  cross-Actor semantics rather than by copying mutable referent state.
- Added standard `NonTransferableValue` error semantics.
- Made transfer validation atomic before concrete-Actor acceptance, preventing
  partial message delivery or source mutation on validation failure.
- Applied the same transferability rule to `request()` reply values.
- Corrected the stale wording `An request()` to `A request()` when present.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 117.

## [0.1.116] - 2026-09-03

### Fixed
- Defined semantic identity hashing as a non-overridable primitive operation
  `identityHashOf(value)`, matching the non-overridable nature of `===`.
- Required `IdentityMap` to use primitive identity hashing rather than an
  overridable `identityHash` message send.
- Allowed a standard ordinary `identityHash()` convenience message to delegate
  to the primitive while preserving normal user override behavior for explicit
  source-level sends only.
- Prevented user overrides, method lookup, or implementation dispatch choices
  from changing identity-keyed collection behavior.

### Changed
- Synchronized all revisioned specification documents to revision 116.
  No grammar, Actor/Future, or I/O semantics change.

## [0.1.115] - 2026-09-03

### Changed
- Renamed the Actor request/reply operation from `ask()` to `request()`.
- Renamed the standard uncertainty error from `AskOutcomeUncertain` to
  `RequestOutcomeUncertain`.
- Preserved all existing delivery, snapshot, ordering, cancellation, timeout,
  reply, Actor-failure, and uncertainty semantics; this is an API vocabulary
  change, not a behavior change.
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 115.

### Rationale
- `ask` is established actor-framework jargon, especially in Akka, but is not
  self-explanatory outside that ecosystem.
- `call` commonly implies synchronous waiting in Erlang/Elixir and would obscure
  Protos's explicit Future-returning semantics.
- `request` states the semantic role directly, pairs naturally with one-way
  `send`, and is already established for asynchronous request/reply actor APIs.

## [0.1.114] - 2026-09-03

### Fixed
- Defined the `hash` protocol result domain as semantic `Integer` rather than
  leaving Map consumers free to accept host integers, Floats, or arbitrary
  objects.
- Defined invalid hash-result handling before Map mutation and prohibited
  implicit truncation, masking, host-word-size coercion, or Float conversion.
- Defined logical Map recorded hashes as exact mathematical Integer values while
  preserving implementation-private bounded reductions for table layout.
- Defined `identityHash` to return semantic `Integer`, remain stable within one
  execution, and satisfy `a === b => identityHash(a) == identityHash(b)`.
- Kept collisions legal and ordinary hash/identityHash explicitly
  non-persistent across executions.

### Changed
- Synchronized all revisioned specification documents to revision 114.
  No grammar, Actor/Future, or I/O semantics change.

## [0.1.113] - 2026-09-03

### Fixed
- Defined how `ask()` maps communication uncertainty into its ordinary
  four-state Future without inventing a fifth Future state.
- Added the standard `AskOutcomeUncertain` error prototype for cases where no
  normal reply is available and remote effects cannot be ruled out.
- Distinguished definite pre-acceptance failure from uncertain or known
  post-acceptance loss of the reply.
- Defined cancellation before acceptance, cancellation after acceptance,
  cancellation/acceptance races, late replies, and reply/cancellation terminal
  races.
- Kept wait timeout separate from cancellation and from ask-Future completion.
- Defined Actor termination after accepted/possibly accepted asks as
  `AskOutcomeUncertain` rather than pretending non-delivery or transparently
  replaying the request.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 113.

## [0.1.112] - 2026-09-03

### Fixed
- Defined a portable filesystem-independent `Path` value model as rootedness plus
  an ordered component sequence.
- Separated normal names from parent traversal and prohibited lexical collapsing
  across parent components where backend indirection could change resolution.
- Defined Path equality structurally, independent of host case folding, Unicode
  normalization, native prefixes, symlinks, or resource identity.
- Made Path immutable/non-authoritative and safe to transfer under ordinary
  immutable-value Actor rules without transferring Filesystem authority.
- Required Filesystem backends to treat each portable normal component as one
  logical child name, rejecting unrepresentable names rather than reinterpreting
  them as native separators, roots, drives, devices, or authority-changing syntax.
- Kept public parsing/display/native conversion APIs outside v0.1 while forbidding
  them from silently embedding host-native identity rules into portable Path.

### Changed
- Synchronized all revisioned specification documents to revision 112. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.111] - 2026-09-03

### Fixed
- Defined `slotNames()` to return local slot names in canonical ascending
  lexicographic Unicode-scalar order rather than implementation-dependent slot
  table, object-shape, hash, or creation order.
- Defined the returned Array as a snapshot of the local slot-name set.
- Kept delegated slots excluded and made remove/recreate history irrelevant to
  reflection ordering.
- Preserved implementation freedom to keep any internal slot layout and sort
  only when reflection requires it.

### Changed
- Synchronized all revisioned specification documents to revision 111.
  No grammar, concurrency, Future, or I/O semantics change.

## [0.1.110] - 2026-09-03

### Fixed
- Defined the sender used by concrete-Actor FIFO ordering as the originating
  Actor incarnation rather than an Actor-local task, Future, activation, or turn.
- Defined same-sender ordering by Protos-visible communication invocation order
  within that originating Actor.
- Defined explicitly initiated retry attempts to take their ordering position
  when the retry is initiated rather than reclaiming an earlier queue position.
- Prevented host/runtime/bootstrap activity from accidentally acquiring an
  Actor sender identity and hidden ordering guarantees.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 110.

## [0.1.109] - 2026-09-03

### Fixed
- Defined the aftermath of a failed `sync()`: durability already reached is not
  rolled back, while the exact partially durable subset remains unexposed.
- Clarified that synchronization failure alone does not universally poison or
  close the receiver.
- Required a later successful `sync()` to cover its complete later frontier,
  including still-undurable changes that belonged to an earlier failed frontier.
- Defined sync retry as another durability request rather than replay of logical
  data changes, preserving optimization freedom for already-durable state.
- Kept stronger post-failure lifecycle rules explicit to concrete receiver
  contracts rather than inheriting host-specific error conventions.

### Changed
- Synchronized all revisioned specification documents to revision 109. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.108] - 2026-09-03

### Fixed
- Removed implementation-defined behavior after a normal `Map` key's `hash` or
  `==` behavior changes while the key is stored.
- Made an entry's insertion-time recorded hash stable until removal and prohibited
  automatic rehashing, relocation, representative-key replacement, or repair.
- Required all later searches to keep using the deterministic query-hash,
  recorded-hash, insertion-order equality algorithm even for unstable or
  contract-violating keys.
- Defined deterministic behavior for keys that become unreachable by themselves,
  entries that later become equal, and equality/hash-contract violations.
- Preserved mutable keys without hidden freezing or mutation tracking and kept
  optional diagnostics outside ordinary Core semantics.

### Changed
- Synchronized all revisioned specification documents to revision 108.
  No grammar, Actor, Future, or I/O semantics change.

## [0.1.107] - 2026-09-03

### Fixed
- Defined failed ordinary `ByteReadable.read` operations to consume zero bytes
  from the observable input sequence rather than permitting hidden partial
  consumption behind a failed Future.
- Required bytes already obtained before cancellation or failure to be
  preserved/rebuffered as the earliest unread bytes for later logical reads.
- Required shared logical read position to remain unchanged across failed or
  cancelled reads even when a native/backend cursor advanced internally.
- Prevented later outstanding reads from bypassing bytes an earlier failed read
  was required to preserve.
- Clarified that read failure alone does not universally close or poison the
  receiver; later operations follow the concrete receiver state.

### Changed
- Synchronized all revisioned specification documents to revision 107. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.106] - 2026-09-03

### Fixed
- Made the Runtime Future/Task conceptual model internally consistent with the
  already-normative structured-concurrency and cancellation semantics.
- Linked every task-backed Future to its producing Task and vice versa.
- Registered ordinary `closure.future()` work with its creating activation,
  matching the existing structured-ownership rule.
- Made `future.cancel()` and structured child cancellation feed the same
  Future-owned cooperative cancellation request observed at portable task
  cancellation boundaries.
- Linked `then()` continuation Tasks back to their destination Futures.
- Clarified that non-task Future producers such as I/O operations may use the
  same Future cancellation-request state under their own domain contracts.
- Made cancellation requests on already-terminal Futures semantically inert.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 106.

## [0.1.105] - 2026-09-03

### Fixed
- Defined cooperative cancellation as an `ensure`-triggering unwind.
- Prevented an already-honored cancellation request from re-triggering at
  suspension boundaries inside cleanup for that same unwind.
- Allowed asynchronous `ensure` cleanup to suspend while releasing resources.
- Defined cleanup-error precedence over cancellation: cleanup failure replaces
  cancellation and fails the task Future.
- Preserved structured cleanup completion before cancellation becomes terminal.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 105.

## [0.1.104] - 2026-09-03

### Fixed
- Defined the receiver direction of normal `Map` equality as
  `queryKey == storedKey`, eliminating implementation-dependent reversal or
  symmetrization of user-defined equality.
- Defined deterministic insertion-order comparison among same-hash candidate
  entries, so hash-table layout and probing strategy cannot change observable
  user `==` effects, errors, or the selected entry.
- Defined one query-key hash operation per key search/insertion semantic
  operation and conceptual per-entry recorded hashes while preserving
  implementation freedom for observationally equivalent layouts.
- Required equal-key updates to retain the original stored key object, recorded
  hash, and insertion position while replacing only the value.
- Required a mutating Map operation to leave the map unchanged when key hashing
  or comparison signals, without rolling back effects performed by user protocol
  code.
- Kept `IdentityMap` separate and explicitly rejected an implicit `===` shortcut
  in normal `Map` matching.

### Changed
- Synchronized all revisioned specification documents to revision 104.
  No grammar, Actor, Future, or I/O semantics change.

## [0.1.103] - 2026-09-03

### Fixed
- Defined failed-flush propagation as ordered progress through a prefix of the
  flush frontier rather than permitting holes or later-output reordering.
- Prohibited a later flush from duplicating output/effects already propagated by
  an earlier failed flush.
- Closed the generic buffering-adapter recovery ambiguity: when an underlying
  ordinary `ByteWritable.write` fails without revealing enough progress to know
  the exact remaining suffix, the wrapper output side becomes failed/unusable
  instead of guessing, replaying, or dropping bytes.
- Allowed recovery only through a stronger downstream protocol that exposes
  sufficient semantic progress, without depending on host-specific error lore.
- Preserved wrapper ownership rules: output failure does not implicitly close or
  transfer ownership of the wrapped target.

### Changed
- Synchronized all revisioned specification documents to revision 103. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.102] - 2026-09-03

### Fixed
- Defined the observable contribution of a failed `ByteWritable.write` as one
  contiguous prefix of the captured write sequence, including the possible
  zero-length and full-length-prefix cases.
- Made failed-write ordering explicit so later writes cannot precede, fill holes
  inside, or byte-interleave with the failed write's committed prefix.
- Clarified that ordinary `write` does not expose the committed prefix length,
  so failure never makes whole-write retry automatically safe.
- Defined shared-position advancement after partial failed writes and preserved
  committed prefixes for later ordered flush/sync/position-sensitive operations.
- Clarified that write failure alone does not implicitly close or universally
  poison a `ByteWritable`; later operations follow the concrete receiver state.

### Changed
- Synchronized all revisioned specification documents to revision 102. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.101] - 2026-09-03

### Fixed
- Defined `Error.signal()` as the portable zero-argument user signaling protocol.
- Required the signaled receiver itself to belong to the `Error` delegation
  hierarchy and to be the exact object matched and delivered to handlers.
- Made signaling non-returning at the original signaling point under Core's
  unwinding model, whether control transfers to a handler or reaches an outer
  execution boundary.
- Separated runtime semantic signaling from overridable source-level message
  dispatch, so user overrides cannot redefine normative runtime failures.
- Prohibited implicit String/prototype/arbitrary-object coercion into errors and
  language-visible mutation merely for signaling.
- Removed known literal-newline formatting artifacts left by the earlier
  error-taxonomy applier where present.

### Changed
- Synchronized all revisioned specification documents to revision 101.
  No syntax, reserved words, Actor scheduling, or I/O semantics change.

## [0.1.100] - 2026-09-03

### Fixed
- Made Filesystem authority confinement normative for complete path resolution,
  rather than relying only on lexical path interpretation.
- Required parent traversal, absolute forms, symlinks/reparse points, aliases,
  mounts/redirections, and other backend indirections not to escape the
  authorized Filesystem namespace.
- Required uncertain confinement under races or backend limitations to fail
  rather than fall back to ambient host authority.
- Kept virtual, mounted, remote, and mediated filesystems valid by defining the
  boundary in terms of capability authority rather than host-directory ancestry.

### Changed
- Synchronized all revisioned specification documents to revision 100. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.99] - 2026-09-03

### Fixed
- Removed the remaining normative references to implementation-defined Future
  cancellation "safe points" from the older Language and Runtime cancellation
  sections.
- Made those older sections explicitly refer to the portable cancellation
  boundaries already defined by the concurrency model.
- Prevented an implementation from treating VM/JIT/GC/allocation/call/loop
  checkpoints as additional observable cancellation points.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 99.

## [0.1.98] - 2026-09-03

### Fixed
- Defined deterministic `readLine(maxBytes)` precedence between line-length,
  decoding, I/O, EOF, and terminator conditions independently of buffering and
  read-ahead strategy.
- Required decoding validity of each next encoded character to be established
  before its source octets count as valid line content for the size bound.
- Defined CR as an immediate line terminator while still consuming a following LF
  as part of CRLF, without allowing later EOF/errors to retroactively fail the
  completed CR-terminated line.
- Required read-ahead across a completed line to preserve following bytes and
  deferred errors for subsequent reads.

### Changed
- Synchronized all revisioned specification documents to revision 98. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.97] - 2026-09-03

### Fixed
- Replaced implementation-defined Future cancellation safepoints with portable
  cancellation-observation boundaries.
- Made every explicit suspension point a mandatory cancellation boundary and
  required pending cancellation to be observed before suspended work resumes
  ordinary Protos execution.
- Defined that normatively cancellation-aware operations may observe cancellation
  while pending, subject to their commitment/effect rules.
- Excluded method calls, allocations, loop back-edges, VM/JIT polls, GC points,
  host calls, and other implementation machinery from becoming hidden observable
  cancellation points.
- Preserved cooperative cancellation: CPU-bound code with no explicit suspension or
  cancellation-aware operation may finish normally despite a pending request.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 97.

## [0.1.96] - 2026-09-03

### Fixed
- Defined the portable Core handler-installation API as the ordinary
  `matchPrototype.handle(body, handler)` protocol inherited through `Error`.
- Fixed dynamic extent, innermost-match ordering, unwinding, handler deactivation
  before handler execution, and the value returned after normal handling.
- Defined handler state as task-local: the same suspended task retains its active
  scopes, while distinct asynchronous tasks and Future continuations do not
  inherit them.
- Aligned Future failure consumption with the rule that `value()` re-signals in
  the consumer's current dynamic handler context.
- Removed formatting artifacts left by the earlier error-taxonomy applier where
  present.

### Changed
- Synchronized all revisioned specification documents to revision 96.
  No new syntax or reserved words are introduced.

## [0.1.95] - 2026-09-03

### Fixed
- Removed implementation-dependent rollback behavior after a committed
  `filesystem.open` later fails before returning a `File`.
- Required the failed open itself not to compensate by deleting a target it
  already created or restoring content it already truncated.
- Clarified that this does not freeze later filesystem state: independent
  Actors, external processes, or backend activity may still change the target
  under their own authority and ordering.

### Changed
- Synchronized all revisioned specification documents to revision 95. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.94] - 2026-09-03

### Fixed
- Defined ordering when distinct capability objects or Actor-local proxies denote
  one logical output flow, so proxy identity cannot accidentally create separate
  ordering domains.
- Preserved each Actor's invocation order while leaving concurrent cross-Actor
  write order nondeterministic until the shared flow admits/routes the writes.
- Required successful writes on one logical flow to contribute whole logical byte
  sequences without byte-level interleaving, while permitting partial native
  writes and other implementation strategies underneath.

### Changed
- Synchronized all revisioned specification documents to revision 94. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.93] - 2026-09-03

### Fixed
- Closed the Core error-taxonomy ambiguity that previously allowed
  implementations to expose different intermediate error prototypes while
  still satisfying the same failure rule.
- Defined `Error` as the mandatory standard root error prototype and made the
  default standard taxonomy shallow: normatively named standard error
  prototypes delegate directly to `Error` unless a normative specification
  explicitly defines another parent relation.
- Clarified that a failure specified only as "signals an error" guarantees only
  the `Error` category for portable handler matching, and that pseudocode
  constructor names do not automatically become standard-prelude bindings.
- Preserved ordinary user/library extensibility: programs may build arbitrary
  error-prototype hierarchies below `Error` using normal delegation.

### Changed
- Synchronized the normative document revisions to 93. No syntax,
  Future scheduling, Actor-transfer, or I/O operation semantics change in this
  revision.

## [0.1.92] - 2026-09-03

### Fixed
- Required finite end-to-end admission/backpressure for `ByteWritable` output so
  invocation-time snapshots cannot turn pending writes into an implementation-
  managed unbounded memory queue.
- Required pressure to propagate through Protos-managed adapters, Actor-safe
  proxies, routing layers, and backend buffering while preserving bounded
  pipelining and implementation freedom.
- Clarified that programs may still retain arbitrarily many Futures or arguments
  themselves; the bounded-retention guarantee applies to state retained by the
  I/O delivery path.

### Changed
- Synchronized all revisioned specification documents to revision 92. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.91] - 2026-09-03

### Fixed
- Defined `filesystem.open(path, options)` commitment and cancellation semantics.
  Cancellation may win only before any portable create/truncate effect and before
  the successful `File` result commits.
- Made create/truncate failure aftermath explicit: once creation or truncation
  becomes observable, a later open failure does not imply rollback and cannot be
  reported as cancellation with zero effect.
- Closed undisclosed-handle lifetime ambiguity: an open that terminates without
  returning a `File` retains implementation custody of internal resources and
  must release them before a cancelled/failed terminal result.

### Changed
- Synchronized all revisioned specification documents to revision 91. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.90] - 2026-09-03

### Fixed
- Closed the structured-child terminal-outcome ambiguity. Normal owner completion
  now waits for every non-detached child to become terminal without implicitly
  observing or propagating that child's result.
- Defined that a failed non-detached child does not automatically fail an otherwise
  normally completing owner, and a cancelled child does not automatically cancel
  that owner. Future failure/cancellation remains observable through the ordinary
  Future API such as `value()`.
- Explicitly rejected hidden "unobserved failure" / "failure consumed" state whose
  history could otherwise make scope-exit behavior implementation-dependent.
- Kept the existing opposite-direction rule unchanged: owner error or cancellation
  still requests cancellation of non-detached children and waits for cleanup.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 90.

## [0.1.89] - 2026-09-03

### Fixed
- Closed the observable execution-context and ownership ambiguity of `Future.then()`.
  A `then` call now creates a distinct structured continuation task owned by the
  activation that calls `then`, unless the destination Future is detached.
- Defined non-reentrant continuation execution: source completion only makes the
  continuation runnable and never invokes the transform inline, including when
  the source Future is already terminal at the `then()` call.
- Defined terminal-state propagation: resolved sources invoke the transform,
  failed sources fail the destination without invoking it, and cancelled sources
  cancel the destination without invoking it.
- Defined downstream-only cancellation and detachment for composition: cancelling
  or detaching the destination continuation does not cancel, detach, or otherwise
  alter the source Future.
- Preserved automatic Future flattening and avoided imposing any new global order
  between independent continuations.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 89.

## [0.1.88] - 2026-09-03

### Fixed
- Made `Closable.close()` lifecycle semantics deterministic: invoking `close()`
  commits permanent lifecycle termination, so the close Future cannot later
  report `cancelled`.
- Removed implementation freedom over pending operations displaced by close:
  closure-induced termination fails them as closing/closed; an independent
  cancellation may still win under that operation's own cancellation contract.
- Required successful close to leave no previously accepted I/O operation
  pending and repeated close calls to observe one consistent lifecycle outcome.

### Changed
- Synchronized all revisioned specification documents to revision 88. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision.


## [0.1.87] - 2026-09-03

### Fixed
- Closed the Core v0.1 value-identity classification. Value identity now applies
  exactly to Number values, String values, the canonical Boolean values, and
  `null`; every other object has individual object identity.
- Removed the previous "includes at least" wording that allowed independent
  implementations to invent additional value-identity categories and therefore
  disagree on observable `===` results.
- Clarified that immutability, `close()`, `freeze()`, interning,
  canonicalization, host representation, and delegation to a value object do
  not grant value identity.
- Clarified that standard prototype objects, Closures, collections, Futures,
  errors, contexts, module instances, and other objects remain identity-bearing
  unless a future normative language revision explicitly adds a new
  value-identity family.

### Changed
- Made the runtime identity classifier exhaustive and semantic rather than
  implementation-extensible. Numeric and String identity continue to use their
  existing family-specific semantic rules.
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 87. No syntax,
  concurrency-model, or I/O-model semantics change in this revision.


## [0.1.86] - 2026-09-03

### Fixed
- Closed the concurrency specification-authority ambiguity. `docs/design/CONCURRENCY_DESIGN.md`
  remains a mixed design ledger, but sections whose status is exactly `CLOSED` or
  `CLOSED --- REVISED` are now explicitly normative for Actor/Future/concurrency
  semantics. Sections carrying OPEN, PENDING, DIRECTION, DETAILS OPEN, API OPEN,
  or other design-only qualifiers remain non-normative.
- Defined the boundary for partially closed sections: an API, syntax, policy,
  mechanism, or implementation detail explicitly stated to remain open inside a
  CLOSED section remains non-normative without weakening the closed semantic
  rules around it.
- Made `AGENTS.md`, `PROTOS_LANGUAGE_SPEC.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, and `PROTOS_IO_MODEL.md` agree on that
  authority model, so an independent implementation no longer has to choose
  between treating the concurrency ledger as merely advisory and treating it as
  the owner of Actor/Future semantics.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 86. No Actor, Future,
  scheduling, ordering, cancellation, I/O, or grammar behavior changes in this
  revision.

## [0.1.85] - 2026-09-03

### Fixed
- Defined `ByteWritable.write(bytes)` argument capture precisely: every invocation
  takes a logical snapshot of the supplied mutable `Bytes` size and octet contents
  at invocation time, so later caller mutation cannot change an already-issued
  write.
- Kept the snapshot semantic rather than representational: implementations may
  use copy-on-write, immutable backing, retained storage, scatter/gather, or other
  optimizations, but `write` creates no caller-visible borrow/freeze lifetime and
  snapshot capture is not the I/O commitment boundary.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 85. Only
  `PROTOS_IO_MODEL.md` gains normative semantic content in this revision; the
  other revisioned documents receive the common revision/date synchronization
  only.


## [0.1.84] - 2026-09-03

### Fixed
- Defined the Core v0.1 `Float` semantic value set exactly as IEEE 754-2019
  `binary64` instead of the previous implementation-dependent
  "IEEE-754-style" formulation.
- Required strict `binary64` results for standard Float basic arithmetic,
  `roundTiesToEven` rounding, gradual underflow, and observable equivalence
  across implementations. Excess precision, flush-to-zero modes, host rounding
  state, and fused-operation contraction may not change Protos results.
- Made positive and negative infinity, signed zero, subnormal values, and NaN
  mandatory parts of the Float model rather than depending on a chosen host
  floating representation.
- Defined decimal Float literal conversion as exact-decimal to IEEE 754-2019
  `binary64` using `roundTiesToEven`, independently of host parsing behavior.
- Closed the remaining NaN representation leak: Core Float has one semantic NaN
  value, while payload/sign bits are non-semantic implementation representation
  and are not required to survive operations or storage.

### Changed
- Clarified that IEEE floating-point conditions arising from the standard Float
  basic arithmetic produce IEEE Float results rather than signaling Protos
  errors merely because overflow, underflow, division by zero, or an invalid
  floating-point operation occurred.
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 84. No syntax,
  concurrency-model, or I/O-model semantics change in this revision.


## [0.1.83] - 2026-09-03

### Fixed
- Defined object-composition visibility during construction. Object-body items
  execute strictly left to right, and a successfully completed composition item
  makes its unique effective contributions immediately visible to subsequent
  body items. Later body items never retroactively affect earlier evaluation.
- Replaced deferred whole-body composition resolution with structural
  reservation of names declared directly by the receiving object body. A direct
  local slot declaration reserves its name against composition independently of
  textual position, but the reservation is not a binding and does not affect
  lookup before the declaration executes.
- Made each composition item atomic with respect to target structural mutation.
  The source expression is evaluated first; all effective contributions are
  then validated before any are installed. A conflict therefore cannot leave a
  partially composed target, and source-slot enumeration order cannot become
  observable through partial installation.
- Clarified that composition has neither first-wins nor last-wins semantics.
  Non-reserved contributions that collide with an existing local target slot
  signal `CompositionConflict`.

### Changed
- Simplified the executable composition model in
  `PROTOS_RUNTIME_SEMANTICS.md`: implementations no longer conceptually collect
  all composition contributions and defer final slot resolution until after the
  object body. Composition may be performed incrementally while preserving the
  structural priority of direct local declarations.
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`,
  `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and
  `PROTOS_IO_MODEL.md` to document revision 83. No grammar,
  concurrency-model, or I/O-model semantics change in this revision.


## [0.1.82] - 2026-09-03

### Changed
- Fixed Core v0.1 to The Unicode Standard, Version 17.0.0 as the normative Unicode repertoire and property version. `XID_Start` and `XID_Continue` used by identifier recognition are now explicitly the Unicode 17.0.0 properties, and identifier NFC conformance is tied to the Unicode normalization specification applicable to Unicode 17.0.0.
- Made host Unicode support non-normative: a JDK, VM, operating system, library, or other host Unicode database may be used only when it produces behavior equivalent to the Core v0.1 Unicode 17.0.0 requirements. Host-version differences must not change whether the same Protos source is lexically valid.
- Defined the normative Unicode version as part of the Protos language version rather than the document revision. Changing the normative Unicode version therefore requires a language-version change.
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`, `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and `PROTOS_IO_MODEL.md` to document revision 82. Only `PROTOS_GRAMMAR.md` gains normative semantic content in this revision; the other revisioned documents receive the common revision/date synchronization only.

### Implementation note
- The current Java 21 lexer implementation derives Unicode identifier properties and NFC checks from host JDK Unicode facilities. After this specification change, that implementation remains incomplete for full Core v0.1 Unicode 17.0.0 conformance until its Unicode data path is made independent of host-version differences.

## [0.1.81] - 2026-09-02

### Fixed
- Removed the remaining ambiguity in `docs/design/CONCURRENCY_DESIGN.md` that listed the Process capability among runtime references an Actor may have implicitly. Process authority is not ambient and is available to an Actor only when explicitly provisioned at creation or explicitly delegated later; granting a subordinate Process-local facility does not implicitly grant the whole Process capability.
- Made `Closable.close()` cancellation lifecycle semantics explicit in `PROTOS_IO_MODEL.md`: invoking `close()` begins permanent closing, and successful cancellation of the close operation never restores the receiver to the open state or re-enables operations requiring an open resource. Cancellation can only prevent close effects that have not yet become irreversible.
- Made the default BOM rule deterministic: UTF8, UTF16LE, and UTF16BE consume an initial matching BOM by default; an explicit configuration may preserve it as U+FEFF.
- Defined successful completion values for completion-only I/O operations. `write`, `flush`, `close`, `sync`, `writeText`, `writeLine`, `shutdownRead`, and `shutdownWrite` resolve their Futures to the receiver. Existing operations with value-bearing results, including `read`, `position`, `seek`, `size`, `truncate`, `readText`, and `readLine`, retain their previously specified result semantics.
- Restored `docs/design/CONCURRENCY_DESIGN.md` Scope Roots to `DIRECTION CLOSED, DETAILS OPEN --- REVISED`; revision 80 closed the Process direction but did not close every remaining NodeRoot/ClusterRoot API/detail.

### Changed
- Synchronized `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_GRAMMAR.md`, `PROTOS_RUNTIME_SEMANTICS.md`, `docs/design/CONCURRENCY_DESIGN.md`, and `PROTOS_IO_MODEL.md` to document revision 81. No grammar production, core evaluator rule, or language syntax changes in this revision.

## [0.1.80] - 2026-09-02

### Added
- Added `PROTOS_IO_MODEL.md` as the normative domain model for Protos I/O. It defines byte-oriented asynchronous I/O capabilities (`ByteReadable`, `ByteWritable`, `Flushable`, `Closable`, `ByteSeekable`, `ByteSized`, `Truncatable`, `Syncable`, `ReadShutdown`, and `WriteShutdown`), I/O-operation commitment/cancellation, wrapper ownership, text adapters, encoding semantics, line reading, file-open dimensions, filesystem authority, Path/URL separation, Process I/O bootstrap, environment/argument snapshots, standard-stream availability/encoding, Actor-safe capability delegation, and pay-as-you-grow requirements.

### Changed
- Refined `Process` in `docs/design/CONCURRENCY_DESIGN.md`: a Protos Process is an execution/isolation/failure domain, not normatively an operating-system process or address-space boundary. Every Protos execution has a lightweight Process and one RootActor even when no additional Actor is ever created. The former conceptual `ProcessRoot` role is folded into the Process runtime entity/capability itself.
- Defined Process as custodian of Process-local application-facing host authority. The RootActor owns initial Actor-local application state but receives appropriate Process capabilities at bootstrap; additional Actors do not implicitly inherit host/Process capabilities and receive only explicitly provisioned or delegated capabilities.
- Added the I/O-domain cross-reference to `PROTOS_LANGUAGE_SPEC.md`, `PROTOS_RUNTIME_SEMANTICS.md`, and the concurrency I/O section. Clarified in Runtime Semantics that I/O `COMMITTED` is an operation-level concept and does not add a fifth Future state.
- Replaced the stale frozen-prelude example based on an assumed `print` binding with an `Object` binding example. `print` is not made a required Core-prelude binding by this revision.
- Updated the Language Specification encoding example to use encoding-owned one-shot operations (`UTF8.decode(bytes)` / `UTF8.encode(text)`) and delegated the standard encoding catalogue, decoding policy, BOM, and text-I/O rules to `PROTOS_IO_MODEL.md`.
- Updated `AGENTS.md` so normative domain models are treated as source-of-truth documents for their semantic domains, while preserving the special design-ledger status of `docs/design/CONCURRENCY_DESIGN.md`.
- Synchronized all revisioned specification/design documents to document revision 80. `PROTOS_GRAMMAR.md` receives only the common revision bump; revision 80 introduces no new I/O syntax and no grammar production changes.

### Scope
- External-process creation/control, process IDs, `kill`, POSIX signals, process groups/sessions, terminal-control APIs, and the exact `print`/object-representation protocol remain outside `PROTOS_IO_MODEL.md`. Future process/host-execution facilities must reuse the I/O protocols for any streams they expose.

## [0.1.79] - 2026-09-02

### Changed
- Corrected the canonical evaluator's evaluation order for explicit-target slot creation and assignment in `PROTOS_RUNTIME_SEMANTICS.md`: `Create(targetExpr?, name, valueExpr)` and `Assign(targetExpr?, name, valueExpr)` now evaluate the target expression before the value expression, matching the normative left-to-right order already stated in `PROTOS_LANGUAGE_SPEC.md`. `getObject().x = makeValue()` and `getObject().x: makeValue()` evaluate the target first, then the RHS, then perform the operation. When no target expression exists, only the RHS is evaluated, as before. Indexed Access Lowering already followed the same left-to-right order and is unchanged. No other assignment or slot-creation semantics changed.
- Made the lexical-binding model of `PROTOS_RUNTIME_SEMANTICS.md` unambiguous. The conceptual operation `lexicalParentOf(context)` now formally returns the immediate lexical parent context of an execution context, and the association is established when the context is created: activation contexts take the closure's captured lexical context, construction contexts take the genuine lexical context chain of the enclosing activation, module contexts take the frozen prelude context, and the prelude context is the root of the lexical chain. `Activation.lexicalParent` holds `lexicalParentOf(activation.context)`, `lookupName` and `assignName` traverse the lexical chain only through `lexicalParentOf`, and `lexicalContextForClosureCreation` returns `activation.lexicalParent` for construction activations. Object delegation and lexical parenthood are now formally distinct: `Context` is the delegation prototype of execution contexts (`activationContext → Context → Object`), never their lexical parent, and bare-name lookup never walks a context's delegation chain. No observable lookup, capture, assignment, or construction semantics changed.
- Replaced the shared-mutable-memory formulation of concurrency memory semantics with the Actor model already decided in `docs/design/CONCURRENCY_DESIGN.md`. `PROTOS_LANGUAGE_SPEC.md` (Concurrency Memory Semantics) and `PROTOS_RUNTIME_SEMANTICS.md` (Future Completion Visibility) no longer promise a general model of shared mutable objects, arbitrary data races, and explicit synchronization primitives. Both documents now state: ordinary Actor-local Future/task execution is cooperative and interleaves only at explicit suspension points, never executing Protos code simultaneously against the same mutable Actor state; between suspension points Actor-local state is serialized; different Actors share no mutable Protos references; explicit isolated parallel computation (whose API remains open) may execute simultaneously but crosses an isolation boundary and receives no arbitrary live mutable aliases to the calling Actor's state. The Future completion visibility guarantee is preserved. The empty `Futures and Concurrency` heading in `PROTOS_LANGUAGE_SPEC.md` was removed.
- Replaced argument-spread examples in `PROTOS_LANGUAGE_SPEC.md` that used array-literal syntax Core v0.1 does not define (`values: [10, 20, 30]` and the neighboring `f.values([10, 20])`) with Core-valid code: the rest-capturing closure `pack: (...items) => items` produces the ordinary collection that is then spread. The `args == [1]` pseudo-example in the same section became `args.size == 1`. Core v0.1 still has no array literal syntax, and no grammar production changed.
- Updated document revisions: `PROTOS_LANGUAGE_SPEC.md` 78 -> 79 and `PROTOS_RUNTIME_SEMANTICS.md` 78 -> 79. `PROTOS_GRAMMAR.md` 78 -> 79 and `docs/design/CONCURRENCY_DESIGN.md` 07 -> 79 are synchronized to the common revision 79: no semantic content changed in either document. All four documentation documents now share the same `Document revision`.
- `docs/design/CONCURRENCY_DESIGN.md` is now explicitly part of the documentation, with design-ledger status: it carries the shared `Document revision`, it no longer has its own independent ledger version (the title `Protos Multithreading Design Ledger v1` became `Protos Multithreading Design Ledger`), and every change to the document must be recorded in this changelog. The governing policy is stated in `AGENTS.md`.

### Fixed
- `docs/design/CONCURRENCY_DESIGN.md` still carried `Document revision: 07` although revision 0.1.78 synchronized all four documents to the common revision 78. Its content (including the CLOSED section 72 added in 0.1.78) corresponds to the revision described by that changelog entry, so the header now reads 79, sharing the common documentation revision; no semantic content changed. The document remains a design ledger with CLOSED and OPEN sections, and nothing OPEN in it was promoted to Core by this revision.

## [0.1.78] - 2026-09-02

### Changed
- Synchronized document revisions: all four canonical documents now carry document revision 78. `PROTOS_LANGUAGE_SPEC.md` 77 -> 78, `PROTOS_RUNTIME_SEMANTICS.md` 76 -> 78, `PROTOS_GRAMMAR.md` 75 -> 78, and `docs/design/CONCURRENCY_DESIGN.md` from its former independent document revision 07 to the common revision 78. Language version remains 0.1.
- Formalized `Context` as the standard prototype for execution-context objects (design option A). Execution contexts remain ordinary Protos objects with ordinary delegation: an activation context and a `moduleContext` each delegate through `Context` to `Object`. `Context` is a standard prelude prototype, is not a reserved word, and is distinct from the reserved intrinsic pseudo-identifier `context`. Runtime pseudocode no longer refers to an undefined `standardContextPrototype`: activation contexts and module contexts are created with `parent = Context`, and behavior provided by `Context` is inherited through ordinary Protos delegation with no special lookup mechanism.
- Closed the shared-prelude / shallow-freeze / Actor-isolation hole. New invariant: any Protos object physically shared between Actors through the standard prelude must be semantically immutable for the duration of that sharing, and mutable Protos state reachable through standard facilities must be Actor-local. Freezing the prelude remains shallow and is not by itself sufficient to make objects referenced by its slots safe to share. No deep freeze is introduced; Actor isolation is not weakened; and implementations may physically share immutable implementation artifacts (parsed syntax, bytecode, machine code, immutable metadata, immutable constant data) where sharing is semantically unobservable, without being required to duplicate immutable data unnecessarily.
- Removed stale export terminology from the module rules. Core v0.1 has no export declarations, no export namespace, no `exports` object, and no separate export mechanism: `import(specifier)` is an ordinary operation exposed by the standard environment, it yields the module instance, and cross-module access occurs explicitly by obtaining a module instance and accessing its slots through ordinary member lookup. The obsolete wording "module/import/export mechanism" and "Import/export syntax is intentionally not defined ... and will be specified with the module system" was replaced consistently. Host-specific resolution of files, packages, standard-library modules, and search paths remains outside Core v0.1.
- Fixed two Future-composition examples that used the invalid parameterized trailing-closure form `future.then() (value) { ... }`. Both now use an ordinary explicit Closure argument: `future.then(value => { transform(value) })`. Parameterized trailing closures are not restored; trailing-closure syntax remains parameterless and unchanged.
- Renumbered `PROTOS_RUNTIME_SEMANTICS.md` into a consistent monotonically increasing sequence, resolving the duplicated section numbers 8 (Prefix Operator Lowering / Slot Creation) and 25 (Identity / Semantic Equality). No semantics changed during renumbering.

### Fixed
- Corrected broken "the language" wording artifacts in `PROTOS_RUNTIME_SEMANTICS.md` (e.g., "a the language object" -> "a language object", "ordinary the language error object" -> "ordinary language error object").
- `docs/design/CONCURRENCY_DESIGN.md` gains a CLOSED section 72, Standard Prelude Sharing, making the Actor-isolation rule and the standard-prelude rule explicitly agree; the document's module-state section already permitted sharing immutable implementation artifacts.

## [0.1.77] - 2026-09-02

### Changed
- Added expression-bodied Closures: a Closure body may now be exactly one ordinary expression instead of a braced sequence, e.g. `(x) => x * 2`. This is an exact mandatory desugaring: `closure-parameters => expression` is exactly equivalent to `closure-parameters => { expression }` (a braced body whose `Sequence` contains exactly that one expression), for every parameter form.
- Added optional parentheses for exactly one simple parameter: when a Closure has exactly one parameter and that parameter is neither a default parameter nor a rest parameter, the parentheses may be omitted and the parameter written as a bare identifier before `=>`: `x => x * 2` and `x => { ... }` are exactly equivalent to `(x) => x * 2` and `(x) => { ... }`. Parentheses remain mandatory for zero parameters, two or more parameters, a default parameter, and a rest parameter: `() => value`, `(a, b) => a + b`, `(x = 10) => x`, `(...items) => items`, and `(first, ...rest) => rest`. A bare parameter must satisfy the ordinary `identifier` rules, so reserved words remain invalid as parameter names.
- All Closure spellings — parenthesized or bare single-parameter, braced or expression body — create the same kind of Closure with identical runtime semantics. No new keyword, no new reserved word, and no new callable category was introduced; in particular there is no JavaScript-style distinction between `function` and arrow callables, and all forms have the same `this`, capture, `context`, `args`, `super`, method-binding, return-home, evaluation, Future/async, and error-propagation behavior.
- Nested shorthand Closures associate to the right: `x => y => x + y` means `x => (y => (x + y))` and is equivalent to `(x) => { (y) => { x + y } }`. This is specified normatively rather than left to accidental parser behavior.
- An expression body contains exactly one ordinary `expression`, never an `expression-sequence`: `x => print(x); foo()` is a Closure whose body is `print(x)` followed by the separate expression `foo()`, and `x => print(x)` followed by `foo()` on the next logical source line does not absorb `foo()`. Multiple expressions still require a braced body.
- The expression body is a full ordinary `expression`, not an artificially restricted subset: assignment, slot creation, non-local return `^`, nested Closures, and other ordinary expression forms are all permitted in an expression body (`x => this.value = x`, `x => ^x`, `x => y => x + y`).
- The shorthand composes with the existing expression grammar by ordinary precedence: `double: x => x * 2` means `double: (x => x * 2)`, and `f = x => x + 1` assigns the Closure object to `f`. Creating a Closure never invokes it: `applyLater(x => x * 2)` passes the Closure as an argument, and invoking an expression-bodied Closure directly requires the grouping already required by the postfix grammar, e.g. `(x => x * 2)(10)`.
- `{` immediately after `=>` continues to begin the Closure's braced body and is never reinterpreted as an object expression; an object-expression body is written with ordinary parenthesized grouping, `x => ({ ... })`. The choice between the braced and single-expression body forms is structural (the body's first token), with no parser heuristic and no semantic/type-based disambiguation.
- Newline handling follows the existing general rules and introduces no ASI-like or Closure-specific continuation mechanism. An identifier followed by a separating logical `NEWLINE` is a completed expression, so a following line that begins with `=>` is a syntax error (`x` alone on a line never becomes a single-parameter Closure). A logical `NEWLINE` immediately after `=>` is ordinary incomplete-construct continuation, so the body may begin on the next logical source line (`x =>` followed by an indented body is a Closure).
- Trailing-closure syntax is unchanged and remains parameterless and braced-only: `foo() { ... }` remains sugar for passing one final parameterless Closure, parameterized trailing closures are not restored, and the new shorthand is an ordinary explicit Closure in ordinary call-argument position (`items.each(item => print(item))`), never a trailing closure.
- `PROTOS_GRAMMAR.md` revision 73 -> 74: `closure-expression = closure-parameters, "=>", closure-body`, with `closure-parameters = parameter-list | identifier`; `closure-body = braced-closure-body | expression`; `braced-closure-body = "{", expression-sequence, "}"`; and `trailing-closure = braced-closure-body`. Closures §16 was extended with subsections specifying parameter forms, body forms and exact equivalence, composition with the expression grammar, right association of nested expression-bodied Closures, newline handling, and valid/invalid examples; the Whitespace and Newlines section now makes the `=>` continuation and identifier/`=>` newline cases explicit; the Compact EBNF and the trailing-closure section were updated consistently.
- `PROTOS_LANGUAGE_SPEC.md` revision 76 -> 77: the Closures section now presents the expression-body and single-parameter shorthand spellings and their exact equivalence and identical runtime semantics; Return Semantics notes that in an expression-bodied Closure the single body expression supplies the normal return value; the Trailing Closures section clarifies that the shorthand never reintroduces trailing closures.
- `PROTOS_RUNTIME_SEMANTICS.md` was not modified and remains at revision 76: no normative runtime behavior changed. Expression-bodied Closures are pure syntax and desugar before reaching the runtime; the existing Closure Creation, method binding, non-local return, Future, and error semantics apply unchanged to every spelling.
- `docs/design/CONCURRENCY_DESIGN.md` was not modified and remains at its independent document revision 07.
- Closed audit defect G1: the rev-77 production `closure-body = braced-closure-body | expression` formally admitted a second derivation for any Closure body whose first parser token is `{` — `x => { value: x }` could derive through `expression` reaching `object-expression` -> `object-body` as well as through `braced-closure-body` — although this revision's normative text already specifies that the `{` immediately after `=>` always begins the Closure's braced body. G1 is a formalization defect, not a semantic one: no Closure semantics are changed, and no new semantic decision is made. The grammar now expresses the already-decided rule itself: `closure-body = braced-closure-body | [ lookahead != "{" ], expression`. The single-expression alternative carries a first-token guard (new EBNF notation defined in the grammar's Scope section) permitting entry only when the next parser token in the continuing token sequence is not `{`; because `braced-closure-body` begins with the literal `{`, the two alternatives are disjoint on the body's first token, and a `{`-initial body has exactly one derivation — the braced form.
- The correction does not change any other expression: object expressions keep their full grammar and semantics (`x => parent { value: x }` remains an expression-bodied Closure returning an object whose parent is `parent`); trailing-closure syntax remains `trailing-closure = braced-closure-body`, parameterless and braced-only; and every expression-bodied spelling valid in 0.1.77 remains valid (`x => x + 1`, `x => foo(x)`, `x => this.value = x`, `x => ^x`, `x => y => x + y`, `x => ({ ... })`, `(x => x * 2)(10)`, and newline-continued bodies such as `x =>` followed by an indented body). No new keyword, no new callable category, no parser heuristic, no speculative parse, and no type-based or semantic disambiguation is introduced; the boundary is decided by the formal grammar. `x => { value: x }` is a braced Closure whose body is the slot-creation expression `value: x`, and `x => ({ value: x })` is the expression-bodied Closure whose parenthesized body evaluates to the object.
- `PROTOS_GRAMMAR.md` revision 74 -> 75: the normative `closure-body` production in Closures §16 and its copy in the Compact EBNF now carry the `[ lookahead != "{" ]` guard on the single-expression alternative; the EBNF notation list in Scope defines the guard; Closures §16 states that the two body forms are disjoint on the body's first token and adds the boundary examples `x => { value: x }` (braced body) versus `x => ({ value: x })` (expression body yielding an object); the Compact EBNF explanatory note is updated consistently.
- `PROTOS_LANGUAGE_SPEC.md` was not modified and remains at revision 77: its normative statement that the `{` immediately after `=>` always begins the Closure's braced body is the already-decided semantics, and no normative rule needed to change for a grammar formalization correction. `PROTOS_RUNTIME_SEMANTICS.md` was not modified and remains at revision 76. `docs/design/CONCURRENCY_DESIGN.md` was not modified and remains at its independent document revision 07.

## [0.1.76] - 2026-09-02

### Changed
- Closed audit issue D7: the classification of an Actor's initial entry point as either an importable initial module or a standalone non-importable entry point is fixed when execution of that entry begins; it is not recomputed later, and a standalone instance is never retroactively adopted into the Actor-local module cache.
- When, at the start of an initial entry's execution, the host/resolver has a canonical importable identity for it, the entry starts through the ordinary importable-initial-module lifecycle unchanged from revision 75: determine the canonical `ModuleKey`, use `ensureModuleInstance`, cache as `INITIALIZING` before body execution, execute the body, and transition to `READY` on success. A cycle back to such an initial module returns the same instance (`main#1`); no `main#2` is created.
- When, at the start of an initial entry's execution, the entry has no canonical `ModuleKey` through which `import()` could address it, it executes as a standalone entry point. That instance is Actor-local, executes its body in its own `moduleContext`, is not registered in the Actor-local module cache, and has no `ModuleKey`. The absence of a `ModuleKey` is determined for that execution when the entry begins; it does not change retroactively when the host's resolution capabilities change.
- There is no retroactive adoption of a standalone instance. The runtime does not later give a standalone instance a `ModuleKey`, does not register an already-executed standalone instance in the module cache, and does not convert it into the active cached module instance of any `ModuleKey`. No retroactive cache registration, module-instance adoption, identity mutation, cache migration, source-code deduplication, or rollback of standalone effects is introduced.
- If the host later changes its configuration, resolver, mounted sources, packages, or other host-defined mechanisms so that code equivalent to a previously executed standalone entry becomes importable under a canonical `ModuleKey`, that does not change the identity or status of the standalone instance already created. A later `import()` that resolves to that `ModuleKey` operates exclusively on the Actor-local module cache: a cache miss creates a new module instance through the ordinary lifecycle (`ensureModuleInstance` / `executeInitialModule`), so the standalone instance and the later cached instance are distinct objects (`E#1 !== E#2`).
- The module body corresponding to a previously standalone entry may execute again and its side effects may occur again when it is later imported. This does not violate cache-before-execute or the D6 invariant of at most one active cached module instance per `ModuleKey`, because the standalone instance never was the cached module instance of that `ModuleKey`.
- The rev-75 formulation that the "normal canonical-identity rules apply if the host later gives it a canonical identity" is superseded: the later appearance of an importable canonical identity does not apply those rules retroactively to an existing standalone instance.
- No syntax and no grammar production changed; D5 and D6 semantics remain unchanged in full (module instance = `moduleContext`; Actor-local module instances and caches; canonical `ModuleKey` identity; cache-before-execute; `INITIALIZING` and `READY`; at most one active cached instance per `ModuleKey` per Actor; cache membership distinct from object reachability; cyclic imports legal; recursive import of an `INITIALIZING` module returns the same partial instance immediately; no hidden suspension; no module TDZ, slot predeclaration, or hoisting; ordinary missing-slot semantics; successful initialization retains the cached instance; failed initialization removes the cache entry and a later import may retry with a fresh instance; escaped failed partial instances remain ordinary reachable objects; no rollback or revocation; importable initial module cached as `INITIALIZING` before execution; host-specific specifier resolution outside Core v0.1; `import()` remains eager and injects no lexical bindings).
- Updated canonical documents to revision 76: `PROTOS_LANGUAGE_SPEC.md` (entry classification fixed at execution start; standalone instances never retroactively adopted; later import of equivalent code may create a distinct instance) and `PROTOS_RUNTIME_SEMANTICS.md` (clarity that the standalone/importable choice is made before the entry executes, that `executeStandaloneEntry` instances remain outside the cache and cannot be adopted later, and that `executeInitialModule` never adopts an existing standalone instance). `PROTOS_GRAMMAR.md` was not modified and remains at revision 73. `docs/design/CONCURRENCY_DESIGN.md` required no change for D7 and remains at its independent document revision 07 (revision 07 was established earlier for the unrelated Isolated Parallel Execution design section); its module/ownership statements are already consistent with D7.

## [0.1.75] - 2026-09-02

### Changed
- Closed audit issue D6: an Actor's initial module participates in module identity and caching exactly like an imported module when it has an importable canonical identity. When the initial module can be resolved by `import()` to a canonical `ModuleKey`, the runtime determines or assigns that key, creates the module instance and its `moduleContext`, inserts it into the Actor-local module cache in state `INITIALIZING`, executes the module body, transitions it to `READY` on success, and applies the same cache-removal rule as any other failed module initialization. The cache-before-execute invariant therefore covers an importable initial module, which is not a special mutable module instance that exists outside the cache.
- A cyclic import back to the initial module returns that same cached instance and cannot create a second initial-module instance. When the initial module `main` imports `b` and `b` imports `main`, the recursive import returns the original `main#1`; no `main#2` is created.
- A host entry point with no importable canonical identity (a host-defined startup mechanism that the module resolver cannot map to a `ModuleKey`) need not be assigned a fabricated filesystem/package identity. It remains Actor-local, its mutable `moduleContext` is not shared with another Actor, it must not alias an imported module, and the normal canonical-identity rules apply if the host later gives it a canonical identity.
- "Module singleton per Actor" is clarified to mean at most one active cached module instance per canonical `ModuleKey`: the Actor-local module cache maps `ModuleKey` to the current active module record, with at most one record per key at a time. The phrase does not guarantee that only one object per canonical module identity can ever remain reachable during the Actor's lifetime.
- Cache membership and ordinary object reachability are distinct concepts. Escaped references to a failed partial instance may remain reachable after its cache entry is removed, and such an instance may coexist with a later fresh cached instance (`foo#1 !== foo#2`); only the later instance is the Actor's active cached module instance for that `ModuleKey`. Both objects belong to the same Actor, so coexistence does not violate Actor isolation.
- Failed-instance references are not revoked, not rolled back, and do not enter a hidden invalid-object state; removing a cache entry does not invalidate the instance object. No tombstone, revocation, identity mutation, or hidden invalidation is introduced.
- Runtime pseudocode in `PROTOS_RUNTIME_SEMANTICS.md` is factored so that ordinary import and Actor startup of an importable initial module share one module-instance lifecycle (`ensureModuleInstance`, called by `importModule` and `executeInitialModule`), making creation of a duplicate instance for the same canonical identity impossible. A non-importable host entry point is executed directly by `executeStandaloneEntry` without fake cache registration.
- No syntax and no grammar production changed; D5 semantics remain unchanged in full (module instance = `moduleContext`; Actor-local module caches; canonical `ModuleKey`; cache-before-execute; `INITIALIZING` and `READY`; cyclic imports are legal; recursive import of an `INITIALIZING` module returns the same partial instance immediately; no hidden suspension; no module TDZ, slot predeclaration, or hoisting; ordinary missing-slot semantics; successful initialization retains the cached instance; failed initialization removes the cache entry and a later import may retry with a fresh instance; no rollback of side effects; escaped references to failed partial instances are not revoked; host-specific module-specifier resolution remains host-defined; module instances remain Actor-local; immutable compiled/code artifacts may be shared invisibly).
- Updated canonical documents to revision 75: `PROTOS_LANGUAGE_SPEC.md` (initial-module cache registration, module-cache authority, escaped-failed-instance coexistence) and `PROTOS_RUNTIME_SEMANTICS.md` (factored module-instance lifecycle pseudocode). `PROTOS_GRAMMAR.md` was not modified. `docs/design/CONCURRENCY_DESIGN.md` (a design ledger, not a canonical document) was updated to its independent document revision 06 to replace the imprecise "module singleton per Actor" wording with the active-cached-instance invariant and to confirm that the RootActor's initial module is not outside the module model merely because it started the Process.

## [0.1.74] - 2026-09-02

### Changed
- Closed audit issue D5: module instances are Actor-local. A module instance belongs to exactly one Actor, and each Actor owns an independent module cache. Importing a module never provides access to mutable module state belonging to another Actor, and there are no process-global mutable module instances. The rule is explicitly consistent with the Actor isolation principle that no shared mutable Protos memory exists between Actors.
- Normative decision: a module instance is the module's `moduleContext` object. The module body executes with the module instance as its current execution context, top-level `:`-created bindings are local slots of the module instance, `import(specifier)` yields the module instance, and reading a member of a module instance observes the top-level binding slots exactly as they exist at that moment. There is no separate namespace object, wrapper, copy, or proxy, and module identity is ordinary object identity (`===`).
- Immutable compiled/code artifacts (parsed syntax, bytecode, machine code, immutable metadata, immutable constant data where otherwise semantically valid) may be physically shared between Actors, provided such sharing never exposes shared mutable Protos state. The observable `moduleContext` and mutable module state remain Actor-local.
- Normative decision: cache-before-execute. When an Actor imports a canonical module absent from that Actor's cache, the runtime creates the module instance, inserts it into the Actor-local module cache in state `INITIALIZING`, and only then executes the module body in that instance's `moduleContext`. The module is therefore discoverable through recursive imports before its body finishes executing.
- Module initialization states conceptually include `INITIALIZING` and `READY`. These are semantic concepts and are not exposed through a public state-inspection or reflection API. A transient internal failure state is permitted while a failed initialization is handled, but a failed initialization must not remain cached as a successfully importable module.
- Repeated imports within one Actor of the same canonical module identity reuse the same Actor-local module instance (`a === b` for `a: import("foo")` followed by `b: import("foo")` when both resolve to the same canonical identity); the module body is not executed again for the second import. Across Actors the same canonical module identity produces distinct Actor-local module instances with distinct mutable `moduleContext`s.
- Cyclic imports are legal. A cycle is not rejected merely because it is cyclic, and no `ModuleInitializationCycle` error is signaled. Recursive import of a module already `INITIALIZING` returns the same partially initialized module instance immediately and does not create a hidden suspension point; suspending would deadlock ordinary cyclic imports within the same Actor, and Actor reentrancy remains identifiable only from explicit suspension operations.
- Partially initialized modules are observable: only slots whose creating top-level statement has already executed are present, and reading a slot that has not yet been created follows the ordinary Protos missing-slot / lookup error semantics. No module-specific temporal-dead-zone mechanism, no predeclaration of module slots, and no hoisting of future slot creations is introduced; normal Protos slot semantics remain authoritative. A partially initialized module is the real module instance in its current state, not a placeholder copy.
- Successful initialization transitions the cached module instance from `INITIALIZING` to `READY`. The same module instance and the same `moduleContext` remain cached, and a later import in that Actor returns that instance without re-executing the module body. No new module identity is created because initialization completed.
- Failed initialization removes that attempt's entry from the Actor's module cache; the initiating `import()` fails with that error according to the normal error-propagation model, and a later import may attempt initialization again and may create a fresh module instance. A failed attempt does not permanently poison the Actor's module cache, and a failed partial module instance is not defined as reusable by a later independent import.
- Failed initialization does not roll back effects already performed. Removing the failed module from the module cache does not time-travel or undo side effects already executed during its failed initialization, and references to a partially initialized instance obtained by cyclic participants before failure are not revoked.
- Actor lifetime and module lifetime coincide: an Actor's module cache and its Actor-local module instances die with the Actor, and creating a new Actor does not inherit the creator's module cache or live module contexts. An Actor's initial module follows the same module-context model and is Actor-local rather than process-global.
- `import()` is not redefined to inject bindings into the importing lexical context. The module specifier remains an ordinary expression, cross-module visibility remains explicit, and no ES-module-style static binding declarations, CommonJS `exports`, Python namespaces, or analogous module syntax is introduced. Host-specific module-specifier resolution remains host-defined; module identity, Actor-local instance, cache, initialization, cycles, and failure are defined by Protos semantics.
- Updated canonical documents to revision 74: `PROTOS_LANGUAGE_SPEC.md` (Actor-local module instance identity, caching, initialization, cycles, and failure) and `PROTOS_RUNTIME_SEMANTICS.md` (Actor-local module cache pseudocode and module states). No grammar production and no grammar prose changed; `PROTOS_GRAMMAR.md` was not modified.

## [0.1.73] - 2026-09-02

### Changed
- Closed audit issue D4: blank-content-line whitespace stripping is now explicitly scoped to multiline String literals whose closing `"""` delimiter establishes a structural indentation prefix.
- Normative decision: blank-line whitespace stripping is part of multiline indentation normalization and occurs only when a structural indentation prefix exists. Where such a prefix exists, blank content lines remain exempt from prefix matching and need not contain the complete structural prefix, and their SPACE/TAB characters are removed as incidental source-formatting indentation so that a source blank line contributes an empty logical line.
- Where content flows into the closing delimiter on its source line and no structural indentation prefix exists, no indentation normalization is performed and no indentation or other whitespace is removed from any content line, including whitespace-only content lines, whose SPACE and TAB characters are ordinary String content and are preserved verbatim. No separate unconditional blank-line-cleanup rule is introduced.
- The conceptual invariant is now explicit in all canonical documents: no structural indentation prefix ⇒ no indentation normalization.
- No other multiline String semantics changed. Revision 72 semantics are preserved in full: the closing delimiter is the sole source of the structural indentation prefix; there is no minimum-common-indent, longest-common-prefix, visual-column, or editor-TAB-width computation; SPACE and TAB remain distinct exact source characters; mixed SPACE/TAB prefixes remain legal when matched exactly; prefix mismatch on a non-blank content line remains a lexical error; prefix matching/removal precedes escape interpretation and escape sequences never satisfy the prefix; opening structural-newline removal and closing structural newline/trailing-line removal are unchanged; retained LF/CR/CRLF code points are unchanged; same-line delimiter behavior remains valid; triple-double-quoted Strings remain non-raw; single-quoted and double-quoted String semantics are unchanged; String interpolation remains unsupported; and the String lexical grammar and quote-run rules are unchanged. No grammar production was modified.
- Updated canonical documents to revision 73: `PROTOS_LANGUAGE_SPEC.md` (normative indentation-normalization semantics and examples), `PROTOS_GRAMMAR.md` (lexical indentation rules, notes, and Compact EBNF explanatory prose), and `PROTOS_RUNTIME_SEMANTICS.md` (String-value semantics).

## [0.1.72] - 2026-09-02

### Changed
- Closed audit issue D3 (triple-double-quoted String indentation): the structural indentation prefix is established solely by the closing `"""` delimiter, never by the content lines.
- When the closing delimiter terminates an indentation-only trailing line, the structural indentation prefix is exactly the sequence of SPACE and TAB characters on that source line immediately preceding the closing delimiter and may be empty. When content flows into the closing delimiter on its source line, no structural indentation prefix exists and no indentation is removed.
- Matching and removal operate on exact source characters. Every non-blank content line must begin with exactly the structural indentation prefix; the prefix is removed exactly once from each such line, and any additional indentation after the prefix is content and is preserved.
- SPACE and TAB are distinct source characters and are never equivalent for indentation purposes; a TAB equals no number of SPACE characters, and Core v0.1 defines no semantic TAB width. Matching is by exact source-character prefix, not by visual column, and no editor-tab-stop rule exists. Mixed SPACE/TAB prefixes are legal when each content line begins with exactly the same prefix.
- No minimum-indent, common-visual-column, or longest-common-prefix algorithm is used to compute indentation from the content lines.
- A non-blank content line that does not begin with the exact structural indentation prefix — fewer prefix characters, SPACE where the prefix requires TAB, TAB where the prefix requires SPACE, or any other difference — makes the triple-double-quoted String invalid. Consistent with the existing String-literal lexical-error model this is a lexical error: no String token and no String value is produced, and no recovery behavior is defined.
- Blank content lines are exempt from the prefix requirement and need not contain the complete structural indentation prefix; their SPACE/TAB characters are removed as incidental source-formatting indentation, so a source blank line contributes an empty logical line. No intentional whitespace is removed from a non-blank content line beyond the single structural prefix.
- Indentation matching and stripping are based on the raw source characters at the beginning of each content line, before escape processing; an escape sequence never counts as source indentation and never satisfies the structural prefix.
- The existing triple-String delimiter, quote-run, escape-set, unterminated-literal, and opening/closing structural-newline rules are unchanged, and single-quoted and double-quoted String semantics are unchanged.
- Updated canonical documents to revision 72: `PROTOS_LANGUAGE_SPEC.md` (normative multiline indentation semantics and examples), `PROTOS_GRAMMAR.md` (lexical indentation rules, notes, and examples), and `PROTOS_RUNTIME_SEMANTICS.md` (String-value semantics).

## [0.1.71] - 2026-09-02

### Changed
- Closed audit issue D2: every Protos object may serve as the delegation parent of another object.
- "Prototype" describes a role that an object plays when another object delegates to it; it is not a distinct object category. No parentability capability, flag, type, predicate, or hidden classification is introduced.
- The rule applies without exception to ordinary objects, built-in objects, immutable value objects, singleton values, execution-context objects, and every other Protos object. Values such as `this`, `context`, `args`, `true`, `false`, `null`, Number values such as `42`, and String values such as `"hello"` may serve as delegation parents, as may the standard built-in prototype objects (`Object`, `Number`, `Integer`, `Float`, `String`, `Boolean`).
- Using an object as a delegation parent does not make the newly created child identical to that parent and does not transfer the parent's value identity or value-category membership to the child. For example, `answer: (42) { ... }` creates an ordinary identity-bearing object delegating to the Number value `42`, so `answer === 42` is false. No coercion or value inheritance is introduced: `answer + 1` is not specified to behave as numeric `43` merely because `answer` delegates to `42`.
- Delegated message lookup through such a parent preserves the original receiver under the existing receiver-preserving delegation rules; `this` remains the child.
- No parentability classification or runtime check is introduced, and the runtime is not required to allocate a unique heap object for a value parent such as `42`; immediate/tagged representations of value parents remain permitted.
- The `parent-expression` grammar is unchanged and was neither broadened nor restricted; no inconsistency requiring correction was discovered during audit. Its broad forms (`identifier`, `intrinsic-reference`, `member-expression`, `parenthesized-expression`) are confirmed as intentional, and literal parents continue to require parentheses (`(true)`, `(42)`, `("hello")`, ...).
- `Object` remains the unique root and has no delegation parent, but may still serve as the parent of another object; bare `{ ... }` already creates an object whose parent is `Object`.
- Value identity of `Number`/`String`/`Boolean`/`null`, `true`/`false`/`null` singleton semantics, immediate/tagged representation freedom, receiver-preserving delegation, `Object` as unique root, immutable delegation parents, and open/closed/frozen object semantics are unchanged.

### Unresolved
- D3 (triple-double-quoted String indentation details: TAB width, visual columns, TAB-to-SPACE equivalence, how common indentation is computed when SPACE and TAB are mixed, and whether mixed SPACE/TAB indentation is legal) remains unresolved and is unchanged by this revision.

## [0.1.70] - 2026-09-02

### Changed
- Closed audit issue D1: all seven Core v0.1 reserved-word spellings (`this`, `context`, `args`, `super`, `true`, `false`, `null`) are now valid contextual member names in the structural position immediately following a member-access `.`.
- Introduced the grammar category `member-name` in `PROTOS_GRAMMAR.md`: `member-name = identifier | "this" | "context" | "args" | "super" | "true" | "false" | "null"`. `member-name` is used only where the grammar structurally expects a name immediately after `.`: `member-suffix`, `member-expression`, and `super-message-send`. The Compact EBNF contains the same definition and updates. `identifier` remains the ordinary lexical/binding-name grammar category and was not globally replaced by `member-name`.
- A reserved spelling used as a `member-name` denotes an ordinary slot or message name and does not retain its expression-level intrinsic/literal/special meaning. Therefore `obj.this`, `obj.context`, `obj.args`, `obj.super`, `obj.true`, `obj.false`, `obj.null`, `obj.true()`, `obj.null = value`, `obj.super: value`, `obj.a.this`, and `f: obj.true` are valid structural member operations, subject to the existing runtime rules for reading, invoking, modifying, or creating the selected slot.
- Lexical reserved-word classification is unchanged: the lexer continues to tokenize the seven spellings as their dedicated reserved tokens rather than as ordinary identifier tokens. This revision introduces no contextual lexing; the parser accepts either an identifier token or one of the seven reserved tokens when parsing `member-name`.
- Bare reserved-word semantics are unchanged: `this`, `context`, `args`, `true`, `false`, and `null` retain their ordinary expression-level meanings, and bare `super` remains invalid. Reserved words remain invalid where the grammar expects `identifier`, including parameter names, rest-parameter names, bare assignment targets, and bare slot-creation targets: `this: value`, `context: value`, `args: value`, `super: value`, `true: value`, `false: value`, `null: value`, `(a, true) => { ... }`, `(...super) => { ... }`, bare `super`, `foo(super)`, and `f: super.foo` all remain invalid.
- The leading `super` of `super.foo()` continues to introduce the existing `super-message-send`; the name after the dot is now a `member-name`, so reserved spellings are valid super message names: `super.true()`, `super.this()`, and `super.super()` are syntactically valid super message sends whose message names are `true`, `this`, and `super`, respectively. This does not make `super` a first-class value, and method extraction (`f: super.foo`) remains unsupported.
- No reflection API and no arbitrary-String slot-name rule are introduced by this revision. The revision decides only the relationship between ordinary identifier spellings, the seven reserved-word spellings, and structural member syntax after `.`.
- No runtime semantics changed: `member-name` is a grammar-level category, and the runtime continues to operate on ordinary slot/message names.

### Unresolved
- D3 (triple-double-quoted String indentation details: TAB width, visual columns, TAB-to-SPACE equivalence, how common indentation is computed when SPACE and TAB are mixed, and whether mixed SPACE/TAB indentation is legal) remains unresolved and is unchanged by this revision.

## [0.1.69] - 2026-09-02

### Changed
- Closed audit issue C3d: the normative grammar in `PROTOS_GRAMMAR.md` is now self-contained for parser productions. Every parser nonterminal referenced by a normative EBNF production is now defined in the normative body, either directly in the relevant normative section or by an explicit normative cross-reference to a production defined elsewhere in the same document.
- Added the normative operator/expression hierarchy to the Operators section, preserving exactly the existing Compact EBNF structure and precedence semantics: `binary-expression`, `logical-or-expression`, `logical-and-expression`, `equality-expression`, `equality-operator`, `comparison-expression`, `comparison-operator`, `additive-expression`, `additive-operator`, `multiplicative-expression`, `multiplicative-operator`, `custom-binary-expression`, `unary-expression`, and `unary-operator`.
- Added the normative `member-expression` production in the Member Access, Calls, Indexing, and Postfix Expressions section, preserving the existing grammar exactly: `member-expression = primary-expression, { postfix-operation }, ".", identifier`.
- Added the normative `intrinsic-reference` production in the Primary Expressions section, preserving revision 68 / C6 exactly: `intrinsic-reference = "this" | "context" | "args"`. `true`, `false`, and `null` remain literals only; `this`, `context`, and `args` remain intrinsic references only; `super` remains governed exclusively by `super-message-send`.
- No syntax, precedence, associativity, tokenization, parsing behavior, or desugaring changed: the normative additions are the same productions already present in the Compact EBNF, moved/aligned into the normative body, and no new parser alternative or lexical rule was introduced.
- The Compact EBNF remains the compact consolidated view of the same grammar and is unchanged apart from its revision meta-note, updated to revision 69.
- No runtime semantics changed.

### Unresolved
- D3 (triple-double-quoted String indentation details: TAB width, visual columns, TAB-to-SPACE equivalence, how common indentation is computed when SPACE and TAB are mixed, and whether mixed SPACE/TAB indentation is legal) remains unresolved and is unchanged by this revision.

## [0.1.68] - 2026-09-02

### Changed
- Closed audit issue C6: removed the duplicate syntactic classification of `true`, `false`, and `null`.
- `true`, `false`, and `null` are literals only; `this`, `context`, and `args` are intrinsic references only. `primary-expression` continues to contain both `literal` and `intrinsic-reference`, so each of `true`, `false`, and `null` now has exactly one syntactic derivation from `primary-expression`, through `literal`, while `this`, `context`, and `args` remain valid through `intrinsic-reference`.
- The Compact EBNF `intrinsic-reference` production now contains only `"this"`, `"context"`, and `"args"`.
- The `literal` production is unchanged and remains `number-literal | string-literal | "true" | "false" | "null"`.
- `super` remains governed exclusively by `super-message-send` and is not added to `intrinsic-reference`.
- All seven reserved words (`this`, `context`, `args`, `super`, `true`, `false`, `null`) are unchanged: the lexical reserved-word rules are untouched.
- No runtime semantics changed: the values produced by these literals and intrinsic references are unchanged.

### Unresolved
- C3d (broader normative-grammar self-containment beyond the String forms) remains unresolved and is unchanged by this revision.

## [0.1.67] - 2026-09-02

### Changed
- Closed issue C3c: all three supported Core v0.1 String literal forms now have formal lexical grammar.
- The normative lexical grammar in `PROTOS_GRAMMAR.md` now formally defines `single-quoted-string`, `double-quoted-string`, and `triple-double-quoted-string`, together with the helper productions they require.
- The valid escape set is formally encoded unchanged: `\\`, `\'`, `\"`, `\n`, `\r`, `\t`, `\b`, `\f`, and `\u{HEX}`. No escape is added, removed, or reinterpreted; `\0`, `\xNN`, octal escapes, named Unicode escapes, and interpolation escapes remain unsupported.
- The Unicode escape source shape is formally encoded as `\u{` followed by 1 to 6 hexadecimal digits followed by `}`; hexadecimal digits are `0`-`9`, `a`-`f`, and `A`-`F`, and underscore separators are not part of the Unicode escape shape.
- Unicode scalar validity remains lexically enforced: a `\u{HEX}` value that is not a valid Unicode scalar value (surrogates, or values greater than U+10FFFF) is a lexical error.
- Single-quoted and double-quoted raw-newline restrictions are unchanged: a logical source newline before the matching closing quote remains a lexical error, and both forms remain single-line literals.
- Triple-double-quoted String logical-newline and indentation behavior is unchanged; the lexical grammar permits logical source newlines in triple-double content and does not alter opening/trailing newline removal or indentation normalization.
- C8 quote-run behavior is unchanged: triple-double opening priority, first-three-unescaped-quotes closing, exact-three-quote delimiters, escaped-quote non-participation, and post-delimiter quote lexing are all preserved by the formal grammar, with the C8 prose remaining authoritative.
- C7 unterminated behavior is unchanged: the valid-token grammar requires a closing delimiter, and no EOF alternative or partial String token is introduced.
- C4 malformed/incomplete escape behavior is unchanged: only valid escape sequences are listed, and a backslash followed by anything outside the valid escape grammar remains a lexical error.
- Triple-single-quoted strings remain unsupported.
- String interpolation remains unsupported.
- No implicit String-literal concatenation is introduced.
- Indentation normalization is unchanged.
- The Compact EBNF now defines `string-literal` and references the normative String lexical productions instead of leaving them dangling; the Compact EBNF revision meta-note was updated to revision 67.
- No runtime semantics changed: this revision formalizes source spelling only.

### Unresolved
- D3 (triple-double-quoted String indentation details: TAB width, visual columns, TAB-to-SPACE equivalence, how common indentation is computed when SPACE and TAB are mixed, and whether mixed SPACE/TAB indentation is legal) remains unresolved and is unchanged by this revision.
- C3d (broader normative-grammar self-containment beyond the String forms) remains unresolved and is unchanged by this revision.

## [0.1.66] - 2026-09-02

### Changed
- Closed issue C8: triple-double quote-run tokenization is now deterministic.
- Triple-double-quoted String opening-delimiter recognition takes priority when `"""` occurs at the current lexical position outside a String: three consecutive unescaped double quotes begin a triple-double-quoted String rather than an ordinary double-quoted String opener followed by another double quote.
- Inside a triple-double-quoted String, the first three consecutive unescaped double-quote characters form the closing delimiter.
- Opening and closing delimiters consume exactly three double quotes; there is no greedy rule that consumes a run of four, five, six, or more quotes as one delimiter.
- One or two consecutive unescaped double quotes inside triple-double content are ordinary content when they do not begin a closing delimiter.
- Quotes remaining after a closing delimiter are lexed normally from the next lexical position.
- An escaped double quote (`\"`) is String content and does not participate in closing-delimiter recognition; no new triple-quote escape is introduced, and the meaning of `\"` is unchanged.
- No lexical backtracking occurs to rescue later malformed tokenization: quote-run decisions are not revised based on whether later tokenization or parsing succeeds.
- Empty triple-double-quoted Strings remain valid (`""""""`).
- No implicit adjacent String-literal concatenation was introduced; a lexically valid sequence of adjacent String tokens remains subject to the ordinary expression grammar.
- C7 behavior is unchanged: if quote-run tokenization begins a new String lexical construct that reaches the end of source before its required closing delimiter, the source is rejected as a lexical error.
- C4 escape behavior is unchanged: malformed or incomplete escape sequences remain lexical errors, and quote-run recognition must not reinterpret characters already consumed as part of an escape.
- Multiline newline and indentation behavior is unchanged: logical source newlines, retained newline code points, opening/trailing newline removal, and indentation normalization in triple-double-quoted Strings are unaffected.
- No runtime semantics changed: quote-run recognition is lexical behavior.

### Unresolved
- C3c (`single-quoted-string`, `double-quoted-string`, `triple-double-quoted-string`) remains unresolved and is unchanged by this revision.
- D3 (triple-double-quoted String indentation details: TAB width, visual columns, TAB-to-SPACE equivalence, how common indentation is computed when SPACE and TAB are mixed, and whether mixed SPACE/TAB indentation is legal) remains unresolved and is unchanged by this revision.

## [0.1.65] - 2026-09-02

### Changed
- Closed issue C7: reaching the end of source before the required closing delimiter of a String literal is now a lexical error.
- The rule applies to all three Core v0.1 String forms: single-quoted (`'...'`), double-quoted (`"..."`), and triple-double-quoted (`"""..."""`).
- An unterminated String literal never produces a partial String token. The lexer must not recover by treating the opening quote as another token, emitting the accumulated content as a partial String, splitting the malformed literal into otherwise valid tokens, implicitly inserting a closing delimiter, or interpreting the end of source as the closing delimiter; the parser never receives a successfully formed String token for an unterminated literal.
- Existing single-quoted and double-quoted raw-newline lexical errors are unchanged: a logical source newline before the matching closing quote remains a lexical error and terminates String recognition before any end-of-source determination.
- Existing malformed/incomplete escape lexical errors are unchanged. Reaching the end of source after a backslash or during an incomplete escape while String recognition is active is lexically invalid, without requiring a normative diagnostic-category precedence between an "incomplete escape" and an "unterminated String".
- Triple-double-quoted multiline String newline, indentation, and delimiter behavior is unchanged.
- No runtime semantics changed: malformed source literals are rejected during lexing, before parser/runtime semantics.

### Unresolved
- C3c (`single-quoted-string`, `double-quoted-string`, `triple-double-quoted-string`) remains unresolved and is unchanged by this revision.
- D3 (triple-double-quoted String indentation details: TAB width, visual columns, TAB-to-SPACE equivalence, how common indentation is computed when SPACE and TAB are mixed, and whether mixed SPACE/TAB indentation is legal) remains unresolved and is unchanged by this revision.

## [0.1.64] - 2026-09-02

### Changed
- Closed issue C4: invalid or incomplete String escape sequences are now classified as lexical errors rather than syntax errors.
- Escape validation is part of String-token lexing: the lexer rejects malformed escape syntax before the parser receives a String token, and the parser does not inspect the interior of a successfully formed String token in order to validate escape syntax.
- The existing Core v0.1 valid escape set is unchanged: `\\`, `\'`, `\"`, `\n`, `\r`, `\t`, `\b`, `\f`, and `\u{HEX}`.
- `\u{HEX}` retains its existing requirements: exactly 1 to 6 hexadecimal digits, and the resulting value must denote a valid Unicode scalar value. Values outside the Unicode scalar-value range and surrogate code points are invalid escapes and produce a lexical error.
- No valid escape sequence is added, removed, or reinterpreted; `\xNN`, octal, `\0`, named Unicode, and interpolation escapes remain unsupported.
- Raw-newline behavior is unchanged: a logical source newline before the matching closing quote in single-quoted and double-quoted String literals remains a lexical error.
- Triple-double-quoted String newline and indentation behavior is unchanged, and triple-double-quoted strings remain non-raw strings using the same escape set.
- String interpolation remains absent, and triple-single-quoted strings remain unsupported.
- No runtime semantics changed: malformed source literals are rejected during lexing, before parser/runtime semantics.

### Unresolved
- C3c (`single-quoted-string`, `double-quoted-string`, `triple-double-quoted-string`) remains unresolved and is unchanged by this revision.
- D3 (triple-double-quoted String indentation details: TAB width, visual columns, TAB-to-SPACE equivalence, how common indentation is computed when SPACE and TAB are mixed, and whether mixed SPACE/TAB indentation is legal) remains unresolved and is unchanged by this revision.

## [0.1.63] - 2026-09-02

### Changed
- Closed issue C3b by giving `custom-binary-operator` a complete normative lexical definition; it is no longer an undefined EBNF reference.
- Formalized the existing custom symbolic operator character alphabet unchanged: `! $ % & * + - / < = > ? @ \ ^ | ~`.
- The normative lexical grammar now defines `operator-character`, `symbolic-operator-spelling`, and `custom-binary-operator`; the candidate maximal symbolic token is a non-empty sequence of consecutive `operator-character` code points.
- Maximal-munch and reserved-spelling classification are formalized without semantic changes: the lexer first forms the longest valid symbolic spelling at a source position, then classifies the complete spelling as a reserved/standard token when it exactly matches a reserved/standard symbolic spelling and as `CUSTOM_OPERATOR` otherwise; a longer custom spelling is never split in order to prefer a shorter reserved/standard token.
- Exact standalone `!` remains prefix logical negation (`not()` lowering) and exact standalone `^` remains non-local return; neither is a `custom-binary-operator`.
- Standard operator spellings (`=>`, `=`, `==`, `===`, `!=`, `!==`, `<=`, `>=`, `&&`, `||`, `+`, `-`, `*`, `/`, `%`, `<`, `>`) and the exact `!` and `^` remain excluded from the custom operator category and keep their dedicated grammar roles and precedence.
- Longer non-reserved symbolic spellings, including those containing or beginning with standard-operator characters, remain custom binary operators where already specified (for example `!!`, `^^`, `!^`, `^!`, `@`, `|>`, `<=>`).
- Precedence and associativity are unchanged: all custom binary operators share one precedence domain, associate left-to-right, and mixing them with standard binary operators without explicit grouping remains a syntax error.
- Structural punctuation and special syntax are unchanged: `.`, `:`, `;`, `,`, `(`, `)`, `{`, `}`, `[`, `]`, and the `...` ellipsis token do not participate in custom operator tokens; decimal-dot, ellipsis, closure `=>`, assignment `=`, and slot-creation `:` behavior are unchanged.
- No runtime semantics changed: message-send lowering, operator dispatch, Boolean laziness, non-local return, and precedence behavior are unchanged.

### Unresolved
- The remaining C3 items (`single-quoted-string`, `double-quoted-string`, `triple-double-quoted-string`, and broader normative-grammar self-containment) remain unresolved and are unchanged by this revision.

## [0.1.62] - 2026-09-02

### Changed
- Closed issue C3a by giving `number-literal` a complete normative lexical grammar; `number-literal` is no longer an undefined EBNF reference.
- The new grammar makes the numeric token families explicit: `decimal-number-literal`, `binary-integer-literal`, `octal-integer-literal`, and `hexadecimal-integer-literal`, with helper productions for decimal digits, digit sequences, fractional parts, exponent parts, radix prefixes, and radix digits.
- Existing numeric syntax was formalized without semantic changes: decimal integers (including leading zeroes), decimal fractional literals, decimal exponent forms, binary/octal/hexadecimal integer literals, and `_` digit separators as previously specified.
- The previously accepted radix prefix case behavior is preserved exactly: `0x`/`0X`, `0b`/`0B`, and `0o`/`0O`.
- Decimal-dot tokenization is unchanged: a `.` belongs to a decimal numeric literal only when immediately followed by a decimal digit, so `1.` remains an integer literal followed by a structural dot and `.5` remains a structural dot followed by an integer literal.
- Malformed numeric continuations remain lexical errors rather than token splitting: `0x`, `0xG`, `0b2`, `0o8`, `2e`, `2e+`, `1__2`, `1_`, `0x_FF`, and `123abc` remain lexical errors.
- Unsupported radix floating-point attempts remain lexical errors: `0b10.5`, `0o17.2`, and `0xFF.1` are not split into radix integer, dot, and integer tokens.
- No runtime semantics changed: numeric value families, literal value production, equality, identity, and arithmetic behavior are unchanged, and no runtime lexing machinery is added.

### Unresolved
- The remaining C3 items (`custom-binary-operator`, `single-quoted-string`, `double-quoted-string`, `triple-double-quoted-string`, and broader normative-grammar self-containment) remain unresolved and are unchanged by this revision.

## [0.1.61] - 2026-09-01

### Changed
- Closed issue C2 by unifying the conflicting `object-body` definitions: the normative grammar now defines `object-body = "{", object-body-sequence, "}"`, and the stale `object-body = "{", expression-sequence, "}"` definition is removed.
- Object bodies use `object-body-sequence`, with `object-body-line-items`, `object-body-line`, `object-body-item`, and `composition-item` productions that mirror the ordinary `expression-sequence` separator structure.
- An object-body item is either an ordinary `expression` or a contextual `composition-item` of the form `...expression`; composition items and ordinary expressions share the same logical-`NEWLINE`/inline-`;` separator rules, and there is no implicit adjacency separator.
- `...expression` remains contextual to object bodies and is not a general expression: it is not added to `expression`, `primary-expression`, or `closure-body`. Closure bodies therefore continue to contain only ordinary expressions, and `() => { ...base }` does not become valid merely because closure bodies use braces.
- No runtime composition semantics changed: conflict handling, binding copying, evaluation order, error behavior, and the existing composition representation are unchanged.

### Unresolved
- Issue D3 remains unresolved and is unchanged by this revision.

## [0.1.60] - 2026-09-01

### Changed
- Resolved newline placement for trailing closures (issue B7): a trailing closure must have no intervening logical `NEWLINE` token after the completed call. `foo() { ... }` attaches the braces as a parameterless trailing closure appended as the final call argument, while `foo()` followed by `{ ... }` on a later source line does not attach them.
- A completed call is syntactically complete, so a following logical `NEWLINE` acts as the ordinary expression separator under the B2 complete-expression newline rule; repeated separating `NEWLINE` tokens (blank lines) under B4 likewise do not permit trailing-closure attachment, and a `;` between the call and the braces does not attach them.
- `{` is not added as a complete-expression newline continuation exception: the only complete-before-newline continuation exception remains the existing leading structural/member `.` rule from revision 55 (B2).
- Horizontal whitespace between the completed call and the closure body remains ordinary lexical separation and is permitted.
- Block comments may appear between the call and the trailing closure, including block comments containing source newlines, because newlines inside a block comment produce no `NEWLINE` tokens (A6). A line comment prevents same-sequence attachment because its terminating newline remains tokenized as a separating `NEWLINE`.
- Indentation plays no role in trailing-closure attachment: the decision concerns logical `NEWLINE` tokens, not physical source formatting.
- The trailing-closure production `trailing-closure = closure-body ;` and the call-suffix form `argument-list, [ trailing-closure ]` are preserved; the no-intervening-`NEWLINE` restriction is stated normatively rather than through new grammar machinery, and no special `same-line` lexical token is introduced.
- B2, B4, A4, and A6 semantics are unchanged, and revision 59 semantics are unchanged: trailing closures remain parameterless and B6 remains closed; `foo() (x) { body }` is still not trailing-closure syntax, and `foo((x) => { body })` remains an ordinary call.
- No runtime mechanism is added: only syntactically attached trailing closures reach trailing-closure lowering.

### Unresolved
- Issue D3 remains unresolved and is unchanged by this revision.

## [0.1.59] - 2026-09-01

### Changed
- Simplified trailing-closure syntax (resolves issue B6): a trailing closure is now always parameterless. `foo(args...) { body }` remains supported and still appends `() => { body }` as the final call argument.
- The parameterized trailing-closure form `foo(args...) (params...) { body }` is removed from Core v0.1: it is no longer recognized as trailing-closure syntax.
- A closure that requires parameters is written as an ordinary explicit closure expression in ordinary call-argument position, for example `items.each((item) => { print(item) })` and `collection.reduce(initial, (acc, item) => { ... })`.
- A trailing closure never has a parameter list. `(x)` remains an ordinary parenthesized expression and `(x) => { body }` remains an ordinary closure expression; there is no third interpretation of `(x)` as trailing-closure parameters.
- B6 is therefore resolved structurally: parentheses are no longer reused as a trailing-closure parameter list, so no parser lookahead, no speculative parsing, and no semantic/type-based disambiguation rule is required for trailing-closure parameters.
- This revision supersedes only the parameterized-trailing-closure portion of revision 54; the parameterless trailing-closure portion of revision 54 is unchanged.

### Unresolved
- Newline placement between a completed call and a trailing closure remains unresolved (issue B7).
- Issue D3 remains unresolved and is unchanged by this revision.

## [0.1.58] - 2026-09-01

### Changed
- Resolved indexed access and assignment versus slot creation (issue B5): slot/member access and indexed access are distinct mechanisms. `object.name` performs ordinary slot lookup, while `object[key]` lowers to the `at(key)` message and is not dynamic slot access.
- `object["foo"]` is not defined to be equivalent to `object.foo`; the two expressions may return completely different values, and an object does not automatically become indexable merely because it has slots.
- Indexable objects remain ordinary objects and may have ordinary slots, methods, delegation, and openness/frozen state; indexed contents and object slots are independent, so `map.description: "users"` and `map["description"] = user` may coexist and refer to entirely different things.
- `:` is specifically the slot-creation operator and can no longer target an index: `object[index]: value`, `object["foo"]: value`, and `object.foo[index]: value` are syntax errors. There is no indexed slot creation and no `atCreate`-style protocol.
- Indexed assignment `object[index] = value` remains valid and lowers to `atPut(index, value)`; the `=` in indexed assignment does not require an already-existing indexed entry, and whether `atPut` creates, replaces, extends, or rejects a missing key/index is defined by the receiver's `atPut` protocol.
- The grammar now distinguishes the legal final targets of `:` and `=`: `slot-creation-target` is a bare identifier or a member target, while `assignment-target` may additionally end in an index suffix. Chained postfix forms whose final operation is a member (for example `object[index].member: value`) remain valid slot-creation targets, and indexed assignment retains its existing evaluation order and written-value result.

### Unresolved
- Issues B6 and D3 remain unresolved and are unchanged by this revision.
- Newline placement between a completed call and a trailing closure, and before a trailing closure's own parameter list, remains unresolved (issue B7).

## [0.1.57] - 2026-09-01

### Changed
- Resolved expression-separation multiplicity and blank-line grammar (issue B4): Core v0.1 has two distinct expression-separation mechanisms — `;` is the inline expression separator between expressions on the same logical source line, and a logical `NEWLINE` is the ordinary separator between expressions on different logical source lines. They are distinct syntactic roles, not interchangeable spellings of one generic separator.
- `;` is a separator, not a terminator: it requires an expression before it and an expression after it on the same logical source line. Leading, trailing, and consecutive semicolons are syntax errors, and a `;` cannot separate an expression from an expression on a following source line; `;\n` is not a redundant separator pair.
- Repeated separating logical `NEWLINE` tokens have the same effect as one separating `NEWLINE`: blank lines are permitted between, before, and after expressions and create no empty expressions, no semantic AST nodes, and no runtime behavior.
- The grammar's `layout` production now permits one or more consecutive logical `NEWLINE` tokens (`newline, { newline }`): blank lines inside open delimited constructs such as argument lists and parameter lists are layout formatting with the same effect as a single layout newline, and never substitute for a required comma.
- The generic `separator = ";" | newline` expression-sequence model is replaced by productions that distinguish same-line `;`-separated expressions from cross-line newline separation; no production permits an optional trailing `;`, and no production creates empty expressions.
- Neither `;` nor a separating, continuation, or layout `NEWLINE` becomes a semantic AST node; expression separation still produces the existing `Sequence(expressions)` representation with strictly left-to-right evaluation.
- The revision 55 newline-continuation rules (B2) and the revision 56 comma-separated list rules (B3) are unchanged: trailing commas remain syntax errors, and newlines remain non-separators inside comma-separated lists.

### Unresolved
- Newline placement between a completed call and a trailing closure, and before a trailing closure's own parameter list, remains unresolved (issue B7).

## [0.1.56] - 2026-09-01

### Changed
- Resolved comma-separated list separators (issue B3): `,` is the only separator between elements of Core v0.1 comma-separated lists, including call arguments and closure parameters.
- A comma is strictly a separator between two list elements; it is not a terminator and does not represent an empty or omitted element. A comma must have a list element on both sides within the same list, so a trailing comma before the closing delimiter is a syntax error.
- A logical `NEWLINE` is no longer an argument or parameter separator. Newlines inside the delimiters of a list are continuation/layout under the revision 55 (B2) rules: formatting within a necessarily-incomplete construct, never a substitute for a required comma.
- Multiline calls and parameter lists remain valid through B2 continuation/layout: elements are separated by commas on their lines, and the closing delimiter may appear on a following line without a trailing comma.
- Removed the `argument-separator = "," | newline` grammar production; argument and parameter items are separated by `,` only, with an optional `layout` continuation-newline helper production for formatting inside the delimiters.
- Indexing is unchanged: Core v0.1 indexing contains one expression, not a comma-separated list, and this revision introduces no multi-index syntax.
- Trailing-closure semantics from revision 54 are unchanged: call arguments and trailing-closure parameters are distinct lists that each follow the comma-only separator rule.
- Commas and continuation newlines are resolved entirely during parsing and introduce no runtime behavior.

### Unresolved
- Separator multiplicity and blank-line grammar remain unresolved (issue B4).
- Newline placement between a completed call and a trailing closure, and before a trailing closure's own parameter list, remains unresolved (issue B7).

## [0.1.55] - 2026-09-01

### Changed
- Resolved newline continuation (issue B2): a logical `NEWLINE` token normally separates expressions when the expression before it may legally end at that point, and does not separate expressions while the syntactic construct before it is necessarily incomplete and requires further input.
- Added the explicit leading-dot postfix continuation: a logical newline immediately before a leading structural `.` continues the preceding postfix/member expression. This is the sole accepted complete-before-newline continuation exception in B2.
- No general leading-operator continuation: a binary or custom symbolic operator at the beginning of the following line does not continue a preceding complete expression.
- Newline continuation is a syntactic/parser rule based on grammatical incompleteness, not on a hard-coded list of token spellings; it is independent of indentation, visual alignment, tab width, and source line-ending spelling.
- No Automatic Semicolon Insertion: the parser decides whether an existing logical `NEWLINE` token separates expressions or is consumed as continuation. An explicit `;` remains an expression separator.
- Newline continuation is resolved entirely during parsing and has no runtime semantic effect; only expressions actually separated by parser-level expression separators become distinct sequence elements.

### Unresolved
- Separators before closing `)` / `]`, trailing commas, and related list-end questions remain unresolved (issue B3).
- Separator multiplicity and blank-line grammar remain unresolved (issue B4).
- Newline placement between a completed call and a trailing closure, and before a trailing closure's own parameter list, remains unresolved (issue B7).

## [0.1.54] - 2026-09-01

### Changed
- Unambiguous trailing-closure call syntax (resolves issue B1): the parentheses of a call always contain call arguments; they are never contextually reinterpreted as the parameter list of a following trailing closure.
- A parameterized trailing closure has its own parameter list placed after the completed call: `foo(args...) (params...) { body }` appends `(params...) => { body }` as the final call argument.
- `foo(args...) { body }` appends a parameterless `() => { body }` closure as the final call argument.
- Fixed the contradictory `items.each(item) { print(item) }` example/desugaring: the form now means one explicit `item` call argument plus a parameterless trailing closure, i.e. `items.each(item, () => { print(item) })`. It is not `items.each((item) => { print(item) })`.
- The parameterized form is written `items.each() (item) { print(item) }`, which desugars to `items.each((item) => { print(item) })`.
- Trailing closures remain ordinary Closure arguments after desugaring: no new runtime value kind and no special runtime trailing-block construct are introduced.
- The object-construction distinction is unchanged: `foo { ... }` creates an object whose parent expression is `foo`; `foo() { ... }` invokes `foo` with a parameterless trailing closure.

### Unresolved
- Newline placement between a completed call and a trailing closure, and before a trailing closure's own parameter list, remains unresolved (issue B7).

## [0.1.53] - 2026-09-01

### Added
- A `/* ... */` block comment is one lexical construct that consumes all source characters from its opening `/*` through its matching closing `*/`; Core v0.1 block comments do not nest and the first `*/` terminates the comment (resolves audit item A6).
- Logical source newlines inside a block comment are consumed as part of the comment: embedded `LF`, `CR`, and `CRLF` emit no `NEWLINE` token. An embedded `CRLF` remains one logical source newline for source-position and logical-line accounting.
- Single-line and multiline block comments have the same token-separation effect: logical newlines inside `/* ... */` cannot themselves separate expressions.
- Newlines outside a block comment remain governed by the revision 51 logical-newline rules.
- `//` line comments are unchanged: they terminate immediately before their terminating logical source newline, which remains available for ordinary `NEWLINE` tokenization.
- Comments remain lexical constructs with whitespace-like token-separation behavior; they do not add code points to the horizontal-whitespace set (revision 52: exactly `SPACE` and `TAB`).

### Unresolved
- Separator multiplicity and blank-line grammar remain unresolved (issue B4).

## [0.1.52] - 2026-09-01

### Added
- Core v0.1 horizontal whitespace is exactly `SPACE` (U+0020) and `CHARACTER TABULATION` (U+0009, TAB); no other code point is horizontal whitespace, and the set does not depend on Unicode whitespace properties or host whitespace classification.
- Outside lexical constructs that consume their own contents, SPACE and TAB are insignificant horizontal whitespace: they separate tokens where separation is required and otherwise emit no parser token.
- Logical source newlines remain a separate lexical category under the revision 51 `LF` / `CR` / `CRLF` rules; they are not horizontal whitespace.
- Other Unicode whitespace-like code points are not implicitly accepted as whitespace: in particular U+000B VERTICAL TAB, U+000C FORM FEED, U+0085 NEXT LINE, U+00A0 NO-BREAK SPACE, U+1680 OGHAM SPACE MARK, U+2000..U+200A Unicode space characters, U+2028 LINE SEPARATOR, U+2029 PARAGRAPH SEPARATOR, U+202F NARROW NO-BREAK SPACE, U+205F MEDIUM MATHEMATICAL SPACE, U+3000 IDEOGRAPHIC SPACE, and U+FEFF ZERO WIDTH NO-BREAK SPACE are not Core v0.1 whitespace (illustrative list, not an open-ended definition).
- A source code point that is neither part of a valid lexical token, nor SPACE or TAB horizontal whitespace, nor a logical source newline, nor consumed inside a lexical construct such as a String or comment is a lexical error; the lexer must not silently discard unknown Unicode whitespace-like or format characters.
- Multiline triple-double-quoted String indentation whitespace consists only of SPACE (U+0020) and TAB (U+0009). Excluded whitespace-like characters remain valid String content where the ordinary String literal rules permit them.
- U+FEFF is not defined as lexical whitespace; source-byte decoding and source-encoding signature behavior remain outside this revision.
- Comments continue to consume their contents according to the existing comment lexical rules.

### Unresolved
- Tab width, visual columns, whether a TAB is equivalent to some number of SPACE characters, and how common indentation is computed when SPACE and TAB are mixed remain open (part of the separate multiline-String indentation question).
- Newline behavior inside `/* ... */` block comments remains unresolved (audit item A6).
- Separator multiplicity and blank-line grammar remain unresolved (issue B4).

## [0.1.51] - 2026-09-01

### Added
- Logical source-newline definition: a logical source newline is exactly one of `LF` (U+000A), `CR` (U+000D), or `CRLF` (U+000D U+000A); `CRLF` is consumed atomically as one logical newline, never two.
- Each logical source newline that is not consumed by another lexical construct produces exactly one `NEWLINE` token for the parser; the parser-level `newline` used in grammar productions denotes this logical `NEWLINE` token.
- Mixed line-ending styles (`LF`, `CR`, and `CRLF`) within one source file are permitted and are not lexical errors.
- `//` line comments terminate immediately before the next logical source newline or at end of file; the terminating logical source newline is not consumed by the comment and remains available for ordinary newline tokenization.
- Single-quoted and double-quoted String literals reject any logical source newline (`LF`, `CR`, or `CRLF`) before the matching closing quote as a lexical error.
- Triple-double-quoted String literals count each logical source newline as one logical newline for structural processing (delimiter placement, content-line splitting, indentation normalization), while retained source newlines preserve their original source code points in the resulting String: `LF` remains U+000A, `CR` remains U+000D, and `CRLF` remains U+000D U+000A; there is no implicit newline normalization of String content.
- Opening/trailing newline removal in triple-double-quoted String literals removes the complete logical newline sequence, including both code points of a removable `CRLF`.
- Newline handling is independent of the host operating system, editor settings, Git line-ending conversion, and host line-separator conventions.

### Unresolved
- Newline behavior inside `/* ... */` block comments remains a separate open question (audit item A6); this revision decides nothing about it.
- Separator multiplicity and blank-line grammar (consecutive or mixed separators) remain a separate open question (issue B4); this revision does not change separator multiplicity.

## [0.1.50] - 2026-09-01

### Added
- Standalone `!` and `^` are standard/reserved symbolic tokens; their existing prefix and non-local-return meanings are unchanged.
- The exact one-character spellings `!` and `^` are not custom binary operators: `a ! b` and `a ^ b` are syntax errors.
- `!` and `^` remain characters in the custom operator alphabet and may participate in longer custom spellings such as `!!`, `^^`, `!^`, and `^!`.
- Symbolic token classification follows maximal munch (longest spelling first) and is independent of parser position; there is no prefix-position exception, so `!!x` tokenizes as `CUSTOM_OPERATOR("!!")` `IDENTIFIER("x")` and is a syntax error.

### Changed
- Reconciled the stale statement that the custom symbolic operator alphabet "remains to be finalized separately" with the already-fixed Core v0.1 alphabet; the alphabet is fixed by the language grammar.

## [0.1.49] - 2026-09-01

### Added
- Radix-Integer-dot boundary: a `.` immediately following a complete radix-prefixed Integer literal is a structural `.` token when not immediately followed by a decimal digit; `0b10.foo` tokenizes as `INTEGER("0b10")` `.` `IDENTIFIER("foo")`. When the `.` is immediately followed by a decimal digit, the source sequence is an attempted unsupported radix Float literal and is a lexical error: `0b10.5`, `0o17.25`, and `0x1.8` are lexical errors rather than being split into `INTEGER` `.` `INTEGER` tokens. The decimal-point vs. member-access lexing rules for decimal literals are unchanged.

## [0.1.48] - 2026-09-01

### Added
- Numeric token termination and malformed numeric boundaries: once a source sequence has begun as a numeric literal, a malformed continuation or an invalid numeric/identifier boundary is a lexical error; the lexer must not split the malformed sequence into otherwise valid tokens in order to recover it.
- Radix prefixes (`0x`, `0X`, `0b`, `0B`, `0o`, `0O`) must be followed by a valid digit for that radix: `0x`, `0xG`, `0b2`, and `0o8` are lexical errors, without fallback to `INTEGER("0")` plus another token.
- An exponent begun by `e` or `E` must be complete: `2e`, `2e+`, and `2e-` are lexical errors.
- Invalid underscore placement inside or immediately adjacent to a numeric literal is a lexical error: `1__2`, `1_`, and `0x_FF`.
- An identifier cannot begin immediately after a numeric literal without a lexical boundary: `123abc` is a lexical error, not `INTEGER("123")` followed by `IDENTIFIER("abc")`.
- Valid token boundaries (punctuation, whitespace, structural delimiters, operators) remain unaffected.

## [0.1.47] - 2026-09-01

### Changed
- Clarified decimal-point vs. member-access dot lexing: a `.` belongs to a decimal numeric literal only when it is immediately followed by a decimal digit. `1.0` is a `Float` literal; `1.` tokenizes as `INTEGER("1")` followed by `.` and `.5` as `.` followed by `INTEGER("5")`; `1.to(10)` tokenizes as `INTEGER("1")` `.` `IDENTIFIER("to")` `(` `INTEGER("10")` `)`. `1.` and `.5` are not numeric literals as complete source sequences; this does not make either sequence necessarily a lexical error — whether the resulting token sequence is syntactically valid is the parser's responsibility.

## [0.1.46] - 2026-08-31

### Added
- Core v0.1 ellipsis token (`...`) definition: single lexical token, greedy recognition, context-dependent meaning.
- Maximal-munch tokenization rule for symbolic operators.
- String escape validation as part of lexical analysis: invalid, incomplete, or unsupported escapes are lexical errors.
- Validation of `\u{HEX}` format and Unicode scalar value constraints.
- Clarification that no new operator semantics are introduced in this revision.

## [0.1.45] - 2026-08-31

### Added
- Core v0.1 newline handling rules for String literals.
- Single-quoted and double-quoted String literals are single-line; raw newlines are lexical errors in these forms.
- Newlines may be represented using `\n` and `\r` escape sequences in single-line literals.
- Triple-double-quoted String literals support raw source newlines as part of multiline content.
- Lexical rules for enforcing single-line constraints on non-multiline String forms.

## [0.1.44] - 2026-08-31

### Added
- Complete Core v0.1 reserved-word set definition: `this`, `context`, `args`, `super`, `true`, `false`, `null`.
- Reserved-word recognition rules: case-sensitive matching after lexical identifier recognition.
- Clarification that prelude names (`Object`, `Future`, `Number`, `String`, `Map`, `IdentityMap`, etc.) are not reserved words.
- Clarification that Core v0.1 does not reserve control-flow or declaration keywords such as `if`, `else`, `while`, `for`, `class`, `function`, `try`, `catch`, `throw`, `async`, or `await`.

## [0.1.43] - 2026-08-31

### Added
- Core v0.1 identifier syntax definition: Unicode-aware, case-sensitive, begin with `_` or `XID_Start`, continue with `XID_Continue`.
- Core v0.1 identifier normalization requirement: all identifiers must be in Unicode NFC form, implementations must reject non-NFC identifiers.
- Clarification that identifier normalization applies to spelling only, not to `String` values.
- Reserved word recognition rules after lexical identifier recognition.

## [0.1.42] - 2026-08-31

### Added
- Core v0.1 numeric literal syntax definition: decimal and radix literals (`0x`, `0b`, `0o`), digit separators, decimal point and exponent handling.
- Core v0.1 prefix operator semantics: prefix `-` lowers to `negated()`, prefix `!` lowers to `not()`, prefix `+` is unsupported, operators apply to arbitrary expressions.

### Changed
- Clarified numeric literal constraints: no leading sign as part of literal, no type suffixes, no `NaN`/`Infinity` literal syntax, unsupported hex/binary/octal Float forms.
- Clarified prefix operator lowering as protocol-based message dispatch rather than privileged intrinsics.

## [0.1.41] - 2026-08-31

### Added
- Core v0.1 string literal syntax: single-quoted, double-quoted, and triple-double-quoted forms with unified escape rules.
- Core v0.1 escape sequences: `\\`, `\'`, `\"`, `\n`, `\r`, `\t`, `\b`, `\f`, and `\u{HEX}`.
- Core v0.1 multiline string indentation normalization rule.
- Core v0.1 comment syntax: `//` line comments, `/* ... */` block comments, no nesting, no `#` syntax, no documentation-comment feature.

### Changed
- Specified constraint: no octal escapes, no `\xNN` escapes, no string interpolation.
- Clarified that Triple-single-quoted strings are not supported.
