# Protos tutorials

This directory teaches Protos progressively. Tutorial source files use the canonical `.protos` extension.

The tutorials are explanatory, non-normative material. If an example conflicts with the normative specification under `spec/`, the specification wins.

Print-dependent tutorial programs are maintained as executable standalone-CLI sources. The CLI regression suite discovers every `.protos` file in this tree containing `print(` and executes it unchanged, including Future/Actor lessons that require RootActor task suspension and resumption.
Package lesson `08-language-interactions/06-package-exports/` instead uses
`protos run Main` from its directory; its `module.protos` files are **not**
standalone CLI entries and deliberately contain no `print(` call. They are
covered by the DOC020 package learning-material integration test.

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

The MODULE/SOURCE progression is
[`04-source-full-context.protos`](08-language-interactions/04-source-full-context.protos)
(SOURCE import and complete top-level context),
[`05-public-module-object.protos`](08-language-interactions/05-public-module-object.protos)
(actual Standard Library MODULE and public-value identity), followed by the
[`06-package-exports` project](08-language-interactions/06-package-exports/package.toml)
(explicit publication, private initializer capture and `package.toml`).
The first two run with `bin/protos <file>` from the product root. For the third,
change to its package directory and invoke
`/path/to/protos/bin/protos package manifest` followed by
`/path/to/protos/bin/protos run Main` (or use `protos` if it is on `PATH`).
The package project includes its matching `protos.lock`; the public CLI has no
lock-generation subcommand. See the [module guide](../../docs/guide/06-modules-and-imports.md).

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
bootstrap-local `filesystem` capability. The standalone CLI provisions one only
when asked, over a directory that must already exist
(`protos --allow-filesystem=<existing-directory> protos/tutorials/12-system-resources/02-filesystem-text-roundtrip.protos`);
the LM006 system/resource integration harness also executes it with confined
test storage. Without the explicit grant there is no `filesystem` binding: the
capability is intentionally not made ambient for tutorial convenience.

The standalone CLI lesson
[`12-system-resources/03-hmac-sha256.protos`](12-system-resources/03-hmac-sha256.protos)
introduces keyed HMAC-SHA256 over explicit standard Bytes, a complete 32-octet
tag and verification of unchanged and altered messages. The key is a public
RFC 4231 test fixture, **not** an application authentication secret. Importing
`std:crypto/hmac_sha256` requires no host grant and does not generate a key;
real application secrets need independently authorized acquisition and custody.
See the [cryptography guide](../../docs/guide/library/crypto.md) and the
[authentication cookbook example](../examples/crypto/hmac-sha256.protos).

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
(`std:http/router`) is a standalone-CLI lesson: the compiled Router is an
ordinary handler that needs no duplex and no authority. The
[HTTP guide](../../docs/guide/library/http.md) explains all five HTTP modules.

The authority-free lesson
[`13-http/05-strict-jwt.protos`](13-http/05-strict-jwt.protos) demonstrates
an explicitly injected test Bytes key, an explicit Instant, structured JSON
claims and HS256 issue/verify. Authentication does not itself grant account
permissions. See the [strict JWT guide](../../docs/guide/library/jwt.md).

Three lessons use the optional external FFM provider (I144) and the shared SQLite
library:
[`08-language-interactions/07-ffi-provider-basics.protos`](08-language-interactions/07-ffi-provider-basics.protos)
(importing `ffi:sqlite3`, building signatures, scalar C calls, C results as
data, pre-entry rejection) and
[`12-system-resources/04-ffi-ownership-and-borrows.protos`](12-system-resources/04-ffi-ownership-and-borrows.protos)
(owned handles, `out` parameters, borrowed memory, release order), followed by
[`12-system-resources/05-sqlite-protos-first.protos`](12-system-resources/05-sqlite-protos-first.protos)
(public `std:sqlite`, numeric/NULL binding, copied rows and Closable cleanup).
All three need
the provider JAR authorized explicitly with
`bin/protos --foreign-provider-path <protos-ffm-provider.jar> <file>`, on
Linux x86-64 with JDK 25. They validate with assertions and contain no `print`
call, so the ordinary tutorial sweep, which runs without any provider, skips
them; `dist/test_external_foreign_providers.py` runs them instead. See the
[FFM provider guide](../../docs/guide/interop/ffm-provider.md).

The public LIB028 path starts with
[`08-public-ffi-descriptors.protos`](08-language-interactions/08-public-ffi-descriptors.protos),
which needs no provider and runs through the ordinary CLI tutorial path:
`bin/protos protos/tutorials/08-language-interactions/08-public-ffi-descriptors.protos`.
It follows the private `07` lesson but uses only `std:ffi` descriptors and
proves that importing the public MODULE does not grant native authority.
Continue with
[`09-public-ffi-bindings.protos`](08-language-interactions/09-public-ffi-bindings.protos)
for explicitly authorized C calls, then
[`05-public-ffi-owned-out.protos`](12-system-resources/05-public-ffi-owned-out.protos)
for output Arrays and owned handles, following the private
[`04-ffi-ownership-and-borrows.protos`](12-system-resources/04-ffi-ownership-and-borrows.protos)
lesson. These latter two programs require the
authorized provider JAR and are run by the FFM harness, not the ordinary
provider-free tutorial sweep.

The public SQLite path builds on `std:sqlite` and is distinct from both the
private I144 provider lessons (`07-ffi-provider-basics`,
`04-ffi-ownership-and-borrows`) and the DOC023 public `std:ffi` lessons
above. The LIB029 introduction
[`05-sqlite-protos-first.protos`](12-system-resources/05-sqlite-protos-first.protos)
shows one prepared query with copied rows. The DOC024 practical lesson
[`06-sqlite-query-lifecycle.protos`](12-system-resources/06-sqlite-query-lifecycle.protos)
introduces no new API: it combines the same operations into a table, numeric
inserts with fresh Statements, ordered `each` traversal, bounded error cases
and Statement/Connection close semantics. Both require explicit provider
authorization (`bin/protos --foreign-provider-path <protos-ffm-provider.jar> <file>`),
contain no `print` call, and are run by
`dist/test_external_foreign_providers.py` rather than the ordinary
provider-free tutorial sweep. See the
[SQLite guide](../../docs/guide/library/sqlite.md).

After the HTTP progression, the next advanced workflow reaches real hosts and
is not a tutorial: the manual cookbook examples
[`http-url-client.protos`](../examples/io/http-url-client.protos) (one HTTPS
request through `std:http/url_client`),
[`hostname-resolution.protos`](../examples/io/hostname-resolution.protos) and
[`tls-client-over-tcp.protos`](../examples/io/tls-client-over-tcp.protos) need a
Java host that grants `network` and `resolver`. Read the
[networking guide](../../docs/guide/library/networking.md) and the
[TLS client guide](../../docs/guide/library/tls.md) first.

The linear-algebra progression is entirely standalone-CLI:
`01-vector-operations.protos`, `02-matrix-operations.protos` and
`03-complex-and-shapes.protos` use `std:math/linear_algebra` and need no host
capability. The [linear-algebra guide](../../docs/guide/library/linear-algebra.md)
is the reference.
