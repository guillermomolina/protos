# Protos specification agent guidelines

These instructions apply to the entire `spec/` tree in addition to the
repository-root `AGENTS.md`.

Work in this tree as a language designer, specification author, and adversarial
reviewer. The objective is not merely to make the documents internally tidy; it
is to define a coherent language whose semantics survive independent
implementation and realistic pressure.

## Research before designing

When a language-design question is not already resolved normatively, do not jump
directly to syntax or implementation.

Research how comparable languages, runtimes, and language designs solve the same
or closely related problem. Prefer primary and authoritative material:
language specifications, design documents, reference implementations, runtime
documentation, proposals, papers, and other primary technical sources.
Community discussions may reveal practical edge cases but must not substitute
for primary sources when comparing normative behavior.

The purpose is to understand the design space, not to copy precedent.

For relevant approaches, identify:

- the semantic model;
- guarantees provided;
- trade-offs;
- conceptual and runtime costs;
- special cases introduced;
- composition with the rest of that language;
- implementation assumptions that shaped the design;
- known scalability, usability, or portability consequences.

Then compare those approaches against the Protos design philosophy. A familiar
or popular solution is not automatically the right Protos solution.

When asked "how should Protos implement X?", interpret the task as:

    research the design space,
    compare established approaches,
    identify their trade-offs,
    and recommend the solution that best preserves the Protos philosophy.

Do not answer only with the first technically workable solution.

## Design by attempted falsification

Do not only look for evidence that a proposed design works. Actively try to
break it.

Search for:

- workloads where it becomes expensive;
- domains where it becomes awkward;
- scale changes that expose hidden costs;
- interactions that require new special cases;
- cases where local reasoning breaks down;
- identity-based privileges or "pets";
- unnecessary shared mutable state;
- unnecessary blocking or global coordination;
- cases where concurrency or distribution leaks into simple programs;
- implementation assumptions that become observable;
- ambiguities that independent implementers could resolve differently.

Construct counterexamples when possible. A design that works only for its
motivating example is probably too narrow for the language core.

## Stress-test beyond the motivating domain

Once a candidate abstraction is coherent, test it mentally against substantially
different contexts and scales where relevant, for example:

- trivial single-threaded programs;
- ordinary applications;
- highly concurrent systems;
- actor-oriented or message-driven systems;
- distributed systems and large clusters;
- event-driven workloads;
- data-oriented workloads;
- interactive applications;
- long-running services;
- libraries and frameworks;
- resource-constrained environments;
- metaprogramming, tooling, or embedding.

The goal is not to optimize Protos for every domain. The goal is to detect
whether a design unnecessarily excludes domains, hides a scaling cliff, or
forces unrelated programs to pay for capabilities they do not use.

A Protos abstraction is not finished merely because it solves the problem that
motivated it. Strong abstractions survive unrelated use cases without acquiring
special treatment.

## Introduce layers only for real boundaries

Do not add layers merely for architectural neatness, and do not overload an
existing abstraction merely to keep the noun count low.

A new layer is justified when an existing abstraction is being forced to span
genuinely different semantic responsibilities, lifecycle boundaries, isolation
domains, scales, or coordination requirements.

A good new layer should:

- solve a concrete semantic or scalability problem;
- preserve the simplicity of lower layers;
- avoid imposing its cost on programs that do not use it;
- compose naturally with adjacent abstractions;
- remain useful beyond the original motivating case;
- reduce special cases rather than create them.

Minimize total conceptual complexity, not the number of abstractions.

## Design decision procedure

For an unresolved language-design question, work in this order:

1. State the semantic capability required without proposing syntax.
2. Determine whether existing Protos mechanisms already express it.
3. Research relevant prior art and compare the real alternatives.
4. If a new concept is necessary, identify the smallest concept that closes the
   semantic gap.
5. Look explicitly for pets, special cases, global state, blocking, hidden
   coordination, heuristics, and implementation leakage.
6. Check whether semantically distinct operations are being conflated.
7. Define failure behavior and violated invariants.
8. Check interaction with objects, slots, delegation, lookup, assignment,
   contexts, closures, calls, mutation, errors, I/O, and concurrency as
   applicable.
9. Stress-test the proposal across different domains and scales.
10. Try to falsify the design with counterexamples.
11. Check the "pay only for what you use" property for simple programs.
12. Remove assumptions specific to Truffle, GraalVM, the JVM, Windows, Linux,
    POSIX, parser technology, or the current implementation unless they are
    explicitly part of the intended semantics.
13. State the semantics precisely enough for independent implementation.
14. Resolve grammar ambiguity structurally before adding new syntax categories.
15. Only then design syntax and implementation strategy.

If a genuine design choice remains after this process, present the alternatives,
trade-offs, recommendation, and consequences instead of silently choosing.

## Recommendation format

A substantive design recommendation should normally make clear:

1. the problem being solved;
2. the relevant approaches found elsewhere;
3. their meaningful trade-offs;
4. which alternatives conflict with Protos principles and why;
5. the recommended Protos design;
6. consequences and scaling characteristics;
7. unresolved semantic questions requiring an explicit decision.

## Specification authority

`spec/` defines Protos. The implementation, tests, examples, README, and other
documentation do not override normative semantics.

Do not redesign language semantics to make the current implementation easier.
Do not change normative behavior merely to satisfy existing tests. Do not infer
intent from historical implementation behavior when the specification says
otherwise.

If normative documents disagree, expose and resolve the inconsistency
explicitly. Do not silently select one document as convenient.

## Independent implementation test

For every normative rule, ask:

> Could two independent implementers, knowing nothing about the current
> interpreter and using only the normative Protos specifications, implement this
> feature and obtain the same observable behavior?

If the answer is no, the specification is not finished.

## The Protos tests

Before accepting a design, ask all of the following:

> Does this make the Protos universe more general, or add another special thing
> that the universe must know about?

> Is this a new rule of the Protos universe, or something that should be built
> inside that universe?

> Does the abstraction eliminate more complexity than it introduces?

> Does a program that does not use this capability avoid paying its conceptual
> and runtime costs?

> Can independent components make progress without sharing state or coordinating
> unnecessarily?

> Does the proposal cross a qualitative 0-to-1 threshold by creating a new
> category of language mechanism?

A proposal that fails one of these tests is not automatically wrong, but it
requires substantially stronger justification.

## Design smells

Stop and reconsider when reasoning relies on statements such as:

- "this object is special";
- "the runtime can just detect it";
- "except in this case";
- "because Java/JavaScript/Python/etc. does it";
- "Truffle already works this way";
- "the parser makes this easier";
- "it is easier to implement";
- "we can return null instead";
- "users will probably mean";
- "we need one global lock";
- "everything can share this singleton";
- "we can make the whole API async";
- "tests currently expect it";
- "we can document the ambiguity".

These are signals to investigate further, not proofs of incorrectness.

## Editing discipline

Follow the root rules for document revision, changelog updates, minimal edits,
format preservation, and cross-document consistency. A semantic change must be
reflected in every normative document whose responsibility it affects.
