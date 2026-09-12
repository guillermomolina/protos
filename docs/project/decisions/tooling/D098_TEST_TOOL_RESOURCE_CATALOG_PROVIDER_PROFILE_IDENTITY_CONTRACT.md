# D098 — Test Tool resource catalog provider/profile identity contract

Status: **RATIFIED — Candidate A′ selected**

Allocated: **2026-09-12**

Explicit project-owner approval: **2026-09-12**

Decision issue: GitHub #393

Primary consumer: `TOOL002-I` / GitHub #96

Predecessors: `D076`, `D077`, `D097` — RATIFIED

Nature: implementation-independent Test Tool resource-catalog provider identity contract

Normative language effect: **none**.

## Decision boundary

D076 already fixes the authority sequence:

```text
inert Requirement
        |
        v
scheduler admission / atomic reservation
        |
        v
environment catalog + placement
        |
        v
provider resolution / provisioning
        |
        v
attempt-private capability
        |
        v
fresh Process
        |
        v
terminal cleanup / release
```

D077 selects a strict, versioned TOML resource catalog with inert provider/profile
selection identity, but deliberately leaves the exact provider/profile identity
contract unresolved.

D097 then fixes catalog-v1 `scope` to:

```text
placement
run
```

strictly as the scheduler-visible capacity accounting / reservation domain.

D098 closes the next catalog boundary: the smallest durable identity contract by
which persisted catalog data selects a logical provider and, optionally, one
provider-scoped profile without coupling persisted configuration to a concrete
implementation mechanism.

D098 does **not** select a provider implementation API, Java/Protos ABI,
discovery/loading mechanism, package source, registry protocol, concrete built-in
provider set, catalog physical filename, public CLI spelling, multiple-catalog
merge policy, worker topology, remote transport, fairness, retry, timeout/kill,
sharding, cross-run global reservation authority or public Protos semantics.

## Selected contract — Candidate A′

For `resource-catalog-version = 1`, every resource record has:

```text
provider = mandatory
profile  = optional
```

Both values are **inert logical identities**.

Example:

```toml
[[resource]]
key = "gpu"
capacity = 4
scope = "placement"
provider = "device/gpu"
profile = "a100"
```

A profile is omitted when no additional provider-owned configuration identity is
needed:

```toml
[[resource]]
key = "license/ansys"
capacity = 20
scope = "run"
provider = "license/flexlm"
```

The persisted record selects logical identities only. It does not name or embed
the concrete implementation.

## Provider identity

`provider` is mandatory for every v1 catalog resource.

The provider identity:

- is explicit;
- is never inferred from `resource.key`;
- identifies a logical provider family / provisioning role;
- remains stable when the concrete provider implementation changes;
- is ordinary inert persisted data;
- grants no authority by itself.

A resource key and provider therefore remain distinct axes.

For example:

```text
resource key: gpu
provider:     device/gpu
```

does not imply that all `gpu` resources must forever use that provider, and the
provider name does not redefine the resource key.

The same logical resource identity may therefore survive provider replacement
without rewriting TestPlan requirements or the Requirement-to-catalog join.

## Profile identity

`profile` is optional.

When present, it is exactly one inert logical identity scoped to the selected
provider.

Conceptually:

```text
(provider, profile-or-absent)
```

is the provider-resolution selector.

`profile` is not a second global provider namespace.

Thus:

```text
provider = "device/gpu"
profile  = "a100"
```

and:

```text
provider = "simulator"
profile  = "a100"
```

may legally use the same lexical profile spelling while denoting unrelated
provider-owned profile identities.

Absence is a real state. The parser/scheduler must not silently invent a default
profile string, derive one from the resource key, or rewrite absence into an
implementation-specific alias.

## Canonical lexical form

`provider` and `profile` reuse the canonical hierarchical logical-identity grammar
already selected for v1 resource keys:

```text
segment[/segment...]
```

They nevertheless occupy **distinct semantic namespaces** from resource keys and
from each other.

The v1 contract therefore keeps the same canonical lexical properties already
used by D077 logical resource identity:

- lower-case ASCII canonical spelling;
- no whitespace;
- no empty path segments;
- no leading or trailing slash;
- no repeated separator;
- no path traversal semantics;
- no platform path semantics.

Reusing one lexical grammar is a syntax/validation convenience only. It does not
make provider IDs, profile IDs and resource keys interchangeable.

## What provider/profile are not

Neither `provider` nor `profile` is:

- a Java class name;
- a Java service/provider class;
- a Protos import name;
- a Protos module coordinate;
- an executable path;
- a shared-library path;
- an OCI image reference;
- a URL or URI;
- a package coordinate;
- a registry download address;
- a host/device handle;
- a worker identity;
- a secret or credential;
- a live provider instance;
- a capability.

Persisted catalog data therefore remains inert and cannot become implicit
code-loading or ambient-authority configuration merely by changing the provider
string.

## No arbitrary provider-specific configuration map in catalog v1

Catalog v1 deliberately does **not** add an arbitrary provider-owned map such as:

```toml
[resource.provider-config]
endpoint = "..."
driver = "..."
token = "..."
vendorField = "..."
```

Such a map would turn the generic catalog into a container for independently
versioned provider schemas before TOOL002 has a concrete need for them.

It would also make generic validation weaker and increase migration, secret
handling and portability risk.

When a provider needs richer configuration, that configuration belongs to a
separate environment/provider configuration authority selected by the logical
provider/profile identity.

A future version may define a typed/versioned provider-configuration institution
if real providers demonstrate the need. D098 intentionally preserves that option
without paying for it in catalog v1.

## Secrets and credentials

Secrets, credentials and live authority remain outside the persisted catalog.

In particular, neither provider/profile identity nor any future mapping implied
by D098 authorizes storing:

```text
password
token
private key
cloud credential
database credential
live session
open file/socket/device handle
provider instance
capability
```

inside the v1 catalog record.

Environment-specific provider resolution may acquire such authority through a
separate secure mechanism, but that is not selected by D098.

## Scheduler ownership

The scheduler **transports** provider/profile identities but does not interpret
their provider-internal semantics.

The scheduler continues to own only the generic information required before
provisioning, including the already-selected resource key, units/mode, capacity
and D097 scope/placement contract.

The boundary is:

```text
Requirement key/mode/units
        |
        v
catalog key/capacity/scope/provider/profile
        |
        v
scheduler validates placement + performs atomic reservation
        |
        v
provider resolution receives provider/profile
        |
        v
concrete provider provisions attempt-private capability
```

The provider must not create a hidden second scheduler or reinterpret D097 scope.

Provider/profile resolution occurs only after the generic scheduling/reservation
boundary has enough information to make its decision.

## Provider registry / implementation mapping

D098 does not require a concrete registry today.

It deliberately permits a future environment mapping such as:

```text
logical provider id
        |
        v
provider registry
        |
        +--> source / package
        +--> version
        +--> ABI generation
        +--> implementation
        +--> environment configuration
```

without changing the persisted logical provider identity.

That separation is the main future-compatibility property of Candidate A′.

The implementation may later be Java, Protos, native code, a local daemon, a
remote service adapter or another mechanism without changing the catalog schema
solely because the implementation technology changed.

## Failure behavior

Catalog-v1 validation fails closed.

### Lexically invalid identity

Malformed provider/profile identity is a catalog schema/configuration failure.

### Unknown provider

A syntactically valid but unresolved/unsupported provider fails as
**infrastructure/configuration** before capability provisioning.

It does not:

- fall back to another provider;
- infer a provider from `resource.key`;
- search executable paths;
- become a guest Protos `Error`.

### Unknown profile

A syntactically valid profile unknown to the resolved provider likewise fails
closed as infrastructure/configuration failure.

There is no profile alias/fallback/search chain selected by D098.

### Provider unavailable after resolution

Concrete provider availability/provisioning failure remains infrastructure
evidence under the D076 provider boundary. D098 does not redefine it as guest
language failure.

## Comparative prior art

The owner decision follows the exhaustive D098 issue audit across Kubernetes DRA
and CSI-style driver separation, Nomad, Buck2, Bazel, CTest, Slurm, Terraform,
CNI, Docker and Jenkins.

The durable pattern is not any one ecosystem's syntax. It is separation between:

```text
logical provider identity
provider configuration/profile
concrete implementation
credentials/live authority
```

### Kubernetes DRA / CSI

Kubernetes provides strong evidence that explicit stable driver identity scales
well and that scheduler-visible resource inventory can remain distinct from
driver-specific configuration and live preparation.

D098 adopts the explicit logical identity and separation, but does not import
Kubernetes' complete driver/class/claim institutions or arbitrary opaque
per-driver configuration blobs into catalog v1.

### Nomad

Nomad reinforces keeping plugin/provider implementation and configuration in the
execution environment while jobs request logical resources and the scheduler
works from inert advertised inventory.

This strongly supports keeping persisted catalog records independent from plugin
binary/class identity.

### Buck2

Buck2 local resources reinforce the distinction between a logical configured
resource role and the setup/implementation machinery that supplies concrete
resources to tests.

D098 does not copy Buck/Bazel target labels into the public catalog identity.

### Bazel

Bazel toolchain resolution is strong evidence for separating a logical role from
the concrete implementation selected for an execution environment.

D098 keeps that separation but deliberately avoids importing Bazel's full
platform/toolchain constraint institution.

### Terraform

Terraform gives especially useful evidence for separating provider identity,
provider configuration/alias and the implementation/distribution source.

D098 adopts the identity/configuration distinction while rejecting registry
source/version/package coordinates as persisted Test Tool provider identity.

### CTest

CTest demonstrates that a scheduler can treat resource identity/capacity as
opaque, but it has no equivalent provider-to-attempt-private-capability layer.

That makes it useful evidence for scheduler opacity, but not sufficient as the
complete Protos architecture because D076 deliberately selected explicit provider
provisioning.

### Slurm

Slurm GRES and license mechanisms demonstrate that logical scheduler resource
identity can remain stable while backend/device/plugin integration differs by
environment.

This supports avoiding implementation names in durable provider identity.

### Docker and Jenkins

Docker's runtime/driver configuration and Jenkins' plugin/agent/resource
institutions reinforce that environment implementation choices should not be
collapsed into the logical identity consumed by higher-level scheduling policy.

### CNI — negative precedent

CNI is intentionally not copied.

Its `type` commonly names the concrete plugin executable while generic config
carries arbitrary plugin-specific fields.

That is flexible, but it couples persisted configuration directly to executable
implementation and creates an open-ended provider-owned schema surface.

D098 chooses the opposite default for catalog v1.

## Candidate comparison

Scores are 1–5.

| Candidate | Future resilience | Scalability | Protos philosophy |
| --- | ---: | ---: | ---: |
| **A′ — logical provider + optional profile** | **4.9** | **5.0** | **5.0** |
| Provider global / implementation-source style | 4.9 | 5.0 | 3.7 |
| Infer provider from resource key | 2.5 | 3.5 | 3.8 |
| Persist class/import/executable/URI | 2.5 | 4.0 | 2.5 |
| Arbitrary inline provider config map | 5.0 | 4.5 | 2.5 |
| Provider + profile both mandatory | 4.7 | 5.0 | 4.2 |

The refined full D098 scorecard records Candidate A′ at **49.5 / 50**.

The decisive strengths are:

- explicitness without implementation coupling;
- provider replacement without requirement migration;
- low-cost parsing/indexing/serialization;
- clean scheduler/provider authority boundary;
- no ambient code-loading semantics;
- no provider-specific schema explosion in v1;
- cheap future addition of a registry/source/version/ABI layer.

## Stress scenarios

### Local GPU

```toml
key = "gpu"
scope = "placement"
provider = "device/gpu"
profile = "a100"
```

The scheduler accounts for placement capacity. The environment resolves
`device/gpu` + `a100` only after placement/reservation and provisions the
attempt-private capability.

No Java class, NVML path or device node appears in the persisted identity.

### Remote worker

A remote execution environment may map the same provider identity to a different
implementation/package/ABI while retaining the same catalog identity.

D098 therefore does not force local-vs-remote implementation technology into the
catalog.

### External service / license

```toml
key = "license/ansys"
scope = "run"
provider = "license/flexlm"
profile = "ansys"
```

The provider may eventually talk to an external service. Physical externality and
credentials remain provider/environment concerns rather than new scheduler
semantics.

### Simulator

Several simulator generations/configurations can share:

```text
provider = simulator
```

and use provider-scoped profiles without fragmenting resource keys or exposing
concrete launcher commands.

### Provider replacement

An environment can migrate:

```text
logical provider: device/gpu
implementation A -> implementation B
```

without rewriting Requirements, resource keys, capacities, D097 scope or catalog
provider identity.

### Persisted-config migration

If a future provider ecosystem adds source/package/version/ABI metadata, that
metadata can live in a provider registry/configuration generation keyed by the
existing logical provider ID.

The v1 catalog need not change merely because installation/distribution becomes
more sophisticated.

### Unknown provider/profile

Unknown values fail before provisioning as infrastructure/configuration evidence.
There is no hidden fallback or guest-visible semantic change.

### Secrets

A provider needing authentication obtains credentials outside the v1 catalog.
The profile identity may select a logical configured environment, but it is not
itself a credential container.

## Strongest regret scenario

The strongest future-regret case is a mature Protos provider ecosystem with:

```text
global provider registry
package/source coordinates
provider versions
ABI compatibility generations
typed provider configuration
several named configured instances
```

Candidate A′ remains compatible with that future because the current provider
field names only the durable logical provider identity.

The extension path is:

```text
catalog logical provider id
        |
        v
provider registry/config generation
        |
        +--> implementation source/package/version
        +--> ABI
        +--> typed configuration
        +--> credential authority
        |
        v
configured provider implementation
```

Existing Requirement records, resource keys, capacity, D097 scope and catalog-v1
provider/profile identities do not need reinterpretation.

## Rejected alternatives

### Infer provider from resource key

Rejected because it conflates resource identity with provisioning mechanism and
makes provider replacement either a resource rename or a hidden convention.

### Persist concrete implementation reference

Rejected because class/module/path/URI/package identities would freeze loading,
distribution and security policy into the durable catalog.

### Arbitrary inline provider-specific map

Rejected for v1 because it immediately creates provider-owned nested schemas,
migration/versioning burden and a high-risk place for credentials/endpoints.

### Provider identity only, no profile

Rejected because multiple configurations of one logical provider are common
enough that omitting the already-anticipated profile axis would cause provider-ID
or resource-key fragmentation.

### Provider/profile both mandatory

Rejected because not every provider needs a separate configured profile. Forcing
one creates meaningless synthetic identifiers.

### Combined provider/profile string

Rejected because it erases the structural distinction between provider family and
provider-scoped configuration identity.

## Intentionally deferred

D098 does not select:

- provider implementation API;
- Java, Protos or native provider ABI;
- provider discovery/loading mechanism;
- registry wire/storage format;
- source/package/version resolution;
- concrete built-in providers;
- typed provider configuration schema;
- credential acquisition mechanism;
- catalog physical filename;
- public catalog CLI option;
- multiple-catalog merge/inheritance;
- worker/host/node topology;
- topology selectors;
- fairness/priority/starvation;
- retries;
- timeout/kill;
- sharding;
- remote transport/CAS;
- cross-run/global reservation authority;
- public Protos language semantics.

Any substantive choice among those remains subject to a later explicit decision
when implementation reaches that boundary.

## Ratification effect

D098 ratification is governance/tooling only.

It changes no:

- executable Test Tool or runtime implementation;
- Protos specification;
- grammar;
- Maven implementation version;
- native boundary;
- D069 jobs contract;
- D076 reservation/provider authority sequence;
- D097 scope semantics;
- current `TOOL002-I6E` requirements-sidecar implementation scope.

`TOOL002-I6E` remains the next executable slice already released by D094.

D098 instead removes the provider/profile identity ambiguity for the later
resource-catalog parser/validation work. That later implementation may rely on
the explicit mandatory-provider / optional-profile contract, but must not invent
the still-deferred provider API, registry, loading or configuration architecture.
