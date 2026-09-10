# I026-A4B2B1 — Actor carrier Process-Context routing

Status: **CLOSED by publication of I026-A4B2B1**

Implementation version: `0.2.271-SNAPSHOT`

Nature: non-normative implementation evidence for ratified `PLAT001`

## Purpose

Close only the Actor-carrier half of I026-A4B2B. Every non-preemptive segment selected by
`ProtosActorScheduler` for an Actor hosted by a semantic Process now executes through that
Process's already-fixed `ProtosProcessExecutionHost`. A4B2B1 does not route isolated `P` carriers
and does not change Core-root bootstrap publication; those remain A4B2B2 and A4B2B3.

## Routing boundary

The scheduler remains Truffle-neutral. It does not import Polyglot, Truffle, Engine, Context or
`ProtosPolyglotProcessContext`. Immediately around the one selected Actor segment it asks the
Actor's owning Process to run the segment in its implementation host:

```text
Actor scheduler
    |
    +-- select exactly one non-preemptive segment
    |
    `-- owning Process.callInExecutionHostForRuntime(...)
            |
            +-- unbound staged Process -> direct call (until A4B3)
            |
            `-- bound Process -> Process-scoped Truffle Context
                    |
                    `-- control / Task / mailbox segment
```

Actor identity remains independent of carrier and Context identity. An Actor without a Process
continues on the direct scheduler path. A Process that is intentionally still unbound during the
A4B3 migration also preserves the existing direct staging path through the already-published
A4B2A host-neutral interface.

## Evidence

Focused runtime evidence proves that all three scheduler segment classes cross the same Process
host boundary:

- initialization/control turn;
- runnable Task segment; and
- accepted mailbox turn.

Focused integration evidence hosts one Process on a real `ProtosPolyglotRuntimeHost`, schedules
two distinct Actors simultaneously on two carrier threads, and proves both observe the same exact
`ProtosLanguageContext`. No Context-per-Actor mapping, carrier affinity, semantic ThreadLocal, GIL,
or global execution lock is introduced.

Process termination remains authoritative. The integration evidence terminates the semantic Process
only after both Actor segments have left and observes the A4B2A Context lifecycle cleanup.

## Remaining A4B2B work

- **I026-A4B2B2 — P carrier Process-Context routing:** propagate the owning Process host through
  isolated P work, including nested P, without weakening P transfer/isolation.
- **I026-A4B2B3 — concurrent Core bootstrap closure:** resolve and prove safe Core-root bootstrap
  publication across concurrently hosted Processes, then close A4B2B/A4B2 and release A4B3.

`ContextPolicy.SHARED` remains deferred.

## Specification boundary

No normative Protos semantics change. Actor scheduling, non-preemptive segment execution, Process
isolation/failure, Task ownership, mailbox ordering and Actor identity remain owned by their
existing specification/runtime layers. This slice changes only where the existing host segment is
placed while it executes.
