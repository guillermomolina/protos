# D108 — Test Tool infrastructure outcome propagation, terminal cleanup barrier and unsafe-capacity failure policy

Status: **RATIFIED — Candidate C′ selected**

Allocated: **2026-09-12**

Explicit project-owner approval: **2026-09-12**

Decision issue: GitHub #427

Nature: implementation-independent Test Tool infrastructure-outcome and terminal-lifecycle contract

Triggered by: `TOOL002-I8D3` closure while composing I8C reservation with D107 provider lifecycle.

Primary consumer: `TOOL002-I`

Normative language effect: **none**.

## Decision boundary

D076, D105 and D107 already establish:

```text
atomic scheduler reservation
        |
        v
transactional provider provisioning
        |
        v
attempt-private guest resources
        |
        v
fresh Process / RootActor
        |
        v
terminal child cleanup
        |
        v
provider cleanup
        |
        v
reservation release
```

I8D1-I8D3 implement the private provider registry/adapter/lease foundation,
multi-provider transaction coordination and frozen RootActor-only `resources`
projection.

The remaining I8D4 composition exposed two facts in the current execution stack:

1. guest execution may produce its observation before the semantic Process has
   actually reached terminal state; requesting termination is not itself the
   terminal barrier;
2. the generic asynchronous exact-execution bridge currently maps a host/runtime
   failure into a caller-domain Protos `Error`, which is incompatible with
   D107's infrastructure-evidence separation.

D108 therefore decides:

- the exact lifecycle point at which a resourceful attempt becomes terminal;
- how guest evidence and infrastructure evidence coexist;
- when I8B capacity may be returned;
- how unsafe cleanup changes D105 admission; and
- what the baseline Test Tool does after an infrastructure-failed resource
  round.

D108 does not decide retry, timeout/kill, preemption, quarantine/recovery,
placement fault-domain identity beyond D097, or distributed reconciliation.

## Selected contract — Candidate C′

The selected architecture is:

> **terminally-closed dual-lane attempt envelope + round-drain fail-stop**

One resourceful attempt exposes one Test-Tool-private asynchronous terminal
completion.

That completion contains two logically separate evidence lanes:

```text
guestObservation        optional
infrastructureOutcome   optional
```

and does not become terminal until lifecycle custody has been resolved through
the D107 cleanup boundary and the Test Tool knows whether the reservation is
safe to return.

## 1. One attempt, one terminal completion

A resourceful attempt has one externally observed Test Tool completion.

There is no public split into:

```text
guestFuture
lifecycleFuture
```

for the scheduler to join manually.

The durable invariant is:

```text
attempt Future complete
        <=>
all lifecycle custody needed by D108 has reached a terminal disposition
```

Producing a guest value, guest Error or detached guest observation is not by
itself attempt completion.

This avoids torn lifecycle state in which reporting or reservation release can
race ahead of provider cleanup.

## 2. Dual evidence lanes

The terminal attempt envelope keeps guest semantics and infrastructure evidence
separate.

Conceptually:

```text
AttemptCompletion
    guestObservation?
    infrastructureOutcome?
    capacityDisposition
```

`guestObservation` uses the existing detached execution-observation semantics.

`infrastructureOutcome` is inert Test Tool / host evidence.

An infrastructure outcome is never automatically converted into:

- a guest Protos `Error`;
- a failed semantic Future owned by guest code;
- a fabricated assertion/test failure; or
- a replacement guest observation.

A guest Error remains guest evidence even when an infrastructure failure is
also recorded later in the same attempt lifecycle.

## 3. Provisioning failure before child creation

If provider resolution/provisioning fails before the child Process is created:

```text
guestObservation = absent
infrastructureOutcome = present
```

I8D2 rollback is attempted according to D107.

All rollback/cleanup evidence remains infrastructure evidence.

No guest result is invented because no guest execution occurred.

If rollback confirms the reserved resources are safe to reuse, the reservation
may later be released at the D108 terminal disposition.

If safety cannot be confirmed, capacity remains held.

## 4. Real child terminal barrier

For an attempt whose child Process started, ProviderLease cleanup cannot begin
merely when guest execution produced an outcome.

The child-side barrier must include:

```text
guest execution observation produced
        |
        v
semantic Process termination reaches terminal state
        |
        v
execution-host / Context terminalization reaches terminal disposition
```

Only after that barrier may provider cleanup begin.

A request to terminate is not equivalent to terminality.

D108 does not prescribe the exact Java waiting primitive, notification object or
state-machine implementation. I8D4 may add the smallest host mechanism needed to
observe real terminal Process/Context completion.

## 5. Provider cleanup follows child terminality

After the child terminal barrier:

- successful ProviderLeases remain host-owned;
- cleanup runs in strict reverse acquisition order;
- all provider cleanups are attempted even if one fails;
- no provider cleanup authority enters guest state.

This preserves D107 and I8D2 lifecycle ownership.

## 6. Infrastructure failure ordering and aggregation

Infrastructure evidence is deterministic.

The earliest lifecycle infrastructure failure remains the primary
infrastructure failure.

Later infrastructure failures are retained in lifecycle order as secondary
evidence.

Examples include:

```text
provider provisioning rejected
rollback cleanup failed
Process terminalization failed
Context/host terminalization failed
ProviderLease cleanup failed
```

A guest Error does not become the infrastructure primary because it belongs to
the guest evidence lane.

I8D4 may use ordinary host exception suppression internally, an immutable
diagnostic list, or another inert carrier, provided the externally visible
ordering and lane separation remain equivalent.

## 7. Reservation release requires confirmed reuse safety

I8B reservation capacity may be released only after the complete D108 terminal
barrier determines that reuse is safe.

The baseline disposition is:

```text
SAFE
    -> release complete I8B reservation

UNSAFE / safety unconfirmed
    -> retain I8B reservation
```

The Test Tool must never infer safety merely because:

- guest code returned;
- guest code failed;
- the Process received a termination request;
- provider cleanup was attempted;
- one provider cleaned successfully; or
- the execution host is no longer accepting guest work.

Reuse must be affirmatively safe under the completed lifecycle.

## 8. Cleanup or terminalization uncertainty is fail-closed

If Process/Context terminalization or provider cleanup cannot establish safe
reuse, the affected reservation remains held for the remainder of the current
invocation.

D108 does not implement:

- quarantine;
- replacement capacity;
- provider health recovery;
- retry/replay;
- external reconciliation; or
- persistence of poisoned capacity across invocations.

Those require later explicit decisions.

## 9. Round-drain infrastructure cutover

If any attempt in the currently admitted D105 resource round acquires an
infrastructure outcome, the Test Tool does not cancel already-admitted siblings.

Every already-admitted sibling is allowed to reach its own D108 terminal
completion.

This is required because D108 does not introduce timeout/kill/preemption and
because a sibling may still own Process/provider cleanup custody.

After the complete admitted round drains:

- safe sibling reservations are released;
- unsafe sibling reservations remain held;
- no still-pending member of the current H window is admitted;
- the current H window is not rescanned;
- no later H window is started; and
- the invocation terminates as infrastructure-failed.

The infrastructure-abort cutover therefore overrides the normal D105 rescan only
after the current admitted round has reached terminal disposition.

## 10. Baseline fail-stop applies to the whole invocation

After a D108 infrastructure-failed resource round drains, the baseline local Test
Tool stops the entire invocation.

It does not continue later resource-free tests.

This is intentionally conservative.

D108 does not claim that unrelated resource-free execution has become unsafe.
The baseline stops because:

- the current local scheduler has no ratified fault-domain/quarantine institution;
- continuing would produce a partial run under a new implicit fail-over policy;
- deterministic evidence is clearer when the infrastructure cutover is one
  explicit invocation boundary.

A future decision may permit continuation on proven-unaffected fault domains.

## 11. Result and reporting semantics

Infrastructure failure is run/tool evidence, not guest-language semantics.

For an attempt:

### Guest failure, infrastructure healthy

```text
guestObservation = failed/Error
infrastructureOutcome = absent
reservation = SAFE -> released
```

This remains an ordinary test failure.

### Guest success, infrastructure cleanup failure

```text
guestObservation = successful
infrastructureOutcome = cleanup failure
reservation = UNSAFE -> retained
```

The guest success is preserved as evidence, but the attempt/run is not reported
as a clean successful test execution.

### Provisioning failure before guest start

```text
guestObservation = absent
infrastructureOutcome = provisioning/rollback evidence
```

No test failure is fabricated.

### Case never admitted because of infrastructure cutover

The case is not fabricated as passed or failed.

Its non-execution is reported as run-level infrastructure-abort evidence through
the Test Tool's later reporting integration.

D108 does not define final CLI wording or a new guest-visible result type.

## 12. D105 deterministic ordering is retained

For attempts that reached execution, logical TestPlan identity/order remains the
reporting order.

Physical terminal-completion order does not reorder guest results.

Infrastructure evidence is attached to its owning attempt/lifecycle and the
run-level infrastructure-abort boundary.

Cases not started because of the cutover remain distinguishable from ordinary
D105 skipped/non-selected semantics; I8D4 must not silently reuse an existing
guest skip reason for infrastructure abort.

The exact final reporting carrier may be implemented in bounded later slices.

## 13. Resource-free fast path remains unchanged when healthy

A healthy resource-free execution path has no provider transaction and does not
need D107 lease cleanup.

D108 does not require provider registry lookup, resource bundles, provider
transactions or unsafe-capacity accounting for resource-free attempts.

The existing H fast path therefore remains the performance baseline.

The whole-invocation fail-stop in section 10 applies only after actual
infrastructure failure has been observed.

## 14. Distributed/remote evolution

The durable D108 envelope is intentionally suitable for later remote execution.

A future worker may report:

```text
guestObservation
infrastructureOutcome
capacityDisposition
faultScope
```

without changing guest Error semantics or D107 capability identity.

A later decision may add:

- resource/provider/placement/worker fault scope;
- quarantine;
- drain of only one placement;
- continued scheduling on unaffected placements;
- worker replacement; or
- distributed reconciliation.

The current baseline deliberately omits `faultScope` policy and globally
fail-stops after the affected round because the local scheduler has not ratified
a narrower recovery institution.

## Comparative audit

The owner approval followed comparison across test frameworks, schedulers,
resource runtimes, CI systems and structured-concurrency models.

### Kubernetes DRA

DRA provides the strongest direct precedent for the distinction between
successful workload execution and safe resource reuse.

Prepared resources require unprepare/cleanup before release, and cleanup failure
must not be silently interpreted as reusable capacity.

D108 adopts the lifecycle rule without importing Kubernetes controllers,
reconciliation or cluster APIs.

### Kubernetes device/plugin health

Device health and allocation state are scheduler infrastructure, not workload
language exceptions.

D108 likewise keeps provider/worker/resource health outside guest Error
semantics.

### Nomad

Nomad separates task exit observation from destructive cleanup of task resources.

The distinction between `WaitTask`-style completion and `DestroyTask`-style
cleanup strongly supports D108's terminal envelope rather than equating guest
return with attempt completion.

### Slurm

Slurm keeps resources/nodes unavailable while epilog cleanup is in progress and
can drain a node after epilog failure rather than immediately returning it to
the scheduler.

D108 adopts the fail-closed reuse principle.

It does not yet adopt Slurm's mature node-level fault-domain continuation.

### Bazel and remote execution

Bazel/remote execution separate test/action result from execution infrastructure
and support a transport-neutral execution boundary.

D108 similarly keeps guest result separate from infrastructure outcome and uses
an envelope that can cross a future remote boundary.

### Buck2 local resources

Buck2's local-resource pool demonstrates that test completion and managed
resource return are scheduler/harness concerns.

D108 preserves that separation while using capability values rather than
environment descriptors as the guest authority model.

### pytest

pytest distinguishes setup/call/teardown phases and runs finalizers in reverse
order for acquired resources even when later setup or teardown fails.

D108 adopts the evidence/lifecycle separation and best-effort cleanup behavior.

### JUnit lifecycle resources

JUnit lifecycle callbacks and closeable extension resources similarly keep
framework cleanup outside the semantic return value of the test body.

D108 does not reinterpret lifecycle failure as a guest-language value.

### Go testing cleanup

Go's testing harness registers LIFO cleanup owned by the harness and executes it
after test/subtest activity.

This supports D108's host-owned terminal cleanup boundary.

### Terraform providers

Terraform's provider boundary demonstrates that diagnostics and partial external
state can coexist.

D108 similarly preserves already-produced guest evidence while also reporting a
later infrastructure cleanup failure.

### Testcontainers / Ryuk

External cleanup/reaping reinforces that resource cleanup ownership must survive
the code under test.

D108 retains cleanup authority outside the child Process.

### GitHub Actions and Jenkins

CI systems distinguish test-report status from runner/pipeline infrastructure
failure and place fail-fast/continuation policy at orchestration level.

D108 similarly defines an explicit Test Tool cutover rather than guest semantics.

### Structured concurrency

Structured-concurrency systems make parent/scope terminality depend on child
terminality.

D108 applies the same ownership principle to a Process/provider attempt scope:
the enclosing attempt is not terminal while child/resource cleanup remains live.

### WASI / WebAssembly Component Model

Owned resource handles model explicit lifetime and destruction independently of
the guest's ordinary returned value.

That aligns strongly with Protos' explicit authority philosophy and D107's
host-owned leases.

## Candidate comparison

Scores 1–10.

| Criterion | A guest Error | B host latch | **C′ terminal dual lane** | D two Futures | E fatal host exception |
| --- | ---: | ---: | ---: | ---: | ---: |
| Future resilience | 2 | 7 | **10** | 9 | 5 |
| Scalability | 3 | 6 | **9** | 8 | 4 |
| Protos philosophy | 1 | 6 | **10** | 8 | 5 |
| Evidence fidelity | 2 | 8 | **10** | 9 | 6 |
| Authority/lifecycle safety | 2 | 8 | **10** | 9 | 6 |
| Deterministic scheduling/reporting | 6 | 7 | **9** | 7 | 5 |
| Local fast-path cost | **10** | 8 | 9 | 7 | 9 |
| Distributed/remote evolution | 2 | 5 | **10** | 9 | 3 |
| Failure containment/recovery compatibility | 2 | 5 | **9** | 9 | 3 |
| Simplicity/reversibility | **9** | 8 | 8 | 5 | 8 |
| **Mean / 10** | **3.9** | **6.8** | **9.4** | **8.0** | **5.4** |

Focused owner criteria:

| Candidate | Future resilience | Scalability | Protos philosophy |
| --- | ---: | ---: | ---: |
| A guest Error | 2 | 3 | 1 |
| B host latch | 7 | 6 | 6 |
| **C′** | **10** | **9** | **10** |
| D two Futures | 9 | 8 | 8 |
| E fatal host exception | 5 | 4 | 5 |

## Why A is rejected

A directly violates D107.

Infrastructure/resource lifecycle failure is not a semantic guest Error merely
because it happened while a test was executing.

Collapsing the lanes would make reporting ambiguous and make future remote
execution substantially harder.

## Why B is insufficient

A single host latch is cheap for the current local implementation.

It loses exact per-attempt ownership and becomes awkward when failures occur
across multiple placements/workers/providers.

C′ retains local simplicity while preserving association.

## Why D is not selected

Two Futures represent the same lifecycle as two independently observable
completion states.

That permits accidental use of:

```text
guest Future complete
```

as if it meant:

```text
attempt terminal
```

and requires every caller to remember to join the second Future before release or
reporting.

C′ makes that invalid state unrepresentable at the Test Tool boundary.

## Why E is insufficient

Fatal host exception unwinding gives a cheap fail-fast path but couples
infrastructure failure to control flow.

It can discard already-produced guest evidence and can interrupt structured
round cleanup unless timeout/cancellation/preemption semantics are also defined.

Those semantics remain outside D108.

## Strongest argument against C′

The whole-invocation fail-stop after one infrastructure-failed round is coarse.

In a future large distributed run, one unsafe GPU/provider/worker could cause the
Test Tool to stop despite many healthy independent placements.

Mature schedulers can quarantine/drain only the affected fault domain.

D108 intentionally pays that utilization cost now rather than silently inventing
fault-domain and recovery semantics.

## Regret scenario

C′ becomes too conservative if Protos evolves toward:

- many remote workers;
- thousands of concurrent resourceful attempts;
- heterogeneous placement domains;
- routine partial worker/provider failures; and
- workloads where stopping the entire run after one domain failure is very
  expensive.

## Escape path

A future Dxxx can extend the infrastructure lane with explicit fault scope:

```text
resource
provider
placement
worker
```

and define quarantine/recovery plus continuation on unaffected domains.

The durable parts of D108 remain unchanged:

- guest/infrastructure lane separation;
- one terminal attempt completion;
- child terminal barrier before provider cleanup;
- deterministic infrastructure evidence;
- safe-capacity disposition;
- no silent reuse of unsafe capacity.

That makes distributed continuation an additive scheduler policy rather than a
rewrite of guest semantics or provider lifecycle.

## Intentionally deferred

D108 does not decide:

- retries or replay;
- timeout/kill/preemption;
- quarantine implementation;
- recovery/health-check loops;
- worker-placement identity beyond D097;
- cross-run/global reservation;
- distributed reconciliation;
- user-configurable fail-fast/maxfail CLI policy;
- exact final CLI/report wording;
- general language-level exception semantics.

## Ratification effect

D108 ratification is governance/design only.

It changes no:

- executable implementation;
- Protos specification;
- Maven implementation version;
- D069 jobs semantics;
- D105 ordinary fixed-window/backfill behavior before infrastructure cutover;
- D107 provider/capability architecture;
- I8A/I8B/I8C implementation;
- I8D1/I8D2/I8D3 implementation;
- native boundary.

After publication, TOOL002-I may continue with bounded I8D4 implementation of
the private terminal lifecycle/envelope/round-drain composition before public
resourceful `Main.protos` wiring.
