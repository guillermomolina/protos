# D113 — Test Tool production provider-registry integration contract

Status: **RATIFIED — Candidate B′ selected**

Allocated: **2026-09-12**

Explicit project-owner approval: **2026-09-12**

Decision issue: GitHub #439

Nature: implementation-independent Test Tool provider-registry/bootstrap authority contract

Triggered by: `TOOL002-I8D4C3` closure after D107/D108 private resource execution composition.

Primary consumer: `TOOL002-I`

Normative language effect: **none**.

## Decision boundary

D098 and D107 already separate durable logical resource/provider identity from provider implementation:

```text
catalog provider/profile
        |
        v
logical inert identity
        |
        v
environment registry
        |
        v
configured ProviderAdapter
        |
        v
attempt-private ProviderLease/capabilities
```

I8D1-I8D4C3 implement the private mechanics beneath that boundary, but public `protos test`
still lacks one ratified production rule for how an invocation obtains the actual environment
registry used by resourceful attempts.

D113 decides that production composition boundary.

D113 does not define provider-specific configuration schemas, a provider package/distribution
system, a secret-store product, retry/quarantine/reconciliation, resource-catalog syntax, or
remote worker fault-scope policy.

## Selected contract — Candidate B′

The selected architecture is:

> **explicit immutable host-bootstrap provider registry injection, with an empty registry as a
> valid/default environment and no ambient provider discovery**

One Test Tool invocation receives one environment-owned provider registry assembled outside
ordinary bundled-Protos tool code.

Conceptually:

```text
host / embedding environment
        |
        v
explicit configured registrations
        |
        v
immutable invocation registry
provider-id -> configured transport-neutral adapter
        |
        v
D107 resolution/provisioning
```

## 1. Registry ownership belongs to the host/bootstrap environment

The production Test Tool registry is assembled by the host/bootstrap environment before
resourceful execution needs provider resolution.

The ordinary bundled Protos Test Tool does not discover provider implementations.

The durable ownership rule is:

```text
host/environment owns provider composition
bundled Protos owns Test Tool policy
guest Process receives only attempt-private capabilities
```

This preserves the authority split already established by D076/D098/D107.

## 2. Registry is explicit and invocation-scoped

One invocation observes one explicit mapping from exact D098 provider identity to configured
adapter.

Conceptually:

```text
EnvironmentProviderRegistry
    "device/gpu"        -> configured adapter A
    "service/database"  -> configured adapter B
```

The mapping is immutable for the invocation after Test Tool execution begins.

D113 introduces no mid-run provider registration or mutation protocol.

A future long-lived daemon/remote worker may construct a fresh registry for each logical
invocation or immutable worker execution environment without changing D098 identity.

## 3. Empty registry is valid and is the baseline default

A resource-free invocation does not require provider configuration.

Therefore:

```text
no configured providers
        |
        v
empty immutable registry
```

is valid.

The healthy resource-free fast path remains pay-for-use:

```text
registry lookup       none
provider call         none
provider lease        none
resources local       absent
```

A resourceful CaseSpec whose exact provider is absent from the registry fails as D107
infrastructure/configuration evidence before guest Process creation.

## 4. No ambient provider discovery

The baseline production contract permits no automatic:

- classpath scanning;
- ServiceLoader registration;
- plugin-directory scanning;
- PATH/executable search;
- Protos import/module scanning;
- resource-catalog-driven code loading;
- URI/package-coordinate interpretation;
- environment-wide provider alias search;
- resource-key-to-provider inference;
- fallback provider chain.

An embedding application may explicitly construct registrations using whatever private
mechanisms it controls, but the Test Tool receives the already-composed registry rather than
performing discovery itself.

## 5. Provider/profile remain inert persisted identities

D098 remains unchanged.

Neither `provider` nor `profile` names:

- a Java class;
- a Protos module/import;
- an executable;
- a filesystem path;
- a URL/URI;
- a package coordinate;
- a transport endpoint;
- a secret;
- credentials;
- a ProviderAdapter instance; or
- a live capability.

Persisted catalog identity is independent from implementation/distribution evolution.

## 6. Implementation, transport and version remain below the registry

A logical provider registration may currently wrap an in-process Java adapter.

The same registry abstraction must remain capable of explicitly registering later:

- a native adapter;
- a privileged Protos-hosted implementation;
- a local daemon;
- a separate-process adapter using versioned RPC;
- a worker-local adapter; or
- a remote provider service.

These mechanisms do not rewrite catalog `provider` or `profile`.

Provider implementation version and adapter/protocol generation are host/environment concerns.

## 7. Provider-scoped profile resolution remains environment-owned

An optional D098 `profile` is interpreted only by the configured logical provider.

Conceptually:

```text
provider = "device/gpu"
profile  = "a100"
        |
        v
registry["device/gpu"]
        |
        v
configured provider resolves profile "a100"
```

The profile does not select implementation code.

Unknown profile is infrastructure/configuration failure.

The exact storage format for provider-specific profile configuration is outside D113 provided it
does not leak implementation identity, credentials or hidden discovery into the resource catalog.

## 8. Configuration, endpoints and secrets remain host authority

Provider-private material remains outside ordinary Test Tool/guest state, including:

- credentials and tokens;
- secret-store handles;
- host paths used only by the adapter;
- sockets/endpoints;
- implementation package/version;
- RPC/session details;
- cleanup authority.

D113 does not require any particular secret store or config file format.

An adapter may receive typed configuration from the host that constructed it.

Only D107's attempt-private capability result crosses into the child Process.

## 9. Exact lookup and fail-closed behavior

Provider resolution is exact.

For one binding group:

```text
logical provider id
        |
        +-- exact registration exists --> provision
        |
        +-- absent ---------------------> infrastructure/configuration failure
```

No fallback or closest-match behavior is permitted.

This retains D107's deterministic provider resolution and avoids accidental authority widening.

## 10. Public CLI syntax for provider installation/configuration is not created here

D113 selects the ownership/injection contract, not a provider-management product.

The baseline local `protos test` may therefore run with the empty registry until concrete providers
are intentionally introduced by later bounded implementation/configuration work.

A future explicit provider-install/config mechanism may feed the host registry without changing
this contract.

Any future mechanism that proposes ambient discovery requires a new decision gate.

## 11. Remote/distributed evolution

D113 deliberately makes logical provider identity portable across execution environments.

Example:

```text
catalog:
    provider = "device/gpu"

developer workstation:
    "device/gpu" -> local simulator adapter

CI worker:
    "device/gpu" -> worker-local hardware adapter

remote execution:
    "device/gpu" -> remote provider client
```

The CaseSpec/catalog identity remains stable.

This is the principal future-proofing reason for selecting B′ over a fixed bundled table.

## 12. Relationship to Main/Runner

Public Test Tool wiring may install the already-implemented private resource execution facilities
using the invocation registry supplied by the host bootstrap.

Bundled Protos Runner/Main must not gain:

- ProviderAdapter objects;
- provider registry mutation;
- provider discovery;
- credentials;
- provider implementation loading.

The Protos layer receives only the resource-execution closures/capabilities intentionally exposed
by the host integration boundary.

## Comparative audit

The owner approval followed an exhaustive comparison across resource schedulers, provider/plugin
systems, test runners, CI environments and capability runtimes.

### Kubernetes DRA

DRA separates workload resource declarations/allocation from driver-side preparation and
unpreparation owned by the execution environment.

The strongest lesson for D113 is that logical requested resources do not encode the concrete
driver implementation or credentials used by the node environment.

D113 adopts that separation without importing Kubernetes registration/controllers.

### Kubernetes device plugins

Device plugins register with kubelet and remain node/environment infrastructure rather than
workload-owned provider discovery.

The registration model scales well, but D113 avoids making runtime registration/discovery itself
part of the Test Tool contract.

### Nomad plugins

Nomad demonstrates strong environment ownership and scalable plugin isolation.

Its plugin-directory/executable discovery is useful operationally but is intentionally rejected as
an ambient authority mechanism for baseline Protos.

### Terraform providers

Terraform strongly demonstrates durable separation among logical provider identity, installation,
version selection, lock state and configured provider instances.

D113 adopts that identity/implementation split while refusing provider source/package identity in
D098 catalog fields.

### Bazel toolchains/execution platforms

Bazel's explicit toolchain registration plus deterministic resolution is strong evidence that
large systems can scale without requiring uncontrolled ambient discovery at the consumer boundary.

D113 similarly chooses explicit environment composition before resolution.

### Buck2 local resources

Buck2 local resources reinforce host/orchestrator ownership of setup, reservation and cleanup.

D113 retains provider implementation outside test descriptions and guest code.

### pytest and JUnit extensions

pytest entry points/conftest/plugin discovery and JUnit ServiceLoader auto-registration illustrate
the convenience of ambient extension discovery.

JUnit's ability to disable auto-registration and register engines explicitly is closer to D113.

These systems demonstrate that discovery is useful UX but not required for extensibility; Protos
chooses explicit authority first.

### Jenkins and GitHub Actions runners

Runner/agent capabilities are owned/configured by the execution environment; jobs request logical
labels/capabilities rather than loading implementation code from the job description.

This supports D113's environment-owned registry direction.

### WASI / WebAssembly Component Model / Wasmtime

Capability runtimes provide the strongest Protos-philosophy precedent: the host explicitly
composes imports/resources and the guest receives only granted handles/capabilities.

D113 adopts explicit host composition and rejects a guest-visible service locator.

## Candidate comparison

Focused scores are 1–10.

| Candidate | Future resilience | Scalability | Protos philosophy |
| --- | ---: | ---: | ---: |
| A — fixed bundled registry | 6.0 | 6.0 | 8.5 |
| B — explicit host registry injection | 10.0 | 9.5 | 10.0 |
| C — Protos-tool-owned registry | 7.5 | 7.0 | 5.0 |
| D — plugin/service discovery | 8.5 | 9.5 | 3.0 |
| E — fixed core + explicit extensions | 9.5 | 9.5 | 8.5 |
| **B′ — explicit immutable host registry; empty default** | **10.0** | **9.5** | **10.0** |

## Why B′ is selected

B′ keeps authority composition explicit while preserving transport and implementation freedom.

It avoids two long-term traps:

1. freezing provider identity to today's Java implementation; and
2. turning plugin discovery into ambient authority that the Test Tool must scan and trust.

It also avoids a permanent distinction between "built-in" and "extension" providers: both are
explicit registrations below the same logical identity boundary.

## Strongest argument against B′

B′ does not itself solve provider-installation UX.

A plugin-discovery system can feel easier for users because installation automatically makes a
provider visible.

D113 treats that as a later tooling/distribution concern. A future package/configuration mechanism
can explicitly construct registrations without weakening the authority model.

## Regret scenario and escape path

If future distributed deployments require dynamic provider availability, the registry abstraction
can be constructed by a worker/daemon control plane from explicit authenticated configuration.

If truly dynamic discovery becomes necessary, a later decision can add a controlled registry
population protocol below this boundary.

Neither evolution requires rewriting D098 catalog identity or D107 capability semantics.

## Ratified outcome

D113 ratifies:

```text
D113 = Candidate B′

explicit immutable host-bootstrap provider registry injection
empty registry is valid/default
exact logical provider lookup
no ambient discovery
implementation/configuration/secrets remain host-owned
resource-free fast path remains zero-provider/pay-for-use
```

After publication, TOOL002-I may implement the bounded production provider-registry bootstrap
wiring together with the independently ratified D114 public outcome/reporting contract.
