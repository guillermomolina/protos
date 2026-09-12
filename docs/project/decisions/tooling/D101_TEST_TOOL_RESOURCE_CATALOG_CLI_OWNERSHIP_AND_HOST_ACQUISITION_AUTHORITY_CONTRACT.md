# D101 — Test Tool resource-catalog CLI ownership and host acquisition authority contract

Status: **RATIFIED — Candidate C′ selected**

Allocated: **2026-09-12**

Explicit project-owner approval: **2026-09-12**

Decision issue: GitHub #420

Primary consumer: `TOOL002-I` / GitHub #96

Predecessors: `D069`, `D076`, `D077`, `D097`, `D098`, `D099` — RATIFIED

Executable prerequisite: `TOOL002-I7A` — CLOSED

Nature: implementation-independent Test Tool CLI-ownership and acquisition-authority contract

Normative language effect: **none**.

## Decision boundary

D099 already selects the public resource-catalog acquisition form:

```text
protos test [--resource-catalog PATH] ...
```

with zero-or-one explicit local filesystem source, invocation-working-directory
resolution for relative paths, ordinary host path resolution, readable-regular-file
final target, one complete acquisition and no implicit search/env/merge/stdin/URI.

Published TOOL002-I7A already owns the strict catalog-v1 TOML/schema boundary in
bundled Protos.

The remaining unresolved boundary was who owns interpretation of the public
`--resource-catalog` option and how the authority to acquire an arbitrary explicit
host path crosses from the host into the bundled Test Tool.

D101 selects that ownership/authority boundary without reopening D069, D076,
D077, D097, D098, D099 or I7A.

## Selected contract — Candidate C′

The architecture is:

```text
process.args()
      |
      v
bundled Protos Test Tool
      |
      +-- owns --jobs policy (D069)
      |
      +-- owns --resource-catalog policy (D099)
                         |
                         v
             Test-Tool-private one-shot
               CatalogAcquirer capability
                         |
                         v
                 host filesystem
                         |
                         v
              immutable Bytes snapshot
                         |
                         v
            bundled Protos UTF-8/TOML/I7A
                         |
                         v
               inert validated catalog
```

The public Test Tool remains the owner of Test Tool option policy. The host owns
only the authority-bearing acquisition mechanism.

## CLI policy ownership

Bundled Protos is the sole owner of D099 public option semantics through ordinary
`process.args()` handling.

Therefore bundled Protos owns:

- recognition of `--resource-catalog`;
- zero/one occurrence cardinality;
- missing-value rejection;
- selection of the exact PATH argument;
- absence semantics;
- the decision whether the acquisition capability is invoked.

The Java host MUST NOT separately parse, reinterpret, merge, default or discover
the public `--resource-catalog` option.

This keeps one Test Tool CLI policy owner and preserves D069's established rule
that public Test Tool scheduling option policy belongs to bundled Protos rather
than host submission machinery.

## Catalog-acquisition capability

The host installs one Test-Tool-private acquisition capability for the invocation.

The capability is deliberately not a Filesystem.

Conceptually it exposes only:

```text
acquireCatalog(PATH) -> Bytes
```

The exact private selector/API spelling is implementation detail unless a later
slice exposes a durable contract requiring another decision.

The capability grants no general authority to:

- list directories;
- enumerate roots;
- open arbitrary files through a generic `open`;
- write or mutate files;
- inspect environment variables;
- search home/system/repository directories;
- merge multiple sources;
- read stdin;
- fetch URLs/URIs;
- execute generators.

The capability exists only to materialize the one D099 source selected by the
bundled Test Tool.

## One-shot authority

The acquisition capability may successfully perform at most one catalog
acquisition per Test Tool invocation.

If bundled Protos does not select a resource catalog, the capability is not
invoked.

After one acquisition attempt has consumed the operation, a second attempt fails
closed. The host must not implement first-wins, last-wins or merge behavior.

This one-shot boundary reinforces D077/D099 source cardinality without creating a
general filesystem institution.

## PATH interpretation

The capability receives exactly the PATH selected by bundled Protos.

The host implements only the D099 path mechanics:

- an absolute PATH remains absolute;
- a relative PATH resolves against the Test Tool invocation working directory;
- ordinary operating-system symbolic-link resolution is permitted;
- the final selected object must be one readable regular file;
- missing, dangling, directory, device, socket, FIFO, unreadable, open or read
  failures fail closed as infrastructure/configuration evidence.

The invocation working directory used for relative resolution is the host
invocation boundary's working directory, not a corpus/package/repository path
discovered later.

No corpus-owned input may redirect the catalog source.

## Acquisition result — immutable Bytes

The acquisition capability returns the complete acquired file as one immutable
`Bytes` snapshot.

It does not return decoded String/TOML/schema objects.

This preserves the ownership split:

```text
host
    -> path resolution
    -> authority check
    -> regular-file acquisition
    -> immutable bytes

bundled Protos
    -> UTF-8 decoding
    -> TOML 1.0 parsing
    -> D077/D097/D098/I7A schema validation
    -> inert catalog snapshot
```

The host therefore does not become a second textual/TOML/catalog-schema authority.

If UTF-8 decoding or TOML/schema validation fails, the failure remains
infrastructure/configuration evidence under the Test Tool configuration boundary;
it is not a guest test `Error`.

## Lifetime and non-delegation

The PATH and the acquisition capability are bootstrap/configuration authority only.

They MUST NOT enter:

- TestPlan;
- CaseSpec;
- Requirement;
- CatalogEntry durable fields;
- resource key identity;
- provider/profile identity;
- reservation identity;
- provider configuration;
- child Process state;
- ordinary guest globals;
- worker identity;
- remote transport identity;
- distributed cache/CAS identity.

The acquisition capability MUST NOT be delegated to a child test Process or to a
resource provider.

Only the inert validated catalog snapshot survives into scheduling.

The host path is discarded as an authority/location input after acquisition and
is not the durable identity of the catalog.

## Absence

When `--resource-catalog` is absent:

```text
bundled Protos selects no source
        |
        v
CatalogAcquirer is not invoked
        |
        v
D077 empty catalog
```

No catalog file discovery or ambient filesystem probing occurs.

D076 still applies: a resourceful case that cannot be admitted against the empty
catalog must not execute as though resource scheduling were disabled.

## Failure plane

Acquisition failures are Test Tool infrastructure/configuration failures.

They are not:

- guest Protos `Error` raised by a test case;
- a failed CaseSpec observation;
- a provider fallback trigger;
- an instruction to continue with an empty catalog.

A selected source that cannot be acquired or validated fails the invocation's
resource-configuration path closed.

## Future managed / remote source adapters

Candidate C′ intentionally keeps the acquisition adapter replaceable:

```text
v1 local PATH -----------\
future managed source ----+--> inert immutable acquisition snapshot
future CAS/service -------/
                                  |
                                  v
                         catalog parse/validation
```

A future source kind must be selected explicitly by a future contract.

It must not reinterpret the v1 `PATH` string as:

- URI;
- registry name;
- CAS digest;
- stdin marker;
- service identifier;
- executable expression.

A future remote coordinator may acquire/validate the catalog once and transport
normalized inert catalog state without requiring workers to mount or rediscover
the coordinator's local path.

## Why not a broad host Filesystem

Giving bundled Protos a general host filesystem would solve D099 mechanically but
would widen authority far beyond the one selected operation.

The existing Test Tool architecture deliberately uses separate confined
Filesystem capabilities for corpus-owned trees.

D101 therefore does not convert D099 into ambient host read authority merely
because one CLI option accepts an absolute or cwd-relative path.

The capability boundary follows least authority:

```text
authority needed: acquire one explicitly selected catalog
authority granted: acquire one explicitly selected catalog
```

not:

```text
authority needed: acquire one file
authority granted: browse/read arbitrary host filesystem
```

## Why not Java-owned D099 policy

Having Java parse only `--resource-catalog` while bundled Protos parses `--jobs`
would create two owners for one public Test Tool CLI.

That split would make future Test Tool options repeatedly answer an implementation
layer question unrelated to their semantics.

D101 instead preserves the existing direction:

```text
Test Tool policy -> bundled Protos
host authority/mechanics -> host capability
```

## Why not a two-phase bootstrap protocol now

A two-phase protocol:

```text
Protos phase 1 -> inert acquisition request -> host -> Protos phase 2
```

can provide an equally strong or slightly stronger authority story and remains a
valid future escape path if Test Tool bootstrap evolves into a general
request/response configuration protocol.

It is not selected in v1 because it introduces additional lifecycle,
request-carrier, continuation/re-entry and failure-transfer machinery solely to
acquire one explicit file.

Candidate C′ obtains the required least-authority boundary without creating that
institution prematurely.

## Prior-art audit

The owner decision followed an exhaustive comparison across conventional
configuration-file tools and capability-oriented systems.

### CTest

CTest owns `--resource-spec-file`, file acquisition and scheduling in one native
tool. It proves explicit external resource inventory is operationally mature, but
does not offer a separate guest-tool/host authority layer.

### kubectl / kubeconfig

An explicit kubeconfig flag is strong precedent for one explicitly selected file
and for avoiding merge under that route. kubectl still owns both option policy
and file I/O in Go.

### Cargo

Cargo supports path-valued configuration and mature structured TOML
configuration, but also supports repeated configuration/merge precedence. D101
uses it as acquisition evidence, not as an ownership template.

### Terraform

Terraform demonstrates explicit CLI file acquisition at scale but combines it
with broader precedence and host-owned policy.

### Docker Compose

Compose demonstrates mature explicit file acquisition and multi-file operation;
its discovery/merge/stdin surface is negative evidence for D099/D101's smaller
authority model.

### Bazel / Buck2

Both prove large client/daemon systems can load layered configuration early and
transport resolved state. Their native startup/config ownership and broad
precedence chains are intentionally not copied.

### pytest / npm

These provide useful evidence that tool policy belongs naturally in the language
that implements the tool. Their ambient filesystem models are not copied.

### Maven / Gradle

They demonstrate mature host-owned configuration acquisition on the JVM, but
their layering and host-language ownership do not preserve Protos' existing
bundled-tool policy direction.

### Slurm

Slurm proves environment-owned scheduler inventory/configuration scales to large
systems, while its daemon-centric ambient authority is not the Protos model.

### Deno

Deno provides the closest language/runtime ownership analogy:

```text
application/tool code owns argument meaning
runtime owns I/O permission/authority
```

D101 adopts that separation while using a narrower purpose-specific capability
than Deno's general file APIs.

### WASI / cap-std

WASI/capability-oriented systems provide the strongest authority precedent:
external I/O is available through held capabilities rather than ambient
namespace authority.

D101 adopts the least-authority principle without importing a literal preopen
directory model, because D099 already permits one explicit absolute or
cwd-relative local path.

## Candidate comparison

Scores are 1–5.

| Criterion | A Java D099 | B broad FS | **C′ narrow capability** | D two-phase | E all-host CLI |
| --- | ---: | ---: | ---: | ---: | ---: |
| Correctness / invariants | 4.8 | 4.0 | **5.0** | **5.0** | 4.8 |
| Protos alignment | 3.0 | 2.5 | **5.0** | 4.7 | 1.5 |
| Future-option resilience | 4.3 | 4.4 | **4.9** | **5.0** | 4.8 |
| Scalability | 4.7 | 4.6 | **5.0** | **5.0** | **5.0** |
| Conceptual simplicity | **4.8** | 4.3 | 4.7 | 3.2 | 4.3 |
| Portability / implementation freedom | 4.2 | 4.0 | **5.0** | **5.0** | 4.5 |
| Runtime / resource cost | **5.0** | 4.8 | **5.0** | 4.7 | **5.0** |
| Failure / operability | 4.8 | 3.5 | **5.0** | 4.8 | 4.8 |
| Reversibility / migration | 4.0 | 4.2 | **4.9** | 4.5 | 2.5 |
| Evidence maturity / risk | **5.0** | 4.2 | 4.8 | 4.2 | **5.0** |
| **Total / 50** | **44.6** | **40.5** | **49.3** | **46.1** | **42.2** |

Focused project-owner criteria:

| Candidate | Future resilience | Scalability | Protos philosophy |
| --- | ---: | ---: | ---: |
| A Java D099 | 4.3 | 4.7 | 3.0 |
| B broad FS | 4.4 | 4.6 | 2.5 |
| **C′ narrow capability** | **4.9** | **5.0** | **5.0** |
| D two-phase | **5.0** | **5.0** | 4.7 |
| E all-host CLI | 4.8 | **5.0** | 1.5 |

## Rejected alternatives

### A — Java owns D099 option + acquisition

Rejected because it splits one public Test Tool CLI between bundled Protos and
Java, contrary to the established D069 ownership direction.

### B — bundled Protos + broad host Filesystem

Rejected because it grants authority far wider than the one catalog acquisition
actually requires.

### D — two-phase request/acquisition/bootstrap

Architecturally strong and future-compatible, but rejected for v1 because it
creates a general multi-phase bootstrap institution before there is evidence that
such an institution is needed.

### E — move all Test Tool CLI policy to Java

Rejected because it directly reopens/contradicts D069. It is inadmissible without
reopening that ratified decision.

## Strongest regret scenario

The strongest regret case is a future Test Tool that needs several independently
authorized operator-owned inputs:

```text
resource catalog
credentials broker request
remote execution descriptor
managed scheduler snapshot
```

If those inputs need asynchronous or negotiated host interaction, Candidate C′
could otherwise accumulate several purpose-specific capability operations.

The escape path remains explicit:

1. preserve bundled Protos as policy owner;
2. introduce a later audited generic bootstrap request/response protocol if real
   consumers justify it;
3. adapt existing local acquisition through that protocol;
4. retain D099 PATH semantics and inert catalog snapshot identity unchanged.

Thus Candidate C′ does not block Candidate D's architecture later; it simply
avoids paying for it now.

## Intentionally deferred

D101 does not select:

- the private implementation class/selector name of CatalogAcquirer;
- provider implementation/loading API;
- provider registry;
- resource reservation algorithm;
- placement topology;
- remote worker protocol;
- managed/CAS/service source syntax;
- generic host-I/O capability framework;
- a general two-phase tool bootstrap protocol;
- public `std:toml`;
- Protos language semantics.

## Ratification effect

D101 ratification is governance/tooling only.

It changes no:

- executable Test Tool implementation;
- Java/runtime implementation;
- Protos specification;
- Standard Library public API;
- Maven implementation version;
- native boundary.

It unblocks the next bounded TOOL002-I implementation slice: materialize D099
CLI selection plus the Candidate C′ one-shot acquisition boundary, while keeping
D076 reservation/provisioning and provider implementation outside that slice.
