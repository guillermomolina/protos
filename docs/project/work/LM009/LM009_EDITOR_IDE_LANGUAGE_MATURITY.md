# LM009 — Editor & IDE Language Maturity

Status: **IN_PROGRESS**

Current published slice after this record: **LM009-A CLOSED; LM009-B CLOSED (S1 PASS); LM009-C CLOSED (S2 PASS); LM009-D CLOSED; LM009-E CLOSED (S3 PASS); LM009-F CLOSED (foundation live check PASS); LM009-G BLOCKED (G1/G2 CLOSED; D082/D085/D089/D102 RATIFIED; G3P P1/P2/P3A + D102 package-domain implementation CLOSED, P3B/P4 pending; G3 BLOCKED_BY_PROJECT_BINDING_IMPLEMENTATION)**

Nature: non-normative language-maturity / editor-tooling evidence

Live coordination: GitHub Issue #288

LM009-A coordination: GitHub Issue #289

LM009-C coordination: GitHub Issue #302

Authoring evidence snapshot: `36be9e0a8613c690f2cb23043fe5ce35fab57b04`.
The publication launcher revalidates the relevant execution-time repository
anchors; this authoring SHA is evidence provenance, not a required future
`PUBLICATION_BASE`.

## Purpose

Define and prove the maturity needed for ordinary Protos development from a
modern IDE while keeping the editor subordinate to the real Protos language,
runtime, parser, resolver and tooling authorities.

Visual Studio Code is the first reference integration and end-to-end acceptance
surface. It is not a Protos semantic authority, and LM009 must not make future
editor support depend on reproducing Protos semantics inside a VS Code
extension.

## Existing foundation

LM009 consumes the already-approved hybrid Truffle-first editor architecture
from AUD002 and the closed I026 Truffle tooling foundation.

The current repository already establishes the following foundations:

- Protos is a registered Truffle guest language and retains exact source
  identity and source sections.
- user-executable source locations are exposed through the ratified
  instrumentation surface;
- debugger values and activation-native local scopes are available through
  faithful read-only Truffle projections;
- real GraalVM DAP behavior has been proven against Protos source for
  breakpoints, stepping, threads, stack frames, scopes, representative values,
  continue and clean disconnect;
- real GraalVM dynamic-LSP transport/document synchronization has been proven,
  while its current static-intelligence boundary is deliberately limited;
- `ProtosCli` already accepts an ordinary source path and executes that file
  through the real Protos runtime;
- parser failures already retain exact `SourceSpan` data suitable for
  source-accurate static diagnostics.

The corresponding durable I026 record is
[`../I026/I026_TRUFFLE_TOOLING_FOUNDATION.md`](../I026/I026_TRUFFLE_TOOLING_FOUNDATION.md).
The selected editor-tooling architecture is retained in
[`../AUD002/AUD002_GRAALVM_EDITOR_TOOLING_AUDIT.md`](../AUD002/AUD002_GRAALVM_EDITOR_TOOLING_AUDIT.md).

## LM009-A classification vocabulary

Every baseline capability is classified as exactly one of:

- `FOUNDATION_PROVEN` — the underlying Protos/runtime behavior already has
  evidence; later editor work may consume it without redefining it.
- `EDITOR_INTEGRATION_MISSING` — the required runtime/tool behavior exists or is
  straightforwardly invokable, but no user-facing VS Code integration is
  published.
- `STATIC_LANGUAGE_SERVICE_MISSING` — the capability requires Protos-specific
  static language intelligence over the real parser/source/module/package
  authorities.
- `DEFERRED_NON_BASELINE` — useful editor functionality that is intentionally
  unnecessary for initial LM009 closure.
- `DECISION_REQUIRED` — a substantive unresolved architecture/public-contract
  choice has been exposed. The owning later slice may audit alternatives but
  cannot silently select one.

`DECISION_REQUIRED` does not imply `PLATxxx` automatically. A later decision
uses the family appropriate to the actual authority: platform/runtime-specific
architecture may require `PLATxxx`; public CLI/tool policy or another durable
boundary must use its own proper owner. Identifier shape is not authority.

## Capability baseline

| Capability | Classification | Current evidence / missing boundary | LM009 owner |
|---|---|---|---|
| `.protos` language association | `EDITOR_INTEGRATION_MISSING` | No shipped editor language registration; VS Code must recognize the canonical extension. | B |
| Syntax highlighting | `EDITOR_INTEGRATION_MISSING` | Existing GitHub #11 already defines the bounded lexical-highlighting work. | B / #11 |
| Comments, brackets and basic editor configuration | `EDITOR_INTEGRATION_MISSING` | Editor metadata is absent; it must follow the normative grammar rather than invent syntax. | B |
| Run current file | `EDITOR_INTEGRATION_MISSING` | Runtime foundation exists: the CLI already accepts a source path directly. VS Code wiring is absent. | C |
| Runtime discovery/configuration | `EDITOR_INTEGRATION_MISSING` | The editor has no published way to locate/select the Protos runtime. | C |
| Public DAP launch/server lifecycle | `DECISION_REQUIRED` | I026 proved the real GraalVM DAP only as bounded test evidence and explicitly did not select public CLI, port, hosting or lifecycle policy. | D |
| Source breakpoints | `FOUNDATION_PROVEN` | I026-F proved verified file-backed Protos breakpoints through real GraalVM DAP. | E consumes |
| Stepping | `FOUNDATION_PROVEN` | I026-F proved real `next` to the following Protos source line. | E consumes |
| Threads and stack frames | `FOUNDATION_PROVEN` | I026-F proved threads and exact Protos stack/source location. | E consumes |
| Activation-local scopes | `FOUNDATION_PROVEN` | I026-E/F proved the PLAT015 activation-native read-only scope with no artificial global/receiver/parent fiction. | E consumes |
| Representative scalar/indexed debugger values | `FOUNDATION_PROVEN` | I026-D/F proved scalar values and expandable indexed Array values through the real debugger path. | E consumes |
| Static parse diagnostics on unexecuted source | `STATIC_LANGUAGE_SERVICE_MISSING` | The real parser and `ParseError`/`SourceSpan` foundation exist, but no static editor service publishes diagnostics. | F/G |
| Document symbols | `STATIC_LANGUAGE_SERVICE_MISSING` | Graal dynamic LSP does not currently advertise this; static Protos source knowledge is required. | G |
| Workspace symbols | `STATIC_LANGUAGE_SERVICE_MISSING` | Graal dynamic LSP does not currently advertise this; workspace/package knowledge is required. | G |
| Go to definition | `STATIC_LANGUAGE_SERVICE_MISSING` | Graal dynamic LSP does not currently advertise definition support; faithful lookup must reuse Protos source/module resolution. | G |
| References | `STATIC_LANGUAGE_SERVICE_MISSING` | Graal dynamic LSP does not currently advertise references; static indexing/semantic knowledge is required. | H |
| Completion | `STATIC_LANGUAGE_SERVICE_MISSING` | The current Graal dynamic-LSP result is faithfully empty; do not fabricate completion from a fake editor-side model. | H |
| Hover | `STATIC_LANGUAGE_SERVICE_MISSING` | The current Graal dynamic-LSP result is faithfully empty; static Protos intelligence is required for baseline-useful hover. | H |
| Signature help | `STATIC_LANGUAGE_SERVICE_MISSING` | The current Graal dynamic-LSP result is faithfully empty; any useful result must follow real callable semantics. | H |
| Install/package + clean end-to-end setup | `EDITOR_INTEGRATION_MISSING` | No reference VS Code extension/package/install workflow is published. | I |
| VS Code extension ownership/repository/packaging topology | `DECISION_REQUIRED` | AUD002 selected a thin extension but deliberately did not select its durable distribution/repository topology. | B/C decision checkpoint |
| Static language-service hosting/transport/lifecycle | `DECISION_REQUIRED` | AUD002 selected reuse of the real parser/semantic/resolver authorities but did not select a standalone/delegate server hosting architecture. | F decision checkpoint |
| Graal dynamic-LSP augmentation beyond the baseline | `DEFERRED_NON_BASELINE` | Transport/capability evidence exists, but initial IDE usability does not require manufacturing runtime-derived features that are currently empty. | later LM009 |
| Formatter / canonical formatting | `DEFERRED_NON_BASELINE` | Formatting policy is not required for the initial usable editor baseline. | deferred |
| Rename/refactoring engine | `DEFERRED_NON_BASELINE` | Useful later; not required for initial LM009 closure. | deferred |
| Debugger expression evaluation/mutation | `DEFERRED_NON_BASELINE` | PLAT013 intentionally kept the debugger baseline read-only. | deferred |

## Architecture boundaries exposed by the baseline

LM009-A selects **no new architecture**. It records three checkpoints that later
work must resolve before crossing their durable boundaries:

1. **Reference extension topology.** B/C may establish lexical metadata and thin
   invocation wiring, but repository/distribution/ownership topology must not be
   selected accidentally as an incidental `package.json` location.
2. **Public DAP launch contract.** D must compare how the proven GraalVM DAP is
   enabled, addressed, started/stopped and bound to an execution without turning
   test-only evidence into an accidental public contract.
3. **Static language-service hosting boundary.** F must determine how the real
   parser/source/module/package authorities are exposed to editor protocols
   without implementing a second Protos parser or semantic model in TypeScript.

If one of these is platform/runtime-specific and materially constrains later
implementations, it belongs at a `PLATxxx` approval gate. If it is public
CLI/tool/editor policy rather than platform architecture, it must be routed to
that proper authority instead. LM009 itself is not a generic decision bucket.

## Reference acceptance scenarios

Later slices must preserve these concrete scenarios.

### S1 — Open and edit

From a clean VS Code installation plus the documented Protos integration:

1. open a workspace containing ordinary `.protos` files;
2. VS Code recognizes them as Protos;
3. comments, delimiters and lexical categories are highlighted/configured
   consistently with the normative grammar;
4. no editor grammar changes compiler/runtime behavior.

### S2 — Run current file

Given an ordinary standalone source file:

1. open the file in VS Code;
2. invoke the reference Run Protos File action;
3. the integration invokes the real Protos runtime rather than an editor-side
   evaluator;
4. stdout/stderr and exit outcome are visible through the ordinary editor
   workflow;
5. the user does not need Maven or repository-internal class names merely to run
   the file.

### S3 — Debug ordinary Protos source

Given a file containing at least two instrumentable source statements and
representative local scalar/indexed values:

1. set a source breakpoint in VS Code;
2. launch debugging with F5 or the documented equivalent;
3. stop at the verified Protos source location;
4. observe threads and the correct stack frame;
5. inspect the activation-local scope and representative values;
6. step to the next expected Protos source location;
7. continue to normal completion;
8. terminate/disconnect cleanly;
9. do not expose artificial global scope, receiver binding, lexical-parent
   fiction or host implementation state.

### S4 — Diagnose unexecuted source

Given a `.protos` file that has not been executed:

1. introduce a syntax error;
2. receive a source-accurate diagnostic derived from the real Protos parser
   authority;
3. fix the error and observe the diagnostic clear;
4. no duplicate editor grammar/parser is treated as semantic authority.

### S5 — Navigate unexecuted workspace source

Given a workspace with a faithful symbol/definition relationship expressible by
current Protos source/module semantics:

1. inspect the symbol without first executing the source;
2. navigate to its definition using the static Protos language service;
3. source/module/package resolution follows the same canonical authorities used
   by Protos rather than editor-specific path guessing.

LM009 baseline closure requires useful symbol/navigation support, not every
possible dynamic dispatch target to become statically decidable.

### S6 — Clean installation

From a clean supported environment:

1. install/configure the reference VS Code integration from published
   instructions;
2. configure or discover an appropriate Protos runtime;
3. repeat S1, S2, S3 and the baseline static-language scenarios without a
   checkout-specific hidden setup;
4. remove/disable the extension without altering ordinary Protos runtime
   behavior.

## Slice dependency map after A

Publication of LM009-A releases investigation/implementation work as follows:

```text
LM009-A  baseline
├── LM009-B  language association + highlighting (#11)
├── LM009-C  run-current-file integration
├── LM009-D  public DAP launch-contract audit/decision path
└── LM009-F  static language-service architecture audit
      |
      └── LM009-G  diagnostics + symbols + definition

LM009-D ──> LM009-E  VS Code debugging integration
LM009-F/G ──> LM009-H completion + hover + signature help + references
B + C + E + G + required H baseline subset ──> LM009-I packaging/end-to-end closure
```

The graph expresses dependency order, not automatic approval. B/C/D/F must stop
at any substantive decision checkpoint identified above.

## LM009-A closure evidence

LM009-A is closed by publication of this record when all of the following are
true:

- the required capability set is classified;
- every baseline capability has a later owner;
- current CLI, parser, I026 DAP and I026 dynamic-LSP evidence is distinguished
  from missing editor/static-service integration;
- exact reference scenarios S1-S6 are recorded;
- unresolved durable architecture is visible rather than silently selected;
- no VS Code extension, public DAP launcher or static language server is
  implemented by A;
- no specification, runtime implementation, public API, license terms or Maven
  implementation version changes are made.

The next independent work fronts after publication are LM009-B/#11, LM009-C,
LM009-D investigation and LM009-F investigation.

## LM009-B approved current reference-extension topology

Decision status: **APPROVED CURRENT TOPOLOGY**

Approval: explicit project-owner approval on 2026-09-10, with future
reconsideration deliberately permitted.

The first reference VS Code integration is selected to live inside the
`guillermomolina/protos` monorepo at:

```text
editors/
└── vscode/
```

This is the current-scale ownership topology, not a permanent prohibition on a
future repository split.

The selected boundary is:

- `spec/` remains the normative lexical/syntactic authority;
- the real Protos parser, resolver, runtime and later static language-service
  implementation remain their own authorities rather than being reimplemented
  in the extension;
- `editors/vscode/` is a thin editor/product-integration subtree;
- the editor subtree may own its own package version and Node/editor
  dependencies, but ordinary Protos Maven builds and runtime execution must not
  depend on Node, npm or VS Code assets merely because the reference extension
  exists;
- lexical assets should remain reusable where practical rather than encoding a
  VS Code-only semantic model;
- a future move to a dedicated `protos-vscode` repository remains allowed when
  independent editor release cadence, contributor volume, multiple editor
  clients, or product ownership makes the additional cross-repository
  coordination worthwhile.

This approval resolves only the repository/distribution topology checkpoint
exposed by LM009-A. It deliberately does **not** select:

- a Visual Studio Marketplace publisher identifier or permanent extension ID;
- the final minimum/supported VS Code version policy;
- public VSIX/Marketplace release policy;
- Protos runtime discovery/configuration UX;
- public GraalVM DAP launcher/port/server lifecycle;
- static Protos language-service process/transport/lifecycle architecture; or
- Graal dynamic-LSP product policy.

Those remain independently owned by the later LM009 slices that expose them.
Any substantive new choice encountered while implementing LM009-B must still
stop at the normal approval gate.

The LM009-A capability matrix records the topology row as `DECISION_REQUIRED`
because that was the correct state at A closure. This section is the durable
post-A resolution of that checkpoint; the historical A classification is not
rewritten retroactively.

With this topology published, LM009-B implementation is released to add only
the approved lexical/editor-metadata surface under `editors/vscode/`: `.protos`
language association, comment/bracket configuration, non-normative TextMate
highlighting, representative fixtures/validation, and development/install-use
instructions. Run integration remains LM009-C; DAP remains LM009-D/E; static
language intelligence remains LM009-F/G/H; public packaging/Marketplace closure
remains LM009-I.

## LM009-B lexical-assets tranche

Status: **LEXICAL ASSETS PUBLISHED; INSTALLABLE MANIFEST DECISION PENDING**

This tranche establishes the reusable lexical/editor asset substrate under the
approved `editors/vscode/` topology without yet creating an installable VS Code
extension.

Published assets:

- `editors/vscode/syntaxes/protos.tmLanguage.json` — non-normative TextMate
  grammar following the current Core v0.1 lexical surface;
- `editors/vscode/test/fixtures/lexical.protos` — representative valid-source
  fixture covering comments, String forms/escapes, numeric families, exact
  reserved spellings, member-name reserved spellings, Closures, contextual
  ellipsis and standard/custom operators;
- `editors/vscode/test/validate_grammar.py` — editor-local structural guard; and
- `editors/vscode/README.md` — authority boundary and development status.

The TextMate asset intentionally highlights only the seven exact Core v0.1
reserved spellings (`this`, `context`, `args`, `super`, `true`, `false`,
`null`) as language-special tokens. Familiar spellings such as `if`, `else`,
`while`, `class`, `function`, `try`, `catch`, `async`, `await`, `var`, `let`,
`const`, `import`, and `export` are not promoted into a fake keyword set.

Reserved spellings immediately following member-access `.` are deliberately not
highlighted as language-special tokens because the normative grammar permits
them there as ordinary structural member names.

This tranche does not publish `package.json`, `language-configuration.json`,
runtime discovery, Run Current File, DAP, LSP, Marketplace metadata, or any
TypeScript semantic implementation. Therefore it does not yet satisfy LM009
scenario S1 end-to-end and does not close LM009-B.

The next LM009-B checkpoint is the already-exposed installable-manifest
compatibility decision: select the bounded `engines.vscode` support/development
floor before adding the manifest, `.protos` language association and basic
language configuration. That choice remains unselected by this publication.

## LM009-B manifest identity and VS Code support floor

Decision status: **APPROVED**

Approval: explicit project-owner approval on 2026-09-10 after confirming that
`guillermomolina` is an already-owned Visual Studio Marketplace publisher.

The approved manifest contract is:

- publisher: `guillermomolina`;
- extension name: `protos`;
- resulting extension identity: `guillermomolina.protos`;
- initial extension version: `0.1.0`;
- `engines.vscode`: `^1.104.0`;
- language id: `protos`; and
- canonical file association: `.protos`.

The extension version is intentionally independent from the Protos Maven/runtime
implementation version. The VS Code support floor is stable by default: it is
not raised merely because newer VS Code releases exist. A later floor increase
requires a demonstrated editor API or dependency need introduced after 1.104
and must validate that new compatibility boundary.

This approval does not publish a Marketplace release, choose a release cadence,
select runtime discovery, public DAP lifecycle, static-language-service hosting,
or change Protos specification/runtime semantics.

## LM009-B declarative manifest-wiring tranche

Status: **MANIFEST WIRED; S1 LIVE VS CODE EVIDENCE PENDING**

This tranche turns the already-published lexical assets into a declarative VS
Code extension surface without executable extension-host code.

Published wiring:

- `editors/vscode/package.json` contributes the `protos` language, canonical
  `.protos` association and existing `source.protos` TextMate grammar under the
  approved `guillermomolina.protos` identity and `^1.104.0` support floor;
- `editors/vscode/language-configuration.json` configures only the normative
  line/block comment delimiters plus ordinary `()`, `[]` and `{}` structural
  bracket pairs;
- `editors/vscode/test/validate_extension.py` guards identity, compatibility
  floor, language association, grammar binding and the intentionally declarative
  no-runtime-code boundary; and
- `editors/vscode/README.md` documents repository-local validation and the live
  VS Code S1 check.

The language configuration deliberately does not invent `wordPattern`,
indentation, folding, on-enter, quote, formatter, parser, or semantic behavior.
Those surfaces require their own real authority or evidence if later work needs
them.

Repository-side structural validation can prove that the VS Code manifest
wiring is internally coherent, but it cannot honestly replace observation in an
actual VS Code extension host. LM009-B therefore remains `IN_PROGRESS` until
the documented S1 live check confirms that VS Code recognizes a `.protos` file
as Protos and applies the published grammar/configuration.

No Protos runtime source, specification, Maven implementation version, public
DAP contract, static language-service architecture or Marketplace release is
changed by this tranche.

## LM009-B S1 live evidence and closure

Status: **CLOSED**

Live evidence was completed by the project owner on 2026-09-10 using a real
Visual Studio Code Extension Development Host against the published
LM009-B manifest surface from `e9ca30d61cb53645a66542710182db5e5b39db91`.

Observed S1 behavior:

- an ordinary `.protos` file was recognized as language mode **Protos**;
- the published `source.protos` TextMate grammar was visibly active over the
  representative lexical fixture;
- the editor's line/block comment actions used the configured `//` and
  `/* ... */` forms; and
- typing `(`, `[` and `{` produced the configured closing delimiters.

This completes scenario S1 together with the repository-side lexical and
manifest structural validators. LM009-B therefore satisfies its closure rule:
the approved reference-editor topology is present, `.protos` association and
useful non-normative highlighting are implemented, basic comment/bracket
configuration follows the grammar, representative fixture/validation and
development/use instructions are present, and no Protos semantic authority is
duplicated in the extension.

LM009 remains **IN_PROGRESS**. Run-current-file integration belongs to LM009-C;
public DAP launch policy and VS Code debugging remain LM009-D/E; static language
intelligence remains LM009-F/G/H; and public packaging/clean end-to-end closure
remains LM009-I.

This closure changes no Protos specification, runtime implementation, Maven
implementation version, public DAP contract, static-language-service
architecture, Marketplace release state, or Node/npm dependency boundary.

## LM009-C approved Run Current File contract

Decision status: **APPROVED**

Coordination: GitHub Issue #302.

Project-owner approval on 2026-09-10 selected the launcher-command +
VS Code Task `ProcessExecution` design.

The selected editor contract is:

- the runtime authority consumed by VS Code is one external Protos launcher
  executable, never repository-internal Java/JAR/classpath structure;
- machine setting `protos.runtime.executable` defaults to `protos`, using
  ordinary PATH resolution; an explicit configured value overrides that default;
- the extension performs no workspace launcher scanning, repository-layout
  guessing, Java/JAR reconstruction, or duplicate runtime validation;
- `Protos: Run Current File` accepts an active `protos` document only when it
  resolves to an execution-host filesystem path: local `file:` or VS Code Remote
  `vscode-remote:`; other/virtual schemes remain non-executable, the document is
  saved before launch when dirty, and launch aborts if that save does not
  complete;
- execution uses a VS Code Task backed by `ProcessExecution(executable,
  [absoluteSourcePath], { cwd: sourceParent })`, with no shell command
  construction;
- task UI uses a dedicated/revealed terminal so stdout/stderr and ordinary
  process completion remain editor-visible;
- LM009-C introduces no application-argument UX;
- Workspace Trust support is `limited`, the runtime setting is listed in
  `restrictedConfigurations`, and the command performs an independent
  `workspace.isTrusted` guard before execution;
- safe declarative LM009-B highlighting remains available in Restricted Mode;
- current S2 live proof may use the supported POSIX/JVM distribution while the
  editor contract remains compatible with a future directly executable
  Windows/native launcher; and
- LM009-D's public DAP launch/server/lifecycle contract remains entirely
  unselected.

Task scoping is implementation machinery rather than execution semantics: use
the owning `WorkspaceFolder` when one exists, otherwise VS Code's supported
`TaskScope.Workspace`. The task `cwd` remains the source parent in either case.
`TaskScope.Global` is deliberately not used because current VS Code documents it
as unsupported.

This decision changes no Protos language semantics, runtime semantics, package
execution semantics, public DAP contract, static-language-service architecture,
or Marketplace release policy.

## LM009-C run-wiring tranche

Status: **CLOSED — S2 LIVE VS CODE EVIDENCE PASS**

This tranche implements only the approved LM009-C contract:

- `editors/vscode/extension.js` registers `protos.runCurrentFile`, retains the
  Workspace Trust/file/language/save guards and delegates execution to a VS Code
  Task `ProcessExecution`;
- `editors/vscode/package.json` gains the extension-host entry point, the
  `Protos: Run Current File` command, machine-local launcher setting, command
  visibility/enabling guards, and limited Workspace Trust declaration;
- `editors/vscode/test/run_current_file.test.js` exercises runtime override,
  argument separation, source-parent cwd, task scope, save-before-run, trust and
  document guards, and start-failure reporting without requiring VS Code itself;
- `editors/vscode/test/validate_extension.py` retains the LM009-B lexical/editor
  invariants while guarding the approved LM009-C manifest/runtime boundary; and
- `editors/vscode/README.md` documents configuration, security boundary and the
  live S2 procedure.

There is no shell command construction, child-process owner outside VS Code
Tasks, editor-side Protos evaluator, workspace runtime scan, application
arguments, package logical-module run mode, DAP/LSP behavior, or Java/JAR
reconstruction.

Repository-side validation proves the wiring and guard logic. LM009-C remains
`IN_PROGRESS` until S2 is exercised in a real VS Code Extension Development Host
against the real Protos launcher with stdout/stderr and completion visible in
the Task terminal.
## LM009-C remote execution correction

Decision status: **APPROVED — C REMOTE WORKSPACE-HOST MODEL**

S2 live testing on 2026-09-10 exposed a portability defect in the first
run-wiring tranche: a Dev Container correctly installed `guillermomolina.protos`
0.1.0 and recognized `.protos`, but the Run command was hidden because the
manifest and extension implementation treated only URI scheme `file:` as
executable. VS Code Remote represents the workspace-host filesystem through
`vscode-remote:` URIs.

The project owner explicitly approved Option C after comparison with retaining a
local-only contract and with special-casing `vscode-remote:` while consuming its
`fsPath` directly.

The selected correction is:

- the extension declares `extensionKind: ["workspace"]`, so executable editor
  integration runs where the workspace and Protos launcher live;
- Run Current File accepts exactly local `file:` and VS Code Remote
  `vscode-remote:` execution resources;
- local `file:` uses its ordinary `fsPath`;
- `vscode-remote:` never consumes the remote URI's `fsPath` directly: its
  decoded URI `path` is converted to a `file:` URI in the workspace extension
  host, and only that file-scheme URI is converted to the host-native filesystem
  path;
- the resulting host-native source path remains the single `ProcessExecution`
  argument and its parent remains `cwd`;
- virtual/non-executable schemes such as `vscode-vfs:` are rejected rather than
  fabricating a host filesystem path;
- Restricted Mode, save-before-run, launcher configuration/PATH behavior,
  dedicated Task terminal, no-shell construction and all previous LM009-C
  exclusions remain unchanged; and
- safe LM009-B lexical/editor functionality is not disabled merely because a
  virtual resource cannot be executed.

This refines the original phrase `file-backed`: in a Remote workspace, a physical
file is represented to the editor by a `vscode-remote:` URI while the workspace
extension host is colocated with the actual filesystem and launcher.

The repaired VS Code Remote/Dev Container path was subsequently exercised by
the project owner and passed S2. The durable live evidence and closure are
recorded in the section below.

## LM009-C S2 live evidence and closure

Status: **CLOSED**

Live evidence was completed by the project owner on 2026-09-10 after publication
of the remote workspace-host correction at
`3fe7ec18e0be84e5c5598eb669fb8a12b77326c1`.

Observed S2 evidence:

- the local test VSIX was installed in the real VS Code Dev Container extension
  host as `guillermomolina.protos@0.1.0`;
- ordinary `.protos` source was recognized as Protos;
- after the `vscode-remote:` correction, `Protos: Run Current File` was exposed
  for the remote file;
- invoking that command successfully executed the current Protos source through
  the real external launcher in the Dev Container using the selected VS Code
  Task / `ProcessExecution` path; and
- the same real launcher had independently been exercised in that container
  against `hello-world.protos` and produced `Hello, Protos!`, establishing that
  the editor action delegates to the actual Protos runtime rather than an
  editor-side evaluator.

Together with the repository-side tests and structural validation, this
satisfies scenario S2 and the LM009-C closure rule: runtime discovery remains the
single machine-local `protos.runtime.executable` setting with ordinary PATH
fallback; execution remains shell-free and task-backed; the source parent is the
working directory; dirty source is saved before execution; Restricted Mode
blocks code execution; local `file:` and VS Code Remote `vscode-remote:` are the
only executable filesystem schemes selected by this slice; virtual resources do
not receive a fabricated launcher path; and no Protos language semantics are
implemented in the editor.

LM009 remains **IN_PROGRESS**. Public DAP launch/lifecycle and VS Code debugging
remain LM009-D/E; static language intelligence remains LM009-F/G/H; and public
VSIX/Marketplace plus clean end-to-end packaging closure remains LM009-I.

This closure changes no Protos specification, runtime implementation, Maven
implementation version, public DAP contract, static-language-service
architecture, Marketplace release state, or ordinary Maven/Node dependency
boundary.

## LM009-D public debugger decision release

Decision status: **PLAT018 C-prime RATIFIED; D060 B-prime RATIFIED; IMPLEMENTATION CLOSED BY D1+D2**

The platform/runtime debugger-hosting boundary is fixed by
[`../../decisions/platform/PLAT018_DAP_DEBUG_SESSION_HOSTING.md`](../../decisions/platform/PLAT018_DAP_DEBUG_SESSION_HOSTING.md),
and the implementation-independent launcher/readiness contract is fixed by
[`../../decisions/tooling/D060_PUBLIC_DEBUGGER_LAUNCHER_READINESS.md`](../../decisions/tooling/D060_PUBLIC_DEBUGGER_LAUNCHER_READINESS.md).

The released LM009-D implementation contract is intentionally narrow:

```text
protos debug <file> [application-args...]
```

starts one PLAT018 debug invocation and emits one versioned
`PROTOS_DEBUG_READY {json}` success record on stdout after the loopback DAP
endpoint is bound and before guest execution. Stderr remains launcher/runtime
diagnostics, while guest stdout/stderr is carried by the real GraalVM DAP once
the session runs.

D060's readiness-file alternative was audited rather than dismissed: it is a
valid future fit for no-config/manual-terminal discovery when the editor is not
the process parent, but it is not imposed on ordinary F5 where the workspace
extension host already owns the launcher's process pipes.

LM009-D may now implement this approved platform + public-tooling boundary.
LM009-E remains the owner of the actual VS Code F5/debug integration and S3 live
editor evidence. Any new substantive CLI, remote-network, attach, stop-on-entry,
termination, distribution or editor-configuration choice still crosses its own
approval gate.

## LM009-D implementation evidence and closure

Status: **CLOSED**

LM009-D is closed by the combined published D1/D2 implementation and the D3
reconciliation recorded here.

Published implementation:

- **D1** — `d61a92a61d0ca81c966870e915dfed834daca92f`:
  the debug-only `ProtosPolyglotRuntimeHost` activates the real GraalVM DAP,
  binds IPv4 loopback port `0`, retains `Suspend=false` and
  `WaitAttached=true`, keeps raw Graal readiness behind a version-bounded
  adapter, and makes the matching DAP tool available in the production/runtime
  distribution while ordinary RuntimeHosts remain DAP-disabled.
- **D2** — `fcfa8932757c0300248e77bbf50e6ab7e299006b`:
  the ordinary launcher exposes the ratified
  `protos debug <file> [application-args...]` surface and emits exactly one
  compact D060 version-1 `PROTOS_DEBUG_READY {json}` record on stdout after the
  real endpoint is bound and before guest execution.

The final D2 focal evidence proves the complete LM009-D public-launch path
against the real GraalVM DAP:

1. the public launcher produces a valid numeric loopback endpoint allocated by
   the OS rather than by the editor or a Protos port registry;
2. DAP `initialize`, `launch` and `configurationDone` succeed against that
   endpoint;
3. `<file>` and `debug` remain launcher identity while arguments following the
   file remain ordinary application arguments through `process.args()`;
4. guest stdout and guest stderr are observed as DAP `output` events with the
   corresponding categories;
5. guest output is not duplicated onto the D060 readiness stdout or launcher
   diagnostics stderr;
6. normal guest completion leads to DAP `terminated`; after the client closes
   the session transport, the owning RuntimeHost/Engine completes cleanup and
   `protos debug` exits successfully; and
7. complete repository publication validation and isolated-worktree cleanup
   pass on the published candidate.

The failed D2 v1 and v3 attempts are useful retained implementation evidence.
v1 exposed that Protos Process standard-stream backends are explicit capabilities
and therefore bypass Truffle output consumers unless debug mode deliberately
routes them through the entered `Env.out()` / `Env.err()` channels. v3 then
proved both guest output categories through DAP and exposed that the focal test
was incorrectly requiring launcher exit while the DAP client transport remained
open. The final publication corrects both boundaries rather than weakening the
test.

No additional executable D3 change is justified: the D2 publication already
contains the real public-launch lifecycle evidence that D3 was decomposed to
obtain. D3 is therefore this governance/documentation-only reconciliation.

LM009-D deliberately does **not** select or promise manual attach,
remote-network listening, rendezvous/readiness files, stop-on-entry,
`terminateDebuggee`, or a stronger user-visible Stop contract. Those remain
outside this closure exactly as preserved by PLAT018 and D060.

LM009-E is now released to consume this public launcher/readiness boundary and
implement the actual VS Code F5 integration plus live S3 evidence. LM009-E must
remain a thin client of `protos debug` and the real DAP; it must not reconstruct
GraalVM options, allocate a debugger port, parse raw Graal readiness, proxy DAP,
or implement Protos semantics in TypeScript.

LM009 as a whole remains **IN_PROGRESS**. Static-language-service work remains
owned by LM009-F/G/H and packaging/end-to-end release by LM009-I.

This closure reconciliation changes no Protos specification, executable runtime,
editor executable asset, Maven implementation version, public debugger contract,
license term or Marketplace publication state.

## LM009-E approved VS Code F5 integration — E1

Status: **IN_PROGRESS — Candidate B-prime explicitly approved; E1 editor wiring published/pending S3**

The project owner explicitly approved LM009-E Candidate B-prime on 2026-09-10.
The selected reference-editor boundary consumes LM009-D rather than creating a
second debugger architecture:

- debugger identity is `type: "protos"` with `request: "launch"` only;
- source breakpoints are contributed for Protos;
- F5 without a persisted `launch.json` derives one in-memory configuration from
  the active executable Protos document;
- persisted configurations expose only `program` plus optional string `args`;
- the existing machine-local `protos.runtime.executable` remains the sole
  launcher configuration;
- a `DebugAdapterDescriptorFactory` owns one child launcher per VS Code session,
  starts `protos debug <absolute-file> [args...]` without a shell, consumes only
  D060 version-1 readiness, and returns `DebugAdapterServer(host, port)`;
- the editor never allocates/probes a port, parses raw GraalVM readiness,
  proxies DAP, or interprets Protos values/scopes;
- executable editor integration remains workspace-hosted and Restricted-Mode
  gated under the LM009-C local/Remote filesystem rule; and
- attach, remote-listen, readiness-file, stop-on-entry and stronger
  `terminateDebuggee` behavior remain deliberately unselected.

E1 owns the repository-side VS Code wiring and deterministic Node/Python
validation. It does **not** close LM009-E: S3 still requires live VS Code
evidence against the real external launcher for breakpoint, stop location,
threads, stack, activation-local scope/values, step, continue and clean normal
termination.

No Protos specification, runtime implementation, Maven implementation version,
DAP protocol implementation, static language-service architecture or
Marketplace release is changed by the editor-only E1 tranche.

## LM009-E D065 / PLAT020 / PLAT022 physical source correction

Status: **IMPLEMENTED — repository validation required; live S3 source-presentation retest pending**

Live S3 after E1 proved the external launcher, readiness, breakpoint, threads, stack, activation
scope/values, step, continue, guest output and process cleanup. The remaining defect was that the
executing ordinary filesystem source reopened as a second Plain Text editor document.

D065 selected the exact absolute lexically-normalized workspace/execution-host path as ordinary
physical tooling identity. The first consuming implementation attempt then exposed that Truffle's
internal `Source.newBuilder(String, File)` overload is not public. PLAT020 therefore ratified
Candidate A′: keep path/content/module facts backend-neutral outside Context ownership and
materialize the genuine physical Source only inside the owning entered Protos Context through that
Context's `Env`.

The unpublished v6 candidate proved those physical Source invariants but the real Graal DAP still
returned `sourceReference: 1`, because the exact path remained unreadable through the target
Context filesystem. PLAT022 therefore ratified Candidate D′: one Context-local, deny-by-default
readability authority admits only exact already-selected physical Sources read-only, with no
socket/write authority and no global path registry.

The consuming repository correction:

- removes eager path-backed literal `Source` ownership from `ProtosModuleSource`;
- retains exact characters plus optional selected physical path as immutable resolver facts;
- admits the exact D065 path read-only in the owning Context before physical Source publication,
  while unrelated paths remain deny-I/O and sockets/writes remain unavailable;
- materializes physical Sources through `Env.getPublicTruffleFile(...)`,
  `canonicalizePath(false)` and the already-read characters;
- routes direct CLI/debug file execution through that same entered-Context boundary;
- keeps virtual REPL/`-e` sources virtual and keeps deliberately unhosted semantic harnesses
  explicitly non-tooling;
- adds selected-path, symlink-preservation, payload-neutrality, exact-admission,
  unrelated-path denial, write denial and cross-Context isolation regression coverage;
- runs the real DAP behavior test through the same Context-local authority and requires the physical
  Source to be path-only in DAP (`sourceReference` absent); and
- adds no editor path mapping, DAP proxy, global Source registry/cache, outer-Polyglot execution
  refactor or Protos semantic change.

LM009-E remains open after repository publication. Final S3 still requires one real VS Code F5
retest proving that the active `.protos` file remains the same physical Protos document rather than
being reopened as a virtual/Plain Text source, while the already-proven debugger behaviors and clean
process termination remain intact.

## LM009-F1 parser-authority static-analysis core

Status: **IMPLEMENTED**

This is the first executable LM009-F slice released by ratified PLAT024
Candidate A′. It establishes only the editor-neutral parser-authority core; it
does not yet claim the client-owned session/workspace state or stdio LSP host
required to close LM009-F.

Published implementation boundary:

- `ProtosDocumentSnapshot` carries one immutable source text plus opaque document
  identity/version metadata. The core does not interpret that identifier as a
  filesystem path, URI, module specifier or ModuleKey.
- `ProtosStaticAnalysisCore` calls the existing real
  `ProtosParser(String).parseProgram()` directly. It requires no Polyglot/Truffle
  Context and executes no guest code.
- `ProtosStaticParseResult` retains either the real parsed `SurfaceSequence` or
  the parser failure message, exact `SourceSpan`, and
  `unexpectedEndOfSource` classification. This representation is internal
  analysis data, not an LM009-G public diagnostic contract.
- No LSP DTO/JSON dependency, VS Code semantic implementation, workspace/global
  index, module-resolution policy, background thread or shared mutable registry
  is introduced by F1.

Focused evidence covers valid unexecuted source, exact unexpected-token span,
unexpected-EOF classification and opaque snapshot metadata without runtime
execution.

LM009-F remains **IN_PROGRESS**. The next mechanical tranche is F2
client-session document/workspace custody and concurrency/cancellation mechanics
over this core, followed by the dedicated stdio LSP edge. LM009-G/H remain the
owners of editor-visible diagnostics, symbols, definition, completion, hover,
signature help and references. Any newly exposed substantive semantic or durable
platform choice still stops at the ordinary Dxxx/PLATxxx gate.

## LM009-F2 session/workspace snapshot custody

Status: **IMPLEMENTED**

F2 builds on the published F1 parser-authority core without selecting another
language or platform architecture. It establishes client-session-local mutable
custody around immutable source snapshots:

- one `ProtosStaticAnalysisSession` instance is owned by one future language
  server/client session;
- workspace identifiers remain opaque and partition independent document maps;
- document identifiers and numeric versions remain opaque analysis metadata;
- `putDocument` atomically replaces one current immutable snapshot within its
  workspace without interpreting version ordering;
- `parseCurrent` first captures one immutable snapshot and then invokes the F1
  core, so a concurrent replacement cannot alter the source observed by that
  parse;
- `isCurrent` compares the tagged snapshot value against current custody before
  an adapter publishes freshness-sensitive output;
- closing one document/workspace drops only that custody domain; and
- state is instance-local with no static/global semantic registry or shared
  cross-workspace mutable model.

The workspace/document maps are concurrent only to make independent requests
safe; F2 creates no background executor, worker pool, scheduler, guest Process,
Actor, Task, Truffle Context or ordinary-runtime overhead.

### Deliberately deferred protocol cancellation boundary

The earlier F decomposition mentioned cancellation mechanics in F2. Current
implementation audit narrows F2 rather than silently selecting a public
cancellation/lifetime policy. F2 therefore introduces **no cancellation
contract**. The F3 LSP edge owns standard protocol request cancellation and
shutdown mapping. If faithful support later requires a stronger parser-level
preemption/cooperative-cancellation guarantee, that is evaluated at the normal
substantive decision gate rather than embedded in this custody class.

LM009-F remains **IN_PROGRESS**. F3 must still provide the PLAT024-dedicated
toolchain-matched stdio LSP process/lifecycle edge over F1/F2. LM009-G/H continue
to own editor-visible diagnostics, symbols, definition, completion, hover,
signature help and references.

## LM009-F3 dedicated stdio LSP host

Status: **IMPLEMENTED**

F3 publishes the process/protocol edge selected by PLAT024 Candidate A′ without
moving LM009-G/H feature semantics into the foundation.

### Protocol/hosting boundary

- `ProtosLanguageServer` is one client-session-owned LSP service object.
- `ProtosLanguageServerStdio` uses the standard LSP4J stdio launcher; there is
  no port allocator, daemon discovery, authentication service or global server.
- `ProtosLanguageServerMain` is an internal JVM process entry point only. F3
  deliberately does **not** select the final public `protos ...` CLI spelling,
  Marketplace launch command or final JAR/Native Image packaging contract.
- Eclipse LSP4J 1.0.0 is an implementation dependency at the protocol edge.
  LSP DTOs do not enter `com.guillermomolina.protos.analysis`.
- ordinary Protos execution instantiates none of the F3 language-server state.

### Document synchronization boundary

F3 advertises exactly full open/change/close document synchronization. It maps
those notifications to immutable F2 snapshots with the exact LSP document URI,
version and current text.

All currently open buffers use one server-local custody-domain identifier
`lsp:open-documents`. This identifier is explicitly **not** a Protos workspace,
package root, module specifier or `ModuleKey`. There is no cross-document
semantic resolution in F3, so this container introduces no cross-workspace
semantic authority. LM009-G must establish any actual workspace/project/module
mapping through the canonical Protos resolution authorities rather than URI
prefix/path guessing.

F3 rejects incremental ranged changes because it advertises full synchronization
only. Numeric document versions remain opaque values passed to F2; F3 does not
invent ordering beyond replacing snapshots in received notification order.

### Lifecycle and cancellation boundary

`shutdown` records orderly LSP shutdown and returns before process exit. A later
`exit` requests status `0`; `exit` before `shutdown` requests status `1`,
matching the standard language-server process lifecycle.

F3 does not introduce a parser-level cancellation/preemption contract. LSP4J
owns the JSON-RPC protocol machinery, but there are no long-running G/H static
feature requests in F3 to cancel. If later semantic requests require stronger
cooperative parser/index cancellation, that requirement must be evaluated
explicitly rather than inferred from this host.

### Capability boundary

F3 intentionally advertises no diagnostics, document/workspace symbols,
definition, references, completion, hover or signature help. Those remain
LM009-G/H. Focused evidence covers:

- only the full document-sync foundation capability is advertised;
- exact open/change/close snapshot custody;
- rejection of ranged incremental changes;
- standard clean/premature exit status; and
- real LSP `Content-Length` stdio framing through LSP4J.

LM009-F remains **IN_PROGRESS** after F3. The remaining F closure work is the
reference client/toolchain launch wiring and end-to-end foundation proof that a
real editor client starts the dedicated matching server without embedding
Protos semantics in TypeScript.


## D070 language-server executable discovery and public launch surface

Status: **RATIFIED — Candidate A′ selected**

Explicit project-owner approval on 2026-09-11 ratifies the public tool-facing
launcher contract:

```text
protos language-server
```

The reference editor must start that command through the already-selected
`protos.runtime.executable` with argv `["language-server"]`, shell-free. Standard
LSP over stdin/stdout remains the PLAT024 baseline; stdout is protocol-only while
the server is active.

The configured Protos executable remains the single toolchain/version authority
for Run, Debug and the static language server. D070 introduces no second
language-server executable setting, sibling-path inference rule, editor-owned
server version, Java/JAR/classpath knowledge, global daemon or TCP discovery.

The public command names the service role rather than its current JVM/LSP
implementation. A future Native Image, sibling binary or self-hosted server may
be selected behind the same compatibility launcher without changing editor
clients.

LM009-F4 is **RELEASED_FOR_IMPLEMENTATION** only after this ratification reaches
`main`. F4 owns the public CLI dispatch, thin VS Code LanguageClient launch wiring
and end-to-end foundation proof. LM009-G/H feature semantics remain excluded.

## LM009-F4 public launcher and reference LanguageClient wiring

Status: **CLOSED — S4 foundation live check PASS**

F4 consumes published PLAT024 Candidate A′ and D070 Candidate A′ without adding
another design choice.

Published implementation boundary:

- `protos language-server` is dispatched by the ordinary `ProtosCli` launcher to
  the already-published F3 stdio host;
- `ProtosLanguageServerMain.run(input, output)` exposes only the internal stream
  boundary needed by the CLI while retaining standard server lifecycle behavior;
- CLI stdout remains LSP framing only while the command is active and
  unexpected command arguments fail before protocol startup;
- the reference VS Code extension uses `vscode-languageclient` as protocol/client
  machinery and launches exactly `protos.runtime.executable` with
  `["language-server"]`, shell-free;
- the existing runtime setting remains the sole Run/Debug/language-server
  toolchain authority;
- the LanguageClient document selector names only language id `protos`; it does
  not convert editor URIs into Protos module/package/path semantics;
- one extension activation owns at most one client/server process and stops it on
  deactivation; Restricted Mode owns none;
- no second server executable setting, sibling path inference, Java/JAR layout,
  editor-side parser/model, global daemon or TCP discovery is introduced; and
- LM009-G/H static language-intelligence semantics remain excluded.

Focused repository evidence covers real LSP Content-Length framing through the
public CLI command, help/argument boundary, exact LanguageClient command/argv and
single configuration authority, trust gating, client lifecycle/retry, and
retained Run/Debug/grammar structural tests.

The real VS Code foundation check is now **PASS**. Project-owner evidence against
published F4 (`ca3d477730e676faf60718e571d0d5367582aa4f`, Protos
`0.2.371-SNAPSHOT`) proved the complete F closure boundary:

- before opening a `.protos` document, no `language-server` process existed;
- opening the document in a real Extension Development Host started exactly one
  matching toolchain server through the selected launcher contract;
- the observed process was the current GraalVM Java runtime executing the current
  Protos JAR with argv `language-server`;
- after multiple edits/saves, the same single server PID remained alive, proving
  ordinary LSP document synchronization did not crash or restart the service;
- no second language-server executable setting, Java/JAR setting or editor-side
  Protos semantic authority was required; and
- closing the Extension Development Host removed the server process completely,
  proving client-owned lifecycle/teardown.

The temporary `node_modules` tree required for the live extension check is local
editor-development state and is ignored by the repository; it is not a Protos
runtime/distribution artifact.

LM009-F is therefore **CLOSED**. F1/F2/F3/F4 plus the real editor lifecycle and
document-sync composition satisfy the foundation target without claiming any
LM009-G/H static feature semantics. **LM009-G is READY** to own diagnostics,
symbols and definition behavior.

## LM009-G1 parser-derived static diagnostics

Status: **CLOSED**

G1 consumes the published F1/F2/F3/F4 foundation without selecting new Protos
semantics or workspace/module identity rules.

Published boundary:

- each `didOpen` / full `didChange` snapshot is parsed through the existing
  `ProtosStaticAnalysisSession` and real `ProtosParser`;
- a real `ProtosStaticParseResult.Failed` becomes one LSP `Error` diagnostic with
  the parser message and exact existing `SourceSpan`;
- source offsets are mapped as UTF-16, matching both the parser's Java-string
  offset model and the LSP default position encoding, with CRLF treated as one
  logical line break;
- successful reparsing publishes an empty diagnostic set for that document and
  close clears diagnostics without inventing another document version;
- published open/change diagnostics carry the exact LSP document version, and a
  result already stale at the F2 snapshot freshness check is not published;
- no background worker, Truffle Context, guest execution, editor-side parser,
  filesystem-path inference or module/package authority is added.

G1 intentionally does not define document/workspace symbol policy or definition
identity. `LM009-G` remains **IN_PROGRESS** and G2 owns the next document-symbol
audit/implementation boundary. If G2 exposes symbol-ownership or naming semantics
that are not already determined by the real source model, it stops at the normal
Dxxx/PLATxxx gate.

## D079 document-symbol projection ratification

Status: **RATIFIED — Candidate A′ selected; LM009-G2 READY**

D079 / GitHub #364 is durably ratified after explicit project-owner approval on
2026-09-11 and the required expanded comparative review.

The selected G2 contract is intentionally slot-centric:

- every explicit named `SurfaceSlotCreation` is one document symbol;
- bare `name: value` and member-target `receiver.name: value` both expose the
  final slot name;
- `=` assignment, Closure parameters and anonymous expressions do not create
  baseline document symbols;
- every slot uses LSP `SymbolKind.Property` strictly as a presentation label,
  not as a Protos semantic Property category;
- a Closure-valued slot remains the same slot symbol and is not recategorized as
  `Function` or `Method` from its value shape;
- children represent only exact syntactic nesting of slot creations inside the
  parent slot value subtree; and
- hierarchy never means ownership, delegation, lookup, receiver/runtime identity
  or module/package ownership.

The durable decision rationale, candidate comparison, GITHUB010 scorecard,
expanded prior-art review and future/regret analysis are recorded in
[`../../decisions/tooling/D079_DOCUMENT_SYMBOL_PROJECTION_FOR_UNIFORM_PROTOS_SLOTS.md`](../../decisions/tooling/D079_DOCUMENT_SYMBOL_PROJECTION_FOR_UNIFORM_PROTOS_SLOTS.md).

This ratification releases **LM009-G2** for bounded document-symbol
implementation over the existing parser/source-snapshot authorities. It does not
implement G2, select G3 workspace-index policy, select G4 definition identity, or
change any LM009-H semantics.

## LM009-G2 parser-derived hierarchical document symbols

Status: **CLOSED WHEN THIS SLICE IS PUBLISHED**

G2 consumes D079 Candidate A′ and the published F1/F2/G1 parser/snapshot
foundation without introducing workspace indexing or definition identity.

Published implementation boundary:

- the editor-neutral analysis layer projects every explicit named
  `SurfaceSlotCreation` from the real `Surface*` AST into a protocol-neutral
  `ProtosDocumentSymbol` model;
- bare and member-target creations use the final slot name, while `=` assignment,
  Closure parameters and anonymous expressions never become symbols themselves;
- the extractor traverses every current `SurfaceExpression` containment position,
  including assignment subexpressions, call arguments, object parents/items and
  Closure parameter defaults/body, so a surrounding expression form cannot hide
  a nested explicit slot creation;
- a parent symbol receives only creations found inside its value subtree; target
  receiver traversal remains at the surrounding source level, preserving D079's
  source-containment-only hierarchy;
- the LSP edge maps every projected slot uniformly to `SymbolKind.Property`, full
  creation range and exact final-name selection range; Closure-valued slots are
  not reclassified as Function/Method and duplicate names remain distinct source
  occurrences;
- requests parse the current immutable open-document snapshot and re-check F2
  freshness after extraction; missing/closed documents, current parse failures or
  stale results return no symbol tree rather than stale/guessed symbols;
- hierarchical `DocumentSymbol` is advertised only when the client explicitly
  reports `hierarchicalDocumentSymbolSupport=true`. No flat `SymbolInformation`
  fallback is introduced because that would create a second projection outside
  the ratified D079 hierarchy; and
- no runtime/guest execution, Truffle Context, background worker, filesystem path
  inference, module/package resolution or editor-side Protos parser/model is
  introduced.

Focused evidence covers uniform Property presentation, Closure-valued slots,
value-subtree nesting, exact member-name selection, duplicate occurrences,
assignment exclusion, UTF-16 positions, parse-failure clearing and capability
gating. Repository-selected publication validation remains the broader executable
merge gate.

`LM009-G` remains **IN_PROGRESS** after G2. Workspace-symbol inclusion/search and
index lifetime remain G3-owned; go-to-definition identity/resolution remains
G4-owned. Neither is selected by this slice, and LM009-H remains excluded.

## D082 workspace-symbol authority ratification

Status: **RATIFIED — Candidate A′ selected; LM009-G3 BLOCKED_BY_PROJECT_BINDING_PREREQUISITE**

D082 / GitHub #367 is durably ratified after explicit project-owner approval on
2026-09-11 and the expanded cross-language/tool audit. The selected architecture is
**Canonical Project Binding + partitioned incremental workspace index + exact
live-document overlay**.

The durable authority boundary is:

- an editor/LSP workspace folder is coordination input, not Protos project identity;
- project membership and complete workspace source inventory come from a canonical
  editor-neutral Protos package/project authority;
- each exact project binding owns an independent incremental symbol-index domain;
- exact open-document snapshots override current source content only after canonical
  mapping to a bound source and never create module identity themselves;
- `workspace/symbol` may aggregate across exact project domains at the request boundary;
- baseline search covers mutable/workspace package source only, while dependencies,
  `std:`, persistence and remote indexing remain future optional layers; and
- D079 remains authoritative for the indexed declaration unit and LSP `Property`
  presentation.

Ratification deliberately does **not** release G3 implementation. The repository still
lacks an editor-neutral canonical provider for exact `ProjectBinding + source inventory`.
LM009-G3 therefore remains blocked until that prerequisite is separately designed and
published. G3 must not substitute raw filesystem scanning, open-document-only workspace
symbols, per-query Package Tool guest execution or a global dependency/stdlib index.

The durable D082 rationale, expanded prior-art survey, candidate comparison, mandatory
GITHUB010 scorecard, future/scalability stress analysis and explicit owner approval are
recorded in
[`../../decisions/tooling/D082_WORKSPACE_SYMBOL_PROJECT_SOURCE_SET_AND_INDEX_AUTHORITY.md`](../../decisions/tooling/D082_WORKSPACE_SYMBOL_PROJECT_SOURCE_SET_AND_INDEX_AUTHORITY.md).

LM009-G4 definition identity/resolution remains unstarted and independently decision-
gated. LM009-H remains excluded.

## D085 static canonical Project Binding provider ratification

Status: **RATIFIED — Candidate F′ selected; LM009-G3 BLOCKED_BY_PROJECT_BINDING_IMPLEMENTATION**

D085 / GitHub #370 closes the tooling-authority decision exposed after D082. The
selected prerequisite architecture is **Package-owned minimal canonical
ProjectBinding projection + bounded canonical source inventory + validated
candidate-root acquisition through a replaceable editor-neutral provider**.

The Package Tool/package subsystem remains the sole package/project semantic
authority. Static tooling consumes a minimal canonical projection sufficient to
bind exact project/package roots and freshness identity; it does not reimplement
manifest/lock/workspace semantics. A workspace/editor path may be offered only as
a candidate location and becomes a ProjectBinding only after exact canonical
validation at that location. No parent/child project search, nearest-manifest
inference, recursive project discovery or open-document project creation is
introduced.

The complete mutable source set is intentionally not frozen into package metadata.
After canonical package roots are bound, an editor-neutral provider may maintain a
bounded live inventory only inside those roots. A `.protos` source is admitted only
when its package-relative path makes an exact canonical round trip with the existing
logical-module -> confined source mapping, including exact-case/confinement/collision
rules. Ordinary source add/remove/content changes may therefore update the inventory
without regenerating package metadata, while manifest/lock/workspace/package-identity
changes invalidate the binding and require canonical reacquisition.

The provider boundary is replaceable. A future Package Tool daemon, BSP-like build
provider, remote project service or per-toolchain process topology may provide the same
binding without changing D082/G3 semantics. Baseline D085 does not require any of those
institutions and ordinary runtime execution pays no cost when static tooling is absent.

D085 publication releases the **bounded ProjectBinding prerequisite implementation**.
It does not itself implement that prerequisite and does not implement G3 workspace
symbols. LM009-G3 remains blocked until the provider/projection/source-inventory
foundation is published. After that prerequisite is green, G3 may mechanically consume
it under D082 without reopening project authority.

D085 does not decide G4 definition identity, workspace-symbol ranking/result caps,
dependency/stdlib index scope, persistent/remote index storage, generated/virtual
sources, LM009-H semantics, a public BSP protocol, or multi-toolchain process splitting.

## LM009-G3P P1 canonical bounded source inventory

Status: **CLOSED WHEN THIS SLICE IS PUBLISHED**
Coordination: GitHub #373
Authority: D082 Candidate A′ + D085 Candidate F′

P1 implements only the current-source inventory half of the ratified ProjectBinding
prerequisite. It does not create a project, package or source-set authority.

Published implementation boundary:

- `ProtosWorkspacePackageSourceInventory` accepts only an already-bound
  `ProtosWorkspacePackageDirectoryIndex`; it has no workspace-folder, manifest,
  lockfile or project-discovery input;
- enumeration begins separately from each package root already authorized by the
  detached canonical workspace package plan;
- candidate files are admitted only when they are ordinary `.protos` sources whose
  package-relative spelling forms a valid existing `ProtosPackageRuntimeNames`
  logical module and whose exact logical-module -> source round trip succeeds through
  `ProtosWorkspacePackageSourceLookup`;
- the existing source lookup therefore remains the authority for exact spelling,
  ASCII case-fold ambiguity, portable runtime names, symlink real-target confinement
  and final regular-file validation; P1 does not reimplement those rules;
- entries resolving outside an authorized package root are not admitted, while a
  case-fold ambiguity or other failure of an otherwise-valid exact module round trip
  fails the inventory snapshot closed;
- results are immutable and deterministically ordered by PackageId then logical
  module, suitable for later ProjectBinding snapshot construction without making
  ordering a package semantic; and
- no dependency/Standard Library inventory, open-document overlay, workspace-symbol
  index/query, definition behavior, guest/Truffle execution, background daemon,
  persistent projection/sidecar or editor-specific package model is introduced.

Focused evidence covers canonical nested modules, rejection of invalid/non-module
entries, immutable deterministic output, already-bound workspace member package
identity, ASCII case-fold ambiguity and confinement against escaping symlink sources.

`LM009-G3` remains **BLOCKED_BY_PROJECT_BINDING_IMPLEMENTATION**. P2/P3/P4 under
GitHub #373 still own the inert ProjectBinding/projection carrier, exact candidate-root
provider/acquisition integration and prerequisite closure. D085's explicitly deferred
projection persistence/production mechanism is not selected by P1.

## LM009-G3P P2 inert ProjectBinding carriers

Status: **CLOSED WHEN THIS SLICE IS PUBLISHED**
Coordination: GitHub #373
Authority: D082 Candidate A′ + D085 Candidate F′

P2 establishes only the editor-neutral immutable data boundary required between the
canonical package authority / future provider and the D082 static-analysis index. It
does not choose how that data is produced, persisted, transported or acquired.

Published implementation boundary:

- `ProtosProjectBindingProjection` is an inert package-authority projection carrying
  one normalized absolute canonical project-root witness, exact root PackageId,
  exact workspace PackageId/location membership, one internal projection generation
  and one opaque non-empty freshness witness;
- the projection interprets no manifest, lockfile or editor workspace and does not
  define the content or algorithm of the freshness witness;
- `ProtosProjectBinding` combines one projection with exact physical package-root
  bindings and already-validated current source identities, defensively freezing all
  collection inputs;
- the carrier rejects incomplete or foreign package-root bindings, root-witness
  mismatch, duplicate PackageId/location/source identity, source packages outside
  the projection and source paths outside their selected package root;
- P2 deliberately does not enumerate source files itself; P1 remains the bounded
  current-source inventory authority over already-authorized package roots;
- P2 adds no project discovery, candidate-root validation/acquisition, sidecar,
  serialization format, persistent filename, manifest/lock parser, Package Tool
  subprocess/daemon, guest/Truffle execution, workspace-symbol index/query,
  open-document overlay, G4 definition behavior or LM009-H feature.

The generation field versions only this in-memory carrier shape. The freshness witness
is opaque package-authority data: P2 neither computes nor interprets it. D085's explicit
choice of the first projection-production/persistence mechanism therefore remains
unselected and belongs to P3 only if it can be implemented without crossing a new
public/durable boundary.

`LM009-G3` remains **BLOCKED_BY_PROJECT_BINDING_IMPLEMENTATION**. P3/P4 under GitHub
#373 still own exact candidate-root provider/acquisition integration, freshness
invalidation evidence, multi-root/duplicate-binding behavior and prerequisite closure.

## LM009-G3P P3A canonical ProjectBinding projection producer

Status: **CLOSED WHEN THIS SLICE IS PUBLISHED**
Coordination: GitHub #413 (parent #373)
Authority: D082 Candidate A′ + D085 Candidate F′ + D089 Candidate A″

P3A implements only the Package-Tool-owned producer side of the D089 generation-1
`protos.project` contract. It does not yet let the static language server acquire a
binding.

Published implementation boundary:

- `self:ProjectDocument` validates and canonically renders generation-1
  `protos.project`, including the fixed semantic/mechanical witness methods,
  lowercase SHA-256 spellings, canonical qstrings, unique PackageIds/locations and
  deterministic workspace-member ordering;
- `self:ProjectMetadata` computes exactly the ratified
  `protos-project-metadata-v1` byte framing over root `protos.toml`, ordered member
  manifests and root `protos.lock`, excluding ordinary `.protos` source bytes;
- `self:ProjectFile` derives the portable projection only from canonical Package
  Tool `ResolutionRoot` + `ResolutionInput` + canonical lock authority, rejects a
  stale or structurally mismatched lock, and provides the bounded
  `publishCurrent(projectTreeFilesystem, projectMetadataFilesystem)` primitive
  through the existing atomic metadata-publication substrate;
- no absolute host path or complete source inventory is serialized;
- no new public Package Tool command spelling, automatic refresh, project discovery,
  LSP manifest/lock interpretation, guest execution in static requests, workspace
  symbol index/query, G4 definition behavior or LM009-H behavior is introduced.

Focused Package Tool conformance covers an exact root-only projection vector,
canonical member ordering, the binary-safe mechanical metadata digest with ordinary
source bytes excluded, and stale semantic-lock rejection.

`LM009-G3` remains **BLOCKED_BY_PROJECT_BINDING_IMPLEMENTATION**. P3B still owns the
host/editor-neutral generation-1 reader, exact candidate-root validation and
ProjectBinding-provider materialization; P4 then owns multi-root/freshness/no-guest
closure evidence.
\
\n## D089 ProjectBinding projection publication/acquisition ratification

Status: **RATIFIED — Candidate A″ selected**
Coordination: GitHub #376
Consumer: LM009-G3P P3 / GitHub #373
Authority: D082 Candidate A′ + D085 Candidate F′ + D089 Candidate A″

D089 closes the durable production/acquisition mechanism deliberately deferred by
D085. The selected baseline is a Package-Tool-generated, repository-carried,
editor-neutral `protos.project` artifact with **dual freshness**:

- Package Tool remains the sole owner of semantic project/package authority and emits
  the existing `protos-resolution-input-v1` SHA-256 identity into the projection;
- a separate `protos-project-metadata-v1` SHA-256 covers exact bytes of root
  `protos.toml`, projected member `protos.toml` files and `protos.lock` using the
  versioned framing defined by D089;
- the static provider may recompute only that mechanical byte witness. It does not
  parse TOML/lock semantics or recompute semantic resolution input;
- the persisted artifact contains no absolute machine path and no source-file
  inventory. The exact validated candidate supplies the P2 physical root, and P1 owns
  the current bounded source inventory inside authorized package roots;
- missing, stale, malformed or unsupported project metadata fails that binding closed;
  there is no baseline automatic Package Tool/guest subprocess repair or filesystem
  project discovery; and
- individual atomic lock/project publication is sufficient because the mechanical
  witness includes the exact final `protos.lock` bytes; interrupted publication is
  detectably stale rather than silently inconsistent.

D089 ratification releases **LM009-G3P P3** to implement only this approved
`protos.project` producer/reader/provider and exact candidate-root path. P3 remains
prohibited from introducing automatic refresh, project-discovery heuristics, a second
manifest/lock semantic authority or workspace-symbol indexing.

`LM009-G3` remains **BLOCKED_BY_PROJECT_BINDING_IMPLEMENTATION** until P3/P4 publish and
GitHub #373 closes. P4 still owns multi-root isolation, duplicate binding rejection,
freshness invalidation and static no-guest closure evidence.

## D102 nested workspace package source ownership ratification

Status: **RATIFIED — Candidate A′ selected; P3B BLOCKED_BY_D102_IMPLEMENTATION**

Coordination: GitHub #421; dependent P3 / #413

Authority: D082 Candidate A′ + D085 Candidate F′ + D089 Candidate A″ + D102 Candidate A′

P3B focal validation exposed that the existing shared source authority could assign one
physical member source both `member:Api` and `root:libs/member/Api` when an authorized
workspace package root is nested inside another authorized package root. The same source
lookup is consumed by runtime workspace module resolution, so this was not an LM009-only
test issue.

D102 ratifies canonical non-overlapping package source domains:

- a strictly-descendant authorized package root is a hard source/module boundary for
  every ancestor package;
- the unique most-specific authorized package root owns a contained physical source;
- distinct PackageIds may not bind to the same canonical physical package root;
- source inventory, exact source lookup, runtime module resolution and static tooling
  must apply the same boundary;
- package boundaries come only from canonical package/project authority and never from
  nearest-manifest/filesystem discovery; and
- physical containment cannot make another package's source reachable through parent
  `self:` lookup; ordinary dependency/export authority remains the cross-package route.

The comparative audit covered Bazel, Buck2, Go modules/workspaces, SwiftPM, Cargo,
Gradle, Maven, sbt, Mill, Node/npm/Yarn/pnpm, BSP, Pants, MSBuild/.NET, Python/uv and
CMake. Candidate A′ scored 5/5 on the project-owner focus dimensions of future-option
resilience, scalability and Protos alignment.

Ratification alone does not resume P3B. The next work must implement D102 once in the
shared package directory/source authorities so runtime and tooling inherit the same
ownership rule. Until that implementation is published, `LM009-G3P P3B` / #413 remains
**BLOCKED_BY_D102_IMPLEMENTATION** and G3 remains
**BLOCKED_BY_PROJECT_BINDING_IMPLEMENTATION**.

## D102 canonical non-overlapping package-domain implementation

Status: **CLOSED WHEN THIS SLICE IS PUBLISHED; LM009-G3P P3B READY**

Coordination: GitHub #413; decision GitHub #421

Authority: D102 Candidate A′ — canonical non-overlapping package source domains

The shared workspace package authorities now realize D102 before P3B resumes:

- physical directory binding rejects distinct PackageIds that resolve to the same
  canonical physical package root;
- the bound finite package-root set derives descendant authorized roots as hard source
  boundaries without scanning for manifests, lockfiles or project markers;
- exact source lookup rejects both direct traversal into a descendant package root and
  a symlink whose canonical target belongs to that descendant package;
- P1 current-source inventory prunes descendant package-root subtrees and excludes
  canonical child-owned symlink targets, so one physical source is emitted under only
  its most-specific authorized PackageId;
- `ProtosWorkspacePackageModuleResolver` continues to consume the same
  `ProtosWorkspacePackageSourceLookup`, so runtime entry/`self:` resolution inherits the
  identical boundary rather than receiving an LM009-specific rule; and
- no `protos.project` format, Package Tool semantic authority, workspace-symbol index,
  filesystem project discovery, source-set/glob policy or language specification
  behavior is added.

Focused evidence covers duplicate canonical-root rejection, nested member ownership,
parent direct lookup rejection, parent symlink-alias rejection, inventory de-duplication
and runtime resolver rejection of the same parent alias.

This closes only the implementation prerequisite exposed by D102. **LM009-G3P P3B is
READY to resume** against the shared authority. P3/#413 still owns the previously
declared integrated validation debt and P4 remains pending.
