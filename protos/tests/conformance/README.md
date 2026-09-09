# Protos language conformance tests

This tree contains executable Protos programs that verify observable language
behavior independently from the Java implementation test layout.

The bundled TOOL002 Test Tool owns every retained main-manifest expectation
family, including all `future-*` families, through the F4 executable ownership
cutover. The former direct Java/JUnit manifest owner has been retired; Java tests
remain appropriate for host/runtime mechanics. The Protos programs themselves
remain implementation-independent corpus sources.

`manifest.tsv` contains exactly three tab-separated fields:

1. relative `.protos` source path;
2. expectation kind;
3. expected value payload.

Current expectation kinds:

- `boolean`: the program must complete normally with canonical `true` or `false`
  as named by field 3;
- `null`: the program must complete normally with canonical `null`; field 3 is
  `-`;
- `integer`: the program must complete normally with the exact mathematical
  Integer in field 3;
- `fixed-integer`: the program must complete normally with a fixed-width
  exact-integer value; field 3 is `Family:value`, for example `UInt8:255`;
- `float-bits`: the program must complete normally with a Float whose raw IEEE
  binary64 bits equal the 16-hex-digit payload in field 3;
- `float-nan`: the program must complete normally with the semantic Float NaN
  value; field 3 is `-` because NaN payload/sign bits are not portable Protos
  semantics;
- `error`: the program must signal a Protos Error; field 3 is `-` because this
  expectation checks only the semantic fact of signaling an Error, not a narrower
  Error category;
- `error-parent`: the program must signal a Protos Error whose immediate
  delegation parent is the standard Error prototype named by field 3;
- `closure-error-parent-fresh`: the program must complete normally with a
  zero-argument Closure; the runner invokes that exact Closure twice, requires
  both invocations to signal Errors whose immediate delegation parent is the
  standard Error prototype named by field 3, and requires the two Error
  occurrences to have distinct identity;
- `future-integer`: the program must return a Future; the runner waits for that
  Future to become terminal and requires resolution to the exact mathematical
  Integer in field 3;
- `future-null`: the program must return a Future that eventually resolves to
  canonical `null`; field 3 is `-`;
- `future-boolean`: the program must return a Future that eventually resolves to
  canonical `true` or `false` as named by field 3;
- `future-error`: the program must return a Future whose terminal state is
  `failed`; field 3 is `-` because this expectation does not require a narrower
  Error category;
- `future-error-parent`: the program must return a Future whose terminal state is
  `failed` and whose recorded Error has the standard Error prototype named by
  field 3 as its immediate delegation parent;
- `future-observation-error-identity`: the program must return an ordinary object
  with local `future` and zero-argument `observe` slots; the runner waits for the
  Future to become terminal, invokes `observe` twice, and interprets field 3 as
  `MODE:ErrorPrototype`. `stored` additionally requires a local `error` slot and
  both observations must signal that exact object; `fresh` requires distinct
  Error identities. Both modes require the named immediate Error parent;
- `future-cancelled`: the program must return a Future whose terminal state is
  `cancelled`; field 3 is `-`.

LM005 Actor cases live under `actor/` with their own `actor/manifest.tsv` because
they require a real RootActor/Process plus deterministic test-host module and
scheduler boundaries. The tested entry programs and Actor bootstrap modules are
still ordinary `.protos` sources; only orchestration and assertions are host-side.

LM005 Group cases similarly live under `group/` with `group/manifest.tsv` and
ordinary `.protos` bootstrap modules. Their local `future-integer-one-of`
expectation asserts that a reply came from one of the explicitly permitted
eligible members without turning scheduler/member selection into a test assumption.

LM006 system/resource maturity cases that need no host-side orchestration live
under `maturity/` and remain in the main manifest. LM006-A deliberately
composes already-closed Bytes and Encoding semantics entirely in ordinary
Protos, so it adds no Java harness or test-only language surface.

The harness deliberately keeps assertions outside the Protos language. Adding
test-only assertion syntax or a privileged testing object would be a language or
library design decision and is not required for conformance testing.
