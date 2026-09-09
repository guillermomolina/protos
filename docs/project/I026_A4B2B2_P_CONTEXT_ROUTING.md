# I026-A4B2B2 — P carrier Process-Context routing

Status: **CLOSED by publication of I026-A4B2B2**

Implementation version: `0.2.273-SNAPSHOT`

Nature: non-normative implementation evidence for ratified `PLAT001`

## Purpose

Close only the isolated-P carrier half of I026-A4B2B. P remains the existing semantic isolated
parallel execution domain; this slice carries only implementation placement so guest code executed
by a P carrier enters the same Truffle Context as the originating hosted Process. A4B2B2 does not
change concurrent Core-root bootstrap publication; that remains the final A4B2B3 slice.

## Placement propagation

`ProtosParallelRuntime` already retained a runtime-only registry of live P execution domains in
order to recognize operations such as `ByteRegion.parallelRange`. A4B2B2 refines that existing
registry from a membership-only set into domain placement metadata:

```text
Actor/Process activation
        |
        | Snapshot.capture
        v
P work item -- host placement only --> Process execution host
        |
        v
fresh P execution domain
        |
        +-- guest invocation enters Process Context
        |
        `-- nested parallel submission inherits placement from this P domain
```

The placement metadata is not a Protos Process capability and is never copied through the P value
transfer graph. P still acquires no ambient Actor, Process, I/O or scheduler authority. The runtime
uses the metadata only to enter/leave implementation hosting around guest execution.

A staged unbound Process or standalone P test carries an explicit direct placement. No semantic
ThreadLocal, carrier identity, Context-per-P mapping or P-specific Context is introduced.

## Execution boundary

Only guest invocation is wrapped in the Process execution host. Queue admission, logical input
snapshotting, deterministic result bookkeeping and other inert host work do not require a Context
entry merely because P exists. Nested P discovers the same placement from its parent P execution
domain, so carrier migration and helper execution do not affect Context identity.

## P root task ownership

The fresh P execution domain already owns one root `ProtosTask`. A4B2B2 attaches the P module
activation to that exact task before invoking guest code. This does not expose a Task value or add
a new scheduling concept; it makes the activation accurately reflect the task that is already
executing it.

Consequently, `Future.value()` inside P uses the existing evaluator suspension/resume path, and a
nested `parallel(...)` submission created from that activation is structurally owned by the current
P task under the already-defined Future/task ownership rules. No busy-wait, blocking host join, or
ambient Actor task is introduced.

## Evidence

Focused Polyglot evidence uses one real hosted Process and proves:

- two independent P computations overlap on distinct carriers while observing the exact same
  `ProtosLanguageContext` as their Process;
- a nested `Closure.parallel(...).value()` creates a fresh semantic P domain yet inherits the same
  Process Context placement; and
- both sibling and nested results preserve the existing Future/P execution contract.

The retained `ProtosParallelExecutionTest` continues to exercise unbound/direct execution,
bounded carrier count, caller-domain Future ownership and deterministic failure selection.

## Remaining A4B2B work

**I026-A4B2B3 — concurrent Core bootstrap closure** has its A+ executable-layer direction approved under PLAT001, but is now **BLOCKED by B010**. The remaining normative question is the structural-state/isolation ownership of the standard root `Object`; a bootstrap-only lock is not sufficient while guest-visible mutable root state would remain physically shared. Only after that dependency is closed and concurrent multi-Process publication evidence passes may A4B2B3 close A4B2B/A4B2 and release A4B3.

`ContextPolicy.SHARED` remains deferred.

## Specification boundary

No normative Protos semantics change. P isolation, projection, transfer/snapshot behavior, nested P,
Future ownership, cancellation, deterministic parallel algorithms and physical parallelism remain
owned by `spec/concurrency/PARALLEL_EXECUTION.md` and the existing runtime. The Process hosting
pointer is implementation placement only and is not a transferable or observable Protos value.


## Truffle sharing-layer closure

The semantic P snapshot remains synchronous before a successful submission returns. Only Truffle
execution-plan rematerialization is deferred: a projected non-native Closure creates fresh
CallTargets on first invocation after its P carrier has entered the owning Process Context. This
prevents AST/CallTarget reuse across Truffle sharing layers without moving the observable snapshot
point.
