# Protos toolchain tool work

These instructions apply to `TOOLxxx`.

Investigation contract: the tool's policy surface plus the governing
specification sections for capabilities the tool consumes. Tool-local
subsystems must reuse established Protos facilities per `protos/AGENTS.md`.

Apply the shared implementation discipline in `AGENTS.work/IMPLEMENTATION.md`
for validation, versioning, changelog, and publication.

## Toolchain tool work

Official toolchain-bundled developer tools use the `TOOLxxx` family once they
are promoted from exploratory architecture to tracked implementation work.
`TOOLxxx` tracks the lifecycle and policy surface of the tool itself; it is
independent of the public command spelling used to invoke that tool.

The initial assignments are:

- `TOOL001` — Package Tool;
- `TOOL002` — Test Tool.

Future official bundled tools such as benchmark, formatter, linter,
documentation, coverage, or profiling tools should allocate the next unused
`TOOLxxx` identifier when they become concrete tracked work. Do not create a new
project-work family solely because a new tool has a distinct public command.

Keep the following boundaries explicit:

- `CLIxxx` owns independently meaningful driver, terminal, REPL, command
  selection/dispatch, and general command-line UX mechanics. A bundled tool does
  not become `CLIxxx` work merely because users reach it through `protos`.
- `TOOLxxx` owns bundled-tool policy and tool-specific orchestration implemented
  primarily as ordinary Protos code where the selected architecture permits it.
- `PERFxxx` owns performance engineering of the Protos project itself. A future
  user-facing benchmark or profiling tool is still `TOOLxxx`; `PERFxxx` may use
  such a tool as evidence without becoming that tool.
- `LIBxxx` owns distributable Standard Library API. Bundled tool implementation
  modules are not Standard Library merely because they ship with the toolchain.
- a future third-party plugin/extension mechanism is a separate design problem;
  official bundled tools are not plugins by default.

`TOOL001` was introduced after Package Tool implementation had already begun;
the TOOL001 project record maps the historical slice names into the canonical
current lifecycle without rewriting commits, changelog entries, or prior
evidence.

When promoting a new bundled tool, create/use the `TOOLxxx` GitHub Issue for
live coordination and record the parent plus any durable formalized slices in an
owning repository project record. Exploratory tool architecture alone does not
reserve an identifier.
