# Platform Architecture Decisions

This registry records durable, non-normative architecture decisions that depend
on a concrete implementation platform, host runtime, VM, operating system,
native backend, or equivalent execution substrate.

`PLATxxx` is deliberately distinct from normative language design:

- `Dxxx` owns language/specification design decisions that define or constrain
  observable Protos behavior;
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

See `docs/project/decisions/platform/PLAT015_TRUFFLE_DEBUGGER_SCOPE_PROJECTION.md` for the selected activation-native debugger-scope projection, absence of an artificial language top scope or named receiver, one-runtime-lookup authority, suspension-bounded lifetime and AST/Bytecode-DSL-independent tooling boundary.
