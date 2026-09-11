# D065 — File-backed tooling source path identity and canonicalization

Status: **RATIFIED — Candidate B**

Allocated: **2026-09-10**

Explicit project-owner approval: **2026-09-11**

Nature: durable implementation-independent tooling/source-location decision

Triggered by: LM009-E / GitHub #318 live S3 acceptance

Decision issue: GitHub #323

Normative language effect: **none**. D065 defines how tooling identifies the location of an
ordinary filesystem-backed Protos source. It does not redefine Protos module identity, package
identity, source-content identity, filesystem equality, import semantics, execution semantics or
language-visible behavior.

## Problem

LM009-E live S3 validation proved that the selected debugger architecture and public launcher
contract were functioning correctly:

- VS Code launched the configured external Protos runtime;
- `protos debug` received the exact active source-file path;
- source breakpoints stopped at the expected line;
- stack, activation-local values, indexed Array values, step and continue worked through the real
  GraalVM DAP server;
- guest output was delivered; and
- after normal completion no `protos debug ...` process remained.

The remaining defect was source presentation. Graal DAP reopened the executing source as a virtual
DAP source and VS Code displayed it as a second Plain Text document even though the same physical
`.protos` file was already open in the workspace.

Repository audit identified the boundary: path-originating sources were created as literal-character
Truffle `Source` values plus a file URI. That preserved `Source.getURI()` but did not make the
source genuinely file-backed for tooling that relies on `Source.getPath()`.

The same production pattern exists in two path-originating boundaries:

- `ProtosCli.sourceFromPath(...)`; and
- `ProtosModuleSource.fromPath(...)`.

In-memory, generated, REPL and `-e` sources are intentionally different and remain outside this
problem.

## Decision boundary

D065 distinguishes three concepts that must not be collapsed:

1. **semantic module identity** — owned by the already-established Protos module/package model,
   including `ProtosModuleKey` where applicable;
2. **tooling source location** — the path/URI by which a debugger, editor or other source-oriented
   tool locates the source selected in the current execution/workspace namespace; and
3. **cross-namespace source mapping** — an optional future mechanism needed only when producer and
   consumer genuinely see the same source through different path namespaces.

D065 decides only the second concept for ordinary filesystem-backed sources and establishes when the
third concept is *not* required by the baseline.

## Prior-art audit

The decision was approved after an extended cross-language/source-debugging audit recorded on
GitHub #323. The recurring lesson across mature ecosystems is that debugger-visible source
location, semantic program identity and cross-host/build path mapping are distinct responsibilities.

### C and C++ — GDB / LLDB

GDB normally consumes source paths recorded in debug information and provides `substitute-path`
when a source tree has moved or build/debug hosts use different roots. LLDB provides the analogous
`target.source-map` mechanism.

**Lesson:** mapping is an explicit boundary for differing namespaces, not mandatory state for every
ordinary local source.

### Go — Delve

Delve normally uses source paths from the executable/debug information and provides `substitutePath`
for trimmed, relocated or remote builds where those paths differ from the editor filesystem.

**Lesson:** preserve the ordinary source namespace and add translation only when another namespace
actually exists.

### Rust

Rust exposes explicit path-remapping facilities for reproducible builds and can deliberately emit
virtualized source prefixes. Debuggers then need matching source lookup/remapping.

**Lesson:** reproducible-build source naming and ordinary interactive editor identity are separate
policies. D065 must not pre-emptively turn local debugging into a reproducible-build path model.

### .NET / C# / F#

PDB tooling supports explicit path rewriting/mapping. Path remapping is useful for CI and production
artifacts but can break ordinary local breakpoint lookup when emitted and local source paths no
longer match.

**Lesson:** hidden rewriting is harmful in the common local-debug path; mapping belongs where the
namespace difference is real.

### Python — debugpy / pydevd

Python debugger tooling distinguishes local/remote `pathMappings` from internal path normalization
or real-path equivalence used for caches/matching.

**Lesson:** an implementation may later use canonical physical equivalence internally without
making canonical realpath the editor-visible source identity.

### Java / JVM

JDI source paths/names are tooling/source-repository concepts rather than semantic class identity.
They are often logical/package-relative rather than filesystem realpaths.

**Lesson:** semantic identity and source-location identity are intentionally separate.

### JavaScript / Node.js

Node demonstrates why realpath can be useful for module-loader identity while debugger tooling
still needs separate local/remote/source-map path translation. Node also exposes preserve-symlink
modes because collapsing aliases is not universally correct.

**Lesson:** runtime/module identity rules do not automatically define the best editor-visible path.
This maps closely to Protos' separation between `ProtosModuleKey` and tooling `Source` location.

### Ruby — rdbg

Local debugging uses naturally visible source paths; remote/container cases add mapping only when
client and target expose the same filesystem under different names.

**Lesson:** this is close to Candidate B plus a future explicit mapping capability if a genuine
remote namespace appears.

### Swift — LLDB/lldb-dap

Swift delegates debugging to LLDB/lldb-dap and uses LLDB source-map machinery when build and source
namespaces differ instead of inventing a second language-specific path identity.

**Lesson:** keep the editor integration thin and preserve debugger-native source location semantics.

### Dart / Flutter

Dart tooling routinely distinguishes physical file URIs from package/logical locations and keeps
DAP implementation in the SDK rather than imposing one editor-owned universal path identity.

**Lesson:** physical sources and logical/virtual sources should remain visibly distinct.

### Erlang / BEAM

Erlang source lookup is layered over module/debug information; module identity is not defined as a
canonical host filesystem path.

**Lesson:** source lookup remains tooling metadata rather than language/module identity.

## Cross-ecosystem conclusion

The strongest common pattern is:

> Use the source location naturally belonging to the current execution/editor namespace; add
> explicit mapping only when a second namespace actually exists; keep semantic/program identity
> separate.

This avoids making filesystem canonicalization a universal user-facing identity and then requiring
ordinary local clients to undo it.

## Candidate comparison

| Candidate | Future-option resilience | Scalability | Protos alignment | Ordinary F5 correctness | Complexity cost | Overall |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| A — canonical physical path | 6.5 | 9.0 | 6.0 | 7.0 | 9.0 | 7.5 |
| **B — exact absolute normalized workspace/invocation path** | **9.5** | **9.5** | **9.8** | **10.0** | **9.8** | **9.7** |
| C — canonical runtime path + editor mapping | 9.0 | 6.5 | 5.5 | 9.0 | 5.0 | 7.0 |
| D — dual identity / alias registry | 9.8 | 4.5 | 3.5 | 9.5 | 2.5 | 5.9 |
| E — literal Source + URI/sourceReference | 3.0 | 8.0 | 4.0 | 2.0 | 8.5 | 5.1 |

## Ratified decision — Candidate B

### 1. Ordinary file-backed sources expose the selected workspace/execution path

For a Protos source originating from an ordinary filesystem path, tooling source location is the
**absolute lexically-normalized path by which the current execution/workspace host selected that
source**.

Conceptually:

```text
selected path
    -> absolute path
    -> lexical normalization
    -> tooling-visible physical Source path
```

This rule does not add a filesystem realpath/canonicalization step solely to create presentation
identity.

### 2. Symlinks are not resolved solely for tooling presentation

If a workspace selected a source through a symlinked path, the tooling-visible source path remains
the selected absolute normalized spelling rather than being rewritten to the symlink target merely
for debugger/editor identity.

This preserves the namespace in which the editor, workspace extension host and launcher are already
operating.

D065 does not forbid a future internal physical-equivalence cache from using canonical/real paths
when that is independently justified. Such an internal representation must not silently replace the
selected tooling-visible path.

### 3. Source path is not semantic module identity

`ProtosModuleKey` and the applicable package/module identity rules remain authoritative for semantic
module/cache identity.

Two different path spellings do not become two semantic modules merely because tooling can observe
them as different source locations. Conversely, semantic module identity does not authorize tooling
to rewrite the user's selected source path.

### 4. Non-filesystem sources remain virtual/logical

A source that genuinely originates from characters, generated content, REPL input, `-e`, memory or
another non-filesystem origin is not forced into a fake physical path.

Those sources may continue to use logical names/URIs and DAP `sourceReference`-style presentation
where appropriate.

The durable distinction is therefore:

```text
fromPath(...)       -> physical file-backed tooling Source
fromCharacters(...) -> virtual/logical tooling Source
```

### 5. Cross-namespace mapping is deferred until a second namespace exists

D065 does not add local/remote path mapping to the ordinary LM009-E F5 baseline because its selected
workspace architecture currently places the VS Code workspace extension host, launcher, DAP server
and source filesystem in the same execution namespace.

A future mode such as remote-network attach, relocated precompiled artifacts, reproducible-build
source prefixes or another genuine client/target namespace split may add an explicit source-mapping
facility through its own decision gate.

That future mapping is expected to compose with Candidate B rather than replace it: Candidate B
remains the no-mapping baseline when producer and consumer already share a source namespace.

### 6. No alias registry or global source mapping institution is introduced

The baseline requires no:

- global path/alias registry;
- per-workspace source identity database;
- editor-side canonical-to-visible translation table;
- DAP proxy;
- filesystem rendezvous state; or
- cross-session synchronization.

Each physical Source carries its own selected path. Source-location state therefore scales with the
sources that already exist and adds no separate O(workspace) reconciliation mechanism.

### 7. Backend neutrality

The durable rule is independent of Truffle and VS Code. A future debugger/backend can preserve the
same contract by exposing the selected physical path through its own source/debug information.

The current Truffle realization is an implementation detail, not the decision itself.

## Current Truffle realization

For the current implementation, a path-originating source should be built as a genuine file-backed
Truffle `Source` while preserving the characters Protos already read and disabling Truffle path
canonicalization:

```java
Source.newBuilder(ProtosLanguage.ID, exact.toFile())
        .canonicalizePath(false)
        .content(characters)
        .mimeType(ProtosLanguage.MIME_TYPE)
        .build();
```

The expected properties are:

```text
Source.getPath() == selected absolute lexically-normalized path
Source.getCharacters() == exact characters already read by Protos
Source.getName() == ordinary file name derived by the file-backed Source
```

This implementation must be applied coherently to the path-originating production boundaries rather
than as a VS Code-specific workaround.

## Implementation consequences

The first consuming correction is bounded to LM009-E source presentation and the shared file-backed
source construction boundary:

- change `ProtosCli.sourceFromPath(...)` to create a genuine file-backed Source under Candidate B;
- change `ProtosModuleSource.fromPath(...)` consistently;
- preserve already-read source characters rather than introducing an unnecessary second source read;
- add regression coverage that `Source.getPath()` is the expected absolute normalized selected path;
- preserve existing URI, language and character guarantees;
- update the CLI architecture assertion that currently encodes literal-source-plus-URI construction;
- leave literal/in-memory `Source.newBuilder(language, characters, name)` call sites unchanged;
- do not introduce editor-side source mapping or DAP protocol translation; and
- repeat LM009-E live S3 acceptance after implementation.

Because this implementation modifies executable source under `src/`, the consuming implementation
commit remains subject to the ordinary Maven implementation-version, root changelog and validation
requirements. D065 ratification itself is documentation/governance-only and does not increment the
implementation version.

## Scalability and future evolution

Candidate B has no separately growing coordination structure. For N loaded file-backed sources it
stores the normal source path already required by tooling, with no global alias graph. Parallel
debug sessions remain independent under the previously ratified LM009-E architecture.

The rule also leaves the following additive future directions open:

```text
Candidate B + internal physical-equivalence cache
Candidate B + explicit remote client/target path mapping
Candidate B + reproducible-build path remapping
Candidate B + generated/logical source URIs
Candidate B + package-source provenance
```

None of those capabilities requires changing the baseline physical-source rule when editor and
runtime already share one namespace.

## Protos alignment

Candidate B follows the project design philosophy:

- **mechanisms over institutions:** a Source carries its selected path; no registry is introduced;
- **pay only for what you use:** local F5 pays no remote-mapping cost;
- **preserve distinctions:** semantic module identity, tooling location and future mapping remain
  separate concepts;
- **minimize shared mutable state:** there is no global alias/path table;
- **prefer independence over coordination:** concurrent sessions and clients do not coordinate path
  identity through shared state;
- **scale by composition:** future mapping or reproducibility mechanisms can layer on the baseline;
- **generality must be earned:** remote mapping is added only when a real second namespace exists;
  and
- **keep platform at the boundary:** `canonicalizePath(false)` is a current Truffle mechanism, not a
  permanent Protos concept.

## Deliberately deferred

D065 does not define:

- package identity;
- semantic module identity;
- filesystem equality;
- source-content identity;
- cache key identity;
- URI schemes for generated/non-filesystem sources;
- remote-network debugger attach;
- client/target path-mapping syntax;
- reproducible-build remapping policy;
- source-map semantics;
- a universal source registry;
- Windows filesystem case/alias equivalence policy; or
- debugger handling for unavailable/archived source content.

If one of those becomes a prerequisite for a future capability, it crosses the normal explicit
decision gate rather than being inferred from D065.

## Ratification closure

The project owner explicitly approved **Candidate B** on 2026-09-11 after the exhaustive
cross-language/source-debugging audit.

Result:

```text
D065        RATIFIED — Candidate B
LM009-E     SOURCE-PRESENTATION IMPLEMENTATION RELEASED
LANGUAGE    unchanged
MODULE ID   unchanged
DAP ARCH    unchanged
```

The ratification changes no executable implementation, specification, Maven implementation version,
Standard Library behavior, package format, release artifact or deployment state.
