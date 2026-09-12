# D115 — Command-line parent positional versus subcommand traversal semantics

Status: **RATIFIED — Candidate C′ selected**

Owning work item: `LIB011` / GitHub Issue `#428`  
Decision issue: GitHub Issue `#441`

## Decision

For each current `CommandSpec` scope, nested traversal uses the **earliest feasible
exact-child boundary with parent-minimum reservation, structural current-scope
`--` escape, and irreversible child scope transfer**.

A token equal to an exact child command name may select that child only when the
parent positional prefix accumulated before the token can already satisfy every
required parent `minOccurrences` under D111 B′. Exact child names therefore
outrank only optional or surplus parent positional capacity; they never steal
capacity still required by the parent.

The rule is structural only. It does not inspect application types, execute guest
callbacks, probe environment/filesystem state, score parse trees, perform fuzzy
matching or backtrack through alternative command paths.

## Ratified rules

1. **Option-value precedence remains absolute.** A recognized value-taking option
   consumes its required value literally before child recognition, even when the
   value equals a child name or begins with `-`.

2. **Exact child names only.** Before the current command scope's structural
   `--`, a non-option token exactly equal to one direct child command name is a
   candidate traversal boundary. Prefix abbreviations, normalization, folding,
   fuzzy matching and near matches do not participate.

3. **Earliest feasible boundary.** The first exact child-name token whose parent
   positional prefix can already satisfy all required parent positional minima is
   selected as the child boundary.

4. **Required parent capacity is preserved.** An exact child name does not steal
   a token that is still required to satisfy the parent's positional
   `minOccurrences`. Optional or surplus parent capacity does not hide a feasible
   child command.

5. **D111 remains the parent allocator.** When a child boundary is selected, the
   positional material accumulated before that token is finalized exactly once
   using D111 B′ — maximal feasible left-biased positional allocation with
   suffix-minimum reservation.

6. **`--` is a current-scope structural escape.** Once the current command
   consumes structural `--`, both option recognition and child-subcommand
   recognition stop for that command scope. Every later token is current-scope
   positional data, even when its String equals a child name.

7. **The child token is structural provenance.** A selected child
   `CommandResult.tokenIndex` is the direct zero-based argument index of the exact
   token that selected that child. The token is not also a parent positional
   occurrence.

8. **Scope transfer is irreversible.** After a child is selected, every remaining
   token belongs exclusively to that child scope. The parent does not resume token
   recognition later in the input.

9. **No implicit cross-scope option inheritance.** An option declared only on the
   parent is not recognized after child selection. If the same option should be
   accepted at multiple command levels, the ordinary `OptionSpec` is explicitly
   composed into every accepting `CommandSpec`.

10. **Recursive uniformity.** The same rules apply independently at every command
    level. The root command has no traversal privilege.

11. **Ordinary failures remain sufficient.** D115 does not introduce a structured
    public parse-error taxonomy. An unrecognized or otherwise invalid token
    sequence continues to fail through the ordinary LIB011 Error lane.

12. **No speculative semantics.** Traversal uses only the frozen `CommandSpec`,
    exact Strings, current structural state, source token indices and positional
    cardinality facts. Typed decoding, callbacks, dispatch, environment,
    filesystem, TTY, process authority and guest execution are outside the rule.

## Canonical examples

### Optional parent positional

```text
parent positionals = [TARGET 0..1]
children           = [run]
arguments          = ["run"]

=> TARGET absent
=> child = run
```

The exact child outranks optional parent capacity.

### Required parent positional

```text
parent positionals = [CONFIG 1..1]
children           = [run]
arguments          = ["run"]

=> CONFIG = "run"
=> no child
```

The parent still requires one token, so the boundary is not yet feasible.

### Required parent satisfied before child

```text
parent positionals = [CONFIG 1..1]
children           = [run]
arguments          = ["config.toml", "run"]

=> CONFIG = "config.toml"
=> child = run
```

### D111 composition

```text
parent positionals = [A 0..1, B 1..1]
children           = [run]
arguments          = ["x", "run"]

=> A absent
=> B = "x"
=> child = run
```

D111 assigns the feasible parent prefix; D115 then transfers to the child.

### Literal child-name escape

```text
parent positionals = [TARGET 0..1]
children           = [run]
arguments          = ["--", "run"]

=> TARGET = "run"
=> no child
```

The structural delimiter disables child recognition in that scope.

### Option value equal to child name

```text
children  = [run]
arguments = ["--output", "run"]

=> output.value = "run"
=> the value token is not a child boundary
```

The previously ratified literal option-value rule remains authoritative.

## Complexity requirement

The traversal must remain deterministic and bounded.

An implementation may build exact-name lookup Maps for the command scopes actually
visited and maintain the amount of parent positional minimum still unsatisfied.
At the selected boundary, the parent prefix is finalized once with D111.

Required target:

```text
time:   O(T + Svisited)
memory: O(result + Svisited)
```

where:

- `T` is the argument-token count; and
- `Svisited` is specification material indexed for the command path actually
  visited.

No global command registry, flattened whole-tree cache, scheduler state,
thread-local parser state, Task/Actor coordination or host parser dependency is
required.

## Prior-art basis

The D115 audit compared:

- Python `argparse`;
- Rust `clap`;
- Java picocli;
- .NET `System.CommandLine`;
- OCaml Cmdliner;
- Swift ArgumentParser;
- Python Click;
- Go Cobra/pflag;
- Haskell `optparse-applicative`;
- Rust `bpaf`;
- Git-style command dispatch; and
- low-level lexopt/getopt-style parsing.

The strongest evidence was:

- Python `argparse` demonstrates joint consideration of earlier positional arity
  and subparser reachability, allowing optional parent capacity to yield to a
  subparser while required parent capacity remains meaningful.
- clap, picocli, `System.CommandLine` and Cobra provide strong evidence that an
  exact reachable child command is a structural boundary rather than ordinary
  surplus positional data.
- `--` in the stronger command-tree precedents provides the explicit escape from
  structural interpretation into positional data.
- Swift ArgumentParser and Click demonstrate the practical failure mode of
  unconditional parent-first positional consumption: an optional/greedy parent
  positional can hide a valid child command.
- Cmdliner and Git show the scalability benefit of crisp irreversible command
  boundaries, while their more restrictive dedicated command zones would narrow
  Protos's already-published mixed parent-positionals/subcommands model.
- lexopt/getopt remain useful mechanism precedent but deliberately leave command
  meaning to callers, which is incompatible with LIB011's already-ratified
  recursive keyed `CommandResult` model.

The approved candidate scored:

```text
Aguante de futuro: 5.0 / 5
Escalabilidad:      5.0 / 5
Filosofía Protos:   5.0 / 5
GITHUB010 mean:     4.88 / 5
```

## Strongest argument against

C′ is context-dependent. With:

```text
parent positionals = [CONFIG 1..1]
children           = [run]
```

then:

```text
["run"]
=> CONFIG = "run"
```

while:

```text
["config.toml", "run"]
=> CONFIG = "config.toml"
=> child = run
```

Candidate A ("an exact child always wins") would be locally simpler.

C′ accepts this context dependence because required parent cardinality is already a
published semantic fact. The traversal boundary becomes structural at the earliest
point where selecting it preserves that required parent contract, rather than
converting a feasible parent invocation into failure merely because a child shares
the spelling.

## Future-regret scenario and escape path

A future command may have an optional positional such as `WORKSPACE`, and a later
release may add a child with the same spelling as a previously valid workspace
value. For example, adding child `build` can reinterpret:

```text
tool build
```

from optional parent data to child selection.

C′ cannot eliminate this collision, but it confines the reinterpretation to
optional/surplus parent capacity; required parent data is never stolen.

The existing structural `--` is the baseline explicit escape:

```text
tool -- build
```

If future consumers prove this insufficient, an opt-in explicit positional
index/from-end/range mechanism or an explicit traversal marker may be added under a
separate decision without redefining existing C′ command trees.

## Explicitly not decided

D115 does not decide:

- command execution or dispatch;
- help/usage rendering;
- aliases or prefix abbreviations;
- public structured parse-error taxonomy;
- typed decoding or parser-owned defaults;
- conflicts, requirements or option groups;
- persistent/global option inheritance;
- environment/configuration merging;
- shell completion;
- filesystem/process/TTY/network authority; or
- future explicit positional-index or traversal-marker extensions.

## Consequence

`LIB011-C` is unblocked for nested command-path traversal and composition under
this contract. If implementation exposes another substantive semantic or durable
architectural choice, the ordinary Dxxx/PLATxxx approval gate applies before
publication.
