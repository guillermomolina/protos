# D123 — Test Tool `SuiteId` lexical identity and namespace contract

Status: **RATIFIED — Candidate G′ selected**

Allocated: **2026-09-13**

Explicit project-owner approval: **2026-09-13**

Decision issue: GitHub #488

Primary consumer: `TOOL005-A2` / GitHub #476

Parent architectural authority: `D122` / GitHub #469

Nature: implementation-independent Test Tool identity/naming contract

Normative language effect: **none**.

## Decision boundary

D122 ratified the explicit composable Test Tool suite graph and fixed that every
suite/corpus node has a stable logical identity, while deliberately leaving the
exact `SuiteId` lexical grammar and concrete production identities open.

D123 closes that boundary before TOOL005-A2 publishes the first production suite
IDs. It decides the durable tooling identity namespace only. It does not change:

- Protos language or Standard Library semantics;
- TestPlan / CaseSpec semantics;
- D077/D098 resource identity or provider/profile semantics;
- suite execution-requirement identity;
- Test Tool scheduling, progress, reporting or exit behavior;
- physical worker placement or remote-execution protocol;
- filesystem layout or corpus membership.

## Selected contract — Candidate G′

`SuiteId` is an **explicitly assigned logical namespace plus a readable semantic
identifier**.

Canonical grammar:

```text
SuiteId := segment ("/" segment)*
segment := [a-z0-9][a-z0-9._-]*
```

This lexical shape is intentionally portable and segmentable. It is a separate
semantic namespace from D077 resource keys even though the portable character
shape is similar.

The slash (`/`) means **logical namespace segmentation only**.

It does not mean and must not be reconstructed from:

- filesystem path;
- SuiteGraph ancestry;
- package locator;
- repository URL;
- Java class, plugin or other host implementation identity;
- suite execution requirement;
- D098 provider/profile;
- worker or placement identity.

## Stable API identity

A `SuiteId` is durable tooling API identity. Once published for an official suite,
its spelling remains stable across ordinary moves, graph reparenting,
implementation replacement and presentation/name changes.

Human-facing display text is a separate concern and may evolve without changing
`SuiteId`.

This intentionally follows the same general Protos design principle used by the
package architecture: durable identity is not reconstructed from a path, URL,
repository locator, transport or current implementation location.

## Initial repository-owned identities

Generation 1 reserves exactly these production IDs:

```text
protos/repository
protos/conformance
protos/actor
protos/group
protos/package-toml
```

Their initial meanings are:

- `protos/repository` — explicit root suite for the repository-owned official
  Protos corpus graph;
- `protos/conformance` — current primary conformance corpus leaf;
- `protos/actor` — current Actor corpus leaf;
- `protos/group` — current Group corpus leaf;
- `protos/package-toml` — current Package Tool TOML syntax corpus leaf.

These identities are independent of graph ancestry. For example, a later graph
may place `protos/package-toml` below additional `tools` / `package` suites while
its `SuiteId` remains `protos/package-toml`.

Likewise, moving the corpus directory does not rename the suite.

## Namespace semantics

The leading `protos` segment is an explicitly assigned durable namespace token
for these repository-owned generation-1 suites. It is not dynamically derived
from:

- the current GitHub owner/repository spelling;
- `protos.guillermomolina.com` or another domain;
- a filesystem root;
- a package locator;
- a source module name.

No rule in D123 grants arbitrary external packages authority to mint identifiers
under the `protos/...` namespace.

Future cross-package suite composition may define an owner/package authority
mechanism separately. D123 does not infer such authority from URLs or paths.

## Identity versus graph composition

Suite composition and suite identity are orthogonal.

For example, these two graph shapes may contain the same suite identity:

```text
protos/repository
  -> protos/package-toml
```

and later:

```text
protos/repository
  -> tools
       -> package
            -> protos/package-toml
```

Graph ancestry may evolve while the leaf identity remains stable.

Therefore a consumer must not derive a child `SuiteId` by concatenating parent
IDs or graph edges.

## Display names and selectors

`SuiteId` is suitable as the canonical machine/tooling selector for:

- filtering and selection;
- persistent validation evidence;
- deterministic reporting identity;
- sharding/planning metadata;
- serialization to future workers;
- explicit suite references.

Presentation remains separate. TOOL005-A2 does not introduce a new display-name
field and does not change current progress phase labels.

A future UI may attach a replaceable human display title without changing the
canonical ID.

## Fail-closed lexical behavior

When production `SuiteId` validation is applied, IDs outside the canonical grammar
fail closed rather than being normalized, case-folded, trimmed, path-resolved or
silently rewritten.

In particular:

- uppercase is not canonical;
- empty segments are invalid;
- leading/trailing `/` is invalid;
- whitespace is invalid;
- `.` and `..` are not treated as path traversal because `SuiteId` is not a path;
  they are also not valid complete segments under the selected grammar because a
  segment must begin with `[a-z0-9]`;
- equivalent-looking URLs, paths, package names or host symbols do not alias an
  ID unless a future explicit authority contract says so.

## Prior-art basis

The decision audit compared materially different identity models across:

- Microsoft.Testing.Platform;
- JUnit Platform;
- Bazel;
- Buck2;
- Meson;
- Gradle Test Suites;
- Go `testing`;
- Rust Cargo/libtest;
- Erlang Common Test;
- Pharo/SUnit;
- pytest and Python unittest;
- Maven Surefire/Failsafe;
- Jest and Vitest;
- Deno and Node test runners;
- ExUnit;
- NUnit;
- GoogleTest;
- PHPUnit;
- tox.

The selected model combines the strongest transferable properties:

1. Microsoft.Testing.Platform: stable identity independent from display/location;
2. JUnit: canonical structured identity with display separation;
3. Bazel/Buck2: compact graph-friendly namespacing without adopting package/path
   location as identity;
4. Meson: explicit qualification rather than an unscoped global short name;
5. Common Test/SUnit: explicit compositional suite ownership;
6. Protos package architecture: durable identity must not be reconstructed from
   path, URL, repository or transport.

## Candidate comparison

Owner-requested principal axes, scored 1–10:

| Candidate | Aguante de futuro | Escalabilidad | Filosofía Protos |
| --- | ---: | ---: | ---: |
| A — repository-path ID | 4 | 8 | 2 |
| B — host/source-symbol ID | 5 | 7 | 2 |
| C — flat global semantic token | 7 | 6 | 9 |
| D — graph-ancestry logical path | 8 | 9 | 7 |
| E — reverse-DNS / organization-scoped readable ID | 9 | 10 | 6 |
| F — opaque persistent UID + separate display | 10 | 10 | 8 |
| **G′ — assigned logical namespace + semantic ID** | **10** | **10** | **10** |
| H — typed segment identity | 10 | 9 | 8 |

## Rejected coupling patterns

D123 specifically rejects making any of these authoritative `SuiteId` inputs:

```text
repository-relative path
absolute path
Java/C++/Rust/Python/etc. symbol name
module/class/method name
GitHub owner/repository locator
domain/reverse-DNS ownership string
SuiteGraph parent path
execution engine/plugin name
provider/profile
worker identity
```

Those may remain useful implementation or display facts, but they do not define
suite identity.

## Strongest argument against G′

A semantic stable token can become historically awkward after a conceptual
rename. A suite once named `protos/actor` may retain that ID even if its future
human title changes substantially.

That cost is intentional. Durable IDs behave like API identifiers; presentation
is allowed to evolve independently.

## Regret scenario and escape path

If future federated package composition proves that one assigned textual
namespace is insufficient for global authority, suite identity may evolve
structurally to something conceptually equivalent to:

```text
(ownerIdentity, localSuiteId)
```

where `ownerIdentity` comes from a separately stable package/repository authority
model and `localSuiteId` retains the G′ token.

That escape path does not require filesystem paths, URLs or host implementations
to become authoritative.

## Ratified invariants

```text
D123_SELECTED_CANDIDATE=G_PRIME
SUITE_ID_MODEL=ASSIGNED_LOGICAL_NAMESPACE_PLUS_SEMANTIC_ID
SUITE_ID_GRAMMAR=segment(/segment)*
SUITE_ID_SEGMENT=[a-z0-9][a-z0-9._-]*
SUITE_ID_SEPARATOR_SEMANTICS=LOGICAL_NAMESPACE_ONLY
SUITE_ID_IS_PATH=NO
SUITE_ID_IS_GRAPH_ANCESTRY=NO
SUITE_ID_IS_PACKAGE_LOCATOR=NO
SUITE_ID_IS_REPOSITORY_URL=NO
SUITE_ID_IS_HOST_IMPLEMENTATION=NO
SUITE_ID_IS_EXECUTION_REQUIREMENT=NO
SUITE_ID_IS_RESOURCE_PROVIDER_PROFILE=NO
DISPLAY_NAME_SEPARATE_FROM_ID=YES
INITIAL_ROOT_SUITE_ID=protos/repository
INITIAL_CONFORMANCE_SUITE_ID=protos/conformance
INITIAL_ACTOR_SUITE_ID=protos/actor
INITIAL_GROUP_SUITE_ID=protos/group
INITIAL_PACKAGE_TOML_SUITE_ID=protos/package-toml
```

## Implementation consequence

D123 releases TOOL005-A2 to materialize exactly the root plus the four current
corpus leaves with the IDs above after this governance record is published.

D123 does not authorize TOOL005-A2 to change Test Tool execution, planning,
resource or reporting behavior. Graph-driven execution/aggregation remains a
later TOOL005 slice.
