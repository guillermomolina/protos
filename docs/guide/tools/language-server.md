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
| Go to definition | `textDocument/definition` | Any open Protos document, for complete same-document proven origins only |
| Find references | `textDocument/references` | Open sources of a bound project |
| Hover | `textDocument/hover` | Any open Protos document |
| Completion | `textDocument/completion` | Any open Protos document |
| Signature help | `textDocument/signatureHelp`, triggers `(` and `,` | Any open Protos document |

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

## Document-local intelligence

Hover, completion, signature help, and go to definition answer for **any open
Protos document**,
including a loose file or a file outside every project. They use only the
exact current (possibly unsaved) buffer and never read the file on disk, other
files, or project metadata. A closed document gets no answer.

The analysis is deliberately narrow and identical inside and outside a
project:

- **Hover** shows a "Proven binding" section for a proven Closure parameter
  reference; otherwise only labeled "Syntax" facts (literal kind, Closure
  parameter declaration, Closure head). Hover text is plain text.
- **Completion** offers proven Closure parameters in scope and the reserved
  words `this`, `context`, `true`, `false`, and `null` where the parser accepts
  them. It never offers slots, members, module names, or Standard Library
  names, and every list is complete (`isIncomplete` is `false`).
- **Signature help** works only when the callee is a literal Closure, for
  example `((a, b: 1) => { a + b })(`, showing source-text parameter labels.
  Calls through a name or a receiver get no signature help.
- **Go to definition** returns a location only when every origin is proven
  inside the same open snapshot, and the location always uses the exact URI of
  the requested document. The proof reads neither the file on disk nor any
  other file. A source without a complete proof (including source that does
  not parse) returns an empty result.

Opening a document never makes it part of a project: these answers create no
project binding and grant no cross-source navigation authority.

## Navigation needs a bound project

**Local definitions** do not need a bound project: go to definition answers
whenever all origins are proven inside the same open snapshot (see
[Document-local intelligence](#document-local-intelligence)).

**References and workspace symbols** work only for documents that
belong to a **Protos project** whose root is
exactly one of the editor's workspace folders. The server checks that root for
`package.toml`, `protos.lock`, and the generated `protos.project` metadata, and
it reads nothing outside it. Exact legacy `protos.toml` entries in the root or declared member are rejected, including symlinks and coexistence. It does not search parent or child directories.

For a loose file, a file outside the workspace folder, or a folder without that
project metadata, references and workspace symbols return empty results.
Diagnostics, formatting, document symbols, hover, completion, signature help,
and same-document go to definition still work.

**Current limitation:** no public `protos` command writes `protos.lock` or
`protos.project` yet (see [Package Tool](package-tool.md)). Until the Package
Tool publishes that operation, references and workspace symbols are available
only for projects that already carry exact, canonical metadata files; for an
ordinary new project expect diagnostics, formatting, document symbols, hover,
completion, signature help, and same-document go to definition only.

The analysis behind definition and references is deliberately narrow. The
current generation mainly proves:

- Closure parameters;
- certain parameters of the contextual `for` form;
- references whose origin remains unambiguous before any opaque effect (such
  as an invocation) intervenes.

Go to definition additionally proves, inside the same activation and only
until an opaque effect intervenes:

- local slot creations: after `mensaje: "Hola"` (or a grouped creation such as
  `(a, b): pair`), a later read of `mensaje` goes to the creation. A read before
  the creation, or from another Closure, is not resolved;
- Closures stored in such slots, including the receiver of a later call such
  as `saludar("Hola")`;
- the target of an assignment such as `count = 2` when the slot it selects is
  proven; the assignment keeps the original creation as the origin;
- own members of a bare object literal stored in a proven local slot, for
  example `obj.dato` after `obj: { dato: 1 }`, and methods stored as values of
  those members, such as `obj.saludar()`. The object must have no parent
  expression and no composition, and its members must be built from literals,
  Closure literals, or such nested objects.

The server never infers a project and never runs Protos code to answer. It
does not resolve inherited members, members reached through dynamic
delegation, objects that may have been changed through an alias or by an
invocation, modules, imports, or other files. Whenever a proof is incomplete,
the answer is empty. References stay within the same document.

Within a bound project:

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
  workspace symbols or references, because no project can be bound; hover,
  completion, signature help, and provable same-document go to definition
  still work.

This repository does not test any particular editor. The official VS Code
extension is maintained in its own repository, and other editors are untested.
