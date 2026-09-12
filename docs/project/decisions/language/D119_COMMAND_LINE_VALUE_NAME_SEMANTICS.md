# D119 — Command-line valueName requirement and help placeholder semantics

Status: **RATIFIED — Candidate A′ selected**

Owning work item: `LIB011` / GitHub Issue `#428`  
Decision issue: GitHub Issue `#446`

## Decision

A valid canonical command-line specification must be intrinsically renderable by
the canonical D118 help renderer.

`valueName` therefore has an exact structural relationship with whether the
specification node consumes a user value:

```text
OptionSpec with minValues=0 and maxValues=0:
    valueName MUST be null

OptionSpec with minValues=1 and maxValues=1:
    valueName MUST be a non-empty String

PositionalSpec:
    valueName MUST be a non-empty String
```

No help interpreter may invent a missing operand name from logical identity,
spelling, type metadata, reflection, converters, runtime values, locale or
generic fallback labels.

The invariant is enforced when the canonical specification node is constructed,
not later when help is rendered.

## Rationale

LIB011 is not a parse-only abstraction. Its selected architecture is one frozen
inspectable `CommandSpec` interpreted by both:

```text
CommandLine.parse(...)
CommandLine.renderHelp(...)
```

A canonical specification that parses successfully but cannot be rendered
without an invented fallback would weaken that single-authority model.

`key`, command-line spelling and `valueName` answer different questions:

```text
key
    stable logical result identity

short / long
    spelling typed by the user

valueName
    human-facing symbolic name of the consumed operand
```

D119 preserves those dimensions as independent data.

For example:

```text
key: "destination"
long: "output"
valueName: "FILE"
```

means:

```text
destination  -> logical result identity
--output     -> command-line spelling
FILE         -> operand presentation identity
```

Changing any one does not require changing the others.

## Exact OptionSpec invariant

### Flag / zero-value option

For:

```text
minValues = 0
maxValues = 0
```

the canonical specification requires:

```text
valueName === null
```

A non-null `valueName` on a flag is rejected as contradictory/dead metadata.

Examples:

```text
{
    key: "verbose"
    long: "verbose"
    short: "v"
    minValues: 0
    maxValues: 0
    valueName: null
}
```

is valid.

```text
{
    key: "verbose"
    long: "verbose"
    short: "v"
    minValues: 0
    maxValues: 0
    valueName: "VALUE"
}
```

is invalid.

### Value-taking option

For:

```text
minValues = 1
maxValues = 1
```

the canonical specification requires `valueName` to be:

```text
String
non-null
non-empty
```

Examples:

```text
{
    key: "output"
    long: "output"
    short: "o"
    minValues: 1
    maxValues: 1
    valueName: "FILE"
}
```

is valid.

The following are invalid:

```text
valueName: null
valueName: ""
```

## Exact PositionalSpec invariant

Every positional represents user-supplied value data, so every canonical
`PositionalSpec` requires:

```text
valueName is a non-empty String
```

This applies independently of occurrence cardinality:

```text
1..1
0..1
0..unbounded
1..unbounded
```

Examples such as:

```text
valueName: "TARGET"
valueName: "FILE"
valueName: "EXTRA"
```

are valid.

The following are invalid:

```text
valueName: null
valueName: ""
```

## valueName lexical policy

D119 does not impose an identifier grammar, ASCII restriction, uppercase
requirement, case conversion or normalization on `valueName`.

The required invariant is only:

```text
String
non-empty when the node consumes/presents a value
```

The canonical help renderer uses the supplied String exactly as presentation
metadata.

No transformation such as:

```text
key -> uppercase
camelCase -> CAMEL_CASE
hyphenation
locale casing
Unicode normalization
```

is performed.

## Explicitly rejected fallback mechanisms

The baseline MUST NOT derive a missing `valueName` from:

- logical `key`;
- `short` or `long` spelling;
- host-language field/property names;
- Java/Python/Rust/Swift identifiers;
- reflection;
- static or runtime type names;
- typed converters/decoders;
- environment or locale;
- generic `VALUE`;
- generic `ARG`; or
- any runtime value.

The baseline also MUST NOT:

- omit a metavariable for a value-taking option;
- omit a metavariable for a positional;
- treat `valueName=null` as "hidden";
- defer the missing-name error until `renderHelp`.

## Failure locality

Invalid metadata fails during canonical construction:

```text
CommandLine.option(...)
CommandLine.positional(...)
CommandLine.command(...)
```

rather than later during:

```text
CommandLine.renderHelp(...)
```

This preserves:

```text
valid canonical CommandSpec
        |
        +--> parseable
        |
        +--> canonically help-renderable
```

There is no second class of parseable-but-not-renderable canonical specification.

## Parsing behavior

D119 changes no token-recognition or parse-result semantics.

It does not change:

- option spellings;
- value arity;
- occurrence cardinality;
- D111 positional allocation;
- D115 subcommand traversal;
- `ParseResult` shape;
- option-value literal consumption;
- `--` behavior;
- typed decoding; or
- command dispatch.

It tightens only which descriptor combinations are valid canonical specification
data.

## Prior-art basis

The D119 audit compared:

- Rust clap;
- Python argparse;
- Java picocli;
- .NET `System.CommandLine`;
- Swift ArgumentParser;
- Click;
- OCaml Cmdliner;
- Haskell `optparse-applicative`;
- Rust bpaf;
- Go pflag; and
- lexopt/getopt-style parse-only baselines.

The main ecosystem families were:

### Logical-identity fallback

Systems such as clap, argparse and `System.CommandLine` reuse an argument
identifier/name/destination when an explicit metavar/help name is absent.

This is mature and practical, but it couples logical identity to presentation.

### Host-declaration fallback

picocli and Swift ArgumentParser can derive display identity from reflected or
declared host-language field/property names.

This source of identity does not exist in portable inert Protos specification
data.

### Type/converter fallback

Cmdliner, Click and pflag can derive or obtain presentation identity from typed
converter/value metadata.

LIB011 deliberately defers typed decoding, so importing this dependency would
violate the current boundary.

### Explicit presentation identity

bpaf and the documentation style of `optparse-applicative` provide the strongest
Protos precedent: a value-bearing parser node carries explicit documentation
metadata for the value it consumes.

That maps directly to Candidate A′ without merging `key`, spelling and
presentation identity.

The approved candidate scored:

```text
Aguante de futuro: 5.0 / 5
Escalabilidad:      5.0 / 5
Filosofía Protos:   5.0 / 5
GITHUB010 mean:     4.90 / 5
```

## Strongest argument against

A parse-only consumer may reasonably ask why it must provide presentation metadata
such as:

```text
valueName: "FILE"
```

if it never calls `renderHelp`.

That objection is valid for a low-level parser such as lexopt.

LIB011 intentionally selected a different abstraction: one canonical grammar
shared by parsing and generated help. Within that abstraction, the minimum
presentation identity is part of a complete valid specification.

If Protos later needs a truly parse-only lower-level facility, it should be an
additive mechanism rather than weakening canonical `CommandSpec`.

## Future-regret scenario and escape path

A future consumer may want an argument to participate in parsing but be hidden
from one renderer.

D119 intentionally does not overload:

```text
valueName = null
```

to mean hidden.

Presentation identity and visibility are separate dimensions. If hidden
arguments/options become a real requirement, a future explicit visibility
mechanism can be added under its own decision.

Likewise, a future typed decoder layer may add independent type/conversion
metadata without replacing `valueName`.

## Migration impact

This is an intentional validation tightening.

Descriptors previously accepted in these shapes become invalid:

```text
value-taking OptionSpec + valueName=null
value-taking OptionSpec + valueName=""
PositionalSpec + valueName=null
PositionalSpec + valueName=""
flag OptionSpec + valueName!=null
```

Migration is mechanical: provide an explicit non-empty presentation name for
every consumed value and remove dead value names from flags.

No command-line invocation syntax or parse-result meaning changes.

## Explicitly not decided

D119 does not decide:

- hidden arguments/options;
- localization;
- typed decoding;
- multiple option values;
- richer metavar structures;
- automatic help/version semantics;
- output styling or wrapping;
- terminal integration;
- public structured help models; or
- command execution.

## Consequence

`LIB011-D` is unblocked to:

1. tighten `OptionSpec` and `PositionalSpec` canonical validation under D119 A′;
2. update semantic tests for the new invariant; and
3. implement D118 F′ `CommandLine.renderHelp(rootSpec, commandPath)` knowing that
   every valid selected operand has an explicit presentation identity.

If implementation exposes another substantive semantic or durable architectural
choice, the normal Dxxx/PLATxxx approval gate applies before publication.
