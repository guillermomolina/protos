# Contributing to Protos

Thank you for considering a contribution to Protos.

Protos is an experimental programming-language project. Contributions are
welcome across the language specification, reference implementation, standard
libraries, tools, tests, documentation, benchmarks, examples, and project
infrastructure.

The project cares about the quality and evidence behind a change, not about
whether every line was typed manually.

## Your first contribution — about 15 minutes

For a small, already-scoped Issue, you should not need to learn the project's
entire internal governance before opening a useful Pull Request. The normal
external-contributor path is:

1. **Fork and clone Protos.** Fork the repository on GitHub, then clone your fork
   and keep the main repository as `upstream`:

   ```sh
   git clone https://github.com/<your-user>/protos.git
   cd protos
   git remote add upstream https://github.com/guillermomolina/protos.git
   git fetch upstream
   ```

   If you already have a clone, just make sure you have the current
   `upstream/main` before branching.

2. **Use the devcontainer when convenient.** The repository includes a
   `.devcontainer/` environment so you can use the project's expected toolchain
   without recreating it manually. It is helpful, not a requirement for every
   documentation-only contribution.

3. **Pick a bounded Issue before doing broad implementation work.** A `Ready`
   Issue with no assignee is normally available; `good first issue` is the best
   starting point for a first contribution. Comment on the Issue if you need a
   maintainer to assign it to you.

4. **Create a narrow branch from current `upstream/main`:**

   ```sh
   git fetch upstream
   git switch -c issue-N-short-name upstream/main
   ```

5. **Read the authority that applies to your change, then make one narrow
   change.** The Issue acceptance criteria and the applicable `AGENTS.md` files
   tell you the operational constraints. If the change would alter language
   semantics, read the owning normative text under `spec/` first; if the desired
   behavior is unspecified or contradictory, stop and raise the design question
   instead of choosing semantics in the implementation.

6. **Add or update the right tests when behavior changes.** When observable
   behavior can be tested in Protos source, prefer Protos-owned conformance or
   regression coverage. Use Java tests for host/JVM machinery and boundaries
   that genuinely belong on the Java side.

7. **Validate according to impact.** For an executable contribution, run
   focused tests for the changed behavior and use the repository's canonical
   integrated suite (`make test`) when broader/full validation is required by
   the current repository policy. For a documentation-only contribution,
   inspect links and commands and run the applicable static/integrity checks;
   unrelated executable tests are not automatically required. In either case,
   inspect the final diff:

   ```sh
   git diff --check
   git diff --stat
   git diff
   ```

8. **Commit, push, and open the Pull Request using the repository template.**
   Explain what changed and what validation passed. Use `Closes #N` only when
   the PR fully satisfies that Issue. If AI materially produced or shaped the
   contribution, disclose that briefly; AI-assisted and fully AI-generated
   contributions are welcome. Prompt transcripts, token logs, and
   human-authored percentages are not required.

The ordinary local validation entry points remain `make test`, `make check`,
and `make test-native`. `make test` still runs Java followed by Protos, including
local slow-test telemetry and reduced-contention confirmation. CI timing remains
advisory; test outcomes determine acceptance.

GitHub Actions runs independent `prepare`, `compilerability`, `java-parallel`,
`java-serial`, `protos-suite`, `protos-foreign`, and `native-image` jobs followed by
`aggregate`. Preparation compiles test classes without running them and publishes
a revision-bound Java selection plan. Four initial parallel Java partitions use
two JUnit class workers per runner. The six Makefile-owned serial classes run in
the separate serial lane. Compilerability retains all of `make check`; Native
Image retains all of `make test-native`. Each Protos lane packages its own CLI:

```sh
make prepare-protos-tests
make test-protos-suite
# On an independent worker, after the same preparation:
make test-protos-foreign
```

The Protos suite still runs through `bin/protos test`; the existing foreign
provider harness retains its FFM checks. Protos Test Tool sharding is deferred.
The CI jobs do not additionally execute `make test` or its Java/Protos aggregates.

To reproduce a Java partition locally:

```sh
make prepare-java-test-shards JAVA_TEST_SHARD_COUNT=4
make test-java-parallel JAVA_TEST_SHARD_COUNT=4 JAVA_TEST_SHARD_INDEX=1 JAVA_TEST_JOBS=2
```

`JAVA_TEST_SHARD_COUNT`, `JAVA_TEST_SHARD_INDEX`, and `JAVA_TEST_SHARD_DIR` control
planning and selection; `JAVA_TEST_JOBS` controls JUnit concurrency independently.
`PROTOS_TEST_JOBS` controls the existing Test Tool workers. CI defaults live in the
workflow's top-level environment (four partitions, two Java/Protos workers).
Changing that initial CI configuration also requires updating its toolchain
contract and regression fixtures.

Plans and exact class-path inclusion files live under `target/java-test-shards`.
They partition lexicographically sorted compiled fully qualified identities by
position modulo shard count; source filenames are not authoritative. Workers
reject stale inventories, revisions, plan digests, and inclusion files. Remove
an old dedicated report directory before repeating a partition invocation:
report directories must initially be empty. Distributed XML and `result.json`
live under `target/java-test-reports/parallel-<index>` or `serial`. The serial CI
lane uses `JAVA_SERIAL_REPORTS=target/java-test-reports/serial` and the prepared
plan. This is also the serial report default when the shard count exceeds one;
ordinary local serial execution keeps its existing report location.

Candidate selection coverage is distinct from observed execution coverage.
Surefire owns discovery and execution; a selected compiled candidate may have no
executable JUnit methods. XML evidence records executed invocations, nested class
ownership, actual skips, failures and errors separately. A missing report is
never a skip. Candidates without testcase evidence stay visible as unresolved
and block acceptance. Reconcile these against authoritative Surefire discovery
or an unpartitioned reference execution before claiming equivalent coverage;
there is no manually maintained helper exemption or automatic coverage waiver.

The always-running aggregate checks every required dependency and exactly one
revision/digest-bound result per Java partition plus serial evidence. Missing,
failed, cancelled, skipped, duplicated or inconsistent evidence fails acceptance.
The unchanged Protos Makefile job result remains its authoritative gate. The
Actions summary contains lane status, candidate coverage and invocation/skip
counts; raw XML, evidence and logs are available in revision-named `java-plan-*`,
`java-evidence-parallel-*`, `java-evidence-serial-*`, and lane diagnostic artifacts.
Each lane retains a 120-minute timeout and records elapsed time and runner
resources without adding a test-duration admission threshold.

The Maven dependency cache is infrastructure, not validation. Every Maven job
restores it through `.github/actions/maven-cache-restore`, which computes the
key once, before validation, from the tracked POM paths and contents. Only a
successful `prepare` job saves the cache, in an explicit non-blocking step that
reuses that key; no post-job step recomputes it over a populated workspace. A
cache service failure therefore cannot turn successful repository tests into a
failed run, and it never masks a failed one: failure tolerance is confined to
the cache steps and `tools/verify_toolchain.py` rejects it anywhere else.

### Diagnostic CI

`CI` (`.github/workflows/tests.yml`) is the mandatory acceptance workflow.
`CI Diagnostics` (`.github/workflows/ci-diagnostics.yml`) is an isolated,
non-authoritative aid for reproducing individual CI problems. It never
aggregates, never claims repository coverage, never publishes anything and
never substitutes for `CI`. Its artifacts are named
`ci-diagnostics-<lane>-<revision>` (the Java shard lane adds its index).

GitHub offers manual `workflow_dispatch` only once a workflow exists on the
default branch. Before `CI Diagnostics` is published, push the same candidate
commit to the exact diagnostic branches instead; this needs no extra commits and
does not touch `main`:

```sh
git push origin HEAD:refs/heads/ci-validation
git push origin HEAD:refs/heads/ci-validation-http
git push origin HEAD:refs/heads/ci-validation-java-2
```

- `ci-validation` runs the cache lane: shared restore, dependency resolution,
  the ordinary explicit Maven save, and a small run-specific marker round trip
  (save, local removal, exact-key restore without fallback keys, content
  check) under a separate `protos-ci-diagnostic-roundtrip-` namespace. A
  successful cache action status is not proof of storage; the round trip
  verifies actual storage and retrieval. Cache errors fail this lane visibly.
- `ci-validation-http` repeats the HTTP root-actor failure test 20 times.
- `ci-validation-java-2` reruns the original concurrent Java shard 2 with its
  two JUnit workers. The focused HTTP lane does not establish equivalence with
  that shard.

No other branch, pull request or `main` push runs diagnostics. Once the workflow
is on the default branch, a manual dispatch selects one fixed `lane` (`cache`,
`prepare`, `compilerability`, `java-shard`, `java-serial`, `protos-suite`,
`protos-foreign`, `native-image`, `http-root-failure`), a Java `shard` (1–4)
and, for the HTTP lane, `repetitions` (1, 5, 10, 20). An early step rejects any
other value, so an invalid selection fails instead of silently running nothing.
There are no free-form commands or test selectors.

The `http-root-failure` lane compiles test classes once and repeats exactly
`ProtosHttpHostFactoryIntegrationTest#rootActorFailureTerminatesTheProcessAndRetiresEveryWorker`
through the configured Surefire `default-test` execution, without retries.
Each repetition keeps its own report directory and must contain an executed,
non-skipped testcase; any failed repetition fails the lane.

To reproduce that test locally (remove `target/http-root-failure` first):

```sh
mvn test-compile
mvn surefire:test@default-test \
  -Dtest='ProtosHttpHostFactoryIntegrationTest#rootActorFailureTerminatesTheProcessAndRetiresEveryWorker' \
  -Dsurefire.rerunFailingTestsCount=0 \
  -Dprotos.surefire.reports="$PWD/target/http-root-failure/run-1"
```

A green diagnostic lane is not acceptance, and no diagnostic workflow substitutes
for `CI`. Before publishing to `main`, manually dispatch the full `CI` workflow
against `ci-validation` and require it to pass.

That is enough for a normal first PR. The sections below explain the project's
design discipline, validation expectations, AI policy, and detailed review
standards. `AGENTS.md` remains the source for the project's more detailed
operational/publication rules when they apply.

## Before you start

Protos deliberately separates language design from implementation.

The normative specification under `spec/` defines the language. Implementation,
tests, documentation, historical behavior, Java/Truffle constraints, or the
output of an AI system do not silently redefine Protos semantics.

Before making a substantive implementation or language change:

1. Read the applicable `AGENTS.md` files.
2. Inspect the relevant documentation under `docs/`.
3. Inspect the normative specification that owns the behavior under `spec/`.
4. Inspect the current implementation and tests.
5. Derive the constraints before designing the change.

If the desired behavior is genuinely unspecified or contradictory, surface the
design question rather than encoding an arbitrary answer in the implementation.

## Where to discuss a change

Use [GitHub Discussions](https://github.com/guillermomolina/protos/discussions)
for questions, exploratory proposals, design discussion, contributor help, and
ideas that are not yet concrete work.

Use [GitHub Issues](https://github.com/guillermomolina/protos/issues) for
reproducible bugs, concrete documentation problems, and sufficiently scoped
actionable work. If you are unsure which applies, start in Discussions.

Security-sensitive information must not be posted in an ordinary public issue;
follow [SECURITY.md](SECURITY.md).

Project participation is governed by
[CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md), and general support routing is
documented in [SUPPORT.md](SUPPORT.md).

## Claiming work
<!-- GITHUB002-C HYBRID-CONTRIBUTOR-WORKFLOW -->

An assignee means active responsibility for the Issue; it does not mean repository
ownership.

A `Ready` Issue with no assignee is normally available to be claimed. If GitHub
does not let you self-assign, leave a short comment saying you would like to work
on it and a maintainer can assign it. `good first issue` identifies bounded work
suited to a first contribution; `help wanted` means contributions are welcome but
the task may require more project/domain context.

Do not start broad implementation from an exploratory Discussion. Once the work
is bounded, create or use the corresponding Issue first so scope and acceptance
criteria have a stable coordination point.

## AI-assisted development

**AI-assisted and fully AI-generated contributions are welcome in Protos.**

The project itself is developed with extensive use of AI agents, including
changes for which the implementation was generated entirely by AI. Protos does
not require a human-authored percentage of a contribution and does not reject a
change merely because an AI system wrote it.

The same standards apply regardless of how a contribution was produced:

- it must have a clear purpose and scope;
- it must respect the normative specification and project architecture;
- its assumptions must be checked against the current repository state;
- it must receive appropriate review before publication;
- it must include the validation evidence appropriate to its impact; and
- the contributor submitting it remains responsible for the contribution.

AI generation is not a substitute for review, testing, specification
compliance, or responsibility.

### Review does not have to mean "human typed it"

For substantive changes, independent review is strongly preferred when
practical. The reviewer may also use AI tools. What matters is that the review
is a real second evaluation of the change rather than an uncritical continuation
of the generation step.

For AI-heavy work, useful independent review can include a separate agent,
model, session, or clean context that is asked to inspect the actual diff,
relevant specification, assumptions, edge cases, and validation evidence.

A review should be able to challenge the proposed change. Passing the same text
through another model without asking it to verify the underlying evidence is not
meaningful independent review.

### No fire-and-forget generated pull requests

Please do not submit a large generated patch and leave maintainers to determine
what it does, whether it matches the specification, or whether it has been
validated.

This rule is about contribution quality, not about AI. The same expectation
applies to manually written code.

A pull request should make it reasonably easy to understand:

- what problem it addresses;
- what behavior or project state changes;
- which specification or design constraints are relevant;
- what important alternatives or risks were considered when applicable; and
- what validation was performed and with what result.

### Transparency

When AI materially produced or shaped a contribution, please say so briefly in
the pull request description. A simple note such as `AI assistance: substantial`
or `AI assistance: fully generated` is enough.

There is no requirement to publish prompts, transcripts, token counts, private
conversations, API credentials, or the identity of every model used. Disclosure
is intended to provide useful review context, not to create a separate class of
contribution.

Do not claim that a change received human review if it did not. AI-based review
is acceptable when it satisfies the review expectations above; describe it
accurately.

## What makes a good contribution

Prefer changes that are narrow enough to review and validate coherently. Large
outcomes may be implemented in several independently publishable slices when
that reduces risk without weakening the requested result.

For implementation work, tests should demonstrate observable Protos behavior at
the highest useful level. When behavior can be tested in Protos source, prefer
Protos-owned conformance or regression coverage. Java tests remain appropriate
for host/JVM machinery, embedding boundaries, fixtures that Protos cannot yet
provide itself, and other behavior that genuinely belongs to the Java side.

Do not change normative specification text merely to make an implementation
convenient. If implementation and specification disagree, investigate the
mismatch and change the correct owner.


## Protos source style

For hand-written Protos source, prefer stable, specified idiomatic syntax in
ordinary programs and libraries rather than exposing the canonical/desugared
protocol form everywhere. Use the explicit form when the protocol or lowering is
itself under test or discussion, when bootstrap/layering requires it, or when it
is materially clearer.

This is a non-normative style preference; `spec/` remains authoritative for
which forms exist and what they mean. See
[`docs/guide/SOURCE_STYLE.md`](docs/guide/SOURCE_STYLE.md) for the complete rule,
exceptions, and rationale.

## Validation

Validation should match the impact of the change.

Executable changes normally require focused tests for the affected behavior and
the broader suite required by the repository's current validation policy.
Specification-only and documentation/governance-only changes use the applicable
static, integrity, link, governance, or focused guards instead of automatically
running unrelated expensive test suites.

A contribution should report what was run and whether it passed. Do not present
skipped validation as successful validation.

The repository's `AGENTS.md` contains the current operational rules used by the
project's automated agents and publication workflows. Those rules are more
detailed than this contributor-facing overview and should be followed when they
apply to the work being performed.

## Pull requests

Pull Requests are the normal integration boundary for **external
contributions**:

```text
Issue → branch/fork → local validation → Pull Request → CI/review → merge
```

Contributors without direct repository publication authority should work on a
branch/fork and open a Pull Request. Do not assume that being able to open an
Issue grants authority to publish directly to `main`.

Maintainer/project automation follows the separate governed workflow in
`AGENTS.md`; this contributor-facing guide does not duplicate that operational
contract. Maintainers may still choose a Pull Request whenever review,
collaboration, or integration visibility is worth the additional remote/CI cost.

For every Pull Request that is opened, link Issues deliberately:

- use `Closes #N`, `Fixes #N`, or `Resolves #N` only when merging the PR fully
  satisfies that leaf Issue's acceptance criteria;
- use `Refs #N` for parent, related, or partially advanced work that must remain
  open; and
- never close a parent merely because one child/slice was implemented.

A useful Pull Request description normally contains:

- the leaf Issue linkage and any parent/related references;
- a concise statement of the problem and result;
- important semantic/design constraints when relevant;
- the exact local validation performed and its results;
- meaningful review evidence for substantive changes;
- known limitations, deferred work, or blockers; and
- a brief AI-assistance disclosure when AI materially produced the change.

Opening a Pull Request is not publication. External PRs should pass the
repository's applicable CI and review expectations before merge. The project
currently keeps `main` unprotected and does not require a mandatory GitHub
approval count while there is only one regular maintainer; stricter enforcement
can be introduced when actual contributor volume justifies it.

Contributions are evaluated on correctness, coherence, reviewability, evidence,
and maintainability, not on whether the project used the maintainer direct-publish
path or the contributor PR path.

## Licensing

Protos is distributed under the Adaptive Public License 1.0 (APL-1.0). See
[`LICENSE.TXT`](LICENSE.TXT) for the authoritative license terms and
[`docs/project/governance/LICENSING_RATIONALE.md`](https://github.com/guillermomolina/protos-project-docs/blob/main/docs/project/governance/LICENSING_RATIONALE.md) for
the project's non-normative explanation of that choice.

Please make sure you have the right to contribute the material you submit.
Accepted contributions become part of the Protos codebase distributed under the
project's applicable license terms.
