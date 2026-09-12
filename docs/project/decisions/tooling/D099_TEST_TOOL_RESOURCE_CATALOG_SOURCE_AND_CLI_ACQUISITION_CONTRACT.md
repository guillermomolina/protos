# D099 — Test Tool resource catalog source and CLI acquisition contract

Status: **RATIFIED — Candidate A′ refined selected**

Allocated: **2026-09-12**

Explicit project-owner approval: **2026-09-12**

Decision issue: GitHub #414

Primary consumer: `TOOL002-I` / GitHub #96

Predecessors: `D076`, `D077`, `D094`, `D097`, `D098` — RATIFIED

Nature: implementation-independent Test Tool resource-catalog acquisition contract

Normative language effect: **none**.

## Decision boundary

D076 and D077 already separate corpus requirements from the execution
environment's resource catalog:

```text
corpus / repository
        |
        v
inert CaseSpec requirements

execution environment
        |
        v
resource catalog
        |
        v
scheduler admission / atomic reservation
        |
        v
provider resolution / provisioning
        |
        v
attempt-private capability
```

D077 fixes the durable source-cardinality and precedence invariant:

```text
zero or one explicit catalog source per Test Tool invocation
```

and rejects implicit catalog merge, user-home/system fallback, environment-variable
fallback, parent-directory search, repository-local automatic catalog, profile
inheritance and last-wins precedence.

D097 fixes catalog-v1 `scope` to `placement | run`.

D098 fixes one mandatory inert logical `provider` identity and an optional
provider-scoped inert `profile` identity.

D099 closes the remaining v1 acquisition boundary: how the initial local
`protos test` invocation selects and acquires the zero-or-one environment catalog
without making its host location part of durable resource identity.

D099 does **not** implement catalog parsing, resource reservation, provider
loading, remote transport or worker topology. It selects only the public
acquisition contract that later bounded implementation may materialize.

## Selected contract — Candidate A′ refined

The v1 public acquisition form is:

```text
protos test [--resource-catalog PATH] ...
```

`--resource-catalog` names the durable concept — the resource catalog — rather
than the v1 transport mechanism.

The option has cardinality:

```text
0 occurrences -> D077 empty catalog
1 occurrence  -> acquire exactly that catalog
2+ occurrences -> CLI/configuration error
```

There is no first-wins, last-wins or merge behavior.

No short option is selected in v1.

## V1 source kind

In v1, `PATH` is one **explicit local filesystem path** supplied by the
invocation operator.

D099 does not interpret the argument as a URI, generic source locator, registry
identifier, CAS digest, stdin marker or executable/generator expression.

A later decision may add a distinct managed/remote source kind if a real
distributed deployment requires one. That future source must enter through the
acquisition boundary and produce the same inert catalog model; it must not
retroactively reinterpret v1 `PATH` strings as a URI mini-language.

## Path interpretation

An absolute path is used as the explicit host path supplied by the operator.

A relative path is interpreted relative to the Test Tool invocation working
directory.

It is **not** interpreted relative to:

- the test corpus root;
- `manifest.tsv`;
- `resource-requirements.toml`;
- a package root;
- a repository root discovered by search;
- a user-home directory; or
- any future remote worker filesystem.

Ordinary operating-system path resolution may resolve an explicitly supplied
symbolic link. The final selected object must be a readable regular file.

D099 does not create a symlink-search institution, follow links discovered from
the corpus, or make the resolved host path part of catalog identity.

## File acquisition and failure behavior

The selected path must resolve/open as one readable regular file.

These conditions fail closed as infrastructure/configuration failure before
resource scheduling:

- missing path;
- directory;
- device or other non-regular object;
- unreadable file;
- open failure;
- read failure;
- subsequent TOML/schema validation failure.

A present-but-invalid source is never reinterpreted as "no catalog".

The file is acquired once at the invocation/configuration boundary. Later schema
parsing remains governed by D077/D097/D098 and subsequent bounded implementation.

## No source

If `--resource-catalog` is absent, the invocation has the already-ratified D077
empty resource catalog.

This preserves the pay-only-for-use path for resource-free test corpora.

Absence does **not** disable resource admission semantics.

If a CaseSpec has a valid resource requirement and the empty catalog cannot
satisfy it, that case fails through the D076 infrastructure/configuration plane
before launch. The Test Tool must not execute the case as though resource
allocation were disabled.

## Ownership boundary

The acquisition authority remains environment/invocation-owned.

The corpus must not select the environment catalog through:

- `manifest.tsv`;
- `resource-requirements.toml`;
- another corpus sidecar;
- a package manifest;
- a repository convention; or
- an executable Protos callback.

The D094 corpus sidecar and the D099 environment catalog therefore remain
orthogonal:

```text
corpus owns intrinsic requirements
environment owns available-resource catalog
```

## No implicit discovery or precedence

Catalog v1 has no:

- canonical/default catalog filename;
- cwd search;
- parent-directory search;
- repository-root search;
- user-home search;
- system configuration path;
- environment-variable fallback;
- implicit CI variable;
- directory expansion;
- multiple-source merge;
- profile inheritance;
- stdin source;
- URL/URI fetch;
- network configuration service;
- generated/executable source.

This preserves D077's zero-or-one explicit-source invariant and avoids creating a
configuration-precedence institution that the resource model does not need.

## Acquisition result and durable identity

The host path is only an acquisition adapter.

The intended boundary is:

```text
explicit PATH
     |
     v
read exact catalog content once
     |
     v
D077 / D097 / D098 parse + validation
     |
     v
inert validated catalog snapshot
     |
     v
scheduler / future coordinator
```

The host path is **not**:

- catalog identity;
- Requirement identity;
- `resource.key`;
- `provider`;
- `profile`;
- reservation identity;
- a TestPlan or CaseSpec field;
- guest Process state;
- a capability;
- a remote-worker path requirement.

Future remote workers must not be required to rediscover or mount the
coordinator's local catalog path. A future coordinator may transport the parsed
catalog or the relevant normalized scheduling data under a separately selected
remote protocol.

## Public option spelling

D099 selects:

```text
--resource-catalog
```

rather than:

```text
--resource-catalog-file
```

because the durable public concept is the resource catalog. The initial source is
a local file, but the CLI name must not unnecessarily freeze that acquisition
technology into the concept name.

This does not authorize a future implementation to change the meaning of the v1
argument in place. New source kinds require an explicit compatible contract.

## Prior-art survey

The owner decision follows an exhaustive comparison across CTest, Kubernetes /
kubectl, Terraform, Docker Compose, Nomad, Slurm, Bazel, pytest, Cargo, Maven and
Gradle.

The important result is not that one mature tool should be copied wholesale.
The useful design is a combination of the strongest narrowly applicable
precedents while rejecting configuration institutions that solve different
problems.

### CTest

CTest provides the closest functional precedent. Tests declare resource needs
while the execution environment supplies a separate resource specification, and
CTest exposes an explicit `--resource-spec-file` input.

Adopted evidence:

- resource inventory is naturally an execution-environment input;
- an explicit long CLI option is understandable and automation-friendly;
- the source path need not become test identity.

Not adopted:

- broader dashboard/variable/generated-source surfaces;
- behavior where tests with resource requirements may still execute when resource
  allocation is not active.

D076 requires complete resource admission before a resourceful Protos case
starts.

### Kubernetes / kubectl

kubectl provides strong evidence for the exact-source branch: when an explicit
`--kubeconfig` file is selected, that file can act as the single explicit
configuration input without merge.

Adopted evidence:

- one explicit CLI-selected file is operationally durable;
- explicit selection can suppress merge/discovery behavior;
- invocation-relative explicit paths are predictable.

Not adopted:

- `KUBECONFIG` source lists;
- home-directory fallback;
- multi-file merge.

Those facilities solve general cluster-client configuration and are unnecessary
for D077's intentionally minimal catalog source model.

### Terraform

Terraform demonstrates that explicit file inputs scale in automation, but also
demonstrates the compatibility cost of combining automatic files, environment
variables, CLI values and precedence rules.

D099 takes the explicit-input lesson and rejects the configuration-layering
institution because the resource catalog is an authority snapshot, not a
compositional variable overlay.

### Docker Compose

Compose demonstrates excellent ergonomics for explicit file selection, but also
supports discovery, repeatable files, ordered merge, environment-based source
selection and stdin.

Those mechanisms are appropriate for composing application configuration.
They would make D099 responsible for hidden effective-catalog construction and
are rejected for v1.

### Nomad

Nomad accepts repeatable configuration files/directories and merges
configuration. This is mature for a long-lived agent whose configuration is
naturally assembled from several administrative fragments.

The Test Tool instead needs one invocation-scoped environment inventory, so
directory expansion and field-specific merge semantics are rejected.

### Slurm

Slurm supplies strong evidence that resource inventory is operator/environment
owned and that the physical configuration source can evolve independently of the
logical scheduler model.

Its conventional file, environment override and configless/server mechanisms
also illustrate why D099 should name the concept `resource-catalog` rather than
bake `file` into the long option.

D099 does not adopt Slurm's implicit system default or environment-variable
precedence.

### pytest

pytest demonstrates that one explicit configuration selected by CLI is a viable
testing-tool model, while its ancestor-directory discovery is appropriate for
repository-owned test configuration.

The Protos resource catalog is environment-owned, so ancestor discovery is
rejected.

### Bazel

Bazel demonstrates that execution-platform/environment selection can remain
separate from individual test/target requirements.

Its layered rc institutions solve broad build-tool configuration needs but would
be excessive for the single catalog authority selected by D077.

### Cargo

Cargo's hierarchical configuration search and merge scale well for developer
tool preferences and project configuration.

They are negative evidence for D099 because the effective environment inventory
would become dependent on cwd ancestry and user configuration.

### Maven

Maven's global/user settings plus explicit settings overrides demonstrate a
mature layered configuration model.

D099 does not need separate global/user catalog authorities and therefore avoids
the merge/precedence commitment.

### Gradle

Gradle demonstrates that CLI, project, user, installation and environment
configuration layers can scale to very large ecosystems.

It also demonstrates the long-term compatibility surface created once those
precedence layers exist. D099 deliberately avoids creating them without a real
resource-catalog requirement.

## Candidate comparison

Focused scores are 1–5.

| Candidate | Future resilience | Scalability | Protos philosophy | Outcome |
| --- | ---: | ---: | ---: | --- |
| **A′ — one explicit local catalog path** | **4.9** | **5.0** | **5.0** | **SELECTED** |
| B — conventional fixed filename | 3.6 | 4.0 | 3.4 | rejected |
| C — CLI + environment fallback | 4.6 | 4.8 | 4.0 | rejected for v1 |
| D — discovery + override | 4.4 | 4.5 | 3.3 | rejected |
| E — repeatable sources + merge | 4.8 | 4.8 | 2.0 | conflicts with D077 |
| F — generic file/stdin/URI source | 5.0 | 5.0 | 3.2 | deferred, premature |
| G — corpus/manifest-selected catalog | 2.8 | 3.0 | 2.0 | rejected |

The refined full D099 GITHUB010 scorecard records Candidate A′ at
**49.6 / 50**, with **HIGH** confidence.

The decisive strengths are:

- exact agreement with D077's source-cardinality invariant;
- explicit environment authority;
- deterministic CI and local invocation;
- no ambient configuration;
- no merge/preference semantics;
- pay-only-for-use for resource-free corpora;
- no corpus/environment ownership collapse;
- no host-path leakage into durable or distributed identity;
- cheap future addition of a distinct managed/CAS/service acquisition adapter.

## Stress scenarios

### Resource-free local run

```text
protos test
```

selects the D077 empty catalog. No resource catalog read/parse work is required.

### Resourceful local run

```text
protos test --resource-catalog /etc/protos/gpu-a100.toml
```

acquires exactly that environment inventory. Requirements are later admitted only
against the validated catalog.

### Resourceful run without catalog

A valid CaseSpec requirement cannot be satisfied by the empty catalog. The case
does not start.

### CI

CI materializes or mounts one environment-specific catalog and passes its path
explicitly in the command. The effective authority is visible in the invocation
rather than inherited from runner environment variables or filesystem discovery.

### Monorepo

Changing the working directory or running a nested corpus never discovers a
different catalog from parent directories. A relative explicit path has only the
ordinary meaning chosen by the operator who typed it.

### Several inventories

An operator may maintain many catalog files, but one invocation selects exactly
one.

Combining or generating an inventory is an external environment-management
operation, not hidden Test Tool merge policy.

### Packaged / CAS corpus

The packaged corpus continues to own requirements only. The executor/coordinator
supplies the environment catalog independently. No host catalog path is embedded
in the package or corpus metadata.

### Future remote workers

The coordinator acquires and validates the catalog before remote placement.
Workers receive normalized scheduling information under a future remote protocol;
they do not rediscover the coordinator's filesystem path.

### Provider replacement

No D099 change is required. D098 logical provider/profile identities remain
inside the catalog and can resolve to a different implementation.

### Invalid source

Missing, non-regular, unreadable, malformed or schema-invalid input fails closed
before resourceful execution.

### Repeated option

```text
--resource-catalog a.toml --resource-catalog b.toml
```

is a configuration error.

## Strongest regret scenario

The strongest plausible regret case is a mature distributed Protos testing
service in which catalogs live in:

- a control-plane configuration service;
- CAS;
- a signed environment inventory store; or
- dynamically generated scheduler state.

Candidate A′ keeps that future cheap because the durable downstream boundary is
the acquired/validated inert catalog, not the local file path.

A later explicit source adapter can map:

```text
managed catalog source
        |
        v
acquire catalog content/model
        |
        v
same D077 / D097 / D098 validation
        |
        v
same scheduler contract
```

without changing Requirements, resource keys, capacity, scope, provider/profile
identity or reservation semantics.

The intentionally rejected shortcut is to make today's `PATH` argument an
arbitrary URI mini-language. Network trust, authentication, caching, offline
behavior and CAS identity deserve their own decision if and when required.

## Rejected alternatives

### Conventional filename

Rejected because an environment authority would become ambient and location
dependent.

### Environment-variable fallback

Rejected because it creates an invisible input source and a precedence rule
without a demonstrated need.

### Discovery + override

Rejected because cwd/parent/project discovery is appropriate for repository-owned
configuration, not an execution-environment resource inventory.

### Repeatable source merge

Rejected because it directly conflicts with D077's zero-or-one explicit-source
invariant and would require an entire effective-catalog merge contract.

### Generic file/stdin/URI source

Deferred because it prematurely combines local filesystem acquisition, streaming,
networking, authentication, caching and trust policy.

### Corpus-selected catalog

Rejected because it collapses the D076/D077 owner split and lets repository data
choose execution-environment authority.

## Intentionally deferred

D099 does not select:

- catalog parser implementation slice;
- provider API or Java/Protos/native ABI;
- provider discovery/loading;
- typed provider configuration;
- credential acquisition;
- catalog merge/inheritance;
- managed catalog service;
- URI/network source;
- CAS identity or transport;
- remote worker protocol;
- physical worker topology;
- fairness/priority/starvation;
- retry or replay policy;
- timeout/kill;
- sharding;
- public Protos language semantics.

## Ratification effect

D099 ratifies only the implementation-independent Test Tool catalog acquisition
contract.

It does not add the CLI flag yet, parse a catalog, reserve a resource, load a
provider or alter public Protos semantics.

`TOOL002-I6E` remains the next already-bounded executable TOOL002 slice. Its
independent repository-wide executable validation blocker is not changed by D099.

Any later implementation slice that requires a still-deferred provider API,
remote source, merge, transport, topology or scheduling policy must stop at the
normal Dxxx/PLATxxx approval gate.
