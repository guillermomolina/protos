# Protos Package Tool Architecture Audit

Status: selected architecture; non-normative; implementation tracked by TOOL001

Related package-system design:
- `docs/design/TOOLCHAIN_TOOL_ARCHITECTURE.md`
- `docs/design/PACKAGE_DISTRIBUTION.md`
- `docs/design/PACKAGE_MANIFEST_FORMAT.md`
- `docs/design/PACKAGE_IDENTITY_VERSIONING.md`
- `docs/design/PACKAGE_VERSION_RESOLUTION.md`
- `docs/design/PACKAGE_LOCKFILE_FORMAT.md`

This document closes the focused architecture question intentionally deferred by
the lockfile audit: how the Protos package-management tooling should be divided
between the irreducible host/toolchain bootstrap and tooling implemented in
Protos itself.

The original audit did not itself implement the package manager, change Core
language semantics, add a public import namespace, create a `LIBxxx` work item,
define networking, or declare that every toolchain component must be written in
Protos. Package-tool implementation has since begun and is now canonically
tracked as `TOOL001`. That tracking assignment does not rename or invalidate the
legacy bootstrap, Filesystem Slice 2A/2B, B006, or manifest Slice 3 labels already
published in repository history.

The selected direction is:

> **The package tool should be self-hosted as early as practical, but it must not
> be self-dependent for bootstrap.**

The host owns irreducible execution and authority mechanisms. Package policy,
manifest/lock interpretation, version selection, graph construction, validation,
and registry behavior should live in bundled Protos tooling when the required
general capabilities exist.

## Why this boundary matters

A package manager accumulates long-lived policy quickly:

```text
manifest schema
lock schema
package identity
version constraints
resolver policy
workspace rules
store policy
registry rules
publishing validation
security checks
diagnostics
```

Putting all of that into Java merely because the current implementation is Java
would make the implementation host the de facto owner of package semantics.

At the same time, requiring the package manager to resolve itself through the
project package graph creates a bootstrap cycle:

```text
need package manager
    -> need to load package manager package
        -> need project package resolution
            -> need package manager
```

The architecture must avoid both failure modes.

## Core observation: self-hosted is not self-dependent

A Protos-written tool does not need to be acquired through the project package
manager at tool invocation time.

Instead, the Protos distribution may contain an already-resolved immutable tool
closure:

```text
toolchain
├── runtime / compiler host
├── Core
├── Standard Library
└── bundled tools
    └── package
        ├── exact entry module
        ├── exact bundled modules
        └── exact toolchain-selected dependencies
```

The toolchain installation determines this closure when the toolchain is built or
packaged. Running the package tool performs no project dependency selection to
obtain the package tool itself.

Therefore:

```text
package tool implementation language = Protos
package tool bootstrap resolver       != project package resolver
```

This is the key to avoiding the chicken-and-egg problem.

## Selected architecture

Use four conceptual layers:

```text
A. host/runtime kernel
B. bundled-tool bootstrap
C. Protos package policy/tooling
D. exact package-backed runtime resolver
```

They have deliberately different responsibilities.

### A. Host/runtime kernel

The host retains only responsibilities that are irreducibly below ordinary Protos
tooling or already belong to the host boundary:

- start the Protos runtime/compiler;
- locate the selected Protos toolchain installation;
- locate Core and the standard distribution;
- locate a small immutable bundled-tool index;
- instantiate a Process/RootActor execution;
- bind host backends to already-defined general Protos capabilities;
- load the exact bundled tool entry closure named by the toolchain index;
- convert a validated package execution plan into a mechanical module resolver;
- perform host-specific operations only through separately designed general
  capability/back-end boundaries.

The host does **not** own:

- SemVer/caret/range policy;
- package version selection;
- TOML manifest semantics;
- `protos.lock` generation policy;
- package naming/identity rules;
- workspace dependency policy;
- yanking/update policy;
- registry selection policy;
- package publication policy.

Those belong above the host mechanism.

### B. Bundled-tool bootstrap

A small bootstrap layer loads tools distributed with the selected toolchain.

It does not perform registry resolution, version solving, cache name search, or
project lock resolution.

Conceptually the toolchain has immutable metadata such as:

```text
tool package:
    entry: exact bundled module
    closure: exact bundled tool modules
    tool-api-generation: N
    content identity: ...
```

The exact serialization is a later implementation detail.

The important invariant is:

> Selecting `protos add` must not search the user's package store for a package
> called `package-manager`.

The bundled tool index is part of the toolchain installation, just as the host
already needs deterministic knowledge of where its Core/standard distribution
lives.

### C. Protos package policy/tooling

The package tool should own, in Protos, as much package policy as the language's
general facilities can naturally express:

- TOML 1.0 parsing for the strict Protos manifest dialect;
- manifest schema validation;
- `PackageId`, locator, source, release and content models;
- SemVer value parsing;
- dependency constraint parsing;
- fresh and lock-preserving resolution algorithms;
- update targeting;
- prerelease/yank eligibility policy;
- canonical semantic resolution-input construction;
- `protos.lock` parser/writer/canonicalizer;
- workspace graph policy;
- package publication validation;
- package tree canonicalization policy;
- registry metadata interpretation;
- user-facing package diagnostics;
- `add`, `remove`, `resolve`, `update`, `fetch`, `package`, `publish`, and
  package-cache policy as their prerequisites become available.

This code is trusted toolchain code, but it remains Protos code using ordinary
language mechanisms and explicit capabilities.

## Future reusable-library extraction opportunities

This is a **non-committing architecture note**, not a roadmap item, blocker, or
request to create a `LIBxxx`/`TOOLxxx` task. The current Package Tool may keep
these components bundled under `protos/tools/package/` until a real second use
case justifies extraction.

Two components are particularly worth revisiting later:

### TOML front-end

`self:TomlSyntax` and the schema-neutral portions of `self:TomlDocument` are
strong candidates for extraction into a reusable TOML library.

The reason is architectural rather than cosmetic: TOOL001-C1 through C4 were
deliberately built below package-manifest meaning. They parse TOML syntax and
assemble the canonical TOML document/table model; package ownership begins above
that boundary in `self:ManifestSchemaV1`.

A future extraction should therefore preserve this split:

```text
reusable TOML syntax/document library
        |
        v
Package Tool ManifestSchemaV1
```

Before promoting that code to a public or Standard Library surface, audit:

- complete TOML 1.0 conformance of the intended public surface;
- the error/diagnostic contract expected by non-Package-Tool callers;
- whether the reusable API should expose syntax nodes, canonical table/value
  values, or both;
- whether the first extraction should remain an internal bundled library before
  acquiring a stable public `std:` namespace.

`ManifestSchemaV1`, package-manifest diagnostics, and package policy remain
Package Tool responsibilities even if the TOML front-end moves.

### Semantic Versioning core

The parse/precedence machinery underlying `self:ReleaseVersion` is another
candidate for a reusable Semantic Versioning library.

Do **not** assume that the whole current Package Tool value is generic SemVer.
The package `ReleaseVersion` contract intentionally narrows SemVer 2.0.0 to:

```text
MAJOR.MINOR.PATCH[-PRERELEASE]
```

and rejects build metadata. A future reusable SemVer library may reasonably own
the general SemVer value/parser/precedence mechanism while Package Tool keeps a
thin `ReleaseVersion` policy layer that applies its package-specific restrictions.

Likewise, these remain Package Tool policy unless an independent use case proves
otherwise:

- `DependencyConstraint` syntax and prerelease-admission policy;
- `FreshVersionSelection`;
- `RetainedVersionSelection`;
- resolver/yank/update/lock semantics.

Conceptually, a future refactor could become:

```text
reusable SemVer library
        |
        v
Package ReleaseVersion policy
        |
        +--> DependencyConstraint
        +--> Package selection/resolution policy
```

### Extraction rule

Extraction should happen only when it reduces duplicated concepts or serves a
real non-Package-Tool caller. Moving a file merely because it looks generic is
not sufficient.

Any future extraction must:

- preserve currently published Package Tool behavior;
- avoid making the Package Tool self-dependent on project package resolution;
- not expose bundled-tool internals through `std:` merely because code was moved;
- separate generic mechanism from Package Tool policy instead of generalizing
  package-specific rules into a supposedly universal library;
- be independently designed, tested, and tracked when it becomes actual work.

Until then, keeping the implementations local to the Package Tool is deliberate
and does not invalidate their future reuse potential.

### D. Exact package-backed runtime resolver

Core already leaves specifier interpretation to the host resolver and requires a
successful resolution to yield the canonical module identity used by module
caching.

For project execution, the host therefore still needs a module-loading mechanism
that can answer:

```text
self:<logical-module>
dep:<alias>/<logical-module>
```

without running a version solver during every import.

That host component must consume an **already validated exact package execution
plan**. It maps exact graph edges/module roots to canonical `ModuleKey` values and
loads exact source. It contains no dependency-selection policy.

Conceptually:

```text
Protos Package Tool
    parses manifest + lock
    validates stale/integrity state
    builds exact PackageExecutionPlan
                |
                v
Host Package Module Resolver
    self:/dep: -> exact node/module source -> ModuleKey
```

This prevents a second Java implementation of resolver policy while keeping the
Core host-resolution boundary mechanically implementable.

## PackageExecutionPlan

Introduce an internal tooling/host concept, not a new Core value family or public
language API:

```text
PackageExecutionPlan
```

It is the validated ephemeral plan for one concrete execution root.

Conceptually it contains enough information for the host to install an exact
module resolver:

```text
resolution root
entry package/node
exact immutable package nodes
materialized source roots
ContentIdentity already verified where required
declaring-node alias -> target-node edges
logical module visibility/exports
workspace member bindings
```

It may contain machine-local materialization paths because it is ephemeral host
execution data, not repository-controlled package identity.

It must not become another lockfile. The committed lock remains the durable exact
graph.

### Why a plan instead of re-parsing policy in Java

Without this boundary, the Java host would need to duplicate:

```text
TOML schema
lock-format semantics
stale detection
workspace rules
edge interpretation
package source policy
```

and the Protos package tool would implement the same rules again for package
commands.

A validated plan lets the Protos tool remain the policy owner while the host only
checks a narrow internal ABI and installs source/module mappings.

### Validation at the host boundary

The host must defensively validate the plan shape before installing it. Toolchain
code is trusted, but boundary validation prevents malformed internal values from
becoming arbitrary filesystem/module authority.

Validation should include:

- every node/reference is structurally valid;
- every physical source root is within authority already granted to the tool;
- alias edges target declared exact nodes;
- logical names satisfy the selected portable module-name rules;
- no node is introduced by ambient cache scanning;
- no project code can forge or mutate the installed plan after handoff.

The exact representation may be a host DTO populated from ordinary immutable
Protos data or another narrow bridge. It is implementation-internal.

## Separate tool execution from application execution

Package tooling can require more host authority than the application being run.

For example, the package tool may need:

```text
project manifest read
lockfile read/write
package-store read
possibly package-store write
eventually network
eventually credential access
```

The user program must not inherit those capabilities merely because the package
tool used them before launch.

Therefore package preflight/tool execution and application execution should be
separate authority domains.

Conceptually:

```text
Host
├── Tool Process
│   ├── exact bundled Package Tool
│   ├── tool-specific explicit capabilities
│   └── returns inert PackageExecutionPlan
│
└── Application Process
    ├── exact package resolver built from validated plan
    └── only application capabilities
```

They may exist sequentially in one JVM/native process. "Separate Process" here
means the Protos execution/authority domain required by the current Process
model, not necessarily another operating-system process.

This prevents package-manager Filesystem/network/store authority from leaking into
ordinary application code.

## Capability model

The package tool must not receive authority through hidden globals, imported
singletons, or ambient package-manager objects.

The existing Protos Process I/O model is a strong fit: host authority is
provisioned explicitly at bootstrap, and Filesystem is separate from Process
authority.

The package tool should consume ordinary explicit capability values.

### Principle of least authority by operation

Different commands should receive only what they require.

Illustratively:

```text
inspect/check:
    project read authority

resolve using already available metadata:
    project read
    lock output write
    metadata/store read

update:
    project read/write as needed
    metadata/store read
    network only when remote discovery is requested

fetch:
    exact lock read
    package-store write
    network

package:
    package source read
    output artifact write
    digest implementation

publish:
    package source/artifact read
    network
    explicit credential provider/authority

run preflight:
    project/lock read
    package-store read
    NO lock mutation
    NO version selection
```

The exact future capability acquisition surface is not fixed here.

### Do not make Process imply package authority

The normative Process model explicitly does not make Filesystem, network,
subprocess, or arbitrary native authority follow from possessing Process.

The package tool must preserve that property.

Do not implement package access as:

```text
process.packageManager()
process.network()
process.nativeHost()
```

merely for convenience.

General host capabilities should be designed independently and supplied explicitly
where needed.

## Repository reality at architecture selection (historical)

The observations in this section describe the repository state at the time this
architecture was selected. They are retained as design history; the implementation
reconciliation below records the subsequently published TOOL001 state.

### Current CLI is host-owned and minimal

`ProtosCli` currently handles:

- REPL;
- `-e`;
- source-file execution;
- `--help`;
- `--version`;

and directly creates one standalone session.

That is a suitable place for a **small top-level dispatch/bootstrap**, not for the
future package policy implementation.

The current shell launcher also resolves the built JAR and invokes the Java
entrypoint; no installed bundled-tool layer exists yet.

### Standard-library resolver already proves logical toolchain resolution

`ProtosStandardLibraryModuleResolver` resolves portable logical `std:` names from
a toolchain library root and produces logical `ModuleKey` identities rather than
making user package discovery depend on ambient search paths.

A bundled-tool resolver should reuse the same architectural lesson:

```text
exact selected toolchain component
logical internal module identity
confined source root
```

but bundled tools are not Standard Library and should not be exposed through
`std:` merely to reuse the existing resolver.

### Filesystem capability bridge exists

`ProtosStandardFilesystemProtocol.createCapability(...)` already constructs one
explicit `Filesystem` capability from a host backend. The class deliberately does
not install it in the Core prelude.

`ProtosStandaloneProcessBootstrap` already accepts an optional provisioned default
Filesystem and places authority through the standard bootstrap model.

This is exactly the architectural direction needed by a Protos-written package
tool.

### Production CLI does not currently provision Filesystem

The current `ProtosCli` passes `null` as the standalone default Filesystem.

Repository search found `ProtosStandardFilesystemProtocol.createCapability(...)`
used by conformance/tests, but not by the production launcher.

Therefore a real Protos package tool cannot yet simply open `protos.toml` through
the current production CLI.

This is an implementation prerequisite, not a reason to move manifest parsing
into Java.

### Filesystem v0.1 closes the metadata-publication minimum, not the full store API

D041 introduced the normative Filesystem surface beyond `filesystem.open(path,
options)` with general confined `replace(sourcePath, targetPath)` and
`remove(path)` operations. D042 corrects the initial ordinary-file-only
precondition: the final Path component is selected as the namespace entry itself
without following symbolic-link/reparse/other indirection entries, while a
backend fails `IOError` when it cannot provide the required atomic transition for
that entry kind or source/target-kind combination. `remove` remains non-recursive.

The correction is required by the race-free capability boundary rather than by
package policy. Common capability-relative rename/unlink APIs select the entry at
the namespace mutation point but do not also provide an atomic
"preclassify-this-mutable-name-as-a-regular-file, then mutate-that-same-entry"
primitive. A separate type query would recreate the check-then-act race that the
Filesystem confinement contract is designed to exclude.

The resulting D042 contract is the minimum semantic namespace machinery needed
for a Protos-written package tool to stage a complete `protos.toml` or
`protos.lock`, publish it without a partial-target window, and clean abandoned
staging entries. Package policy still uses ordinary files; the broader
namespace-entry selection rule exists so the general capability can be
implemented faithfully under concurrent namespace change.

I021 now provides the production confined Filesystem namespace backend. Package-tool
Filesystem Slice 2B closes the remaining metadata-publication integration by
provisioning only explicit project-metadata read, exact staging create/write, and
namespace-mutation authority to the bundled tool. Publication itself remains
ordinary Protos code using standard File write/close plus Filesystem replace/remove;
D042 is not used as permission for a package-specific Java/native filesystem escape.

A complete package store/archive implementation still needs additional namespace
operations such as directory enumeration/materialization, safe directory
creation, metadata/type inspection, symlink/reparse creation/inspection, broader
move/rename policy, recursive directory removal/garbage-collection behavior, and
eventually other capabilities identified by the package architecture.

Those remaining operations must likewise be designed as general Filesystem
capabilities rather than package-manager-only native escape hatches. Initial
package-tool slices should therefore distinguish the now-closed metadata
publication minimum from the still-incomplete full store surface.

### Portable networking is not ready

Current project roadmap material explicitly leaves socket/HTTP API design open.

Therefore remote registry discovery, fetch, and publish are **not** suitable first
self-hosted package-tool slices.

Do not solve this by adding a package-specific Java `download(url)` intrinsic.

Implement local/offline package tooling first. Later remote package operations
should use the general network/HTTP capability architecture once that exists.

### Digest support is a separate prerequisite

The package design requires algorithm-tagged `ContentIdentity` and semantic-input
digests.

No package-specific SHA primitive should be introduced merely to make the package
manager work.

A deterministic digest implementation may eventually be:

- ordinary Protos/library code;
- a standard pure-value protocol with host acceleration;
- another generally useful cryptographic/hash facility.

The package tool consumes that general mechanism; it does not own the language's
hash-primitive design.

## Published implementation reconciliation through manifest Slice 3

The bounded historical manifest Slice 3 is now CLOSED as `TOOL001-C1` through
`TOOL001-C7`.

Published reality now includes:

- exact toolchain-bundled package-tool module resolution;
- a production package-tool bootstrap that provisions explicit confined project
  metadata Filesystem authority without exposing it to ordinary applications;
- metadata staging/publication through ordinary File/Filesystem operations;
- TOML 1.0 parsing and canonical table assembly in bundled Protos source;
- complete manifest schema-v1 structural validation and ordinary ManifestV1
  construction in bundled Protos source;
- `protos package manifest`, mechanically selected by the host as exact bundled
  `ManifestMain`, which reads exactly `protos.toml`, consumes UTF-8 text to EOF,
  invokes the schema-v1 parser and owns its user-facing diagnostics in Protos;
- preservation of the historical bare `Main` entry for `protos package`.

This implementation confirms rather than changes the selected architecture:
host Java owns irreducible exact-entry selection and capability provisioning;
package semantics/policy remains bundled Protos code.

Manifest Slice 3 closure does not imply that the broader Package Tool is
complete. Release-version/constraint semantics, lockfile policy, dependency
resolution, workspace interpretation, package-store/archive operations,
registry/network/credential behavior, fetch/update and publication remain
separately scoped future TOOL001 work with their own prerequisites.

## Bundled tools are not `std:`

The package manager is tooling, not Standard Library API.

Do not make the package manager importable as:

```text
std:package/Resolver
```

merely because it ships with the toolchain.

The distribution should keep a conceptual distinction:

```text
protos/lib/...
    Core/Standard Library source

protos/tools/...
    bundled tool source/closures
```

The exact repository path is not frozen, but the distinction is.

Likewise, do not introduce a user-visible `tool:` import namespace unless a real
programming use case later justifies one.

The host may use internal exact component keys that are never accepted as ordinary
program import specifiers.

## Internal imports of a bundled tool

A bundled tool should be package-shaped during development and should be able to
use ordinary modular source.

Its closure may use:

```text
self:<logical-module>
std:<logical-module>
```

and, if a bundled dependency is needed, that dependency is resolved/frozen when
the toolchain distribution is built.

At runtime the tool's own closure is already exact.

It must not use the user's `protos.lock` to resolve its own dependencies.

This allows the same package abstractions to dogfood the toolchain without making
bootstrap recursive.

## Tool development versus installed tool closure

During development inside this repository, a Protos package tool may itself have
normal package metadata and tests.

Conceptually:

```text
source repository
    package-tool source
    development manifest
    development lock
    tests

installed toolchain
    already-resolved immutable package-tool closure
```

The installed closure is analogous to a linked/bundled tool artifact. Its
development lock does not participate in the user's project graph.

## CLI UX: one `protos` frontend

Keep one public top-level executable:

```text
protos
```

The user should not need a second `pip`/`npm`-style executable merely because
package policy is implemented in Protos.

Package commands remain naturally exposed as:

```text
protos add ...
protos remove ...
protos resolve
protos update ...
protos fetch
protos package
protos publish
protos cache ...
```

Internally these commands may dispatch to the same bundled package-tool entry.

`protos run`, future `protos test`, and future `protos build` may also use the
package tool's read-only preflight service to obtain an exact
`PackageExecutionPlan`.

The Java/native top-level CLI only needs enough parsing to select a built-in
bootstrap action or bundled tool. Full package command parsing belongs to the
Protos tool.

## Bundled tool entry contract

The exact ABI is implementation-internal, but it should be small and stable.

A plausible direction is:

1. host selects the exact bundled tool descriptor;
2. host creates a tool Process/initial module context;
3. host provisions ordinary explicit capabilities;
4. host executes the exact entry module;
5. the entry exposes/captures a `main` callable or equivalent fixed entry
   convention;
6. package command arguments come from the ordinary Process argument snapshot;
7. tool result maps through a small host exit-status convention.

Do not invent a new language-level `main` keyword or tool object family.

A top-level tooling ABI is host/toolchain convention, not Core syntax.

The entry module may capture its bootstrap-local capabilities in ordinary
closures and pass them explicitly to imported helper modules.

Imported helper modules must not magically acquire the tool entry's authority.

## Read-only execution preflight

`run`, `test`, and `build` need a special package-tool mode:

```text
read current semantic resolution inputs
read exact lock
validate lock-format/resolver support
validate semantic resolution-input digest
validate exact materialized package content as required
construct PackageExecutionPlan
return plan
```

This mode:

- never invokes fresh version selection;
- never modifies `protos.lock`;
- never rewrites `protos.toml`;
- never selects a newer package;
- should not need network when all locked artifacts are already available;
- fails if required exact content is unavailable in offline/read-only mode.

This preserves the already-selected rule that normal execution is resolution-free
while still allowing package parsing/validation policy to be implemented once in
Protos.

## Package tool command modes

The same bundled package tooling can expose mutation-capable modes separately.

### `resolve`

Reads manifest/workspace semantic inputs and reconciles the committed lock,
preserving valid locked selections according to the published resolver policy.

### `update`

Explicitly unlocks the requested graph region or complete root and may consult
new authoritative metadata.

### `add` / `remove`

Mutates human manifest dependency declarations and then invokes explicit
resolution policy.

These commands require a separately designed manifest-editing policy. TOML
comment/format preservation versus canonical rewriting should be audited before
the first user-facing mutating implementation; it is not a bootstrap question.

### `fetch`

Consumes an exact lock and materializes exact content. It does not select new
versions.

### `package`

Constructs the canonical package tree/artifact after the required general
filesystem namespace and digest facilities exist.

### `publish`

Validates package/release identity and uploads through future explicit
network/credential authority. It must not execute package code as an install hook.

## Trust model

Bundled package-tool code is part of the selected Protos toolchain trust base.

That does **not** mean user programs can import it or inherit its authority.

Toolchain installation integrity should eventually cover:

```text
host executable/runtime
Core
Standard Library
bundled package tool closure
bundled tool index
```

The package tool then verifies project dependency content according to
`ContentIdentity`.

This gives two distinct trust layers:

```text
toolchain trust
    verifies the resolver/tool code being executed

project dependency trust/integrity
    verified by that tool against manifest/lock/authority rules
```

Do not use the project package graph to redefine the package tool that verifies
that graph.

## Versioning the package tool

Initially the package tool is versioned and distributed **with the toolchain**.

The user does not select a separate package-manager version for each project.

Keep these concepts independent:

```text
Protos implementation/toolchain version
package-tool implementation version
manifest-version
lock-format
resolver-version
package ReleaseVersion
```

They may correlate in one release but are not identity aliases.

The persistent package files store `manifest-version`, `lock-format`, and
`resolver-version` because those govern data semantics. They do not need to store
the Java/JVM tool implementation version as package-language compatibility.

A future independently updatable package tool is possible, but it must preserve a
non-recursive trusted bootstrap channel. Do not add that complexity initially.

## No self-hosting dogma

"Written in Protos" is a design goal for policy/tooling, not a rule that every
byte of implementation must leave the host.

Keep in the host when the responsibility is genuinely irreducible:

```text
JVM/native runtime integration
compiler/parser execution machinery
OS/backend handles
toolchain installation discovery
tool bundle loading
host capability backend bindings
mechanical exact ModuleKey/source resolution
```

Prefer Protos when the responsibility is ordinary deterministic program logic:

```text
TOML parsing
manifest schema
SemVer/ranges
dependency graph algorithms
lock serialization
diagnostics
package validation
registry protocol state machines
workspace policy
```

A host implementation is acceptable as an optimization only when the observable
tool behavior remains owned/tested by the same Protos-facing policy contract and
does not create a second independently evolving semantics.

## Avoid package-specific native escape hatches

The package tool will expose gaps in the Standard Library/capability ecosystem.

Treat those gaps as useful language/toolchain feedback.

Examples of the wrong response:

```text
PackageNative.readDirectory(...)
PackageNative.sha256(...)
PackageNative.httpGet(...)
PackageNative.atomicInstall(...)
```

unless those are explicitly temporary internal prototypes on the way to a
general audited capability.

Preferred response:

```text
general Filesystem namespace capability
general digest/hash facility
general networking/HTTP capability
general secure credential provider boundary
```

then consume those capabilities from the package tool like any other substantial
Protos program.

This makes the package manager a maturity test for Protos rather than a permanent
collection of privileged exceptions.

## Repository/layout recommendation

When implementation begins, prefer a dedicated bundled-tool source area such as:

```text
protos/tools/package/
```

rather than placing the package manager in:

```text
protos/lib/core/
protos/lib/<standard-library-area>/
```

The exact directory should be checked against the then-current repository
governance before creation.

A tool package should contain ordinary `.protos` modules and tests. A generated or
static toolchain bundle index may live with distribution/build metadata.

The public runtime must not search `protos/tools` by name.

Only the toolchain bootstrap selects a tool from the exact bundled index.

## Initial implementation sequence

Do not implement the whole package ecosystem in one change.

The architecture suggests the following dependency-ordered slices.

### Slice 1: bundled-tool bootstrap

Goal:

- teach the top-level CLI/toolchain to locate an exact bundled tool;
- launch a minimal Protos tool entry;
- pass ordinary command arguments/stdio;
- prove bundled internal modules can load without consulting a project package
  graph.

The first tool behavior can be diagnostic/help-only.

This slice deliberately implements **no package resolution**.

Established invariant:

> A Protos-written bundled tool can execute before project package resolution.

### Slice 2: production Filesystem provisioning for tooling

Provide a real host backend for the already-defined general Filesystem capability
and provision the package tool with narrowly scoped authority sufficient to read
and write explicitly named project metadata files.

Do not yet claim full package-store support if directory namespace operations are
still missing.

Established invariant:

> The bundled tool can read `protos.toml` and atomically/safely produce the
> metadata files supported by the current general Filesystem contract.

If the current Filesystem contract cannot provide an operation required for safe
metadata replacement, that gap must be audited rather than bypassed with a
package-only native hook.

### Slice 3: read-only Protos manifest implementation

Implement in Protos:

- TOML 1.0 subset/conformance needed by manifest generation 1;
- strict manifest schema;
- package/dependency model;
- read-only diagnostics.

No lock mutation or network.

This is the first substantial self-hosted package-policy slice.

### Slice 4: version and lock pure logic

Implement in Protos, with in-memory/test catalogs first:

- SemVer value parser;
- v1 constraint parser;
- eligibility/prerelease policy;
- lock-preserving and fresh selection algorithms;
- canonical lock parser/writer;
- semantic resolution-input canonicalization.

Digest production must use an audited general implementation before persisted
hashes are treated as real integrity guarantees.

### Slice 5: execution preflight and exact runtime plan

Implement:

- read-only manifest+lock validation in the bundled Protos tool;
- stale-lock detection;
- `PackageExecutionPlan`;
- host validation of the plan;
- host package-backed module resolver consuming only exact plan nodes;
- initial `self:` / `dep:` local/offline execution.

Established invariant:

> Normal application execution consumes exact package policy produced by one
> Protos implementation and performs no version solving in the host resolver.

### Slice 6: manifest mutation/workspaces

After the manifest editing and required filesystem namespace behavior are audited:

- `add`;
- `remove`;
- workspace root/member discovery;
- targeted `resolve`/`update`.

Keep workspace/path dependencies live rather than content-locking every source
edit.

### Slice 7: package tree/store

Only after general directory/metadata/atomic filesystem operations and digest
support are sufficient:

- canonical package-tree hashing;
- immutable package store;
- exact materialization;
- vendor/offline export;
- cache validation/GC.

### Slice 8: registry/fetch/publish

Only after general networking/HTTP and credential authority are designed:

- registry metadata;
- remote version discovery;
- fetch;
- mirror transport;
- publish;
- yank/deprecation operations where appropriate.

Do not block local/offline package-system progress on networking.

## Why this sequence is useful

It dogfoods Protos early without making missing host capabilities a reason to
write package policy in Java.

It also gives strong incremental milestones:

```text
bundled Protos tool runs
    ->
tool reads manifest
    ->
tool understands manifest
    ->
tool resolves pure graphs
    ->
tool validates lock and launches exact local packages
    ->
store
    ->
network registry
```

Every step can be tested before the next authority boundary exists.

## What should remain testable as pure Protos

The following package behavior should have conformance/unit fixtures that do not
require real network or host filesystem state:

- TOML manifest parsing;
- manifest schema validation;
- SemVer parsing/order;
- constraint set membership;
- prerelease rules;
- resolver candidate ordering;
- lock-preservation behavior;
- update graph selection;
- lock canonical serialization;
- resolution-input canonicalization;
- graph diagnostics;
- package locator/ID validation where applicable.

Host integration tests then focus on:

- tool bundle loading;
- capability confinement;
- filesystem backend fidelity;
- PackageExecutionPlan boundary validation;
- exact module source loading;
- store materialization;
- future network transport.

This keeps most package-policy tests cheap and deterministic.

## Failure behavior

A bundled package tool failure should remain a tool failure, not corrupt the
application runtime.

Before mutation:

- parse/validation errors produce no repository changes;
- unsupported manifest/lock generations fail clearly;
- absent capabilities fail without falling back to ambient host access.

For metadata mutation:

- write through the general Filesystem semantics;
- use failure-safe/atomic replacement when the general capability supports it;
- do not partially rewrite `protos.lock` on resolver failure.

For execution preflight:

- no package-tool failure starts the user application with a guessed/fallback
  graph;
- no stale lock silently triggers resolution;
- no missing artifact silently searches ambient directories.

## Rejected architecture: package manager entirely in Java

Rejected as the target architecture.

A temporary bootstrap helper in the host is acceptable only for mechanisms that
cannot yet be expressed through general Protos facilities.

The package semantics themselves should not become a permanent duplicate Java
subsystem.

## Rejected architecture: package manager resolved from the project

Rejected.

The project must not be able to change the tool that decides what the project's
dependencies mean merely by changing its own dependency graph.

This is both a bootstrap and trust-boundary problem.

## Rejected architecture: package manager inside `std:`

Rejected.

Shipping with the toolchain does not make package management a Standard Library
API.

## Rejected architecture: hidden global tooling authority

Rejected.

Do not expose package-store, filesystem, network, credentials, registry, or
native host access as ambient globals available to every module in a tool
Process.

Authority enters through explicit bootstrap/capability boundaries and is then
passed/captured through ordinary Protos code.

## Rejected architecture: host chooses packages, Protos only formats output

Rejected.

That would make the Protos package tool cosmetic while Java still owns the
important semantics.

The host exact runtime resolver may consume a selected graph; it must not choose
that graph.

## Durability classification

Intended durable decisions:

```text
one public `protos` frontend
package policy primarily in bundled Protos tooling
self-hosted but not self-dependent bootstrap
tool closure exact and toolchain-bundled
no project resolution to start package tool
no public `tool:` namespace
package tool not part of `std:`
explicit least-authority tool capabilities
tool Process authority separated from application Process authority
PackageExecutionPlan policy/mechanism boundary
host runtime resolver consumes exact plan only
no duplicate host version-selection semantics
normal run/test/build package preflight is read-only
network/store features wait for general capabilities rather than package intrinsics
```

Deliberately replaceable details:

```text
exact `protos/tools/...` repository path
bundled-tool index serialization
tool entry callable name/ABI
PackageExecutionPlan physical representation
whether pure hashing is Protos or host-accelerated
exact top-level subcommand dispatch implementation
future independent package-tool release channel
```

## Audit evidence

### AGENTS.md read

- `AGENTS.md`
- `src/AGENTS.md`

Derived constraints:

- package tooling must preserve Protos's mechanisms-over-institutions and no-pets
  philosophy;
- host/platform differences remain at explicit boundaries;
- implementation convenience cannot define language/package semantics;
- shared/ambient state should be minimized;
- implementation should reuse established module/runtime/capability layers;
- this audit is documentation/governance-only and does not require Maven tests.

### docs read

Materially reviewed package-design records:

- `docs/design/PACKAGE_DISTRIBUTION.md`
- `docs/design/PACKAGE_MANIFEST_FORMAT.md`
- `docs/design/PACKAGE_IDENTITY_VERSIONING.md`
- `docs/design/PACKAGE_VERSION_RESOLUTION.md`
- `docs/design/PACKAGE_LOCKFILE_FORMAT.md`
- `docs/design/PROTOS_DESIGN_PHILOSOPHY.md`
- `docs/design/STANDARD_LIBRARY_IDEAS.md`
- `docs/project/registries/IMPLEMENTATION_STATUS.md`

Derived constraints:

- project metadata chooses dependencies; machine state only stores/materializes
  exact selections;
- TOML is a strict human manifest encoding, not package semantics;
- package identity/source/content/transport are separate;
- resolver policy allows contextual multi-version graphs and preserves lock state;
- the lock is canonical, committed and machine-owned;
- networking remains a separately planned capability area.

### spec read

Materially reviewed:

- `spec/semantics/MODULES.md`
- `spec/io/PROCESS_IO.md`
- `spec/io/IO_CORE.md`
- `spec/io/FILESYSTEM.md`

Derived constraints:

- Core `import(String)` delegates specifier meaning to a host resolver, then uses
  canonical `ModuleKey` identity;
- imported modules do not automatically acquire RootActor bootstrap authority;
- Process authority does not imply filesystem/network/subprocess authority;
- default Filesystem is a separate optional bootstrap-local capability;
- Filesystem currently owns file-open authority and does not justify hidden
  package-only host APIs.

### implementation/tests inspected

Materially reviewed:

- `src/main/java/com/guillermomolina/protos/cli/ProtosCli.java`
- `src/main/java/com/guillermomolina/protos/execution/ProtosStandaloneProcessBootstrap.java`
- `src/main/java/com/guillermomolina/protos/execution/ProtosStandardFilesystemProtocol.java`
- `src/main/java/com/guillermomolina/protos/execution/ProtosStandardLibraryModuleResolver.java`
- `src/main/java/com/guillermomolina/protos/runtime/ProtosModuleKey.java`
- `bin/protos`
- `pom.xml`
- production/test call sites for `ProtosStandardFilesystemProtocol.createCapability`
  discovered through repository search.

Implementation-relevant findings:

- the CLI has no bundled-tool dispatch layer yet;
- the CLI currently provisions no default Filesystem (`null`);
- the general Filesystem capability constructor exists and is intentionally
  authority-explicit;
- production repository search did not find a production Filesystem capability
  backend call site;
- the standard resolver demonstrates confined logical toolchain module loading;
- current Protos libraries include Core, collections and JSON, but no bundled
  tool source area;
- no package-manager implementation is being modified by this audit.

## Final recommendation

Adopt this target:

```text
HOST / RUNTIME
    start Protos
    locate exact bundled tool
    bind general host capabilities
    run bundled tool
    validate PackageExecutionPlan
    mechanically load exact package modules

BUNDLED PACKAGE TOOL — PROTOS
    TOML + manifest schema
    package identity model
    SemVer + constraints
    resolver
    lock parser/writer
    workspace policy
    diagnostics
    store/registry/publish policy as general capabilities become available

APPLICATION
    receives only its own explicit capabilities
    imports through exact self:/dep: plan
    never sees package-tool authority
```

The first executable package-system work should therefore be the bundled-tool
bootstrap, not a Java `PackageManager` and not a network registry client.

The package manager should become one of the first substantial real-world tools
written in Protos, and missing general capabilities discovered while building it
should be treated as evidence for improving Protos's ordinary tooling/library
surface rather than as justification for permanent package-specific privileged
escape hatches.

## TOOL001-F2D1 implementation checkpoint — workspace execution-plan boundary

`TOOL001-F2D1` freezes the first executable `PackageExecutionPlan` boundary
without pretending that external locked packages are already materializable.

The selected bounded continuation is:

```text
F2D1  plan/runtime-name/preflight design             CLOSED
F2D2  pure workspace plan construction               READY
F2D3  mechanical host resolver + command preflight   dependency-gated
```

The workspace-only subset is valid because workspace nodes do not carry immutable
ContentIdentity. A canonical non-stale lock plus exact root/member/edge
reconciliation can fully identify that local graph.

Registry/Git nodes remain fail-closed until a later materialization layer can
supply exact source roots and verify the mandatory locked ContentIdentity. The
host must never substitute cache scanning, locator/Git provenance, or a package
with a matching textual PackageId.

F2D1 also closes the initial runtime interpretation of schema-v1 aliases/exports:
`dep:<alias>/<public-export>` resolves one direct locked edge and then the
target manifest's export map; `self:<logical-module>` addresses an internal
module of the current package directly. All names use the portable segment
discipline recorded in the F2D design record.

The plan remains inert Protos data. The host must defensively validate and detach
it into immutable resolver state before application execution; package-tool
Filesystem/store/network authority never transfers merely through plan handoff.
