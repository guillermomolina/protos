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

See `docs/project/PLAT001_TRUFFLE_RUNTIME_HOSTING.md` for the selected topology,
its non-semantic boundary, alternatives, scaling rationale, invariants, and
explicitly deferred choices.

See `docs/project/PLAT002_NETWORK_CAPABILITY_REPRESENTATION.md` for the selected
Network represented-capability boundary, rejected alternatives, scalability rationale and
backend choices deliberately deferred to later I028 slices.

See `docs/project/PLAT003_TCP_LIVE_RESOURCE_ARCHITECTURE.md` for the selected JVM TCP
ordinary-resource representation, host-neutral acquisition custody and independent duplex-I/O
progress architecture; D052 remains authoritative for every observable TCP object-topology rule.

See `docs/project/PLAT004_TRUFFLE_SOURCE_SECTION_OWNERSHIP.md` for the selected root-owned exact Source + node-local compact range + on-demand SourceSection projection, its cross-language rationale, scaling invariants and deliberately deferred instrumentation/tooling choices.

See `docs/project/PLAT005_TRUFFLE_INSTRUMENTATION_ARCHITECTURE.md` for the selected layered semantic-minimum instrumentation architecture: common source-node mechanism, explicit Statement/Call baseline tags, replay-aware wrapper placement, deferred root/expression/value/yield surfaces, and scaling rationale.

See `docs/project/PLAT006_TCP_HOST_IO_OPERATION_ENGINE.md` for the selected host-neutral I/O operation-engine boundary, initial bounded JDK NIO readiness backend, cross-runtime/scalability rationale, future completion/native-backend compatibility constraints, and deliberately deferred tuning choices.

See `docs/project/PLAT007_IPV6_ONLY_TCP_LISTENER_ENFORCEMENT.md` for the public-JDK composite IPv6-only listener baseline, authority/scope and all-or-nothing port invariants, cross-runtime/Truffle rationale, known high-address-cardinality cost and preserved native O(1)-socket future path.

See `docs/project/PLAT008_TRUFFLE_REPLAY_SITE_IDENTITY.md` for the selected logical replay-site identity contract, zero-allocation delegate-backed AST representation, wrapper transparency, live-replacement preservation rule and Bytecode-DSL-compatible future representation boundary.

See `docs/project/PLAT009_BYTEWRITABLE_FIRST_EFFECT_GATE.md` for the host-neutral transient first-effect-attempt gate preserving existing ByteWritable cancellation/commitment semantics across readiness and future completion/native/WASI/brokered backends.
