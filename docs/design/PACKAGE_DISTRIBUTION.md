# Protos Package and Library Distribution Design

Status: exploratory architecture; non-normative

This document explores what a Protos library is as a distribution concept and
how third-party packages should be acquired, resolved, cached, imported, and
published. It does not create a `LIBxxx` work item, define new Core language
semantics, reserve package syntax, or commit the project to a public registry.

The normative module semantics in `spec/semantics/MODULES.md` remain
unchanged. In particular, Core owns module instances, canonical `ModuleKey`
identity after resolution, Actor-local module caching, initialization, cycles,
and failure. Package location and package-manager policy live on the host/tooling
side of that existing resolution boundary.

## Problem statement

Protos needs distributable libraries without recreating an environment model in
which the set of importable dependencies depends on which interpreter happens to
be active, which user/site directory is on a search path, or which packages an
administrator installed globally.

The target property is:

> A project determines its dependency graph from repository-controlled metadata.
> Machine-global state may make exact artifacts faster to obtain, but it must not
> change which package or version an import resolves to.

This separates two questions that are often accidentally coupled:

```text
Which Protos toolchain am I running?

Which third-party packages does this project depend on?
```

The operating system may install a Protos toolchain. A user may keep several
Protos toolchains. Neither fact makes arbitrary third-party libraries visible to
programs.

## Existing Protos foundations

The current language/runtime architecture already provides a strong base for this
model:

- a module specifier is an exact semantic String passed to a host resolver;
- Core assigns no intrinsic filesystem, URL, package, or registry meaning to that
  String;
- successful resolution yields a canonical `ModuleKey`;
- module identity and Actor-local caching use the canonical `ModuleKey`, not the
  original spelling;
- module instances and their mutable top-level state are Actor-local;
- `std:` already names modules in the Protos standard distribution through an
  absolute, case-sensitive, portable logical identity;
- `std:` does not participate in project/user search paths and cannot be
  satisfied by an unrelated local module.

The current standalone CLI wires only the standard-distribution resolver, so a
third-party/project package resolver does not yet exist in production. Package
distribution should extend the existing resolver architecture rather than add a
second import or global-environment mechanism.

## Terminology

### Module

A **module** is the existing Core runtime/import unit. It executes in one
Actor-local module context and produces one Actor-local module instance.

A package manager does not redefine module semantics.

### Package

A **package** is the versioned distribution unit: an immutable release containing
one or more Protos modules plus package metadata and, where later justified,
ordinary package resources.

A package is not a runtime object family and does not automatically execute when
it is downloaded.

### Library

A **library** is a role, not a separate technical container. A package intended
primarily to expose modules for other packages is a library package. An
application/tool package may also expose importable modules.

This avoids creating parallel concepts such as "library environment" versus
"application environment".

### Standard Library

The Protos **Standard Library** remains part of the versioned Protos toolchain /
standard distribution and is addressed through `std:`. It is not selected from
the third-party package graph and cannot be replaced by a project dependency.

### Workspace

A **workspace** is development metadata grouping local packages. It may provide
explicit local dependency overrides for development, but it does not become a
runtime search path.

### Registry

A **registry** is a distribution/discovery service that maps published package
identities and versions to immutable package artifacts and metadata. A registry
is not part of Core module semantics and need not be the only package source.

### Store / cache

A **package store** is machine-local storage for immutable package artifacts,
preferably keyed by cryptographic content identity. It is a cache, not an
installation environment.

## Primary design rule: declare libraries; do not install them into an environment

For project dependencies, Protos should not have an operation analogous to
"install this library into the current interpreter".

Instead:

```text
protos add <dependency>
        ↓
project manifest changes
        ↓
resolver computes exact dependency graph
        ↓
project lockfile records that graph
        ↓
exact immutable artifacts are fetched/cached
```

The important consequence is:

> A package being present on the machine does not make it importable.

The resolver considers the current package's declared/locked dependency graph.
It does not scan system directories, user directories, parent directories,
`PATH`, an ambient package path, or every artifact present in the cache.

Protos should have no `PYTHONPATH`-like general package search-path environment
variable in the initial package model.

## Selected dependency model

### Repository-controlled manifest

A package root should contain a human-edited manifest. `protos.toml` is the
current recommended exploratory filename because package tooling must be able to
read it before Protos library code is running and TOML is already a familiar,
non-executable manifest format.

Illustrative shape only:

```toml
[package]
name = "example/widget"
version = "1.4.0"
protos = ">=0.2,<0.3"

[dependencies]
parser = { package = "community/parser", version = "^2.1" }
metrics = { git = "https://example.invalid/metrics.git", rev = "..." }
```

This syntax is not yet committed API. The important contract is that direct
package dependencies and their constraints are explicit project metadata.

### Repository-controlled lockfile

A generated `protos.lock` should record the exact resolved transitive graph,
including for each package instance at least:

- canonical package source identity;
- package name/identity;
- exact version or exact VCS revision;
- cryptographic artifact/content digest;
- exact dependency edges/aliases needed to reconstruct resolution.

The root project's lockfile is authoritative for execution/build tooling. A
package dependency's own development lockfile must not override the root
resolution graph when that package is consumed as a dependency.

The lockfile should normally be committed for applications and for library
package development so tests and CI reproduce the same graph. Consumers of a
published library resolve from the library's published manifest constraints, not
from the library author's private development lock graph.

### Resolution changes are explicit

Normal `run`, `test`, or build operations should not silently select newer
versions or rewrite the lockfile.

Version selection should occur only through explicit dependency-management
operations such as:

```text
protos add
protos remove
protos resolve
protos update
```

Execution may fetch an artifact already named exactly by the lockfile before the
Protos program starts. Fetching an already-selected immutable artifact does not
change dependency resolution.

If the manifest and lockfile disagree, ordinary execution should fail with a
clear package-state error rather than silently updating dependencies.

This deliberately makes "locked" behavior the normal mode instead of a special
CI flag.

## Import namespaces

The package design should preserve the existing explicit `std:` namespace and
avoid bare-name search.

The current preferred exploratory resolver namespaces are:

```text
std:<logical-module>
    module shipped by the selected Protos standard distribution

self:<logical-module>
    module inside the current package

dep:<dependency-alias>/<logical-module>
    module inside one dependency explicitly declared by the current package
```

For example:

```protos
arrays: import("std:collections/Array")
parser: import("dep:parser/Parser")
helpers: import("self:internal/Helpers")
```

`self:` and `dep:` are proposed host/toolchain resolution policy, not new Core
syntax. They use the existing `import(String)` mechanism.

### Why dependency aliases are useful

Source code should refer to the dependency relation declared by its own package,
not to a machine location or registry URL. This allows a manifest to choose a
registry package, Git source, private mirror, or local workspace replacement
without rewriting every import.

Aliases also permit explicitly distinguished incompatible versions when
necessary without introducing hidden search-order rules.

Direct imports such as `import("pkg:some-registry/...@latest")` are rejected as
the default model because they would move version/source resolution back into
program source and encourage network- or environment-dependent execution.

## Canonical package-backed ModuleKey

A package-backed module's canonical `ModuleKey` should identify the resolved
package instance plus the package-internal logical module name. Conceptually:

```text
(package source identity,
 package identity,
 exact resolved version/revision,
 verified content identity,
 logical module name)
```

The external serialized representation remains an implementation/toolchain
choice. The important invariant is that two different package versions or
contents cannot accidentally collapse to the same `ModuleKey`, while alternate
cache locations for the exact same locked artifact do not create different
module identities.

This composes directly with the existing Actor-local module cache.

## Package store: global storage without global visibility

The package manager should use an immutable cache/store model inspired by Cargo,
Go modules, and content-addressed package stores:

```text
project manifest + lock
          |
          v
exact package identity + digest
          |
          v
one or more cache/store backends
```

The physical cache location is deliberately non-semantic. A user cache, a
system-wide read-only cache, a CI cache, or a vendored/offline cache may all
supply the exact same locked artifact.

A cache lookup is by exact resolved identity/digest, never by "find me a package
named foo and choose whatever is installed".

Therefore cache search order may affect performance but must not affect package
selection or program behavior.

### Suggested local storage split

Keep source artifacts and generated compilation data separate:

```text
package store
    immutable verified package sources/artifacts

build cache
    disposable compiler/runtime products keyed by
    package content + Protos toolchain + target/configuration
```

Deleting either cache must not change the dependency graph. It may only force
redownload/recompilation.

## No virtual environment activation

The normal workflow should not require:

```text
source .venv/bin/activate
activate.ps1
select a site-packages directory
set a package search path
```

Running a Protos command inside a package tree should discover the package root
from repository metadata, then use that package's manifest and lockfile.

If several Protos toolchains are installed, toolchain selection is a separate
problem. A project may declare its supported/required Protos toolchain range, and
a toolchain manager may select an installed compatible version. That does not
change the package graph.

This keeps:

```text
runtime/toolchain selection
```

orthogonal to:

```text
project dependency selection
```

## Standard Library versioning

`std:` belongs to the Protos toolchain/standard distribution, not to a registry
resolution graph.

A project that runs under Protos toolchain version X receives the standard
library shipped/defined for that toolchain compatibility contract. A third-party
package must not publish something that shadows `std:math/Math`,
`std:collections/Array`, or another `std:` identity.

If future Protos compatibility policy allows a Standard Library component to
version independently, that must be an explicit architecture change rather than
falling through to ordinary package lookup.

## Dependency version coexistence

Avoid Maven-style implicit "nearest wins" mediation and avoid requiring one
global machine version of each library.

The preferred resolver direction is:

- unify compatible dependency requirements when possible;
- permit multiple incompatible package versions in one locked graph when needed;
- resolve each `dep:` edge in the context of the importing package's own declared
  dependencies;
- give each resolved package instance distinct canonical identity.

This is closer to Cargo/npm dependency-graph coexistence than to a flat global
classpath.

Multiple versions can be observably important if packages expose
prototype/identity-bearing objects from dependencies. Package/API design should
therefore avoid casually leaking dependency-owned prototype identities across
public boundaries. A later package-system design may add diagnostics for such
version-sensitive public dependency exposure, but should not solve it with a
hidden global singleton version.

The exact version-solving algorithm is intentionally left for a focused resolver
design. It must be deterministic and explain conflicts; "first/nearest found"
is not acceptable.

## Local development and workspaces

Local development overrides must be explicit repository metadata, not ambient
machine state.

A workspace may bind a dependency alias to a sibling package or local path. A
published package must not depend on an accidental developer filesystem path;
publishing either rejects unresolved local-only dependencies or resolves them to
explicit publishable package identities according to a future publishing
contract.

A local override changes dependency source intentionally and visibly. Merely
having a checkout with the same package name somewhere on disk does nothing.

## Offline and vendored operation

Offline operation should be first-class:

```text
protos fetch
    populate all exact locked artifacts

protos run --offline
    refuse network access and use only already available exact artifacts
```

An optional vendor/export command may materialize the locked dependency closure
inside a project or deployment bundle. Vendoring is a storage backend for the
same locked identities; it must not introduce a second dependency-resolution
algorithm based on directory traversal.

## Publishing model

### Immutable releases

A published `(source, package identity, version)` must identify immutable
content. Publishing different content under an already-published version is
rejected.

A registry may support *yanking* a bad release from new version selection while
still allowing existing lockfiles to retrieve the immutable artifact. Deleting
or replacing historical content should not be the normal package lifecycle.

### Verified artifacts

Published package artifacts should have a canonical cryptographic digest. The
resolved digest is recorded in the lockfile and verified after download,
regardless of whether the bytes came from the origin registry, a mirror, a CI
cache, or a local store.

Signatures, transparency logs, and registry trust policy are valuable future
layers, but mandatory content verification is the minimum foundation.

### Deterministic package archive

A future `protos package` command should create a deterministic package artifact
from declared package contents. File order, excluded build products, metadata,
and normalization must be defined well enough that the artifact digest is stable
for the same package content.

The initial package format should be source-oriented and portable. Native binary
extensions, platform-specific artifacts, and package install/build scripts are
out of scope until their capability, ABI, trust, sandbox, and reproducibility
boundaries are designed explicitly.

## No install-time code execution in the initial model

Downloading/fetching a package must not execute package code.

The initial package model should have no npm-style install scripts or arbitrary
package-manager lifecycle hooks. A module's ordinary top-level code executes only
when that module is imported under the existing module initialization semantics.

This significantly reduces supply-chain authority and keeps package acquisition
a data operation.

If native compilation or code generation is needed later, it should be designed
as an explicit build capability with declared inputs/outputs and sandbox/trust
policy rather than introduced as an unconstrained installation hook.

## Executable tools are separate from library visibility

A future command such as:

```text
protos install <tool-package>
```

may install a command-line launcher into a user-selected binary directory. That
is an executable-tool deployment feature, not a library installation mechanism.

Installing a tool globally must not make that tool package or its dependencies
importable by unrelated Protos projects. The installed launcher continues to
reference its own exact package/dependency graph in the package store.

This mirrors the useful Cargo distinction between project dependencies and
`cargo install` while avoiding a global library namespace.

## Script mode

A standalone `.protos` file with no package manifest should not inherit arbitrary
third-party dependencies from the machine.

The safe initial rule is:

```text
standalone script
    may use Core / std:
    may use explicitly defined local-module policy
    cannot use dep: because there is no declared dependency graph
```

A script needing third-party dependencies becomes a package by adding project
metadata. This prevents "works on my machine because I installed foo globally".

## Registry and transport independence

The dependency model must not require one public central registry to be
semantically valid.

Supported sources can eventually include:

- a default public registry;
- private registries;
- registry mirrors/proxies;
- exact Git/VCS revisions;
- explicit local workspace/path dependencies.

Registry credentials, proxy configuration, cache paths, and mirrors are user /
machine tooling configuration. They may affect *how* an exact artifact is
obtained, but after the lockfile has fixed package identity and content they must
not silently change *what* artifact is selected.

Package source identity must be explicit enough that two registries publishing
the same textual package name do not accidentally become the same package.

## Prior-art comparison

### Python / pip / venv

Python virtual environments provide useful isolation, but the interpreter
environment also participates in dependency visibility: a virtual environment
has its own `site-packages`, may optionally expose system site-packages, Python
also has a per-user site directory, and module lookup depends on path
initialization/customization.

**Take:** isolation is valuable, but Protos should not make "which environment is
active?" determine the package graph. Reject ambient system/user package search
and activation as the primary dependency model.

### npm / Node.js

npm defaults dependencies to project-local `node_modules` and its lockfile can
record an exact install tree. Workspaces provide explicit local development
linking. These are useful properties. npm also has a separate global install
mode, physical `node_modules` hierarchy/search behavior, and package lifecycle
scripts.

**Take:** adopt repository-owned manifest/lock and explicit workspaces. Do not
adopt global libraries as import candidates, physical nearest-directory package
resolution, or arbitrary install-time scripts.

### Cargo / Rust

Cargo clearly separates `Cargo.toml` requirements from `Cargo.lock` exact
resolution. Downloaded registry/Git dependencies live in a user cache under
Cargo home, but that cache is storage rather than the package manifest. Cargo
also distinguishes project dependencies from globally installed executable
tools through `cargo install`.

**Take:** this is the closest overall workflow precedent for Protos. Adopt the
manifest/lock/store separation and tool-install distinction, while keeping Protos
module/Actor semantics independent of Cargo's crate/type system.

### Go Modules

Go defines a module as a collection of packages versioned/distributed together,
uses `go.mod` for dependency requirements, caches downloaded modules globally,
and authenticates specific module versions with cryptographic hashes in
`go.sum` / the checksum system. Modern module mode no longer uses GOPATH as the
project dependency namespace.

**Take:** strongly validates that a global cache does not require a global
import environment. Adopt immutable versioned artifacts, content verification,
and project-defined dependency graphs. Do not copy Go's module-path/URL coupling
or minimal-version-selection algorithm without a separate resolver audit.

### Maven

Maven separates project dependency declarations in the POM from a local artifact
repository/cache and remote repositories, and handles transitive dependencies.
This demonstrates that a per-user artifact repository can be merely a cache for
a project-defined classpath. However Maven dependency mediation and mutable
SNAPSHOT conventions can make selected artifacts less explicit than desired.

**Take:** adopt the local-cache/remote-source separation and transitive metadata.
Reject implicit nearest-wins mediation and mutable release identities.

### Nix store model

Nix demonstrates a stronger separation between names and immutable store objects:
store objects are immutable and may be addressed by digest-derived identities.
Its complete build/environment model is intentionally much broader than Protos
needs for language packages.

**Take:** borrow the content-addressed immutable-store principle, not the whole
Nix deployment language or system-environment model.

## Primary prior-art references

The comparison above was based primarily on official ecosystem documentation:

- Python virtual environments: https://docs.python.org/3/library/venv.html
- Python site/user package paths: https://docs.python.org/3/library/site.html
- npm package lock: https://docs.npmjs.com/cli/v11/configuring-npm/package-lock-json/
- npm package folders/local-vs-global model: https://docs.npmjs.com/files/folders/
- npm workspaces: https://docs.npmjs.com/cli/using-npm/workspaces/
- Cargo manifest: https://doc.rust-lang.org/cargo/reference/manifest.html
- Cargo manifest versus lockfile: https://doc.rust-lang.org/cargo/guide/cargo-toml-vs-cargo-lock.html
- Cargo dependency resolver: https://doc.rust-lang.org/cargo/reference/resolver.html
- Cargo cache/home: https://doc.rust-lang.org/cargo/commands/cargo.html
- Go Modules reference: https://go.dev/ref/mod
- Go module manifest reference: https://go.dev/doc/modules/gomod-ref
- Maven repositories: https://maven.apache.org/guides/introduction/introduction-to-repositories
- Maven dependency mechanism: https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism
- Nix store: https://nix.dev/manual/nix/2.35/store/
- Nix content-addressing glossary: https://nix.dev/manual/nix/2.35/glossary

## Rejected initial models

### System/user/site package search

Rejected. The same source could resolve differently depending on machine-global
installation state.

### Virtual environments as the dependency namespace

Rejected as the primary model. Disposable development environments may still be
useful operationally, but they must not be required to define package identity or
dependency visibility.

### Registry URLs or versions embedded in every import

Rejected. Dependency source/version selection belongs in package metadata and
lock resolution, while module source code names a declared dependency relation.

### One mutable global installed version per library

Rejected. It prevents independent projects from selecting different versions and
turns machine administration into program semantics.

### Flat global classpath/search path

Rejected. Resolution by first/nearest matching artifact is difficult to reason
about and lets unrelated machine state change module identity.

### Mutable package versions

Rejected for normal releases. Exact version/revision plus content identity must
be reproducible.

### Package install hooks

Rejected initially. Fetching a dependency should not execute arbitrary code.

## Security and authority consequences

Importing a module executes its top-level code under ordinary Protos module
semantics, so dependencies are executable code once imported. Package download
itself should nevertheless remain authority-minimal.

A package does not gain Filesystem, Process, networking, entropy, or other live
host authority merely because it was downloaded or because it came from a
trusted registry. Runtime authority continues to follow the explicit Protos
capability design.

Package metadata should also not be able to mutate host configuration, select a
different Protos executable, or inject ambient import paths during normal
resolution.

## Suggested tool workflow

Illustrative commands:

```text
protos init
protos add <package>@<constraint>
protos remove <alias>
protos resolve
protos update [alias]
protos fetch
protos run [--offline]
protos test [--offline]
protos package
protos publish
protos cache gc
protos install <tool-package>@<version>
```

The important behavioral split is:

```text
add / remove / resolve / update
    may change dependency selection and lockfile

fetch / run / test
    use the already locked selection

cache gc
    changes only local storage

install tool
    changes command availability, not project import visibility
```

## Recommended first implementation boundary

If promoted from exploration to project work, do not attempt registry,
publishing, workspaces, toolchain management, and every package source at once.
Use cost-aware slices.

A plausible sequence is:

1. **Package model and local locked graph**
   - manifest + lockfile data model;
   - `self:` / `dep:` resolver semantics at the official toolchain layer;
   - package-backed canonical `ModuleKey`;
   - no network and no registry yet.

2. **Immutable package store**
   - exact content-digest addressing;
   - fetch/materialization from an explicit artifact source;
   - offline behavior;
   - store never participates in name search.

3. **Version resolver**
   - deterministic constraint solver;
   - conflict diagnostics;
   - compatible-version unification with explicit coexistence when necessary;
   - explicit lock updates only.

4. **Registry protocol and publishing**
   - immutable package versions;
   - package artifact format/checksum;
   - yanking without mutation;
   - authentication/private registry boundaries.

5. **Workspaces/local overrides**
   - explicit repository-local development replacement;
   - publishing validation against accidental path dependencies.

6. **Executable installation / toolchains**
   - global executable launchers remain separate from package imports;
   - optional multi-toolchain selection remains separate from dependency graph.

Native extensions/install hooks remain outside this sequence until independently
designed.

## Open design questions before implementation

The architecture above intentionally leaves several choices for focused design:

- exact manifest/lock syntax and schema versioning;
- canonical public package naming/scoping rules;
- whether the first package source is registry, Git, local archive, or a minimal
  combination;
- exact deterministic version-solving algorithm;
- exact package archive canonicalization and digest algorithm/version marker;
- whether package resources beyond `.protos` source are included initially;
- exact package/toolchain compatibility declaration;
- registry signing/transparency policy beyond mandatory content digests;
- diagnostics for public APIs that expose dependency-owned prototype identities;
- whether standalone script local imports receive an explicit `file:` or
  synthetic-`self:` policy.

None of these questions requires Python-style environment activation or ambient
package visibility.

## Current recommendation

```text
MODULE
    existing Core import/runtime unit

PACKAGE
    immutable versioned distribution unit containing modules + metadata

LIBRARY
    a package role, not a separate runtime kind

STANDARD LIBRARY
    shipped with the Protos toolchain; absolute std: identities

PROJECT DEPENDENCIES
    declared in protos.toml
    exactly resolved in protos.lock

PACKAGE STORE
    immutable/cache-only
    may be user/system/CI/vendor backed
    never searched by package name
    presence never makes a package importable

IMPORTS
    std:  -> standard distribution
    self: -> current package
    dep:  -> declared dependency alias

NORMAL EXECUTION
    never changes dependency resolution
    may fetch only exact already-locked artifacts before program execution

GLOBAL INSTALL
    reserved for executable tools, never library visibility

NO INITIAL
    virtual-env activation
    site-packages
    ambient package search path
    mutable package versions
    install-time scripts
    native extension package model
```

The core principle is simple:

> **The project chooses dependencies. The machine only stores them.**
