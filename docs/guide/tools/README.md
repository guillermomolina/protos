# Bundled Tools

Protos ships developer tooling through one public `protos` driver, but not every
driver command is the same kind of thing.

A **bundled Tool** is an official tool distributed as an exact part of the
selected Protos toolchain. Its higher-level policy is implemented in ordinary
Protos code when the language can express that policy naturally, while the host
runtime supplies only the bootstrap and general mechanisms that must exist below
ordinary Protos execution.

That separation is deliberate. It keeps package management, testing and future
developer-tool policy out of the language itself without pushing all developer
experience into Java, Truffle, a particular operating system, or another current
implementation detail.

The maintained architecture behind this model is
[`TOOLCHAIN_TOOL_ARCHITECTURE.md`](../../design/TOOLCHAIN_TOOL_ARCHITECTURE.md).

## The four boundaries to keep separate

When working with Protos tooling, it helps to distinguish four different layers.

| Layer | What it owns | What it does **not** become |
|---|---|---|
| Language / Core | Protos syntax, evaluation and Core semantic mechanisms | A package manager or test framework |
| Standard Library | Reusable application/library modules imported through `std:` | Bundled command policy |
| Bundled Tools | Official developer-tool policy selected by the toolchain | Core semantics, Standard Library API, or ambient globals |
| Host/runtime integration | Exact bootstrap, execution domains and explicit host-backed mechanisms/capabilities | The policy of the Package Tool or Test Tool |

A bundled Tool can use ordinary Protos objects, closures, Errors, collections,
modules, Futures and Actors just like other Protos code. What is special is its
**acquisition path**: the installed toolchain already knows exactly which Tool it
is loading.

That is different from resolving an application dependency through a project's
manifest, lockfile or package graph.

## Why Tools do not bootstrap through the project package graph

Some developer tools must exist before project package resolution can be trusted
or even performed.

The Package Tool is the obvious example: making the Package Tool itself depend on
resolving a package named "package" from the project being inspected would create
a bootstrap cycle. The same principle applies to the Test Tool, which may need
package/execution information while remaining part of the exact installed
toolchain.

So the driver selects a bundled Tool from the toolchain distribution directly.
The current resolver does not search project manifests, lockfiles, package
stores, working directories or ambient module paths to acquire that Tool.

This gives the toolchain a stable starting point:

```text
protos driver
    |
    +-- exact bundled Tool
    |       |
    |       +-- ordinary Protos policy
    |       +-- selected std: modules
    |       `-- explicit capabilities/mechanisms
    |
    `-- separately authorized user/package execution
```

Tool authority must not leak into a user program merely because the same driver
or host process launched both.

## Current bundled Tools

The current distribution contains two actual bundled Tools under
[`protos/tools/`](../../../protos/tools/):

| Public entry | Bundled Tool | Purpose |
|---|---|---|
| `protos package ...` | Package Tool (`TOOL001`) | Package metadata, resolution/lock/workspace policy and related package operations as they are published |
| `protos test ...` | Test Tool (`TOOL002`) | Protos-language test planning, execution, expectations, resource-aware scheduling and reporting |

The directory [`protos/tools/shared/`](../../../protos/tools/shared/) is shared
private toolchain support. It is **not** a third bundled Tool.

The Package Tool is still evolving under
[`TOOL001`](https://github.com/guillermomolina/protos-project-docs/blob/main/docs/project/work/TOOL001/TOOL001_PACKAGE_TOOL.md), so this overview
does not pretend that every future package operation already exists.

The Test Tool's durable lower architecture is closed under
[`TOOL002`](https://github.com/guillermomolina/protos-project-docs/blob/main/docs/project/work/TOOL002/TOOL002_TEST_TOOL.md). A separate current
work item is extending which repository-owned Protos corpora the Test Tool can
reach. Until that implementation is published, an architectural decision about a
future suite graph is not the same thing as current executable user behavior.

## What is *not* a bundled Tool

The public `protos` driver also owns or exposes operations that are not bundled
Tools.

In the current CLI, that includes the REPL, direct source execution, `-e`,
`protos run`, `protos debug`, `protos language-server`, help and version
handling. Their existence under the same executable does not make them Package
Tool or Test Tool policy.

This distinction matters because CLI spelling is a user-interface decision,
while Tool ownership is an architectural responsibility boundary. A future
driver could change command organization without moving package or test policy
into Core or the host runtime.

The current dispatch boundary can be inspected in
[`ProtosCli.java`](../../../src/main/java/com/guillermomolina/protos/cli/ProtosCli.java).

## Tool-local modules are private implementation structure

Bundled Tools have a confined module closure. Today that closure uses internal
mechanisms including:

- `self:` for modules local to one bundled Tool;
- `tool-shared:` for explicitly configured private support shared inside the
  bundled-tool/toolchain closure; and
- `std:` for normal selected Standard Library modules.

The important user-facing rule is **not** the spelling of those private
mechanisms. It is that bundled Tool internals are not silently promoted into a
public application import API.

In particular:

- `self:` inside a Tool is scoped to that Tool's exact closure;
- `tool-shared:` is private bootstrap/toolchain sharing, not an ordinary
  user-program namespace;
- internal canonical identities such as host-side bundled-tool ModuleKeys are
  not public import syntax; and
- application code should not depend on Tool implementation modules simply
  because they ship in the same distribution.

If reusable functionality later deserves a real application-facing API, it
should be designed and published as an appropriate library surface rather than
exposing Tool internals by accident.

The current confinement mechanism is implemented by
[`ProtosBundledToolModuleResolver`](../../../src/main/java/com/guillermomolina/protos/execution/ProtosBundledToolModuleResolver.java).

## Policy belongs in Protos; irreducible mechanism stays below it

The preferred division is:

```text
higher-level Tool policy
    -> ordinary bundled Protos code

general mechanism that Protos cannot manufacture itself
    -> explicit host/runtime boundary
```

For example, the Test Tool can own test expectations, deterministic aggregation
and scheduling policy in Protos while receiving a host-backed mechanism capable
of executing an exact test in a fresh semantic Process with controlled streams
and capabilities.

Similarly, the Package Tool can own package/lock policy while using confined
filesystem authority supplied by the host.

This is intentionally different from creating privileged host institutions such
as a Java `TestFramework` or `PackageManager` that become the real owners of
Protos-level policy.

## Capabilities are explicit, not ambient Tool privilege

Being bundled does not grant a Tool unlimited ambient authority.

A Tool invocation should receive only the capabilities needed for that
operation: for example, confined filesystem access, exact execution, private
streams, network access when a package operation genuinely needs it, or a
resource capability selected for one test attempt.

There is no general "Tool superpower" that all bundled code automatically
receives.

This is also why Tool execution and the program being operated on are separate
authority domains. The Tool may inspect or prepare execution, but the program
runs with its own explicitly provisioned authority.

## Errors and diagnostics

Bundled Tool policy should use ordinary Protos failure mechanisms where
applicable. The public driver is still responsible for the lower bootstrap and
runtime boundary, so a user may see a distinction between:

- an ordinary guest/Tool Error produced by Tool policy; and
- a driver/bootstrap/runtime failure that prevented the Tool from operating
  normally.

That distinction must be documented from the public contract of each Tool rather
than from incidental Java exception classes.

The detailed Test Tool guide owns the exact public result, diagnostic and exit
status contract. This overview intentionally does not duplicate it.

## Portability rule

A current implementation may use Java, Truffle, JVM processes or particular host
filesystem APIs internally. Those facts are not automatically part of the
Bundled Tool contract.

Public Tool documentation should describe logical behavior:

- exact toolchain acquisition;
- Protos-owned policy;
- explicit capabilities;
- semantic Process isolation where contracted;
- deterministic observable results;
- portable logical identities.

It should not promise JVM class names, Truffle nodes, physical installation
paths, host threads or operating-system processes unless a separate public
contract deliberately makes one of those observable.

## A practical mental model

When you type a Tool command, read it as:

```text
"Ask the Protos driver to start this exact official Tool,
 give it only the mechanisms it needs,
 and let the Tool's Protos code own the higher-level policy."
```

That answers the main boundary question:

> **A Bundled Tool is part of the toolchain, but it is not part of the language.**

Continue with the maintained [Test Tool guide](test-tool.md). DOC005-B covers
current `protos test` corpus/expectation behavior, isolated execution and
`--jobs`; later DOC005 slices add the already-published resource and final
result/diagnostic contracts without mixing them into this Tool-model overview.
