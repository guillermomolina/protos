# TOOL002 — Test Tool

Status: IN_PROGRESS

Nature: non-normative project implementation record

Architecture owners:

- `docs/design/TOOLCHAIN_TOOL_ARCHITECTURE.md`
- `docs/design/TEST_TOOL_ARCHITECTURE.md`
- `docs/design/TEST_TOOL_COMPARATIVE_AUDIT.md`
- `docs/design/TEST_TOOL_SCALE_AND_DISTRIBUTION_ARCHITECTURE.md`

Normative dependencies inspected by the architecture include:

- `spec/io/PROCESS_IO.md`
- `spec/concurrency/ACTORS.md`
- `spec/concurrency/PARALLEL_EXECUTION.md`
- `spec/semantics/MODULES.md`
- `spec/semantics/ERRORS.md`

Canonical summary:

- `docs/project/IMPLEMENTATION_STATUS.md`

## Purpose

TOOL002 implements the official toolchain-bundled `protos test` experience while
moving Protos-language test policy out of Java/JUnit and into ordinary bundled
Protos tooling wherever the language can express that policy naturally.

Java/JUnit remains appropriate for Java/runtime/host implementation behavior.
The intended validation order is Java implementation tests first, followed by
the Protos Test Tool for the Protos-language corpus.

## Selected architecture

The promoted architecture already closes these directions:

- the test tool is bundled and written primarily in Protos;
- no test syntax, compiler mode, privileged global Test object, or Java-owned
  assertion/suite/expectation policy is introduced;
- one fresh semantic Protos Process / RootActor is the normal isolation boundary
  per ordinary test case;
- outer test parallelism is bounded and independent from concurrency exercised
  inside each test;
- stdout/stderr are private per test and reporting is deterministic;
- capabilities are explicit and real shared resources may require tool-level
  serialization constraints;
- `Closure.parallel` is usable inside tests but is not the runner isolation
  mechanism;
- existing manifests/corpus are preserved during the initial migration.

The architecture also intentionally leaves exact CLI flags, future assertion
helper APIs, discovery policy, `jobs=auto`, resource-declaration syntax, and hard
OS-worker timeout/crash policy open. Implementation slices must not silently
freeze those decisions unless their own audited scope requires and resolves them.

## Initial tracked slices

| Slice | Status | Outcome |
|---|---|---|
| TOOL002-A | CLOSED | Exact bundled `protos test` dispatch and tiny ordinary-Protos entry published in the same commit at implementation version `0.2.168-SNAPSHOT`; no corpus migration or test policy. |
| TOOL002-B | CLOSED | Publish the local, test-neutral `ProtosFreshProcessExecutor` over `ProtosStandaloneProcessBootstrap`, shared RootActor cooperative terminal dispatch through `ProtosRootTaskExecution`, and inert `ProtosExecutionOutcome`; every invocation uses a fresh semantic Process and terminates it before returning. No TestPlan/scheduler/worker/remote/test policy. Implementation version `0.2.169-SNAPSHOT`. |
| TOOL002-C | CLOSED | Publish test-neutral sequential private-stream capture over `ProtosFreshProcessExecutor`: one exact compiled entry gets private stdin/stdout/stderr, a fresh semantic Process and an inert outcome plus detached captured bytes. No manifest/expectation/scheduler/result-transfer policy. Implementation version `0.2.171-SNAPSHOT`. |
| TOOL002-D | CLOSED | D1-D4 are published; all retained non-Future main-manifest expectation policy is owned by bundled Protos. D4 closes at `0.2.211-SNAPSHOT`; `future-*` remains TOOL002-F. |
| TOOL002-E | CLOSED | E1A/E1B/E2A/E2B/E3/E4 complete the retained Package/TOML migration: bundled Protos owns planning, confined source loading, Package execution, Boolean/Error expectation policy and aggregation; duplicate Java corpus-policy ownership is removed. |
| TOOL002-F | IN_PROGRESS | F1/F2 and F3A are CLOSED; F3B is IN_PROGRESS with F3B1 retained-fixture shape CLOSED and F3B2 one-observation evidence READY. F3C/D/E and F4 remain dependency-gated. |
| TOOL002-G | BLOCKED_BY_DEPENDENCIES | After F, migrate Actor/Group scheduler-sensitive language coverage without a test-only concurrency model. |
| TOOL002-H | BLOCKED_BY_DEPENDENCIES | After G, add bounded parallel scheduling of independent fresh Processes, private output capture, and deterministic reporting. |
| TOOL002-I | BLOCKED_BY_DEPENDENCIES | After H, add explicit resource constraints/private capabilities where real external-resource sharing requires them. |
| TOOL002-J | BLOCKED_BY_DEPENDENCIES | After I, integrate the final Java-first / Protos-tool-second validation pipeline. |

## TOOL002-E decomposition

The Package Tool fixture migration is split at the actual ownership boundaries so
planning, capability provisioning, Package Tool module execution, expectation
execution, Java-runner retirement and final closure remain independently
reviewable and publishable.

| Slice | Status | Outcome |
|---|---|---|
| TOOL002-E1A | CLOSED | Bundled `Manifest.protos` accepts the retained two-column Package/TOML manifest as planning input, validates safe relative paths, assigns stable `package-tool/toml-syntax/...` CaseIds, and normalizes retained `true`/`error` rows into the already-owned canonical `boolean true` / generic `error` CaseSpec representation. The loader is parser-parameterized without changing the existing three-column conformance manifest contract. No Package Tool fixture executes in E1A. Implementation version `0.2.216-SNAPSHOT`. |
| TOOL002-E1B | CLOSED | Provision two independent bootstrap-local read-only tree-confined authorities: existing `filesystem` remains rooted at `protos/tests/conformance`, while `packageTomlFilesystem` is rooted exactly at `protos/tests/package-tool/toml-syntax`. `Main.protos` constructs the E1A Package/TOML plan through the second capability but does not execute it. Implementation version `0.2.218-SNAPSHOT`. |
| TOOL002-E2A | CLOSED | E2A1 proves exact bundled Package resolver execution with fresh-Process normal completion; E2A2A/E2A2B prove safe cross-Prelude failed/Error observation and one retained real failed Package/TOML fixture. E2A adds no Test-specific resolver, expectation policy, child Filesystem authority or Java-runner cutover. |
| TOOL002-E2A1 | CLOSED | Generalize the existing exact-source facility to an explicit already-selected Prelude and named bootstrap slot; provision `packageExecution` with the existing bundled Package Tool resolver; one real TOML fixture resolves `self:TomlSyntax` and completes as canonical `true` in a fresh Process. No plan execution policy. Implementation version `0.2.219-SNAPSHOT`. |
| TOOL002-E2A2 | CLOSED | E2A2A rematerializes the closed standard Error taxonomy across distinct Preludes without sharing source-domain prototypes; E2A2B proves the mechanism through retained `invalid-key-error.protos`. E2A2C reconciles ownership/confinement and closes the parent. |
| TOOL002-E2A2A | CLOSED | General source/destination Prelude-aware detached standard Error taxonomy rematerialization. No `packageExecution` wiring or Package fixture execution. Implementation version `0.2.220-SNAPSHOT`. |
| TOOL002-E2A2B | CLOSED | `packageExecution` supplies its selected Package Prelude to the E2A2A snapshot boundary; one retained `invalid-key-error.protos` fixture proves a fresh failed Error is rematerialized under the Test Tool `Error` prototype. Implementation version `0.2.222-SNAPSHOT`. |
| TOOL002-E2A2C | CLOSED | Documentation/governance-only cross-slice reconciliation confirms the E2A execution boundary: selected existing Package resolver, one fresh semantic Process per execution, private capture, no child Filesystem authority, caller-domain detached Error rematerialization, and no expectation/TestPlan policy. No implementation-version change. |
| TOOL002-E2B | CLOSED | E2B1 canonical generic-Error normalization plus E2B2 complete retained Package/TOML plan execution move Boolean/Error expectation interpretation and full-corpus aggregation into bundled Protos. Implementation complete through `0.2.226-SNAPSHOT`. |
| TOOL002-E2B1 | CLOSED | Normalize retained Package/TOML `error` rows to the already-owned D3A2 canonical generic-error CaseSpec form with `expected == "-"`; no fixture execution or runner-policy change. Implementation version `0.2.224-SNAPSHOT`. |
| TOOL002-E2B2 | CLOSED | `Main.protos` executes `packageTomlPlan` through the existing generic Runner with E1B caller-side source authority and E2A Package execution; one Protos full-corpus fixture proves non-empty plan, selected==cases, skipped==0, passed==cases and complete ordered result cardinality. Implementation version `0.2.226-SNAPSHOT`. |
| TOOL002-E3 | CLOSED | Remove the legacy `ProtosPackageToolTomlSyntaxConformanceTest`: Java no longer parses the retained TOML manifest, interprets `true`/`error`, or directly executes corpus fixtures. E1A/E1B/E2A/E2B Protos ownership remains the sole corpus-policy path; Java host-mechanism tests remain. Test-impact only; no implementation-version change. |
| TOOL002-E4 | CLOSED | Documentation/governance-only final reconciliation confirms the E migration boundary and closes parent TOOL002-E: one bundled-Protos corpus-policy path, separate caller-side confined authority, existing Package resolver/fresh Process execution, no duplicate Java TOML owner, no normative change and no implementation-version change. TOOL002-F is READY. |

E1A deliberately grants no new Filesystem authority and invokes no Package Tool
module. Its only executable change is inert planning policy in the bundled Test
Tool plus a Java provisioning harness whose assertions live in Protos source.

## TOOL002-E1B closure

E1B provisions the second corpus capability without merging authority domains:

- existing `filesystem` remains a read-only tree capability rooted exactly at
  `protos/tests/conformance`;
- new bootstrap-local `packageTomlFilesystem` is a separate read-only tree
  capability rooted exactly at `protos/tests/package-tool/toml-syntax`;
- imported `Manifest.protos` receives neither capability implicitly; `Main.protos`
  passes each capability explicitly to the applicable loader;
- `Main.protos` now constructs `packageTomlPlan` from the E1A loader, but only the
  original conformance plan is passed to `Runner.runSimple`;
- the existing tree-confined backend and standard Filesystem bridge are reused;
  no broader root, ambient path authority, new public Filesystem constructor or
  Package-specific Filesystem primitive is introduced.

A Protos-owned fixture loads both real manifests through the two independent
capabilities and checks their distinct stable CaseIds/planning fields. The Java
harness only provisions those host capabilities. No Package Tool module or TOML
fixture is executed in E1B, so Package Tool `self:*` resolution remains exactly
the next mechanical boundary owned by TOOL002-E2A.

TOOL002-E2A is CLOSED; TOOL002-E2B is READY.

The hard-timeout / amortized OS-worker audit remains deferred. It is not part of
TOOL002-A and is not silently made a blocker for the useful initial Test Tool.
If guaranteed recovery from non-preemptible infinite tests becomes a product
requirement, promote that question through an explicit later design/work item.

## TOOL002-E2A decomposition

The E1B -> E2A audit exposed two independent host-mechanical questions. Selecting
the Package Tool resolver and running a normal fixture in a fresh Process does not
itself prove that a failed child Error can be detached safely when the child uses
a different Core Prelude from the Test Tool caller. Keeping those concerns in one
patch would hide the exact boundary that E2B's generic `error` policy depends on.

E2A is therefore split:

```text
E2A1  exact bundled Package resolver + fresh-Process normal execution
E2A2  safe detached failed/Error observation across distinct Preludes
```

### TOOL002-E2A1 closure

E2A1 reuses rather than duplicates the existing execution stack:

- `ProtosExactExecutionFacility` now supports an explicit bootstrap-local slot
  name plus an already-selected `ProtosPrelude`; the original
  `install(activation)` / `execution` behavior is preserved;
- the selected execution still delegates to `ProtosCapturedProcessExecution` and
  `ProtosFreshProcessExecutor`, so each call receives a fresh semantic Process /
  RootActor, private streams, empty args/environment and no default Filesystem;
- `protos test` constructs the Package Prelude with the exact existing
  `ProtosBundledToolModuleResolver("package", protos/tools/package, standard)`
  boundary and exposes it only through bootstrap-local `packageExecution`;
- a Protos-owned fixture loads the first real TOML source through E1B authority
  and proves its `self:TomlSyntax` import resolves and completes as canonical
  `true`.

E2A1 does not modify `Runner.protos`, execute the Package/TOML TestPlan, interpret
a corpus expectation, grant child filesystem authority, or remove the legacy
JUnit runner.

The audit also found that D1's detached observation historically assumed child
and caller executions shared the same Prelude when rematerializing standard
object ancestry. Canonical scalar normal results already cross safely, which is
enough to close E2A1.

### TOOL002-E2A2 decomposition

E2A2 is split again so the general value-boundary repair, real Package integration
proof, and parent closure are independently attributable:

```text
E2A2A  source/destination Prelude-aware standard Error taxonomy rematerialization
E2A2B  one real Package/TOML failed fixture through packageExecution
E2A2C  focused ownership/confinement validation + E2A closure
```

E2A2A changes only the detached-value mechanism. The source Prelude identifies
the closed standard Error taxonomy; matching source prototypes rematerialize to
their destination counterparts before ordinary graph copying. The source Prelude
is not exposed and grants no authority.

### TOOL002-E2A2B closure

E2A2B connects that mechanism to the existing E2A1 execution path. The selected
Package Prelude is supplied only as source-domain metadata to detached observation.
One Protos fixture reads retained `invalid-key-error.protos` through the E1B-confined
corpus capability and proves a failed observation with null value, fresh Error
identity, immediate destination `Error` parent and empty private output.

No Package/TOML TestPlan traversal or expectation-policy cutover occurs.

### TOOL002-E2A2C closure

E2A2C adds no executable behavior. Its focused cross-slice reconciliation closes
the host-mechanical Package execution environment established by E2A:

- E2A1 selects the already-existing bundled Package Tool resolver and executes
  exact source through the existing captured fresh-Process path; the child gets
  empty args/environment, private streams and no default Filesystem authority;
- E2A2A keeps detached observation test-neutral and rematerializes only the
  closed standard Error taxonomy from an explicitly identified source Prelude to
  the corresponding destination Prelude; source prototypes and executable or
  authority-bearing state are not shared;
- E2A2B supplies the selected Package Prelude as source-domain metadata and proves
  one retained real failing TOML fixture arrives as a fresh Test Tool-domain
  Error under the Test Tool's own `Error` prototype;
- E1B corpus authority remains a separate bootstrap-local read-only Filesystem
  rooted exactly at the Package/TOML corpus and is used by the Test Tool caller
  to read source; it is not delegated into the fresh child Process.

Together, the normal and failed paths establish the complete E2A execution
environment needed by E2B without owning any Package/TOML expectation or
TestPlan traversal policy. `Runner.protos` remains unchanged by E2A, and the
legacy Java corpus runner remains intentionally present until E3.

TOOL002-E2A2 is CLOSED.
TOOL002-E2A is CLOSED.

### TOOL002-E2B decomposition

The first composition audit of the E1A Package/TOML plan against the D-owned
runner exposed one independently correctable planning mismatch. D3A2's retained
generic Error CaseSpec uses `expectation == "error"` with `expected == "-"`;
E1A had selected the `error` family but left the expected payload empty because
that planning-only slice never executed a fixture.

E2B is therefore split at that dependency:

```text
E2B1  canonical Package/TOML generic-Error CaseSpec normalization
E2B2  complete Package/TOML plan execution + full-corpus Protos evidence
```

E2B1 changes no expectation semantics. It makes the Package/TOML adapter emit
the canonical representation already required by `Runner.evaluateSimple` and
documented by the Test Tool architecture. E2B2 remains solely responsible for
execution, aggregation evidence and E2B closure.

### TOOL002-E2B2 closure

E2B2 composes the already-closed planning, authority, execution and expectation
boundaries without adding another Test Tool policy layer:

- `Main.protos` passes the retained Package/TOML TestPlan to the same generic
  `Runner.runSimple` used for the conformance plan;
- source loading remains caller-side through the separate E1B read-only
  `packageTomlFilesystem`;
- execution remains E2A `packageExecution`, so each selected fixture runs in one
  fresh semantic Process / RootActor with the bundled Package Tool resolver;
- expectation interpretation remains exactly the D-owned `boolean` and generic
  `error` policy already implemented by `Runner.evaluateSimple`;
- a Protos-owned full-corpus fixture requires every current manifest CaseSpec to
  be selected, no case to be skipped, every selected case to pass, and one
  ordered result per planned case.

E2B2 deliberately leaves `ProtosPackageToolTomlSyntaxConformanceTest` in place.
The corpus is now redundantly validated by the new Protos owner and the legacy
Java owner so E3 can remove Java ownership as an independently attributable
cutover rather than mixing retirement with first proof of Protos ownership.

TOOL002-E2B is CLOSED.
TOOL002-E2B1 is CLOSED.
TOOL002-E2B2 is CLOSED.

### TOOL002-E3 closure

E3 removes the final Java owner of retained Package/TOML corpus policy.
`ProtosPackageToolTomlSyntaxConformanceTest` previously performed all three
policy-bearing operations independently in JUnit: it parsed `manifest.tsv`,
interpreted the retained `true` / `error` expectation column, and compiled /
executed every fixture directly with the bundled Package Tool resolver.

That duplicate owner is now deleted because E1A through E2B already provide the
same corpus path under bundled Protos ownership:

- `Manifest.protos` parses the retained manifest and constructs inert CaseSpecs;
- `packageTomlFilesystem` supplies the separate confined caller-side source
  authority;
- `packageExecution` supplies the already-selected bundled Package Prelude and
  fresh-Process execution mechanism;
- `Runner.protos` owns Boolean/generic-Error expectation interpretation and
  aggregation;
- the E2B2 Protos fixture requires complete current-corpus selection, zero
  skipped cases and all selected cases passing.

Java tests that remain around this path validate only host-side provisioning,
resolver/bootstrap mechanics, confinement and detached-value implementation
boundaries. They do not parse the Package/TOML manifest or interpret its test
expectations.

TOOL002-E3 is CLOSED.

### TOOL002-E4 closure

E4 adds no executable behavior. The final cross-slice audit reconciles the
published E1A/E1B/E2A/E2B/E3 evidence into one ownership boundary:

- retained Package/TOML manifest parsing and CaseSpec construction are ordinary
  bundled-Protos policy in `Manifest.protos`;
- the TOML corpus is read only through the separate E1B caller-side
  `packageTomlFilesystem`, rooted exactly at the retained corpus;
- each selected fixture executes through E2A `packageExecution` with the
  already-existing bundled Package Tool resolver and one fresh semantic Process /
  RootActor with private output capture;
- generic Boolean/Error expectation interpretation and ordered aggregation remain
  the existing D-owned policy in `Runner.protos`;
- E2B2 proves the complete retained TOML plan is non-empty, fully selected,
  unskipped and passing through that Protos path;
- E3 removes the only Java test that independently parsed the TOML manifest,
  interpreted its expectation column and directly executed every fixture;
- remaining Java tests on this surface are host-mechanical checks only and do
  not own TestPlan or expectation semantics.

No second Package resolver, Test-specific module system, child Filesystem
authority, Java Test abstraction, new expectation family, normative specification
change or implementation-version change is introduced by E4.

TOOL002-E4 is CLOSED.
TOOL002-E is CLOSED.

## TOOL002-F decomposition

The fresh-Process/detached-value audit establishes a hard ownership boundary for
the deferred `future-*` families: `ProtosDetachedExecutionValue` deliberately
rejects live Future values, while `ProtosTask.complete` already keeps a root task
non-terminal until its structured children drain. TOOL002-F therefore must not
make Future transferable or add a Java progress loop. Future expectation policy
stays inside the same fresh child Process and observes production Future behavior
through ordinary `Future.value()` / Error semantics, returning only detached
Boolean evidence to the Test Tool Process.

F is split by the distinct terminal/identity contracts:

| Slice | Status | Outcome |
|---|---|---|
| TOOL002-F1 | CLOSED | Child-local mechanism for `future-integer`, `future-null`, and `future-boolean`. The retained source produces the candidate Future in the fresh child Process; `Future.value()` performs ordinary suspension/resume there; only canonical Boolean evidence crosses D1. Not yet activated in `runSimple`. Implementation version `0.2.228-SNAPSHOT`. |
| TOOL002-F2 | CLOSED | Child-local `future-error`, `future-error-parent`, and `future-cancelled` mechanism uses repeated ordinary `Future.value()` observations: FAILED preserves stored Error identity; CANCELLED yields fresh `Cancelled` occurrences; exact immediate parent matching remains ordinary reflection. Not yet activated in `runSimple`. Implementation version `0.2.229-SNAPSHOT`. |
| TOOL002-F3 | IN_PROGRESS | F3A is CLOSED. F3B is IN_PROGRESS: B1 exact retained local-slot/observe-arity shape is CLOSED, B2 one post-terminal observation is READY, and B3 remains dependency-gated. |
| TOOL002-F3A | CLOSED | Semantic prerequisites plus governance reconciliation complete: F3A1 proves repeated failed-Future observations signal one Error identity; F3A2 proves one failed-Future observation is exactly the producer-created Error; F3A3 records the dependency-ordered continuation. No Test Tool executable change. |
| TOOL002-F3A1 | CLOSED | Test-impact-only Protos conformance published at `3c23eaaccecbdcc7c2bcd86bc30c445403cfb047`: two handled observations of one failed Future satisfy `first === second`. |
| TOOL002-F3A2 | CLOSED | Test-impact-only Protos conformance published at `119c90032088ccf428ace37185187b854966e0cb`: one handled observation of a failed Future satisfies `observed === original` for the producer-created same-domain Error. |
| TOOL002-F3A3 | CLOSED | Documentation/governance-only reconciliation of F3A1/F3A2 and the finer F3 continuation. No executable, normative, manifest, implementation-version or license-term change. |
| TOOL002-F3B | IN_PROGRESS | B1 proves the exact retained object/Closure shape without terminal progress or observation; B2 is READY for exactly one post-terminal `observe()` identity check. |
| TOOL002-F3B1 | CLOSED | Test-impact-only direct execution of the exact retained stored fixture proves its result has exactly local `future`, `error`, `observe` slots; their host representations are Future/Object/Closure respectively; `observe` is source-backed and has zero parameters. No Future progress or observation occurs in this slice. |
| TOOL002-F3B2 | READY | F3B1 CLOSED; progress the retained `future` to terminal without observing it, invoke retained zero-argument `observe` exactly once, and require the signalled Error to be the exact retained local `error`. |
| TOOL002-F3B3 | BLOCKED_BY_DEPENDENCIES | After B2, establish the second post-terminal observation and `first === second === error`, closing retained stored-fixture shape evidence. |
| TOOL002-F3C | BLOCKED_BY_DEPENDENCIES | After F3B, migrate retained `stored:Error` into the bundled Test Tool in independently reviewable mechanism/policy slices. |
| TOOL002-F3C1 | BLOCKED_BY_DEPENDENCIES | After B3, implement only the Test Tool mechanism needed for the retained `stored:Error` positive case. Do not add `fresh` or stored negative-policy cases. |
| TOOL002-F3C2 | BLOCKED_BY_DEPENDENCIES | After C1, add only stored-mode malformed/mismatch/parent negative evidence and close F3C. |
| TOOL002-F3D | BLOCKED_BY_DEPENDENCIES | After F3C, migrate retained `fresh:Cancelled` independently. |
| TOOL002-F3D1 | BLOCKED_BY_DEPENDENCIES | Establish one retained cancelled observation with exact immediate `Cancelled` parent. |
| TOOL002-F3D2 | BLOCKED_BY_DEPENDENCIES | Establish the second cancelled observation with `first !== second` and close fresh-mode evidence. |
| TOOL002-F3E | BLOCKED_BY_DEPENDENCIES | After F3D, integrate stored/fresh parsing/evaluation and negative cases, close F3, and make F4 READY without activating `future-*` in public `runSimple`. |
| TOOL002-F4 | BLOCKED_BY_DEPENDENCIES | After F3, activate every retained `future-*` family in the generic sequential runner, require full main-manifest selection/pass evidence, retire Java `future-*` policy ownership, close TOOL002-F and make TOOL002-G READY. |

F1 deliberately leaves `isDExpectation` / `runSimple` unchanged, so D4's
already-published non-Future ownership boundary remains valid until the atomic F4
activation/cutover. Java remains the temporary direct Future owner during F1-F3.

### TOOL002-F2 closure

F2 observes terminal category through ordinary public Future behavior rather than exposing a Test-only state selector. Core requires a failed Future to retain and re-signal its exact stored domain-local Error object, whereas a cancelled Future stores no Error and creates one fresh `Cancelled` occurrence for each `value()` observation. Two handled observations therefore distinguish FAILED from CANCELLED semantically and portably.

`future-error-parent` composes that FAILED identity rule with the already-owned `standardErrorPrototype` immediate-parent policy. A failed Future whose stored Error happens to delegate to `Cancelled` remains FAILED because its repeated observations share identity; cancellation requires fresh identities. No runtime state, task handle, scheduler queue or Java Future object is exposed to the Test Tool.

Like F1, F2 is mechanism-only. `isDExpectation` and public `runSimple` remain unchanged so the Java harness continues to own all retained `future-*` rows until F4 performs one atomic selection/ownership cutover.

### TOOL002-F3A reconciliation

The failed monolithic F3 attempts combined several independent questions and
made a single `stored` failure difficult to attribute. The migration is now
dependency-ordered at the smallest useful semantic and mechanism boundaries.

F3A establishes only the already-normative same-domain failed-Future Error
identity substrate, entirely outside Test Tool implementation:

- F3A1 (`3c23eaaccecbdcc7c2bcd86bc30c445403cfb047`) proves two handled
  `Future.value()` observations of one failed Future re-signal one Error
  identity (`first === second`);
- F3A2 (`119c90032088ccf428ace37185187b854966e0cb`) proves a handled
  observation is the exact producer-created Error object (`observed ===
  original`);
- both fixtures are ordinary Protos and use the temporary retained
  `future-boolean` Java owner only to wait for/check the outer Boolean Future;
  Java does not inspect the inner Error identity.

Therefore later `stored:Error` failures are not evidence of a missing base
Future/Error identity rule. F3B next isolates the retained fixture object and
captured `observe` Closure shape before F3C is allowed to modify Test Tool
mechanism. Fresh cancellation identity remains independently deferred to F3D.
No executor or Runner experiment from the failed monolithic F3 attempts is
carried forward merely by this reconciliation.

TOOL002-F is IN_PROGRESS.
TOOL002-F1 is CLOSED.
TOOL002-F2 is CLOSED.
TOOL002-F3 is IN_PROGRESS.
TOOL002-F3A is CLOSED.
TOOL002-F3A1 is CLOSED.
TOOL002-F3A2 is CLOSED.
TOOL002-F3A3 is CLOSED.
### TOOL002-F3B1 closure

F3B1 validates only the static runtime shape of the exact retained
`future/failed-value-resignals-recorded-error.protos` result. The test executes
that retained file directly in its ordinary module activation and performs no
scheduler progress, `Future.value()` call or `observe()` invocation.

Because the returned object deliberately contains live Future and Closure values,
it cannot be detached into a second Protos Process merely to inspect its shape.
The narrow Java focal therefore checks host representation only: the result has
exactly three local slots named `future`, `error`, and `observe`; those values
are respectively a Future, ordinary object/Error value and source-backed Closure;
and the retained `observe` Closure declares zero parameters. It owns no
`stored`/`fresh` expectation semantics.

This is the deliberate exception to the normal Protos-source-test preference:
the behavior under test is the exact live fixture boundary that cannot cross the
detached execution boundary. F3B2 remains responsible for terminal progress and
the first actual observation.

TOOL002-F3B is IN_PROGRESS.
TOOL002-F3B1 is CLOSED.
TOOL002-F3B2 is READY.
TOOL002-F3B3 is BLOCKED_BY_DEPENDENCIES.
TOOL002-F3C is BLOCKED_BY_DEPENDENCIES.
TOOL002-F3C1 is BLOCKED_BY_DEPENDENCIES.
TOOL002-F3C2 is BLOCKED_BY_DEPENDENCIES.
TOOL002-F3D is BLOCKED_BY_DEPENDENCIES.
TOOL002-F3D1 is BLOCKED_BY_DEPENDENCIES.
TOOL002-F3D2 is BLOCKED_BY_DEPENDENCIES.
TOOL002-F3E is BLOCKED_BY_DEPENDENCIES.
TOOL002-F4 is BLOCKED_BY_DEPENDENCIES.

## TOOL002-A closure

TOOL002-A closes the deliberately narrow bundled-tool bootstrap boundary:

```text
protos test
    -> public Protos driver performs only exact bundled-tool selection/bootstrap
    -> protos/tools/test/Main.protos
    -> ordinary Protos execution
```

The published slice reuses the existing generic bundled-tool resolver and factors
the package/test entry execution through one small common CLI bootstrap helper.
Package retains its separately provisioned confined Filesystem authority; the
test tool receives no Filesystem authority in TOOL002-A. No separate `CLIxxx`
item is created because no independently meaningful public driver mechanism was
needed beyond this TOOL002 bootstrap.

TOOL002-A adds no test discovery, assertion, manifest, Process-per-test, timeout,
parallelism, filter, reporter, or corpus-migration policy.

## Post-A comparative architecture checkpoint — CLOSED

`docs/design/TEST_TOOL_COMPARATIVE_AUDIT.md` completes the required checkpoint
after TOOL002-A. The comparison covers framework-driven, language-native,
process-worker, native-isolation and hermetic/distributed systems.

The checkpoint retains fresh semantic Process / RootActor isolation and adds two
important refinements before runner implementation hardens:

- manifests and future discovery feed stable CaseSpec/CaseId values and one inert
  TestPlan before physical scheduling;
- future parallel scheduling uses general capacity accounting, with capacity-1
  named resources naturally providing mutex/group behavior.

It also confirms that arbitrary hard timeout requires a separately owned physical
worker boundary, that retries must retain flaky evidence, and that result caching
should wait for an explicit hermetic input/capability model.

TOOL002-B is therefore READY. B remains deliberately mechanical: exact entry,
arguments, explicit capabilities, private streams, fresh Process/RootActor,
production execution and inert outcome. TestPlan parsing, assertions, manifests,
fixtures, resources, retry, timeout, sharding, watch, cache and reporting remain
outside B.

## Scale/distribution architecture checkpoint — SELECTED

`docs/design/TEST_TOOL_SCALE_AND_DISTRIBUTION_ARCHITECTURE.md` records the
future-scale architecture after the expanded comparative checkpoint.

The selected long-term boundary is:

```text
stable logical case attempt
        -> replaceable physical execution backend
        -> fresh semantic Protos Process / RootActor
        -> structured semantic + infrastructure evidence
```

The initial implementation remains intentionally smaller. TOOL002-B implements
only the local, test-neutral exact-entry fresh-Process mechanism. Later
OS-worker/remote backends, Run/Case/Variant/Attempt policy, capacity/resource
scheduling, sharding, affected analysis, caching and artifact infrastructure are
not silently pulled into B.

The checkpoint also records that remote/distributed execution cannot assume
exactly-once physical execution and that resource constraints eventually need
locality/scope as well as capacity. These are architecture constraints for future
layers, not new Core semantics.

## TOOL002-B closure

TOOL002-B closes the general local execution prerequisite needed by the Test
Tool without creating a test-specific host institution.

Published implementation:

- `ProtosFreshProcessExecutor` creates one fresh semantic Process/RootActor per
  invocation using the already-established standalone Process bootstrap;
- the request receives an already-selected Prelude/module-resolution environment,
  exact compiled entry, argument/environment snapshots, stream backends/Encodings
  and optional default Filesystem authority;
- `ProtosRootTaskExecution` owns the cooperative RootActor task dispatch required
  for real Future suspension/resume;
- `ProtosExecutionOutcome` carries only terminal COMPLETED/FAILED/CANCELLED data;
- Protos semantic failure is returned as FAILED outcome data rather than being
  reclassified as host/infrastructure failure;
- Process termination is requested before the executor returns;
- CLI standalone execution reuses RootActor terminal dispatch and preserves its
  existing CLI-specific error translation.

B adds no discovery, TestPlan, CaseId, expectation, assertion, fixture, scheduler,
resource, timeout, OS-worker, remote-backend, retry, cache or reporting policy.

TOOL002-C is therefore READY.

## TOOL002-C closure

TOOL002-C publishes the local sequential captured-execution layer needed before
manifest migration:

- `ProtosCapturedProcessExecution` supplies private byte-backed stdin plus
  independent stdout/stderr capture;
- it delegates semantic execution and Process lifecycle entirely to the closed
  TOOL002-B fresh-Process mechanism;
- capture buffers are detached defensively from request/result callers;
- a real `.protos` tooling fixture proves one exact case runs with independent
  stdout/stderr and a normal semantic result;
- a failure-path focal proves output committed before a Protos Error remains in
  that case's private capture;
- no TestPlan, expectation, assertion, CaseId, scheduler, retry, worker, remote,
  cache or reporter policy is added.

The C outcome remains host-inert. It does not leak arbitrary live child-Process
objects into the bundled Test Tool Process. TOOL002-D must audit the safe
Protos-side result-consumption boundary as part of migrating expectation policy.

TOOL002-D is therefore READY.

## TOOL002-D decomposition

The C -> D boundary audit found that the existing general manifest mixes simple
value/Error expectations with Future and callable/identity-sensitive cases.
Migrating all of that together would combine filesystem authority, TestPlan
representation, cross-Process value safety and several independent expectation
policies in one oversized change.

TOOL002-D therefore uses these publishable sub-slices:

| Slice | Status | Outcome |
|---|---|---|
| TOOL002-D1 | CLOSED | Bootstrap-local general `execution(source)` facility for the Test Tool over TOOL002-C, returning a caller-local observation through a strict authority-free detached-value boundary. No manifest/test policy. Implementation version `0.2.174-SNAPSHOT`. |
| TOOL002-D2 | CLOSED | Grant the Test Tool one read-only tree-confined standard Filesystem rooted at the conformance corpus; bundled `Manifest.protos` uses bounded ordered readLine/Future.all windows to parse retained TSV rows into frozen CaseSpec/TestPlan tuples with named Protos accessors and validated path-based stable CaseIds. No case execution/expectation policy. Implementation version `0.2.182-SNAPSHOT`. |
| TOOL002-D3 | CLOSED | D3A, D3B and D3C are CLOSED; ordinary non-Future expectation migration is complete through D3C2C at `0.2.209-SNAPSHOT`. |
| TOOL002-D4 | CLOSED | `closure-error-parent-fresh` stays entirely inside one fresh child Process for two exact Closure invocations and fresh Error identity checking; Java direct conformance ownership is reduced to `future-*`. Implementation version `0.2.211-SNAPSHOT`. |

The `future-*` families (`future-integer`, `future-null`, `future-boolean`,
`future-error`, `future-error-parent`, `future-observation-error-identity`,
`future-cancelled`) remain deliberately covered by the later TOOL002-F slice.
This keeps the already-selected async/Future migration boundary meaningful.

### TOOL002-D1 closure

D1 publishes only general mechanism:

- `ProtosExactExecutionFacility` installs `execution` only in the explicitly
  granted initial tool module context;
- each call runs one exact source String through TOOL002-C with a fresh Process,
  private streams, empty args/environment and no default Filesystem;
- the returned frozen observation is caller-local and contains state, detached
  value/error data, and frozen captured stdout/stderr Bytes;
- `ProtosDetachedExecutionValue` never rematerializes capabilities and fails
  closed with `NonTransferableValue` for authority/execution values;
- frozen standard prelude objects may be shared, while copied identity-bearing
  data receives fresh destination identity;
- a Protos fixture owns the observable success/failure checks; Java tests cover
  the host boundary and fail-closed authority rule.

TOOL002-D2 is READY.

### TOOL002-D2 closure

D2 closes the corpus/planning prerequisite:

- the Test Tool gets a standard `filesystem` capability rooted only at
  `protos/tests/conformance`;
- a new general read-only tree backend performs secure relative traversal and
  nested opens without symlink following or write/mutation authority;
- bundled `Manifest.protos` owns manifest syntax/policy and canonical path
  validation;
- each retained row becomes a frozen internal CaseSpec tuple with stable `caseId == path`, consumed through named bundled-Protos accessors;
- the plan and its case Array are frozen inert Protos data;
- `Main.protos` constructs the plan on ordinary `protos test` startup;
- a Protos fixture verifies the first CaseSpec and reads its nested source
  through the standard Filesystem authority;
- no test case is executed and no expectation is interpreted yet.

TOOL002-D3 is READY.

### TOOL002-D3 decomposition

The post-D2 audit found three independent uncertainties inside the original
ordinary-expectation migration: complete source acquisition, single-case
expectation policy, and whole-plan sequential traversal. D3 is therefore
implemented as smaller publishable slices before the fixed/error-parent and
Float families:

| Slice | Status | Outcome |
|---|---|---|
| TOOL002-D3A1 | CLOSED | Bundled `Runner.readSource(spec, filesystem)` loads one complete UTF-8 case source through the D2 confined standard Filesystem/File surface. Ordered File reads are issued in bounded 16-read windows, exact bytes are accumulated before one UTF-8 decode, and the File is explicitly closed. No case execution or expectation policy. Implementation version `0.2.186-SNAPSHOT`. |
| TOOL002-D3A2 | CLOSED | `Runner.evaluateSimple(spec, source, executor)` interprets `boolean`, `null`, `integer`, and generic `error` entirely in bundled Protos. Normal mismatches return frozen `passed + observation` evidence; malformed/unsupported policy signals. Integer matching uses signed-decimal Protos parsing plus primitive `===` to preserve exact numeric family. Implementation version `0.2.189-SNAPSHOT`. |
| TOOL002-D3A3 | CLOSED | Compose TestPlan + source loader + simple evaluator through an ordered `Future.then` dependency chain built without suspending inside `Array.each`; skip unsupported kinds before source access, aggregate ordered frozen CaseRun evidence with balanced chunks, and integrate the supported subset into `Main.protos`. No reporting/parallel/exit-status policy. Implementation version `0.2.192-SNAPSHOT`. |
| TOOL002-D3B | CLOSED | D3B1 fixed-integer and D3B2A/B error-parent prerequisite/policy are published. The sequential runner owns both retained families without changing D1/D3A evidence boundaries. Implementation version `0.2.199-SNAPSHOT`. |
| TOOL002-D3C | CLOSED | D3C1 float-nan and D3C2 exact float-bits are published; final D3C2C sequential integration closes D3C at `0.2.209-SNAPSHOT`. |

D3A1 deliberately does not modify `Main.protos`: ordinary `protos test`
continues to construct the inert D2 TestPlan but does not execute it yet.
Likewise D3A1 does not call the D1 `execution` capability and owns no PASS/FAIL
or expectation-kind logic.

`Runner.readSource` preserves source acquisition semantics by accumulating File
bytes and decoding UTF-8 only after EOF. It does not decode each read chunk
independently, so a multi-byte UTF-8 scalar may cross a File.read boundary
without becoming an artificial codec error. File ordering supplies the byte
sequence; the tool does not introduce a second source resolver.

TOOL002-D3A2 is CLOSED.

D3A2 adds no Filesystem or TestPlan traversal. One already-supplied CaseSpec and
source are executed exactly once through the explicitly supplied D1 execution
capability. The bundled policy returns frozen inert evidence containing canonical
`passed` plus the complete detached observation. Synthetic Protos fixtures cover
all four supported kinds, normal mismatches, cross-family Integer rejection,
unsupported-kind fail-closed behavior and malformed expected-Integer policy.

TOOL002-D3A3 is CLOSED.

D3A3 is the first whole-plan execution composition. It runs only expectation
kinds already owned by D3A2, keeps unsupported rows inert and unread, preserves
manifest order through a Future dependency chain, and returns ordered frozen
CaseRun evidence. A 1024-case Protos stress fixture verifies exact-once ordered
stack-bounded traversal with no host Test runner policy. A second Protos fixture
uses the real confined Filesystem plus D1 fresh-Process execution and proves an
unsupported row is not read.

`Main.protos` now executes this supported subset through the same composition but
does not yet report individual cases or turn an inert mismatch into CLI exit
policy.

TOOL002-D3B is READY.

### TOOL002-D3B decomposition

The D3B audit found two independently meaningful policies, so publication is
split again:

| Slice | Status | Outcome |
|---|---|---|
| TOOL002-D3B1 | CLOSED | `fixed-integer` parses `FAMILY:value`, accepts exactly the eight Core fixed-width families, constructs the expected semantic value with the selected standard numeric factory, and matches through primitive `===`. Implementation version `0.2.193-SNAPSHOT`. |
| TOOL002-D3B2 | CLOSED | D3B2A published the general `Object.parent()`/prelude prerequisite and D3B2B publishes retained immediate-parent Error expectation policy in bundled Protos. Implementation version `0.2.199-SNAPSHOT`. |

D3B1 preserves the D3A3 result and sequencing contract. `error-parent` remains
skipped before source acquisition until D3B2B.

#### TOOL002-D3B2 decomposition after executable prerequisite audit

| Slice | Status | Outcome |
|---|---|---|
| TOOL002-D3B2A | CLOSED | Implement the already-normative inherited `Object.parent()` reflection selector as one audited general Core representation bridge shared by ordinary lookup/reflection, restore the normative prelude `Object` binding through a temporary non-leaking root bootstrap seed, and cover ordinary/custom-parent, Error-taxonomy, represented values, root failure, and arity in Protos conformance. Implementation version `0.2.196-SNAPSHOT`. |
| TOOL002-D3B2B | CLOSED | Bundled Protos resolves the closed standard Error prototype taxonomy and matches a FAILED detached observation by exact immediate `error.parent() === expectedPrototype`; normal mismatch is inert evidence and malformed taxonomy policy fails closed. Implementation version `0.2.199-SNAPSHOT`. |

The failed pre-D3B2A policy attempt is not closure evidence. It demonstrated the
missing Core prerequisite and cleaned all patch-owned residue before publication.

TOOL002-D3B2B is CLOSED. D3B2 and D3B are CLOSED. TOOL002-D3C is READY.

### TOOL002-D3C decomposition

The D3C audit separates semantic NaN from exact raw-binary64 expectations:

| Slice | Status | Outcome |
|---|---|---|
| TOOL002-D3C1 | CLOSED | `float-nan` is matched entirely in bundled Protos by constructing the semantic Float NaN through ordinary `0.0 / 0.0` arithmetic and comparing the detached value through primitive `===`; payload must be `-`. Implementation version `0.2.201-SNAPSHOT`. |
| TOOL002-D3C2 | CLOSED | D3C2A parser, D3C2B exact binary64 mechanism and D3C2C sequential activation are published through `0.2.209-SNAPSHOT`. |

D3C1 deliberately leaves `float-bits` unsupported and unread by the sequential
runner. D3C remains IN_PROGRESS until D3C2 is published.

TOOL002-D3C2 is READY.

### TOOL002-D3C2 decomposition

The exact binary64 work is split into three independently publishable steps:

| Slice | Status | Outcome |
|---|---|---|
| TOOL002-D3C2A | CLOSED | Parse exactly 16 hex digits to an unbounded Integer raw pattern in bundled Protos; `float-bits` remains unsupported by the runner. Implementation version `0.2.205-SNAPSHOT`. |
| TOOL002-D3C2B | CLOSED | Bundled Protos reconstructs every portable non-NaN binary64 pattern exactly from D3C2A raw Integer fields using exact Float(Integer) significands plus bounded power-of-two scaling, applies sign through ordinary Float.negated(), and compares with primitive `===`. NaN raw patterns fail closed. Implementation version `0.2.208-SNAPSHOT`. |
| TOOL002-D3C2C | CLOSED | Activate `float-bits` in bundled Runner policy, evaluate exact portable non-NaN binary64 through D3C2A/B, migrate all historical unsupported sentinels to TOOL002-F `future-integer`, and preserve D3C2A/B mechanism coverage after activation. Implementation version `0.2.209-SNAPSHOT`. |

TOOL002-D3C2B and D3C2C are CLOSED. D3C2, D3C and D3 are CLOSED. TOOL002-D4 is READY.

### TOOL002-D4 closure

D4 closes the final non-Future identity-sensitive migration and the parent
TOOL002-D boundary:

- `closure-error-parent-fresh` is selected by the bundled runner without adding
  Closure transfer to D1;
- the retained source is evaluated once inside a private child-Process envelope;
- that exact candidate is invoked twice through ordinary `Error.handle`;
- both Error occurrences remain in the child identity domain while immediate
  standard parent and distinct identity are checked;
- only canonical Boolean evidence crosses the detached observation boundary;
- malformed expected Error prototype policy fails closed before source-name
  emission;
- a corpus-level Protos ownership fixture executes every D-owned main-manifest
  row and proves that only the deferred `future-*` families remain skipped;
- the legacy Java conformance harness directly executes only those `future-*`
  rows, eliminating duplicate Java ownership for D-migrated expectation policy.

Implementation version: `0.2.211-SNAPSHOT`.

TOOL002-D is CLOSED.

TOOL002-E is READY.

## Closure rule

TOOL002 closes only after all slices required for the selected initial Test Tool
outcome are implemented, validated, and published. Later optional hard-isolation
work does not block closure unless it is explicitly promoted into the parent
scope before closure.
