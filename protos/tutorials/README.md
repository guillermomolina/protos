# Protos tutorials

This directory teaches Protos progressively. Tutorial source files use the canonical `.protos` extension.

The tutorials are explanatory, non-normative material. If an example conflicts with the normative specification under `spec/`, the specification wins.

Print-dependent tutorial programs are maintained as executable standalone-CLI sources. The CLI regression suite discovers every `.protos` file in this tree containing `print(` and executes it unchanged, including Future/Actor lessons that require RootActor task suspension and resumption.

Start with the numbered directories in order. Each source file focuses on one language mechanism and is intentionally small.

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
`03-host-and-client-over-tcp.protos` (`Host.owning`, Client GETs over TCP,
ownership and drain) requires an explicitly provisioned bootstrap-local
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
