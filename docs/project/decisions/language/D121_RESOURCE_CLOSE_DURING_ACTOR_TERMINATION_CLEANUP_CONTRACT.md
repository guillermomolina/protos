# D121 — Resource close initiated during Actor termination cleanup

Status: **RATIFIED — Candidate A′ selected**

Specification revision: **`0.1.412`**  
Explicit project-owner approval: **2026-09-13**  
Owning work item: `PERF006-B` / GitHub Issue `#276`  
Decision issue: GitHub Issue `#456`

Nature: non-normative decision/rationale record for the normative Actor,
execution/control and I/O lifecycle semantics in `spec/`.

## Decision

Select **Candidate A′ — a first lifecycle close after Actor termination cutover is
admissible only when invoked from the exact dynamic execution extent already
authorized as termination cleanup**.

The ratified rule is:

> Actor termination does not grant a generic `TERMINATING` execution privilege.
> A lifecycle `close()` whose first commitment occurs after termination cutover may
> proceed only when the invocation belongs to guest execution already authorized
> as termination cleanup. The authorization follows synchronous/nested calls and
> suspension/resumption of that exact cleanup continuation. It does not transfer
> merely because the cleanup creates another Task, Future producer, mailbox turn,
> detached job or unrelated callback. A successfully admitted close is an
> ordinary lifecycle close. If its required release needs Actor-local guest
> execution, that release becomes another termination-cleanup obligation under
> PLAT030 in the same `ActorExecutionDomain`, and the Actor remains `TERMINATING`
> until the guest obligation settles. `TERMINATED` remains a hard guest-execution
> boundary.

## Ratified boundaries

1. **Origin, not Actor state alone, grants the exception.** `Actor ==
   TERMINATING` is insufficient to admit a new first-close. The invoking
   execution must itself be the continuation of an already-authorized
   termination-cleanup extent.

2. **Existing Task cancellation/unwind cleanup is authorized.** Standard `ensure`
   cleanup that is already executing as part of Actor-local cancellation/unwind
   may invoke a first `close()` after termination cutover. This follows the
   existing control rule that the delivered cancellation is shielded while that
   cleanup executes and that cleanup may perform ordinary asynchronous release.

3. **Existing PLAT030 release cleanup is authorized.** A lifecycle-release-owned
   C′ segment already executing as termination cleanup may synchronously invoke
   another resource's first `close()`. The nested lifecycle is independently
   owned; authority does not merge lifecycle identities.

4. **Authority follows the exact dynamic cleanup continuation.** It propagates
   through ordinary nested calls, Closure activations and suspension/resumption
   that remain part of that exact cleanup execution.

5. **Authority does not leak into newly created structured or unstructured
   work.** A Task/Future producer, mailbox turn, detached job or unrelated
   callback created while cleanup runs does not inherit D121 authority merely
   because its creation happened in cleanup.

6. **Admitted close commits normally.** D121 does not add a special
   "termination-close" lifecycle or outcome. Once admitted, close uses the
   ordinary lifecycle commitment/follower/error rules.

7. **Guest release extends termination cleanup.** If the newly admitted close
   needs guest release, PLAT030 owns one lifecycle-release C′ record in the same
   Actor domain. That guest obligation keeps the Actor in `TERMINATING` until it
   settles.

8. **Backend-only aftermath does not keep the Actor alive.** Once no guest
   release remains, backend-only residual producer work does not by itself block
   `TERMINATED`, subject to the existing no-guest-reentry-after-termination rule.

9. **Ordinary post-cutover close remains inadmissible.** Code that is not
   executing under termination-cleanup authority cannot use D121 merely because
   the Actor has not yet reached `TERMINATED`.

10. **No implicit resource sweep.** Actor termination still does not discover,
    enumerate or close resources automatically. D121 authorizes only explicit
    `close()` invocations made by already-authorized cleanup.

11. **No hidden Task or domain migration.** D121 does not create a Task,
    system Actor, neutral execution domain, detached release worker or
    post-termination callback lane.

12. **No guest execution after `TERMINATED`.** A nested release that still
    requires guest execution must settle before terminal Actor state is
    published.

13. **No timeout/hard-kill semantics.** A potentially unbounded cleanup graph is
    an operational concern, not permission to weaken graceful cleanup. Any
    timeout, escalation or hard-stop policy requires another explicit decision.

14. **D112 remains authoritative for pre-cutover commitment.** D112 governs
    closes committed before termination cutover. D121 governs only a first close
    whose commitment occurs after cutover from an already-authorized cleanup
    extent.

15. **Existing failure precedence remains authoritative.** D121 changes neither
    `ensure` transfer precedence nor lifecycle/close Error identity and follower
    semantics. A newly exposed unresolved precedence case must stop
    implementation and open another Dxxx.

## Canonical relationship

```text
Actor termination cutover
        |
        +-- ordinary/new execution extent
        |       |
        |       +-- first resource.close()
        |               -> NOT admitted by D121
        |
        +-- exact termination-cleanup extent
                |
                +-- synchronous/nested call
                |       -> cleanup authority preserved
                |
                +-- suspension/resume of same cleanup continuation
                |       -> cleanup authority preserved
                |
                +-- first resource.close()
                |       -> MAY commit normally
                |       -> ordinary lifecycle identity/followers
                |       -> PLAT030 release C′ if guest release required
                |       -> same ActorExecutionDomain
                |       -> guest release becomes termination cleanup
                |
                +-- create new Task/Future producer/mailbox turn
                        -> D121 authority NOT inherited merely by creation

Actor reaches TERMINATED only when required Actor-local cleanup no longer
requires guest execution.

TERMINATED
    -> no guest release execution
```

## Relationship to existing Protos semantics

### `ensure`

The execution/control specification already says that once a cancellation request
has been delivered and cancellation unwind begins, that same delivered request is
not re-observed at suspension points reached while standard `ensure` cleanup is
running. Cleanup may perform ordinary asynchronous operations and suspend while
releasing resources.

D121 makes the lifecycle consequence explicit: an explicit `close()` invoked by
that exact cleanup is one of those permitted asynchronous release operations even
when the close commitment occurs after Actor termination cutover.

D121 does not turn `ensure` into transferable authority. Existing dynamic-control
semantics remain authoritative: creating a distinct asynchronous child does not
copy the parent's `ensure` frame merely because creation occurred inside the
cleanup body.

### D112

D112 protects an already-committed lifecycle across a later termination cutover.
D121 is complementary rather than a revision:

```text
D112:
    close commitment < termination cutover
    -> required guest release is termination cleanup

D121:
    termination cutover < first close commitment
    -> close may commit only if invocation already belongs to termination cleanup
```

Both preserve same-domain guest execution and the hard `TERMINATED` boundary.

### PLAT030

PLAT030 remains the representation/scheduling authority for lifecycle release.
D121 supplies the admission provenance needed when the close itself begins after
cutover.

No release Future becomes owner. No Task is synthesized. The lifecycle retains
one logical close and its followers; its private release C′ record retains only
release execution state.

## Comparative evidence

The decision followed comparison with structured cancellation, actor shutdown,
scope cleanup and host-runtime finalization models.

### Swift concurrency

Swift async `defer` and cancellation shields permit asynchronous cleanup after
cancellation has begun while keeping the privilege scoped to the cleanup
continuation rather than requiring an unstructured detached Task.

### Kotlin coroutines

Suspending cleanup in `finally` is performed with `withContext(NonCancellable)`.
Kotlin explicitly warns that launching independent work with
`launch(NonCancellable)` breaks structured parent/child lifetime. This strongly
supports scoped cleanup authority and rejects detached/system release.

### Trio / AnyIO

Cancellation scopes normally cancel later suspension points, while a nested
shielded scope is the standard way to perform asynchronous shutdown and resource
release after cancellation. The shield is scoped; cancellation does not grant a
generic global privilege.

### Cats Effect

Cancellation runs registered finalizers and waits for uncancelable release
actions. Nested resources compose by scoped finalization rather than by treating
all post-cancellation work as ordinary.

### Haskell

`bracket`/masking guarantees release during unwind and scopes asynchronous
exception masking around cleanup. The privilege belongs to the cleanup region.

### C# / .NET

`await using` performs awaited `DisposeAsync()` from scope-exit/finally
semantics. Resource disposal therefore commonly begins only after the protected
body has already started unwinding.

### Java

`try`-with-resources initiates `close()` during abrupt scope exit. Structured
concurrency similarly distinguishes cancellation request from completed scope
cleanup.

### Rust / Tokio

Synchronous `Drop` is initiated by scope unwind; experimental `AsyncDrop` extends
that concept to asynchronous teardown. Tokio cancellation-safety guidance
reinforces that effectful cleanup cannot be treated as freely truncatable after
partial progress.

### Erlang / OTP

Graceful process termination runs `terminate/2` cleanup before process death.
Hard `brutal_kill`/timeout escalation is a separate stronger contract.

### Orleans

Asynchronous deactivation work runs during the activation's deactivation
lifecycle and completion of deactivation follows that work, supplying a direct
Actor-family precedent.

### Akka Typed

`PostStop`/`PreRestart` are cleanup hooks used to close resources before external
termination observation, although Akka's callback model is less directly
comparable to Protos suspension-capable cleanup.

### Python asyncio

`try/finally`, async context managers, `aclosing` and `AsyncExitStack` perform
and await resource cleanup as the owning Task exits; shielding exists for cleanup
that must survive caller cancellation.

### Go

`defer` is the ordinary mechanism for release during return/unwind while context
cancellation remains cooperative.

### Node.js streams

Graceful stream finalization performs final resource/output work before `finish`,
while destructive `destroy()` is a separately stronger operation.

### Truffle / GraalVM

Normal context exit allows guest exit/finalization work before disposal. Hard
cancellation/exit is explicitly distinct and may skip ordinary guest
finalization. This closely matches Protos' graceful-cleanup versus possible
future hard-stop distinction.

## Candidate comparison

| Candidate | Aguante de futuro | Escalabilidad | Filosofía Protos | Correctness / composability | Result |
|---|---:|---:|---:|---:|---|
| **A′ — only cleanup-authorized nested close** | **10.0/10** | **9.5/10** | **10.0/10** | **10.0/10** | **selected** |
| B — reject every post-cutover close | 4.0/10 | 10.0/10 | 3.0/10 | 4.0/10 | rejected |
| C — any `TERMINATING` execution may close | 6.0/10 | 7.0/10 | 4.0/10 | 5.0/10 | rejected |
| D — migrate nested release to detached/system execution | 3.0/10 | 7.5/10 | 1.0/10 | 3.0/10 | rejected |
| E — execute required guest release after `TERMINATED` | 2.0/10 | 8.0/10 | 1.0/10 | 2.0/10 | rejected |
| F — implicit termination resource sweep | 2.5/10 | 4.0/10 | 2.0/10 | 3.0/10 | rejected |

## Strongest argument against A′

Termination cleanup can grow dynamically. One cleanup may close a resource whose
guest release explicitly closes another resource, producing a chain of required
guest obligations and potentially keeping an Actor `TERMINATING` indefinitely.

This is a real operational cost and the reason scalability is 9.5 rather than
10.0. The future-compatible remedy is observability and, if needed, an explicit
hard-stop/timeout/escalation contract. Ordinary graceful cleanup should not
silently become unreliable to bound that duration.

## Future-regret analysis

A′ leaves the strongest escape path: a later Process/Actor policy can introduce
an explicit hard-stop operation without weakening ordinary cleanup.

Rejecting post-cutover cleanup close now would be significantly harder to reverse
because code would learn that `ensure(() => resource.close())` is unreliable
specifically during Actor termination, despite existing Protos semantics allowing
asynchronous operations during that cleanup.

## Implementation consequence

D121 releases the remaining PLAT030 implementation gate under PERF006-B:

- internal representation of termination-cleanup authority on the exact dynamic
  cleanup flow/release record;
- post-cutover first-close admission only from that authority;
- Actor-domain accounting for guest-requiring lifecycle release;
- same-domain lifecycle-release C′ scheduling/resume;
- no authority inheritance by newly created Task/Future work; and
- no post-`TERMINATED` guest execution.

D121 itself changes no Java implementation and executes no Protos tests.
