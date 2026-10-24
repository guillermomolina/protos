# Protos examples

This directory is a task-oriented cookbook: **how do I do X in Protos?** It is inspired by the task-oriented organization of Rosetta Code rather than by a linear tutorial.

All Protos source files use the canonical `.protos` extension. Examples are non-normative; `spec/` remains authoritative.

[`ROSETTA_CODE.md`](ROSETTA_CODE.md) maps exact external Rosetta Code tasks to their canonical sources in this tree, tracks their correctness status, and records any possible future Rosetta Code publication.

Print-dependent cookbook programs are maintained as executable standalone-CLI sources. The CLI regression suite discovers every `.protos` file in this tree containing `print(` and executes it unchanged, including concurrent examples whose `Future.value()` suspension must run inside the Process RootActor task.

| Task | Example |
| --- | --- |
| Print a value | `hello-world.protos` |
| Print the exact Rosetta Code Hello world/Text message | `basics/rosetta-hello-world-text.protos` |
| Create and modify slots | `basics/slots.protos` |
| Create an object | `objects/object-literal.protos` |
| Delegate behavior | `objects/delegation.protos` |
| Override delegated behavior | `objects/overriding.protos` |
| Use `this` | `objects/this.protos` |
| Create a closure | `closures/basic.protos` |
| Capture local state | `closures/captured-state.protos` |
| Pass behavior as a value | `closures/higher-order.protos` |
| Work with closure call arguments | `closures/rest-arguments.protos` |
| Share captured mutable state across closures | `closures/shared-captured-state.protos` |
| Inherit callable behavior through delegation | `objects/inherited-callability.protos` |
| Express conditional control flow | `control-flow/conditional.protos` |
| Compute Fibonacci with recursive and iterative variants | `algorithms/fibonacci-recursive.protos` |
| Compute factorial recursively | `algorithms/factorial-recursive.protos` |
| Run FizzBuzz over 1 through 100 | `algorithms/fizzbuzz.protos` |
| Compute the greatest common divisor | `algorithms/greatest-common-divisor.protos` |
| Simulate the 100 doors problem | `algorithms/100-doors.protos` |
| Search a sorted integer Array with binary search | `algorithms/binary-search.protos` |
| Sort an Array with bubble sort | `algorithms/bubble-sort.protos` |
| Store, compose and invert first-class functions | `closures/first-class-functions.protos` |
| Capture independent values in a collection of Closures | `closures/value-capture.protos` |
| Delegate optional behavior to a helper object | `objects/rosetta-delegates.protos` |
| Work with exact Fractions, Float mixing, Complex numbers and square roots | `numbers/exact-and-complex.protos` |
| Show portable runtime metadata (`std:runtime`) | `runtime-introspection/info.protos` |
| Inspect the runtime (`std:inspect`, requires `--inspect`) | `runtime-introspection/inspect.protos` |
| Use value-keyed maps | `collections/maps.protos` |
| Use identity-keyed maps | `collections/identity-map.protos` |
| Use structurally equal values as Map vs IdentityMap keys | `collections/path-keys.protos` |
| Build and compare portable paths | `paths/portable-paths.protos` |
| Run work asynchronously and compose Futures | `concurrency/future-chain.protos` |
| Observe the current Actor identity | `concurrency/actor-current.protos` |
| Spawn an Actor and request a reply | `concurrency/actor-request-reply.protos` |
| Observe same-sender FIFO with send then request | `concurrency/actor-send-fifo.protos` |
| Route a request through an ActorGroup | `concurrency/actor-group-request.protos` |
| Execute work in an isolated parallel domain | `concurrency/parallel-execution.protos` |
| Encode, mutate, and decode bytes | `io/encoding-and-bytes.protos` |

Run the metadata example normally from the product repository root:
`bin/protos protos/examples/runtime-introspection/info.protos`. The
inspection example requires explicit authority:
`bin/protos --inspect protos/examples/runtime-introspection/inspect.protos`.
The inspection command produces implementation-specific diagnostic text;
do not treat its contents as a stable output format.
