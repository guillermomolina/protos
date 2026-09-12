# PLAT031 — Buffered I/O operation identity and C-prime ownership integration

Status: **RATIFIED — Candidate A′ selected**

Nature: durable non-normative JVM/Truffle/runtime architecture decision

Approved by project owner: **2026-09-12**

GitHub Issue: **#442**

Primary consumer: `PERF006-B` / GitHub #276, specifically standard buffered byte I/O migration to PLAT029 operation-owned C-prime.

Normative effect: **none**. PLAT031 changes no Protos-visible byte I/O, Future, cancellation, commitment, ordering, buffering, close, Error, Actor or Task semantics. It selects the canonical internal operation/lifecycle identity used by buffered adapters.

## Decision

Select **Candidate A′ — migrate standard buffered I/O to the existing common `ProtosIoLifecycle` + real `ProtosIoOperation` substrate; retain buffered request records only as adapter-local queue/order/payload metadata**.

Each accepted buffered operation has exactly one semantic operation identity:

```text
BufferedReader.read / BufferedWriter.write / BufferedWriter.flush
        |
        v
one ProtosIoOperation
        |
        +-- result Future
        |      outcome / observation only
        |
        +-- commitment / cancellation / terminality
        |
        +-- deferred C-prime custody when required
        |
        v
one ProtosIoLifecycle per buffered wrapper
```

The adapter may retain a private request record such as `Req`, but that record is not a second asynchronous-operation authority.

## Canonical authority split

### `ProtosIoLifecycle`

Exactly one lifecycle instance is authoritative for each standard buffered wrapper.

It owns:

- OPEN / CLOSING / terminal close state;
- ordinary-operation admission;
- close cutover;
- the set of admitted `ProtosIoOperation`s;
- repeated close followers;
- the handoff to lifecycle release.

PLAT030 remains authoritative for the later release-execution phase.

### `ProtosIoOperation`

Every accepted buffered `read`, `write` and ordinary `flush` owns exactly one real `ProtosIoOperation`.

It owns:

- the operation result Future;
- semantic commitment;
- producer-side cancellation state;
- operation terminality;
- close-cutover interaction;
- deferred PLAT029 C-prime execution custody when ordinary guest callbacks must later resume.

The result Future remains outcome/observation only.

### Buffered request metadata

A private adapter request record may retain only state whose meaning is intrinsically buffered-adapter-local, including:

- FIFO queue linkage/order;
- operation kind;
- requested maximum/read bound;
- immutable write payload snapshot;
- currently issued lower Future/dependency reference where needed as leaf bookkeeping;
- adapter-local phase metadata that does not duplicate semantic commitment/cancellation/lifecycle ownership.

It must not independently own:

- a second result Future identity;
- semantic commitment;
- producer cancellation authority;
- close cutover;
- terminal operation state;
- deferred C-prime execution identity;
- Actor scheduling identity.

If implementation needs any of those duplicated responsibilities, it must stop and reopen platform architecture.

## Buffered operations

### `BufferedReader.read`

`BufferedReader.read` is one ordinary asynchronous operation.

When buffered input can satisfy the request locally, Java/native leaf mechanics may complete the same `ProtosIoOperation` without creating C-prime state.

When the adapter must invoke ordinary guest `target.read(...)`, PLAT028/029 apply:

```text
one ProtosIoOperation
    -> ordinary guest target.read(...)
    -> guest may suspend in C-prime
    -> await returned Future
    -> apply inert lower outcome to adapter buffer
    -> terminalize same ProtosIoOperation
```

No shadow operation or hidden Task is introduced.

### `BufferedWriter.write`

`BufferedWriter.write` also receives one real `ProtosIoOperation` even though its accepted body is normally adapter-local byte accumulation and does not require guest C-prime callbacks.

This uniformity is intentional:

- lifecycle admission/cutover is common;
- cancellation/commitment semantics have one authority;
- later buffered implementation evolution cannot accidentally create a special second operation framework for writes.

No C-prime continuation need be allocated unless guest callback control is actually required.

### `BufferedWriter.flush`

One `BufferedWriter.flush` operation owns the complete ordinary flush control extent.

If pending adapter bytes exist:

```text
target.write(pending)
    -> await lower Future
    -> update output frontier
    -> optional target.flush()
    -> await lower Future
    -> settle same outer operation
```

If no bytes are pending but a lower flush capability exists, the same operation invokes/awaits that callback.

All arbitrary guest lower-protocol callback sequencing belongs to operation-owned C-prime under PLAT028/029.

## Adapter-owned state remains adapter-owned

PLAT031 does not move ordinary buffering semantics into `ProtosIoOperation`.

The buffered adapter remains the authority for:

- input read-ahead bytes;
- output byte buffer;
- maximum-buffer bounds;
- output frontier/removal after successful lower write;
- permanent output Error/poison state;
- FIFO request ordering;
- lower-protocol capability decisions;
- any existing exact byte snapshot/framing rules.

The operation substrate owns execution/lifecycle state; the adapter owns buffering/data-plane state.

## Close and release boundary

Adopting `ProtosIoLifecycle` is an identity/lifecycle convergence decision, not authorization to decide D112 implicitly.

Buffered close must use the common lifecycle admission/cutover/follower authority, but the suspendible release implementation remains separately gated by PLAT030 and D112.

Therefore the ordinary buffered-operation migration may land while:

```text
BufferedReader.close release execution = BLOCKED_BY_D112
BufferedWriter.close release execution = BLOCKED_BY_D112
```

The migration must preserve the currently specified close-cutover outcomes for ordinary operations.

If converting old private close bookkeeping to `ProtosIoLifecycle` exposes an observable discrepancy that is not mechanically resolved by existing I/O semantics, implementation must stop and open Dxxx.

## Relationship to PLAT029

PLAT029 remains fully authoritative:

```text
deferred non-Task async operation
    -> operation owns C-prime
ActorExecutionDomain
    -> schedules/resumes operation
result Future
    -> outcome/observation only
```

PLAT031 identifies the buffered operation as the same kind of real operation, rather than creating a second buffered-only operation species.

The existing Java class name `ProtosIoOperation` is an implementation detail, but one semantic operation authority is durable.

## Relationship to PLAT030

PLAT030 remains authoritative for asynchronous lifecycle release/close after ordinary operations have drained.

PLAT031 must not turn release into an ordinary `ProtosIoOperation`, and it must not make a buffered request record own release execution.

Conceptually:

```text
ordinary buffered read/write/flush
    -> PLAT031 / PLAT029 real operation

after close cutover and ordinary-operation drain
    -> PLAT030 lifecycle-release-owned execution
```

## Scheduling and re-entry

`ActorExecutionDomain` remains the sole scheduler/re-entry authority for operation-owned C-prime.

Backend/lower-Future completion may:

- publish readiness;
- enqueue/wake the exact owning Actor domain;
- preserve leaf aftermath required by existing semantics.

It must not directly execute arbitrary guest callbacks.

PLAT031 introduces:

- no second scheduler;
- no buffered microtask queue;
- no hidden Task;
- no physical carrier per operation;
- no replay mechanism.

## Cancellation and commitment

There must be exactly one semantic commitment/cancellation authority for one buffered operation.

The old buffered `Req.committed` / `cancelRequested` design may be used only as migration evidence; after convergence it must not remain an independent semantic authority competing with `ProtosIoOperation`.

Existing I/O authority remains normative for:

- pre-commit cancellation;
- first-effect/commitment boundaries where applicable;
- post-commit cancellation behavior;
- close cutover;
- exact downstream failure identity;
- permanent writer poisoning.

If the old implementation and common-operation model disagree observably on one of these, PLAT031 does not choose a new semantic answer. The slice stops and opens Dxxx.

## Why Candidate B is rejected

Candidate B keeps the private buffered request as a second schedulable operation species.

That would create parallel implementations for:

- commitment;
- cancellation;
- terminality;
- scheduler integration;
- C-prime custody;
- close interaction.

The duplication would be especially costly as TextReader/TextWriter and buffered I/O evolve together.

B is implementable but ages poorly and conflicts with the PLAT029 goal of one operation-owned model.

## Why Candidate C is rejected

Candidate C overlays a `ProtosIoOperation` only for C-prime custody while retaining an independent buffered operation/lifecycle authority.

That creates two objects with overlapping claims over one logical operation.

Typical failure modes would include:

- one object says committed while the other says uncommitted;
- cancellation races have two authorities;
- close cutover terminalizes one layer but not the other;
- C-prime terminality and buffered queue terminality drift.

A shadow operation is therefore explicitly prohibited.

## Why Candidate D is not selected now

A new common private operation/job abstraction could eventually be valid, and evidence from GraalJS/GraalPy shows that carefully factored shared async-action machinery can scale.

But Protos already has the correct semantic operation abstraction for this problem.

Introducing D now would:

- broaden the migration unnecessarily;
- risk creating a generic second Task/job layer;
- duplicate or weaken already-ratified PLAT029 boundaries.

If later `ProtosIoOperation`, PLAT030 release execution, and additional unrelated subsystems demonstrate a genuinely identical minimal execution-custody interface, a future PLAT decision may factor the mechanics without changing semantic ownership.

## Comparative evidence

The owner decision followed an exhaustive review of the current Truffle language catalogue plus relevant historical/experimental implementations.

### Apple Pkl

Pkl provides the strongest lifecycle precedent.

Its external-reader infrastructure keeps lifecycle/process/transport ownership in one coherent runtime object while request-specific Futures remain result handles. The important lesson is not Pkl's subprocess implementation but the absence of competing lifecycle authorities for one resource/request system.

PLAT031 adopts that separation:

```text
resource lifecycle authority
    != request Future
one request identity
    != shadow request identity
```

Focused precedent scores: future endurance **5/5**, scalability **4.7/5**, Protos fit **5/5**.

### GraalJS

`JSAgent` centralizes Promise jobs, async-wait work and finalization scheduling in one execution domain.

Different semantic records may exist, but subsystems do not each manufacture independent scheduler/lifecycle frameworks merely to run deferred guest work.

Useful lesson: central execution-domain scheduling composes better than parallel per-wrapper schedulers.

### GraalPy

`AsyncHandler` and its `AsyncAction` family provide shared async re-entry machinery.

This is evidence that low-level mechanics can be factored later if repeated use proves the abstraction, but it does not justify adding a second operation identity where Protos already has one.

### SOMns

Promises have an owner Actor and callbacks are scheduled on that owner.

The useful PLAT031 lesson is one semantic owner plus owner-domain scheduling, rather than creating a hidden second work identity just to schedule callbacks.

### Yona

Yona's async runtime integrates multiple kinds of asynchronous work through one runtime substrate. The exact APIs differ from Protos, but it is positive evidence against one-off wrapper-specific operation frameworks.

### PorcE / Orc

Future readers and task scheduling use one common runtime model. This is useful evidence for explicit pending-work representation without split authority for one logical operation.

### grCUDA

Logical computation/dependency records are separated from physical execution carriers and scheduling is centralized. This is secondary but consistent evidence.

### TruffleSqueak

Explicit Process/context scheduling supplies weaker supporting evidence for one coherent execution model rather than subsystem-specific host-stack identities.

### Espresso

Espresso is negative evidence for physical/host-stack approaches. Its coherent continuation model does not support creating parallel continuation frameworks per subsystem.

### TruffleRuby

Thread-backed Fibers are negative evidence against multiplying physical carriers for pending operations.

### Additional screened implementations

Enso, FastR, GraalWasm, SimpleLanguage, Sulong, TRegex, TruffleSOM and the remaining current/historical catalogue were screened. They expose no stronger directly comparable buffered async-operation/lifecycle precedent than Pkl/GraalJS/GraalPy/SOMns/Yona.

## Candidate comparison

Scores are 1–5 across ten decision axes.

| Candidate | Correctness | Future endurance | Scalability | Protos fit | Cancellation/lifecycle | Resource cost | Optimizer/AOT | Actor isolation | Migration | Reversibility | Total /50 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| **A′ — common lifecycle + real operation; Req metadata only** | **5.0** | **5.0** | **5.0** | **5.0** | **5.0** | **4.8** | **5.0** | **5.0** | 4.4 | 4.5 | **48.7** |
| B — Req remains second schedulable operation species | 4.3 | 3.6 | 4.4 | 2.8 | 3.8 | 4.6 | 4.5 | 4.7 | 4.7 | 4.4 | 41.8 |
| C — Req + shadow ProtosIoOperation | 3.0 | 2.5 | 3.8 | 1.2 | 2.3 | 4.1 | 4.1 | 4.4 | 4.3 | 3.5 | 33.2 |
| D — introduce new common private operation abstraction now | 4.6 | 4.8 | **5.0** | 4.0 | 4.5 | 4.7 | 4.8 | **5.0** | 3.7 | 3.0 | 44.1 |

Focused owner-requested scores:

- **A′** — future endurance **10/10**, scalability **10/10**, Protos philosophy **10/10**.
- B — future **7.2/10**, scalability **8.8/10**, Protos **5.6/10**.
- C — future **5.0/10**, scalability **7.6/10**, Protos **2.4/10**.
- D — future **9.6/10**, scalability **10/10**, Protos **8.0/10**.

## Strongest argument against A′

A′ is a broader implementation migration than B.

The old buffered adapter has accumulated semantics around queue ordering, close cutover, buffered read-ahead, output frontier, poison state and cancellation. Moving commitment/lifecycle authority to the common substrate can reveal latent differences that were previously hidden in private booleans.

That risk is real.

The ratified answer is not to keep a second operation framework indefinitely. It is to migrate in bounded slices with equivalence tests and stop on any semantic discrepancy.

## Implementation release boundary

After durable PLAT031 ratification, `PERF006-B` may proceed in bounded slices:

1. converge buffered wrapper lifecycle/admission and request identity onto `ProtosIoLifecycle` + real `ProtosIoOperation`, preserving public behavior;
2. migrate `BufferedReader.read` ordinary guest lower callback to PLAT029 C-prime;
3. migrate `BufferedWriter.flush` write/flush guest callback sequence to PLAT029 C-prime;
4. retain `BufferedWriter.write` as local leaf work on its real operation;
5. keep buffered release/close C-prime outside these slices while D112 remains unresolved;
6. perform deferred full PERF006-B validation and replay-retirement audit only after all blocked release work is resolved.

Every slice must re-audit execution-time `main`.

Any observable semantics question becomes Dxxx. Any need for dual operation authority, a second schedulable operation species, or generic Task-like machinery becomes a new PLAT gate.

## Ratified invariants

PLAT031 is satisfied only if:

1. Every accepted standard buffered request has exactly one authoritative operation identity.
2. Every standard buffered wrapper has exactly one authoritative I/O lifecycle.
3. `ProtosIoOperation` (or a future equivalent preserving this semantic role) owns commitment, cancellation, terminality and deferred C-prime custody.
4. The result Future remains outcome/observation only.
5. Buffered request records are adapter-local metadata, not independent async-operation authorities.
6. Adapter buffer/read-ahead/output-frontier/poison/FIFO state remains adapter-owned.
7. `ActorExecutionDomain` remains the sole operation re-entry scheduler.
8. No shadow operation, hidden Task, second scheduler, parked carrier or backend-thread guest re-entry is introduced.
9. Buffered `read` and ordinary `flush` use operation-owned C-prime around arbitrary guest lower callbacks.
10. `BufferedWriter.write` still uses a real operation even when it completes using only adapter-local leaf work.
11. Buffered close/release execution remains governed by PLAT030 and blocked by D112 until that semantic gate is resolved.
12. Any observable old-Req-vs-common-operation discrepancy stops implementation and opens Dxxx.
13. Any requirement for duplicated operation authority or a new independently schedulable operation species stops implementation and opens PLAT.

## D117 delegated-effect semantic resolution

D117 / GitHub #444 is ratified as Candidate C-prime.

Where a buffered output operation delegates the outer operation's first
irreversible effect to a lower standard I/O operation whose Future does not expose
producer commitment/progress, the adapter uses three-way effect evidence:

```text
ZERO_EFFECT
KNOWN_EFFECT
UNKNOWN_EFFECT_FAILURE
```

This resolves PLAT031's old-`Req` versus common-operation semantic gate without
restoring a second commitment authority.

- proven zero effect may return to ordinary pre-commit cancellation/close
  arbitration;
- known irreversible effect commits the same `ProtosIoOperation`; and
- lower failure whose standard contract permits hidden irreversible effect but
  does not expose it terminates as unknown-effect failure, forbids a false
  zero-effect cancellation/closure result and forbids unsafe replay.

The exact internal representation may widen PLAT009's existing first-effect
settlement machinery, but the result Future remains outcome/observation only and
`Req` remains adapter-local metadata.

D117 releases buffered lifecycle/operation convergence and ordinary
`BufferedWriter.flush` commitment integration under PLAT031. Buffered
release/close execution remains independently blocked by D112/PLAT030.
