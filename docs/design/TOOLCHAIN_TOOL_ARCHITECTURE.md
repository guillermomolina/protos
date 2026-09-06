# Protos Toolchain Tool Architecture

Status: selected architecture; non-normative

Related architecture and project material:

- `docs/design/PACKAGE_TOOL_ARCHITECTURE.md`
- `docs/design/TEST_TOOL_ARCHITECTURE.md`
- `docs/design/PACKAGE_DISTRIBUTION.md`
- `docs/design/PACKAGE_LOCKFILE_FORMAT.md`
- `docs/design/PROTOS_DESIGN_PHILOSOPHY.md`
- `spec/io/PROCESS_IO.md`

This document consolidates the architecture shared by the package tool, the
test tool, and plausible future toolchain facilities. It does not add Core
syntax, define a public plugin API, reserve new commands, or require every
future tool to be written in Protos. Concrete official bundled-tool
implementation is tracked separately through the non-normative `TOOLxxx`
project-work family.

The selected direction is:

> **Expose one public `protos` toolchain driver. Keep that driver and the host
> bootstrap small. Implement official higher-level tool policy primarily as
> exact toolchain-bundled Protos programs, using general capabilities and host
> mechanisms rather than tool-specific privileged runtime institutions.**

Package and test tooling are the first two concrete cases. Future facilities
such as benchmarking, formatting, linting, documentation, checking, coverage,
or profiling should be evaluated against the same boundary instead of each
inventing a new launch, authority, isolation, or host-integration model.

## Project work classification

The architecture boundary and the project-work identifier are intentionally
separate from CLI spelling.

Official toolchain-bundled developer tools use `TOOLxxx` when they are promoted
to concrete implementation work. The first assignments are:

```text
TOOL001  Package Tool
TOOL002  Test Tool
```

Future benchmark, formatter, linter, documentation, coverage, profiling, or
other official bundled tools should use later `TOOLxxx` identifiers rather than
creating one project-work family per command.

`CLIxxx` remains reserved for independently meaningful driver/terminal/REPL and
general command-dispatch mechanics. Thus `protos package` may dispatch to
TOOL001 and `protos test` may dispatch to TOOL002 without making package policy
or test policy CLI work.

`PERFxxx` remains project performance engineering. A future `protos bench` or
`protos profile` implementation would be a `TOOLxxx` tool even when PERF work
uses it to produce project evidence. Likewise, bundled tools are not `LIBxxx`
Standard Library modules merely because both ship in one toolchain.

TOOL001 is retrospective tracking because package-tool implementation predates
the family. Its legacy bootstrap, Filesystem 2A/2B, B006, and manifest Slice 3
labels remain historical truth and are mapped rather than renamed. TOOL002 is
the first tool promoted under the family before implementation begins.

## Terminology

Use the following conceptual terms consistently.

### Protos Toolchain

The complete selected Protos distribution needed to develop or execute Protos
software. Conceptually it may contain:

```text
toolchain
├── host/runtime/compiler machinery
├── Core
├── Standard Library
├── bundled-tool bootstrap/index
└── bundled tools
```

The exact physical installation layout remains an implementation detail.

### Protos Driver

The public command-line entry point, normally invoked as:

```text
protos ...
```

The driver is host-owned because something below ordinary Protos execution must
select the toolchain, initialize the runtime, and load an exact entry. Its
responsibility should remain dispatch/bootstrap/mechanism, not the policy of
each higher-level tool.

The driver may expose convenient top-level commands whose internal ownership is
not visible in the CLI spelling. For example, a public command such as
`protos add` may internally dispatch to the bundled package tool rather than
requiring a public `protos package add` namespace. The exact command surface is
not fixed by this document.

### Bundled Tool

An official tool distributed as an exact component of the selected toolchain.
Its policy and user-facing behavior should normally be implemented in ordinary
Protos code when existing language mechanisms can express them naturally.

A bundled tool is trusted distribution code, but it is not thereby Core, not
Standard Library API, and not an ambient privileged singleton.

Examples already selected or under explicit design are:

```text
package
test
```

Plausible future bundled tools include:

```text
bench
fmt
lint
doc
check
coverage
profile
```

This list is illustrative. It does not reserve names or commit the project to
implementing every entry.

### User Program

A normal Protos program or package selected by the user. It executes with only
its own explicitly provisioned authority. Authority used by a preceding tool
must not leak into the user program merely because both executions were started
by the same driver or hosted by the same JVM/native process.

### Extension / Plugin

Reserve these terms for a possible future mechanism that allows tooling not
shipped as part of the selected Protos toolchain to extend the command surface
or tooling behavior.

Official bundled tools are therefore **not plugins** merely because they are
loaded through a common dispatcher.

No third-party plugin/extension model is selected by this document.

## Why this common architecture is needed

The package-tool audit already establishes a bundled-tool bootstrap and a
policy/mechanism boundary. The test-tool audit independently arrives at the same
shape: a bundled Protos tool should own test policy while the host exposes only
general fresh-Process execution machinery.

Without a common architecture, each future facility could independently invent:

```text
its own Java command implementation
its own bootstrap path
its own capability injection rules
its own module/tool lookup convention
its own isolation boundary
its own host bridge
its own extension story
```

That would gradually turn the Java/native host into the de facto owner of the
Protos developer experience even when the behavior is naturally expressible in
Protos itself.

The common bundled-tool model prevents that drift while avoiding the opposite
mistake of making ordinary programs responsible for irreducible host bootstrap.

## Selected layering

Use the following conceptual layers:

```text
A. host/runtime kernel
B. toolchain driver and bundled-tool bootstrap
C. bundled Protos tool policy
D. general host-backed capabilities/mechanisms
E. separately authorized user-program execution
```

These are responsibility boundaries, not necessarily operating-system process,
JVM, package, or source-directory boundaries.

### A. Host/runtime kernel

The host owns only machinery that must exist below ordinary Protos execution or
that is already part of a general host boundary, including:

- start/select the Protos runtime and toolchain;
- locate Core, Standard Library, and the bundled-tool index;
- load an exact bundled-tool entry and its exact bundled closure;
- create Protos execution domains;
- bind explicit host-backed capabilities;
- perform mechanical module/source loading through already selected exact plans;
- expose genuinely host-specific observation/mechanism through general,
  separately designed boundaries.

The host should not acquire higher-level concepts merely because a bundled tool
needs them. In particular, avoid Java/native institutions such as:

```text
PackageManager
TestFramework
BenchmarkFramework
FormatterPolicy
LintPolicy
DocumentationPolicy
```

when those concepts can be ordinary Protos tooling built on general mechanisms.

### B. Toolchain driver and bundled-tool bootstrap

The public `protos` driver maps a command to an exact bundled tool or to another
small host-owned operation.

Conceptually, an immutable toolchain index may describe enough information to
load a tool without project package resolution:

```text
tool name
exact entry module/component
exact bundled closure
internal tool API generation
content/toolchain identity as required
```

The exact schema and serialization remain open.

The important invariant is:

> Selecting an official bundled tool must not search the user's project package
> graph or ambient package store for a package with a matching tool name.

Bundled tools are self-hosted as far as practical, but are not self-dependent on
the project package manager for bootstrap.

### C. Bundled Protos tool policy

The tool implementation owns policy that is naturally expressible in Protos.
Examples include:

```text
package resolution policy
test discovery/filtering/reporting
benchmark iteration/warmup/statistics policy
formatter transformation policy
lint rules and diagnostics
documentation assembly policy
coverage reporting policy
profiling presentation/aggregation policy
```

A tool may use ordinary internal Protos modules and toolchain-selected bundled
dependencies. Being official tooling does not justify adding new Core syntax,
reserved words, privileged global objects, or test/package/perf-specific
intrinsics.

### D. General host-backed capabilities and mechanisms

Some tools need capabilities ordinary pure Protos code cannot manufacture, for
example filesystem authority, network access, exact process execution, clocks,
or host profiling samples.

Those needs should be solved as general boundaries rather than as escape hatches
owned by one tool.

Conceptually:

```text
bundled package tool -> Filesystem / future Network / digest mechanism
bundled test tool    -> fresh exact Process execution + private streams
bundled bench tool   -> execution + suitable measurement/clock mechanism
bundled profile tool -> execution + profiling observation backend
```

A mechanism should be promoted only when its semantics and security boundary are
coherent beyond the first tool that needs it.

### E. Separately authorized user-program execution

Tool execution and application execution are separate authority domains.

Conceptually:

```text
Host
├── Tool Process
│   ├── exact bundled tool
│   └── tool-specific explicit capabilities
│
└── User Process
    ├── exact application/package execution plan
    └── application-specific explicit capabilities
```

They may run sequentially or concurrently inside one host runtime. "Process" in
this architecture follows the Protos semantic execution-domain concept and does
not imply one operating-system process per tool or per program.

## Bundled tools are ordinary Protos programs, with a special acquisition path

The key distinction is **how the tool is acquired**, not that its language
semantics are magical.

A bundled tool should normally:

- execute normal Protos source/modules;
- use ordinary objects, closures, errors, collections, Actors, Futures, and
  other applicable language mechanisms;
- receive explicit capabilities through bootstrap/arguments rather than hidden
  globals;
- fail through ordinary Protos/runtime error mechanisms where applicable;
- remain testable as Protos code;
- be package-shaped during development when useful.

Its special property is that the selected toolchain already knows the exact tool
entry/closure. The user does not first resolve that tool through the project
being operated on.

Therefore:

```text
implementation language of bundled tool = usually Protos
bundled-tool acquisition                 = exact toolchain bootstrap
project dependency acquisition           = package-system concern
```

These are deliberately different mechanisms.

## Bundled tools are not Standard Library

Do not expose official tools through `std:` merely because they ship with the
same distribution.

Conceptually keep:

```text
protos/lib/...
    Core / Standard Library source

protos/tools/...
    official bundled tool source/closures
```

Likewise, do not create a public `tool:` import namespace solely so user
programs can import tool implementation modules.

A future reusable library factored out of tooling should become a normal library
component only when it has a genuine application/library use case and an
appropriate public API independent of tool internals.

## Public command surface is independent from internal tool ownership

The CLI should optimize for coherent user experience, while the internal tool
model optimizes architectural ownership.

For example, all of these public forms are compatible with a single internal
package bundled tool:

```text
protos add foo
protos remove foo
protos update
```

Internally they may map conceptually to:

```text
bundled tool = package
command      = add/remove/update
arguments    = ...
```

Similarly:

```text
protos test
protos bench
protos fmt
```

may each select another bundled tool.

This document intentionally does not decide whether every package command is
flat, whether some commands use namespaces, or whether aliases exist. CLI design
may evolve without changing the bundled-tool responsibility boundary.

## Capability discipline

There must be no universal `ToolSuperPower` capability.

Each invocation receives only authority required by that operation. Illustrative
profiles include:

```text
package inspect/check
    project metadata read

package resolve/update
    project metadata read/write as required
    package metadata/store access as required
    network only when remote discovery is actually needed

package fetch
    exact lock read
    package-store write
    network

package publish
    source/artifact read
    network
    explicit credential authority

protos test
    package/source read
    fresh Process execution
    private streams
    optional isolated test resources

protos bench
    exact execution
    private streams
    measurement/clock facility

protos fmt
    source read
    source write only when formatting in place

protos profile
    exact execution
    profiling observation facility
```

Exact future capability surfaces remain subject to their own design/specification
work. This table records the least-authority direction, not APIs.

## Shared execution mechanisms should remain tool-neutral

`protos run`, `protos test`, benchmarking, coverage, and profiling can all need a
mechanical way to execute an exact Protos entry under controlled capabilities and
streams.

Do not solve each case with a separate host abstraction such as:

```text
TestExecutor
BenchmarkExecutor
CoverageExecutor
ProfileExecutor
```

Prefer one general exact-execution mechanism whose inputs are sufficient to
construct a fresh semantic Process, for example conceptually:

```text
exact module/package execution plan
entry
arguments/environment snapshot
explicit capabilities
standard streams
additional general observation hooks when explicitly enabled
        |
        v
fresh Protos Process
        |
        v
inert execution outcome / observation data
```

Tool-specific policy remains above this boundary.

## Package, test, and benchmark composition

The package tool may prepare or validate an exact package execution plan.
Execution-oriented tools should consume that result rather than reimplementing
package selection.

Conceptually:

```text
package preflight
      |
      v
exact PackageExecutionPlan
      |
      +----------------+----------------+
      |                |                |
      v                v                v
     run              test             bench
```

This does not require those tools to share one Protos Process or one authority
set. The exact plan is inert execution data; each consumer obtains its own
execution domain and only the capabilities appropriate to that operation.

Test and benchmark runners may share general orchestration helpers later, but
one must not become a mode of the other merely because both repeatedly execute
code. Their policy goals differ:

```text
test  -> correctness, isolation, expectations, deterministic reporting
bench -> measurement, warmup, iteration policy, statistical reporting
```

Shared implementation should follow genuine common mechanism, not erase the
semantic distinction.

## Profiling is a hybrid case

Profiling illustrates the policy/mechanism split particularly clearly.

A future `protos profile` experience may reasonably be a bundled Protos tool that
owns:

- command policy;
- selection/filtering;
- aggregation;
- human or machine-readable reports;
- attribution/display decisions that do not require privileged runtime state.

But CPU samples, allocation events, GC/compilation information, scheduler/Actor
telemetry, or Truffle/JVM/native observations may require host support.

That host support should be exposed as a general profiling/observation mechanism
with explicit authority and well-defined overhead rather than by moving the
entire profiler into Java.

Profiling must also respect the project principle "pay only for what you use": a
normal program that is not being profiled should not incur unnecessary
profiling state, synchronization, sampling, or event-retention cost.

## Third-party extensions are a separate future problem

Do not conflate bundled tools with plugins.

An externally installable tool ecosystem introduces additional questions that do
not exist for exact toolchain-bundled code:

```text
installation and discovery
publisher/content identity
supply-chain security
version resolution
compatibility with tool API generations
command-name collisions and shadowing
capability grants and user consent
upgrade/removal policy
reproducibility
sandboxing and credential access
```

Therefore the initial architecture should support official bundled tools without
requiring a public plugin system.

If third-party tooling is later justified, design it explicitly as an extension
layer above the stable bundled-tool/mechanism boundary. Do not silently make
`protos <unknown-command>` execute an arbitrary package from the user's store.

## Tool versioning and compatibility

Official bundled tools are selected by the toolchain installation, so their
implementation version normally evolves with that toolchain rather than through
project dependency resolution at invocation time.

A conceptual bundled-tool API generation may be useful for the narrow internal
host/tool handshake, but it should not become a broad privileged SDK containing
tool-specific policy.

Independent release/update channels for bundled tools remain open. Any future
channel must preserve exact acquisition, reproducibility, and bootstrap
independence rather than recreating the package-manager bootstrap cycle.

Third-party extension compatibility, if ever introduced, is a separate versioning
problem and must not constrain the initial bundled-tool API prematurely.

## Candidate tool families

The common model should be tested against substantially different tools before
new host concepts are accepted.

### Package

Already selected as the first bundled-tool architecture. It exercises manifest,
lock, filesystem/store, resolution, and eventually network/credential concerns.

### Test

Already selected architecturally as a bundled Protos test orchestrator using a
general fresh-Process execution mechanism. It exercises isolation, captured
streams, deterministic reporting, concurrency, and resource constraints.

### Benchmark

A strong candidate for a bundled Protos tool. It exercises repeated exact
execution, measurement, warmup, outer concurrency, and statistics without
justifying benchmark syntax in Core.

### Format / lint / check / doc

Strong candidates for ordinary bundled tooling where the necessary parser/source
and diagnostic facilities are available through general reusable boundaries.
They should not cause the host to own formatter rules, lints, or documentation
policy.

Whether some of these eventually share one implementation package or separate
public commands is open.

### Coverage

Likely a bundled reporting/orchestration tool over explicit execution
instrumentation. The instrumentation mechanism must be general enough not to
become a coverage-only runtime institution.

### Profile

Likely a bundled policy/reporting tool over a host-backed observation mechanism,
as described above.

## Anti-patterns

The architecture explicitly rejects the following defaults:

```text
one Java/native command implementation per developer tool

package/test/bench/profile-specific privileged global objects

bundled tools exposed as Standard Library solely because they ship together

project package resolution used to locate the package manager or another
official bundled tool

one universal capability that grants every tool filesystem/network/process/
credential/profiling authority

user programs inheriting authority from a tool that ran before them

public plugin discovery implicitly executing arbitrary packages for unknown
commands

separate execution/package-resolution semantics invented independently by
run/test/bench/profile
```

A specific future requirement may justify an exception, but it must be argued
against this common model rather than introduced accidentally.

## Decisions consolidated by this document

The following architectural directions are selected at the exploratory design
level:

```text
one public `protos` driver/front end                         YES
small host-owned dispatch/bootstrap                         YES
official higher-level tools primarily implemented in Protos YES
exact bundled acquisition independent of project resolution YES
bundled tools distinct from Standard Library                YES
public `tool:` import namespace required                    NO
official bundled tools considered plugins                   NO
tool-specific privileged runtime institutions by default    NO
explicit least-authority capabilities per invocation         YES
separate tool and user-program authority domains             YES
shared tool-neutral exact execution mechanisms               YES
package and test as instances of the same tool model         YES
third-party plugin/extension system selected now             NO
exact future command set fixed now                           NO
```

These are architecture decisions, not normative Protos language semantics.

## Decisions intentionally left open

The following require later focused design when a concrete need exists:

```text
exact bundled-tool index serialization
exact internal tool API/ABI representation and generation rules
final installed source/binary layout
exact public CLI command/namespace spelling
whether `run` itself becomes a bundled tool or remains a thin driver operation
shared internal modules among bundled tools
benchmark API, discovery, statistics, and measurement semantics
format/lint/check/doc source-model APIs
coverage instrumentation model
profiling/observation API and overhead guarantees
independent bundled-tool update channels
third-party extension/plugin installation and discovery
extension capability consent/sandboxing
command collision/shadowing rules
```

Open items must not be silently frozen by the first implementation slice that
happens to need them.

## Relationship to existing architecture documents

`PACKAGE_TOOL_ARCHITECTURE.md` remains the owner of package-specific decisions:
package policy, bootstrap cycle avoidance, package execution plans, package
capabilities, store/network/digest prerequisites, and package command evolution.

`TEST_TOOL_ARCHITECTURE.md` remains the owner of test-specific decisions:
Process-per-test isolation, captured streams, parallel scheduling, resource
constraints, expectation migration, and the open hard-timeout/worker question.

This document owns only the common toolchain vocabulary and responsibility
boundary that those designs share and that future tools should reuse.

Where a future tool reveals that this common model does not survive a realistic
use case, revise this architecture deliberately rather than forcing that tool
into an unsuitable abstraction.

## Repository evidence reviewed

This consolidation was derived from current repository architecture rather than
from a new language semantic proposal.

### AGENTS.md read

- `AGENTS.md`
- `spec/AGENTS.md`

### docs read

- `docs/design/PACKAGE_TOOL_ARCHITECTURE.md`
- `docs/design/TEST_TOOL_ARCHITECTURE.md`
- `docs/design/PACKAGE_LOCKFILE_FORMAT.md` (bundled-tool/frontend architecture context)
- `docs/design/PROTOS_DESIGN_PHILOSOPHY.md` / repository-root synchronized design philosophy

### spec read

- `spec/io/PROCESS_IO.md` (Process execution-domain and explicit authority boundary)

### Implementation/status evidence inspected

- `src/main/java/com/guillermomolina/protos/cli/ProtosCli.java` via repository search
  evidence showing the existing package bundled-tool dispatch
- root `CHANGELOG.md` evidence for the first package bundled-tool bootstrap slice
- `pom.xml` current implementation coordinate; unchanged because this is a
  documentation-only architecture consolidation

No normative specification change, implementation change, implementation-status
transition, or implementation-version increment is introduced by this document.
