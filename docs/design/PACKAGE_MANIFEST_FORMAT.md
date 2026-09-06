# Protos Package Manifest Format Audit

Status: exploratory architecture; non-normative
Related design: `docs/design/PACKAGE_DISTRIBUTION.md`

This document performs the focused manifest-format audit left open by the package
and library distribution design. It selects an initial exploratory encoding
without making package syntax part of Core language semantics, creating a
`LIBxxx` item, implementing package tooling, or deciding the physical lockfile
format.

The architectural distinction is fundamental:

```text
Package Manifest Model
    semantic package/tooling data and invariants

Manifest Encoding
    one concrete textual representation of that model
```

The encoding must not become the owner of package semantics. A field exists
because the Protos package model needs it, not because the chosen data format can
represent it.

## Requirements

The initial human-edited package manifest should be:

- declarative and non-executable;
- readable and reasonably pleasant to edit by hand;
- comment-friendly;
- deterministic to parse without host-language evaluation;
- independently parseable before any Protos package dependency is resolved;
- strict enough that misspelled or unsupported package fields fail clearly;
- extensible through an explicit Protos manifest-schema version rather than
  accidental parser permissiveness;
- portable across implementations and operating systems;
- independent of registry credentials, cache paths, mirrors, user home
  directories, environment variables, and other machine-local configuration;
- suitable for package, dependency, export, compatibility, and workspace
  metadata without becoming a general repository configuration dumping ground.

The manifest does not need to optimize for machine-only canonical serialization.
That is a separate requirement for `protos.lock` and any future registry metadata
or package artifact descriptors.

## Formats considered

### TOML

TOML is specifically designed as a human-readable configuration format that maps
unambiguously to tables. It provides comments, strings, arrays, tables, inline
tables, arrays of tables, booleans, integers, floats, and date/time values.

Strengths for Protos:

- comments and low visual noise for common package metadata;
- mature implementations and editor/tooling support;
- tables naturally represent package sections and dependency aliases;
- no code evaluation is necessary;
- package tooling can parse it before the Protos runtime/library resolver exists;
- Cargo and Python packaging demonstrate that non-trivial package metadata fits
  into TOML without requiring an executable manifest.

Risks:

- TOML itself evolves; accepting whatever the host parser calls "latest" would
  make manifest acceptance depend on the toolchain's parser version;
- TOML has more value types than the Protos package model initially needs;
- unconstrained table namespaces can turn one manifest into a catch-all tool
  configuration file;
- using a host TOML library without an explicit supported dialect could create
  implementation-dependent package acceptance.

These risks are manageable if Protos owns a strict schema and an explicit TOML
version boundary.

### Dedicated Protos package syntax

A small domain-specific file similar in spirit to `go.mod` could encode exactly
what the package manager needs and nothing else.

Example only:

```text
package example/widget 1.4.0
language 0.1
require parser community/parser ^2.1
export Parser parser/Parser
```

Advantages:

- extremely small grammar;
- no irrelevant value types;
- package semantics and textual concepts can align closely;
- bootstrap parser can be purpose-built and dependency-free.

Costs:

- Protos would create and permanently maintain another language grammar;
- editors, syntax highlighters, formatters, schema tooling, and generic parsers
  would need Protos-specific support;
- syntax evolution becomes a package-manager language-design problem;
- the benefit is currently too small because the package model maps cleanly to a
  restricted TOML schema.

A dedicated syntax should be reconsidered only if the real manifest model later
becomes materially awkward in TOML, not merely because a custom format could be
made smaller.

### JSON

JSON is ubiquitous, has a simple data model, and is easy to parse in the current
host implementation.

It is rejected as the preferred human-edited manifest because ordinary JSON has
no comments and becomes noisy for dependency-heavy files. Its main advantage is
parser ubiquity rather than a better package-authoring model.

JSON may remain suitable for machine-to-machine protocols or registry APIs where
human comments and hand editing are not requirements.

### YAML

YAML is rejected for the initial manifest. Its substantially larger syntax and
implicit/data-model surface are not justified by the package metadata required
here. Protos should not pay that parser and ambiguity cost for configuration that
fits in a smaller model.

### Executable manifest

An executable Protos manifest and a SwiftPM-style executable host-language
manifest were considered and rejected for the initial package model.

An executable manifest creates bootstrap, determinism, authority, caching, and
security questions before dependencies can even be resolved. It also encourages
package structure to depend on machine state or arbitrary computation. Protos
package metadata should remain data.

If a future build system needs computation, that should be an explicit build
mechanism with declared inputs, outputs, capabilities, and reproducibility rules,
not a reason to make dependency metadata executable.

## Selected exploratory direction

Use:

```text
protos.toml
```

as the human-edited package manifest, encoded using **TOML 1.0 syntax plus a
strict Protos manifest schema** for the first package-format generation.

This is deliberately not equivalent to:

> Accept any valid TOML supported by the parser bundled with this implementation.

The accepted language is instead:

```text
TOML 1.0 grammar
        ∩
fields and value shapes defined by Protos manifest schema version 1
```

A parser that also understands TOML 1.1 or later must still reject syntax or
value forms that are outside the manifest generation's declared TOML dialect.
Adopting a later TOML version is an explicit package-tooling format evolution.

### Why TOML 1.0 rather than "latest"

TOML 1.0 has a long-established stable specification and broad parser support.
TOML 1.1 is a legitimate later evolution, but its existence demonstrates why a
Protos manifest cannot bind acceptance to an unversioned external notion of
"current TOML".

The initial schema does not need TOML 1.1-specific facilities. Selecting the
older stable dialect therefore reduces bootstrap/parser compatibility pressure
without preventing a future manifest generation from adopting TOML 1.1 or later
when there is an actual benefit.

## Schema owns meaning

TOML native types do not automatically become Protos package concepts.

For manifest schema version 1, package fields should be deliberately narrow.
Likely useful representation categories are:

- UTF-8 strings for names, versions, constraints, module names, revisions, and
  source identifiers;
- booleans only where a genuine binary package property exists;
- non-negative integers for format/schema versions or similarly bounded metadata;
- arrays where ordering or a collection of declarations is genuinely part of the
  package model;
- tables for structurally grouped package data.

TOML date/time and floating-point values should have no package meaning merely
because TOML supports them. A conforming parser may lex/parse those TOML values
but the Protos schema must reject them unless a future schema explicitly assigns
such a field a meaning.

Likewise, TOML comments are non-semantic. They cannot alter package resolution,
identity, publication, or lock generation.

## Required manifest-version boundary

The manifest should carry an explicit Protos schema/format generation from the
first implementation. Illustratively:

```toml
manifest-version = 1
```

The exact field spelling remains an implementation-design decision until package
work is promoted, but the versioning property is required.

An implementation encountering a newer unsupported manifest generation must fail
clearly rather than reinterpret unknown fields under older rules.

Within a supported manifest generation, unknown Protos-owned package fields
should also fail instead of being silently ignored. This catches misspellings and
prevents an older toolchain from pretending it understood newer package policy.

Forward evolution therefore happens by a declared schema generation, not by
"ignore whatever you do not know" permissiveness.

## Stress-test shape

The following is **illustrative only**. It demonstrates that the difficult
metadata categories fit cleanly in TOML; it does not freeze field names, version
constraint syntax, package naming rules, source syntax, or workspace semantics.

```toml
manifest-version = 1

[package]
name = "example/widget"
version = "1.4.0"

[compatibility]
language = "0.1"

[exports]
Widget = "Widget"
Parser = "parser/Parser"

[dependencies.parser]
package = "community/parser"
version = "^2.1"
visibility = "public"

[dependencies.metrics]
git = "https://example.invalid/metrics.git"
rev = "0123456789abcdef"
visibility = "private"

[dependencies.local_fixture]
path = "../fixture"
visibility = "private"

[workspace]
members = ["app", "parser", "fixture"]
```

The example intentionally uses expanded dependency tables instead of requiring
compact inline-table syntax. A future formatter may choose a canonical style, but
the package model must not depend on whether equivalent TOML was written inline
or expanded.

The stress test covers:

- package identity/version metadata;
- language compatibility separate from implementation/toolchain version;
- explicit exported module names rather than exposing every internal path;
- registry-style dependency requirements;
- exact Git revision sources;
- explicit local/path development dependencies;
- public/private dependency exposure metadata;
- workspace membership.

No case requires executable metadata or a custom package grammar.

## Deliberate restrictions

### No open `[tool.*]` namespace

`protos.toml` should not become the repository's universal configuration file.
The initial package schema should not provide an unrestricted namespace where
formatters, IDEs, linters, coverage tools, or unrelated applications accumulate
configuration.

Package/build metadata that genuinely affects package identity, dependency
resolution, publication, exported modules, or an explicitly designed build
contract may belong in the package manifest. Editor and unrelated tool policy
does not.

This deliberately avoids the broad configuration role that `pyproject.toml` has
acquired through its general `[tool]` namespace.

### No dynamic metadata

Published package metadata should be statically present or derived only by a
separately specified deterministic packaging step. The initial manifest has no
Python-style generic `dynamic` escape hatch that delegates package metadata to an
arbitrary build backend.

Dependency identity, version requirements, exports, and compatibility rules must
not depend on running arbitrary package code merely to discover what the package
claims to be.

### No environment interpolation

Strings such as:

```text
${HOME}
${TOKEN}
%USERPROFILE%
```

have no implicit interpolation semantics in the package manifest.

Machine-local credentials, proxy settings, cache roots, registry mirrors, and
similar configuration belong to tool/user configuration outside the package
manifest. A local `path` dependency, when supported, is interpreted according to
explicit package/workspace path rules rather than environment-variable expansion.

### No manifest includes or search path

The first package model should use one manifest at the package root. It should not
implicitly merge parent, user, system, or included configuration files to obtain
the package graph.

Workspace composition is explicit package metadata, not manifest discovery by
walking arbitrary configuration inheritance chains.

### No platform-conditional dependency language initially

The first schema should not add arbitrary condition expressions for operating
system, architecture, environment variables, or host implementation. Platform
artifacts and target-specific dependencies require a separately audited target /
build model so that the locked graph remains explainable and reproducible.

## Bootstrap and implementation boundary

The package manager must read the manifest before it can resolve/load Protos
packages. Therefore manifest parsing is toolchain bootstrap functionality.

A future `std:toml/...` library must not be required in order to parse
`protos.toml`; that would create a package-resolution cycle. The host toolchain
may:

- embed a small conforming TOML parser;
- depend on a normal host-language TOML parser as part of the toolchain build;
- use another implementation strategy that accepts exactly the Protos-declared
  TOML dialect and schema.

Whichever strategy is chosen, parser implementation details are not package
semantics. Conformance tests must fix the accepted TOML/version/schema behavior.

The current Java implementation has no TOML dependency in `pom.xml`. Selecting
TOML as the manifest encoding therefore does **not** yet imply adding a specific
Java library. Parser/library selection belongs to the implementation slice and
must be audited for compatibility, footprint, licensing, and exact TOML-version
support at that time.

## Manifest versus lockfile

Selecting TOML for `protos.toml` does not select TOML for `protos.lock`.

The two files have different authors and optimization goals:

```text
protos.toml
    human-authored
    comments useful
    readability important
    schema-oriented

protos.lock
    machine-authored
    canonical serialization required
    deterministic ordering required
    no semantic comments
    no machine-local data
```

The physical lockfile encoding remains open. It may later be a canonical textual
format, a restricted TOML representation, JSON-like data, or another explicitly
versioned machine format. The lockfile design must be chosen from lockfile
requirements rather than from a desire to reuse the manifest parser.

## Comparison conclusions

| Candidate | Human editing | Bootstrap safety | Schema precision | Existing tooling | New Protos grammar cost | Recommendation |
| --- | --- | --- | --- | --- | --- | --- |
| TOML 1.0 + strict Protos schema | strong | strong | strong when constrained | strong | none | **selected exploratory direction** |
| dedicated `go.mod`-style syntax | strong | strong | strongest | weak initially | high/permanent | reserve as fallback |
| JSON | adequate/verbose | strong | strong | strongest | none | machine protocols, not preferred manifest |
| YAML | strong superficially | strong if parser trusted | broader than needed | strong | none | reject initial manifest |
| executable Protos/host manifest | flexible | weak | code-defined | language-specific | runtime/evaluation semantics | reject initial manifest |

The important result is not merely "TOML won". It is:

> Protos owns a small, versioned package-manifest model. TOML 1.0 is the initial
> proposed human syntax for writing that model.

That separation preserves the option to migrate the textual encoding in a future
major package-format generation without redefining package identity or dependency
semantics.

## Prior-art conclusions

### Cargo

Cargo demonstrates that substantial package metadata, dependencies, targets, and
workspace information can be represented in TOML. It also demonstrates that
manifest complexity comes primarily from the package feature model and resolver
semantics, not from TOML itself.

Cargo's later addition of TOML 1.1 support is a useful warning for Protos: the
accepted manifest dialect must be explicit rather than inherited from whichever
parser version happens to ship in the toolchain.

### Go modules

`go.mod` demonstrates the value of a very small purpose-built dependency grammar.
Its format is attractive when the domain is sufficiently constrained, but Protos
currently gains little by creating a new manifest language because the proposed
schema remains natural in TOML.

Go also couples module paths closely to source/retrieval identity; the broader
Protos package design intentionally keeps that question separate.

### Python `pyproject.toml`

Python demonstrates that TOML can serve as standardized package metadata, but its
open `[tool]` namespace and dynamic/build-backend facilities also show how a
package manifest can become a general repository-configuration hub.

Protos should adopt the useful human-readable data format without adopting that
open-ended ownership model.

### Swift Package Manager

SwiftPM demonstrates the expressive power of an executable manifest and also the
need to version its manifest API/tool semantics. Protos does not currently need
that computational power. Static package data better preserves bootstrap
simplicity, authority minimization, and reproducibility.

### npm

`package.json` demonstrates the interoperability advantage of a ubiquitous data
format, but JSON's lack of comments and npm's broad package/configuration surface
are not desirable defaults for a hand-maintained Protos manifest.

## Decision durability

The following are intended to survive even if the physical encoding changes:

- package metadata is declarative data, not executable code;
- package semantics are owned by a Protos schema/model, not the serialization
  language;
- manifest schema generations are explicit;
- unknown package fields do not silently acquire meaning;
- no ambient user/system configuration changes dependency identity;
- package manifests do not become an unrestricted third-party tool namespace;
- lockfile encoding is an independent design decision;
- manifest parsing belongs to toolchain bootstrap and cannot depend on packages
  that require the manifest to resolve.

The following remain deliberately replaceable:

- the exact `protos.toml` field names and table layout before implementation;
- the exact version-constraint grammar;
- exact package/source naming syntax;
- TOML 1.0 itself in a future explicitly versioned package-format generation;
- the host parser/library used by any implementation.

## Recommendation before implementation

The package-system implementation audit should proceed under this working rule:

```text
HUMAN MANIFEST
    protos.toml
    TOML 1.0 syntax
    strict Protos manifest schema version 1
    declarative/static
    no [tool.*] catch-all
    no environment interpolation
    no arbitrary includes

LOCKFILE
    protos.lock
    format still independently undecided

BOOTSTRAP
    manifest parser is toolchain functionality
    not stdlib/package functionality
```

Before the first executable package-manager slice, freeze the schema fields
required by that slice and add manifest conformance fixtures that distinguish:

- valid TOML 1.0 + valid Protos schema;
- valid TOML but invalid/unknown Protos package fields;
- syntax accepted only by newer TOML versions;
- machine-local/environment interpolation attempts;
- malformed dependency/source combinations;
- unsupported manifest generations.
