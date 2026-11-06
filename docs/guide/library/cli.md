# Command-line specifications: `std:cli/CommandLine`

`std:cli/CommandLine` builds inert, frozen command-line specifications, parses
an argument Array against them, and renders help text. It reads no process
state: you pass the arguments yourself. The exact contract is the doc comment
of [`CommandLine.protos`](../../../protos/lib/cli/CommandLine.protos).

```protos
CommandLine: import("std:cli/CommandLine")
```

The module publishes exactly `option`, `positional`, `command`, `parse`, and
`renderHelp`.

## Options and positionals

`CommandLine.option` takes nine named parameters and `CommandLine.positional`
takes five. **Every parameter is required**, including the ones whose value may
be `null`: leaving one out signals an `Error`, while an explicit `null` is an
ordinary supplied value.

```protos
jobs: CommandLine.option(
    key: "jobs",
    long: "jobs",
    short: "j",
    minValues: 1,
    maxValues: 1,
    minOccurrences: 0,
    maxOccurrences: 1,
    valueName: "N",
    help: "Number of workers"
)

files: CommandLine.positional(
    key: "files",
    minOccurrences: 1,
    maxOccurrences: null,
    valueName: "FILE",
    help: null
)
```

`null` is meaningful for these parameters:

| Parameter | `null` means |
|---|---|
| `long`, `short` | no long or short spelling (at least one is required) |
| `maxOccurrences` | unbounded repetition (option or final positional) |
| `valueName` | no value (only for an option with `maxValues: 0`) |
| `help` | no help text |

`key` is a non-empty String. An option takes either no value
(`minValues: 0, maxValues: 0`) or exactly one (`minValues: 1, maxValues: 1`);
`minOccurrences` is `0` or `1`, and a bounded `maxOccurrences` is `1`. Invalid
values signal an `Error`. A misspelled or unknown label also signals an
`Error`, before the call runs.

Each call answers a fresh, frozen canonical record: an option has the slots
`kind` (`"option"`), `key`, `long`, `short`, `minValues`, `maxValues`,
`minOccurrences`, `maxOccurrences`, `valueName` and `help`; a positional has
`kind` (`"positional"`), `key`, `minOccurrences`, `maxOccurrences`,
`valueName` and `help`.

## Commands

`CommandLine.command(descriptor)` still takes **one descriptor object**: a
tree of ordinary objects and Arrays. Its `options` and `positionals` Arrays
hold descriptor objects with the same fields as the named parameters above (or
records already returned by `option` and `positional`), and `subcommands`
holds child command descriptors.

```protos
spec: CommandLine.command({
    name: "tool"
    help: "Example tool"
    options: Array(jobs)
    positionals: Array(files)
    subcommands: Array({
        name: "run"
        help: null
        options: Array()
        positionals: Array()
        subcommands: Array()
    })
})

result: CommandLine.parse(spec, Array("--jobs", "4", "input.txt"))
help: CommandLine.renderHelp(spec, Array())
```

`command` copies and validates the complete tree into a frozen specification.
The same descriptor may appear in different branches; that is not a cycle,
and each appearance is normalized independently, so the canonical results are
not guaranteed to share identity. Cycle detection follows only the active
traversal path: a descriptor that reappears inside its own subtree signals an
`Error`. Very deep trees are traversed without growing the execution stack. Later
changes to your descriptor objects do not affect the result.

## Migrating from single-descriptor calls

`CommandLine.option(descriptor)` and `CommandLine.positional(descriptor)` with
one descriptor object are no longer supported, and there is no compatibility
form. Write each field as a named argument instead:

```protos
// Before: CommandLine.option({ key: "jobs" long: "jobs" ... })
CommandLine.option(key: "jobs", long: "jobs", short: null, minValues: 1, maxValues: 1, minOccurrences: 0, maxOccurrences: 1, valueName: "N", help: null)
```

Descriptor objects nested inside `CommandLine.command({ ... })` are unchanged.
