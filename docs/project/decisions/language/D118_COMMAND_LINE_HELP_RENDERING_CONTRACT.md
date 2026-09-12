# D118 — Command-line pure help rendering contract

Status: **RATIFIED — Candidate F′ selected**

Owning work item: `LIB011` / GitHub Issue `#428`  
Decision issue: GitHub Issue `#445`

## Decision

The initial public pure help renderer is:

```text
CommandLine.renderHelp(rootSpec, commandPath) -> String
```

where:

- `rootSpec` is an already-canonical frozen `CommandSpec`;
- `commandPath` is an `Array(String)` of exact child command names relative to
  `rootSpec`; and
- the result is one deterministic canonical plain-text `String`.

The renderer is **path-aware and unwrapped**. It preserves canonical declaration
order, renders only the selected command plus its immediate children, and performs
no terminal-width, TTY, locale, styling, pager, environment, filesystem, process
or output-authority work.

The baseline renderer intentionally does not publish a render-options descriptor
or a public semantic `HelpModel`. Richer renderers remain additive future
interpretations of the same frozen `CommandSpec`.

## Public path contract

Examples:

```text
renderHelp(root, Array())
=> render root

renderHelp(root, Array("package"))
=> render child "package"

renderHelp(root, Array("package", "install"))
=> render grandchild "install"
```

The path is exact and case-sensitive, using the already-canonical child command
names. Prefix abbreviation, normalization, case folding, fuzzy matching and
filesystem/process lookup do not participate.

An invalid path signals an ordinary fresh `Error`.

The renderer does not mutate or re-canonicalize `rootSpec`.

## Scope and command-path presentation

Rendering one selected path includes:

- the selected command's usage synopsis;
- its command help text when present;
- its positional arguments;
- its options; and
- its immediate child commands.

It does **not** recursively expand grandchildren or dump the entire command tree.

The displayed invocation path is mechanically derived from:

```text
rootSpec.name + commandPath
```

For:

```text
rootSpec.name = "tool"
commandPath   = Array("package", "install")
```

the invocation path is:

```text
tool package install
```

No parent pointers, ambient current-command object or Process program name are
required.

## Declaration order

Presentation preserves canonical specification order:

```text
Options:   selectedSpec.options
Arguments: selectedSpec.positionals
Commands:  selectedSpec.subcommands
```

No alphabetical or locale-sensitive sorting is performed.

The frozen ordered Arrays remain the single ordering authority.

## No width-dependent wrapping

The baseline renderer performs no width-dependent wrapping and accepts no width
parameter.

This avoids introducing a second semantic contract for:

- byte count;
- Unicode scalar count;
- grapheme clusters;
- terminal display cells;
- East Asian width;
- combining marks;
- tab expansion;
- minimum widths;
- unbreakable tokens; or
- terminal capability detection.

Descriptions use a next-line layout instead of width-sensitive aligned columns.

The renderer preserves supplied help text content as semantic String content. It
does not probe a terminal or reinterpret content according to display width.

## Canonical section order

Present blocks appear in this exact order:

1. `Usage:`
2. selected command help text, when non-null;
3. `Arguments:`, when the selected command has positionals;
4. `Options:`, when the selected command has options;
5. `Commands:`, when the selected command has immediate subcommands.

Empty sections are omitted.

One empty line separates adjacent present top-level blocks.

No configurable section order or help template exists in the baseline.

## Usage synopsis

The usage synopsis favors bounded scale rather than enumerating every optional
option.

Canonical order is:

```text
<full invocation path>
<required options>
[OPTIONS] when one or more optional options exist
<positionals>
[COMMAND] when immediate subcommands exist
```

### Required option spelling in usage

When a required option has a long spelling, the long spelling is used.

If it has no long spelling, its short spelling is used.

The logical `key` is never rendered as command-line spelling.

Examples:

```text
--output VALUE
-v
```

### Required repeatable options

For one-or-more required option occurrences:

```text
--define VALUE [--define VALUE]...
```

The first occurrence is explicit because it is required; additional occurrences
are represented as the repeated optional suffix.

### Optional options

When at least one optional option exists, optional options are summarized in the
usage synopsis as:

```text
[OPTIONS]
```

Their exact spellings, value names and repeatability are described in the
`Options:` section.

## Positional cardinality notation

Using the positional `valueName` as `NAME`, the four ratified occurrence forms
render as:

```text
1..1          NAME
0..1          [NAME]
0..unbounded  [NAME]...
1..unbounded  NAME [NAME]...
```

No new positional metadata is introduced.

## Option entry notation

Option spellings in the `Options:` section use:

```text
-short, --long VALUE
```

when both short and long forms exist.

If only one form exists, only that declared form is shown.

When option value arity is `1..1`, its `valueName` follows the spelling.

Occurrence policy is rendered using these fixed annotations:

```text
0..1          no annotation
1..1          (required)
0..unbounded  (repeatable)
1..unbounded  (required, repeatable)
```

Examples:

```text
-v, --verbose
-o, --output VALUE (required)
--define VALUE (repeatable)
```

The logical `key` is never used as a display spelling.

## Next-line descriptions

The baseline uses no display-width column alignment.

Examples:

```text
Arguments:
  INPUT
    Input file

Options:
  -o, --output VALUE
    Output file

Commands:
  build
    Build a package
```

When a help field is null, the entry is rendered without a description block.

The renderer does not semantic-wrap help text according to terminal width.

## Immediate subcommands

The `Commands:` section lists only immediate children in declaration order:

```text
Commands:
  build
    Build a package
  test
    Run tests
```

Grandchildren are not recursively expanded.

## Whitespace and output ownership

Renderer-generated line separators are exactly:

```text
\n
```

The returned String has **no final newline**.

This keeps output authority outside LIB011:

```text
renderHelp(...) -> String
caller          -> chooses print/write destination and newline behavior
```

The renderer does not print, exit, colorize, page or write to any stream.

## Purity and authority boundary

`renderHelp` may inspect only the supplied canonical `CommandSpec` tree and
`commandPath`.

It must not acquire or inspect:

- `process.args()`;
- stdout or stderr;
- exit status;
- environment variables;
- configuration files;
- filesystem or network state;
- terminal width;
- TTY state;
- locale;
- ANSI/color support;
- pager availability;
- shell state;
- command execution callbacks;
- a global command registry; or
- mutable process-wide formatting state.

## Complexity target

For command-path depth `D`, specification material inspected along that path
`Svisited`, selected-scope presentation material `Sscope`, and output length `H`,
the target is:

```text
time:   O(Svisited + Sscope + H)
memory: O(H)
```

The renderer does not flatten or recursively format the complete command tree.

An implementation may use ordinary local traversal state; no shared cache,
registry, Task, Actor, thread, Future or lock is required.

## Prior-art basis

The D118 audit compared:

- Rust clap;
- Java picocli;
- .NET `System.CommandLine`;
- OCaml Cmdliner;
- Python argparse;
- Click;
- Cobra/pflag;
- Haskell `optparse-applicative`;
- Rust bpaf;
- Swift ArgumentParser;
- Git-style help; and
- lexopt/getopt-style no-help baselines.

The principal findings were:

- clap strongly separates rendering from printing and demonstrates that
  declaration/display order and next-line help are scalable mechanisms;
- picocli shows the power of spec-derived help but also demonstrates that sorting,
  terminal width, templates and section configuration are additional policies
  rather than parsing necessities;
- `System.CommandLine`, Click and Swift ArgumentParser show that full nested command
  path is real presentation context;
- Cmdliner and `optparse-applicative` demonstrate the power of a semantic
  intermediate help model, but also its extra public/API surface;
- bpaf demonstrates that additional Markdown/manpage-style renderers can be added
  later without requiring the initial renderer to publish a public HelpModel;
- Swift ArgumentParser explicitly treats exact generated-help formatting as outside
  its source-stability guarantee, demonstrating the compatibility cost of a
  canonical exact text contract;
- Git demonstrates that concise CLI help need not recursively become the complete
  project manual; and
- lexopt/getopt show the low-level no-help baseline that LIB011 intentionally moved
  beyond to avoid duplicated grammar/help authorities.

The approved candidate scored:

```text
Aguante de futuro: 4.9 / 5
Escalabilidad:      5.0 / 5
Filosofía Protos:   5.0 / 5
GITHUB010 mean:     4.95 / 5
```

## Strongest argument against

The strongest objection is the compatibility burden of canonical exact text.

Swift ArgumentParser intentionally reserves freedom to change the wording and
formatting of generated help. D118 instead makes the baseline renderer
deterministic enough that callers and tests can depend on its layout.

This may make future aesthetic changes to `renderHelp` breaking changes.

F′ accepts that cost by keeping the baseline deliberately small and plain.
Visually richer output should be additive rather than silently redefining the
canonical renderer.

## Future-regret scenario and escape path

Future tools may require:

- 80-column terminal wrapping;
- Unicode display-cell-aware layout;
- multi-paragraph styled output;
- localization;
- Markdown;
- HTML;
- man pages; or
- richer machine-readable help data.

The baseline unwrapped renderer will remain valid but may not be the best
interactive presentation.

The escape path is additive interpretation, for example:

```text
CommandLine.renderHelpWrapped(...)
```

or future dedicated presentation modules/functions such as:

```text
std:cli/Help
renderMarkdown(...)
renderManpage(...)
deriveHelpModel(...)
```

If several real renderers later need a shared stable semantic intermediate model,
that concrete pressure can justify a future decision to expose one. D118 does not
pre-commit to that institution now.

## Explicitly not decided

D118 does not decide:

- parser token/traversal semantics;
- automatic `--help` or `--version`;
- command dispatch or execution;
- stdout/stderr or exit status;
- terminal display-width semantics;
- ANSI/color/styling;
- pager integration;
- localization;
- shell completion;
- environment/configuration defaults;
- public structured parse diagnostics;
- Markdown/manpage/HTML rendering;
- public `HelpModel`; or
- future wrapped renderer APIs.

## Consequence

`LIB011-D` is unblocked for implementation of the canonical pure renderer under
this contract.

If implementation exposes another substantive semantic or durable architectural
choice, the normal Dxxx/PLATxxx approval gate applies before publication.
