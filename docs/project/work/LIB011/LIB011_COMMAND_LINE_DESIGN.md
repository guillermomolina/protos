# LIB011 — Command-line parsing Standard Library design

Status: **LIB011-0 RATIFIED — Candidate C′ selected; LIB011-A CLOSED — Candidate E′; LIB011-B READY**

Owning work item: GitHub Issue `#428` — `LIB011 — Command-line argument parsing and help generation`

Nature: project Standard Library design record; **non-normative**

Explicit project-owner approval: **2026-09-12**

Validation class: `GOVERNANCE_DOCUMENTATION_ONLY`

## Purpose

This record closes the `LIB011-0` exhaustive comparative design/selection
checkpoint for a public command-line parsing facility in the Protos Standard
Library.

The selected architecture is **Candidate C′ — explicit arguments + inspectable
command specification + lossless inert parse result**, with pure help rendering
from the same specification.

LIB011 deliberately does not turn command-line parsing into a command execution
framework, hidden `Process` authority, environment/configuration framework,
terminal abstraction, filesystem authority, shell integration service, or global
registry.

The normative Protos specification under `spec/` remains authoritative. LIB011
uses existing Protos String, Array, Map, Object, Closure and Error semantics; it
adds no new Core semantic family or runtime privilege.

## Trigger and real consumers

LIB011 is motivated by concrete bundled-tool pressure rather than a hypothetical
API exercise.

The Test Tool already owns a small ordinary-Protos state-machine parser for
`--jobs` and `--resource-catalog`. That parser already demonstrates several
important requirements:

- arguments are explicit data obtained by the caller;
- an option that requires a value consumes the next token literally, even when
  that token begins with `-`;
- duplicate single-valued options are rejected;
- missing values are rejected;
- typed/application validation such as positive-Integer validation belongs to
  the consumer rather than to token recognition itself;
- unknown arguments are currently a TOOL002 policy rather than a universal
  command-line law.

Package Tool and future bundled tools create a second real consumer class. The
repeated mechanism is therefore command-line structure recognition and help
metadata, while tool-specific policy remains with each tool.

## Design goals

The selected architecture optimizes for the following durable properties:

1. **explicit authority** — parsing operates only on arguments and a supplied
   specification;
2. **lossless observation** — successful parsing preserves what was actually
   present instead of immediately collapsing occurrences into application
   defaults;
3. **inspectability** — the complete command grammar exists as ordinary data so
   help, validation and future tooling can inspect it without executing commands;
4. **composition** — shared options/spec fragments are ordinary reusable values,
   not privileged global/persistent flags;
5. **determinism** — no environment, filesystem, terminal, locale or process
   lookup changes parsing behavior;
6. **portability** — public semantics do not inherit a JVM/Rust/Python/Go host
   parser's behavior;
7. **pay only for what is used** — ordinary parsing does not initialize command
   dispatch, shell completion, TTY probing or background machinery;
8. **future escape paths** — typed decoding, forwarding/remainder modes and
   completion can be layered later without invalidating the baseline result.

## Prior-art survey

The audit compared architecture and semantics rather than API spelling.

### Rust `lexopt`

`lexopt` is the strongest precedent for a small explicit parsing mechanism.
Callers supply the argument source and consume recognized tokens themselves. It
keeps application policy visible and avoids a command framework.

Useful lessons for Protos:

- token recognition can be small and deterministic;
- values beginning with `-` can remain literal once the parser is in a
  value-expecting state;
- no reflection/derive model is necessary;
- mechanism and application policy can remain separate.

Limitation: a cursor/state-machine API alone does not provide one inspectable
command grammar from which help, validation or future completion can be derived.

Reference: <https://docs.rs/lexopt/>

### Rust `clap`

`clap` demonstrates a highly scalable command tree, rich option cardinality,
subcommands, value parsers, conflicts, groups, help and completions. Its mature
feature set is strong evidence that a structural command description can scale
to large tools.

Useful lessons:

- subcommands belong naturally in a command-specification tree;
- option occurrence/cardinality should be explicit;
- help should derive from the same grammar users actually parse;
- large CLIs need deterministic conflict and requirement validation.

Protos does not copy `derive`, Rust type ownership, command execution helpers or
host-specific conventions into the public library.

Reference: <https://docs.rs/clap/>

### Rust `bpaf`

`bpaf` is especially useful as evidence that a parser description can remain
composable while also supporting help and completion-oriented inspection.

Useful lesson: parser/specification composition is more future-resilient than a
one-shot mutation-heavy command builder when the same structure must feed
multiple consumers.

Reference: <https://docs.rs/bpaf/>

### Haskell `optparse-applicative`

`optparse-applicative` is strong prior art for a declarative/compositional parser
description. A parser value can be interpreted for parsing and presentation,
which strongly supports Protos's mechanisms-over-institutions preference.

Useful lessons:

- one structural description can feed multiple interpretations;
- shared parser fragments compose without a process-global registry;
- help metadata need not be interwoven with command execution.

Protos does not import Haskell's static applicative typing as a requirement.

Reference: <https://hackage.haskell.org/package/optparse-applicative>

### OCaml `Cmdliner`

`Cmdliner` demonstrates a mature typed command-description architecture with
terms, options, commands and generated manuals/help while keeping command
structure explicit.

Useful lessons:

- command descriptions can remain independent from a reflection system;
- generated documentation is a first-class interpretation of the command model;
- command trees can scale without requiring a global mutable registry.

Reference: <https://erratique.ch/software/cmdliner/doc/Cmdliner/index.html>

### Python `argparse`

`argparse` demonstrates broad baseline expectations: short/long options,
positionals, subparsers, cardinality, defaults and generated help.

Its defaults also expose policies Protos should not inherit implicitly:
abbreviation behavior, parser-owned defaults that erase absence, process-like
error/help behavior and mutable namespace materialization are convenient in
Python but unnecessarily conflate mechanism with application policy for LIB011.

Reference: <https://docs.python.org/3/library/argparse.html>

### Java picocli

picocli is excellent evidence for feature breadth and large command trees. It
supports nested subcommands, sophisticated arity, type conversion, help,
completion generation, annotations and programmatic models.

Its annotation/reflection and execution-oriented conveniences are intentionally
not selected as Protos semantics. Protos should not make class/member reflection,
JVM types, stdout/stderr or command invocation part of command-line parsing.

Reference: <https://picocli.info/>

### Go `flag`, pflag and Cobra

Go's `flag` package demonstrates the simplicity of an explicit parser, while
pflag/Cobra show the ecosystem pressure toward GNU-style flags, subcommands,
help and large command trees.

Cobra's persistent/global flags and command execution hooks are useful product
features but poor baseline semantic primitives for Protos. A shared option in
Protos should be an ordinary reusable specification value composed into the
commands that accept it.

References:

- <https://pkg.go.dev/flag>
- <https://github.com/spf13/pflag>
- <https://cobra.dev/>

### Swift ArgumentParser

Swift ArgumentParser demonstrates an ergonomic typed/declarative mapping from
command structures to application values and nested commands.

The important lesson is the command tree and metadata model. Property wrappers,
Swift protocol derivation and automatic application-value construction are
language-specific mechanisms and are not required for Protos's initial layer.

Reference: <https://github.com/apple/swift-argument-parser>

### .NET `System.CommandLine`

System.CommandLine demonstrates a rich explicit model of commands, options,
arguments, arity, validators and parsing results.

It is particularly useful evidence for keeping parse results inspectable before
application dispatch. LIB011 avoids importing filesystem-specific validators,
host type conversion or invocation middleware into the parsing kernel.

Reference: <https://learn.microsoft.com/dotnet/standard/commandline/>

### JavaScript/Node: Commander and yargs

Commander and yargs demonstrate the ergonomics and ecosystem reach of fluent
command builders, nested commands, generated help and conversion/coercion.

They also illustrate the cost of allowing parser configuration, coercion,
environment integration and command execution to grow into one institution.
Protos keeps these concerns separable.

References:

- <https://github.com/tj/commander.js>
- <https://yargs.js.org/>

### Python Click

Click demonstrates excellent command-group composition and end-user ergonomics,
but it deliberately integrates contexts, invocation, defaults, environment
variables, prompting, terminal output and callbacks.

That integration is appropriate for an application framework but violates the
selected LIB011 kernel boundary.

Reference: <https://click.palletsprojects.com/>

### GNU Smalltalk `Getopt`

Getopt-style parsing provides a useful dynamic-language baseline: command-line
recognition can remain ordinary library behavior without requiring a new language
primitive or static type system.

Its limited structural model reinforces the need for an inspectable command spec
rather than using Getopt alone as the final architecture.

Reference: <https://www.gnu.org/software/smalltalk/manual-base/html_node/Getopt.html>

### Hand-written state machine

The current TOOL002 parser is itself important prior art. It proves that the
required lexical/token mechanism is implementable in ordinary Protos without a
host bridge.

Hand-written parsing remains the minimal-mechanism baseline, but duplicating one
state machine per tool scales poorly, cannot generate canonical help, and makes
cross-tool behavior drift likely.

## Candidate architectures

### Candidate A — imperative token cursor only

Expose a low-level cursor similar to `lexopt`/Getopt and let every caller own all
higher-level recognition and validation.

Strengths:

- tiny mechanism;
- excellent explicitness;
- highly portable;
- near-zero hidden policy.

Weaknesses:

- no single inspectable grammar;
- help/completion must duplicate command knowledge;
- every tool reimplements cardinality/conflict/subcommand policy;
- semantic drift increases as the toolchain grows.

### Candidate B — full command framework

Expose a `Command` framework similar to Cobra/Click/picocli, combining parse,
defaults, typed conversion, help, environment/configuration sources and command
execution.

Strengths:

- high immediate ergonomics;
- mature precedent;
- feature-rich large-CLI support.

Disqualifier: this architecture grants or assumes responsibilities that are not
command-line parsing. It creates a large institution around `Process`, execution,
I/O and environment policy and therefore conflicts with Protos's explicit
authority and pay-only-for-what-you-use principles.

### Candidate C — declarative command spec producing application values

Use one inspectable command description but have parse directly materialize
application-oriented typed/defaulted values.

Strengths:

- generated help and structural validation;
- good ergonomics;
- scalable command trees.

Weaknesses:

- source occurrence/provenance can be lost early;
- defaults erase the distinction between absence and explicit user input;
- typed conversion policy becomes part of the parser API prematurely;
- forwarding/wrapper use cases are harder to add later.

### Candidate C′ — explicit command spec + lossless inert parse result

**Selected.**

The parser receives an explicit `CommandSpec` and explicit argument Array. It
recognizes structure and returns ordinary inert data that preserves command path,
option occurrences, source spelling, values, positionals and token positions.
Application conversion/default policy is a later explicit step owned by the
consumer or a future decoder layer.

The same spec can be interpreted independently for help rendering.

### Candidate D — imperative parser plus independent help schema

Keep parsing small but describe help separately.

Rejected because two authorities would describe the same command grammar. Drift
between accepted syntax and rendered documentation becomes a structural risk.

### Candidate E — host-backed parser bridge

Wrap picocli/System.CommandLine/clap-equivalent behavior behind a Protos API.

Rejected as the public contract. It would couple semantics to one host library,
complicate alternate runtimes/Native Image and make host implementation behavior
observable. A future implementation may internally optimize parsing only if it
preserves the portable LIB011 contract.

## Comparative scorecard

Scores are 1–5. Confidence reflects the strength of prior-art and current-Protos
evidence. Arithmetic is comparison aid only.

| Criterion | A cursor | B framework | C typed spec | C′ lossless spec | D dual-schema | E host bridge |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Correctness / invariant preservation | 4.2 | 3.6 | 4.5 | **5.0** | 3.5 | 3.8 |
| Protos alignment | 4.8 | 2.2 | 4.3 | **5.0** | 3.4 | 2.4 |
| Future-option resilience | 4.4 | 3.1 | 4.4 | **5.0** | 3.2 | 2.6 |
| Scalability | 4.2 | 4.9 | 4.8 | **5.0** | 3.7 | 4.4 |
| Conceptual simplicity | 4.7 | 2.8 | 4.2 | **4.8** | 3.0 | 3.2 |
| Portability / implementation freedom | 5.0 | 3.1 | 4.8 | **5.0** | 4.7 | 2.0 |
| Runtime / resource cost | **5.0** | 3.4 | 4.7 | 4.8 | 4.5 | 3.6 |
| Failure / operability | 4.3 | 3.5 | 4.4 | **5.0** | 3.4 | 3.5 |
| Reversibility / migration cost | 4.7 | 2.9 | 4.3 | **4.9** | 3.5 | 2.5 |
| Evidence maturity / implementation risk | 4.8 | **5.0** | 4.9 | 4.4 | 3.8 | 4.8 |
| **Average** | 4.61 | 3.45 | 4.53 | **4.89** | 3.67 | 3.28 |

Focused project-owner axes:

| Candidate | Aguante de futuro | Escalabilidad | Filosofía Protos |
| --- | ---: | ---: | ---: |
| A cursor | 4.4 | 4.2 | 4.8 |
| B framework | 3.1 | 4.9 | 2.2 |
| C typed spec | 4.4 | 4.8 | 4.3 |
| **C′ lossless spec** | **5.0** | **5.0** | **5.0** |
| D dual-schema | 3.2 | 3.7 | 3.4 |
| E host bridge | 2.6 | 4.4 | 2.4 |

Confidence for the C′ scores is **HIGH** for authority/architecture and
**MEDIUM-HIGH** for final API ergonomics because executable Protos experience will
still be gained during LIB011-A/B.

## Selected public module identity

The canonical initial module identity is:

```text
std:cli/CommandLine
```

with intended physical source:

```text
protos/lib/cli/CommandLine.protos
```

`CommandLine` is an ordinary Standard Library module. It is not a Core prototype,
new runtime family, process singleton or command dispatcher.

Importing it grants no authority.

## Selected ownership boundary

The kernel owns only command-line **description, structural parsing and pure
presentation**.

Conceptually:

```text
CommandSpec + Array(String)
          |
          v
   CommandLine.parse
          |
          v
  lossless ParseResult
```

and independently:

```text
CommandSpec + explicit render parameters
          |
          v
   CommandLine.renderHelp
          |
          v
        String
```

The kernel does **not** own:

- `process.args()` acquisition;
- stdout/stderr;
- exit status;
- command execution/dispatch;
- current working directory;
- environment variables;
- configuration files;
- filesystem or network lookup;
- terminal width/TTY detection;
- colors/ANSI policy;
- interactive prompting;
- localization;
- shell process invocation;
- package/tool business rules.

A caller may explicitly pass `process.args()` to the parser, but that authority
acquisition happens outside LIB011.

## Selected command specification model

LIB011-A owns the exact mechanical ordinary-data shape, subject to this ratified
semantic boundary.

The model must be able to describe:

- command name and descriptive help metadata;
- ordered/reusable option specifications;
- ordered positional-argument specifications;
- nested subcommands;
- exact long and optional short spellings;
- option value arity/cardinality;
- occurrence cardinality;
- required/optional presence;
- explicit conflicts/requirements only when justified by the bounded A design;
- help metavars/text without terminal authority.

The specification is inspectable ordinary data. It contains no command-execution
Closure and no callback whose invocation is required merely to understand the
accepted grammar.

### No privileged persistent/global flag concept

Cobra-style persistent/global flags are not a baseline semantic category.

When several commands accept the same option, callers compose/reuse an ordinary
`OptionSpec` (or equivalent mechanical spec fragment) in those commands. This
preserves one ordinary composition law instead of adding a special inheritance
mechanism for command flags.

## Selected parse-result model

A successful parse result is inert ordinary data and preserves enough
information for later interpretation without reparsing original arguments.

At minimum it preserves:

- selected command/subcommand path;
- ordered option occurrences;
- the exact canonical option identity selected by the spec;
- spelling used by the caller where materially different, such as short versus
  long form;
- associated raw String value when the option requires a value;
- ordered positional arguments;
- original token index / source position sufficient for deterministic
  diagnostics and provenance;
- the distinction between absence and explicit occurrence.

The result does not execute callbacks and does not silently inject application
default values.

A later decoder/interpretation layer can project this lossless representation to
application values.

## Selected token grammar baseline

The initial public parser recognizes exact declared spellings only.

### Long options

Selected forms:

```text
--name
--name value
--name=value
```

`--name` is valid only for a flag/no-value option.

For an option requiring one value, `--name value` consumes the following token
literally once `--name` has been recognized, even when the value begins with
`-`. `--name=value` preserves the exact value after the first selected `=`
boundary, including an empty String if the option's selected value contract
allows an empty value.

### Short options

Selected forms:

```text
-n
-n value
```

Short-option clusters such as:

```text
-abc
```

are accepted only when every selected member is a declared no-value flag. The
initial baseline does not allow a value-taking option to consume a suffix from a
cluster because that creates avoidable policy/ambiguity.

Attached short values such as `-nVALUE` are not selected initially.

### End-of-options marker

Exact token:

```text
--
```

ends option recognition for the current command level. Subsequent tokens are
ordinary positional data according to that command's positional contract.

### No option abbreviation

Long options are never accepted by unique prefix merely because the current spec
happens to make the prefix unambiguous.

This avoids compatibility traps where adding a future option changes the meaning
of an old command line.

### No optional option-values initially

The initial baseline has no `option [value]` form whose value may be omitted.
Optional option-values create ambiguity with positionals, negative-looking data
and subcommands. If a real consumer later requires them, that is an explicit
extension decision with a defined disambiguation rule.

## Occurrence and duplicate policy

Occurrence cardinality is explicit in the specification rather than a universal
`last wins` rule.

The initial useful cardinalities are expected to include the semantic roles:

```text
zero-or-one
exactly-one / required
zero-or-more / repeatable
one-or-more / required-repeatable
```

Exact public constructor/spelling is owned mechanically by LIB011-A.

For a single-valued option, a second occurrence is a parse failure. The parser
does not silently choose first or last occurrence.

For repeatable options, every occurrence remains present and ordered in the
lossless result.

This directly supports TOOL002's current duplicate rejection without making that
one tool's option names public library policy.

## Defaults and absence

The parsing kernel does not materialize application defaults.

If an option is absent, it is absent from the occurrence result. The consumer can
then apply its own default explicitly, for example TOOL002's current `jobs = 1`
policy.

This keeps these distinct states observable:

```text
option absent
option explicitly supplied with a value equal to the application default
```

A future typed decoder layer may support explicit default projection without
changing parse provenance.

## Typed conversion boundary

Initial LIB011 parsing returns String data rather than automatically converting
to Integer, Float, Path, enum-like values or arbitrary application objects.

Reasons:

- command-line transport fundamentally supplies text;
- Core already makes important conversions explicit;
- conversion failure policy belongs to the target domain;
- early conversion can discard source spelling/provenance;
- a future decoder layer can compose over the lossless result without breaking
  the parser contract.

TOOL002 therefore retains ownership of positive-Integer validation for `--jobs`
when it first adopts LIB011.

The architecture explicitly leaves room for a future pure decoder layer such as
String-to-Integer validation; that future layer must remain separate from Process
or I/O authority.

## Subcommands

Nested subcommands are part of the structural model from the beginning because
Package Tool and future toolchain growth make them a plausible immediate scale
requirement.

Subcommand names are exact; no prefix abbreviation is selected.

A subcommand is another `CommandSpec` node, not a separate execution primitive.
The parse result records the selected command path. Actual invocation remains
caller-owned.

The command specification must permit the same reusable option specification to
appear deliberately at multiple command levels without creating implicit
inheritance/global semantics.

## Unknown options and forwarding

Baseline parsing is strict for the selected command grammar: an option-like token
that is not recognized by the active spec is a parse failure before `--`.

This is intentionally different from TOOL002's historical choice to ignore
unknown arguments. TOOL002 migration must preserve TOOL002 behavior only if that
behavior remains an approved tool requirement; adopting LIB011 cannot silently
change it.

Wrapper/plugin forwarding is a legitimate future requirement. The selected
escape path is an explicit future remainder/delegation mode or low-level token
cursor sharing the same lexical rules. LIB011 does not make all parsers
permissive merely to anticipate wrappers.

## Error model

Parsing failure signals an ordinary synchronous fresh Error unless a bounded
implementation slice finds that stable structured parse diagnostics are already
required for the public contract and routes any substantive choice through the
approval gate.

The implementation should retain enough internal failure structure to test:

- unknown option;
- missing required value;
- duplicate/surplus occurrence;
- missing required option/positional;
- unexpected positional;
- invalid subcommand;
- malformed short cluster;
- specification inconsistency.

The lossless/token-position architecture deliberately preserves a migration path
to future structured diagnostics without forcing one host library's exception
model into the initial API.

## Help rendering boundary

Help is a pure interpretation of the command specification.

The initial helper must not probe terminal width. Width/wrapping inputs are
explicit parameters or deterministic library defaults selected by the bounded
LIB011-D implementation design.

Rendering returns String. It does not print, exit, colorize, page, invoke a
pager, inspect locale or detect TTY capabilities.

Because parsing and help share the same spec authority, an option accepted by
the parser can be documented without maintaining a second grammar.

## Shell completion boundary

Shell completion generation/execution is deferred from the first implementation.

The selected inspectable specification intentionally preserves an escape path:
static completion metadata may later be derived without changing parsing.
Dynamic completion that requires filesystem/network/process/package authority
must receive those capabilities explicitly in a separate layer.

No Bash/Zsh/Fish/PowerShell process invocation or shell installation behavior is
part of the parser kernel.

## Unicode and String policy

Argument elements are semantic Protos Strings. LIB011 does not define host byte
or OS argv decoding; that boundary belongs to Process/bootstrap semantics.

Option names in the initial standard-library API should prefer the ordinary
portable command convention of exact declared spellings. The bounded A slice may
constrain option-name grammar for deterministic interoperability, but it must not
silently introduce Unicode normalization or case folding.

Option values and positional values are preserved as exact semantic Strings.

## Complexity and scale

For an argument vector of `n` tokens and a command specification with indexed
option names, baseline parsing should be implementable in expected `O(n)` time
plus result construction, with memory proportional to the explicit spec and
retained occurrences.

No global registry/cache is required. Independent Processes/Actors can own
independent module instances/specifications under the existing module model.

Large command trees should be indexable per command node without forcing every
parse to search the complete recursive tree for each token.

The architecture introduces no shared mutable state and no coordination among
independent parses.

## Concurrency, Actor and Process scaling

Parsing is synchronous pure data processing and requires no Future, Task, Actor,
thread or lock.

A command specification may be constructed and used within an Actor according to
ordinary Standard Library module/value rules. LIB011 introduces no special
cross-Actor sharing semantics.

Many semantic Processes can parse independently without a central registry or
cache. This matches Protos's preference for independence over coordination.

## Portability and implementation freedom

The public contract is independent of:

- JVM argument-parser libraries;
- annotations/reflection;
- Rust derive/macros;
- Python Namespace objects;
- Go flag registries;
- Swift property wrappers;
- .NET type binders;
- POSIX getopt implementation details;
- host terminal/locale APIs.

A current or future implementation may optimize lookup tables, immutable spec
indexes or rendering internally provided observable semantics remain unchanged.

## Future scenario stress tests

### Large Package Tool command tree

Nested subcommands and ordinary spec composition scale without adding a second
framework. Shared options are reused structurally rather than inherited through
special persistent/global flag semantics.

### Test Tool grows dozens of options

The same parser continues to provide exact occurrence provenance. Tool-specific
validation remains local. No process-global state is introduced.

### Wrapper forwards arguments to another tool

Strict baseline parsing would be insufficient by itself. The escape path is an
explicit remainder/forwarding mode or low-level cursor that preserves the same
token laws. Existing strict commands do not change meaning.

### IDE/help/documentation tooling inspects commands without executing them

The inspectable `CommandSpec` supports this directly. A callback-only command
framework would not.

### Static shell completion

The command tree can be interpreted for completion without changing parsing.
Dynamic completion remains an explicit capability-bearing extension.

### Alternate non-JVM Protos runtime

The contract depends only on ordinary Protos values and parsing rules. No host
argument-parser library is semantically required.

### Very many concurrent Processes

No global parser registry, TTY state or shared mutable cache is required. Each
parse remains local data processing.

## What could make us regret C′?

The strongest plausible regret scenario is an ecosystem dominated by transparent
wrapper/plugin commands that must preserve and forward unknown option syntax,
including foreign short-option clustering and attached-value conventions.

A strict structural parser alone would be inconvenient for that workload.

The selected escape path remains good because C′ preserves raw occurrence/token
information and does not claim to be the only lexical mechanism. A later
`remainder`/delegation policy or low-level cursor can be added explicitly without
changing existing strict command meaning or introducing hidden authority.

A second possible regret is that application code may find explicit
String-to-domain decoding verbose. The escape path is a pure composable decoder
layer over ParseResult. Because C′ did not discard absence/provenance, adding that
layer later is easier than recovering lost information from an eager typed parser.

## Strongest argument against C′

The strongest argument is **ergonomics and implementation surface**.

A cursor-only library could be implemented very quickly. A typed framework could
produce application values with less boilerplate. C′ instead requires a command
specification model, validation, lossless occurrence representation and a later
interpretation step.

That cost is real. It is accepted because the same structural model pays for
help, future completion, diagnostics, IDE/tool introspection and large command
trees while keeping authority and application conversion outside the parser.
The added concepts remove more duplicated policy and future compatibility risk
than they introduce.

## Intentionally deferred

LIB011-0 does not select:

- command execution/dispatch framework;
- automatic reading of `process.args()`;
- environment-variable/config-file merging;
- filesystem/network-backed value resolution;
- terminal width discovery;
- colors/ANSI styling;
- interactive prompting;
- localization;
- pager integration;
- shell completion generation/install;
- dynamic completion authority;
- host-specific argv byte decoding;
- optional option-values;
- long-option abbreviation;
- attached short option values;
- a universal typed decoder hierarchy;
- plugin discovery;
- transparent foreign-CLI forwarding syntax.

Each may be added later only when a real consumer justifies it and any new
substantive semantics cross the normal approval gate.

## LIB011-A public model ratification

LIB011-A is **RATIFIED — Candidate E′ selected** by explicit project-owner
approval on 2026-09-12 after the focused public-model audit of mature CLI
libraries and tools.

Candidate E′ refines the already-ratified LIB011-0 Candidate C′ architecture; it
does not replace it. The selected materialization rule is:

```text
validated descriptor records
        |
        v
fresh canonical tagged structural data
        |
        v
node-by-node frozen specification graph
```

and, for eventual parsing:

```text
explicit arguments snapshot
        |
        v
recursive inert lossless result tree
```

The public model remains ordinary inspectable Protos data rather than a nominal
`CommandSpec` / `OptionSpec` prototype hierarchy, mutable builder graph,
reflection/annotation system, typed object mapper or command-execution framework.

Focused selection scores for Candidate E′ are:

| Axis | Score |
| --- | ---: |
| Aguante de futuro | **5.0 / 5** |
| Escalabilidad | **5.0 / 5** |
| Filosofía Protos | **5.0 / 5** |
| Ten-axis comparative average | **4.92 / 5** |

The strongest argument against E′ is verbosity: explicit logical keys,
independent cardinality bounds and canonical copying/freezing require more
ceremony than derive/annotation or fluent-builder frameworks. That cost is
accepted because convenience helpers can be layered later without losing
provenance, absence, portability or inspectability.

### Approved LIB011-A contract

The bounded approved decisions are exactly:

1. `std:cli/CommandLine` uses ordinary tagged structural records, not a public
   `CommandSpec` / `OptionSpec` prototype hierarchy.
2. Public constructors are descriptor-record based and return fresh canonical
   data; they never mutate the supplied descriptor.
3. Canonical specification records and retained Arrays are snapshotted and
   frozen node-by-node. This is required because ordinary Protos `freeze()` is
   shallow and therefore does not by itself make a retained graph deeply
   immutable.
4. Options and positionals have explicit non-empty logical `key` Strings; option
   keys are separate from long/short spelling. `key` is used rather than `id` so
   this library does not overload Protos identity terminology.
5. External names use the initial portable ASCII grammar; comparison is exact
   and case-sensitive; no Unicode normalization or case folding occurs. The
   bounded initial grammar is one ASCII alphanumeric character for a short name,
   and an ASCII-letter first character followed by ASCII letters, digits or `-`
   for long option and command names.
6. Cardinality uses independent min/max fields for values and occurrences.
   LIB011-A validates only the already-ratified baseline value cardinalities
   `0..0` and `1..1` and occurrence roles `0..1`, `1..1`, `0..unbounded`, and
   `1..unbounded`; `null` represents an unbounded maximum.
7. Conflicts, requirements, groups, aliases beyond long+short, typed
   converters/defaults, callbacks and authority-bearing metadata are deferred.
8. Explicit parse argument Arrays exclude the executable/root command name; the
   root name comes from the command specification.
9. `ParseResult` retains a frozen snapshot of those exact arguments and a
   recursive `CommandResult` tree rather than collapsing results into an
   application-value Map.
10. Token indices are zero-based direct indices into the retained arguments
    Array. `CommandResult` records the structural `--` delimiter index, while
    option and positional occurrences retain the source indices needed for exact
    provenance. An option value that is literally `--` remains distinguishable
    from a structural end-of-options delimiter.
11. Results carry logical keys/canonical names, not references whose meaning
    depends on `OptionSpec` / `CommandSpec` object identity or lifetime.
12. Any implementation discovery that requires observable parent-positionals /
    subcommand allocation semantics, a public parse-error taxonomy or another
    durable semantic choice stops the affected LIB011 slice and crosses the
    normal explicit decision gate before implementation continues.

These choices intentionally preserve private implementation freedom. A parser
may later build local lookup indexes or a private compiled plan from the frozen
specification, but no global registry/cache or public compiled-spec identity is
selected.

### Result/provenance shape selected for later parsing

LIB011-A may publish or internally prepare the ordinary record shape needed by
LIB011-B, but A does not publish a complete token parser.

The selected result architecture is conceptually:

```text
ParseResult
  arguments: frozen exact input snapshot
  command: CommandResult

CommandResult
  name: canonical command name
  tokenIndex: null | Integer
  endOfOptionsIndex: null | Integer
  options: ordered option-occurrence Array
  positionals: ordered positional-occurrence Array
  subcommand: null | CommandResult
```

An option occurrence preserves at least its logical `key`, exact spelling,
zero-based option-token index, exact raw String value when present and the
zero-based token index containing that value. For `--name=value`, the option and
value indices are the same input-token index. Multiple members of one accepted
short-flag cluster may likewise share one input-token index while retaining
occurrence order.

The recursive command-result shape scopes parent/child occurrences naturally and
avoids collisions when the same logical key is deliberately reused at different
command levels.

### Deferred boundary after LIB011-A

LIB011-A does **not** select or implement:

- token recognition/parsing behavior beyond model invariants already ratified by
  LIB011-0;
- parent-positionals versus subcommand traversal/allocation policy;
- conflicts, requirements or option groups;
- aliases beyond the one canonical long spelling and optional short spelling;
- typed decoding/default projection;
- callbacks/actions/dispatch;
- environment/config/process/TTY/filesystem/network authority;
- completion providers;
- a public structured parse-error taxonomy;
- a public compiled-spec/cache abstraction.

Those remain later bounded work. LIB011-A1/A2/A3 are now closed; LIB011-B is
released for bounded implementation under the already-ratified token laws.

## Implementation sequence

The ratified architecture releases this bounded sequence:

### LIB011-A — public specification/result model

**CLOSED — Candidate E′ implemented and model-closed.** A1/A2/A3 are closed and
the approved public model is complete; LIB011-B is released for bounded token-parser
implementation.

#### LIB011-A1 — specification model

**CLOSED — implementation version `0.2.450-SNAPSHOT`.**

Public module exports exactly `option`, `positional`, and `command`. The three
constructors implement the approved E-prime specification model as ordinary
structural data: fresh canonical records, exact logical keys separate from
option spellings, portable ASCII external-name validation, explicit cardinality
bounds, per-command uniqueness, ordered positional validation, nested command
canonicalization, recursive-cycle rejection, fresh retained Arrays, and
node-by-node freezing of the complete canonical specification graph. Descriptor
records and their aggregate Arrays are never mutated or retained.

No result model, token parser, command traversal policy, help rendering, typed
decoding/defaults, authority-bearing integration or public Error taxonomy is
introduced by A1.

Publish descriptor-based constructors plus canonical, fresh, tagged and
node-by-node-frozen `CommandSpec`, `OptionSpec` and `PositionalSpec` structural
records. Validate only the approved naming, logical-key, cardinality, ordering,
uniqueness and graph-snapshot invariants. No token parser, command traversal or
help renderer is included.

#### LIB011-A2 — result model

**CLOSED — test-only executable shape evidence; implementation version unchanged.**

A2 closes the already-approved E-prime result/data contract without publishing
result-construction functions. `std:cli/CommandLine` continues to export exactly
`option`, `positional`, and `command`; future `parse` remains the sole intended
public producer of parse-result data. This avoids turning parser-private
construction mechanics into a compatibility surface before the parser exists.

The future parser output is fixed to these ordinary tagged structural records:

```text
ParseResult
  kind: "parseResult"
  arguments: Array(String)
  command: CommandResult

CommandResult
  kind: "commandResult"
  name: String
  tokenIndex: null | Integer
  endOfOptionsIndex: null | Integer
  options: Array(OptionOccurrence)
  positionals: Array(PositionalOccurrence)
  subcommand: null | CommandResult

OptionOccurrence
  kind: "optionOccurrence"
  key: String
  spelling: String
  tokenIndex: Integer
  value: null | String
  valueTokenIndex: null | Integer

PositionalOccurrence
  kind: "positionalOccurrence"
  key: String
  value: String
  tokenIndex: Integer
```

`ParseResult.arguments` is a fresh frozen snapshot of the exact explicit argument
Array supplied to the future parser; it excludes the executable/root-command
name. Every result record and every retained result Array is frozen. Result nodes
carry canonical names/logical keys plus source spelling and values, never
references whose meaning depends on specification-object identity or lifetime.

Every non-null token index is zero-based and directly indexes the retained
`ParseResult.arguments` Array. Root `CommandResult.tokenIndex` is `null`; a
selected nested command records the source token that selected it. A non-null
`endOfOptionsIndex` identifies the exact structural `--` token for that command
scope. This remains distinct from an option value whose exact String is also
`"--"`, because such a value retains its own `valueTokenIndex`.

Ordered option and positional occurrence Arrays are scoped to their containing
`CommandResult`. The optional recursive `subcommand` carries the selected child
scope. A2 therefore does not flatten repeated keys across command levels and does
not retain parent backlinks.

A2 initially published complementary JUnit shape evidence through
`ProtosCommandLineResultModelTest`. Before closing LIB011-A, A3 adds the
authoritative Protos-language semantic corpus under `protos/tests/library/cli/**`
for this same result contract: fresh argument snapshot, node-by-node freezing,
recursive command scoping, direct indices and the literal-`--` versus structural
delimiter distinction. The JUnit evidence remains complementary and continues to
assert exact host-visible slot sets. No production parser or hidden native helper
is introduced by A2 or A3.

A2 deliberately does not validate parent-positionals/subcommand allocation,
recognize tokens, select options, define parse failures, or expose a public Error
taxonomy. Those remain outside this bounded result-model closure.

#### LIB011-A3 — adversarial model closure

**CLOSED — Protos-language semantic closure; implementation version unchanged.**

A3 closes Candidate E′ without changing production `CommandLine.protos` or
widening `std:cli/CommandLine`. In accordance with the repository testing policy,
observable Standard Library semantics are now primarily exercised by executable
Protos fixtures under:

```text
protos/tests/library/cli/
```

The corpus covers the already-published A1 constructors and invariants, snapshot
and node-by-node freeze behavior, the A2 recursive lossless result shape,
zero-based direct provenance, literal `--` versus structural delimiter,
attached-value provenance, shared short-cluster token indices, duplicate short
spellings, duplicate positional logical keys, wrong-family descriptor fields,
portable external-name rejection, malformed nested specifications and recursive
descriptor cycles.

`ProtosCommandLineModuleTest` is the thin Java harness that loads those Protos
fixtures. The previously published `ProtosCommandLineSpecModuleTest` and
`ProtosCommandLineResultModelTest` remain complementary implementation/shape
evidence; they are not treated as the sole semantic authority.

The bounded LIB011 focal runs only those three LIB011 harnesses. A3 is an
intermediate child closure: the owning top-level LIB011 work item remains open,
and this slice changes no specification, public API, production runtime/library
implementation, implementation version, or semantic/platform decision. Its
definitive affected-test dependency closure is therefore the three LIB011
harnesses above. Broader integrated validation remains the responsibility of the
top-level LIB011 executable/conformance closure, as required by the repository
impact-aware publication policy.

No parent-positionals/subcommand allocation rule, public parse-error taxonomy,
new result field, parser behavior, authority surface or other durable semantic
choice was required. The explicit decision gate therefore remains untriggered,
LIB011-A is closed, and LIB011-B is READY.

### LIB011-B — token parser

**READY.**

Implement strict explicit-argument parsing for the ratified long/short option,
value, cardinality, positional and `--` laws with lossless results.

### LIB011-C — subcommand traversal and composition

Implement nested command-path selection and reusable specification composition,
without execution callbacks or global/persistent flag semantics.

### LIB011-D — pure help rendering

Render deterministic help String data from the same command specification with
no terminal or output authority.

### LIB011-E — Test Tool adoption

Audit and migrate TOOL002's common command-line mechanism onto LIB011 without
silently changing Test Tool-specific defaults, validation or unknown-argument
policy.

### LIB011-F — adversarial/conformance closure

Exercise malformed specs, duplicate occurrences, missing values, negative-looking
values, `--`, clusters, nested commands, large specs and isolation/concurrency
properties; reconcile documentation and close the initial LIB011 scope.

## Publication boundary of this ratification

This `LIB011-0` publication is documentation/governance only.

It changes no:

- Protos normative specification;
- executable Standard Library source;
- CLI/runtime/tool behavior;
- Maven implementation version;
- Core native boundary;
- Test Tool behavior;
- Package Tool behavior.

The publication records the project-owner-approved architecture and releases
LIB011-A. Executable behavior begins only in a separately validated implementation
slice.
