## 0.2.257-SNAPSHOT

- Record explicit project-owner ratification of D007 / specification `0.1.346` under AUD001 on 2026-09-08 after corrected cross-language, adversarial, package-system and future-scalability review. Retain `import(specifier)` as an ordinary call whose already-evaluated argument must be exactly a semantic `String`: no coercion, formatting, `toString` dispatch, duck typing, implicit invocation or String-like delegation may manufacture a specifier, and an invalid non-String value fails with the ordinary Core `Error` before host resolver entry. Core passes the exact String text to the host/module resolver without assigning intrinsic path, URI, URL, package, filesystem or registry meaning, without rewriting/normalizing it, and without universally rejecting the empty String. Host policy may interpret or reject that exact text, but a successful resolution produces the canonical `ModuleKey` that owns subsequent Core module identity/cache semantics; original spelling is not module identity, so distinct spellings may converge to one key and one Actor-local module instance. Resolution failure exposes a Protos `Error`, not host exceptions, status codes or sentinel values, and creates/caches no module instance. Specifier validation/resolution introduces no hidden Protos suspension, Future, scheduling or re-entry boundary. Package discovery, dependency/version selection, registry/network acquisition, immutable artifact fetching and execution-plan preparation remain explicit host/tooling concerns before or outside ordinary Core import; a future genuinely asynchronous acquisition or dynamic-loading capability remains separately designable rather than retroactively changing `import`. Governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Record explicit project-owner ratification of D006 / specification `0.1.344` under AUD001 on 2026-09-08 after cross-language comparison spanning Python, Julia, Swift, ECMAScript BigInt, Rust and Self plus adversarial and future-scalability review. Retain exact unbounded ordinary `Integer`, IEEE 754-2019 binary64 `Float`, the eight fixed-width signed/unsigned integer semantic families, strict same-family standard arithmetic with no implicit promotion/coercion, explicit checked numeric conversions, checked fixed-width overflow, exact-rational-to-binary64 integer `/`, same-family exact-integer `div`/`mod`/`%`, exact cross-family numeric equality/ordering, family-sensitive semantic identity and hash coherence with numeric `==`. Keep `SmallInteger`/`BigInteger`, tagging, boxing and other storage representations unobservable, while Rational, Decimal, Complex, bit/wrapping protocols, relaxed/SIMD math and any future promotion framework remain separate explicit designs. Governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Apply the explicitly owner-approved AUD001 D002 correction for bare assignment target timing. Preserve D002's lexical-local-first lookup, receiver fallback, local-only assignment destination and create-vs-modify separation, but make `x = rhs` select its exact existing destination before evaluating `rhs`; a missing destination therefore signals `SlotNotFound` before RHS effects, and a successfully selected destination is never re-resolved after RHS effects create or shadow same-named slots. This aligns the normative contract with the evaluator behavior that already existed when D002 was published and remains implemented today. Add Protos-level conformance cases for non-retargeting and missing-destination-before-RHS behavior. Specification/test correction only: no production implementation, implementation version, runtime/native boundary, license terms or AUD001 final classification change; D002 remains pending the separate governance-ratification slice after this normative correction is published.

- Record explicit project-owner ratification of D004 / specification `0.1.342` under AUD001 on 2026-09-08 after cross-language, adversarial and future-scalability review. Retain the standard Boolean protocol as ordinary one-argument messages `ifTrue`, `ifFalse`, `and` and `or`: when standard behavior is selected, the original receiver is exactly canonical `true` or `false`, while custom objects may define or override the same selectors without becoming semantic Booleans or introducing language-wide truthiness. Receiver and argument expressions keep ordinary call evaluation; only a selected callback is callability-validated and invoked, exactly once with zero positional arguments through ordinary polymorphic invocation, and it need not be a Closure. Standard `ifTrue`/`ifFalse` return the selected callback's exact normal result or canonical `null` on the unselected path. Standard `and`/`or` retain short-circuit behavior and require an invoked callback to return exactly canonical `true` or `false`, with no truthiness conversion, coercion, implicit invocation, implicit awaiting or Future adoption. Error/non-local/cancellation/other non-normal control transfers propagate ordinarily; the Boolean protocol adds no hidden task, lock, suspension or scheduling boundary. Keep the mandatory operator lowering `a && b -> a.and(() => b)` and `a || b -> a.or(() => b)` as ordinary dispatch so a custom receiver may observe the generated RHS Closure. Implementations may specialize canonical Boolean cases only when all observable lookup/evaluation/invocation/result/control behavior remains exact. Future conditional sugar and additional callable kinds remain separately designable. Governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Ratify AUD001 D003 as amended and publish specification `0.1.386` after explicit project-owner approval on 2026-09-08. Retain the complete deterministic Closure positional argument/default/rest/spread binder from specification `0.1.340`, but require all required non-rest parameters to precede defaulted parameters, with optional rest final; the former default-before-required shape is rejected because positional-only invocation cannot use that default in any otherwise successful call. Add no named arguments, omission marker, hidden missing/undefined value, TDZ, second parameter namespace, or runtime binder change. Allocate `I025` READY for parser/conformance alignment. Specification/governance only: no implementation-version, native-boundary or license-term change.

- Record explicit project-owner ratification of D001 / specification `0.1.341` under AUD001 on 2026-09-08 after cross-language comparison, adversarial review and dedicated future-scalability analysis. Retain one general semantic `Sequence` normal-result rule: expressions evaluate strictly left to right; a non-empty Sequence that completes normally returns the exact result of its final expression, while a zero-expression Sequence that completes normally returns canonical `null`. Error signaling/unwind, non-local return, cooperative cancellation and other control transfers are not converted into `null` and produce no normal Sequence result when they leave the Sequence. Apply the rule uniformly to semantic expression-sequences such as source module/program bodies and braced Closure bodies, while keeping `object-body-sequence` and object-construction results under their independent object-model semantics. Introduce no `Unit`, `undefined`, EmptySequence value, receiver/context-dependent empty result or runtime failure merely for emptiness. The rule constrains observable semantics rather than representation, so implementations may erase, inline or constant-fold Sequence machinery when behavior remains exact. Any future static type-system `Unit`/no-useful-result abstraction remains separate design work rather than a retroactive reinterpretation of D001. Governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Record explicit project-owner ratification of D021 / specification `0.1.358` under AUD001 on 2026-09-08 after distributed-systems, object-capability, adversarial and future-scalability review. Retain the three-way identity boundary: target Group identity, semantic `GroupRef` identity and physical proxy/wrapper/address/cache representation are distinct. A particular acquired `GroupRef` remains one identity-bearing communication capability across ordinary Actor/Process transfer, round-trip return and rematerialization; every materialization of that same semantic capability preserves `===`, `identityHashOf(...)` and `IdentityMap` key identity even when the runtime uses different physical proxies. Independently acquired GroupRefs are not canonicalized merely because they denote the same Group or currently carry equivalent effective communication permissions; different effective restriction state necessarily denotes different GroupRef identity. Preserve implementation freedom: no global interning, permanent wrapper registry, stable address, Group-to-GroupRef registry, proxy lifetime tied to Group lifetime or network coordination is required. Group membership/routing evolution, Group lifetime, Authority, future attenuation/revocation, discovery/reacquisition and any future explicit target-equality/joining facility remain separate designs. Governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Record explicit project-owner ratification of D024 / specification `0.1.367` under AUD001 on 2026-09-08 after cross-language comparison, adversarial review and dedicated future-scalability analysis. Retain fresh semantic identity for every successful receiver member-read selecting a Closure: each extraction produces a distinct ordinary Closure carrying the original receiver and current selected lookup home/`methodHome`, even when receiver, slot, stored Closure and home are unchanged. Preserve exact identity under ordinary alias/reference-preserving operations; storing an extracted Closure does not mutate or rebind it, while a later member-read of a Closure-valued slot is a new ordinary extraction with fresh identity and the later read's binding. Keep primitive identity/hash/IdentityMap behavior ordinary, distinguish observable extraction from immediate message invocation so unobservable wrappers need not be materialized, and introduce no Method/BoundMethod kind, Closure prototype, canonicalization cache, interning/global identity registry or bound-method-specific equality. Retaining one extraction remains the explicit way to retain stable callback identity. Future callable/behavior reflection, callback subscription facilities, multiple-delegation dispatch metadata, cross-isolation Closure transfer and physical COW/persistent representation remain separate work. D025/D026 are not classified by this slice and D046 remains outside AUD001. Documentation/governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Close `DIST002-D — cross-environment conformance + closure` and the parent `DIST002` project. Record green post-D3 GitHub Actions evidence on revision `6977f06441624267147b1f146fac340d06f464c3`: Tests run 34224766060 and Distribution snapshot run 34224766104 both SUCCESS, with distribution completing bootstrap/checksum, workspace trust, zero-drift toolchain verification, full Maven tests, portable build, complete extracted-distribution gate, checksum preparation and artifact upload. The closure publication gate revalidates the current descendant publication candidate locally with the exact canonical JDK/Graal/Truffle/Maven contract, toolchain drift 0, full tests and portable distribution validation. Documentation/status closure only: no Protos semantics, specification, implementation version, runtime coordinates, native boundary, license terms or historical `v0.2.236` release change.

- Complete the temporary TOOL001 per-corpus Protos fixture-wrapper consolidation with Runner-D3. Move the remaining `ProtosNioReadOnlyFilesystemBackendTest` fixture execution into `ProtosPackageToolProtosTest`, preserving exact-name read-only authority, final-symlink no-follow and unsupported-provider fail-closed evidence while switching execution to RootActor-local `ProtosRootTaskExecution`. Remove the redundant wrapper and record that F2E2A must extend this single runner rather than recreate a Java ContentIdentity wrapper. Separate Java tests for genuine adapter/backend/preflight mechanics remain independent. Test/governance refactor only: no production source, normative specification, implementation version, native-boundary or license-term change.

- Record explicit project-owner ratification of D022 / specification `0.1.362` under AUD001 on 2026-09-08 after cross-language comparison, adversarial review and dedicated future-scalability analysis. Retain the inherited standard `Object.init()` normal result as its exact receiver (`this`) on zero-argument normal completion; the inherited standard behavior still rejects a non-empty argument vector with the ordinary argument-count Error. Keep overriding `init` methods under ordinary Closure return semantics, so an override is not required to return `this`. Preserve default construction separation: inherited `Object.call` creates one fresh child whose parent is the invocation receiver, sends ordinary `init` to that fresh instance with the supplied arguments, ignores any normal result from `init`, and returns the fresh instance; an Error or other control transfer from initialization propagates and construction has no successful instance result. Direct `init()` therefore remains useful and portable without turning `init` into a factory-return channel or introducing a special initializer value category. Alternative constructors remain ordinary named messages. Governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Continue TOOL001 single-Protos-runner migration with Runner-D2. Add the existing `resolution-input` manifest corpus to the shared `plain` profile, move only its two physical lock stale/fresh host preparations into `ProtosPackageToolProtosTest`, reuse the existing confined metadata Filesystem fixture, and remove the redundant `ProtosPackageToolResolutionInputTest` wrapper. All Protos sources remain unchanged and execute through `ProtosRootTaskExecution`. Read-only metadata backend evidence and F2E2A remain for later bounded slices. Test/governance refactor only: no production source, normative specification, implementation version, native-boundary or license-term change.

- Record explicit project-owner ratification of D023 / specification `0.1.363` under AUD001 on 2026-09-08 after cross-language, adversarial and future-scalability review. Retain one general normal-result rule for the four Core slot-write expression forms `x: rhs`, `object.x: rhs`, `x = rhs`, and `object.x = rhs`: after successful slot creation or assignment, the expression returns the same exact object produced by RHS evaluation and stored by that write. Preserve exact identity without a second slot read, implicit conversion, copying, canonicalization, wrapping, receiver substitution or canonical `null` result. A write that does not complete normally has no normal expression result and does not roll back effects already completed during target/RHS evaluation. Destination-selection, delegation and object-state validity remain owned by their existing rules. Indexed `object[index] = value` remains outside D023 under the indexing protocol's own contract, and future computed-property/setter facilities remain separately designable rather than silently redefining slot writes. The exact-RHS rule constrains observable semantics, not storage/JIT representation: implementations may reuse the same SSA/runtime value, elide unused results and optimize slot storage provided identity and failure/control-transfer behavior remain exact. Governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Close `DIST002-D3 — CI distribution workspace Git trust correction` after post-D2 revision `fab881f88b9bf89b36326bc21aeaba7257a73872` proved checksum bootstrap, checkout, zero-drift JDK25/Graal-Truffle25.3.4.1/Maven3.9.9 validation and the full 1096-test suite all pass, while Distribution snapshot 34223295074 fails only when `dist/build_portable.py` invokes Git under the root job container after `actions/checkout`'s temporary HOME loses its `safe.directory` registration. Keep root only for prerequisite provisioning, explicitly trust exactly `$GITHUB_WORKSPACE` in the real job HOME immediately after checkout, and prove the builder's exact `git status --porcelain=v1 --untracked-files=all` operation before continuing. Do not use `safe.directory=*` or weaken source-identity checks. DIST002-D remains READY pending green post-D3 Tests + Distribution snapshot evidence and final reconciliation. Project-tooling/CI correction only: no Protos semantics, specification, implementation version, JDK/Graal/Truffle/Maven coordinate, runtime/distribution contract, native-boundary, license-term or historical `v0.2.236` release change.

- Continue TOOL001 single-Protos-runner migration with Runner-D1. Migrate only the existing `metadata-publication` Protos corpus into the already-published `ProtosPackageToolProtosTest`, reusing Runner-C's confined metadata Filesystem fixture and host postconditions; remove the redundant `ProtosPackageToolMetadataPublicationTest` wrapper. Keep all Protos fixture sources unchanged and execute them through `ProtosRootTaskExecution`. `resolution-input`, read-only metadata backend evidence and F2E2A remain for later bounded slices. Test/governance refactor only: no production source, normative specification, implementation version, native-boundary or license-term change.

- Record explicit project-owner ratification of D025 / specification `0.1.366` under AUD001 on 2026-09-08 after cross-language comparison, adversarial review and dedicated future-scalability analysis. Retain the Core v0.1 ownership of the standard Closure-specific selectors `future` and `parallel` as ordinary local Closure-valued slots of `Object`, reached by Core Closures through the independently ratified D027 direct delegation edge to `Object`. Introduce no standard `Closure` or `Callable` prototype, hidden Closure method table, per-Closure materialization or second dispatch path. The selected standard behaviors retain the semantic Closure receiver domain: another receiver may find the inherited selector by ordinary lookup, but invocation fails with the ordinary invalid-receiver Error before asynchronous/isolated-parallel effects and does not resume lookup. Preserve ordinary non-reserved selector naming, local shadowing/override behavior and the existing receiver-bound Closure extraction/methodHome rules. Keep operation semantics modular: FUTURES_AND_TASKS owns `future()` after receiver validation and PARALLEL_EXECUTION owns `parallel(...)` after validation; scheduler, worker and host machinery remain unobservable implementation details. This ratification is deliberately bounded to D025 `future`/`parallel`: it does not ratify D024 or D026, does not claim D025 as the owner of later `ensure`/`while` decisions, does not grant `future`/`parallel` to every ordinarily invokable object, and does not prohibit a future explicitly approved `Closure`/callable abstraction if such a category later earns its place. Governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Continue TOOL001 single-Protos-runner migration with Runner-C. Move only the `manifest-command` and `lock-file` host setup/postcondition methods into the existing `ProtosPackageToolProtosTest` bridge, preserve their existing confined metadata Filesystem authority and Protos fixture sources, execute every source through `ProtosRootTaskExecution`, and remove the two redundant Java wrapper classes. `metadata-publication`, `resolution-input`, read-only metadata backend evidence and F2E2A remain for later bounded slices. Test/governance refactor only: no production source, normative specification, implementation version, native-boundary or license-term change.

- Record explicit project-owner ratification of D026 / specification `0.1.364` under AUD001 on 2026-09-08 after cross-language, adversarial and future-scalability review. Retain exactly the canonical Boolean values `true` and `false` as the Core v0.1 Boolean semantic family, with no standard prelude binding, object or prototype named `Boolean`. Keep `Boolean` an ordinary non-reserved identifier: a program or library may bind that name through ordinary mechanisms, but the binding does not confer Boolean-family membership, and delegation/copying/composition likewise do not manufacture another Boolean value. This does not introduce truthiness, a Boolean conversion constructor, nominal Boolean type metadata or wrapper objects. D027's separate portable parent/topology decision is not classified by this slice; future Boolean libraries or family-introspection facilities remain separately designable. Governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Record explicit project-owner ratification of D028 / specification `0.1.369` under AUD001 on 2026-09-08 after cross-language comparison and dedicated future/scalability review. Preserve `parent()` as the general Core structural-reflection selector for the receiver's immediate delegation parent and keep standard `Path` from overloading or shadowing that selector. Retain `parentComponent()` as the distinct immutable Path constructor that appends one semantic parent-traversal component without resolving a Filesystem, computing a lexical parent, dropping a preceding component, or lexically collapsing the component; retain no Core v0.1 `Path.parent()` traversal compatibility alias. This keeps object reflection uniform and preserves structural Path semantics for capability-confined resolution across backend indirection. Future Path parsers/literals, lexical-parent/drop-last operations, normalization, first-class component APIs, multiple-delegation reflection and persistent/rope representations remain separate work; the current component-list copying implementation is not ratified. D046 remains outside AUD001. Documentation/governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Close `DIST002-D2 — CI checksum metadata EOF correction` after post-D1 GitHub Actions runs Tests 34222276908 and Distribution snapshot 34222276919 on revision `4f0b608728eadad569372d17eade6edb251737ec` both failed before checkout: Apache Maven 3.9.9 serves its exact 128-hex `.sha512` body without a trailing newline, so shell `read` returns non-zero at EOF under `set -e` even after populating the digest variable. Preserve D1's exact digest-only authority, 128-hex validation and independently calculated SHA-512 equality check while loading the complete metadata body with successful command substitution. DIST002-D remains READY pending green post-D2 primary workflow evidence and final cross-environment closure. Project-tooling/CI correction only: no Protos semantics, specification, implementation version, JDK/Graal/Truffle/Maven coordinate, runtime/distribution contract, native-boundary, license-term or historical `v0.2.236` release change.

- Continue TOOL001 single-Protos-runner migration with Runner-B. Add one shared `project-tree` profile to the already-published `ProtosPackageToolProtosTest`, migrate only the `resolution-root` and `execution-plan` corpora, preserve their existing read-only `ProtosNioReadOnlyTreeFilesystemBackend` confinement and `projectTreeFilesystem` activation slot, and remove the two redundant Java wrappers. All fixture execution continues through `ProtosRootTaskExecution`; Filesystem-mutable/metadata and F2E2A tests remain outside this slice. Test/governance refactor only: no production source, specification, implementation version, native-boundary or license-term change.

- Close `TOOL002-F3E4 — F3 Future-observation reconciliation`. Reconcile the published F3A/F3B/F3C/F3D and F3E1/E2/E3A/E3B evidence without executable changes: the retained stored/fresh `future-observation-error-identity` policy is complete behind one private bundled-Protos entry, malformed policy fails closed, valid identity/parent mismatches are inert false evidence, and public `isDExpectation`/`runSimple` plus Java's temporary direct `future-*` owner remain unchanged. Close F3E and F3 and make F4 READY for the atomic public Future expectation ownership cutover. Documentation/governance only: no specification, implementation-version, runtime, native-boundary or license-term change.

- Start TOOL001 single-Protos-runner migration with Runner-A. Add one temporary `ProtosPackageToolProtosTest` Java bridge and one small runner manifest, migrate only the existing `version` and `lock` Protos corpora into it, and remove their two per-corpus Java wrappers. Execute fixtures through `ProtosRootTaskExecution` so the shared runner already has valid RootActor-local task semantics for later Future-suspending corpora. Record that additional TOOL001 Protos corpora must migrate into this same runner in bounded slices until TOOL002 replaces the bridge. Test/governance refactor only: no production source, normative specification, implementation version, native-boundary or license-term change.

- Make cost-aware task decomposition continuous rather than a one-time pre-implementation check. If an initially bounded task becomes materially costly, slow, context-heavy, failure-prone, or repeatedly expands/reworks the same surface, require the agent to stop growing the current slice and automatically subdivide the remaining already-approved implementation work whenever independently valid boundaries exist. Decomposition itself needs no user approval when parent outcome, approved semantics, priorities, and dependency order are preserved; user approval remains required when the split exposes a substantive design choice, changes outcome/priority, requires an invalid intermediate state, or selects among materially different project-level dependency orders. Governance only: no specification, implementation version, runtime, native-boundary or license-term change.

- Close `DIST002-D1 — CI bootstrap checksum portability correction` after observed GitHub Actions runs Tests 34221407013 and Distribution snapshot 34221407000 for exact DIST002-C commit `996fed0393c15b122bec6541bf8394bdb15a96c8` both failed in the pre-checkout Maven bootstrap: Apache Maven 3.9.9 publishes its `.sha512` as a bare 128-hex digest, which GNU `sha512sum -c` cannot consume without a filename. Preserve exact Maven 3.9.9 and SHA-512 integrity while making both workflows parse/validate the digest token, independently hash the downloaded archive, compare the values, and emit `MAVEN_ARCHIVE_SHA512_CHECK: PASS` before extraction. DIST002-D remains READY pending green post-D1 primary workflow evidence and final cross-environment closure. Project-tooling/CI correction only: no Protos semantics, specification, implementation version, JDK/Graal/Truffle/Maven coordinate, runtime/distribution contract, native-boundary, license-term or historical `v0.2.236` release change.

- Close `TOOL002-F3E3B — integrated private observation negatives`. Add Protos-owned evidence through the already-private unified `evaluateFutureObservation` entry: malformed family/policy is rejected before the inspector executor can run; stored-mode identity and immediate-parent mismatches complete normally with false evidence; fresh-mode reused identity and immediate-parent mismatches likewise complete normally with false evidence. No bundled Runner, Java host mechanism, public `runSimple`/`isDExpectation`, retained main-manifest, specification or implementation-version behavior changes. Close E3B and make documentation-only E4 READY; F4 remains dependency-gated. Test-impact only.

- Record explicit project-owner ratification of D027 / specification `0.1.365` under AUD001 on 2026-09-08 after comparison with prototype/class-based object models, adversarial review and dedicated future-scalability analysis. Retain the Core v0.1 portable delegation-topology rule: every Core-standard visible object whose immediate parent is not fixed by a more-specific normative owner delegates directly to `Object`, while `Object` remains the unique root with no parent. Preserve explicit semantic exceptions such as the numeric hierarchy, Error taxonomy, Context ancestry, semantic String/Future value parentage, factory-result parentage and domain-owned capability prototypes. Canonical `true`, `false`, `null` and every Closure continue to delegate directly to `Object`; Core v0.1 adds no organizational `Boolean`, `Closure`, `Value`, `Collection`, `Callable` or `AsyncValue` ancestor merely to classify values. Standard `Array`, `Map`, `IdentityMap`, `Future` and other ordinary Core prototype objects likewise use direct `Object` parentage unless a narrower owner says otherwise, and `IdentityMap` does not implicitly inherit `Map`. Keep semantic-family membership separate from delegation and forbid implementation-defined Protos-visible intermediate ancestors while allowing arbitrary hidden host/JIT representation. This ratification owns the Core v0.1 topology only: future explicitly approved prototype categories or a future object-model redesign remain possible normative changes, and D025/D026 remain independent AUD001 decisions rather than being ratified by transitivity. Governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Close `DIST002-C — live distribution/runtime migration`. Migrate the root Graal/Truffle implementation dependencies and portable runtime closure from historical 24.0.0 to the DIST002-A canonical 25.3.4.1 line while retaining Java 21 bytecode compatibility; advance implementation version `0.2.256-SNAPSHOT` -> `0.2.257-SNAPSHOT`. Move distribution snapshot CI from on-demand GraalVM JDK22 setup to the exact primary GraalVM `25i3` / JDK `25.0.4.1` container with checksum-verified Maven 3.9.9, publish exact JDK/Graal/Truffle runtime metadata, enforce the recorded Java version in the distributed launcher, preserve GraalVM-required multi-release/service metadata in the shaded JAR, and make B4B/B5 use the already-provisioned primary runtime while still proving exact `HotSpotTruffleRuntime`. Extend the toolchain auditor/tests so every live development/CI/distribution binding is zero-drift. Preserve the existing public `v0.2.236` artifact/tag/claims and all DIST001/PERF JDK22/Truffle24 evidence as historical evidence. DIST002-C closes and DIST002-D becomes READY. No Protos normative semantics, specification revision, native-Closure boundary or license-term change.

## 0.2.256-SNAPSHOT

- Record explicit project-owner ratification of D029 / specification `0.1.368` under AUD001 on 2026-09-08 after cross-language, adversarial and future-scalability review. Retain the general Core-standard result-family rule: whenever an operation's normal result or Future resolution is specified simply as `Integer` without naming a more specific numeric family, the result is an ordinary unbounded Integer. This is a result-only rule and does not narrow input domains; contracts that explicitly name a fixed-width or other more-specific numeric family remain unchanged. Preserve the separation between semantic family and implementation representation: tagged/machine-word/small/big forms may optimize ordinary Integer values invisibly, while host pointer/index width must not select a portable result family. This avoids per-API `size_t`/Int32/Int64-style host leakage without inventing Size/Natural/Index families; genuinely width-significant APIs remain free to name `UIntN`/`IntN` explicitly. Governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Publish `TOOL002-F3E3A — unified private Future-observation composition`. Add one private bundled-Protos `evaluateFutureObservation` entry for the retained `future-observation-error-identity` family. It parses the already-closed `MODE:ErrorPrototype` policy once, dispatches `stored` to the existing stored evaluator and `fresh` to the existing fresh evaluator, and returns the selected evaluator's inert result tuple unchanged. Add one Protos-owned composition fixture that exercises both retained positive cases through this single private entry; Java only adds bootstrap-local retained stored-source text alongside the already-present fresh source. Close F3E3A and make F3E3B READY. Do not add integrated malformed/mismatch negatives, public `runSimple`/`isDExpectation` selection, main-manifest migration, or Java Future-owner retirement. No normative specification or native-boundary change; implementation version becomes `0.2.256-SNAPSHOT`.

## 0.2.255-SNAPSHOT

- Record explicit project-owner ratification of D030 / specification `0.1.372` under AUD001 on 2026-09-08 after cross-language comparison, adversarial review and dedicated future-scalability analysis. Retain `Future.then(transform)` against the ordinary invocation/callability protocol rather than a Closure-only or hidden Callback category. After ordinary receiver/argument evaluation, validate the exact transform eagerly by read-only ordinary `call` lookup before creating any destination Future, continuation task, registration or scheduling state, independently of whether the source Future is pending, resolved, failed or cancelled. The validation does not invoke the transform and does not preflight declared arity/default/rest behavior. It also does not pin the Closure selected during inspection: when a resolved source later runs the continuation, ordinary polymorphic invocation performs a fresh `call` lookup, so legitimate intervening mutation or shadowing remains observable. Programs that want to preserve an already-selected behavior can do so explicitly through ordinary Closure/member extraction rather than a `then`-specific capture rule. Keep D030 distinct from the separately ratified D034 Closure-only dynamic Error-handler boundary. Future executable value kinds, static signature/arity introspection, distributed continuation transfer and other callable institutions remain separate explicit designs. Governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Record explicit project-owner ratification of D031 / specification `0.1.368` under AUD001 on 2026-09-08 after comparative, adversarial and future-scalability review across JavaScript Promises, Java CompletableFuture/CompletionStage, Python asyncio/coroutines, Kotlin Job, .NET Task/ValueTask and Rust Future models. Retain D031 as the idempotent-lifecycle specialization of the already-ratified D036 Future-result identity rule: every successfully dispatched invocation of a standardized Future-returning idempotent lifecycle operation produces its own fresh semantic standard Future unless that operation expressly returns an existing Future, while repeated `close()`, `shutdownRead()` and `shutdownWrite()` calls observe one irreversible logical lifecycle rather than starting independent attempts. Pending and later terminal observers share that lifecycle's single logical success/failure outcome; where the lifecycle records one terminal Error cause, same-domain re-observation preserves that exact Error object. Fresh Future identity remains semantic rather than a physical-allocation mandate, so follower/completion representation, virtualization, scalar replacement, pooling and shared immutable terminal backing remain implementation freedoms when Future identity and Future-local effects stay exact. Add no canonical lifecycle Future, Future subtype, wrapper, hidden lifecycle token or distributed Future identity. A future API may still explicitly return a pre-existing/shared Future under D036's existing exception. The current follower-list implementation may be optimized independently without changing these semantics. D046 remains outside AUD001. Documentation/governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Publish `TOOL002-F3E2 — positive private fresh observation evaluator`. Extend the private F3E1 policy tuple with its already-validated standard Error prototype name, preserving the existing mode/parent accessors, then add one bundled-Protos `fresh` evaluator over generic same-Process `executionInspect`. The inspector invokes the retained zero-argument `observe` Closure exactly twice, requires both calls to signal, requires each caught Error's immediate parent to be the parsed named standard prototype, and requires distinct Error identities. Add one Protos-owned positive fixture over retained `future/cancelled-value-fresh-error.protos` with `fresh:Cancelled`; Java only installs the existing inspection facility and supplies retained source text. Close F3E2 and make F3E3A READY. Do not add unified stored/fresh dispatch, integrated negatives, public `runSimple`/`isDExpectation` selection, main-manifest migration, or Java Future-owner retirement. No normative specification or native-boundary change; implementation version becomes `0.2.255-SNAPSHOT`.

## 0.2.254-SNAPSHOT

- Record explicit project-owner ratification of D020 / specification `0.1.359` under AUD001 on 2026-09-08 after cross-language, adversarial and future-scalability review. Retain the deliberately narrow Core v0.1 String transformation boundary: `uppercase()` and `replace(...)` are not standard Core String operations. String immutability remains the relevant Core rule, while ordinary libraries, implementation extensions and user objects may define those names through normal slots and invocation. Do not import a mandatory Core policy for Unicode case mapping/version updates, locale tailoring, normalization, literal-versus-pattern matching, overlap, empty-pattern/needle behavior, replacement callbacks or streaming/resource behavior merely to provide familiar conveniences. This boundary does not reject future standard text algorithms: casing, case folding, replacement, split/join, normalization, collation, locale, pattern facilities and streaming transformations remain separately designable Standard Library or later-version work with their own contracts. Governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Publish `TOOL002-F3E1 — Future observation MODE:ErrorPrototype parser`. Add a private bundled-Protos parser for the retained `future-observation-error-identity` expected payload. It requires exactly one non-empty `MODE:ErrorPrototype` separator, accepts only the already-retained `stored` and `fresh` modes, resolves the named immediate parent through the existing standard Error prototype resolver, and fails closed on malformed mode/payload or unknown prototype. Add one Protos-owned fixture covering valid `stored:Error`, `fresh:Cancelled`, and another valid standard parent plus malformed format/mode/prototype cases; the Java harness supplies only ordinary Test Tool bootstrap mechanics. Record the F3E decomposition with E1 CLOSED and E2 READY. Do not add fresh evaluation, unified observation evaluation, public `runSimple`/`isDExpectation` selection, main-manifest migration, or Java Future-owner retirement. No normative specification or native-boundary change; implementation version becomes `0.2.254-SNAPSHOT`.

## 0.2.253-SNAPSHOT

- Record explicit project-owner ratification of D032 / specification `0.1.370` under AUD001 on 2026-09-08 after comparative review across JavaScript, Python, Ruby, Smalltalk, Self, Java/.NET and Go plus dedicated future-scalability analysis. Retain the published `slotNames()` reflection-result contract: every successful invocation returns one fresh identity-bearing ordinary standard Array snapshot, including repeated observations of an unchanged receiver and empty results; independently returned Arrays remain distinct under semantic identity and have independent indexed mutable state. Mutating one snapshot cannot mutate the reflected receiver, another snapshot or a later observation. Preserve the existing shallow contents and deterministic ordering owned by the prior reflection contract, with no deep-copy requirement for contained slot-name Strings. Fresh identity is semantic rather than a physical-copy mandate: lazy, virtual, persistent, shared immutable backing, copy-on-write and allocation-elision strategies remain conforming when observable identity and state independence are preserved. Keep `slotNames()` as ordinary detached data rather than a live view, Mirror, iterator-only surface or implementation-visible cache. Future incremental reflection or explicit reflective-authority facilities remain separate design work. This AUD001 classification does not assert that the current runtime implementation surface is complete; any `slotNames()` implementation/reconciliation gap remains separate implementation work. Governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Record explicit project-owner ratification of D034 / specification `0.1.372` under AUD001 on 2026-09-08 after comparative, adversarial and future-scalability review. Retain `Error.handle(body, handler)` as a deliberately Closure-only dynamic-control boundary: ordinary receiver/body/handler expressions evaluate left-to-right first; body is then Closure-validated, then handler; and no dynamic handler frame is installed until both validations succeed, so invalid body/handler input cannot start the protected body. Preserve ordinary activation-time arity, default and rest binding rather than adding D034 signature preflight; when a handler is selected its frame is consumed/inactive before handler invocation and the exact signaled Error is passed to that Closure. Keep this intentionally distinct from D030's ordinary-invokable `Future.then` callback domain: D034 adds no Handler type, hidden callback category, duck typing, `try`/`catch` syntax, distributed handler state or new executable kind. Resumable recovery/restarts/effects, new executable kinds, generic control-region callable domains and static signature introspection remain separate future design work. D046 remains outside AUD001. Documentation/governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Close `DIST002-B — development + ordinary CI primary-runtime alignment`. Confirm the existing devcontainer already matches the DIST002-A primary GraalVM Community `25i3` / JDK `25.0.4.1` image and exact Maven 3.9.9 binding, replace ordinary Tests CI's Temurin 21 `setup-java` path with a job container using that exact GraalVM image, bootstrap checksum-verified Maven 3.9.9 before repository validation, and verify exact GraalVM/JDK/Maven identity before the full Maven suite. Extend `tools/verify_toolchain.py` with a fail-closed development scope so devcontainer + ordinary-CI drift is now zero while the intentionally remaining Truffle/distribution bindings stay visible for DIST002-C. DIST002-B closes and DIST002-C becomes READY. Project tooling/CI only: no Protos semantics, normative specification, implementation version, native-boundary, license-term, existing public-release artifact, tag, or historical DIST001/PERF evidence change.

- Decompose `TOOL001-F2E2 — verified read-only package-store binding` before implementation now that I024/B009 are closed. Allocate F2E2A as the Protos-only `self:ContentIdentity` canonicalizer/verifier over an already-captured Filesystem; F2E2B as the exact already-selected store-root capture and host custody/handoff boundary; and F2E2C as same-capture integration/final closure. Keep F2E3/F2E4/F2E5 dependency-gated, preserve the prohibition on ambient store scans/package-specific Java tree walking, and defer the concrete captured-backend custody mechanism to B. Documentation/project decomposition only: no production source, normative specification, implementation version, native boundary or license-term change.

- Record explicit project-owner ratification of D033 / specification `0.1.371` under AUD001 on 2026-09-08 after comparative and future-scalability review. Retain the uniform reflective local-slot name domain: standard `hasSlot(name)`, `slotValue(name)`, and `removeSlot(name)` accept exactly semantic `String` values; there is no implicit conversion, stringification, selector coercion, delegation-based String-like acceptance, host-name adaptation, or hidden Unicode normalization. After ordinary argument evaluation, an invalid non-String name fails before local-slot inspection and, for `removeSlot`, before structural mutation. A valid name denotes its exact Unicode scalar-value sequence and the operations remain local-only: `hasSlot` returns canonical false for absence while `slotValue` and `removeSlot` retain their ordinary missing-local-slot Error; a delegated slot does not satisfy them. Preserve implementation freedom to intern or canonicalize storage, cache hashes, use internal Name/Slot IDs, shapes or inline caches when these are unobservable. A future first-class Symbol/Selector/name facility remains a separate design decision and does not silently widen these Core v0.1 APIs. Governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Record explicit project-owner ratification of D036 / specification `0.1.374` under AUD001 on 2026-09-08 after comparative, adversarial and scalability review. Retain the general Core-standard Future result-identity rule: unless an operation expressly returns an already-existing Future, every successfully dispatched Future-producing invocation produces a fresh semantic standard Future identity, including immediately resolved, failed or cancelled results. Distinct invocation results therefore remain distinct under `===`, `!==`, `identityHashOf` and `IdentityMap`; implementation-selected pending aliasing or terminal canonicalization may not change that identity. Preserve the separation between Future identity and eventual value/Error/outcome identity, and preserve explicit contracts such as `cancel()`/`detach()` or future memoized/shared-work APIs that deliberately return an existing Future. Physical allocation remains optimizable through elision, virtualization, pooling or shared backing when observable identity and Future-local effects are preserved. This ratifies D036 only; D031 remains subject to its own AUD001 review. Governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Record AUD001 classification of D035 / specification `0.1.373` as `RATIFIED` from recovered explicit project-owner selection on 2026-09-04. Retain the published general Core standard Bytes-producing result contract: unless an operation expressly returns an existing object, every successful operation whose normal result is a `Bytes` object produces its own fresh open standard Bytes identity with independent receiver-owned mutable state, including empty results; results do not alias source/argument Bytes, the producer, another invocation's result, or an implementation-controlled buffer. Preserve the specific `ByteReadable.read` and `Encoding.encode` applications, observable ordinary identity/hash/open-state consequences, failure/cancellation non-exposure of partial results, and implementation freedom for physical sharing such as immutable backing, copy-on-write, slicing or zero-copy when Protos cannot observe collapsed identity or shared mutable state. This records the already-selected/published semantics only. Documentation/governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Close `DIST002-A — canonical toolchain authority + drift audit` after explicit project-owner selection on 2026-09-08. Persist root `toolchain.json` as the single repository-owned primary toolchain contract: Java 21 bytecode target, exact GraalVM Community 25.3.4.1 / JDK 25.0.4.1 (`25i3`) primary runtime, Graal/Truffle 25.3.4.1 and Maven 3.9.9, with non-floating upgrades requiring an explicit validated project change. Add a tested `tools/verify_toolchain.py` contract/static-binding auditor so current JDK22/Truffle24 and Temurin21 migration drift is visible before DIST002-B/C align consumers. Preserve historical DIST001/PERF JDK22 evidence and the isolated benchmark IGV analyzer's JDK17 tooling boundary; neither constrains the new primary runtime. DIST002 becomes IN_PROGRESS and DIST002-B becomes READY. Project tooling/governance only: no Protos semantics, normative specification, implementation version, native-boundary, license-term, release tag or existing public-release claim changes.

- Close `I024-D — integrated Filesystem tree-observation conformance and I024/B009 closure`. Add Protos-visible conformance over the production secure NIO tree backend for complete eager exact-name descriptors, regular/directory/link/other no-follow kinds, frozen descriptor state, final-link failure, read-only/source-independent captured Filesystems, captured-subtree verify/use stability and cancellation/late-result custody. Re-run the native-boundary architecture guard, close I024 and B009, and make `TOOL001-F2E2` READY to implement verified read-only package-store binding through the general D046 capability rather than package-specific NIO. Test/documentation closure only: no production source, normative specification, implementation-version or license-term change.

- Record AUD001 classification of D038 / specification `0.1.375` as `RATIFIED` from recovered explicit project-owner selection predating publication. Retain the already-published Encoding semantic-family boundary: Encoding descriptors are semantic Encoding values produced/provisioned only by normative Encoding-producing operations or explicitly permitted host provisioning boundaries; delegation, copying, composition, similarly named slots and structural/protocol compatibility do not confer Encoding membership; Encoding-domain parameters perform no duck typing or implicit coercion; and standard Encoding-family behavior validates the original receiver under the general semantic-family receiver-domain rule after ordinary lookup, with an incompatible receiver failing as the ordinary invalid-receiver `Error` before family-specific computation/state effects and without fallback dispatch. D037 remains a separate AUD001 evidence-recording item; D039 is not included; D046 remains outside AUD001. Documentation/governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Record D037 / specification `0.1.375` as `RATIFIED` under AUD001 from already-recovered explicit project-owner confirmation. Preserve the published Path equality/identity split: portable Path equality is structural and filesystem-independent over rootedness plus the ordered component sequence; Path semantic identity remains ordinary individual object identity, so independently created structurally equal Paths remain distinct through `===`, `!==`, `identityHashOf`, and `IdentityMap`; Path is not added to the closed Core value-identity set; and Filesystem namespace lookup identity, host syntax/normalization, and resource identity remain separate. This patch persists already-recorded owner-confirmation evidence only. Documentation/governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Record explicit project-owner ratification of D039 / specification `0.1.376` under AUD001 on 2026-09-08 after comparative and scalability review. Retain the complete published Core v0.1 ActorGroup acquisition contract: `Actor.group(firstMember, additionalMembers...) -> GroupRef` requires one or more explicit ActorRef communication capabilities and validates the complete vector before one synchronous cutover; that cutover creates one fresh Group identity, set-like initial membership and one fresh GroupRef acquisition, with the caller Actor's Process as the Core-created Group lifetime scope. Repeated references to one Actor incarnation do not create weighted membership; creation does not wait for member readiness/reachability, create or rehost members, or grant member lifecycle/control Authority. Group termination does not terminate members, GroupRef reachability does not extend Group lifetime, and GroupRef remains communication-only. Preserve the explicit Core boundary excluding public Group control/membership mutation, Group lookup/reacquisition, registry/service discovery, naming/rebinding, endpoint, placement and transport-selection surfaces; durable/discovery/control layers remain separate future design work. Ratification only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Close `TOOL002-F3D2 — second retained cancelled observation fresh identity`. Extend the dedicated fresh-cancellation Protos evidence with one second same-Process observation of the exact retained `future/cancelled-value-fresh-error.protos` fixture. The inspector captures the first and second signalled Error objects, requires the second occurrence to have immediate parent `Cancelled`, and requires `first !== second`, proving the normative per-observation freshness contract without adding Test-only Future state. Generalize only the existing Java fixture-suite ID guard to admit F3D2. Close F3D and make F3E READY; keep bundled `Runner.protos`, public `runSimple`/`isDExpectation`, the retained main manifest and Java's temporary direct `future-*` ownership unchanged until F4. Test-impact only; no normative specification or implementation-version change.

- Record explicit project-owner ratification of D040 / specification `0.1.377` under AUD001 on 2026-09-08 after comparative and future-scalability review. Retain the published dynamic super-dispatch model: `super` preserves the original receiver and continues lookup after the invocation's `methodHome`; Method remains an invocation role of the single Closure value kind rather than a static category; the complete ordinary argument/spread vector is evaluated before super dispatch; missing `methodHome` signals a fresh `InvalidSuper` with no lookup; a valid home with an empty/exhausted continuation signals ordinary `SlotNotFound`; and no first-class `super`, fallback lookup origin, static Method value kind or P-specific super error is introduced. Record the scalability boundary without changing semantics: `parent(methodHome)` remains the exact Core v0.1 realization under the current single-parent object model, while any future multiple-parent/linearized/directed-resend/next-applicable-method generalization requires a separate explicitly approved specification change. D046 remains outside AUD001. Documentation/governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Close `TOOL002-F3D1 — first retained cancelled observation parent evidence`. Add one Protos-owned live-inspection fixture over the retained `future/cancelled-value-fresh-error.protos` source. The exact retained `observe` Closure is invoked once inside the same fresh Process through the existing generic `executionInspect` boundary, and the caught fresh Error must have immediate parent `Cancelled`. This slice does not make a second observation, does not assert cross-observation freshness, does not modify bundled `Runner.protos`, public `runSimple`/`isDExpectation`, or Java's temporary direct `future-*` corpus ownership. `TOOL002-F3D1` closes, parent F3D becomes IN_PROGRESS and F3D2 becomes READY. Test-impact only; no normative specification or implementation-version change.

- Ratify D043 under AUD001 and publish specification `0.1.385` after explicit project-owner approval on 2026-09-08. Retain the ordinary Closure `ensure(cleanup)` design and clarify only its already-intended boundaries: only a control/Error transfer that escapes cleanup supersedes the pending outcome; an internally handled cleanup Error does not; a selected handler is a consumed one-shot unwind destination that is abandoned if crossed cleanup escapes with a replacement transfer; repeated idempotent `Future.cancel()` calls do not pierce shielding of the already-honored request; and exactly-once cleanup is a semantic-unwind guarantee, not fail-stop/distributed crash recovery. Preserve task-local dynamic control and defer resumable/multi-shot continuations, hard termination, durable/distributed compensation and richer diagnostic aggregation to separate future designs. Specification/project documentation only: no runtime/source, implementation-version, native-boundary or license-term change.

- Close `I024-C — secure NIO tree observation + immutable captured backing`. Extend the existing complete-tree `ProtosNioReadOnlyTreeFilesystemBackend` with D046 `entries` and `captureTree`: every selected directory is bound through fresh relative `SecureDirectoryStream` handles with `NOFOLLOW_LINKS`, direct-child classification uses no-follow basic attributes and exact representable names, recursive capture never follows child links, and regular bytes stream into private implementation-managed temporary blob backing rather than accumulating the complete payload in heap. Captured Filesystem backends keep immutable name/kind/tree metadata, share backing across captured subtrees, retain backing while opened Files exist, and reclaim it through internal Cleaner/lease custody without adding a public Filesystem `close`. Preserve existing read-only open behavior and all source/captured authority boundaries. I024-C closes, I024-D becomes READY, and B009 remains READY until integrated Protos conformance closes I024. No normative specification or native-Closure-boundary change.

## 0.2.252-SNAPSHOT

- Record explicit project-owner ratification of D041 / specification `0.1.378` under AUD001 on 2026-09-08 in its effective form after the already-ratified D042 / `0.1.379` correction. Retain D041's still-effective Filesystem namespace-mutation contract: one confined Filesystem authority for both paths; indivisible failure-atomic `replace`/`remove` namespace transitions; no copy-then-delete or truncate-and-write substitute; same-resource replacement as a no-op; stable already-open File binding; pre-commit cancellation/failure with no operation-attributable namespace mutation; irreversible committed success that cannot later surface as failed/cancelled; fail-closed backends when the required determinate transition cannot be provided; no implicit Filesystem FIFO; and explicit separation of live atomic visibility from crash durability, including that File `sync()` is not a namespace-durability barrier. Explicitly exclude D041's historical ordinary-file-only final-entry restriction from this ratification because D042 superseded it with the race-safe final-entry namespace-selection rule. D046 remains outside AUD001. Documentation/governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Close `TOOL002-F3C2 — stored observation negative policy`. Strengthen the already-private bundled-Protos `stored:Error` inspector so a positive observation requires both exact caught-versus-stored Error identity and immediate `Error` parentage. Add three Protos-owned C2 fixtures: malformed `stored` policy fails closed before inspector execution; a correct `stored:Error` CaseSpec with a different observed Error is an inert expectation mismatch; and exact stored identity with immediate `Cancelled` parent is an inert parent mismatch. Generalize only the existing Java fixture harness id guard to admit C2 evidence. Close F3C, make F3D/F3D1 READY, keep public `runSimple`/`isDExpectation` unchanged and retain Java as temporary direct `future-*` owner until F4. No normative specification, native-boundary or license-term change; implementation version becomes `0.2.252-SNAPSHOT`.

## 0.2.251-SNAPSHOT

- Record explicit project-owner ratification of D042 / specification `0.1.379` under AUD001 on 2026-09-08 after comparative review against POSIX/Unix, Java NIO, Rust, Go, Python and .NET filesystem models. Retain the published race-safe namespace-entry correction: final Path components are selected as namespace entries without following final symbolic-link/reparse/other indirection and without a separate mutable file-kind preclassification; `replace`/`remove` may act on entry kinds when the backend can provide the required atomic transition; unsupported atomic entry-kind or source/target-kind combinations fail as `IOError` rather than being emulated through check-then-act; and `remove` remains non-recursive. This ratifies D042 only: D041's separate atomicity, commitment, cancellation, stable-open-File and namespace-durability package remains subject to its own AUD001 review. D046 remains outside AUD001. Documentation/governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Close `I024-B — standard Filesystem tree-observation materialization`. Extend the existing host-provisioned `ProtosStandardFilesystemProtocol` shared operation bridge with D046/0.1.384 `entries` and `captureTree` selectors without adding another native-Closure construction site. Existing backends remain source-compatible through default-fail tree-observation methods until I024-C. Successful `entries` materializes one fresh standard Array containing fresh frozen root-Object descriptors with exactly semantic String `name`/`kind`; successful capture materializes a fresh standard Filesystem through an internal read-only captured-backend contract, structurally rejecting mutation and write/create/truncate/append opens and exposing no Filesystem `close`. Add Java bridge coverage plus Protos-source conformance proving that both selectors dispatch through ordinary Futures and that unsupported current NIO backends fail as `IOError`. I024-B closes, I024-C becomes READY, and B009 remains READY. No normative specification change; implementation version becomes `0.2.251-SNAPSHOT`; the audited Core native boundary remains 113 construction sites across 30 providers.

## 0.2.250-SNAPSHOT

- Reconcile AUD001 after the concurrent D045 ratification publication. Remove three stale current-state references that still treated D045 as unratified: the priority triage now covers D039-D044 plus D020, the active backwards-review procedure continues from D044, and the separately ratified specification `0.1.90` child-outcome policy now describes D045's ownership-scope core as separately ratified. Preserve D045 as `RATIFIED`, AUD001 as `OPEN`, D046 outside AUD001, and all historical changelog entries unchanged. Documentation/governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Close `TOOL002-F3C1B4`, parent `TOOL002-F3C1B`, and parent `TOOL002-F3C1` by reconciling the published B1 classifier, B2 retained live-inspection evidence, and B3 bundled-Protos evaluator composition. Positive `future-observation-error-identity / stored:Error` policy is complete inside C1 without activating public `runSimple`, changing Java's temporary `future-*` ownership, adding fresh mode or stored negatives. `TOOL002-F3C2` becomes READY; F3D/F3E/F4 remain dependency-gated. Documentation/governance only: no executable, specification, implementation-version, native-boundary or license-term change.

- Record explicit project-owner ratification of D045's task-scoped structured-ownership core under AUD001 on 2026-09-08. Retain the published semantics that synchronous Closure/method activations do not create structured scopes; returning, storing or wrapping a pending Future does not alter its ownership edge; distinct asynchronous child tasks own their own descendants; and `detach()` is the explicit operation that removes the edge. This ratifies D045 without changing normative specification or implementation and remains independent of the already-ratified specification `0.1.90` structured-child terminal-outcome policy. D046 remains outside AUD001. Documentation/governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Record explicit project-owner ratification of the specification `0.1.90` structured-child terminal-outcome policy after AUD001 provenance reconstruction, prior-art comparison and adversarial review. Retain the published Core v0.1 separation between structured lifetime/cleanup and explicit Future outcome observation: child failure/cancellation does not automatically fail/cancel its normally completing owner or siblings, while owner failure/cancellation still cancels non-detached children and waits for cleanup; add no hidden failure-consumption state. Close `STRUCTURED_CHILD_OUTCOME_FOLLOWUP` independently of D045, whose ownership-scope core remains `NEEDS_USER_DECISION`. Governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Record the AUD001 D045 review without ratifying or changing language semantics. Recommend retaining D045's task-scoped ownership core while leaving D045 as `NEEDS_USER_DECISION`; separate the inherited specification `0.1.90` non-propagating structured-child outcome policy into an OPEN AUD001 follow-up covering failure propagation, aggregation, sibling cancellation, supervision and owner termination. D046 remains excluded. Documentation/governance only: no normative specification, implementation, blocker, implementation-version, runtime, native-boundary or license-term change.

- Close `I024-A2 — post-D046/0.1.384 compatibility re-audit` without production modification. Re-audit the published host-neutral tree-observation substrate against the explicitly approved eager-Array, Filesystem-not-Directory and non-Closable captured-Filesystem decisions: complete `List<Entry>` snapshotting remains compatible with eager `entries`, duplicate exact names fail closed, `CapturedTree` remains internal custody only, successful capture transfer does not invoke `releaseIfUntransferred`, and cancellation/late-result/materialization-failure paths release only untransferred custody. Confirm the standard Filesystem surface still has no `close`; record the pre-existing Java `AutoCloseable` read-only tree backend as an I024-C implementation-custody constraint rather than public lifecycle. Focal `ProtosFilesystemTreeObservationFlowTest` and the complete test suite are required before publication. I024-B becomes READY; B009 remains READY. No source, test, normative specification, native-boundary, implementation-version or license-term change.

- Publish the explicitly approved D046 re-evaluation as specification revision `0.1.384`. Retain eager `Filesystem.entries(path) -> Future<Array>` for Core v0.1 while explicitly deferring a future incremental facility; reaffirm `Filesystem` rather than a new public Directory/DirectoryEntry authority family; and clarify that a successful captured Filesystem is immutable/read-only but is not made a standard `Closable` receiver and imposes no programmer-managed release obligation. Preserve exact-name/no-follow-kind/unique-result, unspecified-order, non-point-in-time capture and capture-verify-use semantics. Add I024-A2 as the required post-reevaluation compatibility audit and block I024-B on it; B009 remains READY. Specification/design/project-state only: no runtime/source, implementation-version, native-boundary or license-term change.

- Open AUD001 — Retrospective design-decision ratification audit as HIGH-priority non-normative governance work covering D001-D045 while explicitly excluding separately reviewed D046. Record D037/D038 as already owner-confirmed; prioritize D045-D039 and D020 where recovered evidence does not yet demonstrate explicit owner selection; require provenance/substance review for D021-D036 and evidence rather than assumption for D001-D019. Patch execution and publication are not ratification. AUD001 authorizes no automatic reopening, normative specification change, implementation change, blocker transition, or replacement decision.

- Publish `TOOL002-F3C1B3 — stored observation evaluator composition`. Bundled `Runner.protos` composes the already-closed exact `future-observation-error-identity / stored:Error` classifier with one positive same-Process `executionInspect`-compatible inspector: the inspector reads only retained local `error`/`observe`, invokes `observe()` once, and returns inert Boolean evidence for exact caught-versus-stored Error identity. A Protos-owned fixture passes the retained CaseSpec, retained source and bootstrap-local `executionInspect` through the evaluator. `isDExpectation`, public `runSimple`, fresh mode, stored negatives and canonical TOOL002 ledgers remain unchanged until their later slices. No normative specification or Java host-mechanism change.

## 0.2.249-SNAPSHOT

- Add an explicit user design-approval gate and replace generated patch launchers' clean-caller-checkout requirement with isolated local worktree publication. Agents must research, compare, falsify, and recommend substantive design choices without self-selecting or closing them; publication launchers must build/validate on a temporary local branch rooted at current `origin/main`, rebase/revalidate on concurrent advancement, fast-forward push only the validated commit to `main`, and remove their temporary local worktree/branch on success or failure while leaving caller state untouched. Governance/documentation only: no normative Protos semantic, implementation-version, runtime, native-boundary, or license-term change.

- Close `DOC001-L — Process, I/O, Filesystem/File capabilities, and authority`. Publish `docs/guide/11-process-io-filesystems-and-authority.md` with the programmer-facing authority model for bootstrap-local Process and optional Filesystem capabilities, canonical args/environment snapshots, independently optional byte standard streams with explicit Encoding, explicit borrowing/owning TextReader/TextWriter composition, ordinary-Future I/O with commitment/cancellation and lifecycle cutovers, bounded byte read/write ordering/backpressure, structural authority-free Path values, explicit Filesystem open configuration, stable File resource binding/capability shape, positioned/append/durability distinctions, namespace replace/remove, and resource ownership. Explicitly record that D046 `Filesystem.entries`/`captureTree` are normative at spec 0.1.383 but not yet current runnable reference behavior while I024 remains IN_PROGRESS. Cross-link the Path tutorial plus existing bundled-tool Protos consumers and current I013-I017/I021 implementation evidence. Documentation-only: no runtime, test, library, normative specification, implementation version, native boundary, or license-term change.

- Close `DOC001-K — Actors and Actor Groups`. Publish `docs/guide/10-actors-actorrefs-and-groups.md` with the programmer-facing model for Actor-private mutable state and serialized execution, module-backed `Actor.spawn`, explicit Actor snapshot/capability transfer, incarnation-bound ActorRef identity, send/request/SendOperation backpressure, concrete acceptance and uncertainty/no-transparent-replay rules, Error/control isolation, graceful `stop` plus independent known-termination Futures, and fixed Core failure policy. Explain `Actor.group(...)`, stable Group identity independent of membership, communication-only GroupRef identity/transfer, one-eligible-member routing, pre-acceptance reselection, Group/Process lifetime separation, and the absence of Core broadcast/discovery/post-creation membership-control authority. Cross-link the executable `10-actors` and `11-actor-groups` tutorials plus current I011/LM005 evidence and advance DOC001-L. Documentation-only: no runtime, normative specification, implementation version, native boundary, or license-term change.

- Close `DOC001-J — Isolated parallel execution`. Publish `docs/guide/09-isolated-parallel-execution.md` with the programmer-facing model for ordinary Closure `parallel(...)`, the absence of a public P/worker object, P-local Closure projection without caller lexical/receiver/control capture, explicit argument snapshot/transfer and `NonParallelValue` authority rejection, Future/structured-ownership composition, deterministic Array `parallelMap`/`parallelFilter`/`parallelFindIndex`/canonical `parallelReduce`/stable `parallelSort`, and exclusive Bytes/ByteRegion `parallelRange` writable partitioning. Reconcile the stale detailed DOC001 E-I rows discovered during the current-main status audit while closing J and advancing K. Documentation-only: no runtime, normative specification, implementation version, native boundary, or license-term change.

- Close `DIST001 — End-user distribution and release engineering` after the explicitly authorized direct publication of `Protos 0.2.236`. Reconcile D4-D6, E4, E5 and E6 from observed public evidence: lightweight tag `v0.2.236` resolves exactly to candidate `957b1e16793a682de1d6406e37b5734c44d32d19`; the GitHub Release is a non-draft pre-release; final candidate Maven and envelope verification passed; and re-downloaded `protos-0.2.236-posix-jvm.zip`, its basename-only checksum and `RELEASE_MANIFEST.txt` match the frozen SHA-256 identities. Persist the publication record while leaving later main documentation/implementation work independent of the 0.2.236 release. Documentation/project-state only; no runtime, specification, implementation-version or license change.
- Close `DOC001-I — Futures and structured concurrency`. Publish `docs/guide/08-futures-and-structured-concurrency.md` with the programmer-facing model for Closure `future()` execution, fresh Future identity, pending/resolved/failed/cancelled state, `value()` suspension and Error observation, Future adoption/flattening, asynchronous `then` continuations, deterministic `Future.all`, the intentional absence of generic scheduler-timing race/select, cooperative cancellation boundaries, task-scoped structured ownership, non-propagating child outcomes, and `detach()` as ownership-edge removal rather than Actor-independent lifetime. Cross-link the executable `09-futures` tutorial and retained Future conformance evidence and reconcile DOC001 navigation/status. Documentation-only: no runtime, normative specification, implementation version, native boundary, or license-term change.

- Close `DOC001-H — Errors, handlers, ensure, and resource lifetime`. Publish `docs/guide/07-errors-handlers-ensure-and-resource-lifetime.md` with the programmer-facing model for ordinary Error objects and fresh standard failure occurrences, exact-object non-resumable `signal()`, delegation-based dynamic `Error.handle`, innermost selection and selected-frame deactivation, task-local handler scope, standard Closure `ensure`, exact-once cleanup across normal/return/Error/cancellation exits and suspension, later-cleanup-transfer precedence, cancellation-safe cleanup, explicit asynchronous release, and the absence of deterministic GC finalization. Cross-link retained handler/ensure conformance plus ordinary-Protos filesystem/package-tool usage and reconcile DOC001 navigation/status. Documentation-only: no runtime, normative specification, implementation version, native boundary, or license-term change.

- Close `DOC001-G — Modules and imports`. Publish `docs/guide/06-modules-and-imports.md` with the programmer-facing model for moduleContext top-level state, ordinary eager `import(...)`, semantic-String specifier validation, specifier/ModuleKey/module-instance separation, canonical-key identity, Actor-local caches, cache-before-execute cyclic initialization, partial module visibility, failed-initialization eviction/retry, initial-module identity, and the boundary between Core module lifecycle and resolver policies such as `std:`/`self:`. Cross-link current Standard Library/package-tool import examples, module runtime evidence, and reconcile DOC001 navigation/status. Documentation-only: no runtime, normative specification, implementation version, native boundary, or license-term change.

- Close `DIST001-E4D3 — candidate claims/blockers/spec audit`. Independently re-audit the exact frozen 4-capability/5-limitation claim set SHA-256 `ad0b77ae5bd41bc16e306487072620c99581643d68ed5141011e7537c0439e33` against detached candidate `957b1e16793a682de1d6406e37b5734c44d32d19`, never moving main: require candidate parent `3c23eaaccecbdcc7c2bcd86bc30c445403cfb047` and only `pom.xml` candidate delta; parse B001-B008 independently as CLOSED; require newest global spec revision `0.1.382`, D043/D044/D045 and relevant v0.1 Draft owners; and verify candidate-time portable CLI/Core/control/concurrency/standard-facility capabilities plus experimental/runtime/tooling/guide limitations. Persist `d3_claims_blockers_spec=PASS` and make E4D4 READY. No B5 repetition, Maven run, tag/Release collision check, runtime/spec/implementation/license change or publication authorization occurs.
- Start `I024 — Filesystem directory observation + captured-tree capability` with `I024-A`. Add host-neutral `ProtosFilesystemTreeObservationFlow` for D046 without publishing `Filesystem.entries` or `captureTree`: non-Path arguments fail before backend authority, each invocation owns an independent Future/Actor/cancellation lifecycle, child observations are exact-name/no-follow-kind DTOs copied defensively with duplicate-name rejection, and backend/materialization failures become ordinary `IOError`.
- Model a successful captured tree as opaque host-neutral custody plus a release callback. Cancellation, materialization failure, or another terminal result that wins before transfer releases custody; successful transfer performs one atomic producer-side commit/resolve cutover and adds no source-side effect or Filesystem-wide sequencing.
- Subdivide I024 into A host-neutral flow, B standard Filesystem public materialization, C secure NIO + immutable captured backend, and D integrated Protos conformance/B009 closure. B009 remains READY and TOOL001-F2E2 remains dependency-blocked on I024. No specification or native-boundary change.

## 0.2.248-SNAPSHOT

- Close `DOC001-F — Values, identity, equality, and collections`. Publish `docs/guide/05-values-identity-equality-and-collections.md` with the programmer-facing model for canonical null/Booleans, the closed Number/String/singleton value-identity set, non-overridable `===` versus customizable strict-Boolean `==`, hashing, indexed `at`/`atPut` syntax distinct from slots, Core Array/Map/IdentityMap/Bytes semantics, snapshot iteration, and the Core-versus-Standard-Library boundary for `std:collections/Array`, `Set`, and `IdentitySet`. Cross-link the runnable Map and IdentityMap tutorials plus ordinary-Protos collection modules and reconcile DOC001 navigation/status. Documentation-only: no runtime, normative specification, implementation version, native boundary, or license-term change.

- Close `DOC001-E — Control flow through ordinary protocols`. Publish `docs/guide/04-control-flow-through-protocols.md` with the programmer-facing model for lazy Boolean `ifTrue`/`ifFalse`/`and`/`or`, ordinary trailing-Closure arguments, standard Closure-specific `Object.while`, strict canonical Boolean loop decisions, ignored body values, normal `null` completion, ordinary lookup/shadowing, Error/non-local-return transfer, suspension replay, and the body-Future boundary. Cross-link representative executable conformance evidence and reconcile guide navigation plus DOC001/I023/B007 project ledgers. Documentation-only: no runtime, normative specification, implementation version, native boundary, or license-term change.

- Open `DIST002 — Development/release toolchain alignment` as deferred `OPEN` release/build-environment work. Record the current intentional Java 21 bytecode target but accidental primary-runtime split between the GraalVM JDK25 devcontainer and DIST001's frozen GraalVM JDK22/Truffle24 release contract. Require one authoritative toolchain coordinate source, aligned devcontainer/ordinary CI/release validation, explicit runtime migrations, and pre-provisioned primary runtimes so normal validation gates do not download JDKs on demand. DIST002 is explicitly not a dependency of the already-frozen 0.2.236 candidate and starts after DIST001. Documentation/project-state only; no runtime/specification/version/license/publication change.

- Publish D046 / specification revision `0.1.383` to resolve the normative part of B009 with general capability-confined `Filesystem.entries(path)` and `Filesystem.captureTree(path)`. Entries expose exact no-follow child name/kind descriptors; captureTree returns a fresh immutable read-only captured Filesystem. The capture need not represent one atomic source instant: verify-then-use hashes and consumes the same captured capability, avoiding source-path TOCTOU without a package-specific Java tree walker. B009 moves BLOCKED -> READY, I024 becomes READY, and TOOL001-F2E2 moves to BLOCKED_BY_DEPENDENCIES on I024. No runtime/source, implementation version, license term or native-boundary change.
- Close `DIST001-E4D2 — extracted release-aware B5 candidate gate`. Run the already-published E3C1 B5 surface once against exact detached candidate `957b1e16793a682de1d6406e37b5734c44d32d19` and immutable `protos-0.2.236-posix-jvm.zip` SHA-256 `b1a58ba445d082156bd4eb637ee6df70c046abdee600d468c0fac29be065e296` in explicit public-prerelease / clean-source mode. Require B5 archive identity, outside-checkout Package Tool, bundled Test Tool and exact optimizing GraalVM JDK22/Truffle24 checks to pass without modifying the archive. Persist `d2_release_b5=PASS` and make E4D3 READY. D3 retains claim/blocker/spec truth audit and D4 retains tag/Release collision/publication guards. No repository executable, implementation/spec/runtime/license contract, tag, GitHub Release, asset upload or release-publication authorization changes.
- Open `PERF004 — Cross-language runtime performance characterization` as a deferred `OPEN` performance work item. Plan A correctness-first Python/JavaScript/Java algorithm-equivalent baseline, B attribution only for material measured gaps, and C a ranked 3–5 item optimization-priority output. Opening PERF004 does not start measurements or authorize a Protos optimization; it reuses PERF001 methodology and PERF003's separation of compiler diagnostics from actual timing. Documentation/project-state only; no runtime, specification, implementation-version, native-boundary, or license-term change.


- Close `TOOL001-F2E1C` and parent `TOOL001-F2E1` without production or implementation-version change. Publish fixed `protos-package-tree-v1` conformance streams/digests cross-computed by two one-off Python oracles and verified by a separate JDK-only Java test oracle, covering ordering, binary/empty content, varuint boundaries including >64-bit, framing, exact case, mutations, invalid paths/reserved names/case collisions, special-entry rejection, and empty-directory/metadata/store-layout non-semantics. Reconcile the older identity audit so canonicalization is no longer described as open. The required post-E1 audit confirms Core Filesystem still lacks portable confined directory enumeration/entry-kind/stable-snapshot semantics; allocate B009 and mark F2E2 BLOCKED rather than adding a package-specific Java tree walker. No normative specification or native-boundary change.
- Publish `TOOL002-F3C1B2A4P2 — standard Object.slotValue reflection prerequisite`. The F3C1B2 retained-inspector progression reached local-slot value reading and exposed that the already-normative `Object.slotValue(name)` selector remained absent. Implement only that general Core operation: require one semantic String, read only an ordinary receiver's local binding through `readLocalSlot`, return the exact stored value, and signal ordinary Error when the receiver/name has no such local binding.
- Add Protos conformance for exact stored identity, no delegation, invalid name and represented-value absence. Reconcile the native-boundary inventory from Object 6 to 7 sites and Core 112 to 113 sites. `slotNames` and `removeSlot` remain out of scope; Runner, `executionInspect`, Future policy, canonical TOOL002 ledgers and specification are unchanged.

## 0.2.247-SNAPSHOT

- Close `DIST001-E4D1 — envelope/audit/record consistency`. Independently cross-check the frozen selection, candidate artifact, C3 claims, C4 envelope and C5 audit against candidate `957b1e16793a682de1d6406e37b5734c44d32d19` and verify exact candidate-local archive/envelope/audit bytes: archive `b1a58ba445d082156bd4eb637ee6df70c046abdee600d468c0fac29be065e296`, claims `ad0b77ae5bd41bc16e306487072620c99581643d68ed5141011e7537c0439e33`, notes `98684922feebb1cec41da2a51fca6776cd76aa5c99561b37adb733e5b944ed36`, manifest `2d71e48bf27e52d76bd4bd9166dca4298487532b1de08e832758c7f2abe7dcd4`, checksum file `34b2d9a86c0e136ac2f9d92c8edf94563b9ea941c896f54eddce929680969035` and audit `0f3ea9a321a462e977f4f33a2b4c24754a5cacbd8e45e0df8bc6639d4286692b`. Require clean detached candidate state, exact E3B three-file envelope, basename-only portable checksum and byte-identical main/local audit. Persist the D1 PASS/PENDING D2-D6 validation ledger and make E4D2 READY. No B5 execution, claim-truth re-audit, tag/Release collision check, Maven run, implementation/spec/runtime/license change or publication authorization occurs.
- Close `DIST001-E4C5 — candidate-audit materialization` and parent E4C. Materialize the exact E3C3 audit for candidate `957b1e16793a682de1d6406e37b5734c44d32d19` with explicit candidate selection authorization, specification `0.1.382`, PASS capability/limitation/blocker reviews and `release_publication_authorized=false`; retain byte-identical main and candidate-local audit copies with SHA-256 `0f3ea9a321a462e977f4f33a2b4c24754a5cacbd8e45e0df8bc6639d4286692b` and validate them using the published E3C3 audit verifier. Decompose E4D into D1-D6 and make only D1 READY. No full B5 candidate gate, tag, GitHub Release, asset upload, implementation/spec/runtime/license change, or publication authorization occurs in this slice.
- Close `PERF003 — Collection algorithm Truffle compilability` by reconciling retained A4g/A4h/A4i evidence. Exact result `528` remains correct; the stable control is `50681:150026:150000`; sync/task splitting and preparation-only boundary are rejected as sufficient fixes; preparation-only timing is `0.578142x` control despite retaining the bailout. Retire the zero-bailout gate for PERF003 rather than publish a broad opaque boundary or continue threshold-shaving edits. No runtime, specification, implementation-version, native-boundary, or license-term change.


- Close documentation-only `TOOL001-F2E1B — canonical byte stream + method/hash contract`. Serialize the E1A finite path->bytes map in exact ASCII path-byte order as one domain-separated binary stream: `protos-package-tree-v1` + NUL magic, tagged FILE records, canonical minimal arbitrary-precision unsigned base-128 path/content lengths, exact path/content bytes, and one END tag. Empty files are explicit zero-length contents; directories/metadata/per-file hashes remain absent. Define current ContentIdentity generation/verification as `protos-package-tree-v1 sha256:<64-lowercase-hex>`, where sha256 is standard SHA-256 over the complete stream; method and algorithm tokens remain orthogonal for future evolution. E1C independent vectors/closure becomes READY. No source, specification, implementation-version or native-boundary change.
- Close `DIST001-E4C4 — deterministic release-envelope generation`. Consume the exact frozen E4C3 claim record SHA-256 `ad0b77ae5bd41bc16e306487072620c99581643d68ed5141011e7537c0439e33` and independently verified `protos-0.2.236-posix-jvm.zip` / `b1a58ba445d082156bd4eb637ee6df70c046abdee600d468c0fac29be065e296` through the existing E3B metadata generator. Require two byte-identical generations, retain one candidate-local `release-envelope-0.2.236` envelope, preserve all four capability and five limitation bullets exactly/in order, and pass the existing E3C2 independent envelope verifier. Persist notes `98684922feebb1cec41da2a51fca6776cd76aa5c99561b37adb733e5b944ed36`, manifest `2d71e48bf27e52d76bd4bd9166dca4298487532b1de08e832758c7f2abe7dcd4` and checksum-file `34b2d9a86c0e136ac2f9d92c8edf94563b9ea941c896f54eddce929680969035` identities; mark envelope generated and make E4C5 READY. No candidate audit, tag, GitHub Release, asset upload or release-publication authorization occurs.
- Publish `TOOL002-F3C1B2A1P1 — standard Object.hasSlot reflection prerequisite`. The F3C1B2 live-inspector audit exposed that the already-normative inherited `Object.hasSlot(name)` selector was absent from Core. Implement exactly that general reflection operation as one reviewed `ProtosStandardObjectProtocol` representation bridge: require one semantic String argument, inspect only the receiver's own local slot table, return canonical true/false, and treat opaque represented values as having no representation-owned local slots. Non-String or invalid arity signals the ordinary Error.
- Add Protos conformance for local-only/non-delegating presence, represented-value false, invalid-name and arity failure. Reconcile the executable/native architecture inventory from 111 to 112 Core construction sites and Object provider 5 to 6. `slotNames`, `slotValue`, and `removeSlot` remain out of scope; Test Tool Runner, `executionInspect`, Future policy and canonical TOOL002 ledgers are unchanged. No normative specification change.

## 0.2.246-SNAPSHOT


- Subdivide `TOOL001-F2E1 — protos-package-tree-v1 ContentIdentity` into E1A logical-tree/path domain, E1B canonical byte stream + method/hash, and E1C independent vectors/closure; close documentation-only E1A. The v1 logical tree is the already-materialized package payload, not an arbitrary checkout: every valid regular file contributes its exact path+bytes; root `protos.toml` is mandatory; directories are structural and empty directories non-semantic; symlinks/special entries are rejected; modes/timestamps/ownership/xattrs and other host metadata are excluded. Freeze a conservative ASCII artifact-path domain with slash-only separation, `.`/`..` and Windows-reserved rejection, and ASCII case-fold collision rejection. Source packaging/include-exclude policy remains separate from ContentIdentity and no `.gitignore`/ambient VCS policy is consulted by the identity method. E1B becomes READY; no source, specification, implementation-version or native-boundary change.
- Close `DIST001-E4C3 — release-note claims selection`. Freeze an ordered candidate-specific capability/limitation record for `957b1e16793a682de1d6406e37b5734c44d32d19` / `protos-0.2.236-posix-jvm.zip` SHA-256 `b1a58ba445d082156bd4eb637ee6df70c046abdee600d468c0fac29be065e296` and specification `0.1.382`. Positive claims stay within the verified portable CLI, Core object/control model, Future/parallel/Actor concurrency, and implemented standard value/I/O facilities. Limitations explicitly disclose experimental draft status, exact POSIX/GraalVM JDK22 + Truffle24 runtime scope with no bundled JDK, incomplete Package Tool/Test Tool surfaces, and incomplete Programming Guide coverage. Candidate B001-B008 blocker review passes. E4C4 becomes READY to render the deterministic envelope without changing these claims; no tag, GitHub Release, release asset or publication authorization occurs.

- Allocate `TOOL001-F2E — external immutable-package execution` after closed `TOOL001-F2D`. Close documentation-only `F2E0` with the fresh prerequisite audit and E1-E5 decomposition. The next READY work is the versioned `protos-package-tree-v1` ContentIdentity canonical tree contract; store binding, execution-plan extension, host resolver and public-run integration remain dependency-gated. Current Core Filesystem still lacks the broad portable tree observation needed by external package verification, so this checkpoint forbids a package-specific Java tree-walker escape hatch. Fetch/network remains separate from already-materialized normal execution. No implementation version, source, normative specification or native-boundary change.
- Close `TOOL001-F2D3C3B — public workspace run`, parent C3/C/F2D3 and the bounded workspace-only `TOOL001-F2D`. Publish `protos run <entry> [args...]`: the current working directory is the project root, `<entry>` is an explicit portable logical module in the root package, and neither the command nor entry is included in `process.args()`. No implicit `Main`, manifest entry field, filesystem-path interpretation or fresh dependency selection is introduced.
- Route the public command only through the closed C3A driver, therefore preserving C1 read-only Package Tool preflight, detached-plan handoff, exact self:/dep:/std: package resolution and the separately-authorized C2 application Process. The CLI supplies the existing host environment/stdio authority with exact UTF8 stream bindings and no default application Filesystem.
- Translate completed/failed/cancelled workspace outcomes into ordinary CLI exit behavior and report preflight/lock/source/entry failures as `protos run: ...`. Add real-Protos CLI integration plus C3A/C2/C1/resolver/full-suite regressions and README/help documentation. External registry/Git materialization remains later TOOL001-F2 work; no normative specification or native-boundary change occurs.

## 0.2.245-SNAPSHOT

- Close `DIST001-E4C2 — archive identity + SOURCE/RUNTIME verification`. Add an independent non-regenerating verifier for persisted `protos-0.2.236-posix-jvm.zip` / `b1a58ba445d082156bd4eb637ee6df70c046abdee600d468c0fac29be065e296`. It cross-checks the E4 selection/artifact records, exact candidate worktree `957b1e16793a682de1d6406e37b5734c44d32d19`, external SHA-256, single-root ZIP/CRC, exact public-prerelease SOURCE identity, GraalVM Community JDK22 + Truffle 24.0.0 runtime metadata, full internal SHA256SUMS coverage/values, and the shaded JAR `Implementation-Version: 0.2.236`. Persist independent verification=true and make E4C3 READY. No archive regeneration, release claims, envelope, audit, tag, GitHub Release or asset publication occurs.
- Close `DIST001-E4C1 — public-prerelease portable ZIP build`. From the already-selected clean detached candidate `957b1e16793a682de1d6406e37b5734c44d32d19`, run the existing `dist/build_portable.py --public-prerelease --release-baseline 3c23eaaccecbdcc7c2bcd86bc30c445403cfb047` path to produce exact archive `protos-0.2.236-posix-jvm.zip` with SHA-256 `b1a58ba445d082156bd4eb637ee6df70c046abdee600d468c0fac29be065e296`. Persist that minimal artifact identity in `docs/project/DIST001_E4_CANDIDATE_ARTIFACT.txt`; E4C2 becomes READY and retains ownership of independent archive/SOURCE/RUNTIME verification. No release envelope, candidate audit, tag, GitHub Release or release-asset publication is created; release publication remains explicitly unauthorized.
- Subdivide `TOOL001-F2D3C3` into C3A CLI-neutral workspace-run driver composition and C3B public `protos run` policy/wiring plus final F2D3/F2D closure; close C3A only.
- Add `ProtosWorkspaceRunDriver`, a host-only mechanical composition boundary whose explicit request supplies selected Core/Package-Tool/project roots, Standard Library resolver, root-package logical entry, and application bootstrap authority. It executes the closed C1 read-only preflight, passes only the detached `ProtosPackageExecutionPlan` into the closed C2B application boundary, and returns only `ProtosExecutionOutcome`.
- Add focused real-workspace coverage proving exact dependency-backed execution through the new driver while `protos.toml` and `protos.lock` remain byte-identical. No CLI syntax/current-directory/default-entry policy, normative specification or native boundary changes occur; C3B becomes READY.

## 0.2.244-SNAPSHOT

- Close `DIST001-E4B4 — release-only candidate lineage verification` and parent E4B. Add an independent Git-object verifier and fixture coverage that reconstruct `3c23eaaccecbdcc7c2bcd86bc30c445403cfb047` -> `957b1e16793a682de1d6406e37b5734c44d32d19` without using materialization helpers: exactly one parent, only modified `pom.xml`, byte-exact root `0.2.236-SNAPSHOT` -> `0.2.236` transition, one clean detached registered candidate worktree, no local/remote candidate refs, future tag `v0.2.236` absent locally and on origin, and release-publication flags still false. Decompose E4C into C1-C5 and make only E4C1 READY. No release archive, envelope, tag, GitHub Release or asset is created by this slice.

- Close `TOOL001-F2D3C2C — C1->C2 authority-isolation integration` and parent `TOOL001-F2D3C2` without production changes. The real integration builds a detached workspace execution plan in the separately terminated Package Tool Process, executes package-backed source in a distinct C2B application Process, proves locked dependency routing from the detached DTO, and verifies that the C1-only `projectTreeFilesystem` capability is not visible to application source. `TOOL001-F2D3C3` becomes READY.
- Close `DIST001-E4B3B2 — real candidate creation + SHA persistence`. Materialize the first real detached public-prerelease candidate `957b1e16793a682de1d6406e37b5734c44d32d19` from frozen baseline `3c23eaaccecbdcc7c2bcd86bc30c445403cfb047` by composing the published B1/B2/B3A mechanisms: only the root Maven project identity moves from `0.2.236-SNAPSHOT` to `0.2.236`. Validate the candidate before persisting its exact SHA in `DIST001_E4_SELECTION.txt`; keep the detached worktree as the temporary local reachability anchor. Close E4B3B/E4B3 and make E4B4 READY for independent release-only lineage verification. Release publication remains explicitly unauthorized; no branch, tag, GitHub Release or release asset is created, and active `main` remains on its ordinary SNAPSHOT development line.
- Close `TOOL001-F2D3C2B — detached plan -> fresh application Process wiring`. Add `ProtosWorkspacePackageApplicationExecution`: reconstruct the closed F2D3B resolver only from the selected project root plus immutable execution-plan DTO, resolve an explicit root-package entry, bootstrap a fresh application Process from copied arguments/environment and explicitly supplied standard byte streams/Encoding binding names, execute the canonical entry through C2A, and terminate the Process before returning its inert outcome.
- Keep C2B authority-neutral with respect to the Package Tool: it accepts no tool Process, activation, Filesystem or mutable Protos plan, grants no default Filesystem, and performs exact source-backed Encoding binding lookup without case folding or implicit defaults. C1->C2 authority isolation remains C2C.
- Add real-Protos focal coverage for application args/environment plus stdout Future suspension/resume and exact Encoding selection, along with C2A/resolver regressions. No normative specification or native boundary changes occur. `TOOL001-F2D3C2C` becomes READY.

## 0.2.243-SNAPSHOT

- Subdivide `TOOL001-F2D3C2` into C2A canonical initial-module execution, C2B detached-plan application Process wiring, and C2C C1->C2 authority-isolation integration/closure; close C2A only.
- Add package-neutral `ProtosCanonicalInitialModuleExecution` with cache-before-execute canonical identity, RootTask execution, READY transition and failed/cancelled cache eviction. No normative specification or native-boundary change.

## 0.2.242-SNAPSHOT

- Publish `TOOL002-F3C1B1 — stored observation expectation recognition`. Bundled `Runner.protos` adds only `isFutureStoredObservationExpectation(spec)`, recognizing exactly `future-observation-error-identity / stored:Error`. A Protos-owned fixture proves exact recognition and rejection of `fresh:Cancelled`, a wrong expected token, and another Future kind.
- Keep B1 classification-only: no `executionInspect`, no `observe()` call, no evaluator, no `runSimple` activation, no fresh-mode policy, and no canonical ledger change until B4. No normative specification or host production change.

## 0.2.241-SNAPSHOT

- Close `DIST001-E4B3B1 — candidate materialization composition + recovery/idempotency guard` and subdivide B3B before creating the real candidate. Add `dist/materialize_release_candidate.py` plus isolated Git fixtures. The helper composes E4B1 -> E4B2 -> E4B3A, safely resumes exact B1/B2 intermediate states, reuses an already-created exact detached candidate, rejects broader dirty/ref states, removes only incomplete worktrees it created itself, and keeps a completed detached worktree registered as temporary commit reachability. E4B3B2 becomes READY for the first real `0.2.236` candidate + SHA persistence. The frozen selection remains UNMATERIALIZED and publication unauthorized; no real candidate, branch, tag, GitHub Release, asset, implementation, specification, runtime, workflow or license-term change occurs in this slice.
- Close `DIST001-E4B3A — candidate-commit primitive + guards` and subdivide E4B3 into B3A commit mechanics and B3B real candidate materialization. Add `dist/commit_release_candidate.py` plus isolated Git-worktree fixtures. The helper accepts only the exact unstaged E4B2 POM transition at the selected baseline `3c23eaaccecbdcc7c2bcd86bc30c445403cfb047`, stages only `pom.xml`, suppresses local hooks/signing for the mechanical commit, and verifies the new detached commit has exactly the selected baseline as parent, changes only the exact public-version POM, leaves the worktree clean and does not create/move local branch or tag refs. E4B3B becomes READY. No real release worktree/candidate SHA, branch, tag, GitHub Release, asset, implementation, specification, runtime, workflow or license-term change occurs in this slice.
- Publish `TOOL002-F3C1A — live-result inspection host mechanism`. Extend the existing exact captured fresh-Process tooling boundary with bootstrap-local `executionInspect(source, inspectorSource)`. The selected source executes directly in the fresh initial Process activation, ordinary Actor-domain work is dispatched to cooperative idle, and inspection proceeds only when no live tasks remain. The inspector source must evaluate to one Closure, which receives the still-live source result inside the same Process/activation/domain; only the inspector terminal observation and private streams cross the existing detached boundary.
- Keep the mechanism test-neutral: no TestPlan, manifest, Future-state, stored/fresh, Error expectation, assertion, reporting, retry or timeout policy is added. Existing `execution` root-task behavior is unchanged. Generic focal evidence uses a non-transferable Closure capturing a Future: source work reaches terminal before inspection and the final scalar result alone is detached. F3C1 remains IN_PROGRESS; F3C1B becomes READY to add only Protos-owned positive `stored:Error` policy. No normative specification or Core language change.

## 0.2.240-SNAPSHOT

- Close `DIST001-E4B2 — exact candidate POM version transition`. Add `dist/transition_release_candidate_version.py` plus isolated Git-worktree fixtures. The helper accepts only an E4B1-registered clean detached worktree at selected baseline `3c23eaaccecbdcc7c2bcd86bc30c445403cfb047`, verifies project version `0.2.236-SNAPSHOT`, and changes only the root Protos Maven project token to `0.2.236`. The resulting POM must equal baseline bytes except for that token; only `pom.xml` may be dirty, nothing may be staged, and HEAD/detached state must remain unchanged. E4B3 becomes READY for candidate commit creation. This slice creates no real candidate worktree/commit, branch, tag, GitHub Release, or release asset and does not modify implementation/runtime/specification/license/workflow state.
- Close `TOOL001-F2D3C1 — read-only Package Tool preflight -> detached plan` and formally subdivide F2D3C into C1/C2/C3. Add `ProtosWorkspacePackagePreflight`: one fresh semantic bundled-Package-Tool Process receives empty args/environment, no standard streams, no default Filesystem and exactly one confined read-only `projectTreeFilesystem`; it executes the published `self:ExecutionPlan.build` path, detaches the resulting ordinary plan through F2D3A, and terminates the tool Process before returning only immutable host DTO data.
- Prove the authority/lifecycle cut explicitly: the successful workspace fixture leaves `protos.toml` and `protos.lock` byte-identical, the observed tool Process is TERMINATED with no default Filesystem grant, and a stale workspace fails closed while still terminating the tool Process. No application Process, CLI command, lock mutation, version selection, normative specification or native boundary is added.
- `TOOL001-F2D3C2` becomes READY for separately-authorized application execution; final public workspace-run wiring remains C3 and dependency-gated.

## 0.2.239-SNAPSHOT

- Close `DIST001-E4B1 — selected-baseline detached-worktree guard`. Add `dist/prepare_release_candidate_worktree.py` and isolated Git fixtures. The helper consumes the frozen E4A selection `3c23eaaccecbdcc7c2bcd86bc30c445403cfb047` / `0.2.236-SNAPSHOT` -> `0.2.236`, requires that baseline to remain in `origin/main` lineage, rejects existing/in-checkout destinations, creates only a detached local worktree, and verifies exact HEAD/version, cleanliness, detached state and unchanged branch refs. Post-create failures remove only the invocation-owned worktree. Further decompose E4B into B1-B4; E4B2 becomes READY. Also reconcile the E4 ledger parent row with the exact persisted E4A selection values. No real candidate worktree/version mutation/commit, production implementation, normative specification, implementation version, runtime contract, workflow, license term, Git branch/tag, GitHub Release, or release-asset publication change.
- Close `TOOL001-F2D3B2C — std: delegation + resolver closure`, parent B2 and parent F2D3B. Exact `std:` specifiers and `std:` ModuleKeys delegate to one explicitly selected Standard Library resolver; workspace keys remain PackageId + internal logical-module identities and bare/foreign spellings fail closed.
- Keep the two-argument resolver constructor package-only with a rejecting standard delegate so B2C does not silently select distribution authority before F2D3C. Add real-Protos std-import focal coverage plus B2A/B2B/B1B3 regressions. No normative specification, native-boundary or CLI/application-authority change. `TOOL001-F2D3C` becomes READY.

## 0.2.238-SNAPSHOT

- Close `DIST001-E4A — exact first-prerelease selection freeze`. After the published I023/B007 closure, persist the explicitly authorized exact release basis `3c23eaaccecbdcc7c2bcd86bc30c445403cfb047` / `0.2.236-SNAPSHOT` -> `0.2.236`, future tag `v0.2.236`, and specification revision `0.1.382` in a machine-readable E4 selection record. Further decompose E4 into E4A selection, E4B detached candidate materialization, E4C archive/envelope/audit preparation, and E4D immutable full validation. E4B becomes READY. Candidate source revision remains unmaterialized and release publication remains explicitly unauthorized; no implementation, normative specification, implementation version, runtime contract, workflow, license term, Git tag, GitHub Release, or release asset changes.
- Close `TOOL001-F2D3B2B — dep: edge/export routing`. Extend the exact workspace package resolver so `dep:<alias>/<public-export-name>` is resolved only relative to the importing workspace PackageId, through that declaring package's exact detached dependency alias edge, then through the target package's exact validated public export map. The target export's internal logical module is located only by the closed B1B3 source mechanism and encoded as the closed B1A `target PackageId + internal logical module` identity.
- Preserve dependency aliases and public export names as lookup relations rather than identity: different aliases/exports converging on the same target PackageId and internal logical module produce one canonical ModuleKey. Reject absent/foreign/out-of-plan importers, malformed dependency routes, unknown or wrong-case aliases, missing/wrong-case exports, direct spelling of an unexported physical target module, duplicate declaring-package aliases and edges targeting packages outside the installed plan. A dependency module's later `self:` imports remain relative to the target PackageId.
- Add Protos-source focal fixtures executed through the real Core import/module runtime: a root package imports an exported dependency facade whose implementation performs a target-local `self:` import; separate negative fixtures prove an existing physical target module cannot bypass exports and a package without the declaring edge cannot borrow another package's alias. Keep `std:` and final resolver closure for B2C. `TOOL001-F2D3B2C` becomes READY; no CLI/application-authority, normative specification or native-boundary change occurs.

## 0.2.237-SNAPSHOT

- Close `TOOL001-F2D3B2A — self: routing`. Add the first package-backed `ProtosModuleResolver` surface over the already-closed workspace PackageExecutionPlan, package-directory/source lookup, and canonical PackageId + logical-module ModuleKey codec. Root application bootstrap obtains its entry identity explicitly with `entryModule(logicalName)`; ordinary `self:` resolution is importer-relative and never invents an ambient root package when no importing ModuleKey exists.
- Resolve `self:<logical-module>` only from a canonical workspace ModuleKey whose exact PackageId belongs to the installed detached plan, validate/locate the target through B1B3, and preserve that PackageId in the returned canonical ModuleKey. Package exports are intentionally irrelevant to `self:`. Source loading accepts only canonical workspace keys backed by the current plan and reads the exact confined B1B3 source as UTF-8.
- Add Protos-source focal fixtures executed through the real Core import/module runtime for root-package and member-package `self:` imports, including a non-exported member module. Add host-boundary negatives for absent/foreign/out-of-plan importers and keep `dep:`, `std:` and bare specifiers closed. `TOOL001-F2D3B2B` becomes READY; no dependency-edge/export routing, standard delegation, CLI/application-authority wiring, normative specification or native-boundary change occurs.

## 0.2.236-SNAPSHOT

- Close `I023-D`, parent `I023`, and blocker `B007` after final cross-slice standard-`while` validation. Reconcile all 26 retained I023 Protos conformance fixtures, focused replay-retention and Core native-boundary architecture guards, and the full Maven suite. Correct stale current-count prose in `CORE_NATIVE_BOUNDARY.md`: the executable/provider inventory is 111 production `nativeClosure` construction sites across 30 Core providers, unchanged since I023-A; I023-B/C/D add no production native site. Freshly re-audit the documentation dependency and move `DOC001-E` from `BLOCKED_BY_DEPENDENCIES` to `READY` while leaving the control-flow chapter itself unpublished. No production runtime, normative specification, implementation-version, native-boundary, or license-term change.
- Close `TOOL001-F2D3B1B3 — logical module -> exact regular .protos source`. Add one host-only package source lookup over the closed immutable package-directory index. It validates the already-frozen portable internal logical module name, traverses each segment by exact stored spelling, appends `.protos` only to the final validated segment, and returns the canonical real regular source file.
- Reject ASCII case-fold ambiguity at every physical module component, wrong-case-only matches, missing/non-regular targets, and any directory or source symlink whose real target escapes the selected package root. Confinement is package-local rather than merely workspace-local, so one member cannot reach another member's source through an in-workspace symlink. In-root symlinks remain permitted and `core` remains an ordinary package logical module name.
- Add focused Java host-path tests covering root/member/nested source mapping, exact spelling, case-fold ambiguity, invalid runtime names, non-regular targets, in-package symlinks, workspace escapes and cross-package escapes. No `self:`/`dep:`/`std:` routing, import dispatch, CLI/application authority, normative specification or native boundary changes occur. F2D3B1B/F2D3B1 close and F2D3B2/F2D3B2A become READY.

## 0.2.235-SNAPSHOT

- Close `DIST001-E3C3 — candidate gate composition and E3 closure`, parent `DIST001-E3C`, and parent `DIST001-E3`. Add `dist/validate_release_candidate.py` plus composition/negative fixtures. The gate binds candidate archive SOURCE to clean checkout HEAD, independently verifies the E3B envelope, requires an exact candidate audit recording explicit user selection plus PASS capability/limitation/blocker reviews and `release_publication_authorized=false`, binds that audit to the current specification revision, rejects an existing `vV` tag locally or on origin, then executes the E3C1 public-prerelease B5 gate against the same archive. `DIST001-E4` becomes READY for a later explicit exact baseline/public-version selection decision. No concrete baseline/candidate/public version, `src/`, `protos/lib/`, normative specification, implementation version, runtime contract, workflow, license term, Git tag, GitHub Release, or release-asset publication change.
- Close `I023-C4` and parent `I023-C` with final repeated cross-phase suspension/replay closure. Add Protos Future conformance in which every one of four logical condition activations and three reached body activations creates and awaits a distinct child Future; exact result `443343016` proves all pre/post-suspension effects, child executions and observed values occur exactly once across alternating callback phases. Extend the Java-only replay-retention mechanism test to suspend in every condition and body, comparing the same active condition suspension after 8 versus 1024 completed suspending iterations and requiring identical bounded retained evaluator-event and invocation-activation counts. `I023-D` becomes READY. No production runtime, specification, implementation-version, native-boundary, or license-term change.
- Close `TOOL001-F2D3B1B2C — immutable package -> physical-directory binding` and parent `TOOL001-F2D3B1B2 — exact member-location directory binding`. Compose the closed B1B1 project/package index with the closed B1B2B confined canonical traversal into one immutable host index whose records pair each detached workspace PackageNode with its exact physical real directory.
- Bind root `location = ""` exactly to the anchored real project root and every non-root location only through B1B2B; index those immutable bindings by exact opaque PackageId and exact canonical location without basename fallback, recursive search, case folding, normalization, or source-file lookup. Missing/unbindable members fail closed before a resolver can use the plan.
- Add focused Java host-integration tests for root/member binding, exact PackageId/location lookup, root-only workspaces and missing physical members. No logical module parsing, `.protos` lookup, ModuleKey construction, `self:`/`dep:`/`std:` routing, CLI behavior, Protos-observable import semantics or native boundary changes occur. `TOOL001-F2D3B1B3` becomes READY.

## 0.2.234-SNAPSHOT

- Close `I023-C3 — cooperative cancellation / ensure composition` without a production change. Add Protos Future conformance that self-requests cancellation inside the first reached while body, completes that body, reaches the second condition and second body, and only then hits an ordinary `Future.value()` cancellation-observation boundary. Exact observer result `22110` proves `while` adds no per-iteration cancellation poll, the second callback pair is reached after the request, code after the actual observation boundary does not execute, and outer `ensure` cleanup runs exactly once during unchanged cancellation unwind. `I023-C4` becomes READY. No runtime, specification, implementation-version, native-boundary, or license-term change.
- Close `TOOL001-F2D3B1B2B — confined canonical member-location traversal`. Add one host-only traversal that consumes a canonical non-root workspace location, splits only on literal `/`, delegates every stored-name lookup to the closed B1B2A exact-child primitive, resolves every selected directory to its real path, and continues only while that real path remains under the selected real project root.
- Freeze the B1B2 symlink policy mechanically: directory symlinks are permitted only when their resolved target stays inside the real project root; escaping links fail closed. Every subsequent component is traversed from the resolved in-root directory, and the final result is the canonical real member directory. No package-index application, PackageId binding, `.protos` lookup, ModuleKey construction, resolver routing or CLI behavior is added.
- Add focused Java host-path tests for exact multi-component traversal, in-root symlink continuation, escaping-symlink rejection, malformed/non-canonical locations and invalid roots. Keep Protos-visible import conformance deferred to resolver routing. `TOOL001-F2D3B1B2C` becomes READY.

## 0.2.233-SNAPSHOT

- Close `I023-C2 — body suspension/replay exact-once closure` without a production change. Add a Protos Future conformance case with three logical condition activations and two reached bodies; each body creates a distinct task-backed child Future and immediately observes it with `Future.value()`, forcing two real cooperative suspensions. Exact encoded result `322223` proves three conditions, two pre-suspend body effects, two post-resume effects, two body activations, two child executions, and observed child results `1 + 2`. This composes D044 body-phase replay with B1 completed-callback compaction and detects duplicated/skipped body effects or accidental prior-iteration reuse. `I023-C3` becomes READY. No runtime, specification, implementation-version, native-boundary, or license-term change.
- Close `DIST001-E3C2 — independent release-envelope verifier`. Add `dist/verify_release_metadata.py` plus corruption-focused fixtures. The verifier independently binds one exact public-prerelease ZIP to the E3B envelope's exact file set, v1 manifest key set, SOURCE/RUNTIME and baseline identity, public version/tag, specification revision, archive/checksum/notes digests, basename-only external checksum content, and release-note identity with explicitly populated capability/limitation sections. Archive, checksum, notes, manifest, and extra-file tampering all fail closed. E3C3 becomes READY for final generic candidate-gate composition. No concrete baseline/candidate/public version, `src/`, `protos/lib/`, normative specification, implementation version, runtime contract, workflow, license term, Git tag, GitHub Release, or release-asset publication change.
- Close `I023-C1B` and parent `I023-C1` with repeated real condition suspension across completed loop iterations, without changing production code. The new Protos Future conformance case creates a fresh child Future inside every logical `while` condition activation and immediately observes it with `value()`, forcing three separate cooperative suspensions. Exact encoded result `33236` proves three pre-suspend condition effects, three post-resume effects, two body activations, three distinct child executions and observed child results summing to six. This composes C1A replay stability with B1 completed-callback compaction and detects duplicated, skipped or accidentally reused callback work. `I023-C2` becomes READY. No runtime, specification, implementation-version, native-boundary or license-term change.
- Close `TOOL001-F2D3B1B2A — exact direct-child directory lookup`, the first sub-slice of B1B2. Add one host-only primitive that enumerates an already-selected parent directory and accepts only a child whose stored filename String exactly equals the already-separated workspace location component and whose target denotes a directory.
- Preserve the normative component boundary mechanically: B1B2A never feeds the component to host path parsing, performs no case folding/Unicode normalization, and rejects empty/`.`/`..` plus `/` because `/` belongs to the workspace-location splitter. It deliberately does not call `toRealPath()` or decide symlink confinement; B1B2B owns canonical multi-segment traversal and in-root real-path enforcement.
- Add focused Java host-path tests for exact spelling, missing/non-directory children, invalid single-component inputs and non-directory parents. No PackageExecutionPlan-wide binding, `.protos` lookup, ModuleKey construction, resolver routing, CLI behavior, Protos observable import semantics or native boundary changes occur. `TOOL001-F2D3B1B2B` becomes READY.

## 0.2.232-SNAPSHOT

- Close `DIST001-E3C1 — release-aware B2/B5 identity and conformance plumbing`. Keep development `V-SNAPSHOT` verification as the default while extending `dist/verify_portable.py` and `dist/validate_portable.sh` with an explicit public-prerelease mode. Release mode requires public `V`, clean exact candidate `HEAD`, exact `V-SNAPSHOT` baseline/version/ancestry and coherent E3A SOURCE release metadata before the existing B3 caller-CWD/Package Tool, B4A Test Tool and B4B exact optimizing-runtime checks run against the same immutable ZIP. Add isolated public-mode identity/provenance regression tests and further decompose E3C into C1-C3; E3C2 becomes READY. No concrete baseline/candidate/public version, `src/`, `protos/lib/`, normative specification, implementation version, runtime contract, workflow, license term, Git tag, GitHub Release, or release-asset publication change.
- Close `I023-C1A — single condition suspension/replay exact-once evidence` without a production change. Add a Protos-source Future conformance case whose first reached `while` condition increments state, suspends at ordinary `Future.value()`, resumes the same logical activation, performs its post-resume effect exactly once, runs one body, and then completes a second false condition. The encoded result must be exactly `221`, so replay that duplicates the pre-suspension condition effect cannot pass. Allocate C as C1 condition replay, C2 body replay, C3 cancellation/ensure, and C4 repeated-suspension/bounded-state closure; C1B becomes READY. Re-run the temporary Java owner of `future-*`, the D4 non-Future ownership guard, the while replay-compaction focal, and the full suite. No runtime/specification/implementation-version/native-boundary/license-term change.
- Close `DIST001-E3B — release notes and asset/checksum manifest envelope`. Add deterministic `dist/prepare_release_metadata.py` plus isolated fixtures. The generator accepts an already-built clean public-prerelease ZIP, derives release/source/baseline/runtime identity from its own SOURCE/RUNTIME metadata, requires an explicit numeric specification revision and explicit capability/limitation claims, and emits `RELEASE_NOTES.md`, `RELEASE_MANIFEST.txt`, and a basename-only portable archive `.sha256`. The manifest records archive, checksum, and notes digests for E3C verification. E3C becomes READY. No concrete baseline/candidate/public version, `src/`, `protos/lib/`, normative specification, implementation version, runtime contract, workflow, license term, Git tag, GitHub Release, or release-asset publication change.
- Close `DIST001-E3A — release-mode builder and provenance/version guards`. Preserve the existing development `V-SNAPSHOT` distribution path and add an explicit fail-closed `--public-prerelease --release-baseline <exact-sha>` mode. Generic release identity logic requires a clean public `V` candidate, an exact baseline whose `pom.xml` is `V-SNAPSHOT`, and baseline ancestry; release-mode `SOURCE.txt` records baseline/candidate revisions separately with `artifact_kind=public-prerelease`, `public_release=true`, `release_version=V`, and `release_tag=vV`. Add isolated Python fixtures for strict version mapping, development-mode regression, public metadata, dirty-candidate rejection and baseline-version mismatch. Subdivide E3 into E3A-E3C; E3B becomes READY. No concrete baseline/candidate/public version is selected; no source under `src/` or `protos/lib/`, normative specification, implementation version, runtime contract, license term, workflow, Git tag, GitHub Release, or release asset publication changes.
- Close `TOOL001-F2D3B1B1 — selected project-root anchor + detached package index` and further decompose the physical-source parent into B1B1 root/index, B1B2 exact member-directory binding, and B1B3 logical-module source-file binding. This keeps representation, workspace path traversal, and source lookup independently publishable.
- Add host-only `ProtosWorkspacePackageProjectIndex`: anchor the caller-selected project root as normalized + real directory state and index the already-detached immutable PackageExecutionPlan by exact opaque PackageId and exact location String. Reject empty/duplicate PackageIds, duplicate locations, empty package sets, unknown lookups and disagreement between the plan root ref and the unique empty root location. Do not traverse non-empty member locations in this slice.
- Add focused Java tests for the host Path/DTO index boundary, including deterministic root/package/location lookup and fail-closed missing/non-directory project root, duplicate/mismatched plan index shape, while explicitly proving member-location traversal is deferred. No module source lookup, resolver, `self:`/`dep:`/`std:` routing, CLI change, Protos semantic test or native Closure boundary is added. `TOOL001-F2D3B1B2` becomes READY.

## 0.2.231-SNAPSHOT

- Close `DIST001-E2 — coherent public pre-release version contract`. Select the generic mapping from an explicitly selected `V-SNAPSHOT` development baseline to public version `V`, Git tag `vV`, GitHub Release title `Protos V`, and `prerelease=true`, with POM/JAR/`protos --version`/distribution/archive/SOURCE identity all required to agree on `V`. Define an isolated candidate-commit model derived from the selected baseline so active `main` remains on its ordinary SNAPSHOT development sequence; record baseline and candidate revisions separately and forbid unrelated implementation changes in the release transition. `DIST001-E3` becomes READY to implement generic release metadata/assets/validation machinery. No concrete baseline, candidate, public version, executable, workflow, normative specification, implementation version, license term, Git tag, GitHub Release, or release asset is changed or selected.
- Close `I023-B2D2`, parent `I023-B2D`, `I023-B2`, `I023-B`, and `B008` after D045/spec `0.1.382` resolves the task-scoped structured-ownership boundary. Add Protos-source conformance in which a `while` body returns a newly-created task-backed Future from inside an enclosing asynchronous task: the post-loop parent effect occurs before the child effect (`12`, not activation-drain `21`), while observation of the enclosing Future cannot complete until that non-detached child has terminalized. Re-run the complete non-Future main-manifest Test Tool corpus, including all B2 evidence and the JSON text-adapter overlap canary that falsified per-synchronous-activation draining. No production runtime, normative specification, implementation-version, native-boundary, or license-term change. `I023-C` becomes READY.
- Close `TOOL001-F2D3B1A — canonical workspace ModuleKey codec` and further decompose F2D3B by independently valid identity, physical-source, and routing boundaries. Add host-only `ProtosWorkspacePackageModuleKey`: canonical workspace module identity depends exactly on opaque non-empty PackageId plus the F2D1 portable internal logical module name and serializes under the workspace-specific `pkg-workspace:v1:` domain with canonical URL-safe unpadded UTF-8 base64 components.
- Keep non-identity data out of the key: dependency/export aliases, workspace member location, selected project root, checkout/cache paths and Filesystem state do not participate. Decode accepts only canonical spelling and revalidates the portable logical name. The workspace-specific domain deliberately leaves future registry/Git immutable-node identity free to use a non-colliding key contract.
- Add a focused Java host-boundary test for deterministic identity, PackageId exact/opaque round-trip, module/package distinction, domain ownership and malformed/non-canonical key rejection. This focal is Java intentionally: normative Core defines ModuleKey as an internal host-produced identity that need not be exposed as a Protos object; observable `import("self:...")` / `import("dep:...")` behavior is deferred to B2A/B2B and will prefer Protos-source conformance. No Filesystem/source loading, resolver implementation, CLI change or native Closure boundary is added. `TOOL001-F2D3B1B` becomes READY.

## 0.2.230-SNAPSHOT

- Close `DIST001-E1 — first pre-release readiness and candidate envelope`. Record that DIST001-A/B/C/D provide sufficient release-engineering infrastructure to begin bounded pre-release preparation, while explicitly leaving both the candidate source revision and public pre-release version UNSELECTED. Persist the E1-E6 execution plan, exact candidate eligibility gate, current limitation/claim boundary (including open I023/B008), selected GraalVM JDK22 + Truffle 24.0.0 runtime scope, and the rule that E1-E3 cannot create or authorize a public tag/GitHub Release. `DIST001-E2` becomes READY for the coherent public-version contract. No normative specification, implementation version, executable, workflow, license term, Git tag, GitHub Release, or release asset changes.
- Resolve B008 normatively with D045 / specification revision `0.1.382`: structured ownership is task-scoped at asynchronous execution boundaries, not per ordinary synchronous Closure/method activation. Returning/storing/wrapping a pending task-backed Future does not wait, detach, transfer, re-parent or otherwise change its ownership edge; distinct async child tasks own their own descendants and the enclosing async owner drains non-detached children only at its own terminal completion.
- Reconcile `Future.then`, P, `Future.detach()`, D043 `ensure`, D044 `while` and the README summary with that single rule. `while` still ignores body Future results without implicit await/adopt/flatten/cancel/detach/re-parent/scheduling; B008 moves BLOCKED -> READY and I023-B2D2 becomes READY for implementation/conformance closure. No runtime, implementation-version, license-term, syntax or Future state/identity change.

- Close `DIST001-D2B — observed repaired CI snapshot artifact closure`, parent `DIST001-D2`, and parent `DIST001-D`. `Distribution snapshot` run `34101588533` for exact source `994429173b6ec0fc086f307f4a49815f219c6523` completed successfully through the full Maven suite, portable build, complete B5 gate, portable checksum preflight, and artifact upload. Artifact `protos-snapshot-994429173b6ec0fc086f307f4a49815f219c6523` (id `10010752033`, Actions digest `sha256:ada6593e40efee2e78981d0d0881817b5061250db6dbbd8946ad93b786e049f4`) was downloaded and independently inspected: it contains `protos-0.2.230-SNAPSHOT-posix-jvm.zip` plus its `.sha256`; the checksum records only the ZIP basename, matches `f66f011ba9a7c579f81b5aad7098bd4ec421ebc8117b3c4c8954374441e94337`, and passes `sha256sum -c` outside the runner workspace. `DIST001-E` becomes READY for bounded prerelease-readiness work, but no tag or GitHub Release is authorized without a later explicit exact-candidate user decision. No normative specification, implementation-version, executable, workflow, or license-term change.
- Record `B008 — Structured ownership when a task-backed Future escapes an activation` after I023-B2D2 adversarial work exposed a genuine normative ambiguity rather than a loop-runtime defect. Current Core text simultaneously permits ordinary functions/P to return Future-shaped results owned under creating-activation structured rules and requires a normally completing owner to wait for every non-detached child, but does not define the ownership/lifetime transition when the created task-backed Future is itself the activation's exact returned value.
- Mark `I023-B2D2` BLOCKED on B008 and keep I023-B2D/B2/B in progress. D044/B007 remain unchanged: `while` still ignores a normal body Future result without implicit await/adoption/flattening/cancellation; the unresolved question is the independent structured-lifetime rule supplied by Future/Task semantics. Diagnostic activation-drain experiments were never published and are not treated as normative evidence. No runtime, specification, implementation-version, native-boundary, license-term, or conformance-manifest change.

- Close `DIST001-D2A — portable external snapshot checksum repair`. Observed D1 workflow run `34100296135` for source `dc64fb8c4103b44020cd1b718d6850028f08b3a5` completed green and uploaded artifact `10010254675`, but downloaded-artifact inspection found that its external `.sha256` embedded the GitHub runner's absolute archive pathname. The digest value itself matched the portable ZIP. Generate the checksum from the archive directory so it records only the ZIP basename, and require `sha256sum -c` to pass before upload. `DIST001-D2B` becomes READY to observe a repaired real CI artifact; parent D remains open. No Git tag, GitHub Release, normative specification, implementation-version, or license-term change.
- Close `TOOL001-F2D3A — immutable host DTO + defensive plan detach` and formally subdivide the previously monolithic F2D3 handoff into F2D3A detach, F2D3B exact workspace resolver, and F2D3C command-scoped preflight/application-authority separation. This is implementation decomposition only; F2D remains workspace-only and external registry/Git nodes remain fail-closed.
- Add immutable host-internal `ProtosPackageExecutionPlan` plus `ProtosPackageExecutionPlanAdapter.detach(...)`. The adapter defensively validates the already-published generation-1 ordinary Protos ABI and recursively copies it into immutable records/Lists/Maps. PackageId remains opaque; Java does not parse manifests/locks or invent package semantics.
- Add focused F2D3A host-boundary tests over the existing F2D2 physical workspace fixture, including mutation-detachment proof and fail-closed malformed generation, unexpected shape, escaping member location and invalid runtime alias. No resolver, CLI dispatch, package selection, external materialization, lock mutation or native Closure boundary is added. `TOOL001-F2D3B` becomes READY.

## 0.2.229-SNAPSHOT

- Publish `TOOL002-F2 — child-local failed/cancelled Future terminal mechanism`. Bundled `Runner.protos` now recognizes, but still does not publicly select, `future-error`, `future-error-parent`, and `future-cancelled`. The expectation-private child envelope calls ordinary `Future.value()` twice: a FAILED Future must re-signal the same stored Error identity, while a CANCELLED Future must signal two fresh standard `Cancelled` occurrences. `future-error-parent` additionally requires the exact immediate standard Error parent. No Test-only Future state reflection or host dispatch loop is added.
- Add Protos-owned mechanism evidence over retained closed-TextReader failure, retained InvalidIOArgument Future failure, and retained pre-start cancellation, plus cross-family negative cases proving FAILED/CANCELLED are not collapsed and Error-parent matching is immediate/exact. Java only provisions existing host mechanisms. Public `runSimple` remains unchanged until F4; F3 becomes READY for `future-observation-error-identity`. No Core/runtime/specification change.

## 0.2.228-SNAPSHOT

- Close `DIST001-D1 — CI snapshot workflow definition`. Add `.github/workflows/distribution.yml` for `main` pushes and explicit manual dispatch. The job selects exact GraalVM Community JDK 22.0.0, runs the full Maven suite, builds a clean portable ZIP, executes the complete B5 extracted-distribution gate, writes an outer SHA-256, and uploads the ZIP/checksum as a 14-day `protos-snapshot-<full-source-sha>` GitHub Actions artifact using `actions/upload-artifact@v7`. D remains IN_PROGRESS until D2 observes a real green run and matching artifact. No binary is published as a GitHub Release, and no Git tag, normative specification, implementation-version, or license-term change is made.
- Publish `TOOL002-F1 — child-local resolved Future expectation mechanism`. Bundled `Runner.protos` now recognizes, but does not yet publicly select, `future-integer`, `future-null`, and `future-boolean` for an expectation-private child-source envelope. The retained case source creates its Future inside the same fresh semantic Process; ordinary `Future.value()` performs all pending-work suspension/resume there, and the D1 detached boundary receives only canonical Boolean evidence. Live Future transfer remains forbidden.
- Add Protos-owned mechanism evidence over retained `future/then-transforms.protos`, retained `future/closure-future-null.protos`, an inline Boolean Future, mismatch, non-Future and failed-Future cases. Java only provisions the existing Test Tool Prelude, confined corpus Filesystem and exact execution capability. `runSimple`/D4 selection is deliberately unchanged until F4. Subdivide remaining TOOL002-F into F2 terminal failed/cancelled identity, F3 observation-error identity, and F4 activation/Java ownership cutover. No Core/runtime/specification change.

## 0.2.227-SNAPSHOT

- Close `DIST001-B5 — cross-slice distribution closure` and parent `DIST001-B`. Add `dist/validate_portable.sh` as the single reusable complete distribution gate: one exact archive first passes B2 clean-source identity/checksums, then B3 outside-checkout caller-CWD + Package Tool, B4A bundled Test Tool, and B4B exact GraalVM Community JDK22 + Truffle 24.0.0 `HotSpotTruffleRuntime`. The gate hashes the outer ZIP before/after all disposable extractions to prove validation does not mutate the artifact. `DIST001-D` becomes READY to reuse the gate for CI snapshot artifacts. No binary publication, Git tag, GitHub Release, normative specification, implementation-version, or license-term change.
- Close `TOOL001-F2D2 — pure workspace execution-state + plan construction`. Refactor `self:ResolutionRoot` into a single physical parse path: new `assembleState(projectTreeFilesystem)` returns the unchanged F2B `ResolutionRootV1` together with full root/member `ManifestV1` package records and their projected semantic manifests; the existing `assemble(...)` surface remains a compatibility projection and preserves F2C behavior.
- Add bundled-Protos `self:RuntimeNames` and `self:ExecutionPlan`. RuntimeNames implements the F2D1 portable ASCII segment/logical-module validator including case-insensitive Windows reserved-name rejection. `ExecutionPlan.build(projectTreeFilesystem)` loads one canonical lock, verifies the F2B3 resolution-input header, rejects all registry/Git nodes, reconciles exact root/workspace-member PackageIds/declarations and exact per-declaring-package path dependency alias->workspace target edges, validates/copies ManifestV1 exports, and returns a fresh inert generation-1 workspace `PackageExecutionPlanV1`.
- Add Protos-owned physical conformance for root-only/export aliases, workspace bidirectional path edges and target exports, plus fail-closed stale header, invalid runtime export name, workspace-member body mismatch, external lock node, missing locked path edge and registry-dependency graphs. Java only provisions the existing confined read-only project-tree Filesystem and executes the fixtures. No CLI, production Java/native boundary, resolver installation, lock mutation, version selection, cache scan, fetch or external materialization is introduced. `TOOL001-F2D3` becomes READY.

## 0.2.226-SNAPSHOT

- Close `DIST001-B4B — exact optimizing-runtime probe` and parent `DIST001-B4`. Add a bounded exact-runtime smoke plus a tiny Java probe. The publication launcher requires exact GraalVM Community JDK 22.0.0 before touching repository state, provisioning the official Linux x64/aarch64 archive into an external user cache when necessary and verifying the release-provided SHA-256. The smoke extracts the B2-verified bundle outside the checkout, preserves its optimizer JARs intact, passes the launcher supported-runtime gate without the override, compiles against the extracted Protos/Truffle classpath, and requires exact `com.oracle.truffle.runtime.hotspot.HotSpotTruffleRuntime` with Truffle 24.0.0. `DIST001-B5` becomes READY. No bundled JDK, Git tag, GitHub Release, normative specification, implementation-version, or license-term change.
- Close `TOOL002-E3 — Java Package/TOML ownership retirement`. Remove `ProtosPackageToolTomlSyntaxConformanceTest`, the legacy JUnit path that independently parsed `protos/tests/package-tool/toml-syntax/manifest.tsv`, interpreted retained `true`/`error` expectations and directly compiled/executed every TOML fixture. The E1A/E1B/E2A/E2B bundled-Protos path is now the sole owner of retained corpus planning, source loading, execution, expectation interpretation and aggregation.
- Retain Java tests only for host-side Test Tool mechanics such as confined Filesystem provisioning, selected bundled Package resolver execution and detached cross-Prelude observation. This is test-impact/project-state-only retirement: no production source, distributable Protos source, specification, authority boundary, language semantics or implementation-version change. `TOOL002-E4` becomes READY.
- Close documentation/design slice `TOOL001-F2D1 — PackageExecutionPlan ABI + runtime-name/preflight contract` without changing the implementation version. Define a bounded workspace-only normal-execution subset: a canonical non-stale lock must match the physical root/member PackageIds/declarations and exact path-dependency alias->target edges; registry/Git lock nodes or declarations fail closed until exact materialization plus mandatory ContentIdentity verification exists.
- Freeze the initial package-backed runtime naming policy above structural manifest schema v1. Dependency aliases are one portable ASCII segment; export keys/values and internal module names are portable `/`-separated logical names using the existing standard-resolver segment discipline (without the `std:`-specific first-segment `core` reservation). `self:<logical-module>` addresses the current package internally; `dep:<alias>/<public-export>` follows the exact locked edge then the target manifest export map, so dependency consumers cannot bypass exports.
- Define inert ordinary-Protos `PackageExecutionPlanV1` ownership and host handoff: preserve full ManifestV1 execution metadata beside F2B `ResolutionRootV1`, project workspace refs/relative locations/exports/dependency edges, then defensively validate and detach the plan into immutable host resolver state. Package Tool Filesystem/store/network authority never transfers to the application through the plan. `TOOL001-F2D2` becomes READY for pure plan construction and F2D3 remains dependency-gated.

- Close `TOOL002-E2B — Package/TOML Protos-owned expectation execution` through E2B2. `Main.protos` now executes the retained Package/TOML TestPlan with the existing bundled `Runner.runSimple`, the separate E1B `packageTomlFilesystem`, and the E2A `packageExecution` facility. No Package-specific expectation branch or resolver is added to Runner.
- Add one ordinary-Protos full-corpus integration fixture that loads the real retained Package/TOML manifest, executes every CaseSpec through the D-owned Boolean/generic-Error policy, and requires a non-empty plan, selected count equal to plan size, zero skipped cases, passed count equal to plan size, full result cardinality and `runAllPassed`. Java only provisions the existing Package Prelude and confined corpus Filesystem, then asserts canonical true.
- Keep the legacy Java `ProtosPackageToolTomlSyntaxConformanceTest` unchanged in this slice so ownership retirement remains attributable to TOOL002-E3. E2B is CLOSED and E3 becomes READY; no normative language change or authority broadening is introduced.

## 0.2.225-SNAPSHOT

- Close `DIST001-B4A — extracted bundled Test Tool smoke`. Add a bounded `dist/smoke_test_tool.sh` that extracts the B2-validated distribution outside the checkout and runs public `protos test` from a separate temporary project, requiring the Test Tool bootstrap/argument markers after successful bundled-plan execution. On a validation JDK outside the selected JDK22 contract, isolate optimizer JARs only in the disposable copy and exercise fallback Truffle, exactly as B3 does; this does not broaden runtime support. Subdivide B4 so `DIST001-B4B` remains READY for the intact optimizer classpath and exact `HotSpotTruffleRuntime` proof. No Git tag, GitHub Release, normative specification, implementation-version, or license-term change.
- Close `TOOL001-F2C — physical resolution-root assembly`. Add ordinary bundled-Protos `self:ResolutionRoot.assemble(projectTreeFilesystem)`, reading the physical root `protos.toml` plus exactly the root-declared workspace member manifests through an explicitly supplied confined read-only tree Filesystem, then projecting schema-v1 values into the already-frozen F2B `ResolutionRootV1`.
- Implement F2B2 path/workspace semantics at the physical boundary: canonical root-relative workspace member declarations, root-only workspace expansion, unique root/member PackageIds, D1 ReleaseVersion + D2 registry-constraint parsing, registry/Git dependency projection, and path-dependency `.`/`..` normalization relative to the declaring package with fail-closed root escape and target restriction to the root or an explicit member. Member-local workspace declarations are not recursively expanded.
- Reuse the already-general `ProtosNioReadOnlyTreeFilesystemBackend` only as the focal host capability; no Core/Filesystem/native API changes and no CLI-wide project-tree grant are introduced. Protos fixtures own root-only/workspace/dependency behavior plus invalid member path, duplicate PackageId, missing member, undeclared path target and root-escape failures. F2 remains open for command-scoped run-preflight, exact PackageExecutionPlan construction/handoff and later resolve/update work.

## 0.2.224-SNAPSHOT

- Publish `TOOL002-E2B1 — Package/TOML generic-error CaseSpec normalization`. Retained Package/TOML `error` rows now map to the D3A2 canonical generic-error CaseSpec representation with `expectation == "error"` and `expected == "-"`, matching `Runner.evaluateSimple` and the selected Test Tool architecture. This corrects the E1A planning-only placeholder discovered when E2B began composing that plan with the D-owned runner; no Package/TOML fixture execution is added here.
- Update the existing Protos planning fixture to assert the canonical `-` sentinel. No language semantics, resolver behavior, Filesystem authority, Runner policy, Java corpus ownership or specification text changes. E2B2 becomes READY for full retained Package/TOML plan execution and full-corpus Protos-owned evidence.

## 0.2.223-SNAPSHOT

- Close `DIST001-B3 — outside-checkout CWD and Package Tool smoke`. Add a bounded POSIX smoke that extracts the B2-validated ZIP outside the repository, creates a separate caller project, executes a relative `.protos` source, and validates caller-local `protos.toml` through public `protos package manifest`. When the validation host is outside the selected GraalVM Community JDK 22 contract, disable optimizer JARs only in the disposable extracted copy and use the documented unsupported-runtime override so B3 tests relocation/CWD through fallback Truffle rather than an unsupported optimizer/JDK pairing. Captured launcher stdout/stderr is now emitted on smoke failure. `DIST001-B4` becomes READY. No Git tag, GitHub Release, normative specification, implementation-version, or license-term change.
- Close `TOOL001-F2B3 — resolution-input digest + stale comparison` and parent `TOOL001-F2B`. Publish ordinary bundled-Protos `self:ResolutionInput` over the F2B1/F2B2 semantic `ResolutionRootV1`: exact UTF-8/LF domain-separated canonical bytes, F1B1 qstring scalar encoding, root-first/member-location ordering, alias-ordered dependency projections, D2 semantic constraint normalization and validated root/member/path/compatibility invariants. Raw TOML, source ordering, host paths, exports, package locator, caches and lock output remain excluded.
- Hash canonical semantic bytes only through closed reusable `std:crypto/SHA256`, render exactly 64 lowercase hexadecimal digits and expose the lock-header identity `protos-resolution-input-v1 sha256:<digest>`. `matchesHeader` also requires lock-format 1 and resolver-version 1. Extend F2A `self:LockFile` with read-only `isStale(filesystem, semanticRoot)`: canonical load failures remain ordinary failures; a successfully loaded but mismatching resolver/header input returns stale. No resolve/discovery/fetch/update/publication side effect occurs.
- Add Protos-owned conformance for exact canonical bytes/digest, member/dependency ordering, D2 interval spelling equivalence, compatibility participation, normalized path-target identity, header match/mismatch and fail-closed duplicate/member/path/compatibility invariants, plus a confined-Filesystem stale/fresh integration harness. `TOOL001-F2` and parent F remain open for later normal-execution lock consumption and explicit resolve/update policy after fresh audit.

## 0.2.222-SNAPSHOT

- Publish `TOOL002-E2A2B — real Package/TOML failed-fixture integration`. The exact-source facility now supplies its selected execution Prelude to the E2A2A detached snapshot for completed and failed observations.
- Add one ordinary-Protos integration fixture for retained `invalid-key-error.protos`: read through E1B `packageTomlFilesystem`, execute through E2A1 `packageExecution`, and prove a failed observation with a fresh caller-domain Error whose immediate parent is the Test Tool `Error`. Java only provisions host mechanics and asserts canonical true.
- Keep parent closure separate: E2A2B closes and E2A2C becomes READY; no full TOML TestPlan traversal, expectation-policy cutover, Java-runner retirement, authority broadening, or normative specification change.

## 0.2.221-SNAPSHOT

- Close `DIST001-B2 — clean-source archive identity`. Add a non-executing `dist/verify_portable.py` guard that opens the generated ZIP directly, validates CRC and a single safe distribution root, requires `SOURCE.txt` to identify the exact clean committed `HEAD`, and requires `SHA256SUMS` to cover every distributed file except itself exactly once with matching SHA-256 values. The definitive publication candidate is rebuilt after commit before verification. `DIST001-B3` becomes READY; no extracted Protos execution, Git tag, GitHub Release, normative specification, implementation-version, or license-term change.
- Close `LIB006-B — pure-Protos SHA-256 implementation` and bounded parent `LIB006 — deterministic hashing`. Publish ordinary Standard Library module `std:crypto/SHA256` with exactly one public module slot, `digest(bytes)`, returning a fresh 32-octet SHA-256 Bytes result. The implementation is ordinary Protos: exact non-negative Integer arithmetic, `div`/`mod`, private per-invocation fixed-32-bit helpers, standard FIPS 180-4 constants/padding/schedule/rounds and no Java `MessageDigest`, native Closure, host crypto provider, entropy, I/O or ambient capability.
- Keep Bytes handling capability-neutral and caller-safe: standard Bytes domain validation occurs without mutation, the complete input is copied before padding, the SHA-256 64-bit message-length limit fails closed, all working state is per call, and equal calls produce equal octets with fresh result identity. No public Bits/wrapping API, `digestHex`, keyed crypto, password/KDF, cipher/signature/TLS or streaming digest state is introduced.
- Add Protos-owned known-answer conformance for empty, `abc`, the standard multi-block vector, binary octets, 55/56/64-byte padding boundaries, fresh result identity, caller-input preservation and invalid non-Bytes rejection. Java contributes only the module/bootstrap harness and does not compute expected digests. With reusable `std:crypto/SHA256` now published, `TOOL001-F2B3 — resolution-input digest + stale comparison` transitions from `BLOCKED_BY_DEPENDENCIES` to READY.

## 0.2.220-SNAPSHOT

- Close documentation/governance slice `DIST001-B1 — validation hygiene and bounded smoke decomposition`. Ignore Python `__pycache__` and bytecode outputs after the DIST001-A publication launcher exposed a dirty-worktree false positive, and split the extracted-distribution gate into B2 clean-source identity, B3 outside-checkout CWD/Package Tool execution, B4 bundled Test Tool + optimizing-runtime probe, and B5 cross-slice closure. `DIST001-B2` becomes READY. No distribution behavior, binary artifact, Git tag, GitHub Release, specification, implementation-version, or license-term change.
- Publish `TOOL002-E2A2A — detached cross-Prelude standard Error taxonomy`. Add an explicit source-Prelude form of the existing test-neutral detached execution-value boundary. Source `Error` and every closed standard Error prototype are mapped by the existing Core taxonomy name to the corresponding destination-Prelude prototype before ordinary graph copying. Fresh Error occurrences retain fresh destination identity; no source Prelude prototype is shared into the destination.
- Subdivide `TOOL002-E2A2` into E2A2A mechanism, E2A2B one real Package failed-fixture integration, and E2A2C closure/reconciliation. E2A2A does not change `packageExecution`, execute a Package/TOML fixture, activate TestPlan expectation policy, or retire Java runner ownership. No normative specification change.

## 0.2.219-SNAPSHOT

- Open bounded Standard Library prerequisite `LIB006 — deterministic hashing` and close documentation/design slice `LIB006-A` without changing the implementation version. Freeze `std:crypto/SHA256.digest(Bytes) -> Bytes` as a stateless one-shot SHA-256 operation returning fresh 32-octet digest values, with no entropy, keyed crypto, password/KDF, cipher/signature/TLS surface, streaming state, Core value family or native/JVM crypto boundary.
- Select a bootstrap-safe pure-Protos implementation for `LIB006-B`: private fixed-32-bit SHA-256 helpers over exact non-negative Integer arithmetic using the already-standard `div`/`mod` primitives, with modulo-2^32 reduction and Protos-owned known-answer conformance. The private helpers do not publish a general Bits/wrapping API. `LIB006-B` becomes READY and is recorded as the concrete dependency blocking `TOOL001-F2B3`; after it publishes, F2B3 may become READY.

- Close `DIST001-A — relocatable portable distribution layout and runtime contract`. Add a constructible POSIX/JVM development ZIP under `target/distributions/` containing the shaded Protos JAR, Core/Standard Library, bundled tools, test corpus, examples, tutorials, exact source/runtime metadata and internal SHA-256 checksums. The shared `bin/protos` launcher now sets `PROTOS_HOME` without changing the caller CWD and detects checkout versus extracted-distribution layout. Distribution assembly resolves the exact PERF002-validated optimizing stack (GraalVM Community JDK 22 + external `truffle-runtime:24.0.0`) without adding that runtime to the normal project Maven build/test classpath. DIST001-B becomes READY for independent extracted execution and optimizing-runtime verification. No Git tag, GitHub Release, normative specification, implementation-version, or license-term change.

- Publish `I023-B2D1` with Protos-source conformance for the D044 body-Future boundary. A Future returned normally by a reached body is an ignored ordinary body result: a child Future that fails does not get awaited/adopted by `while` and therefore does not interrupt later loop iterations, while a separately retained successful Future remains usable after loop completion and is not cancelled or replaced by the loop. This slice deliberately leaves final structured-ownership reconciliation and cross-B2 closure to READY `I023-B2D2`; no runtime, normative specification, native-boundary, license-term, or implementation-version change.
- Publish `TOOL002-E2A1 — selected Package Tool resolver execution environment`. Generalize the existing test-neutral `ProtosExactExecutionFacility` so a host may install a named bootstrap-local exact-source executor backed by an already-selected Prelude/module resolver. The default `execution` slot remains unchanged. `protos test` now provisions `packageExecution` with the existing `ProtosBundledToolModuleResolver("package", ...)`, and every invocation still executes through TOOL002-B/C in one fresh semantic Process with private streams, empty args/environment and no child Filesystem authority.
- Prove the mechanism with ordinary Protos source: the E2A1 fixture reads the first real Package/TOML case through E1B `packageTomlFilesystem`, invokes it through `packageExecution`, and observes successful canonical `true`, demonstrating that fixture `self:TomlSyntax` resolves inside the exact bundled Package Tool environment. Do not activate the TOML plan in `Runner`, interpret the corpus as a whole, or retire the legacy Java conformance runner yet.
- Subdivide parent `TOOL002-E2A` after cross-Prelude outcome audit. E2A1 closes only the normal selected-resolver path; E2A2 remains READY to close safe failed/Error observation across the distinct Package Prelude without leaking or copying execution-environment authority. E2B remains blocked until parent E2A closes. No normative specification change.

## 0.2.218-SNAPSHOT

- Close documentation/design slice `TOOL001-F2B2 — resolution-root/workspace semantic assembly design` without changing the implementation version. Freeze one active root workspace per resolution root; canonical `/`-separated root-relative member paths over normative Path components; unique root/member PackageIds; non-recursive member workspace declarations; and path dependencies normalized relative to their declaring package but constrained to the root or one explicitly declared member.
- Define schema-v1 `compatibility.language` as an opaque exact `LanguageCompatibilityId`, with current language-generation identifier `0.1`, absence meaning no package-declared restriction and presence requiring exact equality with the active resolution context. The active language compatibility identifier is itself resolution input. Complete deterministic root/member semantic assembly is now owned without raw TOML, absolute host paths, member lockfiles or filesystem traversal order.
- Keep `TOOL001-F2B3` dependency-blocked after F2B2: the semantic input model is complete, but the repository does not yet expose an explicitly owned suitable hashing capability. Digest/stale implementation must not add a Package-Tool-private JVM/host crypto shortcut merely to close F2.

- Close `I023-B2C` with Protos-source conformance for D044 synchronous control transfer. Exact Error objects signaled by either a reached condition or body cross `while` unchanged to the surrounding handler; non-local `^` from either callback likewise reaches the pre-existing lexical return home and preserves exact returned-object identity. Callback counters prove effects before each transfer remain visible while no body, next condition, or post-loop effect executes after the transfer.
- `I023-B2D` becomes READY for the remaining normal-Future body-result and structured-ownership closure. This slice changes conformance/project state only: no runtime, normative specification, native boundary, license terms, or implementation version change.

- Formalize `DIST001 — end-user distribution and release engineering` and close documentation-only `DIST001-C` with the selected release policy: implementation `-SNAPSHOT` revisions are traceability points rather than automatic releases; CI snapshot artifacts remain distinct from GitHub Releases; public releases are explicitly selected milestone revisions and normally pre-releases while Core v0.1 remains draft; each distribution must declare and validate its GraalVM/JDK runtime contract. DIST001-A is READY. No binary artifact, tag, GitHub Release, specification, implementation-version, or license-term change.
- Close `TOOL002-E1B — confined Package/TOML corpus authority`. The bundled Test Tool now receives two independent bootstrap-local standard `Filesystem` capabilities: existing `filesystem` remains rooted exactly at `protos/tests/conformance`, while `packageTomlFilesystem` is rooted exactly at `protos/tests/package-tool/toml-syntax`. `Main.protos` constructs the E1A Package/TOML TestPlan through `Manifest.loadPackageToml(packageTomlFilesystem)` but deliberately does not execute that plan.
- Keep the host boundary mechanical: `ProtosCli` generalizes bundled-tool Filesystem installation to an explicit bootstrap slot name and provisions the existing read-only tree-confined backend twice. A Protos-owned fixture proves that the same relative `manifest.tsv` path resolves independently through the two capabilities and yields the expected distinct canonical plans. No Package Tool `self:*` fixture resolution, Package fixture execution, expectation-policy change, new Filesystem constructor, or normative language change is introduced; `TOOL002-E2A` becomes READY.

## 0.2.217-SNAPSHOT

- Close documentation/design slice `TOOL001-F2B1 — per-manifest semantic resolution-input projection design` without changing the implementation version. Freeze which manifest-v1 values affect resolution, which values are excluded, and which semantic owner must normalize each included value before stale hashing. D2 dependency constraints contribute their parsed semantic kind/bounds rather than retained source `.text`; raw TOML formatting is never digest input.
- Require future `protos-resolution-input-v1` construction to fail closed when a resolver-affecting value lacks a canonical semantic owner. Workspace member/path interpretation, canonical path-dependency identity, language/package compatibility and complete root/member assembly remain unresolved at this checkpoint, so F2B1 does not implement a digest or stale comparison. `TOOL001-F2B2` becomes READY for that root/workspace semantic assembly design; F2B3 digest/stale comparison remains dependency-gated.

- Start `TOOL001-F2 — physical lock integration` with `TOOL001-F2A — confined protos.lock read/publish substrate`. Add ordinary bundled-Protos `self:LockFile`: `load(filesystem)` reads exactly `protos.lock` through the already-provisioned confined Filesystem, decodes the complete UTF-8 stream and delegates canonical validation/model construction to closed F1C `LockDocument.parse`; `publish(filesystem, model)` first canonicalizes/structurally validates through `LockDocument.write`, then publishes through the closed B2 `MetadataPublication` transaction using `.protos.lock.stage -> protos.lock`.
- Preserve capability and update boundaries: F2A adds no host privilege, no new CLI command/dispatch, no resolver/candidate discovery, no workspace traversal, no PackageId generation, no ContentIdentity hashing, no registry/network/store behavior and no normal-execution rewrite. It also does not claim stale detection: exact `protos-resolution-input-v1` semantic digest construction remains a separate design/implementation prerequisite.
- Add Protos-owned lock-file fixtures plus a Java host-boundary harness for the confined Filesystem only. Conformance covers canonical load, non-canonical rejection, ordinary missing-file IOError, canonical publication, validation before stage creation and createNew stage-collision preservation.

## 0.2.216-SNAPSHOT

- Close `I023-B2B` with Protos-source conformance for D044 callback-activation timing. The condition and body are activated with an empty supplied-argument vector only when their semantic step is reached; ordinary left-to-right parameter binding therefore runs defaults at activation time, preserves an earlier default effect before a later missing-required failure, and never inspects or binds an unreachable body. A reached invalid body fails only after the true condition, while default-only condition/body closures execute normally under the same zero-argument activation path.
- Reconcile the four I023-B2A conformance sources with the repository-required APL Part 5 source notice after the previous slice omitted those headers. This changes notice text only and does not change APL terms. `I023-B2C` becomes READY; I023-B2 and I023-B remain IN_PROGRESS. No runtime, normative specification, native boundary, license terms, or implementation version change.

- Close `TOOL002-E1A — Package/TOML manifest planning`. Bundled `Manifest.protos` now consumes the retained two-column Package Tool TOML manifest shape as inert planning policy: safe relative fixture paths receive stable `package-tool/toml-syntax/...` CaseIds, retained `true` rows normalize to the already-owned `boolean`/`true` CaseSpec form, and retained `error` rows normalize to generic `error` without executing a fixture. Unknown expectations, wrong column counts and unsafe paths fail closed in Protos policy.
- Factor the manifest reader into one parser-parameterized bounded loader while preserving the existing three-column conformance `load(filesystem)` contract exactly. Add a Protos-owned planning fixture with Java used only to provision a temporary confined read-only Filesystem and execute the fixture harness. Formalize E1B/E2A/E2B/E3/E4 as dependency-ordered follow-up slices; E1A adds no Package Tool resolver, no second corpus authority to the public Test Tool, no test execution/cutover, and no normative specification change.

## 0.2.215-SNAPSHOT

- Close `TOOL001-F1C3 — total writer + canonical rejection + F1C closure` and parent `TOOL001-F1C`. Extend pure bundled-Protos `self:LockDocument` with the F1B3 canonical total ordering and writer: workspace members by canonical qstring bytes then PackageId qstring bytes; registry nodes by PackageId qstring bytes then D1 ReleaseVersion precedence; Git nodes by PackageId/revision qstring bytes; and dependency records by source-kind-ranked declaring ref (`workspace < registry < git`), alias qstring bytes and target ref.
- Preserve the F1C2 structural boundary as `parseStructural(text)`, while public `parse(text)` now requires exact canonical bytes by `parseStructural -> write -> byte-identical String equality`. The writer emits the one F1A header separator, root first, then workspace/registry/Git/dependency classes, one LF per body record, and no extra blank/final records. Structurally valid but non-canonical class/order input therefore fails closed.
- Add cross-slice Protos conformance for canonical root/full-document round trips, class normalization, semantic registry-version ordering (`1.2.0` before `1.10.0`), workspace/Git/dependency total ordering and canonical rejection. The existing Java harness remains unchanged. F1C is complete; TOOL001-F remains IN_PROGRESS and this slice deliberately does not allocate a new F continuation before a fresh post-F1C audit.

## 0.2.214-SNAPSHOT

- Add community entry points for adoption and external contribution: a human-facing roadmap, Code of Conduct, support and security policies, GitHub Issue Forms, and a pull-request template. Route questions, exploratory ideas, and design discussion to the now-enabled GitHub Discussions space, while keeping Issues focused on reproducible bugs and scoped actionable work. No specification, implementation-version, or license-term change.
- Close `I023-B2A` with adversarial Protos-source conformance for representative normal non-Boolean D044 condition results: `null`, Integer, ordinary `Object`, and Future all signal a fresh generic standard Error before any body activation. Two independent invalid-null failures prove fresh Error identity and direct `Error` parentage; the Future case proves `while` does not await or adopt a Future condition result.
- Subdivide the remaining synchronous I023-B2 evidence into B2B callback-activation timing, B2C Error/non-local-return transfer, and B2D body-Future/structured-ownership closure. This slice changes conformance/project state only: no runtime, normative specification, native boundary, license terms, or implementation version changes.

- Close `TOOL001-F1C2 — body record/model + structural validation`. Add ordinary bundled-Protos `self:LockDocument` over the closed F1A/F1B/F1C1 grammar. It parses complete in-memory lock-format-1 documents into explicit root/workspace-member/registry-node/git-node/dependency arrays, preserving opaque locator/authority/fetch/content fields without inventing PackageId, registry, Git or content-hashing policy.
- Reject structurally invalid graphs: missing/multiple roots; duplicate workspace declarations or workspace node identities; the root repeated as a member; duplicate registry/Git node references; duplicate `(declaring-ref, alias)` edges; dangling declaring/target references; blank body records; unknown/malformed record keywords/fields; and invalid lexical content delegated to F1C1. Record class/order canonicality is intentionally not rejected here: F1C3 owns total sorting/writing and whole-document parse-write equality.
- Reuse the existing F1C1 Java harness unchanged; all new expectations remain Protos fixtures. `TOOL001-F1C3 — total writer + canonical rejection + F1C closure` becomes READY. Filesystem I/O, resolution/discovery, PackageId generation, AuthorityIdentity authentication, ContentIdentity tree hashing, registry/network/store and update behavior remain outside F1C.

## 0.2.213-SNAPSHOT

- Add `CONTRIBUTING.md` and a public AI-assisted development policy. Explicitly welcome AI-assisted and fully AI-generated contributions while holding them to the same specification, review, validation, and contributor-responsibility standards as manually written work; require only a brief material-AI disclosure rather than prompts or model logs. Link the policy from the README and align repository agents with the same contribution rules.
- Start the executable `TOOL001-F1C — canonical lock parser/writer + round-trip conformance` parent with `TOOL001-F1C1 — lock lexical primitives`. Add ordinary bundled-Protos `self:LockSyntax` implementing the already-frozen lock-format-1 in-memory lexical layer: strict one-space line tokenization, canonical F1B1 qstring parse/render, canonical F1A header parse/render, and typed workspace/registry/git node-reference parse/render. Registry refs delegate ReleaseVersion validation to the closed D1 owner; opaque PackageId/Git revision text is not redefined here.
- Keep F1C1 bounded below body graph semantics. It does not parse root/workspace/external/dependency records, validate graph references/uniqueness, sort whole documents, access Filesystem, resolve dependencies, discover packages, authenticate authorities, hash package content, or use registry/network/store capabilities. Formalize F1C2 for body record/model/structural validation and F1C3 for total writer/canonical rejection/round-trip closure.
- Add a minimal Java execution harness only for provisioning the existing bundled Package Tool module resolver; all lexical/header/reference expectations and error cases live in Protos fixtures. The lexer is state-machine based over Bytes iteration with a per-octet state snapshot, preventing a state transition from being reinterpreted by another branch during the same octet; it avoids recursive one-call-per-octet scanning.

## 0.2.212-SNAPSHOT

- Add a non-normative licensing rationale explaining why APL-1.0 was chosen, with emphasis on welcoming open-source, commercial and proprietary applications, independent extensions and private internal use while preserving reciprocity for the Protos Licensed Work. Link the rationale from the README. No license terms, specification semantics or implementation version change.
- Close `I023-B1` after the post-I023-A longevity audit exposed iteration-proportional replay retention. Replace the accumulated completed-callback prefix with one stable per-`while` callback checkpoint: every normally completed condition/body callback commits and discards its evaluator child suffix plus completed callback activation keys, then reuses the same callback ordinal while retaining the enclosing native `while` activation. A callback that suspends remains uncommitted and replayable, so D044 observable semantics are unchanged while retained state becomes independent of completed iteration count.
- Remove `whileCompletedInvocations` and the resulting host-`int` overflow path from the dynamic frame. Add a Java runtime-invariant regression (Java intentionally, because it measures private evaluator retention rather than Protos language behavior) comparing replay event/activation counts after 8 versus 4096 completed iterations before a forced suspension. Subdivide I023-B into closed B1 and READY B2; the Core native boundary remains unchanged.

## 0.2.211-SNAPSHOT

- Close `TOOL002-D4` and parent `TOOL002-D`. Migrate retained `closure-error-parent-fresh` without transferring Closure authority: bundled Protos evaluates the retained source once inside one fresh child Process, invokes that exact candidate twice through ordinary `Error.handle`, checks both immediate Error parents plus distinct Error identity in the child identity domain, and returns only canonical Boolean evidence through the existing D1 detached observation.
- Complete the D ownership cutover. A full-corpus Protos fixture now requires every non-Future main-manifest row to be selected and pass through the bundled Test Tool and requires every skipped row to be one of the explicitly deferred `future-*` families. The legacy `ProtosLanguageConformanceTest` removes direct policy for boolean/null/integer/fixed/Float/Error/closure-fresh families and now executes only `future-*` pending TOOL002-F. TOOL002-E becomes READY. No runtime/D1 boundary or normative specification change.

## 0.2.210-SNAPSHOT

- Publish `I023-A` for D044 standard `Object.while`: add the ordinary Closure-specific selector with eager semantic Closure receiver/body validation, exact one-argument contract, zero-argument pre-test condition/body activation, strict canonical `true`/`false` decisions, ignored body results, and canonical `null` normal completion. Zero and repeated iterations are covered by Protos-source conformance; invalid receiver/body/arity cases fail through the standard generic Error path.
- Reuse the I022 replay-stable task-local control substrate for iterative callback replay. A `WHILE` frame records only the next semantic phase plus the completed direct-invocation prefix/cursor, and the evaluator tape can skip that prefix in O(1) on resume. This prevents completed condition/body callbacks from being invoked again merely to reconstruct the host stack, without adding a language-visible frame, scheduler checkpoint, Future, cleanup scope, truthiness rule, or alternate invocation model.
- Reconcile the audited Core native boundary for the one host-irreducible control primitive: `ProtosStandardObjectProtocol` grows from four to five native Closure construction sites and the Core total from 110 to 111 across the same 30 providers. `I023-A` closes, `I023-B` becomes READY, while B007 remains READY until final I023 closure.

## 0.2.209-SNAPSHOT

- Close `TOOL001-F1B3 — external node blocks + dependency edges + F1B closure` and parent `TOOL001-F1B`. Freeze lock-format-1 external records as flat single-line `registry-node` and `git-node` records over the typed F1B1 node references. Immutable registry nodes carry diagnostic/retrieval-stable `locator`, opaque `AuthorityIdentity`, and mandatory `ContentIdentity`; Git nodes carry credential-free portable `fetch` provenance and mandatory `ContentIdentity`. PackageId/version/revision are not duplicated because they already live in the exact typed node reference.
- Freeze exact dependency records as `dependency <declaring-node-ref> alias <qstring> target <node-ref>`. The complete v1 body is line-oriented with no blank lines after the F1A header separator: root, workspace members, registry nodes, Git nodes, then dependency records, all under deterministic total ordering. PackageLocator remains non-identifying; its registry-node copy is diagnostic/retrieval metadata and may remain at an older permanent alias after rename. `ArtifactDigest` is deliberately omitted from core lock-format 1; transport metadata stays registry/store-owned.
- Reconcile the lock-format audit now that F1A/F1B1/F1B2/F1B3 freeze the complete canonical v1 grammar. `TOOL001-F1C — canonical lock parser/writer + round-trip conformance` becomes READY as a pure bundled-Protos in-memory slice; Filesystem publication, graph resolution, candidate discovery, ContentIdentity tree canonicalization, registry/network/store/workspace policy and update operations remain separate. No implementation-version change is made.

- Close `TOOL002-D3C2C`, D3C2, D3C and D3: activate retained `float-bits` expectations in the bundled sequential Test Tool. D3C2A parses the exact 16-hex raw pattern, D3C2B reconstructs its portable non-NaN binary64 Float, and D3C2C requires a completed/null-error observation whose detached value matches that Float by primitive `===`, preserving signed zero and Float-family identity.
- Migrate every historical D3 test that used `float-bits` solely as an unsupported sentinel to TOOL002-F-owned `future-integer`. Reconcile the D3C2A parser and D3C2B mechanism regression tests so they assert those mechanisms remain available after activation rather than incorrectly requiring `float-bits` to stay unsupported. Add Protos-owned exact/mismatch, detached signed-zero/one-third, raw-NaN rejection and sequential selection coverage. D4 becomes READY; no runtime/D1 or normative change.

## 0.2.208-SNAPSHOT

- Close `TOOL001-F1B2 — root/workspace representation`. Lock-format 1 now represents the resolution root with exactly one `root workspace <PackageId>` record using the F1B1 typed workspace reference. A manifest-v1 workspace does not acquire a second virtual identity: the root remains the package described by the root manifest, and each additional `workspace.members` declaration is recorded as `workspace-member <declared-member-string> workspace <PackageId>`. The member string is preserved as an opaque F1B1 qstring; path interpretation and membership validation remain workspace-policy concerns.
- Freeze root-section cardinality/order only: exactly one root record, followed by zero or more workspace-member records sorted by canonical qstring bytes of the declared member string and then PackageId qstring bytes as a defensive tie-breaker. The root package itself is not redundantly emitted as a member. F1B2 does not choose external registry/git blocks, dependency-edge syntax, content/provenance fields, or final inter-block blank-line policy. `TOOL001-F1B3` becomes READY for those remaining body decisions and F1B closure. No implementation-version change is made.

- Subdivide `TOOL001-F1B — canonical lock body node/edge grammar` and close `TOOL001-F1B1 — canonical scalar strings and typed node references`. Freeze one deterministic quoted-string representation for body scalar values and identity-derived node references as typed tuples (`registry`, `git`, `workspace`) instead of delimiter-composed strings such as `pkg:<PackageId>@<version>`. This deliberately avoids coupling lock-format 1 to the still-open public textual encoding of `PackageId`.
- Keep F1B1 below body structure: it does not select root/workspace records, node blocks, field ordering, dependency-edge lines, locator/fetch/content field requirements, parser/writer behavior or PackageId's public encoding. `TOOL001-F1B2` becomes READY for root/workspace representation; F1B3 remains dependency-blocked for registry/git node blocks, fields, edges and final F1B closure. No implementation version change is made.

- Record a non-committing Package Tool architecture note for future reusable-library extraction. The schema-neutral TOML front-end (`TomlSyntax` plus generic `TomlDocument`) is a candidate for a reusable TOML library while `ManifestSchemaV1` stays package-specific. The generic Semantic Versioning parse/precedence core behind `ReleaseVersion` is also a candidate, while the Package Tool retains its restricted release-value contract (including no build metadata), dependency constraint semantics and selection/resolution policy unless independent use cases justify broader extraction. This note creates no `LIBxxx`/`TOOLxxx` item and does not block TOOL001-F.

- Start `TOOL001-F — canonical physical lockfile v1` with the deliberately small documentation/design prerequisite `TOOL001-F1A — lock header grammar`. Freeze only the exact canonical three-line header shape for `protos.lock`: `lock-format <decimal>`, `resolver-version <decimal>`, and `resolution-input <method-token> <algorithm-token>:<lowercase-hex>`, followed by exactly one empty line before body records. Header keywords use exact ASCII spelling, fields are separated by one ASCII space, decimals are canonical unsigned decimal, tokens are lowercase ASCII kebab tokens, and header lines never wrap.
- Keep the scope boundary explicit: F1A does not choose package/root/dependency body punctuation, node-key encoding, string escaping, diagnostic locator emission, artifact-digest fields, VCS/workspace records, parser/writer implementation, graph resolution or network/store behavior. `TOOL001-F1B` becomes READY for body node/edge grammar design. No implementation version change is made because this slice changes design/project governance only.

- Publish `TOOL002-D3C2B`: establish exact portable binary64 reconstruction/comparison entirely in bundled Protos on top of the D3C2A 16-hex parser. Sign/exponent/fraction are decomposed with exact Integer arithmetic; finite/subnormal/zero values use an exact at-most-53-bit `Float(Integer)` significand scaled by a logarithmically constructed exact power of two, infinities use ordinary IEEE division, and sign is applied through source-backed `Float.negated()`.
- Match reconstructed non-NaN values through primitive `===`, which preserves exact Float raw-binary64 identity including `+0.0` versus `-0.0`. Reject exponent-all-ones/nonzero-fraction raw patterns so NaN payload/sign bits remain non-portable and owned by `float-nan`. D3C2B deliberately leaves `float-bits` unsupported by the sequential runner; D3C2C becomes READY. No runtime/D1 or normative change.

## 0.2.207-SNAPSHOT

- Close `TOOL001-E2 — retained exact-version preference` and parent `TOOL001-E — local/offline version selection policy`. Add ordinary bundled-Protos `self:RetainedVersionSelection`: when an exact retained ReleaseVersion remains present among the already-known candidates and still satisfies the current closed D2 constraint/prerelease policy, preserve it even if E1 could choose a newer satisfying version; otherwise fall back to E1 fresh highest-satisfying selection.
- Keep E2 intentionally version-level and lockfile-independent. `null` means no retained version and immediately delegates to E1. A retained version that disappeared or became ineligible does not block progress; fresh selection applies. Invalid retained/candidate text fails through the existing strict ReleaseVersion parser, and no-match still fails through E1. E2 does not parse `protos.lock`, validate package/content identity, decide yank/trust/language compatibility, resolve graph closure, discover candidates or perform registry/network/store/workspace/update operations.
- Extend the existing Protos-owned package-version corpus with retained-version dominance over newer releases, missing/ineligible fallback, null-retention fresh selection, retained prerelease preservation, prerelease invalidation, exact constraints and fail-closed invalid/no-fallback cases. Final E reconciliation closes the bounded pure local version-selection parent while TOOL001 remains open for separately scoped resolver/lock work.

## 0.2.206-SNAPSHOT

- Start `TOOL001-E — local/offline version selection policy` with the bounded `TOOL001-E1 — fresh version selection` slice. Add ordinary bundled-Protos `self:FreshVersionSelection`, which receives already-known ReleaseVersion candidates, filters them only through the closed D2 DependencyConstraint policy and chooses the highest satisfying candidate by D1 SemVer precedence.
- Keep E1 intentionally below full resolver/candidate eligibility. It does not discover candidates, interpret PackageId/authority metadata, consult yank/trust/language compatibility, fetch registry/network data, inspect transitive dependency closure, preserve a lock selection, serialize `protos.lock`, or perform workspace/store/update operations. An empty/no-match candidate set fails closed so later resolver layers can own contextual dependency-path diagnostics instead of silently manufacturing absence.
- Extend the existing Protos-owned package-version corpus with exact/caret/interval selection, explicit-prerelease admission, order independence and fail-closed empty/no-match/invalid-candidate cases. `TOOL001-E1` closes and `TOOL001-E2` retained exact-version preference becomes READY.

## 0.2.205-SNAPSHOT

- Publish `TOOL002-D3C2A`: bundled Protos now parses retained `float-bits` payloads as exactly sixteen hexadecimal digits into an exact unbounded Integer raw pattern in `0..2^64-1`. Both upper- and lower-case hex digits are accepted; wrong length or any non-hex octet fails closed as Test Tool policy.
- This slice deliberately does not activate `float-bits` in `isSimpleExpectation`, execute such cases, construct Floats, compare binary64 values, or change runtime/D1. D3C2B owns the exact binary64 construction/comparison mechanism; D3C2C owns final runner integration.

## 0.2.204-SNAPSHOT

- Close `TOOL001-D2D — prerelease admission + D2 closure` and parent `TOOL001-D`. Complete ordinary bundled-Protos dependency-constraint v1 so stable requirements never admit prerelease candidates implicitly; a prerelease candidate is eligible only when the exact/caret/interval constraint explicitly names a prerelease with the same MAJOR.MINOR.PATCH tuple, after which ordinary exact/range precedence still decides satisfaction.
- Preserve the selected intent boundary across forms: `^2.0.0-rc.1` admits later `2.0.0` prereleases, final `2.0.0`, and later stable releases within the caret range but rejects unrelated future-tuple prereleases; `>=2.0.0-rc.1 <2.0.0` admits the named tuple's later prereleases while still excluding final `2.0.0`; explicit prerelease upper bounds likewise admit only that bound tuple. `^0.0.0-rc.1` admits its release-candidate train and final `0.0.0` while retaining the D2B exact-zero stable boundary.
- Promote the four D2B/D2C deferred prerelease fixtures into final boolean conformance, add cross-form Protos regressions, reconcile the version-resolution implementation checkpoint and close `TOOL001-D2`/`TOOL001-D`. Candidate selection, graph resolution, lock preservation/serialization, workspace/store, registry/network, yank and update policy remain outside this closed pure-value parent.

## 0.2.203-SNAPSHOT

- Start the subdivided `LIB004-A — Files.readAllBytes` implementation with `LIB004-A1`. Publish exact-case `std:io/Files` as ordinary Protos Standard Library source with only `readAllBytes(filesystem, path)` in this slice: explicit authority, one retained open Future, `ensure`-owned File custody/close, finite 16×65536 read windows, one fresh open whole-result Bytes, and no filesystem snapshot/reopen/probe semantics.
- Keep Core and the native boundary unchanged. Bytes allocation uses the existing source-level `Encoding.UTF8.encode("")` empty-Bytes path because Bytes is deliberately not a required public prelude binding; the operation remains binary and exposes no Encoding parameter/default. Add Protos-owned empty/freshness, multi-window ordering and open-failure conformance with Java restricted to standard-module/Filesystem fixture provisioning. A2/A3 retain the adversarial cancellation/late-open/close-precedence evidence before parent LIB004-A can close.

## 0.2.202-SNAPSHOT

- Close `TOOL001-D2C — explicit bounded intervals`. Extend ordinary bundled-Protos `self:DependencyConstraint` with exactly two whitespace-joined primitive comparisons over full ReleaseVersion values, requiring one lower (`>`/`>=`) and one upper (`<`/`<=`) bound. Conjunction order is irrelevant; stable candidates are evaluated by D1 precedence with exact inclusive/exclusive endpoints.
- Keep D2C deliberately bounded: open-ended requirements, extra comparisons and same-side pairs fail closed. Bounds may parse prerelease ReleaseVersions, but any interval satisfaction involving a prerelease bound/candidate remains fail-closed until D2D owns the cross-form prerelease-admission policy. Candidate selection, resolver, lockfile, workspace, store and registry/network behavior remain outside D2.
- Promote the D2A deferred interval fixture into a successful parse fixture and extend the existing Protos-owned package-version corpus with boundary, reversed-order, tab-separator, prerelease-parse and malformed/deferred-prerelease cases. `TOOL001-D2C` closes and `TOOL001-D2D` becomes READY.

## 0.2.201-SNAPSHOT

- Publish `TOOL002-D3C1`: bundled Protos now owns retained `float-nan` expectation policy. The expected payload must be exactly `-`; the Test Tool constructs the Core semantic Float NaN with ordinary `0.0 / 0.0` arithmetic and compares the detached completed value by primitive `===`, preserving Float-family membership while remaining independent of host NaN payload/sign representation.
- Add Protos-owned policy, mismatch, detached fresh-Process and sequential-runner focal coverage. Finite/infinite Floats and non-Float values do not match; malformed payload fails closed. `float-bits` remains skipped before source access and is isolated as TOOL002-D3C2, which becomes READY. No runtime/D1 boundary or normative specification change.

## 0.2.200-SNAPSHOT

- Close `TOOL001-D2B — caret dependency constraints`. Extend ordinary bundled-Protos `self:DependencyConstraint` with caret parsing/bound construction and stable-candidate satisfaction: `^1.4.2` is `>=1.4.2 <2.0.0`, `^0.4.2` is `<0.5.0`, `^0.0.7` is `<0.0.8`, and `^0.0.0` is exact.
- Preserve the deliberate D2 subdivision: D2B may parse a prerelease lower bound but satisfaction involving prerelease constraints/candidates fails closed until D2D owns the cross-form prerelease-admission rule. Explicit bounded intervals remain D2C; candidate selection/resolver/lockfile/network/store policy remains outside D2.
- Extend the existing Protos-owned package-version corpus with caret major/zero-major bounds, lower-inclusive/upper-exclusive stable satisfaction, exact-zero behavior, prerelease-bound parsing and fail-closed deferred prerelease satisfaction. `TOOL001-D2B` closes and `TOOL001-D2C` becomes READY.

## 0.2.199-SNAPSHOT

- Close `TOOL002-D3B2B` and parent `TOOL002-D3B`: bundled `Runner.evaluateSimple` now owns retained `error-parent` expectations entirely in Protos, resolving only the closed Core v0.1 standard Error prototype taxonomy and requiring FAILED state, canonical null value, a detached Error, and primitive-identity equality between `observation.error.parent()` and the named expected prototype.
- Preserve immediate-parent semantics rather than ancestry: an Error occurrence parented by `EncodingError` does not satisfy expected `IOError`. Normal category mismatches remain inert `passed=false` evidence, while an unknown expected Error prototype fails closed as Test Tool policy. Real D1 fresh-Process focal cases prove detached `SlotNotFound` and generic root-`Object.parent()` failures retain their standard immediate parent. The sequential runner now selects `error-parent`; the old D3B1 skip fixture moves to `float-bits`, which remains D3C-owned. D3C becomes READY.

## 0.2.198-SNAPSHOT

- Start the subdivided `TOOL001-D2 — dependency constraint language v1` with `TOOL001-D2A — exact constraints`. Add ordinary bundled-Protos `self:DependencyConstraint` with only bare full `ReleaseVersion` parsing and exact satisfaction; a bare `1.4.2` means exactly `1.4.2`, and an explicitly named prerelease means exactly that prerelease.
- Keep the slice boundary strict: caret constraints remain D2B, explicit bounded intervals remain D2C, and general prerelease-admission/cross-form conformance remains D2D. D2A adds no candidate selection, resolver, lockfile, workspace, package-store, registry/network or Core behavior.
- Extend the existing Protos-owned package-version corpus with exact parse/match/mismatch/prerelease cases and fail-closed deferred syntax checks. `TOOL001-D2A` closes, `TOOL001-D2B` becomes READY, and parent D2 remains IN_PROGRESS.

## 0.2.197-SNAPSHOT

- Start `TOOL001-D — release-version and dependency-constraint value policy` with the bounded `TOOL001-D1 — ReleaseVersion` slice. Add ordinary bundled-Protos `self:ReleaseVersion` with strict `MAJOR.MINOR.PATCH[-PRERELEASE]` parsing, no build metadata, canonical decimal core identifiers, SemVer prerelease identifier validation and exact precedence comparison.
- Preserve the package architecture boundary: D1 implements only the already-selected `ReleaseVersion` value/ordering contract. It does not parse dependency constraints, apply caret/interval/prerelease eligibility, choose candidates, read/write `protos.lock`, resolve dependencies, interpret PackageId/locator/authority strings, access network/store state or change Core semantics.
- Add Protos-owned conformance for core/prerelease structure, arbitrary-size Integer components, the canonical SemVer precedence chain, ASCII/numeric prerelease ordering and strict rejection of prefixes, missing components, leading zeroes, build metadata, malformed prerelease identifiers, whitespace and non-ASCII prerelease syntax. `TOOL001-D1` closes and `TOOL001-D2` becomes READY.

## 0.2.196-SNAPSHOT

- Close `TOOL001-C7` and the bounded historical package-tool manifest Slice 3 parent `TOOL001-C` as documentation/governance-only final reconciliation over the already-published C1-C6 implementation. Final cross-slice validation re-runs the bundled-tool/bootstrap, metadata publication, TOML/schema and manifest-command focal suites plus the complete Maven suite; no production, test, Protos source, normative specification or implementation-version change is introduced.
- Reconcile the Package Tool architecture and schema-v1 records with published reality: exact bundled tool entry selection is host bootstrap mechanism, package policy remains ordinary bundled Protos code, `protos package manifest` reads exactly confined `protos.toml`, consumes UTF-8 to EOF, validates the closed structural schema and emits Protos-owned diagnostics. Resolution/version constraints, lockfile policy, workspace interpretation, package-store/archive work, registry/networking and publication remain separate future TOOL001 scopes rather than implicit continuation of manifest Slice 3.

- Publish `TOOL002-D3B2A`, the general Core prerequisites discovered by the `error-parent` migration: implement the already-normative inherited `Object.parent()` reflection message and restore the normative standard prelude `Object` binding instead of adding a Test-only parent inspection escape hatch. The zero-argument operation reports the receiver's exact immutable immediate delegation parent, or canonical `null` only for the unique root `Object`, across ordinary and represented values.
- Centralize semantic immediate-parent projection in `ProtosValueLookup` so ordinary lookup and reflection use the same representation boundary. Add Protos conformance for ordinary/custom parents, root, Error taxonomy, Integer/Float/String/Boolean/null represented values, and arity failure. The audited Core native boundary grows by one reviewed representation-bridge construction site in the existing Object provider. `error-parent` policy itself remains D3B2B READY.

## 0.2.195-SNAPSHOT

- Close `TOOL001-C6 — confined project manifest read/diagnostics` (legacy manifest Slice 3D). Add ordinary-Protos `self:ManifestCommand`, reading exactly `protos.toml` through the already-provisioned confined Filesystem, decoding complete UTF-8 text across progress-oriented `TextReader.readText()` chunks, and invoking the closed `ManifestSchemaV1.parse` pipeline.
- Add exact bundled `ManifestMain` for `protos package manifest`. The host driver performs only mechanical exact-entry selection, while read/validation/diagnostic policy stays in Protos; the historical bare `Main.protos` entry remains byte-for-byte unchanged. Successful validation reports `protos.toml: valid schema v1`; read/UTF-8 failures and TOML/schema failures receive distinct Protos-owned diagnostics before the exact Error continues to the common tool boundary.
- Add Protos-source integrated cases for valid, invalid-schema, missing, invalid-UTF8 and multi-chunk (>8 KiB) manifests, with Java restricted to confined-Filesystem/driver integration. C6 closes and `TOOL001-C7` becomes READY. No dependency resolution, `protos.lock` read, network access, metadata mutation, package store or new Core/native boundary is introduced.

# Changelog

## 0.2.194-SNAPSHOT

- Close I022-F and the parent I022 with adversarial cross-feature evidence rather than another production control mechanism. New Protos conformance composes selected-handler deactivation with cleanup-initiated cancellation, cancellation with cleanup Error recovery, cleanup Error/non-local-return precedence with structured children, and task-local handler isolation across child Futures. Add an Actor-termination focal showing that cancellation cleanup may suspend while the Actor remains TERMINATING and that terminal Actor completion waits for the cleanup-owned task to finish.
- Re-audit the Core native boundary at I022 closure. The executable architecture guard remains exactly 109 `nativeClosure` construction sites across 30 Core providers; I022-D/E/F add none. Reconcile stale non-Core explanatory references in `CORE_NATIVE_BOUNDARY.md` to that definitive count while retaining older numerical entries only as historical progression at their named slices.
- Mark I022 and I022-F CLOSED without changing the implementation version: this is test/project/native-inventory closure over the already-published I022-A..E runtime. Clear LIB004's I022 dependency, but move LIB004 only to OPEN—not READY—because its persisted design record is still a non-normative DRAFT with no approved implementation surface. Finalize that design before implementation begins.

- Close `TOOL001-C5 — manifest schema v1` with `TOOL001-C5D` (legacy manifest Slice 3C3). Complete ordinary-Protos `self:ManifestSchemaV1` with `fromTable(root)` / `parse(text)`, preserving the published C5B mandatory base and C5C optional-section helpers while adding the final `dependencies` Map to the ordinary ManifestV1 data shape.
- Validate each dependency alias declaration as exactly one schema-v1 source form: registry requires non-empty String `authority`/`package`/`version`; Git requires non-empty String `git`/`rev`; path requires one non-empty String `path`. Mixed, incomplete, empty, non-table, array-of-tables and unknown-field declarations fail closed. Alias spelling and all decoded Strings are preserved exactly; no alias grammar, URL/revision validation, version/constraint semantics, path interpretation, network, resolution or lock policy is introduced.
- Add Protos-owned final-schema conformance for absent/empty dependencies, all three expanded forms, inline-table equivalence, malformed/mixed/incomplete declarations and cross-section failure propagation. `TOOL001-C5D` and parent C5 close; `TOOL001-C6` becomes READY for the separate confined `protos.toml` read/UTF-8/diagnostic integration.

## 0.2.193-SNAPSHOT

- Subdivide `TOOL002-D3B` into D3B1 fixed-width policy and D3B2 Error-parent policy, and close only D3B1. `Runner.evaluateSimple` now accepts `fixed-integer`, parses retained `FAMILY:value` syntax in Protos, selects one of the eight Core fixed-width factories, and matches through primitive `===` so numeric family and exact value are both preserved.
- Keep `error-parent` unsupported until D3B2. Focals cover all eight families, family/value mismatches, malformed/unknown/out-of-range policy, and runner selection that still skips D3B2 rows before source access. D3B2 becomes READY; D3C remains dependent.

## 0.2.192-SNAPSHOT

- Close `TOOL002-D3A3`: compose the D2 TestPlan, D3A1 complete source loading and D3A2 simple expectation policy into the initial sequential bundled-Protos runner. `Array.each` only constructs a dependency chain; each selected case runs in a `Future.then` continuation after the previous selected case resolves, avoiding one recursive Protos frame per case while retaining manifest order and fresh-Process-per-case execution through D1.
- Skip expectation kinds not yet owned by D3A without reading or executing their source, aggregate each selected CaseSpec plus its complete D3A2 evidence into frozen ordered run results, and preserve normal mismatches as data so later cases still run. Tool/source-policy failures still fail the chain closed. `Main.protos` now runs the supported subset while preserving its existing bootstrap stdout; reporting/exit-status policy remains later TOOL002 work. D3B becomes READY.

## 0.2.191-SNAPSHOT

- Close I022-E3 and the parent I022-E cancellation-unwind slice. Preserve the E1 invariant that only `REQUESTED` is pending for observation: once cancellation reaches `UNWINDING`, `ensure` cleanup can use ordinary Future suspension/replay without the same request being delivered again, and the task-backed producer Future remains PENDING until cleanup and required structured drain finish.
- Make cleanup precede structured cancellation drain when cancellation is first observed inside an active ensure extent. Pre-existing children still receive cancellation, but their presence no longer moves the parent to child-drain suspension before cleanup can run. At successful post-cleanup cutover, cancel every child still structurally owned, including cleanup-created unawaited children, and publish terminal CANCELLED only after that drain. Cleanup-created children explicitly awaited by cleanup may run to ordinary completion while the already-honored parent request stays shielded.
- Add Protos-source conformance for suspended cleanup exact-once progress under a repeated `cancel()` call, suspended cleanup Error supersession with exact identity, awaited cleanup-created child progress, unawaited cleanup-created child cancellation/drain, and pre-existing-child ordering. Add one Java-side focal only for the runtime-internal intermediate assertion that the producer Future is still PENDING while cancellation cleanup is suspended. No specification or native-Closure boundary change. I022-E is CLOSED and I022-F becomes READY for final adversarial cross-feature closure.

## 0.2.190-SNAPSHOT

- Close `TOOL001-C5C — optional manifest schema sections` (legacy manifest Slice 3C2). Extend ordinary-Protos `self:ManifestSchemaV1` with `sectionsFromTable(root)` / `parseSections(text)` over the published C5B mandatory base. The partial schema model now validates and materializes optional `compatibility`, `exports`, and `workspace` data while deliberately leaving `dependencies` uninterpreted for C5D.
- Enforce schema-v1 structural rules only: `[compatibility]` owns exactly required non-empty String `language`; `[exports]` is an optional user-keyed table whose values are non-empty Strings and whose empty table is valid; `[workspace]` owns exactly required Array `members`, every member is a non-empty String, duplicates are rejected, and an empty member Array is valid. Exact decoded Strings and source order are preserved; no compatibility grammar, module-name normalization, path interpretation, globbing, environment expansion, or dependency policy is introduced.
- Add Protos-owned conformance for absent/complete/empty optional sections and fail-closed table/field/value/member shapes. `TOOL001-C5C` closes, `TOOL001-C5D` becomes READY for dependency declaration forms plus final schema-v1 closure, and C6 remains dependency-blocked until C5 closes.

## 0.2.189-SNAPSHOT

- Close `TOOL002-D3A2`: bundled `Runner.evaluateSimple(spec, source, executor)` now owns single-case `boolean`, `null`, `integer`, and generic `error` expectation interpretation over the D1 detached observation boundary. Normal expectation mismatches are inert results rather than Test Tool Errors; malformed/unsupported policy still fails closed.
- Preserve exact numeric family semantics for ordinary `integer` expectations by parsing the manifest decimal in Protos and comparing with primitive semantic identity `===`; equal-magnitude Float/fixed-width values therefore do not satisfy an Integer expectation. Each result is a frozen tuple retaining both canonical `passed` and the complete D1 observation for later reporting/aggregation. `TOOL002-D3A3` becomes READY; no Filesystem/TestPlan traversal or `Main.protos` integration is added in A2.

## 0.2.188-SNAPSHOT

- Continue the cost-aware `TOOL001-C5` schema-v1 work with `TOOL001-C5B — manifest base schema`. Add ordinary-Protos `self:ManifestSchemaV1.parseBase(text)` / `baseFromTable(root)` over the closed C1-C4 parser and C5A scale prerequisite. This helper validates only the stable mandatory envelope: the complete schema-v1 root-name allowlist, required Integer `manifest-version` exactly `1`, and required `[package]` table with non-empty String `id`/`version` plus optional non-empty String `locator`.
- Keep the slice boundary explicit rather than publishing a temporary half-validator: the four known optional root sections are permitted by the base root allowlist but deliberately remain structurally unvalidated by `parseBase`; the final full-schema `parse/fromTable` API is not published in C5B. The base result exposes only the ordinary-Protos package model and performs no SemVer, PackageId lexical, locator, compatibility, export, dependency, path, URL or resolution-policy validation.
- Add 12 Protos-owned focused fixtures covering minimal/locator success, future optional-section deferral, unknown root fields, missing/wrong/unsupported manifest generation, required package/table shape, package unknown fields and non-empty package strings. Close C5B and make C5C compatibility/exports/workspace READY; C5D and C6 remain dependency-gated.

## 0.2.187-SNAPSHOT

- Close I022-E2 by integrating cooperative cancellation with D043 `ensure` for the synchronous-cleanup slice. `EnsureExitKind` gains `CANCELLATION`; an observed request with an active ensure frame remains in Task `UNWINDING` instead of publishing a terminal cancelled Future; each crossed ensure records and propagates the exact cancellation transfer after running cleanup in ordinary LIFO order.
- Add explicit later-transfer precedence over delivered cancellation. Cleanup Error or non-local return changes the internal cancellation phase to `SUPERSEDED`, preventing redelivery of the original request and allowing the ordinary Error/return path to determine the Task/Future outcome. Normal cleanup reaches `finishCancellationUnwind()`, which preserves E1's existing child-drain terminalization when applicable.
- Add Protos-source conformance for synchronous cancellation cleanup execution, exact cleanup Error identity, cleanup `^` superseding cancellation, and nested LIFO cleanup; retain narrow Java Task-state focal coverage for the internal UNWINDING/SUPERSEDED transitions. No specification or native-Closure boundary change. I022-E remains IN_PROGRESS and I022-E3 becomes READY for suspension/shielding and cleanup-created structured-child closure.

## 0.2.186-SNAPSHOT

- Start the subdivided `TOOL002-D3` implementation with `TOOL002-D3A1`: add bundled `Runner.readSource(spec, filesystem)` as ordinary Protos policy. It resolves the D2 canonical CaseSpec path through the confined standard Filesystem, consumes the ordered File with bounded 16-read / 64-KiB windows, accumulates exact bytes, closes the File, and decodes UTF-8 exactly once so codec scalars split across read results remain intact.
- Keep D3A1 deliberately below the execution/expectation boundary: no call to `execution(source)`, no PASS/FAIL interpretation, no TestPlan traversal and no `Main.protos` integration. A Protos fixture validates a 1,100,007-byte source crossing the 1-MiB batch boundary and verifies exact UTF-8 bytes around a multi-byte scalar boundary. `TOOL002-D3A2` becomes READY.

## 0.2.185-SNAPSHOT

- Start the cost-aware subdivision of `TOOL001-C5` with `TOOL001-C5A — manifest-scale parser prerequisite`. The first C5 schema corpus exposed that the already-published C3/C4 document implementation consumed host stack linearly: `statements(text)` recursively invoked one Protos Closure per source octet and `table(text)` recursively invoked one Closure per logical statement.
- Preserve the C3/C4 TOML contract while replacing those two unbounded linear-recursion paths with ordered standard collection traversal: `Bytes.each` advances the existing scanner state only at the current consumed index, including multi-octet CRLF/escape/quote consumption, and `Array.each` processes logical statements in encounter order. No TOML syntax, table ownership, canonical node shape or package/schema meaning changes.
- Add one Protos-owned long flat-document regression that parses 96 ordered assignments and observes the first/last values without StackOverflow. Formalize the remaining smaller C5 slices: C5B root/manifest-version/package is READY; C5C compatibility/exports/workspace and C5D dependencies/final schema closure remain dependency-ordered. `TOOL001-C6` stays blocked until C5 closes.

## 0.2.184-SNAPSHOT

- Close I022-E1 as the first publishable tranche of the decomposed I022-E cancellation work. Replace the single internal cancellation boolean with one idempotently recorded request plus explicit `NONE`, `REQUESTED`, `UNWINDING` and `TERMINAL` lifecycle phases. Only `REQUESTED` remains a pending portable observation; once observed, the same request is no longer exposed for redelivery.
- Make the already-normative structured-child cancellation drain explicit inside `UNWINDING`: observation requests cancellation of existing children, the parent waits on its existing child-drain dependency, and drain completion terminalizes cancellation without re-entering parent ordinary code. Preserve the current no-cleanup observable path, including pre-start cancellation, suspended-task wakeup, upstream-wait isolation and immediate cancellation when no structured child remains.
- Add Java-only focal coverage because E1 is intentionally internal scheduler/Task machinery rather than a new Protos-visible feature. Formalize I022-E1/E2/E3 in the implementation ledger: I022-E stays IN_PROGRESS, E2 becomes READY, and no `ensure`, Future public protocol, specification or native-Closure boundary change is made.

## 0.2.183-SNAPSHOT

- Close `TOOL001-C4 — canonical TOML table assembly` with `TOOL001-C4B` (legacy manifest Slice 3B2-B2). Extend ordinary-Protos `self:TomlDocument.table(text)` with TOML 1.0 `[[array-of-tables]]`: the first header creates an Array containing one fresh table, repeated headers append fresh table elements in encounter order, and assignments target the selected fresh element.
- Resolve ordinary/nested headers through the most recently defined array element exactly at the TOML data-model boundary, including nested arrays-of-tables. Reject static-Array append attempts, normal-table/array-of-table redefinition in either direction, inline-table traversal, and child-before-parent-element ordering. Array-of-table provenance remains parser-private `IdentityMap` state; the returned model stays ordinary `{ kind, value }` nodes backed by Maps and Arrays and acquires no package/schema meaning.
- Add Protos-owned conformance for ordered append, nested latest-element routing, implicit-super-table completion and the invalid collision/ordering cases from the TOML 1.0 array-of-tables contract. `TOOL001-C4` and C4B close; `TOOL001-C5` becomes READY for strict manifest schema-v1 validation/model construction while C6/C7 remain dependency-gated.

## 0.2.182-SNAPSHOT

- Close `TOOL002-D2`: grant the bundled Test Tool one explicit standard `Filesystem` capability rooted at `protos/tests/conformance` and backed by a new general read-only tree-confined NIO backend. Nested opens remain relative to pinned `SecureDirectoryStream` handles with `NOFOLLOW_LINKS`; write/create/mutation authority is absent and unsupported host providers fail closed.
- Add bundled `protos/tools/test/Manifest.protos`. The existing TSV manifest is read with bounded windows of ordinary ordered `TextReader.readLine` Futures, aggregated by `Future.all`, parsed in Protos, and materialized with balanced ordinary-Protos chunk accumulation as a frozen inert plan of frozen CaseSpec tuples. Named bundled-Protos accessors preserve the conceptual CaseSpec/TestPlan fields independently of that private representation. The initial stable `caseId` is the validated canonical relative manifest path and is independent of worker/completion order. No case is executed and no expectation is interpreted in D2.
- `protos test` now constructs that plan during normal bundled-tool startup while retaining its existing bootstrap output. A Protos fixture validates the first CaseSpec and proves nested corpus source authority through the standard Filesystem surface. TOOL002-D3 becomes READY; `future-*` remains owned by TOOL002-F.

## 0.2.181-SNAPSHOT

- Start `TOOL001-C4 — canonical TOML table assembly` with the smaller publishable `TOOL001-C4A` tranche. Extend ordinary-Protos `self:TomlDocument` with `table(text)`, producing the existing `{ kind, value }` TOML node model over C3 statement scanning and C1/C2 key/value parsing. Ordinary headers, dotted keys and inline tables normalize into the same nested Map representation without acquiring manifest-schema or package-policy meaning.
- Implement TOML 1.0 ordinary-table ownership rules with internal `IdentityMap` metadata only: implicit super-tables may later receive their own header; dotted-key-created tables cannot be redefined by that header; header-defined tables cannot be redefined through dotted-key traversal; headers may add new sub-tables below dotted-key-created tables; inline tables are closed against later extension; duplicate keys, scalar/table conflicts and repeated headers fail where the invariant is violated. Unsupported scalar nodes remain parser data for later schema validation.
- Apply the repository cost-aware decomposition rule to keep array-of-tables ownership separate: `TOOL001-C4` remains IN_PROGRESS, `TOOL001-C4A` closes ordinary table/dotted/header assembly, and `TOOL001-C4B` becomes READY for `[[array-of-tables]]` plus final TOML table-model conformance. C5 schema-v1 validation, C6 confined `protos.toml` I/O/diagnostics and C7 closure remain dependency-blocked.

## 0.2.180-SNAPSHOT

- Continue `PERF003-A — Collection algorithm Truffle compilability` after the exact external diagnostic against Protos `5404667964dec84b8b8de2ff8dbe7923a5d1dd2e` again preserved `array-reduce` correctness (`528`, `rc=0`) and reduced the remaining failing graph from 150069 to 150036 against the 150000 limit. The bailout count remains 2, so PERF003-A is not closed.
- Refine only ordinary-Protos `Array.reduce` initialization state: after the existing `initialSize > 1` guard, `initialSize` is exactly 0 or 1, so remove the `hasInitial` and mutable `startIndex` bindings, select the same two initialization paths directly, and derive the fold start as `1 - initialSize`. Preserve the pre-callback shallow snapshot, strict left fold, exact reducer calls/effects/failures and zero-or-one initial semantics. No `sort`, Java/runtime, Truffle-boundary, benchmark-specific, or normative change is made. Exact external validation against this publication remains required.

## 0.2.179-SNAPSHOT

- Close I022-D by first making the cooperative Task producer Closure activation itself replay-stable across segments: `invokeInTask()` now reuses one root activation so lexical bindings and ReturnHome identity survive suspension. Then make D043 `ensure(cleanup)` replay-stable across real `Future.value()` suspension in both protected body and cleanup. ENSURE frames now persist BODY/CLEANUP phase, the exact pending normal result/Error/non-local-return transfer and the body replay cursor. If cleanup suspended after the body had semantically exited, replay skips that body instead of reconstructing its transfer or effects.
- Extend the existing per-Task evaluator replay tape with one internal control-primitive operation that consumes the already-exited body's direct Closure-invocation ordinal while advancing to its recorded event boundary. Cleanup therefore reuses the same invocation activation and resumes its incomplete expression range; there is no second continuation stack, programmer-visible cleanup object or native-boundary expansion.
- Add Protos-source Future regressions for root lexical-binding and ReturnHome preservation across suspension, plus conformance for body and cleanup suspension, exact-once effects, exact normal-result and Error identity, selected-handler inactivity across suspended cleanup, pending non-local return, nested LIFO cleanup and two suspension phases in one ensure. I022 remains IN_PROGRESS and I022-E becomes READY for cooperative cancellation unwind and same-request shielding.

## 0.2.178-SNAPSHOT

- Continue `PERF003-A — Collection algorithm Truffle compilability` after the exact external rerun against Protos `eb8b9c588bb363309da5193bf8d98d64d5456cee` again preserved `array-reduce` result `528` and reduced the failing graph from 150500 to 150069 / 150000, leaving only 69 graph-size units while still recording 2 `GraphTooBig` failures. Refine only ordinary-Protos `Array.reduce`: cache the immutable invocation-local `initial` cardinality and fresh internal snapshot cardinality once, reuse them through validation/fold setup, and remove the recursive helper's redundant explicit `null` result expression.
- Keep the balanced left-before-right traversal, exact left-fold accumulator flow, reducer effects/failures, zero-or-one initial semantics and existing 32-element repeated-reduction conformance unchanged. No `sort`, Java/runtime, Truffle-boundary, benchmark-specific, or normative change is made. PERF003-A remains `IN_PROGRESS` pending an exact external rerun against this publication; PERF003-B remains `BLOCKED_BY_DEPENDENCIES`.

## 0.2.177-SNAPSHOT

- Close `TOOL001-C3 — TOML document scanner` (legacy manifest Slice 3B2-A) with ordinary-Protos `self:TomlDocument.statements(text)`. The scanner segments a TOML document into logical source statements while respecting basic/literal strings, multiline strings, comments, CRLF, nested arrays and inline tables; it rejects unterminated strings and unbalanced delimiters without assigning table or manifest-schema meaning.
- Keep the boundary intentionally below document assembly: C3 returns source-compatible statement Strings and does not interpret `key = value`, table headers, dotted-key ownership, duplicate/redefinition policy, arrays of tables, manifest generation, package fields, Filesystem reads or diagnostics. Add Protos-owned fixtures to the existing package-tool TOML conformance corpus; TOOL001-C remains IN_PROGRESS and C4 becomes READY for canonical TOML table assembly.

## 0.2.176-SNAPSHOT

- Close I022-C by publishing D043's standard Closure `ensure(cleanup)` as one audited host-irreducible `Object` native control primitive. The operation validates the semantic Closure receiver, exact arity and semantic Closure cleanup before entering the protected dynamic extent; preserves the body's exact normal result; ignores the cleanup's normal result; runs synchronous cleanup for normal completion, non-local return and Error unwind; preserves nested LIFO ordering; and lets a later cleanup Error/non-local return supersede the pending transfer.
- Integrate I022-B handler selection ordering: an Error handler selected by the body is already inactive while crossed `ensure` cleanup runs, so a cleanup Error cannot recursively re-select the consumed handler. Add Protos-source conformance for exact result identity, normal cleanup, LIFO nesting, Error preservation/precedence, selected-handler deactivation, non-local-return cleanup/precedence, eager validation, Closure-only cleanup and arity/receiver rejection.
- Reconcile the I018 native boundary by increasing `ProtosStandardObjectProtocol` from two to three construction sites and the repository total by exactly one, with provider count unchanged. I022 remains IN_PROGRESS; I022-D becomes READY for suspension/replay closure and I022-E remains the owner of cancellation unwind/shielding.

## 0.2.175-SNAPSHOT

- Continue `PERF003-A — Collection algorithm Truffle compilability` after valid external evidence against Protos `4b2d1c661ed943e51253ec44a324b45e798e1666` preserved `array-reduce` correctness (`528`) and reduced `GraphTooBig` from 40 failures to 2, but left one compiled graph at size 150500 against the 150000 limit. Refine only ordinary-Protos `Array.reduce` traversal so the balanced left-before-right range helper updates its invocation-local captured accumulator instead of threading accumulator/result values through recursive calls.
- Add 32-element repeated-reduction Protos conformance for exact results, 64 reducer calls across two reductions, and invocation-local accumulator isolation. No normative, Java/runtime, Truffle-boundary, benchmark-specific, or `sort` change is made. PERF003-A remains `IN_PROGRESS` pending an exact external rerun against this publication; PERF003-B remains `BLOCKED_BY_DEPENDENCIES`.

## 0.2.174-SNAPSHOT

- Start `TOOL002-D` with `TOOL002-D1`: expose the closed B/C exact-source fresh-Process/captured-execution mechanism to the bundled Test Tool through one bootstrap-local `execution` capability. The capability is not Core/prelude state and owns no manifest, expectation, CaseId, scheduler, reporter or retry policy.
- Add `ProtosDetachedExecutionValue`, a deliberately stricter-than-Actor transfer boundary for execution observations. Scalar and explicitly audited authority-free Object/Array/Bytes graphs are detached into fresh destination identities; Process, Actor/Group refs, streams, Filesystem/File, Closure, Future, task, activation and other execution/resource values fail closed with `NonTransferableValue` rather than leaking a child-Process graph into the tool Process.
- Record the cost-aware TOOL002-D decomposition: D2 will grant/read the confined conformance corpus and construct inert TestPlan/CaseId data in Protos; D3 migrates ordinary scalar/Error expectations; D4 handles the remaining non-Future closure/identity-sensitive expectations. Existing `future-*` expectation policy remains intentionally assigned to TOOL002-F by the already-selected sequence.

## 0.2.173-SNAPSHOT

- Close I022-B by publishing the already-normative `Error.handle(body, handler)` protocol over I022-A's dynamic-control substrate: eager Closure-only validation, ordinary-delegation matching, dynamically innermost selection, selected-frame deactivation before boundary unwind, exact Error identity and non-resumable handler transfer. Explicit `Error.signal()` preselects immediately; runtime-created Error transfers select at Closure invocation boundaries so both paths share one mechanism.
- Preserve pay-for-use/task isolation: Task-backed activations use Task-owned I022 state; direct synchronous activations propagate an existing flow-local state and allocate none until `handle` is actually used. Add Protos-source conformance for matching, nesting, validation, non-resumability, copied receiver rejection and runtime `SlotNotFound` handling, plus narrow Java machinery/native-boundary focal coverage.
- Reconcile the I018 native boundary for the one justified primitive: `ProtosStandardErrorProtocol` moves from one to two native Closure construction sites with provider count unchanged. I022 remains IN_PROGRESS and I022-C becomes READY.

## 0.2.172-SNAPSHOT

- Close I022-A with the internal replay-stable dynamic-control substrate required by D043/I022: add lazy per-task `ProtosDynamicControlState`, semantic Handler/Ensure frame identity keyed by stable invocation identity, pre-unwind frame deactivation distinct from LIFO extent removal, and a replaceable active transfer record for later return/Error/cancellation unwind integration. Structured child tasks receive independent state while synchronous activations continue sharing their existing task association.
- Add Java runtime focal coverage for the machinery-only invariants. Publish no Protos-visible `Error.handle` or `ensure` behavior yet and add no native Closure construction site; I022 remains IN_PROGRESS and I022-B becomes READY.

## 0.2.171-SNAPSHOT

- Close `TOOL002-C — single-case sequential captured execution`: add the test-neutral `ProtosCapturedProcessExecution` wrapper over TOOL002-B so one exact already-compiled Protos entry runs in a fresh semantic Process with private stdin, stdout, and stderr bindings and returns the inert semantic outcome plus detached captured output bytes. The initial capture is deliberately in-memory and sequential; it adds no parallel scheduler, manifest, expectation, retry, worker, remote, cache, reporter, or result-transfer policy.
- Add executable evidence using a real `.protos` tooling fixture that writes independently to stdout/stderr and returns an Integer, verifies per-execution capture separation, and verifies that committed output preceding a semantic Error is retained. The host mechanism remains general and TOOL002-D becomes READY for Protos-owned manifest/expectation migration; cross-Process result exposure to the bundled tool is intentionally not invented in C.

## 0.2.170-SNAPSHOT

- Continue `PERF003-A — Collection algorithm Truffle compilability` with the Protos-side implementation phase. Rewrite only `std:collections/Array.reduce` traversal and the internal merge traversal of stable `sort` from native `Array.each` callback loops into balanced ordinary-Protos range recursion. Preserve the pre-callback shallow snapshot, exact left-fold order and reducer invocation count, stable merge tree/order, two-direction comparator invocation/validation, failure behavior and fresh result semantics while avoiding a generic per-element Truffle boundary or benchmark-specific fast path.
- Add Protos-source conformance over an eight-element reduce/sort case and retain the complete existing LIB001 reduce/sort semantic corpus. PERF003-A remains `IN_PROGRESS` until the exact published implementation revision is validated under the external GraalVM Community JDK 22 / Truffle 24.0.0 / `-Xss128m` diagnostic environment and the two recorded `GraphTooBig` findings are shown absent.

## 0.2.169-SNAPSHOT

- Close `TOOL002-B — general fresh-Process execution mechanism`: add the test-neutral `ProtosFreshProcessExecutor` over the existing host-neutral standalone Process bootstrap, execute the exact precompiled entry as a real RootActor-local cooperative task, and return inert `ProtosExecutionOutcome` data for completed, failed, or cancelled terminal states. Every invocation creates and terminates a fresh semantic Protos Process; semantic Protos Errors are result data rather than CLI/test-specific host exceptions.
- Extract RootActor terminal dispatch from CLI-owned machinery into `ProtosRootTaskExecution` and keep the CLI's existing diagnostics as a consumer translation of the shared outcome. Add Java mechanism tests for fresh Process identity, Future suspension/resume, semantic failure capture, and Process termination. No test manifest, TestPlan, CaseId, scheduler, resource policy, timeout, worker, remote execution, retry, cache, reporter, Core semantic or specification change is introduced.

## 0.2.168-SNAPSHOT

- Close `PERF001-E` by reconciling companion reference evidence `guillermomolina/protos-benchmarks@4bff9f7f6c5e0e006530f166c188e0e988acf565` produced by harness `280173d743b2ed838a89be0ad930b20828d89558` against exact Protos corpus revision `86b35d8bb2d7ab2ad54bc2947e1bf7fbff1fca15`. All 18 cross-language correctness cases pass and startup/warmup/steady-state evidence is retained in 54 raw result records. Separate Truffle diagnostics retain two optimization findings: `collections/array-reduce` and `collections/array-sort` each execute correctly (`rc=0`) but record 40 `GraphTooBig` optimization failures. Introduce `PERF003 — Collection algorithm Truffle compilability` as READY so those findings are handled separately without rewriting PERF001-E baseline evidence. This reconciliation is documentation/project-state only and changes no implementation, specification, distributable artifact or implementation version.
- Select the non-normative TOOL002 scale/distribution architecture in `docs/design/TEST_TOOL_SCALE_AND_DISTRIBUTION_ARCHITECTURE.md`: preserve one fresh semantic Protos Process/RootActor per ordinary attempt while separating physical placement into replaceable local, future amortized OS-worker and future remote backends. Introduce conceptual Run/Case/Variant/Attempt identity layers, separate semantic from infrastructure outcomes, reject exactly-once assumptions for remote execution, and require inert serializable/streamable planning boundaries.
- Refine future massive scheduling around capacity plus resource access/locality, explicit capability provisioning, bounded/artifact-friendly output, deterministic sharding plus optional dynamic assignment, and affected/cache layers only when dependencies/hermetic execution fingerprints justify them. TOOL002-B remains READY and local/test-neutral; this checkpoint changes no Core semantics, runtime, distributable source or implementation version and requires no Maven tests.

- Close D043 / specification revision `0.1.380` by standardizing `body.ensure(cleanup)` as an ordinary Closure-specific `Object` behavior. The exact protocol validates a Closure receiver plus one Closure cleanup before entering the protected extent, preserves the body's exact normal result, runs cleanup exactly once across normal/non-local-return/Error/cancellation exits, treats suspension/replay as remaining inside the scope, and makes a later cleanup control transfer supersede the pending transfer without adding `try`/`finally` syntax, a `Closure` prototype, a general cancellation mask, or resource-specific semantics.
- Formalize `I022 — Dynamic Error handlers and unwind-safe cleanup` as READY with replay-stable dynamic-control, `Error.handle`, `ensure`, suspension, cancellation-unwind and integration slices. Mark LIB004 `BLOCKED_BY_DEPENDENCIES` until I022 is CLOSED rather than bypassing the general Core substrate with File/Filesystem-specific native helpers. This change is specification/project-governance only and does not change the Maven implementation version or executable behavior.
- Complete the required post-TOOL002-A expanded Test Tool comparative architecture checkpoint across pytest/xdist, cargo/libtest/nextest, Go test, JUnit/Surefire/Gradle, Node/Vitest, .NET, ExUnit/Common Test, CTest/GoogleTest, Bazel/Buck2 and RSpec. Retain one fresh semantic Protos Process/RootActor per ordinary case, private streams, explicit capabilities and bounded parallelism while introducing an inert stable-CaseId/TestPlan planning boundary above the general executor and generalizing future shared-resource scheduling from Boolean groups to capacity accounting.
- Confirm that TOOL002-B remains a test-neutral exact-entry fresh-Process mechanism returning inert execution outcomes: hard arbitrary timeout still requires a later physical OS-worker/controller layer, retries must preserve flaky-attempt evidence, and result caching is deferred until a hermetic input/environment/capability model exists. Close the comparative dependency checkpoint and make TOOL002-B READY. This is documentation/governance-only; no normative semantics, runtime, distributable source or implementation-version change is introduced.
- Close `TOOL002-A — Test Tool bundled bootstrap`: add public `protos test` dispatch to the exact toolchain-bundled `protos/tools/test/Main.protos` entry and execute it as ordinary Protos through the existing bundled-tool resolver. Factor package/test entry execution through one small common CLI bootstrap helper while retaining the Package Tool's separately provisioned confined Filesystem capability. The Test Tool receives only its ordinary Process/bootstrap streams in this slice and gains no Filesystem authority, test discovery, assertions, manifests, Process-per-test runner, timeout, parallelism, filtering, reporting, or corpus-migration policy.
- Add Java CLI/bootstrap conformance because the changed subject is Java host/driver functionality, while keeping language-test policy out of Java. Persist the required expanded cross-language/test-system comparative architecture audit as a dependency before `TOOL002-B` fresh-Process execution work begins. No normative Protos specification change is introduced.

## 0.2.167-SNAPSHOT

- Formalize official bundled-tool implementation tracking with the new non-normative `TOOLxxx` family. `TOOL001` retrospectively indexes the already-published Package Tool bootstrap, confined Filesystem 2A/2B, B006 metadata publication, and manifest Slice 3 sub-slices without renaming historical evidence; `TOOL002` promotes the selected Test Tool architecture with TOOL002-A READY and later slices dependency-gated. Keep `CLIxxx` for independently meaningful driver/terminal/dispatch mechanics, `PERFxxx` for project performance engineering, and `LIBxxx` for Standard Library API. No runtime, language, specification, distributable-code, or implementation-version change is introduced.
- Start `PERF001-E` with a fresh closed-surface audit and canonical Protos corpus extension: add six deterministic sequential collection workloads for `std:collections/Array` map/filter/reduce/stable-sort, Core Map lookup/update, and Map-backed Set algebra. Each workload exposes a scalar final-expression correctness value and fixed explicit work shape for later algorithm-equivalent Python/JavaScript translation. PERF001-E becomes IN_PROGRESS pending companion correctness/timing evidence against the exact corpus commit; no language/runtime/library/specification semantics or implementation version change is introduced.
- Close `PERF001-D` by reconciling published reference timing evidence `guillermomolina/protos-benchmarks@52b083cef5f8726f73be869c56f3cd2933e919ab`, produced by exact harness `0a406373c497df1173ff26a3ed4fcada015e0879`. The companion run compares adjacent pre/post-PERF002 revisions `8f363d0146164f99e72210eb44667f4efb7b88e7` and `3c93912a5579326374782a43527fbb51046f8f91` on the same GraalVM Community JDK 22 / external Truffle 24.0.0 environment with `-Xss128m` and a pinned CPU; it retains startup, ordered warmup and steady-state samples for all 11 canonical workloads in interpreter and Truffle modes, plus separate non-timing compilation diagnostics. Post-PERF002 diagnostics have zero optimization failures and zero recorded known bailout/runtime failures across all 11 workloads. PERF001 remains IN_PROGRESS with PERF001-E still READY; this reconciliation changes no implementation, specification, distributable artifact or implementation version.
- Complete `LIB003-D — streaming and text/byte adapters` with `LIB003-D3`: add ordinary `JSON.readEvents(textReader, consumer)` and `JSON.writeEvents(textWriter)` composition over the published D1/D2 event surfaces. Input consumes one `TextReader.readText()` chunk per explicit Future-shaped `read()` call; output validates one D2 event then contributes its deterministic String chunk through ordered `TextWriter.writeText`.
- Preserve explicit I/O boundaries: callers construct borrowing or owning TextReader/TextWriter with their chosen Encoding before adaptation; JSON does not infer ownership, acquire byte authority, close/flush resources, hide suspension, or create another codec/resource family. Each adapter permits one outstanding operation, providing bounded backpressure and becoming terminal after failed/cancelled/uncertain I/O instead of running JSON state past unknown source/target progress.
- Make writer `finish()` Future-shaped with the standard ordered zero-output `TextWriter.writeText("")` barrier, which contributes no bytes or encoder-state transition while preserving predecessor failure/order semantics. Add Protos-source Future conformance for chunked/empty/EOF reader flow, truncation/reuse/overlap failure, deterministic writer output/barrier behavior, downstream Future failure, and post-finish/overlap rejection; tests return Futures to the conformance runner instead of calling pending `Future.value()` outside an Actor-local task.
- Close LIB003-D and make LIB003-E READY. No normative specification or production Java boundary changes are introduced.
- Close `LIB003-E` and the bounded initial `LIB003 — JSON` scope after published `LIB003-E1` executable evidence at `e215952e5459782e95f0d9c73c7bffc948bac943`: Protos conformance covers deep valid/truncated parsing, explicit event-stack streaming, large Array materialization, large exact-decimal round-trip, long incremental String tokenization, and the B -> C -> D1 -> D2 -> B cross-slice pipeline; the existing Java-specific JSON parser stress and Actor graph-transfer boundary regressions plus the full Maven suite also pass.
- Complete `LIB003-E2` as documentation/governance-only architecture reconciliation: the distributable JSON module remains the sole exact-case `std:json/JSON` source and introduces no lowercase alias, generic Serializer/Deserializer hierarchy, reflection/object-binding hook, YAML/XML vocabulary, runtime type classifier, identity-reference persistence convention, hidden Encoding/ownership policy, production Java boundary, or normative specification change. E2 does not increment the Maven implementation version.

## 0.2.166-SNAPSHOT

- Implement `LIB002-A` and close the initial `LIB002 — Text / encoding conveniences` surface with ordinary `std:text/UTF8`, `std:text/UTF16LE`, `std:text/UTF16BE`, and `std:text/Latin1` modules. Each module exposes only `encode`, `decode`, `reader`, `owningReader`, `writer`, and `owningWriter`, delegating directly to the corresponding finalized Core Encoding/TextReader/TextWriter operations.
- Preserve Core argument/result, strict decoding/encoding, fresh/open Bytes, wrapper ownership, I/O authority, ordering, lifecycle, cancellation, and per-flow codec-state semantics without a new runtime value family, registry, default Encoding, automatic detection, production Java/native boundary, or shared mutable state.
- Add real-`std:` Protos conformance for all four one-shot codecs, import caching, borrowing/owning wrapper construction, invalid argument/capability paths, strict malformed/unrepresentable failures, and the explicit rule that helper module instances are not Encoding semantic values. Deferred read-all, Unicode transformation/normalization/locale, registry/discovery, and Core String/Bytes augmentation remain outside this closed initial scope.

## 0.2.165-SNAPSHOT

- Continue `LIB003 — JSON` with `LIB003-D2`: publish fresh ordinary `JSON.eventWriter(consumer)` instances with synchronous `feed(event)` and `finish()` over the JSON-specific D1 event vocabulary. The writer validates structural order, matching container boundaries, one root value, object name/value pairing and duplicate member names while inserting deterministic JSON punctuation itself.
- Emit exactly one semantic String chunk for each accepted event. Reuse the published JSON constructors/encoder for String escaping, strict Boolean/String/Number domains and exact unbounded coefficient/exponent decimal output, preventing a second divergent scalar-encoding contract.
- Keep consumer callbacks synchronous/non-reentrant and terminal on consumer failure; already-consumed output is not rolled back by later invalid events. Keep TextWriter/Future/ownership and byte-I/O lifecycle behavior outside D2 for LIB003-D3.
- Close LIB003-D2 and make LIB003-D3 READY. LIB003-D and top-level LIB003 remain IN_PROGRESS; no normative specification or production Java boundary changes are introduced.

## 0.2.164-SNAPSHOT

- Continue `LIB003 — JSON` with `LIB003-D1`: publish fresh ordinary `JSON.eventParser(consumer)` instances with synchronous `feed(String)` and `finish()` operations and a JSON-specific event vocabulary for object/array boundaries, member names, and null/boolean/string/exact-decimal scalar values.
- Preserve the strict LIB003-B grammar across arbitrary semantic-String chunk boundaries, including split literals/numbers/escapes/surrogate pairs, decoded duplicate-name rejection, exact unbounded coefficient/exponent decimals, one top-level value and trailing-data rejection. Container parsing uses an explicit linked frame stack and does not materialize a JSON tree.
- Make consumer callbacks non-reentrant for the parser and terminal on consumer failure; already-emitted events are not rolled back if later JSON input is malformed. Keep YAML/XML vocabularies, generic Serializer abstractions, object persistence, TextReader/TextWriter lifecycle and byte-adapter policy outside D1.
- Decompose the remaining streaming work into LIB003-D2 incremental event writing and LIB003-D3 I/O adapters. LIB003-D remains IN_PROGRESS and top-level LIB003 remains IN_PROGRESS; no normative specification or production Java boundary changes are introduced.

## 0.2.163-SNAPSHOT

- Continue `LIB003 — JSON` with `LIB003-C`: publish ordinary-Protos `JSON.encode(node)` for the explicit JSON tree model. The encoder validates each visited representation, preserves Array order and retained Object Map traversal order, and rejects malformed or cyclic trees instead of reflecting over arbitrary application objects or inventing references.
- Emit semantic Strings as strict JSON text over UTF-8, escaping quote/backslash and U+0000..U+001F without Unicode normalization. Emit exact Number coefficient/exponent data entirely through unbounded Integer arithmetic (`coefficient` plus optional `e` exponent), with no Float conversion, binary64 formatting, host/JVM decimal formatter, or eager normalization.
- Detect cycles only along the active traversal path with an ordinary `IdentityMap`, so shared acyclic JSON nodes remain valid and are encoded at each occurrence. Add Protos-source conformance for exact scalar/decimal output, escapes/Unicode round-trip, deterministic nested order, shared subtrees, malformed representations, and cycle rejection.
- Close LIB003-C with executable-impact focal/full-suite validation and make LIB003-D READY. Top-level LIB003 remains IN_PROGRESS pending streaming/adapters and final cross-slice conformance; no normative specification or production Java boundary changes are introduced.

## 0.2.162-SNAPSHOT

- Publish `PERF002-A`, the Protos-side implementation/conformance slice of the Truffle compilability and dispatch optimization discovered while preparing PERF001-D. Fixed Sequence and argument-vector child arrays are exposed as compilation-constant exploded loops; ordinary execution avoids the evaluator continuation bridge unless an active cooperative-task segment requires it; immediate selected-method invocation preserves the original receiver and physical `methodHome` without materializing an unobservable extracted Closure.
- Preserve actual extracted-method semantics exactly: a Closure-valued member read still creates a fresh receiver-bound Closure with copied local Closure slots, while only extraction materialization is kept outside Truffle partial evaluation. Deliberately do not add a Closure-type direct-call bypass, so ordinary local `call` shadowing remains observable.
- Add Protos-source conformance for Closure-local `call` shadowing and extracted-method freshness/receiver/local-state preservation. `PERF002-A` is repository-native and requires only Protos static/focused/full/package/license validation; `PERF002-B` remains READY for GraalVM/Truffle/container validation in `guillermomolina/protos-benchmarks`. PERF002 remains IN_PROGRESS until that external evidence is published and reconciled.

- Close `PERF002-B` and `PERF002` by reconciling published non-timing external evidence `guillermomolina/protos-benchmarks@c69248714a60dd62894164bf5332b55f780d6fe4` produced by harness `224ce852f550a7d9126fad9f5923a9a2fd8194cc` against exact Protos revision `3c93912a5579326374782a43527fbb51046f8f91`. GraalVM Community JDK 22 with external `truffle-runtime:24.0.0` and `-Xss128m` passes the semantic smoke, canonical 11x2 interpreter/Truffle correctness matrix, known Truffle bailout/runtime guard, and 10/10 consecutive polymorphic-dispatch Truffle stability gate with 238 successful optimization events and zero optimization failures. This is project-status reconciliation only: no implementation, specification, distributable artifact, timing result, or implementation-version change is introduced.

## 0.2.161-SNAPSHOT

- Close package-tool Filesystem Slice 2B / B006: provision the bundled package tool with explicit confined project-metadata read authority, write-only `createNew` staging authority, and namespace-mutation authority. Staging content is written through standard `File.write`/`close`, publication uses standard `Filesystem.replace`, and explicit abandoned-stage cleanup uses standard `Filesystem.remove`; ordinary application CLI sessions still receive no Filesystem authority.
- Add `self:MetadataPublication` in ordinary Protos code. It validates Path/String/Encoding inputs before staging creation, refuses to overwrite an existing staging name through `createNew`, completes the staging write and close before atomic replacement, and exposes explicit discard rather than silently deleting a colliding stage. Protos-owned fixtures exercise both `protos.toml` and `protos.lock`, collision preservation, pre-stage validation, forbidden targets, and cleanup.
- Preserve the audited Core native boundary at 107 construction sites across 30 providers: no package-specific native rename/write primitive, ambient filesystem access, new Core native Closure site, or normative specification change is introduced. I021 remains CLOSED and B006 transitions READY -> CLOSED.

## 0.2.160-SNAPSHOT

- Close I021-B with a production confined direct-child NIO Filesystem backend for D042 namespace-entry replacement/removal. The backend retains a `SecureDirectoryStream` for the authority root, applies `replace` through one atomic relative `move`, applies non-recursive `remove` through relative `deleteFile`, never pre-classifies or follows the final entry, and fails closed when the host/provider cannot provide the required transition.
- Keep read authority and namespace-mutation authority independent: `ProtosNioConfinedFilesystemBackend` delegates existing-file read opens to the established read-only backend while requiring a separate exact direct-child allowlist for `replace`/`remove`. The current package-tool CLI continues using `ProtosNioReadOnlyFilesystemBackend`, so I021-B does not silently make package metadata writable or grant staging policy ahead of the later package-tool integration.
- Add host-boundary conformance for absent/existing-target replacement, hard-link same-resource no-op, source/target symlink non-follow behavior, non-recursive removal, authority rejection, and fail-closed providers. No Core native-Closure construction site or normative specification changes are introduced; I021-C becomes READY and B006 remains READY.

- Close I021 with I021-C without changing the implementation version: execute ordinary Protos-source `Filesystem.replace`/`remove` programs through the production `ProtosNioConfinedFilesystemBackend`, covering exact Filesystem success identity, fresh Future results, authority failures as `IOError`, hard-link same-resource no-op, final-symlink non-follow replacement, and non-recursive removal failure.
- Reconcile the final I021 architecture/status boundary: the Core native-Closure inventory remains 107 construction sites across 30 providers, the bundled package tool remains deliberately read-only, and B006 stays READY for the separate write/staging-authority and metadata-publication integration.

## 0.2.159-SNAPSHOT

- Start I021 with I021-A: add the host-neutral asynchronous Filesystem namespace-mutation substrate for D041 `replace`/`remove`, including fresh Future results, eager Path-domain validation, independent operation state, pre-commit cancellation, exact Filesystem success results, and one per-operation effect/commit cutover that prevents cancellation from splitting a successful atomic backend effect from its Protos commitment.
- Widen the host-provisioned Filesystem bridge to the standard `open`, `replace`, and `remove` selectors without adding a Java native-Closure construction site: one audited operation-Closure helper serves all three resource/capability selectors. Backends that do not implement namespace mutation fail those valid operations as `IOError` instead of gaining ambient authority or a host fallback.
- Keep B006 READY and package metadata mutation blocked on the production confined namespace backend: I021-A establishes the protocol/substrate only; I021-B remains the next dependency.
- Correct D041 with D042 / specification revision `0.1.379`: `Filesystem.replace`/`remove` now select final namespace entries directly without following them instead of requiring a non-atomic ordinary-file preclassification. Unsupported atomic entry-kind combinations remain `IOError`, removal is non-recursive, I021-A remains valid, and I021-B continues as the production-backend dependency.

## 0.2.158-SNAPSHOT

- Resolve B006 normatively with D041 / specification revision `0.1.378`: add the minimal general confined file-entry `Filesystem.replace(sourcePath, targetPath)` and `Filesystem.remove(path)` surface, with failure-atomic namespace visibility, explicit commitment/cancellation/failure aftermath, stable already-open File binding, and no implicit namespace crash-durability guarantee.
- Transition B006 from BLOCKED to READY and formalize I021 as the implementation owner for the new general Filesystem namespace surface. Package-tool metadata mutation remains disabled until I021 is published; no package-specific native rename/truncate-write workaround is authorized.
- Continue package-tool manifest Slice 3 with Slice 3B1 by completing the TOML 1.0 String surface required by manifest schema v1. `self:TomlSyntax` now handles multiline basic and literal strings, first-newline trimming, deterministic CRLF-to-LF normalization, basic-string line continuations, one/two quote runs inside multiline strings, and the one/two content quotes permitted immediately next to a closing triple-quote delimiter. Multiline quote runs are consumed by an exclusive branch so they cannot fall through and be appended twice.
- Add Protos-owned conformance for multiline basic/literal values, line continuation folding, CRLF normalization, internal single/double quote runs, four/five-quote closing boundaries, overlong closing runs, and literal newlines rejected by single-line strings. No production Java parser, Filesystem write, lock mutation, schema/CLI behavior, Standard Library dependency, or LIB work item is introduced; Slice 3 remains open.

## 0.2.157-SNAPSHOT

- Close `CLI007` by executing each non-interactive standalone entry CallTarget as a real cooperative task owned by the Process RootActor and driving that RootActor execution domain until terminal. This realizes the existing pending `Future.value()` suspension contract for file/`-e` programs instead of leaking the host-side missing-task implementation error. Preserve ordinary Protos Error mapping and use the same task entry path for the bundled package tool; the persistent REPL model is intentionally unchanged.
- Restore executable validation for every shipped example/tutorial source that depends on CLI `print(...)`: dynamically discover all matching `.protos` files under `protos/examples` and `protos/tutorials`, execute each unchanged through the real standalone CLI, and require successful completion, observable stdout and blank stderr, including Future/Actor/ActorGroup learning programs. No normative specification change and no native-Closure boundary expansion.


## 0.2.156-SNAPSHOT

- Start package-tool manifest Slice 3 with Slice 3A: add an internal `self:TomlSyntax` key/value engine written entirely in Protos. It scans semantic String input as UTF-8 octets and establishes reusable parsing for bare/quoted/dotted keys, single-line TOML basic/literal strings, booleans, signed and radix integers with the TOML signed-64-bit boundary, nested arrays with comments/trailing commas, and inline tables with dotted keys and duplicate rejection. Bare scalar forms outside the manifest generation 1 value model are retained as explicit `unsupported` nodes for later schema validation instead of acquiring package meaning.
- Keep this sub-slice internal and non-authoritative until Slice 3 closes: TOML multiline string forms, full document/table assembly, schema-v1 validation, project Filesystem reads, and package CLI diagnostics remain for the next sub-slice. Protos fixtures own parser behavior while a small Java test harness only executes them through the existing bundled-tool resolver. No production Java parser, `std:toml` dependency, metadata mutation, lock behavior, normative spec change, or `LIBxxx` item is introduced.

## 0.2.155-SNAPSHOT

- Close package-tool Filesystem Slice 2A by provisioning the bundled Protos package tool with one explicit read-only Filesystem capability rooted at the launcher's project directory and restricted to the exact direct-child names `protos.toml` and `protos.lock`. The NIO backend uses a pinned `SecureDirectoryStream` plus `NOFOLLOW_LINKS` when the host provider can preserve the standard confinement contract and fails closed otherwise; ordinary CLI applications still receive no Filesystem authority.
- Exercise the capability from ordinary Protos source through `Path`, `filesystem.open(...).value()`, `TextReader`, and `Encoding.UTF8`; no package-specific file-read primitive or Standard Library dependency is introduced. Record B006 for metadata mutation because Filesystem v0.1 explicitly leaves rename/replace and richer namespace operations undefined, so Slice 2B must not publish `protos.toml`/`protos.lock` via fragile in-place truncate/write or a `PackageNative` escape hatch.

## 0.2.154-SNAPSHOT

- Close `CLI006` with a standalone CLI-owned `print(value)` binding installed as an ordinary Closure only in normal initial CLI sessions. String values print their contents directly; other values reuse CLI display rendering; each call delegates one complete line through a borrowing standard `TextWriter` over the already-provisioned Process stdout capability and Encoding, then returns canonical `null`.
- Make file and `-e` execution explicit-output-only instead of implicitly echoing the final evaluation result; retain the REPL general result-display policy. Add a Protos-source CLI fixture plus focused host/bootstrap/architecture conformance and end-to-end dogfooding of the published hello-world and values tutorial. Preserve the bundled `protos package` tool bootstrap without injecting `print` into its internal tool context. No normative specification change and no Core native-Closure provider/site expansion.


## 0.2.153-SNAPSHOT

- Establish the first bundled-tool bootstrap slice for the package-system architecture. `protos package` now launches an exact toolchain-bundled Protos entry from `protos/tools/package` without reading a project manifest, lockfile, package store, or ambient module search path. Tool-local `self:` imports resolve to internal `bundled-tool:` ModuleKeys while `std:` remains delegated to the selected Standard Library resolver.
- Keep package policy out of Java: the bundled Protos probe imports a second bundled module, observes the ordinary `process.args()` snapshot, and writes through the ordinary Process stdout/TextWriter path. The host change is limited to exact bundled-tool discovery/loading and CLI dispatch; no package resolution, filesystem authority, registry/networking, new public `tool:` namespace, normative semantics, or Standard Library work item is introduced.

## 0.2.152-SNAPSHOT

- Close `I020-D` and the post-Ixxx `I020` audit reconciliation by implementing D040 missing-`methodHome` super semantics. Core now publishes source-backed `InvalidSuper -> Error`; after the complete ordinary argument/spread vector finishes, a super send without `methodHome` signals one fresh `InvalidSuper` occurrence before any lookup, while valid method-bound super lookup and `SlotNotFound` behavior remain unchanged.
- Add focused Java and implementation-independent `.protos` conformance for `InvalidSuper` parentage/freshness, no host `IllegalStateException` leakage, argument-before-dispatch precedence, left-to-right exactly-once effects, spread precedence/order, no receiver fallback, and the root-`Object` empty-search `SlotNotFound` case. B005 and I020 are closed with no normative specification change and no native-Closure boundary expansion.

## 0.2.151-SNAPSHOT

- Continue `LIB003 — JSON` with `LIB003-B`: publish strict `JSON.parse(text)` in ordinary Protos source over `Encoding.UTF8` octets. The parser accepts every JSON top-level value, rejects comments/trailing commas/extra roots, rejects duplicate member names after escape decoding, handles JSON escapes and surrogate pairs without Unicode normalization, and preserves deterministic Object insertion order.
- Parse JSON Number syntax exactly into LIB003-A's unbounded Integer `coefficient * 10^exponent` representation without Float conversion or host/JVM decimal parsing. Leading zeroes, incomplete decimal/exponent forms, leading `+`, NaN and infinities are rejected.
- Keep JSON structural nesting off the host recursive-descent stack with explicit linked parser frames. Normal-mode dispatch snapshots top-level/container state before consuming each octet, so an opening delimiter is routed exactly once. JSON Arrays use balanced power-of-two chunk accumulation rather than quadratic element-by-element growth.
- Close LIB003-B with executable-impact focal and full-suite validation; keep LIB003-C READY. No normative specification or production Java boundary changes are introduced.

## 0.2.150-SNAPSHOT

- Start `I020 — Post-Ixxx implementation audit reconciliation` with I020-A: execute the already-canonicalized `super.message(arguments...)` operation end-to-end for method-bound activations. Super lookup starts strictly at the parent of the physical `methodHome`, preserves the original dynamic receiver, binds any newly selected Closure to its own physical lookup home through the existing invocation path, and uses the same ordered argument/spread vector machinery as ordinary sends.
- Add focused Java and implementation-independent `.protos` conformance for multilevel delegation with dynamic receiver state, nested Closure capture of `methodHome`, super-send spread arguments, and `SlotNotFound` after the defined super lookup origin. I020-A adds no native Closure construction site and changes no normative specification.
- Record B005 instead of inventing semantics for executing `super.message(...)` when the activation has no `methodHome`: the normative execution spec defines `parent(methodHome)` lookup but does not define that absent-home failure, while the `InvalidSuper` branch exists only in non-normative abstract-runtime pseudocode. I020-B and I020-C remain independently READY; I020-D is BLOCKED on B005.

## 0.2.149-SNAPSHOT

- Start `LIB003 — JSON` with `LIB003-A`: publish exact-case `std:json/JSON` as an ordinary Protos Standard Library module and establish an explicit JSON data model made only from fresh ordinary objects, Arrays, Maps, Strings, canonical Booleans/null and exact unbounded Integers. No JSON runtime family, `typeOf` mechanism, reflection-based object serializer, generic serialization hierarchy or production Java boundary is introduced.
- Add constructors for all six JSON value categories: `nullValue`, `boolean`, `string`, exact decimal `number(coefficient, exponent)`, ordered `array`, and String-keyed `object` over alternating name/node pairs. Number data remains exact as coefficient × 10^exponent without Float conversion or eager decimal normalization; JSON Array payloads use the language's existing fresh frozen trailing-rest capture, while object construction rejects duplicate semantic String names instead of silently choosing first/last-wins behavior.
- Persist `docs/project/LIB003_JSON_DESIGN.md` with the comparative JSON/YAML/XML/object-persistence audit, strict parser/encoder direction, Unicode/duplicate/order/streaming boundaries and planned LIB003-B/C/D/E slices. Reconcile stale LIB003 dependency text now that LIB001 and I015 are closed. LIB003-A is CLOSED; top-level LIB003 remains IN_PROGRESS.

## 0.2.148-SNAPSHOT

- Close `LM005 — Concurrent Language Maturity` with `LM005-C`: add a Group-aware conformance harness over ordinary `.protos` programs and the already-closed I011 ActorGroup surface. The harness drives a real Process RootActor, deterministic test-host Actor bootstrap and scheduler work, while assertions remain host-side. No test-only Protos syntax, production runtime behavior, native boundary, or normative specification change is introduced.
- Add language-level coverage for complete-vector `Actor.group` validation, stable GroupRef aliases versus fresh separate acquisitions, requests issued before members become READY, routing whose assertion permits any eligible member, synchronous argument snapshotting, GroupRef Actor transfer preserving semantic identity, terminated-member exclusion, and the communication-only GroupRef surface. Add an executable ActorGroup example and tutorial `11-actor-groups`; LM005-A/B/C and top-level LM005 are CLOSED.

## 0.2.147-SNAPSHOT

- Close I015 with I015-E final Encoding/Text I/O integration. Replace the provisional one-shot-only host codec boundary with explicit transactional per-flow streaming decoder/encoder factories carried by immutable Encoding descriptors. Mandatory `Encoding.UTF8`/`UTF16LE`/`UTF16BE`/`Latin1` remain the same strict, matching-BOM-consuming public descriptors; trusted hosts may explicitly provision configured portable or additional host Encoding values with strict/replacement and initial-BOM policy without adding a name registry, public constructor, ambient discovery or I/O authority.
- Unify TextReader over the Encoding streaming contract for portable and host descriptors. Decoder previews are reversible checkpoints until the surrounding read commits, so readText/readLine cancellation preserves zero logical consumption while stateful decoder transitions, replacement extents and exact source-octet line budgets remain deterministic. Portable replacement follows Unicode maximal-subpart segmentation across native/source chunk boundaries; strict remains default; explicit initial-BOM preservation emits U+FEFF as ordinary text and therefore participates in line byte accounting.
- Unify TextWriter over transactional per-flow encoder state for portable and host descriptors. Complete payload encoding is validated before target-visible contribution, empty write performs zero encoder transition, committed writes install the next encoder state in operation order, downstream uncertainty retains the permanent output-failure frontier, and close explicitly finalizes encoder state and propagates required final bytes before optional owned-target release. Final cross-slice conformance covers host reader/writer acceptance, independent flow state, close finalization, portable and host replacement, BOM policy, I015-A transfer invariants and I015-B/C/D regressions. No native Closure construction site is added by E; the post-I015 I018 boundary remains 30 providers / 107 sites. I015 and I015-E are CLOSED; LIB002 becomes READY for its own fresh ordinary-library design/audit.

## 0.2.146-SNAPSHOT

- Continue `LM005 — Concurrent Language Maturity` with `LM005-B`: add a deterministic Actor-aware conformance harness that executes ordinary `.protos` programs inside a real Process RootActor while a test-only module resolver and scheduler executor drive spawned Actor bootstrap and turns. Assertions stay host-side; no test-only Protos syntax, privileged language object, production runtime behavior, or normative specification change is introduced.
- Add language-level coverage for stable `Actor.current()` identity, module-based `Actor.spawn`, request/reply and snapshot transfer, child/root ActorRef identity, persistent Actor-local state, same-sender concrete-Actor `send` then `request` FIFO, graceful `stop`/`termination`, and synchronous invalid/non-transferable spawn failures. Add Actor examples and tutorial `10-actors`. LM005-B is CLOSED and LM005-C becomes READY.

## 0.2.145-SNAPSHOT

- Start `LM005 — Concurrent Language Maturity` with `LM005-A`: extend the implementation-independent language-conformance corpus with terminal-Future expectations, while keeping assertions in the host runner rather than adding test-only Protos syntax or privileged testing objects.
- Add Protos conformance for closure `future()`, fresh Future identity, `then` transformation and automatic Future flattening, deterministic `Future.all` input ordering/empty input, pre-start cancellation, and eager rejection of a non-invokable `then` transform. Add an executable Future-chain example and tutorial `09-futures`. No runtime or normative specification behavior changes; LM005 remains IN_PROGRESS and LM005-B becomes READY.

## 0.2.144-SNAPSHOT

- Implement I015-D source-backed standard `TextWriter` with borrowing `TextWriter(target, encoding)` and explicit `TextWriter.owning(target, encoding)` construction. Construction validates ByteWritable authority, exact portable Encoding-family membership and owning Closable authority synchronously before wrapper creation or target I/O; each success creates a fresh wrapper exposing exactly `writeText`, `writeLine`, `flush`, and `close`.
- Add one ordered text-output domain. `writeText(text)` and `writeLine(text)` validate complete portable UTF8/UTF16LE/UTF16BE/Latin1 payload encoding before target contribution; `writeLine` appends canonical LF in the same logical operation. `writeText("")` performs no encoder invocation/state transition/BOM/flush/reset/target write but remains ordered behind earlier operations. Encoding failure is zero-output and non-poisoning; once target write/flush delegation crosses the wrapper commitment frontier, downstream failure permanently fails later output without guessed replay.
- Compose Flushable and Closable/ownership semantics with I014 lifecycle machinery. Wrapper `flush()` propagates through an immediate Flushable target and otherwise ends at the ByteWritable boundary; close cuts over accepted-but-uncommitted operations, waits for already-committed output aftermath, does not imply flush, and closes the target only for `owning`. A prior wrapper-output failure remains the primary close failure while owned-target close is still attempted. I015-D covers portable encoders; host-provided incremental encoder integration remains I015-E. Register the two-site TextWriter resource bridge in I018, growing the definitive boundary by exactly one provider and two construction sites. I015-D is CLOSED and I015-E becomes READY.

## 0.2.143-SNAPSHOT

- Close the focused LIB001-E audit and complete `std:collections/Array` with eager sequential `reduce(array, reducer, ...initial)` and stable `sort(array, less)` as ordinary Protos library behavior; close top-level LIB001 after final cross-slice validation.
- Define `reduce` as a strict left fold over a pre-callback shallow snapshot: zero or one initial value, `null` for empty/no-initial, exact singleton pass-through without reducer invocation, exact initial pass-through for empty/seeded reduction, and `(accumulator, element)` callback order with ordinary failure/control propagation.
- Define `sort` as a fresh open stable Array result using one canonical sequential merge-sort tree; each merge decision calls `less(left,right)` then `less(right,left)`, accepts only canonical Booleans, preserves left order for comparator-equivalent elements, and signals fresh `InvalidComparatorResult` or `InvalidComparatorOrder` occurrences for the existing invalid-result/antisymmetry failures.
- Keep sequential reduce/sort free of P/Future/transfer/cancellation semantics, add no production Java or generic collection hierarchy, and transition LIB003 from its LIB001 dependency block to OPEN pending its own fresh serialization/text dependency audit.

## 0.2.142-SNAPSHOT

- Implement I015-C `TextReader.readLine()` / `readLine(maxBytes)` inside the same ordered decoder/input domain introduced by I015-B. Line framing recognizes LF, CR and CRLF; terminators are consumed and omitted, empty lines are preserved, EOF-final unterminated text is returned once, and EOF with no remaining text returns null. A terminating CR completes immediately without waiting for more backend input; a possible later LF is folded as the already-completed CRLF terminator before the next logical text operation can observe it.
- Enforce exact encoded-source-octet line budgeting for `readLine(maxBytes)`: positive Integer-family validation happens as a failed Future before I/O; initial matching BOM bytes and the encoded line terminator are excluded; valid pre-terminator scalar extents count exactly; LineTooLong is established as soon as valid content exceeds the bound and permanently fails text reading without scanning/discarding to a later terminator. Strict malformed-input vs size-failure precedence follows source order.
- Preserve I015-B cancellation/error/ownership semantics across mixed `readText` and line requests: one queue determines invocation order, successful cancellation consumes no logical text, partial lines are never returned on pre-terminator I/O/decoding failure, completed CR lines are not retroactively failed by later input, and permanent line/decoding/I/O failure prevents later source consumption. I015-C adds no native Closure construction site; the TextReader provider remains a two-site resource bridge and the definitive I018 provider/site totals are unchanged. I015-C is CLOSED and I015-D becomes READY.

## 0.2.141-SNAPSHOT

- Close the fresh LIB001-D Array API audit and publish `std:collections/Array` with eager sequential `map(array, transform)`, `filter(array, predicate)`, and `findIndex(array, predicate)` as ordinary Protos library behavior over the existing Core Array surface.
- Capture one shallow ascending-index Array snapshot before any user callback; `map` returns a fresh open same-length Array, `filter` returns a fresh open stable-order selected Array with exact source element identities, and `findIndex` stops at the first matching snapshot index or returns `null`.
- Require canonical Boolean predicate results for `filter`/`findIndex`, signal fresh `InvalidPredicateResult` occurrences otherwise, and explicitly keep empty-input callbacks uninspected because Standard Library source has no hidden non-invoking callability preflight primitive.
- Implement variable-length filtering in Protos source with balanced range composition and ordinary multi-spread Array construction, avoiding a new runtime builder/native boundary and avoiding quadratic one-element-at-a-time result rebuilding; leave `reduce`/`sort` to LIB001-E's required fresh audit.

## 0.2.140-SNAPSHOT

- Implement I015-B standard `TextReader` borrowing/owning construction and progress-oriented `readText()`. `TextReader(source, encoding)` and `TextReader.owning(source, encoding)` are source-backed frozen-prelude factory operations; constructors validate ByteReadable capability, exact Encoding-family membership and owning Closable authority synchronously before wrapper creation or I/O. Each success creates a fresh wrapper with exactly `readText` and `close`.
- Add one ordered TextReader input/decoder domain with transactional strict portable UTF-8/UTF-16LE/UTF-16BE/Latin1 decoding, initial matching-BOM consumption, incomplete-sequence retention, progress as soon as valid text is returnable, valid-prefix delivery before a later malformed error, permanent text-side failure once decoding/underlying I/O failure becomes an operation outcome, and stable no-more-source-consumption behavior after failure.
- Integrate readText cancellation and close with the I014 I/O lifecycle machinery: queued/active cancellation is zero logical consumption even when lower read-ahead completes late, close irreversibly cuts over uncommitted reads, borrowing close leaves the source open, owning close explicitly releases a Closable source, and reader failure does not itself close the source. I015-B intentionally covers portable Encoding streaming; I015-E retains host-provided streaming-Codec integration because the published I015-A host Encoding boundary is one-shot only. Register the two-site TextReader resource/capability bridge in I018, moving the audited boundary from 28 providers / 103 sites to 29 providers / 105 sites. I015-B is CLOSED and I015-C becomes READY.

## 0.2.139-SNAPSHOT

- Close LIB001-C by completing the initial `std:collections/Set` and `std:collections/IdentitySet` surfaces with fresh `union`, `intersection`, and `difference` results plus `sameMembers`, `isSubset`, `isSuperset`, and `isDisjoint`; no runtime Set family, wrapper, tag, generic collection hierarchy, or production Java path is added.
- Preserve deterministic algebra order and representatives through ordinary Map/IdentityMap operations: union inserts left then right into a fresh keyed result, intersection/difference retain matching/nonmatching left representatives in left traversal order, and every result remains a fresh open Map-backed Set with canonical `true` markers.
- Implement predicate short-circuiting with the existing Protos non-local return mechanism, keep ordinary Map `==`/`hash` unchanged in favor of explicit `sameMembers`, and add focal conformance for numeric-equality versus identity membership, traversal order, short-circuit key-search effects, freshness/open state, and Actor transfer of Set data while module behavior remains Actor-local.

## 0.2.138-SNAPSHOT

- Close LIB001-B for `std:collections/Set` and `std:collections/IdentitySet` with ordinary Protos `add`, `remove`, and member-only `each`: `add` performs exactly one underlying `atPut(element, true)`, `remove` exactly one keyed removal, and both return the exact Set object after normal completion.
- Preserve the underlying Map/IdentityMap open/closed/frozen mutation laws, existing representative/insertion position on duplicate add, absent-remove Error behavior, and insertion-order shallow-snapshot traversal; Set `each` adapts the keyed `(element, marker)` callback to exactly `block(element)` without adding a callability primitive or runtime Set family.
- Add focal conformance for both modules covering receiver identity, canonical `true` markers, closed/frozen state, duplicate representatives, snapshot mutation, deterministic visit order, empty/non-empty callback validation, arity/invocation failure propagation, and transition LIB001-C to READY.

## 0.2.137-SNAPSHOT

- Implement I011-21 / D039 public ActorGroup acquisition with the exact frozen-Core selector `Actor.group(firstMember, additionalMembers...) -> GroupRef`: validate the complete evaluated argument vector as ActorRefs before cutover, create one fresh Group identity plus one fresh GroupRef acquisition, and collapse duplicate Actor incarnations into set membership.
- Bind Core-created Group lifetime to the caller Actor's Process without exposing a Group/controller handle: owning-Process termination terminates the Group before member-lifecycle callbacks can continue routing, while Group termination never stops member Actors and existing GroupRefs remain bound to the same terminated Group identity.
- Reuse the I011-15..20 GroupRef/routing/transport machinery and keep service discovery, post-creation membership control, placement, endpoints, and transport selection outside Core v0.1. Close B004 and top-level I011 after focused and full-suite validation; record the reviewed native boundary expansion from 102 to 103 sites across the same 28 providers.

## 0.2.136-SNAPSHOT

- Close I019-A by renaming the Core Actor source from `actor.protos` to `Actor.protos`: `Actor` is the dominant public owner of the source while `_coreActorRefPrototype`, `_coreGroupRefPrototype`, and `_coreSendOperationPrototype` are private subordinate bootstrap identities.
- Refine the Core naming rule from strict one-object ownership to dominant public conceptual ownership with subordinate private helpers; keep `error_taxonomy.protos` and `prelude.protos` descriptive because they are true aggregation/responsibility sources, and keep `import.protos` because it already preserves the exact lowercase public facility name.
- Update Core bootstrap, the executable source-naming guard, current architecture documentation, and the canonical implementation ledger without changing normative semantics or the native boundary.

## 0.2.135-SNAPSHOT

- Implement I019 Core source naming reconciliation without changing normative semantics: rename the 26 distributable one-owner Core sources so each filename preserves the exact canonical Protos object/prototype name and case.
- Rewrite every live physical-source reference under `src/` and `protos/`, including source-loading tests, and require a zero-result post-change scan for all superseded names; historical CHANGELOG evidence remains untouched.
- Keep `actor.protos`, `error_taxonomy.protos`, `prelude.protos`, and `import.protos` as reviewed responsibility/aggregation or exact-lowercase facility sources; normalize exact APL Part 5 notices on every renamed source and add an executable naming guard. I018 remains closed and the audited native boundary is unchanged.

## 0.2.134-SNAPSHOT

- Resolve B004 normatively with D039: Core v0.1 adds exactly one direct ActorGroup acquisition surface, `Actor.group(firstMember, additionalMembers...) -> GroupRef`, using explicitly held ActorRef capabilities and creating no public Group handle, registry, discovery namespace, endpoint, placement, or transport API.
- Define synchronous creation cutover, initial membership, caller-Process ownership/lifetime, fresh GroupRef acquisition identity, zero-eligible-member behavior, and explicit absence of Core post-creation membership/control or Group termination selectors; GroupRef remains communication-only authority.
- Keep service discovery explicitly outside Core v0.1: no name/identity lookup, rebinding contract, TTL/watch/federation, or ambient reacquisition API is introduced. B004 moves `BLOCKED -> READY`; I011 remains IN_PROGRESS until the newly specified acquisition surface is implemented and validated.


## 0.2.133-SNAPSHOT

- Implement I011-20 remote ActorGroup routing by allowing the internal Group membership view to contain either local READY Actors or already-materialized remote ActorRefs carrying the I011-19 host-neutral transport route; no public membership, discovery, endpoint, or transport-selection API is added.
- Extend the internal transport delivery SPI with state observation so Group routing can react without polling: known pre-acceptance transport failure may requeue/select another eligible member, while accepted or acceptance-uncertain work immediately leaves Group routing ownership and is never transparently replayed.
- Preserve the original logical send/request snapshot across remote-member selection and pre-acceptance rerouting; Group SendOperation retry is explicit after remote uncertainty, and Group request acceptance uncertainty fails with RequestOutcomeUncertain. B004 remains limited to the still-undefined public Group/GroupRef acquisition/discovery surface.

## 0.2.132-SNAPSHOT

- Close I017 with the I017-F integrated Process conformance pass. Exercise the published A/B/C/D1/D2/E1/E2/E3 surfaces together: one logical standalone Process/RootActor authority, canonical args/environment reacquisition through independently rematerialized Actor-local Process proxies, fresh ordinary Actor-copy identity for already-acquired snapshots, exact bootstrap-local optional Filesystem separation, independent byte-stream capability shape with stable Encoding associations, and Process/stream/Filesystem exclusion from P where specified.
- Revalidate the Process termination cutover across the public eight-accessor surface and retain the focused D1 stream-lifecycle termination test in the closure suite. No I017 production behavior, selector, capability, host authority, or native Closure construction site is added by F.
- Complete the post-I017 I018 re-audit at exactly 28 Java native-Closure providers / 102 construction sites. Reconcile project state: I017 is CLOSED; I011 no longer lists Process/bootstrap integration as an outstanding dependency; LIB004 Filesystem/process conveniences becomes READY for its own focused design/audit now that I016 and I017 are both closed.

## 0.2.131-SNAPSHOT

- Implement CLI005 portable Standard Library naming: `std:` logical names preserve ASCII letter case as part of canonical `ModuleKey` identity and every physical path component must match the distributed spelling exactly even on case-insensitive filesystems.
- Reject case-fold-equivalent path ambiguity, every case spelling of the reserved `core` first segment, and Windows reserved device-name segments while preserving absolute no-fallback resolution and the hidden `.protos` extension.
- Rename the initial LIB001 modules to canonical `std:collections/Set` and `std:collections/IdentitySet`, backed physically by `Set.protos` and `IdentitySet.protos`, with no aliases for the superseded lowercase spellings; LIB001-B remains READY.

## 0.2.130-SNAPSHOT

- Implement I017-E3 standalone host/CLI Process bootstrap. `protos <file> [args...]` and `protos -e <source> [args...]` now capture only trailing application arguments into the stable Process args snapshot; launcher file/source identity is excluded. The REPL owns one persistent Process with an empty args snapshot for the whole session.
- Capture the host Environment exactly once before the first source expression. Native environment-name representability and identity are probed through an isolated `ProcessBuilder.environment()` map, preserving the JDK native platform rules without hard-coded POSIX/Windows case folding or mutation of the real environment. stdin/stdout/stderr are independently established byte bindings over the CLI-supplied streams and the CLI explicitly selects standard UTF-8 descriptors as its host text associations.
- Add host-neutral `ProtosStandaloneProcessBootstrap`, route non-importable standalone entries through the same E2 RootActor bootstrap-local `process`/optional `filesystem` authority model, and retain the exact source-backed hidden Core ActorRef/Bytes prototypes as runtime-only Prelude metadata so no duplicate standard identity is manufactured. The CLI deliberately grants no default Filesystem capability: launcher authority used to read source is not ambient application Filesystem authority, and I016 host filesystem policy remains explicit. No native Closure site is added; I018 stays 28 providers / 102 sites. I017-E3 and coordinating I017-E are CLOSED; I017-F is READY.

## 0.2.129-SNAPSHOT

- Implement I011-19 host-neutral remote ActorRef communication transport state: one internal route SPI carries the existing logical message snapshot while preserving ActorRef identity and exposes only delivery knowledge needed by Core (pending, accepted, known pre/post-acceptance failure, cancellation, completion, or acceptance uncertainty).
- Route ActorRef send/request through that SPI when present: SendOperation cancel remains true only for known pre-acceptance cancellation and retry is valid after terminal failure or delivery uncertainty; request transport uncertainty, including cancellation when non-acceptance can no longer be proved, fails with RequestOutcomeUncertain.
- Re-snapshot normal remote request replies at the caller Actor boundary so a transport adapter cannot introduce cross-Process mutable references. No wire format, endpoint syntax, discovery API, timeout/failure detector, transport selector, or new native Closure boundary is introduced; B004 remains limited to public Group/GroupRef acquisition/discovery.

## 0.2.128-SNAPSHOT

- Implement I017-E2 RootActor bootstrap-local authority provisioning. The unique RootActor's canonical initial module receives exactly one local `process` slot before its first source expression; the value is a fresh Actor-local Process capability proxy delegating to the source-backed `Process` prototype. The initial module still enters the Actor-local cache before source execution, so recursive imports observe the same partially initialized module and its already-provisioned bootstrap locals.
- Add a bootstrap-stable optional default Filesystem grant to `ProtosProcessRuntime`: the host either constructs the Process with one `ProtosFilesystemValue` or with no grant. When granted, the RootActor initial module receives the exact capability in a local `filesystem` slot; when absent, no such slot exists. Process authority remains unable to recover Filesystem authority.
- Keep ordinary module import and non-root Actor bootstrap on the existing ambient-authority-free path. Imported modules receive no local `process`/`filesystem` slots, hosted Actors do not inherit them merely because they belong to the same Process, and explicit Process Actor-transfer/delegation remains the only Core path for giving another Actor Process authority. E2 adds no native Closure site, so I018 remains 28 providers / 102 sites. I017-E2 is CLOSED and I017-E3 becomes READY for standalone host/CLI capture and wiring.

## 0.2.127-SNAPSHOT

- Start the safe subdivision of I017-E with I017-E1. Add a source-backed, frozen, authority-free standard `Process` prototype to the Core prelude and install exactly the eight standardized synchronous accessors: `args`, `environment`, `stdin`, `stdinEncoding`, `stdout`, `stdoutEncoding`, `stderr`, and `stderrEncoding`. The prototype has no constructor/call surface and cannot recover a Process capability.
- Implement all eight accessors as one audited runtime/capability Closure-construction helper over the already-published I017-A/B/C/D1/D2 substrate. Accessors require an actual represented Process capability rather than duck typing or delegation masquerading, perform no host discovery or waiting, return canonical args/environment snapshots and already-established stream/Encoding bindings, fail on unavailable/invalid bootstrap state, and reject every existing proxy after the Process termination cutover.
- Keep RootActor moduleContext provisioning and host/CLI capture out of E1. I017-E is subdivided into E1 public Process accessors, E2 RootActor bootstrap-local `process`/optional `filesystem` provisioning and imported-module confinement, and E3 host/CLI bootstrap capture/wiring. Register the single new Process Closure construction site in I018, moving the audited boundary from 27 providers / 101 sites to 28 providers / 102 sites. I017-E1 is CLOSED and I017-E2 becomes READY.

## 0.2.126-SNAPSHOT

- Implement I011-18 final local/cross-Process ActorGroup conformance: prove GroupRef continuity across member replacement, pre-acceptance rerouting when a selected member's Process terminates, and no rerouting after concrete acceptance.
- Add deterministic request-loss and cancellation races spanning Process, Actor, Group, and communication boundaries: accepted Group requests lost to Process termination fail with RequestOutcomeUncertain, while READY-versus-cancel races produce exactly one legal pre/post-acceptance outcome and never duplicate handler execution.
- Record B004 for the remaining public Group/GroupRef acquisition/discovery API: the distributed-runtime spec closes identity/routing semantics but explicitly leaves exact Group/GroupRef API/syntax and any new public discovery API undefined. I011 remains IN_PROGRESS; genuinely remote transport/acceptance uncertainty and I017 reconciliation may continue independently.

## 0.2.125-SNAPSHOT

- Implement LIB001-A as ordinary distributable Protos modules `std:collections/set` and `std:collections/identity_set`, with variadic module-call construction over fresh open Map/IdentityMap state and canonical `key -> true` representation.
- Add initial `contains` and `size` library observations, preserve normal Map equality versus IdentityMap semantic-identity membership and first-representative insertion order through the existing keyed protocols, and add focal real-`std:` module conformance without introducing a runtime Set family, wrapper, prototype, or native boundary.

## 0.2.124-SNAPSHOT

- Implement I011-17 language-visible GroupRef communication surface: create the hidden source-backed GroupRef delegation prototype in Core bootstrap and install exactly `send` / `request`, with no `stop`, `termination`, controller, Authority, broadcast, or acquisition selector.
- Reuse the already-audited Actor communication native construction helpers to create distinct ActorRef/GroupRef send/request Closures; receiver-directed dispatch selects concrete ActorRef or local ActorGroup routing while preserving synchronous whole-graph snapshot, caller Actor identity, SendOperation cancel/retry, Future reply transfer, and RequestOutcomeUncertain semantics from I011-8/9/16.
- Keep the I018 Java native construction-site inventory unchanged: ProtosStandardActorProtocol remains at eight sites and the repository-wide provider/site totals do not grow. Public/distributed Group acquisition and genuinely remote transport/acceptance uncertainty remain outside this slice.

## 0.2.123-SNAPSHOT

- Implement I017-D2 stable Process-bootstrap associations between each independently optional stdin/stdout/stderr byte binding and its host-selected immutable Encoding descriptor. Portable and explicitly host-provided Encoding descriptors from I015-A are both valid association values.
- Record each association exactly once as AVAILABLE, UNAVAILABLE, or INVALID: an available byte stream requires one Encoding; an unavailable stream requires no Encoding; any availability/Encoding mismatch becomes stable invalid bootstrap configuration rather than triggering a later host lookup, inferred default, alias lookup, or codec discovery.
- Keep D2 internal and synchronous: it adds no public Process selector yet, no Future/suspension, no text wrapper, no stream capability enlargement, and no Java native-Closure construction site. The audited I018 boundary remains exactly 27 providers / 101 sites. I017-D2 is CLOSED and I017-E becomes READY.

## 0.2.122-SNAPSHOT

- Implement I015-A standard Encoding semantic family with a source-backed frozen `Encoding` prelude factory/prototype and exactly the four mandatory portable descriptors: `Encoding.UTF8`, `Encoding.UTF16LE`, `Encoding.UTF16BE`, and `Encoding.Latin1`; no String-name constructor, alias registry, or implicit codec discovery is introduced.
- Implement strict synchronous one-shot `encoding.encode(String) -> Bytes` and `encoding.decode(Bytes) -> String` with exact semantic argument/receiver domains, fresh open Bytes identity on every successful encode including empty output, UTF-8/UTF-16 validity checks, default consumption of one initial matching BOM without encoding switching, no default BOM emission, and exact ISO-8859-1 Latin1 behavior. Malformed/unrepresentable conversion signals `EncodingError`.
- Add the explicit host-provisioning boundary for additional immutable Encoding descriptors and ordinary Actor/P transfer for Encoding as an authority-free immutable semantic value. Register the two-site Encoding representation bridge in I018, moving the audited boundary from 26 providers / 99 sites to 27 providers / 101 sites.
- Start the phased I015 implementation ledger. I015 remains IN_PROGRESS for streaming TextReader/readLine/TextWriter work, but the actual dependency needed by I017-D2 is now satisfied by I015-A Encoding; I017-D2 becomes READY.

## 0.2.121-SNAPSHOT

- Implement I011-16 local ActorGroup communication-operation foundation over I011-15 membership: GroupRef runtime send/request keep the original Actor-transfer snapshot while a live Group has zero eligible members, select one READY member when routing can progress, and wake pending routing when an INITIALIZING member becomes READY.
- Keep concrete-Actor acceptance authoritative: membership removal or selected-member pre-acceptance failure cancels/requeues only while cancellation is known to beat acceptance; accepted work is never transparently rerouted, Group termination fails only still-pre-acceptance work, and accepted request loss resolves as RequestOutcomeUncertain.
- Reuse the existing SendOperation cancel/retry contract through a shared runtime control interface, and keep every concrete/group SendOperation local by rejecting the common control type at Actor and P transfer boundaries, without adding a new native Closure site. Public Group acquisition/GroupRef selector installation and genuinely distributed transport ambiguity remain for later I011 slices.

## 0.2.120-SNAPSHOT

- Implement CLI004 standard-library module resolution for the official CLI with a reserved `std:<logical-name>` distribution namespace, logical relocation-independent `ModuleKey` identity, hidden `.protos` file mapping under `protos/lib/`, and explicit exclusion of bootstrap `core/`.
- Keep `std:` absolute and non-shadowable: invalid, missing, non-standard, extension-bearing, traversal-like, or otherwise non-portable spellings fail through the existing Core import Error path with no user search-path fallback.
- Wire the resolver through the existing Core bootstrap host boundary without changing normative Module semantics, and close the LIB001 import/distribution prerequisite while leaving Collections implementation itself READY and not started.

## 0.2.119-SNAPSHOT

- Implement I017-D1 Process-local standard byte-stream bindings with independently optional stdin/stdout/stderr bootstrap availability, non-waiting repeated view materialization, one shared input-consumption or output-ordering/backpressure domain per binding, and distinct stdout/stderr domains even when a host reuses one backend object.
- Expose exact byte-direction capability shape through construction-only helpers: stdin views have only `read`; stdout/stderr views have only `write`; no backend property implicitly adds Closable, Flushable, File, seek, text, Encoding, shutdown, or other authority. Actor transfer of the deliberately Actor-safe standard-stream proxy rematerializes a fresh wrapper to the same logical binding; P rejects the live I/O authority.
- Integrate standard-stream operations with ordinary I014 Future/commitment and Actor I/O cancellation machinery, including a two-sided Process-termination admission check and zero-consumption restoration when a cancelled stdin read races a late backend data completion. Register the two-site Process-stream resource bridge in I018, moving the audited boundary from 25 providers / 97 sites to 26 providers / 99 sites. I015 is not yet CLOSED on this definitive baseline, so I017-D2 remains dependency-blocked.

## 0.2.118-SNAPSHOT

- Implement I017-C standardized read-only Environment bootstrap snapshots: one stable acquisition outcome and canonical semantic snapshot per Process; duplicate-equivalent native names are rejected at establishment under the represented host name-identity rules; later host mutation cannot alter the captured mapping.
- Implement exact `get`/`contains` query semantics with lossless native-name representability checked before lookup, native name identity preserved, absent valid names distinguished from invalid queries, and value decoding deferred so `contains` can report an existing entry whose value is not portable Unicode while `get` fails only when that value is selected.
- Implement polymorphic `Environment.each` with callback callability validation before whole-snapshot `(String,String)` representability validation, zero callbacks on representation failure, canonical lexicographic Unicode-scalar name ordering, and receiver result on success; Environment is immutable, outside the required Core prelude and not a Map subtype.
- Allow ordinary Actor/P transfer of the immutable Environment snapshot with fresh destination semantic identity and per-transfer alias preservation, distinct from canonical re-acquisition through Process. Register the three-site Environment representation bridge in I018, moving the audited boundary from 24 providers / 94 sites to 25 providers / 97 sites. I017-C is CLOSED and I017-D1 becomes READY.

## 0.2.117-SNAPSHOT

- Implement I017-B canonical Process-argument bootstrap snapshots: one stable immutable snapshot per Process after complete one-time host capture, stable UNREPRESENTABLE outcome for invalid Unicode bootstrap data, exact `size`/zero-based `at`/polymorphic ordered `each`, no Array mutability, and no host argv re-read after establishment.
- Distinguish canonical acquisition from isolation transfer: repeated acquisition from the same Process returns the same semantic snapshot identity, distinct Processes remain distinct, while ordinary Actor and P transfer rematerialize immutable destination snapshots with fresh semantic identity and preserve aliases within one transfer graph.
- Register the three-site Process-argument representation bridge in the reviewed I018 boundary, moving the audited total from 23 providers / 91 sites to 24 providers / 94 sites. I017-B is CLOSED and I017-C becomes READY; the external I015 dependency remains only for I017-D2 Encoding accessors.

## 0.2.116-SNAPSHOT

- Implement I011-15 internal ActorGroup routing foundation: add one stable local ActorGroup runtime identity with explicit membership, live/terminated lifecycle, and routing eligibility that selects only READY concrete Actor members while allowing a live Group to have zero eligible members.
- Bind runtime-acquired GroupRef values to that concrete Group target without exposing mutable membership/controller state; independent acquisitions remain distinct GroupRef identities, while Actor-transfer rematerialization retains the same GroupRef identity and exact Group target.
- Keep member selection policy internal and make no Group-wide FIFO, broadcast, public acquisition, send/request, automatic reconciliation, Authority, controller, or transport promise in this slice. I011 remains IN_PROGRESS; Group communication operations and uncertainty are layered next.

## 0.2.115-SNAPSHOT

- Close I017-A by reconciling I017 with the concurrently published I011-14 Process-capability delegation substrate instead of duplicating it: runtime-provisioned Actor-local Process proxies, fresh explicit Actor rematerialization to the same logical Process authority, whole-graph alias preservation, authority-bearing descendant rebuilding, no authority amplification, and no Core P-transfer contract are already implemented and validated on current main.
- Record the phased I017 implementation plan. No public Process prototype, RootActor `process` bootstrap slot, Process accessor, host argument/environment/stream acquisition, or new native-Closure provider is added by I017-A itself. Process-termination rejection of actual accessor use will be enforced when the standard Process protocol becomes observable.
- Keep independent I017-B/C/D1 work unblocked while recording the external dependency I015 CLOSED -> I017-D2 for the standardized `stdinEncoding`/`stdoutEncoding`/`stderrEncoding` accessors.

## 0.2.114-SNAPSHOT

- Implement I011-14 runtime Process-capability Actor delegation: a host/runtime-provisioned represented Process proxy delegates to the future standard Process prototype, carries only authority into one existing logical Protos Process, and rematerializes as a fresh wrapper when explicitly transferred across an Actor boundary.
- Preserve whole-graph transfer aliasing for repeated references to one Process proxy while preventing a mutable wrapper alias across Actors; descendants whose delegation chain carries Process authority are rebuilt over the destination proxy, without manufacturing Filesystem, Node, Cluster, subprocess, or other authority.
- Keep Process outside isolated P transfer and leave the public Process prototype, RootActor bootstrap-local `process` slot, args/environment/standard-stream protocol, launcher provisioning, and Process I/O lifecycle to I017. No native-Closure provider or Core prelude surface is added; I011 remains IN_PROGRESS.

## 0.2.113-SNAPSHOT

- Close I016 Filesystem / File after final cross-slice validation of I016-A preflight/acquisition, I016-B positioned File semantics and lifecycle, I016-C append/aliasing, I016-D1 host-provisioned Filesystem.open integration, I016-D2 Actor/P authority-transfer boundaries, and I016-D3 deterministic integrated authority/race conformance.
- Preserve the post-D2/D3 I018 native boundary at the re-audited 23 providers / 91 native-Closure construction sites. A fresh current-main dependency audit, including the published I011-13 Process failure-domain / RootActor substrate, finds no unresolved Process-I/O implementation blocker; I016 is CLOSED and I017 Process I/O / bootstrap becomes READY for its own mandatory current-main normative/implementation audit before implementation.

## 0.2.112-SNAPSHOT

- Implement I011-13 internal Process failure-domain and RootActor failure-authority substrate: one Process owns exactly one RootActor plus its currently hosted Actors, Process termination requests termination of every hosted incarnation, and Process TERMINATED is reached only after Actor-required cancellation/cleanup has completed.
- Distinguish fatal Actor failure from graceful lifecycle termination. A non-root fatal Actor failure terminates only that incarnation under Core v0.1, while a RootActor fatal failure drives authoritative local Process termination without transferring the internal failure object to another Actor.
- Keep Actor.spawn creation inside the caller's already-bound local Process when such a Process runtime exists, while preserving the pre-existing standalone Actor path for unbound runtime/tests. No public Process capability, standard-stream provisioning, Group routing, replacement policy, Node/Cluster machinery, or OS-process termination API is introduced; I011 remains IN_PROGRESS.

## 0.2.111-SNAPSHOT

- Implement I016-D3 integrated Filesystem/File conformance: deterministic pre-commit cancellation and late-custody release, post-commit cancellation precedence, stable selected-resource binding across simulated namespace replacement, independent same-path opens with out-of-order completion, backend authority rejection without fallback, and descriptor-owned optional capability shape.
- Re-audit the complete post-D2 I018 native boundary and confirm it remains 23 providers / 91 native-Closure construction sites; strengthen the architecture guard with the explicit provider count and the invariant that Filesystem authority is absent from the Core prelude. I016-D4 becomes READY for final cross-slice validation and project-status/dependency closure.

## 0.2.110-SNAPSHOT

- Implement I016-D2 authority/transfer boundaries with explicit runtime markers for live File and host-provisioned Filesystem capabilities while keeping their language-visible ordinary-object protocols unchanged.
- Reject direct File/Filesystem authority and ordinary descendants carrying that authority at Actor transfer with `NonTransferableValue`, and at isolated P transfer with `NonParallelValue`; no proxy, reopen, handle duplication, ambient inheritance, or new native Closure provider is introduced. I016-D3 becomes READY for deterministic authority/race conformance and the final post-D2 I018 boundary re-audit.

## 0.2.109-SNAPSHOT

- Implement I016-D1 Filesystem open integration: host-provisioned open-only Filesystem capability, exact one/two-argument `open` bridge over the I016-A preflight/acquisition flow, and standard positioned/append File materialization through the I016-B/C File protocol with exact requested read/write/append authority.
- Tighten open options to the normative ordinary-object domain and document the backend authority contract for confinement, race-free selection, create/truncate commitment, stable selected-resource custody, and synchronous pre-commit cancellation cleanup. Register the single new resource/capability native bridge in the post-I018 boundary guard/inventory so the full suite remains architectural evidence. I016 remains IN_PROGRESS under the recorded D1-D4 completion plan.

## 0.2.108-SNAPSHOT

- Implement I011-12 GroupRef capability-identity foundation: add an opaque represented `GroupRef` value whose semantic reference identity is independent of Group identity, physical wrapper identity, and acquisition path.
- Integrate GroupRef with primitive semantic identity/identityHash and Actor-boundary transfer so repeated rematerializations preserve one capability identity and effective restriction descriptor, while independent acquisitions to the same Group remain distinct.
- Verify transferred GroupRef values remain valid `Map` and `IdentityMap` keys without exposing or copying mutable Group/controller/routing state. This slice intentionally adds no public Group acquisition, membership, routing, send/request, broadcast, Authority, or controller API; I011 remains IN_PROGRESS.

## 0.2.107-SNAPSHOT

- Implement I011-11 Actor-boundary keyed-collection transfer for `Map` and `IdentityMap`, preserving keyed insertion order, graph aliasing/cycles, local slots, and open/closed/frozen state while keeping the complete transfer atomic.
- Rebuild destination hash bookkeeping without invoking ordinary `hash`/`==` during snapshot formation: default identity-based Map keys receive the copied identity's hash, specialized recorded Map hashes are preserved, and IdentityMap entries receive the copied key's semantic `identityHash`.
- Align default `Object.hash` with the existing semantic `ProtosIdentity.identityHash` primitive so rematerialized identity-bearing capabilities such as `ActorRef` remain valid equal/hash keys. I011 remains IN_PROGRESS; Group/distributed routing, GroupRef/Process capability transfer, failure authority and RootActor/Process integration remain.

## 0.2.106-SNAPSHOT

- Close I018 Core self-hosting/bootstrap minimization after an exhaustive current-main inventory of every production `ProtosClosureValue.nativeClosure(...)` provider. The final boundary contains 90 native-Closure construction sites across exactly 22 Java providers, all classified as irreducible host execution/control, semantic-value representation, concurrency/runtime, or resource/capability bridges; no remaining standard slot is classified as faithfully source-expressible.
- Add `docs/project/CORE_NATIVE_BOUNDARY.md` as the non-normative maintenance inventory and `ProtosCoreNativeBoundaryArchitectureTest` as an executable guard over the exact provider set/counts, helper-backed runtime selector surfaces, migrated Object/Integer/Float source provenance, internal Bytes/ActorRef/SendOperation surfaces, and the Core-bootstrap direct-allocation boundary.
- Mark I018 CLOSED and lift the temporary coordination pause that held I016 at the already-published I016-C state. I016 may resume from I016-D after re-auditing the then-current `origin/main`. No normative specification changes are made.

## 0.2.105-SNAPSHOT

- Implement I011-10 graceful Actor lifecycle: add public `ActorRef.stop()` and `ActorRef.termination()`, establish the irreversible stop cutover without a stop Future, and provide fresh caller-local termination observation Futures tied to one concrete incarnation.
- Move Actor termination cancellation to the TERMINATING cutover: cancel all live Actor-local tasks (including detached work), pending Actor-originated non-task Futures and I/O operations, suppress accepted-but-undispatched work/bootstrap after the cutover, and reach TERMINATED only after required task cancellation unwind completes.
- Preserve producer commitment/acceptance boundaries, exact request uncertainty, non-preemption of already-running non-suspending turns, and independent/cancellable termination observations. I011 remains IN_PROGRESS; distributed/Group routing, specialized capability transfer, failure authority and RootActor/Process integration remain for later slices.

## 0.2.104-SNAPSHOT

- Continue I018 Core self-hosting/bootstrap minimization by constructing the internal standard `ActorRef` and `SendOperation` delegation prototypes in distributable `protos/lib/core/actor.protos` rather than allocating those prototype identities inside `ProtosStandardActorProtocol`.
- Supply the exact source-created prototypes to the Actor runtime installer, validate direct-`Object` parentage plus open/empty source shape before mutation, install only the existing native communication/lifecycle bridges (`send`, `request`, `cancel`, `retry`), freeze the exact supplied objects, and remove the construction-only helper bindings before prelude construction.
- Extend Actor API regression coverage for the exact supplied prototype identities/surfaces and update deterministic send/request fixtures to inject their prototype identities explicitly. `ActorRef` and `SendOperation` remain absent as public prelude bindings. No normative specification changes are made; I018 remains open only for the final exhaustive native-boundary inventory/architectural guard, and I016 remains frozen at I016-C.

## 0.2.103-SNAPSHOT

- Continue I018 Core self-hosting/bootstrap minimization by moving the standard default `Object.==` body from Java into distributable `protos/lib/core/object.protos`.
- Define the source-backed body as `this === other`, delegating exactly to Protos primitive semantic identity rather than host object identity; reuse the existing isolated frozen Object-source capture context and narrow symbolic-selector installation bridge, and remove the duplicated native equality closure from `ProtosStandardObjectProtocol`.
- Extend bootstrap provenance coverage so `Object.init`, `Object.==`, and `Object.!=` are all source-backed and share the isolated frozen source context. Existing default-equality/non-identity conformance remains focal. No normative specification changes are made, I018 remains open for the final native-bridge inventory/guard, and I016 remains frozen at I016-C.

## 0.2.102-SNAPSHOT

- Continue I018 Core self-hosting/bootstrap minimization by constructing the standard frozen-prelude bindings object in distributable `protos/lib/core/prelude.protos` instead of allocating and populating that object slot-by-slot in `ProtosCoreBootstrap`.
- Preserve the exact existing prelude surface and identities, including the complete validated Error taxonomy, direct `Context` parentage, absence of the construction-only `Bytes` binding, and shallow final freeze. Remove the Java-side Error-taxonomy export helper while retaining host-side topology validation.
- Add focused regression coverage for the exact prelude binding set and an architectural guard preventing `ProtosCoreBootstrap` from reintroducing direct `preludeBindings.createLocalSlot(...)` construction. No normative specification changes are made, and I016 remains frozen at I016-C.

## 0.2.101-SNAPSHOT

- Continue I018 Core self-hosting/bootstrap minimization by moving the standard `Float.negated()` body from Java into distributable `protos/lib/core/float.protos`.
- Express Float negation as `(0.0 - 1.0) * this`: constructing semantic binary64 `-1.0` through native subtraction avoids recursive use of `negated`, and native Float multiplication then validates the original receiver while producing exact binary64 sign inversion, including `+0.0`/`-0.0`, infinities, subnormals, and Core NaN semantics.
- Add focused provenance and receiver-domain regression coverage proving `Float.negated` is source-backed while the binary64 `*` representation primitive remains native-backed. No normative specification changes are made, and I016 remains frozen at I016-C.

## 0.2.100-SNAPSHOT

- Continue I018 Core self-hosting/bootstrap minimization by moving the standard ordinary `Integer.%` derived body from Java into distributable `protos/lib/core/integer.protos`.
- Express `%` as `(0 + this).mod(argument)`: the native standard Integer `+` on semantic zero validates the original receiver before any `mod` dispatch, then the existing native `mod` primitive supplies the normative truncation-toward-zero remainder semantics. Java retains only the narrow symbolic-selector installation bridge from the temporary source name to `%`.
- Add provenance and adversarial receiver-domain regression coverage proving `%` is source-backed, `mod` remains native-backed, the temporary source slot is removed, and an incompatible Integer-delegating object cannot bypass standard receiver validation by overriding `mod`. No normative specification changes are made, and I016 remains frozen at I016-C.

## 0.2.99-SNAPSHOT

- Implement I011-9 concrete-Actor `ActorRef.request(selector, arguments...)`: reuse the I011-7/I011-8 delivery boundary, return a fresh caller-domain Future, transfer the normal handler result back across the Actor boundary, and never flatten or adopt a destination-local Future.
- Complete direct concrete-Actor accepted-work loss notification: accepted-but-undispatched work lost at termination becomes post-acceptance failure; request loss/fatal handler failure maps to a fresh caller-domain `RequestOutcomeUncertain`, non-transferable replies fail with `NonTransferableValue`, and cancellation preserves the pre/post-acceptance distinction. Distributed/Group routing uncertainty, lifecycle observation/stop, specialized transfers, and final Actor cleanup remain for later I011 slices.

## 0.2.98-SNAPSHOT

- Continue I018 Core self-hosting/bootstrap minimization by moving the standard ordinary `Integer.negated()` body from Java into distributable `protos/lib/core/integer.protos` as ordinary Protos behavior (`0 - this`).
- Keep exact unbounded Integer subtraction host-backed as the representation primitive; using semantic Integer zero as the subtraction receiver preserves the existing Integer receiver-domain rejection even when an incompatible ordinary object merely delegates to `Integer`.
- Add focused provenance and receiver-domain regression coverage proving `negated` is source-backed while `Integer.-` remains native-backed. No normative specification changes are made, and I016 remains frozen at I016-C.

## 0.2.97-SNAPSHOT

- Continue I018 Core self-hosting/bootstrap minimization by constructing the internal standard `Bytes` factory/prototype used by buffered byte wrappers from distributable `protos/lib/core/bytes.protos` instead of allocating that standard identity in Java.
- Preserve the existing native Bytes constructor/indexing/mutation/snapshot/parallel-region protocol unchanged, including factory-receiver parentage of produced Bytes values; remove the construction-only `Bytes` binding before the frozen standard prelude is built because Core v0.1 does not require a `Bytes` prelude binding.
- Add a bootstrap regression that `Bytes` remains absent from the standard prelude. I016 remains frozen at I016-C.

## 0.2.96-SNAPSHOT

- Continue I018 Core self-hosting/bootstrap minimization by constructing the standard frozen-prelude `import` facility from distributable `protos/lib/core/import.protos` instead of allocating that public standard identity in Java.
- Keep module-specifier validation, host resolution, canonical ModuleKey handling, Actor-local caching, cache-before-execute, cycles, initialization failure, and retry semantics in the existing runtime; Java now installs only the `call` primitive bridge into the exact source-created import object and freezes that same object.
- Add focused regression coverage for direct-`Object` parentage, exact `call` surface, native bridge provenance, and installer identity preservation. Restore the required full APL Part 5 notice on the touched import protocol/module runtime test sources. I016 remains frozen at I016-C.

## 0.2.95-SNAPSHOT

- Continue I018 Core self-hosting/bootstrap minimization by constructing the standard `BufferedReader` and `BufferedWriter` frozen-prelude factory/prototype objects from distributable Core source instead of allocating those public standard identities in Java.
- Keep buffered byte construction, capability validation, borrowing/ownership, Future, buffering, and lifecycle behavior host-backed; Java now installs only the existing `call`/`owning` primitive bridges into the exact source-created factory objects and freezes those same objects.
- Add focused regression coverage for direct-`Object` parentage, exact `call`/`owning` surface, preserved supplied factory identity, and native bridge provenance. Restore the required full APL Part 5 notice on the touched buffered protocol/test sources. I016 remains frozen at I016-C.

## 0.2.94-SNAPSHOT

- Continue I018 Core self-hosting/bootstrap minimization by constructing the standard `Actor` prelude entry object from distributable `protos/lib/core/actor.protos` instead of allocating that public standard object in Java.
- Keep `Actor.spawn` and `Actor.current` as host-backed primitive bridges, install them into the exact source-created Actor object, validate its direct-`Object` parent and empty/open source shape, and freeze that same object without changing Actor semantics.
- Add focused regression coverage that the Actor protocol installer preserves the supplied source object identity and contributes only native `spawn`/`current` behavior. I016 remains frozen at the already-published I016-C state.

## 0.2.93-SNAPSHOT

- Implement I011-8 public concrete-Actor `ActorRef.send(selector, arguments...)`: require an exact semantic String selector, form the complete Actor-boundary snapshot synchronously before admission, and dispatch accepted work against the destination Actor's stable behavior/message environment while ignoring the handler's normal result.
- Add the local identity-bearing SendOperation with exactly `cancel()` / `retry()`: cancellation succeeds only when known pre-acceptance cancellation wins; retry is explicit after known terminal delivery failure, creates a fresh operation identity, and reuses the original logical snapshot without source re-evaluation. SendOperation is local/non-transferable. Request/reply and distributed/accepted-work-loss uncertainty remain for later I011 slices.

## 0.2.92-SNAPSHOT

- Start I018 Core self-hosting/bootstrap minimization by moving standard `Object.init` and the body of standard `Object.!=` out of Java and into distributable `protos/lib/core/object.protos`.
- Load those source-backed Object Closures in an isolated frozen bootstrap context, promote them through a narrow host selector bridge, and prevent the process-global root `Object` from retaining the main Core-construction bindings.
- Keep observable Object initialization and inequality semantics unchanged; remaining Java-installed standard protocols are left for later bounded I018 slices rather than expanded. I016-C was already published before this slice; I018 does not advance I016 further.

## 0.2.91-SNAPSHOT

- Implement I011-7 concrete-Actor pre-acceptance delivery admission/backpressure substrate: keep pending logical operations outside the bounded accepted mailbox, wake admission deterministically as capacity is released, and preserve known acceptance/cancellation boundaries.
- Use a FIFO pending discipline to satisfy same-sender FIFO across backpressure and Core weak admission fairness without exposing a new public queue or total-order contract. Public ActorRef send/request, SendOperation retry/reply/uncertainty, specialized transfers, and lifecycle observation remain for later I011 slices.

## 0.2.90-SNAPSHOT

- Implement I016-C append-mode File placement and aliasing semantics: every non-empty append write selects the then-current EOF at the operation's contribution boundary, successful empty append leaves the logical cursor unchanged, failed writes preserve the exact contributed-prefix aftermath, and an earlier seek never turns append into positioned output.
- Define the backend AppendWritableResource contract as an underlying-resource-wide atomic append-placement boundary shared across distinct File/resource aliases, preventing overlap/interleaving while leaving concurrent alias order nondeterministic. Add deterministic cancellation, Actor-termination, ordering, capability-honesty, and cross-alias conformance coverage. Filesystem authority/confinement and public open materialization remain for the final I016 slice.

## 0.2.89-SNAPSHOT

- Implement I016-B positioned standard File capability: stable per-File logical cursor beginning at zero, explicit positional read/write backend boundary, ordered read/write/position/seek/seekToEnd/size/truncate/sync operations, exact failed-write prefix position aftermath, bounded write snapshot admission, and capability-honest stable protocol shape.
- Integrate File Closable lifecycle with I014 commitment and Actor-termination cancellation machinery; close-cutover now invokes the cancellation hook of accepted uncommitted I/O before resource release begins, while committed operations keep their normal aftermath. Raw File does not invent Flushable, Filesystem.open is still not publicly installed, and append/confinement host integration remain for later I016 slices.

## 0.2.88-SNAPSHOT

- Implement I011-6 internal bounded Actor mailbox ownership and READY-gated implicit event-loop dispatch; accepted message turns remain finite, FIFO in accepted order, and undispatched while the destination is INITIALIZING.
- Add automatic Actor-local scheduler wakeups and a weak-fair cross-Actor scheduler that selects one non-preemptive segment per Actor turn, permits independent Actors to use different carriers, and never executes two Protos segments concurrently in one Actor incarnation. I011 remains IN_PROGRESS; pre-acceptance admission/backpressure/FIFO fairness and the public send/request operation semantics remain for later slices.

## 0.2.87-SNAPSHOT

- Implement I016-A Filesystem open preflight/acquisition substrate: exact invocation-time capture of local standard open options, deterministic invalid-combination rejection before backend authority, host-neutral asynchronous acquisition, and independent open dispatch without an implicit Filesystem/Path FIFO.
- Reuse the established I/O commitment and Actor-termination cancellation machinery so pre-commit cancellation contributes no portable filesystem effect, committed create/truncate effects cannot be rewritten as cancelled, and a result-only open that loses cancellation releases untransferred backend custody. No public Filesystem/File surface is installed by this foundational slice; I016 remains IN_PROGRESS.


## 0.2.86-SNAPSHOT

- Implement I011-5 public Actor prelude surface with exactly `spawn` and `current`; perform exact semantic-String validation, one creator-side canonical module resolution, and the I011-4 atomic initialization-vector transfer before the creation cutover.
- Kick off I011-3 destination-local bootstrap only after the cutover and return the stable ActorRef without waiting for READY; preserve current-ActorRef identity and same-incarnation termination on later bootstrap failure. I011 remains IN_PROGRESS; mailbox/send/request, stop/termination observation, RootActor integration, distributed routing, and remaining specialized transfers stay outside this slice.

## 0.2.85-SNAPSHOT

- Implement I011-4 Actor graph snapshot/value-transfer foundation: add one Actor-specific atomic graph-copy boundary with shared-operation memoization, preserving aliases and cycles across roots while copying transferable scalar values, ordinary object state, Arrays, Bytes state, and Paths.
- Rematerialize ActorRef capability wrappers without retargeting or copying Actor state; preserve ordinary delegation/local-slot/mutation state for copied objects; reject Closure, Future, execution-context, ByteRegion, unknown host/runtime values, and not-yet-integrated keyed collections with the standard NonTransferableValue occurrence before any snapshot is exposed. I011 remains IN_PROGRESS and no Actor public API is installed by this slice.

## 0.2.84-SNAPSHOT

- Implement I011-3 Actor bootstrap and behavior cutover: bind each concrete Actor to its existing execution domain, expose only a runtime-local current-ActorRef substrate, load bootstrap code by an already-canonical ModuleKey in the destination Actor-local module cache, require an exact local bootstrap binding, invoke it with already-transferred arguments, and install the exact ordinary-object result before the READY cutover.
- Preserve canonical module identity without destination re-resolution, Actor-local module instances, stable behavior identity after READY, and initialization-failure termination. Public Actor.spawn/current remain deliberately uninstalled until synchronous graph transfer and the remaining runtime prerequisites are implemented; mailbox/admission, send/request, stop/termination monitoring, GroupRef, and distributed routing remain outside this slice.

## 0.2.83-SNAPSHOT

### Fixed
- Implement the normative standard Number ordering selectors `<`, `<=`, `>`, and `>=` as ordinary Number-owned Closure-valued behavior inherited by Integer, fixed-width Integer, and Float values.
- Compare numeric families without promotion or coercion, including exact arbitrary-precision Integer versus finite binary64 Float ordering, signed zero, infinities, and unordered NaN behavior.
- Add regression coverage for the official recursive factorial form in a persistent multiline REPL session.

### Notes
- Standard ordering rejects non-Number arguments and incompatible original receivers with a Protos Error.
- No parser, multiline-REPL, arithmetic compatibility, or normative specification behavior is changed.

## 0.2.82-SNAPSHOT

- Implement I011-2 ActorRef capability semantics: represent ActorRef as an opaque communication capability permanently bound to one Actor incarnation and add explicit Actor-boundary rematerialization that creates fresh wrappers while preserving semantic identity, `identityHash`, delegation parent, and the original target.
- Keep the mutable Actor target outside Protos-visible state and preserve references after termination without retargeting replacements. This slice intentionally does not add Actor.spawn/current, bootstrap/READY behavior installation, mailbox/admission, graph snapshot traversal, send/request, stop/termination monitoring, GroupRef, or distributed routing.

## 0.2.81-SNAPSHOT

- Implement I011-1 Actor incarnation identity/lifecycle foundation: add an explicit runtime-local immutable incarnation identity, centralized race-safe `INITIALIZING`/`READY`/`TERMINATING`/`TERMINATED` state machine, and a semantic `ActorRef` value permanently bound to one incarnation.
- Reuse the existing Actor execution domain and Actor-local module state, preserve `ActorRef` semantic identity and `identityHash` across rematerialized wrappers, and add deterministic lifecycle/concurrency focal tests. I011 remains open; this slice does not add spawn/current, mailbox, send/request, backpressure, transfer, monitoring, graceful-stop policy, GroupRef, or distributed routing.

## 0.2.80-SNAPSHOT

### Fixed
- Implement CLI003 multiline REPL input so a complete JLine bracketed-paste payload is compiled and evaluated once instead of being split into per-line evaluations.
- Accumulate interactive and stream REPL source while the parser reports an unexpected end of source, preserving nested closures, object/block source, blank lines, and persistent top-level context until the syntactic unit is complete.
- Keep invalid non-EOF syntax recoverable without poisoning the next REPL input, and make Ctrl-C discard the entire pending interactive unit.

### Notes
- Parser completeness uses the actual EOF token carried by `ParseError`; CLI003 does not count delimiters or introduce a separate syntax heuristic.
- JLine history continues to store non-blank physical lines independently, while evaluation now operates on the complete source unit.
- File execution and `-e` continue to use the existing source compiler path unchanged, and the Truffle `sun.misc.Unsafe` warning remains outside CLI003.
- No normative specification revision is changed.

## 0.2.79-SNAPSHOT

- Complete I014-G buffered byte-I/O lifecycle/cancellation conformance: close cutover now terminates accepted-but-uncommitted adapter operations with fresh lifecycle failures, active cancellation is propagated to lower Futures without rolling back committed effects, close retains its activation across asynchronous completion, in-flight buffered flushes are reconciled without duplicate propagation, and owning close waits for the owned target close while preserving a primary wrapper-finalization failure.

## 0.2.78-SNAPSHOT

- Implement I014-F standard buffered byte I/O: frozen `BufferedReader`/`BufferedWriter` factories with borrowing/owning forms, ordered bounded buffering, transparent/resumable EOF, recursive flush propagation, deterministic wrapper close ownership/failure handling, permanent output-side failure after ambiguous propagation, fresh standard Futures, and focal conformance tests.

## 0.2.77-SNAPSHOT

- Implement I014-E standard byte I/O directional shutdown: capability-honest `ReadShutdown`/`WriteShutdown`, irreversible directional cutovers, idempotent fresh-Future lifecycle observation, pending-read cutover, output-frontier ordering, cancellation/commitment behavior, and focal tests.

## 0.2.76-SNAPSHOT

- Implement I014-D standard byte I/O durability: explicit `Syncable.sync()` capability, ordered durability frontiers integrated with I014-C sequence-state ordering, and cancellation/commitment semantics that prevent post-commit sync from becoming cancelled.

## 0.2.75-SNAPSHOT

- Implement I014-C standard byte I/O positioning: `Flushable.flush`, `ByteSeekable` position/seek operations, `ByteSized.size`, and failure-atomic non-extending `Truncatable.truncate`, integrated with I014-B transfer ordering and Future cancellation/commitment semantics.


## 0.2.74-SNAPSHOT

- Implement I014-B standard sequential byte transfer: ordered asynchronous `ByteReadable.read(maxBytes)` and `ByteWritable.write(bytes)`, Future-based validation/errors, EOF/partial-read behavior, write snapshotting, bounded admission, cancellation/commitment integration, and focal tests.

All notable changes to the Protos implementation project will be documented in this file.

For specification changes, see [spec/PROTOS_SPEC_CHANGELOG.md](spec/PROTOS_SPEC_CHANGELOG.md).

## 0.2.73-SNAPSHOT

- Implement I010 isolated parallel execution: Closure.parallel, deterministic Array parallelMap/parallelFilter/parallelFindIndex/canonical parallelReduce/stable parallelSort, and exclusive Bytes/ByteRegion parallelRange.
- Add bounded lazy process-local P carriers, graph isolation and per-P Closure relowering, nested-P helping, Future structured ownership/cancellation, deterministic failure precedence, and atomic ByteRegion publication.
- Preserve caller standard-prototype mutability while restricting direct P physical sharing to already-frozen standard identities. No public P/Task/scheduler value, parallelEach, I011, or I014 surface is introduced.

## 0.2.72-SNAPSHOT

- Implement I009 — Future/Task: standard Future prototype/value state machine, `closure.future()`, suspendable `value()`, cancellation, `then()`, deterministic `Future.all(...)`, adoption/flattening, and `detach()`.
- Integrate pending Future waiters with the I009B evaluator bridge and I009A Actor-local Task scheduler, including race-safe register/suspend handoff, multiple waiters, cancellation cleanup, exact resume, and first-terminal-wins.
- Preserve Future/result/Error identity, semantic null, fresh Cancelled observations, Actor-domain isolation, continuation non-reentrancy, and deterministic aggregate ordering.
- Add focal end-to-end tests across real Truffle suspension/resumption plus protocol/state-machine coverage. No normative specification revision is changed.

## 0.2.71-SNAPSHOT

- Implement I009B evaluator suspension/resumption bridge for ordinary Truffle-backed Protos execution using Actor-local per-task continuation state.
- Preserve completed expression effects across cooperative suspension while rebuilding only the host Java/Truffle call stack, including nested Closure invocation activation and return-home state.
- Add explicit cancellation-aware wait cleanup and deterministic end-to-end coverage for peer-task progress, exact resume values, side-effect non-repetition, nested evaluation/non-local return, cancellation wake-up, and no double resume.
- No normative specification revision is changed.

## 0.2.70-SNAPSHOT

- Implement I009A internal Task/Actor execution infrastructure: Actor-local cooperative FIFO runnable queues, race-safe Task state transitions, suspension/resume, cooperative cancellation wake-up, and structured parent/child ownership.
- Keep cancellation of a suspended Task independent from its observed wait dependency so Future.value() can later wake for cancellation without cancelling or completing the observed Future.
- Add deterministic focal runtime tests for Actor isolation, dispatch ordering, duplicate resume exclusion, cancellation boundaries/races, structured ownership, and terminal transition safety.

## 0.2.69-SNAPSHOT

- Implement I008 standard module runtime semantics: exact semantic-String `import(specifier)`, host-produced canonical `ModuleKey`, Actor-local module caches, cache-before-execute cycles, single evaluation, failure eviction, and retry.
- Execute imported module source exclusively through `ProtosSourceCompiler`; module instances are their ordinary `moduleContext` objects and partially initialized state is directly observable during cycles.
- Translate host resolver/source/compiler failures to Core `Error` signaling without exposing Java exceptions, while leaving host resolution policy and Filesystem APIs outside I008.
- Add focal module conformance coverage for semantic membership, canonical identity, Actor isolation, cycles, cache behavior, retry, and host-error translation.

## 0.2.68-SNAPSHOT

- Implement Standard Bytes semantics: add receiver-owned mutable standard Bytes state and an explicitly installable standardized Bytes factory/prototype without adding a mandatory Core-prelude binding.
- Add exact octet validation (semantic Integer 0..255), zero-based size/at, replacement-only atPut, open-only add/removeAt, and ascending-snapshot each through ordinary polymorphic invocation.
- Preserve identity-bearing defaults for ==, hash, === and identityHash; byte contents are not traversed for equality or hashing.
- Add focal I012 conformance tests for construction, membership, lookup, numeric boundaries, mutation state, snapshot iteration, identity/equality/hash, bracket indexing, and Core Error paths.
- Do not add String/Encoding/Text I/O, UTF-8 coercion, slicing, concatenation, iterators, Array conversion, Filesystem, Set, or IdentitySet behavior.

## 0.2.67-SNAPSHOT

### Added
- Implemented I013 Standard Path with portable represented values and the normative `relative`, `rooted`, `child`, and `parentComponent` construction protocol.
- Added filesystem-independent structural equality/hash while preserving D037 ordinary individual identity and IdentityMap behavior.
- Added focused conformance coverage for construction, lookup, receiver domains, frozen-prelude binding, D037, and absence of host path parsing.

### Notes
- No filesystem authority, host normalization, realpath, separator interpretation, or String-to-Path coercion is introduced.
- No normative specification revision is changed.

## 0.2.66-SNAPSHOT

### Fixed
- Changed interactive REPL history handling so a bracketed paste containing multiple complete lines is stored as one JLine history entry per line, matching the already independent per-line evaluation behavior.
- Added focused coverage for multiline history splitting and ordering.

### Notes
- History remains implemented by JLine; Protos does not introduce a separate readline/history subsystem.
- Structured syntactic multiline parsing remains out of scope: pasted complete lines are still evaluated independently.
- No normative specification revision is changed.

## 0.2.65-SNAPSHOT

### Fixed
- Corrected the CLI002 multiline-paste regex Java string literal so the regex engine receives `\R` through a valid Java string literal.
- This is a compile-fix only; REPL semantics and the previously added line-by-line paste handling are unchanged.
- No normative specification revision is changed.

## 0.2.64-SNAPSHOT

### Fixed
- Fixed CLI002 bracketed-paste handling so a JLine `readLine` result containing multiple complete lines is split and evaluated sequentially in the same persistent REPL session.
- Added a focused regression test for the actual JLine-style multiline paste payload, rather than only testing newline-separated stream input.
- Added `--enable-native-access=ALL-UNNAMED` to the launcher to suppress the JDK restricted-native-access warning emitted when JLine initializes its native terminal support.

### Notes
- This does not add structured syntactic multiline parsing; each pasted complete line remains an independent evaluation.
- The pre-existing Truffle `sun.misc.Unsafe` warning is unchanged and remains outside CLI002.
- No normative specification revision is changed.

## 0.2.63-SNAPSHOT

### Added
- Added CLI002 interactive terminal UX using JLine 3.30.6 for readline-style editing, session history, bracketed paste, Ctrl-C cancellation, and clean Ctrl-D exit.
- Preserved the stream-based REPL path for automated tests and non-terminal input; pasted complete lines remain independent evaluations with one persistent Protos context.

### Notes
- CLI002 changes terminal input only; the existing parser/canonical/lowering/Truffle evaluation pipeline is unchanged.
- Structured syntactic multiline input is not added because CLI002 does not introduce parser-completeness heuristics.
- Protos Error rendering remains minimal (`Error: <object>`); Truffle/JDK `sun.misc.Unsafe` warnings are outside CLI002.
- No normative specification revision is changed.

## 0.2.62-SNAPSHOT

### Added
- Added CLI001 basic executable CLI with file execution, `-e`, help/version, and persistent-context REPL.
- Added non-normative value rendering, focused CLI/REPL tests, `bin/protos`, and executable shaded-JAR packaging.

### Notes
- CLI001 reuses the normal parser/canonical/lowering/Truffle pipeline.
- Top-level script arguments remain unavailable because Core `args` is Closure-invocation-only.
- No normative specification revision is changed.

## 0.2.61-SNAPSHOT

### Added
- Implemented I006 IdentityMap with semantic identity hashing plus primitive `===`.
- Completed fixed-width Integer identity and identity-hash coherence.
- Added IdentityMap conformance coverage.

### Notes
- Standard Map remains on `hash` plus `==`; Set and IdentitySet remain out of scope.
- No normative specification revision is changed.

## 0.2.60-SNAPSHOT

### Added
- Implemented I005 Standard Map: Map(), at, atPut, containsKey, remove, size, insertion-order each, semantic receiver-domain checks, recorded hashes and query-key equality.
- Added Map-required standard hash behavior for ordinary objects, Numbers and Strings.
- Set remains explicitly out of scope.

### Notes
- No normative specification revision is changed.

## [0.2.59-SNAPSHOT] - 2026-09-04

### Added

- I003 — implemented the standard semantic String prototype and represented-value
  delegation through the generalized I002 lookup bridge.
- Added standard String `size`, `at`, bracket-read, and binary `+` behavior with
  receiver-domain validation and Protos Error signaling.
- Pinned ICU4J 78.1 and require Unicode 17 data so `String.size` and `String.at`
  use Unicode 17.0.0 default extended grapheme clusters rather than host/JDK text
  segmentation.
- Added conformance coverage for empty/ASCII/Unicode/supplementary Strings,
  exact-scalar equality/identity, fixed-width Integer indexing, invalid indexes,
  concatenation, and non-membership by delegation.

### Notes

- String semantic identity remains exact Unicode-scalar-sequence identity; no
  normalization, coercion, encoding, Bytes, module, or text-I/O behavior is added.
- No normative specification change is introduced.
- Maven remains intentionally outside the installer and is run manually.
- Project implementation version is `0.2.59-SNAPSHOT`.

## [0.2.58-SNAPSHOT] - 2026-09-04

### Fixed

- I007 follow-up — SlotNotFound test-prelude compatibility: updated the lightweight
  test prelude to expose the normative `SlotNotFound -> Error` prototype and
  corrected legacy lookup/member/message/bare-assignment assertions to expect
  fresh `SlotNotFound` occurrences.
- Repaired the malformed implementation version `0.2.57-SNAPSHOT-SNAPSHOT`
  produced by the original I007 installer.

### Notes

- No normative specification change is introduced.
- Maven remains intentionally outside the installer and is run manually.
- Project implementation version is `0.2.58-SNAPSHOT`.

## [0.2.57-SNAPSHOT] - 2026-09-04

### Added

- I007 — Core error infrastructure: installed the closed normative Core Error
  prototype taxonomy and exact parent relations.
- Added typed fresh-occurrence factories and exact-object signaling through
  `ProtosCoreErrors`.
- Installed `Error.signal()` with receiver/arity validation, no implicit
  condition-designator coercion, and exact signaled-object preservation.
- Added focused tests for freshness, hierarchy, `InvalidReturn`, signaling,
  incompatible receivers, and host/program-error separation.

### Fixed

- Normative lookup absence now creates fresh `SlotNotFound` occurrences rather
  than generic `Error` occurrences.

### Notes

- Future, Actor, parallel, I/O, and filesystem execution remain reserved to
  their subsystem implementations; I007 only exposes the standard Core
  prototypes/factory infrastructure they require.
- `Error.handle(body, handler)` is not faked with a Java catch. The current
  runtime does not yet contain the handler-frame/unwind machinery required to
  implement the normative deactivation-before-cleanup rule correctly.
- No normative specification change is introduced.
- Project implementation version changed from `0.2.56-SNAPSHOT` to `0.2.57-SNAPSHOT`.

## [0.2.56-SNAPSHOT] - 2026-09-04

### Changed

- I002 — uniform represented-value lookup: introduced a small internal
  `ProtosRepresentedValue` bridge that supplies only the immediate delegation
  parent used by ordinary lookup for specialized runtime representations.
- Migrated Integer, Float, fixed-width Integer, canonical Boolean, and canonical
  `null` lookup onto the uniform bridge, removing the growing represented-value
  `instanceof` chain from `ProtosValueLookup`.
- Unified activation receiver lookup through `ProtosValueLookup`, preserving
  ordinary-object lookup while allowing represented values whose normative
  parent does not require the Core prelude to participate in the same path.
- Added regression coverage for ordinary lookup, numeric/fixed-width lookup,
  Boolean/null delegation, original-receiver binding, receiver-domain rejection,
  bridge extensibility, and polymorphic invocation.
- Project implementation version changed from `0.2.55-SNAPSHOT` to
  `0.2.56-SNAPSHOT`.

### Notes

- No normative specification change is introduced.
- No new Core family or standard prototype is materialized by I002. In
  particular, the existing String runtime representation is left unchanged until
  its standard Core bootstrap/protocol work is implemented; future specialized
  families can adopt the same bridge without adding dispatcher cases.


## [0.2.55-SNAPSHOT] - 2026-09-04

### Added

- Completed the I004 standard Array conformance audit against the current
  normative collection, object-model, callable, and Error contracts.
- Added exhaustive Array completion coverage for fresh/empty construction,
  dense boundary indexing, exact indexed-mutation result, closed/frozen
  mutation behavior, semantic Integer size, polymorphic non-Closure callback
  invocation, shallow snapshot order, callback failure propagation, ordinary
  identity/default equality, and receiver-domain rejection.
- Added explicit coverage that an ordinary object delegating to `Array` does
  not acquire standard Array indexed state, while an inherited Array factory
  still creates a real standard Array whose parent is the invocation receiver.
- Project implementation version changed from `0.2.54-SNAPSHOT` to `0.2.55-SNAPSHOT`.

### Notes

- The audit found the existing standard Array runtime behavior already aligned
  with the current normative surface; this completion closes the remaining
  conformance gaps without changing Array runtime semantics.
- No Array literal, growth, slicing, insertion/removal, negative-from-end
  indexing, or unrelated collection behavior is introduced.
- I001 (`args`), I002 (represented-value lookup), I007 (Error infrastructure),
  and the normative specification are intentionally untouched.



## [0.2.54-SNAPSHOT] - 2026-09-04

### Fixed

- I001: `args` now lowers uniformly to `ProtosArgsNode` instead of retaining a
  generic-lowering `UnsupportedOperationException` path for nested canonical
  expression shapes.
- Object-construction activations preserve the enclosing invocation's already
  materialized `args` Array while retaining existing construction `context`,
  receiver, lexical-capture, method-home, and return-home semantics.
- Added end-to-end coverage for zero/one/multiple arguments, ordering, mixed
  value families, element identity, fresh Array identity, nested Closure and
  method calls, polymorphic invocation, non-local return, and object bodies.
- Replaced the obsolete regression that explicitly expected `args` lowering to
  remain unimplemented.
- Project implementation version changed from `0.2.53-SNAPSHOT` to
  `0.2.54-SNAPSHOT`.

### Notes

- `args` remains the fresh frozen standard Array established from the flattened
  caller-supplied positional vector; receiver/default values are not inserted.
- No normative specification change is introduced.


## [0.2.53-SNAPSHOT] - 2026-09-04

### Fixed

- Updated `CanonicalizerEqualityTest` to the normative P66 lowering:
  source `!=` canonicalizes to an ordinary `!=` message send, and source `!==`
  canonicalizes to the dedicated non-dispatchable `CanonicalNotIdentity` form.
- Removed the obsolete test expectations that both operators lowered through
  the unrelated `not` selector.
- Project implementation version changed from `0.2.52-SNAPSHOT` to
  `0.2.53-SNAPSHOT`.

### Notes

- No runtime semantics changed.
- No normative specification change is introduced.


## [0.2.52-SNAPSHOT] - 2026-09-04

### Added

- Implemented standard `Object.==` using Protos semantic identity as the default
  equality for receivers without a nearer equality override.
- Implemented standard `Object.!=` as the strict Boolean complement of the
  receiver's dynamically selected current `==` behavior.
- `Object.!=` propagates equality errors and rejects a non-Boolean normal result
  instead of applying truthiness.
- Added a dedicated canonical non-identity form and Truffle execution node so
  `!==` is the primitive Boolean complement of `===` with no `==`, `!=`, or
  `not` message dispatch.
- Corrected source `!=` lowering to send the ordinary `!=` selector rather than
  synthesizing a `not` send after `==`.
- Added Java and executable `.protos` conformance coverage for default object
  equality, numeric inequality, fixed-width cross-family inequality, and
  primitive non-identity.
- Project implementation version changed from `0.2.51-SNAPSHOT` to
  `0.2.52-SNAPSHOT`.

### Notes

- Prefix `!` remains a separate implementation/spec audit item; this patch does
  not invent standard `not` behavior.
- No normative specification change is introduced.


## [0.2.51-SNAPSHOT] - 2026-09-04

### Fixed

- Corrected the standard numeric-equality wrong-arity regression test so it
  invokes the `==` selector directly with two supplied arguments through
  `ProtosInvocation`.
- Removed the invalid source spelling `1.==(1, 2)`, which is rejected by the
  parser before message dispatch and therefore could not test runtime arity
  behavior.
- Project implementation version changed from `0.2.50-SNAPSHOT` to
  `0.2.51-SNAPSHOT`.

### Notes

- No numeric equality runtime semantics changed.
- No normative specification change is introduced.


## [0.2.50-SNAPSHOT] - 2026-09-04

### Added

- Implemented standard Number-family `==` as an ordinary `Number`-owned
  Closure-valued slot inherited by Integer, Float, and all fixed-width integer
  prototypes.
- Numeric equality compares mathematical numeric value across semantic numeric
  families without performing arithmetic coercion or narrowing.
- Added exact Float-vs-exact-integer comparison using the actual represented
  binary64 value, avoiding rounded host-integer comparison.
- Implemented IEEE-style NaN equality (`NaN == x` is always false), signed-zero
  numeric equality, infinity equality, and cross-family exact-integer equality.
- Standard Number equality with a non-Number argument returns canonical `false`.
- Added receiver-domain protection so ordinary objects that merely inherit
  Number-family `==` are not treated as semantic Numbers.
- Added Java and executable `.protos` conformance coverage for cross-family,
  exact-rounding, NaN, signed-zero, infinity, and non-Number cases.
- Project implementation version changed from `0.2.49-SNAPSHOT` to
  `0.2.50-SNAPSHOT`.

### Notes

- `!=` is intentionally not implemented in this slice. The current canonicalizer
  lowers it through a `not` send, but Core's normative Boolean section does not
  currently define a standard `not` selector. That normative gap is left
  untouched rather than inventing behavior.
- No normative specification change is introduced.


## [0.2.49-SNAPSHOT] - 2026-09-04

### Added

- Implemented the standard Boolean protocol selectors `ifTrue`, `ifFalse`,
  `and`, and `or` as ordinary Object-owned Closure-valued slots reached by the
  canonical `true`/`false` delegation bridge.
- Added exact Boolean receiver-domain enforcement: standard Boolean behavior
  accepts only canonical `true` and `false`.
- Implemented selected-path ordinary polymorphic callback invocation with zero
  positional arguments and exact propagation of normal results for `ifTrue`
  and `ifFalse`.
- Implemented path-local callability validation: callbacks on unselected paths
  are neither validated nor invoked.
- Implemented canonical Boolean result validation for selected `and` and `or`
  callbacks, with invalid normal results signaling Error.
- Added `boolean` and `null` language-conformance expectation kinds and
  executable `.protos` cases for selected/unselected paths, short-circuiting,
  invalid callback results, and non-invokable selected callbacks.
- Project implementation version changed from `0.2.48-SNAPSHOT` to
  `0.2.49-SNAPSHOT`.

### Notes

- No standard `Boolean` prototype or prelude binding is introduced.
- The already-published direct canonical Boolean delegation to `Object` remains
  unchanged.
- No truthiness conversion, implicit awaiting, hidden suspension, or callback
  pre-validation is introduced.
- No normative specification change is introduced.


## [0.2.48-SNAPSHOT] - 2026-09-04

### Added

- Added source-backed standard prototypes `UInt8`, `Int8`, `UInt16`, `Int16`,
  `UInt32`, `Int32`, `UInt64`, and `Int64`, each delegating directly through
  `Integer`.
- Added semantic fixed-width integer runtime values carrying exact family and
  mathematical value with range-enforced construction.
- Extended ordinary lookup to each fixed-width numeric prototype.
- Added all eight explicit fixed-width conversion factories with exact range
  checks for Integer, integral Float, and cross-family fixed-width inputs.
- Extended `Integer(...)` and `Float(...)` to accept fixed-width exact integers.
- Added Java and `.protos` conformance coverage and a `fixed-integer`
  expectation kind.
- Project implementation version changed from `0.2.47-SNAPSHOT` to
  `0.2.47-SNAPSHOT`.

### Notes

- Fixed-width arithmetic is intentionally deferred to P64.
- No implicit numeric promotion or wrapping is introduced.
- D027/B003 remains untouched.
- No normative specification change is introduced.


## [0.2.47-SNAPSHOT] - 2026-09-04

### Added

- Implemented the canonical Boolean delegation bridge: represented `true` and
  `false` now continue ordinary lookup directly through `Object`.
- Added regression coverage for inherited Object lookup, original-receiver
  dispatch through polymorphic invocation, and absence of a standard `Boolean`
  prelude prototype.
- Closed implementation blocker B003 after D027 normatively fixed the Boolean
  parent topology.
- Project implementation version changed from `0.2.46-SNAPSHOT` to
  `0.2.47-SNAPSHOT`.

### Notes

- Canonical Booleans remain the existing host singleton representations
  `ProtosBooleanValue.TRUE` and `ProtosBooleanValue.FALSE`.
- No standard `Boolean`, `Value`, or other synthetic Protos-visible ancestor is
  introduced.
- No normative specification document or specification revision changed.

## [0.2.46-SNAPSHOT] - 2026-09-04

### Fixed

- Corrected the P62 non-integral Float-to-Integer rejection test to use `1.5`,
  a binary64 value that is actually finite and mathematically non-integral.
- Removed the incorrect test assumption that the source literal
  `9007199254740991.5` remains non-integral after Float literal rounding; that
  decimal source rounds to the exact binary64 value `9007199254740992.0`,
  which is mathematically integral and therefore valid input to `Integer(...)`.
- Project implementation version changed from `0.2.45-SNAPSHOT` to
  `0.2.46-SNAPSHOT`.

### Notes

- No runtime conversion semantics changed.
- Existing P62 implementation remains unchanged.
- No normative specification change is introduced.
- D027/B003 remains untouched.


## [0.2.45-SNAPSHOT] - 2026-09-04

### Added

- Added standard one-argument ordinary invocation factories for the `Integer`
  and `Float` prototype objects.
- `Integer(value)` now accepts ordinary Integer values and finite mathematically
  integral Float values, returning the exact unbounded Integer without rounding
  or truncation.
- Float-to-Integer conversion derives the exact mathematical integer represented
  by binary64 bits rather than relying on decimal rendering or host narrowing.
- `Float(value)` now preserves existing Float semantic values and converts exact
  Integers with exact-to-binary64 `roundTiesToEven`, including precision loss and
  overflow to infinity required by Core.
- Added arity, non-Number, non-integral Float, NaN, infinity, and incompatible
  invocation-receiver rejection.
- Added receiver-domain protection so inheriting or copying a standard numeric
  factory `call` does not turn an ordinary object into a numeric conversion
  prototype.
- Added Java and executable `.protos` conformance coverage, including the
  adversarial exact value of `Integer(1e23)`.
- Project implementation version changed from `0.2.44-SNAPSHOT` to
  `0.2.45-SNAPSHOT`.

### Notes

- This slice implements the currently represented ordinary `Integer` and
  `Float` semantic families. Fixed-width conversion factories remain separate
  until those eight semantic families have runtime representations.
- No implicit numeric promotion or coercion is introduced by these explicit
  factories.
- D027/B003 remains untouched.
- No normative specification change is introduced.


## [0.2.44-SNAPSHOT] - 2026-09-04

### Added

- Added standard ordinary Float arithmetic for `+`, `-`, `*`, `/`, and unary
  `negated`.
- Standard Float arithmetic operates directly on semantic binary64 operands and
  produces binary64 results, including signed zero, subnormal/underflow,
  infinity, and NaN behavior required by IEEE 754-2019.
- Float division by zero and invalid IEEE arithmetic now produce the
  corresponding Float infinity or NaN rather than a Protos Error.
- Added Float receiver-domain validation and rejection of mixed Float/Integer
  arithmetic without implicit numeric promotion or coercion.
- Added Java coverage for normal arithmetic, signed-zero negation, division by
  zero, overflow, underflow, NaN-producing operations, mixed-family rejection,
  and copied-method incompatible receivers.
- Added executable `.protos` Float conformance programs and a semantic
  `float-nan` expectation that does not expose implementation-specific NaN
  payload or sign bits.
- Project implementation version changed from `0.2.43-SNAPSHOT` to
  `0.2.44-SNAPSHOT`.

### Notes

- Java `double` is used here only as the host representation of Protos binary64
  primitive operations; no wider intermediate value is retained across a Protos
  operation boundary.
- NaN conformance intentionally tests semantic NaN membership rather than raw
  NaN payload bits, which are not portable Protos surface semantics.
- Numeric comparison/equality/hash and explicit numeric conversion factories
  remain separate implementation slices.
- D027/B003 remains untouched.
- No normative specification change is introduced.


## [0.2.43-SNAPSHOT] - 2026-09-04

### Added

- Added standard ordinary `Integer / Integer` behavior returning the correctly
  rounded IEEE binary64 Float representation of the exact mathematical rational
  quotient.
- Added exact integer-arithmetic binary64 rounding with `roundTiesToEven`,
  including normal values, subnormals, signed underflow zero, overflow to
  infinity, and the normal/subnormal boundary.
- Integer division now rounds the exact rational quotient once rather than first
  converting each arbitrary-precision Integer operand to a host `double`.
- Added Java coverage for huge operands, halfway ties, subnormal rounding,
  signed zero, infinity, zero-divisor failure, and mixed-family rejection.
- Extended the language conformance manifest with `float-bits` expectations so
  Float results can be checked by exact raw binary64 representation.
- Added executable `.protos` conformance programs for Integer division,
  including adversarial cases where separate host-double operand conversion
  would produce the wrong result.
- Project implementation version changed from `0.2.42-SNAPSHOT` to
  `0.2.43-SNAPSHOT`.

### Notes

- This slice implements only ordinary `Integer / Integer`. Standard Float
  arithmetic and fixed-width integer-family division remain separate work.
- Exact zero divided by a nonzero Integer produces positive `0.0`; a nonzero
  exact quotient that rounds to zero preserves the quotient sign as required.
- D027/B003 remains untouched.
- No normative specification change is introduced.


## [0.2.42-SNAPSHOT] - 2026-09-04

### Added

- Added standard ordinary `Integer.div(argument)` quotient behavior with
  truncation toward zero.
- Added standard ordinary `Integer.mod(argument)` and `%` remainder behavior
  using `a - (a div b) * b`, preserving the dividend sign for nonzero
  remainders.
- Added zero-divisor and mixed-numeric-family rejection for standard Integer
  quotient and remainder operations.
- Added exact arbitrary-precision quotient/remainder coverage in Java and
  executable `.protos` conformance programs.
- Added conformance cases for positive and negative operands, zero divisors,
  mixed Integer/Float rejection, and `%` equivalence with standard `mod`.
- Project implementation version changed from `0.2.41-SNAPSHOT` to
  `0.2.42-SNAPSHOT`.

### Notes

- Ordinary Integer `/` remains separate work because Core requires its Float
  result to be the correctly rounded binary64 representation of the exact
  rational quotient; it must not be implemented by separately rounding large
  Integer operands to host doubles before division.
- Float `div`, `mod`, and `%` are intentionally not introduced.
- Fixed-width integer-family quotient/remainder behavior remains separate until
  those semantic families are represented by the implementation.
- D027/B003 remains untouched.
- No normative specification change is introduced.


## [0.2.41-SNAPSHOT] - 2026-09-04

### Added

- Added the first end-to-end Protos language conformance harness.
- Added executable `.protos` conformance programs under
  `protos/tests/conformance/`, keeping the language test corpus independent from
  the Java/Maven resource layout.
- Added an external tab-separated expectation manifest so conformance assertions
  remain outside the Protos language itself.
- Added P57 Integer conformance programs covering small arithmetic, negative
  results, arbitrary-precision overflow boundaries, unary negation, and
  mixed-family Error cases.
- Conformance programs execute through `ProtosSourceFileLoader`, the normal
  source compiler/lowering/runtime path, and a freshly bootstrapped Core prelude.
- Project implementation version changed from `0.2.40-SNAPSHOT` to
  `0.2.41-SNAPSHOT`.

### Notes

- The JUnit runner remains under `src/test/java`, but the Protos conformance
  corpus is intentionally implementation-layout-independent under `protos/tests`.
- Existing Java unit/integration tests remain valuable for implementation
  invariants; Protos conformance tests complement rather than replace them.
- No test-only Protos syntax, assertion primitive, privileged test object, or
  standard-library testing API is introduced.
- Future observable language slices should add `.protos` conformance programs
  when their behavior can be expressed through the executable language surface.
- D027/B003 remains untouched.
- No normative specification change is introduced.


## [0.2.40-SNAPSHOT] - 2026-09-04

### Added

- Added standard ordinary `Integer` arithmetic behavior for `+`, `-`, `*`, and
  unary `negated`.
- Ordinary Integer arithmetic now returns exact unbounded Integer values backed
  by arbitrary-precision arithmetic and therefore does not expose host-machine
  integer overflow.
- Standard Integer binary arithmetic rejects arguments from other numeric
  families instead of implicitly promoting or coercing them.
- Added receiver-domain validation so copying a standard Integer arithmetic
  Closure onto an ordinary object does not make that receiver a semantic
  Integer.
- Added coverage for large exact results, unary negation, mixed-family rejection,
  and incompatible receivers.
- Project implementation version changed from `0.2.39-SNAPSHOT` to `0.2.40-SNAPSHOT`.

### Notes

- Division, remainder/modulo, comparisons, conversion factories, Float
  arithmetic, and fixed-width integer families remain separate implementation
  work.
- D027/B003 remains untouched.
- No normative specification change is introduced.


## [0.2.39-SNAPSHOT] - 2026-09-04

### Fixed

- Corrected P56 `Array.each` tests so they validate callback order and shallow
  snapshot behavior without depending on the not-yet-implemented standard
  numeric `+` operator.
- The revised tests use ordinary invokable native-backed Closure values only as
  test callbacks, preserving the same `Array.each` invocation path while
  isolating the behavior under test.
- Project implementation version changed from `0.2.38-SNAPSHOT` to `0.2.39-SNAPSHOT`.

### Notes

- No `Array.each` runtime semantics are changed.
- No numeric arithmetic behavior is introduced.
- D027/B003 remains untouched.
- No normative specification change is introduced.


## [0.2.38-SNAPSHOT] - 2026-09-04

### Added

- Added standard `Array.size()` as a read-only ordinary Closure-valued protocol
  operation returning the semantic Integer indexed length.
- Added standard `Array.each(block)` with ordinary polymorphic callback
  validation, ascending shallow-snapshot traversal, one exact element argument
  per callback, and original-receiver normal result.
- Array iteration snapshots are isolated from later element replacement while
  preserving ordinary element identity and callback effects.
- Added coverage for open/closed/frozen size observation, iteration order,
  snapshot replacement behavior, callback validation, and exact receiver result.
- Project implementation version changed from `0.2.37-SNAPSHOT` to `0.2.38-SNAPSHOT`.

### Notes

- `each` invokes callbacks through the existing ordinary invocation protocol;
  it does not require callbacks to be Closures.
- No parallel Array operations are introduced.
- D027/B003 remains untouched.
- No normative specification change is introduced.


## [0.2.37-SNAPSHOT] - 2026-09-04

### Added

- Added standard ordinary `Array.at(index)` and `Array.atPut(index, value)`
  Closure-valued protocol slots on the source-backed `Array` object.
- Standard Array indexed reads now require a semantic Integer index in the dense
  range `0 <= index < length` and return the exact stored element.
- Standard Array indexed updates replace exactly one existing element, preserve
  length, return the exact supplied value, allow replacement on closed Arrays,
  and reject mutation of frozen Arrays before index validation.
- Added executable lowering for `CanonicalIndexedAssign`, preserving the
  syntax-level evaluation order receiver -> index -> RHS -> `atPut` and returning
  the exact RHS after normal `atPut` completion.
- Added coverage for custom `atPut` return values, closed/frozen Arrays,
  non-Integer indices, and bounds failures.
- Project implementation version changed from `0.2.36-SNAPSHOT` to `0.2.37-SNAPSHOT`.

### Notes

- Bracket syntax remains ordinary `at` / `atPut` protocol dispatch rather than a
  privileged Array runtime operation.
- No insertion, growth, holes, negative-from-end indexing, or Array literals are
  introduced.
- D027/B003 remains untouched.
- No normative specification change is introduced.


## [0.2.36-SNAPSHOT] - 2026-09-04

### Added

- Added the standard source-backed `Array` object's ordinary local `call`
  specialization as an Array factory.
- `Array(...)` now creates a fresh open `ProtosArrayValue` containing the exact
  supplied positional objects in order, with no cloning, freezing, or
  Integer-length overload.
- Inherited Array-factory invocation uses the original invocation receiver as
  the new Array's delegation parent, so ordinary descendants such as `MyArray`
  construct Arrays delegating to that descendant.
- Added receiver-domain validation so copying the standard Array factory Closure
  onto an unrelated object does not make that object a standard Array-family
  factory.
- Project implementation version changed from `0.2.35-SNAPSHOT` to `0.2.36-SNAPSHOT`.

### Notes

- The factory is installed as an ordinary Closure-valued `call` slot on the
  source-loaded `Array` object; no hidden invocation special case is added.
- Indexed Array protocol methods remain separate implementation work.
- D027/B003 remains untouched.
- No normative specification change is introduced.


## [0.2.35-SNAPSHOT] - 2026-09-04

### Fixed

- Corrected the P53 message-send spread test so it exercises spread flattening
  with an already-materialized `ProtosArrayValue` instead of depending on the
  still-pending standard `Array.call` factory specialization.
- Project implementation version changed from `0.2.34-SNAPSHOT` to `0.2.35-SNAPSHOT`.

### Notes

- No message-send runtime semantics are changed.
- No standard Array factory behavior is introduced by this correction.
- D027/B003 remains untouched.
- No normative specification change is introduced.


## [0.2.34-SNAPSHOT] - 2026-09-04

### Added

- Added executable lowering for `CanonicalSend` through a dedicated
  `ProtosSendNode`.
- Added direct ordinary message invocation for arbitrary Protos receivers using
  the existing semantic value lookup bridge, preserving original receiver and
  physical `methodHome`.
- Added coverage for local and inherited method sends, argument/spread handling,
  and missing-message Core Error behavior.
- Project implementation version changed from `0.2.33-SNAPSHOT` to `0.2.34-SNAPSHOT`.

### Changed

- Callable lowering now recursively supports nested `CanonicalSend` expressions
  in closure bodies and defaults.

### Notes

- Message send remains ordinary slot lookup plus Closure activation; no parallel
  host dispatch mechanism is introduced.
- D027/B003 remains untouched: this change does not choose a parent for
  canonical `true` or `false` and does not introduce a `Boolean` prototype.
- No normative specification change is introduced.


## [0.2.33-SNAPSHOT] - 2026-09-04

### Fixed

- Allowed semantic member lookup to traverse ordinary-object delegation chains
  without requiring a Core prelude when no represented value boundary is
  crossed.
- Required the Core prelude lazily only when the semantic lookup walker actually
  reaches represented Integer or Float values.
- Restored extracted-Closure member-read execution in minimal activations while
  preserving represented numeric lookup through source-backed Core prototypes.
- Project implementation version changed from `0.2.32-SNAPSHOT` to `0.2.33-SNAPSHOT`.

### Notes

- D027/B003 remains untouched. No parent for canonical `true` or `false` is
  selected and no `Boolean` prototype is introduced.
- No normative specification change is introduced.


## [0.2.32-SNAPSHOT] - 2026-09-04

### Fixed

- Routed member lookup through the semantic value walker even when the initial
  receiver is an ordinary object, so an ordinary delegation chain can continue
  through a represented numeric value and then into its source-backed
  `Integer`/`Float`/`Number` prototype chain.
- Prevented the legacy `ProtosObjectValue.lookupSlot` host exception from
  escaping that mixed ordinary/represented delegation path.
- Preserved the existing language-level Core Error behavior for represented
  value families whose prototype bridge is not implemented in this slice.
- Project implementation version changed from `0.2.31-SNAPSHOT` to `0.2.32-SNAPSHOT`.

### Notes

- No delegation parent is selected for canonical `true` or `false`; D027/B003
  remains untouched.
- No `Boolean` prototype or Boolean fallback is introduced.
- No normative specification change is introduced.


## [0.2.31-SNAPSHOT] - 2026-09-04

### Fixed

- Restored minimal/internal `ProtosPrelude` construction without requiring
  source-backed `Number`, `Integer`, and `Float` bindings in every prelude.
  Full Core bootstrap remains responsible for loading and validating the numeric
  hierarchy.
- Ordinary `ProtosObjectValue` member reads no longer require an owning Core
  prelude. A prelude is required only when lookup crosses from a represented
  non-ordinary runtime value into its source-backed standard prototype chain.
- Corrected P52 numeric receiver-binding tests to use member extraction followed
  by the already-supported ordinary `CanonicalCall` path instead of unsupported
  `CanonicalSend` lowering.
- Project implementation version changed from `0.2.30-SNAPSHOT` to `0.2.31-SNAPSHOT`.

### Notes

- Numeric hierarchy semantics from P52 are unchanged.
- D027/B003 remains untouched: no parent is chosen for canonical `true` or
  `false`, and no `Boolean` prototype is introduced.
- No normative specification change is introduced.


## [0.2.30-SNAPSHOT] - 2026-09-04

### Added

- Added source-backed standard `Number`, `Integer`, and `Float` prototype objects
  under `protos/lib/core/`.
- Added the runtime value-lookup bridge that maps semantic Integer and Float
  value representations into those source-backed ordinary prototype chains.
- Added coverage for numeric prototype hierarchy, inherited numeric lookup,
  exact receiver preservation, and ordinary objects delegating to numeric
  values.
- Added implementation blocker B003 so canonical Boolean parentage remains
  explicitly deferred to D027 rather than being guessed by the runtime.

### Changed

- Core bootstrap now loads and validates `Number -> Object`,
  `Integer -> Number`, and `Float -> Number`, then publishes those exact objects
  in the frozen prelude.
- Member lookup, invocation lookup, and activation receiver fallback now use the
  semantic value lookup bridge when a Core prelude is available.
- Project implementation version changed from `0.2.29-SNAPSHOT` to `0.2.30-SNAPSHOT`.

### Notes

- This change intentionally does not choose a parent for canonical `true` or
  `false`, does not create a `Boolean` prototype, and does not install Boolean
  lookup behavior. That work remains blocked on D027.
- String/null and the remaining standard value families are not assigned
  substitute parentage by this increment.
- No normative specification change is introduced.


## [0.2.29-SNAPSHOT] - 2026-09-04

### Added

- Added ordinary parenthesized invocation through the normative `call` slot protocol.
- Added `CanonicalCall` lowering, target-before-argument evaluation, spread flattening, ordinary `call` lookup, Closure validation, receiver/method-home binding, and terminal direct Closure activation.
- Installed standard `Object.call` and `Object.init` as ordinary Closure-valued slots on `Object`, with default construction and D022's `Object.init() -> this` result.

### Changed

- `ProtosClosureValue` now participates in the ordinary object/delegation model as a direct child of `Object`; standard Closure invocation therefore inherits `Object.call` rather than using a hidden callable flag.
- Extracted/bound Closure wrappers preserve ordinary local slots and structural state while replacing only receiver/method-home binding metadata.
- Project implementation version changed from `0.2.28-SNAPSHOT` to `0.2.29-SNAPSHOT`.

### Tests

- Added end-to-end coverage for plain Closure calls, local and inherited `call`, incompatible shadowing, default construction, overridden and standard `init`, spread, nested calls, and non-local return across ordinary calls.

### Notes

- Host-represented primitive value prototype bridging and standard Array/Map/numeric `call` specializations remain later implementation layers.
- No normative specification change is introduced.


## [0.2.28-SNAPSHOT] - 2026-09-04

### Changed

- Closed implementation blocker B002 for the runtime semantics of
  `Object.without(name)` and `Object.alias(sourceName, aliasName)` structural
  views.
- Structural-view results now always use the unique root `Object` as their
  immediate delegation parent rather than accepting an implementation-selected
  parent.
- The result remains a fresh open ordinary object regardless of the source
  object's parent or open/closed/frozen state.
- Project implementation version changed from `0.2.27-SNAPSHOT` to `0.2.28-SNAPSHOT`.

### Tests

- Extended object-runtime coverage for fresh identity, root-`Object` parent,
  open result state, frozen-source behavior, shallow exact-value copying,
  mutation independence, and delegated alias-name non-collision.

### Notes

- This block implements the now-closed runtime object semantics without exposing
  new Protos-visible messages yet; ordinary message dispatch remains pending the
  invocation-protocol work.
- `CanonicalCall` remains deliberately unopened while the current specification
  does not yet define the portable inheritance/replacement mechanism of the
  ordinary invocation protocol.
- No normative specification change is introduced.


## [0.2.27-SNAPSHOT] - 2026-09-04

### Added

- Added source-backed standard `InvalidReturn` under `protos/lib/core/`, with
  direct delegation to the standard `Error` prototype.
- Added `ProtosReturnNode` and a dedicated internal non-local-return control
  transfer carrying the exact target home and result value.
- Callable-plan lowering now lowers canonical `^value` in Closure defaults and
  bodies.
- Added fresh `InvalidReturn` construction through the activation-owned Core
  prelude.

### Changed

- Selected Closure invocation now catches a non-local return only when the
  invocation owns the exact target home; nested invocations sharing a captured
  home rethrow the transfer unchanged.
- An owned home is still completed on every exit path, including a handled
  non-local return.
- Project implementation version changed from `0.2.26-SNAPSHOT` to `0.2.27-SNAPSHOT`.

### Tests

- Added integrated coverage for direct active `^`, `^` from a default
  expression, nested captured-home propagation, escaped-Closure
  `InvalidReturn`, fresh Error identity, and the exact source-backed
  `InvalidReturn -> Error` prototype relationship.
- Extended Core bootstrap coverage for the standard `InvalidReturn` prototype.

### Notes

- This block closes non-local return execution for already selected Closures.
- Ordinary `CanonicalCall`, message send, `super`, and polymorphic object
  invocation remain outside this block.
- No normative specification change is introduced.


## [0.2.26-SNAPSHOT] - 2026-09-04

### Added

- Added `ProtosClosureInvoker` for executing an already selected Closure through
  activation establishment, normative parameter binding, and body execution.
- Closure execution plans now own reusable Truffle call targets for binding and
  body execution.

### Changed

- An invocation-owned return home is completed when that invocation leaves its
  dynamic binding/body extent, including failure exits.
- Nested Closure invocations that reuse a captured return home never complete
  that home themselves.
- Project implementation version changed from `0.2.25-SNAPSHOT` to `0.2.26-SNAPSHOT`.

### Tests

- Added coverage for selected-Closure invocation, normal owned-home completion,
  binding failure propagation, and preservation of a captured nested return home.

### Notes

- This increment still does not lower or dispatch `CanonicalCall`/message sends.
- Non-local `^` transfer itself remains unopened; this slice establishes the
  lifecycle boundary it will target.
- No normative specification change is introduced.


## [0.2.25-SNAPSHOT] - 2026-09-04

### Fixed

- Corrected the P47 callable-lowering test to import `ProtosParser` from its
  actual `com.guillermomolina.protos.parser` package.

### Changed

- Project implementation version changed from `0.2.24-SNAPSHOT` to `0.2.25-SNAPSHOT`.

### Notes

- No production implementation or Protos semantics changed in this corrective
  commit.



## [0.2.24-SNAPSHOT] - 2026-09-04

### Added

- Closure lowering now prepares an implementation-private execution plan
  containing the parameter-binding node and body node without invoking either.
- Callable-plan lowering recognizes `args` in Closure defaults and bodies and
  lowers it to `ProtosArgsNode`.
- Materialized Closure values retain their prepared execution plan, including
  across extracted-method binding.

### Changed

- General program/module lowering continues to reject `args`; only callable
  plans receive invocation-context `args` lowering.
- Project implementation version changed from `0.2.23-SNAPSHOT` to `0.2.24-SNAPSHOT`.

### Tests

- Added coverage proving both a default expression and a Closure body plan can
  observe the exact activation-owned `args` Array.

### Notes

- This increment does not execute calls, parameter binding automatically, or
  Closure bodies automatically; it only prepares the complete callable plan.
- Return-home completion and `^` execution remain unopened.
- No normative specification change is introduced.


## [0.2.23-SNAPSHOT] - 2026-09-04

### Added

- Added `ProtosArgsNode`, an invocation-only execution node that returns the
  exact standard frozen Array already established on the current Closure
  activation.

### Changed

- Project implementation version changed from `0.2.22-SNAPSHOT` to `0.2.23-SNAPSHOT`.

### Tests

- Added focused coverage proving `args` returns the exact activation-owned Array,
  retaining its source-backed standard `Array` parent and frozen state.

### Notes

- This increment intentionally does not enable `args` in the general
  program/module lowerer because Core defines it as an invocation-context
  binding and does not assign a substitute value outside invocation.
- Callable-body/default lowering will use this node when the Closure execution
  plan is connected.
- No normative specification change is introduced.


## [0.2.22-SNAPSHOT] - 2026-09-04

### Added

- Added `ProtosParameterBindingNode`, implementing the normative left-to-right
  Closure parameter-binding algorithm over an already established invocation
  activation.
- Binding supports supplied arguments, real-activation default evaluation,
  trailing rest capture as a distinct fresh frozen standard Array, and generic
  argument-count Error signaling.

### Changed

- Project implementation version changed from `0.2.21-SNAPSHOT` to `0.2.22-SNAPSHOT`.

### Tests

- Added coverage for earlier-parameter visibility from defaults, exact `args`
  preservation, distinct frozen rest Arrays, missing required parameters, and
  deferred excess-argument detection.

### Notes

- Parameter names are created only after their supplied/default value is
  obtained; there is no predeclaration or arity preflight.
- Slot-creation conflicts during parameter establishment use ordinary generic
  Error signaling.
- This increment does not yet execute `CanonicalCall`, lower the `args`
  intrinsic, execute Closure bodies, or complete return homes.
- No normative specification change is introduced.


## [0.2.21-SNAPSHOT] - 2026-09-04

### Added

- Closures now preserve their implementation-private owning `ProtosPrelude`
  together with lexical contexts and callable control metadata.
- Added `ProtosActivation.forClosureInvocation(...)`, which atomically
  establishes a fresh execution context, exact captured receiver and lexical
  contexts, source-backed frozen `args`, `methodHome`, and return-home state.
- Invocation activations record whether they own a newly established return home
  or reuse a captured lexical home.

### Changed

- Closure literal materialization now captures the exact owning prelude.
- Project implementation version changed from `0.2.20-SNAPSHOT` to `0.2.21-SNAPSHOT`.

### Tests

- Added coverage for complete top-level Closure activation establishment and for
  nested Closure reuse of the exact captured return home.

### Notes

- This increment still does not execute `CanonicalCall`, bind parameters or
  defaults/rest, execute Closure bodies, complete return homes, or execute `^`.
- No partial observable call path is introduced.
- No normative specification change is introduced.


## [0.2.20-SNAPSHOT] - 2026-09-04

### Added

- Added implementation-private activation `methodHome` state matching the
  normative `super` lookup model.
- Closure literals now capture the current activation's `methodHome` together
  with receiver and return-home metadata.

### Changed

- Object-construction execution preserves enclosing `methodHome` metadata while
  continuing not to become a lexical capture scope.
- Project implementation version changed from `0.2.19-SNAPSHOT` to `0.2.20-SNAPSHOT`.

### Tests

- Added focused coverage proving construction preserves the exact enclosing
  method home.

### Notes

- This increment does not execute `super`, perform callable dispatch, or create
  method activations yet.
- No normative specification change is introduced.


## [0.2.19-SNAPSHOT] - 2026-09-04

### Added

- Added `ProtosReturnHome`, an implementation-private identity object with
  explicit active/completed lifecycle state for callable non-local return
  ownership.
- `ProtosActivation` can now carry an optional lexical return home, and object
  construction preserves the enclosing home.
- `ProtosClosureValue` now captures and preserves the exact lexical return home,
  including across extracted-method binding.

### Changed

- Closure literal materialization now records the current activation's return
  home when one exists.
- Project implementation version changed from `0.2.18-SNAPSHOT` to
  `0.2.19-SNAPSHOT`.

### Tests

- Added focused return-home lifecycle and object-construction propagation
  coverage.

### Notes

- This increment does not yet execute `^`, create callable activations, or
  establish fresh invocation return homes.
- It does not add activation-level `methodHome`; `super` activation state remains
  a separate subsequent slice.
- No normative specification change is introduced.


## [0.2.18-SNAPSHOT] - 2026-09-04

### Added

- Added implementation-private invocation-argument state to `ProtosActivation`.
  Non-invocation activations expose no argument Array.
- Added `ProtosPrelude.newFrozenArray(...)`, producing a fresh frozen standard
  Array with the exact source-backed `Array` prototype.

### Changed

- Test preludes now include the mandatory standard `Array` binding introduced
  by the source-backed Core bootstrap.
- Project implementation version changed from `0.2.17-SNAPSHOT` to
  `0.2.18-SNAPSHOT`.

### Tests

- Extended Core bootstrap coverage for fresh frozen standard Array
  materialization.

### Notes

- This increment prepares the activation representation required by the
  normative `args` semantics but does not yet establish callable activations,
  bind parameters, or lower the `args` intrinsic.
- No observable invocation shortcut or partial call execution is introduced.
- No normative specification change is introduced.


## [0.2.17-SNAPSHOT] - 2026-09-04

### Added

- Added distributable `protos/lib/core/array.protos`, defining the standard
  `Array` prototype as an ordinary child of `Object`.
- Added `ProtosPrelude.arrayPrototype()` and `newArray(...)` so runtime
  machinery can materialize standard Arrays with the exact source-backed
  Array prototype as delegation parent.

### Changed

- Core bootstrap now loads and validates the `Array` binding before freezing
  the standard prelude and installs that exact source-created object alongside
  `Context` and `Error`.
- Project implementation version changed from `0.2.16-SNAPSHOT` to
  `0.2.17-SNAPSHOT`.

### Tests

- Extended Core bootstrap coverage to verify the exact source-backed Array
  binding, its delegation parent, and the parent of a materialized standard
  Array value.

### Notes

- This increment does not implement Array invocation, `at`, `atPut`, `size`,
  `each`, `args`, rest binding, or any other Array protocol.
- No hardcoded Java Array prototype singleton is introduced.
- No normative specification change is introduced.


## [0.2.16-SNAPSHOT] - 2026-09-04

### Added

- Added distributable `protos/lib/core/error.protos`, defining the mandatory
  standard `Error` prototype as an ordinary child of `Object`.

### Changed

- Core bootstrap now executes both `context.protos` and `error.protos`, validates
  their exact required parent relationships, installs both bindings into the
  frozen standard prelude, and exposes `Error` through that prelude.
- Runtime language failures now create fresh error objects whose parent is the
  exact source-backed `Error` prototype owned by the current activation's
  prelude.
- `ProtosCoreErrors` is now a stateless runtime factory; its process-global
  `ERROR_PROTOTYPE` singleton and `errorPrototype()` accessor were removed.
- Project implementation version changed from `0.2.15-SNAPSHOT` to
  `0.2.16-SNAPSHOT`.

### Tests

- Runtime error tests now use an explicit test prelude rather than the removed
  production Error singleton.
- Core bootstrap coverage verifies the source-backed Error binding and its
  direct delegation to Object.

### Notes

- A module-local binding named `Error` cannot redirect runtime-generated Core
  errors because runtime failure identity is obtained from the activation's
  owning prelude, not ordinary shadowable lexical lookup.
- No second Error identity or process-global standard Error object remains.
- No normative specification change is introduced.


## [0.2.15-SNAPSHOT] - 2026-09-04

### Changed

- Activations created by a `ProtosPrelude` now retain an implementation-private
  reference to that exact owning prelude.
- Object-construction activations preserve the owning prelude from their
  enclosing activation.
- Legacy direct activation construction remains available for bootstrap and
  focused runtime tests and carries no implicit process-global prelude.
- Project implementation version changed from `0.2.14-SNAPSHOT` to
  `0.2.15-SNAPSHOT`.

### Tests

- Added coverage that module activations retain their exact prelude and that
  object-construction activations propagate it unchanged.

### Notes

- This is runtime plumbing only and introduces no Protos-visible binding,
  lookup rule, identity, or behavior change.
- The explicit prelude reference is the foundation for resolving standard Core
  identities such as `Error` without using shadowable lexical lookup or
  process-global standard-object singletons.
- No normative specification change is introduced.


## [0.2.14-SNAPSHOT] - 2026-09-04

### Changed

- `ProtosPrelude` now owns an explicit frozen ordinary Protos bindings context
  rather than only retaining the `Context` prototype reference.
- `ProtosCoreBootstrap` now constructs that real prelude context after loading
  `Context`: it delegates to `Context`, contains the exact `Context` binding,
  and is frozen before becoming observable to later runtime stages.
- Added `ProtosPrelude.newModuleActivation()` so a fresh module context captures
  the frozen standard prelude through the ordinary lexical-context mechanism.
- Project implementation version changed from `0.2.13-SNAPSHOT` to
  `0.2.14-SNAPSHOT`.

### Tests

- Added coverage for frozen prelude structure and ordinary lexical lookup of the
  `Context` binding from a module activation.
- Extended Core bootstrap coverage to verify the source-backed prelude context.

### Notes

- This does not add any new standard objects or hardcoded Core behavior.
- The prelude remains explicit runtime state; no process-global mutable prelude
  is introduced.
- No normative specification change is introduced.


## [0.2.13-SNAPSHOT] - 2026-09-04

### Removed

- Removed the temporary static `ProtosCorePrelude` and its Java-constructed
  standard `Context` prototype.
- Removed the obsolete tests that treated that static scaffold as the Core
  prelude.

### Changed

- Migrated remaining test execution-context construction to explicit
  `ProtosPrelude` state or, for the Core bootstrap test itself, to the
  irreducible root-backed bootstrap context.
- Updated the Core bootstrap architecture note to record that the Java-side
  `Context` scaffold has been retired.
- Project implementation version changed from `0.2.12-SNAPSHOT` to
  `0.2.13-SNAPSHOT`.

### Notes

- The standard `Context` identity is now constructed by
  `protos/lib/core/context.protos` through `ProtosCoreBootstrap`.
- No replacement process-global standard prototype is introduced.
- No normative specification change is introduced.


## [0.2.12-SNAPSHOT] - 2026-09-04

### Added

- Added explicit `ProtosPrelude` runtime state whose `Context` prototype is
  supplied rather than hardcoded.
- Added `ProtosCoreBootstrap`, which executes distributable
  `protos/lib/core/context.protos` through the ordinary source pipeline and
  returns a prelude backed by the resulting ordinary `Context` object.
- Added focused coverage for explicit prelude context creation and source-backed
  Core bootstrap.

### Changed

- Project implementation version changed from `0.2.11-SNAPSHOT` to
  `0.2.12-SNAPSHOT`.

### Notes

- Core bootstrap uses a short-lived internal root-backed context only to create
  the first standard `Context`; that bootstrap context is not a Protos module
  instance or a new standard prototype.
- No process-global mutable prelude state is introduced. `ProtosPrelude` is an
  explicit object so later Actor ownership can remain local.
- The older static `ProtosCorePrelude` remains for one migration increment and
  is not extended.
- No normative specification change is introduced.


## [0.2.11-SNAPSHOT] - 2026-09-04

### Added

- Added the first executable distributable Core source,
  `protos/lib/core/context.protos`.
- The Core source constructs the standard `Context` prototype as an ordinary
  Protos object with `Object` as its delegation parent.
- Added coverage that loads and executes that Core source through the ordinary
  source-file/compiler pipeline and observes the resulting `Context` binding.

### Changed

- Project implementation version changed from `0.2.10-SNAPSHOT` to `0.2.11-SNAPSHOT`.

### Notes

- The existing Java-side `Context` in `ProtosCorePrelude` remains temporary
  bootstrap scaffolding for now. This increment establishes the source-side
  replacement before changing activation construction to consume it.
- No special Core parser, AST, object constructor, or Java-side standard
  prototype was added.
- No normative specification change is introduced.


## [0.2.10-SNAPSHOT] - 2026-09-04

### Added

- Added `ProtosSourceFileLoader` to read UTF-8 Protos source from a host `Path`
  and compile it through the existing ordinary source compiler pipeline.
- Added focused coverage for successful UTF-8 loading and host file-loading
  failure propagation.

### Changed

- Project implementation version changed from `0.2.9-SNAPSHOT` to
  `0.2.10-SNAPSHOT`.

### Notes

- This is host-side implementation plumbing, not the Protos language I/O model.
  It does not expose `Path`, Java NIO, or file-loading behavior to Protos code.
- The loader deliberately does not define module identity, import caching,
  bootstrap order, or Core object identities. Those higher-level semantics stay
  outside this increment.
- This entry point is intended for the upcoming `protos/lib/core/` bootstrap
  loader and ordinary module-loading machinery.
- No normative specification change is introduced.


## [0.2.9-SNAPSHOT] - 2026-09-04

### Added

- Added `ProtosSourceCompiler`, a single source-to-execution entry point that
  composes the existing parser, canonicalizer, Truffle lowerer, and call-target
  construction pipeline.
- Added focused coverage proving compiled source preserves ordinary sequence,
  literal, object-construction, and parser-failure behavior.

### Changed

- Project implementation version changed from `0.2.8-SNAPSHOT` to
  `0.2.9-SNAPSHOT`.

### Notes

- This is implementation plumbing only. It introduces no new syntax, lookup,
  invocation, object, or bootstrap semantics.
- The new entry point is intended to be reused by the upcoming
  `protos/lib/core/` loader so Core source and user source travel through the
  same ordinary compiler pipeline.
- No normative specification change is introduced.


## [0.2.8-SNAPSHOT] - 2026-09-04

### Fixed

- Fixed `ProtosArgumentVectorNodeTest` to use the actual `SourceSpan` constructor
  instead of a nonexistent `SourceSpan.unknown()` helper.
- Project implementation version changed from `0.2.7-SNAPSHOT` to
  `0.2.8-SNAPSHOT`.

### Notes

- This is a test-compilation correction only; caller argument-vector semantics
  and implementation behavior are unchanged.
- No normative specification change is introduced.


## [0.2.7-SNAPSHOT] - 2026-09-04

### Added

- Added the caller-supplied positional-vector evaluation stage for future
  polymorphic invocation.
- Ordinary argument items are evaluated exactly once from left to right.
- Spread items require standard Array indexed state and append a shallow
  ascending-index snapshot at their exact evaluation position.
- Invalid spread sources signal Core `Error` immediately and prevent later
  argument evaluation.

### Changed

- Project implementation version changed from `0.2.6-SNAPSHOT` to `0.2.7-SNAPSHOT`.

### Notes

- This increment deliberately stops before Closure activation and parameter
  binding. It therefore does not need to manufacture the standard frozen
  `args` Array before the Core `Array` prototype is available from
  `protos/lib/core/`.
- No standard Array prototype or protocol behavior is hardcoded in Java.
- No normative specification change is introduced.


## [0.2.6-SNAPSHOT] - 2026-09-04

### Changed

- Removed temporary Java-side `Number`, `Integer`, and `Float` prototype objects
  from `ProtosCorePrelude`.
- Removed the Java-side numeric-family prototype resolver and the tests that
  treated those temporary objects as the implementation's standard numeric
  prelude.
- Kept only the explicitly documented temporary `Context` bootstrap scaffold.
- Project implementation version changed from `0.2.5-SNAPSHOT` to
  `0.2.6-SNAPSHOT`.

### Notes

- Numeric value representations and already-implemented numeric literal/identity
  semantics are unchanged.
- This corrects an implementation-architecture regression: standard numeric
  prototype objects belong to the future `protos/lib/core/` bootstrap path
  rather than a growing hardcoded Java standard library.
- No normative specification change is introduced.


## [0.2.5-SNAPSHOT] - 2026-09-04

### Added

- Added an internal standard-Array object representation with receiver-owned
  dense indexed state distinct from ordinary object slots.
- Array indexed reads and updates use mathematical `BigInteger` indices,
  preserve exact element references, reject negative/out-of-range indices, and
  never grow or create holes.
- Closed Arrays may replace existing indexed elements while frozen Arrays reject
  replacement before index validation.
- Added detached shallow indexed snapshots for future call-spread and iteration
  semantics.

### Changed

- `ProtosObjectValue` is now extensible internally so specialized object
  representations can retain the ordinary object/delegation/slot model without
  adding parallel language object categories.
- Project implementation version changed from `0.2.4-SNAPSHOT` to
  `0.2.5-SNAPSHOT`.

### Notes

- This increment adds representation only. It does not hardcode or expose the
  standard `Array` prelude object or its protocol methods in Java.
- The explicit parent supplied to each Array instance preserves the normative
  Array-factory rule and will allow `protos/lib/core/` to own the standard
  prototype object when Core bootstrap is available.
- No normative specification change is introduced.


## [0.2.4-SNAPSHOT] - 2026-09-04

### Added

- Added execution of canonical composition items inside object bodies.
- Composition evaluates its source first, copies effective local bindings into
  the object under construction, and makes successful contributions immediately
  visible to later body items.
- Direct local declarations reserve their names structurally across the complete
  receiving object body, excluding those names from every composition item.
- Composition conflicts and invalid non-ordinary composition sources now signal
  Core `Error` objects instead of leaking host exceptions.

### Changed

- Canonical object-body lowering now supplies the object's structural reservation
  set to each composition item while preserving strict left-to-right body
  execution.
- Project implementation version changed from `0.2.3-SNAPSHOT` to
  `0.2.4-SNAPSHOT`.

### Notes

- Composition reuses the existing atomic runtime contribution helper, so a
  conflicting item installs none of its effective bindings.
- `without` and `alias` remain blocked by B002 and are not exposed by this
  increment.
- No normative specification change is introduced.


## [0.2.3-SNAPSHOT] - 2026-09-04

### Added

- Added Truffle execution for canonical object expressions without composition.
- Bare object expressions now create fresh open ordinary objects delegating to
  the unique `Object` root.
- Explicit parent expressions are evaluated before object-body execution and
  their exact result becomes the constructed object's immutable delegation
  parent.
- Object bodies execute through construction activations, so local slot creation
  targets the new object while Closures skip the construction object as a lexical
  capture scope.

### Changed

- Canonical-to-Truffle lowering now accepts `CanonicalObject` when all body
  expressions are otherwise supported by the current execution slice.
- Project implementation version changed from `0.2.2-SNAPSHOT` to
  `0.2.3-SNAPSHOT`.

### Notes

- Canonical composition execution remains a separate following increment.
- No normative specification change is introduced; this implements the current
  object-model and execution-context contracts.


## [0.2.2-SNAPSHOT] - 2026-09-04

### Added

- Added explicit object-construction activations whose current context and
  receiver are the object under construction while Closure capture skips that
  construction object.
- Added transitive construction-scope skipping so Closures created inside nested
  object bodies capture only genuine enclosing lexical contexts.

### Changed

- Closure materialization now obtains its lexical capture chain from activation
  semantics instead of unconditionally capturing the activation's current
  context.
- Project implementation version changed from `0.2.1-SNAPSHOT` to
  `0.2.2-SNAPSHOT`.

### Notes

- This is implementation architecture for already-specified object-construction
  and lexical-capture semantics; no normative specification change is introduced.
- The new construction-activation boundary is intended to support subsequent
  canonical object execution and Core source bootstrap.


## [0.2.1-SNAPSHOT] - 2026-09-04

### Added

- Added executable numeric literal materialization and semantic identity execution.
- Added the runtime ordinary-object foundation with immutable delegation parents,
  local/delegated lookup, slot mutation, structural open/closed/frozen state,
  local-slot removal and snapshots, composition views, and atomic composition
  contributions.
- Added activation-context lookup ordering, Core error signaling, `this` and
  `context` execution, bare/member slot mutation, member lookup-home preservation,
  Closure materialization, and extracted-method binding foundations.
- Added empty-Sequence execution returning canonical `null`.
- Added canonical object composition-reservation discovery for direct local
  declarations.
- Added the initial standard `Context` bootstrap scaffold and fresh execution
  contexts delegating through `Context` to `Object`.
- Added the non-normative Core bootstrap architecture and reserved
  `protos/lib/core/` for standard objects and behavior implemented in Protos.

### Changed

- Project implementation version changed from `0.2.0-SNAPSHOT` to
  `0.2.1-SNAPSHOT`.
- Established an explicit repository rule requiring every committed executable
  implementation or distributable Core-library change to bump the Maven
  implementation patch version and add its corresponding root changelog entry.
- Limited Java-side standard-object construction to irreducible or explicitly
  temporary bootstrap scaffolding; ordinary Core behavior should move to
  `protos/lib/core/` as soon as it can be loaded faithfully.

### Notes

- This entry catches up implementation release metadata that was not maintained
  during the preceding incremental runtime/execution work.
- Protos Core language version remains 0.1.
- No normative language semantics are changed by the bootstrap architecture
  decision; observable Core behavior remains defined exclusively by `spec/`.
- The current Java-side `Context` bootstrap is temporary scaffolding until the
  Core source loader can construct it faithfully.

## [0.2.0-SNAPSHOT] - 2026-09-01

### Added

- Added canonical String literal execution as ordinary immutable Protos String values.

- Added initial Canonical-to-Truffle lowering for canonical `true`, `false`, and `null` singleton literals and non-empty sequences.

- Added a Truffle CallTarget entry point for executing Protos expression trees through the root-node boundary.
- Added the initial Truffle root execution boundary, delegating directly to the executable expression tree without introducing language-value semantics.

- Added the first executable Truffle node: non-empty expression sequences execute strictly left-to-right and return the final child result.
- Added the initial Truffle execution-node boundary with source-span preservation and the Truffle API dependency.
- Added canonical call-spread lowering with a contextual `CanonicalSpread(expression)` marker for ordinary, member, and super invocation arguments.
- Added dedicated canonical indexed-assignment lowering that preserves receiver/index/value evaluation structure and the distinct `atPut` assignment-result semantics.
- Added dedicated canonical lowering for non-spread `super.message(arguments...)` operations, preserving super lookup semantics separately from ordinary message sends.
- Added dedicated canonical intrinsic nodes for the reserved execution-context expressions `this`, `context`, and `args`, keeping them distinct from ordinary lexical lookup.
- Added canonical lowering for non-spread calls, preserving the semantic distinction between ordinary `Call(receiver, arguments)` and member message `Send(receiver, message, arguments)` forms.
- Added canonical object lowering with optional explicit parent, canonical object-body sequencing, and `Compose(object)` nodes for contextual composition items.
- Added canonical `Return(value)` lowering for the `^ expression` non-local return form.
- Added canonical `Create`/`Assign` lowering for bare and explicit-member slot writes while leaving indexed assignment for its distinct `atPut` semantics.
- Added canonical lowering for indexed reads as ordinary one-argument `at` message sends.
- Added canonical lowering for lazy `&&` and `||` as `and`/`or` message sends whose right-hand side is wrapped in a parameterless canonical Closure.
- Added canonical Closure and parameter lowering, including mandatory normalization of expression-bodied Closures to a one-expression canonical Sequence.
- Added canonical lowering for semantic equality/inequality and non-overridable identity/non-identity, with a dedicated canonical identity node.
- Added canonical lowering for the standard comparison operators `<`, `<=`, `>`, and `>=` as ordinary one-argument message sends.
- Added canonical lowering for the standard arithmetic operators `+`, `-`, `*`, `/`, and `%` as ordinary one-argument message sends.
- Added canonical lowering for custom symbolic binary operators as ordinary one-argument message sends while leaving standard binary operators for dedicated semantic lowering.
- Added canonical lowering for Core prefix `-` and `!` as ordinary zero-argument `negated` and `not` sends.
- Added the canonical semantic AST foundation and the first Surface AST canonicalization slice for literals, name lookup, grouping, member reads, and sequences.
- Established `.protos` as the project source-file extension and added initial non-normative tutorial, task-oriented example, and portable benchmark corpora.
- Added benchmark workloads for recursion, slots, closure and method calls, object creation, delegation depth, and monomorphic/polymorphic dispatch.
- Added parser support for same-line parameterless trailing closures as the final argument of ordinary call suffixes.
- Added closure surface AST integration and deterministic parsing for closure parameters, defaults, rest parameters, and braced or expression bodies.
- Added parser support for object expressions, parent expressions, and contextual object composition items.
- Added parser support for structural super message sends without making `super` a first-class expression.
- Added parsing for slot creation and assignment with grammar-defined target restrictions.
- Added deterministic parsing for the grammar-defined non-local return expression.
- Added parser support for the separate custom binary-operator precedence domain and its required standard/custom mixing errors.
- Added deterministic parsing for the standard unary and binary operator precedence ladder.
- Added parser support for same-line semicolon expression separators with the grammar-defined error cases.
- Extended the parser foundation with parenthesized expressions, member access, calls, indexing, argument spread, and leading-dot continuation.
- Added the first deterministic parser foundation with source-aware errors and a portable surface AST.
- Added source-aware lexer token occurrences with portable half-open source spans as parser infrastructure.
- Expanded lexer conformance coverage for raw Unicode scalar handling across every Core String form, including rejection of unpaired surrogates.
- Expanded lexer conformance coverage for numeric termination at structural delimiters, logical newlines, and standard and custom operators.
- Expanded lexer conformance coverage for uppercase radix-prefix commitment and case-insensitive exponent completion errors.
- Expanded lexer conformance coverage for malformed numeric/identifier adjacency across ASCII, reserved-word, underscore, and Unicode identifier continuations.
- Expanded lexer conformance coverage for single-line String raw-newline rejection, raw-source indentation matching, and interpolation-looking escape rejection.
- Expanded lexer conformance coverage for triple-double String CR/CRLF delimiter-newline handling and shared escape semantics.
- Expanded lexer conformance coverage for Unicode escape digit-count boundaries, hexadecimal case handling, and the closed String escape set.
- Expanded lexer conformance coverage for exact, case-sensitive reserved-word recognition and the closed Core v0.1 reserved-word set.
- Expanded lexer conformance coverage for triple-double String structural indentation, including absent and empty prefixes, blank-line exemption, and exact SPACE/TAB matching.
- Expanded lexer conformance coverage for the symbolic-operator alphabet, maximal-munch classification, and comment/operator lexical precedence.
- Expanded lexer conformance coverage for numeric separator placement, unsupported suffixes and radix floats, special-value identifiers, and valid token boundaries.
- Expanded lexer conformance coverage for line-comment termination, non-nesting block comments, first-delimiter closure, and comment delimiters inside Strings.
- Expanded lexer conformance coverage for String normalization independence, raw Unicode scalar content, quote-run boundaries, and the absence of triple-single String syntax.
- Expanded lexer conformance coverage for Core v0.1 String quote-run boundaries, unterminated String forms, and the closed whitespace rules.
- Added bundled Unicode 17.0.0 character-property and normalization data used by the lexer independently of the host JDK Unicode version.
- Added conformance tests against the official Unicode 17.0.0 `XID_Start`, `XID_Continue`, and normalization test data.

### Changed

- Decoupled canonical literal kinds from parser surface literal kinds with an explicit Surface-to-Canonical mapping.
- Corrected malformed parser imports introduced by slot-creation and assignment support.
- Restored the canonical NEWLINE token value after adding source-aware token occurrences.
- Fixed source-aware newline token occurrence emission so canonical NEWLINE lexemes remain valid Java Strings.
- Updated GitHub Actions CI to current supported `actions/checkout` and `actions/setup-java` major versions.
- Relicensed Protos from the Server Side Public License (SSPL) v1 to the OSI-approved Adaptive Public License 1.0 (APL-1.0). The complete license, including the completed Exhibit A, is in [LICENSE.TXT](LICENSE.TXT).
- Project implementation version changed from `0.1.6-SNAPSHOT` to `0.2.0-SNAPSHOT`.

### Notes

- Protos Core language version remains 0.1.
- No language semantics changed.

### Fixed

- Fixed parser newline continuation while a member suffix or structural `super` message send is necessarily incomplete.
- Fixed parser newline continuation after `...` in spread arguments and rest parameters, matching the grammar's necessarily-incomplete construct rule.
## [0.1.6-SNAPSHOT] - 2026-08-31

### Added

- Expanded lexer support to cover the lexical rules defined by the current Core v0.1 specification.
- Added lexer support for comments, Unicode-aware identifiers, reserved words, numeric literal forms, multiline strings, ellipsis, and custom symbolic operators.
- Added lexical validation for malformed string escape sequences and invalid Unicode scalar values.
- Added regression coverage for supplementary Unicode escape sequences.

### Changed

- Updated lexer tests to match the current specification for reserved words, period tokenization, and numeric literals adjacent to periods.
- Unicode escape decoding now preserves supplementary Unicode code points instead of truncating them to Java `char` values.

### Notes

- The lexer implementation is still under specification-compliance review.
- Unicode identifier handling requires further review for exact `XID_Start` and `XID_Continue` compliance.
- Specification changes are documented separately in [spec/PROTOS_SPEC_CHANGELOG.md](spec/PROTOS_SPEC_CHANGELOG.md).

## [0.1.5-SNAPSHOT] - 2026-08-31

### Notes
- No implementation changes in this session.
- Specification changes documented in [spec/PROTOS_SPEC_CHANGELOG.md](spec/PROTOS_SPEC_CHANGELOG.md).

## [0.1.4-SNAPSHOT] - 2026-08-31

### Added
- Initial minimal Protos lexer implementation under `com.guillermomolina.protos.lexer`.
- Token model and token type definitions to represent the current lexical grammar.
- Basic lexical support for:
  - identifiers and reserved intrinsic keywords
  - numeric literals, including radix literals (`0x`, `0b`, `0o`)
  - string literals with escape handling
  - punctuation and structural tokens
  - operators and custom symbolic operators
  - newline-delimited token separation
- Focused lexer regression tests covering the implemented lexical behavior.
- Project licensing metadata by adding the license text in the then-current `LICENSE` file and referencing it from the README.

### Changed
- Added JUnit 5 to support lexer-focused test coverage.
- Updated project version from `0.1.3-SNAPSHOT` to `0.1.4-SNAPSHOT` as a conservative patch bump for the completed milestone.
- Documented the chosen license in the project README for this version.

### Notes
- Specification changes documented in [spec/PROTOS_SPEC_CHANGELOG.md](spec/PROTOS_SPEC_CHANGELOG.md).
