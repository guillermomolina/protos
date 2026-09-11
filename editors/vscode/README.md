# Protos VS Code integration

This directory is the approved current home for the Protos reference VS Code
integration.

The extension is intentionally thin and declarative at this stage. Protos
language semantics remain owned by `../../spec/PROTOS_GRAMMAR.md` and the real
parser/runtime/resolver authorities; the VS Code surface must not become a
second implementation of the language.

Current LM009-B assets:

- `package.json` — declarative VS Code extension manifest;
- `language-configuration.json` — comments and structural delimiter behavior;
- `syntaxes/protos.tmLanguage.json` — non-normative TextMate grammar;
- `test/fixtures/lexical.protos` — representative valid Protos source;
- `test/validate_grammar.py` — lexical-asset structural guard; and
- `test/validate_extension.py` — manifest/language-association/configuration
  structural guard.

The approved manifest identity and compatibility floor are:

```text
extension id:    guillermomolina.protos
extension name:  protos
publisher:       guillermomolina
extension ver.:  0.1.0
engines.vscode:  ^1.104.0
language id:     protos
file extension:  .protos
TextMate scope:  source.protos
```

The extension version is intentionally independent from the Protos Maven/runtime
implementation version. The VS Code support floor is not raised merely because a
new Stable version exists; a future increase requires a concrete editor API or
dependency need introduced after VS Code 1.104.

## Local validation

From `editors/vscode/`:

```sh
npm install --no-package-lock
python3 test/validate_grammar.py
python3 test/validate_extension.py
node test/run_current_file.test.js
node test/debug_integration.test.js
node test/language_server_integration.test.js
```

The static language-service client uses Microsoft's `vscode-languageclient`
package. Node/npm remains an editor-development/packaging concern only; ordinary
Protos Maven/runtime development and execution do not depend on Node/npm.
`--no-package-lock` keeps this pre-LM009-I development workflow from creating a
new distribution lockfile policy before editor packaging is closed.

## S1 live VS Code check

The manifest can be loaded directly as an extension-development path without
publishing anything to Marketplace:

```sh
cd editors/vscode
code --new-window --extensionDevelopmentPath="$PWD"
```

In that VS Code window, open `test/fixtures/lexical.protos` and verify:

1. the language mode is **Protos**;
2. `.protos` receives the `source.protos` TextMate grammar;
3. `//` and `/* ... */` are recognized as the configured comment forms; and
4. `()`, `[]`, and `{}` use the structural bracket configuration.

LM009-B S1 was completed by the project owner on 2026-09-10 in a real VS Code
Extension Development Host: `.protos` was recognized as **Protos**, highlighting
was active, the configured line/block comment actions worked, and structural
delimiter auto-closing worked. The steps above remain the reproducible live
editor check; the repository-side validators complement rather than replace it.

No VSIX/Marketplace release is performed by LM009-B, and no Run action, DAP/LSP
client, runtime-discovery policy, formatter, parser, or TypeScript semantic model
is introduced here.

## Run current Protos file (LM009-C)

`Protos: Run Current File` executes the active file through the real external
Protos launcher. The extension does not evaluate Protos itself and does not
reconstruct Java/JAR internals.

Runtime resolution is intentionally small:

```text
protos.runtime.executable
```

is a machine-scoped setting whose default is `protos`, resolved through the
extension host's ordinary `PATH`. Configure it in User/Remote settings when the
launcher is installed elsewhere, for example:

```json
{
  "protos.runtime.executable": "/absolute/path/to/protos"
}
```

The configured value is passed directly to VS Code `ProcessExecution`; it is
not interpolated into a shell command. The source filename is a separate process
argument, so spaces in launcher/source paths do not create shell-quoting policy.

The Run action requires:

- a trusted VS Code workspace/window;
- an active `protos` document backed by either the local `file:` filesystem or
  VS Code Remote's `vscode-remote:` workspace filesystem;
- a successful save first when the document is dirty.

The executable integration is explicitly a VS Code `workspace` extension, so it
runs where the workspace and configured Protos launcher live. Local `file:`
resources use ordinary `Uri.fsPath`. For `vscode-remote:`, the decoded URI path
is first converted to a `file:` URI in that workspace extension host and only
then converted to the host-native filesystem path. The extension never treats a
remote URI's `fsPath` as if it belonged to the UI machine.

Other schemes, including virtual-workspace resources such as `vscode-vfs:`, are
not executable by Run Current File. Safe language association and highlighting
remain available; LM009-C simply does not fabricate a filesystem path that an
external launcher cannot consume.

Execution uses a VS Code Task with a dedicated, revealed terminal. Its working
directory is always the current file's parent directory. LM009-C does not add
application arguments, workspace/package-module execution, Java/JAR discovery,
or debugger behavior.

In Restricted Mode, Protos syntax highlighting remains available but execution
is blocked. `protos.runtime.executable` is also listed as a restricted
configuration, and the command implementation independently checks
`workspace.isTrusted` before any process can be started.

### S2 live VS Code check

After the LM009-C run-wiring tranche is published, load the extension from
`editors/vscode/` in an Extension Development Host or install a local test VSIX,
ensure a real Protos launcher is available through `PATH` or
`protos.runtime.executable`, trust the test window, and open an ordinary
standalone `.protos` file. Local `file:` and VS Code Remote `vscode-remote:`
resources are valid execution surfaces when the launcher exists in the same
workspace extension-host environment.

Invoke:

```text
Protos: Run Current File
```

The S2 live check passes when the dedicated Task terminal shows the real Protos
program's stdout/stderr and ordinary task/process completion outcome, with the
source executed from its parent directory.

LM009-C S2 was completed by the project owner on 2026-09-10 in a real VS Code
Dev Container with the repaired workspace extension. The installed
`guillermomolina.protos` 0.1.0 extension recognized `.protos`, exposed
`Protos: Run Current File` for the `vscode-remote:` resource, and the command
successfully executed the current source through the real external Protos
launcher. LM009-C is therefore CLOSED; packaging/Marketplace remains LM009-I.

The current supported end-user distribution is POSIX/JVM, but this editor
contract is not POSIX-specific: it consumes a directly executable Protos launcher.
A future Windows/native launcher can satisfy the same contract without changing
the editor command semantics.

## Debug Protos in VS Code (LM009-E)

LM009-E consumes the public debugger launcher closed by LM009-D; the extension
does not construct GraalVM options or implement a second debug adapter.

The ordinary single-file path is F5 with an active executable Protos document.
A `launch.json` file is not required for that case. The in-memory configuration
is equivalent to:

```json
{
  "type": "protos",
  "request": "launch",
  "name": "Debug Protos File",
  "program": "${file}",
  "args": []
}
```

For repeatable configurations, the same shape may be stored in `launch.json`.
`program` must resolve to an absolute path in the workspace extension host, and
`args` contains ordinary application arguments passed after the source file.

The runtime executable is the same machine-scoped
`protos.runtime.executable` setting used by Run Current File. The extension
starts it directly, without a shell, as:

```text
protos debug <absolute-source-file> [application-args...]
```

The launcher publishes exactly one D060 startup record on stdout:

```text
PROTOS_DEBUG_READY {"version":1,"protocol":"dap","transport":"tcp","host":"127.0.0.1","port":54321}
```

The extension validates that record, including a numeric loopback endpoint, and
returns a VS Code `DebugAdapterServer` descriptor. VS Code then speaks DAP
directly to the real GraalVM adapter. The extension does not choose a port,
probe sockets, parse raw GraalVM readiness, relay DAP messages, or introduce a
readiness file.

Launcher diagnostics are shown through the **Protos Debug** output channel.
Guest stdout/stderr remains debugger output delivered by DAP. The extension
retains only one launcher child handle per active debug session and has no
global port or session registry.

Debug execution remains disabled in Restricted Mode. As with Run Current File,
the executable extension runs in the VS Code workspace extension host, so local
and Dev Container/Remote sessions use the launcher and filesystem of that
workspace host.

The LM009-E baseline intentionally exposes only `request: "launch"`. Attach,
remote-network listen configuration, stop-on-entry, readiness-file discovery and
a stronger `terminateDebuggee` promise remain outside this slice.

### S3 live VS Code check

Repository-side Node/Python tests validate the orchestration contract but do not
close LM009-E. After the E1 wiring is published, S3 must still be exercised in a
real VS Code extension host against the real external Protos launcher.

Set a source breakpoint in an ordinary `.protos` file and press F5. S3 requires
live evidence for: breakpoint stop location, threads, stack, activation-local
scope, representative scalar/indexed values, step, continue, guest output and
clean normal termination. The project owner live check is the acceptance
surface; Marketplace publication remains LM009-I.

## Language server foundation (LM009-F4)

LM009-F4 consumes ratified PLAT024 and D070. The reference extension stays a
thin standard-LSP client and starts the language server through the same selected
toolchain executable already used by Run and Debug:

```text
protos.runtime.executable
        +
["language-server"]
```

The resulting public command is:

```text
protos language-server
```

The extension uses `vscode-languageclient` and passes the executable plus argv
directly with `shell: false`. It does not know the Java main class, JAR/classpath
layout, or whether a future implementation behind the launcher is JVM, Native
Image, a sibling binary, or self-hosted Protos.

The client selects Protos documents by language id only. Their LSP URI and exact
editor text are sent through standard document synchronization to the F3 server;
the extension does not infer package, module, workspace or filesystem identity
from those URIs. Those authorities remain server/toolchain responsibilities for
later LM009-G/H work.

The language server is client-session-owned and runs in the workspace extension
host. Local, Remote SSH and Dev Container sessions therefore use the same
toolchain namespace already selected for Run/Debug. Restricted Mode starts no
language-server process; after Workspace Trust is granted, the same controller
may start it normally.

F4 adds no diagnostics, symbols, definition, references, completion, hover or
signature-help semantics. Those remain LM009-G/H.

### S4 foundation live VS Code check

Repository tests prove the CLI/LSP framing and thin client launch contract, but
LM009-F closure still requires one real VS Code host check.

After building the current Protos launcher and running
`npm install --no-package-lock` in `editors/vscode/`, open the extension through
an Extension Development Host (or an equivalent local test installation), point
`protos.runtime.executable` at that exact current launcher and open a `.protos`
document.

The foundation live check is:

1. opening the Protos document starts exactly one matching
   `protos language-server` child in the workspace extension host;
2. editing the document leaves the server healthy while normal LSP
   open/change/close synchronization is exercised;
3. no second server-path setting or Java/JAR command is required;
4. closing/reloading the Extension Development Host stops the client-owned
   server process cleanly; and
5. no static language feature is claimed yet merely because the foundation is
   alive.

Once this live composition is confirmed, LM009-F can close and LM009-G can own
the first visible static diagnostics/symbol/definition features.
