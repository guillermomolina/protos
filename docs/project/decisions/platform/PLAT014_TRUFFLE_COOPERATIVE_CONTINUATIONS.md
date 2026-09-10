# PLAT014 — Truffle cooperative suspension continuation/compilation boundary

Status: `RATIFIED`

Selected architecture: C-prime (`C′`) — Protos stackful continuation composition
over Truffle Bytecode DSL continuations.

Approval: explicit project-owner approval on 2026-09-10 after the comparative
Truffle-language/runtime audit and the complete PLAT014-A feasibility spike.

Publication base: `3139454d98abc5a4f1f918377fd660478db4d8e9`

Primary consumers: PERF006, the Truffle execution/continuation work consumed by
I026 and later runtime slices, and future Task/Future execution backends on the
JVM/Truffle implementation.

## Boundary

PLAT014 is a durable JVM/Truffle implementation architecture decision. It does
not introduce or redefine observable Protos Task, Future, Error, handler,
`ensure`, cancellation, non-local-return, Actor, Process, call, or closure
semantics. Those remain owned by their existing normative specifications and
ratified language decisions.

Another conforming implementation may preserve the same Protos semantics with a
different continuation mechanism. Truffle Bytecode DSL types are therefore
backend machinery, not Protos language objects or public concepts.

## Problem

PERF006-A established that ordinary Maven/checkout execution did not expose the
intended optimizing Truffle runtime, while simply making
`HotSpotTruffleRuntime` available was not sufficient: the current cooperative
replay architecture forced every expression in an active Task segment through
`CompilerDirectives.transferToInterpreter()`.

The resulting optimizer trace showed a pathological pattern rather than an
ordinary warmup effect:

- 2,702,344 `opt deopt` events in the bounded compiler trace;
- zero explicit `opt inv.` invalidations;
- 260 compilation failures/permanent-bailout markers;
- dominant deoptimizations inside ordinary pure-Protos SHA256 work;
- the same optimizing runtime with Truffle compilation disabled completed the
  representative package-tool workload materially faster than compilation
  enabled.

The problem is therefore architectural: per-expression replay bookkeeping and
forced interpreter transfer make normal Task execution hostile to Truffle
partial evaluation.

## Selected architecture

The JVM/Truffle backend SHALL implement resumable cooperative execution according
to these rules:

1. Ordinary guest execution remains compilation-eligible. Merely executing
   inside a `ProtosTask` MUST NOT force every ordinary expression into the
   interpreter or into continuation bookkeeping.

2. Continuation state is captured only when execution actually reaches a
   semantic suspension boundary, such as a pending `Future.value()`.

3. Each Protos executable root may use Truffle Bytecode DSL continuation/yield
   support for its own frame/location. Because Bytecode DSL continuations are
   single-root/single-method, Protos composes those continuations across ordinary
   call boundaries into one private logical stackful continuation chain.

4. When a callee suspends, each active Protos caller preserves its own resumable
   state while retaining the suspended child continuation. Resumption proceeds
   through that logical chain without replaying already-completed guest effects.

5. The suspended `ProtosTask` owns the logical continuation state. Its physical
   platform carrier is released immediately and remains reusable under the
   already-ratified bounded-carrier architecture. Task identity MUST NOT depend
   on Java thread identity.

6. Suspension itself is not semantic unwind. Existing handler and `ensure`
   scopes remain live across suspension. Cleanup executes only for the
   already-defined normal exit, Error, non-local return, cancellation, or other
   real unwind conditions.

7. Error, cancellation, non-local-return and other internal control outcomes
   cross continuation boundaries without changing their existing Protos-visible
   identity, precedence, ownership, or cleanup semantics. Their exact Java class
   hierarchy or adapter representation remains backend-private.

8. Stable continuation/call targets should use the normal Truffle
   direct-call/caching mechanisms where applicable. The selected architecture
   MUST NOT require a global continuation registry, a global execution lock, or
   a per-expression replay tape on the ordinary hot path.

9. PLAT005 remains the tooling authority. Bytecode DSL instrumentation maps the
   already-approved semantic roles to `StatementTag` and `CallTag`; suspension
   and resumption must not duplicate completed events. Instrumentation remains
   observational and should stay lazy/pay-only-when-used.

10. PLAT008 remains the logical location-identity authority.
    `BytecodeLocation` is an appropriate Truffle-backend representation when it
    preserves that already-ratified identity contract; the Truffle type itself
    does not become semantic authority.

11. Truffle-specific continuation APIs remain behind an internal backend
    boundary. The selected DIST/toolchain version is pinned and validated
    because Bytecode DSL continuation APIs may evolve independently of Protos
    semantics.

## Feasibility evidence

PLAT014-A / GitHub #269 was explicitly authorized as a non-publishing
feasibility spike and is complete.

### A1 — isolated continuation composition

Baseline `3baa37a726436c197b1081a1b2973b82c368a691` demonstrated under
`HotSpotTruffleRuntime`:

- deep A -> B -> C continuation composition;
- multiple suspensions;
- exact resume without replay;
- `finally` only on real completion/unwind;
- guest Error/control transfer across the composed chain;
- independent suspended chains;
- reuse of one carrier while another logical execution remains suspended.

Result: `C_PRIME_SUBSTRATE_SUPPORTED`.

### A2a — real Task/Future/scheduler/cancellation integration

Baseline `3baa37a726436c197b1081a1b2973b82c368a691` integrated the candidate
mechanism with the real `ProtosTask`, `ProtosFutureValue`,
`ProtosActorExecutionDomain` and `ProtosDynamicControlState`:

- a real Task reached `SUSPENDED` while retaining a Bytecode DSL continuation;
- a real Future woke exactly that Task;
- the same carrier executed another Task while the first remained suspended;
- Future completion resumed the continuation without replay;
- cancellation detached only the waiting Task, preserved the dependency Future,
  traversed the existing cancellation unwind phases and executed continuation
  cleanup.

Fallback-runtime warnings: zero.

### A2b — Error, handler, ensure and non-local return

Baseline `0f3583c5e625ebce096e97b64f77dd703fde8d5a` preserved the existing
control/unwind contracts across suspension:

- exact Protos Error occurrence and `ProtosSignalException` identity;
- selected handler inactive before crossed `ensure` cleanup;
- cleanup itself able to suspend and later resume without re-entering the body;
- pending Error transfer surviving cleanup suspension unchanged;
- cleanup Error superseding the original transfer and selecting only an
  eligible still-active outer handler;
- exact `ProtosNonLocalReturnException` payload and `ProtosReturnHome` identity;
- `ensure` completion preceding return-home consumption.

The spike used a private guest-transfer adapter only to cross Bytecode DSL
exception tables; no production transfer representation was selected by that
experiment.

### A2c — instrumentation

Baseline `d588fa597e5ad148476030e14a54bca3ba2bc03b` demonstrated:

- exact source sections;
- existing PLAT005 `StatementTag` and `CallTag` authority only;
- no RootTag or ExpressionTag expansion;
- no duplicate pre-yield events after resume;
- explicit instrumentation `YIELD`/`RESUME` behavior rather than a false guest
  exception;
- no replay with instrumentation attached;
- cached ordinary execution with zero active instrumentation instructions before
  attachment and eight instrumentation instructions after attachment.

Fallback-runtime warnings: zero.

### A2d — optimizing execution/deoptimization

Baseline `5e0fc2e910b069f5200a8a434fccdf3e78ca47f1` executed the same C-prime
A -> B -> C continuation workload on four pinned CPUs in alternating compiled
and `engine.Compilation=false` modes.

Each measured sample performed 4,000 real suspensions and 2,048,000 ordinary
logical operations after warmup. All samples returned the same checksum.

Measured medians:

- compiled: `0.012045 s`;
- same `HotSpotTruffleRuntime`, compilation disabled: `0.096507 s`;
- compiled / non-compiled ratio: `0.124813` (about 8.0x faster).

Compiler evidence across the three compiled samples:

- 9 successful `opt done` compilations;
- 0 `opt deopt`;
- 0 compilation failures;
- 0 permanent bailouts;
- 0.0 deoptimizations per million logical operations;
- 0 fallback-runtime warning markers.

This directly falsifies the concern that the selected continuation composition
intrinsically defeats the Truffle optimizer.

## Comparative review

The decision was compared at implementation level against relevant Truffle
languages/runtimes rather than selected from API documentation alone:

- GraalJS localizes resumable state to suspension-capable nodes rather than
  imposing it on every expression.
- GraalPy uses Bytecode DSL continuation/yield support, providing strong current
  precedent for the substrate.
- Espresso demonstrates the desired stackful cost model: retain live execution
  state on actual suspension rather than a second physical thread or replay
  history.
- TruffleSqueak preserves process/context execution state around process switch,
  a particularly relevant Smalltalk-family precedent against expression-history
  replay.
- Apple Pkl provides a useful negative/control precedent: ordinary expression
  and call paths remain compilation-friendly and interpreter transfer is not a
  universal execution tax.
- TruffleRuby's thread-per-Fiber approach preserves stack state simply but was
  rejected for Protos because it conflicts with bounded reusable carriers and
  scales physical execution resources with suspended logical work.

Raw Bytecode DSL `yield` alone was also rejected: Protos suspension can occur
through arbitrary ordinary call depth, so single-root continuations must be
composed rather than exposing an `async`/`await`-style semantic boundary.

## Why this is the Protos architecture

The selected design preserves the existing small semantic universe:

- no public `Continuation` object;
- no `async` function category;
- no `await` keyword;
- no generator/coroutine parallel universe;
- no Task = thread identity;
- no special SHA256/native shortcut;
- no global continuation manager required by semantics.

An ordinary Closure remains an ordinary Closure. An ordinary call remains an
ordinary call. Only when execution actually suspends does the implementation pay
to retain resumable state. This follows the project principles "ordinary things
should remain ordinary", "mechanisms over institutions", "pay only for what you
use", "scale by composition", "minimize shared mutable state", and "keep platform
differences at the boundary".

## Scalability invariants

The implementation consuming PLAT014 must preserve these scaling properties:

- physical carrier count remains bounded by runtime capacity, not suspended Task
  count;
- retained continuation state is proportional to actually live suspended
  execution depth/state, not to every expression ever executed;
- unrelated Tasks, Actors and Processes do not share mutable continuation state;
- no global continuation lock is introduced;
- ordinary non-suspending workloads remain eligible for Truffle optimization;
- instrumentation/debugger attachment does not become an always-on runtime tax;
- implementation-specific continuation state remains local enough that future
  Process isolation, NUMA placement or distributed execution can evolve without
  redefining Protos semantics.

PLAT014 does **not** claim that current Bytecode DSL continuation objects are
serializable or migratable between Processes/machines. Distributed continuation
migration is deliberately not selected.

## Rejected alternatives

- **A — replay plus compilation disabled:** rejected because it permanently
  sacrifices the optimizer on the normal Task execution path.
- **B — permanent AST state-machine framework:** rejected as avoidable duplicate
  continuation infrastructure with substantial semantic-maintenance burden.
- **C — raw Bytecode DSL yield only:** rejected because its single-root
  continuation boundary does not by itself satisfy arbitrary-depth ordinary
  Protos calls.
- **D — temporary AST hybrid then Bytecode DSL:** rejected after the spike showed
  direct C-prime feasibility; it would create a second transitional continuation
  system that would later be retired.
- **E — host/virtual thread per suspended Task:** rejected because it couples
  logical lifetime to physical/host continuation resources and conflicts with
  PLAT010/PLAT011.

## Deliberately deferred implementation choices

Ratification does not pre-authorize arbitrary implementation details. The
following remain implementation work or future decision checkpoints:

- exact migration slices from the current AST/replay backend to Bytecode DSL;
- exact internal class names and data structures for composed continuation
  outcomes;
- the exact Java guest-transfer/adaptation representation used for Error,
  cancellation and non-local return;
- exact Bytecode DSL lowering shapes, quickening thresholds and tuning;
- exact Maven dependency scope, launcher/classpath and distribution wiring that
  makes the selected optimizing runtime available on every intended execution
  surface;
- debugger/internal location bridges beyond the already-ratified PLAT005,
  PLAT008, PLAT013 and PLAT015 contracts;
- serialization, persistence or migration of live continuations across
  Processes, hosts or machines.

If any deferred implementation item exposes a materially different durable
architecture or observable semantic choice, it crosses the normal PLAT/Dxxx
approval gate before implementation.

## Consequences

After this decision is durably published:

- PLAT014 is `RATIFIED`;
- the PLAT014 architecture blocker on PERF006 remediation is released;
- PERF006 may implement and validate the ordinary optimizing-runtime closure only
  in a way compatible with this continuation architecture;
- production replacement of the current replay backend should be decomposed into
  independently valid slices and validated against the A1/A2 semantic and
  optimizer gates;
- PLAT014-A/#269 remains historical feasibility evidence and is already closed;
- no specification or implementation version changes merely because this
  architecture decision is ratified.
