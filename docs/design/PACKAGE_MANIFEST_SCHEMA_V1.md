# Protos Package Manifest Schema v1

Status: package-tool implementation contract; non-normative with respect to Core language semantics

Related design:

- `docs/design/PACKAGE_DISTRIBUTION.md`
- `docs/design/PACKAGE_MANIFEST_FORMAT.md`
- `docs/design/PACKAGE_IDENTITY_VERSIONING.md`
- `docs/design/PACKAGE_VERSION_RESOLUTION.md`
- `docs/design/PACKAGE_TOOL_ARCHITECTURE.md`

## Purpose

`PACKAGE_MANIFEST_FORMAT.md` selected `protos.toml`, TOML 1.0, strict schema
versioning, and rejection of unknown package fields, but intentionally left exact
field names and table layout unfrozen.

That flexibility was appropriate before implementation. It is no longer
appropriate once the bundled package tool starts parsing real project manifests:
an implementation must not silently turn an illustrative example into the
package format.

This document closes the **structural schema** required by package-tool manifest
Slice 3. It fixes the field/table layout for `manifest-version = 1` while
preserving the conceptual boundaries already selected by the package audits.

It does **not**:

- change Core module semantics;
- make package metadata part of the Protos language specification;
- define a public registry protocol;
- define PackageId generation;
- define registry locator lexical policy;
- implement version/constraint parsing;
- define language/toolchain compatibility constraint syntax;
- define package-store, lockfile, network, publication, or manifest-mutation
  behavior;
- create a Standard Library module or `LIBxxx` item.

Those concerns remain owned by their focused designs and later package-tool
slices.

## Design rule

The schema is intentionally structural:

```text
TOML 1.0 syntax
        ↓
canonical TOML table/value model
        ↓
manifest schema v1 field + shape validation
        ↓
package semantic value validation
        ↓
resolution / lock policy
```

The manifest parser owns the first two boundaries.

Schema v1 owns:

- which package fields exist;
- which are required;
- which TOML value family each field accepts;
- which dependency source combinations are structurally valid;
- rejection of unknown fields.

Later pure package logic owns semantic value grammars such as strict
`ReleaseVersion`, dependency constraint v1, and compatibility constraints.

A field being structurally a String does not mean every String is semantically
valid for the concept it carries.

## TOML dialect

Manifest generation 1 accepts TOML **1.0** syntax only.

TOML-equivalent spellings normalize to the same schema model. In particular,
these are representation choices, not package semantics:

- bare versus quoted TOML keys;
- dotted keys versus expanded tables when TOML defines the same table;
- inline tables versus expanded tables when they produce the same table value;
- basic, literal, or multiline TOML string forms when they produce the same
  String value;
- comments and whitespace.

TOML floating-point and date/time values have no schema-v1 package meaning.

Arrays of tables are not used by schema v1.

The parser may recognize TOML value forms that schema v1 does not use, but schema
validation must reject them wherever the declared field shape does not admit
them.

## Root shape

A schema-v1 manifest is one TOML root table with exactly these permitted
top-level fields/tables:

```text
manifest-version
package
compatibility
exports
dependencies
workspace
```

No other top-level field is accepted.

In particular schema v1 has no open:

```text
tool
build
scripts
environment
registry
credentials
```

namespace.

### `manifest-version`

Required.

```toml
manifest-version = 1
```

Shape:

```text
Integer
```

The only accepted value for schema v1 is exactly `1`.

A newer integer is not interpreted under schema v1. It fails as an unsupported
manifest generation.

Negative values, floating-point values, strings, booleans, arrays, and tables
are invalid for this field.

## `[package]`

Required.

The `package` table describes the package whose root contains this manifest.

Allowed fields:

```text
id
version
locator
```

No other field is accepted in schema v1.

### `package.id`

Required String.

```toml
[package]
id = "..."
```

It carries the durable opaque `PackageId` selected by
`PACKAGE_IDENTITY_VERSIONING.md`.

Schema v1 requires a non-empty String and preserves it exactly. It performs no
case folding, Unicode normalization, URL interpretation, filesystem
interpretation, or content-derived reconstruction.

The exact generation/text-encoding policy for newly created PackageIds remains a
separate package-identity tooling decision. Parsing an existing opaque identifier
does not require package-tool bootstrap to invent that generation policy.

### `package.version`

Required String.

```toml
version = "1.4.0"
```

It carries `ReleaseVersion`.

The structural schema requires a non-empty String. The semantic validator must
apply the strict ReleaseVersion model already selected by
`PACKAGE_IDENTITY_VERSIONING.md`:

```text
MAJOR.MINOR.PATCH[-PRERELEASE]
```

with the selected initial rejection of SemVer build metadata.

That semantic validation belongs to the pure version-logic slice, not to TOML
lexing.

### `package.locator`

Optional String.

```toml
locator = "community/parser"
```

It carries a human-facing `PackageLocator`, not PackageId.

The value is diagnostic/discovery metadata and must never become runtime module
identity.

Schema v1 requires a non-empty String when present and preserves it exactly.
Registry naming/canonicalization rules remain a separate audit. Absence is valid
for local/unpublished development packages.

## `[compatibility]`

Optional.

Allowed fields:

```text
language
```

No other field is accepted.

### `compatibility.language`

Required when the `compatibility` table is present.

String.

```toml
[compatibility]
language = "..."
```

This field is reserved for the explicit Protos language-compatibility contract
already anticipated by package resolution.

Schema v1 does not invent the compatibility constraint grammar. Until that
focused contract is closed, the structural validator treats the field as an
exact non-empty String and later resolution work must not infer semantics from
its spelling.

An empty `[compatibility]` table is invalid.

## `[exports]`

Optional.

`exports` is a table whose keys are package-public export names and whose values
are package-internal logical module names.

Example:

```toml
[exports]
Widget = "Widget"
Parser = "parser/Parser"
```

Every value must be a non-empty String.

Schema v1 does not normalize export keys or module strings beyond TOML key
decoding. The exact logical-module naming validator remains the existing/future
module/package resolver responsibility.

An empty `[exports]` table is valid and means the package deliberately exports no
modules.

Exports are the package visibility boundary. The existence of a source file
inside a dependency does not by itself make that module importable by consumers.

## `[dependencies]`

Optional.

`dependencies` is a table from dependency alias to one dependency declaration
table.

The alias is the local name used by future `dep:<alias>/...` resolution. It is
not PackageId, PackageLocator, module identity, or source identity.

A declaration must match exactly one of the three source forms below.

No other dependency fields are accepted.

### Registry dependency

Required fields:

```text
authority
package
version
```

Example:

```toml
[dependencies.parser]
authority = "protos-public"
package = "community/parser"
version = "^2.1.0"
```

Shapes:

```text
authority -> non-empty String
package   -> non-empty String
version   -> non-empty String
```

`authority` denotes the selected `AuthorityIdentity`/authority relation, not a
retrieval URL or mirror.

`package` denotes a human-facing PackageLocator to be resolved by that authority.

`version` denotes dependency constraint language v1 and is semantically
validated by the version/constraint slice.

The manifest keeps authority explicit so changing ambient mirror, cache, proxy,
or endpoint configuration cannot silently change which authority defines the
dependency relation.

### Git dependency

Required fields:

```text
git
rev
```

Example:

```toml
[dependencies.metrics]
git = "https://example.invalid/metrics.git"
rev = "0123456789abcdef"
```

Both values are non-empty Strings.

`rev` is an exact immutable revision in the package model. Mutable branch/tag
selection is not a locked execution identity.

The fetched package's own manifest supplies its PackageId. The Git URL remains
retrieval/provenance metadata, not PackageId.

Schema v1 does not define network transport, Git protocol support, credential
handling, URL normalization, or revision lexical validation.

### Path dependency

Required field:

```text
path
```

Example:

```toml
[dependencies.fixture]
path = "../fixture"
```

The value is a non-empty String.

It is an explicit local development relation, not an ambient package search
path.

Workspace path interpretation, workspace membership validation and canonical
filesystem identity are owned by the existing workspace/package-resolution
policy.

D056 ratifies the immutable-consumption boundary: a `path` declaration is valid
only as mutable workspace/local-development input. When a manifest is consumed
as an immutable registry or exact-Git package instance, an operational `path`
dependency is invalid and normal package preflight fails closed rather than
resolving it against the consumer workspace, package store or ambient
filesystem.

This does not change ManifestV1 structural parsing and does not select a future
publication projection or override syntax. A future local/vendor/patch
substitution, if designed, must be explicit resolution-root-owned policy that
produces an ordinary exact graph; it is not inherited transitively from an
immutable package's `path` spelling. A future explicitly designed immutable
multi-package source/bundle model is also not prohibited by D056, but must define
its own package/source identity instead of implicitly reusing `path`.

### Source-form exclusivity

A dependency declaration is invalid if it mixes source forms.

Examples rejected by schema v1:

```toml
# registry + Git mixed
{ authority = "a", package = "p", version = "^1.0.0",
  git = "https://example.invalid/p.git", rev = "abc" }

# Git + path mixed
{ git = "https://example.invalid/p.git", rev = "abc",
  path = "../p" }

# incomplete registry source
{ package = "community/parser", version = "^1.0.0" }

# incomplete Git source
{ git = "https://example.invalid/p.git" }
```

There is no source-precedence rule. Invalid mixed/incomplete declarations fail
instead of choosing one source by field order.

### No dependency visibility field in schema v1

Earlier exploratory examples included:

```toml
visibility = "public"
```

but no focused package-policy audit has yet closed the observable meaning of
public transitive dependency exposure.

Schema v1 therefore deliberately **rejects** `visibility` rather than freezing an
illustrative field whose semantics are not owned.

Initial module resolution remains based on explicit direct dependency aliases
and package exports. A later design may add public dependency exposure in a new
manifest generation if the use case justifies the extra relation.

## `[workspace]`

Optional.

Allowed fields:

```text
members
```

### `workspace.members`

Required when `[workspace]` is present.

Array of non-empty Strings.

```toml
[workspace]
members = ["app", "parser", "fixture"]
```

No element is implicitly expanded, globbed, environment-interpolated, or treated
as a search path by schema v1.

Workspace path interpretation and validation belong to the later workspace
policy slice.

Duplicates are structurally invalid because one workspace member relation should
not be declared twice.

An empty members array is valid but has no additional member packages.

An empty `[workspace]` table is invalid.

### No virtual workspace root in schema v1

Every schema-v1 manifest still describes one package and therefore requires
`[package]`.

A workspace may group that package with additional member packages, but schema v1
does not introduce a second kind of manifest that is a workspace-only container
with no PackageId or package version.

A future need for virtual workspaces can be designed explicitly rather than
making package identity conditional on the presence of unrelated fields.

## Equivalent TOML representations

The schema operates on the parsed TOML data model, not source formatting.

For example, these dependency declarations are structurally equivalent:

```toml
[dependencies.parser]
authority = "protos-public"
package = "community/parser"
version = "^2.1.0"
```

```toml
[dependencies]
parser = {
  authority = "protos-public",
  package = "community/parser",
  version = "^2.1.0"
}
```

and dotted-key forms that TOML 1.0 defines as the same table are equivalent as
well.

The package model must not remember which TOML spelling produced the table.

## Unknown fields

Schema v1 is fail-closed.

Unknown fields fail at every owned level:

```text
root
package
compatibility
workspace
each dependency declaration
```

`exports` and `dependencies` intentionally use user-defined keys as export names
and dependency aliases respectively; those key sets are data, not open schema
names.

Misspellings therefore do not silently become ignored configuration.

## String handling

TOML string decoding produces semantic Strings.

After decoding, package tooling must not implicitly:

- interpolate `${...}` or `%...%`;
- expand `~`;
- normalize Unicode;
- case-fold identifiers;
- reinterpret a String as a filesystem path, URL, version, or constraint before
  the owning validator explicitly does so.

Equivalent TOML string syntaxes that decode to the same String are equivalent
manifest input.

## Integer handling

Schema v1 uses TOML Integer only for:

```text
manifest-version
```

No other schema-v1 field accepts Integer.

The parser must reject/represent according to TOML 1.0 integer syntax and the
TOML signed-64-bit value boundary before schema validation.

## Unsupported TOML value families

Schema v1 assigns no meaning to:

```text
Float
Offset Date-Time
Local Date-Time
Local Date
Local Time
Array of Tables
```

If encountered where schema v1 expects another family, validation fails.

This is not a claim that TOML lacks those values. It is the deliberate
intersection:

```text
TOML 1.0
    ∩
Protos manifest schema v1
```

## Canonical structural model

After TOML parsing and schema validation, the package tool should expose an
internal ordinary-Protos model conceptually equivalent to:

```text
ManifestV1 {
    package: {
        id: String
        version: String
        locator: String | null
    }

    compatibility: {
        language: String
    } | null

    exports: Map<String, String>

    dependencies: Map<String, Dependency>

    workspace: {
        members: Array<String>
    } | null
}
```

with dependency variants:

```text
RegistryDependency {
    authority: String
    package: String
    version: String
}

GitDependency {
    git: String
    rev: String
}

PathDependency {
    path: String
}
```

These are tooling data shapes, not new Core value families. Ordinary objects,
Maps, Arrays, Strings, Integers, booleans, and `null` are sufficient.

The package tool must not create privileged manifest/runtime object kinds merely
to represent this data.

## Read-only command consequence

The published package-tool manifest Slice 3 provides a read-only command that:

1. opens exactly `protos.toml` through its already-provisioned project-read
   Filesystem capability;
2. decodes it as UTF-8 through ordinary Core text I/O;
3. parses TOML 1.0;
4. validates schema generation 1;
5. constructs the internal ordinary-Protos manifest model;
6. reports success/failure without modifying project metadata.

That command:

- does not resolve dependency versions;
- does not read or rewrite `protos.lock` merely to validate the manifest;
- does not fetch packages;
- does not use network;
- does not mutate the manifest;
- does not perform package-store work.

This remains independent of B006.

## Implementation closure

The structural schema defined here is implemented and closed by
`TOOL001-C5`. `TOOL001-C6` composes that closed parser with the already
provisioned confined Filesystem as `protos package manifest`: exactly
`protos.toml` is read, UTF-8 text is consumed to EOF, `ManifestSchemaV1.parse`
constructs/validates the ordinary model, and Protos-owned diagnostics distinguish
read/decoding failure from TOML/schema failure.

`TOOL001-C7` closes the bounded historical manifest Slice 3 after cross-slice
validation and project/architecture reconciliation. No additional schema field,
semantic value grammar, resolver rule, lock policy, workspace interpretation,
network operation, package-store behavior or mutation policy is implied by that
closure.

## Relationship to later slices

### Version/constraint logic

`package.version` and registry dependency `version` are Strings at the structural
schema boundary.

The already-selected ReleaseVersion and constraint semantics are implemented as
pure Protos logic in the subsequent package-tool version/lock slice.

### Workspace policy

Schema v1 reserves only the explicit `workspace.members` data shape.

Discovery/root selection, member path validation, path dependency relationships,
and authoritative workspace lock ownership remain later workspace policy.

### Registry/network

The registry dependency shape keeps `AuthorityIdentity` separate from package
locator and retrieval endpoint.

Actual authority authentication, metadata discovery, mirrors, fetch, and
publication remain later networking/registry work.

### Metadata mutation

Schema v1 is read-only compatible.

`protos add`, `remove`, `resolve`, or any operation that must safely replace
`protos.toml` or `protos.lock` remains implementation-gated by I021/B006 closure.
D041 now defines the required general Filesystem namespace replacement/removal
semantics, but mutation must wait until that general capability is faithfully
implemented and provisioned to the bundled package tool.

## Rejected alternatives

### Reuse the illustrative manifest verbatim

Rejected because the earlier document explicitly marked its field names and
layout as illustrative.

Implementation must not promote examples to contracts accidentally.

### Use PackageId as the human package name

Rejected because the identity audit deliberately separates `PackageId` and
`PackageLocator`.

### Omit registry authority from dependency source

Rejected because ambient registry/mirror configuration must not silently change
the authority that defines a dependency relation.

Retrieval endpoints remain separately replaceable.

### Add `visibility` now

Rejected because its observable transitive-exposure semantics are not closed.

Schema v1 prefers a smaller direct-dependency model over freezing an unowned
policy field.

### Add open extension tables

Rejected. Unknown package fields fail and there is no `[tool.*]` escape hatch in
schema v1.

### Make workspace-only manifests

Rejected initially. Every v1 manifest describes a package; virtual workspace
roots can be added later only if real use demonstrates the need.

## Implementation gate for Slice 3B

Slice 3B may implement this schema when all of these remain true on current
`origin/main`:

- bundled package-tool bootstrap remains available;
- package-tool project Filesystem remains read-only and confined;
- B006 remains irrelevant to read-only manifest validation;
- TOML syntax parsing remains implemented in Protos;
- no package-specific Java parser or filesystem escape hatch is introduced;
- no unresolved Standard Library dependency is required for bootstrap.

If those conditions hold, no `LIBxxx` dependency blocks Slice 3B.

## TOOL001-F2B2 workspace/path semantic checkpoint

The structural schema above deliberately leaves Strings uninterpreted until
their semantic owner runs. `TOOL001-F2B2` now closes the initial workspace/path
interpretation used by the resolution-input model:

- only the active resolution-root manifest expands `workspace.members`;
- member declarations are canonical `/`-separated root-relative normal-component
  paths with no empty, `.` or `..` component;
- each member names exactly one package manifest at
  `<root>/<member>/protos.toml`;
- root/member PackageIds are unique;
- member manifests do not recursively expand their own `[workspace]` while
  consumed under an enclosing root;
- a path dependency resolves relative to its declaring participating package,
  may use `.`/`..` navigation, may not escape the root, and must finish at the
  root package or an explicitly declared member;
- path dependency semantic identity is its declared source kind plus resolved
  canonical target location/PackageId, not the original relative spelling.

These are package-tool resolution-root rules. They do not alter the schema-v1
TOML shapes or Core `Path` semantics.

F2B2 also gives `compatibility.language` its initial semantic value contract:
an opaque exact `LanguageCompatibilityId`. The current language generation is
`"0.1"`; absence means no declared restriction and presence requires exact
equality with the active resolution-context identifier.

## TOOL001-F2D1 runtime-name semantic checkpoint

The schema-v1 parser continues to preserve dependency aliases, export keys and
export values as decoded Strings. F2D1 now defines the initial runtime validator
applied when those Strings participate in package-backed module resolution.

A portable segment begins with an ASCII letter and continues with ASCII letters,
digits or `_`; Windows reserved device-name spellings are rejected
case-insensitively. A portable logical module name is one or more such segments
separated by literal `/`.

Runtime requirements:

- dependency alias: exactly one portable segment;
- `[exports]` key: one portable logical module name;
- `[exports]` value: one portable logical module name.

This is semantic validation above the structural manifest parser. Schema parsing
does not need to reject an otherwise structurally valid manifest merely because
a String is unsuitable for runtime module resolution; execution preflight fails
when that runtime invariant is required.

For package N:

```text
self:<logical-module>
```

selects that internal module directly.

For:

```text
dep:<alias>/<public-export>
```

the resolver follows N's exact locked dependency edge for `alias`, then maps
`public-export` through the target package's `[exports]` table to its internal
logical module. A dependency consumer cannot bypass `[exports]` by naming an
unexported source module.

Package-backed naming reuses the portable segment discipline of the standard
distribution but does not inherit the `std:`-specific first-segment `core`
reservation.
