# Protos language conformance tests

This tree contains executable Protos programs that verify observable language
behavior independently from the Java implementation test layout.

The Java/JUnit harness currently lives under `src/test/java` only as the runner
for the reference implementation. The Protos programs themselves live here so a
future implementation can execute the same conformance corpus.

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
- `error`: the program must signal a Protos Error; field 3 is `-` because the
  current harness checks the semantic fact of signaling an Error, not a narrower
  Error category.

The harness deliberately keeps assertions outside the Protos language. Adding
test-only assertion syntax or a privileged testing object would be a language or
library design decision and is not required for conformance testing.
