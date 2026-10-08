# Language server

`protos language-server` starts a Language Server Protocol (LSP) service that
speaks standard JSON-RPC over stdin/stdout. While it runs, stdout carries only
protocol messages. It takes no arguments.

The server is **static**: it parses and analyzes the documents your editor has
open, but it never executes Protos code, never runs the Package Tool, and never
guesses. When it cannot prove an answer it returns an empty result rather than
a plausible-looking one.

The implementation is under
[`src/main/java/com/guillermomolina/protos/lsp/`](../../../src/main/java/com/guillermomolina/protos/lsp/).

## Starting it from an editor

Configure your editor's generic LSP client with:

| Setting | Value |
|---|---|
| Command | `protos language-server` |
| Transport | stdio |
| Language id | `protos` |
| Files | `*.protos` |

The [official Protos VS Code extension](https://github.com/guillermomolina/protos-vscode-extension)
and the Protos Dev Container are the maintained editor setup (see
[Try Protos](../00-try-protos.md)). The server is included in both the Native
and the portable JVM distributions.

## Advertised capabilities

| LSP feature | Method | Scope |
|---|---|---|
| Document sync | `didOpen`, `didChange`, `didClose` (full-document sync) | Any open document |
| Diagnostics | `textDocument/publishDiagnostics` | Any open document |
| Formatting | `textDocument/formatting` | Any open document |
| Document symbols | `textDocument/documentSymbol` | Any open document; advertised only when the client supports hierarchical symbols |
| Workspace symbols | `workspace/symbol` | Sources of a bound project (see below) |
| Go to definition | `textDocument/definition` | Open sources of a bound project |
| Find references | `textDocument/references` | Open sources of a bound project |
| Hover | `textDocument/hover` | Open sources of a bound project |
| Completion | `textDocument/completion` | Open sources of a bound project |
| Signature help | `textDocument/signatureHelp`, triggers `(` and `,` | Open sources of a bound project |

Positions use LSP's default zero-based lines and UTF-16 character offsets,
including for non-BMP text and CRLF line endings.

### Not provided

The server does **not** advertise code actions or quick fixes, rename, range
or on-type formatting, semantic tokens, inlay hints, code lenses, folding
ranges, call or type hierarchy, or save notifications. Lint warnings therefore
have no automatic fix.

## Diagnostics

On every open or change, the server parses the exact current buffer and
publishes one diagnostic set:

- a parser **error** when the buffer does not parse; or
- the lint **warnings** of [`protos lint`](cli.md#protos-lint)
  (`protos/unreachable-after-nonlocal-return`,
  `protos/always-different-fresh-object`) when it does.

There are no type, import-resolution, or runtime diagnostics: the server does
not resolve modules or run code. A result computed for an outdated buffer is
discarded.

## Formatting

Formatting uses the same canonical formatter as
[`protos format`](cli.md#protos-format) on the current unsaved buffer. The
editor's tab-size and whitespace options are accepted but **ignored**: the
canonical style is fixed. The answer is no edit when the buffer is already
canonical, or one whole-document edit. Invalid source gets no edit.

## Navigation needs a bound project

Workspace symbols, definition, references, hover, completion, and signature
help work only for documents that belong to a **Protos project** whose root is
exactly one of the editor's workspace folders. The server checks that root for
`protos.toml`, `protos.lock`, and the generated `protos.project` metadata, and
it reads nothing outside it. It does not search parent or child directories.

For a loose file, a file outside the workspace folder, or a folder without that
project metadata, these requests return empty results. Diagnostics, formatting,
and document symbols still work.

**Current limitation:** no public `protos` command writes `protos.lock` or
`protos.project` yet (see [Package Tool](package-tool.md)). Until the Package
Tool publishes that operation, navigation features are available only for
projects that already carry exact, canonical metadata files; for an ordinary
new project expect diagnostics, formatting, and document symbols only.

Within a bound project the analysis is deliberately narrow:

- **Definition and references** resolve Closure parameters and match bindings
  only when the analysis proves the binding, and only within the same
  document. Slots, module members, imports, and other files are not resolved.
- **Hover** shows a "Proven binding" section for a proven Closure parameter
  reference; otherwise only labeled "Syntax" facts (literal kind, Closure
  parameter declaration, Closure head). Hover text is plain text.
- **Completion** offers proven Closure parameters in scope and the reserved
  words `this`, `context`, `true`, `false`, and `null` where the parser accepts
  them. It never offers slots, members, module names, or Standard Library
  names, and every list is complete (`isIncomplete` is `false`).
- **Signature help** works only when the callee is a literal Closure, for
  example `((a, b = 1) => { a + b })(`, showing source-text parameter labels.
  Calls through a name get no signature help.
- **Workspace symbols** search the project's own sources; dependencies and
  `std:` modules are excluded, and at most 100 results are returned.

## Server support versus editor behavior

The repository's integrated test drives the server with a standard LSP client
over real JSON-RPC and checks every capability in the table above. What a
particular editor shows can still differ:

- an editor may not request a feature it does not support, or may need its own
  configuration to send formatting requests;
- document symbols appear only when the editor declares hierarchical symbol
  support;
- an editor that opens a single file without a workspace folder gets no
  navigation features, because no project can be bound.

This repository does not test any particular editor. The official VS Code
extension is maintained in its own repository, and other editors are untested.
