# I115 — Temporary host-to-guest entry deviation

> **Status:** provisional implementation note, non-normative.
> **Owner:** I115/#931; related performance evidence: PERF042/#871 and PERF033/#832.
> **Scope:** standard Polyglot embedding, extracted source-backed Closure invoked via `Value.execute()`.
> **Authority:** Protos `spec/` remains normative. This note records a deliberate, owner-authorized exception; it **does not amend the specification**.

## Objective

Keep the ordinary host-to-guest call as close as practical to the established Truffle
interop shape: Polyglot framework entry, a cached direct target, compact argument
preparation, one direct guest call, and return projection. Runtime facilities for
host-entry suspension remain available through the existing fallback, rather
than imposed on every call.

The strategy is **pay-as-you-grow for host-entry admission and suspension state**, not
lazy creation of the RootActor. Standard embedding already creates the Process
and RootActor during the first valid host evaluation; this slice does not alter
that bootstrap boundary.

## Physical paths

Previously, a direct embedding Closure invocation paid the following operations
for every call, including trivially non-suspending Call0 and identity bodies:

```text
Value.execute()
  -> Truffle HostToGuestRootNode
  -> ProtosHostExecutableClosure.Execute.direct
  -> admitArguments() / owning Context check
  -> ProtosEmbeddedProcess.enterRootActor()       [Thread + CAS admission]
  -> beginHostEntryExtent()                      [Actor execution-domain mutation]
  -> prepare()                                  [B5 ordinary Closure frame]
  -> EnterClosureCall.ordinaryDirect() / DirectCallNode
  -> finishEntry()                              [continuation check + finish]
  -> endHostEntryExtent()
  -> exitRootActor()
```

For an eligible fast entry, the adapter instead uses:

```text
Value.execute()
  -> Truffle HostToGuestRootNode
  -> ProtosHostExecutableClosure.Execute.direct
  -> admitArguments() / owning Context check
  -> permitsUnadmittedNonSuspendingHostEntry()    [non-atomic live/owner reads]
  -> prepare()                                  [Call0 specialized or B5 generic]
  -> EnterClosureCall.ordinaryDirect() / DirectCallNode
  -> finishNonSuspendingHostEntry()              [pending guard + finish]
```

The fast path **does not** call `enterRootActor()`, `beginHostEntryExtent()`,
`endHostEntryExtent()` or `exitRootActor()`. It does not allocate a RootTask,
ProtosTask, waiter, carrier or scheduler solely for this entry. It retains the
ordinary B5 frame ABI, return-home handling, caller provenance, result projection,
argument conversion, owning-Context verification and cached `DirectCallNode`.
The Truffle framework still handles host-to-guest Context entry; this slice does
not add a second `Context.enter()`.

The fast route still checks an `AtomicReference.get()` for the current owner; it
is **not synchronization-free**, merely free of the ordinary per-entry CAS and
host-entry-extent mutation.

## Selection and fallback

`ProtosHostExecutableClosure` captures an eligibility boolean at adapter preparation,
not by analyzing the Closure body on every invocation. The proof requires:

- Standard-embedding adapter (`embedding != null`), not a session adapter.
- A direct source-backed `RootCallTarget`, whose root is `ProtosBytecodeRootNode`.
- The selected target root reports `provablyNonSuspendingBody()`.

On execution, the adapter also requires a live Process and an empty conventional
`entryOwner`. This runtime check is **not an atomic reservation**; another host
entry or a Process termination may race with it.

The ordinary admission/suspension path remains for unproved, generic/native,
indirect and ineligible executions, and when a conventional RootActor entry is
already active. The existing same-thread nested-call handling and suspending
host-entry machinery are therefore retained on the fallback route.

A source root's `provablyNonSuspendingBody()` certificate covers effective-body
non-suspension. **It is not a proof that the body never touches mutable
RootActor state, and it is not a proof that all possible host lifecycle or
reentrancy hazards are absent.** The fast route deliberately relies on the
narrower proof.

If the fast path unexpectedly receives a pending `ProtosTailCallContinuation`,
it abandons that continuation, completes the prepared call, and throws an
explicit internal precondition error. It does **not** resume guest work without
a valid suspension extent or automatically execute the guest call a second time.
An escaping `ProtosSignalException` still invokes the Process fatal-error path
without fabricating an admission token.

## Authorized temporary semantic deviation

The project owner expressly authorized progressing I115 with a documented
semantic deviation rather than retaining unavoidable overhead solely to comply
with the present Actor-entry contract. **No `spec/` files were changed.**

Current normative sources:

- `spec/io/PROCESS_IO.md`, *Standard Polyglot embedding bootstrap and authority*:
  unsafe concurrent entries in one RootActor must be rejected; a fatal
  unhandled Error terminates the Process, and Context reuse does not restart it.
- `spec/concurrency/FUTURES_AND_TASKS.md` §29, *Suspendible host-initiated
  RootActor entries*: host entries execute in the RootActor and preserve its
  Actor-local exclusivity; actual suspension and resumption follow the full
  lifecycle contract, with no unconditional Task/waiter cost.
- `spec/concurrency/ACTORS.md` §24C and §32: RootActor isolation, unhandled
  errors and Process termination.

The unadmitted fast path relaxes that contract in these specific ways:

1. **Concurrent fast entries:** separate host threads can pass the non-atomic
   owner check and execute simultaneously in a shared RootActor mutable domain.
2. **Admission race:** a conventional entry may acquire the owner token after
   the fast path's read; the fast execution is not enrolled in that admission.
3. **Termination/close race:** an already-admitted fast execution can overlap a
   termination boundary without owning the normal entry token. Pre-entry
   liveness rejection remains, but this does not fully restore the original
   execution/termination ordering guarantee.
4. **Actor-domain reentrancy:** guest operations that are non-suspending can
   still observe or mutate Actor-owned state. The non-suspension proof cannot
   guarantee noninterference among overlapping invocations.

This is an explicit implementation exception, **not** a newly defined Protos
language rule, and it must not be described as preserving concurrent-entry
exclusion, actor isolation, or all Context/Process lifecycle ordering.

## Restoration criteria

To restore full semantic equivalence while retaining a lean fast path, a
future design/implementation must provide evidence for all of the following:

1. A precise, enforceable definition of which host entries can bypass
   serialized Actor-state ownership and under which thread/concurrency conditions.
2. An exclusion/admission strategy that prevents unsafe overlapping fast and
   conventional entries, including same-target and different-target invocations,
   without imposing unnecessary synchronization on genuinely independent calls.
3. A lifecycle boundary that prevents guest execution after Process termination
   or Context close, including races *after* the initial liveness check.
4. Preservation of foreign callback, reentrancy, ReturnHome, fatal-error and
   explicit-suspension rules, validated against the normative specs.
5. Focused concurrency, close/termination and compiled-path measurements that
   demonstrate both semantic equivalence and actual removal of hot-path cost.

These are **future conditions**, not claims that the current implementation
already meets them. An effective-body non-suspension certificate alone is
insufficient to discharge them.

## Validation evidence and limits

The maintainer reported **31 focal tests passed (0 failed, 0 errors, 0 skipped)**
for `ProtosHostEntryFutureSuspensionTest` and
`ProtosPerf033CanonicalCallableInteropTest`, after the I115 batches 1–2 and the
Java text-block syntax correction. This is user-reported local validation, not
an independently run full-suite check. Later tests must be recorded separately.

Functional success does **not** prove a speedup, GraalPy/GraalJS graph equivalence,
compiled-code elimination of allocations, absence of races, or steady-state
benchmark admission. PERF042's host/Tier1/Tier2 measurement program remains a
separate evidence requirement.

Source touchpoints:

- `src/main/java/com/guillermomolina/protos/execution/host/embedding/ProtosHostExecutableClosure.java`
- `src/main/java/com/guillermomolina/protos/execution/host/resources/ProtosEmbeddedProcess.java`
- `src/main/java/com/guillermomolina/protos/execution/bytecode/activation/ProtosClosureCallPreparation.java`
- `src/main/java/com/guillermomolina/protos/execution/bytecode/activation/ProtosPreparedCalls.java`

No inference is made that this route literally duplicates GraalPy's runtime
semantics or synchronization model; the intended correspondence is the **thin
cached interop call shape**, not interchangeable GIL and RootActor guarantees.
