# Protos Roadmap

This is a human-facing, non-normative view of project direction.

It is intentionally directional rather than an exact live status view.
Current work state/readiness lives in the repository's
[GitHub Issues](https://github.com/guillermomolina/protos/issues); the
[`Protos Development` Project](https://github.com/users/guillermomolina/projects/1)
is the automatically derived dashboard/scheduling view. Priority and roadmap
placement remain advisory planning metadata in that Project.
This roadmap does not promise dates, compatibility, or that every item will be
implemented exactly as described here.

The Core v0.1 specification remains a draft and the reference implementation is
under active development.

## What you can explore today

The repository contains a working reference implementation and CLI, executable
examples and tutorials, a growing Standard Library, and substantial coverage of
the Core object, closure, error, I/O, concurrency, Future/Task, and Actor model.

The Package Tool and Test Tool are being developed as bundled Protos tools rather
than as unrelated external systems.

See the [README](README.md) for the current build/run path, the
[`Protos Development` Project](https://github.com/users/guillermomolina/projects/1)
for the derived live scheduling/status dashboard, and the
[implementation registry](docs/project/registries/IMPLEMENTATION_STATUS.md) for durable
historical/closure evidence.

## Now

Current project work is focused on making the existing language coherent,
testable, understandable, and increasingly self-hosted:

- finish remaining Core v0.1 implementation/conformance work;
- complete the bundled Package Tool and Test Tool foundations;
- expand the programming guide and executable learning material;
- continue Standard Library work without growing the Core unnecessarily;
- improve Truffle/Graal compilability and performance evidence; and
- make contribution, support, and project-direction entry points friendlier to
  people outside the original development workflow.

## Next

The next adoption-oriented milestone is to make trying Protos require much less
tooling knowledge:

- build a relocatable downloadable distribution so a new user does not need
  Maven merely to run a first Protos program;
- validate that extracted distribution outside the repository checkout;
- publish transient CI snapshot artifacts separately from selected public
  releases;
- cut user-facing GitHub pre-releases only at coherent, explicitly selected
  milestones rather than for every implementation snapshot;
- establish a simple installation and upgrade path;
- publish several non-trivial, task-oriented example programs;
- identify a small set of real `good first issue` contributions;
- make package and test workflows comfortable for ordinary projects; and
- keep improving error messages, diagnostics, and newcomer documentation.

These are directional priorities, not a promise that they will land in this
exact order.

The selected snapshot-versus-release and publication policy is documented in
[`docs/project/work/DIST001/DIST001_RELEASE_POLICY.md`](docs/project/work/DIST001/DIST001_RELEASE_POLICY.md).

## Later

As the language and toolchain mature, likely areas include:

- broader platform/package-manager distribution;
- a practical package ecosystem and registry workflow;
- editor integration and language-aware tooling;
- formatter, linting, coverage, profiling, and other bundled developer tools
  where they justify their own design;
- stronger release engineering and compatibility policy; and
- larger real-world programs that exercise the language outside the conformance
  corpus.

## How to influence the roadmap

Questions, use cases, and exploratory proposals belong in
[GitHub Discussions](https://github.com/guillermomolina/protos/discussions).

Concrete bugs and sufficiently scoped actionable work belong in
[GitHub Issues](https://github.com/guillermomolina/protos/issues).

The roadmap should evolve from evidence: what users try, what contributors find
difficult, what the specification requires, and what real programs expose.
Popularity alone does not define Protos design, but real-world use is valuable
design evidence.
