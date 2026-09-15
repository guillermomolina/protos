# DOC006 — Try Protos and Getting Started guide

GitHub coordination: Issue [#524](https://github.com/guillermomolina/protos/issues/524), labelled `family:DOC`.

Status: **IN_PROGRESS**

Validation class by default: **GOVERNANCE_DOCUMENTATION_ONLY**

## Purpose

DOC006 creates a clear, maintained newcomer path for trying the published Protos
implementation without requiring users to understand the implementation build
or repository-development workflow first.

The work owns two supported onboarding paths:

1. the Protos Dev Container as the recommended quickest path; and
2. manual installation from an official Protos release distribution.

DOC006 is intentionally separate from DOC001. DOC001 owns the broader
programming-documentation progression; DOC006 owns runnable onboarding and
environment setup.

## Authority

User-facing claims in DOC006 must be grounded in current published authority:

- the current Protos GitHub Release and portable distribution;
- the release-owned runtime/toolchain version contract;
- the public `protos` launcher and CLI;
- [`guillermomolina/protos-devcontainer`](https://github.com/guillermomolina/protos-devcontainer);
- [`guillermomolina/protos-vscode-extension`](https://github.com/guillermomolina/protos-vscode-extension);
- canonical examples and tutorials in this repository; and
- the maintained Programming Guide navigation.

The guide is non-normative. It must not redefine language semantics or present
an implementation detail as a language contract.

## Documentation shape

DOC006 publishes:

```text
docs/guide/
  00-try-protos.md   # runnable onboarding guide
  README.md          # Programming Guide navigation entry

README.md            # repository landing-page entry
```

The durable work record remains under `docs/project/work/DOC006/`.

## Slice ledger

| Slice | Status | Scope |
|---|---|---|
| DOC006-A | IN_PROGRESS | Publish `docs/guide/00-try-protos.md` with the Dev Container and manual-distribution paths. |
| DOC006-B | IN_PROGRESS | Make the guide discoverable from `docs/guide/README.md` and the root `README.md`. |
| DOC006-C | READY_AFTER_A_B | Reconcile links, commands, release/runtime claims and close DOC006. |

## Dev Container path

The recommended path must explain:

- required host prerequisites;
- cloning `guillermomolina/protos-devcontainer`;
- reopening the repository in the Dev Container;
- verifying the selected Protos version with `protos --version`;
- running at least one canonical example from the bundled `examples/` snapshot;
- creating and running a user-owned `.protos` file;
- using the official Protos VS Code extension for supported editor/debugging
  integration; and
- what the container already provides so users do not install duplicate runtime
  components inside it.

The devcontainer's `examples/` tree is a convenience snapshot. Canonical example
ownership remains in the main Protos repository.

## Manual-distribution path

The manual path must teach the official release distribution rather than making
a raw implementation JAR the primary user workflow.

It must explain:

- where to obtain the current official release;
- that the matching published GraalVM/JDK contract matters;
- integrity verification when supplied by the release;
- preserving the extracted distribution layout;
- making the distribution launcher available to the shell;
- verifying with `protos --version`; and
- running a first `.protos` source file.

A low-level `java -jar` route is documented only if current release authority
explicitly supports it as a user-facing path.

## Boundaries

DOC006 does not:

- change language, runtime, CLI or package semantics;
- invent a package manager or installation convention;
- claim a prerelease is stable;
- promise arbitrary Java/GraalVM compatibility;
- duplicate canonical examples as independently maintained guide sources;
- redefine VS Code extension behavior;
- turn `self:` or other resolver behavior into an undocumented relative-import
  mechanism; or
- duplicate the guide manually into the website.

The website may materialize the canonical Protos documentation under separate
WEB work.

## Validation policy

DOC006 is documentation/governance-only by default.

Validation should confirm:

- first-use commands against the selected published release/devcontainer;
- `protos --version`;
- current repository and extension names;
- canonical example paths;
- current release archive/runtime claims;
- maintained guide/root navigation; and
- repository-relative links.

Pure DOC006 publication does not require a Protos implementation/specification
version bump or the executable Maven test suite.

## Closure rule

DOC006 closes when:

1. `docs/guide/00-try-protos.md` is published;
2. both supported onboarding paths are documented from current authority;
3. the first runnable Protos path is clear and reproducible;
4. the guide is discoverable from the Programming Guide and root README;
5. release/runtime/devcontainer/extension claims are current;
6. canonical examples remain owned by the main Protos repository; and
7. no competing documentation authority has been introduced.
