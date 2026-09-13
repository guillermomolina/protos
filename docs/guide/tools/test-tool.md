# Test Tool

The **Test Tool** is the bundled Protos Tool behind `protos test`.

It owns higher-level testing policy in Protos: loading the retained corpus plans,
interpreting expectations, deciding bounded outer test-case admission, aggregating
results in a stable order, and producing Test Tool progress/results. The host
runtime supplies lower mechanisms such as executing one already-selected source
in an isolated semantic Process.

For the general Tool/Core/Standard-Library/runtime boundary, start with
[Bundled Tools](README.md). The durable implementation/work authority is
[`TOOL002`](../../project/work/TOOL002/TOOL002_TEST_TOOL.md).

> **Current-surface note:** this chapter documents the executable Test Tool that
> exists now. `TOOL005` is separately extending repository-wide corpus routing.
> Its ratified suite-graph architecture is not described here as though it were
> already an invokable user feature.

## First use

The ordinary command is:

```text
protos test
```

The Test Tool currently executes the Protos test corpora selected by the
installed/repository toolchain. It does **not** recursively discover arbitrary
`*.protos` files from the current working directory, and the current CLI does not
take a user-supplied test-root or manifest path.

Today the bundled entry point executes four explicit plans, in this logical
order:

| Phase | Current corpus root | Purpose |
|---|---|---|
| `main` | `protos/tests/conformance` | Primary language/runtime/library conformance corpus retained by the Test Tool |
| `actor` | `protos/tests/conformance/actor` | Actor-specific corpus with its selected module environment |
| `group` | `protos/tests/conformance/group` | Actor Group-specific corpus with its selected module environment |
| `package-toml` | `protos/tests/package-tool/toml-syntax` | Retained Package Tool TOML syntax corpus |

This four-plan routing is a **current implementation boundary**, not a design
recommendation for future corpus composition. [`TOOL005`](https://github.com/guillermomolina/protos/issues/468)
owns the migration away from hard-coded repository corpus roots.

## The corpus is manifest-driven

Each current plan has a `manifest.tsv`. For the ordinary conformance-style
plans, every non-empty, non-comment line has three tab-separated fields:

```text
path<TAB>expectation<TAB>expected
```

Blank lines and lines beginning with `#` are ignored.

The path is a confined relative test path. The current parser rejects empty path
components, `.` and `..` components, backslashes and NUL. A manifest therefore
selects an exact file inside the corpus rather than giving the Tool an ambient
host path to resolve freely.

The primary retained manifest can be inspected at
[`protos/tests/conformance/manifest.tsv`](../../../protos/tests/conformance/manifest.tsv).
The manifest parser and inert plan representation live in
[`Manifest.protos`](../../../protos/tools/test/Manifest.protos).

The Package Tool TOML syntax corpus retains a smaller two-field format:

```text
path<TAB>retained-expectation
```

where the current retained values are `true` and `error`. The Test Tool maps
those rows into its ordinary internal expectation model before execution.

These manifest formats are the current repository corpus contract. They should
not be read as a promise that all future third-party/project tests will be
registered through exactly this file format.

## A real minimal retained case

The repository contains this exact source at
[`integer/add-small.protos`](../../../protos/tests/conformance/integer/add-small.protos):

```protos
1 + 1
```

Its manifest row is:

```text
integer/add-small.protos	integer	2
```

Read that row as:

1. execute exactly `integer/add-small.protos` in the case's isolated execution;
2. interpret the resulting observation using the `integer` expectation policy;
3. pass only if the completed value is the integer `2`.

A test case is therefore not required to call a special assertion function. For
this retained corpus model, the **program result plus the manifest expectation**
define success or failure.

## Expectation model

The Test Tool compares each supported manifest expectation against the captured
execution observation. Common retained forms include:

| Expectation | Meaning of `expected` |
|---|---|
| `boolean` | `true` or `false` |
| `null` | no payload (`-` is retained convention) |
| `integer` | decimal ordinary Integer text |
| `fixed-integer` | fixed-width family and value, for example `UInt8:255` |
| `float-bits` | exact 16-hex-digit binary64 bit pattern |
| `float-nan` | NaN result; payload/sign are not made portable semantics |
| `error` | execution must fail with an ordinary guest Error |
| `error-parent` | guest Error whose parent matches the retained prototype name |

The current retained corpus also has specialized expectation families for
Futures and error-identity/liveness checks, including resolved Future values,
Future cancellation/error states and selected inspection-based cases. Those are
implemented by the same runner rather than by a second test framework.

The current expectation vocabulary is defined by
[`Runner.protos`](../../../protos/tools/test/Runner.protos). When adding retained
corpus rows, use an expectation that the current runner already recognizes;
documentation is not authority to invent a new expectation kind.

## One fresh semantic Process per test execution

Each selected test execution runs in a **fresh semantic Protos Process**. The
source is parsed/executed inside that fresh Process context, and the Process is
terminated after the execution observation is obtained.

That is the isolation boundary that matters publicly. One case must not depend
on mutable Process/RootActor state left by a previous case.

The lower exact-execution mechanism is deliberately test-neutral; it receives an
already-selected source and returns captured execution evidence. The Test Tool,
not that host mechanism, owns manifests, expectations, scheduling and reporting.

Current mechanism references:

- [`ProtosFreshProcessExecutor`](../../../src/main/java/com/guillermomolina/protos/execution/ProtosFreshProcessExecutor.java)
  creates one fresh semantic Process for an exact source;
- [`ProtosCapturedProcessExecution`](../../../src/main/java/com/guillermomolina/protos/execution/ProtosCapturedProcessExecution.java)
  captures the execution outcome and private standard streams.

Those Java class names explain the current implementation. They are **not** a
portable Test Tool API.

## Standard output is private to the case execution

A test execution receives private standard-stream backends. Its `stdout` and
`stderr` bytes are captured as part of that execution's evidence instead of
sharing one writable stream with concurrently running cases.

This matters for parallel testing: two tests may physically overlap without
interleaving their guest output into one semantic test stream.

The Test Tool has its own progress/reporting channel. Do not use guest output
timing as a synchronization or result-order contract.

## `--jobs`: bounded outer test-case parallelism

By default, the Test Tool uses logical capacity `1`:

```text
protos test
```

To allow up to four outer test-case executions to be in flight in one scheduling
window:

```text
protos test --jobs 4
```

`--jobs N` controls **Test Tool case admission**, not the concurrency mechanisms
inside one test. A test remains free to use ordinary Protos Futures, Actors,
Actor Groups or other published concurrency semantics inside its own fresh
Process.

The current option contract is:

- `N` is required after the exact separate token `--jobs`;
- `N` must contain only decimal digits and represent an Integer greater than
  zero;
- if `--jobs` is absent, the value is `1`;
- the selected option may occur at most once; duplicate or missing selected
  values fail option parsing;
- there is currently no separate documented numeric maximum below the ordinary
  Integer domain; practical admitted concurrency may be lower when other Test
  Tool constraints apply.

For compatibility with the existing TOOL002 argument policy, only the exact
separate-token form is selected. In particular:

```text
--jobs 4     # selected
--jobs=4     # currently ignored, not an alias for --jobs 4
```

Other unknown tokens are currently ignored by the Test Tool projection layer,
including `--`. That permissive compatibility behavior should not be used as a
namespace for scripts. Prefer only documented exact options.

The current parser is
[`Options.protos`](../../../protos/tools/test/Options.protos).

## Determinism with `--jobs > 1`

Increasing `--jobs` changes how many independent test cases may execute
concurrently; it does not make physical completion order the public result
order.

The runner admits bounded windows, waits for the admitted executions, then
projects their observations in the original TestPlan/manifest order. The stable
case order therefore comes from the plan, even when physical executions finish
in another order.

Conceptually:

```text
manifest / TestPlan order
    case A   case B   case C   case D
       \       |        /       /
        \--- physical overlap --/
                 |
                 v
       observations projected back
          in TestPlan order
```

This is why CI or tooling that consumes Test Tool results should not infer
meaning from which physical case happened to finish first.

The outer scheduler belongs to bundled Protos policy in
[`Runner.protos`](../../../protos/tools/test/Runner.protos). Current host code
only transports already-admitted exact executions; host thread topology is not
part of the portable `--jobs` contract.

## Progress is separate from case completion order

The current Test Tool reports phase progress independently from guest case
streams. Phases are named `main`, `actor`, `group` and `package-toml`, and the
progress layer can report failed cases plus phase/invocation summaries.

Detailed failure classes, final diagnostics and public exit-status meaning are
covered by the DOC005 results/diagnostics slice. The important fundamental rule
here is simpler:

> parallel execution may change physical timing, but it must not redefine the
> plan's logical ordering or make guest stream interleaving the reporting model.

The current progress policy is implemented in
[`Progress.protos`](../../../protos/tools/test/Progress.protos).

## What `protos test` does not mean yet

With the current published executable surface:

- it does not recursively discover every repository `*.protos` test;
- it does not select arbitrary current-directory test roots;
- it does not expose the D122 suite graph as a public CLI selection surface;
- `--jobs` does not mean "number of Actors" or "number of host threads";
- manifest ordering remains semantically relevant even when cases overlap.

Repository-wide Test Tool corpus routing is active work under TOOL005. This
guide should be updated when that owning implementation is actually published,
not when an architectural candidate merely exists.

## Where to go next

This chapter establishes the Test Tool fundamentals: current corpus selection,
manifest expectations, isolated execution, private captured output, bounded
outer parallelism and deterministic logical ordering.

Resource requirements/catalogs and provider/profile semantics are a separate
layer on top of this model. Result classes, diagnostics, exit status and CI
recipes are also documented separately so those contracts can evolve without
turning this fundamentals chapter into an implementation-history ledger.
