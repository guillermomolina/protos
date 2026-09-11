# D077 — Test Tool resource requirement and catalog representation contract

Status: **RATIFIED — Candidate D selected**

Allocated: **2026-09-11**

Explicit project-owner approval: **2026-09-11**

Decision issue: GitHub #361

Predecessor: `D076` / GitHub #359 — RATIFIED Candidate D

Primary consumer: `TOOL002-I` / GitHub #96

Nature: implementation-independent Test Tool data/configuration representation contract

Normative language effect: **none**.

## Decision boundary

D076 already selected the durable two-plane resource architecture:

```text
inert CaseSpec requirement
    -> bundled-Protos scheduling/reservation
    -> environment-owned catalog/placement
    -> provider
    -> attempt-private capability
    -> fresh Protos Process
    -> terminal cleanup/release
```

D077 selects the representation layer needed to materialize that architecture
without making live authority part of the TestPlan.

D077 selects:

1. the logical resource-key representation;
2. the in-memory CaseSpec requirement carrier;
3. the persistent corpus representation for sparse requirements;
4. the environment catalog representation family;
5. the initial requirement/catalog join identity;
6. duplicate/conflict handling; and
7. the initial configuration-precedence invariant.

D077 deliberately does **not** select the final physical sidecar filename, the
final CLI option spelling used to select a catalog, the exact closed scope-name
vocabulary, provider implementation APIs, selector languages, binding aliases,
catalog inheritance/merge, fairness/priority/starvation, retries, timeout/kill,
sharding, remote transport, CAS, `jobs=auto`, CPU/memory auto-accounting or
physical worker topology.

A later implementation slice that needs one of those unresolved public contracts
must stop at the normal Dxxx/PLATxxx approval gate rather than inventing it.

## Selected representation — sparse structured requirements

The selected representation keeps the ordinary test corpus small and adds
resource metadata only where it is used:

```text
manifest.tsv
    |
    +---- optional strict/versioned TOML requirement data
                     |
                     v
        private frozen CaseSpec
        requirements: frozen Array<Requirement>
                     |
                     v
              Runner / scheduler
                     |
                     +---- one explicitly selected strict/versioned catalog
                     |
                     v
             atomic reservation
                     |
                     v
          provider/profile resolution
                     |
                     v
          attempt-private capability
```

The durable model is the requirement/catalog schema, not TOML itself. TOML 1.0
is the selected initial human-readable encoding family.

## Logical resource key

A resource key is an ordinary String with a canonical hierarchical lexical form:

```text
segment[/segment...]
```

Each segment:

- is non-empty;
- begins with a lower-case ASCII letter or decimal digit;
- thereafter contains only lower-case ASCII letters, decimal digits, `.`, `_`,
  or `-`.

The complete key:

- is case-sensitive;
- contains no whitespace;
- has no leading or trailing `/`;
- has no empty segment.

Examples:

```text
gpu
db/integration
license/ansys
test/http-server
vendor/example-device
```

`/` supplies namespace structure only. Protos assigns no built-in ontology such
as vendor/type/model to segment position.

The key identifies a logical resource within the selected catalog. It is not a
physical device ID, worker ID, provider object, OS handle or capability.

## CaseSpec requirement carrier

The existing private frozen CaseSpec representation is extended conceptually
with zero or more resource requirements.

The durable internal shape is:

```text
CaseSpec
    ...
    requirements -> frozen Array<Requirement>
```

Each `Requirement` is a fixed private inert record/tuple carrying:

```text
key
mode
units-or-null
```

Consumers use named private accessors rather than depending on tuple positions.

A resource-free case has an empty frozen requirement Array.

A `Map` may be constructed internally after validation for scheduler lookup, but
`Map(key -> value)` is **not** the durable declaration representation because it
would hide duplicate declarations and encourage mode-specific ad-hoc value
shapes.

## Requirement modes

The persistent structured representation uses the D076 conflict semantics:

```text
mode = "shared"
units = positive Integer
```

or:

```text
mode = "exclusive"
```

with no `units` field/value.

`shared` consumes positive capacity units.

`exclusive` requests sole reservation of the logical resource pool for the
attempt.

Scheduling mode does not imply functional read/write authority. The actual
capability provisioned to the child Process defines functional authority.

## Persistent corpus representation

The existing `manifest.tsv` remains the ordinary corpus/expectation manifest.

Resource requirements are represented separately and sparsely in an optional
strict, versioned TOML 1.0 document associated with a corpus.

Conceptual schema:

```toml
resource-requirements-version = 1

[[requirement]]
case = "integration/db.protos"
key = "db/integration"
mode = "exclusive"

[[requirement]]
case = "gpu/matrix.protos"
key = "gpu"
mode = "shared"
units = 2
```

The physical filename is intentionally not selected by D077.

The structure is deliberately a flat array of requirement records rather than a
deep per-case table hierarchy:

- cases without resources have no declaration;
- each requirement remains independently visible for validation;
- `(case,key)` duplicates remain detectable before indexing;
- the representation remains easy to stream and transform;
- future schema generations can add fields without inventing a mini-language in
  one TSV column.

The sidecar is declarative data, not executable Protos code.

## Environment resource catalog

The execution environment supplies a separate strict, versioned TOML 1.0
catalog.

The catalog and the corpus requirements have different owners:

```text
repository/corpus -> requirements
execution environment -> catalog
```

Conceptual catalog entry:

```toml
resource-catalog-version = 1

[[resource]]
key = "gpu"
capacity = 4
scope = "..."
provider = "gpu"
profile = "a100"
```

Catalog schema invariants selected by D077:

- `key` uses the same canonical logical resource-key form as requirements;
- `capacity` is a positive ordinary Integer;
- `scope` is a schema-controlled String value owned by the catalog representation;
- `provider` is an inert provider/profile-selection identity, not a live provider;
- optional provider-specific/profile identity remains inert configuration data;
- no secret, credential, live capability, worker object, OS handle or provider
  instance is embedded in the catalog model.

The exact initial public `scope` vocabulary is intentionally deferred because the
approved packet selected the ownership boundary and extensibility requirement,
not final public enum spellings.

Provider/profile identities do not give the Test Tool permission to interpret
provider-specific live authority inside the TestPlan.

## Requirement-to-capability correspondence

Initially the same logical resource key joins the planes:

```text
CaseRequirement.key
        |
        v
CatalogEntry.key
        |
        v
Reservation.key
        |
        v
ProvisionedCapabilities[key]
```

No additional binding alias, claim name or request ID institution is selected.

If a future real case needs two distinct bindings backed by the same logical pool,
that requirement may justify a later schema generation. D077 does not pay that
complexity in advance.

## Duplicate and malformed declaration policy

D077 selects fail-closed validation.

### Requirement duplicates

For one case, duplicate `(case,key)` declarations are invalid.

They are not implicitly:

- summed;
- merged;
- overwritten;
- resolved by declaration order; or
- resolved by an "exclusive wins" rule.

Thus these are invalid as two declarations for the same case/key:

```text
shared(key, 1)
shared(key, 1)
```

and:

```text
shared(key, 2)
exclusive(key)
```

### Mode validation

`shared` requires a positive Integer `units`.

Zero, negative, missing or malformed shared units are invalid.

`exclusive` must not carry units.

Unknown modes are invalid.

### Catalog duplicates

A logical catalog key may be declared at most once in one selected catalog.

Duplicate catalog keys are invalid; there is no last-wins behavior.

### Schema validation

Unsupported schema versions, unknown schema-owned fields, malformed resource
keys and unknown scope values are explicit configuration errors.

Unknown fields are not silently ignored under an older schema generation.

### Unsatisfied requirements

A valid case requirement whose key cannot be satisfied by the selected catalog
is infrastructure/configuration evidence.

It is not fabricated into a semantic guest Protos `Error`.

## Initial catalog configuration and precedence

The initial durable precedence invariant is deliberately minimal:

```text
zero or one explicit catalog source per Test Tool invocation
```

There is no implicit:

- merge of multiple catalogs;
- user-home catalog;
- system catalog;
- environment-variable fallback;
- parent-directory search;
- repository-local automatic catalog;
- profile inheritance; or
- last-wins precedence chain.

No catalog source means an empty resource catalog.

Resource-free cases therefore remain unaffected.

A resourceful case with no satisfiable catalog entry is reported through the
infrastructure/configuration plane selected by D076.

The exact public CLI spelling for selecting the one explicit catalog is not
selected by D077 and must not be invented silently.

## Why the representation is sparse

The retained test corpus is dominated by ordinary expectation cases that require
no scarce external resource.

Migrating every row from the existing compact manifest into a richer format would
make the common case pay syntactic and processing cost for an exceptional
capability.

The sparse representation preserves:

```text
simple case -> manifest.tsv only
resourceful case -> manifest.tsv + requirement record(s)
```

This follows the Protos "pay only for what you use" rule while keeping the
underlying CaseSpec model extensible.

## Prior-art survey

### CTest

CTest separates per-test resource requirements from a versioned external resource
specification that describes resource types, instances and slots.

Useful evidence:

- requirement and catalog should be different representations;
- resource names can remain abstract to the scheduler;
- capacity and physical instance identity are distinct;
- environment inventory belongs outside the test manifest.

Not adopted:

- compact `RESOURCE_GROUPS` mini-language;
- environment-variable delivery of allocations;
- multiple implicit resource-spec sources.

### cargo-nextest

nextest combines structured TOML configuration, global capacity,
`threads-required`, test groups, profiles and per-test overrides.

Useful evidence:

- structured declarative TOML scales better than compact custom strings;
- global jobs capacity can compose with local/resource capacity.

Not adopted:

- selector/filter-driven resource ownership;
- broad profile/CLI/env/repository precedence chains for intrinsic requirements.

A resource requirement belongs with the case rather than depending on an
external selector that may silently stop matching after a rename.

### Kubernetes Dynamic Resource Allocation

DRA models requests as structured named records and preserves request/allocation
correspondence while keeping physical device selection and drivers separate.

Useful evidence:

- request data should be explicit records;
- logical request identity should survive allocation;
- count/constraints/selection remain data, not encoded mini-languages;
- physical handles belong after placement.

Not adopted:

- ResourceClaim/Template/DeviceClass/ResourceSlice institutions;
- API-server metadata;
- CEL selector languages;
- controllers.

### Slurm TRES/GRES

Slurm demonstrates that logical resource family/type/count abstractions scale to
large heterogeneous HPC systems.

Useful evidence:

- logical resource key and quantity are durable;
- inventory and request remain separate;
- locality matters at scale.

Not adopted:

- compact colon-delimited resource grammars;
- cluster accounting/QoS/partition institutions.

### Nomad

Nomad demonstrates a smaller structured model with hierarchical device names,
counts, placement and provider/plugin provisioning.

Useful evidence:

- a hierarchical logical key plus structured count record scales without
  Kubernetes-scale institutions;
- provider selection can remain below the workload requirement.

Not adopted:

- fixed vendor/type/model ontology for key path segments.

### Buck2

Buck2 maps logical local-resource types to resource providers and lazily
materializes/reserves pool entries.

Useful evidence:

- logical resource key -> provider is a good replaceable boundary;
- provider initialization can be pay-for-use;
- provider identity need not be a live object in test metadata.

Not adopted:

- Starlark labels as the public Test Tool resource identity;
- environment-variable resource delivery.

### Bazel / Remote Execution

Remote Execution and Bazel platform properties demonstrate the value of inert,
canonical, backend-neutral property data.

Useful evidence:

- transportable execution requirements must avoid host handles;
- backend/provider-specific data can remain opaque above the provider boundary.

Not adopted:

- an unconstrained generic `Map<String,String>` as the whole D076 resource model;
  D076 requires scheduler-visible shared/exclusive/capacity invariants.

### JUnit ResourceLock

JUnit demonstrates the usefulness of one explicit resource key plus one explicit
conflict mode.

Useful evidence:

- conflict semantics should be represented structurally rather than by a single
  parallel/not-parallel flag.

Limit:

- no quantity, catalog, placement or provisioning layer.

### TOML 1.0

TOML arrays-of-tables preserve explicit records and provide strict duplicate-key
rules without requiring a new Protos-owned textual grammar.

Protos already has a bundled-Protos TOML parser and a package-tool precedent for:

```text
TOML syntax
    -> canonical ordinary data
    -> strict Protos-owned schema
```

D077 reuses that representation strategy, not Package Tool package semantics.

## Candidate set

### A — fourth TSV column + mini-DSL

Keep one manifest and encode resources into a compact custom field.

Rejected as the durable representation because extensibility would turn the
field into a second language with escaping, separators, modes, counts and future
attributes.

### B — Map-centric requirements/catalog

Use `Map(key -> value)` for CaseSpec requirements.

Rejected as the durable declaration representation because Maps can hide
duplicates and encourage mode-specific value unions.

Maps remain valid internal scheduler indexes after declaration validation.

### C — migrate the complete test manifest to TOML

Structurally coherent and future-friendly, but rejected now because the common
resource-free case would pay for a metadata model it does not use and the entire
retained corpus would be migrated for an exceptional concern.

### D — sparse structured sidecar + frozen requirement records + separate catalog

**Selected.**

Preserves the existing simple corpus, gives resource declarations a strict
structured/versioned representation and keeps environment capacity in a separate
catalog.

### E — external override/filter rules

Powerful and proven by mature runners, but rejected for intrinsic resource
authority because requirement ownership would become non-local and sensitive to
selector/rename behavior.

### F — claim/class objects

Scales strongly but rejected as premature institution. D076 intentionally keeps
the durable resource layer much smaller than Kubernetes.

### G — executable Protos metadata

Rejected. Running code to discover scheduling requirements would introduce
bootstrap, authority, effects, reproducibility and distribution problems and
would violate D076's inert TestPlan boundary.

## Comparative scorecard

Scores are 1–5.

| Criterion | A TSV DSL | B Map | C unified TOML | **D sparse structured** | E overrides | F claims/classes | G executable |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Correctness / invariants | 4.0 | 4.0 | 5.0 | **5.0** | 4.0 | 5.0 | 2.5 |
| Protos alignment | 4.0 | 4.5 | 4.0 | **5.0** | 3.5 | 2.5 | 2.5 |
| Future-option resilience | 2.5 | 3.5 | 4.5 | **5.0** | 4.5 | **5.0** | 3.0 |
| Scalability | 3.0 | 4.0 | 4.0 | **5.0** | 4.5 | **5.0** | 3.0 |
| Conceptual simplicity | 3.5 | 4.5 | 4.0 | **4.5** | 3.0 | 1.5 | 3.0 |
| Portability / implementation freedom | 4.0 | 5.0 | 5.0 | **5.0** | 4.5 | 4.0 | 3.5 |
| Runtime / resource cost | **5.0** | 4.5 | 3.0 | **5.0** | 4.5 | 2.5 | 3.0 |
| Failure / operability | 3.5 | 3.5 | **5.0** | **5.0** | 3.0 | 4.5 | 2.0 |
| Reversibility / migration | 2.5 | 3.5 | 3.0 | **4.5** | 3.5 | 2.0 | 2.0 |
| Evidence maturity / implementation risk | 4.0 | 4.5 | **5.0** | **5.0** | 4.5 | **5.0** | 3.0 |
| **Total / 50** | **36.5** | **41.5** | **42.5** | **49.0** | **39.5** | **37.0** | **27.5** |

Focused project-owner criteria, scale 1–10:

| Candidate | Future resilience | Scalability | Protos philosophy |
| --- | ---: | ---: | ---: |
| A TSV DSL | 5.5 | 6.0 | 7.5 |
| B Map | 7.5 | 8.0 | 8.5 |
| C unified TOML | 8.5 | 8.0 | 8.0 |
| **D sparse structured** | **10** | **10** | **10** |
| E overrides | 8.5 | 8.5 | 6.5 |
| F claims/classes | **10** | **10** | 5.0 |
| G executable | 6.0 | 6.0 | 4.0 |

## Future stress test

### Very large test corpus

Resource-free cases remain `manifest.tsv`-only. Requirement storage grows with
actual resource declarations, not with every case.

### Many requirements on one case

A frozen Array of fixed records remains small, iterable and serializable. The
scheduler may build an indexed Map after validation without changing the durable
carrier.

### Thousands of logical resource keys

Hierarchical canonical keys prevent one flat naming namespace from accumulating
ad-hoc prefixes while avoiding a central registry or built-in hardware ontology.

### Hardened workers / remote execution

Requirement records contain only `key`, `mode` and `units`. They remain
transportable. Physical provider handles and capabilities remain placement-local.

### Different providers in different environments

The same case requirement can resolve through distinct environment catalogs:

```text
db/integration -> local container provider
db/integration -> CI managed-database provider
```

without changing CaseSpec semantics.

### Secrets

Secrets are not catalog values. Provider/profile identity may select an
environment-owned secret/capability source after placement without making
scheduler-visible metadata a secret store.

### Future richer selectors

If a real future use case requires selector attributes beyond one logical key, a
later schema generation can add structured fields without replacing D076 or the
frozen-record model.

### Future unified test manifest

If resource/tag/platform metadata becomes universal rather than sparse, a future
manifest generation can migrate the physical encoding to a unified structured
document while keeping CaseSpec/accessor semantics stable.

## What could make D regrettable?

The strongest plausible regret is a future where nearly every test accumulates
rich metadata: resources, tags, platform requirements, retries, fixtures,
timeouts and other policy. At that point `manifest.tsv` plus sparse sidecars may
be less ergonomic than one unified structured test manifest.

The escape path remains clean because D077 separates model from encoding:

```text
stable CaseSpec model
    |
    +-- current TSV + sparse sidecar
    |
    +-- future unified manifest generation
```

Consumers do not depend on the physical source encoding.

The reverse migration would be more expensive: selecting a rich unified manifest
today would force every simple case to pay the new representation cost before
there is evidence that the metadata is universal.

## Strongest argument against Candidate D

A single unified TOML manifest is superficially cleaner and avoids an additional
file.

Candidate D is preferred because the current corpus has a strongly asymmetric
shape: semantic expectation metadata is universal, while scarce-resource metadata
is exceptional. Preserving sparse representation better satisfies Protos
pay-only-for-what-you-use and leaves a reversible path to a unified future
generation if that asymmetry disappears.

## Intentionally deferred

D077 does not select:

- physical requirement-sidecar filename;
- exact public CLI spelling for catalog selection;
- exact initial public scope enum spellings;
- provider API implementation;
- provider/profile discovery rules beyond one explicit catalog source;
- selector language;
- binding aliases/request IDs;
- catalog inheritance or merge;
- user/system catalog institutions;
- fairness/priority/starvation;
- retry/replay;
- hard timeout/kill;
- sharding;
- remote transport/protocol;
- CAS;
- `jobs=auto`;
- automatic CPU/memory resource accounting;
- physical worker topology.

## Ratification effect

D077 ratification is governance/design only.

It changes no:

- executable production implementation;
- Protos specification;
- Maven implementation version;
- native boundary;
- D069 jobs contract;
- D076 architecture;
- PLAT023 carrier topology.

After ratification, TOOL002-I is released to `READY` for bounded implementation
and decomposition under D076+D077.

Any implementation slice that reaches one of the explicitly deferred public
contracts above must stop for the normal approval gate rather than selecting it
inside the implementation patch.
