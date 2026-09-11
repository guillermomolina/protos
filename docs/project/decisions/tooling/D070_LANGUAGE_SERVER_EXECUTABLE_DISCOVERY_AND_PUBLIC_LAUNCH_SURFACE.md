# D070 — Language-server executable discovery and public launch surface

Status: **RATIFIED — Candidate A′ selected**

Allocated: **2026-09-11**

Explicit project-owner approval: **2026-09-11**

Decision issue: GitHub #348

Triggered by: `LM009-F4` / GitHub #340 after F3 publication
`7bb9261fc27d8fa04085bd0fa717b68dc07e4b38`.

Primary consumer: `LM009-F4`

Nature: implementation-independent public tooling/distribution contract

Normative Protos language effect: **none**.

## Decision boundary

PLAT024 already ratified the durable static-tooling architecture:

```text
editor/client
    |
    | standard LSP / stdio
    v
toolchain-matched dedicated Protos language-server process
    |
    v
editor-neutral static-analysis core
```

LM009-F3 published the internal JVM `ProtosLanguageServerMain`, but deliberately
left two deployment details unsettled:

- the public tool-facing launcher contract by which an editor selects and starts
  the matching server; and
- the final physical JVM/JAR/Native Image/self-hosted packaging behind that
  launcher.

D070 decides the first point and deliberately keeps the second replaceable.

## Ratified decision — Candidate A′

The public tool-facing command is:

```text
protos language-server
```

Its baseline contract is:

- start the dedicated language server belonging to the selected Protos
  installation/toolchain;
- speak standard LSP over stdin/stdout;
- reserve stdout exclusively for LSP framing while the server is active;
- use stderr for non-protocol diagnostics/logging;
- remain a public supported tool-facing command, not a hidden VS Code-only hook;
- permit editor clients to launch it with an argv array and no shell; and
- keep JVM/JAR/main-class/distribution layout private behind the ordinary
  `protos` launcher.

For the reference VS Code extension, the executable authority remains the
already-existing machine-local setting:

```text
protos.runtime.executable
```

and the server launch is conceptually:

```text
configuredExecutable + ["language-server"]
```

No second language-server executable setting is introduced by D070.

## Toolchain-matching invariant

The selected executable is the version-selection authority for Run, Debug and
the static language server:

```text
selected Protos toolchain
        |
        +-- run
        +-- debug
        +-- test/package
        `-- language-server
```

This avoids a new compatibility institution between independently selected
runtime and server versions.

The command name identifies the **service role**, not its current JVM
implementation and not the LSP protocol as an eternal implementation detail.

## Remote / Dev Container / SSH invariant

The language server is launched in the workspace extension host where
`protos.runtime.executable`, the source tree and the selected Protos toolchain
already live.

D070 introduces no client-host/workspace-host path translation scheme, remote
daemon discovery, global port registry or separate server installation rule.

## Exhaustive prior-art review

The approval review compared materially different production launch/distribution
families rather than only similar CLI spellings.

### Gleam

Gleam exposes the language server through the primary toolchain executable as a
public `gleam lsp` command intended for editor clients.

This is the closest direct precedent for the selected same-toolchain-launcher
family. Protos chooses `language-server` rather than `lsp` so the public command
names the role rather than fixing the protocol in the command spelling.

### Swift / SourceKit-LSP

SourceKit-LSP is physically separate, but mature Swift tooling treats the
selected Swift toolchain as the compatibility authority. The useful lesson is
that compiler/runtime/server version matching should be derived from one
toolchain selection rather than independent user choices.

This motivated the strongest alternative B′ below.

### Dart Analysis Server

Dart ships its analysis server as part of the SDK/toolchain. Clients launch the
server belonging to the selected SDK rather than reconstructing arbitrary
compiler/server compatibility.

This strongly supports the single-toolchain-authority invariant even though the
physical server artifact is distinct internally.

### OCaml / ocamllsp

OCaml tooling commonly uses a distinct `ocamllsp` executable whose compatibility
depends on the active compiler/project environment; wrappers/tooling can select
the appropriate instance.

This demonstrates that separate physical binaries can remain toolchain-coupled,
but it also introduces an executable discovery layer Protos does not currently
need.

### rust-analyzer

rust-analyzer is a mature separately packaged language-server executable; VS Code
normally manages/bundles it and offers a server-path override.

This is excellent evidence that an independent server product scales, but it
also establishes a second distribution/version authority that is unnecessary
while Protos explicitly wants the server to match `protos.runtime.executable`.

### Go / gopls

gopls is a separate language-server executable resolved and launched by editor
tooling. This is a production-grade separate-server model with excellent
scalability, but again requires its own installation/discovery/version story.

### clangd

clangd is another highly scalable independent server executable with an explicit
editor path setting. It demonstrates the viability of separate-server
deployment and the operational cost of a second machine-local executable
configuration surface.

### Haskell Language Server

HLS uses separately installed binaries and toolchain/version matching mechanisms
such as GHCup. The architecture is powerful but reflects an ecosystem in which
compiler-version-specific server selection is already an institution.

### Zig / ZLS

ZLS is separately installed and version compatibility with Zig matters. This
again demonstrates that a separate server can scale while making matching and
discovery explicit ecosystem responsibilities.

### Eclipse JDT LS

JDT LS is an independently packaged Java language-server product with its own
launcher/product identity. It is strong evidence for the scalability of a
separate server, but Java tooling already has an independent distribution
architecture much larger than current Protos needs.

### Scala / Metals

Metals is an independently acquired JVM language server, commonly managed through
editor/Coursier tooling. This model optimizes editor installation convenience at
the cost of a separate server distribution lifecycle.

### Apple Pkl

The official Pkl VS Code integration resolves a Java runtime plus a separate
Pkl-LSP distribution and normally launches `java -jar <lsp>`, with socket mode
available.

Pkl is strong evidence for editor-managed server artifacts, but for Protos that
would create a second update/version authority beside the selected runtime
toolchain.

### Enso

Enso has a larger integrated engine/tooling ecosystem with language-server
lifecycle owned by the IDE/project system. It demonstrates a viable integrated
tooling product but introduces more institutional/runtime coupling than the
small Protos launcher boundary requires.

### Pyright / Pylance family

Python static tooling often lets the editor/tooling distribution own the server
independently of the Python interpreter/runtime selected for program execution.
That scales operationally, but deliberately has a different compatibility model
from Protos's PLAT024 requirement for a toolchain-matched server.

## Candidate set

### A′ — `protos language-server` through the existing configured executable

**Selected.**

One existing toolchain authority plus one public role-named subcommand. No shell,
second setting, sibling-path inference or JVM layout knowledge.

### A″ — `protos lsp`

Same topology and strong Gleam precedent, but the public spelling binds the
service name to today's protocol. Rejected in favor of the role-named command.

### B — independently discovered `protos-language-server`

A clean and scalable physical executable boundary with strong rust-analyzer,
gopls, clangd and JDT LS precedent. Rejected as the baseline because it creates
another discovery/version-matching institution.

### B′ — sibling `protos-language-server` belonging to the selected toolchain

Strongest alternative after the expanded survey. Swift/Dart/OCaml-style
toolchain coupling avoids arbitrary version skew.

Not selected because a configured `protos.runtime.executable` may be a PATH
command, wrapper, shim or remote installation; deriving a sibling executable
path would itself become another durable distribution API.

A future implementation may nevertheless use this topology **behind**
`protos language-server`.

### C — separate `protos.languageServer.executable` setting

Maximally flexible, but creates a second machine-local toolchain authority and
makes skew an ordinary supported state. Rejected.

### D — editor invokes Java/JAR/main class directly

Technically straightforward for the current F3 JVM host, but exposes private
packaging details and makes Native Image/self-hosting migration an editor change.
Rejected.

### E — editor bundles/downloads its own server artifact

Mature precedent exists in Pkl, rust-analyzer and Metals. Rejected for the
current Protos stage because it creates update/signing/cache/version lifecycle
that LM009-F does not otherwise require and can diverge from the selected
runtime.

### F — hidden/private `protos` subcommand

Mechanically simple but becomes a de-facto compatibility contract as soon as the
extension depends on it while denying other editors a supported surface.
Rejected.

## Required GITHUB010 scorecard

Scores are 1–5. Arithmetic is supporting evidence, not the decision authority.

| Criterion | A′ role subcommand | A″ `lsp` | B separate | B′ matched sibling | C second setting | D direct JVM | E editor-managed | F hidden |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Correctness / invariants | **5** | **5** | 4 | **5** | 3 | 4 | 3 | **5** |
| Protos alignment | **5** | **5** | 3 | 4 | 2 | 2 | 2 | 3 |
| Future-option resilience | **5** | 4 | **5** | **5** | 4 | 1 | 4 | 3 |
| Scalability | **5** | **5** | **5** | **5** | **5** | 4 | **5** | **5** |
| Conceptual simplicity | **5** | **5** | 3 | 4 | 2 | 2 | 3 | 3 |
| Portability / implementation freedom | **5** | 4 | **5** | **5** | **5** | 1 | 4 | 4 |
| Runtime / resource cost | **5** | **5** | 4 | 4 | 4 | 4 | 3 | **5** |
| Failure / operability | **5** | **5** | 3 | 4 | 2 | 2 | 3 | 2 |
| Reversibility / migration | **5** | 4 | 4 | **5** | 4 | 1 | 3 | 2 |
| Evidence maturity / implementation risk | 4 | **5** | **5** | **5** | **5** | **5** | **5** | 3 |
| **Total / 50** | **49** | **47** | **41** | **46** | **36** | **26** | **35** | **35** |

## Focused future / scalability / Protos scorecard

Scores are 0–10 on the three project-owner-requested long-term axes.

| Candidate | Future endurance | Scalability | Protos philosophy | Total / 30 |
| --- | ---: | ---: | ---: | ---: |
| **A′ `protos language-server`** | **10** | **10** | **10** | **30** |
| B′ matched sibling executable | 9.8 | **10** | 9.2 | 29.0 |
| A″ `protos lsp` | 9.0 | **10** | 9.8 | 28.8 |
| B independent executable | 9.5 | **10** | 7.0 | 26.5 |
| C second configured executable | 8.5 | 9.5 | 4.5 | 22.5 |
| E editor-managed artifact | 8.5 | 9.5 | 5.0 | 23.0 |
| F hidden subcommand | 6.5 | 9.5 | 6.0 | 22.0 |
| D direct JVM/JAR | 3.0 | 8.5 | 3.0 | 14.5 |

## Future stress

### Multiple editors / many workspaces

D070 does not change PLAT024's server topology. Discovery remains O(1) per client
session and each client owns its server process.

### Side-by-side Protos versions

Selecting a different `protos.runtime.executable` selects the server from that
same toolchain automatically.

### Remote / Dev Container / SSH

The same executable is resolved in the workspace extension host. No new
client/remote server path mapping is introduced.

### Native Image

The public launcher may internally delegate to a Native Image server without
changing clients.

### Self-hosted Protos language server

The launcher may later delegate to a server implemented in Protos without
changing clients.

### Bytecode DSL / non-Truffle runtime

The public launcher contract is independent of the analysis/runtime backend.

### Separately versioned future server

If the server later becomes an independently released product, the existing
command can remain a compatibility launcher:

```text
protos language-server
    -> exec/select independently packaged server
```

That preserves existing clients while allowing B′/B-like internals.

## Strongest argument against A′

A′ adds a public CLI command whose compatibility must be retained even though it
primarily serves tooling rather than humans. Ecosystems such as rust-analyzer,
clangd, gopls and JDT LS keep this machinery in a separate executable namespace.

The project accepts that cost because current Protos already has exactly one
distribution-facing launcher and one configured runtime authority, and PLAT024
requires the server to match that selected toolchain. The small public command
removes more version/discovery coordination than it creates.

## Regret trigger and escape path

**Regret trigger:** the language server becomes a separately versioned product
with an intentionally independent release/install lifecycle.

**Escape path:** retain `protos language-server` as the stable compatibility
launcher and delegate internally to the independently packaged/native/self-hosted
server belonging to or selected by that installation.

## Intentionally deferred

D070 does **not** decide:

- physical JVM/JAR versus Native Image versus self-hosted server packaging;
- whether a future sibling `protos-language-server` binary exists internally;
- socket/TCP or other optional transports;
- Marketplace toolchain download/update/signing policy;
- parser/index cancellation semantics;
- workspace/module/package resolution semantics;
- diagnostics, symbols, definition, references, completion, hover, signature
  help or other LM009-G/H feature semantics;
- server logging format beyond stdout being protocol-only while active; or
- a global/shared language-server daemon.

## LM009-F4 release boundary

Once this ratification is published to `main`, LM009-F4 is released to implement:

- public `protos language-server` dispatch to the already-published F3 stdio
  language-server host;
- reference VS Code `LanguageClient` wiring that starts exactly the configured
  `protos.runtime.executable` with `["language-server"]`, shell-free;
- workspace-extension-host operation under the existing VS Code extension kind;
- end-to-end lifecycle/document-sync proof; and
- no static language semantics in TypeScript.

F4 must not add a second server executable setting, expose JVM/JAR layout to the
editor, or implement LM009-G/H language-intelligence semantics.

## Approval record

The project owner explicitly approved **Candidate A′** on 2026-09-11 after the
initial GITHUB010 comparison and a broader follow-up survey covering Gleam,
Swift/SourceKit-LSP, Dart Analysis Server, OCaml/ocamllsp, rust-analyzer,
gopls, clangd, Haskell Language Server, Zig/ZLS, Eclipse JDT LS, Scala/Metals,
Apple Pkl, Enso and Pyright/Pylance-style deployment families, including focused
future-endurance, scalability and Protos-philosophy scoring.

This ratification records that approval. It contains no F4 executable/editor
implementation.
