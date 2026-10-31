# The `protos` command line

One executable, `protos`, runs programs and starts every developer tool. This
page lists the commands the current driver implements, their exit codes, and
how the Native and portable JVM distributions differ. `protos --help` prints
the same command summary.

The dispatch code is
[`ProtosCli.java`](../../../src/main/java/com/guillermomolina/protos/cli/ProtosCli.java).

## Commands at a glance

| Command | What it does | Guide |
|---|---|---|
| `protos [--inspect] <file> [args...]` | Runs one source file | below |
| `protos [--inspect] -e <source> [args...]` | Runs one source String | below |
| `protos [--inspect]` | Starts the interactive REPL | below |
| `protos run <entry> [args...]` | Runs a root-package logical entry of the project in the current directory | below |
| `protos debug <file> [args...]` | Runs one file under the Debug Adapter Protocol | below |
| `protos format [<file>]` | Prints the canonical formatting of one document | below |
| `protos lint [--output text\|json] [--fail-on-warning] [<file>]` | Reports parser errors and lint warnings without running code | below |
| `protos language-server` | Starts the Language Server Protocol service on stdin/stdout | [Language server](language-server.md) |
| `protos test [options]` | Runs the bundled Test Tool | [Test Tool](test-tool.md) |
| `protos package [args...]` | Runs the bundled Package Tool | [Package Tool](package-tool.md) |
| `protos -h`, `protos --help` | Prints usage | |
| `protos -v`, `protos --version` | Prints `Protos <version>` | |

`--foreign-provider-path <paths>` may precede `<file>`, `-e`, or the REPL; see
[Foreign interoperability](../interop/README.md). `--inspect` may precede the
same three forms; see [Inspection](#inspection) below.

## Running programs

```sh
protos hello.protos first second
protos -e 'print(process.args().size())' a b c
```

- Everything after the file or `-e` source is an application argument,
  available through `process.args()`. The file or source itself is not
  included.
- Standard input, output, and error are byte streams associated with UTF-8.
- File and `-e` execution print only what the program writes; the REPL also
  shows each evaluation result.
- Imports such as `import("./util.protos")` resolve relative to the importing
  file, inside the entry file's directory tree. There is no current-directory
  fallback and no implicit `.protos` extension.

The interactive REPL supports line editing, history and TAB completion.
`:help` lists the REPL commands; `:quit`, `:exit` or Ctrl-D exits.
Ctrl-C cancels the current input.

### TAB completion in the REPL

On a terminal, press TAB to complete:

- REPL commands `:help`, `:quit` and `:exit`, when entering a command.
- Currently present lexical binding names from the persistent REPL context,
  including names visible in its actual lexical-parent chain.
- Additional identifiers and language syntax only where the Protos parser and
  static analyzer can establish a valid completion position.

For example, after `sessionVariable: 42`, typing `sess` and pressing TAB
can complete the existing name to `sessionVariable`. Completion also supports
editing inside an identifier and within expressions where the parser proves
the cursor position safe.

Completion reads binding **names**, not their values. It does not execute
guest expressions, inspect dynamic receiver members, import modules, search
files or infer names from previous commands. Removing a binding changes what
the next TAB can suggest; a binding whose value is `null` is still present.

Completion is conservative: unfinished or invalid source may have no
suggestions. An unmatched TAB leaves the input intact. Multiline input is
handled using the pending source when its position can be established safely.

TAB completion is available on the interactive terminal in both JVM and
Native distributions. When the REPL receives piped input instead of a terminal,
it uses its existing stream behavior without terminal completion.

### Inspection

```sh
protos --inspect show.protos
protos --inspect -e 'Inspect: import("std:inspect")'
protos --inspect
protos --foreign-provider-path plugins --inspect show.protos
```

`--inspect` authorizes the [`std:inspect`](../library/inspect.md) module for
this one invocation; without it the import signals an ordinary `Error`. It
takes no value and grants nothing else.

- It must appear before the file, `-e`, or the REPL start, and can be combined
  with `--foreign-provider-path` in either order.
- It may be given only once; repeating it is a usage error (status `2`).
- Before any other command (`run`, `debug`, `package`, `test`, `format`,
  `lint`, `language-server`, `--help`, `--version`, `-h`, `-v`) it is a usage
  error (status `2`): the flag applies only to file, `-e`, or REPL execution.
- Arguments after the file or the `-e` source are application arguments and
  are kept intact in `process.args()`, even one spelled `--inspect`; such an
  argument does not authorize inspection.

### `protos run`

`protos run <entry>` executes an entry of the root package of the Protos
project in the current directory (a directory with a `protos.toml` manifest).
Neither `run` nor `<entry>` appears in `process.args()`. The application gets
no network capability from the CLI.

### `protos debug`

`protos debug <file>` runs one file through the standard Debug Adapter Protocol
debugger. Before guest execution it writes exactly one `PROTOS_DEBUG_READY`
JSON record to standard output; afterwards guest output travels over DAP. It is
intended to be launched by an editor integration rather than by hand.

### Exit status

| Status | Meaning |
|---|---|
| `0` | The program completed |
| `1` | Syntax error, uncaught Error (`Runtime error: ...` on stderr), or unreadable source file |
| `2` | Command-line usage error (`protos: <message>` and `Try 'protos --help'.` on stderr) |
| `70` | Internal error in the toolchain itself |

## `protos format`

```sh
protos format main.protos > main.formatted.protos
cat main.protos | protos format
```

`format` reads one whole UTF-8 document from the file, or from stdin when no
file is given, and writes its canonical form to stdout. It **never modifies
files**, never executes the source, and does not resolve imports. There are no
style options: the canonical style is fixed.

| Status | Meaning |
|---|---|
| `0` | Formatted output written |
| `1` | Invalid source (echoed unchanged on stdout, diagnostic on stderr), or unreadable / non-UTF-8 input |
| `2` | Usage error (for example more than one file) |
| `70` | Internal formatter error |

To format in place, redirect to a temporary file and move it yourself after
checking the status.

## `protos lint`

`protos lint` statically checks one file, or stdin, without executing it:

```sh
protos lint main.protos
protos lint --output json --fail-on-warning main.protos
```

It parses the source once. A parser error is reported alone; on a successful
parse the lint rules run.

### Rules

| Rule | Reports |
|---|---|
| `protos/unreachable-after-nonlocal-return` | Expressions after a `^` non-local return that is a direct child of the same braced Closure body |
| `protos/always-different-fresh-object` | `x === <object literal>`: a freshly created object can never be identical to an earlier value |

Both rules are warnings. There are no other rules, no rule configuration, no
suppression comments, and no automatic fixes.

### Text output

One line per diagnostic, on stdout:

```text
main.protos:3:4-3:9: warning [lint protos/unreachable-after-nonlocal-return]: <message>
main.protos:0:7-0:7: error [parser]: <message>
```

The range is `startLine:startCharacter-endLine:endCharacter`, **zero-based**,
with characters counted in UTF-16 code units (the LSP convention). Stdin is
reported as `<stdin>`.

### JSON output

`--output json` writes one JSON document (schema version 1):

```json
{"schemaVersion":1,"source":"main.protos","status":"valid","diagnostics":[
  {"origin":"lint","code":"protos/always-different-fresh-object","severity":"warning",
   "message":"...","range":{"start":{"line":2,"character":0},"end":{"line":2,"character":12}}}]}
```

(Shown wrapped; the real output is one line.) `status` is `"valid"` or
`"invalid"`. Parser diagnostics have `"origin":"parser"` and `"code":null`.
Diagnostics are ordered by start, end, then rule.

### Exit status

| Status | Meaning |
|---|---|
| `0` | Source parsed; warnings are allowed |
| `1` | Parser error, or any warning with `--fail-on-warning` |
| `2` | Usage error (unknown or repeated option, bad `--output` value, more than one file) |
| `3` | The source could not be read or is not valid UTF-8 |
| `70` | Internal error |

On statuses `2`, `3`, and `70` stdout is empty and the message is on stderr. A
CI gate is simply:

```sh
protos lint --fail-on-warning src/main.protos
```

## Native and portable JVM

| | Native Linux x86_64 | Portable JVM |
|---|---|---|
| Requirements | Linux x86_64, dynamic glibc 2.39 or newer; no JDK | GraalVM 25.4.4.1.1 (JDK 25.0.4.1.1) |
| Guest execution | Interpreter-only; guest JIT disabled (PLAT045) | Optimizing runtime with guest JIT |
| Commands on this page | All available | All available |
| `--foreign-provider-path` | Not available: no dynamic plugin loading | Available |
| `--inspect` | Available | Available |
| Java embedding | Not available | Available through the standard Polyglot API ([guide](../embedding/java.md)) |

Download and installation are described in [Try Protos](../00-try-protos.md).
