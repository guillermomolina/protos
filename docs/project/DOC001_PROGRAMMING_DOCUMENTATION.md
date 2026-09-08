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
- DOC001-E:
  `SAME_COMMIT`
  (`Publish control flow programming guide`).

## Slice ledger

| Slice | Status | Scope | Dependency / closure condition |
|---|---|---|---|
| DOC001-A | CLOSED | Root README positioning and documentation architecture | Published at `bd3cd38218cfccdca8de529f4d6c26fede7ad771`. |
| DOC001-B | CLOSED | Bindings, execution contexts, lexical state, and receiver state | Published at `bd3cd38218cfccdca8de529f4d6c26fede7ad771`. |
| DOC001-C | CLOSED | Objects, delegation, composition, structural state, reflection | Published at `8ab9463b8466974fc5f23f0c7304faeacb3db641`. |
| DOC001-D | CLOSED | Closures, methods, receivers, extraction, `super`, return homes | Published at `01470dca9df787ed216b2c19faaead965fb18cc8`. |
| DOC001-E | CLOSED | Control flow through ordinary protocols | Published in `SAME_COMMIT`: chapter 04, guide navigation, current-behavior cross-links, and DOC001 project reconciliation. |
| DOC001-F | READY | Values, identity, equality, and collections | Independent of DOC001-E. |
| DOC001-G | READY | Modules and imports | Independent of DOC001-E. |
| DOC001-H | READY | Errors, handlers, `ensure`, and resource lifetime | Independent of DOC001-E. |
| DOC001-I | READY | Futures and structured concurrency | Independent of DOC001-E. |
| DOC001-J | READY | Isolated parallel execution | Independent of DOC001-E. |
| DOC001-K | READY | Actors and Actor Groups | Independent of DOC001-E. |
| DOC001-L | READY | Process, I/O, Filesystem/File capabilities, and authority | Independent of DOC001-E. |
| DOC001-M | BLOCKED_BY_DEPENDENCIES | Packages, testing, and bundled toolchain | Final chapter closure requires TOOL001 and TOOL002 CLOSED. |
| DOC001-N | BLOCKED_BY_DEPENDENCIES | Final navigation and consistency closure | Requires DOC001-E through DOC001-M complete. |

## B007 / I023 relationship

`B007 — Standard while protocol semantics` was discovered while preparing
DOC001-E. D044 / specification revision `0.1.381` resolved the normative
ambiguity, and D045 / specification revision `0.1.382` later clarified the
task-scoped Future-ownership interaction exposed during I023.

I023 is now CLOSED after A/B/C/D implementation and conformance publication, and
B007 is CLOSED with it. The fresh I023-D current-main audit confirms that the
standard inherited `Object.while` behavior is runnable reference-implementation
behavior rather than merely specified future behavior.

DOC001-E is now CLOSED. Chapter 04 publishes the control-flow explanation,
cross-links representative executable conformance evidence, and keeps the
normative specification authoritative. The chapter describes the current
runnable standard Boolean and `Object.while` behavior without copying
project-ledger prose or redefining the language.

B007/I023 no longer gate DOC001. DOC001-F through DOC001-L remain independently
READY and retain their own current-main audit requirement when started.
DOC001-M remains toolchain-gated and DOC001-N remains the final consistency
closure.

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
