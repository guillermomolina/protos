# Protos Implementation Changelog

Current implementation-series changes are kept in this file.

Historical implementation changelogs:

- [0.2.x](changelog/CHANGELOG-0.2.md)
- [0.1.x](changelog/CHANGELOG-0.1.md)

## 0.3.200-SNAPSHOT

- `LM011-D1` (issue #670; D183, PLAT050 Candidate F, PLAT024) exposes the
  canonical formatter over standard LSP `textDocument/formatting`; the server
  now advertises `documentFormattingProvider`. A request formats the current
  open immutable document snapshot, including unsaved edits, through the same
  `ProtosWholeDocumentFormatter` / TOOL010 authority as `protos format`.
  `FormattingOptions` (tab size, spaces, newline preferences) never change the
  canonical style. Success yields zero edits for already-canonical source or
  exactly one full-document `TextEdit`. Invalid or incomplete source, a
  snapshot superseded while formatting, and an unopened URI all yield no
  edits; internal formatter/bootstrap failures still fail the LSP request.
  There is no disk fallback and no user-module execution. The formatter
  runtime host is request-scoped, never persistent. The `PROTOS_HOME`
  toolchain-root lookup is shared between the CLI and the LSP. Range and
  on-type formatting are not advertised and there is no TypeScript formatter.
  No Protos semantic or specification change.

## 0.3.199-SNAPSHOT

- `LM011-C` (issue #670; D184/#796 Candidate B) exposes the public canonical
  formatter command `protos format [<file>]`. With no operand it reads one
  whole UTF-8 document from stdin; with one operand it reads exactly that
  regular file (symlinks to regular files are accepted for reading) without
  executing it or resolving imports. More operands are a usage error.
  Formatting goes only through the existing editor-neutral
  `ProtosWholeDocumentFormatter` / TOOL010 authority. On success stdout holds
  exactly the canonical source and stderr is empty (exit 0). Invalid or
  incomplete source fails closed: the exact original source goes to stdout and
  a `protos format:` diagnostic goes to stderr (exit 1). Missing, unreadable,
  non-regular or malformed-UTF-8 input leaves stdout empty (exit 1). Usage
  errors exit 2, and genuine formatter/bootstrap failures stay internal errors
  (exit 70). The command never modifies files and has no multi-file, directory,
  workspace or package discovery. Check, write, range, on-type formatting and
  style configuration remain deferred. No Protos semantic or specification
  change.

## 0.3.198-SNAPSHOT

- `TEST009-C` (TEST009/#795; PERF030/#784) adds the static Bytecode API
  PE-argument guard (`make check-bytecode-api-pe`, part of `make check`). It
  checks the arguments that `BytecodeNode` local-table operations
  (`getLocalValues`/`getLocalNames`/`getLocalInfos`/`setLocalValues`/
  `copyLocalValues`), `BytecodeNode.get(Node)`,
  `BytecodeRootNodes.update(BytecodeConfig)` and `BytecodeLocation.get`
  require to be partial-evaluation constants. Sinks are found by receiver
  type and arity and followed through helpers to Bytecode DSL operations,
  Truffle DSL specializations, library exports and externally invoked node
  overrides. A boundary cut is accepted as the only safe outcome for runtime
  values, and an unproven or unresolved PE-reachable argument fails. The
  NodeLibrary tag-tree exports read the local table at a runtime tag-tree
  bytecode index, so the two tooling-only helpers that do this are now
  `@TruffleBoundary` and receive the materialized frame. Debugger-visible
  scopes, bindings and local metadata are unchanged.

## 0.3.197-SNAPSHOT

- `LM011-B2` / `TOOL010` (issue #670; D183/#791; PLAT050/#792) adds
  the first canonical whole-document Protos formatter authority. The exact
  bundled formatter under `protos/tools/formatter/` owns D183's fixed
  structural formatting policy in Protos, including 4-space structural and
  continuation indentation, canonical token/delimiter spacing, deterministic
  soft-100 wrapping, brace and sequence layout, Array/Map/Closure and trailing
  Closure forms, and canonical ordinary blank-line/final-LF handling. The B1
  source-layout bridge supplies the required structural, separator, comment and
  exact raw-spelling facts without introducing a CST, second parser/grammar or
  host-side formatter policy. Comments preserve exact raw text, ordering,
  attachment and grammar-significant boundaries; String, numeric and Unicode
  spellings remain exact, including locked triple-double lexical payload
  newlines and horizontal whitespace. The editor-neutral
  `ProtosWholeDocumentFormatter` fails closed for invalid or incomplete source,
  returning the exact original source unchanged without starting TOOL010, while
  genuine bundled-formatter failures remain host failures rather than being
  misclassified as invalid source. Focal correctness gates cover successful
  reparse, deterministic output, exact idempotence, D183 preservation
  invariants and non-executing canonical semantic-AST equivalence; the
  maintainer-reported full suite is 1328 passed, 0 failed. Public CLI spelling,
  LSP `textDocument/formatting`, VS Code integration, range/check/on-type
  formatting, recovery/partial formatting and configurable style remain
  deferred to later LM011 slices. No observable Protos language semantic or
  specification change.

## 0.3.196-SNAPSHOT

- `TEST009-A` (issue #795) adds the static
  `tools/java_local_range_operand_pe_guard.py` (with focal self-tests and an
  exact topology baseline). It proves, per indexed `LocalRangeAccessor`
  operation, that the accessor receiver and the `BytecodeNode` argument are
  partial-evaluation constants (a `LocalRangeAccessor` `@ConstantOperand` and
  a `@Bind("$bytecodeNode")` of the Bytecode DSL operation, propagated
  losslessly through helpers) on every PE-reachable path. It complements the
  PERF030 index guard, whose target is now `make check-local-range-index-pe`
  (`make test-local-range-pe-guard` remains an alias). The new guard runs
  as `make check-local-range-operands-pe`, and both deterministic guards
  (about 2 s each) now run in `make check`. The guard found five
  PE-reachable sinks whose accessor and node came from the per-invocation
  `ProtosFrameLexicalBindingAuthority` (a field and the declaring root's
  current node); its captured-read/assign and indexed-creation seams are now
  `@TruffleBoundary`. No observable language semantic or specification change.

## 0.3.195-SNAPSHOT

- `CLI008-D` (issue #312) reconciles terminal Error presentation across the
  REPL, `-e`, direct-file, workspace and bundled Tool surfaces so an available
  `ProtosDiagnosticTrace` is never silently dropped. The bundled Tool launcher
  now presents a FAILED outcome directly with its existing
  `<Tool> tool error: ` prefix followed by the occurrence's guest frames,
  instead of rebuilding a trace-less `ProtosSignalException`; the `-e` and
  direct-file `ProtosSignalException` fallbacks render an attached terminal
  trace like the REPL already did. All surfaces share one summary-plus-trace
  presenter. Summary prefixes, program stdout, exit-code policies, COMPLETED
  Test Tool classification and CANCELLED/host runtime-error handling are
  unchanged; no trace is recaptured in the CLI. No observable language
  semantic, specification, print, serialization, PLAT049 capture, Test Tool or
  Package Tool change.

## 0.3.194-SNAPSHOT

- `BUG015` (issue #794) fixes a ready-before-suspend lost wakeup in
  `ProtosParallelRuntime.ownedFuture()`. When the P outcome became ready after
  the producer Task's pending check but before `suspend()`, `suspend()` returned
  `false` and left the Task `RUNNING`, yet the continuation returned anyway; the
  Task was then neither running, suspended, runnable nor terminal, and
  `dispatchUntilTerminal()` could wait forever (observed as an intermittent
  `array-parallel-sort.protos` stall under `--jobs 8`). The producer now returns
  only on a true suspension, consumes the ready outcome while still `RUNNING`,
  and returns without consuming it when cancellation won and the Task was
  re-enqueued, matching the existing `Future.then()` contract. Deterministic
  Java regressions cover both the ready and the cancellation-wins paths through
  a package-private test seam. No observable language semantic or
  specification change.

## 0.3.193-SNAPSHOT

- `PERF030-N` (issue #784, `F3_GENERIC_NAME_TO_RANGE_INDEX`) cuts the remaining
  three PE-reachable runtime-name-derived `LocalRangeAccessor` sinks at the
  generic frame-backed lexical membership/read boundary. `containsBinding(String)`
  and `readBinding(String)` are now `@TruffleBoundary` slow paths for genuinely
  dynamic names, while statically selected lexical execution continues to use
  the existing indexed/ordinal seams. The final LocalRange PE inventory remains
  36 sinks with 13 PE-reachable proven-constant sites, 19 boundary cuts,
  4 non-PE-reachable sites, 0 PE-reachable risks and 0 unknown reachability.
  `PRESENT(null)` versus `ABSENT`, D179 late-nearer retargeting, capture by
  reference, current-`BytecodeNode` coherence, dynamic overflow behavior,
  deferred Context laziness and debugger/reflection projection are preserved.
  No observable language semantic, specification or benchmark change.

## 0.3.192-SNAPSHOT

- `CLI008-C1` (issue #416, PLAT049 Candidate C) adds an occurrence-carried,
  bounded guest diagnostic trace for terminal Errors. When an Error
  occurrence fails a Task (or escapes a REPL unit), its Truffle guest stack
  is projected once, before the throwable is discarded, into an inert
  `ProtosDiagnosticTrace` of at most 64 semantic guest frames (innermost
  kept, `truncated` flag). Only `ProtosSemanticBytecodeRootNode` frames are
  projected; continuation roots normalize to their semantic source root,
  structured-dispatch/C-prime, host and scheduler frames are excluded, and
  frames live at the capture point (the Task's host) are cut off. PLAT044 B′
  inline callbacks contribute exactly one frame each through their nested
  `RootTag` (source information plus `RootTag` are materialized lazily only
  for a root failing inside a live inline region). The trace is stored on
  the failing Task at its first failure commit (surviving child drain), is
  never forwarded to the associated Future, and travels as optional metadata
  on FAILED `ProtosExecutionOutcome` without changing `error()` identity.
  The CLI (`-e`, file run, workspace `run`, REPL) prints `  at
  <source>:line:column` lines after the existing `Error:` summary on stderr.
  Success paths do no new diagnostic bookkeeping; the Error value is never
  mutated. No observable language semantic or specification change.

## 0.3.191-SNAPSHOT

- `PERF030-M` (issue #784, `F4_PE_VISIBLE_LIFECYCLE_RANGE_SCANS`) moves the
  10 PE-visible lifecycle `LocalRangeAccessor` scans in
  `ProtosFrameLexicalBindingAuthority` and
  `ProtosInlineCallbackFrameBindings` behind dedicated `@TruffleBoundary`
  helpers. General establishment-order materialization, adoption of already
  PRESENT frame-backed bindings, and authority handoff now perform their full
  range scans outside partial evaluation while preserving the existing cheap
  guards and current-`BytecodeNode` resolution where applicable. Inline
  durable activation keeps the `frameBindingsTransferred()` fast guard outside
  the boundary and materializes the frame only for the first transfer so the
  slow helper receives a `MaterializedFrame`. No `@ExplodeLoop` is introduced.
  The final LocalRange PE inventory remains 36 sinks: 13 PE-reachable proven
  constant, 3 PE-reachable risks, 16 boundary cuts, 4 non-PE-reachable and 0
  unknown; only `F3_GENERIC_NAME_TO_RANGE_INDEX` remains PE-reachable risk.
  Establishment order, compact/general transition, `PRESENT(null)` versus
  `ABSENT`, remove/recreate order, authority handoff, inline activation
  laziness and first-observer transfer, and debugger/tooling observation are
  preserved. No observable language semantic, specification or benchmark
  change.

## 0.3.190-SNAPSHOT

- `LM011-B1` (issue #670, D183 Candidate B, PLAT050 Candidate F) adds the
  on-demand editor-neutral source-layout and source-preservation foundation for
  the future canonical Protos formatter. Exact immutable source remains the raw
  spelling authority while tooling-only demand can retain horizontal
  whitespace, line comments and block comments without changing the ordinary
  token stream; logical newlines remain parser tokens and block-comment
  internal newlines remain non-parser newlines. The canonical parser can
  additionally expose source-only sequence separator kind, bare versus
  parenthesized single-parameter Closure form and trailing-Closure origin.
  `ProtosSourceLayoutView` groups the exact snapshot, canonical
  `TokenOccurrence`s and existing Surface AST with immutable trivia/source
  facts, source-position-independent structural paths and deterministic
  `OWN_LINE`, `END_OF_LINE` and `EMBEDDED_BETWEEN_TOKENS` comment
  attachments. Its D183 preservation projection retains raw token/comment
  spellings, structural/grouping/Array/Map forms, separator kinds, Closure
  forms and trailing-Closure relationships without using source offsets as
  structural identity. The ordinary lexer/parser/static-analysis path retains
  no formatter-only metadata and allocates no sequence-separator `SourceSpan`
  when source facts are not requested. No formatter transformation, CLI/LSP
  integration, style configuration, CST/lossless syntax layer, language
  semantic change or specification change is introduced.

## 0.3.189-SNAPSHOT

- `PERF030-L` (issue #784) removes the PE-visible frame-local rescan from
  `ProtosFrameLexicalBindingAuthority.isEmpty()`. Empty-state queries now use
  the authority's existing establishment metadata in O(1): general mode reads
  `establishmentOrder`, while compact mode reads `lastCompactFrameOrdinal`;
  the defensive dynamic-overflow check is retained. Focused regression
  coverage verifies non-empty, empty and recreate transitions in both compact
  and general modes, including remove/recreate ordering. The LocalRange PE
  guard removes exactly the F5 `isEmpty()` sink from both baselines, leaving
  36 sinks: 13 PE-reachable proven constant, 13 PE-reachable risks, 6 boundary
  cuts, 4 non-PE-reachable and 0 unknown. Observable language semantics,
  specification behavior and benchmarks remain unchanged.

## 0.3.188-SNAPSHOT

- `PERF030-K` (issue #784) removes the structurally unreachable inline
  captured-access range probe from `ProtosInlineCallbackFrameBindings`.
  Binding analysis and lowering already restrict these direct operations to
  `CapturedResolved` owners in established outer lexical scopes, so
  `admitsCapturedAccess` now depends only on an unmaterialized activation and
  positive lexical depth instead of resolving the runtime name back into the
  callback's current frame layout. Focused binding-analysis coverage makes the
  ownership invariant explicit: an outer established name is
  `CapturedResolved`, while a declaration owned by the current scope is
  `Candidate` before its creation and `Resolved` afterwards. The LocalRange PE
  guard removes exactly the obsolete F6 sink from both baselines, leaving 37
  sinks: 13 PE-reachable proven constant, 14 PE-reachable risks, 6 boundary
  cuts, 4 non-PE-reachable and 0 unknown. Capture by reference, D179 late
  nearer-binding behavior, inline activation laziness, pre-RHS captured-write
  destination selection, selected-owner mutation behavior, and observable
  language semantics remain unchanged. No specification or benchmark change.

## 0.3.187-SNAPSHOT

- `TOOL001-F2E5` (issue #93, PLAT048 B′) completes the current bounded
  external immutable-package run closure by adding an implementation-private,
  read-only exact local materialization backend and routing public
  `protos run <entry> [args...]` through `ProtosPackageRunDriver`. The backend
  derives one deterministic opaque private path from the complete typed exact
  identity (registry/Git kind, PackageId, exact version/revision and
  ContentIdentity), performs no directory enumeration, alternative lookup,
  fetch, network access, lock mutation, package-store writes, repair or
  garbage collection, and fails closed when the exact already-present root is
  missing. The selected root still passes unchanged through the existing F2E2
  capture+ContentIdentity verification, F2E3 planning and F2E4
  detach/reconciliation before application execution. Workspace-only public
  runs remain on the generation-1 path and do not consult the materialization
  provider. The physical materialization root/key layout is implementation
  private and introduces no CLI option, environment variable, public
  configuration or canonical package-store contract. Focused coverage verifies
  complete-identity separation, fail-closed missing materializations,
  workspace-only zero lookup and a public mixed run that imports and executes
  source from an exact verified external package. No language or specification
  semantic change.

## 0.3.186-SNAPSHOT

- `PERF030-J` (captured-owner constant ordinal pipeline, issue #784) carries
  the statically proven frame-layout ordinal for captured frame-native reads,
  captured writable-destination resolution, and post-RHS captured assignment
  as an `int` `@ConstantOperand` on both ordinary and inline Bytecode
  operations. `CapturedLexicalWriteTarget` now retains the exact owner and
  authority selected before RHS evaluation without retaining the ordinal, so
  assignment still mutates exactly that preselected destination and never
  re-resolves after RHS effects while the `LocalRangeAccessor` index comes
  from operation-constant structure. The static PE reachability guard
  reclassifies the three F2 sinks (`hasFrameBackedBindingAt`,
  `readFrameBackedBindingAt`, and `assignFrameBackedBindingAt`) from
  `PE_REACHABLE_RISK` to `PE_REACHABLE_PROVEN_CONSTANT`, leaving 13 proven
  constant sinks, 15 PE-reachable risks, 6 boundary cuts, and 4 non-PE-
  reachable sinks. Captured-by-reference behavior, late-nearer retargeting at
  resolution time, selected-owner removal failure, CLOSED/FROZEN mutation
  rules, and inline activation laziness are unchanged. No semantic or
  specification change.

## 0.3.185-SNAPSHOT

- `TOOL001-F2E5` (issue #93, PLAT048 B′) adds the CLI-neutral
  `ProtosPackageRunDriver`, composing the exact external requirements preflight
  with a host-supplied `ProtosExactPackageMaterializationProvider` (complete
  exact identity -> one already-present selected root). When there are no
  external requirements the run is the unchanged generation-1
  `ProtosWorkspaceRunDriver` route and the provider is never consulted.
  Otherwise each requirement, in lock order, gets exactly one provider lookup
  and one F2E2 capture+verification, then F2E3 planning, F2E4 detach and
  resource-scope reconciliation, and one mixed V2 application Process. The
  driver owns every verified custody until reconciliation succeeds (provider
  miss, verification, planning, detach or reconciliation failure closes them
  all, with no application Process started); afterwards the scope owns them
  and is closed only after the application Process has TERMINATED.
  `VerifiedExternalPackage.fromIdentity` builds planning inputs from exact
  identities, and the application bootstrap of
  `ProtosWorkspacePackageApplicationExecution` is shared through a small
  `executeEntry` helper with unchanged workspace behavior. No default
  materialization backend, CLI, specification or semantic change.

## 0.3.184-SNAPSHOT

- `PERF030-I` (lowerer-known establishment ordinals, issue #784) makes every
  frame-layout ordinal the lowerer already knows reach its
  `LocalRangeAccessor` access as an `int` `@ConstantOperand` instead of a
  dynamic `emitLoadConstant` operand. This covers the frame-native parameter,
  rest and current creation operations of both Bytecode roots, the
  persistent-authority indexed parameter and rest operations, and the
  inline-callback parameter and creation operations. Frame-native multiple
  creation no longer indexes an ordinal array at run time: it is lowered to
  one observation of the complete fixed source prefix
  (`ObserveMultipleCreatePrefix` / `ObserveInlineMultipleCreatePrefix`)
  followed by one scalar constant-ordinal creation per name, in source order,
  with no rollback. The array-based `MultipleCreateFrameLocals`,
  `MultipleCreateInlineFrameLocals` and inline `multipleCreate` are removed.
  The static LocalRangeAccessor PE guard now classifies the six affected sinks
  as `PE_REACHABLE_PROVEN_CONSTANT` (10 proven, 18 remaining risks); the
  reachability baseline is updated accordingly. No semantic or specification
  change.

## 0.3.183-SNAPSHOT

- `TOOL001-F2E5` (exact external requirements preflight, PLAT048 B′) adds the
  bundled Package Tool operation `ExecutionPlan.exactExternalRequirements`. It
  validates the current lock against the current ResolutionRoot (header, root,
  workspace membership and workspace-declared dependencies) and returns one
  `{ref, content}` per locked registry/Git node, before any external package
  is captured. Exports and external-declared dependencies stay with the later
  F2E3 planning over the same verified captures. The current-lock, external-node
  index and workspace-dependency checks move into shared helpers used by
  `buildV2FromVerifiedCaptures` as well; the V2 planner's behavior is unchanged.
  The host-side `ProtosExactExternalRequirementsPreflight` runs that operation in
  a fresh Package Tool Process over a read-only confined project Filesystem. It
  detaches the result into a list of `ProtosExactExternalPackageIdentity` values,
  using the exact V2 ref/content shape checks, and rejects duplicates only by
  complete exact identity. The process is terminated before the call returns,
  and the result holds no Path, locator, Filesystem, custody or guest value.
  No materialization provider, public-run wiring, CLI or specification change.

## 0.3.182-SNAPSHOT

- `TOOL001-F2E4` (mixed V2 resolver and lazy external source loading) adds
  `ProtosPackageExecutionPlanV2ModuleResolver`. It routes `self:`, `dep:` and
  `std:` over one detached mixed workspace/registry/Git PackageExecutionPlanV2
  graph using immutable exact-NodeRef, exact-external-identity and
  `declaring NodeRef + alias -> target NodeRef` indexes, so distinct versions,
  revisions, registry/Git kinds and same-content identities never collapse.
  Importers are identified only from canonical workspace or external
  ModuleKeys; `dep:` always goes through the exact edge and target exports.
  Workspace sources keep the unchanged generation-1 physical rules. External
  sources are read lazily per load from the borrowed
  `ProtosExternalPackageResourceScope`, decoded as strict UTF-8 and returned
  without a physical path; no source/store Path is reopened and nothing is
  cached. The resolver never closes the scope; loads fail after its owner
  closes it. No public-run wiring, CLI or specification change.

## 0.3.181-SNAPSHOT

- `TOOL001-F2E4` (verified-custody resource scope) implements the PLAT012
  authority/lifetime layer for external packages. `CapturedBackend` gains a
  host-only `readRegularResource` projection, and the captured-tree backend
  implements it over its existing tree, blobs and leases. It reads exact
  regular resources by a package-relative `ProtosPackageResourceName` without
  Activation, guest Filesystem, Process, Actor, Context or any source/backing
  Path. Absolute, `.`/`..`, empty and NUL names are rejected, and links,
  other entries, directories and missing entries fail closed. Each read
  returns a detached copy and uses its own channel, so independent reads run
  concurrently. `ProtosCapturedFilesystemCustody.readResource` fails after
  close. `ProtosExternalPackageResourceScope.reconcile` reconciles the detached
  PackageExecutionPlanV2 external identities exactly 1:1 with verified
  custodies (via the new `VerifiedExternalPackage.identity()`), keyed by the
  full exact identity. Missing, extra, duplicate or mismatched identities and
  reused custodies fail closed with no partial scope. A successful scope owns
  its custodies and closes each exactly once, idempotently. Planning remains a
  borrower. No resolver, source decoding, cache or public-run wiring is added,
  and no observable semantics change.

## 0.3.180-SNAPSHOT

- `TOOL001-F2E4` (first slice) adds the host side of D053/PLAT012 external
  package identity. `ProtosPackageExecutionPlanV2Adapter` defensively detaches
  the ordinary-Protos PackageExecutionPlanV2 into the immutable host
  `ProtosPackageExecutionPlanV2`. The detach requires exact shapes and typed
  workspace/registry/Git NodeRefs, carries ContentIdentity only on external
  packages and location only on workspace packages, and keeps nodes unique by
  exact NodeRef, so several exact versions or revisions of one PackageId may
  coexist. It also checks root and dependency closure and rejects duplicate
  `(declaring, alias)` pairs. `ProtosExactExternalPackageIdentity` represents
  `(kind, PackageId, exact version or revision, ContentIdentity)`.
  `ProtosExternalPackageModuleKey` is a strict canonical `ProtosModuleKey`
  codec whose domain is disjoint from workspace keys. It encodes that identity
  plus the internal logical module and excludes aliases, exports, locators and
  physical provenance. Generation-1 detach is unchanged. No resolver, custody
  index, source loading or public-run wiring is added, and no observable
  semantics change.

## 0.3.179-SNAPSHOT

- `PERF030` establishes a statically proven target-less creation of a root
  that installs its persistent frame authority through the new
  `CreateCurrentIndexedLocalSlot` operation, whose layout and frame ordinal are
  Bytecode DSL constant operands taken at lowering time. While that root's
  authority admits creation and stores the expected layout, the binding is
  created at its known ordinal (still rejecting a PRESENT binding as a
  duplicate creation); any other state takes the unchanged named
  `CreateCurrentLocalSlot` path. The ordinary path no longer resolves the frame
  ordinal from the runtime binding name before its frame-local accesses, which
  partial evaluation could not reduce to a constant. No observable semantics
  change.

## 0.3.178-SNAPSHOT

- `I058` implements D160 (Candidate B): the parallel Array algorithms move from
  Core to the Standard Library. `std:collections/Array` adds the module
  functions `parallelMap`, `parallelFilter`, `parallelFindIndex`,
  `parallelReduce`, and `parallelSort`, each taking
  `(array, callback, ...arguments)` and returning a Future. They are ordinary
  Protos source composed only from `Closure.parallel`, Futures, `Future.all`,
  and `ensure`: a synchronous P snapshot of elements, callback, and arguments
  at call time; one isolated P child per callback invocation; results in
  source order; lowest-index failure selection; outcome validation
  (`InvalidPredicateResult`, `InvalidComparatorResult`,
  `InvalidComparatorOrder`) in the caller in ascending index order; and
  cancellation of unfinished children when the result Future is cancelled.
  `parallelReduce` combines adjacent pairs round by round, and `parallelSort`
  reproduces the stable sequential `sort` with each comparator invocation in
  its own P domain.
- The Core Array prototype no longer has `parallelMap`, `parallelFilter`,
  `parallelFindIndex`, `parallelReduce`, or `parallelSort`, and no
  compatibility alias exists; calling them on an Array now signals
  `SlotNotFound`. `ProtosParallelRuntime` drops `installArrayParallel`, the
  native indexed/reduce/sort algorithms, their staging/transfer helpers, and the
  Completion forwarding/abandon/cancel-action machinery, leaving only
  `Closure.parallel`. The native-boundary audit shrinks accordingly.
- The isolated-parallel example and the canonical `parallel-array-map`
  benchmark use `Arrays.parallelMap(...)` and the other module functions.

## 0.3.177-SNAPSHOT

- `PERF030` makes the statically known `CreateCurrentFrameLocal` frame-local
  ordinal a Bytecode DSL constant operand, as PERF029 did for
  `BindClosureFrameParameter`. Lowering no longer emits that ordinal as a
  runtime stack operand, so every `LocalRangeAccessor` access it drives sees a
  partial-evaluation constant index. This addresses the permanent
  partial-evaluation constant-index bailout identified as `8231|Pi`. The
  compact-call direct creation path and the activation fallback (including
  duplicate creation Errors, OPEN/frozen/conflict rules, and the returned
  value) are unchanged. No semantic change.

## 0.3.176-SNAPSHOT

- `I057` implements D161: removes `ByteRegion` and writable parallel-range
  reservations. Standard `Bytes` no longer installs `parallelRange`, so sending
  it follows ordinary missing-selector lookup (`SlotNotFound`). The
  `ProtosByteRegionValue` runtime family is deleted. `ProtosBytesValue` loses
  all reservation state and checks, and `Bytes.at`/`atPut`/`add`/`removeAt`
  have no reservation-dependent failure path. `ProtosParallelRuntime` drops
  region creation, reservation, the commit-on-publication and release-on-cancel
  hooks, and `ProtosFutureValue.resolveWithCommit`. Ordinary
  `Closure.parallel`, Array parallel operations, P snapshot/result transfer, and
  Future cancellation are unchanged. The Core Error prototypes
  `ParallelRegionOverlap`, `ParallelRegionInUse`, and `ParallelRegionOutsideP`
  are removed from the taxonomy, the prelude, and the Test Tool error-kind
  mapping. The ByteRegion-specific Actor/detached transfer exclusions and the
  diagnostic projection are also removed. The retired reservation unit test is
  deleted, and a conformance test now guards the removed surface. Implements
  specification `0.1.439`.

## 0.3.175-SNAPSHOT

- `PERF025` repairs the slice 3 inline callback consumer evidence. A
  non-canonical guarded Integer send inside a frame-native inline callback (for
  example `n < 3`) now prepares its native invocation with the
  provenance-equivalent caller, as the ordinary-send and direct-Closure-call
  consumers already do, instead of materializing the callback activation. The
  focal test now warms each scenario past the Bytecode DSL uncached-interpreter
  threshold before observing the specialized cached tier, and reads bindings
  from the durable authority once they have been transferred. No observable
  semantics change.

## 0.3.174-SNAPSHOT

- `I063` follow-up: removes stale append wording from the
  `ProtosStandardFilesystemProtocol` Javadoc (File capability descriptors match
  the captured read/write authority; captured-tree backends reject
  write/create/truncate opens). No behavior change.

## 0.3.173-SNAPSHOT

- `I063` implements D170 by removing the standard File append institution.
  `Filesystem.open` no longer accepts an `append` option: it is now an ordinary
  unknown option and fails with `InvalidIOArgument` before backend authority is
  exercised. `ProtosFilesystemOpenOptions` drops its write-placement state, and
  `ProtosFileFlow` drops `AppendWritableResource`, `AppendCompletion`, the
  append-only write path, the append capability flag and the cross-alias
  append-placement contract. `ProtosStandardFileProtocol` now has a single
  `create` factory. Every writable File writes at its own logical position.
  `ByteSeekable` (`position`, `seek`, `seekBy`, `seekToEnd`), `ByteSized`,
  `Truncatable`, `Syncable`, truncate-on-open and the generic
  commitment/cancellation/lifecycle machinery are unchanged, and backends still
  expose only the capabilities they can honestly support. The Standard Library
  `Files` helper no longer passes `append: false`.

## 0.3.172-SNAPSHOT

- `PERF029` removes the two permanent Truffle partial-evaluation bailouts
  reached by an ordinary source-backed Closure call. The frame-native
  `BindClosureFrameParameter` operation now receives its local ordinal as a
  Bytecode DSL constant operand, so every `LocalRangeAccessor` access it
  performs uses a partial-evaluation constant index. The name-keyed
  `ProtosFrameLexicalBindingAuthority.putBinding`, whose frame ordinal is
  resolved from a runtime name (for example while an authority handoff
  migrates existing bindings), is now a Truffle boundary instead of being
  partially evaluated. Observable Protos semantics are unchanged.

## 0.3.171-SNAPSHOT

- `I065` lets a fresh application Process executed on a caller-supplied
  `ProtosPolyglotRuntimeHost` carry an explicit, already-selected Network grant
  into the existing D047 bootstrap authority model: the grant binds the initial
  `moduleContext` local `network` slot, and without a grant the slot remains
  absent. The temporary-host convenience overload rejects a request carrying a
  grant before any guest execution, so a Network is never used by a Process
  hosted outside the RuntimeHost that provisioned it. Captured tool Processes
  remain Network-less. No CLI, workspace or session selection syntax is added.

## 0.3.170-SNAPSHOT

- `I078` records the D180 Candidate B rename of the standard Closure pre-test
  loop selector from `while` to `whileTrue`, as already represented by the
  current product state. `condition.whileTrue(body)` is the only standard
  pre-test loop selector: the standard `while` selector is removed with no
  compatibility alias, and no standard inverse `whileFalse` selector is
  introduced. The rename preserves every non-name D044 semantic (receiver and
  argument validation, exact canonical Boolean condition results, pre-test
  order, `null` result, Error/non-local-return/suspension/cancellation
  propagation, and no implicit Future adoption). Ordinary lookup, shadowing,
  extraction and override of the `Object.whileTrue` slot are unchanged, and
  user-defined selectors named `while` remain ordinary members. No dedicated
  loop syntax, truthiness or selector intrinsic is introduced. Repository-owned
  standard-loop call sites, the normative specification, runtime protocol
  publication, conformance and Java tests, and documentation were migrated
  coherently.

## 0.3.169-SNAPSHOT

- `PERF025` specializes the residual consumers of a frame-native PLAT044 B′
  inline callback so that their successful path no longer materializes the
  callback's semantic activation. New carrier-operand operations take the
  invocation's `PreparedInlineLiteralCall` for statically resolved captured
  reads, PERF028-A captured write-target selection and assignment, canonical
  guarded Integer sends, guarded and fast ordinary source sends, direct
  source-Closure calls, `this`, member reads (both PIC tiers) and fixed-prefix
  multiple creation. Selection uses the invocation's prelude; a child
  invocation is prepared with the callback's compact caller only when the
  callback would inherit every provenance field from it, otherwise the
  callback activation is materialized. Nearer-binding (D179), absent-binding,
  non-canonical, structured, generic and Error paths still materialize the
  activation through the existing durable transfer and run the unchanged
  operations. `context`, debugger scopes and non-local return remain
  observers. No observable semantics change.

## 0.3.168-SNAPSHOT

- `PERF025` lazily materializes the semantic activation of PLAT044 B′ inline
  literal callbacks. The inline region now keeps the invocation's
  `PreparedInlineLiteralCall` carrier instead of an eagerly built
  `ProtosActivation`; the callback's one fresh activation is materialized
  from the carrier's compact frame arguments at most once, by the first
  semantic or tooling observer, and every later observer of the same
  invocation, including resumption after a yield, receives that same
  instance. For callbacks whose bindings are frame-native, parameter binding,
  arity checks, current-local creation, resolved reads and PERF028-A resolved
  writes operate on the callback's block locals through new carrier-operand
  operations and never materialize the activation on their ordinary path. The
  activation of such a callback is only ever handed out through the new
  `MaterializeInlineCallbackActivation` operation (and its tooling
  counterpart), which, before returning it, moves the PRESENT block-local
  bindings in layout order into a fresh durable map-backed authority installed
  on the activation and relinquishes the block locals; no Context can
  therefore observe, or keep aliasing, the block locals that the next
  invocation reuses. The debugger projects that durable activation instead of
  a suspension-local snapshot. D179 presence semantics, destination selection
  before RHS evaluation, FROZEN/mutation Errors, arity Errors, non-local
  return, suspension, B′ admission and the context-observing fallback path are
  unchanged. New PERF025 focal coverage freezes lazy entry for zero-, one- and
  two-parameter callbacks, at-most-once and per-invocation identity, durable
  transfer before the first observer returns, non-aliasing after region reuse,
  on-demand debugger materialization, suspension and non-local return.

## 0.3.167-SNAPSHOT

- `PERF025` replaces eager O(n) `IdentityMap` snapshot copying with a
  generation-backed copy-on-write representation while preserving the existing
  shallow logical snapshot contract and exact semantic-identity lookup.
  `keyedSnapshot()` now publishes and reuses a read-only insertion-order view
  of the current generation, while `associationSnapshot()` projects the same
  generation without allocating one host `Map.Entry` pair per association.
  Once either snapshot is published, the first later append, replacement or
  removal detaches the live map to a fresh generation with fresh entry objects
  and rebuilt recorded-identity-hash buckets; previously published generations
  therefore retain their exact representative key references, mapped value
  references, membership and order. Mutations before publication and subsequent
  mutations before another publication remain in-place, so never-snapshotted
  maps pay no copy-on-write cost. Standard `IdentityMap.each` retains the stable
  generation view directly instead of immediately copying it again. Existing
  exact recorded-hash bucket lookup, semantic `===` collision resolution,
  remove/reinsert ordering, open/closed/frozen behavior, Actor transfer and
  isolated-P transfer remain unchanged. New PERF025 focal coverage freezes
  generation reuse, detach-on-first-mutation, historical snapshot stability,
  collision-bucket preservation, read-only exposure and P-isolation after
  capture. Existing affected `each`, physical-index, Actor-transfer and
  collection-state regressions plus the full test suite remain green. This is
  an internal physical-representation optimization only: no observable Protos
  semantic, specification or platform decision changes, and no performance
  magnitude is claimed.

## 0.3.166-SNAPSHOT

- `PERF025` replaces eager O(n) Array snapshot reference copying with a
  generation-backed copy-on-write representation while preserving the existing
  shallow logical snapshot contract. `indexedSnapshot()` now publishes and
  reuses a read-only view of the current indexed generation without copying the
  complete element-reference sequence; the first later indexed replacement
  detaches the live Array to a fresh generation before writing, so previously
  established snapshots retain their exact captured references and order.
  Replacement before snapshot publication remains in-place, repeated snapshots
  of an unchanged generation reuse the same view, constructor input remains
  defensively owned, and open/closed/frozen mutation behavior plus validation
  ordering remain unchanged. Existing Array iteration, matching, Actor transfer,
  isolated-P transfer and interop consumers continue through the same snapshot
  API. New PERF025 focal coverage freezes generation reuse, detach-on-first-write,
  old-snapshot stability, read-only snapshot exposure and state-boundary
  behavior. This is an internal physical-representation optimization only: no
  observable Protos semantic, specification or platform decision changes, and
  no performance magnitude is claimed.

## 0.3.165-SNAPSHOT

- `PERF025-G2` reduces fixed physical RootTask/Task bookkeeping cost without
  changing observable Task, Future, Actor, cancellation or structured-concurrency
  semantics. The Actor execution domain now tracks live Tasks in an
  identity-backed set rather than a `LinkedHashSet`, preserving exact live-Task
  enumeration for Actor termination while avoiding per-entry linked-set nodes
  and any incidental iteration-order dependency. `ProtosTask` structured-parent
  provenance is now immutable after construction, and internal terminal
  bookkeeping reads that parent directly instead of reacquiring the child Task
  monitor and allocating an `Optional`. Terminal publication also no longer
  reacquires the Task monitor solely to revalidate the exact terminal state
  already established by its private callers. The existing
  `beginDirectDispatch()` plus `runContinuation()` first-execution path is
  deliberately retained so pre-start cancellation and Actor-termination races
  keep the established boundary. Focused Task/domain, terminal-lifecycle,
  Actor-termination and Future regressions remain green. This is an internal
  runtime bookkeeping optimization only: no observable Protos semantic,
  specification or platform decision changes, and no performance magnitude is
  claimed.

## 0.3.164-SNAPSHOT

- `PERF025` makes hot guest Bytecode Context acquisition node-aware in both
  generated Protos interpreters. Direct Closure-call and send specializations
  now bind the current `ProtosLanguageContext` through the current Truffle
  `Node` and `ContextReference.get(node)` instead of routing those hot
  specializations through the host-level Polyglot current-Context probe plus a
  node-less Context reference. The already-proven entered Context is also
  propagated into guarded Integer specialization establishment and
  Context-owned Bytecode plan derivation instead of being rediscovered from
  ambient runtime state inside those helpers. The existing
  `enteredContext == cachedContext` guards, Context-local executable projection,
  PLAT001 ownership, ordinary fallback paths and host/boundary callers without
  an adopted guest node remain unchanged. Focused direct-Closure, compact-call,
  guarded-Integer and structured-send regressions plus Actor, isolated-P and
  nested-CallTarget multi-Context routing regressions remain green. This is an
  internal hot-path lookup/propagation optimization only: no observable Protos
  semantic, specification or Context-policy change is introduced, and no
  performance magnitude is claimed.

## 0.3.163-SNAPSHOT

- `PERF025` preserves already-established Unicode-scalar-sequence proof and
  derived scalar cardinality across Core String operations instead of
  repeatedly forgetting and reconstructing that information. Arbitrary host
  `String` ingress still validates the complete UTF-16 representation and
  rejects unpaired surrogates, but now derives the exact semantic scalar count
  in that same traversal and stores it immutably in `ProtosStringValue`.
  Standard `String.size` reads the cached count directly; standard `String.at`
  creates its already-proven one-scalar result without revalidation; standard
  `String +` and variadic `String.concat` compose validity and scalar counts
  from already-valid semantic String operands without rescanning the complete
  derived text. Actor transfer, isolated-P transfer, detached execution and
  Test Tool discovery rematerialization likewise preserve the proof metadata
  while retaining fresh wrappers where their existing isolation/copy policy
  requires one. String identity/equality/hash, interop, strict String domains,
  exact scalar-sequence semantics and the prohibition on implicit Unicode
  normalization remain unchanged. New PERF025 focal regressions freeze the
  cached-count and proof-preserving productive paths. This is an internal
  representation/runtime-cost optimization only: no Protos specification or
  platform decision changes, and no performance magnitude is claimed.

## 0.3.162-SNAPSHOT

- `PERF025` makes `Bytes` and recursive `ByteRegion` reservation coordination
  pay only while isolated-parallel byte regions are actually live. Ordinary
  indexed size/read/write/add/remove and snapshot operations no longer acquire a
  Java monitor merely because `parallelRange` exists, and fresh values no
  longer allocate an empty reservation list. The first non-empty reservation
  lazily publishes an immutable reservation snapshot; zero-length ranges remain
  allocation-free, disjoint reservations coexist, overlap checks retain the
  existing half-open interval semantics, and releasing or committing the last
  reservation returns the value to the storage-only representation. Snapshot
  reads take the coordinated slow path only while reservation state is active,
  preserving publication visibility and the existing `Bytes.each`,
  Actor-transfer and nested-P snapshot behavior. Successful commit, cancellation
  and failure continue to use the existing Future commitment boundary; parent
  reserved-index access, structural-mutation rejection, readable `size`, P
  ownership and all public errors/protocols are unchanged.
  `ProtosBytesReservationPayAsYouGrowTest` freezes lazy reservation lifetime,
  zero-length behavior, disjoint/overlap handling, commit publication and the
  absence of monitor modifiers on the ordinary fast path, while existing
  indexed-interop, P execution/context routing, Actor-transfer and PERF006/
  PERF026 `Bytes.each` regressions remain green. This is an internal
  representation/synchronization optimization only: no observable Protos
  semantic or specification change, and no performance magnitude is claimed.

## 0.3.161-SNAPSHOT

- `PERF025` replaces whole-collection keyed search in `Map` and `IdentityMap`
  with physical exact-hash indexing while preserving the existing logical
  insertion-order sequence and all observable collection semantics. Each live
  association remains present exactly once in insertion order and is also
  registered in one bucket keyed by its exact recorded `BigInteger` hash;
  bucket candidate order is insertion order, replacement changes only the
  value, removal updates both authorities and drops an empty bucket, and
  reinsertion appends normally. Ordinary `Map` lookup now computes the query
  hash once and examines only entries whose recorded hash is exactly equal
  before applying the existing directed `queryKey == storedKey` comparison.
  Structured Map read lookup, initial definition, `atPut` and `remove` select
  that same exact bucket after the guest hash result is accepted instead of
  copying `keyedSnapshot()` or scanning unrelated recorded hashes. Absent
  `atPut` continues to store the already-computed query hash, retaining the
  existing single-hash I035 behavior and representative key, recorded hash and
  insertion position on replacement. `IdentityMap` analogously indexes by
  exact recorded identity hash and still resolves collisions exclusively with
  `ProtosIdentity.identical`; it does not introduce Java reference identity or
  `IdentityHashMap`, and absent `atPut` reuses its single computed identity
  hash. The existing Map comparison-scope, suspension/cancellation/error and
  open/closed/frozen machinery is unchanged. Logical snapshots used by
  `Map.each`, `IdentityMap.each`, `Map.match`, transfer/copy/render paths and
  Array/Bytes are deliberately unchanged. New
  `ProtosPerf025MapPhysicalIndexTest` freezes bucket ordering,
  remove/reinsert behavior, semantic IdentityMap identity and the structural
  absence of productive whole-Map search snapshots; the existing PLAT028,
  I049, F-prime construction, Map.match and PERF026-D2 regressions cover
  callback direction, exact recorded-hash filtering, representative-key
  retention, single-hash behavior, suspension/unwind, state boundaries and
  snapshot stability. This is a physical representation/indexing
  optimization only: no observable Protos semantic or specification change,
  and no performance magnitude is claimed.

## 0.3.160-SNAPSHOT

- `PERF025` makes ordinary object/member and frame-backed lexical storage pay
  only for the generality actually used. Empty ordinary objects and freshly
  rebound Closures now share a storage-free lexical-binding authority and
  promote to map-backed storage only on first local-slot creation; the
  map-backed authority allocates its `LinkedHashMap` lazily and uses that one
  ordered map as both value store and establishment-order authority, releasing
  the backing map again when emptied. Execution Context materialization can now
  attach its definitive deferred/frame lexical authority directly instead of
  constructing and then replacing a provisional map-backed authority.
  Frame-backed lexical state likewise keeps `dynamicOverflow` and explicit
  `establishmentOrder` absent until dynamic bindings or non-monotonic
  establishment history require them; Closure frame layouts place parameters
  before body declarations so the normal parameter-then-local path remains in
  compact ordinal order without changing lexical identity or PRESENT/ABSENT
  semantics. `bindMethod` skips local-slot copying when the stored Closure has
  no local bindings while still producing one fresh receiver-bound Closure
  identity per member extraction. Ordinary `ReadMember` in both generated
  Bytecode interpreters now has a three-entry exact-receiver/constant-selector
  PIC backed by the existing selector-specific D013 lookup `Assumption`;
  successful hits cache only `ProtosSlotLookupResult`, never the extracted
  value, so Closure reads still rebind freshly on every access, selector
  mutation invalidates exactly as before, unsupported representations and
  megamorphic sites retain the unchanged generic fallback, and scalar member
  materialization no longer crosses an unnecessary `TruffleBoundary`.
  `ProtosPerf025ObjectModelPayAsYouGrowTest`, expanded frame-materialization
  coverage and expanded lazy-capture/member-read coverage freeze storage
  promotion, remove/recreate order, compact-to-general frame transitions,
  guarded invalidation, fresh Closure extraction and bounded-PIC fallback.
  Existing lexical-layout, H1 indexed-parameter, guarded-lookup, debugger,
  capture, Context and D179 regressions remain green. This slice deliberately
  does not introduce Graal `DynamicObject`/`Shape` or any observable object
  model change, and makes no performance-magnitude claim.

## 0.3.159-SNAPSHOT

- `PERF025` makes PLAT044 B′ immediate literal standard-control callbacks pay
  for a rich Closure invocation only when it is actually required. Boolean,
  `whileTrue` and local `each` callback preparation now performs authoritative
  ordinary D013 `call` selection once and retains a lean
  `PreparedInlineLiteralCall` carrying the selected source target plus compact
  invocation arguments. Inline admission is decided from that carrier before
  any rich `ProtosActivation`, guest Context/argument Array or physical
  `PreparedClosureCall` is materialized; an admitted callback materializes its
  one fresh semantic Closure activation only when entering the parser-inlined
  resumable region, while a missed admission constructs the exact ordinary
  prepared call lazily from the already-selected target without repeating
  lookup. Canonical `call` overrides, alias-home selection and invalidation,
  captured lexical state and ReturnHome provenance, Task/dynamic control,
  Error/non-local-return/suspension behavior, debugger scope projection and
  ordinary structured/physical fallback remain unchanged. The callback body
  remains parser-inlined in its caller semantic Bytecode root under PLAT044;
  callback-specific lexical/frame-native lowering is deliberately unchanged
  for the later slice. New
  `ProtosPerf025InlineCallbackPreparationTest` freezes the preparation boundary
  for Boolean, `whileTrue` and `Array.each`, while the existing PERF026 B1/B2/B3,
  C1 and D1/D2/D3 regressions continue to cover observable semantics and
  tooling. No performance magnitude is claimed.

## 0.3.158-SNAPSHOT

- `PERF027` removes the mandatory `BigInteger` physical representation from
  ordinary small semantic Integers while preserving the single exact unbounded
  `Integer` family. `ProtosIntegerValue` now canonicalizes values that fit the
  full signed `long` range to an internal machine-word representation and keeps
  arbitrary-precision `BigInteger` storage only when the mathematical value is
  outside that range. Canonical `+`, binary `-`, `*`, `div` and `mod` operations
  execute directly on two small operands and transparently promote on overflow;
  arbitrary-precision results canonicalize back to the small representation
  whenever they again fit in signed `long`. The `Long.MIN_VALUE / -1` boundary
  promotes exactly instead of overflowing. Integer/Integer equality, ordering
  and semantic identity compare the canonical internal representation without
  requiring `BigInteger` materialization on the small/small path. Integral
  interop width checks/projections likewise use the machine-word representation
  directly where possible. Exact Integer `/` to binary64 deliberately retains
  the existing arbitrary-precision rounding authority, and Integer/Float
  cross-family semantics, hashes, lookup/delegation, recognition, guarded
  selection/fallback, Actor transfer, debugger/tooling behavior and Native
  Image architecture remain unchanged. No SmallInteger/BigInteger family or
  primitive `long` Bytecode carrier becomes guest-visible. New
  `ProtosIntegerValueRepresentationTest` covers signed-long canonicalization,
  overflow promotion, down-normalization, signed quotient/remainder behavior
  and representation-independent identity/comparison; existing arithmetic,
  interop, numeric equality/ordering, guarded-Integer and Actor-transfer tests
  cover the affected integration surface. This is a structural representation
  optimization only: no observable Protos semantic or specification change and
  no performance magnitude is claimed.

## 0.3.157-SNAPSHOT

- `PERF025` removes the residual full native-Closure invocation scaffold from
  successful guarded canonical local Integer operations. PERF016/D013 guarded
  lookup remains the semantic selection authority; after that selection, the
  runtime additionally proves the exact standard implementation by the
  installed Closure identity, its private Integer-operation provenance, the
  exact `Integer` prototype `methodHome`, selector and existing lookup
  `Assumption`. Proven `+`, binary `-`, `*`, `/`, `div` and `mod` operations
  with valid Integer operands execute through the same canonical arithmetic
  authority used by their native Closure bodies and return through a minimal
  `ImmediateResultCall`, without constructing a rich method `ProtosActivation`,
  `ProtosReturnHome`, guest argument Array or `PreparedClosureCall.NativeCall`,
  and without calling `nativeBody.execute`. Wrong-domain arguments,
  division/remainder by zero, inherited Number operations such as ordering,
  non-canonical selections and all unsupported cases retain the exact
  PERF027-A deferred native path, preserving selected Closure/methodHome,
  Error/control state and generic fallback. `ProtosIntegerValue(BigInteger)`
  representation and exact arithmetic remain unchanged. The Integer protocol
  native-provider boundary contracts from four lexical `nativeClosure`
  construction sites to two because the six arithmetic selectors now share one
  audited installer/body implementation; the native semantic surface itself is
  not expanded. `ProtosStandardIntegerArithmeticTest`,
  `ProtosPerf027AGuardedIntegerDeferredActivationTest`,
  `ProtosGuardedLookupTest` and
  `ProtosCoreNativeBoundaryArchitectureTest` cover canonical provenance,
  exact large-Integer results, immediate successful execution, deferred Error
  and inherited-operation fallback, Context isolation and the audited native
  boundary. No observable Protos semantic or specification change; no
  performance magnitude is claimed.

## 0.3.156-SNAPSHOT

- `PERF025-H1` establishes statically proven Closure parameters of a root that
  installs its persistent frame authority at their known layout ordinal. The
  lowerer emits the new `BindClosureIndexedParameter`/`BindClosureIndexedRest`
  operations, carrying the root's `ProtosFrameLexicalLayout` and the
  parameter's ordinal, for every parameter whose binding identity is owned by
  that root's own scope; `ProtosFrameLexicalBindingAuthority.createFrameBackedBindingAt`
  then checks presence and writes the frame local directly at that ordinal,
  recording establishment order exactly as before, instead of re-resolving the
  name through `containsBinding`/`putBinding`. The indexed path applies only
  while the activation's current authority stores that exact layout and the
  execution context admits creation
  (`ProtosActivation.currentAuthorityAdmittingLocalCreationForRuntime`: an
  unmaterialized, or an OPEN materialized, execution context still using that
  authority); a CLOSED or FROZEN context, another authority, or an unproven
  parameter takes the unchanged named `BindClosureParameter`/`BindClosureRest`
  path. Static identity never implies presence: a PRESENT binding, including
  PRESENT(null), is still a duplicate-creation Error, and a binding removed
  before its formal bind (D179 C0) is re-established at the same ordinal.
  Defaults, rest Array creation, argument transport and the frame-native
  `BindClosureFrameParameter` path are unchanged; no observable semantic
  change. New `ProtosPerf025H1IndexedParameterEstablishmentTest`.

## 0.3.155-SNAPSHOT

- `PERF025` virtualizes statically unobservable Closure return homes. A new
  `CanonicalReturnHomeAnalysis` proves, once per source Closure execution plan
  (never per invocation), whether the Closure or any lexical descendant -
  parameter defaults, nested Closures at any depth, and inline Object bodies -
  contains `^`. An owning invocation of a source-backed Closure whose plan
  proves its Smalltalk-style return home unobservable runs under the shared
  non-materialized `ProtosReturnHome.unobservable()` marker instead of a fresh
  `ProtosReturnHome`, and skips the home's `isActive`/`complete` lifecycle and
  non-local-return matching. All owning sites (compact frame ABI, rich
  activation factories, deferred immediate-method preparation) select the home
  through `ProtosClosureValue.invocationReturnHomeForRuntime()`. Home
  ownership and captured-home provenance are unchanged, so PLAT044 inline
  literal-callback admission is preserved; native bodies and plans not yet
  prepared keep a fresh physical home. Direct, default, descendant, transitive
  and Object-body non-local return, escaped and foreign-Task `InvalidReturn`,
  `ensure` precedence and suspension lifetime of physical homes are preserved;
  no observable semantic change. New `ProtosPerf025VirtualReturnHomeTest`;
  return-home lifecycle assertions over proven-unobservable plans in
  `ProtosClosureInvokerTest`, `ProtosPerf006B2BClosureContinuationCompositionTest`,
  `ProtosPerf006B2D1OrdinarySendCompositionTest`,
  `ProtosPerf006B2D3BSelectedStandardObjectCallIntrinsicTest`,
  `ProtosPerf006B2D3AOrdinaryObjectCallProtocolTest`,
  `ProtosPerf010APreparedTargetSpecializationTest`,
  `ProtosPerf025DirectSourceClosureCompactInvocationTest` and
  `ProtosPerf025H1LazyRootActivationTest` follow the non-materialized home.

## 0.3.154-SNAPSHOT

- `PERF025` completes compact callee execution on top of `PERF025-H1`: a
  compact source root keeps its compact invocation state authoritative through
  ordinary body-level local creation and assignment, not only through
  parameter binding and reads. `CreateCurrentFrameLocal` establishes an ABSENT
  frame-native local directly while the frame is still compact, and new
  root-level `ResolveRootFrameLocalWriteTarget`/`AssignRootFrameLocal`
  operations select and write a PRESENT statically `Resolved` current local
  through its constant `LocalAccessor` without materializing the activation;
  duplicate creation, ABSENT (D179 C0) targets, FROZEN contexts and every
  other destination take the unchanged activation path and Errors. When a
  compact invocation is materialized, the activation adopts a read-only view
  of the frame-argument supplied range instead of copying it with
  `List.copyOf`, and `ProtosFrameArguments.compactTask` reports the published
  activation's exact Task after materialization. BUG013 (no retained
  `VirtualFrame`), D179 presence, parameter/default/rest order, Context
  identity, Error-handler selection, ReturnHome and Task/control provenance
  are preserved. New `ProtosPerf025CompactCalleeExecutionTest`;
  `ProtosPerf025FrameMaterializationSliceTest` and
  `ProtosPerf028AResolvedCurrentLexicalWriteTest` accept the root-level write
  operations. No observable Protos semantics change; the specification is
  unchanged.

## 0.3.153-SNAPSHOT

- `PERF025-H1` (#758) removes the unconditional `ProtosActivation`
  materialization from compact semantic source roots. The root prologue
  (`PublishFrameActivation`) is gone; the current activation is loaded through
  a lazy `CurrentActivation` operation that materializes the exact activation
  from the compact frame ABI only when an operation, an Error path, root
  exception interception or tooling needs it, at most once, published into
  frame argument 0. At root level, supplied-argument presence, loads and the
  arity upper bound (`HasFrameClosureArgument`, `LoadFrameClosureArgument`,
  `CheckFrameClosureArgumentUpperBound`), frame-native parameter binding
  (`BindClosureFrameParameter`) and PRESENT current-frame reads
  (`ReadRootFrameLocal`) operate directly from compact frame arguments and
  frame locals, so ordinary zero/simple-argument and default-argument calls
  run without a rich activation. The rich activation remains an exact lazy
  projection with unchanged Context, Task, receiver, method-home, capture and
  return-home provenance. ReturnHome representation, frame materialization and
  Closure-capture behavior are intentionally unchanged. New
  `ProtosPerf025H1LazyRootActivationTest`; affected PERF025/I068 tests updated
  to the new structural shape. No observable Protos semantics change; the
  specification is unchanged.

## 0.3.152-SNAPSHOT

- `PERF025` makes Closure-literal lexical capture lazy. A materialized
  Closure no longer captures an eagerly built `List` of guest execution
  Contexts: `MaterializeClosure` now captures the creating activation's
  `ProtosLexicalEnvironment` through
  `ProtosActivation.lexicalEnvironmentForClosureCapture()`, without calling
  `context()` or copying the outer chain. Each activation creates at most one
  environment node, shared by reference by every Closure it creates and by
  the activations derived from them; a deferred node answers membership,
  reads and frame-backed authority through the owning activation's single
  lexical store (PLAT036/I068, PERF013 `MaterializedLocalAccessor` paths
  unchanged) and materializes the guest Context only through the activation's
  own `context()`, so every observer (the `context` intrinsic, reflection,
  escape, the debugger, generic writes) sees one identity. Captured
  read/write operations and `ProtosLexicalFallback` walk the chain without
  materializing; D179 C0 nearer-presence retargeting, object-body capture
  boundary, `bindMethod`, parallel projection, receiver/`methodHome`/return
  home and NLR are unchanged. List-based `capturedLexicalContexts()` /
  `lexicalContextsForClosureCapture()` remain as cold materializing
  projections. New `ProtosPerf025LazyLexicalCaptureTest`. No semantic or
  specification change.

## 0.3.151-SNAPSHOT

- `PERF025` frame-materialization slice: ordinary ROOT/CLOSURE frames are no
  longer materialized merely because they declare a local or parameter.
  `CanonicalToBytecodeLowerer` now emits `InstallFrameLexicalAuthority` only
  when a conservative lowering predicate (`requiresPersistentFrameAuthority`)
  cannot prove that a Closure root's bindings stay confined to its live frame:
  top-level/module roots, nested Closures (including inline literal callbacks
  and Closure defaults), Object construction, the `context` intrinsic,
  `compose`, any non-`Resolved` (`Candidate`/`Dynamic`) access to a name the
  root declares, and any unrecognized form keep the persistent authority.
  In admitted roots, statically proven current bindings (supplied, defaulted
  and rest parameters, target-less `:` and D143 multiple creation) are
  established directly in their frame locals by the new
  `BindClosureFrameParameter`, `BindClosureFrameRest`,
  `CreateCurrentFrameLocal` and `MultipleCreateFrameLocals` operations, with
  the exact duplicate-creation error and presence-by-not-cleared rule; reads
  and PERF028-A writes keep their existing `LocalAccessor` fast paths. If an
  admitted root's activation is already observed (materialized Context or an
  installed authority), its first establishment installs, in-frame and while
  the frame is live, the same materialized-frame authority, adopting the
  bindings already present. Tooling scope projection of an unobserved
  admitted root installs that same authority from the live queried frame
  before projecting, so `ProtosDebuggerScope` is unchanged.
  Persistent/materialized frame authority is retained for every case that
  requires general or escaping context semantics; BUG013 lifetime safety is
  intact (no raw `VirtualFrame` is ever retained). No observable Protos
  semantic change and no specification change. New
  `ProtosPerf025FrameMaterializationSliceTest`.

## 0.3.150-SNAPSHOT

- `PERF025` completes the PLAT040 F′ convergence for direct source-backed
  Closure invocation. Stable direct Closure-call hits (`finishDirectClosureCall`,
  under the unchanged PERF014 selection and `DirectCallNode` target) and
  Task-owned direct source Closure entry (`prepareTaskOwnedDirectClosureIfBytecode`,
  the path taken by `PreparedTopLevel.invoke()`) no longer build a pre-target
  `ProtosActivation` through `forClosureInvocation`. They enter the source
  target through a new compact direct-Closure frame ABI kind in
  `ProtosFrameArguments` (closure, caller provenance, explicit Task or null,
  invocation return home, supplied values), and the root prologue materializes
  the exact activation through the new
  `ProtosActivation.forDirectClosureInvocationWithReturnHomeForRuntime` with
  the guest execution Context and supplied guest Array deferred. Captured
  receiver, `methodHome`, lexical contexts, return-home ownership, Task
  attachment and dynamic-control inheritance are unchanged. Native,
  structured-control, generic-fallback and synchronous host Closure paths keep
  their existing shape. ReturnHome allocation, BUG013 frame materialization
  and `PublishFrameActivation` are unchanged. New
  `ProtosPerf025DirectSourceClosureCompactInvocationTest` covers the compact
  shape, deferred state, rest parameters, Task-owned entry, distinct
  invocations, capture by reference, `this`, non-local return and InvalidReturn.
  No observable Protos semantics change; the specification is unchanged.

## 0.3.149-SNAPSHOT

- `PERF028-A` (#780) specializes bare lexical assignments statically
  `Resolved` in the genuine scope that owns the executing frame. They now
  lower to `ResolveCurrentFrameLocalWriteTarget`/`AssignCurrentFrameLocal`,
  which identify the binding through a constant `LocalAccessor` instead of
  the String-keyed `ResolveWritableLexicalTarget`/`AssignResolvedLexicalTarget`
  pair, in both body and parameter-default lowering. A PRESENT binding is
  selected without name search or a fresh destination object; a binding
  cleared under D179 C0 runs the unchanged generic selection before RHS
  evaluation, and the selected destination is written without re-resolution
  (FROZEN rejected, CLOSED writable, RHS removal is a mutation Error). The
  FROZEN check uses a new non-materializing
  `ProtosActivation.currentContextIsFrozenForRuntime()`. Candidate, Dynamic,
  explicit member and captured writes keep their existing paths. New
  `ProtosPerf028AResolvedCurrentLexicalWriteTest` covers the structural path,
  fallback selection, no retargeting, remove/recreate, CLOSED/FROZEN,
  `PRESENT(null)`, control transfer, suspension/resume with the
  uncached-to-cached transition, and default assignments. No observable
  Protos semantics change; the specification is unchanged.

## 0.3.148-SNAPSHOT

- `PERF027-A` (#779) isolates the Integer invocation tax. The PERF016 guarded
  Integer native send keeps its exact cached selection (native Closure,
  method home, stability) but now prepares the selected method invocation
  through the existing PLAT040 deferred activation
  (`forImmediateMethodInvocationWithReturnHomeForRuntime`) instead of the
  eager immediate-method factory. The fresh invocation return home is still
  established before entry; the guest execution Context and supplied guest
  Array materialize only when observed (for example by an Error path,
  reflection or a debugger). Every other immediate-method caller keeps the
  eager path. Integer representation (`ProtosIntegerValue(BigInteger)`) and
  arithmetic are unchanged. New
  `ProtosPerf027AGuardedIntegerDeferredActivationTest` covers selection and
  home identity, deferred state after success, return-home completion,
  on-demand materialization, exact large and overflow-sensitive results,
  comparison results, div/mod zero and wrong-domain Errors, and separate
  Contexts. No observable Protos semantics change; the specification is
  unchanged.

## 0.3.147-SNAPSHOT

- `PERF025-G1` (#758) makes Task structured-child bookkeeping pay-only-when-used.
  `ProtosTask.children` is now materialized on the first `addChild`; a never
  materialized set behaves as the empty set, and child snapshots of an empty
  set return `Set.of()` without allocation. The per-Task anonymous child-drain
  `WaitDependency` is replaced by one shared stateless identity sentinel,
  compared only against each Task's own wait/resume dependency. New
  `ProtosPerf025G1LazyTaskChildrenTest` covers childless completion without
  materialization, first-child ownership and removal, completion, failure and
  cancellation child drain. No observable Protos semantics change; the
  specification is unchanged.

## 0.3.146-SNAPSHOT

- `PERF025-F1` (#758) implements PLAT046 Candidate B: direct-caller hosted
  session execution. `ProtosStandaloneHostedSession` (and therefore
  `ProtosStandaloneHostedExecution.executeFile`) now runs open,
  `invokeTopLevel`, `prepareTopLevel`, `PreparedTopLevel.invoke` and close on
  the calling thread, still through the Process execution host, Process
  Polyglot Context entry and a fresh RootTask. A session-local
  `ReentrantLock` gate admits one guest operation at a time; close publishes
  its cutover before taking the gate, so an already-started operation
  finishes and every later operation fails with `IllegalStateException`,
  then performs the unchanged teardown sequence. `ProtosGuestCarrier` and
  `ProtosGuestCarrierTest` are retired; the shared
  `GUEST_CALL_STACK_SIZE_BYTES` budget remains for the CLI and Test Tool
  carriers. New `ProtosStandaloneHostedSessionGateTest` covers caller-thread
  execution, multi-caller serialization and call-versus-close exclusion. No
  observable Protos semantics change; the specification is unchanged.

## 0.3.145-SNAPSHOT

- `PERF025-E2` (#758) cleans up the guest-carrier transport.
  `ProtosGuestCarrier.call()` now queues one private `CarrierCall` request
  (operation, waiting thread, result, failure, volatile completion) directly
  as the carrier `Runnable` and completes it with `LockSupport.park`/`unpark`,
  replacing the per-call one-element state arrays, the wrapping lambda and the
  per-call monitor `wait()`/`notifyAll()`. The dedicated carrier thread, its
  16 MiB stack budget, the `LinkedBlockingQueue`, carrier serialization, STOP
  ordering, close rejection, failure propagation and caller interrupt-status
  restoration are unchanged. New `ProtosGuestCarrierTest` covers the transport.
  No observable Protos semantics change; the specification is unchanged.

## 0.3.144-SNAPSHOT

- `BUG014` restores the ordinary shaded checkout JAR under the `native`
  Maven profile. The profile no longer sets `protos.shade.skip=true`;
  instead `native-maven-plugin` receives the compiled application classes
  directly through
  `<classesDirectory>${project.build.outputDirectory}</classesDirectory>`
  plus the resolved dependency graph, so Native Image still never consumes
  an uber-JAR that duplicates those dependencies. The PLAT045 interpreter-only
  fallback runtime selection is unchanged.
  `ProtosPerf006C1OptimizingRuntimeClosureTest` now asserts the
  classes-directory input and the absence of the shade skip. No observable
  Protos semantics change; the specification is unchanged.

## 0.3.143-SNAPSHOT

- `PERF025-C2D` (#758) reduces the shared explicit JVM guest-carrier stack
  budget (`ProtosStandaloneHostedExecution.GUEST_CALL_STACK_SIZE_BYTES`)
  from 64 MiB to 16 MiB after the post-B′ PERF025-C2C evidence gate showed
  the artificial per-level root multipliers removed. The dedicated guest
  carrier, Test Tool per-Case carrier use, serialization/placement
  behavior, the unchanged 10,000-deep
  `deep-recursive-closure-call-stack-capacity` regression workload, and all
  observable Protos semantics are retained; the specification is unchanged.
  16 MiB is a conservative fixed budget recorded as part of the reference
  runtime's run identity, not a claimed minimum. The benchmark README now
  records the 16 MiB carrier stack.

## 0.3.142-SNAPSHOT

- `PERF026-D3` (#769) adds the standard `Environment.each` consumer of the
  PLAT044 (#766) Candidate B′ inline literal callback mechanism, without
  changing observable Protos semantics or the specification. It reuses the
  PERF026-D2 two-parameter literal candidate (exactly two ordinary required
  positional parameters, no default, not rest, no nested Closure) unchanged;
  only runtime capability distinguishes the families. After ordinary lookup
  and selection prepare the canonical standard `Environment.each` with
  exactly that staged literal, and it neither owns its return home nor
  requires a Context-local projection, the loop is sequenced in the semantic
  source root instead of the structured-dispatch helper root. Admission
  prepares and validates nothing and does not examine callback arity:
  receiver validation, callback callability validation before portable
  validation, the complete portable (String, String) conversion and
  validation with canonical name ordering before callback #1, the fresh
  per-entry activation carrying the exact portable name and value Strings,
  advance only after normal callback completion, the ignored callback
  result and the original receiver result all stay owned by the existing
  prepared Environment each call, which joins the common local each view
  directly rather than as an association each. Each entry child that passes
  the shared B′ exact-invocation proof runs inline with its own RootTag and
  projected debugger scope, binding `name` and `value` through the ordinary
  Closure parameter binding from that prepared activation; a child that
  fails it keeps its exact physical invocation. The two-parameter send-site
  admission and preparation operations now cover Map, IdentityMap and
  Environment, so the inline callback region is not duplicated. Because the
  generic preparation path classifies the structured Environment.each
  capability by implementation identity alone, the Environment admission
  additionally re-proves the canonical home from the call's own receiver and
  method home, so the standard `each` copied to another home keeps its
  ordinary path. Every other
  callback shape and every other `each` keep the existing paths. Focused
  topology, tooling, fallback and semantic tests are added; no performance
  claim is made.

## 0.3.141-SNAPSHOT

- `PERF026-D2` (#769) adds the standard `Map.each` and `IdentityMap.each`
  consumers of the PLAT044 (#766) Candidate B′ inline literal callback
  mechanism, without changing observable Protos semantics or the
  specification. B′ is extended only to an immediate Closure literal with
  exactly two ordinary required positional parameters (no default, not rest)
  and no nested Closure, staged as the sole argument by a candidate check kept
  separate from the one-parameter indexed each candidate and the
  zero-parameter Boolean/while candidates. After ordinary lookup and selection
  prepare the canonical standard `Map.each` or `IdentityMap.each` with exactly
  that staged literal, and it neither owns its return home nor requires a
  Context-local projection, the loop is sequenced in the semantic source root
  instead of the structured-dispatch helper root. Admission prepares nothing
  and does not examine callback arity: receiver validation, callback
  callability validation, the single shallow insertion-ordered association
  snapshot, the fresh per-association activation carrying the exact
  representative key and value, advance only after normal callback
  completion, the ignored callback result and the original receiver result
  all stay owned by the existing prepared Map/IdentityMap each calls; no
  hash, equality or identity re-search is added. Each association child that
  passes the shared B′ exact-invocation proof runs inline with its own
  RootTag and projected debugger scope, binding `key` and `value` through the
  ordinary Closure parameter binding from that prepared activation; a child
  that fails it keeps its exact physical invocation. The D1 local loop is
  generalized over a common prepared each view instead of being duplicated.
  Every other callback shape and every other `each` keep the existing paths.
  Focused topology, tooling, fallback and semantic tests are added; no
  performance claim is made.

## 0.3.140-SNAPSHOT

- `PERF026-D1` (#769) adds the standard `Array.each` and `Bytes.each`
  consumers of the PLAT044 (#766) Candidate B′ inline literal callback
  mechanism, without changing observable Protos semantics or the
  specification. B′ is extended only to the smallest parameterized shape: an
  immediate Closure literal with exactly one ordinary required positional
  parameter (no default, not rest) and no nested Closure, staged as the sole
  argument by a candidate check kept separate from the zero-parameter
  Boolean/while candidates. After ordinary lookup and selection prepare the
  canonical standard `Array.each` or `Bytes.each` with exactly that staged
  literal, and it neither owns its return home nor requires a Context-local
  projection, the loop is sequenced in the semantic source root instead of the
  structured-dispatch helper root. Admission prepares nothing: receiver
  validation, callback callability validation, the single shallow ascending
  indexed snapshot, the fresh per-element activation carrying the exact
  snapshot element or semantic Integer octet, advance only after normal
  callback completion, the ignored callback result and the original receiver
  result all stay owned by the existing prepared Array/Bytes each calls. Each
  element child that passes the shared B′ exact-invocation proof runs inline
  with its own RootTag and projected debugger scope, binding its formal
  through the ordinary Closure parameter binding from that prepared
  activation; a child that fails it keeps its exact physical invocation. Every
  other callback shape and every other `each` keep the existing paths.
  Focused topology, tooling, fallback and semantic tests are added; no
  performance claim is made.

## 0.3.139-SNAPSHOT

- `PERF026-C1` (#768) adds the first standard `whileTrue` consumer of the
  PLAT044 (#766) Candidate B′ inline literal callback mechanism, without
  changing observable Protos semantics or the specification. When the
  condition receiver and the sole body argument are both immediate
  zero-parameter Closure literals without nested Closures, both are staged
  in their ordinary evaluation order. After ordinary lookup and selection
  prepare the canonical standard `whileTrue` with exactly those two values,
  and neither literal owns its return home or requires a Context-local
  projection, the loop is sequenced in the semantic source root instead of
  the structured-dispatch helper root. Admission prepares no activation, so
  receiver/body validation, the fresh per-iteration condition activation,
  the body activation prepared only after a canonical `true`, the strict
  Boolean condition authority, the ignored body result and the canonical
  `null` result are all unchanged. Each fresh child that passes the shared
  B′ exact-invocation proof runs inline with its own RootTag and projected
  debugger scope; a child that fails it keeps its exact physical invocation.
  Every other while keeps the existing structured-dispatch path. Focused
  topology, tooling and semantic tests are added; no performance claim is
  made.

## 0.3.138-SNAPSHOT

- `PERF026-B3` (#767) completes the standard Boolean PLAT044 (#766) Candidate
  B′ family by adding `IF_TRUE_IF_FALSE` two-candidate selected-branch
  handling, without changing observable Protos semantics or the specification.
  Both callback arguments are still evaluated eagerly, exactly once and in
  source order; each eligible immediate literal position is staged as a
  positional candidate. After ordinary lookup, `PreparedBooleanCall`
  selection and selected-callback preparation, the selected callback runs
  inline only when its position and exact value match a staged literal and
  every existing B1 shape check holds. Selection and admission share one
  selected-position authority, so the unselected callback is never entered
  and any other shape keeps the exact physical callback invocation. The
  selected result completes through the existing Boolean callback finish. The
  guest carrier and its 64 MiB stack size are unchanged. The superseded
  B1/B2 `ifTrueIfFalse` physical-boundary tests are replaced by focused B3
  topology, tooling and semantic tests; no performance claim is made.

## 0.3.137-SNAPSHOT

- `BUG013-F` implements the ratified PLAT045 (#772) Candidate B contract
  ("Native Image supported, guest JIT unavailable"). The `native` Maven
  profile now builds with the supported fallback Truffle runtime
  (`-Dtruffle.UseFallbackRuntime=true`): the executable remains a real
  GraalVM Native Image AOT host, but guest execution is interpreter-only and
  Native guest JIT is unavailable while upstream `oracle/graal#14579` blocks
  Bytecode DSL Tier-2 compilation in Native Image. The JVM path, Surefire and
  `bin/protos` keep the optimizing runtime and guest JIT unchanged; observable
  Protos semantics and the specification do not change. The maintained Native
  regression (`build/native/test-native.sh`) no longer claims forced Tier-2
  success: it keeps the CLI, guest, Test Tool and DAP gates and instead
  validates interpreter-only execution, failing closed on a missing fallback
  runtime, any guest compilation, opt failure, `FrameWithoutBoxing` or
  compilation failure. The interpreter-only warning is not suppressed.
  Restoring Native guest JIT remains gated on an upstream fix and Native
  Tier-2 revalidation. No release is implied.

## 0.3.136-SNAPSHOT

- `PERF026-B2` (#767) extends the PLAT044 (#766) Candidate B′ single-literal
  inline callback path published by `PERF026-B1` from standard `IF_TRUE` to
  standard `IF_FALSE`, `AND` and `OR`, without changing observable Protos
  semantics or the specification. Eligibility is still decided after ordinary
  lookup and the existing `PreparedBooleanCall` classification, only when the
  one supplied callback is actually selected and every existing B1 shape check
  holds; unselected callbacks are never entered. The inline callback result
  still completes through the standard Boolean callback finish, so `and`/`or`
  keep the exact Boolean-result validation and Error. `IF_TRUE_IF_FALSE`
  keeps its physical callback path (PERF026-B3), as do all B1 fallbacks. The
  guest carrier and its 64 MiB stack size are unchanged. Focused topology,
  tooling and semantic tests are added; no performance claim is made.

## 0.3.135-SNAPSHOT

- `PERF026-B1` (#767) implements the first PLAT044 (#766) Candidate B′ slice,
  without changing observable Protos semantics or the specification. After
  ordinary lookup and the existing `PreparedBooleanCall` classification, an
  eligible standard `IF_TRUE` immediate literal callback (a zero-parameter
  Closure literal with no nested Closure, whose prepared invocation is its own
  activation root with a rich activation and a captured return home) no longer
  enters a distinct callback RootCallTarget: its body runs inline in the
  containing semantic source root. The literal is still evaluated exactly once
  as an ordinary argument, and the fresh semantic callback activation is
  preserved and selected as the current activation for the whole inline
  region, so `context`, lexical capture by reference, non-local return, Error
  propagation and suspension behave exactly as before. The inline region
  carries a custom RootTag, and debugger scopes inside it project the callback
  activation; as approved by PLAT044, the callback no longer appears as a
  separate debugger or Truffle stack frame. Dynamic callbacks, non-Closure
  invokables, custom same-name selectors, top-level literals owning their
  return home, every other non-eligible shape, and `IF_FALSE`,
  `IF_TRUE_IF_FALSE`, `AND` and `OR` keep the exact physical callback path.
  The guest carrier and its 64 MiB stack size are unchanged. Focused topology,
  tooling and semantic tests and Boolean conformance cases are added.

## 0.3.134-SNAPSHOT

- `PERF025-C2B` implements the PLAT043 (#763) standard Boolean ownership
  exception to PLAT042, without changing observable Protos semantics or the
  specification. A prepared standard Boolean call (`ifTrue`, `ifFalse`,
  `ifTrueIfFalse`, `and`, `or`), classified from the already-selected
  behavior after ordinary lookup, is now sequenced inside the tagged semantic
  source root instead of entering the untagged structured/C-prime root, so a
  reached Boolean callback no longer adds a helper CallTarget per level. The
  semantic root gains operations that delegate to the existing
  `PreparedBooleanCall` state machine and prepared-call completion; the outer
  call is completed exactly once through a Bytecode `TryFinally`. Every other
  structured family (`while`, `each`, `ensure`, `Error.handle`, matching,
  Map/collection control, import, I/O C-prime) still enters the untagged
  helper root. The guest carrier and its fixed stack size are unchanged.
  Topology evidence and a Boolean callback dispatch/control conformance test
  are added.

## 0.3.133-SNAPSHOT

- `PERF025-C` (#758) completes the PLAT042 Candidate B′ semantic/structured
  interpreter cutover (C1c), without changing observable Protos semantics or
  the specification. `ProtosSemanticBytecodeRootNode` is now the real tagged
  semantic source interpreter (automatic RootTag, no RootBodyTag, same tier,
  yield, materialized-local, tail-call and boxing configuration as the
  untagged interpreter): `CanonicalToBytecodeLowerer` lowers top-level,
  module and Closure roots directly into it, so the universal semantic
  wrapper root is removed and an ordinary source Closure call enters exactly
  one CallTarget. Its source-surface operations delegate to the single
  operation implementations in `ProtosBytecodeRootNode`, and root
  Error-handler selection is shared by both interpreters. Each semantic root
  first publishes a compact guarded-send activation into frame argument 0,
  preserving one activation identity per call. `ProtosBytecodeRootNode` is
  now the untagged structured-dispatch/C-prime interpreter; the structured
  dispatcher moved unchanged into `ProtosStructuredDispatchLowerer`, and a
  structured prepared invocation enters it once per invocation through a
  direct call to the Context's stable Task C-prime entry target (indirect
  fallback), composing its continuation without replay. Lexically nested
  Closure roots share the semantic interpreter's `BytecodeRootNodes`
  generation, preserving the PERF013 materialized-local fast path. Native
  generated-structure policy and the native forced-JIT workload cover both
  interpreters. A bounded JVM comparison against `595d547b` measured faster
  ordinary (-46%), structured-boundary (-36%) and retained closure-call
  (-43%) workloads. The BUG008 64 MiB dedicated guest carrier is unchanged:
  stack evidence shows the retained 10,000-deep recursive drivers, which use
  a structured `ifTrue` at every level, still need a large fixed stack (32
  MiB in the interpreter), so carrier retirement remains pending.

## 0.3.132-SNAPSHOT

- `PERF025-C1` (#758) partial implementation checkpoint (C1a+C1b), without
  changing observable Protos semantics or the specification.
  `CanonicalToBytecodeLowerer` now loads the current `ProtosActivation`
  through one compile-time lowering seam instead of emitting frame-argument
  loads directly. An Object construction body is now lowered inline as a
  resumable region of its enclosing Bytecode root, executing with its
  construction activation (still created by
  `ProtosActivation.forObjectConstruction`) held in Bytecode locals; the
  Object-body helper root and its call/continuation machinery
  (`ProtosObjectBodyTargetCell` and the Prepare/Enter/Resume/Finish Object
  construction operations) are removed. Object bodies remain non-lexical
  execution regions, and Closures declared inside them still share their
  lexical owner's `BytecodeRootNodes` generation, preserving the PERF013
  same-generation Closure/owner relationship. Updates the PERF013 grouping
  tests to the inline topology and adds Object-body suspension, nesting,
  capture, Error, ensure and non-local-return coverage.

## 0.3.131-SNAPSHOT

- `PERF025-B` (#758): add a prepared reusable top-level callable to the
  supported JVM embedding session without changing observable Protos
  semantics. `ProtosStandaloneHostedSession.prepareTopLevel(name)` resolves and
  validates the entry module's source-backed top-level Closure once on the
  guest carrier and returns a `PreparedTopLevel` handle whose `invoke()`
  repeatedly runs exactly that captured Closure in the same live Process,
  Polyglot Context and carrier, through the existing execution-host boundary
  and `ProtosRootTaskExecution.executeClosure(...)` (so it inherits the
  PERF025-A direct first dispatch), with no per-invocation slot lookup, source
  parsing, or Process/Context creation. `invokeTopLevel(name)` keeps its
  dynamic per-call slot read and therefore still observes guest reassignment;
  the prepared handle intentionally does not. Preparation of a missing or
  non-source-backed slot fails like `invokeTopLevel`, and a prepared handle is
  rejected once its session is closed. Adds external-consumer embedding tests
  for repeated prepared calls, shared module state, the reassignment
  distinction, preparation rejection, post-close rejection and `FAILED`
  outcome mapping.

## 0.3.130-SNAPSHOT

- `PERF025-A` (#758): direct-dispatch the first segment of a fresh RootTask
  without changing observable Protos semantics. `ProtosRootTaskExecution` now
  starts its root Task through the new
  `ProtosActorExecutionDomain.runFreshRootTaskDirectly(...)`, which registers
  the Task exactly as `createTask(...)` does and runs its first segment via
  `beginDirectDispatch()` instead of an initial runnable-queue
  enqueue/dequeue and scheduler wakeup. A Task created on an already
  TERMINATING Actor still records cancellation before the first segment, so
  it is cancelled at the first-execution boundary without running ordinary
  code. Suspension, resumption and re-runnable re-entry continue through the
  ordinary Actor queue, and terminal mapping is unchanged. Adds focused domain
  regressions for direct start, cancellation, suspend/resume, TERMINATING and
  TERMINATED Actors.

## 0.3.129-SNAPSHOT

- `PERF022` (#752): reduce pathological ordinary Java test-suite runtime
  without changing observable Protos semantics or weakening required
  correctness coverage. Benchmark-scale standalone-embedding inputs are
  replaced by proportional recursion/factorial cases while PERF021 remains
  the owner of benchmark-scale workloads; the reusable-session regression
  keeps repeated invocation coverage with a proportional repeat count.
  Test Tool source loading now crosses its 16-read batch boundary using
  deterministic short reads and a split UTF-8 scalar instead of a
  megabyte-scale payload. Redundant full public Test Tool file-selection and
  workspace-run executions are replaced by existing focal coverage or direct
  outcome/host-failure translation checks, including explicit cancelled
  workspace outcome coverage. Filesystem-library, Package execution-plan and
  workspace-preflight tests amortize immutable Core/RuntimeHost setup while
  retaining fresh activations, Processes, Filesystems and resources where
  isolation is part of the contract. Removes the duplicate
  `ProtosWorkspaceRunDriverTest`; its preflight, Tool/Application isolation,
  dependency execution, public driver path and metadata non-mutation
  invariants remain owned by their focused integration tests.

## 0.3.128-SNAPSHOT

- `I077` (#751, PERF021 #748): expose a reusable standalone JVM embedding
  session. The new `ProtosStandaloneHostedSession` opens one standalone source
  file with the I076 standalone direct-file sequence and keeps the semantic
  Process, its Process-scoped Polyglot Context (and therefore Engine and JIT
  state) and the runtime host alive, so that `invokeTopLevel(name)` can invoke
  an already-defined top-level Closure such as `run` repeatedly in that same
  Process without reparsing source text. All guest operations, including open
  and close, run on one long-lived dedicated carrier with the BUG008 stack
  budget. `close()` requests Process termination, awaits terminal Process
  completion and Context disposition, then closes the runtime host and module
  resolver, and is idempotent. `ProtosStandaloneHostedExecution.executeFile`
  is now implemented on the session and remains supported; it additionally
  awaits terminal Process completion before closing. `ProtosRootTaskExecution`
  gains `executeClosure` sharing its terminal-state mapping. No observable
  Protos semantics or CLI behavior change and no benchmark policy is added.
  Adds session embedding and lifecycle tests.

## 0.3.127-SNAPSHOT

- `I076`: expose reusable standalone hosted execution for JVM embedding. The
  new `ProtosStandaloneHostedExecution.executeFile(coreRoot, sourceFile, ...)`
  executes one standalone source file through the same Core bootstrap,
  standalone Process bootstrap, Process-scoped Polyglot Context binding and
  canonical initial-module execution as the CLI, on a dedicated guest carrier
  thread with the BUG008 stack budget, and returns the terminal
  `ProtosExecutionOutcome` (the terminal expression's guest value when
  completed). Every Process, Context and runtime-host resource it opens is
  released before it returns. The CLI now consumes the same shared bootstrap,
  binding, host Environment/stream provisioning and direct-file execution
  instead of private copies. Embedded execution installs no CLI conveniences
  such as `print`. No observable Protos semantics, raw `Context.eval`
  behavior or CLI behavior change. Adds embedding tests for recursive
  Fibonacci and factorial terminal values and updates the CLI Polyglot routing
  architecture test to follow the moved code.

## 0.3.126-SNAPSHOT

- `BUG013` (#749, DIST009 #743): repair the Truffle frame lifetime used by the
  frame-backed lexical authority. `InstallFrameLexicalAuthority` now
  materializes its bound `VirtualFrame` at the explicit escape boundary before
  constructing `ProtosFrameLexicalBindingAuthority`, and that authority retains
  only a final `MaterializedFrame`. Context observation is no longer the first
  materialization point, and the captured-materialized-local seam reuses the
  already escape-safe retained frame directly. The I075-D invariant remains
  intact: the authority retains the stable declaring `BytecodeRootNode` and
  resolves the current `BytecodeNode` for local access. The repair preserves
  boxing elimination, the uncached interpreter, yield/materialized-local
  support, and observable Protos semantics. A focused regression verifies that
  the deferred authority already retains a `MaterializedFrame` before
  `ProtosActivation.context()` makes the guest execution context observable.

## 0.3.125-SNAPSHOT

- `I075-D` (#746, PERF011 #693): enable Bytecode DSL boxing elimination with
  `boxingEliminationTypes = {int.class}` on `ProtosBytecodeRootNode`, an
  implementation-internal `int` carrier only; no guest primitive
  representation is introduced. Prerequisite repair:
  `ProtosFrameLexicalBindingAuthority` no longer retains the installation-time
  `BytecodeNode`. It retains the stable declaring `BytecodeRootNode` and
  resolves `getBytecodeNode()` for every frame-backed `LocalRangeAccessor`
  operation, so writes after an uncached-to-cached transition update the
  current node's local metadata coherently. Adds a regression test that
  installs the authority while uncached, transitions across a continuation
  resume, then creates, removes, re-creates and reads the binding. Presence
  remains frame-clear based (D179 C0) and no observable Protos semantics
  change.

## 0.3.124-SNAPSHOT

- `I074` (#745): enable the Truffle Bytecode DSL uncached interpreter by
  setting `enableUncachedInterpreter = true` on `ProtosBytecodeRootNode`, so
  cold roots start in the uncached tier and transition to the cached
  interpreter when they warm up. The annotation processor accepted every
  existing operation unchanged: no operation was adapted and no `forceCached`
  is used. This is internal interpreter machinery only: it changes no
  observable Protos semantics, and no performance claim is made.

## 0.3.123-SNAPSHOT

- `I073` (#744): enable Bytecode DSL bytecode-handler tail-call compilation by
  setting `enableTailCallHandlers = true` on `ProtosBytecodeRootNode`. This is
  internal interpreter machinery only: it introduces no guest tail-call
  semantics, changes no observable Protos semantics, and makes no performance
  improvement claim. Publication metadata for commit `f206eddf`, which omitted
  the version bump and this entry.

## 0.3.122-SNAPSHOT

- `I062` (#665, D169): simplify Core `Path` to relative/downward components.
  `ProtosPathValue` is now one immutable ordered `List<String>` of normal
  components plus its delegation prototype; the rooted flag, the `Parent`
  component kind, and `Component`/`Normal` wrappers are removed. Core `Path`
  installs only `relative`, `child`, `==`, and `hash`; `Path.rooted()` and
  `path.parentComponent()` no longer exist. Structural equality and hash depend
  only on the ordered component Strings. Actor and P transfer copy the
  components with the destination Path prototype. The read-only, confined,
  read-only-tree, and captured-tree Filesystem backends drop their obsolete
  rooted/Parent branches while keeping every confinement, direct-child,
  empty-Path, portable-name, and no-follow rule unchanged. No file-URL to Path
  bridge is implemented. Maintained Protos examples, conformance tests, Java
  tests, the architecture audit counts, and guide 11 are reconciled.

## 0.3.121-SNAPSHOT

- `BUG012` (#742): repair Native Image DAP stack-frame materialization by
  initializing both the explicit external-receiver `NodeLibrary` export owner
  and its generated registration owner at image build time. Keeping
  `ProtosBytecodeTagTreeNodeExports` hosted-initialized preserves the DIST006-C1
  / I069 guest runtime-compilation parsing requirement, while also initializing
  `ProtosBytecodeTagTreeNodeExportsGen` prevents its generated
  `LibraryExport.register(...)` class initializer from registering the same
  receiver again on the first runtime debugger `stackTrace`. The Native
  regression gate now exercises a real DAP breakpoint, suspended thread,
  stack trace, continuation, and clean process termination while retaining the
  existing forced Tier-2 guest-compilation checks. No Protos language semantics
  or Standard Library behavior changes.

## 0.3.120-SNAPSHOT

- `I061` (#664, D168): replace `ProcessArguments` with frozen ordinary Array
  bootstrap snapshots. `process.args()` now returns an ordinary Core `Array` of
  the stable bootstrap argument Strings, frozen before exposure and built from
  the calling Actor's own `Array` prototype on each acquisition; the Process
  runtime retains only the immutable ordered String content. Removed
  `ProtosProcessArgumentsValue`, `ProtosStandardProcessArgumentsProtocol`, and
  every ProcessArguments-specific Actor transfer, P transfer, detached-execution,
  diagnostic, interop, and Bytecode/C-prime path (the structured
  `ProcessArguments.each` operations, capability flag, guarded kind, and
  lowering block). Argument content, order, String-only elements, representability
  failure, and host-mutation isolation are unchanged. Canonical container identity
  across `process.args()` and `process.environment()` acquisitions is no longer
  a guarantee and no test asserts it; the Environment family and its native-name,
  representability, `contains`, and `get` behavior are unchanged. Tests, the
  native-boundary architecture audit, and the process guide were reconciled.

## 0.3.119-SNAPSHOT

- `PERF016` (#727): admit the semantic Integer representation family to a
  guarded selection path. `ProtosValueLookup.lookupGuardedInteger` runs the
  same lookup loop as `lookupGuarded`, exempting only the Integer receiver's
  own represented step (its delegation parent is the prelude's Integer
  prototype, taken from the frozen prelude bindings); every other represented
  value remains on generic lookup. `PrepareSendArguments.guardedIntegerSend`
  caches the selected native Closure, its exact method home (Integer prototype
  for `-`, Number prototype for `>`) and the stability Assumption, keyed by
  selector, entered Context and prelude rather than receiver identity, so
  fresh Integer values hit. The actual receiver and arguments still flow into
  the unchanged `prepareImmediateMethodCall` invocation path; no Integer
  operation is implemented at the send site. Non-native selections and any
  failed guard fall back to the existing exact paths. No observable Protos,
  Standard Library, or public behavior changes; no timing claim is made.

## 0.3.118-SNAPSHOT

- `PERF015` (#726): admit canonical `true`/`false` to the guarded structured
  selection path. `ProtosValueLookup.lookupGuardedCanonicalBoolean` runs the
  same lookup loop as `lookupGuarded`, exempting only the canonical Boolean
  receiver's own represented step (its delegation parent is fixed by
  representation to the root Object); `lookupGuarded` and every other
  represented value remain on generic lookup. `createGuardedStructuredSend`
  uses it for canonical Boolean receivers and admits only selections classified
  by the existing exact canonical Boolean behavior/home helper (`ifTrue`,
  `ifFalse`, `ifTrueIfFalse`, `and`, `or`), reusing the I072 Phase E structured
  Boolean machinery, so a valid hit no longer repeats generic represented-value
  selection. Any other selection falls back to the exact generic path. No
  observable Protos, Standard Library, or public behavior changes; no timing
  claim is made.

## 0.3.117-SNAPSHOT

- `PERF018` (#731): remove the per-logical-Case Core bootstrap and its
  temporary Polyglot Engine/Context from suite-native Test Tool execution.
  `ProtosTestLogicalCaseAttemptBridge` now bootstraps one Core Prelude lazily
  and reuses it for every Case attempt; the per-Case direct-file module
  resolver is bound to each fresh Process's own Polyglot Context and reached
  through the new internal `ProtosContextBoundModuleResolver`. Every attempt
  still creates a fresh semantic Process and Process Context, rematerializes the
  suite declaration, revalidates the discovery signature, resolves the selected
  Test inside that Process, and keeps private stdout/stderr. No observable
  Protos, Standard Library, or Test Tool behavior changes. A regression test
  proves one shared Prelude across Cases whose same-named local modules differ.

## 0.3.116-SNAPSHOT

- `DIST005-D6` (#549): add an explicit, fail-closed publication primitive for
  an already-prepared and already-verified `JVM_PLUS_NATIVE` prerelease.
  `dist/publish_release.py` consumes a separate exact publication authorization
  bound to candidate SHA, public version/tag, release-manifest SHA-256, and
  prerelease/non-draft GitHub Release mode. Before any public mutation it
  revalidates detached clean candidate identity and release-only lineage,
  canonical repository identity, release manifest, notes, archive/checksum
  bytes, the independent multi-asset metadata verifier, local and remote tag
  state, and any existing GitHub Release/assets. Publication creates or safely
  reuses only the exact lightweight `vV` tag, exact `Protos V` prerelease body,
  and the five current downloadable assets: Native ZIP/checksum, portable JVM
  ZIP/checksum, and `RELEASE_MANIFEST.txt`; release notes remain the body rather
  than an asset. Exact partial publication is resumable without tag force,
  asset clobber, deletion, replacement, or automatic rollback, while conflicting
  tags, metadata, bodies, digests, or unexpected assets fail closed. Post-
  publication verification re-reads the remote tag, GitHub Release metadata,
  complete asset set, downloaded asset digests, and authorized manifest identity
  before reporting success. The publication path remains separate from
  `dist/prepare_release.py`; implementation tests use fixtures and local Git
  only and perform no real release publication.

## 0.3.115-SNAPSHOT

- `PERF019` (#736): remove the two independent caller-Actor serialization
  sources that limited Test Tool replacement admission under bounded parallel
  execution. Future completion now supports an exact runtime-only targeted
  continuation handoff: when terminalization wakes the one waiting lane Task,
  that exact queued Task can continue immediately while unrelated Actor-local
  work retains its existing FIFO order and cancellation still defeats the
  handoff. Logical Case execution no longer carries source text through the
  caller Actor; after the source association has been authorized, each host
  execution carrier performs the required fresh UTF-8 read immediately before
  D153 fresh-Process declaration rematerialization, preserving per-Case source
  freshness without reusing discovery source. The Process-snapshot route uses
  the same host-carrier source model. A scheduling regression fixture now waits
  for its host wrapper's physical completion before asserting physical
  completion order, avoiding a race exposed by the faster Future handoff
  without weakening its `[1, 0, 2]` ordering requirement. The canonical
  `--jobs 16` Test Tool run completes all 1263 logical Cases with 16 lanes and
  complete 1247/1247 replacement-pair coverage in 70.84 s at 1096% process CPU;
  mean completion-to-replacement delay falls to 104.643 ms and the selected
  caller-serialization intervals to 60.792 ms. The full repository validation
  passes.

## 0.3.114-SNAPSHOT

- `DIST005-D5` (#549): compose the already-proven DIST001/DIST005 release
  primitives into one deterministic, fail-closed `dist/prepare_release.py`
  candidate-preparation entry point. After an explicit selection record exists,
  the orchestrator materializes or safely resumes one detached release-only
  candidate, builds and completely admits the selected Linux x86_64 Native
  artifact, builds and completely admits the portable JVM compatibility
  fallback, then prepares and independently verifies their common
  `JVM_PLUS_NATIVE` release envelope. Preparation remains strictly separate
  from publication: it creates no Git tag, GitHub Release, or public asset.
  Candidate materialization retains the historical `origin/main` lineage guard
  by default while allowing this orchestrated pre-publication proof to use an
  explicitly selected commit already on local `main`, so implementation can be
  proven before it is pushed. Portable distribution Test Tool admission now
  uses bounded `--jobs` parallelism, aligned with the repository
  `PROTOS_TEST_JOBS=8` default and overridable by that environment variable,
  instead of silently falling back to the Test Tool's serial default. Generated
  release-envelope state is safely replaceable only under the candidate
  `target/` tree, making preparation retryable without weakening candidate
  identity or publication guards.

## 0.3.113-SNAPSHOT

- `DIST005-D4` (#549): add fail-closed clean-candidate machinery for the
  owner-approved Native public artifact under the selected Oracle Linux 10 /
  Linux x86_64 / glibc 2.39 / `-march=compatibility` policy. Native
  public-prerelease builds now reuse the existing DIST001 release identity and
  baseline lineage model while retaining the distinct development-only Native
  path. The Native archive records empirical ELF evidence derived from the exact
  executable bytes, including GLIBC symbol versions and observed maximum,
  interpreter, `DT_NEEDED`, resolved dynamic-library closure, build-host
  provenance, configured Native Image `-march`, and post-link GNU ISA evidence.
  Archive admission independently re-inspects the extracted Native executable
  and requires the recorded evidence to match. The multi-asset
  `JVM_PLUS_NATIVE` envelope now fails closed on missing, malformed, excessive,
  or internally inconsistent GLIBC evidence, unresolved library closure,
  incompatible build ISA policy, or mixed candidate identity. The observed
  post-link ISA evidence is retained as evidence rather than being rewritten as
  the build policy. Final D4 closure still requires the exact clean detached
  `0.3.113` candidate, complete Native admission, portable JVM fallback built
  from the same candidate identity, and real multi-asset envelope verification;
  this changelog entry does not authorize release publication.

## 0.3.112-SNAPSHOT

- `DIST005-D3` (#549): apply the selected public Native platform, CPU ISA, and
  libc ABI metadata policy to the approved `JVM_PLUS_NATIVE` release model.
  The public Native artifact is Linux x86_64, dynamically linked against glibc,
  built on the existing Oracle Linux 10 Native Image toolchain generation, and
  declares `libc_abi_min=2.39`. Native Image compilation now explicitly uses
  `-march=compatibility` so the public artifact does not inherit the platform's
  stronger default x86-64 ISA assumption. Native `RUNTIME.txt`, artifact keys,
  platform identity, release manifests, and release notes expose the selected
  ABI and CPU policy, while the multi-asset envelope fails closed on missing or
  mismatched OS, architecture, libc family, libc ABI floor, linkage, or CPU ISA
  identity. The portable JVM compatibility/fallback artifact and the existing
  release identity model remain unchanged. This slice records and enforces the
  selected policy; clean-candidate proof of the produced binary's actual ABI
  and platform properties remains for the subsequent DIST005-D4 validation
  slice.

## 0.3.111-SNAPSHOT

- `DIST005-D1` (#549): add the multi-asset release-envelope foundation for the
  approved `JVM_PLUS_NATIVE` distribution model. A single exact release
  candidate can now describe and independently verify a recommended Native
  artifact together with the portable JVM compatibility/fallback artifact,
  while preserving the existing single-JVM release-envelope workflow.
  Per-artifact identity covers stable artifact key, kind, role, platform,
  runtime, source/candidate identity, archive checksum, and license/notice
  identity. Multi-asset verification fails closed on mixed candidates,
  duplicate identities, ambiguous recommended roles, missing platform/runtime
  metadata, unknown artifact kinds, checksum mismatches, and incomplete
  license/notice identity, with deterministic artifact ordering. This slice
  does not select a public Native CPU ISA or platform support matrix, build a
  public Native release artifact, or change Protos language semantics.

## 0.3.110-SNAPSHOT

- `DIST005-B` (#549): implement and prove a development Native Image
  self-contained distribution for the current Linux x86_64/glibc target. The
  extracted archive carries the Native Protos executable plus the required
  Protos library, tool, test, license, runtime/dependency identity, provenance,
  and checksum material, and the launcher selects the bundled Native payload
  without probing for Java while preserving the existing JVM execution paths
  when no Native payload is present. Native Image construction now uses a
  recreated thin application JAR rather than feeding a previously shaded JAR
  back into the Native classpath, making repeated Native builds compositionally
  stable. Closed-world reachability retains the LSP4J protocol surface
  structurally, while the Native build keeps Truffle runtime compilation
  enabled. Distribution admission exercises version/help/eval, unrelated-CWD
  source execution, workspace run, Package Tool, focal and complete Test Tool
  execution, real PTY REPL, real LSP stdio, real DAP TCP, archive integrity,
  absence of external Java/Maven, and forced guest compilation of both generated
  Bytecode roots to Truffle Tier 2. No Protos language, specification, Standard
  Library, or public tool semantics change.
## 0.3.109-SNAPSHOT

- `BUG011` (#738): make Test Tool PERF017 JFR admission instrumentation
  capability-optional so the Test Tool runs correctly in GraalVM Native Image,
  where Flight Recorder is not available. PERF017 previously initialized its
  JFR `EventType` eagerly; Native Image therefore raised
  `InternalError: Flight Recorder is not supported on this VM` as soon as the
  Test Tool reported its first lifecycle event. The subsequent Session teardown
  then attempted to close the RuntimeHost while Process Contexts from the
  interrupted execution were still active, masking the primary failure with
  `Polyglot runtime host cannot close while Process Contexts are active`.
  PERF017 now reports disabled when JFR is unavailable while retaining the
  existing JFR event behavior on supported JVMs; the RuntimeHost close
  invariant remains unchanged. The Native Image regression gate now includes
  the canonical `uri/parse.protos` Test Tool smoke and requires its 4/4 result,
  zero exit status, and absence of the Process-Context teardown diagnostic.

## 0.3.108-SNAPSHOT

- `DIST006-C1`: restore Native Image compatibility on the canonical GraalVM /
  Truffle 25.4.4.1.1 baseline without weakening runtime compilation. The
  Bytecode DSL `NodeLibrary` bridge keeps frame/activation extraction
  PE-visible while moving debugger-scope construction and debugger-only member
  projection behind narrow `TruffleBoundary` edges. This prevents the
  tooling-only projection from pulling broad Java collection, debugger,
  logging, reflection, I/O, and SubstrateVM paths into guest runtime
  compilation. Native Image 25.4 builds successfully, both generated Bytecode
  DSL roots still compile at Truffle Tier 2, and the retained native regression
  gate reports `OPT_FAILED=0`, `FRAME_WITHOUT_BOXING_FAILURES=0`, and
  `COMPILATION_FAILURES=0`.
- `DIST006-C1` also binds the Native Image container to the canonical 25.4
  toolchain authority and extends the repository toolchain verifier so
  all-surface verification detects stale Native Image channel/JDK bindings
  while development-scope verification remains unaffected.

## 0.3.107-SNAPSHOT

- `PERF017` (#729): add Test Tool-private JFR instrumentation for the
  completion-to-replacement admission path without changing scheduling,
  concurrency, result ordering, lifecycle, cancellation, Process/Context
  ownership, or public Test Tool output. The internal
  `protos.TestToolPerf017Admission` event timestamps host Case completion,
  caller-domain completion-task entry, Future terminalization, lifecycle
  terminal/start observation, submission entry/return, and replacement-carrier
  entry. Stable diagnostic `operationId` values correlate one host operation,
  while weakly held `laneId` values correlate the existing pull-lane Task
  across lifecycle and execution seams. The ordinary logical-Case facility
  and Process-snapshot facility emit the same schema. This slice is
  diagnostic-only: it establishes measurement points for quantitative
  attribution of lost `--jobs` scaling and does not claim a scheduling cause
  or implement a performance fix.

## 0.3.106-SNAPSHOT

- `TOOL009-F-I1`: consolidate the F-I1 slice of the conformance test corpus
  (`boolean`, `float`, `integer`, `number`, `numeric-conversion`,
  `numeric-equality`, `equality`, `string`, `object`, `object-structural`,
  `path`, `call`, `core-surface`, `matching`, `network`, `surface-sugar`) from
  282 one-Test-per-file suite-native sources down to 58 sources grouped by
  semantic/behavioral cohesion (shared corpus, `ExecutionRequirement`,
  namespace, bootstrap, and execution authority), per the ratified
  `TOOL009` source-granularity policy. Every existing `Test(...)` is preserved
  verbatim as an independent entry in its consolidated source's `tests`
  Array: 282 Logical Cases before and after, with exact selector/name and
  body preservation confirmed by full-corpus reconciliation. `manifest.tsv`
  is updated to the 58 consolidated paths. `network/network-prototype.protos`
  is retained unchanged as the sole reused single-case source for its family.
  Two live-path regression fixtures
  (`protos/tests/tooling/tool002-d2-manifest-plan.protos`,
  `protos/tests/tooling/tool002-e1b-package-toml-filesystem.protos`) and
  `ProtosTestToolManifestPlanTest.java` are updated from the retired
  `integer/add-small.protos` first-manifest-row identity to the new first row,
  `integer/arithmetic-and-unary.protos`. The suite-native source-granularity
  policy (semantic/behavioral cohesion as the grouping unit; one-Test-per-file
  and minimum file count are not repository conventions) is now documented in
  `protos/AGENTS.md`.

- Fix a syntax defect introduced by the automated `TOOL009-F-I1`/`TOOL009-F-I2`
  conformance-corpus consolidation: consecutive `Test(...)` entries inside a
  consolidated source's `tests: [ ... ]` Array were separated only by a
  logical `NEWLINE`, with no comma. Per `spec/PROTOS_GRAMMAR.md` §12.3/§17, an
  Array construction expression desugars to an ordinary call
  (`[a, b] -> Array(a, b)`), and a `NEWLINE` is never an argument separator; a
  comma is required between every two elements. Every affected source failed
  to parse (`bin/protos <file>` raised `Syntax error: Expected ']' but found
  IDENTIFIER`), and under the Test Tool the same parse failure surfaced only
  as an opaque `Test tool error: Object {}`, because Discovery detaches a
  failed declaration execution into a generic `Error` with no local slots.
  Insert the missing comma after every `Test(...)` entry's closing `})` that
  is immediately followed by another `Test(...)` entry, across the 53
  affected sources under `protos/tests/conformance/{bytes,collections,
  control,encoding,future,reflection,text-reader,text-writer}/` (229 sites);
  no test body, selector/name, or `tests` Array element count changes. No
  production or Standard Library source is affected, so no implementation
  version increment applies to this change.

## 0.3.105-SNAPSHOT

- `PERF014` (#725) follow-up: the direct Closure-call guarded specialization
  gains a second, definition-keyed cache tier on `PrepareClosureCall`,
  `PrepareClosureCallArguments`, and `PrepareDefaultClosureCallArguments`,
  mirroring `PrepareSendArguments.fastOrdinarySend`'s existing institution for
  ordinary sends. The first tier alone keyed admission on exact receiver
  instance identity (`receiver == cachedReceiver`, `limit = 3`), which paid
  full specialization-establishment cost (a fresh guarded D013 lookup plus a
  new `Assumption`) for every distinct `ProtosClosureValue` materialization of
  the same Closure definition, converging to the generic fallback only after
  three misses. The new `fastDirect` specialization instead keys the cached
  Context-owned target on `ProtosClosureValue.definition()` — the immutable
  `CanonicalClosure` identity shared by every materialization of one Closure
  literal — while still performing the authoritative D013 `call` selection
  fresh on every hit via `directClosureCallSelectionOrNull`, so a local
  override on one particular instance is observed exactly as before. A
  controlled baseline-vs-worktree `--jobs 8`/`--jobs 16` comparison confirmed
  receiver-identity churn as the material cause of a Protos test-suite
  concurrency-adjacent slowdown observed after the first tier alone, and
  confirmed this second tier resolves it without any observable Protos
  semantic change.

## 0.3.104-SNAPSHOT

- `BUG010` (#728): the Test Tool's normal D120 progress is now observable
  from terminal logical Case events while a `protos test` invocation is
  still running, instead of only after every logical Case has already
  completed.

  `protos/tools/test/Main.protos` previously created every suite's
  `Progress` state and drove `Progress.observer(...)` only after
  `LogicalCaseRunner.run(...).value()` had already awaited the complete
  logical run, then grouped and replayed the already-finished completions
  through `Progress.observer(...)` in a single post-run pass. Even the
  initial `[phase] 0/N` line could not reach the reporting backend until the
  whole invocation had finished.

  `protos/tools/test/LogicalCaseRunner.protos` gains a new optional,
  backward-compatible trailing parameter on `run(...)`,
  `completionObserver = null`, called `(index, completion)` at the exact
  point an individual logical Case already crosses the existing terminal
  barrier — the narrow, presentation-neutral counterpart of the D120
  `terminalObserver` seam already established on the D108 path in
  `Runner.protos`. `Main.protos` now creates every suite's `Progress` state
  and `Progress.observer(...)` before scheduling begins, and wires a new
  `logicalCompletionObserver` through this seam to classify and forward
  each completion to its suite's observer immediately, independently of
  when the remaining Cases complete. The final `logicalResults` projection,
  exit-code classification, `--jobs` bounded admission, D174/D176
  lifecycle/watchdog behavior, and guest stdout/stderr privacy are
  unchanged; stable logical TestPlan result order is preserved exactly as
  before.

## 0.3.103-SNAPSHOT

- `PERF014` (#725): direct Closure-call invocation (`operation()`,
  `operation(args)`, and default-argument call positions) gains a guarded
  fast-hit specialization on `PrepareClosureCall`, `PrepareClosureCallArguments`,
  and `PrepareDefaultClosureCallArguments`, converging with the I072-A
  guarded-selection institutions `PrepareSendArguments` already established
  for ordinary sends instead of introducing a parallel mechanism.

  A new shared helper, `createGuardedDirectClosureCall`, reuses
  `ProtosValueLookup.lookupGuarded("call")` to obtain the authoritative D013
  selection under a selector-specific `Assumption`, admits the fast hit only
  when the selection is exactly the canonical `Object.call` implementation
  (`ProtosStandardObjectProtocol.isCanonicalStandardCallSelection`) on a
  non-native, ordinary source-backed Closure receiver, and reuses
  `PrepareSendArguments.fastOrdinarySendTarget` for the Context-owned
  Bytecode activation target (including foreign-Context projection). A valid
  hit builds the ordinary `ProtosActivation.forClosureInvocation` shape and
  the existing shared `EnterClosureCall.direct` `DirectCallNode` exactly as
  before, without repeating the D013 lookup or the
  `finishPreparingComposedCallByImplementation` structured-protocol
  classification against the ~14 native Object/Boolean/Array/Map/etc.
  callback shapes on every call. Any override, shadowing, native body,
  non-canonical selection, or projection failure misses and falls through to
  the exact, unmodified generic `prepareClosureCall` path, so call
  shadowing, replacement, and removal/recreation invalidation remain exactly
  as observable as before.

  This slice is structural/product-correctness only: it does not change the
  causal timing conclusion from the PERF010-B/PERF013 checkpoint and does not
  touch `protos-benchmarks`. A separate controlled-timing checkpoint measures
  whether the closure-call/guest-call increment actually falls by a
  multiplicative factor.

## 0.3.102-SNAPSHOT

- `PERF013` Slice B2 (#724): a proven `CapturedResolved` lexical write whose
  owner root shares the current lowering group (Slice A/A2/A3, Slice B1) now
  compiles to new `ResolveCapturedMaterializedWritableLexicalTarget`/
  `AssignCapturedMaterializedLocal` Bytecode operations instead of the
  runtime-authority `ResolveCapturedWritableLexicalTarget`/
  `AssignCapturedFrameLocal` path, mirroring Slice B1 on the write side. Both
  operations share the same constant `MaterializedLocalAccessor` operand,
  built from the owner's own `BytecodeLocal`, so the accessor's identity is
  proven and partial-evaluation constant at both resolution and assignment
  time; only the owner's retained `MaterializedFrame` is looked up
  dynamically.

  The destination is still fully selected before the RHS is evaluated, and
  `AssignCapturedMaterializedLocal` only revalidates (via `isCleared`) and
  writes the exact selection `ResolveCapturedMaterializedWritableLexicalTarget`
  already returned; it never re-resolves or retargets the destination after
  the RHS runs, so late nearer-binding creation/removal/recreation during RHS
  evaluation keeps the exact existing D179 C0 observable semantics.
  `CapturedLexicalWriteTarget` gained a `materializedOwnerFrame` variant
  alongside its existing generic/frame-backed ones. `CLOSED` destinations
  remain writable and `FROZEN` destinations are rejected exactly as before.

  `CanonicalToBytecodeLowerer.emitBodyAssign` and `emitDefaultAssign` both
  reuse the existing `capturedOwnerBytecodeLocal`/`frameLocalsByScope`
  infrastructure from Slice B1 to select the new fast path only when the
  owner's `BytecodeLocal` is actually available in the current shared
  `BytecodeRootNodes` group; otherwise they fall back to the unchanged
  runtime-authority path, still necessary for an isolated Closure
  rebuild/rematerialization that does not include its lexical owner in the
  same group (PERF013 Slice C).

  Slice B1 captured reads (`ReadCapturedMaterializedLocal`) are unchanged.
  This slice changes no observable Protos semantics.

## 0.3.101-SNAPSHOT

- `PERF013` Slice B1 (#724): a proven `CapturedResolved` lexical read whose
  owner root shares the current lowering group (Slice A/A2/A3) now compiles
  to a new `ReadCapturedMaterializedLocal` Bytecode operation instead of the
  runtime-authority `ReadCapturedFrameLocal` path. The operation's constant
  operand is a Truffle Bytecode DSL-generated `MaterializedLocalAccessor`,
  built from the owner's own `BytecodeLocal` (never a hand-rolled
  `rootIndex`), so the accessor's identity is proven and partial-evaluation
  constant at lowering time; only the owner's retained `MaterializedFrame`
  (already held by `ProtosFrameLexicalBindingAuthority`, exposed through a
  new `retainedMaterializedFrameForCapturedAccess` seam) is looked up
  dynamically. `ProtosBytecodeRootNode` now enables
  `enableMaterializedLocalAccesses`.

  `CanonicalToBytecodeLowerer` gained a backend-private
  `frameLocalsByScope` registry mapping each genuine lexical scope to the
  `BytecodeLocal`s created for it, populated by `emitRootBody` before that
  root's own body (and therefore any nested Closure/object-body root) is
  lowered. `emitLookup` now uses this registry, together with the
  already-present (previously unused) `capturedOwnerMatchesCurrentRoot`
  structural check, to select the new fast path only when the owner's
  `BytecodeLocal` is actually available in the current shared
  `BytecodeRootNodes` group; otherwise it falls back to the unchanged
  `ReadCapturedFrameLocal` path, which remains necessary for an isolated
  Closure rebuild/rematerialization that does not include its lexical owner
  in the same group (PERF013 Slice C).

  The exact D179 C0 presence/fallback algorithm (current-context check,
  nearer-context presence guard, `isCleared`-based `PRESENT(null) != ABSENT`
  distinction, owner-removal/recreate observation) is preserved unchanged;
  only the read's physical local-identity/frame-access mechanism changes.
  Captured writes are untouched (`ResolveCapturedWritableLexicalTarget`/
  `AssignCapturedFrameLocal`); that migration is PERF013 Slice B2.

  Existing instruction-identity assertions in
  `ProtosI068Slice5CapturedMaterializedLexicalLoweringTest` and
  `ProtosI068Slice7ActivationLexicalDecompositionTest` are updated for the
  same-group proven captures whose selected instruction changed; the
  isolated-rebuild/Candidate/Dynamic assertions that must keep observing the
  old runtime-authority path are unchanged.

## 0.3.100-SNAPSHOT

- `PERF013` Slice A3 (#724) closes the last remaining independent
  `BytecodeRootNodes` group: an object-construction helper root (the body of
  an object literal, e.g. `{ x: 1 }` or `obj: { method: () => x }`) now
  lowers into the exact same shared `BytecodeRootNodes<ProtosBytecodeRootNode>`
  group as the lexical lowering unit that contains the object literal,
  instead of opening an independent lowerer/`create()` call. A Closure
  declared inside that object body continues to nest into the same group via
  the existing Slice A/A2 `bytecodeClosurePlan`/`lowerNestedClosureRoot`
  mechanism, so owner root, object-construction helper root, and any
  arbitrarily deep nested Closures inside the object body now all belong to
  one physical group.

  `CanonicalToBytecodeLowerer`'s `CanonicalObject` validation cases
  (`validateSupportedDefaultExpression` and `validateSupportedExpression`) no
  longer eagerly build the object's helper root; they only register
  `CanonicalCompose` reserved-slot-name metadata and perform a
  structural-support walk of the object body, mirroring the structural-only
  treatment already used for `CanonicalClosure`. Real emission
  (`emitBodyObjectLiteral`/`emitDefaultObjectLiteral`) resolves a new
  backend-private, freeze-once `ProtosObjectBodyTargetCell` — the
  object-construction analogue of `ProtosClosureExecutionPlanCell` — before
  opening `PrepareObjectConstruction`, deferring the helper root's real
  `RootCallTarget` until the owning group's `create()` call returns. The
  now-dead independent-construction path (`bytecodeObjectBodyTarget`,
  `bytecodeObjectBodyTargets`, `lowerObjectBodyRoot`) is removed.

  Sharing this physical root group does not change object-construction
  semantics: an object-construction body remains lowered with
  `genuineExecutionContextRoot=false`, so it still never receives a
  frame-backed lexical-binding authority (`ProtosFrameLexicalLayout`/
  `ProtosFrameLexicalBindingAuthority`), and it is still not a lexical
  capture scope. A Closure created during object construction continues to
  capture only the enclosing genuine lexical execution context(s)
  (`ProtosActivation.forObjectConstruction`/
  `lexicalContextsForClosureCapture` are unchanged), never the constructed
  object itself, while the constructed object remains the Closure's
  receiver. Object slots (e.g. `x` in `{ x: 1 }`) remain receiver state, not
  lexical locals. `PrepareObjectConstruction`/`EnterObjectConstruction`/
  `ResumeObjectConstruction`/`FinishObjectConstruction`'s suspension protocol
  is unchanged apart from `PrepareObjectConstruction` now consuming a
  `ProtosObjectBodyTargetCell` instead of a plain constant `RootCallTarget`.

  This slice does not adopt `MaterializedLocalAccessor` (PERF013 Slice B) and
  does not change the captured-local read/write mechanism
  (`ReadCapturedFrameLocal`/`AssignCapturedFrameLocal`), which remains
  exactly as before.

## 0.3.99-SNAPSHOT

- `PERF013` Slice A2 (#724) closes the one exception Slice A retained: a
  parameter default-value Closure literal (for example `g` in
  `f: (x, g = () => x) => g`) now lowers into the exact same shared
  `BytecodeRootNodes<ProtosBytecodeRootNode>` group as its lexical owner and
  any ordinary body Closure, instead of opening an independent
  lowerer/`create()` call. `CanonicalToBytecodeLowerer.validateSupportedDefaultExpression`
  now performs structural-support validation only for a default-value
  Closure — mirroring the structural-only check `validateSupportedExpression`
  already used for an ordinary body Closure — rather than eagerly
  constructing its execution plan/root before the owner root's builder is
  even open. Real emission (`emitClosureParameterBindings` ->
  `emitExpression`) already ran, and continues to run, while that builder is
  open, so it now reaches the same `bytecodeClosurePlan`/
  `lowerNestedClosureRoot` path an ordinary nested Closure uses, recursively,
  to arbitrary default-expression nesting depth. The now-unreachable
  independent-construction machinery
  (`independentBytecodeClosurePlan`, `ProtosClosureExecutionPlanCell.independent`,
  `isFromIndependentGroup`) is removed.

  This closes the last known lexical-root grouping exception ahead of PERF013
  Slice B's `MaterializedLocalAccessor` adoption. No observable Protos
  semantics change: capture-by-reference, sequential parameter
  establishment, default-expression evaluation only when the argument is
  absent, earlier-parameter visibility in later defaults, `D179` C0,
  `I071`, the object-construction-body boundary, and `RootTag`/tooling
  architecture are all unaffected. The captured-local read/write mechanism
  itself (`ReadCapturedFrameLocal`/`AssignCapturedFrameLocal`) remains
  unchanged and is still PERF013 Slice B's responsibility; this slice does
  not adopt `MaterializedLocalAccessor`.

## 0.3.98-SNAPSHOT

- `PERF013` Slice A (#724) restructures `CanonicalToBytecodeLowerer` so a
  lexical owner root and every Closure it lexically contains now lower into
  one shared `BytecodeRootNodes<ProtosBytecodeRootNode>` group: a Closure
  reached while an enclosing root's own `beginRoot()`/`endRoot()` pair is
  still open nests its own `beginRoot()`/`endRoot()` pair inside that same
  open builder/`create()` invocation (recursively, to arbitrary lexical
  depth), instead of opening an independent lowerer/`create()` call the way
  every Closure did before this slice. This is the prerequisite topology
  PERF010-B/PERF012 identified for PERF013 Slice B's `MaterializedLocalAccessor`
  adoption, which requires the reading child root and its lexical owner's
  root to belong to the same group.

  Because `RootNode.getCallTarget()` refuses until the whole group's
  `create()` call returns, a nested Closure's real `ProtosClosureExecutionPlan`
  cannot be constructed at the exact point its `MaterializeClosure` bytecode
  is emitted. `ProtosClosureExecutionPlanCell` is a new backend-private,
  immutable-once-frozen indirection emitted as that bytecode's constant
  instead: the lowerer freezes it with the real plan once the owning group's
  `create()` call returns. It is never guest-visible and is always frozen
  before any guest code in the affected lowering unit can execute, so it does
  not introduce a second, mutable execution-plan authority. A Closure used as
  a parameter default value keeps its previous independent construction path
  (validated/lowered before its owner's own `create()` call is even open, so
  no shared builder exists to nest it in) unchanged.

  `ProtosFrameLexicalLayout` remains precomputed per root exactly as PERF012
  left it; object-construction bodies remain non-lexical-owner roots with
  their own independent `BytecodeRootNodes` group, unchanged; `RootTag`/
  tooling architecture, capture-by-reference, `D179` C0, `I071`, and every
  other observable Closure/capture semantic are unaffected — this slice is a
  lowering-topology change only. The captured-local read/write mechanism
  itself (`ReadCapturedFrameLocal`/`AssignCapturedFrameLocal`) is unchanged
  and remains PERF013 Slice B's responsibility.

## 0.3.97-SNAPSHOT

- `PERF012` (#723) removes the JDK `LinkedHashMap`/`ArrayList` construction
  that previously ran inside `ProtosFrameLexicalBindingAuthority`'s
  constructor on every installation of a genuine `ROOT`/`CLOSURE` execution
  context's frame-backed lexical authority. That construction rebuilt an
  already-static name/ordinal layout — the same declaration-order names
  `CanonicalToBytecodeLowerer` already carried as a lowering-time constant —
  from scratch on every invocation of the owning root, and PERF010-B Step 0
  identified it as the cause of a "too deep inlining" partial-evaluation
  bailout on Bytecode roots whose frame-backed layout was large enough to
  expand through JDK `HashMap`/`Class` generic-signature machinery.

  The name/ordinal layout is now `ProtosFrameLexicalLayout`, an immutable,
  backend-private descriptor built exactly once per root during
  `CanonicalToBytecodeLowerer` lowering and installed as a Bytecode
  `ConstantOperand` in place of the previous raw `String[]`. Every
  per-invocation `ProtosFrameLexicalBindingAuthority` instance now holds a
  reference to that one shared layout instead of rebuilding its own copy;
  only the authority's runtime frame/dynamic-overflow state remains
  per-invocation. The layout carries no binding values or presence state of
  its own, so this remains a single frame-backed authority per genuine
  execution context, not a second store.

  `PLAT036` Candidate D's frame-backed-single-authority shape, `D179` C0
  clear/recreate, `I071` frame-native cleared-presence semantics,
  `PRESENT(null) != ABSENT`, dynamic overflow, capture-by-reference,
  establishment/name order, and debugger/reflection projection are
  unaffected: the same authority object, the same `LocalRangeAccessor`
  physical storage, and the same presence derivation are used exactly as
  before. No observable Protos semantics change.

## 0.3.96-SNAPSHOT

- `I072` Phase E (slice 2) extends structured-control convergence to the
  remaining collection-callback families admitted by the existing structured
  capability vector: `Array.each`, `Bytes.each`, `ProcessArguments.each`,
  `Environment.each`, `IdentityMap.each`, `Map.each`, the `Map` read-lookup
  family (`at`/`containsKey`/`atIfAbsent`), `Map.atPut`, and `Map.remove`. The
  `guardedStructuredSend` specialization added in slice 1 now classifies a
  stable canonically-selected send against all thirteen structured kinds using
  the existing canonical-selection helpers (behavior identity plus home/
  provenance, or receiver identity for `ProcessArguments`/`Environment`), and
  feeds the single cached kind into the unchanged `finishPreparingComposedCall`
  path; collection-callback order, snapshot timing, mutation visibility,
  suspension/resume and error propagation are unaffected.

  The slice also converges the four remaining structured paths that were still
  classified only from native-body identity at native-body execution time:
  `Object.caseOf`, `Array.match`, `Map.match`, and `IdentityMap.atIfAbsent`.
  These four are outside the `StructuredCallCapabilities` vector — `NativeCall`
  recognizes them from `nativeBody` identity independent of that vector — so a
  canonically-selected ordinary send to one of them previously still paid the
  full generic D013 lookup and classifier scan on every hit even though the
  other thirteen kinds had already converged. New canonical-selection helpers
  (`ProtosStandardObjectProtocol.isCanonicalStandardCaseOfSelection`,
  `ProtosStandardArrayProtocol.isCanonicalStandardMatchSelection`,
  `ProtosStandardMapProtocol.isCanonicalStandardMatchSelection`, and the
  existing `ProtosStandardIdentityMapProtocol.isCanonicalStandardAtIfAbsentSelection`)
  let `guardedStructuredSend` recognize a stable canonical selection of these
  four and route it through the same guarded fast path; the resulting
  `NativeCall` is unchanged, since its `isStructuredCaseOf`/`isStructuredMapMatch`/
  `isStructuredArrayMatch`/`isStructuredIdentityMapAtIfAbsent` accessors already
  recognize the operation from `nativeBody` regardless of how the call was
  prepared. `Object.call`, standard import, and the generic
  `finishPreparingComposedCallByImplementation` classifier remain unchanged
  and still required by the cache-miss/megamorphic, direct-Closure-invocation,
  and Object.call/import paths, which do not carry D013 selection provenance
  for the guarded specialization to reuse. This completes the structured
  families identified for `I072` Phase E; no dead compatibility machinery was
  found to remove. No observable Protos semantics change.

## 0.3.95-SNAPSHOT

- `I072` Phase E (slice 1) begins structured-control convergence: a stable,
  canonically-selected send to `Object.ensure`, `Error.handle`, `Object.while`,
  or a Boolean callback selector (`ifTrue`/`ifFalse`/`ifTrue:ifFalse:`/`and`/
  `or`) no longer repeats the general D013 lookup and the generic
  `finishPreparingComposedCallByImplementation` classifier scan on its valid
  hit. A new `guardedStructuredSend` specialization on `PrepareSendArguments`
  reuses the exact Phase A `ProtosValueLookup.lookupGuarded`/`Assumption`
  contract, classifies the selected value exactly once against the existing
  canonical-selection helpers (behavior identity plus home/provenance, never
  native-body identity or selector spelling alone), and feeds the cached kind
  directly into the unchanged `finishPreparingComposedCall`/
  `PreparedClosureCall.nativeCall` path, so execution, suspension, cleanup and
  error semantics are byte-identical to the generic path. Any override, alias
  at a noncanonical home, or invalidated selection falls straight through to
  the exact existing generic path, unchanged. The remaining structured
  families (`Array`/`Bytes`/`ProcessArguments`/`Environment`/`IdentityMap`/
  `Map` callbacks, `Map` read/`atPut`/`remove`, `Object.caseOf`/`match`) and
  the corresponding dead-compatibility-machinery review remain open follow-up
  slices of `I072` Phase E; `StructuredCallCapabilities` and the generic
  classifier cascade are unchanged and still required by those paths. No
  observable Protos semantics change.

## 0.3.94-SNAPSHOT

- `I072` Phase D completes the prepared-call/control-mode separation left open
  by the D1 slice. The internal `PreparedClosureCall` invocation carrier is no
  longer one universal class; it is a small dispatch interface implemented by
  three mutually exclusive leaf representations — a lean `OrdinarySourceCall`
  (rich-activation and compact frame-argument shapes), `NativeCall` (native
  body plus the D1 `StructuredCallCapabilities` holder), and
  `ModuleInitializationCall` (standard-import module lifecycle). An ordinary
  source-backed, nonsuspending call now physically carries only a selected
  target, its compact frame arguments (or pre-target activation), and the
  return-home/ownership state non-local return completion requires; it never
  allocates native-body, structured-control-capability, or
  module-initialization fields (PLAT040 Candidate F′: pay only for what is
  used). Return-home ownership/completion and non-local-return handling are
  shared between the ordinary and native shapes through a small internal base
  class instead of being duplicated. Task-owned dispatch, C-prime suspension
  and continuation resume, guarded selector-specific stability (Phase A), the
  compact frame-argument ABI (Phase B), and conditional guest-Context/Array
  materialization (Phase C) are unaffected; no observable Protos semantics
  change. Structured-control convergence remains Phase E, not started here.

## 0.3.93-SNAPSHOT

- `I072` Phase D (slice 1) removes the physical structured-control capability
  vector from the ordinary/native/module-initialization forms of the internal
  `PreparedClosureCall` invocation carrier. The sixteen mutually-exclusive
  `structuredEnsure`/`structuredWhile`/`structuredBoolean`/collection-callback/
  `structuredObjectCall`/`structuredImportRuntime`/etc. fields are now grouped
  behind a single nullable `StructuredCallCapabilities` holder that is only
  allocated when a call genuinely owns one of those capabilities (PLAT040
  Candidate F′: optional semantic capability must not force unrelated ordinary
  calls to carry its full physical machinery). Ordinary source-backed calls,
  compact frame-argument calls, plain native calls, and module-initialization
  calls now pass `null` and pay no allocation or field cost for structured
  control. Every mutual-exclusion check, dispatch guard, and error message is
  preserved exactly; no observable Protos semantics change. Full type-level
  separation of the ordinary call path from the shared carrier class remains
  open for a later Phase D slice.

## 0.3.92-SNAPSHOT

- `I072` Phase C removes unconditional guest execution-context materialization
  from ordinary optimized source-backed calls while preserving the Phase A
  selector-specific lookup guard and the Phase B compact frame-argument ABI.
  The current root now operates primarily against the single frame-backed
  lexical authority; a genuine guest `Context` object is materialized only when
  guest semantics observe, capture, escape, reflect, or otherwise require it.
- Supplied positional arguments remain an internal compact vector through
  ordinary parameter/default binding instead of being wrapped eagerly in a
  frozen guest `Array`. Guest Array materialization remains available on demand,
  and rest binding still creates the semantically required fresh frozen Array.
- Current-local create/read/assign paths, unqualified writable-target
  resolution, captured-lexical nearer-binding checks, and repeated-root lexical
  authority handoff now avoid forcing a guest Context while preserving
  `PRESENT(null) != ABSENT`, D179 remove/recreate behavior, capture by reference,
  late nearer creation/retargeting, receiver fallback, evaluation ordering, and
  one authoritative lexical store.
- Frame materialization is deferred until the execution context becomes
  guest-observable. Closure capture and debugger/reflection projection still
  force the required escape-safe materialization, while ordinary execution does
  not. Focused Java regressions, the execution-context conformance corpus,
  structural falsification of the Phase A/B/C call path, and the integrated
  `make test` gate pass with no observable Protos semantic change.

## 0.3.91-SNAPSHOT

- `I072` Phase B implements the PLAT040 compact ordinary-call frame-argument ABI
  for admitted source-backed guarded sends. The Phase A selector-specific
  stability guard, selected Closure, exact `methodHome`, and Context-owned
  `RootCallTarget` remain authoritative; valid guarded hits still avoid general
  D013 lookup/delegation traversal and generic selected-value classification.
- Ordinary source-backed target entry no longer requires a preconstructed rich
  `ProtosActivation`. The call crosses the Truffle boundary with compact frame
  arguments carrying the selected Closure, receiver, exact `methodHome`, caller
  provenance, return-home token, and supplied positional values. Captured lexical
  state remains owned by the Closure, and the existing rich activation,
  execution-context, and frozen guest argument Array are materialized only after
  target entry by the semantic Bytecode adapter where current semantics require
  them.
- Existing generic/native fallbacks and prepared control/suspension machinery
  remain intact. Receiver/`this`, method-home, positional/default/rest/spread
  argument behavior, fresh activation/execution-context identity, capture by
  reference, non-local return, Error/dynamic control, Task ownership,
  suspension/resume without replay, debugger/reflection projection, and
  multi-Context execution remain unchanged. Generated Bytecode DSL structure and
  the complete Maven `verify` gate validate the Phase B boundary with no
  observable Protos semantic change.

## 0.3.90-SNAPSHOT

- `I072` Phase A implements the PLAT040 guarded selected-send substrate for
  ordinary stable receiver chains. D013 selection is established once under a
  selector-specific Truffle `Assumption`; the valid monomorphic hit reuses the
  selected Closure, exact `methodHome`, and Context-owned `RootCallTarget`
  without repeating general lookup, delegation traversal, or selected-value
  classification.
- Slot replacement, removal, and nearer same-selector additions invalidate the
  exact dependent lookup assumptions, while unrelated selector/object mutation
  does not. Unsupported represented/subclass/context-backed chains retain the
  authoritative generic path. The existing activation, execution-context,
  argument, suspension/control, and generic fallback representation is preserved,
  with no observable Protos semantic change.

## 0.3.89-SNAPSHOT

- `I069` implements the PLAT038/PLAT039 Native Image runtime architecture as a
  first-class Maven `native` build path while retaining the Stage1 JVM path.
  The native profile uses GraalVM Build Tools to produce the `protos` executable
  and generates its Native Image class-initialization arguments as a dedicated
  build step rather than coupling native packaging to ordinary JVM builds.
- Native Image initialization is now deterministic and deliberately narrower
  than package-wide `--initialize-at-build-time`. The generated policy includes
  the language provider and generated Truffle library exports, both generated
  Bytecode DSL root structures, every generated helper and semantic operation
  node, and the narrowly required hosted constants
  `ProtosTextReaderCPrimeExecution.Advance` and
  `ProtosCoreErrors.StandardError`. The validated policy contains 311 unique
  build-time initialized classes.
- The PLAT039 PE-visible guest kernel was audited across bytecode execution,
  closure invocation, argument/default/rest binding, frame-backed lexical
  access, object/member lookup, module execution-plan caching, continuation and
  suspension handling, Future/Task/Actor state, parallel execution, Core value
  operations, standard protocols, debugger/interop projections, and guest
  callbacks. Guest-runtime paths that previously exposed opaque iterator,
  collection-view, stream, Optional, or structurally unsuitable access shapes
  were replaced with PE-visible indexed or explicit structural operations where
  required.
- Lexical and object-state helpers now provide explicit ordered projections for
  PE-sensitive traversal while preserving a single authoritative binding store,
  stable frame-backed local identity, `PRESENT(null) != ABSENT`, capture by
  reference, current/captured fallback semantics, and the PLAT036/I068
  frame-backed execution-context architecture. No second lexical authority or
  observable Protos semantic change is introduced.
- Native host/cold edges are isolated with narrow Truffle boundaries where the
  operation is genuinely outside the guest PE kernel, including selected host
  filesystem/network/runtime and cold materialization paths. Ordinary guest
  send/call/lookup, continuation decisions, guest callbacks, runtime state, and
  the main Bytecode DSL execution loops remain PE-visible; no broad
  enter/resume boundary is introduced.
- Standard Object `caseOf`, IdentityMap `at:ifAbsent:`, Map matching, and Array
  matching fast paths now use explicit marker `ProtosNativeClosureBody`
  implementations instead of identity-sensitive lambda singleton bodies. This
  preserves the existing protocol behavior while keeping those native closures
  structurally safe for Native Image hosted/runtime compilation.
- Runtime compilation support was validated on the produced Native Image rather
  than accepting an interpreter-only executable. The final image reports 2,132
  runtime-compiled methods, and forced compilation completes successfully for
  both `ProtosBytecodeRootNodeGen` and
  `ProtosSemanticBytecodeRootNodeGen` at Truffle Tier 2 with `OPT_FAILED=0` and
  no `FrameWithoutBoxing` materialization failure.
- Native `--version`, `--help`, and basic guest evaluation all pass, followed by
  the complete Maven `verify` gate. The investigation-only Native Image graph,
  blocklist, frontier, policy, and PE-audit Python programs used during I069 are
  not retained as product tooling; the durable native build inputs are
  `build/native/Dockerfile` and `build/native/generate-init-args.sh`.
- Native Image developer tooling is now isolated under `build/native/`.
  `make -C build/native build` builds the Stage1 Native Image executable, while
  `make -C build/native test` rebuilds it and runs the retained Native regression
  suite. The regression suite validates `--version`, `--help`, basic guest
  execution, and forced Truffle guest compilation for both generated Bytecode
  DSL roots at Tier 2, requiring `OPT_FAILED=0` and guarding against recurrence
  of the `FrameWithoutBoxing` materialization failure observed during I069.


## 0.3.88-SNAPSHOT

- `I071` / D179 Candidate C0 with implementation Candidate E restores
  execution-context `PRESENT -> ABSENT` removal on top of the PLAT036/I068
  frame-backed lexical architecture. `ProtosExecutionContextValue` again uses
  the ordinary open/closed/frozen structural rules instead of the superseded
  C3 monotonic-membership rejection.
- Statically admitted bindings keep their existing Bytecode DSL frame-local
  identity and ordinal. Removal uses the existing frame-local cleared state as
  semantic absence; later legal re-creation reuses the same frame-backed
  binding rather than introducing a sentinel, bitmap, dynamic-map migration,
  or second binding authority.
- `ReadFrameLocal` is now presence-aware: a cleared current local takes the
  exact lexical/receiver fallback path instead of reading an absent frame
  local. Captured frame-backed reads retain their existing presence/topology
  guards.
- Conformance coverage now exercises current-local removal revealing an outer
  lexical binding, receiver fallback after removal, captured/escaped removal,
  remove/recreate, PRESENT-null versus ABSENT, CLOSED/FROZEN rejection, and
  ordinary-object removal. Focused Java coverage additionally proves stable
  frame layout across clear/recreate, debugger/reflection hiding of cleared
  bindings, and that a captured assignment destination fixed before RHS
  evaluation is not retargeted if the RHS removes that destination.
- No PLAT036/I068 architecture is reopened, no broad Truffle boundary is added,
  and the change remains compatible with PLAT039's PE-visible guest-kernel
  boundary.

## 0.3.87-SNAPSHOT

- `I068` / PLAT036 Candidate D, Slice 7 ("activation lexical
  decomposition/fallback cleanup"): exact name-based lexical fallback is no
  longer an intrinsic responsibility of `ProtosActivation`.
  `ProtosLexicalFallback` now owns the residual bare-name read and
  bare-assignment destination-resolution paths that remain necessary when
  canonical binding analysis cannot prove a direct frame-backed binding or
  runtime presence/topology requires fallback. Statically proven current
  `Resolved` reads continue to lower through `ReadFrameLocal`, and proven
  `CapturedResolved` reads/writes continue through the captured frame-backed
  operations without regressing through generic name lookup. Candidate and
  Dynamic reads, compatibility paths for non-genuine execution contexts,
  captured late-presence/retargeting fallback, non-statically-proven bare
  assignment destination selection, debugger semantic reads, and receiver
  fallback retain their exact prior semantics. Bare assignment still selects
  its destination before RHS evaluation and never follows receiver
  delegation for writes. `capturedLexicalContexts` and
  `lexicalContextsForClosureCapture` remain on `ProtosActivation` as required
  activation/capture topology rather than obsolete lookup machinery.
  Existing activation-inspection tests now target the explicit lexical
  fallback boundary. No second lexical-binding authority, lazy execution
  context materialization, or observable language-semantic change is
  introduced. This completes the planned I068 Slice 1-7 implementation
  sequence for PLAT036 Candidate D.

## 0.3.86-SNAPSHOT

- `I068` / PLAT036 Candidate D, Slice 6 ("debugger/reflection projection"):
  debugger scope and Core reflection are now covered against the frame-backed
  lexical-state architecture established by Slices 2-5. The projection remains
  semantic rather than backend-native: PRESENT frame-backed bindings and
  dynamic-overflow bindings are visible, statically allocated but not yet
  established bindings remain hidden, and Bytecode DSL implementation
  temporaries are not exposed. Debugger reads continue to route through
  `ProtosActivation` lookup precedence, including live captured frame-backed
  bindings after outer mutation, while Core `slotNames` / `slotValue` observe
  the same single lexical-binding authority. The existing read-only tooling
  baseline, receiver fallback, object-body lexical boundary, and
  `NO_DUAL_BINDING_AUTHORITY` invariant remain unchanged. No production-code
  change was required; the slice closes the missing focused evidence over real
  frame-backed execution. Slice 7 retains the
  `ProtosActivation` lexical-decomposition/fallback cleanup.

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
