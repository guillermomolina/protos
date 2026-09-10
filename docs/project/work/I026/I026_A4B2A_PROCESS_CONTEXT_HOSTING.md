# I026-A4B2A — shared Engine + Process Context lifecycle

Status: **CLOSED by publication of I026-A4B2A**

Implementation version: `0.2.269-SNAPSHOT`

Nature: non-normative implementation evidence for ratified `PLAT001`

## Purpose

Establish the Process-scoped half of PLAT001 before carrier integration: one explicit shareable
Truffle Engine owner, one distinct multithread Context per hosted semantic Process, a fixed
implementation-only Process-to-host binding, and lifecycle isolation. This slice does not yet route
Actor/P carriers or claim concurrent Core bootstrap safety; those remain A4B2B.

## Hosting boundary

`ProtosPolyglotRuntimeHost` owns one explicit Engine and may create multiple
`ProtosPolyglotProcessContext` instances over it. The host is deliberately constructed and owned by
the embedding layer rather than installed as a static singleton, so PLAT001's exact Engine topology
remains deferred. Each semantic `ProtosProcessRuntime` accepts one fixed
`ProtosProcessExecutionHost` binding while RUNNING. The runtime package depends only on that
host-neutral internal interface, not on GraalVM classes.

The following remain false:

```text
Engine  == Node
Context == Process
Thread  == Actor
```

## Lifecycle

Process semantics remain authoritative. A host binding receives notification only after the
semantic Process has already reached `TERMINATED`. The associated Polyglot Context then rejects
new entries and closes physically. If termination completes on a carrier currently entered in that
Context, close is deferred until the carrier leaves rather than attempting a read-to-write lifecycle
upgrade.

The Engine owner refuses to close while any Process Context remains active. It therefore cannot be
used as hidden Process termination/cancellation authority. Terminating one Process closes only its
Context; sibling Processes sharing the Engine remain usable.

## Staging boundary

Until A4B3 completes the production-driver cutover, `ProtosProcessRuntime` may still be unbound and
therefore use the staged direct host path. This is migration machinery, not the target production
architecture. A4B3 must ensure every production Process driver binds before guest execution and
retire the direct primary entry architecture.

## Remaining A4B2 work

A4B2B is READY and owns:

- routing `ProtosActorScheduler` carriers through the bound Process Context;
- propagating the same host placement through isolated P carriers, including nested P;
- making first/concurrent Core-root protocol publication safe without a guest-execution GIL;
- concurrent Actor/P and concurrent Process-bootstrap evidence; and
- final A4B2 closure / A4B3 dependency release.

`ContextPolicy.SHARED` remains deferred. A4B2A uses one explicit Engine across Contexts but does
not alter the language registration's default EXCLUSIVE context policy.

## Specification boundary

No normative Protos semantics change. Process termination, Actor identity, task ownership, P
isolation, failure authority and module identity remain owned by their existing specification and
runtime layers.
