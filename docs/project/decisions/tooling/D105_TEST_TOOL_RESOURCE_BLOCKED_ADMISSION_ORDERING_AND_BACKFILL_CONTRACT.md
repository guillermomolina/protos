# D105 — Test Tool resource-blocked admission ordering and backfill contract

Status: **RATIFIED — Candidate E′ selected**

Allocated: **2026-09-12**

Explicit project-owner approval: **2026-09-12**

Decision issue: GitHub #424

Nature: implementation-independent Test Tool scheduler admission-order contract

Triggered by: `TOOL002-I8B` / GitHub #96 after publication of the private D076 atomic reservation kernel.

Primary consumer: `TOOL002-I`

Normative language effect: **none**.

## Identifier note

This decision was initially opened as `D102`, but `D102` was already allocated
to GitHub #421 and ratified for nested workspace package source ownership in
`0.2.412-SNAPSHOT`.

`D103` and `D104` were also already allocated. The decision was therefore
corrected to the next free identifier, `D105`, before repository ratification.

Historical comments on GitHub #424 that say `D102` refer to this same D105
decision and do not create a second D102 authority.

## Decision trigger

D076 deliberately deferred fairness, priority and starvation policy.

TOOL002-H already defines a deterministic bounded wave scheduler. I8B now adds
the live reservation primitive:

```text
tryReserve(bindings, state) -> reservation | null
```

with complete-set atomicity and no partial holding.

Composing these two mechanisms exposes a policy question that cannot be decided
inside `Runner.protos`:

```text
jobs = N

oldest pending case in current scheduling region:
    temporarily resource-blocked

later case:
    complete resource set currently available
```

The Test Tool must decide whether later feasible work may pass the blocked case,
and how far such overtaking may extend.

## Selected contract — Candidate E′

The local Test Tool keeps the existing TOOL002-H logical wave/window boundary
and adds **stable resource rounds inside that fixed window**.

### Fixed logical H window

At the start of a logical window, select exactly the next:

```text
min(jobs, remainingCases)
```

consecutive CaseSpecs in TestPlan order.

That set is immutable for the lifetime of the window.

No CaseSpec outside the window may be pulled forward merely because a member of
the current window is resource-blocked.

### Stable resource-round scan

For each resource round:

1. inspect remaining members of the current logical window in TestPlan order;
2. obtain their already-canonical I8A Requirement-to-CatalogEntry bindings;
3. call I8B `tryReserve` for each remaining member;
4. admit each member whose complete reservation succeeds;
5. leave `tryReserve == null` members pending inside the same logical window;
6. never create a partial reservation for a blocked member.

The scan order is stable TestPlan order.

The policy does not assign a priority number, aging value, fair-share score or
other hidden rank.

### Round barrier and retry

After admitting the feasible members of one resource round:

1. execute only those admitted attempts;
2. retain each reservation through the attempt's terminal cleanup under D076;
3. release the complete reservation after terminal cleanup;
4. wait for the admitted resource round to reach its barrier;
5. rescan only the still-pending members of the same logical window.

The logical window is complete only when every CaseSpec in it has reached
terminal completion.

Only then may the Test Tool construct the next logical H window.

### D069 jobs remains independent

D069 remains global logical execution-slot capacity.

A CaseSpec starts only when both conditions hold:

```text
one slot inside the current logical H window
AND
the complete D076 resource reservation
```

D105 does not reinterpret resource capacity as jobs capacity and does not make
`jobs` provider-dependent.

### Resource-free behavior

For a resource-free window, every member is immediately reservable and the
window executes in exactly one resource round.

Therefore the existing TOOL002-H behavior remains the zero-resource fast path;
D105 adds no queue institution or extra scheduling rounds when no resource is
contended.

### Bounded overtaking

Overtaking is permitted only among CaseSpecs that already belong to the same
fixed logical H window.

A temporarily blocked member may be passed by a later feasible member of that
same window.

A CaseSpec from a later logical window may never pass the blocked member.

This is the central distinction from unrestricted eager/global backfill.

### Deterministic progress

I8A proves that every accepted per-case requirement set is statically feasible
against an otherwise-empty selected catalog.

I8B guarantees complete atomic release of successful reservations.

At the beginning of a resource round after the previous round's terminal cleanup,
the window has no reservation retained by a completed prior round. Under the
current local H lifecycle assumptions, at least the oldest remaining statically
feasible CaseSpec can therefore make reservation progress.

D105 consequently needs no aging, priority or fair-share state to guarantee
per-window progress.

A future lifecycle with non-terminating attempts is a timeout/kill concern, which
remains outside D105.

### Result/report ordering

Execution and resource-round completion order do not redefine TestPlan order.

Aggregation/reporting remains deterministic in the existing logical CaseSpec
order.

D105 is an admission policy only.

## Why not strict FIFO

Strict head-of-line FIFO has the simplest starvation proof but can leave both
jobs slots and unrelated resource capacity idle whenever the oldest pending
CaseSpec is temporarily blocked.

That sacrifices utilization without adding a correctness property needed by the
current fixed-wave Test Tool.

## Why not unrestricted eager backfill

Scanning all remaining TestPlan cases would improve utilization but would destroy
the TOOL002-H wave boundary and allow unbounded overtaking distance.

Once admission can cross the whole pending plan, starvation protection and queue
policy become real scheduler institutions rather than bounded local behavior.

D105 deliberately does not create that institution.

## Why not no-delay predictive backfill

Production-grade conservative backfill, as used by HPC schedulers, protects
older work by reasoning about expected future start/end times or reservations.

The current Test Tool has no duration estimates, preemption institution or
reservation calendar.

Adding them merely to preserve unrestricted backfill would be disproportionate
to TOOL002's present scope.

## Why not priorities, aging or fair-share

No current TestPlan field or user requirement asks for priority.

Adding priority, aging, fair-share accounting, historical usage or preemption
would create new user-visible or durable scheduling semantics that D076
explicitly deferred.

If a future rolling/distributed scheduler requires these mechanisms, it must
make that decision explicitly rather than inheriting an accidental local policy.

## Prior-art findings

The owner approval followed comparison across test runners, CI systems and
general schedulers.

### CTest

CTest demonstrates that resource-capacity correctness and queue-order policy are
separate concerns.

### cargo-nextest

nextest demonstrates that when per-test priority is desired, priority should be
an explicit scheduler input rather than an implicit consequence of iteration
order.

### Slurm

Slurm demonstrates both the utilization cost of strict FIFO and the additional
institution required for protected backfill.

### Kubernetes

kube-scheduler separates feasibility from queue ordering and can keep
temporarily unschedulable work pending while other feasible work progresses.

### Nomad

Nomad similarly treats placement/resource blockage as scheduler state to revisit,
not as application failure.

### HTCondor

HTCondor demonstrates the scalability of explicit priority/fair-share machinery
and also its substantial policy/accounting cost.

### Jenkins Lockable Resources, GitLab resource groups and Buildkite concurrency

These systems make ordered versus opportunistic admission an explicit policy
dimension rather than an invisible implementation detail.

D105 therefore selects the smallest bounded policy compatible with the existing
H abstraction rather than importing a general scheduler institution.

## Candidate comparison

Scores 1–5.

| Criterion | A FIFO | B eager global | C no-delay backfill | D priority/aging | **E′ fixed H-window** |
| --- | ---: | ---: | ---: | ---: | ---: |
| Correctness / starvation | **5.0** | 3.8 | **5.0** | 4.8 | **5.0** |
| Protos alignment | 4.2 | 4.0 | 4.4 | 3.0 | **5.0** |
| Future resilience | 3.6 | 4.2 | **5.0** | **5.0** | 4.8 |
| Scale / utilization | 2.6 | **5.0** | 4.8 | 4.8 | 4.6 |
| Determinism | **5.0** | 4.0 | 4.8 | 4.0 | **5.0** |
| Conceptual simplicity | **5.0** | 4.5 | 3.0 | 2.7 | 4.7 |
| Portability | **5.0** | **5.0** | 4.3 | 4.2 | **5.0** |
| Runtime/resource cost | 3.5 | **5.0** | 3.2 | 3.5 | 4.8 |
| Failure / operability | **5.0** | 3.5 | 4.8 | 4.3 | **5.0** |
| Reversibility | 4.8 | 4.3 | 3.8 | 2.8 | **5.0** |
| **Total / 50** | **43.7** | **43.3** | **43.1** | **39.1** | **48.9** |

Focused owner criteria:

| Candidate | Future resilience | Scalability | Protos philosophy |
| --- | ---: | ---: | ---: |
| A FIFO | 3.6 | 2.6 | 4.2 |
| B eager global | 4.2 | **5.0** | 4.0 |
| C no-delay | **5.0** | 4.8 | 4.4 |
| D priority/aging | **5.0** | 4.8 | 3.0 |
| **E′ fixed H-window** | **4.8** | **4.6** | **5.0** |

## Scalability

For one logical H window, the number of pending members is bounded by `jobs`.

A resource-round rescan is therefore bounded by `O(jobs)` rather than scanning
the complete remaining TestPlan.

The worst-case number of resource rounds for one window is bounded by the number
of cases in that window, so the local scheduler does not introduce a global
`O(totalCases^2)` pending-plan scan.

No history table, priority heap, duration estimate, global queue or cross-run
accounting state is required.

## Future resilience

The contract is deliberately scoped to the current H wave scheduler.

A future rolling, distributed or remote scheduler may choose a broader queue and
cross-window backfill strategy, but must do so through a new explicit decision
that defines its fairness/starvation contract.

The durable parts that survive such a future change are:

- D069 independent jobs capacity;
- D076 complete-set atomic reservation;
- I8A static feasibility;
- I8B reservation/release correctness;
- temporary resource contention as scheduler state rather than guest Error;
- deterministic TestPlan/result identity.

## Strongest argument against E′

The fixed-window boundary can leave resources idle even when a CaseSpec in a
later window would be feasible.

That is a real utilization cost compared with unrestricted backfill.

The selected tradeoff is intentional: it bounds overtaking, preserves H,
guarantees deterministic local progress without adding priority/aging machinery,
and keeps the policy cost proportional to `jobs`, not total suite size.

## Regret scenario and escape path

E′ could become too conservative if Protos evolves to:

- very large continuously-fed test queues;
- long-running heterogeneous tests;
- many scarce independent resources;
- remote workers with strongly asymmetric capacity; or
- throughput-sensitive distributed execution where wave barriers dominate cost.

The escape path is explicit rather than hidden: replace the H-window scheduler
through a future audited decision selecting rolling/global admission plus the
required fairness/starvation institution.

D105 does not constrain that future scheduler to retain fixed windows.

## Intentionally deferred

D105 does not decide:

- provider API/resolution/provisioning;
- placement-domain identity or physical worker topology;
- user-visible priorities;
- aging/fair-share policy;
- preemption;
- retries;
- timeout/kill;
- global/cross-window backfill;
- rolling scheduler architecture;
- sharding;
- remote/distributed scheduling protocol;
- duration estimation;
- reservation calendars;
- `jobs=auto`.

## Ratification effect

D105 ratification is governance/design only.

It changes no:

- executable implementation;
- Protos specification;
- Maven implementation version;
- native boundary;
- D069 jobs contract;
- D076 reservation semantics;
- I8B reservation kernel;
- provider implementation.

After publication, `TOOL002-I` may continue with the bounded executable
composition of I8A/I8B into the existing H scheduler under this fixed-window,
stable-resource-round contract.
