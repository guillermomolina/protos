# DOC001 — Protos Programming Documentation

Status: IN_PROGRESS

This is the canonical project record for the first independently tracked
documentation initiative in Protos.

DOC001 is non-normative. The specification under `spec/` defines Protos. This
record tracks explanatory/public documentation work and must never be used as
authority for language semantics.

## Objective

Make the repository answer, coherently and without forcing readers to reconstruct
the language from normative documents alone:

- what Protos is and why its model is different;
- how programmers should think about its semantic model;
- how familiar programming concepts map into current Protos behavior;
- where executable tutorials/examples provide runnable companions;
- what is implemented now versus specified, planned, blocked, or still under
  active tool/library development.

The Programming Guide complements rather than duplicates:

- `README.md` as the project landing page;
- `protos/tutorials/` as progressive executable lessons;
- `protos/examples/` as the task-oriented cookbook;
- `spec/` as the normative authority.

## Family boundary

`DOCxxx` tracks independently meaningful documentation initiatives.

It does not absorb documentation changes that belong to another formal work
item. A documentation-only closure slice of `Ixxx`, `LIBxxx`, `TOOLxxx`, or
another family remains owned by that family.

DOC001 also does not own executable language conformance policy (`LMxxx`) or a
future bundled documentation-generation tool (`TOOLxxx`).

## Retrospective reconciliation

DOC001 is introduced after its first four documentation slices were already
published. These slice labels are administrative reconciliation over those
published commits; they do not rewrite historical commit messages or imply the
identifiers existed at publication time.

Closure evidence:

- DOC001-A and DOC001-B:
  `bd3cd38218cfccdca8de529f4d6c26fede7ad771`
  (`Refresh README and add programming guide`);
- DOC001-C:
  `8ab9463b8466974fc5f23f0c7304faeacb3db641`
  (`Add object model programming guide`);
- DOC001-D:
  `01470dca9df787ed216b2c19faaead965fb18cc8`
  (`Add callable programming guide`).

## Slice ledger

| Slice | Status | Scope | Dependency / closure condition |
|---|---|---|---|
| DOC001-A | CLOSED | Root README positioning and documentation architecture | Published at `bd3cd38218cfccdca8de529f4d6c26fede7ad771`. |
| DOC001-B | CLOSED | Bindings, execution contexts, lexical state, and receiver state | Published at `bd3cd38218cfccdca8de529f4d6c26fede7ad771`. |
| DOC001-C | CLOSED | Objects, delegation, composition, structural state, reflection | Published at `8ab9463b8466974fc5f23f0c7304faeacb3db641`. |
| DOC001-D | CLOSED | Closures, methods, receivers, extraction, `super`, return homes | Published at `01470dca9df787ed216b2c19faaead965fb18cc8`. |
| DOC001-E | BLOCKED | Control flow through ordinary protocols | B007 must first define the complete observable standard `while` protocol. |
| DOC001-F | READY | Values, identity, equality, and collections | Independent of DOC001-E. |
| DOC001-G | READY | Modules and imports | Independent of DOC001-E. |
| DOC001-H | READY | Errors, handlers, `ensure`, and resource lifetime | Independent of DOC001-E. |
| DOC001-I | READY | Futures and structured concurrency | Independent of DOC001-E. |
| DOC001-J | READY | Isolated parallel execution | Independent of DOC001-E. |
| DOC001-K | READY | Actors and Actor Groups | Independent of DOC001-E. |
| DOC001-L | READY | Process, I/O, Filesystem/File capabilities, and authority | Independent of DOC001-E. |
| DOC001-M | BLOCKED_BY_DEPENDENCIES | Packages, testing, and bundled toolchain | Final chapter closure requires TOOL001 and TOOL002 CLOSED. |
| DOC001-N | BLOCKED_BY_DEPENDENCIES | Final navigation and consistency closure | Requires DOC001-E through DOC001-M complete. |

## B007 relationship

`B007 — Standard while protocol semantics` is a genuine normative blocker
discovered while preparing DOC001-E.

B007 does not block DOC001 as a whole. The control-flow chapter must not guess the
missing `while` contract, but unrelated documentation slices whose semantics and
implementation are already defined may continue independently.

When B007's normative unblock condition is satisfied, DOC001-E must be re-audited
against the then-current specification and implementation before changing its
status.

## Future implementation tracking

DOC001 does not reserve an `Ixxx` for `while`.

While B007 is BLOCKED, standard `while` implementation work is not READY.
Because `main` is developed concurrently, reserving a future implementation
number now could collide with unrelated work. When B007 is normatively resolved,
the implementation task must audit the then-current `origin/main` and allocate
the next unused `Ixxx` at that time.

## Completion rule

DOC001 remains IN_PROGRESS until every required slice is published and DOC001-N
completes the final cross-document audit.

## Publication and validation

Pure DOC001 content/governance changes are documentation-only unless they also
change executable material, specification, implementation, tests, build
configuration, or another validation-relevant surface.

Documentation-only DOC001 work:

- does not increment the implementation version;
- does not change the specification revision;
- must pass applicable static/documentation/governance checks;
- must not present planned or specified-but-unimplemented behavior as current
  runnable behavior.
