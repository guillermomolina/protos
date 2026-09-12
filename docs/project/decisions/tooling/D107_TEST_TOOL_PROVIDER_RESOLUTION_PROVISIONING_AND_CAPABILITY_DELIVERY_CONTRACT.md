# D107 — Test Tool provider resolution, provisioning transaction and attempt-private capability delivery contract

Status: **RATIFIED — Candidate D′ selected**

Allocated: **2026-09-12**

Explicit project-owner approval: **2026-09-12**

Decision issue: GitHub #426

Nature: implementation-independent Test Tool provider/provisioning/capability-lifecycle contract

Triggered by: `TOOL002-I8C` after D105 fixed-H-window resource-round composition.

Primary consumer: `TOOL002-I`

Normative language effect: **none**.

## Decision boundary

D076 already fixes the durable authority sequence:

```text
inert Requirement
        |
        v
scheduler admission / atomic reservation
        |
        v
environment-owned provider
        |
        v
attempt-private lease / capability
        |
        v
fresh Protos Process / RootActor
        |
        v
terminal cleanup / release
```

D098 fixes persisted provider identity:

```text
provider = mandatory logical identity
profile  = optional provider-scoped logical identity
```

and explicitly rejects treating either field as a Java class, Protos import,
executable, path, URI, package coordinate, secret, live provider instance or
capability.

I8A, I8B and I8C now close the generic scheduler side through:

- Requirement-to-CatalogEntry binding;
- static per-case feasibility;
- invocation-local atomic shared/exclusive reservation; and
- D105 fixed-H-window resource rounds.

D107 therefore decides the missing boundary between a successful scheduler
reservation and the fresh child Process:

```text
logical provider/profile
        |
        v
environment provider resolution
        |
        v
provider provisioning transaction
        |
        v
host-owned leases
        |
        v
attempt-private guest capability bundle
        |
        v
fresh Process
        |
        v
terminal cleanup
        |
        v
provider cleanup
        |
        v
scheduler reservation release
```

D107 does not define a public provider installation/distribution registry,
concrete built-in providers, a secret-store mechanism, physical worker topology,
retry/timeout/preemption, or cross-run distributed reservation.

## Selected contract — Candidate D′

The selected architecture is:

> **explicit environment provider registry + transport-neutral provider adapter
> boundary + attempt-level transactional provisioning + host-owned leases +
> immutable RootActor-only resource capability bundle + fail-closed cleanup**

The provider adapter abstraction is a mechanism boundary, not a persisted
provider identity and not a new language-level provider object.

## 1. Explicit environment provider registry

The execution environment owns one explicit mapping:

```text
logical provider id
        |
        v
configured ProviderAdapter
```

Resolution is exact and fail-closed.

The baseline contract permits no:

- classpath scanning;
- ServiceLoader-style ambient discovery;
- PATH lookup;
- executable-name search;
- Protos import-name interpretation;
- module-directory scanning;
- URI/package-source interpretation;
- resource-key inference;
- fallback provider chain;
- provider alias search.

`provider` and optional `profile` remain exactly the inert D098 identities.

A syntactically valid but unresolved provider is infrastructure/configuration
failure before child Process creation.

An unknown profile is likewise infrastructure/configuration failure.

## 2. Transport-neutral provider adapter boundary

D107 does not make Java the durable provider ABI.

Conceptually, the environment supplies:

```text
ProviderAdapter
    provision(...)
        -> ProviderLease
```

The first implementation may be an in-process Java adapter.

The same boundary must remain able to wrap later:

- a Protos-hosted implementation with separately granted privileged mechanisms;
- a native adapter;
- a local daemon;
- a separate process using a versioned RPC protocol;
- a hardened worker-side adapter; or
- a remote execution/provider service.

The concrete transport and implementation language are environment mechanism.

They are not encoded into catalog `provider` or `profile`.

Provider ABI/protocol generations may evolve independently from D098 persisted
identity.

## 3. Provider grouping per attempt

After I8B has reserved the complete Requirement set and the applicable placement
is known, the Test Tool groups the attempt's already-bound resource entries by
logical provider identity.

Each logical provider is invoked at most once for one attempt provisioning
transaction.

The provider receives all of that provider's bindings for the attempt in stable
first-appearance order.

Each binding retains the generic information already owned by D076/D077/D097/D098:

```text
resource key
shared/exclusive mode
units when shared
scope
provider
profile-or-absent
```

The provider may use provider-internal semantics for its own group.

It must not:

- reinterpret global TestPlan admission;
- create a second scheduler queue;
- change D097 scope meaning;
- acquire an unreserved extra Test Tool resource;
- rewrite the logical resource key; or
- infer missing provider/profile identity.

## 4. Attempt-level transactional provisioning

A fresh child Process does not start until provisioning for the attempt is
complete.

For multiple providers:

```text
reservation complete
        |
        v
provider A provision
        |
        v
provider B provision
        |
        v
provider C provision
        |
        +---- failure
                 |
                 v
        rollback partial C if needed
                 |
                 v
        cleanup B
                 |
                 v
        cleanup A
                 |
                 v
        attempt does not start
```

Successful provider leases are therefore provisional until every provider group
has provisioned successfully.

If a later provider fails, earlier successful providers are cleaned up in
reverse acquisition order.

No child guest code observes a partially provisioned attempt.

This is transactional coordination at the Test Tool boundary; it does not claim
that arbitrary external systems support distributed ACID rollback.

Each provider remains responsible for making its own failed/partial provisioning
cleanup safe and idempotent enough for the adapter contract selected by its
implementation.

## 5. Host-owned ProviderLease

A successful provider returns a host-owned lease.

Conceptually one lease owns:

```text
covered resource keys
guest capability values
provider-private live state
cleanup/revocation mechanism
```

The lease itself never enters the guest.

The child Process does not receive:

- the ProviderAdapter;
- provider registry;
- credentials;
- secret-store handle;
- host paths merely used by the provider;
- provider RPC/session implementation details;
- cleanup callback;
- scheduler reservation handle; or
- mutable cross-Process provider registry.

Lease custody remains outside the child through terminal attempt cleanup.

## 6. One guest capability per exact resource key

Every required D077 resource key receives exactly one guest-visible capability
value in the successful attempt capability bundle.

Examples:

```text
resources["gpu"]
resources["db/integration"]
resources["license/ansys"]
```

D107 does not prescribe the concrete capability prototype for every provider.

A provider may return:

- a real I/O/service/device capability;
- a provider-specific capability object;
- a composite capability; or
- an opaque authority token when the resource grants only scheduling/exclusion
  authority and no guest operation is needed.

Several logical resource keys may reference one shared underlying provider
session, but each required key still has one exact bundle entry.

A provider must not omit a required key silently.

Duplicate guest key production fails closed.

## 7. Immutable attempt capability bundle

Only after all providers succeed does the Test Tool construct one immutable
attempt resource bundle keyed by exact D077 resource key.

The conceptual guest surface is:

```protos
resources["gpu"]
resources["db/integration"]
```

The bundle:

- is created for one attempt only;
- is immutable/frozen before guest execution;
- grants no provider-discovery operation;
- grants no arbitrary acquisition operation;
- grants no authority beyond its supplied capability values;
- contains no ProviderAdapter or lease lifecycle control;
- does not expose provider/profile as a code-loading mechanism.

The initial RootActor module receives the bundle through bootstrap authority.

The bundle is not a Core global and is not ambient process-wide discovery.

Ordinary imports do not gain it merely by importing a module.

Non-root Actor bootstrap does not automatically receive a new copy.

Guest code may explicitly pass an individual capability onward using ordinary
Protos semantics when that capability itself permits such use.

## 8. RootActor-only bootstrap projection

D107 follows the existing explicit Process-authority pattern already used by
optional Filesystem and Network grants.

A resourceful attempt receives a local initial RootActor binding conceptually
named:

```text
resources
```

when and only when the attempt has successfully provisioned resources.

A resource-free attempt has no `resources` local.

This preserves pay-for-use semantics and avoids creating a permanently present
empty global resource registry.

The exact implementation carrier may extend the existing standalone Process
bootstrap or another equivalent explicit bootstrap descriptor, but it must
preserve the same authority boundary.

## 9. Resource-free zero-provider fast path

For a CaseSpec with zero resource requirements:

```text
registry lookup        = none
provider calls         = none
leases                 = none
resources bundle       = absent
provider cleanup       = none
```

The existing TOOL002-H/I8C resource-free execution path remains the semantic and
performance baseline.

D107 does not require every test execution to pay provider abstraction cost.

## 10. Lifecycle and custody order

The required lifecycle is:

```text
I8B atomic reservation
        |
        v
provider resolution
        |
        v
transactional provisioning
        |
        v
frozen attempt resources bundle
        |
        v
fresh Process / RootActor bootstrap
        |
        v
guest execution
        |
        v
terminal Process / attempt cleanup
        |
        v
ProviderLease cleanup in reverse acquisition order
        |
        v
I8B reservation release
```

A reservation must not be released merely because guest code produced a value
or Error.

It remains held through the already-ratified terminal cleanup boundary.

Provider cleanup precedes returning scheduler capacity.

## 11. Cleanup failure is fail-closed

A provider cleanup failure is infrastructure failure.

It does not become a fabricated guest Protos `Error`.

If the Test Tool cannot establish that a provisioned resource has been safely
cleaned/revoked, the corresponding scheduler capacity must **not** be silently
returned for reuse.

For the baseline local implementation, D107 selects fail-closed behavior:

```text
cleanup cannot be confirmed safe
        |
        v
do not release affected scheduler capacity
        |
        v
stop/abort further resourceful scheduling that could reuse unsafe capacity
        |
        v
report infrastructure failure
```

D107 does not invent a quarantine manager, health-recovery protocol, replacement
resource search or distributed reconciliation loop.

Those may be added later through an explicit decision.

Resource-free work is not thereby reclassified as resourceful, though overall
invocation-failure policy may choose to stop the run once terminal infrastructure
failure makes continued evidence unreliable.

## 12. Asynchronous-capable adapter semantics

The durable adapter contract must permit implementations whose provisioning or
cleanup complete asynchronously.

A local in-process provider may complete synchronously.

D107 does not require RPC/process overhead for such a provider.

The Test Tool coordinator owns awaiting provider operations before crossing the
next lifecycle boundary.

This prevents the initial Java implementation from freezing the architecture into
a synchronous-only ABI that would fit poorly with network services, remote
workers or external license/device systems.

## 13. Infrastructure outcomes remain separate

The following remain infrastructure/configuration evidence:

```text
unknown provider
unknown profile
provider unavailable
provisioning rejected
partial provisioning failure
provider transport failure
lease lost
cleanup/revocation failure
placement vanished
worker/provider endpoint disappeared
```

They are not automatically converted into the child Process's semantic Error
taxonomy.

If failure occurs before child creation, there is no guest outcome to fabricate.

If failure is discovered after/during execution, reporting retains distinct
infrastructure evidence alongside whatever guest outcome was already produced.

## 14. Provider identity stays independent of implementation evolution

The durable direction is:

```text
catalog:
provider = "device/gpu"
profile  = "a100"
        |
        v
environment registry
        |
        +--> adapter ABI/protocol generation
        +--> implementation/source/package/version
        +--> typed environment configuration
        +--> credential authority
        |
        v
configured provider implementation
```

Changes below the registry do not require rewriting Requirement records,
resource keys, D097 scope or D098 logical provider/profile identity.

## Comparative audit

The owner approval followed comparison across resource schedulers, test runners,
plugin systems, dependency-injection/lifecycle frameworks and capability
runtimes.

### Kubernetes DRA

DRA strongly supports separation between workload resource claims, scheduler
allocation, driver-side preparation, node/placement-sensitive materialization,
and unprepare/cleanup.

The key lesson for Protos is that allocation and usable authority are distinct
phases and cleanup must gate safe reuse.

D107 adopts that lifecycle split without importing Kubernetes API objects,
controllers, cluster control-plane machinery or CRDs.

### Kubernetes device plugins and CDI

Device plugins demonstrate scalable scheduler-visible device inventory plus a
post-allocation projection step.

Their env/device/mount/CDI-descriptor projection is useful for container
integration but is deliberately not the primary Protos guest contract: Protos
can deliver explicit capability values instead of requiring guest code to
reinterpret host descriptors.

### Nomad device/task plugins

Nomad demonstrates environment-owned plugins, scheduler-visible resource
capacity, post-placement reserve/setup and implementation isolation through
plugin processes.

D107 adopts scheduler/provider separation and permits a future RPC adapter, but
does not require an external plugin process for every local provider.

### Terraform and HashiCorp go-plugin

Terraform provides particularly strong evidence for separating persisted logical
provider identity/distribution concerns from a versioned runtime provider
protocol.

`go-plugin` demonstrates that a provider implementation can later move into a
separate process and negotiate a versioned RPC protocol without changing
higher-level logical identity.

D107 therefore keeps the adapter transport-neutral and keeps ABI generation below
D098 identity.

### Buck2 local resources

Buck2 demonstrates setup, pooling, reservation and cleanup of local test resources
without making the test description own the concrete implementation.

Its environment-string projection is less authority-safe than the selected
Protos capability-bundle approach, but its provider lifecycle supports D107's
separation.

### CTest resource allocation

CTest is strong evidence for reserving resources before child execution and
keeping resource specification separate from the test itself.

Its child environment-variable resource IDs are intentionally not copied:
Protos should provision usable capability values rather than requiring the guest
to interpret scheduler metadata into authority.

### Bazel toolchain/execution-platform resolution

Bazel demonstrates durable separation between a logical role/requirement and the
concrete implementation chosen for a specific execution environment.

D107 adopts that resolution principle without importing Bazel's complete
constraint/toolchain institution.

### pytest fixtures

pytest fixture injection and `yield` teardown demonstrate the value of explicit
acquisition paired with structured teardown outside the test body's direct
control.

D107 applies the lifecycle lesson at the resource-provider boundary rather than
making providers guest fixtures.

### JUnit extension resources

JUnit extension stores and closeable-resource patterns reinforce reverse-order
structured cleanup and lifecycle ownership by the framework/host rather than the
test code.

D107 similarly uses reverse acquisition order for successful provider leases.

### WASI / WebAssembly Component Model

WASI and the Component Model provide the strongest authority-model precedent.

Host resources are explicit handles/capabilities; components do not automatically
receive host filesystem/network/process authority.

Owned/borrowed resource-handle semantics demonstrate that guest-visible authority
can remain abstract from the host implementation.

D107 adopts explicit host-to-guest capability projection and rejects a guest
provider/service locator.

### Testcontainers

Testcontainers demonstrates why cleanup ownership should remain outside the code
under test and why abnormal termination needs an external cleanup authority.

D107 similarly keeps ProviderLease custody outside the child Process.

### OCI/CDI descriptors — contrasting precedent

OCI/CDI demonstrates that descriptor projection scales well for devices and
containers.

D107 keeps this as a valid provider-adapter implementation mechanism but does not
make descriptor strings/paths the generic Protos guest-facing authority model.

## Candidate comparison

Scores 1–5.

| Criterion | A Java table | B Protos modules | C external RPC | **D′ registry + adapter + lease transaction** | E descriptors | F guest manager | G ambient discovery |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Future resilience | 3.7 | 4.1 | **5.0** | **5.0** | 4.5 | 3.2 | 4.1 |
| Scalability | 4.4 | 4.0 | 4.8 | **4.9** | 4.8 | 3.5 | 4.5 |
| Protos philosophy | 4.0 | 4.8 | 4.2 | **5.0** | 2.8 | 1.2 | 2.6 |
| Correctness / authority safety | 4.2 | 4.0 | 4.5 | **5.0** | 3.6 | 2.0 | 2.8 |
| Isolation / security | 3.8 | 3.7 | **4.9** | **4.9** | 3.8 | 1.8 | 2.8 |
| Local fast-path cost | **5.0** | 4.4 | 2.5 | 4.8 | 4.6 | 4.0 | 4.2 |
| Distributed / remote evolution | 3.0 | 3.7 | **5.0** | **5.0** | 4.7 | 3.8 | 3.5 |
| Cleanup / failure semantics | 4.0 | 3.8 | 4.5 | **5.0** | 3.8 | 2.0 | 3.0 |
| Conceptual / implementation simplicity | **5.0** | 3.5 | 2.8 | 3.6 | 4.2 | 4.0 | 4.2 |
| Reversibility | 4.3 | 4.2 | 4.5 | **4.7** | 3.8 | 2.9 | 3.0 |
| **Total / 50** | **41.4** | **40.2** | **42.2** | **47.9** | **40.6** | **28.4** | **34.7** |

The issue-time qualitative scorecard rounded the complete D′ audit to 48.9/50
under the project's weighted evaluation. The exact arithmetic above is an
unweighted ten-axis view; both select D′ decisively.

Focused owner criteria:

| Candidate | Future resilience | Scalability | Protos philosophy |
| --- | ---: | ---: | ---: |
| A Java table | 3.7 | 4.4 | 4.0 |
| B Protos modules | 4.1 | 4.0 | 4.8 |
| C external RPC | **5.0** | 4.8 | 4.2 |
| **D′** | **5.0** | **4.9** | **5.0** |
| E descriptors | 4.5 | 4.8 | 2.8 |
| F guest manager | 3.2 | 3.5 | 1.2 |
| G ambient discovery | 4.1 | 4.5 | 2.6 |

## Why A is insufficient as the durable contract

A static Java provider interface is attractive for the first implementation and
may be used internally as the first D′ adapter.

It is not selected as the durable architecture because it would accidentally
equate:

```text
provider implementation == same-JVM Java object
```

A hardened worker, native provider, daemon or remote execution backend would then
need a parallel provider architecture.

D′ permits today's cheap Java implementation without freezing Java into
catalog/provider semantics.

## Why B is insufficient

Bundled Protos provider modules align superficially with language self-hosting,
but real providers often need privileged authority deliberately unavailable to
ordinary guest code:

- device APIs;
- host filesystem/device nodes;
- credentials;
- privileged daemons;
- external control-plane APIs.

Supplying enough ambient privilege to let a guest provider manufacture arbitrary
new guest capabilities would weaken the authority model.

A Protos implementation can still sit behind D′ when the environment explicitly
supplies the privileged adapter mechanisms it needs.

## Why C is not the baseline

External RPC providers have excellent isolation and remote evolution.

They also impose process/protocol/version/serialization/supervision cost on every
provider, including trivial local ones.

D107 therefore requires a boundary that **can** become RPC, not a boundary that
**must** be RPC.

## Why E is insufficient

Execution descriptors such as:

```text
environment variable
mount path
device path
URI
container runtime descriptor
```

scale well as backend mechanisms.

They are not a sufficiently strong generic guest authority model because guest
code must reinterpret strings/paths/IDs into usable authority.

D′ allows an adapter/backend to use those descriptors internally while still
presenting an explicit Protos capability value to the fresh Process.

## Why F is rejected

A guest `ResourceManager` or on-demand provider lookup would invert D076.

It would make resource acquisition occur after guest startup, reintroduce
hold-and-wait semantics, weaken scheduler visibility and create ambient authority.

The scheduler must continue to reserve the complete resource set before the
attempt starts.

## Why G is rejected

Ambient plugin discovery makes environment contents implicitly executable
configuration.

It also risks turning D098 provider IDs into class/module/executable lookup names.

Provider installation/distribution may gain an explicit future registry, but
resolution remains exact environment authority rather than scanning/search.

## Scalability

D′ keeps scheduler and provider complexity separable.

For one attempt:

- scheduler binding/reservation remains proportional to the attempt's resource
  set;
- provider resolution is exact registry lookup;
- provisioning groups bindings by provider;
- provider call count is bounded by distinct providers used by the attempt;
- cleanup count is bounded by successful provider groups;
- resource-free attempts perform no provider work.

At scale, physical provider implementations may move behind local daemon, worker
or remote adapters without changing TestPlan or catalog identity.

No global guest registry, plugin scan or per-case provider process is required by
the contract.

## Future resilience

D′ survives:

### Local same-JVM provider

Use an in-process Java adapter.

### Protos provider logic

Wrap Protos logic behind an explicitly configured adapter with only the
privileged host mechanisms it needs.

### Native provider

Wrap JNI/Panama/native daemon machinery below the adapter.

### Hardened OS worker

Resolve/provision inside or for the selected worker after placement.

### Remote worker

Transport the generic attempt/provider request to a worker-side adapter and
return a backend-appropriate guest capability projection.

### External service/license

Provider may manage credentials and lease/session state externally while the
guest receives only a safe capability.

### Provider replacement

Change the registry mapping for the same logical provider identity.

No Requirement/catalog migration is required merely because implementation
technology changed.

## Strongest argument against D′

D′ introduces `ProviderAdapter`, `ProviderLease`, transactional coordination and
a resource bundle before Protos has many real resource providers.

For a permanently local JVM-only Test Tool with one or two providers, a Java
interface and direct bootstrap grant would be simpler.

The mitigation is that D′ does **not** require a plugin framework, RPC protocol,
package registry or provider process now.

The first implementation can be almost as small as Candidate A while preserving
a boundary that does not make Java the durable semantic contract.

## Regret scenario

D′ would be over-designed if Protos permanently remained:

- local-only;
- JVM-only;
- single-machine;
- with very few resourceful tests; and
- with providers that never require asynchronous setup or complex cleanup.

In that world the adapter/lease layer would add concepts with little visible
benefit.

The opposite regret is substantially larger: selecting direct Java/provider
objects or env-descriptor semantics now and later having to migrate provider
resolution, guest authority, cleanup and remote execution at the same time.

## Escape path

If the adapter architecture later proves unnecessarily broad, a local environment
may standardize one direct in-process adapter implementation while keeping the
same D107 contract.

If remote/distributed providers later need stronger protocol semantics, add a
versioned adapter transport/registry decision below D107 without changing:

- Requirement;
- D077 key;
- D097 scope;
- D098 provider/profile;
- I8A binding;
- I8B reservation; or
- guest `resources` capability lookup.

If provider cleanup needs recovery/quarantine, add that as an explicit lifecycle
extension rather than weakening D107's fail-closed reuse rule.

## Intentionally deferred

D107 does not decide:

- public provider package/distribution registry format;
- concrete built-in provider set;
- secret-store implementation;
- credential-source configuration format;
- physical worker topology;
- public provider installation commands;
- exact Java interface names/signatures;
- exact RPC protocol;
- exact provider ABI version encoding;
- cross-run/global distributed reservation protocol;
- quarantine/recovery manager;
- retry/replay;
- timeout/kill/preemption;
- provider health-check institution;
- general-purpose language-level dependency injection.

## Ratification effect

D107 ratification is governance/design only.

It changes no:

- executable implementation;
- Protos specification;
- Maven implementation version;
- D069 jobs semantics;
- D076 reservation semantics;
- D077/D097/D098 persisted representation;
- I8A/I8B/I8C implementation;
- native boundary.

After publication, TOOL002-I may continue with bounded implementation of the D107
provider registry/transaction/lease/capability-delivery boundary before public
`Main.protos` resourceful execution is wired.
