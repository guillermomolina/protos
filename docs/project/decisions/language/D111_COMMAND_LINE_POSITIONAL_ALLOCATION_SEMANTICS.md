# D111 — Command-line positional allocation semantics

Status: **RATIFIED — Candidate B′ selected**

Owning work item: `LIB011` / GitHub Issue `#428`  
Decision issue: GitHub Issue `#437`

## Decision

For one already-selected `CommandSpec` scope, positional tokens are allocated by
**maximal feasible left-biased allocation with suffix-minimum reservation**.

Each positional consumes the maximum number of tokens allowed by its own
cardinality **only when doing so still leaves enough tokens to satisfy the sum of
all later `minOccurrences`**. Among allocations that satisfy every positional
minimum, earlier declarations therefore win. The parser performs no semantic
search, ranking, typed disambiguation or backtracking.

This decision is command-local. It does **not** decide where a parent command
stops and a nested subcommand begins. `LIB011-C` remains the owner of
parent/subcommand traversal and cross-command allocation.

## Ratified rules

1. Ordered positional specs remain the authority for allocation inside the
   already-selected command scope.
2. Before deciding whether the current positional may consume a token, the
   parser reserves the sum of `minOccurrences` for all later positional specs.
3. A `1..1` positional consumes exactly one token. If no token can be assigned
   while preserving the later minimum, parsing fails.
4. A `0..1` positional consumes one token if and only if at least one token
   remains beyond the minimum required by later positionals.
5. The existing LIB011-A invariant remains authoritative: at most one positional
   may have unbounded `maxOccurrences`, and it must be last.
6. A final `0..unbounded` positional consumes every remaining positional token.
7. A final `1..unbounded` positional consumes every remaining positional token
   and fails when none is available.
8. If bounded positionals are exhausted and unallocated positional tokens
   remain, parsing fails as an unexpected-positional condition.
9. When several allocations are feasible, the earlier positional declaration
   receives the surplus. Later positionals receive only the capacity that must
   be reserved to satisfy their minima.
10. Allocation never depends on String contents, typed conversion, filesystem,
    environment, runtime state, callbacks or application-specific predicates.

## Canonical examples

```text
[A 0..1, B 1..1]

[]        -> ERROR
[x]       -> A absent, B=x
[x,y]     -> A=x, B=y
[x,y,z]   -> ERROR
```

```text
[A 0..1, B 0..1]

[]        -> A absent, B absent
[x]       -> A=x, B absent
[x,y]     -> A=x, B=y
```

```text
[A 0..1, B 1..unbounded]

[]        -> ERROR
[x]       -> A absent, B=[x]
[x,y]     -> A=x, B=[y]
[x,y,z]   -> A=x, B=[y,z]
```

```text
[A 0..1, B 0..unbounded]

[]        -> A absent, B=[]
[x]       -> A=x, B=[]
[x,y]     -> A=x, B=[y]
```

The last example is intentional: B′ is left-biased, not right-biased. Because
`B` has minimum zero, no token is reserved for it.

## Complexity requirement

Under the already-ratified LIB011-A positional invariants, B′ is implementable
without backtracking.

An implementation may precompute or incrementally maintain the minimum required
by the remaining positional suffix. The required asymptotic target is:

```text
time:   O(P + T)
memory: O(result)
```

where `P` is positional-spec count and `T` is positional-token count.

No global cache, registry, synchronization or shared mutable state is required.

## Prior-art basis

The D111 audit compared Python `argparse`, Rust `clap`, picocli, Cmdliner,
System.CommandLine, Swift ArgumentParser, Click, optparse-applicative, bpaf,
lexopt, Cobra, GNU/POSIX getopt and docopt.

The most relevant evidence was:

- Python `argparse` jointly considers pending positional arities, which lets
  later required positionals constrain earlier optional/repeating consumption.
- Rust `clap` exposes `allow_missing_positional` for the exact
  optional-before-required family.
- picocli and Cmdliner demonstrate a strong future escape path through explicit
  position/range/end-relative addressing when implicit ordered allocation is not
  expressive enough.
- System.CommandLine is the strongest mature counterexample for strict
  left-greedy fill-then-validate behavior.
- lexopt/getopt/Cobra show the low-level alternative of returning raw operands,
  but that alternative is incompatible with LIB011-A's already-ratified keyed
  `PositionalOccurrence` result model.

The approved candidate scored:

```text
Aguante de futuro: 5.0 / 5
Escalabilidad:      5.0 / 5
Filosofía Protos:   5.0 / 5
GITHUB010 mean:     4.91 / 5
```

## Strongest argument against

An early optional positional is not locally greedy. In:

```text
[A 0..1, B 1..1] + [x]
```

`A` remains absent because `x` must be reserved for required `B`.

That non-local dependency is accepted because it preserves the declared meaning
that `A` is optional and `B` is required, accepts an invocation whenever the
current cardinality model admits a feasible allocation, and does so without
content-sensitive heuristics or backtracking.

## Future-regret and escape path

A future CLI may need semantically meaningful holes, direct source-position
addressing, explicit ranges or end-relative fields. If that requirement
materializes, LIB011 may add an explicit positional mapping extension inspired
by Cmdliner/picocli.

Such an extension can be opt-in for new specifications while B′ remains the
deterministic compatibility rule for existing ordered positional specs. The
already-ratified keyed, lossless result model and direct token indices preserve
the information required for that migration.

## Explicitly not decided

D111 does not decide:

- parent-command positional allocation versus subcommand selection;
- subcommand traversal;
- option recognition or option-value parsing;
- structured public parse-error taxonomy;
- explicit positional index/range syntax;
- typed decoding/defaults;
- command dispatch or any authority-bearing integration.

Those remain owned by their existing LIB011 slices and approval gates.

## Consequence

`LIB011-B` is unblocked for deterministic single-command token parsing under
this rule. If implementation exposes a new substantive semantic choice, the
ordinary Dxxx approval gate applies before publication.
