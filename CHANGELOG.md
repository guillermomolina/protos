# Protos Implementation Changelog

Current implementation-series changes are kept in this file.

Historical implementation changelogs:

- [0.2.x](changelog/CHANGELOG-0.2.md)
- [0.1.x](changelog/CHANGELOG-0.1.md)

## 0.3.85-SNAPSHOT

- `I068` / PLAT036 Candidate D, Slice 5 ("captured/materialized lexical
  lowering"): statically proven captured lexical bindings now retain their
  canonical owner/depth proof through nested Closure plan construction and
  lower eligible reads through a dedicated frame-native
  `ReadCapturedFrameLocal` operation against the same materialized
  frame-backed lexical authority already projected by the escaped first-class
  execution context. Captured writes resolve and retain their exact lexical
  destination before RHS evaluation, then update that same authoritative
  frame-backed binding through `ResolveCapturedWritableLexicalTarget` /
  `AssignCapturedFrameLocal`; no captured value copy or second authoritative
  store is introduced. Capture remains by reference after the outer activation
  returns, later outer mutation remains visible, lexical depth greater than one
  is preserved, and `PRESENT(null)` remains distinct from `ABSENT`.
  D179 candidate C3 late `ABSENT -> PRESENT` creation remains observable:
  dynamically-created nearer bindings retarget subsequent captured reads and
  writes, while a write whose destination was already resolved before its RHS
  continues to mutate that previously selected destination. Presence-ambiguous
  `Candidate` references and truly `Dynamic` references preserve the existing
  generic/receiver fallback, and Object-construction bodies remain outside the
  genuine lexical execution-context chain. Context-local/source reparsing now
  remaps previously proven captured sites onto fresh canonical AST identities
  using portable source-position/name metadata plus the stable owner local
  layout, without storing Truffle `Frame`, `BytecodeNode`, or other
  Context-owned execution objects in semantic `ProtosClosureValue` state.
  `DUAL_AUTHORITATIVE_COPIES=NO`, `LAZY_CONTEXT_MATERIALIZATION=NO`, and
  observable Protos semantics are unchanged. Debugger/reflection projection
  cleanup remains allocated to Slice 6.

## 0.3.84-SNAPSHOT

- `I068` / PLAT036 Candidate D, Slice 4 ("sequential/default parameter
  lowering"): Closure parameters now participate in the genuine current
  execution-context root's stable Truffle Bytecode DSL `BytecodeLocal` layout.
  Physical local allocation still does not establish semantic presence:
  parameter locals begin cleared and become PRESENT only when the existing
  left-to-right parameter-binding operation successfully creates that binding
  through the single frame-backed lexical authority. Supplied arguments still
  suppress defaults, omitted defaults execute exactly once in the invocation
  activation, earlier parameters are available to later defaults, and current
  or later parameters remain semantically absent during an earlier/default-own
  lookup and therefore retain ordinary fallback behavior. Rest parameters
  continue to receive a fresh frozen Array containing exactly the unconsumed
  supplied suffix, and `PRESENT(null) != ABSENT` remains preserved. Parameter
  reads that are statically `Resolved` in the current activation can now use
  the same direct frame-local read path established by Slice 3, while
  `Candidate`/`Dynamic` lookups remain on the exact generic path. Escaped
  execution contexts continue to project the same authoritative parameter
  values after activation return. Captured/materialized outer lexical lowering
  remains allocated to Slice 5; no second authoritative binding store and no
  lazy execution-context materialization are introduced.

## 0.3.83-SNAPSHOT

- `I068` / PLAT036 Candidate D, Slice 3 ("definitely-current local lowering"):
  the first runtime lexical-authority cutover is now active for statically
  `Resolved` bindings owned by the genuine current execution-context scope
  (`ROOT` / `CLOSURE`). Those bindings receive stable Truffle Bytecode DSL
  `BytecodeLocal` storage and eligible bare reads lower directly through the
  generated local-accessor path instead of
  `Lookup -> ProtosActivation.lookup(String)`. The same frame-backed storage
  remains observable through the first-class `ProtosExecutionContextValue`
  authority seam, including after the context escapes its activation; no
  duplicate map-backed authoritative value copy is maintained for a migrated
  binding. Semantic presence remains independent from physical local
  allocation (`STATIC_LOCAL_EXISTS != SEMANTIC_BINDING_PRESENT`,
  `PRESENT(null) != ABSENT`), and the existing OPEN/CLOSED/FROZEN mutation
  rules plus D179 C3 monotonic membership are preserved. `Candidate` and
  `Dynamic` references retain the exact existing presence/topology and
  receiver/member fallback path, object-construction (`OBJECT_BODY`) slots
  remain ordinary object state, and legacy/internal activations whose current
  lexical context is an ordinary `ProtosObjectValue` retain the historical
  String-keyed lookup path. Closure parameters/default establishment remain
  for Slice 4 and captured/materialized outer lexical access remains for
  Slice 5; lazy execution-context materialization and performance attribution
  remain out of scope. `RUNTIME_AUTHORITY_CUTOVER=CURRENT_RESOLVED_ONLY`.

## 0.3.82-SNAPSHOT

- `I068` / PLAT036 Candidate D, Slice 2 ("frame/context single-authority
  seam"): `ProtosObjectValue`'s local-slot storage is now reached through an
  explicit, backend-private `ProtosLexicalBindingAuthority` seam installed
  once at construction, instead of every operation (`hasLocalSlot`,
  `readLocalSlot`, `localSlotsSnapshot`, `withoutLocalSlot`, `aliasLocalSlot`,
  `composeLocalSlotsFrom`, `lookupSlot`, `readSlot`, `createLocalSlot`,
  `assignLocalSlot`, `removeLocalSlot`) touching a private map field directly.
  The default `ProtosMapBackedLexicalBindingAuthority` is the same ordinary
  `LinkedHashMap` storage used before this slice, installed for both ordinary
  objects and, via a new authority-attachment constructor, for
  `ProtosExecutionContextValue`. No second authoritative binding-value copy
  exists anywhere: each object holds exactly one authority instance backed by
  exactly one map. This establishes the seam a later slice can use to install
  a Truffle Bytecode DSL frame-backed authority for statically admitted
  lexical bindings without changing any caller of these operations. D179
  candidate C3 monotonic-removal rejection, `PRESENT(null) != ABSENT`,
  close/freeze behavior, and delegated lookup through an execution context are
  all preserved exactly. `RUNTIME_AUTHORITY_CUTOVER=NO`.

## 0.3.81-SNAPSHOT

- `I068` / PLAT036 Candidate D, Slice 1 ("canonical binding identity and
  presence metadata"): the Bytecode DSL lowering backend now carries
  statically proven lexical binding identity from canonical analysis into
  lowering, preparing for later frame-backed lexical authority without
  changing any observable behavior. Five new backend-private classes under
  `com.guillermomolina.protos.execution`
  (`CanonicalLexicalScope`, `CanonicalBindingIdentity`,
  `CanonicalBindingResolution`, `CanonicalBindingAnalyzer`,
  `CanonicalBindingAnalysis`) discover, for a canonical subtree, the owning
  lexical scope (module root, Closure activation, or Object-construction
  body) of every bare local creation and closure parameter, and classify
  every bare lexical read/write reference as `Resolved` (guaranteed PRESENT
  in the current scope at this exact program point), `Candidate` (a
  statically known owning scope and lexical depth, without a presence
  guarantee, because a nearer scope may still legally create the same name
  later per D179 candidate C3 monotonic membership), or `Dynamic` (no scope
  in the static chain declares the name anywhere, preserving today's exact
  receiver/member-fallback path unchanged). `CanonicalToBytecodeLowerer`
  computes and caches this analysis for each lowering unit as a side effect
  of `lowerRoot`; it is not yet read by any codegen path, so
  `ProtosActivation`'s existing exact String-keyed lookup/write operations
  remain the sole runtime lexical authority. `RUNTIME_AUTHORITY_CUTOVER=NO`.

## 0.3.80-SNAPSHOT

- `I067` / D179 candidate C3 ("monotonic context membership"): a genuine
  execution context (the object bound to a Closure invocation's fresh
  activation, or to a module's `moduleContext`) now rejects `removeSlot(name)`
  whenever `name` currently identifies one of its local slots, regardless of
  whether the execution context is open, closed, or frozen. The new
  `ProtosExecutionContextValue` runtime family (a `ProtosObjectValue`
  subclass) overrides `removeLocalSlot` to reject removal of a present local
  slot and otherwise defer to the ordinary contract; `ProtosPrelude
  .newExecutionContext()` is the sole factory for this family and now returns
  it. The rejection reuses the existing generic structural-mutation `Error`
  already raised by `ProtosStandardObjectProtocol.removeSlot` for every other
  removal failure; no new Error family or public selector was introduced.
  `Object.removeSlot(name)` on ordinary objects, including the object under
  construction that temporarily serves as an object-literal body's
  slot-creation context, is unaffected. Late absent-to-present local
  creation while open, present-to-present value mutation while writable,
  closure capture-by-reference, context escape, `close()`, `freeze()`, and
  present-`null`-distinct-from-absent semantics are all unaffected.

## 0.3.79-SNAPSHOT

- `PERF010-A`: replace the `fastOrdinarySend` cache guard's `closure`/
  `methodHome` object-identity check with a `ProtosClosureValue.definition()`
  identity check in `ProtosBytecodeRootNode.PrepareSendArguments`. The
  selected Closure and its `methodHome` are legitimately fresh runtime
  objects on every invocation that re-executes the Source producing them
  (a re-materialized Closure literal, a freshly constructed receiver), even
  when D013 keeps selecting the same executable behavior; guarding on their
  object identity consumed a fresh cache entry per execution and, after the
  cache limit, permanently generalized the call site to the generic
  `perform` fallback (the PERF010-A stable-specialization-identity-churn
  root cause). The effective Context-owned Bytecode activation target
  depends only on the selected Closure's immutable `CanonicalClosure`
  definition and the entered `ProtosLanguageContext`, never on the selected
  Closure or `methodHome` instance, so keying the cache on that definition
  instead keeps the fast hit stable across fresh materializations of the
  same executable behavior while remaining exactly as discriminating
  whenever D013 genuinely selects a different Closure. The currently
  selected Closure and `methodHome` are still bound fresh on every hit and
  flow into a fresh `ProtosActivation` exactly as before; only the cache
  guard identity changes. D013 re-lookup per invocation, mutation
  visibility/shadowing/delegation, and every other preserved PERF010-A
  invariant are unaffected.

## 0.3.78-SNAPSHOT

- `PERF010-A`: add a prepared Context-owned target specialization for
  `ProtosBytecodeRootNode.PrepareSendArguments`, the exact hot caller
  identified by the retained PERF010-A causal evidence as compiler-stranded
  behind generic ordinary-send preparation. A new `fastOrdinarySend`
  specialization re-runs authoritative D013 lookup on every hit and, only
  when the selector, selected Closure identity, `methodHome` identity, and
  entered `ProtosLanguageContext` identity all match a cached hit, reuses an
  effective Context-owned Bytecode activation target materialized once at
  cache-population time; it builds a fresh activation/invocation on every
  call as before. This keeps the fast hit from calling
  `ProtosStandardImportProtocol.selectedRuntimeForBytecodeIntrinsic`/
  `prepareBytecodeImport` and `ProtosLanguageContext.
  bytecodeExecutionPlanForDefinition`'s `sharedBytecodeExecutionPlans.
  computeIfAbsent(...)`, both of which the retained evidence traces directly
  into the caller helper's permanent compiler bailout, for a proven
  non-native, source-backed ordinary Closure that can never reach either
  branch. Any native Closure, standard-import selection, Closure/home/
  selector/Context mismatch, or unsupported representation falls through to
  the exact existing generic `perform` path, which is unchanged in
  behavior (only its lookup logic is now shared via the extracted
  `performOrdinarySendLookup` helper). This is a bounded implementation
  experiment: it does not itself establish PERF010-A's dominant cause or
  attributable fraction, and no production optimization is selected by this
  change alone.

## 0.3.77-SNAPSHOT

- `TOOL009-B`: final legacy Test Tool migration cleanup. Repository production
  execution is now logical-only end to end:
  - `RepositorySuite` execution no longer partitions a corpus's TestPlan into
    a legacy Runner plan and suite-native logical Cases: `Main.protos`
    instead validates, per Case, that every registered production Case is
    `suite-native` and fails closed otherwise. `Runner.runD108WithResources`
    is no longer called from repository production, `completedRuns`/
    `primaryRun` reconciliation across D108 runs is gone, and the final
    `TestRunOutcome` is derived solely from the current Logical Case result
    projection (`LogicalCaseResult.exitCode`) rather than from a legacy/
    logical outcome merge. `Runner.runD108WithResources` and the rest of the
    D108/D114/D116 infrastructure-outcome machinery remain fully intact and
    independently authoritative for their own callers.
  - `LogicalCaseMigration.protos` (the mixed-ownership partition/merge
    bridge: `splitPlan`, `legacyPlan`, `suiteNativeSpecs`,
    `isSuiteNativeSpec`, `neutralLegacyOutcome`, `mergeOutcome`) is removed.
    Its two still-authoritative helpers, `sourceAssociation` and
    `logicalCaseExecutorAsync`, are re-homed to a new small current-owner
    module, `protos/tools/test/LogicalCaseDispatch.protos`, unchanged in
    behavior.
  - The legacy repository lifecycle/display adapter is removed:
    `Main.protos` no longer builds a `legacyCaseDisplayReference` or a
    `legacyLifecycleObserver`; the invocation-wide D174 lifecycle tracker now
    has exactly one observer, the logical one.
  - The whole-source project-tree `CaseAuthority` execution path is removed:
    `ProtosTestCaseAuthorityExecutionScope`,
    `ProtosTestCaseAuthorityExecutionFacility`,
    `ProtosTestCaseAuthorityAttemptBridge` and
    `ProtosTestCaseAuthorityAttemptCompletion` are deleted, along with the
    five `caseAuthorityExecutionAsync` project-tree corpus bootstrap slots
    and `ProtosTestCorpusRegistry`'s dedicated project-tree binding helper
    (a project-tree corpus binding is now the same shape as a case-outcomes
    binding: `filesystem`/`planLoader`/`caseNamespace`). The two still-live
    D133/D134 primitives this path owned, project-tree CaseAuthority
    descriptor validation and trusted-root confinement resolution, are
    re-homed as `ProtosTestLogicalCaseAttemptBridge.fixtureIdentity` and
    `ProtosTestLogicalCaseAttemptBridge.resolveAuthorityRoot`, the current
    authoritative owner of D133/D134 physical project-tree authority for
    suite-native Logical Case execution.
    `ProtosTestToolAsyncExecutionScope.installWithCaseAuthorities` is renamed
    to `installWithProjectTreeAuthorities`, reflecting that it no longer
    installs any `CaseAuthority` execution scope.
  - Repository-suite-native execution's current route is otherwise
    unchanged: `CorpusBinding`/`planLoader` → suite-native `TestPlan`
    validation → fail-closed native-plan validation → source associations →
    Logical Case discovery → `CasePlan` → `ExecutionRequirementId` →
    `logicalCaseExecutionAsync` → `LogicalCaseRunner` → `LogicalCaseResult` →
    logical-only final `TestRunOutcome` → `Progress.finishInvocation`. The
    Package project-tree suite-native route
    (`protos/test/package` → Package `logicalCaseExecutionAsync` →
    `sourceAssociation` authority descriptor → trusted `casesRoot` →
    physical project-tree authority → `projectTreeFilesystem` → selected
    `Test.call()`) is unaffected and has no dependency on the removed
    whole-source `CaseAuthority` execution facility.
  - Retained unchanged and independently authoritative: D108/D114/D116
    outcome/fail-stop machinery and the legacy expectation engine; Manifest
    `true`/`error` compatibility for `packageTomlCaseSpec`/
    `caseOutcomeSpec`/`projectTreeCaseSpec`; the D125/D077/D108
    `executionAsync`/`executionInspectAsync`/`resourceExecutionAsync`/
    `resourceExecutionInspectAsync` APIs; Process Snapshot; and the ordinary/
    Actor/Group/Package/Package-project-tree Logical Case execution
    facilities.

## 0.3.76-SNAPSHOT

- `TOOL009`: migrate the complete 54-case Package Tool project-tree corpus
  (`protos/tests/package-tool/content-identity` (12),
  `protos/tests/package-tool/resolution-input-lock` (2),
  `protos/tests/package-tool/resolution-root` (8),
  `protos/tests/package-tool/execution-plan` (28),
  `protos/tests/package-tool/project-projection` (4)) from the legacy
  project-tree `CaseAuthority` execution path to suite-native Logical Case
  execution (Publication 2 of the Package non-TOML migration), completing the
  Package non-TOML migration alongside Publication 1's 157 case-outcomes
  cases.
  - Adds the previously missing project-tree-aware Package Logical Case
    authority adapter: `ProtosTestLogicalCaseAttemptBridge` optionally
    provisions a fresh read-only physical project-tree authority (reusing
    `ProtosTestCaseAuthorityAttemptBridge`'s trusted confinement mechanics)
    and installs it under the `projectTreeFilesystem` slot the declaration's
    module scope closes over, before Discovery/selection run and the
    selected Test's `call()` executes — all inside the same fresh Process.
    `ProtosTestLogicalCaseExecutionFacility` gains a `Map<corpusId, casesRoot>`
    parameter (empty for the ordinary/Actor/Group installations, populated
    for the Package-flavored installation) and decodes an optional third
    `sourceAssociation` element carrying the D133 project-tree CaseAuthority
    descriptor. This stays the ordinary four-argument suite-native Logical
    Case protocol; the descriptor travels inside the single
    `sourceAssociation` argument. `ProtosTestLogicalCaseDiscoveryFacility`,
    the separate authority-free discovery boundary that runs earlier in the
    same pipeline, is extended the same way: it now accepts an optional
    third `sourceAssociation` element and preserves it opaquely (without
    interpreting it) into every discovered CasePlan entry's own
    `sourceAssociation`, so the descriptor a project-tree spec supplies at
    discovery time is still present when that Case later reaches execution.
  - `LogicalCaseMigration.sourceAssociation` now appends the CaseAuthority
    descriptor as a third element when a suite-native spec carries one, so
    distinct project identities that reuse the same fixture source path
    remain distinct logical records; `splitPlan` now accepts a well-formed
    project-tree/case/case descriptor alongside `suite-native` instead of
    rejecting it; `logicalCaseExecutorAsync` forwards a 2- or 3-element
    association unchanged.
  - `Manifest.protos`'s `projectTreeCaseSpec` now also accepts the
    `suite-native` outcome marker (normalized to `expectation =
    "suite-native"`, `expected = "-"`), alongside the already-supported
    `true`/`error` markers, while still attaching the D133 CaseAuthority
    descriptor.
  - `Main.protos`'s suite-native discovery bookkeeping is keyed by
    `(sourcePath, fixtureIdentity)` rather than `sourcePath` alone, so
    project-tree fixture scripts reused across distinct project identities
    (for example `execution-plan`'s shared `f2e3b-build-v2-error.protos`)
    are discovered and re-read independently per project identity instead of
    colliding.
  - The 40 distinct project-tree fixture scripts are rewritten from the
    legacy whole-source boolean/error convention into the `std:test/Test`
    suite-native convention (`tests: [Test("<name>", () => { ... })]`),
    preserving each fixture's original assertions verbatim: a `true`
    expectation's trailing completion expression is wrapped in
    `Assertions.require(...)`; an `error` expectation's whole body is
    wrapped in `Assertions.signals(Error, () => { ... })`. The five
    `manifest.tsv` files are updated to the `suite-native` outcome marker.
  - `ProtosTestCaseAuthorityAttemptBridge.resolveAuthorityRoot` and
    `ProtosTestCaseAuthorityExecutionFacility.fixtureIdentity` are widened
    from `private` to package-private so the new adapter reuses the exact
    same confinement/descriptor-validation logic rather than duplicating it.
    TOOL009-B legacy cleanup (removing the now-unused legacy CaseAuthority
    scope/facilities once no CaseSpec references them) remains out of scope
    for this publication.

## 0.3.75-SNAPSHOT

- `TOOL009`: migrate the complete 157-case-outcomes Package non-TOML corpus
  (`protos/tests/package-tool/version` (74), `protos/tests/package-tool/lock`
  (71), `protos/tests/package-tool/resolution-input` (12)) from the legacy
  Test Tool execution path to suite-native Logical Case execution
  (Publication 1 of the Package non-TOML migration).
  - `protos/tools/test/Manifest.protos`'s generic two-column
    `caseOutcomeSpec`/`loadCaseOutcomes` loader now also accepts the
    established `suite-native` outcome marker (normalized to
    `expectation = "suite-native"`, `expected = "-"`), alongside the
    already-supported `true`/`error` markers. The loader remains generic
    and Package-Tool-name-free; the change is the same normalization
    `packageTomlCaseSpec` already performs for the Package TOML corpus.
  - The Package-flavored suite-native logical Case fallback resolver
    (`packageLogicalCaseFallbackResolver` in `ProtosCli`) is extended with
    seven finite exact overlays for the real
    `self:ReleaseVersion`/`self:DependencyConstraint`/
    `self:FreshVersionSelection`/`self:RetainedVersionSelection`/
    `self:LockSyntax`/`self:LockDocument`/`self:ResolutionInput` modules
    under `protos/tools/package/`, following the same finite exact-overlay
    strategy already published for the Package TOML module graph. Their
    shared `std:collections/Array` and `std:crypto/SHA256` dependencies
    already resolve through the standard library resolver and need no
    overlay. Generic `ProtosBundledToolModuleResolver` closure guards and
    ambient Package-directory search remain unchanged.
  - `ProtosPackageTestLogicalCaseExecutionFacilityTest` gains matching
    resolver overlay coverage plus three new focal tests proving real
    execution through the new graph: `RetainedVersionSelection ->
    FreshVersionSelection -> DependencyConstraint -> ReleaseVersion`,
    `LockDocument -> LockSyntax -> ReleaseVersion`, and `ResolutionInput ->
    LockSyntax / ReleaseVersion` plus the required `std:collections/Array`
    and `std:crypto/SHA256` standard library dependencies.
  - All 157 `.protos` fixtures (74 `version`, 71 `lock`, 12
    `resolution-input`) now declare `std:test/Test` cases using
    `std:test/Assertions.require(...)` (former `true` expectation) or
    `std:test/Assertions.signals(Error, ...)` (former `error`
    expectation), preserving the exact behavior each fixture tests inside
    the selected Test body. Each fixture's `self:` imports are declared
    inside the selected Test's closure body rather than at module top
    level, for the same discovery/execution resolver-boundary reason
    already recorded for the `0.3.74-SNAPSHOT` Package TOML migration.
  - The three corpora's `manifest.tsv` files now record `suite-native` for
    all 157 entries (`CASE_OUTCOMES_SUITE_NATIVE=157`,
    `CASE_OUTCOMES_LEGACY=0`), with row count, row order, source path, and
    case namespace preserved.
  - The 54 `project-tree` Package Tool cases
    (`content-identity`/`resolution-input-lock`/`resolution-root`/
    `execution-plan`/`project-projection`) intentionally remain on the
    legacy Runner/CaseAuthority path (`PROJECT_TREE_LEGACY=54`); they
    require a project-tree-aware Package Logical Case authority adapter
    (Publication 2) and are out of scope for this change.
    `LogicalCaseMigration`/`splitPlan`/`legacyPlan`/`suiteNativeSpecs`/
    `Runner.runD108WithResources` and the other legacy mixed-ownership
    infrastructure remain production-live and unremoved; TOOL009-B legacy
    cleanup remains blocked until Publication 2 also completes.

## 0.3.74-SNAPSHOT

- `TOOL009`: migrate the complete 102-case Package Tool TOML corpus under
  `protos/tests/package-tool/toml-syntax` from the legacy Test Tool
  execution path to suite-native Logical Case execution, using the
  Package-flavored resolver coverage published in `0.3.73-SNAPSHOT`.
  - All 102 `.protos` fixtures (27 `TomlSyntax`, 31 `TomlDocument`, 44
    `ManifestSchemaV1`) now declare `std:test/Test` cases using
    `std:test/Assertions.require(...)` (former `true` expectation) or
    `std:test/Assertions.signals(Error, ...)` (former `error`
    expectation), preserving the exact behavior each fixture tests
    inside the selected Test body.
  - Each fixture's `self:TomlSyntax` / `self:TomlDocument` /
    `self:ManifestSchemaV1` import is declared inside the selected
    Test's closure body rather than at module top level. The Test Tool
    CLI's suite-native discovery boundary
    (`ProtosTestLogicalCaseDiscoveryFacility`) is flavor-agnostic and
    installed once with the ordinary, non-overlaid Test Tool fallback
    resolver; it fully evaluates a suite's module source to read its
    `tests` declaration, before any flavor-specific (Package/Actor/
    Group) execution resolver is ever consulted. A module-top-level
    `self:` import is therefore unresolvable at discovery time for this
    corpus, even though the already-published Package-flavored
    execution resolver overlay resolves it correctly once a selected
    Test is actually invoked. Group/Actor's suite-native corpora never
    surfaced this discovery/execution resolver-boundary seam because
    their flavor-specific dependency (`workers`) is referenced only
    from inside a Test body at runtime, never as a module-top-level
    import. Moving the import inside the Test closure (syntactically
    ordinary, since `import(specifier)` is an ordinary call expression
    per `spec/PROTOS_GRAMMAR.md`, not top-level-only syntax) defers its
    resolution to execution time, matching the pattern Group/Actor
    already rely on; this is a fixture-authoring adjustment, not a
    resolver or discovery-facility change, and
    `ProtosTestLogicalCaseDiscoveryFacility`'s generic wiring is
    unchanged.
  - `manifest.tsv` now records `suite-native` for all 102 entries
    (`LEGACY_TRUE_METADATA_REMAINING=0`,
    `LEGACY_ERROR_METADATA_REMAINING=0`).
  - `protos/tools/test/Manifest.protos`'s `packageTomlCaseSpec` (the
    two-column Package TOML manifest parser) now recognizes
    `suite-native` as a third valid `expectation`/`expected` pair
    (`"suite-native"`/`"-"`), alongside its existing `true`/`error`
    handling, so `Manifest.caseExpectation(...) == "suite-native"`
    routes these cases through the already-published
    `LogicalCaseMigration` suite-native dispatch. The `true`/`error`
    handling is unchanged.
  - Two pre-existing legacy exact-execution infrastructure fixtures
    (`protos/tests/tooling/tool002-e2a1-package-resolved-execution.protos`
    and
    `protos/tests/tooling/tool002-e2a2b-package-failed-fixture.protos`)
    directly executed raw source read from
    `key-bare-dotted.protos`/`invalid-key-error.protos` in the corpus,
    which no longer evaluate to a bare boolean/signal now that those
    files are suite-native Test declarations. Both fixtures now execute
    a dedicated inline source string reproducing the exact former
    corpus behavior, decoupling this legacy-plumbing infrastructure
    check from the now-migrated corpus content; the corresponding Java
    tests (`ProtosTestToolPackageExecutionEnvironmentTest`,
    `ProtosTestToolPackageFailedExecutionTest`) are unchanged.
  - `protos/tests/tooling/tool002-e1b-package-toml-filesystem.protos`
    (which asserts on the real corpus's first manifest row) now expects
    `caseExpectation == "suite-native"` / `caseExpected == "-"` instead
    of `"boolean"`/`"true"`, matching the migrated
    `key-bare-dotted.protos` entry.
  - This migration does not modify Java production code, does not
    remove legacy Test Tool infrastructure
    (`LogicalCaseMigration`/`splitPlan`/`legacyPlan`/`mergeOutcome`, the
    D108 Runner route, or the `packageExecutionAsync` family), and does
    not change `D108`/`D152`/`D153`/`D178`/Case Authority semantics.
    Global legacy-infrastructure liveness reconciliation remains
    separate follow-up work (`TOOL009-B` / #685).

## 0.3.73-SNAPSHOT

- `TOOL009`: complete the Package-flavored suite-native logical Case
  resolver coverage required by the real Package Tool TOML module graph.
  This is a bounded prerequisite for migrating the Package Tool TOML
  corpus; the 102 TOML manifest fixtures under
  `protos/tests/package-tool/toml-syntax` are not migrated by this change.
  - The previously published `packageLogicalCaseFallbackResolver`
    (`0.3.72-SNAPSHOT`) overlaid only `package-runtime-names`, a
    dedicated proof-of-bootstrap module. It did not resolve the actual
    modules the TOML corpus imports: `self:TomlSyntax`,
    `self:TomlDocument`, and `self:ManifestSchemaV1`. A corpus-loaded
    suite is materialized through `ProtosDirectFileModuleResolver` and
    therefore carries a `direct-file:` `ProtosModuleKey`, which never
    satisfies `ProtosBundledToolModuleResolver`'s `self:`/`tool-shared:`
    importer-closure guards, so every one of those imports failed.
  - `ProtosCli`'s `packageLogicalCaseFallbackResolver` now also overlays
    the finite Package TOML module graph as exact specifiers, each with
    an explicit canonical `ProtosModuleKey` and exact source path:
    `self:TomlSyntax`, `self:TomlDocument`, `self:ManifestSchemaV1`
    (the three Package-owned modules, keyed under a `tool001-package:`
    namespace consistent with `package-runtime-names`), and
    `tool-shared:Toml10/TomlSyntax` /
    `tool-shared:Toml10/TomlDocument` (the shared Toml10 modules those
    modules depend on, keyed with the same `bundled-tool-shared:`
    canonical identity `ProtosBundledToolModuleResolver` would itself
    assign for the same specifier, so the shared modules keep one single
    canonical identity regardless of which resolution path reaches
    them). `ProtosExactModuleOverlayResolver` matches on the literal
    import specifier before any importer-closure check runs, so this
    closes the gap without relaxing
    `ProtosBundledToolModuleResolver`'s generic `self:`/`tool-shared:`
    closure guards for any other consumer. `packagePrelude` and the
    legacy `packageExecutionAsync` facility are unchanged.
  - Strengthened `ProtosPackageTestLogicalCaseExecutionFacilityTest` with
    focal coverage that resolves and executes the real
    `protos/tools/package/TomlSyntax.protos`,
    `protos/tools/package/TomlDocument.protos`, and
    `protos/tools/package/ManifestSchemaV1.protos` modules (not copies
    embedded in the test) from a direct-file suite, proving: `self:
    TomlSyntax` resolves its `tool-shared:Toml10/TomlSyntax` dependency;
    `self:TomlDocument` resolves its `tool-shared:Toml10/TomlDocument`
    dependency, which itself resolves `tool-shared:Toml10/TomlSyntax`;
    and `self:ManifestSchemaV1`'s `self:TomlDocument` import, which is
    lazy (declared inside the `parseBase` callable body rather than at
    module top level), actually resolves when the selected Test body
    calls `Schema.parseBase(...)`. Each case asserts the real parsed
    TOML structure, not merely that a module loaded.
  Implementation version becomes `0.3.73-SNAPSHOT`.

## 0.3.72-SNAPSHOT

- `TOOL009` (GitHub #692 infrastructure): add the Package-flavored
  suite-native logical Case execution route so `protos/test/package` can
  reuse the same `ProtosTestLogicalCaseExecutionFacility` /
  `ProtosTestLogicalCaseAttemptBridge` machinery the ordinary/Actor/Group
  routes already use, without touching Package Tool's own bootstrap. This
  is plumbing only: the Package Tool corpus (102 TOML manifest fixtures
  under `protos/tests/package-tool/toml-syntax`) is not migrated by this
  change and remains entirely legacy-shaped.
  - `ProtosCli` now builds a `packageLogicalCaseFallbackResolver`: the
    same ordinary bundled Test Tool fallback resolver the ordinary/
    Actor/Group routes already use as their base (the suite-native
    bridge's own selection machinery resolves `self:Discovery` against
    that root regardless of flavor), overlaid with one host-selected
    exact reference to Package Tool's own `RuntimeNames.protos` module
    (specifier `package-runtime-names`) so a selected Test body can prove
    it resolved the Package-flavored bootstrap rather than the plain
    unoverlaid ordinary resolver. `packagePrelude` and the legacy
    `packageExecutionAsync` facility, which resolve against
    `packageToolRoot` directly, are unchanged.
  - `ProtosTestToolAsyncExecutionScope` installs a fourth
    `ProtosTestLogicalCaseExecutionFacility` instance under the new
    `packageLogicalCaseExecutionAsync` bootstrap slot
    (`PACKAGE_LOGICAL_CASE_EXECUTION_BOOTSTRAP_SLOT`), distinct from the
    ordinary, Actor- and Group-flavored slots so all four routes coexist;
    each selected Test Case still receives a fresh Process/Prelude per
    attempt.
  - `ProtosTestExecutionRequirementRegistry` now publishes
    `logicalCaseExecutionAsync` on the `protos/test/package` D125 binding,
    reusing the newly installed facility rather than provisioning a
    parallel one. The legacy `packageExecutionAsync` /
    `packageExecutionInspectAsync` / `packageResourceExecutionAsync` /
    `packageResourceExecutionInspectAsync` bindings are unchanged and
    remain fully functional (TOOL009-B legacy removal is out of scope).
  - Added `ProtosPackageTestLogicalCaseExecutionFacilityTest`, dedicated
    inline-fixture focal coverage demonstrating the Package Tool
    `RuntimeNames` overlay resolves inside a selected Test body, selected
    `Test.call()` authority (module declaration completing does not make
    a failing selected Test pass), exactly-once execution, correct
    multi-Test selector resolution, a fresh Process per Case with no
    state leaking between Cases, and rejection (without executing the
    body) on selector and signature mismatch.
  - Updated `ProtosTestToolExecutionRequirementRegistryTest` and
    `ProtosTestToolH2B3PublicIntegrationTest` for the new
    `packageLogicalCaseFallbackResolver` parameter and the
    `protos/test/package` binding now carrying
    `logicalCaseExecutionAsync`.
  Implementation version becomes `0.3.72-SNAPSHOT`.

## 0.3.71-SNAPSHOT

- `TOOL009-B` (GitHub #685): remove the unused
  `LogicalCaseMigration.selectSuiteNativeSpecs` migration helper and its
  migration-only characterization coverage in
  `protos/tests/tooling/tool009-logical-case-migration.protos`. The global
  reconciliation for TOOL009-B established that this helper had zero
  production consumers while every other `LogicalCaseMigration` slot
  (`splitPlan`, `legacyPlan`, `suiteNativeSpecs`, `sourceAssociation`,
  `logicalCaseExecutorAsync`, `mergeOutcome`, `neutralLegacyOutcome`) remains
  required by the hybrid `Main.protos` execution path while Package Tool
  legacy corpora still generate legacy `CaseSpec`s. Drop the now-unused
  `FileSelection` import this helper alone required, and correct the stale
  module-header claim that "M3/M4 must remove this module": the module
  remains production infrastructure and is not scheduled for automatic
  removal. This does not remove or simplify the legacy execution path, the
  D108 Runner, the expectation engine, or any host execution facility.
  Implementation version becomes `0.3.71-SNAPSHOT`.

## 0.3.70-SNAPSHOT

- TOOL009-C Slice 4 (#686): add a Group-flavored suite-native logical
  Case execution route and migrate all 10
  `protos/tests/conformance/group` fixtures to it, reusing the same
  `ProtosTestLogicalCaseExecutionFacility` /
  `ProtosTestLogicalCaseAttemptBridge` machinery the Actor route (Slice
  3) already established rather than a parallel implementation.
  - `ProtosCli` now builds a `groupLogicalCaseFallbackResolver`
    (ordinary bundled Test Tool fallback resolver overlaid with the
    Group-specific `tool002-group:workers` module, exactly as
    `groupPrelude` already resolves it) and passes it to
    `ProtosTestToolAsyncExecutionScope.installWithCaseAuthorities`.
  - `ProtosTestToolAsyncExecutionScope` installs a second
    `ProtosTestLogicalCaseExecutionFacility` instance under the new
    `groupLogicalCaseExecutionAsync` bootstrap slot
    (`GROUP_LOGICAL_CASE_EXECUTION_BOOTSTRAP_SLOT`), distinct from the
    ordinary and Actor-flavored slots so all three routes coexist; each
    selected Test Case still receives a fresh Process/Prelude per
    attempt.
  - `ProtosTestExecutionRequirementRegistry` now publishes
    `logicalCaseExecutionAsync` on the `protos/test/group` D125
    binding, reusing the newly installed facility rather than
    provisioning a parallel one. The legacy `groupExecutionAsync` /
    `groupExecutionInspectAsync` / `groupResourceExecutionAsync` /
    `groupResourceExecutionInspectAsync` bindings are unchanged and
    remain fully functional (TOOL009-B legacy removal is out of scope).
  - Added `ProtosGroupTestLogicalCaseExecutionFacilityTest`, dedicated
    inline-fixture focal coverage demonstrating the Group "workers"
    module overlay resolves inside a selected Test body, selected
    `Test.call()` authority, exactly-once execution, a fresh
    Process/Group per Case with no state leaking between Cases,
    intra-Case Group state across multiple requests within one Case,
    and rejection (without executing the body) on selector and
    signature mismatch.
  - Migrated all 10 `protos/tests/conformance/group` fixtures from the
    legacy manifest-driven expectation model (`boolean`/
    `future-integer`/`future-integer-one-of`/`future-boolean`/`error`
    kinds) to suite-native `Test(...)` declarations, so
    `group/manifest.tsv` now records all 10 entries as `suite-native`.
    Original fixture bodies, license headers, and observable semantics
    (member readiness, eligible-member selection, argument
    snapshotting, GroupRef identity/transfer, and routing after member
    termination) are unchanged: the 2 former `boolean`-kind cases wrap
    their body in a `tool009LegacyCase` closure asserted with
    `Assertions.require(tool009LegacyCase() === <expected>)`; the 3
    former `future-integer`-kind cases follow the same pattern with
    `Assertions.require(tool009LegacyCase().value() == N)`; the 1
    former `future-boolean`-kind case follows the same pattern with
    `Assertions.require(tool009LegacyCase().value() === true)`; the 1
    former `future-integer-one-of`-kind case
    (`request-selects-one-eligible-member.protos`) preserves its exact
    `1,2` acceptable-result set with
    `Assertions.require((value == 1) || (value == 2))`; the 3 former
    `error`-kind cases wrap their body in
    `Assertions.signals(Error, ...)`. Each fixture remains an
    independent physical `.protos` source with exactly one `Test`; no
    fixtures were merged or regrouped. Process, Actor, D152, D153,
    D178, and TOOL009-D are unmodified; legacy Group execution
    infrastructure (`groupExecutionAsync` / `groupExecutionInspectAsync`
    / `groupResourceExecutionAsync` / `groupResourceExecutionInspectAsync`)
    is not removed (TOOL009-B, out of scope).
    - `ProtosExactExecutionFacilityTest` exercised the legacy
      whole-source inspection route directly against
      `request-selects-one-eligible-member.protos`'s raw file content;
      since that file's module-level completion is no longer a bare
      Future after migration, the test now holds the original fixture
      body as an inline `GROUP_REQUEST_CASE_SOURCE` Java string
      constant instead of reading it from disk, decoupling it from the
      corpus file's new suite-native purpose while preserving its
      original assertions unchanged. No production/`src/main` or
      `protos/lib` source was modified by this decoupling.

## 0.3.69-SNAPSHOT

- TOOL009-C Slice 3 (#686): migrate all 11
  `protos/tests/conformance/actor` fixtures from the legacy
  manifest-driven expectation model (`boolean`/`future-integer`/
  `future-boolean`/`error` kinds) to suite-native `Test(...)`
  declarations, so `actor/manifest.tsv` now records all 11 entries as
  `suite-native` and each fixture routes through
  `protos/test/actor`'s `actorLogicalCaseExecutionAsync` (the Slice 3
  infrastructure facility, itself unmodified) instead of the legacy
  whole-source completion route. Original fixture bodies, license
  headers, and observable semantics are unchanged: the 1 former
  `boolean:true`-kind case and the 4 former `future-boolean:true`-kind
  cases wrap their body in a `tool009LegacyCase` closure asserted with
  `Assertions.require(tool009LegacyCase() === true)` (the `future-*`
  cases additionally observe the returned Future with `.value()` before
  the assertion, per the Future-observation authority the migration is
  required to preserve); the 4 former `future-integer`-kind cases follow
  the same pattern with `Assertions.require(tool009LegacyCase().value()
  == N)`; the 2 former `error`-kind cases wrap their body in
  `Assertions.signals(Error, ...)`. Each fixture remains an independent
  physical `.protos` source with exactly one `Test`; no fixtures were
  merged or regrouped. Process, Group, D152, D153, D178, and TOOL009-D
  are unmodified; legacy Actor execution infrastructure
  (`actorExecutionAsync` / `actorExecutionInspectAsync` /
  `actorResourceExecutionAsync` / `actorResourceExecutionInspectAsync`)
  is not removed (TOOL009-B, out of scope).
  - `ProtosExactExecutionFacilityTest` exercised the legacy whole-source
    inspection route directly against `spawn-request-echo.protos`'s and
    `ip-data-transfer.protos`'s raw file content; since those files'
    module-level completion is no longer a bare Future after migration,
    the test now holds the original fixture bodies as inline
    `ACTOR_REQUEST_CASE_SOURCE` / `ACTOR_CHAIN_CASE_SOURCE` Java string
    constants instead of reading them from disk, decoupling it from the
    corpus files' new suite-native purpose while preserving its original
    assertions unchanged. No production/`src/main` or `protos/lib`
    source was modified.

## 0.3.68-SNAPSHOT

- TOOL009-C Slice 3 infrastructure (#686): add the suite-native Actor
  Logical Case execution facility that Slice 3's actual migration of the
  11 `protos/tests/conformance/actor` fixtures will depend on.
  `protos/test/actor` now additionally exposes `logicalCaseExecutionAsync`
  in `ProtosTestExecutionRequirementRegistry`, backed by a second
  installation of the existing `ProtosTestLogicalCaseExecutionFacility` /
  `ProtosTestLogicalCaseAttemptBridge` machinery under its own
  `actorLogicalCaseExecutionAsync` bootstrap slot
  (`ProtosTestToolAsyncExecutionScope`). No new Case authority or bridge
  class was introduced: the Actor route reuses the ordinary D152/D153
  logical Case protocol unchanged, parameterized with an Actor-flavored
  fallback resolver that overlays the same `workers` module the legacy
  `actorExecutionAsync` facility resolves through `actorPrelude`, so
  `Actor.spawn("workers", ...)` inside a selected Test body resolves the
  Actor-flavored blueprint set instead of falling back to the ordinary
  Test Tool resolver. Each Case still gets a fresh Process/Prelude/Actor
  graph per attempt (unchanged bridge behavior), giving Case isolation and
  intra-Case Actor state persistence for free. The 11 corpus fixtures and
  `manifest.tsv` are untouched; legacy `actorExecutionAsync` /
  `actorExecutionInspectAsync` / `actorResourceExecutionAsync` /
  `actorResourceExecutionInspectAsync` routes are unmodified. D152, D153,
  D178, CaseAuthority, TOOL009-D, and Group are unmodified.
  - Added `ProtosActorTestLogicalCaseExecutionFacilityTest` (focal
    infrastructure tests: workers overlay resolution + selected-Test
    execution, intra-Case Actor state persistence across requests,
    Case-to-Case Actor isolation, selector mismatch rejection, signature
    mismatch rejection — all against dedicated inline fixtures, not the
    corpus).
  - Extended `ProtosTestToolExecutionRequirementRegistryTest` to assert
    the new `protos/test/actor` binding shape and updated its existing
    assertions that previously encoded "Actor has no suite-native route
    yet".
  - `ProtosTestToolAsyncExecutionScope.installWithCaseAuthorities` and
    `ProtosCli` gained one new `ProtosModuleResolver` parameter/argument
    for the Actor-flavored fallback resolver; other call sites
    (`ProtosTestToolH2B3PublicIntegrationTest`) were updated accordingly.

## 0.3.67-SNAPSHOT

- TOOL009-C Slice 2 (#686): migrate all 15
  `protos/tests/conformance/process` fixtures from the legacy
  manifest-driven expectation model (`error`/`boolean` kinds) to
  suite-native `Test(...)` declarations, so `manifest.tsv` now records all
  15 entries as `suite-native` and each fixture routes through
  `protos/test/process-snapshot`'s `logicalCaseExecutionAsync` (the D178
  selected-`Test.call()` authority) instead of the legacy whole-source
  completion route. Original fixture bodies, license headers, and
  observable semantics are unchanged: the 8 former `error`-kind cases wrap
  their body in `Assertions.signals(Error, ...)`, and the 7 former
  `boolean:true`-kind cases wrap their body in a `tool009LegacyCase`
  closure asserted with `Assertions.require(tool009LegacyCase() === true)`.
  Actor, Group, D152, D153, D178, and TOOL009-D are unmodified; legacy
  execution infrastructure is not removed (TOOL009-B, out of scope).
  - `ProtosProcessSnapshotExecutionTest` and
    `ProtosAsyncProcessSnapshotExecutionFacilityTest` exercised the legacy
    whole-source execution route directly against
    `snapshot-identity.protos`'s raw file content; since that file's
    module-level completion is no longer a boolean after migration, both
    tests now hold the original fixture body as an inline
    `SNAPSHOT_IDENTITY_SOURCE` Java string constant instead of reading it
    from disk, decoupling them from the corpus file's new suite-native
    purpose while preserving their original assertions unchanged. No
    production/`src/main` or `protos/lib` source was modified.

## 0.3.66-SNAPSHOT

- D178: implement the ratified `A_TEST_BODY_AUTHORITY` Process-snapshot Logical
  Case authority model in isolation (docs/project/decisions/tooling/D178_
  PROCESS_SNAPSHOT_LOGICAL_CASE_AUTHORITY_MODEL.md,
  guillermomolina/protos-project-docs@5cbcee32). `logicalCaseExecutionAsync`
  for `protos/test/process-snapshot` no longer treats whole-source-module
  completion as Case authority: it now declares `source`'s top-level `tests`
  (discovery-observational — constructing a named Test never invokes its
  body), resolves exactly one selected Test through the same
  `Discovery.resolveSelectedTest` authority the ordinary suite-native lane
  uses, and invokes only that selected Test's `call()` exactly once; that
  invocation's completion is the Case authority. A signature or selector
  mismatch is classified `rematerialization-error` and rejects before the
  selected Test body ever runs, mirroring `ProtosTestLogicalCaseAttemptBridge`'s
  phase model. The D135 Process-snapshot bootstrap (fresh
  `ProtosProcessRuntime` -> `establishArgumentsForRuntime`/
  `establishEnvironmentForRuntime`) is reused unchanged and still produces a
  fresh Process per Logical Case; `process.args()`/`process.environment()`
  semantics, D152, D153, `CaseAuthority`, and TOOL009-D are unmodified. Actor,
  Group, and the Process conformance corpus/manifest are untouched, and the 15
  `protos/tests/conformance/process` fixtures are not migrated in this slice.
  - `ProtosProcessSnapshotExecution` gains `executeCase(source, prelude,
    expectedSignature, selector)` alongside the unchanged `execute(source,
    prelude)` (still used by `ProtosAsyncProcessSnapshotExecutionFacility`'s
    unrelated whole-source `processSnapshotExecutionAsync` route). Both share
    one extracted bootstrap helper, so the D135 Process/arguments/Environment
    materialization is not duplicated. `executeCase` runs `source` once to
    materialize its `tests` declaration in the bootstrap activation, then
    evaluates the same `Discovery.resolveSelectedTest` selection script
    `ProtosTestLogicalCaseAttemptBridge` uses (sharing its actor module
    state/execution domain so the selected Test closure still resolves
    `process`/`otherProcess`/`emptyProcess` lexically), and finally invokes
    `selected()` as one separate root execution.
  - `ProtosProcessSnapshotLogicalCaseExecutionFacility` no longer validates
    the degenerate "signature names exactly one Case equal to the selector"
    shape; it now validates only argument/entry types (mirroring
    `ProtosTestLogicalCaseExecutionFacility`) and defers real signature/
    selector verification to `executeCase`'s Discovery-based resolution, so a
    Process-snapshot source may now declare more than one named Test.
  - Rewrites `ProtosProcessSnapshotLogicalCaseExecutionFacilityTest` to
    demonstrate the selected-Test authority end to end: the selected Test body
    actually executes exactly once, a failing selected Test body fails the
    Case, selecting one of two declared Tests runs only that one, a signature
    mismatch and a selector mismatch both reject before the selected body
    runs (each distinguished from a body failure by the `rematerialization-
    error` vs `case-execution` phase), and `process.args()`/
    `process.environment()` still read the real D135 bootstrap snapshots
    with a fresh Process per independent Logical Case.

## 0.3.65-SNAPSHOT

- TOOL009-E (#688): add a suite-native Logical Case execution route for the
  `protos/test/process-snapshot` Test Tool lane, so a Logical Case can reach
  `protos/test/process-snapshot` through the same discovery-driven
  `logicalCaseExecutionAsync` protocol already wired for `protos/test/ordinary`
  (TOOL009-D/#687). The D135 Process-snapshot bootstrap
  (`ProtosProcessSnapshotExecution.execute` -> fresh `ProtosProcessRuntime` ->
  `establishArgumentsForRuntime`/`establishEnvironmentForRuntime`) is reused
  unchanged; it is not duplicated or reimplemented. `CaseAuthority`, D152, and
  D153 are unmodified, and Process/Actor/Group corpus migration
  (`protos/tests/conformance/process/manifest.tsv`) remains out of scope.
  - Adds `ProtosProcessSnapshotLogicalCaseExecutionFacility` (Java), a thin
    protocol adapter that translates the 4-argument discovery-driven Logical
    Case call (`sourceAssociation, source, signature, selector`) into the
    existing single-source Process-snapshot execution mechanism. Because the
    Process-snapshot corpus has no Suite/Discovery module structure — each
    source unit is exactly one Case — the adapter validates the degenerate
    one-Case protocol shape (the signature names exactly one Case and the
    selector selects it) and fails closed synchronously on a mismatch,
    without performing a host-side rematerialization search. Execution itself
    is delegated to `ProtosProcessSnapshotExecution.execute(...)`, so every
    accepted Case rematerializes its own fresh Process; two Logical Cases
    never share Process state.
  - `ProtosTestToolAsyncExecutionScope.installWithCaseAuthorities` installs
    the new facility alongside the existing
    `ProtosAsyncProcessSnapshotExecutionFacility` and closes it with the rest
    of the scope.
  - `ProtosTestExecutionRequirementRegistry` extends `addExecutionOnlyBinding`
    with the same optional-route pattern TOOL009-D introduced for the
    7-argument `addBinding` overload: when the new facility's bootstrap slot
    is installed, the `protos/test/process-snapshot` binding additionally
    exposes `logicalCaseExecutionAsync`, reusing that installed slot rather
    than provisioning a parallel one. The binding remains otherwise
    unchanged, and `protos/test/actor`/`protos/test/group`/`protos/test/package`
    are untouched.
  - Adds `ProtosProcessSnapshotLogicalCaseExecutionFacilityTest` demonstrating
    that a `logicalCaseExecutionAsync` call actually reaches
    `ProtosProcessSnapshotExecution` (not merely that the slot exists),
    including successful completion driven by `snapshot-identity.protos`
    (so `process.args()`/`process.environment()` still read from the D135
    bootstrap snapshots), synchronous fail-closed rejection of a
    signature/selector mismatch, and two independent Logical Case invocations
    each observing their own rematerialized Process.
  - Extends `ProtosTestToolExecutionRequirementRegistryTest
    .ordinaryBindingCarriesSuiteNativeLogicalCaseExecutionRouteWhenInstalled`
    to assert the `protos/test/process-snapshot` binding now carries the
    exact installed `ProtosProcessSnapshotLogicalCaseExecutionFacility`
    bootstrap object under `logicalCaseExecutionAsync`.

## 0.3.64-SNAPSHOT

- TOOL009-D (#687): transport the D125 `ExecutionRequirementId` selected by a
  logical Case's corpus into suite-native `LogicalCaseRunner` execution,
  without weakening the D152/D153 inert-`CasePlan` invariant. Process, Actor,
  and Group corpus migration remain out of scope (TOOL009-C/#686 Slices 2-4).
  - `protos/tools/test/LogicalCaseMigration.protos` adds
    `logicalCaseExecutorAsync(corpusExecutionRequirements,
    testExecutionRequirementBindings)`, which returns the
    `LogicalCaseRunner.run` `executorAsync` callback. At each Case's
    execution boundary, the callback resolves the corpus's authoritative
    `ExecutionRequirementId` (never carried on the guest-visible
    `LogicalCasePlan`/`CasePlan` entry) against the existing D125
    `testExecutionRequirementBindings` registry and dispatches to that
    requirement's `logicalCaseExecutionAsync` binding, failing closed for a
    requirement or corpus with no suite-native execution route.
  - `protos/tools/test/Main.protos` records each selected suite's
    `ExecutionRequirementId` by corpus id while building the flat
    suite-native Case plan, and passes
    `LogicalCaseMigration.logicalCaseExecutorAsync(...)` to
    `LogicalCaseRunner.run` in place of the single generic
    `logicalCaseExecutionAsync` executor.
  - `ProtosTestExecutionRequirementRegistry` (Java) exposes the
    already-installed `ProtosTestLogicalCaseExecutionFacility` bootstrap slot
    as an additional optional `logicalCaseExecutionAsync` member of the
    `protos/test/ordinary` binding, reusing that existing facility rather
    than installing a parallel one; the binding is otherwise unchanged and
    the other requirement bindings (`actor`, `group`, `package`,
    `process-snapshot`) are untouched.
  - Extends `protos/tests/tooling/tool009-logical-case-migration.protos`
    (run by the existing `ProtosTestToolLogicalCaseMigrationTest`) with
    direct coverage of the new dispatcher: correct per-corpus requirement
    routing, and fail-closed behavior for a requirement binding with no
    suite-native execution route and for an unrecorded corpus. Adds
    `ProtosTestToolExecutionRequirementRegistryTest
    .ordinaryBindingCarriesSuiteNativeLogicalCaseExecutionRouteWhenInstalled`
    verifying the `protos/test/ordinary` binding carries the exact installed
    facility object and that no other requirement binding gains the route.
  - Updates the literal `Main.protos`-content assertions in
    `ProtosTestToolI8D5CPublicCutoverTest` and
    `ProtosTestToolTool004CProgressPresentationTest` to check for
    `LogicalCaseMigration.logicalCaseExecutorAsync(` in place of the removed
    bare `logicalCaseExecutionAsync` reference.
  - Process, Actor, and Group corpora remain on the incumbent legacy
    execution path; TOOL009-C/#686 Slices 2-4 still own migrating them to
    suite-native, and TOOL009-B/#685 still owns retiring legacy execution
    infrastructure once that migration makes it unnecessary. No
    `CaseAuthority` concept was introduced for Process/Actor/Group and no new
    `ExecutionRequirementId` was added.
  - `make test-java` and the integrated `make test` (Java + native Protos,
    after `make clean`) both pass.

## 0.3.63-SNAPSHOT

- TOOL009-C (#686, Slice 1): migrate the seven library-conformance
  `RepositoryCorpusPlans` corpora (`uri`, `csv`, `cli`, `math/integer`,
  `crypto/sha256`, `network/ip-addresses`, `network/ip-endpoints`; 78 case
  sources) from legacy Test Tool interpretation (`boolean`, `error`,
  `future-boolean` expectations) to the suite-native authoring model: each
  source now imports `std:test/Test` and `std:test/Assertions`, exposes a
  finite frozen `tests` Array of named `Test` declarations, and keeps prior
  legacy case logic intact inside the Test body. `error`-expectation cases use
  `Assertions.signals(Error, body)`; `future-boolean`-expectation cases
  observe their `Future` explicitly with `value()` inside the Test body
  before asserting, rather than returning the `Future` for runner
  interpretation.
  - `protos/tools/test/RepositoryCorpusPlans.protos` keeps the exact same
    explicit corpus membership, path spelling, and case order for all seven
    corpora; every entry's expectation column now reads `suite-native`. The
    manifest itself is preserved as the explicit membership authority, not
    removed.
  - `ProtosTestToolRepositoryCorpusPlansTest.java` remains the golden
    membership/order JUnit test: `ordinaryLibraryPlansHaveExactExplicitMembership`
    is unchanged, and the renamed
    `csvFutureAndShaCasesRemainOrderedAndAreSuiteNative` now asserts both case
    path identity and the `suite-native` expectation at the previously
    special csv future-boolean and sha256 error indices.
  - Process, Actor, and Group corpora are out of scope for this slice and are
    unchanged; TOOL009-C tracks their migration as separate remaining slices
    under #686, which stays open.
  - `make test-java` and the integrated `make test` (Java + native Protos)
    both pass.

## 0.3.62-SNAPSHOT

- TOOL009: reconcile the JUnit/harness surface with the now-complete
  suite-native corpus migration (`protos/tests/conformance/manifest.tsv` has
  zero non-`suite-native` entries). No runtime, parser, or Standard Library
  behavior changed; this slice is test-harness and test-fixture only.
  - Remove `ProtosFutureObservationFixtureShapeTest`: it asserted the old
    bare-object shape (`future`/`error`/`observe`) of
    `future/failed-value-resignals-recorded-error.protos`, which is now owned
    and re-asserted by that corpus source's own suite-native `Test` body —
    duplicated incumbent ownership over an already-migrated manifest source.
  - Decouple the still-authoritative TOOL002 Test Tool mechanism harnesses
    (`ProtosTestToolFutureResolvedMechanismTest` (F1),
    `ProtosTestToolFutureTerminalMechanismTest` (F2),
    `ProtosTestToolFutureStoredInspectionFixtureSuiteTest` (F3C1B/F3C2),
    `ProtosTestToolFutureObservationPolicyFixtureSuiteTest` (F3E),
    `ProtosTestToolFutureFreshInspectionFixtureSuiteTest` (F3D)) from the
    conformance corpus, whose sample sources they used to borrow by raw text
    before the corpus fixtures changed shape under them. Each harness keeps
    its exact existing mechanism (real filesystem-backed reading,
    `executionInspect` live inspection, fresh-child-process boundary); only
    the sample sources moved to new tooling-owned fixtures under
    `protos/tests/tooling/` (`tool002-future-shape-stored.protos`,
    `tool002-future-shape-fresh.protos`, and five standalone F1/F2 samples),
    reproducing exactly the structural properties (`future`, `error`,
    `observe`, parent identities, dispatch counts) those harnesses require.
  - Update the `tool002-d2-manifest-plan.protos` tooling fixture's first-row
    expectation from the pre-migration `"integer"`/`"2"` to the current
    `"suite-native"`/`"-"`, matching the real corpus manifest without
    restoring any legacy manifest metadata.
  - Adapt `ProtosCoreBootstrapTest#objectMatchConformanceUsesDynamicEqualityExactlyOnceAndReturnsExactResult`
    and `#guestCannotCreateAStandardRootSlotAfterPublication` to inline,
    CORE-only-compatible Protos sources (no `std:test` import), preserving
    the exact CORE-only bootstrap boundary and the exact semantic property
    each test proves, instead of depending on now-suite-native corpus
    fixtures that require a full Standard Library bootstrap.
  - Focal JUnit set and the full `make test-java` lane pass; `make test`
    (integrated Java + native Protos suite) passes.

## 0.3.61-SNAPSHOT

- Implement ratified `D174` and `D176` as `TOOL009-A` (GitHub #684): expose
  bounded in-flight progress observability for the Test Tool so a hung Case can
  be identified during a directory-scoped run. The invocation-wide `D174`
  lifecycle tracker in `Progress.protos` now assigns an invocation-local token
  per Case and forwards presentation-neutral `CaseStarted`/`CaseTerminal` facts,
  plus a caller-rendered display reference, to separate reporting machinery.
  `Main.protos` attaches that machinery and supplies one display renderer per
  execution path, so both the incumbent and the suite-native scheduler report
  through a single tracker. A new Test-Tool-private host facility
  (`ProtosTestToolStalledCaseDiagnosticFacility` and
  `ProtosTestToolStalledCaseReporter`) owns the `D176` policy: after 30 seconds
  with no `CaseTerminal` while work remains in flight, one bounded snapshot of
  at most 8 Case references plus a collapsed `+N more` is written to Test Tool
  stderr, re-arming only after later terminal progress and disarming when
  nothing remains in flight. The diagnostic is advisory only — it never fails,
  cancels, times out, classifies, retries or reschedules a Case, and it changes
  no result or exit code. Normal Tool output remains the compact `D120`
  aggregate progress with no mandatory per-Case terminal write, and Protos guest
  code gains no Clock or Timer capability. Add focused regressions for the
  lifecycle boundary, in-flight tracking, the bounded snapshot, re-arming,
  quiescence under continuing terminal progress, and concurrent observation.
  No timeout policy, retry policy, public event/JSON protocol, JUnit reporting,
  telemetry, or terminal UI framework is introduced. The new facility installs
  one bootstrap-local native Closure, so the `I018` guard records it in the
  audited non-Core native-provider inventory alongside the existing Test Tool
  acquisition and file-selection facilities; the Core native boundary is
  unchanged.

## 0.3.60-SNAPSHOT

- Implement ratified `D159` as `I055` (GitHub #656): remove the public
  `Future.detach()` operation and enforce the strict complete-lifetime
  structured-ownership invariant. The standard Future protocol no longer
  installs a `detach` selector, `ProtosFutureValue` loses its detached state
  and `detach()` operation, and `ProtosTask.detachFromParent()` is removed
  without touching the retained parent/child terminalization, child-drain,
  cancellation-unwind, or Actor-termination machinery. Cancellation, cleanup,
  loop, and cross-Task return fixtures retain their task-local pending
  dependencies without detachment. Add regressions for ordinary missing-selector
  behavior and complete-lifetime child ownership. Normative reconciliation
  updates `FUTURES_AND_TASKS.md`, `EXECUTION_AND_CONTROL.md`, `ACTORS.md`,
  `PARALLEL_EXECUTION.md`, `IO_CORE.md`, `ABSTRACT_RUNTIME.md`, and the guide
  chapters, advancing the specification to `0.1.432`. No public replacement
  background-work API, native-boundary expansion, or license-term change is
  introduced.
- Fix a source-wait cancellation defect exposed by I055 validation:
  `Future.then()` now honors cancellation when its private source wait resumes,
  before waiting again or propagating the source outcome. This prevents an
  endlessly runnable continuation while the source is pending and preserves
  cancellation precedence when the source becomes terminal before resumption.
  The source remains unchanged, and a running non-suspending transform retains
  the ordinary cooperative-cancellation behavior.

## 0.3.59-SNAPSHOT

- Fix `BUG008` — the canonical recursive benchmark corpus (for example
  `protos/benchmarks/micro/closure-call.protos` at its checked-in 10,000-deep
  `repeat` recursion) overflowed the host call stack with
  `java.lang.StackOverflowError` well short of its documented iteration count.
  Root cause: a Protos-level Closure call composes through two nested Bytecode
  `CallTarget` invocations (the ratified `PLAT026` truthful semantic-root shell
  wrapping the untagged execution helper), so host stack consumption per guest
  recursion level is higher than a single-`CallTarget` interpreter would need,
  and no part of the implementation provisioned a call stack sized for that
  cost. This is a host-resource-budget gap, not a `PLAT026` semantic regression:
  the existing two-root architecture and its RootTag truthfulness rationale are
  unchanged. `ProtosCli.run` now dispatches every CLI command (file/REPL/`-e`
  evaluation, `run`, `debug`, `package`, `test`) on a dedicated carrier thread
  with a fixed 64 MiB stack instead of depending on whatever stack size the
  calling thread happened to have, matching `protos/benchmarks/README.md`'s
  existing allowance for a benchmark runtime to "provision a larger call stack
  when required" as long as that runtime option is fixed and recorded; the
  README now records the exact provisioned value. All seven `micro/` workloads,
  both `runtime/` dispatch workloads and the affected `collections/` workloads
  execute successfully at their checked-in iteration counts with unchanged
  documented results. Adds a dedicated regression
  (`regression/deep-recursive-closure-call-stack-capacity.protos`) exercising
  the same 10,000-deep recursive self-call shape as the benchmark corpus;
  writing that regression as ordinary suite-native Test Tool corpus content
  exposed a second, independently reachable instance of the identical gap:
  `ProtosTestToolAsyncExecutionScope$PlatformThreadPerTaskSubmission` gives
  each Test Tool Case its own isolated Process on a fresh
  `protos-test-exact-N` platform Thread that can likewise drive arbitrarily
  deep guest recursion, and that carrier previously kept the JVM's unpatched
  default stack size. Left unfixed, the same 10,000-deep recursion running
  inside that isolated per-Case Process did not merely crash — the resulting
  `StackOverflowError` during Actor/Task unwinding left the Process's
  lifecycle unable to reach `TERMINATED`, so `bin/protos test` hung
  indefinitely instead of failing the Case. `PlatformThreadPerTaskSubmission`
  now provisions the same fixed 64 MiB budget for its carriers. Diagnosing
  and hardening that unwind-time hang-on-`StackOverflowError` path itself
  (independent of stack provisioning) is not in scope here and is not
  addressed by this fix.
  No Protos specification, observable language semantics, or `PLAT026`
  architecture change.
  Implementation version becomes `0.3.59-SNAPSHOT`.

## 0.3.58-SNAPSHOT

- Fix `BUG009` — a self-cancelled Task's suspended `.ensure()` cleanup that
  superseded the cancellation via a non-local return (`^`) whose captured
  return home belonged to a different, currently-suspended Task escaped
  uncaught instead of being recognized, permanently stalling that Task and
  deadlocking anything awaiting its Future. Under ratified `D177`,
  `ProtosBytecodeTaskExecution`'s Task drivers (`runSegment`,
  `runPreparedSegment`) now recognize a non-local return whose captured home
  is unreachable from the executing Task and fail that Task with
  `InvalidReturn`, matching the already-specified completed-home case in
  `spec/semantics/CALLABLES.md` §14. Adds a dedicated regression
  (`regression/escaped-closure-cross-task-invalid-return.protos`) and
  corrects the TOOL009 suite-native migration of the three previously-blocked
  `control/` conformance tests so their `.ensure()`-cleanup closures keep
  their original same-Task return home instead of unintentionally capturing
  the enclosing Test body's.
  Implementation version becomes `0.3.58-SNAPSHOT`.

## 0.3.57-SNAPSHOT

- Advance `TOOL009 — Local-first logical Case Test Tool integration` (GitHub
  #600) with composable invocation-local source selection. Make `--file FILE`
  repeatable, add repeatable recursive `--directory DIR` selection, and project
  both selector families over already-materialized authoritative TestPlans
  without filesystem test discovery. Require every explicit selector to match
  independently, union overlapping selections without duplicate Case execution,
  preserve canonical TestPlan order, retain invocation-CWD relative and absolute
  locator support behind host authority, and keep physical paths out of Case
  identity. Extend the host selection capability, Test Tool option/orchestration
  wiring, focal regressions, and end-to-end coverage accordingly.
  Implementation version becomes `0.3.57-SNAPSHOT`.

## 0.3.56-SNAPSHOT

- Implement `I052 — Remove Core fixed-width integer families and preserve
  interop capability` (GitHub #628) under ratified D156 Candidate D′ and
  specification revision `0.1.430`. Remove the eight former Core
  `UInt8`/`Int8` through `UInt64`/`Int64` source prototypes, prelude bindings,
  conversion/arithmetic/equality/order/hash participation, runtime transfer and
  indexed/I/O widening paths, and the obsolete Test Tool `fixed-integer`
  expectation policy. Core numeric semantics now expose only `Number`,
  unbounded exact `Integer`, and binary64 `Float`. Preserve reusable
  width/range-aware host numeric projection behind the internal
  `ProtosFixedIntegerInteropValue` carrier without granting it guest Core
  prototype, identity, equality, arithmetic, hashing, transfer, or public FFI
  semantics. Reconcile Standard Library domain tests, Core architecture guards,
  current public documentation, and exploratory numeric-boundary notes with the
  ratified model while retaining the historical TOOL002-D3B1 record as retired.
  Implementation version becomes `0.3.56-SNAPSHOT`.

## 0.3.55-SNAPSHOT

- Advance `TOOL009 — Local-first logical Case Test Tool integration` (GitHub
  #600) through the first production D152-M2 ownership cutovers under ratified
  D151, D152, D153, D155 and the LIB018 suite-native authoring model. Wire the
  temporary mixed-ownership Test Tool orchestrator so selected `suite-native`
  manifest entries are discovered before scheduling, projected into one flat
  logical Case plan, executed through fresh semantic Processes with the global
  `--jobs` capacity, and removed from incumbent TOOL002 ownership to prevent
  double execution while unmigrated entries continue through the legacy path.
  Migrate all 13 `library/test/` production sources and all 12 `library/text/`
  production sources to finite frozen `tests` Arrays of named `std:test/Test`
  values, preserving D151 exact `--file` selection and D155 terminal outcome
  precedence across mixed ownership. Correct the migrated malformed-UTF8
  conformance case to construct Bytes through the public UTF8 Encoding surface
  and assert the intended `EncodingError`, eliminating a historical generic
  `error` false positive that could succeed because direct `Bytes()` is no
  longer a public Core binding. Admit both migrated library domains as
  suite-native during the temporary M2 bridge. TOOL009 remains open: additional
  production domains, legacy-owner exhaustion, M3 cleanup and the M4
  migration-scar audit remain pending. Implementation version becomes
  `0.3.55-SNAPSHOT`.

## 0.3.54-SNAPSHOT

- Implement `I053 — Implement scalar Core String indexing and preserve grapheme
  capability` (GitHub #629) under ratified D157 Candidate D and specification
  revision `0.1.429`. Change Core `String.size` and `String.at` / bracket read
  from Unicode-17 extended-grapheme units to exact Unicode scalar values,
  including supplementary-plane scalars as one position and decomposed or
  multi-scalar emoji sequences as multiple positions. Preserve zero-based
  semantic-Integer indexing, existing Error behavior, String immutability,
  exact-scalar identity/no-normalization, concatenation, Encoding boundaries,
  and strict String-family receiver checks. Retain Unicode-17 default
  extended-grapheme segmentation behind an internal reusable boundary for the
  separately placed Standard Library capability without choosing its deferred
  public API. Enforce the semantic String invariant by rejecting unpaired
  UTF-16 surrogates at `ProtosStringValue` construction, migrate Core
  conformance coverage to scalar expectations, and retain focused Java evidence
  for preserved grapheme segmentation. Implementation version becomes
  `0.3.54-SNAPSHOT`.

## 0.3.53-SNAPSHOT

- Advance `TOOL009 — Local-first logical Case Test Tool integration` (GitHub
  #600) under ratified D151, D152, D153 and D155. Replace physical source-path
  arguments at the suite-native discovery and logical Case execution guest
  boundaries with canonical `[CorpusId, relativePath]` source associations,
  resolving exact physical locators only inside the existing D151 host authority
  boundary. Reuse the same invocation-local corpus source roots for discovery
  and execution, preserving inert logical identity across planning and fresh
  Process rematerialization. Add the temporary D152-M2 mixed-ownership migration
  scaffold with explicit legacy/suite-native partitioning, D151 source
  selection, and D155 outcome precedence (`3 > 1 > 0`) in preparation for the
  first production Case migration. No production test source changes ownership
  in this checkpoint. Implementation version becomes `0.3.53-SNAPSHOT`.

## 0.3.52-SNAPSHOT

- Implement `I051 — Remove public Object.identityHash selector` (GitHub #621)
  under ratified D154 Candidate A and specification revision `0.1.428`. Remove
  the standard guest-visible `Object.identityHash()` selector while preserving
  primitive non-overridable semantic identity hashing, `===` / `!==`,
  `IdentityMap` identity semantics, default `Object.hash()`, execution-scoped
  identity-hash stability/collision rules, and ActorRef/GroupRef identity.
  Ordinary user-defined slots named `identityHash` remain ordinary behavior and
  have no semantic identity-hash authority. Add focused guest and Java coverage
  proving the standard selector is absent and IdentityMap does not dispatch
  user-defined `identityHash` callbacks. Implementation version becomes
  `0.3.52-SNAPSHOT`.

## 0.3.51-SNAPSHOT

- Advance `TOOL009 — Local-first logical Case Test Tool integration` (GitHub
  #600) under ratified D152, D153, LIB018-0 and D155. Add authority-free
  suite-native discovery of the explicit `tests` declaration, inert ordered
  logical Case plans, signature-checked fresh-Process Case rematerialization,
  case-private execution observations, a global work-conserving logical Case
  scheduler, and terminal-state Case result classification. Wire the discovery
  and execution facilities into the Test Tool bootstrap, allow D155
  infrastructure-aborted outcomes without manufacturing historical D108
  evidence, and reuse D151 corpus/source authority for exact logical-source
  resolution without exposing physical paths to guest code. Add focused Protos
  and Java coverage for discovery, planning, scheduling, execution,
  rematerialization, result policy, bootstrap integration, and source-authority
  resolution. This remains an intermediate migration checkpoint: incumbent
  production test ownership is unchanged and no repository test source has yet
  been cut over to suite-native ownership. Implementation version becomes
  `0.3.51-SNAPSHOT`.

## 0.3.50-SNAPSHOT

- Begin `LIB018 — Expand Protos testing support beyond the initial Assertions
  surface` (GitHub #592) under the ratified LIB018-0 Candidate C-prime
  authoring model. Add `std:test/Test` as the canonical ordinary Test-value
  constructor: `Test(name, body)` requires a non-empty semantic String name and
  returns one fresh frozen ordinary object exposing local `name` and zero-argument
  `call` slots. Test invocation returns the exact body result and preserves exact
  Error/control propagation, while body callability remains deferred until
  invocation. Add no nominal Test runtime type, Suite abstraction, ambient
  registration mechanism, fixture lifecycle, parameterization framework, or
  Test Tool discovery/scheduling change. Add focused Protos conformance for the
  Test value surface, freshness/frozen behavior, result/Error propagation,
  invalid names, and deferred body validation. Implementation version becomes
  `0.3.50-SNAPSHOT`.

## 0.3.49-SNAPSHOT

- Implement `I049 — Implement Map.atIfAbsent expected-absence lookup` (GitHub
  #598) under ratified D142 Candidate C and specification revision `0.1.421`.
  Add standard `Map.atIfAbsent(key, fallback)` and
  `IdentityMap.atIfAbsent(key, fallback)` with exactly one logical key search,
  exact present-value return including `null` and `false`, and path-sensitive
  ordinary zero-argument fallback invocation only on absence. Preserve normal
  Map `hash` and directed `queryKey == storedKey` observability, IdentityMap
  identity lookup without guest key callbacks, fallback suspension/resumption
  without search replay, no second search after fallback, no implicit mutation,
  and eligibility on closed/frozen maps. Keep existing `at` and `containsKey`
  behavior unchanged, and add focused Protos conformance plus Bytecode
  DSL/C-prime regression coverage. Implementation version becomes
  `0.3.49-SNAPSHOT`.

## 0.3.48-SNAPSHOT

- Implement `I050 — Implement D143 multiple-slot creation` (GitHub #599)
  under ratified D143 and specification revision `0.1.422`. Add dedicated
  `(a, b): source` multiple-slot creation with one RHS evaluation, strict
  receiver-owned standard Array indexed-state validation, complete short-source
  preflight, ascending shallow observation of exactly the first N indexed
  references before mutation, ignored extra elements, and ordinary bare-slot
  creation left-to-right with deliberate no-rollback behavior. Return the exact
  RHS object on success, preserve closure and object-body creation contexts,
  reserve every target name against object composition, bypass user-visible
  `at`/iteration/deconstruction protocols, and reject non-Array, inherited-only
  Array lookalike, and short sources before creating any target slot. Add
  focused parser/canonicalization coverage, Protos conformance coverage, and
  Java runtime-mechanics regressions for preflight and partial effects.
  Implementation version becomes `0.3.48-SNAPSHOT`.

## 0.3.47-SNAPSHOT

- Implement `TOOL008 — Test Tool exact file-backed focal selection` (GitHub
  #591) under ratified D151 Candidate B-prime. Add exact separate-token
  `protos test --file FILE` selection over already-authoritative CaseSpecs,
  resolving relative locators from the invocation working directory and
  accepting absolute locators without turning physical paths into Test Tool
  identity. Preserve authoritative TestPlan ordering and CaseSpec metadata,
  reject arbitrary or zero-match source files, support multiple CaseSpecs for
  one source, and continue through the existing D108 scheduler, resource,
  result-classification and reporting paths. Implementation version becomes
  `0.3.47-SNAPSHOT`.

## 0.3.46-SNAPSHOT

- Implement `I048 — Implement Object null-aware control protocol` (GitHub #589)
  under ratified D144 Candidate B-prime and specification revision `0.1.427`.
  Add source-backed ordinary `Object.ifNull(block)` and
  `Object.ifNotNull(block)` control messages using exact canonical-null
  identity, with no truthiness and no null identity conferred by delegation.
  Preserve eager argument-expression evaluation, branch-sensitive callback
  validation, ordinary polymorphic callback invocation, exact receiver/result
  identity, unchanged Future results with no implicit await/adoption, and
  ordinary Error/non-local-control propagation. Keep failed lookup and D142 Map
  absence semantics unchanged, retain ordinary custom overrides, document the
  public selectors in distributable Core source, and add focused conformance
  plus source-backed/native-boundary guards. Implementation version becomes
  `0.3.46-SNAPSHOT`.

## 0.3.45-SNAPSHOT

- Complete `I045-B — Add exact standard-owner recognizes predicates` (GitHub
  #584) under ratified D147 Candidate B-prime and specification revision
  `0.1.426`. Add exact canonical-owner `recognizes(value)` predicates for
  `String`, ordinary unbounded `Integer`, `Float`, and standard-state `Array`.
  Preserve mismatch-as-false behavior, strict owner/arity errors, fixed-width
  Integer exclusion, Array recognition independent of immediate delegation
  parent, and the no-candidate-dispatch/no-coercion contract. Migrate matching
  Standard Library and bundled Tool family-validation probes to the new
  predicates while preserving caller-specific failure behavior, and add focused
  semantic and native-boundary coverage. Implementation version becomes
  `0.3.45-SNAPSHOT`.

## 0.3.44-SNAPSHOT

- Implement `LIB017 — Integer ranges and progression iteration` (GitHub #570)
  under the owner-approved G-prime decision. Add
  `std:collections/Range` with `each(start, stop, block)` and
  `reverseEach(start, stop, block)` over the same half-open `[start, stop)`
  domain of ordinary unbounded Integer values. Preserve empty equal/reversed
  bounds, ordinary polymorphic callback invocation, ignored callback results,
  canonical `null` normal completion, and ordinary Error/non-local-control
  propagation. Reject Float, fixed-width Integer-family, and delegated
  Integer-lookalike bounds; keep host-width limits, proportional
  materialization, first-class Range values, arbitrary step, generic
  Iterable/Iterator integration, slicing, and range syntax out of this slice.
  Add focused conformance coverage for traversal order, callback behavior,
  strict bound domains, single bound evaluation, unbounded endpoints, and
  control propagation. Implementation version becomes `0.3.44-SNAPSHOT`.

## 0.3.43-SNAPSHOT

- Implement `I047 — Implement strict String.concat semantics` (GitHub #587)
  under ratified D141 Candidate M1 and specification revision `0.1.424`. Add
  ordinary variadic `String.concat` with strict semantic-String receiver and
  argument domains, zero-argument semantic identity, exact scalar-sequence
  concatenation, and no normalization or implicit textual conversion. Preserve
  ordinary eager argument/spread evaluation before method-body validation,
  reject invalid inputs without user callbacks or partial String results, keep
  standard `+` unchanged, and keep `+` and `concat` as independent selectors.
  Add focused conformance coverage and update the audited Java-native String
  boundary. Implementation version becomes `0.3.43-SNAPSHOT`.

## 0.3.42-SNAPSHOT

- Complete `I044 — Remove custom symbolic binary operators` (GitHub #583)
  under ratified D149 Candidate A. Remove arbitrary custom symbolic binary
  source syntax, the `CUSTOM_OPERATOR` token/category, the custom-binary parser
  path, custom precedence/mixing behavior, and the dedicated conformance and
  canonicalization coverage. Preserve the fixed standard symbolic surface and
  precedence ladder, D148-derived `!=`, ordinary named one-argument messages,
  and `Object.alias`. Unsupported maximal symbolic spellings are lexical errors
  and are not split into shorter standard operators; forms such as `!!x`,
  `^^x`, `--x`, `-!x`, and `!-x` therefore remain invalid. Implementation
  version becomes `0.3.42-SNAPSHOT`.

## 0.3.41-SNAPSHOT

- Continue `I046-B — Add standard prelude fail callable` (GitHub #585) with the
  bounded equivalent-source migration approved by D146. Remove 46 redundant
  local zero-argument `fail` wrappers across 29 Standard Library and bundled
  Tool source files where the wrapper was exactly `Error().signal()` and had no
  additional state, alternate Error binding, reassignment, or value-observable
  behavior. Those call sites now resolve the ordinary frozen-prelude `fail`
  callable. Preserve stateful/custom local `fail` helpers, direct
  `Error().signal()` sites, `error.signal()` identity semantics, and
  `std:test/Assertions`. Implementation version becomes `0.3.41-SNAPSHOT`.

## 0.3.40-SNAPSHOT

- Begin executable `I046-B — Add standard prelude fail callable` (GitHub #585)
  under ratified D146 Candidate B-prime and specification revision `0.1.423`.
  Add `fail` as an ordinary source-backed zero-argument Closure in the frozen
  standard prelude. Each reached invocation creates and signals one fresh
  generic Error with canonical standard `Error` parentage, while extraction,
  caller-local `Error` shadowing, and ordinary local `fail` shadowing preserve
  the ratified semantics. Add focused conformance for fresh identity/parentage,
  canonical provenance under shadowing, and wrong arity. Bounded production
  call-site migration remains subsequent I046 work. Implementation version
  becomes `0.3.40-SNAPSHOT`.

## 0.3.39-SNAPSHOT

- Complete `PERF009-C` (GitHub #559) by removing the fixed wall-clock
  validation budget introduced during the post-quarantine validation work.
  Restore GitHub CI to the canonical
  `make test JAVA_TEST_JOBS=4 PROTOS_TEST_JOBS=4` correctness path and remove
  the `test-budget` target and timeout guard. Test execution has no fixed
  duration acceptance threshold; proportional routine workloads remain governed
  by `AGENTS.md`, while future measured performance regressions are investigated
  when evidence shows a regression. Implementation version becomes
  `0.3.39-SNAPSHOT`.

## 0.3.38-SNAPSHOT

- Fix `BUG007 — PERF006 canonical coverage still expects removed args
  intrinsic` (GitHub #586). Remove the stale PERF006 B6A1 bytecode coverage
  that still expected bare `args` to expose the caller-supplied argument vector
  after I042/D145 removed `args` from the intrinsic model. Preserve all other
  PERF006 B6A1 canonical/bytecode coverage and keep bare `args` as an ordinary
  identifier. Implementation version becomes `0.3.38-SNAPSHOT`.

## 0.3.37-SNAPSHOT

- Implement `I042 — Remove Closure args intrinsic` under ratified D145
  Candidate A. Remove bare `args` from the reserved/intrinsic lexer, parser,
  surface/canonical and bytecode execution path; `args` is now an ordinary
  identifier and may be used for parameters, rest parameters, local slots and
  ordinary lookup. Preserve internal caller-supplied argument vectors required
  for binding, preserve required/default/rest/spread behavior, and leave
  `process.args()` unchanged. Replace obsolete ambient-`args` conformance with
  ordinary-identifier/rest-forwarding coverage and reconcile specification
  revision `0.1.420`. Implementation version becomes `0.3.37-SNAPSHOT`.

## 0.3.36-SNAPSHOT

- Implement `I043 — Implement derived inequality semantics` (GitHub #582) under
  ratified D148 Candidate A-prime. Keep the `!=` source operator while lowering
  it to exactly one ordinary `==` invocation followed by strict canonical
  Boolean validation and inversion. Remove the independent standard
  `Object.!=` protocol and its source-backed Core bootstrap behavior, so a
  structural slot named `!=` no longer changes source inequality. Preserve
  `===` / `!==`, Map/hash equality semantics, left-to-right exactly-once
  evaluation, Errors/control, and suspension behavior. Add focused Java and
  Protos conformance coverage; the Protos suite remains green at 1301/1301.
  Reconcile the normative specification at revision `0.1.419`. Implementation
  version becomes `0.3.36-SNAPSHOT`.

## 0.3.35-SNAPSHOT

- Complete the implementation slice for `PERF009-C — Post-quarantine
  full-suite budget and validation-routing retirement` (GitHub #559). Normalize
  routine test workloads whose cost came from repeated guest bootstrap/execution
  rather than the correctness property being proved, while preserving every
  retained invalid-input and semantic case. Establish the owner-approved
  180-second complete-repository validation budget for
  `make test JAVA_TEST_JOBS=4 PROTOS_TEST_JOBS=4`, add a fail-visible retained
  budget guard used by CI, and retire the PERF007 `FULL:NON_TOOL` temporary Tool
  quarantine so shared, unknown, unmapped, empty, and top-level closure deltas
  again require complete Maven validation. Retain the independently justified
  `TOOL_LOCAL:PACKAGE` and `TOOL_LOCAL:TEST` intermediate-publication routing.
  Implementation version becomes `0.3.35-SNAPSHOT`.

## 0.3.34-SNAPSHOT

- Reconcile the Standard Library documentation extractor with the D138
  source-local documentation layer for `TOOL007` (GitHub #556). Remove the
  extractor's duplicate `//!`/`///` parsing and association logic and delegate
  authored documentation ownership and validation to
  `ProtosSourceDocumentation`. Preserve D064/D067 publication semantics:
  Standard Library artifacts continue to expose the top-level module surface,
  while valid nested D138 documentation is accepted without creating additional
  published symbols. Implementation version becomes `0.3.34-SNAPSHOT`.

## 0.3.33-SNAPSHOT

- Implement `TOOL007-A3 — named slot source-documentation association`
  (GitHub #556) under ratified D138 Candidate A′. Associate contiguous
  line-leading `///` documentation blocks with exact source-local named
  `SurfaceSlotCreation` owners, including nested and member-target creations.
  Preserve distinct same-name occurrences, allow parenthesized grouping as a
  transparent source boundary, and fail closed across blank lines, ordinary
  comments, unrelated constructs, scope boundaries, non-owner forms, and
  inline documentation markers. Standard Library documentation extractor
  reconciliation remains deferred. Implementation version becomes
  `0.3.33-SNAPSHOT`.

## 0.3.32-SNAPSHOT

- Implement the initial `LIB016 — Protos testing support library and Test Tool
  boundary` public standard-library surface under the ratified LIB016-0 design.
  Add `std:test/Assertions` with the ordinary `AssertionFailure` Error prototype,
  `require(condition)`, and `signals(errorPrototype, body)`. Preserve the Core
  Error model and Test Tool boundary: assertion failures are fresh ordinary
  Error occurrences, `require` accepts only Boolean conditions, `signals`
  delegates matching and propagation to ordinary `Error.handle`, and no TOOL002,
  Core, parser, compiler, or runtime semantics change. Add focused conformance
  coverage for the public surface, success/failure behavior, fresh assertion
  failures, non-Boolean misuse, exact matching Error identity, missing expected
  errors, and unchanged propagation of nonmatching Errors. Protos conformance
  validation passes 1299/1299 cases. Implementation version becomes
  `0.3.32-SNAPSHOT`.

## 0.3.31-SNAPSHOT

- Implement `TOOL007-A2 — module source-documentation association`
  (GitHub #556) under ratified D138 Candidate A′. Add tooling-only extraction
  of one contiguous line-leading `//!` documentation block from the module
  preamble, preserving ordinary comment behavior for Protos execution. Reject
  late module documentation, multiple module documentation blocks, and inline
  documentation markers. Slot-level `///` association remains deferred to
  TOOL007-A3. Implementation version becomes `0.3.31-SNAPSHOT`.

## 0.3.30-SNAPSHOT

- Implement `TOOL007-A1 — source-local documentation owner inventory`
  (GitHub #556) under ratified D138 Candidate A′. Add the minimal
  source-documentation ownership projection over the parser-authoritative
  Surface AST, identifying the module source unit plus explicit named
  `SurfaceSlotCreation` occurrences in source order, including nested and
  member-target creations. Preserve occurrence-local identity so repeated
  names remain distinct, while parameters and assignments remain outside the
  independent documentation-owner model. Documentation comment association
  (`//!` and `///`) remains deferred to subsequent TOOL007-A slices.
  Implementation version becomes `0.3.30-SNAPSHOT`.

## 0.3.29-SNAPSHOT

- Complete `PERF009-B — Quarantined Java test normalization and reintegration`
  (GitHub #546) by removing the remaining Java slow-test quarantine while
  preserving the semantic and integration properties owned by the retained
  tests. Normalize external-package planning failure coverage to construct and
  retain only the borrowed custody actually exercised by that failure path, and
  reuse one Core bootstrap when checking multiple borrowed custodies. Reuse one
  real package execution plan across the independent malformed-generation,
  shape, location, and runtime-alias rejection checks instead of rebuilding the
  same expensive fixture for every corruption. Split JSON parser validation
  into proportional ordinary correctness coverage and an explicit
  `test-java-stress` surface that retains the original 2048-level nesting and
  2048-element implementation-shape guards. Pin Maven Surefire 3.5.6 after
  demonstrating that inherited 3.2.5 misattributes JUnit testcases between XML
  suites under class-parallel execution while 3.5.6 reports 1853 parallel
  testcases with zero suite/class mismatches. Tune the local Java-test default
  from eight to six jobs from measured throughput/latency evidence; CI retains
  its explicit four-job setting. Remove the PERF009 Java quarantine completely.
  The explicit Java stress validation passes 2/2 tests and the full ordinary
  validation passes, including 1292/1292 Protos tests. No Protos-visible
  language, specification, package-format, or Standard Library semantics
  change. Implementation version becomes `0.3.29-SNAPSHOT`.

## 0.3.28-SNAPSHOT

- Implement `CLI009 — Direct-file local module resolution` (GitHub #555) under ratified D137
  Candidate B′. Add an official direct-file resolver domain for explicit `./`
  and `../` module specifiers, resolve them relative to the importing local
  source inside the selected entry-directory tree, preserve exact component
  spelling and fail-closed no-follow confinement, and keep CWD lookup,
  implicit extensions, index/search-path probing, absolute-file semantics and
  general Filesystem authority absent. Give direct and debug file entries a
  canonical `ModuleKey` before execution and route them through the existing
  cache-before-execute initial-module lifecycle so `main -> helper -> main`
  aliases the same partially initialized entry instance. Preserve `std:`
  composition and leave package `self:`/`dep:` plus locationless `-e`/REPL
  resolution unchanged. Add focused resolver, CLI cycle/confinement, debugger
  parity and routing coverage and document the direct-file namespace. Core
  language and package semantics remain unchanged. Implementation version
  becomes `0.3.28-SNAPSHOT`.

## 0.3.27-SNAPSHOT

- Advance `AUD012 — Direct Java guest-execution path audit` (GitHub #541) under
  ratified PLAT035 by completing retirement of the legacy executable-AST
  backend. Remove `CanonicalToTruffleLowerer` and the
  `ProtosExpressionNode`/`ProtosRootNode` execution tree, collapse Closure
  execution planning onto the Truffle Bytecode DSL backend, and require
  source-backed execution crossing Polyglot Context ownership boundaries to be
  rematerialized through the entered `ProtosLanguageContext`. Migrate retained
  execution tests to the Bytecode-backed boundaries where appropriate and
  remove tests whose only subject was the retired AST backend. Strengthen the
  fail-closed legacy-execution guard with an explicit retired-backend symbol
  family and record the PLAT035 execution/test-placement rule in `AGENTS.md`.
  Final source/test rescan finds no references to the retired backend classes.
  After reconciliation with current `main`, the affected execution focal set
  passes 33/33 tests; the Java lane passes 1849/1849, the separately executed
  DAP/LSP set passes 3/3, packaging succeeds, and the Protos Test Tool suites
  pass 1292/1292. No Protos-visible language, specification, or Standard
  Library semantics change. Implementation version becomes `0.3.27-SNAPSHOT`.

## 0.3.26-SNAPSHOT

- Advance `I041-D — D131 matching conformance reconciliation` (GitHub #550).
  Reconcile the retained matching evidence around the ratified protocol-first
  model and make the ordinary `Array.match`, `Map.match`, and `Object.caseOf`
  implementations safe on the production Task/C-prime execution path. Route
  nested matcher, case action, Map key `hash`, and query-side `==` calls through
  structured Bytecode dispatch instead of synchronous guest invocation inside a
  Task. Preserve D131 ordering and observation rules: Array matcher/subject
  snapshots remain shallow and left-to-right; Map matcher and subject
  associations remain stable snapshots, all required associations are resolved
  through the normal Map hash/equality law before the first mapped-value matcher,
  and case selection remains insertion ordered with first-success commitment.
  Add direct `Object.match` authority coverage, additional `caseOf` ordering,
  uniqueness, and lazy-validation coverage, fresh-Process regressions for all
  three structured paths, and seven protocol-first matching cases in the primary
  Protos conformance manifest. The matching focal set passes 36/36 tests, the
  authoritative Java lane passes 1861/1861, and the Protos Test Tool suites pass
  1278/1278 with the primary manifest at 865/865. Specification remains at
  revision `0.1.417`; implementation version becomes `0.3.26-SNAPSHOT`.

## 0.3.25-SNAPSHOT

- Advance `PERF009-B — Quarantined Java test normalization and reintegration`
  (GitHub #546) by reintegrating the former
  `ProtosPackageToolProtosTest` quarantine into the ordinary Java lane while
  preserving or strengthening its semantic and integration coverage. Move the
  six frozen positive ContentIdentity digest and verification vectors into the
  canonical Test Tool project-tree corpus, and move filesystem-backed fresh/stale resolution-input
  lock checks into a dedicated project-tree corpus. Preserve the TextReader
  multi-chunk regression with valid TOML larger than the 8192-byte read-ahead
  boundary without retaining hundreds of parser-scale exports. Split the
  remaining 26 Java host/runtime tests by their existing responsibilities into
  Manifest, metadata persistence/lock, filesystem security, and ContentIdentity
  integration classes with shared harness support. Across five repeated
  comparable runs, the maximum observed class times are 1.975 s, 1.941 s,
  0.066 s, and 2.216 s respectively, with maximum individual test time
  1.347 s; all satisfy the PERF009 `< 5 s` class and `< 2 s` per-test budgets.
  Retain the pure-Protos SHA-256 implementation while replacing recursive
  per-bit ternary operations with semantically equivalent 2-bit lookup-table
  processing. Remove the former Package Tool class from the slow-test
  quarantine, leaving four quarantined Java classes. No Protos-visible
  language, specification, package-format, or Standard Library semantics
  change. Implementation version becomes `0.3.25-SNAPSHOT`.

## 0.3.24-SNAPSHOT

- Advance `I041-C — remove the superseded dedicated matching implementation`
  (GitHub #550). Remove the pre-D131 `subject match { ... }` language mechanism
  completely after the ordinary protocol-first replacement became available.
  Delete the dedicated surface and canonical Match ASTs, match coverage model,
  parser grammar, canonicalization/lowering paths, Truffle nodes, Bytecode
  lowering and Match-specific Bytecode operations, together with their obsolete
  Java execution/analysis tests and retained conformance corpus. Remove the 38
  retired matching cases from the primary Test Tool manifest. Preserve
  `Object.match`, `Array.match`, `Map.match`, `Any.match`, and `Capture.match` as
  ordinary D131 protocol behavior; `match`, `case`, `when`, `exact`, and
  `captures` remain ordinary identifiers. Retain an explicit parser regression
  proving that the removed dedicated syntax is rejected while ordinary
  `pattern.match(subject)` remains valid. Specification remains at revision
  `0.1.417`; implementation version becomes `0.3.24-SNAPSHOT`.

## 0.3.23-SNAPSHOT

- Advance `AUD012 — Direct Java guest-execution path audit` (GitHub #541)
  under ratified PLAT035 by removing another production and retained-Java
  consumer block from the legacy executable-AST backend. Route Process
  snapshot execution, canonical initial-module unhosted execution, and the
  ModuleRuntime unhosted fallback through initialized Polyglot Contexts and
  the canonical public-parse Bytecode path while preserving activation,
  module-cache, signal, and Process-hosting behavior. Migrate TOML parser
  module execution and Process-context parallel-routing coverage away from
  direct `ProtosSourceCompiler.compile(...)`, and move the Test Tool Manifest
  compile-only check to `Context.parse(...)` so it remains non-executing while
  exercising the canonical frontend/Bytecode path. Update A4B3 architecture
  evidence accordingly and add direct unhosted Bytecode coverage for initial
  modules and ModuleRuntime. Deliberately retain legacy-backend-specific
  compiler/lowering tests and `ProtosSourceFileLoader`, whose Core-bootstrap
  ownership requires a separate retirement cut. The combined affected focal
  set passes 40/40 tests. No Protos-visible language, specification, or
  Standard Library semantics change. Implementation version becomes
  `0.3.23-SNAPSHOT`.

## 0.3.22-SNAPSHOT

- Advance `AUD012 — Direct Java guest-execution path audit` (GitHub #541) under
  ratified PLAT035 by migrating the remaining straightforward retained-Java
  semantic, representation, runtime, and conformance harness block away from
  direct legacy-AST `ProtosSourceCompiler.compile(...).call(...)` and
  `ProtosSourceFileLoader` execution routes onto the canonical Bytecode-backed
  `ProtosTestExecutionSupport` boundary. Cover TOML data-model and closure
  conformance, IP address/endpoint modules, retained structural matching
  representation and error cases, Array lifecycle/callback behavior, Core
  bootstrap and Context source behavior, the exact execution facility, and
  filesystem language, maturity, and tree-surface conformance. Remove stale
  `ProtosSourceFileLoader` imports from already-migrated Future observation and
  integrated filesystem-tree harnesses. The final Block 6 rescan leaves direct
  compiler uses only where they are compiler/backend, explicit Bytecode,
  compile-only, architecture, PERF006, Context-routing, or legacy-backend
  retirement evidence; those remain for the subsequent PLAT035 retirement
  work. The combined affected Java set passes 58/58 focal tests. No
  Protos-visible language, specification, Standard Library, or production
  runtime semantics change. Implementation version becomes
  `0.3.22-SNAPSHOT`.

## 0.3.21-SNAPSHOT

- Advance `I041-B4 — structural Map.match publication` (GitHub #550).
  Publish the ratified D131 open/subset structural matching protocol on ordinary
  normal standard Map values. Require the matcher receiver to own normal
  standard Map keyed-entry state and return canonical `false` for an ineligible
  subject or a missing required association. Establish stable shallow
  observations of matcher and subject associations before any requirement
  search or nested value matching; preserve representative key references,
  recorded hashes, mapped values, and insertion order for the attempt. Resolve
  every matcher requirement against the observed subject associations using the
  existing normal Map query-side `hash` / `==` law before invoking the first
  mapped-value child matcher. Ignore unrelated subject associations, then invoke
  resolved child matchers exactly once in matcher insertion order through
  ordinary `childMatcher.match(selectedMappedValue)` dispatch. Canonical
  `false` fails immediately, canonical `true` contributes no captures, and each
  non-empty standard Array result contributes its shallowly observed outer
  elements to the positional capture sequence without flattening captured Array
  values themselves. Invalid reached matcher outcomes signal ordinary Error.
  Return canonical `true` when no captures are produced, otherwise return one
  new standard Array in child/capture order. Extend focal coverage for open
  subset behavior, eligibility, complete pre-resolution, matcher order, stable
  observation, normal Map key law, hash count, capture composition, capture
  carrier stability, and invalid outcomes. Specification remains at revision
  `0.1.417`; implementation version becomes `0.3.21-SNAPSHOT`.

## 0.3.20-SNAPSHOT

- Advance `AUD012 — Direct Java guest-execution path audit` (GitHub #541) under
  ratified PLAT035 by migrating another retained-Java runtime and representation
  block from direct `ProtosSourceCompiler.compile(...).call(...)` legacy-AST
  execution to the canonical Bytecode-backed
  `ProtosTestExecutionSupport.evaluate(...)` boundary. Migrate IdentityMap
  represented-value/bootstrap-binding coverage and the ModuleRuntime resolver,
  namespace, canonical-key cache, Actor-local cache, retry/eviction, and
  host-failure translation harnesses. In `ProtosPolymorphicInvocationTest`,
  migrate the represented Object.call parent-materialization and call-spread
  representation cases while deliberately retaining the dedicated nested-call
  compiler/lowering test on `ProtosSourceCompiler`. No Protos-visible language,
  specification, Standard Library, or production runtime semantics change.
  Implementation version becomes `0.3.20-SNAPSHOT`.

## 0.3.19-SNAPSHOT

- Advance `PERF009-B — Quarantined Java test normalization and reintegration`
  (GitHub #546) by reintegrating `ProtosTomlEncoderModuleTest` into the ordinary
  Java lane. Share one Core bootstrap across the semantic encoder class and use
  the canonical `ProtosTestExecutionSupport` execution boundary. Separate
  ordinary nested encode/parse semantic round-trip coverage from the
  input-depth recursion regression into
  `ProtosTomlEncoderDeepContainerRegressionTest`. Historical pre-LIB010-C2
  recursive encoder evidence establishes Array last-pass/first-fail depths
  `74`/`75` and selected guard `139`, and inline
  Table last-pass/first-fail depths `77`/`78` with selected
  guard `142`; the selected guards reproduce `StackOverflowError`
  against the prohibited historical implementation while the current iterative
  encoder passes. Five fresh-JVM repetitions measure
  `ProtosTomlEncoderModuleTest` at at most 3.498 s/class and
  0.656 s/test and the deep-container regression class at at most
  1.792 s/class and 1.036 s/test, satisfying the `< 5 s`
  class quarantine threshold and `< 2.0 s` hard per-test budget with equivalent
  or stronger regression ownership. Remove `ProtosTomlEncoderModuleTest` from
  `JAVA_SLOW_TEST_EXCLUDES`. No Protos-visible language, specification,
  Standard Library, or production runtime behavior changes. Implementation
  version becomes `0.3.19-SNAPSHOT`.

## 0.3.18-SNAPSHOT

- Advance `I041-B3 — structural Array.match publication` (GitHub #550).
  Publish the ratified D131 structural matching protocol on ordinary standard
  Array values. Require the matcher receiver to own standard Array indexed state;
  return canonical `false` for an ineligible subject or an exact-length mismatch;
  and take one shallow snapshot of both matcher and subject Arrays before any
  child matcher is invoked. Invoke reached children exactly once from left to
  right through ordinary `matcherElement.match(subjectElement)` dispatch.
  Canonical `false` fails immediately, canonical `true` contributes no captures,
  and each non-empty standard Array result contributes its shallowly observed
  outer elements to the positional capture sequence without flattening captured
  Array values themselves. Invalid reached matcher outcomes signal ordinary
  Error. Return canonical `true` when no captures are produced, otherwise return
  one new standard Array containing the captures in child/capture order. Extend
  the audited native Array representation boundary for the required state
  snapshot and add focal coverage for eligibility, exact length, evaluation
  order, fail-fast behavior, capture composition, matcher/subject snapshot
  stability, child-capture carrier stability, invalid outcomes, and inherited
  behavior on an ineligible receiver. Specification remains at revision
  `0.1.417`; implementation version becomes `0.3.18-SNAPSHOT`.

## 0.3.17-SNAPSHOT

- Advance `AUD012 — Direct Java guest-execution path audit` (GitHub #541) under
  ratified PLAT035 by migrating retained-Java representation and lifecycle
  harnesses from direct `ProtosSourceCompiler.compile(...).call(...)`
  legacy-AST execution to the canonical Bytecode-backed
  `ProtosTestExecutionSupport.evaluate(...)` boundary. Migrate Map lifecycle,
  Path representation, Array factory materialization, and JSON actor-transfer
  coverage while preserving Java ownership of represented-value state,
  prototype/lookup identity, close/freeze semantics, Actor graph-transfer
  assertions, and host-side runtime inspection. No Protos-visible language,
  specification, Standard Library, or production runtime semantics change.
  Implementation version becomes `0.3.17-SNAPSHOT`.

## 0.3.16-SNAPSHOT

- Advance `I041-B2 — ordinary Object.caseOf publication` (GitHub #550).
  Publish the ratified D131 `caseOf` behavior as an ordinary `Object` message.
  Require an eligible normal standard Map as the case carrier, establish one
  shallow insertion-ordered association snapshot before matcher execution, and
  attempt each observed matcher exactly once through ordinary
  `matcher.match(subject)` dispatch. Canonical `false` continues, canonical
  `true` invokes the selected callable with zero arguments, and a non-empty
  standard Array spreads positional captures into the selected callable.
  Invalid reached matcher outcomes, an invalid selected callable, an ineligible
  case carrier, and no successful case signal ordinary Error. Preserve selected
  callable results unchanged, including `null`, and perform no eager validation
  of unreached matchers or callables. Extend the audited native Object boundary
  only for this representation-owned operation and add focal evidence covering
  case order, capture spreading, null preservation, lazy validation,
  case-carrier snapshot mutation, invalid outcomes, selected-callable
  validation, non-Map carrier, and no-match behavior. Specification remains at
  revision `0.1.417`; implementation version becomes `0.3.16-SNAPSHOT`.

## 0.3.15-SNAPSHOT

- Advance `AUD012 — Direct Java guest-execution path audit` (GitHub #541) under
  ratified PLAT035 by migrating the retained-Java Collections execution block
  from direct `ProtosSourceCompiler.compile(...).call(...)` legacy-AST execution
  to the canonical Bytecode-backed `ProtosTestExecutionSupport.evaluate(...)`
  boundary. Migrate Set algebra and mutation coverage, Array algorithm and
  reduce/sort callback-ordering coverage, and Standard Library resolver
  import/caching/error coverage while preserving Java ownership, actor-local
  Activations, native callbacks, assertions, and semantic coverage. No
  Protos-visible semantics, specification, Standard Library behavior, or
  production runtime behavior changes. Implementation version becomes
  `0.3.15-SNAPSHOT`.

## 0.3.14-SNAPSHOT

- Fix checkout/developer execution after implementation-version changes. Repair
  the PERF009 Java slow-test exclusion list after TOML parser reintegration and
  make `bin/protos` select the exact artifact identified by Maven build metadata
  instead of choosing among stale `target/protos-*.jar` files by lexicographic
  filename order. This prevents an older implementation jar from executing
  against newer Core sources when multiple build artifacts remain in `target/`.
  No Protos-visible language, specification, Standard Library, or production
  runtime semantics change. Implementation version becomes
  `0.3.14-SNAPSHOT`.

## 0.3.13-SNAPSHOT

- Advance `PERF009-B — Quarantined Java test normalization and reintegration`
  (GitHub #546) by reintegrating the TOML parser coverage into the ordinary
  Java lane. Replace the quarantined monolithic `ProtosTomlParserStressTest`
  with eight bounded, responsibility-specific regression classes covering
  nested values, flat documents, paths, lexical keys, string/trivia scanning,
  integers, float/temporal tokens, and arrays of tables. Remove
  `ProtosTomlParserStressTest` from `JAVA_SLOW_TEST_EXCLUDES` and delete the
  superseded stress class. Repeated cold-JVM validation satisfies the existing
  `< 2.0 s` hard per-test budget and `< 5 s` class threshold for all eight
  final classes; the Java lane passes and the full test suite passes with zero
  failures. No Protos-visible semantics, specification, Standard Library
  behavior, or production runtime behavior changes. Implementation version
  becomes `0.3.13-SNAPSHOT`.

## 0.3.12-SNAPSHOT

- Advance `AUD012 — Direct Java guest-execution path audit` (GitHub #541) under
  ratified PLAT035 by migrating a second retained-Java semantic-harness block
  from direct `ProtosSourceCompiler.compile(...).call(...)` legacy-AST execution
  to the canonical Bytecode-backed `ProtosTestExecutionSupport.evaluate(...)`
  boundary. Migrate InvalidSuper execution coverage and the CommandLine module,
  specification-model, and result-model harnesses while preserving Java
  ownership, existing Activations, assertions, error signaling, and semantic
  coverage. No Protos-visible semantics, specification, Standard Library
  behavior, or production runtime behavior changes. Implementation version
  becomes `0.3.12-SNAPSHOT`.

## 0.3.11-SNAPSHOT

- Advance `I041-B1 — standard matcher helper publication` (GitHub #550).
  Publish the ratified D131 `Any` and `Capture` helper matchers as ordinary
  frozen standard-prelude objects. Existing Core topology authority makes both
  direct children of `Object`; no `Matcher`, `Pattern`, hidden intermediate
  prototype, second matcher authority, or new matching capability is
  introduced. `Any.match(subject)` returns canonical `true` and
  `Capture.match(subject)` returns a one-element standard Array containing the
  exact subject reference. Add bootstrap/publication evidence for prelude
  visibility, direct-`Object` parentage, freezing, and exact helper behavior.
  Specification remains at revision `0.1.417`; implementation version becomes
  `0.3.11-SNAPSHOT`.

## 0.3.10-SNAPSHOT

- Advance `AUD012 — Direct Java guest-execution path audit` (GitHub #541) under
  ratified PLAT035 by migrating the first bounded retained-Java semantic-harness
  block from direct `ProtosSourceCompiler.compile(...).call(...)` legacy-AST
  execution to the canonical Bytecode-backed
  `ProtosTestExecutionSupport.evaluate(...)` boundary. Migrate the URI, CSV,
  JSON parser stress, Integer math, and Collections Set harnesses while
  preserving Java ownership, existing Activations, Actor-local module
  caching/isolation, assertions, and stress coverage. No Protos-visible
  semantics, specification, Standard Library behavior, or production runtime
  behavior changes. Implementation version becomes `0.3.10-SNAPSHOT`.

## 0.3.9-SNAPSHOT

- Restore `TOOL006 — public Test Tool resource-requirements wiring` (GitHub
  #473). Public `Main.protos` now applies the existing D094/D091
  `resource-requirements.toml` join to complete manifest-backed corpus plans
  before progress counting and D108 scheduling. True sidecar absence preserves
  the resource-free path; valid requirements reach the existing
  binding/reservation/provider machinery. Reconcile the historical I8C guard
  that kept `ResourceRequirements` out of public Main and strengthen I8D5C
  public-cutover evidence for join ordering. TOOL005 non-manifest loaders remain
  outside this bounded restoration. No Protos language/specification, resource
  schema, catalog, provider, reservation, scheduling, reporting or CLI semantics
  change. Implementation version becomes `0.3.9-SNAPSHOT`.

## 0.3.8-SNAPSHOT

- Publish `I040-C — D136 F-prime Map-construction cutover`. Reconcile `%{...}`
  with the owner-approved D136 initial-association model across the normative
  specification, direct AST execution, C-prime continuation execution,
  conformance corpus, and programmer guide. Construction now requires canonical
  standard Map factory behavior selected directly or by inheritance, creates a
  fresh standard Map with the actual factory receiver as parent, defines each
  initial association without `atPut` dispatch, and signals `Error` for a
  duplicate/equal initial key under the normal Map hash/directed-equality query
  law. Later indexed assignment remains ordinary `atPut`. Specification
  revision becomes `0.1.415`; implementation version becomes
  `0.3.8-SNAPSHOT`.

## 0.3.7-SNAPSHOT

- Advance `PERF009-B — Quarantined Java test normalization and reintegration` (GitHub #546) by reintegrating `ProtosTestToolManifestPlanTest` into the ordinary Java lane. Bound the retained Manifest workloads to the minimum scales that preserve their regression properties, replace incidental host-NIO cost in Manifest-policy cases with deterministic read-only test storage, and separate the D133 project-tree responsibility into `ProtosTestToolProjectTreePlanTest` so independently schedulable correctness responsibilities remain independently bounded. Repeated evidence keeps `ProtosTestToolManifestPlanTest` at 3.158–3.344 s per class with a 0.686 s maximum testcase and `ProtosTestToolProjectTreePlanTest` at 1.825–1.878 s with a 0.857 s maximum testcase, satisfying the existing `< 5 s` class quarantine threshold and `< 2.0 s` hard ordinary-test budget. Remove `ProtosTestToolManifestPlanTest` from `JAVA_SLOW_TEST_EXCLUDES`; the class-parallel Java lane passes 1829 tests with zero failures or errors. No Protos-visible semantics, specification, Standard Library behavior, or production runtime behavior changes. Implementation version becomes `0.3.7-SNAPSHOT`.

## 0.3.6-SNAPSHOT

- Publish `I040-B — F-prime C-prime Map-construction foundation`. Add the continuation-capable internal C-prime machinery required to construct through the canonical standard Map factory and define initial associations with normal Map hash and directed-equality semantics without `atPut` dispatch. Duplicate/equal initial keys are rejected rather than replaced, and the original representative association is preserved. This remains an internal foundation slice: production `%{...}` execution is not yet cut over to F-prime semantics. Specification unchanged. Implementation version becomes `0.3.6-SNAPSHOT`.

## 0.3.5-SNAPSHOT

- Publish `I040-A — F-prime Map construction foundation` for D136 reconciliation. Give the canonical standard `Map.call` implementation stable internal provenance so `%{...}` construction can later admit the standard factory selected directly or through inheritance while rejecting copied or nearer arbitrary `call` behavior. Add an internal create-only initial-association operation that reuses normal Map `hash` and directed `==` query semantics, rejects an equal existing initial key instead of replacing it, preserves the first representative key and recorded hash, and bypasses `atPut`. This slice establishes internal runtime machinery only: production `%{...}` execution remains on the previous E-prime path until the coordinated AST/C-prime cutover. Specification unchanged. Implementation version becomes `0.3.5-SNAPSHOT`.

## 0.3.4-SNAPSHOT

- Correct `PERF009-A — Current full-suite baseline and critical-path attribution` (GitHub #531) by routing `ProtosTomlParserStressTest` through the canonical `ProtosTestExecutionSupport.evaluate(...)` execution bridge instead of invoking `ProtosSourceCompiler` directly. This removes the legacy direct-harness execution distortion from the retained TOML stress measurements while preserving the existing test inputs and assertions, allowing PERF009 attribution to measure the ordinary Protos execution path. No Protos-visible semantics, specification, Standard Library behavior, or production runtime path changes. Implementation version becomes `0.3.4-SNAPSHOT`.

## 0.3.3-SNAPSHOT

- Implement `I040 — Map populated construction syntax implementation` (GitHub #543) for ratified D136, including its approved newline/semicolon layout amendment. Add `%{ ... }` as a primary Map construction expression: resolve ordinary shadow-sensitive `Map`, invoke it exactly once with zero arguments before entry evaluation, then evaluate each key and value in the enclosing activation and perform a fresh ordinary `atPut` dispatch before continuing to the next entry. Construction uses newline or `;` entry separators, rejects comma separation and trailing `;`, preserves fail-fast/non-transactional effects and ordinary control propagation, composes with postfix operations, and introduces no construction activation, guest-visible temporary binding, Association value, IdentityMap sugar, spread/merge form, or matching change. Add C-prime and direct-AST backend support, parser/canonical/static-tooling regression coverage, and Protos/Test-Tool conformance for standard construction, evaluated keys, duplicates, postfix use, outer `this`, factory-before-entry order, sequential insertion, and per-entry `atPut` redispatch. Specification revision becomes `0.1.414`; implementation version becomes `0.3.3-SNAPSHOT`.

## 0.3.2-SNAPSHOT

- Implement `I039 — Array construction syntax implementation` (GitHub #539) for ratified D130. Add `[a, b]` as surface-only construction syntax that lowers to the ordinary shadow-sensitive call `Array(a, b)`, reuses existing argument spread and left-to-right evaluation semantics, composes with ordinary postfix indexing and nesting, and leaves matching Array-pattern syntax separate. Add parser/tooling regression coverage plus TOOL002 conformance for empty/nested construction, spread/evaluation order, postfix indexing, and ordinary `Array` shadowing. Specification revision becomes `0.1.413`; implementation version becomes `0.3.2-SNAPSHOT`.

## 0.3.1-SNAPSHOT

- Reconcile `TEST001-I — Superseded test infrastructure cleanup and final closure` (GitHub #467) by removing the abandoned TEST001-C/D/E/F/G semantic-test ownership registry/guard and its publication-validation coupling. Replace the registry-backed `AGENTS.md` rule with a simpler test-placement contract: prefer Protos/TOOL002 for faithfully expressible Protos behavior, retain Java/JUnit for distinct host/runtime/bootstrap evidence, and leave legacy pre-policy Java-to-Protos migration to TEST002/#538. Existing useful Protos conformance cases are retained. Governance/test-infrastructure cleanup only; no specification, runtime semantics, or implementation-version change.

- Establish `GITHUB020 — Formal work closure-evidence and durable-publication contract` (GitHub #536). Require every formal Issue closure to carry a compact evidence-backed closure summary and an explicit durable-record decision, while keeping publication to `protos-project-docs` conditional on whether durable project knowledge would otherwise be lost or an owning work-item contract explicitly requires it. Required durable publication remains fail-closed with exact revision identities; `Protos Development` remains a derived scheduling dashboard rather than evidence authority. Governance only; no specification, executable behavior, runtime architecture, or implementation-version change.

## 0.3.0

- Ratify `D133 — Test Tool per-case CaseAuthority scheduling boundary` (GitHub #519) as Candidate B′. Keep CaseSpec logical/inert, materialize a physical case-scoped CaseAuthority only at scheduling admission, preserve D108 aggregation/progress/cancellation semantics, and keep project-tree authority separate from generic D077/D098 resources. Governance/tooling decision only; no specification or version change.

- Ratify `D132 — Plain two-column Test Tool manifest semantics` (GitHub #506) as Candidate B′. Keep `Manifest.load(...)` strict for the existing three-column schema and add a separate generic semantic loader for explicit two-column outcome manifests, with explicit case namespace and stable logical CaseId independent from physical paths. Governance/tooling decision only; no specification or version change.

- Publish Protos 0.3.0 as a GitHub pre-release, the first public milestone of the optimized Truffle-runtime era following the PERF006 runtime-integrity closure. The release identifies the immutable validated candidate `e98fcc85855c8cefdb0b205804f81a0357aba51c` from development baseline `705d8238af0407104b15ba1d46e628fd2d243581` (`0.3.0-SNAPSHOT`) at Core specification revision `0.1.412`, tagged `v0.3.0`, with portable archive `protos-0.3.0-posix-jvm.zip` (SHA-256 `2cb9dea7091e391b0bb93d6533367914fe580d6fc05669da0be7e1f4a66c5fe7`).

- Ship the DIST002-validated optimizing runtime contract for distribution: GraalVM Community Edition 25.3.4.1 for JDK 25.0.4.1, Truffle 25.3.4.1, and HotSpotTruffleRuntime as the expected optimizing runtime; the portable POSIX/JVM bundle does not include the JDK, and the Java 21 bytecode target is not a generic Java 21+ support claim.

- Include the DIST003-D1 Test Tool stdout-completion correction in the published artifact: the bundled Test Tool executes its complete bundled corpus end to end (1089 passing cases at this candidate) and emits the exact public bootstrap and argument stdout markers verified by the distribution smoke gate.

- Retain the published pre-release limitations: Core v0.1 remains a draft specification; automatic repository CI stays intentionally suspended while TOOL005 (#468) completes the official corpus-execution path and GITHUB017 (#492) owns reactivation (the eight CI toolchain bindings remain `VALID_KNOWN_LIMITATION`, not active-CI PASS); Test Tool hard-timeout, OS-worker recovery and full corpus-path ownership remain deferred; and the bundled Package Tool external immutable-package execution (TOOL001-F2E) remains incomplete and fail-closed. Ordinary development continues on the existing `0.3.0-SNAPSHOT` line; this entry records only the published pre-release evidence and changes no repository development version.
