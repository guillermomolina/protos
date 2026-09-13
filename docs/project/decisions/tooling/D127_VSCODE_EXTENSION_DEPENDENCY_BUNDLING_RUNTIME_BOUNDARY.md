# D127 — VS Code extension dependency bundling and runtime distribution boundary

Status: **RATIFIED — Candidate B′ selected**

Allocated: **2026-09-13**

Explicit project-owner approval: **2026-09-13**

Decision issue: GitHub #497

Primary consumer: `LM009-I1` / GitHub #474

Nature: implementation-independent editor packaging, dependency-distribution and
runtime-boundary contract

Normative language effect: **none**.

## Decision boundary

LM009-I1 must turn the reference VS Code integration from a development-tree
artifact into a reproducibly packaged VSIX without turning the extension into a
second Protos runtime distribution.

The durable question is:

> What is the authoritative distribution boundary between the VS Code
> extension's JavaScript/LSP-client dependencies and the external Protos
> runtime/language-server executable?

This affects reproducibility, supply-chain surface, install/startup cost, offline
behavior, VS Code Remote/Dev Container operation, Marketplace/Open VSX
portability, release-matrix growth and whether the editor remains a thin client.

At the D127 audit baseline:

- `editors/vscode/package.json` has one direct runtime npm dependency:
  `vscode-languageclient` `10.1.1`;
- `main` points to raw `./extension.js`;
- `extension.js` directly requires `./debug_adapter` and
  `vscode-languageclient/node`;
- no committed `editors/vscode/package-lock.json` exists;
- local development intentionally uses `npm install --no-package-lock` pending
  packaging policy;
- no `editors/vscode/.vscodeignore` exists;
- `protos.runtime.executable` is already the machine-scoped external launcher
  authority for Run, Debug and `protos language-server`;
- the extension is a VS Code `workspace` extension and must run in the same
  local/Remote/Dev-Container extension host as that launcher;
- root `.gitignore` already excludes `editors/vscode/*.vsix`; and
- prior manual packaging produced a working but unbounded VSIX containing 399
  files, 186 JavaScript files, test material, the full installed `node_modules`
  tree and no package-root license material.

D127 selects the dependency/runtime boundary only. Public registry/version policy
remains LM009-I4.

## Already-ratified boundaries preserved

D127 does not reopen the following LM009 contracts:

1. Protos language/runtime semantics remain outside the VS Code extension.
2. `protos.runtime.executable` remains the single external launcher authority for
   Run Current File, Debug and language-server startup.
3. The extension does not know Java main classes, JARs or classpath internals.
4. The extension remains a workspace extension compatible with VS Code
   Remote/Dev Container.
5. No generated VSIX is committed to the repository.
6. Marketplace/Open VSX release/version policy remains LM009-I4.
7. APL-1.0 material and required third-party notices/licenses must be present in
   the packaged artifact.

## Prior-art survey

The audit compared mature editor/language-extension packaging patterns and
backend-distribution models.

### Visual Studio Code platform guidance

Current VS Code extension guidance recommends bundling because many small files
increase installation and load cost. The documented esbuild/webpack patterns
bundle extension code, keep the host-provided `vscode` module external, and
exclude source/tests/intermediate files and raw `node_modules` from the published
artifact once dependencies are bundled.

Transferable lesson: the platform-native default is a compact client bundle with
an explicit package boundary.

### rust-analyzer

The VS Code client uses a production JavaScript bundle and depends on
`vscode-languageclient`. Its client-side dependency closure is therefore a build
input rather than a public raw npm filesystem layout.

rust-analyzer also demonstrates the opposite server-distribution choice: editor
releases commonly carry platform-specific server binaries. That gives turnkey
installation but creates platform-specific artifact coupling. Publication
incidents where a platform artifact is missing its server illustrate the added
release-matrix risk.

Transferable lesson: client bundling is strong precedent; server-in-VSIX is not
automatically desirable.

### VS Code Go

The Go extension uses a bundled JavaScript client while keeping Go tooling,
including `gopls`, as an external toolchain concern.

Transferable lesson: bundled editor protocol/client code and external semantic
backend are independent, and this is the closest mature analogue to Protos.

### clangd VS Code

The clangd extension bundles the JavaScript/LSP client and resolves a separate
`clangd` server executable.

Transferable lesson: one compact editor client can preserve a hard external
server boundary.

### Ruff VS Code

Ruff uses a production bundle for the extension client while keeping backend
strategy independently selectable.

Transferable lesson: JavaScript bundling does not force the language/runtime
backend into the VSIX.

### Red Hat Java and XML extensions

Mature Red Hat language extensions use committed npm lockfiles and production
bundles with `vscode-languageclient`.

Transferable lesson: lockfile + deterministic production bundle scales for large
language integrations.

### Haskell and OCaml editor integrations

These ecosystems commonly resolve the language server/toolchain separately from
the editor JavaScript package.

Transferable lesson: rich editor integration does not require the editor artifact
to become the language runtime distribution.

### Native/platform-binary extension families

Extensions that ship native or platform-specific semantic engines improve
turnkey onboarding but pay in artifact size, signing, update coupling,
platform-specific testing and release-matrix complexity.

For Protos this cost is especially weakly justified because the external
`protos` launcher contract is already shared across Run, Debug and LSP.

### Raw production `node_modules`

Shipping a pruned production dependency tree is simple and can be deterministic
with a lockfile, but exposes many transitive files as package structure, increases
install/file-count cost and makes package-content regression sensitive to
dependency-internal file churn.

### Activation-time dependency installation

Downloading/installing JavaScript dependencies after extension installation can
reduce initial VSIX size but creates network-dependent activation, mutable
post-install state, proxy/certificate failure modes, weaker offline behavior and
a larger supply-chain attack surface.

### Hand-written LSP client

Replacing `vscode-languageclient` with Protos-owned protocol machinery removes an
npm dependency only by creating a larger editor-specific correctness and
compatibility burden.

That would duplicate mature infrastructure while adding no Protos semantic value.

## Prior-art fit for D127

Scores are suitability as a D127 precedent, not overall product quality.

| System/pattern | Aguante de futuro /10 | Escalabilidad /10 | Filosofía Protos /10 | Transferable lesson |
| --- | ---: | ---: | ---: | --- |
| VS Code official bundling guidance | 10 | 10 | 10 | bundle client dependencies, keep `vscode` external |
| VS Code Go | 10 | 10 | 9.5 | bundled JS client + external language/toolchain backend |
| clangd VS Code | 9.5 | 9.5 | 9.5 | bundled LSP client + separate server executable |
| rust-analyzer client bundling | 10 | 10 | 9 | mature `vscode-languageclient` bundling precedent |
| rust-analyzer server-in-VSIX model | 8 | 6.5 | 6 | turnkey but platform release matrix becomes editor concern |
| Ruff VS Code | 9.5 | 9.5 | 9 | production client bundle; backend remains separable |
| Red Hat Java/XML | 9 | 9.5 | 8.5 | committed lockfile + production bundle at scale |
| Haskell/OCaml external server/toolchain | 9.5 | 9 | 9.5 | editor package stays separate from toolchain |
| raw production `node_modules` in VSIX | 6.5 | 5.5 | 7.5 | simple but large public filesystem/supply-chain surface |
| activation-time dependency download/install | 4 | 4 | 3 | weak reproducibility/offline/security |

## Candidate comparison

### A — committed lockfile + raw production `node_modules`

Commit a lockfile, use `npm ci`, prune dev dependencies and package the exact
production dependency tree beside raw extension sources.

Advantages:

- smallest build-system change;
- dependency graph can be pinned;
- direct source-level debugging.

Costs:

- large multi-file public artifact;
- dependency-internal file churn becomes VSIX churn;
- larger install/load and package-regression surface;
- weaker alignment with current VS Code guidance.

### B — committed lockfile + bundled JavaScript client closure

Commit a lockfile and use a production bundler so extension sources,
`vscode-languageclient` and the required JavaScript transitive closure become one
bounded runtime bundle. The host-provided `vscode` module remains external.
`protos.runtime.executable` remains the external Protos runtime/server.

Advantages:

- small explicit runtime artifact;
- npm topology remains a build input rather than shipped filesystem API;
- fast install/load;
- straightforward package-content allowlisting;
- preserves one external Protos runtime authority.

Costs:

- explicit build/bundle step;
- third-party notice/license obligations for bundled code;
- bundle-level runtime regression required.

### B′ — B + deterministic inputs + hard thin-client boundary — SELECTED

B′ keeps B's artifact model and makes the durable contract explicit:

1. commit `editors/vscode/package-lock.json`;
2. use `npm ci` from a clean checkout;
3. pin bundling/packaging tools in `devDependencies`; do not depend on ambient
   global tools or moving `npx` resolution;
4. emit a Node/CommonJS production bundle appropriate for the existing workspace
   extension;
5. keep `vscode` external because it is provided by the extension host;
6. package no production `node_modules` tree once the client bundle is proven
   self-contained;
7. keep `protos.runtime.executable` external;
8. copy no Protos Java/JAR/runtime/language-server payload into the VSIX;
9. perform no activation-time npm install or dependency network fetch;
10. include package-root APL-1.0 material plus required third-party
    notices/licenses;
11. exclude tests, fixtures, source-only/build-only material and generated package
    artifacts through an explicit VSIX content boundary; and
12. add a fail-closed package-content regression so new files/dependencies cannot
    silently expand the shipping artifact.

The bundler product is subordinate implementation detail. Generation 1 may use
esbuild because the current Protos extension is plain CommonJS JavaScript with
one extension entry point and no TypeScript/CSS/webview pipeline. A future
bundler migration does not alter D127 if the emitted boundary remains equivalent.

### C — bundle Protos runtime/language server into the VSIX

Advantages:

- turnkey installation;
- offline after VSIX installation.

Costs:

- duplicates/competes with `protos.runtime.executable`;
- couples editor and runtime release cadence;
- creates JVM/native/platform payload matrix inside editor packaging;
- larger artifact and more complex Remote/Dev-Container placement;
- requires reopening earlier LM009 runtime-discovery contracts.

### D — install/download npm client dependencies on first activation

Advantages:

- tiny initial VSIX.

Costs:

- network-dependent activation;
- mutable installation state;
- weaker reproducibility/offline behavior;
- more proxy/certificate/Remote failure modes;
- greater supply-chain exposure.

### E — remove `vscode-languageclient` and hand-roll the LSP client

Advantages:

- removes the direct npm LSP client dependency.

Costs:

- duplicates mature JSON-RPC/LSP machinery;
- increases compatibility/correctness burden;
- creates unnecessary editor-specific protocol authority.

### F — retain the pre-I1 no-lock/unbounded package state

This cannot satisfy LM009-I1 reproducibility or bounded-package requirements and
is not a viable closure candidate.

## Project-owner scores

| Candidate | Aguante de futuro /10 | Escalabilidad /10 | Filosofía Protos /10 |
| --- | ---: | ---: | ---: |
| A | 7.5 | 6.5 | 8 |
| B | 9.5 | 9.5 | 9.5 |
| **B′** | **10** | **10** | **10** |
| C | 7 | 5.5 | 3.5 |
| D | 4 | 4.5 | 2.5 |
| E | 5.5 | 6 | 2 |
| F | 2 | 3 | 5 |

## Selected architecture

The durable boundary is:

```text
VSIX
  +-- editor client bundle
  |     +-- Protos VS Code orchestration
  |     `-- bundled LSP/JSON-RPC JavaScript client closure
  +-- declarative editor assets
  +-- APL-1.0 license material
  `-- required third-party notices/licenses

workspace extension host
  `-- external protos launcher
        +-- run
        +-- debug
        `-- language-server
```

No Protos semantics or runtime implementation move into JavaScript.

## Ratified invariants

```text
D127_SELECTED_CANDIDATE=B_PRIME

PACKAGE_LOCKFILE=COMMITTED
CLEAN_INSTALL_COMMAND=npm_ci
BUILD_TOOLING=PINNED_DEV_DEPENDENCIES
PRODUCTION_CLIENT=BUNDLED
VSCODE_MODULE=EXTERNAL_HOST_PROVIDED
PRODUCTION_NODE_MODULES_IN_VSIX=NO

PROTOS_RUNTIME_IN_VSIX=NO
PROTOS_LANGUAGE_SERVER_PAYLOAD_IN_VSIX=NO
PROTOS_RUNTIME_EXECUTABLE_AUTHORITY=UNCHANGED
ACTIVATION_TIME_DEPENDENCY_DOWNLOAD=NO

APL_LICENSE_AT_PACKAGE_ROOT=YES
THIRD_PARTY_NOTICES_REQUIRED=YES
PACKAGE_CONTENT_BOUNDARY=EXPLICIT
PACKAGE_CONTENT_REGRESSION=FAIL_CLOSED

REMOTE_DEVCONTAINER_WORKSPACE_EXTENSION_MODEL=UNCHANGED
PUBLIC_REGISTRY_POLICY_SELECTED=NO
EXTENSION_VERSION_POLICY_SELECTED=NO
```

## Implementation consequence

After this governance record is published, LM009-I1 is released to implement the
mechanical packaging slice under B′:

1. add the committed npm lockfile;
2. pin the generation-1 bundling/packaging toolchain;
3. add the client bundle build;
4. preserve `vscode` as external;
5. package no raw production `node_modules`;
6. add package-root APL material and required third-party notices/licenses;
7. add an explicit `.vscodeignore` and/or equivalent allowlisted package boundary;
8. exclude test/fixture/build-only/repository-only material;
9. add a fail-closed VSIX content regression; and
10. prove deterministic packaging from a clean checkout.

LM009-I1 does not publish to Marketplace/Open VSX, choose extension/runtime
version coordination, or change the external Protos runtime authority. Those
remain downstream LM009-I responsibilities.
