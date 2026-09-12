# D097 — Test Tool resource catalog scope/locality vocabulary contract

Status: **RATIFIED — Candidate B′ selected**

Allocated: **2026-09-11**

Explicit project-owner approval: **2026-09-12**

Decision issue: GitHub #392

Primary consumer: `TOOL002-I` / GitHub #96

Predecessors: `D076`, `D077`, `D094` — RATIFIED

Nature: implementation-independent Test Tool resource-catalog scheduling contract

Normative language effect: **none**.

## Decision boundary

D076 assigns resource capacity, scope/locality and provider selection to the
environment-owned catalog while keeping TestPlan requirements inert. D077
persists one schema-controlled `scope` String in the strict/versioned catalog but
deliberately deferred its exact vocabulary. D094 concerns only corpus-owned
requirements-sidecar acquisition and does not select catalog scope.

D097 selects the smallest v1 semantic vocabulary for that existing `scope`
field. It deliberately does **not** select physical worker topology, catalog
source/CLI spelling, provider implementation APIs, multiple-catalog merge,
cross-run global coordination, sharding, remote transport, fairness or public
Protos language semantics.

## Selected contract — Candidate B′

For `resource-catalog-version = 1`, the complete valid scope vocabulary is:

```text
placement
run
```

Any other value fails closed as an unsupported v1 catalog scope.

`scope` represents only the **capacity accounting / reservation domain** that the
scheduler must understand before provider provisioning.

It does not represent:

- capability or lease lifetime;
- provider lifetime or ownership;
- physical worker, host, node, VM, rack, zone or cluster identity;
- whether the physical resource is internal or external to the execution fleet;
- functional read/write authority;
- physical resource IDs;
- cross-run/global fairness or coordination.

D076 remains authoritative that the concrete capability/lease supplied to the
fresh child Process is attempt-private and remains in custody through terminal
cleanup.

## `scope = "placement"`

A `placement` pool belongs to an execution-placement domain.

A case may reserve that pool only when its eventual execution placement is
compatible with that domain. Reservation still precedes provider capability
provisioning as required by D076.

D097 does not define the physical granularity of an execution-placement domain.
It is intentionally not synonymous with:

```text
thread
carrier
worker
JVM
host
node
container
VM
rack
zone
```

The current local Test Tool may have one effective placement domain. A future
distributed executor may materialize placement domains differently without
changing CaseSpec resource requirements or D097's meaning.

Examples that naturally fit `placement` include a GPU or simulator pool that is
usable only from the applicable execution environment.

## `scope = "run"`

A `run` pool is one logical capacity pool visible across compatible execution
placements participating in one logical Test Tool invocation.

It imposes no placement affinity by itself.

A physically external service may still have `scope = "run"` when its scheduling
capacity is shared by all placements in that invocation. For example, a license
server, integration database or SaaS test environment is not classified by
physical externality; the provider/profile layer owns how concrete access is
materialized.

`run` does not define cross-invocation or global coordination. If independent
Test Tool runs later need one globally coordinated capacity authority, that is a
future environment/scheduler decision rather than hidden provider-side
scheduling.

## Orthogonality with D076/D077

The selected axes remain:

```text
Requirement.key / mode / units
        |
        v
Catalog capacity + scope
        |
        v
atomic scheduler reservation
        |
        v
placement known
        |
        v
provider/profile
        |
        v
attempt-private capability
        |
        v
fresh Process
        |
        v
terminal cleanup/release
```

`scope` answers where the scheduler accounts for the catalog pool.

`mode` remains independently:

```text
shared(key, positiveUnits)
exclusive(key)
```

Scheduling mode does not imply functional authority.

## Why the D076 examples are not the v1 enum

D076 required the architecture to remain capable of distinguishing concepts such
as:

```text
case-private
worker-local
host-local
run-local
cluster/run-global
external-global
```

Those examples mix several independent dimensions:

1. capability lifetime/visibility (`case-private`);
2. physical topology (`worker`, `host`, `cluster`);
3. reservation namespace / placement affinity (`local`, `run-global`);
4. physical/provider location (`external`).

Turning them directly into one public enum would silently collapse these
dimensions and would prematurely make current physical topology part of the
durable Test Tool schema.

Candidate B′ assigns `scope` only dimension 3 and keeps the others with their
existing or future owners.

## Single-host behavior

On today's local single-host Test Tool, `placement` and `run` may map to the same
physical machine. They remain semantically distinct:

- `placement` states that the pool is attached to the selected execution
  placement domain;
- `run` states that the pool is shared across the complete logical Test Tool
  invocation.

This distinction costs no extra live machinery for resource-free cases and does
not require a public host/worker abstraction.

## Multiple local workers

D097 does not decide whether several future workers on one host share one
placement domain or use several domains.

A future topology contract can map physical workers/hosts/nodes to placement
domains or add an inert topology-domain descriptor without changing:

- resource keys;
- Requirement representation;
- shared/exclusive modes;
- units;
- D091 requirement-to-CaseSpec join;
- D076 atomic reservation; or
- provider/capability authority.

## Remote execution

For remote execution:

- `placement` capacity is attached to a compatible execution-placement inventory;
- `run` capacity remains one run-wide logical pool;
- the requirement remains topology-neutral;
- placement occurs before live provider provisioning.

D097 therefore does not force the future remote executor to expose hostnames,
worker IDs or a particular cluster institution.

## Cross-run global resources

A genuinely global scarce resource shared by several independent Test Tool runs
is intentionally not claimed by v1.

For example, a FlexLM pool shared by three independent runs cannot safely be
modeled by giving each run the full capacity. Future solutions may include:

- an external authority allocating a quota to each run, which then appears as
  ordinary `scope = "run"` capacity; or
- a later versioned global reservation authority/scope contract.

A provider must not silently create its own hidden global admission queue because
D076 assigns scheduling/admission ownership above the provider boundary.

## Failure behavior

Under catalog schema v1:

```text
scope ∈ { "placement", "run" }
```

Unknown, missing or malformed scope values fail closed through the catalog
configuration/infrastructure plane. They are not fabricated into guest Protos
`Error` values.

A syntactically valid resource whose placement compatibility cannot be satisfied
is likewise infrastructure/placement evidence under D076.

## Comparative prior art

The project-owner approval followed an expanded survey across test runners, CI
systems, build systems, distributed schedulers and HPC/cluster resource managers.

### CTest

CTest separates test resource requirements from a versioned resource
specification describing concrete resource IDs/slots. It is useful evidence for
keeping capacity allocation separate from test declarations, but its locality
model is too local-execution-specific to copy as the Protos vocabulary.

### JUnit and cargo-nextest

JUnit `ResourceLock` and cargo-nextest groups/required-threads demonstrate that
conflict/capacity scheduling can be expressed independently from actual
functional authority. They do not provide enough physical locality or provider
architecture to serve as a complete D097 model.

### pytest-xdist and language test harnesses

pytest-xdist, Swift Testing, Go test, Rust test harnesses and NUnit provide useful
worker affinity or parallel/serialized controls but do not model a complete
inventory -> placement -> reservation -> capability chain. They are negative
evidence against making current worker/thread concepts durable catalog scope.

### GitHub Actions, GitLab CI, Jenkins, Buildkite and Azure Pipelines

These CI systems separate, to different degrees, execution routing/agent
selection from concurrency locks/resource groups. Jenkins is especially
instructive because lockable-resource labels and node labels are distinct
concepts. This supports keeping physical execution topology separate from the
reservation-domain meaning of D097 scope.

### Bazel / Remote Execution

Bazel models execution platforms and execution constraints/properties as a
separate placement substrate. It provides strong evidence that topology and
execution-environment matching should remain orthogonal. Protos deliberately
does not import Bazel's general platform/selector institution in v1.

### Buck2

Buck2 local resources use a provider-created homogeneous pool, reserve an entry
for a test and release it afterward, while remote execution remains a separate
execution tier. This closely supports Protos' separation between resource pool,
attempt reservation, command/run lifetime and execution backend.

### Kubernetes Dynamic Resource Allocation

Kubernetes DRA separates resource inventory/accessibility, claim lifetime,
scheduler placement and driver preparation. ResourceSlice node accessibility is
not the same field or concept as ResourceClaim lifetime. This is strong evidence
against one enum mixing case lifetime, host locality and provider/externality.

### Nomad

Nomad device requirements constrain scheduling to a compatible node; only after
placement does the client/plugin materialize device access. This strongly matches
D076's placement-before-provisioning invariant while demonstrating that the
physical mechanism underneath does not need to become the public scope value.

### Slurm

Slurm sharply distinguishes node-bound GRES such as GPUs from shared software
licenses that are not tied to a host. Remote licenses may additionally be
coordinated through slurmdbd. This supports the durable distinction between
placement-bound capacity and placement-independent shared capacity without
turning physical externality into scheduling scope.

### HTCondor

HTCondor advertises machine resources and matches job requests to compatible
execution points. It reinforces the same placement/inventory split while keeping
the job request independent of a concrete host identity.

## Candidate comparison

Scores are 1–5.

| Criterion | A literal enum | **B′ placement/run** | C +external | D multi-axis now | E topology IDs | F provider inferred | G selectors/platform |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Correctness / invariants | 3.5 | **5.0** | 4.0 | **5.0** | 4.5 | 2.5 | 4.5 |
| Protos alignment | 2.5 | **5.0** | 3.8 | 4.0 | 3.0 | 3.0 | 2.0 |
| Future-option resilience | 3.0 | **4.8** | 4.2 | **5.0** | **5.0** | 2.5 | **5.0** |
| Scalability | 3.5 | **5.0** | 4.5 | **5.0** | **5.0** | 3.0 | **5.0** |
| Conceptual simplicity | 4.0 | **4.8** | 4.0 | 2.8 | 3.0 | 4.5 | 1.5 |
| Portability / implementation freedom | 3.0 | **5.0** | 4.0 | **5.0** | 3.5 | 4.0 | 4.0 |
| Runtime / resource cost | **5.0** | **5.0** | 4.8 | 4.5 | 4.5 | 4.5 | 4.0 |
| Failure / operability | 3.5 | **5.0** | 4.0 | 4.5 | 4.0 | 2.5 | 3.5 |
| Reversibility / migration | 3.0 | **4.8** | 4.0 | 4.0 | 3.5 | 3.5 | 3.0 |
| Evidence maturity / risk | 4.5 | **5.0** | 4.5 | 4.5 | 4.5 | 3.5 | **5.0** |
| **Total / 50** | **36.0** | **49.4** | **41.8** | **44.3** | **40.5** | **34.0** | **37.5** |

Focused project-owner criteria:

| Candidate | Future resilience | Scalability | Protos philosophy |
| --- | ---: | ---: | ---: |
| A literal enum | 3.0 | 3.5 | 2.5 |
| **B′ placement/run** | **4.8** | **5.0** | **5.0** |
| C +external | 4.2 | 4.5 | 3.8 |
| D multi-axis now | **5.0** | **5.0** | 4.0 |
| E topology IDs | **5.0** | **5.0** | 3.0 |
| F provider inferred | 2.5 | 3.0 | 3.0 |
| G selectors/platform | **5.0** | **5.0** | 2.0 |

## Why B′ is selected

B′ preserves the architectural separation found in the strongest prior art while
keeping the v1 institution very small:

```text
placement -> capacity attached to execution placement
run       -> capacity shared by the logical invocation
```

It does not force Protos to define worker, host, node, cluster or external
resource ontology before those concepts are needed.

It preserves D076's single scheduler/admission authority, D077's strict inert
catalog representation and pay-only-for-what-you-use design.

## Rejected alternatives

### A — literal D076 example enum

Rejected because it mixes lifetime, physical topology, reservation namespace and
externality.

### C — add `external`

Rejected because physical externality does not determine scheduler visibility.
An external database can be placement-restricted or run-wide.

### D — multiple public axes in v1

Architecturally strong and maximally expressive, but rejected as premature.
D076 already fixes attempt-private custody and there is no current need for
public lifetime/topology axes.

### E — opaque/hierarchical topology IDs now

Scales well but prematurely publishes physical topology before worker/remote
placement architecture exists.

### F — provider-inferred scope

Rejected because the scheduler needs placement/reservation compatibility before
provider provisioning. It would risk a second hidden scheduler inside providers.

### G — general selector/platform model

Powerful but too institutional for current TOOL002. It would overlap future
placement/topology design and make simple local tests pay for a distributed
constraint language they do not use.

## Future regret scenario and escape path

The strongest regret case is a distributed executor that simultaneously needs:

```text
worker-local scratch
host-shared GPU
rack-local appliance
cluster-wide license
```

B′ alone does not name all of those physical levels.

The escape path is deliberately cheap: retain `scope = "placement"` and add a
later versioned inert topology-domain/locality descriptor that explains how one
placement domain relates to workers/hosts/nodes/racks. Existing resource keys,
Requirement records, shared/exclusive semantics, D091 joins, D076 atomic
reservation and provider/capability boundaries remain unchanged.

If independent runs later require one globally coordinated capacity pool, add a
separate environment/global reservation authority or schema generation rather
than changing the meaning of `run`.

## Intentionally deferred

D097 does not select:

- catalog physical filename;
- public catalog CLI option spelling;
- multiple catalog merge/inheritance;
- provider implementation API;
- concrete physical worker/host/node topology;
- topology-domain identifiers or selectors;
- cross-run/global reservation authority;
- fairness/priority/starvation;
- retry, timeout/kill or sharding protocol;
- remote transport or CAS;
- `jobs=auto`;
- public Protos semantics.

## Ratification effect

D097 ratification is governance/tooling only.

It changes no:

- executable Test Tool or runtime implementation;
- Protos specification;
- Maven implementation version;
- native boundary;
- D069 jobs contract;
- D076 requirement/reservation/provider architecture;
- I6E requirements-sidecar implementation.

TOOL002-I6E remains the next executable slice once repository-wide publication
validation is again green. D097 prepares the later catalog parser/validation
work without activating catalog/provider behavior.
