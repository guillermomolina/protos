# I026-A4B1 — Truffle multithread safety audit

Status: **CLOSED by publication of I026-A4B1**

Implementation version: `0.2.267-SNAPSHOT`

Nature: non-normative implementation safety evidence for ratified `PLAT001`

## Purpose

Establish the bounded concurrency claim required before Protos relies on
Truffle multi-thread access. This slice does not yet bind a Polyglot Context to
a semantic Protos Process and does not cut CLI, Actor, P or tool drivers over;
those remain I026-A4B2/A4B3.

## Audited state

### Truffle language and compilation state

`ProtosLanguage` owns one final `ProtosSourceCompiler`. The compiler contains one
final `Canonicalizer` and one final `CanonicalToTruffleLowerer`. The canonicalizer
has no mutable instance state. The lowerer contains only one final immutable
`ProtosRootFactory`; source-bound compilation creates a fresh root factory and a
fresh lowerer view for that Source. `ProtosParser` is created per compilation.
No parse request stores mutable source/activation state on the language instance.

The current registration keeps Truffle's default EXCLUSIVE context policy.
A4B1 therefore makes no cross-Context language-instance sharing claim and does
not approve `ContextPolicy.SHARED`.

### Language-context state

`ProtosLanguageContext` contains the Truffle `Env` for exactly one Polyglot
Context plus the static Truffle `ContextReference` used to locate that current
context while entered. It stores no Actor, Task, Process, ModuleKey, activation,
resolver, mutable module cache or carrier identity. No semantic ThreadLocal is
introduced.

### Semantic/runtime state

Protos execution continues to pass `ProtosActivation` explicitly to roots.
Actor scheduling already guarantees at most one executing segment per Actor while
allowing distinct Actors on distinct carriers. Module cache state remains
Actor-local. P remains an isolated execution domain with explicit value transfer.
A4B1 does not reinterpret any of those rules as Truffle thread ownership.

### Core/bootstrap global state

The runtime still has process-independent implementation state such as the
static root `ProtosObjectValue` and bootstrap protocol installation that mutates
that root during Core construction. Existing production Core bootstrap happens
before the steady-state Actor execution it provisions. A4B1 therefore does not
run protocol installation concurrently and does not add a global bootstrap lock.

This is an explicit A4B2 gate: before multiple hosted Process Contexts may
bootstrap concurrently under one shared Engine/runtime, B2 must prove or provide
a bounded safe bootstrap publication mechanism. A4B1's `isThreadAccessAllowed`
claim covers concurrent entered guest execution over already-published runtime
machinery; it is not evidence for concurrent unsynchronized mutation of the
static Core bootstrap root.

### P and Actor carrier integration

A4B1 establishes the carrier-safe Context entry primitive but does not yet wire
that primitive into `ProtosActorScheduler` or `ProtosParallelRuntime`, because the
Process-to-Context ownership/lifecycle object does not exist until A4B2. B2/B3
must route those existing carriers through the same Process-hosted Context; they
must not create Context-per-Actor, Context-per-carrier or a global serialization
lock.

## Implementation

`ProtosLanguage.isThreadAccessAllowed(...)` now authorizes external carrier
threads. No `initializeMultiThreading` hook is needed because the audited
language/context implementation has no single-thread-only transition state.

`ProtosPolyglotExecutionContext` no longer records an owner thread or remains
permanently entered. Each `execute(...)` call:

1. acquires a shared per-Context lifecycle read lock;
2. verifies the context is open;
3. enters the Polyglot Context on the current carrier;
4. parses through `ContextReference -> Env.parsePublic -> ProtosLanguage.parse`;
5. executes through the existing activation-bearing root-task machinery; and
6. leaves the Polyglot Context before releasing the lifecycle read lock.

Multiple read holders execute concurrently. `close()` acquires only the
per-Context write side, so it waits for already-entered executions and prevents
new entry after closure without serializing unrelated guest executions. Closing
from a carrier that currently holds an execution read lock fails immediately
instead of attempting an impossible read-to-write upgrade.

## Evidence

Focused tests require:

- ordinary parse/execution through the registered language;
- foreign-language Source rejection;
- two distinct host carrier threads simultaneously blocked inside one Protos
  Context, observing one exact `ProtosLanguageContext`;
- completion of both executions after release, proving no global GIL/serialization
  in the bridge;
- `close()` waiting while guest execution is entered and later rejecting entry;
- a new Polyglot Context producing a distinct language-context identity; and
- retained registration/Source-bound parsing, Actor scheduler and P tests plus
  the complete Maven suite.

## Remaining PLAT001 work

A4B2 is READY and owns:

- one shared Engine supplied to multiple Process-hosted Contexts;
- the concrete Process/Context lifetime binding;
- Actor/P carrier routing into the owning Process Context;
- concurrent Process bootstrap safety for the existing static Core bootstrap
  machinery;
- termination/close isolation across Processes; and
- multi-Process/concurrent-Actor evidence.

`ContextPolicy.SHARED` remains deferred and is not implied by this closure.
A4B3 remains dependency-blocked on A4B2 and owns the production CLI/REPL/tool/
workspace cutover plus retirement of the direct compiler/call entry path.

## Specification boundary

No normative Protos specification changes. Thread, Context, Engine and host lock
identity remain implementation-only; Process/Actor/Task/P semantics remain
unchanged.
