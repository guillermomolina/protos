# Protos receives its first community contribution

**September 11, 2026**

Protos crossed an important community milestone today: the project merged its
**first external community pull request**.

[`jabrailkhalil`](https://github.com/jabrailkhalil) contributed
[PR #315](https://github.com/guillermomolina/protos/pull/315),
**“Examples: add isolated parallel execution cookbook program (#12)”**, which
closed [issue #12](https://github.com/guillermomolina/protos/issues/12).

The contribution added:

```text
protos/examples/concurrency/parallel-execution.protos
```

and linked it from the executable examples cookbook.

## What the example teaches

The new cookbook program demonstrates the public Protos isolated-parallel model
without exposing internal worker or scheduler machinery.

It covers:

- explicit data transfer into `closure.parallel(...)`;
- observing the returned `Future` with `value()`;
- deterministic `parallelMap` and `parallelFilter` result ordering;
- lowest-logical-index semantics for `parallelFindIndex`;
- the canonical logical reduction tree used by `parallelReduce`;
- stable `parallelSort`;
- the rule that isolated parallel work does not implicitly capture caller
  mutable lexical state.

That made it a particularly fitting first contribution: small enough to review
cleanly, but attached to a distinctive part of the language model rather than
being a cosmetic change.

## Review and validation

The pull request went through normal review rather than receiving special
treatment for being the first contribution.

The final contribution retained validation for:

```text
standalone example execution                PASS
ProtosCliLearningMaterialsTest             50 / 50 PASS
ProtosParallelExecutionTest                 7 / 7 PASS
source style guard                          PASS
git diff --check                            clean
```

The review also tightened the wording around `parallelReduce`: determinism
comes from Protos selecting one canonical logical reduction tree, not from
requiring reducers to be associative or commutative.

After the contributor incorporated the requested polish and rebased onto the
then-current `main`, CI was green and the pull request was approved and merged.

The merge completed on **September 11, 2026 at 15:18:21 UTC**.

## Why this matters

A project changes character when someone who did not create it can:

1. find a public work item;
2. understand enough of the design to make a focused change;
3. pass the project's validation and review process;
4. respond to review;
5. and land useful work in `main`.

For Protos, PR #315 demonstrated that the repository was beginning to work not
only as a private development workspace, but as an open-source project that
another person could actually enter and contribute to.

That is worth preserving as a project milestone.

## Announcements and the durable news archive

This milestone was also celebrated through GitHub Discussions when it happened.

The two surfaces have different roles:

```text
GitHub Discussions / Announcements
    conversation, reactions and community visibility

docs/news/
    durable, versioned project milestone record
```

Not every Announcement needs a permanent news entry, and not every news entry
needs a Discussion. Major project milestones can have both.

## References

- [PR #315 — Examples: add isolated parallel execution cookbook program (#12)](https://github.com/guillermomolina/protos/pull/315)
- [Issue #12 — Examples: add an isolated parallel execution cookbook program](https://github.com/guillermomolina/protos/issues/12)
- [GitHub Discussions](https://github.com/guillermomolina/protos/discussions)
