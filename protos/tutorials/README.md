# Protos tutorials

This directory teaches Protos progressively. Tutorial source files use the canonical `.protos` extension.

The tutorials are explanatory, non-normative material. If an example conflicts with the normative specification under `spec/`, the specification wins.

Print-dependent tutorial programs are maintained as executable standalone-CLI sources. The CLI regression suite discovers every `.protos` file in this tree containing `print(` and executes it unchanged, including Future/Actor lessons that require RootActor task suspension and resumption.

Start with the numbered directories in order. Each source file focuses on one language mechanism and is intentionally small.

The `03-closures/` lessons build on each other: `01-closure.protos` introduces
ordinary Closure values, `02-captured-state.protos` introduces lexical capture,
and [`03-closures/03-return-home.protos`](03-closures/03-return-home.protos)
introduces normal final-expression results and the equivalence of `^value` and
contextual `return value`. The third lesson demonstrates an early exit through
nested callbacks and records the visited elements in an explicit counter, so the
skipped remainder of the enclosing function is observable.

[`01-values-and-slots/04-bitwise-operators.protos`](01-values-and-slots/04-bitwise-operators.protos)
teaches the six bitwise and shift operators (`&`, `|`, binary `~`, prefix `~`,
`<<`, `>>`). It distinguishes binary XOR from unary NOT by position, shows that
Integer is unlimited and that fixed-width fields need explicit masks, contrasts
the strict evaluation of `&` and `|` with the lazy Boolean `&&`, and
demonstrates that each operator sends an ordinary message (such as `bitAnd`)
to its receiver, including a custom object. It runs with the standalone CLI.

The control-flow progression now ends with
[`04-control-flow/04-contextual-for.protos`](04-control-flow/04-contextual-for.protos).
It introduces generic Array traversal, ascending and descending numeric ranges,
signed and dynamic steps, Closure-parameter binder scope, and the exact
relationship with ordinary `each`. The lesson runs unchanged through the
standalone CLI without imports or additional host authority. See the
[control-flow guide](../../docs/guide/04-control-flow-through-protocols.md#the-contextual-for-forms-are-each-and-eachstep)
for the accompanying explanation.

The collection progression continues after Map and IdentityMap with
[`05-collections/03-array-copies-and-queries.protos`](05-collections/03-array-copies-and-queries.protos)
(fresh Array copy/edit operations, default sort, ordered queries and saturating
prefix/suffix copies), then
[`05-collections/04-array-bulk-transformations.protos`](05-collections/04-array-bulk-transformations.protos)
(`flatMap`, `zip`, `partition`, `distinct` and callback snapshot behavior).
Both run unchanged with the standalone CLI and require no extra host authority.

The `07-call-arguments/` progression now has two lessons:
[`01-defaults-args-and-rest.protos`](07-call-arguments/01-defaults-args-and-rest.protos)
introduces positional arguments, defaults, and rest; then
[`02-named-arguments.protos`](07-call-arguments/02-named-arguments.protos)
teaches D237 names in any order, MIX-C positional indices, spread, explicit
`null`, and grouped slot creation. Both run with the standalone CLI and need
no extra host authority. The [Closure guide](../../docs/guide/03-closures-methods-and-receivers.md#named-arguments-and-name-aware-calls-d237)
explains the corresponding dynamic binding and error boundaries.

Current progression:

1. `01-values-and-slots/`
2. `02-objects/`
3. `03-closures/`
4. `04-control-flow/`
5. `05-collections/`
6. `06-path-values/`
7. `07-call-arguments/`
8. `08-language-interactions/`
9. `09-futures/`
10. `10-actors/`
11. `11-actor-groups/`
12. `12-system-resources/`
13. `13-http/`
14. `14-linear-algebra/`

The system/resource progression deliberately uses two execution hosts:
`01-process-stream-output.protos` is a normal standalone-CLI lesson, while
`02-filesystem-text-roundtrip.protos` requires an explicitly provisioned
bootstrap-local `filesystem` capability and is executed by the LM006 system/resource
integration harness. The latter is intentionally not made ambient merely for
tutorial convenience.

The HTTP progression follows the same rule. `01-serve-one-response.protos`
(`Server.serveOne`) and `02-client-response-body.protos` (`Client.exchangeOne`
with incremental body reads) are standalone-CLI lessons over the in-memory
duplex of the support module `memory-duplex.protos`, whose byte-I/O and close
contracts are checked by the standalone-CLI source `memory-duplex-contract.protos`.
`03-host-and-client-over-tcp.protos` (`Host.owning` with the named options
`workers` and `maxConnections`, Client GETs over TCP, ownership and drain)
requires an explicitly provisioned bootstrap-local
`network` capability and the support module `hello-handler.protos` installed as
`app:hello`; `ProtosHttpLearningMaterialIntegrationTest` runs it in a Java
Polyglot embedding with socket access. `04-router-dispatch.protos`
(`std:http/Router`) is a standalone-CLI lesson: the compiled Router is an
ordinary handler that needs no duplex and no authority. The
[HTTP guide](../../docs/guide/library/http.md) explains all five HTTP modules.

After the HTTP progression, the next advanced workflow reaches real hosts and
is not a tutorial: the manual cookbook examples
[`http-url-client.protos`](../examples/io/http-url-client.protos) (one HTTPS
request through `std:http/UrlClient`),
[`hostname-resolution.protos`](../examples/io/hostname-resolution.protos) and
[`tls-client-over-tcp.protos`](../examples/io/tls-client-over-tcp.protos) need a
Java host that grants `network` and `resolver`. Read the
[networking guide](../../docs/guide/library/networking.md) and the
[TLS client guide](../../docs/guide/library/tls.md) first.

The linear-algebra progression is entirely standalone-CLI:
`01-vector-operations.protos`, `02-matrix-operations.protos` and
`03-complex-and-shapes.protos` use `std:math/LinearAlgebra` and need no host
capability. The [linear-algebra guide](../../docs/guide/library/linear-algebra.md)
is the reference.
