# D117 — Buffered wrapper delegated-effect commitment across cancellation and close cutover

Status: **RATIFIED — Candidate C′ selected**

Specification revision: **`0.1.410`**  
Explicit project-owner approval: **2026-09-12**  
Owning work item: `PERF006-B` / GitHub Issue `#276`  
Decision issue: GitHub Issue `#444`

Nature: non-normative decision/rationale record for the normative standard I/O
semantics in `spec/io/BYTE_IO.md`.

## Decision

Select **Candidate C′ — delegated first-effect arbitration with three-way internal
effect evidence**.

A standard output wrapper whose outer operation can acquire its first irreversible
effect only through a delegated lower `ByteWritable` or `Flushable` operation must
not collapse lower progress uncertainty into a boolean merely because the lower
result is represented by a `Future`.

The durable evidence classes are:

```text
ZERO_EFFECT
KNOWN_EFFECT
UNKNOWN_EFFECT_FAILURE
```

These are semantic evidence classes for producer-side arbitration. They are not
new public Future states, not new Error categories and not a public progress API.

Conceptually:

```text
outer UNCOMMITTED
        |
        | begin lower operation that may create
        | the outer operation's first irreversible effect
        v
FIRST_EFFECT_IN_FLIGHT
        |
        +-- ZERO_EFFECT
        |      -> return to pre-commit arbitration
        |      -> an already-pending outer cancellation/close may win
        |
        +-- KNOWN_EFFECT
        |      -> outer COMMITTED
        |      -> continue ordinary success/failure aftermath
        |
        +-- UNKNOWN_EFFECT_FAILURE
               -> outer FAILED
               -> no claim that k == 0
               -> no claim that k > 0
               -> no zero-effect cancellation/closure result
               -> no unsafe replay
```

The implementation may represent `FIRST_EFFECT_IN_FLIGHT` with the existing
`ProtosIoOperation.ATTEMPTING_FIRST_EFFECT` mechanism or an equivalent future
representation that preserves the same one-operation authority.

## Ratified rules

1. **Future terminal state is not producer commitment state.** A lower Future is an
   outcome/observation carrier. A wrapper must not infer lower commitment merely
   from the existence of the Future or from a cancellation request.

2. **Delegation start alone is not necessarily commitment.** Starting a lower
   operation that may have zero irreversible effect does not by itself force the
   outer operation to commit.

3. **Unknown first-effect aftermath blocks zero-effect terminal publication.**
   While the lower operation's first-effect aftermath is unresolved, an outer
   cancellation, Actor-termination cancellation request or close cutover may be
   recorded and may request lower cancellation, but it must not publish an outer
   terminal result whose contract asserts zero irreversible effect.

4. **`ZERO_EFFECT` requires proof.** A lower terminal outcome may release the outer
   operation back to pre-commit arbitration only when the applicable lower
   contract proves that no irreversible effect attributable to that delegated
   operation occurred.

5. **Successful standard lower cancellation is zero-effect evidence.** For the
   standard I/O contracts governed by Core v0.1, a lower Future reaches
   `cancelled` only when that lower operation's cancellation contract permits a
   zero-effect cancelled outcome. This can therefore provide `ZERO_EFFECT`
   evidence for the delegated attempt.

6. **A lower cancellation is not automatically the outer cancellation.** If a
   delegated lower operation reaches `cancelled` but no eligible outer
   cancellation/cutover request owns the arbitration, the wrapper must not invent
   an outer `cancelled` result. Failure to complete required lower propagation is
   handled by the wrapper's ordinary failure semantics.

7. **Known irreversible progress commits the outer operation.** When lower
   aftermath proves that the first irreversible effect required by the outer
   operation occurred, the outer operation becomes committed before any pending
   zero-effect cancellation or close-cutover outcome can be published.

8. **A generic failed `ByteWritable.write` is not zero-effect evidence.** Standard
   `ByteWritable.write` permits a failed operation to have contributed an
   unexposed contiguous prefix `k`, where `0 <= k <= N`. Because ordinary
   `ByteWritable` does not expose `k`, a lower failed Future does not prove
   `k == 0`.

9. **A generic failed delegated write is also not positive-effect proof.** The
   wrapper must not pretend that `k > 0` is known merely to fit a binary internal
   API. The correct evidence class is `UNKNOWN_EFFECT_FAILURE`.

10. **`UNKNOWN_EFFECT_FAILURE` outranks zero-effect cancellation/closure.** When
    the lower contract permits irreversible effect but the lower failure does not
    reveal whether that effect occurred, the outer operation fails under its
    ordinary error/poison rules. A competing cancellation or close cutover must
    not replace that failure with an outcome whose contract would assert zero
    outer effect.

11. **Unknown-effect failure forbids replay.** The wrapper may retain whatever
    adapter-local failure/poison state its existing contract requires, but it must
    not automatically replay bytes or propagation work whose already-completed
    portion cannot be reconstructed exactly.

12. **Stronger lower contracts may prove more.** If a future lower capability
    explicitly and normatively guarantees failure atomicity or otherwise proves
    zero effect for a particular failed outcome, that stronger contract may
    provide `ZERO_EFFECT` evidence. D117 does not infer such a guarantee from an
    ordinary failed `ByteWritable` or `Flushable` Future.

13. **Post-commit lower cancellation is committed aftermath.** Once the outer
    operation is committed, cancellation of a later required delegated operation
    cannot turn the outer Future into `cancelled` as though the outer effect never
    happened. The wrapper follows its ordinary committed failure mapping.

14. **`BufferedWriter.flush()` is one operation.** Delivery of pending adapter
    bytes and any required lower `flush()` are phases of the same outer flush
    operation and one output-propagation frontier; they are not independent
    user-visible operations merely because they use multiple lower Futures.

15. **Close uses the same evidence.** A close cutover competing with a first-effect
    delegated attempt is classified from `ZERO_EFFECT`, `KNOWN_EFFECT` or
    `UNKNOWN_EFFECT_FAILURE`; host completion order does not manufacture a
    different semantic classification.

16. **One semantic authority remains mandatory.** D117 does not authorize a
    buffered-only shadow operation, second commitment bit, hidden Task, second
    scheduler or Future-owned producer state. PLAT031's one
    `ProtosIoOperation`/one-lifecycle architecture remains authoritative.

17. **No public progress surface is introduced.** D117 adds no partial-write
    result, byte-count exposure, fifth Future state, continuation object or
    wrapper-specific cancellation token.

18. **Backend neutrality is required.** The same contract must remain implementable
    over readiness I/O, completion I/O, native/FFM, WASI, brokered and distributed
    providers without changing observable Protos semantics.

## Canonical BufferedWriter cases

### Lower write cancelled before any effect

```text
BufferedWriter.flush()
    -> target.write(pending)
    -> lower CANCELLED with standard zero-effect cancellation guarantee
```

If an outer cancellation or close cutover was already pending and remains eligible,
the lower cancellation provides `ZERO_EFFECT` evidence and that pending outer
zero-effect outcome may win.

If no such outer cancellation/cutover owns the arbitration, the wrapper does not
invent an outer cancellation merely because its required lower operation was
cancelled.

### Lower write resolves

```text
BufferedWriter.flush()
    -> target.write(pending)
    -> lower RESOLVED
```

The pending bytes reached the immediate target's accepted-output boundary. The
outer flush has crossed an irreversible propagation effect and is committed before
continuing any required lower `flush()`.

### Lower write fails with ordinary ByteWritable uncertainty

```text
BufferedWriter.flush()
    -> target.write(pending)
    -> lower FAILED
    -> hidden k may be 0..N
```

This is `UNKNOWN_EFFECT_FAILURE`.

The outer flush fails. It does not claim zero effect, does not claim a positive
prefix that the protocol did not reveal and does not replay the uncertain output.

### Lower flush cancelled after bytes were delivered

```text
target.write(pending) -> RESOLVED
outer = COMMITTED
target.flush()        -> CANCELLED
```

The outer operation cannot become `cancelled`. Required propagation did not
complete normally after outer commitment, so the wrapper follows committed failure
aftermath. This matches the existing TextWriter direction: a lower cancellation
after writer commitment is mapped as output failure rather than rewriting the
outer operation to cancellation.

## Relationship to existing normative I/O rules

D117 refines composition of already-existing rules; it does not replace them.

`IO_CORE.md` remains authoritative that:

- commitment belongs to the operation, not to the Future state;
- cancellation can win only while the operation contract still permits the
  corresponding cancelled outcome;
- an irreversible effect cannot later be represented as though cancellation won
  before that effect;
- close closure-terminates only work that is still semantically eligible for its
  zero-effect uncommitted outcome.

`BYTE_IO.md` remains authoritative that:

- successful pre-commit write cancellation contributes zero bytes;
- failed writes may contribute an unexposed contiguous prefix;
- flush is an ordered propagation frontier;
- partial failed flush propagation does not roll back; and
- unsafe duplicate replay is forbidden.

D117 fixes the missing wrapper-composition rule when the lower Future exposes an
outcome but not the lower producer's hidden commitment/progress state.

## Relationship to PLAT009

PLAT009 is the strongest internal implementation precedent.

Its transient first-effect gate already establishes that:

- entering an effect attempt is not commitment;
- cancellation/close may be recorded while aftermath is unknown;
- a proven zero-effect attempt returns to pre-commit arbitration; and
- a proven positive first effect commits before a pending zero-effect cutover can
  rewrite the operation.

D117 extends the **semantic evidence model** needed by wrappers. A delegated lower
operation can also fail under a protocol where positive effect is possible but not
observable. Such failure is neither proven-zero nor proven-positive; C′ records it
as `UNKNOWN_EFFECT_FAILURE` instead of forcing PLAT009's implementation-level
boolean `contributed` to carry information it does not possess.

A future implementation may therefore widen the internal settlement API from a
boolean contribution result to a three-way evidence result. That is an
implementation matter as long as the D117 semantics are preserved.

## Relationship to PLAT029 and PLAT031

PLAT029 remains authoritative for operation-owned C-prime custody and
`ActorExecutionDomain` re-entry.

PLAT031 remains authoritative that buffered byte I/O converges on:

```text
one ProtosIoLifecycle per wrapper
one ProtosIoOperation per accepted operation
Req = adapter-local metadata only
```

D117 supplies the previously missing observable commitment/cutover rule required
to perform that convergence without preserving the legacy `Req.committed` as a
second semantic authority.

D117 did not resolve D112 or PLAT030 lifecycle-release execution semantics. D112 was subsequently ratified independently as Candidate A′ at specification revision `0.1.411`; PLAT030 remains the release-execution architecture authority.

## Comparative evidence

The owner decision followed an exhaustive review of materially different
compositional and asynchronous I/O models.

### Boost.Asio

Boost.Asio provides the cleanest vocabulary for cancellation strength:
**total**, **partial** and **terminal** guarantees. Total cancellation is useful
only when side effects can be guaranteed absent. Partial cancellation is viable
when progress can be reported accurately. Terminal cancellation acknowledges
cases where operation aftermath may no longer be safely resumed.

D117 adopts the underlying information principle without adopting Asio APIs:
cancellation strength must not exceed what is known about irreversible effects.

### Rust / Tokio

Tokio distinguishes cancellation-safe operations from operations such as
`write_all` whose partial progress can make restart unsafe. Buffered flushing can
remain cancellation-safe when the implementation retains exact progress and can
resume without replaying already-propagated bytes.

The Protos difference is intentional: ordinary `ByteWritable.write` hides `k`.
D117 therefore preserves uncertainty instead of pretending that Tokio-style exact
resume information exists.

### Go `io.Writer` / `bufio.Writer`

Go exposes `(n, err)`. `bufio.Writer` can retain exactly the unwritten suffix after
a partial lower write because it knows `n`.

This is strong evidence for D117's negative rule: exact retry/recovery requires
exact progress evidence. Protos deliberately does not expose `k`, so a generic
failed lower write cannot authorize exact suffix replay.

### Java NIO asynchronous channels

Java's asynchronous cancellation model does not make cancellation request equal to
proof that physical I/O performed no effect. Implementations may be unable to
cancel in-flight I/O cleanly and may have to restrict subsequent operations after
uncertain cancellation.

D117 keeps that uncertainty below Protos semantics rather than making Java timing
or channel state the language contract.

### libuv

libuv explicitly distinguishes request cancellation from transferred-byte
aftermath. A cancellation race may complete with success or another error, and
write progress can survive cancellation attempts.

This reinforces the rule that a cancellation request cannot itself prove zero
effect.

### Windows overlapped I/O

`CancelIoEx` is a cancellation request, not synchronous rollback. The original I/O
completion still owns the eventual operation aftermath and storage associated with
the request cannot be reused merely because cancellation was requested.

This supports producer/runtime custody of in-flight aftermath.

### Linux `io_uring`

Cancellation and original completion are separate asynchronous events and may race.
The original completion must still be consumed; cancellation does not retroactively
erase already-completing work.

C′ maps naturally to future completion-oriented Protos backends.

### POSIX / native write semantics

Native writes may complete partially. A failure after a prefix has become
observable cannot be treated as zero-effect rollback merely because the high-level
operation as a whole failed.

This is the underlying systems fact represented by Protos's hidden-prefix rule.

### .NET `System.IO.Pipelines`

`PipeWriter.FlushAsync` strongly demonstrates separation between publication,
backpressure waiting and cancellation of a pending flush wait. It is a useful
architectural precedent but a weaker direct semantic precedent because Pipelines'
flush boundary is not identical to Protos `Flushable`.

### Python `asyncio`

`StreamWriter.write` plus `drain()` primarily models buffered output and
flow-control backpressure. Cancellation of a coroutine wait does not provide the
precise lower effect evidence D117 requires. It is therefore supporting rather
than decisive evidence.

### Node.js Writable streams

Node's write callbacks, buffering and `drain` event provide strong backpressure and
ordered-error evidence, but no direct standard contract equivalent to Protos
zero-effect cancellation of one logical write. It supports separation of queue
state from completion but is not the selected semantic model.

### SwiftNIO

SwiftNIO's event-loop futures keep channel operation ownership in the channel/event
loop rather than treating cancellation of a waiting structured-concurrency task as
automatic rollback of the underlying I/O. This is strong architectural evidence
for producer-side operation authority.

### Kotlin / Ktor

Ktor's `ByteWriteChannel` separates suspending caller mechanics from the channel's
buffer/flush lifecycle, but its public contract does not expose a sufficiently
strong partial-effect/cancellation guarantee to decide D117 directly. It remains
supporting evidence only.

## Candidate comparison

Focused project-owner criteria:

| Candidate | Future endurance | Scalability | Protos philosophy | Result |
|---|---:|---:|---:|---|
| A — commit at delegation start | 8.6/10 | 10.0/10 | 7.4/10 | safe but over-conservative |
| B — preserve legacy outcome-late terminalization | 4.0/10 | 8.5/10 | 2.5/10 | rejected |
| C — boolean first-effect attempt | 9.1/10 | 10.0/10 | 8.7/10 | strong but information-lossy on failed lower write |
| **C′ — three-way delegated effect evidence** | **10.0/10** | **10.0/10** | **10.0/10** | **selected** |
| D — expose lower commitment/progress | 7.8/10 | 7.5/10 | 6.2/10 | too coupled |
| E — shadow buffered authority | 2.0/10 | 6.0/10 | 1.0/10 | prohibited by PLAT031 |

The decisive distinction is not API style. It is **what the wrapper is actually
entitled to know**.

C′ is the only candidate that preserves all three information states without
inventing knowledge:

```text
effect zero is proven
effect is known to have happened
failure occurred but effect is unknown
```

## Strongest argument against C′

C′ adds one more internal evidence class than the existing PLAT009 boolean
`finishFirstEffectAttempt(boolean contributed)` representation.

That makes the runtime state machine slightly richer and may require widening an
internal settlement API.

The alternative is worse. Mapping an information-theoretically ternary situation
onto a boolean necessarily invents one of two facts:

- `false` falsely claims that effect zero is known; or
- `true` falsely claims that positive irreversible effect is known.

C′ keeps the additional state private, bounded and operation-local. It therefore
pays O(1) state only where uncertainty actually exists and does not expand the
language surface.

## Future-regret scenario and escape path

A future backend may expose exact progress, for example a native completion with a
known transferred-byte count or a broker protocol that reports an exact committed
frontier.

C′ does not waste that information. A stronger receiver/backend may retain exact
progress internally and resume safely where its own public contract permits.

Conversely, a remote or distributed provider may only be able to report:

```text
operation failed; side-effect status unknown
```

C′ already represents that case without changing `Future`, `ByteWritable`,
`Flushable` or the language's Error taxonomy.

If future standard APIs deliberately expose exact partial progress, that must be a
separate language decision. It can refine what evidence a wrapper receives without
invalidating D117's rule that cancellation strength cannot exceed effect evidence.

## Consequence

D117 releases the PLAT031 buffered-operation/lifecycle convergence portion of
`PERF006-B` from this semantic blocker.

The next implementation slice may:

- move standard buffered wrapper admission/cutover onto `ProtosIoLifecycle`;
- give each accepted buffered request one real `ProtosIoOperation`;
- represent delegated first-effect uncertainty with bounded operation-local state;
- preserve `Req` only as adapter-local queue/order/payload/lower-Future metadata;
- map post-commit lower cancellation to committed failure aftermath; and
- preserve no-replay/poison behavior for unknown lower write progress.

The next implementation slice must not:

- expose `k`;
- add a public Future state;
- restore `Req.committed` as a second authority;
- create a shadow operation or hidden Task; or
- resolve D112 implicitly.

D112 / PLAT030 remains independently blocking for suspendible lifecycle
release/close implementation.
