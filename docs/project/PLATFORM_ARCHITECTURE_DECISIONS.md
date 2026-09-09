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

See `docs/project/PLAT001_TRUFFLE_RUNTIME_HOSTING.md` for the selected topology,
its non-semantic boundary, alternatives, scaling rationale, invariants, and
explicitly deferred choices.

See `docs/project/PLAT002_NETWORK_CAPABILITY_REPRESENTATION.md` for the selected
Network represented-capability boundary, rejected alternatives, scalability rationale and
backend choices deliberately deferred to later I028 slices.

See `docs/project/PLAT003_TCP_LIVE_RESOURCE_ARCHITECTURE.md` for the selected JVM TCP
ordinary-resource representation, host-neutral acquisition custody and independent duplex-I/O
progress architecture; D052 remains authoritative for every observable TCP object-topology rule.
