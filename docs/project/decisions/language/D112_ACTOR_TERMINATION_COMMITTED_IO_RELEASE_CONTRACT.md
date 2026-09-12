# D112 — Actor termination versus committed I/O lifecycle release requiring guest re-entry

Status: **RATIFIED — Candidate A′ selected**

Specification revision: **`0.1.411`**  
Explicit project-owner approval: **2026-09-12**  
Owning work item: `PERF006-B` / GitHub Issue `#276`  
Decision issue: GitHub Issue `#438`

Nature: non-normative decision/rationale record for the normative Actor/I/O
lifecycle semantics in `spec/concurrency/ACTORS.md` and `spec/io/IO_CORE.md`.

## Decision

Select **Candidate A′ — a pre-cutover committed lifecycle release that still
requires ordinary guest execution is an Actor termination-cleanup obligation**.

The tightened ratified rule is:

> A lifecycle `close()` committed before the Actor termination cutover becomes an
> Actor termination cleanup obligation only while its remaining release requires
> ordinary guest execution. The release is not a Task and not a new ordinary
> Actor turn. PLAT030's lifecycle-release-owned C′ remains owned by the same
> `ActorExecutionDomain`. The Actor remains `TERMINATING` while those guest
> release segments suspend/resume and reaches `TERMINATED` only after all such
> pre-cutover committed guest-release obligations, together with the already
> required Task cancellation/unwind cleanup, have settled. Backend-only residual
> work that needs no guest execution does not by itself keep the Actor alive.

## Ratified boundaries

1. **Commitment precedes termination arbitration.** An asynchronous lifecycle
   `close()` that crossed its existing irrevocable close commitment boundary
   before Actor termination cutover remains one committed lifecycle operation.
   The later termination cancellation request cannot rewrite it into a cancelled
   or zero-effect outcome.

2. **Guest release is termination cleanup, not ordinary post-cutover work.**
   Remaining release callbacks needed to honor that already-committed lifecycle
   are classified as cleanup of the existing obligation. This does not admit a
   new user operation, mailbox turn, message, Task or lifecycle operation.

3. **Same Actor domain.** Guest release execution remains in the originating
   Actor's `ActorExecutionDomain` under PLAT030 lifecycle-release-owned C′.
   D112 does not transfer execution to a system Actor, neutral domain,
   replacement Actor or detached worker semantic domain.

4. **`TERMINATING` remains executable only for required cleanup.** A release C′
   segment may suspend and later resume while the Actor is `TERMINATING` when it
   is required to finish an already-committed lifecycle. Ordinary post-cutover
   Actor work remains inadmissible.

5. **`TERMINATED` remains a hard guest-execution boundary.** No ordinary Protos
   guest callback belonging to the terminated Actor may resume after the Actor
   reaches `TERMINATED`.

6. **Termination waits only while guest execution is semantically required.**
   Once an already-committed lifecycle has no remaining guest release work,
   backend-only residual producer work may continue under runtime/producer
   custody without keeping the Actor in `TERMINATING`, subject to the existing
   rule that it cannot re-enter ordinary Protos code in the terminated Actor.

7. **No implicit resource close.** Actor termination still does not implicitly
   call `close()`, `flush()`, `sync()` or shutdown on arbitrary resources. D112
   applies only to a lifecycle release whose close commitment already existed
   before the termination cutover.

8. **No hidden Task.** Lifecycle release retains PLAT030's one private
   release-owned C′ execution record. D112 does not manufacture Task identity,
   parentage, `detach()`, Task cancellation state or Task-owned Future semantics.

9. **No force-kill or timeout semantics.** D112 adds no timeout, watchdog,
   escalation or hard-stop operation. A future explicit hard-kill policy, if
   desired, requires a separate normative decision.

10. **Existing failure precedence remains authoritative.** D112 does not redefine
    Error identity, primary-failure precedence, `ensure` supersession, close
    follower identity, ordinary I/O commitment or D117 delegated-effect
    arbitration. If implementing release C′ exposes a genuinely unresolved
    observable precedence case, implementation must stop and open a new Dxxx.

11. **Fatal and graceful Actor termination share the same committed-cleanup
    obligation unless another existing rule explicitly gives stronger
    authority.** D112 does not create a special path that truncates already
    committed guest release merely because termination was triggered by an
    unhandled Actor Error rather than `ActorRef.stop()`.

12. **Replacement does not inherit release execution.** Because the original
    incarnation cannot reach `TERMINATED` while its required guest-release
    obligation remains, no replacement Actor inherits or resumes that
    continuation, mutable state, capability authority or lifecycle record.

## Canonical state relationship

```text
close() committed before Actor termination cutover
        |
        v
remaining lifecycle release
        |
        +-- requires guest execution
        |      -> same ActorExecutionDomain
        |      -> PLAT030 lifecycle-release-owned C′
        |      -> Actor remains TERMINATING
        |      -> release may suspend/resume
        |      -> no new ordinary Actor work admitted
        |      -> settle required guest release
        |
        +-- backend-only residual work
               -> runtime/producer custody
               -> does not by itself keep Actor alive
               -> cannot later re-enter guest code after TERMINATED

TERMINATED is reached only after:
    existing required Task cancellation/unwind cleanup
    +
    all pre-cutover committed guest-release obligations that still require
    Actor-local guest execution
have settled.
```

## Relationship to existing Protos semantics

### Actor termination

`ACTORS.md` already makes the termination cutover irreversible, rejects new
ordinary Actor work after that cutover and delays `TERMINATED` until Actor-local
Task cleanup that remains semantically required has completed.

D112 extends that same semantic principle to a distinct non-Task cleanup owner:
a lifecycle `close()` already committed before the cutover whose remaining
release still requires Actor-local guest execution.

The extension is deliberately narrow. It does not make every pending producer
Future a termination obligation.

### I/O commitment and close

`IO_CORE.md` already establishes that:

- Actor termination requests cancellation with the same strength as ordinary
  operation cancellation;
- already-committed effects cannot be rewritten as pre-commit cancellation;
- Actor termination is not implicit `close()`/`flush()`/shutdown;
- backend residual work may survive Actor termination but cannot re-enter guest
  code in an already terminated Actor; and
- `close()` is one committed lifecycle whose required release aftermath must be
  completed according to the applicable wrapper/resource contract.

D112 resolves the apparent conflict when that required close aftermath itself
needs guest execution: the Actor does not become `TERMINATED` yet.

### PLAT030

PLAT030 remains the runtime architecture authority:

```text
one lifecycle release
    -> one private lifecycle-release-owned C′ execution record
    -> same ActorExecutionDomain
    -> close Futures remain outcome followers
```

D112 supplies the missing observable relationship between that release record
and Actor termination. It does not change PLAT030 representation.

### PLAT029 / PLAT031 / D117

D112 does not reopen:

- PLAT029 ordinary non-Task operation-owned C′ custody;
- PLAT031 one lifecycle / one operation authority for buffered wrappers; or
- D117 three-way delegated first-effect evidence.

Those decisions continue to own ordinary operation execution and commitment.
D112 owns only the already-committed lifecycle-release versus Actor-termination
relationship.

## Comparative evidence

The decision followed an exhaustive comparison of materially relevant
termination, structured-concurrency, async-cleanup and Actor/runtime models.

### Erlang / OTP

OTP distinguishes graceful termination from hard termination. Normal
`gen_server`/supervisor shutdown allows termination cleanup to run before process
death, while `brutal_kill` and timeout escalation are explicitly stronger
policies. This strongly supports keeping a must-run committed cleanup obligation
inside normal termination while leaving a future hard-kill contract separate.

### Kotlin coroutines

Structured coroutine cancellation normally preserves parent/child completion
relationships. Suspending cleanup can be protected with a non-cancellable
region; launching detached non-cancellable cleanup is discouraged because it
severs the relationship and lets the parent finish without waiting.

This is strong evidence for A′ and directly against migrating release guest code
to an unrelated execution owner.

### Swift concurrency

Swift 6.4's async `defer` makes asynchronous cleanup part of scope exit and waits
for it before the scope completes. Cancellation shields provide a mechanism for
must-finish cleanup. Actor isolation additionally argues against moving
actor-local cleanup to detached or nonisolated execution merely to allow the
origin Actor to appear terminated earlier.

### Rust / Tokio

Rust async cancellation makes cancellation safety an explicit concern.
Operations such as `write_all` may have irreversible partial progress, and
graceful shutdown patterns signal cancellation then wait for tracked work to
finish. Experimental async destruction further points toward structured
asynchronous cleanup rather than pretending destruction completed before its
async obligations.

### Java structured concurrency / CompletionStage

Structured scopes cancel unfinished children on close but still join them before
the owning scope disappears. `CompletableFuture` cancellation, by contrast, is
primarily an outcome-level state transition and is not producer-side execution
authority.

### .NET / C# and Orleans

`IAsyncDisposable.DisposeAsync()` and `await using` make asynchronous resource
cleanup part of scope exit. Orleans is an especially relevant Actor precedent:
grain deactivation is itself asynchronous, deactivation callbacks run as part of
the deactivation process, and completion of the deactivation operation follows
completion of that lifecycle.

### Akka

Akka actor post-stop processing occurs before external observation of
termination. Coordinated shutdown similarly sequences asynchronous shutdown work
by phase rather than declaring later phases complete while earlier required work
is still pending.

### Python asyncio

Cancellation propagates cooperatively through exception/unwind mechanics;
`finally` cleanup runs before cancellation finishes propagating, and structured
task groups wait for children to settle before scope exit.

### Go

`context` cancellation is cooperative rather than a forcible rollback
mechanism. Components remain responsible for finishing their own shutdown and
resource cleanup.

### Node.js streams

Writable `_final(callback)` participates in stream finalization and delays
completion until required buffered/final output work has completed.

### Truffle / GraalVM

Truffle provides the strongest host-runtime distinction. Normal context
finalization occurs before disposal and may execute guest-language cleanup.
Truffle separately exposes force-cancellation/hard-exit paths whose contract can
skip ordinary guest `finally` behavior.

That separation is directly relevant to Protos: ordinary Actor stop/fatal
cleanup should preserve already-required guest release, while any future hard
kill should be a separately specified stronger operation rather than silently
weakening normal termination.

### GraalJS, GraalPy, Espresso, Pkl, TruffleSqueak, SOMns

No language-specific lifecycle rule among these implementations provides a
stronger directly comparable Actor/resource contract than the underlying
Truffle context lifecycle distinction. None supplies evidence for transferring
actor-local guest cleanup into another semantic domain or for permitting guest
execution after observable termination.

## Candidate comparison

| Candidate | Future endurance | Scalability | Protos philosophy | Result |
|---|---:|---:|---:|---|
| **A′ — committed release is termination cleanup** | **10.0/10** | **9.5/10** | **10.0/10** | **selected** |
| B — termination wins and truncates remaining guest release | 4.5/10 | 10.0/10 | 4.0/10 | rejected |
| C — migrate release guest execution to system/neutral Actor | 3.0/10 | 5.0/10 | 1.0/10 | rejected |
| D — privileged guest cleanup after observable `TERMINATED` | 3.5/10 | 6.0/10 | 2.0/10 | rejected |
| E — restrict owning wrappers to host/native close only | 2.0/10 | 8.5/10 | 1.0/10 | rejected |

## Strongest argument against A′

A buggy or malicious guest release can keep an Actor in `TERMINATING`
indefinitely.

That is a real operational cost and the reason scalability is 9.5 rather than
10.0. D112 intentionally does not solve it by falsifying lifecycle completion.

The future-compatible remedy is observability and, if the language later needs
it, an explicit separately specified hard-stop/kill or Process escalation policy.

## Regret scenario and escape path

A′ has the strongest reversible escape path.

If indefinite termination later proves unacceptable, Protos can add a deliberate
hard-stop policy with stronger semantics. Existing ordinary `stop()` remains the
safe graceful contract.

Choosing B now would be much harder to reverse: programs could come to depend on
Actor termination retroactively truncating or changing the observable result of
an already-committed close, making later strengthening a breaking semantic
change.

## Implementation consequence

D112 releases the previously blocked PLAT030 lifecycle-release C′ work under
PERF006-B:

- `TextWriter.close` lifecycle release C′;
- buffered reader/writer close/release C′ where applicable; and
- equivalent standard lifecycle release paths that were blocked only by D112.

Implementation remains subject to PLAT030 ownership and the existing strict
decision rule. Any newly exposed semantic ambiguity must stop and open a new
Dxxx rather than being chosen inside the implementation patch.

D112 itself changes no Java implementation and executes no Protos tests.
