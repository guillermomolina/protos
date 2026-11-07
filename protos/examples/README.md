# Protos examples

This directory is a task-oriented cookbook: **how do I do X in Protos?** It is inspired by the task-oriented organization of Rosetta Code rather than by a linear tutorial.

All Protos source files use the canonical `.protos` extension. Examples are non-normative; `spec/` remains authoritative.

[`ROSETTA_CODE.md`](ROSETTA_CODE.md) maps exact external Rosetta Code tasks to their canonical sources in this tree, tracks their correctness status, and records any possible future Rosetta Code publication.

Print-dependent cookbook programs are maintained as executable standalone-CLI sources. The CLI regression suite discovers every `.protos` file in this tree containing `print(` and executes it unchanged, including concurrent examples whose `Future.value()` suspension must run inside the Process RootActor task.
The examples `modules/public-module-pricing/` and `modules/package-invoice/`
are package projects, not standalone `.protos` files. Their `module.protos`
entries use `protos run Main` and are verified separately; do not treat their
`module.protos` paths as standalone CLI entries.

`closures/non-local-return.protos` compares contextual `return value` with
`^value` when returning early from an enclosing function inside an Array
callback, records the number of visited elements, and shows `(return)(x)` for
an ordinary function binding named `return`. It teaches that control-flow
requirement; ordinary index searches may use `std:collections/array.findIndex`
instead.

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
| Triage incident records with named arguments and constructor defaults | `closures/named-arguments.protos` |
| Share captured mutable state across closures | `closures/shared-captured-state.protos` |
| Return early from an enclosing function inside an Array callback | `closures/non-local-return.protos` |
| Inherit callable behavior through delegation | `objects/inherited-callability.protos` |
| Express conditional control flow | `control-flow/conditional.protos` |
| Compute Fibonacci with recursive and iterative variants | `algorithms/fibonacci-recursive.protos` |
| Compute factorial recursively | `algorithms/factorial-recursive.protos` |
| Run FizzBuzz over 1 through 100 | `algorithms/fizzbuzz.protos` |
| Compute the greatest common divisor | `algorithms/greatest-common-divisor.protos` |
| Simulate the 100 doors problem | `algorithms/100-doors.protos` |
| Search a sorted integer Array with binary search | `algorithms/binary-search.protos` |
| Sort an Array with bubble sort | `algorithms/bubble-sort.protos` |
| Summarize threshold alerts and reconcile forward/reverse stride samples | `algorithms/stride-sampling-summary.protos` |
| Store, compose and invert first-class functions | `closures/first-class-functions.protos` |
| Capture independent values in a collection of Closures | `closures/value-capture.protos` |
| Delegate optional behavior to a helper object | `objects/rosetta-delegates.protos` |
| Work with exact Fractions, Float mixing, Complex numbers and square roots | `numbers/exact-and-complex.protos` |
| Set, clear, test and toggle Integer flags with an explicit 32-bit mask | `numbers/bit-flags-and-masks.protos` |
| Apply and verify a linear transformation with Vectors and Matrices (`std:math/linear_algebra`) | `numbers/vector-matrix.protos` |
| Show portable runtime metadata (`std:runtime`) | `runtime-introspection/info.protos` |
| Inspect the runtime (`std:inspect`, requires `--inspect`) | `runtime-introspection/inspect.protos` |
| Summarize incident severities and distinct responsible teams with Array operations | `collections/array-incident-summary.protos` |
| Pair ordered stock counts, split by threshold and deduplicate item names | `collections/array-pairing-and-deduplication.protos` |
| Use value-keyed maps | `collections/maps.protos` |
| Use identity-keyed maps | `collections/identity-map.protos` |
| Use structurally equal values as Map vs IdentityMap keys | `collections/path-keys.protos` |
| Build and compare portable paths | `paths/portable-paths.protos` |
| Run work asynchronously and compose Futures | `concurrency/future-chain.protos` |
| Observe the current Actor identity | `concurrency/actor-current.protos` |
| Spawn an Actor and request a reply | `concurrency/actor-request-reply.protos` |
| Observe same-sender FIFO with send then request | `concurrency/actor-send-fifo.protos` |
| Route a request through an ActorGroup | `concurrency/actor-group-request.protos` |
| Serve messages from the RootActor through a single-member GroupRef (RootActor service entry) | `actors/root-message-service.protos` |
| Execute work in an isolated parallel domain | `concurrency/parallel-execution.protos` |
| Encode, mutate, and decode bytes | `io/encoding-and-bytes.protos` |
| Answer one HTTP request and observe a rejected request (`std:http/server`) | `io/http-minimal-response.protos` |
| Perform an HTTP GET over an application-opened TCP connection (`std:http/client`, requires `network`) | `io/http-get.protos` |
| Host HTTP on loopback with explicit bounds and drain it (`std:http/host`, requires `network`) | `io/http-loopback-hosting.protos` with `io/http-echo-handler.protos` |
| Host HTTP Workers that call a RootActor service (`std:http/host`, RootActor service entry, requires `network`) | `http/root-service-host.protos` with `http/root-service-handler.protos` as `app:root-service-handler` |
| Dispatch requests by method and path with captured parameters (`std:http/router`) | `io/http-router.protos` |
| Make one HTTPS request to a URL (`std:http/url_client`, requires `network` and `resolver`, manual) | `io/http-url-client.protos` |
| Resolve a hostname and connect to a chosen address (`std:network/resolvers`, requires `network` and `resolver`, manual) | `io/hostname-resolution.protos` |
| Authenticate TLS over a TCP connection and send one HTTP request (`std:tls/client`, requires `network` and `resolver`, manual) | `io/tls-client-over-tcp.protos` |
| Import an entire neighboring SOURCE context | `modules/direct-file-import/main.protos` |
| Publish a pricing MODULE with an encapsulated discount helper | `modules/public-module-pricing/` (`protos run Main`) |
| Compose a real invoice across root and dependency MODULE exports | `modules/package-invoice/` (`protos run Main`) |

The [`direct-file-import`](modules/direct-file-import/main.protos) example uses
`bin/protos protos/examples/modules/direct-file-import/main.protos` from the
product root. Its imported helper is a SOURCE: every top-level binding is
visible. By contrast, the [pricing package](modules/public-module-pricing/package.toml)
exports a distinct public MODULE object, and the
[invoice workspace](modules/package-invoice/package.toml) uses a declared
`dep:rates/Rates` module export. Both package projects ship matching
`protos.lock` files. From each respective project directory invoke
`<absolute-path-to-repo>/bin/protos package manifest`, then
`<absolute-path-to-repo>/bin/protos run Main`. The runnable negative entries
`CheckPrivate`, `CheckDepPrivate`, `CheckDepSource`, and `CheckMissingInitializer`
are documented in the invoice package's [project README](modules/package-invoice/README.md).
The public CLI cannot regenerate those lockfiles or install packages.


Run the metadata example normally from the product repository root:
`bin/protos protos/examples/runtime-introspection/info.protos`. The
inspection example requires explicit authority:
`bin/protos --inspect protos/examples/runtime-introspection/inspect.protos`.
The inspection command produces implementation-specific diagnostic text;
do not treat its contents as a stable output format.

`numbers/bit-flags-and-masks.protos` runs with the standalone CLI
(`bin/protos protos/examples/numbers/bit-flags-and-masks.protos`); it needs no
imports and no host authority, and prints six true Boolean checks.

The sensor-sampling example
[`algorithms/stride-sampling-summary.protos`](algorithms/stride-sampling-summary.protos)
also runs with the standalone CLI and needs no imports or host authorization.
It exercises generic `for` traversal, positive stepping and descending negative
stepping, and prints exactly three lines: `2`, `55` and `true`. It is paired
with the
[`04-control-flow/04-contextual-for.protos`](../tutorials/04-control-flow/04-contextual-for.protos)
tutorial.

The standalone CLI grants no `network`. The two `io/` examples marked "requires
`network`" therefore contain no `print(...)` call and are not run by the CLI
regression suite; they end with a Boolean verdict and are executed by
`ProtosHttpLearningMaterialIntegrationTest` in a Java Polyglot embedding with
socket access, which also installs `http-echo-handler.protos` as `app:echo`.
The two "RootActor service entry" examples are started through the explicit
`ProtosRootActorServiceSession` Java entry with their `service` bootstrap
binding; `protos <file>` would only evaluate their top level. The HTTP one also
supplies `root-service-handler.protos` through that session's application
module catalog.
See the [HTTP guide](../../docs/guide/library/http.md).

Execution modes at a glance:

- **Standalone CLI** (`bin/protos <file>`): `io/http-router.protos`,
  `numbers/vector-matrix.protos`, and the in-memory byte-duplex HTTP example
  `io/http-minimal-response.protos`.
- **Explicit host authority required**: `io/hostname-resolution.protos`,
  `io/tls-client-over-tcp.protos` and `io/http-url-client.protos` (`network`
  and `resolver`), and the TCP Host/Client examples above (`network`).

The three examples marked "manual" reach a real external host. They are not
CI tests and no automated test runs them unchanged. Verified behavior of the
same facilities lives in controlled-fixture Java integration tests:
`ProtosResolverAuthorityTest`, `ProtosTlsClientIntegrationTest` and
`ProtosHttpUrlClientIntegrationTest`. See the
[networking guide](../../docs/guide/library/networking.md) and the
[TLS client guide](../../docs/guide/library/tls.md).
