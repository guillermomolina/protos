# Platform Architecture Decisions

This registry records durable, non-normative architecture decisions that depend
on a concrete implementation platform, host runtime, VM, operating system,
native backend, or equivalent execution substrate.

`PLATxxx` is deliberately distinct from implementation-independent decision
records and from normative semantic authority:

- `Dxxx` is an implementation-independent decision identifier family whose
  documentation role is classified separately by primary domain. Language/
  specification decisions live under `decisions/language/`; tooling/package-
  system decisions live under `decisions/tooling/`. Observable Protos semantics
  remain authoritative only in the applicable ratified material under `spec/`;
- `PLATxxx` owns durable platform/runtime architecture choices that materially
  constrain an implementation while remaining semantically invisible to Protos;
- `Ixxx`, `CLIxxx`, `TOOLxxx`, `PERFxxx`, `DISTxxx`, and similar work families
  implement, validate, measure, distribute, or otherwise consume those decisions.

A platform-dependent choice MUST NOT be placed in `PLATxxx` merely to avoid the
normative design process. If the choice changes observable Protos semantics,
identity, authority, ordering, isolation, failure behavior, portability
promises, or another language contract, the applicable `Dxxx`/specification
process remains authoritative first.

## Status vocabulary

- `OPEN` — a platform/runtime architecture question is identified but unresolved.
- `PROPOSED` — researched recommendation exists but project-owner approval is
  still pending.
- `RATIFIED` — explicitly approved durable platform/runtime architecture.
- `SUPERSEDED` — replaced by a later explicitly approved PLAT decision while
  retaining historical rationale.

## Decisions

| Item | Decision | Status | Approval | Primary consumers |
|---|---|---|---|---|
| PLAT001 | Truffle runtime hosting topology | RATIFIED | Explicit project-owner approval, 2026-09-08; 2026-09-09 A+ executable-layer amendment explicitly approved | I026-A4 and later Truffle-hosted runtime/tooling work |
| PLAT002 | Network capability represented-value architecture | RATIFIED | Explicit project-owner approval, 2026-09-09 after cross-language/runtime/capability/scalability review | I028-B and later Network/TCP runtime implementation |
| PLAT003 | JVM TCP live-resource and duplex-I/O architecture | RATIFIED | Explicit project-owner approval, 2026-09-09 after cross-language/runtime/full-duplex/future-scalability review | I028-C/D/E |
| PLAT004 | Truffle SourceSection ownership and materialization | RATIFIED | Explicit project-owner approval, 2026-09-09 after Truffle guidance + SimpleLanguage/TruffleRuby/GraalJS/FastR/Sulong/Apple Pkl scalability review | I026-B/C/F/G |
| PLAT005 | Truffle instrumentation coverage and StandardTags architecture | RATIFIED | Explicit project-owner approval, 2026-09-09 after Truffle + SimpleLanguage/TruffleRuby/GraalJS/Apple Pkl/GraalPy/Sulong/Espresso scalability review | I026-C/E/F/G |
| PLAT006 | JVM TCP host I/O operation engine architecture | RATIFIED | Explicit project-owner approval, 2026-09-09 after cross-language/runtime/readiness/completion/cancellation/scalability review | I028-E/F |
| PLAT007 | JVM NIO IPv6-only TCP listener enforcement | RATIFIED | Explicit project-owner approval, 2026-09-09 after mainstream-runtime, Truffle/GraalVM, Apple Pkl, future-backend and scalability review | I028-E/F |
| PLAT008 | Truffle replay-site identity across wrappers, rewrites and continuations | RATIFIED | Explicit project-owner approval, 2026-09-09 after Truffle + Apple Pkl/TruffleRuby/GraalJS/FastR/Sulong/Espresso/GraalPy Bytecode DSL replay/wrapper/future-scalability review | I026-C/F and future replay backend work |
| PLAT009 | Host-neutral first-effect attempt gate for asynchronous ByteWritable output | RATIFIED | Explicit project-owner approval, 2026-09-09 after ByteWritable/NIO race analysis plus cross-runtime, Truffle/GraalVM, Apple Pkl and future-scalability review | I028-E3 and future async output backends |
| PLAT010 | Bounded reusable platform carriers for normal Actor guest execution | RATIFIED | Explicit project-owner approval, 2026-09-09 after exhaustive BEAM/Go/Tokio/Akka/Orleans/Swift/Pony/Kotlin/GHC/OCaml/Loom and Truffle scalability review triggered by PERF001-F #239 | Actor runtime scheduler, #239, PERF001-F |
| PLAT011 | RuntimeHost-owned shared Actor carrier substrate across local Processes | RATIFIED | Explicit project-owner approval, 2026-09-09 after cross-runtime many-Process, 64/128+ core, NUMA/affinity, blocking-lane and distributed-evolution review | Actor runtime hosting, #239, PERF001-F and future host capacity governance |
| PLAT012 | Verified external package custody and source-resolution architecture | RATIFIED | Explicit project-owner approval, 2026-09-09 after exhaustive loader/runtime/package-store, CAS/distributed, lifetime and scalability review | TOOL001-F2E4/F2E5 and future immutable external package resource loading |
| PLAT013 | Truffle debugger/interop value projection architecture | RATIFIED | Explicit project-owner approval, 2026-09-09 after exhaustive Truffle-language (including Apple Pkl), Bytecode-DSL, identity, large-graph and scalability review | I026-D/E/F/G |
| PLAT014 | Truffle cooperative suspension continuation/compilation boundary | RATIFIED | Explicit project-owner approval, 2026-09-10 after deep Truffle-language/runtime review and complete A1/A2a/A2b/A2c/A2d feasibility evidence | PERF006, I026 continuation/backend work and future JVM/Truffle Task/Future execution |
| PLAT015 | Truffle debugger scope projection topology | RATIFIED | Explicit project-owner approval, 2026-09-10 after deep Truffle implementation audit including Apple Pkl, GraalPy Bytecode DSL, SimpleLanguage, TruffleRuby, GraalJS, FastR, Sulong and Espresso; future/scalability/Protos-fit review | I026-E/F/G |
| PLAT016 | Bytecode Closure default-parameter execution topology | RATIFIED | Explicit project-owner approval, 2026-09-10 after exhaustive maintained-Truffle implementation review including Apple Pkl plus future/scalability/Protos-fit scoring | PERF006-B2C3B and later Bytecode Closure activation/default execution |\n
| PLAT017 | Selected-standard `Object.call` Bytecode intrinsic after ordinary D013 lookup | RATIFIED | Explicit project-owner approval, 2026-09-10 after exhaustive Truffle implementation audit covering all 16 principal + 21 experimental catalogue entries, with deep Bytecode DSL/SimpleLanguage, TruffleSqueak, Espresso, TruffleRuby, Sulong, GraalJS, GraalPy, FastR, GraalWasm, Enso, SOMns and Pkl review plus future/scalability/Protos-fit analysis | PERF006-B2D3B and later D013-conformant Bytecode call preparation |
| PLAT018 | GraalVM DAP debug-invocation ownership, OS-ephemeral loopback endpoint and readiness boundary | RATIFIED | Explicit project-owner approval, 2026-09-10 after extended Truffle-language/GraalVM DAP lifecycle, port-0, future/scalability and Protos-fit review | LM009-D/E and future debugger launchers/distributions |
| PLAT019 | Native semantic suspension bridge into Bytecode C-prime continuations | RATIFIED | Explicit project-owner approval, 2026-09-10 after exhaustive audit of all 16 principal + 21 experimental/historical Truffle catalogue entries including Apple Pkl, with future/scalability/Protos-fit scoring selecting B-prime | PERF006-B3 and future JVM/Truffle suspension-capable native boundaries |
| PLAT020 | Context-bound Truffle file Source materialization | RATIFIED | Explicit project-owner approval, 2026-09-11 after exhaustive audit of all 16 principal + 21 experimental/historical Truffle catalogue entries including Apple Pkl, with future/scalability/Protos-fit scoring selecting A′ | LM009-E / D065 source-presentation correction and future hosted physical Source materialization |
| PLAT021 | Bytecode C-prime dynamic control/unwind representation | RATIFIED | Explicit project-owner approval, 2026-09-11 after exhaustive current Truffle-catalogue audit including Apple Pkl plus future/scalability/Protos-philosophy scoring selecting Candidate F | PERF006-B4 and later JVM/Truffle C-prime structured control/unwind |
| PLAT022 | Context source-readability authority for physical debugger paths | RATIFIED | Explicit project-owner approval, 2026-09-11 after exhaustive audit of all 16 principal + 21 experimental/historical Truffle catalogue entries, Graal LSP/Polyglot filesystem machinery, Apple Pkl, DAP/VS Code behavior and future/scalability/Protos-philosophy scoring selecting Candidate D′ | LM009-E D065/PLAT020 physical-source correction and future Truffle-hosted physical Source tooling readability |
| PLAT023 | Async exact-execution carrier topology for Test Tool | RATIFIED | Explicit project-owner approval, 2026-09-11 after exhaustive review of all 16 principal Truffle implementations including Apple Pkl plus future/scalability/Protos-philosophy scoring selecting Candidate A′ | TOOL002-H2B3 and future same-runtime async exact-execution host transport |
| PLAT024 | Static language-service hosting and protocol boundary | RATIFIED | Explicit project-owner approval, 2026-09-11 after exhaustive audit of all 16 principal + 21 experimental/historical Truffle catalogue entries including Apple Pkl, plus future-endurance/scalability/Protos-philosophy scoring selecting Candidate A′ | LM009-F and later LM009-G/H static editor intelligence |
| PLAT025 | Bytecode module-initialization composition boundary | RATIFIED | Explicit project-owner approval, 2026-09-11 after exhaustive audit of all 16 principal + 21 experimental/historical Truffle catalogue entries including Apple Pkl, plus outside-runtime evidence and future/scalability/Protos-philosophy scoring selecting Candidate A′ | PERF006-B6A5 and later C-prime production module initialization |
| PLAT026 | Bytecode production root-tag compatibility boundary | RATIFIED | Explicit project-owner approval, 2026-09-11 after expanded Truffle root/tooling audit including GraalPy Bytecode DSL, SimpleLanguage, TruffleRuby, GraalJS, Espresso, Sulong/LLVM, GraalWasm, FastR, TruffleSqueak, Enso and Apple Pkl, plus GraalVM DAP/Bytecode-DSL root-prolog evidence and future/scalability/Protos-philosophy scoring selecting Candidate C′+ | PERF006-B6A6 and later Bytecode production debugger/tooling compatibility |
| PLAT027 | Task-owned terminal lifecycle composition around C-prime guest execution | RATIFIED | Explicit project-owner approval, 2026-09-11 after exhaustive review of all 16 principal + 21 experimental/historical Truffle catalogue entries including Apple Pkl, plus Rust/Tokio, Swift concurrency, BEAM, Java CompletionStage and Go; future/scalability/Protos-philosophy scoring selected Candidate A′ | PERF006-B6A6A and later Task-owned host lifecycle finalization around C-prime guest execution |
| PLAT028 | C-prime-owned suspendible callback orchestration and native-leaf boundary | RATIFIED | Explicit project-owner approval, 2026-09-11 after exhaustive audit of all 16 principal Truffle implementations, relevant experimental/historical implementations including Apple Pkl comparison, plus non-Truffle async runtimes and future/scalability/Protos-philosophy scoring selecting Candidate C | PERF006-B production callback cutover and later JVM/Truffle standard/runtime operations that invoke suspendible ordinary guest callbacks |
| PLAT029 | Deferred C-prime ownership for non-task-backed asynchronous I/O operations | RATIFIED | Explicit project-owner approval, 2026-09-12 after exhaustive current/historical Truffle audit including Apple Pkl, GraalJS, GraalPy, SOMns, TruffleSqueak, Espresso, TruffleRuby and PorcE/Orc plus non-Truffle async-runtime review; future/scalability/Protos-philosophy scoring selected Candidate B′ | PERF006-B deferred TextReader/TextWriter/buffered-I/O callback migration and future non-task-backed asynchronous operation C-prime custody |
| PLAT030 | C-prime ownership for asynchronous I/O lifecycle release/close | RATIFIED | Explicit project-owner approval, 2026-09-12 after exhaustive current/historical Truffle audit including Apple Pkl, GraalJS, SOMns, GraalPy, grCUDA, Yona, TruffleSqueak, Espresso, TruffleRuby and PorcE/Orc plus secondary non-Truffle evidence; future/scalability/Protos-philosophy scoring selected Candidate A′ | PERF006-B TextWriter.close and later asynchronous lifecycle-release/owning-close C-prime migration |
See `docs/project/decisions/platform/PLAT001_TRUFFLE_RUNTIME_HOSTING.md` for the selected topology,
its non-semantic boundary, alternatives, scaling rationale, invariants, and
explicitly deferred choices.

See `docs/project/decisions/platform/PLAT002_NETWORK_CAPABILITY_REPRESENTATION.md` for the selected
Network represented-capability boundary, rejected alternatives, scalability rationale and
backend choices deliberately deferred to later I028 slices.

See `docs/project/decisions/platform/PLAT003_TCP_LIVE_RESOURCE_ARCHITECTURE.md` for the selected JVM TCP
ordinary-resource representation, host-neutral acquisition custody and independent duplex-I/O
progress architecture; D052 remains authoritative for every observable TCP object-topology rule.

See `docs/project/decisions/platform/PLAT004_TRUFFLE_SOURCE_SECTION_OWNERSHIP.md` for the selected root-owned exact Source + node-local compact range + on-demand SourceSection projection, its cross-language rationale, scaling invariants and deliberately deferred instrumentation/tooling choices.

See `docs/project/decisions/platform/PLAT005_TRUFFLE_INSTRUMENTATION_ARCHITECTURE.md` for the selected layered semantic-minimum instrumentation architecture: common source-node mechanism, explicit Statement/Call baseline tags, replay-aware wrapper placement, deferred root/expression/value/yield surfaces, and scaling rationale.

See `docs/project/decisions/platform/PLAT006_TCP_HOST_IO_OPERATION_ENGINE.md` for the selected host-neutral I/O operation-engine boundary, initial bounded JDK NIO readiness backend, cross-runtime/scalability rationale, future completion/native-backend compatibility constraints, and deliberately deferred tuning choices.

See `docs/project/decisions/platform/PLAT007_IPV6_ONLY_TCP_LISTENER_ENFORCEMENT.md` for the public-JDK composite IPv6-only listener baseline, authority/scope and all-or-nothing port invariants, cross-runtime/Truffle rationale, known high-address-cardinality cost and preserved native O(1)-socket future path.

See `docs/project/decisions/platform/PLAT008_TRUFFLE_REPLAY_SITE_IDENTITY.md` for the selected logical replay-site identity contract, zero-allocation delegate-backed AST representation, wrapper transparency, live-replacement preservation rule and Bytecode-DSL-compatible future representation boundary.

See `docs/project/decisions/platform/PLAT009_BYTEWRITABLE_FIRST_EFFECT_GATE.md` for the host-neutral transient first-effect-attempt gate preserving existing ByteWritable cancellation/commitment semantics across readiness and future completion/native/WASI/brokered backends.

See `docs/project/decisions/platform/PLAT010_ACTOR_PLATFORM_CARRIERS.md` for the selected bounded reusable platform-carrier primitive for normal Actor guest execution, the rejection of virtual-thread carriers as the default CPU/guest substrate, and the required repeated 1/2/4/8 production-path evidence before PERF001-F #239 may close.

See `docs/project/decisions/platform/PLAT011_RUNTIMEHOST_CARRIER_SUBSTRATE.md` for the selected RuntimeHost-owned bounded carrier-capacity topology across multiple local Processes, its O(runtime CPU capacity) physical-thread scaling target, separate blocking/offload lane boundary, and preserved future work-stealing/NUMA/resource-governance evolution path.


See `docs/project/decisions/platform/PLAT012_VERIFIED_EXTERNAL_PACKAGE_CUSTODY_SOURCE_RESOLUTION.md` for the run-owned exact immutable package-resource scope, lazy host-neutral verified-resource reader, canonical no-path external ModuleKey boundary and future CAS/distributed backing evolution.

See `docs/project/decisions/platform/PLAT013_TRUFFLE_DEBUGGER_INTEROP_VALUE_PROJECTION.md` for the selected semantic-value-native read-only interop baseline, local-slot-only object projection, synthetic scope/view adapter boundary, identity/side-effect constraints and future Bytecode DSL / non-JVM evolution path.

See `docs/project/decisions/platform/PLAT014_TRUFFLE_COOPERATIVE_CONTINUATIONS.md` for the selected C-prime stackful continuation composition over Truffle Bytecode DSL, its suspension-only cost boundary, semantic-preservation constraints, cross-runtime evidence, scalability invariants and deliberately deferred implementation choices.

See `docs/project/decisions/platform/PLAT015_TRUFFLE_DEBUGGER_SCOPE_PROJECTION.md` for the selected activation-native debugger-scope projection, absence of an artificial language top scope or named receiver, one-runtime-lookup authority, suspension-bounded lifetime and AST/Bytecode-DSL-independent tooling boundary.\nSee `docs/project/decisions/platform/PLAT016_BYTECODE_CLOSURE_DEFAULT_EXECUTION_TOPOLOGY.md` for the selected single Bytecode Closure activation-root topology, default-expression execution and continuation constraints, cross-language evidence, scalability rationale and semantically invisible internal-outlining freedom.\n

See `docs/project/decisions/platform/PLAT017_SELECTED_STANDARD_OBJECT_CALL_INTRINSIC.md` for the selected post-lookup canonical-standard `Object.call` Bytecode intrinsic, its D013/PLAT014/tooling equivalence gates, exhaustive Truffle precedent, scalability constraints and the exact PERF006-B2D3B implementation boundary released by ratification.

See `docs/project/decisions/platform/PLAT018_DAP_DEBUG_SESSION_HOSTING.md` for the selected RuntimeHost/Engine-owned one-shot GraalVM DAP topology, OS-ephemeral loopback endpoint, Protos-owned readiness boundary, launch/wait-attached lifecycle constraints, scaling rationale, distribution consequence and deliberately deferred public tooling choices.


See `docs/project/decisions/platform/PLAT019_NATIVE_SEMANTIC_SUSPENSION_BRIDGE.md` for the selected B-prime suspension-capable native provenance boundary, explicit resumability requirement, interpreter-owned C-prime capture, two-phase Task continuation publication, exhaustive Truffle/Apple-Pkl review, scalability constraints and deliberately unspecified private native-to-interpreter transport.


See `docs/project/decisions/platform/PLAT020_CONTEXT_BOUND_TRUFFLE_FILE_SOURCE_MATERIALIZATION.md` for the selected A′ split between immutable backend-neutral source facts and owning-Context physical Truffle Source materialization, exhaustive Truffle/Apple-Pkl review, D065 path/content invariants, multi-Context scalability constraints and deliberately deferred Java representation/cache choices.

See `docs/project/decisions/platform/PLAT021_BYTECODE_DYNAMIC_CONTROL_UNWIND.md` for the selected Candidate F Bytecode-local structured-control ownership, separate normal/suspension/unwind lanes, narrow private EH bridge, exhaustive Truffle/Apple-Pkl review, scaling constraints and PERF006-B4 implementation boundaries.

See `docs/project/decisions/platform/PLAT022_CONTEXT_SOURCE_READABILITY_AUTHORITY.md` for the selected Candidate D′ Context-local admitted-source read-only authority, exhaustive Truffle/Apple-Pkl/tooling review, D065/PLAT020 composition, debugger-transparency and least-authority invariants, multi-Context scaling constraints and deliberately deferred public filesystem semantics.

See `docs/project/decisions/platform/PLAT023_ASYNC_EXACT_EXECUTION_CARRIER_TOPOLOGY.md` for the selected Candidate A′ fresh-platform-Thread-per-admitted-execution topology, exhaustive Truffle/Apple-Pkl carrier audit, D055/D069 composition, cancellation/custody invariants, scaling analysis and preserved virtual-thread/OS-worker/remote evolution paths.

See `docs/project/decisions/platform/PLAT024_STATIC_LANGUAGE_SERVICE_HOSTING_PROTOCOL_BOUNDARY.md` for the selected Candidate A′ dedicated toolchain-matched static LSP process, stdio baseline, thin editor client, protocol-neutral analysis core, exhaustive Truffle/Apple-Pkl survey, scalability/future stress analysis, and preserved self-hosted/native migration path.

See `docs/project/decisions/platform/PLAT025_BYTECODE_MODULE_INITIALIZATION_COMPOSITION_BOUNDARY.md` for the selected Candidate A′ exact-standard post-lookup `import` intrinsic, backend-private module-lifecycle carrier, nested C-prime child-root composition, exhaustive Truffle/Apple-Pkl survey, scaling/future constraints and PERF006-B6A5 implementation invariants.

See `docs/project/decisions/platform/PLAT026_BYTECODE_PRODUCTION_ROOT_TAG_COMPATIBILITY_BOUNDARY.md` for the selected Candidate C′+ semantic-root/helper-root split, automatic `RootTag` only on truthful top-level/module/Closure activation roots, deferred `RootBodyTag`, expanded Truffle/Apple-Pkl/DAP evidence, scaling/future constraints and PERF006-B6A6 implementation invariants.
See `docs/project/decisions/platform/PLAT027_TASK_TERMINAL_LIFECYCLE_BOUNDARY.md` for the selected Candidate A′ Task-owned one-shot terminal lifecycle boundary, C-prime sole-continuation constraint, exhaustive Truffle/non-Truffle evidence, scaling and cancellation invariants, and the bounded PERF006-B6A6A implementation authority released by ratification.

See `docs/project/decisions/platform/PLAT028_C_PRIME_CALLBACK_ORCHESTRATION_NATIVE_LEAF_BOUNDARY.md` for the selected Candidate C single-continuation architecture: callback-owning post-guest control stays C-prime-resumable, Java/native remains guest-non-reentrant leaf mechanics, PLAT019 native suspension and PLAT027 terminal finalization remain valid, and no generic host continuation stack, blocking carrier, child-Task bridge, replay island or host-stack capture is selected.
