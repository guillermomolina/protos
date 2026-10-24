# Rosetta Code catalog

This catalog maps original [Rosetta Code](https://rosettacode.org/) tasks to canonical, original Protos source files.

- Examples are organized in the existing topical tree of this directory, not copied into a parallel `rosetta/` source tree.
- Catalog membership does not imply that a solution has been published on the external Rosetta Code wiki.
- No verification revision may be claimed until the human has executed and reported the applicable checks.
- The original Rosetta Code task descriptions and existing solutions are not copied into this repository.
- Publication to Rosetta Code and license compatibility require a separate explicit authorization.

| Rosetta Code task | Task URL | Canonical Protos source | State | Verified revision | Published URL |
| --- | --- | --- | --- | --- | --- |
| Hello world/Text | https://rosettacode.org/wiki/Hello_world/Text | [basics/rosetta-hello-world-text.protos](basics/rosetta-hello-world-text.protos) | Pending validation | — | — |
| Factorial | https://rosettacode.org/wiki/Factorial | [algorithms/factorial-recursive.protos](algorithms/factorial-recursive.protos) | Pending validation | — | — |
| Fibonacci sequence | https://rosettacode.org/wiki/Fibonacci_sequence | [algorithms/fibonacci-recursive.protos](algorithms/fibonacci-recursive.protos) | Pending validation | — | — |
| FizzBuzz | https://rosettacode.org/wiki/FizzBuzz | [algorithms/fizzbuzz.protos](algorithms/fizzbuzz.protos) | Pending validation | — | — |
| Greatest common divisor | https://rosettacode.org/wiki/Greatest_common_divisor | [algorithms/greatest-common-divisor.protos](algorithms/greatest-common-divisor.protos) | Pending validation | — | — |
| 100 doors | https://rosettacode.org/wiki/100_doors | [algorithms/100-doors.protos](algorithms/100-doors.protos) | Pending validation | — | — |
| Binary search | https://rosettacode.org/wiki/Binary_search | [algorithms/binary-search.protos](algorithms/binary-search.protos) | Pending validation | — | — |
| Sorting algorithms/Bubble sort | https://rosettacode.org/wiki/Sorting_algorithms/Bubble_sort | [algorithms/bubble-sort.protos](algorithms/bubble-sort.protos) | Pending validation | — | — |
| First-class functions | https://rosettacode.org/wiki/First-class_functions | [closures/first-class-functions.protos](closures/first-class-functions.protos) | Pending validation | — | — |
| Closures/Value capture | https://rosettacode.org/wiki/Closures/Value_capture | [closures/value-capture.protos](closures/value-capture.protos) | Pending validation | — | — |
| Delegates | https://rosettacode.org/wiki/Delegates | [objects/rosetta-delegates.protos](objects/rosetta-delegates.protos) | Pending validation | — | — |
| Parallel calculations | https://rosettacode.org/wiki/Parallel_calculations | `concurrency/parallel-calculations.protos` | Planned | — | — |

## Publication readiness

Any future publication of a catalog entry requires all of the following:

- The canonical source exists and satisfies the external task.
- Its correctness tests passed on an identified Protos Git revision.
- The relevant Protos version is recorded.
- The external contribution follows Rosetta Code's language registration, example formatting, and license requirements.
- Publication is a separate, human-authorized operation.
- Only after publication is the actual external URL entered into the catalog.

## Rosetta Code submission preparation

Any future submission of a catalog entry follows this workflow:

1. Confirm the canonical source and its task-level correctness.
2. Record the successful human-executed validation and the corresponding published Protos Git revision.
3. Record the Protos version used for validation.
4. Prepare a minimal standalone excerpt from the canonical source, excluding unrelated cookbook content.
5. Keep the external Rosetta Code task title and URL exactly as recorded in the catalog.
6. Confirm that Rosetta Code has a language category for **Protos**, distinct from any similarly named language such as Proto. This catalog does not assume that such a category exists.
7. Use the Rosetta Code language-section header convention, with the final language name matching its category.
8. Confirm the current Rosetta Code syntax-highlighting mechanism and whether a `protos` lexer identifier exists. Do not assume one exists.
9. Confirm the rights and license requirements before placing any Protos-owned source on the externally GFDL-licensed wiki. Compatibility between the Adaptive Public License and the GNU Free Documentation License has not been determined.
10. Obtain explicit authorization before publishing anything externally.
11. After actual publication, record the final external task-section URL.

Official Rosetta Code guidance:

- [Rosetta Code:Add a Language](https://rosettacode.org/wiki/Rosetta_Code:Add_a_Language)
- [Help:Adding a new programming example](https://rosettacode.org/wiki/Help:Adding_a_new_programming_example)
- [Help:Formatting](https://rosettacode.org/wiki/Help:Formatting)
